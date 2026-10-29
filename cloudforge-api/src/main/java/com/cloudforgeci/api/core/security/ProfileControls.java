package com.cloudforgeci.api.core.security;

import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.rules.ComplianceMatrix;
import com.cloudforgeci.api.core.rules.ComplianceMatrix.SecurityControl;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Resolves the value of a security profile control. A framework that requires the control in
 * ENFORCE mode wins, then an explicit deployment-context value, then the profile default.
 */
final class ProfileControls {

    private final String profile;
    private final DeploymentContext context;
    private final Logger log;

    ProfileControls(String profile, DeploymentContext context, Logger log) {
        this.profile = profile;
        this.context = context;
        this.log = log;
    }

    /**
     * The compliance mode in effect: the deployment-context value, else ENFORCE when frameworks are
     * selected and DISABLED when none are.
     */
    ComplianceMode effectiveMode() {
        if (context == null) {
            return ComplianceMode.DISABLED;
        }
        ComplianceMode contextMode = context.complianceMode();
        if (contextMode != null) {
            return contextMode;
        }
        String frameworks = context.complianceFrameworks();
        return (frameworks != null && !frameworks.isEmpty()) ? ComplianceMode.ENFORCE : ComplianceMode.DISABLED;
    }

    /**
     * @param label the control's name in log messages
     * @param control the matrix control a framework can require
     * @param explicit reads the control's explicit deployment-context value, null when unset
     * @param profileDefault the value when neither a framework nor the deployment context decides
     */
    boolean resolve(String label, SecurityControl control, Function<DeploymentContext, Boolean> explicit,
                    boolean profileDefault) {
        if (context == null) {
            return profileDefault;
        }
        String frameworks = context.complianceFrameworks();
        if (ComplianceMatrix.isControlEnforced(frameworks, effectiveMode(), control)) {
            log.info(profile + " profile: " + label + " enforced by compliance frameworks: " + frameworks);
            return true;
        }
        Boolean value = explicit.apply(context);
        if (value != null) {
            log.info(profile + " profile: Overriding " + label + " from deployment context: " + value);
            return value;
        }
        return profileDefault;
    }
}
