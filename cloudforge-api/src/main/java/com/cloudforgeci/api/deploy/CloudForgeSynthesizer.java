package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationLoader;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.launch.ApplicationEc2Stack;
import com.cloudforgeci.api.launch.ApplicationFargateStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * In-process CDK synthesis — produces the canonical CloudFormation template
 * {@link CloudForgeDeployment} (specifically {@code AwsDirectDeployer}, but the shape is target-
 * agnostic) needs, without shelling out to the {@code cdk} CLI.
 *
 * <p>Same recipe {@code cfc-testing}'s {@code InteractiveDeployer} already uses for its
 * "synthesize only" / "export template" options: build a CDK {@code App} with {@code cfc} context
 * set from {@link DeploymentConfig#toContextMap()}, construct the right
 * {@code com.cloudforgeci.api.launch} stack for {@link DeploymentConfig#runtime}, call {@code
 * app.synth()}. Factored out here (rather than reused from cfc-testing directly) because Manager
 * — the first caller with no interactive CLI — cannot depend on the sample/reference repo.</p>
 *
 * <p><b>Account resolution, stated plainly:</b> {@link DeploymentConfig#account} wins when set;
 * otherwise falls back to {@code CDK_DEFAULT_ACCOUNT}; otherwise the synthesized template's
 * {@code Environment} is account-agnostic (same fallback {@code InteractiveDeployer}'s "Export
 * Template" option already relies on) — CloudFormation resolves {@code AWS::AccountId}
 * pseudo-parameters against whichever real account the template is actually deployed into. This
 * class stays entirely unaware of *why* a caller might set an explicit account (cross-account
 * deploy, multi-region, whatever) — that's the caller's concern.</p>
 *
 * <p><b>Availability-zone resolution:</b> {@code VpcFactory.maxAzs(2)} reads
 * {@code Stack.availabilityZones}, which CDK resolves via {@code Fn::GetAZs} (a
 * CloudFormation-time token) only while account+region are both unresolved — true for the
 * account-agnostic template above. The moment an explicit account is set, CDK instead routes AZ
 * resolution through its {@code availability-zones} *synth-time* context provider — and since
 * this deploy path never shells out to the {@code cdk} CLI to satisfy that lookup, CDK would
 * silently return its built-in dummy values ({@code dummy1a}/{@code dummy1b}) baked straight into
 * the template, which then fails at CloudFormation. Whenever an account is pinned, {@link
 * #synthesize} seeds that context key itself first — see {@link #seedAvailabilityZoneContext}.</p>
 */
public final class CloudForgeSynthesizer {

    /**
     * Serializes every {@link #synthesize} call process-wide. Discovered the hard way: aws-cdk-lib
     * is a jsii-wrapped library backed by a single shared Node.js kernel child process per JVM,
     * and its Java↔jsii communication channel is not safe for concurrent use from multiple
     * threads — two overlapping {@code app.synth()} calls (or one interrupted mid-call) corrupted
     * kernel state badly enough that unrelated, previously-passing synthesis calls in the *same*
     * JVM started failing with nonsensical NPEs ({@code Node.getId()} returning null,
     * {@code Tags.of(...)} returning null) deep inside {@code ApplicationFactory}, for stacks that
     * had nothing to do with the call that corrupted things. A single static lock is the
     * conservative fix — this is not a hot path (interactive deploys, not a request-per-second
     * API), so serializing it costs nothing that matters and removes an entire class of
     * hard-to-reproduce cross-request corruption. If a caller (e.g. Manager's async job pool)
     * ever runs multiple {@code deploy:create} jobs concurrently, this lock is what makes that
     * safe rather than a silent landmine.
     */
    private static final Object SYNTH_LOCK = new Object();

    private CloudForgeSynthesizer() {
    }

    /**
     * @param stackName the synthesized stack's name (== {@code config.stackName})
     * @param templateFile absolute path to the synthesized CloudFormation template JSON —
     *     what {@link DeploymentRequest#canonicalTemplate()} expects
     * @param assemblyDirectory the cloud assembly directory ({@code outputDirectory}, resolved)
     */
    public record Result(String stackName, Path templateFile, Path assemblyDirectory) {
    }

    /**
     * Resolves {@code config.applicationSpec} via {@link ApplicationLoader} when not already set
     * (mutates {@code config} — matches {@code CloudForgeDeployment.deploy()}'s existing
     * convention of resolving/preparing the config it's handed rather than requiring the caller
     * to have already done so).
     *
     * @throws IllegalArgumentException when applicationId/stackName/runtime are missing, or no
     *     {@code ApplicationSpec} is registered for {@code config.applicationId}
     * @throws IOException when {@code app.synth()} fails, or the output directory can't be created
     */
    public static Result synthesize(DeploymentConfig config, Path outputDirectory) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (config.stackName == null || config.stackName.isBlank()) {
            throw new IllegalArgumentException("config.stackName is required to synthesize");
        }
        if (config.runtime == null) {
            throw new IllegalArgumentException("config.runtime is required to synthesize");
        }
        if (config.applicationSpec == null) {
            if (config.applicationId == null || config.applicationId.isBlank()) {
                throw new IllegalArgumentException("config.applicationId is required to synthesize");
            }
            config.applicationSpec = ApplicationLoader.findById(config.applicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "No ApplicationSpec registered for applicationId=" + config.applicationId));
        }
        ApplicationSpec applicationSpec = config.applicationSpec;

        Files.createDirectories(outputDirectory);

        Map<String, Object> cfcContext = config.toContextMap();
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(config.securityProfile);

        // Everything from here on talks to the jsii-backed CDK objects (App/Stack/DeploymentContext
        // .from/app.synth()) — see SYNTH_LOCK's javadoc for why this whole section is serialized.
        synchronized (SYNTH_LOCK) {
            App app = App.Builder.create()
                .analyticsReporting(false)
                .autoSynth(false)
                .treeMetadata(false)
                .outdir(outputDirectory.toAbsolutePath().toString())
                .build();
            app.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(app);

            String region = cfc.region() != null && !cfc.region().isBlank()
                ? cfc.region()
                : System.getenv().getOrDefault("CDK_DEFAULT_REGION", "us-east-1");
            String account = config.account != null && !config.account.isBlank()
                ? config.account
                : System.getenv("CDK_DEFAULT_ACCOUNT");

            Environment.Builder envBuilder = Environment.builder().region(region);
            if (account != null && !account.isBlank()) {
                envBuilder.account(account);
                seedAvailabilityZoneContext(app, account, region, config.availabilityZones);
            }
            StackProps props = StackProps.builder().env(envBuilder.build()).build();

            switch (config.runtime) {
                case FARGATE -> new ApplicationFargateStack(
                    app, config.stackName, props, config.securityProfile, iamProfile, applicationSpec);
                case EC2 -> new ApplicationEc2Stack(
                    app, config.stackName, props, config.securityProfile, iamProfile, applicationSpec);
            }

            CloudAssembly assembly;
            try {
                assembly = app.synth();
            } catch (RuntimeException e) {
                throw new IOException("CDK synthesis failed for " + config.stackName + ": " + e.getMessage(), e);
            }

            CloudFormationStackArtifact artifact = assembly.getStackByName(config.stackName);
            Path templateFile = Path.of(assembly.getDirectory()).resolve(artifact.getTemplateFile());
            return new Result(config.stackName, templateFile, Path.of(assembly.getDirectory()));
        }
    }

    /**
     * Seeds CDK's {@code availability-zones} synth-time context provider so pinning {@code
     * Environment.account} (see the class javadoc) never falls through to CDK's dummy-AZ
     * fallback. Key format ({@code availability-zones:account=<account>:region=<region>}) matches
     * CDK's own {@code ContextProvider.AVAILABILITY_ZONE_PROVIDER} key construction; the value is
     * the plain list of full zone names the VPC construct expects.
     *
     * <p>Suffixes come from {@link DeploymentConfig#availabilityZones} when the caller populated
     * it (region-relative — "a" means whichever zone the target region calls "a"); defaults to
     * {@code ["a", "b"]} otherwise, since every commercial AWS region has at least two AZs with
     * those conventional suffixes.</p>
     */
    private static void seedAvailabilityZoneContext(App app, String account, String region, String[] suffixes) {
        List<String> resolvedSuffixes = suffixes != null && suffixes.length > 0
            ? Arrays.asList(suffixes)
            : List.of("a", "b");
        List<String> zones = resolvedSuffixes.stream()
            .map(suffix -> region + suffix.trim().toLowerCase(Locale.ROOT))
            .toList();
        app.getNode().setContext(
            "availability-zones:account=" + account + ":region=" + region, zones);
    }
}
