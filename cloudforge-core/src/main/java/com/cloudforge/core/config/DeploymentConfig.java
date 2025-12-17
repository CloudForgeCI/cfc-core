package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.annotation.FieldTag;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Universal deployment configuration for CloudForge applications.
 *
 * <p>This is the canonical configuration structure used by both interactive deployers
 * and non-interactive deployment tools. It maps directly to deployment-context.json
 * and can be serialized/deserialized via Jackson.</p>
 *
 * <p><b>Architecture:</b> This class lives in cloudforge-core (the contract layer) as
 * it defines the data model interface between libraries and consumers. This ensures
 * cfc-testing and other consumers always use the latest configuration schema without
 * duplication.</p>
 *
 * @since CloudForge 3.0.0
 */
public class DeploymentConfig {

    // ========== Basic Configuration ==========

    /** CloudFormation stack name */
    public String stackName;

    /** Environment name (e.g., "dev", "staging", "production") */
    public String environment;

    /** Application identifier (e.g., "jenkins", "gitlab", "vault") */
    public String applicationId;

    /** Human-readable application name */
    public String applicationName;

    /** ApplicationSpec instance (not serialized to JSON) */
    @JsonIgnore
    public ApplicationSpec applicationSpec;

    // ========== Application Metadata ==========

    /** Whether application supports Fargate deployment */
    public boolean supportsFargate;

    /** Whether application supports EC2 deployment */
    public boolean supportsEc2;

    /** Whether application supports OIDC integration */
    public boolean supportsOidc;

    // ========== Domain Configuration ==========

    /** Primary domain (e.g., "example.com") */
    public String domain;

    /** Subdomain prefix (e.g., "ci", "gitlab") */
    public String subdomain;

    /** Enable SSL certificate via ACM */
    public boolean enableSsl;

    // ========== Runtime Configuration ==========

    /** Runtime type (FARGATE or EC2) */
    public RuntimeType runtime;

    /** Topology type (APPLICATION_SERVICE, etc.) */
    public TopologyType topology;

    /** Security profile (DEV, STAGING, PRODUCTION) */
    public SecurityProfile securityProfile;

    // ========== Network Configuration ==========

    /** Network mode (e.g., "private-with-nat", "public") */
    public String networkMode;

    /** Enable AWS WAF */
    public boolean wafEnabled;

    /** Enable ALB access logs to S3 */
    public boolean albAccessLogging = false;

    /** Enable CloudFront CDN */
    public boolean cloudfrontEnabled;

    // ========== Resource Configuration ==========

    /** Minimum instance capacity for auto-scaling */
    public int minInstanceCapacity = 1;

    /** Maximum instance capacity for auto-scaling */
    public int maxInstanceCapacity = 1;

    /** CPU target utilization percentage for auto-scaling */
    public int cpuTargetUtilization = 60;

    /** Fargate CPU units (256, 512, 1024, 2048, 4096) */
    public int cpu = 1024;

    /** Fargate memory in MB */
    public int memory = 2048;

    /** EC2 instance type (e.g., "t3.micro", "t3.small") */
    public String instanceType = "t3.micro";

    // ========== OIDC Configuration ==========

    /** OIDC provider (none, cognito, identity-center, external-idp) */
    public String oidcProvider = "none";

    /** Authentication mode (none, alb-oidc, application-oidc) */
    public String authMode = "none";

    /** Auto-provision new Cognito User Pool */
    public boolean cognitoAutoProvision = false;

    /** Cognito User Pool name */
    public String cognitoUserPoolName = null;

    /** Cognito domain prefix (must be globally unique) */
    public String cognitoDomainPrefix = null;

    /** Enable MFA for Cognito */
    public boolean cognitoMfaEnabled = false;

    /** Create admin and user groups in Cognito */
    public boolean cognitoCreateGroups = true;

    /** Admin group name */
    public String cognitoAdminGroupName = null;

    /** User group name */
    public String cognitoUserGroupName = null;

    /** Initial admin email address */
    public String cognitoInitialAdminEmail = null;

    /** Initial admin phone number (E.164 format) */
    public String cognitoInitialAdminPhone = null;

    /** Existing Cognito User Pool ID */
    public String cognitoUserPoolId = null;

    /** Existing Cognito App Client ID */
    public String cognitoAppClientId = null;

    // ========== OIDC Configuration ==========

    /** OIDC issuer URL */
    public String oidcIssuer = null;

    /** OIDC authorization endpoint */
    public String oidcAuthorizationEndpoint = null;

    /** OIDC token endpoint */
    public String oidcTokenEndpoint = null;

    /** OIDC user info endpoint */
    public String oidcUserInfoEndpoint = null;

    /** OIDC client ID */
    public String oidcClientId = null;

    /** OIDC client secret name in Secrets Manager */
    public String oidcClientSecretName = null;

    // ========== Optional Ports Configuration ==========
    // These enable optional services on applications that support them.
    // Ports are NOT exposed by default - must be explicitly enabled.

    /** Enable JNLP build agent port (Jenkins: 50000) */
    public boolean enableAgents = false;

    /** Enable Git SSH port (GitLab: 22, Gitea: 2222) */
    public boolean enableSsh = false;

    /** Enable SMTP email port (Mattermost: 587) */
    public boolean enableSmtp = false;

    /** Enable SMTP TLS email port (Mattermost: 465) */
    public boolean enableSmtps = false;

    /** Enable clustering ports (Mattermost: 8074-8075, Vault: 8201) */
    public boolean enableClustering = false;

    /** Enable container registry port (GitLab: 5050, Nexus: 5000-5002) */
    public boolean enableDockerRegistry = false;

    /** Enable Prometheus metrics port (GitLab: 9090) */
    public boolean enableMetrics = false;

    /** Enable Notary content trust port (Harbor: 4443) */
    public boolean enableNotary = false;

    /** Enable Trivy vulnerability scanner port (Harbor: 8080) */
    public boolean enableTrivy = false;

    /** Enable Redis Sentinel port (Redis: 26379) */
    public boolean enableSentinel = false;

    /** Enable Redis Cluster bus port (Redis: 16379) */
    public boolean enableCluster = false;

    // ========== IAM Identity Center Configuration ==========

    /** Auto-provision SAML application in IAM Identity Center */
    public boolean autoProvisionIdentityCenter = false;

    /** IAM Identity Center (SSO) Instance ARN */
    public String ssoInstanceArn = null;

    /** Identity Center group name for user assignment */
    public String identityCenterGroupName = null;

    // ========== Database Configuration ==========

    /**
     * Provision RDS database for application.
     * Only shown for applications with optional database support (e.g., Metabase, Grafana).
     * Applications requiring database (e.g., Mattermost, GitLab) always provision one.
     */
    @ConfigField(
        displayName = "Provision RDS Database",
        description = "Create managed RDS database for high availability and automatic backups",
        category = "database",
        visibleWhen = "supportsDatabase && !requiresDatabase",
        required = false,
        order = 10
    )
    public boolean provisionDatabase = false;

    /**
     * Database engine (e.g., postgres, mysql, mariadb).
     * Default comes from ApplicationSpec.databaseRequirement().engine()
     */
    @ConfigField(
        displayName = "Database Engine",
        description = "RDS database engine type",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        allowedValues = {"postgres", "mysql", "mariadb", "aurora-postgresql", "aurora-mysql"},
        defaultFrom = "databaseRequirement().engine",
        example = "postgres",
        order = 20
    )
    public String databaseEngine = "postgres";

    /**
     * Database engine version.
     * Default comes from ApplicationSpec.databaseRequirement().version()
     */
    @ConfigField(
        displayName = "Database Version",
        description = "Database engine version (e.g., 15 for PostgreSQL 15)",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        example = "15",
        defaultFrom = "databaseRequirement().version",
        order = 30
    )
    public String databaseVersion = "15";

    /**
     * RDS instance class (e.g., db.t3.small, db.m5.large).
     * DESTRUCTIVE: Changing this requires resource replacement.
     * BILLING_IMPACT: Larger instances cost more.
     */
    @ConfigField(
        displayName = "Database Instance Class",
        description = "RDS instance type - larger instances provide more CPU/memory",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        allowedValues = {
            "db.t3.micro", "db.t3.small", "db.t3.medium", "db.t3.large",
            "db.m5.large", "db.m5.xlarge", "db.m5.2xlarge",
            "db.r5.large", "db.r5.xlarge", "db.r5.2xlarge"
        },
        example = "db.t3.small",
        defaultFrom = "databaseRequirement().instanceClass",
        tags = {FieldTag.DESTRUCTIVE, FieldTag.BILLING_IMPACT},
        order = 40
    )
    public String databaseInstanceClass = "db.t3.small";

    /**
     * Allocated storage in GB.
     * BILLING_IMPACT: More storage costs more.
     */
    @ConfigField(
        displayName = "Database Storage (GB)",
        description = "Allocated storage for RDS database in GB",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        min = 20,
        max = 65536,
        example = "100",
        defaultFrom = "databaseRequirement().allocatedStorageGB",
        tags = {FieldTag.BILLING_IMPACT},
        order = 50
    )
    public Integer databaseAllocatedStorageGB = 20;

    /**
     * Enable Multi-AZ deployment for high availability.
     * BILLING_IMPACT: Multi-AZ doubles database costs.
     */
    @ConfigField(
        displayName = "Multi-AZ Deployment",
        description = "Deploy database across multiple availability zones for high availability",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        tags = {FieldTag.BILLING_IMPACT},
        order = 60
    )
    public boolean databaseMultiAz = false;

    /**
     * Database name.
     * IMMUTABLE: Cannot be changed after creation.
     */
    @ConfigField(
        displayName = "Database Name",
        description = "Initial database name to create",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        pattern = "^[a-zA-Z][a-zA-Z0-9_]{0,62}$",
        example = "appdb",
        defaultFrom = "databaseRequirement().databaseName",
        tags = {FieldTag.IMMUTABLE},
        order = 70
    )
    public String databaseName = "appdb";

    /**
     * Backup retention period in days.
     * Compliance frameworks may override: PCI-DSS (90 days), HIPAA (30 days), SOC2 (14 days).
     */
    @ConfigField(
        displayName = "Backup Retention (Days)",
        description = "Number of days to retain automated backups (0 = disabled)",
        category = "database",
        visibleWhen = "provisionDatabase",
        dependsOn = "provisionDatabase",
        min = 0,
        max = 35,
        example = "7",
        order = 80
    )
    public Integer databaseBackupRetentionDays = 7;

    /** Enable RDS deletion protection remediation */
    public boolean enableRdsDeletionProtectionRemediation = false;

    /** Enable RDS auto minor version upgrade remediation */
    public boolean enableRdsAutoMinorVersionUpgradeRemediation = false;

    // ========== Compliance Configuration ==========

    /** Enable CloudWatch monitoring */
    public boolean enableMonitoring = true;

    /** Enable encryption at rest */
    public boolean enableEncryption = true;

    /** Enable AWS Config */
    public boolean awsConfigEnabled = false;

    /** Create AWS Config infrastructure */
    public boolean createConfigInfrastructure = true;

    /** Enable GuardDuty threat detection */
    public boolean guardDutyEnabled = false;

    /** Create GuardDuty detector (account-region singleton) */
    public boolean createGuardDutyDetector = false;

    /** GuardDuty alerts configured (EventBridge to SNS/SIEM) */
    public boolean guardDutyAlertsConfigured = false;

    /** Certificate expiration monitoring enabled */
    public boolean certificateExpirationMonitoring = false;

    /** Enable CloudTrail for API audit logging */
    public boolean cloudTrailEnabled = false;

    // Advanced Monitoring & Threat Protection
    /** Enable Amazon Macie for PII/PHI discovery (HIPAA/GDPR) */
    public boolean macieEnabled = false;

    /** Enable Macie automated discovery jobs */
    public boolean macieAutomatedDiscovery = false;

    /** Enable AWS Security Hub for centralized security findings */
    public boolean securityHubEnabled = false;

    /** Enable Amazon Inspector for vulnerability scanning */
    public boolean inspectorEnabled = false;

    /** Enable anti-malware scanning */
    public boolean antiMalwareEnabled = false;

    /** Enable file integrity monitoring */
    public boolean fileIntegrityMonitoring = false;

    /** Enable container runtime security monitoring */
    public boolean containerRuntimeSecurity = false;

    /** Enable container image vulnerability scanning */
    public boolean containerImageScanning = false;

    /** Enable AWS Audit Manager */
    public boolean auditManagerEnabled = false;

    /** Compliance frameworks (comma-separated: "soc2,hipaa,pci-dss,gdpr") */
    public String complianceFrameworks = "";

    /**
     * Compliance validation mode controlling how validation failures are handled.
     * - "enforce": Validation failures block synthesis/deployment (PRODUCTION default)
     * - "advisory": Validation failures logged as warnings only (DEV/STAGING default)
     * - "disabled": No compliance validation (not recommended)
     */
    public String complianceMode = "advisory";

    /** CloudWatch Logs retention days */
    public String logRetentionDays = null;

    /** Enable S3 versioning remediation */
    public boolean enableS3VersioningRemediation = false;

    /** Enable CloudTrail bucket access logging remediation */
    public boolean enableCloudTrailBucketAccessRemediation = false;

    // ========== Health Check Configuration ==========

    @ConfigField(
        displayName = "Health Check Grace Period (seconds)",
        description = "Time to wait before starting health checks after container starts. GitLab needs 600s due to database migrations.",
        category = "resources",
        min = 60,
        max = 900,
        defaultFrom = "defaultHealthCheckGracePeriod",
        order = 600
    )
    public int healthCheckGracePeriod = 300;

    @ConfigField(
        displayName = "Health Check Interval (seconds)",
        description = "Time between health checks",
        category = "resources",
        min = 5,
        max = 300,
        order = 610
    )
    public int healthCheckInterval = 30;

    @ConfigField(
        displayName = "Health Check Timeout (seconds)",
        description = "Time to wait for health check response",
        category = "resources",
        min = 2,
        max = 60,
        order = 620
    )
    public int healthCheckTimeout = 5;

    @ConfigField(
        displayName = "Healthy Threshold Count",
        description = "Number of consecutive successful health checks before marking healthy",
        category = "resources",
        min = 1,
        max = 10,
        order = 630
    )
    public int healthyThreshold = 2;

    @ConfigField(
        displayName = "Unhealthy Threshold Count",
        description = "Number of consecutive failed health checks before marking unhealthy",
        category = "resources",
        min = 1,
        max = 10,
        order = 640
    )
    public int unhealthyThreshold = 3;

    // ========== Region Configuration ==========

    /** AWS region (e.g., "us-east-1", "us-west-2") */
    public String region = "us-east-1";

    /**
     * GDPR data transfer approval flag for non-EU deployments.
     * Set to true to confirm that proper data transfer mechanisms are in place
     * (Standard Contractual Clauses, Binding Corporate Rules, etc.) when deploying
     * outside EU regions with GDPR compliance enabled.
     */
    public Boolean gdprDataTransferApproved = false;

    /** Availability zones for deployment */
    public String[] availabilityZones;

    /** Enable auto-scaling */
    public boolean enableAutoScaling = false;
}
