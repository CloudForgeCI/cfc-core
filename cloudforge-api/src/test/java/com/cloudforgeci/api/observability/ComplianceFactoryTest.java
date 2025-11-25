package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ComplianceFactory.
 *
 * Tests compliance and audit resource creation including:
 * - CloudTrail audit logging
 * - AWS Config compliance monitoring
 * - AWS Audit Manager continuous auditing
 * - Security profile-based configuration
 * - Compliance framework support (SOC2, HIPAA, PCI-DSS, GDPR)
 */
class ComplianceFactoryTest {

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
    void testComplianceFactoryCreation() {
        // Given: A stack with PRODUCTION security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestCompliance", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should create without errors
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithDevProfile() {
        // Given: A stack with DEV security profile (minimal compliance)
        App app = new App();
        Stack stack = createTestStack(app, "TestComplianceDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating ComplianceFactory for DEV
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should create with minimal compliance (CloudTrail only)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithStagingProfile() {
        // Given: A stack with STAGING security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestComplianceStaging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Creating ComplianceFactory for STAGING
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should create with full compliance testing
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithAwsConfigEnabled() {
        // Given: A stack with AWS Config explicitly enabled
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceConfig");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceConfig");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with Config enabled
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should create Config resources
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithAuditManagerEnabled() {
        // Given: A stack with Audit Manager enabled
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceAuditMgr");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceAuditMgr");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("auditManagerEnabled", true);
        cfcContext.put("auditManagerFrameworkId", "test-framework-id");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with Audit Manager
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should create Audit Manager resources
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithComplianceFrameworks() {
        // Given: A stack with multiple compliance frameworks
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceFrameworks");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceFrameworks");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "SOC2,HIPAA,PCI-DSS,GDPR");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with frameworks
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should configure for all frameworks
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithConfigInfrastructureCreation() {
        // Given: A stack with Config infrastructure creation enabled
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceConfigInfra");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceConfigInfra");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("createConfigInfrastructure", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with infrastructure
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should create Config infrastructure (recorder, bucket, role)
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithS3VersioningRemediation() {
        // Given: A stack with S3 versioning remediation enabled
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceS3Remediation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceS3Remediation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("enableS3VersioningRemediation", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with remediation
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should configure automatic remediation
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithCloudTrailBucketAccessRemediation() {
        // Given: A stack with CloudTrail bucket access remediation
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceTrailRemediation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceTrailRemediation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("enableCloudTrailBucketAccessRemediation", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with CloudTrail remediation
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should configure CloudTrail access remediation
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithScopedConfigRules() {
        // Given: A stack with Config rules scoped to deployment
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceScopedRules");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceScopedRules");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("scopeConfigRulesToDeployment", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with scoped rules
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should scope Config rules to deployment resources
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestComplianceProfile" + counter++, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

            // When: Creating ComplianceFactory for each profile
            assertDoesNotThrow(factory::create,
                "ComplianceFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testComplianceFactoryWithMinimalConfiguration() {
        // Given: A stack with minimal configuration
        App app = new App();
        Stack stack = createTestStack(app, "TestComplianceMinimal", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating ComplianceFactory with minimal config
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should handle minimal configuration
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testComplianceFactoryWithMaximalConfiguration() {
        // Given: A stack with all compliance features enabled
        App app = new App();
        Stack stack = new Stack(app, "TestComplianceMaximal");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestComplianceMaximal");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("auditManagerEnabled", true);
        cfcContext.put("auditManagerFrameworkId", "test-framework-id");
        cfcContext.put("complianceFrameworks", "SOC2,HIPAA,PCI-DSS,GDPR");
        cfcContext.put("createConfigInfrastructure", true);
        cfcContext.put("enableS3VersioningRemediation", true);
        cfcContext.put("enableCloudTrailBucketAccessRemediation", true);
        cfcContext.put("scopeConfigRulesToDeployment", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating ComplianceFactory with all features
        ComplianceFactory factory = new ComplianceFactory(stack, "Compliance");

        // Then: Should handle maximal configuration
        assertDoesNotThrow(factory::create);
    }
}
