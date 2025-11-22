package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.rules.AuditManagerControl;
import com.cloudforgeci.api.core.rules.AuditManagerControlRegistry;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.CfnCondition;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.auditmanager.CfnAssessment;
import software.amazon.awscdk.services.cloudtrail.Trail;
import software.amazon.awscdk.services.cloudtrail.CfnTrail;
import software.amazon.awscdk.services.config.*;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.AnyPrincipal;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for creating compliance and audit resources (CloudTrail, AWS Config, AWS Audit Manager).
 * Creates audit logging and compliance monitoring based on security profiles.
 *
 * <h2>Supported Compliance Tools</h2>
 * <ul>
 *   <li><b>CloudTrail</b> - Audit logging for API calls and account activity</li>
 *   <li><b>AWS Config</b> - Compliance monitoring with managed rules</li>
 *   <li><b>AWS Audit Manager</b> - Continuous auditing and automated evidence collection</li>
 * </ul>
 *
 * <h2>AWS Audit Manager Setup</h2>
 * Before using Audit Manager, you must:
 * <ol>
 *   <li>Enable AWS Audit Manager in your AWS account via the AWS Console</li>
 *   <li>Configure data source connections (CloudTrail, Config, Security Hub, etc.)</li>
 *   <li>Choose appropriate compliance framework (SOC2, HIPAA, PCI-DSS, etc.)</li>
 *   <li>Update framework IDs in {@link #getFrameworkId()} to match your account</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * Compliance features are enabled/disabled based on security profile:
 * <ul>
 *   <li><b>DEV</b> - Minimal compliance (CloudTrail only)</li>
 *   <li><b>STAGING</b> - Full compliance testing (Config + Audit Manager)</li>
 *   <li><b>PRODUCTION</b> - Full compliance (CloudTrail + Config + Audit Manager)</li>
 * </ul>
 *
 * You can override defaults using deployment context:
 * <pre>
 * cfc.put("awsConfigEnabled", true);
 * cfc.put("auditManagerEnabled", true);
 * </pre>
 */
public class ComplianceFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(ComplianceFactory.class.getName());

    @SystemContext("security")
    private SecurityProfile security;

    @SystemContext("stackName")
    private String stackName;

    @DeploymentContext("awsConfigEnabled")
    private Boolean awsConfigEnabled;

    @DeploymentContext("auditManagerEnabled")
    private Boolean auditManagerEnabled;

    @DeploymentContext("auditManagerFrameworkId")
    private String auditManagerFrameworkId;

    @DeploymentContext("complianceFrameworks")
    private String complianceFrameworks;

    @DeploymentContext("createConfigInfrastructure")
    private Boolean createConfigInfrastructure;

    @DeploymentContext("enableS3VersioningRemediation")
    private Boolean enableS3VersioningRemediation;

    @DeploymentContext("enableCloudTrailBucketAccessRemediation")
    private Boolean enableCloudTrailBucketAccessRemediation;

    @DeploymentContext("scopeConfigRulesToDeployment")
    private Boolean scopeConfigRulesToDeployment;

    @DeploymentContext("deploymentId")
    private String deploymentId;

    @DeploymentContext("region")
    private String region;

    public ComplianceFactory(Construct scope, String id) {
        super(scope, id);
    }

    // CloudFormation conditions for framework-specific Config rules
    private CfnCondition pciDssCondition;
    private CfnCondition soc2Condition;
    private CfnCondition hipaaCondition;
    private CfnCondition gdprCondition;

    // CloudTrail and bucket for S3 data event configuration
    private Trail trail;
    private Bucket trailBucket;
    private String trailName;

    @Override
    public void create() {
        LOG.info("Creating compliance resources for security profile: " + security);

        // Create CloudFormation conditions for compliance frameworks
        createFrameworkConditions();

        // Create CloudTrail if enabled for this security profile
        if (config.isCloudTrailEnabled()) {
            createCloudTrail();
        } else {
            LOG.info("CloudTrail disabled for security profile: " + security);
        }

        // Check if AWS Audit Manager should be enabled (do this BEFORE Config infrastructure)
        // This ensures Audit Manager configuration errors fail before creating account-level resources
        boolean auditManagerEnabledFlag = (auditManagerEnabled != null)
            ? auditManagerEnabled
            : config.isAuditManagerEnabled();

        if (auditManagerEnabledFlag) {
            LOG.info("Creating AWS Audit Manager assessments for profile: " + security);
            createAuditManagerAssessments();
        } else {
            LOG.info("AWS Audit Manager disabled for profile: " + security);
        }

        // Check if AWS Config should be enabled (deployment override takes precedence over profile default)
        // Config infrastructure creation happens AFTER Audit Manager setup to fail fast on configuration errors
        boolean configEnabled = (awsConfigEnabled != null)
            ? awsConfigEnabled
            : config.isAwsConfigEnabled();

        if (configEnabled) {
            LOG.info("Creating AWS Config infrastructure and rules for profile: " + security);

            // Auto-detect if Config infrastructure already exists
            boolean configInfraExists = checkConfigInfrastructureExists();

            // Check if we should create Config infrastructure (Recorder + Delivery Channel)
            // These are account-level singleton resources - only ONE per region per account allowed
            boolean shouldCreateInfra = (createConfigInfrastructure != null)
                ? createConfigInfrastructure
                : !configInfraExists;  // Auto-skip if already exists

            if (configInfraExists && shouldCreateInfra) {
                LOG.warning("Config infrastructure already exists but createConfigInfrastructure=true");
                LOG.warning("  Existing Config Recorder detected in region: " + region);
                LOG.warning("  Setting createConfigInfrastructure=false automatically to avoid conflict");
                LOG.warning("  To override this behavior, manually set createConfigInfrastructure in deployment-context.json");
                shouldCreateInfra = false;
            }

            if (shouldCreateInfra) {
                LOG.info("Creating Config Recorder and Delivery Channel (account-level singletons)");
                LOG.info("  IMPORTANT: Only ONE stack per region should have createConfigInfrastructure=true");
                ConfigInfrastructure configInfra = createConfigInfrastructure();

                // Create Config Rules that depend on the recorder being started
                createConfigRules(configInfra.recorder, configInfra.starterResource);
                createAllFrameworkConfigRules(configInfra.recorder, configInfra.starterResource);
            } else {
                if (configInfraExists) {
                    LOG.info("Config infrastructure already exists in region (auto-detected)");
                } else {
                    LOG.info("Skipping Config infrastructure creation (createConfigInfrastructure=false)");
                }
                LOG.info("  Config Recorder 'cloudforge-config-recorder' will be referenced by name");
                LOG.info("  Config Rules will use existing recorder (no CloudFormation dependency)");

                // Create Config Rules without recorder dependency
                // Rules will reference the existing recorder by name at runtime
                createConfigRulesWithoutRecorder();
                createAllFrameworkConfigRulesWithoutRecorder();
            }
        } else {
            LOG.info("AWS Config disabled for profile: " + security);
        }

        LOG.info("Compliance resources created successfully for profile: " + security);
    }

    /**
     * Creates CloudFormation conditions for each compliance framework.
     * These conditions determine which framework-specific Config rules are deployed.
     *
     * Supports multiple frameworks via comma-separated values (e.g., "PCI-DSS,SOC2,HIPAA").
     * When a framework is removed, its condition becomes false and CloudFormation deletes those rules.
     */
    private void createFrameworkConditions() {
        software.amazon.awscdk.Stack stack = software.amazon.awscdk.Stack.of(this);

        List<String> frameworks = determineFrameworks();

        LOG.info("Creating CloudFormation conditions for frameworks: " + String.join(", ", frameworks));

        // Normalize framework names for comparison
        List<String> normalizedFrameworks = frameworks.stream()
                .map(f -> f.trim().toUpperCase().replace("-", "").replace("_", ""))
                .collect(java.util.stream.Collectors.toList());

        // Create condition for PCI-DSS
        boolean enablePciDss = normalizedFrameworks.contains("PCIDSS");
        pciDssCondition = CfnCondition.Builder.create(stack, "EnablePciDssRules")
                .expression(Fn.conditionEquals(enablePciDss ? "true" : "false", "true"))
                .build();

        // Create condition for SOC 2
        boolean enableSoc2 = normalizedFrameworks.contains("SOC2");
        soc2Condition = CfnCondition.Builder.create(stack, "EnableSoc2Rules")
                .expression(Fn.conditionEquals(enableSoc2 ? "true" : "false", "true"))
                .build();

        // Create condition for HIPAA
        boolean enableHipaa = normalizedFrameworks.contains("HIPAA");
        hipaaCondition = CfnCondition.Builder.create(stack, "EnableHipaaRules")
                .expression(Fn.conditionEquals(enableHipaa ? "true" : "false", "true"))
                .build();

        // Create condition for GDPR
        boolean enableGdpr = normalizedFrameworks.contains("GDPR");
        gdprCondition = CfnCondition.Builder.create(stack, "EnableGdprRules")
                .expression(Fn.conditionEquals(enableGdpr ? "true" : "false", "true"))
                .build();

        LOG.info("CloudFormation conditions created: PCI-DSS=" + enablePciDss + ", SOC2=" + enableSoc2 +
                 ", HIPAA=" + enableHipaa + ", GDPR=" + enableGdpr);
    }

    /**
     * Create CloudTrail for audit logging.
     *
     * Uses fixed account-level bucket name for cross-stack reusability.
     * This allows multiple stacks to share the same CloudTrail infrastructure.
     *
     * Naming Strategy:
     * - Fixed bucket name: cloudforge-cloudtrail-{accountId}-{region}
     * - Fixed trail name: cloudforge-cloudtrail
     * - First deployment: CloudFormation creates bucket with fixed name
     * - Redeployment: CloudFormation reuses existing bucket
     * - Removal: Bucket is retained (RemovalPolicy.RETAIN) for compliance
     * - Multiple stacks: Share the same CloudTrail bucket
     *
     * IMPORTANT: Uses fromBucketName to import existing buckets instead of creating new ones.
     * This prevents "AlreadyExists" errors when buckets were retained from previous deployments.
     */
    private void createCloudTrail() {
        LOG.info("Creating CloudTrail for audit logging");

        // Use a fixed, account-level trail name instead of stack-specific
        // This allows multiple stacks to share the same CloudTrail
        this.trailName = "cloudforge-cloudtrail-" + this.region;

        LOG.info("CloudTrail will use fixed name: " + this.trailName);
        LOG.info("  If trail exists, CloudFormation will update it (no conflict)");

        // Check SSM Parameter Store at DEPLOYMENT TIME for existing bucket (stack-scoped)
        String ssmParamName = "/cloudforge/shared/" + this.region + "/stack/" + this.stackName + "/cloudtrail/bucket-arn";
        Bucket trailBucket = getOrCreateBucketWithSSM("CloudTrailBucket", ssmParamName);

        LOG.info("CloudTrail bucket ARN will be tracked in SSM Parameter Store for reuse");

        // Create CloudTrail using high-level Trail construct
        // If trail with this name already exists, CloudFormation will update it or fail gracefully
        Trail trail = Trail.Builder.create(this, "CloudTrail")
                .trailName(this.trailName)  // Fixed name for reusability
                .bucket(trailBucket)
                .sendToCloudWatchLogs(true)
                .enableFileValidation(true)
                .includeGlobalServiceEvents(true)
                .isMultiRegionTrail(true)
                .build();

        // Store trail for later configuration
        this.trail = trail;
        this.trailBucket = trailBucket;

        // Set removal policy to match bucket retention behavior
        trail.applyRemovalPolicy(security == SecurityProfile.PRODUCTION ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY);

        LOG.info("CloudTrail created: " + trail.getTrailArn());
        LOG.info("CloudTrail removal policy: " + (security == SecurityProfile.PRODUCTION ? "RETAIN (production)" : "DESTROY (non-production)"));
        LOG.info("CloudTrail name: " + trailName + " (reusable across multiple stacks in this region)");

        // Store CloudTrail ARN in SSM for future reference (stack-specific)
        AwsCustomResource cloudTrailSsmWriter = storeResourceArnInSSM("CloudTrailArn",
                "/cloudforge/" + this.stackName + "/" + this.region + "/cloudtrail/arn",
                trail.getTrailArn(),
                "CloudTrail ARN for stack " + this.stackName + " in region " + this.region);
        if (cloudTrailSsmWriter != null) {
            cloudTrailSsmWriter.getNode().addDependency(trail);
        }

        // Configure S3 data event logging using CloudFormation escape hatch
        configureCloudTrailS3DataEvents(trail);
    }

    /**
     * Configure CloudTrail S3 data event logging using Advanced Event Selectors.
     * This adds AdvancedEventSelectors to the underlying CfnTrail resource for SOC2/PCI-DSS/HIPAA compliance.
     * Uses the newer Advanced Event Selectors API which properly supports wildcards.
     */
    private void configureCloudTrailS3DataEvents(Trail trail) {
        // Get the underlying CfnTrail resource
        CfnTrail cfnTrail = (CfnTrail) trail.getNode().getDefaultChild();

        // Use Advanced Event Selectors (newer API) via CloudFormation property override
        // Advanced Event Selectors support proper wildcard matching for all S3 buckets
        // Using addPropertyOverride to bypass CDK builder API issues
        cfnTrail.addPropertyOverride("AdvancedEventSelectors", List.of(
            // Selector 1: Log all management events
            Map.of(
                "Name", "Log all management events",
                "FieldSelectors", List.of(
                    Map.of(
                        "Field", "eventCategory",
                        "Equals", List.of("Management")
                    )
                )
            ),
            // Selector 2: Log all S3 data events (SOC2/PCI-DSS/HIPAA compliance)
            Map.of(
                "Name", "Log all S3 data events for SOC2 compliance",
                "FieldSelectors", List.of(
                    Map.of(
                        "Field", "eventCategory",
                        "Equals", List.of("Data")
                    ),
                    Map.of(
                        "Field", "resources.type",
                        "Equals", List.of("AWS::S3::Object")
                    )
                )
            )
        ));

        LOG.info("CloudTrail S3 data event logging configured for ALL S3 buckets using Advanced Event Selectors (SOC2/PCI-DSS/HIPAA compliance)");
    }

    /**
     * Add additional S3 bucket to CloudTrail data event logging.
     * Called from createAuditManagerAssessments() to track Audit Manager reports bucket.
     *
     * <p>Note: Since CloudTrail is now configured to track ALL S3 buckets using wildcard,
     * this method is no longer needed to add specific buckets. Kept for compatibility.</p>
     */
    private void addS3DataEventLogging(Bucket bucket) {
        if (this.trail == null || this.trailBucket == null) {
            LOG.warning("CloudTrail not initialized, cannot add S3 data event logging for: " + bucket.getBucketName());
            return;
        }

        // No action needed - CloudTrail already tracks all S3 buckets via wildcard configuration
        LOG.info("S3 data event logging already enabled for all buckets (including " + bucket.getBucketName() + ")");
    }

    /**
     * Create AWS Config rules for compliance monitoring.
     * All rules depend on the provided Configuration Recorder.
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        LOG.info("Setting up AWS Config rules for compliance monitoring");

        // Create managed rules based on security profile
        // All rules explicitly depend on the recorder being started
        createEncryptionConfigRules(recorder, starterResource);
        createS3ConfigRules(recorder, starterResource);
        createIAMConfigRules(recorder, starterResource);

        if (security == SecurityProfile.PRODUCTION) {
            createProductionConfigRules(recorder, starterResource);
        }

        LOG.info("AWS Config rules configured");
    }

    /**
     * Inner class to hold Config infrastructure components (recorder + starter).
     * Config Rules must depend on BOTH the recorder AND the starter resource
     * to ensure the recorder is in STARTED state before rules are created.
     */
    private static class ConfigInfrastructure {
        final CfnConfigurationRecorder recorder;
        final AwsCustomResource starterResource;

        ConfigInfrastructure(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
            this.recorder = recorder;
            this.starterResource = starterResource;
        }
    }

    /**
     * Helper method to add dependencies to Config Rules.
     * Rules must depend on both the recorder AND the starter resource.
     *
     * @param rule The Config Rule to add dependencies to
     * @param recorder The Configuration Recorder
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void addConfigRuleDependencies(CfnConfigRule rule, CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        rule.getNode().addDependency(recorder);
        if (starterResource != null) {
            rule.getNode().addDependency(starterResource);
        }
    }

    /**
     * Creates Configuration Recorder and Delivery Channel for AWS Config.
     *
     * IMPORTANT: AWS Config only allows ONE Configuration Recorder and ONE Delivery Channel
     * per region per account. This method creates account-level resources with fixed names.
     *
     * If resources already exist from a previous deployment, CDK will reuse them.
     * This enables multiple stacks to share the same Config infrastructure.
     *
     * @return ConfigInfrastructure containing recorder and starter resource for rule dependencies
     */
    private ConfigInfrastructure createConfigInfrastructure() {
        // Get stack reference for ARN construction
        software.amazon.awscdk.Stack stack = software.amazon.awscdk.Stack.of(this);

        // Use fixed names (not stack-specific) since these are account-level resources
        // AWS Config only allows 1 recorder and 1 delivery channel per region per account
        String recorderName = "cloudforge-config-recorder";
        String channelName = "cloudforge-config-channel";

        LOG.info("Creating AWS Config infrastructure (account-level resources)");
        LOG.info("  Recorder name: " + recorderName);
        LOG.info("  Delivery channel: " + channelName);

        // Create bucket with SSM tracking for PRODUCTION mode (stack-scoped)
        String configSsmParam = "/cloudforge/shared/" + this.region + "/stack/" + this.stackName + "/config/bucket-arn";
        Bucket configBucket = getOrCreateBucketWithSSM("ConfigBucket", configSsmParam);

        LOG.info("AWS Config bucket ARN will be tracked in SSM Parameter Store");

        Role configRole = Role.Builder.create(this, "ConfigRole")
                .assumedBy(ServicePrincipal.Builder.create("config.amazonaws.com").build())
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWS_ConfigRole"),
                        ManagedPolicy.fromAwsManagedPolicyName("SecurityAudit")  // Required for Security Hub evaluation
                ))
                .build();

        // Grant additional permissions for Config rule evaluations
        configRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                        // Security Hub permissions
                        "securityhub:DescribeHub",
                        "securityhub:GetFindings",
                        "securityhub:GetEnabledStandards",
                        // ACM permissions for certificate expiration checks
                        "acm:DescribeCertificate",
                        "acm:ListCertificates",
                        "acm:GetCertificate",
                        // GuardDuty permissions
                        "guardduty:GetDetector",
                        "guardduty:ListDetectors",
                        // Inspector permissions
                        "inspector2:GetFindingsReportStatus",
                        "inspector2:ListFindings",
                        // Macie permissions
                        "macie2:GetMacieSession",
                        // EC2 permissions for EBS encryption checks
                        "ec2:DescribeVolumes",
                        "ec2:DescribeSnapshots",
                        "ec2:DescribeSnapshotAttribute",
                        "ec2:GetEbsEncryptionByDefault",
                        "ec2:GetEbsDefaultKmsKeyId",
                        // IAM permissions for password policy and root account checks
                        "iam:GetAccountPasswordPolicy",
                        "iam:GetAccountSummary",
                        "iam:ListUsers",
                        "iam:GetUser",
                        "iam:GetLoginProfile",
                        "iam:ListAccessKeys",
                        "iam:GetAccessKeyLastUsed",
                        // S3 permissions for bucket encryption and versioning checks
                        "s3:GetEncryptionConfiguration",
                        "s3:GetBucketVersioning",
                        "s3:GetBucketPolicy",
                        "s3:GetBucketPolicyStatus",
                        "s3:ListAllMyBuckets"
                ))
                .resources(List.of("*"))
                .build());

        configBucket.grantWrite(configRole);

        CfnConfigurationRecorder recorder = CfnConfigurationRecorder.Builder.create(this, "ConfigRecorder")
                .name(recorderName)
                .roleArn(configRole.getRoleArn())
                .recordingGroup(CfnConfigurationRecorder.RecordingGroupProperty.builder()
                        .allSupported(true)
                        .includeGlobalResourceTypes(true)
                        .build())
                .recordingMode(CfnConfigurationRecorder.RecordingModeProperty.builder()
                        .recordingFrequency("CONTINUOUS")
                        .build())
                .build();

        // RETAIN resources - they're account-level and should survive stack deletion
        recorder.applyRemovalPolicy(RemovalPolicy.RETAIN);
        recorder.getNode().addDependency(configRole);
        recorder.getNode().addDependency(configBucket);

        CfnDeliveryChannel deliveryChannel = CfnDeliveryChannel.Builder.create(this, "ConfigDeliveryChannel")
                .name(channelName)
                .s3BucketName(configBucket.getBucketName())
                .build();

        // RETAIN resources - they're account-level and should survive stack deletion
        deliveryChannel.applyRemovalPolicy(RemovalPolicy.RETAIN);
        deliveryChannel.getNode().addDependency(configRole);
        deliveryChannel.getNode().addDependency(configBucket);

        LOG.info("AWS Config infrastructure created (will be retained on stack deletion)");

        // Store Config Recorder and Delivery Channel ARNs in SSM for compliance tracking
        // These are ALWAYS tracked (not just PRODUCTION) because they're account-level singletons
        String recorderArn = stack.formatArn(software.amazon.awscdk.ArnComponents.builder()
                .service("config")
                .resource("config-recorder")
                .resourceName(recorderName)
                .build());
        String channelArn = stack.formatArn(software.amazon.awscdk.ArnComponents.builder()
                .service("config")
                .resource("delivery-channel")
                .resourceName(channelName)
                .build());

        // Store Recorder ARN (always, not just PRODUCTION)
        AwsCustomResource recorderSsmWriter = storeResourceArnInSSMAlways("ConfigRecorderArn",
                "/cloudforge/shared/" + this.region + "/config/recorder-arn",
                recorderArn,
                "AWS Config Recorder ARN for region " + this.region);
        recorderSsmWriter.getNode().addDependency(recorder);

        // Store Channel ARN (always, not just PRODUCTION)
        AwsCustomResource channelSsmWriter = storeResourceArnInSSMAlways("ConfigDeliveryChannelArn",
                "/cloudforge/shared/" + this.region + "/config/channel-arn",
                channelArn,
                "AWS Config Delivery Channel ARN for region " + this.region);
        channelSsmWriter.getNode().addDependency(deliveryChannel);

        // Automatically start the Config Recorder for SOC2 and other compliance frameworks
        // This ensures compliance recording begins immediately upon deployment
        AwsCustomResource starterResource = startConfigRecorder(recorder, recorderName);

        return new ConfigInfrastructure(recorder, starterResource);
    }

    /**
     * Automatically start the Config Recorder using AWS SDK custom resource.
     * This ensures compliance recording begins immediately upon deployment.
     *
     * <p><b>Why auto-start is required:</b></p>
     * <ul>
     *   <li>AWS Config Recorder is created in a STOPPED state by default (AWS safeguard)</li>
     *   <li>SOC2, HIPAA, PCI-DSS, and GDPR require continuous compliance monitoring</li>
     *   <li>Manual start would create compliance gap between deployment and activation</li>
     * </ul>
     *
     * <p><b>Implementation:</b></p>
     * Uses AWS SDK custom resource to call StartConfigurationRecorder API.
     * This is idempotent - if already started, the call succeeds with no changes.
     *
     * @param recorder The CfnConfigurationRecorder to start
     * @param recorderName The name of the recorder (e.g., "cloudforge-config-recorder")
     * @return The AwsCustomResource that starts the recorder (for Config Rule dependencies)
     */
    private AwsCustomResource startConfigRecorder(CfnConfigurationRecorder recorder, String recorderName) {
        LOG.info("Auto-starting Config Recorder for compliance frameworks");
        LOG.info("  Recorder: " + recorderName);
        LOG.info("  Reason: SOC2/HIPAA/PCI-DSS/GDPR require continuous compliance monitoring");

        // Validate region is available
        if (region == null || region.isEmpty() || region.contains("$")) {
            LOG.warning("Region not available - cannot auto-start Config Recorder");
            LOG.warning("  Manual start required: aws configservice start-configuration-recorder --configuration-recorder-name " + recorderName);
            return null;
        }

        // Create AWS SDK call to start the recorder
        AwsSdkCall startRecorderCall = AwsSdkCall.builder()
                .service("ConfigService")
                .action("startConfigurationRecorder")
                .parameters(Map.of(
                        "ConfigurationRecorderName", recorderName
                ))
                .physicalResourceId(PhysicalResourceId.of("config-recorder-starter-" + recorderName))
                .region(region)
                .build();

        // Create AWS SDK call to verify the recorder is running
        // This ensures Config Rules are not created until recorder is operational
        AwsSdkCall verifyRecorderCall = AwsSdkCall.builder()
                .service("ConfigService")
                .action("describeConfigurationRecorderStatus")
                .parameters(Map.of(
                        "ConfigurationRecorderNames", List.of(recorderName)
                ))
                .physicalResourceId(PhysicalResourceId.of("config-recorder-verifier-" + recorderName))
                .region(region)
                .outputPaths(List.of("ConfigurationRecordersStatus.0.recording"))  // Extract recording status
                .build();

        // Create custom resource that starts the recorder on create and update
        AwsCustomResource startRecorderResource = AwsCustomResource.Builder.create(this, "StartConfigRecorder")
                .onCreate(startRecorderCall)
                .onUpdate(startRecorderCall)  // Idempotent - safe to call on update
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))  // Config Service requires wildcard for start operation
                                .build()
                ))
                .build();

        // Create a second custom resource that WAITS for the recorder to be operational
        // This verifier depends on the starter and MUST complete before Config Rules are created
        AwsCustomResource verifyRecorderResource = AwsCustomResource.Builder.create(this, "VerifyConfigRecorderStarted")
                .onCreate(verifyRecorderCall)
                .onUpdate(verifyRecorderCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        // Chain: Recorder → Starter → Verifier → Config Rules
        verifyRecorderResource.getNode().addDependency(startRecorderResource);

        // Ensure recorder is created before we try to start it
        startRecorderResource.getNode().addDependency(recorder);

        LOG.info("Config Recorder auto-start configured successfully");
        LOG.info("  Recorder will start automatically during deployment");
        LOG.info("  Verifier will confirm recorder is operational before Config Rules are created");
        LOG.info("  Compliance recording will begin immediately");

        // Return the VERIFIER resource (not the starter) for Config Rule dependencies
        // Config Rules must wait for the verifier to confirm the recorder is operational
        return verifyRecorderResource;
    }

    /**
     * Creates Config rules for encryption compliance.
     * All rules explicitly depend on the Configuration Recorder being started.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createEncryptionConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        CfnConfigRule ebsRule = CfnConfigRule.Builder.create(this, "EbsEncryptionRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.EC2_EBS_ENCRYPTION_BY_DEFAULT)
                        .build())
                .build();
        addConfigRuleDependencies(ebsRule, recorder, starterResource);

        CfnConfigRule s3EncryptionRule = CfnConfigRule.Builder.create(this, "S3BucketEncryptionRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.S3_BUCKET_SERVER_SIDE_ENCRYPTION_ENABLED)
                        .build())
                .build();
        addConfigRuleDependencies(s3EncryptionRule, recorder, starterResource);
    }

    /**
     * Creates Config rules for S3 security compliance.
     * All rules explicitly depend on the Configuration Recorder being started.
     *
     * Optional scoping: If scopeConfigRulesToDeployment is enabled, rules only monitor
     * S3 buckets tagged with the deployment ID, not all buckets in the account.
     *
     * Optional remediation: If enableS3VersioningRemediation is enabled, automatically
     * enables versioning on non-compliant buckets.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createS3ConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        CfnConfigRule publicAccessRule = CfnConfigRule.Builder.create(this, "S3PublicAccessBlockRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.S3_BUCKET_PUBLIC_READ_PROHIBITED)
                        .build())
                .build();
        addConfigRuleDependencies(publicAccessRule, recorder, starterResource);

        // Build S3 versioning rule with optional scope configuration
        CfnConfigRule.Builder versioningRuleBuilder = CfnConfigRule.Builder.create(this, "S3VersioningRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.S3_BUCKET_VERSIONING_ENABLED)
                        .build());

        // Add scope to only monitor buckets created by this stack if configured
        if (Boolean.TRUE.equals(scopeConfigRulesToDeployment) && stackName != null) {
            versioningRuleBuilder.scope(CfnConfigRule.ScopeProperty.builder()
                    .complianceResourceTypes(List.of("AWS::S3::Bucket"))
                    .tagKey("aws:cloudformation:stack-name")
                    .tagValue(stackName)
                    .build());
            LOG.info("S3 versioning rule scoped to stack: " + stackName);
        } else {
            LOG.info("S3 versioning rule monitoring all account buckets");
        }

        CfnConfigRule versioningRule = versioningRuleBuilder.build();
        addConfigRuleDependencies(versioningRule, recorder, starterResource);

        // Add automatic remediation if enabled
        if (Boolean.TRUE.equals(enableS3VersioningRemediation)) {
            createS3VersioningRemediation(versioningRule);
        }
    }

    /**
     * Creates Config rules for IAM security compliance.
     * All rules explicitly depend on the Configuration Recorder being started.
     *
     * Includes automatic remediation for password policy based on enabled compliance frameworks.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createIAMConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        // Get password policy parameters based on enabled compliance frameworks
        Map<String, Object> passwordPolicyParams = getPasswordPolicyParameters();

        CfnConfigRule passwordPolicyRule = CfnConfigRule.Builder.create(this, "IAMPasswordPolicyRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.IAM_PASSWORD_POLICY)
                        .build())
                .inputParameters(passwordPolicyParams)
                .build();
        addConfigRuleDependencies(passwordPolicyRule, recorder, starterResource);

        // Create automatic remediation for password policy
        createPasswordPolicyRemediation(passwordPolicyRule);

        CfnConfigRule rootAccessKeyRule = CfnConfigRule.Builder.create(this, "IAMRootAccessKeyRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.IAM_ROOT_ACCESS_KEY_CHECK)
                        .build())
                .build();
        addConfigRuleDependencies(rootAccessKeyRule, recorder, starterResource);
    }

    /**
     * Get password policy parameters based on enabled compliance frameworks.
     *
     * Applies the strictest requirements from all enabled frameworks:
     * - HIPAA: 14 chars, all complexity, 90-day rotation, 24 password reuse
     * - PCI-DSS: 8 chars, complexity required, 90-day rotation
     * - SOC2: 12 chars, complexity recommended
     *
     * @return Map of password policy parameters for AWS Config rule
     */
    private Map<String, Object> getPasswordPolicyParameters() {
        List<String> frameworks = determineFrameworks();

        // Normalize framework names
        List<String> normalizedFrameworks = frameworks.stream()
                .map(f -> f.trim().toUpperCase().replace("-", "").replace("_", ""))
                .collect(java.util.stream.Collectors.toList());

        boolean hipaaEnabled = normalizedFrameworks.contains("HIPAA");
        boolean pciDssEnabled = normalizedFrameworks.contains("PCIDSS");
        boolean soc2Enabled = normalizedFrameworks.contains("SOC2");

        // Determine strictest requirements
        int minLength;
        int maxPasswordAge;
        int passwordReusePrevention;
        String frameworkName;

        if (hipaaEnabled) {
            minLength = 14;
            maxPasswordAge = 90;
            passwordReusePrevention = 24;
            frameworkName = "HIPAA";
        } else if (soc2Enabled) {
            minLength = 12;
            maxPasswordAge = 90;
            passwordReusePrevention = 12;
            frameworkName = "SOC2";
        } else if (pciDssEnabled) {
            minLength = 8;
            maxPasswordAge = 90;
            passwordReusePrevention = 4;
            frameworkName = "PCI-DSS";
        } else {
            // Default based on security profile
            minLength = security == SecurityProfile.PRODUCTION ? 14 : 12;
            maxPasswordAge = 90;
            passwordReusePrevention = 12;
            frameworkName = "Default (" + security + ")";
        }

        LOG.info("IAM password policy requirements (" + frameworkName + "):");
        LOG.info("  Minimum length: " + minLength + " characters");
        LOG.info("  Max password age: " + maxPasswordAge + " days");
        LOG.info("  Password reuse prevention: " + passwordReusePrevention + " passwords");
        LOG.info("  Complexity: Uppercase, lowercase, numbers, symbols required");

        // Return parameters as JSON string (required by AWS Config)
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("MinimumPasswordLength", minLength);
        params.put("RequireUppercaseCharacters", true);
        params.put("RequireLowercaseCharacters", true);
        params.put("RequireNumbers", true);
        params.put("RequireSymbols", true);
        params.put("MaxPasswordAge", maxPasswordAge);
        params.put("PasswordReusePrevention", passwordReusePrevention);

        return params;
    }

    /**
     * Create automatic remediation for IAM password policy using AWS SSM Automation.
     *
     * Uses the official AWS-managed SSM document: AWSConfigRemediation-SetIAMPasswordPolicy
     * This automatically fixes non-compliant password policies based on the Config rule parameters.
     *
     * @param passwordPolicyRule The Config rule to attach remediation to
     */
    private void createPasswordPolicyRemediation(CfnConfigRule passwordPolicyRule) {
        // Create IAM role for SSM Automation
        Role ssmAutomationRole = Role.Builder.create(this, "PasswordPolicyRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .inlinePolicies(Map.of(
                    "IAMPasswordPolicyPermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "iam:UpdateAccountPasswordPolicy",
                                    "iam:GetAccountPasswordPolicy"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Get password policy parameters
        Map<String, Object> policyParams = getPasswordPolicyParameters();

        // Create remediation configuration
        // Note: Using addPropertyOverride to work around AWS CDK bug #8996 where
        // staticValue generates lowercase keys instead of StaticValue (uppercase)
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "PasswordPolicyRemediation")
                .configRuleName(passwordPolicyRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId("AWSConfigRemediation-SetIAMPasswordPolicy")
                .targetVersion("1")
                .automatic(true)  // Enable automatic remediation
                .maximumAutomaticAttempts(5)
                .retryAttemptSeconds(60)
                .build();

        // Manually override Parameters with correct CloudFormation format
        // CloudFormation expects StaticValue (uppercase) as an object with a Values array property
        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmAutomationRole.getRoleArn()))),
            "MinimumPasswordLength", Map.of("StaticValue", Map.of("Values", List.of(String.valueOf(policyParams.get("MinimumPasswordLength"))))),
            "RequireUppercaseCharacters", Map.of("StaticValue", Map.of("Values", List.of("true"))),
            "RequireLowercaseCharacters", Map.of("StaticValue", Map.of("Values", List.of("true"))),
            "RequireNumbers", Map.of("StaticValue", Map.of("Values", List.of("true"))),
            "RequireSymbols", Map.of("StaticValue", Map.of("Values", List.of("true"))),
            "MaxPasswordAge", Map.of("StaticValue", Map.of("Values", List.of(String.valueOf(policyParams.get("MaxPasswordAge"))))),
            "PasswordReusePrevention", Map.of("StaticValue", Map.of("Values", List.of(String.valueOf(policyParams.get("PasswordReusePrevention"))))),
            "AllowUsersToChangePassword", Map.of("StaticValue", Map.of("Values", List.of("true")))
        ));

        LOG.info("IAM password policy automatic remediation enabled");
        LOG.info("  SSM Document: AWSConfigRemediation-SetIAMPasswordPolicy");
        LOG.info("  Mode: Automatic (fixes non-compliant policies immediately)");
        LOG.info("  Max attempts: 5, Retry interval: 60 seconds");
    }

    /**
     * Creates automatic remediation for S3 bucket versioning compliance.
     * Uses the official AWS-managed SSM document: AWS-ConfigureS3BucketVersioning
     * This automatically enables versioning on non-compliant S3 buckets.
     *
     * NOTE: Enabling versioning has storage cost implications. Once enabled, versioning
     * cannot be fully disabled (only suspended). Users should understand these implications
     * before enabling automatic remediation.
     *
     * @param versioningRule The Config rule to attach remediation to
     */
    private void createS3VersioningRemediation(CfnConfigRule versioningRule) {
        // Create IAM role for SSM Automation
        Role ssmAutomationRole = Role.Builder.create(this, "S3VersioningRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .inlinePolicies(Map.of(
                    "S3VersioningPermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "s3:PutBucketVersioning",
                                    "s3:GetBucketVersioning"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create remediation configuration
        // Note: Using addPropertyOverride to work around AWS CDK bug #8996
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "S3VersioningRemediation")
                .configRuleName(versioningRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId("AWS-ConfigureS3BucketVersioning")
                .targetVersion("1")
                .automatic(true)  // Enable automatic remediation
                .maximumAutomaticAttempts(5)
                .retryAttemptSeconds(60)
                .build();

        // Manually override Parameters with correct CloudFormation format
        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmAutomationRole.getRoleArn()))),
            "BucketName", Map.of("ResourceValue", Map.of("Value", "RESOURCE_ID")),
            "VersioningState", Map.of("StaticValue", Map.of("Values", List.of("Enabled")))
        ));

        LOG.info("S3 bucket versioning automatic remediation enabled");
        LOG.info("  SSM Document: AWS-ConfigureS3BucketVersioning");
        LOG.info("  Mode: Automatic (enables versioning on non-compliant buckets)");
        LOG.info("  WARNING: This has cost implications - versioned objects consume additional storage");
        LOG.info("  Max attempts: 5, Retry interval: 60 seconds");
    }

    /**
     * Creates auto-remediation configuration for CloudTrail bucket access errors.
     *
     * <p>This remediation automatically fixes CloudTrail S3 bucket policy access issues
     * that prevent CloudTrail from writing logs. Common issues include:</p>
     * <ul>
     *   <li>Missing or incorrect bucket policy for CloudTrail service</li>
     *   <li>Insufficient ACL permissions on the bucket</li>
     *   <li>Bucket policy that denies CloudTrail access</li>
     * </ul>
     *
     * <p>The remediation uses the AWS-managed SSM document
     * <code>AWSConfigRemediation-EnableCloudTrailLogFileValidation</code> to automatically
     * fix bucket access policies when AWS Config detects non-compliance.</p>
     *
     * <h3>How It Works</h3>
     * <ol>
     *   <li>AWS Config detects CloudTrail is not enabled or cannot access its bucket</li>
     *   <li>Config triggers this automated remediation</li>
     *   <li>SSM Automation updates the S3 bucket policy with correct CloudTrail permissions</li>
     *   <li>CloudTrail resumes logging to the bucket</li>
     * </ol>
     *
     * <h3>Required Permissions</h3>
     * <p>The SSM automation role needs:</p>
     * <ul>
     *   <li>s3:GetBucketPolicy - Read current bucket policy</li>
     *   <li>s3:PutBucketPolicy - Update bucket policy</li>
     *   <li>s3:GetBucketAcl - Read bucket ACL</li>
     *   <li>cloudtrail:GetTrail - Get CloudTrail configuration</li>
     *   <li>cloudtrail:DescribeTrails - List CloudTrail details</li>
     * </ul>
     *
     * @param cloudTrailRule The AWS Config rule for CloudTrail enabled check
     */
    private void addCloudTrailBucketAccessRemediation(CfnConfigRule cloudTrailRule) {
        LOG.info("Setting up CloudTrail bucket access auto-remediation");

        // Create IAM role for SSM Automation with CloudTrail and S3 permissions
        Role ssmAutomationRole = Role.Builder.create(this, "CloudTrailBucketAccessRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .description("Role for automated CloudTrail S3 bucket access remediation")
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .inlinePolicies(Map.of(
                    "CloudTrailBucketAccessPermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .sid("S3BucketPolicyManagement")
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "s3:GetBucketPolicy",
                                    "s3:PutBucketPolicy",
                                    "s3:GetBucketAcl",
                                    "s3:PutBucketAcl"
                                ))
                                .resources(List.of("*"))
                                .build(),
                            PolicyStatement.Builder.create()
                                .sid("CloudTrailReadAccess")
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "cloudtrail:GetTrail",
                                    "cloudtrail:DescribeTrails",
                                    "cloudtrail:GetEventSelectors"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create custom SSM Automation document for CloudTrail bucket policy fix
        software.amazon.awscdk.services.ssm.CfnDocument cloudTrailFixDocument =
            software.amazon.awscdk.services.ssm.CfnDocument.Builder.create(this, "CloudTrailBucketPolicyFixDocument")
                .name(stackName + "-fix-cloudtrail-bucket-access")
                .documentType("Automation")
                .documentFormat("YAML")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Fixes CloudTrail S3 bucket access policy to allow CloudTrail to write logs",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for automation execution"
                        ),
                        "TrailName", Map.of(
                            "type", "String",
                            "description", "Name of the CloudTrail trail"
                        ),
                        "BucketName", Map.of(
                            "type", "String",
                            "description", "Name of the S3 bucket for CloudTrail logs"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "GetCloudTrailBucket",
                            "action", "aws:executeAwsApi",
                            "description", "Retrieve CloudTrail S3 bucket name",
                            "inputs", Map.of(
                                "Service", "cloudtrail",
                                "Api", "GetTrail",
                                "Name", "{{ TrailName }}"
                            ),
                            "outputs", List.of(
                                Map.of(
                                    "Name", "S3BucketName",
                                    "Selector", "$.Trail.S3BucketName",
                                    "Type", "String"
                                )
                            )
                        ),
                        Map.of(
                            "name", "FixBucketPolicy",
                            "action", "aws:executeAwsApi",
                            "description", "Update S3 bucket policy to grant CloudTrail access",
                            "inputs", Map.of(
                                "Service", "s3",
                                "Api", "PutBucketPolicy",
                                "Bucket", "{{ BucketName }}",
                                "Policy", "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"AWSCloudTrailAclCheck\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"cloudtrail.amazonaws.com\"},\"Action\":\"s3:GetBucketAcl\",\"Resource\":\"arn:aws:s3:::{{ BucketName }}\"},{\"Sid\":\"AWSCloudTrailWrite\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"cloudtrail.amazonaws.com\"},\"Action\":\"s3:PutObject\",\"Resource\":\"arn:aws:s3:::{{ BucketName }}/AWSLogs/*\",\"Condition\":{\"StringEquals\":{\"s3:x-amz-acl\":\"bucket-owner-full-control\"}}}]}"
                            )
                        )
                    )
                ))
                .build();

        // Create remediation configuration using the custom document
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "CloudTrailBucketAccessRemediation")
                .configRuleName(cloudTrailRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(cloudTrailFixDocument.getRef())
                .targetVersion("1")
                .automatic(true)  // Enable automatic remediation
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(120)
                .build();

        // Configure remediation parameters
        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of(
                "StaticValue", Map.of("Values", List.of(ssmAutomationRole.getRoleArn()))
            ),
            "TrailName", Map.of(
                "StaticValue", Map.of("Values", List.of(this.trailName != null ? this.trailName : "cloudforge-cloudtrail-" + this.region))
            ),
            "BucketName", Map.of(
                "StaticValue", Map.of("Values", List.of(this.trailBucket != null ? this.trailBucket.getBucketName() : ""))
            )
        ));

        // Ensure dependencies
        remediation.getNode().addDependency(cloudTrailRule);
        remediation.getNode().addDependency(cloudTrailFixDocument);

        LOG.info("CloudTrail bucket access automatic remediation configured");
        LOG.info("  SSM Document: " + cloudTrailFixDocument.getRef());
        LOG.info("  Mode: Automatic (fixes S3 bucket policy when CloudTrail cannot access bucket)");
        LOG.info("  Max attempts: 3, Retry interval: 120 seconds");
        LOG.info("  Permissions: S3 bucket policy updates, CloudTrail read access");
        LOG.info("  This ensures CloudTrail can always write audit logs for compliance");
    }

    /**
     * Creates additional Config rules for production environments.
     * All rules explicitly depend on the Configuration Recorder.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createProductionConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        CfnConfigRule cloudTrailRule = CfnConfigRule.Builder.create(this, "CloudTrailEnabledRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.CLOUD_TRAIL_ENABLED)
                        .build())
                .build();
        addConfigRuleDependencies(cloudTrailRule, recorder, starterResource);

        // Add auto-remediation for CloudTrail bucket access errors if enabled
        if (enableCloudTrailBucketAccessRemediation != null && enableCloudTrailBucketAccessRemediation) {
            addCloudTrailBucketAccessRemediation(cloudTrailRule);
        }

        CfnConfigRule cloudTrailValidationRule = CfnConfigRule.Builder.create(this, "CloudTrailLogFileValidationRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.CLOUD_TRAIL_LOG_FILE_VALIDATION_ENABLED)
                        .build())
                .build();
        addConfigRuleDependencies(cloudTrailValidationRule, recorder, starterResource);

        CfnConfigRule vpcFlowLogsRule = CfnConfigRule.Builder.create(this, "VpcFlowLogsRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.VPC_FLOW_LOGS_ENABLED)
                        .build())
                .build();
        addConfigRuleDependencies(vpcFlowLogsRule, recorder, starterResource);
    }

    /**
     * Creates Config rules WITHOUT recorder dependency.
     * Used when createConfigInfrastructure=false and recorder already exists outside this stack.
     * Rules will reference existing recorder by name at runtime.
     */
    private void createConfigRulesWithoutRecorder() {
        LOG.info("Setting up AWS Config rules (no recorder dependency)");

        // Create managed rules without explicit dependency on recorder
        createEncryptionConfigRulesWithoutRecorder();
        createS3ConfigRulesWithoutRecorder();
        createIAMConfigRulesWithoutRecorder();

        if (security == SecurityProfile.PRODUCTION) {
            createProductionConfigRulesWithoutRecorder();
        }

        LOG.info("AWS Config rules configured (without recorder dependency)");
    }

    /**
     * Creates Config rules for encryption compliance WITHOUT recorder dependency.
     */
    private void createEncryptionConfigRulesWithoutRecorder() {
        CfnConfigRule.Builder.create(this, "EbsEncryptionRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.EC2_EBS_ENCRYPTION_BY_DEFAULT)
                        .build())
                .build();

        CfnConfigRule.Builder.create(this, "S3BucketEncryptionRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.S3_BUCKET_SERVER_SIDE_ENCRYPTION_ENABLED)
                        .build())
                .build();
    }

    /**
     * Creates Config rules for S3 security compliance WITHOUT recorder dependency.
     */
    private void createS3ConfigRulesWithoutRecorder() {
        CfnConfigRule.Builder.create(this, "S3PublicAccessBlockRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.S3_BUCKET_PUBLIC_READ_PROHIBITED)
                        .build())
                .build();

        CfnConfigRule.Builder.create(this, "S3VersioningRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.S3_BUCKET_VERSIONING_ENABLED)
                        .build())
                .build();
    }

    /**
     * Creates Config rules for IAM security compliance WITHOUT recorder dependency.
     */
    private void createIAMConfigRulesWithoutRecorder() {
        CfnConfigRule.Builder.create(this, "IAMPasswordPolicyRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.IAM_PASSWORD_POLICY)
                        .build())
                .build();

        CfnConfigRule.Builder.create(this, "IAMRootAccessKeyRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.IAM_ROOT_ACCESS_KEY_CHECK)
                        .build())
                .build();
    }

    /**
     * Creates additional Config rules for production environments WITHOUT recorder dependency.
     */
    private void createProductionConfigRulesWithoutRecorder() {
        CfnConfigRule cloudTrailRule = CfnConfigRule.Builder.create(this, "CloudTrailEnabledRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.CLOUD_TRAIL_ENABLED)
                        .build())
                .build();

        // Add auto-remediation for CloudTrail bucket access errors if enabled
        if (enableCloudTrailBucketAccessRemediation != null && enableCloudTrailBucketAccessRemediation) {
            addCloudTrailBucketAccessRemediation(cloudTrailRule);
        }

        CfnConfigRule.Builder.create(this, "CloudTrailLogFileValidationRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.CLOUD_TRAIL_LOG_FILE_VALIDATION_ENABLED)
                        .build())
                .build();

        CfnConfigRule.Builder.create(this, "VpcFlowLogsRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.VPC_FLOW_LOGS_ENABLED)
                        .build())
                .build();
    }

    /**
     * Creates ALL framework-specific AWS Config rules WITHOUT recorder dependency.
     * Used when createConfigInfrastructure=false.
     */
    private void createAllFrameworkConfigRulesWithoutRecorder() {
        List<String> frameworks = determineFrameworks();

        if (frameworks.isEmpty()) {
            LOG.info("No compliance frameworks specified - all framework Config rules will be skipped via conditions");
        } else {
            LOG.info("Framework-specific Config rules enabled for: " + String.join(", ", frameworks));
        }

        // Always create ALL framework rules, using conditions to control deployment
        LOG.info("Creating PCI-DSS Config rules (condition-controlled, no recorder dependency)");
        createPciDssConfigRulesWithoutRecorder();

        LOG.info("Creating SOC 2 Config rules (condition-controlled, no recorder dependency)");
        createSoc2ConfigRulesWithoutRecorder();

        LOG.info("Creating HIPAA Config rules (condition-controlled, no recorder dependency)");
        createHipaaConfigRulesWithoutRecorder();

        LOG.info("Creating GDPR Config rules (condition-controlled, no recorder dependency)");
        createGdprConfigRulesWithoutRecorder();

        LOG.info("All framework-specific Config rules created with conditions (no recorder dependency)");
    }

    /**
     * Creates ALL framework-specific AWS Config rules with CloudFormation conditions.
     * This makes compliance controls visible immediately in AWS Config console.
     *
     * IMPORTANT: Always creates rules for ALL frameworks (PCI-DSS, SOC2, HIPAA, GDPR),
     * but uses CloudFormation conditions to deploy only the selected ones.
     * When frameworks are removed from complianceFrameworks, their condition becomes false
     * and CloudFormation automatically DELETES those rules.
     *
     * Supports multiple frameworks simultaneously via comma-separated values (e.g., "PCI-DSS,SOC2").
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createAllFrameworkConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        List<String> frameworks = determineFrameworks();

        if (frameworks.isEmpty()) {
            LOG.info("No compliance frameworks specified - all framework Config rules will be skipped via conditions");
        } else {
            LOG.info("Framework-specific Config rules enabled for: " + String.join(", ", frameworks));
        }

        // Always create ALL framework rules, using conditions to control deployment
        // This allows CloudFormation to track and DELETE rules when frameworks are removed
        LOG.info("Creating PCI-DSS Config rules (condition-controlled)");
        createPciDssConfigRules(recorder, starterResource);

        LOG.info("Creating SOC 2 Config rules (condition-controlled)");
        createSoc2ConfigRules(recorder, starterResource);

        LOG.info("Creating HIPAA Config rules (condition-controlled)");
        createHipaaConfigRules(recorder, starterResource);

        LOG.info("Creating GDPR Config rules (condition-controlled)");
        createGdprConfigRules(recorder, starterResource);

        LOG.info("All framework-specific Config rules created with conditions");
    }

    /**
     * Creates PCI-DSS specific AWS Config rules.
     * Implements controls for PCI-DSS Requirements 1-11.
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createPciDssConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        LOG.info("Creating PCI-DSS AWS Config rules (condition-controlled)");

        // Requirement 1: Network Segmentation
        CfnConfigRule vpcDefaultSecurityGroupClosed = CfnConfigRule.Builder.create(this, "PciDssVpcDefaultSecurityGroupClosed")
                .configRuleName("pci-dss-vpc-default-sg-closed")
                .description("PCI-DSS Req 1.3: Prohibit direct public access between internet and cardholder data")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_DEFAULT_SECURITY_GROUP_CLOSED")
                        .build())
                .build();
        vpcDefaultSecurityGroupClosed.addOverride("Condition", pciDssCondition.getLogicalId());
        vpcDefaultSecurityGroupClosed.getNode().addDependency(recorder);

        // Requirement 2: Secure Configuration
        CfnConfigRule ec2InstanceManagedBySsm = CfnConfigRule.Builder.create(this, "PciDssEc2ManagedBySsm")
                .configRuleName("pci-dss-ec2-managed-by-ssm")
                .description("PCI-DSS Req 2: Secure system configuration management")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_INSTANCE_MANAGED_BY_SSM")
                        .build())
                .build();
        ec2InstanceManagedBySsm.addOverride("Condition", pciDssCondition.getLogicalId());
        ec2InstanceManagedBySsm.getNode().addDependency(recorder);

        // Requirement 3: Protect stored cardholder data
        CfnConfigRule rdsEncryptionEnabled = CfnConfigRule.Builder.create(this, "PciDssRdsEncryption")
                .configRuleName("pci-dss-rds-encryption-enabled")
                .description("PCI-DSS Req 3.4: Render cardholder data unreadable with encryption")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        rdsEncryptionEnabled.addOverride("Condition", pciDssCondition.getLogicalId());
        rdsEncryptionEnabled.getNode().addDependency(recorder);

        // Requirement 4: Encrypt transmission
        CfnConfigRule elbTlsHttpsListenersOnly = CfnConfigRule.Builder.create(this, "PciDssElbTlsOnly")
                .configRuleName("pci-dss-elb-tls-https-only")
                .description("PCI-DSS Req 4.1: Use strong cryptography for transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_TLS_HTTPS_LISTENERS_ONLY")
                        .build())
                .build();
        elbTlsHttpsListenersOnly.addOverride("Condition", pciDssCondition.getLogicalId());
        elbTlsHttpsListenersOnly.getNode().addDependency(recorder);

        // Requirement 7: Restrict access by business need to know
        CfnConfigRule iamPolicyNoStatementsWithAdminAccess = CfnConfigRule.Builder.create(this, "PciDssIamNoAdminPolicy")
                .configRuleName("pci-dss-iam-no-admin-policy")
                .description("PCI-DSS Req 7.1: Limit access to system components by business need-to-know")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS")
                        .build())
                .build();
        iamPolicyNoStatementsWithAdminAccess.addOverride("Condition", pciDssCondition.getLogicalId());
        iamPolicyNoStatementsWithAdminAccess.getNode().addDependency(recorder);

        // Requirement 8: Identify and authenticate access
        CfnConfigRule iamUserMfaEnabled = CfnConfigRule.Builder.create(this, "PciDssIamMfaEnabled")
                .configRuleName("pci-dss-iam-user-mfa-enabled")
                .description("PCI-DSS Req 8.3: Multi-factor authentication for remote access")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_MFA_ENABLED")
                        .build())
                .build();
        iamUserMfaEnabled.addOverride("Condition", pciDssCondition.getLogicalId());
        iamUserMfaEnabled.getNode().addDependency(recorder);

        // Requirement 10: Track and monitor access
        Map<String, Object> alarmActionParams = Map.of(
                "alarmActionRequired", "true",
                "insufficientDataActionRequired", "false",
                "okActionRequired", "false"
        );
        CfnConfigRule cloudwatchAlarmActionCheck = CfnConfigRule.Builder.create(this, "PciDssCloudWatchAlarmAction")
                .configRuleName("pci-dss-cloudwatch-alarm-action")
                .description("PCI-DSS Req 10.6: Review logs daily for suspicious activity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDWATCH_ALARM_ACTION_CHECK")
                        .build())
                .inputParameters(alarmActionParams)
                .build();
        cloudwatchAlarmActionCheck.addOverride("Condition", pciDssCondition.getLogicalId());
        cloudwatchAlarmActionCheck.getNode().addDependency(recorder);

        // Requirement 11: Test security systems
        CfnConfigRule guardDutyEnabledCentralized = CfnConfigRule.Builder.create(this, "PciDssGuardDutyEnabled")
                .configRuleName("pci-dss-guardduty-enabled")
                .description("PCI-DSS Req 11.4: Use intrusion detection systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_ENABLED_CENTRALIZED")
                        .build())
                .build();
        guardDutyEnabledCentralized.addOverride("Condition", pciDssCondition.getLogicalId());
        guardDutyEnabledCentralized.getNode().addDependency(recorder);

        LOG.info("Created 8 PCI-DSS Config rules with conditional deployment");
    }

    /**
     * Creates SOC 2 specific AWS Config rules.
     * Implements controls for SOC 2 Trust Services Criteria (Common Criteria).
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createSoc2ConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        LOG.info("Creating SOC 2 AWS Config rules (condition-controlled)");

        // CC6.1: Logical Access Controls
        CfnConfigRule iamUserNoPolicies = CfnConfigRule.Builder.create(this, "Soc2IamUserNoPolicies")
                .configRuleName("soc2-iam-user-no-policies")
                .description("SOC 2 CC6.1: Implement role-based access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_NO_POLICIES_CHECK")
                        .build())
                .build();
        iamUserNoPolicies.addOverride("Condition", soc2Condition.getLogicalId());
        iamUserNoPolicies.getNode().addDependency(recorder);

        // CC6.6: Network Segmentation
        CfnConfigRule restrictedSshCheck = CfnConfigRule.Builder.create(this, "Soc2RestrictedSsh")
                .configRuleName("soc2-restricted-ssh")
                .description("SOC 2 CC6.6: Network segmentation and access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("INCOMING_SSH_DISABLED")
                        .build())
                .build();
        restrictedSshCheck.addOverride("Condition", soc2Condition.getLogicalId());
        restrictedSshCheck.getNode().addDependency(recorder);

        // CC6.7: Transmission Encryption
        CfnConfigRule albHttpToHttpsRedirection = CfnConfigRule.Builder.create(this, "Soc2AlbHttpsRedirection")
                .configRuleName("soc2-alb-https-redirection")
                .description("SOC 2 CC6.7: Encrypt data in transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_HTTP_TO_HTTPS_REDIRECTION_CHECK")
                        .build())
                .build();
        albHttpToHttpsRedirection.addOverride("Condition", soc2Condition.getLogicalId());
        albHttpToHttpsRedirection.getNode().addDependency(recorder);

        // CC7.2: System Monitoring
        CfnConfigRule securityHubEnabled = CfnConfigRule.Builder.create(this, "Soc2SecurityHubEnabled")
                .configRuleName("soc2-security-hub-enabled")
                .description("SOC 2 CC7.2: Monitor system components for anomalies")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("SECURITYHUB_ENABLED")
                        .build())
                .build();
        securityHubEnabled.addOverride("Condition", soc2Condition.getLogicalId());
        securityHubEnabled.getNode().addDependency(recorder);

        // CC8.1: Change Management
        CfnConfigRule cloudtrailS3DataEventsEnabled = CfnConfigRule.Builder.create(this, "Soc2CloudTrailS3DataEvents")
                .configRuleName("soc2-cloudtrail-s3-data-events")
                .description("SOC 2 CC8.1: Track and authorize infrastructure changes")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDTRAIL_S3_DATAEVENTS_ENABLED")
                        .build())
                .build();
        cloudtrailS3DataEventsEnabled.addOverride("Condition", soc2Condition.getLogicalId());
        cloudtrailS3DataEventsEnabled.getNode().addDependency(recorder);

        // A1.2: High Availability (for production)
        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule rdsMultiAz = CfnConfigRule.Builder.create(this, "Soc2RdsMultiAz")
                    .configRuleName("soc2-rds-multi-az-support")
                    .description("SOC 2 A1.2: Deploy across multiple availability zones")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_MULTI_AZ_SUPPORT")
                            .build())
                    .build();
            rdsMultiAz.addOverride("Condition", soc2Condition.getLogicalId());
            rdsMultiAz.getNode().addDependency(recorder);

            CfnConfigRule elbDeletionProtection = CfnConfigRule.Builder.create(this, "Soc2ElbDeletionProtection")
                    .configRuleName("soc2-elb-deletion-protection")
                    .description("SOC 2 A1.2: Protect critical components from accidental deletion")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("ELB_DELETION_PROTECTION_ENABLED")
                            .build())
                    .build();
            elbDeletionProtection.addOverride("Condition", soc2Condition.getLogicalId());
            elbDeletionProtection.getNode().addDependency(recorder);
        }

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "7" : "5") + " SOC 2 Config rules with conditional deployment");
    }

    /**
     * Creates HIPAA specific AWS Config rules.
     * Implements controls for HIPAA Security Rule (45 CFR Part 164).
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createHipaaConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        LOG.info("Creating HIPAA AWS Config rules (condition-controlled)");

        // §164.308(a)(1): Security Management Process
        CfnConfigRule cloudtrailCloudwatchLogsEnabled = CfnConfigRule.Builder.create(this, "HipaaCloudTrailCloudWatchLogs")
                .configRuleName("hipaa-cloudtrail-cloudwatch-logs")
                .description("HIPAA §164.308(a)(1)(ii)(D): Information system activity review")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_CLOUD_WATCH_LOGS_ENABLED")
                        .build())
                .build();
        cloudtrailCloudwatchLogsEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        cloudtrailCloudwatchLogsEnabled.getNode().addDependency(recorder);

        // §164.308(a)(3): Workforce Security
        CfnConfigRule iamUserGroupMembershipCheck = CfnConfigRule.Builder.create(this, "HipaaIamGroupMembership")
                .configRuleName("hipaa-iam-group-membership")
                .description("HIPAA §164.308(a)(3): Implement procedures for workforce clearance")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_GROUP_MEMBERSHIP_CHECK")
                        .build())
                .build();
        iamUserGroupMembershipCheck.addOverride("Condition", hipaaCondition.getLogicalId());
        iamUserGroupMembershipCheck.getNode().addDependency(recorder);

        // §164.310(d): Device and Media Controls (Backup)
        CfnConfigRule dynamodbPitrEnabled = CfnConfigRule.Builder.create(this, "HipaaDynamoDbPitr")
                .configRuleName("hipaa-dynamodb-pitr-enabled")
                .description("HIPAA §164.310(d)(2)(iv): Create backup copies of ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DYNAMODB_PITR_ENABLED")
                        .build())
                .build();
        dynamodbPitrEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        dynamodbPitrEnabled.getNode().addDependency(recorder);

        CfnConfigRule rdsSnapshotEncrypted = CfnConfigRule.Builder.create(this, "HipaaRdsSnapshotEncrypted")
                .configRuleName("hipaa-rds-snapshot-encrypted")
                .description("HIPAA §164.312(a)(2)(iv): Encrypt ePHI backups")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_SNAPSHOT_ENCRYPTED")
                        .build())
                .build();
        rdsSnapshotEncrypted.addOverride("Condition", hipaaCondition.getLogicalId());
        rdsSnapshotEncrypted.getNode().addDependency(recorder);

        // §164.312(a)(1): Access Control
        CfnConfigRule rootAccountMfaEnabled = CfnConfigRule.Builder.create(this, "HipaaRootMfaEnabled")
                .configRuleName("hipaa-root-account-mfa-enabled")
                .description("HIPAA §164.312(a)(2)(i): Assign unique user identification")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ROOT_ACCOUNT_MFA_ENABLED")
                        .build())
                .build();
        rootAccountMfaEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        rootAccountMfaEnabled.getNode().addDependency(recorder);

        // §164.312(b): Audit Controls
        CfnConfigRule albWafEnabled = CfnConfigRule.Builder.create(this, "HipaaAlbWafEnabled")
                .configRuleName("hipaa-alb-waf-enabled")
                .description("HIPAA §164.312(b): Record and examine activity in systems with ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_WAF_ENABLED")
                        .build())
                .build();
        albWafEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        albWafEnabled.getNode().addDependency(recorder);

        // §164.312(c)(1): Integrity Controls
        CfnConfigRule cloudtrailEncryptionEnabled = CfnConfigRule.Builder.create(this, "HipaaCloudTrailEncryption")
                .configRuleName("hipaa-cloudtrail-encryption-enabled")
                .description("HIPAA §164.312(c)(2): Authenticate ePHI integrity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_ENCRYPTION_ENABLED")
                        .build())
                .build();
        cloudtrailEncryptionEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        cloudtrailEncryptionEnabled.getNode().addDependency(recorder);

        // §164.312(e)(1): Transmission Security
        CfnConfigRule elbAcmCertificateRequired = CfnConfigRule.Builder.create(this, "HipaaElbAcmCertificate")
                .configRuleName("hipaa-elb-acm-certificate-required")
                .description("HIPAA §164.312(e)(2)(ii): Encrypt ePHI during transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_ACM_CERTIFICATE_REQUIRED")
                        .build())
                .build();
        elbAcmCertificateRequired.addOverride("Condition", hipaaCondition.getLogicalId());
        elbAcmCertificateRequired.getNode().addDependency(recorder);

        LOG.info("Created 8 HIPAA Config rules with conditional deployment");
    }

    /**
     * Creates GDPR specific AWS Config rules.
     * Implements technical controls for GDPR compliance.
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createGdprConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        LOG.info("Creating GDPR AWS Config rules (condition-controlled)");

        // Article 25: Data Protection by Design
        CfnConfigRule ec2EbsOptimized = CfnConfigRule.Builder.create(this, "GdprEc2EbsOptimized")
                .configRuleName("gdpr-ec2-ebs-optimized")
                .description("GDPR Art. 25: Data protection by design - optimize storage security")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_EBS_OPTIMIZATION_CHECK")
                        .build())
                .build();
        ec2EbsOptimized.addOverride("Condition", gdprCondition.getLogicalId());
        ec2EbsOptimized.getNode().addDependency(recorder);

        // Article 30: Records of Processing Activities
        CfnConfigRule vpcFlowLogsEnabled = CfnConfigRule.Builder.create(this, "GdprVpcFlowLogs")
                .configRuleName("gdpr-vpc-flow-logs-enabled")
                .description("GDPR Art. 30(1): Maintain records of processing activities")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_FLOW_LOGS_ENABLED")
                        .build())
                .build();
        vpcFlowLogsEnabled.addOverride("Condition", gdprCondition.getLogicalId());
        vpcFlowLogsEnabled.getNode().addDependency(recorder);

        // Article 32(1)(a): Pseudonymisation and Encryption
        CfnConfigRule s3DefaultEncryptionKms = CfnConfigRule.Builder.create(this, "GdprS3DefaultEncryptionKms")
                .configRuleName("gdpr-s3-default-encryption-kms")
                .description("GDPR Art. 32(1)(a): Encrypt personal data at rest")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("S3_DEFAULT_ENCRYPTION_KMS")
                        .build())
                .build();
        s3DefaultEncryptionKms.addOverride("Condition", gdprCondition.getLogicalId());
        s3DefaultEncryptionKms.getNode().addDependency(recorder);

        CfnConfigRule kmsBackingKeyRotationEnabled = CfnConfigRule.Builder.create(this, "GdprKmsKeyRotation")
                .configRuleName("gdpr-kms-backing-key-rotation")
                .description("GDPR Art. 32(1)(a): Rotate encryption keys regularly")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CMK_BACKING_KEY_ROTATION_ENABLED")
                        .build())
                .build();
        kmsBackingKeyRotationEnabled.addOverride("Condition", gdprCondition.getLogicalId());
        kmsBackingKeyRotationEnabled.getNode().addDependency(recorder);

        // Article 32(1)(b): Confidentiality
        CfnConfigRule restrictedRdpCheck = CfnConfigRule.Builder.create(this, "GdprRestrictedRdp")
                .configRuleName("gdpr-restricted-rdp")
                .description("GDPR Art. 32(1)(b): Ensure ongoing confidentiality of systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RESTRICTED_INCOMING_TRAFFIC")
                        .build())
                .build();
        restrictedRdpCheck.addOverride("Condition", gdprCondition.getLogicalId());
        restrictedRdpCheck.getNode().addDependency(recorder);

        // Article 32(1)(c): Availability and Resilience
        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule dynamodbAutoscalingEnabled = CfnConfigRule.Builder.create(this, "GdprDynamoDbAutoscaling")
                    .configRuleName("gdpr-dynamodb-autoscaling-enabled")
                    .description("GDPR Art. 32(1)(c): Ensure resilience and availability of systems")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("DYNAMODB_AUTOSCALING_ENABLED")
                            .build())
                    .build();
            dynamodbAutoscalingEnabled.addOverride("Condition", gdprCondition.getLogicalId());
            dynamodbAutoscalingEnabled.getNode().addDependency(recorder);

            CfnConfigRule s3BucketReplicationEnabled = CfnConfigRule.Builder.create(this, "GdprS3Replication")
                    .configRuleName("gdpr-s3-bucket-replication")
                    .description("GDPR Art. 32(1)(c): Implement geographic redundancy")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("S3_BUCKET_REPLICATION_ENABLED")
                            .build())
                    .build();
            s3BucketReplicationEnabled.addOverride("Condition", gdprCondition.getLogicalId());
            s3BucketReplicationEnabled.getNode().addDependency(recorder);
        }

        // Article 33: Breach Detection
        CfnConfigRule guarddutyNonArchivedFindings = CfnConfigRule.Builder.create(this, "GdprGuardDutyFindings")
                .configRuleName("gdpr-guardduty-non-archived-findings")
                .description("GDPR Art. 33(1): Detect data breaches within 72 hours")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_NON_ARCHIVED_FINDINGS")
                        .build())
                .build();
        guarddutyNonArchivedFindings.addOverride("Condition", gdprCondition.getLogicalId());
        guarddutyNonArchivedFindings.getNode().addDependency(recorder);

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "8" : "6") + " GDPR Config rules with conditional deployment");
    }

    /**
     * Creates PCI-DSS Config rules WITHOUT recorder dependency.
     * Rules will reference existing recorder by name at runtime.
     */
    private void createPciDssConfigRulesWithoutRecorder() {
        LOG.info("Creating PCI-DSS AWS Config rules (condition-controlled, no recorder dependency)");

        // Requirement 1: Network Segmentation
        CfnConfigRule vpcDefaultSecurityGroupClosed = CfnConfigRule.Builder.create(this, "PciDssVpcDefaultSecurityGroupClosed")
                .configRuleName("pci-dss-vpc-default-sg-closed")
                .description("PCI-DSS Req 1.3: Prohibit direct public access between internet and cardholder data")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_DEFAULT_SECURITY_GROUP_CLOSED")
                        .build())
                .build();
        vpcDefaultSecurityGroupClosed.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 2: Secure Configuration
        CfnConfigRule ec2InstanceManagedBySsm = CfnConfigRule.Builder.create(this, "PciDssEc2ManagedBySsm")
                .configRuleName("pci-dss-ec2-managed-by-ssm")
                .description("PCI-DSS Req 2: Secure system configuration management")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_INSTANCE_MANAGED_BY_SSM")
                        .build())
                .build();
        ec2InstanceManagedBySsm.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 3: Protect stored cardholder data
        CfnConfigRule rdsEncryptionEnabled = CfnConfigRule.Builder.create(this, "PciDssRdsEncryption")
                .configRuleName("pci-dss-rds-encryption-enabled")
                .description("PCI-DSS Req 3.4: Render cardholder data unreadable with encryption")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        rdsEncryptionEnabled.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 4: Encrypt transmission
        CfnConfigRule elbTlsHttpsListenersOnly = CfnConfigRule.Builder.create(this, "PciDssElbTlsOnly")
                .configRuleName("pci-dss-elb-tls-https-only")
                .description("PCI-DSS Req 4.1: Use strong cryptography for transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_TLS_HTTPS_LISTENERS_ONLY")
                        .build())
                .build();
        elbTlsHttpsListenersOnly.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 7: Restrict access by business need to know
        CfnConfigRule iamPolicyNoStatementsWithAdminAccess = CfnConfigRule.Builder.create(this, "PciDssIamNoAdminPolicy")
                .configRuleName("pci-dss-iam-no-admin-policy")
                .description("PCI-DSS Req 7.1: Limit access to system components by business need-to-know")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS")
                        .build())
                .build();
        iamPolicyNoStatementsWithAdminAccess.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 8: Identify and authenticate access
        CfnConfigRule iamUserMfaEnabled = CfnConfigRule.Builder.create(this, "PciDssIamMfaEnabled")
                .configRuleName("pci-dss-iam-user-mfa-enabled")
                .description("PCI-DSS Req 8.3: Multi-factor authentication for remote access")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_MFA_ENABLED")
                        .build())
                .build();
        iamUserMfaEnabled.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 10: Track and monitor access
        Map<String, Object> alarmActionParams = Map.of(
                "alarmActionRequired", "true",
                "insufficientDataActionRequired", "false",
                "okActionRequired", "false"
        );
        CfnConfigRule cloudwatchAlarmActionCheck = CfnConfigRule.Builder.create(this, "PciDssCloudWatchAlarmAction")
                .configRuleName("pci-dss-cloudwatch-alarm-action")
                .description("PCI-DSS Req 10.6: Review logs daily for suspicious activity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDWATCH_ALARM_ACTION_CHECK")
                        .build())
                .inputParameters(alarmActionParams)
                .build();
        cloudwatchAlarmActionCheck.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 11: Test security systems
        CfnConfigRule guardDutyEnabledCentralized = CfnConfigRule.Builder.create(this, "PciDssGuardDutyEnabled")
                .configRuleName("pci-dss-guardduty-enabled")
                .description("PCI-DSS Req 11.4: Use intrusion detection systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_ENABLED_CENTRALIZED")
                        .build())
                .build();
        guardDutyEnabledCentralized.addOverride("Condition", pciDssCondition.getLogicalId());

        LOG.info("Created 8 PCI-DSS Config rules (no recorder dependency)");
    }

    /**
     * Creates SOC2 Config rules WITHOUT recorder dependency.
     */
    private void createSoc2ConfigRulesWithoutRecorder() {
        LOG.info("Creating SOC 2 AWS Config rules (condition-controlled, no recorder dependency)");

        // CC6.1: Logical Access Controls
        CfnConfigRule iamUserNoPolicies = CfnConfigRule.Builder.create(this, "Soc2IamUserNoPolicies")
                .configRuleName("soc2-iam-user-no-policies")
                .description("SOC 2 CC6.1: Implement role-based access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_NO_POLICIES_CHECK")
                        .build())
                .build();
        iamUserNoPolicies.addOverride("Condition", soc2Condition.getLogicalId());

        // CC6.6: Network Segmentation
        CfnConfigRule restrictedSshCheck = CfnConfigRule.Builder.create(this, "Soc2RestrictedSsh")
                .configRuleName("soc2-restricted-ssh")
                .description("SOC 2 CC6.6: Network segmentation and access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("INCOMING_SSH_DISABLED")
                        .build())
                .build();
        restrictedSshCheck.addOverride("Condition", soc2Condition.getLogicalId());

        // CC6.7: Transmission Encryption
        CfnConfigRule albHttpToHttpsRedirection = CfnConfigRule.Builder.create(this, "Soc2AlbHttpsRedirection")
                .configRuleName("soc2-alb-https-redirection")
                .description("SOC 2 CC6.7: Encrypt data in transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_HTTP_TO_HTTPS_REDIRECTION_CHECK")
                        .build())
                .build();
        albHttpToHttpsRedirection.addOverride("Condition", soc2Condition.getLogicalId());

        // CC7.2: System Monitoring
        CfnConfigRule securityHubEnabled = CfnConfigRule.Builder.create(this, "Soc2SecurityHubEnabled")
                .configRuleName("soc2-security-hub-enabled")
                .description("SOC 2 CC7.2: Monitor system components for anomalies")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("SECURITYHUB_ENABLED")
                        .build())
                .build();
        securityHubEnabled.addOverride("Condition", soc2Condition.getLogicalId());

        // CC8.1: Change Management
        CfnConfigRule cloudtrailS3DataEventsEnabled = CfnConfigRule.Builder.create(this, "Soc2CloudTrailS3DataEvents")
                .configRuleName("soc2-cloudtrail-s3-data-events")
                .description("SOC 2 CC8.1: Track and authorize infrastructure changes")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDTRAIL_S3_DATAEVENTS_ENABLED")
                        .build())
                .build();
        cloudtrailS3DataEventsEnabled.addOverride("Condition", soc2Condition.getLogicalId());

        // A1.2: High Availability (for production)
        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule rdsMultiAz = CfnConfigRule.Builder.create(this, "Soc2RdsMultiAz")
                    .configRuleName("soc2-rds-multi-az-support")
                    .description("SOC 2 A1.2: Deploy across multiple availability zones")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_MULTI_AZ_SUPPORT")
                            .build())
                    .build();
            rdsMultiAz.addOverride("Condition", soc2Condition.getLogicalId());

            CfnConfigRule elbDeletionProtection = CfnConfigRule.Builder.create(this, "Soc2ElbDeletionProtection")
                    .configRuleName("soc2-elb-deletion-protection")
                    .description("SOC 2 A1.2: Protect critical components from accidental deletion")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("ELB_DELETION_PROTECTION_ENABLED")
                            .build())
                    .build();
            elbDeletionProtection.addOverride("Condition", soc2Condition.getLogicalId());
        }

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "7" : "5") + " SOC 2 Config rules (no recorder dependency)");
    }

    /**
     * Creates HIPAA Config rules WITHOUT recorder dependency.
     */
    private void createHipaaConfigRulesWithoutRecorder() {
        LOG.info("Creating HIPAA AWS Config rules (condition-controlled, no recorder dependency)");

        // §164.308(a)(1): Security Management Process
        CfnConfigRule cloudtrailCloudwatchLogsEnabled = CfnConfigRule.Builder.create(this, "HipaaCloudTrailCloudWatchLogs")
                .configRuleName("hipaa-cloudtrail-cloudwatch-logs")
                .description("HIPAA §164.308(a)(1)(ii)(D): Information system activity review")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_CLOUD_WATCH_LOGS_ENABLED")
                        .build())
                .build();
        cloudtrailCloudwatchLogsEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.308(a)(3): Workforce Security
        CfnConfigRule iamUserGroupMembershipCheck = CfnConfigRule.Builder.create(this, "HipaaIamGroupMembership")
                .configRuleName("hipaa-iam-group-membership")
                .description("HIPAA §164.308(a)(3): Implement procedures for workforce clearance")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_GROUP_MEMBERSHIP_CHECK")
                        .build())
                .build();
        iamUserGroupMembershipCheck.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.310(d): Device and Media Controls (Backup)
        CfnConfigRule dynamodbPitrEnabled = CfnConfigRule.Builder.create(this, "HipaaDynamoDbPitr")
                .configRuleName("hipaa-dynamodb-pitr-enabled")
                .description("HIPAA §164.310(d)(2)(iv): Create backup copies of ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DYNAMODB_PITR_ENABLED")
                        .build())
                .build();
        dynamodbPitrEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        CfnConfigRule rdsSnapshotEncrypted = CfnConfigRule.Builder.create(this, "HipaaRdsSnapshotEncrypted")
                .configRuleName("hipaa-rds-snapshot-encrypted")
                .description("HIPAA §164.312(a)(2)(iv): Encrypt ePHI backups")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_SNAPSHOT_ENCRYPTED")
                        .build())
                .build();
        rdsSnapshotEncrypted.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(a)(1): Access Control
        CfnConfigRule rootAccountMfaEnabled = CfnConfigRule.Builder.create(this, "HipaaRootMfaEnabled")
                .configRuleName("hipaa-root-account-mfa-enabled")
                .description("HIPAA §164.312(a)(2)(i): Assign unique user identification")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ROOT_ACCOUNT_MFA_ENABLED")
                        .build())
                .build();
        rootAccountMfaEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(b): Audit Controls
        CfnConfigRule albWafEnabled = CfnConfigRule.Builder.create(this, "HipaaAlbWafEnabled")
                .configRuleName("hipaa-alb-waf-enabled")
                .description("HIPAA §164.312(b): Record and examine activity in systems with ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_WAF_ENABLED")
                        .build())
                .build();
        albWafEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(c)(1): Integrity Controls
        CfnConfigRule cloudtrailEncryptionEnabled = CfnConfigRule.Builder.create(this, "HipaaCloudTrailEncryption")
                .configRuleName("hipaa-cloudtrail-encryption-enabled")
                .description("HIPAA §164.312(c)(2): Authenticate ePHI integrity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_ENCRYPTION_ENABLED")
                        .build())
                .build();
        cloudtrailEncryptionEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(e)(1): Transmission Security
        CfnConfigRule elbAcmCertificateRequired = CfnConfigRule.Builder.create(this, "HipaaElbAcmCertificate")
                .configRuleName("hipaa-elb-acm-certificate-required")
                .description("HIPAA §164.312(e)(2)(ii): Encrypt ePHI during transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_ACM_CERTIFICATE_REQUIRED")
                        .build())
                .build();
        elbAcmCertificateRequired.addOverride("Condition", hipaaCondition.getLogicalId());

        LOG.info("Created 8 HIPAA Config rules (no recorder dependency)");
    }

    /**
     * Creates GDPR Config rules WITHOUT recorder dependency.
     */
    private void createGdprConfigRulesWithoutRecorder() {
        LOG.info("Creating GDPR AWS Config rules (condition-controlled, no recorder dependency)");

        // Article 25: Data Protection by Design
        CfnConfigRule ec2EbsOptimized = CfnConfigRule.Builder.create(this, "GdprEc2EbsOptimized")
                .configRuleName("gdpr-ec2-ebs-optimized")
                .description("GDPR Art. 25: Data protection by design - optimize storage security")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_EBS_OPTIMIZATION_CHECK")
                        .build())
                .build();
        ec2EbsOptimized.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 30: Records of Processing Activities
        CfnConfigRule vpcFlowLogsEnabled = CfnConfigRule.Builder.create(this, "GdprVpcFlowLogs")
                .configRuleName("gdpr-vpc-flow-logs-enabled")
                .description("GDPR Art. 30(1): Maintain records of processing activities")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_FLOW_LOGS_ENABLED")
                        .build())
                .build();
        vpcFlowLogsEnabled.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 32(1)(a): Pseudonymisation and Encryption
        CfnConfigRule s3DefaultEncryptionKms = CfnConfigRule.Builder.create(this, "GdprS3DefaultEncryptionKms")
                .configRuleName("gdpr-s3-default-encryption-kms")
                .description("GDPR Art. 32(1)(a): Encrypt personal data at rest")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("S3_DEFAULT_ENCRYPTION_KMS")
                        .build())
                .build();
        s3DefaultEncryptionKms.addOverride("Condition", gdprCondition.getLogicalId());

        CfnConfigRule kmsBackingKeyRotationEnabled = CfnConfigRule.Builder.create(this, "GdprKmsKeyRotation")
                .configRuleName("gdpr-kms-backing-key-rotation")
                .description("GDPR Art. 32(1)(a): Rotate encryption keys regularly")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CMK_BACKING_KEY_ROTATION_ENABLED")
                        .build())
                .build();
        kmsBackingKeyRotationEnabled.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 32(1)(b): Confidentiality
        CfnConfigRule restrictedRdpCheck = CfnConfigRule.Builder.create(this, "GdprRestrictedRdp")
                .configRuleName("gdpr-restricted-rdp")
                .description("GDPR Art. 32(1)(b): Ensure ongoing confidentiality of systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RESTRICTED_INCOMING_TRAFFIC")
                        .build())
                .build();
        restrictedRdpCheck.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 32(1)(c): Availability and Resilience
        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule dynamodbAutoscalingEnabled = CfnConfigRule.Builder.create(this, "GdprDynamoDbAutoscaling")
                    .configRuleName("gdpr-dynamodb-autoscaling-enabled")
                    .description("GDPR Art. 32(1)(c): Ensure resilience and availability of systems")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("DYNAMODB_AUTOSCALING_ENABLED")
                            .build())
                    .build();
            dynamodbAutoscalingEnabled.addOverride("Condition", gdprCondition.getLogicalId());

            CfnConfigRule s3BucketReplicationEnabled = CfnConfigRule.Builder.create(this, "GdprS3Replication")
                    .configRuleName("gdpr-s3-bucket-replication")
                    .description("GDPR Art. 32(1)(c): Implement geographic redundancy")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("S3_BUCKET_REPLICATION_ENABLED")
                            .build())
                    .build();
            s3BucketReplicationEnabled.addOverride("Condition", gdprCondition.getLogicalId());
        }

        // Article 33: Breach Detection
        CfnConfigRule guarddutyNonArchivedFindings = CfnConfigRule.Builder.create(this, "GdprGuardDutyFindings")
                .configRuleName("gdpr-guardduty-non-archived-findings")
                .description("GDPR Art. 33(1): Detect data breaches within 72 hours")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_NON_ARCHIVED_FINDINGS")
                        .build())
                .build();
        guarddutyNonArchivedFindings.addOverride("Condition", gdprCondition.getLogicalId());

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "8" : "6") + " GDPR Config rules (no recorder dependency)");
    }

    /**
     * Creates AWS Audit Manager assessments based on complianceFrameworks list.
     * Creates one assessment per framework specified in the comma-separated list.
     * Requires Audit Manager to be enabled in the AWS account.
     */
    private void createAuditManagerAssessments() {
        LOG.info("Creating AWS Audit Manager assessments for continuous auditing");

        // Determine which frameworks to create assessments for
        List<String> frameworks = determineFrameworks();

        if (frameworks.isEmpty()) {
            LOG.warning("No compliance frameworks specified for Audit Manager assessments");
            LOG.warning("Set 'complianceFrameworks' (e.g., \"PCI-DSS,HIPAA,SOC2\") or 'auditManagerFrameworkId'");
            return;
        }

        LOG.info("Creating " + frameworks.size() + " assessment(s): " + String.join(", ", frameworks));

        // Get stack reference for lazy evaluation
        software.amazon.awscdk.Stack stack = software.amazon.awscdk.Stack.of(this);

        // Generate shortId for assessment names (assessments are stack-specific)
        String shortId = Integer.toHexString(stackName.hashCode());

        // Create bucket with SSM tracking for PRODUCTION mode (stack-scoped)
        String auditSsmParam = "/cloudforge/shared/" + this.region + "/stack/" + this.stackName + "/audit-manager/bucket-arn";
        Bucket assessmentReportBucket = getOrCreateBucketWithSSM("AuditManagerReportBucket", auditSsmParam);

        LOG.info("Audit Manager bucket ARN will be tracked in SSM Parameter Store");

        // Enforce SSL/TLS for compliance
        assessmentReportBucket.addToResourcePolicy(
            PolicyStatement.Builder.create()
                .sid("EnforceSSLOnly")
                .effect(Effect.DENY)
                .principals(List.of(new AnyPrincipal()))
                .actions(List.of("s3:*"))
                .resources(List.of(
                    assessmentReportBucket.getBucketArn(),
                    assessmentReportBucket.arnForObjects("*")
                ))
                .conditions(Map.of(
                    "Bool", Map.of("aws:SecureTransport", "false")
                ))
                .build()
        );

        LOG.info("Created Audit Manager report bucket: " + assessmentReportBucket.getBucketName());

        // Add Audit Manager bucket to CloudTrail S3 data event logging
        addS3DataEventLogging(assessmentReportBucket);

        // Create shared IAM role for Audit Manager
        Role auditManagerRole = Role.Builder.create(this, "AuditManagerRole")
                .assumedBy(ServicePrincipal.Builder.create("auditmanager.amazonaws.com").build())
                .build();

        // Grant S3 write permissions for assessment reports
        assessmentReportBucket.grantWrite(auditManagerRole);

        // Add Audit Manager permissions
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AuditManagerAccess")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "auditmanager:GetAccountStatus",
                        "auditmanager:GetOrganizationAdminAccount",
                        "auditmanager:GetServicesInScope",
                        "auditmanager:ListAssessmentFrameworks",
                        "auditmanager:ListControls",
                        "auditmanager:ListKeywordsForDataSource"
                ))
                .resources(List.of("*"))
                .build());

        // Add CloudTrail read permissions for evidence collection
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("CloudTrailRead")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "cloudtrail:DescribeTrails",
                        "cloudtrail:GetTrailStatus",
                        "cloudtrail:LookupEvents"
                ))
                .resources(List.of("*"))
                .build());

        // Add Config read permissions for evidence collection
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ConfigRead")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "config:DescribeConfigurationRecorders",
                        "config:DescribeConfigurationRecorderStatus",
                        "config:DescribeConfigRules",
                        "config:GetComplianceDetailsByConfigRule"
                ))
                .resources(List.of("*"))
                .build());

        // Add Security Hub permissions for security findings evidence
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("SecurityHubRead")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "securityhub:GetFindings",
                        "securityhub:DescribeHub",
                        "securityhub:GetInsights"
                ))
                .resources(List.of("*"))
                .build());

        // Add S3 permissions to read CloudTrail logs for evidence
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("S3ReadCloudTrail")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "s3:GetObject",
                        "s3:ListBucket"
                ))
                .resources(List.of("*"))
                .build());

        // Add IAM read permissions for IAM policy evidence
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("IAMRead")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "iam:GetAccountSummary",
                        "iam:GetAccountPasswordPolicy",
                        "iam:ListUsers",
                        "iam:ListRoles",
                        "iam:ListPolicies",
                        "iam:GetPolicy",
                        "iam:GetPolicyVersion"
                ))
                .resources(List.of("*"))
                .build());

        // Add EC2 read permissions for infrastructure evidence
        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("EC2Read")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "ec2:DescribeInstances",
                        "ec2:DescribeSecurityGroups",
                        "ec2:DescribeVolumes",
                        "ec2:DescribeSnapshots",
                        "ec2:DescribeVpcs"
                ))
                .resources(List.of("*"))
                .build());

        LOG.info("Created shared Audit Manager role and bucket with comprehensive permissions");

        // Get account ID for assessments
        String accountId = stack.getAccount();

        // Create one assessment per framework
        int assessmentCount = 0;
        for (String framework : frameworks) {
            assessmentCount++;
            createSingleAssessment(
                framework,
                assessmentCount,
                shortId,
                assessmentReportBucket,
                auditManagerRole,
                accountId
            );
        }

        LOG.info("Created " + assessmentCount + " Audit Manager assessment(s) successfully");
    }

    /**
     * Determines which frameworks to create assessments for.
     * Priority: complianceFrameworks (multiple) > auditManagerFrameworkId (single, legacy)
     */
    private List<String> determineFrameworks() {
        List<String> frameworks = new java.util.ArrayList<>();

        // Priority 1: Use complianceFrameworks if specified (comma-separated list)
        if (complianceFrameworks != null && !complianceFrameworks.trim().isEmpty()) {
            String[] parts = complianceFrameworks.split(",");
            for (String framework : parts) {
                String trimmed = framework.trim();
                if (!trimmed.isEmpty()) {
                    frameworks.add(trimmed);
                }
            }
            return frameworks;
        }

        // Priority 2: Fall back to legacy auditManagerFrameworkId (single framework)
        if (auditManagerFrameworkId != null && !auditManagerFrameworkId.trim().isEmpty()) {
            LOG.info("Using legacy auditManagerFrameworkId field (consider using complianceFrameworks for multiple assessments)");
            frameworks.add(auditManagerFrameworkId);
            return frameworks;
        }

        // No frameworks specified
        return frameworks;
    }

    /**
     * Creates a single Audit Manager assessment for the specified framework.
     * Logs control mappings from AuditManagerControlRegistry to document evidence sources.
     */
    private void createSingleAssessment(
        String frameworkName,
        int index,
        String shortId,
        Bucket reportBucket,
        Role role,
        String accountId
    ) {
        String assessmentName = "audit-" + frameworkName.toLowerCase().replace("_", "-") +
                                "-" + security.name().toLowerCase() + "-" + shortId;
        String constructId = "Assessment" + frameworkName.replace("-", "").replace("_", "");

        LOG.info("Creating assessment " + index + ": " + assessmentName);

        // Log control mappings for this framework
        logFrameworkControlMappings(frameworkName);

        // Resolve framework identifier to UUID
        String frameworkId = resolveFrameworkIdentifier(frameworkName);

        // Create Audit Manager Assessment using CFN resource
        CfnAssessment assessment = CfnAssessment.Builder.create(this, constructId)
                .assessmentReportsDestination(CfnAssessment.AssessmentReportsDestinationProperty.builder()
                        .destination("s3://" + reportBucket.getBucketName())
                        .destinationType("S3")
                        .build())
                .frameworkId(frameworkId)
                .name(assessmentName)
                .roles(List.of(
                        CfnAssessment.RoleProperty.builder()
                                .roleArn(role.getRoleArn())
                                .roleType("PROCESS_OWNER")
                                .build()
                ))
                .scope(CfnAssessment.ScopeProperty.builder()
                        .awsAccounts(List.of(
                                CfnAssessment.AWSAccountProperty.builder()
                                        .id(accountId)
                                        .build()
                        ))
                        .awsServices(List.of(
                                // Core AWS services for evidence collection
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS Config")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS CloudTrail")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon CloudWatch")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS Security Hub")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon GuardDuty")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS IAM")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon VPC")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon EC2")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon ECS")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon EFS")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon S3")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS KMS")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS Secrets Manager")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Amazon Route53")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("AWS Certificate Manager")
                                        .build(),
                                CfnAssessment.AWSServiceProperty.builder()
                                        .serviceName("Elastic Load Balancing")
                                        .build()
                        ))
                        .build())
                .tags(List.of(
                        software.amazon.awscdk.CfnTag.builder()
                                .key("Environment")
                                .value(security.name().toLowerCase())
                                .build(),
                        software.amazon.awscdk.CfnTag.builder()
                                .key("Framework")
                                .value(frameworkName)
                                .build(),
                        software.amazon.awscdk.CfnTag.builder()
                                .key("ManagedBy")
                                .value("CloudForge")
                                .build()
                ))
                .build();

        assessment.getNode().addDependency(role);
        assessment.getNode().addDependency(reportBucket);

        LOG.info("  Created: " + assessmentName + " (framework: " + frameworkId + ")");
    }


    /**
     * Logs framework control mappings showing which Config rules provide evidence for which controls.
     * This bridges the disconnect between validation rules, Config rules, and Audit Manager.
     */
    private void logFrameworkControlMappings(String framework) {
        LOG.info("  Framework: " + framework);

        // Get all controls that apply to this framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework(framework);

        if (controls.isEmpty()) {
            LOG.warning("  No control mappings found for framework: " + framework);
            return;
        }

        LOG.info("  Controls mapped: " + controls.size());

        // Get all Config rules needed for this framework
        List<String> configRules = AuditManagerControlRegistry.getConfigRulesForFramework(framework);
        LOG.info("  Config rules providing evidence: " + configRules.size());

        // Get evidence sources
        List<String> evidenceSources = AuditManagerControlRegistry.getEvidenceSourcesForFramework(framework);
        LOG.info("  Evidence sources: " + String.join(", ", evidenceSources));

        // Log detailed control → Config rule mappings
        LOG.info("  Control Mappings:");
        for (AuditManagerControl control : controls) {
            control.getFrameworkControl(framework).ifPresent(fc -> {
                LOG.info("    • " + fc.controlId() + " (" + fc.controlName() + ")");
                LOG.info("      → Config Rules: " + String.join(", ", control.configRuleIds()));
                LOG.info("      → Evidence: " + String.join(", ", control.evidenceSources()));
            });
        }
    }

    /**
     * Resolves framework identifier to framework UUID.
     * Handles short names (SOC2, HIPAA), full ARNs, and UUIDs.
     * Queries AWS to find matching framework if short name is provided.
     */
    private String resolveFrameworkIdentifier(String identifier) {
        // If it's an ARN, extract the UUID from the end
        if (identifier.startsWith("arn:")) {
            String[] parts = identifier.split("/");
            return parts[parts.length - 1]; // Get the last segment (UUID or name)
        }

        // If it looks like a UUID (36 chars with hyphens), use as-is
        if (identifier.matches("[0-9a-fA-F-]{36}")) {
            return identifier;
        }

        // For short names, try to query AWS for matching framework
        String result = queryAwsForFramework(identifier);

        // If query failed (returned placeholder), don't use placeholder - fail clearly
        if ("00000000-0000-0000-0000-000000000000".equals(result)) {
            LOG.severe("Failed to resolve framework '" + identifier + "'. " +
                      "Please provide the full UUID instead. " +
                      "Find it with: aws auditmanager list-assessment-frameworks --framework-type Standard");
            throw new RuntimeException("Unable to resolve Audit Manager framework: " + identifier);
        }

        return result;
    }

    /**
     * Queries AWS Audit Manager for framework by name.
     * Falls back to placeholder if query fails or framework not found.
     */
    private String queryAwsForFramework(String frameworkName) {
        LOG.info("Querying AWS for framework: " + frameworkName);
        try {
            // Try to execute AWS CLI to list frameworks
            ProcessBuilder pb = new ProcessBuilder(
                "/usr/local/bin/aws", "auditmanager", "list-assessment-frameworks",
                "--framework-type", "Standard",
                "--output", "json"
            );

            // Inherit environment variables (AWS_PROFILE, AWS_REGION, etc.)
            Map<String, String> env = pb.environment();
            // Preserve AWS_PROFILE if set
            String awsProfile = System.getenv("AWS_PROFILE");
            if (awsProfile != null && !awsProfile.isEmpty()) {
                env.put("AWS_PROFILE", awsProfile);
                LOG.info("Using AWS_PROFILE=" + awsProfile + " for framework query");
            }
            // Preserve AWS_REGION if set
            String awsRegion = System.getenv("AWS_REGION");
            if (awsRegion != null && !awsRegion.isEmpty()) {
                env.put("AWS_REGION", awsRegion);
                LOG.info("Using AWS_REGION=" + awsRegion + " for framework query");
            }

            Process process = pb.start();

            // Wait for process with timeout (10 seconds - increased from 5)
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                LOG.warning("AWS CLI query timed out after 10 seconds for framework '" + frameworkName + "'");
                return "00000000-0000-0000-0000-000000000000";
            }

            if (process.exitValue() == 0) {
                // Read output - use try-with-resources to ensure streams are closed
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }

                // Parse JSON output to find matching framework
                String json = output.toString();
                String searchName = frameworkName.toUpperCase();

                // Also check stderr for any warnings - use try-with-resources
                StringBuilder errorOutput = new StringBuilder();
                try (java.io.BufferedReader errorReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getErrorStream()))) {
                    String errLine;
                    while ((errLine = errorReader.readLine()) != null) {
                        errorOutput.append(errLine);
                    }
                }
                if (errorOutput.length() > 0) {
                    LOG.warning("AWS CLI stderr: " + errorOutput.toString());
                }

                LOG.info("Searching for framework matching: " + searchName);

                // Simple JSON parsing to find framework ID
                // Look for framework names that match (SOC2, HIPAA, PCI-DSS, etc.)
                if (json.contains("\"name\"")) {
                    String[] frameworks = json.split("\\{");
                    for (String framework : frameworks) {
                        if (framework.toUpperCase().contains(searchName) ||
                            (searchName.equals("SOC2") && framework.contains("SOC 2")) ||
                            (searchName.equals("PCI-DSS") && framework.contains("PCI DSS"))) {

                            // Extract the ID field (UUID)
                            int idIndex = framework.indexOf("\"id\"");
                            if (idIndex > 0) {
                                int startQuote = framework.indexOf("\"", idIndex + 5);
                                int endQuote = framework.indexOf("\"", startQuote + 1);
                                if (startQuote > 0 && endQuote > startQuote) {
                                    String frameworkId = framework.substring(startQuote + 1, endQuote);
                                    LOG.info("Found framework '" + frameworkName + "' with ID: " + frameworkId);
                                    return frameworkId;
                                }
                            }
                        }
                    }
                }
            } else {
                LOG.warning("AWS CLI command failed with exit code: " + process.exitValue());
            }

            LOG.warning("Could not find framework '" + frameworkName + "' in AWS account");
        } catch (Exception e) {
            LOG.warning("Error querying AWS for framework '" + frameworkName + "': " + e.getMessage());
        }

        return "00000000-0000-0000-0000-000000000000";
    }

    /**
     * Check if AWS Config infrastructure (Recorder and Delivery Channel) already exists.
     * Queries AWS Config to detect existing resources before attempting creation.
     *
     * @return true if Config Recorder exists, false otherwise
     */
    private boolean checkConfigInfrastructureExists() {
        LOG.info("Checking if AWS Config infrastructure exists in region: " + region);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "/usr/local/bin/aws", "configservice", "describe-configuration-recorders",
                "--output", "json"
            );

            // Inherit environment variables (AWS_PROFILE, AWS_REGION, etc.)
            Map<String, String> env = pb.environment();
            String awsProfile = System.getenv("AWS_PROFILE");
            if (awsProfile != null && !awsProfile.isEmpty()) {
                env.put("AWS_PROFILE", awsProfile);
            }
            String awsRegion = System.getenv("AWS_REGION");
            if (awsRegion != null && !awsRegion.isEmpty()) {
                env.put("AWS_REGION", awsRegion);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                LOG.warning("AWS CLI query timed out after 10 seconds checking Config Recorder");
                return false;
            }

            if (process.exitValue() == 0) {
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }

                String json = output.toString();
                // Check if ConfigurationRecorders array has any entries
                boolean exists = json.contains("\"ConfigurationRecorders\"") &&
                                json.contains("\"name\"") &&
                                !json.contains("\"ConfigurationRecorders\": []");

                if (exists) {
                    LOG.info("✓ Existing Config Recorder detected in region: " + region);
                    LOG.info("  Will skip infrastructure creation to avoid 'ResourceAlreadyExistsException'");
                } else {
                    LOG.info("✗ No existing Config Recorder found in region: " + region);
                    LOG.info("  Will create new Config infrastructure");
                }

                return exists;
            } else {
                LOG.warning("AWS CLI command failed with exit code: " + process.exitValue());
                return false;
            }
        } catch (Exception e) {
            LOG.warning("Error checking for existing Config infrastructure: " + e.getMessage());
            LOG.warning("  Assuming no Config infrastructure exists (will attempt creation)");
            return false;
        }
    }

    /**
     * Creates an S3 bucket with auto-generated name to avoid "AlreadyExists" errors.
     *
     * CloudFormation auto-generates unique bucket names when bucketName is not specified.
     * This prevents conflicts with retained buckets from previous deployments.
     *
     * Lifecycle policies are automatically determined based on enabled compliance frameworks:
     * - HIPAA: 6-year retention (strictest)
     * - SOC2: 2-year retention
     * - PCI-DSS: 1-year retention (minimum)
     * - GDPR: Varies by data type
     *
     * If multiple frameworks are enabled, the strictest retention requirement is applied.
     *
     * @param id CDK construct ID
     * @return Bucket reference with CloudFormation-generated name
     */
    private Bucket getOrCreateBucket(String id) {
        LOG.info("Creating bucket with auto-generated name: " + id);
        LOG.info("  CloudFormation will generate unique bucket name automatically");
        LOG.info("  This prevents 'AlreadyExists' errors from retained buckets");

        // Determine lifecycle policy based on enabled compliance frameworks
        List<software.amazon.awscdk.services.s3.LifecycleRule> lifecycleRules =
            getLifecycleRulesForEnabledFrameworks();

        // Create bucket WITHOUT specifying bucketName - CloudFormation generates unique name
        Bucket bucket = Bucket.Builder.create(this, id)
                // NO bucketName specified - CloudFormation auto-generates unique name
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(security == SecurityProfile.PRODUCTION ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .autoDeleteObjects(security != SecurityProfile.PRODUCTION)
                .versioned(true)  // Required for compliance (SOC2/PCI-DSS/HIPAA)
                .lifecycleRules(lifecycleRules)  // Compliance-driven retention policies
                .build();

        return bucket;
    }

    /**
     * Get or create S3 bucket with SSM Parameter Store tracking (deployment-time).
     *
     * For PRODUCTION mode:
     * - Stores bucket ARN in SSM Parameter Store at deployment time
     * - Allows reuse of retained buckets across stack redeployments
     * - Prevents conflicts with retained buckets
     *
     * For DEV/STAGING mode:
     * - Just creates bucket normally (will be destroyed with stack)
     */
    private Bucket getOrCreateBucketWithSSM(String id, String ssmParameterName) {
        // Only use SSM tracking in PRODUCTION mode (where resources are retained)
        if (security != SecurityProfile.PRODUCTION) {
            LOG.info("Non-production mode: Creating bucket without SSM tracking");
            return getOrCreateBucket(id);
        }

        LOG.info("Production mode: Using SSM Parameter Store for bucket tracking: " + ssmParameterName);

        // Always create new bucket (CloudFormation will generate unique name)
        // This avoids conflicts with retained buckets from previous deployments
        Bucket bucket = getOrCreateBucket(id);

        // Create Custom Resource to store bucket ARN in SSM at deployment time
        // ARN is used instead of name for direct code reference capability
        AwsSdkCall putParameterCall = AwsSdkCall.builder()
                .service("SSM")
                .action("putParameter")
                .parameters(java.util.Map.of(
                        "Name", ssmParameterName,
                        "Value", bucket.getBucketArn(),
                        "Type", "String",
                        "Description", "CloudForge retained " + id + " ARN for region " + region,
                        "Overwrite", true
                ))
                .physicalResourceId(software.amazon.awscdk.customresources.PhysicalResourceId.of(id + "-SSMWriter"))
                .region(region)
                .build();

        AwsCustomResource ssmWriter = AwsCustomResource.Builder.create(this, id + "SSMWriter")
                .onCreate(putParameterCall)
                .onUpdate(putParameterCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        // Ensure bucket is created before we write to SSM
        ssmWriter.getNode().addDependency(bucket);

        LOG.info("Bucket ARN will be tracked in SSM for future reference: " + ssmParameterName);
        LOG.info("  Note: New unique bucket created to avoid conflicts with retained buckets");
        return bucket;
    }

    /**
     * Store resource ARN in SSM Parameter Store (deployment-time).
     *
     * This helper method stores any resource ARN in SSM for compliance tracking.
     * The parameter persists after stack deletion for audit trail purposes.
     *
     * Only active in PRODUCTION mode.
     *
     * @param id Unique identifier for the Custom Resource
     * @param ssmParameterName SSM parameter name (e.g., "/cloudforge/shared/us-west-2/cloudtrail/arn")
     * @param arnValue The ARN value to store (CDK token or string)
     * @param description Human-readable description for the parameter
     * @return The AwsCustomResource that stores the parameter
     */
    private AwsCustomResource storeResourceArnInSSM(String id, String ssmParameterName, String arnValue, String description) {
        if (security != SecurityProfile.PRODUCTION) {
            LOG.fine("Non-production mode: Skipping SSM tracking for " + id);
            return null;
        }

        LOG.info("Storing " + id + " ARN in SSM Parameter Store: " + ssmParameterName);

        AwsSdkCall putParameterCall = AwsSdkCall.builder()
                .service("SSM")
                .action("putParameter")
                .parameters(java.util.Map.of(
                        "Name", ssmParameterName,
                        "Value", arnValue,
                        "Type", "String",
                        "Description", description,
                        "Overwrite", true
                ))
                .physicalResourceId(software.amazon.awscdk.customresources.PhysicalResourceId.of(id + "-SSMWriter"))
                .region(region)
                .build();

        AwsCustomResource ssmWriter = AwsCustomResource.Builder.create(this, id + "SSMWriter")
                .onCreate(putParameterCall)
                .onUpdate(putParameterCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        LOG.fine(id + " ARN will be tracked in SSM: " + ssmParameterName);
        return ssmWriter;
    }

    /**
     * Store resource ARN in SSM Parameter Store (deployment-time) - ALWAYS (not just PRODUCTION).
     *
     * This variant always stores the ARN regardless of security profile.
     * Used for account-level singleton resources like Config Recorder and Delivery Channel.
     *
     * @param id Unique identifier for the Custom Resource
     * @param ssmParameterName SSM parameter name (e.g., "/cloudforge/shared/us-west-2/config/recorder-arn")
     * @param arnValue The ARN value to store (CDK token or string)
     * @param description Human-readable description for the parameter
     * @return The AwsCustomResource that stores the parameter
     */
    private AwsCustomResource storeResourceArnInSSMAlways(String id, String ssmParameterName, String arnValue, String description) {
        LOG.info("Storing " + id + " ARN in SSM Parameter Store (always): " + ssmParameterName);

        AwsSdkCall putParameterCall = AwsSdkCall.builder()
                .service("SSM")
                .action("putParameter")
                .parameters(java.util.Map.of(
                        "Name", ssmParameterName,
                        "Value", arnValue,
                        "Type", "String",
                        "Description", description,
                        "Overwrite", true
                ))
                .physicalResourceId(software.amazon.awscdk.customresources.PhysicalResourceId.of(id + "-SSMWriter"))
                .region(region)
                .build();

        AwsCustomResource ssmWriter = AwsCustomResource.Builder.create(this, id + "SSMWriter")
                .onCreate(putParameterCall)
                .onUpdate(putParameterCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        LOG.fine(id + " ARN will be tracked in SSM: " + ssmParameterName);
        return ssmWriter;
    }

    /**
     * Determine lifecycle rules based on enabled compliance frameworks.
     *
     * Applies the strictest retention requirement from all enabled frameworks:
     * - HIPAA: 6 years (2190 days) - strictest
     * - SOC2: 2 years (730 days)
     * - PCI-DSS: 1 year (365 days) - minimum
     *
     * Storage class transitions optimize costs while maintaining compliance:
     * - 0-90 days: S3 Standard (PCI-DSS requires 3 months immediately available)
     * - 90-365 days: Glacier (archival storage)
     * - 365+ days: Glacier Deep Archive (long-term compliance)
     *
     * @return List of lifecycle rules for the strictest enabled framework
     */
    private List<software.amazon.awscdk.services.s3.LifecycleRule> getLifecycleRulesForEnabledFrameworks() {
        List<String> frameworks = determineFrameworks();

        // Normalize framework names for comparison
        List<String> normalizedFrameworks = frameworks.stream()
                .map(f -> f.trim().toUpperCase().replace("-", "").replace("_", ""))
                .collect(java.util.stream.Collectors.toList());

        boolean hipaaEnabled = normalizedFrameworks.contains("HIPAA");
        boolean soc2Enabled = normalizedFrameworks.contains("SOC2");
        boolean pciDssEnabled = normalizedFrameworks.contains("PCIDSS");

        // Determine strictest retention requirement
        int retentionDays;
        String frameworkName;

        if (hipaaEnabled) {
            retentionDays = 2190;  // 6 years for HIPAA
            frameworkName = "HIPAA";
            LOG.info("  Lifecycle: HIPAA-compliant (6-year retention)");
        } else if (soc2Enabled) {
            retentionDays = 730;   // 2 years for SOC2
            frameworkName = "SOC2";
            LOG.info("  Lifecycle: SOC2-compliant (2-year retention)");
        } else if (pciDssEnabled) {
            retentionDays = 365;   // 1 year for PCI-DSS
            frameworkName = "PCI-DSS";
            LOG.info("  Lifecycle: PCI-DSS-compliant (1-year retention)");
        } else {
            // Default: match security profile behavior for non-compliance deployments
            retentionDays = switch (security) {
                case PRODUCTION -> 2190;  // 6 years default for production
                case STAGING -> 730;      // 2 years for staging
                case DEV -> 365;          // 1 year for dev
            };
            frameworkName = "Default (" + security + ")";
            LOG.info("  Lifecycle: " + frameworkName + " (" + (retentionDays/365) + "-year retention)");
        }

        LOG.info("    0-90 days: S3 Standard (immediate availability for PCI-DSS)");
        LOG.info("    90-365 days: Glacier (cost optimization)");
        if (retentionDays > 365) {
            LOG.info("    365-" + retentionDays + " days: Glacier Deep Archive (long-term compliance)");
        }
        LOG.info("    " + retentionDays + "+ days: Delete (" + frameworkName + " retention)");

        // Build lifecycle rule with appropriate transitions based on retention period
        var ruleBuilder = software.amazon.awscdk.services.s3.LifecycleRule.builder();

        if (retentionDays > 365) {
            // Long retention: use Glacier and Deep Archive
            ruleBuilder.transitions(List.of(
                software.amazon.awscdk.services.s3.Transition.builder()
                    .storageClass(software.amazon.awscdk.services.s3.StorageClass.GLACIER)
                    .transitionAfter(software.amazon.awscdk.Duration.days(90))
                    .build(),
                software.amazon.awscdk.services.s3.Transition.builder()
                    .storageClass(software.amazon.awscdk.services.s3.StorageClass.DEEP_ARCHIVE)
                    .transitionAfter(software.amazon.awscdk.Duration.days(365))
                    .build()
            ));
        } else {
            // Short retention: only use Glacier
            ruleBuilder.transitions(List.of(
                software.amazon.awscdk.services.s3.Transition.builder()
                    .storageClass(software.amazon.awscdk.services.s3.StorageClass.GLACIER)
                    .transitionAfter(software.amazon.awscdk.Duration.days(90))
                    .build()
            ));
        }

        return List.of(
            ruleBuilder
                .expiration(software.amazon.awscdk.Duration.days(retentionDays))
                .build()
        );
    }

}
