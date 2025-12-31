package com.cloudforgeci.samples.app;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.config.ApplicationInfo;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InteractiveDeployer utility methods.
 *
 * Tests focus on:
 * 1. Context building and field mapping
 * 2. Application metadata and selection
 * 3. OIDC configuration
 * 4. Truth table logic validation
 */
class InteractiveDeployerTest {

    /**
     * Helper method to access buildCfcContext via reflection since it's private.
     */
    private Map<String, Object> buildCfcContext(DeploymentConfig config) throws Exception {
        var method = InteractiveDeployer.class.getDeclaredMethod("buildCfcContext", DeploymentConfig.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(null, config);
    }

    /**
     * Helper to create minimal config.
     */
    private DeploymentConfig createMinimalConfig() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "test-stack";
        config.environment = "dev";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.topology = TopologyType.JENKINS_SERVICE;
        config.securityProfile = SecurityProfile.DEV;
        config.domain = "";
        config.subdomain = "";
        return config;
    }

    @Test
    void testBuildCfcContext_BasicFields() throws Exception {
        // Given
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "test-stack";
        config.environment = "dev";
        config.applicationId = "grafana";
        config.runtime = RuntimeType.FARGATE;
        config.topology = TopologyType.JENKINS_SERVICE;
        config.securityProfile = SecurityProfile.DEV;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("test-stack", context.get("stackName"));
        assertEquals("dev", context.get("env"));
        assertEquals("grafana", context.get("applicationId"));
        assertEquals("FARGATE", context.get("runtime"));
        assertEquals("jenkins-service", context.get("topology"));  // kebab-case via Jackson serialization
        assertEquals("dev", context.get("securityProfile"));  // lowercase via Jackson serialization
    }

    @Test
    void testBuildCfcContext_DomainConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.domain = "example.com";
        config.subdomain = "ci";
        config.enableSsl = true;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("example.com", context.get("domain"));
        assertEquals("ci", context.get("subdomain"));
        assertEquals(true, context.get("enableSsl"));
    }

    @Test
    void testBuildCfcContext_EmptyDomainConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.domain = "";
        config.subdomain = "";
        config.enableSsl = false;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then - empty strings should still be present
        assertEquals("", context.get("domain"));
        assertEquals("", context.get("subdomain"));
        assertEquals(false, context.get("enableSsl"));
    }

    @Test
    void testBuildCfcContext_ResourceConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.cpu = 2048;
        config.memory = 4096;
        config.minInstanceCapacity = 2;
        config.maxInstanceCapacity = 5;
        config.cpuTargetUtilization = 70;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(2048, context.get("cpu"));
        assertEquals(4096, context.get("memory"));
        assertEquals(2, context.get("minInstanceCapacity"));
        assertEquals(5, context.get("maxInstanceCapacity"));
        assertEquals(70, context.get("cpuTargetUtilization"));
    }

    @Test
    void testBuildCfcContext_EC2Configuration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.runtime = RuntimeType.EC2;
        config.instanceType = "t3.medium";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("EC2", context.get("runtime"));
        assertEquals("t3.medium", context.get("instanceType"));
    }

    @Test
    void testBuildCfcContext_ComplianceConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.enableMonitoring = true;
        config.enableEncryption = true;
        config.awsConfigEnabled = true;
        config.guardDutyEnabled = true;
        config.auditManagerEnabled = true;
        config.complianceFrameworks = List.of(
            ComplianceFrameworkType.SOC2,
            ComplianceFrameworkType.HIPAA,
            ComplianceFrameworkType.PCI_DSS,
            ComplianceFrameworkType.GDPR
        );
        config.logRetentionDays = "730";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(true, context.get("enableMonitoring"));
        assertEquals(true, context.get("enableEncryption"));
        assertEquals(true, context.get("awsConfigEnabled"));
        assertEquals(true, context.get("guardDutyEnabled"));
        assertEquals(true, context.get("auditManagerEnabled"));
        assertEquals("soc2,hipaa,pci-dss,gdpr", context.get("complianceFrameworks")); // Serialized as lowercase
        assertEquals("730", context.get("logRetentionDays"));
    }

    @Test
    void testBuildCfcContext_NetworkConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.networkMode = NetworkMode.PRIVATE_WITH_NAT;
        config.wafEnabled = true;
        config.cloudfrontEnabled = true;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("private-with-nat", context.get("networkMode")); // Serialized as lowercase
        assertEquals(true, context.get("wafEnabled"));
        assertEquals(true, context.get("cloudfrontEnabled"));
    }

    @Test
    void testBuildCfcContext_OidcConfiguration_Cognito() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.oidcProvider = "cognito";
        config.cognitoAutoProvision = true;
        config.cognitoUserPoolName = "test-pool";
        config.cognitoDomainPrefix = "test-app-auth";
        config.cognitoMfaEnabled = true;
        config.cognitoCreateGroups = true;
        config.cognitoAdminGroupName = "Admins";
        config.cognitoUserGroupName = "Users";
        config.cognitoInitialAdminEmail = "admin@example.com";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("cognito", context.get("oidcProvider"));
        assertEquals(true, context.get("cognitoAutoProvision"));
        assertEquals("test-pool", context.get("cognitoUserPoolName"));
        assertEquals("test-app-auth", context.get("cognitoDomainPrefix"));
        assertEquals(true, context.get("cognitoMfaEnabled"));
        assertEquals(true, context.get("cognitoCreateGroups"));
        assertEquals("Admins", context.get("cognitoAdminGroupName"));
        assertEquals("Users", context.get("cognitoUserGroupName"));
        assertEquals("admin@example.com", context.get("cognitoInitialAdminEmail"));
    }

    @Test
    void testBuildCfcContext_OidcConfiguration_IdentityCenter() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.oidcProvider = "identity-center";
        config.oidcIssuer = "https://my-tenant.awsapps.com/start";
        config.oidcAuthorizationEndpoint = "https://my-tenant.awsapps.com/start/oauth2/authorize";
        config.oidcTokenEndpoint = "https://my-tenant.awsapps.com/token";
        config.oidcUserInfoEndpoint = "https://my-tenant.awsapps.com/userinfo";
        config.oidcClientId = "client-id-123";
        config.oidcClientSecretName = "identity-center/client-secret";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("identity-center", context.get("oidcProvider"));
        assertEquals("https://my-tenant.awsapps.com/start", context.get("oidcIssuer"));
        assertEquals("https://my-tenant.awsapps.com/start/oauth2/authorize", context.get("oidcAuthorizationEndpoint"));
        assertEquals("https://my-tenant.awsapps.com/token", context.get("oidcTokenEndpoint"));
        assertEquals("https://my-tenant.awsapps.com/userinfo", context.get("oidcUserInfoEndpoint"));
        assertEquals("client-id-123", context.get("oidcClientId"));
        assertEquals("identity-center/client-secret", context.get("oidcClientSecretName"));
    }

    @Test
    void testBuildCfcContext_OidcConfiguration_None() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.oidcProvider = "none";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("none", context.get("oidcProvider"));
    }

    @Test
    void testBuildCfcContext_HealthCheckConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.healthCheckGracePeriod = 600;
        config.healthCheckInterval = 60;
        config.healthCheckTimeout = 10;
        config.healthyThreshold = 3;
        config.unhealthyThreshold = 5;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(600, context.get("healthCheckGracePeriod"));
        assertEquals(60, context.get("healthCheckInterval"));
        assertEquals(10, context.get("healthCheckTimeout"));
        assertEquals(3, context.get("healthyThreshold"));
        assertEquals(5, context.get("unhealthyThreshold"));
    }

    @Test
    void testBuildCfcContext_RegionConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.region = "us-west-2";
        config.availabilityZones = new String[]{"us-west-2a", "us-west-2b"};

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("us-west-2", context.get("region"));
        assertNotNull(context.get("availabilityZones"));
    }

    @Test
    void testBuildCfcContext_ScalingConfiguration() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.minInstanceCapacity = 1;
        config.maxInstanceCapacity = 3;
        config.enableAutoScaling = true;
        config.cpuTargetUtilization = 60;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(1, context.get("minInstanceCapacity"));
        assertEquals(3, context.get("maxInstanceCapacity"));
        assertEquals(true, context.get("enableAutoScaling"));
        assertEquals(60, context.get("cpuTargetUtilization"));
    }

    @Test
    void testApplicationInfo_AllFields() {
        // Given & When
        ApplicationInfo app = new ApplicationInfo(
            "grafana",
            "Grafana",
            "Metrics visualization platform",
            true,  // supportsFargate
            true,  // supportsEc2
            true   // supportsOidc
        );

        // Then
        assertEquals("grafana", app.id);
        assertEquals("Grafana", app.name);
        assertEquals("Metrics visualization platform", app.description);
        assertTrue(app.supportsFargate);
        assertTrue(app.supportsEc2);
        assertTrue(app.supportsOidc);
    }

    @Test
    void testApplicationInfo_DatabaseNoOidc() {
        // Given & When
        ApplicationInfo app = new ApplicationInfo(
            "postgresql",
            "PostgreSQL",
            "Relational database",
            false, // supportsFargate
            true,  // supportsEc2
            false  // supportsOidc
        );

        // Then
        assertEquals("postgresql", app.id);
        assertFalse(app.supportsFargate);
        assertTrue(app.supportsEc2);
        assertFalse(app.supportsOidc);
    }

    @Test
    void testDeploymentConfig_Defaults() {
        // Given & When
        DeploymentConfig config = new DeploymentConfig();

        // Then - verify default values
        assertEquals(1, config.minInstanceCapacity);
        assertEquals(1, config.maxInstanceCapacity);
        assertEquals(60, config.cpuTargetUtilization);
        assertEquals(1024, config.cpu);
        assertEquals(2048, config.memory);
        assertEquals("t3.micro", config.instanceType);
        assertEquals("none", config.oidcProvider);
        assertFalse(config.cognitoAutoProvision);
        assertTrue(config.cognitoCreateGroups);
        assertTrue(config.enableMonitoring);
        assertTrue(config.enableEncryption);
        assertFalse(config.awsConfigEnabled);
        assertFalse(config.guardDutyEnabled);
        assertFalse(config.auditManagerEnabled);
        assertNull(config.logRetentionDays);
        assertEquals(300, config.healthCheckGracePeriod);
        assertEquals(30, config.healthCheckInterval);
        assertEquals(5, config.healthCheckTimeout);
        assertEquals(2, config.healthyThreshold);
        assertEquals(3, config.unhealthyThreshold);
        assertEquals("us-east-1", config.region);
        assertFalse(config.enableAutoScaling);
    }

    @Test
    void testBuildCfcContext_ApplicationMetadata() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.applicationId = "gitlab";
        // Note: supportsFargate, supportsEc2, supportsOidc are on ApplicationInfo, not DeploymentConfig
        // These are populated from ApplicationSpec during interactive deployment

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("gitlab", context.get("applicationId"));
    }

    @Test
    void testBuildCfcContext_EnvironmentRename() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.environment = "staging";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then - "environment" should be renamed to "env"
        assertEquals("staging", context.get("env"));
        assertFalse(context.containsKey("environment"));
    }

    @Test
    void testBuildCfcContext_NullValues() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.cognitoUserPoolName = null;
        config.cognitoAdminGroupName = null;
        config.oidcIssuer = null;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then - null values should not be included due to JsonInclude.Include.NON_NULL
        assertFalse(context.containsKey("cognitoUserPoolName") && context.get("cognitoUserPoolName") != null);
        assertFalse(context.containsKey("cognitoAdminGroupName") && context.get("cognitoAdminGroupName") != null);
        assertFalse(context.containsKey("oidcIssuer") && context.get("oidcIssuer") != null);
    }

    @Test
    void testBuildCfcContext_ConfigInfrastructure() throws Exception {
        // Given
        DeploymentConfig config = createMinimalConfig();
        config.awsConfigEnabled = true;
        config.createConfigInfrastructure = false;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(true, context.get("awsConfigEnabled"));
        assertEquals(false, context.get("createConfigInfrastructure"));
    }

    @Test
    void testHealthCheckGracePeriod_ApplicationSpecDefaults() {
        // Test that application-specific health check grace periods are properly
        // exposed via ApplicationSpec.defaultHealthCheckGracePeriod()

        // Load GitLab application spec
        var gitlabSpecOpt = com.cloudforgeci.api.compute.ApplicationLoader.findById("gitlab");
        assertTrue(gitlabSpecOpt.isPresent(), "GitLab ApplicationSpec should be loaded");

        var gitlabSpec = gitlabSpecOpt.get();

        // GitLab requires 900s due to database migrations and initialization
        assertEquals(900, gitlabSpec.defaultHealthCheckGracePeriod(),
            "GitLab should have 900s health check grace period");

        // Load Jenkins application spec
        var jenkinsSpecOpt = com.cloudforgeci.api.compute.ApplicationLoader.findById("jenkins");
        assertTrue(jenkinsSpecOpt.isPresent(), "Jenkins ApplicationSpec should be loaded");

        var jenkinsSpec = jenkinsSpecOpt.get();

        // Jenkins uses default 300s
        assertEquals(300, jenkinsSpec.defaultHealthCheckGracePeriod(),
            "Jenkins should use default 300s health check grace period");
    }

    @Test
    void testHealthCheckConfiguration_DefaultValueResolver() {
        // Test that DefaultValueResolver correctly pulls application-specific defaults
        // from ApplicationSpec when using @ConfigField(defaultFrom="defaultHealthCheckGracePeriod")

        // Given - GitLab deployment config
        DeploymentConfig gitlabConfig = createMinimalConfig();
        gitlabConfig.applicationId = "gitlab";

        var gitlabSpecOpt = com.cloudforgeci.api.compute.ApplicationLoader.findById("gitlab");
        assertTrue(gitlabSpecOpt.isPresent(), "GitLab ApplicationSpec should be loaded");
        gitlabConfig.applicationSpec = gitlabSpecOpt.get();

        // When - Using ConfigurationIntrospector to discover health check fields
        var healthCheckFields = com.cloudforge.core.config.ConfigurationIntrospector
            .discoverVisibleFields(gitlabConfig.applicationSpec, gitlabConfig, "resources");

        // Then - Should find all health check fields
        assertFalse(healthCheckFields.isEmpty(), "Should discover health check fields in resources category");

        // Find the healthCheckGracePeriod field
        var gracePeriodField = healthCheckFields.stream()
            .filter(f -> f.fieldName().equals("healthCheckGracePeriod"))
            .findFirst();

        assertTrue(gracePeriodField.isPresent(), "Should find healthCheckGracePeriod field");

        // Verify defaultFrom attribute is set correctly
        assertEquals("defaultHealthCheckGracePeriod", gracePeriodField.get().defaultFrom(),
            "Field should use defaultFrom='defaultHealthCheckGracePeriod'");

        // Verify DefaultValueResolver resolves to 600 for GitLab
        Object resolvedDefault = com.cloudforge.core.config.DefaultValueResolver.resolve(
            gracePeriodField.get(),
            gitlabConfig.applicationSpec,
            null
        );

        assertEquals(900, resolvedDefault,
            "DefaultValueResolver should resolve GitLab health check grace period to 900s");
    }
}
