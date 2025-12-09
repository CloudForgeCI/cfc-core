package com.cloudforgeci.api.security;

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
 * Test suite for CertificateFactory.
 *
 * Tests certificate management factory which intentionally delegates
 * certificate creation to runtime configurations to avoid CloudFormation
 * deletion order issues.
 */
class CertificateFactoryTest {

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
    void testCertificateFactoryCreation() {
        // Given: A deployment with SSL enabled
        App app = new App();
        Stack stack = new Stack(app, "TestCertificate");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCertificate");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableSsl", true);
        cfcContext.put("domain", "example.com");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating Certificate factory
        CertificateFactory factory = new CertificateFactory(stack, "Certificate");

        // Then: Should create without errors (intentionally does nothing)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCertificateFactoryWithoutSsl() {
        // Given: A deployment without SSL
        App app = new App();
        Stack stack = createTestStack(app, "TestCertificateNoSsl", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating Certificate factory without SSL
        CertificateFactory factory = new CertificateFactory(stack, "Certificate");

        // Then: Should create without errors
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testCertificateFactoryWithAllSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestCertificate" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When: Creating Certificate factory
            CertificateFactory factory = new CertificateFactory(stack, "Certificate");

            // Then: Should not throw for any security profile
            assertDoesNotThrow(factory::create,
                "CertificateFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testCertificateFactoryDelegationPattern() {
        // Given: A deployment (certificate creation is delegated to runtime)
        App app = new App();
        Stack stack = createTestStack(app, "TestCertificateDelegation", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating Certificate factory
        CertificateFactory factory = new CertificateFactory(stack, "Certificate");
        factory.create();

        // Then: Should complete without creating certificate resources
        // (Certificate creation is delegated to runtime configurations)
        assertDoesNotThrow(() -> factory.create());
    }

    @Test
    void testCertificateFactoryWithSubdomain() {
        // Given: A deployment with subdomain
        App app = new App();
        Stack stack = new Stack(app, "TestCertificateSubdomain");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCertificateSubdomain");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("domain", "example.com");
        cfcContext.put("subdomain", "jenkins");
        cfcContext.put("enableSsl", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Creating Certificate factory with subdomain
        CertificateFactory factory = new CertificateFactory(stack, "Certificate");

        // Then: Should handle subdomain configuration
        assertDoesNotThrow(factory::create);
    }
}
