package com.cloudforge.core.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.local.DeploymentTarget;

import java.nio.file.Path;

/**
 * Optional, application-owned behavior around a CloudForge deployment.
 *
 * <p>Implementations are discovered with {@link java.util.ServiceLoader}. They keep
 * application packaging and verification outside the generic CDK engine and its
 * consumer applications.</p>
 */
public interface ApplicationDeploymentExtension {

    /** Application ID this extension owns. */
    String applicationId();

    /** Whether this extension applies to the requested target. */
    // codeql[java/unused-parameter] -- default is target-agnostic; overriders use the parameter.
    default boolean supports(DeploymentTarget target) {
        return true;
    }

    /** Invoked before the generic deployer synthesizes or deploys the application. */
    // codeql[java/unused-parameter] -- no-op default; overriders use these parameters.
    default void beforeDeploy(DeploymentConfig config, DeploymentTarget target, Path workingDirectory)
            throws Exception {
        // Optional.
    }

    /** Invoked after a successful local deployment. */
    // codeql[java/unused-parameter] -- no-op default; overriders use these parameters.
    default void afterDeploy(DeploymentConfig config, DeploymentTarget target, Path workingDirectory)
            throws Exception {
        // Optional.
    }
}
