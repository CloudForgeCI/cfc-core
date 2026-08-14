package com.cloudforge.core.local;

import java.io.IOException;
import java.net.URI;

/**
 * Docker lifecycle for a target-owned StackPort resource browser.
 *
 * <p>Resolve implementations via {@link StackPortRuntimes}.</p>
 */
public interface StackPortRuntime {

    DeploymentTarget target();

    String containerName();

    URI browserUrl();

    void start() throws IOException;

    void stop() throws IOException;

    default void restart() throws IOException {
        stop();
        start();
    }

    /** Pull the latest image, remove the container, and start fresh. */
    void rebuild() throws IOException;

    boolean isRunning() throws IOException;

    boolean isHealthy();
}
