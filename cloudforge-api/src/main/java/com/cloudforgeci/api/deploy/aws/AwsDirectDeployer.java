package com.cloudforgeci.api.deploy.aws;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.manager.ManagerEndpointSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Capability;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetStatus;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetType;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DeleteChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.ExecuteChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.GetTemplateRequest;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.ResourceChange;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sts.StsClient;

import com.cloudforgeci.localstack.LocalStackCdkAssetPublisher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and incrementally updates CloudFormation stacks on real AWS via change sets.
 *
 * <p>Backs {@code CloudForgeDeployment}'s {@code AWS} case — the direct-deploy counterpart to
 * {@code LocalStackDeployer}/{@code MiniStackDeployer}, minus everything specific to emulating
 * AWS locally (no LocalStack Cognito/RDS-secret reconciliation, no ECS-restart-after-secret-sync —
 * real AWS Cognito/RDS integrate through CDK's own Secrets Manager wiring and need none of
 * that).</p>
 *
 * <p><b>Targets real AWS by default, but transparently redirects to a local emulator when
 * *Manager itself* is running inside one</b> — same {@code LOCALSTACK_ENDPOINT}/
 * {@code AWS_ENDPOINT_URL} env-var detection {@link
 * com.cloudforgeci.localstack.LocalStackDeployer#resolveEndpoint()} already uses, deliberately
 * mirrored here rather than left real-AWS-only — without an endpoint override, a {@code
 * deploy:create} click from a Manager instance hosted on LocalStack would try to reach
 * {@code cloudformation.us-east-1.amazonaws.com} instead of the local emulator it's actually
 * running against.</p>
 *
 * <p>Every stack this deployer creates or updates is tagged with the same
 * {@code cloudforge:managed}/{@code cloudforge:application}/{@code cloudforge:runtime}
 * convention {@code ApplicationFargateStack}/{@code ApplicationEc2Stack} apply via
 * {@code Tags.of(this).add(...)} at synthesis time — required both for Manager's own AWS
 * inventory (see {@code StackListingPolicy}) and for the {@code aws:RequestTag}/
 * {@code aws:ResourceTag} IAM conditions {@code ManagerOperatorIamSupport.deployStatements}
 * scopes {@code CreateStack}/{@code UpdateStack} to. Those conditions evaluate the stack-level
 * {@code Tags} parameter on the CloudFormation API call itself, not any resource-level tags
 * already baked into the template by the CDK {@code Tags} aspect — the two are separate
 * CloudFormation tagging mechanisms, and only the former is what this class controls.</p>
 *
 * <p><b>Not exercised against real AWS in this repo's test suite</b> — no AWS credentials are
 * available in CI/dev sandboxes for that. {@code AwsDirectDeployerTest} instead points this class
 * at a real LocalStack instance via the injectable-client constructor (LocalStack is
 * CloudFormation-API-compatible), which exercises every code path except real AWS's actual
 * network/auth surface. Treat a real AWS deploy as the first true end-to-end validation of this
 * class specifically — the underlying changeset create/execute/wait sequence is otherwise a
 * close copy of {@code LocalStackDeployer}'s, which has been exercised that way for a while.</p>
 */
public final class AwsDirectDeployer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration OPERATION_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    /**
     * Caps how long the stack create/update waiter below ({@code
     * cloudFormation.waiter().waitUntilStackCreateComplete(...)}) will wait for a LocalStack
     * target specifically — real AWS keeps the SDK's own default waiter config (up to ~60
     * minutes; a genuinely large stack can legitimately take a while), but a LocalStack resource
     * that hangs (a common emulation gap) is not a real deploy taking a long time, and shouldn't
     * leave the journal entry — and the UI polling it — showing "RUNNING" for the better part of
     * an hour with no indication anything is wrong. A LocalStack deploy that hasn't finished in
     * 5 minutes is hung, not slow.
     */
    private static final Duration LOCAL_EMULATOR_WAITER_TIMEOUT = Duration.ofMinutes(5);
    /** CloudFormation inline template body limit (same on real AWS as LocalStack). */
    private static final int MAX_INLINE_TEMPLATE_BYTES = 51_200;
    /** Shared with {@code ManagerOperatorIamSupport}'s S3 grant for this bucket — keep the two in
     *  sync rather than duplicating the literal. */
    public static final String TEMPLATE_BUCKET_PREFIX = "cfc-cfn-templates-";

    public static final String TAG_MANAGED = "cloudforge:managed";
    public static final String TAG_APPLICATION = "cloudforge:application";
    public static final String TAG_RUNTIME = "cloudforge:runtime";

    private final CloudFormationClient cloudFormation;
    private final S3Client s3;
    private final String applicationId;
    private final String runtimeTag;
    private final String templateBucket;
    private final boolean localEmulatorTarget;
    private final Region region;
    private final AwsCredentialsProvider credentialsOverride;

    /**
     * Real AWS by default (default credential/region chain, region from {@code config.region});
     * redirects to a local emulator instead when {@code target} is {@link DeploymentTarget#LOCALSTACK}/
     * {@link DeploymentTarget#MINISTACK} and {@link ManagerEndpointSupport#resolveLocalEmulatorEndpoint}
     * finds one — see class javadoc. {@code target} must be the caller's own already-known,
     * validated target (never re-derived from env vars here — see that method's own javadoc for
     * why). Equivalent to {@code AwsDirectDeployer(config, target, null)}.
     */
    public AwsDirectDeployer(DeploymentConfig config, DeploymentTarget target) {
        this(config, target, null);
    }

    /**
     * Same as {@link #AwsDirectDeployer(DeploymentConfig, DeploymentTarget)}, but with an
     * explicit credentials provider to use for real-AWS calls instead of the default chain — e.g.
     * a caller that assumed a cross-account IAM role and wants this deployer to act as that
     * principal. {@code null} means "use the default credential chain," exactly {@link
     * #AwsDirectDeployer(DeploymentConfig, DeploymentTarget)}'s only prior behavior.
     *
     * <p>Ignored when {@code target} resolves to a local emulator — a real AssumeRole against
     * LocalStack/MiniStack is meaningless, so the fixed {@code test}/{@code test} static
     * credentials still win in that case, unchanged from before this overload existed.</p>
     */
    public AwsDirectDeployer(
            DeploymentConfig config, DeploymentTarget target, AwsCredentialsProvider credentialsOverride) {
        this(
            cloudFormationClient(config, target, credentialsOverride),
            s3Client(config, target, credentialsOverride),
            config.applicationId,
            runtimeTag(config.runtime),
            // null, not an eagerly-computed name -- resolvedTemplateBucket() fills in the
            // account-scoped name lazily (see its own javadoc for why: same "no network call in
            // the constructor" rule resolveAccountId() already follows).
            null,
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target) != null,
            Region.of(config.region == null ? "us-east-1" : config.region),
            credentialsOverride);
    }

    private static CloudFormationClient cloudFormationClient(
            DeploymentConfig config, DeploymentTarget target, AwsCredentialsProvider credentialsOverride) {
        Region region = Region.of(config.region == null ? "us-east-1" : config.region);
        String localEndpoint = ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target);
        if (localEndpoint == null) {
            return CloudFormationClient.builder()
                .region(region)
                .credentialsProvider(credentialsOverride != null
                    ? credentialsOverride : DefaultCredentialsProvider.create())
                .build();
        }
        // Local emulator always wins, regardless of any credentialsOverride — see
        // AwsDirectDeployer(DeploymentConfig, DeploymentTarget, AwsCredentialsProvider)'s javadoc.
        return CloudFormationClient.builder()
            .region(region)
            .endpointOverride(URI.create(localEndpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    private static S3Client s3Client(
            DeploymentConfig config, DeploymentTarget target, AwsCredentialsProvider credentialsOverride) {
        Region region = Region.of(config.region == null ? "us-east-1" : config.region);
        String localEndpoint = ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target);
        if (localEndpoint == null) {
            return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsOverride != null
                    ? credentialsOverride : DefaultCredentialsProvider.create())
                .build();
        }
        return S3Client.builder()
            .region(region)
            .endpointOverride(URI.create(localEndpoint))
            .forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    /**
     * LocalStack's fixed test account needs no network call; real AWS resolves the caller's
     * actual account via {@code sts:GetCallerIdentity} — needed to substitute CDK's
     * {@code ${AWS::AccountId}} pseudo-parameter token in asset-manifest bucket names (see
     * {@code LocalStackCdkAssetPublisher}'s class javadoc). Deliberately called lazily from
     * {@link #deploy} rather than eagerly in the constructor: constructing a client is not itself
     * a network call for any other client this class builds, and {@code
     * realAwsConstructorDefaultsRegionWhenConfigOmitsIt} in the test suite specifically relies on
     * construction alone never reaching the network — an eager STS call here would have broken
     * that invariant (and made every construction pay real-AWS latency even for a dry run that
     * never actually deploys).
     */
    private String resolveAccountId() {
        if (localEmulatorTarget) {
            return "000000000000";
        }
        try (StsClient sts = StsClient.builder()
                .region(region)
                .credentialsProvider(credentialsOverride != null
                    ? credentialsOverride : DefaultCredentialsProvider.create())
                .build()) {
            return sts.getCallerIdentity().account();
        }
    }

    /**
     * Visible for tests — inject pre-built clients (e.g. pointed at LocalStack, or mocks).
     * Defaults {@code localEmulatorTarget} to {@code false} (no CDK-bootstrap-parameter
     * adaptation) since no existing test deploys a template containing one; use the 7-arg
     * overload to test that behavior, or {@link #resolveAccountId}, specifically.
     */
    AwsDirectDeployer(
            CloudFormationClient cloudFormation,
            S3Client s3,
            String applicationId,
            String runtimeTag,
            String templateBucket) {
        this(cloudFormation, s3, applicationId, runtimeTag, templateBucket, false, Region.US_EAST_1);
    }

    /** Visible for tests — same as the 5-arg overload, with explicit control over adaptation. */
    AwsDirectDeployer(
            CloudFormationClient cloudFormation,
            S3Client s3,
            String applicationId,
            String runtimeTag,
            String templateBucket,
            boolean localEmulatorTarget,
            Region region) {
        this(cloudFormation, s3, applicationId, runtimeTag, templateBucket, localEmulatorTarget, region, null);
    }

    /** Visible for tests — same as the 7-arg overload, with an explicit credentials override
     *  (e.g. asserting a cross-account provider actually reaches {@link #resolveAccountId}). */
    AwsDirectDeployer(
            CloudFormationClient cloudFormation,
            S3Client s3,
            String applicationId,
            String runtimeTag,
            String templateBucket,
            boolean localEmulatorTarget,
            Region region,
            AwsCredentialsProvider credentialsOverride) {
        this.cloudFormation = cloudFormation;
        this.s3 = s3;
        this.applicationId = applicationId;
        this.runtimeTag = runtimeTag;
        this.templateBucket = templateBucket;
        this.localEmulatorTarget = localEmulatorTarget;
        this.region = region;
        this.credentialsOverride = credentialsOverride;
    }

    static String runtimeTag(RuntimeType runtime) {
        return runtime == null ? "unknown" : runtime.name().toLowerCase(Locale.ROOT);
    }

    /**
     * S3 bucket names are globally unique across every AWS account, not just this one — the
     * previous {@code "cfc-cfn-templates-" + region} name (no account ID) collided with whatever
     * account anywhere happened to have claimed it first, and every {@code headBucket}/
     * {@code createBucket}/{@code putObject} call against a bucket this account doesn't own comes
     * back 403 Access Denied, not a friendlier "already exists" error. {@code accountId} makes
     * the name as unique as CDK's own bootstrap bucket convention ({@code
     * cdk-hnb659fds-assets-<account>-<region>}) already relies on.
     */
    static String templateBucketName(String accountId, String region) {
        return TEMPLATE_BUCKET_PREFIX + accountId + "-"
            + (region == null || region.isBlank() ? "us-east-1" : region);
    }

    /**
     * The actual CloudFormation stack name every AWS/S3 API call in this class uses —
     * {@code stackName} plus a {@code -localstack} suffix when targeting a local emulator, the
     * exact same convention {@code LocalStackDeployer}'s caller already applies for its own
     * deploys. {@code StackListingPolicy.acceptsName} requires that suffix for a stack to appear
     * under Manager's LocalStack target view — without it, a {@code deploy:create} stack
     * redirected to a local emulator (see class javadoc) deploys successfully on CloudFormation's
     * side but stays invisible in both Manager's Instances list and its Catalog list. Every
     * public method on this class still deals exclusively in the
     * logical, unsuffixed name — both what callers pass in and what
     * {@link AwsStackDeployResult#stackName()} reports back — this conversion is applied once,
     * internally, and never leaks out.
     */
    String physicalStackName(String stackName) {
        return localEmulatorTarget ? stackName + "-localstack" : stackName;
    }

    /** The three CloudForge stack tags applied to every {@code CreateStack}/{@code UpdateStack} call. */
    List<Tag> managedTags() {
        return List.of(
            Tag.builder().key(TAG_MANAGED).value("true").build(),
            Tag.builder().key(TAG_APPLICATION).value(applicationId == null ? "unknown" : applicationId).build(),
            Tag.builder().key(TAG_RUNTIME).value(runtimeTag).build());
    }

    /**
     * Creates or updates {@code stackName} from {@code template} via a change set. No-op
     * (returns {@code noOp = true}) when the stack already matches the candidate template.
     */
    public AwsStackDeployResult deploy(String stackName, Path template) throws IOException {
        String physical = physicalStackName(stackName);
        // stackName here (not physical) deliberately — the manifest file is named after the CDK
        // construct id CloudForgeSynthesizer actually synthesized with, which is always the
        // logical name; only the CloudFormation-facing stack name below gets suffixed.
        //
        // Unconditional — real cdk deploy always publishes assets before its own CloudFormation
        // call too, for real AWS as much as a local emulator. Without this, any asset-backed
        // resource (including aws-cdk-lib's own LogRetention custom resource) fails the instant a
        // real deploy reaches it, since nothing published its asset anywhere first.
        //
        // createBucketIfMissing = localEmulatorTarget: a local emulator has no separate "cdk
        // bootstrap" step, so self-creating the asset bucket on first use IS its bootstrap. Real
        // AWS must not self-create it — that bucket is CDK's own bootstrap-owned resource, and a
        // bare, untracked bucket with its exact name permanently blocks the real `cdk bootstrap`
        // from ever creating its own properly-configured copy. See this method's own IOException
        // (surfaced as an actionable "run cdk bootstrap" message) when the account isn't
        // bootstrapped yet.
        LocalStackCdkAssetPublisher.publish(
            template.getParent(), stackName, s3, resolveAccountId(), localEmulatorTarget);

        boolean exists = stackExists(physical) && stackIsDeployable(physical);
        String templateBody = Files.readString(template);
        if (localEmulatorTarget) {
            templateBody = resolveCdkBootstrapParameters(templateBody);
        }

        if (exists && templateMatches(physical, templateBody)) {
            return new AwsStackDeployResult(stackName, false, true, List.of(), outputs(physical));
        }

        String changeSetName = "cfc-" + UUID.randomUUID().toString().substring(0, 8);
        CreateChangeSetRequest.Builder changeSetBuilder = CreateChangeSetRequest.builder()
            .stackName(physical)
            .changeSetName(changeSetName)
            .changeSetType(exists ? ChangeSetType.UPDATE : ChangeSetType.CREATE)
            .capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
            .tags(managedTags());

        if (templateBody.getBytes(StandardCharsets.UTF_8).length > MAX_INLINE_TEMPLATE_BYTES) {
            changeSetBuilder.templateURL(uploadTemplateToS3(physical, templateBody));
        } else {
            changeSetBuilder.templateBody(templateBody);
        }

        cloudFormation.createChangeSet(changeSetBuilder.build());

        var changeSet = waitForChangeSet(physical, changeSetName);
        if (changeSet.status() == ChangeSetStatus.FAILED) {
            String reason = changeSet.statusReason() == null ? "" : changeSet.statusReason();
            deleteChangeSet(physical, changeSetName);
            if (isNoOp(reason)) {
                return new AwsStackDeployResult(stackName, false, true, List.of(), outputs(physical));
            }
            throw new IOException("AWS change set failed for " + physical + ": " + reason);
        }

        List<String> changeSummaries = changeSet.changes().stream()
            .map(change -> summarize(change.resourceChange()))
            .toList();

        cloudFormation.executeChangeSet(ExecuteChangeSetRequest.builder()
            .stackName(physical)
            .changeSetName(changeSetName)
            .build());

        var request = DescribeStacksRequest.builder().stackName(physical).build();
        try {
            if (localEmulatorTarget) {
                WaiterOverrideConfiguration override = WaiterOverrideConfiguration.builder()
                    .waitTimeout(LOCAL_EMULATOR_WAITER_TIMEOUT)
                    .build();
                if (exists) {
                    cloudFormation.waiter().waitUntilStackUpdateComplete(request, override);
                } else {
                    cloudFormation.waiter().waitUntilStackCreateComplete(request, override);
                }
            } else if (exists) {
                cloudFormation.waiter().waitUntilStackUpdateComplete(request);
            } else {
                cloudFormation.waiter().waitUntilStackCreateComplete(request);
            }
        } catch (Exception e) {
            throw new IOException(
                "AWS deployment failed for " + physical + ":\n" + recentEvents(physical), e);
        }

        return new AwsStackDeployResult(
            stackName, !exists, false, changeSummaries, outputs(physical));
    }

    public void delete(String stackName) throws IOException {
        stackName = physicalStackName(stackName);
        if (!stackExists(stackName)) {
            return;
        }
        try {
            cloudFormation.deleteStack(DeleteStackRequest.builder().stackName(stackName).build());
            cloudFormation.waiter().waitUntilStackDeleteComplete(
                DescribeStacksRequest.builder().stackName(stackName).build());
        } catch (Exception e) {
            if (stackExists(stackName)) {
                throw new IOException("Failed to delete AWS stack " + stackName, e);
            }
        }
    }

    public boolean stackExists(String stackName) {
        try {
            var stacks = cloudFormation.describeStacks(
                    DescribeStacksRequest.builder().stackName(stackName).build())
                .stacks();
            if (stacks == null || stacks.isEmpty()) {
                return false;
            }
            String status = stacks.getFirst().stackStatusAsString();
            // DELETE_COMPLETE (and in-progress deletes) must not short-circuit redeploy.
            return status == null || !status.toUpperCase(Locale.ROOT).contains("DELETE");
        } catch (CloudFormationException e) {
            if (e.statusCode() == 400 || e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public Map<String, String> outputs(String stackName) {
        List<Output> stackOutputs = cloudFormation.describeStacks(
                DescribeStacksRequest.builder().stackName(stackName).build())
            .stacks().getFirst().outputs();
        Map<String, String> result = new LinkedHashMap<>();
        for (Output output : stackOutputs) {
            result.put(output.outputKey(), output.outputValue());
        }
        return Map.copyOf(result);
    }

    /**
     * Confirm the stack exists and return its outputs.
     *
     * @throws IOException when the stack is missing or outputs cannot be read
     */
    public Map<String, String> verifyDeployment(String stackName) throws IOException {
        String physical = physicalStackName(stackName);
        if (!stackExists(physical)) {
            throw new IOException("Stack does not exist: " + stackName);
        }
        return outputs(physical);
    }

    private boolean stackIsDeployable(String stackName) {
        try {
            String status = cloudFormation.describeStacks(
                    DescribeStacksRequest.builder().stackName(stackName).build())
                .stacks().getFirst().stackStatusAsString();
            if (status == null) {
                return false;
            }
            String normalized = status.toUpperCase(Locale.ROOT);
            return normalized.contains("COMPLETE") && !normalized.contains("ROLLBACK");
        } catch (CloudFormationException e) {
            return false;
        }
    }

    /**
     * CDK injects {@code BootstrapVersion} as {@code AWS::SSM::Parameter::Value<String>}, whose
     * default resolves dynamically from {@code /cdk-bootstrap/hnb659fds/version} in SSM Parameter
     * Store — populated by a real {@code cdk bootstrap} run, which nothing in this deploy path
     * ever performs. A real, bootstrapped AWS account has that path unrelated to this deployer;
     * a local emulator's account never does, and CloudFormation rejects the dynamic reference
     * outright: {@code "Parameter BootstrapVersion should either have input value or default
     * value"} — hit for real the first time this ran against LocalStack after the endpoint-
     * override fix above got far enough to actually talk to it. Same rewrite
     * {@code LocalStackTemplateAdapter.resolveCdkBootstrapParameters} already applies on the
     * {@code deploy:catalog}-adjacent local-target path — duplicated here rather than shared
     * because {@code cloudforge-api} cannot depend on {@code cloudforge-localstack} (the
     * dependency runs the other way), and this is the only piece of that adapter this class
     * needs. Only ever called when {@link #localEmulatorTarget} is true — real AWS keeps its
     * template completely untouched.
     */
    static String resolveCdkBootstrapParameters(String templateBody) throws IOException {
        JsonNode root = MAPPER.readTree(templateBody);
        if (!(root instanceof ObjectNode template)) {
            return templateBody;
        }
        JsonNode parametersNode = template.get("Parameters");
        if (!(parametersNode instanceof ObjectNode parameters)) {
            return templateBody;
        }
        boolean changed = false;
        var fields = parameters.properties().iterator();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode parameterNode = entry.getValue();
            if (!(parameterNode instanceof ObjectNode parameter)) {
                continue;
            }
            String type = parameter.path("Type").asText();
            if (type == null || !type.startsWith("AWS::SSM::Parameter::Value")) {
                continue;
            }
            String defaultValue = parameter.path("Default").asText(null);
            String resolved = (defaultValue != null && defaultValue.startsWith("/"))
                ? "21"
                : (defaultValue == null || defaultValue.isBlank() ? "21" : defaultValue);
            parameter.put("Type", "String");
            parameter.put("Default", resolved);
            changed = true;
        }
        return changed ? MAPPER.writeValueAsString(template) : templateBody;
    }

    private boolean templateMatches(String stackName, String candidate) throws IOException {
        try {
            String deployed = cloudFormation.getTemplate(
                GetTemplateRequest.builder().stackName(stackName).build()).templateBody();
            if (deployed == null || deployed.isBlank()) {
                return false;
            }
            return MAPPER.readTree(deployed).equals(MAPPER.readTree(candidate));
        } catch (CloudFormationException e) {
            // Incomplete / missing template — force a real create/update path
            return false;
        } catch (Exception e) {
            throw new IOException("Unable to compare deployed AWS template", e);
        }
    }

    private software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse
            waitForChangeSet(String stackName, String changeSetName) throws IOException {
        Instant deadline = Instant.now().plus(OPERATION_TIMEOUT);
        DescribeChangeSetRequest request = DescribeChangeSetRequest.builder()
            .stackName(stackName)
            .changeSetName(changeSetName)
            .build();
        while (Instant.now().isBefore(deadline)) {
            var response = cloudFormation.describeChangeSet(request);
            if (response.status() == ChangeSetStatus.CREATE_COMPLETE
                    || response.status() == ChangeSetStatus.FAILED) {
                return response;
            }
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for AWS change set", e);
            }
        }
        throw new IOException("Timed out waiting for AWS change set " + changeSetName);
    }

    private void deleteChangeSet(String stackName, String changeSetName) {
        cloudFormation.deleteChangeSet(DeleteChangeSetRequest.builder()
            .stackName(stackName)
            .changeSetName(changeSetName)
            .build());
    }

    private String recentEvents(String stackName) {
        try {
            StringBuilder summary = new StringBuilder();
            var events = cloudFormation.describeStackEvents(
                    DescribeStackEventsRequest.builder().stackName(stackName).build())
                .stackEvents();

            events.stream()
                .filter(event -> event.resourceStatusAsString() != null
                    && (event.resourceStatusAsString().contains("FAILED")
                        || event.resourceStatusAsString().contains("ROLLBACK")))
                .limit(5)
                .forEach(event -> summary.append(formatEvent(event)).append('\n'));

            if (summary.isEmpty()) {
                events.stream().limit(15).forEach(event ->
                    summary.append(formatEvent(event)).append('\n'));
            } else {
                summary.append("\nRecent events:\n");
                events.stream().limit(10).forEach(event ->
                    summary.append(formatEvent(event)).append('\n'));
            }
            return summary.toString();
        } catch (Exception e) {
            return "Unable to read stack events: " + e.getMessage();
        }
    }

    private static String formatEvent(StackEvent event) {
        return "%s %s %s %s".formatted(
            event.resourceStatusAsString(),
            event.resourceType(),
            event.logicalResourceId(),
            event.resourceStatusReason() == null ? "" : event.resourceStatusReason());
    }

    private static String summarize(ResourceChange change) {
        return "%s %s (%s)%s".formatted(
            change.actionAsString(),
            change.logicalResourceId(),
            change.resourceType(),
            change.replacementAsString() == null ? "" : " replacement=" + change.replacementAsString());
    }

    private String uploadTemplateToS3(String stackName, String templateBody) throws IOException {
        String bucket = resolvedTemplateBucket();
        ensureTemplateBucket(bucket);
        String key = stackName + ".template.json";
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .build(),
            RequestBody.fromString(templateBody, StandardCharsets.UTF_8));
        return s3.utilities().getUrl(builder -> builder.bucket(bucket).key(key)).toString();
    }

    /**
     * The production constructor leaves {@link #templateBucket} {@code null} rather than eagerly
     * computing a name at construction time — same "no network call in the constructor" rule
     * {@link #resolveAccountId} already documents, since the caller's own account ID is now part
     * of the name (see that method's own account-ID-collision fix). Test-visible constructors that
     * inject an explicit bucket name still win outright, unchanged.
     */
    private String resolvedTemplateBucket() {
        return templateBucket != null ? templateBucket : templateBucketName(resolveAccountId(), region.id());
    }

    private void ensureTemplateBucket(String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ignored) {
                // concurrent create
            }
        }
    }

    private static boolean isNoOp(String reason) {
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("didn't contain changes")
            || normalized.contains("no updates")
            || normalized.contains("no changes");
    }

    @Override
    public void close() {
        cloudFormation.close();
        s3.close();
    }
}
