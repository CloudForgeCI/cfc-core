package com.cloudforgeci.api.core.topology;

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
 * Test suite for JenkinsServiceTopologyConfiguration.
 *
 * Tests Jenkins Service topology which handles:
 * - Support for both Fargate and EC2 runtimes
 * - OIDC authentication requiring SSL
 * - DNS record creation (A/AAAA) for ALB
 * - Auto-scaling configuration for Fargate services
 * - Auto-scaling configuration for EC2 Auto Scaling Groups
 * - SSL/domain validation rules
 */
class JenkinsServiceTopologyConfigurationTest {

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
    void testJenkinsServiceTopologyConfigurationKind() {
        // Given: Jenkins Service topology configuration
        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting kind
        TopologyType kind = config.kind();

        // Then: Should return JENKINS_SERVICE
        assertEquals(TopologyType.JENKINS_SERVICE, kind);
    }

    @Test
    void testJenkinsServiceTopologyConfigurationId() {
        // Given: Jenkins Service topology configuration
        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Should return expected ID
        assertEquals("topology:JENKINS_SERVICE", id);
    }

    @Test
    void testJenkinsServiceTopologyConfigurationRulesWithFargate() {
        // Given: A Jenkins Service deployment with Fargate runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceFargateRules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have rules for runtime validation, OIDC/SSL, etc.
        assertNotNull(rules);
        assertTrue(rules.size() >= 3);
    }

    @Test
    void testJenkinsServiceTopologyConfigurationRulesWithEc2() {
        // Given: A Jenkins Service deployment with EC2 runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceEc2Rules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should support EC2 runtime
        assertNotNull(rules);
        assertTrue(rules.size() >= 3);
    }

    @Test
    void testJenkinsServiceTopologyConfigurationBasicWiring() {
        // Given: A basic Jenkins Service deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceBasic", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring configuration
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithAutoscaling() {
        // Given: A Jenkins Service deployment with autoscaling
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("minInstanceCapacity", 2);
        context.put("maxInstanceCapacity", 10);
        context.put("cpuTargetUtilization", 70);
        Stack stack = createTestStack(app, "TestJenkinsServiceAutoscaling", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with autoscaling
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithSsl() {
        // Given: A Jenkins Service deployment with SSL
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestJenkinsServiceSsl", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with SSL
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithSubdomain() {
        // Given: A Jenkins Service deployment with subdomain
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("subdomain", "jenkins");
        Stack stack = createTestStack(app, "TestJenkinsServiceSubdomain", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with subdomain
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestJenkinsServiceProfile" + counter++, profile, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

            // When: Wiring for each profile
            assertDoesNotThrow(() -> config.wire(ctx),
                    "JenkinsServiceTopologyConfiguration should not throw for security profile: " + profile);
        }
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithEc2Runtime() {
        // Given: A Jenkins Service deployment with EC2 runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceEc2", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with EC2 runtime
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithFargateRuntime() {
        // Given: A Jenkins Service deployment with Fargate runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceFargate", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with Fargate runtime
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithMinimalIamProfile() {
        // Given: A Jenkins Service deployment with MINIMAL IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceMinimalIam", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with MINIMAL IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithExtendedIamProfile() {
        // Given: A Jenkins Service deployment with EXTENDED IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceExtendedIam", SecurityProfile.PRODUCTION, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, IAMProfile.EXTENDED, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with EXTENDED IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationMultipleWireCalls() {
        // Given: A Jenkins Service deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsServiceMultiWire", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Calling wire() multiple times
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should handle duplicate wire calls gracefully
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithDefaultAutoscaling() {
        // Given: A Jenkins Service deployment with default autoscaling settings
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("minInstanceCapacity", 1);
        context.put("maxInstanceCapacity", 5);
        // cpuTargetUtilization will default to 60
        Stack stack = createTestStack(app, "TestJenkinsServiceDefaultAutoscaling", SecurityProfile.STAGING, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with default autoscaling
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithNoAutoscaling() {
        // Given: A Jenkins Service deployment without autoscaling (single instance)
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("minInstanceCapacity", 1);
        context.put("maxInstanceCapacity", 1);
        Stack stack = createTestStack(app, "TestJenkinsServiceNoAutoscaling", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring without autoscaling
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithSslAndSubdomain() {
        // Given: A Jenkins Service deployment with SSL and subdomain
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("subdomain", "jenkins");
        Stack stack = createTestStack(app, "TestJenkinsServiceSslSubdomain", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with SSL and subdomain
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsServiceTopologyConfigurationWithSslAndFqdn() {
        // Given: A Jenkins Service deployment with SSL and fqdn
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestJenkinsServiceSslFqdn", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Wiring with SSL and fqdn
        assertDoesNotThrow(() -> config.wire(ctx));
    }
}
