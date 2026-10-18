package com.cloudforge.core.local;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Target-agnostic adapt → deploy → auth-runtime reconcile pipeline.
 *
 * <p>Concrete targets wire a {@link TemplateAdapter}, {@link LocalDeployer}, and optional
 * {@link LocalAuthRuntime} — for example MiniStack in {@code cloudforge-ministack}.</p>
 */
public final class LocalDeploymentPipeline {
    private final TemplateAdapter templateAdapter;
    private final Function<String, ? extends LocalDeployer> deployerFactory;
    private final LocalAuthRuntime authRuntime;

    public LocalDeploymentPipeline(
            TemplateAdapter templateAdapter,
            Supplier<? extends LocalDeployer> deployerFactory,
            LocalAuthRuntime authRuntime) {
        this(templateAdapter, (Function<String, LocalDeployer>) region -> deployerFactory.get(), authRuntime, true);
    }

    private LocalDeploymentPipeline(
            TemplateAdapter templateAdapter,
            Function<String, ? extends LocalDeployer> deployerFactory,
            LocalAuthRuntime authRuntime,
            boolean unused) {
        this.templateAdapter = templateAdapter;
        this.deployerFactory = deployerFactory;
        this.authRuntime = authRuntime;
    }

    /**
     * A pipeline whose deployer is built for the region a deployment targets. The function receives
     * the region given to {@link #deploy(LocalDeploymentRequest, String)}, or null when none was.
     */
    public static LocalDeploymentPipeline forRegion(
            TemplateAdapter templateAdapter,
            Function<String, ? extends LocalDeployer> regionalDeployerFactory,
            LocalAuthRuntime authRuntime) {
        return new LocalDeploymentPipeline(templateAdapter, regionalDeployerFactory, authRuntime, true);
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
        return deploy(request, null);
    }

    /** Deploys to {@code region}; null uses the deployer's default region. */
    public LocalDeploymentPipelineResult deploy(LocalDeploymentRequest request, String region)
            throws IOException {
        TemplateAdaptationResult adaptation = adapt(request);
        try (LocalDeployer deployer = deployerFactory.apply(region)) {
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
