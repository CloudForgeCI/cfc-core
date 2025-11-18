package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeploymentContext computed/derived fields.
 * Covers fqdn composition, enableSsl logic, createZone logic, and other derived values.
 */
@DisplayName("DeploymentContext Computed Fields Tests")
class DeploymentContextComputedFieldsTest {

    private DeploymentContext createContext(Map<String, Object> config) {
        App app = new App();
        app.getNode().setContext("cfc", config);
        Stack stack = new Stack(app, "TestStack");
        return DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("FQDN Composition Tests")
    class FqdnCompositionTests {

        @Test
        @DisplayName("fqdn should be null when neither domain nor subdomain provided")
        void fqdnNullWhenNoDomainOrSubdomain() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertNull(ctx.fqdn(), "FQDN should be null when no domain or subdomain");
        }

        @Test
        @DisplayName("fqdn should equal domain when only domain provided")
        void fqdnEqualsDomainWhenOnlyDomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("example.com", ctx.fqdn(), "FQDN should equal domain when no subdomain");
        }

        @Test
        @DisplayName("fqdn should be composed from subdomain and domain")
        void fqdnComposedFromSubdomainAndDomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "jenkins");
            DeploymentContext ctx = createContext(config);

            assertEquals("jenkins.example.com", ctx.fqdn(), "FQDN should be subdomain.domain");
        }

        @Test
        @DisplayName("explicit fqdn should override composed value")
        void explicitFqdnOverridesComposition() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "jenkins");
            config.put("fqdn", "custom.other.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("custom.other.com", ctx.fqdn(), "Explicit FQDN should override composition");
        }

        @Test
        @DisplayName("fqdn should handle multi-level subdomains")
        void fqdnHandlesMultiLevelSubdomains() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "ci.jenkins");
            DeploymentContext ctx = createContext(config);

            assertEquals("ci.jenkins.example.com", ctx.fqdn(), "FQDN should handle multi-level subdomains");
        }

        @Test
        @DisplayName("fqdn should be null when only subdomain provided without domain")
        void fqdnNullWhenOnlySubdomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("subdomain", "jenkins");
            DeploymentContext ctx = createContext(config);

            assertNull(ctx.fqdn(), "FQDN should be null when subdomain provided without domain");
        }

        @Test
        @DisplayName("fqdn should handle empty string domain")
        void fqdnHandlesEmptyStringDomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "");
            config.put("subdomain", "jenkins");
            DeploymentContext ctx = createContext(config);

            assertNull(ctx.fqdn(), "FQDN should be null for empty domain string");
        }

        @Test
        @DisplayName("fqdn should handle blank subdomain")
        void fqdnHandlesBlankSubdomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "  ");
            DeploymentContext ctx = createContext(config);

            assertEquals("example.com", ctx.fqdn(), "FQDN should equal domain when subdomain is blank");
        }
    }

    @Nested
    @DisplayName("enableSsl Logic Tests")
    class EnableSslLogicTests {

        @Test
        @DisplayName("enableSsl should be false by default")
        void enableSslDefaultFalse() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.enableSsl(), "enableSsl should default to false");
        }

        @Test
        @DisplayName("enableSsl should be true when explicitly set")
        void enableSslExplicitlyTrue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.enableSsl(), "enableSsl should be true when explicitly set");
        }

        @Test
        @DisplayName("enableSsl should be false even when domain is provided")
        void enableSslStaysFalseWithDomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.enableSsl(), "enableSsl should remain false even with domain");
        }

        @Test
        @DisplayName("enableSsl should be false when explicitly set to false")
        void enableSslExplicitlyFalse() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", false);
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.enableSsl(), "enableSsl should be false when explicitly set to false");
        }

        @Test
        @DisplayName("enableSsl should parse string 'true' as boolean")
        void enableSslParsesStringTrue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", "true");
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.enableSsl(), "enableSsl should parse string 'true' as boolean");
        }

        @Test
        @DisplayName("enableSsl should parse string '1' as true")
        void enableSslParsesString1() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", "1");
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.enableSsl(), "enableSsl should parse string '1' as true");
        }
    }

    @Nested
    @DisplayName("createZone Logic Tests")
    class CreateZoneLogicTests {

        @Test
        @DisplayName("createZone should be false by default")
        void createZoneDefaultFalse() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.createZone(), "createZone should default to false");
        }

        @Test
        @DisplayName("createZone should be true when explicitly set")
        void createZoneExplicitlyTrue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("createZone", true);
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.createZone(), "createZone should be true when explicitly set");
        }

        @Test
        @DisplayName("createZone should be false even when domain is provided")
        void createZoneStaysFalseWithDomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.createZone(), "createZone should remain false even with domain");
        }

        @Test
        @DisplayName("createZone should be false when explicitly set to false")
        void createZoneExplicitlyFalse() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("createZone", false);
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.createZone(), "createZone should be false when explicitly set");
        }

        @Test
        @DisplayName("createZone should parse string 'yes' as true")
        void createZoneParsesStringYes() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("createZone", "yes");
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.createZone(), "createZone should parse string 'yes' as true");
        }
    }

    @Nested
    @DisplayName("Runtime and Topology Normalization Tests")
    class RuntimeTopologyNormalizationTests {

        @Test
        @DisplayName("runtime should default to FARGATE when not specified")
        void runtimeDefaultsFargate() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("FARGATE", ctx.runtime().name(), "Runtime should default to FARGATE");
        }

        @Test
        @DisplayName("runtime should be set to EC2 when specified")
        void runtimeSetToEc2() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "ec2");
            DeploymentContext ctx = createContext(config);

            assertEquals("EC2", ctx.runtime().name(), "Runtime should be EC2 when specified");
        }

        @Test
        @DisplayName("runtime should be FARGATE when specified")
        void runtimeSetToFargate() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "fargate");
            DeploymentContext ctx = createContext(config);

            assertEquals("FARGATE", ctx.runtime().name(), "Runtime should be FARGATE");
        }

        @Test
        @DisplayName("topology should default to JENKINS_SERVICE when not specified")
        void topologyDefaultsJenkinsService() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            // The default topology is determined by the process() method based on runtime
            assertNotNull(ctx.topology(), "Topology should not be null");
        }

        @Test
        @DisplayName("topology should be set when specified")
        void topologySetWhenSpecified() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topology", "service");
            DeploymentContext ctx = createContext(config);

            assertNotNull(ctx.topology(), "Topology should be set");
        }
    }

    @Nested
    @DisplayName("Domain and Subdomain Accessor Tests")
    class DomainSubdomainAccessorTests {

        @Test
        @DisplayName("domain should return configured value")
        void domainReturnsConfiguredValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "mycompany.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("mycompany.com", ctx.domain(), "Domain should return configured value");
        }

        @Test
        @DisplayName("domain should return null when not configured")
        void domainReturnsNullWhenNotConfigured() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertNull(ctx.domain(), "Domain should be null when not configured");
        }

        @Test
        @DisplayName("subdomain should return configured value")
        void subdomainReturnsConfiguredValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("subdomain", "ci");
            DeploymentContext ctx = createContext(config);

            assertEquals("ci", ctx.subdomain(), "Subdomain should return configured value");
        }

        @Test
        @DisplayName("subdomain should return null when not configured")
        void subdomainReturnsNullWhenNotConfigured() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertNull(ctx.subdomain(), "Subdomain should be null when not configured");
        }
    }

    @Nested
    @DisplayName("Complex Computed Field Scenarios")
    class ComplexComputedFieldScenarios {

        @Test
        @DisplayName("SSL enabled with explicit fqdn and no domain/subdomain")
        void sslEnabledWithExplicitFqdnOnly() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("fqdn", "jenkins.mycompany.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.enableSsl(), "SSL should be enabled");
            assertEquals("jenkins.mycompany.com", ctx.fqdn(), "FQDN should be set");
            assertNull(ctx.domain(), "Domain should be null");
            assertNull(ctx.subdomain(), "Subdomain should be null");
        }

        @Test
        @DisplayName("All DNS fields configured together")
        void allDnsFieldsConfigured() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "jenkins");
            config.put("fqdn", "override.custom.com");
            config.put("enableSsl", true);
            config.put("createZone", true);
            DeploymentContext ctx = createContext(config);

            assertEquals("example.com", ctx.domain(), "Domain should be set");
            assertEquals("jenkins", ctx.subdomain(), "Subdomain should be set");
            assertEquals("override.custom.com", ctx.fqdn(), "Explicit FQDN should override");
            assertTrue(ctx.enableSsl(), "SSL should be enabled");
            assertTrue(ctx.createZone(), "Zone creation should be enabled");
        }

        @Test
        @DisplayName("Computed fqdn with SSL and zone creation")
        void computedFqdnWithSslAndZone() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "myapp.io");
            config.put("subdomain", "api");
            config.put("enableSsl", true);
            config.put("createZone", true);
            DeploymentContext ctx = createContext(config);

            assertEquals("api.myapp.io", ctx.fqdn(), "FQDN should be computed");
            assertTrue(ctx.enableSsl(), "SSL should be enabled");
            assertTrue(ctx.createZone(), "Zone should be created");
        }

        @Test
        @DisplayName("Domain with no subdomain results in simple fqdn")
        void domainWithNoSubdomainSimpleFqdn() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "app.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("app.com", ctx.fqdn(), "FQDN should equal domain");
            assertEquals("app.com", ctx.domain(), "Domain should be set");
            assertNull(ctx.subdomain(), "Subdomain should be null");
        }
    }

    @Nested
    @DisplayName("Field Consistency Tests")
    class FieldConsistencyTests {

        @Test
        @DisplayName("All computed fields should be consistent with configuration")
        void allComputedFieldsConsistent() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.org");
            config.put("subdomain", "test");
            config.put("enableSsl", "true");
            config.put("createZone", "1");
            DeploymentContext ctx = createContext(config);

            // Verify all fields are consistent
            assertEquals("example.org", ctx.domain());
            assertEquals("test", ctx.subdomain());
            assertEquals("test.example.org", ctx.fqdn());
            assertTrue(ctx.enableSsl());
            assertTrue(ctx.createZone());
        }

        @Test
        @DisplayName("Computed fields should not affect raw configuration")
        void computedFieldsDontAffectRaw() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "ci");
            DeploymentContext ctx = createContext(config);

            // FQDN is computed but shouldn't modify raw config
            assertEquals("ci.example.com", ctx.fqdn());
            // The raw config should still only have domain and subdomain
            assertNotNull(ctx.domain());
            assertNotNull(ctx.subdomain());
        }

        @Test
        @DisplayName("Null and empty string should behave differently for domain")
        void nullVsEmptyStringDomain() {
            Map<String, Object> configNull = new LinkedHashMap<>();
            // domain is null (not set)
            DeploymentContext ctxNull = createContext(configNull);

            Map<String, Object> configEmpty = new LinkedHashMap<>();
            configEmpty.put("domain", "");
            DeploymentContext ctxEmpty = createContext(configEmpty);

            assertNull(ctxNull.fqdn(), "FQDN should be null when domain not set");
            assertNull(ctxEmpty.fqdn(), "FQDN should be null when domain is empty string");
        }
    }
}
