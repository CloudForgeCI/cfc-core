package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.Set;

/**
 * ISO/IEC 27001:2022 Information Security Management compliance validation.
 *
 * <p>This is an example implementation demonstrating the v2.0 instance-based
 * plugin architecture. External contributors can use this as a template for
 * implementing additional compliance frameworks.</p>
 *
 * <h2>ISO 27001:2022 Coverage</h2>
 * <p>The 2022 edition organizes Annex A into four themes (organizational, people, physical,
 * technological) instead of the 2013 edition's 14 numbered clauses. This class checks controls
 * from:
 * <ul>
 *   <li><strong>A.5:</strong> Organizational Controls (access control, authentication
 *       information, information security during disruption)</li>
 *   <li><strong>A.8:</strong> Technological Controls (cryptography, logging, monitoring
 *       activities, application security)</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * "complianceFrameworks": "ISO-27001"
 * }</pre>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "ISO-27001",
    priority = 50,
    displayName = "ISO/IEC 27001:2022 Information Security Management",
    description = "Validates ISO 27001 information security controls for cloud infrastructure"
)
public class Iso27001Rules implements FrameworkRules<SystemContext> {
    private static final Logger LOG = Logger.getLogger(Iso27001Rules.class.getName());

    /**
     * Install ISO 27001 compliance validation rules.
     *
     * <p>This method demonstrates the v2.0 instance-based pattern where the class
     * implements {@link FrameworkRules} as an instance method rather than using
     * static methods.</p>
     *
     * @param ctx the system context containing deployment configuration
     */
    @Override
    public void install(SystemContext ctx) {
        // ISO 27001 applies primarily to production and staging
        if (ctx.security != SecurityProfile.PRODUCTION && ctx.security != SecurityProfile.STAGING) {
            LOG.info("ISO 27001 validation enforced for PRODUCTION and STAGING profiles only");
            return;
        }

        LOG.info("Installing ISO/IEC 27001:2022 compliance validation for " + ctx.security);

        // Determine compliance mode
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        LOG.info("  Compliance mode: " + complianceMode);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // A.5.15 / A.8.16 - Access Control & Monitoring Activities
            rules.addAll(validateAccessControl(ctx));

            // A.8.24 - Use of Cryptography
            rules.addAll(validateCryptography(ctx));

            // A.8.15 / A.8.16 - Logging & Monitoring Activities
            rules.addAll(validateOperationsSecurity(ctx));

            // A.8.16 - Monitoring Activities (network traffic)
            rules.addAll(validateCommunicationsSecurity(ctx));

            // A.5.29 - Information Security During Disruption
            rules.addAll(validateBusinessContinuity(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            // Convert to error strings
            List<String> errors = failedRules.stream()
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();

            if (!errors.isEmpty()) {
                if (complianceMode == ComplianceMode.ADVISORY) {
                    LOG.warning("ISO 27001 validation found " + errors.size() + " recommendations (ADVISORY mode)");
                    errors.forEach(err -> LOG.warning("  - " + err));
                    ComplianceFindingsCollector.record(failedRules);
                    return List.of(); // Don't block synthesis
                } else if (ctx.security == SecurityProfile.STAGING) {
                    // STAGING runs the same checks as PRODUCTION but never blocks on them -- the
                    // finding is still visible, synthesis still succeeds.
                    LOG.warning("ISO 27001 validation found " + errors.size() + " violations (STAGING - not blocking)");
                    errors.forEach(err -> LOG.warning("  - " + err));
                    ComplianceFindingsCollector.record(failedRules);
                    return List.of();
                } else {
                    LOG.severe("ISO 27001 validation failed with " + errors.size() + " violations (ENFORCE mode)");
                    errors.forEach(err -> LOG.severe("  - " + err));
                    return errors; // Block synthesis
                }
            } else {
                LOG.info("ISO 27001 validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * A.5.15 / A.8.16 - Access Control &amp; Monitoring Activities.
     *
     * <p>Validates that access to information and systems is properly controlled.</p>
     */
    private List<ComplianceRule> validateAccessControl(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // A.8.16 - Monitoring Activities (network access monitoring)
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.16",
                "Network access monitoring required (ISO 27001 A.8.16)",
                "Enable security monitoring for network access control"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.16",
                "Network access monitoring enabled (ISO 27001 A.8.16)"
            ));
        }

        // A.8.26 - Application Security Requirements (WAF)
        if (!config.isWafEnabled() && (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING)) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.26",
                "WAF required for access restriction in production (ISO 27001 A.8.26)",
                "Enable WAF to restrict malicious access"
            ));
        } else if (config.isWafEnabled()) {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.26",
                "WAF enabled for access restriction (ISO 27001 A.8.26)"
            ));
        }

        return rules;
    }

    /**
     * A.8.24 - Use of Cryptography.
     *
     * <p>Validates proper use of cryptographic controls.</p>
     */
    private List<ComplianceRule> validateCryptography(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.8.24 - Use of Cryptography (data at rest)
        if (!config.isEbsEncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.24-EBS",
                "EBS encryption required (ISO 27001 A.8.24)",
                "Enable EBS encryption for data at rest"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.24-EBS",
                "EBS encryption enabled (ISO 27001 A.8.24)"
            ));
        }

        if (!config.isEfsEncryptionAtRestEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.24-EFS-Rest",
                "EFS encryption at rest required (ISO 27001 A.8.24)",
                "Enable EFS encryption at rest"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.24-EFS-Rest",
                "EFS encryption at rest enabled (ISO 27001 A.8.24)"
            ));
        }

        // A.8.24 - Use of Cryptography (data in transit)
        if (!config.isEfsEncryptionInTransitEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.24-EFS-Transit",
                "EFS encryption in transit required (ISO 27001 A.8.24)",
                "Enable EFS encryption in transit (TLS)"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.24-EFS-Transit",
                "EFS encryption in transit enabled (ISO 27001 A.8.24)"
            ));
        }

        return rules;
    }

    /**
     * A.8.15 / A.8.16 - Logging &amp; Monitoring Activities.
     *
     * <p>Validates operational procedures and responsibilities.</p>
     */
    private List<ComplianceRule> validateOperationsSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.8.15 - Logging (event logging)
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.15",
                "CloudTrail logging required (ISO 27001 A.8.15)",
                "Enable CloudTrail for API event tracking"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.15",
                "CloudTrail logging enabled (ISO 27001 A.8.15)"
            ));
        }

        // A.8.16 - Monitoring Activities (threat detection)
        if (!config.isGuardDutyEnabled() && (ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING)) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.16-ThreatDetection",
                "Vulnerability detection required for production (ISO 27001 A.8.16)",
                "GuardDutyEnabled",
                "Enable AWS GuardDuty for vulnerability and threat detection"
            ));
        } else if (config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.16-ThreatDetection",
                "Vulnerability detection enabled (ISO 27001 A.8.16)",
                "GuardDutyEnabled"
            ));
        }

        return rules;
    }

    /**
     * A.8.16 - Monitoring Activities (network traffic).
     *
     * <p>Validates security of network communications.</p>
     */
    private List<ComplianceRule> validateCommunicationsSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.8.16 - Monitoring Activities (network traffic)
        if (!config.isFlowLogsEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.8.16-NetworkTraffic",
                "Network traffic logging required (ISO 27001 A.8.16)",
                "VpcFlowLogsEnabled",
                "Enable VPC Flow Logs for network traffic monitoring"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.8.16-NetworkTraffic",
                "Network traffic logging enabled (ISO 27001 A.8.16)",
                "VpcFlowLogsEnabled"
            ));
        }

        return rules;
    }

    /**
     * A.5.29 - Information Security During Disruption.
     *
     * <p>Validates availability and disaster recovery controls.</p>
     */
    private List<ComplianceRule> validateBusinessContinuity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.5.29 - Information Security During Disruption (Multi-AZ availability)
        if ((ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING) && !config.isMultiAzEnforced()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.5.29",
                "Multi-AZ deployment required for production availability (ISO 27001 A.5.29)",
                "Enable Multi-AZ for high availability"
            ));
        } else if (config.isMultiAzEnforced()) {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.5.29",
                "Multi-AZ deployment enabled (ISO 27001 A.5.29)"
            ));
        }

        return rules;
    }

    /**
     * Controls checked across every {@code validate*} method above -- see {@link
     * com.cloudforge.core.interfaces.FrameworkRules#claimedControls}. {@link ComplianceMatrix}
     * now has an ISO-27001 column; this declaration is kept in sync with it like the other
     * infrastructure frameworks (PCI-DSS/HIPAA/SOC2/GDPR).
     */
    @Override
    public Set<String> claimedControls() {
        return Set.of(
            "SECURITY_MONITORING", "WAF_PROTECTION", "ENCRYPTION_AT_REST",
            "ENCRYPTION_IN_TRANSIT", "AUDIT_LOGGING", "THREAT_DETECTION",
            "NETWORK_FLOW_LOGS", "HIGH_AVAILABILITY"
        );
    }
}
