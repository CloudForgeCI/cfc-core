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
 * @param passed Whether the validation check passed
 * @param errorMessage Optional error message if validation failed
 */
public record ComplianceRule(
    String ruleId,
    String description,
    Optional<String> configRuleId,
    boolean passed,
    Optional<String> errorMessage
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
            Optional.of(errorMessage)
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
            Optional.of(errorMessage)
        );
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
