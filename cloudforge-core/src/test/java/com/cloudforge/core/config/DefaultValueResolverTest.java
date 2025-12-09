package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultValueResolverTest {

    // Test ApplicationSpec implementation with all required methods
    static class TestApplicationSpec implements ApplicationSpec {
        @Override
        public String applicationId() { return "test-app"; }

        @Override
        public String defaultContainerImage() { return "test/image:latest"; }

        @Override
        public int applicationPort() { return 8080; }

        @Override
        public String containerDataPath() { return "/app/data"; }

        @Override
        public String efsDataPath() { return "/test-app"; }

        @Override
        public String volumeName() { return "testAppData"; }

        @Override
        public String containerUser() { return "1000:1000"; }

        @Override
        public String efsPermissions() { return "755"; }

        @Override
        public String ebsDeviceName() { return "/dev/xvdh"; }

        @Override
        public String ec2DataPath() { return "/var/lib/test-app"; }

        @Override
        public List<String> ec2LogPaths() { return List.of("/var/log/test-app.log"); }

        @Override
        public void configureUserData(UserDataBuilder builder, Ec2Context context) {
            // No-op for testing
        }

        // Method that returns a simple value (used by defaultFrom)
        public int defaultCpu() {
            return 2048;
        }

        // Method that returns a string
        public String defaultEngine() {
            return "postgres";
        }

        // Method for chaining test
        public DatabaseRequirement databaseRequirement() {
            return new DatabaseRequirement();
        }

        // Method that returns null
        public String nullMethod() {
            return null;
        }
    }

    // Minimal test ApplicationSpec that doesn't have custom methods
    static class MinimalApplicationSpec implements ApplicationSpec {
        @Override
        public String applicationId() { return "minimal"; }

        @Override
        public String defaultContainerImage() { return "minimal/image:latest"; }

        @Override
        public int applicationPort() { return 8080; }

        @Override
        public String containerDataPath() { return "/data"; }

        @Override
        public String efsDataPath() { return "/minimal"; }

        @Override
        public String volumeName() { return "minimalData"; }

        @Override
        public String containerUser() { return "1000:1000"; }

        @Override
        public String efsPermissions() { return "755"; }

        @Override
        public String ebsDeviceName() { return "/dev/xvdh"; }

        @Override
        public String ec2DataPath() { return "/var/lib/minimal"; }

        @Override
        public List<String> ec2LogPaths() { return List.of(); }

        @Override
        public void configureUserData(UserDataBuilder builder, Ec2Context context) {
            // No-op
        }
        // Note: defaultCpu() method is NOT defined here - will cause NoSuchMethodException
    }

    // Helper class for chained method tests
    static class DatabaseRequirement {
        public String engine() {
            return "aurora-postgresql";
        }

        public String version() {
            return "15.4";
        }

        public NestedConfig nested() {
            return new NestedConfig();
        }
    }

    static class NestedConfig {
        public String deepValue() {
            return "deep-value";
        }
    }

    // Test config class with @ConfigField annotations
    static class TestConfig {
        @ConfigField(
            displayName = "CPU Units",
            description = "CPU units for container",
            defaultFrom = "defaultCpu"
        )
        public Integer cpu = 1024;

        @ConfigField(
            displayName = "Database Engine",
            description = "Database engine",
            defaultFrom = "databaseRequirement().engine"
        )
        public String databaseEngine = "mysql";

        @ConfigField(
            displayName = "Database Version",
            description = "Database version",
            defaultFrom = "databaseRequirement().version"
        )
        public String databaseVersion = "14";

        @ConfigField(
            displayName = "No Default From",
            description = "Field without defaultFrom"
        )
        public String noDefaultFrom = "static-default";

        @ConfigField(
            displayName = "Invalid Method",
            description = "Field with invalid method",
            defaultFrom = "nonExistentMethod"
        )
        public String invalidMethod = "fallback";

        @ConfigField(
            displayName = "Null Result",
            description = "Field where method returns null",
            defaultFrom = "nullMethod"
        )
        public String nullResult = "fallback-for-null";

        @ConfigField(
            displayName = "Deep Nested",
            description = "Deep nested chain",
            defaultFrom = "databaseRequirement().nested().deepValue"
        )
        public String deepNested = "default-deep";
    }

    private ConfigFieldInfo createFieldInfo(String fieldName) throws NoSuchFieldException {
        Field field = TestConfig.class.getDeclaredField(fieldName);
        return ConfigFieldInfo.from(field);
    }

    @Test
    void testResolveSimpleMethodDefault() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertEquals(2048, result);
    }

    @Test
    void testResolveChainedMethodDefault() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("databaseEngine");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertEquals("aurora-postgresql", result);
    }

    @Test
    void testResolveChainedMethodVersion() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("databaseVersion");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertEquals("15.4", result);
    }

    @Test
    void testResolveWithoutDefaultFromReturnsNull() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("noDefaultFrom");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertNull(result);
    }

    @Test
    void testResolveWithNullAppSpecReturnsNull() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");

        Object result = DefaultValueResolver.resolve(fieldInfo, null, null);

        assertNull(result);
    }

    @Test
    void testResolveWithInvalidMethodReturnsNull() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("invalidMethod");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertNull(result);
    }

    @Test
    void testResolveMethodReturningNullReturnsNull() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("nullResult");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertNull(result);
    }

    @Test
    void testResolveDeepNestedChain() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("deepNested");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertEquals("deep-value", result);
    }

    @Test
    void testResolveWithEmptyFrameworksList() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, Collections.emptyList());

        // Should fall through to ApplicationSpec default
        assertEquals(2048, result);
    }

    @Test
    void testResolveWithFallbackReturnsResolvedValue() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");
        TestApplicationSpec appSpec = new TestApplicationSpec();
        TestConfig config = new TestConfig();

        Object result = DefaultValueResolver.resolveWithFallback(fieldInfo, appSpec, null, config);

        // Should return ApplicationSpec default (2048), not config default (1024)
        assertEquals(2048, result);
    }

    @Test
    void testResolveWithFallbackReturnsConfigValueWhenNoDefault() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("noDefaultFrom");
        TestApplicationSpec appSpec = new TestApplicationSpec();
        TestConfig config = new TestConfig();
        config.noDefaultFrom = "custom-value";

        Object result = DefaultValueResolver.resolveWithFallback(fieldInfo, appSpec, null, config);

        // Should fall back to config value since no defaultFrom
        assertEquals("custom-value", result);
    }

    @Test
    void testResolveWithFallbackAndNullAppSpec() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");
        TestConfig config = new TestConfig();
        config.cpu = 512;

        Object result = DefaultValueResolver.resolveWithFallback(fieldInfo, null, null, config);

        // Should fall back to config value since appSpec is null
        assertEquals(512, result);
    }

    @Test
    void testResolveWithNullFrameworksListHandledGracefully() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");
        TestApplicationSpec appSpec = new TestApplicationSpec();

        // Should not throw and should use ApplicationSpec default
        Object result = DefaultValueResolver.resolve(fieldInfo, appSpec, null);

        assertEquals(2048, result);
    }

    @Test
    void testResolveReturnsNullWhenAllSourcesFail() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("invalidMethod");

        // No appSpec, no frameworks
        Object result = DefaultValueResolver.resolve(fieldInfo, null, null);

        assertNull(result);
    }

    @Test
    void testResolveUsesInterfaceDefaultMethod() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("cpu");

        // MinimalApplicationSpec doesn't override defaultCpu(), so it uses the interface default (1024)
        MinimalApplicationSpec minimalSpec = new MinimalApplicationSpec();

        // Should return the interface default value (1024) from ApplicationSpec.defaultCpu()
        Object result = DefaultValueResolver.resolve(fieldInfo, minimalSpec, null);

        // The interface provides a default implementation that returns 1024
        assertEquals(1024, result);
    }

    @Test
    void testResolveWithFieldThatHasNoInterfaceMethod() throws NoSuchFieldException {
        ConfigFieldInfo fieldInfo = createFieldInfo("invalidMethod");
        MinimalApplicationSpec minimalSpec = new MinimalApplicationSpec();

        // nonExistentMethod doesn't exist on ApplicationSpec at all
        Object result = DefaultValueResolver.resolve(fieldInfo, minimalSpec, null);

        assertNull(result);
    }
}
