package com.cloudforge.core.local;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;

/**
 * Applies {@link EmulatorEdgeLifecycleAction} to the shared nginx emulator edge.
 *
 * <p>Preferred entry for Maven goals and programmatic callers. Emulator start/stop also
 * drives this via {@link EmulatorLifecycle} companion orchestration.</p>
 */
public final class EmulatorEdgeLifecycle {

    private EmulatorEdgeLifecycle() {
    }

    public static void execute(EmulatorEdgeLifecycleAction action) throws IOException {
        execute(new DefaultEmulatorEdgeRuntime(), action);
    }

    public static void execute(EmulatorEdgeRuntime runtime, EmulatorEdgeLifecycleAction action)
            throws IOException {
        switch (action) {
            case START -> runtime.start();
            case STOP -> runtime.stop();
            case RESTART -> runtime.restart();
            case REBUILD -> runtime.rebuild();
            case STATUS -> logStatus(runtime);
            case RECONCILE -> runtime.reconcile();
            case RELOAD -> runtime.reload();
        }
    }

    public static void executeUnchecked(EmulatorEdgeLifecycleAction action) {
        try {
            execute(action);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ensure edge is up and vhosts match current Docker publishes. */
    public static Map<String, Integer> ensureRunningAndReconcile() throws IOException {
        return new DefaultEmulatorEdgeRuntime().ensureRunningAndReconcile();
    }

    private static void logStatus(EmulatorEdgeRuntime runtime) throws IOException {
        System.out.println("Emulator edge container " + runtime.containerName()
            + " running=" + runtime.isRunning()
            + " healthy=" + runtime.isHealthy()
            + " url=" + runtime.browserUrl());
        if (runtime.isRunning()) {
            Map<String, Integer> routes = runtime.reconcile();
            if (routes.isEmpty()) {
                System.out.println("  (no vhosts yet — deploy an app, then reconcile)");
            }
        }
    }

    /** True unless {@code CFC_EMULATOR_COMPANIONS} or {@code CFC_EDGE_AUTOSTART} is false. */
    public static boolean autostartEnabled() {
        return envFlagTrue(LocalEmulatorDefaults.EMULATOR_COMPANIONS_ENV)
            && envFlagTrue(LocalEmulatorDefaults.EDGE_AUTOSTART_ENV);
    }

    static boolean envFlagTrue(String envName) {
        String raw = System.getenv(envName);
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return !(value.equals("0") || value.equals("false") || value.equals("no") || value.equals("off"));
    }
}
