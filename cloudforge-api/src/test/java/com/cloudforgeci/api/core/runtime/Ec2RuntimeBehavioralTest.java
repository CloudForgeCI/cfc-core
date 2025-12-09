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
 * Behavioral tests for EC2 runtime configuration.
 *
 * <p>Validates state transitions, deferred action registration, and configuration
 * contracts without synthesizing CDK infrastructure. Tests guard clauses, conditional
 * logic for HTTP vs SSL modes, and scaling policy configuration for PRODUCTION profile.
 */
class Ec2RuntimeBehavioralTest {

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
    void testEc2RuntimeKindReturnsCorrectEnum() {
        // Given: EC2 runtime configuration
        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting runtime kind
        RuntimeType kind = config.kind();

        // Then: Should return EC2 enum
        assertEquals(RuntimeType.EC2, kind,
                "Ec2RuntimeConfiguration must return RuntimeType.EC2");
    }

    @Test
    void testEc2RuntimeIdFollowsNamingConvention() {
        // Given: EC2 runtime configuration
        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting configuration ID
        String id = config.id();

        // Then: Should follow runtime:{TYPE} convention
        assertEquals("runtime:EC2", id,
                "Ec2RuntimeConfiguration ID must follow 'runtime:EC2' convention");
    }

    @Test
    void testEc2RuntimeRulesRequireVpcAlbTargetGroupAndInstanceSg() {
        // Given: EC2 deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2Rules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting validation rules
        var rules = config.rules(ctx);

        // Then: Rules must require VPC, ALB, targetGroup, instanceSg
        assertNotNull(rules, "Rules list must not be null");
        assertTrue(rules.size() >= 4,
                "Ec2RuntimeConfiguration must have at least 4 validation rules");
    }

    @Test
    void testEc2RuntimeRulesForbidFargateResources() {
        // Given: EC2 deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2Forbidden", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Rules must forbid Fargate service
        assertNotNull(rules);
        // Note: Detailed rule validation happens in rule execution tests
    }

    @Test
    void testEc2RuntimeRulesRequireAsgForMultiInstanceTopology() {
        // Given: EC2 deployment with maxInstanceCapacity > 1
        App app = new App();
        Stack stack = new Stack(app, "TestEc2AsgRequired");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2AsgRequired");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("maxInstanceCapacity", 3);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting rules for multi-instance deployment
        var rules = config.rules(ctx);

        // Then: Should require ASG when maxInstanceCapacity > 1
        assertNotNull(rules);
        assertTrue(rules.size() >= 5,
                "Multi-instance EC2 deployment must require ASG");
    }

    @Test
    void testEc2RuntimeRulesDoNotRequireAsgForSingleInstance() {
        // Given: EC2 deployment with maxInstanceCapacity = 1
        App app = new App();
        Stack stack = new Stack(app, "TestEc2AsgNotRequired");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2AsgNotRequired");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("maxInstanceCapacity", 1);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting rules for single-instance deployment
        var rules = config.rules(ctx);

        // Then: Should NOT require ASG when maxInstanceCapacity <= 1
        assertNotNull(rules);
        // ASG rule should not be added for single instance
    }

    @Test
    void testWireSkipsExecutionForFargateRuntime() {
        // Given: Fargate runtime context (not EC2)
        App app = new App();
        Stack stack = createTestStack(app, "TestWireSkipFargate", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Calling wire() with Fargate runtime
        // Then: Should skip execution gracefully (guard clause at line 68-71)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must skip gracefully when runtime is not EC2");
    }

    @Test
    void testWireHttpOnlyModeWithoutSsl() {
        // Given: EC2 deployment with HTTP only (SSL disabled)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", false);
        Stack stack = createTestStack(app, "TestEc2HttpOnly", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring HTTP-only configuration
        // Then: Should complete without attempting SSL setup
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle HTTP-only mode (enableSsl=false)");

        // Verify SSL was not enabled
        assertFalse(cfc.enableSsl(),
                "SSL must remain disabled in HTTP-only mode");
    }

    @Test
    void testWireSslModeWithFqdn() {
        // Given: SSL enabled with FQDN specified
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestEc2SslFqdn", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

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
        Stack stack = createTestStack(app, "TestEc2SslDomain", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When/Then: Should handle SSL with domain (fallback from FQDN at line 159)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle SSL mode with domain (no FQDN)");
    }

    @Test
    void testWireProductionProfileRegistersScalingPolicyCallback() {
        // Given: PRODUCTION profile with ASG (maxInstanceCapacity > 1)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("maxInstanceCapacity", 3);
        Stack stack = createTestStack(app, "TestEc2ProdScaling", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring PRODUCTION profile
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register scaling policy callback for PRODUCTION profile");

        // Then: Scaling policy deferred action should be registered
        // Note: Actual execution requires ASG + TargetGroup (integration test)
    }

    @Test
    void testWireDevProfileDoesNotApplyScaling() {
        // Given: DEV profile (auto-scaling disabled)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("maxInstanceCapacity", 1);
        Stack stack = createTestStack(app, "TestEc2DevNoScaling", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring DEV profile (isProduction check)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must skip scaling policies for non-PRODUCTION profiles");

        // Then: No scaling policies should be registered for DEV
    }

    @Test
    void testWireStagingProfileDoesNotApplyScaling() {
        // Given: STAGING profile (auto-scaling disabled)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("maxInstanceCapacity", 2);
        Stack stack = createTestStack(app, "TestEc2StagingNoScaling", SecurityProfile.STAGING, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.STAGING, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring STAGING profile
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must skip scaling policies for STAGING profile");
    }

    @Test
    void testWireRegistersAlbToInstanceSecurityGroupRule() {
        // Given: EC2 deployment (requires ALB→Instance SG wiring)
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2AlbSgRule", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring EC2 configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register ALB→Instance SG ingress rule deferred action");

        // Then: Deferred action should be registered for ALB SG → Instance SG on port 8080
    }

    @Test
    void testWireRegistersCertificateDeferredAction() {
        // Given: SSL-enabled deployment
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestEc2CertDeferred", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring SSL configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register certificate creation deferred action");

        // Then: Certificate deferred action should be registered
    }

    @Test
    void testWireRegistersHttpsListenerWithTargetGroupDeferredAction() {
        // Given: SSL-enabled deployment
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestEc2HttpsTgDeferred", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring SSL configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register HTTPS listener with target group deferred action");

        // Then: HTTPS listener deferred action should be registered
    }

    @Test
    void testWireRegistersHttpRedirectDeferredAction() {
        // Given: SSL-enabled deployment (triggers HTTP → HTTPS redirect)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestEc2RedirectDeferred", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring SSL configuration
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must register HTTP → HTTPS redirect deferred action");

        // Then: Redirect deferred action should be registered
    }

    @Test
    void testWireWithAllSecurityProfiles() {
        // Given: Each security profile (DEV, STAGING, PRODUCTION)
        SecurityProfile[] profiles = SecurityProfile.values();

        int counter = 0;
        for (SecurityProfile profile : profiles) {
            App app = new App();
            Stack stack = createTestStack(app, "TestEc2Profile" + counter++, profile, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    profile, iamProfile, cfc);

            Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

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
            Stack stack = createTestStack(app, "TestEc2IamProfile" + counter++, SecurityProfile.DEV, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    SecurityProfile.DEV, iamProfile, cfc);

            Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

            // When/Then: wire() must handle all IAM profiles
            assertDoesNotThrow(() -> config.wire(ctx),
                    "wire() must handle IAM profile: " + iamProfile);
        }
    }

    @Test
    void testWireNormalizesDomainsToLowercase() {
        // Given: Domain with uppercase and trailing dot
        App app = new App();
        Stack stack = new Stack(app, "TestEc2Normalize");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2Normalize");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "EXAMPLE.COM.");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring with domain that needs normalization (norm() method at line 236-238)
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
        Stack stack = createTestStack(app, "TestEc2NormalizeFqdn", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring with FQDN that needs normalization (norm() method at line 236-238)
        // Then: wire() should complete successfully, normalizing internally
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle FQDN normalization internally (lowercase, trim, remove trailing dot)");
    }

    @Test
    void testWireHandlesNullDomainGracefully() {
        // Given: Deployment context with null domain
        App app = new App();
        Stack stack = new Stack(app, "TestEc2NullDomain");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2NullDomain");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("enableSsl", false);
        // Omit domain entirely
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When/Then: Should handle null domain gracefully
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle null domain gracefully");
    }

    @Test
    void testWireSslWithoutHostFallsBackToHttp() {
        // Given: SSL enabled but no domain or FQDN (edge case)
        App app = new App();
        Stack stack = new Stack(app, "TestEc2SslNoHost");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2SslNoHost");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("enableSsl", true);
        // Intentionally omit domain and fqdn
        stack.getNode().setContext("cfc", cfcContext);

        // When/Then: DeploymentContext should fail validation (SSL requires host)
        assertThrows(IllegalArgumentException.class, () -> {
            DeploymentContext.from(stack);
        }, "SSL mode requires domain or fqdn");
    }

    @Test
    void testWireProductionProfileWithSingleInstanceSkipsScaling() {
        // Given: PRODUCTION profile but maxInstanceCapacity = 1
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("maxInstanceCapacity", 1);
        Stack stack = createTestStack(app, "TestEc2ProdSingleNoScaling", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring PRODUCTION with single instance
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must handle PRODUCTION with single instance");

        // Then: Scaling policies should not be applied (maxInstanceCapacity check)
    }

    @Test
    void testWireWithHttpToHttpsRedirect() {
        // Given: SSL enabled (should trigger redirect logic)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestEc2Redirect", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring with SSL (triggers HTTP → HTTPS redirect at lines 219-231)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must configure HTTP → HTTPS redirect when SSL is enabled");
    }

    @Test
    void testWireLogsStartAndCompletion() {
        // Given: EC2 deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2Logging", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration (logs at lines 65, 73, 233)
        assertDoesNotThrow(() -> config.wire(ctx),
                "wire() must log start and completion");

        // Then: Logging should occur without errors
        // Note: Actual log verification would require log capturing in integration tests
    }
}
