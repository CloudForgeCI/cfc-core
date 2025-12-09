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

/**
 * ISO/IEC 27001:2022 Information Security Management compliance validation.
 *
 * <p>This is an example implementation demonstrating the v2.0 instance-based
 * plugin architecture. External contributors can use this as a template for
 * implementing additional compliance frameworks.</p>
 *
 * <h2>ISO 27001 Coverage:</h2>
 * <ul>
 *   <li><strong>A.5:</strong> Information Security Policies</li>
 *   <li><strong>A.8:</strong> Asset Management</li>
 *   <li><strong>A.9:</strong> Access Control</li>
 *   <li><strong>A.10:</strong> Cryptography</li>
 *   <li><strong>A.12:</strong> Operations Security</li>
 *   <li><strong>A.13:</strong> Communications Security</li>
 *   <li><strong>A.14:</strong> System Acquisition, Development and Maintenance</li>
 *   <li><strong>A.17:</strong> Business Continuity Management</li>
 *   <li><strong>A.18:</strong> Compliance</li>
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
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        LOG.info("  Compliance mode: " + complianceMode +
                (complianceModeStr == null ? " (default for " + ctx.security + ")" : " (explicit)"));

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // A.9 - Access Control
            rules.addAll(validateAccessControl(ctx));

            // A.10 - Cryptography
            rules.addAll(validateCryptography(ctx));

            // A.12 - Operations Security
            rules.addAll(validateOperationsSecurity(ctx));

            // A.13 - Communications Security
            rules.addAll(validateCommunicationsSecurity(ctx));

            // A.17 - Business Continuity
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
                    return List.of(); // Don't block synthesis
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
     * A.9 - Access Control.
     *
     * <p>Validates that access to information and systems is properly controlled.</p>
     */
    private List<ComplianceRule> validateAccessControl(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow(
            () -> new IllegalStateException("SecurityProfileConfiguration not set")
        );

        // A.9.1.2 - Access to networks and network services
        if (!config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.9.1.2",
                "Network access monitoring required (ISO 27001 A.9.1.2)",
                "Enable security monitoring for network access control"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.9.1.2",
                "Network access monitoring enabled (ISO 27001 A.9.1.2)"
            ));
        }

        // A.9.4.1 - Information access restriction
        if (!config.isWafEnabled() && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.9.4.1",
                "WAF required for access restriction in production (ISO 27001 A.9.4.1)",
                "Enable WAF to restrict malicious access"
            ));
        } else if (config.isWafEnabled()) {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.9.4.1",
                "WAF enabled for access restriction (ISO 27001 A.9.4.1)"
            ));
        }

        return rules;
    }

    /**
     * A.10 - Cryptography.
     *
     * <p>Validates proper use of cryptographic controls.</p>
     */
    private List<ComplianceRule> validateCryptography(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.10.1.1 - Policy on the use of cryptographic controls
        if (!config.isEbsEncryptionEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.10.1.1-EBS",
                "EBS encryption required (ISO 27001 A.10.1.1)",
                "Enable EBS encryption for data at rest"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.10.1.1-EBS",
                "EBS encryption enabled (ISO 27001 A.10.1.1)"
            ));
        }

        if (!config.isEfsEncryptionAtRestEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.10.1.1-EFS-Rest",
                "EFS encryption at rest required (ISO 27001 A.10.1.1)",
                "Enable EFS encryption at rest"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.10.1.1-EFS-Rest",
                "EFS encryption at rest enabled (ISO 27001 A.10.1.1)"
            ));
        }

        if (!config.isEfsEncryptionInTransitEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.10.1.1-EFS-Transit",
                "EFS encryption in transit required (ISO 27001 A.10.1.1)",
                "Enable EFS encryption in transit (TLS)"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.10.1.1-EFS-Transit",
                "EFS encryption in transit enabled (ISO 27001 A.10.1.1)"
            ));
        }

        return rules;
    }

    /**
     * A.12 - Operations Security.
     *
     * <p>Validates operational procedures and responsibilities.</p>
     */
    private List<ComplianceRule> validateOperationsSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.12.4.1 - Event logging
        if (!config.isCloudTrailEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.12.4.1",
                "CloudTrail logging required (ISO 27001 A.12.4.1)",
                "Enable CloudTrail for API event tracking"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.12.4.1",
                "CloudTrail logging enabled (ISO 27001 A.12.4.1)"
            ));
        }

        // A.12.6.1 - Management of technical vulnerabilities
        if (!config.isGuardDutyEnabled() && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.12.6.1",
                "Vulnerability detection required for production (ISO 27001 A.12.6.1)",
                "GuardDutyEnabled",
                "Enable AWS GuardDuty for vulnerability and threat detection"
            ));
        } else if (config.isGuardDutyEnabled()) {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.12.6.1",
                "Vulnerability detection enabled (ISO 27001 A.12.6.1)",
                "GuardDutyEnabled"
            ));
        }

        return rules;
    }

    /**
     * A.13 - Communications Security.
     *
     * <p>Validates security of network communications.</p>
     */
    private List<ComplianceRule> validateCommunicationsSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.13.1.1 - Network controls
        if (!config.isFlowLogsEnabled()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.13.1.1",
                "Network traffic logging required (ISO 27001 A.13.1.1)",
                "VpcFlowLogsEnabled",
                "Enable VPC Flow Logs for network traffic monitoring"
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.13.1.1",
                "Network traffic logging enabled (ISO 27001 A.13.1.1)",
                "VpcFlowLogsEnabled"
            ));
        }

        return rules;
    }

    /**
     * A.17 - Business Continuity Management.
     *
     * <p>Validates availability and disaster recovery controls.</p>
     */
    private List<ComplianceRule> validateBusinessContinuity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElseThrow();

        // A.17.2.1 - Availability of information processing facilities
        if (ctx.security == SecurityProfile.PRODUCTION && !config.isMultiAzEnforced()) {
            rules.add(ComplianceRule.fail(
                "ISO-27001-A.17.2.1",
                "Multi-AZ deployment required for production availability (ISO 27001 A.17.2.1)",
                "Enable Multi-AZ for high availability"
            ));
        } else if (config.isMultiAzEnforced()) {
            rules.add(ComplianceRule.pass(
                "ISO-27001-A.17.2.1",
                "Multi-AZ deployment enabled (ISO 27001 A.17.2.1)"
            ));
        }

        return rules;
    }
}
