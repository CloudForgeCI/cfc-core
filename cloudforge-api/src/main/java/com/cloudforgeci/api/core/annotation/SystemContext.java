package com.cloudforgeci.api.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to extract specific values from SystemContext.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @SystemContext("vpc")
 * private Vpc vpc;
 *
 * @SystemContext("security")
 * private SecurityProfile security;
 *
 * @SystemContext("alb")
 * private ApplicationLoadBalancer alb;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemContext {
    /**
     * The property name to extract from SystemContext.
     * Valid values include: vpc, security, cfc, alb, nlb, targetGroup,
     * securityGroup, fileSystem, accessPoint, ec2InstanceRole, asg, etc.
     *
     * @return the property name to extract
     */
    String value() default "";
}
