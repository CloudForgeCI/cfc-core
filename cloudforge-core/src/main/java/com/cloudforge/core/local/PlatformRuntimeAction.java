package com.cloudforge.core.local;

/** Lifecycle capability exposed by a local deployment platform. */
public enum PlatformRuntimeAction {
    START,
    STOP,
    RESTART,
    STATUS,
    RECONCILE_EDGE
}
