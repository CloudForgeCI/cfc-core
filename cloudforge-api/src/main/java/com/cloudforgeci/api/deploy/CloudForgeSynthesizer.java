package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationLoader;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.rules.ComplianceFindingsCollector;
import com.cloudforgeci.api.core.rules.NagReportReader;
import com.cloudforgeci.api.core.rules.NagReportReader.ComplianceFinding;
import com.cloudforgeci.api.launch.ApplicationEc2Stack;
import com.cloudforgeci.api.launch.ApplicationFargateStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;

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
 * <p>Follows the same recipe as {@code cfc-testing}'s {@code InteractiveDeployer} "synthesize
 * only" / "export template" options: build a CDK {@code App} with {@code cfc} context set from
 * {@link DeploymentConfig#toContextMap()}, construct the {@code com.cloudforgeci.api.launch} stack
 * for {@link DeploymentConfig#runtime}, and call {@code app.synth()}. It lives here so that callers
 * without an interactive CLI, such as CloudForge Manager, need not depend on the sample project.</p>
 *
 * <p><b>Account resolution:</b> {@link DeploymentConfig#account} wins when set; otherwise
 * {@code CDK_DEFAULT_ACCOUNT}; otherwise the template's {@code Environment} is account-agnostic
 * and CloudFormation resolves {@code AWS::AccountId} in whichever account it is deployed to.</p>
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
 *
 * <p><b>Hosted-zone resolution:</b> the same exposure, a different context provider —
 * {@code DomainFactory}'s {@code HostedZone.fromLookup} for an SSL+custom-domain deploy has no
 * CloudFormation-intrinsic fallback the way AZs do, so it always resolves through CDK's
 * synth-time {@code hosted-zone} context provider once a domain is set, and would otherwise
 * silently bake in CDK's dummy zone id. See {@link #seedHostedZoneContext}.</p>
 */
public final class CloudForgeSynthesizer {

    /**
     * Serializes every {@link #synthesize} call process-wide. aws-cdk-lib is backed by a single
     * jsii Node.js kernel process per JVM, and its Java/jsii channel is not thread-safe:
     * overlapping {@code app.synth()} calls (or one interrupted mid-call) can corrupt kernel state,
     * causing later, unrelated synthesis calls in the same JVM to fail with NPEs
     * ({@code Node.getId()} or {@code Tags.of(...)} returning null). Synthesis is not a hot path, so
     * a single static lock is cheap and makes concurrent callers (such as a job pool running
     * several deploys) safe.
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

    /** {@link #synthesizeAdvisoryDryRun}'s result — every finding cdk-nag and the requested
     *  {@code FrameworkRules} implementations produced, with nothing filtered out the way {@link
     *  #synthesize}'s own ENFORCE-mode check only reads error-level cdk-nag lines.
     *
     *  @param error non-null only when {@code app.synth()} itself failed to complete (e.g. an
     *      "always load" cross-framework validator that hard-blocks regardless of {@code
     *      complianceMode} — not every validator in this codebase honors ADVISORY the way the
     *      framework-specific ones do). {@code frameworkFindings} may still be partially populated
     *      in that case (whatever ran before the failing validator), but {@code nagFindings} is
     *      always empty, since cdk-nag's own report files never get written unless synthesis
     *      actually finished. */
    public record ComplianceCheckResult(
        String stackName, List<ComplianceFinding> nagFindings, List<ComplianceFinding> frameworkFindings,
        String error) {
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
            // Sibling top-level key, deliberately outside "cfc" -- see
            // DeploymentConfig#complianceFrameworksRawOverride's own javadoc for why it can't be
            // folded into the "cfc" map like every other field here.
            if (config.complianceFrameworksRawOverride != null && !config.complianceFrameworksRawOverride.isBlank()) {
                app.getNode().setContext("complianceFrameworksRawOverride", config.complianceFrameworksRawOverride);
            }

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
                // DomainFactory calls HostedZone.fromLookup (a context-provider lookup with the
                // same dummy-value fallback as availability zones) only when a domain is set and
                // the zone is not being created. See seedHostedZoneContext.
                if (config.domain != null && !config.domain.isBlank()
                        && !Boolean.TRUE.equals(config.createZone)) {
                    seedHostedZoneContext(app, account, region, config.domain);
                }
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

            // ComplianceMode.ENFORCE: read cdk-nag's report files after synthesis (see
            // NagReportReader for why) and fail on errors. ADVISORY leaves findings uninspected;
            // SecurityRules.applyCdkNagValidation decides which packs run.
            // A null complianceMode resolves to the profile default, as in DeploymentContext
            // (ENFORCE for PRODUCTION, ADVISORY otherwise).
            ComplianceMode effectiveComplianceMode = config.complianceMode != null
                ? config.complianceMode
                : ComplianceMode.defaultForProfile(config.securityProfile);
            if (effectiveComplianceMode == ComplianceMode.ENFORCE) {
                List<ComplianceFinding> errors = NagReportReader.readErrors(
                    Path.of(assembly.getDirectory()), config.stackName);
                if (!errors.isEmpty()) {
                    throw new ComplianceViolationException(config.stackName, errors);
                }
            }

            CloudFormationStackArtifact artifact = assembly.getStackByName(config.stackName);
            Path templateFile = Path.of(assembly.getDirectory()).resolve(artifact.getTemplateFile());
            return new Result(config.stackName, templateFile, Path.of(assembly.getDirectory()));
        }
    }

    /**
     * A synth-only "what would this report" pass — never deploys anything, never writes to
     * CloudFormation, and never throws over a compliance finding the way {@link #synthesize} can
     * in {@code ComplianceMode.ENFORCE}. Built for an on-demand compliance check against an
     * already-deployed stack's reconstructed configuration (CloudForge Manager's Compliance tab):
     * the caller sets {@code config.complianceFrameworks} to whichever framework(s) the user
     * picked, and this returns every cdk-nag and framework-rule finding for them, instead of only
     * the subset that would block a real deploy.
     *
     * <p>{@code config.complianceMode} is forced to {@code ADVISORY} for the duration of this call
     * (and restored after) regardless of what the caller set it to — this method's entire point is
     * "report, don't block". Framework-rule findings only reach a caller at all via {@link
     * ComplianceFindingsCollector} — see its own javadoc for why {@code Node.addValidation} itself
     * can't carry them out of ADVISORY mode.</p>
     *
     * <p>{@code config.securityProfile} and {@code config.auditManagerEnabled} are likewise forced
     * (to {@code PRODUCTION} and {@code true}, both restored after) — {@code
     * SecurityRules#install}'s own gates only ever apply cdk-nag for a {@code PRODUCTION} profile
     * and only ever install any {@code FrameworkRules} implementation at all when {@code
     * auditManagerEnabled} is set, regardless of which frameworks were requested. Without forcing
     * both, a check against a real DEV/STAGING stack's own reconstructed profile would silently
     * come back with zero findings from either engine — not "this stack is compliant", just "these
     * checks never ran" — which would actively mislead the very user this preview exists for. The
     * whole point of an advisory check is "would this pass production-grade scrutiny", independent
     * of whatever the target stack's own real profile happens to be.</p>
     *
     * <p>Never throws — not every validator in this codebase honors {@code ComplianceMode}
     * (several "always load" cross-framework validators hard-block on their own {@code
     * SecurityProfile} gate regardless of mode; see {@code ComplianceCheckResult#error}'s own
     * javadoc). A caller building a UI around this (CloudForge Manager's Compliance tab) can
     * always render SOMETHING rather than handling a thrown exception on top of the findings
     * lists it otherwise expects.</p>
     *
     * @param outputDirectory a throwaway cloud-assembly directory — the caller owns its lifecycle
     *     (create before, delete after); nothing here is meant to persist
     */
    public static ComplianceCheckResult synthesizeAdvisoryDryRun(DeploymentConfig config, Path outputDirectory) {
        Objects.requireNonNull(config, "config");
        ComplianceMode originalMode = config.complianceMode;
        var originalProfile = config.securityProfile;
        Boolean originalAuditManagerEnabled = config.auditManagerEnabled;
        config.complianceMode = ComplianceMode.ADVISORY;
        config.securityProfile = SecurityProfile.PRODUCTION;
        config.auditManagerEnabled = true;
        ComplianceFindingsCollector.start();
        try {
            Result result = synthesize(config, outputDirectory);
            List<ComplianceFinding> nagFindings =
                NagReportReader.readAll(result.assemblyDirectory(), result.stackName());
            List<ComplianceFinding> frameworkFindings = ComplianceFindingsCollector.drain();
            return new ComplianceCheckResult(result.stackName(), nagFindings, frameworkFindings, null);
        } catch (IOException e) {
            List<ComplianceFinding> frameworkFindings = ComplianceFindingsCollector.drain();
            return new ComplianceCheckResult(config.stackName, List.of(), frameworkFindings, e.getMessage());
        } finally {
            config.complianceMode = originalMode;
            config.securityProfile = originalProfile;
            config.auditManagerEnabled = originalAuditManagerEnabled;
            // Idempotent if the try block already drained -- guards the case synthesize() itself
            // threw before that line ran, which would otherwise leave a stale collector on this
            // thread for whatever the next synth call happens to be.
            ComplianceFindingsCollector.drain();
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

    /**
     * Seeds CDK's {@code hosted-zone} synth-time context provider. {@link DomainFactory}'s
     * {@code HostedZone.fromLookup} (SSL with a custom domain) has the same dummy-value fallback
     * that {@link #seedAvailabilityZoneContext} addresses for AZs: without seeding, the template
     * contains a literal {@code "DUMMY"} hosted zone id and CloudFormation fails with
     * "No hosted zone found with ID: DUMMY".
     *
     * <p>Unlike AZs, the zone id cannot be derived from account/region, so this makes one
     * {@code ListHostedZonesByName} call (Route53 is global; account/region appear in the key only
     * because CDK's context key format requires them). The entry maps
     * {@code hosted-zone:account=<id>:domainName=<domain>:privateZone=false:region=<region>} to
     * {@code {"Id": "/hostedzone/<ZONEID>", "Name": "<domain>."}}.
     *
     * @throws IllegalArgumentException when no hosted zone for this exact domain exists in the
     *     account being deployed into — the same failure CloudFormation would eventually report,
     *     just surfaced immediately instead of after minutes of provisioning other resources first
     */
    private static void seedHostedZoneContext(App app, String account, String region, String domain) {
        String zoneName = domain.endsWith(".") ? domain : domain + ".";
        try (Route53Client route53 = Route53Client.builder().region(Region.AWS_GLOBAL).build()) {
            List<HostedZone> zones = route53.listHostedZonesByName(
                ListHostedZonesByNameRequest.builder().dnsName(zoneName).maxItems("1").build())
                .hostedZones();
            if (zones.isEmpty() || !zones.getFirst().name().equals(zoneName)) {
                throw new IllegalArgumentException(
                    "No Route53 hosted zone found for domain \"" + domain + "\" in account " + account
                        + " -- create one first, or set createZone to have this deploy create it.");
            }
            app.getNode().setContext(
                "hosted-zone:account=" + account + ":domainName=" + domain + ":privateZone=false:region=" + region,
                Map.of("Id", zones.getFirst().id(), "Name", zoneName));
        }
    }
}
