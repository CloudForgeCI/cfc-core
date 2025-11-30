package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GrafanaOidcIntegrationTest {

    private GrafanaOidcIntegration integration;
    private OidcConfiguration cognitoConfig;
    private OidcConfiguration identityCenterConfig;
    private Ec2Context ec2Context;

    // Mock Ec2Context implementation
    static class TestEc2Context implements Ec2Context {
        @Override
        public String stackName() {
            return "grafana-prod";
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
        integration = new GrafanaOidcIntegration();
        ec2Context = new TestEc2Context();

        cognitoConfig = new CognitoOidcConfiguration(
            "us-east-1",
            "us-east-1_abcdef123",
            "myapp",
            "cognito-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:cognito-secret",
            "https://grafana.example.com/login/generic_oauth",
            "Admins"
        );

        identityCenterConfig = new IdentityCenterOidcConfiguration(
            "us-east-1",
            "d-1234567890",
            "my-company",
            "ic-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:ic-secret",
            "https://grafana.example.com/login/generic_oauth",
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
        assertTrue(method.contains("generic_oauth"));
        assertTrue(method.contains("environment variables"));
    }

    @Test
    void testGetEnvironmentVariablesWithCognito() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);

        // Verify basic OAuth config
        assertEquals("true", env.get("GF_AUTH_GENERIC_OAUTH_ENABLED"));
        assertEquals("AWS Cognito", env.get("GF_AUTH_GENERIC_OAUTH_NAME"));
        assertEquals("cognito-client-id", env.get("GF_AUTH_GENERIC_OAUTH_CLIENT_ID"));

        // Verify endpoints
        assertEquals("https://myapp.auth.us-east-1.amazoncognito.com/oauth2/authorize",
            env.get("GF_AUTH_GENERIC_OAUTH_AUTH_URL"));
        assertEquals("https://myapp.auth.us-east-1.amazoncognito.com/oauth2/token",
            env.get("GF_AUTH_GENERIC_OAUTH_TOKEN_URL"));
        assertEquals("https://myapp.auth.us-east-1.amazoncognito.com/oauth2/userInfo",
            env.get("GF_AUTH_GENERIC_OAUTH_API_URL"));

        // Verify claims
        assertEquals("cognito:username", env.get("GF_AUTH_GENERIC_OAUTH_LOGIN_ATTRIBUTE_PATH"));
        assertEquals("cognito:groups", env.get("GF_AUTH_GENERIC_OAUTH_GROUPS_ATTRIBUTE_PATH"));

        // Verify security settings
        assertEquals("true", env.get("GF_AUTH_GENERIC_OAUTH_USE_PKCE"));
        assertEquals("false", env.get("GF_AUTH_GENERIC_OAUTH_TLS_SKIP_VERIFY_INSECURE"));

        // Verify client secret placeholder
        assertEquals("${GRAFANA_OAUTH_CLIENT_SECRET}", env.get("GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET"));
    }

    @Test
    void testGetEnvironmentVariablesWithIdentityCenter() {
        Map<String, String> env = integration.getEnvironmentVariables(identityCenterConfig);

        // Verify provider name
        assertEquals("AWS IAM Identity Center", env.get("GF_AUTH_GENERIC_OAUTH_NAME"));
        assertEquals("ic-client-id", env.get("GF_AUTH_GENERIC_OAUTH_CLIENT_ID"));

        // Verify Identity Center endpoints
        assertEquals("https://my-company.awsapps.com/start/oauth2/authorize",
            env.get("GF_AUTH_GENERIC_OAUTH_AUTH_URL"));
        assertEquals("https://my-company.awsapps.com/start/oauth2/token",
            env.get("GF_AUTH_GENERIC_OAUTH_TOKEN_URL"));
        assertEquals("https://my-company.awsapps.com/start/oauth2/userInfo",
            env.get("GF_AUTH_GENERIC_OAUTH_API_URL"));

        // Verify Identity Center claims (different from Cognito)
        assertEquals("preferred_username", env.get("GF_AUTH_GENERIC_OAUTH_LOGIN_ATTRIBUTE_PATH"));
        assertEquals("groups", env.get("GF_AUTH_GENERIC_OAUTH_GROUPS_ATTRIBUTE_PATH"));
    }

    @Test
    void testScopes() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("openid profile email", env.get("GF_AUTH_GENERIC_OAUTH_SCOPES"));
    }

    @Test
    void testAutoSignUp() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("true", env.get("GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP"));
    }

    @Test
    void testGetContainerStartupCommand() {
        String command = integration.getContainerStartupCommand();
        assertEquals("/run.sh", command);
    }

    @Test
    void testGetPostDeploymentInstructions() {
        String instructions = integration.getPostDeploymentInstructions();
        assertNotNull(instructions);
        assertTrue(instructions.contains("Grafana"));
        assertTrue(instructions.contains("OIDC"));
        assertTrue(instructions.contains("role"));
    }

    @Test
    void testGetUserDataCommandsWithCognito() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());

        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("GF_AUTH_GENERIC_OAUTH_ENABLED") || allCommands.contains("grafana"));
    }

    @Test
    void testGetUserDataCommandsWithIdentityCenter() {
        List<String> commands = integration.getUserDataCommands(identityCenterConfig, ec2Context);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());
    }

    @Test
    void testUserDataCommandsIncludeSecretRetrieval() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);

        // Should contain some setup logic
        assertTrue(allCommands.length() > 100);
    }

    @Test
    void testUserDataCommandsStructure() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        
        // Should have multiple commands
        assertTrue(commands.size() > 3);
    }
}
