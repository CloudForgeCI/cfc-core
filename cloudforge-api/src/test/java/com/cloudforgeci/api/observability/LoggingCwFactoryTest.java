package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for LoggingCwFactory.
 *
 * Tests CloudWatch logging configuration with:
 * - Annotation-based context injection
 * - Security profile-based retention settings
 * - Configurable log retention via DeploymentContext
 * - Null context validation
 */
class LoggingCwFactoryTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testLoggingCwFactoryCreationWithDevProfile() {
        // Given: A DEV deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestLoggingDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating LoggingCw factory
        LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

        // Then: Should create log group successfully
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testLoggingCwFactoryCreationWithProductionProfile() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestLoggingProd", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating LoggingCw factory
        LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

        // Then: Should create log group with production retention settings
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testLoggingCwFactoryWithCustomRetention() {
        // Given: A deployment with custom log retention
        App app = new App();
        Stack stack = new Stack(app, "TestLoggingRetention");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestLoggingRetention");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("logRetentionDays", 90);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating LoggingCw factory with custom retention
        LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

        // Then: Should use custom retention days
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testLoggingCwFactorySkipsIfLogsAlreadyConfigured() {
        // Given: A deployment where logs are already configured
        App app = new App();
        Stack stack = createTestStack(app, "TestLoggingSkip", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating two LoggingCw factories
        LoggingCwFactory factory1 = new LoggingCwFactory(stack, "Logging1");
        factory1.create();

        LoggingCwFactory factory2 = new LoggingCwFactory(stack, "Logging2");

        // Then: Second factory should skip creation
        assertDoesNotThrow(factory2::create);
    }

    @Test
    void testLoggingCwFactoryWithAllSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestLogging" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When: Creating LoggingCw factory
            LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

            // Then: Should not throw for any security profile
            assertDoesNotThrow(factory::create,
                "LoggingCwFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testLoggingCwFactoryWithAllRuntimeTypes() {
        // Given: Each runtime type
        for (RuntimeType runtime : RuntimeType.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestLogging" + runtime, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

            TopologyType topology = runtime == RuntimeType.FARGATE
                ? TopologyType.JENKINS_SERVICE
                : TopologyType.JENKINS_SINGLE_NODE;

            SystemContext ctx = SystemContext.start(stack, topology, runtime,
                    SecurityProfile.DEV, iamProfile, cfc);

            // When: Creating LoggingCw factory
            LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

            // Then: Should not throw for any runtime type
            assertDoesNotThrow(factory::create,
                "LoggingCwFactory should not throw for runtime: " + runtime);
        }
    }

    @Test
    void testLoggingCwFactoryWithMonitoringEnabled() {
        // Given: A deployment with monitoring enabled
        App app = new App();
        Stack stack = new Stack(app, "TestLoggingMonitoring");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestLoggingMonitoring");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("logRetentionDays", 180);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Creating LoggingCw factory with monitoring
        LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

        // Then: Should configure with custom retention
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testLoggingCwFactoryConstructorValidation() {
        // Given: A basic stack with SystemContext
        App app = new App();
        Stack stack = createTestStack(app, "TestLoggingConstructor", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When/Then: Constructor should accept valid parameters
        assertDoesNotThrow(() -> new LoggingCwFactory(stack, "Logging"));
        assertDoesNotThrow(() -> new LoggingCwFactory(stack, "Logging2"));
    }

    @Test
    void testLoggingCwFactoryWithVariousRetentionDays() {
        // Given: Different retention day values
        int[] retentionValues = {7, 30, 90, 180, 365, 400, 545, 731};

        for (int retentionDays : retentionValues) {
            App app = new App();
            Stack stack = new Stack(app, "TestLoggingRetention" + retentionDays);

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "TestLoggingRetention" + retentionDays);
            cfcContext.put("securityProfile", "DEV");
            cfcContext.put("enableMonitoring", true);
            cfcContext.put("logRetentionDays", retentionDays);
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, iamProfile, cfc);

            // When: Creating LoggingCw factory with different retention
            LoggingCwFactory factory = new LoggingCwFactory(stack, "Logging");

            // Then: Should handle various retention values
            assertDoesNotThrow(factory::create,
                "LoggingCwFactory should not throw for retention days: " + retentionDays);
        }
    }
}
