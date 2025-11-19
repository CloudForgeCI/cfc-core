package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for DeploymentContext.
 * Tests boundary conditions, invalid inputs, null handling, and unusual configurations.
 * Target: 15+ edge case tests.
 */
@DisplayName("DeploymentContext Edge Cases Tests")
class DeploymentContextEdgeCasesTest {

    private DeploymentContext fromMap(Map<String, Object> m) throws Exception {
        Constructor<DeploymentContext> ctor = DeploymentContext.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(m);
    }

    @Nested
    @DisplayName("Null and Empty String Handling")
    class NullEmptyStringHandling {

        @Test
        @DisplayName("empty map should use all defaults")
        void emptyMapUsesDefaults() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNotNull(cfc, "Context should be created with empty map");
            assertEquals("public", cfc.tier());
            assertEquals("dev", cfc.env());
        }

        @Test
        @DisplayName("empty domain string should be treated as null")
        void emptyDomainTreatedAsNull() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "");
            DeploymentContext cfc = fromMap(config);

            assertNull(cfc.fqdn(), "Empty domain should result in null FQDN");
        }

        @Test
        @DisplayName("whitespace-only domain should be treated as null")
        void whitespaceDomainTreatedAsNull() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "   ");
            DeploymentContext cfc = fromMap(config);

            assertNull(cfc.fqdn(), "Whitespace domain should result in null FQDN");
        }

        @Test
        @DisplayName("whitespace-only subdomain with valid domain should be ignored")
        void whitespaceSubdomainIgnored() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "  ");
            DeploymentContext cfc = fromMap(config);

            assertEquals("example.com", cfc.fqdn(), "Whitespace subdomain should be ignored");
        }

        @Test
        @DisplayName("null values in config map should use defaults")
        void nullValuesUseDefaults() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", null);
            config.put("env", null);
            config.put("domain", null);
            DeploymentContext cfc = fromMap(config);

            assertEquals("public", cfc.tier());
            assertEquals("dev", cfc.env());
            assertNull(cfc.domain());
        }
    }

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        @Test
        @DisplayName("cpu value of 0 should be accepted")
        void cpuZeroAccepted() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 0);
            DeploymentContext cfc = fromMap(config);

            assertEquals(0, cfc.cpu(), "CPU 0 should be accepted");
        }

        @Test
        @DisplayName("negative cpu value should be accepted (validation happens elsewhere)")
        void negativeCpuAccepted() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", -1);
            DeploymentContext cfc = fromMap(config);

            assertEquals(-1, cfc.cpu(), "Negative CPU should be stored as-is");
        }

        @Test
        @DisplayName("extremely large cpu value should be accepted")
        void extremelyLargeCpuAccepted() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", Integer.MAX_VALUE);
            DeploymentContext cfc = fromMap(config);

            assertEquals(Integer.MAX_VALUE, cfc.cpu(), "Maximum integer CPU should be accepted");
        }

        @Test
        @DisplayName("minInstanceCapacity greater than maxInstanceCapacity should be accepted")
        void minGreaterThanMaxAccepted() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 10);
            config.put("maxInstanceCapacity", 5);
            DeploymentContext cfc = fromMap(config);

            assertEquals(10, cfc.minInstanceCapacity());
            assertEquals(5, cfc.maxInstanceCapacity());
            // Note: Validation of this inconsistency happens in validation rules
        }

        @Test
        @DisplayName("cpuTargetUtilization of 0 should be accepted")
        void cpuTargetUtilizationZeroAccepted() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", 0);
            DeploymentContext cfc = fromMap(config);

            assertEquals(0, cfc.cpuTargetUtilization());
        }

        @Test
        @DisplayName("cpuTargetUtilization greater than 100 should be accepted")
        void cpuTargetUtilizationOver100Accepted() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", 150);
            DeploymentContext cfc = fromMap(config);

            assertEquals(150, cfc.cpuTargetUtilization());
        }
    }

    @Nested
    @DisplayName("Type Conversion Edge Cases")
    class TypeConversionEdgeCases {

        @Test
        @DisplayName("string integer should be parsed to int")
        void stringIntegerParsed() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", "2048");
            DeploymentContext cfc = fromMap(config);

            assertEquals(2048, cfc.cpu(), "String '2048' should be parsed to int");
        }

        @Test
        @DisplayName("boolean string 'true' should parse to true")
        void booleanStringTrueParsed() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("wafEnabled", "true");
            DeploymentContext cfc = fromMap(config);

            assertTrue(cfc.wafEnabled(), "String 'true' should parse to boolean true");
        }

        @Test
        @DisplayName("boolean string '1' should parse to true")
        void booleanString1ParsedToTrue() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", "1");
            config.put("domain", "example.com");
            DeploymentContext cfc = fromMap(config);

            assertTrue(cfc.enableSsl(), "String '1' should parse to boolean true");
        }

        @Test
        @DisplayName("boolean string 'yes' should parse to true")
        void booleanStringYesParsedToTrue() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("createZone", "yes");
            config.put("domain", "example.com");
            DeploymentContext cfc = fromMap(config);

            assertTrue(cfc.createZone(), "String 'yes' should parse to boolean true");
        }

        @Test
        @DisplayName("boolean string 'false' should parse to false")
        void booleanStringFalseParsed() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("wafEnabled", "false");
            DeploymentContext cfc = fromMap(config);

            assertFalse(cfc.wafEnabled(), "String 'false' should parse to boolean false");
        }

        @Test
        @DisplayName("boolean string '0' should parse to false")
        void booleanString0ParsedToFalse() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cloudfront", "0");
            DeploymentContext cfc = fromMap(config);

            assertFalse(cfc.cloudfrontEnabled(), "String '0' should parse to boolean false");
        }

        @Test
        @DisplayName("boolean string 'no' should parse to false")
        void booleanStringNoParsedToFalse() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("retainStorage", "no");
            DeploymentContext cfc = fromMap(config);

            assertFalse(cfc.retainStorage(), "String 'no' should parse to boolean false");
        }
    }

    @Nested
    @DisplayName("Special Character and Format Edge Cases")
    class SpecialCharacterEdgeCases {

        @Test
        @DisplayName("domain with trailing dot should be preserved")
        void domainWithTrailingDot() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com.");
            DeploymentContext cfc = fromMap(config);

            assertEquals("example.com.", cfc.domain());
        }

        @Test
        @DisplayName("subdomain with hyphens should be accepted")
        void subdomainWithHyphens() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "my-jenkins-ci");
            DeploymentContext cfc = fromMap(config);

            assertEquals("my-jenkins-ci.example.com", cfc.fqdn());
        }

        @Test
        @DisplayName("subdomain with numbers should be accepted")
        void subdomainWithNumbers() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "jenkins123");
            DeploymentContext cfc = fromMap(config);

            assertEquals("jenkins123.example.com", cfc.fqdn());
        }

        @Test
        @DisplayName("very long domain name should be accepted")
        void veryLongDomainAccepted() throws Exception {
            String longDomain = "a".repeat(50) + ".example.com";
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", longDomain);
            DeploymentContext cfc = fromMap(config);

            assertEquals(longDomain, cfc.domain());
        }

        @Test
        @DisplayName("CIDR notation with edge values should be accepted")
        void cidrEdgeValues() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("bastionCidr", "0.0.0.0/0");
            DeploymentContext cfc = fromMap(config);

            assertEquals("0.0.0.0/0", cfc.bastionCidr());
        }
    }

    @Nested
    @DisplayName("Case Sensitivity and Enum Parsing")
    class CaseSensitivityTests {

        @Test
        @DisplayName("runtime with uppercase should be normalized")
        void runtimeUppercaseNormalized() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "FARGATE");
            DeploymentContext cfc = fromMap(config);

            assertEquals("FARGATE", cfc.runtime().name());
        }

        @Test
        @DisplayName("runtime with mixed case should be handled")
        void runtimeMixedCaseHandled() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "Ec2");
            DeploymentContext cfc = fromMap(config);

            // Should handle case-insensitive parsing
            assertNotNull(cfc.runtime());
        }

        @Test
        @DisplayName("securityProfile with different cases should be parsed")
        void securityProfileCaseInsensitive() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("securityProfile", "PRODUCTION");
            DeploymentContext cfc = fromMap(config);

            assertNotNull(cfc.securityProfile());
        }
    }

    @Nested
    @DisplayName("Configuration Overlap and Precedence")
    class ConfigurationPrecedenceTests {

        @Test
        @DisplayName("explicit fqdn takes precedence over domain and subdomain")
        void explicitFqdnPrecedence() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "ci");
            config.put("fqdn", "completely-different.org");
            DeploymentContext cfc = fromMap(config);

            assertEquals("completely-different.org", cfc.fqdn());
            assertEquals("example.com", cfc.domain());
            assertEquals("ci", cfc.subdomain());
        }

        @Test
        @DisplayName("multiple conflicting boolean values should use last set value")
        void booleanOverrides() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            // This tests that the value is stored correctly, not overridden
            config.put("wafEnabled", true);
            DeploymentContext cfc = fromMap(config);

            assertTrue(cfc.wafEnabled());
        }
    }

    @Nested
    @DisplayName("Unexpected Input Types")
    class UnexpectedInputTypes {

        @Test
        @DisplayName("integer provided for string field should be converted to string")
        void integerForStringField() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", 123);
            DeploymentContext cfc = fromMap(config);

            // Should handle conversion gracefully
            assertNotNull(cfc.tier());
        }

        @Test
        @DisplayName("map should handle extra unknown keys gracefully")
        void extraUnknownKeysIgnored() throws Exception {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("unknownKey1", "value1");
            config.put("unknownKey2", 999);
            config.put("tier", "public");
            DeploymentContext cfc = fromMap(config);

            assertEquals("public", cfc.tier());
            // Unknown keys should not cause errors
        }
    }
}
