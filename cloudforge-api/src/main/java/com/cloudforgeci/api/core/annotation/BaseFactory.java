package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.constructs.Construct;

/**
 * Base class for factory classes that provides convenient access to SystemContext,
 * DeploymentContext, and SecurityProfileConfiguration.
 *
 * <p>Subclasses can use annotations on fields to automatically extract specific context
 * values:</p>
 * <pre>{@code
 * @SystemContext("vpc")
 * private Vpc vpc;
 *
 * @DeploymentContext("region")
 * private String region;
 *
 * @SecurityProfileConfiguration("wafEnabled")
 * private boolean wafEnabled;
 * }</pre>
 *
 * <p>The annotations automatically extract and inject the specified values from the
 * context objects during construction.</p>
 */
public abstract class BaseFactory extends Construct {

    protected final SystemContext ctx;
    protected final DeploymentContext cfc;
    protected final SecurityProfileConfiguration config;

    /**
     * Constructor that initializes contexts and automatically extracts annotated field values.
     *
     * @param scope The parent construct
     * @param id The construct ID
     */
    public BaseFactory(Construct scope, String id) {
        super(scope, id);
        this.ctx = SystemContext.of(this);
        this.cfc = DeploymentContext.from(this);
        this.config = getSecurityProfileConfiguration(ctx.security);

        // Automatically inject annotated fields
        ContextInjector.inject(this, ctx, cfc);
    }

    /**
     * Get the appropriate security profile configuration based on the security profile.
     */
    private SecurityProfileConfiguration getSecurityProfileConfiguration(com.cloudforgeci.api.interfaces.SecurityProfile securityProfile) {
        return switch (securityProfile) {
            case DEV -> new com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration(cfc);
            case STAGING -> new com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration(cfc);
            case PRODUCTION -> new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration(cfc);
        };
    }

    /**
     * Convenience method to get SystemContext.
     *
     * @return The SystemContext
     */
    protected SystemContext getSystemContext() {
        return ctx;
    }

    /**
     * Convenience method to get DeploymentContext.
     *
     * @return The DeploymentContext
     */
    protected DeploymentContext getDeploymentContext() {
        return cfc;
    }

    /**
     * Convenience method to get SecurityProfileConfiguration.
     *
     * @return The SecurityProfileConfiguration
     */
    protected SecurityProfileConfiguration getSecurityProfileConfiguration() {
        return config;
    }

    /**
     * Abstract method that must be implemented by all factory subclasses.
     * This method should contain the actual infrastructure creation logic.
     */
    public abstract void create();
}
