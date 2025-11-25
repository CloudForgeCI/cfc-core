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

    @Test
    void testSecurityRulesWithPciDssCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestPciDss");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciDss");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesWithHipaaCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaa");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaa");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesWithSoc2Compliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestSoc2");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSoc2");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "SOC2");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesWithGdprCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestGdpr");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdpr");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesWithMultipleComplianceFrameworks() {
        App app = new App();
        Stack stack = new Stack(app, "TestMultiFramework");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestMultiFramework");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS,HIPAA,SOC2,GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesWithEmptyComplianceFrameworks() {
        App app = new App();
        Stack stack = new Stack(app, "TestEmptyFrameworks");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEmptyFrameworks");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should skip compliance when frameworks is empty
        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }

    @Test
    void testSecurityRulesWithFargateRuntime() {
        App app = new App();
        Stack stack = createTestStack(app, "TestFargate", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityRulesWithEc2Runtime() {
        App app = new App();
        Stack stack = createTestStack(app, "TestEc2", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
        assertTrue(ctx.securityProfileConfig.get().isPresent());
    }

    @Test
    void testSecurityProfileConfigurationCreatedForDev() {
        App app = new App();
        Stack stack = createTestStack(app, "TestDevProfileConfig", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        SecurityRules.install(ctx);

        assertTrue(ctx.securityProfileConfig.get().isPresent());
        assertNotNull(ctx.securityProfileConfig.get().get());
    }

    @Test
    void testSecurityProfileConfigurationCreatedForStaging() {
        App app = new App();
        Stack stack = createTestStack(app, "TestStagingProfileConfig", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        SecurityRules.install(ctx);

        assertTrue(ctx.securityProfileConfig.get().isPresent());
        assertNotNull(ctx.securityProfileConfig.get().get());
    }

    @Test
    void testSecurityProfileConfigurationCreatedForProduction() {
        App app = new App();
        Stack stack = createTestStack(app, "TestProductionProfileConfig", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        SecurityRules.install(ctx);

        assertTrue(ctx.securityProfileConfig.get().isPresent());
        assertNotNull(ctx.securityProfileConfig.get().get());
    }

    @Test
    void testMultipleInstallCallsOnSameContext() {
        App app = new App();
        Stack stack = createTestStack(app, "TestMultiInstall", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // First install should work
        assertDoesNotThrow(() -> SecurityRules.install(ctx));

        // Second install on same context should also work (idempotent)
        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }

    @Test
    void testSecurityRulesHandlesNullContext() {
        // This tests that the install method requires a non-null context
        assertThrows(NullPointerException.class, () -> SecurityRules.install(null));
    }

    @Test
    void testCrossFrameworkValidatorsInstalledWithCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestCrossFramework");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCrossFramework");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "SOC2");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Cross-framework validators (KeyManagement, AdvancedMonitoring, etc.) should be installed
        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }

    @Test
    void testDevProfileWithPciDssCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestDevPciDss");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDevPciDss");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }

    @Test
    void testStagingProfileWithHipaaCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestStagingHipaa");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestStagingHipaa");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }

    @Test
    void testProductionProfileWithGdprCompliance() {
        App app = new App();
        Stack stack = new Stack(app, "TestProdGdpr");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestProdGdpr");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> SecurityRules.install(ctx));
    }

    @Test
    void testSecurityRulesWithAllTopologyTypes() {
        for (TopologyType topology : TopologyType.values()) {
            App app = new App();
            // Sanitize topology name to only contain alphanumeric and hyphens (no underscores)
            String sanitizedName = "TestTopology" + topology.name().replace("_", "");
            Stack stack = createTestStack(app, sanitizedName, SecurityProfile.PRODUCTION);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            SystemContext ctx = SystemContext.start(stack, topology, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            assertDoesNotThrow(() -> SecurityRules.install(ctx),
                "SecurityRules.install should work with topology: " + topology);
        }
    }
}
