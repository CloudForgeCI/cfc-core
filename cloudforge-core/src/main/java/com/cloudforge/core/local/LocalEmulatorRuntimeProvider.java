package com.cloudforge.core.local;

/**
 * ServiceLoader entry point for {@link LocalEmulatorRuntime} implementations in target modules.
 *
 * <p>Register in {@code META-INF/services/com.cloudforge.core.local.LocalEmulatorRuntimeProvider}.</p>
 */
public interface LocalEmulatorRuntimeProvider {

    LocalEmulatorRuntime runtime();
}
