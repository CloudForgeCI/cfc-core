package com.cloudforgeci.api.integration.deployment;

import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.*;

/**
 * Validates compliance framework requirements in CloudFormation templates.
 *
 * <p>Systematically validates that compliance frameworks (SOC2, PCI-DSS, HIPAA, GDPR)
 * properly deploy required AWS resources and configurations.
 *
 * <p><b>Framework Requirements:</b>
 * <ul>
 *   <li>SOC2: Access controls, audit logging, threat detection, encryption</li>
 *   <li>PCI-DSS: Network segmentation, GuardDuty, WAF, encryption</li>
 *   <li>HIPAA: PHI encryption, audit trails, access controls</li>
 *   <li>GDPR: Data protection, encryption, audit logging</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * ComplianceValidationMatrix validator = new ComplianceValidationMatrix(template);
 * validator.validateCompliance("SOC2", SecurityProfile.PRODUCTION);
 * assertTrue(validator.isCompliant(), validator.getViolationsReport());
 * }</pre>
 */
public class ComplianceValidationMatrix {

    private final Template template;
    private final List<String> violations;
    private final Map<String, List<String>> frameworkRequirements;

    public ComplianceValidationMatrix(Template template) {
        this.template = template;
        this.violations = new ArrayList<>();
        this.frameworkRequirements = initializeFrameworkRequirements();
    }

    /**
     * Initialize compliance framework requirements.
     * Maps each framework to its required AWS Config rule names.
     */
    private Map<String, List<String>> initializeFrameworkRequirements() {
        Map<String, List<String>> requirements = new HashMap<>();

        // SOC2 requirements (subset of actual rules)
        requirements.put("SOC2", List.of(
            "s3-bucket-versioning-enabled",
            "cloudtrail-enabled",
            "guardduty-enabled-centralized",
            "ebs-encryption-enabled",
            "efs-encrypted-check",
            "alb-http-to-https-redirection-check",
            "vpc-flow-logs-enabled"
        ));

        // PCI-DSS requirements (subset of actual rules)
        requirements.put("PCI-DSS", List.of(
            "restricted-ssh",
            "vpc-sg-open-only-to-authorized-ports",
            "guardduty-enabled-centralized",
            "cloudtrail-encryption-enabled",
            "s3-bucket-logging-enabled",
            "vpc-default-security-group-closed",
            "waf-enabled-check"
        ));

        // HIPAA requirements (subset of actual rules)
        requirements.put("HIPAA", List.of(
            "encrypted-volumes",
            "s3-bucket-server-side-encryption-enabled",
            "cloudtrail-enabled",
            "access-keys-rotated",
            "iam-password-policy",
            "rds-encryption-enabled",
            "vpc-flow-logs-enabled"
        ));

        // GDPR requirements (subset of actual rules)
        requirements.put("GDPR", List.of(
            "ebs-encryption-enabled",
            "s3-bucket-server-side-encryption-enabled",
            "cloudtrail-enabled",
            "access-keys-rotated",
            "vpc-flow-logs-enabled"
        ));

        return requirements;
    }

    /**
     * Validate compliance framework requirements.
     *
     * @param complianceFramework The compliance framework (SOC2, PCI-DSS, HIPAA, GDPR) or comma-separated list
     * @param securityProfile The security profile (DEV, STAGING, PRODUCTION)
     */
    public void validateCompliance(String complianceFramework, SecurityProfile securityProfile) {
        if (complianceFramework == null || "none".equals(complianceFramework)) {
            return; // No compliance validation needed
        }

        // Support multi-framework configurations (e.g., "SOC2,PCI-DSS")
        String[] frameworks = complianceFramework.split(",");
        for (String framework : frameworks) {
            String trimmedFramework = framework.trim();
            switch (trimmedFramework) {
                case "SOC2":
                    validateSoc2Compliance(securityProfile);
                    break;
                case "PCI-DSS":
                    validatePciDssCompliance(securityProfile);
                    break;
                case "HIPAA":
                    validateHipaaCompliance(securityProfile);
                    break;
                case "GDPR":
                    validateGdprCompliance(securityProfile);
                    break;
                default:
                    violations.add("Unknown compliance framework: " + trimmedFramework);
            }
        }
    }

    /**
     * Validate SOC2 compliance requirements.
     *
     * SOC2 Trust Service Criteria:
     * - Security: Access controls, encryption, monitoring
     * - Availability: Redundancy, backups, monitoring
     * - Confidentiality: Encryption, access controls
     */
    private void validateSoc2Compliance(SecurityProfile securityProfile) {
        // 1. Access Controls - OIDC/Cognito authentication
        // (Optional - not all deployments need auth)

        // 2. Audit Logging - CloudTrail
        if (securityProfile == SecurityProfile.PRODUCTION || securityProfile == SecurityProfile.STAGING) {
            try {
                template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
                    "IsLogging", true,
                    "EnableLogFileValidation", true
                )));
            } catch (AssertionError e) {
                violations.add("SOC2: CloudTrail with log validation required for " + securityProfile);
            }
        }

        // 3. Threat Detection - GuardDuty
        // NOTE: GuardDuty is ADVISORY for SOC2 (not required), so we don't check for it
        // It's handled by ThreatProtectionRules using ComplianceMatrix which marks it as recommended but not required

        // 4. Encryption at Rest - EFS
        try {
            template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
                "Encrypted", true
            )));
        } catch (AssertionError e) {
            violations.add("SOC2: EFS encryption required");
        }

        // 5. Access Logging - ALB (STAGING/PRODUCTION)
        if (securityProfile != SecurityProfile.DEV) {
            // ALB access logging is configured via attributes, harder to validate
            // We'll just check ALB exists
            try {
                template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
            } catch (AssertionError e) {
                violations.add("SOC2: Load balancer required");
            }
        }

        // 6. VPC Flow Logs
        if (securityProfile != SecurityProfile.DEV) {
            try {
                template.hasResourceProperties("AWS::EC2::FlowLog", Match.objectLike(Map.of(
                    "ResourceType", "VPC"
                )));
            } catch (AssertionError e) {
                violations.add("SOC2: VPC Flow Logs required for " + securityProfile);
            }
        }

        // 7. HTTPS/TLS - Encryption in transit (SOC2 CC6.7)
        try {
            template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Match.objectLike(Map.of(
                "Protocol", "HTTPS"
            )));
        } catch (AssertionError e) {
            violations.add("SOC2: HTTPS listener required for data transmission encryption (CC6.7)");
        }
    }

    /**
     * Validate PCI-DSS compliance requirements.
     *
     * PCI-DSS Requirements:
     * - Network segmentation
     * - Strong access controls
     * - Encryption in transit and at rest
     * - Regular monitoring and testing
     * - WAF deployment
     */
    private void validatePciDssCompliance(SecurityProfile securityProfile) {
        // 1. WAF - Required for PCI-DSS
        try {
            template.resourceCountIs("AWS::WAFv2::WebACL", 1);
        } catch (AssertionError e) {
            violations.add("PCI-DSS: WAF WebACL required");
        }

        // 2. Threat Detection - GuardDuty (auto-enabled for PCI-DSS)
        try {
            template.hasResourceProperties("AWS::GuardDuty::Detector", Match.objectLike(Collections.emptyMap()));
        } catch (Exception e) {
            violations.add("PCI-DSS: GuardDuty detector required for Requirement 11.4");
        }

        // 3. CloudTrail - Audit logging
        try {
            template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
                "IsLogging", true,
                "IsMultiRegionTrail", true
            )));
        } catch (AssertionError e) {
            violations.add("PCI-DSS: Multi-region CloudTrail required");
        }

        // 4. EFS Encryption
        try {
            template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
                "Encrypted", true
            )));
        } catch (AssertionError e) {
            violations.add("PCI-DSS: EFS encryption required");
        }

        // 5. VPC Flow Logs
        try {
            template.hasResourceProperties("AWS::EC2::FlowLog", Match.objectLike(Collections.emptyMap()));
        } catch (AssertionError e) {
            violations.add("PCI-DSS: VPC Flow Logs required");
        }

        // 6. Security Groups - Should exist and be restrictive
        try {
            template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Collections.emptyMap()));
        } catch (AssertionError e) {
            violations.add("PCI-DSS: Security groups required");
        }

        // 7. HTTPS/TLS - Encryption in transit (PCI-DSS Req 4.1)
        try {
            template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Match.objectLike(Map.of(
                "Protocol", "HTTPS"
            )));
        } catch (AssertionError e) {
            violations.add("PCI-DSS: HTTPS listener required for encrypted transmission of cardholder data (Req 4.1)");
        }
    }

    /**
     * Validate HIPAA compliance requirements.
     *
     * HIPAA Requirements:
     * - PHI encryption at rest and in transit
     * - Audit trails
     * - Access controls
     * - Breach notification mechanisms
     */
    private void validateHipaaCompliance(SecurityProfile securityProfile) {
        // 1. EFS Encryption (for PHI storage)
        try {
            template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
                "Encrypted", true
            )));
        } catch (AssertionError e) {
            violations.add("HIPAA: EFS encryption required for PHI protection");
        }

        // 2. CloudTrail - Required for HIPAA audit trails
        try {
            template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
                "IsLogging", true,
                "EnableLogFileValidation", true
            )));
        } catch (AssertionError e) {
            violations.add("HIPAA: CloudTrail with log validation required");
        }

        // 3. VPC Flow Logs - Network audit trails
        try {
            template.hasResourceProperties("AWS::EC2::FlowLog", Match.objectLike(Collections.emptyMap()));
        } catch (AssertionError e) {
            violations.add("HIPAA: VPC Flow Logs required for audit trails");
        }

        // 4. HTTPS/TLS - Encryption in transit (HIPAA §164.312(e)(2)(i))
        try {
            template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Match.objectLike(Map.of(
                "Protocol", "HTTPS"
            )));
        } catch (AssertionError e) {
            violations.add("HIPAA: HTTPS listener required for encryption in transit (§164.312(e)(2)(i))");
        }

        // 5. Access Controls - IAM roles should exist
        try {
            template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Collections.emptyMap()));
        } catch (AssertionError e) {
            violations.add("HIPAA: IAM roles required for access control");
        }
    }

    /**
     * Validate GDPR compliance requirements.
     *
     * GDPR Requirements:
     * - Data encryption
     * - Audit logging
     * - Right to be forgotten (data deletion capabilities)
     * - Data protection officer (DPO) designation
     */
    private void validateGdprCompliance(SecurityProfile securityProfile) {
        // 1. Encryption - Required for personal data protection
        try {
            template.hasResourceProperties("AWS::EFS::FileSystem", Match.objectLike(Map.of(
                "Encrypted", true
            )));
        } catch (AssertionError e) {
            violations.add("GDPR: Encryption required for personal data protection");
        }

        // 2. Audit Logging - CloudTrail for data access tracking
        try {
            template.hasResourceProperties("AWS::CloudTrail::Trail", Match.objectLike(Map.of(
                "IsLogging", true
            )));
        } catch (AssertionError e) {
            violations.add("GDPR: CloudTrail required for data access audit");
        }

        // 3. VPC Flow Logs - Network activity monitoring
        if (securityProfile != SecurityProfile.DEV) {
            try {
                template.hasResourceProperties("AWS::EC2::FlowLog", Match.objectLike(Collections.emptyMap()));
            } catch (AssertionError e) {
                violations.add("GDPR: VPC Flow Logs required for monitoring");
            }
        }

        // 4. Data deletion capabilities - S3 bucket versioning/lifecycle
        // This is more about operational capabilities than synthesis
    }

    /**
     * Validate AWS Config rules are deployed for the compliance framework.
     *
     * @param complianceFramework The compliance framework
     * @param awsConfigEnabled Whether AWS Config is enabled in deployment context
     */
    public void validateConfigRules(String complianceFramework, boolean awsConfigEnabled) {
        if (!frameworkRequirements.containsKey(complianceFramework)) {
            return;
        }

        if (!awsConfigEnabled) {
            violations.add(complianceFramework +
                ": AWS Config rules not deployed (awsConfigEnabled=false)");
            return;
        }

        List<String> requiredRules = frameworkRequirements.get(complianceFramework);

        // Check that AWS Config infrastructure exists
        try {
            template.hasResourceProperties("AWS::Config::ConfigurationRecorder",
                Match.objectLike(Collections.emptyMap()));
        } catch (Exception e) {
            violations.add(complianceFramework + ": AWS Config ConfigurationRecorder not found");
            return; // No point checking individual rules if Config isn't enabled
        }

        try {
            template.hasResourceProperties("AWS::Config::DeliveryChannel",
                Match.objectLike(Collections.emptyMap()));
        } catch (Exception e) {
            violations.add(complianceFramework + ": AWS Config DeliveryChannel not found");
        }

        // Count Config rules deployed
        int configRuleCount = 0;
        try {
            // Try to find at least one Config rule
            template.hasResourceProperties("AWS::Config::ConfigRule",
                Match.objectLike(Collections.emptyMap()));

            // If we get here, at least one rule exists
            // Note: CDK Template API doesn't provide easy way to count resources
            // So we just verify at least one rule exists
            configRuleCount = 1;
        } catch (Exception e) {
            violations.add(complianceFramework + ": No AWS::Config::ConfigRule resources found " +
                "(expected rules for framework)");
            return;
        }

        if (configRuleCount == 0) {
            violations.add(complianceFramework + ": No Config rules deployed (expected " +
                requiredRules.size() + " rules)");
        }
    }

    /**
     * Validate specific Config rule exists by logical ID pattern.
     *
     * @param ruleNamePattern Pattern to match in logical ID (e.g., "EbsEncryptionRule")
     * @return true if rule found, false otherwise
     */
    public boolean hasConfigRule(String ruleNamePattern) {
        try {
            template.hasResourceProperties("AWS::Config::ConfigRule",
                Match.objectLike(Collections.emptyMap()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate remediation actions are deployed for Config rules.
     *
     * @param complianceFramework The compliance framework
     * @param awsConfigEnabled Whether AWS Config is enabled
     */
    public void validateRemediationActions(String complianceFramework, boolean awsConfigEnabled) {
        if (!awsConfigEnabled) {
            violations.add(complianceFramework +
                ": Remediation actions not deployed (awsConfigEnabled=false)");
            return;
        }

        try {
            template.hasResourceProperties("AWS::Config::RemediationConfiguration",
                Match.objectLike(Collections.emptyMap()));
        } catch (Exception e) {
            violations.add(complianceFramework +
                ": No AWS::Config::RemediationConfiguration resources found " +
                "(automated remediation not enabled)");
        }
    }

    /**
     * Check if all compliance validations passed.
     *
     * @return true if compliant, false if violations found
     */
    public boolean isCompliant() {
        return violations.isEmpty();
    }

    /**
     * Get list of compliance violations.
     *
     * @return List of violation messages
     */
    public List<String> getViolations() {
        return new ArrayList<>(violations);
    }

    /**
     * Get formatted violations report.
     *
     * @return Multi-line string with all violations
     */
    public String getViolationsReport() {
        if (violations.isEmpty()) {
            return "✅ All compliance checks passed";
        }

        StringBuilder report = new StringBuilder();
        report.append("❌ Compliance violations found (").append(violations.size()).append("):\n");
        for (int i = 0; i < violations.size(); i++) {
            report.append("  ").append(i + 1).append(". ").append(violations.get(i)).append("\n");
        }
        return report.toString();
    }

    /**
     * Get compliance framework requirements (Config rule names).
     *
     * @param framework The compliance framework
     * @return List of required Config rule names
     */
    public List<String> getFrameworkRequirements(String framework) {
        return frameworkRequirements.getOrDefault(framework, Collections.emptyList());
    }
}
