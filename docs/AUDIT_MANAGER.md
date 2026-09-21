# AWS Audit Manager Integration

CloudForge can create AWS Audit Manager assessments for the compliance frameworks selected in a deployment. Audit Manager then collects evidence from AWS services for those assessments.

## Overview

Set `auditManagerEnabled: true` to enable the integration. It is `false` by default for every security profile. When enabled, CloudForge:

- Creates one Audit Manager assessment per framework in `complianceFrameworks`
- Creates an S3 bucket for assessment reports and an IAM role for Audit Manager
- Installs the CloudForge framework validators (`FrameworkRules`) during synthesis; see [Validation Architecture](compliance/VALIDATION_ARCHITECTURE.md)

Audit Manager assessments collect evidence. They do not certify a system or replace an auditor.

## Prerequisites

### 1. Enable AWS Audit Manager in Your Account

Audit Manager must be set up in each region where you deploy with `auditManagerEnabled: true`. Use the AWS Audit Manager console, or:

```bash
aws auditmanager register-account --region us-east-1
aws auditmanager get-account-status --region us-east-1
```

### 2. Data Sources

Audit Manager collects evidence from:
- **AWS CloudTrail** - API activity (CloudForge creates a trail for `staging` and `production`)
- **AWS Config** - Configuration history (CloudForge creates Config rules when `awsConfigEnabled` is `true`, and the recorder when `createConfigInfrastructure` is `true`)
- **AWS Security Hub** - Security findings (optional)

### 3. AWS CLI at Synthesis Time

CloudForge resolves each framework to an Audit Manager framework ID during synthesis by running `aws auditmanager list-assessment-frameworks --framework-type Standard`. The CLI is invoked at `/usr/local/bin/aws`, with the `AWS_PROFILE` and `AWS_REGION` environment variables passed through. If the CLI is missing, times out (10 seconds), or finds no match, the assessment for that framework is skipped with a warning and synthesis continues.

## Framework Selection

Assessments are created for the values in `complianceFrameworks` (`soc2`, `pci-dss`, `hipaa`, `gdpr`). For each value, CloudForge searches the names of the standard frameworks in your account and region:

- Names containing the value (case-insensitive)
- `SOC 2` for `soc2`
- `PCI DSS` for `pci-dss`

The first match is used. List the available frameworks with:

```bash
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

`ComplianceFactory` also reads an `auditManagerFrameworkId` key (a framework UUID, ARN, or name) as a fallback when `complianceFrameworks` is empty. The key is not exposed through `DeploymentContext` yet, so it cannot currently be set from configuration.

## Configuration

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "soc2,hipaa",
  "awsConfigEnabled": true,
  "auditManagerEnabled": true
}
```

### Security Profile Defaults

| Profile    | Audit Manager | CloudTrail | AWS Config rules |
|------------|---------------|------------|------------------|
| DEV        | Off unless `auditManagerEnabled` | Not created | Off unless `awsConfigEnabled` |
| STAGING    | Off unless `auditManagerEnabled` | Created | Off unless `awsConfigEnabled` |
| PRODUCTION | Off unless `auditManagerEnabled` | Created | Off unless `awsConfigEnabled` |

## What Gets Created

### 1. S3 Bucket for Assessment Reports
- S3-managed encryption, versioning, and public access blocked
- Bucket policy that denies requests without TLS (`aws:SecureTransport`)
- S3 data events recorded by the stack's CloudTrail trail
- ARN recorded in SSM at `/cloudforge/shared/{region}/stack/{stackName}/audit-manager/bucket-arn`
- Retained in `production`; see [Retained Resources](compliance/RETAINED_RESOURCES.md)

### 2. IAM Role for Audit Manager
- Service principal: `auditmanager.amazonaws.com`
- Permissions for:
  - Audit Manager read access (`GetAccountStatus`, `ListAssessmentFrameworks`, and related actions)
  - CloudTrail read access for evidence collection
  - AWS Config read access for evidence collection
  - Writing to the assessment report bucket

### 3. Audit Manager Assessments
- One per resolved framework
- Name: `audit-{framework}-{stackName}-{hash}`
- Scope: the current AWS account
- Reports: stored in the report bucket
- Tags: `Environment`, `Framework`, and `ManagedBy`

## IAM Permissions for Deployment

The deployment role needs these permissions in addition to the usual CloudFormation permissions:

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

The credentials used for `cdk synth` also need `auditmanager:ListAssessmentFrameworks` for framework resolution.

Application IAM roles do not need Audit Manager permissions.

## Accessing Assessment Reports

Generate assessment reports in the Audit Manager console or with `aws auditmanager create-assessment-report`. Reports are written to the report bucket:

```bash
aws s3 ls s3://<audit-manager-report-bucket>/
```

## Cost Considerations

Audit Manager is priced per resource assessment, and report storage uses standard S3 pricing. See [AWS Audit Manager Pricing](https://aws.amazon.com/audit-manager/pricing/).

## Troubleshooting

### Assessments Not Created

**Symptoms:** Synthesis logs contain `Unable to resolve Audit Manager framework` or `No Audit Manager assessments were created`.

**Solution:**
1. Confirm Audit Manager is set up in the region:
   ```bash
   aws auditmanager get-account-status --region us-east-1
   ```
2. Confirm the AWS CLI is available at `/usr/local/bin/aws` in the synthesis environment and that `AWS_PROFILE`/`AWS_REGION` point to the target account and region.
3. List the standard frameworks and confirm one matches the selected framework name:
   ```bash
   aws auditmanager list-assessment-frameworks --framework-type Standard --region us-east-1
   ```

### Error: "Insufficient permissions"

Ensure the deployment role has permissions to create Audit Manager resources. See [IAM Permissions for Deployment](#iam-permissions-for-deployment).

### No Evidence Being Collected

**Symptoms:** The assessment exists, but its evidence count stays at 0.

**Solution:**
1. Verify CloudTrail is logging
2. Verify the AWS Config recorder is running:
   ```bash
   aws configservice describe-configuration-recorders
   aws configservice describe-configuration-recorder-status
   ```
3. Check the Audit Manager data source settings in the AWS console
4. Allow 24-48 hours for initial evidence collection

## Best Practices

1. **Framework selection**: Select frameworks that match your compliance requirements
2. **Evidence retention**: Keep assessment reports for the period your frameworks require
3. **Access control**: Restrict access to the report bucket
4. **Regular reviews**: Review assessment findings on a fixed schedule
5. **Automation**: Use Amazon EventBridge to act on assessment status changes

## Integration with Other CloudForge Features

Audit Manager uses evidence from:
- **CloudTrail** - API activity
- **AWS Config** - Configuration compliance
- **Security Hub and GuardDuty** - Security findings
- **VPC Flow Logs** - Network activity

## Example Deployment

```java
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class ComplianceStack {
    public static void main(String[] args) {
        App app = new App();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("securityProfile", "production");
        config.put("complianceFrameworks", "soc2");
        config.put("awsConfigEnabled", true);
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
- [Audit Manager Framework Library](https://docs.aws.amazon.com/audit-manager/latest/userguide/framework-overviews.html)

## Support

For issues or questions:
1. Check the [troubleshooting section](#troubleshooting) above
2. Review the synthesis log for Audit Manager messages
3. Consult the AWS Audit Manager documentation
4. Open an issue at https://github.com/CloudForgeCI/cfc-core/issues
