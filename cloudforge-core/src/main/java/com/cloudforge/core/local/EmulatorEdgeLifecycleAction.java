package com.cloudforge.core.local;

/**
 * Emulator edge (nginx) lifecycle actions for Maven goals and {@link EmulatorLifecycle}.
 */
public enum EmulatorEdgeLifecycleAction {
    START,
    STOP,
    RESTART,
    REBUILD,
    STATUS,
    RECONCILE,
    RELOAD
}
