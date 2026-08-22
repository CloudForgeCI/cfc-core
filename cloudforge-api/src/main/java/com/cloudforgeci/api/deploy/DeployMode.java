package com.cloudforgeci.api.deploy;

/**
 * Local deployment operation mode for {@link CloudForgeDeployment}.
 */
public enum DeployMode {
    /** Adapt canonical template only (no stack create/update). */
    DRY_RUN,
    /** Adapt and deploy to the local emulator target. */
    DEPLOY,
    /** Confirm stack exists and read outputs (no template required). */
    VERIFY
}
