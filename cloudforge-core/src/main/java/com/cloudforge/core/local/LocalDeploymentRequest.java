package com.cloudforge.core.local;

import java.nio.file.Path;

/** Inputs for adapt-and-deploy against a local emulator target. */
public record LocalDeploymentRequest(
        DeploymentTarget target,
        String localStackName,
        Path canonicalTemplate,
        Path localTemplate,
        Path adaptationReport,
        String contextStackName) {

    public static LocalDeploymentRequest forTarget(
            DeploymentTarget target,
            String contextStackName,
            Path canonicalTemplate,
            Path outputDirectory) {
        LocalDeploymentArtifactPaths paths =
            LocalDeploymentArtifactPaths.forTarget(outputDirectory, contextStackName, target);
        return new LocalDeploymentRequest(
            target,
            LocalDeploymentNaming.localStackName(contextStackName, target),
            canonicalTemplate,
            paths.localTemplate(),
            paths.adaptationReport(),
            contextStackName);
    }
}
