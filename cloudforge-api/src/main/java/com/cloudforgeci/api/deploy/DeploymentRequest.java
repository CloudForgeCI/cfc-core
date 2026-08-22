package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.local.DeploymentTarget;

import java.nio.file.Path;
import java.util.Objects;

/** Inputs for {@link CloudForgeDeployment}. */
public record DeploymentRequest(
        DeploymentConfig config,
        DeploymentTarget target,
        DeployMode mode,
        Path canonicalTemplate,
        Path outputDirectory,
        DeployOptions options) {

    public DeploymentRequest {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mode, "mode");
        if (outputDirectory == null) {
            outputDirectory = Path.of("cdk.out");
        }
        if (options == null) {
            options = DeployOptions.defaults();
        }
        if (target == DeploymentTarget.AWS && (mode == DeployMode.DEPLOY || mode == DeployMode.DRY_RUN)
                && (config.applicationId == null || config.applicationId.isBlank())) {
            throw new IllegalArgumentException(
                "config.applicationId is required for AWS deploy — used to tag the stack "
                    + "(cloudforge:application) for inventory and the deploy:create IAM condition");
        }
        if ((mode == DeployMode.DEPLOY || mode == DeployMode.DRY_RUN) && canonicalTemplate == null) {
            throw new IllegalArgumentException("canonicalTemplate is required for mode " + mode);
        }
        if (config.stackName == null || config.stackName.isBlank()) {
            throw new IllegalArgumentException("config.stackName is required");
        }
    }

    public static DeploymentRequest deploy(
            DeploymentConfig config,
            DeploymentTarget target,
            Path canonicalTemplate,
            Path outputDirectory) {
        return new DeploymentRequest(
            config, target, DeployMode.DEPLOY, canonicalTemplate, outputDirectory, DeployOptions.defaults());
    }

    public static DeploymentRequest dryRun(
            DeploymentConfig config,
            DeploymentTarget target,
            Path canonicalTemplate,
            Path outputDirectory) {
        return new DeploymentRequest(
            config,
            target,
            DeployMode.DRY_RUN,
            canonicalTemplate,
            outputDirectory,
            DeployOptions.defaults().withoutCatalog());
    }

    public static DeploymentRequest verify(DeploymentConfig config, DeploymentTarget target) {
        return new DeploymentRequest(
            config, target, DeployMode.VERIFY, null, Path.of("cdk.out"), DeployOptions.defaults().withoutCatalog());
    }
}
