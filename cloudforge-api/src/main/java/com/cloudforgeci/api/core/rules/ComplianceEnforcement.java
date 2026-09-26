package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Shared STAGING/ADVISORY/ENFORCE enforcement policy for a {@code FrameworkRules} plugin's
 * {@code Node.addValidation} callback -- every framework class ({@code HipaaRules},
 * {@code PciDssRules}, {@code GdprRules}, {@code FedRampRules}, {@code Iso27001Rules},
 * {@code Soc2Rules}) was independently reimplementing this identical policy, risking exactly the
 * kind of drift that happened before this class existed (FedRAMP's STAGING/ADVISORY branches not
 * recording to {@link ComplianceFindingsCollector} the way every other framework's did).
 *
 * <p>Policy, in order:
 * <ul>
 *   <li>No failures -- pass, nothing blocks.</li>
 *   <li>{@link ComplianceMode#ADVISORY} -- every failure is a visible, non-blocking finding.</li>
 *   <li>{@link SecurityProfile#STAGING} -- runs the same checks as PRODUCTION, but only rule IDs
 *       in {@code stagingBlockingRules} actually block synthesis; everything else is a visible,
 *       non-blocking finding. Pass {@link Set#of()} for a framework where nothing blocks in
 *       STAGING.</li>
 *   <li>Otherwise (PRODUCTION under {@link ComplianceMode#ENFORCE}) -- every failure blocks.</li>
 * </ul>
 *
 * <p>Every branch that produces a non-empty error list also records the underlying {@link
 * ComplianceRule} list with {@link ComplianceFindingsCollector}, so a caller draining findings
 * (e.g. {@code CloudForgeSynthesizer#synthesizeAdvisoryDryRun}) sees them regardless of which
 * branch fired.
 */
public final class ComplianceEnforcement {

    /** Static utility only -- no instances. */
    private ComplianceEnforcement() {
    }

    /**
     * @param frameworkDisplayName name used in log lines (e.g. "HIPAA", "PCI-DSS", "SOC 2")
     * @param rules every {@link ComplianceRule} the framework's validators produced, pass and fail
     * @param complianceMode the resolved {@link ComplianceMode} for this deployment
     * @param profile the resolved {@link SecurityProfile} for this deployment
     * @param stagingBlockingRules rule IDs that still block synthesis in STAGING; ignored outside
     *     STAGING
     * @return the error strings to return from the {@code Node.addValidation} callback -- an
     *     empty list means synthesis proceeds, a non-empty list blocks it
     */
    public static List<String> resolve(
            String frameworkDisplayName,
            List<ComplianceRule> rules,
            ComplianceMode complianceMode,
            SecurityProfile profile,
            Set<String> stagingBlockingRules,
            Logger log) {

        List<ComplianceRule> failedRules = rules.stream().filter(rule -> !rule.passed()).toList();
        List<String> errors = failedRules.stream()
            .map(ComplianceRule::toErrorString)
            .flatMap(Optional::stream)
            .toList();

        if (errors.isEmpty()) {
            log.info(frameworkDisplayName + " validation passed (" + rules.size() + " checks)");
            return List.of();
        }

        if (complianceMode == ComplianceMode.ADVISORY) {
            log.warning(frameworkDisplayName + " validation found " + errors.size()
                + " recommendations (ADVISORY mode - not blocking)");
            errors.forEach(err -> log.warning("  - " + err));
            ComplianceFindingsCollector.record(failedRules);
            return List.of();
        }

        if (profile == SecurityProfile.STAGING) {
            List<String> blocking = failedRules.stream()
                .filter(rule -> stagingBlockingRules.contains(rule.ruleId()))
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();
            log.warning(frameworkDisplayName + " validation found " + errors.size() + " violations (STAGING - "
                + blocking.size() + " blocking)");
            errors.forEach(err -> log.warning("  - " + err));
            ComplianceFindingsCollector.record(failedRules);
            return blocking;
        }

        log.severe(frameworkDisplayName + " validation failed with " + errors.size()
            + " violations (ENFORCE mode - blocking deployment)");
        errors.forEach(err -> log.severe("  - " + err));
        return errors;
    }
}
