package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.interfaces.Rule;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive behavioral tests for TopologyConfiguration implementations.
 *
 * Tests validate actual business logic, rules enforcement, and configuration behavior
 * rather than just null checks. Each test verifies specific requirements and constraints.
 */
class TopologyConfigurationBehavioralTest {

    // ========== JenkinsServiceTopology Behavioral Tests ==========

    @Test
    void testJenkinsServiceTopologyKindIsCorrect() {
        // Given: Jenkins Service topology configuration
        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting kind
        TopologyType kind = config.kind();

        // Then: Must return exactly JENKINS_SERVICE
        assertNotNull(kind, "Topology kind must not be null");
        assertEquals(TopologyType.JENKINS_SERVICE, kind, "Jenkins Service topology must return JENKINS_SERVICE kind");
        assertNotEquals(TopologyType.S3_WEBSITE, kind, "Jenkins Service topology must not return S3_WEBSITE");
    }

    @Test
    void testJenkinsServiceTopologyIdFormat() {
        // Given: Jenkins Service topology configuration
        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Must match expected format
        assertNotNull(id, "Topology ID must not be null");
        assertEquals("topology:JENKINS_SERVICE", id, "ID must match expected format");
        assertTrue(id.startsWith("topology:"), "ID must start with 'topology:' prefix");
    }

    @Test
    void testJenkinsServiceSupportsEc2Runtime() {
        // Given: Jenkins Service deployment with EC2 runtime
        App app = new App();
        Stack stack = new Stack(app, "TestJenkinsEc2");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestJenkinsEc2");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting rules and checking runtime support
        List<Rule> rules = config.rules(ctx);

        // Then: Must not produce runtime validation errors for EC2
        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                List<String> errors = rule.check(ctx);
                for (String error : errors) {
                    if (error.contains("requires runtime")) {
                        fail("Jenkins Service must support EC2 runtime but got error: " + error);
                    }
                }
            }
        }, "Jenkins Service topology must support EC2 runtime");
    }

    @Test
    void testJenkinsServiceSupportsFargateRuntime() {
        // Given: Jenkins Service deployment with Fargate runtime
        App app = new App();
        Stack stack = new Stack(app, "TestJenkinsFargate");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestJenkinsFargate");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting rules and checking runtime support
        List<Rule> rules = config.rules(ctx);

        // Then: Must not produce runtime validation errors for Fargate
        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                List<String> errors = rule.check(ctx);
                for (String error : errors) {
                    if (error.contains("requires runtime")) {
                        fail("Jenkins Service must support Fargate runtime but got error: " + error);
                    }
                }
            }
        }, "Jenkins Service topology must support Fargate runtime");
    }

    @Test
    void testValidationAlbOidcRequiresSslInTopology() {
        // Given: Jenkins Service with alb-oidc but SSL disabled
        App app = new App();
        Stack stack = new Stack(app, "TestJenkinsOidcNoSsl");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestJenkinsOidcNoSsl");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("enableSsl", false);
        stack.getNode().setContext("cfc", cfcContext);

        // When/Then: DeploymentContext validation should catch this first
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            DeploymentContext.from(stack);
        }, "alb-oidc without SSL must be rejected at DeploymentContext level");

        assertTrue(ex.getMessage().contains("alb-oidc"),
            "Error message must mention alb-oidc");
    }

    @Test
    void testJenkinsServiceWireCompletesWithoutErrors() {
        // Given: Valid Jenkins Service configuration with Fargate
        App app = new App();
        Stack stack = new Stack(app, "TestJenkinsWire");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestJenkinsWire");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When/Then: Wire should complete without errors
        assertDoesNotThrow(() -> config.wire(ctx),
            "Jenkins Service topology wire() must complete without errors for valid configuration");
    }

    @Test
    void testJenkinsServiceRulesAreExecutable() {
        // Given: Jenkins Service deployment
        App app = new App();
        Stack stack = new Stack(app, "TestJenkinsRules");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestJenkinsRules");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();

        // When: Getting and executing rules
        List<Rule> rules = config.rules(ctx);

        // Then: All rules must be executable
        assertNotNull(rules, "Rules list must not be null");
        assertTrue(rules.size() >= 3, "Jenkins Service must have at least 3 validation rules");

        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                rule.check(ctx);
            }
        }, "All Jenkins Service topology rules must be executable");
    }

    // ========== S3WebsiteTopology Behavioral Tests ==========

    @Test
    void testS3WebsiteTopologyKindIsCorrect() {
        // Given: S3 Website topology configuration
        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Getting kind
        TopologyType kind = config.kind();

        // Then: Must return exactly S3_WEBSITE
        assertNotNull(kind, "Topology kind must not be null");
        assertEquals(TopologyType.S3_WEBSITE, kind, "S3 Website topology must return S3_WEBSITE kind");
        assertNotEquals(TopologyType.JENKINS_SERVICE, kind, "S3 Website topology must not return JENKINS_SERVICE");
    }

    @Test
    void testS3WebsiteTopologyIdFormat() {
        // Given: S3 Website topology configuration
        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Must match expected format
        assertNotNull(id, "Topology ID must not be null");
        assertEquals("topology:S3_WEBSITE", id, "ID must match expected format");
        assertTrue(id.startsWith("topology:"), "ID must start with 'topology:' prefix");
    }

    @Test
    void testS3WebsiteRulesAreExecutable() {
        // Given: S3 Website deployment
        App app = new App();
        Stack stack = new Stack(app, "TestS3WebsiteRules");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestS3WebsiteRules");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("topology", "s3-website");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Getting and executing rules
        List<Rule> rules = config.rules(ctx);

        // Then: All rules must be executable
        assertNotNull(rules, "Rules list must not be null");

        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                rule.check(ctx);
            }
        }, "All S3 Website topology rules must be executable");
    }

    @Test
    void testS3WebsiteWireCompletesWithoutErrors() {
        // Given: Valid S3 Website configuration
        App app = new App();
        Stack stack = new Stack(app, "TestS3WebsiteWire");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestS3WebsiteWire");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("topology", "s3-website");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When/Then: Wire should complete without errors
        assertDoesNotThrow(() -> config.wire(ctx),
            "S3 Website topology wire() must complete without errors for valid configuration");
    }

    // ========== ApplicationServiceTopology Behavioral Tests ==========

    @Test
    void testApplicationServiceTopologyKindIsCorrect() {
        // Given: Application Service topology configuration
        ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();

        // When: Getting kind
        TopologyType kind = config.kind();

        // Then: Must return exactly APPLICATION_SERVICE
        assertNotNull(kind, "Topology kind must not be null");
        assertEquals(TopologyType.APPLICATION_SERVICE, kind, "Application Service topology must return APPLICATION_SERVICE kind");
    }

    @Test
    void testApplicationServiceTopologyIdFormat() {
        // Given: Application Service topology configuration
        ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Must match expected format
        assertNotNull(id, "Topology ID must not be null");
        assertEquals("topology:APPLICATION_SERVICE", id, "ID must match expected format");
        assertTrue(id.startsWith("topology:"), "ID must start with 'topology:' prefix");
    }

    @Test
    void testApplicationServiceSupportsEc2Runtime() {
        // Given: Application Service deployment with EC2 runtime
        App app = new App();
        Stack stack = new Stack(app, "TestAppServiceEc2");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppServiceEc2");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("topology", "application-service");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.APPLICATION_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();

        // When: Getting rules and checking runtime support
        List<Rule> rules = config.rules(ctx);

        // Then: Must not produce runtime validation errors for EC2
        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                List<String> errors = rule.check(ctx);
                for (String error : errors) {
                    if (error.contains("requires runtime")) {
                        fail("Application Service must support EC2 runtime but got error: " + error);
                    }
                }
            }
        }, "Application Service topology must support EC2 runtime");
    }

    @Test
    void testApplicationServiceSupportsFargateRuntime() {
        // Given: Application Service deployment with Fargate runtime
        App app = new App();
        Stack stack = new Stack(app, "TestAppServiceFargate");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppServiceFargate");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("topology", "application-service");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.APPLICATION_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();

        // When: Getting rules and checking runtime support
        List<Rule> rules = config.rules(ctx);

        // Then: Must not produce runtime validation errors for Fargate
        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                List<String> errors = rule.check(ctx);
                for (String error : errors) {
                    if (error.contains("requires runtime")) {
                        fail("Application Service must support Fargate runtime but got error: " + error);
                    }
                }
            }
        }, "Application Service topology must support Fargate runtime");
    }

    @Test
    void testApplicationServiceRulesAreExecutable() {
        // Given: Application Service deployment
        App app = new App();
        Stack stack = new Stack(app, "TestAppServiceRules");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppServiceRules");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("topology", "application-service");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.APPLICATION_SERVICE, RuntimeType.EC2,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();

        // When: Getting and executing rules
        List<Rule> rules = config.rules(ctx);

        // Then: All rules must be executable
        assertNotNull(rules, "Rules list must not be null");

        assertDoesNotThrow(() -> {
            for (Rule rule : rules) {
                rule.check(ctx);
            }
        }, "All Application Service topology rules must be executable");
    }

    @Test
    void testApplicationServiceWireCompletesWithoutErrors() {
        // Given: Valid Application Service configuration
        App app = new App();
        Stack stack = new Stack(app, "TestAppServiceWire");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestAppServiceWire");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("topology", "application-service");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.APPLICATION_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), cfc);

        ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();

        // When/Then: Wire should complete without errors
        assertDoesNotThrow(() -> config.wire(ctx),
            "Application Service topology wire() must complete without errors for valid configuration");
    }

    // ========== Cross-Topology Comparison Tests ==========

    @Test
    void testTopologyConfigurationsHaveDistinctKinds() {
        // Given: All topology configurations
        JenkinsServiceTopologyConfiguration jenkinsConfig = new JenkinsServiceTopologyConfiguration();
        S3WebsiteTopologyConfiguration s3Config = new S3WebsiteTopologyConfiguration();
        ApplicationServiceTopologyConfiguration appConfig = new ApplicationServiceTopologyConfiguration();

        // When: Comparing their kinds
        TopologyType jenkinsKind = jenkinsConfig.kind();
        TopologyType s3Kind = s3Config.kind();
        TopologyType appKind = appConfig.kind();

        // Then: Must all be unique
        assertNotEquals(jenkinsKind, s3Kind, "Jenkins Service and S3 Website must have different kinds");
        assertNotEquals(jenkinsKind, appKind, "Jenkins Service and Application Service must have different kinds");
        assertNotEquals(s3Kind, appKind, "S3 Website and Application Service must have different kinds");

        // And: Each must be specific
        assertEquals(TopologyType.JENKINS_SERVICE, jenkinsKind);
        assertEquals(TopologyType.S3_WEBSITE, s3Kind);
        assertEquals(TopologyType.APPLICATION_SERVICE, appKind);
    }

    @Test
    void testTopologyConfigurationsHaveDistinctIds() {
        // Given: All topology configurations
        JenkinsServiceTopologyConfiguration jenkinsConfig = new JenkinsServiceTopologyConfiguration();
        S3WebsiteTopologyConfiguration s3Config = new S3WebsiteTopologyConfiguration();
        ApplicationServiceTopologyConfiguration appConfig = new ApplicationServiceTopologyConfiguration();

        // When: Comparing their IDs
        String jenkinsId = jenkinsConfig.id();
        String s3Id = s3Config.id();
        String appId = appConfig.id();

        // Then: Must all be unique
        assertNotEquals(jenkinsId, s3Id, "Topology IDs must be different");
        assertNotEquals(jenkinsId, appId, "Topology IDs must be different");
        assertNotEquals(s3Id, appId, "Topology IDs must be different");
    }

    @Test
    void testAllTopologiesWorksWithAllSecurityProfiles() {
        // Given: All security profiles
        SecurityProfile[] profiles = {SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION};

        for (SecurityProfile profile : profiles) {
            // Test Jenkins Service
            App app1 = new App();
            Stack stack1 = new Stack(app1, "TestJenkins" + profile);
            Map<String, Object> cfcContext1 = new HashMap<>();
            cfcContext1.put("stackName", "TestJenkins" + profile);
            cfcContext1.put("securityProfile", profile.name());
            cfcContext1.put("domain", "example.com");
            stack1.getNode().setContext("cfc", cfcContext1);

            assertDoesNotThrow(() -> {
                DeploymentContext cfc = DeploymentContext.from(stack1);
                SystemContext ctx = SystemContext.start(stack1, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        profile, IAMProfileMapper.mapFromSecurity(profile), cfc);
                JenkinsServiceTopologyConfiguration config = new JenkinsServiceTopologyConfiguration();
                config.rules(ctx);
                config.wire(ctx);
            }, "Jenkins Service topology must support " + profile + " profile");

            // Test Application Service
            App app2 = new App();
            Stack stack2 = new Stack(app2, "TestAppService" + profile);
            Map<String, Object> cfcContext2 = new HashMap<>();
            cfcContext2.put("stackName", "TestAppService" + profile);
            cfcContext2.put("securityProfile", profile.name());
            cfcContext2.put("topology", "application-service");
            cfcContext2.put("domain", "example.com");
            stack2.getNode().setContext("cfc", cfcContext2);

            assertDoesNotThrow(() -> {
                DeploymentContext cfc = DeploymentContext.from(stack2);
                SystemContext ctx = SystemContext.start(stack2, TopologyType.APPLICATION_SERVICE, RuntimeType.FARGATE,
                        profile, IAMProfileMapper.mapFromSecurity(profile), cfc);
                ApplicationServiceTopologyConfiguration config = new ApplicationServiceTopologyConfiguration();
                config.rules(ctx);
                config.wire(ctx);
            }, "Application Service topology must support " + profile + " profile");
        }
    }
}
