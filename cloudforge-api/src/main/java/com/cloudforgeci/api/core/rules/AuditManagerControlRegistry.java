package com.cloudforgeci.api.core.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central registry mapping infrastructure controls to multiple compliance frameworks.
 *
 * <p>This registry bridges the disconnect between:</p>
 * <ul>
 *   <li>Validation rules (Soc2Rules, PciDssRules, HipaaRules, GdprRules)</li>
 *   <li>AWS Config rules created in ComplianceFactory</li>
 *   <li>AWS Audit Manager control sets and evidence collection</li>
 * </ul>
 *
 * <p>Each control can map to multiple frameworks simultaneously. For example,
 * encryption at rest applies to SOC2 (CC6.1), PCI-DSS (Req3.4), HIPAA (164.312),
 * and GDPR (Art.32).</p>
 *
 * <h2>Usage in ComplianceFactory</h2>
 * <pre>{@code
 * // Get all Config rules needed for PCI-DSS framework
 * List<String> pciConfigRules = AuditManagerControlRegistry.getConfigRulesForFramework("PCI-DSS");
 *
 * // Get control details for evidence mapping
 * AuditManagerControl encryptionControl = AuditManagerControlRegistry.getControl("ENCRYPTION_AT_REST");
 * }</pre>
 */
public final class AuditManagerControlRegistry {

    private static final Map<String, AuditManagerControl> CONTROLS = new HashMap<>();

    static {
        registerAllControls();
    }

    private AuditManagerControlRegistry() {}

    /**
     * Register all infrastructure controls with their framework mappings.
     */
    private static void registerAllControls() {
        // Encryption at Rest
        register(new AuditManagerControl(
            "ENCRYPTION_AT_REST",
            "Encryption of data at rest (EBS, EFS, S3)",
            List.of("EbsEncryptionRule", "S3BucketEncryptionRule"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req3.4", "Render PAN unreadable"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(a)(2)(iv)", "Encryption and Decryption"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.1", "Logical and Physical Access Controls"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(a)", "Pseudonymization and Encryption")
            ),
            List.of("config", "cloudtrail")
        ));

        // Encryption in Transit
        register(new AuditManagerControl(
            "ENCRYPTION_IN_TRANSIT",
            "Encryption of data in transit (TLS/SSL)",
            List.of("ALBHttpsOnly", "CloudFrontViewerProtocolPolicy"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req4.1", "Encrypt transmission of cardholder data"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(e)(2)(ii)", "Transmission Encryption"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.7", "Data Transmission Security"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(a)", "Encryption of Personal Data")
            ),
            List.of("config", "cloudtrail")
        ));

        // Network Segmentation
        register(new AuditManagerControl(
            "NETWORK_SEGMENTATION",
            "Network segmentation (VPC, security groups)",
            List.of("VpcDefaultSecurityGroupClosed", "RestrictedIncomingTraffic"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req1.2.1", "Restrict inbound/outbound traffic"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req1.3", "Prohibit direct public access"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(e)(1)", "Network Controls"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.6", "Network Segmentation"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(b)", "Confidentiality")
            ),
            List.of("config", "cloudtrail", "vpc-flowlogs")
        ));

        // Access Control (IAM)
        register(new AuditManagerControl(
            "ACCESS_CONTROL",
            "Role-based access control (IAM, least privilege)",
            List.of("IAMPasswordPolicyRule", "IAMRootAccessKeyRule", "IAMUserNoPolicies"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req7.1", "Limit access by business need to know"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req7.2", "Access control system"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(a)(1)", "Access Control"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(4)", "Information Access Management"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.1", "Logical Access Controls"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.2", "Access Management"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(b)", "Confidentiality")
            ),
            List.of("config", "cloudtrail", "iam")
        ));

        // Authentication (MFA, SSO)
        register(new AuditManagerControl(
            "AUTHENTICATION",
            "User authentication (SSO, OIDC, MFA)",
            List.of("IAMMfaEnabled", "RootAccountMfaEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req8.2", "Ensure proper user authentication"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req8.3", "Multi-factor authentication"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(d)", "Person or Entity Authentication"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.2", "User Authentication"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(b)", "Ability to ensure confidentiality")
            ),
            List.of("config", "cloudtrail", "iam")
        ));

        // Audit Logging
        register(new AuditManagerControl(
            "AUDIT_LOGGING",
            "Audit logging with CloudTrail, VPC Flow Logs, and ALB access logs",
            List.of("CloudTrailEnabledRule", "CloudTrailLogFileValidationRule", "VpcFlowLogsEnabled", "AlbAccessLogsEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req10.1", "Implement audit trails"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req10.2", "Automated audit trails"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(b)", "Audit Controls"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.2", "System Monitoring"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.30", "Records of Processing Activities")
            ),
            List.of("cloudtrail", "vpc-flowlogs", "s3")
        ));

        // Log Retention
        register(new AuditManagerControl(
            "LOG_RETENTION",
            "Long-term log retention (1-6 years based on framework)",
            List.of("CloudWatchLogGroupRetention", "S3BucketLifecyclePolicy"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req10.7", "Retain audit trail for at least one year"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.316(b)(2)(i)", "Retain documentation for 6 years"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.2", "Log retention for forensic analysis"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.30", "Maintain processing records")
            ),
            List.of("cloudtrail", "cloudwatch-logs", "s3")
        ));

        // Security Monitoring
        register(new AuditManagerControl(
            "SECURITY_MONITORING",
            "Continuous security monitoring (GuardDuty, CloudWatch, AWS Config)",
            List.of("GuardDutyEnabled", "SecurityHubEnabled", "ConfigEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11.4", "Intrusion detection/prevention"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11.5", "File integrity monitoring"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(1)(ii)(D)", "Information System Activity Review"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.2", "System Monitoring for Anomalies"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(d)", "Regular testing and evaluation")
            ),
            List.of("config", "guardduty", "securityhub", "cloudwatch")
        ));

        // Threat Detection
        register(new AuditManagerControl(
            "THREAT_DETECTION",
            "Threat detection system (GuardDuty)",
            List.of("GuardDutyEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11.4", "Use intrusion detection systems"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(1)(ii)(D)", "Security incident procedures"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.2", "Threat Detection"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.33(1)", "Breach Detection")
            ),
            List.of("guardduty", "cloudtrail", "vpc-flowlogs")
        ));

        // WAF Protection
        register(new AuditManagerControl(
            "WAF_PROTECTION",
            "Web Application Firewall (AWS WAF)",
            List.of("WafEnabled", "WafRegionalRuleGroupPresent"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req6.6", "Public-facing web applications protected"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(e)(1)", "Transmission security mechanisms"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.6", "Web application protection"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)", "Appropriate security measures")
            ),
            List.of("config", "waf", "cloudwatch")
        ));

        // Backup and Recovery
        register(new AuditManagerControl(
            "BACKUP_RECOVERY",
            "Automated backup and disaster recovery",
            List.of("EfsBackupEnabled", "DynamoDbBackupEnabled", "RdsBackupEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req9.5.1", "Store backup media in secure location"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.310(d)(2)(iii)", "Data Backup and Storage"),
                new AuditManagerControl.FrameworkControl("SOC2", "A1.3", "Recovery capabilities"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(c)", "Restore availability and access")
            ),
            List.of("config", "backup", "s3")
        ));

        // High Availability
        register(new AuditManagerControl(
            "HIGH_AVAILABILITY",
            "High availability configuration (Multi-AZ, auto-scaling)",
            List.of("RdsMultiAzEnabled", "ElbCrossZoneEnabled", "AutoScalingGroupMultiAz"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req12.10.4", "Provide coverage for critical systems"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(7)(ii)(B)", "Disaster recovery plan"),
                new AuditManagerControl.FrameworkControl("SOC2", "A1.2", "Maintain system availability"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(b)", "Ensure resilience of systems")
            ),
            List.of("config", "cloudwatch", "autoscaling")
        ));

        // Change Management
        register(new AuditManagerControl(
            "CHANGE_MANAGEMENT",
            "Infrastructure as Code and change tracking",
            List.of("CloudTrailEnabledRule", "ConfigEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req6.4.5", "Implement change control procedures"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(8)", "Evaluation of security measures"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC8.1", "Change Management Process"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(d)", "Process for regular testing")
            ),
            List.of("cloudtrail", "config", "cloudformation")
        ));

        // Vulnerability Management
        register(new AuditManagerControl(
            "VULNERABILITY_MANAGEMENT",
            "Configuration compliance monitoring (AWS Config)",
            List.of("ConfigEnabled", "SecurityHubEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req6.2", "Ensure systems protected from known vulnerabilities"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11.2", "Run internal and external scans"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(8)", "Periodic evaluation"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.1", "Vulnerability detection and remediation"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(d)", "Regular testing effectiveness")
            ),
            List.of("config", "inspector", "securityhub")
        ));

        // Key Management
        register(new AuditManagerControl(
            "KEY_MANAGEMENT",
            "Cryptographic key management (KMS rotation, Secrets Manager)",
            List.of("KmsKeyRotationEnabled", "SecretsManagerInUse", "SecretsManagerRotation"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req3.5", "Document and implement key-management processes"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req3.6", "Fully document and implement key-management processes and procedures"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(a)(2)(iv)", "Encryption and decryption key management"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.1", "Encryption key protection"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(a)", "Encryption key management")
            ),
            List.of("kms", "secretsmanager", "cloudtrail")
        ));

        // Certificate Management
        register(new AuditManagerControl(
            "CERTIFICATE_MANAGEMENT",
            "TLS/SSL certificate lifecycle management",
            List.of("CertificateExpirationAlarm", "ALBHttpsOnly"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req4.1", "Use strong cryptography and security protocols"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(e)(2)(i)", "Implement encryption mechanisms"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.7", "Data transmission security"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(a)", "Encryption of personal data in transit")
            ),
            List.of("acm", "cloudwatch", "config")
        ));

        // Vendor Default Security
        register(new AuditManagerControl(
            "VENDOR_DEFAULTS",
            "Vendor-supplied default security configuration changes",
            List.of("Ec2InstanceDetailedMonitoring", "SecurityGroupDefaultRuleCheck"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req2.1", "Change vendor-supplied defaults"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req2.2", "Develop configuration standards"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req2.3", "Encrypt non-console administrative access"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(3)(ii)(A)", "Unique user identification"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.1", "System security configuration")
            ),
            List.of("config", "systems-manager", "cloudtrail")
        ));

        // Database Security
        register(new AuditManagerControl(
            "DATABASE_SECURITY",
            "Database encryption, backup, and monitoring",
            List.of("RdsEncryptionAtRestEnabled", "RdsBackupEnabled", "RdsMultiAzEnabled", "DynamoDbEncryptionEnabled", "DynamoDbPitrEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req3.4", "Render PAN unreadable in databases"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req8.7", "Database access secured"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(a)(2)(iv)", "Database encryption"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.310(d)", "Database backup and storage"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC6.1", "Database protection"),
                new AuditManagerControl.FrameworkControl("SOC2", "A1.3", "Database backup and recovery"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32", "Database security measures"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.25", "Data protection by design")
            ),
            List.of("rds", "dynamodb", "config", "cloudwatch")
        ));

        // Advanced Monitoring (Security Hub, Inspector, Macie)
        register(new AuditManagerControl(
            "ADVANCED_MONITORING",
            "Advanced security monitoring and compliance dashboard",
            List.of("SecurityHubEnabled", "InspectorEnabled", "MacieEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req10", "Track and monitor all access to network resources"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11", "Regularly test security systems"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(1)(ii)(D)", "Information system activity review"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.2", "System monitoring"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.3", "Threat detection"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(1)(d)", "Regular testing and evaluation"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.33(1)", "Breach detection")
            ),
            List.of("securityhub", "inspector", "macie", "cloudwatch")
        ));

        // HIPAA Organizational Controls
        register(new AuditManagerControl(
            "HIPAA_ORGANIZATIONAL",
            "HIPAA Business Associate Agreements and organizational safeguards",
            List.of("BaaDocumented", "WorkforceSecurityProcedures", "BreachNotificationProcedures"),
            List.of(
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(b)(1)", "Business associate contracts"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.314(a)", "Business associate contract provisions"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(3)", "Workforce security"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(6)", "Security incident procedures"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.410", "Breach notification")
            ),
            List.of("documentation", "procedures", "cloudtrail")
        ));

        // GDPR Data Protection
        register(new AuditManagerControl(
            "GDPR_DATA_PROTECTION",
            "GDPR lawfulness, data subject rights, and DPIA",
            List.of("LegalBasisDocumented", "DataSubjectRightsProcedures", "DpiaCompleted"),
            List.of(
                new AuditManagerControl.FrameworkControl("GDPR", "Art.6", "Lawfulness of processing"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.7", "Conditions for consent"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.15-22", "Data subject rights"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.25", "Data protection by design and default"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.30", "Records of processing activities"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.35", "Data protection impact assessment"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.46", "International data transfers")
            ),
            List.of("documentation", "procedures", "macie")
        ));

        // Incident Response and Disaster Recovery
        register(new AuditManagerControl(
            "INCIDENT_RESPONSE",
            "Incident response plan, disaster recovery, and business continuity",
            List.of("IncidentResponsePlanDocumented", "DisasterRecoveryTested", "CloudTrailLogFileValidationRule"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req12.10", "Incident response plan"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req12.10.4", "Business continuity and disaster recovery"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(6)", "Security incident procedures"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(7)(ii)(B)", "Disaster recovery plan"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.4", "Incident response"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.5", "Incident resolution"),
                new AuditManagerControl.FrameworkControl("SOC2", "A1.2", "System availability and recovery"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.33", "Breach notification within 72 hours")
            ),
            List.of("cloudtrail", "cloudwatch", "backup", "documentation")
        ));

        // Threat Protection (Malware, Intrusion Detection)
        register(new AuditManagerControl(
            "THREAT_PROTECTION",
            "Anti-malware, intrusion detection, and file integrity monitoring",
            List.of("GuardDutyEnabled", "WafEnabled", "VpcFlowLogsEnabled", "ConfigEnabled"),
            List.of(
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req5", "Protect systems against malware"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11.4", "Intrusion detection and prevention"),
                new AuditManagerControl.FrameworkControl("PCI-DSS", "Req11.5", "File integrity monitoring"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.308(a)(5)(ii)(B)", "Protection from malicious software"),
                new AuditManagerControl.FrameworkControl("HIPAA", "164.312(e)(1)", "Transmission security mechanisms"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.2", "Threat detection and monitoring"),
                new AuditManagerControl.FrameworkControl("SOC2", "CC7.3", "Threat response"),
                new AuditManagerControl.FrameworkControl("GDPR", "Art.32(2)", "Regular security testing")
            ),
            List.of("guardduty", "waf", "vpc-flowlogs", "config", "inspector")
        ));
    }

    private static void register(AuditManagerControl control) {
        CONTROLS.put(control.controlId(), control);
    }

    /**
     * Get a control by its ID.
     */
    public static AuditManagerControl getControl(String controlId) {
        return CONTROLS.get(controlId);
    }

    /**
     * Get all controls.
     */
    public static List<AuditManagerControl> getAllControls() {
        return new ArrayList<>(CONTROLS.values());
    }

    /**
     * Get all controls that apply to a specific framework.
     */
    public static List<AuditManagerControl> getControlsForFramework(String framework) {
        return CONTROLS.values().stream()
            .filter(control -> control.appliesToFramework(framework))
            .collect(Collectors.toList());
    }

    /**
     * Get all AWS Config rule IDs needed for a specific framework.
     * This tells ComplianceFactory which Config rules to create.
     */
    public static List<String> getConfigRulesForFramework(String framework) {
        return CONTROLS.values().stream()
            .filter(control -> control.appliesToFramework(framework))
            .flatMap(control -> control.configRuleIds().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Get all evidence sources needed for a specific framework.
     * This tells ComplianceFactory which data sources to configure for Audit Manager.
     */
    public static List<String> getEvidenceSourcesForFramework(String framework) {
        return CONTROLS.values().stream()
            .filter(control -> control.appliesToFramework(framework))
            .flatMap(control -> control.evidenceSources().stream())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Get framework control mapping for evidence documentation.
     */
    public static Map<String, List<String>> getFrameworkControlMap(String framework) {
        Map<String, List<String>> controlMap = new HashMap<>();

        for (AuditManagerControl control : CONTROLS.values()) {
            control.getFrameworkControl(framework).ifPresent(fc -> {
                String key = fc.controlId() + " - " + fc.controlName();
                controlMap.computeIfAbsent(key, k -> new ArrayList<>())
                    .addAll(control.configRuleIds());
            });
        }

        return controlMap;
    }
}
