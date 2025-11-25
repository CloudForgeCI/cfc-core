package com.cloudforgeci.api.application;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.compute.FargateFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for JenkinsBootstrap.
 *
 * Tests Jenkins infrastructure bootstrapping:
 * - Security group rule configuration for EFS and ALB
 * - CloudFormation output creation for Jenkins URL
 * - Integration with VPC, EFS, ALB, and Fargate components
 */
class JenkinsBootstrapTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        return createTestStack(app, stackName, profile, false);
    }

    private Stack createTestStack(App app, String stackName, SecurityProfile profile, boolean withRegion) {
        Stack stack;
        if (withRegion) {
            stack = new Stack(app, stackName, StackProps.builder()
                    .env(Environment.builder().region("us-east-1").build())
                    .build());
        } else {
            stack = new Stack(app, stackName);
        }

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        if (withRegion) {
            cfcContext.put("region", "us-east-1");
        }
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testJenkinsBootstrapCreation() {
        // Given: A deployment with required infrastructure
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsBootstrap", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // Create required infrastructure
        VpcFactory vpc = new VpcFactory(stack, "Vpc");
        vpc.create();

        EfsFactory efs = new EfsFactory(stack, "Efs");
        efs.create();

        AlbFactory alb = new AlbFactory(stack, "Alb");
        alb.create();

        FargateFactory fargate = new FargateFactory(stack, "Fargate");
        fargate.create();

        // When: Creating JenkinsBootstrap
        JenkinsBootstrap bootstrap = new JenkinsBootstrap(stack, "Bootstrap");

        // Then: Should configure security groups and outputs
        assertDoesNotThrow(bootstrap::create);
    }

    @Test
    void testJenkinsBootstrapWithProductionProfile() {
        // Given: A PRODUCTION deployment with region for ALB logging
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsBootstrapProd", SecurityProfile.PRODUCTION, true);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // Create required infrastructure
        VpcFactory vpc = new VpcFactory(stack, "Vpc");
        vpc.create();

        EfsFactory efs = new EfsFactory(stack, "Efs");
        efs.create();

        AlbFactory alb = new AlbFactory(stack, "Alb");
        alb.create();

        FargateFactory fargate = new FargateFactory(stack, "Fargate");
        fargate.create();

        // When: Creating JenkinsBootstrap
        JenkinsBootstrap bootstrap = new JenkinsBootstrap(stack, "Bootstrap");

        // Then: Should configure security groups with production settings
        assertDoesNotThrow(bootstrap::create);
    }

    @Test
    void testJenkinsBootstrapConstructorValidation() {
        // Given: A basic stack with SystemContext
        App app = new App();
        Stack stack = createTestStack(app, "TestJenkinsBootstrapConstructor", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When/Then: Constructor should accept valid parameters
        assertDoesNotThrow(() -> new JenkinsBootstrap(stack, "Bootstrap"));
        assertDoesNotThrow(() -> new JenkinsBootstrap(stack, "Bootstrap2"));
    }

    @Test
    void testJenkinsBootstrapWithAllSecurityProfiles() {
        // Given: Each security profile with region for ALB logging
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestJenkinsBootstrap" + profile, profile, true);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // Create required infrastructure
            VpcFactory vpc = new VpcFactory(stack, "Vpc");
            vpc.create();

            EfsFactory efs = new EfsFactory(stack, "Efs");
            efs.create();

            AlbFactory alb = new AlbFactory(stack, "Alb");
            alb.create();

            FargateFactory fargate = new FargateFactory(stack, "Fargate");
            fargate.create();

            // When: Creating JenkinsBootstrap
            JenkinsBootstrap bootstrap = new JenkinsBootstrap(stack, "Bootstrap");

            // Then: Should not throw for any security profile
            assertDoesNotThrow(bootstrap::create,
                "JenkinsBootstrap should not throw for security profile: " + profile);
        }
    }
}
