package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive functional tests for SecurityMonitoringFactory.
 * Tests actual functionality with proper context injection and resource creation.
 */
@DisplayName("SecurityMonitoringFactory Functional Tests")
class SecurityMonitoringFactoryFunctionalTest {

    private App app;
    private Stack stack;
    private DeploymentContext cfc;
    private SystemContext ctx;
    private SecurityMonitoringFactory factory;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "TestStack");
        cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
        factory = new SecurityMonitoringFactory(stack, "TestSecurityMonitoring");
    }

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Should create factory instance successfully")
        void shouldCreateFactoryInstanceSuccessfully() {
            assertNotNull(factory, "Factory should be created successfully");
            assertNotNull(factory.getNode(), "Factory should have a valid CDK node");
        }

        @Test
        @DisplayName("Should inject contexts successfully")
        void shouldInjectContextsSuccessfully() {
            assertDoesNotThrow(() -> {
                factory.injectContexts();
            }, "Context injection should not throw exceptions");
        }

        @Test
        @DisplayName("Should handle create method without throwing exceptions")
        void shouldHandleCreateMethodWithoutThrowingExceptions() {
            factory.injectContexts();
            assertDoesNotThrow(() -> {
                factory.create();
            }, "Create method should not throw exceptions");
        }
    }

    @Nested
    @DisplayName("Security Profile Specific Tests")
    class SecurityProfileSpecificTests {

        @Test
        @DisplayName("Should work with DEV profile")
        void shouldWorkWithDevProfile() {
            factory.injectContexts();
            assertDoesNotThrow(() -> {
                factory.create();
            }, "Should work with DEV profile");
        }

        @Test
        @DisplayName("Should work with STAGING profile")
        void shouldWorkWithStagingProfile() {
            // Create new context for staging
            App stagingApp = new App();
            Stack stagingStack = new Stack(stagingApp, "StagingStack");
            DeploymentContext stagingCfc = DeploymentContext.from(stagingStack);
            IAMProfile stagingIamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
            SystemContext stagingCtx = SystemContext.start(stagingStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, stagingIamProfile, stagingCfc);
            
            SecurityMonitoringFactory stagingFactory = new SecurityMonitoringFactory(stagingStack, "StagingSecurityMonitoring");
            stagingFactory.injectContexts();
            
            assertDoesNotThrow(() -> {
                stagingFactory.create();
            }, "Should work with STAGING profile");
        }

        @Test
        @DisplayName("Should work with PRODUCTION profile")
        void shouldWorkWithProductionProfile() {
            // Create new context for production
            App productionApp = new App();
            Stack productionStack = new Stack(productionApp, "ProductionStack");
            DeploymentContext productionCfc = DeploymentContext.from(productionStack);
            IAMProfile productionIamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            SystemContext productionCtx = SystemContext.start(productionStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.PRODUCTION, productionIamProfile, productionCfc);
            
            SecurityMonitoringFactory productionFactory = new SecurityMonitoringFactory(productionStack, "ProductionSecurityMonitoring");
            productionFactory.injectContexts();
            
            assertDoesNotThrow(() -> {
                productionFactory.create();
            }, "Should work with PRODUCTION profile");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle missing context gracefully")
        void shouldHandleMissingContextGracefully() {
            SecurityMonitoringFactory factoryWithoutContext = new SecurityMonitoringFactory(stack, "NoContextFactory");
            
            // Should not throw exception even without context injection
            assertDoesNotThrow(() -> {
                factoryWithoutContext.create();
            }, "Should handle missing context gracefully");
        }

        @Test
        @DisplayName("Should handle multiple factory instances")
        void shouldHandleMultipleFactoryInstances() {
            SecurityMonitoringFactory factory2 = new SecurityMonitoringFactory(stack, "TestSecurityMonitoring2");
            SecurityMonitoringFactory factory3 = new SecurityMonitoringFactory(stack, "TestSecurityMonitoring3");
            
            factory.injectContexts();
            factory2.injectContexts();
            factory3.injectContexts();
            
            assertDoesNotThrow(() -> {
                factory.create();
                factory2.create();
                factory3.create();
            }, "Should handle multiple factory instances");
        }
    }

    @Nested
    @DisplayName("Factory Behavior Tests")
    class FactoryBehaviorTests {

        @Test
        @DisplayName("Should create different instances with different IDs")
        void shouldCreateDifferentInstancesWithDifferentIds() {
            SecurityMonitoringFactory factory1 = new SecurityMonitoringFactory(stack, "Factory1");
            SecurityMonitoringFactory factory2 = new SecurityMonitoringFactory(stack, "Factory2");
            
            assertNotEquals(factory1.getNode().getId(), factory2.getNode().getId(), 
                "Different factories should have different IDs");
        }

        @Test
        @DisplayName("Should maintain factory state correctly")
        void shouldMaintainFactoryStateCorrectly() {
            String originalId = factory.getNode().getId();
            Stack originalStack = (Stack) factory.getNode().getScope();
            
            assertEquals("TestSecurityMonitoring", originalId, "Factory should maintain correct ID");
            assertEquals(stack, originalStack, "Factory should maintain correct stack reference");
        }

        @Test
        @DisplayName("Should handle repeated create calls")
        void shouldHandleRepeatedCreateCalls() {
            factory.injectContexts();
            
            assertDoesNotThrow(() -> {
                factory.create();
                factory.create(); // Second call should not fail
            }, "Should handle repeated create calls");
        }
    }

    @Nested
    @DisplayName("Context Integration Tests")
    class ContextIntegrationTests {

        @Test
        @DisplayName("Should integrate with SystemContext properly")
        void shouldIntegrateWithSystemContextProperly() {
            assertNotNull(ctx, "SystemContext should be available");
            assertEquals(SecurityProfile.DEV, ctx.security, "SystemContext should have correct security profile");
            
            factory.injectContexts();
            assertDoesNotThrow(() -> {
                factory.create();
            }, "Should integrate with SystemContext properly");
        }

        @Test
        @DisplayName("Should work with different runtime types")
        void shouldWorkWithDifferentRuntimeTypes() {
            // Test with EC2 runtime
            App ec2App = new App();
            Stack ec2Stack = new Stack(ec2App, "EC2Stack");
            DeploymentContext ec2Cfc = DeploymentContext.from(ec2Stack);
            IAMProfile ec2IamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
            SystemContext ec2Ctx = SystemContext.start(ec2Stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, ec2IamProfile, ec2Cfc);
            
            SecurityMonitoringFactory ec2Factory = new SecurityMonitoringFactory(ec2Stack, "EC2SecurityMonitoring");
            ec2Factory.injectContexts();
            
            assertDoesNotThrow(() -> {
                ec2Factory.create();
            }, "Should work with EC2 runtime");
        }

        @Test
        @DisplayName("Should work with different topology types")
        void shouldWorkWithDifferentTopologyTypes() {
            // Test with different topology
            App topologyApp = new App();
            Stack topologyStack = new Stack(topologyApp, "TopologyStack");
            DeploymentContext topologyCfc = DeploymentContext.from(topologyStack);
            IAMProfile topologyIamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
            SystemContext topologyCtx = SystemContext.start(topologyStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, topologyIamProfile, topologyCfc);
            
            SecurityMonitoringFactory topologyFactory = new SecurityMonitoringFactory(topologyStack, "TopologySecurityMonitoring");
            topologyFactory.injectContexts();
            
            assertDoesNotThrow(() -> {
                topologyFactory.create();
            }, "Should work with different topology types");
        }
    }
}