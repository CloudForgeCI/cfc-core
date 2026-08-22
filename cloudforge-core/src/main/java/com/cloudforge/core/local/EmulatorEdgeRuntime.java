package com.cloudforge.core.local;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Shared nginx edge in front of MiniStack/LocalStack host-published ECS ports.
 *
 * <p>Lifecycle is owned by {@link EmulatorEdgeLifecycle} and by {@link EmulatorLifecycle}
 * companion orchestration (start/stop/restart with the emulator).</p>
 */
public interface EmulatorEdgeRuntime {

    String containerName();

    URI browserUrl();

    void start() throws IOException;

    void stop() throws IOException;

    default void restart() throws IOException {
        stop();
        start();
    }

    /** Remove container and recreate (same image; refresh bind mounts). */
    void rebuild() throws IOException;

    void reload() throws IOException;

    /** Rewrite vhosts from Docker publishes and reload if the container is running. */
    Map<String, Integer> reconcile() throws IOException;

    /**
     * Ensure the edge is running, then reconcile routes. Used after emulator start and
     * after local deploys so Host routing stays current without a separate step.
     */
    default Map<String, Integer> ensureRunningAndReconcile() throws IOException {
        if (!isRunning()) {
            start();
            return reconcile();
        }
        return reconcile();
    }

    boolean isRunning() throws IOException;

    boolean isHealthy();
}
