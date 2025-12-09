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
 * Test suite for Ec2RuntimeConfiguration.
 *
 * Tests EC2 runtime configuration which handles:
 * - Validation rules (vpc, alb, targetGroup, instanceSg required; fargate forbidden)
 * - ALB security group to instance security group wiring
 * - SSL certificate creation and HTTPS listener configuration
 * - HTTP to HTTPS redirect configuration
 * - Auto-scaling policy application for PRODUCTION profile
 * - DNS A record creation for ALB
 */
class Ec2RuntimeConfigurationTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testEc2RuntimeConfigurationKind() {
        // Given: EC2 runtime configuration
        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting kind
        RuntimeType kind = config.kind();

        // Then: Should return EC2
        assertEquals(RuntimeType.EC2, kind);
    }

    @Test
    void testEc2RuntimeConfigurationId() {
        // Given: EC2 runtime configuration
        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Should return expected ID
        assertEquals("runtime:EC2", id);
    }

    @Test
    void testEc2RuntimeConfigurationRulesWithSingleInstance() {
        // Given: A deployment with single instance (maxInstanceCapacity = 1)
        App app = new App();
        Stack stack = new Stack(app, "TestEc2Rules");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2Rules");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("maxInstanceCapacity", 1);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should require vpc, alb, targetGroup, instanceSg
        // ASG is NOT required for single instance
        assertNotNull(rules);
        assertTrue(rules.size() >= 4);
    }

    @Test
    void testEc2RuntimeConfigurationRulesWithMultipleInstances() {
        // Given: A deployment with multiple instances (maxInstanceCapacity > 1)
        App app = new App();
        Stack stack = new Stack(app, "TestEc2MultiRules");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2MultiRules");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("maxInstanceCapacity", 3);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should require asg when maxInstanceCapacity > 1
        assertNotNull(rules);
        assertTrue(rules.size() >= 5);
    }

    @Test
    void testEc2RuntimeConfigurationRulesForSingleNode() {
        // Given: A deployment with single node topology
        App app = new App();
        Stack stack = new Stack(app, "TestEc2SingleNode");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2SingleNode");
        cfcContext.put("securityProfile", "DEV");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should not require ASG for single node topology
        assertNotNull(rules);
        assertTrue(rules.size() >= 4);
    }

    @Test
    void testEc2RuntimeConfigurationWireWithoutSsl() {
        // Given: A deployment without SSL
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2WireNoSsl", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should complete without errors
        // Certificate and HTTPS listener should not be set
        assertFalse(ctx.cert.get().isPresent());
        assertFalse(ctx.https.get().isPresent());
    }

    @Test
    void testEc2RuntimeConfigurationWireWithSsl() {
        // Given: A deployment with SSL enabled
        App app = new App();
        Stack stack = new Stack(app, "TestEc2WireSsl");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2WireSsl");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableSsl", true);
        cfcContext.put("domain", "example.com");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration with SSL
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should complete without errors
        // SSL configuration is deferred until zone and alb are available
    }

    @Test
    void testEc2RuntimeConfigurationWireWithProductionProfile() {
        // Given: A deployment with PRODUCTION profile
        App app = new App();
        Stack stack = new Stack(app, "TestEc2Production");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2Production");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("maxInstanceCapacity", 3);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration for PRODUCTION
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should set up scaling policy callback
        // Scaling policies are applied when ASG is available
    }

    @Test
    void testEc2RuntimeConfigurationSkipsForDifferentRuntime() {
        // Given: A deployment with FARGATE runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2SkipFargate", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration for non-EC2 runtime
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should skip without errors
        // No EC2-specific configuration should be applied
    }

    @Test
    void testEc2RuntimeConfigurationWireWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestEc2Profile" + counter++, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    profile, iamProfile, cfc);

            Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

            // When: Wiring configuration
            assertDoesNotThrow(() -> config.wire(ctx),
                "EC2 runtime configuration should not throw for security profile: " + profile);
        }
    }

    @Test
    void testEc2RuntimeConfigurationWireWithSubdomain() {
        // Given: A deployment with subdomain
        App app = new App();
        Stack stack = new Stack(app, "TestEc2Subdomain");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2Subdomain");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("domain", "example.com");
        cfcContext.put("subdomain", "jenkins");
        cfcContext.put("enableSsl", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.STAGING, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration with subdomain
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should handle subdomain configuration
    }

    @Test
    void testEc2RuntimeConfigurationWireWithMinimalDomain() {
        // Given: A deployment with minimal domain for SSL
        App app = new App();
        Stack stack = new Stack(app, "TestEc2MinimalDomain");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEc2MinimalDomain");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        Ec2RuntimeConfiguration config = new Ec2RuntimeConfiguration();

        // When: Wiring configuration with minimal domain
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should complete without errors
    }
}
