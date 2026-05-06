package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetabaseSamlIntegration.
 *
 * <p>Tests verify that Metabase SAML 2.0 configuration is correctly
 * generated for both Amazon Cognito and IAM Identity Center providers.</p>
 *
 * <p><strong>Note:</strong> Metabase does not support native OIDC.
 * This integration uses SAML 2.0 which requires Metabase Pro/Enterprise.</p>
 */
class MetabaseSamlIntegrationTest {

    private MetabaseSamlIntegration integration;
    private OidcConfiguration cognitoConfig;
    private OidcConfiguration identityCenterConfig;
    private Ec2Context ec2Context;

    // Mock Ec2Context implementation
    static class TestEc2Context implements Ec2Context {
        @Override
        public String stackName() {
            return "metabase-prod";
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
        integration = new MetabaseSamlIntegration();
        ec2Context = new TestEc2Context();

        cognitoConfig = new CognitoOidcConfiguration(
            "us-east-1",
            "us-east-1_abcdef123",
            "myapp",
            "cognito-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:cognito-saml-cert",
            "https://metabase.example.com/auth/sso",
            "Admins"
        );

        identityCenterConfig = new IdentityCenterOidcConfiguration(
            "us-east-1",
            "d-1234567890",
            "my-company",
            "ic-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:ic-saml-cert",
            "https://metabase.example.com/auth/sso",
            "Administrators"
        );
    }

    @Test
    @DisplayName("Integration should be supported")
    void testIsSupported() {
        assertTrue(integration.isSupported());
    }

    @Test
    @DisplayName("Integration method should describe SAML (not OIDC)")
    void testGetIntegrationMethod() {
        String method = integration.getIntegrationMethod();
        assertNotNull(method);
        assertTrue(method.contains("SAML"));
        assertTrue(method.contains("MB_SAML_"));
        assertTrue(method.contains("does not support native OIDC"));
    }

    @Test
    @DisplayName("Environment variables should enable SAML")
    void testEnableSaml() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("true", env.get("MB_SAML_ENABLED"));
    }

    @Test
    @DisplayName("Cognito provider should use Cognito SAML IdP URI")
    void testCognitoSamlIdpUri() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        String idpUri = env.get("MB_SAML_IDENTITY_PROVIDER_URI");
        assertNotNull(idpUri);
        assertTrue(idpUri.contains("cognito-idp"));
        assertTrue(idpUri.contains("saml2/idp/SSO"));
    }

    @Test
    @DisplayName("Identity Center provider should use Identity Center SAML URI")
    void testIdentityCenterSamlIdpUri() {
        Map<String, String> env = integration.getEnvironmentVariables(identityCenterConfig);
        String idpUri = env.get("MB_SAML_IDENTITY_PROVIDER_URI");
        assertNotNull(idpUri);
        assertTrue(idpUri.contains("saml/SSO"));
    }

    @Test
    @DisplayName("SAML attribute mappings should be configured")
    void testSamlAttributeMappings() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("email", env.get("MB_SAML_ATTRIBUTE_EMAIL"));
        assertEquals("firstName", env.get("MB_SAML_ATTRIBUTE_FIRSTNAME"));
        assertEquals("lastName", env.get("MB_SAML_ATTRIBUTE_LASTNAME"));
    }

    @Test
    @DisplayName("Group sync should be enabled")
    void testGroupSyncEnabled() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("true", env.get("MB_SAML_GROUP_SYNC"));
        assertNotNull(env.get("MB_SAML_ATTRIBUTE_GROUP"));
    }

    @Test
    @DisplayName("Cognito should use custom:groups attribute")
    void testCognitoGroupAttribute() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("custom:groups", env.get("MB_SAML_ATTRIBUTE_GROUP"));
    }

    @Test
    @DisplayName("Identity Center should use groups attribute")
    void testIdentityCenterGroupAttribute() {
        Map<String, String> env = integration.getEnvironmentVariables(identityCenterConfig);
        assertEquals("groups", env.get("MB_SAML_ATTRIBUTE_GROUP"));
    }

    @Test
    @DisplayName("IdP certificate should use placeholder for runtime injection")
    void testIdpCertificatePlaceholder() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("${METABASE_SAML_IDP_CERTIFICATE}", env.get("MB_SAML_IDENTITY_PROVIDER_CERTIFICATE"));
    }

    @Test
    @DisplayName("Logout URI should be configured for SLO")
    void testLogoutUri() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertNotNull(env.get("MB_SAML_IDENTITY_PROVIDER_LOGOUT_URI"));
        // SLO requires SameSite=none for cookies
        assertEquals("none", env.get("MB_SESSION_COOKIE_SAMESITE"));
    }

    @Test
    @DisplayName("Container startup command should return null to use default entrypoint")
    void testGetContainerStartupCommand() {
        String command = integration.getContainerStartupCommand();
        assertNull(command, "Should return null to use default Metabase container entrypoint");
    }

    @Test
    @DisplayName("Post-deployment instructions should contain Metabase-specific SAML guidance")
    void testGetPostDeploymentInstructions() {
        String instructions = integration.getPostDeploymentInstructions();
        assertNotNull(instructions);
        assertTrue(instructions.contains("Metabase"));
        assertTrue(instructions.contains("SAML"));
        assertTrue(instructions.contains("Pro or Enterprise"));
        assertTrue(instructions.contains("/auth/sso"));
    }

    @Test
    @DisplayName("UserData commands should not be empty")
    void testGetUserDataCommandsNotEmpty() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());
    }

    @Test
    @DisplayName("UserData commands should include certificate retrieval")
    void testUserDataCommandsIncludeCertificateRetrieval() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("secretsmanager"));
        assertTrue(allCommands.contains("METABASE_SAML_IDP_CERTIFICATE"));
    }

    @Test
    @DisplayName("UserData commands should include SAML settings")
    void testUserDataCommandsIncludeSamlSettings() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("MB_SAML_ENABLED"));
        assertTrue(allCommands.contains("MB_SAML_IDENTITY_PROVIDER_URI"));
        assertTrue(allCommands.contains("MB_SAML_ATTRIBUTE_EMAIL"));
    }

    @Test
    @DisplayName("UserData commands should create environment file")
    void testUserDataCommandsCreateEnvFile() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("metabase-saml-env.sh"));
    }

    @Test
    @DisplayName("UserData commands should note Pro/Enterprise requirement")
    void testUserDataCommandsNoteEditionRequirement() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("Pro") || allCommands.contains("Enterprise"));
    }

    @Test
    @DisplayName("All required SAML environment variables should be set")
    void testAllRequiredSamlEnvVarsSet() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);

        // List of required Metabase SAML settings
        String[] requiredVars = {
            "MB_SAML_ENABLED",
            "MB_SAML_IDENTITY_PROVIDER_URI",
            "MB_SAML_IDENTITY_PROVIDER_CERTIFICATE",
            "MB_SAML_ATTRIBUTE_EMAIL",
            "MB_SAML_ATTRIBUTE_FIRSTNAME",
            "MB_SAML_ATTRIBUTE_LASTNAME",
            "MB_SAML_GROUP_SYNC",
            "MB_SAML_ATTRIBUTE_GROUP"
        };

        for (String var : requiredVars) {
            assertNotNull(env.get(var), "Missing required variable: " + var);
        }
    }

    @Test
    @DisplayName("UserData commands should handle both Cognito and Identity Center")
    void testUserDataCommandsForBothProviders() {
        List<String> cognitoCommands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        List<String> icCommands = integration.getUserDataCommands(identityCenterConfig, ec2Context);

        String cognitoAll = String.join("\n", cognitoCommands);
        String icAll = String.join("\n", icCommands);

        // Both should have SAML setup
        assertTrue(cognitoAll.contains("MB_SAML_ENABLED"));
        assertTrue(icAll.contains("MB_SAML_ENABLED"));

        // Both should retrieve certificates
        assertTrue(cognitoAll.contains("METABASE_SAML_IDP_CERTIFICATE"));
        assertTrue(icAll.contains("METABASE_SAML_IDP_CERTIFICATE"));
    }
}
