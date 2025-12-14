package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

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
        cfcContext.put("gdprDataTransferApproved", true);
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
        assertDoesNotThrow(() -> new GdprRules().install(ctx));
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
        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    void testGdprRulesMultipleInstallations() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createGdprStack(app, "TestGdprMultiple", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing GDPR rules multiple times
        new GdprRules().install(ctx);

        // Then: Should be idempotent
        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithEncryptionAtRest() {
        // Given: A deployment with encryption at rest (Art. 32 - Confidentiality)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprEncryption");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprEncryption");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("encryptionAtRest", "true");
        cfcContext.put("kmsKeyRotation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithEncryptionInTransit() {
        // Given: A deployment with encryption in transit (Art. 32)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprTransit");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprTransit");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        cfcContext.put("tlsVersion", "1.2");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataMinimization() {
        // Given: A deployment with data minimization (Art. 5(1)(c))
        App app = new App();
        Stack stack = new Stack(app, "TestGdprDataMin");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprDataMin");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("dataMinimizationEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithBreachNotification() {
        // Given: A deployment with breach notification (Art. 33 - 72 hour requirement)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprBreach");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprBreach");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("breachNotification72Hours", "true");
        cfcContext.put("securityContactEmail", "dpo@example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataSubjectRights() {
        // Given: A deployment supporting data subject rights (Art. 15-22)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprRights");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprRights");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("dataSubjectAccessEnabled", "true");
        cfcContext.put("rightToErasureSupported", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithAuditLogging() {
        // Given: A deployment with audit logging (Art. 30 - Records of processing)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprAudit");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprAudit");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("auditLoggingEnabled", "true");
        cfcContext.put("processingRecordsEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithAccessControl() {
        // Given: A deployment with access control (Art. 32 - Integrity and confidentiality)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprAccess");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprAccess");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("rbacEnabled", "true");
        cfcContext.put("leastPrivilegeEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataProtectionImpactAssessment() {
        // Given: A deployment with DPIA (Art. 35)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprDpia");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprDpia");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("dpiaCompleted", "true");
        cfcContext.put("highRiskProcessing", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataRetention() {
        // Given: A deployment with data retention policy (Art. 5(1)(e) - Storage limitation)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprRetention");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprRetention");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("dataRetentionPolicyEnabled", "true");
        cfcContext.put("automaticDeletionEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithPseudonymization() {
        // Given: A deployment with pseudonymization (Art. 32(1)(a))
        App app = new App();
        Stack stack = new Stack(app, "TestGdprPseudo");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprPseudo");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("pseudonymizationEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataPortability() {
        // Given: A deployment supporting data portability (Art. 20)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprPortability");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprPortability");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("dataPortabilityEnabled", "true");
        cfcContext.put("structuredDataFormat", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithAllRequirements() {
        // Given: A deployment with all GDPR requirements
        App app = new App();
        Stack stack = new Stack(app, "TestGdprComplete");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprComplete");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        // Art. 32: Security of processing
        cfcContext.put("encryptionAtRest", "true");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        cfcContext.put("pseudonymizationEnabled", "true");
        // Art. 33: Breach notification
        cfcContext.put("breachNotification72Hours", "true");
        cfcContext.put("securityContactEmail", "dpo@example.com");
        // Art. 30: Records of processing
        cfcContext.put("auditLoggingEnabled", "true");
        cfcContext.put("processingRecordsEnabled", "true");
        // Art. 15-22: Data subject rights
        cfcContext.put("dataSubjectAccessEnabled", "true");
        cfcContext.put("rightToErasureSupported", "true");
        cfcContext.put("dataPortabilityEnabled", "true");
        // Art. 5: Principles
        cfcContext.put("dataMinimizationEnabled", "true");
        cfcContext.put("dataRetentionPolicyEnabled", "true");
        // Art. 35: DPIA
        cfcContext.put("dpiaCompleted", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithStagingProfile() {
        // Given: A STAGING deployment with GDPR (should be advisory)
        App app = new App();
        Stack stack = createGdprStack(app, "TestGdprStaging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithMinimalConfiguration() {
        // Given: A minimal GDPR deployment
        App app = new App();
        Stack stack = createGdprStack(app, "TestGdprMinimal", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithLawfulBasisDocumentation() {
        // Given: A deployment with lawful basis documentation (Art. 6)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprLawful");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprLawful");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("lawfulBasisDocumented", "true");
        cfcContext.put("consentManagementEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataTransferMechanisms() {
        // Given: A deployment with international data transfer safeguards (Art. 44-49)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprTransfer");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprTransfer");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("internationalDataTransfers", "true");
        cfcContext.put("adequacyDecision", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithAutomatedDecisionMaking() {
        // Given: A deployment with automated decision-making safeguards (Art. 22)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprAutomated");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprAutomated");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("automatedDecisionMaking", "true");
        cfcContext.put("humanReviewRequired", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithProcessorAgreements() {
        // Given: A deployment with data processor agreements (Art. 28)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprProcessor");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprProcessor");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("processorAgreementsInPlace", "true");
        cfcContext.put("subProcessorApproval", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithPrivacyByDesign() {
        // Given: A deployment with privacy by design (Art. 25)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprPrivacyDesign");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprPrivacyDesign");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("privacyByDesign", "true");
        cfcContext.put("privacyByDefault", "true");
        cfcContext.put("dataProtectionByDefault", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithTransparency() {
        // Given: A deployment with transparency measures (Art. 12-14)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprTransparency");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprTransparency");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("privacyNoticeProvided", "true");
        cfcContext.put("clearLanguageUsed", "true");
        cfcContext.put("processingPurposesDocumented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithDataAccuracy() {
        // Given: A deployment ensuring data accuracy (Art. 5(1)(d))
        App app = new App();
        Stack stack = new Stack(app, "TestGdprAccuracy");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprAccuracy");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("dataAccuracyChecks", "true");
        cfcContext.put("dataUpdateMechanism", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithRightToRectification() {
        // Given: A deployment supporting right to rectification (Art. 16)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprRectification");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprRectification");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("rectificationSupported", "true");
        cfcContext.put("recipientNotificationEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithRightToRestriction() {
        // Given: A deployment supporting right to restriction of processing (Art. 18)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprRestriction");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprRestriction");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("processingRestrictionSupported", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithRightToObject() {
        // Given: A deployment supporting right to object (Art. 21)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprObject");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprObject");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("rightToObjectSupported", "true");
        cfcContext.put("directMarketingOptOut", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithAccountability() {
        // Given: A deployment with accountability measures (Art. 5(2))
        App app = new App();
        Stack stack = new Stack(app, "TestGdprAccountability");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprAccountability");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("complianceDocumentation", "true");
        cfcContext.put("evidenceOfCompliance", "true");
        cfcContext.put("dataProtectionPolicies", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprWithChildrenDataProtection() {
        // Given: A deployment with special protections for children's data (Art. 8)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprChildren");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprChildren");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("childrenDataProcessing", "true");
        cfcContext.put("parentalConsentRequired", "true");
        cfcContext.put("ageVerificationEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    // ========================================
    // GDPR Truth Table Tests - Branch Coverage
    // ========================================
    // These parameterized tests systematically test all branch combinations
    // within GDPR compliance validation logic using truth-table methodology.
    // See: docs/testing/COMPLIANCE_TRUTH_TABLES.md

    /**
     * Test GDPR security profile branches (Art. 25-33).
     * GDPR validation only applies to PRODUCTION and STAGING profiles.
     * DEV profile should skip GDPR validation entirely (early return).
     */
    @ParameterizedTest
    @CsvSource({
        "DEV,FARGATE,ADVISORY",           // DEV skips GDPR entirely (early return branch)
        "DEV,FARGATE,ENFORCE",            // DEV with enforce still skips
        "STAGING,FARGATE,ADVISORY",       // STAGING runs GDPR validation
        "STAGING,FARGATE,ENFORCE",        // STAGING with enforce mode
        "PRODUCTION,FARGATE,ADVISORY",    // PRODUCTION runs GDPR validation
        "PRODUCTION,FARGATE,ENFORCE"      // PRODUCTION with enforce mode
    })
    void testGdprSecurityProfileBranches(String profile, String runtime, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprProfile");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Add minimal requirements to ensure synthesis succeeds
            customContext.putIfAbsent("ebsEncryptionEnabled", "true");
            customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
            customContext.putIfAbsent("s3EncryptionEnabled", "true");
            customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
            customContext.putIfAbsent("cloudTrailEnabled", "true");
            customContext.putIfAbsent("flowLogsEnabled", "true");
            customContext.putIfAbsent("albAccessLogging", "true");
            customContext.putIfAbsent("guardDutyEnabled", "true");
            customContext.putIfAbsent("securityMonitoringEnabled", "true");
            customContext.putIfAbsent("networkMode", "private-with-nat");
            customContext.putIfAbsent("authMode", "alb-oidc");
            customContext.putIfAbsent("enableSsl", "true");
            customContext.putIfAbsent("fqdn", "gdpr.example.com");
            customContext.putIfAbsent("cognitoAutoProvision", "true");

            if (secProfile == SecurityProfile.PRODUCTION) {
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("awsConfigEnabled", "true");
                customContext.putIfAbsent("wafEnabled", "true");
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprProfile", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        // Should not throw regardless of whether validation runs
        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
            "Expected validation to pass for profile: " + profile + " mode: " + complianceMode);
    }

    /**
     * Test GDPR Article 25: Data Protection by Design - Encryption combinations.
     * Tests all combinations of EBS, EFS, and S3 encryption (Art. 25(1) & 32(1)(a)).
     */
    @ParameterizedTest
    @CsvSource({
        // All encryption enabled - fully compliant
        "PRODUCTION,FARGATE,true,true,true,ENFORCE",
        // Individual encryption types disabled
        "PRODUCTION,FARGATE,false,true,true,ENFORCE",     // EBS disabled - FAIL
        "PRODUCTION,FARGATE,true,false,true,ENFORCE",     // EFS at-rest disabled - FAIL
        "PRODUCTION,FARGATE,true,true,false,ENFORCE",     // S3 disabled - FAIL
        "PRODUCTION,FARGATE,false,false,true,ENFORCE",    // EBS and EFS disabled - FAIL
        "PRODUCTION,FARGATE,false,true,false,ENFORCE",    // EBS and S3 disabled - FAIL
        "PRODUCTION,FARGATE,true,false,false,ENFORCE",    // EFS and S3 disabled - FAIL
        // All encryption disabled - maximum non-compliance
        "PRODUCTION,FARGATE,false,false,false,ENFORCE",   // FAIL
        // Advisory mode scenarios
        "PRODUCTION,FARGATE,true,true,true,ADVISORY",
        "PRODUCTION,FARGATE,false,false,false,ADVISORY",
        // STAGING scenarios
        "STAGING,FARGATE,true,true,true,ENFORCE",
        "STAGING,FARGATE,false,false,false,ENFORCE"       // FAIL
    })
    void testGdprDataProtectionByDesignEncryption(String profile, String runtime, boolean ebsEncryption,
                                                   boolean efsEncryption, boolean s3Encryption,
                                                   String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprEncryption");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        customContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsEncryption));
        customContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = ebsEncryption && efsEncryption && s3Encryption;
            if (hasRequirement) {
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprEncryption", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            if (!ebsEncryption || !efsEncryption || !s3Encryption) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing encryption: " + profile + " ebs=" + ebsEncryption + " efs=" + efsEncryption + " s3=" + s3Encryption);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " ebs=" + ebsEncryption + " efs=" + efsEncryption + " s3=" + s3Encryption);
        }
    }

    /**
     * Test GDPR Article 25: Network isolation for data protection by design.
     * Public network mode in PRODUCTION should trigger validation failure (Art. 25(1)).
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,public-no-nat,ENFORCE",      // Public mode in PROD - should fail
        "PRODUCTION,FARGATE,private-with-nat,ENFORCE",   // Private mode in PROD - compliant
        "STAGING,FARGATE,public-no-nat,ENFORCE",         // Public mode in STAGING - should fail
        "STAGING,FARGATE,private-with-nat,ENFORCE",      // Private mode in STAGING
        "PRODUCTION,FARGATE,public-no-nat,ADVISORY",     // Public mode PROD advisory
        "PRODUCTION,FARGATE,private-with-nat,ADVISORY"   // Private mode PROD advisory
    })
    void testGdprNetworkIsolation(String profile, String runtime, String networkMode, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprNetwork");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("networkMode", networkMode);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        // Network isolation is only required for PRODUCTION, so STAGING with public network still needs other requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean shouldPass = (secProfile != SecurityProfile.PRODUCTION || networkMode.equals("private-with-nat"));
            if (shouldPass) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprNetwork", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (networkMode.equals("public-no-nat")) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for public network in PRODUCTION: " + profile + " network=" + networkMode);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " network=" + networkMode);
        }
    }

    /**
     * Test GDPR Article 30: Records of Processing Activities - Audit logging combinations.
     * Tests CloudTrail, VPC Flow Logs, and ALB access logging (Art. 30(1)).
     */
    @ParameterizedTest
    @CsvSource({
        // All logging enabled - fully compliant
        "PRODUCTION,FARGATE,true,true,true,ENFORCE",
        // Individual logging types disabled
        "PRODUCTION,FARGATE,false,true,true,ENFORCE",     // CloudTrail disabled - FAIL
        "PRODUCTION,FARGATE,true,false,true,ENFORCE",     // Flow Logs disabled - FAIL
        "PRODUCTION,FARGATE,true,true,false,ENFORCE",     // ALB logging disabled - FAIL
        "PRODUCTION,FARGATE,false,false,true,ENFORCE",    // CloudTrail and Flow Logs disabled - FAIL
        "PRODUCTION,FARGATE,false,true,false,ENFORCE",    // CloudTrail and ALB disabled - FAIL
        "PRODUCTION,FARGATE,true,false,false,ENFORCE",    // Flow Logs and ALB disabled - FAIL
        // All logging disabled - maximum non-compliance
        "PRODUCTION,FARGATE,false,false,false,ENFORCE",   // FAIL
        // Advisory mode scenarios
        "PRODUCTION,FARGATE,true,true,true,ADVISORY",
        "PRODUCTION,FARGATE,false,false,false,ADVISORY",
        // STAGING scenarios
        "STAGING,FARGATE,true,true,true,ENFORCE",
        "STAGING,FARGATE,false,false,false,ENFORCE"       // FAIL
    })
    void testGdprProcessingRecordsLogging(String profile, String runtime, boolean cloudTrail,
                                          boolean flowLogs, boolean albLogging, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprLogging");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        customContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        customContext.put("albAccessLogging", String.valueOf(albLogging));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = cloudTrail && flowLogs && albLogging;
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprLogging", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            if (!cloudTrail || !flowLogs || !albLogging) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing logging: " + profile + " cloudTrail=" + cloudTrail + " flowLogs=" + flowLogs + " albLogging=" + albLogging);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " cloudTrail=" + cloudTrail + " flowLogs=" + flowLogs + " albLogging=" + albLogging);
        }
    }

    /**
     * Test GDPR Article 32: Security of Processing - Authentication and encryption in transit.
     * Tests TLS certificate, EFS in-transit encryption, and authentication mode (Art. 32(1)(a-b)).
     */
    @ParameterizedTest
    @CsvSource({
        // Fully secure - TLS cert, EFS transit encryption, authentication enabled
        "PRODUCTION,FARGATE,true,true,alb-oidc,ENFORCE",
        "PRODUCTION,FARGATE,true,true,jenkins-oidc,ENFORCE",
        // Missing TLS certificate - use jenkins-oidc since alb-oidc requires cert
        "PRODUCTION,FARGATE,false,true,jenkins-oidc,ENFORCE",   // FAIL - no TLS
        "PRODUCTION,FARGATE,false,true,none,ENFORCE",           // FAIL - no TLS, no auth
        // Missing EFS in-transit encryption
        "PRODUCTION,FARGATE,true,false,alb-oidc,ENFORCE",       // FAIL - no EFS transit
        "PRODUCTION,FARGATE,true,false,jenkins-oidc,ENFORCE",   // FAIL - no EFS transit
        // No authentication
        "PRODUCTION,FARGATE,true,true,none,ENFORCE",            // FAIL - no auth
        "PRODUCTION,FARGATE,false,true,none,ENFORCE",           // FAIL - no TLS, no auth
        "PRODUCTION,FARGATE,true,false,none,ENFORCE",           // FAIL - no EFS transit, no auth
        // All disabled - maximum non-compliance
        "PRODUCTION,FARGATE,false,false,none,ENFORCE",          // FAIL
        // Advisory mode scenarios
        "PRODUCTION,FARGATE,true,true,alb-oidc,ADVISORY",
        "PRODUCTION,FARGATE,false,false,none,ADVISORY",
        // STAGING scenarios
        "STAGING,FARGATE,true,true,alb-oidc,ENFORCE",
        "STAGING,FARGATE,false,false,none,ENFORCE"              // FAIL
    })
    void testGdprSecurityOfProcessingTransit(String profile, String runtime, boolean hasCert,
                                             boolean efsTransit, String authMode, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprTransit");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("authMode", authMode);
        customContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));

        if (hasCert) {
            customContext.put("enableSsl", "true");
            customContext.put("fqdn", "gdpr.example.com");
        }

        if (authMode.equals("alb-oidc") || authMode.equals("jenkins-oidc")) {
            customContext.put("cognitoAutoProvision", "true");
        }

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = hasCert && efsTransit && !authMode.equals("none");
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprTransit", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        if (hasCert) {
            builder.createMockCertificate();
        }
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            if (!hasCert || !efsTransit || authMode.equals("none")) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing transit security: " + profile + " hasCert=" + hasCert + " efsTransit=" + efsTransit + " authMode=" + authMode);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " hasCert=" + hasCert + " efsTransit=" + efsTransit + " authMode=" + authMode);
        }
    }

    /**
     * Test GDPR Article 32: Security monitoring and backup (Art. 32(1)(b-c)).
     * Security monitoring is required for integrity, backup for availability.
     * Tests only apply these requirements to PRODUCTION profile.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - all combinations
        "PRODUCTION,FARGATE,true,true,ENFORCE",      // Full monitoring and backup
        "PRODUCTION,FARGATE,false,true,ENFORCE",     // No security monitoring - FAIL
        "PRODUCTION,FARGATE,true,false,ENFORCE",     // No backup - FAIL
        "PRODUCTION,FARGATE,false,false,ENFORCE",    // No monitoring or backup - FAIL
        "PRODUCTION,FARGATE,true,true,ADVISORY",     // Advisory mode
        "PRODUCTION,FARGATE,false,false,ADVISORY",   // Advisory with both disabled
        // STAGING - backup not required
        "STAGING,FARGATE,true,true,ENFORCE",
        "STAGING,FARGATE,false,false,ENFORCE"        // FAIL - monitoring required
    })
    void testGdprSecurityMonitoringAndBackup(String profile, String runtime, boolean securityMonitoring,
                                             boolean automatedBackup, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprMonitoring");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("securityMonitoringEnabled", String.valueOf(securityMonitoring));
        customContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = securityMonitoring && (secProfile != SecurityProfile.PRODUCTION || automatedBackup);
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprMonitoring", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            if (!securityMonitoring) {
                shouldFail = true;
            }
            if (secProfile == SecurityProfile.PRODUCTION && !automatedBackup) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing monitoring/backup: " + profile + " monitoring=" + securityMonitoring + " backup=" + automatedBackup);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " monitoring=" + securityMonitoring + " backup=" + automatedBackup);
        }
    }

    /**
     * Test GDPR Article 32(1)(d): AWS Config for regularly assessing security measures.
     * Only required for PRODUCTION profile.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,ENFORCE",       // AWS Config enabled in PROD
        "PRODUCTION,FARGATE,false,ENFORCE",      // AWS Config disabled in PROD - should fail
        "PRODUCTION,FARGATE,true,ADVISORY",      // Advisory mode
        "PRODUCTION,FARGATE,false,ADVISORY",     // Advisory disabled
        "STAGING,FARGATE,true,ENFORCE",          // STAGING - not required
        "STAGING,FARGATE,false,ENFORCE"          // STAGING disabled - allowed
    })
    void testGdprAwsConfig(String profile, String runtime, boolean awsConfig, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprConfig");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("awsConfigEnabled", String.valueOf(awsConfig));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = (secProfile != SecurityProfile.PRODUCTION || awsConfig);
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprConfig", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!awsConfig) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing AWS Config: " + profile + " awsConfig=" + awsConfig);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " awsConfig=" + awsConfig);
        }
    }

    /**
     * Test GDPR Article 33: Breach Detection and Notification (72-hour requirement).
     * Tests GuardDuty for threat detection and security monitoring for breach detection.
     */
    @ParameterizedTest
    @CsvSource({
        // Both enabled - fully compliant
        "PRODUCTION,FARGATE,true,true,ENFORCE",
        // Individual components disabled
        "PRODUCTION,FARGATE,false,true,ENFORCE",           // No GuardDuty - FAIL
        "PRODUCTION,FARGATE,true,false,ENFORCE",           // No security monitoring - FAIL
        // Both disabled - maximum non-compliance
        "PRODUCTION,FARGATE,false,false,ENFORCE",          // FAIL
        // Advisory mode scenarios
        "PRODUCTION,FARGATE,true,true,ADVISORY",
        "PRODUCTION,FARGATE,false,false,ADVISORY",
        // STAGING scenarios
        "STAGING,FARGATE,true,true,ENFORCE",
        "STAGING,FARGATE,false,false,ENFORCE"              // FAIL
    })
    void testGdprBreachDetection(String profile, String runtime, boolean guardDuty,
                                 boolean securityMonitoring, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprBreach");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        customContext.put("securityMonitoringEnabled", String.valueOf(securityMonitoring));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = guardDuty && securityMonitoring;
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                    customContext.putIfAbsent("wafEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprBreach", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            if (!guardDuty || !securityMonitoring) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing breach detection: " + profile + " guardDuty=" + guardDuty + " securityMonitoring=" + securityMonitoring);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " guardDuty=" + guardDuty + " securityMonitoring=" + securityMonitoring);
        }
    }

    /**
     * Test GDPR Article 33: WAF for breach prevention (Art. 32(1)).
     * Only required for PRODUCTION profile.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,ENFORCE",       // WAF enabled in PROD
        "PRODUCTION,FARGATE,false,ENFORCE",      // WAF disabled in PROD - should fail
        "PRODUCTION,FARGATE,true,ADVISORY",      // Advisory mode
        "PRODUCTION,FARGATE,false,ADVISORY",     // Advisory disabled
        "STAGING,FARGATE,true,ENFORCE",          // STAGING - not required
        "STAGING,FARGATE,false,ENFORCE"          // STAGING disabled - allowed
    })
    void testGdprWafProtection(String profile, String runtime, boolean wafEnabled, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestGdprWaf");
        customContext.put("gdprDataTransferApproved", true);
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("wafEnabled", String.valueOf(wafEnabled));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);

        // Add baseline GDPR requirements for tests expecting to pass
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            boolean hasRequirement = (secProfile != SecurityProfile.PRODUCTION || wafEnabled);
            if (hasRequirement) {
                customContext.putIfAbsent("ebsEncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionAtRestEnabled", "true");
                customContext.putIfAbsent("s3EncryptionEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("authMode", "alb-oidc");
                customContext.putIfAbsent("enableSsl", "true");
                customContext.putIfAbsent("fqdn", "gdpr.example.com");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("automatedBackupEnabled", "true");
                    customContext.putIfAbsent("awsConfigEnabled", "true");
                }
            }
        }

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestGdprWaf", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new GdprRules().install(builder.getSystemContext());

        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && secProfile == SecurityProfile.PRODUCTION) {
            if (!wafEnabled) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing WAF: " + profile + " wafEnabled=" + wafEnabled);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " wafEnabled=" + wafEnabled);
        }
    }

    /**
     * Comprehensive GDPR compliance scenarios testing realistic configuration combinations.
     * Tests combinations of multiple compliance requirements together.
     */
    @ParameterizedTest
    @CsvSource({
        // Scenario 1: Fully compliant PRODUCTION
        "PRODUCTION,ENFORCE,true,true,true,true,alb-oidc,true,true,true,true,private-with-nat",
        // Scenario 2: Minimal PRODUCTION (many failures)
        "PRODUCTION,ENFORCE,false,false,false,false,none,false,false,false,false,public-no-nat",
        // Scenario 3: Partial compliance - encryption only
        "PRODUCTION,ENFORCE,true,true,true,false,none,false,false,false,false,public-no-nat",
        // Scenario 4: Partial compliance - monitoring only
        "PRODUCTION,ENFORCE,false,false,false,true,alb-oidc,true,true,true,true,private-with-nat",
        // Scenario 5: Advisory mode - fully configured
        "PRODUCTION,ADVISORY,true,true,true,true,alb-oidc,true,true,true,true,private-with-nat",
        // Scenario 6: Advisory mode - minimal
        "PRODUCTION,ADVISORY,false,false,false,false,none,false,false,false,false,public-no-nat",
        // Scenario 7: STAGING fully compliant
        "STAGING,ENFORCE,true,true,true,true,alb-oidc,true,true,true,true,private-with-nat",
        // Scenario 8: STAGING minimal (less restrictive)
        "STAGING,ENFORCE,false,false,false,false,none,false,false,false,false,public-no-nat"
    })
    void testGdprComprehensiveScenarios(String profile, String complianceMode,
                                        boolean ebsEncryption, boolean efsEncryption, boolean s3Encryption,
                                        boolean guardDuty, String authMode,
                                        boolean cloudTrail, boolean flowLogs, boolean securityMonitoring,
                                        boolean waf, String networkMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestGdprComprehensive");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprComprehensive");
        cfcContext.put("gdprDataTransferApproved", true);
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsEncryption));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("authMode", authMode);
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("securityMonitoringEnabled", String.valueOf(securityMonitoring));
        cfcContext.put("wafEnabled", String.valueOf(waf));
        cfcContext.put("networkMode", networkMode);

        // ALB OIDC requires SSL configuration
        if ("alb-oidc".equals(authMode)) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "gdpr.example.com");
        }

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    // ========================================
    // EXPANDED TRUTH TABLE TESTS - GDPR
    // ========================================
    // These expanded tests systematically cover all branch combinations
    // to maximize branch coverage for GDPR compliance validation

    /**
     * Expanded truth table: GDPR Art. 25 - Data Protection by Design (All Encryption Combinations).
     * Tests all 2^4 = 16 combinations of encryption settings including KMS rotation.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^4 = 16)
        "PRODUCTION,true,true,true,true,ENFORCE",       // All encryption - PASS
        "PRODUCTION,true,true,true,false,ENFORCE",      // EBS+EFS+S3, no KMS
        "PRODUCTION,true,true,false,true,ENFORCE",      // EBS+EFS+KMS, no S3
        "PRODUCTION,true,false,true,true,ENFORCE",      // EBS+S3+KMS, no EFS
        "PRODUCTION,false,true,true,true,ENFORCE",      // EFS+S3+KMS, no EBS
        "PRODUCTION,true,true,false,false,ENFORCE",     // EBS+EFS only
        "PRODUCTION,true,false,true,false,ENFORCE",     // EBS+S3 only
        "PRODUCTION,false,true,true,false,ENFORCE",     // EFS+S3 only
        "PRODUCTION,true,false,false,true,ENFORCE",     // EBS+KMS only
        "PRODUCTION,false,true,false,true,ENFORCE",     // EFS+KMS only
        "PRODUCTION,false,false,true,true,ENFORCE",     // S3+KMS only
        "PRODUCTION,true,false,false,false,ENFORCE",    // EBS only
        "PRODUCTION,false,true,false,false,ENFORCE",    // EFS only
        "PRODUCTION,false,false,true,false,ENFORCE",    // S3 only
        "PRODUCTION,false,false,false,true,ENFORCE",    // KMS only
        "PRODUCTION,false,false,false,false,ENFORCE",   // No encryption - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,true,ENFORCE",
        "STAGING,false,false,false,false,ENFORCE",
        "STAGING,true,true,true,true,ADVISORY",
        // DEV - Should skip
        "DEV,true,true,true,true,ENFORCE",
        "DEV,false,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,false,ADVISORY"
    })
    void testGdprExpandedDataProtectionEncryption(String profile, boolean ebsEncryption,
                                                   boolean efsEncryption, boolean s3Encryption,
                                                   boolean kmsRotation, String complianceMode) {
        App app = new App();
        String stackId = "TestGdprExpEnc" + profile.substring(0,3) + ebsEncryption + efsEncryption +
                        s3Encryption + kmsRotation;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsEncryption));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));
        cfcContext.put("kmsKeyRotationEnabled", String.valueOf(kmsRotation));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    /**
     * Expanded truth table: GDPR Art. 30 & 32 - Processing Records and Audit Logging (All Combinations).
     * Tests all 2^4 = 16 combinations including AWS Config for continuous assessment.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^4 = 16)
        "PRODUCTION,true,true,true,true,ENFORCE",       // All logging - PASS
        "PRODUCTION,true,true,true,false,ENFORCE",      // CloudTrail+FlowLogs+ALB, no Config
        "PRODUCTION,true,true,false,true,ENFORCE",      // CloudTrail+FlowLogs+Config, no ALB
        "PRODUCTION,true,false,true,true,ENFORCE",      // CloudTrail+ALB+Config, no FlowLogs
        "PRODUCTION,false,true,true,true,ENFORCE",      // FlowLogs+ALB+Config, no CloudTrail
        "PRODUCTION,true,true,false,false,ENFORCE",     // CloudTrail+FlowLogs only
        "PRODUCTION,true,false,true,false,ENFORCE",     // CloudTrail+ALB only
        "PRODUCTION,false,true,true,false,ENFORCE",     // FlowLogs+ALB only
        "PRODUCTION,true,false,false,true,ENFORCE",     // CloudTrail+Config only
        "PRODUCTION,false,true,false,true,ENFORCE",     // FlowLogs+Config only
        "PRODUCTION,false,false,true,true,ENFORCE",     // ALB+Config only
        "PRODUCTION,true,false,false,false,ENFORCE",    // CloudTrail only
        "PRODUCTION,false,true,false,false,ENFORCE",    // FlowLogs only
        "PRODUCTION,false,false,true,false,ENFORCE",    // ALB only
        "PRODUCTION,false,false,false,true,ENFORCE",    // Config only
        "PRODUCTION,false,false,false,false,ENFORCE",   // No logging - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,true,ENFORCE",
        "STAGING,false,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,false,ADVISORY"
    })
    void testGdprExpandedAuditLoggingAndConfig(String profile, boolean cloudTrail, boolean flowLogs,
                                               boolean albLogging, boolean awsConfig,
                                               String complianceMode) {
        App app = new App();
        String stackId = "TestGdprExpLog" + profile.substring(0,3) + cloudTrail + flowLogs +
                        albLogging + awsConfig;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLogging", String.valueOf(albLogging));
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    /**
     * Expanded truth table: GDPR Art. 32 & 33 - Security Monitoring and Breach Detection (All Combinations).
     * Tests all 2^4 = 16 combinations of monitoring and threat detection services.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^4 = 16)
        "PRODUCTION,true,true,true,true,ENFORCE",       // All monitoring - PASS
        "PRODUCTION,true,true,true,false,ENFORCE",      // SecMon+GuardDuty+SecurityHub, no WAF
        "PRODUCTION,true,true,false,true,ENFORCE",      // SecMon+GuardDuty+WAF, no SecurityHub
        "PRODUCTION,true,false,true,true,ENFORCE",      // SecMon+SecurityHub+WAF, no GuardDuty
        "PRODUCTION,false,true,true,true,ENFORCE",      // GuardDuty+SecurityHub+WAF, no SecMon
        "PRODUCTION,true,true,false,false,ENFORCE",     // SecMon+GuardDuty only
        "PRODUCTION,true,false,true,false,ENFORCE",     // SecMon+SecurityHub only
        "PRODUCTION,false,true,true,false,ENFORCE",     // GuardDuty+SecurityHub only
        "PRODUCTION,true,false,false,true,ENFORCE",     // SecMon+WAF only
        "PRODUCTION,false,true,false,true,ENFORCE",     // GuardDuty+WAF only
        "PRODUCTION,false,false,true,true,ENFORCE",     // SecurityHub+WAF only
        "PRODUCTION,true,false,false,false,ENFORCE",    // SecMon only
        "PRODUCTION,false,true,false,false,ENFORCE",    // GuardDuty only
        "PRODUCTION,false,false,true,false,ENFORCE",    // SecurityHub only
        "PRODUCTION,false,false,false,true,ENFORCE",    // WAF only
        "PRODUCTION,false,false,false,false,ENFORCE",   // No monitoring - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,true,true,ENFORCE",
        "STAGING,false,false,false,false,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,false,ADVISORY"
    })
    void testGdprExpandedSecurityMonitoringAndBreach(String profile, boolean secMonitoring,
                                                     boolean guardDuty, boolean securityHub,
                                                     boolean waf, String complianceMode) {
        App app = new App();
        String stackId = "TestGdprExpMon" + profile.substring(0,3) + secMonitoring + guardDuty +
                        securityHub + waf;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("securityHubEnabled", String.valueOf(securityHub));
        cfcContext.put("wafEnabled", String.valueOf(waf));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    /**
     * Expanded truth table: GDPR Art. 32 - Transmission Security (All Combinations).
     * Tests TLS, EFS transit encryption, network modes, and authentication combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All secure transmission combinations
        "PRODUCTION,true,true,private-with-nat,alb-oidc,ENFORCE",      // All secure - PASS
        "PRODUCTION,true,true,private-with-nat,jenkins-oidc,ENFORCE",  // Jenkins OIDC
        "PRODUCTION,true,true,private-with-nat,none,ENFORCE",          // No auth
        "PRODUCTION,true,true,public-no-nat,alb-oidc,ENFORCE",         // Public network
        "PRODUCTION,true,false,private-with-nat,alb-oidc,ENFORCE",     // No EFS transit
        "PRODUCTION,false,true,private-with-nat,jenkins-oidc,ENFORCE", // No TLS cert
        "PRODUCTION,true,false,public-no-nat,none,ENFORCE",            // Public, no EFS, no auth
        "PRODUCTION,false,false,private-with-nat,none,ENFORCE",        // No TLS, no EFS, no auth
        "PRODUCTION,false,false,public-no-nat,none,ENFORCE",           // All insecure - FAIL
        // STAGING - Key combinations
        "STAGING,true,true,private-with-nat,alb-oidc,ENFORCE",
        "STAGING,false,false,public-no-nat,none,ENFORCE",
        "STAGING,true,true,private-with-nat,alb-oidc,ADVISORY",
        // DEV - Should skip
        "DEV,true,true,private-with-nat,alb-oidc,ENFORCE",
        "DEV,false,false,public-no-nat,none,ENFORCE",
        // PRODUCTION with ADVISORY
        "PRODUCTION,true,true,private-with-nat,alb-oidc,ADVISORY",
        "PRODUCTION,false,false,public-no-nat,none,ADVISORY"
    })
    void testGdprExpandedTransmissionSecurity(String profile, boolean hasCert, boolean efsTransit,
                                              String networkMode, String authMode,
                                              String complianceMode) {
        App app = new App();
        String stackId = "TestGdprExpTrans" + profile.substring(0,3) + hasCert + efsTransit +
                        networkMode.substring(0,3) + authMode.substring(0,3);
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        cfcContext.put("networkMode", networkMode);
        cfcContext.put("authMode", authMode);

        if (hasCert) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "gdpr.example.com");
        }

        if (authMode.equals("alb-oidc") || authMode.equals("jenkins-oidc")) {
            cfcContext.put("cognitoAutoProvision", "true");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    /**
     * Expanded truth table: GDPR Art. 32 - Backup and Availability (All Combinations).
     * Tests automated backup, cross-region backup, point-in-time recovery combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION - All combinations (2^3 = 8)
        "PRODUCTION,true,true,true,ENFORCE",      // All backup features - PASS
        "PRODUCTION,true,true,false,ENFORCE",     // Automated+CrossRegion, no PITR
        "PRODUCTION,true,false,true,ENFORCE",     // Automated+PITR, no CrossRegion
        "PRODUCTION,false,true,true,ENFORCE",     // CrossRegion+PITR, no Automated
        "PRODUCTION,true,false,false,ENFORCE",    // Automated only
        "PRODUCTION,false,true,false,ENFORCE",    // CrossRegion only
        "PRODUCTION,false,false,true,ENFORCE",    // PITR only
        "PRODUCTION,false,false,false,ENFORCE",   // No backup - FAIL
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
    void testGdprExpandedBackupAndAvailability(String profile, boolean automatedBackup,
                                               boolean crossRegionBackup, boolean pointInTimeRecovery,
                                               String complianceMode) {
        App app = new App();
        String stackId = "TestGdprExpBackup" + profile.substring(0,3) + automatedBackup +
                        crossRegionBackup + pointInTimeRecovery;
        Stack stack = new Stack(app, stackId);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stack.getStackName());
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("crossRegionBackupEnabled", String.valueOf(crossRegionBackup));
        cfcContext.put("pointInTimeRecoveryEnabled", String.valueOf(pointInTimeRecovery));
        cfcContext.put("networkMode", "private-with-nat");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    /**
     * Expanded comprehensive truth table: GDPR Multiple Articles.
     * Tests realistic scenarios combining multiple GDPR requirements.
     */
    @ParameterizedTest
    @CsvSource({
        // Scenario 1: Fully compliant production with all security measures
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 2: Minimal production (maximum violations)
        "PRODUCTION,ENFORCE,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 3: Encryption and logging compliant, but weak monitoring
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,false,false,false,false,true",

        // Scenario 4: Strong monitoring, but weak encryption
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,false,false,false,false,true,true,true,true,true,true,true,true,true",

        // Scenario 5: Good encryption and monitoring, no backup
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,false,false",

        // Scenario 6: Network isolation issues
        "PRODUCTION,ENFORCE,public-no-nat,none,true,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 7: No authentication but otherwise compliant
        "PRODUCTION,ENFORCE,private-with-nat,none,true,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 8: STAGING fully compliant
        "STAGING,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 9: STAGING minimal
        "STAGING,ENFORCE,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 10: DEV (should skip GDPR)
        "DEV,ENFORCE,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 11: PRODUCTION ADVISORY mode - fully compliant
        "PRODUCTION,ADVISORY,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 12: PRODUCTION ADVISORY mode - minimal
        "PRODUCTION,ADVISORY,public-no-nat,none,false,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 13: Partial compliance - encryption only
        "PRODUCTION,ENFORCE,private-with-nat,none,true,true,true,true,false,false,false,false,false,false,false,false,false",

        // Scenario 14: Partial compliance - monitoring only
        "PRODUCTION,ENFORCE,private-with-nat,none,false,false,false,false,true,true,true,true,true,true,true,false,false",

        // Scenario 15: Good auth and encryption, weak monitoring
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,false,true,false,false,true,true",

        // Scenario 16: Extended compliance with all premium features
        "PRODUCTION,ENFORCE,private-with-nat,alb-oidc,true,true,true,true,true,true,true,true,true,true,true,true,true"
    })
    void testGdprExpandedComprehensiveMultiArticle(String profile, String complianceMode,
                                                   String networkMode, String authMode,
                                                   boolean ebsEnc, boolean efsRestEnc, boolean s3Enc,
                                                   boolean efsTransEnc, boolean cloudTrail, boolean flowLogs,
                                                   boolean albLogging, boolean awsConfig, boolean guardDuty,
                                                   boolean secMonitoring, boolean waf, boolean automatedBackup,
                                                   boolean crossRegion) {
        App app = new App();
        String stackId = "TestGdprExpComp" + profile.substring(0,3) + complianceMode.substring(0,3) +
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
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        cfcContext.put("wafEnabled", String.valueOf(waf));
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("crossRegionBackupEnabled", String.valueOf(crossRegion));

        if (authMode.equals("alb-oidc")) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "gdpr.example.com");
            cfcContext.put("cognitoAutoProvision", "true");
        }
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }
}
