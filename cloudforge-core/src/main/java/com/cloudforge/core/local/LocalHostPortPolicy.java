package com.cloudforge.core.local;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resolves the host port a MiniStack Fargate deploy will bind ({@code localhost:port}).
 */
public final class LocalHostPortPolicy {

    private LocalHostPortPolicy() {
    }

    /**
     * Host port pinned by the MiniStack adapter (container port on {@code localhost}).
     */
    public static int resolveRequiredHostPort(
            DeploymentConfig config,
            ApplicationSpec spec,
            JsonNode canonicalTemplate) {
        if (canonicalTemplate != null && !canonicalTemplate.isMissingNode()) {
            int fromTemplate = TemplateResourceScanner.findEcsContainerPort(canonicalTemplate);
            if (fromTemplate > 0) {
                return fromTemplate;
            }
        }
        if (spec != null) {
            return spec.applicationPort();
        }
        return 0;
    }

    public static boolean isPrivilegedHostPort(int port) {
        return port > 0 && port < 1024;
    }

    public static String portConflictSuggestion(DeploymentTarget target) {
        return switch (target) {
            case MINISTACK -> "Stop or delete the conflicting MiniStack stack, choose a different app, "
                + "or set MINISTACK_PREFLIGHT=warn|off (not recommended). "
                + "Only one app per host port: MiniStack maps localhost:<port> to the container.";
            case LOCALSTACK -> "Stop or delete the conflicting LocalStack stack, choose a different app, "
                + "or set LOCALSTACK_PREFLIGHT=warn|off / CFC_LOCALSTACK_SKIP_PREFLIGHT=true.";
            case AWS -> "";
        };
    }
}
