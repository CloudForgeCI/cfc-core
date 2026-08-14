package com.cloudforge.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field in DeploymentConfig as user-configurable with metadata for
 * automatic prompt generation, validation, and JSON schema generation.
 *
 * <p>This annotation enables the Configuration Introspection system to automatically
 * discover configuration fields, filter them by application capabilities, and generate
 * interactive prompts without manual code changes.</p>
 *
 * <h2>Integration with Plugin Systems</h2>
 *
 * <h3>ApplicationSpec Plugin Integration</h3>
 * <p>The {@link #visibleWhen()} condition references ApplicationSpec capabilities:</p>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "Database Engine",
 *     visibleWhen = "supportsDatabase",  // ← Checks ApplicationSpec.supportsDatabase()
 *     category = "database"
 * )
 * public String databaseEngine;
 * }</pre>
 *
 * <h3>FrameworkRules Plugin Integration</h3>
 * <p>FrameworkRules plugins provide compliance-driven defaults and validation:</p>
 * <pre>{@code
 * // Annotation defines minimum
 * @ConfigField(
 *     displayName = "Backup Retention Days",
 *     min = 7
 * )
 * public Integer databaseBackupRetentionDays;
 *
 * // FrameworkRules overrides for compliance
 * // PCI-DSS: 90 days
 * // HIPAA: 30 days
 * // SOC2: 14 days
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Field</h3>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "Stack Name",
 *     description = "CloudFormation stack name",
 *     category = "basic",
 *     required = true,
 *     pattern = "^[a-zA-Z][a-zA-Z0-9-]{0,127}$",
 *     example = "my-application"
 * )
 * public String stackName;
 * }</pre>
 *
 * <h3>Conditional Field (Application-Specific)</h3>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "OIDC Provider",
 *     description = "OIDC identity provider",
 *     category = "security",
 *     visibleWhen = "supportsOidc",  // Only for OIDC-enabled apps
 *     allowedValues = {"cognito", "identity-center", "external-idp"}
 * )
 * public String oidcProvider;
 * }</pre>
 *
 * <h3>Numeric Field with Constraints</h3>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "CPU Units",
 *     description = "Fargate CPU units",
 *     category = "resources",
 *     min = 256,
 *     max = 4096,
 *     example = "1024"
 * )
 * public int cpu;
 * }</pre>
 *
 * <h3>Sensitive Field</h3>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "OIDC Client Secret",
 *     description = "OIDC client secret (stored in Secrets Manager)",
 *     category = "security",
 *     visibleWhen = "oidcProvider == external-idp",
 *     sensitive = true
 * )
 * public String oidcClientSecret;
 * }</pre>
 *
 * <h2>Visibility Condition Language</h2>
 *
 * <p>The {@link #visibleWhen()} attribute supports simple expressions:</p>
 * <ul>
 *   <li>"always" - Field always visible (default)</li>
 *   <li>"supportsDatabase" - Checks ApplicationSpec.supportsDatabase()</li>
 *   <li>"requiresDatabase" - Checks DatabaseSpec.databaseRequirement().type() == REQUIRED</li>
 *   <li>"supportsOidc" - Checks ApplicationSpec.supportsOidcIntegration()</li>
 *   <li>"applicationId == redis" - Checks specific application</li>
 *   <li>"securityProfile == PRODUCTION" - Checks security profile</li>
 *   <li>"provisionDatabase" - Checks boolean field value</li>
 * </ul>
 *
 * <h2>Field Categories</h2>
 *
 * <p>Categories group related fields in interactive prompts:</p>
 * <ul>
 *   <li><b>basic</b> - Stack name, environment, application ID</li>
 *   <li><b>domain</b> - Domain, subdomain, SSL configuration</li>
 *   <li><b>resources</b> - CPU, memory, instance type, scaling</li>
 *   <li><b>database</b> - RDS configuration (engine, instance class, storage)</li>
 *   <li><b>network</b> - VPC, WAF, CloudFront</li>
 *   <li><b>security</b> - OIDC, encryption, compliance</li>
 *   <li><b>monitoring</b> - CloudWatch, GuardDuty, logging</li>
 * </ul>
 *
 * @see com.cloudforge.core.config.DeploymentConfig
 * @see com.cloudforge.core.interfaces.ApplicationSpec
 * @see com.cloudforge.core.interfaces.FrameworkRules
 * @since 3.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigField {

    /**
     * Display name shown to users in interactive prompts.
     *
     * <p>Should be human-readable and concise (e.g., "Database Engine", "Stack Name").</p>
     *
     * @return display name for this field
     */
    String displayName();

    /**
     * Detailed description shown to users explaining the purpose and behavior.
     *
     * <p>Should provide enough context for users to make informed decisions.</p>
     *
     * @return description of this field
     */
    String description();

    /**
     * Field category for grouping related configuration options.
     *
     * <p>Categories are used to organize prompts in the interactive deployer
     * and group related fields in generated documentation.</p>
     *
     * <p>Common categories:</p>
     * <ul>
     *   <li>basic - Essential configuration (stack name, app ID)</li>
     *   <li>domain - Domain and SSL configuration</li>
     *   <li>resources - CPU, memory, instance sizing</li>
     *   <li>database - RDS database configuration</li>
     *   <li>network - VPC, WAF, CloudFront</li>
     *   <li>security - OIDC, encryption, compliance</li>
     *   <li>monitoring - CloudWatch, GuardDuty, logging</li>
     * </ul>
     *
     * @return field category
     */
    String category() default "basic";

    /**
     * Visibility condition expression determining when this field should be shown.
     *
     * <p>Expressions reference ApplicationSpec capabilities and DeploymentConfig field values.
     * This enables application-aware configuration where fields are only shown when relevant.</p>
     *
     * <p>Supported expressions:</p>
     * <ul>
     *   <li>"always" - Always visible (default)</li>
     *   <li>"supportsDatabase" - Visible when ApplicationSpec supports database</li>
     *   <li>"requiresDatabase" - Visible when database is required</li>
     *   <li>"supportsOidc" - Visible when OIDC is supported</li>
     *   <li>"provisionDatabase" - Visible when provisionDatabase flag is true</li>
     *   <li>"applicationId == redis" - Visible only for specific applications</li>
     * </ul>
     *
     * <p>For complex conditions, use logical operators (future enhancement):</p>
     * <ul>
     *   <li>"supportsDatabase &amp;&amp; provisionDatabase"</li>
     *   <li>"securityProfile == PRODUCTION || complianceMode == PCI_DSS"</li>
     * </ul>
     *
     * @return visibility condition expression
     */
    String visibleWhen() default "always";

    /**
     * Whether this field is required to have a non-null value.
     *
     * <p>Required fields must be provided by the user or have a default value.
     * The configuration introspector will validate required fields before deployment.</p>
     *
     * @return true if field is required
     */
    boolean required() default false;

    /**
     * Example value shown to users as guidance.
     *
     * <p>Should demonstrate the expected format and a typical use case.</p>
     *
     * @return example value
     */
    String example() default "";

    /**
     * Allowed values for enum-like fields with constrained choices.
     *
     * <p>When specified, only these values are accepted. Interactive prompts
     * will present these as a selection menu.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * allowedValues = {"postgres", "mysql", "mariadb"}
     * }</pre>
     *
     * @return array of allowed values, or empty for unconstrained fields
     */
    String[] allowedValues() default {};

    /**
     * Minimum value for numeric fields (int, double, float).
     *
     * <p>Validation will reject values below this minimum.</p>
     *
     * @return minimum allowed value
     */
    double min() default Double.NEGATIVE_INFINITY;

    /**
     * Maximum value for numeric fields (int, double, float).
     *
     * <p>Validation will reject values above this maximum.</p>
     *
     * @return maximum allowed value
     */
    double max() default Double.POSITIVE_INFINITY;

    /**
     * Regular expression pattern for string validation.
     *
     * <p>String values must match this pattern to be considered valid.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * pattern = "^[a-zA-Z][a-zA-Z0-9-]{0,127}$"  // Stack name validation
     * }</pre>
     *
     * @return regex pattern, or empty string for no pattern validation
     */
    String pattern() default "";

    /**
     * Whether this field contains sensitive data (passwords, secrets, API keys).
     *
     * <p>Sensitive fields are:</p>
     * <ul>
     *   <li>Masked in interactive prompts</li>
     *   <li>Excluded from logs and error messages</li>
     *   <li>Marked as sensitive in JSON schemas</li>
     *   <li>Should be stored in AWS Secrets Manager, not deployment-context.json</li>
     * </ul>
     *
     * @return true if field contains sensitive data
     */
    boolean sensitive() default false;

    /**
     * Impact and characteristic tags for change analysis.
     *
     * <p>Tags drive warnings and approval workflows:</p>
     * <ul>
     *   <li>DESTRUCTIVE - Resource replacement (data loss risk)</li>
     *   <li>REQUIRES_RESTART - Service restart needed (~1-2 min downtime)</li>
     *   <li>BILLING_IMPACT - AWS cost impact</li>
     *   <li>IMMUTABLE - Cannot change after creation</li>
     *   <li>REQUIRES_APPROVAL - Manual approval for production</li>
     *   <li>EXPERIMENTAL - Not production-ready</li>
     * </ul>
     *
     * @return array of field tags
     */
    FieldTag[] tags() default {};

    /**
     * Order hint for field display within a category.
     *
     * <p>Fields with lower order values are shown first. Fields with the same
     * order are sorted alphabetically by display name.</p>
     *
     * <p>Default order is 1000, allowing insertion before and after.</p>
     *
     * @return display order hint
     */
    int order() default 1000;

    /**
     * Name of parent field that this field depends on.
     *
     * <p>Used to establish explicit dependencies between fields, making it clear
     * when one field's visibility or value depends on another.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * @ConfigField(
     *     displayName = "Database Version",
     *     dependsOn = "provisionDatabase",
     *     visibleWhen = "provisionDatabase == true"
     * )
     * public String databaseVersion;
     * }</pre>
     *
     * @return parent field name, or empty if no dependency
     */
    String dependsOn() default "";

    /**
     * Method name or expression for resolving default value from ApplicationSpec.
     *
     * <p>Supports convention-based lookup via reflection:</p>
     * <ul>
     *   <li>"defaultCpu" - Calls appSpec.defaultCpu()</li>
     *   <li>"databaseRequirement().engine" - Chained method calls</li>
     * </ul>
     *
     * <p>This enables layered defaults:</p>
     * <pre>
     * User Override (highest priority)
     *     ↓
     * FrameworkRules (compliance)
     *     ↓
     * ApplicationSpec (defaultFrom)
     *     ↓
     * ConfigField default (system)
     * </pre>
     *
     * @return method name or expression, or empty for no ApplicationSpec default
     */
    String defaultFrom() default "";

    /**
     * Optional key in {@code application.properties} / {@code application-local.properties}
     * (and matching env / JVM system properties) used to supply a default when the field
     * is unset in deployment-context.json.
     *
     * <p>Example: {@code propertyKey = "cfc.manager.url"} reads {@code cfc.manager.url}
     * from properties or {@code CFC_MANAGER_URL} from the environment.</p>
     *
     * <p>Empty (default) means no properties-file binding — existing behavior unchanged.</p>
     *
     * @return dotted property key, or empty for none
     * @since 3.3.0
     */
    String propertyKey() default "";

    /**
     * Configuration for sensitive field source strategy.
     *
     * <p>References another field containing the source location (e.g., Secrets Manager ARN).</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * @ConfigField(
     *     displayName = "Database Password ARN",
     *     sensitive = false
     * )
     * public String databasePasswordArn;
     *
     * @ConfigField(
     *     displayName = "Database Password",
     *     sensitive = true,
     *     sourceConfig = "databasePasswordArn"
     * )
     * public String databasePassword;  // Value comes from Secrets Manager
     * }</pre>
     *
     * @return field name containing source configuration
     */
    String sourceConfig() default "";

    /**
     * Custom validators for cross-field validation.
     *
     * <p>Validators are executed after basic field-level validation (required, min, max, etc.)
     * and enable complex validation logic that depends on multiple fields or external state.</p>
     *
     * <p>Validators are specified by their simple class name and must implement the
     * {@link com.cloudforge.core.config.FieldValidator} interface.</p>
     *
     * <p>Built-in validators:</p>
     * <ul>
     *   <li><b>CapacityValidator</b> - Validates maxCapacity >= minCapacity</li>
     *   <li><b>FargateCpuMemoryValidator</b> - Validates AWS Fargate CPU/memory combinations</li>
     * </ul>
     *
     * <p>Example:</p>
     * <pre>{@code
     * @ConfigField(
     *     displayName = "Maximum Capacity",
     *     validators = {"CapacityValidator"}
     * )
     * public int maxCapacity = 10;
     *
     * @ConfigField(
     *     displayName = "Fargate Memory (MB)",
     *     validators = {"FargateCpuMemoryValidator"}
     * )
     * public int fargateMemory = 2048;
     * }</pre>
     *
     * @return array of validator class names
     */
    String[] validators() default {};
}
