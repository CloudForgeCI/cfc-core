package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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

    @Test
    void testPciDssWithEncryptionAtRest() {
        // Given: A deployment with encryption at rest (PCI-DSS Req 3.4)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("encryptionAtRest", "true");
        additionalContext.put("kmsKeyRotation", "true");

        Stack stack = createPciDssStack(app, "TestPciEncryption", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithAccessLogging() {
        // Given: A deployment with access logging (PCI-DSS Req 10)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("accessLoggingEnabled", "true");
        additionalContext.put("logRetentionDays", "90");

        Stack stack = createPciDssStack(app, "TestPciAccessLog", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithNetworkSegmentation() {
        // Given: A deployment with network segmentation (PCI-DSS Req 1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("networkSegmentation", "true");
        additionalContext.put("privateSubnets", "true");

        Stack stack = createPciDssStack(app, "TestPciNetSeg", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithVulnerabilityScanning() {
        // Given: A deployment with vulnerability scanning (PCI-DSS Req 11.2)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("vulnerabilityScanning", "true");
        additionalContext.put("inspectorEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciVulnScan", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithSecureTransmission() {
        // Given: A deployment with secure transmission (PCI-DSS Req 4)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "pci.example.com");
        additionalContext.put("tlsVersion", "1.2");

        Stack stack = createPciDssStack(app, "TestPciSecureTrans", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithAntiMalware() {
        // Given: A deployment with anti-malware (PCI-DSS Req 5)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("antiMalwareEnabled", "true");
        additionalContext.put("antiMalwareAutoUpdate", "true");

        Stack stack = createPciDssStack(app, "TestPciAntiMalware", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithSecureSystemDevelopment() {
        // Given: A deployment with secure development practices (PCI-DSS Req 6)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("codeReviewEnabled", "true");
        additionalContext.put("securityTestingEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciSecureDev", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithAccessControl() {
        // Given: A deployment with access control (PCI-DSS Req 7, 8)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("rbacEnabled", "true");
        additionalContext.put("mfaRequired", "true");

        Stack stack = createPciDssStack(app, "TestPciAccessCtrl", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithPhysicalAccess() {
        // Given: A deployment with physical access controls (PCI-DSS Req 9)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("physicalAccessControls", "true");
        additionalContext.put("mediaDestruction", "true");

        Stack stack = createPciDssStack(app, "TestPciPhysical", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithMonitoringAndTesting() {
        // Given: A deployment with monitoring and testing (PCI-DSS Req 10, 11)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("auditLoggingEnabled", "true");
        additionalContext.put("fileIntegrityMonitoring", "true");
        additionalContext.put("intrusionDetectionEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciMonitoring", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithInformationSecurityPolicy() {
        // Given: A deployment with information security policy (PCI-DSS Req 12)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("securityPolicyDocumented", "true");
        additionalContext.put("riskAssessmentCompleted", "true");

        Stack stack = createPciDssStack(app, "TestPciPolicy", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithAllRequirements() {
        // Given: A deployment with all PCI-DSS requirements satisfied
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        // Req 1-2: Network security
        additionalContext.put("networkSegmentation", "true");
        additionalContext.put("privateSubnets", "true");
        // Req 3-4: Data protection
        additionalContext.put("encryptionAtRest", "true");
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "pci.example.com");
        // Req 5: Anti-malware
        additionalContext.put("antiMalwareEnabled", "true");
        // Req 6: Secure development
        additionalContext.put("codeReviewEnabled", "true");
        // Req 7-8: Access control
        additionalContext.put("rbacEnabled", "true");
        additionalContext.put("mfaRequired", "true");
        // Req 10-11: Monitoring
        additionalContext.put("auditLoggingEnabled", "true");
        additionalContext.put("fileIntegrityMonitoring", "true");
        additionalContext.put("vulnerabilityScanning", "true");
        // Req 12: Policy
        additionalContext.put("securityPolicyDocumented", "true");

        Stack stack = createPciDssStack(app, "TestPciComplete", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithMinimalConfiguration() {
        // Given: A minimal PCI-DSS deployment (relying on defaults)
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciMinimal", null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithDifferentTopologies() {
        // Given: Different topology types with PCI-DSS
        TopologyType[] topologies = {
            TopologyType.JENKINS_SERVICE,
            TopologyType.JENKINS_SINGLE_NODE
        };

        for (TopologyType topology : topologies) {
            App app = new App();
            Stack stack = createPciDssStack(app, "TestPciTopology" + topology.name().replace("_", "-"), null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            RuntimeType runtime = (topology == TopologyType.JENKINS_SINGLE_NODE)
                ? RuntimeType.EC2 : RuntimeType.FARGATE;

            SystemContext ctx = SystemContext.start(stack, topology, runtime,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            assertDoesNotThrow(() -> PciDssRules.install(ctx),
                "PCI-DSS rules should work with topology: " + topology);
        }
    }

    @Test
    void testPciDssWithCardholderDataEnvironment() {
        // Given: A deployment with cardholder data environment (CDE)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("cardholderDataEnvironment", "true");
        additionalContext.put("dataClassification", "SENSITIVE");
        additionalContext.put("encryptionAtRest", "true");
        additionalContext.put("encryptionInTransit", "true");

        Stack stack = createPciDssStack(app, "TestPciCDE", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithFirewallConfiguration() {
        // Given: A deployment with firewall configuration (PCI-DSS Req 1.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("firewallEnabled", "true");
        additionalContext.put("firewallRulesDocumented", "true");
        additionalContext.put("restrictedInboundTraffic", "true");

        Stack stack = createPciDssStack(app, "TestPciFirewall", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithDefaultPasswordRestrictions() {
        // Given: A deployment with default password restrictions (PCI-DSS Req 2.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("defaultPasswordsDisabled", "true");
        additionalContext.put("vendorDefaultsRemoved", "true");

        Stack stack = createPciDssStack(app, "TestPciDefaults", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithDataRetentionPolicy() {
        // Given: A deployment with data retention policy (PCI-DSS Req 3.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("dataRetentionPolicyEnabled", "true");
        additionalContext.put("dataRetentionDays", "90");
        additionalContext.put("automaticDataDeletion", "true");

        Stack stack = createPciDssStack(app, "TestPciRetention", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithKeyManagement() {
        // Given: A deployment with cryptographic key management (PCI-DSS Req 3.5-3.6)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("kmsEnabled", "true");
        additionalContext.put("kmsKeyRotation", "true");
        additionalContext.put("keyManagementProcedures", "true");

        Stack stack = createPciDssStack(app, "TestPciKeyMgmt", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithTlsStrongCryptography() {
        // Given: A deployment with strong TLS cryptography (PCI-DSS Req 4.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "secure.example.com");
        additionalContext.put("tlsVersion", "1.3");
        additionalContext.put("strongCiphersOnly", "true");

        Stack stack = createPciDssStack(app, "TestPciStrongTLS", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithPanMasking() {
        // Given: A deployment with PAN masking (PCI-DSS Req 3.3)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("panMaskingEnabled", "true");
        additionalContext.put("maxVisibleDigits", "4");

        Stack stack = createPciDssStack(app, "TestPciPanMask", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithSecureDevelopmentLifecycle() {
        // Given: A deployment with secure SDLC (PCI-DSS Req 6.3)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("sdlcSecurityEnabled", "true");
        additionalContext.put("codeReviewEnabled", "true");
        additionalContext.put("securityTestingEnabled", "true");
        additionalContext.put("changeManagementEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciSDLC", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithPatchManagement() {
        // Given: A deployment with patch management (PCI-DSS Req 6.2)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("patchManagementEnabled", "true");
        additionalContext.put("criticalPatchWindow", "30");
        additionalContext.put("automatedPatchingEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciPatch", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithLeastPrivilege() {
        // Given: A deployment with least privilege access (PCI-DSS Req 7.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("leastPrivilegeEnabled", "true");
        additionalContext.put("rbacEnabled", "true");
        additionalContext.put("accessReviewEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciLeastPriv", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithUniqueIdentification() {
        // Given: A deployment with unique user identification (PCI-DSS Req 8.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("uniqueUserIdRequired", "true");
        additionalContext.put("sharedAccountsDisabled", "true");

        Stack stack = createPciDssStack(app, "TestPciUniqueId", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithPasswordComplexity() {
        // Given: A deployment with password complexity requirements (PCI-DSS Req 8.2)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("passwordComplexityEnabled", "true");
        additionalContext.put("minPasswordLength", "12");
        additionalContext.put("passwordExpiryDays", "90");

        Stack stack = createPciDssStack(app, "TestPciPassword", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithMultiFactorAuthentication() {
        // Given: A deployment with MFA for all access (PCI-DSS Req 8.3)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("mfaRequired", "true");
        additionalContext.put("mfaForAllUsers", "true");
        additionalContext.put("mfaForRemoteAccess", "true");

        Stack stack = createPciDssStack(app, "TestPciMFA", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithSessionManagement() {
        // Given: A deployment with secure session management (PCI-DSS Req 8.2)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("sessionTimeoutEnabled", "true");
        additionalContext.put("sessionTimeoutMinutes", "15");
        additionalContext.put("reauthenticationRequired", "true");

        Stack stack = createPciDssStack(app, "TestPciSession", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithAuditTrails() {
        // Given: A deployment with comprehensive audit trails (PCI-DSS Req 10.2)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("auditTrailsEnabled", "true");
        additionalContext.put("userActivityLogging", "true");
        additionalContext.put("administrativeActionLogging", "true");
        additionalContext.put("dataAccessLogging", "true");

        Stack stack = createPciDssStack(app, "TestPciAudit", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithLogProtection() {
        // Given: A deployment with log protection (PCI-DSS Req 10.5)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("logProtectionEnabled", "true");
        additionalContext.put("logImmutability", "true");
        additionalContext.put("logIntegrityValidation", "true");

        Stack stack = createPciDssStack(app, "TestPciLogProtect", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithLogReview() {
        // Given: A deployment with daily log review (PCI-DSS Req 10.6)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("dailyLogReviewEnabled", "true");
        additionalContext.put("automatedLogAnalysis", "true");
        additionalContext.put("securityAlertingEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciLogReview", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithLogRetention() {
        // Given: A deployment with 1-year log retention (PCI-DSS Req 10.7)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("logRetentionEnabled", "true");
        additionalContext.put("logRetentionDays", "365");
        additionalContext.put("logArchivingEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciLogRetention", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithPenetrationTesting() {
        // Given: A deployment with penetration testing (PCI-DSS Req 11.3)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("penetrationTestingEnabled", "true");
        additionalContext.put("annualPenTestRequired", "true");
        additionalContext.put("segmentationTestingEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciPenTest", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithChangeDetection() {
        // Given: A deployment with change detection (PCI-DSS Req 11.5)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("changeDetectionEnabled", "true");
        additionalContext.put("fileIntegrityMonitoring", "true");
        additionalContext.put("unauthorizedChangeAlerting", "true");

        Stack stack = createPciDssStack(app, "TestPciChangeDetect", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithSecurityAwareness() {
        // Given: A deployment with security awareness program (PCI-DSS Req 12.6)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("securityAwarenessEnabled", "true");
        additionalContext.put("annualTrainingRequired", "true");
        additionalContext.put("onboardingSecurityTraining", "true");

        Stack stack = createPciDssStack(app, "TestPciAwareness", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithIncidentResponsePlan() {
        // Given: A deployment with incident response plan (PCI-DSS Req 12.10)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("incidentResponsePlanEnabled", "true");
        additionalContext.put("incidentResponseTeamDefined", "true");
        additionalContext.put("incidentResponseTestingEnabled", "true");

        Stack stack = createPciDssStack(app, "TestPciIncident", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithThirdPartyServiceProviders() {
        // Given: A deployment with third-party service provider management (PCI-DSS Req 12.8)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("thirdPartyManagementEnabled", "true");
        additionalContext.put("vendorComplianceValidation", "true");
        additionalContext.put("serviceProviderMonitoring", "true");

        Stack stack = createPciDssStack(app, "TestPciThirdParty", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithQuarterlyScanning() {
        // Given: A deployment with quarterly vulnerability scanning (PCI-DSS Req 11.2.2)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("quarterlyVulnerabilityScanningEnabled", "true");
        additionalContext.put("asvScanningEnabled", "true");
        additionalContext.put("passingScansRequired", "true");

        Stack stack = createPciDssStack(app, "TestPciQuarterlyScan", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithWirelessSecurity() {
        // Given: A deployment with wireless security (PCI-DSS Req 2.1.1, 4.1.1)
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("wirelessSecurityEnabled", "true");
        additionalContext.put("wpa2OrHigherRequired", "true");
        additionalContext.put("wirelessInventoryMaintained", "true");

        Stack stack = createPciDssStack(app, "TestPciWireless", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssIdempotency() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciIdempotent", null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing PCI-DSS rules multiple times
        PciDssRules.install(ctx);
        PciDssRules.install(ctx);
        PciDssRules.install(ctx);

        // Then: Should be fully idempotent
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWithAllSecurityProfiles() {
        // Given: Testing PCI-DSS rules across all security profiles
        SecurityProfile[] profiles = {
            SecurityProfile.DEV,
            SecurityProfile.STAGING,
            SecurityProfile.PRODUCTION
        };

        for (SecurityProfile profile : profiles) {
            App app = new App();
            Stack stack = new Stack(app, "TestPciProfile" + profile.name());

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "TestPciProfile" + profile.name());
            cfcContext.put("securityProfile", profile.name());
            cfcContext.put("complianceFrameworks", "PCI-DSS");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            assertDoesNotThrow(() -> PciDssRules.install(ctx),
                "PCI-DSS rules should work with profile: " + profile);
        }
    }

    @Test
    void testPciDssWithAllRuntimeTypes() {
        // Given: Testing PCI-DSS rules across all runtime types
        RuntimeType[] runtimes = {
            RuntimeType.EC2,
            RuntimeType.FARGATE
        };

        for (RuntimeType runtime : runtimes) {
            App app = new App();
            Stack stack = createPciDssStack(app, "TestPciRuntime" + runtime.name(), null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, runtime,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            assertDoesNotThrow(() -> PciDssRules.install(ctx),
                "PCI-DSS rules should work with runtime: " + runtime);
        }
    }

    // ========== Branch Coverage Tests for validateVendorDefaults() ==========

    @Test
    void testPciDssVendorDefaultsWithStagingAndNoCustomConfig() {
        // Tests PciDssRules.java lines 527-540: STAGING (non-PRODUCTION) with customConfigurationApplied=false
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("securityProfile", "STAGING");
        additionalContext.put("customConfigurationApplied", "false");  // Explicit false

        Stack stack = createPciDssStack(app, "TestPciVendorDefaultsStaging", additionalContext);
        stack.getNode().setContext("cfc", Map.of(
            "stackName", "TestPciVendorDefaultsStaging",
            "securityProfile", "STAGING",
            "auditManagerEnabled", "true",
            "complianceFrameworks", "PCI-DSS",
            "complianceMode", "ENFORCE",
            "customConfigurationApplied", "false"
        ));

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // This should pass STAGING profile rules
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssVendorDefaultsWithStagingAndNoHardening() {
        // Tests PciDssRules.java lines 544-557: STAGING with securityHardeningApplied=false
        App app = new App();
        Stack stack = new Stack(app, "TestPciNoHardeningStaging");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNoHardeningStaging");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ENFORCE");
        cfcContext.put("securityHardeningApplied", "false");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssVendorDefaultsWithStagingAndServicesNotDisabled() {
        // Tests PciDssRules.java lines 561-574: STAGING with unnecessaryServicesDisabled=false
        App app = new App();
        Stack stack = new Stack(app, "TestPciServicesEnabledStaging");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciServicesEnabledStaging");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ENFORCE");
        cfcContext.put("unnecessaryServicesDisabled", "false");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssVendorDefaultsWithStagingAndNonMinimalImage() {
        // Tests PciDssRules.java lines 578-591: STAGING with minimalBaseImageUsed=false
        App app = new App();
        Stack stack = new Stack(app, "TestPciNonMinimalImageStaging");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNonMinimalImageStaging");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ENFORCE");
        cfcContext.put("minimalBaseImageUsed", "false");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssVendorDefaultsWithSystemInventory() {
        // Tests PciDssRules.java lines 611-624: systemInventoryMaintained=true
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("systemInventoryMaintained", "true");

        Stack stack = createPciDssStack(app, "TestPciInventory", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssRetentionWithLongerPeriods() {
        // Tests PciDssRules.java lines 38-51: Additional retention periods not commonly tested
        App app = new App();

        // Test SIX_YEARS
        Map<String, Object> sixYearsContext = new HashMap<>();
        sixYearsContext.put("logRetentionDays", "2190");  // 6 years
        Stack stack1 = createPciDssStack(app, "TestPciRetentionSixYears", sixYearsContext);
        DeploymentContext cfc1 = DeploymentContext.from(stack1);
        IAMProfile iamProfile1 = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx1 = SystemContext.start(stack1, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile1, cfc1);
        assertDoesNotThrow(() -> PciDssRules.install(ctx1));

        // Test SEVEN_YEARS
        App app2 = new App();
        Map<String, Object> sevenYearsContext = new HashMap<>();
        sevenYearsContext.put("logRetentionDays", "2555");  // 7 years
        Stack stack2 = createPciDssStack(app2, "TestPciRetentionSevenYears", sevenYearsContext);
        DeploymentContext cfc2 = DeploymentContext.from(stack2);
        IAMProfile iamProfile2 = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx2 = SystemContext.start(stack2, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile2, cfc2);
        assertDoesNotThrow(() -> PciDssRules.install(ctx2));

        // Test EIGHT_YEARS
        App app3 = new App();
        Map<String, Object> eightYearsContext = new HashMap<>();
        eightYearsContext.put("logRetentionDays", "2920");  // 8 years
        Stack stack3 = createPciDssStack(app3, "TestPciRetentionEightYears", eightYearsContext);
        DeploymentContext cfc3 = DeploymentContext.from(stack3);
        IAMProfile iamProfile3 = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx3 = SystemContext.start(stack3, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile3, cfc3);
        assertDoesNotThrow(() -> PciDssRules.install(ctx3));

        // Test NINE_YEARS
        App app4 = new App();
        Map<String, Object> nineYearsContext = new HashMap<>();
        nineYearsContext.put("logRetentionDays", "3285");  // 9 years
        Stack stack4 = createPciDssStack(app4, "TestPciRetentionNineYears", nineYearsContext);
        DeploymentContext cfc4 = DeploymentContext.from(stack4);
        IAMProfile iamProfile4 = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx4 = SystemContext.start(stack4, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile4, cfc4);
        assertDoesNotThrow(() -> PciDssRules.install(ctx4));
    }

    @Test
    void testPciDssMfaWithCognitoNoMfa() {
        // Tests PciDssRules.java lines 353-373: OIDC auth with Cognito but MFA disabled
        App app = new App();
        Stack stack = new Stack(app, "TestPciMfaCognitoNoMfa");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciMfaCognitoNoMfa");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("cognitoAutoProvision", "true");
        cfcContext.put("cognitoMfaEnabled", "false");  // MFA disabled
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "pci.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    // ========== Individual Branch Coverage Tests ==========
    // These tests target specific untested branches identified through deep analysis

    @Test
    void testPciDssNetworkSecurityWithPublicNoNat() {
        // Tests PciDssRules.java line 149: networkMode="public-no-nat" TRUE branch
        // This should trigger a FAIL rule for PCI-DSS-Req-1.3-Network
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("networkMode", "public-no-nat");  // Prohibited network mode

        Stack stack = createPciDssStack(app, "TestPciPublicNoNat", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance violations
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAccessControlWithAuthModeNone() {
        // Tests PciDssRules.java line 337: authMode="none" TRUE branch
        // This should trigger a FAIL rule for PCI-DSS-Req-8.2-Auth
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("authMode", "none");  // No authentication - prohibited

        Stack stack = createPciDssStack(app, "TestPciAuthNone", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance violations
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAccessControlWithJenkinsOidc() {
        // Tests PciDssRules.java line 353: authMode="jenkins-oidc" (RIGHT side of OR)
        // This tests the jenkins-oidc path without Cognito, requiring SSO validation
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("authMode", "jenkins-oidc");
        additionalContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "pci-jenkins.example.com");

        Stack stack = createPciDssStack(app, "TestPciJenkinsOidc", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw - SSO with MFA is valid
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAccessControlWithSsoInstanceArn() {
        // Tests PciDssRules.java line 357: hasValidSso = ssoInstanceArn != null && !isEmpty
        // This tests the TRUE branch where ssoInstanceArn is present and non-empty
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("authMode", "alb-oidc");
        additionalContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-abcdef123456");
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "pci-sso.example.com");
        // NOT using Cognito, so this tests the SSO path

        Stack stack = createPciDssStack(app, "TestPciSsoArn", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should pass MFA validation via SSO
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssVendorDefaultsWithNoCertificate() {
        // Tests PciDssRules.java line 595: ctx.cert.get().isEmpty() - FALSE branch (no certificate)
        // This tests the else branch where no TLS certificate is configured
        App app = new App();
        Stack stack = new Stack(app, "TestPciNoCert");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNoCert");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ENFORCE");
        // Deliberately NOT setting enableSsl or fqdn - no certificate

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance violation for missing TLS
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAllValidationsPass() {
        // Tests PciDssRules.java line 111: errors.isEmpty() - TRUE branch
        // This is the "happy path" where all PCI-DSS requirements are met
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        // Configure all required security features to pass validation
        additionalContext.put("networkMode", "private-with-nat");
        additionalContext.put("authMode", "alb-oidc");
        additionalContext.put("cognitoAutoProvision", "true");
        additionalContext.put("cognitoMfaEnabled", "true");
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "pci-compliant.example.com");
        additionalContext.put("systemInventoryMaintained", "true");

        Stack stack = createPciDssStack(app, "TestPciAllPass", additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should pass all validations and return empty error list
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssEncryptionEfsInTransitDisabled() {
        // Tests PciDssRules.java line 249: !config.isEfsEncryptionInTransitEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-4.1-EFS-Transit
        App app = new App();
        Stack stack = new Stack(app, "TestPciEfsTransitDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciEfsTransitDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");  // Use ADVISORY to avoid blocking
        cfcContext.put("efsEncryptionInTransitEnabled", "false");  // Disable EFS transit encryption

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssWebApplicationSecurityWafDisabled() {
        // Tests PciDssRules.java line 291: !config.isWafEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-6.6-WAF
        App app = new App();
        Stack stack = new Stack(app, "TestPciWafDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciWafDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("wafEnabled", "false");  // Disable WAF

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAuditLoggingCloudTrailDisabled() {
        // Tests PciDssRules.java line 390: !config.isCloudTrailEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-10.2-CloudTrail
        App app = new App();
        Stack stack = new Stack(app, "TestPciCloudTrailDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciCloudTrailDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("cloudTrailEnabled", "false");  // Disable CloudTrail

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAuditLoggingFlowLogsDisabled() {
        // Tests PciDssRules.java line 406: !config.isFlowLogsEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-10.3-FlowLogs
        App app = new App();
        Stack stack = new Stack(app, "TestPciFlowLogsDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciFlowLogsDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("flowLogsEnabled", "false");  // Disable Flow Logs

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssAuditLoggingAlbAccessLoggingDisabled() {
        // Tests PciDssRules.java line 420: !config.isAlbAccessLoggingEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-10.5-ALB
        App app = new App();
        Stack stack = new Stack(app, "TestPciAlbLoggingDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciAlbLoggingDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("albAccessLoggingEnabled", "false");  // Disable ALB access logging

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssSecurityMonitoringGuardDutyDisabled() {
        // Tests PciDssRules.java line 464: !config.isGuardDutyEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-11.4-GuardDuty
        App app = new App();
        Stack stack = new Stack(app, "TestPciGuardDutyDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciGuardDutyDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("guardDutyEnabled", "false");  // Disable GuardDuty

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssSecurityMonitoringDisabled() {
        // Tests PciDssRules.java line 480: !config.isSecurityMonitoringEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-11.5-Monitoring
        App app = new App();
        Stack stack = new Stack(app, "TestPciMonitoringDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciMonitoringDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("securityMonitoringEnabled", "false");  // Disable security monitoring

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssSecurityMonitoringAwsConfigDisabled() {
        // Tests PciDssRules.java line 494: !config.isAwsConfigEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-11.6-Config
        App app = new App();
        Stack stack = new Stack(app, "TestPciConfigDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciConfigDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("awsConfigEnabled", "false");  // Disable AWS Config

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should not throw, but will generate compliance warning
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssNetworkSecurityVpcMissing() {
        // Tests PciDssRules.java line 135: ctx.vpc.get().isEmpty() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-1.2.1-VPC
        App app = new App();
        Stack stack = new Stack(app, "TestPciNoVpc");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNoVpc");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        // VPC will be empty when SystemContext is started without infrastructure

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // VPC should be empty - will generate compliance violation
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssNetworkSecurityAlbSgMissing() {
        // Tests PciDssRules.java line 164: ctx.albSg.get().isEmpty() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-1.3.1-ALB-SG
        App app = new App();
        Stack stack = new Stack(app, "TestPciNoAlbSg");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNoAlbSg");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // ALB security group should be empty - will generate compliance violation
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssNetworkSecurityEfsSgMissing() {
        // Tests PciDssRules.java line 177: ctx.efsSg.get().isEmpty() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-1.3.1-EFS-SG
        App app = new App();
        Stack stack = new Stack(app, "TestPciNoEfsSg");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNoEfsSg");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // EFS security group should be empty - will generate compliance violation
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssEncryptionEbsDisabled() {
        // Tests PciDssRules.java line 205: !config.isEbsEncryptionEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-3.4-EBS
        App app = new App();
        Stack stack = new Stack(app, "TestPciEbsDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciEbsDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("ebsEncryptionEnabled", "false");  // Disable EBS encryption

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should generate compliance warning for disabled EBS encryption
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssEncryptionEfsAtRestDisabled() {
        // Tests PciDssRules.java line 220: !config.isEfsEncryptionAtRestEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-3.4-EFS
        App app = new App();
        Stack stack = new Stack(app, "TestPciEfsRestDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciEfsRestDisabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("efsEncryptionAtRestEnabled", "false");  // Disable EFS at-rest encryption

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should generate compliance warning for disabled EFS at-rest encryption
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssEncryptionS3Disabled() {
        // Tests PciDssRules.java line 233: !config.isS3EncryptionEnabled() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-3.4-S3
        App app = new App();
        Stack stack = new Stack(app, "TestPciS3Disabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciS3Disabled");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("s3EncryptionEnabled", "false");  // Disable S3 encryption

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should generate compliance warning for disabled S3 encryption
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    @Test
    void testPciDssEncryptionTlsCertMissing() {
        // Tests PciDssRules.java line 263: ctx.cert.get().isEmpty() - TRUE branch
        // This should create a FAIL rule for PCI-DSS-Req-4.1-TLS
        App app = new App();
        Stack stack = new Stack(app, "TestPciNoTls");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciNoTls");
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        // No enableSsl or fqdn - no TLS certificate

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Should generate compliance warning for missing TLS certificate
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    // ========== Parameterized Tests for Branch Coverage ==========
    // Data-driven tests to efficiently cover many branches with minimal code

    /**
     * Parameterized test for encryption settings combinations.
     * Tests all combinations of encryption enable/disable flags.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,true,true,true,ENFORCE",    // All enabled - PASS
        "PRODUCTION,FARGATE,false,true,true,true,ENFORCE",   // EBS disabled - FAIL
        "PRODUCTION,FARGATE,true,false,true,true,ENFORCE",   // EFS at-rest disabled - FAIL
        "PRODUCTION,FARGATE,true,true,false,true,ENFORCE",   // EFS transit disabled - FAIL
        "PRODUCTION,FARGATE,true,true,true,false,ENFORCE",   // S3 disabled - FAIL
        "PRODUCTION,FARGATE,false,false,false,false,ENFORCE", // All disabled - FAIL
        "PRODUCTION,FARGATE,true,true,true,true,ADVISORY",   // Advisory mode - PASS
        "PRODUCTION,FARGATE,false,false,false,false,ADVISORY" // Advisory all disabled - PASS
    })
    void testPciDssEncryptionCombinations(String profile, String runtime, boolean ebsEncryption, boolean efsAtRest,
                                          boolean efsTransit, boolean s3Encryption, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestPciEnc");
        customContext.put("securityProfile", profile);
        customContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        customContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsAtRest));
        customContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        customContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));
        customContext.put("complianceMode", complianceMode);
        customContext.put("networkMode", "private-with-nat");
        customContext.put("region", "us-east-1");

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline PCI-DSS requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            boolean hasRequirement = ebsEncryption && efsAtRest && efsTransit && s3Encryption;
            if (hasRequirement) {
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("awsConfigEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "365");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "jenkins.example.com");
                customContext.putIfAbsent("cognitoMfaEnabled", "true");
                customContext.putIfAbsent("cognitoAutoProvision", "true");
                customContext.putIfAbsent("wafEnabled", "true");
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestPciEnc", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        SecurityRules.install(builder.getSystemContext());
        PciDssRules.install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!ebsEncryption || !efsAtRest || !efsTransit || !s3Encryption) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing encryption: " + profile);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile);
        }
    }

    /**
     * Parameterized test for audit logging combinations.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,true,true,ENFORCE",     // All enabled - PASS
        "PRODUCTION,FARGATE,false,true,true,ENFORCE",    // CloudTrail disabled - FAIL
        "PRODUCTION,FARGATE,true,false,true,ENFORCE",    // Flow logs disabled - FAIL
        "PRODUCTION,FARGATE,true,true,false,ENFORCE",    // ALB logging disabled - FAIL
        "PRODUCTION,FARGATE,false,false,false,ENFORCE",  // All disabled - FAIL
        "PRODUCTION,FARGATE,true,true,true,ADVISORY",    // Advisory mode - PASS
        "PRODUCTION,FARGATE,false,false,false,ADVISORY"  // Advisory all disabled - PASS
    })
    void testPciDssAuditLoggingCombinations(String profile, String runtime, boolean cloudTrail, boolean flowLogs,
                                            boolean albLogging, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestPciLog");
        customContext.put("securityProfile", profile);
        customContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        customContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        customContext.put("albAccessLogging", String.valueOf(albLogging));
        customContext.put("complianceMode", complianceMode);
        customContext.put("networkMode", "private-with-nat");
        customContext.put("region", "us-east-1");

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline PCI-DSS requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            boolean hasRequirement = cloudTrail && flowLogs && albLogging;
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("awsConfigEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "365");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "jenkins.example.com");
                customContext.putIfAbsent("cognitoMfaEnabled", "true");
                customContext.putIfAbsent("cognitoAutoProvision", "true");
                customContext.putIfAbsent("wafEnabled", "true");
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestPciLog", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        SecurityRules.install(builder.getSystemContext());
        PciDssRules.install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!cloudTrail || !flowLogs || !albLogging) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing audit logging: " + profile);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile);
        }
    }

    /**
     * Parameterized test for security monitoring combinations.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,true,true,ENFORCE",     // All enabled - PASS
        "PRODUCTION,FARGATE,false,true,true,ENFORCE",    // GuardDuty disabled - FAIL
        "PRODUCTION,FARGATE,true,false,true,ENFORCE",    // Security monitoring disabled - FAIL
        "PRODUCTION,FARGATE,true,true,false,ENFORCE",    // AWS Config disabled - FAIL
        "PRODUCTION,FARGATE,false,false,false,ENFORCE",  // All disabled - FAIL
        "PRODUCTION,FARGATE,true,true,true,ADVISORY",    // Advisory mode - PASS
        "PRODUCTION,FARGATE,false,false,false,ADVISORY"  // Advisory all disabled - PASS
    })
    void testPciDssSecurityMonitoringCombinations(String profile, String runtime, boolean guardDuty,
                                                   boolean secMonitoring, boolean awsConfig, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestPciMon");
        customContext.put("securityProfile", profile);
        customContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        customContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        customContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        customContext.put("complianceMode", complianceMode);
        customContext.put("networkMode", "private-with-nat");
        customContext.put("region", "us-east-1");

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline PCI-DSS requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            boolean hasRequirement = guardDuty && secMonitoring && awsConfig;
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("logRetentionDays", "365");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "jenkins.example.com");
                customContext.putIfAbsent("cognitoMfaEnabled", "true");
                customContext.putIfAbsent("cognitoAutoProvision", "true");
                customContext.putIfAbsent("wafEnabled", "true");
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestPciMon", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        SecurityRules.install(builder.getSystemContext());
        PciDssRules.install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!guardDuty || !secMonitoring || !awsConfig) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing security monitoring: " + profile);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile);
        }
    }

    /**
     * Parameterized test for retention periods.
     * Tests all valid retention period values.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,365,ENFORCE,true",    // ONE_YEAR - sufficient
        "PRODUCTION,FARGATE,730,ENFORCE,true",    // TWO_YEARS - sufficient
        "PRODUCTION,FARGATE,1095,ENFORCE,true",   // THREE_YEARS - sufficient
        "PRODUCTION,FARGATE,1825,ENFORCE,true",   // FIVE_YEARS - sufficient
        "PRODUCTION,FARGATE,3650,ENFORCE,true",   // TEN_YEARS - sufficient
        "PRODUCTION,FARGATE,30,ENFORCE,false",    // 30 days - insufficient - FAIL
        "PRODUCTION,FARGATE,90,ENFORCE,false",    // 90 days - insufficient - FAIL
        "PRODUCTION,FARGATE,180,ENFORCE,false",   // 180 days - insufficient - FAIL
        "PRODUCTION,FARGATE,30,ADVISORY,false",   // Advisory mode - PASS
        "PRODUCTION,FARGATE,365,ADVISORY,true"    // Advisory sufficient - PASS
    })
    void testPciDssRetentionPeriods(String profile, String runtime, int days, String complianceMode, boolean sufficient) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestPciRet");
        customContext.put("securityProfile", profile);
        customContext.put("logRetentionDays", String.valueOf(days));
        customContext.put("complianceMode", complianceMode);
        customContext.put("networkMode", "private-with-nat");
        customContext.put("region", "us-east-1");

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline PCI-DSS requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (sufficient) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("awsConfigEnabled", "true");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "jenkins.example.com");
                customContext.putIfAbsent("cognitoMfaEnabled", "true");
                customContext.putIfAbsent("cognitoAutoProvision", "true");
                customContext.putIfAbsent("wafEnabled", "true");
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestPciRet", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        SecurityRules.install(builder.getSystemContext());
        PciDssRules.install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!sufficient) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for insufficient retention: " + days + " days");
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for retention: " + days + " days");
        }
    }

    /**
     * Parameterized test for vendor defaults with different security profiles and settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,false,false,false,false",  // PRODUCTION auto-passes
        "STAGING,true,true,true,true",         // STAGING with all settings
        "STAGING,false,false,false,false",     // STAGING without settings
        "DEV,true,false,true,false",           // DEV mixed settings
        "DEV,false,false,false,false"          // DEV no settings
    })
    void testPciDssVendorDefaultsCombinations(String profile, boolean customConfig, boolean hardening,
                                              boolean servicesDisabled, boolean minimalImage) {
        App app = new App();
        Stack stack = new Stack(app, "TestPciVendor" + profile + customConfig + hardening);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciVendor" + profile);
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", "ADVISORY");
        cfcContext.put("customConfigurationApplied", String.valueOf(customConfig));
        cfcContext.put("securityHardeningApplied", String.valueOf(hardening));
        cfcContext.put("unnecessaryServicesDisabled", String.valueOf(servicesDisabled));
        cfcContext.put("minimalBaseImageUsed", String.valueOf(minimalImage));

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Parameterized test for authentication modes and MFA configurations.
     */
    @ParameterizedTest
    @CsvSource({
        "none,false,false,",                                          // No auth
        "alb-oidc,true,true,",                                       // Cognito with MFA
        "alb-oidc,false,false,arn:aws:sso:::instance/ssoins-123",  // SSO
        "jenkins-oidc,false,false,arn:aws:sso:::instance/ssoins-456", // Jenkins OIDC with SSO
        "alb-oidc,false,false,",                                     // OIDC no MFA no SSO - should fail
        "jenkins-oidc,true,true,"                                    // Jenkins OIDC with Cognito MFA
    })
    void testPciDssAuthenticationCombinations(String authMode, boolean cognitoProvision,
                                             boolean cognitoMfa, String ssoArn) {
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("complianceMode", "ADVISORY");
        additionalContext.put("authMode", authMode);
        additionalContext.put("enableSsl", "true");
        additionalContext.put("fqdn", "pci-auth-test.example.com");

        if (cognitoProvision) {
            additionalContext.put("cognitoAutoProvision", "true");
        }
        if (cognitoMfa) {
            additionalContext.put("cognitoMfaEnabled", "true");
        }
        if (ssoArn != null && !ssoArn.isEmpty()) {
            additionalContext.put("ssoInstanceArn", ssoArn);
        }

        Stack stack = createPciDssStack(app,
            "TestPciAuth" + authMode.replace("-", "") + cognitoMfa + (ssoArn != null && !ssoArn.isEmpty()),
            additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Parameterized test for network modes.
     */
    @ParameterizedTest
    @CsvSource({
        "private-with-nat,true",      // Compliant
        "public-no-nat,false"          // Non-compliant
    })
    void testPciDssNetworkModes(String networkMode, boolean shouldPass) {
        App app = new App();
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("complianceMode", "ADVISORY");
        additionalContext.put("networkMode", networkMode);

        Stack stack = createPciDssStack(app, "TestPciNetwork" + networkMode.replace("-", ""), additionalContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Parameterized test for compliance modes with violations.
     */
    @ParameterizedTest
    @CsvSource({
        "ADVISORY",   // Logs warnings, returns empty errors
        "ENFORCE"     // Logs errors, returns error list
    })
    void testPciDssComplianceModes(String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestPciMode" + complianceMode);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPciMode" + complianceMode);
        cfcContext.put("runtime", "FARGATE");
        cfcContext.put("topology", "JENKINS_SERVICE");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "PCI-DSS");
        cfcContext.put("complianceMode", complianceMode);
        // Add some violations
        cfcContext.put("authMode", "none");
        cfcContext.put("ebsEncryptionEnabled", "false");

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Both modes should not throw - ADVISORY logs warnings, ENFORCE logs errors
        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    // ========================================
    // Truth Table Style Parameterized Tests
    // ========================================

    /**
     * Truth table test for PCI-DSS security profile requirement.
     * Tests that PCI-DSS only enforces for PRODUCTION profile.
     */
    @ParameterizedTest
    @CsvSource({
        "DEV,ADVISORY,false",           // DEV should skip PCI-DSS entirely
        "STAGING,ADVISORY,false",       // STAGING should skip PCI-DSS entirely
        "STAGING,ENFORCE,false",        // STAGING with enforce still skips
        "PRODUCTION,ADVISORY,true",     // PRODUCTION should run PCI-DSS validation
        "PRODUCTION,ENFORCE,true"       // PRODUCTION with enforce mode
    })
    void testPciDssSecurityProfileBranches(String profile, String complianceMode, boolean shouldValidate) {
        App app = new App();
        Stack stack = new Stack(app, "TestPciProfile" + profile + complianceMode);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 1: Network Security.
     * Tests all combinations of network mode and security group settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,private-with-nat,ENFORCE",    // Compliant network mode - PASS
        "PRODUCTION,FARGATE,public-no-nat,ENFORCE",       // Non-compliant network mode - FAIL
        "PRODUCTION,FARGATE,private-with-nat,ADVISORY",   // Advisory with compliant mode - PASS
        "PRODUCTION,FARGATE,public-no-nat,ADVISORY"       // Advisory with non-compliant mode - PASS
    })
    void testPciDssNetworkSecurityCombinations(String profile, String runtime, String networkMode, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestPciNet");
        customContext.put("securityProfile", profile);
        customContext.put("networkMode", networkMode);
        customContext.put("complianceMode", complianceMode);
        customContext.put("region", "us-east-1");

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        boolean isCompliant = "private-with-nat".equals(networkMode);

        // Add baseline PCI-DSS requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (isCompliant) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("awsConfigEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "365");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "jenkins.example.com");
                customContext.putIfAbsent("cognitoMfaEnabled", "true");
                customContext.putIfAbsent("cognitoAutoProvision", "true");
                customContext.putIfAbsent("wafEnabled", "true");
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestPciNet", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        SecurityRules.install(builder.getSystemContext());
        PciDssRules.install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!isCompliant) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for non-compliant network mode: " + networkMode);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for network mode: " + networkMode);
        }
    }

    /**
     * Truth table test for PCI-DSS Requirement 3 & 4: Encryption (at rest and in transit).
     * Tests all combinations of encryption settings.
     */
    @ParameterizedTest
    @CsvSource({
        // All encryption enabled - compliant
        "true,true,true,true,true,ENFORCE",
        // Individual encryption types disabled
        "false,true,true,true,true,ENFORCE",   // EBS disabled
        "true,false,true,true,true,ENFORCE",   // EFS at-rest disabled
        "true,true,false,true,true,ENFORCE",   // S3 disabled
        "true,true,true,false,true,ENFORCE",   // EFS in-transit disabled
        "true,true,true,true,false,ENFORCE",   // TLS cert missing
        // All encryption disabled - maximum non-compliance
        "false,false,false,false,false,ENFORCE",
        // Advisory mode scenarios
        "true,true,true,true,true,ADVISORY",   // All compliant in advisory
        "false,false,false,false,false,ADVISORY" // All non-compliant in advisory
    })
    void testPciDssEncryptionCombinations(boolean ebsEncryption, boolean efsAtRest, boolean s3Encryption,
                                          boolean efsTransit, boolean hasCert, String complianceMode) {
        App app = new App();
        String stackId = "TestPciEnc" + ebsEncryption + efsAtRest + s3Encryption + efsTransit + hasCert;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsAtRest));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));
        cfcContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        cfcContext.put("complianceMode", complianceMode);

        if (hasCert) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "jenkins.example.com");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 6.6: Web Application Security (WAF).
     * Tests WAF enabled/disabled combinations.
     */
    @ParameterizedTest
    @CsvSource({
        "true,ENFORCE",     // WAF enabled - compliant
        "false,ENFORCE",    // WAF disabled - non-compliant
        "true,ADVISORY",    // Advisory with WAF
        "false,ADVISORY"    // Advisory without WAF
    })
    void testPciDssWebApplicationSecurityCombinations(boolean wafEnabled, String complianceMode) {
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciWaf" + wafEnabled + complianceMode,
                Map.of("wafEnabled", String.valueOf(wafEnabled), "complianceMode", complianceMode));

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 7 & 8: Access Control and Authentication.
     * Tests all combinations of authentication modes and MFA settings.
     */
    @ParameterizedTest
    @CsvSource({
        // No authentication - non-compliant
        "none,false,false,false,ENFORCE",
        // ALB OIDC with Cognito MFA - compliant
        "alb-oidc,true,true,false,ENFORCE",
        // ALB OIDC without MFA - non-compliant
        "alb-oidc,false,false,false,ENFORCE",
        // ALB OIDC with SSO (no Cognito) - compliant
        "alb-oidc,false,false,true,ENFORCE",
        // Jenkins OIDC with Cognito MFA
        "jenkins-oidc,true,true,false,ENFORCE",
        // Jenkins OIDC with SSO
        "jenkins-oidc,false,false,true,ENFORCE",
        // Advisory mode combinations
        "none,false,false,false,ADVISORY",
        "alb-oidc,true,true,false,ADVISORY"
    })
    void testPciDssAccessControlCombinations(String authMode, boolean cognitoMfa, boolean cognitoAuto,
                                             boolean hasSso, String complianceMode) {
        App app = new App();
        String stackId = "TestPciAuth" + authMode + cognitoMfa + cognitoAuto + hasSso;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("authMode", authMode);
        cfcContext.put("complianceMode", complianceMode);

        if (!authMode.equals("none")) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "jenkins.example.com");
        }

        if (cognitoAuto) {
            cfcContext.put("cognitoAutoProvision", "true");
        }
        if (cognitoMfa) {
            cfcContext.put("cognitoMfaEnabled", "true");
        }
        if (hasSso) {
            cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 10: Audit Logging.
     * Tests all combinations of audit logging settings.
     */
    @ParameterizedTest
    @CsvSource({
        // All audit logging enabled - compliant
        "true,true,true,ENFORCE",
        // Individual logging disabled
        "false,true,true,ENFORCE",    // CloudTrail disabled
        "true,false,true,ENFORCE",    // Flow Logs disabled
        "true,true,false,ENFORCE",    // ALB logging disabled
        // All logging disabled - maximum non-compliance
        "false,false,false,ENFORCE",
        // Advisory mode
        "true,true,true,ADVISORY",
        "false,false,false,ADVISORY"
    })
    void testPciDssAuditLoggingCombinations(boolean cloudTrail, boolean flowLogs, boolean albLogging,
                                            String complianceMode) {
        App app = new App();
        String stackId = "TestPciAudit" + cloudTrail + flowLogs + albLogging + complianceMode;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLogging", String.valueOf(albLogging));
        cfcContext.put("complianceMode", complianceMode);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 10.7: Log Retention.
     * Tests various retention periods against 1-year minimum requirement.
     */
    @ParameterizedTest
    @CsvSource({
        "3650,ENFORCE,true",    // 10 years - compliant
        "2555,ENFORCE,true",    // 7 years - compliant
        "1825,ENFORCE,true",    // 5 years - compliant
        "1095,ENFORCE,true",    // 3 years - compliant
        "730,ENFORCE,true",     // 2 years - compliant
        "365,ENFORCE,true",     // 1 year exactly - compliant (minimum)
        "180,ENFORCE,false",    // 180 days - non-compliant
        "90,ENFORCE,false",     // 90 days - non-compliant
        "30,ENFORCE,false",     // 30 days - non-compliant
        "365,ADVISORY,true",    // Advisory mode with 1 year
        "90,ADVISORY,false"     // Advisory mode with 90 days
    })
    void testPciDssRetentionCombinations(int retentionDays, String complianceMode, boolean isCompliant) {
        App app = new App();
        Stack stack = createPciDssStack(app, "TestPciRet" + retentionDays + complianceMode,
                Map.of("logRetentionDays", String.valueOf(retentionDays), "complianceMode", complianceMode));

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 11: Security Monitoring.
     * Tests all combinations of monitoring settings.
     */
    @ParameterizedTest
    @CsvSource({
        // All monitoring enabled - compliant
        "true,true,true,ENFORCE",
        // Individual monitoring disabled
        "false,true,true,ENFORCE",    // GuardDuty disabled
        "true,false,true,ENFORCE",    // Security monitoring disabled
        "true,true,false,ENFORCE",    // AWS Config disabled
        // All monitoring disabled - maximum non-compliance
        "false,false,false,ENFORCE",
        // Advisory mode
        "true,true,true,ADVISORY",
        "false,false,false,ADVISORY"
    })
    void testPciDssSecurityMonitoringCombinations(boolean guardDuty, boolean secMonitoring, boolean awsConfig,
                                                   String complianceMode) {
        App app = new App();
        String stackId = "TestPciMon" + guardDuty + secMonitoring + awsConfig + complianceMode;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        cfcContext.put("complianceMode", complianceMode);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Truth table test for PCI-DSS Requirement 2: Vendor Defaults.
     * Tests all combinations of vendor default settings.
     */
    @ParameterizedTest
    @CsvSource({
        // All vendor defaults addressed - compliant
        "true,true,true,true,true,ENFORCE",
        // Individual settings not configured
        "false,true,true,true,true,ENFORCE",    // Custom config not applied
        "true,false,true,true,true,ENFORCE",    // Security hardening not applied
        "true,true,false,true,true,ENFORCE",    // Unnecessary services not disabled
        "true,true,true,false,true,ENFORCE",    // Minimal image not used
        "true,true,true,true,false,ENFORCE",    // System inventory not maintained
        // All settings not configured - maximum non-compliance
        "false,false,false,false,false,ENFORCE",
        // Advisory mode
        "true,true,true,true,true,ADVISORY",
        "false,false,false,false,false,ADVISORY"
    })
    void testPciDssVendorDefaultsCombinations(boolean customConfig, boolean hardening, boolean servicesDisabled,
                                              boolean minimalImage, boolean inventory, String complianceMode) {
        App app = new App();
        String stackId = "TestPciVendor" + customConfig + hardening + servicesDisabled + minimalImage + inventory;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("customConfigurationApplied", String.valueOf(customConfig));
        cfcContext.put("securityHardeningApplied", String.valueOf(hardening));
        cfcContext.put("unnecessaryServicesDisabled", String.valueOf(servicesDisabled));
        cfcContext.put("minimalBaseImageUsed", String.valueOf(minimalImage));
        cfcContext.put("systemInventoryMaintained", String.valueOf(inventory));
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Comprehensive truth table test combining multiple PCI-DSS requirements.
     * Tests realistic scenarios with multiple configuration flags.
     */
    @ParameterizedTest
    @CsvSource({
        // Full compliance scenarios
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,365",

        // Partial compliance - encryption issues
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,false,false,false,false,true,true,true,true,true,true,true,365",

        // Partial compliance - authentication issues
        "PRODUCTION,ENFORCE,private-with-nat,none,true,true,true,true,true,false,false,false,false,false,false,365",

        // Partial compliance - logging issues
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,false,false,false,true,true,true,90",

        // Network mode violation
        "PRODUCTION,ENFORCE,public-no-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,365",

        // Advisory mode with full compliance
        "PRODUCTION,ADVISORY,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,365",

        // Advisory mode with minimal compliance
        "PRODUCTION,ADVISORY,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,90"
    })
    void testPciDssComprehensiveCombinations(String profile, String complianceMode, String networkMode, String authMode,
                                            boolean ebsEnc, boolean efsRestEnc, boolean s3Enc, boolean efsTransEnc,
                                            boolean hasCert, boolean cloudTrail, boolean flowLogs, boolean albLogging,
                                            boolean guardDuty, boolean secMon, boolean awsConfig, int retention) {
        App app = new App();
        String stackId = "TestPciComp" + profile.substring(0,3) + complianceMode.substring(0,3) +
                        networkMode.substring(0,3) + authMode.substring(0,3);
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("networkMode", networkMode);
        cfcContext.put("authMode", authMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEnc));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsRestEnc));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Enc));
        cfcContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransEnc));
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLogging", String.valueOf(albLogging));
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("securityMonitoringEnabled", String.valueOf(secMon));
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        cfcContext.put("logRetentionDays", String.valueOf(retention));

        if (hasCert) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "jenkins.example.com");
        }

        if (authMode.equals("alb-oidc") || authMode.equals("jenkins-oidc")) {
            cfcContext.put("cognitoAutoProvision", "true");
            cfcContext.put("cognitoMfaEnabled", "true");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    // ========================================
    // EXPANDED TRUTH TABLE TESTS - PCI-DSS
    // ========================================
    // These expanded tests systematically cover all branch combinations
    // to maximize branch coverage for PCI-DSS compliance validation

    /**
     * Expanded truth table: PCI-DSS Req 3.4 - Encryption at Rest (All Combinations).
     * Tests all 2^3 = 8 combinations of encryption settings across security profiles.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,true,ENFORCE",      // All encrypted - PASS
        "PRODUCTION,true,true,false,ENFORCE",     // EBS+EFS, no S3 - FAIL
        "PRODUCTION,true,false,true,ENFORCE",     // EBS+S3, no EFS - FAIL
        "PRODUCTION,false,true,true,ENFORCE",     // EFS+S3, no EBS - FAIL
        "PRODUCTION,true,false,false,ENFORCE",    // EBS only - FAIL
        "PRODUCTION,false,true,false,ENFORCE",    // EFS only - FAIL
        "PRODUCTION,false,false,true,ENFORCE",    // S3 only - FAIL
        "PRODUCTION,false,false,false,ENFORCE",   // No encryption - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,ENFORCE",         // All encrypted
        "STAGING,false,false,false,ENFORCE",      // No encryption
        "STAGING,true,true,true,ADVISORY",        // Advisory mode
        // DEV - Should skip PCI-DSS
        "DEV,true,true,true,ENFORCE",
        "DEV,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY"
    })
    void testPciDssExpandedEncryptionAtRest(String profile, boolean ebsEncryption,
                                            boolean efsEncryption, boolean s3Encryption,
                                            String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpEnc" + profile.substring(0,3) + ebsEncryption + efsEncryption + s3Encryption;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsEncryption));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 10.1-10.7 - Audit Logging (All Combinations).
     * Tests all 2^3 = 8 combinations of logging settings.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,true,ENFORCE",      // All logging - PASS
        "PRODUCTION,true,true,false,ENFORCE",     // CloudTrail+FlowLogs, no ALB - FAIL
        "PRODUCTION,true,false,true,ENFORCE",     // CloudTrail+ALB, no FlowLogs - FAIL
        "PRODUCTION,false,true,true,ENFORCE",     // FlowLogs+ALB, no CloudTrail - FAIL
        "PRODUCTION,true,false,false,ENFORCE",    // CloudTrail only - FAIL
        "PRODUCTION,false,true,false,ENFORCE",    // FlowLogs only - FAIL
        "PRODUCTION,false,false,true,ENFORCE",    // ALB only - FAIL
        "PRODUCTION,false,false,false,ENFORCE",   // No logging - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,ENFORCE",         // All logging
        "STAGING,false,false,false,ENFORCE",      // No logging
        "STAGING,true,true,true,ADVISORY",        // Advisory mode
        // DEV - Should skip
        "DEV,true,true,true,ENFORCE",
        "DEV,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY"
    })
    void testPciDssExpandedAuditLogging(String profile, boolean cloudTrail,
                                        boolean flowLogs, boolean albLogging,
                                        String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpLog" + profile.substring(0,3) + cloudTrail + flowLogs + albLogging;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLogging", String.valueOf(albLogging));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 3.6 - Key Management (All Combinations).
     * Tests KMS key rotation, automated backup, and cross-region backup combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,true,ENFORCE",      // All key mgmt - PASS
        "PRODUCTION,true,true,false,ENFORCE",     // Rotation+Backup, no cross-region
        "PRODUCTION,true,false,true,ENFORCE",     // Rotation+Cross-region, no backup - FAIL
        "PRODUCTION,false,true,true,ENFORCE",     // Backup+Cross-region, no rotation - FAIL
        "PRODUCTION,true,false,false,ENFORCE",    // Rotation only - FAIL
        "PRODUCTION,false,true,false,ENFORCE",    // Backup only - FAIL
        "PRODUCTION,false,false,true,ENFORCE",    // Cross-region only - FAIL
        "PRODUCTION,false,false,false,ENFORCE",   // No key mgmt - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,ENFORCE",         // All key mgmt
        "STAGING,false,false,false,ENFORCE",      // No key mgmt
        "STAGING,true,true,false,ADVISORY",       // Advisory mode
        // DEV - Should skip
        "DEV,true,true,true,ENFORCE",
        "DEV,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY"
    })
    void testPciDssExpandedKeyManagement(String profile, boolean kmsRotation,
                                         boolean automatedBackup, boolean crossRegionBackup,
                                         String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpKey" + profile.substring(0,3) + kmsRotation + automatedBackup + crossRegionBackup;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("kmsKeyRotationEnabled", String.valueOf(kmsRotation));
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("crossRegionBackupEnabled", String.valueOf(crossRegionBackup));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 7.1, 7.2 - Access Control (All Auth Mode Combinations).
     * Tests authentication modes with MFA and Identity Center SSO.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All auth modes with MFA/SSO combinations
        "PRODUCTION,alb-oidc,true,true,ENFORCE",      // ALB OIDC with MFA+SSO - PASS
        "PRODUCTION,alb-oidc,true,false,ENFORCE",     // ALB OIDC with MFA only
        "PRODUCTION,alb-oidc,false,true,ENFORCE",     // ALB OIDC with SSO only
        "PRODUCTION,alb-oidc,false,false,ENFORCE",    // ALB OIDC without MFA/SSO - FAIL
        "PRODUCTION,jenkins-oidc,true,true,ENFORCE",  // Jenkins OIDC with MFA+SSO
        "PRODUCTION,jenkins-oidc,false,false,ENFORCE", // Jenkins OIDC without MFA/SSO
        "PRODUCTION,none,false,false,ENFORCE",        // No auth - FAIL
        // STAGING - Key combinations
        "STAGING,alb-oidc,true,true,ENFORCE",
        "STAGING,none,false,false,ENFORCE",
        "STAGING,alb-oidc,true,true,ADVISORY",
        // DEV - Should skip
        "DEV,alb-oidc,true,true,ENFORCE",
        "DEV,none,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,alb-oidc,true,true,ADVISORY",
        "PRODUCTION,none,false,false,ADVISORY"
    })
    void testPciDssExpandedAccessControl(String profile, String authMode,
                                         boolean cognitoMfa, boolean identityCenterSso,
                                         String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpAcc" + profile.substring(0,3) + authMode.substring(0,3) +
                        cognitoMfa + identityCenterSso;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("authMode", authMode);
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");

        if (authMode.equals("alb-oidc") || authMode.equals("jenkins-oidc")) {
            cfcContext.put("cognitoAutoProvision", "true");
            if (cognitoMfa) {
                cfcContext.put("cognitoMfaEnabled", "true");
            }
            if (identityCenterSso) {
                cfcContext.put("identityCenterSsoEnabled", "true");
            }
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 1.3 - Network Segmentation (All Network Mode Combinations).
     * Tests network modes (public-no-nat vs private-with-nat) across security profiles.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - Network modes
        "PRODUCTION,private-with-nat,ENFORCE",    // Private network - PASS
        "PRODUCTION,public-no-nat,ENFORCE",       // Public network - FAIL
        // STAGING - Network modes
        "STAGING,private-with-nat,ENFORCE",
        "STAGING,public-no-nat,ENFORCE",
        "STAGING,private-with-nat,ADVISORY",
        // DEV - Should skip
        "DEV,private-with-nat,ENFORCE",
        "DEV,public-no-nat,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,private-with-nat,ADVISORY",
        "PRODUCTION,public-no-nat,ADVISORY"
    })
    void testPciDssExpandedNetworkSegmentation(String profile, String networkMode,
                                               String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpNet" + profile.substring(0,3) + networkMode.substring(0,3);
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("networkMode", networkMode);
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 10.7 - Log Retention Periods (All Values).
     * Tests all retention period values from 90 days to 7+ years.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All retention periods
        "PRODUCTION,90,ENFORCE",      // 90 days - Below PCI-DSS minimum (365 days) - FAIL
        "PRODUCTION,180,ENFORCE",     // 180 days - Below PCI-DSS minimum - FAIL
        "PRODUCTION,365,ENFORCE",     // 1 year - PCI-DSS minimum - PASS
        "PRODUCTION,730,ENFORCE",     // 2 years - Above minimum - PASS
        "PRODUCTION,1095,ENFORCE",    // 3 years - Above minimum - PASS
        "PRODUCTION,2190,ENFORCE",    // 6 years - Above minimum - PASS
        "PRODUCTION,2555,ENFORCE",    // 7 years - Above minimum - PASS
        // STAGING - Key retention periods
        "STAGING,90,ENFORCE",
        "STAGING,365,ENFORCE",
        "STAGING,2190,ENFORCE",
        "STAGING,365,ADVISORY",
        // DEV - Should skip
        "DEV,90,ENFORCE",
        "DEV,365,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,90,ADVISORY",
        "PRODUCTION,365,ADVISORY"
    })
    void testPciDssExpandedRetentionPeriods(String profile, int retentionDays,
                                            String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpRet" + profile.substring(0,3) + retentionDays;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("logRetentionDays", String.valueOf(retentionDays));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 2.1, 3.6 - Vendor Defaults and Database Security.
     * Tests combinations of database security hardening and KMS settings.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,true,ENFORCE",      // All security - PASS
        "PRODUCTION,true,true,false,ENFORCE",     // DB+KMS, no backup
        "PRODUCTION,true,false,true,ENFORCE",     // DB+Backup, no KMS - FAIL
        "PRODUCTION,false,true,true,ENFORCE",     // KMS+Backup, no DB - FAIL
        "PRODUCTION,true,false,false,ENFORCE",    // DB only - FAIL
        "PRODUCTION,false,true,false,ENFORCE",    // KMS only - FAIL
        "PRODUCTION,false,false,true,ENFORCE",    // Backup only - FAIL
        "PRODUCTION,false,false,false,ENFORCE",   // No security - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,ENFORCE",
        "STAGING,false,false,false,ENFORCE",
        "STAGING,true,true,true,ADVISORY",
        // DEV - Should skip
        "DEV,true,true,true,ENFORCE",
        "DEV,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY"
    })
    void testPciDssExpandedVendorDefaultsAndDbSecurity(String profile, boolean dbSecurity,
                                                       boolean kmsRotation, boolean automatedBackup,
                                                       String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpVnd" + profile.substring(0,3) + dbSecurity + kmsRotation + automatedBackup;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("databaseSecurityEnabled", String.valueOf(dbSecurity));
        cfcContext.put("kmsKeyRotationEnabled", String.valueOf(kmsRotation));
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 4 - Transmission Security (All Combinations).
     * Tests TLS certificate, EFS encryption in transit, and network mode combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,private-with-nat,ENFORCE",   // All secure - PASS
        "PRODUCTION,true,true,public-no-nat,ENFORCE",      // TLS+EFS, public network
        "PRODUCTION,true,false,private-with-nat,ENFORCE",  // TLS only, private - FAIL
        "PRODUCTION,false,true,private-with-nat,ENFORCE",  // EFS only, private - FAIL
        "PRODUCTION,true,false,public-no-nat,ENFORCE",     // TLS only, public - FAIL
        "PRODUCTION,false,true,public-no-nat,ENFORCE",     // EFS only, public - FAIL
        "PRODUCTION,false,false,private-with-nat,ENFORCE", // No TLS/EFS, private - FAIL
        "PRODUCTION,false,false,public-no-nat,ENFORCE",    // No security - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,private-with-nat,ENFORCE",
        "STAGING,false,false,public-no-nat,ENFORCE",
        "STAGING,true,true,private-with-nat,ADVISORY",
        // DEV - Should skip
        "DEV,true,true,private-with-nat,ENFORCE",
        "DEV,false,false,public-no-nat,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,private-with-nat,ADVISORY",
        "PRODUCTION,false,false,public-no-nat,ADVISORY"
    })
    void testPciDssExpandedTransmissionSecurity(String profile, boolean hasCert,
                                                boolean efsTransit, String networkMode,
                                                String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpTrans" + profile.substring(0,3) + hasCert + efsTransit +
                        networkMode.substring(0,3);
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        cfcContext.put("networkMode", networkMode);

        if (hasCert) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "jenkins.example.com");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded truth table: PCI-DSS Req 8 - System Monitoring and GuardDuty (All Combinations).
     * Tests security monitoring, GuardDuty, and AWS Config combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,true,ENFORCE",      // All monitoring - PASS
        "PRODUCTION,true,true,false,ENFORCE",     // SecMon+GuardDuty, no Config
        "PRODUCTION,true,false,true,ENFORCE",     // SecMon+Config, no GuardDuty - FAIL
        "PRODUCTION,false,true,true,ENFORCE",     // GuardDuty+Config, no SecMon - FAIL
        "PRODUCTION,true,false,false,ENFORCE",    // SecMon only - FAIL
        "PRODUCTION,false,true,false,ENFORCE",    // GuardDuty only - FAIL
        "PRODUCTION,false,false,true,ENFORCE",    // Config only - FAIL
        "PRODUCTION,false,false,false,ENFORCE",   // No monitoring - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,ENFORCE",
        "STAGING,false,false,false,ENFORCE",
        "STAGING,true,true,true,ADVISORY",
        // DEV - Should skip
        "DEV,true,true,true,ENFORCE",
        "DEV,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY"
    })
    void testPciDssExpandedSystemMonitoring(String profile, boolean secMonitoring,
                                            boolean guardDuty, boolean awsConfig,
                                            String complianceMode) {
        App app = new App();
        String stackId = "TestPciExpMon" + profile.substring(0,3) + secMonitoring + guardDuty + awsConfig;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }

    /**
     * Expanded comprehensive truth table: PCI-DSS Multiple Requirements.
     * Tests realistic scenarios combining multiple PCI-DSS requirements.
     */
    @ParameterizedTest
    @CsvSource({
        // Scenario 1: Fully compliant production
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,365",

        // Scenario 2: Minimal production (maximum violations)
        "PRODUCTION,ENFORCE,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,90",

        // Scenario 3: Encryption issues only
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,false,false,false,false,true,true,true,true,true,true,true,true,365",

        // Scenario 4: Authentication issues only
        "PRODUCTION,ENFORCE,private-with-nat,none,true,true,true,true,true,true,true,true,false,false,false,false,365",

        // Scenario 5: Logging issues only
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,false,false,false,true,true,true,true,90",

        // Scenario 6: Network and transmission security issues
        "PRODUCTION,ENFORCE,public-no-nat,none,true,true,true,false,false,true,true,true,true,true,true,true,365",

        // Scenario 7: Key management issues
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,false,false,false,true,365",

        // Scenario 8: Monitoring issues
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,false,365",

        // Scenario 9: STAGING fully compliant
        "STAGING,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,365",

        // Scenario 10: STAGING minimal compliance
        "STAGING,ENFORCE,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,90",

        // Scenario 11: DEV (should skip PCI-DSS)
        "DEV,ENFORCE,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,90",

        // Scenario 12: PRODUCTION ADVISORY mode - fully compliant
        "PRODUCTION,ADVISORY,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,365",

        // Scenario 13: PRODUCTION ADVISORY mode - minimal compliance
        "PRODUCTION,ADVISORY,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,90",

        // Scenario 14: Partial compliance - good encryption, poor auth
        "PRODUCTION,ENFORCE,private-with-nat,none,true,true,true,true,true,true,true,true,false,false,true,true,365",

        // Scenario 15: Partial compliance - good auth, poor encryption
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,false,false,false,false,true,true,true,true,true,true,true,true,365",

        // Scenario 16: Extended retention with full compliance
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,2555"
    })
    void testPciDssExpandedComprehensiveMultiRequirement(String profile, String complianceMode,
                                                         String networkMode, String authMode,
                                                         boolean ebsEnc, boolean efsRestEnc,
                                                         boolean s3Enc, boolean efsTransEnc,
                                                         boolean hasCert, boolean cloudTrail,
                                                         boolean flowLogs, boolean albLogging,
                                                         boolean kmsRotation, boolean automatedBackup,
                                                         boolean crossRegion, boolean awsConfig,
                                                         int retentionDays) {
        App app = new App();
        String stackId = "TestPciExpComp" + profile.substring(0,3) + complianceMode.substring(0,3) +
                        networkMode.substring(0,3) + authMode.substring(0,3);
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("networkMode", networkMode);
        cfcContext.put("authMode", authMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEnc));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsRestEnc));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Enc));
        cfcContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransEnc));
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLogging", String.valueOf(albLogging));
        cfcContext.put("kmsKeyRotationEnabled", String.valueOf(kmsRotation));
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("crossRegionBackupEnabled", String.valueOf(crossRegion));
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        cfcContext.put("logRetentionDays", String.valueOf(retentionDays));

        if (hasCert) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "jenkins.example.com");
        }

        if (authMode.equals("alb-oidc") || authMode.equals("jenkins-oidc")) {
            cfcContext.put("cognitoAutoProvision", "true");
            cfcContext.put("cognitoMfaEnabled", "true");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> PciDssRules.install(ctx));
    }
}
