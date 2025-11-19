package com.cloudforgeci.api.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to extract specific values from DeploymentContext.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @DeploymentContext("region")
 * private String region;
 *
 * @DeploymentContext("env")
 * private String environment;
 *
 * @DeploymentContext("wafEnabled")
 * private boolean wafEnabled;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeploymentContext {
    /**
     * The property name to extract from DeploymentContext.
     * Valid values include: region, env, tier, domain, subdomain, fqdn,
     * networkMode, wafEnabled, cloudfront, lbType, authMode, instanceType,
     * enableMonitoring, enableEncryption, logRetentionDays, cpu, memory, etc.
     *
     * @return the property name to extract
     */
    String value() default "";
}
