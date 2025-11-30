package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.OidcConfiguration;

/**
 * OIDC configuration for Amazon Cognito User Pools.
 *
 * <p>Amazon Cognito is a standalone user directory service that provides:</p>
 * <ul>
 *   <li>User sign-up and sign-in</li>
 *   <li>Multi-factor authentication (MFA)</li>
 *   <li>Social identity providers (Google, Facebook, etc.)</li>
 *   <li>OIDC and OAuth 2.0 support</li>
 *   <li>Built-in hosted UI</li>
 * </ul>
 *
 * <p><strong>Important:</strong> Cognito and IAM Identity Center are separate systems.
 * Use CognitoOidcConfiguration for Cognito User Pools, and
 * IdentityCenterOidcConfiguration for IAM Identity Center.</p>
 *
 * <p><strong>Cognito OIDC Endpoints:</strong></p>
 * <ul>
 *   <li>Authorization: https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize</li>
 *   <li>Token: https://{domain}.auth.{region}.amazoncognito.com/oauth2/token</li>
 *   <li>UserInfo: https://{domain}.auth.{region}.amazoncognito.com/oauth2/userInfo</li>
 *   <li>JWKS: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json</li>
 * </ul>
 *
 * @see <a href="https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-userpools-server-contract-reference.html">Cognito OIDC Specification</a>
 */
public class CognitoOidcConfiguration implements OidcConfiguration {

    private final String region;
    private final String userPoolId;
    private final String domain;  // Custom domain or Cognito domain prefix
    private final String clientId;
    private final String clientSecretArn;
    private final String redirectUrl;
    private final String adminGroupName;

    /**
     * Creates a Cognito OIDC configuration.
     *
     * @param region AWS region (e.g., us-east-1)
     * @param userPoolId Cognito User Pool ID (e.g., us-east-1_abcdef123)
     * @param domain Cognito domain prefix or custom domain (e.g., myapp or auth.example.com)
     * @param clientId OAuth 2.0 client ID
     * @param clientSecretArn Secrets Manager ARN for client secret
     * @param redirectUrl Application callback URL
     * @param adminGroupName Admin group name for role mapping
     */
    public CognitoOidcConfiguration(
            String region,
            String userPoolId,
            String domain,
            String clientId,
            String clientSecretArn,
            String redirectUrl,
            String adminGroupName) {
        this.region = region;
        this.userPoolId = userPoolId;
        this.domain = domain;
        this.clientId = clientId;
        this.clientSecretArn = clientSecretArn;
        this.redirectUrl = redirectUrl;
        this.adminGroupName = adminGroupName != null ? adminGroupName : "Admins";
    }

    @Override
    public String getProviderType() {
        return "cognito";
    }

    @Override
    public String getIssuerUrl() {
        return String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
    }

    @Override
    public String getAuthorizationEndpoint() {
        // Check if domain is a custom domain (contains dots)
        if (domain.contains(".")) {
            // Custom domain
            return String.format("https://%s/oauth2/authorize", domain);
        } else {
            // Cognito domain prefix
            return String.format("https://%s.auth.%s.amazoncognito.com/oauth2/authorize", domain, region);
        }
    }

    @Override
    public String getTokenEndpoint() {
        if (domain.contains(".")) {
            return String.format("https://%s/oauth2/token", domain);
        } else {
            return String.format("https://%s.auth.%s.amazoncognito.com/oauth2/token", domain, region);
        }
    }

    @Override
    public String getUserInfoEndpoint() {
        if (domain.contains(".")) {
            return String.format("https://%s/oauth2/userInfo", domain);
        } else {
            return String.format("https://%s.auth.%s.amazoncognito.com/oauth2/userInfo", domain, region);
        }
    }

    @Override
    public String getJwksUri() {
        return String.format("https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json", region, userPoolId);
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getClientSecretArn() {
        return clientSecretArn;
    }

    @Override
    public String getRedirectUrl() {
        return redirectUrl;
    }

    @Override
    public String getScopes() {
        return "openid profile email";
    }

    @Override
    public String getUsernameClaim() {
        return "cognito:username";
    }

    @Override
    public String getGroupsClaim() {
        return "cognito:groups";
    }

    @Override
    public String getAdminGroupName() {
        return adminGroupName;
    }

    /**
     * Returns the Cognito User Pool ID.
     *
     * @return user pool ID
     */
    public String getUserPoolId() {
        return userPoolId;
    }

    /**
     * Returns the AWS region.
     *
     * @return region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Returns the Cognito domain.
     *
     * @return domain prefix or custom domain
     */
    public String getDomain() {
        return domain;
    }

    @Override
    public String toString() {
        return "CognitoOidcConfiguration{" +
                "region='" + region + '\'' +
                ", userPoolId='" + userPoolId + '\'' +
                ", domain='" + domain + '\'' +
                ", clientId='" + clientId + '\'' +
                ", redirectUrl='" + redirectUrl + '\'' +
                '}';
    }
}
