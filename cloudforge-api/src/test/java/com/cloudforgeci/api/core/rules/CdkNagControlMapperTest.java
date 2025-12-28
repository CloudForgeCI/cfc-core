package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.rules.ComplianceMatrix.SecurityControl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for CdkNagControlMapper.
 *
 * <p>Validates the mapping between cdk-nag rule IDs and CloudForge SecurityControl enums,
 * ensuring accurate cross-layer compliance reporting.</p>
 *
 * @since 3.1.0
 */
class CdkNagControlMapperTest {

    // ========== Encryption At Rest Tests ==========

    @Test
    void testS3EncryptionRuleMapsToEncryptionAtRest() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-S3-2");

        assertTrue(control.isPresent(), "AwsSolutions-S3-2 should have a mapping");
        assertEquals(SecurityControl.ENCRYPTION_AT_REST, control.get(),
                "AwsSolutions-S3-2 should map to ENCRYPTION_AT_REST");
    }

    @Test
    void testRdsEncryptionRuleMapsToEncryptionAtRest() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-RDS-2");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ENCRYPTION_AT_REST, control.get());
    }

    @Test
    void testHipaaS3EncryptionRuleMapsToEncryptionAtRest() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "HIPAA.Security-S3BucketDefaultLockEnabled");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ENCRYPTION_AT_REST, control.get());
    }

    @Test
    void testPciDssRdsEncryptionRuleMapsToEncryptionAtRest() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "PCI.DSS.321-RDSStorageEncrypted");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ENCRYPTION_AT_REST, control.get());
    }

    // ========== Encryption In Transit Tests ==========

    @Test
    void testElbHttpsRuleMapsToEncryptionInTransit() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-ELB-2");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ENCRYPTION_IN_TRANSIT, control.get());
    }

    @Test
    void testCloudFrontHttpsRuleMapsToEncryptionInTransit() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-CFR-4");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ENCRYPTION_IN_TRANSIT, control.get());
    }

    @Test
    void testHipaaAlbHttpsRedirectMapsToEncryptionInTransit() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "HIPAA.Security-ALBHttpToHttpsRedirection");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ENCRYPTION_IN_TRANSIT, control.get());
    }

    // ========== Network Segmentation Tests ==========

    @Test
    void testVpcSecurityGroupRuleMapsToNetworkSegmentation() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-EC2-19");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.NETWORK_SEGMENTATION, control.get());
    }

    @Test
    void testRdsPublicAccessRuleMapsToNetworkSegmentation() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-RDS-11");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.NETWORK_SEGMENTATION, control.get());
    }

    @Test
    void testPciDssRestrictedSshMapsToNetworkSegmentation() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "PCI.DSS.321-EC2RestrictedSSH");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.NETWORK_SEGMENTATION, control.get());
    }

    // ========== Access Control Tests ==========

    @Test
    void testIamWildcardRuleMapsToAccessControl() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-IAM-5");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ACCESS_CONTROL, control.get());
    }

    @Test
    void testS3PublicReadRuleMapsToAccessControl() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-S3-1");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ACCESS_CONTROL, control.get());
    }

    @Test
    void testHipaaIamNoInlinePolicyMapsToAccessControl() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "HIPAA.Security-IAMNoInlinePolicy");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.ACCESS_CONTROL, control.get());
    }

    // ========== Authentication Tests ==========

    @Test
    void testCognitoMfaRuleMapsToAuthentication() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-COG-2");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.AUTHENTICATION, control.get());
    }

    @Test
    void testApiGatewayAuthRuleMapsToAuthentication() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-APIG-1");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.AUTHENTICATION, control.get());
    }

    // ========== Audit Logging Tests ==========

    @Test
    void testCloudTrailRuleMapsToAuditLogging() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-CT-1");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.AUDIT_LOGGING, control.get());
    }

    @Test
    void testVpcFlowLogsRuleMapsToNetworkFlowLogs() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-VPC-7");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.NETWORK_FLOW_LOGS, control.get());
    }

    @Test
    void testAlbAccessLogsRuleMapsToAuditLogging() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-ALB-1");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.AUDIT_LOGGING, control.get());
    }

    @Test
    void testPciDssCloudTrailEnabledMapsToAuditLogging() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "PCI.DSS.321-CloudTrailEnabled");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.AUDIT_LOGGING, control.get());
    }

    // ========== Security Monitoring Tests ==========

    @Test
    void testCloudWatchAlarmRuleMapsToSecurityMonitoring() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-SNS-1");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.SECURITY_MONITORING, control.get());
    }

    @Test
    void testHipaaAwsConfigEnabledMapsToSecurityMonitoring() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "HIPAA.Security-AWSConfigEnabled");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.SECURITY_MONITORING, control.get());
    }

    // ========== Threat Detection Tests ==========

    @Test
    void testHipaaGuardDutyEnabledMapsToThreatDetection() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(
                "HIPAA.Security-GuardDutyEnabled");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.THREAT_DETECTION, control.get());
    }

    // ========== WAF Protection Tests ==========

    @Test
    void testApiGatewayWafRuleMapsToWafProtection() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-APIG-6");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.WAF_PROTECTION, control.get());
    }

    @Test
    void testCloudFrontWafRuleMapsToWafProtection() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-CFR-2");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.WAF_PROTECTION, control.get());
    }

    // ========== Backup Recovery Tests ==========

    @Test
    void testRdsBackupRuleMapsToBackupRecovery() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-RDS-9");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.BACKUP_RECOVERY, control.get());
    }

    @Test
    void testDynamoDbPitrRuleMapsToBackupRecovery() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-DDB-2");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.DATABASE_PITR, control.get());
    }

    @Test
    void testS3VersioningRuleMapsToBackupRecovery() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-S3-3");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.BACKUP_RECOVERY, control.get());
    }

    // ========== High Availability Tests ==========

    @Test
    void testRdsMultiAzRuleMapsToHighAvailability() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-RDS-1");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.DATABASE_MULTI_AZ, control.get());
    }

    @Test
    void testElbDeletionProtectionRuleMapsToHighAvailability() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-ELB-4");

        assertTrue(control.isPresent());
        assertEquals(SecurityControl.HIGH_AVAILABILITY, control.get());
    }

    // ========== Utility Method Tests ==========

    @Test
    void testMapRuleToControlWithNullRuleId() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl(null);

        assertFalse(control.isPresent(), "Null rule ID should return empty Optional");
    }

    @Test
    void testMapRuleToControlWithEmptyRuleId() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("");

        assertFalse(control.isPresent(), "Empty rule ID should return empty Optional");
    }

    @Test
    void testMapRuleToControlWithUnknownRuleId() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("Unknown-Rule-123");

        assertFalse(control.isPresent(), "Unknown rule ID should return empty Optional");
    }

    @Test
    void testMapRuleToControlTrimsWhitespace() {
        Optional<SecurityControl> control = CdkNagControlMapper.mapRuleToControl("  AwsSolutions-S3-2  ");

        assertTrue(control.isPresent(), "Rule ID with whitespace should be trimmed and matched");
        assertEquals(SecurityControl.ENCRYPTION_AT_REST, control.get());
    }

    @Test
    void testGetRulesForEncryptionAtRestControl() {
        List<String> rules = CdkNagControlMapper.getRulesForControl(SecurityControl.ENCRYPTION_AT_REST);

        assertFalse(rules.isEmpty(), "ENCRYPTION_AT_REST should have mapped rules");
        assertTrue(rules.contains("AwsSolutions-S3-2"), "Should include AwsSolutions-S3-2");
        assertTrue(rules.contains("AwsSolutions-RDS-2"), "Should include AwsSolutions-RDS-2");
        assertTrue(rules.contains("HIPAA.Security-RDSStorageEncrypted"), "Should include HIPAA RDS rule");
    }

    @Test
    void testGetRulesForControlWithNullControl() {
        List<String> rules = CdkNagControlMapper.getRulesForControl(null);

        assertTrue(rules.isEmpty(), "Null control should return empty list");
    }

    @Test
    void testGetAllMappedRules() {
        Set<String> allRules = CdkNagControlMapper.getAllMappedRules();

        assertFalse(allRules.isEmpty(), "Should have mapped rules");
        assertTrue(allRules.size() > 100, "Should have comprehensive rule mappings");
        assertTrue(allRules.contains("AwsSolutions-S3-2"), "Should contain AWS Solutions rules");
        assertTrue(allRules.contains("HIPAA.Security-S3BucketDefaultLockEnabled"), "Should contain HIPAA rules");
        assertTrue(allRules.contains("PCI.DSS.321-RDSStorageEncrypted"), "Should contain PCI-DSS rules");
    }

    @Test
    void testHasMappingForRuleWithMappedRule() {
        assertTrue(CdkNagControlMapper.hasMappingForRule("AwsSolutions-S3-2"),
                "Should return true for mapped rule");
    }

    @Test
    void testHasMappingForRuleWithUnmappedRule() {
        assertFalse(CdkNagControlMapper.hasMappingForRule("Unknown-Rule-123"),
                "Should return false for unmapped rule");
    }

    @Test
    void testHasMappingForRuleWithNullRule() {
        assertFalse(CdkNagControlMapper.hasMappingForRule(null),
                "Should return false for null rule");
    }

    @Test
    void testGetMappingStatistics() {
        Map<String, Object> stats = CdkNagControlMapper.getMappingStatistics();

        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.containsKey("totalRulesMapped"), "Should contain totalRulesMapped");
        assertTrue(stats.containsKey("totalSecurityControls"), "Should contain totalSecurityControls");
        assertTrue(stats.containsKey("rulesByControl"), "Should contain rulesByControl");

        Integer totalRulesMapped = (Integer) stats.get("totalRulesMapped");
        assertNotNull(totalRulesMapped);
        assertTrue(totalRulesMapped > 0, "Should have mapped rules");

        Integer totalSecurityControls = (Integer) stats.get("totalSecurityControls");
        assertEquals(43, totalSecurityControls, "Should have 43 SecurityControl enums");
    }

    @Test
    void testGenerateMappingReport() {
        String report = CdkNagControlMapper.generateMappingReport();

        assertNotNull(report, "Report should not be null");
        assertFalse(report.isEmpty(), "Report should not be empty");
        assertTrue(report.contains("CDK-NAG to CloudForge Control Mapping Report"),
                "Report should contain title");
        assertTrue(report.contains("ENCRYPTION_AT_REST"), "Report should contain control names");
        assertTrue(report.contains("rules"), "Report should contain rule counts");
    }

    @Test
    void testAllSecurityControlsHaveMappings() {
        // Runtime-only controls that don't have CloudFormation/CDK-nag equivalents
        // These are validated during synthesis or require external service checks, not template analysis
        Set<SecurityControl> runtimeOnlyControls = Set.of(
            SecurityControl.SECRETS_MANAGER,        // Runtime check for Secrets Manager usage
            SecurityControl.SECRETS_ROTATION,       // Runtime check for secret rotation config
            SecurityControl.CERTIFICATE_EXPIRATION_MONITORING,  // Runtime check for CloudWatch alarms
            SecurityControl.AUDIT_MANAGER,          // Runtime check for AWS Audit Manager enablement
            SecurityControl.CLOUDWATCH_LOGS_KMS_ENCRYPTION,  // Log group KMS - validated at synth time
            SecurityControl.CLOUDTRAIL_INSIGHTS,    // CloudTrail Insights enablement - runtime config
            SecurityControl.ROUTE53_QUERY_LOGGING,  // DNS query logging - runtime config
            SecurityControl.ROOT_ACCOUNT_PROTECTION,  // Root account MFA - not deployable via CFN
            SecurityControl.CREDENTIAL_ROTATION,    // IAM key rotation - external service check
            SecurityControl.CERTIFICATE_MANAGEMENT,  // ACM certificate lifecycle - runtime config
            SecurityControl.DATABASE_ACCESS_CONTROL,  // RDS IAM auth - validated in rules, not cdk-nag
            SecurityControl.CONTAINER_SECURITY,  // EKS/ECS container security - validated in rules, not cdk-nag
            SecurityControl.API_SECURITY,  // API Gateway security - validated in rules, not cdk-nag
            SecurityControl.CDN_SECURITY,  // CloudFront security - validated in rules, not cdk-nag
            SecurityControl.INSTANCE_METADATA_SECURITY,  // EC2 IMDSv2 - validated in rules, not cdk-nag
            SecurityControl.LAMBDA_SECURITY,  // Lambda security - validated in rules, not cdk-nag
            SecurityControl.DATABASE_LOGGING,  // RDS logging - validated in rules, not cdk-nag
            SecurityControl.DELETION_PROTECTION,  // RDS deletion protection - validated in rules, not cdk-nag
            SecurityControl.HTTPS_STRICT,  // HTTPS-only mode - validated in FrameworkRules, not cdk-nag
            SecurityControl.S3_OBJECT_LOCK  // S3 Object Lock - validated in FrameworkRules, not cdk-nag
        );

        // Verify every SecurityControl enum (except runtime-only) has at least one mapped rule
        for (SecurityControl control : SecurityControl.values()) {
            if (runtimeOnlyControls.contains(control)) {
                // Skip runtime-only controls - they're validated during synthesis, not in templates
                continue;
            }

            List<String> rules = CdkNagControlMapper.getRulesForControl(control);
            assertFalse(rules.isEmpty(),
                    "SecurityControl " + control.name() + " should have at least one mapped cdk-nag rule");
        }
    }

    @Test
    void testFrameworkCoverage() {
        Set<String> allRules = CdkNagControlMapper.getAllMappedRules();

        // Verify we have rules from all three packs
        long awsSolutionsCount = allRules.stream()
                .filter(rule -> rule.startsWith("AwsSolutions-"))
                .count();
        long hipaaCount = allRules.stream()
                .filter(rule -> rule.startsWith("HIPAA.Security-"))
                .count();
        long pciDssCount = allRules.stream()
                .filter(rule -> rule.startsWith("PCI.DSS.321-"))
                .count();

        assertTrue(awsSolutionsCount > 0, "Should have AwsSolutions rules");
        assertTrue(hipaaCount > 0, "Should have HIPAA rules");
        assertTrue(pciDssCount > 0, "Should have PCI-DSS rules");

        System.out.println("Framework Coverage:");
        System.out.println("  AwsSolutions: " + awsSolutionsCount + " rules");
        System.out.println("  HIPAA: " + hipaaCount + " rules");
        System.out.println("  PCI-DSS: " + pciDssCount + " rules");
        System.out.println("  Total: " + allRules.size() + " rules");
    }

    @Test
    void testEncryptionAtRestComprehensiveCoverage() {
        List<String> encryptionRules = CdkNagControlMapper.getRulesForControl(SecurityControl.ENCRYPTION_AT_REST);

        // Verify we cover major AWS services for encryption at rest
        assertTrue(encryptionRules.stream().anyMatch(r -> r.contains("S3")), "Should cover S3 encryption");
        assertTrue(encryptionRules.stream().anyMatch(r -> r.contains("RDS")), "Should cover RDS encryption");
        assertTrue(encryptionRules.stream().anyMatch(r -> r.contains("EBS") || r.contains("EC2")),
                "Should cover EBS/EC2 encryption");
        assertTrue(encryptionRules.stream().anyMatch(r -> r.contains("EFS")), "Should cover EFS encryption");
        // Note: SNS encryption moved to SNS_KMS_ENCRYPTION control for KMS-specific requirements
        assertTrue(encryptionRules.stream().anyMatch(r -> r.contains("SQS")), "Should cover SQS encryption");

        // Verify SNS encryption is covered by SNS_KMS_ENCRYPTION control
        List<String> snsKmsRules = CdkNagControlMapper.getRulesForControl(SecurityControl.SNS_KMS_ENCRYPTION);
        assertTrue(snsKmsRules.stream().anyMatch(r -> r.contains("SNS")), "SNS_KMS_ENCRYPTION should cover SNS encryption");
    }

    @Test
    void testAuditLoggingComprehensiveCoverage() {
        List<String> auditRules = CdkNagControlMapper.getRulesForControl(SecurityControl.AUDIT_LOGGING);

        // Verify we cover major AWS services for audit logging
        // Note: VPC Flow Logs are now in NETWORK_FLOW_LOGS control
        assertTrue(auditRules.stream().anyMatch(r -> r.contains("CloudTrail")),
                "Should cover CloudTrail logging");
        assertTrue(auditRules.stream().anyMatch(r -> r.contains("ALB") || r.contains("ELB")),
                "Should cover ALB/ELB logging");
        assertTrue(auditRules.stream().anyMatch(r -> r.contains("S3") && r.contains("Logging")),
                "Should cover S3 access logs");
        assertTrue(auditRules.stream().anyMatch(r -> r.contains("RDS")), "Should cover RDS logging");
    }

    @Test
    void testNetworkSegmentationComprehensiveCoverage() {
        List<String> networkRules = CdkNagControlMapper.getRulesForControl(SecurityControl.NETWORK_SEGMENTATION);

        // Verify we cover network security aspects
        assertTrue(networkRules.stream().anyMatch(r -> r.contains("VPC")), "Should cover VPC security");
        assertTrue(networkRules.stream().anyMatch(r -> r.contains("SecurityGroup") || r.contains("EC2")),
                "Should cover security groups");
        assertTrue(networkRules.stream().anyMatch(r -> r.contains("RDS") && r.contains("Public")),
                "Should cover RDS public access");
        assertTrue(networkRules.stream().anyMatch(r -> r.contains("SSH") || r.contains("Port")),
                "Should cover SSH/port restrictions");
    }

    @Test
    void testBackupRecoveryComprehensiveCoverage() {
        List<String> backupRules = CdkNagControlMapper.getRulesForControl(SecurityControl.BACKUP_RECOVERY);

        // Verify we cover backup and recovery for major data stores
        assertTrue(backupRules.stream().anyMatch(r -> r.contains("RDS")), "Should cover RDS backups");
        // Note: DynamoDB PITR now mapped to DATABASE_PITR control, not BACKUP_RECOVERY
        assertTrue(backupRules.stream().anyMatch(r -> r.contains("S3") && r.contains("Version")),
                "Should cover S3 versioning");

        // Verify DATABASE_PITR control exists and covers DynamoDB
        List<String> pitrRules = CdkNagControlMapper.getRulesForControl(SecurityControl.DATABASE_PITR);
        assertTrue(pitrRules.stream().anyMatch(r -> r.contains("DynamoDB") || r.contains("DDB")),
                "DATABASE_PITR should cover DynamoDB PITR");
    }
}
