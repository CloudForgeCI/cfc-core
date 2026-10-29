package com.cloudforgeci.api.core.rules;


import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.Set;
import com.cloudforgeci.api.core.rules.ComplianceMatrix.SecurityControl;

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
        boolean awsBaaSigned = RuleSettings.contextValue(ctx, "awsBaaSigned", false);

        rules.add(ComplianceRule.check(
            awsBaaSigned,
            "HIPAA-BAA-AWS",
            "AWS Business Associate Agreement signed",
            "AWS Business Associate Agreement (BAA) must be signed",
            null,
            "Sign AWS BAA through AWS Artifact or contact AWS sales. " +
                "HIPAA §164.308(b)(1): Required for all AWS services handling PHI. " +
                "Set awsBaaSigned = true after signing BAA."));

        // BAA for third-party services
        boolean thirdPartyBaasDocumented = RuleSettings.contextValue(ctx, "thirdPartyBaasDocumented", false);

        rules.add(ComplianceRule.check(
            thirdPartyBaasDocumented,
            "HIPAA-BAA-THIRD-PARTY",
            "Third-party Business Associate Agreements documented",
            "Business Associate Agreements required for all third-party services",
            null,
            "Document BAAs with all vendors that may access PHI: " +
                "monitoring tools, log management, backup services, support vendors. " +
                "HIPAA §164.308(b)(1), §164.314(a). " +
                "Set thirdPartyBaasDocumented = true when BAAs are in place."));

        // BAA provisions verification
        boolean baaProvisionsVerified = RuleSettings.contextValue(ctx, "baaProvisionsVerified", false);

        rules.add(ComplianceRule.check(
            baaProvisionsVerified,
            "HIPAA-BAA-PROVISIONS",
            "BAA provisions verified for compliance",
            "Verify all BAAs include required HIPAA provisions",
            null,
            "Ensure BAAs include: safeguard obligations, subcontractor requirements, " +
                "breach notification, termination provisions, data return/destruction. " +
                "HIPAA §164.314(a)(2). Set baaProvisionsVerified = true after verification."));

        // Subcontractor BAAs (AWS sub-processors)
        boolean subcontractorBaasTracked = RuleSettings.contextValue(ctx, "subcontractorBaasTracked", false);

        rules.add(ComplianceRule.check(
            subcontractorBaasTracked,
            "HIPAA-BAA-SUBCONTRACTORS",
            "Subcontractor BAAs tracked",
            "Track Business Associate Agreements for all subcontractors",
            null,
            "Maintain list of AWS services used and verify each has BAA coverage. " +
                "Monitor AWS sub-processor lists. HIPAA §164.308(b)(2). " +
                "Set subcontractorBaasTracked = true when tracking system is in place."));

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
        boolean workforceAuthorizationProcedures = RuleSettings.contextValue(ctx, "workforceAuthorizationProcedures", false);

        rules.add(ComplianceRule.check(
            workforceAuthorizationProcedures,
            "HIPAA-WORKFORCE-AUTHORIZATION",
            "Workforce authorization procedures implemented",
            "Implement workforce authorization procedures for PHI access",
            null,
            "Document and implement procedures for determining PHI access needs. " +
                "HIPAA §164.308(a)(3)(ii)(A). IAM roles provide technical enforcement. " +
                "Set workforceAuthorizationProcedures = true when documented."));

        // Termination procedures
        boolean terminationProcedures = RuleSettings.contextValue(ctx, "terminationProcedures", false);

        rules.add(ComplianceRule.check(
            terminationProcedures,
            "HIPAA-TERMINATION-PROCEDURES",
            "Termination procedures implemented",
            "Implement workforce termination procedures",
            null,
            "Document procedures for terminating access when employment ends. " +
                "HIPAA §164.308(a)(3)(ii)(C). Include IAM user/role removal. " +
                "Set terminationProcedures = true when procedures are documented."));

        // HIPAA training
        boolean hipaaTrainingProgram = RuleSettings.contextValue(ctx, "hipaaTrainingProgram", false);

        rules.add(ComplianceRule.check(
            hipaaTrainingProgram,
            "HIPAA-TRAINING",
            "HIPAA training program implemented",
            "HIPAA security awareness training required for workforce",
            null,
            "Implement annual HIPAA security awareness training program. " +
                "HIPAA §164.308(a)(5). Document training completion. " +
                "Set hipaaTrainingProgram = true when training is implemented."));

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
        boolean emergencyAccessProcedures = RuleSettings.contextValue(ctx, "emergencyAccessProcedures", false);

        rules.add(ComplianceRule.check(
            emergencyAccessProcedures,
            "HIPAA-EMERGENCY-ACCESS",
            "Emergency access procedures documented",
            "Emergency access procedures required for PHI systems",
            null,
            "Document procedures for obtaining PHI access during emergency. " +
                "HIPAA §164.308(a)(7)(ii)(C), §164.312(a)(2)(ii). " +
                "Include break-glass IAM roles, audit logging, approval workflows. " +
                "Set emergencyAccessProcedures = true when documented."));

        // Automatic logoff
        boolean automaticLogoffEnabled = RuleSettings.contextValue(ctx, "automaticLogoffEnabled", false);

        rules.add(ComplianceRule.check(
            automaticLogoffEnabled,
            "HIPAA-AUTOMATIC-LOGOFF",
            "Automatic logoff/session timeout enabled",
            "Automatic logoff required for workstations accessing PHI",
            null,
            "Implement automatic session timeout for Jenkins/application access. " +
                "HIPAA §164.312(a)(2)(iii). Typical timeout: 15-30 minutes. " +
                "Set automaticLogoffEnabled = true when session timeouts are configured."));

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
        boolean incidentResponsePlan = RuleSettings.contextValue(ctx, "incidentResponsePlan", false);

        rules.add(ComplianceRule.check(
            incidentResponsePlan,
            "HIPAA-INCIDENT-RESPONSE",
            "Security incident response plan documented",
            "Security incident response plan required",
            null,
            "Document incident response procedures including breach identification, " +
                "investigation, mitigation, and notification. HIPAA §164.308(a)(6). " +
                "Set incidentResponsePlan = true when plan is documented."));

        // Breach notification procedures
        boolean breachNotificationProcedures = RuleSettings.contextValue(ctx, "breachNotificationProcedures", false);

        rules.add(ComplianceRule.check(
            breachNotificationProcedures,
            "HIPAA-BREACH-NOTIFICATION",
            "Breach notification procedures documented",
            "Breach notification procedures required (60-day timeline)",
            null,
            "Document breach notification procedures: risk assessment, individual notification " +
                "(60 days), HHS notification, media notification (>500 individuals). " +
                "HIPAA §164.410, 45 CFR 164.400-414. " +
                "Set breachNotificationProcedures = true when procedures are documented."));

        // Breach detection automation
        boolean breachDetectionAutomation = RuleSettings.contextValue(ctx, "breachDetectionAutomation", false);

        rules.add(ComplianceRule.check(
            breachDetectionAutomation,
            "HIPAA-BREACH-DETECTION",
            "Automated breach detection configured",
            "Automated breach detection recommended",
            null,
            "Configure GuardDuty, Security Hub, and CloudWatch alarms for breach indicators. " +
                "Set breachDetectionAutomation = true when automation is configured."));

        return rules;
    }

    /**
     * Controls checked across every {@code validate*} method above -- see {@link
     * com.cloudforge.core.interfaces.FrameworkRules#claimedControls}. "HIPAA-Organizational" is
     * not a {@link ComplianceMatrix} column, so this declaration is for documentation only, not
     * enforced by the sync test. Every check in this class reads a manually-attested
     * deployment-context flag (BAA signed, workforce authorization, emergency access, breach
     * notification, etc.) rather than any infrastructure state a
     * {@code ComplianceMatrix.SecurityControl} describes, so no control names are claimed.
     */
    @Override
    public Set<String> claimedControls() {
        return ClaimedControls.of();
    }
}
