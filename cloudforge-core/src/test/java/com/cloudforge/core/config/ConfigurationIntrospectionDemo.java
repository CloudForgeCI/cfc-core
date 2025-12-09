package com.cloudforge.core.config;

import com.cloudforge.core.annotation.FieldTag;
import com.cloudforge.core.interfaces.ApplicationSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the Configuration Introspection system in action.
 *
 * <p>This test shows how fields are automatically discovered from DeploymentConfig
 * using @ConfigField annotations, filtered by category, and sorted for display.</p>
 */
public class ConfigurationIntrospectionDemo {

    @Test
    public void demonstrateFieldDiscovery() {
        System.out.println("\n=== Configuration Introspection Demo ===\n");

        // Create a mock application spec (null for basic demo)
        ApplicationSpec appSpec = null;

        // Discover all database fields
        List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverFields(appSpec, "database");

        System.out.println("📦 Discovered " + databaseFields.size() + " database configuration fields:");
        System.out.println();

        for (ConfigFieldInfo field : databaseFields) {
            System.out.println("Field: " + field.displayName());
            System.out.println("  Internal name: " + field.fieldName());
            System.out.println("  Description: " + field.description());
            System.out.println("  Category: " + field.category());
            System.out.println("  Visible when: " + field.visibleWhen());

            if (!field.dependsOn().isEmpty()) {
                System.out.println("  Depends on: " + field.dependsOn());
            }

            if (field.allowedValues().length > 0) {
                System.out.println("  Allowed values: " + String.join(", ", field.allowedValues()));
            }

            if (!field.defaultFrom().isEmpty()) {
                System.out.println("  Default from: ApplicationSpec." + field.defaultFrom() + "()");
            }

            if (!field.example().isEmpty()) {
                System.out.println("  Example: " + field.example());
            }

            if (field.tags().length > 0) {
                System.out.print("  Tags: ");
                for (FieldTag tag : field.tags()) {
                    System.out.print(tag + " ");
                }
                System.out.println();
            }

            System.out.println("  Type: " + field.type().getSimpleName());
            System.out.println("  Order: " + field.order());
            System.out.println();
        }

        // Verify we discovered the expected fields
        assertTrue(databaseFields.size() >= 8, "Should discover at least 8 database fields");

        // Verify fields are sorted by order
        for (int i = 1; i < databaseFields.size(); i++) {
            int prevOrder = databaseFields.get(i - 1).order();
            int currOrder = databaseFields.get(i).order();
            assertTrue(prevOrder <= currOrder,
                "Fields should be sorted by order: " + databaseFields.get(i - 1).fieldName() +
                " (order=" + prevOrder + ") should come before " +
                databaseFields.get(i).fieldName() + " (order=" + currOrder + ")");
        }

        // Test specific field metadata
        ConfigFieldInfo provisionField = databaseFields.stream()
            .filter(f -> f.fieldName().equals("provisionDatabase"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("provisionDatabase field not found"));

        assertEquals("Provision RDS Database", provisionField.displayName());
        assertEquals("database", provisionField.category());
        assertEquals("supportsDatabase", provisionField.visibleWhen());
        assertEquals(10, provisionField.order());

        // Test field with tags
        ConfigFieldInfo instanceClassField = databaseFields.stream()
            .filter(f -> f.fieldName().equals("databaseInstanceClass"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("databaseInstanceClass field not found"));

        assertTrue(instanceClassField.hasTag(FieldTag.DESTRUCTIVE),
            "databaseInstanceClass should have DESTRUCTIVE tag");
        assertTrue(instanceClassField.hasTag(FieldTag.BILLING_IMPACT),
            "databaseInstanceClass should have BILLING_IMPACT tag");
        assertEquals("databaseRequirement().instanceClass", instanceClassField.defaultFrom());

        System.out.println("✅ All assertions passed!");
        System.out.println();
        System.out.println("=== Key Capabilities Demonstrated ===");
        System.out.println("✓ Automatic field discovery via reflection");
        System.out.println("✓ Category-based filtering");
        System.out.println("✓ Metadata extraction from @ConfigField annotations");
        System.out.println("✓ Field ordering for consistent display");
        System.out.println("✓ Tag-based change impact analysis");
        System.out.println("✓ Convention-based default value resolution");
        System.out.println("✓ Dependency tracking (dependsOn attribute)");
        System.out.println("✓ Visibility conditions (application-aware config)");
        System.out.println();
    }

    @Test
    public void demonstrateAllCategories() {
        System.out.println("\n=== All Configuration Categories ===\n");

        ApplicationSpec appSpec = null;

        String[] categories = {"basic", "network", "security", "database", "resources", "monitoring"};

        for (String category : categories) {
            List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverFields(appSpec, category);
            System.out.println("📁 " + category + ": " + fields.size() + " fields discovered");
        }

        System.out.println();
    }

    @Test
    public void demonstrateFieldValues() {
        System.out.println("\n=== Field Value Access Demo ===\n");

        // Create a config with some values
        DeploymentConfig config = new DeploymentConfig();
        config.provisionDatabase = true;
        config.databaseEngine = "postgres";
        config.databaseVersion = "15";
        config.databaseInstanceClass = "db.m5.large";
        config.databaseAllocatedStorageGB = 500;

        List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverFields(null, "database");

        System.out.println("Reading values from DeploymentConfig:");
        System.out.println();

        for (ConfigFieldInfo field : databaseFields) {
            Object value = field.getValue(config);
            if (value != null) {
                System.out.println(field.displayName() + ": " + value);
            }
        }

        System.out.println();

        // Demonstrate setting values
        ConfigFieldInfo multiAzField = databaseFields.stream()
            .filter(f -> f.fieldName().equals("databaseMultiAz"))
            .findFirst()
            .orElseThrow();

        System.out.println("Changing databaseMultiAz from " + multiAzField.getValue(config) + " to true");
        multiAzField.setValue(config, true);
        System.out.println("New value: " + config.databaseMultiAz);

        System.out.println();
    }
}
