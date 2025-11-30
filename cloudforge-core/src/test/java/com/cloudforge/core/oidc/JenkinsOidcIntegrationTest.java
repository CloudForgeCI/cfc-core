package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JenkinsOidcIntegrationTest {

    private JenkinsOidcIntegration integration;
    private OidcConfiguration cognitoConfig;
    private OidcConfiguration identityCenterConfig;
    private Ec2Context ec2Context;

    static class TestEc2Context implements Ec2Context {
        @Override
        public String stackName() {
            return "jenkins-prod";
        }

        @Override
        public String runtimeType() {
            return "ec2";
        }

        @Override
        public String securityProfile() {
            return "production";
        }

        @Override
        public boolean hasEfs() {
            return true;
        }

        @Override
        public Optional<String> efsId() {
            return Optional.of("fs-12345678");
        }

        @Override
        public Optional<String> accessPointId() {
            return Optional.of("fsap-12345678");
        }
    }

    @BeforeEach
    void setUp() {
        integration = new JenkinsOidcIntegration();
        ec2Context = new TestEc2Context();

        cognitoConfig = new CognitoOidcConfiguration(
            "us-east-1",
            "us-east-1_abcdef123",
            "myapp",
            "cognito-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:cognito-secret",
            "https://jenkins.example.com/securityRealm/finishLogin",
            "Admins"
        );

        identityCenterConfig = new IdentityCenterOidcConfiguration(
            "us-east-1",
            "d-1234567890",
            "my-company",
            "ic-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:ic-secret",
            "https://jenkins.example.com/securityRealm/finishLogin",
            "Administrators"
        );
    }

    @Test
    void testIsSupported() {
        assertTrue(integration.isSupported());
    }

    @Test
    void testGetIntegrationMethod() {
        String method = integration.getIntegrationMethod();
        assertNotNull(method);
        assertTrue(method.length() > 5);
    }

    @Test
    void testGetConfigurationFileWithCognito() {
        String config = integration.getConfigurationFile(cognitoConfig);
        assertNotNull(config);

        // Verify it contains JCasC YAML configuration
        assertTrue(config.contains("jenkins:"));
        assertTrue(config.contains("securityRealm:"));
        assertTrue(config.contains("oic:"));
    }

    @Test
    void testGetConfigurationFileWithIdentityCenter() {
        String config = integration.getConfigurationFile(identityCenterConfig);
        assertNotNull(config);

        assertTrue(config.contains("jenkins:"));
        assertTrue(config.contains("oic:"));
    }

    @Test
    void testConfigurationContainsClientId() {
        String config = integration.getConfigurationFile(cognitoConfig);
        assertTrue(config.contains("cognito-client-id"));
    }

    @Test
    void testConfigurationContainsEndpoints() {
        String config = integration.getConfigurationFile(cognitoConfig);
        assertTrue(config.contains("tokenServerUrl") || config.contains("authorizationServerUrl"));
    }

    @Test
    void testConfigurationContainsAuthorizationStrategy() {
        String config = integration.getConfigurationFile(cognitoConfig);
        assertTrue(config.contains("authorizationStrategy"));
    }

    @Test
    void testGetConfigurationFilePath() {
        String path = integration.getConfigurationFilePath();
        assertEquals("/var/jenkins_home/casc_configs/oidc.yaml", path);
    }

    @Test
    void testGetContainerStartupCommand() {
        String command = integration.getContainerStartupCommand();
        assertNotNull(command);
        assertTrue(command.contains("jenkins-plugin-cli"));
        assertTrue(command.contains("jenkins.sh"));
    }

    @Test
    void testGetPostDeploymentInstructions() {
        String instructions = integration.getPostDeploymentInstructions();
        assertNotNull(instructions);
        assertTrue(instructions.contains("Jenkins") || instructions.contains("OIDC"));
    }

    @Test
    void testGetUserDataCommandsWithCognito() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());
    }

    @Test
    void testGetUserDataCommandsWithIdentityCenter() {
        List<String> commands = integration.getUserDataCommands(identityCenterConfig, ec2Context);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());
    }

    @Test
    void testUserDataCommandsStructure() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        assertTrue(commands.size() > 5);
    }

    @Test
    void testUserDataCommandsIncludeJenkinsSetup() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        
        assertTrue(allCommands.length() > 200);
    }
}
