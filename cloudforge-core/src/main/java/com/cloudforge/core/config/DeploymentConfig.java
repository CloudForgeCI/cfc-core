package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.annotation.FieldTag;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.LoadBalancerType;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    @ConfigField(
        displayName = "Stack Name",
        description = "CloudFormation stack name (lowercase alphanumeric with hyphens)",
        category = "basic",
        required = true,
        pattern = "^[a-zA-Z][a-zA-Z0-9-]{0,127}$",
        example = "my-app-stack",
        order = 10
    )
    public String stackName;

    /** Environment name (e.g., "dev", "staging", "production") */
    @ConfigField(
        displayName = "Environment",
        description = "Deployment environment name",
        category = "basic",
        allowedValues = {"dev", "staging", "prod"},
        example = "dev",
        order = 20
    )
    public String environment;

    /** Application identifier (e.g., "jenkins", "gitlab", "vault") */
    @ConfigField(
        displayName = "Application ID",
        description = "Application identifier from available plugins",
        category = "basic",
        required = true,
        order = 5
    )
    public String applicationId;

    /** Human-readable application name */
    @ConfigField(
        displayName = "Application Name",
        description = "Human-readable application display name",
        category = "basic",
        order = 6
    )
    public String applicationName;

    /** ApplicationSpec instance (not serialized to JSON) */
    @JsonIgnore
    public ApplicationSpec applicationSpec;

    // ========== Domain Configuration ==========

    /** Primary domain (e.g., "example.com") */
    @ConfigField(
        displayName = "Domain",
        description = "Primary domain for the application (e.g., example.com)",
        category = "domain",
        pattern = "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$",
        example = "example.com",
        order = 10
    )
    public String domain;

    /** Subdomain prefix (e.g., "ci", "gitlab") */
    @ConfigField(
        displayName = "Subdomain",
        description = "Subdomain prefix (e.g., 'ci' for ci.example.com)",
        category = "domain",
        pattern = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$",
        example = "ci",
        order = 20
    )
    public String subdomain;

    /** Fully qualified domain name (computed from domain+subdomain if not provided) */
    @ConfigField(
        displayName = "FQDN",
        description = "Fully qualified domain name (overrides domain+subdomain)",
        category = "domain",
        order = 25
    )
    @JsonIgnore  // Computed field, not serialized
    public String fqdn;

    /** Enable SSL certificate via ACM */
    @ConfigField(
        displayName = "Enable SSL",
        description = "Enable SSL/TLS with AWS Certificate Manager",
        category = "domain",
        order = 30
    )
    public Boolean enableSsl;

    // ========== Runtime Configuration ==========

    /** Runtime type (FARGATE or EC2) */
    @ConfigField(
        displayName = "Runtime Type",
        description = "Container runtime: FARGATE (serverless) or EC2 (instance-based)",
        category = "basic",
        allowedValues = {"FARGATE", "EC2"},
        required = true,
        order = 30
    )
    public RuntimeType runtime;

    /** Topology type (APPLICATION_SERVICE, etc.) */
    @ConfigField(
        displayName = "Topology",
        description = "Deployment topology pattern",
        category = "basic",
        allowedValues = {"APPLICATION_SERVICE"},
        order = 40
    )
    public TopologyType topology;

    /** Security profile (DEV, STAGING, PRODUCTION) */
    @ConfigField(
        displayName = "Security Profile",
        description = "Security profile determines compliance requirements and defaults",
        category = "security",
        allowedValues = {"DEV", "STAGING", "PRODUCTION"},
        required = true,
        order = 10
    )
    public SecurityProfile securityProfile = SecurityProfile.DEV;

    // ========== Network Configuration ==========

    /** Network mode for VPC topology */
    @ConfigField(
        displayName = "Network Mode",
        description = "VPC network topology: private-with-nat (recommended), public, or isolated",
        category = "network",
        allowedValues = "none,public,private-with-nat",
        example = "private-with-nat",
        order = 10
    )
    public NetworkMode networkMode = NetworkMode.PUBLIC;

    /** Load balancer type */
    @ConfigField(
        displayName = "Load Balancer Type",
        description = "ALB (HTTP/HTTPS) or NLB (TCP/UDP) - ALB required for OIDC",
        category = "network",
        order = 20
    )
    public LoadBalancerType lbType = LoadBalancerType.ALB;

    /** Create Route53 hosted zone */
    @ConfigField(
        displayName = "Create Route53 Zone",
        description = "Create new Route53 hosted zone for the domain",
        category = "domain",
        order = 40
    )
    public Boolean createZone = false;

    /** Enable VPC flow logs */
    @ConfigField(
        displayName = "Enable Flow Logs",
        description = "Enable VPC flow logs for network traffic analysis",
        category = "network",
        order = 30
    )
    public Boolean enableFlowlogs = null;  // Null means security profile determines default

    /** Enable AWS WAF */
    @ConfigField(
        displayName = "Enable WAF",
        description = "Enable AWS Web Application Firewall for ALB protection",
        category = "network",
        visibleWhen = "lbType == alb",
        order = 40
    )
    public Boolean wafEnabled;

    /** HTTPS-only mode (no HTTP listener when SSL enabled) */
    @ConfigField(
        displayName = "HTTPS Strict Mode",
        description = "Disable HTTP listener when SSL enabled (no HTTP→HTTPS redirect)",
        category = "network",
        visibleWhen = "enableSsl == true",
        order = 45
    )
    public Boolean httpsStrictEnabled;

    /** Enable ALB access logs to S3 */
    @ConfigField(
        displayName = "ALB Access Logging",
        description = "Log all ALB requests to S3 for auditing",
        category = "network",
        visibleWhen = "lbType == alb",
        order = 50
    )
    public Boolean albAccessLogging = false;

    /** Enable CloudFront CDN */
    @ConfigField(
        displayName = "Enable CloudFront",
        description = "Enable CloudFront CDN for global content delivery",
        category = "network",
        tags = {FieldTag.BILLING_IMPACT},
        order = 60
    )
    @JsonAlias("cloudfront")
    public Boolean cloudfrontEnabled;

    /** CIDR for bastion/VPN SSH access */
    @ConfigField(
        displayName = "Bastion CIDR",
        description = "CIDR for bastion/VPN SSH access (PRODUCTION profile)",
        category = "network",
        pattern = "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$",
        example = "10.0.1.0/24",
        order = 70
    )
    public String bastionCidr = "10.0.1.0/24";

    // ========== Resource Configuration ==========

    /** Minimum instance capacity for auto-scaling */
    @ConfigField(
        displayName = "Min Instance Capacity",
        description = "Minimum number of instances for auto-scaling",
        category = "resources",
        min = 1,
        max = 100,
        order = 10
    )
    public int minInstanceCapacity = 1;

    /** Maximum instance capacity for auto-scaling */
    @ConfigField(
        displayName = "Max Instance Capacity",
        description = "Maximum number of instances for auto-scaling",
        category = "resources",
        min = 1,
        max = 100,
        validators = {"CapacityValidator"},
        tags = {FieldTag.BILLING_IMPACT},
        order = 20
    )
    public int maxInstanceCapacity = 1;

    /** CPU target utilization percentage for auto-scaling */
    @ConfigField(
        displayName = "CPU Target Utilization (%)",
        description = "Target CPU utilization for auto-scaling triggers",
        category = "resources",
        min = 10,
        max = 90,
        visibleWhen = "maxInstanceCapacity > 1",
        order = 30
    )
    public int cpuTargetUtilization = 60;

    /** Fargate CPU units (256, 512, 1024, 2048, 4096) */
    @ConfigField(
        displayName = "CPU (Fargate units)",
        description = "Fargate CPU units: 256=0.25vCPU, 512=0.5vCPU, 1024=1vCPU, etc.",
        category = "resources",
        allowedValues = {"256", "512", "1024", "2048", "4096", "8192", "16384"},
        visibleWhen = "runtime == FARGATE",
        validators = {"FargateCpuMemoryValidator"},
        tags = {FieldTag.BILLING_IMPACT},
        order = 40
    )
    public int cpu = 1024;

    /** Fargate memory in MB */
    @ConfigField(
        displayName = "Memory (MB)",
        description = "Container memory in MB (must be valid for CPU selection)",
        category = "resources",
        min = 512,
        max = 122880,
        visibleWhen = "runtime == FARGATE",
        validators = {"FargateCpuMemoryValidator"},
        tags = {FieldTag.BILLING_IMPACT},
        order = 50
    )
    public int memory = 2048;

    /** EC2 instance type (e.g., "t3.micro", "t3.small") */
    @ConfigField(
        displayName = "EC2 Instance Type",
        description = "EC2 instance size for compute capacity",
        category = "resources",
        allowedValues = {
            "t3.micro", "t3.small", "t3.medium", "t3.large", "t3.xlarge",
            "t3a.micro", "t3a.small", "t3a.medium", "t3a.large",
            "m5.large", "m5.xlarge", "m5.2xlarge",
            "c5.large", "c5.xlarge", "c5.2xlarge"
        },
        visibleWhen = "runtime == EC2",
        defaultFrom = "defaultInstanceType",
        tags = {FieldTag.BILLING_IMPACT},
        order = 60
    )
    public String instanceType = "t3.micro";

    /** Override container image tag */
    @ConfigField(
        displayName = "Container Image",
        description = "Override container image tag (e.g., 'v1.2.3' or '2024.1')",
        category = "resources",
        example = "v1.2.3",
        order = 65
    )
    public String containerImage;

    // ========== Storage Configuration ==========

    /** Retain EFS/EBS volumes on stack deletion */
    @ConfigField(
        displayName = "Retain Storage",
        description = "Retain EFS/EBS volumes on stack deletion (agnostic - works for any workload)",
        category = "storage",
        order = 10
    )
    public Boolean retainStorage = false;

    /** Reuse existing EFS by ID (for disaster recovery workflows) */
    @ConfigField(
        displayName = "Existing File System ID",
        description = "Reuse existing EFS by ID (for disaster recovery workflows)",
        category = "storage",
        example = "fs-12345678",
        order = 20
    )
    public String existingFileSystemId;

    /** S3 bucket for artifacts */
    @ConfigField(
        displayName = "Artifacts Bucket",
        description = "S3 bucket name for build artifacts",
        category = "storage",
        order = 30
    )
    public String artifactsBucket;

    /** S3 prefix for artifacts */
    @ConfigField(
        displayName = "Artifacts Prefix",
        description = "S3 prefix for build artifacts",
        category = "storage",
        example = "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}",
        order = 40
    )
    public String artifactsPrefix = "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}";

    // ========== Authentication Configuration ==========

    /** Authentication mode */
    @ConfigField(
        displayName = "Authentication Mode",
        description = "Authentication integration type: ALB-level OIDC, application-level, or none",
        category = "security",
        visibleWhen = "supportsOidc",
        order = 20
    )
    public AuthMode authMode = AuthMode.NONE;

    /** OIDC provider (none, cognito, identity-center, external-idp) */
    @ConfigField(
        displayName = "OIDC Provider",
        description = "Identity provider for OIDC authentication",
        category = "security",
        allowedValues = {"none", "cognito", "identity-center", "external-idp"},
        visibleWhen = "authMode != none",
        order = 30
    )
    public String oidcProvider = "none";

    /** Auto-provision new Cognito User Pool */
    @ConfigField(
        displayName = "Auto-Provision Cognito",
        description = "Create a new Cognito User Pool for authentication",
        category = "security",
        visibleWhen = "oidcProvider == cognito",
        order = 40
    )
    public Boolean cognitoAutoProvision = false;

    /** Cognito User Pool name */
    @ConfigField(
        displayName = "Cognito User Pool Name",
        description = "Name for the Cognito User Pool",
        category = "security",
        visibleWhen = "cognitoAutoProvision == true",
        order = 50
    )
    public String cognitoUserPoolName = null;

    /** Cognito domain prefix (must be globally unique) */
    @ConfigField(
        displayName = "Cognito Domain Prefix",
        description = "Globally unique prefix for Cognito hosted UI domain",
        category = "security",
        pattern = "^[a-z][a-z0-9-]{0,62}$",
        visibleWhen = "cognitoAutoProvision == true",
        order = 60
    )
    public String cognitoDomainPrefix = null;

    /** Enable MFA for Cognito */
    @ConfigField(
        displayName = "Enable MFA",
        description = "Require multi-factor authentication for Cognito users",
        category = "security",
        visibleWhen = "cognitoAutoProvision == true",
        order = 70
    )
    public Boolean cognitoMfaEnabled = false;

    /** Cognito MFA method */
    @ConfigField(
        displayName = "MFA Method",
        description = "MFA method: totp (authenticator app), sms, or both",
        category = "security",
        allowedValues = {"totp", "sms", "both"},
        visibleWhen = "cognitoMfaEnabled == true",
        order = 75
    )
    public String cognitoMfaMethod = "both";

    /** Create admin and user groups in Cognito */
    @ConfigField(
        displayName = "Create User Groups",
        description = "Create admin and user groups in Cognito",
        category = "security",
        visibleWhen = "cognitoAutoProvision == true",
        order = 80
    )
    public Boolean cognitoCreateGroups = true;

    /** Admin group name */
    @ConfigField(
        displayName = "Admin Group Name",
        description = "Name for the Cognito admin group",
        category = "security",
        visibleWhen = "cognitoCreateGroups == true",
        order = 90
    )
    public String cognitoAdminGroupName = null;

    /** User group name */
    @ConfigField(
        displayName = "User Group Name",
        description = "Name for the Cognito users group",
        category = "security",
        visibleWhen = "cognitoCreateGroups == true",
        order = 100
    )
    public String cognitoUserGroupName = null;

    /** Initial admin email address */
    @ConfigField(
        displayName = "Initial Admin Email",
        description = "Email address for the first admin user",
        category = "security",
        pattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
        visibleWhen = "cognitoAutoProvision == true",
        example = "admin@example.com",
        order = 110
    )
    public String cognitoInitialAdminEmail = null;

    /** Initial admin phone number (E.164 format) */
    @ConfigField(
        displayName = "Initial Admin Phone",
        description = "Phone number for admin user (E.164 format: +1234567890)",
        category = "security",
        pattern = "^\\+[1-9]\\d{1,14}$",
        visibleWhen = "cognitoMfaEnabled == true",
        example = "+15551234567",
        order = 120
    )
    public String cognitoInitialAdminPhone = null;

    /** Existing Cognito User Pool ID */
    @ConfigField(
        displayName = "Existing User Pool ID",
        description = "ID of existing Cognito User Pool to use",
        category = "security",
        visibleWhen = "oidcProvider == cognito && cognitoAutoProvision == false",
        order = 130
    )
    public String cognitoUserPoolId = null;

    /** Existing Cognito App Client ID */
    @ConfigField(
        displayName = "Existing App Client ID",
        description = "ID of existing Cognito App Client to use",
        category = "security",
        visibleWhen = "oidcProvider == cognito && cognitoAutoProvision == false",
        order = 140
    )
    public String cognitoAppClientId = null;

    // ========== External OIDC Configuration ==========

    /** OIDC issuer URL */
    @ConfigField(
        displayName = "OIDC Issuer URL",
        description = "Issuer URL from your identity provider (e.g., https://login.example.com)",
        category = "security",
        visibleWhen = "oidcProvider == external-idp",
        pattern = "^https://.*$",
        example = "https://login.example.com",
        order = 200
    )
    public String oidcIssuer = null;

    /** OIDC authorization endpoint */
    @ConfigField(
        displayName = "Authorization Endpoint",
        description = "OIDC authorization endpoint URL",
        category = "security",
        visibleWhen = "oidcProvider == external-idp",
        pattern = "^https://.*$",
        order = 210
    )
    public String oidcAuthorizationEndpoint = null;

    /** OIDC token endpoint */
    @ConfigField(
        displayName = "Token Endpoint",
        description = "OIDC token endpoint URL",
        category = "security",
        visibleWhen = "oidcProvider == external-idp",
        pattern = "^https://.*$",
        order = 220
    )
    public String oidcTokenEndpoint = null;

    /** OIDC user info endpoint */
    @ConfigField(
        displayName = "User Info Endpoint",
        description = "OIDC user info endpoint URL",
        category = "security",
        visibleWhen = "oidcProvider == external-idp",
        pattern = "^https://.*$",
        order = 230
    )
    public String oidcUserInfoEndpoint = null;

    /** OIDC client ID */
    @ConfigField(
        displayName = "OIDC Client ID",
        description = "Client ID from your identity provider",
        category = "security",
        visibleWhen = "oidcProvider == external-idp",
        order = 240
    )
    public String oidcClientId = null;

    /** OIDC client secret name in Secrets Manager */
    @ConfigField(
        displayName = "Client Secret (Secrets Manager)",
        description = "Name of the secret in AWS Secrets Manager containing the client secret",
        category = "security",
        visibleWhen = "oidcProvider == external-idp",
        sensitive = true,
        order = 250
    )
    public String oidcClientSecretName = null;

    // ========== Optional Ports Configuration ==========
    // These enable optional services on applications that support them.
    // Ports are NOT exposed by default - must be explicitly enabled.

    /** Enable JNLP build agent port (Jenkins: 50000) */
    @ConfigField(
        displayName = "Enable Build Agents",
        description = "Enable JNLP build agent port for distributed builds (Jenkins: 50000)",
        category = "ports",
        visibleWhen = "supportsAgents",
        order = 10
    )
    public boolean enableAgents = false;

    /** Enable Git SSH port (GitLab: 22, Gitea: 2222) */
    @ConfigField(
        displayName = "Enable Git SSH",
        description = "Enable Git SSH access for repository cloning (GitLab: 22, Gitea: 2222)",
        category = "ports",
        visibleWhen = "supportsSsh",
        order = 20
    )
    public boolean enableSsh = false;

    /** Enable SMTP email port (Mattermost: 587) */
    @ConfigField(
        displayName = "Enable SMTP",
        description = "Enable SMTP port for outbound email (port 587)",
        category = "ports",
        visibleWhen = "supportsSmtp",
        order = 30
    )
    public boolean enableSmtp = false;

    /** Enable SMTP TLS email port (Mattermost: 465) */
    @ConfigField(
        displayName = "Enable SMTPS",
        description = "Enable SMTP over TLS port for secure outbound email (port 465)",
        category = "ports",
        visibleWhen = "supportsSmtps",
        order = 40
    )
    public boolean enableSmtps = false;

    /** Enable clustering ports (Mattermost: 8074-8075, Vault: 8201) */
    @ConfigField(
        displayName = "Enable Clustering",
        description = "Enable inter-node clustering ports for high availability",
        category = "ports",
        visibleWhen = "supportsClustering",
        order = 50
    )
    public boolean enableClustering = false;

    /** Enable container registry port (GitLab: 5050, Nexus: 5000-5002) */
    @ConfigField(
        displayName = "Enable Docker Registry",
        description = "Enable container registry ports (GitLab: 5050, Nexus: 5000-5002)",
        category = "ports",
        visibleWhen = "supportsDockerRegistry",
        order = 60
    )
    public boolean enableDockerRegistry = false;

    /** Enable Prometheus metrics port (GitLab: 9090) */
    @ConfigField(
        displayName = "Enable Metrics Port",
        description = "Enable Prometheus metrics endpoint port (typically 9090)",
        category = "ports",
        visibleWhen = "supportsMetrics",
        order = 70
    )
    public boolean enableMetrics = false;

    /** Enable Notary content trust port (Harbor: 4443) */
    @ConfigField(
        displayName = "Enable Notary",
        description = "Enable Docker Content Trust Notary port (Harbor: 4443)",
        category = "ports",
        visibleWhen = "supportsNotary",
        order = 80
    )
    public boolean enableNotary = false;

    /** Enable Trivy vulnerability scanner port (Harbor: 8080) */
    @ConfigField(
        displayName = "Enable Trivy Scanner",
        description = "Enable Trivy vulnerability scanner port (Harbor: 8080)",
        category = "ports",
        visibleWhen = "supportsTrivy",
        order = 90
    )
    public boolean enableTrivy = false;

    /** Enable Redis Sentinel port (Redis: 26379) */
    @ConfigField(
        displayName = "Enable Sentinel",
        description = "Enable Redis Sentinel port for HA monitoring (port 26379)",
        category = "ports",
        visibleWhen = "supportsSentinel",
        order = 100
    )
    public boolean enableSentinel = false;

    /** Enable Redis Cluster bus port (Redis: 16379) */
    @ConfigField(
        displayName = "Enable Cluster Bus",
        description = "Enable Redis Cluster bus port for cluster gossip (port 16379)",
        category = "ports",
        visibleWhen = "supportsClusterBus",
        order = 110
    )
    public boolean enableCluster = false;

    // ========== IAM Identity Center Configuration ==========

    /** Auto-provision SAML application in IAM Identity Center */
    @ConfigField(
        displayName = "Auto-Provision Identity Center",
        description = "Create SAML application in IAM Identity Center",
        category = "security",
        visibleWhen = "oidcProvider == identity-center",
        order = 260
    )
    public Boolean autoProvisionIdentityCenter = false;

    /** IAM Identity Center (SSO) Instance ARN */
    @ConfigField(
        displayName = "SSO Instance ARN",
        description = "ARN of IAM Identity Center instance",
        category = "security",
        visibleWhen = "oidcProvider == identity-center",
        order = 270
    )
    public String ssoInstanceArn = null;

    /** SSO Group ID */
    @ConfigField(
        displayName = "SSO Group ID",
        description = "IAM Identity Center group UUID",
        category = "security",
        visibleWhen = "oidcProvider == identity-center",
        order = 275
    )
    public String ssoGroupId = null;

    /** SSO Target Account ID */
    @ConfigField(
        displayName = "SSO Target Account ID",
        description = "12-digit AWS account ID for SSO target",
        category = "security",
        pattern = "^\\d{12}$",
        visibleWhen = "oidcProvider == identity-center",
        order = 278
    )
    public String ssoTargetAccountId = null;

    /** Identity Center group name for user assignment */
    @ConfigField(
        displayName = "Identity Center Group",
        description = "IAM Identity Center group for user assignment",
        category = "security",
        visibleWhen = "oidcProvider == identity-center",
        order = 280
    )
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
    public Boolean provisionDatabase = false;

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
    public Boolean databaseMultiAz = false;

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
    @ConfigField(
        displayName = "RDS Deletion Protection Remediation",
        description = "Enable automatic remediation of RDS deletion protection compliance violations",
        category = "compliance",
        visibleWhen = "provisionDatabase && awsConfigEnabled",
        order = 400
    )
    public Boolean enableRdsDeletionProtectionRemediation = false;

    /** Enable RDS auto minor version upgrade remediation */
    @ConfigField(
        displayName = "RDS Auto Minor Version Upgrade Remediation",
        description = "Enable automatic remediation of RDS auto minor version upgrade compliance violations",
        category = "compliance",
        visibleWhen = "provisionDatabase && awsConfigEnabled",
        order = 410
    )
    public Boolean enableRdsAutoMinorVersionUpgradeRemediation = false;

    // ========== Compliance Configuration ==========

    /**
     * Compliance frameworks to enable.
     *
     * <p>Supports comma-separated string format in JSON for backward compatibility:
     * <pre>{"complianceFrameworks": "soc2,pci-dss,hipaa"}</pre>
     *
     * <p>In Java code, use the type-safe List:
     * <pre>config.complianceFrameworks.contains(ComplianceFrameworkType.HIPAA)</pre>
     */
    @ConfigField(
        displayName = "Compliance Frameworks",
        description = "Compliance frameworks to enable (soc2, pci-dss, hipaa, gdpr)",
        category = "security",
        example = "soc2,pci-dss",
        order = 300
    )
    @JsonDeserialize(using = ComplianceFrameworkListConverter.Deserializer.class)
    @JsonSerialize(using = ComplianceFrameworkListConverter.Serializer.class)
    public List<ComplianceFrameworkType> complianceFrameworks = new ArrayList<>();

    /**
     * Compliance validation mode controlling how validation failures are handled.
     */
    @ConfigField(
        displayName = "Compliance Mode",
        description = "How to handle compliance validation failures",
        category = "security",
        allowedValues = {"enforce", "advisory", "disabled"},
        order = 310
    )
    public ComplianceMode complianceMode = ComplianceMode.ADVISORY;

    /** CloudWatch Logs retention days */
    @ConfigField(
        displayName = "Log Retention (Days)",
        description = "CloudWatch Logs retention period in days",
        category = "monitoring",
        allowedValues = {"1", "3", "5", "7", "14", "30", "60", "90", "120", "150", "180", "365", "400", "545", "731", "1827", "3653"},
        order = 10
    )
    public String logRetentionDays = null;

    // ========== Monitoring Configuration ==========

    /** Enable CloudWatch monitoring */
    @ConfigField(
        displayName = "Enable Monitoring",
        description = "Enable CloudWatch metrics and alarms",
        category = "monitoring",
        order = 20
    )
    public Boolean enableMonitoring = true;

    /** Enable encryption at rest */
    @ConfigField(
        displayName = "Enable Encryption",
        description = "Enable encryption at rest for all resources",
        category = "security",
        order = 320
    )
    public Boolean enableEncryption = true;

    /** Enable AWS Config */
    @ConfigField(
        displayName = "Enable AWS Config",
        description = "Enable AWS Config for configuration compliance monitoring",
        category = "monitoring",
        order = 30
    )
    public Boolean awsConfigEnabled = false;

    /** Create AWS Config infrastructure */
    @ConfigField(
        displayName = "Create Config Infrastructure",
        description = "Create AWS Config recorder and delivery channel (only one per region)",
        category = "monitoring",
        visibleWhen = "awsConfigEnabled == true",
        order = 40
    )
    public Boolean createConfigInfrastructure = true;

    /** Enable GuardDuty threat detection */
    @ConfigField(
        displayName = "Enable GuardDuty",
        description = "Enable Amazon GuardDuty for threat detection",
        category = "monitoring",
        order = 50
    )
    public Boolean guardDutyEnabled = false;

    /** Create GuardDuty detector (account-region singleton) */
    @ConfigField(
        displayName = "Create GuardDuty Detector",
        description = "Create GuardDuty detector (only one per account/region)",
        category = "monitoring",
        visibleWhen = "guardDutyEnabled == true",
        order = 60
    )
    public Boolean createGuardDutyDetector = false;

    /** GuardDuty alerts configured (EventBridge to SNS/SIEM) */
    @ConfigField(
        displayName = "Configure GuardDuty Alerts",
        description = "Configure EventBridge rules to forward GuardDuty findings to SNS/SIEM",
        category = "monitoring",
        visibleWhen = "guardDutyEnabled == true",
        order = 65
    )
    public Boolean guardDutyAlertsConfigured = false;

    /** Certificate expiration monitoring enabled */
    @ConfigField(
        displayName = "Certificate Expiration Monitoring",
        description = "Enable CloudWatch alarms for ACM certificate expiration",
        category = "monitoring",
        visibleWhen = "enableSsl == true",
        order = 68
    )
    public Boolean certificateExpirationMonitoring = false;

    /** Enable CloudTrail for API audit logging */
    @ConfigField(
        displayName = "Enable CloudTrail",
        description = "Enable CloudTrail for API audit logging",
        category = "monitoring",
        order = 70
    )
    public Boolean cloudTrailEnabled = false;

    /** Enable security monitoring */
    @ConfigField(
        displayName = "Security Monitoring",
        description = "Enable security monitoring features",
        category = "monitoring",
        order = 72
    )
    public Boolean securityMonitoringEnabled = false;

    /** Enable EFS encryption in transit */
    @ConfigField(
        displayName = "EFS Encryption in Transit",
        description = "Enable EFS encryption in transit for data protection",
        category = "security",
        order = 340
    )
    public Boolean efsEncryptionInTransitEnabled = true;

    /** Restrict security group egress to VPC CIDR only (requires VPC endpoints for AWS service access) */
    @ConfigField(
        displayName = "Restrict Security Group Egress",
        description = "Restrict security group egress to VPC CIDR only. Requires VPC endpoints for AWS services (CloudWatch, RDS monitoring). If false, allows 0.0.0.0/0 egress (default CDK behavior).",
        category = "security",
        order = 345
    )
    public Boolean restrictSecurityGroupEgress = false;

    /** Enable automated backups (null = use security profile default) */
    @ConfigField(
        displayName = "Automated Backups",
        description = "Enable automated backups for EFS and databases (null = profile default: PRODUCTION=true, others=false)",
        category = "storage",
        order = 50
    )
    public Boolean automatedBackupEnabled = null;

    /** Enable cross-region backups (null = use security profile default) */
    @ConfigField(
        displayName = "Cross-Region Backups",
        description = "Enable cross-region backup replication for disaster recovery (null = profile default)",
        category = "storage",
        tags = {FieldTag.BILLING_IMPACT},
        order = 60
    )
    public Boolean crossRegionBackupEnabled = null;

    // ========== Advanced Monitoring & Threat Protection ==========

    /** Enable Amazon Macie for PII/PHI discovery (HIPAA/GDPR) */
    @ConfigField(
        displayName = "Enable Macie",
        description = "Enable Amazon Macie for PII/PHI discovery (required for HIPAA/GDPR)",
        category = "monitoring",
        tags = {FieldTag.BILLING_IMPACT},
        order = 80
    )
    public Boolean macieEnabled = false;

    /** Enable Macie automated discovery jobs */
    @ConfigField(
        displayName = "Macie Auto-Discovery",
        description = "Enable Macie automated discovery jobs for continuous scanning",
        category = "monitoring",
        visibleWhen = "macieEnabled == true",
        tags = {FieldTag.BILLING_IMPACT},
        order = 90
    )
    public Boolean macieAutomatedDiscovery = false;

    /** Enable AWS Security Hub for centralized security findings */
    @ConfigField(
        displayName = "Enable Security Hub",
        description = "Enable AWS Security Hub for centralized security findings",
        category = "monitoring",
        order = 100
    )
    public Boolean securityHubEnabled = false;

    /** Enable Amazon Inspector for vulnerability scanning */
    @ConfigField(
        displayName = "Enable Inspector",
        description = "Enable Amazon Inspector for vulnerability scanning",
        category = "monitoring",
        order = 110
    )
    public Boolean inspectorEnabled = false;

    /** Enable anti-malware scanning */
    @ConfigField(
        displayName = "Anti-Malware Scanning",
        description = "Enable anti-malware scanning for uploaded files",
        category = "monitoring",
        order = 120
    )
    public Boolean antiMalwareEnabled = false;

    /** Enable file integrity monitoring */
    @ConfigField(
        displayName = "File Integrity Monitoring",
        description = "Monitor critical files for unauthorized changes",
        category = "monitoring",
        order = 130
    )
    public Boolean fileIntegrityMonitoring = false;

    /** Enable container runtime security monitoring */
    @ConfigField(
        displayName = "Container Runtime Security",
        description = "Monitor container runtime for suspicious activity",
        category = "monitoring",
        order = 140
    )
    public Boolean containerRuntimeSecurity = false;

    /** Enable container image vulnerability scanning */
    @ConfigField(
        displayName = "Container Image Scanning",
        description = "Scan container images for known vulnerabilities",
        category = "monitoring",
        order = 150
    )
    public Boolean containerImageScanning = false;

    /** Enable AWS Audit Manager */
    @ConfigField(
        displayName = "Enable Audit Manager",
        description = "Enable AWS Audit Manager for compliance evidence collection",
        category = "monitoring",
        tags = {FieldTag.BILLING_IMPACT},
        order = 160
    )
    public Boolean auditManagerEnabled = false;

    /** Enable CloudWatch Logs KMS encryption */
    @ConfigField(
        displayName = "CloudWatch Logs KMS Encryption",
        description = "Encrypt CloudWatch Logs with customer-managed KMS keys (required for PCI-DSS, HIPAA, SOC2)",
        category = "monitoring",
        tags = {FieldTag.BILLING_IMPACT},
        order = 170
    )
    public Boolean cloudWatchLogsKmsEncryptionEnabled = false;

    /** Enable CloudTrail Insights */
    @ConfigField(
        displayName = "CloudTrail Insights",
        description = "Enable CloudTrail Insights for API activity anomaly detection (required for SOC2, NIST)",
        category = "monitoring",
        tags = {FieldTag.BILLING_IMPACT},
        visibleWhen = "cloudTrailEnabled == true",
        order = 180
    )
    public Boolean cloudTrailInsightsEnabled = false;

    /** Enable Route53 Query Logging */
    @ConfigField(
        displayName = "Route53 Query Logging",
        description = "Enable DNS query logging for Route53 hosted zones (required for SOC2, NIST)",
        category = "monitoring",
        order = 190
    )
    public Boolean route53QueryLoggingEnabled = false;

    /** Enable S3 Object Lock for audit buckets (HIPAA/PCI-DSS immutability requirement) */
    @ConfigField(
        displayName = "S3 Object Lock",
        description = "Enable S3 Object Lock for compliance audit buckets to ensure immutability (HIPAA § 164.312(c)(1), PCI-DSS Req 10.7)",
        category = "compliance",
        order = 195
    )
    public Boolean s3ObjectLockEnabled = false;

    /** Enable S3 versioning remediation */
    @ConfigField(
        displayName = "S3 Versioning Remediation",
        description = "Enable automatic remediation of S3 versioning compliance violations",
        category = "compliance",
        visibleWhen = "awsConfigEnabled == true",
        order = 420
    )
    public Boolean enableS3VersioningRemediation = false;

    /** Enable CloudTrail bucket access logging remediation */
    @ConfigField(
        displayName = "CloudTrail Bucket Access Logging Remediation",
        description = "Enable automatic remediation of CloudTrail S3 bucket access logging violations",
        category = "compliance",
        visibleWhen = "awsConfigEnabled == true && cloudTrailEnabled == true",
        order = 430
    )
    public Boolean enableCloudTrailBucketAccessRemediation = false;

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
    @ConfigField(
        displayName = "AWS Region",
        description = "AWS region for deployment",
        category = "basic",
        allowedValues = {
            "us-east-1", "us-east-2", "us-west-1", "us-west-2",
            "eu-west-1", "eu-west-2", "eu-west-3", "eu-central-1", "eu-north-1",
            "ap-northeast-1", "ap-northeast-2", "ap-southeast-1", "ap-southeast-2",
            "ap-south-1", "sa-east-1", "ca-central-1"
        },
        required = true,
        order = 50
    )
    public String region = "us-east-1";

    /**
     * GDPR data transfer approval flag for non-EU deployments.
     */
    @ConfigField(
        displayName = "GDPR Data Transfer Approved",
        description = "Confirm proper data transfer mechanisms (SCCs, BCRs) are in place for non-EU deployments with GDPR",
        category = "security",
        visibleWhen = "complianceFrameworks contains gdpr",
        order = 330
    )
    public Boolean gdprDataTransferApproved = false;

    /** Availability zones for deployment */
    @ConfigField(
        displayName = "Availability Zones",
        description = "Specific availability zones for deployment (leave empty for automatic selection)",
        category = "network",
        example = "us-east-1a,us-east-1b",
        order = 5
    )
    public String[] availabilityZones;

    /** Enable auto-scaling */
    @ConfigField(
        displayName = "Enable Auto-Scaling",
        description = "Enable automatic scaling based on CPU utilization",
        category = "resources",
        order = 5
    )
    public Boolean enableAutoScaling = false;

    // ========== JSON Serialization/Deserialization ==========

    /**
     * Creates a pre-configured ObjectMapper for DeploymentConfig serialization.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Field visibility (not getters) for public field serialization</li>
     *   <li>Enums serialized as strings</li>
     *   <li>Unknown properties ignored for forward compatibility</li>
     *   <li>Null values excluded from output</li>
     * </ul>
     */
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .build();
        return mapper;
    }

    /**
     * Load DeploymentConfig from a JSON file (e.g., deployment-context.json).
     *
     * @param path Path to the JSON file
     * @return DeploymentConfig populated from JSON
     * @throws IOException if file cannot be read or parsed
     */
    public static DeploymentConfig fromFile(Path path) throws IOException {
        return createMapper().readValue(path.toFile(), DeploymentConfig.class);
    }

    /**
     * Load DeploymentConfig from a JSON file path string.
     *
     * @param filePath Path to the JSON file
     * @return DeploymentConfig populated from JSON
     * @throws IOException if file cannot be read or parsed
     */
    public static DeploymentConfig fromFile(String filePath) throws IOException {
        return fromFile(Path.of(filePath));
    }

    /**
     * Load DeploymentConfig from a JSON string.
     *
     * @param json JSON string
     * @return DeploymentConfig populated from JSON
     * @throws JsonProcessingException if JSON cannot be parsed
     */
    public static DeploymentConfig fromJson(String json) throws JsonProcessingException {
        return createMapper().readValue(json, DeploymentConfig.class);
    }

    /**
     * Load DeploymentConfig from a Map (e.g., CDK context).
     *
     * <p>Uses Jackson's type-safe conversion to handle:
     * <ul>
     *   <li>String → Enum conversion via @JsonCreator methods</li>
     *   <li>String/Number → Boolean conversion (supports "1", "yes", "0", "no")</li>
     *   <li>Comma-separated strings → List conversion (complianceFrameworks)</li>
     *   <li>Unknown properties are ignored for forward compatibility</li>
     * </ul>
     *
     * @param map Map containing configuration key-value pairs
     * @return DeploymentConfig populated from the map
     */
    public static DeploymentConfig fromMap(java.util.Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new DeploymentConfig();
        }

        // Normalize the map: handle CDK context key rename and boolean string coercion
        java.util.Map<String, Object> normalized = new java.util.LinkedHashMap<>(map);
        if (normalized.containsKey("env") && !normalized.containsKey("environment")) {
            normalized.put("environment", normalized.get("env"));
        }

        // Coerce boolean-like string values before Jackson processing
        normalized.replaceAll((key, value) -> coerceBooleanIfNeeded(value));

        return createMapper().convertValue(normalized, DeploymentConfig.class);
    }

    /**
     * Coerces common boolean-like string values to actual Boolean objects.
     * Supports: "1", "yes", "on" → true; "0", "no", "off" → false.
     */
    private static Object coerceBooleanIfNeeded(Object value) {
        if (value instanceof String str) {
            String lower = str.trim().toLowerCase();
            return switch (lower) {
                case "1", "yes", "on" -> Boolean.TRUE;
                case "0", "no", "off" -> Boolean.FALSE;
                default -> value; // Keep original string for Jackson to parse
            };
        }
        return value;
    }

    /**
     * Serialize this DeploymentConfig to a JSON string.
     *
     * @return JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public String toJson() throws JsonProcessingException {
        return createMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
    }

    /**
     * Save this DeploymentConfig to a JSON file.
     *
     * @param path Path to write the JSON file
     * @throws IOException if file cannot be written
     */
    public void toFile(Path path) throws IOException {
        createMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    /**
     * Save this DeploymentConfig to a JSON file path string.
     *
     * @param filePath Path to write the JSON file
     * @throws IOException if file cannot be written
     */
    public void toFile(String filePath) throws IOException {
        toFile(Path.of(filePath));
    }

    // ========== Compliance Framework Helpers ==========

    /**
     * Returns the compliance frameworks as a comma-separated string.
     * Provided for backward compatibility with code expecting the old string format.
     *
     * @return comma-separated framework string (e.g., "soc2,pci-dss,hipaa")
     */
    public String getComplianceFrameworksAsString() {
        return ComplianceFrameworkType.toCommaSeparated(complianceFrameworks);
    }

    /**
     * Checks if a specific compliance framework is enabled.
     *
     * @param framework the framework to check
     * @return true if the framework is in the list
     */
    public boolean hasComplianceFramework(ComplianceFrameworkType framework) {
        return complianceFrameworks != null && complianceFrameworks.contains(framework);
    }

    /**
     * Checks if any compliance framework is enabled.
     *
     * @return true if at least one framework is configured
     */
    public boolean hasAnyComplianceFramework() {
        return complianceFrameworks != null && !complianceFrameworks.isEmpty();
    }

    /**
     * Convert this DeploymentConfig to a Map for CDK context.
     *
     * <p>Special handling:
     * <ul>
     *   <li>Renames "environment" to "env" for CDK compatibility</li>
     *   <li>Excludes null values</li>
     * </ul>
     *
     * @return Map suitable for CDK App context
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> toContextMap() {
        ObjectMapper mapper = createMapper();
        java.util.Map<String, Object> context = mapper.convertValue(this, java.util.Map.class);

        // CDK uses "env" not "environment"
        if (context.containsKey("environment")) {
            context.put("env", context.remove("environment"));
        }

        return context;
    }
}
