package com.cloudforgeci.api.core.annotation;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to demonstrate and verify the annotation-based context injection system.
 */
public class ContextInjectionTest {

    @Test
    public void testContextInjection() {
        // Create a test construct
        App app = new App();
        Stack stack = new Stack(app, "TestStack");
        
        // Start SystemContext first
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, IAMProfile.MINIMAL, DeploymentContext.from(stack));
        
        // Create a test factory that uses annotations
        TestFactory factory = new TestFactory(stack, "TestFactory");
        
        // Manually inject contexts after SystemContext is started
        factory.injectContexts();
        
        // Verify that contexts were injected
        assertNotNull(factory.getInjectedSystemContext(), "SystemContext should be injected");
        assertNotNull(factory.getInjectedDeploymentContext(), "DeploymentContext should be injected");
        
        // Verify that the factory can use the injected contexts
        factory.create();
        
        // Verify that the factory accessed the contexts correctly
        assertTrue(factory.wasSystemContextUsed(), "SystemContext should have been used");
        assertTrue(factory.wasDeploymentContextUsed(), "DeploymentContext should have been used");
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
}
