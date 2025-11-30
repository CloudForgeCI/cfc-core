# CloudForge OIDC Integration Framework

This directory contains the universal OIDC integration framework for CloudForge 3.0.0, enabling application-level authentication with AWS Cognito and IAM Identity Center.

## Overview

CloudForge provides a flexible OIDC integration system that allows containerized applications to authenticate users against AWS authentication services. This framework separates OIDC configuration (provider-specific) from OIDC integration (application-specific), enabling any application to work with any OIDC provider.

## Architecture

### Core Interfaces

#### 1. OidcConfiguration Interface
Defines OIDC provider configuration (Cognito, Identity Center, or any OIDC-compliant provider).

**Key Methods**:
- `getProviderType()` - "cognito" or "identity-center"
- `getIssuerUrl()` - OIDC issuer URL
- `getAuthorizationEndpoint()` - OAuth2 authorization endpoint
- `getTokenEndpoint()` - OAuth2 token endpoint
- `getUserInfoEndpoint()` - OIDC userinfo endpoint
- `getJwksUri()` - JSON Web Key Set URI
- `getClientId()` - OAuth2 client ID
- `getClientSecretArn()` - AWS Secrets Manager ARN for client secret
- `getRedirectUrl()` - OAuth2 redirect URI
- `getUsernameClaim()` - OIDC claim for username
- `getGroupsClaim()` - OIDC claim for groups
- `usePkce()` - Enable PKCE (Proof Key for Code Exchange)

#### 2. OidcIntegration Interface
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

#### Amazon Cognito (`CognitoOidcConfiguration`)
**Standalone user directory with built-in OIDC support**

**Endpoints**:
- Custom domain: `https://{domain}/oauth2/authorize`
- Cognito domain: `https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize`

**Claims**:
- Username: `cognito:username`
- Groups: `cognito:groups`

**Use Cases**:
- Customer-facing applications
- B2C authentication
- Mobile/web app user management
- Multi-tenant SaaS applications

**Example Configuration**:
```java
OidcConfiguration config = new CognitoOidcConfiguration(
    "us-east-1",                        // AWS region
    "us-east-1_abcdef123",               // User Pool ID
    "my-app",                            // Domain (or custom domain)
    "client-id-from-cognito",            // Client ID
    "arn:aws:secretsmanager:...",        // Client secret ARN
    "https://myapp.example.com/callback"  // Redirect URL
);
```

#### IAM Identity Center (`IdentityCenterOidcConfiguration`)
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

**Example Configuration**:
```java
OidcConfiguration config = new IdentityCenterOidcConfiguration(
    "us-east-1",                         // AWS region
    "my-tenant",                         // Tenant ID
    "d-1234567890",                      // Identity Store ID
    "client-id-from-identity-center",    // Client ID
    "arn:aws:secretsmanager:...",        // Client secret ARN
    "https://myapp.example.com/callback"  // Redirect URL
);
```

**IMPORTANT**: Cognito and IAM Identity Center are **two completely separate authentication systems**:
- Different endpoints
- Different claims
- Different use cases
- NOT interchangeable

### Application Integrations

#### 1. Grafana (`GrafanaOidcIntegration`)
**Integration Method**: Environment variables via `generic_oauth` provider

**Configuration**:
- Uses Grafana's built-in generic OAuth support
- Environment variables: `GF_AUTH_GENERIC_OAUTH_*`
- Auto-create users on first login
- Maps OIDC groups to Grafana roles

**Files**:
- Implementation: [GrafanaOidcIntegration.java](GrafanaOidcIntegration.java)
- Environment file: `/etc/grafana/grafana-env.sh`

**Key Features**:
- PKCE support
- Group/role mapping
- Auto-user creation
- Token refresh

**Example UserData Commands**:
```bash
# Retrieve client secret from Secrets Manager
export GRAFANA_OAUTH_CLIENT_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id arn:aws:secretsmanager:... \
  --query SecretString --output text)

# Create Grafana environment file
cat > /etc/grafana/grafana-env.sh <<'EOF'
export GF_AUTH_GENERIC_OAUTH_ENABLED="true"
export GF_AUTH_GENERIC_OAUTH_CLIENT_ID="client-id"
export GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET="${GRAFANA_OAUTH_CLIENT_SECRET}"
# ... additional configuration
EOF

# Restart Grafana
docker restart grafana
```

#### 2. GitLab (`GitLabOidcIntegration`)
**Integration Method**: Configuration file via `gitlab.rb` (OmniAuth)

**Configuration**:
- Uses GitLab's built-in OmniAuth OpenID Connect provider
- Configuration file: `/etc/gitlab/gitlab.rb`
- Auto-create and auto-link users
- Group synchronization support

**Files**:
- Implementation: [GitLabOidcIntegration.java](GitLabOidcIntegration.java)
- Configuration: `/etc/gitlab/gitlab.rb`

**Key Features**:
- Built-in OIDC discovery
- Auto-link existing users
- Group synchronization
- Admin role assignment

**Example gitlab.rb Configuration**:
```ruby
gitlab_rails['omniauth_enabled'] = true
gitlab_rails['omniauth_allow_single_sign_on'] = ['openid_connect']
gitlab_rails['omniauth_block_auto_created_users'] = false
gitlab_rails['omniauth_auto_link_user'] = ['openid_connect']

gitlab_rails['omniauth_providers'] = [
  {
    name: 'openid_connect',
    label: 'AWS Cognito',
    args: {
      name: 'openid_connect',
      scope: ['openid', 'profile', 'email'],
      response_type: 'code',
      issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abcdef123',
      discovery: true,
      client_auth_method: 'query',
      uid_field: 'cognito:username',
      pkce: true,
      client_options: {
        identifier: 'client-id',
        secret: 'client-secret',
        redirect_uri: 'https://gitlab.example.com/users/auth/openid_connect/callback'
      }
    }
  }
]
```

#### 3. Jenkins (`JenkinsOidcIntegration`)
**Integration Method**: Jenkins Configuration as Code (JCasC) via OIDC plugin

**Configuration**:
- Requires: OpenID Connect Authentication Plugin (`oic-auth`)
- Configuration: Jenkins Configuration as Code YAML
- Auto-create users on first login
- Matrix-based authorization

**Files**:
- Implementation: [JenkinsOidcIntegration.java](JenkinsOidcIntegration.java)
- Configuration: `/var/jenkins_home/casc_configs/oidc.yaml`
- Plugin installer: `/tmp/install-oidc-plugin.sh`

**Key Features**:
- JCasC integration
- Escape hatch for emergency access
- Group/role mapping
- Full user info synchronization

**Example JCasC Configuration**:
```yaml
jenkins:
  securityRealm:
    oic:
      clientId: "client-id"
      clientSecret: "${JENKINS_OIDC_CLIENT_SECRET}"
      wellKnownOpenIDConfigurationUrl: "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abcdef123/.well-known/openid-configuration"
      tokenServerUrl: "https://my-app.auth.us-east-1.amazoncognito.com/oauth2/token"
      authorizationServerUrl: "https://my-app.auth.us-east-1.amazoncognito.com/oauth2/authorize"
      userInfoServerUrl: "https://my-app.auth.us-east-1.amazoncognito.com/oauth2/userInfo"
      userNameField: "cognito:username"
      fullNameFieldName: "name"
      emailFieldName: "email"
      groupsFieldName: "cognito:groups"
      scopes: "openid profile email"
      disableSslVerification: false
      logoutFromOpenidProvider: true
      escapeHatchEnabled: true
      escapeHatchUsername: "admin"
      escapeHatchSecret: "admin"

  authorizationStrategy:
    globalMatrix:
      permissions:
        - "Overall/Administer:authenticated"
        - "Overall/Read:authenticated"
```

**IMPORTANT**: Jenkins requires manual plugin installation:
1. Deploy Jenkins with OIDC configuration
2. Run `/tmp/install-oidc-plugin.sh` to install the OIDC plugin
3. Jenkins will restart and OIDC authentication will be active

## Security Considerations

### Client Secret Management
All client secrets are stored in **AWS Secrets Manager** and retrieved at runtime:

```bash
# Retrieve secret at EC2 instance startup
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

## Usage Example

### 1. Create OIDC Configuration
```java
// For Cognito
OidcConfiguration cognitoConfig = new CognitoOidcConfiguration(
    "us-east-1",
    "us-east-1_abcdef123",
    "my-app",
    "cognito-client-id",
    "arn:aws:secretsmanager:us-east-1:123456789012:secret:cognito-secret",
    "https://myapp.example.com/callback"
);

// For IAM Identity Center
OidcConfiguration identityCenterConfig = new IdentityCenterOidcConfiguration(
    "us-east-1",
    "my-tenant",
    "d-1234567890",
    "identity-center-client-id",
    "arn:aws:secretsmanager:us-east-1:123456789012:secret:ic-secret",
    "https://myapp.example.com/callback"
);
```

### 2. Get Application's OIDC Integration
```java
ApplicationSpec grafanaSpec = new GrafanaApplicationSpec();

// Check if application supports OIDC
if (grafanaSpec.supportsOidcIntegration()) {
    OidcIntegration integration = grafanaSpec.getOidcIntegration();

    // Get environment variables
    Map<String, String> envVars = integration.getEnvironmentVariables(cognitoConfig);

    // Get UserData commands
    List<String> commands = integration.getUserDataCommands(cognitoConfig, ec2Context);

    // Get post-deployment instructions
    String instructions = integration.getPostDeploymentInstructions();
}
```

### 3. Deploy with OIDC
The CloudForge deployment framework automatically integrates OIDC configuration into the deployment process:

```java
// UserData script automatically includes OIDC setup
UserDataBuilder builder = new UserDataBuilder();
ApplicationSpec app = new GrafanaApplicationSpec();
OidcConfiguration oidc = new CognitoOidcConfiguration(...);

// Configure application
app.configureUserData(builder, ec2Context);

// Configure OIDC (if supported)
if (app.supportsOidcIntegration()) {
    OidcIntegration integration = app.getOidcIntegration();
    List<String> oidcCommands = integration.getUserDataCommands(oidc, ec2Context);
    builder.addCommands(oidcCommands.toArray(String[]::new));
}

// Build final UserData script
String userData = builder.build();
```

## Application Support Matrix

| Application | OIDC Support | Integration Method | Auto-Create Users | Group Mapping | Status |
|-------------|--------------|-------------------|-------------------|---------------|--------|
| **Grafana** | ✅ Yes | Environment Variables | ✅ Yes | ✅ Yes | ✅ Implemented |
| **GitLab** | ✅ Yes | Configuration File (gitlab.rb) | ✅ Yes | ✅ Yes | ✅ Implemented |
| **Jenkins** | ✅ Yes | JCasC YAML | ✅ Yes | ✅ Yes | ✅ Implemented |
| **Gitea** | ✅ Yes | Configuration File (app.ini) | ✅ Yes | ✅ Yes | ⏳ In Progress |
| **Drone** | ⏳ Planned | Environment Variables | ✅ Yes | ❌ No | ⏳ Planned |
| **Metabase** | ⏳ Planned | Environment Variables | ✅ Yes | ✅ Yes | ⏳ Planned |
| **Superset** | ⏳ Planned | Configuration File (superset_config.py) | ✅ Yes | ✅ Yes | ⏳ Planned |
| **Prometheus** | ❌ No | N/A - Uses external auth proxy | N/A | N/A | N/A |
| **PostgreSQL** | ❌ No | Database - uses application auth | N/A | N/A | N/A |
| **Redis** | ❌ No | Cache - uses application auth | N/A | N/A | N/A |
| **Nexus** | ⏳ Planned | SAML/OIDC plugin | ✅ Yes | ✅ Yes | ⏳ Planned |
| **Harbor** | ⏳ Planned | Configuration File (harbor.yml) | ✅ Yes | ✅ Yes | ⏳ Planned |
| **Vault** | ❌ No | Enterprise feature only | N/A | N/A | N/A |
| **Mattermost** | ⏳ Planned | Configuration File (config.json) | ✅ Yes | ✅ Yes | ⏳ Planned |

## Future Applications

### Planned OIDC Integrations
- **Gitea**: OIDC via app.ini configuration
- **Drone**: OAuth2 via environment variables
- **SonarQube**: OIDC plugin integration
- **Nexus**: OIDC via security configuration
- **Harbor**: OIDC via harbor.yml
- **Artifactory**: OIDC via system.yaml

### Applications Using External Auth
Some applications don't have native OIDC but can use reverse proxy authentication:
- **Prometheus**: Use oauth2-proxy or nginx with OIDC
- **Alertmanager**: Use oauth2-proxy
- **Netdata**: Use oauth2-proxy

## Troubleshooting

### Common Issues

#### 1. Client Secret Not Found
**Error**: "Secret not found" when retrieving from Secrets Manager

**Solution**: Verify IAM role has `secretsmanager:GetSecretValue` permission:
```json
{
  "Effect": "Allow",
  "Action": "secretsmanager:GetSecretValue",
  "Resource": "arn:aws:secretsmanager:*:*:secret:*-oidc-*"
}
```

#### 2. Redirect URI Mismatch
**Error**: "redirect_uri_mismatch" from OIDC provider

**Solution**:
- Verify redirect URI in OIDC provider matches exactly
- Include protocol (https://), hostname, and path
- No trailing slashes unless provider has them

#### 3. Invalid Claims
**Error**: "Invalid username claim" or "User not found"

**Solution**:
- For Cognito: Use `cognito:username` and `cognito:groups`
- For Identity Center: Use `preferred_username` and `groups`
- Verify claim exists in token (check JWT at jwt.io)

#### 4. Group Mapping Not Working
**Error**: Users can login but don't have proper permissions

**Solution**:
- Verify groups claim is included in token scopes
- Check application's group mapping configuration
- Ensure OIDC provider sends groups claim

#### 5. PKCE Errors
**Error**: "PKCE required" or "Invalid code verifier"

**Solution**:
- Ensure `usePkce() = true` in configuration
- Verify OIDC provider supports PKCE
- Check application properly generates code_challenge

## References

### CloudForge Documentation
- [ApplicationSpec Interface](../interfaces/ApplicationSpec.java)
- [OidcConfiguration Interface](../interfaces/OidcConfiguration.java)
- [OidcIntegration Interface](../interfaces/OidcIntegration.java)

### AWS Documentation
- [Amazon Cognito User Pools](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools.html)
- [IAM Identity Center](https://docs.aws.amazon.com/singlesignon/latest/userguide/what-is.html)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)

### OIDC Specifications
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [PKCE RFC 7636](https://tools.ietf.org/html/rfc7636)

### Application-Specific OIDC Documentation
- [Grafana Generic OAuth](https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/generic-oauth/)
- [GitLab OmniAuth](https://docs.gitlab.com/ee/administration/auth/oidc.html)
- [Jenkins OIDC Plugin](https://plugins.jenkins.io/oic-auth/)

---

**CloudForge 3.0.0** - Universal Application Deployment with Enterprise Authentication
*Making cloud infrastructure deployment painless and secure*
