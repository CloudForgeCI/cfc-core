package com.cloudforge.core.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a local emulator deploy preflight check.
 */
public record PreflightResult(
        DeploymentTarget target,
        List<PreflightViolation> violations) {

    public PreflightResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static PreflightResult allowed(DeploymentTarget target) {
        return new PreflightResult(target, List.of());
    }

    public static PreflightResult of(DeploymentTarget target, List<PreflightViolation> violations) {
        return new PreflightResult(target, violations);
    }

    public boolean allowed(PreflightMode mode) {
        return switch (mode) {
            case OFF -> true;
            case WARN -> true;
            case ENFORCE -> blockingViolations().isEmpty();
        };
    }

    public List<PreflightViolation> blockingViolations() {
        return violations.stream()
            .filter(v -> v.severity() == PreflightSeverity.BLOCK)
            .toList();
    }

    public List<PreflightViolation> warnings() {
        return violations.stream()
            .filter(v -> v.severity() == PreflightSeverity.WARN)
            .toList();
    }

    public PreflightResult merge(PreflightResult other) {
        if (other == null || other.violations.isEmpty()) {
            return this;
        }
        List<PreflightViolation> merged = new ArrayList<>(violations);
        merged.addAll(other.violations);
        return new PreflightResult(target, merged);
    }

    public void throwIfBlocked(PreflightMode mode) throws IOException {
        if (allowed(mode)) {
            return;
        }
        throw new IOException(PreflightMessages.formatBlocked(this));
    }
}
