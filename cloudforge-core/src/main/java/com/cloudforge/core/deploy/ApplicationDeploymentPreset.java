package com.cloudforge.core.deploy;

import com.cloudforge.core.config.DeploymentConfig;

/** Optional application-owned defaults applied before generic interactive prompts. */
public interface ApplicationDeploymentPreset {

    String applicationId();

    void applyDefaults(DeploymentConfig config);
}
