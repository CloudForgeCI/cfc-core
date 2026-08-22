package com.cloudforge.core.local;

import java.io.IOException;

/**
 * Reconciles local processes required when canonical templates include auth the emulator
 * cannot execute at the load balancer (for example ALB OIDC/Cognito).
 */
@FunctionalInterface
public interface LocalAuthRuntime {

    void reconcile(boolean authenticationEnabled, String applicationUrl) throws IOException;
}
