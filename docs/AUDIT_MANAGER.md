# AWS Audit Manager Integration

CloudForge now supports AWS Audit Manager for continuous auditing and automated evidence collection. This integration helps organizations maintain compliance with various regulatory frameworks.

## Overview

AWS Audit Manager is automatically enabled for **STAGING** and **PRODUCTION** security profiles. It provides:

- **Continuous Auditing** - Automated evidence collection from AWS services
- **Compliance Frameworks** - Pre-built frameworks for SOC2, HIPAA, PCI-DSS, GDPR, etc.
- **Assessment Reports** - Automated compliance reports stored in S3
- **Evidence Management** - Centralized evidence collection and organization

## Prerequisites

⚠️ **IMPORTANT:** CloudForge validates Audit Manager setup **before** creating AWS Config infrastructure (Recorder + Delivery Channel). This fail-fast approach prevents creating account-level resources when Audit Manager is not properly configured.

Before deploying with Audit Manager enabled, you must:

### 1. Enable AWS Audit Manager in Your Account

**This must be done per-region** where you plan to deploy CloudForge with Audit Manager enabled.

```bash
# Navigate to AWS Audit Manager in the AWS Console for the target region
# OR use AWS CLI
aws auditmanager update-settings \
  --region us-east-1 \
  --default-assessment-reports-destination destinationType=S3,destination=s3://your-audit-reports-bucket
```

**What happens if not enabled:**
- ❌ Deployment will **fail during CDK synthesis or deployment**
- ✅ Config Recorder and Delivery Channel will **NOT be created** (fail-fast behavior)
- ✅ Prevents orphaned account-level resources

### 2. Configure Data Sources

Audit Manager collects evidence from:
- **AWS CloudTrail** - API activity logs (CloudForge creates this automatically)
- **AWS Config** - Configuration change history (CloudForge creates this automatically)
- **AWS Security Hub** - Security findings (optional)
- **AWS Control Tower** (if applicable)

**Note:** CloudForge automatically creates CloudTrail and AWS Config infrastructure, so you only need to enable Audit Manager itself.

### 3. List Available Frameworks

```bash
# List standard AWS frameworks
aws auditmanager list-assessment-frameworks --framework-type Standard

# Example output:
# {
#   "frameworkMetadataList": [
#     {
#       "arn": "arn:aws:auditmanager:us-east-1::framework/a1b2c3d4-5678-90ab-cdef-EXAMPLE11111",
#       "id": "a1b2c3d4-5678-90ab-cdef-EXAMPLE11111",
#       "name": "AWS Foundational Security Best Practices",
#       "type": "Standard"
#     },
#     ...
#   ]
# }
```

**IMPORTANT**: Note the framework `id` field (36-character UUID). CloudForge automatically queries AWS to discover framework UUIDs based on framework names.

### 4. Configure Framework

**How Framework Selection Works**:
CloudForge automatically queries your AWS account for available Audit Manager frameworks. When you select a framework by short name (SOC2, HIPAA, PCI-DSS) or use the default, the system:
1. Queries AWS using `aws auditmanager list-assessment-frameworks`
2. Searches for matching framework by name
3. Extracts the framework UUID automatically
4. Uses that UUID to create the assessment

**Interactive Deployer** (Recommended)
```bash
mvn clean compile exec:java
# Select from menu (framework UUID is auto-discovered from AWS):
#   1. AWS Foundational Security Best Practices (default)
#   2. SOC 2
#   3. HIPAA
#   4. PCI DSS 3.2.1
#   5. Custom (enter framework UUID manually)
```

**Environment Variables** (CI/CD)
```bash
# Use short names (framework UUID will be auto-discovered from AWS)
export AUDIT_MANAGER_FRAMEWORK_PRODUCTION=SOC2
export AUDIT_MANAGER_FRAMEWORK_STAGING=HIPAA

# Or provide framework UUID directly (36-character ID from AWS)
export AUDIT_MANAGER_FRAMEWORK_PRODUCTION=a1b2c3d4-5678-90ab-cdef-EXAMPLE11111

# Or provide full ARN (UUID will be extracted automatically)
export AUDIT_MANAGER_FRAMEWORK_PRODUCTION=arn:aws:auditmanager:region::framework/a1b2c3d4-5678-90ab-cdef-EXAMPLE11111
```

**CDK Context**
```json
{
  "context": {
    "auditManagerFrameworkPRODUCTION": "SOC2",
    "auditManagerFrameworkSTAGING": "HIPAA"
  }
}
```

**Default**: Queries AWS for "AWS Foundational Security Best Practices" framework. If AWS query fails, uses placeholder UUID.

## Configuration

### Enable Audit Manager for a Deployment

Audit Manager is enabled by default for STAGING and PRODUCTION profiles. To override:

```java
Map<String, Object> config = new LinkedHashMap<>();
config.put("auditManagerEnabled", true);  // Force enable
// OR
config.put("auditManagerEnabled", false); // Force disable

App app = new App();
app.getNode().setContext("cfc", config);
```

### Security Profile Defaults

| Profile    | Audit Manager | CloudTrail | AWS Config |
|------------|---------------|------------|------------|
| DEV        | ❌ Disabled   | ✅ Enabled  | ❌ Disabled |
| STAGING    | ✅ Enabled    | ✅ Enabled  | ✅ Enabled  |
| PRODUCTION | ✅ Enabled    | ✅ Enabled  | ✅ Enabled  |

## What Gets Created

When Audit Manager is enabled, CloudForge creates:

### 1. S3 Bucket for Assessment Reports
- Encrypted with S3-managed encryption
- Versioning enabled
- Public access blocked
- Retention policy: RETAIN

### 2. IAM Role for Audit Manager
- Service principal: `auditmanager.amazonaws.com`
- Inline policies for:
  - Audit Manager access (GetAccountStatus, ListAssessmentFrameworks, etc.)
  - CloudTrail read permissions (evidence collection)
  - AWS Config read permissions (evidence collection)
  - S3 write permissions to assessment report bucket
- SSL/TLS enforcement for S3 access
- Server access logging enabled

### 3. Audit Manager Assessment
- Name: `audit-{profile}-{stack-hash}`
- Framework: Based on security profile (or configured)
- Scope: Current AWS account (automatically detected)
- Reports: Stored in S3 bucket
- Tags: Environment and ManagedBy

## Common Compliance Frameworks

| Framework | Use Case | ARN Pattern |
|-----------|----------|-------------|
| SOC 2 Type II | SaaS, cloud providers | `arn:aws:auditmanager:region::framework/SOC2-ID` |
| HIPAA | Healthcare | `arn:aws:auditmanager:region::framework/HIPAA-ID` |
| PCI DSS 3.2.1 | Payment processing | `arn:aws:auditmanager:region::framework/PCI-DSS-ID` |
| AWS Best Practices | General compliance | `arn:aws:auditmanager:::framework/aws-foundational-security-best-practices` |

## IAM Permissions for Deployment

Deployment role needs these permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AuditManagerDeployment",
      "Effect": "Allow",
      "Action": [
        "auditmanager:CreateAssessment",
        "auditmanager:GetAssessment",
        "auditmanager:UpdateAssessment",
        "auditmanager:DeleteAssessment",
        "auditmanager:TagResource",
        "auditmanager:ListAssessmentFrameworks"
      ],
      "Resource": "*"
    },
    {
      "Sid": "AuditManagerIAMRole",
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole",
        "iam:GetRole",
        "iam:AttachRolePolicy",
        "iam:PassRole"
      ],
      "Resource": "arn:aws:iam::*:role/*AuditManager*"
    }
  ]
}
```

Note: Application IAM roles don't need Audit Manager permissions. CloudForge creates a dedicated service role.

## Accessing Assessment Reports

After deployment, assessment reports are automatically generated and stored in S3:

```bash
# List assessment reports
aws s3 ls s3://audit-manager-report-bucket-XXXXX/

# Download a report
aws s3 cp s3://audit-manager-report-bucket-XXXXX/report.zip ./
```

You can also access reports via the AWS Audit Manager console.

## Cost Considerations

AWS Audit Manager pricing:
- **Assessment Evidence Collection**: $1.00 per 100,000 evidence items
- **S3 Storage**: Standard S3 pricing for reports
- **No charge**: For assessments themselves

**Recommendation**: Enable for STAGING and PRODUCTION only to control costs.

## Troubleshooting

### Error: "Audit Manager is not enabled in this region"

**Symptoms:**
- Deployment fails during CDK synthesis or CloudFormation deployment
- Error mentions Audit Manager not being initialized
- Config Recorder and Delivery Channel were NOT created (fail-fast behavior)

**Root Cause:**
Audit Manager must be enabled per-region before deploying CloudForge with `auditManagerEnabled: true`.

**Solution:**
1. Enable Audit Manager in the target region:
   ```bash
   aws auditmanager update-settings \
     --region us-east-1 \
     --default-assessment-reports-destination destinationType=S3,destination=s3://your-bucket
   ```

2. Verify Audit Manager is enabled:
   ```bash
   aws auditmanager get-account-status --region us-east-1
   ```

3. Redeploy the stack

**Why this is good:**
- ✅ Prevents creating orphaned Config Recorder/Delivery Channel resources
- ✅ Fails before creating account-level singleton resources
- ✅ Easier cleanup if configuration is wrong

### Error: "Framework not found"

**Solution**:
1. List available frameworks in your region:
   ```bash
   aws auditmanager list-assessment-frameworks \
     --framework-type Standard \
     --region us-east-1
   ```

2. Update `deployment-context.json` with correct framework ID:
   ```json
   {
     "auditManagerFrameworkId": "a1b2c3d4-5678-90ab-cdef-EXAMPLE11111"
   }
   ```

3. Verify the framework ID is correct for your region and account

### Error: "Insufficient permissions"

**Solution**: Ensure the deployment IAM role has permissions to create Audit Manager resources. See [IAM Permissions for Deployment](#iam-permissions-for-deployment) section above.

### No Evidence Being Collected

**Symptoms:**
- Assessment created successfully but no evidence appears
- Evidence count remains at 0 in Audit Manager console

**Solution**:
1. Verify CloudTrail is enabled and logging (CloudForge creates this automatically)
2. Verify AWS Config Recorder is running:
   ```bash
   aws configservice describe-configuration-recorders
   aws configservice describe-configuration-recorder-status
   ```
3. Check Audit Manager data source settings in AWS Console
4. Wait 24-48 hours for initial evidence collection

### Deployment Order Issues

**Problem:** Deploying to a new region without Audit Manager enabled creates Config infrastructure before failing.

**Solution (Fixed):** CloudForge now validates Audit Manager **before** creating Config infrastructure:

**Deployment Order:**
1. ✅ CloudTrail creation
2. ✅ Audit Manager validation (fails fast if not enabled)
3. ✅ Config Recorder + Delivery Channel (only if Audit Manager succeeds)
4. ✅ Config Rules

This ensures clean failure without orphaned resources.

## Best Practices

1. **Framework Selection**: Choose frameworks that match your compliance requirements
2. **Evidence Retention**: Keep assessment reports for at least 7 years for most frameworks
3. **Access Control**: Restrict access to assessment reports using S3 bucket policies
4. **Regular Reviews**: Review assessment findings monthly
5. **Automation**: Use AWS EventBridge to trigger automated actions based on assessment status

## Integration with Other CloudForge Features

Audit Manager works alongside:
- **CloudTrail** - Provides API activity evidence
- **AWS Config** - Provides configuration compliance evidence
- **Security Monitoring** - GuardDuty findings can be evidence sources
- **VPC Flow Logs** - Network activity evidence

## Example Deployment

```java
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;

public class ComplianceStack {
    public static void main(String[] args) {
        App app = new App();

        // Configure for production with Audit Manager
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tier", "production");
        config.put("env", "prod");
        config.put("auditManagerEnabled", true);

        app.getNode().setContext("cfc", config);

        Stack stack = new Stack(app, "ProductionStack");
        DeploymentContext cfc = DeploymentContext.from(stack);

        SystemContext.start(stack,
            TopologyType.JENKINS_SERVICE,
            RuntimeType.FARGATE,
            SecurityProfile.PRODUCTION,
            IAMProfile.MINIMAL,
            cfc
        );

        app.synth();
    }
}
```

## Additional Resources

- [AWS Audit Manager Documentation](https://docs.aws.amazon.com/audit-manager/)
- [AWS Audit Manager Pricing](https://aws.amazon.com/audit-manager/pricing/)
- [Compliance Framework Guide](https://docs.aws.amazon.com/audit-manager/latest/userguide/framework-overviews.html)
- [CloudForge Security Configuration](../README.md#security-profiles)

## Support

For issues or questions:
1. Check the [troubleshooting section](#troubleshooting) above
2. Review CloudForge logs for error messages
3. Consult AWS Audit Manager documentation
4. Open an issue in the CloudForge repository
