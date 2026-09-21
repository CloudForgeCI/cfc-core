# Cognito MFA Setup for Compliance

How to configure multi-factor authentication (MFA) on a CloudForge-provisioned Amazon Cognito user
pool, and how to roll it out to users.

MFA maps to these controls:

- SOC 2 CC6.2 (logical access)
- PCI-DSS Requirement 8.3 (multi-factor authentication)
- HIPAA §164.312(d) (person or entity authentication)
- GDPR Article 32 (security of processing)

CloudForge's PCI-DSS, HIPAA, and FedRAMP rule sets check MFA for `alb-oidc` and `application-oidc`
deployments. The check passes when `cognitoAutoProvision` and `cognitoMfaEnabled` are both `true`, or
when `ssoInstanceArn` is set (see [Identity Center setup](AWS_IDENTITY_CENTER_SETUP.md)).

## Configuration

`CognitoAuthenticationFactory` reads these deployment-context keys:

| Key | Default | Effect |
|-----|---------|--------|
| `oidcProvider` | `none` | Set to `cognito`. |
| `cognitoAutoProvision` | `false` | Create a user pool, app client, and Hosted UI domain. |
| `cognitoDomainPrefix` | — | Globally unique Hosted UI domain prefix. |
| `cognitoMfaEnabled` | `false` | `true` sets the pool's MFA configuration to **required**; `false` sets it to **optional** (users may enroll, but are not forced to). |
| `cognitoMfaMethod` | `both` | `totp`, `sms`, or `both`. Selects the enabled second factors. An unrecognized value falls back to `totp`. |
| `cognitoInitialAdminEmail` | — | Creates an initial user; Cognito emails a temporary password. |
| `cognitoInitialAdminPhone` | — | E.164 phone number for the initial user (required for SMS MFA). |
| `cognitoCreateGroups` | `true` | Create admin and user groups; the initial user joins the admin group. |
| `cognitoSelfSignupEnabled` | profile default | Allow self-registration. DEV allows it; STAGING and PRODUCTION do not. |

Example:

```json
{
  "authMode": "alb-oidc",
  "oidcProvider": "cognito",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "myapp-auth",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

When SMS is enabled (`sms` or `both`), CloudForge also creates an IAM role that lets Cognito publish
through SNS, and auto-verifies phone numbers. In PRODUCTION the user pool (and SMS role) is retained on
stack deletion; reuse it with `cognitoUserPoolId`.

## Rolling out required MFA

With `cognitoMfaEnabled: true`, every user must complete an MFA challenge at sign-in, including the
first one. Users enroll a TOTP authenticator or phone number during that first sign-in when the login
UI supports it.

If your login flow cannot enroll users, use a staged rollout:

1. Deploy with `cognitoMfaEnabled: false`. Second factors are still enabled, so users can enroll.
2. Have each user sign in, change the temporary password, and register a TOTP authenticator.
3. Confirm enrollment:

   ```bash
   aws cognito-idp admin-get-user \
     --user-pool-id <user-pool-id> \
     --username <email> \
     --query 'UserMFASettingList'
   ```

   Expect `["SOFTWARE_TOKEN_MFA"]` (or `SMS_MFA`).

4. Redeploy with `cognitoMfaEnabled: true`.

Make the final change through the deployment context, not only with
`aws cognito-idp set-user-pool-mfa-config`. The CloudFormation template owns the pool's MFA setting,
and a later deployment resets any change made directly in the console or CLI.

While `cognitoMfaEnabled` is `false`, the PCI-DSS and HIPAA MFA checks fail. Under
`complianceMode: enforce` this blocks synthesis, so use `advisory` for the enrollment window or enroll
users in a non-production stack first.

### Adding users later

Create users with the CLI or console. With MFA required, each new user enrolls at first sign-in:

```bash
aws cognito-idp admin-create-user \
  --user-pool-id <user-pool-id> \
  --username newuser@example.com \
  --user-attributes Name=email,Value=newuser@example.com Name=email_verified,Value=true
```

## SMS MFA

SMS delivery goes through Amazon SNS. New accounts are in the SNS SMS sandbox and have a low spending
limit, which blocks most messages.

1. Check sandbox status:

   ```bash
   aws sns get-sms-sandbox-account-status
   ```

2. Request production access (SNS console → Text messaging (SMS) → Sandbox) and, if needed, raise the
   account SMS spending limit.
3. Give each SMS user a verified E.164 phone number:

   ```bash
   aws cognito-idp admin-update-user-attributes \
     --user-pool-id <user-pool-id> \
     --username user@example.com \
     --user-attributes Name=phone_number,Value=+15555550100 Name=phone_number_verified,Value=true
   ```

Use `cognitoMfaMethod: "totp"` to avoid the SNS dependency.

## Audit evidence

```bash
# MFA configuration: expect "ON" when required
aws cognito-idp get-user-pool-mfa-config --user-pool-id <user-pool-id>

# Per-user enrollment
aws cognito-idp list-users --user-pool-id <user-pool-id> \
  --query 'Users[].Username' --output text | tr '\t' '\n' | while read -r u; do
    aws cognito-idp admin-get-user --user-pool-id <user-pool-id> --username "$u" \
      --query '{User: Username, MFA: UserMFASettingList}'
  done
```

## Troubleshooting

**SMS codes never arrive** — the account is still in the SNS sandbox or has hit its SMS spending
limit. The synthesis log prints a warning whenever SMS MFA is enabled.

**User cannot complete SMS MFA setup** — the user has no verified `phone_number`. Add one as shown
above, or switch that user to TOTP.

**MFA setting reverted after a deploy** — the pool's MFA configuration was changed outside
CloudFormation. Set `cognitoMfaEnabled` in the deployment context instead.

## References

- [Cognito user pool MFA](https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-mfa.html)
- [SNS SMS sandbox](https://docs.aws.amazon.com/sns/latest/dg/sns-sms-sandbox.html)
- [OIDC integrations](../applications/OIDC.md)
