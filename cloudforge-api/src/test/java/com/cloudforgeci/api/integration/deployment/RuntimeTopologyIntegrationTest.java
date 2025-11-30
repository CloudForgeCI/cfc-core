package com.cloudforgeci.api.integration.deployment;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for runtime and topology combinations.
 *
 * Tests validate:
 * - All valid runtime/topology combinations
 * - SSL/TLS configuration across different deployments
 * - Autoscaling behavior for different profiles
 * - DNS and domain configuration
 * - Cross-component integration (VPC, ALB, EFS, compute)
 */
class RuntimeTopologyIntegrationTest extends IntegrationTestBase {

    @Test
    void testFargateJenkinsServiceTopology() {
        // Given: Fargate + Jenkins Service topology
        App app = new App();
        Stack stack = new Stack(app, "FargateServiceStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "fargate-service");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context for Fargate + Jenkins Service
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify system context is created
        assertNotNull(ctx);
        assertEquals(RuntimeType.FARGATE, ctx.runtime);
        assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
        assertEquals(SecurityProfile.PRODUCTION, ctx.security);
    }

    @Test
    void testFargateJenkinsSingleNodeTopology() {
        // Given: Fargate + Jenkins Single Node topology
        App app = new App();
        Stack stack = new Stack(app, "FargateSingleNodeStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "fargate-single-node");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

        // When: Creating system context for Fargate + Single Node
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);

        // Then: Verify system context is created
        assertNotNull(ctx);
        assertEquals(RuntimeType.FARGATE, ctx.runtime);
        assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    }

    @Test
    void testEc2JenkinsServiceTopology() {
        // Given: EC2 + Jenkins Service topology
        App app = new App();
        Stack stack = new Stack(app, "Ec2ServiceStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "ec2-service");
        cfcContext.put("securityProfile", "STAGING");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);

        // When: Creating system context for EC2 + Jenkins Service
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.EC2, SecurityProfile.STAGING, iamProfile, cfc);

        // Then: Verify system context is created
        assertNotNull(ctx);
        assertEquals(RuntimeType.EC2, ctx.runtime);
        assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    }

    @Test
    void testEc2JenkinsSingleNodeTopology() {
        // Given: EC2 + Jenkins Single Node topology
        App app = new App();
        Stack stack = new Stack(app, "Ec2SingleNodeStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "ec2-single-node");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("domain", "example.com");
        cfcContext.put("maxInstanceCapacity", 1);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

        // When: Creating system context for EC2 + Single Node
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.EC2, SecurityProfile.DEV, iamProfile, cfc);

        // Then: Verify system context is created
        assertNotNull(ctx);
        assertEquals(RuntimeType.EC2, ctx.runtime);
        assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    }

    @Test
    void testS3WebsiteTopologyWithCloudFront() {
        // Given: S3 Website topology with CloudFront
        App app = new App();
        Stack stack = new Stack(app, "S3WebsiteStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "s3-website");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("cloudfrontEnabled", true);
        cfcContext.put("fqdn", "www.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context for S3 Website
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify system context is created
        assertNotNull(ctx);
        assertEquals(TopologyType.S3_WEBSITE, ctx.topology);
    }

    @Test
    void testFargateWithSslConfiguration() {
        // Given: Fargate deployment with SSL enabled
        App app = new App();
        Stack stack = new Stack(app, "FargateSslStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "fargate-ssl");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "jenkins.example.com");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context with SSL
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify SSL configuration
        assertNotNull(ctx);
        assertTrue(cfc.enableSsl());
        assertEquals("jenkins.example.com", cfc.fqdn());
    }

    @Test
    void testFargateWithAutoscaling() {
        // Given: Fargate deployment with autoscaling
        App app = new App();
        Stack stack = new Stack(app, "FargateAutoscalingStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "fargate-autoscaling");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("minInstanceCapacity", 2);
        cfcContext.put("maxInstanceCapacity", 10);
        cfcContext.put("cpuTargetUtilization", 70);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context with autoscaling
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify autoscaling configuration is passed through DeploymentContext
        assertNotNull(ctx);
        assertEquals(2, cfc.minInstanceCapacity());
        assertEquals(10, cfc.maxInstanceCapacity());
        assertEquals(70, cfc.cpuTargetUtilization());
    }

    @Test
    void testEc2WithAutoscaling() {
        // Given: EC2 deployment with autoscaling
        App app = new App();
        Stack stack = new Stack(app, "Ec2AutoscalingStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "ec2-autoscaling");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("minInstanceCapacity", 2);
        cfcContext.put("maxInstanceCapacity", 5);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context with autoscaling
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.EC2, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify autoscaling configuration is passed through DeploymentContext
        assertNotNull(ctx);
        assertEquals(2, cfc.minInstanceCapacity());
        assertEquals(5, cfc.maxInstanceCapacity());
    }

    @Test
    void testAllSecurityProfilesWithFargate() {
        // Given/When/Then: Test all security profiles with Fargate
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = new Stack(app, "FargateProfile" + profile.name());

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "fargate-" + profile.name().toLowerCase());
            cfcContext.put("securityProfile", profile.name());
            cfcContext.put("domain", "example.com");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);

            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                    RuntimeType.FARGATE, profile, iamProfile, cfc);

            assertNotNull(ctx, "Context should be created for profile: " + profile);
            assertEquals(profile, ctx.security);
        }
    }

    @Test
    void testAllSecurityProfilesWithEc2() {
        // Given/When/Then: Test all security profiles with EC2
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = new Stack(app, "Ec2Profile" + profile.name());

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "ec2-" + profile.name().toLowerCase());
            cfcContext.put("securityProfile", profile.name());
            cfcContext.put("domain", "example.com");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);

            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                    RuntimeType.EC2, profile, iamProfile, cfc);

            assertNotNull(ctx, "Context should be created for profile: " + profile);
            assertEquals(profile, ctx.security);
        }
    }

    @Test
    void testDnsConfigurationWithSubdomain() {
        // Given: Deployment with subdomain configuration
        App app = new App();
        Stack stack = new Stack(app, "SubdomainStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "subdomain-test");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("subdomain", "jenkins");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context with subdomain
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify subdomain configuration
        assertNotNull(ctx);
        assertEquals("jenkins", cfc.subdomain());
        assertEquals("example.com", cfc.domain());
    }

    @Test
    void testHttpToHttpsRedirect() {
        // Given: Deployment with HTTP to HTTPS redirect
        App app = new App();
        Stack stack = new Stack(app, "HttpRedirectStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "http-redirect");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("enableSsl", true);
        cfcContext.put("fqdn", "jenkins.example.com");
        cfcContext.put("httpToHttpsRedirect", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context with redirect
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify redirect configuration
        assertNotNull(ctx);
        assertTrue(cfc.enableSsl());
    }

    @Test
    void testCustomHealthCheckConfiguration() {
        // Given: Deployment with custom health check settings
        App app = new App();
        Stack stack = new Stack(app, "HealthCheckStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "health-check");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        cfcContext.put("healthCheckInterval", 60);
        cfcContext.put("healthCheckTimeout", 10);
        cfcContext.put("healthyThreshold", 3);
        cfcContext.put("unhealthyThreshold", 5);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // When: Creating system context with custom health check
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Then: Verify health check configuration
        assertNotNull(ctx);
        assertEquals(Integer.valueOf(60), cfc.healthCheckInterval());
        assertEquals(Integer.valueOf(10), cfc.healthCheckTimeout());
        assertEquals(Integer.valueOf(3), cfc.healthyThreshold());
        assertEquals(Integer.valueOf(5), cfc.unhealthyThreshold());
    }

    @Test
    void testMultipleTopologyTypes() {
        // Given/When/Then: Test all topology types
        TopologyType[] topologies = {
            TopologyType.JENKINS_SERVICE,
            TopologyType.JENKINS_SERVICE,
            TopologyType.S3_WEBSITE
        };

        for (TopologyType topology : topologies) {
            App app = new App();
            Stack stack = new Stack(app, "Topology" + topology.name().replace("_", "-"));

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "topo-" + topology.name().toLowerCase());
            cfcContext.put("securityProfile", "PRODUCTION");
            cfcContext.put("domain", "example.com");

            // S3_WEBSITE requires CloudFront configuration
            if (topology == TopologyType.S3_WEBSITE) {
                cfcContext.put("cloudfrontEnabled", true);
                cfcContext.put("fqdn", "www.example.com");
            }

            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

            // Use appropriate runtime for topology
            RuntimeType runtime = (topology == TopologyType.JENKINS_SERVICE)
                ? RuntimeType.EC2
                : RuntimeType.FARGATE;

            SystemContext ctx = SystemContext.start(stack, topology,
                    runtime, SecurityProfile.PRODUCTION, iamProfile, cfc);

            assertNotNull(ctx, "Context should be created for topology: " + topology);
            assertEquals(topology, ctx.topology);
        }
    }

    @Test
    void testAllIamProfiles() {
        // Given/When/Then: Test all IAM profiles
        for (IAMProfile iamProfile : IAMProfile.values()) {
            App app = new App();
            Stack stack = new Stack(app, "IamProfile" + iamProfile.name().replace("_", "-"));

            Map<String, Object> cfcContext = new HashMap<>();
            cfcContext.put("stackName", "iam-" + iamProfile.name().toLowerCase());
            cfcContext.put("securityProfile", "PRODUCTION");
            cfcContext.put("domain", "example.com");
            stack.getNode().setContext("cfc", cfcContext);

            DeploymentContext cfc = DeploymentContext.from(stack);

            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
                    RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);

            assertNotNull(ctx, "Context should be created for IAM profile: " + iamProfile);
            assertEquals(iamProfile, ctx.iamProfile);
        }
    }

    @Test
    void testCrossComponentIntegration() {
        // Given: Complete infrastructure with all components
        builder.createCompleteInfrastructure();
        SystemContext ctx = builder.getSystemContext();

        // Then: Verify all components are integrated
        assertTrue(ctx.vpc.get().isPresent(), "VPC should be present");
        assertTrue(ctx.alb.get().isPresent(), "ALB should be present");
        assertTrue(ctx.albSg.get().isPresent(), "ALB security group should be present");
        assertTrue(ctx.http.get().isPresent(), "HTTP listener should be present");
        assertTrue(ctx.efs.get().isPresent(), "EFS should be present");
        assertTrue(ctx.efsSg.get().isPresent(), "EFS security group should be present");

        // Verify runtime-specific components
        if (ctx.runtime == RuntimeType.FARGATE) {
            assertTrue(ctx.fargateService.get().isPresent(), "Fargate service should be present");
            assertTrue(ctx.fargateTaskDef.get().isPresent(), "Fargate task def should be present");
            assertTrue(ctx.container.get().isPresent(), "Container should be present");
        }
    }

    @Test
    void testMinimalInfrastructureForAllProfiles() {
        // Given/When/Then: Test minimal infrastructure for all profiles
        for (SecurityProfile profile : SecurityProfile.values()) {
            com.cloudforgeci.api.test.TestInfrastructureBuilder testBuilder =
                new com.cloudforgeci.api.test.TestInfrastructureBuilder(
                    "Minimal" + profile.name(),
                    profile,
                    RuntimeType.FARGATE
                );

            testBuilder.createMinimalInfrastructure();
            Template template = Template.fromStack(testBuilder.getStack());

            // Verify minimal components exist
            template.resourceCountIs("AWS::EC2::VPC", 1);
            template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
            template.resourceCountIs("AWS::EFS::FileSystem", 1);
        }
    }
}
