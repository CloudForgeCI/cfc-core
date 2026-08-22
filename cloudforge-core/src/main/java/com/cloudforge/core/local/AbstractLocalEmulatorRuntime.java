package com.cloudforge.core.local;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Shared Docker lifecycle for CloudForge local emulators.
 *
 * <p>Target modules supply {@link #dockerCreateArgs()} only; container names, health URLs,
 * and conflict handling come from {@link LocalEmulatorSpec} in core.</p>
 */
public abstract class AbstractLocalEmulatorRuntime implements LocalEmulatorRuntime {

    private final LocalEmulatorSpec spec;

    protected AbstractLocalEmulatorRuntime(LocalEmulatorSpec spec) {
        this.spec = spec;
    }

    protected LocalEmulatorSpec spec() {
        return spec;
    }

    /** Arguments after {@code docker run -d} when the container does not yet exist. */
    protected abstract List<String> dockerCreateArgs() throws IOException;

    @Override
    public DeploymentTarget target() {
        return spec.target();
    }

    @Override
    public String containerName() {
        return spec.containerName();
    }

    @Override
    public Optional<String> conflictingContainerName() {
        return Optional.ofNullable(spec.conflictingContainerName());
    }

    @Override
    public URI healthEndpoint() {
        return spec.healthEndpoint();
    }

    @Override
    public void start() throws IOException {
        stopConflicting();
        DockerEmulatorSupport.ensureNetwork(LocalEmulatorDefaults.DOCKER_NETWORK);

        if (DockerEmulatorSupport.isContainerRunning(spec.containerName())) {
            if (isHealthy()) {
                System.out.println(spec.displayName() + " already running and healthy on :"
                    + LocalEmulatorDefaults.GATEWAY_PORT);
                return;
            }
            DockerEmulatorSupport.stopContainer(spec.containerName());
        }

        if (DockerEmulatorSupport.containerExists(spec.containerName())) {
            DockerEmulatorSupport.startExistingContainer(spec.containerName());
        } else {
            DockerEmulatorSupport.runDetached(dockerCreateArgs());
        }

        DockerEmulatorSupport.waitForHealthy(healthEndpoint());
        System.out.println(spec.displayName() + " started: " + healthEndpoint());
        onStarted();
    }

    @Override
    public void stop() throws IOException {
        DockerEmulatorSupport.stopContainer(spec.containerName());
        System.out.println(spec.displayName() + " stopped");
    }

    @Override
    public boolean isRunning() throws IOException {
        return DockerEmulatorSupport.isContainerRunning(spec.containerName());
    }

    @Override
    public boolean isHealthy() {
        return DockerEmulatorSupport.isHttpHealthy(healthEndpoint());
    }

    protected void onStarted() throws IOException {
        // Optional hook for target-specific post-start logging
    }

    protected final List<String> baseDockerCreateArgs() {
        return List.of(
            "run", "-d",
            "--name", spec.containerName(),
            "--network", LocalEmulatorDefaults.DOCKER_NETWORK,
            "-p", LocalEmulatorDefaults.GATEWAY_PORT + ":4566",
            "-v", LocalEmulatorPaths.dockerSocketMount());
    }

    private void stopConflicting() throws IOException {
        if (spec.conflictingContainerName() != null && !spec.conflictingContainerName().isBlank()) {
            DockerEmulatorSupport.stopContainer(spec.conflictingContainerName());
        }
    }
}
