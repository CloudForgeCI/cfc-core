package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.RuntimeType;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.iam.AnyPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;
import io.github.cdklabs.cdknag.NagSuppressions;
import io.github.cdklabs.cdknag.NagPackSuppression;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ALB Factory using annotation-based context injection.
 * This demonstrates the cleaner approach without passing SystemContext as parameters.
 */
public class AlbFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(AlbFactory.class.getName());

    @SystemContext("runtime")
    private RuntimeType runtime;

    @SystemContext("vpc")
    private Vpc vpc;

    @DeploymentContext("healthCheckInterval")
    private Integer healthCheckInterval;

    @DeploymentContext("healthCheckTimeout")
    private Integer healthCheckTimeout;

    @DeploymentContext("healthyThreshold")
    private Integer healthyThreshold;

    @DeploymentContext("unhealthyThreshold")
    private Integer unhealthyThreshold;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    @DeploymentContext("albAccessLogging")
    private Boolean albAccessLogging;

    @DeploymentContext("region")
    private String region;

    @DeploymentContext("stackName")
    private String stackName;

    /**
     * Writes one SSM parameter (Name/Value/Type/Description from {@code ResourceProperties},
     * fixed physical resource ID) and does nothing on delete. Hand-rolled rather than {@code
     * AwsCustomResource}/{@code Provider}: both of those wrap CDK's own bundled framework Lambda,
     * whose code CloudFormation stages to the deployer's private {@code cdk-hnb659fds-assets}
     * bootstrap bucket -- fine for our own deploys, but unusable by an AWS Marketplace buyer
     * launching this template directly in their own, unbootstrapped account. {@link Code#fromInline}
     * embeds this source directly in the template (subject to Lambda's 4096-character {@code
     * ZipFile} limit), so no S3 reference is emitted at all. Implements the raw CloudFormation
     * custom-resource protocol itself (the presigned-URL PUT callback) rather than relying on
     * any CDK-provided response helper, for the same reason.
     */
    private static final String SSM_PARAMETER_WRITER_SOURCE = """
        const https = require('https');
        const { SSMClient, PutParameterCommand } = require('@aws-sdk/client-ssm');

        function respond(event, status, reason) {
          return new Promise((resolve, reject) => {
            const body = JSON.stringify({
              Status: status,
              Reason: reason || 'See the function\\'s own CloudWatch Logs group for details.',
              PhysicalResourceId: 'AlbLogsBucket-SSMWriter',
              StackId: event.StackId,
              RequestId: event.RequestId,
              LogicalResourceId: event.LogicalResourceId,
              Data: {}
            });
            const url = new URL(event.ResponseURL);
            const req = https.request({
              hostname: url.hostname,
              path: url.pathname + url.search,
              method: 'PUT',
              headers: { 'content-type': '', 'content-length': Buffer.byteLength(body) },
              timeout: 10000
            }, (res) => {
              res.on('data', () => {});
              res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                  resolve();
                } else {
                  reject(new Error('ResponseURL PUT failed with HTTP status ' + res.statusCode));
                }
              });
            });
            req.on('timeout', () => req.destroy(new Error('ResponseURL PUT timed out')));
            req.on('error', reject);
            req.write(body);
            req.end();
          });
        }

        exports.handler = async (event) => {
          try {
            if (event.RequestType !== 'Delete') {
              const p = event.ResourceProperties;
              const client = new SSMClient({ region: p.Region });
              await client.send(new PutParameterCommand({
                Name: p.ParameterName,
                Value: p.ParameterValue,
                Type: 'String',
                Description: p.ParameterDescription,
                Overwrite: true
              }));
            }
            await respond(event, 'SUCCESS');
          } catch (err) {
            console.error('AlbLogsBucketSSMWriter failed: ' + err);
            try {
              await respond(event, 'FAILED', String(err));
            } catch (deliveryErr) {
              // Nothing further to do: the ResponseURL PUT itself is what just failed, and
              // it's the only channel this protocol has to reach CloudFormation. Logged so
              // it's at least visible in CloudWatch instead of a silently-hung stack operation.
              console.error('Could not deliver FAILED response to CloudFormation: ' + deliveryErr);
            }
          }
        };
        """;

    @SystemContext("securityProfileConfig")
    private com.cloudforgeci.api.interfaces.SecurityProfileConfiguration securityProfileConfig;

    @SystemContext("applicationSpec")
    private com.cloudforge.core.interfaces.ApplicationSpec applicationSpec;

    public AlbFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        if (ctx == null) {
            throw new IllegalStateException("SystemContext is null in AlbFactory.create()");
        }

        try {
            // Get compliance settings from security profile (injected via annotation)
            if (securityProfileConfig != null && albAccessLogging == null) {
                // Use security profile setting if not explicitly configured in deployment context
                albAccessLogging = securityProfileConfig.isAlbAccessLoggingEnabled();
                LOG.info("ALB access logging inherited from security profile: " + albAccessLogging);
            }

            // Create security group
            SecurityGroup albSg = createSecurityGroup();
            ctx.albSg.set(albSg);

            // Create ALB
            ApplicationLoadBalancer alb = createLoadBalancer(albSg);
            ctx.alb.set(alb);

            // Check if HTTPS strict mode is enabled (skip HTTP listener entirely for compliance)
            boolean httpsStrict = config != null && config.isHttpsStrictEnabled();
            boolean sslEnabled = Boolean.TRUE.equals(enableSsl);

            if (httpsStrict && sslEnabled) {
                // HTTPS strict mode: No HTTP listener for PCI-DSS compliance (Req 4.1)
                // Target group wiring will be handled by RuntimeConfiguration after HTTPS listener is created
                LOG.info("HTTPS strict mode enabled: Skipping HTTP listener (port 80) for compliance");
                LOG.info("  Target group wiring will be handled by RuntimeConfiguration");
                // ctx.http remains empty - HTTPS listener will be created by RuntimeConfiguration
            } else {
                // Create HTTP listener with placeholder default action
                // The target group will be created by orchestration layer and added to listener later
                ApplicationListener http = createFargateHttpListener(alb, sslEnabled);
                ctx.http.set(http);
            }

        } catch (Exception e) {
            LOG.severe("Exception in AlbFactory.create(): " + e.getMessage());
            throw e;
        }
    }

    private SecurityGroup createSecurityGroup() {
        // Check if egress should be restricted to VPC CIDR only
        boolean restrictEgress = config.isRestrictSecurityGroupEgressEnabled();

        SecurityGroup sg = SecurityGroup.Builder.create(this, "AlbSg")
                .vpc(vpc)
                .description("ALB Security Group")
                .allowAllOutbound(!restrictEgress)
                .build();

        // If egress is restricted, add explicit egress rule for VPC CIDR only
        // ALB only needs to communicate with backend targets within VPC
        if (restrictEgress) {
            sg.addEgressRule(
                Peer.ipv4(vpc.getVpcCidrBlock()),
                Port.allTraffic(),
                "Allow egress to VPC CIDR only (backend targets)"
            );
        }

        // Suppress EC23 for ALB security group - public-facing load balancer requires open ingress
        NagSuppressions.addResourceSuppressions(
            sg,
            List.of(
                NagPackSuppression.builder()
                    .id("AwsSolutions-EC23")
                    .reason("ALB security group requires open ingress on ports 80/443 for public-facing " +
                            "load balancer. This is the intended architecture for serving web applications " +
                            "to internet users. Backend instances are protected in private subnets.")
                    .build()
            ),
            Boolean.TRUE
        );

        return sg;
    }

    private ApplicationLoadBalancer createLoadBalancer(SecurityGroup albSg) {
        // Enable access logging for compliance if configured
        if (Boolean.TRUE.equals(albAccessLogging)) {
            // Get region from deployment context
            final String effectiveRegion = (region != null && !region.isEmpty()) ? region : "us-east-1";

            // Validate required fields for ALB access logging
            String validationError = validateLoggingPrerequisites(effectiveRegion, stackName);
            if (validationError != null) {
                LOG.warning("ALB access logging enabled but prerequisites not met: " + validationError);
                return createAlbWithoutLogging(albSg);
            }

            // Get stack account for bucket naming (captured in lambda)
            final String accountId = Stack.of(this).getAccount();

            // Use Lazy.uncachedString() to defer bucket name construction until synthesis time
            String bucketName = Lazy.uncachedString(
                    new IStringProducer() {
                        @Override
                        public String produce(IResolveContext context) {
                            // STACK-SPECIFIC bucket name to avoid conflicts between stacks.
                            //
                            // Lowercase only the literal parts. Lowercasing the whole string would
                            // corrupt the embedded accountId token marker (e.g. "${Token[AWS.AccountId.7]}"
                            // -> "${token[aws.accountid.7]}"), which CDK then emits verbatim as an invalid
                            // bucket name. Account IDs are numeric, so they need no lowercasing.
                            return stackName.toLowerCase() + "-alb-logs-" + accountId + "-" + effectiveRegion.toLowerCase();
                        }
                    },
                    LazyStringValueOptions.builder()
                            .displayHint(stackName + "-alb-logs-bucket")
                            .build()
            );

            LOG.info("ALB logs bucket name (stack-specific): " + bucketName);

            // Determine removal policy based on security profile (injected via annotation)
            boolean isProduction = (securityProfileConfig != null && securityProfileConfig.getClass().getSimpleName().contains("Production"));
            RemovalPolicy removalPolicy = isProduction ?
                    RemovalPolicy.RETAIN :
                    RemovalPolicy.DESTROY;

            // Get or create ALB logs bucket with SSM tracking (PRODUCTION only)
            IBucket logBucket = getOrCreateAlbLogsBucketWithSSM(
                bucketName,
                removalPolicy,
                isProduction,
                effectiveRegion
            );

            String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
            String albId = Character.toUpperCase(appId.charAt(0)) + appId.substring(1) + "Alb";

            ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, albId)
                    .vpc(vpc)
                    .securityGroup(albSg)
                    .internetFacing(true)
                    .deletionProtection(shouldEnableDeletionProtection())
                    .build();

            // Enable security compliance settings
            configureAlbSecurity(alb);

            // Enable access logs on the created ALB
            alb.logAccessLogs(logBucket);

            // Note: SSL enforcement policy is added in getOrCreateAlbLogsBucketWithSSM but
            // CDK's logAccessLogs() creates a bucket policy that doesn't merge with addToResourcePolicy()
            // statements. The ALB access logs bucket is only written by AWS ELB service via internal
            // HTTPS connections, and has blockPublicAccess enabled. See PCI-DSS suppression in InteractiveDeployer.

            // Register AWS Config rules for ALB access logging compliance
            ctx.requireConfigRule(AwsConfigRule.ELB_LOGGING_ENABLED);

            LOG.info("ALB access logging enabled");
            LOG.info("  S3 Bucket: " + logBucket.getBucketName());
            LOG.info("  Retention: 6 years (2190 days)");
            LOG.info("  Lifecycle: Glacier (90d), Deep Archive (1y), Delete (6y)");
            LOG.info("  Encryption: S3-managed (SSE-S3)");
            LOG.info("  Drop invalid HTTP headers: enabled (compliance)");

            return alb;
        } else {
            LOG.info("ALB access logging is disabled");
            LOG.info("  Note: Access logs may be required for audit and compliance frameworks");
            LOG.info("  Enable by setting albAccessLogging = true or using PRODUCTION/STAGING security profile");

            return createAlbWithoutLogging(albSg);
        }
    }

    private ApplicationLoadBalancer createAlbWithoutLogging(SecurityGroup albSg) {
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
        String albId = Character.toUpperCase(appId.charAt(0)) + appId.substring(1) + "Alb";

        ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, albId)
                .vpc(vpc)
                .securityGroup(albSg)
                .internetFacing(true)
                .deletionProtection(shouldEnableDeletionProtection())
                .build();

        // Enable security compliance settings
        configureAlbSecurity(alb);

        // Add suppression for ELB2 when access logs are disabled (DEV profile cost optimization)
        NagSuppressions.addResourceSuppressions(
            alb,
            List.of(
                NagPackSuppression.builder()
                    .id("AwsSolutions-ELB2")
                    .reason("ALB access logging is intentionally disabled for DEV/non-production " +
                            "environments to reduce S3 storage costs. Production deployments enable " +
                            "access logging for audit compliance and security monitoring.")
                    .build()
            ),
            Boolean.TRUE
        );

        return alb;
    }

    /**
     * Determine if deletion protection should be enabled based on security profile.
     * Production security profiles enable deletion protection by default.
     */
    private boolean shouldEnableDeletionProtection() {
        if (securityProfileConfig == null) {
            return false; // Default to disabled if no security profile
        }

        if (securityProfileConfig.isAlbDeletionProtectionEnabled()) {
            // Register AWS Config rule for deletion protection compliance
            ctx.requireConfigRule(AwsConfigRule.ELB_DELETION_PROTECTION);
            LOG.info("ALB deletion protection: ENABLED (" + securityProfileConfig.getClass().getSimpleName() + ")");
            return true;
        }
        LOG.info("ALB deletion protection: DISABLED (" + securityProfileConfig.getClass().getSimpleName() + ")");
        return false;
    }

    /**
     * Configure ALB security settings for compliance (SOC2, PCI-DSS).
     * Enables dropping invalid HTTP headers to prevent header injection attacks.
     */
    private void configureAlbSecurity(ApplicationLoadBalancer alb) {
        // setAttribute adds to the attribute list; a raw property override would replace it and
        // drop deletion protection and access logging.
        alb.setAttribute("routing.http.drop_invalid_header_fields.enabled", "true");
        LOG.info("Drop invalid HTTP headers: enabled (compliance)");
    }

    /**
     * Get or create ALB logs bucket with SSM tracking for PRODUCTION mode.
     *
     * For PRODUCTION mode:
     * - Create bucket WITHOUT explicit name (CloudFormation generates unique name)
     * - Store ARN in SSM at deployment time for tracking
     * - This prevents conflicts with retained buckets
     *
     * For DEV/STAGING mode:
     * - Create bucket with explicit name (will be destroyed with stack)
     */
    private IBucket getOrCreateAlbLogsBucketWithSSM(String bucketName, RemovalPolicy removalPolicy,
                                                     boolean isProduction, String region) {
        if (!isProduction) {
            // DEV/STAGING: Create bucket without SSM tracking (will be deleted with stack)
            // autoDeleteObjects=true ensures bucket contents are emptied before deletion
            LOG.info("Non-production mode: Creating ALB logs bucket without SSM tracking (auto-delete enabled)");
            return createAlbLogsBucket(bucketName, removalPolicy, true);
        }

        // PRODUCTION: Create bucket WITHOUT explicit name and track ARN in SSM
        LOG.info("Production mode: Creating ALB logs bucket with auto-generated name");
        LOG.info("  CloudFormation will generate unique bucket name to avoid conflicts");

        // Create bucket WITHOUT specifying bucketName - CloudFormation generates unique name
        Bucket newBucket = Bucket.Builder.create(this, "AlbLogsBucket")
                // NO bucketName specified - CloudFormation auto-generates unique name
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(removalPolicy)
                .autoDeleteObjects(false)  // Never auto-delete in PRODUCTION
                .versioned(true)
                .lifecycleRules(List.of(
                    LifecycleRule.builder()
                        .transitions(List.of(
                            Transition.builder()
                                .storageClass(StorageClass.GLACIER)
                                .transitionAfter(Duration.days(90))
                                .build(),
                            Transition.builder()
                                .storageClass(StorageClass.DEEP_ARCHIVE)
                                .transitionAfter(Duration.days(365))
                                .build()
                        ))
                        .expiration(Duration.days(2190))
                        .build()
                ))
                .build();

        // Enforce SSL for all S3 requests (PCI-DSS Req 4.1)
        newBucket.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("DenyInsecureTransport")
                .effect(Effect.DENY)
                .principals(List.of(new AnyPrincipal()))
                .actions(List.of("s3:*"))
                .resources(List.of(
                        newBucket.getBucketArn(),
                        newBucket.arnForObjects("*")
                ))
                .conditions(java.util.Map.of(
                        "Bool", java.util.Map.of("aws:SecureTransport", "false")
                ))
                .build());
        // Note: This SSL policy is added but may not appear in synthesized CloudFormation
        // due to CDK limitation where logAccessLogs() bucket policy doesn't merge with
        // addToResourcePolicy() statements. See PCI-DSS suppression in InteractiveDeployer.

        // Add NagSuppressions for CDK limitations and justified architecture decisions
        NagSuppressions.addResourceSuppressions(
            newBucket,
            List.of(
                NagPackSuppression.builder()
                    .id("AwsSolutions-S1")
                    .reason("ALB access logs bucket receives logs from ALB. Server access logging " +
                           "would create circular dependency. CloudTrail S3 data events provide audit logging.")
                    .build(),
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3BucketReplicationEnabled")
                    .reason("S3 replication is not required for single-region deployments. " +
                           "ALB access logs are retained with versioning enabled for compliance.")
                    .build(),
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3BucketLoggingEnabled")
                    .reason("ALB access logs bucket receives logs from ALB. Server access logging " +
                           "would create circular dependency. CloudTrail S3 data events provide audit logging.")
                    .build(),
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3DefaultEncryptionKMS")
                    .reason("ALB access logging requires S3-managed encryption. KMS encryption is not " +
                           "supported for ALB access logs due to AWS service limitations.")
                    .build(),
                // Same three findings, flagged again under HIPAA's own rule IDs when HIPAA is one
                // of the active compliance frameworks -- same justification as the PCI-DSS
                // suppressions above, since the underlying architecture doesn't change per framework.
                NagPackSuppression.builder()
                    .id("HIPAA.Security-S3BucketReplicationEnabled")
                    .reason("S3 replication is not required for single-region deployments. " +
                           "ALB access logs are retained with versioning enabled for compliance.")
                    .build(),
                NagPackSuppression.builder()
                    .id("HIPAA.Security-S3BucketLoggingEnabled")
                    .reason("ALB access logs bucket receives logs from ALB. Server access logging " +
                           "would create circular dependency. CloudTrail S3 data events provide audit logging.")
                    .build(),
                NagPackSuppression.builder()
                    .id("HIPAA.Security-S3DefaultEncryptionKMS")
                    .reason("ALB access logging requires S3-managed encryption. KMS encryption is not " +
                           "supported for ALB access logs due to AWS service limitations.")
                    .build()
            ),
            Boolean.TRUE
        );

        // Store bucket ARN in SSM at deployment time using Custom Resource (stack-scoped)
        String ssmParameterName = "/cloudforge/shared/" + region + "/stack/" + this.stackName + "/alb-logs/bucket-arn";
        // Scope the IAM policy to the exact parameter rather than resources: ["*"]
        // Partition-aware (not a hardcoded "arn:aws" literal) — a literal breaks this custom
        // resource's policy outside the standard AWS partition (GovCloud's "arn:aws-us-gov",
        // China's "arn:aws-cn"), which this project's own compliance/FedRAMP-adjacent posture
        // makes a plausible real target even though this codebase doesn't deploy there today.
        String ssmParameterArn = "arn:" + Stack.of(this).getPartition() + ":ssm:" + region + ":"
            + Stack.of(this).getAccount() + ":parameter" + ssmParameterName;

        Function ssmWriterFn = Function.Builder.create(this, "AlbLogsBucketSSMWriterFn")
                .runtime(Runtime.NODEJS_20_X)
                .handler("index.handler")
                .code(Code.fromInline(SSM_PARAMETER_WRITER_SOURCE))
                .timeout(Duration.seconds(30))
                .build();

        ssmWriterFn.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("ssm:PutParameter"))
                .resources(List.of(ssmParameterArn))
                .build());

        CustomResource ssmWriter = CustomResource.Builder.create(this, "AlbLogsBucketSSMWriter")
                .serviceToken(ssmWriterFn.getFunctionArn())
                .properties(Map.of(
                        "ParameterName", ssmParameterName,
                        "ParameterValue", newBucket.getBucketArn(),
                        "ParameterDescription", "CloudForge retained ALB logs bucket ARN for region " + region,
                        "Region", region
                ))
                .build();

        // Add NagSuppressions for this hand-rolled custom resource's own shape
        NagSuppressions.addResourceSuppressions(
            ssmWriterFn,
            List.of(
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-IAMNoInlinePolicy")
                    .reason("This Lambda's own execution policy is scoped to a single " +
                           "ssm:PutParameter call on one parameter ARN -- an inline policy is the " +
                           "right shape for a permission this narrow.")
                    .build(),
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-LambdaInsideVPC")
                    .reason("This custom resource Lambda only makes an SSM API call and does not " +
                           "require VPC access.")
                    .build()
            ),
            Boolean.TRUE
        );

        ssmWriter.getNode().addDependency(newBucket);

        LOG.info("ALB logs bucket will use CloudFormation-generated unique name");
        LOG.info("ALB logs bucket ARN will be stored in SSM: " + ssmParameterName);
        return newBucket;
    }

    /**
     * Create ALB logs S3 bucket with compliance-driven lifecycle rules.
     */
    private Bucket createAlbLogsBucket(String bucketName, RemovalPolicy removalPolicy, boolean autoDelete) {
        Bucket bucket = Bucket.Builder.create(this, "AlbLogsBucket")
                .bucketName(bucketName)
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(removalPolicy)
                .autoDeleteObjects(autoDelete)
                .versioned(true)  // Enable versioning for compliance (SOC2/PCI-DSS/HIPAA)
                .lifecycleRules(List.of(
                    LifecycleRule.builder()
                        .transitions(List.of(
                            Transition.builder()
                                .storageClass(StorageClass.GLACIER)
                                .transitionAfter(Duration.days(90))
                                .build(),
                            Transition.builder()
                                .storageClass(StorageClass.DEEP_ARCHIVE)
                                .transitionAfter(Duration.days(365))
                                .build()
                        ))
                        .expiration(Duration.days(2190))
                        .build()
                ))
                .build();

        // Enforce SSL for all S3 requests (PCI-DSS Req 4.1)
        bucket.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("DenyInsecureTransport")
                .effect(Effect.DENY)
                .principals(List.of(new AnyPrincipal()))
                .actions(List.of("s3:*"))
                .resources(List.of(
                        bucket.getBucketArn(),
                        bucket.arnForObjects("*")
                ))
                .conditions(java.util.Map.of(
                        "Bool", java.util.Map.of("aws:SecureTransport", "false")
                ))
                .build());
        LOG.info("  SSL enforcement policy added to bucket");

        return bucket;
    }

    /**
     * Validate prerequisites for ALB access logging.
     *
     * @param region The AWS region (must not be null, empty, or contain CDK tokens)
     * @param stackName The stack name (must not be null or empty)
     * @return Error message if validation fails, null if validation passes
     */
    private String validateLoggingPrerequisites(String region, String stackName) {
        if (region == null || region.isEmpty() || region.contains("$")) {
            return "Region is not available. Set 'region' in deployment context or CDK_DEFAULT_REGION environment variable";
        }
        if (stackName == null || stackName.isEmpty()) {
            return "Stack name is not set. Set 'stackName' in deployment context to enable ALB access logging";
        }
        return null; // Validation passed
    }

    // codeql[java/unused-parameter] -- alb is unused: a target group isn't scoped to a specific
    // ALB at creation (it's attached later via a listener rule); kept for signature symmetry with
    // the listener-creation methods below, which do need it.
    private ApplicationTargetGroup createTargetGroup(ApplicationLoadBalancer alb) {
        // Use configurable health check settings from annotated fields
        int interval = healthCheckInterval != null ? healthCheckInterval : 30;
        int timeout = healthCheckTimeout != null ? healthCheckTimeout : 5;
        int healthy = healthyThreshold != null ? healthyThreshold : 2;
        int unhealthy = unhealthyThreshold != null ? unhealthyThreshold : 3;

        // Get application-specific health check path and port
        String healthCheckPath = applicationSpec != null ? applicationSpec.healthCheckPath() : "/";
        int applicationPort = applicationSpec != null ? applicationSpec.applicationPort() : 8080;

        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
        String tgId = Character.toUpperCase(appId.charAt(0)) + appId.substring(1) + "Tg";

        return ApplicationTargetGroup.Builder.create(this, tgId)
                .vpc(vpc)
                .port(applicationPort)
                .protocol(ApplicationProtocol.HTTP)
                .targetType(TargetType.INSTANCE)
                .healthCheck(HealthCheck.builder()
                        .path(healthCheckPath)
                        .healthyHttpCodes("200-299")
                        .interval(Duration.seconds(interval))
                        .timeout(Duration.seconds(timeout))
                        .healthyThresholdCount(healthy)
                        .unhealthyThresholdCount(unhealthy)
                        .build())
                .build();
    }

    private ApplicationListener createHttpListener(ApplicationLoadBalancer alb, ApplicationTargetGroup targetGroup) {
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.forward(List.of(targetGroup)))
                .build());
    }

    // codeql[java/unused-parameter] -- sslEnabled is unused: SSL redirect logic is centralized in
    // SecurityProfile.wire() (see createHttpListenerWithoutTargetGroup's identical note below),
    // not decided per-listener here.
    private ApplicationListener createFargateHttpListener(ApplicationLoadBalancer alb, boolean sslEnabled) {
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "Application";
        String startupMessage = Character.toUpperCase(appId.charAt(0)) + appId.substring(1) + " is starting up...";

        // Create HTTP listener with a temporary default action
        // This will be updated by FargateRuntimeConfiguration when the Fargate service is ready
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                        .contentType("text/plain")
                        .messageBody(startupMessage)
                        .build()))
                .build());
    }

    // codeql[java/unused-parameter] -- sslEnabled is unused; see this method's own comment below
    // on why (SSL redirect logic is centralized in SecurityProfile.wire()).
    private ApplicationListener createHttpListenerWithoutTargetGroup(ApplicationLoadBalancer alb, boolean sslEnabled) {
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "Application";
        String startupMessage = Character.toUpperCase(appId.charAt(0)) + appId.substring(1) + " is starting up...";

        // HTTP listener configuration is now handled by SecurityProfile wiring
        // SSL redirect logic is centralized in SecurityProfile.wire() method
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                        .contentType("text/plain")
                        .messageBody(startupMessage)
                        .build()))
                .build());
    }
}
