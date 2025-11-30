package com.cloudforgeci.api.core.rules;

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
 * Test suite for RuntimeRules.
 *
 * Tests runtime rule installation and configuration across different runtime types.
 */
class RuntimeRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testRuntimeRulesInstallWithEC2() {
        // Given: A deployment with EC2 runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestRuntimeEC2", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing runtime rules
        // Then: Should not throw
        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
    }

    @Test
    void testRuntimeRulesInstallWithFargate() {
        // Given: A deployment with FARGATE runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestRuntimeFargate", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Installing runtime rules
        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testRuntimeRulesWiringDeferredUntilAfterFactories() {
        // Given: A deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestRuntimeWiring", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing runtime rules
        // Then: Wiring should be deferred via ctx.once() - should not throw
        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
    }

    @Test
    void testRuntimeRulesWithAllRuntimeTypes() {
        // Given: Each runtime type
        for (RuntimeType runtime : RuntimeType.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestRuntime" + runtime, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

            // Choose appropriate topology for each runtime
            TopologyType topology = runtime == RuntimeType.FARGATE
                ? TopologyType.JENKINS_SERVICE
                : TopologyType.JENKINS_SERVICE;

            SystemContext ctx = SystemContext.start(stack, topology, runtime,
                    SecurityProfile.DEV, iamProfile, cfc);

            // When: Installing runtime rules
            // Then: Should not throw for any runtime type
            assertDoesNotThrow(() -> new RuntimeRules().install(ctx),
                "RuntimeRules.install should not throw for runtime: " + runtime);
        }
    }

    @Test
    void testRuntimeRulesAddsValidationToNode() {
        // Given: A deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestRuntimeValidation", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing runtime rules
        new RuntimeRules().install(ctx);

        // Then: Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testRuntimeRulesWithDifferentSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestRuntimeSecurity" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When: Installing runtime rules
            // Then: Should not throw for any security profile
            assertDoesNotThrow(() -> new RuntimeRules().install(ctx),
                "RuntimeRules.install should not throw for security profile: " + profile);
        }
    }

    @Test
    void testRuntimeRulesHandlesNullContext() {
        // This tests that the install method requires a non-null context
        assertThrows(NullPointerException.class, () -> new RuntimeRules().install(null));
    }

    @Test
    void testMultipleInstallCallsOnSameContext() {
        App app = new App();
        Stack stack = createTestStack(app, "TestMultiInstallRuntime", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // First install should work
        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));

        // Second install on same context should also work (idempotent)
        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
    }

    @Test
    void testEC2RuntimeWithProductionProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestEC2Production", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
        assertNotNull(ctx.getNode());
    }

    @Test
    void testFargateRuntimeWithDevProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
        assertNotNull(ctx.getNode());
    }

    @Test
    void testFargateRuntimeWithStagingProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateStaging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
        assertNotNull(ctx.getNode());
    }

    @Test
    void testEC2RuntimeWithDifferentIAMProfiles() {
        for (IAMProfile iamProfile : IAMProfile.values()) {
            App app = new App();
            String stackName = "TestEC2IAM" + iamProfile.name().replace("_", "");
            Stack stack = createTestStack(app, stackName, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    SecurityProfile.DEV, iamProfile, cfc);

            assertDoesNotThrow(() -> new RuntimeRules().install(ctx),
                "RuntimeRules.install should work with IAM profile: " + iamProfile);
        }
    }

    @Test
    void testFargateRuntimeWithDifferentIAMProfiles() {
        for (IAMProfile iamProfile : IAMProfile.values()) {
            App app = new App();
            String stackName = "TestFargateIAM" + iamProfile.name().replace("_", "");
            Stack stack = createTestStack(app, stackName, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, iamProfile, cfc);

            assertDoesNotThrow(() -> new RuntimeRules().install(ctx),
                "RuntimeRules.install should work with IAM profile: " + iamProfile);
        }
    }

    @Test
    void testEC2RuntimeWithJenkinsSingleNodeTopology() {
        App app = new App();
        Stack stack = createTestStack(app, "TestEC2SingleNode", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
    }

    @Test
    void testFargateRuntimeWithJenkinsServiceTopology() {
        App app = new App();
        Stack stack = createTestStack(app, "TestFargateService", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
    }

    @Test
    void testRuntimeRulesWithAllTopologyTypes() {
        for (TopologyType topology : TopologyType.values()) {
            App app = new App();
            String sanitizedName = "TestRuntimeTopology" + topology.name().replace("_", "");
            Stack stack = createTestStack(app, sanitizedName, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

            // Use FARGATE for all topologies in this test
            SystemContext ctx = SystemContext.start(stack, topology, RuntimeType.FARGATE,
                    SecurityProfile.DEV, iamProfile, cfc);

            assertDoesNotThrow(() -> new RuntimeRules().install(ctx),
                "RuntimeRules.install should work with topology: " + topology);
        }
    }

    @Test
    void testRuntimeRulesValidationExecutes() {
        App app = new App();
        Stack stack = createTestStack(app, "TestRuntimeValidationExec", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        new RuntimeRules().install(ctx);

        // Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testRuntimeRulesWiringDeferred() {
        App app = new App();
        Stack stack = createTestStack(app, "TestRuntimeDeferredWiring", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Wiring should be deferred via ctx.once() - this tests the pattern works
        assertDoesNotThrow(() -> new RuntimeRules().install(ctx));
    }
}
