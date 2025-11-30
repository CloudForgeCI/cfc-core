package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.OidcConfiguration;

/**
 * OIDC configuration for AWS IAM Identity Center (formerly AWS SSO).
 *
 * <p>IAM Identity Center is an enterprise single sign-on (SSO) service that provides:</p>
 * <ul>
 *   <li>Centralized access management across AWS accounts</li>
 *   <li>Integration with external identity providers (Active Directory, Okta, etc.)</li>
 *   <li>SAML 2.0 and OIDC support</li>
 *   <li>Fine-grained permissions</li>
 *   <li>Multi-account access</li>
 * </ul>
 *
 * <p><strong>Important:</strong> IAM Identity Center and Cognito are completely separate systems.</p>
 * <ul>
 *   <li><strong>IAM Identity Center:</strong> Enterprise SSO, integrates with corporate directories</li>
 *   <li><strong>Cognito:</strong> Standalone user directory for customer-facing apps</li>
 * </ul>
 *
 * <p><strong>IAM Identity Center OIDC Setup:</strong></p>
 * <ol>
 *   <li>Create a custom OIDC application in IAM Identity Center console</li>
 *   <li>Configure redirect URLs for your application</li>
 *   <li>Note the client ID and client secret</li>
 *   <li>Store client secret in AWS Secrets Manager</li>
 *   <li>Use this configuration to wire up application OIDC</li>
 * </ol>
 *
 * <p><strong>Identity Center OIDC Endpoints:</strong></p>
 * <ul>
 *   <li>Authorization: https://{tenant}.awsapps.com/start/oauth2/authorize</li>
 *   <li>Token: https://{tenant}.awsapps.com/start/oauth2/token</li>
 *   <li>UserInfo: https://{tenant}.awsapps.com/start/oauth2/userInfo</li>
 *   <li>JWKS: https://{tenant}.awsapps.com/start/.well-known/jwks.json</li>
 * </ul>
 *
 * @see <a href="https://docs.aws.amazon.com/singlesignon/latest/userguide/oidc-endpoints.html">Identity Center OIDC Endpoints</a>
 */
public class IdentityCenterOidcConfiguration implements OidcConfiguration {

    private final String region;
    private final String identityStoreId;
    private final String tenant;  // Identity Center tenant (e.g., d-1234567890)
    private final String clientId;
    private final String clientSecretArn;
    private final String redirectUrl;
    private final String adminGroupName;

    /**
     * Creates an IAM Identity Center OIDC configuration.
     *
     * @param region AWS region where Identity Center is configured
     * @param identityStoreId Identity Store ID (e.g., d-1234567890)
     * @param tenant Identity Center tenant/portal URL prefix
     * @param clientId OAuth 2.0 client ID from Identity Center application
     * @param clientSecretArn Secrets Manager ARN for client secret
     * @param redirectUrl Application callback URL
     * @param adminGroupName Admin group name from Identity Center for role mapping
     */
    public IdentityCenterOidcConfiguration(
            String region,
            String identityStoreId,
            String tenant,
            String clientId,
            String clientSecretArn,
            String redirectUrl,
            String adminGroupName) {
        this.region = region;
        this.identityStoreId = identityStoreId;
        this.tenant = tenant;
        this.clientId = clientId;
        this.clientSecretArn = clientSecretArn;
        this.redirectUrl = redirectUrl;
        this.adminGroupName = adminGroupName != null ? adminGroupName : "Admins";
    }

    @Override
    public String getProviderType() {
        return "identity-center";
    }

    @Override
    public String getIssuerUrl() {
        // IAM Identity Center issuer URL
        return String.format("https://%s.awsapps.com/start", tenant);
    }

    @Override
    public String getAuthorizationEndpoint() {
        return String.format("https://%s.awsapps.com/start/oauth2/authorize", tenant);
    }

    @Override
    public String getTokenEndpoint() {
        return String.format("https://%s.awsapps.com/start/oauth2/token", tenant);
    }

    @Override
    public String getUserInfoEndpoint() {
        return String.format("https://%s.awsapps.com/start/oauth2/userInfo", tenant);
    }

    @Override
    public String getJwksUri() {
        return String.format("https://%s.awsapps.com/start/.well-known/jwks.json", tenant);
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
        // Identity Center supports these standard OIDC scopes
        return "openid profile email";
    }

    @Override
    public String getUsernameClaim() {
        // Identity Center uses 'preferred_username' for the username
        return "preferred_username";
    }

    @Override
    public String getGroupsClaim() {
        // Identity Center uses 'groups' claim
        return "groups";
    }

    @Override
    public String getAdminGroupName() {
        return adminGroupName;
    }

    /**
     * Returns the Identity Store ID.
     *
     * @return identity store ID
     */
    public String getIdentityStoreId() {
        return identityStoreId;
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
     * Returns the Identity Center tenant.
     *
     * @return tenant ID
     */
    public String getTenant() {
        return tenant;
    }

    @Override
    public String toString() {
        return "IdentityCenterOidcConfiguration{" +
                "region='" + region + '\'' +
                ", identityStoreId='" + identityStoreId + '\'' +
                ", tenant='" + tenant + '\'' +
                ", clientId='" + clientId + '\'' +
                ", redirectUrl='" + redirectUrl + '\'' +
                '}';
    }
}
