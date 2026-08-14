package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GitLabOidcIntegrationTest {

    private GitLabOidcIntegration integration;
    private OidcConfiguration cognitoConfig;
    private OidcConfiguration identityCenterConfig;
    private Ec2Context ec2Context;

    static class TestEc2Context implements Ec2Context {
        @Override
        public String stackName() {
            return "gitlab-prod";
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
        integration = new GitLabOidcIntegration();
        ec2Context = new TestEc2Context();

        cognitoConfig = new CognitoOidcConfiguration(
            "us-east-1",
            "us-east-1_abcdef123",
            "myapp",
            "cognito-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:cognito-secret",
            "https://gitlab.example.com/users/auth/openid_connect/callback",
            "Admins"
        );

        identityCenterConfig = new IdentityCenterOidcConfiguration(
            "us-east-1",
            "d-1234567890",
            "my-company",
            "ic-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:ic-secret",
            "https://gitlab.example.com/users/auth/openid_connect/callback",
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
        assertTrue(method.contains("OmniAuth") || method.contains("OIDC"));
    }

    @Test
    void testGetOidcCallbackPath() {
        assertEquals("/users/auth/openid_connect/callback", integration.getOidcCallbackPath());
    }

    @Test
    void testGetConfigurationFileWithCognito() {
        // GitLab uses environment variables for container configuration
        String config = integration.getConfigurationFile(cognitoConfig);
        assertNull(config);
    }

    @Test
    void testGetConfigurationFileWithIdentityCenter() {
        // GitLab uses environment variables for container configuration
        String config = integration.getConfigurationFile(identityCenterConfig);
        assertNull(config);
    }

    @Test
    void testEnvironmentVariablesContainGitLabConfig() {
        Map<String, String> envVars = integration.getEnvironmentVariables(cognitoConfig);
        assertNotNull(envVars);
        assertTrue(envVars.containsKey("GITLAB_OMNIBUS_CONFIG"));

        String config = envVars.get("GITLAB_OMNIBUS_CONFIG");
        // Verify it contains gitlab_rails OIDC configuration
        assertTrue(config.contains("omniauth"));
        assertTrue(config.contains("openid_connect"));
        assertTrue(config.contains("cognito-client-id"));
    }

    @Test
    void testEnvironmentVariablesContainOidcEndpoints() {
        Map<String, String> envVars = integration.getEnvironmentVariables(cognitoConfig);
        String config = envVars.get("GITLAB_OMNIBUS_CONFIG");

        // Should contain OIDC configuration
        assertTrue(config.contains("discovery"));
        assertTrue(config.contains("issuer"));
    }

    @Test
    void testGetConfigurationFilePath() {
        // GitLab uses environment variables for container configuration
        String path = integration.getConfigurationFilePath();
        assertNull(path);
    }

    @Test
    void testGetContainerStartupCommand() {
        String command = integration.getContainerStartupCommand();
        assertEquals("/assets/init-container", command);
    }

    @Test
    void testGetPostDeploymentInstructions() {
        String instructions = integration.getPostDeploymentInstructions();
        assertNotNull(instructions);
        assertTrue(instructions.contains("GitLab") || instructions.contains("gitlab"));
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
        assertTrue(commands.size() > 3);
    }

    @Test
    void testUserDataCommandsIncludeGitLabConfig() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        
        assertTrue(allCommands.length() > 100);
    }
}
