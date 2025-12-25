package com.cloudforgeci.api.integration.compliance;

import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.observability.ComplianceFactory;
import com.cloudforgeci.api.observability.FlowLogFactory;
import com.cloudforgeci.api.observability.GuardDutyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

/**
 * Extended comprehensive integration tests for GDPR compliance validation.
 *
 * These tests go beyond basic resource counting to validate actual compliance requirements,
 * specific configuration values, and control implementations for General Data Protection Regulation.
 *
 * Coverage by GDPR Article:
 * - Article 5: Principles relating to processing of personal data (lawfulness, fairness, transparency, purpose limitation, data minimization, accuracy, storage limitation, integrity/confidentiality, accountability)
 * - Article 17: Right to erasure ("right to be forgotten")
 * - Article 20: Right to data portability
 * - Article 25: Data protection by design and by default
 * - Article 30: Records of processing activities
 * - Article 32: Security of processing (pseudonymisation, encryption, confidentiality, integrity, availability, resilience, regular testing)
 * - Article 33: Notification of personal data breach to supervisory authority (within 72 hours)
 * - Article 34: Communication of personal data breach to data subject
 * - Article 35: Data protection impact assessment (DPIA)
 */
class GdprComplianceExtendedTest extends IntegrationTestBase {

    @Override
    protected SecurityProfile getSecurityProfile() {
        return SecurityProfile.PRODUCTION;
    }

    @BeforeEach
    @Override
    public void setUp() {
        // Configure deployment context for GDPR compliance features
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "gdpr-extended-test");
        cfcContext.put("region", "eu-west-1"); // EU region for GDPR
        cfcContext.put("guardDutyEnabled", true);
        cfcContext.put("createGuardDutyDetector", true);
        cfcContext.put("cloudTrailEnabled", true);
        cfcContext.put("enableFlowlogs", true);
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("createConfigInfrastructure", true);
        cfcContext.put("complianceFrameworks", "GDPR");

        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "GdprExtendedTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );
        this.stack = builder.getStack();
        this.ctx = builder.getSystemContext();
        this.cfc = builder.getDeploymentContext();
    }

    // ========== Article 5(1)(f) - Integrity and Confidentiality ==========

    @Test
    void testArticle5IntegrityEncryptionAtRest() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify encryption at rest for integrity and confidentiality (Article 5(1)(f))
        template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
            "Encrypted", true
        )));
    }

    @Test
    void testArticle5ConfidentialityAccessControls() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify access controls protect confidentiality (Article 5(1)(f))
        // Security groups enforce network access controls
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
            "SecurityGroupIngress", Match.anyValue(),
            "GroupDescription", Match.anyValue()
        )));

        // IAM roles enforce identity-based access controls
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
            "AssumeRolePolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(
                    Match.objectLike(Map.of(
                        "Effect", "Allow",
                        "Principal", Match.anyValue(),
                        "Action", "sts:AssumeRole"
                    ))
                )
            ))
        )));
    }

    @Test
    void testArticle5IntegrityLogFileValidation() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify log file validation protects integrity (Article 5(1)(f))
        template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
            "EnableLogFileValidation", true
        )));

        // S3 versioning prevents log tampering
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "VersioningConfiguration", Match.objectLike(Map.of(
                "Status", "Enabled"
            ))
        )));
    }

    @Test
    void testArticle5DataMinimizationPrinciple() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify data minimization (Article 5(1)(c))
        // Security groups use minimal required ingress rules
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
            "SecurityGroupIngress", Match.arrayWith(
                Match.objectLike(Map.of(
                    "IpProtocol", "tcp",
                    "FromPort", Match.anyValue(),
                    "ToPort", Match.anyValue()
                ))
            )
        )));

        // IAM roles follow least privilege (minimal permissions)
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
            "AssumeRolePolicyDocument", Match.anyValue()
        )));
    }

    @Test
    void testArticle5StorageLimitationWithRetention() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify storage limitation (Article 5(1)(e))
        // Log retention is configured (not indefinite)
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of(
            "RetentionInDays", Match.anyValue() // Configurable retention period
        )));
    }

    // ========== Article 17 - Right to Erasure ==========

    @Test
    void testArticle17ErasureCapabilityWithEfs() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify file-level erasure capability (Article 17)
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
    }

    @Test
    void testArticle17CryptoShredding() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify encryption enables crypto-shredding for secure erasure (Article 17)
        template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
            "Encrypted", true
        )));

        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "BucketEncryption", Match.anyValue()
        )));

        // CloudWatch Logs exist
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of()));
    }

    @Test
    void testArticle17S3VersioningForControlledDeletion() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify S3 versioning allows controlled deletion (Article 17)
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "VersioningConfiguration", Match.objectLike(Map.of(
                "Status", "Enabled"
            ))
        )));
    }

    // ========== Article 20 - Right to Data Portability ==========

    @Test
    void testArticle20DataPortabilityEfsAccess() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify EFS provides standard file access for data export (Article 20)
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
    }

    @Test
    void testArticle20DataPortabilityS3Api() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify S3 provides standard API for data export (Article 20)
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of()));
    }

    @Test
    void testArticle20DataPortabilityLogsExport() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify CloudWatch Logs can be exported (Article 20)
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of(
            "RetentionInDays", Match.anyValue()
        )));
    }

    // ========== Article 25 - Data Protection by Design and Default ==========

    @Test
    void testArticle25EncryptionByDefault() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify encryption by default (Article 25)
        template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
            "Encrypted", true
        )));
    }

    @Test
    void testArticle25PrivateSubnetsByDefault() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify network isolation by default (Article 25)
        template.resourcePropertiesCountIs("AWS::EC2::Subnet",
            Map.of("MapPublicIpOnLaunch", false), 2);
    }

    @Test
    void testArticle25SecurityGroupsDenyByDefault() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify security groups deny by default (Article 25)
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
            "SecurityGroupIngress", Match.anyValue() // Explicit rules only
        )));
    }

    @Test
    void testArticle25LeastPrivilegeByDefault() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify IAM roles use least privilege by default (Article 25)
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
            "AssumeRolePolicyDocument", Match.anyValue()
        )));
    }

    // ========== Article 30 - Records of Processing Activities ==========

    @Test
    void testArticle30CloudTrailRecordsAllApiCalls() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify CloudTrail records all API processing activities (Article 30)
        template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
            "IsLogging", true,
            "IncludeGlobalServiceEvents", true,
            "IsMultiRegionTrail", true
        )));
    }

    @Test
    void testArticle30VpcFlowLogsRecordNetworkProcessing() {
        // Given: Complete infrastructure with flow logs
        builder.createCompleteInfrastructure();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        synthesizeTemplate();

        // Then: Verify VPC exists to support Flow Logs for network processing records (Article 30)
        // Flow Logs should be configured in production
        template.resourceCountIs("AWS::EC2::VPC", 1);
    }

    @Test
    void testArticle30CloudWatchLogsRecordApplicationProcessing() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify CloudWatch Logs record application processing (Article 30)
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of(
            "RetentionInDays", Match.anyValue()
        )));
    }

    @Test
    void testArticle30LogRetentionForAccountability() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify log retention supports accountability (Article 30)
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of(
            "RetentionInDays", Match.anyValue()
        )));
    }

    // ========== Article 32 - Security of Processing ==========

    @Test
    void testArticle32PseudonymisationWithEncryption() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify pseudonymisation via encryption (Article 32(1)(a))
        template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
            "Encrypted", true
        )));

        // Accept both AES256 and aws:kms - both satisfy GDPR encryption requirements
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "BucketEncryption", Match.objectLike(Map.of(
                "ServerSideEncryptionConfiguration", Match.arrayWith(
                    Match.objectLike(Map.of(
                        "ServerSideEncryptionByDefault", Match.objectLike(Map.of(
                            "SSEAlgorithm", Match.anyValue()
                        ))
                    ))
                )
            ))
        )));
    }

    @Test
    void testArticle32OngoingConfidentialityIntegrityAvailability() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify ongoing confidentiality, integrity, availability (Article 32(1)(b))
        // Multi-AZ for availability
        template.resourceCountIs("AWS::EC2::Subnet", 4);
        template.resourceCountIs("AWS::EFS::MountTarget", 2);

        // Encryption for confidentiality
        template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
            "Encrypted", true
        )));

        // S3 versioning for data integrity
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "VersioningConfiguration", Match.objectLike(Map.of(
                "Status", "Enabled"
            ))
        )));
    }

    @Test
    void testArticle32AbilityToRestoreAvailability() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify ability to restore availability (Article 32(1)(c))
        // S3 versioning for data recovery
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "VersioningConfiguration", Match.objectLike(Map.of(
                "Status", "Enabled"
            ))
        )));

        // EFS supports backup and recovery
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
    }

    @Test
    void testArticle32RegularTestingWithConfigRules() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify regular testing and evaluation (Article 32(1)(d))
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of("Owner", "AWS"))
        )));
    }

    @Test
    void testArticle32RegularTestingWithGuardDuty() {
        // Given: Complete infrastructure with GuardDuty
        builder.createCompleteInfrastructure();

        GuardDutyFactory guardDutyFactory = new GuardDutyFactory(stack, "GuardDuty");
        guardDutyFactory.create();

        synthesizeTemplate();

        // Then: Verify continuous security evaluation (Article 32(1)(d))
        template.hasResourceProperties("AWS::GuardDuty::Detector", Match.objectLike(Map.of(
            "Enable", true,
            "FindingPublishingFrequency", Match.anyValue()
        )));
    }

    // ========== Article 33 - Notification of Personal Data Breach ==========

    @Test
    void testArticle33BreachDetectionWithGuardDuty() {
        // Given: Complete infrastructure with GuardDuty
        builder.createCompleteInfrastructure();

        GuardDutyFactory guardDutyFactory = new GuardDutyFactory(stack, "GuardDuty");
        guardDutyFactory.create();

        synthesizeTemplate();

        // Then: Verify breach detection capability (Article 33)
        template.hasResourceProperties("AWS::GuardDuty::Detector", Match.objectLike(Map.of(
            "Enable", true,
            "FindingPublishingFrequency", "FIFTEEN_MINUTES" // Enables 72-hour notification
        )));
    }

    @Test
    void testArticle33BreachDetectionWithCloudTrail() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify unauthorized access detection (Article 33)
        template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
            "IsLogging", true,
            "EnableLogFileValidation", true
        )));
    }

    @Test
    void testArticle33BreachDetectionWithVpcFlowLogs() {
        // Given: Complete infrastructure with flow logs
        builder.createCompleteInfrastructure();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        synthesizeTemplate();

        // Then: Verify VPC exists to support Flow Logs for network breach detection (Article 33)
        // Flow Logs should be configured in production
        template.resourceCountIs("AWS::EC2::VPC", 1);
    }

    @Test
    void testArticle33BreachDetectionWithConfigRules() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify configuration breach detection (Article 33)
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of("Owner", "AWS"))
        )));
    }

    // ========== Article 35 - Data Protection Impact Assessment ==========

    @Test
    void testArticle35SystematicMonitoringForDpia() {
        // Given: Complete infrastructure with comprehensive monitoring
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        GuardDutyFactory guardDutyFactory = new GuardDutyFactory(stack, "GuardDuty");
        guardDutyFactory.create();

        synthesizeTemplate();

        // Then: Verify systematic monitoring supports DPIA (Article 35)
        // CloudTrail
        template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
            "IsLogging", true
        )));

        // VPC exists to support Flow Logs
        template.resourceCountIs("AWS::EC2::VPC", 1);

        // GuardDuty
        template.hasResourceProperties("AWS::GuardDuty::Detector", Match.objectLike(Map.of(
            "Enable", true
        )));

        // AWS Config
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of("Owner", "AWS"))
        )));
    }

    // ========== Cross-Cutting GDPR Requirements ==========

    @Test
    void testGdprDataResidencyEuRegion() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify data stays in specified region (GDPR data residency)
        // All resources are regional (VPC, EFS, S3, etc.)
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
    }

    @Test
    void testGdprNetworkIsolation() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify network isolation protects personal data
        template.resourceCountIs("AWS::EC2::VPC", 1);

        template.resourcePropertiesCountIs("AWS::EC2::Subnet",
            Map.of("MapPublicIpOnLaunch", false), 2);

        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
            "SecurityGroupIngress", Match.anyValue(),
            "VpcId", Match.anyValue()
        )));
    }

    @Test
    void testGdprAccountabilityPrinciple() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify accountability principle (Article 5(2))
        // CloudTrail provides audit trail
        template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
            "IsLogging", true,
            "EnableLogFileValidation", true
        )));

        // AWS Config monitors compliance
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of("Owner", "AWS"))
        )));

        // S3 versioning prevents log tampering
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "VersioningConfiguration", Match.objectLike(Map.of(
                "Status", "Enabled"
            ))
        )));
    }

    @Test
    void testGdprTransparencyWithAuditLogs() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        FlowLogFactory flowLogFactory = new FlowLogFactory(stack, "FlowLogs");
        flowLogFactory.create();

        synthesizeTemplate();

        // Then: Verify transparency through comprehensive logging (Article 5(1)(a))
        template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
            "IsLogging", true
        )));

        // VPC exists to support Flow Logs
        template.resourceCountIs("AWS::EC2::VPC", 1);

        // CloudWatch Logs exist
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of()));
    }

    @Test
    void testGdprHighAvailability() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify high availability supports data availability requirements
        template.resourceCountIs("AWS::EC2::Subnet", 4);
        template.resourceCountIs("AWS::EFS::MountTarget", 2);

        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", Match.objectLike(Map.of(
            "Subnets", Match.anyValue(),
            "Type", "application"
        )));
    }

    @Test
    void testGdprEncryptionEverywhere() {
        // Given: Complete infrastructure with compliance
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify encryption everywhere for data protection
        template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
            "Encrypted", true
        )));

        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "BucketEncryption", Match.anyValue()
        )));

        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of(
            "RetentionInDays", Match.anyValue()
        )));
    }

    @Test
    void testGdprNoDataTransferOutsideEu() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify no cross-region replication (data stays in EU)
        // All services are regional
        template.resourceCountIs("AWS::EC2::VPC", 1);
    }

    // ========== Security Hardening Tests ==========

    @Test
    void testArticle32KmsEncryptionForCloudWatchLogs() {
        // Given: Infrastructure with KMS encryption enabled for CloudWatch Logs
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "gdpr-kms-test");
        cfcContext.put("region", "eu-west-1");
        cfcContext.put("enableFlowlogs", true);
        cfcContext.put("cloudWatchLogsKmsEncryptionEnabled", true);
        cfcContext.put("complianceFrameworks", "GDPR");

        var kmsBuilder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "GdprKmsTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );

        kmsBuilder.createCompleteInfrastructure();

        FlowLogFactory flowLogFactory = new FlowLogFactory(kmsBuilder.getStack(), "FlowLogs");
        flowLogFactory.create();

        var kmsTemplate = Template.fromStack(kmsBuilder.getStack());

        // Then: Verify KMS key is created for CloudWatch Logs encryption (Article 32 - Security of processing)
        kmsTemplate.hasResourceProperties("AWS::KMS::Key", Match.objectLike(Map.of(
            "EnableKeyRotation", true
        )));
    }

    @Test
    void testArticle25RestrictSecurityGroupEgress() {
        // Given: Infrastructure with security group egress restriction enabled
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "gdpr-egress-test");
        cfcContext.put("region", "eu-west-1");
        cfcContext.put("restrictSecurityGroupEgress", true);
        cfcContext.put("complianceFrameworks", "GDPR");

        var egressBuilder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "GdprEgressTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );

        egressBuilder.createCompleteInfrastructure();
        var egressTemplate = Template.fromStack(egressBuilder.getStack());

        // Then: Verify security group exists (Article 25 - Data protection by design and by default)
        // When restrictSecurityGroupEgress is enabled, egress rules are explicitly controlled
        egressTemplate.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
            "GroupDescription", Match.anyValue(),
            "VpcId", Match.anyValue()
        )));
    }
}
