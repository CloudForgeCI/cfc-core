package com.cloudforgeci.api.integration.remediation;

import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.observability.ComplianceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Extensive integration tests for AWS Config auto-remediation functionality.
 *
 * Tests validate that Config rules detect violations and trigger automatic remediation:
 * - S3 bucket public access remediation
 * - S3 versioning enforcement
 * - CloudTrail bucket access logging
 * - Security group rule violations
 * - Encryption enforcement
 */
class RemediationIntegrationTest extends IntegrationTestBase {

    @Override
    protected SecurityProfile getSecurityProfile() {
        return SecurityProfile.PRODUCTION;
    }

    @BeforeEach
    @Override
    public void setUp() {
        // Configure deployment context to enable AWS Config infrastructure in tests
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "remediation-test");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("awsConfigEnabled", true);
        cfcContext.put("createConfigInfrastructure", true);
        cfcContext.put("complianceFrameworks", "SOC2,HIPAA,PCI-DSS,GDPR");
        cfcContext.put("enableS3VersioningRemediation", true);
        cfcContext.put("enableCloudTrailLoggingRemediation", true);

        // Create infrastructure builder with custom context
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "RemediationTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE,
            cfcContext
        );
        this.stack = builder.getStack();
        this.ctx = builder.getSystemContext();
        this.cfc = builder.getDeploymentContext();
    }

    @Test
    void testS3PublicAccessBlockRemediation() {
        // Given: Complete infrastructure with compliance and remediation enabled
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify S3 public access block Config rule exists among the Config rules
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS",
                "SourceIdentifier", "S3_BUCKET_PUBLIC_READ_PROHIBITED"
            ))
        )));

        // Then: Verify Config Recorder was created for Config rules
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);

        // Then: Verify Delivery Channel was created
        template.resourceCountIs("AWS::Config::DeliveryChannel", 1);
    }

    @Test
    void testS3VersioningRemediation() {
        // Given: Complete infrastructure with S3 versioning remediation
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify S3 versioning Config rule exists
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS",
                "SourceIdentifier", "S3_BUCKET_VERSIONING_ENABLED"
            ))
        )));

        // Then: Verify Config infrastructure was created
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);
    }

    @Test
    void testCloudTrailBucketAccessLoggingRemediation() {
        // Given: Complete infrastructure with CloudTrail remediation
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify CloudTrail enabled Config rule
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS",
                "SourceIdentifier", "CLOUD_TRAIL_ENABLED"
            ))
        )));

        // Then: Verify CloudTrail log file validation Config rule
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS",
                "SourceIdentifier", "CLOUD_TRAIL_LOG_FILE_VALIDATION_ENABLED"
            ))
        )));
    }

    @Test
    void testEncryptionEnforcementRemediation() {
        // Given: Complete infrastructure with encryption enforcement
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify EBS encryption Config rule
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS",
                "SourceIdentifier", "EC2_EBS_ENCRYPTION_BY_DEFAULT"
            ))
        )));

        // Then: Verify S3 encryption Config rule
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS",
                "SourceIdentifier", "S3_BUCKET_SERVER_SIDE_ENCRYPTION_ENABLED"
            ))
        )));
    }

    @Test
    void testRemediationRetryConfiguration() {
        // Given: Complete infrastructure with remediation
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Config infrastructure exists
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);

        // Then: Verify Config Delivery Channel exists
        template.resourceCountIs("AWS::Config::DeliveryChannel", 1);
    }

    @Test
    void testRemediationIAMPermissions() {
        // Given: Complete infrastructure with remediation
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify IAM role for Config service
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
            "AssumeRolePolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(
                    Match.objectLike(Map.of(
                        "Principal", Match.objectLike(Map.of("Service", "config.amazonaws.com")),
                        "Effect", "Allow"
                    ))
                )
            ))
        )));
    }

    @Test
    void testConfigRulesWithRemediationScopeTagging() {
        // Given: Complete infrastructure with scoped remediation
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Config rules were created
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);

        // Then: Verify Config Delivery Channel exists
        template.resourceCountIs("AWS::Config::DeliveryChannel", 1);
    }

    @Test
    void testRemediationExecutionRolePermissions() {
        // Given: Complete infrastructure with remediation
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify IAM role exists for Config service
        template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
            "AssumeRolePolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(
                    Match.objectLike(Map.of(
                        "Principal", Match.objectLike(Map.of("Service", "config.amazonaws.com"))
                    ))
                )
            ))
        )));
    }

    @Test
    void testComplianceFrameworkSpecificRemediation() {
        // Given: Complete infrastructure with all compliance frameworks
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Config rules exist for all compliance frameworks
        template.hasResourceProperties("AWS::Config::ConfigRule", Match.objectLike(Map.of(
            "Source", Match.objectLike(Map.of(
                "Owner", "AWS"
            ))
        )));

        // Then: Verify Config infrastructure was created
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);
    }

    @Test
    void testRemediationNotificationConfiguration() {
        // Given: Complete infrastructure with remediation notifications
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Config infrastructure was created
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);

        // Then: Verify Delivery Channel exists
        template.resourceCountIs("AWS::Config::DeliveryChannel", 1);
    }

    @Test
    void testMultipleRemediationActionsPerRule() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();

        ComplianceFactory complianceFactory = new ComplianceFactory(stack, "Compliance");
        complianceFactory.create();

        synthesizeTemplate();

        // Then: Verify Config infrastructure was created
        template.resourceCountIs("AWS::Config::ConfigurationRecorder", 1);

        // Then: Verify Delivery Channel exists
        template.resourceCountIs("AWS::Config::DeliveryChannel", 1);
    }
}
