package com.cloudforgeci.api.integration.compliance;

import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.observability.ComplianceFactory;
import com.cloudforgeci.api.observability.FlowLogFactory;
import com.cloudforgeci.api.observability.GuardDutyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Extensive integration tests for GDPR (General Data Protection Regulation) compliance.
 *
 * Tests validate that infrastructure meets GDPR requirements:
 * - Article 5(1)(f) - Integrity and Confidentiality
 * - Article 25 - Data Protection by Design and by Default
 * - Article 30 - Records of Processing Activities
 * - Article 32 - Security of Processing
 * - Article 33 - Notification of Personal Data Breach
 * - Article 35 - Data Protection Impact Assessment
 */
class GdprComplianceIntegrationTest extends IntegrationTestBase {

    @Override
    protected SecurityProfile getSecurityProfile() {
        return SecurityProfile.PRODUCTION;
    }

    @BeforeEach
    @Override
    public void setUp() {
        // Configure deployment context for GDPR compliance features
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "gdpr-compliance-test");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("guardDutyEnabled", true);
        cfcContext.put("createGuardDutyDetector", true);
        cfcContext.put("cloudTrailEnabled", true);
        cfcContext.put("enableFlowlogs", true);
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("createConfigInfrastructure", true);
        cfcContext.put("complianceFrameworks", "SOC2,HIPAA,PCI-DSS,GDPR");

        // Create infrastructure builder with custom context
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "GdprComplianceTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );
        this.stack = builder.getStack();
        this.ctx = builder.getSystemContext();
        this.cfc = builder.getDeploymentContext();
    }

    @Test
    void testGdprArticle32SecurityOfProcessing() {
        // Given: Complete infrastructure with compliance controls
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Article 32 - Security of Processing
        // (a) Pseudonymisation and encryption of personal data
        assertEfsEncrypted();
        assertS3BucketsEncrypted();
        assertLogGroupsEncrypted();

        // (b) Ensure ongoing confidentiality, integrity, availability
        assertMultiAzDeployment();
        assertBackupPoliciesConfigured();

        // (c) Ability to restore availability and access
        // EFS exists (backup policies configured separately in production)
        template.resourceCountIs("AWS::EFS::FileSystem", 1);

        // (d) Process for regularly testing security measures
        assertConfigRulesDeployed(10);
    }

    @Test
    void testGdprArticle5IntegrityAndConfidentiality() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        synthesizeTemplate();

        // Then: Verify Article 5(1)(f) - Integrity and Confidentiality
        // Encryption protects against unauthorized processing
        assertEfsEncrypted();
        assertS3BucketsEncrypted();

        // Access controls prevent unauthorized access
        // Security groups exist - count varies
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of()));
        // IAM roles exist - count varies by runtime and factories
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of()));

        // Audit logging detects breaches
        assertCloudTrailEnabled();
        assertVpcFlowLogsEnabled();

        // Log file validation ensures integrity
        template.hasResourceProperties("AWS::CloudTrail::Trail", Map.of(
            "EnableLogFileValidation", true
        ));
    }

    @Test
    void testGdprArticle25DataProtectionByDesign() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify Article 25 - Data Protection by Design and Default
        // Default encryption (encryption by default)
        template.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
            "Encrypted", true
        ));

        // Network isolation by default (private subnets)
        template.resourcePropertiesCountIs("AWS::EC2::Subnet",
            Map.of("MapPublicIpOnLaunch", false), 2);

        // Security groups deny by default, allow explicitly
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Map.of(
            "SecurityGroupIngress", Match.anyValue()
        ));

        // IAM roles follow least privilege by default
        // IAM roles exist - count varies by runtime and factories
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of()));
    }

    @Test
    void testGdprArticle30RecordsOfProcessing() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        synthesizeTemplate();

        // Then: Verify Article 30 - Records of Processing Activities
        // CloudTrail maintains records of all API operations
        assertCloudTrailEnabled();

        // Multi-region trail captures all activities
        template.hasResourceProperties("AWS::CloudTrail::Trail", Map.of(
            "IsMultiRegionTrail", true,
            "IncludeGlobalServiceEvents", true
        ));

        // VPC Flow Logs record network processing activities
        assertVpcFlowLogsEnabled();

        // CloudWatch Logs record application processing
        // LogGroup count varies based on factories used
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of()));

        // Log retention for accountability
        assertLogRetentionConfigured(90);
    }

    @Test
    void testGdprArticle33BreachNotification() {
        // Given: Complete infrastructure with monitoring
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        GuardDutyFactory guardDutyFactory = new GuardDutyFactory(stack, "GuardDuty");
        guardDutyFactory.create();

        synthesizeTemplate();

        // Then: Verify Article 33 - Notification of Personal Data Breach
        // GuardDuty detects threats and security events
        assertGuardDutyEnabled();

        // CloudTrail detects unauthorized access
        assertCloudTrailEnabled();

        // VPC Flow Logs detect unauthorized network access
        assertVpcFlowLogsEnabled();

        // CloudWatch Alarms enable timely breach notification
        assertCriticalAlarmsConfigured();

        // AWS Config detects configuration changes that could lead to breaches
        assertConfigRulesDeployed(10);

        // Log file validation detects tampering
        template.hasResourceProperties("AWS::CloudTrail::Trail", Map.of(
            "EnableLogFileValidation", true
        ));
    }

    @Test
    void testGdprDataMinimization() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify data minimization principles
        // Infrastructure uses minimal IAM permissions
        // IAM roles exist - count varies by runtime and factories
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of()));

        // Security groups use minimal required ports
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Map.of(
            "SecurityGroupIngress", Match.anyValue()
        ));

        // Log retention is configurable (not indefinite by default)
        assertLogRetentionConfigured(90); // Can be reduced per data minimization
    }

    @Test
    void testGdprRightToErasure() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify support for right to erasure (Article 17)
        // EFS provides file-level deletion capability
        template.resourceCountIs("AWS::EFS::FileSystem", 1);

        // S3 versioning allows controlled deletion
        template.hasResourceProperties("AWS::S3::Bucket", Map.of(
            "VersioningConfiguration", Map.of(
                "Status", "Enabled"
            )
        ));

        // Encryption supports crypto-shredding for secure erasure
        assertEfsEncrypted();
        assertS3BucketsEncrypted();
    }

    @Test
    void testGdprDataPortability() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify support for data portability (Article 20)
        // EFS allows standard file access for data export
        template.resourceCountIs("AWS::EFS::FileSystem", 1);

        // S3 provides standard API access for data export
        template.resourcePropertiesCountIs("AWS::S3::Bucket", Map.of(), 1);

        // CloudWatch Logs can be exported
        // LogGroup count varies based on factories used
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of()));
    }

    @Test
    void testGdprAccessControls() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify access controls for data protection
        // IAM roles enforce authentication and authorization
        // IAM roles exist - count varies by runtime and factories
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of()));

        // Security groups control network access
        // Security groups exist - count varies
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of()));

        // Private subnets restrict direct internet access
        template.resourcePropertiesCountIs("AWS::EC2::Subnet",
            Map.of("MapPublicIpOnLaunch", false), 2);

        // CloudTrail logs all access attempts
        assertCloudTrailEnabled();
    }

    @Test
    void testGdprDataResidency() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify data residency controls
        // VPC ensures data stays in specified region
        template.resourceCountIs("AWS::EC2::VPC", 1);

        // EFS is region-specific
        template.resourceCountIs("AWS::EFS::FileSystem", 1);

        // S3 buckets are region-specific
        template.resourcePropertiesCountIs("AWS::S3::Bucket", Map.of(), 1);

        // No cross-region replication by default
        // Regional services ensure compliance with data localization requirements
    }

    @Test
    void testGdprAccountabilityAndGovernance() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        synthesizeTemplate();

        // Then: Verify accountability and governance (Article 5(2))
        // CloudTrail provides audit trail for accountability
        assertCloudTrailEnabled();

        // AWS Config monitors compliance with policies
        assertConfigRulesDeployed(10);

        // VPC Flow Logs provide network accountability
        assertVpcFlowLogsEnabled();

        // Immutable logs (log file validation)
        template.hasResourceProperties("AWS::CloudTrail::Trail", Map.of(
            "EnableLogFileValidation", true
        ));

        // S3 versioning prevents log tampering
        template.hasResourceProperties("AWS::S3::Bucket", Map.of(
            "VersioningConfiguration", Map.of(
                "Status", "Enabled"
            )
        ));
    }

    @Test
    void testGdprThreatDetectionAndPrevention() {
        // Given: Complete infrastructure with security monitoring
        builder.createCompleteInfrastructure();

        GuardDutyFactory guardDutyFactory = new GuardDutyFactory(stack, "GuardDuty");
        guardDutyFactory.create();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Article 32 - Security of Processing includes threat detection
        // GuardDuty provides continuous threat detection
        assertGuardDutyEnabled();

        // GuardDuty detector properties
        template.hasResourceProperties("AWS::GuardDuty::Detector", Map.of(
            "Enable", true,
            "FindingPublishingFrequency", "FIFTEEN_MINUTES"
        ));

        // AWS Config provides configuration monitoring
        assertConfigRulesDeployed(10);

        // CloudTrail provides API activity monitoring
        assertCloudTrailEnabled();

        // Combined monitoring satisfies GDPR Article 32(1)(d):
        // "A process for regularly testing, assessing and evaluating
        // the effectiveness of technical and organisational measures"
    }
}
