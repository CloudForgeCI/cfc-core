package com.cloudforgeci.api.core;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.LoadBalancerType;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforgeci.api.compute.CmsLoader;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
 *   <li>topology: "jenkins-service" | "s3-website" | "application-service" | "cms-service"</li>
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
 *   <li>bastionCidr: Retained for backwards compatibility; no longer gates ECS Exec or SSH. Instance access uses SSM Session Manager; Fargate access uses ECS Exec.</li>
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
 *   <li>securityMonitoringEnabled: Enable security monitoring (default: false)</li>
 *   <li>efsEncryptionInTransitEnabled: Enable EFS encryption in transit (default: profile default)</li>
 *   <li>automatedBackupEnabled: Enable automated backups (default: profile default)</li>
 *   <li>crossRegionBackupEnabled: Enable cross-region backups (default: profile default)</li>
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

    // Backing configuration loaded via Jackson (type-safe)
    private final DeploymentConfig config;

    // Raw map snapshot (frozen) - kept for backward compatibility
    private final Map<String, Object> raw;

    // Computed/derived fields not in DeploymentConfig
    private final String fqdn;        // computed if not provided

    // Legacy raw values (kept for compatibility & logging)
    private final String runtimeRaw;      // may be "ec2"/"fargate" or a legacy combo like "jenkins-fargate"
    private final String topologyRaw;     // if user provided an explicit string topology

    // Fields that need special handling beyond DeploymentConfig
    private final String tier;        // public | enterprise (not in DeploymentConfig per user preference)
    private final String env;         // dev | stage | prod (mapped from environment)
    private final String complianceFrameworks;  // String form for backward compatibility
    private final ComplianceMode complianceMode;  // With profile-based default

    protected DeploymentContext(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));

        // Load all configuration via Jackson type-safe deserialization
        this.config = DeploymentConfig.fromMap(raw);

        // Fields not in DeploymentConfig (legacy compatibility)
        this.tier = str("tier", "public");
        this.env = str("env", "dev");

        // Compute FQDN from domain+subdomain if not provided
        String fqdnCtx = str("fqdn", null);
        this.fqdn = (fqdnCtx != null) ? fqdnCtx : composeFqdn(config.subdomain, config.domain);

        // Legacy raw values for compatibility
        String runtimeAlias = str("runtime", "fargate");
        this.runtimeRaw = runtimeAlias;
        this.topologyRaw = str("topology", "service");

        // Handle runtime/topology legacy combos (e.g., "jenkins-fargate")
        if (config.runtime == null || config.topology == null) {
            DeploymentConfigurations configurations = process(runtimeAlias, topologyRaw);
            if (config.runtime == null) {
                config.runtime = configurations.runtime;
            }
            if (config.topology == null) {
                config.topology = configurations.topology;
            }
        }

        // Compliance frameworks string form for backward compatibility
        this.complianceFrameworks = config.getComplianceFrameworksAsString();

        // ComplianceMode with profile-based default
        this.complianceMode = config.complianceMode != null
            ? config.complianceMode
            : ComplianceMode.defaultForProfile(config.securityProfile);

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

    // --------- Public getters (delegate to config) ---------

    public String tier() { return tier; }
    public String env() { return env; }

    /**
     * Gets the security profile enum.
     *
     * @return SecurityProfile enum value
     */
    public SecurityProfile securityProfile() {
        return config.securityProfile;
    }

    public String region() { return config.region; }
    public Boolean gdprDataTransferApproved() { return config.gdprDataTransferApproved; }

    public String domain() { return config.domain; }
    public String subdomain() { return config.subdomain; }
    public String fqdn() { return fqdn; }

    public NetworkMode networkMode() { return config.networkMode; }
    public Boolean wafEnabled() { return config.wafEnabled; }
    public Boolean httpsStrictEnabled() { return config.httpsStrictEnabled; }
    public Boolean albAccessLogging() { return config.albAccessLogging; }
    public Boolean guardDutyEnabled() { return config.guardDutyEnabled; }
    public Boolean createGuardDutyDetector() { return config.createGuardDutyDetector; }
    public Boolean guardDutyAlertsConfigured() { return config.guardDutyAlertsConfigured; }
    public Boolean certificateExpirationMonitoring() { return config.certificateExpirationMonitoring; }

    // Advanced Monitoring & Threat Protection
    public Boolean macieEnabled() { return config.macieEnabled; }
    public Boolean macieAutomatedDiscoveryEnabled() { return config.macieAutomatedDiscovery; }
    public Boolean securityHubEnabled() { return config.securityHubEnabled; }
    public Boolean inspectorEnabled() { return config.inspectorEnabled; }
    public Boolean antiMalwareEnabled() { return config.antiMalwareEnabled; }
    public Boolean fileIntegrityMonitoringEnabled() { return config.fileIntegrityMonitoring; }
    public Boolean containerRuntimeSecurityEnabled() { return config.containerRuntimeSecurity; }
    public Boolean containerImageScanningEnabled() { return config.containerImageScanning; }

    // Enhanced Compliance Controls
    public Boolean cloudWatchLogsKmsEncryptionEnabled() { return config.cloudWatchLogsKmsEncryptionEnabled; }
    public Boolean cloudTrailInsightsEnabled() { return config.cloudTrailInsightsEnabled; }
    public Boolean route53QueryLoggingEnabled() { return config.route53QueryLoggingEnabled; }
    public Boolean s3ObjectLockEnabled() { return config.s3ObjectLockEnabled; }

    public Boolean cloudfrontEnabled() { return config.cloudfrontEnabled; }
    public LoadBalancerType lbType() { return config.lbType; }

    public Integer cpuTargetUtilization() { return config.cpuTargetUtilization; }
    public Integer maxInstanceCapacity() { return config.maxInstanceCapacity; }
    public Integer minInstanceCapacity() { return config.minInstanceCapacity; }

    public Boolean enableFlowlogs() { return config.enableFlowlogs; }
    public Boolean cloudTrailEnabled() { return config.cloudTrailEnabled; }
    public Boolean securityMonitoringEnabled() { return config.securityMonitoringEnabled; }
    public Boolean efsEncryptionInTransitEnabled() { return config.efsEncryptionInTransitEnabled; }
    public Boolean restrictSecurityGroupEgress() { return config.restrictSecurityGroupEgress; }
    public Boolean automatedBackupEnabled() { return config.automatedBackupEnabled; }
    public Boolean crossRegionBackupEnabled() { return config.crossRegionBackupEnabled; }

    // Security - SSH Access Control
    public String bastionCidr() { return config.bastionCidr; }

    // Storage Persistence Configuration
    public Boolean retainStorage() { return config.retainStorage; }
    public String existingFileSystemId() { return config.existingFileSystemId; }

    // Advanced Configuration
    public Boolean enableMonitoring() { return config.enableMonitoring; }
    public Boolean enableEncryption() { return config.enableEncryption; }
    public Boolean awsConfigEnabled() { return config.awsConfigEnabled; }
    public Boolean createConfigInfrastructure() { return config.createConfigInfrastructure; }
    public Boolean auditManagerEnabled() { return config.auditManagerEnabled; }
    public String complianceFrameworks() { return complianceFrameworks; }
    public ComplianceMode complianceMode() { return complianceMode; }
    public Integer logRetentionDays() { return config.logRetentionDays != null ? Integer.parseInt(config.logRetentionDays) : null; }
    public String instanceType() { return config.instanceType; }
    public Boolean provisionDatabase() { return config.provisionDatabase; }
    public Boolean enableS3VersioningRemediation() { return config.enableS3VersioningRemediation; }
    public Boolean enableCloudTrailBucketAccessRemediation() { return config.enableCloudTrailBucketAccessRemediation; }
    public Boolean enableRdsDeletionProtectionRemediation() { return config.enableRdsDeletionProtectionRemediation; }
    public Boolean enableRdsAutoMinorVersionUpgradeRemediation() { return config.enableRdsAutoMinorVersionUpgradeRemediation; }

    // Health Check Configuration
    public Integer healthCheckGracePeriod() { return config.healthCheckGracePeriod; }
    public Integer healthCheckInterval() { return config.healthCheckInterval; }
    public Integer healthCheckTimeout() { return config.healthCheckTimeout; }
    public Integer healthyThreshold() { return config.healthyThreshold; }
    public Integer unhealthyThreshold() { return config.unhealthyThreshold; }

    public AuthMode authMode() { return config.authMode; }

    // Cognito Configuration
    public Boolean cognitoAutoProvision() { return config.cognitoAutoProvision; }
    public String cognitoDomainPrefix() { return config.cognitoDomainPrefix; }
    public String cognitoUserPoolName() { return config.cognitoUserPoolName; }
    public Boolean cognitoMfaEnabled() { return config.cognitoMfaEnabled; }
    public String cognitoMfaMethod() { return config.cognitoMfaMethod; }
    public Boolean cognitoCreateGroups() { return config.cognitoCreateGroups; }
    public String cognitoAdminGroupName() { return config.cognitoAdminGroupName; }
    public String cognitoUserGroupName() { return config.cognitoUserGroupName; }
    public String cognitoUserPoolId() { return config.cognitoUserPoolId; }
    public String cognitoAppClientId() { return config.cognitoAppClientId; }
    public String cognitoInitialAdminEmail() { return config.cognitoInitialAdminEmail; }
    public String cognitoInitialAdminPhone() { return config.cognitoInitialAdminPhone; }

    // Manual OIDC Configuration
    public String oidcIssuer() { return config.oidcIssuer; }
    public String oidcAuthorizationEndpoint() { return config.oidcAuthorizationEndpoint; }
    public String oidcTokenEndpoint() { return config.oidcTokenEndpoint; }
    public String oidcUserInfoEndpoint() { return config.oidcUserInfoEndpoint; }
    public String oidcClientId() { return config.oidcClientId; }
    public String oidcClientSecretName() { return config.oidcClientSecretName; }

    // Legacy IAM Identity Center Configuration
    public String ssoInstanceArn() { return config.ssoInstanceArn; }
    public String ssoGroupId() { return config.ssoGroupId; }
    public String ssoTargetAccountId() { return config.ssoTargetAccountId; }
    public Boolean autoProvisionIdentityCenter() { return config.autoProvisionIdentityCenter; }
    public String identityCenterGroupName() { return config.identityCenterGroupName; }

    // Additional deployment tracking fields - not in config yet, use raw
    public String deploymentId() { return str("deploymentId", null); }
    public String deploymentVersion() { return str("deploymentVersion", null); }
    public String tags() { return str("tags", null); }
    public String stackName() { return config.stackName; }

    public String artifactsBucket() { return config.artifactsBucket; }
    public String artifactsPrefix() { return config.artifactsPrefix; }

    public int cpu() { return config.cpu; }
    public int memory() { return config.memory; }
    public String containerImage() { return config.containerImage; }

    public boolean enableSsl() { return config.enableSsl != null && config.enableSsl; }
    public boolean createZone() { return config.createZone != null && config.createZone; }

    /** Raw immutable view of all context keys. */
    public Map<String, Object> raw() { return raw; }

    /** Canonical axes (preferred). */
    public RuntimeType runtime() { return config.runtime; }
    public TopologyType topology() { return config.topology; }

    /** Legacy raw accessors (compat only). */
    @Deprecated public String runtimeRaw() { return runtimeRaw; }
    @Deprecated public String topologyRaw() { return topologyRaw; }

    // --------- CMS application resolution ---------

    /**
     * Returns the application identifier from the deployment context.
     *
     * <p>Maps to the {@code "application"} key in cdk.json / CLI context.
     * Used with {@link #cmsSpec()} to resolve the full {@link CmsSpec}.</p>
     *
     * <p>Example cdk.json usage:</p>
     * <pre>{@code
     * "cfc": {
     *   "topology": "cms-service",
     *   "application": "wordpress",
     *   "runtime": "fargate"
     * }
     * }</pre>
     *
     * @return the application ID (e.g., "wordpress", "magento") or {@code null} if not set
     */
    public String applicationId() {
        return str("application", null);
    }

    /**
     * Resolves the {@link CmsSpec} for the configured application via {@link CmsLoader}.
     *
     * <p>Returns {@link Optional#empty()} when no {@code "application"} key is set,
     * or when the ID does not match any registered CMS plugin.</p>
     *
     * @return the resolved CmsSpec, or empty if not a CMS deployment
     */
    public Optional<CmsSpec> cmsSpec() {
        String id = applicationId();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return CmsLoader.findById(id);
    }

    // --------- Helpers / derived behavior ---------

    /** True if the service should run in private subnets without public IPs. */
    public boolean isPrivateWithNat() { return config.networkMode == NetworkMode.PRIVATE_WITH_NAT; }

    /** True if enterprise features should be enabled. */
    public boolean isEnterprise() { return "enterprise".equalsIgnoreCase(tier); }

    /** Get the runtime type. */
    public RuntimeType getRuntime() { return config.runtime; }

    /** Get the topology type. */
    public TopologyType getTopology() { return config.topology; }

    /** Get a context value by key with default. */
    public String getContextValue(String key, String defaultValue) {
        return str(key, defaultValue);
    }

    /**
     * Export all deployment context fields to a Map for serialization.
     * This produces the same format as InteractiveDeployer's buildCfcContext.
     * Delegates to DeploymentConfig.toContextMap() for consistency.
     */
    public Map<String, Object> toContextMap() {
        return config.toContextMap();
    }

    /** Tag a stack so you can see the config in the console. */
    public void tagStack(Stack stack) {
        stack.getTags().setTag("cfc:tier", tier);
        stack.getTags().setTag("cfc:runtime", config.runtime.name());
        stack.getTags().setTag("cfc:topology", config.topology.name());
        stack.getTags().setTag("cfc:env", env);
        if (fqdn != null) stack.getTags().setTag("cfc:fqdn", fqdn);
        stack.getTags().setTag("cfc:network", config.networkMode.getValue());
        stack.getTags().setTag("cfc:auth", config.authMode.getValue());
    }

    private void validateOrThrow() {
        List<String> errs = new ArrayList<>();

        // SSL can be enabled without a domain - AWS Private CA will be used for the ALB DNS name
        // No validation needed here - both custom domain SSL and private certificate SSL are valid

        // OIDC modes require HTTPS (enableSsl=true)
        // When no custom domain is configured, AWS Private CA is used for the ALB DNS name
        boolean sslEnabled = config.enableSsl != null && config.enableSsl;
        if (config.authMode == AuthMode.ALB_OIDC && !sslEnabled) {
            errs.add("authMode=alb-oidc requires HTTPS; set enableSsl=true. " +
                    "A custom domain (fqdn/domain) is recommended but not required - " +
                    "without a domain, AWS Private CA will be used for the ALB DNS name.");
        }

        if (config.authMode == AuthMode.APPLICATION_OIDC && !sslEnabled) {
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
                "Unknown topology '" + val + "'. Valid values: jenkins-service, s3-website, application-service, cms-service. " +
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

    @Override public String toString() {
        return "DeploymentContext{" +
                "runtimeKind = " + config.runtime +
                ", topologyKind = " + config.topology +
                ", env = '" + env + '\'' +
                ", fqdn = '" + fqdn + '\'' +
                ", cpu = " + config.cpu +
                ", memory = " + config.memory +
                '}';
    }

    static DeploymentContext of(Map<String, Object> raw) {
        return new DeploymentContext(raw);
    }
}
