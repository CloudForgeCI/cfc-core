package com.cloudforgeci.api.core.rules;

import java.util.Optional;

/**
 * Represents a compliance rule with its validation status and AWS Config rule mapping.
 *
 * <p>This structured approach links compliance validation to actual AWS Config rules,
 * providing traceability between framework requirements and infrastructure monitoring.</p>
 *
 * @param ruleId Unique identifier for this compliance rule (e.g., "SOC2-CC6.1", "PCI-DSS-Req3.4")
 * @param description Human-readable description of the requirement
 * @param configRuleId Optional AWS Config rule ID that monitors this requirement
 * @param passed Whether the validation check passed -- true for both a clean pass and an advisory
 * @param errorMessage Optional error message if validation failed
 * @param recommendation Optional recommendation for an ADVISORY-tier control that's off; present only
 *        on a passed=true result, distinguishing "clean pass" from "passed, but here's a recommendation"
 *        without a separate boolean. See {@link #advisory}.
 */
public record ComplianceRule(
    String ruleId,
    String description,
    Optional<String> configRuleId,
    boolean passed,
    Optional<String> errorMessage,
    Optional<String> recommendation
) {
    /**
     * Create a passing compliance rule.
     */
    public static ComplianceRule pass(String ruleId, String description, String configRuleId) {
        return new ComplianceRule(
            ruleId,
            description,
            Optional.ofNullable(configRuleId),
            true,
            Optional.empty(),
            Optional.empty()
        );
    }

    /**
     * Create a passing compliance rule without Config rule mapping.
     */
    public static ComplianceRule pass(String ruleId, String description) {
        return new ComplianceRule(
            ruleId,
            description,
            Optional.empty(),
            true,
            Optional.empty(),
            Optional.empty()
        );
    }

    /**
     * Create a failing compliance rule with error message.
     */
    public static ComplianceRule fail(String ruleId, String description, String errorMessage) {
        return new ComplianceRule(
            ruleId,
            description,
            Optional.empty(),
            false,
            Optional.of(errorMessage),
            Optional.empty()
        );
    }

    /**
     * Create a failing compliance rule with Config rule mapping.
     */
    public static ComplianceRule fail(String ruleId, String description, String configRuleId, String errorMessage) {
        return new ComplianceRule(
            ruleId,
            description,
            Optional.ofNullable(configRuleId),
            false,
            Optional.of(errorMessage),
            Optional.empty()
        );
    }

    /**
     * Create an ADVISORY-tier finding: the control is off, but the framework doesn't require it, so
     * this must never block synthesis. {@code passed} stays true; {@code recommendation} carries the
     * specific, actionable text for the compliance report.
     */
    public static ComplianceRule advisory(String ruleId, String description, String recommendation) {
        return new ComplianceRule(
            ruleId,
            description,
            Optional.empty(),
            true,
            Optional.empty(),
            Optional.of(recommendation)
        );
    }

    /**
     * Create an ADVISORY-tier finding with Config rule mapping.
     */
    public static ComplianceRule advisory(String ruleId, String description, String configRuleId,
                                          String recommendation) {
        return new ComplianceRule(
            ruleId,
            description,
            Optional.ofNullable(configRuleId),
            true,
            Optional.empty(),
            Optional.of(recommendation)
        );
    }

    /**
     * True for a passed result that carries a recommendation -- distinguishes an advisory finding
     * from a clean pass without a separate constructor flag.
     */
    public boolean isAdvisory() {
        return passed && recommendation.isPresent();
    }

    /**
     * Convert to legacy string error format for CDK validation.
     */
    public Optional<String> toErrorString() {
        if (passed) {
            return Optional.empty();
        }

        StringBuilder error = new StringBuilder();
        error.append(ruleId).append(": ").append(description);

        errorMessage.ifPresent(msg -> error.append(" - ").append(msg));

        configRuleId.ifPresent(id -> error.append(" (Config Rule: ").append(id).append(")"));

        return Optional.of(error.toString());
    }
}
