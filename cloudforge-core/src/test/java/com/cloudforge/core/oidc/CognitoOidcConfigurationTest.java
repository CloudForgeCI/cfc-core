package com.cloudforge.core.oidc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CognitoOidcConfigurationTest {

    private static final String REGION = "us-east-1";
    private static final String USER_POOL_ID = "us-east-1_abcdef123";
    private static final String DOMAIN_PREFIX = "myapp";
    private static final String CUSTOM_DOMAIN = "auth.example.com";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET_ARN = "arn:aws:secretsmanager:us-east-1:123456789012:secret:test-secret";
    private static final String REDIRECT_URL = "https://app.example.com/callback";
    private static final String ADMIN_GROUP = "Admins";

    @Test
    void testCognitoConfigurationWithDomainPrefix() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals("cognito", config.getProviderType());
        assertEquals("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abcdef123", config.getIssuerUrl());
        assertEquals("https://myapp.auth.us-east-1.amazoncognito.com/oauth2/authorize", config.getAuthorizationEndpoint());
        assertEquals("https://myapp.auth.us-east-1.amazoncognito.com/oauth2/token", config.getTokenEndpoint());
        assertEquals("https://myapp.auth.us-east-1.amazoncognito.com/oauth2/userInfo", config.getUserInfoEndpoint());
        assertEquals("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abcdef123/.well-known/jwks.json", config.getJwksUri());
    }

    @Test
    void testCognitoConfigurationWithCustomDomain() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            CUSTOM_DOMAIN,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals("https://auth.example.com/oauth2/authorize", config.getAuthorizationEndpoint());
        assertEquals("https://auth.example.com/oauth2/token", config.getTokenEndpoint());
        assertEquals("https://auth.example.com/oauth2/userInfo", config.getUserInfoEndpoint());
        assertEquals("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abcdef123/.well-known/jwks.json", config.getJwksUri());
    }

    @Test
    void testClientConfiguration() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
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
    void testCognitoClaims() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals("cognito:username", config.getUsernameClaim());
        assertEquals("cognito:groups", config.getGroupsClaim());
        assertEquals("openid profile email", config.getScopes());
    }

    @Test
    void testAdminGroupName() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            "CustomAdmins"
        );

        assertEquals("CustomAdmins", config.getAdminGroupName());
    }

    @Test
    void testDefaultAdminGroupName() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            null
        );

        assertEquals("Admins", config.getAdminGroupName());
    }

    @Test
    void testGetters() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        assertEquals(USER_POOL_ID, config.getUserPoolId());
        assertEquals(REGION, config.getRegion());
        assertEquals(DOMAIN_PREFIX, config.getDomain());
    }

    @Test
    void testToString() {
        CognitoOidcConfiguration config = new CognitoOidcConfiguration(
            REGION,
            USER_POOL_ID,
            DOMAIN_PREFIX,
            CLIENT_ID,
            CLIENT_SECRET_ARN,
            REDIRECT_URL,
            ADMIN_GROUP
        );

        String toString = config.toString();
        assertTrue(toString.contains("CognitoOidcConfiguration"));
        assertTrue(toString.contains(REGION));
        assertTrue(toString.contains(USER_POOL_ID));
        assertTrue(toString.contains(DOMAIN_PREFIX));
        assertTrue(toString.contains(CLIENT_ID));
    }

    @Test
    void testMultipleRegions() {
        String[] regions = {"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1"};

        for (String region : regions) {
            CognitoOidcConfiguration config = new CognitoOidcConfiguration(
                region,
                region + "_abcdef123",
                "myapp",
                CLIENT_ID,
                CLIENT_SECRET_ARN,
                REDIRECT_URL,
                ADMIN_GROUP
            );

            assertTrue(config.getAuthorizationEndpoint().contains(region));
            assertTrue(config.getTokenEndpoint().contains(region));
            assertTrue(config.getUserInfoEndpoint().contains(region));
            assertTrue(config.getIssuerUrl().contains(region));
        }
    }
}
