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
 * Test suite for PciDssRules.
 *
 * Tests PCI-DSS v3.2.1 compliance validation rules.
 */
class PciDssRulesTest {

    /**
     * Helper method to create a test stack with PCI-DSS compliance enabled.
     */
    private Stack createPciDssStack(App app, String stackName, Map<String, Object> additionalContext) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ENFORCE");

        if (additionalContext != null) {
            cfcContext.putAll(additionalContext);
        }

        stack.getNode().setContext("cfc", cfcContext);
        return stack;
    }

    @Test
    void testPciDssRulesOnlyEnforcedForProduction() {
        // Given: A DEV deployment with PCI-DSS enabled
        App app = new App();
        Stack stack = new Stack(app, "TestDevNoPci");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDevNoPci");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should not throw (rules are only enforced for PRODUCTION)
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesInstallWithProduction() {
        // Given: A PRODUCTION deployment with PCI-DSS enabled
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciProd", null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should not throw
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesWithAdvisoryMode() {
        // Given: A PRODUCTION deployment with ADVISORY mode
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("complianceMode", "ADVISORY");

        Stack stack = createPciDssStack(app, "TestPciAdvisory", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules in ADVISORY mode
        // Then: Should not throw (violations logged as warnings)
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesWithEnforceMode() {
        // Given: A PRODUCTION deployment with ENFORCE mode
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("complianceMode", "ENFORCE");

        Stack stack = createPciDssStack(app, "TestPciEnforce", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules in ENFORCE mode
        // Then: Should not throw during installation
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesCannotBeInstantiated() {
        // The PciDssRules class should not be instantiable (utility class)
        try {
            var constructor = PciDssRules.class.getDeclaredConstructor();
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("PciDssRules should have a no-args constructor");
        }
    }

    @Test
    void testPciDssRulesWithStagingProfile() {
        // Given: A STAGING deployment with PCI-DSS enabled
        App app = new App();
        Stack stack = new Stack(app, "TestStagingNoPci");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestStagingNoPci");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should not enforce (only PRODUCTION enforces PCI-DSS)
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesWithEc2Runtime() {
        // Given: A PRODUCTION EC2 deployment with PCI-DSS
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("runtime", "EC2");

        Stack stack = createPciDssStack(app, "TestPciEc2", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should not throw
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesWithFargateRuntime() {
        // Given: A PRODUCTION Fargate deployment with PCI-DSS
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciFargate", null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should not throw
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesWithMultipleComplianceFrameworks() {
        // Given: A deployment with multiple compliance frameworks
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("complianceFrameworks", "PCI-DSS,HIPAA,SOC2");

        Stack stack = createPciDssStack(app, "TestPciMulti", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should not throw (PCI-DSS rules install independently)
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesWithPrivateSubnets() {
        // Given: A deployment with private subnets (secure network)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("networkMode", "private-with-nat");  // Use valid network mode

        Stack stack = createPciDssStack(app, "TestPciPrivate", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules
        // Then: Should pass network security validation
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRulesMultipleInstallations() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciMultipleInstall", null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules multiple times
        PciDssRules.install(ctx);
        PciDssRules.install(ctx);

        // Then: Should be idempotent (no errors)
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }
}
