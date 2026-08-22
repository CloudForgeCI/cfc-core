package com.cloudforge.core.local;

/**
 * StackPort lifecycle operations exposed by Maven goals and programmatic callers.
 */
public enum StackPortLifecycleAction {
    START,
    STOP,
    RESTART,
    STATUS,
    REBUILD
}
