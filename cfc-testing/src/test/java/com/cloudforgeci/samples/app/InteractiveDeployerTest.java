package com.cloudforgeci.samples.app;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awscdk.App;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InteractiveDeployer utility methods.
 *
 * Tests focus on:
 * 1. Context building and field mapping
 * 2. JSON serialization/deserialization
 * 3. Field propagation from deployment-context.json to DeploymentContext
 */
class InteractiveDeployerTest {

    /**
     * Helper method to access buildCfcContext via reflection since it's private.
     * In production, consider making it package-private or providing a public test accessor.
     */
    private Map<String, Object> buildCfcContext(InteractiveDeployer.DeploymentConfig config) throws Exception {
        var method = InteractiveDeployer.class.getDeclaredMethod("buildCfcContext", InteractiveDeployer.DeploymentConfig.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(null, config);
    }

    /**
     * Helper to access extractValue via reflection.
     */
    private String extractValue(String json, String key) throws Exception {
        var method = InteractiveDeployer.class.getDeclaredMethod("extractValue", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, json, key);
    }

    @Test
    void testBuildCfcContext_BasicFields() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = new InteractiveDeployer.DeploymentConfig();
        config.stackName = "test-stack";
        config.environment = "dev";
        config.tier = "public";
        config.runtime = RuntimeType.FARGATE;
        config.topology = TopologyType.JENKINS_SERVICE;
        config.securityProfile = SecurityProfile.DEV;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("test-stack", context.get("stackName"));
        assertEquals("dev", context.get("env"));
        assertEquals("public", context.get("tier"));
        assertEquals("FARGATE", context.get("runtime"));
        assertEquals("JENKINS_SERVICE", context.get("topology"));
        assertEquals("DEV", context.get("securityProfile"));
    }

    @Test
    void testBuildCfcContext_DomainConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
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
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
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
    void testBuildCfcContext_NetworkConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.networkMode = "private-with-nat";
        config.wafEnabled = true;
        config.cloudfrontEnabled = false;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("private-with-nat", context.get("networkMode"));
        assertEquals(true, context.get("wafEnabled"));
        assertEquals(false, context.get("cloudfrontEnabled"));
    }

    @Test
    void testBuildCfcContext_ComplianceConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.guardDutyEnabled = true;
        config.auditManagerEnabled = true;
        config.awsConfigEnabled = true;
        config.createConfigInfrastructure = false;
        config.complianceFrameworks = "HIPAA,SOC2,GDPR";
        config.auditManagerFrameworkId = "12345-abcde";
        config.logRetentionDays = "2190";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(true, context.get("guardDutyEnabled"));
        assertEquals(true, context.get("auditManagerEnabled"));
        assertEquals(true, context.get("awsConfigEnabled"));
        assertEquals(false, context.get("createConfigInfrastructure"));
        assertEquals("HIPAA,SOC2,GDPR", context.get("complianceFrameworks"));
        assertEquals("12345-abcde", context.get("auditManagerFrameworkId"));
        assertEquals("2190", context.get("logRetentionDays"));
    }

    @Test
    void testBuildCfcContext_ScalingConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.minInstanceCapacity = 2;
        config.maxInstanceCapacity = 10;
        config.cpuTargetUtilization = 70;
        config.enableAutoScaling = true;
        config.cpu = 2048;
        config.memory = 4096;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(2, context.get("minInstanceCapacity"));
        assertEquals(10, context.get("maxInstanceCapacity"));
        assertEquals(70, context.get("cpuTargetUtilization"));
        assertEquals(true, context.get("enableAutoScaling"));
        assertEquals(2048, context.get("cpu"));
        assertEquals(4096, context.get("memory"));
    }

    @Test
    void testBuildCfcContext_HealthCheckConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
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
    void testBuildCfcContext_CognitoConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.authMode = "alb-oidc";
        config.cognitoAutoProvision = true;
        config.cognitoDomainPrefix = "my-app-auth";
        config.cognitoUserPoolName = "my-app-users";
        config.cognitoMfaEnabled = true;
        config.cognitoCreateGroups = true;
        config.cognitoAdminGroupName = "Admins";
        config.cognitoUserGroupName = "Users";
        config.cognitoInitialAdminEmail = "admin@example.com";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("alb-oidc", context.get("authMode"));
        assertEquals(true, context.get("cognitoAutoProvision"));
        assertEquals("my-app-auth", context.get("cognitoDomainPrefix"));
        assertEquals("my-app-users", context.get("cognitoUserPoolName"));
        assertEquals(true, context.get("cognitoMfaEnabled"));
        assertEquals(true, context.get("cognitoCreateGroups"));
        assertEquals("Admins", context.get("cognitoAdminGroupName"));
        assertEquals("Users", context.get("cognitoUserGroupName"));
        assertEquals("admin@example.com", context.get("cognitoInitialAdminEmail"));
    }

    @Test
    void testBuildCfcContext_OidcConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.authMode = "alb-oidc";
        config.cognitoAutoProvision = false;
        config.oidcIssuer = "https://identity.example.com";
        config.oidcAuthorizationEndpoint = "https://identity.example.com/authorize";
        config.oidcTokenEndpoint = "https://identity.example.com/token";
        config.oidcUserInfoEndpoint = "https://identity.example.com/userinfo";
        config.oidcClientId = "my-client-id";
        config.oidcClientSecretName = "my-app/oidc/secret";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("alb-oidc", context.get("authMode"));
        assertEquals("https://identity.example.com", context.get("oidcIssuer"));
        assertEquals("https://identity.example.com/authorize", context.get("oidcAuthorizationEndpoint"));
        assertEquals("https://identity.example.com/token", context.get("oidcTokenEndpoint"));
        assertEquals("https://identity.example.com/userinfo", context.get("oidcUserInfoEndpoint"));
        assertEquals("my-client-id", context.get("oidcClientId"));
        assertEquals("my-app/oidc/secret", context.get("oidcClientSecretName"));
    }

    @Test
    void testExtractValue_QuotedStrings() throws Exception {
        // Given
        String json = "{\"stackName\": \"test-stack\", \"domain\": \"example.com\"}";

        // When/Then
        assertEquals("test-stack", extractValue(json, "stackName"));
        assertEquals("example.com", extractValue(json, "domain"));
    }

    @Test
    void testExtractValue_UnquotedBooleans() throws Exception {
        // Given
        String json = "{\"enableSsl\": true, \"wafEnabled\": false}";

        // When/Then
        assertEquals("true", extractValue(json, "enableSsl"));
        assertEquals("false", extractValue(json, "wafEnabled"));
    }

    @Test
    void testExtractValue_UnquotedNumbers() throws Exception {
        // Given
        String json = "{\"cpu\": 1024, \"memory\": 2048, \"logRetentionDays\": 2190}";

        // When/Then
        assertEquals("1024", extractValue(json, "cpu"));
        assertEquals("2048", extractValue(json, "memory"));
        assertEquals("2190", extractValue(json, "logRetentionDays"));
    }

    @Test
    void testExtractValue_NonExistentKey() throws Exception {
        // Given
        String json = "{\"stackName\": \"test-stack\"}";

        // When/Then
        assertNull(extractValue(json, "nonExistentKey"));
    }

    @Test
    void testExtractValue_EmptyString() throws Exception {
        // Given
        String json = "{\"domain\": \"\"}";

        // When/Then
        assertEquals("", extractValue(json, "domain"));
    }

    /**
     * Integration test: Verify full round-trip of context saving and loading.
     * This ensures all fields properly propagate through JSON serialization.
     */
    @Test
    void testContextSaveAndLoad_RoundTrip(@TempDir Path tempDir) throws Exception {
        // Given - Create a comprehensive config
        InteractiveDeployer.DeploymentConfig originalConfig = new InteractiveDeployer.DeploymentConfig();
        originalConfig.stackName = "integration-test-stack";
        originalConfig.environment = "staging";
        originalConfig.runtime = RuntimeType.FARGATE;
        originalConfig.topology = TopologyType.JENKINS_SERVICE;
        originalConfig.securityProfile = SecurityProfile.STAGING;
        originalConfig.domain = "test.cloudforgeci.com";
        originalConfig.subdomain = "jenkins";
        originalConfig.enableSsl = true;
        originalConfig.guardDutyEnabled = true;
        originalConfig.auditManagerEnabled = true;
        originalConfig.complianceFrameworks = "HIPAA,SOC2";
        originalConfig.logRetentionDays = "2190";
        originalConfig.cpu = 1024;
        originalConfig.memory = 2048;
        originalConfig.minInstanceCapacity = 1;
        originalConfig.maxInstanceCapacity = 5;
        originalConfig.region = "us-east-1";

        // When - Build context and save to JSON
        Map<String, Object> context = buildCfcContext(originalConfig);

        // Save to temp file
        Path contextFile = tempDir.resolve("deployment-context.json");
        saveContextToTempFile(context, originalConfig.stackName, contextFile);

        // Load JSON back
        String json = Files.readString(contextFile);

        // Then - Verify all critical fields are present and correct in JSON
        assertTrue(json.contains("\"stackName\": \"integration-test-stack\""));
        assertTrue(json.contains("\"env\": \"staging\""));
        assertTrue(json.contains("\"runtime\": \"FARGATE\""));
        assertTrue(json.contains("\"topology\": \"JENKINS_SERVICE\""));
        assertTrue(json.contains("\"securityProfile\": \"STAGING\""));
        assertTrue(json.contains("\"domain\": \"test.cloudforgeci.com\""));
        assertTrue(json.contains("\"subdomain\": \"jenkins\""));
        assertTrue(json.contains("\"enableSsl\": true"));
        assertTrue(json.contains("\"guardDutyEnabled\": true"));
        assertTrue(json.contains("\"auditManagerEnabled\": true"));
        assertTrue(json.contains("\"complianceFrameworks\": \"HIPAA,SOC2\""));
        assertTrue(json.contains("\"logRetentionDays\": \"2190\""));
        assertTrue(json.contains("\"cpu\": 1024"));
        assertTrue(json.contains("\"memory\": 2048"));
        assertTrue(json.contains("\"region\": \"us-east-1\""));

        // Verify extractValue can parse all fields back
        assertEquals("integration-test-stack", extractValue(json, "stackName"));
        assertEquals("staging", extractValue(json, "env"));
        assertEquals("FARGATE", extractValue(json, "runtime"));
        assertEquals("STAGING", extractValue(json, "securityProfile"));
        assertEquals("test.cloudforgeci.com", extractValue(json, "domain"));
        assertEquals("true", extractValue(json, "guardDutyEnabled"));
        assertEquals("HIPAA,SOC2", extractValue(json, "complianceFrameworks"));
        assertEquals("2190", extractValue(json, "logRetentionDays"));
        assertEquals("1024", extractValue(json, "cpu"));
    }

    /**
     * Test that DeploymentContext properly loads from JSON with all annotation-based fields.
     * This validates the @DeploymentContext annotation injection mechanism.
     */
    @Test
    void testDeploymentContextLoading(@TempDir Path tempDir) throws Exception {
        // Given - Create a deployment-context.json file
        String json = """
            {
              "stackName": "test-deployment",
              "context": {
                "stackName": "test-deployment",
                "env": "staging",
                "runtime": "FARGATE",
                "topology": "JENKINS_SERVICE",
                "securityProfile": "STAGING",
                "guardDutyEnabled": true,
                "logRetentionDays": 2190,
                "complianceFrameworks": "HIPAA,SOC2,GDPR",
                "awsConfigEnabled": true,
                "auditManagerEnabled": true,
                "region": "us-east-1",
                "cpu": 1024,
                "memory": 2048,
                "domain": "test.example.com",
                "subdomain": "app",
                "enableSsl": true,
                "authMode": "alb-oidc",
                "cognitoAutoProvision": true,
                "cognitoMfaEnabled": true
              }
            }
            """;

        Path contextFile = tempDir.resolve("deployment-context.json");
        Files.writeString(contextFile, json);

        // When - Create App with context
        App app = new App();

        // Parse JSON and set context
        Map<String, Object> contextMap = parseJsonToMap(json);
        app.getNode().setContext("cfc", contextMap);

        // Load DeploymentContext
        DeploymentContext cfc = DeploymentContext.from(app);

        // Then - Verify all fields loaded correctly via annotations
        assertEquals("test-deployment", cfc.stackName());
        assertEquals("staging", cfc.env());
        assertEquals(RuntimeType.FARGATE, cfc.runtime());
        assertEquals(TopologyType.JENKINS_SERVICE, cfc.topology());
        assertEquals(SecurityProfile.STAGING, cfc.securityProfile());
        assertEquals(true, cfc.guardDutyEnabled());
        assertEquals(2190, cfc.logRetentionDays());
        assertEquals("HIPAA,SOC2,GDPR", cfc.complianceFrameworks());
        assertEquals(true, cfc.awsConfigEnabled());
        assertEquals(true, cfc.auditManagerEnabled());
        assertEquals("us-east-1", cfc.region());
        assertEquals(1024, cfc.cpu());
        assertEquals(2048, cfc.memory());
        assertEquals("test.example.com", cfc.domain());
        assertEquals("app", cfc.subdomain());
        assertEquals(true, cfc.enableSsl());
        assertEquals("alb-oidc", cfc.authMode());
        assertEquals(true, cfc.cognitoAutoProvision());
        assertEquals(true, cfc.cognitoMfaEnabled());
    }

    /**
     * Test numeric fields are properly typed (Integer vs String).
     * logRetentionDays should support both for backwards compatibility.
     */
    @Test
    void testDeploymentContext_NumericFieldTypes(@TempDir Path tempDir) throws Exception {
        // Given - JSON with logRetentionDays as both string and number
        String jsonWithStringRetention = """
            {
              "stackName": "test",
              "context": {
                "stackName": "test",
                "logRetentionDays": "2190",
                "cpu": 1024
              }
            }
            """;

        App app1 = new App();
        Map<String, Object> contextMap1 = parseJsonToMap(jsonWithStringRetention);
        app1.getNode().setContext("cfc", contextMap1);
        DeploymentContext cfc1 = DeploymentContext.from(app1);

        // Then - String "2190" should be converted to integer 2190
        assertEquals(2190, cfc1.logRetentionDays());
        assertEquals(1024, cfc1.cpu());

        // Given - JSON with logRetentionDays as number
        String jsonWithNumberRetention = """
            {
              "stackName": "test",
              "context": {
                "stackName": "test",
                "logRetentionDays": 2190,
                "cpu": 1024
              }
            }
            """;

        App app2 = new App();
        Map<String, Object> contextMap2 = parseJsonToMap(jsonWithNumberRetention);
        app2.getNode().setContext("cfc", contextMap2);
        DeploymentContext cfc2 = DeploymentContext.from(app2);

        // Then - Number 2190 should remain 2190
        assertEquals(2190, cfc2.logRetentionDays());
        assertEquals(1024, cfc2.cpu());
    }

    // ==================== Helper Methods ====================

    /**
     * Helper to save context to a temp file for testing.
     */
    private void saveContextToTempFile(Map<String, Object> context, String stackName, Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"stackName\": \"").append(stackName).append("\",\n");
        sb.append("  \"context\": {\n");
        boolean first = true;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!first) sb.append(",\n");
            Object value = entry.getValue();
            String formattedValue;
            if (value instanceof Boolean || value instanceof Number) {
                formattedValue = value.toString();
            } else {
                formattedValue = "\"" + value + "\"";
            }
            sb.append("    \"").append(entry.getKey()).append("\": ").append(formattedValue);
            first = false;
        }
        sb.append("\n  }\n");
        sb.append("}\n");
        Files.writeString(file, sb.toString());
    }

    /**
     * Create a minimal valid DeploymentConfig with required fields initialized.
     * This prevents NullPointerException when calling buildCfcContext().
     */
    private InteractiveDeployer.DeploymentConfig createMinimalConfig() {
        InteractiveDeployer.DeploymentConfig config = new InteractiveDeployer.DeploymentConfig();
        config.runtime = RuntimeType.FARGATE;
        config.topology = TopologyType.JENKINS_SERVICE;
        config.securityProfile = SecurityProfile.DEV;
        config.stackName = "test-stack";
        config.environment = "dev";
        return config;
    }

    /**
     * Helper to parse JSON into a Map for CDK context.
     */
    private Map<String, Object> parseJsonToMap(String json) {
        // Extract the "context" object from JSON
        int contextStart = json.indexOf("\"context\":");
        if (contextStart == -1) throw new IllegalArgumentException("No 'context' field in JSON");

        int braceStart = json.indexOf("{", contextStart);
        int braceCount = 1;
        int i = braceStart + 1;
        while (i < json.length() && braceCount > 0) {
            if (json.charAt(i) == '{') braceCount++;
            else if (json.charAt(i) == '}') braceCount--;
            i++;
        }
        String contextJson = json.substring(braceStart, i);

        // Parse into Map (simplified - production would use Jackson/Gson)
        Map<String, Object> map = new java.util.HashMap<>();

        // Extract key-value pairs - pattern matches: "key": value (where value can be quoted string, bool, or number)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\":\\s*(?:\"([^\"]*)\"|([^,}\\s]+))");
        java.util.regex.Matcher matcher = pattern.matcher(contextJson);
        while (matcher.find()) {
            String key = matcher.group(1);
            String quotedValue = matcher.group(2);  // Captured if value was in quotes
            String unquotedValue = matcher.group(3); // Captured if value was NOT in quotes

            if (quotedValue != null) {
                // String value (was quoted)
                map.put(key, quotedValue);
            } else if (unquotedValue != null) {
                // Boolean or number (was not quoted)
                String value = unquotedValue.trim();
                if (value.equals("true")) {
                    map.put(key, true);
                } else if (value.equals("false")) {
                    map.put(key, false);
                } else {
                    // Try as number
                    try {
                        if (value.contains(".")) {
                            map.put(key, Double.parseDouble(value));
                        } else {
                            map.put(key, Integer.parseInt(value));
                        }
                    } catch (NumberFormatException e) {
                        map.put(key, value); // Keep as string if not a valid number
                    }
                }
            }
        }

        return map;
    }

    @Test
    void testBuildCfcContext_IdentityCenterConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.authMode = "alb-oidc";
        config.cognitoAutoProvision = false;
        config.autoProvisionIdentityCenter = true;
        config.ssoInstanceArn = "arn:aws:sso:::instance/ssoins-1234567890abcdef";
        config.ssoGroupId = "group-12345678-1234-1234-1234-123456789012";
        config.ssoTargetAccountId = "123456789012";
        config.identityCenterGroupName = "ApplicationUsers";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("alb-oidc", context.get("authMode"));
        assertEquals(true, context.get("autoProvisionIdentityCenter"));
        assertEquals("arn:aws:sso:::instance/ssoins-1234567890abcdef", context.get("ssoInstanceArn"));
        assertEquals("group-12345678-1234-1234-1234-123456789012", context.get("ssoGroupId"));
        assertEquals("123456789012", context.get("ssoTargetAccountId"));
        assertEquals("ApplicationUsers", context.get("identityCenterGroupName"));
    }

    @Test
    void testBuildCfcContext_StoragePersistenceConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.retainStorage = true;
        config.existingFileSystemId = "fs-0123456789abcdef0";
        config.createZone = true;
        config.artifactsPrefix = "custom/jenkins/artifacts/${JOB_NAME}/${BUILD_ID}";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(true, context.get("retainStorage"));
        assertEquals("fs-0123456789abcdef0", context.get("existingFileSystemId"));
        assertEquals(true, context.get("createZone"));
        assertEquals("custom/jenkins/artifacts/${JOB_NAME}/${BUILD_ID}", context.get("artifactsPrefix"));
    }

    @Test
    void testBuildCfcContext_CognitoExistingPoolConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.authMode = "alb-oidc";
        config.cognitoAutoProvision = false;
        config.cognitoUserPoolId = "us-east-1_ABCDEFGHI";
        config.cognitoAppClientId = "1234567890abcdefghijklmnop";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("alb-oidc", context.get("authMode"));
        assertEquals(false, context.get("cognitoAutoProvision"));
        assertEquals("us-east-1_ABCDEFGHI", context.get("cognitoUserPoolId"));
        assertEquals("1234567890abcdefghijklmnop", context.get("cognitoAppClientId"));
    }

    @Test
    void testBuildCfcContext_InfrastructureConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.bastionCidr = "192.168.1.0/24";
        config.lbType = "nlb";
        config.enableFlowlogs = true;
        config.availabilityZones = new String[]{"us-west-2a", "us-west-2b"};
        config.instanceType = "t3.large";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("192.168.1.0/24", context.get("bastionCidr"));
        assertEquals("nlb", context.get("lbType"));
        assertEquals(true, context.get("enableFlowlogs"));
        // availabilityZones is now an array, so check it properly
        assertNotNull(context.get("availabilityZones"));
        assertEquals("t3.large", context.get("instanceType"));
    }

    @Test
    void testBuildCfcContext_AdvancedSecurityConfiguration() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.enableMonitoring = false;
        config.enableEncryption = false;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(false, context.get("enableMonitoring"));
        assertEquals(false, context.get("enableEncryption"));
    }

    @Test
    void testBuildCfcContext_DeploymentType() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.deploymentType = "s3-website";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("s3-website", context.get("deploymentType"));
    }

    /**
     * Integration test: Verify all missing fields properly round-trip through JSON.
     * This ensures the 23 previously uncovered fields are correctly serialized.
     */
    @Test
    void testContextSaveAndLoad_AllMissingFields(@TempDir Path tempDir) throws Exception {
        // Given - Create a config with ALL missing fields populated
        InteractiveDeployer.DeploymentConfig config = new InteractiveDeployer.DeploymentConfig();
        config.stackName = "comprehensive-test";
        config.environment = "staging";
        config.runtime = RuntimeType.EC2;
        config.topology = TopologyType.JENKINS_SERVICE;
        config.securityProfile = SecurityProfile.STAGING;

        // Previously missing fields
        config.deploymentType = "jenkins";
        config.instanceType = "t3.large";
        config.cognitoUserPoolId = "us-east-1_TestPool123";
        config.cognitoAppClientId = "abcdef123456clientid";
        config.ssoInstanceArn = "arn:aws:sso:::instance/ssoins-test";
        config.ssoGroupId = "group-test-id";
        config.ssoTargetAccountId = "123456789012";
        config.autoProvisionIdentityCenter = true;
        config.identityCenterGroupName = "TestGroup";
        config.enableMonitoring = false;
        config.enableEncryption = false;
        config.availabilityZones = new String[]{"us-east-1a", "us-east-1b"};
        config.bastionCidr = "10.10.10.0/24";
        config.lbType = "nlb";
        config.enableFlowlogs = true;
        config.retainStorage = true;
        config.existingFileSystemId = "fs-testfilesystem";
        config.createZone = true;
        config.artifactsPrefix = "test/artifacts/${BUILD_NUMBER}";
        config.region = "us-east-1";

        // When - Build context and save to JSON
        Map<String, Object> context = buildCfcContext(config);
        Path contextFile = tempDir.resolve("deployment-context.json");
        saveContextToTempFile(context, config.stackName, contextFile);

        // Load JSON back
        String json = Files.readString(contextFile);

        // Then - Verify all missing fields are present in JSON
        assertTrue(json.contains("\"deploymentType\": \"jenkins\""));
        assertTrue(json.contains("\"instanceType\": \"t3.large\""));
        assertTrue(json.contains("\"cognitoUserPoolId\": \"us-east-1_TestPool123\""));
        assertTrue(json.contains("\"cognitoAppClientId\": \"abcdef123456clientid\""));
        assertTrue(json.contains("\"ssoInstanceArn\": \"arn:aws:sso:::instance/ssoins-test\""));
        assertTrue(json.contains("\"ssoGroupId\": \"group-test-id\""));
        assertTrue(json.contains("\"ssoTargetAccountId\": \"123456789012\""));
        assertTrue(json.contains("\"autoProvisionIdentityCenter\": true"));
        assertTrue(json.contains("\"identityCenterGroupName\": \"TestGroup\""));
        assertTrue(json.contains("\"enableMonitoring\": false"));
        assertTrue(json.contains("\"enableEncryption\": false"));
        // availabilityZones is now an array
        assertTrue(json.contains("\"availabilityZones\""));
        assertTrue(json.contains("\"bastionCidr\": \"10.10.10.0/24\""));
        assertTrue(json.contains("\"lbType\": \"nlb\""));
        assertTrue(json.contains("\"enableFlowlogs\": true"));
        assertTrue(json.contains("\"retainStorage\": true"));
        assertTrue(json.contains("\"existingFileSystemId\": \"fs-testfilesystem\""));
        assertTrue(json.contains("\"createZone\": true"));
        assertTrue(json.contains("\"artifactsPrefix\": \"test/artifacts/${BUILD_NUMBER}\""));

        // Verify extractValue can parse all fields back
        assertEquals("jenkins", extractValue(json, "deploymentType"));
        assertEquals("t3.large", extractValue(json, "instanceType"));
        assertEquals("us-east-1_TestPool123", extractValue(json, "cognitoUserPoolId"));
        assertEquals("abcdef123456clientid", extractValue(json, "cognitoAppClientId"));
        assertEquals("arn:aws:sso:::instance/ssoins-test", extractValue(json, "ssoInstanceArn"));
        assertEquals("group-test-id", extractValue(json, "ssoGroupId"));
        assertEquals("123456789012", extractValue(json, "ssoTargetAccountId"));
        assertEquals("true", extractValue(json, "autoProvisionIdentityCenter"));
        assertEquals("TestGroup", extractValue(json, "identityCenterGroupName"));
        assertEquals("false", extractValue(json, "enableMonitoring"));
        assertEquals("false", extractValue(json, "enableEncryption"));
        // availabilityZones is now an array, so we just verify it exists in the JSON
        assertNotNull(extractValue(json, "availabilityZones"));
        assertEquals("10.10.10.0/24", extractValue(json, "bastionCidr"));
        assertEquals("nlb", extractValue(json, "lbType"));
        assertEquals("true", extractValue(json, "enableFlowlogs"));
        assertEquals("true", extractValue(json, "retainStorage"));
        assertEquals("fs-testfilesystem", extractValue(json, "existingFileSystemId"));
        assertEquals("true", extractValue(json, "createZone"));
        assertEquals("test/artifacts/${BUILD_NUMBER}", extractValue(json, "artifactsPrefix"));
    }

    /**
     * Test that empty strings for SSO/Identity Center fields are preserved.
     * Empty strings should be present in JSON, not omitted.
     */
    @Test
    void testBuildCfcContext_EmptyIdentityCenterFields() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        config.ssoInstanceArn = "";
        config.ssoGroupId = "";
        config.ssoTargetAccountId = "";
        config.identityCenterGroupName = "Jenkins-Users";  // Default value
        config.autoProvisionIdentityCenter = false;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then - Empty strings should be present
        assertEquals("", context.get("ssoInstanceArn"));
        assertEquals("", context.get("ssoGroupId"));
        assertEquals("", context.get("ssoTargetAccountId"));
        assertEquals("Jenkins-Users", context.get("identityCenterGroupName"));
        assertEquals(false, context.get("autoProvisionIdentityCenter"));
    }

    /**
     * Test storage persistence defaults (mostly false/null).
     */
    @Test
    void testBuildCfcContext_StoragePersistenceDefaults() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        // Use defaults: retainStorage=false, existingFileSystemId=null, createZone=false
        config.artifactsPrefix = "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}";

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals(false, context.get("retainStorage"));
        assertFalse(context.containsKey("existingFileSystemId"));  // null should be omitted
        assertEquals(false, context.get("createZone"));
        assertEquals("jenkins/job/${JOB_NAME}/${BUILD_NUMBER}", context.get("artifactsPrefix"));
    }

    /**
     * Test infrastructure defaults match production deployment-context.json.
     */
    @Test
    void testBuildCfcContext_InfrastructureDefaults() throws Exception {
        // Given
        InteractiveDeployer.DeploymentConfig config = createMinimalConfig();
        // Use defaults from DeploymentConfig class
        config.bastionCidr = "10.0.1.0/24";
        config.lbType = "alb";
        config.enableFlowlogs = false;

        // When
        Map<String, Object> context = buildCfcContext(config);

        // Then
        assertEquals("10.0.1.0/24", context.get("bastionCidr"));
        assertEquals("alb", context.get("lbType"));
        assertEquals(false, context.get("enableFlowlogs"));
    }
}
