package com.cloudforge.core.local;

import java.io.IOException;

/**
 * Target-owned platform lifecycle capability, discovered through {@link java.util.ServiceLoader}.
 * The contract contains no application, Maven, Docker, or test-entrypoint knowledge.
 */
public interface PlatformRuntimeProvider {

    DeploymentTarget target();

    void execute(PlatformRuntimeAction action) throws IOException;
}
