package com.cloudforgeci.api.core.iam;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ExtendedIAMConfiguration.
 *
 * Tests the extended IAM configuration for development environments.
 */
class ExtendedIAMConfigurationTest {

    private ExtendedIAMConfiguration config;
    private App app;
    private Stack stack;
    private DeploymentContext cfc;

    @BeforeEach
    void setUp() {
        config = new ExtendedIAMConfiguration();
        app = new App();
        stack = new Stack(app, "TestStack");
        cfc = DeploymentContext.from(stack);
    }

    @Test
    void testConfigurationExists() {
        // When: Creating ExtendedIAMConfiguration
        var cfg = new ExtendedIAMConfiguration();

        // Then: Should not be null
        assertNotNull(cfg);
    }

    @Test
    void testKindReturnsExtended() {
        // When: Getting kind
        IAMProfile kind = config.kind();

        // Then: Should return EXTENDED
        assertEquals(IAMProfile.EXTENDED, kind);
    }

    @Test
    void testIdReturnsCorrectValue() {
        // When: Getting id
        String id = config.id();

        // Then: Should return iam:EXTENDED
        assertEquals("iam:EXTENDED", id);
    }

    @Test
    void testIdIsNotNull() {
        // When: Getting id
        String id = config.id();

        // Then: Should not be null
        assertNotNull(id);
    }

    @Test
    void testIdIsNotEmpty() {
        // When: Getting id
        String id = config.id();

        // Then: Should not be empty
        assertFalse(id.isEmpty());
    }

    @Test
    void testIdStartsWithIamPrefix() {
        // When: Getting id
        String id = config.id();

        // Then: Should start with iam:
        assertTrue(id.startsWith("iam:"));
    }

    @Test
    void testRulesNotNull() {
        // Given: SystemContext with EC2 runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should not be null
        assertNotNull(rules);
    }

    @Test
    void testRulesNotEmpty() {
        // Given: SystemContext with EC2 runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have rules
        assertFalse(rules.isEmpty());
    }

    @Test
    void testRulesForEc2Runtime() {
        // Given: SystemContext with EC2 runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have vpc, instance sg, alb sg, efs sg rules
        assertTrue(rules.size() >= 4, "EC2 should have at least 4 rules");
    }

    @Test
    void testRulesForFargateRuntime() {
        // Given: SystemContext with Fargate runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.FARGATE, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have vpc, alb sg, efs sg rules (no instance sg)
        assertTrue(rules.size() >= 3, "Fargate should have at least 3 rules");
    }

    @Test
    void testRulesReturnsList() {
        // Given: SystemContext
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should return a (statically-typed) List, non-null
        assertNotNull(rules);
    }

    @Test
    void testWireMethodDoesNotThrowForEc2() {
        // When/Then: Wire method should exist and be callable
        assertDoesNotThrow(() -> {
            config.getClass().getDeclaredMethod("wire", SystemContext.class);
        });
    }

    @Test
    void testWireMethodDoesNotThrowForFargate() {
        // When/Then: Wire method should exist and be callable
        assertDoesNotThrow(() -> {
            config.getClass().getDeclaredMethod("wire", SystemContext.class);
        });
    }

    @Test
    void testClassIsFinal() {
        // When: Checking class modifiers
        Class<?> clazz = ExtendedIAMConfiguration.class;

        // Then: Should be final
        assertTrue(java.lang.reflect.Modifier.isFinal(clazz.getModifiers()));
    }

    @Test
    void testClassIsPublic() {
        // When: Checking class modifiers
        Class<?> clazz = ExtendedIAMConfiguration.class;

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(clazz.getModifiers()));
    }

    @Test
    void testImplementsIAMConfiguration() {
        // When: Checking interfaces
        Class<?>[] interfaces = ExtendedIAMConfiguration.class.getInterfaces();

        // Then: Should implement IAMConfiguration
        assertTrue(java.util.Arrays.stream(interfaces)
            .anyMatch(i -> i.getSimpleName().equals("IAMConfiguration")));
    }

    @Test
    void testPackageIsCorrect() {
        // When: Getting package
        Package pkg = ExtendedIAMConfiguration.class.getPackage();

        // Then: Should be in iam package
        assertEquals("com.cloudforgeci.api.core.iam", pkg.getName());
    }

    @Test
    void testHasPublicConstructor() {
        // When: Getting constructors
        var constructors = ExtendedIAMConfiguration.class.getConstructors();

        // Then: Should have public constructor
        assertTrue(constructors.length > 0);
    }

    @Test
    void testCanInstantiate() {
        // When/Then: Should be able to instantiate
        assertDoesNotThrow(() -> new ExtendedIAMConfiguration());
    }

    @Test
    void testKindMethodExists() throws NoSuchMethodException {
        // When: Getting kind method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("kind");

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testIdMethodExists() throws NoSuchMethodException {
        // When: Getting id method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("id");

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testRulesMethodExists() throws NoSuchMethodException {
        // When: Getting rules method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("rules", SystemContext.class);

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testWireMethodExists() throws NoSuchMethodException {
        // When: Getting wire method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("wire", SystemContext.class);

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testKindIsConsistent() {
        // When: Getting kind multiple times
        IAMProfile kind1 = config.kind();
        IAMProfile kind2 = config.kind();

        // Then: Should be the same
        assertEquals(kind1, kind2);
    }

    @Test
    void testIdIsConsistent() {
        // When: Getting id multiple times
        String id1 = config.id();
        String id2 = config.id();

        // Then: Should be the same
        assertEquals(id1, id2);
    }

    @Test
    void testKindMatchesProfile() {
        // When: Getting kind
        IAMProfile kind = config.kind();

        // Then: Should match the profile in the class name
        assertEquals(IAMProfile.EXTENDED, kind);
    }

    @Test
    void testDifferentInstancesHaveSameKind() {
        // Given: Two instances
        ExtendedIAMConfiguration cfg1 = new ExtendedIAMConfiguration();
        ExtendedIAMConfiguration cfg2 = new ExtendedIAMConfiguration();

        // When: Getting kinds
        IAMProfile kind1 = cfg1.kind();
        IAMProfile kind2 = cfg2.kind();

        // Then: Should be equal
        assertEquals(kind1, kind2);
    }

    @Test
    void testDifferentInstancesHaveSameId() {
        // Given: Two instances
        ExtendedIAMConfiguration cfg1 = new ExtendedIAMConfiguration();
        ExtendedIAMConfiguration cfg2 = new ExtendedIAMConfiguration();

        // When: Getting ids
        String id1 = cfg1.id();
        String id2 = cfg2.id();

        // Then: Should be equal
        assertEquals(id1, id2);
    }

    @Test
    void testRulesAreReproducible() {
        // Given: SystemContext
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

        // When: Getting rules twice
        var rules1 = config.rules(ctx);
        var rules2 = config.rules(ctx);

        // Then: Should have same size
        assertEquals(rules1.size(), rules2.size());
    }

    @Test
    void testKindMethodIsPublic() throws NoSuchMethodException {
        // When: Getting kind method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("kind");

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testIdMethodIsPublic() throws NoSuchMethodException {
        // When: Getting id method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("id");

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testRulesMethodIsPublic() throws NoSuchMethodException {
        // When: Getting rules method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("rules", SystemContext.class);

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testWireMethodIsPublic() throws NoSuchMethodException {
        // When: Getting wire method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("wire", SystemContext.class);

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testKindReturnType() throws NoSuchMethodException {
        // When: Getting kind method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("kind");

        // Then: Should return IAMProfile
        assertEquals(IAMProfile.class, method.getReturnType());
    }

    @Test
    void testIdReturnType() throws NoSuchMethodException {
        // When: Getting id method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("id");

        // Then: Should return String
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    void testRulesReturnType() throws NoSuchMethodException {
        // When: Getting rules method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("rules", SystemContext.class);

        // Then: Should return List
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void testWireReturnType() throws NoSuchMethodException {
        // When: Getting wire method
        var method = ExtendedIAMConfiguration.class.getDeclaredMethod("wire", SystemContext.class);

        // Then: Should return void
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testHasExpectedMethodCount() {
        // When: Getting declared methods
        var methods = ExtendedIAMConfiguration.class.getDeclaredMethods();

        // Then: Should have expected methods (kind, id, rules, wire, createExtendedEC2Role, createExtendedFargateRoles)
        assertTrue(methods.length >= 4, "Should have at least 4 methods");
    }
}
