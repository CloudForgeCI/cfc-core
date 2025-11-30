package com.cloudforge.core.oidc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityCenterOidcConfigurationTest {

    private static final String REGION = "us-east-1";
    private static final String IDENTITY_STORE_ID = "d-1234567890";
    private static final String TENANT = "my-company";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET_ARN = "arn:aws:secretsmanager:us-east-1:123456789012:secret:test-secret";
    private static final String REDIRECT_URL = "https://app.example.com/callback";
    private static final String ADMIN_GROUP = "Administrators";

    @Test
    void testIdentityCenterConfiguration() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals("identity-center", config.getProviderType());
        assertEquals("https://my-company.awsapps.com/start", config.getIssuerUrl());
        assertEquals("https://my-company.awsapps.com/start/oauth2/authorize", config.getAuthorizationEndpoint());
        assertEquals("https://my-company.awsapps.com/start/oauth2/token", config.getTokenEndpoint());
        assertEquals("https://my-company.awsapps.com/start/oauth2/userInfo", config.getUserInfoEndpoint());
        assertEquals("https://my-company.awsapps.com/start/.well-known/jwks.json", config.getJwksUri());
    }

    @Test
    void testClientConfiguration() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals(CLIENT_ID, config.getClientId());
        assertEquals(CLIENT_SECRET_ARN, config.getClientSecretArn());
        assertEquals(REDIRECT_URL, config.getRedirectUrl());
    }

    @Test
    void testIdentityCenterClaims() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        // Identity Center uses different claims than Cognito
        assertEquals("preferred_username", config.getUsernameClaim());
        assertEquals("groups", config.getGroupsClaim());
        assertEquals("openid profile email", config.getScopes());
    }

    @Test
    void testAdminGroupName() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            "CustomAdmins"
        );

        assertEquals("CustomAdmins", config.getAdminGroupName());
    }

    @Test
    void testDefaultAdminGroupName() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            null
        );

        assertEquals("Admins", config.getAdminGroupName());
    }

    @Test
    void testGetters() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals(IDENTITY_STORE_ID, config.getIdentityStoreId());
        assertEquals(REGION, config.getRegion());
        assertEquals(TENANT, config.getTenant());
    }

    @Test
    void testToString() {
        IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        String toString = config.toString();
        assertTrue(toString.contains("IdentityCenterOidcConfiguration"));
        assertTrue(toString.contains(REGION));
        assertTrue(toString.contains(IDENTITY_STORE_ID));
        assertTrue(toString.contains(TENANT));
        assertTrue(toString.contains(CLIENT_ID));
    }

    @Test
    void testDifferentTenants() {
        String[] tenants = {"my-company", "example-org", "test-portal"};

        for (String tenant : tenants) {
            IdentityCenterOidcConfiguration config = new IdentityCenterOidcConfiguration(
                REGION,
                IDENTITY_STORE_ID,
                tenant,
                CLIENT_ID,
                CLIENT_SECRET_ARN,
                REDIRECT_URL,
                ADMIN_GROUP
            );

            assertTrue(config.getAuthorizationEndpoint().contains(tenant));
            assertTrue(config.getTokenEndpoint().contains(tenant));
            assertTrue(config.getUserInfoEndpoint().contains(tenant));
            assertTrue(config.getIssuerUrl().contains(tenant));
        }
    }

    @Test
    void testClaimDifferencesFromCognito() {
        IdentityCenterOidcConfiguration icConfig = new IdentityCenterOidcConfiguration(
            REGION,
            IDENTITY_STORE_ID,
            TENANT,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        // Verify Identity Center doesn't use Cognito-specific claims
        assertNotEquals("cognito:username", icConfig.getUsernameClaim());
        assertNotEquals("cognito:groups", icConfig.getGroupsClaim());

        // Verify correct Identity Center claims
        assertEquals("preferred_username", icConfig.getUsernameClaim());
        assertEquals("groups", icConfig.getGroupsClaim());
    }
}
