package com.cloudforgeci.samples.app;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ApplicationLoader plugin discovery system.
 */
public class PluginDiscoveryTest {

    @Test
    public void testPluginDiscovery() {
        System.out.println("\n📦 Testing Application Plugin Discovery");
        System.out.println("========================================\n");

        // Discover all applications
        Map<String, ApplicationSpec> apps = ApplicationLoader.discover();

        System.out.println("✅ Discovered " + apps.size() + " applications:");
        for (String appId : apps.keySet()) {
            ApplicationSpec spec = apps.get(appId);
            System.out.println("  • " + appId + " (" + spec.displayName() + ") - " + spec.category());
        }
        System.out.println();

        // Verify we have applications
        assertTrue(apps.size() > 0, "Should discover at least one application");
    }

    @Test
    public void testSonarQubeDiscovery() {
        System.out.println("\n🔍 Testing SonarQube Plugin Discovery");
        System.out.println("======================================\n");

        // Find SonarQube
        Optional<ApplicationSpec> sonarQube = ApplicationLoader.findById("sonarqube");

        if (sonarQube.isPresent()) {
            ApplicationSpec spec = sonarQube.get();
            System.out.println("✅ Found SonarQube:");
            System.out.println("   ID: " + spec.applicationId());
            System.out.println("   Display Name: " + spec.displayName());
            System.out.println("   Description: " + spec.description());
            System.out.println("   Category: " + spec.category());
            System.out.println("   Supports Fargate: " + spec.supportsFargate());
            System.out.println("   Supports EC2: " + spec.supportsEc2());
            System.out.println("   Supports OIDC: " + spec.supportsOidcIntegration());
            System.out.println("   Default CPU: " + spec.defaultCpu());
            System.out.println("   Default Memory: " + spec.defaultMemory());
            System.out.println("   Default Instance Type: " + spec.defaultInstanceType());
            System.out.println();

            // Verify SonarQube metadata
            assertEquals("sonarqube", spec.applicationId());
            assertEquals("SonarQube", spec.displayName());
            assertEquals("code-quality", spec.category());
            assertEquals(2048, spec.defaultCpu());
            assertEquals(4096, spec.defaultMemory());
            assertEquals("t3.medium", spec.defaultInstanceType());
        } else {
            fail("SonarQube plugin not discovered! Check META-INF/services registration.");
        }
    }

    @Test
    public void testCategoryGrouping() {
        System.out.println("\n📂 Testing Category Grouping");
        System.out.println("=============================\n");

        Map<String, List<ApplicationSpec>> grouped = ApplicationLoader.discoverGroupedByCategory();

        System.out.println("✅ Found " + grouped.size() + " categories:");
        for (Map.Entry<String, List<ApplicationSpec>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<ApplicationSpec> apps = entry.getValue();
            System.out.println("\n  " + category.toUpperCase() + " (" + apps.size() + " apps):");
            for (ApplicationSpec spec : apps) {
                System.out.println("    • " + spec.displayName() + " (" + spec.applicationId() + ")");
            }
        }
        System.out.println();

        // Verify categories exist
        assertTrue(grouped.size() > 0, "Should have at least one category");
    }

    @Test
    public void testPrintCatalog() {
        System.out.println("\n📋 Application Catalog");
        System.out.println("======================\n");

        String catalog = ApplicationLoader.printCatalog();
        System.out.println(catalog);

        // Verify catalog contains content
        assertNotNull(catalog);
        assertTrue(catalog.contains("CloudForge Application Catalog"));
    }

    @Test
    public void testBuiltInApplications() {
        System.out.println("\n🔧 Testing Built-In Applications");
        System.out.println("=================================\n");

        // Test some known built-in applications (must resolve via ServiceLoader)
        String[] knownApps = {"jenkins", "gitlab", "grafana", "vault", "postgresql", "cloudforge-manager"};

        for (String appId : knownApps) {
            Optional<ApplicationSpec> spec = ApplicationLoader.findById(appId);
            assertTrue(spec.isPresent(), "Built-in application not discovered: " + appId);
            System.out.println("  ✅ " + spec.get().displayName() + " (" + appId + ") - " + spec.get().category());
        }

        Optional<ApplicationSpec> manager = ApplicationLoader.findById("cloudforge-manager");
        assertEquals("operations", manager.get().category());
        assertEquals(1958, manager.get().applicationPort());
        System.out.println();
    }
}
