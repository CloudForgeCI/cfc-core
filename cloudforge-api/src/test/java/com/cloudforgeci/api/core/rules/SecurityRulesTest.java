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
 * Test suite for SecurityRules.
 *
 * Tests security rule installation and configuration.
 */
class SecurityRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testSecurityRulesInstallWithDevProfile() {
        // Given: A DEV deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestSecurityDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing security rules
        // Then: Should not throw
        assertDoesNotThrow(() -> SecurityRules.install(ctx));

        // And: Security profile config should be set
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesInstallWithStagingProfile() {
        // Given: A STAGING deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestSecurityStaging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Installing security rules
        assertDoesNotThrow(() -> SecurityRules.install(ctx));

        // Then: Security profile config should be set
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesInstallWithProductionProfile() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestSecurityProduction", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing security rules
        assertDoesNotThrow(() -> SecurityRules.install(ctx));

        // Then: Security profile config should be set
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesCannotBeInstantiated() {
        // The SecurityRules class should not be instantiable (utility class)
        try {
            var constructor = SecurityRules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("SecurityRules should have a private constructor");
        }
    }

    @Test
    void testSecurityRulesWithoutComplianceFrameworks() {
        // Given: A deployment without compliance frameworks enabled
        App app = new App();
        Stack stack = new Stack(app, "TestNoCompliance");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestNoCompliance");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "false");  // Disable compliance
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing security rules
        // Then: Should skip compliance validation
        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }
}
