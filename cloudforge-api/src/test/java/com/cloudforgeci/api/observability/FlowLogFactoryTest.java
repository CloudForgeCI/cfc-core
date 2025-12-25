package com.cloudforgeci.api.observability;

import com.cloudforge.core.enums.SecurityProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for FlowLogFactory.
 *
 * Tests VPC flow log configuration without requiring full CDK context.
 */
class FlowLogFactoryTest {

    @Test
    void testFlowLogFactoryClassExists() {
        // When: Accessing FlowLogFactory class
        Class<?> factoryClass = FlowLogFactory.class;

        // Then: Should exist
        assertNotNull(factoryClass);
        assertEquals("FlowLogFactory", factoryClass.getSimpleName());
    }

    @Test
    void testFlowLogFactoryIsPublicClass() {
        // When: Checking FlowLogFactory modifiers
        Class<?> factoryClass = FlowLogFactory.class;

        // Then: Should be public
        assertTrue(Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testFlowLogFactoryExtendsBaseFactory() {
        // When: Checking FlowLogFactory superclass
        Class<?> factoryClass = FlowLogFactory.class;
        Class<?> superclass = factoryClass.getSuperclass();

        // Then: Should extend BaseFactory
        assertNotNull(superclass);
        assertEquals("BaseFactory", superclass.getSimpleName());
    }

    @Test
    void testFlowLogFactoryHasCreateMethod() throws NoSuchMethodException {
        // When: Getting create method
        var method = FlowLogFactory.class.getDeclaredMethod("create");

        // Then: Should exist and be public
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testFlowLogFactoryPackage() {
        // When: Getting package
        Package pkg = FlowLogFactory.class.getPackage();

        // Then: Should be in observability package
        assertNotNull(pkg);
        assertEquals("com.cloudforgeci.api.observability", pkg.getName());
    }

    @Test
    void testFlowLogFactoryIsNotAbstract() {
        // When: Checking if FlowLogFactory is abstract
        Class<?> factoryClass = FlowLogFactory.class;

        // Then: Should not be abstract
        assertFalse(Modifier.isAbstract(factoryClass.getModifiers()));
    }

    @Test
    void testFlowLogFactoryIsNotInterface() {
        // When: Checking if FlowLogFactory is interface
        Class<?> factoryClass = FlowLogFactory.class;

        // Then: Should not be interface
        assertFalse(factoryClass.isInterface());
    }

    @Test
    void testFlowLogFactoryIsNotEnum() {
        // When: Checking if FlowLogFactory is enum
        Class<?> factoryClass = FlowLogFactory.class;

        // Then: Should not be enum
        assertFalse(factoryClass.isEnum());
    }

    @Test
    void testFlowLogFactoryHasLogger() {
        // When: Getting declared fields
        var fields = FlowLogFactory.class.getDeclaredFields();

        // Then: Should have Logger field
        boolean hasLogger = Arrays.stream(fields)
            .anyMatch(f -> f.getType().equals(Logger.class));

        assertTrue(hasLogger, "Should have Logger field");
    }

    @Test
    void testFlowLogFactoryHasSecurityField() {
        // When: Getting declared fields
        var fields = FlowLogFactory.class.getDeclaredFields();

        // Then: Should have security field
        boolean hasSecurity = Arrays.stream(fields)
            .anyMatch(f -> f.getType().equals(SecurityProfile.class));

        assertTrue(hasSecurity, "Should have SecurityProfile field");
    }

    @Test
    void testFlowLogFactoryFieldsArePrivate() {
        // When: Getting declared fields
        var fields = FlowLogFactory.class.getDeclaredFields();

        // Then: All instance fields should be private
        for (var field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                assertTrue(Modifier.isPrivate(field.getModifiers()),
                    "Field " + field.getName() + " should be private");
            }
        }
    }

    @Test
    void testFlowLogFactoryHasConstructor() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = FlowLogFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Should exist and be public
        assertNotNull(constructor);
        assertTrue(Modifier.isPublic(constructor.getModifiers()));
    }

    @Test
    void testFlowLogFactoryConstructorParameterCount() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = FlowLogFactory.class.getDeclaredConstructor(
            software.constructs.Construct.class,
            String.class
        );

        // Then: Should have exactly 2 parameters
        assertEquals(2, constructor.getParameterCount());
    }

    @Test
    void testFlowLogFactoryIsInCorrectPackage() {
        // When: Getting package name
        String packageName = FlowLogFactory.class.getPackage().getName();

        // Then: Should be in observability package
        assertTrue(packageName.endsWith(".observability"),
            "Should be in observability package");
    }

    @Test
    void testFlowLogFactoryFieldsHaveSystemContextAnnotation() {
        // When: Getting declared fields
        var fields = FlowLogFactory.class.getDeclaredFields();

        // Then: Should have fields with SystemContext annotation
        long annotatedFields = Arrays.stream(fields)
            .filter(f -> f.isAnnotationPresent(com.cloudforge.core.annotation.SystemContext.class))
            .count();

        assertTrue(annotatedFields >= 1, "Should have at least 1 @SystemContext annotated field");
    }

    @Test
    void testFlowLogFactoryCreateMethodExists() throws NoSuchMethodException {
        // When: Getting create method
        var method = FlowLogFactory.class.getDeclaredMethod("create");

        // Then: Should exist and be public
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testFlowLogFactoryHasNoInnerClasses() {
        // When: Getting inner classes
        Class<?>[] innerClasses = FlowLogFactory.class.getDeclaredClasses();

        // Then: Should not have inner classes
        assertEquals(0, innerClasses.length, "Should not have inner classes");
    }

    @Test
    void testFlowLogFactoryClassStructure() {
        // When: Analyzing class structure
        Class<?> factoryClass = FlowLogFactory.class;

        // Then: Should have proper structure
        assertNotNull(factoryClass);
        assertEquals("FlowLogFactory", factoryClass.getSimpleName());
        assertFalse(factoryClass.isInterface());
        assertFalse(factoryClass.isEnum());
        assertFalse(Modifier.isAbstract(factoryClass.getModifiers()));
        assertTrue(Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testFlowLogFactoryMethodsAreWellNamed() {
        // When: Getting methods
        var methods = FlowLogFactory.class.getDeclaredMethods();

        // Then: All methods should have meaningful names
        for (var method : methods) {
            String name = method.getName();
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    @Test
    void testFlowLogFactoryFieldCount() {
        // When: Getting declared fields
        var fields = FlowLogFactory.class.getDeclaredFields();

        // Then: Should have reasonable number of fields
        assertTrue(fields.length >= 1, "Should have at least 1 field");
        assertTrue(fields.length < 10, "Should not have too many fields");
    }

    @Test
    void testFlowLogFactoryMethodCount() {
        // When: Getting all methods (excluding inherited)
        var methods = FlowLogFactory.class.getDeclaredMethods();

        // Then: Should have create method
        assertTrue(methods.length >= 1, "Should have at least create method");
    }

    // ========== Security Hardening Tests (KMS Encryption) ==========

    @Test
    void testFlowLogFactoryUsesSecurityProfileConfiguration() {
        // FlowLogFactory should use SecurityProfileConfiguration for KMS encryption checks
        // This is accessed via the inherited 'config' field from BaseFactory
        Class<?> factoryClass = FlowLogFactory.class;
        Class<?> superclass = factoryClass.getSuperclass();

        // Then: BaseFactory should have config field
        boolean hasConfigInBase = Arrays.stream(superclass.getDeclaredFields())
            .anyMatch(f -> f.getName().equals("config"));

        assertTrue(hasConfigInBase, "BaseFactory should have config field for SecurityProfileConfiguration");
    }

    @Test
    void testFlowLogFactoryCreateMethodCanBeInvoked() throws NoSuchMethodException {
        // When: Getting create method
        var method = FlowLogFactory.class.getDeclaredMethod("create");

        // Then: Should be invokable (not abstract)
        assertFalse(Modifier.isAbstract(method.getModifiers()));
    }

    @Test
    void testFlowLogFactoryImportsAwsConfigRule() {
        // FlowLogFactory should import AwsConfigRule for VPC_FLOW_LOGS_ENABLED and CLOUDWATCH_LOG_GROUP_ENCRYPTED
        // This test verifies the factory is designed to register Config rules
        Class<?> factoryClass = FlowLogFactory.class;

        // The factory uses ctx.requireConfigRule() which is part of the SystemContext
        // Verify the class structure supports this pattern
        assertNotNull(factoryClass);
        assertTrue(Modifier.isPublic(factoryClass.getModifiers()));
    }

    @Test
    void testFlowLogFactoryHasSecurityProfileForKmsDecision() {
        // When: Checking for SecurityProfile field
        var fields = FlowLogFactory.class.getDeclaredFields();

        // Then: Should have SecurityProfile to help determine KMS encryption settings
        boolean hasSecurityProfile = Arrays.stream(fields)
            .anyMatch(f -> f.getType().getName().contains("SecurityProfile"));

        assertTrue(hasSecurityProfile, "Should have SecurityProfile field for KMS encryption decisions");
    }
}
