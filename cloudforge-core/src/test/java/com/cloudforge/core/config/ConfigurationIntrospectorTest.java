package com.cloudforge.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationIntrospectorTest {

    @Test
    void testDiscoverFieldsReturnsNonEmptyList() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null);
        assertFalse(fields.isEmpty(), "Should discover at least one @ConfigField annotated field");
    }

    @Test
    void testDiscoverFieldsWithCategory() {
        List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverFields(null, "database");

        // All returned fields should be in the database category
        for (ConfigFieldInfo field : databaseFields) {
            assertEquals("database", field.category(),
                "Field " + field.fieldName() + " should be in database category");
        }
    }

    @Test
    void testDiscoverFieldsWithNonexistentCategoryReturnsEmpty() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null, "nonexistent-category");
        assertTrue(fields.isEmpty(), "Should return empty list for nonexistent category");
    }

    @Test
    void testDiscoverFieldsWithNullCategoryReturnsAll() {
        List<ConfigFieldInfo> allFields = ConfigurationIntrospector.discoverFields(null);
        List<ConfigFieldInfo> fieldsWithNullCategory = ConfigurationIntrospector.discoverFields(null, null);

        assertEquals(allFields.size(), fieldsWithNullCategory.size(),
            "Passing null category should return all fields");
    }

    @Test
    void testDiscoverFieldsWithEmptyCategoryReturnsAll() {
        List<ConfigFieldInfo> allFields = ConfigurationIntrospector.discoverFields(null);
        List<ConfigFieldInfo> fieldsWithEmptyCategory = ConfigurationIntrospector.discoverFields(null, "");

        assertEquals(allFields.size(), fieldsWithEmptyCategory.size(),
            "Passing empty category should return all fields");
    }

    @Test
    void testFieldsSortedByOrder() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null, "database");

        if (fields.size() > 1) {
            for (int i = 0; i < fields.size() - 1; i++) {
                ConfigFieldInfo current = fields.get(i);
                ConfigFieldInfo next = fields.get(i + 1);

                // Fields should be sorted by order within same category
                assertTrue(current.order() <= next.order() ||
                           current.displayName().compareTo(next.displayName()) <= 0,
                    "Fields should be sorted by order, then by display name");
            }
        }
    }

    @Test
    void testDiscoveredFieldsHaveRequiredProperties() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null);

        for (ConfigFieldInfo field : fields) {
            // Every field should have a non-empty field name
            assertNotNull(field.fieldName(), "Field name should not be null");
            assertFalse(field.fieldName().isEmpty(), "Field name should not be empty");

            // Every field should have a display name
            assertNotNull(field.displayName(), "Display name should not be null for " + field.fieldName());
            assertFalse(field.displayName().isEmpty(),
                "Display name should not be empty for " + field.fieldName());

            // Every field should have a category
            assertNotNull(field.category(), "Category should not be null for " + field.fieldName());

            // Every field should have a type
            assertNotNull(field.type(), "Type should not be null for " + field.fieldName());
        }
    }

    @Test
    void testDatabaseFieldsExist() {
        List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverFields(null, "database");

        // Extract field names
        Set<String> fieldNames = databaseFields.stream()
            .map(ConfigFieldInfo::fieldName)
            .collect(Collectors.toSet());

        // Verify known database fields are discovered
        assertTrue(fieldNames.contains("provisionDatabase"),
            "Should discover provisionDatabase field");
        assertTrue(fieldNames.contains("databaseEngine"),
            "Should discover databaseEngine field");
        assertTrue(fieldNames.contains("databaseVersion"),
            "Should discover databaseVersion field");
    }

    @Test
    void testDiscoverFieldsReturnsSameResultsOnMultipleCalls() {
        List<ConfigFieldInfo> fields1 = ConfigurationIntrospector.discoverFields(null);
        List<ConfigFieldInfo> fields2 = ConfigurationIntrospector.discoverFields(null);

        assertEquals(fields1.size(), fields2.size(),
            "Multiple calls should return same number of fields");

        // Compare field names
        List<String> names1 = fields1.stream().map(ConfigFieldInfo::fieldName).toList();
        List<String> names2 = fields2.stream().map(ConfigFieldInfo::fieldName).toList();
        assertEquals(names1, names2, "Field names should match on multiple calls");
    }

    @Test
    void testDiscoverVisibleFieldsWithNullConfig() {
        // With null config, visibility evaluation should default to showing fields
        List<ConfigFieldInfo> visibleFields = ConfigurationIntrospector.discoverVisibleFields(null, null);

        // Should return some fields (visibility defaults to true when config is null)
        assertNotNull(visibleFields, "Should return non-null list");
    }

    @Test
    void testDiscoverVisibleFieldsWithConfigObject() {
        DeploymentConfig config = new DeploymentConfig();
        config.provisionDatabase = true;

        List<ConfigFieldInfo> visibleFields = ConfigurationIntrospector.discoverVisibleFields(null, config);
        assertNotNull(visibleFields, "Should return non-null list");
    }

    @Test
    void testCategoryOrder() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null);

        // Collect categories in order they appear
        List<String> categoriesInOrder = fields.stream()
            .map(ConfigFieldInfo::category)
            .distinct()
            .toList();

        // Define expected order
        List<String> expectedOrder = List.of("basic", "network", "security", "database", "resources", "monitoring");

        // For each expected category that appears, it should appear before later categories
        for (int i = 0; i < expectedOrder.size(); i++) {
            String earlier = expectedOrder.get(i);
            if (!categoriesInOrder.contains(earlier)) continue;

            for (int j = i + 1; j < expectedOrder.size(); j++) {
                String later = expectedOrder.get(j);
                if (!categoriesInOrder.contains(later)) continue;

                int earlierIndex = categoriesInOrder.indexOf(earlier);
                int laterIndex = categoriesInOrder.indexOf(later);

                assertTrue(earlierIndex < laterIndex,
                    "Category " + earlier + " should come before " + later);
            }
        }
    }

    @Test
    void testFieldWithAllowedValuesHasNonEmptyArray() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null, "database");

        // Find databaseEngine field which should have allowed values
        ConfigFieldInfo databaseEngineField = fields.stream()
            .filter(f -> f.fieldName().equals("databaseEngine"))
            .findFirst()
            .orElse(null);

        if (databaseEngineField != null) {
            assertNotNull(databaseEngineField.allowedValues());
            assertTrue(databaseEngineField.allowedValues().length > 0,
                "databaseEngine should have allowed values");
        }
    }

    @Test
    void testFieldWithTagsHasCorrectTags() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null, "database");

        // Find databaseInstanceClass field which should have DESTRUCTIVE and BILLING_IMPACT tags
        ConfigFieldInfo instanceClassField = fields.stream()
            .filter(f -> f.fieldName().equals("databaseInstanceClass"))
            .findFirst()
            .orElse(null);

        if (instanceClassField != null) {
            assertNotNull(instanceClassField.tags());
            assertTrue(instanceClassField.tags().length > 0,
                "databaseInstanceClass should have tags");
        }
    }

    @Test
    void testDiscoverVisibleFieldsByCategoryWithNullValues() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverVisibleFields(null, null, "database");
        assertNotNull(fields, "Should return non-null list even with null config");
    }

    @Test
    void testDiscoverVisibleFieldsByCategoryFiltersCorrectly() {
        DeploymentConfig config = new DeploymentConfig();
        List<ConfigFieldInfo> databaseFields =
            ConfigurationIntrospector.discoverVisibleFields(null, config, "database");

        for (ConfigFieldInfo field : databaseFields) {
            assertEquals("database", field.category(),
                "All returned fields should be in database category");
        }
    }

    @Test
    void testFieldsHaveFieldReference() {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(null);

        for (ConfigFieldInfo field : fields) {
            assertNotNull(field.field(), "Field reference should not be null for " + field.fieldName());
            assertEquals(field.fieldName(), field.field().getName(),
                "Field reference name should match fieldName");
        }
    }
}
