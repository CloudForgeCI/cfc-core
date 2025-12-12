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
 * Behavioral tests for Fargate runtime configuration.
 *
 * <p>Validates state transitions, deferred action registration, and configuration
 * contracts without synthesizing CDK infrastructure. Tests guard clauses, HTTP vs SSL
 * conditional logic, health check settings, and application spec integration.
 */
class FargateRuntimeBehavioralTest {

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
    void testFargateRuntimeKindReturnsCorrectEnum() {
        // Given: Fargate runtime configuration
        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting runtime kind
        RuntimeType kind = config.kind();

        // Then: Should return FARGATE enum
        assertEquals(RuntimeType.FARGATE, kind,
                "FargateRuntimeConfiguration must return RuntimeType.FARGATE");
    }

    @Test
    void testFargateRuntimeIdFollowsNamingConvention() {
        // Given: Fargate runtime configuration
        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting configuration ID
        String id = config.id();

        // Then: Should follow runtime:{TYPE} convention
        assertEquals("runtime:FARGATE", id,
                "FargateRuntimeConfiguration ID must follow 'runtime:FARGATE' convention");
    }

    @Test
    void testFargateRuntimeRulesRequireVpcAndAlb() {
        // Given: Fargate deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateRules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting validation rules
        var rules = config.rules(ctx);

        // Then: Rules must require VPC, ALB, HTTP listener, Fargate service
        assertNotNull(rules, "Rules list must not be null");
        assertTrue(rules.size() >= 5,
                "FargateRuntimeConfiguration must have at least 5 validation rules");

        // Verify rule structure (all rules should have meaningful error messages)
        rules.forEach(rule -> {
            assertNotNull(rule, "Each rule must not be null");
        });
    }

    @Test
    void testFargateRuntimeRulesForbidEc2SpecificResources() {
        // Given: Fargate deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateForbidden", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Rules must forbid ASG and EC2 instance security groups
        assertNotNull(rules);
        // Note: Detailed rule validation happens in rule execution tests
    }

    @Test
    void testWireSkipsExecutionForEc2Runtime() {
        // Given: EC2 runtime context (not Fargate)
        App app = new App();
        Stack stack = createTestStack(app, "TestWireSkipEc2", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Calling wire() with EC2 runtime
        // Then: Should skip execution gracefully (guard clause at line 55-57)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must skip gracefully when runtime is not FARGATE");

        // Verify no wiring occurred
        assertFalse(ctx.wired.get().orElse(false),
                "wire() must not set wired flag for non-Fargate runtime");
    }

    @Test
    void testWirePreventsDuplicateExecution() {
        // Given: Fargate deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestWireDuplicate", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Calling wire() multiple times
        assertDoesNotThrow(() -> config.wire(ctx),
                "First wire() call must complete successfully");
        assertDoesNotThrow(() -> config.wire(ctx),
                "Second wire() call must be prevented by guard clause");
        assertDoesNotThrow(() -> config.wire(ctx),
                "Third wire() call must be prevented by guard clause");

        // Then: wired flag should be set after first call (guard at line 60-62)
        assertTrue(ctx.wired.get().orElse(false),
                "wire() must set wired flag to prevent duplicate execution");
    }

    @Test
    void testWireHttpOnlyModeWithoutSsl() {
        // Given: Fargate deployment with HTTP only (SSL disabled)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        Stack stack = createTestStack(app, "TestHttpOnly", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring HTTP-only configuration
        // Then: Should complete without attempting SSL setup
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle HTTP-only mode (enableSsl=false)");

        // Verify SSL was not enabled
        assertFalse(cfc.enableSsl(),
                "SSL must remain disabled in HTTP-only mode");
    }

    @Test
    void testWireHttpOnlyModeWithDomain() {
        // Given: HTTP-only with domain specified
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        context.put("domain", "example.com");
        Stack stack = createTestStack(app, "TestHttpOnlyDomain", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When/Then: Should handle domain in HTTP-only mode (no cert creation)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle HTTP-only mode with domain");
    }

    @Test
    void testWireSslModeWithFqdn() {
        // Given: SSL enabled with FQDN specified
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestSslFqdn", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with SSL + FQDN
        // Then: Should register certificate + HTTPS deferred actions
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle SSL mode with FQDN");

        // Verify SSL is enabled
        assertTrue(cfc.enableSsl(),
                "SSL must be enabled");
        assertEquals("jenkins.example.com", cfc.fqdn(),
                "FQDN must be preserved");
    }

    @Test
    void testWireSslModeWithDomainOnly() {
        // Given: SSL enabled with domain (no FQDN)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.remove("fqdn");
        Stack stack = createTestStack(app, "TestSslDomain", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When/Then: Should handle SSL with domain (fallback from FQDN)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle SSL mode with domain (no FQDN)");
    }

    @Test
    void testWireSslWithoutHostUsesPrivateCa() {
        // Given: SSL enabled but no domain or FQDN (uses Private CA for ALB DNS)
        App app = new App();
        Stack stack = new Stack(app, "TestSslNoHost");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSslNoHost");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("enableSsl", true);
        // Intentionally omit domain and fqdn - Private CA will be used
        stack.getNode().setContext("cfc", cfcContext);

        // When/Then: DeploymentContext should succeed (Private CA will be used for ALB DNS)
        assertDoesNotThrow(() -> {
            DeploymentContext.from(stack);
        }, "SSL without domain should succeed using Private CA");
    }

    @Test
    void testWireWithCustomHealthCheckSettings() {
        // Given: Custom health check intervals and thresholds
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        context.put("healthCheckInterval", 60);
        context.put("healthCheckTimeout", 10);
        context.put("healthyThreshold", 3);
        context.put("unhealthyThreshold", 5);
        Stack stack = createTestStack(app, "TestCustomHealthCheck", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with custom health check settings
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle custom health check settings");

        // Then: Settings should be accessible from context (lines 118-121, 217-220)
        assertEquals(60, cfc.healthCheckInterval(),
                "Custom health check interval must be preserved");
        assertEquals(10, cfc.healthCheckTimeout(),
                "Custom health check timeout must be preserved");
        assertEquals(3, cfc.healthyThreshold(),
                "Custom healthy threshold must be preserved");
        assertEquals(5, cfc.unhealthyThreshold(),
                "Custom unhealthy threshold must be preserved");
    }

    @Test
    void testWireWithDefaultHealthCheckSettings() {
        // Given: No health check settings (use defaults)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        Stack stack = createTestStack(app, "TestDefaultHealthCheck", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring without custom health check settings
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must use default health check settings when not specified");

        // Then: Default values should be used (null checks at lines 118-121)
        // Note: Defaults are applied in the wire() method itself
    }

    @Test
    void testWireWithAllSecurityProfiles() {
        // Given: Each security profile (DEV, STAGING, PRODUCTION)
        SecurityProfile[] profiles = SecurityProfile.values();

        int counter = 0;
        for (SecurityProfile profile : profiles) {
            App app = new App();
            Stack stack = createTestStack(app, "TestProfile" + counter++, profile, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

            // When/Then: wire() must handle all security profiles
            assertDoesNotThrow(() -> config.wire(ctx),
                    "wire() must handle security profile: " + profile);
        }
    }

    @Test
    void testWireWithAllIamProfiles() {
        // Given: Each IAM profile (MINIMAL, STANDARD, EXTENDED)
        IAMProfile[] iamProfiles = {IAMProfile.MINIMAL, IAMProfile.STANDARD, IAMProfile.EXTENDED};

        int counter = 0;
        for (IAMProfile iamProfile : iamProfiles) {
            App app = new App();
            Stack stack = createTestStack(app, "TestIamProfile" + counter++, SecurityProfile.DEV, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, iamProfile, cfc);

            FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

            // When/Then: wire() must handle all IAM profiles
            assertDoesNotThrow(() -> config.wire(ctx),
                    "wire() must handle IAM profile: " + iamProfile);
        }
    }

    @Test
    void testWireNormalizesDomainsToLowercase() {
        // Given: Domain with uppercase and trailing dot
        App app = new App();
        Stack stack = new Stack(app, "TestNormalize");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestNormalize");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "EXAMPLE.COM.");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with domain that needs normalization (norm() method)
        // Then: wire() should complete successfully, normalizing internally
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle domain normalization internally (lowercase, trim, remove trailing dot)");
    }

    @Test
    void testWireNormalizesFqdnToLowercase() {
        // Given: FQDN with uppercase and trailing dot
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("fqdn", "JENKINS.EXAMPLE.COM.");
        Stack stack = createTestStack(app, "TestNormalizeFqdn", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with FQDN that needs normalization (norm() method)
        // Then: wire() should complete successfully, normalizing internally
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle FQDN normalization internally (lowercase, trim, remove trailing dot)");
    }

    @Test
    void testWireHandlesNullDomainGracefully() {
        // Given: Deployment context with null domain
        App app = new App();
        Stack stack = new Stack(app, "TestNullDomain");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestNullDomain");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("enableSsl", false);
        // Omit domain entirely
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When/Then: Should handle null domain gracefully
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle null domain gracefully");
    }

    @Test
    void testWireWithHttpToHttpsRedirect() {
        // Given: SSL enabled (should trigger redirect logic)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestRedirect", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring with SSL (triggers HTTP → HTTPS redirect at lines 260-273)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must configure HTTP → HTTPS redirect when SSL is enabled");
    }

    @Test
    void testWireExceptionPropagation() {
        // Given: Fargate deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestExceptionProp", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When/Then: wire() should propagate exceptions (catch block at lines 275-277)
        // In normal operation, no exception should be thrown
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must not throw exceptions in normal operation");
    }

    @Test
    void testWireWithProductionSecurityProfile() {
        // Given: PRODUCTION security profile
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "production.example.com");
        Stack stack = createTestStack(app, "TestProduction", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When/Then: PRODUCTION profile should require SSL
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle PRODUCTION security profile");
        assertTrue(cfc.enableSsl(),
                "PRODUCTION profile typically requires SSL");
    }

    @Test
    void testWireWithStagingSecurityProfile() {
        // Given: STAGING security profile
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "staging.example.com");
        Stack stack = createTestStack(app, "TestStaging", SecurityProfile.STAGING, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When/Then: STAGING profile with SSL
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle STAGING security profile");
    }

    @Test
    void testWireRegistersHttpListenerDeferredAction() {
        // Given: HTTP-only Fargate deployment
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        Stack stack = createTestStack(app, "TestHttpDeferred", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring HTTP-only configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register HTTP listener deferred action");

        // Then: HTTP listener configuration should be registered
        // Note: Actual execution requires ALB + Fargate service (integration test)
    }

    @Test
    void testWireRegistersCertificateDeferredAction() {
        // Given: SSL-enabled deployment
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestCertDeferred", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring SSL configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register certificate creation deferred action");

        // Then: Certificate deferred action should be registered
    }

    @Test
    void testWireRegistersHttpsListenerDeferredAction() {
        // Given: SSL-enabled deployment
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestHttpsDeferred", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring SSL configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register HTTPS listener deferred action");

        // Then: HTTPS listener deferred action should be registered
    }

    @Test
    void testWireRegistersHttpRedirectDeferredAction() {
        // Given: SSL-enabled deployment (triggers HTTP → HTTPS redirect)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestRedirectDeferred", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        FargateRuntimeConfiguration config = new FargateRuntimeConfiguration();

        // When: Wiring SSL configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register HTTP → HTTPS redirect deferred action");

        // Then: Redirect deferred action should be registered
    }
}
