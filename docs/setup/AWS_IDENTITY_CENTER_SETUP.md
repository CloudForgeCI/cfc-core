# AWS IAM Identity Center Setup

Quick guide for setting up IAM Identity Center (formerly AWS SSO) with OIDC authentication.

## What You Need

- AWS CLI with admin access
- AWS Organizations enabled (Identity Center requirement)
- jq for JSON parsing (`brew install jq`)

## Quick Start

If you have the setup script:

```bash
chmod +x scripts/setup-identity-center.sh

./scripts/setup-identity-center.sh \
  --group-name "Jenkins-Users" \
  --app-name "Jenkins-ALB-OIDC" \
  --jenkins-url "https://jenkins.example.com"
```

This script:
- Enables Identity Center if needed
- Creates a user group
- Registers the OIDC app
- Generates and stores the client secret
- Outputs your config values

## Manual Setup

Prefer to do it manually? Here's how:

### 1. Enable Identity Center

Run this in your management account (where Organizations is enabled):

```bash
aws sso-admin enable-aws-organizations-access
aws sso-admin list-instances
```

You should see:
```json
{
  "Instances": [{
    "InstanceArn": "arn:aws:sso:::instance/ssoins-xxxx",
    "IdentityStoreId": "d-xxxx"
  }]
}
```

Save both values - you'll need them.

### 2. Create a Group

```bash
IDENTITY_STORE_ID="d-xxxx"  # from step 1

GROUP_ID=$(aws identitystore create-group \
  --identity-store-id $IDENTITY_STORE_ID \
  --display-name "Jenkins-Users" \
  --description "Jenkins OIDC access" \
  --query 'GroupId' \
  --output text)

echo "Group ID: $GROUP_ID"
```

Save this Group ID - it's your `ssoGroupId`.

### 3. Add Users

CLI:
```bash
# List users
aws identitystore list-users --identity-store-id $IDENTITY_STORE_ID

# Add to group
aws identitystore create-group-membership \
  --identity-store-id $IDENTITY_STORE_ID \
  --group-id $GROUP_ID \
  --member-id UserId=xxxx
```

Or use the console:
- IAM Identity Center → Groups → Jenkins-Users
- Click "Add users"
- Select users

### 4. Register OIDC App

**Heads up**: Identity Center's OIDC support is limited. You have two options:

#### Option A: Use Cognito (Easier)

Cognito has better OIDC support and is simpler to set up. See the Cognito section below.

#### Option B: Identity Center (Advanced)

> **⚠️ Note:** SAML integration with Identity Center is in active development and may have breaking changes.

If you need Identity Center:

**Via Console:**
- IAM Identity Center → Applications → Add application
- Choose "Add custom SAML 2.0 application"
- Note: True OIDC app type might not be available

**Get endpoints:**
```bash
REGION="us-east-1"
INSTANCE_ID="ssoins-xxxx"

# Your endpoints will be:
echo "Issuer: https://portal.sso.$REGION.amazonaws.com/saml/assertion/$INSTANCE_ID"
echo "Auth: https://portal.sso.$REGION.amazonaws.com/saml/assertion/$INSTANCE_ID/authorize"
echo "Token: https://portal.sso.$REGION.amazonaws.com/saml/assertion/$INSTANCE_ID/token"
echo "UserInfo: https://portal.sso.$REGION.amazonaws.com/saml/assertion/$INSTANCE_ID/userinfo"
```

**Generate and store secret:**
```bash
CLIENT_SECRET=$(openssl rand -base64 32)

aws secretsmanager create-secret \
  --name jenkins/oidc/client-secret \
  --description "OIDC client secret for Jenkins" \
  --secret-string "$CLIENT_SECRET" \
  --region us-east-1
```

### 5. Configure Callback URLs

Set these in your OIDC app:

```
Callback: https://jenkins.example.com/oauth2/idpresponse
Sign-out: https://jenkins.example.com/
```

### 6. Assign Group to App

Via console (CLI support is limited):
1. Your OIDC app → Assign users and groups
2. Select Groups → Jenkins-Users
3. Click Assign

### 7. Collect Config Values

Grab everything you need:

```bash
# Instance ARN
aws sso-admin list-instances --query 'Instances[0].InstanceArn' --output text

# Group ID (if you lost it)
aws identitystore list-groups \
  --identity-store-id $IDENTITY_STORE_ID \
  --filters "AttributePath=DisplayName,AttributeValue=Jenkins-Users" \
  --query 'Groups[0].GroupId' \
  --output text

# Account ID
aws sts get-caller-identity --query 'Account' --output text
```

## Alternative: Cognito (Recommended)

Cognito is easier and has better OIDC support:

### 1. Create User Pool

```bash
USER_POOL_ID=$(aws cognito-idp create-user-pool \
  --pool-name jenkins-users \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=true}" \
  --auto-verified-attributes email \
  --username-attributes email \
  --query 'UserPool.Id' \
  --output text)

# Create domain
USER_POOL_DOMAIN="jenkins-auth-$(date +%s)"
aws cognito-idp create-user-pool-domain \
  --domain $USER_POOL_DOMAIN \
  --user-pool-id $USER_POOL_ID

echo "Pool ID: $USER_POOL_ID"
echo "Domain: $USER_POOL_DOMAIN.auth.us-east-1.amazoncognito.com"
```

### 2. Create App Client

```bash
APP_CLIENT=$(aws cognito-idp create-user-pool-client \
  --user-pool-id $USER_POOL_ID \
  --client-name jenkins-alb \
  --generate-secret \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email profile \
  --callback-urls "https://jenkins.example.com/oauth2/idpresponse" \
  --supported-identity-providers COGNITO \
  --allowed-o-auth-flows-user-pool-client)

CLIENT_ID=$(echo $APP_CLIENT | jq -r '.UserPoolClient.ClientId')
CLIENT_SECRET=$(echo $APP_CLIENT | jq -r '.UserPoolClient.ClientSecret')

echo "Client ID: $CLIENT_ID"
echo "Client Secret: $CLIENT_SECRET"
```

### 3. Store Secret

```bash
aws secretsmanager create-secret \
  --name jenkins/oidc/client-secret \
  --description "Cognito client secret for Jenkins" \
  --secret-string "$CLIENT_SECRET"
```

### 4. Get Endpoints

```bash
REGION="us-east-1"

echo "Issuer: https://cognito-idp.$REGION.amazonaws.com/$USER_POOL_ID"
echo "Auth: https://$USER_POOL_DOMAIN.auth.$REGION.amazoncognito.com/oauth2/authorize"
echo "Token: https://$USER_POOL_DOMAIN.auth.$REGION.amazoncognito.com/oauth2/token"
echo "UserInfo: https://$USER_POOL_DOMAIN.auth.$REGION.amazoncognito.com/oauth2/userInfo"
```

### 5. Create Users

```bash
aws cognito-idp admin-create-user \
  --user-pool-id $USER_POOL_ID \
  --username user@example.com \
  --user-attributes Name=email,Value=user@example.com Name=email_verified,Value=true \
  --temporary-password 'TempPass123!' \
  --message-action SUPPRESS

echo "User created (will need to change password on first login)"
```

## CloudForge Configuration

After setup, use these values in your deployment:

### Identity Center

```json
{
  "authMode": "alb-oidc",
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxx",
  "ssoGroupId": "90d67f97-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "ssoTargetAccountId": "123456789012",
  "enableSsl": true,
  "domain": "example.com",
  "fqdn": "jenkins.example.com"
}
```

### Cognito

For Cognito, you'll need to modify `OidcAuthenticationFactory` to accept custom endpoints, or use Cognito-specific configuration (see Cognito docs).

## Troubleshooting

### "AWS SSO is not enabled"

Must run in management account or delegated admin:
```bash
aws organizations describe-organization
aws sso-admin enable-aws-organizations-access
```

### "IdentityStore is not accessible"

Identity Center isn't fully initialized. Wait a few minutes and try again.

### "Secret not found"

Check if it exists:
```bash
aws secretsmanager describe-secret --secret-id jenkins/oidc/client-secret

# Create if missing
aws secretsmanager create-secret \
  --name jenkins/oidc/client-secret \
  --secret-string "your-client-secret"
```

## Security Tips

**Rotate secrets regularly:**
```bash
aws secretsmanager rotate-secret \
  --secret-id jenkins/oidc/client-secret \
  --rotation-lambda-arn arn:aws:lambda:region:account:function:rotation \
  --rotation-rules AutomaticallyAfterDays=30
```

**Limit group membership** - Only add users who need access

**Enable MFA** - Configure in Identity Center console per-user

**Monitor access** - Turn on ALB access logs:
```bash
aws elbv2 modify-load-balancer-attributes \
  --load-balancer-arn <your-alb-arn> \
  --attributes Key=access_logs.s3.enabled,Value=true \
               Key=access_logs.s3.bucket,Value=my-logs
```

## Verify It Works

Test endpoints:
```bash
# Check OIDC config
curl -v "https://portal.sso.us-east-1.amazonaws.com/saml/assertion/ssoins-xxxx/.well-known/openid-configuration"

# Check secret
aws secretsmanager get-secret-value --secret-id jenkins/oidc/client-secret --query 'SecretString' --output text
```

## Next Steps

1. Deploy CloudForge with OIDC config
2. Test by accessing Jenkins URL
3. Check CloudWatch logs for auth events
4. Set up alerts for failed logins

## Docs

- [Identity Center](https://docs.aws.amazon.com/singlesignon/latest/userguide/what-is.html)
- [Cognito User Pools](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools.html)
- [ALB OIDC](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
