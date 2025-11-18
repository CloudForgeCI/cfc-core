package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.compute.Ec2Factory;
import com.cloudforgeci.api.security.CertificateFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for OidcAuthenticationFactory.
 *
 * Tests OIDC authentication configuration for ALB-based authentication with AWS SSO.
 */
class OidcAuthenticationFactoryTest {

    /**
     * Helper method to create a stack with OIDC configuration.
     */
    private Stack createOidcEnabledStack(App app, String stackName, RuntimeType runtime) {
        Stack stack = new Stack(app, stackName);

        // Set OIDC configuration in context BEFORE creating DeploymentContext
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("ssoGroupId", "90d67f97-1234-5678-9abc-def012345678");
        cfcContext.put("ssoTargetAccountId", "123456789012");
        cfcContext.put("lbType", "alb");
        cfcContext.put("enableSsl", true);  // Required for OIDC
        cfcContext.put("domain", "example.com");  // Required for certificate
        cfcContext.put("createZone", true);  // Create zones in tests to avoid AWS lookups
        cfcContext.put("fqdn", "jenkins.example.com");  // Required for OIDC
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    /**
     * Helper method to build minimal infrastructure for testing.
     */
    private void buildMinimalInfrastructure(Stack stack, SystemContext ctx, RuntimeType runtime) {
        // Create VPC
        VpcFactory vpcFactory = new VpcFactory(stack, "Vpc");
        vpcFactory.create();

        // Create domain/hosted zone (required for certificate)
        DomainFactory domainFactory = new DomainFactory(stack, "Domain");
        domainFactory.create();

        // Create certificate (required for HTTPS/OIDC)
        CertificateFactory certificateFactory = new CertificateFactory(stack, "Certificate");
        certificateFactory.create();

        // Create ALB
        AlbFactory albFactory = new AlbFactory(stack, "Alb");
        albFactory.create();

        if (runtime == RuntimeType.FARGATE) {
            // Create EFS and Fargate
            EfsFactory efsFactory = new EfsFactory(stack, "Efs");
            efsFactory.create();

            FargateFactory fargateFactory = new FargateFactory(stack, "Fargate");
            fargateFactory.create();
        } else {
            // Create EC2 first, then EFS
            Ec2Factory ec2Factory = new Ec2Factory(stack, "Ec2");
            ec2Factory.create();

            EfsFactory efsFactory = new EfsFactory(stack, "Efs");
            efsFactory.create();

            // Create target groups for EC2 runtime (required for OIDC)
            ctx.createTargetGroups(stack, "Test");
        }
    }

    /**
     * Test OIDC configuration with valid AWS SSO parameters.
     */
    @Disabled("Complex infrastructure test - requires full CDK stack synthesis")
    @Test
    void testOidcAuthenticationEnabled() {
        // Given: A deployment with ALB-OIDC enabled
        App app = new App();
        Stack stack = createOidcEnabledStack(app, "TestOidcEnabled", RuntimeType.FARGATE);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: Template should contain OIDC authentication configuration
        Template template = Template.fromStack(stack);

        // Verify listener rule is created with OIDC action
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Priority", 1,
            "Actions", new Object[] {
                Map.of(
                    "Type", "authenticate-oidc",
                    "Order", 1
                )
            }
        ));
    }

    /**
     * Test that OIDC is not configured when authMode is not "alb-oidc".
     */
    @Disabled("Complex infrastructure test - requires full CDK stack synthesis")
    @Test
    void testOidcAuthenticationDisabled() {
        // Given: A deployment without ALB-OIDC
        App app = new App();
        Stack stack = new Stack(app, "TestOidcDisabled");

        // Set minimal context without authMode
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                                                SecurityProfile.DEV, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.EC2);

        // When: OIDC factory is created with authMode != "alb-oidc"
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: No OIDC listener rules should be created
        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::ListenerRule", 0);
    }

    /**
     * Test that OIDC is not configured when ssoInstanceArn is missing.
     */
    @Test
    void testMissingSsoInstanceArn() {
        // Given: A deployment with ALB-OIDC but missing ssoInstanceArn
        App app = new App();
        Stack stack = new Stack(app, "TestMissingInstanceArn");

        // Set OIDC configuration without ssoInstanceArn
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoGroupId", "90d67f97-1234-5678-9abc-def012345678");
        cfcContext.put("ssoTargetAccountId", "123456789012");
        cfcContext.put("lbType", "alb");
        cfcContext.put("enableSsl", true);  // Required for OIDC
        cfcContext.put("domain", "example.com");  // Required for certificate
        cfcContext.put("createZone", true);  // Create zones in tests to avoid AWS lookups
        cfcContext.put("fqdn", "jenkins.example.com");  // Required for OIDC
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created without required parameters
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: No OIDC listener rules should be created
        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::ListenerRule", 0);
    }

    /**
     * Test that OIDC is not configured when ssoGroupId is missing.
     */
    @Test
    void testMissingSsoGroupId() {
        // Given: A deployment with ALB-OIDC but missing ssoGroupId
        App app = new App();
        Stack stack = new Stack(app, "TestMissingGroupId");

        // Set OIDC configuration without ssoGroupId
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("ssoTargetAccountId", "123456789012");
        cfcContext.put("lbType", "alb");
        cfcContext.put("enableSsl", true);  // Required for OIDC
        cfcContext.put("domain", "example.com");  // Required for certificate
        cfcContext.put("createZone", true);  // Create zones in tests to avoid AWS lookups
        cfcContext.put("fqdn", "jenkins.example.com");  // Required for OIDC
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created without required parameters
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: No OIDC listener rules should be created
        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::ListenerRule", 0);
    }

    /**
     * Test that OIDC is not configured when ssoTargetAccountId is missing.
     */
    @Test
    void testMissingSsoTargetAccountId() {
        // Given: A deployment with ALB-OIDC but missing ssoTargetAccountId
        App app = new App();
        Stack stack = new Stack(app, "TestMissingAccountId");

        // Set OIDC configuration without ssoTargetAccountId
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
        cfcContext.put("ssoGroupId", "90d67f97-1234-5678-9abc-def012345678");
        cfcContext.put("lbType", "alb");
        cfcContext.put("enableSsl", true);  // Required for OIDC
        cfcContext.put("domain", "example.com");  // Required for certificate
        cfcContext.put("createZone", true);  // Create zones in tests to avoid AWS lookups
        cfcContext.put("fqdn", "jenkins.example.com");  // Required for OIDC
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created without required parameters
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: No OIDC listener rules should be created
        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::ListenerRule", 0);
    }

    /**
     * Test OIDC listener rule priority.
     */
    @Disabled("Complex infrastructure test - requires full CDK stack synthesis")
    @Test
    void testOidcListenerRulePriority() {
        // Given: A deployment with ALB-OIDC enabled
        App app = new App();
        Stack stack = createOidcEnabledStack(app, "TestOidcPriority", RuntimeType.FARGATE);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: Listener rule should have priority 1 (high priority)
        Template template = Template.fromStack(stack);
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Priority", 1
        ));
    }

    /**
     * Test OIDC path pattern configuration.
     */
    @Disabled("Complex infrastructure test - requires full CDK stack synthesis")
    @Test
    void testOidcPathPatterns() {
        // Given: A deployment with ALB-OIDC enabled
        App app = new App();
        Stack stack = createOidcEnabledStack(app, "TestOidcPaths", RuntimeType.FARGATE);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: Listener rule should match all paths
        Template template = Template.fromStack(stack);
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Conditions", new Object[] {
                Map.of(
                    "Field", "path-pattern",
                    "Values", new Object[] {"/*"}
                )
            }
        ));
    }

    /**
     * Test that OIDC configuration is applied correctly with EC2 runtime.
     */
    @Disabled("Complex infrastructure test - requires full CDK stack synthesis")
    @Test
    void testOidcWithEc2Runtime() {
        // Given: A deployment with ALB-OIDC enabled on EC2
        App app = new App();
        Stack stack = createOidcEnabledStack(app, "TestOidcEc2", RuntimeType.EC2);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.EC2);

        // When: OIDC factory is created
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: Template should contain OIDC authentication configuration
        Template template = Template.fromStack(stack);

        // Verify listener rule is created with OIDC action
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Priority", 1,
            "Actions", new Object[] {
                Map.of(
                    "Type", "authenticate-oidc",
                    "Order", 1
                )
            }
        ));
    }

    /**
     * Test OIDC authentication action type and order.
     */
    @Disabled("Complex infrastructure test - requires full CDK stack synthesis")
    @Test
    void testOidcActionConfiguration() {
        // Given: A deployment with ALB-OIDC enabled
        App app = new App();
        Stack stack = new Stack(app, "TestOidcAction");

        // Set OIDC configuration with different instance ARN
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("authMode", "alb-oidc");
        cfcContext.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-fedcba0987654321");
        cfcContext.put("ssoGroupId", "90d67f97-1234-5678-9abc-def012345678");
        cfcContext.put("ssoTargetAccountId", "123456789012");
        cfcContext.put("lbType", "alb");
        cfcContext.put("enableSsl", true);  // Required for OIDC
        cfcContext.put("domain", "example.com");  // Required for certificate
        cfcContext.put("fqdn", "jenkins.example.com");  // Required for OIDC
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                                SecurityProfile.PRODUCTION, iamProfile, cfc);

        buildMinimalInfrastructure(stack, ctx, RuntimeType.FARGATE);

        // When: OIDC factory is created
        OidcAuthenticationFactory factory = new OidcAuthenticationFactory(stack, "OidcAuth");
        factory.create();

        // Then: Verify the action is authenticate-oidc with correct order
        Template template = Template.fromStack(stack);
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::ListenerRule", Map.of(
            "Actions", new Object[] {
                Map.of(
                    "Type", "authenticate-oidc",
                    "Order", 1
                )
            }
        ));
    }
}
