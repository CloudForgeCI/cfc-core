package com.cloudforge.core.local;

import java.nio.file.Path;

/** Canonical, adapted, and adaptation-report paths for a local deployment. */
public record LocalDeploymentArtifactPaths(
        Path canonicalTemplate,
        Path localTemplate,
        Path adaptationReport) {

    public static LocalDeploymentArtifactPaths forTarget(
            Path outputDirectory,
            String contextStackName,
            DeploymentTarget target) {
        String suffix = LocalDeploymentNaming.artifactSuffix(target);
        return new LocalDeploymentArtifactPaths(
            outputDirectory.resolve(contextStackName + ".template.json"),
            outputDirectory.resolve(contextStackName + "." + suffix + ".template.json"),
            outputDirectory.resolve(contextStackName + "." + suffix + "-adaptations.json"));
    }
}
