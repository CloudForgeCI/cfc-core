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
 * GDPR organizational and data protection validation rules.
 *
 * <p>These rules enforce GDPR requirements beyond infrastructure security:</p>
 * <ul>
 *   <li><b>Lawfulness of Processing</b> - Article 6</li>
 *   <li><b>Data Subject Rights</b> - Articles 15-22</li>
 *   <li><b>Data Protection Impact Assessment</b> - Article 35</li>
 *   <li><b>International Data Transfers</b> - Articles 44-50</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>Legal basis documentation</li>
 *   <li>Consent mechanism validation</li>
 *   <li>Data subject rights procedures</li>
 *   <li>Data retention policies</li>
 *   <li>International transfer safeguards</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Install GDPR organizational validation
 * GdprOrganizationalRules.install(ctx);
 * }</pre>
 *
 * <h2>Important Note</h2>
 * <p>These controls require organizational policies and legal procedures that
 * cannot be fully automated. Infrastructure validation ensures technical readiness,
 * but organizations must maintain separate legal documentation and processes.</p>
 */
@ComplianceFramework(
    value = "GDPR-Organizational",
    priority = 35,
    displayName = "GDPR Organizational Requirements",
    description = "Validates GDPR organizational and data protection requirements"
)
public class GdprOrganizationalRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(GdprOrganizationalRules.class.getName());


    /**
     * Install GDPR organizational validation rules.
     * Only applies when GDPR framework is selected.
     *
     * @param ctx System context
     */
        @Override
    public void install(SystemContext ctx) {
        // Only apply if GDPR is in compliance frameworks
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        if (complianceFrameworks == null || !complianceFrameworks.toUpperCase().contains("GDPR")) {
            LOG.info("GDPR organizational rules skipped - GDPR not in complianceFrameworks");
            return;
        }

        LOG.info("Installing GDPR organizational compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Lawfulness of processing
            rules.addAll(validateLawfulnessOfProcessing(ctx));

            // Data subject rights
            rules.addAll(validateDataSubjectRights(ctx));

            // Data protection impact assessment
            rules.addAll(validateDataProtectionImpactAssessment(ctx));

            // International data transfers
            rules.addAll(validateInternationalTransfers(ctx));

            // Data retention and minimization
            rules.addAll(validateDataRetention(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("GDPR Organizational validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // These are always advisory - cannot fully automate legal/organizational controls
                if (ctx.security == SecurityProfile.DEV) {
                    return List.of();
                }

                // For production, convert to warnings (not blocking)
                return failedRules.stream()
                    .map(rule -> "[ADVISORY] " + rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
            } else {
                LOG.info("GDPR Organizational validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate lawfulness of processing (GDPR Article 6).
     *
     * <p>GDPR Requirements:</p>
     * <ul>
     *   <li>Article 6(1) - Legal basis for processing personal data</li>
     *   <li>Article 7 - Conditions for consent</li>
     *   <li>Article 13 - Information to be provided when collecting data</li>
     * </ul>
     */
    private List<ComplianceRule> validateLawfulnessOfProcessing(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Legal basis documented
        boolean legalBasisDocumented = RuleSettings.contextValue(ctx, "gdprLegalBasisDocumented", false);

        rules.add(ComplianceRule.check(
            legalBasisDocumented,
            "GDPR-ART6-LEGAL-BASIS",
            "Legal basis for processing documented",
            "Document legal basis for processing personal data",
            null,
            "Identify and document legal basis for each processing activity: " +
                "consent, contract, legal obligation, vital interests, public task, or legitimate interests. " +
                "GDPR Article 6(1). Required for lawful processing. " +
                "Set gdprLegalBasisDocumented = true when documentation is complete."));

        // Consent mechanism (if consent is legal basis)
        boolean consentMechanismImplemented = RuleSettings.contextValue(ctx, "gdprConsentMechanismImplemented", false);

        rules.add(ComplianceRule.check(
            consentMechanismImplemented,
            "GDPR-ART7-CONSENT",
            "Consent mechanism implemented or not applicable",
            "Implement compliant consent mechanism if using consent as legal basis",
            null,
            "If using consent: ensure freely given, specific, informed, unambiguous. " +
                "Separate consent for different purposes. Easy withdrawal. " +
                "GDPR Article 7. Document consent records. " +
                "Set gdprConsentMechanismImplemented = true when implemented (or N/A if not using consent)."));

        // Privacy notice
        boolean privacyNoticeProvided = RuleSettings.contextValue(ctx, "gdprPrivacyNoticeProvided", false);

        rules.add(ComplianceRule.check(
            privacyNoticeProvided,
            "GDPR-ART13-PRIVACY-NOTICE",
            "Privacy notice provided to data subjects",
            "Provide privacy notice when collecting personal data",
            null,
            "Privacy notice must include: controller identity, purposes, legal basis, " +
                "recipients, retention periods, data subject rights, withdrawal of consent. " +
                "GDPR Articles 13-14. Must be concise, transparent, accessible. " +
                "Set gdprPrivacyNoticeProvided = true when privacy notice is published."));

        return rules;
    }

    /**
     * Validate data subject rights procedures (GDPR Articles 15-22).
     *
     * <p>GDPR Rights:</p>
     * <ul>
     *   <li>Article 15 - Right of access</li>
     *   <li>Article 16 - Right to rectification</li>
     *   <li>Article 17 - Right to erasure ("right to be forgotten")</li>
     *   <li>Article 18 - Right to restriction of processing</li>
     *   <li>Article 20 - Right to data portability</li>
     *   <li>Article 21 - Right to object</li>
     * </ul>
     */
    private List<ComplianceRule> validateDataSubjectRights(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Data subject request procedures
        boolean dataSubjectRequestProcedures = RuleSettings.contextValue(ctx, "gdprDataSubjectRequestProcedures", false);

        rules.add(ComplianceRule.check(
            dataSubjectRequestProcedures,
            "GDPR-DATA-SUBJECT-RIGHTS",
            "Data subject rights request procedures implemented",
            "Implement procedures for handling data subject rights requests",
            null,
            "Document procedures for: access requests, rectification, erasure, " +
                "restriction, portability, objection. 1-month response timeline. " +
                "GDPR Articles 15-22. Include identity verification. " +
                "Set gdprDataSubjectRequestProcedures = true when procedures are documented."));

        // Right to erasure technical capability
        boolean rightToErasureCapability = RuleSettings.contextValue(ctx, "gdprRightToErasureCapability", false);

        rules.add(ComplianceRule.check(
            rightToErasureCapability,
            "GDPR-ART17-ERASURE",
            "Right to erasure technical capability implemented",
            "Implement technical capability for data erasure",
            null,
            "Ensure systems can delete/anonymize personal data upon request. " +
                "GDPR Article 17. Consider backup retention, legal holds. " +
                "Document erasure procedures. " +
                "Set gdprRightToErasureCapability = true when erasure is technically feasible."));

        // Data portability capability
        boolean dataPortabilityCapability = RuleSettings.contextValue(ctx, "gdprDataPortabilityCapability", false);

        rules.add(ComplianceRule.check(
            dataPortabilityCapability,
            "GDPR-ART20-PORTABILITY",
            "Data portability capability implemented",
            "Implement data portability capability (structured, machine-readable format)",
            null,
            "Enable data export in structured, commonly used, machine-readable format (JSON, CSV). " +
                "GDPR Article 20. Applies when processing is based on consent or contract. " +
                "Set gdprDataPortabilityCapability = true when export functionality exists."));

        return rules;
    }

    /**
     * Validate Data Protection Impact Assessment (DPIA) - GDPR Article 35.
     *
     * <p>GDPR Requirements:</p>
     * <ul>
     *   <li>Article 35(1) - DPIA required for high-risk processing</li>
     *   <li>Article 35(3) - DPIA required for: systematic monitoring, special categories, large scale</li>
     *   <li>Article 35(7) - DPIA must assess: necessity, proportionality, risks, safeguards</li>
     * </ul>
     */
    private List<ComplianceRule> validateDataProtectionImpactAssessment(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // DPIA conducted
        boolean dpiaCompleted = RuleSettings.contextValue(ctx, "gdprDpiaCompleted", false);

        boolean isProdOrStaging = ctx.security == SecurityProfile.PRODUCTION || ctx.security == SecurityProfile.STAGING;
        if (isProdOrStaging && !dpiaCompleted) {
            rules.add(ComplianceRule.fail(
                "GDPR-ART35-DPIA",
                "Data Protection Impact Assessment (DPIA) required for high-risk processing",
                "Conduct DPIA if processing: involves systematic monitoring, special categories of data, " +
                "large-scale profiling, automated decision-making. GDPR Article 35. " +
                "Assess risks to rights and freedoms. Document safeguards. " +
                "Set gdprDpiaCompleted = true when DPIA is documented."
            ));
        } else if (dpiaCompleted || !isProdOrStaging) {
            rules.add(ComplianceRule.pass(
                "GDPR-ART35-DPIA",
                "Data Protection Impact Assessment completed or not required"
            ));
        }

        // Privacy by Design
        boolean privacyByDesignImplemented = RuleSettings.contextValue(ctx, "gdprPrivacyByDesignImplemented", false);

        rules.add(ComplianceRule.check(
            privacyByDesignImplemented,
            "GDPR-ART25-PRIVACY-BY-DESIGN",
            "Privacy by design and by default implemented",
            "Implement data protection by design and by default",
            null,
            "Implement technical and organizational measures ensuring: data minimization, " +
                "pseudonymization, encryption, confidentiality, integrity, availability. " +
                "GDPR Article 25. Infrastructure encryption satisfies part of this. " +
                "Set gdprPrivacyByDesignImplemented = true when measures are documented."));

        return rules;
    }

    /**
     * Validate international data transfer safeguards (GDPR Chapter V).
     *
     * <p>GDPR Requirements:</p>
     * <ul>
     *   <li>Articles 44-50 - Transfer of personal data to third countries</li>
     *   <li>Article 46 - Standard Contractual Clauses (SCCs)</li>
     *   <li>Article 49 - Derogations for specific situations</li>
     * </ul>
     */
    private List<ComplianceRule> validateInternationalTransfers(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Check if using EU region
        String region = ctx.cfc.region();
        boolean isEuRegion = region != null && region.startsWith("eu-");

        // International transfer safeguards
        boolean internationalTransferSafeguards = RuleSettings.contextValue(ctx, "gdprInternationalTransferSafeguards", false);

        rules.add(ComplianceRule.check(
            !(!isEuRegion && !internationalTransferSafeguards),
            "GDPR-ART46-INTERNATIONAL-TRANSFERS",
            "International transfer safeguards in place or using EU region",
            "Implement safeguards for international data transfers outside EU/EEA",
            null,
            "If transferring personal data outside EU/EEA: use Standard Contractual Clauses (SCCs), " +
                "Binding Corporate Rules (BCRs), or adequacy decision. " +
                "GDPR Articles 44-46. AWS SCCs available through AWS Artifact. " +
                "Set gdprInternationalTransferSafeguards = true when safeguards are in place."));

        // Data localization
        boolean dataLocalizationEnforced = RuleSettings.contextValue(ctx, "gdprDataLocalizationEnforced", false);

        rules.add(ComplianceRule.check(
            dataLocalizationEnforced,
            "GDPR-DATA-LOCALIZATION",
            "Data localization enforced",
            "Document data localization requirements and enforcement",
            null,
            "Verify all personal data remains in approved regions (EU/EEA or adequate country). " +
                "Configure S3 bucket policies, RDS read replicas, backup regions accordingly. " +
                "Set gdprDataLocalizationEnforced = true when localization is configured."));

        return rules;
    }

    /**
     * Validate data retention and minimization (GDPR Article 5).
     *
     * <p>GDPR Requirements:</p>
     * <ul>
     *   <li>Article 5(1)(c) - Data minimization</li>
     *   <li>Article 5(1)(e) - Storage limitation (retention periods)</li>
     *   <li>Article 30 - Records of processing activities</li>
     * </ul>
     */
    private List<ComplianceRule> validateDataRetention(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Data retention policy
        boolean dataRetentionPolicyDefined = RuleSettings.contextValue(ctx, "gdprDataRetentionPolicyDefined", false);

        rules.add(ComplianceRule.check(
            dataRetentionPolicyDefined,
            "GDPR-ART5-RETENTION",
            "Data retention policy defined",
            "Define data retention policy and deletion schedules",
            null,
            "Document retention periods for each category of personal data. " +
                "GDPR Article 5(1)(e): data kept no longer than necessary. " +
                "Implement automated deletion after retention period expires. " +
                "Set gdprDataRetentionPolicyDefined = true when policy is documented."));

        // Records of processing activities (ROPA)
        boolean recordsOfProcessingActivities = RuleSettings.contextValue(ctx, "gdprRecordsOfProcessingActivities", false);

        rules.add(ComplianceRule.check(
            recordsOfProcessingActivities,
            "GDPR-ART30-ROPA",
            "Records of processing activities (ROPA) maintained",
            "Maintain records of processing activities (ROPA)",
            null,
            "Document: purposes, categories of data subjects, categories of data, " +
                "recipients, transfers, retention periods, security measures. " +
                "GDPR Article 30. Required for organizations >250 employees or high-risk processing. " +
                "Set gdprRecordsOfProcessingActivities = true when ROPA is maintained."));

        return rules;
    }

    /**
     * Controls checked across every {@code validate*} method above -- see {@link
     * com.cloudforge.core.interfaces.FrameworkRules#claimedControls}. "GDPR-Organizational" is
     * not a {@link ComplianceMatrix} column, so this declaration is for documentation only, not
     * enforced by the sync test. Every check in this class reads a manually-attested
     * deployment-context flag (legal basis, consent, DPIA, retention policy, ROPA, etc.) rather
     * than any infrastructure state a {@code ComplianceMatrix.SecurityControl} describes, so no
     * control names are claimed.
     */
    @Override
    public Set<String> claimedControls() {
        return ClaimedControls.of();
    }
}
