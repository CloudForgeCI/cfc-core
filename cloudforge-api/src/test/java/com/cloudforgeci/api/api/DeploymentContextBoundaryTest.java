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
 * Tests for DeploymentContext boundary values and edge cases.
 * Covers numeric field boundaries (cpu, memory, capacity, port numbers),
 * string length limits, and special values.
 */
@DisplayName("DeploymentContext Boundary Tests")
class DeploymentContextBoundaryTest {

    private DeploymentContext createContext(Map<String, Object> config) {
        App app = new App();
        app.getNode().setContext("cfc", config);
        Stack stack = new Stack(app, "TestStack");
        return DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("CPU Boundary Tests")
    class CpuBoundaryTests {

        @Test
        @DisplayName("cpu should handle minimum valid value (256)")
        void cpuMinimumValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 256);
            DeploymentContext ctx = createContext(config);

            assertEquals(256, ctx.cpu(), "CPU should accept minimum value 256");
        }

        @Test
        @DisplayName("cpu should handle maximum typical value (16384)")
        void cpuMaximumValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 16384);
            DeploymentContext ctx = createContext(config);

            assertEquals(16384, ctx.cpu(), "CPU should accept maximum value 16384");
        }

        @Test
        @DisplayName("cpu should handle common values (512, 1024, 2048, 4096)")
        void cpuCommonValues() {
            for (int cpuValue : new int[]{512, 1024, 2048, 4096}) {
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("cpu", cpuValue);
                DeploymentContext ctx = createContext(config);

                assertEquals(cpuValue, ctx.cpu(), "CPU should accept common value " + cpuValue);
            }
        }

        @Test
        @DisplayName("cpu should handle string representation of numbers")
        void cpuStringRepresentation() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", "2048");
            DeploymentContext ctx = createContext(config);

            assertEquals(2048, ctx.cpu(), "CPU should parse string '2048' as integer");
        }

        @Test
        @DisplayName("cpu should default to 1024 when not specified")
        void cpuDefaultValue() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertEquals(1024, ctx.cpu(), "CPU should default to 1024");
        }

        @Test
        @DisplayName("cpu should handle zero value")
        void cpuZeroValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 0);
            DeploymentContext ctx = createContext(config);

            assertEquals(0, ctx.cpu(), "CPU should accept zero (validation happens elsewhere)");
        }

        @Test
        @DisplayName("cpu should handle negative value")
        void cpuNegativeValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", -1);
            DeploymentContext ctx = createContext(config);

            assertEquals(-1, ctx.cpu(), "CPU should accept negative (validation happens elsewhere)");
        }
    }

    @Nested
    @DisplayName("Memory Boundary Tests")
    class MemoryBoundaryTests {

        @Test
        @DisplayName("memory should handle minimum value (512)")
        void memoryMinimumValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("memory", 512);
            DeploymentContext ctx = createContext(config);

            assertEquals(512, ctx.memory(), "Memory should accept minimum value 512");
        }

        @Test
        @DisplayName("memory should handle maximum value (122880)")
        void memoryMaximumValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("memory", 122880);
            DeploymentContext ctx = createContext(config);

            assertEquals(122880, ctx.memory(), "Memory should accept maximum value 122880");
        }

        @Test
        @DisplayName("memory should handle common values (1024, 2048, 4096, 8192)")
        void memoryCommonValues() {
            for (int memValue : new int[]{1024, 2048, 4096, 8192}) {
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("memory", memValue);
                DeploymentContext ctx = createContext(config);

                assertEquals(memValue, ctx.memory(), "Memory should accept common value " + memValue);
            }
        }

        @Test
        @DisplayName("memory should default to 2048 when not specified")
        void memoryDefaultValue() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertEquals(2048, ctx.memory(), "Memory should default to 2048");
        }

        @Test
        @DisplayName("memory should handle string representation")
        void memoryStringRepresentation() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("memory", "4096");
            DeploymentContext ctx = createContext(config);

            assertEquals(4096, ctx.memory(), "Memory should parse string '4096' as integer");
        }
    }

    @Nested
    @DisplayName("Instance Capacity Boundary Tests")
    class InstanceCapacityBoundaryTests {

        @Test
        @DisplayName("minInstanceCapacity should handle minimum value (1)")
        void minInstanceCapacityMinimum() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 1);
            DeploymentContext ctx = createContext(config);

            assertEquals(1, ctx.minInstanceCapacity(), "Min capacity should be 1");
        }

        @Test
        @DisplayName("minInstanceCapacity should handle high value (100)")
        void minInstanceCapacityHigh() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 100);
            DeploymentContext ctx = createContext(config);

            assertEquals(100, ctx.minInstanceCapacity(), "Min capacity should accept 100");
        }

        @Test
        @DisplayName("minInstanceCapacity should default to 1 when not specified")
        void minInstanceCapacityDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertEquals(1, ctx.minInstanceCapacity(), "Min capacity should default to 1");
        }

        @Test
        @DisplayName("maxInstanceCapacity should handle minimum value (1)")
        void maxInstanceCapacityMinimum() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxInstanceCapacity", 1);
            DeploymentContext ctx = createContext(config);

            assertEquals(1, ctx.maxInstanceCapacity(), "Max capacity should be 1");
        }

        @Test
        @DisplayName("maxInstanceCapacity should handle high value (1000)")
        void maxInstanceCapacityHigh() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxInstanceCapacity", 1000);
            DeploymentContext ctx = createContext(config);

            assertEquals(1000, ctx.maxInstanceCapacity(), "Max capacity should accept 1000");
        }

        @Test
        @DisplayName("maxInstanceCapacity should default to 1 when not specified")
        void maxInstanceCapacityDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertEquals(1, ctx.maxInstanceCapacity(), "Max capacity should default to 1");
        }

        @Test
        @DisplayName("minInstanceCapacity can equal maxInstanceCapacity")
        void minEqualsMaxCapacity() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 5);
            config.put("maxInstanceCapacity", 5);
            DeploymentContext ctx = createContext(config);

            assertEquals(5, ctx.minInstanceCapacity());
            assertEquals(5, ctx.maxInstanceCapacity());
        }

        @Test
        @DisplayName("minInstanceCapacity greater than maxInstanceCapacity should be accepted")
        void minGreaterThanMax() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 10);
            config.put("maxInstanceCapacity", 5);
            DeploymentContext ctx = createContext(config);

            assertEquals(10, ctx.minInstanceCapacity());
            assertEquals(5, ctx.maxInstanceCapacity());
            // Note: Logical validation would happen elsewhere
        }
    }

    @Nested
    @DisplayName("CPU Target Utilization Boundary Tests")
    class CpuTargetUtilizationBoundaryTests {

        @Test
        @DisplayName("cpuTargetUtilization should handle minimum value (1)")
        void cpuTargetUtilizationMinimum() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", 1);
            DeploymentContext ctx = createContext(config);

            assertEquals(1, ctx.cpuTargetUtilization(), "CPU target utilization should be 1");
        }

        @Test
        @DisplayName("cpuTargetUtilization should handle maximum value (100)")
        void cpuTargetUtilizationMaximum() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", 100);
            DeploymentContext ctx = createContext(config);

            assertEquals(100, ctx.cpuTargetUtilization(), "CPU target utilization should be 100");
        }

        @Test
        @DisplayName("cpuTargetUtilization should handle typical value (70)")
        void cpuTargetUtilizationTypical() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", 70);
            DeploymentContext ctx = createContext(config);

            assertEquals(70, ctx.cpuTargetUtilization(), "CPU target utilization should be 70");
        }

        @Test
        @DisplayName("cpuTargetUtilization should default to 60 when not specified")
        void cpuTargetUtilizationDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertEquals(60, ctx.cpuTargetUtilization(), "CPU target utilization should default to 60");
        }

        @Test
        @DisplayName("cpuTargetUtilization should handle string representation")
        void cpuTargetUtilizationStringRepresentation() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", "80");
            DeploymentContext ctx = createContext(config);

            assertEquals(80, ctx.cpuTargetUtilization(), "Should parse string '80' as integer");
        }
    }

    @Nested
    @DisplayName("Log Retention Days Boundary Tests")
    class LogRetentionDaysBoundaryTests {

        @Test
        @DisplayName("logRetentionDays should handle minimum value (1)")
        void logRetentionDaysMinimum() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("logRetentionDays", 1);
            DeploymentContext ctx = createContext(config);

            assertEquals(1, ctx.logRetentionDays(), "Log retention should be 1 day");
        }

        @Test
        @DisplayName("logRetentionDays should handle typical values (7, 30, 90)")
        void logRetentionDaysTypical() {
            for (int days : new int[]{7, 30, 90}) {
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("logRetentionDays", days);
                DeploymentContext ctx = createContext(config);

                assertEquals(days, ctx.logRetentionDays(), "Log retention should be " + days + " days");
            }
        }

        @Test
        @DisplayName("logRetentionDays should handle long retention (365)")
        void logRetentionDaysLong() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("logRetentionDays", 365);
            DeploymentContext ctx = createContext(config);

            assertEquals(365, ctx.logRetentionDays(), "Log retention should be 365 days");
        }

        @Test
        @DisplayName("logRetentionDays should default to null when not specified (SecurityProfileConfiguration provides default)")
        void logRetentionDaysDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertNull(ctx.logRetentionDays(), "Log retention should default to null (SecurityProfileConfiguration provides actual default)");
        }
    }

    @Nested
    @DisplayName("String Length Boundary Tests")
    class StringLengthBoundaryTests {

        @Test
        @DisplayName("domain should handle single character")
        void domainSingleCharacter() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "a");
            DeploymentContext ctx = createContext(config);

            assertEquals("a", ctx.domain(), "Domain should accept single character");
        }

        @Test
        @DisplayName("domain should handle very long string (253 characters)")
        void domainVeryLong() {
            String longDomain = "a".repeat(250) + ".io";  // 253 chars total
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", longDomain);
            DeploymentContext ctx = createContext(config);

            assertEquals(longDomain, ctx.domain(), "Domain should accept long string");
        }

        @Test
        @DisplayName("subdomain should handle single character")
        void subdomainSingleCharacter() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("subdomain", "a");
            DeploymentContext ctx = createContext(config);

            assertEquals("a", ctx.subdomain(), "Subdomain should accept single character");
        }

        @Test
        @DisplayName("fqdn composition should handle very long combined value")
        void fqdnLongCombined() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("subdomain", "very-long-subdomain-name-here");
            config.put("domain", "very-long-domain-name-example.com");
            DeploymentContext ctx = createContext(config);

            String expected = "very-long-subdomain-name-here.very-long-domain-name-example.com";
            assertEquals(expected, ctx.fqdn(), "FQDN should compose long values");
        }

        @Test
        @DisplayName("tier should handle very short value")
        void tierShortValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "a");
            DeploymentContext ctx = createContext(config);

            assertEquals("a", ctx.tier(), "Tier should accept short value");
        }

        @Test
        @DisplayName("env should handle very short value")
        void envShortValue() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("env", "p");
            DeploymentContext ctx = createContext(config);

            assertEquals("p", ctx.env(), "Env should accept short value");
        }
    }

    @Nested
    @DisplayName("Special Character and Encoding Tests")
    class SpecialCharacterTests {

        @Test
        @DisplayName("domain should handle hyphens")
        void domainWithHyphens() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "my-app-domain.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("my-app-domain.com", ctx.domain(), "Domain should handle hyphens");
        }

        @Test
        @DisplayName("subdomain should handle hyphens")
        void subdomainWithHyphens() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("subdomain", "my-jenkins-ci");
            DeploymentContext ctx = createContext(config);

            assertEquals("my-jenkins-ci", ctx.subdomain(), "Subdomain should handle hyphens");
        }

        @Test
        @DisplayName("domain should handle numbers")
        void domainWithNumbers() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "app123.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("app123.com", ctx.domain(), "Domain should handle numbers");
        }

        @Test
        @DisplayName("tier should handle special characters")
        void tierWithSpecialCharacters() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "tier-123_dev");
            DeploymentContext ctx = createContext(config);

            assertEquals("tier-123_dev", ctx.tier(), "Tier should handle special characters");
        }

        @Test
        @DisplayName("authMode should handle kebab-case")
        void authModeKebabCase() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "jenkins-oidc");
            DeploymentContext ctx = createContext(config);

            assertEquals("jenkins-oidc", ctx.authMode(), "Auth mode should handle kebab-case");
        }
    }

    @Nested
    @DisplayName("Null and Empty Value Edge Cases")
    class NullEmptyEdgeCases {

        @Test
        @DisplayName("null integer should use default value")
        void nullIntegerUsesDefault() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", null);
            DeploymentContext ctx = createContext(config);

            assertEquals(1024, ctx.cpu(), "Null CPU should use default 1024");
        }

        @Test
        @DisplayName("null boolean should use default value")
        void nullBooleanUsesDefault() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", null);
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.enableSsl(), "Null enableSsl should default to false");
        }

        @Test
        @DisplayName("empty string should be treated as empty not null")
        void emptyStringTreatedAsEmpty() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "");
            DeploymentContext ctx = createContext(config);

            assertEquals("", ctx.tier(), "Empty string should remain empty string");
        }

        @Test
        @DisplayName("whitespace-only string should be preserved")
        void whitespaceOnlyStringPreserved() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("env", "   ");
            DeploymentContext ctx = createContext(config);

            assertEquals("   ", ctx.env(), "Whitespace-only string should be preserved");
        }
    }

    @Nested
    @DisplayName("Numeric Type Boundary Tests")
    class NumericTypeBoundaryTests {

        @Test
        @DisplayName("cpu should handle Integer.MAX_VALUE")
        void cpuMaxInteger() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", Integer.MAX_VALUE);
            DeploymentContext ctx = createContext(config);

            assertEquals(Integer.MAX_VALUE, ctx.cpu(), "CPU should accept Integer.MAX_VALUE");
        }

        @Test
        @DisplayName("memory should handle Integer.MAX_VALUE")
        void memoryMaxInteger() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("memory", Integer.MAX_VALUE);
            DeploymentContext ctx = createContext(config);

            assertEquals(Integer.MAX_VALUE, ctx.memory(), "Memory should accept Integer.MAX_VALUE");
        }

        @Test
        @DisplayName("minInstanceCapacity should handle zero")
        void minInstanceCapacityZero() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 0);
            DeploymentContext ctx = createContext(config);

            assertEquals(0, ctx.minInstanceCapacity(), "Min instance capacity should accept 0");
        }

        @Test
        @DisplayName("maxInstanceCapacity should handle very large value")
        void maxInstanceCapacityVeryLarge() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxInstanceCapacity", 10000);
            DeploymentContext ctx = createContext(config);

            assertEquals(10000, ctx.maxInstanceCapacity(), "Max instance capacity should accept 10000");
        }
    }

    @Nested
    @DisplayName("Combined Boundary Scenarios")
    class CombinedBoundaryScenarios {

        @Test
        @DisplayName("High resource configuration")
        void highResourceConfiguration() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 16384);
            config.put("memory", 122880);
            config.put("minInstanceCapacity", 10);
            config.put("maxInstanceCapacity", 100);
            DeploymentContext ctx = createContext(config);

            assertEquals(16384, ctx.cpu());
            assertEquals(122880, ctx.memory());
            assertEquals(10, ctx.minInstanceCapacity());
            assertEquals(100, ctx.maxInstanceCapacity());
        }

        @Test
        @DisplayName("Minimal resource configuration")
        void minimalResourceConfiguration() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 256);
            config.put("memory", 512);
            config.put("minInstanceCapacity", 1);
            config.put("maxInstanceCapacity", 1);
            DeploymentContext ctx = createContext(config);

            assertEquals(256, ctx.cpu());
            assertEquals(512, ctx.memory());
            assertEquals(1, ctx.minInstanceCapacity());
            assertEquals(1, ctx.maxInstanceCapacity());
        }

        @Test
        @DisplayName("All default values configuration")
        void allDefaultValuesConfiguration() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());

            assertEquals(1024, ctx.cpu());
            assertEquals(2048, ctx.memory());
            assertEquals(1, ctx.minInstanceCapacity());
            assertEquals(1, ctx.maxInstanceCapacity());
            assertEquals(60, ctx.cpuTargetUtilization());
            assertNull(ctx.logRetentionDays()); // null = SecurityProfileConfiguration provides default
        }

        @Test
        @DisplayName("Long domain with SSL and subdomain")
        void longDomainWithSslAndSubdomain() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "very-long-domain-name-that-represents-real-world-usage.example.com");
            config.put("subdomain", "super-long-subdomain-for-jenkins-ci-cd");
            config.put("enableSsl", true);
            DeploymentContext ctx = createContext(config);

            assertEquals("very-long-domain-name-that-represents-real-world-usage.example.com",
                    ctx.domain());
            assertEquals("super-long-subdomain-for-jenkins-ci-cd", ctx.subdomain());
            assertEquals("super-long-subdomain-for-jenkins-ci-cd.very-long-domain-name-that-represents-real-world-usage.example.com",
                    ctx.fqdn());
            assertTrue(ctx.enableSsl());
        }
    }
}
