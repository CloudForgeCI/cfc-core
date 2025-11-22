package com.cloudforgeci.api.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SecurityProfileFactory.
 *
 * Tests security profile-based observability configurations without requiring full CDK context.
 */
class SecurityProfileFactoryTest {

    @Test
    void testSecurityProfileFactoryClassExists() {
        // When: Accessing SecurityProfileFactory class
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Should exist
        assertNotNull(factoryClass);
        assertEquals("SecurityProfileFactory", factoryClass.getSimpleName());
    }

    @Test
    void testSecurityProfileFactoryIsPublicClass() {
        // When: Checking SecurityProfileFactory modifiers
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testSecurityProfileFactoryExtendsBaseFactory() {
        // When: Checking SecurityProfileFactory superclass
        Class<?> factoryClass = SecurityProfileFactory.class;
        Class<?> superclass = factoryClass.getSuperclass();

        // Then: Should extend BaseFactory
        assertNotNull(superclass);
        assertEquals("BaseFactory", superclass.getSimpleName());
    }

    @Test
    void testSecurityProfileFactoryHasCreateMethod() throws NoSuchMethodException {
        // When: Getting create method
        var method = SecurityProfileFactory.class.getDeclaredMethod("create");

        // Then: Should exist and be public
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testSecurityProfileFactoryPackage() {
        // When: Getting package
        Package pkg = SecurityProfileFactory.class.getPackage();

        // Then: Should be in security package
        assertNotNull(pkg);
        assertEquals("com.cloudforgeci.api.core.security", pkg.getName());
    }

    @Test
    void testSecurityProfileFactoryIsNotAbstract() {
        // When: Checking if SecurityProfileFactory is abstract
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Should not be abstract
        assertFalse(java.lang.reflect.Modifier.isAbstract(factoryClass.getModifiers()));
    }

    @Test
    void testSecurityProfileFactoryIsNotInterface() {
        // When: Checking if SecurityProfileFactory is interface
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Should not be interface
        assertFalse(factoryClass.isInterface());
    }

    @Test
    void testSecurityProfileFactoryIsNotEnum() {
        // When: Checking if SecurityProfileFactory is enum
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Should not be enum
        assertFalse(factoryClass.isEnum());
    }

    @Test
    void testSecurityProfileFactoryIsNotFinal() {
        // When: Checking if SecurityProfileFactory is final
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Could be extended if needed
        assertFalse(java.lang.reflect.Modifier.isFinal(factoryClass.getModifiers()));
    }

    @Test
    void testSecurityProfileFactoryHasPrivateMethods() {
        // When: Getting all declared methods
        var methods = SecurityProfileFactory.class.getDeclaredMethods();

        // Then: Should have private helper methods
        long privateMethodCount = java.util.Arrays.stream(methods)
            .filter(m -> java.lang.reflect.Modifier.isPrivate(m.getModifiers()))
            .count();

        assertTrue(privateMethodCount > 0, "Should have private helper methods");
    }

    @Test
    void testSecurityProfileFactoryHasGetSecurityProfileConfigurationMethod() throws NoSuchMethodException {
        // When: Getting method
        var method = SecurityProfileFactory.class.getDeclaredMethod(
            "getSecurityProfileConfiguration",
            com.cloudforgeci.api.interfaces.SecurityProfile.class
        );

        // Then: Should exist and be private
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()));
        assertEquals(com.cloudforgeci.api.interfaces.SecurityProfileConfiguration.class, method.getReturnType());
    }

    @Test
    void testSecurityProfileFactoryHasConfigureCloudWatchLogsMethod() throws NoSuchMethodException {
        // When: Getting method
        var method = SecurityProfileFactory.class.getDeclaredMethod(
            "configureCloudWatchLogs",
            com.cloudforgeci.api.interfaces.SecurityProfileConfiguration.class
        );

        // Then: Should exist and be private
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testSecurityProfileFactoryHasConfigureVpcFlowLogsMethod() throws NoSuchMethodException {
        // When: Getting method
        var method = SecurityProfileFactory.class.getDeclaredMethod(
            "configureVpcFlowLogs",
            com.cloudforgeci.api.interfaces.SecurityProfileConfiguration.class
        );

        // Then: Should exist and be private
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testSecurityProfileFactoryHasConfigureSecurityMonitoringMethod() throws NoSuchMethodException {
        // When: Getting method
        var method = SecurityProfileFactory.class.getDeclaredMethod(
            "configureSecurityMonitoring",
            com.cloudforgeci.api.interfaces.SecurityProfileConfiguration.class
        );

        // Then: Should exist and be private
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testSecurityProfileFactoryHasLogger() {
        // When: Getting declared fields
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should have Logger field
        boolean hasLogger = java.util.Arrays.stream(fields)
            .anyMatch(f -> f.getType().equals(java.util.logging.Logger.class));

        assertTrue(hasLogger, "Should have Logger field");
    }

    @Test
    void testSecurityProfileFactoryHasSecurityField() {
        // When: Getting declared fields
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should have security field
        boolean hasSecurity = java.util.Arrays.stream(fields)
            .anyMatch(f -> f.getType().equals(com.cloudforgeci.api.interfaces.SecurityProfile.class));

        assertTrue(hasSecurity, "Should have SecurityProfile field");
    }

    @Test
    void testSecurityProfileFactoryHasRuntimeField() {
        // When: Getting declared fields
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should have runtime field
        boolean hasRuntime = java.util.Arrays.stream(fields)
            .anyMatch(f -> f.getType().equals(com.cloudforgeci.api.interfaces.RuntimeType.class));

        assertTrue(hasRuntime, "Should have RuntimeType field");
    }

    @Test
    void testSecurityProfileFactoryHasStackNameField() {
        // When: Getting declared fields
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should have stackName field
        boolean hasStackName = java.util.Arrays.stream(fields)
            .anyMatch(f -> f.getType().equals(String.class) && f.getName().equals("stackName"));

        assertTrue(hasStackName, "Should have stackName field");
    }

    @Test
    void testSecurityProfileFactoryFieldsArePrivate() {
        // When: Getting declared fields
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: All instance fields should be private
        for (var field : fields) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "Field " + field.getName() + " should be private");
            }
        }
    }

    @Test
    void testSecurityProfileFactoryMethodCount() {
        // When: Getting all methods (excluding inherited)
        var methods = SecurityProfileFactory.class.getDeclaredMethods();

        // Then: Should have reasonable number of methods (create + helpers)
        assertTrue(methods.length >= 4, "Should have at least 4 methods (create + helpers)");
        assertTrue(methods.length < 20, "Should not have too many methods");
    }

    @Test
    void testSecurityProfileFactoryFieldsHaveSystemContextAnnotation() {
        // When: Getting declared fields
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should have fields with SystemContext annotation
        long annotatedFields = java.util.Arrays.stream(fields)
            .filter(f -> f.isAnnotationPresent(com.cloudforgeci.api.core.annotation.SystemContext.class))
            .count();

        assertTrue(annotatedFields >= 3, "Should have at least 3 @SystemContext annotated fields");
    }

    @Test
    void testSecurityProfileFactoryClassStructure() {
        // When: Analyzing class structure
        Class<?> factoryClass = SecurityProfileFactory.class;

        // Then: Should have proper structure
        assertNotNull(factoryClass);
        assertEquals("SecurityProfileFactory", factoryClass.getSimpleName());
        assertFalse(factoryClass.isInterface());
        assertFalse(factoryClass.isEnum());
        assertFalse(java.lang.reflect.Modifier.isAbstract(factoryClass.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testSecurityProfileFactoryHasNoInnerClasses() {
        // When: Getting inner classes
        Class<?>[] innerClasses = SecurityProfileFactory.class.getDeclaredClasses();

        // Then: Should not have inner classes
        assertEquals(0, innerClasses.length, "Should not have inner classes");
    }

    @Test
    void testSecurityProfileFactoryMethodsAreWellNamed() {
        // When: Getting methods
        var methods = SecurityProfileFactory.class.getDeclaredMethods();

        // Then: All methods should have meaningful names
        for (var method : methods) {
            String name = method.getName();
            assertNotNull(name);
            assertFalse(name.isEmpty());
            assertFalse(name.equals("method1") || name.equals("temp") || name.equals("foo"),
                "Method name should be meaningful: " + name);
        }
    }

    @Test
    void testSecurityProfileFactoryImportsSecurityProfile() {
        // When: Checking if SecurityProfile is used
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should import and use SecurityProfile
        boolean usesSecurityProfile = java.util.Arrays.stream(fields)
            .anyMatch(f -> f.getType().getName().contains("SecurityProfile"));

        assertTrue(usesSecurityProfile, "Should use SecurityProfile");
    }

    @Test
    void testSecurityProfileFactoryImportsRuntimeType() {
        // When: Checking if RuntimeType is used
        var fields = SecurityProfileFactory.class.getDeclaredFields();

        // Then: Should import and use RuntimeType
        boolean usesRuntimeType = java.util.Arrays.stream(fields)
            .anyMatch(f -> f.getType().getName().contains("RuntimeType"));

        assertTrue(usesRuntimeType, "Should use RuntimeType");
    }

    @Test
    void testSecurityProfileFactoryIsInCorrectPackage() {
        // When: Getting package name
        String packageName = SecurityProfileFactory.class.getPackage().getName();

        // Then: Should be in core.security package
        assertTrue(packageName.endsWith(".core.security"),
            "Should be in core.security package");
    }

    @Test
    void testSecurityProfileFactoryHasConstructor() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = SecurityProfileFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Should exist and be public
        assertNotNull(constructor);
        assertTrue(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
    }

    @Test
    void testSecurityProfileFactoryConstructorParameterCount() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = SecurityProfileFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Should have exactly 2 parameters
        assertEquals(2, constructor.getParameterCount());
    }

    @Test
    void testSecurityProfileFactoryConstructorParameterTypes() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = SecurityProfileFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Parameters should be Construct and String
        var paramTypes = constructor.getParameterTypes();
        assertEquals(software.constructs.Construct.class, paramTypes[0]);
        assertEquals(String.class, paramTypes[1]);
    }
}
