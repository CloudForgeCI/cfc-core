package com.cloudforgeci.samples.ministack;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.local.CloudFormationTemplateDiff;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.application.JenkinsApplicationSpec;
import com.cloudforgeci.samples.launchers.ApplicationFargateStack;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDK synth parity for MiniStack-relevant features (LB / domain / TLS / Cognito).
 * Does not require a running MiniStack; live API tests live in {@code cloudforge-ministack}.
 */
@Tag("ministack")
class CanonicalTemplateParityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void loadBalancerOnlyTemplateContainsAwsRuntimeWiring() {
        ObjectNode template = synthesize(config());

        assertCount(template, "AWS::EC2::VPC", 1);
        assertCount(template, "AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        assertCount(template, "AWS::ElasticLoadBalancingV2::Listener", 1);
        assertCount(template, "AWS::ECS::Service", 1);
        assertCount(template, "AWS::Route53::HostedZone", 0);
        assertCount(template, "AWS::Cognito::UserPool", 0);
        assertResourceProperty(template, "AWS::ElasticLoadBalancingV2::LoadBalancer",
            "Scheme", "internet-facing");
    }

    @Test
    void domainAndSubdomainProduceRoute53AliasWithoutReplacingLoadBalancer() {
        DeploymentConfig baseConfig = config();
        ObjectNode base = synthesize(baseConfig);

        DeploymentConfig domainConfig = config();
        domainConfig.domain = "example.com";
        domainConfig.createZone = true;
        ObjectNode domain = synthesize(domainConfig);

        DeploymentConfig subdomainConfig = config();
        subdomainConfig.domain = "example.com";
        subdomainConfig.subdomain = "ci";
        subdomainConfig.createZone = true;
        ObjectNode subdomain = synthesize(subdomainConfig);

        assertCount(domain, "AWS::Route53::HostedZone", 1);
        assertTrue(count(domain, "AWS::Route53::RecordSet") >= 1);

        List<CloudFormationTemplateDiff.ResourceChange> addDomain =
            CloudFormationTemplateDiff.diff(base, domain);
        assertTrue(addDomain.stream().anyMatch(change ->
            change.action() == CloudFormationTemplateDiff.Action.ADD
                && "AWS::Route53::HostedZone".equals(change.resourceType())));
        assertFalse(addDomain.stream().anyMatch(change ->
            change.action() == CloudFormationTemplateDiff.Action.REMOVE
                && "AWS::ElasticLoadBalancingV2::LoadBalancer".equals(change.resourceType())));

        List<CloudFormationTemplateDiff.ResourceChange> changeSubdomain =
            CloudFormationTemplateDiff.diff(domain, subdomain);
        assertTrue(changeSubdomain.stream().anyMatch(change ->
            "AWS::Route53::RecordSet".equals(change.resourceType())));
    }

    @Test
    void tlsAndCognitoAuthArePresentInCanonicalAwsTemplateAndRemovable() {
        DeploymentConfig tlsConfig = domainConfig();
        tlsConfig.enableSsl = true;
        ObjectNode tls = synthesize(tlsConfig);

        DeploymentConfig authConfig = domainConfig();
        authConfig.enableSsl = true;
        authConfig.authMode = AuthMode.ALB_OIDC;
        authConfig.oidcProvider = "cognito";
        authConfig.cognitoAutoProvision = true;
        authConfig.cognitoUserPoolName = "cfc-parity-users";
        authConfig.cognitoDomainPrefix = "cfc-parity-users";
        ObjectNode authenticated = synthesize(authConfig);

        assertTrue(count(tls, "AWS::CertificateManager::Certificate") >= 1);
        assertCount(authenticated, "AWS::Cognito::UserPool", 1);
        assertTrue(containsAction(authenticated, "authenticate-cognito"));

        List<CloudFormationTemplateDiff.ResourceChange> addAuth =
            CloudFormationTemplateDiff.diff(tls, authenticated);
        assertTrue(addAuth.stream().anyMatch(change ->
            change.action() == CloudFormationTemplateDiff.Action.ADD
                && "AWS::Cognito::UserPool".equals(change.resourceType())));

        List<CloudFormationTemplateDiff.ResourceChange> removeAuth =
            CloudFormationTemplateDiff.diff(authenticated, tls);
        assertTrue(removeAuth.stream().anyMatch(change ->
            change.action() == CloudFormationTemplateDiff.Action.REMOVE
                && "AWS::Cognito::UserPool".equals(change.resourceType())));
        assertFalse(containsAction(tls, "authenticate-cognito"));
    }

    private static DeploymentConfig domainConfig() {
        DeploymentConfig config = config();
        config.domain = "example.com";
        config.subdomain = "ci";
        config.createZone = true;
        return config;
    }

    private static DeploymentConfig config() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "CanonicalParityStack";
        config.environment = "dev";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.topology = TopologyType.APPLICATION_SERVICE;
        config.securityProfile = SecurityProfile.DEV;
        config.region = "us-east-1";
        config.networkMode = com.cloudforge.core.enums.NetworkMode.PUBLIC;
        config.domain = "";
        config.subdomain = "";
        config.enableSsl = false;
        config.authMode = AuthMode.NONE;
        config.oidcProvider = "none";
        config.wafEnabled = false;
        config.guardDutyEnabled = false;
        config.awsConfigEnabled = false;
        config.createConfigInfrastructure = false;
        config.auditManagerEnabled = false;
        config.cloudfrontEnabled = false;
        config.enableMonitoring = false;
        config.enableEncryption = true;
        config.albAccessLogging = false;
        config.minInstanceCapacity = 1;
        config.maxInstanceCapacity = 2;
        config.cpu = 512;
        config.memory = 1024;
        return config;
    }

    private static ObjectNode synthesize(DeploymentConfig config) {
        App app = new App();
        app.getNode().setContext("cfc", config.toContextMap());
        StackProps props = StackProps.builder()
            .env(Environment.builder().account("000000000000").region("us-east-1").build())
            .build();
        ApplicationFargateStack stack = new ApplicationFargateStack(
            app,
            config.stackName,
            props,
            SecurityProfile.DEV,
            IAMProfile.EXTENDED,
            new JenkinsApplicationSpec()
        );
        return MAPPER.convertValue(Template.fromStack(stack).toJSON(), ObjectNode.class);
    }

    private static void assertCount(ObjectNode template, String type, int expected) {
        assertEquals(expected, count(template, type), "Unexpected count for " + type);
    }

    private static long count(ObjectNode template, String type) {
        return template.path("Resources").findValuesAsText("Type").stream()
            .filter(type::equals)
            .count();
    }

    private static void assertResourceProperty(
            ObjectNode template, String type, String property, String expected) {
        boolean found = template.path("Resources").findParents("Type").stream()
            .filter(resource -> type.equals(resource.path("Type").asText()))
            .anyMatch(resource -> expected.equals(resource.path("Properties").path(property).asText()));
        assertTrue(found, type + " did not have " + property + "=" + expected);
    }

    private static boolean containsAction(ObjectNode template, String actionType) {
        return template.findValuesAsText("Type").stream().anyMatch(actionType::equals);
    }
}
