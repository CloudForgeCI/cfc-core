package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.constructs.Construct;

/**
 * Base class for factory classes that use annotation-based context injection.
 * This eliminates the need to pass SystemContext, DeploymentContext, and SecurityProfileConfiguration as parameters.
 */
public abstract class BaseFactory extends Construct {
    
    @com.cloudforgeci.api.core.annotation.SystemContext
    protected com.cloudforgeci.api.core.SystemContext ctx;
    
    @com.cloudforgeci.api.core.annotation.DeploymentContext
    protected com.cloudforgeci.api.core.DeploymentContext cfc;
    
    @com.cloudforgeci.api.core.annotation.SecurityProfileConfiguration
    protected com.cloudforgeci.api.interfaces.SecurityProfileConfiguration config;
    
    /**
     * Constructor that automatically injects contexts.
     * 
     * @param scope The parent construct
     * @param id The construct ID
     */
    public BaseFactory(Construct scope, String id) {
        super(scope, id);
        
        System.out.println("BaseFactory: Constructor called for " + id);
        System.out.println("BaseFactory: Constructor completed successfully for " + id);
        
        // Automatically inject contexts if SystemContext is available
        try {
            // Look for SystemContext in the parent hierarchy, not as a child
            System.out.println("BaseFactory: About to call SystemContext.of() for " + id);
            SystemContext systemContext = SystemContext.of(this);
            System.out.println("BaseFactory: SystemContext.of() successful for " + id);
            DeploymentContext deploymentContext = DeploymentContext.from(this);
            System.out.println("BaseFactory: DeploymentContext.from() successful for " + id);
            ContextInjector.injectContexts(this, systemContext, deploymentContext);
            System.out.println("BaseFactory: Context injection successful for " + id);
        } catch (IllegalStateException e) {
            System.out.println("BaseFactory: Context injection failed for " + id + ": " + e.getMessage());
            // SystemContext not started yet - contexts will be injected later
            // This is normal for factories created before SystemContext.start()
        }
    }
    
    /**
     * Abstract method that subclasses must implement.
     * This method will be called after contexts are injected.
     */
    public abstract void create();
    
    /**
     * Convenience method to get SystemContext.
     * 
     * @return The injected SystemContext
     */
    protected SystemContext getSystemContext() {
        return ctx;
    }
    
    /**
     * Convenience method to get DeploymentContext.
     * 
     * @return The injected DeploymentContext
     */
    protected DeploymentContext getDeploymentContext() {
        return cfc;
    }
    
    /**
     * Convenience method to get SecurityProfileConfiguration.
     * 
     * @return The injected SecurityProfileConfiguration
     */
    protected SecurityProfileConfiguration getSecurityProfileConfiguration() {
        return config;
    }
    
    /**
     * Manually inject contexts after SystemContext has been started.
     * This is useful when the factory is created before SystemContext.start().
     */
    public void injectContexts() {
        SystemContext systemContext = SystemContext.of(this);
        DeploymentContext deploymentContext = DeploymentContext.from(this);
        ContextInjector.injectContexts(this, systemContext, deploymentContext);
    }
}
