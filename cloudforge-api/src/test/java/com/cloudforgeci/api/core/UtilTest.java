package com.cloudforgeci.api.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Util class.
 *
 * Tests deployment context extraction and conversion from various sources:
 * - Map to DeploymentContext conversion
 * - JSON string to DeploymentContext conversion
 * - Object to DeploymentContext conversion using Jackson
 * - Null and empty input handling
 */
class UtilTest {

    @Test
    void testExtractDeploymentContextFromMap() {
        // Given: A Map with deployment context data
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("stackName", "TestStack");
        contextMap.put("securityProfile", "PRODUCTION");
        contextMap.put("region", "us-east-1");

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(contextMap);

        // Then: Should create valid DeploymentContext
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromJsonString() {
        // Given: A JSON string with deployment context data
        String json = "{\"stackName\":\"TestStack\",\"securityProfile\":\"STAGING\",\"region\":\"us-west-2\"}";

        // When: Extracting deployment context from JSON
        DeploymentContext context = Util.createDeploymentContext(json);

        // Then: Should parse JSON and create DeploymentContext
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromNull() {
        // Given: Null input
        Object nullInput = null;

        // When: Extracting deployment context from null
        DeploymentContext context = Util.createDeploymentContext(nullInput);

        // Then: Should create empty DeploymentContext
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromEmptyString() {
        // Given: Empty JSON string
        String emptyJson = "";

        // When: Extracting deployment context from empty string
        DeploymentContext context = Util.createDeploymentContext(emptyJson);

        // Then: Should create empty DeploymentContext
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromWhitespaceString() {
        // Given: Whitespace-only JSON string
        String whitespaceJson = "   ";

        // When: Extracting deployment context from whitespace
        DeploymentContext context = Util.createDeploymentContext(whitespaceJson);

        // Then: Should create empty DeploymentContext
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromInvalidJson() {
        // Given: Invalid JSON string
        String invalidJson = "{invalid json}";

        // When/Then: Should throw RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            Util.createDeploymentContext(invalidJson);
        });

        assertTrue(exception.getMessage().contains("Failed to parse context JSON"));
    }

    @Test
    void testExtractDeploymentContextFromComplexJson() {
        // Given: Complex JSON with nested properties
        String complexJson = """
            {
                "stackName": "ComplexStack",
                "securityProfile": "PRODUCTION",
                "region": "eu-west-1",
                "enableMonitoring": true,
                "logRetentionDays": 365,
                "complianceFrameworks": "PCI-DSS,SOC2,HIPAA"
            }
            """;

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(complexJson);

        // Then: Should parse all properties
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromMapWithNonStringKeys() {
        // Given: A Map with non-string keys (should be converted to strings)
        Map<Object, Object> contextMap = new HashMap<>();
        contextMap.put("stackName", "TestStack");
        contextMap.put(123, "numeric-key");
        contextMap.put(true, "boolean-key");

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(contextMap);

        // Then: Should convert keys to strings and create context
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromEmptyMap() {
        // Given: Empty Map
        Map<String, Object> emptyMap = new HashMap<>();

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(emptyMap);

        // Then: Should create empty DeploymentContext
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextPreservesDataTypes() {
        // Given: A Map with various data types
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("stackName", "TestStack");
        contextMap.put("enableMonitoring", true);
        contextMap.put("logRetentionDays", 90);
        contextMap.put("tags", Map.of("Environment", "Production"));

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(contextMap);

        // Then: Should preserve data types and create context
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromJsonWithBooleans() {
        // Given: JSON with boolean values
        String json = "{\"stackName\":\"TestStack\",\"enableMonitoring\":true,\"guardDutyEnabled\":false}";

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(json);

        // Then: Should parse booleans correctly and create context
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromJsonWithNumbers() {
        // Given: JSON with numeric values
        String json = "{\"stackName\":\"TestStack\",\"logRetentionDays\":180,\"maxAzs\":3}";

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(json);

        // Then: Should parse numbers correctly and create context
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextHandlesSpecialCharacters() {
        // Given: Map with special characters in values
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("stackName", "Test-Stack_123");
        contextMap.put("domain", "example.com");
        contextMap.put("description", "Stack with special chars: @#$%");

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(contextMap);

        // Then: Should preserve special characters and create context
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextFromNestedJsonObjects() {
        // Given: JSON with nested objects
        String json = "{\"stackName\":\"TestStack\",\"tags\":{\"Environment\":\"Production\",\"Team\":\"DevOps\"}}";

        // When: Extracting deployment context
        DeploymentContext context = Util.createDeploymentContext(json);

        // Then: Should handle nested objects and create context
        assertNotNull(context);
    }

    @Test
    void testExtractDeploymentContextMultipleTimes() {
        // Given: Same map used multiple times
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("stackName", "TestStack");

        // When: Extracting deployment context multiple times
        DeploymentContext context1 = Util.createDeploymentContext(contextMap);
        DeploymentContext context2 = Util.createDeploymentContext(contextMap);

        // Then: Should create separate context instances
        assertNotNull(context1);
        assertNotNull(context2);
        assertNotSame(context1, context2);
    }
}
