# OIDC Authentication

How CloudForge authenticates users in front of, or inside, a deployed application.

## Authentication modes

`authMode` (enum `AuthMode` in `cloudforge-core/src/main/java/com/cloudforge/core/enums/`):

| Value | Where authentication happens |
|-------|------------------------------|
| `none` (default) | Nowhere; the application's own login applies. |
| `alb-oidc` | The Application Load Balancer authenticates requests before forwarding them. Works for any application that allows it; the application sees the user through the `x-amzn-oidc-*` headers. |
| `application-oidc` | The application runs the OIDC flow itself through its `OidcIntegration` (for example the Jenkins `oic-auth` plugin). Supports group-to-role mapping and provider logout. |

The legacy value `jenkins-oidc` is accepted as an alias for `application-oidc`.

Each application declares the modes it supports (`ApplicationSpec.getSupportedAuthModes()`); see the
[catalog](README.md#authentication-support). When a deployment requests an unsupported mode, CloudForge switches to
the application's recommended mode and prints a warning.

Both OIDC modes require HTTPS. When `authMode` is `alb-oidc` or `application-oidc`, CloudForge sets
`enableSsl: true`. Without `domain` or `certificateArn`, the certificate is issued by a stack-owned AWS Private CA
for the load balancer DNS name; browsers show a warning because the CA is not publicly trusted.

With `alb-oidc`, every path is protected unless the application declares `protectedPaths()` (for example
WordPress protects only `/wp-admin/*` and `/wp-login.php`), minus any `publicPaths()` it declares.

## Quick start

Cognito, application-level:

```json
{
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "my-app",
  "cognitoMfaEnabled": true,
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

Cognito, ALB-level:

```json
{
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "my-app"
}
```

External OIDC provider:

```json
{
  "authMode": "application-oidc",
  "oidcProvider": "external-idp",
  "oidcIssuer": "https://login.example.com",
  "oidcAuthorizationEndpoint": "https://login.example.com/oauth2/v1/authorize",
  "oidcTokenEndpoint": "https://login.example.com/oauth2/v1/token",
  "oidcUserInfoEndpoint": "https://login.example.com/oauth2/v1/userinfo",
  "oidcClientId": "my-client-id",
  "oidcClientSecretName": "my-app/oidc/client-secret"
}
```

CloudForge Manager as the identity provider:

```json
{
  "authMode": "application-oidc",
  "oidcProvider": "cloudforge-manager",
  "cloudforgeManagerIssuerUrl": "https://manager.example.com",
  "oidcClientId": "my-client-id",
  "oidcClientSecretName": "my-app/oidc/client-secret"
}
```

## Provider selection

For `application-oidc`, the provider is chosen from the configured keys in this order:

1. Cognito: `cognitoAutoProvision` is `true`, or `cognitoUserPoolId` is set.
2. IAM Identity Center (SAML): `autoProvisionIdentityCenter` is `true` and `ssoInstanceArn` is set.
3. CloudForge Manager: `oidcProvider` is `cloudforge-manager` and `cloudforgeManagerIssuerUrl` is set.
4. External provider: `oidcIssuer` is set.

If `oidcProvider` is unset or `none` when an OIDC mode is requested, it defaults to `cognito` and Cognito
auto-provisioning is enabled unless `cognitoUserPoolId` is given.

## Configuration reference

### Core

| Key | Default | Description |
|-----|---------|-------------|
| `authMode` | `none` | `none`, `alb-oidc`, or `application-oidc` |
| `oidcProvider` | `none` (becomes `cognito` for OIDC modes) | `cognito`, `identity-center`, `external-idp`, or `cloudforge-manager` |

### Cognito user pool (auto-provisioned)

| Key | Default | Description |
|-----|---------|-------------|
| `cognitoAutoProvision` | `false` (`true` for OIDC modes without `cognitoUserPoolId`) | Create a user pool in the stack |
| `cognitoDomainPrefix` | `{stackName}-auth` | Hosted UI domain prefix. Lowercased and sanitized, then suffixed with `-{stackName}` |
| `cognitoUserPoolName` | `{stackName}-users` | User pool name |
| `cognitoMfaEnabled` | `false` | Require MFA. Set it explicitly for production deployments |
| `cognitoMfaMethod` | `both` | `totp`, `sms`, or `both` |
| `cognitoSelfSignupEnabled` | security profile default | Allow hosted UI self-registration |
| `cognitoCreateGroups` | `true` | Create an admin group and a user group |
| `cognitoAdminGroupName` | `{applicationId}-Admins` | Admin group name |
| `cognitoUserGroupName` | `{applicationId}-Users` | User group name |
| `cognitoInitialAdminEmail` | `admin@{domain}` | First admin user; Cognito emails a temporary password |
| `cognitoInitialAdminPhone` | — | E.164 phone number for SMS MFA |

Production user pools use `RemovalPolicy.RETAIN`; reuse a retained pool with `cognitoUserPoolId`.

SMS MFA requires an Amazon SNS SMS spending limit above the account default of USD 0.

### Existing Cognito user pool

| Key | Description |
|-----|-------------|
| `cognitoUserPoolId` | Existing user pool ID |
| `cognitoAppClientId` | Existing app client ID |
| `cognitoDomainPrefix` | Existing hosted UI domain prefix |

### External provider (`external-idp`)

| Key | Description |
|-----|-------------|
| `oidcIssuer` | Issuer URL (`https://`) |
| `oidcAuthorizationEndpoint` | Authorization endpoint |
| `oidcTokenEndpoint` | Token endpoint |
| `oidcUserInfoEndpoint` | UserInfo endpoint |
| `oidcClientId` | Client ID registered with the provider |
| `oidcClientSecretName` | Secrets Manager secret name holding the client secret |

### CloudForge Manager (`cloudforge-manager`)

| Key | Description |
|-----|-------------|
| `cloudforgeManagerIssuerUrl` | Public base URL of the Manager install; endpoints are derived from it |
| `oidcClientId` | Client ID registered in Manager |
| `oidcClientSecretName` | Secrets Manager secret name holding the client secret |

### IAM Identity Center

| Key | Description |
|-----|-------------|
| `autoProvisionIdentityCenter` | Provision the Identity Center application |
| `ssoInstanceArn` | Identity Center instance ARN |
| `ssoGroupId`, `identityCenterGroupName`, `ssoTargetAccountId` | Group assignment |

IAM Identity Center and the SAML-based integrations (including Metabase's) are incomplete and should not be relied on
for production deployments.

## Application integrations

`OidcIntegration` implementations live in `cloudforge-core/src/main/java/com/cloudforge/core/oidc/`. The
general applications with an integration are:

| Application | Integration class | Method |
|-------------|-------------------|--------|
| Jenkins | `JenkinsOidcIntegration` | `oic-auth` plugin, JCasC |
| GitLab | `GitLabOidcIntegration` | OmniAuth OpenID Connect |
| Grafana | `GrafanaOidcIntegration` | `generic_oauth` environment variables |
| Mattermost Team | `MattermostGitLabOidcIntegration` | GitLab OAuth settings (`MM_GITLABSETTINGS_*`); no single logout |
| Mattermost Enterprise | `MattermostOidcIntegration` | Native OpenID Connect (`MM_OPENIDSETTINGS_*`) |
| Metabase | `MetabaseSamlIntegration` | SAML |
| CloudForge Manager | `CloudForgeManagerOidcIntegration` | Built-in OIDC client |

CMS platforms have their own integrations (`WordPressOidcIntegration`, `DrupalOidcIntegration`, and so on); see
[CMS.md](CMS.md).

Gitea, SonarQube, Harbor, Nexus, Superset, Drone, Prometheus, Vault, PostgreSQL, and Redis have no integration and
support only `authMode: none`.

### Jenkins

With `application-oidc`, CloudForge writes `/var/jenkins_home/casc_configs/oidc.yaml` and sets
`CASC_JENKINS_CONFIG`. The generated configuration:

- uses the `oic` security realm with `serverConfiguration.manual` endpoints, client secret from
  `${JENKINS_OIDC_CLIENT_SECRET}`, and `logoutFromOpenidProvider: true`;
- sets `endSessionUrl` to the Cognito `/logout` endpoint with `client_id` and `logout_uri`, because Cognito's
  logout endpoint does not follow the standard `end_session_endpoint` parameters
  ([oic-auth-plugin#95](https://github.com/jenkinsci/oic-auth-plugin/issues/95));
- reads groups from the `cognito:groups` claim (JMESPath-escaped);
- when groups are enabled, applies a `projectMatrix` strategy: the admin group gets `Overall/Administer`, the user
  group gets build, configure, create, read, and workspace permissions, and anonymous access is denied;
- when `cognitoCreateGroups` is `false`, uses `loggedInUsersCanDoAnything` with anonymous read disabled;
- enables the Audit Trail plugin, logging to `/var/jenkins_home/logs/audit.log`.

### Interfaces

`OidcConfiguration` carries provider settings (`getProviderType()`, `getIssuerUrl()`, the endpoint getters,
`getJwksUri()`, `getLogoutEndpoint()`, `getClientId()`, `getClientSecretArn()`, `getRedirectUrl()`, `getScopes()`,
claim getters, group names, `usePkce()` (default `true`)).

`OidcIntegration` adapts it to an application: `getEnvironmentVariables(config)`, `getConfigurationFile(config)`,
`getConfigurationFilePath()`, `getUserDataCommands(config, context)`, `getPostDeploymentInstructions()`,
`getOidcCallbackPath()`, and capability flags (`supportsAlbOidc()`, `supportsApplicationOidc()`,
`supportsCognito()`, `supportsIdentityCenterSaml()`).

## Cognito reference

| Endpoint | URL |
|----------|-----|
| Authorization | `https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize` |
| Token | `https://{domain}.auth.{region}.amazoncognito.com/oauth2/token` |
| UserInfo | `https://{domain}.auth.{region}.amazoncognito.com/oauth2/userInfo` |
| Logout | `https://{domain}.auth.{region}.amazoncognito.com/logout` |
| Issuer | `https://cognito-idp.{region}.amazonaws.com/{userPoolId}` |
| JWKS | `https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json` |

Groups are delivered in the `cognito:groups` claim. IAM Identity Center uses different endpoints and a `groups`
claim; the two are not interchangeable.

## Client secrets

Client secrets are stored in AWS Secrets Manager and read by the container or EC2 user data at startup. They are not
written into the CloudFormation template or deployment context.

## Troubleshooting

**`redirect_uri_mismatch`** — the callback URL does not match the Cognito app client. Check that `domain` and
`subdomain` (or the load balancer DNS name when using a Private CA) match the URL users open, and that the URL is
HTTPS.

**Browser certificate warning** — expected with a Private CA certificate. Import the CA certificate into the client
trust store for testing, or configure a public `domain`.

**User can sign in but has no permissions** — add the user to the admin or user Cognito group.

**Jenkins logs** — on Fargate, the application log group is `/aws/ecs/{stackName}/fargate/{securityProfile}` for
`dev` and `staging`; `production` log groups are retained and get a CloudFormation-generated name. On EC2, see
`/var/log/jenkins/jenkins.log`.

## References

- [Amazon Cognito user pools](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools.html)
- [ALB user authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
- [IAM Identity Center](https://docs.aws.amazon.com/singlesignon/latest/userguide/what-is.html)
- [Jenkins OpenID Connect Authentication plugin](https://plugins.jenkins.io/oic-auth/)
- [Jenkins Configuration as Code](https://plugins.jenkins.io/configuration-as-code/)
- [GitLab OpenID Connect](https://docs.gitlab.com/ee/administration/auth/oidc.html)
- [Grafana generic OAuth](https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/generic-oauth/)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [RFC 7636 (PKCE)](https://tools.ietf.org/html/rfc7636)
