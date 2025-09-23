package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import software.constructs.Construct;

/**
 * Base class for factory classes that use annotation-based context injection.
 * This eliminates the need to pass SystemContext and DeploymentContext as parameters.
 */
public abstract class AnnotatedFactory extends Construct {
    
    @com.cloudforgeci.api.core.annotation.SystemContext
    protected com.cloudforgeci.api.core.SystemContext ctx;
    
    @com.cloudforgeci.api.core.annotation.DeploymentContext
    protected com.cloudforgeci.api.core.DeploymentContext cfc;
    
    /**
     * Constructor that automatically injects contexts.
     * 
     * @param scope The parent construct
     * @param id The construct ID
     */
    public AnnotatedFactory(Construct scope, String id) {
        super(scope, id);
        
        // Automatically inject contexts if SystemContext is available
        try {
            ContextInjector.injectFromConstruct(this, this);
        } catch (IllegalStateException e) {
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
     * Manually inject contexts after SystemContext has been started.
     * This is useful when the factory is created before SystemContext.start().
     */
    public void injectContexts() {
        ContextInjector.injectFromConstruct(this, this);
    }
}
