package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.LocalStackCapabilitySnapshot;
import com.cloudforge.core.local.PreflightMessages;
import com.cloudforge.core.local.PreflightMode;
import com.cloudforge.core.local.PreflightResult;
import com.cloudforgeci.localstack.LocalStackCapabilityProbe;
import com.cloudforgeci.localstack.LocalStackDeployPreflight;
import com.cloudforgeci.localstack.LocalStackDeployer;
import com.cloudforgeci.ministack.MiniStackDeployPreflight;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs target-specific deploy preflight before local emulator pipelines execute.
 */
public final class LocalDeployPreflight {

    private LocalDeployPreflight() {
    }

    public static PreflightOutcome run(DeploymentRequest request) throws IOException {
        DeployMode mode = request.mode();
        if (mode != DeployMode.DEPLOY && mode != DeployMode.DRY_RUN) {
            return PreflightOutcome.skipped();
        }

        PreflightMode preflightMode = modeFor(request.target());
        if (preflightMode == PreflightMode.OFF) {
            return PreflightOutcome.skipped();
        }

        DeploymentConfig config = request.config();
        ApplicationSpec spec = config.applicationSpec;
        Path canonical = request.canonicalTemplate();

        String stackName = config.stackName;
        PreflightResult result = switch (request.target()) {
            case MINISTACK -> MiniStackDeployPreflight.validate(
                config,
                spec,
                canonical,
                java.net.URI.create(com.cloudforgeci.ministack.MiniStackDeployer.resolveEndpoint()),
                stackName);
            case LOCALSTACK -> {
                LocalStackCapabilitySnapshot snapshot =
                    LocalStackCapabilityProbe.probe(LocalStackDeployer.resolveEndpoint());
                yield LocalStackDeployPreflight.validateForDeployment(
                    config, spec, canonical, snapshot, stackName);
            }
            case AWS -> PreflightResult.allowed(request.target());
        };

        return new PreflightOutcome(result, preflightMode, true);
    }

    public static PreflightMode modeFor(DeploymentTarget target) {
        return switch (target) {
            case MINISTACK -> PreflightMode.fromEnv("MINISTACK_PREFLIGHT", PreflightMode.ENFORCE);
            case LOCALSTACK -> {
                if (Boolean.parseBoolean(System.getenv().getOrDefault(
                        "CFC_LOCALSTACK_SKIP_PREFLIGHT", "false"))) {
                    yield PreflightMode.OFF;
                }
                yield PreflightMode.fromEnv("LOCALSTACK_PREFLIGHT", PreflightMode.ENFORCE);
            }
            case AWS -> PreflightMode.OFF;
        };
    }

    public record PreflightOutcome(PreflightResult result, PreflightMode mode, boolean ran) {

        public PreflightOutcome(PreflightResult result, PreflightMode mode) {
            this(result, mode, true);
        }

        public static PreflightOutcome skipped() {
            return new PreflightOutcome(null, PreflightMode.OFF, false);
        }

        public List<String> warningMessages() {
            if (result == null) {
                return List.of();
            }
            List<String> messages = new ArrayList<>();
            for (var warning : result.warnings()) {
                messages.add("[" + warning.ruleId() + "] " + warning.message());
            }
            return messages;
        }

        public void throwIfBlocked() throws IOException {
            if (!ran || result == null) {
                return;
            }
            result.throwIfBlocked(mode);
        }

        public String formattedWarnings() {
            return result == null ? "" : PreflightMessages.formatWarnings(result);
        }
    }
}
