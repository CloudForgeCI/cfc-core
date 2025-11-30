package com.cloudforgeci.api.core.rules;


import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * HIPAA organizational and administrative safeguard validation rules.
 *
 * <p>These rules enforce HIPAA requirements that extend beyond infrastructure:</p>
 * <ul>
 *   <li><b>Business Associate Agreements (BAA)</b> - §164.308(b), §164.314</li>
 *   <li><b>Workforce Security</b> - §164.308(a)(3)</li>
 *   <li><b>Administrative Safeguards</b> - §164.308(a)</li>
 *   <li><b>Breach Notification</b> - §164.308(a)(6), §164.410</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>Business Associate Agreement validation</li>
 *   <li>Workforce security procedures</li>
 *   <li>Emergency access procedures</li>
 *   <li>Automatic logoff validation</li>
 *   <li>Breach notification readiness</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Install HIPAA organizational validation
 * HipaaOrganizationalRules.install(ctx);
 * }</pre>
 *
 * <h2>Important Note</h2>
 * <p>These controls require organizational policies and procedures that cannot be
 * fully automated. Infrastructure validation ensures technical readiness, but
 * organizations must maintain separate documentation and processes.</p>
 */
@ComplianceFramework(
    value = "HIPAA-Organizational",
    priority = 15,
    displayName = "HIPAA Organizational Requirements",
    description = "Validates HIPAA organizational and administrative safeguards"
)
public class HipaaOrganizationalRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(HipaaOrganizationalRules.class.getName());


    /**
     * Install HIPAA organizational validation rules.
     * Only applies when HIPAA framework is selected.
     *
     * @param ctx System context
     */
        @Override
    public void install(SystemContext ctx) {
        // Only apply if HIPAA is in compliance frameworks
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        if (complianceFrameworks == null || !complianceFrameworks.toUpperCase().contains("HIPAA")) {
            LOG.info("HIPAA organizational rules skipped - HIPAA not in complianceFrameworks");
            return;
        }

        LOG.info("Installing HIPAA organizational compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Business Associate Agreements
            rules.addAll(validateBusinessAssociateAgreements(ctx));

            // Workforce Security
            rules.addAll(validateWorkforceSecurity(ctx));

            // Emergency Access Procedures
            rules.addAll(validateEmergencyAccess(ctx));

            // Breach Notification Readiness
            rules.addAll(validateBreachNotification(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("HIPAA Organizational validation found " + failedRules.size() + " issues");
                failedRules.forEach(rule ->
                    LOG.warning("  - [ADVISORY] " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // Return advisory messages - organizational controls require manual procedures
                return failedRules.stream()
                    .map(rule -> "[ADVISORY] " + rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
            } else {
                LOG.info("HIPAA Organizational validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate Business Associate Agreement (BAA) compliance.
     *
     * <p>HIPAA Requirements:</p>
     * <ul>
     *   <li>§164.308(b)(1) - Written contract required with business associates</li>
     *   <li>§164.314(a) - Business associate contracts must include specific provisions</li>
     *   <li>§164.308(b)(3) - Documentation of satisfactory assurances</li>
     * </ul>
     */
    private List<ComplianceRule> validateBusinessAssociateAgreements(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // AWS BAA signed
        boolean awsBaaSigned = getBooleanSetting(ctx, "awsBaaSigned", false);

        if (!awsBaaSigned) {
            rules.add(ComplianceRule.fail(
                "HIPAA-BAA-AWS",
                "AWS Business Associate Agreement (BAA) must be signed",
                "Sign AWS BAA through AWS Artifact or contact AWS sales. " +
                "HIPAA §164.308(b)(1): Required for all AWS services handling PHI. " +
                "Set awsBaaSigned = true after signing BAA."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-BAA-AWS",
                "AWS Business Associate Agreement signed"
            ));
        }

        // BAA for third-party services
        boolean thirdPartyBaasDocumented = getBooleanSetting(ctx, "thirdPartyBaasDocumented", false);

        if (!thirdPartyBaasDocumented) {
            rules.add(ComplianceRule.fail(
                "HIPAA-BAA-THIRD-PARTY",
                "Business Associate Agreements required for all third-party services",
                "Document BAAs with all vendors that may access PHI: " +
                "monitoring tools, log management, backup services, support vendors. " +
                "HIPAA §164.308(b)(1), §164.314(a). " +
                "Set thirdPartyBaasDocumented = true when BAAs are in place."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-BAA-THIRD-PARTY",
                "Third-party Business Associate Agreements documented"
            ));
        }

        // BAA provisions verification
        boolean baaProvisionsVerified = getBooleanSetting(ctx, "baaProvisionsVerified", false);

        if (!baaProvisionsVerified) {
            rules.add(ComplianceRule.fail(
                "HIPAA-BAA-PROVISIONS",
                "Verify all BAAs include required HIPAA provisions",
                "Ensure BAAs include: safeguard obligations, subcontractor requirements, " +
                "breach notification, termination provisions, data return/destruction. " +
                "HIPAA §164.314(a)(2). Set baaProvisionsVerified = true after verification."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-BAA-PROVISIONS",
                "BAA provisions verified for compliance"
            ));
        }

        // Subcontractor BAAs (AWS sub-processors)
        boolean subcontractorBaasTracked = getBooleanSetting(ctx, "subcontractorBaasTracked", false);

        if (!subcontractorBaasTracked) {
            rules.add(ComplianceRule.fail(
                "HIPAA-BAA-SUBCONTRACTORS",
                "Track Business Associate Agreements for all subcontractors",
                "Maintain list of AWS services used and verify each has BAA coverage. " +
                "Monitor AWS sub-processor lists. HIPAA §164.308(b)(2). " +
                "Set subcontractorBaasTracked = true when tracking system is in place."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-BAA-SUBCONTRACTORS",
                "Subcontractor BAAs tracked"
            ));
        }

        return rules;
    }

    /**
     * Validate workforce security procedures.
     *
     * <p>HIPAA Requirements:</p>
     * <ul>
     *   <li>§164.308(a)(3)(i) - Workforce security procedures</li>
     *   <li>§164.308(a)(3)(ii)(A) - Authorization and/or supervision</li>
     *   <li>§164.308(a)(3)(ii)(B) - Workforce clearance procedure</li>
     *   <li>§164.308(a)(3)(ii)(C) - Termination procedures</li>
     * </ul>
     */
    private List<ComplianceRule> validateWorkforceSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Authorization procedures
        boolean workforceAuthorizationProcedures = getBooleanSetting(ctx, "workforceAuthorizationProcedures", false);

        if (!workforceAuthorizationProcedures) {
            rules.add(ComplianceRule.fail(
                "HIPAA-WORKFORCE-AUTHORIZATION",
                "Implement workforce authorization procedures for PHI access",
                "Document and implement procedures for determining PHI access needs. " +
                "HIPAA §164.308(a)(3)(ii)(A). IAM roles provide technical enforcement. " +
                "Set workforceAuthorizationProcedures = true when documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-WORKFORCE-AUTHORIZATION",
                "Workforce authorization procedures implemented"
            ));
        }

        // Termination procedures
        boolean terminationProcedures = getBooleanSetting(ctx, "terminationProcedures", false);

        if (!terminationProcedures) {
            rules.add(ComplianceRule.fail(
                "HIPAA-TERMINATION-PROCEDURES",
                "Implement workforce termination procedures",
                "Document procedures for terminating access when employment ends. " +
                "HIPAA §164.308(a)(3)(ii)(C). Include IAM user/role removal. " +
                "Set terminationProcedures = true when procedures are documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-TERMINATION-PROCEDURES",
                "Termination procedures implemented"
            ));
        }

        // HIPAA training
        boolean hipaaTrainingProgram = getBooleanSetting(ctx, "hipaaTrainingProgram", false);

        if (!hipaaTrainingProgram) {
            rules.add(ComplianceRule.fail(
                "HIPAA-TRAINING",
                "HIPAA security awareness training required for workforce",
                "Implement annual HIPAA security awareness training program. " +
                "HIPAA §164.308(a)(5). Document training completion. " +
                "Set hipaaTrainingProgram = true when training is implemented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-TRAINING",
                "HIPAA training program implemented"
            ));
        }

        return rules;
    }

    /**
     * Validate emergency access procedures.
     *
     * <p>HIPAA Requirements:</p>
     * <ul>
     *   <li>§164.308(a)(7)(ii)(C) - Emergency access procedure</li>
     *   <li>§164.312(a)(2)(ii) - Emergency access procedure (technical)</li>
     * </ul>
     */
    private List<ComplianceRule> validateEmergencyAccess(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Emergency access procedures documented
        boolean emergencyAccessProcedures = getBooleanSetting(ctx, "emergencyAccessProcedures", false);

        if (!emergencyAccessProcedures) {
            rules.add(ComplianceRule.fail(
                "HIPAA-EMERGENCY-ACCESS",
                "Emergency access procedures required for PHI systems",
                "Document procedures for obtaining PHI access during emergency. " +
                "HIPAA §164.308(a)(7)(ii)(C), §164.312(a)(2)(ii). " +
                "Include break-glass IAM roles, audit logging, approval workflows. " +
                "Set emergencyAccessProcedures = true when documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-EMERGENCY-ACCESS",
                "Emergency access procedures documented"
            ));
        }

        // Automatic logoff
        boolean automaticLogoffEnabled = getBooleanSetting(ctx, "automaticLogoffEnabled", false);

        if (!automaticLogoffEnabled) {
            rules.add(ComplianceRule.fail(
                "HIPAA-AUTOMATIC-LOGOFF",
                "Automatic logoff required for workstations accessing PHI",
                "Implement automatic session timeout for Jenkins/application access. " +
                "HIPAA §164.312(a)(2)(iii). Typical timeout: 15-30 minutes. " +
                "Set automaticLogoffEnabled = true when session timeouts are configured."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-AUTOMATIC-LOGOFF",
                "Automatic logoff/session timeout enabled"
            ));
        }

        return rules;
    }

    /**
     * Validate breach notification readiness.
     *
     * <p>HIPAA Requirements:</p>
     * <ul>
     *   <li>§164.308(a)(6) - Security incident procedures</li>
     *   <li>§164.410 - Notification to individuals (Breach Notification Rule)</li>
     *   <li>45 CFR Part 164, Subpart D - Breach notification requirements</li>
     * </ul>
     */
    private List<ComplianceRule> validateBreachNotification(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Incident response plan
        boolean incidentResponsePlan = getBooleanSetting(ctx, "incidentResponsePlan", false);

        if (!incidentResponsePlan) {
            rules.add(ComplianceRule.fail(
                "HIPAA-INCIDENT-RESPONSE",
                "Security incident response plan required",
                "Document incident response procedures including breach identification, " +
                "investigation, mitigation, and notification. HIPAA §164.308(a)(6). " +
                "Set incidentResponsePlan = true when plan is documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-INCIDENT-RESPONSE",
                "Security incident response plan documented"
            ));
        }

        // Breach notification procedures
        boolean breachNotificationProcedures = getBooleanSetting(ctx, "breachNotificationProcedures", false);

        if (!breachNotificationProcedures) {
            rules.add(ComplianceRule.fail(
                "HIPAA-BREACH-NOTIFICATION",
                "Breach notification procedures required (60-day timeline)",
                "Document breach notification procedures: risk assessment, individual notification " +
                "(60 days), HHS notification, media notification (>500 individuals). " +
                "HIPAA §164.410, 45 CFR 164.400-414. " +
                "Set breachNotificationProcedures = true when procedures are documented."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-BREACH-NOTIFICATION",
                "Breach notification procedures documented"
            ));
        }

        // Breach detection automation
        boolean breachDetectionAutomation = getBooleanSetting(ctx, "breachDetectionAutomation", false);

        if (!breachDetectionAutomation) {
            rules.add(ComplianceRule.fail(
                "HIPAA-BREACH-DETECTION",
                "Automated breach detection recommended",
                "Configure GuardDuty, Security Hub, and CloudWatch alarms for breach indicators. " +
                "Set breachDetectionAutomation = true when automation is configured."
            ));
        } else {
            rules.add(ComplianceRule.pass(
                "HIPAA-BREACH-DETECTION",
                "Automated breach detection configured"
            ));
        }

        return rules;
    }

    /**
     * Helper method to safely get boolean settings from deployment context.
     */
    private static boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
