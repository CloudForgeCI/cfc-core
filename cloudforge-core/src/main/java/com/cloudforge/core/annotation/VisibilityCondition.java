package com.cloudforge.core.annotation;

/**
 * Visibility condition expression language for configuration fields.
 *
 * <p>Defines a simple, well-specified DSL for field visibility instead of complex
 * nested annotations (which Java doesn't support well).</p>
 *
 * <h2>Grammar Specification</h2>
 *
 * <pre>
 * condition    := "always" | expression
 * expression   := term ( "||" term )*
 * term         := factor ( "&&" factor )*
 * factor       := "!" factor | "(" expression ")" | predicate
 * predicate    := capability | fieldCheck | appCheck
 *
 * capability   := "supportsDatabase" | "requiresDatabase" | "supportsOidc"
 * fieldCheck   := fieldName "==" value
 * appCheck     := "app==" appId
 *
 * fieldName    := JavaIdentifier
 * value        := "\"" string "\"" | "true" | "false" | number | EnumValue
 * appId        := "\"" string "\""
 * </pre>
 *
 * <h2>Operator Precedence</h2>
 * <ol>
 *   <li><b>!</b> (NOT) - Highest precedence</li>
 *   <li><b>&&</b> (AND)</li>
 *   <li><b>||</b> (OR) - Lowest precedence</li>
 *   <li><b>()</b> - Parentheses for grouping</li>
 * </ol>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Capability Check</h3>
 * <pre>{@code
 * visibleWhen = "supportsDatabase"
 * }</pre>
 *
 * <h3>Field Equality</h3>
 * <pre>{@code
 * visibleWhen = "provisionDatabase == true"
 * }</pre>
 *
 * <h3>Application-Specific</h3>
 * <pre>{@code
 * visibleWhen = "app == \"redis\""
 * }</pre>
 *
 * <h3>Compound Condition (AND)</h3>
 * <pre>{@code
 * visibleWhen = "provisionDatabase == true && supportsDatabase"
 * }</pre>
 *
 * <h3>Compound Condition (OR)</h3>
 * <pre>{@code
 * visibleWhen = "supportsDatabase || requiresBackup"
 * }</pre>
 *
 * <h3>Negation</h3>
 * <pre>{@code
 * visibleWhen = "!provisionDatabase"
 * }</pre>
 *
 * <h3>Complex Expression with Grouping</h3>
 * <pre>{@code
 * visibleWhen = "(supportsDatabase && provisionDatabase == true) || requiresDatabase"
 * }</pre>
 *
 * <h3>Production-Only Feature</h3>
 * <pre>{@code
 * visibleWhen = "securityProfile == PRODUCTION && provisionDatabase == true"
 * }</pre>
 *
 * <h2>Supported Capabilities</h2>
 * <ul>
 *   <li><b>supportsDatabase</b> - ApplicationSpec implements DatabaseSpec</li>
 *   <li><b>requiresDatabase</b> - Database is REQUIRED (not optional)</li>
 *   <li><b>supportsOidc</b> - ApplicationSpec.supportsOidcIntegration() == true</li>
 *   <li><b>supportsFargate</b> - ApplicationSpec supports Fargate runtime</li>
 *   <li><b>supportsEc2</b> - ApplicationSpec supports EC2 runtime</li>
 * </ul>
 *
 * <h2>Field References</h2>
 * <p>Any public field in DeploymentConfig can be referenced:</p>
 * <ul>
 *   <li><b>provisionDatabase</b> - boolean</li>
 *   <li><b>securityProfile</b> - SecurityProfile enum (DEV, STAGING, PRODUCTION)</li>
 *   <li><b>runtime</b> - RuntimeType enum (FARGATE, EC2)</li>
 *   <li><b>oidcProvider</b> - String ("cognito", "identity-center", etc.)</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>Invalid expressions throw {@code IllegalArgumentException} at initialization time
 * with detailed error messages including position and expected tokens.</p>
 *
 * @see com.cloudforge.core.annotation.ConfigField
 * @since 3.1.0
 */
public final class VisibilityCondition {

    private VisibilityCondition() {
        // Utility class - documentation only
    }

    /**
     * Example expressions for testing and documentation.
     */
    public static final class Examples {
        public static final String ALWAYS = "always";
        public static final String SUPPORTS_DATABASE = "supportsDatabase";
        public static final String REQUIRES_DATABASE = "requiresDatabase";
        public static final String DATABASE_AND_PROVISION = "supportsDatabase && provisionDatabase == true";
        public static final String PRODUCTION_ONLY = "securityProfile == PRODUCTION";
        public static final String REDIS_SPECIFIC = "app == \"redis\"";
        public static final String COMPLEX = "(supportsDatabase && provisionDatabase == true) || (app == \"metabase\" && embeddedMode == false)";

        private Examples() {}
    }
}
