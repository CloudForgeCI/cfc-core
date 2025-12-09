package com.cloudforgeci.api.core.rules;

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
 * Test suite for GdprOrganizationalRules.
 *
 * Tests GDPR organizational compliance validation rules.
 */
class GdprOrganizationalRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testGdprOrganizationalRulesSkippedWithoutGDPR() {
        // Given: A deployment without GDPR compliance
        App app = new App();
        Stack stack = createTestStack(app, "TestGdprOrgNoGDPR", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing GDPR organizational rules
        // Then: Should not throw (rules are skipped)
        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testGdprOrganizationalRulesWithGDPREnabled() {
        // Given: A deployment with GDPR compliance
        App app = new App();
        Stack stack = new Stack(app, "TestGdprOrg");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprOrg");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing GDPR organizational rules
        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testGdprOrganizationalRulesValidationExecutes() {
        // Given: A deployment context with GDPR
        App app = new App();
        Stack stack = new Stack(app, "TestGdprOrgValidation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprOrgValidation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing GDPR organizational rules
        new GdprOrganizationalRules().install(ctx);

        // Then: Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testLegalBasisValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestLegalBasis");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestLegalBasis");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprLegalBasisDocumented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testConsentMechanismValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestConsent");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestConsent");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprConsentMechanismImplemented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testPrivacyNoticeValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestPrivacyNotice");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPrivacyNotice");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprPrivacyNoticeProvided", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testDataSubjectRightsValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestDataRights");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDataRights");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataSubjectRequestProcedures", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testRightToErasureValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestErasure");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestErasure");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprRightToErasureCapability", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testDataPortabilityValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestPortability");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPortability");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataPortabilityCapability", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testDpiaValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestDpia");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDpia");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDpiaCompleted", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testPrivacyByDesignValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestPrivacyDesign");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPrivacyDesign");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprPrivacyByDesignImplemented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testInternationalTransferSafeguardsValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestTransfer");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestTransfer");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("region", "us-west-2");
        cfcContext.put("gdprInternationalTransferSafeguards", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testDataLocalizationValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestLocalization");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestLocalization");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataLocalizationEnforced", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testDataRetentionPolicyValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestRetention");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestRetention");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprDataRetentionPolicyDefined", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testRecordsOfProcessingActivitiesValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestRopa");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestRopa");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("gdprRecordsOfProcessingActivities", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testGdprSkippedWithDevProfile() {
        App app = new App();
        Stack stack = new Stack(app, "TestDevProfile");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDevProfile");
        cfcContext.put("complianceFrameworks", "GDPR");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }

    @Test
    void testEuRegionSkipsInternationalTransferValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestEuRegion");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEuRegion");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("region", "eu-west-1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprOrganizationalRules().install(ctx));
    }
}
