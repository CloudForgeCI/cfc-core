# Cognito MFA Setup for Compliance

## Overview

Multi-Factor Authentication (MFA) is **REQUIRED** for compliance with:
- **SOC 2 CC6.2** - Logical Access Controls
- **PCI-DSS Requirement 8.3** - Multi-factor authentication for all access
- **HIPAA §164.312(d)** - Person or entity authentication
- **GDPR Article 32** - Appropriate security measures

CloudForge automatically configures Cognito MFA based on your security profile.

## Quick Start

### 1. Configure MFA in deployment-context.json

```json
{
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "myapp",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

### 2. Deploy

```bash
cdk deploy
```

CloudForge automatically creates the Cognito User Pool with MFA enabled.

### 3. First Login

Users automatically set up MFA on first login:

1. Access your application URL (e.g., `https://jenkins.example.com`)
2. Log in with temporary password from email
3. Change password when prompted
4. Scan QR code with authenticator app (Google Authenticator, Authy, etc.)
5. Enter verification code

MFA is now active.

## Security Profile Defaults

MFA is automatically configured based on your security profile:

| Security Profile | MFA Required | Default Method | Token Validity |
|-----------------|--------------|----------------|----------------|
| **PRODUCTION** | ✅ Yes | Both (TOTP + SMS) | 1 hour |
| **STAGING** | ✅ Yes | Both (TOTP + SMS) | 2 hours |
| **DEV** | ❌ No (Optional) | TOTP only | 12 hours |

To use profile defaults, simply omit `cognitoMfaEnabled` and `cognitoMfaMethod`:

```json
{
  "securityProfile": "production",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "myapp"
}
```

## MFA Methods

| Method | Description | Requirements |
|--------|-------------|--------------|
| **totp** | Authenticator apps only | None - works immediately |
| **sms** | Text message codes | AWS SMS spending limit > $0 |
| **both** | User chooses TOTP or SMS | SMS spending limit for SMS option |

**Recommendation:** Use `"totp"` for production - no SMS costs, no delivery issues.

## SMS MFA Configuration

AWS accounts default to **$0/month SMS spending limit**, blocking all SMS messages.

**To enable SMS MFA:**

1. AWS Console → SNS → Text messaging (SMS) → Sandbox
2. Click "Request production access"
3. Monthly spend: $1-$10
4. Use case: "Multi-factor authentication"
5. Submit (usually instant approval)

Add phone number in deployment context (E.164 format):

```json
{
  "cognitoMfaMethod": "sms",
  "cognitoInitialAdminEmail": "admin@example.com",
  "cognitoInitialAdminPhone": "+12025551234"
}
```

## Complete Configuration Example

```json
{
  "stackName": "jenkins-prod",
  "region": "us-east-1",
  "securityProfile": "production",
  "authMode": "alb-oidc",
  "enableSsl": true,
  "domain": "example.com",
  "subdomain": "jenkins",

  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-auth",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoInitialAdminEmail": "admin@example.com",

  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "Jenkins-Admins",
  "cognitoUserGroupName": "Jenkins-Users"
}
```

## User Management

### Creating Additional Users

**AWS Console:**
1. AWS Console → Cognito → User Pools → Select your pool
2. Users → Create user
3. Email: `user@example.com`
4. Send email invitation: Yes

User receives temporary password and sets up MFA on first login.

**AWS CLI:**

```bash
aws cognito-idp admin-create-user \
  --user-pool-id us-east-1_XXXXXXXXX \
  --username user@example.com \
  --user-attributes Name=email,Value=user@example.com Name=email_verified,Value=true \
  --desired-delivery-mediums EMAIL \
  --region us-east-1
```

### Adding Users to Groups

```bash
aws cognito-idp admin-add-user-to-group \
  --user-pool-id us-east-1_XXXXXXXXX \
  --username user@example.com \
  --group-name Jenkins-Admins \
  --region us-east-1
```

## Verification

### Check MFA Configuration

```bash
# Get User Pool ID
aws cognito-idp list-user-pools --max-results 60 --region us-east-1

# Check MFA status
aws cognito-idp describe-user-pool \
  --user-pool-id us-east-1_XXXXXXXXX \
  --region us-east-1 | jq '.UserPool.MfaConfiguration'
```

**Expected:** `"ON"` (production/staging) or `"OPTIONAL"` (dev)

### Verify User MFA Status

```bash
aws cognito-idp admin-get-user \
  --user-pool-id us-east-1_XXXXXXXXX \
  --username admin@example.com \
  --region us-east-1 | jq '.UserMFASettingList'
```

**Expected:** `["SOFTWARE_TOKEN_MFA"]` or `["SMS_MFA"]`

### Audit All Users

```bash
aws cognito-idp list-users \
  --user-pool-id us-east-1_XXXXXXXXX \
  --region us-east-1 | \
  jq '.Users[] | {Username, MFAConfigured: (.UserMFASettingList != null)}'
```

## Troubleshooting

### SMS Not Sending

**Cause:** SNS Sandbox mode

**Solution:** Exit SNS sandbox (see "SMS MFA Configuration" above)

### MFA Not Required in Production

**Cause:** Deployment context override

**Solution:** Remove `"cognitoMfaEnabled": false` from deployment-context.json to use profile default

### User Pool Already Exists

**Cause:** Production User Pools have `RETAIN` removal policy

**Solution:** Reuse existing pool:

```json
{
  "cognitoAutoProvision": false,
  "cognitoUserPoolId": "us-east-1_XXXXXXXXX",
  "cognitoAppClientId": "1234567890abcdef"
}
```

Find User Pool ID:
```bash
aws cognito-idp list-user-pools --max-results 60 --region us-east-1
```

## Compliance Evidence

For audits, provide:

### MFA Enforcement Policy

```bash
aws cognito-idp describe-user-pool \
  --user-pool-id us-east-1_XXXXXXXXX \
  --region us-east-1 | \
  jq '{UserPoolName: .UserPool.UserPoolName, MfaConfiguration: .UserPool.MfaConfiguration}'
```

### User MFA Compliance

```bash
aws cognito-idp list-users \
  --user-pool-id us-east-1_XXXXXXXXX \
  --region us-east-1 | \
  jq '.Users[] | {Username, MFAConfigured: (.UserMFASettingList != null)}'
```

## References

- **SOC 2**: CC6.2 - Multi-factor Authentication
- **PCI-DSS v4.0**: Requirement 8.3
- **HIPAA**: §164.312(d) - Person or entity authentication
- **GDPR**: Article 32 - Security of processing
- **AWS Cognito MFA**: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-mfa.html

## See Also

- [Identity Center Setup](AWS_IDENTITY_CENTER_SETUP.md) - Enterprise SSO with SAML
- [OIDC Integration Guide](../applications/OIDC.md) - Application-level OIDC
- [Multi-Framework Compliance](../compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
