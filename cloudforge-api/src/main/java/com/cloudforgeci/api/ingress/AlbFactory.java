package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import software.amazon.awscdk.*;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

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

    public AlbFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        if (ctx == null) {
            throw new IllegalStateException("SystemContext is null in AlbFactory.create()");
        }

        try {
            // Get compliance settings from security profile
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
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

            // Create HTTP listener with placeholder default action
            // The target group will be created by orchestration layer and added to listener later
            // For both EC2 and Fargate, the default action will be updated by RuntimeConfiguration
            ApplicationListener http = createFargateHttpListener(alb, Boolean.TRUE.equals(enableSsl));
            ctx.http.set(http);
            
        } catch (Exception e) {
            LOG.severe("Exception in AlbFactory.create(): " + e.getMessage());
            throw e;
        }
    }

    private SecurityGroup createSecurityGroup() {
        return SecurityGroup.Builder.create(this, "AlbSg")
                .vpc(vpc)
                .allowAllOutbound(true)
                .build();
    }

    private ApplicationLoadBalancer createLoadBalancer(SecurityGroup albSg) {
        // Enable access logging for compliance if configured
        if (Boolean.TRUE.equals(albAccessLogging)) {
            // Get region from deployment context or stack
            String tempRegion = region;
            if (tempRegion == null || tempRegion.isEmpty()) {
                tempRegion = Stack.of(this).getRegion();
            }
            final String effectiveRegion = tempRegion; // Make final for use in lambda

            // Validate required fields for ALB access logging
            String validationError = validateLoggingPrerequisites(effectiveRegion, stackName);
            if (validationError != null) {
                LOG.warning("ALB access logging enabled but prerequisites not met: " + validationError);
                return createAlbWithoutLogging(albSg);
            }

            // Get stack reference for lazy evaluation
            Stack stack = Stack.of(this);

            // Use Lazy.uncachedString() to defer bucket name construction until synthesis time
            String bucketName = Lazy.uncachedString(
                    new IStringProducer() {
                        @Override
                        public String produce(IResolveContext context) {
                            // STACK-SPECIFIC bucket name to avoid conflicts between stacks
                            return (stackName + "-alb-logs-" + stack.getAccount() + "-" + effectiveRegion).toLowerCase();
                        }
                    },
                    LazyStringValueOptions.builder()
                            .displayHint(stackName + "-alb-logs-bucket")
                            .build()
            );

            LOG.info("ALB logs bucket name (stack-specific): " + bucketName);

            // Determine removal policy based on security profile
            var securityProfile = ctx.securityProfileConfig.get().orElse(null);
            boolean isProduction = (securityProfile != null && securityProfile.getClass().getSimpleName().contains("Production"));
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

            var alb = ApplicationLoadBalancer.Builder.create(this, "JenkinsAlb")
                    .vpc(vpc)
                    .securityGroup(albSg)
                    .internetFacing(true)
                    .build();

            // Enable security compliance settings
            configureAlbSecurity(alb);

            // Enable access logs on the created ALB
            alb.logAccessLogs(logBucket);

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
            LOG.info("  Enable by setting albAccessLogging=true or using PRODUCTION/STAGING security profile");

            return createAlbWithoutLogging(albSg);
        }
    }

    private ApplicationLoadBalancer createAlbWithoutLogging(SecurityGroup albSg) {
        var alb = ApplicationLoadBalancer.Builder.create(this, "JenkinsAlb")
                .vpc(vpc)
                .securityGroup(albSg)
                .internetFacing(true)
                .build();

        // Enable security compliance settings
        configureAlbSecurity(alb);

        return alb;
    }

    /**
     * Configure ALB security settings for compliance (SOC2, PCI-DSS).
     * Enables dropping invalid HTTP headers to prevent header injection attacks.
     */
    private void configureAlbSecurity(ApplicationLoadBalancer alb) {
        CfnLoadBalancer cfnAlb = (CfnLoadBalancer) alb.getNode().getDefaultChild();
        cfnAlb.addPropertyOverride("LoadBalancerAttributes", List.of(
            Map.of("Key", "routing.http.drop_invalid_header_fields.enabled", "Value", "true")
        ));
        LOG.info("Drop invalid HTTP headers: enabled (compliance)");
    }

    /**
     * Validate prerequisites for ALB access logging.
     *
     * @param region The AWS region (must not be null, empty, or contain CDK tokens)
     * @param stackName The stack name (must not be null or empty)
     * @return Error message if validation fails, null if validation passes
     */
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
            LOG.info("Non-production mode: Creating ALB logs bucket without SSM tracking");
            return createAlbLogsBucket(bucketName, removalPolicy, false);
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

        // Store bucket ARN in SSM at deployment time using Custom Resource (stack-scoped)
        String ssmParameterName = "/cloudforge/shared/" + region + "/stack/" + this.stackName + "/alb-logs/bucket-arn";

        AwsSdkCall putParameterCall = AwsSdkCall.builder()
                .service("SSM")
                .action("putParameter")
                .parameters(java.util.Map.of(
                        "Name", ssmParameterName,
                        "Value", newBucket.getBucketArn(),
                        "Type", "String",
                        "Description", "CloudForge retained ALB logs bucket ARN for region " + region,
                        "Overwrite", true
                ))
                .physicalResourceId(PhysicalResourceId.of("AlbLogsBucket-SSMWriter"))
                .region(region)
                .build();

        AwsCustomResource ssmWriter = AwsCustomResource.Builder.create(this, "AlbLogsBucketSSMWriter")
                .onCreate(putParameterCall)
                .onUpdate(putParameterCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        ssmWriter.getNode().addDependency(newBucket);

        LOG.info("ALB logs bucket will use CloudFormation-generated unique name");
        LOG.info("ALB logs bucket ARN will be stored in SSM: " + ssmParameterName);
        return newBucket;
    }

    /**
     * Create ALB logs S3 bucket with compliance-driven lifecycle rules.
     */
    private Bucket createAlbLogsBucket(String bucketName, RemovalPolicy removalPolicy, boolean autoDelete) {
        return Bucket.Builder.create(this, "AlbLogsBucket")
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
    }

    private String validateLoggingPrerequisites(String region, String stackName) {
        if (region == null || region.isEmpty() || region.contains("$")) {
            return "Region is not available. Set 'region' in deployment context or CDK_DEFAULT_REGION environment variable";
        }
        if (stackName == null || stackName.isEmpty()) {
            return "Stack name is not set. Set 'stackName' in deployment context to enable ALB access logging";
        }
        return null; // Validation passed
    }

    private ApplicationTargetGroup createTargetGroup(ApplicationLoadBalancer alb) {
        // Use configurable health check settings from annotated fields
        int interval = healthCheckInterval != null ? healthCheckInterval : 30;
        int timeout = healthCheckTimeout != null ? healthCheckTimeout : 5;
        int healthy = healthyThreshold != null ? healthyThreshold : 2;
        int unhealthy = unhealthyThreshold != null ? unhealthyThreshold : 3;

        return ApplicationTargetGroup.Builder.create(this, "JenkinsTg")
                .vpc(vpc)
                .port(8080)
                .protocol(ApplicationProtocol.HTTP)
                .targetType(TargetType.INSTANCE)
                .healthCheck(HealthCheck.builder()
                        .path("/login")
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

    private ApplicationListener createFargateHttpListener(ApplicationLoadBalancer alb, boolean sslEnabled) {
        // Create HTTP listener with a temporary default action
        // This will be updated by FargateRuntimeConfiguration when the Fargate service is ready
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                        .contentType("text/plain")
                        .messageBody("Jenkins is starting up...")
                        .build()))
                .build());
    }

    private ApplicationListener createHttpListenerWithoutTargetGroup(ApplicationLoadBalancer alb, boolean sslEnabled) {
        // HTTP listener configuration is now handled by SecurityProfile wiring
        // SSL redirect logic is centralized in SecurityProfile.wire() method
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                        .contentType("text/plain")
                        .messageBody("Jenkins is starting up...")
                        .build()))
                .build());
    }
}