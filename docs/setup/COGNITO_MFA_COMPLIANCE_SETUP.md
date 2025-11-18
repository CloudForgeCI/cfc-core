# Cognito MFA Setup for Compliance (SOC 2, PCI-DSS, HIPAA, GDPR)

## Overview

Multi-Factor Authentication (MFA) is **REQUIRED** for compliance with:
- **SOC 2 CC6.2** - Logical Access Controls
- **PCI-DSS Requirement 8.3** - Multi-factor authentication for all access
- **HIPAA §164.312(d)** - Person or entity authentication
- **GDPR Article 32** - Appropriate security measures

## The Challenge

AWS Cognito has a limitation: when MFA is set to **REQUIRED**, new users cannot complete first-time login because they're forced into an MFA setup loop before they can access the MFA setup screen.

## Compliance-Compliant Solution

### **Phase 1: Initial Deployment (MFA OPTIONAL)**

1. Deploy stack with MFA enabled but OPTIONAL:
   ```json
   {
     "cognitoMfaEnabled": true,
     "cognitoMfaMethod": "totp"
   }
   ```

2. The CDK code will create the User Pool with MFA set to OPTIONAL initially.

### **Phase 2: Admin TOTP Setup**

After deployment, the admin user must set up TOTP:

1. **Login to Application**:
   - Go to your application URL (e.g., `https://jesoc.dev.cloudforgeci.com`)
   - Log in with the temporary password sent to admin email
   - Change password when prompted

2. **Set Up TOTP Authenticator**:
   - After login, you'll be redirected to Jenkins/Application
   - Navigate to Cognito Hosted UI MFA settings:
     ```
     https://[your-cognito-domain].auth.[region].amazoncognito.com/
     ```
   - Or use AWS Console → Cognito → Users → Select user → Enable MFA
   - Scan QR code with authenticator app (Google Authenticator, Authy, Microsoft Authenticator)
   - Enter 6-digit verification code

3. **Verify TOTP is Set Up**:
   ```bash
   aws cognito-idp admin-get-user \
     --user-pool-id [YOUR_USER_POOL_ID] \
     --username [ADMIN_EMAIL] \
     --region us-east-1
   ```

   Look for `"UserMFASettingList": ["SOFTWARE_TOKEN_MFA"]` in the output.

### **Phase 3: Enforce REQUIRED MFA (Compliance Mode)**

Once admin TOTP is configured, make MFA REQUIRED:

```bash
aws cognito-idp set-user-pool-mfa-config \
  --user-pool-id [YOUR_USER_POOL_ID] \
  --mfa-configuration ON \
  --software-token-mfa-configuration Enabled=true \
  --region us-east-1
```

**Verify it's set to REQUIRED:**
```bash
aws cognito-idp get-user-pool-mfa-config \
  --user-pool-id [YOUR_USER_POOL_ID] \
  --region us-east-1
```

Expected output:
```json
{
  "MfaConfiguration": "ON",
  "SoftwareTokenMfaConfiguration": {
    "Enabled": true
  }
}
```

### **Phase 4: Additional Users**

For each new user:

1. **Admin creates user** (via Cognito Console or AWS CLI)
2. **User receives temporary password via email**
3. **Before user can login**, admin must:
   - Set MFA to OPTIONAL temporarily (for this specific user only)
   - User logs in and sets up TOTP
   - Admin sets MFA back to REQUIRED

**Alternative: Use Admin CLI to Set Up User MFA**:
```bash
# 1. Create user
aws cognito-idp admin-create-user \
  --user-pool-id [USER_POOL_ID] \
  --username newuser@example.com \
  --user-attributes Name=email,Value=newuser@example.com Name=email_verified,Value=true \
  --region us-east-1

# 2. Set temporary password
aws cognito-idp admin-set-user-password \
  --user-pool-id [USER_POOL_ID] \
  --username newuser@example.com \
  --password "TemporaryPassword123!" \
  --permanent \
  --region us-east-1

# 3. Temporarily set pool to OPTIONAL
aws cognito-idp set-user-pool-mfa-config \
  --user-pool-id [USER_POOL_ID] \
  --mfa-configuration OPTIONAL \
  --software-token-mfa-configuration Enabled=true \
  --region us-east-1

# 4. User logs in and sets up TOTP via Cognito Hosted UI

# 5. Verify TOTP is configured
aws cognito-idp admin-get-user \
  --user-pool-id [USER_POOL_ID] \
  --username newuser@example.com \
  --region us-east-1 | grep MFA

# 6. Set pool back to REQUIRED
aws cognito-idp set-user-pool-mfa-config \
  --user-pool-id [USER_POOL_ID] \
  --mfa-configuration ON \
  --software-token-mfa-configuration Enabled=true \
  --region us-east-1
```

## SMS MFA (Additional Considerations)

If using SMS MFA (`cognitoMfaMethod: "sms"` or `"both"`):

### **SNS Sandbox Mode**

AWS accounts default to **SNS Sandbox mode**, which blocks SMS messages. To enable SMS:

1. **Check SNS Sandbox Status**:
   ```bash
   aws sns get-sms-sandbox-account-status --region us-east-1
   ```

2. **Exit SNS Sandbox** (AWS Console):
   - Go to: SNS Console → Text messaging (SMS) → Sandbox
   - Click "Request production access"
   - Fill out form:
     - Monthly spend: $1-$10
     - Use case: "Multi-factor authentication"
   - Wait for approval (usually instant)

3. **Verify Exit**:
   ```bash
   aws sns get-sms-sandbox-account-status --region us-east-1
   ```
   Should return: `"IsInSandbox": false`

### **Phone Number Requirements**

- Phone numbers must be in **E.164 format**: `+[country code][number]`
- Example: `+12025551234` (US number)
- Add to deployment context:
  ```json
  {
    "cognitoInitialAdminPhone": "+12025551234"
  }
  ```

## Compliance Audit Evidence

For compliance audits, document:

1. **MFA Enforcement Policy**:
   ```bash
   aws cognito-idp describe-user-pool \
     --user-pool-id [USER_POOL_ID] \
     --region us-east-1 | jq '.UserPool.MfaConfiguration'
   ```
   Should return: `"ON"` (REQUIRED)

2. **User MFA Status**:
   ```bash
   aws cognito-idp list-users \
     --user-pool-id [USER_POOL_ID] \
     --region us-east-1 | jq '.Users[] | {Username, MFAOptions, UserMFASettingList}'
   ```

3. **MFA Methods Enabled**:
   ```bash
   aws cognito-idp get-user-pool-mfa-config \
     --user-pool-id [USER_POOL_ID] \
     --region us-east-1
   ```

## Troubleshooting

### **HTTP 561 Error / QR Code Won't Scan**

**Cause**: MFA is REQUIRED but user hasn't set up TOTP yet (circular dependency)

**Solution**:
1. Temporarily set MFA to OPTIONAL (as shown in Phase 1)
2. User logs in and sets up TOTP
3. Set MFA back to REQUIRED (Phase 3)

### **SMS Not Sending**

**Cause**: SNS Sandbox mode or insufficient SMS spending limit

**Solution**:
1. Exit SNS Sandbox (see SMS MFA section above)
2. Increase SMS spending limit:
   - AWS Console → Service Quotas → Amazon SNS
   - Request increase for "Account spending limit for SMS"
   - Set to at least $1-$10/month

### **User Can't Complete MFA Setup**

**Cause**: Missing phone number for SMS MFA

**Solution**:
Add phone number to user attributes:
```bash
aws cognito-idp admin-update-user-attributes \
  --user-pool-id [USER_POOL_ID] \
  --username user@example.com \
  --user-attributes Name=phone_number,Value=+12025551234 Name=phone_number_verified,Value=true \
  --region us-east-1
```

## Automation Script

For your specific deployment:

```bash
#!/bin/bash
# setup-mfa-compliance.sh

USER_POOL_ID="us-east-1_1czTA3ttE"
ADMIN_EMAIL="phillip@cloudforgeci.com"
REGION="us-east-1"

echo "=== MFA Compliance Setup for SOC 2 / PCI-DSS / HIPAA / GDPR ==="

# Step 1: Check if admin has TOTP configured
echo "Step 1: Checking admin TOTP status..."
MFA_STATUS=$(aws cognito-idp admin-get-user \
  --user-pool-id "$USER_POOL_ID" \
  --username "$ADMIN_EMAIL" \
  --region "$REGION" \
  --query 'UserMFASettingList' \
  --output text)

if [[ "$MFA_STATUS" == *"SOFTWARE_TOKEN_MFA"* ]]; then
  echo "✓ Admin has TOTP configured"

  # Step 2: Set MFA to REQUIRED
  echo "Step 2: Setting MFA to REQUIRED (compliance mode)..."
  aws cognito-idp set-user-pool-mfa-config \
    --user-pool-id "$USER_POOL_ID" \
    --mfa-configuration ON \
    --software-token-mfa-configuration Enabled=true \
    --region "$REGION"

  echo "✓ MFA is now REQUIRED - compliant with SOC 2, PCI-DSS, HIPAA, GDPR"
else
  echo "✗ Admin has NOT set up TOTP yet"
  echo ""
  echo "Please complete TOTP setup first:"
  echo "1. Log in to your application"
  echo "2. Go to MFA settings in Cognito Hosted UI"
  echo "3. Scan QR code with authenticator app"
  echo "4. Verify with 6-digit code"
  echo "5. Re-run this script"
fi

# Step 3: Verify compliance
echo ""
echo "Step 3: Verifying compliance configuration..."
aws cognito-idp get-user-pool-mfa-config \
  --user-pool-id "$USER_POOL_ID" \
  --region "$REGION"

echo ""
echo "=== Setup Complete ==="
```

Make executable and run:
```bash
chmod +x setup-mfa-compliance.sh
./setup-mfa-compliance.sh
```

## References

- **SOC 2 Trust Services Criteria**: CC6.2 - Multi-factor Authentication
- **PCI-DSS v4.0**: Requirement 8.3 - Multi-factor authentication for all access
- **HIPAA Security Rule**: §164.312(d) - Person or entity authentication
- **GDPR**: Article 32 - Security of processing
- **AWS Cognito MFA Documentation**: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-mfa.html
