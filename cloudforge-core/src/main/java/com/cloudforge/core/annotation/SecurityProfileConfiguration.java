package com.cloudforge.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to extract specific values from SecurityProfileConfiguration.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @SecurityProfileConfiguration("wafEnabled")
 * private boolean wafEnabled;
 *
 * @SecurityProfileConfiguration("encryptionEnabled")
 * private boolean encryptionEnabled;
 *
 * @SecurityProfileConfiguration("logRetentionDays")
 * private int logRetentionDays;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityProfileConfiguration {
    /**
     * The property name to extract from SecurityProfileConfiguration.
     * Valid values include: wafEnabled, encryptionEnabled, flowLogsEnabled,
     * accessLogsEnabled, sshEnabled, publicAccessEnabled, logRetentionDays,
     * backupEnabled, multiAzEnabled, vpcFlowLogsEnabled, etc.
     *
     * @return the property name to extract
     */
    String value() default "";
}
