package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Soc2Rules.
 *
 * Tests SOC 2 Trust Services Criteria compliance validation.
 */
class Soc2RulesTest {

    private Stack createSoc2Stack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "SOC2");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testSoc2RulesInstallWithProduction() {
        // Given: A PRODUCTION deployment with SOC2 enabled
        App app = new App();
        Stack stack = createSoc2Stack(app, "TestSoc2", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing SOC2 rules
        // Then: Should not throw
        assertDoesNotThrow(() -> Soc2Rules.install(ctx));
    }

    @Test
    void testSoc2RulesWithStagingProfile() {
        // Given: A STAGING deployment
        App app = new App();
        Stack stack = createSoc2Stack(app, "TestSoc2Staging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Installing SOC2 rules
        // Then: Should not throw
        assertDoesNotThrow(() -> Soc2Rules.install(ctx));
    }

    @Test
    void testSoc2RulesCannotBeInstantiated() {
        try {
            var constructor = Soc2Rules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("Soc2Rules should have a private constructor");
        }
    }

    @Test
    void testSoc2RulesMultipleInstallations() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createSoc2Stack(app, "TestSoc2Multiple", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing SOC2 rules multiple times
        Soc2Rules.install(ctx);

        // Then: Should be idempotent
        assertDoesNotThrow(() -> Soc2Rules.install(ctx));
    }
}
