package com.cloudforgeci.api.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject SecurityProfileConfiguration into factory classes.
 * Fields annotated with this will be automatically populated with the current SecurityProfileConfiguration.
 * This provides direct access to security profile settings without needing to call ctx.securityProfileConfig.get().orElseThrow().
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityProfileConfiguration {
}
