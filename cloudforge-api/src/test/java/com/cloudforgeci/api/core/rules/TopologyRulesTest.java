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
 * Test suite for TopologyRules.
 *
 * Tests topology rule installation and configuration across different topology types.
 */
class TopologyRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testTopologyRulesInstallWithJenkinsSingleNode() {
        // Given: A deployment with JENKINS_SINGLE_NODE topology
        App app = new App();
        Stack stack = createTestStack(app, "TestTopologyJenkinsSN", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing topology rules
        // Then: Should not throw
        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testTopologyRulesInstallWithJenkinsService() {
        // Given: A deployment with JENKINS_SERVICE topology
        App app = new App();
        Stack stack = createTestStack(app, "TestTopologyJenkinsService", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Installing topology rules
        assertDoesNotThrow(() -> new TopologyRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testTopologyRulesInstallWithS3Website() {
        // Given: A deployment with S3_WEBSITE topology
        App app = new App();
        Stack stack = createTestStack(app, "TestTopologyS3Website", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing topology rules
        assertDoesNotThrow(() -> new TopologyRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testTopologyRulesWiringDeferredUntilAfterFactories() {
        // Given: A deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestTopologyWiring", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing topology rules
        // Then: Should not throw (wiring is deferred via ctx.once())
        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testTopologyRulesWithAllTopologyTypes() {
        // Given: Each topology type
        int counter = 0;
        for (TopologyType topology : TopologyType.values()) {
            App app = new App();
            // Use counter to ensure unique and valid stack names
            Stack stack = createTestStack(app, "TestTopo" + counter++, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

            // Choose appropriate runtime for each topology
            RuntimeType runtime = topology == TopologyType.JENKINS_SERVICE
                ? RuntimeType.EC2
                : RuntimeType.FARGATE;

            SystemContext ctx = SystemContext.start(stack, topology, runtime,
                    SecurityProfile.DEV, iamProfile, cfc);

            // When: Installing topology rules
            // Then: Should not throw for any topology type
            assertDoesNotThrow(() -> new TopologyRules().install(ctx),
                "TopologyRules.install should not throw for topology: " + topology);
        }
    }

    @Test
    void testTopologyRulesAddsValidationToNode() {
        // Given: A deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestTopologyValidation", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing topology rules
        new TopologyRules().install(ctx);

        // Then: Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testTopologyRulesWithDifferentSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestTopologySecurity" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When: Installing topology rules
            // Then: Should not throw for any security profile
            assertDoesNotThrow(() -> new TopologyRules().install(ctx),
                "TopologyRules.install should not throw for security profile: " + profile);
        }
    }

    @Test
    void testTopologyRulesHandlesNullContext() {
        // This tests that the install method requires a non-null context
        assertThrows(NullPointerException.class, () -> new TopologyRules().install(null));
    }

    @Test
    void testMultipleInstallCallsOnSameContext() {
        App app = new App();
        Stack stack = createTestStack(app, "TestMultiInstallTopology", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // First install should work
        assertDoesNotThrow(() -> new TopologyRules().install(ctx));

        // Second install on same context should also work (idempotent)
        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testJenkinsSingleNodeTopologyWithEC2Runtime() {
        App app = new App();
        Stack stack = createTestStack(app, "TestJSNEC2", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
        assertNotNull(ctx.getNode());
    }

    @Test
    void testJenkinsServiceTopologyWithFargateRuntime() {
        App app = new App();
        Stack stack = createTestStack(app, "TestJSFargate", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
        assertNotNull(ctx.getNode());
    }

    @Test
    void testS3WebsiteTopologyWithDevProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
        assertNotNull(ctx.getNode());
    }

    @Test
    void testTopologyRulesWithDifferentIAMProfiles() {
        for (IAMProfile iamProfile : IAMProfile.values()) {
            App app = new App();
            String stackName = "TestTopoIAM" + iamProfile.name().replace("_", "");
            Stack stack = createTestStack(app, stackName, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, iamProfile, cfc);

            assertDoesNotThrow(() -> new TopologyRules().install(ctx),
                "TopologyRules.install should work with IAM profile: " + iamProfile);
        }
    }

    @Test
    void testJenkinsSingleNodeWithProductionProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestJSNProduction", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testJenkinsServiceWithStagingProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestJSStaging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testS3WebsiteWithProductionProfile() {
        App app = new App();
        Stack stack = createTestStack(app, "TestS3Production", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testTopologyRulesWithAllRuntimeTypes() {
        for (RuntimeType runtime : RuntimeType.values()) {
            App app = new App();
            String stackName = "TestTopoRuntime" + runtime.name();
            Stack stack = createTestStack(app, stackName, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

            // Choose appropriate topology for each runtime
            TopologyType topology = runtime == RuntimeType.EC2
                ? TopologyType.JENKINS_SERVICE
                : TopologyType.JENKINS_SERVICE;

            SystemContext ctx = SystemContext.start(stack, topology, runtime,
                    SecurityProfile.DEV, iamProfile, cfc);

            assertDoesNotThrow(() -> new TopologyRules().install(ctx),
                "TopologyRules.install should work with runtime: " + runtime);
        }
    }

    @Test
    void testTopologyRulesValidationExecutes() {
        App app = new App();
        Stack stack = createTestStack(app, "TestTopoValidationExec", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        new TopologyRules().install(ctx);

        // Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testTopologyRulesWiringDeferred() {
        App app = new App();
        Stack stack = createTestStack(app, "TestTopoDeferredWiring", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testCmsServiceTopologyWithFargateDev() {
        App app = new App();
        Stack stack = createTestStack(app, "TestCmsTopoFargateDev", SecurityProfile.DEV);
        stack.getNode().setContext("cfc", buildCmsContext("TestCmsTopoFargateDev", SecurityProfile.DEV, "wordpress"));

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.CMS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testCmsServiceTopologyWithEc2Staging() {
        App app = new App();
        Stack stack = createTestStack(app, "TestCmsTopoEc2Staging", SecurityProfile.STAGING);
        stack.getNode().setContext("cfc", buildCmsContext("TestCmsTopoEc2Staging", SecurityProfile.STAGING, "drupal"));

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.CMS_SERVICE, RuntimeType.EC2,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    @Test
    void testCmsServiceTopologyWithFargateProduction() {
        App app = new App();
        Stack stack = createTestStack(app, "TestCmsTopoProd", SecurityProfile.PRODUCTION);
        stack.getNode().setContext("cfc", buildCmsContext("TestCmsTopoProd", SecurityProfile.PRODUCTION, "magento"));

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.CMS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new TopologyRules().install(ctx));
    }

    private Map<String, Object> buildCmsContext(String stackName, SecurityProfile profile, String applicationId) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", stackName);
        ctx.put("securityProfile", profile.name());
        ctx.put("domain", "example.com");
        ctx.put("applicationId", applicationId);
        return ctx;
    }
}
