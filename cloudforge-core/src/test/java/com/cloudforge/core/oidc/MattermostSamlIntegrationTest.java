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
 * Unit tests for MattermostSamlIntegration.
 *
 * <p>Tests verify that Mattermost SAML 2.0 configuration is correctly
 * generated for both Amazon Cognito and IAM Identity Center providers.</p>
 *
 * <p><strong>Why SAML over OIDC:</strong> Mattermost's OIDC implementation
 * does not support group sync. SAML + AD/LDAP integration enables automatic
 * team/channel membership management via group synchronization.</p>
 */
class MattermostSamlIntegrationTest {

    private MattermostSamlIntegration integration;
    private OidcConfiguration cognitoConfig;
    private OidcConfiguration identityCenterConfig;
    private Ec2Context ec2Context;

    // Mock Ec2Context implementation
    static class TestEc2Context implements Ec2Context {
        @Override
        public String stackName() {
            return "mattermost-prod";
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
        integration = new MattermostSamlIntegration();
        ec2Context = new TestEc2Context();

        // Use a custom config that returns "cognito-saml" as provider type for SAML testing
        cognitoConfig = new OidcConfiguration() {
            @Override
            public String getProviderType() { return "cognito-saml"; }
            @Override
            public String getIssuerUrl() { return "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abcdef123"; }
            @Override
            public String getAuthorizationEndpoint() { return getIssuerUrl() + "/oauth2/authorize"; }
            @Override
            public String getTokenEndpoint() { return getIssuerUrl() + "/oauth2/token"; }
            @Override
            public String getUserInfoEndpoint() { return getIssuerUrl() + "/oauth2/userInfo"; }
            @Override
            public String getJwksUri() { return getIssuerUrl() + "/.well-known/jwks.json"; }
            @Override
            public String getClientId() { return "cognito-client-id"; }
            @Override
            public String getClientSecretArn() { return "arn:aws:secretsmanager:us-east-1:123456789012:secret:cognito-saml-cert"; }
            @Override
            public String getRedirectUrl() { return "https://mattermost.example.com/login/sso/saml"; }
            @Override
            public String getScopes() { return "openid profile email"; }
            @Override
            public String getUsernameClaim() { return "preferred_username"; }
            @Override
            public String getAdminGroupName() { return "Admins"; }
            @Override
            public String getApplicationUrl() { return "https://mattermost.example.com"; }
        };

        identityCenterConfig = new IdentityCenterOidcConfiguration(
            "us-east-1",
            "d-1234567890",
            "my-company",
            "ic-client-id",
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:ic-saml-cert",
            "https://mattermost.example.com/login/sso/saml",
            "Administrators"
        );
    }

    @Test
    @DisplayName("Integration should be supported")
    void testIsSupported() {
        assertTrue(integration.isSupported());
    }

    @Test
    @DisplayName("Integration method should describe SAML with group sync")
    void testGetIntegrationMethod() {
        String method = integration.getIntegrationMethod();
        assertNotNull(method);
        assertTrue(method.contains("SAML"));
        assertTrue(method.contains("MM_SAMLSETTINGS_"));
        assertTrue(method.contains("group sync"));
    }

    @Test
    @DisplayName("Environment variables should enable SAML")
    void testEnableSaml() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("true", env.get("MM_SAMLSETTINGS_ENABLE"));
    }

    @Test
    @DisplayName("Cognito provider should use Cognito SAML IdP URL")
    void testCognitoSamlIdpUrl() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        String idpUrl = env.get("MM_SAMLSETTINGS_IDPURL");
        assertNotNull(idpUrl);
        assertTrue(idpUrl.contains("cognito-idp"));
        assertTrue(idpUrl.contains("saml2/idp/SSO"));
    }

    @Test
    @DisplayName("Identity Center provider should use Identity Center SAML URL")
    void testIdentityCenterSamlIdpUrl() {
        Map<String, String> env = integration.getEnvironmentVariables(identityCenterConfig);
        String idpUrl = env.get("MM_SAMLSETTINGS_IDPURL");
        assertNotNull(idpUrl);
        assertTrue(idpUrl.contains("saml/SSO"));
    }

    @Test
    @DisplayName("IdP descriptor URL should be set")
    void testIdpDescriptorUrl() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        String descriptorUrl = env.get("MM_SAMLSETTINGS_IDPDESCRIPTORURL");
        assertNotNull(descriptorUrl);
        assertTrue(descriptorUrl.contains("cognito-idp"));
    }

    @Test
    @DisplayName("Service Provider Identifier (Entity ID) should be set - REQUIRED")
    void testServiceProviderIdentifier() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        String spIdentifier = env.get("MM_SAMLSETTINGS_SERVICEPROVIDERIDENTIFIER");
        assertNotNull(spIdentifier, "Service Provider Identifier is required by Mattermost");
        // Should be the application URL (site URL)
        assertTrue(spIdentifier.startsWith("https://"));
    }

    @Test
    @DisplayName("SAML attribute mappings should be configured")
    void testSamlAttributeMappings() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("email", env.get("MM_SAMLSETTINGS_EMAILATTRIBUTE"));
        assertEquals("firstName", env.get("MM_SAMLSETTINGS_FIRSTNAMEATTRIBUTE"));
        assertEquals("lastName", env.get("MM_SAMLSETTINGS_LASTNAMEATTRIBUTE"));
        assertNotNull(env.get("MM_SAMLSETTINGS_USERNAMEATTRIBUTE"));
    }

    @Test
    @DisplayName("Admin and guest attributes should be disabled by default")
    void testRoleAttributes() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        // Disabled by default - enable when IdP sends role information
        // Format when enabled: "field=value" (e.g., "isAdmin=true")
        assertEquals("false", env.get("MM_SAMLSETTINGS_ENABLEADMINATTRIBUTE"));
        assertEquals("", env.get("MM_SAMLSETTINGS_ADMINATTRIBUTE"));
        assertEquals("", env.get("MM_SAMLSETTINGS_GUESTATTRIBUTE"));
    }

    @Test
    @DisplayName("AD/LDAP sync should be enabled for group support")
    void testLdapSyncEnabled() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("true", env.get("MM_SAMLSETTINGS_ENABLESYNCWITHLDAP"));
    }

    @Test
    @DisplayName("Certificate verification should be enabled")
    void testCertificateVerification() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("true", env.get("MM_SAMLSETTINGS_VERIFY"));
    }

    @Test
    @DisplayName("IdP certificate file should always be set - required by Mattermost")
    void testIdpCertificateAlwaysSet() {
        // Mattermost REQUIRES the certificate file - metadata URL auto-fetch doesn't work
        // ContainerFactory creates an init container to fetch the certificate from metadata URL
        // and write it to the certificate file path
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals(MattermostSamlIntegration.SAML_CERTIFICATE_MOUNT_PATH,
            env.get("MM_SAMLSETTINGS_IDPCERTIFICATEFILE"),
            "Certificate file path should always be set");
        assertNotNull(env.get("MM_SAMLSETTINGS_IDPMETADATAURL"),
            "Metadata URL should also be set for reference");
    }

    @Test
    @DisplayName("SAML certificate mount path constant should be defined")
    void testSamlCertificateMountPathConstant() {
        // Verify the constant exists and uses /mattermost/saml (NOT /mattermost/config)
        // because Mattermost needs write access to /mattermost/config/config.json
        assertEquals("/mattermost/saml/idp.crt", MattermostSamlIntegration.SAML_CERTIFICATE_MOUNT_PATH);
    }

    @Test
    @DisplayName("Cognito button text should reference AWS Cognito")
    void testCognitoButtonText() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);
        assertEquals("Sign in with AWS Cognito", env.get("MM_SAMLSETTINGS_LOGINBUTTONTEXT"));
    }

    @Test
    @DisplayName("Identity Center button text should reference IAM Identity Center")
    void testIdentityCenterButtonText() {
        Map<String, String> env = integration.getEnvironmentVariables(identityCenterConfig);
        assertEquals("Sign in with AWS IAM Identity Center", env.get("MM_SAMLSETTINGS_LOGINBUTTONTEXT"));
    }

    @Test
    @DisplayName("Container startup command should return Mattermost binary")
    void testGetContainerStartupCommand() {
        String command = integration.getContainerStartupCommand();
        // Mattermost uses a Go binary (distroless image, no shell)
        assertEquals("/mattermost/bin/mattermost", command);
    }

    @Test
    @DisplayName("Should be marked as distroless (no /bin/sh available)")
    void testIsDistroless() {
        // Mattermost official image is distroless - Go binary only
        assertTrue(integration.isDistroless());
    }

    @Test
    @DisplayName("Post-deployment instructions should contain SAML guidance")
    void testGetPostDeploymentInstructions() {
        String instructions = integration.getPostDeploymentInstructions();
        assertNotNull(instructions);
        assertTrue(instructions.contains("Mattermost"));
        assertTrue(instructions.contains("SAML"));
        assertTrue(instructions.contains("group sync"));
        assertTrue(instructions.contains("/login/sso/saml"));
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
        assertTrue(allCommands.contains("SAML_CERT"));
    }

    @Test
    @DisplayName("UserData commands should create certificate directory")
    void testUserDataCommandsCreateCertDirectory() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("mkdir -p /opt/mattermost/config/saml"));
    }

    @Test
    @DisplayName("UserData commands should include SAML settings")
    void testUserDataCommandsIncludeSamlSettings() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("MM_SAMLSETTINGS_ENABLE"));
        assertTrue(allCommands.contains("MM_SAMLSETTINGS_IDPURL"));
        assertTrue(allCommands.contains("MM_SAMLSETTINGS_EMAILATTRIBUTE"));
    }

    @Test
    @DisplayName("UserData commands should include error handling for certificate retrieval")
    void testUserDataCommandsIncludeErrorHandling() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("SAML_RETRIEVAL_FAILED"));
        assertTrue(allCommands.contains("exit 1"));
    }

    @Test
    @DisplayName("UserData commands should create environment file")
    void testUserDataCommandsCreateEnvFile() {
        List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);
        String allCommands = String.join("\n", commands);
        assertTrue(allCommands.contains("mattermost-saml-env.sh"));
    }

    @Test
    @DisplayName("All required SAML environment variables should be set")
    void testAllRequiredSamlEnvVarsSet() {
        Map<String, String> env = integration.getEnvironmentVariables(cognitoConfig);

        // List of required Mattermost SAML settings
        String[] requiredVars = {
            "MM_SAMLSETTINGS_ENABLE",
            "MM_SAMLSETTINGS_IDPURL",
            "MM_SAMLSETTINGS_IDPDESCRIPTORURL",
            "MM_SAMLSETTINGS_IDPMETADATAURL",  // Metadata URL for init container to fetch certificate
            "MM_SAMLSETTINGS_IDPCERTIFICATEFILE",  // Certificate file path - REQUIRED by Mattermost
            "MM_SAMLSETTINGS_VERIFY",
            "MM_SAMLSETTINGS_EMAILATTRIBUTE",
            "MM_SAMLSETTINGS_USERNAMEATTRIBUTE",
            "MM_SAMLSETTINGS_FIRSTNAMEATTRIBUTE",
            "MM_SAMLSETTINGS_LASTNAMEATTRIBUTE",
            "MM_SAMLSETTINGS_LOGINBUTTONTEXT",
            "MM_SAMLSETTINGS_ENABLESYNCWITHLDAP"
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
        assertTrue(cognitoAll.contains("MM_SAMLSETTINGS_ENABLE"));
        assertTrue(icAll.contains("MM_SAMLSETTINGS_ENABLE"));

        // Both should retrieve certificates
        assertTrue(cognitoAll.contains("SAML_CERT"));
        assertTrue(icAll.contains("SAML_CERT"));

        // Button text should differ
        assertTrue(cognitoAll.contains("AWS Cognito"));
        assertTrue(icAll.contains("AWS IAM Identity Center"));
    }
}
