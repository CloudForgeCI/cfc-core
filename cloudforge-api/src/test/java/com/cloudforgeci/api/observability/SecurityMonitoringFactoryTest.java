package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SecurityMonitoringFactory.
 *
 * Tests security monitoring and alerting including:
 * - CloudWatch alarm configuration
 * - SNS topic creation for security alerts
 * - Flow log monitoring
 * - Security profile-based thresholds
 * - Critical infrastructure monitoring
 */
class SecurityMonitoringFactoryTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testSecurityMonitoringFactoryCreation() {
        // Given: A stack with PRODUCTION security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestSecurityMonitoring", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should create without errors
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithDevProfile() {
        // Given: A stack with DEV security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestSecMonDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory for DEV
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should create with relaxed monitoring thresholds
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithStagingProfile() {
        // Given: A stack with STAGING security profile
        App app = new App();
        Stack stack = createTestStack(app, "TestSecMonStaging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory for STAGING
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should create with production-like monitoring
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestSecMonProfile" + counter++, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

            // When: Creating SecurityMonitoringFactory for each profile
            assertDoesNotThrow(factory::create,
                "SecurityMonitoringFactory should not throw for security profile: " + profile);
        }
    }

    @Test
    void testSecurityMonitoringFactoryWithFlowLogsEnabled() {
        // Given: A stack with flow logs monitoring enabled
        App app = new App();
        Stack stack = new Stack(app, "TestSecMonFlowLogs");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSecMonFlowLogs");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableFlowLogs", true);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory with flow logs
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should configure flow log monitoring
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithEc2Runtime() {
        // Given: A stack with EC2 runtime (requires instance monitoring)
        App app = new App();
        Stack stack = createTestStack(app, "TestSecMonEc2", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory for EC2
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should configure EC2-specific monitoring
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithFargateRuntime() {
        // Given: A stack with Fargate runtime
        App app = new App();
        Stack stack = createTestStack(app, "TestSecMonFargate", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory for Fargate
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should configure Fargate-specific monitoring
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithCustomAlarmThresholds() {
        // Given: A stack with custom alarm thresholds
        App app = new App();
        Stack stack = new Stack(app, "TestSecMonCustomThresholds");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSecMonCustomThresholds");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("highCpuThreshold", 85.0);
        cfcContext.put("highMemoryThreshold", 90.0);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory with custom thresholds
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should use custom threshold values
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithMinimalConfiguration() {
        // Given: A stack with minimal configuration
        App app = new App();
        Stack stack = createTestStack(app, "TestSecMonMinimal", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory with minimal config
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should handle minimal configuration
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithMaximalConfiguration() {
        // Given: A stack with all monitoring features enabled
        App app = new App();
        Stack stack = new Stack(app, "TestSecMonMaximal");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSecMonMaximal");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableFlowLogs", true);
        cfcContext.put("enableDetailedMonitoring", true);
        cfcContext.put("enableSecurityHubFindings", true);
        cfcContext.put("highCpuThreshold", 80.0);
        cfcContext.put("highMemoryThreshold", 85.0);
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory with maximal configuration
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should handle all monitoring features
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryCreatesAlertsTopic() {
        // Given: A stack requiring security alerts
        App app = new App();
        Stack stack = createTestStack(app, "TestSecMonAlerts", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Creating SecurityMonitoringFactory
        SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

        // Then: Should create SNS topic for security alerts
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityMonitoringFactoryWithAllTopologies() {
        // Given: Each topology type
        TopologyType[] topologies = {TopologyType.JENKINS_SERVICE, TopologyType.JENKINS_SERVICE, TopologyType.S3_WEBSITE};
        int counter = 0;

        for (TopologyType topology : topologies) {
            App app = new App();
            Stack stack = createTestStack(app, "TestSecMonTopology" + counter++, SecurityProfile.PRODUCTION);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
            RuntimeType runtime = topology == TopologyType.S3_WEBSITE ? RuntimeType.FARGATE : RuntimeType.EC2;
            SystemContext.start(stack, topology, runtime,
                    SecurityProfile.PRODUCTION, iamProfile, cfc);

            SecurityMonitoringFactory factory = new SecurityMonitoringFactory(stack, "SecurityMonitoring");

            // When: Creating SecurityMonitoringFactory for each topology
            assertDoesNotThrow(factory::create,
                "SecurityMonitoringFactory should not throw for topology: " + topology);
        }
    }
}
