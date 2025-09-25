package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.constructs.Construct;

import java.lang.reflect.Field;

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
        // Automatically inject contexts after construction
        performContextInjection();
    }
    
    /**
     * Performs the actual context injection using reflection.
     */
    private void performContextInjection() {
        try {
            SystemContext systemContext = SystemContext.of(this);
            DeploymentContext deploymentContext = DeploymentContext.from(this);
            
            // Use reflection to inject into annotated fields
            Class<?> clazz = this.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(com.cloudforgeci.api.core.annotation.SystemContext.class)) {
                        field.setAccessible(true);
                        field.set(this, systemContext);
                    } else if (field.isAnnotationPresent(com.cloudforgeci.api.core.annotation.DeploymentContext.class)) {
                        field.setAccessible(true);
                        field.set(this, deploymentContext);
                    } else if (field.isAnnotationPresent(com.cloudforgeci.api.core.annotation.SecurityProfileConfiguration.class)) {
                        field.setAccessible(true);
                        // Get the appropriate security profile configuration
                        SecurityProfileConfiguration config = getSecurityProfileConfiguration(systemContext.security);
                        field.set(this, config);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            // Context not available yet - will be injected later
        }
    }
    
    /**
     * Get the appropriate security profile configuration based on the security profile.
     */
    private SecurityProfileConfiguration getSecurityProfileConfiguration(com.cloudforgeci.api.interfaces.SecurityProfile securityProfile) {
        return switch (securityProfile) {
            case DEV -> new com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration();
            case STAGING -> new com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration();
            case PRODUCTION -> new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration();
        };
    }
    
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
        performContextInjection();
    }
    
    /**
     * Abstract method that must be implemented by all factory subclasses.
     * This method should contain the actual infrastructure creation logic.
     */
    public abstract void create();
}
