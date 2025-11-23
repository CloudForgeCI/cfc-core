# Audit Readiness Guide

This guide helps you prepare for external compliance audits (SOC 2, HIPAA, PCI-DSS, GDPR) by explaining how to collect evidence, generate reports, and present CloudForge CI infrastructure to auditors.

## Table of Contents

1. [Pre-Audit Preparation](#pre-audit-preparation)
2. [Evidence Collection](#evidence-collection)
3. [Audit Artifacts](#audit-artifacts)
4. [Auditor Access](#auditor-access)
5. [Common Audit Questions](#common-audit-questions)
6. [Framework-Specific Guidance](#framework-specific-guidance)
7. [Post-Audit Actions](#post-audit-actions)

---

## Pre-Audit Preparation

### 1. Run Compliance Validation (2 weeks before audit)

```bash
# Generate compliance report
./scripts/generate-compliance-report.sh production-soc2

# Check for non-compliant resources
aws configservice describe-compliance-by-config-rule \
  --compliance-types NON_COMPLIANT \
  --output json > compliance-status.json

# Review and remediate non-compliant resources
cat compliance-status.json | jq '.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT")'
```

### 2. Generate Evidence Package

```bash
# Run evidence generation script
./scripts/generate-audit-evidence.sh \
  --stack-name CloudForge-Prod-SOC2 \
  --framework SOC2 \
  --start-date 2024-01-01 \
  --end-date 2024-12-31 \
  --output audit-evidence-2024

# This creates:
# - audit-evidence-2024/
#   ├── cloudformation-template.yaml
#   ├── config-rules-report.json
#   ├── cloudtrail-logs/
#   ├── config-snapshots/
#   ├── compliance-matrix.md
#   └── control-evidence/
```

### 3. Document Infrastructure Changes

```bash
# Export CloudFormation change sets for the audit period
aws cloudformation list-change-sets \
  --stack-name CloudForge-Prod-SOC2 \
  --output json > change-sets.json

# Export Git commits for infrastructure code
git log --since="2024-01-01" --until="2024-12-31" \
  --pretty=format:"%h - %an, %ar : %s" > infrastructure-changes.txt
```

### 4. Prepare Documentation Package

Collect these documents for auditors:

- ✅ [Auditor Compliance Mapping](AUDITOR_COMPLIANCE_MAPPING.md) - Control implementation details
- ✅ [Compliance Posture](compliance/COMPLIANCE_POSTURE.md) - Current compliance status
- ✅ [Deployment Context](../deployment-contexts/production-soc2.json) - Configuration
- ✅ [CloudFormation Template](generated from `cdk synth`)
- ✅ [CHANGELOG.md](../CHANGELOG.md) - Version history and compliance changes
- ✅ [Security Rules](guides/SECURITY_RULES_README.md) - Security control details

---

## Evidence Collection

### Evidence Types by Control Area

#### 1. Access Control Evidence

**What auditors need:**
- IAM policies and roles
- Cognito user pool configuration
- MFA enforcement evidence
- Access logs

**How to collect:**

```bash
# Export IAM policies
aws iam list-policies --scope Local --output json > iam-policies.json

# Export IAM roles
aws iam list-roles --output json > iam-roles.json

# Export Cognito User Pool configuration
POOL_ID=$(aws cloudformation describe-stacks \
  --stack-name CloudForge-Prod-SOC2 \
  --query 'Stacks[0].Outputs[?OutputKey==`CognitoUserPoolId`].OutputValue' \
  --output text)

aws cognito-idp describe-user-pool \
  --user-pool-id $POOL_ID \
  --output json > cognito-config.json

# Verify MFA enforcement
aws cognito-idp describe-user-pool \
  --user-pool-id $POOL_ID \
  --query 'UserPool.MfaConfiguration' \
  --output text
```

#### 2. Encryption Evidence

**What auditors need:**
- KMS key policies
- S3 bucket encryption configuration
- EFS encryption status
- TLS/SSL certificate details

**How to collect:**

```bash
# List KMS keys
aws kms list-keys --output json > kms-keys.json

# Check S3 bucket encryption
aws s3api list-buckets --query 'Buckets[].Name' --output text | \
while read bucket; do
  echo "Bucket: $bucket"
  aws s3api get-bucket-encryption --bucket $bucket 2>/dev/null || echo "No encryption"
done > s3-encryption-status.txt

# Check EFS encryption
aws efs describe-file-systems \
  --query 'FileSystems[].[FileSystemId,Encrypted]' \
  --output table > efs-encryption-status.txt

# Export ACM certificates
aws acm list-certificates --output json > acm-certificates.json
```

#### 3. Audit Logging Evidence

**What auditors need:**
- CloudTrail configuration
- CloudTrail logs for audit period
- Config history
- VPC Flow Logs configuration

**How to collect:**

```bash
# Export CloudTrail configuration
aws cloudtrail describe-trails --output json > cloudtrail-config.json

# Download CloudTrail logs for audit period
aws cloudtrail lookup-events \
  --start-time 2024-01-01 \
  --end-time 2024-12-31 \
  --max-results 10000 \
  --output json > cloudtrail-events.json

# Export Config timeline for a resource
aws configservice get-resource-config-history \
  --resource-type AWS::EC2::Instance \
  --resource-id i-1234567890abcdef0 \
  --output json > config-history.json

# Verify VPC Flow Logs
aws ec2 describe-flow-logs --output json > vpc-flow-logs.json
```

#### 4. Change Management Evidence

**What auditors need:**
- Infrastructure as Code (IaC) repository history
- CloudFormation stack events
- Config rule compliance history
- Remediation execution history

**How to collect:**

```bash
# Export CloudFormation events
aws cloudformation describe-stack-events \
  --stack-name CloudForge-Prod-SOC2 \
  --max-items 1000 \
  --output json > stack-events.json

# Export Config rule compliance timeline
aws configservice describe-compliance-by-config-rule \
  --output json > config-compliance-history.json

# Export Config remediation executions
aws configservice describe-remediation-execution-status \
  --config-rule-name s3-bucket-versioning-enabled \
  --output json > remediation-executions.json
```

#### 5. Monitoring and Incident Response Evidence

**What auditors need:**
- GuardDuty findings
- CloudWatch alarms configuration
- SNS notification topics
- Security incident response logs

**How to collect:**

```bash
# Export GuardDuty findings
aws guardduty list-detectors --query 'DetectorIds[0]' --output text | \
xargs -I {} aws guardduty list-findings --detector-id {} --output json > guardduty-findings.json

# Export CloudWatch alarms
aws cloudwatch describe-alarms --output json > cloudwatch-alarms.json

# Export SNS topics
aws sns list-topics --output json > sns-topics.json

# Export Security Hub findings (if enabled)
aws securityhub get-findings --output json > securityhub-findings.json
```

---

## Audit Artifacts

### Automated Evidence Generation Script

Create `scripts/generate-audit-evidence.sh`:

```bash
#!/bin/bash
set -euo pipefail

STACK_NAME=${1:-CloudForge-Prod-SOC2}
FRAMEWORK=${2:-SOC2}
OUTPUT_DIR="audit-evidence-$(date +%Y%m%d)"

echo "Generating audit evidence for $STACK_NAME ($FRAMEWORK)..."
mkdir -p "$OUTPUT_DIR"/{config,iam,encryption,logging,monitoring}

# 1. Infrastructure as Code
echo "Exporting CloudFormation template..."
aws cloudformation get-template \
  --stack-name "$STACK_NAME" \
  --query 'TemplateBody' > "$OUTPUT_DIR/cloudformation-template.yaml"

# 2. IAM Configuration
echo "Exporting IAM configuration..."
aws iam list-policies --scope Local --output json > "$OUTPUT_DIR/iam/policies.json"
aws iam list-roles --output json > "$OUTPUT_DIR/iam/roles.json"

# 3. Config Rules
echo "Exporting Config rules and compliance status..."
aws configservice describe-config-rules --output json > "$OUTPUT_DIR/config/rules.json"
aws configservice describe-compliance-by-config-rule --output json > "$OUTPUT_DIR/config/compliance.json"

# 4. Encryption
echo "Exporting encryption configuration..."
aws kms list-keys --output json > "$OUTPUT_DIR/encryption/kms-keys.json"
aws efs describe-file-systems --output json > "$OUTPUT_DIR/encryption/efs.json"

# 5. Audit Logging
echo "Exporting audit logging configuration..."
aws cloudtrail describe-trails --output json > "$OUTPUT_DIR/logging/cloudtrail.json"
aws logs describe-log-groups --output json > "$OUTPUT_DIR/logging/log-groups.json"

# 6. Monitoring
echo "Exporting monitoring configuration..."
aws cloudwatch describe-alarms --output json > "$OUTPUT_DIR/monitoring/alarms.json"
aws guardduty list-detectors --output json > "$OUTPUT_DIR/monitoring/guardduty.json"

# 7. Generate compliance matrix
echo "Generating compliance matrix..."
cat > "$OUTPUT_DIR/compliance-matrix.md" <<EOF
# Compliance Matrix - $FRAMEWORK
Generated: $(date)
Stack: $STACK_NAME

## Controls Implemented

$(cat docs/AUDITOR_COMPLIANCE_MAPPING.md | grep -A 5 "^### $FRAMEWORK")

## Evidence Location

- CloudFormation Template: cloudformation-template.yaml
- IAM Configuration: iam/
- Config Rules: config/
- Encryption: encryption/
- Audit Logging: logging/
- Monitoring: monitoring/

EOF

# 8. Create evidence package
echo "Creating evidence package..."
tar -czf "${OUTPUT_DIR}.tar.gz" "$OUTPUT_DIR"

echo "✅ Evidence package created: ${OUTPUT_DIR}.tar.gz"
echo "📦 Size: $(du -h "${OUTPUT_DIR}.tar.gz" | cut -f1)"
```

### Control Evidence Mapping

Create `scripts/map-controls-to-evidence.sh`:

```bash
#!/bin/bash
# Maps compliance controls to specific evidence files

FRAMEWORK=${1:-SOC2}
OUTPUT="control-evidence-mapping.md"

cat > "$OUTPUT" <<EOF
# Control Evidence Mapping - $FRAMEWORK
Generated: $(date)

This document maps each compliance control to specific evidence artifacts.

## SOC 2 Controls

### CC6.1 - Logical and Physical Access Controls

**Evidence:**
- IAM policies: \`audit-evidence-*/iam/policies.json\`
- IAM roles: \`audit-evidence-*/iam/roles.json\`
- Cognito configuration: \`audit-evidence-*/iam/cognito-config.json\`
- Config rule: \`iam-password-policy\`, \`iam-user-mfa-enabled\`

**Validation:**
\`\`\`bash
jq '.Policies[] | select(.PolicyName | contains("CloudForge"))' iam/policies.json
\`\`\`

### CC6.6 - Network Segmentation

**Evidence:**
- Security groups: CloudFormation template section \`AWS::EC2::SecurityGroup\`
- VPC configuration: CloudFormation template section \`AWS::EC2::VPC\`
- Config rule: \`vpc-sg-open-only-to-authorized-ports\`

**Validation:**
\`\`\`bash
aws ec2 describe-security-groups --filters "Name=tag:cloudforge:deployment,Values=CloudForge-Prod"
\`\`\`

### CC6.7 - Transmission Security

**Evidence:**
- ALB listener configuration: CloudFormation template \`AWS::ElasticLoadBalancingV2::Listener\`
- ACM certificates: \`audit-evidence-*/encryption/acm-certificates.json\`
- Config rule: \`alb-http-to-https-redirection-check\`

**Validation:**
\`\`\`bash
aws elbv2 describe-listeners --output json | jq '.Listeners[] | select(.Protocol == "HTTPS")'
\`\`\`

### CC7.2 - System Monitoring

**Evidence:**
- CloudTrail configuration: \`audit-evidence-*/logging/cloudtrail.json\`
- CloudWatch alarms: \`audit-evidence-*/monitoring/alarms.json\`
- GuardDuty: \`audit-evidence-*/monitoring/guardduty.json\`
- Config rules: All rules in \`audit-evidence-*/config/rules.json\`

**Validation:**
\`\`\`bash
aws cloudtrail get-trail-status --name cloudforge-trail
\`\`\`

### CC7.3 - Backup and Recovery

**Evidence:**
- EFS configuration: \`audit-evidence-*/encryption/efs.json\`
- S3 versioning: Config rule \`s3-bucket-versioning-enabled\`
- Retention configuration: CloudFormation template \`DeletionPolicy: Retain\`

**Validation:**
\`\`\`bash
aws efs describe-file-systems | jq '.FileSystems[] | {FileSystemId, SizeInBytes}'
\`\`\`

EOF

echo "✅ Control evidence mapping created: $OUTPUT"
```

---

## Auditor Access

### Read-Only IAM Policy for Auditors

Create a restricted IAM policy for auditor access:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadOnlyCloudFormation",
      "Effect": "Allow",
      "Action": [
        "cloudformation:Describe*",
        "cloudformation:Get*",
        "cloudformation:List*"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ReadOnlyConfig",
      "Effect": "Allow",
      "Action": [
        "config:Describe*",
        "config:Get*",
        "config:List*"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ReadOnlyCloudTrail",
      "Effect": "Allow",
      "Action": [
        "cloudtrail:Describe*",
        "cloudtrail:Get*",
        "cloudtrail:List*",
        "cloudtrail:LookupEvents"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ReadOnlyIAM",
      "Effect": "Allow",
      "Action": [
        "iam:Get*",
        "iam:List*"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ReadOnlyS3Evidence",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::audit-evidence-bucket",
        "arn:aws:s3:::audit-evidence-bucket/*"
      ]
    }
  ]
}
```

### Granting Temporary Access

```bash
# Create auditor user
aws iam create-user --user-name auditor-external

# Attach read-only policy
aws iam put-user-policy \
  --user-name auditor-external \
  --policy-name AuditorReadOnly \
  --policy-document file://auditor-policy.json

# Generate temporary credentials (valid for 12 hours)
aws iam create-access-key --user-name auditor-external

# After audit, remove access
aws iam delete-access-key --user-name auditor-external --access-key-id AKIA...
aws iam delete-user --user-name auditor-external
```

---

## Common Audit Questions

### Q1: "How do you ensure that only authorized users can access the Jenkins environment?"

**Answer:**
- Cognito User Pool with MFA (TOTP) enabled
- IAM roles with least-privilege permissions
- Security groups restricting network access
- ALB authentication with OIDC

**Evidence:**
- Cognito configuration: `cognito-config.json`
- IAM policies: `iam/policies.json`
- Security groups: CloudFormation template section `AWS::EC2::SecurityGroup`

**Demonstration:**
```bash
# Show MFA is enforced
aws cognito-idp describe-user-pool --user-pool-id <pool-id> \
  --query 'UserPool.MfaConfiguration'
# Output: "ON" or "OPTIONAL"
```

### Q2: "How do you monitor for security incidents and unauthorized access?"

**Answer:**
- AWS CloudTrail for API call logging
- GuardDuty for threat detection
- AWS Config for compliance monitoring
- CloudWatch alarms for anomaly detection
- VPC Flow Logs for network traffic analysis

**Evidence:**
- CloudTrail: `logging/cloudtrail.json`
- GuardDuty: `monitoring/guardduty.json`
- Config rules: `config/rules.json`

**Demonstration:**
```bash
# Show recent CloudTrail events
aws cloudtrail lookup-events --max-results 10

# Show GuardDuty findings
aws guardduty list-findings --detector-id <detector-id>
```

### Q3: "How do you ensure data is encrypted at rest and in transit?"

**Answer:**
- EFS encryption enabled with AWS managed keys
- S3 bucket encryption enforced via Config rules
- TLS 1.2+ enforced on ALB listeners
- KMS keys for sensitive data

**Evidence:**
- EFS encryption: [encryption/efs.json](file:encryption/efs.json)
- ALB listeners: CloudFormation template `AWS::ElasticLoadBalancingV2::Listener`
- Config rule: `encrypted-volumes`, `s3-bucket-server-side-encryption-enabled`

**Demonstration:**
```bash
# Verify EFS encryption
aws efs describe-file-systems --query 'FileSystems[].[FileSystemId,Encrypted]'

# Verify ALB uses HTTPS
aws elbv2 describe-listeners --query 'Listeners[?Protocol==`HTTPS`]'
```

### Q4: "How do you handle compliance violations?"

**Answer:**
- AWS Config automatic remediation for common violations
- SNS notifications for critical findings
- Runbook for manual remediation
- Quarterly compliance reviews

**Evidence:**
- Remediation configurations: `config/remediation-executions.json`
- SNS topics: `monitoring/sns-topics.json`
- Runbooks: [docs/compliance/AUTOMATED_COMPLIANCE.md](compliance/AUTOMATED_COMPLIANCE.md)

**Demonstration:**
```bash
# Show auto-remediation configuration
aws configservice describe-remediation-configurations \
  --config-rule-names s3-bucket-versioning-enabled
```

### Q5: "How do you ensure infrastructure changes are authorized and tracked?"

**Answer:**
- Infrastructure as Code (AWS CDK) with Git version control
- CloudFormation change sets reviewed before deployment
- CloudTrail logging all infrastructure changes
- Required approvals for production changes (GitHub branch protection)

**Evidence:**
- Git repository: Source code with commit history
- CloudFormation events: `stack-events.json`
- CloudTrail: API call logs for CloudFormation operations

**Demonstration:**
```bash
# Show recent infrastructure changes
git log --since="30 days ago" --oneline

# Show CloudFormation stack updates
aws cloudformation describe-stack-events --stack-name CloudForge-Prod \
  --max-items 20
```

---

## Framework-Specific Guidance

### SOC 2 Type II

**Audit Duration:** Minimum 6 months of continuous operation

**Key Focus Areas:**
- CC6: Logical and physical access controls
- CC7: System operations (monitoring, backup, incident response)

**Evidence Timeline:**
- Prepare 6+ months of CloudTrail logs
- Config compliance history for audit period
- GuardDuty findings and remediation

**Script:**
```bash
./scripts/generate-audit-evidence.sh CloudForge-Prod-SOC2 SOC2 \
  --start-date $(date -d '6 months ago' +%Y-%m-%d) \
  --end-date $(date +%Y-%m-%d)
```

### HIPAA

**Audit Duration:** Typically annual

**Key Focus Areas:**
- §164.312(a): Access control
- §164.312(b): Audit controls
- §164.312(d): Authentication
- §164.312(e): Transmission security

**Evidence Timeline:**
- Prepare 6 years of audit logs (retention requirement)
- Access logs for PHI/ePHI
- Encryption validation

**Additional Documentation:**
- Business Associate Agreement (BAA) with AWS
- HIPAA risk assessment
- Breach notification procedures

### PCI-DSS

**Audit Duration:** Annual

**Key Focus Areas:**
- Req 1-2: Network security
- Req 3-4: Data encryption
- Req 7-8: Access control
- Req 10: Logging and monitoring

**Evidence Timeline:**
- 1 year of audit logs minimum
- Quarterly network scans
- Penetration testing reports

**Additional Documentation:**
- Attestation of Compliance (AOC)
- Self-Assessment Questionnaire (SAQ)
- Network diagram

### GDPR

**Audit Duration:** Ongoing (data protection impact assessment)

**Key Focus Areas:**
- Article 25: Data protection by design
- Article 30: Records of processing
- Article 32: Security of processing
- Article 33: Breach notification

**Evidence Timeline:**
- Data processing records
- Data breach incident logs (if any)
- DPO communications

**Additional Documentation:**
- Data Processing Agreement (DPA)
- Privacy policy
- Data subject rights procedures

---

## Post-Audit Actions

### 1. Address Findings

```bash
# Document audit findings
cat > audit-findings.md <<EOF
# Audit Findings - $(date +%Y)

## Finding 1: [Description]
- **Severity**: High/Medium/Low
- **Control**: [Control ID]
- **Remediation**: [Steps to fix]
- **Timeline**: [Completion date]
- **Status**: Open/In Progress/Closed

EOF
```

### 2. Update Documentation

```bash
# Update compliance posture
vim docs/compliance/COMPLIANCE_POSTURE.md

# Update CHANGELOG with audit-related changes
vim CHANGELOG.md
```

### 3. Implement Corrective Actions

```bash
# Example: Fix non-compliant resource
aws s3api put-bucket-versioning \
  --bucket my-bucket \
  --versioning-configuration Status=Enabled

# Verify remediation
aws configservice describe-compliance-by-config-rule \
  --config-rule-names s3-bucket-versioning-enabled
```

### 4. Schedule Follow-Up

```bash
# Create reminder for next audit
echo "Next audit: $(date -d '+1 year' +%Y-%m-%d)" >> audit-schedule.txt
```

---

## Audit Checklist

### Pre-Audit (2 weeks before)
- [ ] Run compliance validation scripts
- [ ] Generate evidence package
- [ ] Review and remediate non-compliant resources
- [ ] Prepare documentation package
- [ ] Export CloudFormation templates
- [ ] Create auditor read-only IAM user

### During Audit
- [ ] Provide evidence package to auditors
- [ ] Be available for questions
- [ ] Demonstrate controls in real-time
- [ ] Document all auditor requests
- [ ] Track findings in real-time

### Post-Audit
- [ ] Address all findings
- [ ] Update documentation
- [ ] Implement corrective actions
- [ ] Schedule follow-up audit
- [ ] Archive evidence package
- [ ] Remove auditor access

---

## Resources

- [Auditor Compliance Mapping](AUDITOR_COMPLIANCE_MAPPING.md)
- [Compliance Posture](compliance/COMPLIANCE_POSTURE.md)
- [Automated Compliance](compliance/AUTOMATED_COMPLIANCE.md)
- [AWS Compliance Resources](https://aws.amazon.com/compliance/resources/)
- [AWS Artifact](https://aws.amazon.com/artifact/) - Compliance reports and agreements
