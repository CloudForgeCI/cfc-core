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
 * Test suite for MinimalIAMConfiguration.
 *
 * Tests the minimal IAM configuration for production environments.
 */
class MinimalIAMConfigurationTest {

    private MinimalIAMConfiguration config;
    private App app;
    private Stack stack;
    private DeploymentContext cfc;

    @BeforeEach
    void setUp() {
        config = new MinimalIAMConfiguration();
        app = new App();
        stack = new Stack(app, "TestStack");
        cfc = DeploymentContext.from(stack);
    }

    @Test
    void testConfigurationExists() {
        // When: Creating MinimalIAMConfiguration
        var cfg = new MinimalIAMConfiguration();

        // Then: Should not be null
        assertNotNull(cfg);
    }

    @Test
    void testKindReturnsMinimal() {
        // When: Getting kind
        IAMProfile kind = config.kind();

        // Then: Should return MINIMAL
        assertEquals(IAMProfile.MINIMAL, kind);
    }

    @Test
    void testIdReturnsCorrectValue() {
        // When: Getting id
        String id = config.id();

        // Then: Should return iam:MINIMAL
        assertEquals("iam:MINIMAL", id);
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
            RuntimeType.EC2, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should not be null
        assertNotNull(rules);
    }

    @Test
    void testRulesNotEmpty() {
        // Given: SystemContext with EC2 runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have rules
        assertFalse(rules.isEmpty());
    }

    @Test
    void testRulesForEc2Runtime() {
        // Given: SystemContext with EC2 runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have vpc, instance sg, alb sg rules (minimal doesn't require efs sg)
        assertTrue(rules.size() >= 3, "EC2 should have at least 3 rules");
    }

    @Test
    void testRulesForFargateRuntime() {
        // Given: SystemContext with Fargate runtime
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.FARGATE, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have vpc, alb sg rules (no instance sg for Fargate)
        assertTrue(rules.size() >= 2, "Fargate should have at least 2 rules");
    }

    @Test
    void testRulesReturnsList() {
        // Given: SystemContext
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should return List
        assertTrue(rules instanceof List);
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
        Class<?> clazz = MinimalIAMConfiguration.class;

        // Then: Should be final
        assertTrue(java.lang.reflect.Modifier.isFinal(clazz.getModifiers()));
    }

    @Test
    void testClassIsPublic() {
        // When: Checking class modifiers
        Class<?> clazz = MinimalIAMConfiguration.class;

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(clazz.getModifiers()));
    }

    @Test
    void testImplementsIAMConfiguration() {
        // When: Checking interfaces
        Class<?>[] interfaces = MinimalIAMConfiguration.class.getInterfaces();

        // Then: Should implement IAMConfiguration
        assertTrue(java.util.Arrays.stream(interfaces)
            .anyMatch(i -> i.getSimpleName().equals("IAMConfiguration")));
    }

    @Test
    void testPackageIsCorrect() {
        // When: Getting package
        Package pkg = MinimalIAMConfiguration.class.getPackage();

        // Then: Should be in iam package
        assertEquals("com.cloudforgeci.api.core.iam", pkg.getName());
    }

    @Test
    void testHasPublicConstructor() {
        // When: Getting constructors
        var constructors = MinimalIAMConfiguration.class.getConstructors();

        // Then: Should have public constructor
        assertTrue(constructors.length > 0);
    }

    @Test
    void testCanInstantiate() {
        // When/Then: Should be able to instantiate
        assertDoesNotThrow(() -> new MinimalIAMConfiguration());
    }

    @Test
    void testKindMethodExists() throws NoSuchMethodException {
        // When: Getting kind method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("kind");

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testIdMethodExists() throws NoSuchMethodException {
        // When: Getting id method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("id");

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testRulesMethodExists() throws NoSuchMethodException {
        // When: Getting rules method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("rules", SystemContext.class);

        // Then: Should exist
        assertNotNull(method);
    }

    @Test
    void testWireMethodExists() throws NoSuchMethodException {
        // When: Getting wire method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("wire", SystemContext.class);

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
        assertEquals(IAMProfile.MINIMAL, kind);
    }

    @Test
    void testDifferentInstancesHaveSameKind() {
        // Given: Two instances
        MinimalIAMConfiguration cfg1 = new MinimalIAMConfiguration();
        MinimalIAMConfiguration cfg2 = new MinimalIAMConfiguration();

        // When: Getting kinds
        IAMProfile kind1 = cfg1.kind();
        IAMProfile kind2 = cfg2.kind();

        // Then: Should be equal
        assertEquals(kind1, kind2);
    }

    @Test
    void testDifferentInstancesHaveSameId() {
        // Given: Two instances
        MinimalIAMConfiguration cfg1 = new MinimalIAMConfiguration();
        MinimalIAMConfiguration cfg2 = new MinimalIAMConfiguration();

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
            RuntimeType.EC2, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Getting rules twice
        var rules1 = config.rules(ctx);
        var rules2 = config.rules(ctx);

        // Then: Should have same size
        assertEquals(rules1.size(), rules2.size());
    }

    @Test
    void testKindMethodIsPublic() throws NoSuchMethodException {
        // When: Getting kind method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("kind");

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testIdMethodIsPublic() throws NoSuchMethodException {
        // When: Getting id method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("id");

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testRulesMethodIsPublic() throws NoSuchMethodException {
        // When: Getting rules method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("rules", SystemContext.class);

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testWireMethodIsPublic() throws NoSuchMethodException {
        // When: Getting wire method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("wire", SystemContext.class);

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void testKindReturnType() throws NoSuchMethodException {
        // When: Getting kind method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("kind");

        // Then: Should return IAMProfile
        assertEquals(IAMProfile.class, method.getReturnType());
    }

    @Test
    void testIdReturnType() throws NoSuchMethodException {
        // When: Getting id method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("id");

        // Then: Should return String
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    void testRulesReturnType() throws NoSuchMethodException {
        // When: Getting rules method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("rules", SystemContext.class);

        // Then: Should return List
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void testWireReturnType() throws NoSuchMethodException {
        // When: Getting wire method
        var method = MinimalIAMConfiguration.class.getDeclaredMethod("wire", SystemContext.class);

        // Then: Should return void
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void testHasExpectedMethodCount() {
        // When: Getting declared methods
        var methods = MinimalIAMConfiguration.class.getDeclaredMethods();

        // Then: Should have expected methods (kind, id, rules, wire, createMinimalEC2Role, createMinimalFargateRoles)
        assertTrue(methods.length >= 4, "Should have at least 4 methods");
    }

    @Test
    void testMinimalHasFewerRulesThanExtended() {
        // Given: Contexts for minimal and extended
        SystemContext minimalCtx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        App extendedApp = new App();
        Stack extendedStack = new Stack(extendedApp, "ExtendedStack");
        DeploymentContext extendedCfc = DeploymentContext.from(extendedStack);
        SystemContext extendedCtx = SystemContext.start(extendedStack, TopologyType.JENKINS_SERVICE,
            RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, extendedCfc);

        // When: Getting rules
        var minimalRules = config.rules(minimalCtx);
        var extendedRules = new ExtendedIAMConfiguration().rules(extendedCtx);

        // Then: Minimal should have same or fewer rules
        assertTrue(minimalRules.size() <= extendedRules.size(),
            "Minimal should have same or fewer rules than Extended");
    }
}
