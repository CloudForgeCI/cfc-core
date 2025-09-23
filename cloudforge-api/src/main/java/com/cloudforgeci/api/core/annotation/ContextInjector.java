package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.constructs.Construct;

import java.lang.reflect.Field;

/**
 * Utility class to inject SystemContext, DeploymentContext, and SecurityProfileConfiguration into factory classes using annotations.
 */
public class ContextInjector {
    
    private ContextInjector() {}
    
    /**
     * Injects contexts into the given object using reflection and annotations.
     * 
     * @param target The object to inject contexts into
     * @param systemContext The SystemContext to inject
     * @param deploymentContext The DeploymentContext to inject
     */
    public static void injectContexts(Object target, SystemContext systemContext, DeploymentContext deploymentContext) {
        Class<?> clazz = target.getClass();
        
        // Inject SystemContext
        injectSystemContext(target, clazz, systemContext);
        
        // Inject DeploymentContext
        injectDeploymentContext(target, clazz, deploymentContext);
        
        // Inject SecurityProfileConfiguration
        injectSecurityProfileConfiguration(target, clazz, systemContext);
    }
    
    /**
     * Injects SystemContext into fields annotated with @InjectSystemContext.
     */
    private static void injectSystemContext(Object target, Class<?> clazz, SystemContext systemContext) {
        // Check all fields in the class hierarchy
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            
            for (Field field : fields) {
                      if (field.isAnnotationPresent(com.cloudforgeci.api.core.annotation.SystemContext.class)) {
                    if (field.getType().equals(com.cloudforgeci.api.core.SystemContext.class)) {
                        try {
                            field.setAccessible(true);
                            field.set(target, systemContext);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Failed to inject SystemContext into field: " + field.getName(), e);
                        }
                    } else {
                        throw new IllegalArgumentException("Field " + field.getName() + " annotated with @InjectSystemContext must be of type SystemContext");
                    }
                }
            }
            
            currentClass = currentClass.getSuperclass();
        }
    }
    
    /**
     * Injects DeploymentContext into fields annotated with @InjectDeploymentContext.
     */
    private static void injectDeploymentContext(Object target, Class<?> clazz, DeploymentContext deploymentContext) {
        // Check all fields in the class hierarchy
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            
            for (Field field : fields) {
                      if (field.isAnnotationPresent(com.cloudforgeci.api.core.annotation.DeploymentContext.class)) {
                    if (field.getType().equals(com.cloudforgeci.api.core.DeploymentContext.class)) {
                        try {
                            field.setAccessible(true);
                            field.set(target, deploymentContext);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Failed to inject DeploymentContext into field: " + field.getName(), e);
                        }
                    } else {
                        throw new IllegalArgumentException("Field " + field.getName() + " annotated with @InjectDeploymentContext must be of type DeploymentContext");
                    }
                }
            }
            
            currentClass = currentClass.getSuperclass();
        }
    }
    
    /**
     * Injects SecurityProfileConfiguration into fields annotated with @SecurityProfileConfiguration.
     */
    private static void injectSecurityProfileConfiguration(Object target, Class<?> clazz, SystemContext systemContext) {
        // Check all fields in the class hierarchy
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            
            for (Field field : fields) {
                if (field.isAnnotationPresent(com.cloudforgeci.api.core.annotation.SecurityProfileConfiguration.class)) {
                    if (field.getType().equals(com.cloudforgeci.api.interfaces.SecurityProfileConfiguration.class)) {
                        try {
                            field.setAccessible(true);
                            // Get SecurityProfileConfiguration from SystemContext
                            if (systemContext.securityProfileConfig.get().isPresent()) {
                                SecurityProfileConfiguration config = systemContext.securityProfileConfig.get().orElseThrow();
                                field.set(target, config);
                            } else {
                                throw new IllegalStateException("SecurityProfileConfiguration not available in SystemContext");
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Failed to inject SecurityProfileConfiguration into field: " + field.getName(), e);
                        }
                    } else {
                        throw new IllegalArgumentException("Field " + field.getName() + " annotated with @SecurityProfileConfiguration must be of type SecurityProfileConfiguration");
                    }
                }
            }
            
            currentClass = currentClass.getSuperclass();
        }
    }
    
    /**
     * Convenience method to inject contexts from a Construct.
     * 
     * @param target The object to inject contexts into
     * @param construct The Construct to extract contexts from
     */
    public static void injectFromConstruct(Object target, Construct construct) {
        SystemContext systemContext = SystemContext.of(construct);
        DeploymentContext deploymentContext = DeploymentContext.from(construct);
        
        injectContexts(target, systemContext, deploymentContext);
    }
}
