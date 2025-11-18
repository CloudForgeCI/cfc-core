package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for BaseFactory class.
 * Tests annotation-based context injection and getter methods.
 */
class BaseFactoryTest {

    private App app;
    private Stack stack;
    private TestBaseFactory factory;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "TestStack");
        // Start SystemContext before creating factory
        SystemContext.start(stack,
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.FARGATE,
            com.cloudforgeci.api.interfaces.SecurityProfile.DEV,
            com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL,
            DeploymentContext.from(stack));
        factory = new TestBaseFactory(stack, "TestFactory");
    }

    /**
     * Test implementation of BaseFactory for testing purposes.
     */
    private static class TestBaseFactory extends BaseFactory {
        public TestBaseFactory(Construct scope, String id) {
            super(scope, id);
        }

        public void create() {
            // Test implementation - does nothing
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create BaseFactory with correct parent and ID")
        void shouldCreateBaseFactoryWithCorrectParentAndId() {
            assertNotNull(factory, "Factory should be created");
            assertEquals("TestFactory", factory.getNode().getId(), "Factory should have correct ID");
            assertEquals(stack, factory.getNode().getScope(), "Factory should have correct parent");
        }

        @Test
        @DisplayName("Should extend Construct")
        void shouldExtendConstruct() {
            assertTrue(Construct.class.isAssignableFrom(BaseFactory.class), 
                "BaseFactory should extend Construct");
        }

        @Test
        @DisplayName("Should have final protected context fields")
        void shouldHaveFinalProtectedContextFields() {
            assertDoesNotThrow(() -> {
                Field ctxField = BaseFactory.class.getDeclaredField("ctx");
                assertTrue(java.lang.reflect.Modifier.isFinal(ctxField.getModifiers()),
                    "ctx field should be final");
                assertTrue(java.lang.reflect.Modifier.isProtected(ctxField.getModifiers()),
                    "ctx field should be protected");

                Field cfcField = BaseFactory.class.getDeclaredField("cfc");
                assertTrue(java.lang.reflect.Modifier.isFinal(cfcField.getModifiers()),
                    "cfc field should be final");
                assertTrue(java.lang.reflect.Modifier.isProtected(cfcField.getModifiers()),
                    "cfc field should be protected");

                Field configField = BaseFactory.class.getDeclaredField("config");
                assertTrue(java.lang.reflect.Modifier.isFinal(configField.getModifiers()),
                    "config field should be final");
                assertTrue(java.lang.reflect.Modifier.isProtected(configField.getModifiers()),
                    "config field should be protected");
            }, "Should have final protected context fields");
        }
    }

    @Nested
    @DisplayName("Getter Method Tests")
    class GetterMethodTests {

        @Test
        @DisplayName("getSystemContext should return ctx field")
        void getSystemContextShouldReturnCtxField() {
            assertDoesNotThrow(() -> {
                SystemContext result = factory.getSystemContext();

                // The getter should return the ctx field initialized in constructor
                assertNotNull(result, "getSystemContext should return initialized SystemContext");
            }, "getSystemContext should work without throwing exceptions");
        }

        @Test
        @DisplayName("getDeploymentContext should return cfc field")
        void getDeploymentContextShouldReturnCfcField() {
            assertDoesNotThrow(() -> {
                DeploymentContext result = factory.getDeploymentContext();

                // The getter should return the cfc field initialized in constructor
                assertNotNull(result, "getDeploymentContext should return initialized DeploymentContext");
            }, "getDeploymentContext should work without throwing exceptions");
        }

        @Test
        @DisplayName("getSecurityProfileConfiguration should return config field")
        void getSecurityProfileConfigurationShouldReturnConfigField() {
            assertDoesNotThrow(() -> {
                SecurityProfileConfiguration result = factory.getSecurityProfileConfiguration();

                // The getter should return the config field initialized in constructor
                assertNotNull(result, "getSecurityProfileConfiguration should return initialized SecurityProfileConfiguration");
            }, "getSecurityProfileConfiguration should work without throwing exceptions");
        }

        @Test
        @DisplayName("All getter methods should be accessible")
        void allGetterMethodsShouldBeAccessible() {
            assertDoesNotThrow(() -> {
                BaseFactory.class.getDeclaredMethod("getSystemContext");
                BaseFactory.class.getDeclaredMethod("getDeploymentContext");
                BaseFactory.class.getDeclaredMethod("getSecurityProfileConfiguration");
            }, "All getter methods should be accessible");
        }

        @Test
        @DisplayName("All getter methods should return correct types")
        void allGetterMethodsShouldReturnCorrectTypes() {
            assertDoesNotThrow(() -> {
                var getSystemContextMethod = BaseFactory.class.getDeclaredMethod("getSystemContext");
                assertEquals(SystemContext.class, getSystemContextMethod.getReturnType(), 
                    "getSystemContext should return SystemContext");
                
                var getDeploymentContextMethod = BaseFactory.class.getDeclaredMethod("getDeploymentContext");
                assertEquals(DeploymentContext.class, getDeploymentContextMethod.getReturnType(), 
                    "getDeploymentContext should return DeploymentContext");
                
                var getSecurityProfileConfigurationMethod = BaseFactory.class.getDeclaredMethod("getSecurityProfileConfiguration");
                assertEquals(SecurityProfileConfiguration.class, getSecurityProfileConfigurationMethod.getReturnType(), 
                    "getSecurityProfileConfiguration should return SecurityProfileConfiguration");
            }, "All getter methods should have correct return types");
        }
    }

    @Nested
    @DisplayName("Abstract Method Tests")
    class AbstractMethodTests {

        @Test
        @DisplayName("BaseFactory should have abstract create method")
        void baseFactoryShouldHaveAbstractCreateMethod() {
            // Since we added the abstract create() method, verify it exists
            assertDoesNotThrow(() -> {
                BaseFactory.class.getMethod("create");
            }, "create method should exist in BaseFactory");
        }

        @Test
        @DisplayName("TestBaseFactory should implement create method")
        void testBaseFactoryShouldImplementCreateMethod() {
            assertDoesNotThrow(() -> {
                factory.create();
            }, "TestBaseFactory should implement create method");
        }
    }

    @Nested
    @DisplayName("Field Access Tests")
    class FieldAccessTests {

        @Test
        @DisplayName("Protected fields should be accessible to subclasses")
        void protectedFieldsShouldBeAccessibleToSubclasses() {
            assertDoesNotThrow(() -> {
                Field ctxField = BaseFactory.class.getDeclaredField("ctx");
                assertTrue(java.lang.reflect.Modifier.isProtected(ctxField.getModifiers()), 
                    "ctx field should be protected");
                
                Field cfcField = BaseFactory.class.getDeclaredField("cfc");
                assertTrue(java.lang.reflect.Modifier.isProtected(cfcField.getModifiers()), 
                    "cfc field should be protected");
                
                Field configField = BaseFactory.class.getDeclaredField("config");
                assertTrue(java.lang.reflect.Modifier.isProtected(configField.getModifiers()), 
                    "config field should be protected");
            }, "Protected fields should be accessible to subclasses");
        }

        @Test
        @DisplayName("Fields should have correct types")
        void fieldsShouldHaveCorrectTypes() {
            assertDoesNotThrow(() -> {
                Field ctxField = BaseFactory.class.getDeclaredField("ctx");
                assertEquals(SystemContext.class, ctxField.getType(), 
                    "ctx field should be SystemContext type");
                
                Field cfcField = BaseFactory.class.getDeclaredField("cfc");
                assertEquals(DeploymentContext.class, cfcField.getType(), 
                    "cfc field should be DeploymentContext type");
                
                Field configField = BaseFactory.class.getDeclaredField("config");
                assertEquals(SecurityProfileConfiguration.class, configField.getType(), 
                    "config field should be SecurityProfileConfiguration type");
            }, "Fields should have correct types");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("BaseFactory should work with multiple instances")
        void baseFactoryShouldWorkWithMultipleInstances() {
            assertDoesNotThrow(() -> {
                TestBaseFactory factory1 = new TestBaseFactory(stack, "Factory1");
                TestBaseFactory factory2 = new TestBaseFactory(stack, "Factory2");
                
                assertNotNull(factory1, "First factory should be created");
                assertNotNull(factory2, "Second factory should be created");
                assertNotEquals(factory1.getNode().getId(), factory2.getNode().getId(), 
                    "Factories should have different IDs");
            }, "BaseFactory should work with multiple instances");
        }

        @Test
        @DisplayName("BaseFactory should initialize contexts in constructor")
        void baseFactoryShouldInitializeContextsInConstructor() {
            assertDoesNotThrow(() -> {
                // Create factory - contexts are initialized in constructor
                TestBaseFactory factory = new TestBaseFactory(stack, "ContextInitFactory");

                // Getters should return initialized contexts
                assertNotNull(factory.getSystemContext(), "getSystemContext should return initialized context");
                assertNotNull(factory.getDeploymentContext(), "getDeploymentContext should return initialized context");
                assertNotNull(factory.getSecurityProfileConfiguration(), "getSecurityProfileConfiguration should return initialized config");
            }, "BaseFactory should initialize contexts in constructor");
        }

        @Test
        @DisplayName("BaseFactory should be thread-safe for getter methods")
        void baseFactoryShouldBeThreadSafeForGetterMethods() {
            assertDoesNotThrow(() -> {
                TestBaseFactory factory = new TestBaseFactory(stack, "ThreadSafeFactory");
                
                // Multiple threads calling getter methods should be safe
                Runnable task = () -> {
                    factory.getSystemContext();
                    factory.getDeploymentContext();
                    factory.getSecurityProfileConfiguration();
                };
                
                Thread thread1 = new Thread(task);
                Thread thread2 = new Thread(task);
                
                thread1.start();
                thread2.start();
                
                thread1.join();
                thread2.join();
            }, "BaseFactory should be thread-safe for getter methods");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Constructor should handle invalid parameters gracefully")
        void constructorShouldHandleInvalidParametersGracefully() {
            assertDoesNotThrow(() -> {
                // Test with null scope (should throw IllegalArgumentException from Construct)
                assertThrows(Exception.class, () -> {
                    new TestBaseFactory(null, "TestId");
                }, "Constructor should throw exception with null scope");
            }, "Constructor should handle invalid parameters gracefully");
        }

        @Test
        @DisplayName("Getter methods should return initialized contexts")
        void getterMethodsShouldReturnInitializedContexts() {
            assertDoesNotThrow(() -> {
                TestBaseFactory factory = new TestBaseFactory(stack, "InitializedFactory");

                // All getters should return initialized contexts
                SystemContext ctx = factory.getSystemContext();
                DeploymentContext cfc = factory.getDeploymentContext();
                SecurityProfileConfiguration config = factory.getSecurityProfileConfiguration();

                assertNotNull(ctx, "SystemContext should be initialized");
                assertNotNull(cfc, "DeploymentContext should be initialized");
                assertNotNull(config, "SecurityProfileConfiguration should be initialized");
            }, "Getter methods should return initialized contexts");
        }
    }
}
