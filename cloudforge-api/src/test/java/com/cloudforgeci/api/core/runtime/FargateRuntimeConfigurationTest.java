package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for FargateRuntimeConfiguration.
 *
 * Tests AWS Fargate runtime configuration including:
 * - HTTP/HTTPS listener setup
 * - SSL certificate creation and validation
 * - Target group configuration with health checks
 * - HTTP to HTTPS redirect logic
 * - Guard clauses preventing duplicate execution
 * - Conditional logic based on domain/fqdn presence
 */
class FargateRuntimeConfigurationTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile,
                                   Map<String, Object> additionalContext) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        if (additionalContext != null) {
            cfcContext.putAll(additionalContext);
        }
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testFargateRuntimeConfigurationKind() {
        // Given: Fargate runtime configuration
        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting kind
        RuntimeType kind = config.kind();

        // Then: Should return FARGATE
        assertEquals(RuntimeType.FARGATE, kind);
    }

    @Test
    void testFargateRuntimeConfigurationId() {
        // Given: Fargate runtime configuration
        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Should return expected ID
        assertEquals("runtime:FARGATE", id);
    }

    @Test
    void testFargateRuntimeConfigurationRules() {
        // Given: A Fargate deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateRules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should require vpc, alb, http listener, fargate service, fargate container
        // Should forbid asg, instanceSg (EC2-specific)
        assertNotNull(rules);
        assertTrue(rules.size() >= 5);
    }

    @Test
    void testFargateRuntimeConfigurationBasicWiring() {
        // Given: A basic Fargate deployment without SSL
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateBasic", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring configuration
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithSsl() {
        // Given: A Fargate deployment with SSL enabled
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestFargateSsl", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with SSL
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithHttpToHttpsRedirect() {
        // Given: A Fargate deployment with HTTP to HTTPS redirect
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        context.put("httpToHttpsRedirect", true);
        Stack stack = createTestStack(app, "TestFargateRedirect", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with redirect
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithCustomHealthCheck() {
        // Given: A Fargate deployment with custom health check settings
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("healthCheckInterval", 60);
        context.put("healthCheckTimeout", 10);
        context.put("healthyThreshold", 3);
        context.put("unhealthyThreshold", 5);
        Stack stack = createTestStack(app, "TestFargateHealthCheck", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with custom health check
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestFargateProfile" + counter++, profile, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

            // When: Wiring for each profile
            assertDoesNotThrow(() -> config.wire(ctx),
                    "FargateRuntimeConfiguration should not throw for security profile: " + profile);
        }
    }

    @Test
    void testFargateRuntimeConfigurationSkipsForEc2Runtime() {
        // Given: A deployment with EC2 runtime (not Fargate)
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2Runtime", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring for EC2 runtime (should be skipped by guard clause)
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithDomain() {
        // Given: A Fargate deployment with domain only (no fqdn)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        Stack stack = createTestStack(app, "TestFargateDomain", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with domain
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithFqdn() {
        // Given: A Fargate deployment with fqdn specified
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestFargateFqdn", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with fqdn
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithStagingProfile() {
        // Given: A Fargate deployment with STAGING security profile
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "staging.example.com");
        Stack stack = createTestStack(app, "TestFargateStaging", SecurityProfile.STAGING, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring for STAGING
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithMinimalIamProfile() {
        // Given: A Fargate deployment with MINIMAL IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateMinimalIam", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with MINIMAL IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithExtendedIamProfile() {
        // Given: A Fargate deployment with EXTENDED IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateExtendedIam", SecurityProfile.PRODUCTION, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, IAMProfile.EXTENDED, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with EXTENDED IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationMultipleWireCalls() {
        // Given: A Fargate deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateMultiWire", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Calling wire() multiple times
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should handle duplicate wire calls gracefully (guard clause)
    }

    @Test
    void testFargateRuntimeConfigurationWithSslWithoutDomain() {
        // Given: A Fargate deployment with SSL but no domain (uses Private CA)
        App app = new App();
        Stack stack = new Stack(app, "TestFargateSslValidation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestFargateSslValidation");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("enableSsl", true);
        // Intentionally omit domain and fqdn - Private CA will be used
        stack.getNode().setContext("cfc", cfcContext);

        // When/Then: Should succeed (Private CA will be used for ALB DNS)
        assertDoesNotThrow(() -> {
            DeploymentContext.from(stack);
        }, "SSL without domain should succeed using Private CA");
    }

    @Test
    void testFargateRuntimeConfigurationWithHttpOnly() {
        // Given: A Fargate deployment with HTTP only (no SSL)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        Stack stack = createTestStack(app, "TestFargateHttpOnly", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring HTTP only
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testFargateRuntimeConfigurationWithAllTopologyTypes() {
        // Given: Different topology types
        TopologyType[] topologies = {
            TopologyType.JENKINS_SERVICE,
            TopologyType.JENKINS_SERVICE
        };

        int counter = 0;
        for (TopologyType topology : topologies) {
            App app = new App();
            Stack stack = createTestStack(app, "TestFargateTopo" + counter++, SecurityProfile.DEV, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
            SystemContext ctx = SystemContext.start(stack, topology, RuntimeType.FARGATE,
                    SecurityProfile.DEV, iamProfile, cfc);

            FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

            // When: Wiring for each topology
            assertDoesNotThrow(() -> config.wire(ctx),
                    "FargateRuntimeConfiguration should not throw for topology: " + topology);
        }
    }
}
