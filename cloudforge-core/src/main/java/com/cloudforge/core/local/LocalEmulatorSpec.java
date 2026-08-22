package com.cloudforge.core.local;

import java.net.URI;

/**
 * Immutable metadata for a CloudForge-managed local emulator container.
 */
public record LocalEmulatorSpec(
        DeploymentTarget target,
        String containerName,
        String conflictingContainerName,
        String image,
        String healthPath,
        String displayName) {

    public LocalEmulatorSpec {
        if (target == null || target == DeploymentTarget.AWS) {
            throw new IllegalArgumentException("Emulator spec requires MINISTACK or LOCALSTACK target");
        }
    }

    public static LocalEmulatorSpec ministack() {
        return new LocalEmulatorSpec(
            DeploymentTarget.MINISTACK,
            LocalEmulatorDefaults.MINISTACK_CONTAINER,
            LocalEmulatorDefaults.LOCALSTACK_CONTAINER,
            LocalEmulatorDefaults.MINISTACK_IMAGE,
            LocalEmulatorDefaults.MINISTACK_HEALTH_PATH,
            "MiniStack");
    }

    public static LocalEmulatorSpec localstack() {
        return new LocalEmulatorSpec(
            DeploymentTarget.LOCALSTACK,
            LocalEmulatorDefaults.LOCALSTACK_CONTAINER,
            LocalEmulatorDefaults.MINISTACK_CONTAINER,
            LocalEmulatorDefaults.LOCALSTACK_IMAGE,
            LocalEmulatorDefaults.LOCALSTACK_HEALTH_PATH,
            "LocalStack");
    }

    public static LocalEmulatorSpec forTarget(DeploymentTarget target) {
        return switch (target) {
            case MINISTACK -> ministack();
            case LOCALSTACK -> localstack();
            case AWS -> throw new IllegalArgumentException("AWS is not a local emulator target");
        };
    }

    public URI healthEndpoint() {
        return URI.create("http://" + LocalEmulatorDefaults.GATEWAY_HOST + ":"
            + LocalEmulatorDefaults.GATEWAY_PORT + healthPath);
    }

    public URI gatewayEndpoint() {
        return URI.create("http://" + LocalEmulatorDefaults.GATEWAY_HOST + ":"
            + LocalEmulatorDefaults.GATEWAY_PORT);
    }
}
