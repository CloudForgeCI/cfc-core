package com.cloudforge.core.local;

/**
 * ServiceLoader entry point for {@link StackPortRuntime} implementations in target modules.
 */
public interface StackPortRuntimeProvider {

    StackPortRuntime runtime();
}
