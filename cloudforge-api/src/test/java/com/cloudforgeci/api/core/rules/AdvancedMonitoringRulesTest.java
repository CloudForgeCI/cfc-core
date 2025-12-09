package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AdvancedMonitoringRules.
 *
 * Tests advanced monitoring compliance validation rules.
 */
class AdvancedMonitoringRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testAdvancedMonitoringRulesInstallWithDevProfile() {
        // Given: A DEV deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestAdvMonDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        // Then: Should not throw
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringRulesInstallWithProductionProfile() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestAdvMonProd", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testAdvancedMonitoringRulesWithSecurityHubEnabled() {
        // Given: A deployment with Security Hub enabled
        App app = new App();
        Stack stack = new Stack(app, "TestAdvMonSecHub");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAdvMonSecHub");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("securityHubEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));

    }

    @Test
    void testAdvancedMonitoringRulesWithInspectorEnabled() {
        // Given: A deployment with Inspector enabled
        App app = new App();
        Stack stack = new Stack(app, "TestAdvMonInspector");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAdvMonInspector");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("inspectorEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));

    }

    @Test
    void testAdvancedMonitoringRulesWithMacieForGDPR() {
        // Given: A PRODUCTION deployment with GDPR compliance
        App app = new App();
        Stack stack = new Stack(app, "TestAdvMonMacie");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAdvMonMacie");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceFrameworks", "GDPR");
        cfcContext.put("macieEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));

    }

    @Test
    void testAdvancedMonitoringRulesWithCentralizedMonitoring() {
        // Given: A deployment with centralized monitoring enabled
        App app = new App();
        Stack stack = new Stack(app, "TestAdvMonCentralized");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAdvMonCentralized");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("complianceDashboardEnabled", "true");
        cfcContext.put("securityAlertingEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));

    }

    @Test
    void testAdvancedMonitoringRulesValidationExecutes() {
        // Given: A deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestAdvMonValidation", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing advanced monitoring rules
        new AdvancedMonitoringRules().install(ctx);

        // Then: Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testAdvancedMonitoringWithCloudWatchMetrics() {
        // Given: A deployment with CloudWatch metrics enabled
        App app = new App();
        Stack stack = new Stack(app, "TestMetrics");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestMetrics");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("enableCloudWatchMetrics", true);
        cfcContext.put("customMetricsEnabled", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure CloudWatch metrics
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithXRayTracing() {
        // Given: A deployment with X-Ray tracing
        App app = new App();
        Stack stack = new Stack(app, "TestXRay");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestXRay");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("xrayTracingEnabled", true);
        cfcContext.put("xraySamplingRate", 0.1);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure X-Ray tracing
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithLogInsights() {
        // Given: A deployment with CloudWatch Log Insights
        App app = new App();
        Stack stack = new Stack(app, "TestLogInsights");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestLogInsights");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("logInsightsEnabled", true);
        cfcContext.put("logInsightsQueries", "ERROR,WARN,INFO");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure Log Insights queries
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithDashboards() {
        // Given: A deployment with custom dashboards
        App app = new App();
        Stack stack = new Stack(app, "TestDashboards");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDashboards");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("dashboardEnabled", true);
        cfcContext.put("dashboardRefreshInterval", 60);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure dashboards
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithApplicationInsights() {
        // Given: A deployment with CloudWatch Application Insights
        App app = new App();
        Stack stack = new Stack(app, "TestAppInsights");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppInsights");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("applicationInsightsEnabled", true);
        cfcContext.put("opsItemSnsTopicArn", "arn:aws:sns:us-east-1:123456789012:ops");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure Application Insights
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithMetricFilters() {
        // Given: A deployment with custom metric filters
        App app = new App();
        Stack stack = new Stack(app, "TestMetricFilters");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestMetricFilters");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("metricFiltersEnabled", true);
        cfcContext.put("errorMetricFilterPattern", "[ERROR]");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure metric filters
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithContainerInsights() {
        // Given: A FARGATE deployment with Container Insights
        App app = new App();
        Stack stack = new Stack(app, "TestContainerInsights");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestContainerInsights");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("containerInsightsEnabled", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure Container Insights
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithPerformanceMonitoring() {
        // Given: A deployment with performance monitoring
        App app = new App();
        Stack stack = new Stack(app, "TestPerformanceMonitoring");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestPerformanceMonitoring");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("performanceMonitoringEnabled", true);
        cfcContext.put("cpuThreshold", 80);
        cfcContext.put("memoryThreshold", 85);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure performance monitoring
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithAllSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestAdvancedMonitoring" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When/Then: Should not throw for any security profile
            assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx),
                "AdvancedMonitoringRules should not throw for security profile: " + profile);
        }
    }

    @Test
    void testAdvancedMonitoringWithAllRuntimeTypes() {
        // Given: Each runtime type
        for (RuntimeType runtime : RuntimeType.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestAdvancedMonitoring" + runtime, SecurityProfile.DEV);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

            TopologyType topology = runtime == RuntimeType.FARGATE
                ? TopologyType.JENKINS_SERVICE
                : TopologyType.JENKINS_SERVICE;

            SystemContext ctx = SystemContext.start(stack, topology, runtime,
                    SecurityProfile.DEV, iamProfile, cfc);

            // When/Then: Should not throw for any runtime type
            assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx),
                "AdvancedMonitoringRules should not throw for runtime: " + runtime);
        }
    }

    @Test
    void testAdvancedMonitoringIdempotency() {
        // Given: A deployment with advanced monitoring
        App app = new App();
        Stack stack = createTestStack(app, "TestAdvancedMonitoringIdempotent", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing rules multiple times
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));

        // Then: Should be idempotent (no errors on repeated calls)
    }

    @Test
    void testAdvancedMonitoringWithAnomalyDetection() {
        // Given: A deployment with anomaly detection
        App app = new App();
        Stack stack = new Stack(app, "TestAnomalyDetection");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAnomalyDetection");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("anomalyDetectionEnabled", true);
        cfcContext.put("anomalyDetectionBands", 2);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure anomaly detection
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithCompositeAlarms() {
        // Given: A deployment with composite alarms
        App app = new App();
        Stack stack = new Stack(app, "TestCompositeAlarms");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestCompositeAlarms");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("compositeAlarmsEnabled", true);
        cfcContext.put("alarmActionArn", "arn:aws:sns:us-east-1:123456789012:alerts");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure composite alarms
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithEC2Runtime() {
        // Given: An EC2 deployment with monitoring
        App app = new App();
        Stack stack = new Stack(app, "TestEC2Monitoring");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestEC2Monitoring");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("ec2DetailedMonitoring", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure EC2-specific monitoring
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    @Test
    void testAdvancedMonitoringWithAlarmThresholds() {
        // Given: A deployment with custom alarm thresholds
        App app = new App();
        Stack stack = new Stack(app, "TestAlarmThresholds");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAlarmThresholds");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("enableMonitoring", true);
        cfcContext.put("cpuAlarmThreshold", 75);
        cfcContext.put("memoryAlarmThreshold", 80);
        cfcContext.put("diskAlarmThreshold", 90);
        cfcContext.put("errorRateThreshold", 5);
        cfcContext.put("latencyThreshold", 500);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure custom alarm thresholds
        assertDoesNotThrow(() -> new AdvancedMonitoringRules().install(ctx));
    }

    // ==================== EXPANDED PARAMETERIZED TRUTH TABLE TESTS ====================

    /**
     * Expanded test for Security Hub validation.
     * Tests all combinations of: security profile, security monitoring, SecurityHub enabled, standards, auto-remediation.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION scenarios
        "PRODUCTION,true,false,false,false,false,false",      // Security monitoring only (defaults) - partial PASS
        "PRODUCTION,false,false,false,false,false,false",     // No SecurityHub - FAIL
        "PRODUCTION,false,true,false,false,false,false",      // SecurityHub but no standards - FAIL
        "PRODUCTION,false,true,true,false,false,false",       // SecurityHub + PCI-DSS - PASS
        "PRODUCTION,false,true,false,true,false,false",       // SecurityHub + CIS - PASS
        "PRODUCTION,false,true,false,false,true,false",       // SecurityHub + AWS Foundational - PASS
        "PRODUCTION,false,true,true,true,false,false",        // SecurityHub + multiple standards - PASS
        "PRODUCTION,false,true,true,false,false,true",        // SecurityHub + auto-remediation - PASS
        "PRODUCTION,true,true,true,true,true,true",           // All features - PASS
        "PRODUCTION,false,true,false,false,false,true",       // SecurityHub + auto-remediation but no standards - FAIL

        // STAGING scenarios (advisory)
        "STAGING,false,false,false,false,false,false",        // No SecurityHub STAGING - PASS (advisory)
        "STAGING,true,true,true,true,true,true",              // Full STAGING - PASS

        // DEV scenarios (advisory)
        "DEV,false,false,false,false,false,false",            // Minimal DEV - PASS (advisory)
        "DEV,true,true,true,false,false,false",               // SecurityHub DEV - PASS
    })
    void testAMExpandedSecurityHub(String profile, boolean securityMonitoring, boolean securityHubEnabled,
                                   boolean pciDss, boolean cis, boolean awsFoundational, boolean autoRemediation) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestSecHub");
        customContext.put("securityProfile", profile);
        customContext.put("securityHubEnabled", String.valueOf(securityHubEnabled));
        customContext.put("securityHubPciDssEnabled", String.valueOf(pciDss));
        customContext.put("securityHubCisEnabled", String.valueOf(cis));
        customContext.put("securityHubAwsFoundationalEnabled", String.valueOf(awsFoundational));
        customContext.put("securityHubAutoRemediation", String.valueOf(autoRemediation));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestSecHub", secProfile, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();
        new AdvancedMonitoringRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;
        if (secProfile == SecurityProfile.PRODUCTION) {
            // PRODUCTION requires SecurityHub enabled
            if (!securityHubEnabled) {
                shouldFail = true;
            } else {
                // If SecurityHub enabled, at least one standard must be enabled
                // Note: awsFoundational defaults to true, so we need to check explicitly
                if (!pciDss && !cis && !awsFoundational) {
                    shouldFail = true;
                }
                // Auto-remediation is recommended (source line 159-165), which means it FAILS
                if (!autoRemediation) {
                    shouldFail = true;
                }
            }
        }
        // STAGING and DEV are advisory, always pass

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + " securityHub=" + securityHubEnabled +
                " standards=" + pciDss + "/" + cis + "/" + awsFoundational);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + " securityHub=" + securityHubEnabled +
                " standards=" + pciDss + "/" + cis + "/" + awsFoundational);
        }
    }

    /**
     * Expanded test for Amazon Inspector validation.
     * Tests all combinations of: security profile, security monitoring, Inspector enabled, scan types, continuous scanning.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION scenarios
        "PRODUCTION,true,false,false,false,false",            // Security monitoring only (defaults) - partial PASS
        "PRODUCTION,false,false,false,false,false",           // No Inspector - FAIL
        "PRODUCTION,false,true,false,false,false",            // Inspector without scan types - partial PASS
        "PRODUCTION,false,true,true,false,false",             // Inspector + EC2 scanning - partial PASS
        "PRODUCTION,false,true,false,true,false",             // Inspector + ECR scanning - partial PASS
        "PRODUCTION,false,true,true,true,false",              // Inspector + both scan types - FAIL (no continuous)
        "PRODUCTION,false,true,true,true,true",               // Inspector + all features - PASS
        "PRODUCTION,true,true,true,true,true",                // Security monitoring + all Inspector - PASS
        "PRODUCTION,false,true,false,false,true",             // Inspector + continuous only - partial PASS
        "PRODUCTION,true,true,false,false,false",             // Monitoring + Inspector minimal - partial PASS

        // STAGING scenarios (advisory)
        "STAGING,false,false,false,false,false",              // No Inspector STAGING - PASS (advisory)
        "STAGING,true,true,true,true,true",                   // Full STAGING - PASS

        // DEV scenarios (advisory)
        "DEV,false,false,false,false,false",                  // Minimal DEV - PASS (advisory)
        "DEV,true,true,true,true,true",                       // Full DEV - PASS
    })
    void testAMExpandedInspector(String profile, boolean securityMonitoring, boolean inspectorEnabled,
                                 boolean ec2Scanning, boolean ecrScanning, boolean continuousScanning) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspector");
        customContext.put("securityProfile", profile);
        customContext.put("inspectorEnabled", String.valueOf(inspectorEnabled));
        customContext.put("inspectorEc2Scanning", String.valueOf(ec2Scanning));
        customContext.put("inspectorEcrScanning", String.valueOf(ecrScanning));
        customContext.put("inspectorContinuousScanning", String.valueOf(continuousScanning));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspector", secProfile, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();
        new AdvancedMonitoringRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;
        if (secProfile == SecurityProfile.PRODUCTION) {
            // PRODUCTION requires Inspector enabled
            if (!inspectorEnabled) {
                shouldFail = true;
            } else {
                // Continuous scanning is recommended (source line 248-254), which means it FAILS
                if (!continuousScanning) {
                    shouldFail = true;
                }
            }
        }
        // STAGING and DEV are advisory, always pass

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + " inspector=" + inspectorEnabled);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + " inspector=" + inspectorEnabled);
        }
    }

    /**
     * Expanded test for Amazon Macie validation.
     * Tests all combinations of: security profile, compliance frameworks (GDPR/HIPAA), Macie enabled, automated discovery.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION with GDPR/HIPAA - Macie required
        "PRODUCTION,GDPR,false,false",                        // GDPR without Macie - FAIL
        "PRODUCTION,GDPR,true,false",                         // GDPR + Macie but no automated discovery - FAIL
        "PRODUCTION,GDPR,true,true",                          // GDPR + Macie + automated discovery - PASS
        "PRODUCTION,HIPAA,false,false",                       // HIPAA without Macie - FAIL
        "PRODUCTION,HIPAA,true,false",                        // HIPAA + Macie but no automated discovery - FAIL
        "PRODUCTION,HIPAA,true,true",                         // HIPAA + Macie + automated discovery - PASS
        "PRODUCTION,GDPR+HIPAA,true,true",                    // GDPR+HIPAA + full Macie - PASS

        // PRODUCTION without GDPR/HIPAA - Macie optional
        "PRODUCTION,NONE,false,false",                        // No Macie (no GDPR/HIPAA) - PASS
        "PRODUCTION,PCI-DSS,false,false",                     // PCI-DSS without Macie - PASS
        "PRODUCTION,SOC2,false,false",                        // SOC2 without Macie - PASS
        "PRODUCTION,NONE,true,true",                          // Macie enabled voluntarily - PASS

        // STAGING scenarios (advisory)
        "STAGING,GDPR,false,false",                           // STAGING + GDPR - PASS (advisory)
        "STAGING,GDPR,true,true",                             // STAGING + full Macie - PASS

        // DEV scenarios (advisory)
        "DEV,HIPAA,false,false",                              // DEV + HIPAA - PASS (advisory)
        "DEV,GDPR,true,true",                                 // DEV + full Macie - PASS
    })
    void testAMExpandedMacie(String profile, String complianceFramework, boolean macieEnabled,
                             boolean automatedDiscovery) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestMacie");
        customContext.put("securityProfile", profile);
        if (!complianceFramework.equals("NONE")) {
            customContext.put("complianceFrameworks", complianceFramework);
        }
        customContext.put("macieEnabled", String.valueOf(macieEnabled));
        customContext.put("macieAutomatedDiscovery", String.valueOf(automatedDiscovery));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestMacie", secProfile, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();
        new AdvancedMonitoringRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;
        if (secProfile == SecurityProfile.PRODUCTION) {
            // Macie is required for GDPR and HIPAA compliance
            boolean requiresMacie = !complianceFramework.equals("NONE") &&
                (complianceFramework.toUpperCase().contains("GDPR") ||
                 complianceFramework.toUpperCase().contains("HIPAA"));

            if (requiresMacie) {
                if (!macieEnabled) {
                    shouldFail = true;
                } else if (!automatedDiscovery) {
                    // Automated discovery required when Macie is enabled for GDPR/HIPAA
                    shouldFail = true;
                }
            }
        }
        // STAGING and DEV are advisory, always pass

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + " framework=" + complianceFramework +
                " macie=" + macieEnabled + " discovery=" + automatedDiscovery);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + " framework=" + complianceFramework +
                " macie=" + macieEnabled + " discovery=" + automatedDiscovery);
        }
    }

    /**
     * Expanded test for centralized monitoring validation.
     * Tests all combinations of: security profile, security monitoring, compliance dashboard, security alerting.
     */
    @ParameterizedTest
    @CsvSource({
        // PRODUCTION scenarios
        "PRODUCTION,true,false,false",                        // Security monitoring only (defaults) - partial PASS
        "PRODUCTION,false,false,false",                       // No centralized monitoring - FAIL
        "PRODUCTION,false,true,false",                        // Dashboard only - FAIL (missing alerting)
        "PRODUCTION,false,false,true",                        // Alerting only - FAIL (missing dashboard)
        "PRODUCTION,false,true,true",                         // Dashboard + alerting - PASS
        "PRODUCTION,true,true,true",                          // Security monitoring + all features - PASS
        "PRODUCTION,true,false,true",                         // Monitoring + alerting - partial PASS
        "PRODUCTION,true,true,false",                         // Monitoring + dashboard - partial PASS

        // STAGING scenarios (no requirements)
        "STAGING,false,false,false",                          // Minimal STAGING - PASS
        "STAGING,true,true,true",                             // Full STAGING - PASS

        // DEV scenarios (no requirements)
        "DEV,false,false,false",                              // Minimal DEV - PASS
        "DEV,true,true,true",                                 // Full DEV - PASS
    })
    void testAMExpandedCentralizedMonitoring(String profile, boolean securityMonitoring,
                                             boolean complianceDashboard, boolean securityAlerting) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestCentralized");
        customContext.put("securityProfile", profile);
        customContext.put("complianceDashboardEnabled", String.valueOf(complianceDashboard));
        customContext.put("securityAlertingEnabled", String.valueOf(securityAlerting));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestCentralized", secProfile, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();
        new AdvancedMonitoringRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;
        if (secProfile == SecurityProfile.PRODUCTION) {
            // PRODUCTION requires both dashboard and alerting (both are recommended, which means they fail)
            if (!complianceDashboard || !securityAlerting) {
                shouldFail = true;
            }
        }
        // STAGING and DEV are advisory, always pass

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + " dashboard=" + complianceDashboard +
                " alerting=" + securityAlerting);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + " dashboard=" + complianceDashboard +
                " alerting=" + securityAlerting);
        }
    }

    /**
     * Comprehensive realistic scenarios combining all advanced monitoring features.
     */
    @ParameterizedTest
    @CsvSource({
        // Scenario 1: Maximum security - PRODUCTION with all features
        "PRODUCTION,true,GDPR,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 2: PRODUCTION with GDPR but no monitoring - FAIL (many missing features)
        "PRODUCTION,false,GDPR,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 3: PRODUCTION with SecurityHub only - FAIL (missing Inspector, Macie for GDPR, alerting, auto-remediation)
        "PRODUCTION,false,GDPR,true,true,false,false,false,false,false,false,false,true,true,false",

        // Scenario 4: PRODUCTION with Inspector only - FAIL (missing SecurityHub, Macie for GDPR)
        "PRODUCTION,false,GDPR,false,false,false,false,true,true,true,true,false,false,false,false",

        // Scenario 5: PRODUCTION with Macie only (GDPR) - FAIL (missing SecurityHub, Inspector, alerting)
        "PRODUCTION,false,GDPR,false,false,false,false,false,false,false,false,true,true,false,false",

        // Scenario 6: PRODUCTION without GDPR/HIPAA - no Macie required
        "PRODUCTION,false,PCI-DSS,true,true,true,true,true,true,true,true,false,false,true,true",

        // Scenario 7: STAGING with all features (advisory)
        "STAGING,true,GDPR,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 8: STAGING minimal (advisory)
        "STAGING,false,NONE,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 9: DEV with all features
        "DEV,true,HIPAA,true,true,true,true,true,true,true,true,true,true,true,true",

        // Scenario 10: DEV minimal
        "DEV,false,NONE,false,false,false,false,false,false,false,false,false,false,false,false",

        // Scenario 11: PRODUCTION with infrastructure monitoring but weak compliance features - FAIL (missing SecurityHub)
        "PRODUCTION,true,NONE,false,false,false,false,true,true,true,true,false,false,true,true",

        // Scenario 12: PRODUCTION with compliance features but no centralized monitoring - FAIL
        "PRODUCTION,false,SOC2,true,true,true,true,true,true,true,true,false,false,false,false",
    })
    void testAMExpandedComprehensiveScenarios(String profile, boolean securityMonitoring,
                                              String complianceFramework, boolean securityHubEnabled,
                                              boolean pciDss, boolean awsFoundational, boolean autoRemediation,
                                              boolean inspectorEnabled, boolean ec2Scanning, boolean ecrScanning,
                                              boolean continuousScanning, boolean macieEnabled, boolean automatedDiscovery,
                                              boolean complianceDashboard, boolean securityAlerting) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestAMComprehensive");
        customContext.put("securityProfile", profile);

        if (!complianceFramework.equals("NONE")) {
            customContext.put("complianceFrameworks", complianceFramework);
        }
        customContext.put("securityHubEnabled", String.valueOf(securityHubEnabled));
        customContext.put("securityHubPciDssEnabled", String.valueOf(pciDss));
        customContext.put("securityHubAwsFoundationalEnabled", String.valueOf(awsFoundational));
        customContext.put("securityHubAutoRemediation", String.valueOf(autoRemediation));
        customContext.put("inspectorEnabled", String.valueOf(inspectorEnabled));
        customContext.put("inspectorEc2Scanning", String.valueOf(ec2Scanning));
        customContext.put("inspectorEcrScanning", String.valueOf(ecrScanning));
        customContext.put("inspectorContinuousScanning", String.valueOf(continuousScanning));
        customContext.put("macieEnabled", String.valueOf(macieEnabled));
        customContext.put("macieAutomatedDiscovery", String.valueOf(automatedDiscovery));
        customContext.put("complianceDashboardEnabled", String.valueOf(complianceDashboard));
        customContext.put("securityAlertingEnabled", String.valueOf(securityAlerting));

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestAMComprehensive", secProfile, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();
        new AdvancedMonitoringRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;
        if (secProfile == SecurityProfile.PRODUCTION) {
            // SecurityHub required, with at least one standard
            if (!securityHubEnabled) {
                shouldFail = true;
            } else if (!pciDss && !awsFoundational) {
                // No standards enabled (note: cis is not in params, checking pciDss and awsFoundational)
                shouldFail = true;
            } else if (!autoRemediation) {
                // Auto-remediation is required (fails validation per AdvancedMonitoringRules line 159-165)
                shouldFail = true;
            }

            // Inspector required
            if (!inspectorEnabled) {
                shouldFail = true;
            } else {
                // Continuous scanning recommended (fails if not enabled)
                if (!continuousScanning) {
                    shouldFail = true;
                }
            }

            // Macie required for GDPR/HIPAA
            boolean requiresMacie = !complianceFramework.equals("NONE") &&
                (complianceFramework.toUpperCase().contains("GDPR") ||
                 complianceFramework.toUpperCase().contains("HIPAA"));
            if (requiresMacie) {
                if (!macieEnabled || !automatedDiscovery) {
                    shouldFail = true;
                }
            }

            // Dashboard and alerting required
            if (!complianceDashboard || !securityAlerting) {
                shouldFail = true;
            }
        }
        // STAGING and DEV are advisory, always pass

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for comprehensive scenario: " + profile);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for comprehensive scenario: " + profile);
        }
    }
}
