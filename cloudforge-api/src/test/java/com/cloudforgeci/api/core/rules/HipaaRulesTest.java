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
 * Test suite for HipaaRules.
 *
 * Tests HIPAA Security Rule compliance validation.
 */
class HipaaRulesTest {

    private Stack createHipaaStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testHipaaRulesInstallWithProduction() {
        // Given: A PRODUCTION deployment with HIPAA enabled
        App app = new App();
        Stack stack = createHipaaStack(app, "TestHipaa", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        // Then: Should not throw
        assertDoesNotThrow(() -> HipaaRules.install(ctx));
    }

    @Test
    void testHipaaRulesWithDevProfile() {
        // Given: A DEV deployment
        App app = new App();
        Stack stack = createHipaaStack(app, "TestHipaaDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing HIPAA rules
        // Then: Should not throw
        assertDoesNotThrow(() -> HipaaRules.install(ctx));
    }

    @Test
    void testHipaaRulesCannotBeInstantiated() {
        try {
            var constructor = HipaaRules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("HipaaRules should have a private constructor");
        }
    }

    @Test
    void testHipaaRulesMultipleInstallations() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createHipaaStack(app, "TestHipaaMultiple", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules multiple times
        HipaaRules.install(ctx);

        // Then: Should be idempotent
        assertDoesNotThrow(() -> HipaaRules.install(ctx));
    }
}
