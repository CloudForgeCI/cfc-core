package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforgeci.api.core.rules.AuditManagerControl;
import com.cloudforgeci.api.core.rules.AuditManagerControlRegistry;
import com.cloudforge.core.enums.SecurityProfile;
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
import software.amazon.awscdk.services.ssm.CfnDocument;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.PhysicalResourceIdReference;
import software.constructs.Construct;
import io.github.cdklabs.cdknag.NagSuppressions;
import io.github.cdklabs.cdknag.NagPackSuppression;

import java.util.ArrayList;
import java.util.HashMap;
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

    @DeploymentContext("enableRdsDeletionProtectionRemediation")
    private Boolean enableRdsDeletionProtectionRemediation;

    @DeploymentContext("enableRdsAutoMinorVersionUpgradeRemediation")
    private Boolean enableRdsAutoMinorVersionUpgradeRemediation;

    @DeploymentContext("enableSecurityHubRemediation")
    private Boolean enableSecurityHubRemediation;

    @DeploymentContext("enableInspectorRemediation")
    private Boolean enableInspectorRemediation;

    @DeploymentContext("enableMacieRemediation")
    private Boolean enableMacieRemediation;

    @DeploymentContext("enableGuardDutyRemediation")
    private Boolean enableGuardDutyRemediation;

    @DeploymentContext("enableEcrImageScanningRemediation")
    private Boolean enableEcrImageScanningRemediation;

    @DeploymentContext("scopeConfigRulesToDeployment")
    private Boolean scopeConfigRulesToDeployment;

    @DeploymentContext("provisionDatabase")
    private Boolean provisionDatabase;

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
    private CfnCondition databaseProvisionedCondition;

    // Combined conditions for RDS rules (framework AND database)
    private CfnCondition pciDssRdsCondition;
    private CfnCondition soc2RdsCondition;
    private CfnCondition hipaaRdsCondition;
    private CfnCondition gdprRdsCondition;

    // CloudTrail and bucket for S3 data event configuration
    private Trail trail;
    private Bucket trailBucket;
    private String trailName;

    @Override
    public void create() {
        LOG.info("Creating compliance resources for security profile: " + security);

        // Create CloudFormation conditions for compliance frameworks
        createFrameworkConditions();

        // Create CloudTrail if enabled for this security profile (STAGING/PRODUCTION by default)
        if (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) {
            createCloudTrail();
        } else {
            LOG.info("CloudTrail disabled for security profile: " + security);
        }

        // Check if AWS Audit Manager should be enabled (do this BEFORE Config infrastructure)
        // This ensures Audit Manager configuration errors fail before creating account-level resources
        boolean auditManagerEnabledFlag = Boolean.TRUE.equals(auditManagerEnabled)
            || (auditManagerEnabled == null && (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING));

        if (auditManagerEnabledFlag) {
            LOG.info("Creating AWS Audit Manager assessments and framework controls for profile: " + security);
            createAuditManagerAssessments();
        } else {
            LOG.info("AWS Audit Manager disabled for profile: " + security);
        }

        // STEP 1: Check if Config INFRASTRUCTURE should be created (Recorder + Delivery Channel)
        // These are account-level singleton resources - only ONE per region per account allowed
        // createConfigInfrastructure controls ONLY infrastructure, NOT rules
        boolean shouldCreateInfra = Boolean.TRUE.equals(createConfigInfrastructure);
        boolean configInfraExists = false;

        // Only check for existing infrastructure if we're planning to create it
        if (shouldCreateInfra) {
            configInfraExists = checkConfigInfrastructureExists();

            if (configInfraExists) {
                LOG.warning("Config infrastructure already exists but createConfigInfrastructure = true");
                LOG.warning("  Existing Config Recorder detected in region: " + region);
                LOG.warning("  Setting createConfigInfrastructure = false automatically to avoid conflict");
                LOG.warning("  To override this behavior, manually set createConfigInfrastructure in deployment-context.json");
                shouldCreateInfra = false;
            }
        }

        ConfigInfrastructure configInfra = null;
        if (shouldCreateInfra) {
            LOG.info("Creating Config Recorder and Delivery Channel (account-level singletons)");
            LOG.info("  IMPORTANT: Only ONE stack per region should have createConfigInfrastructure = true");
            configInfra = createConfigInfrastructure();
        } else {
            if (configInfraExists) {
                LOG.info("Config infrastructure already exists in region (auto-detected)");
            } else {
                LOG.info("Skipping Config infrastructure creation (createConfigInfrastructure = false)");
            }
            LOG.info("  Config Recorder 'cloudforge-config-recorder' will be referenced by name if needed");
        }

        // STEP 2: Deploy Conformance Packs (AWS managed rule bundles for compliance frameworks)
        // Conformance Packs are the foundation - they deploy standardized Config rules
        boolean configRulesEnabled = Boolean.TRUE.equals(awsConfigEnabled)
            || (awsConfigEnabled == null && (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING));

        if (configRulesEnabled) {
            LOG.info("Deploying Conformance Packs for compliance frameworks");
            deployConformancePacks(configInfra);
        }

        // STEP 3: Deploy custom/additional Config rules not covered by Conformance Packs
        // awsConfigEnabled controls ONLY rules and remediation, NOT infrastructure
        // This is now completely decoupled from infrastructure creation
        if (configRulesEnabled) {
            LOG.info("Creating additional AWS Config rules not covered by Conformance Packs");

            // Config Rules reference the recorder by name at runtime, regardless of whether
            // we created the infrastructure in this stack or it already existed
            // This eliminates the need for two separate code paths
            if (configInfra != null) {
                // We just created the infrastructure - add CloudFormation dependencies for ordering
                LOG.info("  Config infrastructure created in this stack - adding CloudFormation dependencies");
                createConfigRules(configInfra.recorder, configInfra.starterResource);
                createAllFrameworkConfigRules(configInfra.recorder, configInfra.starterResource);
            } else {
                // Infrastructure already exists or wasn't created - rules still work, just no CF dependency
                LOG.info("  Config Recorder referenced by name 'cloudforge-config-recorder'");
                LOG.info("  Rules will be created without CloudFormation dependencies");
                createConfigRulesWithoutRecorder();
                createAllFrameworkConfigRulesWithoutRecorder();
            }
        } else {
            LOG.info("AWS Config rules and remediation disabled for profile: " + security);
            LOG.info("  awsConfigEnabled = " + awsConfigEnabled + " (controls rules and remediation)");
            LOG.info("  createConfigInfrastructure = " + createConfigInfrastructure + " (controls recorder and channel)");
        }

        LOG.info("Compliance resources created successfully for profile: " + security);

        // STEP 4: Deploy Config rules collected from factories
        // This deploys only the rules that match actually-provisioned infrastructure
        deployCollectedConfigRules();
    }

    /**
     * Deploy AWS Config rules collected from factories.
     *
     * <p>This method deploys Config rules that were registered by infrastructure factories
     * (e.g., GuardDutyFactory, VpcFactory, RdsFactory) where the infrastructure was actually
     * created. Rules are filtered based on compliance frameworks and mode.</p>
     *
     * <p>This approach ensures Config rules are only deployed for infrastructure that exists,
     * and automatically deduplicates rules when multiple frameworks require the same control.</p>
     */
    private void deployCollectedConfigRules() {
        var collectedRules = ctx.getRequiredConfigRules();
        if (collectedRules.isEmpty()) {
            LOG.info("No Config rules collected from factories");
            return;
        }

        LOG.info("Deploying " + collectedRules.size() + " Config rules collected from factories");

        String frameworks = complianceFrameworks != null ? complianceFrameworks : "";
        ComplianceMode mode = ctx.cfc.complianceMode();

        int deployedCount = 0;
        for (AwsConfigRule rule : collectedRules) {
            if (rule.isRequired(frameworks, mode)) {
                // Deploy the Config rule
                CfnConfigRule.Builder.create(this, "Collected" + rule.name())
                    .configRuleName(rule.getRuleName())
                    .description(rule.getDescription() + " (collected from factory)")
                    .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(rule.getRuleName().toUpperCase().replace("-", "_"))
                        .build())
                    .build();
                deployedCount++;
                LOG.fine("  Deployed: " + rule.getRuleName() + " (" + rule.getSecurityControl() + ")");
            } else {
                LOG.fine("  Skipped: " + rule.getRuleName() + " (not required for " + frameworks + "/" + mode + ")");
            }
        }

        LOG.info("Deployed " + deployedCount + " of " + collectedRules.size() + " collected Config rules");

        // Also deploy account-level authentication/access control rules
        deployAuthenticationConfigRules(frameworks, mode);
    }

    /**
     * Deploy account-level authentication and access control Config rules.
     * These rules check IAM settings across the entire AWS account and are
     * required by most compliance frameworks (PCI-DSS, HIPAA, SOC2, etc.).
     */
    private void deployAuthenticationConfigRules(String frameworks, ComplianceMode mode) {
        // Authentication rules - required by most compliance frameworks
        var authRules = java.util.List.of(
            AwsConfigRule.IAM_USER_MFA_ENABLED,
            AwsConfigRule.IAM_PASSWORD_POLICY,
            AwsConfigRule.IAM_USER_GROUP_MEMBERSHIP,
            AwsConfigRule.IAM_NO_ADMIN_ACCESS
        );

        int deployedCount = 0;
        for (AwsConfigRule rule : authRules) {
            if (rule.isRequired(frameworks, mode)) {
                CfnConfigRule.Builder.create(this, "Auth" + rule.name())
                    .configRuleName(rule.getRuleName())
                    .description(rule.getDescription())
                    .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(rule.getRuleName().toUpperCase().replace("-", "_"))
                        .build())
                    .build();
                deployedCount++;
            }
        }

        if (deployedCount > 0) {
            LOG.info("Deployed " + deployedCount + " authentication/access control Config rules");
        }
    }

    /**
     * Creates CloudFormation conditions for each compliance framework.
     * These conditions determine which framework-specific Config rules are deployed.
     *
     * Supports multiple frameworks via comma-separated values (e.g., "PCI-DSS,SOC2,HIPAA").
     * When a framework is removed, its condition becomes false and CloudFormation deletes those rules.
     */
    private void createFrameworkConditions() {
        List<String> frameworks = determineFrameworks();

        LOG.info("Creating CloudFormation conditions for frameworks: " + String.join(", ", frameworks));

        // Normalize framework names for comparison
        List<String> normalizedFrameworks = frameworks.stream()
                .map(f -> f.trim().toUpperCase().replace("-", "").replace("_", ""))
                .collect(java.util.stream.Collectors.toList());

        // Create condition for PCI-DSS
        boolean enablePciDss = normalizedFrameworks.contains("PCIDSS");
        pciDssCondition = CfnCondition.Builder.create(this, "EnablePciDssRules")
                .expression(Fn.conditionEquals(enablePciDss ? "true" : "false", "true"))
                .build();

        // Create condition for SOC 2
        boolean enableSoc2 = normalizedFrameworks.contains("SOC2");
        soc2Condition = CfnCondition.Builder.create(this, "EnableSoc2Rules")
                .expression(Fn.conditionEquals(enableSoc2 ? "true" : "false", "true"))
                .build();

        // Create condition for HIPAA
        boolean enableHipaa = normalizedFrameworks.contains("HIPAA");
        hipaaCondition = CfnCondition.Builder.create(this, "EnableHipaaRules")
                .expression(Fn.conditionEquals(enableHipaa ? "true" : "false", "true"))
                .build();

        // Create condition for GDPR
        boolean enableGdpr = normalizedFrameworks.contains("GDPR");
        gdprCondition = CfnCondition.Builder.create(this, "EnableGdprRules")
                .expression(Fn.conditionEquals(enableGdpr ? "true" : "false", "true"))
                .build();

        // Create condition for database provisioning
        // RDS Config rules should only deploy when a database is actually provisioned
        boolean dbProvisioned = (provisionDatabase != null && provisionDatabase);
        databaseProvisionedCondition = CfnCondition.Builder.create(this, "DatabaseProvisioned")
                .expression(Fn.conditionEquals(dbProvisioned ? "true" : "false", "true"))
                .build();

        // Create combined conditions for RDS rules (framework AND database)
        // These ensure RDS Config rules only deploy when both the framework is enabled AND a database is provisioned
        pciDssRdsCondition = CfnCondition.Builder.create(this, "EnablePciDssRdsRules")
                .expression(Fn.conditionAnd(pciDssCondition, databaseProvisionedCondition))
                .build();

        soc2RdsCondition = CfnCondition.Builder.create(this, "EnableSoc2RdsRules")
                .expression(Fn.conditionAnd(soc2Condition, databaseProvisionedCondition))
                .build();

        hipaaRdsCondition = CfnCondition.Builder.create(this, "EnableHipaaRdsRules")
                .expression(Fn.conditionAnd(hipaaCondition, databaseProvisionedCondition))
                .build();

        gdprRdsCondition = CfnCondition.Builder.create(this, "EnableGdprRdsRules")
                .expression(Fn.conditionAnd(gdprCondition, databaseProvisionedCondition))
                .build();

        LOG.info("CloudFormation conditions created: PCI-DSS = " + enablePciDss + ", SOC2 = " + enableSoc2 +
                 ", HIPAA = " + enableHipaa + ", GDPR = " + enableGdpr + ", Database = " + dbProvisioned);
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

        // Use stack-scoped trail name to avoid conflicts between multiple stacks in same region
        // Each stack gets its own CloudTrail trail (but can share buckets via SSM)
        this.trailName = "cloudforge-cloudtrail-" + this.stackName;

        LOG.info("CloudTrail will use stack-scoped name: " + this.trailName);
        LOG.info("  This avoids conflicts when deploying multiple stacks in the same region");

        // Check SSM Parameter Store at DEPLOYMENT TIME for existing bucket (stack-scoped)
        String ssmParamName = "/cloudforge/shared/" + this.region + "/stack/" + this.stackName + "/cloudtrail/bucket-arn";
        Bucket trailBucket = getOrCreateBucketWithSSM("CloudTrailBucket", ssmParamName);

        LOG.info("CloudTrail bucket ARN will be tracked in SSM Parameter Store for reuse");

        // Enable KMS encryption for CloudTrail when:
        // 1. HIPAA or PCI-DSS compliance in PRODUCTION (mandatory)
        // 2. cloudWatchLogsKmsEncryptionEnabled is explicitly set to true (optional hardening)
        boolean isHipaa = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("HIPAA");
        boolean isPciDss = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean useKms = (security == SecurityProfile.PRODUCTION && (isHipaa || isPciDss))
                || config.isCloudWatchLogsKmsEncryptionEnabled();

        // Create KMS-encrypted CloudWatch Log Group for CloudTrail (PCI-DSS Req 3.4)
        Key cloudTrailLogsKmsKey = null;
        LogGroup cloudTrailLogGroup = null;
        if (useKms) {
            cloudTrailLogsKmsKey = Key.Builder.create(this, "CloudTrailLogsKmsKey")
                    .description("KMS key for CloudTrail CloudWatch Logs (HIPAA/PCI-DSS compliance)")
                    .enableKeyRotation(true)
                    .removalPolicy(security == SecurityProfile.PRODUCTION ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                    .build();

            cloudTrailLogGroup = LogGroup.Builder.create(this, "CloudTrailLogGroup")
                    .logGroupName("/aws/cloudtrail/" + this.trailName)
                    .retention(config.getLogRetentionDays())
                    .encryptionKey(cloudTrailLogsKmsKey)
                    .removalPolicy(security == SecurityProfile.PRODUCTION ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                    .build();
        }

        // Create CloudTrail using high-level Trail construct
        // If trail with this name already exists, CloudFormation will update it or fail gracefully
        Trail.Builder trailBuilder = Trail.Builder.create(this, "CloudTrail")
                .trailName(this.trailName)  // Fixed name for reusability
                .bucket(trailBucket)
                .enableFileValidation(true)
                .includeGlobalServiceEvents(true)
                .isMultiRegionTrail(true);

        // Configure CloudWatch Logs - use pre-created encrypted log group if KMS enabled
        if (cloudTrailLogGroup != null) {
            trailBuilder.cloudWatchLogGroup(cloudTrailLogGroup);
            trailBuilder.sendToCloudWatchLogs(true);
        } else {
            trailBuilder.sendToCloudWatchLogs(true);
            trailBuilder.cloudWatchLogsRetention(config.getLogRetentionDays());
        }

        // Apply KMS encryption for HIPAA/PCI-DSS compliance in PRODUCTION
        if (useKms) {
            Key cloudTrailKmsKey = Key.Builder.create(this, "CloudTrailKmsKey")
                    .description("KMS key for CloudTrail (HIPAA/PCI-DSS compliance)")
                    .enableKeyRotation(true)
                    .removalPolicy(security == SecurityProfile.PRODUCTION ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                    .build();

            // Grant CloudTrail service permission to use the key
            cloudTrailKmsKey.addToResourcePolicy(PolicyStatement.Builder.create()
                    .sid("Enable CloudTrail log encryption")
                    .effect(Effect.ALLOW)
                    .principals(List.of(new ServicePrincipal("cloudtrail.amazonaws.com")))
                    .actions(List.of("kms:GenerateDataKey*", "kms:DecryptDataKey"))
                    .resources(List.of("*"))
                    .build());

            trailBuilder.encryptionKey(cloudTrailKmsKey);
        }

        Trail trail = trailBuilder.build();

        // Store trail for later configuration
        this.trail = trail;
        this.trailBucket = trailBucket;

        // CloudTrail trail can be safely deleted - all audit logs are stored in the S3 bucket
        // The S3 bucket has its own RETAIN policy to preserve the actual log data
        trail.applyRemovalPolicy(RemovalPolicy.DESTROY);

        LOG.info("CloudTrail created: " + trail.getTrailArn());
        LOG.info("CloudTrail removal policy: DESTROY (logs retained in S3 bucket)");
        LOG.info("CloudTrail name: " + trailName + " (stack-scoped to avoid conflicts)");

        // Register AWS Config rules for CloudTrail compliance
        ctx.requireConfigRule(AwsConfigRule.CLOUDTRAIL_ENABLED);
        ctx.requireConfigRule(AwsConfigRule.CLOUDTRAIL_LOG_FILE_VALIDATION);
        ctx.requireConfigRule(AwsConfigRule.MULTI_REGION_CLOUDTRAIL);

        // Register KMS encryption Config rules when enabled
        if (useKms) {
            ctx.requireConfigRule(AwsConfigRule.CLOUDTRAIL_ENCRYPTION_ENABLED);
            ctx.requireConfigRule(AwsConfigRule.CLOUDWATCH_LOG_GROUP_ENCRYPTED);
        }

        // Configure CloudTrail Insights when required by compliance frameworks (SOC2, NIST)
        if (config.isCloudTrailInsightsEnabled()) {
            configureCloudTrailInsights(trail);
        }

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
     * Configure CloudTrail Insights for anomaly detection in API activity.
     * This enables both API call rate and API error rate insights.
     *
     * <p>CloudTrail Insights identifies unusual operational activity, such as:
     * <ul>
     *   <li>Spikes in API call volume</li>
     *   <li>Increases in error rates</li>
     *   <li>Unusual patterns that may indicate security incidents</li>
     * </ul>
     *
     * <p>Required for SOC2 CC7.2 (anomaly detection) and NIST SI-4 (system monitoring).
     *
     * @param trail The CloudTrail trail to configure Insights on
     */
    private void configureCloudTrailInsights(Trail trail) {
        LOG.info("Enabling CloudTrail Insights for anomaly detection (SOC2/NIST compliance)");

        // Get the underlying CfnTrail resource to configure Insights
        CfnTrail cfnTrail = (CfnTrail) trail.getNode().getDefaultChild();

        // Enable both API call rate and API error rate Insights
        cfnTrail.addPropertyOverride("InsightSelectors", List.of(
            Map.of("InsightType", "ApiCallRateInsight"),
            Map.of("InsightType", "ApiErrorRateInsight")
        ));

        LOG.info("CloudTrail Insights enabled: ApiCallRateInsight, ApiErrorRateInsight");
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
        rule.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rule.getNode().addDependency(recorder);
        if (starterResource != null) {
            rule.getNode().addDependency(starterResource);
        }
    }

    /**
     * Deploys AWS Conformance Packs for compliance frameworks.
     * Conformance Packs are AWS-managed bundles of Config rules for SOC2, PCI-DSS, HIPAA, and GDPR.
     *
     * <p>Benefits:</p>
     * <ul>
     *   <li>AWS maintains the rule definitions and updates them when standards change</li>
     *   <li>Industry-standard rule sets with best practice configurations</li>
     *   <li>Standardized compliance reporting across accounts</li>
     * </ul>
     *
     * @param configInfra Config infrastructure (null if not created in this stack)
     */
    private void deployConformancePacks(ConfigInfrastructure configInfra) {
        List<String> frameworks = determineFrameworks();

        LOG.info("Deploying Conformance Packs for frameworks: " + String.join(", ", frameworks));

        // Map framework names to AWS Conformance Pack SSM document names
        // These are the actual document names in AWS Systems Manager
        // NOTE: Use "ExcludingGlobalResourceTypes" for non-us-east-1 regions
        // The "IncludingGlobalResourceTypes" version has CloudFront rules that only work in us-east-1
        String pciDssTemplate = "us-east-1".equals(this.region)
            ? "AWSConformancePacks-OperationalBestPracticesforPCIDSSv4IncludingGlobalResourceTypes"
            : "AWSConformancePacks-OperationalBestPracticesforPCIDSSv4ExcludingGlobalResourceTypes";

        Map<String, String> frameworkToTemplate = Map.of(
            "PCIDSS", pciDssTemplate,
            "HIPAA", "AWSConformancePacks-OperationalBestPracticesforHIPAASecurity"
            // Note: SOC2 and GDPR don't have dedicated AWS-managed conformance packs
            // SOC2 controls are covered by individual Config rules deployed separately
            // GDPR requirements can use NIST Privacy Framework as closest match
        );

        for (String framework : frameworks) {
            String normalizedFramework = framework.trim().toUpperCase().replace("-", "").replace("_", "");
            String templateName = frameworkToTemplate.get(normalizedFramework);

            if (templateName == null) {
                LOG.warning("No AWS Conformance Pack available for framework: " + framework);
                continue;
            }

            // Use stack name instead of security profile to avoid conflicts when switching profiles
            String conformancePackName = "CloudForge-" + normalizedFramework + "-" + this.stackName;

            LOG.info("  Deploying Conformance Pack: " + conformancePackName);
            LOG.info("    SSM Document: " + templateName);

            // Create Conformance Pack using SSM document reference
            // Note: We use addPropertyOverride for TemplateSSMDocumentDetails because CDK 2.196.0
            // has a bug where the Java bindings generate incorrect property casing (documentName
            // instead of DocumentName). CloudFormation requires PascalCase for this property.
            CfnConformancePack conformancePack = CfnConformancePack.Builder.create(this, "ConformancePack" + normalizedFramework)
                    .conformancePackName(conformancePackName)
                    .deliveryS3Bucket("") // Empty string uses default Config bucket
                    .build();

            // Workaround for CDK 2.196.0 property casing bug - use L1 override to set correct casing
            conformancePack.addPropertyOverride("TemplateSSMDocumentDetails.DocumentName", templateName);

            // Delete conformance packs on stack deletion - they can be recreated when needed
            conformancePack.applyRemovalPolicy(RemovalPolicy.DESTROY);

            // Add condition for framework-specific deployment
            CfnCondition condition = getConditionForFramework(normalizedFramework);
            if (condition != null) {
                conformancePack.getCfnOptions().setCondition(condition);
            }

            // Add dependency on Config infrastructure if it was created in this stack
            if (configInfra != null) {
                conformancePack.getNode().addDependency(configInfra.recorder);
                conformancePack.getNode().addDependency(configInfra.starterResource);
            }

            LOG.info("    Conformance Pack created: " + conformancePackName);
        }

        LOG.info("Conformance Packs deployment complete");
    }

    /**
     * Get the CloudFormation condition for a specific framework.
     */
    private CfnCondition getConditionForFramework(String normalizedFramework) {
        return switch (normalizedFramework) {
            case "SOC2" -> soc2Condition;
            case "PCIDSS" -> pciDssCondition;
            case "HIPAA" -> hipaaCondition;
            case "GDPR" -> gdprCondition;
            default -> null;
        };
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

        // Create IAM role for AWS Config with explicit trust policy
        Role configRole = Role.Builder.create(this, "ConfigRole")
                .assumedBy(ServicePrincipal.Builder.create("config.amazonaws.com").build())
                .description("AWS Config service role for compliance monitoring and rule evaluation")
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

        // RETAIN the Config role - it's referenced by the retained recorder/delivery channel
        configRole.applyRemovalPolicy(RemovalPolicy.RETAIN);

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
        String recorderArn = software.amazon.awscdk.Stack.of(this).formatArn(software.amazon.awscdk.ArnComponents.builder()
                .service("config")
                .resource("config-recorder")
                .resourceName(recorderName)
                .build());
        String channelArn = software.amazon.awscdk.Stack.of(this).formatArn(software.amazon.awscdk.ArnComponents.builder()
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

        // Add automatic remediation if enabled (PRODUCTION only by default)
        boolean shouldEnableS3VersioningRemediation = Boolean.TRUE.equals(enableS3VersioningRemediation)
            || (enableS3VersioningRemediation == null && ctx.security == SecurityProfile.PRODUCTION);

        if (shouldEnableS3VersioningRemediation) {
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
     * <h3>Required Permissions (Least Privilege)</h3>
     * <p>The SSM automation role has scoped permissions following AWS best practices:</p>
     * <ul>
     *   <li>s3:GetBucketPolicy - Scoped to CloudForge CloudTrail buckets (arn:aws:s3:::cloudforge-cloudtrail-*)</li>
     *   <li>s3:PutBucketPolicy - Scoped to CloudForge CloudTrail buckets (arn:aws:s3:::cloudforge-cloudtrail-*)</li>
     *   <li>s3:GetBucketAcl - Scoped to CloudForge CloudTrail buckets (arn:aws:s3:::cloudforge-cloudtrail-*)</li>
     *   <li>cloudtrail:GetTrail - Scoped to CloudForge trails (arn:aws:cloudtrail:*:*:trail/cloudforge-cloudtrail-*)</li>
     *   <li>cloudtrail:DescribeTrails - Scoped to CloudForge trails (arn:aws:cloudtrail:*:*:trail/cloudforge-cloudtrail-*)</li>
     * </ul>
     * <p><b>NOTE:</b> No wildcard (*) permissions are used. All permissions are scoped to CloudForge-managed resources.</p>
     *
     * @param cloudTrailRule The AWS Config rule for CloudTrail enabled check
     * @throws IllegalStateException if CloudTrail or S3 bucket is not configured
     */
    private void addCloudTrailBucketAccessRemediation(CfnConfigRule cloudTrailRule) {
        LOG.info("Setting up CloudTrail bucket access auto-remediation");

        // Null guard: Verify CloudTrail and S3 bucket are configured before creating remediation
        if (this.trail == null) {
            String errorMsg = "Cannot configure CloudTrail bucket access remediation: CloudTrail is not configured. " +
                "Ensure CloudTrail is enabled in the security profile configuration.";
            LOG.severe(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (this.trailBucket == null) {
            String errorMsg = "Cannot configure CloudTrail bucket access remediation: CloudTrail S3 bucket is not configured. " +
                "Ensure CloudTrail bucket creation succeeded before enabling remediation.";
            LOG.severe(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (this.trailName == null || this.trailName.isEmpty()) {
            String errorMsg = "Cannot configure CloudTrail bucket access remediation: CloudTrail name is not set. " +
                "This is an internal error - CloudTrail should have been assigned a name during creation.";
            LOG.severe(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        LOG.info("  CloudTrail name: " + this.trailName);
        LOG.info("  CloudTrail bucket: " + this.trailBucket.getBucketName());
        LOG.info("  Remediation will fix bucket policy if CloudTrail cannot write logs");

        // Create IAM role for SSM Automation with CloudTrail and S3 permissions
        // IMPORTANT: Permissions are scoped to EXACT resources (no wildcards) following least privilege

        // Use exact ARNs for the specific CloudTrail and bucket in this deployment
        String exactTrailArn = this.trail.getTrailArn();
        String exactBucketArn = this.trailBucket.getBucketArn();

        Role ssmAutomationRole = Role.Builder.create(this, "CloudTrailBucketAccessRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .description("Role for automated CloudTrail S3 bucket access remediation - scoped to exact resources")
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
                                // Scoped to exact CloudTrail bucket ARN (no wildcards)
                                .resources(List.of(exactBucketArn))
                                .build(),
                            PolicyStatement.Builder.create()
                                .sid("CloudTrailReadAccess")
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "cloudtrail:GetTrail",
                                    "cloudtrail:DescribeTrails",
                                    "cloudtrail:GetEventSelectors"
                                ))
                                // Scoped to exact CloudTrail ARN (no wildcards)
                                .resources(List.of(exactTrailArn))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // RETAIN the remediation role when CloudTrail is retained (production)
        // CloudTrail uses this role for auto-remediation of bucket access issues
        ssmAutomationRole.applyRemovalPolicy(
            security == SecurityProfile.PRODUCTION ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY
        );

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
     * Creates automatic remediation for RDS deletion protection compliance.
     * Enables deletion protection on non-compliant RDS instances.
     *
     * <p><b>Safety:</b> This is a SAFE remediation - it only enables protection, never disables it.</p>
     * <p><b>Impact:</b> Protected databases cannot be accidentally deleted (requires manual disable first).</p>
     *
     * @param rdsDeletionProtectionRule The Config rule to remediate
     */
    private void createRdsDeletionProtectionRemediation(CfnConfigRule rdsDeletionProtectionRule) {

        if (!Boolean.TRUE.equals(enableRdsDeletionProtectionRemediation)) {
            LOG.info("RDS deletion protection remediation disabled (enableRdsDeletionProtectionRemediation = false)");
            return;
        }

        LOG.info("Creating RDS deletion protection automatic remediation");

        // Create IAM role for SSM Automation with RDS permissions
        Role ssmAutomationRole = Role.Builder.create(this, "RdsDeletionProtectionRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .description("Role for automated RDS deletion protection remediation")
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .inlinePolicies(Map.of(
                    "RdsDeletionProtectionPolicy", software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "rds:ModifyDBInstance",
                                    "rds:DescribeDBInstances"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create custom SSM document for RDS deletion protection
        CfnDocument rdsDeletionProtectionDocument = CfnDocument.Builder.create(
                this, "RdsDeletionProtectionDocument")
                .name(this.stackName + "-enable-rds-deletion-protection")
                .documentType("Automation")
                .documentFormat("JSON")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Enable deletion protection on RDS instances",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for automation"
                        ),
                        "ResourceId", Map.of(
                            "type", "String",
                            "description", "RDS instance identifier"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "EnableDeletionProtection",
                            "action", "aws:executeAwsApi",
                            "inputs", Map.of(
                                "Service", "rds",
                                "Api", "ModifyDBInstance",
                                "DBInstanceIdentifier", "{{ ResourceId }}",
                                "DeletionProtection", true,
                                "ApplyImmediately", true
                            ),
                            "description", "Enable deletion protection on RDS instance"
                        )
                    )
                ))
                .build();

        // Create remediation configuration
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "RdsDeletionProtectionRemediation")
                .configRuleName(rdsDeletionProtectionRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(rdsDeletionProtectionDocument.getRef())
                .targetVersion("1")
                .automatic(true)  // Enable automatic remediation
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(120)
                .resourceType("AWS::RDS::DBInstance")
                .build();

        // Configure remediation parameters
        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of(
                "StaticValue", Map.of("Values", List.of(ssmAutomationRole.getRoleArn()))
            ),
            "ResourceId", Map.of(
                "ResourceValue", Map.of("Value", "RESOURCE_ID")
            )
        ));

        remediation.getNode().addDependency(rdsDeletionProtectionRule);
        remediation.getNode().addDependency(rdsDeletionProtectionDocument);

        LOG.info("RDS deletion protection automatic remediation configured");
        LOG.info("  SSM Document: " + rdsDeletionProtectionDocument.getRef());
        LOG.info("  Mode: Automatic (enables deletion protection on non-compliant instances)");
        LOG.info("  Max attempts: 3, Retry interval: 120 seconds");
        LOG.info("  Impact: SAFE - only enables protection, does not require downtime");
    }

    /**
     * Creates automatic remediation for RDS automatic minor version upgrade compliance.
     * Enables automatic minor version upgrades on non-compliant RDS instances.
     *
     * <p><b>Safety:</b> This is generally SAFE - only minor version upgrades (bug fixes, security patches).</p>
     * <p><b>Impact:</b> Database will auto-upgrade during maintenance window (minimal downtime).</p>
     *
     * @param rdsAutoUpgradeRule The Config rule to remediate
     */
    private void createRdsAutoMinorVersionUpgradeRemediation(CfnConfigRule rdsAutoUpgradeRule) {

        if (!Boolean.TRUE.equals(enableRdsAutoMinorVersionUpgradeRemediation)) {
            LOG.info("RDS auto minor version upgrade remediation disabled (enableRdsAutoMinorVersionUpgradeRemediation = false)");
            return;
        }

        LOG.info("Creating RDS automatic minor version upgrade remediation");

        // Create IAM role for SSM Automation with RDS permissions
        Role ssmAutomationRole = Role.Builder.create(this, "RdsAutoMinorVersionUpgradeRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .description("Role for automated RDS auto minor version upgrade remediation")
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .inlinePolicies(Map.of(
                    "RdsAutoUpgradePolicy", software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "rds:ModifyDBInstance",
                                    "rds:DescribeDBInstances"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create custom SSM document for RDS auto minor version upgrade
        CfnDocument rdsAutoUpgradeDocument = CfnDocument.Builder.create(
                this, "RdsAutoMinorVersionUpgradeDocument")
                .name(this.stackName + "-enable-rds-auto-minor-version-upgrade")
                .documentType("Automation")
                .documentFormat("JSON")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Enable automatic minor version upgrades on RDS instances",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for automation"
                        ),
                        "ResourceId", Map.of(
                            "type", "String",
                            "description", "RDS instance identifier"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "EnableAutoMinorVersionUpgrade",
                            "action", "aws:executeAwsApi",
                            "inputs", Map.of(
                                "Service", "rds",
                                "Api", "ModifyDBInstance",
                                "DBInstanceIdentifier", "{{ ResourceId }}",
                                "AutoMinorVersionUpgrade", true,
                                "ApplyImmediately", false
                            ),
                            "description", "Enable automatic minor version upgrades (applied during next maintenance window)"
                        )
                    )
                ))
                .build();

        // Create remediation configuration
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "RdsAutoMinorVersionUpgradeRemediation")
                .configRuleName(rdsAutoUpgradeRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(rdsAutoUpgradeDocument.getRef())
                .targetVersion("1")
                .automatic(true)  // Enable automatic remediation
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(120)
                .resourceType("AWS::RDS::DBInstance")
                .build();

        // Configure remediation parameters
        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of(
                "StaticValue", Map.of("Values", List.of(ssmAutomationRole.getRoleArn()))
            ),
            "ResourceId", Map.of(
                "ResourceValue", Map.of("Value", "RESOURCE_ID")
            )
        ));

        remediation.getNode().addDependency(rdsAutoUpgradeRule);
        remediation.getNode().addDependency(rdsAutoUpgradeDocument);

        LOG.info("RDS automatic minor version upgrade remediation configured");
        LOG.info("  SSM Document: " + rdsAutoUpgradeDocument.getRef());
        LOG.info("  Mode: Automatic (enables auto-upgrade during maintenance window)");
        LOG.info("  Max attempts: 3, Retry interval: 120 seconds");
        LOG.info("  Impact: SAFE - only minor versions, applied during maintenance window");
    }

    /**
     * Creates additional Config rules for production environments.
     * All rules explicitly depend on the Configuration Recorder.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createProductionConfigRules(CfnConfigurationRecorder recorder, AwsCustomResource starterResource) {
        // Build CloudTrail Config rule with parameters if CloudTrail was created
        CfnConfigRule.Builder cloudTrailRuleBuilder = CfnConfigRule.Builder.create(this, "CloudTrailEnabledRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.CLOUD_TRAIL_ENABLED)
                        .build());

        // If CloudTrail was created, add input parameters for the Config rule
        if (this.trail != null && this.trailBucket != null) {
            try {
                Map<String, Object> inputParams = new HashMap<>();
                inputParams.put("s3BucketName", this.trailBucket.getBucketName());

                // Add CloudWatch Logs log group ARN if available
                if (this.trail.getLogGroup() != null) {
                    inputParams.put("cloudWatchLogsLogGroupArn", this.trail.getLogGroup().getLogGroupArn());
                }

                // Add SNS topic ARN if available
                CfnTrail cfnTrail = (CfnTrail) this.trail.getNode().getDefaultChild();
                if (cfnTrail.getSnsTopicName() != null) {
                    inputParams.put("snsTopicArn", cfnTrail.getSnsTopicName());
                }

                // Convert to JSON string for CloudFormation
                String inputParamsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(inputParams);
                cloudTrailRuleBuilder.inputParameters(inputParamsJson);

                LOG.info("CloudTrail Config rule configured with parameters: " + inputParamsJson);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                LOG.warning("Failed to serialize CloudTrail Config rule parameters: " + e.getMessage());
            }
        }

        CfnConfigRule cloudTrailRule = cloudTrailRuleBuilder.build();
        addConfigRuleDependencies(cloudTrailRule, recorder, starterResource);

        // Add auto-remediation for CloudTrail bucket access errors if enabled (PRODUCTION only by default)
        boolean shouldEnableCloudTrailBucketAccessRemediation = Boolean.TRUE.equals(enableCloudTrailBucketAccessRemediation)
            || (enableCloudTrailBucketAccessRemediation == null && ctx.security == SecurityProfile.PRODUCTION);

        if (shouldEnableCloudTrailBucketAccessRemediation) {
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
     * Used when createConfigInfrastructure = false and recorder already exists outside this stack.
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
        // Build CloudTrail Config rule with parameters if CloudTrail was created
        CfnConfigRule.Builder cloudTrailRuleBuilder = CfnConfigRule.Builder.create(this, "CloudTrailEnabledRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.CLOUD_TRAIL_ENABLED)
                        .build());

        // If CloudTrail was created, add input parameters for the Config rule
        if (this.trail != null && this.trailBucket != null) {
            try {
                Map<String, Object> inputParams = new HashMap<>();
                inputParams.put("s3BucketName", this.trailBucket.getBucketName());

                // Add CloudWatch Logs log group ARN if available
                if (this.trail.getLogGroup() != null) {
                    inputParams.put("cloudWatchLogsLogGroupArn", this.trail.getLogGroup().getLogGroupArn());
                }

                // Add SNS topic ARN if available
                CfnTrail cfnTrail = (CfnTrail) this.trail.getNode().getDefaultChild();
                if (cfnTrail.getSnsTopicName() != null) {
                    inputParams.put("snsTopicArn", cfnTrail.getSnsTopicName());
                }

                // Convert to JSON string for CloudFormation
                String inputParamsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(inputParams);
                cloudTrailRuleBuilder.inputParameters(inputParamsJson);

                LOG.info("CloudTrail Config rule (without recorder) configured with parameters: " + inputParamsJson);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                LOG.warning("Failed to serialize CloudTrail Config rule parameters: " + e.getMessage());
            }
        }

        CfnConfigRule cloudTrailRule = cloudTrailRuleBuilder.build();

        // Add auto-remediation for CloudTrail bucket access errors if enabled (PRODUCTION only by default)
        boolean shouldEnableCloudTrailBucketAccessRemediation = Boolean.TRUE.equals(enableCloudTrailBucketAccessRemediation)
            || (enableCloudTrailBucketAccessRemediation == null && ctx.security == SecurityProfile.PRODUCTION);

        if (shouldEnableCloudTrailBucketAccessRemediation) {
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
     * Used when createConfigInfrastructure = false.
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
                .configRuleName(this.stackName + "-pci-dss-vpc-default-sg-closed")
                .description("PCI-DSS Req 1.3: Prohibit direct public access between internet and cardholder data")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_DEFAULT_SECURITY_GROUP_CLOSED")
                        .build())
                .build();
        vpcDefaultSecurityGroupClosed.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        vpcDefaultSecurityGroupClosed.addOverride("Condition", pciDssCondition.getLogicalId());
        vpcDefaultSecurityGroupClosed.getNode().addDependency(recorder);

        // Requirement 2: Secure Configuration
        CfnConfigRule ec2InstanceManagedBySsm = CfnConfigRule.Builder.create(this, "PciDssEc2ManagedBySsm")
                .configRuleName(this.stackName + "-pci-dss-ec2-managed-by-ssm")
                .description("PCI-DSS Req 2: Secure system configuration management")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_INSTANCE_MANAGED_BY_SSM")
                        .build())
                .build();
        ec2InstanceManagedBySsm.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        ec2InstanceManagedBySsm.addOverride("Condition", pciDssCondition.getLogicalId());
        ec2InstanceManagedBySsm.getNode().addDependency(recorder);

        // Requirement 3: Protect stored cardholder data
        CfnConfigRule rdsEncryptionEnabled = CfnConfigRule.Builder.create(this, "PciDssRdsEncryption")
                .configRuleName(this.stackName + "-pci-dss-rds-encryption-enabled")
                .description("PCI-DSS Req 3.4: Render cardholder data unreadable with encryption")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        rdsEncryptionEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rdsEncryptionEnabled.addOverride("Condition", pciDssRdsCondition.getLogicalId());
        rdsEncryptionEnabled.getNode().addDependency(recorder);

        CfnConfigRule rdsPublicAccessCheck = CfnConfigRule.Builder.create(this, "PciDssRdsPublicAccess")
                .configRuleName(this.stackName + "-pci-dss-rds-instance-public-access-check")
                .description("PCI-DSS Req 1.3.1: Prohibit direct public access between internet and cardholder data")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_INSTANCE_PUBLIC_ACCESS_CHECK")
                        .build())
                .build();
        rdsPublicAccessCheck.addOverride("DeletionPolicy", "Delete");
        rdsPublicAccessCheck.addOverride("Condition", pciDssRdsCondition.getLogicalId());
        rdsPublicAccessCheck.getNode().addDependency(recorder);

        CfnConfigRule rdsBackupEnabled = CfnConfigRule.Builder.create(this, "PciDssRdsBackup")
                .configRuleName(this.stackName + "-pci-dss-db-instance-backup-enabled")
                .description("PCI-DSS Req 3.4: Protect stored cardholder data with backups")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DB_INSTANCE_BACKUP_ENABLED")
                        .build())
                .build();
        rdsBackupEnabled.addOverride("DeletionPolicy", "Delete");
        rdsBackupEnabled.addOverride("Condition", pciDssRdsCondition.getLogicalId());
        rdsBackupEnabled.getNode().addDependency(recorder);

        CfnConfigRule rdsAutoUpgrade = CfnConfigRule.Builder.create(this, "PciDssRdsAutoUpgrade")
                .configRuleName(this.stackName + "-pci-dss-rds-automatic-minor-version-upgrade")
                .description("PCI-DSS Req 6.2: Ensure all system components are protected with security patches")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED")
                        .build())
                .build();
        rdsAutoUpgrade.addOverride("DeletionPolicy", "Delete");
        rdsAutoUpgrade.addOverride("Condition", pciDssRdsCondition.getLogicalId());
        rdsAutoUpgrade.getNode().addDependency(recorder);
        createRdsAutoMinorVersionUpgradeRemediation(rdsAutoUpgrade);

        CfnConfigRule rdsLoggingEnabled = CfnConfigRule.Builder.create(this, "PciDssRdsLogging")
                .configRuleName(this.stackName + "-pci-dss-rds-logging-enabled")
                .description("PCI-DSS Req 10.2: Implement automated audit trails for all system components")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_LOGGING_ENABLED")
                        .build())
                .build();
        rdsLoggingEnabled.addOverride("DeletionPolicy", "Delete");
        rdsLoggingEnabled.addOverride("Condition", pciDssRdsCondition.getLogicalId());
        rdsLoggingEnabled.getNode().addDependency(recorder);

        // Requirement 4: Encrypt transmission
        CfnConfigRule elbTlsHttpsListenersOnly = CfnConfigRule.Builder.create(this, "PciDssElbTlsOnly")
                .configRuleName(this.stackName + "-pci-dss-elb-tls-https-only")
                .description("PCI-DSS Req 4.1: Use strong cryptography for transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_TLS_HTTPS_LISTENERS_ONLY")
                        .build())
                .build();
        elbTlsHttpsListenersOnly.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        elbTlsHttpsListenersOnly.addOverride("Condition", pciDssCondition.getLogicalId());
        elbTlsHttpsListenersOnly.getNode().addDependency(recorder);

        // Requirement 7: Restrict access by business need to know
        CfnConfigRule iamPolicyNoStatementsWithAdminAccess = CfnConfigRule.Builder.create(this, "PciDssIamNoAdminPolicy")
                .configRuleName(this.stackName + "-pci-dss-iam-no-admin-policy")
                .description("PCI-DSS Req 7.1: Limit access to system components by business need-to-know")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS")
                        .build())
                .build();
        iamPolicyNoStatementsWithAdminAccess.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamPolicyNoStatementsWithAdminAccess.addOverride("Condition", pciDssCondition.getLogicalId());
        iamPolicyNoStatementsWithAdminAccess.getNode().addDependency(recorder);

        // Requirement 8: Identify and authenticate access
        CfnConfigRule iamUserMfaEnabled = CfnConfigRule.Builder.create(this, "PciDssIamMfaEnabled")
                .configRuleName(this.stackName + "-pci-dss-iam-user-mfa-enabled")
                .description("PCI-DSS Req 8.3: Multi-factor authentication for remote access")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_MFA_ENABLED")
                        .build())
                .build();
        iamUserMfaEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamUserMfaEnabled.addOverride("Condition", pciDssCondition.getLogicalId());
        iamUserMfaEnabled.getNode().addDependency(recorder);

        // Requirement 10: Track and monitor access
        Map<String, Object> alarmActionParams = Map.of(
                "alarmActionRequired", "true",
                "insufficientDataActionRequired", "false",
                "okActionRequired", "false"
        );
        CfnConfigRule cloudwatchAlarmActionCheck = CfnConfigRule.Builder.create(this, "PciDssCloudWatchAlarmAction")
                .configRuleName(this.stackName + "-pci-dss-cloudwatch-alarm-action")
                .description("PCI-DSS Req 10.6: Review logs daily for suspicious activity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDWATCH_ALARM_ACTION_CHECK")
                        .build())
                .inputParameters(alarmActionParams)
                .build();
        cloudwatchAlarmActionCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudwatchAlarmActionCheck.addOverride("Condition", pciDssCondition.getLogicalId());
        cloudwatchAlarmActionCheck.getNode().addDependency(recorder);

        // Requirement 11: Test security systems
        CfnConfigRule guardDutyEnabledCentralized = CfnConfigRule.Builder.create(this, "PciDssGuardDutyEnabled")
                .configRuleName(this.stackName + "-pci-dss-guardduty-enabled")
                .description("PCI-DSS Req 11.4: Use intrusion detection systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_ENABLED_CENTRALIZED")
                        .build())
                .build();
        guardDutyEnabledCentralized.addOverride("DeletionPolicy", "Delete");
        guardDutyEnabledCentralized.addOverride("Condition", pciDssCondition.getLogicalId());
        guardDutyEnabledCentralized.getNode().addDependency(recorder);

        // Automatic remediation: enables GuardDuty if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableGuardDutyRemediation) ||
            (enableGuardDutyRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createGuardDutyRemediation(guardDutyEnabledCentralized);
        }

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
                .configRuleName(this.stackName + "-soc2-iam-user-no-policies")
                .description("SOC 2 CC6.1: Implement role-based access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_NO_POLICIES_CHECK")
                        .build())
                .build();
        iamUserNoPolicies.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamUserNoPolicies.addOverride("Condition", soc2Condition.getLogicalId());
        iamUserNoPolicies.getNode().addDependency(recorder);

        // CC6.6: Network Segmentation
        CfnConfigRule restrictedSshCheck = CfnConfigRule.Builder.create(this, "Soc2RestrictedSsh")
                .configRuleName(this.stackName + "-soc2-restricted-ssh")
                .description("SOC 2 CC6.6: Network segmentation and access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("INCOMING_SSH_DISABLED")
                        .build())
                .build();
        restrictedSshCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        restrictedSshCheck.addOverride("Condition", soc2Condition.getLogicalId());
        restrictedSshCheck.getNode().addDependency(recorder);

        // CC6.7: Transmission Encryption
        CfnConfigRule albHttpToHttpsRedirection = CfnConfigRule.Builder.create(this, "Soc2AlbHttpsRedirection")
                .configRuleName(this.stackName + "-soc2-alb-https-redirection")
                .description("SOC 2 CC6.7: Encrypt data in transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_HTTP_TO_HTTPS_REDIRECTION_CHECK")
                        .build())
                .build();
        albHttpToHttpsRedirection.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        albHttpToHttpsRedirection.addOverride("Condition", soc2Condition.getLogicalId());
        albHttpToHttpsRedirection.getNode().addDependency(recorder);

        // CC7.2: System Monitoring
        CfnConfigRule securityHubEnabled = CfnConfigRule.Builder.create(this, "Soc2SecurityHubEnabled")
                .configRuleName(this.stackName + "-soc2-security-hub-enabled")
                .description("SOC 2 CC7.2: Monitor system components for anomalies")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("SECURITYHUB_ENABLED")
                        .build())
                .build();
        securityHubEnabled.addOverride("DeletionPolicy", "Delete");
        securityHubEnabled.addOverride("Condition", soc2Condition.getLogicalId());
        securityHubEnabled.getNode().addDependency(recorder);

        // Automatic remediation: enables Security Hub if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableSecurityHubRemediation) ||
            (enableSecurityHubRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createSecurityHubRemediation(securityHubEnabled);
        }

        // CC7.2: Vulnerability Scanning
        CfnConfigRule inspectorEnabled = CfnConfigRule.Builder.create(this, "Soc2InspectorEnabled")
                .configRuleName(this.stackName + "-soc2-inspector-enabled")
                .description("SOC 2 CC7.2: Continuous vulnerability scanning")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("INSPECTOR_ENABLED")
                        .build())
                .build();
        inspectorEnabled.addOverride("DeletionPolicy", "Delete");
        inspectorEnabled.addOverride("Condition", soc2Condition.getLogicalId());
        inspectorEnabled.getNode().addDependency(recorder);

        // Automatic remediation: enables Inspector if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableInspectorRemediation) ||
            (enableInspectorRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createInspectorRemediation(inspectorEnabled);
        }

        // CC7.2: Sensitive Data Protection
        CfnConfigRule macieEnabled = CfnConfigRule.Builder.create(this, "Soc2MacieEnabled")
                .configRuleName(this.stackName + "-soc2-macie-enabled")
                .description("SOC 2 CC7.2: Sensitive data discovery and protection")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("MACIE_ENABLED")
                        .build())
                .build();
        macieEnabled.addOverride("DeletionPolicy", "Delete");
        macieEnabled.addOverride("Condition", soc2Condition.getLogicalId());
        macieEnabled.getNode().addDependency(recorder);

        // Automatic remediation: enables Macie if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableMacieRemediation) ||
            (enableMacieRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createMacieRemediation(macieEnabled);
        }

        // CC8.1: Change Management
        CfnConfigRule cloudtrailS3DataEventsEnabled = CfnConfigRule.Builder.create(this, "Soc2CloudTrailS3DataEvents")
                .configRuleName(this.stackName + "-soc2-cloudtrail-s3-data-events")
                .description("SOC 2 CC8.1: Track and authorize infrastructure changes")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDTRAIL_S3_DATAEVENTS_ENABLED")
                        .build())
                .build();
        cloudtrailS3DataEventsEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudtrailS3DataEventsEnabled.addOverride("Condition", soc2Condition.getLogicalId());
        cloudtrailS3DataEventsEnabled.getNode().addDependency(recorder);

        // A1.2: High Availability (for production)
        // A1.3: Data Backup
        CfnConfigRule soc2RdsBackupEnabled = CfnConfigRule.Builder.create(this, "Soc2RdsBackup")
                .configRuleName(this.stackName + "-soc2-db-instance-backup-enabled")
                .description("SOC 2 A1.3: Implement data backup procedures")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DB_INSTANCE_BACKUP_ENABLED")
                        .build())
                .build();
        soc2RdsBackupEnabled.addOverride("DeletionPolicy", "Delete");
        soc2RdsBackupEnabled.addOverride("Condition", soc2RdsCondition.getLogicalId());
        soc2RdsBackupEnabled.getNode().addDependency(recorder);

        // CC6.1: Logical Access Security Controls
        CfnConfigRule soc2RdsPublicAccess = CfnConfigRule.Builder.create(this, "Soc2RdsPublicAccess")
                .configRuleName(this.stackName + "-soc2-rds-instance-public-access-check")
                .description("SOC 2 CC6.1: Restrict logical access to databases")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_INSTANCE_PUBLIC_ACCESS_CHECK")
                        .build())
                .build();
        soc2RdsPublicAccess.addOverride("DeletionPolicy", "Delete");
        soc2RdsPublicAccess.addOverride("Condition", soc2RdsCondition.getLogicalId());
        soc2RdsPublicAccess.getNode().addDependency(recorder);

        // CC6.1: Encryption Controls
        CfnConfigRule soc2RdsEncryption = CfnConfigRule.Builder.create(this, "Soc2RdsEncryption")
                .configRuleName(this.stackName + "-soc2-rds-storage-encrypted")
                .description("SOC 2 CC6.1: Encrypt data at rest to protect logical access")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        soc2RdsEncryption.addOverride("DeletionPolicy", "Delete");
        soc2RdsEncryption.addOverride("Condition", soc2RdsCondition.getLogicalId());
        soc2RdsEncryption.getNode().addDependency(recorder);

        // CC7.2: System Monitoring
        CfnConfigRule soc2RdsLogging = CfnConfigRule.Builder.create(this, "Soc2RdsLogging")
                .configRuleName(this.stackName + "-soc2-rds-logging-enabled")
                .description("SOC 2 CC7.2: Enable logging to monitor and detect system anomalies")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_LOGGING_ENABLED")
                        .build())
                .build();
        soc2RdsLogging.addOverride("DeletionPolicy", "Delete");
        soc2RdsLogging.addOverride("Condition", soc2RdsCondition.getLogicalId());
        soc2RdsLogging.getNode().addDependency(recorder);

        // CC7.1: System Operations
        CfnConfigRule soc2RdsAutoUpgrade = CfnConfigRule.Builder.create(this, "Soc2RdsAutoUpgrade")
                .configRuleName(this.stackName + "-soc2-rds-automatic-minor-version-upgrade")
                .description("SOC 2 CC7.1: Ensure systems are updated with security patches")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED")
                        .build())
                .build();
        soc2RdsAutoUpgrade.addOverride("DeletionPolicy", "Delete");
        soc2RdsAutoUpgrade.addOverride("Condition", soc2RdsCondition.getLogicalId());
        soc2RdsAutoUpgrade.getNode().addDependency(recorder);
        createRdsAutoMinorVersionUpgradeRemediation(soc2RdsAutoUpgrade);

        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule rdsMultiAz = CfnConfigRule.Builder.create(this, "Soc2RdsMultiAz")
                    .configRuleName(this.stackName + "-soc2-rds-multi-az-support")
                    .description("SOC 2 A1.2: Deploy across multiple availability zones")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_MULTI_AZ_SUPPORT")
                            .build())
                    .build();
            rdsMultiAz.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
            rdsMultiAz.addOverride("Condition", soc2RdsCondition.getLogicalId());
            rdsMultiAz.getNode().addDependency(recorder);

            CfnConfigRule soc2RdsDeletionProtection = CfnConfigRule.Builder.create(this, "Soc2RdsDeletionProtection")
                    .configRuleName(this.stackName + "-soc2-rds-deletion-protection-enabled")
                    .description("SOC 2 A1.2: Protect critical databases from accidental deletion")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_INSTANCE_DELETION_PROTECTION_ENABLED")
                            .build())
                    .build();
            soc2RdsDeletionProtection.addOverride("DeletionPolicy", "Delete");
            soc2RdsDeletionProtection.addOverride("Condition", soc2RdsCondition.getLogicalId());
            soc2RdsDeletionProtection.getNode().addDependency(recorder);
            createRdsDeletionProtectionRemediation(soc2RdsDeletionProtection);

            CfnConfigRule soc2RdsEnhancedMonitoring = CfnConfigRule.Builder.create(this, "Soc2RdsEnhancedMonitoring")
                    .configRuleName(this.stackName + "-soc2-rds-enhanced-monitoring-enabled")
                    .description("SOC 2 CC7.2: Monitor system components and detect anomalies")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_ENHANCED_MONITORING_ENABLED")
                            .build())
                    .build();
            soc2RdsEnhancedMonitoring.addOverride("DeletionPolicy", "Delete");
            soc2RdsEnhancedMonitoring.addOverride("Condition", soc2Condition.getLogicalId());
            soc2RdsEnhancedMonitoring.getNode().addDependency(recorder);

            CfnConfigRule elbDeletionProtection = CfnConfigRule.Builder.create(this, "Soc2ElbDeletionProtection")
                    .configRuleName(this.stackName + "-soc2-elb-deletion-protection")
                    .description("SOC 2 A1.2: Protect critical components from accidental deletion")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("ELB_DELETION_PROTECTION_ENABLED")
                            .build())
                    .build();
            elbDeletionProtection.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
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
                .configRuleName(this.stackName + "-hipaa-cloudtrail-cloudwatch-logs")
                .description("HIPAA §164.308(a)(1)(ii)(D): Information system activity review")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_CLOUD_WATCH_LOGS_ENABLED")
                        .build())
                .build();
        cloudtrailCloudwatchLogsEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudtrailCloudwatchLogsEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        cloudtrailCloudwatchLogsEnabled.getNode().addDependency(recorder);

        // §164.308(a)(3): Workforce Security
        CfnConfigRule iamUserGroupMembershipCheck = CfnConfigRule.Builder.create(this, "HipaaIamGroupMembership")
                .configRuleName(this.stackName + "-hipaa-iam-group-membership")
                .description("HIPAA §164.308(a)(3): Implement procedures for workforce clearance")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_GROUP_MEMBERSHIP_CHECK")
                        .build())
                .build();
        iamUserGroupMembershipCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamUserGroupMembershipCheck.addOverride("Condition", hipaaCondition.getLogicalId());
        iamUserGroupMembershipCheck.getNode().addDependency(recorder);

        // §164.310(d): Device and Media Controls (Backup)
        CfnConfigRule dynamodbPitrEnabled = CfnConfigRule.Builder.create(this, "HipaaDynamoDbPitr")
                .configRuleName(this.stackName + "-hipaa-dynamodb-pitr-enabled")
                .description("HIPAA §164.310(d)(2)(iv): Create backup copies of ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DYNAMODB_PITR_ENABLED")
                        .build())
                .build();
        dynamodbPitrEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        dynamodbPitrEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        dynamodbPitrEnabled.getNode().addDependency(recorder);

        CfnConfigRule rdsSnapshotEncrypted = CfnConfigRule.Builder.create(this, "HipaaRdsSnapshotEncrypted")
                .configRuleName(this.stackName + "-hipaa-rds-snapshot-encrypted")
                .description("HIPAA §164.312(a)(2)(iv): Encrypt ePHI backups")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_SNAPSHOT_ENCRYPTED")
                        .build())
                .build();
        rdsSnapshotEncrypted.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rdsSnapshotEncrypted.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        rdsSnapshotEncrypted.getNode().addDependency(recorder);

        CfnConfigRule hipaaRdsEncryption = CfnConfigRule.Builder.create(this, "HipaaRdsEncryption")
                .configRuleName(this.stackName + "-hipaa-rds-storage-encrypted")
                .description("HIPAA §164.312(a)(2)(iv): Encrypt ePHI at rest")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        hipaaRdsEncryption.addOverride("DeletionPolicy", "Delete");
        hipaaRdsEncryption.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        hipaaRdsEncryption.getNode().addDependency(recorder);

        CfnConfigRule hipaaRdsPublicAccess = CfnConfigRule.Builder.create(this, "HipaaRdsPublicAccess")
                .configRuleName(this.stackName + "-hipaa-rds-instance-public-access-check")
                .description("HIPAA §164.308(a)(4)(ii)(B): Implement access management controls")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_INSTANCE_PUBLIC_ACCESS_CHECK")
                        .build())
                .build();
        hipaaRdsPublicAccess.addOverride("DeletionPolicy", "Delete");
        hipaaRdsPublicAccess.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        hipaaRdsPublicAccess.getNode().addDependency(recorder);

        CfnConfigRule hipaaRdsBackupEnabled = CfnConfigRule.Builder.create(this, "HipaaRdsBackup")
                .configRuleName(this.stackName + "-hipaa-db-instance-backup-enabled")
                .description("HIPAA §164.310(d)(2)(iii): Create retrievable backup copies of ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DB_INSTANCE_BACKUP_ENABLED")
                        .build())
                .build();
        hipaaRdsBackupEnabled.addOverride("DeletionPolicy", "Delete");
        hipaaRdsBackupEnabled.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        hipaaRdsBackupEnabled.getNode().addDependency(recorder);

        CfnConfigRule hipaaRdsAutoUpgrade = CfnConfigRule.Builder.create(this, "HipaaRdsAutoUpgrade")
                .configRuleName(this.stackName + "-hipaa-rds-automatic-minor-version-upgrade")
                .description("HIPAA §164.308(a)(5)(ii)(B): Install security patches and updates")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED")
                        .build())
                .build();
        hipaaRdsAutoUpgrade.addOverride("DeletionPolicy", "Delete");
        hipaaRdsAutoUpgrade.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        hipaaRdsAutoUpgrade.getNode().addDependency(recorder);
        createRdsAutoMinorVersionUpgradeRemediation(hipaaRdsAutoUpgrade);

        CfnConfigRule hipaaRdsLoggingEnabled = CfnConfigRule.Builder.create(this, "HipaaRdsLogging")
                .configRuleName(this.stackName + "-hipaa-rds-logging-enabled")
                .description("HIPAA §164.312(b): Implement hardware/software audit controls")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_LOGGING_ENABLED")
                        .build())
                .build();
        hipaaRdsLoggingEnabled.addOverride("DeletionPolicy", "Delete");
        hipaaRdsLoggingEnabled.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        hipaaRdsLoggingEnabled.getNode().addDependency(recorder);

        CfnConfigRule hipaaRdsDeletionProtection = CfnConfigRule.Builder.create(this, "HipaaRdsDeletionProtection")
                .configRuleName(this.stackName + "-hipaa-rds-deletion-protection-enabled")
                .description("HIPAA §164.308(a)(7)(ii)(A): Establish data backup contingency plan")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_INSTANCE_DELETION_PROTECTION_ENABLED")
                        .build())
                .build();
        hipaaRdsDeletionProtection.addOverride("DeletionPolicy", "Delete");
        hipaaRdsDeletionProtection.addOverride("Condition", hipaaRdsCondition.getLogicalId());
        hipaaRdsDeletionProtection.getNode().addDependency(recorder);
        createRdsDeletionProtectionRemediation(hipaaRdsDeletionProtection);

        // §164.312(a)(1): Access Control
        CfnConfigRule rootAccountMfaEnabled = CfnConfigRule.Builder.create(this, "HipaaRootMfaEnabled")
                .configRuleName(this.stackName + "-hipaa-root-account-mfa-enabled")
                .description("HIPAA §164.312(a)(2)(i): Assign unique user identification")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ROOT_ACCOUNT_MFA_ENABLED")
                        .build())
                .build();
        rootAccountMfaEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rootAccountMfaEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        rootAccountMfaEnabled.getNode().addDependency(recorder);

        // §164.312(b): Audit Controls
        CfnConfigRule albWafEnabled = CfnConfigRule.Builder.create(this, "HipaaAlbWafEnabled")
                .configRuleName(this.stackName + "-hipaa-alb-waf-enabled")
                .description("HIPAA §164.312(b): Record and examine activity in systems with ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_WAF_ENABLED")
                        .build())
                .build();
        albWafEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        albWafEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        albWafEnabled.getNode().addDependency(recorder);

        // §164.312(c)(1): Integrity Controls
        CfnConfigRule cloudtrailEncryptionEnabled = CfnConfigRule.Builder.create(this, "HipaaCloudTrailEncryption")
                .configRuleName(this.stackName + "-hipaa-cloudtrail-encryption-enabled")
                .description("HIPAA §164.312(c)(2): Authenticate ePHI integrity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_ENCRYPTION_ENABLED")
                        .build())
                .build();
        cloudtrailEncryptionEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudtrailEncryptionEnabled.addOverride("Condition", hipaaCondition.getLogicalId());
        cloudtrailEncryptionEnabled.getNode().addDependency(recorder);

        // §164.312(e)(1): Transmission Security
        CfnConfigRule elbAcmCertificateRequired = CfnConfigRule.Builder.create(this, "HipaaElbAcmCertificate")
                .configRuleName(this.stackName + "-hipaa-elb-acm-certificate-required")
                .description("HIPAA §164.312(e)(2)(ii): Encrypt ePHI during transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_ACM_CERTIFICATE_REQUIRED")
                        .build())
                .build();
        elbAcmCertificateRequired.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
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
                .configRuleName(this.stackName + "-gdpr-ec2-ebs-optimized")
                .description("GDPR Art. 25: Data protection by design - optimize storage security")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_EBS_OPTIMIZATION_CHECK")
                        .build())
                .build();
        ec2EbsOptimized.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        ec2EbsOptimized.addOverride("Condition", gdprCondition.getLogicalId());
        ec2EbsOptimized.getNode().addDependency(recorder);

        // Article 30: Records of Processing Activities
        CfnConfigRule vpcFlowLogsEnabled = CfnConfigRule.Builder.create(this, "GdprVpcFlowLogs")
                .configRuleName(this.stackName + "-gdpr-vpc-flow-logs-enabled")
                .description("GDPR Art. 30(1): Maintain records of processing activities")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_FLOW_LOGS_ENABLED")
                        .build())
                .build();
        vpcFlowLogsEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        vpcFlowLogsEnabled.addOverride("Condition", gdprCondition.getLogicalId());
        vpcFlowLogsEnabled.getNode().addDependency(recorder);

        // Article 32(1)(a): Pseudonymisation and Encryption
        CfnConfigRule s3DefaultEncryptionKms = CfnConfigRule.Builder.create(this, "GdprS3DefaultEncryptionKms")
                .configRuleName(this.stackName + "-gdpr-s3-default-encryption-kms")
                .description("GDPR Art. 32(1)(a): Encrypt personal data at rest")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("S3_DEFAULT_ENCRYPTION_KMS")
                        .build())
                .build();
        s3DefaultEncryptionKms.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        s3DefaultEncryptionKms.addOverride("Condition", gdprCondition.getLogicalId());
        s3DefaultEncryptionKms.getNode().addDependency(recorder);

        CfnConfigRule kmsBackingKeyRotationEnabled = CfnConfigRule.Builder.create(this, "GdprKmsKeyRotation")
                .configRuleName(this.stackName + "-gdpr-kms-backing-key-rotation")
                .description("GDPR Art. 32(1)(a): Rotate encryption keys regularly")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CMK_BACKING_KEY_ROTATION_ENABLED")
                        .build())
                .build();
        kmsBackingKeyRotationEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        kmsBackingKeyRotationEnabled.addOverride("Condition", gdprCondition.getLogicalId());
        kmsBackingKeyRotationEnabled.getNode().addDependency(recorder);

        CfnConfigRule gdprRdsEncryption = CfnConfigRule.Builder.create(this, "GdprRdsEncryption")
                .configRuleName(this.stackName + "-gdpr-rds-storage-encrypted")
                .description("GDPR Art. 32(1)(a): Encrypt personal data in databases at rest")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        gdprRdsEncryption.addOverride("DeletionPolicy", "Delete");
        gdprRdsEncryption.addOverride("Condition", gdprRdsCondition.getLogicalId());
        gdprRdsEncryption.getNode().addDependency(recorder);

        // Article 32(1)(b): Confidentiality
        CfnConfigRule gdprRdsPublicAccess = CfnConfigRule.Builder.create(this, "GdprRdsPublicAccess")
                .configRuleName(this.stackName + "-gdpr-rds-instance-public-access-check")
                .description("GDPR Art. 32(1)(b): Ensure confidentiality of personal data systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_INSTANCE_PUBLIC_ACCESS_CHECK")
                        .build())
                .build();
        gdprRdsPublicAccess.addOverride("DeletionPolicy", "Delete");
        gdprRdsPublicAccess.addOverride("Condition", gdprRdsCondition.getLogicalId());
        gdprRdsPublicAccess.getNode().addDependency(recorder);
        CfnConfigRule restrictedRdpCheck = CfnConfigRule.Builder.create(this, "GdprRestrictedRdp")
                .configRuleName(this.stackName + "-gdpr-restricted-rdp")
                .description("GDPR Art. 32(1)(b): Ensure ongoing confidentiality of systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RESTRICTED_INCOMING_TRAFFIC")
                        .build())
                .build();
        restrictedRdpCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        restrictedRdpCheck.addOverride("Condition", gdprCondition.getLogicalId());
        restrictedRdpCheck.getNode().addDependency(recorder);

        // Article 32(1)(c): Availability and Resilience
        CfnConfigRule gdprRdsBackupEnabled = CfnConfigRule.Builder.create(this, "GdprRdsBackup")
                .configRuleName(this.stackName + "-gdpr-db-instance-backup-enabled")
                .description("GDPR Art. 32(1)(c): Ensure resilience through data backups")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DB_INSTANCE_BACKUP_ENABLED")
                        .build())
                .build();
        gdprRdsBackupEnabled.addOverride("DeletionPolicy", "Delete");
        gdprRdsBackupEnabled.addOverride("Condition", gdprRdsCondition.getLogicalId());
        gdprRdsBackupEnabled.getNode().addDependency(recorder);

        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule dynamodbAutoscalingEnabled = CfnConfigRule.Builder.create(this, "GdprDynamoDbAutoscaling")
                    .configRuleName(this.stackName + "-gdpr-dynamodb-autoscaling-enabled")
                    .description("GDPR Art. 32(1)(c): Ensure resilience and availability of systems")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("DYNAMODB_AUTOSCALING_ENABLED")
                            .build())
                    .build();
            dynamodbAutoscalingEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
            dynamodbAutoscalingEnabled.addOverride("Condition", gdprCondition.getLogicalId());
            dynamodbAutoscalingEnabled.getNode().addDependency(recorder);

            CfnConfigRule s3BucketReplicationEnabled = CfnConfigRule.Builder.create(this, "GdprS3Replication")
                    .configRuleName(this.stackName + "-gdpr-s3-bucket-replication")
                    .description("GDPR Art. 32(1)(c): Implement geographic redundancy")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("S3_BUCKET_REPLICATION_ENABLED")
                            .build())
                    .build();
            s3BucketReplicationEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
            s3BucketReplicationEnabled.addOverride("Condition", gdprCondition.getLogicalId());
            s3BucketReplicationEnabled.getNode().addDependency(recorder);
        }

        // Article 32(1)(d): Audit and Assessment Capabilities
        CfnConfigRule gdprRdsLoggingEnabled = CfnConfigRule.Builder.create(this, "GdprRdsLogging")
                .configRuleName(this.stackName + "-gdpr-rds-logging-enabled")
                .description("GDPR Art. 32(1)(d): Implement processes for testing and assessing security")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_LOGGING_ENABLED")
                        .build())
                .build();
        gdprRdsLoggingEnabled.addOverride("DeletionPolicy", "Delete");
        gdprRdsLoggingEnabled.addOverride("Condition", gdprRdsCondition.getLogicalId());
        gdprRdsLoggingEnabled.getNode().addDependency(recorder);

        // Article 32(1)(a): Security of Processing - Automated Updates
        CfnConfigRule gdprRdsAutoUpgrade = CfnConfigRule.Builder.create(this, "GdprRdsAutoUpgrade")
                .configRuleName(this.stackName + "-gdpr-rds-automatic-minor-version-upgrade")
                .description("GDPR Art. 32(1)(a): Maintain security through automated system updates")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED")
                        .build())
                .build();
        gdprRdsAutoUpgrade.addOverride("DeletionPolicy", "Delete");
        gdprRdsAutoUpgrade.addOverride("Condition", gdprRdsCondition.getLogicalId());
        gdprRdsAutoUpgrade.getNode().addDependency(recorder);
        createRdsAutoMinorVersionUpgradeRemediation(gdprRdsAutoUpgrade);

        // Article 32(1)(c): Availability and Resilience - Deletion Protection
        CfnConfigRule gdprRdsDeletionProtection = CfnConfigRule.Builder.create(this, "GdprRdsDeletionProtection")
                .configRuleName(this.stackName + "-gdpr-rds-deletion-protection-enabled")
                .description("GDPR Art. 32(1)(c): Protect personal data from accidental destruction")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_INSTANCE_DELETION_PROTECTION_ENABLED")
                        .build())
                .build();
        gdprRdsDeletionProtection.addOverride("DeletionPolicy", "Delete");
        gdprRdsDeletionProtection.addOverride("Condition", gdprRdsCondition.getLogicalId());
        gdprRdsDeletionProtection.getNode().addDependency(recorder);
        createRdsDeletionProtectionRemediation(gdprRdsDeletionProtection);

        if (security == SecurityProfile.PRODUCTION) {
            // Article 32(1)(c): High Availability for Production Systems
            CfnConfigRule gdprRdsMultiAz = CfnConfigRule.Builder.create(this, "GdprRdsMultiAz")
                    .configRuleName(this.stackName + "-gdpr-rds-multi-az-support")
                    .description("GDPR Art. 32(1)(c): Ensure ability to restore availability of personal data")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_MULTI_AZ_SUPPORT")
                            .build())
                    .build();
            gdprRdsMultiAz.addOverride("DeletionPolicy", "Delete");
            gdprRdsMultiAz.addOverride("Condition", gdprCondition.getLogicalId());
            gdprRdsMultiAz.getNode().addDependency(recorder);
        }

        // Article 33: Breach Detection
        CfnConfigRule guarddutyNonArchivedFindings = CfnConfigRule.Builder.create(this, "GdprGuardDutyFindings")
                .configRuleName(this.stackName + "-gdpr-guardduty-non-archived-findings")
                .description("GDPR Art. 33(1): Detect data breaches within 72 hours")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_NON_ARCHIVED_FINDINGS")
                        .build())
                .build();
        guarddutyNonArchivedFindings.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        guarddutyNonArchivedFindings.addOverride("Condition", gdprCondition.getLogicalId());
        guarddutyNonArchivedFindings.getNode().addDependency(recorder);

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "11" : "9") + " GDPR Config rules with conditional deployment");
    }

    /**
     * Creates PCI-DSS Config rules WITHOUT recorder dependency.
     * Rules will reference existing recorder by name at runtime.
     */
    private void createPciDssConfigRulesWithoutRecorder() {
        LOG.info("Creating PCI-DSS AWS Config rules (condition-controlled, no recorder dependency)");

        // Requirement 1: Network Segmentation
        CfnConfigRule vpcDefaultSecurityGroupClosed = CfnConfigRule.Builder.create(this, "PciDssVpcDefaultSecurityGroupClosed")
                .configRuleName(this.stackName + "-pci-dss-vpc-default-sg-closed")
                .description("PCI-DSS Req 1.3: Prohibit direct public access between internet and cardholder data")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_DEFAULT_SECURITY_GROUP_CLOSED")
                        .build())
                .build();
        vpcDefaultSecurityGroupClosed.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        vpcDefaultSecurityGroupClosed.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 2: Secure Configuration
        CfnConfigRule ec2InstanceManagedBySsm = CfnConfigRule.Builder.create(this, "PciDssEc2ManagedBySsm")
                .configRuleName(this.stackName + "-pci-dss-ec2-managed-by-ssm")
                .description("PCI-DSS Req 2: Secure system configuration management")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_INSTANCE_MANAGED_BY_SSM")
                        .build())
                .build();
        ec2InstanceManagedBySsm.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        ec2InstanceManagedBySsm.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 3: Protect stored cardholder data
        CfnConfigRule rdsEncryptionEnabled = CfnConfigRule.Builder.create(this, "PciDssRdsEncryption")
                .configRuleName(this.stackName + "-pci-dss-rds-encryption-enabled")
                .description("PCI-DSS Req 3.4: Render cardholder data unreadable with encryption")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_STORAGE_ENCRYPTED")
                        .build())
                .build();
        rdsEncryptionEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rdsEncryptionEnabled.addOverride("Condition", pciDssRdsCondition.getLogicalId());

        // Requirement 4: Encrypt transmission
        CfnConfigRule elbTlsHttpsListenersOnly = CfnConfigRule.Builder.create(this, "PciDssElbTlsOnly")
                .configRuleName(this.stackName + "-pci-dss-elb-tls-https-only")
                .description("PCI-DSS Req 4.1: Use strong cryptography for transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_TLS_HTTPS_LISTENERS_ONLY")
                        .build())
                .build();
        elbTlsHttpsListenersOnly.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        elbTlsHttpsListenersOnly.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 7: Restrict access by business need to know
        CfnConfigRule iamPolicyNoStatementsWithAdminAccess = CfnConfigRule.Builder.create(this, "PciDssIamNoAdminPolicy")
                .configRuleName(this.stackName + "-pci-dss-iam-no-admin-policy")
                .description("PCI-DSS Req 7.1: Limit access to system components by business need-to-know")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS")
                        .build())
                .build();
        iamPolicyNoStatementsWithAdminAccess.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamPolicyNoStatementsWithAdminAccess.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 8: Identify and authenticate access
        CfnConfigRule iamUserMfaEnabled = CfnConfigRule.Builder.create(this, "PciDssIamMfaEnabled")
                .configRuleName(this.stackName + "-pci-dss-iam-user-mfa-enabled")
                .description("PCI-DSS Req 8.3: Multi-factor authentication for remote access")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_MFA_ENABLED")
                        .build())
                .build();
        iamUserMfaEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamUserMfaEnabled.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 10: Track and monitor access
        Map<String, Object> alarmActionParams = Map.of(
                "alarmActionRequired", "true",
                "insufficientDataActionRequired", "false",
                "okActionRequired", "false"
        );
        CfnConfigRule cloudwatchAlarmActionCheck = CfnConfigRule.Builder.create(this, "PciDssCloudWatchAlarmAction")
                .configRuleName(this.stackName + "-pci-dss-cloudwatch-alarm-action")
                .description("PCI-DSS Req 10.6: Review logs daily for suspicious activity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDWATCH_ALARM_ACTION_CHECK")
                        .build())
                .inputParameters(alarmActionParams)
                .build();
        cloudwatchAlarmActionCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudwatchAlarmActionCheck.addOverride("Condition", pciDssCondition.getLogicalId());

        // Requirement 11: Test security systems
        CfnConfigRule guardDutyEnabledCentralized = CfnConfigRule.Builder.create(this, "PciDssGuardDutyEnabled")
                .configRuleName(this.stackName + "-pci-dss-guardduty-enabled")
                .description("PCI-DSS Req 11.4: Use intrusion detection systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_ENABLED_CENTRALIZED")
                        .build())
                .build();
        guardDutyEnabledCentralized.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
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
                .configRuleName(this.stackName + "-soc2-iam-user-no-policies")
                .description("SOC 2 CC6.1: Implement role-based access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_NO_POLICIES_CHECK")
                        .build())
                .build();
        iamUserNoPolicies.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamUserNoPolicies.addOverride("Condition", soc2Condition.getLogicalId());

        // CC6.6: Network Segmentation
        CfnConfigRule restrictedSshCheck = CfnConfigRule.Builder.create(this, "Soc2RestrictedSsh")
                .configRuleName(this.stackName + "-soc2-restricted-ssh")
                .description("SOC 2 CC6.6: Network segmentation and access control")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("INCOMING_SSH_DISABLED")
                        .build())
                .build();
        restrictedSshCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        restrictedSshCheck.addOverride("Condition", soc2Condition.getLogicalId());

        // CC6.7: Transmission Encryption
        CfnConfigRule albHttpToHttpsRedirection = CfnConfigRule.Builder.create(this, "Soc2AlbHttpsRedirection")
                .configRuleName(this.stackName + "-soc2-alb-https-redirection")
                .description("SOC 2 CC6.7: Encrypt data in transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_HTTP_TO_HTTPS_REDIRECTION_CHECK")
                        .build())
                .build();
        albHttpToHttpsRedirection.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        albHttpToHttpsRedirection.addOverride("Condition", soc2Condition.getLogicalId());

        // CC7.2: System Monitoring
        CfnConfigRule securityHubEnabled = CfnConfigRule.Builder.create(this, "Soc2SecurityHubEnabled")
                .configRuleName(this.stackName + "-soc2-security-hub-enabled")
                .description("SOC 2 CC7.2: Monitor system components for anomalies")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("SECURITYHUB_ENABLED")
                        .build())
                .build();
        securityHubEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        securityHubEnabled.addOverride("Condition", soc2Condition.getLogicalId());

        // CC8.1: Change Management
        CfnConfigRule cloudtrailS3DataEventsEnabled = CfnConfigRule.Builder.create(this, "Soc2CloudTrailS3DataEvents")
                .configRuleName(this.stackName + "-soc2-cloudtrail-s3-data-events")
                .description("SOC 2 CC8.1: Track and authorize infrastructure changes")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUDTRAIL_S3_DATAEVENTS_ENABLED")
                        .build())
                .build();
        cloudtrailS3DataEventsEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudtrailS3DataEventsEnabled.addOverride("Condition", soc2Condition.getLogicalId());

        // A1.2: High Availability (for production)
        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule rdsMultiAz = CfnConfigRule.Builder.create(this, "Soc2RdsMultiAz")
                    .configRuleName(this.stackName + "-soc2-rds-multi-az-support")
                    .description("SOC 2 A1.2: Deploy across multiple availability zones")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("RDS_MULTI_AZ_SUPPORT")
                            .build())
                    .build();
            rdsMultiAz.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
            rdsMultiAz.addOverride("Condition", soc2RdsCondition.getLogicalId());

            CfnConfigRule elbDeletionProtection = CfnConfigRule.Builder.create(this, "Soc2ElbDeletionProtection")
                    .configRuleName(this.stackName + "-soc2-elb-deletion-protection")
                    .description("SOC 2 A1.2: Protect critical components from accidental deletion")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("ELB_DELETION_PROTECTION_ENABLED")
                            .build())
                    .build();
            elbDeletionProtection.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
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
                .configRuleName(this.stackName + "-hipaa-cloudtrail-cloudwatch-logs")
                .description("HIPAA §164.308(a)(1)(ii)(D): Information system activity review")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_CLOUD_WATCH_LOGS_ENABLED")
                        .build())
                .build();
        cloudtrailCloudwatchLogsEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudtrailCloudwatchLogsEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.308(a)(3): Workforce Security
        CfnConfigRule iamUserGroupMembershipCheck = CfnConfigRule.Builder.create(this, "HipaaIamGroupMembership")
                .configRuleName(this.stackName + "-hipaa-iam-group-membership")
                .description("HIPAA §164.308(a)(3): Implement procedures for workforce clearance")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("IAM_USER_GROUP_MEMBERSHIP_CHECK")
                        .build())
                .build();
        iamUserGroupMembershipCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        iamUserGroupMembershipCheck.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.310(d): Device and Media Controls (Backup)
        CfnConfigRule dynamodbPitrEnabled = CfnConfigRule.Builder.create(this, "HipaaDynamoDbPitr")
                .configRuleName(this.stackName + "-hipaa-dynamodb-pitr-enabled")
                .description("HIPAA §164.310(d)(2)(iv): Create backup copies of ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("DYNAMODB_PITR_ENABLED")
                        .build())
                .build();
        dynamodbPitrEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        dynamodbPitrEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        CfnConfigRule rdsSnapshotEncrypted = CfnConfigRule.Builder.create(this, "HipaaRdsSnapshotEncrypted")
                .configRuleName(this.stackName + "-hipaa-rds-snapshot-encrypted")
                .description("HIPAA §164.312(a)(2)(iv): Encrypt ePHI backups")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RDS_SNAPSHOT_ENCRYPTED")
                        .build())
                .build();
        rdsSnapshotEncrypted.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rdsSnapshotEncrypted.addOverride("Condition", hipaaRdsCondition.getLogicalId());

        // §164.312(a)(1): Access Control
        CfnConfigRule rootAccountMfaEnabled = CfnConfigRule.Builder.create(this, "HipaaRootMfaEnabled")
                .configRuleName(this.stackName + "-hipaa-root-account-mfa-enabled")
                .description("HIPAA §164.312(a)(2)(i): Assign unique user identification")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ROOT_ACCOUNT_MFA_ENABLED")
                        .build())
                .build();
        rootAccountMfaEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rootAccountMfaEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(b): Audit Controls
        CfnConfigRule albWafEnabled = CfnConfigRule.Builder.create(this, "HipaaAlbWafEnabled")
                .configRuleName(this.stackName + "-hipaa-alb-waf-enabled")
                .description("HIPAA §164.312(b): Record and examine activity in systems with ePHI")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ALB_WAF_ENABLED")
                        .build())
                .build();
        albWafEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        albWafEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(c)(1): Integrity Controls
        CfnConfigRule cloudtrailEncryptionEnabled = CfnConfigRule.Builder.create(this, "HipaaCloudTrailEncryption")
                .configRuleName(this.stackName + "-hipaa-cloudtrail-encryption-enabled")
                .description("HIPAA §164.312(c)(2): Authenticate ePHI integrity")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CLOUD_TRAIL_ENCRYPTION_ENABLED")
                        .build())
                .build();
        cloudtrailEncryptionEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        cloudtrailEncryptionEnabled.addOverride("Condition", hipaaCondition.getLogicalId());

        // §164.312(e)(1): Transmission Security
        CfnConfigRule elbAcmCertificateRequired = CfnConfigRule.Builder.create(this, "HipaaElbAcmCertificate")
                .configRuleName(this.stackName + "-hipaa-elb-acm-certificate-required")
                .description("HIPAA §164.312(e)(2)(ii): Encrypt ePHI during transmission")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("ELB_ACM_CERTIFICATE_REQUIRED")
                        .build())
                .build();
        elbAcmCertificateRequired.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
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
                .configRuleName(this.stackName + "-gdpr-ec2-ebs-optimized")
                .description("GDPR Art. 25: Data protection by design - optimize storage security")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("EC2_EBS_OPTIMIZATION_CHECK")
                        .build())
                .build();
        ec2EbsOptimized.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        ec2EbsOptimized.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 30: Records of Processing Activities
        CfnConfigRule vpcFlowLogsEnabled = CfnConfigRule.Builder.create(this, "GdprVpcFlowLogs")
                .configRuleName(this.stackName + "-gdpr-vpc-flow-logs-enabled")
                .description("GDPR Art. 30(1): Maintain records of processing activities")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("VPC_FLOW_LOGS_ENABLED")
                        .build())
                .build();
        vpcFlowLogsEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        vpcFlowLogsEnabled.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 32(1)(a): Pseudonymisation and Encryption
        CfnConfigRule s3DefaultEncryptionKms = CfnConfigRule.Builder.create(this, "GdprS3DefaultEncryptionKms")
                .configRuleName(this.stackName + "-gdpr-s3-default-encryption-kms")
                .description("GDPR Art. 32(1)(a): Encrypt personal data at rest")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("S3_DEFAULT_ENCRYPTION_KMS")
                        .build())
                .build();
        s3DefaultEncryptionKms.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        s3DefaultEncryptionKms.addOverride("Condition", gdprCondition.getLogicalId());

        CfnConfigRule kmsBackingKeyRotationEnabled = CfnConfigRule.Builder.create(this, "GdprKmsKeyRotation")
                .configRuleName(this.stackName + "-gdpr-kms-backing-key-rotation")
                .description("GDPR Art. 32(1)(a): Rotate encryption keys regularly")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("CMK_BACKING_KEY_ROTATION_ENABLED")
                        .build())
                .build();
        kmsBackingKeyRotationEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        kmsBackingKeyRotationEnabled.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 32(1)(b): Confidentiality
        CfnConfigRule restrictedRdpCheck = CfnConfigRule.Builder.create(this, "GdprRestrictedRdp")
                .configRuleName(this.stackName + "-gdpr-restricted-rdp")
                .description("GDPR Art. 32(1)(b): Ensure ongoing confidentiality of systems")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("RESTRICTED_INCOMING_TRAFFIC")
                        .build())
                .build();
        restrictedRdpCheck.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        restrictedRdpCheck.addOverride("Condition", gdprCondition.getLogicalId());

        // Article 32(1)(c): Availability and Resilience
        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule dynamodbAutoscalingEnabled = CfnConfigRule.Builder.create(this, "GdprDynamoDbAutoscaling")
                    .configRuleName(this.stackName + "-gdpr-dynamodb-autoscaling-enabled")
                    .description("GDPR Art. 32(1)(c): Ensure resilience and availability of systems")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("DYNAMODB_AUTOSCALING_ENABLED")
                            .build())
                    .build();
            dynamodbAutoscalingEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
            dynamodbAutoscalingEnabled.addOverride("Condition", gdprCondition.getLogicalId());

            CfnConfigRule s3BucketReplicationEnabled = CfnConfigRule.Builder.create(this, "GdprS3Replication")
                    .configRuleName(this.stackName + "-gdpr-s3-bucket-replication")
                    .description("GDPR Art. 32(1)(c): Implement geographic redundancy")
                    .source(CfnConfigRule.SourceProperty.builder()
                            .owner("AWS")
                            .sourceIdentifier("S3_BUCKET_REPLICATION_ENABLED")
                            .build())
                    .build();
            s3BucketReplicationEnabled.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
            s3BucketReplicationEnabled.addOverride("Condition", gdprCondition.getLogicalId());
        }

        // Article 33: Breach Detection
        CfnConfigRule guarddutyNonArchivedFindings = CfnConfigRule.Builder.create(this, "GdprGuardDutyFindings")
                .configRuleName(this.stackName + "-gdpr-guardduty-non-archived-findings")
                .description("GDPR Art. 33(1): Detect data breaches within 72 hours")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier("GUARDDUTY_NON_ARCHIVED_FINDINGS")
                        .build())
                .build();
        guarddutyNonArchivedFindings.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
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
        // Scoped to CloudTrail and AuditManager buckets only (HIPAA/PCI-DSS least privilege)
        List<String> s3BucketArns = new ArrayList<>();
        List<String> s3ObjectArns = new ArrayList<>();

        // Add CloudTrail bucket if available
        if (this.trailBucket != null) {
            s3BucketArns.add(this.trailBucket.getBucketArn());
            s3ObjectArns.add(this.trailBucket.arnForObjects("*"));
        }

        // Add AuditManager report bucket
        s3BucketArns.add(assessmentReportBucket.getBucketArn());
        s3ObjectArns.add(assessmentReportBucket.arnForObjects("*"));

        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("S3ReadCloudTrail")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "s3:GetObject",
                        "s3:GetObjectVersion"
                ))
                .resources(s3ObjectArns)
                .build());

        auditManagerRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("S3ListBuckets")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "s3:ListBucket",
                        "s3:ListBucketVersions"
                ))
                .resources(s3BucketArns)
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

        // Suppress CDK-nag warnings for IAM wildcards - all are justified
        NagSuppressions.addResourceSuppressions(
            auditManagerRole,
            List.of(
                NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("S3 bucket object access requires /* pattern for write operations. " +
                           "Service read APIs (AuditManager, CloudTrail, Config, SecurityHub, IAM, EC2) " +
                           "require * for account-wide read-only operations - AWS service requirements.")
                    .build()
            ),
            Boolean.TRUE  // Apply to all policies
        );

        // Get account ID for assessments
        String accountId = software.amazon.awscdk.Stack.of(this).getAccount();

        // Create one assessment per framework
        int assessmentCount = 0;
        int attemptedCount = 0;
        for (String framework : frameworks) {
            attemptedCount++;
            try {
                boolean created = createSingleAssessment(
                    framework,
                    attemptedCount,  // Use attempted count for logging
                    shortId,
                    assessmentReportBucket,
                    auditManagerRole,
                    accountId
                );
                if (created) {
                    assessmentCount++;
                }
            } catch (Exception e) {
                LOG.warning("Failed to create Audit Manager assessment for framework " + framework + ": " + e.getMessage());
                LOG.warning("  Continuing with remaining frameworks...");
            }
        }

        if (assessmentCount > 0) {
            LOG.info("Created " + assessmentCount + " Audit Manager assessment(s) successfully");
        } else {
            LOG.warning("No Audit Manager assessments were created (all frameworks were skipped or failed)");
            LOG.warning("  This is normal for custom frameworks (SOC2, PCI-DSS, HIPAA, GDPR, etc.)");
            LOG.warning("  Compliance validation will use other layers: cdk-nag, FrameworkRules, cfn-guard, AWS Config");
        }
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
     * Populates custom control sets from AuditManagerControlRegistry to map Config rules to framework controls.
     */
    private boolean createSingleAssessment(
        String frameworkName,
        int index,
        String shortId,
        Bucket reportBucket,
        Role role,
        String accountId
    ) {
        // Use stack name instead of security profile to avoid conflicts when switching profiles
        String assessmentName = "audit-" + frameworkName.toLowerCase().replace("_", "-") +
                                "-" + this.stackName + "-" + shortId;
        String constructId = "Assessment" + frameworkName.replace("-", "").replace("_", "");

        LOG.info("Creating assessment " + index + ": " + assessmentName);

        // Log control mappings for this framework
        logFrameworkControlMappings(frameworkName);

        // Use AWS-managed frameworks since Conformance Packs handle the Config rule deployment
        // Conformance Packs provide standardized, AWS-maintained rule sets that Audit Manager can reference
        String frameworkId = resolveFrameworkIdentifier(frameworkName);
        if (frameworkId == null) {
            LOG.info("  Skipping Audit Manager assessment for custom framework: " + frameworkName);
            return false;  // Skip this assessment
        }
        LOG.info("  Using AWS-managed framework: " + frameworkId);
        LOG.info("  Evidence will be collected from Conformance Pack rules automatically");

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
        return true;
    }


    /**
     * Creates a custom Audit Manager framework with CloudForge-specific controls.
     * Uses AWS Custom Resource to create the framework via AWS SDK.
     *
     * @param frameworkName Compliance framework name (SOC2, PCI-DSS, HIPAA, GDPR)
     * @param controlSets List of control sets for the framework
     * @param shortId Short identifier for uniqueness
     * @return Framework ARN that can be used in CfnAssessment
     */
    private String createCustomFramework(String frameworkName, List<Object> controlSets, String shortId) {
        String customFrameworkName = "CloudForge-" + frameworkName + "-" + security.name() + "-" + shortId;
        String constructId = "CustomFramework" + frameworkName.replace("-", "").replace("_", "");

        LOG.info("Creating custom Audit Manager framework: " + customFrameworkName);

        // Convert control sets to the proper format for AWS SDK
        List<Map<String, Object>> formattedControlSets = new ArrayList<>();
        for (Object controlSetObj : controlSets) {
            @SuppressWarnings("unchecked")
            Map<String, Object> controlSet = (Map<String, Object>) controlSetObj;
            formattedControlSets.add(controlSet);
        }

        // Build parameters for createAssessmentFramework API call
        Map<String, Object> params = new HashMap<>();
        params.put("name", customFrameworkName);
        params.put("description", "CloudForge custom compliance framework for " + frameworkName +
                                  " with Config rule-based controls (Environment: " + security.name() + ")");
        params.put("complianceType", frameworkName);
        params.put("controlSets", formattedControlSets);
        params.put("tags", Map.of(
            "ManagedBy", "CloudForge",
            "Environment", security.name().toLowerCase(),
            "Framework", frameworkName
        ));

        // Build resource ARN patterns for this region and account
        // AWS Audit Manager creates frameworks, controls, and control sets that need tagging permissions
        String accountId = software.amazon.awscdk.Stack.of(this).getAccount();
        String awsRegion = software.amazon.awscdk.Stack.of(this).getRegion();
        String arnBase = String.format("arn:aws:auditmanager:%s:%s", awsRegion, accountId);

        List<String> auditManagerResources = List.of(
            arnBase + ":assessmentFramework/*",  // Custom frameworks
            arnBase + ":control/*",               // Controls created within frameworks
            arnBase + ":controlSet/*"             // Control sets within frameworks
        );

        // Ensure Audit Manager is enabled before creating frameworks
        // RegisterAccount is idempotent - safe to call even if already registered
        AwsCustomResource registerAuditManager = AwsCustomResource.Builder.create(this, "RegisterAuditManager")
                .onCreate(AwsSdkCall.builder()
                        .service("AuditManager")
                        .action("registerAccount")
                        .parameters(Map.of(
                            "kmsKey", "",  // Use AWS managed key
                            "delegatedAdminAccount", ""  // No delegated admin
                        ))
                        .physicalResourceId(PhysicalResourceId.of("audit-manager-registration"))
                        .build())
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("auditmanager:RegisterAccount", "auditmanager:GetAccountStatus"))
                                .resources(List.of("*"))
                                .build()
                )))
                .build();

        // Create custom framework using AWS SDK via Custom Resource
        // Physical resource ID will be framework.id from the create response
        // IMPORTANT: We must add explicit onDelete to clean up the framework
        AwsCustomResource customFramework = AwsCustomResource.Builder.create(this, constructId)
                .onCreate(AwsSdkCall.builder()
                        .service("AuditManager")
                        .action("createAssessmentFramework")
                        .parameters(params)
                        .physicalResourceId(PhysicalResourceId.fromResponse("framework.id"))
                        .build())
                .onDelete(AwsSdkCall.builder()
                        .service("AuditManager")
                        .action("deleteAssessmentFramework")
                        // Use PhysicalResourceIdReference to pass the framework ID from onCreate to onDelete
                        // This is the correct CDK pattern for referencing the physical resource ID
                        .parameters(java.util.Collections.singletonMap("frameworkId", new PhysicalResourceIdReference()))
                        .build())
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                        "auditmanager:CreateAssessmentFramework",
                                        "auditmanager:DeleteAssessmentFramework",
                                        "auditmanager:TagResource"
                                ))
                                // createAssessmentFramework requires wildcard resource per AWS docs
                                // https://docs.aws.amazon.com/service-authorization/latest/reference/list_awsauditmanager.html
                                .resources(List.of("*"))
                                .build()
                )))
                .build();

        // Ensure Audit Manager is registered before creating frameworks
        customFramework.getNode().addDependency(registerAuditManager);

        // Return the framework ID from the custom resource response
        return customFramework.getResponseField("framework.id");
    }

    /**
     * Builds custom control sets for a compliance framework from AuditManagerControlRegistry.
     * Groups controls into control sets by category (Encryption, Access Control, Monitoring, etc.).
     *
     * @param frameworkName Compliance framework (SOC2, PCI-DSS, HIPAA, GDPR)
     * @return List of control set objects for CfnAssessment
     */
    private List<Object> buildControlSetsForFramework(String frameworkName) {
        List<Object> controlSets = new ArrayList<>();

        // Get all controls that apply to this framework
        List<AuditManagerControl> controls = AuditManagerControlRegistry.getControlsForFramework(frameworkName);

        if (controls.isEmpty()) {
            LOG.warning("No controls found for framework: " + frameworkName);
            return controlSets;
        }

        // Group controls by category for better organization
        Map<String, List<AuditManagerControl>> controlsByCategory = new java.util.LinkedHashMap<>();
        controlsByCategory.put("Data Protection", new ArrayList<>());
        controlsByCategory.put("Access Control & Authentication", new ArrayList<>());
        controlsByCategory.put("Security Monitoring & Logging", new ArrayList<>());
        controlsByCategory.put("Network Security", new ArrayList<>());
        controlsByCategory.put("Backup & Recovery", new ArrayList<>());
        controlsByCategory.put("Vulnerability Management", new ArrayList<>());
        controlsByCategory.put("Data Privacy & Governance", new ArrayList<>());

        // Categorize controls
        for (AuditManagerControl control : controls) {
            String controlId = control.controlId();
            if (controlId.contains("ENCRYPTION")) {
                controlsByCategory.get("Data Protection").add(control);
            } else if (controlId.contains("ACCESS") || controlId.contains("AUTHENTICATION") || controlId.contains("IAM")) {
                controlsByCategory.get("Access Control & Authentication").add(control);
            } else if (controlId.contains("LOGGING") || controlId.contains("MONITORING") || controlId.contains("THREAT") || controlId.contains("GUARDDUTY") || controlId.contains("SECURITYHUB")) {
                controlsByCategory.get("Security Monitoring & Logging").add(control);
            } else if (controlId.contains("NETWORK") || controlId.contains("WAF") || controlId.contains("VPC")) {
                controlsByCategory.get("Network Security").add(control);
            } else if (controlId.contains("BACKUP") || controlId.contains("RECOVERY")) {
                controlsByCategory.get("Backup & Recovery").add(control);
            } else if (controlId.contains("PATCH") || controlId.contains("VULNERABILITY") || controlId.contains("SCANNING")) {
                controlsByCategory.get("Vulnerability Management").add(control);
            } else if (controlId.contains("PRIVACY") || controlId.contains("GDPR") || controlId.contains("DATA_RETENTION")) {
                controlsByCategory.get("Data Privacy & Governance").add(control);
            } else {
                // Default category
                controlsByCategory.get("Data Protection").add(control);
            }
        }

        // Build control sets for each category that has controls
        for (Map.Entry<String, List<AuditManagerControl>> entry : controlsByCategory.entrySet()) {
            String category = entry.getKey();
            List<AuditManagerControl> categoryControls = entry.getValue();

            if (categoryControls.isEmpty()) {
                continue; // Skip empty categories
            }

            // Build controls list for this control set
            List<Object> controlsList = new ArrayList<>();
            for (AuditManagerControl control : categoryControls) {
                control.getFrameworkControl(frameworkName).ifPresent(fc -> {
                    controlsList.add(buildControlProperty(control, fc, frameworkName));
                });
            }

            if (!controlsList.isEmpty()) {
                // Create control set property
                Map<String, Object> controlSetMap = new java.util.LinkedHashMap<>();
                controlSetMap.put("id", java.util.UUID.randomUUID().toString());
                controlSetMap.put("name", category);
                controlSetMap.put("controls", controlsList);

                controlSets.add(controlSetMap);
            }
        }

        return controlSets;
    }

    /**
     * Builds a CfnAssessment control property from AuditManagerControl.
     * Maps Config rules to control data sources for automated evidence collection.
     *
     * @param control AuditManagerControl with Config rule mappings
     * @param frameworkControl Framework-specific control details
     * @param frameworkName Framework name for data source naming
     * @return Control property map for CfnAssessment
     */
    private Map<String, Object> buildControlProperty(
        AuditManagerControl control,
        AuditManagerControl.FrameworkControl frameworkControl,
        String frameworkName
    ) {
        Map<String, Object> controlMap = new java.util.LinkedHashMap<>();

        // Control identification
        controlMap.put("id", java.util.UUID.randomUUID().toString());
        controlMap.put("name", frameworkControl.controlName());
        controlMap.put("description", control.description() + " (Control: " + frameworkControl.controlId() + ")");

        // Build data sources from Config rules and evidence sources
        List<Object> dataSources = new ArrayList<>();

        // Add Config rule data sources with proper AWS Audit Manager API structure
        for (String configRuleId : control.configRuleIds()) {
            Map<String, Object> dataSource = new java.util.LinkedHashMap<>();
            dataSource.put("sourceName", configRuleId);
            dataSource.put("sourceDescription", "AWS Config rule monitoring " + control.description());
            dataSource.put("sourceType", "AWS_Config");
            dataSource.put("sourceSetUpOption", "System_Controls_Mapping");  // Automated evidence collection
            dataSource.put("sourceFrequency", "DAILY");

            // CRITICAL: Add sourceKeyword for Config rule mapping (case-sensitive!)
            Map<String, Object> sourceKeyword = new java.util.LinkedHashMap<>();
            sourceKeyword.put("keywordInputType", "SELECT_FROM_LIST");
            sourceKeyword.put("keywordValue", configRuleId);  // Must match Config rule name exactly
            dataSource.put("sourceKeyword", sourceKeyword);

            dataSources.add(dataSource);
        }

        // Add evidence source data sources (CloudTrail, VPC Flow Logs, etc.)
        for (String evidenceSource : control.evidenceSources()) {
            // Skip "config" since we already added Config rules above
            if ("config".equals(evidenceSource.toLowerCase())) {
                continue;
            }

            Map<String, Object> dataSource = new java.util.LinkedHashMap<>();

            // Map evidence source to AWS service with proper API structure
            switch (evidenceSource.toLowerCase()) {
                case "cloudtrail":
                    dataSource.put("sourceName", "CloudTrail-" + control.controlId());
                    dataSource.put("sourceDescription", "CloudTrail API activity logs");
                    dataSource.put("sourceType", "AWS_Cloudtrail");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "securityhub":
                    dataSource.put("sourceName", "SecurityHub-" + control.controlId());
                    dataSource.put("sourceDescription", "Security Hub findings");
                    dataSource.put("sourceType", "AWS_Security_Hub");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "guardduty":
                    dataSource.put("sourceName", "GuardDuty-" + control.controlId());
                    dataSource.put("sourceDescription", "GuardDuty threat detections");
                    dataSource.put("sourceType", "AWS_API_Call");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "vpc-flowlogs":
                    dataSource.put("sourceName", "VPCFlowLogs-" + control.controlId());
                    dataSource.put("sourceDescription", "VPC network traffic logs");
                    dataSource.put("sourceType", "AWS_Cloudtrail");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "cloudwatch":
                case "cloudwatch-logs":
                    dataSource.put("sourceName", "CloudWatch-" + control.controlId());
                    dataSource.put("sourceDescription", "CloudWatch metrics and logs");
                    dataSource.put("sourceType", "AWS_API_Call");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "s3":
                    dataSource.put("sourceName", "S3-" + control.controlId());
                    dataSource.put("sourceDescription", "S3 access logs");
                    dataSource.put("sourceType", "AWS_Cloudtrail");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "iam":
                    dataSource.put("sourceName", "IAM-" + control.controlId());
                    dataSource.put("sourceDescription", "IAM credential reports");
                    dataSource.put("sourceType", "AWS_API_Call");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                case "waf":
                    dataSource.put("sourceName", "WAF-" + control.controlId());
                    dataSource.put("sourceDescription", "WAF web ACL logs");
                    dataSource.put("sourceType", "AWS_Cloudtrail");
                    dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
                default:
                    // Generic evidence source - manual collection
                    dataSource.put("sourceName", evidenceSource + "-" + control.controlId());
                    dataSource.put("sourceDescription", "Manual evidence collection for " + evidenceSource);
                    dataSource.put("sourceType", "MANUAL");
                    dataSource.put("sourceSetUpOption", "Procedural_Controls_Mapping");
                    dataSource.put("sourceFrequency", "DAILY");
                    break;
            }

            dataSources.add(dataSource);
        }

        // Deduplicate data sources by type to avoid duplicates
        Map<String, Object> uniqueDataSources = new java.util.LinkedHashMap<>();
        for (Object ds : dataSources) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dsMap = (Map<String, Object>) ds;
            String key = dsMap.get("sourceType") + ":" + dsMap.get("sourceName");
            uniqueDataSources.putIfAbsent(key, ds);
        }

        controlMap.put("controlMappingSources", new ArrayList<>(uniqueDataSources.values()));

        return controlMap;
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

        // If query failed (returned placeholder), skip this framework with a warning
        if ("00000000-0000-0000-0000-000000000000".equals(result)) {
            LOG.warning("Unable to resolve Audit Manager framework: " + identifier);
            LOG.warning("  Framework '" + identifier + "' is not available as an AWS Audit Manager standard framework");
            LOG.warning("  This may be a custom framework that requires CloudForge-specific Config rules");
            LOG.warning("  Skipping Audit Manager assessment creation for this framework");
            LOG.warning("  Note: Other compliance layers (cdk-nag, FrameworkRules, cfn-guard, AWS Config) will still validate this framework");
            return null;  // Return null to signal "skip this framework"
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
                LOG.info("Using AWS_PROFILE = " + awsProfile + " for framework query");
            }
            // Preserve AWS_REGION if set
            String awsRegion = System.getenv("AWS_REGION");
            if (awsRegion != null && !awsRegion.isEmpty()) {
                env.put("AWS_REGION", awsRegion);
                LOG.info("Using AWS_REGION = " + awsRegion + " for framework query");
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
        // Only PRODUCTION buckets are retained for audit purposes.
        // DEV and STAGING buckets can be auto-deleted with their contents.
        boolean shouldRetain = security == SecurityProfile.PRODUCTION;
        boolean shouldAutoDelete = !shouldRetain;

        // Enable KMS encryption for compliance buckets when:
        // 1. HIPAA or PCI-DSS compliance in PRODUCTION (mandatory)
        // 2. cloudWatchLogsKmsEncryptionEnabled is explicitly set to true (optional hardening)
        boolean isHipaa = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("HIPAA");
        boolean isPciDss = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean useKms = (security == SecurityProfile.PRODUCTION && (isHipaa || isPciDss))
                || config.isCloudWatchLogsKmsEncryptionEnabled();

        // Enable Object Lock for compliance audit buckets when required by compliance matrix
        // HIPAA § 164.312(c)(1) - Data integrity controls
        // PCI-DSS Req 10.7 - Protect audit trail files
        boolean enableObjectLock = config.isS3ObjectLockEnabled();

        LOG.info("Bucket " + id + " encryption decision:");
        LOG.info("  complianceFrameworks = " + complianceFrameworks);
        LOG.info("  security = " + security);
        LOG.info("  isHipaa = " + isHipaa + ", isPciDss = " + isPciDss);
        LOG.info("  cloudWatchLogsKmsEncryptionEnabled = " + config.isCloudWatchLogsKmsEncryptionEnabled());
        LOG.info("  useKms = " + useKms);
        LOG.info("  enableObjectLock = " + enableObjectLock);

        Bucket.Builder bucketBuilder = Bucket.Builder.create(this, id)
                // NO bucketName specified - CloudFormation auto-generates unique name
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(shouldRetain ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                // Enable autoDeleteObjects for non-production so CloudFormation can delete bucket contents
                // Note: autoDeleteObjects is incompatible with Object Lock, so disable it when Object Lock is enabled
                .autoDeleteObjects(shouldAutoDelete && !enableObjectLock)
                .versioned(true)  // Required for compliance (SOC2/PCI-DSS/HIPAA)
                .objectLockEnabled(enableObjectLock)  // Immutability for audit trails when required
                .lifecycleRules(lifecycleRules);  // Compliance-driven retention policies

        // Apply KMS encryption for HIPAA/PCI-DSS compliance in PRODUCTION
        if (useKms) {
            Key kmsKey = Key.Builder.create(this, id + "KmsKey")
                    .description("KMS key for " + id + " (HIPAA/PCI-DSS compliance)")
                    .enableKeyRotation(true)
                    .removalPolicy(shouldRetain ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                    .build();
            bucketBuilder.encryptionKey(kmsKey).encryption(BucketEncryption.KMS);
        } else {
            bucketBuilder.encryption(BucketEncryption.S3_MANAGED);
        }

        Bucket bucket = bucketBuilder.build();

        // Register AWS Config rules for S3 bucket compliance
        ctx.requireConfigRule(AwsConfigRule.S3_BUCKET_ENCRYPTION);
        ctx.requireConfigRule(AwsConfigRule.S3_BUCKET_SSL_REQUESTS);
        ctx.requireConfigRule(AwsConfigRule.S3_BUCKET_VERSIONING_ENABLED);

        // Enforce SSL for all S3 requests (HIPAA requirement)
        bucket.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("DenyInsecureTransport")
                .effect(Effect.DENY)
                .principals(List.of(new AnyPrincipal()))
                .actions(List.of("s3:*"))
                .resources(List.of(
                        bucket.getBucketArn(),
                        bucket.getBucketArn() + "/*"
                ))
                .conditions(java.util.Map.of(
                        "Bool", java.util.Map.of("aws:SecureTransport", "false")
                ))
                .build());

        // Add NagSuppressions for CDK limitations and justified architecture decisions
        // Apply to the underlying CfnBucket resource (L1) where cdk-nag checks run
        List<NagPackSuppression> suppressions = new ArrayList<>();
        suppressions.add(NagPackSuppression.builder()
                .id("PCI.DSS.321-S3BucketReplicationEnabled")
                .reason("S3 replication is not required for single-region deployments. " +
                       "Compliance data is retained in the same region with versioning enabled.")
                .build());
        suppressions.add(NagPackSuppression.builder()
                .id("PCI.DSS.321-S3BucketLoggingEnabled")
                .reason("CloudTrail S3 data events provide equivalent audit logging for bucket access. " +
                       "Server access logging would create circular dependency for compliance buckets.")
                .build());
        suppressions.add(NagPackSuppression.builder()
                .id("HIPAA.Security-S3BucketReplicationEnabled")
                .reason("S3 replication is not required for single-region deployments. " +
                       "Compliance data is retained in the same region with versioning enabled.")
                .build());
        suppressions.add(NagPackSuppression.builder()
                .id("PCI.DSS.321-S3DefaultEncryptionKMS")
                .reason("Bucket uses encryption with SSL enforcement. " +
                       "KMS encryption is applied for HIPAA/PCI-DSS PRODUCTION deployments.")
                .build());
        suppressions.add(NagPackSuppression.builder()
                .id("HIPAA.Security-S3BucketLoggingEnabled")
                .reason("CloudTrail S3 data events provide equivalent audit logging for bucket access. " +
                       "Server access logging would create circular dependency for compliance buckets.")
                .build());

        NagSuppressions.addResourceSuppressions(bucket, suppressions, Boolean.TRUE);

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

        // Apply NagSuppressions for CDK custom resource limitations
        applyCustomResourceNagSuppressions(ssmWriter);

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

        // Apply NagSuppressions for CDK custom resource limitations
        applyCustomResourceNagSuppressions(ssmWriter);

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

        // Apply NagSuppressions for CDK custom resource limitations
        applyCustomResourceNagSuppressions(ssmWriter);

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

    /**
     * Creates auto-remediation for Security Hub enablement.
     * Automatically enables AWS Security Hub if not already enabled.
     *
     * @param securityHubRule The Config rule to attach remediation to
     */
    private void createSecurityHubRemediation(CfnConfigRule securityHubRule) {
        // Create SSM Automation document for Security Hub enablement
        CfnDocument securityHubDocument = CfnDocument.Builder.create(this, "SecurityHubEnablementDocument")
                .documentType("Automation")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Enables AWS Security Hub if not already enabled",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for SSM Automation"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "EnableSecurityHub",
                            "action", "aws:executeAwsApi",
                            "onFailure", "Continue",
                            "inputs", Map.of(
                                "Service", "securityhub",
                                "Api", "EnableSecurityHub"
                            )
                        )
                    )
                ))
                .build();

        // Create IAM role for SSM Automation
        Role ssmRole = Role.Builder.create(this, "SecurityHubRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .inlinePolicies(Map.of(
                    "SecurityHubPermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "securityhub:EnableSecurityHub",
                                    "securityhub:GetEnabledStandards"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create remediation configuration
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "SecurityHubRemediation")
                .configRuleName(securityHubRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(securityHubDocument.getRef())
                .targetVersion("1")
                .automatic(true)
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(60)
                .build();

        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmRole.getRoleArn())))
        ));

        LOG.info("Security Hub automatic remediation enabled");
    }

    /**
     * Creates auto-remediation for Inspector enablement.
     * Automatically enables Amazon Inspector v2 if not already enabled.
     *
     * @param inspectorRule The Config rule to attach remediation to
     */
    private void createInspectorRemediation(CfnConfigRule inspectorRule) {
        // Create SSM Automation document for Inspector enablement
        CfnDocument inspectorDocument = CfnDocument.Builder.create(this, "InspectorEnablementDocument")
                .documentType("Automation")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Enables Amazon Inspector v2 if not already enabled",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for SSM Automation"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "EnableInspector",
                            "action", "aws:executeAwsApi",
                            "onFailure", "Continue",
                            "inputs", Map.of(
                                "Service", "inspector2",
                                "Api", "Enable",
                                "resourceTypes", List.of("EC2", "ECR", "LAMBDA")
                            )
                        )
                    )
                ))
                .build();

        // Create IAM role for SSM Automation
        Role ssmRole = Role.Builder.create(this, "InspectorRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .inlinePolicies(Map.of(
                    "InspectorPermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "inspector2:Enable",
                                    "inspector2:GetConfiguration"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create remediation configuration
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "InspectorRemediation")
                .configRuleName(inspectorRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(inspectorDocument.getRef())
                .targetVersion("1")
                .automatic(true)
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(60)
                .build();

        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmRole.getRoleArn())))
        ));

        LOG.info("Inspector automatic remediation enabled");
    }

    /**
     * Creates auto-remediation for Macie enablement.
     * Automatically enables Amazon Macie if not already enabled.
     *
     * @param macieRule The Config rule to attach remediation to
     */
    private void createMacieRemediation(CfnConfigRule macieRule) {
        // Create SSM Automation document for Macie enablement
        CfnDocument macieDocument = CfnDocument.Builder.create(this, "MacieEnablementDocument")
                .documentType("Automation")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Enables Amazon Macie if not already enabled",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for SSM Automation"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "EnableMacie",
                            "action", "aws:executeAwsApi",
                            "onFailure", "Continue",
                            "inputs", Map.of(
                                "Service", "macie2",
                                "Api", "EnableMacie"
                            )
                        )
                    )
                ))
                .build();

        // Create IAM role for SSM Automation
        Role ssmRole = Role.Builder.create(this, "MacieRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .inlinePolicies(Map.of(
                    "MaciePermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "macie2:EnableMacie",
                                    "macie2:GetMacieSession"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create remediation configuration
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "MacieRemediation")
                .configRuleName(macieRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(macieDocument.getRef())
                .targetVersion("1")
                .automatic(true)
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(60)
                .build();

        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmRole.getRoleArn())))
        ));

        LOG.info("Macie automatic remediation enabled");
    }

    /**
     * Creates auto-remediation for GuardDuty enablement.
     * Automatically enables AWS GuardDuty if not already enabled.
     *
     * @param guardDutyRule The Config rule to attach remediation to
     */
    private void createGuardDutyRemediation(CfnConfigRule guardDutyRule) {
        // Create SSM Automation document for GuardDuty enablement
        CfnDocument guardDutyDocument = CfnDocument.Builder.create(this, "GuardDutyEnablementDocument")
                .documentType("Automation")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", "Enables AWS GuardDuty if not already enabled",
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for SSM Automation"
                        )
                    ),
                    "mainSteps", List.of(
                        Map.of(
                            "name", "EnableGuardDuty",
                            "action", "aws:executeAwsApi",
                            "onFailure", "Continue",
                            "inputs", Map.of(
                                "Service", "guardduty",
                                "Api", "CreateDetector",
                                "Enable", true,
                                "FindingPublishingFrequency", "FIFTEEN_MINUTES"
                            )
                        )
                    )
                ))
                .build();

        // Create IAM role for SSM Automation
        Role ssmRole = Role.Builder.create(this, "GuardDutyRemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .inlinePolicies(Map.of(
                    "GuardDutyPermissions",
                    software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of(
                                    "guardduty:CreateDetector",
                                    "guardduty:ListDetectors"
                                ))
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        // Create remediation configuration
        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, "GuardDutyRemediation")
                .configRuleName(guardDutyRule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(guardDutyDocument.getRef())
                .targetVersion("1")
                .automatic(true)
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(60)
                .build();

        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmRole.getRoleArn())))
        ));

        LOG.info("GuardDuty automatic remediation enabled");
    }

    /**
     * Apply NagSuppressions for CDK custom resource limitations.
     *
     * <p>CDK AwsCustomResource creates Lambda functions that:</p>
     * <ul>
     *   <li>Use inline policies (IAMNoInlinePolicy) - CDK framework limitation</li>
     *   <li>Run outside VPC (LambdaInsideVPC) - No VPC access needed for AWS API calls</li>
     *   <li>Use wildcard resources (IAM5) - Required for SSM parameter operations</li>
     * </ul>
     *
     * @param customResource The AwsCustomResource to suppress warnings for
     */
    private void applyCustomResourceNagSuppressions(AwsCustomResource customResource) {
        NagSuppressions.addResourceSuppressions(
            customResource,
            List.of(
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-IAMNoInlinePolicy")
                    .reason("CDK AwsCustomResource creates inline policies by design. " +
                           "These are auto-generated Lambda execution policies for AWS SDK calls.")
                    .build(),
                NagPackSuppression.builder()
                    .id("PCI.DSS.321-LambdaInsideVPC")
                    .reason("CDK custom resource Lambdas only make AWS API calls (SSM, etc.) " +
                           "and do not require VPC access. Running in VPC would add unnecessary complexity.")
                    .build(),
                NagPackSuppression.builder()
                    .id("HIPAA.Security-IAMNoInlinePolicy")
                    .reason("CDK AwsCustomResource creates inline policies by design. " +
                           "These are auto-generated Lambda execution policies for AWS SDK calls.")
                    .build(),
                NagPackSuppression.builder()
                    .id("HIPAA.Security-LambdaInsideVPC")
                    .reason("CDK custom resource Lambdas only make AWS API calls (SSM, etc.) " +
                           "and do not require VPC access. Running in VPC would add unnecessary complexity.")
                    .build(),
                NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("SSM parameter operations require wildcard resource patterns " +
                           "for parameter paths. This is a CDK custom resource limitation.")
                    .build(),
                NagPackSuppression.builder()
                    .id("AwsSolutions-L1")
                    .reason("CDK AwsCustomResource manages its own Lambda runtime version. " +
                           "Runtime is determined by the CDK framework version and updated via CDK upgrades.")
                    .build()
            ),
            Boolean.TRUE
        );
    }

}
