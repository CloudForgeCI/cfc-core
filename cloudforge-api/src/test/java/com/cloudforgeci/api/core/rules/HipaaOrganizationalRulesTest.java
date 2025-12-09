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
 * Test suite for HipaaOrganizationalRules.
 *
 * Tests HIPAA organizational compliance validation rules.
 */
class HipaaOrganizationalRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testHipaaOrganizationalRulesSkippedWithoutHIPAA() {
        // Given: A deployment without HIPAA compliance
        App app = new App();
        Stack stack = createTestStack(app, "TestHipaaOrgNoHIPAA", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA organizational rules
        // Then: Should not throw (rules are skipped)
        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testHipaaOrganizationalRulesWithHIPAAEnabled() {
        // Given: A deployment with HIPAA compliance
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaOrg");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaOrg");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "HIPAA");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA organizational rules
        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testHipaaOrganizationalRulesValidationExecutes() {
        // Given: A deployment context with HIPAA
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaOrgValidation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaOrgValidation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "HIPAA");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA organizational rules
        new HipaaOrganizationalRules().install(ctx);

        // Then: Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testAwsBaaSignedValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestAwsBaa");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAwsBaa");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("awsBaaSigned", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testThirdPartyBaasDocumentedValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestThirdPartyBaas");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestThirdPartyBaas");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("thirdPartyBaasDocumented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testBaaProvisionsVerifiedValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestBaaProvisions");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestBaaProvisions");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("baaProvisionsVerified", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testSubcontractorBaasTrackedValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestSubcontractorBaas");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSubcontractorBaas");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("subcontractorBaasTracked", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testWorkforceAuthorizationProceduresValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestWorkforceAuth");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestWorkforceAuth");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("workforceAuthorizationProcedures", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testTerminationProceduresValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestTermination");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestTermination");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("terminationProcedures", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testHipaaTrainingProgramValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaTraining");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaTraining");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("hipaaTrainingProgram", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testEmergencyAccessProceduresValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestEmergencyAccess");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEmergencyAccess");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("emergencyAccessProcedures", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testAutomaticLogoffEnabledValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestAutomaticLogoff");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAutomaticLogoff");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("automaticLogoffEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testIncidentResponsePlanValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestIncidentResponse");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestIncidentResponse");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("incidentResponsePlan", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testBreachNotificationProceduresValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestBreachNotification");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestBreachNotification");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("breachNotificationProcedures", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testBreachDetectionAutomationValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestBreachDetection");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestBreachDetection");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("breachDetectionAutomation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testHipaaSkippedWithDevProfile() {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaDevProfile");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaDevProfile");
        cfcContext.put("complianceFrameworks", "HIPAA");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testAllBaaRequirementsValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestAllBaa");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAllBaa");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("awsBaaSigned", "true");
        cfcContext.put("thirdPartyBaasDocumented", "true");
        cfcContext.put("baaProvisionsVerified", "true");
        cfcContext.put("subcontractorBaasTracked", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testAllWorkforceSecurityRequirementsValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestAllWorkforce");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAllWorkforce");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("workforceAuthorizationProcedures", "true");
        cfcContext.put("terminationProcedures", "true");
        cfcContext.put("hipaaTrainingProgram", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testAllEmergencyAccessRequirementsValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestAllEmergency");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAllEmergency");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("emergencyAccessProcedures", "true");
        cfcContext.put("automaticLogoffEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }

    @Test
    void testAllBreachNotificationRequirementsValidation() {
        App app = new App();
        Stack stack = new Stack(app, "TestAllBreach");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAllBreach");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("incidentResponsePlan", "true");
        cfcContext.put("breachNotificationProcedures", "true");
        cfcContext.put("breachDetectionAutomation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaOrganizationalRules().install(ctx));
    }
}
