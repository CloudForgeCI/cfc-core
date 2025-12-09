package com.cloudforge.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field equality check for visibility conditions.
 *
 * <p>Checks if a DeploymentConfig field has a specific value.</p>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Simple boolean check
 * @FieldEquals(field = "provisionDatabase", value = "true")
 *
 * // Enum check
 * @FieldEquals(field = "securityProfile", value = "PRODUCTION")
 *
 * // String check
 * @FieldEquals(field = "oidcProvider", value = "cognito")
 * }</pre>
 *
 * @see VisibilityCondition
 * @since 3.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({}) // Only used within @VisibilityCondition
public @interface FieldEquals {

    /**
     * DeploymentConfig field name to check.
     *
     * <p>Must match an actual field name in DeploymentConfig class.</p>
     *
     * @return field name
     */
    String field();

    /**
     * Expected value as string (converted to field type at runtime).
     *
     * <p>Type conversion:</p>
     * <ul>
     *   <li>boolean: "true" or "false"</li>
     *   <li>int/Integer: numeric string</li>
     *   <li>String: direct comparison</li>
     *   <li>Enum: enum constant name</li>
     * </ul>
     *
     * @return expected value as string
     */
    String value();
}
