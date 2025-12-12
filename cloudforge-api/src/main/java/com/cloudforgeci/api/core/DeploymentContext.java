package com.cloudforgeci.api.core;

import com.cloudforge.core.utilities.DnsLabel;
import com.cloudforge.core.utilities.DnsName;
import com.cloudforge.core.utilities.OneOf;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed configuration interface for CDK deployment context.
 *
 * <p>Loads configuration from cdk.json "cfc" block or CLI flags (-c key = value).
 * Provides type-safe access with validation and sensible defaults.</p>
 *
 * <p><b>Quick Start Example (cdk.json):</b></p>
 * <pre>
 * {
 *   "app": "...",
 *   "context": {
 *     "cfc": {
 *       "runtime": "fargate",
 *       "topology": "jenkins-service",
 *       "env": "dev",
 *       "domain": "example.com",
 *       "subdomain": "jenkins",
 *       "enableSsl": true,
 *       "authMode": "alb-oidc",
 *       "cognitoAutoProvision": true,
 *       "cognitoDomainPrefix": "myapp-auth",
 *       "cognitoMfaEnabled": true
 *     }
 *   }
 * }
 * </pre>
 *
 * <p><b>Configuration Keys</b> (all optional unless noted):</p>
 *
 * <p><b>Core Settings:</b></p>
 * <ul>
 *   <li>tier: "public" | "enterprise" (default: public)</li>
 *   <li>runtime: "ec2" | "fargate" (default: fargate)</li>
 *   <li>topology: "jenkins-single-node" | "jenkins-service" | "s3-website"</li>
 *   <li>env: "dev" | "stage" | "prod" (default: dev)</li>
 *   <li>securityProfile: "dev" | "staging" | "production" (default: dev)</li>
 *   <li>region: AWS region (default: us-east-1)</li>
 * </ul>
 *
 * <p><b>DNS &amp; SSL:</b></p>
 * <ul>
 *   <li>domain: Base domain (e.g., "example.com")</li>
 *   <li>subdomain: Subdomain prefix (e.g., "jenkins")</li>
 *   <li>fqdn: Full domain (e.g., "jenkins.example.com") - overrides domain+subdomain</li>
 *   <li>enableSsl: Enable HTTPS with ACM certificate (default: false)</li>
 *   <li>createZone: Create Route53 hosted zone (default: false)</li>
 * </ul>
 *
 * <p><b>Network &amp; Security:</b></p>
 * <ul>
 *   <li>networkMode: "public-no-nat" | "private-with-nat" (default: public-no-nat)</li>
 *   <li>wafEnabled: Enable AWS WAF (default: false)</li>
 *   <li>albAccessLogging: Enable ALB access logs to S3 (default: false)</li>
 *   <li>cloudfront: Enable CloudFront distribution (default: false)</li>
 *   <li>bastionCidr: CIDR for SSH bastion access (default: 10.0.1.0/24)</li>
 * </ul>
 *
 * <p><b>Authentication:</b></p>
 * <ul>
 *   <li>authMode: "none" | "alb-oidc" | "jenkins-oidc" | "application-oidc" (default: none)</li>
 * </ul>
 *
 * <p><b>Cognito (Auto-provision User Pool):</b></p>
 * <ul>
 *   <li>cognitoAutoProvision: Auto-create Cognito User Pool (default: false)</li>
 *   <li>cognitoDomainPrefix: Globally unique domain prefix (required if auto-provisioning)</li>
 *   <li>cognitoUserPoolName: User Pool name (optional)</li>
 *   <li>cognitoMfaEnabled: Enable MFA (default: false)</li>
 *   <li>cognitoMfaMethod: "totp" | "sms" | "both" (default: "both")</li>
 *   <li>cognitoCreateGroups: Create admin/user groups (default: true)</li>
 *   <li>cognitoAdminGroupName: Admin group name (default: "Jenkins-Admins")</li>
 *   <li>cognitoUserGroupName: User group name (default: "Jenkins-Users")</li>
 *   <li>cognitoUserPoolId: Existing User Pool ID (for reuse)</li>
 *   <li>cognitoAppClientId: Existing App Client ID (for reuse)</li>
 *   <li>cognitoInitialAdminEmail: Initial admin user email (optional)</li>
 *   <li>cognitoInitialAdminPhone: Initial admin user phone in E.164 format, e.g., +12025551234 (optional, required for SMS MFA)</li>
 * </ul>
 *
 * <p><b>Manual OIDC (Identity Center, Okta, Auth0):</b></p>
 * <ul>
 *   <li>oidcIssuer: OIDC issuer URL</li>
 *   <li>oidcAuthorizationEndpoint: Authorization endpoint</li>
 *   <li>oidcTokenEndpoint: Token endpoint</li>
 *   <li>oidcUserInfoEndpoint: UserInfo endpoint</li>
 *   <li>oidcClientId: OIDC client ID</li>
 *   <li>oidcClientSecretName: Secrets Manager secret name (default: "jenkins/oidc/client-secret")</li>
 * </ul>
 *
 * <p><b>Legacy IAM Identity Center:</b></p>
 * <ul>
 *   <li>ssoInstanceArn: IAM Identity Center instance ARN</li>
 *   <li>ssoGroupId: Group UUID</li>
 *   <li>ssoTargetAccountId: 12-digit account ID</li>
 *   <li>autoProvisionIdentityCenter: Auto-provision (default: false)</li>
 *   <li>identityCenterGroupName: Group name (default: "Jenkins-Users")</li>
 * </ul>
 *
 * <p><b>Compute &amp; Scaling:</b></p>
 * <ul>
 *   <li>lbType: "alb" | "nlb" (default: alb)</li>
 *   <li>instanceType: EC2 type (default: t3.micro)</li>
 *   <li>cpu: Fargate vCPU units (default: 1024)</li>
 *   <li>memory: Fargate memory MiB (default: 2048)</li>
 *   <li>containerImage: Override container image tag, e.g., "v1.2.3" or "2024.1" (default: uses tag from ApplicationSpec)</li>
 *   <li>minInstanceCapacity: Min instances (default: 1)</li>
 *   <li>maxInstanceCapacity: Max instances (default: 1)</li>
 *   <li>cpuTargetUtilization: CPU target % (default: 60)</li>
 * </ul>
 *
 * <p><b>Monitoring &amp; Compliance:</b></p>
 * <ul>
 *   <li>enableMonitoring: CloudWatch monitoring (default: true)</li>
 *   <li>enableEncryption: Encryption at rest (default: true)</li>
 *   <li>logRetentionDays: CloudWatch log retention (default: security profile default)</li>
 *   <li>awsConfigEnabled: AWS Config compliance (default: false)</li>
 *   <li>complianceMode: "enforce" | "advisory" (auto: enforce for PRODUCTION, advisory for DEV/STAGING)</li>
 *   <li>complianceFrameworks: "PCI-DSS,HIPAA,SOC2,GDPR" (comma-separated)</li>
 * </ul>
 *
 * <p><b>Health Checks:</b></p>
 * <ul>
 *   <li>healthCheckGracePeriod: Grace period seconds (default: 300)</li>
 *   <li>healthCheckInterval: Interval seconds (default: 30)</li>
 *   <li>healthCheckTimeout: Timeout seconds (default: 5)</li>
 *   <li>healthyThreshold: Healthy count (default: 2)</li>
 *   <li>unhealthyThreshold: Unhealthy count (default: 3)</li>
 * </ul>
 *
 * <p><b>Storage:</b></p>
 * <ul>
 *   <li>artifactsBucket: S3 bucket name (optional)</li>
 *   <li>artifactsPrefix: S3 prefix (default: "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}")</li>
 *   <li>retainStorage: Retain EFS/EBS on deletion (default: false)</li>
 *   <li>existingFileSystemId: Reuse existing EFS (disaster recovery)</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // In CDK app
 * DeploymentContext ctx = DeploymentContext.from(app);
 *
 * // In any Construct
 * DeploymentContext ctx = DeploymentContext.from(scope);
 * </pre>
 */
public final class DeploymentContext {

    // Raw map snapshot (frozen)
    private final Map<String, Object> raw;

    // Required-ish high level knobs
    @OneOf(value = {"public", "enterprise"}, message = "Tier must be 'public' or 'enterprise'")
    private final String tier;        // public | enterprise

    @OneOf(value = {"dev", "stage", "prod"}, message = "Environment must be 'dev', 'stage', or 'prod'")
    private final String env;         // dev | stage | prod

    private final SecurityProfile securityProfile; // DEV | STAGING | PRODUCTION
    private final String region;      // default: us-east-1

    // Naming / DNS
    @DnsName(message = "Domain must be a valid DNS name")
    private final String domain;

    @DnsLabel(message = "Subdomain must be a valid DNS label")
    private final String subdomain;
    private final String fqdn;        // computed if not provided

    // Networking
    @OneOf(value = {"public-no-nat", "private-with-nat"}, message = "Network mode must be 'public-no-nat' or 'private-with-nat'")
    private final String networkMode; // public-no-nat | private-with-nat
    private final boolean wafEnabled;
    private final Boolean albAccessLogging;  // Enable ALB access logs to S3
    private final boolean cloudfront;
    @OneOf(value = {"alb", "nlb"}, message = "Load balancer type must be 'alb' or 'nlb'")
    private final String lbType;      // alb | nlb

    // Threat Detection
    private final Boolean guardDutyEnabled;  // Enable GuardDuty for threat detection (PCI-DSS Req 11.4)
    private final Boolean createGuardDutyDetector;  // Create GuardDuty detector (account-region singleton)
    private final Boolean guardDutyAlertsConfigured;  // GuardDuty alerts configured (EventBridge to SNS/SIEM)
    private final Boolean certificateExpirationMonitoring;  // Certificate expiration monitoring enabled (CloudWatch alarms)

    // Security - SSH Access Control
    private final String bastionCidr;  // CIDR for bastion/VPN SSH access (PRODUCTION profile)

    // Storage Persistence Configuration
    private final boolean retainStorage;  // Retain EFS/EBS volumes on stack deletion (agnostic - works for any workload)
    private final String existingFileSystemId;  // Reuse existing EFS by ID (for disaster recovery workflows)

    // Auth / SSO
    @OneOf(value = {"none", "alb-oidc", "jenkins-oidc", "application-oidc"}, message = "Auth mode must be 'none', 'alb-oidc', 'jenkins-oidc', or 'application-oidc'")
    private final String authMode;    // none | alb-oidc | jenkins-oidc | application-oidc

    // Cognito Configuration (recommended for OIDC)
    private final Boolean cognitoAutoProvision;         // Auto-provision Cognito User Pool
    private final String cognitoDomainPrefix;           // Cognito domain prefix (globally unique)
    private final String cognitoUserPoolName;           // User Pool name
    private final Boolean cognitoMfaEnabled;            // Enable MFA
    private final String cognitoMfaMethod;              // MFA method: "totp", "sms", or "both"
    private final Boolean cognitoCreateGroups;          // Create admin and user groups
    private final String cognitoAdminGroupName;         // Admin group name
    private final String cognitoUserGroupName;          // User group name
    private final String cognitoUserPoolId;             // Existing User Pool ID (if not auto-provisioning)
    private final String cognitoAppClientId;            // Existing App Client ID (if not auto-provisioning)
    private final String cognitoInitialAdminEmail;      // Initial admin user email address
    private final String cognitoInitialAdminPhone;      // Initial admin user phone number (E.164 format, e.g., +12025551234)

    // Manual OIDC Configuration (for IAM Identity Center, Okta, Auth0, etc.)
    private final String oidcIssuer;                    // OIDC issuer URL
    private final String oidcAuthorizationEndpoint;     // OIDC authorization endpoint
    private final String oidcTokenEndpoint;             // OIDC token endpoint
    private final String oidcUserInfoEndpoint;          // OIDC userinfo endpoint
    private final String oidcClientId;                  // OIDC client ID
    private final String oidcClientSecretName;          // Secrets Manager secret name for client secret

    // Legacy IAM Identity Center Configuration
    private final String ssoInstanceArn;
    private final String ssoGroupId;
    private final String ssoTargetAccountId;
    private final Boolean autoProvisionIdentityCenter;  // Auto-provision IAM Identity Center
    private final String identityCenterGroupName;       // Group name for auto-provisioned Identity Center

    // Artifacts
    private final String artifactsBucket;
    private final String artifactsPrefix;

    // Auto Scaling
    private final Integer cpuTargetUtilization;
    private final Integer maxInstanceCapacity;
    private final Integer minInstanceCapacity;

    private final boolean enableFlowlogs;
    private final Boolean cloudTrailEnabled;  // Enable CloudTrail for API audit logging

    // Advanced Configuration
    private final boolean enableMonitoring;
    private final boolean enableEncryption;
    private final boolean awsConfigEnabled;
    private final Boolean createConfigInfrastructure;  // Create AWS Config Recorder and Delivery Channel (account-level singletons)
    private final boolean auditManagerEnabled;
    private final String complianceFrameworks;  // Comma-separated list: "PCI-DSS,HIPAA,SOC2,GDPR"
    private final String complianceMode;  // "enforce" | "advisory" (default based on securityProfile)
    private final Integer logRetentionDays;
    private final String instanceType;
    private final Boolean provisionDatabase;  // Whether to provision RDS database for applications with optional database support

    // AWS Config Remediation Settings
    private final Boolean enableS3VersioningRemediation;  // Enable automated S3 versioning remediation
    private final Boolean enableCloudTrailBucketAccessRemediation;  // Enable automated CloudTrail bucket access logging remediation
    private final Boolean enableRdsDeletionProtectionRemediation;  // Enable automated RDS deletion protection remediation
    private final Boolean enableRdsAutoMinorVersionUpgradeRemediation;  // Enable automated RDS auto minor version upgrade remediation

    // Health Check Configuration
    private final Integer healthCheckGracePeriod;
    private final Integer healthCheckInterval;
    private final Integer healthCheckTimeout;
    private final Integer healthyThreshold;
    private final Integer unhealthyThreshold;

    // Container configuration
    private final int cpu;
    private final int memory;
    private final String containerImage;  // Override container image tag (e.g., "v1.2.3" replaces ":latest")

    // Derived conveniences
    private final boolean enableSsl;
    private final boolean createZone;

    // New canonical types
    private final RuntimeType runtime;
    private final TopologyType topology;

    // Legacy raw values (kept for compatibility & logging)
    private final String runtimeRaw;      // may be "ec2"/"fargate" or a legacy combo like "jenkins-fargate"
    private final String topologyRaw;     // if user provided an explicit string topology

    // Additional deployment tracking fields
    private final String deploymentId;
    private final String deploymentVersion;
    private final String tags;
    private final String stackName;

    protected DeploymentContext(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));

        this.tier   = str("tier", "public");
        this.env    = str("env", "dev");
        this.securityProfile = parseSecurityProfile(str("securityProfile", "dev"));
        this.region = str("region", "us-east-1");

        this.domain = str("domain", null);
        this.subdomain = str("subdomain", null);
        String fqdnCtx = str("fqdn", null);
        this.fqdn = (fqdnCtx != null) ? fqdnCtx : composeFqdn(subdomain, domain);

        this.networkMode = oneOf("networkMode", "public-no-nat",
                List.of("public-no-nat", "private-with-nat"));
        this.wafEnabled = bool("wafEnabled", false);
        this.albAccessLogging = boolOrNull("albAccessLogging");
        this.guardDutyEnabled = boolOrNull("guardDutyEnabled");
        this.createGuardDutyDetector = boolOrNull("createGuardDutyDetector");
        this.guardDutyAlertsConfigured = boolOrNull("guardDutyAlertsConfigured");
        this.certificateExpirationMonitoring = boolOrNull("certificateExpirationMonitoring");
        this.cloudfront = bool("cloudfront", false);
        this.lbType = oneOf("lbType", "alb", List.of("alb", "nlb"));

        this.authMode = oneOf("authMode", "none",
                List.of("none", "alb-oidc", "jenkins-oidc", "application-oidc"));

        // Cognito Configuration
        this.cognitoAutoProvision = bool("cognitoAutoProvision", false);
        this.cognitoDomainPrefix = str("cognitoDomainPrefix", null);
        this.cognitoUserPoolName = str("cognitoUserPoolName", null);
        this.cognitoMfaEnabled = bool("cognitoMfaEnabled", false);
        this.cognitoMfaMethod = oneOf("cognitoMfaMethod", "both", List.of("totp", "sms", "both"));
        this.cognitoCreateGroups = bool("cognitoCreateGroups", true);
        this.cognitoAdminGroupName = str("cognitoAdminGroupName", "Jenkins-Admins");
        this.cognitoUserGroupName = str("cognitoUserGroupName", "Jenkins-Users");
        this.cognitoUserPoolId = str("cognitoUserPoolId", null);
        this.cognitoAppClientId = str("cognitoAppClientId", null);
        this.cognitoInitialAdminEmail = str("cognitoInitialAdminEmail", null);
        this.cognitoInitialAdminPhone = str("cognitoInitialAdminPhone", null);

        // Manual OIDC Configuration
        this.oidcIssuer = str("oidcIssuer", null);
        this.oidcAuthorizationEndpoint = str("oidcAuthorizationEndpoint", null);
        this.oidcTokenEndpoint = str("oidcTokenEndpoint", null);
        this.oidcUserInfoEndpoint = str("oidcUserInfoEndpoint", null);
        this.oidcClientId = str("oidcClientId", null);
        this.oidcClientSecretName = str("oidcClientSecretName", null);

        // Legacy IAM Identity Center Configuration
        this.ssoInstanceArn = str("ssoInstanceArn", null);
        this.ssoGroupId = str("ssoGroupId", null);
        this.ssoTargetAccountId = str("ssoTargetAccountId", null);
        this.autoProvisionIdentityCenter = bool("autoProvisionIdentityCenter", false);
        this.identityCenterGroupName = str("identityCenterGroupName", "Jenkins-Users");

        this.artifactsBucket = str("artifactsBucket", null);
        this.artifactsPrefix = str("artifactsPrefix", "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}");

        this.cpu = intval("cpu", 1024);
        this.memory = intval("memory", 2048);
        this.containerImage = str("containerImage", null);  // null = use ApplicationSpec.defaultContainerImage()

        this.minInstanceCapacity = intval("minInstanceCapacity", 1);
        this.maxInstanceCapacity = intval("maxInstanceCapacity", 1);
        this.cpuTargetUtilization = intval("cpuTargetUtilization", 60);

        this.enableFlowlogs = bool("enableFlowlogs", false);
        this.cloudTrailEnabled = boolOrNull("cloudTrailEnabled");

        // Security - SSH Access Control
        this.bastionCidr = str("bastionCidr", "10.0.1.0/24");

        // Storage Persistence Configuration
        this.retainStorage = bool("retainStorage", false);
        this.existingFileSystemId = str("existingFileSystemId", null);

        // Advanced Configuration
        this.enableMonitoring = bool("enableMonitoring", true);
        this.enableEncryption = bool("enableEncryption", true);
        this.awsConfigEnabled = bool("awsConfigEnabled", false);
        this.createConfigInfrastructure = boolOrNull("createConfigInfrastructure");
        this.auditManagerEnabled = bool("auditManagerEnabled", false);
        this.complianceFrameworks = str("complianceFrameworks", "");
        this.complianceMode = str("complianceMode", null);  // null = use default based on securityProfile
        this.logRetentionDays = intval("logRetentionDays", null);  // Default: null (overridden by SecurityProfileConfiguration if needed)
        this.instanceType = str("instanceType", "t3.micro");
        this.provisionDatabase = boolOrNull("provisionDatabase");

        // AWS Config Remediation Settings (all disabled by default, opt-in required)
        this.enableS3VersioningRemediation = boolOrNull("enableS3VersioningRemediation");
        this.enableCloudTrailBucketAccessRemediation = boolOrNull("enableCloudTrailBucketAccessRemediation");
        this.enableRdsDeletionProtectionRemediation = boolOrNull("enableRdsDeletionProtectionRemediation");
        this.enableRdsAutoMinorVersionUpgradeRemediation = boolOrNull("enableRdsAutoMinorVersionUpgradeRemediation");

        // Health Check Configuration
        this.healthCheckGracePeriod = intval("healthCheckGracePeriod", 300);
        this.healthCheckInterval = intval("healthCheckInterval", 30);
        this.healthCheckTimeout = intval("healthCheckTimeout", 5);
        this.healthyThreshold = intval("healthyThreshold", 2);
        this.unhealthyThreshold = intval("unhealthyThreshold", 3);

        // Additional deployment tracking fields
        this.deploymentId = str("deploymentId", null);
        this.deploymentVersion = str("deploymentVersion", null);
        this.tags = str("tags", null);
        this.stackName = str("stackName", null);

        // Legacy/alias inputs
        String runtimeAlias = str("runtime", "fargate");
        this.runtimeRaw = runtimeAlias;
        this.topologyRaw = str("topology", "service");

        // Normalize to enums (supports legacy combos)
        DeploymentConfigurations configurations = process(runtimeAlias, topologyRaw);
        this.runtime = configurations.runtime;
        this.topology = configurations.topology;

        // SSL default remains explicit; do not silently infer on domain unless asked to
        this.enableSsl = bool("enableSsl", false);

        // Zone creation flag - only create hosted zones when explicitly requested
        this.createZone = bool("createZone", false);

        validateOrThrow();
    }

    /** Build from the 'cfc' context object on the App. */
    public static DeploymentContext from(App app) {
        return Util.extractDeploymentContext(app.getNode().tryGetContext("cfc"));
    }

    /** Build from the 'cfc' context object on any Construct scope. */
    public static DeploymentContext from(Construct scope) {
        return Util.extractDeploymentContext(scope.getNode().tryGetContext("cfc"));
    }

    // --------- Public getters ---------

    public String tier() { return tier; }
    public String env() { return env; }

    /**
     * Gets the security profile enum.
     *
     * @return SecurityProfile enum value
     */
    public SecurityProfile securityProfile() {
        return securityProfile;
    }

    public String region() { return region; }

    public String domain() { return domain; }
    public String subdomain() { return subdomain; }
    public String fqdn() { return fqdn; }

    public String networkMode() { return networkMode; }
    public boolean wafEnabled() { return wafEnabled; }
    public Boolean albAccessLogging() { return albAccessLogging; }
    public Boolean guardDutyEnabled() { return guardDutyEnabled; }
    public Boolean createGuardDutyDetector() { return createGuardDutyDetector; }
    public Boolean guardDutyAlertsConfigured() { return guardDutyAlertsConfigured; }
    public Boolean certificateExpirationMonitoring() { return certificateExpirationMonitoring; }
    public boolean cloudfrontEnabled() { return cloudfront; }
    public String lbType() { return lbType; }

    public Integer cpuTargetUtilization() { return cpuTargetUtilization; }
    public Integer maxInstanceCapacity() { return maxInstanceCapacity; }
    public Integer minInstanceCapacity() { return minInstanceCapacity; }

    public boolean enableFlowlogs() { return enableFlowlogs; }
    public Boolean cloudTrailEnabled() { return cloudTrailEnabled; }

    // Security - SSH Access Control
    public String bastionCidr() { return bastionCidr; }

    // Storage Persistence Configuration
    public boolean retainStorage() { return retainStorage; }
    public String existingFileSystemId() { return existingFileSystemId; }

    // Advanced Configuration
    public boolean enableMonitoring() { return enableMonitoring; }
    public boolean enableEncryption() { return enableEncryption; }
    public boolean awsConfigEnabled() { return awsConfigEnabled; }
    public Boolean createConfigInfrastructure() { return createConfigInfrastructure; }
    public boolean auditManagerEnabled() { return auditManagerEnabled; }
    public String complianceFrameworks() { return complianceFrameworks; }
    public String complianceMode() { return complianceMode; }
    public Integer logRetentionDays() { return logRetentionDays; }
    public String instanceType() { return instanceType; }
    public Boolean provisionDatabase() { return provisionDatabase; }
    public Boolean enableS3VersioningRemediation() { return enableS3VersioningRemediation; }
    public Boolean enableCloudTrailBucketAccessRemediation() { return enableCloudTrailBucketAccessRemediation; }
    public Boolean enableRdsDeletionProtectionRemediation() { return enableRdsDeletionProtectionRemediation; }
    public Boolean enableRdsAutoMinorVersionUpgradeRemediation() { return enableRdsAutoMinorVersionUpgradeRemediation; }

    // Health Check Configuration
    public Integer healthCheckGracePeriod() { return healthCheckGracePeriod; }
    public Integer healthCheckInterval() { return healthCheckInterval; }
    public Integer healthCheckTimeout() { return healthCheckTimeout; }
    public Integer healthyThreshold() { return healthyThreshold; }
    public Integer unhealthyThreshold() { return unhealthyThreshold; }

    public String authMode() { return authMode; }

    // Cognito Configuration
    public Boolean cognitoAutoProvision() { return cognitoAutoProvision; }
    public String cognitoDomainPrefix() { return cognitoDomainPrefix; }
    public String cognitoUserPoolName() { return cognitoUserPoolName; }
    public Boolean cognitoMfaEnabled() { return cognitoMfaEnabled; }
    public String cognitoMfaMethod() { return cognitoMfaMethod; }
    public Boolean cognitoCreateGroups() { return cognitoCreateGroups; }
    public String cognitoAdminGroupName() { return cognitoAdminGroupName; }
    public String cognitoUserGroupName() { return cognitoUserGroupName; }
    public String cognitoUserPoolId() { return cognitoUserPoolId; }
    public String cognitoAppClientId() { return cognitoAppClientId; }
    public String cognitoInitialAdminEmail() { return cognitoInitialAdminEmail; }
    public String cognitoInitialAdminPhone() { return cognitoInitialAdminPhone; }

    // Manual OIDC Configuration
    public String oidcIssuer() { return oidcIssuer; }
    public String oidcAuthorizationEndpoint() { return oidcAuthorizationEndpoint; }
    public String oidcTokenEndpoint() { return oidcTokenEndpoint; }
    public String oidcUserInfoEndpoint() { return oidcUserInfoEndpoint; }
    public String oidcClientId() { return oidcClientId; }
    public String oidcClientSecretName() { return oidcClientSecretName; }

    // Legacy IAM Identity Center Configuration
    public String ssoInstanceArn() { return ssoInstanceArn; }
    public String ssoGroupId() { return ssoGroupId; }
    public String ssoTargetAccountId() { return ssoTargetAccountId; }
    public Boolean autoProvisionIdentityCenter() { return autoProvisionIdentityCenter; }
    public String identityCenterGroupName() { return identityCenterGroupName; }

    // Additional deployment tracking fields
    public String deploymentId() { return deploymentId; }
    public String deploymentVersion() { return deploymentVersion; }
    public String tags() { return tags; }
    public String stackName() { return stackName; }

    public String artifactsBucket() { return artifactsBucket; }
    public String artifactsPrefix() { return artifactsPrefix; }

    public int cpu() { return cpu; }
    public int memory() { return memory; }
    public String containerImage() { return containerImage; }

    public boolean enableSsl() { return enableSsl; }
    public boolean createZone() { return createZone; }

    /** Raw immutable view of all context keys. */
    public Map<String, Object> raw() { return raw; }

    /** Canonical axes (preferred). */
    public RuntimeType runtime() { return runtime; }
    public TopologyType topology() { return topology; }

    /** Legacy raw accessors (compat only). */
    @Deprecated public String runtimeRaw() { return runtimeRaw; }
    @Deprecated public String topologyRaw() { return topologyRaw; }

    // --------- Helpers / derived behavior ---------

    /** True if the service should run in private subnets without public IPs. */
    public boolean isPrivateWithNat() { return "private-with-nat".equals(networkMode); }

    /** True if enterprise features should be enabled. */
    public boolean isEnterprise() { return "enterprise".equalsIgnoreCase(tier); }

    /** Get the runtime type. */
    public RuntimeType getRuntime() { return runtime; }

    /** Get the topology type. */
    public TopologyType getTopology() { return topology; }

    /** Get a context value by key with default. */
    public String getContextValue(String key, String defaultValue) {
        return str(key, defaultValue);
    }

    /** Tag a stack so you can see the config in the console. */
    public void tagStack(Stack stack) {
        stack.getTags().setTag("cfc:tier", tier);
        stack.getTags().setTag("cfc:runtime", runtime.name());
        stack.getTags().setTag("cfc:topology", topology.name());
        stack.getTags().setTag("cfc:env", env);
        if (fqdn != null) stack.getTags().setTag("cfc:fqdn", fqdn);
        stack.getTags().setTag("cfc:network", networkMode);
        stack.getTags().setTag("cfc:auth", authMode);
    }

    private void validateOrThrow() {
        List<String> errs = new ArrayList<>();

        // SSL can be enabled without a domain - AWS Private CA will be used for the ALB DNS name
        // No validation needed here - both custom domain SSL and private certificate SSL are valid

        // OIDC modes require HTTPS (enableSsl=true)
        // When no custom domain is configured, AWS Private CA is used for the ALB DNS name
        if ("alb-oidc".equals(authMode) && !enableSsl) {
            errs.add("authMode=alb-oidc requires HTTPS; set enableSsl=true. " +
                    "A custom domain (fqdn/domain) is recommended but not required - " +
                    "without a domain, AWS Private CA will be used for the ALB DNS name.");
        }

        if ("application-oidc".equals(authMode) && !enableSsl) {
            errs.add("authMode=application-oidc requires HTTPS; set enableSsl=true. " +
                    "A custom domain (fqdn/domain) is recommended but not required - " +
                    "without a domain, AWS Private CA will be used for the ALB DNS name.");
        }

        // Cross-axis sanity (context level; rules will also validate)
        // JENKINS_SINGLE_NODE topology removed in 3.0.0 - use JENKINS_SERVICE instead

        if (!errs.isEmpty()) {
            throw new IllegalArgumentException("DeploymentContext validation failed:\n - "
                    + String.join("\n - ", errs));
        }
    }

    // ---- Normalization helpers ----

    private static final class DeploymentConfigurations {
        final RuntimeType runtime;
        final TopologyType topology;
        DeploymentConfigurations(RuntimeType r, TopologyType t) { this.runtime = r; this.topology = t; }
    }

    private static DeploymentConfigurations process(String runtimeAlias, String topologyAlias) {
        RuntimeType runtime = RuntimeType.FARGATE; // default

        TopologyType topology = TopologyType.JENKINS_SERVICE; // conservative default

        // explicit topology string wins if present
        if (topologyAlias != null) {
            topology = parseTopology(topologyAlias);
        }

        if (runtimeAlias != null) {
            String r = runtimeAlias.trim().toLowerCase(Locale.ROOT);
            switch (r) {
                case "ec2" -> { runtime = RuntimeType.EC2;}
                case "fargate" -> { runtime = RuntimeType.FARGATE; }
                case "jenkins-fargate" -> { runtime = RuntimeType.FARGATE; topology = TopologyType.JENKINS_SERVICE; }
                case "jenkins-ec2"     -> { runtime = RuntimeType.EC2;     topology = TopologyType.JENKINS_SERVICE; }
                case "cf-alb-s3"       -> { runtime = RuntimeType.EC2;     topology = TopologyType.S3_WEBSITE; }
                case "cf-alb-proxy"    -> { runtime = RuntimeType.EC2;     topology = TopologyType.JENKINS_SERVICE; }
                default -> { runtime = RuntimeType.FARGATE; topology = TopologyType.JENKINS_SERVICE; }
            }
        }

        return new DeploymentConfigurations(runtime, topology);
    }

    private static SecurityProfile parseSecurityProfile(String val) {
        String s = val.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "dev" -> SecurityProfile.DEV;
            case "staging" -> SecurityProfile.STAGING;
            case "production" -> SecurityProfile.PRODUCTION;
            default -> SecurityProfile.DEV; // Default to DEV
        };
    }

    private static TopologyType parseTopology(String val) {
        String t = val.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        return switch (t) {
            case "jenkins-service", "jenkins_service", "service" -> TopologyType.JENKINS_SERVICE;
            case "s3-website", "s3_website", "s3" -> TopologyType.S3_WEBSITE;
            case "application-service", "application_service", "app-service", "application" -> TopologyType.APPLICATION_SERVICE;
            // CloudForge 3.0.0: No default fallback - explicit topology required
            default -> throw new IllegalArgumentException(
                "Unknown topology '" + val + "'. Valid values: jenkins-service, s3-website, application-service. " +
                "Note: JENKINS_SINGLE_NODE was removed in 3.0.0 - use jenkins-service instead."
            );
        };
    }

    private static String composeFqdn(String sub, String dom) {
        if (dom == null || dom.isBlank()) return null;
        if (sub == null || sub.isBlank()) return dom;
        return sub + "." + dom;
    }

    private String str(String key, String def) {
        Object v = raw.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private String strOrNull(String key) {
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null) return v;
        return null;
    }

    private boolean bool(String key, boolean def) {
        Object v = raw.get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private Boolean boolOrNull(String key) {
        Object v = raw.get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private int intval(String key, int def) {
        Object v = raw.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return def; }
    }

    private Integer intval(String key, Integer def) {
        Object v = raw.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return def; }
    }

    private String oneOf(String key, String def, List<String> allowed) {
        String val = str(key, def);
        if (!allowed.contains(val)) {
            String msg = String.format("Context '%s' must be one of %s (got '%s')",
                    key, allowed, val);
            throw new IllegalArgumentException(msg);
        }
        return val;
    }

    @Override public String toString() {
        return "DeploymentContext{" +
                "runtimeKind = " + runtime +
                ", topologyKind = " + topology +
                ", env = '" + env + '\'' +
                ", fqdn = '" + fqdn + '\'' +
                ", cpu = " + cpu +
                ", memory = " + memory +
                '}';
    }

    static DeploymentContext of(Map<String, Object> raw) {
        return new DeploymentContext(raw);
    }
}
