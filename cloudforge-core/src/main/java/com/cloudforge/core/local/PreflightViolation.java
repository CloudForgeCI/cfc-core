package com.cloudforge.core.local;

/**
 * Single preflight finding.
 */
public record PreflightViolation(
        PreflightSeverity severity,
        String ruleId,
        String message,
        String suggestion) {

    public PreflightViolation {
        if (severity == null) {
            throw new IllegalArgumentException("severity required");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message required");
        }
    }
}
