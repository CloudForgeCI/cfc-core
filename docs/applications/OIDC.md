# CloudForge OIDC Authentication Guide

This guide covers authentication configuration for CloudForge applications, including AWS Cognito integration and application-level OIDC setup.

## Authentication Modes

CloudForge supports three authentication modes:

| Mode | Description | How it Works |
|------|-------------|--------------|
| `none` | No authentication | Application handles its own auth (default admin login) |
| `alb-oidc` | ALB-level authentication | AWS ALB authenticates users before requests reach the application |
| `application-oidc` | Application-level authentication | Application handles OIDC directly (e.g., Jenkins oic-auth plugin) |

### Mode Comparison

| Feature | `none` | `alb-oidc` | `application-oidc` |
|---------|--------|------------|-------------------|
| **Authentication Point** | Application | Load Balancer | Application |
| **Requires HTTPS** | No | Yes | Yes |
| **Requires Custom Domain** | No | No* | No* |
| **Public Pages** | Yes | No (all authenticated) | Yes |
| **Group/Role Mapping** | N/A | Limited | Full |
| **Application Plugin Required** | No | No | Yes |
| **Logout from Provider** | N/A | Automatic | Configurable |

\* When no custom domain is configured, CloudForge automatically provisions an AWS Private CA certificate for the ALB DNS name. This enables HTTPS without requiring a registered domain. Note: Private CA certificates are not trusted by browsers, so users will see certificate warnings.

---

## Quick Start

### Option 1: Cognito with Application-Level OIDC (Recommended for Jenkins)

```json
{
  "authMode": "application-oidc",
  "oidcProvider": "cognito",
  "cognitoAutoProvision": true,
  "cognitoUserPoolName": "my-app-users",
  "cognitoDomainPrefix": "my-app-auth",
  "cognitoMfaEnabled": true,
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "Admins",
  "cognitoUserGroupName": "Users",
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

### Option 2: ALB-Level OIDC (Works with Any Application)

```json
{
  "authMode": "alb-oidc",
  "oidcProvider": "cognito",
  "cognitoAutoProvision": true,
  "cognitoUserPoolName": "my-app-users",
  "cognitoDomainPrefix": "my-app-auth",
  "cognitoMfaEnabled": true
}
```

### Option 3: External OIDC Provider

```json
{
  "authMode": "application-oidc",
  "oidcProvider": "external",
  "oidcIssuer": "https://your-domain.okta.com",
  "oidcAuthorizationEndpoint": "https://your-domain.okta.com/oauth2/v1/authorize",
  "oidcTokenEndpoint": "https://your-domain.okta.com/oauth2/v1/token",
  "oidcUserInfoEndpoint": "https://your-domain.okta.com/oauth2/v1/userinfo",
  "oidcClientId": "client-id-from-provider",
  "oidcClientSecretName": "my-app/oidc/client-secret"
}
```

---

## Configuration Reference

### Core Authentication Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `authMode` | String | `none` | Authentication mode: `none`, `alb-oidc`, or `application-oidc` |
| `oidcProvider` | String | `cognito` | OIDC provider: `cognito`, `identity-center`, or `external` |

### Cognito Auto-Provisioning

Used when `cognitoAutoProvision: true` to create a new Cognito User Pool.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `cognitoAutoProvision` | Boolean | `false` | Create a new Cognito User Pool automatically |
| `cognitoUserPoolName` | String | `{stackName}-users` | Name for the Cognito User Pool |
| `cognitoDomainPrefix` | String | `{stackName}-auth` | Domain prefix for Cognito Hosted UI |
| `cognitoMfaEnabled` | Boolean | `false` | Enable Multi-Factor Authentication |
| `cognitoMfaMethod` | String | `TOTP` | MFA method: `TOTP` (authenticator app) or `SMS` |
| `cognitoCreateGroups` | Boolean | `true` | Create user groups for role-based access |
| `cognitoAdminGroupName` | String | `Admins` | Name of the admin group |
| `cognitoUserGroupName` | String | `Users` | Name of the standard user group |
| `cognitoInitialAdminEmail` | String | - | Email for initial admin user (auto-created) |
| `cognitoInitialAdminPhone` | String | - | Phone number for SMS MFA (if using SMS) |

### Existing Cognito User Pool

Connect to a pre-existing Cognito User Pool instead of creating one.

| Field | Type | Description |
|-------|------|-------------|
| `cognitoUserPoolId` | String | Existing User Pool ID (e.g., `us-west-2_abcdef123`) |
| `cognitoAppClientId` | String | Existing App Client ID |
| `cognitoUserPoolDomain` | String | Existing domain prefix or custom domain |

### External OIDC Provider

Use with Okta, Auth0, Azure AD, or any OIDC-compliant provider.

| Field | Type | Description |
|-------|------|-------------|
| `oidcIssuer` | String | OIDC issuer URL (e.g., `https://your-domain.okta.com`) |
| `oidcAuthorizationEndpoint` | String | OAuth2 authorization endpoint |
| `oidcTokenEndpoint` | String | OAuth2 token endpoint |
| `oidcUserInfoEndpoint` | String | OIDC userinfo endpoint |
| `oidcClientId` | String | OAuth2 client ID |
| `oidcClientSecretName` | String | AWS Secrets Manager secret name for client secret |

### IAM Identity Center

For enterprise SSO with AWS IAM Identity Center.

| Field | Type | Description |
|-------|------|-------------|
| `ssoInstanceArn` | String | IAM Identity Center instance ARN |
| `oidcIssuer` | String | Identity Center issuer URL |

---

## Application Support Matrix

### Tested Applications

| Application | `alb-oidc` | `application-oidc` | OIDC Plugin | Status |
|-------------|-----------|-------------------|-------------|--------|
| **Jenkins** | ✅ Tested | ✅ Tested | `oic-auth` | Production Ready |
| **GitLab** | ✅ Tested | ❌ Not Working | Built-in OmniAuth | ALB-OIDC Ready |
| **Grafana** | ✅ Tested | ⏳ Planned | Built-in generic_oauth | ALB-OIDC Ready |
| **Mattermost Enterprise** | ✅ Tested | ⚠️ Partial (logout issue) | Native OIDC | ALB-OIDC Ready |
| **Mattermost Team** | ✅ Tested | ⚠️ Partial (no logout) | GitLab OAuth | ALB-OIDC Ready |
| **Superset** | ✅ Tested | ❌ Not Implemented | superset_config.py | ALB-OIDC Ready |

### Application Callback URLs

CloudForge automatically configures the correct callback URLs for each application when using `application-oidc` mode.

#### OIDC Callback URLs

| Application | Callback URL | Method | Source |
|-------------|--------------|--------|--------|
| **Jenkins** | `/securityRealm/finishLogin` | OIDC | `JenkinsOidcIntegration.getOidcCallbackPath()` |
| **GitLab** | `/users/auth/openid_connect/callback` | OIDC | `GitLabOidcIntegration.getOidcCallbackPath()` |
| **Mattermost Enterprise** | `/signup/openid/complete` | OIDC | `MattermostOidcIntegration.getOidcCallbackPath()` |
| **Mattermost Team** | `/signup/gitlab/complete` | OIDC (GitLab OAuth) | `MattermostGitLabOidcIntegration.getOidcCallbackPath()` |
| **Grafana** | `/login/generic_oauth` | OIDC | `GrafanaOidcIntegration.getOidcCallbackPath()` |

#### SAML Callback URLs (ACS URLs)

| Application | ACS URL | Method | Source |
|-------------|---------|--------|--------|
| **Metabase Enterprise** | `/auth/sso` | SAML 2.0 | `MetabaseSamlIntegration.getSamlAcsPath()` |
| **Mattermost Enterprise** | `/login/sso/saml` | SAML 2.0 | `MattermostSamlIntegration.getSamlAcsPath()` |
| **GitLab Enterprise** | `/users/auth/saml/callback` | SAML 2.0 | `GitLabSamlIntegration.getSamlAcsPath()` |

#### ALB-OIDC Callback URL

For ALB-level OIDC (`authMode: "alb-oidc"`), all applications use:
- **Callback URL:** `/oauth2/idpresponse` (handled by AWS ALB, not the application)

**How It Works:**
1. `CognitoAuthenticationFactory` checks `authMode`
2. If `application-oidc`: Calls `ApplicationSpec.getOidcIntegration().getOidcCallbackPath()`
3. If `alb-oidc`: Uses standard ALB callback path `/oauth2/idpresponse`
4. Constructs full callback URL: `https://your-domain.com` + callback path
5. Registers callback URL with OIDC provider (Cognito, Identity Center, etc.)

### Known Issues

| Application | Issue | Details | Status |
|-------------|-------|---------|--------|
| **GitLab** | `application-oidc` callback URL issues | ✅ FIXED - Callback URL now properly retrieved from ApplicationSpec | ✅ Fixed |
| **Mattermost Enterprise** | `application-oidc` logout callback | ✅ FIXED - Callback URL now properly retrieved from ApplicationSpec | ✅ Fixed |
| **Mattermost Team** | `application-oidc` no single logout | GitLab OAuth provider does not support single logout (Team Edition limitation) | ⚠️ Limitation |
| **Metabase Enterprise** | SAML integration requires specific config | `applicationOidcConfig` NULL when missing `autoProvisionIdentityCenter: true` and `ssoInstanceArn` | ⚠️ Configuration Issue |
| **Metabase OSS** | OIDC not supported | Open-source edition does not support native OIDC; use ALB-level OIDC instead | ⚠️ Limitation |
| **Superset** | `application-oidc` not implemented | CloudForge does not auto-configure superset_config.py for OIDC | ⚠️ Not Implemented |

**Workarounds:**
- **GitLab**: FIXED - Callback URL now retrieved from ApplicationSpec instead of hardcoded Jenkins default
- **Mattermost Enterprise**: FIXED - Callback URL now retrieved from ApplicationSpec; logout callback should work correctly
- **Mattermost Team**: FIXED - Callback URL now retrieved from ApplicationSpec; single logout not supported in Team Edition (GitLab OAuth limitation)
- **Metabase Enterprise**: For SAML, set `authMode: "application-oidc"`, `autoProvisionIdentityCenter: true`, and `ssoInstanceArn`; or use `alb-oidc` as simpler alternative
- **Metabase OSS**: Use `alb-oidc` mode (only option for OSS edition)
- **Superset**: Use `alb-oidc` mode (application-level OIDC not implemented)

### Untested Applications

The following applications have OIDC support but have not been fully tested with CloudForge:

| Application | Expected Support | OIDC Method |
|-------------|-----------------|-------------|
| **Gitea** | `application-oidc` | Built-in OAuth2 |
| **SonarQube** | `application-oidc` | OIDC Plugin |
| **Harbor** | `application-oidc` | harbor.yml config |
| **Nexus** | `application-oidc` | OIDC Plugin (Pro) |
| **Mattermost Team** | `application-oidc` | GitLab OAuth (MM_GITLABSETTINGS_*) |
| **Mattermost Enterprise** | `application-oidc` | Native OIDC (MM_OPENIDSETTINGS_*) |
| **Superset** | `application-oidc` | superset_config.py |

> **Mattermost Note:** Team Edition uses GitLab OAuth provider (no single logout). Enterprise Edition uses native OpenID Connect with discovery endpoint and single logout support.

---

## Jenkins OIDC Integration

Jenkins has full support for both authentication modes.

### ALB-OIDC Mode

With `authMode: "alb-oidc"`, authentication happens at the AWS Application Load Balancer:

1. User accesses Jenkins URL
2. ALB redirects to Cognito login
3. User authenticates with Cognito
4. ALB validates token and forwards request to Jenkins
5. Jenkins sees user as authenticated via `X-Amzn-Oidc-*` headers

**Pros:**
- Works without any Jenkins plugins
- All requests are authenticated
- Simple configuration

**Cons:**
- Cannot have public pages
- Limited role mapping
- Requires HTTPS

### Application-OIDC Mode (Recommended)

With `authMode: "application-oidc"`, Jenkins handles authentication directly:

1. User accesses Jenkins URL
2. Jenkins redirects to Cognito login
3. User authenticates with Cognito
4. Cognito redirects back to Jenkins with authorization code
5. Jenkins exchanges code for tokens and creates session
6. Group-based permissions applied from Cognito groups

**Pros:**
- Full group/role mapping from Cognito
- Can have public pages if needed
- Application controls auth flow
- Proper logout from Cognito

**Cons:**
- Requires oic-auth plugin
- More configuration options

### Jenkins JCasC Configuration

CloudForge automatically generates Jenkins Configuration as Code (JCasC) for OIDC:

```yaml
jenkins:
  securityRealm:
    oic:
      serverConfiguration:
        manual:
          authorizationServerUrl: "https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize"
          tokenServerUrl: "https://{domain}.auth.{region}.amazoncognito.com/oauth2/token"
          userInfoServerUrl: "https://{domain}.auth.{region}.amazoncognito.com/oauth2/userInfo"
          jwksServerUrl: "https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json"
          issuer: "https://cognito-idp.{region}.amazonaws.com/{userPoolId}"
          scopes: "openid profile email"
          endSessionUrl: "https://{domain}.auth.{region}.amazoncognito.com/logout?client_id={clientId}&logout_uri={jenkinsUrl}/"
      clientId: "{clientId}"
      clientSecret: "${JENKINS_OIDC_CLIENT_SECRET}"
      userNameField: "sub"
      fullNameFieldName: "name"
      emailFieldName: "email"
      groupsFieldName: '"cognito:groups"'
      disableSslVerification: false
      logoutFromOpenidProvider: true
      postLogoutRedirectUrl: "{jenkinsUrl}/"

  authorizationStrategy:
    projectMatrix:
      permissions:
        - "Overall/Administer:{adminGroup}"
        - "Overall/Read:{adminGroup}"
        - "Overall/Read:{userGroup}"
        - "Job/Build:{userGroup}"
        - "Job/Configure:{userGroup}"
        - "Job/Create:{userGroup}"
        - "Job/Read:{userGroup}"
        - "Overall/Read:authenticated"
```

### Cognito Logout Integration

CloudForge configures proper Cognito logout using manual server configuration with `endSessionUrl`. This ensures users are logged out of both Jenkins and Cognito when they click "Log out":

```
https://{domain}.auth.{region}.amazoncognito.com/logout?client_id={clientId}&logout_uri={jenkinsUrl}/
```

**Technical Note:** Cognito's logout endpoint requires `client_id` and `logout_uri` parameters that differ from the standard OIDC `end_session_endpoint` spec. CloudForge uses the oic-auth plugin's manual configuration mode to set the `endSessionUrl` with these parameters pre-formatted. Reference: [oic-auth-plugin#95](https://github.com/jenkinsci/oic-auth-plugin/issues/95)

---

## Cognito User Groups

When `cognitoCreateGroups: true`, CloudForge creates user groups for role-based access:

### Default Groups

| Group | Jenkins Role | Permissions |
|-------|--------------|-------------|
| `{prefix}-Admin` | Administrator | Full access to all Jenkins features |
| `{prefix}-User` | Developer | Build, configure, and create jobs |

### Custom Group Names

```json
{
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "Jenkins-Admins",
  "cognitoUserGroupName": "Jenkins-Developers"
}
```

### Disabling Groups

Set `cognitoCreateGroups: false` to grant all authenticated users full access:

```json
{
  "cognitoCreateGroups": false
}
```

This configures Jenkins with `loggedInUsersCanDoAnything` authorization.

---

## Multi-Factor Authentication

### Enabling MFA

```json
{
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "TOTP"
}
```

### MFA Methods

| Method | Description | Requirements |
|--------|-------------|--------------|
| `TOTP` | Time-based One-Time Password | Authenticator app (Google Authenticator, Authy) |
| `SMS` | SMS text messages | Phone number required, additional SMS costs |

### Compliance Considerations

MFA is **required** for certain compliance frameworks:

| Framework | MFA Requirement |
|-----------|----------------|
| SOC 2 | Required for privileged access |
| PCI-DSS | Required for all users accessing cardholder data |
| HIPAA | Required for ePHI access |
| GDPR | Recommended for personal data access |

---

## OIDC Architecture

### Core Interfaces

#### OidcConfiguration Interface
Defines OIDC provider configuration (Cognito, Identity Center, or any OIDC-compliant provider).

**Key Methods**:
- `getProviderType()` - "cognito" or "identity-center"
- `getIssuerUrl()` - OIDC issuer URL
- `getAuthorizationEndpoint()` - OAuth2 authorization endpoint
- `getTokenEndpoint()` - OAuth2 token endpoint
- `getUserInfoEndpoint()` - OIDC userinfo endpoint
- `getJwksUri()` - JSON Web Key Set URI
- `getLogoutEndpoint()` - OIDC logout endpoint (for Cognito)
- `getClientId()` - OAuth2 client ID
- `getClientSecretArn()` - AWS Secrets Manager ARN for client secret
- `getRedirectUrl()` - OAuth2 redirect URI
- `getUsernameClaim()` - OIDC claim for username
- `getGroupsClaim()` - OIDC claim for groups
- `usePkce()` - Enable PKCE (Proof Key for Code Exchange)

#### OidcIntegration Interface
Defines application-specific OIDC integration logic.

**Key Methods**:
- `isSupported()` - Whether this application supports OIDC
- `getIntegrationMethod()` - Description of integration approach
- `getEnvironmentVariables(config)` - Environment variables for container
- `getConfigurationFile(config)` - Configuration file content
- `getConfigurationFilePath()` - Path to configuration file
- `getUserDataCommands(config, context)` - EC2 UserData commands
- `getPostDeploymentInstructions()` - Post-deployment instructions

### OIDC Providers

#### Amazon Cognito
**Standalone user directory with built-in OIDC support**

**Endpoints**:
- Authorization: `https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize`
- Token: `https://{domain}.auth.{region}.amazoncognito.com/oauth2/token`
- UserInfo: `https://{domain}.auth.{region}.amazoncognito.com/oauth2/userInfo`
- Logout: `https://{domain}.auth.{region}.amazoncognito.com/logout`
- JWKS: `https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json`

**Claims**:
- Username: `sub` (recommended) or `cognito:username`
- Groups: `cognito:groups`

**Use Cases**:
- Customer-facing applications
- B2C authentication
- Mobile/web app user management
- Multi-tenant SaaS applications

#### IAM Identity Center
**Enterprise SSO service - COMPLETELY SEPARATE from Cognito**

**Endpoints**:
- Authorization: `https://{tenant}.awsapps.com/start/oauth2/authorize`
- Token: `https://{tenant}.awsapps.com/token`

**Claims**:
- Username: `preferred_username`
- Groups: `groups` (NOT "cognito:groups")

**Use Cases**:
- Enterprise internal applications
- Corporate directory integration (Active Directory, Okta, etc.)
- B2B authentication
- Multi-account AWS organization access

**IMPORTANT**: Cognito and IAM Identity Center are **two completely separate authentication systems**:
- Different endpoints
- Different claims
- Different use cases
- NOT interchangeable

---

## Security Considerations

### Client Secret Management
All client secrets are stored in **AWS Secrets Manager** and retrieved at runtime:

```bash
# Retrieve secret at container/EC2 startup
export APP_OIDC_CLIENT_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id arn:aws:secretsmanager:us-east-1:123456789012:secret:app-oidc-secret \
  --query SecretString --output text)
```

**Benefits**:
- Secrets never stored in code or configuration files
- IAM-based access control
- Automatic encryption at rest
- Audit logging via CloudTrail
- Secret rotation support

### PKCE (Proof Key for Code Exchange)
All integrations support PKCE for enhanced security:
- Mitigates authorization code interception attacks
- Required for mobile and SPA applications
- Recommended for all OAuth2 flows

**Default**: `usePkce() = true`

### Network Security
- All OIDC endpoints use HTTPS/TLS
- Token validation uses JWKS (JSON Web Key Set)
- Tokens are validated on every request
- Session management via secure cookies

### Compliance
OIDC integration supports compliance requirements:
- **SOC2 CC6.1**: User authentication and authorization
- **HIPAA §164.312(d)**: Person or entity authentication
- **PCI-DSS Req 8.2**: Multi-factor authentication support
- **GDPR Art. 32**: Authentication security controls

---

## Security Best Practices

### 1. Always Use HTTPS

**Option A: With Custom Domain (Recommended for Production)**
```json
{
  "enableSsl": true,
  "domain": "example.com",
  "subdomain": "jenkins"
}
```

**Option B: Without Custom Domain (Using Private CA)**
```json
{
  "enableSsl": true,
  "authMode": "alb-oidc"
}
```

When `enableSsl: true` is set without a domain, CloudForge provisions an AWS Private CA certificate for the ALB DNS name. This is useful for:
- Development/testing environments
- Quick deployments without domain registration
- Internal applications where browser warnings are acceptable

**Note:** Private CA certificates cost ~$400/month per CA. The CA is automatically deleted when the stack is destroyed (RemovalPolicy.DESTROY).

### 2. Enable MFA for Production

```json
{
  "securityProfile": "PRODUCTION",
  "cognitoMfaEnabled": true
}
```

### 3. Use Group-Based Access Control

```json
{
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "Admins",
  "cognitoUserGroupName": "Developers"
}
```

### 4. Create Initial Admin User

```json
{
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

This creates an admin user and sends them a temporary password via email.

---

## Troubleshooting

### Common Issues

#### "Required String parameter 'client_id' is not present"

**Cause:** Cognito logout endpoint requires special parameters that the standard OIDC spec doesn't include.

**Solution:** CloudForge 3.0.0+ handles this automatically using manual server configuration with `endSessionUrl`.

#### "redirect_uri_mismatch"

**Cause:** The callback URL doesn't match what's configured in Cognito.

**Solution:** Verify the FQDN in your deployment context matches the Cognito app client callback URLs. If using Private CA without a custom domain, ensure `enableSsl: true` is set so the callback URL uses HTTPS with the ALB DNS name.

#### Browser shows "Your connection is not private" warning

**Cause:** Using AWS Private CA certificate without a custom domain.

**Solution:** This is expected behavior when using Private CA. The certificate is valid but not trusted by browsers because it's issued by a private CA, not a public CA like Let's Encrypt or DigiCert. Options:
1. Click "Advanced" → "Proceed to site" to continue (acceptable for dev/test)
2. Import the Private CA root certificate into your browser/system trust store
3. Use a custom domain with public DNS validation for production

#### "Invalid username claim"

**Cause:** Cognito uses special claim names like `cognito:groups` that require JMESPath escaping.

**Solution:** CloudForge automatically configures the correct claim names with proper escaping.

#### Users Can Login But Have No Permissions

**Cause:** User is not in any Cognito group.

**Solution:** Add the user to the appropriate Cognito group (Admin or User).

#### "Single entry map expected to configure a org.jenkinsci.plugins.oic.OicServerConfiguration"

**Cause:** JCasC YAML structure is incorrect for the oic-auth plugin.

**Solution:** CloudForge 3.0.0+ uses the correct manual configuration structure:
```yaml
serverConfiguration:
  manual:
    authorizationServerUrl: "..."
    tokenServerUrl: "..."
    # other fields at manual level
clientId: "..."  # at oic level, not under serverConfiguration
```

### Debug Logging

Check Jenkins logs for OIDC issues:

```bash
# ECS/Fargate
aws logs tail /aws/{stackName}/FARGATE/STAGING --follow

# EC2
ssh ec2-user@{instance-ip} 'tail -f /var/log/jenkins/jenkins.log'
```

### Verify OIDC Configuration

Check the generated JCasC configuration:

```bash
# Inside container
cat /var/jenkins_home/casc_configs/oidc.yaml
```

---

## Architecture Diagrams

### ALB-OIDC Flow

```
┌──────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│  User    │────▶│   ALB   │────▶│ Cognito │────▶│ Jenkins │
└──────────┘     └─────────┘     └─────────┘     └─────────┘
     │                │                │              │
     │  1. Access URL │                │              │
     │───────────────▶│                │              │
     │                │ 2. Redirect    │              │
     │◀───────────────│───────────────▶│              │
     │                │                │              │
     │ 3. Login at Cognito             │              │
     │────────────────────────────────▶│              │
     │                │                │              │
     │ 4. Redirect back with token     │              │
     │◀────────────────────────────────│              │
     │                │                │              │
     │                │ 5. Validate token              │
     │                │◀───────────────│              │
     │                │                │              │
     │                │ 6. Forward with headers        │
     │                │───────────────────────────────▶│
```

### Application-OIDC Flow

```
┌──────────┐     ┌─────────┐     ┌─────────┐
│  User    │────▶│ Jenkins │────▶│ Cognito │
└──────────┘     └─────────┘     └─────────┘
     │                │                │
     │  1. Access URL │                │
     │───────────────▶│                │
     │                │                │
     │ 2. Redirect to Cognito          │
     │◀───────────────│                │
     │                │                │
     │ 3. Login at Cognito             │
     │────────────────────────────────▶│
     │                │                │
     │ 4. Callback with auth code      │
     │◀────────────────────────────────│
     │───────────────▶│                │
     │                │                │
     │                │ 5. Exchange code for tokens
     │                │───────────────▶│
     │                │◀───────────────│
     │                │                │
     │ 6. Session created              │
     │◀───────────────│                │
```

---

## References

### AWS Documentation
- [Amazon Cognito User Pools](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools.html)
- [ALB OIDC Authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
- [IAM Identity Center](https://docs.aws.amazon.com/singlesignon/latest/userguide/what-is.html)

### Application Documentation
- [Jenkins OIDC Plugin](https://plugins.jenkins.io/oic-auth/)
- [Jenkins OIDC Plugin Configuration](https://github.com/jenkinsci/oic-auth-plugin/blob/master/docs/configuration/README.md)
- [Jenkins Configuration as Code](https://plugins.jenkins.io/configuration-as-code/)
- [GitLab OmniAuth](https://docs.gitlab.com/ee/administration/auth/oidc.html)
- [Grafana OAuth](https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/generic-oauth/)

### OIDC Specifications
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [PKCE RFC 7636](https://tools.ietf.org/html/rfc7636)

---

**CloudForge 3.0.0** - Enterprise Authentication for Cloud Applications
