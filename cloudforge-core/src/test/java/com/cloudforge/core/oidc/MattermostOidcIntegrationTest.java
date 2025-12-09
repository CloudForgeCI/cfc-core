package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for MattermostOidcIntegration.
 *
 * Tests Mattermost OIDC integration including:
 * - Environment variable generation
 * - Native OpenID Connect configuration
 * - Site URL derivation
 * - Provider-specific button text
 */
class MattermostOidcIntegrationTest {

    private MattermostOidcIntegration integration;

    @BeforeEach
    void setUp() {
        integration = new MattermostOidcIntegration();
    }

    // ========== Basic Properties Tests ==========

    @Test
    void testIsSupported() {
        assertTrue(integration.isSupported());
    }

    @Test
    void testGetIntegrationMethod() {
        String method = integration.getIntegrationMethod();
        assertNotNull(method);
        assertTrue(method.contains("OpenID Connect"));
        assertTrue(method.contains("MM_OPENIDSETTINGS"));
    }

    @Test
    void testGetAuthenticationType() {
        assertEquals("OIDC", integration.getAuthenticationType());
    }

    @Test
    void testGetOidcCallbackPath() {
        assertEquals("/signup/openid/complete", integration.getOidcCallbackPath());
    }

    @Test
    void testSupportsCognito() {
        assertTrue(integration.supportsCognito());
    }

    @Test
    void testSupportsIdentityCenterSaml() {
        // OIDC integration doesn't support Identity Center SAML
        assertFalse(integration.supportsIdentityCenterSaml());
    }

    @Test
    void testGetContainerStartupCommand() {
        assertEquals("/mattermost/bin/mattermost", integration.getContainerStartupCommand());
    }

    @Test
    void testGetConfigurationFile() {
        // Mattermost uses environment variables, not config files
        assertNull(integration.getConfigurationFile(createMockConfig()));
    }

    @Test
    void testGetConfigurationFilePath() {
        assertNull(integration.getConfigurationFilePath());
    }

    // ========== Environment Variables Tests ==========

    @Test
    void testEnvironmentVariablesContainOpenIdSettings() {
        OidcConfiguration config = createMockConfig();
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertNotNull(env);
        assertEquals("true", env.get("MM_OPENIDSETTINGS_ENABLE"));
        assertEquals("test-client-id", env.get("MM_OPENIDSETTINGS_ID"));
    }

    @Test
    void testEnvironmentVariablesContainDiscoveryEndpoint() {
        OidcConfiguration config = createMockConfig();
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertEquals("https://auth.example.com/.well-known/openid-configuration",
                     env.get("MM_OPENIDSETTINGS_DISCOVERYENDPOINT"));
    }

    @Test
    void testEnvironmentVariablesContainScopes() {
        OidcConfiguration config = createMockConfig();
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertEquals("openid profile email", env.get("MM_OPENIDSETTINGS_SCOPE"));
    }

    @Test
    void testEnvironmentVariablesContainSiteUrl() {
        OidcConfiguration config = createMockConfig();
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertEquals("https://mattermost.example.com", env.get("MM_SERVICESETTINGS_SITEURL"));
    }

    @Test
    void testEnvironmentVariablesDoNotContainClientSecret() {
        // Client secret should be injected via ECS secrets, not environment variables
        OidcConfiguration config = createMockConfig();
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertFalse(env.containsKey("MM_OPENIDSETTINGS_SECRET"),
                "Client secret should NOT be in environment variables");
    }

    // ========== Button Text Tests ==========

    @ParameterizedTest
    @CsvSource({
        "cognito,Sign in with AWS Cognito",
        "identity-center,Sign in with AWS IAM Identity Center",
        ",Sign in with AWS Cognito"
    })
    void testButtonTextByProviderType(String providerType, String expectedText) {
        OidcConfiguration config = createMockConfigWithProvider(providerType);
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertEquals(expectedText, env.get("MM_OPENIDSETTINGS_BUTTONTEXT"));
    }

    @Test
    void testButtonColor() {
        OidcConfiguration config = createMockConfig();
        Map<String, String> env = integration.getEnvironmentVariables(config);

        assertEquals("#FF9900", env.get("MM_OPENIDSETTINGS_BUTTONCOLOR"));
    }

    // ========== Site URL Derivation Tests ==========

    @Test
    void testSiteUrlFromApplicationUrl() {
        OidcConfiguration config = new MockOidcConfiguration(
                "test-client-id",
                "https://auth.example.com/oauth2/authorize",
                "https://auth.example.com/oauth2/token",
                "https://auth.example.com/oauth2/userInfo",
                "https://auth.example.com",
                "https://mattermost.example.com/signup/openid/complete",
                "https://custom-site.example.com",  // applicationUrl
                null,
                "cognito"
        );

        Map<String, String> env = integration.getEnvironmentVariables(config);
        assertEquals("https://custom-site.example.com", env.get("MM_SERVICESETTINGS_SITEURL"));
    }

    @Test
    void testSiteUrlDerivedFromRedirectUrl() {
        OidcConfiguration config = new MockOidcConfiguration(
                "test-client-id",
                "https://auth.example.com/oauth2/authorize",
                "https://auth.example.com/oauth2/token",
                "https://auth.example.com/oauth2/userInfo",
                "https://auth.example.com",
                "https://mattermost.example.com/signup/openid/complete",
                null,  // No applicationUrl
                null,
                "cognito"
        );

        Map<String, String> env = integration.getEnvironmentVariables(config);
        assertEquals("https://mattermost.example.com", env.get("MM_SERVICESETTINGS_SITEURL"));
    }

    // ========== User Data Commands Tests ==========

    @Test
    void testGetUserDataCommands() {
        OidcConfiguration config = createMockConfig();
        List<String> commands = integration.getUserDataCommands(config, null);

        assertNotNull(commands);
        assertFalse(commands.isEmpty());
        assertTrue(commands.stream().anyMatch(cmd -> cmd.contains("OIDC")));
    }

    // ========== Post-Deployment Instructions Tests ==========

    @Test
    void testPostDeploymentInstructions() {
        String instructions = integration.getPostDeploymentInstructions();

        assertNotNull(instructions);
        assertTrue(instructions.contains("Mattermost"));
        assertTrue(instructions.contains("Cognito"));
        assertTrue(instructions.contains("OIDC"));
    }

    // ========== Helper Methods ==========

    private OidcConfiguration createMockConfig() {
        return createMockConfigWithProvider("cognito");
    }

    private OidcConfiguration createMockConfigWithProvider(String providerType) {
        return new MockOidcConfiguration(
                "test-client-id",
                "https://auth.example.com/oauth2/authorize",
                "https://auth.example.com/oauth2/token",
                "https://auth.example.com/oauth2/userInfo",
                "https://auth.example.com",
                "https://mattermost.example.com/signup/openid/complete",
                "https://mattermost.example.com",
                null,
                providerType
        );
    }

    // ========== Mock OidcConfiguration ==========

    static class MockOidcConfiguration implements OidcConfiguration {
        private final String clientId;
        private final String authEndpoint;
        private final String tokenEndpoint;
        private final String userInfoEndpoint;
        private final String issuerUrl;
        private final String redirectUrl;
        private final String applicationUrl;
        private final String clientSecretArn;
        private final String providerType;

        MockOidcConfiguration(String clientId, String authEndpoint, String tokenEndpoint,
                              String userInfoEndpoint, String issuerUrl, String redirectUrl,
                              String applicationUrl, String clientSecretArn, String providerType) {
            this.clientId = clientId;
            this.authEndpoint = authEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.userInfoEndpoint = userInfoEndpoint;
            this.issuerUrl = issuerUrl;
            this.redirectUrl = redirectUrl;
            this.applicationUrl = applicationUrl;
            this.clientSecretArn = clientSecretArn;
            this.providerType = providerType;
        }

        @Override
        public String getClientId() { return clientId; }

        @Override
        public String getAuthorizationEndpoint() { return authEndpoint; }

        @Override
        public String getTokenEndpoint() { return tokenEndpoint; }

        @Override
        public String getUserInfoEndpoint() { return userInfoEndpoint; }

        @Override
        public String getIssuerUrl() { return issuerUrl; }

        @Override
        public String getRedirectUrl() { return redirectUrl; }

        @Override
        public String getApplicationUrl() { return applicationUrl; }

        @Override
        public String getClientSecretArn() { return clientSecretArn; }

        @Override
        public String getProviderType() { return providerType; }

        @Override
        public String getJwksUri() { return issuerUrl + "/.well-known/jwks.json"; }

        @Override
        public String getScopes() { return "openid profile email"; }

        @Override
        public String getUsernameClaim() { return "preferred_username"; }
    }
}
