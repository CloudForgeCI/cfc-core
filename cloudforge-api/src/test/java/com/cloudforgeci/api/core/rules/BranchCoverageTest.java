package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive branch coverage tests for core.rules package.
 *
 * This test suite specifically targets untested branches identified through
 * coverage analysis to maximize branch and instruction coverage.
 */
class BranchCoverageTest {

    // ========== Soc2Rules Branch Coverage ==========

    @Test
    void testSoc2WithOnlyEbsEncryptionDisabled() {
        // Tests Soc2Rules.java line 144: EBS disabled, EFS enabled (first condition true, second false)
        App app = new App();
        Stack stack = new Stack(app, "TestSoc2OnlyEbsDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSoc2OnlyEbsDisabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "SOC2");
        cfcContext.put("ebsEncryptionEnabled", "false");
        cfcContext.put("efsEncryptionAtRestEnabled", "true");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "soc2.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new Soc2Rules().install(ctx));
    }

    @Test
    void testSoc2WithOnlyEfsEncryptionAtRestDisabled() {
        // Tests Soc2Rules.java line 144: EBS enabled, EFS disabled (first condition false, second true)
        App app = new App();
        Stack stack = new Stack(app, "TestSoc2OnlyEfsDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSoc2OnlyEfsDisabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "SOC2");
        cfcContext.put("ebsEncryptionEnabled", "true");
        cfcContext.put("efsEncryptionAtRestEnabled", "false");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "soc2.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new Soc2Rules().install(ctx));
    }

    @Test
    void testSoc2ProductionWithAllAvailabilityFeaturesEnabled() {
        // Tests Soc2Rules.java lines 351-388: PRODUCTION with all availability features enabled
        App app = new App();
        Stack stack = new Stack(app, "TestSoc2ProductionAvailability");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSoc2ProductionAvailability");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "SOC2");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "soc2.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("multiAzEnabled", "true");
        cfcContext.put("autoScalingEnabled", "true");
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("crossRegionBackupEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new Soc2Rules().install(ctx));
    }

    // ========== GdprRules Branch Coverage ==========

    @Test
    void testGdprWithPublicNetworkAndNonProduction() {
        // Tests GdprRules.java lines 176-187: networkMode="public-no-nat" with STAGING (not PRODUCTION)
        App app = new App();
        Stack stack = new Stack(app, "TestGdprPublicNetworkStaging");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprPublicNetworkStaging");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("networkMode", "public-no-nat");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprProductionWithAutomatedBackupEnabled() {
        // Tests GdprRules.java lines 339-345: PRODUCTION with automatedBackup=true
        App app = new App();
        Stack stack = new Stack(app, "TestGdprBackupEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprBackupEnabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("automatedBackupEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprProductionWithAwsConfigEnabled() {
        // Tests GdprRules.java lines 355-361: PRODUCTION with awsConfigEnabled=true
        App app = new App();
        Stack stack = new Stack(app, "TestGdprConfigEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprConfigEnabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("awsConfigEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    @Test
    void testGdprProductionWithWafEnabled() {
        // Tests GdprRules.java lines 431-437: PRODUCTION with wafEnabled=true
        App app = new App();
        Stack stack = new Stack(app, "TestGdprWafEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestGdprWafEnabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "gdpr.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("wafEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new GdprRules().install(ctx));
    }

    // ========== HipaaRules Branch Coverage ==========

    @Test
    void testHipaaWithProductionAndCrossRegionBackupDisabled() {
        // Tests HipaaRules.java lines 212-217: PRODUCTION with crossRegionBackup=false
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaCrossRegionDisabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaCrossRegionDisabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "hipaa.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("automatedBackupEnabled", "true");
        cfcContext.put("crossRegionBackupEnabled", "false");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithProductionAndCrossRegionBackupEnabled() {
        // Tests HipaaRules.java lines 218-223: PRODUCTION with crossRegionBackup=true (else-if branch)
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaCrossRegionEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaCrossRegionEnabled");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "hipaa.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("backupEnabled", "true");
        cfcContext.put("automatedBackupEnabled", "true");
        cfcContext.put("crossRegionBackupEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaWithOidcAuthModeWithoutMfaOrSso() {
        // Tests HipaaRules.java lines 382-403: OIDC auth with no MFA and no SSO
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaOidcNoMfaNoSso");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaOidcNoMfaNoSso");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "hipaa.example.com");
        cfcContext.put("cognitoAutoProvision", "false");  // No Cognito with MFA
        cfcContext.put("cognitoMfaEnabled", "false");
        // No ssoInstanceArn provided - missing SSO
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    @Test
    void testHipaaRetentionWithThreeYears() {
        // Tests HipaaRules.java line 505-522: THREE_YEARS retention period
        App app = new App();
        Stack stack = new Stack(app, "TestHipaaRetentionThreeYears");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestHipaaRetentionThreeYears");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "HIPAA");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", "true");
        cfcContext.put("fqdn", "hipaa.example.com");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("logRetentionDays", "1095");  // THREE_YEARS
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new HipaaRules().install(ctx));
    }

    // ========== SecurityRules Branch Coverage ==========

    @Test
    void testSecurityRulesWithWhitespaceOnlyComplianceFrameworks() {
        // Tests SecurityRules.java lines 61-64: frameworks.trim().isEmpty() with whitespace-only string
        App app = new App();
        Stack stack = new Stack(app, "TestSecurityWhitespaceFrameworks");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSecurityWhitespaceFrameworks");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        cfcContext.put("complianceFrameworks", "   ");  // Whitespace only
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new SecurityRules().install(ctx));
    }

    @Test
    void testSecurityRulesWithNullComplianceFrameworks() {
        // Tests SecurityRules.java lines 61-64: frameworks == null
        App app = new App();
        Stack stack = new Stack(app, "TestSecurityNullFrameworks");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSecurityNullFrameworks");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", "true");
        // complianceFrameworks not set (null)
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        assertDoesNotThrow(() -> new SecurityRules().install(ctx));
    }

    /*
     * NOTE: Rules.installAll() integration tests are omitted from this file to avoid
     * JSII kernel state conflicts where IAM role constructs persist across the entire
     * Maven Surefire test run. Rules.installAll() is already tested comprehensively
     * through the following integration tests:
     * - JenkinsBootstrapTest (uses Rules.installAll())
     * - RuntimeTopologyIntegrationTest (exercises all rule combinations)
     * - Individual rule tests (IAMRules, RuntimeRules, TopologyRules, SecurityRules)
     *
     * The architecture of Rules.installAll() is simple (sequential calls to sub-rules)
     * and is validated through the existing test suite.
     */
}
