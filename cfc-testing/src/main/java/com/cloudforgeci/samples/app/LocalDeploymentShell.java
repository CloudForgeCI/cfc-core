package com.cloudforgeci.samples.app;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforgeci.api.deploy.CanonicalTemplateResolver;
import com.cloudforgeci.api.deploy.CloudForgeDeployment;
import com.cloudforgeci.api.deploy.DeployMode;
import com.cloudforgeci.api.deploy.DeployOptions;
import com.cloudforgeci.api.deploy.DeploymentRequest;
import com.cloudforgeci.api.deploy.DeploymentResult;
import software.amazon.awscdk.cxapi.CloudAssembly;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Thin sample entrypoint for local emulator deploys via {@link CloudForgeDeployment}.
 *
 * <p>Copy this class into cloudforge-sample (or any Java app): BOM + cloudforge-api +
 * optional target modules, then call {@link #deploy} after CDK synth.</p>
 */
public final class LocalDeploymentShell {

    private static final Path DEFAULT_OUTPUT = Path.of("cdk.out");

    private LocalDeploymentShell() {
    }

    public static DeploymentResult deploy(
            DeploymentConfig config,
            DeploymentTarget target,
            CloudAssembly assembly,
            DeployOptions options) throws IOException {
        return CloudForgeDeployment.deploy(deployRequest(
            config, target, DeployMode.DEPLOY, assembly, DEFAULT_OUTPUT, options));
    }

    public static DeploymentResult dryRun(
            DeploymentConfig config,
            DeploymentTarget target,
            CloudAssembly assembly) throws IOException {
        return CloudForgeDeployment.deploy(deployRequest(
            config,
            target,
            DeployMode.DRY_RUN,
            assembly,
            DEFAULT_OUTPUT,
            DeployOptions.defaults().withoutCatalog()));
    }

    public static DeploymentResult verify(DeploymentConfig config, DeploymentTarget target)
            throws IOException {
        return CloudForgeDeployment.deploy(
            DeploymentRequest.verify(config, target));
    }

    private static DeploymentRequest deployRequest(
            DeploymentConfig config,
            DeploymentTarget target,
            DeployMode mode,
            CloudAssembly assembly,
            Path outputDirectory,
            DeployOptions options) throws IOException {
        Path canonical = CanonicalTemplateResolver.resolve(
            outputDirectory, config.stackName, assembly);
        return new DeploymentRequest(config, target, mode, canonical, outputDirectory, options);
    }
}
