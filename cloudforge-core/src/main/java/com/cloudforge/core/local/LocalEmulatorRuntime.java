package com.cloudforge.core.local;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * Docker lifecycle for a local AWS emulator (MiniStack or LocalStack).
 *
 * <p>Resolve implementations via {@link LocalEmulatorRuntimes}. Metadata lives in
 * {@link LocalEmulatorSpec}; shared defaults in {@link LocalEmulatorDefaults}.</p>
 */
public interface LocalEmulatorRuntime {

    /** Deployment target this runtime serves. */
    DeploymentTarget target();

    /** Stable Docker container name (for example {@code cfc-ministack}). */
    String containerName();

    /** Optional container that must be stopped first because it binds the same gateway port. */
    Optional<String> conflictingContainerName();

    /** Gateway health URL on the host (for example {@code http://localhost:4566/_ministack/health}). */
    URI healthEndpoint();

    /** Start the emulator container and wait until {@link #isHealthy()}. */
    void start() throws IOException;

    /** Stop the emulator container if it is running. */
    void stop() throws IOException;

    /** Stop then start. */
    default void restart() throws IOException {
        stop();
        start();
    }

    /** Whether the named container is in the running state. */
    boolean isRunning() throws IOException;

    /** Whether the gateway health endpoint returns HTTP 2xx. */
    boolean isHealthy();
}
