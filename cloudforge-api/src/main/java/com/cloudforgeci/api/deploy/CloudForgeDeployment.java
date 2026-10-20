package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentContextPreparer;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.EmulatorEdgeLifecycle;
import com.cloudforge.core.local.LocalDeployResult;
import com.cloudforge.core.local.LocalSameApplicationStackReplacer;
import com.cloudforge.core.local.TemplateAdaptationResult;
import com.cloudforgeci.api.deploy.aws.AwsDirectDeployer;
import com.cloudforgeci.api.deploy.aws.AwsStackDeployResult;
import com.cloudforgeci.api.deploy.aws.StackProgressListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Direct-to-AWS deploy façade (the {@code deploy:create}/{@code deploy:catalog} work). MiniStack
 * and LocalStack deploys are dispatched by {@code cloudforge-cli} directly against those targets'
 * own modules — cloudforge-api has no dependency on either, so this class only ever handles AWS.
 *
 * <p>The AWS case needs no template adaptation (real ALB/Route53 need no local port-remapping),
 * no {@link LocalSameApplicationStackReplacer} (auto-deleting a caller's other AWS stacks on
 * redeploy is a MiniStack/LocalStack local-resource convenience, not a safe default against real
 * infrastructure — {@link DeployOptions#replaceSameApplication()} is ignored for this target), no
 * emulator-edge reconciliation. See {@link AwsDirectDeployer} for the actual CloudFormation
 * mechanics and the CloudForge stack tagging every AWS deploy applies.</p>
 */
public final class CloudForgeDeployment {

    private CloudForgeDeployment() {
    }

    public static DeploymentResult deploy(DeploymentRequest request) throws IOException {
        return deploy(request, null);
    }

    /**
     * Same as {@link #deploy(DeploymentRequest)}, additionally reporting each CloudFormation
     * stack event to {@code listener} as it happens. Only {@link DeploymentTarget#AWS} requests
     * are supported here — see the class javadoc.
     */
    public static DeploymentResult deploy(DeploymentRequest request, StackProgressListener listener)
            throws IOException {
        if (request.target() != DeploymentTarget.AWS) {
            throw new UnsupportedOperationException(
                "CloudForgeDeployment only handles AWS deploys; " + request.target()
                    + " is dispatched by cloudforge-cli directly.");
        }
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
        return deployAws(request, preflight, listener);
    }

    /**
     * Whether a local-emulator deploy deletes the other stacks of the same application. A
     * deployment with its own {@code subdomain} is an additional named instance reached on its
     * own hostname, so it leaves the others in place; one without a subdomain replaces its
     * predecessor. Used by cloudforge-cli's MiniStack/LocalStack dispatch, not by this class's
     * own (AWS-only) deploy path.
     */
    public static boolean replacesSameApplication(DeploymentRequest request) {
        String subdomain = request.config().subdomain;
        return request.options().replaceSameApplication()
            && request.mode() == DeployMode.DEPLOY
            && (subdomain == null || subdomain.isBlank());
    }

    private static DeploymentResult deployAws(
            DeploymentRequest request,
            LocalDeployPreflight.PreflightOutcome preflight,
            StackProgressListener listener) throws IOException {
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
                    // previewChangeSet is the create-time equivalent of `cdk deploy --no-execute`:
                    // creates a real change set, reports what it would do, deletes it unexecuted.
                    AwsStackDeployResult preview = deployer.previewChangeSet(stackName, request.canonicalTemplate());
                    messages.add(preview.noOp()
                        ? "No changes for " + stackName
                        : "Would " + (preview.created() ? "create " : "update ") + stackName
                            + " (" + preview.changeSummaries().size() + " resource changes)");
                    messages.addAll(preview.changeSummaries());
                }
                case DEPLOY -> {
                    AwsStackDeployResult result =
                        deployer.deploy(stackName, request.canonicalTemplate(), listener);
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
                // Catalog persistence is best-effort bookkeeping and must never abort the deploy,
                // so RuntimeException is caught as well as IOException.
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

    /**
     * Shared result-assembly tail for a local-emulator (MiniStack/LocalStack) deploy: persists
     * the deployment-contexts catalog, reconciles the emulator edge, and wraps everything into a
     * {@link DeploymentResult}. Called by cloudforge-cli's own MiniStack/LocalStack dispatch,
     * which has no access to this class's AWS-only deploy path otherwise.
     */
    public static DeploymentResult finalizeResult(
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
                // Must never abort the deploy: reconcileEmulatorEdge below must still run, or the
                // new stack's nginx route is never added ("CloudForge application route not found").
                // Catches RuntimeException as well as IOException as a second line of defense
                // behind DeploymentContextCatalog's own broad catch.
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
