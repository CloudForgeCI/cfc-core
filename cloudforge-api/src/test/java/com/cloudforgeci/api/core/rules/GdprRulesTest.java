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
 * Test suite for GdprRules.
 *
 * Tests GDPR technical safeguards compliance validation.
 */
class GdprRulesTest {

    private Stack createGdprStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testGdprRulesInstallWithProduction() {
        // Given: A PRODUCTION deployment with GDPR enabled
        App app = new App();
        Stack stack = createGdprStack(app, "TestGdpr", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing GDPR rules
        // Then: Should not throw
        assertDoesNotThrow(() -> GdprRules.install(ctx));
    }

    @Test
    void testGdprRulesWithDevProfile() {
        // Given: A DEV deployment
        App app = new App();
        Stack stack = createGdprStack(app, "TestGdprDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing GDPR rules
        // Then: Should not throw
        assertDoesNotThrow(() -> GdprRules.install(ctx));
    }

    @Test
    void testGdprRulesCannotBeInstantiated() {
        try {
            var constructor = GdprRules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("GdprRules should have a private constructor");
        }
    }

    @Test
    void testGdprRulesMultipleInstallations() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createGdprStack(app, "TestGdprMultiple", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing GDPR rules multiple times
        GdprRules.install(ctx);

        // Then: Should be idempotent
        assertDoesNotThrow(() -> GdprRules.install(ctx));
    }
}
