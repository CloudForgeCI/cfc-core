package com.cloudforge.core.local;

/**
 * Formats preflight outcomes for CLI and exception messages.
 */
public final class PreflightMessages {

    private PreflightMessages() {
    }

    public static String formatBlocked(PreflightResult result) {
        String target = result.target() == DeploymentTarget.MINISTACK ? "MiniStack" : "LocalStack";
        StringBuilder sb = new StringBuilder();
        sb.append(target).append(" deployment blocked (preflight):\n");
        for (PreflightViolation violation : result.blockingViolations()) {
            sb.append("  • [").append(violation.ruleId()).append("] ")
                .append(violation.message());
            if (violation.suggestion() != null && !violation.suggestion().isBlank()) {
                sb.append("\n    → ").append(violation.suggestion());
            }
            sb.append('\n');
        }
        sb.append("Override: ")
            .append(envKey(result.target()))
            .append("=warn|off");
        return sb.toString().trim();
    }

    public static String formatWarnings(PreflightResult result) {
        if (result.warnings().isEmpty()) {
            return "";
        }
        String target = result.target() == DeploymentTarget.MINISTACK ? "MiniStack" : "LocalStack";
        StringBuilder sb = new StringBuilder();
        sb.append(target).append(" preflight warnings:\n");
        for (PreflightViolation violation : result.warnings()) {
            sb.append("  • [").append(violation.ruleId()).append("] ")
                .append(violation.message());
            if (violation.suggestion() != null && !violation.suggestion().isBlank()) {
                sb.append("\n    → ").append(violation.suggestion());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static String envKey(DeploymentTarget target) {
        return target == DeploymentTarget.MINISTACK
            ? "MINISTACK_PREFLIGHT"
            : "LOCALSTACK_PREFLIGHT";
    }
}
