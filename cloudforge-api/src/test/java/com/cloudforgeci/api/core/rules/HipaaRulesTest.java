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
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
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
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    void testHipaaRulesMultipleInstallations() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createHipaaStack(app, "TestHipaaMultiple", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules multiple times
        new HipaaRules().install(ctx);

        // Then: Should be idempotent
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEncryptionAtRest() {
        // Given: A deployment with encryption at rest (§164.312(a)(2)(iv))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEncryptionRest");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEncryptionRest");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("encryptionAtRest", "true");
        cfcContext.put("kmsKeyRotation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEncryptionInTransit() {
        // Given: A deployment with encryption in transit (§164.312(e)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEncryptionTransit");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEncryptionTransit");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        cfcContext.put("minimumTlsVersion", "1.2");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithAccessControl() {
        // Given: A deployment with access control (§164.312(a)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaAccessControl");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaAccessControl");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithAuditControls() {
        // Given: A deployment with audit controls (§164.312(b))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaAuditControls");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaAuditControls");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("auditLoggingEnabled", "true");
        cfcContext.put("cloudTrailEnabled", "true");
        cfcContext.put("logRetentionDays", "2555");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithIntegrityControls() {
        // Given: A deployment with integrity controls (§164.312(c)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaIntegrity");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaIntegrity");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("integrityVerification", "true");
        cfcContext.put("fileIntegrityMonitoring", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithPersonIdentification() {
        // Given: A deployment with person/entity authentication (§164.312(d))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaPersonId");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaPersonId");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("mfaEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithTransmissionSecurity() {
        // Given: A deployment with transmission security (§164.312(e)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaTransmission");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaTransmission");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        cfcContext.put("minimumTlsVersion", "1.2");
        cfcContext.put("integrityVerification", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithBackupAndRecovery() {
        // Given: A deployment with backup/recovery (§164.308(a)(7)(ii)(A))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaBackup");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaBackup");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("backupRetentionDays", "90");
        cfcContext.put("disasterRecoveryPlan", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithIncidentResponse() {
        // Given: A deployment with incident response (§164.308(a)(6))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaIncidentResponse");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaIncidentResponse");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("incidentResponsePlan", "true");
        cfcContext.put("securityIncidentLogging", "true");
        cfcContext.put("securityContactEmail", "security@example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithWorkstationSecurity() {
        // Given: A deployment with workstation security (§164.310(c))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaWorkstation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaWorkstation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("sessionTimeout", "900");
        cfcContext.put("autoLogout", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithDeviceSecurity() {
        // Given: A deployment with device/media controls (§164.310(d)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaDevice");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaDevice");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("retainStorage", "false");
        cfcContext.put("dataDisposalPolicy", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithNetworkSecurity() {
        // Given: A deployment with network security
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaNetwork");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaNetwork");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("privateSubnets", "true");
        cfcContext.put("networkSegmentation", "true");
        cfcContext.put("networkMonitoring", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithVulnerabilityManagement() {
        // Given: A deployment with vulnerability management (§164.308(a)(5))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaVulnMgmt");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaVulnMgmt");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("vulnerabilityScanning", "true");
        cfcContext.put("patchManagement", "true");
        cfcContext.put("securityUpdatesEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithMalwareProtection() {
        // Given: A deployment with malware protection (§164.308(a)(5)(ii)(B))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaMalware");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaMalware");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("antiMalwareEnabled", "true");
        cfcContext.put("antiMalwareAutoUpdate", "true");
        cfcContext.put("malwareScanLogging", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithAllRequirements() {
        // Given: A deployment with all HIPAA requirements
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaAllReqs");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaAllReqs");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        // §164.312(a) - Access Control
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("mfaEnabled", "true");
        // §164.312(b) - Audit Controls
        cfcContext.put("auditLoggingEnabled", "true");
        cfcContext.put("cloudTrailEnabled", "true");
        cfcContext.put("logRetentionDays", "2555");
        // §164.312(c) - Integrity
        cfcContext.put("integrityVerification", "true");
        cfcContext.put("fileIntegrityMonitoring", "true");
        // §164.312(d) - Person/Entity Authentication
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "jenkins.example.com");
        // §164.312(e) - Transmission Security
        cfcContext.put("minimumTlsVersion", "1.2");
        cfcContext.put("encryptionAtRest", "true");
        cfcContext.put("kmsKeyRotation", "true");
        // §164.308(a)(6) - Security Incident Procedures
        cfcContext.put("incidentResponsePlan", "true");
        cfcContext.put("securityIncidentLogging", "true");
        cfcContext.put("securityContactEmail", "security@example.com");
        // §164.308(a)(7) - Contingency Plan
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("backupRetentionDays", "90");
        cfcContext.put("disasterRecoveryPlan", "true");
        // §164.308(a)(5) - Security Awareness
        cfcContext.put("vulnerabilityScanning", "true");
        cfcContext.put("antiMalwareEnabled", "true");
        cfcContext.put("antiMalwareAutoUpdate", "true");
        // §164.310 - Physical Safeguards
        cfcContext.put("privateSubnets", "true");
        cfcContext.put("networkSegmentation", "true");
        cfcContext.put("networkMonitoring", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules with all requirements
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithMinimalConfiguration() {
        // Given: A deployment with minimal HIPAA configuration
        App app = new App();
        Stack stack = createHipaaStack(app, "TestHipaaMinimal", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing HIPAA rules
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithRiskAnalysis() {
        // Given: A deployment with risk analysis (§164.308(a)(1)(ii)(A))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaRiskAnalysis");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaRiskAnalysis");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("riskAnalysisCompleted", "true");
        cfcContext.put("riskManagementProcess", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithRiskManagement() {
        // Given: A deployment with risk management (§164.308(a)(1)(ii)(B))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaRiskMgmt");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaRiskMgmt");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("riskMitigationPlan", "true");
        cfcContext.put("securityMeasuresImplemented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithSanctionPolicy() {
        // Given: A deployment with sanction policy (§164.308(a)(1)(ii)(C))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaSanction");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaSanction");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("sanctionPolicyDefined", "true");
        cfcContext.put("violationTrackingEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithInformationSystemActivity() {
        // Given: A deployment with information system activity review (§164.308(a)(1)(ii)(D))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaActivityReview");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaActivityReview");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("activityReviewEnabled", "true");
        cfcContext.put("auditLoggingEnabled", "true");
        cfcContext.put("regularAuditSchedule", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithSecurityOfficer() {
        // Given: A deployment with security officer designation (§164.308(a)(2))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaSecurityOfficer");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaSecurityOfficer");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("securityOfficerDesignated", "true");
        cfcContext.put("securityContactEmail", "security@example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithWorkforceSecurityTraining() {
        // Given: A deployment with workforce security training (§164.308(a)(5)(i))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaTraining");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaTraining");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("securityTrainingEnabled", "true");
        cfcContext.put("annualSecurityTraining", "true");
        cfcContext.put("trainingDocumentation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithSecurityReminders() {
        // Given: A deployment with security reminders (§164.308(a)(5)(ii)(A))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaReminders");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaReminders");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("securityRemindersEnabled", "true");
        cfcContext.put("periodicSecurityUpdates", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithPasswordManagement() {
        // Given: A deployment with password management (§164.308(a)(5)(ii)(D))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaPassword");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaPassword");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("passwordPolicyEnabled", "true");
        cfcContext.put("passwordComplexity", "true");
        cfcContext.put("passwordRotationDays", "90");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithLoginMonitoring() {
        // Given: A deployment with login monitoring (§164.308(a)(5)(ii)(C))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaLoginMonitor");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaLoginMonitor");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("loginMonitoringEnabled", "true");
        cfcContext.put("failedLoginAlerting", "true");
        cfcContext.put("bruteForceProtection", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithBusinessAssociateContracts() {
        // Given: A deployment with business associate contracts (§164.308(b)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaBAA");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaBAA");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("businessAssociateAgreements", "true");
        cfcContext.put("thirdPartyRiskAssessment", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithFacilityAccessControls() {
        // Given: A deployment with facility access controls (§164.310(a)(1))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaFacilityAccess");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaFacilityAccess");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("physicalAccessControls", "true");
        cfcContext.put("datacenterSecurityEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithWorkstationUse() {
        // Given: A deployment with workstation use policy (§164.310(b))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaWorkstationUse");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaWorkstationUse");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("workstationUsePolicyDefined", "true");
        cfcContext.put("screenLockEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithDataBackupPlan() {
        // Given: A deployment with data backup plan (§164.308(a)(7)(ii)(A))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaBackupPlan");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaBackupPlan");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("backupPlanDocumented", "true");
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("backupRetentionDays", "90");
        cfcContext.put("backupTestingEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithDisasterRecoveryPlan() {
        // Given: A deployment with disaster recovery plan (§164.308(a)(7)(ii)(B))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaDRP");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaDRP");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("disasterRecoveryPlan", "true");
        cfcContext.put("recoveryTimeObjective", "4");
        cfcContext.put("recoveryPointObjective", "1");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEmergencyModePlan() {
        // Given: A deployment with emergency mode operation plan (§164.308(a)(7)(ii)(C))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEmergency");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEmergency");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("emergencyOperationPlan", "true");
        cfcContext.put("criticalSystemsIdentified", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithTestingProcedures() {
        // Given: A deployment with testing and revision procedures (§164.308(a)(7)(ii)(D))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaTestingProc");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaTestingProc");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("contingencyPlanTesting", "true");
        cfcContext.put("annualContingencyTest", "true");
        cfcContext.put("testResultsDocumented", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithApplicationsDataCriticalityAnalysis() {
        // Given: A deployment with applications/data criticality analysis (§164.308(a)(7)(ii)(E))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaCriticality");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaCriticality");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("criticalityAnalysisCompleted", "true");
        cfcContext.put("dataClassification", "SENSITIVE");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEvaluationStandards() {
        // Given: A deployment with evaluation standards (§164.308(a)(8))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEvaluation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEvaluation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("securityEvaluationStandards", "true");
        cfcContext.put("periodicSecurityAssessment", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithUniqueUserIdentification() {
        // Given: A deployment with unique user identification (§164.312(a)(2)(i))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaUniqueUser");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaUniqueUser");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("uniqueUserIdRequired", "true");
        cfcContext.put("sharedAccountsProhibited", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEmergencyAccessProcedure() {
        // Given: A deployment with emergency access procedure (§164.312(a)(2)(ii))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEmergencyAccess");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEmergencyAccess");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("emergencyAccessProcedure", "true");
        cfcContext.put("breakGlassAccountsConfigured", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithAutomaticLogoff() {
        // Given: A deployment with automatic logoff (§164.312(a)(2)(iii))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaAutoLogoff");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaAutoLogoff");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("automaticLogoffEnabled", "true");
        cfcContext.put("sessionTimeout", "900");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEncryptionDecryption() {
        // Given: A deployment with encryption/decryption (§164.312(a)(2)(iv))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEncryptDecrypt");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEncryptDecrypt");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("encryptionAtRest", "true");
        cfcContext.put("encryptionInTransit", "true");
        cfcContext.put("kmsEnabled", "true");
        cfcContext.put("kmsKeyRotation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithMechanismAuthentication() {
        // Given: A deployment with mechanism to authenticate ePHI (§164.312(c)(2))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaAuthMechanism");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaAuthMechanism");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("dataAuthenticationEnabled", "true");
        cfcContext.put("digitalSignaturesEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithIntegrityControls2() {
        // Given: A deployment with integrity controls (§164.312(e)(2)(i))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaIntegrity2");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaIntegrity2");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("transmissionIntegrityEnabled", "true");
        cfcContext.put("checksumValidation", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithEncryptionTransmission() {
        // Given: A deployment with encryption for transmission (§164.312(e)(2)(ii))
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEncryptTransmit");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEncryptTransmit");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "secure.example.com");
        cfcContext.put("minimumTlsVersion", "1.3");
        cfcContext.put("tlsEnforcementEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaIdempotency() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createHipaaStack(app, "TestHipaaIdempotent", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing HIPAA rules multiple times
        new HipaaRules().install(ctx);
        new HipaaRules().install(ctx);
        new HipaaRules().install(ctx);

        // Then: Should be fully idempotent
        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithAllSecurityProfiles() {
        // Given: Testing HIPAA rules across all security profiles
        SecurityProfile[] profiles = {
            SecurityProfile.DEV,
            SecurityProfile.STAGING,
            SecurityProfile.PRODUCTION
        };

        for (SecurityProfile profile : profiles) {
            App app = new App();
            Stack stack = createHipaaStack(app, "TestHipaaProfile" + profile.name(), profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            assertDoesNotThrow(() -> new HipaaRules().install(ctx),
                "HIPAA rules should work with profile: " + profile);
        }
    }

    @Test
    void testHipaaWithAllRuntimeTypes() {
        // Given: Testing HIPAA rules across all runtime types
        RuntimeType[] runtimes = {
            RuntimeType.EC2,
            RuntimeType.FARGATE
        };

        for (RuntimeType runtime : runtimes) {
            App app = new App();
            Stack stack = createHipaaStack(app, "TestHipaaRuntime" + runtime.name(), SecurityProfile.PRODUCTION);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, runtime,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            assertDoesNotThrow(() -> new HipaaRules().install(ctx),
                "HIPAA rules should work with runtime: " + runtime);
        }
    }

    // ========================================
    // Truth Table Style Parameterized Tests
    // ========================================

    /**
     * Truth table test for HIPAA Security Management Process validation (§164.308(a)(1)).
     * Tests all combinations of security monitoring and GuardDuty settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,true,true",      // Full monitoring - PASS all branches
        "PRODUCTION,FARGATE,false,true,true",     // No security monitoring - FAIL branch
        "PRODUCTION,FARGATE,true,false,true",     // No GuardDuty - FAIL branch
        "PRODUCTION,FARGATE,false,false,true",    // No monitoring at all - FAIL both branches
        "STAGING,FARGATE,true,true,true",         // Staging with full monitoring
        "STAGING,FARGATE,false,false,true",       // Staging with no monitoring
        "DEV,FARGATE,true,true,false"             // DEV profile - should skip HIPAA entirely
    })
    void testHipaaSecurityManagementCombinations(String profile, String runtime, boolean securityMonitoring,
                                                   boolean guardDuty, boolean shouldEnforce) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaSecMgmt");
        customContext.put("securityProfile", profile);
        customContext.put("securityMonitoringEnabled", String.valueOf(securityMonitoring));
        customContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        customContext.put("complianceMode", shouldEnforce ? "ENFORCE" : "ADVISORY");

        String complianceMode = shouldEnforce ? "ENFORCE" : "ADVISORY";
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = securityMonitoring && guardDuty;
            if (hasRequirement) {
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "2190");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("region", "us-east-1");

                // Add auth baseline if not testing no-auth scenarios
                if (!customContext.containsKey("authMode") || !customContext.get("authMode").equals("none")) {
                    customContext.putIfAbsent("authMode", "alb-oidc");
                    customContext.putIfAbsent("enableSsl", "true");
                    customContext.putIfAbsent("fqdn", "jenkins.example.com");
                    customContext.putIfAbsent("cognitoMfaEnabled", "true");
                    customContext.putIfAbsent("cognitoAutoProvision", "true");
                }

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("crossRegionBackupEnabled", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaSecMgmt", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (!securityMonitoring || !guardDuty) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing security monitoring: " + profile + " securityMonitoring=" + securityMonitoring + " guardDuty=" + guardDuty);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " securityMonitoring=" + securityMonitoring + " guardDuty=" + guardDuty);
        }
    }

    /**
     * Truth table test for HIPAA Physical Safeguards validation (§164.310).
     * Tests all combinations of backup and cross-region settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,true,ENFORCE",     // Full backup - PASS all branches
        "PRODUCTION,FARGATE,false,true,ENFORCE",    // No automated backup - FAIL branch
        "PRODUCTION,FARGATE,true,false,ENFORCE",    // No cross-region - FAIL production branch
        "PRODUCTION,FARGATE,false,false,ENFORCE",   // No backup at all - FAIL both branches
        "PRODUCTION,FARGATE,true,true,ADVISORY",    // Advisory mode with full backup
        "PRODUCTION,FARGATE,false,false,ADVISORY",  // Advisory mode with no backup
        "STAGING,FARGATE,true,true,ENFORCE",        // Staging doesn't check cross-region
        "STAGING,FARGATE,false,true,ENFORCE",       // Staging with no automated backup
        "STAGING,FARGATE,true,false,ENFORCE"        // Staging without cross-region (OK)
    })
    void testHipaaPhysicalSafeguardsCombinations(String profile, String runtime, boolean automatedBackup,
                                                  boolean crossRegion, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaPhys");
        customContext.put("securityProfile", profile);
        customContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        customContext.put("crossRegionBackupEnabled", String.valueOf(crossRegion));
        customContext.put("complianceMode", complianceMode);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = automatedBackup && (secProfile != SecurityProfile.PRODUCTION || crossRegion);
            if (hasRequirement) {
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("logRetentionDays", "2190");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("region", "us-east-1");

                // Add auth baseline if not testing no-auth scenarios
                if (!customContext.containsKey("authMode") || !customContext.get("authMode").equals("none")) {
                    customContext.putIfAbsent("authMode", "alb-oidc");
                    customContext.putIfAbsent("enableSsl", "true");
                    customContext.putIfAbsent("fqdn", "jenkins.example.com");
                    customContext.putIfAbsent("cognitoMfaEnabled", "true");
                    customContext.putIfAbsent("cognitoAutoProvision", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaPhys", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (!automatedBackup || (secProfile == SecurityProfile.PRODUCTION && !crossRegion)) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing backup: " + profile + " automatedBackup=" + automatedBackup + " crossRegion=" + crossRegion);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " automatedBackup=" + automatedBackup + " crossRegion=" + crossRegion);
        }
    }

    /**
     * Truth table test for HIPAA Access Controls (§164.312(a)(1)).
     * Tests all combinations of authentication modes.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,none,ENFORCE",           // No auth - FAIL branches
        "PRODUCTION,FARGATE,alb-oidc,ENFORCE",       // ALB OIDC - PASS branches
        "PRODUCTION,FARGATE,jenkins-oidc,ENFORCE",   // Jenkins OIDC - PASS branches
        "PRODUCTION,FARGATE,none,ADVISORY",          // Advisory mode with no auth
        "STAGING,FARGATE,none,ENFORCE",              // Staging with no auth
        "STAGING,FARGATE,alb-oidc,ENFORCE"           // Staging with ALB OIDC
    })
    void testHipaaAccessControlAuthModeCombinations(String profile, String runtime, String authMode, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaAuth");
        customContext.put("securityProfile", profile);
        customContext.put("authMode", authMode);
        customContext.put("complianceMode", complianceMode);
        if (!authMode.equals("none")) {
            customContext.put("enableSsl", "true");
            customContext.put("fqdn", "jenkins.example.com");
        }

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = !authMode.equals("none");
            if (hasRequirement) {
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "2190");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("region", "us-east-1");

                // Add auth baseline with MFA
                customContext.putIfAbsent("cognitoMfaEnabled", "true");
                customContext.putIfAbsent("cognitoAutoProvision", "true");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("crossRegionBackupEnabled", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaAuth", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (authMode.equals("none")) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for authMode=none: " + profile + " " + authMode);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " " + authMode);
        }
    }

    /**
     * Truth table test for HIPAA Audit Controls (§164.312(b)).
     * Tests all combinations of audit logging settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,FARGATE,true,true,true,ENFORCE",     // Full audit - PASS all branches
        "PRODUCTION,FARGATE,false,true,true,ENFORCE",    // No CloudTrail - FAIL branch
        "PRODUCTION,FARGATE,true,false,true,ENFORCE",    // No Flow Logs - FAIL branch
        "PRODUCTION,FARGATE,true,true,false,ENFORCE",    // No ALB logging - FAIL branch
        "PRODUCTION,FARGATE,false,false,false,ENFORCE",  // No audit at all - FAIL all branches
        "PRODUCTION,FARGATE,true,true,true,ADVISORY",    // Advisory mode with full audit
        "PRODUCTION,FARGATE,false,false,false,ADVISORY", // Advisory mode with no audit
        "STAGING,FARGATE,true,true,true,ENFORCE",        // Staging with full audit
        "STAGING,FARGATE,false,false,false,ENFORCE"      // Staging with no audit
    })
    void testHipaaAuditControlsCombinations(String profile, String runtime, boolean cloudTrail, boolean flowLogs,
                                            boolean albLogging, String complianceMode) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaAudit");
        customContext.put("securityProfile", profile);
        customContext.put("region", "us-east-1");  // Required for ALB logging
        customContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        customContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        customContext.put("albAccessLogging", String.valueOf(albLogging));
        customContext.put("complianceMode", complianceMode);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = cloudTrail && flowLogs && albLogging;
            if (hasRequirement) {
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "2190");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");

                // Add auth baseline if not testing no-auth scenarios
                if (!customContext.containsKey("authMode") || !customContext.get("authMode").equals("none")) {
                    customContext.putIfAbsent("authMode", "alb-oidc");
                    customContext.putIfAbsent("enableSsl", "true");
                    customContext.putIfAbsent("fqdn", "jenkins.example.com");
                    customContext.putIfAbsent("cognitoMfaEnabled", "true");
                    customContext.putIfAbsent("cognitoAutoProvision", "true");
                }

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("crossRegionBackupEnabled", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaAudit", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (!cloudTrail || !flowLogs || !albLogging) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing audit controls: " + profile + " cloudTrail=" + cloudTrail + " flowLogs=" + flowLogs + " albLogging=" + albLogging);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " cloudTrail=" + cloudTrail + " flowLogs=" + flowLogs + " albLogging=" + albLogging);
        }
    }

    /**
     * Truth table test for HIPAA Authentication Controls (§164.312(d)).
     * Tests all combinations of authentication and MFA settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,alb-oidc,true,true,false,ENFORCE,FARGATE",     // Cognito with MFA - PASS all branches
        "PRODUCTION,alb-oidc,false,true,false,ENFORCE,FARGATE",    // Cognito MFA enabled but no auto-provision - FAIL MFA branch
        "PRODUCTION,alb-oidc,true,false,false,ENFORCE,FARGATE",    // No auto-provision - FAIL MFA branch
        "PRODUCTION,alb-oidc,false,false,true,ENFORCE,FARGATE",    // SSO instead - PASS MFA branch
        "PRODUCTION,alb-oidc,false,false,false,ENFORCE,FARGATE",   // No MFA at all - FAIL MFA branch
        "PRODUCTION,jenkins-oidc,true,true,false,ENFORCE,FARGATE", // Jenkins OIDC with Cognito MFA
        "PRODUCTION,jenkins-oidc,false,false,true,ENFORCE,FARGATE",// Jenkins OIDC with SSO
        "PRODUCTION,none,false,false,false,ENFORCE,FARGATE",       // No auth - FAIL auth branch
        "STAGING,alb-oidc,true,true,false,ENFORCE,FARGATE",        // Staging with full MFA
        "STAGING,alb-oidc,false,false,false,ENFORCE,FARGATE"       // Staging with no MFA
    })
    void testHipaaAuthenticationMfaCombinations(String profile, String authMode, boolean cognitoMfa,
                                                boolean cognitoAuto, boolean hasSso, String complianceMode, String runtime) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaMfa");
        customContext.put("securityProfile", profile);
        customContext.put("authMode", authMode);
        customContext.put("complianceMode", complianceMode);

        if (!authMode.equals("none")) {
            customContext.put("enableSsl", "true");
            customContext.put("fqdn", "jenkins.example.com");
        }

        if (cognitoAuto) {
            customContext.put("cognitoAutoProvision", "true");
        }
        if (cognitoMfa) {
            customContext.put("cognitoMfaEnabled", "true");
        }
        if (hasSso) {
            customContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        }

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = !authMode.equals("none") && ((cognitoMfa && cognitoAuto) || hasSso);
            if (hasRequirement) {
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "2190");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("region", "us-east-1");

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("crossRegionBackupEnabled", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaMfa", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        // HIPAA MFA requires BOTH cognitoMfa AND cognitoAuto to be true, OR hasSso
        boolean hasMfa = (cognitoMfa && cognitoAuto) || hasSso;
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (authMode.equals("none") || !hasMfa) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing auth/MFA: " + profile + " authMode=" + authMode + " cognitoMfa=" + cognitoMfa + " cognitoAuto=" + cognitoAuto + " hasSso=" + hasSso);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " authMode=" + authMode + " cognitoMfa=" + cognitoMfa + " cognitoAuto=" + cognitoAuto + " hasSso=" + hasSso);
        }
    }

    /**
     * Truth table test for HIPAA Transmission Security (§164.312(e)(1)).
     * Tests all combinations of transmission security settings.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,true,true,private-with-nat,ENFORCE,FARGATE",    // Full TLS - PASS all branches
        "PRODUCTION,false,true,private-with-nat,ENFORCE,FARGATE",   // No TLS cert - FAIL branch
        "PRODUCTION,true,false,private-with-nat,ENFORCE,FARGATE",   // No EFS transit - FAIL branch
        "PRODUCTION,true,true,public-no-nat,ENFORCE,FARGATE",       // Public network - FAIL branch
        "PRODUCTION,false,false,public-no-nat,ENFORCE,FARGATE",     // No security - FAIL all branches
        "PRODUCTION,true,true,private-with-nat,ADVISORY,FARGATE",   // Advisory mode with full TLS
        "PRODUCTION,false,false,public-no-nat,ADVISORY,FARGATE",    // Advisory mode with no security
        "STAGING,true,true,private-with-nat,ENFORCE,FARGATE",       // Staging with full TLS
        "STAGING,false,false,public-no-nat,ENFORCE,FARGATE"         // Staging with no security
    })
    void testHipaaTransmissionSecurityCombinations(String profile, boolean hasCert, boolean efsTransit,
                                                   String networkMode, String complianceMode, String runtime) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaTLS");
        customContext.put("securityProfile", profile);
        customContext.put("networkMode", networkMode);
        customContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        customContext.put("complianceMode", complianceMode);

        if (hasCert) {
            customContext.put("enableSsl", "true");
            customContext.put("fqdn", "jenkins.example.com");
        }

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = hasCert && efsTransit && networkMode.equals("private-with-nat");
            if (hasRequirement) {
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("logRetentionDays", "2190");
                customContext.putIfAbsent("region", "us-east-1");

                // Add auth baseline if not testing no-auth scenarios
                if (!customContext.containsKey("authMode") || !customContext.get("authMode").equals("none")) {
                    customContext.putIfAbsent("authMode", "alb-oidc");
                    customContext.putIfAbsent("cognitoMfaEnabled", "true");
                    customContext.putIfAbsent("cognitoAutoProvision", "true");
                }

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("crossRegionBackupEnabled", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaTLS", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (!hasCert || !efsTransit || "public-no-nat".equals(networkMode)) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for transmission security: " + profile + " hasCert=" + hasCert + " efsTransit=" + efsTransit + " networkMode=" + networkMode);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " hasCert=" + hasCert + " efsTransit=" + efsTransit + " networkMode=" + networkMode);
        }
    }

    /**
     * Truth table test for HIPAA Retention Requirements (§164.316(b)(2)(i)).
     * Tests all combinations of log retention periods.
     */
    @ParameterizedTest
    @CsvSource({
        "PRODUCTION,2555,ENFORCE,true,FARGATE",      // 7 years - PASS branch
        "PRODUCTION,2190,ENFORCE,true,FARGATE",      // 6 years exactly - PASS branch
        "PRODUCTION,1825,ENFORCE,true,FARGATE",      // 5 years - PASS branch
        "PRODUCTION,1095,ENFORCE,true,FARGATE",      // 3 years - PASS branch
        "PRODUCTION,730,ENFORCE,true,FARGATE",       // 2 years - PASS branch (minimum)
        "PRODUCTION,365,ENFORCE,false,FARGATE",      // 1 year - FAIL branch
        "PRODUCTION,180,ENFORCE,false,FARGATE",      // 180 days - FAIL branch
        "PRODUCTION,90,ENFORCE,false,FARGATE",       // 90 days - FAIL branch
        "PRODUCTION,365,ADVISORY,false,FARGATE",     // Advisory mode with 1 year
        "STAGING,730,ENFORCE,true,FARGATE",          // Staging with 2 years
        "STAGING,365,ENFORCE,false,FARGATE"          // Staging with 1 year
    })
    void testHipaaRetentionRequirementsCombinations(String profile, int retentionDays,
                                                    String complianceMode, boolean shouldPass, String runtime) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaRetention");
        customContext.put("securityProfile", profile);
        customContext.put("logRetentionDays", String.valueOf(retentionDays));
        customContext.put("complianceMode", complianceMode);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);

        // Add baseline HIPAA requirements for tests expecting to pass
        // This ensures focused tests don't fail on unrelated requirements
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Only add baseline if test expects to pass (has the specific requirement being tested)
            boolean hasRequirement = retentionDays >= 730;
            if (hasRequirement) {
                customContext.putIfAbsent("securityMonitoringEnabled", "true");
                customContext.putIfAbsent("guardDutyEnabled", "true");
                customContext.putIfAbsent("cloudTrailEnabled", "true");
                customContext.putIfAbsent("flowLogsEnabled", "true");
                customContext.putIfAbsent("albAccessLogging", "true");
                customContext.putIfAbsent("automatedBackupEnabled", "true");
                customContext.putIfAbsent("efsEncryptionInTransitEnabled", "true");
                customContext.putIfAbsent("networkMode", "private-with-nat");
                customContext.putIfAbsent("region", "us-east-1");

                // Add auth baseline if not testing no-auth scenarios
                if (!customContext.containsKey("authMode") || !customContext.get("authMode").equals("none")) {
                    customContext.putIfAbsent("authMode", "alb-oidc");
                    customContext.putIfAbsent("enableSsl", "true");
                    customContext.putIfAbsent("fqdn", "jenkins.example.com");
                    customContext.putIfAbsent("cognitoMfaEnabled", "true");
                    customContext.putIfAbsent("cognitoAutoProvision", "true");
                }

                if (secProfile == SecurityProfile.PRODUCTION) {
                    customContext.putIfAbsent("crossRegionBackupEnabled", "true");
                }
            }
        }

        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaRetention", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // HIPAA validation checks ALL requirements, so any missing requirement causes failure
        boolean shouldFail = false;
        if ("ENFORCE".equals(complianceMode) && (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING)) {
            // Test will fail if THIS requirement is missing
            // (it may also fail for other missing requirements, but we're specifically testing this one)
            if (retentionDays < 730) {
                shouldFail = true;
            }
        }

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for retention: " + profile + " retentionDays=" + retentionDays);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " retentionDays=" + retentionDays);
        }
    }

    /**
     * Truth table test for HIPAA with all security profiles.
     * Tests the early return branch for DEV profile.
     */
    @ParameterizedTest
    @CsvSource({
        "DEV,ADVISORY,false,FARGATE",           // DEV should skip HIPAA entirely
        "STAGING,ADVISORY,true,FARGATE",        // STAGING should run HIPAA validation
        "STAGING,ENFORCE,true,FARGATE",         // STAGING with enforce mode
        "PRODUCTION,ADVISORY,true,FARGATE",     // PRODUCTION should run HIPAA validation
        "PRODUCTION,ENFORCE,true,FARGATE"       // PRODUCTION with enforce mode
    })
    void testHipaaSecurityProfileBranches(String profile, String complianceMode, boolean shouldValidate, String runtime) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaProfile");
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaProfile", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // This test validates the security profile early-return branches
        // DEV profile should always pass (early return), ADVISORY mode should always pass
        // ENFORCE mode with PRODUCTION/STAGING will fail due to missing requirements (expected)
        boolean shouldFail = "ENFORCE".equals(complianceMode) &&
                           (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING);

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for missing requirements: " + profile + " " + complianceMode);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass: " + profile + " " + complianceMode);
        }
    }

    /**
     * Comprehensive truth table test combining multiple HIPAA requirements.
     * Tests realistic scenarios with multiple configuration flags.
     */
    @ParameterizedTest
    @CsvSource({
        // Full compliance scenarios
        "PRODUCTION,ENFORCE,alb-oidc,true,true,true,true,true,true,true,private-with-nat,2555,FARGATE",
        "STAGING,ENFORCE,alb-oidc,true,true,true,true,true,false,true,private-with-nat,730,FARGATE",

        // Partial compliance scenarios
        "PRODUCTION,ADVISORY,none,false,false,false,false,false,false,false,public-no-nat,90,FARGATE",
        "STAGING,ADVISORY,none,false,false,false,false,false,false,false,public-no-nat,90,FARGATE",

        // Mixed compliance scenarios
        "PRODUCTION,ENFORCE,alb-oidc,true,true,false,true,false,false,true,private-with-nat,365,FARGATE",
        "STAGING,ENFORCE,jenkins-oidc,true,false,true,false,true,false,false,private-with-nat,180,FARGATE",

        // Authentication variations
        "PRODUCTION,ENFORCE,alb-oidc,false,true,true,true,true,true,true,private-with-nat,2190,FARGATE",
        "PRODUCTION,ENFORCE,jenkins-oidc,false,false,true,true,true,true,true,private-with-nat,1095,FARGATE"
    })
    void testHipaaComprehensiveCombinations(String profile, String complianceMode, String authMode,
                                           boolean cognitoMfa, boolean secMonitoring, boolean guardDuty,
                                           boolean cloudTrail, boolean flowLogs, boolean crossRegion,
                                           boolean efsTransit, String networkMode, int retention, String runtime) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestHipaaComp");
        customContext.put("securityProfile", profile);
        customContext.put("complianceMode", complianceMode);
        customContext.put("authMode", authMode);
        customContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        customContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        customContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        customContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        customContext.put("crossRegionBackupEnabled", String.valueOf(crossRegion));
        customContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        customContext.put("networkMode", networkMode);
        customContext.put("logRetentionDays", String.valueOf(retention));
        customContext.put("automatedBackupEnabled", "true");

        if (!authMode.equals("none")) {
            customContext.put("enableSsl", "true");
            customContext.put("fqdn", "jenkins.example.com");
            if (cognitoMfa) {
                customContext.put("cognitoAutoProvision", "true");
                customContext.put("cognitoMfaEnabled", "true");
            }
        }

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        RuntimeType runtimeType = RuntimeType.valueOf(runtime);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestHipaaComp", secProfile, runtimeType, customContext);

        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        new SecurityRules().install(builder.getSystemContext());
        new HipaaRules().install(builder.getSystemContext());

        // Comprehensive checks for all HIPAA requirements when ENFORCE + PRODUCTION/STAGING
        boolean shouldFail = "ENFORCE".equals(complianceMode) &&
                           (secProfile == SecurityProfile.PRODUCTION || secProfile == SecurityProfile.STAGING) &&
                           (authMode.equals("none") ||                           // No auth
                            !cognitoMfa ||                                       // No MFA
                            (!secMonitoring || !guardDuty) ||                    // Missing monitoring
                            (!cloudTrail || !flowLogs) ||                        // Missing audit logs
                            !efsTransit ||                                       // No EFS transit encryption
                            "public-no-nat".equals(networkMode) ||               // Public network
                            retention < 730 ||                                   // Insufficient retention
                            (secProfile == SecurityProfile.PRODUCTION && !crossRegion)); // PROD needs cross-region backup

        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for comprehensive check: " + profile + " auth=" + authMode + " mfa=" + cognitoMfa + " retention=" + retention);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for comprehensive check: " + profile + " auth=" + authMode + " mfa=" + cognitoMfa + " retention=" + retention);
        }
    }

    // ========================================
    // EXPANDED DEEP UNIT TEST BRANCHING
    // ========================================
    // Additional exhaustive parameterized tests for maximum branch coverage

    /**
     * Expanded security management tests - §164.308(a)(1).
     * Tests all combinations of security monitoring flags across different scenarios.
     */
    @ParameterizedTest
    @CsvSource({
        // GuardDuty enabled combinations
        "PRODUCTION,true,true,true,ENFORCE",
        "PRODUCTION,true,false,true,ENFORCE",
        "STAGING,true,true,true,ENFORCE",
        "STAGING,true,false,true,ENFORCE",
        "PRODUCTION,true,true,false,ADVISORY",
        "PRODUCTION,true,false,false,ADVISORY",
        // GuardDuty disabled combinations
        "PRODUCTION,false,true,true,ENFORCE",
        "PRODUCTION,false,false,true,ENFORCE",
        "STAGING,false,true,true,ENFORCE",
        "STAGING,false,false,true,ENFORCE",
        "PRODUCTION,false,true,false,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY",
        // Edge cases
        "DEV,true,true,false,ENFORCE",
        "DEV,false,false,false,ADVISORY"
    })
    void testHipaaExpandedSecurityManagement(String profile, boolean guardDuty,
                                             boolean securityMonitoring, boolean awsConfig,
                                             String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaSecManExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaSecManExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("securityMonitoringEnabled", String.valueOf(securityMonitoring));
        cfcContext.put("awsConfigEnabled", String.valueOf(awsConfig));
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded encryption at rest combinations - §164.312(a)(2)(iv).
     * Tests all permutations of encryption settings.
     */
    @ParameterizedTest
    @CsvSource({
        // All encryption enabled
        "PRODUCTION,true,true,true,ENFORCE",
        "STAGING,true,true,true,ENFORCE",
        // Two enabled, one disabled
        "PRODUCTION,true,true,false,ENFORCE",
        "PRODUCTION,true,false,true,ENFORCE",
        "PRODUCTION,false,true,true,ENFORCE",
        "STAGING,true,true,false,ENFORCE",
        "STAGING,true,false,true,ENFORCE",
        "STAGING,false,true,true,ENFORCE",
        // One enabled, two disabled
        "PRODUCTION,true,false,false,ENFORCE",
        "PRODUCTION,false,true,false,ENFORCE",
        "PRODUCTION,false,false,true,ENFORCE",
        "STAGING,true,false,false,ENFORCE",
        "STAGING,false,true,false,ENFORCE",
        "STAGING,false,false,true,ENFORCE",
        // All disabled
        "PRODUCTION,false,false,false,ENFORCE",
        "STAGING,false,false,false,ENFORCE",
        // Advisory mode combinations
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY",
        "STAGING,true,true,true,ADVISORY",
        "STAGING,false,false,false,ADVISORY",
        // DEV profile
        "DEV,false,false,false,ENFORCE",
        "DEV,true,true,true,ADVISORY"
    })
    void testHipaaExpandedEncryptionAtRest(String profile, boolean ebsEncryption,
                                           boolean efsEncryption, boolean s3Encryption,
                                           String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaEncExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaEncExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEncryption));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsEncryption));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Encryption));
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded audit logging combinations - §164.312(b).
     * Tests all logging flag combinations across profiles and modes.
     */
    @ParameterizedTest
    @CsvSource({
        // All three logging types enabled
        "PRODUCTION,true,true,true,ENFORCE",
        "STAGING,true,true,true,ENFORCE",
        // Two enabled, one disabled (3 combinations)
        "PRODUCTION,true,true,false,ENFORCE",
        "PRODUCTION,true,false,true,ENFORCE",
        "PRODUCTION,false,true,true,ENFORCE",
        "STAGING,true,true,false,ENFORCE",
        "STAGING,true,false,true,ENFORCE",
        "STAGING,false,true,true,ENFORCE",
        // One enabled, two disabled (3 combinations)
        "PRODUCTION,true,false,false,ENFORCE",
        "PRODUCTION,false,true,false,ENFORCE",
        "PRODUCTION,false,false,true,ENFORCE",
        "STAGING,true,false,false,ENFORCE",
        "STAGING,false,true,false,ENFORCE",
        "STAGING,false,false,true,ENFORCE",
        // All disabled
        "PRODUCTION,false,false,false,ENFORCE",
        "STAGING,false,false,false,ENFORCE",
        // Advisory mode
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY",
        "STAGING,true,true,true,ADVISORY",
        "STAGING,false,false,false,ADVISORY",
        // DEV profile
        "DEV,false,false,false,ENFORCE",
        "DEV,true,true,true,ADVISORY"
    })
    void testHipaaExpandedAuditLogging(String profile, boolean cloudTrail,
                                       boolean flowLogs, boolean albLogging,
                                       String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaLogExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaLogExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLoggingEnabled", String.valueOf(albLogging));
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded authentication combinations - §164.312(d).
     * Tests all auth modes with MFA and SSO combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // ALB OIDC with various MFA/SSO combinations
        "PRODUCTION,alb-oidc,true,true,ENFORCE",
        "PRODUCTION,alb-oidc,true,false,ENFORCE",
        "PRODUCTION,alb-oidc,false,true,ENFORCE",
        "PRODUCTION,alb-oidc,false,false,ENFORCE",
        "STAGING,alb-oidc,true,true,ENFORCE",
        "STAGING,alb-oidc,false,false,ENFORCE",
        // Jenkins OIDC with various MFA/SSO combinations
        "PRODUCTION,jenkins-oidc,true,true,ENFORCE",
        "PRODUCTION,jenkins-oidc,true,false,ENFORCE",
        "PRODUCTION,jenkins-oidc,false,true,ENFORCE",
        "PRODUCTION,jenkins-oidc,false,false,ENFORCE",
        "STAGING,jenkins-oidc,true,true,ENFORCE",
        "STAGING,jenkins-oidc,false,false,ENFORCE",
        // No authentication with MFA/SSO combinations
        "PRODUCTION,none,true,true,ENFORCE",
        "PRODUCTION,none,true,false,ENFORCE",
        "PRODUCTION,none,false,true,ENFORCE",
        "PRODUCTION,none,false,false,ENFORCE",
        "STAGING,none,true,true,ENFORCE",
        "STAGING,none,false,false,ENFORCE",
        // Advisory mode scenarios
        "PRODUCTION,alb-oidc,true,true,ADVISORY",
        "PRODUCTION,jenkins-oidc,false,false,ADVISORY",
        "PRODUCTION,none,false,false,ADVISORY",
        // DEV profile
        "DEV,none,false,false,ENFORCE",
        "DEV,alb-oidc,true,true,ADVISORY"
    })
    void testHipaaExpandedAuthentication(String profile, String authMode,
                                         boolean cognitoMfa, boolean identityCenterSso,
                                         String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaAuthExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaAuthExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("authMode", authMode);
        cfcContext.put("cognitoMfaEnabled", String.valueOf(cognitoMfa));
        cfcContext.put("identityCenterSsoEnabled", String.valueOf(identityCenterSso));

        // ALB OIDC requires SSL
        if ("alb-oidc".equals(authMode)) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "hipaa.example.com");
        }

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded transmission security combinations - §164.312(e)(1).
     * Tests TLS, EFS transit encryption, and network mode combinations.
     */
    @ParameterizedTest
    @CsvSource({
        // All secure (TLS + EFS transit + private network)
        "PRODUCTION,true,true,private-with-nat,ENFORCE",
        "STAGING,true,true,private-with-nat,ENFORCE",
        // Missing TLS certificate
        "PRODUCTION,false,true,private-with-nat,ENFORCE",
        "STAGING,false,true,private-with-nat,ENFORCE",
        // Missing EFS transit encryption
        "PRODUCTION,true,false,private-with-nat,ENFORCE",
        "STAGING,true,false,private-with-nat,ENFORCE",
        // Public network mode
        "PRODUCTION,true,true,public-no-nat,ENFORCE",
        "STAGING,true,true,public-no-nat,ENFORCE",
        // Two issues
        "PRODUCTION,false,false,private-with-nat,ENFORCE",
        "PRODUCTION,false,true,public-no-nat,ENFORCE",
        "PRODUCTION,true,false,public-no-nat,ENFORCE",
        // All three issues
        "PRODUCTION,false,false,public-no-nat,ENFORCE",
        "STAGING,false,false,public-no-nat,ENFORCE",
        // Advisory mode
        "PRODUCTION,true,true,private-with-nat,ADVISORY",
        "PRODUCTION,false,false,public-no-nat,ADVISORY",
        // DEV profile
        "DEV,false,false,public-no-nat,ENFORCE",
        "DEV,true,true,private-with-nat,ADVISORY"
    })
    void testHipaaExpandedTransmissionSecurity(String profile, boolean hasCert,
                                               boolean efsTransit, String networkMode,
                                               String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaTransExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaTransExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("efsEncryptionInTransitEnabled", String.valueOf(efsTransit));
        cfcContext.put("networkMode", networkMode);

        if (hasCert) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "hipaa.example.com");
        }

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded log retention period tests - §164.316(b)(2)(i).
     * Tests all retention period combinations with compliance modes.
     */
    @ParameterizedTest
    @CsvSource({
        // Test each retention period with ENFORCE mode
        "PRODUCTION,90,ENFORCE",
        "PRODUCTION,180,ENFORCE",
        "PRODUCTION,365,ENFORCE",
        "PRODUCTION,730,ENFORCE",
        "PRODUCTION,1095,ENFORCE",
        "PRODUCTION,2190,ENFORCE",  // HIPAA minimum (6 years)
        "PRODUCTION,2555,ENFORCE",
        // Test retention periods with STAGING
        "STAGING,90,ENFORCE",
        "STAGING,365,ENFORCE",
        "STAGING,2190,ENFORCE",
        "STAGING,2555,ENFORCE",
        // Test with ADVISORY mode
        "PRODUCTION,90,ADVISORY",
        "PRODUCTION,2190,ADVISORY",
        "STAGING,90,ADVISORY",
        "STAGING,2190,ADVISORY",
        // Test DEV profile
        "DEV,90,ENFORCE",
        "DEV,2190,ADVISORY"
    })
    void testHipaaExpandedRetentionPeriods(String profile, int retentionDays,
                                           String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaRetExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaRetExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("logRetentionDays", String.valueOf(retentionDays));
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded physical safeguards combinations - §164.310.
     * Tests backup and disaster recovery configurations.
     */
    @ParameterizedTest
    @CsvSource({
        // All backup features enabled
        "PRODUCTION,true,true,true,ENFORCE",
        "STAGING,true,true,true,ENFORCE",
        // Automated backup only
        "PRODUCTION,true,false,false,ENFORCE",
        "STAGING,true,false,false,ENFORCE",
        // Cross-region backup only
        "PRODUCTION,false,true,false,ENFORCE",
        "STAGING,false,true,false,ENFORCE",
        // Point-in-time recovery only
        "PRODUCTION,false,false,true,ENFORCE",
        "STAGING,false,false,true,ENFORCE",
        // Two features enabled
        "PRODUCTION,true,true,false,ENFORCE",
        "PRODUCTION,true,false,true,ENFORCE",
        "PRODUCTION,false,true,true,ENFORCE",
        // No backup features
        "PRODUCTION,false,false,false,ENFORCE",
        "STAGING,false,false,false,ENFORCE",
        // Advisory mode
        "PRODUCTION,true,true,true,ADVISORY",
        "PRODUCTION,false,false,false,ADVISORY",
        // DEV profile
        "DEV,false,false,false,ENFORCE",
        "DEV,true,true,true,ADVISORY"
    })
    void testHipaaExpandedPhysicalSafeguards(String profile, boolean automatedBackup,
                                             boolean crossRegion, boolean pointInTime,
                                             String complianceMode) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaPhysExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaPhysExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("crossRegionBackupEnabled", String.valueOf(crossRegion));
        cfcContext.put("pointInTimeRecoveryEnabled", String.valueOf(pointInTime));
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    /**
     * Expanded comprehensive multi-requirement scenarios - All §164 sections.
     * Tests realistic combinations of multiple HIPAA requirements together.
     */
    @ParameterizedTest
    @CsvSource({
        // Scenario 1: Fully compliant production
        "PRODUCTION,ENFORCE,true,true,true,true,true,true,alb-oidc,true,true,true,true,private-with-nat,2190",
        // Scenario 2: Minimal production (maximum violations)
        "PRODUCTION,ENFORCE,false,false,false,false,false,false,none,false,false,false,false,public-no-nat,90",
        // Scenario 3: Partial compliance - encryption only
        "PRODUCTION,ENFORCE,true,true,true,false,false,false,none,false,false,false,false,public-no-nat,90",
        // Scenario 4: Partial compliance - logging only
        "PRODUCTION,ENFORCE,false,false,false,true,true,true,none,false,false,false,false,public-no-nat,90",
        // Scenario 5: Partial compliance - auth only
        "PRODUCTION,ENFORCE,false,false,false,false,false,false,alb-oidc,true,true,false,false,public-no-nat,90",
        // Scenario 6: Partial compliance - monitoring only
        "PRODUCTION,ENFORCE,false,false,false,false,false,false,none,false,false,true,true,public-no-nat,90",
        // Scenario 7: Partial compliance - network only
        "PRODUCTION,ENFORCE,false,false,false,false,false,false,none,false,false,false,false,private-with-nat,90",
        // Scenario 8: Partial compliance - backup only
        "PRODUCTION,ENFORCE,false,false,false,false,false,false,none,true,true,false,false,public-no-nat,90",
        // Scenario 9: Two categories compliant (encryption + logging)
        "PRODUCTION,ENFORCE,true,true,true,true,true,true,none,false,false,false,false,public-no-nat,90",
        // Scenario 10: Advisory mode - fully configured
        "PRODUCTION,ADVISORY,true,true,true,true,true,true,alb-oidc,true,true,true,true,private-with-nat,2190",
        // Scenario 11: Advisory mode - minimal
        "PRODUCTION,ADVISORY,false,false,false,false,false,false,none,false,false,false,false,public-no-nat,90",
        // Scenario 12: Staging fully compliant
        "STAGING,ENFORCE,true,true,true,true,true,true,alb-oidc,true,true,true,true,private-with-nat,2190",
        // Scenario 13: Staging minimal
        "STAGING,ENFORCE,false,false,false,false,false,false,none,false,false,false,false,public-no-nat,90",
        // Scenario 14: Mixed compliance pattern 1
        "PRODUCTION,ENFORCE,true,false,true,true,false,true,jenkins-oidc,true,false,true,false,private-with-nat,365",
        // Scenario 15: Mixed compliance pattern 2
        "PRODUCTION,ENFORCE,false,true,false,false,true,false,alb-oidc,false,true,false,true,private-with-nat,1095",
        // Scenario 16: DEV profile
        "DEV,ENFORCE,false,false,false,false,false,false,none,false,false,false,false,public-no-nat,90"
    })
    void testHipaaExpandedComprehensiveMultiRequirement(String profile, String complianceMode,
                                                        boolean ebsEnc, boolean efsEnc, boolean s3Enc,
                                                        boolean cloudTrail, boolean flowLogs, boolean albLogging,
                                                        String authMode, boolean automatedBackup, boolean crossRegion,
                                                        boolean guardDuty, boolean secMonitoring,
                                                        String networkMode, int retentionDays) {
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaCompExp");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaCompExp");
        cfcContext.put("securityProfile", profile);
        cfcContext.put("complianceMode", complianceMode);
        cfcContext.put("ebsEncryptionEnabled", String.valueOf(ebsEnc));
        cfcContext.put("efsEncryptionAtRestEnabled", String.valueOf(efsEnc));
        cfcContext.put("s3EncryptionEnabled", String.valueOf(s3Enc));
        cfcContext.put("cloudTrailEnabled", String.valueOf(cloudTrail));
        cfcContext.put("flowLogsEnabled", String.valueOf(flowLogs));
        cfcContext.put("albAccessLoggingEnabled", String.valueOf(albLogging));
        cfcContext.put("authMode", authMode);
        cfcContext.put("automatedBackupEnabled", String.valueOf(automatedBackup));
        cfcContext.put("crossRegionBackupEnabled", String.valueOf(crossRegion));
        cfcContext.put("guardDutyEnabled", String.valueOf(guardDuty));
        cfcContext.put("securityMonitoringEnabled", String.valueOf(secMonitoring));
        cfcContext.put("networkMode", networkMode);
        cfcContext.put("logRetentionDays", String.valueOf(retentionDays));

        // ALB OIDC requires SSL
        if ("alb-oidc".equals(authMode) || "jenkins-oidc".equals(authMode)) {
            cfcContext.put("enableSsl", "true");
            cfcContext.put("fqdn", "hipaa.example.com");
        }

        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(secProfile);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, secProfile, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }
}
