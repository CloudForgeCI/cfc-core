# AWS IAM Identity Center Setup

## Overview

CloudForge automatically configures AWS IAM Identity Center (formerly AWS SSO) to provide **SAML 2.0 authentication** for SAML-supported applications.

### Two Integration Types

CloudForge supports two distinct IAM Identity Center integration types for SAML applications:

#### 1. `identity-center-saml`
**Pure Identity Center SAML** - Users managed directly in Identity Center's identity store
- Users stored in: Identity Center identity store
- SAML provider: Identity Center
- Best for: Pure SAML deployments without Cognito

#### 2. `cognito-saml`
**Cognito + Identity Center Hybrid** - Cognito manages users, Identity Center provides SAML
- Users stored in: Cognito User Pool
- SAML provider: Identity Center (using Cognito as trusted token issuer)
- Best for: Applications requiring SAML but you want Cognito's user management features

**IMPORTANT:** Both integration types provide SAML 2.0 credentials to SAML-supported applications (like Metabase Enterprise, GitLab Enterprise, etc.). The difference is where users are stored and managed:
- `identity-center-saml` → Users in Identity Center
- `cognito-saml` → Users in Cognito, SAML from Identity Center

## Prerequisites

### 1. Enable AWS Organizations

Identity Center requires AWS Organizations:

```bash
# Check if Organizations is enabled
aws organizations describe-organization
```

If not enabled, go to AWS Console → AWS Organizations → Create organization

### 2. Enable IAM Identity Center

```bash
# Check if Identity Center is enabled
aws sso-admin list-instances
```

If no instances exist:
1. AWS Console → IAM Identity Center → Enable
2. Choose identity source (default: Identity Center directory)
3. Wait for setup to complete (~2 minutes)

### 3. Get SSO Instance ARN

```bash
aws sso-admin list-instances --query 'Instances[0].InstanceArn' --output text
```

Save this ARN - you'll need it in your deployment configuration.

**Example output:** `arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx`

## Integration Type 1: identity-center-saml

Pure Identity Center SAML - users managed directly in Identity Center's identity store.

### Configuration

```json
{
  "authMode": "application-oidc",
  "oidcProvider": "identity-center-saml",
  "autoProvisionIdentityCenter": true,
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
  "identityCenterInitialAdminEmail": "admin@example.com",
  "identityCenterGroups": ["Administrators", "Analysts", "Viewers"]
}
```

### How It Works

The `identity-center-saml` flow provides SAML credentials directly from Identity Center:

```
User Login
    ↓
Identity Center Identity Store (authentication, user/group management)
    ↓
Identity Center (provides SAML assertions)
    ↓
Application (receives SAML 2.0 credentials)
```

**Key Point:** Both users and SAML credentials come from Identity Center.

### Deploy

```bash
cdk deploy
```

CloudForge automatically:
- Creates groups in Identity Center identity store
- Creates initial admin user
- Adds admin to Administrators group
- Creates SAML application in Identity Center
- Configures SAML attributes and mappings

### Managing Users

**AWS Console:**
1. IAM Identity Center → Users → Add user
2. Email: `user@example.com`
3. First name, Last name
4. Send email invitation

**AWS CLI:**

```bash
# Get identity store ID
IDENTITY_STORE_ID=$(aws sso-admin list-instances --query 'Instances[0].IdentityStoreId' --output text)

# Create user
aws identitystore create-user \
  --identity-store-id $IDENTITY_STORE_ID \
  --user-name "user@example.com" \
  --display-name "User Name" \
  --name '{"GivenName":"User","FamilyName":"Name"}' \
  --emails '[{"Value":"user@example.com","Type":"work","Primary":true}]'
```

### Adding Users to Groups

```bash
# List groups
aws identitystore list-groups --identity-store-id $IDENTITY_STORE_ID

# Get user ID
USER_ID=$(aws identitystore list-users \
  --identity-store-id $IDENTITY_STORE_ID \
  --filters AttributePath=UserName,AttributeValue=user@example.com \
  --query 'Users[0].UserId' --output text)

# Get group ID
GROUP_ID=$(aws identitystore list-groups \
  --identity-store-id $IDENTITY_STORE_ID \
  --filters AttributePath=DisplayName,AttributeValue=Administrators \
  --query 'Groups[0].GroupId' --output text)

# Add user to group
aws identitystore create-group-membership \
  --identity-store-id $IDENTITY_STORE_ID \
  --group-id $GROUP_ID \
  --member-id UserId=$USER_ID
```

## Integration Type 2: cognito-saml

Cognito + Identity Center hybrid - Cognito manages users, Identity Center provides SAML credentials.

**When to use:**
- Application requires SAML 2.0 authentication
- You want Cognito's user management features (API, MFA, triggers, etc.)
- You need automated user provisioning (Cognito API is more comprehensive)

### Configuration

```json
{
  "authMode": "application-oidc",
  "oidcProvider": "cognito-saml",
  "autoProvisionIdentityCenter": true,
  "cognitoAutoProvision": true,
  "cognitoCreateGroups": true,
  "cognitoGroups": ["Administrators", "Analysts", "Viewers"],
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
  "cognitoDomainPrefix": "myapp-auth",
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

### How It Works

The `cognito-saml` flow provides SAML credentials from Identity Center, with users stored in Cognito:

```
User Login
    ↓
Cognito User Pool (authentication, MFA, user/group management)
    ↓
Identity Center (acts as trusted token issuer, provides SAML assertions)
    ↓
Application (receives SAML 2.0 credentials with user attributes from Cognito)
```

**Key Point:** Identity Center issues the SAML credentials, but user data comes from Cognito.

### Deploy

```bash
cdk deploy
```

CloudForge automatically:
- Creates Cognito User Pool with groups
- Creates SAML application in Identity Center
- Configures Trusted Token Issuer (Cognito → Identity Center)
- Maps Cognito groups to SAML attributes

### Managing Users

See [Cognito MFA Setup](COGNITO_MFA_COMPLIANCE_SETUP.md) - users are managed in Cognito User Pool.

## Complete Configuration Examples

### Metabase with identity-center-saml

```json
{
  "stackName": "metabase-prod",
  "region": "us-east-1",
  "securityProfile": "production",
  "authMode": "application-oidc",
  "oidcProvider": "identity-center-saml",
  "enableSsl": true,
  "domain": "example.com",
  "subdomain": "metabase",

  "autoProvisionIdentityCenter": true,
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
  "identityCenterInitialAdminEmail": "admin@example.com",
  "identityCenterGroups": ["Administrators", "Analysts", "Viewers"]
}
```

### Metabase with cognito-saml

```json
{
  "stackName": "metabase-prod",
  "region": "us-east-1",
  "securityProfile": "production",
  "authMode": "application-oidc",
  "oidcProvider": "cognito-saml",
  "enableSsl": true,
  "domain": "example.com",
  "subdomain": "metabase",

  "autoProvisionIdentityCenter": true,
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-auth",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoInitialAdminEmail": "admin@example.com",
  "cognitoCreateGroups": true,
  "cognitoGroups": ["Administrators", "Analysts", "Viewers"]
}
```

## Verification

### Check SAML Application

```bash
# List applications
aws sso-admin list-applications \
  --instance-arn arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx
```

### Check Users and Groups

```bash
# Get identity store ID
IDENTITY_STORE_ID=$(aws sso-admin list-instances --query 'Instances[0].IdentityStoreId' --output text)

# List users
aws identitystore list-users --identity-store-id $IDENTITY_STORE_ID

# List groups
aws identitystore list-groups --identity-store-id $IDENTITY_STORE_ID

# List group memberships
aws identitystore list-group-memberships \
  --identity-store-id $IDENTITY_STORE_ID \
  --group-id <group-id>
```

## Troubleshooting

### "Organizations is not enabled"

**Cause:** Identity Center requires AWS Organizations

**Solution:**
1. AWS Console → AWS Organizations
2. Create organization
3. Wait for setup to complete
4. Re-enable Identity Center

### "SSO instance not found"

**Cause:** Identity Center not enabled

**Solution:**
1. AWS Console → IAM Identity Center
2. Click "Enable"
3. Choose identity source
4. Wait for setup (~2 minutes)

### "Cannot create SAML application"

**Cause:** Incorrect SSO instance ARN

**Solution:**
Verify ARN format:
```bash
aws sso-admin list-instances
```
Should return: `arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx`

### "User already exists"

**Cause:** User email already in Identity Center

**Solution:**
Use existing user or delete from Identity Center console first

## Security Best Practices

### Enable MFA

1. IAM Identity Center → Settings → Authentication
2. Multi-factor authentication → Configure
3. Require MFA for all users

### Session Duration

1. IAM Identity Center → Settings → Authentication
2. Session duration → Set to 1-8 hours
3. Production: Use 1-2 hours maximum

### Access Logging

CloudForge automatically enables CloudWatch logging for authentication events. Review logs:

```bash
aws logs tail /aws/sso/applications/<app-id> --follow
```

## Comparison: identity-center-saml vs cognito-saml

| Feature | `identity-center-saml` | `cognito-saml` |
|---------|------------------------|----------------|
| **User Storage** | Identity Center identity store | Cognito User Pool |
| **Group Management** | Identity Center groups | Cognito groups |
| **MFA** | Identity Center MFA settings | Cognito MFA (TOTP, SMS) |
| **SAML Provider** | Identity Center | Identity Center (Cognito as token issuer) |
| **User Management API** | Identity Store API | Cognito API (more comprehensive) |
| **Complexity** | Simpler (single system) | More components (Cognito + Identity Center) |
| **Best For** | Pure SAML deployments | SAML apps needing Cognito features |

## References

- **IAM Identity Center**: https://docs.aws.amazon.com/singlesignon/latest/userguide/what-is.html
- **Identity Store API**: https://docs.aws.amazon.com/singlesignon/latest/IdentityStoreAPIReference/welcome.html
- **Trusted Token Issuers**: https://docs.aws.amazon.com/singlesignon/latest/userguide/trustedidentitypropagation.html

## See Also

- [Cognito MFA Setup](COGNITO_MFA_COMPLIANCE_SETUP.md) - For hybrid approach
- [OIDC Integration Guide](../applications/OIDC.md) - Application-level OIDC
- [Metabase Setup](../guides/applications/metabase.md) - SAML configuration example
