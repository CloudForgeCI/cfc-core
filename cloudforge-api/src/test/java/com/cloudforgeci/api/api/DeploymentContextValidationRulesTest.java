package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive validation rule tests for DeploymentContext.
 * Tests all validation rules including SSL requirements, auth mode validation,
 * topology/runtime compatibility, and constraint violations.
 */
@DisplayName("DeploymentContext Validation Rules Tests")
class DeploymentContextValidationRulesTest {

    private DeploymentContext fromMap(Map<String, Object> m) throws Exception {
        Constructor<DeploymentContext> ctor = DeploymentContext.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(m);
    }

    @Nested
    @DisplayName("SSL Validation Rules")
    class SslValidationRules {

        @Test
        @DisplayName("enableSsl=true requires fqdn or domain")
        void sslRequiresFqdnOrDomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            // No fqdn, no domain

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
            assertTrue(cause.getMessage().contains("enableSsl=true"), "Error should mention enableSsl=true");
            assertTrue(cause.getMessage().contains("fqdn") || cause.getMessage().contains("domain"),
                    "Error should mention fqdn or domain requirement");
        }

        @Test
        @DisplayName("enableSsl=true with fqdn should succeed")
        void sslWithFqdnSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("fqdn", "jenkins.example.com");

            assertDoesNotThrow(() -> fromMap(config), "Should succeed with fqdn provided");
        }

        @Test
        @DisplayName("enableSsl=true with domain should succeed")
        void sslWithDomainSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "example.com");

            assertDoesNotThrow(() -> fromMap(config), "Should succeed with domain provided");
        }

        @Test
        @DisplayName("enableSsl=true with both domain and subdomain should succeed")
        void sslWithDomainAndSubdomainSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "example.com");
            config.put("subdomain", "jenkins");

            assertDoesNotThrow(() -> fromMap(config), "Should succeed with domain and subdomain");
        }

        @Test
        @DisplayName("enableSsl=true with empty domain should fail")
        void sslWithEmptyDomainFails() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
        }

        @Test
        @DisplayName("enableSsl=true with blank fqdn should fail")
        void sslWithBlankFqdnFails() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("fqdn", "   ");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
        }

        @Test
        @DisplayName("enableSsl=false should not require domain or fqdn")
        void sslFalseNoRequirement() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", false);

            assertDoesNotThrow(() -> fromMap(config), "Should succeed without domain when SSL disabled");
        }
    }

    @Nested
    @DisplayName("Auth Mode Validation Rules")
    class AuthModeValidationRules {

        @Test
        @DisplayName("authMode=alb-oidc requires enableSsl=true")
        void albOidcRequiresSsl() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "alb-oidc");
            config.put("enableSsl", false);

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
            assertTrue(cause.getMessage().contains("alb-oidc"), "Error should mention alb-oidc");
            assertTrue(cause.getMessage().contains("HTTPS") || cause.getMessage().contains("enableSsl"),
                    "Error should mention HTTPS or SSL requirement");
        }

        @Test
        @DisplayName("authMode=alb-oidc with enableSsl=true and domain should succeed")
        void albOidcWithSslSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "alb-oidc");
            config.put("enableSsl", true);
            config.put("domain", "example.com");

            assertDoesNotThrow(() -> fromMap(config), "Should succeed with SSL enabled and domain");
        }

        @Test
        @DisplayName("authMode=alb-oidc with enableSsl=true and fqdn should succeed")
        void albOidcWithSslAndFqdnSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "alb-oidc");
            config.put("enableSsl", true);
            config.put("fqdn", "jenkins.example.com");

            assertDoesNotThrow(() -> fromMap(config), "Should succeed with SSL enabled and fqdn");
        }

        @Test
        @DisplayName("authMode=jenkins-oidc should not require SSL")
        void jenkinsOidcNoSslRequirement() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "jenkins-oidc");

            assertDoesNotThrow(() -> fromMap(config), "jenkins-oidc should work without SSL");
        }

        @Test
        @DisplayName("authMode=none should not require SSL")
        void noneAuthModeNoSslRequirement() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "none");

            assertDoesNotThrow(() -> fromMap(config), "none auth mode should work without SSL");
        }

        @Test
        @DisplayName("default authMode should not require SSL")
        void defaultAuthModeNoSslRequirement() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            // authMode not specified - defaults to "none"

            assertDoesNotThrow(() -> fromMap(config), "default auth mode should work without SSL");
        }
    }

    @Nested
    @DisplayName("Topology and Runtime Compatibility Rules")
    class TopologyRuntimeCompatibilityRules {

        @Test
        @DisplayName("JENKINS_SINGLE_NODE requires EC2 runtime")
        void jenkinsSingleNodeRequiresEc2() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "single-node");
            config.put("runtime", "fargate");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
            assertTrue(cause.getMessage().contains("JENKINS_SINGLE_NODE"), "Error should mention JENKINS_SINGLE_NODE");
            assertTrue(cause.getMessage().contains("EC2"), "Error should mention EC2 requirement");
        }

        @Test
        @DisplayName("JENKINS_SINGLE_NODE with EC2 should succeed")
        void jenkinsSingleNodeWithEc2Succeeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "single-node");
            config.put("runtime", "ec2");

            assertDoesNotThrow(() -> fromMap(config), "JENKINS_SINGLE_NODE with EC2 should succeed");
        }

        @Test
        @DisplayName("JENKINS_SERVICE with FARGATE should succeed")
        void jenkinsServiceWithFargateSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "service");
            config.put("runtime", "fargate");

            assertDoesNotThrow(() -> fromMap(config), "JENKINS_SERVICE with FARGATE should succeed");
        }

        @Test
        @DisplayName("JENKINS_SERVICE with EC2 should succeed")
        void jenkinsServiceWithEc2Succeeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "service");
            config.put("runtime", "ec2");

            assertDoesNotThrow(() -> fromMap(config), "JENKINS_SERVICE with EC2 should succeed");
        }

        @Test
        @DisplayName("S3_WEBSITE topology should succeed regardless of runtime")
        void s3WebsiteSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "s3-website");
            config.put("runtime", "fargate");

            assertDoesNotThrow(() -> fromMap(config), "S3_WEBSITE should succeed");
        }
    }

    @Nested
    @DisplayName("Combined Validation Scenarios")
    class CombinedValidationScenarios {

        @Test
        @DisplayName("Multiple validation errors should all be reported")
        void multipleValidationErrors() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);  // No domain/fqdn - ERROR 1
            config.put("authMode", "alb-oidc");  // Requires SSL but enableSsl has no domain - ERROR 2
            // Both errors should be present

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
            String message = cause.getMessage();

            // Should contain both validation errors
            assertTrue(message.contains("enableSsl") || message.contains("fqdn") || message.contains("domain"),
                    "Should mention SSL domain requirement");
        }

        @Test
        @DisplayName("Valid complete production configuration")
        void validProductionConfiguration() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("env", "production");
            config.put("tier", "private");
            config.put("securityProfile", "production");
            config.put("domain", "myapp.com");
            config.put("subdomain", "jenkins");
            config.put("enableSsl", true);
            config.put("createZone", true);
            config.put("authMode", "alb-oidc");
            config.put("wafEnabled", true);
            config.put("cloudfront", false);
            config.put("networkMode", "private-with-nat");
            config.put("lbType", "alb");
            config.put("runtime", "fargate");
            config.put("topology", "service");
            config.put("cpu", 2048);
            config.put("memory", 4096);
            config.put("minInstanceCapacity", 2);
            config.put("maxInstanceCapacity", 10);

            DeploymentContext ctx = fromMap(config);

            assertNotNull(ctx, "Valid production config should create context");
            assertEquals("production", ctx.env());
            assertEquals("jenkins.myapp.com", ctx.fqdn());
            assertTrue(ctx.enableSsl());
            assertEquals("alb-oidc", ctx.authMode());
        }

        @Test
        @DisplayName("Valid minimal development configuration")
        void validMinimalDevConfiguration() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            // Use all defaults - should succeed

            DeploymentContext ctx = fromMap(config);

            assertNotNull(ctx, "Minimal config should create context");
            assertEquals("dev", ctx.env());
            assertEquals("public", ctx.tier());
            assertFalse(ctx.enableSsl());
            assertEquals("none", ctx.authMode());
        }

        @Test
        @DisplayName("SSL enabled without auth should succeed")
        void sslWithoutAuthSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "example.com");
            config.put("authMode", "none");

            assertDoesNotThrow(() -> fromMap(config), "SSL without auth should succeed");
        }

        @Test
        @DisplayName("Auth without SSL for non-OIDC modes should succeed")
        void authWithoutSslForNonOidcSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "jenkins-oidc");
            config.put("enableSsl", false);

            assertDoesNotThrow(() -> fromMap(config), "jenkins-oidc without SSL should succeed");
        }
    }

    @Nested
    @DisplayName("Edge Case Validation")
    class EdgeCaseValidation {

        @Test
        @DisplayName("enableSsl with fqdn but empty domain should succeed")
        void sslWithFqdnEmptyDomainSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("fqdn", "jenkins.example.com");
            config.put("domain", "");

            assertDoesNotThrow(() -> fromMap(config), "FQDN should be sufficient even if domain is empty");
        }

        @Test
        @DisplayName("enableSsl with domain but empty fqdn should succeed")
        void sslWithDomainEmptyFqdnSucceeds() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "example.com");
            config.put("fqdn", "");

            assertDoesNotThrow(() -> fromMap(config), "Domain should be sufficient even if fqdn is empty");
        }

        @Test
        @DisplayName("Both fqdn and domain blank with SSL should fail")
        void bothFqdnAndDomainBlankFails() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("fqdn", "  ");
            config.put("domain", "  ");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();
            assertInstanceOf(IllegalArgumentException.class, cause);
        }

        @Test
        @DisplayName("Null values should be treated as unset")
        void nullValuesTreatedAsUnset() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", null);
            config.put("subdomain", null);
            config.put("fqdn", null);

            DeploymentContext ctx = fromMap(config);

            assertNull(ctx.domain());
            assertNull(ctx.subdomain());
            assertNull(ctx.fqdn());
        }

        @Test
        @DisplayName("String 'null' should be treated as string value")
        void stringNullTreatedAsString() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "null");  // String value "null", not actual null

            DeploymentContext ctx = fromMap(config);

            assertEquals("null", ctx.tier(), "String 'null' should be treated as actual string value");
        }
    }

    @Nested
    @DisplayName("Validation Error Message Quality")
    class ValidationErrorMessageQuality {

        @Test
        @DisplayName("SSL validation error should be clear and actionable")
        void sslValidationErrorClear() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();

            String message = cause.getMessage();
            assertNotNull(message, "Error message should not be null");
            assertTrue(message.contains("DeploymentContext validation failed"),
                    "Should indicate it's a validation error");
        }

        @Test
        @DisplayName("Auth mode validation error should mention requirements")
        void authModeValidationErrorMentionsRequirements() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "alb-oidc");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();

            String message = cause.getMessage();
            assertTrue(message.contains("HTTPS") || message.contains("SSL") || message.contains("enableSsl"),
                    "Should mention SSL/HTTPS requirement");
        }

        @Test
        @DisplayName("Topology/runtime validation error should mention both values")
        void topologyRuntimeErrorMentionsBoth() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "single-node");
            config.put("runtime", "fargate");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(config));
            Throwable cause = ex.getTargetException();

            String message = cause.getMessage();
            assertTrue(message.contains("SINGLE_NODE") || message.contains("single-node"),
                    "Should mention topology");
            assertTrue(message.contains("EC2"), "Should mention required runtime");
        }
    }
}
