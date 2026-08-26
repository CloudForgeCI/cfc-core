package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentContextPreparer;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.EmulatorEdgeLifecycle;
import com.cloudforge.core.local.LocalDeployResult;
import com.cloudforge.core.local.LocalDeploymentPipelineResult;
import com.cloudforge.core.local.LocalDeploymentRequest;
import com.cloudforge.core.local.LocalSameApplicationStackReplacer;
import com.cloudforge.core.local.TemplateAdaptationResult;
import com.cloudforgeci.api.deploy.aws.AwsDirectDeployer;
import com.cloudforgeci.api.deploy.aws.AwsStackDeployResult;
import com.cloudforgeci.localstack.LocalStackDeployer;
import com.cloudforgeci.localstack.LocalStackDeploymentPipeline;
import com.cloudforgeci.ministack.MiniStackDeployer;
import com.cloudforgeci.ministack.MiniStackDeploymentPipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central deploy façade for MiniStack, LocalStack, and (as of the {@code deploy:create}/
 * {@code deploy:catalog} work) direct-to-AWS.
 *
 * <p>The AWS case is deliberately the simplest of the three: no template adaptation (real ALB/
 * Route53 need no local port-remapping), no {@link LocalSameApplicationStackReplacer} (auto-
 * deleting a caller's other AWS stacks on redeploy is a MiniStack/LocalStack local-resource
 * convenience, not a safe default against real infrastructure — {@link DeployOptions
 * #replaceSameApplication()} is ignored for this target), no emulator-edge reconciliation. See
 * {@link AwsDirectDeployer} for the actual CloudFormation mechanics and the CloudForge stack
 * tagging every AWS deploy applies.</p>
 */
public final class CloudForgeDeployment {

    private CloudForgeDeployment() {
    }

    public static DeploymentResult deploy(DeploymentRequest request) throws IOException {
        if (request.config().applicationSpec != null) {
            DeploymentContextPreparer.prepare(
                request.config(),
                request.config().applicationSpec,
                request.target().configKey());
        }
        LocalDeployPreflight.PreflightOutcome preflight = LocalDeployPreflight.run(request);
        preflight.throwIfBlocked();
        if (preflight.ran() && !preflight.formattedWarnings().isBlank()) {
            System.out.println("\n" + preflight.formattedWarnings());
        }
        return switch (request.target()) {
            case MINISTACK -> deployMiniStack(request, preflight);
            case LOCALSTACK -> deployLocalStack(request, preflight);
            case AWS -> deployAws(request, preflight);
        };
    }

    private static DeploymentResult deployMiniStack(
            DeploymentRequest request,
            LocalDeployPreflight.PreflightOutcome preflight) throws IOException {
        LocalDeploymentRequest localRequest = LocalDeploymentRequest.forTarget(
            DeploymentTarget.MINISTACK,
            request.config().stackName,
            request.canonicalTemplate(),
            request.outputDirectory());

        String endpoint = MiniStackDeployer.resolveEndpoint();
        LocalSameApplicationStackReplacer.Result replaceResult = null;
        if (request.options().replaceSameApplication() && request.mode() == DeployMode.DEPLOY) {
            try (MiniStackDeployer deployer = new MiniStackDeployer()) {
                replaceResult = deployer.replaceSameApplicationStacks(
                    request.config().applicationId,
                    localRequest.localStackName(),
                    request.options().catalogDirectory());
            }
        }

        TemplateAdaptationResult adaptation = null;
        LocalDeployResult deployment = null;
        Map<String, String> outputs = Map.of();

        switch (request.mode()) {
            case DRY_RUN -> adaptation = MiniStackDeploymentPipeline.adapt(localRequest);
            case DEPLOY -> {
                LocalDeploymentPipelineResult pipelineResult =
                    MiniStackDeploymentPipeline.deploy(localRequest);
                adaptation = pipelineResult.adaptation();
                deployment = pipelineResult.deployment();
                outputs = deployment.outputs();
            }
            case VERIFY -> {
                try (MiniStackDeployer deployer = new MiniStackDeployer()) {
                    outputs = deployer.verifyDeployment(localRequest.localStackName());
                }
            }
        }

        return finalizeResult(
            request,
            preflight,
            localRequest.localStackName(),
            endpoint,
            localRequest.adaptationReport(),
            adaptation,
            deployment,
            replaceResult,
            outputs);
    }

    private static DeploymentResult deployLocalStack(
            DeploymentRequest request,
            LocalDeployPreflight.PreflightOutcome preflight) throws IOException {
        LocalDeploymentRequest localRequest = LocalDeploymentRequest.forTarget(
            DeploymentTarget.LOCALSTACK,
            request.config().stackName,
            request.canonicalTemplate(),
            request.outputDirectory());

        String endpoint = LocalStackDeployer.resolveEndpoint();
        LocalSameApplicationStackReplacer.Result replaceResult = null;
        if (request.options().replaceSameApplication() && request.mode() == DeployMode.DEPLOY) {
            try (LocalStackDeployer deployer = new LocalStackDeployer()) {
                replaceResult = deployer.replaceSameApplicationStacks(
                    request.config().applicationId,
                    localRequest.localStackName(),
                    request.options().catalogDirectory());
            }
        }

        TemplateAdaptationResult adaptation = null;
        LocalDeployResult deployment = null;
        Map<String, String> outputs = Map.of();

        switch (request.mode()) {
            case DRY_RUN -> adaptation = LocalStackDeploymentPipeline.adapt(localRequest);
            case DEPLOY -> {
                LocalDeploymentPipelineResult pipelineResult =
                    LocalStackDeploymentPipeline.deploy(localRequest);
                adaptation = pipelineResult.adaptation();
                deployment = pipelineResult.deployment();
                outputs = deployment.outputs();
            }
            case VERIFY -> {
                try (LocalStackDeployer deployer = new LocalStackDeployer()) {
                    outputs = deployer.verifyDeployment(localRequest.localStackName());
                }
            }
        }

        return finalizeResult(
            request,
            preflight,
            localRequest.localStackName(),
            endpoint,
            localRequest.adaptationReport(),
            adaptation,
            deployment,
            replaceResult,
            outputs);
    }

    private static DeploymentResult deployAws(
            DeploymentRequest request,
            LocalDeployPreflight.PreflightOutcome preflight) throws IOException {
        String stackName = request.config().stackName;
        List<String> messages = new ArrayList<>();
        if (preflight != null && preflight.ran()) {
            messages.addAll(preflight.warningMessages());
        }
        Map<String, String> outputs = Map.of();

        try (AwsDirectDeployer deployer = new AwsDirectDeployer(
                request.config(), request.target(), request.options().credentialsOverride())) {
            switch (request.mode()) {
                case DRY_RUN -> {
                    // No adaptation pipeline for AWS — the canonical template deploys as-is.
                    // Nothing to do beyond preflight (already run above).
                }
                case DEPLOY -> {
                    AwsStackDeployResult result = deployer.deploy(stackName, request.canonicalTemplate());
                    outputs = result.outputs();
                    messages.add(result.noOp()
                        ? "No changes for " + stackName
                        : (result.created() ? "Created " : "Updated ") + stackName
                            + " (" + result.changeSummaries().size() + " resource changes)");
                }
                case VERIFY -> outputs = deployer.verifyDeployment(stackName);
            }
        }

        List<java.nio.file.Path> catalogPaths = List.of();
        if (request.mode() == DeployMode.DEPLOY && request.options().persistCatalog()) {
            try {
                catalogPaths = DeploymentContextCatalog.persist(
                    request.config(),
                    request.options().catalogDirectory(),
                    request.options().managerVolumeRoots());
            } catch (IOException | RuntimeException e) {
                // Catalog persistence is best-effort bookkeeping, not the deploy itself — must
                // never abort the caller. Broadened beyond IOException after a real
                // NullPointerException from DeploymentContextCatalog escaped this same shape of
                // catch elsewhere and took down an entire deploy; see that fix's javadoc.
                messages.add("Could not write deployment-contexts catalog: " + e.getMessage());
            }
        }

        return new DeploymentResult(
            request.target(),
            request.mode(),
            stackName,
            request.config().region,
            null,
            null,
            null,
            null,
            outputs,
            catalogPaths,
            messages,
            false);
    }

    private static DeploymentResult finalizeResult(
            DeploymentRequest request,
            LocalDeployPreflight.PreflightOutcome preflight,
            String localStackName,
            String endpoint,
            java.nio.file.Path adaptationReport,
            TemplateAdaptationResult adaptation,
            LocalDeployResult deployment,
            LocalSameApplicationStackReplacer.Result replaceResult,
            Map<String, String> outputs) throws IOException {
        List<String> messages = new ArrayList<>();
        if (preflight != null && preflight.ran()) {
            messages.addAll(preflight.warningMessages());
        }
        List<java.nio.file.Path> catalogPaths = List.of();
        boolean historyRecorded = false;

        if (request.mode() == DeployMode.DEPLOY && request.options().persistCatalog()) {
            try {
                catalogPaths = DeploymentContextCatalog.persist(
                    request.config(),
                    request.options().catalogDirectory(),
                    request.options().managerVolumeRoots());
            } catch (IOException | RuntimeException e) {
                // Must never abort the deploy — reconcileEmulatorEdge below depends on reaching
                // this point. Broadened beyond IOException: a real NullPointerException from
                // DeploymentContextCatalog.registerKnownStack (a null-parent Path — fixed there
                // now, but this catch stays broad as a second line of defense) escaped an
                // IOException-only catch here, silently skipping emulator-edge reconciliation for
                // every local deploy afterward in that run — the new stack's route never got
                // added to nginx, surfacing as "CloudForge application route not found" in a
                // browser hitting its hostname. Discovered via a real LocalStack deploy, not a
                // test — nothing in the test suite exercises this catalog-persist call chain
                // against a real filesystem layout where catalogDirectory is a bare relative path.
                messages.add("Could not write deployment-contexts catalog: " + e.getMessage());
            }
        }

        if (request.mode() == DeployMode.DEPLOY) {
            reconcileEmulatorEdge(messages);
        }

        return new DeploymentResult(
            request.target(),
            request.mode(),
            localStackName,
            endpoint,
            adaptationReport,
            adaptation,
            deployment,
            replaceResult,
            outputs,
            catalogPaths,
            messages,
            historyRecorded);
    }

    /**
     * Keep {@code *.cloudforge.localhost} Host routes in sync after a local deploy publishes
     * new ECS host ports. No-op when edge companions are disabled.
     */
    private static void reconcileEmulatorEdge(List<String> messages) {
        if (!EmulatorEdgeLifecycle.autostartEnabled()) {
            return;
        }
        try {
            EmulatorEdgeLifecycle.ensureRunningAndReconcile();
        } catch (Exception e) {
            messages.add("Emulator edge reconcile skipped: " + e.getMessage());
        }
    }
}
