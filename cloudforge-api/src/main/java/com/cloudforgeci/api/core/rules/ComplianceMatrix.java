package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforgeci.api.core.SystemContext;

import java.util.*;
import java.util.logging.Logger;

/**
 * Multi-framework compliance control mapping matrix.
 *
 * Maps CloudForge CI security controls to requirements across multiple compliance frameworks:
 * - PCI-DSS v3.2.1
 * - HIPAA Security Rule
 * - SOC 2 Trust Services Criteria
 * - GDPR (General Data Protection Regulation)
 * - NIST SP 800-53
 * - FedRamp Moderate/High
 *
 * This matrix helps organizations understand which infrastructure controls satisfy
 * requirements across multiple frameworks, reducing audit burden and demonstrating
 * comprehensive security coverage.
 *
 * <p>Each control maps to framework requirements with enforcement levels:
 * <ul>
 *   <li>REQUIRED - Must be implemented for framework compliance</li>
 *   <li>ADVISORY - Recommended but alternative controls acceptable</li>
 *   <li>NOT_APPLICABLE - Not relevant to this framework</li>
 * </ul>
 *
 * <p>Validation behavior depends on complianceMode:
 * <ul>
 *   <li>ENFORCE - REQUIRED controls block deployment, ADVISORY controls warn</li>
 *   <li>ADVISORY - All violations logged as warnings only</li>
 *   <li>DISABLED - No validation performed</li>
 * </ul>
 */
public final class ComplianceMatrix {
    private static final Logger LOG = Logger.getLogger(ComplianceMatrix.class.getName());

    private ComplianceMatrix() {}

    /**
     * Requirement enforcement level for a control within a compliance framework.
     */
    public enum RequirementLevel {
        /** Must be implemented - enforced in ENFORCE mode, warnings in ADVISORY mode */
        REQUIRED,

        /** Recommended but not mandatory - always advisory regardless of mode */
        ADVISORY,

        /** Not applicable to this framework */
        NOT_APPLICABLE
    }

    /**
     * Framework-specific requirement with enforcement level.
     */
    public record FrameworkRequirement(
        String citation,
        RequirementLevel level
    ) {
        public static FrameworkRequirement required(String citation) {
            return new FrameworkRequirement(citation, RequirementLevel.REQUIRED);
        }

        public static FrameworkRequirement advisory(String citation) {
            return new FrameworkRequirement(citation, RequirementLevel.ADVISORY);
        }

        public static FrameworkRequirement notApplicable() {
            return new FrameworkRequirement("N/A", RequirementLevel.NOT_APPLICABLE);
        }
    }

    /**
     * Security control definitions mapped to framework requirements.
     */
    public enum SecurityControl {
        ENCRYPTION_AT_REST(
            "Encryption of data at rest (EBS, EFS, S3)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 3.4 - Render PAN unreadable"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(2)(iv) - Encryption and Decryption"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Logical and Physical Access Controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Pseudonymization and Encryption"),
                "NIST", FrameworkRequirement.required("SC-28 - Protection of Information at Rest")
            )
        ),

        ENCRYPTION_IN_TRANSIT(
            "Encryption of data in transit (TLS/SSL, EFS)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 4.1 - Encrypt transmission of cardholder data"),
                "HIPAA", FrameworkRequirement.required("§164.312(e)(1) - Transmission Security"),
                "SOC2", FrameworkRequirement.required("CC6.7 - Data Transmission Security"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Encryption of Personal Data"),
                "NIST", FrameworkRequirement.required("SC-8 - Transmission Confidentiality and Integrity")
            )
        ),

        HTTPS_STRICT(
            "HTTPS-only mode (disable HTTP listener when SSL enabled)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 4.1 - Strong cryptography for transmission"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(e)(2)(ii) - Encryption mechanism"),
                "SOC2", FrameworkRequirement.advisory("CC6.7 - Minimize attack surface"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(a) - State of the art encryption"),
                "NIST", FrameworkRequirement.required("SC-8(1) - Cryptographic Protection")
            )
        ),

        NETWORK_SEGMENTATION(
            "Network segmentation (VPC, private subnets, security groups)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 1.3 - Prohibit direct public access to cardholder data"),
                "HIPAA", FrameworkRequirement.required("§164.312(e)(1) - Network Controls"),
                "SOC2", FrameworkRequirement.required("CC6.6 - Network Segmentation"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Confidentiality"),
                "NIST", FrameworkRequirement.required("SC-7 - Boundary Protection")
            )
        ),

        ACCESS_CONTROL(
            "Role-based access control (IAM, least privilege)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 7.1 - Limit access by business need to know"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(1) - Access Control"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Logical Access Controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Confidentiality"),
                "NIST", FrameworkRequirement.required("AC-3 - Access Enforcement")
            )
        ),

        AUTHENTICATION(
            "User authentication (SSO, OIDC, MFA)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.3 - Multi-factor authentication"),
                "HIPAA", FrameworkRequirement.required("§164.312(d) - Person or Entity Authentication"),
                "SOC2", FrameworkRequirement.required("CC6.2 - User Authentication"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Ability to ensure confidentiality"),
                "NIST", FrameworkRequirement.required("IA-2 - Identification and Authentication")
            )
        ),

        AUDIT_LOGGING(
            "Comprehensive audit logging (CloudTrail, Flow Logs, ALB logs)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.2 - Automated audit trails"),
                "HIPAA", FrameworkRequirement.required("§164.312(b) - Audit Controls"),
                "SOC2", FrameworkRequirement.required("CC7.2 - System Monitoring"),
                "GDPR", FrameworkRequirement.required("Art. 30 - Records of Processing Activities"),
                "NIST", FrameworkRequirement.required("AU-2 - Audit Events")
            )
        ),

        LOG_RETENTION(
            "Long-term log retention (2-6 years)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.7 - Retain audit trail for at least one year"),
                "HIPAA", FrameworkRequirement.required("§164.316(b)(2)(i) - Retain documentation for 6 years"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Log retention for forensic analysis"),
                "GDPR", FrameworkRequirement.advisory("Art. 30 - Maintain processing records"),
                "NIST", FrameworkRequirement.required("AU-11 - Audit Record Retention")
            )
        ),

        SECURITY_MONITORING(
            "Continuous security monitoring (CloudWatch, AWS Config)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.5 - File integrity monitoring"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(1)(ii)(D) - Information System Activity Review"),
                "SOC2", FrameworkRequirement.advisory("CC7.2 - System Monitoring for Anomalies"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Regular testing and evaluation"),
                "NIST", FrameworkRequirement.required("SI-4 - Information System Monitoring")
            )
        ),

        THREAT_DETECTION(
            "Threat detection system (GuardDuty)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.4 - Use intrusion detection/prevention systems"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(1)(ii)(D) - Security incident procedures"),
                "SOC2", FrameworkRequirement.advisory("CC7.2 - Threat Detection"),
                "GDPR", FrameworkRequirement.advisory("Art. 33(1) - Breach Detection"),
                "NIST", FrameworkRequirement.required("SI-4 - Information System Monitoring")
            )
        ),

        SECURITY_HUB(
            "Centralized security findings (AWS Security Hub)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 11.4 - Centralized monitoring"),
                "HIPAA", FrameworkRequirement.advisory("§164.308(a)(1)(ii)(D) - Centralized security monitoring"),
                "SOC2", FrameworkRequirement.advisory("CC7.3 - Centralized security monitoring"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(b) - Centralized security posture"),
                "NIST", FrameworkRequirement.advisory("SI-4 - Information System Monitoring")
            )
        ),

        VULNERABILITY_SCANNING(
            "Vulnerability scanning (AWS Inspector)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.2 - Run internal and external vulnerability scans"),
                "HIPAA", FrameworkRequirement.advisory("§164.308(a)(8) - Periodic evaluation"),
                "SOC2", FrameworkRequirement.advisory("CC7.1 - Vulnerability detection"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(d) - Regular testing"),
                "NIST", FrameworkRequirement.required("RA-5 - Vulnerability Scanning")
            )
        ),

        SENSITIVE_DATA_DISCOVERY(
            "Sensitive data discovery (AWS Macie)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 3 - Cardholder data discovery"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(1)(ii)(A) - PHI identification"),
                "SOC2", FrameworkRequirement.notApplicable(),
                "GDPR", FrameworkRequirement.required("Art. 30 - Personal data inventory"),
                "NIST", FrameworkRequirement.advisory("SI-4 - Information System Monitoring")
            )
        ),

        WAF_PROTECTION(
            "Web Application Firewall (AWS WAF)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 6.6 - Public-facing web applications protected"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(e)(1) - Transmission security mechanisms"),
                "SOC2", FrameworkRequirement.required("CC6.6 - Web application protection"),
                "GDPR", FrameworkRequirement.required("Art. 32(1) - Appropriate security measures"),
                "NIST", FrameworkRequirement.required("SC-7(11) - Boundary Protection")
            )
        ),

        BACKUP_RECOVERY(
            "Automated backup and disaster recovery",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 9.5.1 - Store backup media in secure location"),
                "HIPAA", FrameworkRequirement.required("§164.310(d)(2)(iii) - Data Backup and Storage"),
                "SOC2", FrameworkRequirement.required("A1.3 - Recovery capabilities"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(c) - Restore availability and access"),
                "NIST", FrameworkRequirement.required("CP-9 - Information System Backup")
            )
        ),

        HIGH_AVAILABILITY(
            "High availability configuration (Multi-AZ, auto-scaling)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 12.10.4 - Provide coverage for critical systems"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(7)(ii)(B) - Disaster recovery plan"),
                "SOC2", FrameworkRequirement.required("A1.2 - Maintain system availability"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Ensure resilience of systems"),
                "NIST", FrameworkRequirement.required("CP-2 - Contingency Plan")
            )
        ),

        CHANGE_MANAGEMENT(
            "Infrastructure as Code and change tracking",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 6.4.5 - Implement change control procedures"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(8) - Evaluation of security measures"),
                "SOC2", FrameworkRequirement.required("CC8.1 - Change Management Process"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Process for regular testing"),
                "NIST", FrameworkRequirement.required("CM-3 - Configuration Change Control")
            )
        ),

        VULNERABILITY_MANAGEMENT(
            "Configuration compliance monitoring (AWS Config)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 11.2 - Run internal and external scans"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(8) - Periodic evaluation"),
                "SOC2", FrameworkRequirement.required("CC7.1 - Vulnerability detection and remediation"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Regular testing and evaluating effectiveness"),
                "NIST", FrameworkRequirement.required("RA-5 - Vulnerability Scanning")
            )
        ),

        KMS_KEY_ROTATION(
            "Automatic KMS key rotation (annual)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 3.6.4 - Cryptoperiod rotation"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(2)(iv) - Encryption key management"),
                "SOC2", FrameworkRequirement.advisory("CC6.1 - Encryption key rotation"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Key management"),
                "NIST", FrameworkRequirement.required("SC-12 - Cryptographic Key Management")
            )
        ),

        CERTIFICATE_EXPIRATION_MONITORING(
            "Certificate expiration monitoring (CloudWatch alarms)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 6.3.3 - Certificate management"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(e)(1) - Certificate lifecycle"),
                "SOC2", FrameworkRequirement.advisory("CC6.7 - Certificate management"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1) - Availability assurance"),
                "NIST", FrameworkRequirement.advisory("IA-5 - Authenticator Management")
            )
        ),

        SECRETS_MANAGER(
            "Secrets Manager for credentials (databases, API keys)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.2.1 - Strong authentication credentials"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(1) - Access credential management"),
                "SOC2", FrameworkRequirement.advisory("CC6.1 - Credential storage"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1) - Secure credential storage"),
                "NIST", FrameworkRequirement.required("IA-5 - Authenticator Management")
            )
        ),

        SECRETS_ROTATION(
            "Automatic secrets rotation (90 days or less)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.2.4 - Change user passwords at least every 90 days"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(5)(ii)(D) - Password management"),
                "SOC2", FrameworkRequirement.advisory("CC6.1 - Credential rotation"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1) - Credential lifecycle"),
                "NIST", FrameworkRequirement.required("IA-5(1) - Password-based Authentication")
            )
        ),

        DATABASE_MULTI_AZ(
            "Database Multi-AZ deployment (RDS, Aurora)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 12.10.4 - Critical system availability"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(7)(ii)(B) - Disaster recovery"),
                "SOC2", FrameworkRequirement.required("A1.2 - System availability"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - System resilience"),
                "NIST", FrameworkRequirement.required("CP-6 - Alternate Storage Site")
            )
        ),

        DATABASE_PITR(
            "Database point-in-time recovery (DynamoDB, RDS)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 9.5.1 - Backup and restore capability"),
                "HIPAA", FrameworkRequirement.required("§164.310(d)(2)(iii) - Data backup and recovery"),
                "SOC2", FrameworkRequirement.required("A1.3 - Recovery capabilities"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(c) - Restore data availability"),
                "NIST", FrameworkRequirement.required("CP-9 - Information System Backup")
            )
        ),

        NETWORK_FLOW_LOGS(
            "VPC Flow Logs for network traffic monitoring",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.2.2 - Log all network access"),
                "HIPAA", FrameworkRequirement.required("§164.312(b) - Audit network access"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Network monitoring"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Network monitoring"),
                "NIST", FrameworkRequirement.required("AU-2 - Audit Events")
            )
        ),

        AUDIT_MANAGER(
            "Continuous audit evidence collection (AWS Audit Manager)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 10 - Continuous audit evidence"),
                "HIPAA", FrameworkRequirement.advisory("§164.308(a)(1)(ii)(D) - Audit evidence"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Continuous monitoring and audit evidence"),
                "GDPR", FrameworkRequirement.advisory("Art. 30 - Documentation of compliance"),
                "NIST", FrameworkRequirement.required("AU-6 - Audit Review, Analysis, and Reporting")
            )
        ),

        CLOUDWATCH_LOGS_KMS_ENCRYPTION(
            "KMS encryption for CloudWatch Logs (at-rest encryption)",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 3.4 - Render sensitive data unreadable"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(2)(iv) - Encryption of PHI logs"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Encryption of audit logs"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Encryption of processing records"),
                "NIST", FrameworkRequirement.required("SC-28 - Protection of Information at Rest")
            )
        ),

        CLOUDTRAIL_INSIGHTS(
            "CloudTrail Insights for anomaly detection in API activity",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 10.6 - Review logs for anomalies"),
                "HIPAA", FrameworkRequirement.advisory("§164.308(a)(1)(ii)(D) - Activity review"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Anomaly detection in system activity"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(d) - Regular testing and evaluation"),
                "NIST", FrameworkRequirement.required("SI-4 - Information System Monitoring")
            )
        ),

        ROUTE53_QUERY_LOGGING(
            "Route53 DNS query logging for network visibility",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 10.2 - Log DNS resolution"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(b) - Audit DNS queries"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Network activity monitoring"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(d) - Network monitoring"),
                "NIST", FrameworkRequirement.required("AU-2 - Audit Events including DNS")
            )
        ),

        // ==================== Audit Trail Immutability ====================
        S3_OBJECT_LOCK(
            "S3 Object Lock for audit trail immutability",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.7 - Protect audit trail files"),
                "HIPAA", FrameworkRequirement.required("§164.312(c)(1) - Data integrity controls"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Audit trail protection"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(d) - Data integrity"),
                "NIST", FrameworkRequirement.required("AU-9 - Protection of Audit Information"),
                "FedRAMP", FrameworkRequirement.required("AU-9 - Protection of Audit Information")
            )
        ),

        // ==================== Root Account Protection ====================
        ROOT_ACCOUNT_PROTECTION(
            "Root account MFA and access key protection",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.3 - MFA for administrative access"),
                "HIPAA", FrameworkRequirement.required("§164.312(d) - Person or Entity Authentication"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Privileged access controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Administrative access security"),
                "NIST", FrameworkRequirement.required("IA-2(1) - Multi-factor Authentication to Privileged Accounts"),
                "FedRAMP", FrameworkRequirement.required("IA-2(1) - MFA for privileged accounts")
            )
        ),

        // ==================== Credential Rotation ====================
        CREDENTIAL_ROTATION(
            "Access key rotation and unused credential management",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.2.4 - Change credentials every 90 days"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(5)(ii)(D) - Password management"),
                "SOC2", FrameworkRequirement.required("CC6.2 - Credential lifecycle management"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(b) - Credential hygiene"),
                "NIST", FrameworkRequirement.required("IA-5(1) - Password-based Authentication rotation"),
                "FedRAMP", FrameworkRequirement.required("IA-5(1) - Authenticator management")
            )
        ),

        // ==================== Database Access Control ====================
        DATABASE_ACCESS_CONTROL(
            "Database public access restriction and IAM authentication",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 1.3 - Prohibit direct public access to databases"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(1) - Database access control"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Database access restrictions"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Database confidentiality"),
                "NIST", FrameworkRequirement.required("AC-3 - Access Enforcement for databases"),
                "FedRAMP", FrameworkRequirement.required("AC-3 - Database access control")
            )
        ),

        // ==================== Container Security ====================
        CONTAINER_SECURITY(
            "EKS cluster endpoint protection and secrets encryption",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 1.3/3.4 - Container network and secrets protection"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(2)(iv) - Container secrets encryption"),
                "SOC2", FrameworkRequirement.required("CC6.1/CC6.6 - Container access and network controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - Container data protection"),
                "NIST", FrameworkRequirement.required("SC-28/AC-3 - Container security controls"),
                "FedRAMP", FrameworkRequirement.required("SC-28 - Container secrets protection")
            )
        ),

        // ==================== API Security ====================
        API_SECURITY(
            "API Gateway logging, SSL enforcement, and WAF protection",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 4.1/6.6 - API encryption and protection"),
                "HIPAA", FrameworkRequirement.required("§164.312(e)(1) - API transmission security"),
                "SOC2", FrameworkRequirement.required("CC6.7 - API security controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - API data protection"),
                "NIST", FrameworkRequirement.required("SC-8/AU-2 - API security and logging"),
                "FedRAMP", FrameworkRequirement.required("SC-8 - API transmission protection")
            )
        ),

        // ==================== CDN Security ====================
        CDN_SECURITY(
            "CloudFront HTTPS enforcement and WAF association",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 4.1/6.6 - CDN encryption and WAF"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(e)(1) - CDN transmission security"),
                "SOC2", FrameworkRequirement.required("CC6.6/CC6.7 - CDN access and data protection"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(a) - CDN data protection"),
                "NIST", FrameworkRequirement.required("SC-7(11)/SC-8 - CDN boundary and transmission protection"),
                "FedRAMP", FrameworkRequirement.required("SC-8 - CDN transmission protection")
            )
        ),

        // ==================== Instance Metadata Security ====================
        INSTANCE_METADATA_SECURITY(
            "EC2 IMDSv2 enforcement for metadata service protection",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 2.2 - Secure system configuration"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(a)(1) - Instance access control"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Secure instance configuration"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(b) - Instance security"),
                "NIST", FrameworkRequirement.required("SC-28 - Protection of Information at Rest"),
                "FedRAMP", FrameworkRequirement.required("CM-6 - Configuration Settings")
            )
        ),

        // ==================== Certificate Management ====================
        CERTIFICATE_MANAGEMENT(
            "ACM certificate expiration monitoring and lifecycle management",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Req 6.3.3 - Certificate lifecycle management"),
                "HIPAA", FrameworkRequirement.advisory("§164.312(e)(1) - Certificate validity"),
                "SOC2", FrameworkRequirement.required("CC6.7/A1.2 - Certificate availability"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1) - Service availability"),
                "NIST", FrameworkRequirement.required("IA-5(2) - PKI-based Authentication"),
                "FedRAMP", FrameworkRequirement.required("IA-5(2) - Certificate management")
            )
        ),

        // ==================== Lambda Security ====================
        LAMBDA_SECURITY(
            "Lambda function public access restriction and VPC configuration",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 1.3/7.1 - Lambda network and access control"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(1) - Lambda access control"),
                "SOC2", FrameworkRequirement.required("CC6.1/CC6.6 - Lambda access and network controls"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(b) - Lambda function protection"),
                "NIST", FrameworkRequirement.required("AC-3/SC-7 - Lambda access and boundary protection"),
                "FedRAMP", FrameworkRequirement.required("AC-3 - Lambda access enforcement")
            )
        ),

        // ==================== RDS Logging ====================
        DATABASE_LOGGING(
            "RDS database audit logging and activity monitoring",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 10.2 - Database audit trails"),
                "HIPAA", FrameworkRequirement.required("§164.312(b) - Database audit controls"),
                "SOC2", FrameworkRequirement.required("CC7.2 - Database monitoring"),
                "GDPR", FrameworkRequirement.required("Art. 30 - Database processing records"),
                "NIST", FrameworkRequirement.required("AU-2 - Database audit events"),
                "FedRAMP", FrameworkRequirement.required("AU-2 - Database auditing")
            )
        ),

        // ==================== Deletion Protection ====================
        DELETION_PROTECTION(
            "RDS and resource deletion protection for data integrity",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 9.5.1 - Data protection controls"),
                "HIPAA", FrameworkRequirement.required("§164.310(d)(2)(iii) - Data integrity"),
                "SOC2", FrameworkRequirement.required("A1.2 - System availability protection"),
                "GDPR", FrameworkRequirement.required("Art. 32(1)(c) - Data availability"),
                "NIST", FrameworkRequirement.required("CP-9/SC-28 - Information protection"),
                "FedRAMP", FrameworkRequirement.required("CP-9 - Information system backup")
            )
        ),

        // ==================== SNS KMS Encryption ====================
        SNS_KMS_ENCRYPTION(
            "SNS topic encryption with customer-managed KMS keys",
            Map.of(
                "PCI-DSS", FrameworkRequirement.required("Req 8.2.1 - Data at rest encryption"),
                "HIPAA", FrameworkRequirement.required("§164.312(a)(2)(iv) - Encryption of ePHI"),
                "SOC2", FrameworkRequirement.advisory("CC6.1 - Message encryption"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(a) - Encryption of personal data"),
                "NIST", FrameworkRequirement.required("SC-28 - Protection of Information at Rest"),
                "FedRAMP", FrameworkRequirement.required("SC-28 - Information at rest")
            )
        ),

        // ==================== EC2 IMDSv2 ====================
        EC2_IMDSV2(
            "EC2 Instance Metadata Service Version 2 (IMDSv2) enforcement",
            Map.of(
                "PCI-DSS", FrameworkRequirement.advisory("Defense in depth for instance metadata"),
                "HIPAA", FrameworkRequirement.required("§164.308(a)(3)(i) - Access controls"),
                "SOC2", FrameworkRequirement.required("CC6.1 - Instance metadata protection"),
                "GDPR", FrameworkRequirement.advisory("Art. 32(1)(b) - Access security"),
                "NIST", FrameworkRequirement.required("AC-3 - Access Enforcement"),
                "FedRAMP", FrameworkRequirement.required("AC-3 - Access control enforcement")
            )
        );

        private final String description;
        private final Map<String, FrameworkRequirement> frameworkMappings;

        SecurityControl(String description, Map<String, FrameworkRequirement> frameworkMappings) {
            this.description = description;
            this.frameworkMappings = frameworkMappings;
        }

        public String getDescription() {
            return description;
        }

        public Map<String, FrameworkRequirement> getFrameworkMappings() {
            return frameworkMappings;
        }

        public FrameworkRequirement getRequirement(String framework) {
            return frameworkMappings.getOrDefault(framework, FrameworkRequirement.notApplicable());
        }

        public RequirementLevel getRequirementLevel(String framework) {
            return getRequirement(framework).level();
        }

        public boolean isRequired(String framework) {
            return getRequirementLevel(framework) == RequirementLevel.REQUIRED;
        }

        public boolean isAdvisory(String framework) {
            return getRequirementLevel(framework) == RequirementLevel.ADVISORY;
        }
    }

    /**
     * Generate a comprehensive compliance matrix report showing all controls
     * and their mappings across frameworks.
     */
    public static String generateMatrixReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        report.append("║  CloudForge CI Multi-Framework Compliance Control Matrix       ║\n");
        report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

        report.append("This matrix shows how CloudForge CI security controls map to\n");
        report.append("requirements across multiple compliance frameworks.\n\n");

        for (SecurityControl control : SecurityControl.values()) {
            report.append("─────────────────────────────────────────────────────────────────\n");
            report.append("Control: ").append(control.name()).append("\n");
            report.append("Description: ").append(control.getDescription()).append("\n\n");

            report.append("Framework Mappings:\n");
            for (Map.Entry<String, FrameworkRequirement> entry : control.getFrameworkMappings().entrySet()) {
                String levelBadge = switch (entry.getValue().level()) {
                    case REQUIRED -> "[REQUIRED]";
                    case ADVISORY -> "[ADVISORY]";
                    case NOT_APPLICABLE -> "[N/A]";
                };
                report.append("  ").append(String.format("%-10s", entry.getKey())).append(" │ ");
                report.append(String.format("%-12s", levelBadge)).append(" ");
                report.append(entry.getValue().citation());
                report.append("\n");
            }
            report.append("\n");
        }

        report.append("═════════════════════════════════════════════════════════════════\n");
        report.append("Total Controls: ").append(SecurityControl.values().length).append("\n");
        report.append("Frameworks Covered: PCI-DSS, HIPAA, SOC 2, GDPR, NIST SP 800-53\n");
        report.append("═════════════════════════════════════════════════════════════════\n\n");

        return report.toString();
    }

    /**
     * Generate a framework-specific requirements checklist.
     */
    public static String generateFrameworkChecklist(String framework) {
        StringBuilder report = new StringBuilder();
        report.append("\n").append(framework).append(" Requirements Coverage\n");
        report.append("═".repeat(50)).append("\n\n");

        Map<String, Set<SecurityControl>> requirementToControls = new HashMap<>();

        for (SecurityControl control : SecurityControl.values()) {
            FrameworkRequirement req = control.getRequirement(framework);
            if (req.level() != RequirementLevel.NOT_APPLICABLE) {
                requirementToControls.computeIfAbsent(req.citation(), k -> new HashSet<>()).add(control);
            }
        }

        List<String> sortedReqs = new ArrayList<>(requirementToControls.keySet());
        Collections.sort(sortedReqs);

        for (String requirement : sortedReqs) {
            report.append("✓ ").append(requirement).append("\n");
            report.append("  Implemented by:\n");
            for (SecurityControl control : requirementToControls.get(requirement)) {
                String levelBadge = control.getRequirement(framework).level() == RequirementLevel.REQUIRED
                    ? "[REQUIRED]" : "[ADVISORY]";
                report.append("    • ").append(control.name()).append(" ").append(levelBadge).append("\n");
                report.append("      ").append(control.getDescription()).append("\n");
            }
            report.append("\n");
        }

        report.append("Total requirements covered: ").append(sortedReqs.size()).append("\n\n");

        return report.toString();
    }

    /**
     * Validation result for a control check.
     */
    public enum ValidationResult {
        PASS,      // Control is compliant
        FAIL,      // Control violation - blocks deployment
        WARN       // Control violation - warning only
    }

    /**
     * Validates a control against framework requirements with complianceMode consideration.
     *
     * @param control Security control to validate
     * @param framework Compliance framework (e.g., "SOC2", "PCI-DSS")
     * @param isEnabled Whether the control is currently enabled
     * @param complianceMode Compliance enforcement mode
     * @return Validation result (PASS, FAIL, or WARN)
     */
    public static ValidationResult validateControl(
        SecurityControl control,
        String framework,
        boolean isEnabled,
        ComplianceMode complianceMode
    ) {
        if (complianceMode == ComplianceMode.DISABLED) {
            return ValidationResult.PASS;
        }

        RequirementLevel level = control.getRequirementLevel(framework);

        if (level == RequirementLevel.NOT_APPLICABLE) {
            return ValidationResult.PASS;
        }

        if (isEnabled) {
            return ValidationResult.PASS;
        }

        // Control is not enabled - determine enforcement based on mode and requirement level
        if (complianceMode == ComplianceMode.ADVISORY) {
            return ValidationResult.WARN;
        }

        // ENFORCE mode
        if (level == RequirementLevel.REQUIRED) {
            return ValidationResult.FAIL;
        } else {
            return ValidationResult.WARN;
        }
    }

    /**
     * Validates multiple frameworks against a control.
     *
     * @param control Security control to validate
     * @param frameworksStr Comma-separated list of frameworks (e.g., "SOC2,PCI-DSS")
     * @param isEnabled Whether the control is currently enabled
     * @param complianceMode Compliance enforcement mode
     * @return Worst validation result across all frameworks (FAIL > WARN > PASS)
     */
    public static ValidationResult validateControlMultiFramework(
        SecurityControl control,
        String frameworksStr,
        boolean isEnabled,
        ComplianceMode complianceMode
    ) {
        if (frameworksStr == null || frameworksStr.isEmpty()) {
            return ValidationResult.PASS;
        }

        ValidationResult worst = ValidationResult.PASS;

        // Support comma, space, and + as delimiters (consistent with ComplianceFrameworkType)
        for (String framework : frameworksStr.split("[,\\s+]+")) {
            // Normalize: uppercase + replace underscores with hyphens (PCI_DSS → PCI-DSS)
            // This handles both enum.name() (PCI_DSS) and enum.getMatrixKey() (PCI-DSS) inputs
            String normalized = framework.trim().toUpperCase().replace("_", "-");
            if (normalized.isEmpty()) continue;
            ValidationResult result = validateControl(control, normalized, isEnabled, complianceMode);

            if (result == ValidationResult.FAIL) {
                return ValidationResult.FAIL;
            } else if (result == ValidationResult.WARN && worst == ValidationResult.PASS) {
                worst = ValidationResult.WARN;
            }
        }

        return worst;
    }

    /**
     * Generate a deployment-specific compliance report showing which controls are enabled.
     */
    public static String generateDeploymentReport(SystemContext ctx) {
        StringBuilder report = new StringBuilder();
        report.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        report.append("║       Deployment Compliance Status Report                      ║\n");
        report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        report.append("Environment: ").append(ctx.security).append("\n");
        report.append("Region: ").append(ctx.cfc.region()).append("\n");
        report.append("Network Mode: ").append(ctx.cfc.networkMode()).append("\n");
        report.append("Authentication: ").append(ctx.cfc.authMode()).append("\n\n");

        report.append("Control Implementation Status:\n");
        report.append("─".repeat(60)).append("\n\n");

        // Check each control
        report.append(formatControlStatus("ENCRYPTION_AT_REST",
            config.isEbsEncryptionEnabled() && config.isEfsEncryptionAtRestEnabled() && config.isS3EncryptionEnabled()));

        report.append(formatControlStatus("ENCRYPTION_IN_TRANSIT",
            ctx.cert.get().isPresent() && config.isEfsEncryptionInTransitEnabled()));

        report.append(formatControlStatus("NETWORK_SEGMENTATION",
            ctx.vpc.get().isPresent() && ctx.cfc.networkMode() != NetworkMode.PUBLIC));

        report.append(formatControlStatus("ACCESS_CONTROL",
            ctx.iamProfile != null));

        report.append(formatControlStatus("AUTHENTICATION",
            ctx.cfc.authMode() != AuthMode.NONE));

        report.append(formatControlStatus("AUDIT_LOGGING",
            config.isCloudTrailEnabled() && config.isFlowLogsEnabled()));

        report.append(formatControlStatus("LOG_RETENTION",
            config.getLogRetentionDays() == software.amazon.awscdk.services.logs.RetentionDays.TWO_YEARS));

        report.append(formatControlStatus("SECURITY_MONITORING",
            config.isSecurityMonitoringEnabled() && config.isAwsConfigEnabled()));

        report.append(formatControlStatus("THREAT_DETECTION",
            config.isGuardDutyEnabled()));

        report.append(formatControlStatus("WAF_PROTECTION",
            config.isWafEnabled()));

        report.append(formatControlStatus("BACKUP_RECOVERY",
            config.isAutomatedBackupEnabled()));

        report.append(formatControlStatus("HIGH_AVAILABILITY",
            config.isMultiAzEnforced() && config.isAutoScalingEnabled()));

        report.append(formatControlStatus("CHANGE_MANAGEMENT",
            config.isCloudTrailEnabled())); // IaC + CloudTrail provides change management

        report.append(formatControlStatus("VULNERABILITY_MANAGEMENT",
            config.isAwsConfigEnabled()));

        report.append("\n");
        report.append("═".repeat(60)).append("\n");

        // Count enabled controls
        int enabledCount = 0;
        int totalCount = SecurityControl.values().length;

        if (config.isEbsEncryptionEnabled()) enabledCount++;
        if (ctx.cert.get().isPresent()) enabledCount++;
        if (ctx.vpc.get().isPresent()) enabledCount++;
        if (ctx.iamProfile != null) enabledCount++;
        if (ctx.cfc.authMode() != AuthMode.NONE) enabledCount++;
        if (config.isCloudTrailEnabled()) enabledCount++;
        if (config.getLogRetentionDays() == software.amazon.awscdk.services.logs.RetentionDays.TWO_YEARS) enabledCount++;
        if (config.isSecurityMonitoringEnabled()) enabledCount++;
        if (config.isGuardDutyEnabled()) enabledCount++;
        if (config.isWafEnabled()) enabledCount++;
        if (config.isAutomatedBackupEnabled()) enabledCount++;
        if (config.isMultiAzEnforced()) enabledCount++;
        if (config.isCloudTrailEnabled()) enabledCount++;
        if (config.isAwsConfigEnabled()) enabledCount++;

        report.append("Controls Enabled: ").append(enabledCount).append(" / ").append(totalCount);
        report.append(" (").append(String.format("%.1f", (enabledCount * 100.0 / totalCount))).append("%)\n");
        report.append("═".repeat(60)).append("\n\n");

        return report.toString();
    }

    private static String formatControlStatus(String controlName, boolean enabled) {
        String status = enabled ? "✓ ENABLED " : "✗ DISABLED";
        String color = enabled ? "" : " [ACTION REQUIRED]";
        return String.format("  %-30s %s%s\n", controlName, status, color);
    }

    /**
     * Get frameworks satisfied by current deployment configuration.
     */
    public static List<String> getSatisfiedFrameworks(SystemContext ctx) {
        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        List<String> satisfied = new ArrayList<>();

        // Check if deployment meets minimum requirements for each framework
        boolean hasEncryption = config.isEbsEncryptionEnabled() && config.isEfsEncryptionAtRestEnabled();
        boolean hasNetworkSecurity = ctx.vpc.get().isPresent() && ctx.cfc.networkMode() != NetworkMode.PUBLIC;
        boolean hasAuthentication = ctx.cfc.authMode() != AuthMode.NONE;
        boolean hasAuditLogging = config.isCloudTrailEnabled() && config.isFlowLogsEnabled();
        boolean hasMonitoring = config.isSecurityMonitoringEnabled() && config.isGuardDutyEnabled();

        if (hasEncryption && hasNetworkSecurity && hasAuthentication && hasAuditLogging && hasMonitoring) {
            satisfied.add("SOC2-Security-Common-Criteria");
            satisfied.add("GDPR-Technical-Safeguards");
        }

        if (hasEncryption && hasAuthentication && hasAuditLogging &&
            config.getLogRetentionDays() == software.amazon.awscdk.services.logs.RetentionDays.TWO_YEARS) {
            satisfied.add("HIPAA-Technical-Safeguards");
        }

        if (hasEncryption && hasNetworkSecurity && hasAuthentication && hasAuditLogging &&
            hasMonitoring && config.isWafEnabled()) {
            satisfied.add("PCI-DSS-Infrastructure");
        }

        return satisfied;
    }

    /**
     * Check if a security control should be enforced based on compliance requirements.
     *
     * <p>This method determines whether a control must be enabled based on:
     * <ul>
     *   <li>The selected compliance frameworks (e.g., "PCI-DSS,HIPAA")</li>
     *   <li>The compliance mode (ENFORCE, ADVISORY, DISABLED)</li>
     *   <li>The requirement level of the control in each framework</li>
     * </ul>
     *
     * <p>Enforcement logic:
     * <ul>
     *   <li>ENFORCE mode + control is REQUIRED in any framework → enforce (return true)</li>
     *   <li>ENFORCE mode + control is only ADVISORY → don't enforce (return false)</li>
     *   <li>ADVISORY mode → never enforce (return false, but may warn)</li>
     *   <li>DISABLED mode → never enforce (return false)</li>
     *   <li>No frameworks selected → never enforce (return false)</li>
     * </ul>
     *
     * @param frameworksStr Comma-separated list of frameworks (e.g., "PCI-DSS,HIPAA,SOC2")
     * @param mode Compliance mode (ENFORCE, ADVISORY, or DISABLED)
     * @param control Security control to check
     * @return true if the control should be enforced (must be enabled)
     */
    public static boolean isControlRequired(
        String frameworksStr,
        ComplianceMode mode,
        SecurityControl control
    ) {
        // DISABLED mode: never enforce
        if (mode == ComplianceMode.DISABLED) {
            return false;
        }

        // ADVISORY mode: never enforce (just warn)
        if (mode == ComplianceMode.ADVISORY) {
            return false;
        }

        // ENFORCE mode: check if any selected framework REQUIRES this control
        if (mode == ComplianceMode.ENFORCE) {
            if (frameworksStr == null || frameworksStr.isEmpty()) {
                return false; // No frameworks selected
            }

            // Support comma, space, and + as delimiters (consistent with ComplianceFrameworkType)
            for (String framework : frameworksStr.split("[,\\s+]+")) {
                // Normalize: uppercase + replace underscores with hyphens (PCI_DSS → PCI-DSS)
                // This handles both enum.name() (PCI_DSS) and enum.getMatrixKey() (PCI-DSS) inputs
                String normalized = framework.trim().toUpperCase().replace("_", "-");
                if (normalized.isEmpty()) continue;
                if (control.isRequired(normalized)) {
                    LOG.fine("Control " + control.name() + " REQUIRED by framework: " + normalized);
                    return true; // At least one framework requires it
                }
            }
        }

        return false;
    }

    /**
     * Check if warnings should be logged for a disabled control.
     *
     * <p>Warnings are generated when:
     * <ul>
     *   <li>Control is disabled (isEnabled = false)</li>
     *   <li>Mode is ADVISORY or ENFORCE</li>
     *   <li>At least one framework has requirements (REQUIRED or ADVISORY)</li>
     * </ul>
     *
     * @param frameworksStr Comma-separated list of frameworks
     * @param mode Compliance mode
     * @param control Security control to check
     * @param isEnabled Whether the control is currently enabled
     * @return true if warnings should be logged
     */
    public static boolean shouldWarnForControl(
        String frameworksStr,
        ComplianceMode mode,
        SecurityControl control,
        boolean isEnabled
    ) {
        // No warnings if mode is DISABLED or control is already enabled
        if (mode == ComplianceMode.DISABLED || isEnabled) {
            return false;
        }

        // No warnings if no frameworks selected
        if (frameworksStr == null || frameworksStr.isEmpty()) {
            return false;
        }

        // Check if any framework has requirements (REQUIRED or ADVISORY)
        // Support comma, space, and + as delimiters (consistent with ComplianceFrameworkType)
        for (String framework : frameworksStr.split("[,\\s+]+")) {
            // Normalize: uppercase + replace underscores with hyphens (PCI_DSS → PCI-DSS)
            // This handles both enum.name() (PCI_DSS) and enum.getMatrixKey() (PCI-DSS) inputs
            String normalized = framework.trim().toUpperCase().replace("_", "-");
            if (normalized.isEmpty()) continue;
            RequirementLevel level = control.getRequirementLevel(normalized);
            if (level != RequirementLevel.NOT_APPLICABLE) {
                return true; // Found a framework with requirements
            }
        }

        return false;
    }
}
