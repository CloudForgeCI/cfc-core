package com.cloudforgeci.samples.app;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awscdk.App;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests focusing on field propagation from deployment-context.json through to DeploymentContext.
 * This ensures all fields are properly mapped via @DeploymentContext annotations.
 */
class DeploymentContextPropagationTest {

    /**
     * Test that all critical fields properly propagate from context map to DeploymentContext.
     */
    @Test
    void testAllFieldsPropagateToDeploymentContext() {
        // Given - Create App with comprehensive context
        App app = new App();
        Map<String, Object> context = new HashMap<>();

        // Basic fields
        context.put("stackName", "test-stack");
        context.put("env", "staging");
        context.put("runtime", "FARGATE");
        context.put("topology", "JENKINS_SERVICE");
        context.put("securityProfile", "STAGING");
        context.put("region", "us-east-1");

        // Compliance fields
        context.put("guardDutyEnabled", true);
        context.put("auditManagerEnabled", true);
        context.put("awsConfigEnabled", true);
        context.put("createConfigInfrastructure", false);
        context.put("complianceFrameworks", "HIPAA,SOC2,GDPR");
        context.put("logRetentionDays", 2190);

        // Resource sizing
        context.put("cpu", 1024);
        context.put("memory", 2048);
        context.put("minInstanceCapacity", 1);
        context.put("maxInstanceCapacity", 5);
        context.put("cpuTargetUtilization", 70);
        context.put("enableAutoScaling", true);

        // Network & Security
        context.put("domain", "test.example.com");
        context.put("subdomain", "app");
        context.put("enableSsl", true);
        context.put("networkMode", "private-with-nat");
        context.put("wafEnabled", true);
        context.put("cloudfrontEnabled", false);
        context.put("authMode", "alb-oidc");

        // Cognito
        context.put("cognitoAutoProvision", true);
        context.put("cognitoMfaEnabled", true);
        context.put("cognitoDomainPrefix", "test-auth");
        context.put("cognitoUserPoolName", "test-users");

        // Health checks
        context.put("healthCheckGracePeriod", 300);
        context.put("healthCheckInterval", 30);
        context.put("healthCheckTimeout", 5);
        context.put("healthyThreshold", 2);
        context.put("unhealthyThreshold", 3);

        app.getNode().setContext("cfc", context);

        // When - Load DeploymentContext
        DeploymentContext cfc = DeploymentContext.from(app);

        // Then - Verify all fields loaded correctly
        // Basic fields
        assertEquals("test-stack", cfc.stackName());
        assertEquals("staging", cfc.env());
        assertEquals(RuntimeType.FARGATE, cfc.runtime());
        assertEquals(TopologyType.JENKINS_SERVICE, cfc.topology());
        assertEquals(SecurityProfile.STAGING, cfc.securityProfile());
        assertEquals("us-east-1", cfc.region());

        // Compliance fields
        assertEquals(true, cfc.guardDutyEnabled());
        assertEquals(true, cfc.auditManagerEnabled());
        assertEquals(true, cfc.awsConfigEnabled());
        // Note: createConfigInfrastructure is injected directly into ComplianceFactory, not exposed as a getter
        assertEquals("HIPAA,SOC2,GDPR", cfc.complianceFrameworks());
        assertEquals(2190, cfc.logRetentionDays());

        // Resource sizing
        assertEquals(1024, cfc.cpu());
        assertEquals(2048, cfc.memory());
        assertEquals(1, cfc.minInstanceCapacity());
        assertEquals(5, cfc.maxInstanceCapacity());
        assertEquals(70, cfc.cpuTargetUtilization());
        // Note: enableAutoScaling is written to JSON but not currently used by cloudforge-api

        // Network & Security
        assertEquals("test.example.com", cfc.domain());
        assertEquals("app", cfc.subdomain());
        assertEquals(true, cfc.enableSsl());
        assertEquals("private-with-nat", cfc.networkMode());
        assertEquals(true, cfc.wafEnabled());
        assertEquals(false, cfc.cloudfrontEnabled());
        assertEquals("alb-oidc", cfc.authMode());

        // Cognito
        assertEquals(true, cfc.cognitoAutoProvision());
        assertEquals(true, cfc.cognitoMfaEnabled());
        assertEquals("test-auth", cfc.cognitoDomainPrefix());
        assertEquals("test-users", cfc.cognitoUserPoolName());

        // Health checks
        assertEquals(300, cfc.healthCheckGracePeriod());
        assertEquals(30, cfc.healthCheckInterval());
        assertEquals(5, cfc.healthCheckTimeout());
        assertEquals(2, cfc.healthyThreshold());
        assertEquals(3, cfc.unhealthyThreshold());
    }

    /**
     * Test that logRetentionDays supports both String and Integer types.
     * This is important for backwards compatibility with existing deployment-context.json files.
     */
    @Test
    void testLogRetentionDays_StringAndIntegerSupport() {
        // Test with Integer
        App app1 = new App();
        Map<String, Object> context1 = createMinimalContext();
        context1.put("logRetentionDays", 2190);
        app1.getNode().setContext("cfc", context1);
        DeploymentContext cfc1 = DeploymentContext.from(app1);
        assertEquals(2190, cfc1.logRetentionDays());

        // Test with String (for backwards compatibility)
        App app2 = new App();
        Map<String, Object> context2 = createMinimalContext();
        context2.put("logRetentionDays", "2190");
        app2.getNode().setContext("cfc", context2);
        DeploymentContext cfc2 = DeploymentContext.from(app2);
        assertEquals(2190, cfc2.logRetentionDays());
    }

    /**
     * Test that boolean fields default correctly when not provided.
     * Some fields are Boolean (nullable) and return null when not set,
     * while others are boolean (primitive) and return false.
     */
    @Test
    void testBooleanFieldDefaults() {
        // Given - Minimal context with no boolean fields
        App app = new App();
        app.getNode().setContext("cfc", createMinimalContext());

        // When
        DeploymentContext cfc = DeploymentContext.from(app);

        // Then - Verify defaults based on actual implementation
        // Fields using boolOrNull() return null when not set
        assertNull(cfc.guardDutyEnabled());

        // Fields using bool(..., false) return false when not set
        assertFalse(cfc.auditManagerEnabled());
        assertFalse(cfc.wafEnabled());
        assertFalse(cfc.cloudfrontEnabled());
        assertFalse(cfc.enableSsl());
        assertEquals(false, cfc.cognitoAutoProvision());  // Boolean type but has default false
        assertEquals(false, cfc.cognitoMfaEnabled());     // Boolean type but has default false
    }

    /**
     * Test that numeric fields have sensible defaults.
     */
    @Test
    void testNumericFieldDefaults() {
        // Given - Minimal context
        App app = new App();
        app.getNode().setContext("cfc", createMinimalContext());

        // When
        DeploymentContext cfc = DeploymentContext.from(app);

        // Then - Verify defaults
        assertEquals(1024, cfc.cpu());
        assertEquals(2048, cfc.memory());
        assertEquals(1, cfc.minInstanceCapacity());
        assertEquals(1, cfc.maxInstanceCapacity());
        assertEquals(60, cfc.cpuTargetUtilization());
    }

    /**
     * Test enum parsing for RuntimeType.
     */
    @Test
    void testRuntimeTypeParsing() {
        // Test FARGATE
        App app1 = new App();
        Map<String, Object> context1 = createMinimalContext();
        context1.put("runtime", "FARGATE");
        app1.getNode().setContext("cfc", context1);
        assertEquals(RuntimeType.FARGATE, DeploymentContext.from(app1).runtime());

        // Test EC2
        App app2 = new App();
        Map<String, Object> context2 = createMinimalContext();
        context2.put("runtime", "EC2");
        app2.getNode().setContext("cfc", context2);
        assertEquals(RuntimeType.EC2, DeploymentContext.from(app2).runtime());
    }

    /**
     * Test enum parsing for TopologyType.
     */
    @Test
    void testTopologyTypeParsing() {
        // Test JENKINS_SERVICE
        App app1 = new App();
        Map<String, Object> context1 = createMinimalContext();
        context1.put("topology", "JENKINS_SERVICE");
        app1.getNode().setContext("cfc", context1);
        assertEquals(TopologyType.JENKINS_SERVICE, DeploymentContext.from(app1).topology());

        // Test JENKINS_SINGLE_NODE (requires runtime=EC2)
        App app2 = new App();
        Map<String, Object> context2 = createMinimalContext();
        context2.put("topology", "JENKINS_SINGLE_NODE");
        context2.put("runtime", "EC2");  // JENKINS_SINGLE_NODE requires EC2
        app2.getNode().setContext("cfc", context2);
        assertEquals(TopologyType.JENKINS_SINGLE_NODE, DeploymentContext.from(app2).topology());
    }

    /**
     * Test enum parsing for SecurityProfile.
     */
    @Test
    void testSecurityProfileParsing() {
        // Test DEV
        App app1 = new App();
        Map<String, Object> context1 = createMinimalContext();
        context1.put("securityProfile", "DEV");
        app1.getNode().setContext("cfc", context1);
        assertEquals(SecurityProfile.DEV, DeploymentContext.from(app1).securityProfile());

        // Test STAGING
        App app2 = new App();
        Map<String, Object> context2 = createMinimalContext();
        context2.put("securityProfile", "STAGING");
        app2.getNode().setContext("cfc", context2);
        assertEquals(SecurityProfile.STAGING, DeploymentContext.from(app2).securityProfile());

        // Test PRODUCTION
        App app3 = new App();
        Map<String, Object> context3 = createMinimalContext();
        context3.put("securityProfile", "PRODUCTION");
        app3.getNode().setContext("cfc", context3);
        assertEquals(SecurityProfile.PRODUCTION, DeploymentContext.from(app3).securityProfile());
    }

    /**
     * Test that compliance frameworks string is preserved exactly.
     */
    @Test
    void testComplianceFrameworksString() {
        App app = new App();
        Map<String, Object> context = createMinimalContext();
        context.put("complianceFrameworks", "HIPAA,SOC2,GDPR,PCI-DSS");
        app.getNode().setContext("cfc", context);

        DeploymentContext cfc = DeploymentContext.from(app);
        assertEquals("HIPAA,SOC2,GDPR,PCI-DSS", cfc.complianceFrameworks());
    }

    /**
     * Test authMode variations.
     */
    @Test
    void testAuthModeParsing() {
        // Test "none" authMode
        App app1 = new App();
        Map<String, Object> context1 = createMinimalContext();
        context1.put("authMode", "none");
        app1.getNode().setContext("cfc", context1);
        DeploymentContext cfc1 = DeploymentContext.from(app1);
        assertEquals("none", cfc1.authMode());

        // Test "alb-oidc" authMode (requires enableSsl=true and domain)
        App app2 = new App();
        Map<String, Object> context2 = createMinimalContext();
        context2.put("authMode", "alb-oidc");
        context2.put("enableSsl", true);
        context2.put("domain", "example.com");
        app2.getNode().setContext("cfc", context2);
        DeploymentContext cfc2 = DeploymentContext.from(app2);
        assertEquals("alb-oidc", cfc2.authMode());

        // Test "jenkins-oidc" authMode
        App app3 = new App();
        Map<String, Object> context3 = createMinimalContext();
        context3.put("authMode", "jenkins-oidc");
        app3.getNode().setContext("cfc", context3);
        DeploymentContext cfc3 = DeploymentContext.from(app3);
        assertEquals("jenkins-oidc", cfc3.authMode());
    }

    // ==================== Helper Methods ====================

    /**
     * Create a minimal context map with required fields.
     */
    private Map<String, Object> createMinimalContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("stackName", "test-stack");
        context.put("env", "dev");
        context.put("runtime", "FARGATE");
        context.put("topology", "JENKINS_SERVICE");
        context.put("securityProfile", "DEV");
        return context;
    }
}
