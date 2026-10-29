package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.util.NagSuppress;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforgeci.api.core.rules.AuditManagerControl;
import com.cloudforgeci.api.core.rules.AuditManagerControlRegistry;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.customresource.AssetFreeCustomResource;
import software.amazon.awscdk.Aws;
import software.amazon.awscdk.CfnCondition;
import software.amazon.awscdk.CustomResource;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.auditmanager.CfnAssessment;
import software.amazon.awscdk.services.cloudtrail.Trail;
import software.amazon.awscdk.services.cloudtrail.CfnTrail;
import software.amazon.awscdk.services.config.*;
import software.amazon.awscdk.services.iam.CfnRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.AnyPrincipal;
import software.amazon.awscdk.services.iam.PolicyDocument;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
 *   <li>Update framework IDs to match your account</li>
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

    // Note: ECR image scanning remediation removed - not implemented
    // If needed in future, add: @DeploymentContext("enableEcrImageScanningRemediation")

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
        // By now BaseFactory's own constructor (the super() call above) has already run
        // ContextInjector.inject, so `region` already holds whatever the deployment context
        // supplied -- this only fills the gap when that left it unset, so an ARN or SSM
        // parameter path built from `region` anywhere in this class never embeds the literal
        // string "null" (or, worse, crashes a Map.of(..., "Region", region) call with an NPE).
        if (region == null || region.isEmpty()) {
            region = Stack.of(this).getRegion();
        }
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
        // createConfigInfrastructure controls ONLY infrastructure, NOT rules, and applies only
        // when awsConfigEnabled is true (matching the field's visibleWhen). This flag is the sole
        // source of truth for whether this stack creates that infrastructure -- deliberately not
        // second-guessed by a synth-time existence check. A local AWS CLI probe here previously
        // tried to auto-detect an existing Recorder and silently downgrade this flag, but that
        // made synthesis depend on whatever real AWS account state (and locally-installed AWS
        // CLI) happened to be present on whoever ran cdk synth -- non-deterministic, and it
        // reached out to a live account during what should be a pure, offline synthesis step.
        // If a deployer genuinely sets this true when infrastructure already exists, CloudFormation
        // itself now surfaces that as a real ResourceAlreadyExistsException at deploy time -- a
        // louder, more honest failure than a heuristic silently working around it.
        boolean shouldCreateInfra = Boolean.TRUE.equals(awsConfigEnabled)
            && Boolean.TRUE.equals(createConfigInfrastructure);

        ConfigInfrastructure configInfra = null;
        if (shouldCreateInfra) {
            LOG.info("Creating Config Recorder and Delivery Channel (account-level singletons)");
            LOG.info("  IMPORTANT: Only ONE stack per region should have createConfigInfrastructure = true");
            configInfra = createConfigInfrastructure();
        } else {
            LOG.info("Skipping Config infrastructure creation (createConfigInfrastructure = false)");
            LOG.info("  Config Recorder 'cloudforge-config-recorder' will be referenced by name if needed");
        }

        // STEP 2: Deploy Conformance Packs (AWS managed rule bundles for compliance frameworks)
        // Conformance Packs are the foundation - they deploy standardized Config rules
        // awsConfigEnabled has no PRODUCTION/STAGING auto-default (unlike GuardDuty/Macie/
        // SecurityHub/Inspector): Config's cost scales with rule count -- 89 rules for a
        // four-framework stack -- rather than being a flat per-service toggle, so it always
        // requires an explicit true.
        boolean configRulesEnabled = Boolean.TRUE.equals(awsConfigEnabled);

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
                createAllFrameworkConfigRules(configInfra.recorder);
            } else {
                // Infrastructure already exists or wasn't created - rules still work, just no CF dependency
                LOG.info("  Config Recorder referenced by name 'cloudforge-config-recorder'");
                LOG.info("  Rules will be created without CloudFormation dependencies");
                createConfigRulesWithoutRecorder();
                createAllFrameworkConfigRules(null);
            }
        } else {
            LOG.info("AWS Config rules and remediation disabled for profile: " + security);
            LOG.info("  awsConfigEnabled = " + awsConfigEnabled + " (controls rules and remediation)");
            LOG.info("  createConfigInfrastructure = " + createConfigInfrastructure + " (controls recorder and channel)");
        }

        LOG.info("Compliance resources created successfully for profile: " + security);

        // STEP 4: Deploy Config rules collected from factories
        // This deploys only the rules that match actually-provisioned infrastructure
        if (configRulesEnabled) {
            deployCollectedConfigRules();
        } else {
            LOG.info("Skipping collected Config rules deployment (configRulesEnabled = false)");
        }
    }

    // AwsConfigRule.getRuleName() is the AWS Config rule page slug (e.g. "restricted-ssh"), which
    // usually -- but not always -- becomes the real managed-rule SourceIdentifier by uppercasing
    // and replacing hyphens with underscores. These four don't follow that pattern; the real
    // identifiers come from CDK's own ManagedRuleIdentifiers constants, not a guess.
    private static final Map<AwsConfigRule, String> SOURCE_IDENTIFIER_OVERRIDES = Map.of(
        AwsConfigRule.CLOUDTRAIL_ENABLED, ManagedRuleIdentifiers.CLOUD_TRAIL_ENABLED,
        AwsConfigRule.MULTI_REGION_CLOUDTRAIL, ManagedRuleIdentifiers.CLOUDTRAIL_MULTI_REGION_ENABLED,
        AwsConfigRule.EC2_INSTANCES_IN_VPC, ManagedRuleIdentifiers.EC2_INSTANCES_IN_VPC,
        AwsConfigRule.RESTRICTED_SSH, ManagedRuleIdentifiers.EC2_SECURITY_GROUPS_INCOMING_SSH_DISABLED
    );

    static String sourceIdentifierFor(AwsConfigRule rule) {
        return SOURCE_IDENTIFIER_OVERRIDES.getOrDefault(
            rule, rule.getRuleName().toUpperCase(Locale.ROOT).replace("-", "_"));
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
                        .sourceIdentifier(sourceIdentifierFor(rule))
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
                        .sourceIdentifier(sourceIdentifierFor(rule))
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

        // Enable KMS encryption for CloudTrail when the security profile resolves
        // cloudWatchLogsKmsEncryptionEnabled to true: on in PRODUCTION, required by a framework
        // in ENFORCE mode, or set explicitly.
        boolean isHipaa = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("HIPAA");
        boolean isPciDss = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean useKms = config.isCloudWatchLogsKmsEncryptionEnabled();

        // Create KMS-encrypted CloudWatch Log Group for CloudTrail (PCI-DSS Req 3.4)
        Key cloudTrailLogsKmsKey = null;
        LogGroup cloudTrailLogGroup = null;
        if (useKms) {
            cloudTrailLogsKmsKey = Key.Builder.create(this, "CloudTrailLogsKmsKey")
                    .description("KMS key for CloudTrail CloudWatch Logs (HIPAA/PCI-DSS compliance)")
                    .enableKeyRotation(true)
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .build();

            cloudTrailLogGroup = LogGroup.Builder.create(this, "CloudTrailLogGroup")
                    .logGroupName("/aws/cloudtrail/" + this.trailName)
                    .retention(config.getLogRetentionDays())
                    .encryptionKey(cloudTrailLogsKmsKey)
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .build();

            // CDK's LogGroup#encryptionKey does NOT grant the CloudWatch Logs service permission
            // to use the key — a customer-managed key defaults to an account-root-only policy, so
            // without this explicit grant CreateLogGroup fails with AccessDenied. (This path runs
            // for PRODUCTION + HIPAA/PCI-DSS, or when the optional hardening flag is set.)
            //
            // Resource is "*", not this key's own explicit ARN — spelling out the key's own ARN
            // here creates a CDK circular dependency: it's a self-reference (Ref to this same
            // key's own logical id) inside this key's OWN resource policy, which CDK's dependency
            // graph rejects as a self-loop. "*" in a resource-based policy on the key itself isn't
            // a wildcard grant — the policy is already scoped to this one key by being attached to
            // it — it's the standard AWS pattern specifically to avoid this self-reference, and
            // matches every other working KMS-log grant already in this codebase (the CloudTrail
            // Trail's own KMS key grant just above, LoggingCwFactory's).
            String region = software.amazon.awscdk.Stack.of(this).getRegion();
            cloudTrailLogsKmsKey.addToResourcePolicy(PolicyStatement.Builder.create()
                    .sid("Enable CloudWatch Logs encryption")
                    .effect(Effect.ALLOW)
                    .principals(List.of(new ServicePrincipal("logs." + region + ".amazonaws.com")))
                    .actions(List.of("kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*",
                        "kms:GenerateDataKey*", "kms:Describe*"))
                    .resources(List.of("*"))
                    .build());
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
            // Create the log group explicitly rather than via Trail's cloudWatchLogsRetention(...)
            // shortcut, which uses CDK's default RETAIN policy regardless of
            // config.getLogRemovalPolicy(). A retained log group with a fixed name blocks a later
            // redeploy of the same stackName. Same conditional-name pattern as
            // LoggingCwFactory/FlowLogFactory: explicit name only under DESTROY, CDK-generated
            // unique name under RETAIN.
            LogGroup.Builder plainCloudTrailLogGroupBuilder = LogGroup.Builder.create(this, "CloudTrailLogGroupPlain")
                    .retention(config.getLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy());
            if (config.getLogRemovalPolicy() == RemovalPolicy.DESTROY) {
                plainCloudTrailLogGroupBuilder.logGroupName("/aws/cloudtrail/" + this.trailName);
            }
            trailBuilder.cloudWatchLogGroup(plainCloudTrailLogGroupBuilder.build());
            trailBuilder.sendToCloudWatchLogs(true);
        }

        // Apply KMS encryption for HIPAA/PCI-DSS compliance in PRODUCTION
        if (useKms) {
            Key cloudTrailKmsKey = Key.Builder.create(this, "CloudTrailKmsKey")
                    .description("KMS key for CloudTrail (HIPAA/PCI-DSS compliance)")
                    .enableKeyRotation(true)
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .build();

            // Grant CloudTrail service permission to use the key
            cloudTrailKmsKey.addToResourcePolicy(PolicyStatement.Builder.create()
                    .sid("Enable CloudTrail log encryption")
                    .effect(Effect.ALLOW)
                    .principals(List.of(new ServicePrincipal("cloudtrail.amazonaws.com")))
                    .actions(List.of("kms:GenerateDataKey*", "kms:Decrypt"))
                    .resources(List.of("*"))
                    .build());

            trailBuilder.encryptionKey(cloudTrailKmsKey);
        }

        Trail trail = trailBuilder.build();
        nameTrailLogsRole(trail);

        // Store trail for later configuration
        this.trail = trail;
        this.trailBucket = trailBucket;
        ctx.cloudTrail.set(trail);

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
        CustomResource cloudTrailSsmWriter = storeResourceArnInSSM("CloudTrailArn",
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
    private void createConfigRules(CfnConfigurationRecorder recorder, CustomResource starterResource) {
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
        final CustomResource starterResource;

        ConfigInfrastructure(CfnConfigurationRecorder recorder, CustomResource starterResource) {
            this.recorder = recorder;
            this.starterResource = starterResource;
        }
    }

    /**
     * Helper method to add dependencies to Config Rules.
     * Rules must depend on both the recorder AND the starter resource.
     *
     * @param rule The Config Rule to add dependencies to
     * @param recorder The Configuration Recorder, or null when it exists outside this stack
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void addConfigRuleDependencies(CfnConfigRule rule, CfnConfigurationRecorder recorder, CustomResource starterResource) {
        if (recorder == null) {
            return;
        }
        rule.addOverride("DeletionPolicy", "Delete");  // Ensure Config rules are deleted with stack
        rule.getNode().addDependency(recorder);
        if (starterResource != null) {
            rule.getNode().addDependency(starterResource);
        }
    }

    /** Makes a rule depend on the Configuration Recorder; a null recorder exists outside this stack. */
    private void dependOnRecorder(CfnConfigRule rule, CfnConfigurationRecorder recorder) {
        if (recorder != null) {
            rule.getNode().addDependency(recorder);
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
        CustomResource recorderSsmWriter = storeResourceArnInSSMAlways("ConfigRecorderArn",
                "/cloudforge/shared/" + this.region + "/config/recorder-arn",
                recorderArn,
                "AWS Config Recorder ARN for region " + this.region);
        recorderSsmWriter.getNode().addDependency(recorder);

        // Store Channel ARN (always, not just PRODUCTION)
        CustomResource channelSsmWriter = storeResourceArnInSSMAlways("ConfigDeliveryChannelArn",
                "/cloudforge/shared/" + this.region + "/config/channel-arn",
                channelArn,
                "AWS Config Delivery Channel ARN for region " + this.region);
        channelSsmWriter.getNode().addDependency(deliveryChannel);

        // Automatically start the Config Recorder for SOC2 and other compliance frameworks
        // This ensures compliance recording begins immediately upon deployment
        CustomResource starterResource = startConfigRecorder(recorder, recorderName);

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
     * @return The CustomResource that starts and verifies the recorder (for Config Rule
     *         dependencies) -- an {@link AssetFreeCustomResource}, not {@code AwsCustomResource}:
     *         AWS Config Recorder has no CloudFormation-native "start" operation (it's always
     *         created in STOPPED state by AWS's own design), so starting it genuinely requires
     *         an SDK call at deploy time either way; the choice is only which Lambda packaging
     *         makes that call. {@code AwsCustomResource}/Provider pulls in a CDK-bundled, asset-
     *         staged Lambda needing the deployer's CDK bootstrap bucket -- unavailable to an AWS
     *         Marketplace buyer launching this template directly in their own, unbootstrapped
     *         account (see {@code CloudForgeSynthesizer#synthesizeForMarketplace|}). This combines
     *         the old two-resource start-then-verify chain into one Lambda invocation.
     */
    private CustomResource startConfigRecorder(CfnConfigurationRecorder recorder, String recorderName) {
        LOG.info("Auto-starting Config Recorder for compliance frameworks");
        LOG.info("  Recorder: " + recorderName);
        LOG.info("  Reason: SOC2/HIPAA/PCI-DSS/GDPR require continuous compliance monitoring");

        // Validate region is available
        if (region == null || region.isEmpty() || region.contains("$")) {
            LOG.warning("Region not available - cannot auto-start Config Recorder");
            LOG.warning("  Manual start required: aws configservice start-configuration-recorder --configuration-recorder-name " + recorderName);
            return null;
        }

        AssetFreeCustomResource.Result starter = AssetFreeCustomResource.create(
                this, "StartConfigRecorder", START_AND_VERIFY_CONFIG_RECORDER_HANDLER_JS,
                Duration.seconds(30),
                List.of(PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        // Config Service requires a wildcard resource for start/describe operations.
                        .actions(List.of("config:StartConfigurationRecorder", "config:DescribeConfigurationRecorderStatus"))
                        .resources(List.of("*"))
                        .build()),
                java.util.Map.of(
                        "ConfigurationRecorderName", recorderName,
                        "Region", region
                ));

        // Ensure recorder is created before we try to start it
        starter.customResource.getNode().addDependency(recorder);

        LOG.info("Config Recorder auto-start configured successfully");
        LOG.info("  Recorder will start automatically during deployment");
        LOG.info("  Compliance recording will begin immediately");

        return starter.customResource;
    }

    /** Node.js handler for {@link AssetFreeCustomResource}: starts the given AWS Config recorder
     *  and confirms it reports {@code recording: true} before succeeding -- combining what used
     *  to be two separate custom resources (start, then verify) into one Lambda invocation. Does
     *  nothing on delete; the recorder itself is a RETAINed, account-level singleton. */
    private static final String START_AND_VERIFY_CONFIG_RECORDER_HANDLER_JS = """
        const { ConfigServiceClient, StartConfigurationRecorderCommand, DescribeConfigurationRecorderStatusCommand } = require('@aws-sdk/client-config-service');

        exports.handler = async (event) => {
          try {
            if (event.RequestType !== 'Delete') {
              const p = event.ResourceProperties;
              const endpoint = process.env.AWS_ENDPOINT_URL;
              const client = new ConfigServiceClient({ region: p.Region, ...(endpoint ? { endpoint } : {}) });
              await client.send(new StartConfigurationRecorderCommand({
                ConfigurationRecorderName: p.ConfigurationRecorderName
              }));
              const status = await client.send(new DescribeConfigurationRecorderStatusCommand({
                ConfigurationRecorderNames: [p.ConfigurationRecorderName]
              }));
              const recording = status.ConfigurationRecordersStatus
                && status.ConfigurationRecordersStatus[0]
                && status.ConfigurationRecordersStatus[0].recording;
              if (!recording) {
                throw new Error('Config Recorder did not report recording=true after start');
              }
            }
            await respond(event, 'SUCCESS');
          } catch (err) {
            console.error('Config Recorder start/verify failed: ' + err);
            try {
              await respond(event, 'FAILED', String(err));
            } catch (deliveryErr) {
              console.error('Could not deliver FAILED response to CloudFormation: ' + deliveryErr);
            }
          }
        };
        """;

    /**
     * Creates Config rules for encryption compliance.
     * All rules explicitly depend on the Configuration Recorder being started.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createEncryptionConfigRules(CfnConfigurationRecorder recorder, CustomResource starterResource) {
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
    private void createS3ConfigRules(CfnConfigurationRecorder recorder, CustomResource starterResource) {
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
    private void createIAMConfigRules(CfnConfigurationRecorder recorder, CustomResource starterResource) {
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

        CfnConfigRule adminAccessRule = CfnConfigRule.Builder.create(this, "IAMNoAdminAccessRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS)
                        .build())
                .build();
        addConfigRuleDependencies(adminAccessRule, recorder, starterResource);
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

        // Destroy remediation role with stack deletion
        ssmAutomationRole.applyRemovalPolicy(RemovalPolicy.DESTROY);

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
                // Guaranteed non-null/non-empty by the guard clauses at the top of this method.
                "StaticValue", Map.of("Values", List.of(this.trailName))
            ),
            "BucketName", Map.of(
                "StaticValue", Map.of("Values", List.of(this.trailBucket.getBucketName()))
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

    /** Names and settings that distinguish one RDS instance remediation from another. */
    private record RdsRemediation(String id, String label, String policyName, String documentName,
                                  String documentDescription, String stepName, String stepDescription,
                                  String attribute, boolean applyImmediately, String mode, String impact) {}

    /**
     * Creates an SSM Automation document that sets one boolean attribute on an RDS instance, the role
     * it runs as, and an automatic remediation that runs it for each non-compliant instance.
     */
    private void createRdsInstanceRemediation(CfnConfigRule rule, RdsRemediation spec) {
        LOG.info("Creating " + spec.label() + " automatic remediation");

        Role ssmAutomationRole = Role.Builder.create(this, spec.id() + "RemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .description("Role for automated " + spec.label() + " remediation")
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .inlinePolicies(Map.of(
                    spec.policyName(), PolicyDocument.Builder.create()
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

        CfnDocument document = CfnDocument.Builder.create(this, spec.id() + "Document")
                .name(this.stackName + "-" + spec.documentName())
                .documentType("Automation")
                .documentFormat("JSON")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", spec.documentDescription(),
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
                            "name", spec.stepName(),
                            "action", "aws:executeAwsApi",
                            "inputs", Map.of(
                                "Service", "rds",
                                "Api", "ModifyDBInstance",
                                "DBInstanceIdentifier", "{{ ResourceId }}",
                                spec.attribute(), true,
                                "ApplyImmediately", spec.applyImmediately()
                            ),
                            "description", spec.stepDescription()
                        )
                    )
                ))
                .build();

        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, spec.id() + "Remediation")
                .configRuleName(rule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(document.getRef())
                .targetVersion("1")
                .automatic(true)
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(120)
                .resourceType("AWS::RDS::DBInstance")
                .build();

        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of(
                "StaticValue", Map.of("Values", List.of(ssmAutomationRole.getRoleArn()))
            ),
            "ResourceId", Map.of(
                "ResourceValue", Map.of("Value", "RESOURCE_ID")
            )
        ));

        remediation.getNode().addDependency(rule);
        remediation.getNode().addDependency(document);

        LOG.info(spec.label() + " automatic remediation configured");
        LOG.info("  SSM Document: " + document.getRef());
        LOG.info("  Mode: Automatic (" + spec.mode() + ")");
        LOG.info("  Max attempts: 3, Retry interval: 120 seconds");
        LOG.info("  Impact: " + spec.impact());
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

        createRdsInstanceRemediation(rdsDeletionProtectionRule, new RdsRemediation(
            "RdsDeletionProtection", "RDS deletion protection", "RdsDeletionProtectionPolicy",
            "enable-rds-deletion-protection", "Enable deletion protection on RDS instances",
            "EnableDeletionProtection", "Enable deletion protection on RDS instance",
            "DeletionProtection", true,
            "enables deletion protection on non-compliant instances",
            "SAFE - only enables protection, does not require downtime"));
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

        createRdsInstanceRemediation(rdsAutoUpgradeRule, new RdsRemediation(
            "RdsAutoMinorVersionUpgrade", "RDS auto minor version upgrade", "RdsAutoUpgradePolicy",
            "enable-rds-auto-minor-version-upgrade", "Enable automatic minor version upgrades on RDS instances",
            "EnableAutoMinorVersionUpgrade",
            "Enable automatic minor version upgrades (applied during next maintenance window)",
            "AutoMinorVersionUpgrade", false,
            "enables auto-upgrade during maintenance window",
            "SAFE - only minor versions, applied during maintenance window"));
    }

    /**
     * Creates additional Config rules for production environments.
     * All rules explicitly depend on the Configuration Recorder.
     *
     * @param recorder The Configuration Recorder that rules depend on
     * @param starterResource The Custom Resource that starts the recorder
     */
    private void createProductionConfigRules(CfnConfigurationRecorder recorder, CustomResource starterResource) {
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
        createEncryptionConfigRules(null, null);
        createS3ConfigRulesWithoutRecorder();
        createIAMConfigRulesWithoutRecorder();

        if (security == SecurityProfile.PRODUCTION) {
            createProductionConfigRules(null, null);
        }

        LOG.info("AWS Config rules configured (without recorder dependency)");
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

        CfnConfigRule.Builder.create(this, "IAMNoAdminAccessRule")
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(ManagedRuleIdentifiers.IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS)
                        .build())
                .build();
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
    // starterResource dropped: unused by this method and the four create*ConfigRules methods
    // below (unlike createConfigRules, which does wire CloudFormation dependencies from it).
    private void createAllFrameworkConfigRules(CfnConfigurationRecorder recorder) {
        List<String> frameworks = determineFrameworks();

        if (frameworks.isEmpty()) {
            LOG.info("No compliance frameworks specified - all framework Config rules will be skipped via conditions");
        } else {
            LOG.info("Framework-specific Config rules enabled for: " + String.join(", ", frameworks));
        }

        // Always create ALL framework rules, using conditions to control deployment
        // This allows CloudFormation to track and DELETE rules when frameworks are removed
        LOG.info("Creating PCI-DSS Config rules (condition-controlled)");
        createPciDssConfigRules(recorder);

        LOG.info("Creating SOC 2 Config rules (condition-controlled)");
        createSoc2ConfigRules(recorder);

        LOG.info("Creating HIPAA Config rules (condition-controlled)");
        createHipaaConfigRules(recorder);

        LOG.info("Creating GDPR Config rules (condition-controlled)");
        createGdprConfigRules(recorder);

        LOG.info("All framework-specific Config rules created with conditions");
    }

    /**
     * Creates PCI-DSS specific AWS Config rules.
     * Implements controls for PCI-DSS Requirements 1-11.
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createPciDssConfigRules(CfnConfigurationRecorder recorder) {
        LOG.info("Creating PCI-DSS AWS Config rules (condition-controlled)");

        // Requirement 1: Network Segmentation
        CfnConfigRule vpcDefaultSecurityGroupClosed = managedConfigRule("PciDssVpcDefaultSecurityGroupClosed", this.stackName + "-pci-dss-vpc-default-sg-closed",
                "PCI-DSS Req 1.3: Prohibit direct public access between internet and cardholder data",
                "VPC_DEFAULT_SECURITY_GROUP_CLOSED", pciDssCondition);
        dependOnRecorder(vpcDefaultSecurityGroupClosed, recorder);

        // Requirement 2: Secure Configuration
        CfnConfigRule ec2InstanceManagedBySsm = managedConfigRule("PciDssEc2ManagedBySsm", this.stackName + "-pci-dss-ec2-managed-by-ssm",
                "PCI-DSS Req 2: Secure system configuration management",
                ManagedRuleIdentifiers.EC2_INSTANCE_MANAGED_BY_SSM, pciDssCondition);
        dependOnRecorder(ec2InstanceManagedBySsm, recorder);

        // Requirement 3: Protect stored cardholder data
        CfnConfigRule rdsEncryptionEnabled = managedConfigRule("PciDssRdsEncryption", this.stackName + "-pci-dss-rds-encryption-enabled",
                "PCI-DSS Req 3.4: Render cardholder data unreadable with encryption",
                "RDS_STORAGE_ENCRYPTED", pciDssRdsCondition);
        dependOnRecorder(rdsEncryptionEnabled, recorder);

        CfnConfigRule rdsPublicAccessCheck = managedConfigRule("PciDssRdsPublicAccess", this.stackName + "-pci-dss-rds-instance-public-access-check",
                "PCI-DSS Req 1.3.1: Prohibit direct public access between internet and cardholder data",
                "RDS_INSTANCE_PUBLIC_ACCESS_CHECK", pciDssRdsCondition);
        dependOnRecorder(rdsPublicAccessCheck, recorder);

        CfnConfigRule rdsBackupEnabled = managedConfigRule("PciDssRdsBackup", this.stackName + "-pci-dss-db-instance-backup-enabled",
                "PCI-DSS Req 3.4: Protect stored cardholder data with backups",
                "DB_INSTANCE_BACKUP_ENABLED", pciDssRdsCondition);
        dependOnRecorder(rdsBackupEnabled, recorder);

        CfnConfigRule rdsAutoUpgrade = managedConfigRule("PciDssRdsAutoUpgrade", this.stackName + "-pci-dss-rds-automatic-minor-version-upgrade",
                "PCI-DSS Req 6.2: Ensure all system components are protected with security patches",
                "RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED", pciDssRdsCondition);
        dependOnRecorder(rdsAutoUpgrade, recorder);
        createRdsAutoMinorVersionUpgradeRemediation(rdsAutoUpgrade);

        CfnConfigRule rdsLoggingEnabled = managedConfigRule("PciDssRdsLogging", this.stackName + "-pci-dss-rds-logging-enabled",
                "PCI-DSS Req 10.2: Implement automated audit trails for all system components",
                "RDS_LOGGING_ENABLED", pciDssRdsCondition);
        dependOnRecorder(rdsLoggingEnabled, recorder);

        // Requirement 4: Encrypt transmission
        CfnConfigRule elbTlsHttpsListenersOnly = managedConfigRule("PciDssElbTlsOnly", this.stackName + "-pci-dss-elb-tls-https-only",
                "PCI-DSS Req 4.1: Use strong cryptography for transmission",
                "ELB_TLS_HTTPS_LISTENERS_ONLY", pciDssCondition);
        dependOnRecorder(elbTlsHttpsListenersOnly, recorder);

        // Requirement 7: Restrict access by business need to know
        CfnConfigRule iamPolicyNoStatementsWithAdminAccess = managedConfigRule("PciDssIamNoAdminPolicy", this.stackName + "-pci-dss-iam-no-admin-policy",
                "PCI-DSS Req 7.1: Limit access to system components by business need-to-know",
                "IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS", pciDssCondition);
        dependOnRecorder(iamPolicyNoStatementsWithAdminAccess, recorder);

        // Requirement 8: Identify and authenticate access
        CfnConfigRule iamUserMfaEnabled = managedConfigRule("PciDssIamMfaEnabled", this.stackName + "-pci-dss-iam-user-mfa-enabled",
                "PCI-DSS Req 8.3: Multi-factor authentication for remote access",
                "IAM_USER_MFA_ENABLED", pciDssCondition);
        dependOnRecorder(iamUserMfaEnabled, recorder);

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
        dependOnRecorder(cloudwatchAlarmActionCheck, recorder);

        // Requirement 11: Test security systems
        CfnConfigRule guardDutyEnabledCentralized = managedConfigRule("PciDssGuardDutyEnabled", this.stackName + "-pci-dss-guardduty-enabled",
                "PCI-DSS Req 11.4: Use intrusion detection systems",
                "GUARDDUTY_ENABLED_CENTRALIZED", pciDssCondition);
        dependOnRecorder(guardDutyEnabledCentralized, recorder);

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
    private void createSoc2ConfigRules(CfnConfigurationRecorder recorder) {
        LOG.info("Creating SOC 2 AWS Config rules (condition-controlled)");

        // CC6.1: Logical Access Controls
        CfnConfigRule iamUserNoPolicies = managedConfigRule("Soc2IamUserNoPolicies", this.stackName + "-soc2-iam-user-no-policies",
                "SOC 2 CC6.1: Implement role-based access control",
                "IAM_USER_NO_POLICIES_CHECK", soc2Condition);
        dependOnRecorder(iamUserNoPolicies, recorder);

        // CC6.6: Network Segmentation
        CfnConfigRule restrictedSshCheck = managedConfigRule("Soc2RestrictedSsh", this.stackName + "-soc2-restricted-ssh",
                "SOC 2 CC6.6: Network segmentation and access control",
                ManagedRuleIdentifiers.EC2_SECURITY_GROUPS_INCOMING_SSH_DISABLED, soc2Condition);
        dependOnRecorder(restrictedSshCheck, recorder);

        // CC6.7: Transmission Encryption
        CfnConfigRule albHttpToHttpsRedirection = managedConfigRule("Soc2AlbHttpsRedirection", this.stackName + "-soc2-alb-https-redirection",
                "SOC 2 CC6.7: Encrypt data in transmission",
                "ALB_HTTP_TO_HTTPS_REDIRECTION_CHECK", soc2Condition);
        dependOnRecorder(albHttpToHttpsRedirection, recorder);

        // CC7.2: System Monitoring
        CfnConfigRule securityHubEnabled = managedConfigRule("Soc2SecurityHubEnabled", this.stackName + "-soc2-security-hub-enabled",
                "SOC 2 CC7.2: Monitor system components for anomalies",
                "SECURITYHUB_ENABLED", soc2Condition);
        dependOnRecorder(securityHubEnabled, recorder);

        // Automatic remediation: enables Security Hub if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableSecurityHubRemediation) ||
            (enableSecurityHubRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createSecurityHubRemediation(securityHubEnabled);
        }

        // CC7.2: Vulnerability Scanning
        CfnConfigRule inspectorEnabled = managedConfigRule("Soc2InspectorEnabled", this.stackName + "-soc2-inspector-enabled",
                "SOC 2 CC7.2: Continuous vulnerability scanning",
                "INSPECTOR_EC2_SCAN_ENABLED", soc2Condition);
        dependOnRecorder(inspectorEnabled, recorder);

        // Automatic remediation: enables Inspector if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableInspectorRemediation) ||
            (enableInspectorRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createInspectorRemediation(inspectorEnabled);
        }

        // CC7.2: Sensitive Data Protection
        CfnConfigRule macieEnabled = managedConfigRule("Soc2MacieEnabled", this.stackName + "-soc2-macie-enabled",
                "SOC 2 CC7.2: Sensitive data discovery and protection",
                "MACIE_STATUS_CHECK", soc2Condition);
        dependOnRecorder(macieEnabled, recorder);

        // Automatic remediation: enables Macie if not already enabled (PRODUCTION only by default)
        if (Boolean.TRUE.equals(enableMacieRemediation) ||
            (enableMacieRemediation == null && ctx.security == SecurityProfile.PRODUCTION)) {
            createMacieRemediation(macieEnabled);
        }

        // CC8.1: Change Management
        CfnConfigRule cloudtrailS3DataEventsEnabled = managedConfigRule("Soc2CloudTrailS3DataEvents", this.stackName + "-soc2-cloudtrail-s3-data-events",
                "SOC 2 CC8.1: Track and authorize infrastructure changes",
                "CLOUDTRAIL_S3_DATAEVENTS_ENABLED", soc2Condition);
        dependOnRecorder(cloudtrailS3DataEventsEnabled, recorder);

        // A1.2: High Availability (for production)
        // A1.3: Data Backup
        CfnConfigRule soc2RdsBackupEnabled = managedConfigRule("Soc2RdsBackup", this.stackName + "-soc2-db-instance-backup-enabled",
                "SOC 2 A1.3: Implement data backup procedures",
                "DB_INSTANCE_BACKUP_ENABLED", soc2RdsCondition);
        dependOnRecorder(soc2RdsBackupEnabled, recorder);

        // CC6.1: Logical Access Security Controls
        CfnConfigRule soc2RdsPublicAccess = managedConfigRule("Soc2RdsPublicAccess", this.stackName + "-soc2-rds-instance-public-access-check",
                "SOC 2 CC6.1: Restrict logical access to databases",
                "RDS_INSTANCE_PUBLIC_ACCESS_CHECK", soc2RdsCondition);
        dependOnRecorder(soc2RdsPublicAccess, recorder);

        // CC6.1: Encryption Controls
        CfnConfigRule soc2RdsEncryption = managedConfigRule("Soc2RdsEncryption", this.stackName + "-soc2-rds-storage-encrypted",
                "SOC 2 CC6.1: Encrypt data at rest to protect logical access",
                "RDS_STORAGE_ENCRYPTED", soc2RdsCondition);
        dependOnRecorder(soc2RdsEncryption, recorder);

        // CC7.2: System Monitoring
        CfnConfigRule soc2RdsLogging = managedConfigRule("Soc2RdsLogging", this.stackName + "-soc2-rds-logging-enabled",
                "SOC 2 CC7.2: Enable logging to monitor and detect system anomalies",
                "RDS_LOGGING_ENABLED", soc2RdsCondition);
        dependOnRecorder(soc2RdsLogging, recorder);

        // CC7.1: System Operations
        CfnConfigRule soc2RdsAutoUpgrade = managedConfigRule("Soc2RdsAutoUpgrade", this.stackName + "-soc2-rds-automatic-minor-version-upgrade",
                "SOC 2 CC7.1: Ensure systems are updated with security patches",
                "RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED", soc2RdsCondition);
        dependOnRecorder(soc2RdsAutoUpgrade, recorder);
        createRdsAutoMinorVersionUpgradeRemediation(soc2RdsAutoUpgrade);

        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule rdsMultiAz = managedConfigRule("Soc2RdsMultiAz", this.stackName + "-soc2-rds-multi-az-support",
                    "SOC 2 A1.2: Deploy across multiple availability zones",
                    "RDS_MULTI_AZ_SUPPORT", soc2RdsCondition);
            dependOnRecorder(rdsMultiAz, recorder);

            CfnConfigRule soc2RdsDeletionProtection = managedConfigRule("Soc2RdsDeletionProtection", this.stackName + "-soc2-rds-deletion-protection-enabled",
                    "SOC 2 A1.2: Protect critical databases from accidental deletion",
                    "RDS_INSTANCE_DELETION_PROTECTION_ENABLED", soc2RdsCondition);
            dependOnRecorder(soc2RdsDeletionProtection, recorder);
            createRdsDeletionProtectionRemediation(soc2RdsDeletionProtection);

            CfnConfigRule soc2RdsEnhancedMonitoring = managedConfigRule("Soc2RdsEnhancedMonitoring", this.stackName + "-soc2-rds-enhanced-monitoring-enabled",
                    "SOC 2 CC7.2: Monitor system components and detect anomalies",
                    "RDS_ENHANCED_MONITORING_ENABLED", soc2Condition);
            dependOnRecorder(soc2RdsEnhancedMonitoring, recorder);

            CfnConfigRule elbDeletionProtection = managedConfigRule("Soc2ElbDeletionProtection", this.stackName + "-soc2-elb-deletion-protection",
                    "SOC 2 A1.2: Protect critical components from accidental deletion",
                    "ELB_DELETION_PROTECTION_ENABLED", soc2Condition);
            dependOnRecorder(elbDeletionProtection, recorder);
        }

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "7" : "5") + " SOC 2 Config rules with conditional deployment");
    }

    /**
     * Creates HIPAA specific AWS Config rules.
     * Implements controls for HIPAA Security Rule (45 CFR Part 164).
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createHipaaConfigRules(CfnConfigurationRecorder recorder) {
        LOG.info("Creating HIPAA AWS Config rules (condition-controlled)");

        // §164.308(a)(1): Security Management Process
        CfnConfigRule cloudtrailCloudwatchLogsEnabled = managedConfigRule("HipaaCloudTrailCloudWatchLogs", this.stackName + "-hipaa-cloudtrail-cloudwatch-logs",
                "HIPAA §164.308(a)(1)(ii)(D): Information system activity review",
                "CLOUD_TRAIL_CLOUD_WATCH_LOGS_ENABLED", hipaaCondition);
        dependOnRecorder(cloudtrailCloudwatchLogsEnabled, recorder);

        // §164.308(a)(3): Workforce Security
        CfnConfigRule iamUserGroupMembershipCheck = managedConfigRule("HipaaIamGroupMembership", this.stackName + "-hipaa-iam-group-membership",
                "HIPAA §164.308(a)(3): Implement procedures for workforce clearance",
                "IAM_USER_GROUP_MEMBERSHIP_CHECK", hipaaCondition);
        dependOnRecorder(iamUserGroupMembershipCheck, recorder);

        // §164.310(d): Device and Media Controls (Backup)
        CfnConfigRule dynamodbPitrEnabled = managedConfigRule("HipaaDynamoDbPitr", this.stackName + "-hipaa-dynamodb-pitr-enabled",
                "HIPAA §164.310(d)(2)(iv): Create backup copies of ePHI",
                "DYNAMODB_PITR_ENABLED", hipaaCondition);
        dependOnRecorder(dynamodbPitrEnabled, recorder);

        CfnConfigRule rdsSnapshotEncrypted = managedConfigRule("HipaaRdsSnapshotEncrypted", this.stackName + "-hipaa-rds-snapshot-encrypted",
                "HIPAA §164.312(a)(2)(iv): Encrypt ePHI backups",
                "RDS_SNAPSHOT_ENCRYPTED", hipaaRdsCondition);
        dependOnRecorder(rdsSnapshotEncrypted, recorder);

        CfnConfigRule hipaaRdsEncryption = managedConfigRule("HipaaRdsEncryption", this.stackName + "-hipaa-rds-storage-encrypted",
                "HIPAA §164.312(a)(2)(iv): Encrypt ePHI at rest",
                "RDS_STORAGE_ENCRYPTED", hipaaRdsCondition);
        dependOnRecorder(hipaaRdsEncryption, recorder);

        CfnConfigRule hipaaRdsPublicAccess = managedConfigRule("HipaaRdsPublicAccess", this.stackName + "-hipaa-rds-instance-public-access-check",
                "HIPAA §164.308(a)(4)(ii)(B): Implement access management controls",
                "RDS_INSTANCE_PUBLIC_ACCESS_CHECK", hipaaRdsCondition);
        dependOnRecorder(hipaaRdsPublicAccess, recorder);

        CfnConfigRule hipaaRdsBackupEnabled = managedConfigRule("HipaaRdsBackup", this.stackName + "-hipaa-db-instance-backup-enabled",
                "HIPAA §164.310(d)(2)(iii): Create retrievable backup copies of ePHI",
                "DB_INSTANCE_BACKUP_ENABLED", hipaaRdsCondition);
        dependOnRecorder(hipaaRdsBackupEnabled, recorder);

        CfnConfigRule hipaaRdsAutoUpgrade = managedConfigRule("HipaaRdsAutoUpgrade", this.stackName + "-hipaa-rds-automatic-minor-version-upgrade",
                "HIPAA §164.308(a)(5)(ii)(B): Install security patches and updates",
                "RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED", hipaaRdsCondition);
        dependOnRecorder(hipaaRdsAutoUpgrade, recorder);
        createRdsAutoMinorVersionUpgradeRemediation(hipaaRdsAutoUpgrade);

        CfnConfigRule hipaaRdsLoggingEnabled = managedConfigRule("HipaaRdsLogging", this.stackName + "-hipaa-rds-logging-enabled",
                "HIPAA §164.312(b): Implement hardware/software audit controls",
                "RDS_LOGGING_ENABLED", hipaaRdsCondition);
        dependOnRecorder(hipaaRdsLoggingEnabled, recorder);

        CfnConfigRule hipaaRdsDeletionProtection = managedConfigRule("HipaaRdsDeletionProtection", this.stackName + "-hipaa-rds-deletion-protection-enabled",
                "HIPAA §164.308(a)(7)(ii)(A): Establish data backup contingency plan",
                "RDS_INSTANCE_DELETION_PROTECTION_ENABLED", hipaaRdsCondition);
        dependOnRecorder(hipaaRdsDeletionProtection, recorder);
        createRdsDeletionProtectionRemediation(hipaaRdsDeletionProtection);

        // §164.312(a)(1): Access Control
        CfnConfigRule rootAccountMfaEnabled = managedConfigRule("HipaaRootMfaEnabled", this.stackName + "-hipaa-root-account-mfa-enabled",
                "HIPAA §164.312(a)(2)(i): Assign unique user identification",
                "ROOT_ACCOUNT_MFA_ENABLED", hipaaCondition);
        dependOnRecorder(rootAccountMfaEnabled, recorder);

        // §164.312(b): Audit Controls
        CfnConfigRule albWafEnabled = managedConfigRule("HipaaAlbWafEnabled", this.stackName + "-hipaa-alb-waf-enabled",
                "HIPAA §164.312(b): Record and examine activity in systems with ePHI",
                "ALB_WAF_ENABLED", hipaaCondition);
        dependOnRecorder(albWafEnabled, recorder);

        // §164.312(c)(1): Integrity Controls
        CfnConfigRule cloudtrailEncryptionEnabled = managedConfigRule("HipaaCloudTrailEncryption", this.stackName + "-hipaa-cloudtrail-encryption-enabled",
                "HIPAA §164.312(c)(2): Authenticate ePHI integrity",
                "CLOUD_TRAIL_ENCRYPTION_ENABLED", hipaaCondition);
        dependOnRecorder(cloudtrailEncryptionEnabled, recorder);

        // §164.312(e)(1): Transmission Security
        CfnConfigRule elbAcmCertificateRequired = managedConfigRule("HipaaElbAcmCertificate", this.stackName + "-hipaa-elb-acm-certificate-required",
                "HIPAA §164.312(e)(2)(ii): Encrypt ePHI during transmission",
                "ELB_ACM_CERTIFICATE_REQUIRED", hipaaCondition);
        dependOnRecorder(elbAcmCertificateRequired, recorder);

        LOG.info("Created 8 HIPAA Config rules with conditional deployment");
    }

    /**
     * Creates GDPR specific AWS Config rules.
     * Implements technical controls for GDPR compliance.
     *
     * @param recorder The Configuration Recorder that rules depend on
     */
    private void createGdprConfigRules(CfnConfigurationRecorder recorder) {
        LOG.info("Creating GDPR AWS Config rules (condition-controlled)");

        // Article 25: Data Protection by Design
        CfnConfigRule ec2EbsOptimized = managedConfigRule("GdprEc2EbsOptimized", this.stackName + "-gdpr-ec2-ebs-optimized",
                "GDPR Art. 25: Data protection by design - optimize storage security",
                ManagedRuleIdentifiers.EBS_OPTIMIZED_INSTANCE, gdprCondition);
        dependOnRecorder(ec2EbsOptimized, recorder);

        // Article 30: Records of Processing Activities
        CfnConfigRule vpcFlowLogsEnabled = managedConfigRule("GdprVpcFlowLogs", this.stackName + "-gdpr-vpc-flow-logs-enabled",
                "GDPR Art. 30(1): Maintain records of processing activities",
                "VPC_FLOW_LOGS_ENABLED", gdprCondition);
        dependOnRecorder(vpcFlowLogsEnabled, recorder);

        // Article 32(1)(a): Pseudonymisation and Encryption
        CfnConfigRule s3DefaultEncryptionKms = managedConfigRule("GdprS3DefaultEncryptionKms", this.stackName + "-gdpr-s3-default-encryption-kms",
                "GDPR Art. 32(1)(a): Encrypt personal data at rest",
                "S3_DEFAULT_ENCRYPTION_KMS", gdprCondition);
        dependOnRecorder(s3DefaultEncryptionKms, recorder);

        CfnConfigRule kmsBackingKeyRotationEnabled = managedConfigRule("GdprKmsKeyRotation", this.stackName + "-gdpr-kms-backing-key-rotation",
                "GDPR Art. 32(1)(a): Rotate encryption keys regularly",
                "CMK_BACKING_KEY_ROTATION_ENABLED", gdprCondition);
        dependOnRecorder(kmsBackingKeyRotationEnabled, recorder);

        CfnConfigRule gdprRdsEncryption = managedConfigRule("GdprRdsEncryption", this.stackName + "-gdpr-rds-storage-encrypted",
                "GDPR Art. 32(1)(a): Encrypt personal data in databases at rest",
                "RDS_STORAGE_ENCRYPTED", gdprRdsCondition);
        dependOnRecorder(gdprRdsEncryption, recorder);

        // Article 32(1)(b): Confidentiality
        CfnConfigRule gdprRdsPublicAccess = managedConfigRule("GdprRdsPublicAccess", this.stackName + "-gdpr-rds-instance-public-access-check",
                "GDPR Art. 32(1)(b): Ensure confidentiality of personal data systems",
                "RDS_INSTANCE_PUBLIC_ACCESS_CHECK", gdprRdsCondition);
        dependOnRecorder(gdprRdsPublicAccess, recorder);
        CfnConfigRule restrictedRdpCheck = managedConfigRule("GdprRestrictedRdp", this.stackName + "-gdpr-restricted-rdp",
                "GDPR Art. 32(1)(b): Ensure ongoing confidentiality of systems",
                ManagedRuleIdentifiers.EC2_SECURITY_GROUPS_RESTRICTED_INCOMING_TRAFFIC, gdprCondition);
        dependOnRecorder(restrictedRdpCheck, recorder);

        // Article 32(1)(c): Availability and Resilience
        CfnConfigRule gdprRdsBackupEnabled = managedConfigRule("GdprRdsBackup", this.stackName + "-gdpr-db-instance-backup-enabled",
                "GDPR Art. 32(1)(c): Ensure resilience through data backups",
                "DB_INSTANCE_BACKUP_ENABLED", gdprRdsCondition);
        dependOnRecorder(gdprRdsBackupEnabled, recorder);

        if (security == SecurityProfile.PRODUCTION) {
            CfnConfigRule dynamodbAutoscalingEnabled = managedConfigRule("GdprDynamoDbAutoscaling", this.stackName + "-gdpr-dynamodb-autoscaling-enabled",
                    "GDPR Art. 32(1)(c): Ensure resilience and availability of systems",
                    "DYNAMODB_AUTOSCALING_ENABLED", gdprCondition);
            dependOnRecorder(dynamodbAutoscalingEnabled, recorder);

            CfnConfigRule s3BucketReplicationEnabled = managedConfigRule("GdprS3Replication", this.stackName + "-gdpr-s3-bucket-replication",
                    "GDPR Art. 32(1)(c): Implement geographic redundancy",
                    "S3_BUCKET_REPLICATION_ENABLED", gdprCondition);
            dependOnRecorder(s3BucketReplicationEnabled, recorder);
        }

        // Article 32(1)(d): Audit and Assessment Capabilities
        CfnConfigRule gdprRdsLoggingEnabled = managedConfigRule("GdprRdsLogging", this.stackName + "-gdpr-rds-logging-enabled",
                "GDPR Art. 32(1)(d): Implement processes for testing and assessing security",
                "RDS_LOGGING_ENABLED", gdprRdsCondition);
        dependOnRecorder(gdprRdsLoggingEnabled, recorder);

        // Article 32(1)(a): Security of Processing - Automated Updates
        CfnConfigRule gdprRdsAutoUpgrade = managedConfigRule("GdprRdsAutoUpgrade", this.stackName + "-gdpr-rds-automatic-minor-version-upgrade",
                "GDPR Art. 32(1)(a): Maintain security through automated system updates",
                "RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED", gdprRdsCondition);
        dependOnRecorder(gdprRdsAutoUpgrade, recorder);
        createRdsAutoMinorVersionUpgradeRemediation(gdprRdsAutoUpgrade);

        // Article 32(1)(c): Availability and Resilience - Deletion Protection
        CfnConfigRule gdprRdsDeletionProtection = managedConfigRule("GdprRdsDeletionProtection", this.stackName + "-gdpr-rds-deletion-protection-enabled",
                "GDPR Art. 32(1)(c): Protect personal data from accidental destruction",
                "RDS_INSTANCE_DELETION_PROTECTION_ENABLED", gdprRdsCondition);
        dependOnRecorder(gdprRdsDeletionProtection, recorder);
        createRdsDeletionProtectionRemediation(gdprRdsDeletionProtection);

        if (security == SecurityProfile.PRODUCTION) {
            // Article 32(1)(c): High Availability for Production Systems
            CfnConfigRule gdprRdsMultiAz = managedConfigRule("GdprRdsMultiAz", this.stackName + "-gdpr-rds-multi-az-support",
                    "GDPR Art. 32(1)(c): Ensure ability to restore availability of personal data",
                    "RDS_MULTI_AZ_SUPPORT", gdprCondition);
            dependOnRecorder(gdprRdsMultiAz, recorder);
        }

        // Article 33: Breach Detection
        CfnConfigRule guarddutyNonArchivedFindings = managedConfigRule("GdprGuardDutyFindings", this.stackName + "-gdpr-guardduty-non-archived-findings",
                "GDPR Art. 33(1): Detect data breaches within 72 hours",
                "GUARDDUTY_NON_ARCHIVED_FINDINGS", gdprCondition);
        dependOnRecorder(guarddutyNonArchivedFindings, recorder);

        LOG.info("Created " + (security == SecurityProfile.PRODUCTION ? "11" : "9") + " GDPR Config rules with conditional deployment");
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

        LOG.info("Created shared Audit Manager role and bucket with evidence storage and service read permissions");

        // Suppress CDK-nag warnings for IAM wildcards - all are justified
        NagSuppressions.addResourceSuppressions(
            auditManagerRole,
            List.of(
                NagSuppress.of("AwsSolutions-IAM5",
                    "S3 bucket object access requires /* pattern for write operations. " +
                           "Service read APIs (AuditManager, CloudTrail, Config, SecurityHub, IAM, EC2) " +
                           "require * for account-wide read-only operations - AWS service requirements.")
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
                        .awsServices(ASSESSMENT_SERVICES.stream()
                                .map(name -> CfnAssessment.AWSServiceProperty.builder().serviceName(name).build())
                                .toList())
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

    /** AWS services an Audit Manager assessment collects evidence from. */
    private static final List<String> ASSESSMENT_SERVICES = List.of(
        "AWS Config",
        "AWS CloudTrail",
        "Amazon CloudWatch",
        "AWS Security Hub",
        "Amazon GuardDuty",
        "AWS IAM",
        "Amazon VPC",
        "Amazon EC2",
        "Amazon ECS",
        "Amazon EFS",
        "Amazon S3",
        "AWS KMS",
        "AWS Secrets Manager",
        "Amazon Route53",
        "AWS Certificate Manager",
        "Elastic Load Balancing"
    );

    /** How an Audit Manager evidence source maps to a data source. */
    private record EvidenceSourceMapping(String namePrefix, String description, String sourceType) {}

    private static final Map<String, EvidenceSourceMapping> EVIDENCE_SOURCES = Map.ofEntries(
        Map.entry("cloudtrail", new EvidenceSourceMapping("CloudTrail", "CloudTrail API activity logs", "AWS_Cloudtrail")),
        Map.entry("securityhub", new EvidenceSourceMapping("SecurityHub", "Security Hub findings", "AWS_Security_Hub")),
        Map.entry("guardduty", new EvidenceSourceMapping("GuardDuty", "GuardDuty threat detections", "AWS_API_Call")),
        Map.entry("vpc-flowlogs", new EvidenceSourceMapping("VPCFlowLogs", "VPC network traffic logs", "AWS_Cloudtrail")),
        Map.entry("cloudwatch", new EvidenceSourceMapping("CloudWatch", "CloudWatch metrics and logs", "AWS_API_Call")),
        Map.entry("cloudwatch-logs", new EvidenceSourceMapping("CloudWatch", "CloudWatch metrics and logs", "AWS_API_Call")),
        Map.entry("s3", new EvidenceSourceMapping("S3", "S3 access logs", "AWS_Cloudtrail")),
        Map.entry("iam", new EvidenceSourceMapping("IAM", "IAM credential reports", "AWS_API_Call")),
        Map.entry("waf", new EvidenceSourceMapping("WAF", "WAF web ACL logs", "AWS_Cloudtrail"))
    );

    /**
     * Builds a CfnAssessment control property from AuditManagerControl.
     * Maps Config rules to control data sources for automated evidence collection.
     *
     * @param control AuditManagerControl with Config rule mappings
     * @param frameworkControl Framework-specific control details
     * @param frameworkName Framework name for data source naming
     * @return Control property map for CfnAssessment
     */
    // codeql[java/unused-parameter] -- frameworkName isn't consulted below (sourceName uses only
    // control.controlId()), despite the javadoc's "for data source naming". Each CfnAssessment
    // is already built per-framework (its caller's own frameworkName parameter), so the
    // control-id-only naming here doesn't collide across frameworks in practice; kept for the
    // caller's convenience/symmetry rather than a fix that would change these labels.
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

            EvidenceSourceMapping mapping = EVIDENCE_SOURCES.get(evidenceSource.toLowerCase());
            Map<String, Object> dataSource = new java.util.LinkedHashMap<>();
            if (mapping != null) {
                dataSource.put("sourceName", mapping.namePrefix() + "-" + control.controlId());
                dataSource.put("sourceDescription", mapping.description());
                dataSource.put("sourceType", mapping.sourceType());
                dataSource.put("sourceSetUpOption", "System_Controls_Mapping");
            } else {
                // Generic evidence source - manual collection
                dataSource.put("sourceName", evidenceSource + "-" + control.controlId());
                dataSource.put("sourceDescription", "Manual evidence collection for " + evidenceSource);
                dataSource.put("sourceType", "MANUAL");
                dataSource.put("sourceSetUpOption", "Procedural_Controls_Mapping");
            }
            dataSource.put("sourceFrequency", "DAILY");

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
     * Handles full ARNs and UUIDs directly, both pure string parsing.
     *
     * <p>Does NOT resolve short names ("SOC2", "HIPAA") automatically -- that used to mean a
     * live AWS Audit Manager lookup, which had two real problems: it ran at synth time (a local
     * subprocess call to a hardcoded AWS CLI path, unreachable in most environments and reaching
     * a live account when it did work), and even a working lookup would only be reliable per
     * region, since Audit Manager's standard-framework UUIDs are not stable across regions. Audit
     * Manager is also opt-in only now (closed to new AWS accounts as of April 30, 2026, see
     * {@link com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration#isAuditManagerEnabled}),
     * so it only ever matters for the shrinking set of deployers who already have it set up in an
     * existing account -- not worth a deploy-time custom resource to automate a lookup that
     * population can just do once themselves. Supply the framework's ARN or UUID directly (find
     * it with {@code aws auditmanager list-assessment-frameworks --framework-type Standard
     * --region <your-region>}) instead of a short name.
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

        LOG.warning("Cannot resolve Audit Manager framework '" + identifier + "': short-name "
            + "auto-resolution isn't supported (see resolveFrameworkIdentifier's own javadoc for "
            + "why). Supply this framework's ARN or UUID directly in complianceFrameworks instead "
            + "-- find it with: aws auditmanager list-assessment-frameworks "
            + "--framework-type Standard --region <your-region>");
        LOG.warning("  Skipping Audit Manager assessment creation for this framework");
        LOG.warning("  Note: Other compliance layers (cdk-nag, FrameworkRules, cfn-guard, AWS Config) will still validate this framework");
        return null;  // Return null to signal "skip this framework"
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

        // Enable KMS encryption for compliance buckets when the security profile resolves
        // cloudWatchLogsKmsEncryptionEnabled to true: on in PRODUCTION, required by a framework
        // in ENFORCE mode, or set explicitly.
        boolean isHipaa = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("HIPAA");
        boolean isPciDss = complianceFrameworks != null && complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean useKms = config.isCloudWatchLogsKmsEncryptionEnabled();

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
                // Object Lock requires RETAIN - CloudFormation cannot delete buckets with Object Lock enabled
                .removalPolicy(shouldRetain || enableObjectLock ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
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
                    // Always destroy - if bucket is retained, key is useless without encrypted data
                    .removalPolicy(RemovalPolicy.DESTROY)
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
        suppressions.add(NagSuppress.of("PCI.DSS.321-S3BucketReplicationEnabled",
            "S3 replication is not required for single-region deployments. " +
                       "Compliance data is retained in the same region with versioning enabled."));
        suppressions.add(NagSuppress.of("PCI.DSS.321-S3BucketLoggingEnabled",
            "CloudTrail S3 data events provide equivalent audit logging for bucket access. " +
                       "Server access logging would create circular dependency for compliance buckets."));
        suppressions.add(NagSuppress.of("HIPAA.Security-S3BucketReplicationEnabled",
            "S3 replication is not required for single-region deployments. " +
                       "Compliance data is retained in the same region with versioning enabled."));
        suppressions.add(NagSuppress.of("PCI.DSS.321-S3DefaultEncryptionKMS",
            "Bucket uses encryption with SSL enforcement. " +
                       "KMS encryption is applied for HIPAA/PCI-DSS PRODUCTION deployments."));
        suppressions.add(NagSuppress.of("HIPAA.Security-S3BucketLoggingEnabled",
            "CloudTrail S3 data events provide equivalent audit logging for bucket access. " +
                       "Server access logging would create circular dependency for compliance buckets."));

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

        CustomResource ssmWriter = createSsmArnWriter(id, ssmParameterName, bucket.getBucketArn(),
                "CloudForge retained " + id + " ARN for region " + region);

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
    private CustomResource storeResourceArnInSSM(String id, String ssmParameterName, String arnValue, String description) {
        if (security != SecurityProfile.PRODUCTION) {
            LOG.fine("Non-production mode: Skipping SSM tracking for " + id);
            return null;
        }

        return createSsmArnWriter(id, ssmParameterName, arnValue, description);
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
    private CustomResource storeResourceArnInSSMAlways(String id, String ssmParameterName, String arnValue, String description) {
        LOG.info("Storing " + id + " ARN in SSM Parameter Store (always): " + ssmParameterName);
        return createSsmArnWriter(id, ssmParameterName, arnValue, description);
    }

    /**
     * Writes {@code arnValue} to SSM Parameter Store as {@code ssmParameterName} at deploy time --
     * shared by getOrCreateBucketWithSSM/storeResourceArnInSSM/storeResourceArnInSSMAlways, which
     * only differ in when they call this (PRODUCTION-only vs. always) and what they do with the
     * result. Asset-free ({@link AssetFreeCustomResource}, not {@code AwsCustomResource}/{@code
     * Provider}): both of those pull in CDK's own bundled framework Lambda, staged to the
     * deployer's private {@code cdk-hnb659fds-assets} bootstrap bucket -- unusable by an AWS
     * Marketplace buyer launching a template directly in their own, unbootstrapped account.
     */
    private CustomResource createSsmArnWriter(String id, String ssmParameterName, String arnValue, String description) {
        LOG.info("Storing " + id + " ARN in SSM Parameter Store: " + ssmParameterName);

        // Partition-aware (not a hardcoded "arn:aws" literal) -- a literal breaks this custom
        // resource's policy outside the standard AWS partition (GovCloud's "arn:aws-us-gov",
        // China's "arn:aws-cn"), which this project's own compliance/FedRAMP-adjacent posture
        // makes a plausible real target even though this codebase doesn't deploy there today.
        String ssmParameterArn = "arn:" + Stack.of(this).getPartition() + ":ssm:" + region + ":"
            + Stack.of(this).getAccount() + ":parameter" + ssmParameterName;

        AssetFreeCustomResource.Result result = AssetFreeCustomResource.create(
                this, id + "SSMWriter", AssetFreeCustomResource.SSM_PUT_PARAMETER_HANDLER_JS,
                Duration.seconds(30),
                List.of(PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(List.of("ssm:PutParameter"))
                        .resources(List.of(ssmParameterArn))
                        .build()),
                java.util.Map.of(
                        "ParameterName", ssmParameterName,
                        "ParameterValue", arnValue,
                        "ParameterDescription", description,
                        "Region", region
                ));

        applyCustomResourceNagSuppressions(result.function);

        LOG.fine(id + " ARN will be tracked in SSM: " + ssmParameterName);
        return result.customResource;
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
     * Creates an SSM Automation document that enables a service, the role it runs as, and an
     * automatic Config remediation that runs it. The remediation carries the same condition as the
     * rule it targets: an unconditional Ref to a condition-omitted resource fails CloudFormation
     * validation.
     */
    private void createEnablementRemediation(String name, CfnConfigRule rule, CfnCondition condition,
                                             String description, List<Object> steps, List<String> actions) {
        CfnDocument document = CfnDocument.Builder.create(this, name + "EnablementDocument")
                .documentType("Automation")
                .content(Map.of(
                    "schemaVersion", "0.3",
                    "description", description,
                    "assumeRole", "{{ AutomationAssumeRole }}",
                    "parameters", Map.of(
                        "AutomationAssumeRole", Map.of(
                            "type", "String",
                            "description", "IAM role ARN for SSM Automation"
                        )
                    ),
                    "mainSteps", steps
                ))
                .build();

        Role ssmRole = Role.Builder.create(this, name + "RemediationRole")
                .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
                .inlinePolicies(Map.of(
                    name + "Permissions",
                    PolicyDocument.Builder.create()
                        .statements(List.of(
                            PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(actions)
                                .resources(List.of("*"))
                                .build()
                        ))
                        .build()
                ))
                .build();

        CfnRemediationConfiguration remediation = CfnRemediationConfiguration.Builder.create(
                this, name + "Remediation")
                .configRuleName(rule.getRef())
                .targetType("SSM_DOCUMENT")
                .targetId(document.getRef())
                .targetVersion("1")
                .automatic(true)
                .maximumAutomaticAttempts(3)
                .retryAttemptSeconds(60)
                .build();

        remediation.addPropertyOverride("Parameters", Map.of(
            "AutomationAssumeRole", Map.of("StaticValue", Map.of("Values", List.of(ssmRole.getRoleArn())))
        ));
        remediation.addOverride("Condition", condition.getLogicalId());

        LOG.info(name + " automatic remediation enabled");
    }

    /**
     * Creates auto-remediation for Security Hub enablement.
     * Automatically enables AWS Security Hub if not already enabled.
     *
     * @param securityHubRule The Config rule to attach remediation to
     */
    private void createSecurityHubRemediation(CfnConfigRule securityHubRule) {
        createEnablementRemediation("SecurityHub", securityHubRule, soc2Condition,
            "Enables AWS Security Hub if not already enabled",
            List.of(Map.of(
                "name", "EnableSecurityHub",
                "action", "aws:executeAwsApi",
                "onFailure", "Continue",
                "inputs", Map.of(
                    "Service", "securityhub",
                    "Api", "EnableSecurityHub"
                )
            )),
            List.of("securityhub:EnableSecurityHub", "securityhub:GetEnabledStandards"));
    }

    /**
     * Creates auto-remediation for Inspector enablement.
     * Automatically enables Amazon Inspector v2 if not already enabled.
     *
     * @param inspectorRule The Config rule to attach remediation to
     */
    private void createInspectorRemediation(CfnConfigRule inspectorRule) {
        createEnablementRemediation("Inspector", inspectorRule, soc2Condition,
            "Enables Amazon Inspector v2 if not already enabled",
            List.of(Map.of(
                "name", "EnableInspector",
                "action", "aws:executeAwsApi",
                "onFailure", "Continue",
                "inputs", Map.of(
                    "Service", "inspector2",
                    "Api", "Enable",
                    "resourceTypes", List.of("EC2", "ECR", "LAMBDA")
                )
            )),
            List.of("inspector2:Enable", "inspector2:GetConfiguration"));
    }

    /**
     * Creates auto-remediation for Macie enablement.
     * Automatically enables Amazon Macie if not already enabled.
     *
     * @param macieRule The Config rule to attach remediation to
     */
    private void createMacieRemediation(CfnConfigRule macieRule) {
        createEnablementRemediation("Macie", macieRule, soc2Condition,
            "Enables Amazon Macie if not already enabled",
            List.of(Map.of(
                "name", "EnableMacie",
                "action", "aws:executeAwsApi",
                "onFailure", "Continue",
                "inputs", Map.of(
                    "Service", "macie2",
                    "Api", "EnableMacie"
                )
            )),
            List.of("macie2:EnableMacie", "macie2:GetMacieSession"));
    }

    /**
     * Creates auto-remediation for GuardDuty enablement.
     * Automatically enables AWS GuardDuty if not already enabled.
     *
     * @param guardDutyRule The Config rule to attach remediation to
     */
    private void createGuardDutyRemediation(CfnConfigRule guardDutyRule) {
        // Idempotent: the detector is only created when none exists
        createEnablementRemediation("GuardDuty", guardDutyRule, pciDssCondition,
            "Enables AWS GuardDuty if not already enabled (idempotent)",
            List.of(
                Map.of(
                    "name", "CheckGuardDutyStatus",
                    "action", "aws:executeAwsApi",
                    "onFailure", "Continue",
                    "inputs", Map.of(
                        "Service", "guardduty",
                        "Api", "ListDetectors"
                    ),
                    "outputs", List.of(
                        Map.of(
                            "Name", "DetectorIds",
                            "Selector", "$.DetectorIds",
                            "Type", "StringList"
                        )
                    )
            ),
            Map.of(
                "name", "EnableGuardDuty",
                "action", "aws:branch",
                "inputs", Map.of(
                    "Choices", List.of(
                        Map.of(
                            "NextStep", "CreateDetector",
                            "Variable", "{{ CheckGuardDutyStatus.DetectorIds }}",
                            "StringEquals", "[]"
                        )
                    ),
                    "Default", "AlreadyEnabled"
                )
            ),
            Map.of(
                "name", "CreateDetector",
                "action", "aws:executeAwsApi",
                "inputs", Map.of(
                    "Service", "guardduty",
                    "Api", "CreateDetector",
                    "Enable", true,
                    "FindingPublishingFrequency", "FIFTEEN_MINUTES"
                ),
                "isEnd", true
            ),
            Map.of(
                "name", "AlreadyEnabled",
                "action", "aws:sleep",
                "inputs", Map.of(
                    "Duration", "PT0S"
                ),
                "isEnd", true
            )
            ),
            List.of("guardduty:CreateDetector", "guardduty:ListDetectors"));
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
    /** Suppressions for {@link #createSsmArnWriter}'s Lambda -- see that method's own javadoc for
     *  why it's an {@link AssetFreeCustomResource} rather than {@code AwsCustomResource}. Only the
     *  inline-policy/no-VPC findings still apply: the execution policy is scoped to one exact
     *  parameter ARN (no wildcard-resource finding to suppress), and the runtime is an explicit
     *  choice made here, not something CDK manages on this construct's behalf. */
    private void applyCustomResourceNagSuppressions(software.amazon.awscdk.services.lambda.Function customResourceFunction) {
        NagSuppressions.addResourceSuppressions(
            customResourceFunction,
            List.of(
                NagSuppress.of("PCI.DSS.321-IAMNoInlinePolicy",
                    "This Lambda's own execution policy is scoped to a single " +
                           "ssm:PutParameter call on one parameter ARN -- an inline policy is the " +
                           "right shape for a permission this narrow."),
                NagSuppress.of("PCI.DSS.321-LambdaInsideVPC",
                    "This custom resource Lambda only makes an SSM API call and does not " +
                           "require VPC access."),
                NagSuppress.of("HIPAA.Security-IAMNoInlinePolicy",
                    "This Lambda's own execution policy is scoped to a single " +
                           "ssm:PutParameter call on one parameter ARN -- an inline policy is the " +
                           "right shape for a permission this narrow."),
                NagSuppress.of("HIPAA.Security-LambdaInsideVPC",
                    "This custom resource Lambda only makes an SSM API call and does not " +
                           "require VPC access.")
            ),
            Boolean.TRUE
        );
    }


    /** Suffix of the explicit name given to the trail's CloudWatch Logs delivery role, so the
     *  CloudForge Manager deploy role can match it by name. See {@link #nameTrailLogsRole}. */
    private static final String TRAIL_LOGS_ROLE_SUFFIX = "-CfcCloudTrailLogs";

    /** 64, less the suffix, a hyphen, an eight character hash, a hyphen and the longest region name (14). */
    private static final int TRAIL_LOGS_ROLE_PREFIX_LENGTH = 22;

    /**
     * {@code Trail} creates its CloudWatch Logs delivery role itself and has no property to name
     * it, so the role is left with a CloudFormation-generated name that truncates to a generic
     * tail. Manager's deploy role can only create and delete roles it can match by name, so the
     * role is given an explicit one, the same way {@code FlowLogFactory}, {@code RdsFactory} and
     * {@code BackupFactory} name theirs. IAM role names are limited to 64 characters and are unique
     * per account across regions, so the name is the start of the stack name, a hash of the whole
     * stack name (stack names sharing a long prefix stay distinct), the region and the suffix.
     */
    private void nameTrailLogsRole(Trail trail) {
        if (trail.getNode().tryFindChild("LogsRole") instanceof Role logsRole
                && logsRole.getNode().getDefaultChild() instanceof CfnRole cfnRole) {
            String prefix = stackName.length() > TRAIL_LOGS_ROLE_PREFIX_LENGTH
                ? stackName.substring(0, TRAIL_LOGS_ROLE_PREFIX_LENGTH)
                : stackName;
            cfnRole.setRoleName(prefix + "-" + stackNameHash() + "-" + Aws.REGION + TRAIL_LOGS_ROLE_SUFFIX);
        }
    }

    /** Eight hex characters derived from the stack name. */
    private String stackNameHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(stackName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /**
     * Creates an AWS managed Config rule that exists only when {@code condition} holds and is deleted
     * with the stack.
     */
    private CfnConfigRule managedConfigRule(String id, String name, String description,
                                            String sourceIdentifier, CfnCondition condition) {
        CfnConfigRule rule = CfnConfigRule.Builder.create(this, id)
                .configRuleName(name)
                .description(description)
                .source(CfnConfigRule.SourceProperty.builder()
                        .owner("AWS")
                        .sourceIdentifier(sourceIdentifier)
                        .build())
                .build();
        rule.addOverride("DeletionPolicy", "Delete");
        rule.addOverride("Condition", condition.getLogicalId());
        return rule;
    }
}
