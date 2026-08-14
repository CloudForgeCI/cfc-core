package com.cloudforge.core.local;

import java.io.IOException;

/**
 * Applies {@link StackPortLifecycleAction} to a {@link StackPortRuntime}.
 */
public final class StackPortLifecycle {

    private StackPortLifecycle() {
    }

    public static void execute(DeploymentTarget target, StackPortLifecycleAction action)
            throws IOException {
        execute(StackPortRuntimes.forTarget(target), action);
    }

    public static void execute(StackPortRuntime runtime, StackPortLifecycleAction action)
            throws IOException {
        switch (action) {
            case START -> runtime.start();
            case STOP -> runtime.stop();
            case RESTART -> runtime.restart();
            case REBUILD -> runtime.rebuild();
            case STATUS -> logStatus(runtime);
        }
    }

    private static void logStatus(StackPortRuntime runtime) throws IOException {
        System.out.println(runtime.target() + " StackPort container " + runtime.containerName()
            + " running=" + runtime.isRunning()
            + " healthy=" + runtime.isHealthy()
            + " url=" + runtime.browserUrl());
    }
}
