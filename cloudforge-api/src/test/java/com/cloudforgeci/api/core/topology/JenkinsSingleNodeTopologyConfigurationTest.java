package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for JenkinsSingleNodeTopologyConfiguration.
 *
 * Tests Jenkins Single Node topology which handles:
 * - EC2 runtime requirement (Fargate not supported)
 * - OIDC authentication requiring SSL
 * - DNS record creation (A/AAAA) for ALB
 * - SSL/domain validation rules
 * - Single instance (no autoscaling)
 */
class JenkinsSingleNodeTopologyConfigurationTest {

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
    void testJenkinsSingleNodeTopologyConfigurationKind() {
        // Given: Jenkins Single Node topology configuration
        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Getting kind
        TopologyType kind = config.kind();

        // Then: Should return JENKINS_SINGLE_NODE
        assertEquals(TopologyType.JENKINS_SINGLE_NODE, kind);
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationId() {
        // Given: Jenkins Single Node topology configuration
        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Should return expected ID
        assertEquals("topology:JENKINS_SINGLE_NODE", id);
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationRules() {
        // Given: A Jenkins Single Node deployment with EC2 runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeRules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have rules for runtime validation (EC2 only), OIDC/SSL, etc.
        assertNotNull(rules);
        assertTrue(rules.size() >= 3);
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationBasicWiring() {
        // Given: A basic Jenkins Single Node deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeBasic", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring configuration
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithSsl() {
        // Given: A Jenkins Single Node deployment with SSL
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeSsl", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with SSL
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithSubdomain() {
        // Given: A Jenkins Single Node deployment with subdomain
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("subdomain", "jenkins");
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeSubdomain", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with subdomain
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestJenkinsSingleNodeProfile" + counter++, profile, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                    profile, iamProfile, cfc);

            JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

            // When: Wiring for each profile
            assertDoesNotThrow(() -> config.wire(ctx),
                    "JenkinsSingleNodeTopologyConfiguration should not throw for security profile: " + profile);
        }
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithEc2Runtime() {
        // Given: A Jenkins Single Node deployment with EC2 runtime (required)
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeEc2", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with EC2 runtime
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithMinimalIamProfile() {
        // Given: A Jenkins Single Node deployment with MINIMAL IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeMinimalIam", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with MINIMAL IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithExtendedIamProfile() {
        // Given: A Jenkins Single Node deployment with EXTENDED IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeExtendedIam", SecurityProfile.PRODUCTION, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, IAMProfile.EXTENDED, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with EXTENDED IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationMultipleWireCalls() {
        // Given: A Jenkins Single Node deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeMultiWire", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Calling wire() multiple times
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should handle duplicate wire calls gracefully (guard clause)
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithSslAndSubdomain() {
        // Given: A Jenkins Single Node deployment with SSL and subdomain
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("subdomain", "jenkins");
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeSslSubdomain", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with SSL and subdomain
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithSslAndFqdn() {
        // Given: A Jenkins Single Node deployment with SSL and fqdn
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "jenkins.example.com");
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeSslFqdn", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with SSL and fqdn
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithDomainOnly() {
        // Given: A Jenkins Single Node deployment with domain only (no subdomain)
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeDomainOnly", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring with domain only
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyConfigurationWithStagingProfile() {
        // Given: A Jenkins Single Node deployment with STAGING profile
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("fqdn", "staging.example.com");
        Stack stack = createTestStack(app, "TestJenkinsSingleNodeStaging", SecurityProfile.STAGING, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.STAGING, iamProfile, cfc);

        JenkinsSingleNodeTopologyConfiguration config = new JenkinsSingleNodeTopologyConfiguration();

        // When: Wiring for STAGING
        assertDoesNotThrow(() -> config.wire(ctx));
    }
}
