package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to demonstrate and verify the annotation-based context system.
 * Tests both BaseFactory inheritance and standalone ContextInjector usage.
 */
public class ContextInjectionTest {

    @Test
    public void testContextInitialization() {
        // Create a test construct
        App app = new App();
        Stack stack = new Stack(app, "TestStack");

        // Start SystemContext first
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, IAMProfile.MINIMAL, DeploymentContext.from(stack));

        // Create a test factory - contexts are automatically initialized in constructor
        TestFactory factory = new TestFactory(stack, "TestFactory");

        // Verify that contexts were initialized
        assertNotNull(factory.getInjectedSystemContext(), "SystemContext should be initialized");
        assertNotNull(factory.getInjectedDeploymentContext(), "DeploymentContext should be initialized");

        // Verify that the factory can use the contexts
        factory.create();

        // Verify that the factory accessed the contexts correctly
        assertTrue(factory.wasSystemContextUsed(), "SystemContext should have been used");
        assertTrue(factory.wasDeploymentContextUsed(), "DeploymentContext should have been used");
    }

    @Test
    public void testStandaloneContextInjector() {
        // Create a test construct
        App app = new App();
        Stack stack = new Stack(app, "TestStack");

        // Start SystemContext first
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, IAMProfile.MINIMAL, DeploymentContext.from(stack));

        // Create a standalone class that uses ContextInjector directly
        StandaloneClass standalone = new StandaloneClass(stack);

        // Verify that annotation-based fields were injected
        assertNotNull(standalone.getRegion(), "Region should be injected");
        assertNotNull(standalone.getEnvironment(), "Environment should be injected");
        assertNotNull(standalone.getSecurityProfile(), "Security profile should be injected");
        assertNotNull(standalone.getTopology(), "Topology should be injected");

        // Verify values are correct
        assertEquals("us-east-1", standalone.getRegion());
        assertEquals("dev", standalone.getEnvironment()); // Default environment is "dev"
        assertEquals(SecurityProfile.STAGING, standalone.getSecurityProfile());
        assertEquals(TopologyType.JENKINS_SERVICE, standalone.getTopology());
    }

    @Test
    public void testBaseFactoryUsesContextInjector() {
        // Create a test construct
        App app = new App();
        Stack stack = new Stack(app, "TestStack");

        // Start SystemContext first
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, DeploymentContext.from(stack));

        // Create a factory with annotated fields
        AnnotatedTestFactory factory = new AnnotatedTestFactory(stack, "AnnotatedFactory");

        // Verify that annotation-based fields were injected via ContextInjector
        assertNotNull(factory.getRegion(), "Region should be injected");
        assertNotNull(factory.getEnvironment(), "Environment should be injected");
        assertEquals("us-east-1", factory.getRegion());
        assertEquals("dev", factory.getEnvironment()); // Default environment is "dev"
        assertEquals(SecurityProfile.PRODUCTION, factory.getSecurityProfile());
        assertEquals(RuntimeType.FARGATE, factory.getRuntime());
    }

    /**
     * Test factory class that demonstrates annotation-based context injection.
     */
    private static class TestFactory extends BaseFactory {

        private boolean systemContextUsed = false;
        private boolean deploymentContextUsed = false;

        public TestFactory(Construct scope, String id) {
            super(scope, id);
        }

        public void create() {
            // Use the injected SystemContext
            if (ctx != null) {
                systemContextUsed = true;
            }

            // Use the injected DeploymentContext
            if (cfc != null) {
                deploymentContextUsed = true;
            }
        }

        public SystemContext getInjectedSystemContext() {
            return ctx;
        }

        public DeploymentContext getInjectedDeploymentContext() {
            return cfc;
        }

        public boolean wasSystemContextUsed() {
            return systemContextUsed;
        }

        public boolean wasDeploymentContextUsed() {
            return deploymentContextUsed;
        }
    }

    /**
     * Test factory class that uses @DeploymentContext and @SystemContext annotations.
     * This verifies that BaseFactory properly delegates to ContextInjector.
     */
    private static class AnnotatedTestFactory extends BaseFactory {

        @com.cloudforge.core.annotation.DeploymentContext("region")
        private String region;

        @com.cloudforge.core.annotation.DeploymentContext("env")
        private String environment;

        @com.cloudforge.core.annotation.SystemContext("security")
        private SecurityProfile securityProfile;

        @com.cloudforge.core.annotation.SystemContext("runtime")
        private RuntimeType runtime;

        public AnnotatedTestFactory(Construct scope, String id) {
            super(scope, id);
        }

        @Override
        public void create() {
            // Nothing to create in this test
        }

        public String getRegion() {
            return region;
        }

        public String getEnvironment() {
            return environment;
        }

        public SecurityProfile getSecurityProfile() {
            return securityProfile;
        }

        public RuntimeType getRuntime() {
            return runtime;
        }
    }

    /**
     * Standalone class that does NOT extend BaseFactory, but uses ContextInjector directly.
     * This demonstrates the portability of the annotation-based injection.
     */
    private static class StandaloneClass extends Construct {

        @com.cloudforge.core.annotation.DeploymentContext("region")
        private String region;

        @com.cloudforge.core.annotation.DeploymentContext("env")
        private String environment;

        @com.cloudforge.core.annotation.SystemContext("security")
        private SecurityProfile securityProfile;

        @com.cloudforge.core.annotation.SystemContext("topology")
        private TopologyType topology;

        public StandaloneClass(Construct scope) {
            super(scope, "StandaloneClass");
            // Manually invoke ContextInjector - no BaseFactory needed!
            ContextInjector.inject(this, scope);
        }

        public String getRegion() {
            return region;
        }

        public String getEnvironment() {
            return environment;
        }

        public SecurityProfile getSecurityProfile() {
            return securityProfile;
        }

        public TopologyType getTopology() {
            return topology;
        }
    }
}
