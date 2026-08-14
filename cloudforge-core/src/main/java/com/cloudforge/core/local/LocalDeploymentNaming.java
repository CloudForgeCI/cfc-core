package com.cloudforge.core.local;

/** Stack and artifact naming conventions for local deployment targets. */
public final class LocalDeploymentNaming {
    private LocalDeploymentNaming() {
    }

    public static String localStackName(String stackName, DeploymentTarget target) {
        if (stackName == null || stackName.isBlank()) {
            throw new IllegalArgumentException("stackName is required for local deployment");
        }
        String suffix = switch (target) {
            case MINISTACK -> "-ministack";
            case LOCALSTACK -> "-localstack";
            case AWS -> throw new IllegalArgumentException(
                "AWS deployments do not use local stack naming");
        };
        return stackName.endsWith(suffix) ? stackName : stackName + suffix;
    }

    public static String artifactSuffix(DeploymentTarget target) {
        return switch (target) {
            case MINISTACK -> "ministack";
            case LOCALSTACK -> "localstack";
            case AWS -> throw new IllegalArgumentException(
                "AWS deployments do not use local artifact suffixes");
        };
    }
}
