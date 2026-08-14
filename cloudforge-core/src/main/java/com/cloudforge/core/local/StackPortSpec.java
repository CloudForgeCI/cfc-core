package com.cloudforge.core.local;

import java.net.URI;

/**
 * Metadata for a target-owned StackPort resource browser container.
 */
public record StackPortSpec(
        DeploymentTarget target,
        String containerName,
        String conflictingContainerName,
        String emulatorContainerName,
        String image,
        int hostPort,
        int containerPort,
        String displayName) {

    public StackPortSpec {
        if (target == null || target == DeploymentTarget.AWS) {
            throw new IllegalArgumentException("StackPort spec requires MINISTACK or LOCALSTACK target");
        }
    }

    public static StackPortSpec ministack() {
        return new StackPortSpec(
            DeploymentTarget.MINISTACK,
            LocalEmulatorDefaults.MINISTACK_STACKPORT_CONTAINER,
            LocalEmulatorDefaults.LOCALSTACK_STACKPORT_CONTAINER,
            LocalEmulatorDefaults.MINISTACK_CONTAINER,
            LocalEmulatorDefaults.STACKPORT_IMAGE,
            LocalEmulatorDefaults.STACKPORT_HOST_PORT,
            LocalEmulatorDefaults.STACKPORT_CONTAINER_PORT,
            "MiniStack StackPort");
    }

    public static StackPortSpec localstack() {
        return new StackPortSpec(
            DeploymentTarget.LOCALSTACK,
            LocalEmulatorDefaults.LOCALSTACK_STACKPORT_CONTAINER,
            LocalEmulatorDefaults.MINISTACK_STACKPORT_CONTAINER,
            LocalEmulatorDefaults.LOCALSTACK_CONTAINER,
            LocalEmulatorDefaults.STACKPORT_IMAGE,
            LocalEmulatorDefaults.STACKPORT_HOST_PORT,
            LocalEmulatorDefaults.STACKPORT_CONTAINER_PORT,
            "LocalStack StackPort");
    }

    public static StackPortSpec forTarget(DeploymentTarget target) {
        return switch (target) {
            case MINISTACK -> ministack();
            case LOCALSTACK -> localstack();
            case AWS -> throw new IllegalArgumentException("AWS is not a local emulator target");
        };
    }

    public URI browserUrl() {
        return URI.create("http://" + LocalEmulatorDefaults.GATEWAY_HOST + ":" + hostPort);
    }

    public String emulatorEndpointUrl() {
        return "http://" + emulatorContainerName + ":" + LocalEmulatorDefaults.GATEWAY_PORT;
    }

    /** Named StackPort endpoint key (used in {@code STACKPORT_ENDPOINTS}). */
    public String endpointConfigName() {
        return target == DeploymentTarget.MINISTACK ? "ministack" : "localstack";
    }

    public String stackPortEndpointsEnvValue() {
        return endpointConfigName() + "=" + emulatorEndpointUrl();
    }
}
