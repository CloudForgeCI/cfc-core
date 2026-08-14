package com.cloudforge.core.local;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Applies {@link EmulatorLifecycleAction} to a {@link LocalEmulatorRuntime}.
 *
 * <p>Companion services (StackPort simulated console + nginx emulator edge) are part of the
 * same lifecycle process: started after the emulator, stopped with it (edge only when no
 * emulator remains), and reported by {@link EmulatorLifecycleAction#STATUS}. Disable with
 * {@code CFC_EMULATOR_COMPANIONS}, {@code CFC_STACKPORT_AUTOSTART}, or {@code CFC_EDGE_AUTOSTART}.
 */
public final class EmulatorLifecycle {

    private EmulatorLifecycle() {
    }

    public static void execute(DeploymentTarget target, EmulatorLifecycleAction action)
            throws IOException {
        execute(LocalEmulatorRuntimes.forTarget(target), action);
    }

    public static void execute(LocalEmulatorRuntime runtime, EmulatorLifecycleAction action)
            throws IOException {
        DeploymentTarget target = runtime.target();
        switch (action) {
            case START -> {
                runtime.start();
                startCompanions(target);
            }
            case STOP -> {
                stopStackPort(target);
                runtime.stop();
                stopEdgeIfIdle();
            }
            case RESTART -> {
                stopStackPort(target);
                runtime.restart();
                startCompanions(target);
            }
            case STATUS -> {
                logStatus(runtime);
                logCompanionStatus(target);
            }
        }
    }

    public static void executeUnchecked(DeploymentTarget target, EmulatorLifecycleAction action) {
        try {
            execute(target, action);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void logStatus(LocalEmulatorRuntime runtime) throws IOException {
        System.out.println(runtime.target() + " container " + runtime.containerName()
            + " running=" + runtime.isRunning()
            + " healthy=" + runtime.isHealthy()
            + " endpoint=" + runtime.healthEndpoint());
    }

    private static void startCompanions(DeploymentTarget target) throws IOException {
        if (!companionsEnabled()) {
            System.out.println("Emulator companions skipped (CFC_EMULATOR_COMPANIONS=false)");
            return;
        }
        if (stackPortAutostartEnabled()) {
            StackPortLifecycle.execute(target, StackPortLifecycleAction.START);
        }
        if (EmulatorEdgeLifecycle.autostartEnabled()) {
            EmulatorEdgeLifecycle.ensureRunningAndReconcile();
        }
    }

    private static void stopStackPort(DeploymentTarget target) throws IOException {
        if (!companionsEnabled() || !stackPortAutostartEnabled()) {
            return;
        }
        StackPortLifecycle.execute(target, StackPortLifecycleAction.STOP);
    }

    /**
     * Edge is shared across MiniStack/LocalStack — stop only when neither emulator is up.
     */
    private static void stopEdgeIfIdle() throws IOException {
        if (!companionsEnabled() || !EmulatorEdgeLifecycle.autostartEnabled()) {
            return;
        }
        if (anyEmulatorRunning()) {
            EmulatorEdgeLifecycle.execute(EmulatorEdgeLifecycleAction.RECONCILE);
            return;
        }
        EmulatorEdgeLifecycle.execute(EmulatorEdgeLifecycleAction.STOP);
    }

    private static boolean anyEmulatorRunning() {
        for (LocalEmulatorRuntime runtime : LocalEmulatorRuntimes.all()) {
            try {
                if (runtime.isRunning()) {
                    return true;
                }
            } catch (IOException ignored) {
                // treat as not running
            }
        }
        return false;
    }

    private static void logCompanionStatus(DeploymentTarget target) {
        try {
            StackPortRuntime stackPort = StackPortRuntimes.forTarget(target);
            System.out.println(target + " StackPort container " + stackPort.containerName()
                + " running=" + stackPort.isRunning()
                + " healthy=" + stackPort.isHealthy()
                + " url=" + stackPort.browserUrl());
        } catch (Exception e) {
            System.out.println(target + " StackPort: unavailable (" + e.getMessage() + ")");
        }
        try {
            EmulatorEdgeRuntime edge = new DefaultEmulatorEdgeRuntime();
            System.out.println("Emulator edge container " + edge.containerName()
                + " running=" + edge.isRunning()
                + " healthy=" + edge.isHealthy()
                + " url=" + edge.browserUrl());
        } catch (Exception e) {
            System.out.println("Emulator edge: unavailable (" + e.getMessage() + ")");
        }
    }

    private static boolean companionsEnabled() {
        return EmulatorEdgeLifecycle.envFlagTrue(LocalEmulatorDefaults.EMULATOR_COMPANIONS_ENV);
    }

    private static boolean stackPortAutostartEnabled() {
        return companionsEnabled()
            && EmulatorEdgeLifecycle.envFlagTrue(LocalEmulatorDefaults.STACKPORT_AUTOSTART_ENV);
    }
}
