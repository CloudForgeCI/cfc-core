# Troubleshooting Identity Center SAML ForbiddenException

## Error
```
https://portal.sso.us-east-1.amazonaws.com/saml/assertion/apl-xxxx?SAMLRequest=...
{"message":"No access","__type":"com.amazonaws.switchboard.portal#ForbiddenException"}
```

## Root Cause Analysis

The ForbiddenException typically means Identity Center is rejecting the SAML assertion request. This can happen for several reasons:

### **CRITICAL BUG FIX (v3.0.1): Wrong Application Provider ARN**

**The Issue:**
CloudForge 3.0.0 was using the wrong application provider ARN:
- ❌ **Used**: `arn:aws:sso::aws:applicationProvider/custom` (FederationProtocol: **OAUTH**)
- ✅ **Should use**: `arn:aws:sso::aws:applicationProvider/custom-saml` (FederationProtocol: **SAML**)

**Impact:**
Identity Center was expecting an OAUTH flow but receiving SAML requests, causing ForbiddenException.

**Fix:**
Updated `IdentityCenterSamlFactory.java:250` to use `custom-saml` provider.

**Verification:**
```bash
# Check your current application
aws sso-admin describe-application --application-arn YOUR_APP_ARN \
  --query 'ApplicationProviderArn'

# Should return:
"arn:aws:sso::aws:applicationProvider/custom-saml"

# If it returns "custom" (without -saml), you need to redeploy with the fix
```

**If You Have Existing Deployments:**
1. Update to CloudForge 3.0.1+
2. Destroy the old stack: `cdk destroy`
3. Redeploy: `cdk deploy`
4. Reconfigure SAML settings in console (ACS URL, Entity ID, attribute mappings)

---

### 1. SAML Application Not Fully Configured (Most Common)

**Check**: The AWS SSO Admin API **CANNOT** configure SAML applications programmatically. Even though CloudForge creates the application shell, you MUST complete the SAML configuration manually in the Identity Center console.

**Verify**:
```bash
# Get application details
aws sso-admin describe-application \
  --application-arn $(aws sso-admin list-applications \
    --instance-arn arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx \
    --query 'Applications[0].ApplicationArn' --output text)
```

**Fix**:
1. Open AWS Console → IAM Identity Center → Applications
2. Find your application (e.g., `stackname-metabase`)
3. Click **Actions** → **Edit configuration**
4. Configure SAML settings:
   - **Application ACS URL**: Check CloudFormation outputs for `SamlAcsUrl`
   - **Application SAML audience**: Check CloudFormation outputs for `SamlEntityId`
5. Click **Actions** → **Edit attribute mappings**
6. Add ALL of these mappings:
   ```
   Subject: ${user:email}  Format: emailAddress
   email: ${user:email}
   firstName: ${user:givenName}
   lastName: ${user:familyName}
   preferred_username: ${user:preferredUsername}
   ```
7. For cognito-saml mode, also add:
   ```
   groups: ${path:cognito:groups}
   ```
8. Click **Save changes**

### 2. Application Assignment Not Configured

**Check**: Even though `AssignmentRequired=false` is set in the code, verify it's actually configured:

```bash
# Get application ARN
APP_ARN=$(aws sso-admin list-applications \
  --instance-arn arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx \
  --query 'Applications[?contains(Name,`YOUR_STACK_NAME`)].ApplicationArn' \
  --output text)

# Check assignment configuration
aws sso-admin get-application-assignment-configuration \
  --application-arn $APP_ARN
```

Expected output:
```json
{
  "AssignmentRequired": false
}
```

**Fix**: If `AssignmentRequired` is `true`, update it:
```bash
aws sso-admin put-application-assignment-configuration \
  --application-arn $APP_ARN \
  --assignment-required false
```

### 3. Trusted Token Issuer Not Configured (cognito-saml mode only)

**Check**: For `cognito-saml` mode, verify the trusted token issuer exists:

```bash
# List trusted token issuers
aws sso-admin list-trusted-token-issuers \
  --instance-arn arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx
```

Should show a Cognito issuer with URL like:
```
https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX
```

**Fix**: If missing, check your deployment configuration:
- `cognitoAutoProvision: true` must be set
- `oidcProvider: "cognito-saml"` must be set
- Redeploy the stack

### 4. Application Grant Not Configured (cognito-saml mode only)

**Check**: Verify the JWT Bearer grant is configured with the correct audience:

```bash
# List application grants
aws sso-admin list-application-grants \
  --application-arn $APP_ARN
```

Should show a JWT Bearer grant with:
- `TrustedTokenIssuerArn`: Points to Cognito trusted token issuer
- `AuthorizedAudiences`: Contains your Cognito App Client ID

**Fix**: If missing or incorrect:
1. Get Cognito App Client ID from CloudFormation outputs
2. Get Trusted Token Issuer ARN from previous step
3. Update the grant (requires custom resource or manual console configuration)

### 5. User Not Authenticated with Identity Center

**Check**: The ForbiddenException can also occur if the user isn't properly authenticated.

**For cognito-saml mode**:
The flow should be:
1. User logs into Cognito (gets JWT token)
2. Application sends JWT to Identity Center
3. Identity Center validates JWT via trusted token issuer
4. Identity Center issues SAML assertion

**For identity-center-saml mode**:
The flow should be:
1. User logs into Identity Center directly
2. Identity Center issues SAML assertion

**Fix**: Test the authentication flow:
- For cognito-saml: First test login to Cognito User Pool
- For identity-center-saml: First test login to Identity Center portal

## Verification Steps

### Step 1: Check CloudFormation Outputs

```bash
aws cloudformation describe-stacks \
  --stack-name YOUR_STACK_NAME \
  --query 'Stacks[0].Outputs[?contains(OutputKey,`Saml`)]'
```

Look for:
- `SamlAcsUrl`: The callback URL for SAML responses
- `SamlEntityId`: The SAML audience/entity ID
- `SamlIdpMetadataUrl`: The Identity Center metadata URL
- `SamlPostDeployment`: Manual configuration instructions

### Step 2: Verify SAML Application Configuration

```bash
# Get application ARN
APP_ARN=$(aws sso-admin list-applications \
  --instance-arn arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx \
  --query 'Applications[?contains(Name,`YOUR_STACK_NAME`)].ApplicationArn' \
  --output text)

# Describe application
aws sso-admin describe-application --application-arn $APP_ARN

# Check grants
aws sso-admin list-application-grants --application-arn $APP_ARN

# Check assignment config
aws sso-admin get-application-assignment-configuration --application-arn $APP_ARN
```

### Step 3: Test SAML Metadata

```bash
# Get metadata URL from CloudFormation outputs
METADATA_URL=$(aws cloudformation describe-stacks \
  --stack-name YOUR_STACK_NAME \
  --query 'Stacks[0].Outputs[?OutputKey==`SamlIdpMetadataUrl`].OutputValue' \
  --output text)

# Fetch metadata (should return XML)
curl $METADATA_URL
```

If this returns XML with SAML metadata, the Identity Center side is configured correctly.

### Step 4: Check Application Logs

Look for SAML errors in your application logs:
```bash
# For ECS/Fargate
aws logs tail /aws/YOUR_STACK_NAME/FARGATE/STAGING --follow

# For EC2
ssh ec2-user@instance-ip 'tail -f /var/log/app/app.log'
```

## Quick Fixes

### Fix 1: Complete Manual SAML Configuration
This is required due to AWS API limitations:
1. IAM Identity Center Console → Applications → Your app
2. Actions → Edit configuration → Set ACS URL and Entity ID
3. Actions → Edit attribute mappings → Add all required mappings
4. Save changes

### Fix 2: Verify AssignmentRequired=false
```bash
aws sso-admin put-application-assignment-configuration \
  --application-arn $APP_ARN \
  --assignment-required false
```

### Fix 3: Check Identity Center Application Status
```bash
# Application should be ENABLED
aws sso-admin describe-application --application-arn $APP_ARN \
  --query 'Status'
```

## Configuration Checklist

- [ ] SAML application created in Identity Center
- [ ] Application status is ENABLED
- [ ] ACS URL configured manually in console
- [ ] Entity ID configured manually in console
- [ ] Attribute mappings configured manually in console
- [ ] AssignmentRequired set to false
- [ ] (cognito-saml only) Trusted token issuer created
- [ ] (cognito-saml only) Application grant configured with correct audience
- [ ] (cognito-saml only) Cognito User Pool exists and has users
- [ ] (identity-center-saml only) Users exist in Identity Center identity store

## References

- See: `cloudforge-api/src/main/java/com/cloudforgeci/api/security/IdentityCenterSamlFactory.java:580-593`
- See: `docs/setup/AWS_IDENTITY_CENTER_SETUP.md`
- AWS Docs: https://docs.aws.amazon.com/singlesignon/latest/userguide/samlapps.html
