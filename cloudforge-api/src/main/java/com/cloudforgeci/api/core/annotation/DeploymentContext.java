package com.cloudforgeci.api.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject DeploymentContext into factory classes.
 * Fields annotated with this will be automatically populated with the current DeploymentContext.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeploymentContext {
}
