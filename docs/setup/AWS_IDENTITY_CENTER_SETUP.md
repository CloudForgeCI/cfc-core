# AWS IAM Identity Center Setup

How to use AWS IAM Identity Center (formerly AWS SSO) as the identity provider for a CloudForge
deployment. For most deployments, a CloudForge-provisioned Cognito user pool
(`oidcProvider: "cognito"`, `cognitoAutoProvision: true`) is simpler; see
[Cognito MFA setup](COGNITO_MFA_COMPLIANCE_SETUP.md).

CloudForge supports Identity Center in two ways:

| Mode | `authMode` | What CloudForge creates | Status |
|------|------------|-------------------------|--------|
| [ALB OIDC](#alb-oidc-with-identity-center) | `alb-oidc` | ALB listener rule that authenticates against OIDC endpoints you supply, plus a placeholder client-secret | Supported |
| [SAML auto-provisioning](#saml-auto-provisioning) | `application-oidc` | A custom SAML 2.0 application in Identity Center and a Secrets Manager entry with the IdP metadata URL | Partial; manual console steps required |

## Prerequisites

- AWS Organizations enabled, and Identity Center enabled in the management account or a delegated
  administrator account.
- AWS CLI v2 with permissions for `sso-admin`, `identitystore`, and `secretsmanager`.
- The instance ARN and identity store ID:

  ```bash
  aws sso-admin list-instances \
    --query 'Instances[0].[InstanceArn,IdentityStoreId]' --output text
  ```

  The ARN has the form `arn:aws:sso:::instance/ssoins-xxxxxxxxxxxxxxxx`.

## Create a group and add users

```bash
IDENTITY_STORE_ID="d-xxxxxxxxxx"

GROUP_ID=$(aws identitystore create-group \
  --identity-store-id "$IDENTITY_STORE_ID" \
  --display-name "App-Users" \
  --query 'GroupId' --output text)

aws identitystore create-group-membership \
  --identity-store-id "$IDENTITY_STORE_ID" \
  --group-id "$GROUP_ID" \
  --member-id UserId=<user-id>
```

Assign the group to the application you register below (Identity Center console → Applications →
your application → Assign users and groups).

Enforce MFA in Identity Center itself (Settings → Authentication → Multi-factor authentication);
CloudForge does not configure it.

---

## ALB OIDC with Identity Center

In this mode the Application Load Balancer authenticates every request before it reaches the
application (`OidcAuthenticationFactory`). It works with any OIDC provider, including Identity
Center, Okta, and Auth0.

1. Register an OIDC/OAuth 2.0 application with your identity provider:
   - Redirect URL: `https://<fqdn>/oauth2/idpresponse`
   - Grant type: authorization code
   - Scopes: `openid` (plus `email`, `profile` as needed)
2. Copy the issuer, authorization, token, and user-info endpoints and the client ID.
3. Add them to the deployment context:

   ```json
   {
     "authMode": "alb-oidc",
     "oidcProvider": "identity-center",
     "enableSsl": true,
     "domain": "example.com",
     "subdomain": "app",
     "oidcIssuer": "https://<issuer>",
     "oidcAuthorizationEndpoint": "https://<issuer>/authorize",
     "oidcTokenEndpoint": "https://<issuer>/token",
     "oidcUserInfoEndpoint": "https://<issuer>/userinfo",
     "oidcClientId": "<client-id>",
     "oidcClientSecretName": "my-stack/app/oidc/client-secret"
   }
   ```

   All five endpoint/client fields are required for this path. `oidcClientSecretName` defaults to
   `<stackName>/jenkins/oidc/client-secret`.

4. Deploy. CloudForge creates the secret with a placeholder value. Replace it with the real client
   secret:

   ```bash
   aws secretsmanager put-secret-value \
     --secret-id my-stack/app/oidc/client-secret \
     --secret-string "<client-secret>"
   ```

   If a secret with that name already exists, the deployment fails; delete it or choose another name.

If `ssoInstanceArn` is also set, CloudForge does not create the placeholder secret. Create
`oidcClientSecretName` yourself before deploying.

### Endpoints derived from `ssoInstanceArn`

If `authMode` is `alb-oidc`, no `oidcIssuer` is set, and `ssoInstanceArn` is set, CloudForge builds
endpoints of the form `https://portal.sso.<region>.amazonaws.com/saml/assertion/<instance-id>` and uses
the account ID as the client ID. `IdentityCenterFactory` creates a placeholder secret at
`<stackName>/<applicationId>/oidc/client-secret`. This mode is kept for compatibility, logs a
warning, and does not work with most Identity Center configurations. Use explicit endpoints instead.

---

## SAML auto-provisioning

`IdentityCenterSamlFactory` creates a custom SAML 2.0 application in Identity Center when all of
these hold:

- `authMode` is `application-oidc`,
- `autoProvisionIdentityCenter` is `true`,
- `ssoInstanceArn` is set (synthesis fails without it),
- the application's `OidcIntegration` returns `true` from `supportsIdentityCenterSaml()`. In the
  bundled catalog this is Metabase.

```json
{
  "applicationId": "metabase",
  "authMode": "application-oidc",
  "oidcProvider": "identity-center",
  "autoProvisionIdentityCenter": true,
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxxxxxx",
  "enableSsl": true,
  "domain": "example.com",
  "subdomain": "metabase"
}
```

The factory stores the IdP metadata URL
(`https://portal.sso.<region>.amazonaws.com/saml/metadata/<instance-id>`) and SSO URL in Secrets
Manager at `<stackName>/<applicationId>/saml/idp-config`. The synthesis log prints the ACS URL and
entity ID.

After deployment, finish the setup in the Identity Center console:

1. Applications → select the created application.
2. Enter the ACS URL and entity ID (audience) from the synthesis log, and map the `email`,
   `firstName`, `lastName`, and `groups` attributes.
3. Assign users or groups.

The application grant configuration is not implemented yet, so these console steps are required.

---

## Configuration reference

| Key | Used by | Purpose |
|-----|---------|---------|
| `authMode` | all | `alb-oidc` or `application-oidc`. |
| `oidcProvider` | wizard, SAML selection | `identity-center` for these modes. |
| `oidcIssuer`, `oidcAuthorizationEndpoint`, `oidcTokenEndpoint`, `oidcUserInfoEndpoint`, `oidcClientId` | ALB OIDC | Provider endpoints and client ID. |
| `oidcClientSecretName` | ALB OIDC | Secrets Manager name for the client secret. |
| `ssoInstanceArn` | SAML, derived endpoints, compliance checks | Identity Center instance ARN. |
| `autoProvisionIdentityCenter` | SAML | Create the SAML application. |

`ssoGroupId`, `ssoTargetAccountId`, and `identityCenterGroupName` are accepted in the deployment
context but no factory currently reads them.

The HIPAA §164.312(d), PCI-DSS Req 8.3, and FedRAMP IA-2(1) MFA checks pass for `alb-oidc` and
`application-oidc` deployments when either `ssoInstanceArn` is set or Cognito is auto-provisioned with
`cognitoMfaEnabled: true`. Setting `ssoInstanceArn` does not verify that Identity Center enforces MFA;
enforce it in Identity Center.

## Troubleshooting

**`ssoInstanceArn is required when autoProvisionIdentityCenter = true`** — add the instance ARN, or
turn off `autoProvisionIdentityCenter`.

**Log says "ALB-OIDC enabled but no OIDC configuration provided"** — one of the five endpoint/client
fields is missing.

**ALB returns 561 after sign-in** — the client secret in Secrets Manager is still the placeholder, or
the redirect URL registered with the provider does not match `https://<fqdn>/oauth2/idpresponse`.

**"AWS SSO is not enabled"** — run the commands from the Organizations management account or a
delegated administrator account.

## References

- [IAM Identity Center](https://docs.aws.amazon.com/singlesignon/latest/userguide/what-is.html)
- [Identity Center SAML applications](https://docs.aws.amazon.com/singlesignon/latest/userguide/samlapps.html)
- [ALB user authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
- [OIDC integrations](../applications/OIDC.md)
