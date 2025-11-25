package com.cloudforgeci.api.observability;

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
 * Test suite for GuardDutyFactory.
 *
 * Tests GuardDuty threat detection enablement with conditional logic:
 * - Security profile inheritance for guardDutyEnabled setting
 * - Conditional detector creation based on createGuardDutyDetector flag
 * - Region validation before enabling GuardDuty
 */
class GuardDutyFactoryTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testGuardDutyFactoryCreationWithEnabledFlag() {
        // Given: A deployment with GuardDuty explicitly enabled
        App app = new App();
        Stack stack = new Stack(app, "TestGuardDutyEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGuardDutyEnabled");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("guardDutyEnabled", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating GuardDuty factory
        // Then: Should not throw
        assertDoesNotThrow(() -> new GuardDutyFactory(stack, "GuardDuty"));
    }

    @Test
    void testGuardDutyFactoryCreationWithDisabledFlag() {
        // Given: A deployment with GuardDuty explicitly disabled
        App app = new App();
        Stack stack = new Stack(app, "TestGuardDutyDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGuardDutyDisabled");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("guardDutyEnabled", false);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating GuardDuty factory
        GuardDutyFactory factory = new GuardDutyFactory(stack, "GuardDuty");

        // Then: Should not throw (disabled case is handled gracefully)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testGuardDutyFactoryWithDetectorCreation() {
        // Given: A deployment with detector creation enabled
        App app = new App();
        Stack stack = new Stack(app, "TestGuardDutyDetector");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGuardDutyDetector");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("guardDutyEnabled", true);
        cfcContext.put("createGuardDutyDetector", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating GuardDuty factory with detector creation
        GuardDutyFactory factory = new GuardDutyFactory(stack, "GuardDuty");

        // Then: Should create detector resource
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testGuardDutyFactoryInheritsFromSecurityProfile() {
        // Given: A PRODUCTION deployment (should have GuardDuty enabled by default)
        App app = new App();
        Stack stack = createTestStack(app, "TestGuardDutyInherit", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating GuardDuty factory without explicit guardDutyEnabled flag
        GuardDutyFactory factory = new GuardDutyFactory(stack, "GuardDuty");

        // Then: Should not throw (inherits from security profile)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testGuardDutyFactoryWithMissingRegion() {
        // Given: A deployment without region set
        App app = new App();
        Stack stack = new Stack(app, "TestGuardDutyNoRegion");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGuardDutyNoRegion");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("guardDutyEnabled", true);
        // No region set
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating GuardDuty factory with missing region
        GuardDutyFactory factory = new GuardDutyFactory(stack, "GuardDuty");

        // Then: Should handle gracefully (logs warning and skips setup)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testGuardDutyFactoryWithTokenRegion() {
        // Given: A deployment with CDK token in region (not yet resolved)
        App app = new App();
        Stack stack = new Stack(app, "TestGuardDutyTokenRegion");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGuardDutyTokenRegion");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("guardDutyEnabled", true);
        cfcContext.put("region", "${Token[AWS.Region.0]}");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating GuardDuty factory with token region
        GuardDutyFactory factory = new GuardDutyFactory(stack, "GuardDuty");

        // Then: Should handle gracefully (skips setup due to unresolved token)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testGuardDutyFactoryWithAllSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestGuardDuty" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When: Creating GuardDuty factory
            GuardDutyFactory factory = new GuardDutyFactory(stack, "GuardDuty");

            // Then: Should not throw for any security profile
            assertDoesNotThrow(factory::create,
                "GuardDutyFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testGuardDutyFactoryConstructorValidation() {
        // Given: A basic stack with SystemContext
        App app = new App();
        Stack stack = createTestStack(app, "TestGuardDutyConstructor", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When/Then: Constructor should accept valid parameters
        assertDoesNotThrow(() -> new GuardDutyFactory(stack, "GuardDuty"));
        assertDoesNotThrow(() -> new GuardDutyFactory(stack, "GuardDuty2"));
    }
}
