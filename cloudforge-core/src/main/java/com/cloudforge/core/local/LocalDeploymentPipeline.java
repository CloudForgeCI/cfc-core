package com.cloudforge.core.local;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Target-agnostic adapt → deploy → auth-runtime reconcile pipeline.
 *
 * <p>Concrete targets wire a {@link TemplateAdapter}, {@link LocalDeployer}, and optional
 * {@link LocalAuthRuntime} — for example MiniStack in {@code cloudforge-ministack}.</p>
 */
public final class LocalDeploymentPipeline {
    private final TemplateAdapter templateAdapter;
    private final Supplier<? extends LocalDeployer> deployerFactory;
    private final LocalAuthRuntime authRuntime;

    public LocalDeploymentPipeline(
            TemplateAdapter templateAdapter,
            Supplier<? extends LocalDeployer> deployerFactory,
            LocalAuthRuntime authRuntime) {
        this.templateAdapter = templateAdapter;
        this.deployerFactory = deployerFactory;
        this.authRuntime = authRuntime;
    }

    public TemplateAdaptationResult adapt(LocalDeploymentRequest request) throws IOException {
        return TemplateAdapterSupport.adaptFile(
            templateAdapter,
            request.canonicalTemplate(),
            request.localTemplate(),
            request.adaptationReport(),
            request.contextStackName());
    }

    public LocalDeploymentPipelineResult deploy(LocalDeploymentRequest request)
            throws IOException {
        TemplateAdaptationResult adaptation = adapt(request);
        try (LocalDeployer deployer = deployerFactory.get()) {
            LocalDeployResult deployment = deployer.deploy(
                request.localStackName(),
                request.localTemplate());
            if (authRuntime != null) {
                authRuntime.reconcile(
                    templateAdapter.requiresLocalAuthRuntime(adaptation),
                    templateAdapter.applicationUrl(adaptation));
            }
            return new LocalDeploymentPipelineResult(adaptation, deployment);
        }
    }
}
