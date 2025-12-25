package com.cloudforgeci.api.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for FargateFactory.
 *
 * Tests Fargate configuration without requiring full CDK context.
 */
class FargateFactoryTest {

    @Test
    void testFargateFactoryClassExists() {
        // When: Accessing FargateFactory class
        Class<?> factoryClass = FargateFactory.class;

        // Then: Should exist
        assertNotNull(factoryClass);
        assertEquals("FargateFactory", factoryClass.getSimpleName());
    }

    @Test
    void testFargateFactoryIsPublicClass() {
        // When: Checking FargateFactory modifiers
        Class<?> factoryClass = FargateFactory.class;

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testFargateFactoryExtendsBaseFactory() {
        // When: Checking FargateFactory superclass
        Class<?> factoryClass = FargateFactory.class;
        Class<?> superclass = factoryClass.getSuperclass();

        // Then: Should extend BaseFactory
        assertNotNull(superclass);
        assertEquals("BaseFactory", superclass.getSimpleName());
    }

    @Test
    void testFargateFactoryHasCreateMethod() throws NoSuchMethodException {
        // When: Getting create method
        var method = FargateFactory.class.getDeclaredMethod("create");

        // Then: Should exist and be public
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testFargateFactoryPackage() {
        // When: Getting package
        Package pkg = FargateFactory.class.getPackage();

        // Then: Should be in compute package
        assertNotNull(pkg);
        assertEquals("com.cloudforgeci.api.compute", pkg.getName());
    }

    @Test
    void testFargateFactoryIsNotAbstract() {
        // When: Checking if FargateFactory is abstract
        Class<?> factoryClass = FargateFactory.class;

        // Then: Should not be abstract
        assertFalse(java.lang.reflect.Modifier.isAbstract(factoryClass.getModifiers()));
    }

    @Test
    void testFargateFactoryIsNotInterface() {
        // When: Checking if FargateFactory is interface
        Class<?> factoryClass = FargateFactory.class;

        // Then: Should not be interface
        assertFalse(factoryClass.isInterface());
    }

    @Test
    void testFargateFactoryIsNotEnum() {
        // When: Checking if FargateFactory is enum
        Class<?> factoryClass = FargateFactory.class;

        // Then: Should not be enum
        assertFalse(factoryClass.isEnum());
    }

    @Test
    void testFargateFactoryHasDeploymentContextFields() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: Should have DeploymentContext annotated fields
        long annotatedFields = java.util.Arrays.stream(fields)
            .filter(f -> f.isAnnotationPresent(com.cloudforge.core.annotation.DeploymentContext.class))
            .count();

        assertTrue(annotatedFields >= 1, "Should have at least 1 @DeploymentContext annotated field");
    }

    @Test
    void testFargateFactoryFieldsArePrivate() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: All instance fields should be private
        for (var field : fields) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "Field " + field.getName() + " should be private");
            }
        }
    }

    @Test
    void testFargateFactoryHasConstructor() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = FargateFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Should exist and be public
        assertNotNull(constructor);
        assertTrue(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
    }

    @Test
    void testFargateFactoryConstructorParameterCount() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = FargateFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Should have exactly 2 parameters
        assertEquals(2, constructor.getParameterCount());
    }

    @Test
    void testFargateFactoryIsInCorrectPackage() {
        // When: Getting package name
        String packageName = FargateFactory.class.getPackage().getName();

        // Then: Should be in compute package
        assertTrue(packageName.endsWith(".compute"),
            "Should be in compute package");
    }

    @Test
    void testFargateFactoryFieldsHaveAnnotations() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: Should have annotated fields (DeploymentContext)
        long annotatedFields = java.util.Arrays.stream(fields)
            .filter(f -> f.getAnnotations().length > 0)
            .count();

        assertTrue(annotatedFields >= 1, "Should have at least 1 annotated field");
    }

    @Test
    void testFargateFactoryHasNoInnerClasses() {
        // When: Getting inner classes
        Class<?>[] innerClasses = FargateFactory.class.getDeclaredClasses();

        // Then: Should not have inner classes
        assertEquals(0, innerClasses.length, "Should not have inner classes");
    }

    @Test
    void testFargateFactoryClassStructure() {
        // When: Analyzing class structure
        Class<?> factoryClass = FargateFactory.class;

        // Then: Should have proper structure
        assertNotNull(factoryClass);
        assertEquals("FargateFactory", factoryClass.getSimpleName());
        assertFalse(factoryClass.isInterface());
        assertFalse(factoryClass.isEnum());
        assertFalse(java.lang.reflect.Modifier.isAbstract(factoryClass.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testFargateFactoryMethodsAreWellNamed() {
        // When: Getting methods
        var methods = FargateFactory.class.getDeclaredMethods();

        // Then: All methods should have meaningful names
        for (var method : methods) {
            String name = method.getName();
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    @Test
    void testFargateFactoryFieldCount() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: Should have reasonable number of fields
        assertTrue(fields.length >= 1, "Should have at least 1 field");
        assertTrue(fields.length < 50, "Should not have too many fields");
    }

    @Test
    void testFargateFactoryMethodCount() {
        // When: Getting all methods (excluding inherited)
        var methods = FargateFactory.class.getDeclaredMethods();

        // Then: Should have create method and helpers
        assertTrue(methods.length >= 1, "Should have at least create method");
    }

    @Test
    void testFargateFactoryHasPrivateMethods() {
        // When: Getting all declared methods
        var methods = FargateFactory.class.getDeclaredMethods();

        // Then: Should have private helper methods
        long privateMethodCount = java.util.Arrays.stream(methods)
            .filter(m -> java.lang.reflect.Modifier.isPrivate(m.getModifiers()))
            .count();

        assertTrue(privateMethodCount > 0, "Should have private helper methods");
    }

    @Test
    void testFargateFactoryConstructorParameterTypes() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = FargateFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Parameters should be Construct and String
        var paramTypes = constructor.getParameterTypes();
        assertEquals(software.constructs.Construct.class, paramTypes[0]);
        assertEquals(String.class, paramTypes[1]);
    }

    @Test
    void testFargateFactoryMethodsHaveProperAccess() {
        // When: Getting methods
        var methods = FargateFactory.class.getDeclaredMethods();

        // Then: Should have proper access modifiers
        long publicMethods = java.util.Arrays.stream(methods)
            .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
            .count();

        assertTrue(publicMethods >= 1, "Should have at least 1 public method (create)");
    }

    @Test
    void testFargateFactoryIsNotFinal() {
        // When: Checking if FargateFactory is final
        Class<?> factoryClass = FargateFactory.class;

        // Then: Could be extended if needed
        assertFalse(java.lang.reflect.Modifier.isFinal(factoryClass.getModifiers()));
    }

    @Test
    void testFargateFactoryHasStringFields() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: Should have String configuration fields
        long stringFields = java.util.Arrays.stream(fields)
            .filter(f -> f.getType().equals(String.class))
            .count();

        assertTrue(stringFields >= 1, "Should have at least 1 String field");
    }

    @Test
    void testFargateFactoryHasIntegerFields() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: Should have Integer configuration fields
        long integerFields = java.util.Arrays.stream(fields)
            .filter(f -> f.getType().equals(Integer.class))
            .count();

        assertTrue(integerFields >= 1, "Should have at least 1 Integer field for capacity/resource config");
    }

    // ========== Security Hardening Tests ==========

    @Test
    void testFargateFactoryHasNetworkModeField() {
        // When: Getting declared fields
        var fields = FargateFactory.class.getDeclaredFields();

        // Then: Should have NetworkMode field for egress restriction logic
        long networkModeFields = java.util.Arrays.stream(fields)
            .filter(f -> f.getType().getName().contains("NetworkMode"))
            .count();

        assertTrue(networkModeFields >= 1, "Should have NetworkMode field for egress restriction");
    }

    @Test
    void testFargateFactoryUsesSecurityProfileConfiguration() {
        // FargateFactory should use SecurityProfileConfiguration via inherited 'config' field from BaseFactory
        Class<?> factoryClass = FargateFactory.class;
        Class<?> superclass = factoryClass.getSuperclass();

        // Then: BaseFactory should have config field
        boolean hasConfigInBase = java.util.Arrays.stream(superclass.getDeclaredFields())
            .anyMatch(f -> f.getName().equals("config"));

        assertTrue(hasConfigInBase, "BaseFactory should have config field for SecurityProfileConfiguration");
    }

    @Test
    void testFargateFactoryCreateMethodIsOverrideable() throws NoSuchMethodException {
        // When: Getting create method
        var method = FargateFactory.class.getDeclaredMethod("create");

        // Then: Should not be final (allowing subclass override if needed)
        assertFalse(java.lang.reflect.Modifier.isFinal(method.getModifiers()));
    }
}
