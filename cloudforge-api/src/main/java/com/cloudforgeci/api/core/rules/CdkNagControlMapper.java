package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.rules.ComplianceMatrix.SecurityControl;

import java.util.*;
import java.util.logging.Logger;

/**
 * Maps cdk-nag rule IDs to CloudForge SecurityControl enums for unified compliance reporting.
 *
 * <p>This mapper enables multi-layer defense-in-depth by correlating violations detected
 * by cdk-nag with CloudForge's ComplianceMatrix framework. It supports:</p>
 * <ul>
 *   <li>AwsSolutionsChecks (general AWS best practices)</li>
 *   <li>HIPAASecurityChecks (HIPAA-specific rules)</li>
 *   <li>PCIDSS321Checks (PCI-DSS-specific rules)</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>
 * Optional&lt;SecurityControl&gt; control = CdkNagControlMapper.mapRuleToControl("AwsSolutions-S3-2");
 * // Returns: Optional[ENCRYPTION_AT_REST]
 * </pre>
 *
 * @since 3.1.0
 */
public final class CdkNagControlMapper {
    private static final Logger LOG = Logger.getLogger(CdkNagControlMapper.class.getName());

    /**
     * Comprehensive mapping from cdk-nag rule IDs to CloudForge SecurityControl enums.
     *
     * <p>Rules are organized by security control category for maintainability.</p>
     */
    private static final Map<String, SecurityControl> RULE_TO_CONTROL = createRuleMapping();

    private CdkNagControlMapper() {}

    private static Map<String, SecurityControl> createRuleMapping() {
        Map<String, SecurityControl> map = new HashMap<>();

        // ========== ENCRYPTION_AT_REST ==========
        // S3 encryption
        map.put("AwsSolutions-S3-2", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-S3BucketDefaultLockEnabled", SecurityControl.ENCRYPTION_AT_REST);
        map.put("PCI.DSS.321-S3BucketDefaultLockEnabled", SecurityControl.ENCRYPTION_AT_REST);

        // RDS encryption
        map.put("AwsSolutions-RDS-2", SecurityControl.ENCRYPTION_AT_REST);
        map.put("AwsSolutions-RDS-3", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-RDSStorageEncrypted", SecurityControl.ENCRYPTION_AT_REST);
        map.put("PCI.DSS.321-RDSStorageEncrypted", SecurityControl.ENCRYPTION_AT_REST);

        // DynamoDB encryption
        map.put("AwsSolutions-DDB-3", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-DynamoDBInBackupPlan", SecurityControl.ENCRYPTION_AT_REST);

        // EBS encryption
        map.put("AwsSolutions-EC2-1", SecurityControl.ENCRYPTION_AT_REST);
        map.put("AwsSolutions-EC2-3", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-EC2EBSEncryptionByDefault", SecurityControl.ENCRYPTION_AT_REST);
        map.put("PCI.DSS.321-EC2EBSEncryptionByDefault", SecurityControl.ENCRYPTION_AT_REST);

        // EFS encryption
        map.put("AwsSolutions-EFS-1", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-EFSEncrypted", SecurityControl.ENCRYPTION_AT_REST);
        map.put("PCI.DSS.321-EFSEncrypted", SecurityControl.ENCRYPTION_AT_REST);

        // SNS encryption
        map.put("AwsSolutions-SNS-2", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-SNSEncryptedKMS", SecurityControl.ENCRYPTION_AT_REST);
        map.put("PCI.DSS.321-SNSEncryptedKMS", SecurityControl.ENCRYPTION_AT_REST);

        // SQS encryption
        map.put("AwsSolutions-SQS-2", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-SQSQueueEncrypted", SecurityControl.ENCRYPTION_AT_REST);
        map.put("PCI.DSS.321-SQSQueueEncrypted", SecurityControl.ENCRYPTION_AT_REST);

        // Secrets Manager encryption
        map.put("AwsSolutions-SMG-4", SecurityControl.ENCRYPTION_AT_REST);
        map.put("HIPAA.Security-SecretsManagerUsingKMSKey", SecurityControl.ENCRYPTION_AT_REST);

        // ========== ENCRYPTION_IN_TRANSIT ==========
        // ALB/ELB HTTPS
        map.put("AwsSolutions-ELB-2", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("AwsSolutions-ELB-1", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("HIPAA.Security-ELBTlsHttpsListenersOnly", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("HIPAA.Security-ALBHttpToHttpsRedirection", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("PCI.DSS.321-ELBTlsHttpsListenersOnly", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("PCI.DSS.321-ALBHttpToHttpsRedirection", SecurityControl.ENCRYPTION_IN_TRANSIT);

        // CloudFront HTTPS
        map.put("AwsSolutions-CFR-4", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("HIPAA.Security-CloudFrontViewerPolicyHTTPS", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("PCI.DSS.321-CloudFrontViewerPolicyHTTPS", SecurityControl.ENCRYPTION_IN_TRANSIT);

        // API Gateway HTTPS
        map.put("AwsSolutions-APIG-2", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("HIPAA.Security-APIGWSSLEnabled", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("PCI.DSS.321-APIGWSSLEnabled", SecurityControl.ENCRYPTION_IN_TRANSIT);

        // RDS SSL/TLS
        map.put("AwsSolutions-RDS-10", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("HIPAA.Security-RDSEnhancedMonitoringEnabled", SecurityControl.SECURITY_MONITORING);

        // Redshift SSL
        map.put("AwsSolutions-RS-1", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("HIPAA.Security-RedshiftRequireTLSSSL", SecurityControl.ENCRYPTION_IN_TRANSIT);
        map.put("PCI.DSS.321-RedshiftRequireTLSSSL", SecurityControl.ENCRYPTION_IN_TRANSIT);

        // ========== NETWORK_SEGMENTATION ==========
        // VPC security
        map.put("AwsSolutions-VPC-3", SecurityControl.NETWORK_SEGMENTATION);
        map.put("AwsSolutions-EC2-10", SecurityControl.NETWORK_SEGMENTATION);
        map.put("HIPAA.Security-VPCDefaultSecurityGroupClosed", SecurityControl.NETWORK_SEGMENTATION);
        map.put("PCI.DSS.321-VPCDefaultSecurityGroupClosed", SecurityControl.NETWORK_SEGMENTATION);

        // Security group restrictions
        map.put("AwsSolutions-EC2-18", SecurityControl.NETWORK_SEGMENTATION);
        map.put("AwsSolutions-EC2-19", SecurityControl.NETWORK_SEGMENTATION);
        map.put("AwsSolutions-EC2-21", SecurityControl.NETWORK_SEGMENTATION);
        map.put("AwsSolutions-EC2-23", SecurityControl.NETWORK_SEGMENTATION);
        map.put("HIPAA.Security-EC2RestrictedSSH", SecurityControl.NETWORK_SEGMENTATION);
        map.put("HIPAA.Security-RestrictedCommonPorts", SecurityControl.NETWORK_SEGMENTATION);
        map.put("PCI.DSS.321-EC2RestrictedSSH", SecurityControl.NETWORK_SEGMENTATION);
        map.put("PCI.DSS.321-RestrictedCommonPorts", SecurityControl.NETWORK_SEGMENTATION);

        // RDS public access
        map.put("AwsSolutions-RDS-11", SecurityControl.NETWORK_SEGMENTATION);
        map.put("HIPAA.Security-RDSInstancePublicAccess", SecurityControl.NETWORK_SEGMENTATION);
        map.put("PCI.DSS.321-RDSInstancePublicAccess", SecurityControl.NETWORK_SEGMENTATION);

        // Lambda VPC
        map.put("AwsSolutions-L1", SecurityControl.NETWORK_SEGMENTATION);
        map.put("HIPAA.Security-LambdaInsideVPC", SecurityControl.NETWORK_SEGMENTATION);
        map.put("PCI.DSS.321-LambdaInsideVPC", SecurityControl.NETWORK_SEGMENTATION);

        // ========== ACCESS_CONTROL ==========
        // IAM policies
        map.put("AwsSolutions-IAM-4", SecurityControl.ACCESS_CONTROL);
        map.put("AwsSolutions-IAM-5", SecurityControl.ACCESS_CONTROL);
        map.put("HIPAA.Security-IAMNoInlinePolicy", SecurityControl.ACCESS_CONTROL);
        map.put("HIPAA.Security-IAMPolicyNoStatementsWithAdminAccess", SecurityControl.ACCESS_CONTROL);
        map.put("PCI.DSS.321-IAMNoInlinePolicy", SecurityControl.ACCESS_CONTROL);
        map.put("PCI.DSS.321-IAMPolicyNoStatementsWithAdminAccess", SecurityControl.ACCESS_CONTROL);

        // S3 bucket policies
        map.put("AwsSolutions-S3-1", SecurityControl.ACCESS_CONTROL);
        map.put("AwsSolutions-S3-5", SecurityControl.ACCESS_CONTROL);
        map.put("AwsSolutions-S3-6", SecurityControl.ACCESS_CONTROL);
        map.put("HIPAA.Security-S3BucketPublicReadProhibited", SecurityControl.ACCESS_CONTROL);
        map.put("HIPAA.Security-S3BucketPublicWriteProhibited", SecurityControl.ACCESS_CONTROL);
        map.put("PCI.DSS.321-S3BucketPublicReadProhibited", SecurityControl.ACCESS_CONTROL);
        map.put("PCI.DSS.321-S3BucketPublicWriteProhibited", SecurityControl.ACCESS_CONTROL);

        // ECR image scanning
        map.put("AwsSolutions-ECR-1", SecurityControl.ACCESS_CONTROL);
        map.put("AwsSolutions-ECR-2", SecurityControl.ACCESS_CONTROL);
        // ECRImageScanning moved to VULNERABILITY_MANAGEMENT section (line 289)

        // KMS key rotation
        map.put("AwsSolutions-KMS-5", SecurityControl.ACCESS_CONTROL);
        map.put("HIPAA.Security-KMSBackingKeyRotationEnabled", SecurityControl.ACCESS_CONTROL);
        map.put("PCI.DSS.321-KMSBackingKeyRotationEnabled", SecurityControl.ACCESS_CONTROL);

        // ========== AUTHENTICATION ==========
        // Cognito user pool MFA
        map.put("AwsSolutions-COG-1", SecurityControl.AUTHENTICATION);
        map.put("AwsSolutions-COG-2", SecurityControl.AUTHENTICATION);
        map.put("AwsSolutions-COG-3", SecurityControl.AUTHENTICATION);
        map.put("HIPAA.Security-CognitoUserPoolAdvancedSecurityModeEnforced", SecurityControl.AUTHENTICATION);

        // API Gateway authentication
        map.put("AwsSolutions-APIG-1", SecurityControl.AUTHENTICATION);
        map.put("AwsSolutions-APIG-4", SecurityControl.AUTHENTICATION);
        map.put("HIPAA.Security-APIGWRequestValidation", SecurityControl.AUTHENTICATION);
        map.put("PCI.DSS.321-APIGWRequestValidation", SecurityControl.AUTHENTICATION);

        // ========== AUDIT_LOGGING ==========
        // CloudTrail
        map.put("AwsSolutions-CT-1", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-CloudTrailCloudWatchLogsEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-CloudTrailEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-CloudTrailCloudWatchLogsEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-CloudTrailEnabled", SecurityControl.AUDIT_LOGGING);

        // VPC Flow Logs
        map.put("AwsSolutions-VPC-7", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-VPCFlowLogsEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-VPCFlowLogsEnabled", SecurityControl.AUDIT_LOGGING);

        // ALB/ELB access logs
        map.put("AwsSolutions-ALB-1", SecurityControl.AUDIT_LOGGING);
        map.put("AwsSolutions-ELB-3", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-ALBAccessLogsEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-ALBAccessLogsEnabled", SecurityControl.AUDIT_LOGGING);

        // S3 access logs
        map.put("AwsSolutions-S3-9", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-S3BucketLoggingEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-S3BucketLoggingEnabled", SecurityControl.AUDIT_LOGGING);

        // CloudFront access logs
        map.put("AwsSolutions-CFR-1", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-CloudFrontAccessLogsEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-CloudFrontAccessLogsEnabled", SecurityControl.AUDIT_LOGGING);

        // RDS logging
        map.put("AwsSolutions-RDS-6", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-RDSLoggingEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-RDSLoggingEnabled", SecurityControl.AUDIT_LOGGING);

        // API Gateway logging
        map.put("AwsSolutions-APIG-3", SecurityControl.AUDIT_LOGGING);
        map.put("HIPAA.Security-APIGWExecutionLoggingEnabled", SecurityControl.AUDIT_LOGGING);
        map.put("PCI.DSS.321-APIGWExecutionLoggingEnabled", SecurityControl.AUDIT_LOGGING);

        // Lambda logging
        map.put("AwsSolutions-L-1", SecurityControl.AUDIT_LOGGING);
        // LambdaConcurrency moved to VULNERABILITY_MANAGEMENT section (line 293)

        // ========== LOG_RETENTION ==========
        // CloudWatch Logs retention
        map.put("AwsSolutions-L3", SecurityControl.LOG_RETENTION);
        map.put("HIPAA.Security-LogGroupRetentionPeriod", SecurityControl.LOG_RETENTION);

        // ========== SECURITY_MONITORING ==========
        // CloudWatch alarms
        map.put("AwsSolutions-SNS-1", SecurityControl.SECURITY_MONITORING);
        map.put("AwsSolutions-RDS-5", SecurityControl.SECURITY_MONITORING);
        map.put("HIPAA.Security-CloudWatchAlarmAction", SecurityControl.SECURITY_MONITORING);
        map.put("PCI.DSS.321-CloudWatchAlarmAction", SecurityControl.SECURITY_MONITORING);

        // AWS Config
        map.put("HIPAA.Security-AWSConfigEnabled", SecurityControl.SECURITY_MONITORING);
        map.put("PCI.DSS.321-AWSConfigEnabled", SecurityControl.SECURITY_MONITORING);

        // ========== THREAT_DETECTION ==========
        // GuardDuty
        map.put("HIPAA.Security-GuardDutyEnabled", SecurityControl.THREAT_DETECTION);
        map.put("PCI.DSS.321-GuardDutyEnabled", SecurityControl.THREAT_DETECTION);

        // ========== SECURITY_HUB ==========
        // Security Hub centralized findings
        map.put("HIPAA.Security-SecurityHubEnabled", SecurityControl.SECURITY_HUB);
        map.put("PCI.DSS.321-SecurityHubEnabled", SecurityControl.SECURITY_HUB);

        // ========== VULNERABILITY_SCANNING ==========
        // AWS Inspector vulnerability scanning
        map.put("HIPAA.Security-EC2InstanceManagedBySSM", SecurityControl.VULNERABILITY_SCANNING);
        map.put("PCI.DSS.321-EC2InstanceManagedBySSM", SecurityControl.VULNERABILITY_SCANNING);

        // ========== SENSITIVE_DATA_DISCOVERY ==========
        // AWS Macie sensitive data discovery
        map.put("HIPAA.Security-DataClassificationEnabled", SecurityControl.SENSITIVE_DATA_DISCOVERY);
        map.put("PCI.DSS.321-S3BucketReplicationEnabled", SecurityControl.SENSITIVE_DATA_DISCOVERY);

        // ========== WAF_PROTECTION ==========
        // WAF v2
        map.put("AwsSolutions-APIG-6", SecurityControl.WAF_PROTECTION);
        map.put("AwsSolutions-CFR-2", SecurityControl.WAF_PROTECTION);
        map.put("HIPAA.Security-WAFv2LoggingEnabled", SecurityControl.WAF_PROTECTION);
        map.put("PCI.DSS.321-WAFv2LoggingEnabled", SecurityControl.WAF_PROTECTION);

        // ========== BACKUP_RECOVERY ==========
        // RDS backups
        map.put("AwsSolutions-RDS-9", SecurityControl.BACKUP_RECOVERY);
        map.put("AwsSolutions-RDS-14", SecurityControl.BACKUP_RECOVERY);
        map.put("HIPAA.Security-RDSAutomaticBackupEnabled", SecurityControl.BACKUP_RECOVERY);
        map.put("PCI.DSS.321-RDSAutomaticBackupEnabled", SecurityControl.BACKUP_RECOVERY);

        // DynamoDB backups
        map.put("AwsSolutions-DDB-2", SecurityControl.BACKUP_RECOVERY);
        map.put("HIPAA.Security-DynamoDBPITREnabled", SecurityControl.BACKUP_RECOVERY);
        map.put("PCI.DSS.321-DynamoDBPITREnabled", SecurityControl.BACKUP_RECOVERY);

        // S3 versioning
        map.put("AwsSolutions-S3-3", SecurityControl.BACKUP_RECOVERY);
        map.put("HIPAA.Security-S3BucketVersioningEnabled", SecurityControl.BACKUP_RECOVERY);
        map.put("PCI.DSS.321-S3BucketVersioningEnabled", SecurityControl.BACKUP_RECOVERY);

        // ========== HIGH_AVAILABILITY ==========
        // RDS Multi-AZ
        map.put("AwsSolutions-RDS-1", SecurityControl.HIGH_AVAILABILITY);
        map.put("HIPAA.Security-RDSMultiAZSupport", SecurityControl.HIGH_AVAILABILITY);
        map.put("PCI.DSS.321-RDSMultiAZSupport", SecurityControl.HIGH_AVAILABILITY);

        // ELB deletion protection
        map.put("AwsSolutions-ELB-4", SecurityControl.HIGH_AVAILABILITY);
        map.put("HIPAA.Security-ELBDeletionProtection", SecurityControl.HIGH_AVAILABILITY);

        // Lambda DLQ
        map.put("AwsSolutions-L-4", SecurityControl.HIGH_AVAILABILITY);
        map.put("HIPAA.Security-LambdaDLQ", SecurityControl.HIGH_AVAILABILITY);

        // ========== CHANGE_MANAGEMENT ==========
        // CodeBuild privileged mode
        map.put("AwsSolutions-CB-3", SecurityControl.CHANGE_MANAGEMENT);
        map.put("AwsSolutions-CB-4", SecurityControl.CHANGE_MANAGEMENT);

        // ========== VULNERABILITY_MANAGEMENT ==========
        // ECR vulnerability scanning
        map.put("AwsSolutions-ECR-3", SecurityControl.VULNERABILITY_MANAGEMENT);
        map.put("HIPAA.Security-ECRImageScanning", SecurityControl.VULNERABILITY_MANAGEMENT);

        // Lambda runtime
        map.put("AwsSolutions-L-10", SecurityControl.VULNERABILITY_MANAGEMENT);
        map.put("HIPAA.Security-LambdaConcurrency", SecurityControl.VULNERABILITY_MANAGEMENT);

        return Collections.unmodifiableMap(map);
    }

    /**
     * Maps a cdk-nag rule ID to the corresponding CloudForge SecurityControl.
     *
     * @param ruleId the cdk-nag rule ID (e.g., "AwsSolutions-S3-2")
     * @return Optional containing the SecurityControl, or empty if no mapping exists
     */
    public static Optional<SecurityControl> mapRuleToControl(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(RULE_TO_CONTROL.get(ruleId.trim()));
    }

    /**
     * Gets all cdk-nag rules mapped to a specific SecurityControl.
     *
     * @param control the SecurityControl to lookup
     * @return List of rule IDs that map to this control
     */
    public static List<String> getRulesForControl(SecurityControl control) {
        if (control == null) {
            return Collections.emptyList();
        }

        List<String> rules = new ArrayList<>();
        for (Map.Entry<String, SecurityControl> entry : RULE_TO_CONTROL.entrySet()) {
            if (entry.getValue() == control) {
                rules.add(entry.getKey());
            }
        }
        return rules;
    }

    /**
     * Gets all supported cdk-nag rule IDs.
     *
     * @return Set of all rule IDs with mappings
     */
    public static Set<String> getAllMappedRules() {
        return Collections.unmodifiableSet(RULE_TO_CONTROL.keySet());
    }

    /**
     * Checks if a cdk-nag rule has a mapping to a SecurityControl.
     *
     * @param ruleId the cdk-nag rule ID
     * @return true if mapping exists, false otherwise
     */
    public static boolean hasMappingForRule(String ruleId) {
        return ruleId != null && RULE_TO_CONTROL.containsKey(ruleId.trim());
    }

    /**
     * Gets statistics about the mapping coverage.
     *
     * @return Map containing mapping statistics
     */
    public static Map<String, Object> getMappingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRulesMapped", RULE_TO_CONTROL.size());
        stats.put("totalSecurityControls", SecurityControl.values().length);

        Map<SecurityControl, Integer> rulesByControl = new HashMap<>();
        for (SecurityControl control : RULE_TO_CONTROL.values()) {
            rulesByControl.merge(control, 1, Integer::sum);
        }
        stats.put("rulesByControl", rulesByControl);

        return stats;
    }

    /**
     * Generates a human-readable report of the mapping coverage.
     *
     * @return Formatted report string
     */
    public static String generateMappingReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        report.append("║       CDK-NAG to CloudForge Control Mapping Report            ║\n");
        report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

        report.append("Total cdk-nag rules mapped: ").append(RULE_TO_CONTROL.size()).append("\n");
        report.append("Total SecurityControls: ").append(SecurityControl.values().length).append("\n\n");

        report.append("Rules by SecurityControl:\n");
        report.append("─".repeat(60)).append("\n");

        Map<SecurityControl, Integer> rulesByControl = new HashMap<>();
        for (SecurityControl control : RULE_TO_CONTROL.values()) {
            rulesByControl.merge(control, 1, Integer::sum);
        }

        for (SecurityControl control : SecurityControl.values()) {
            int count = rulesByControl.getOrDefault(control, 0);
            String status = count > 0 ? "✓" : "✗";
            report.append(String.format("  %s %-30s : %3d rules\n", status, control.name(), count));
        }

        report.append("\n");
        return report.toString();
    }
}
