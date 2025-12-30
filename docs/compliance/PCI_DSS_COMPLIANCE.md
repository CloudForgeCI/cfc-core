# PCI-DSS Compliance Guide for CloudForge CI

## IMPORTANT: Scope and Limitations

**CloudForge CI provides infrastructure controls only. This is NOT full PCI-DSS compliance.**

### What This Gives You:
- Infrastructure-level security controls aligned with PCI-DSS v3.2.1
- Automated validation during deployment
- Evidence collection for infrastructure requirements
- AWS services configured per PCI-DSS best practices

### What You Still Need:
- **Application-level controls** - See [PCI_DSS_APPLICATION_SECURITY.md](PCI_DSS_APPLICATION_SECURITY.md)
- **Organizational policies** - Security policy, incident response, acceptable use, etc.
- **Procedures** - Change management, access reviews, vulnerability management
- **Training** - Security awareness training for all personnel
- **Assessment** - Validation by a Qualified Security Assessor (QSA)
- **Documentation** - Risk assessments, network diagrams, data flow diagrams

**You cannot achieve PCI-DSS compliance with infrastructure alone.** This guide shows you how to configure the infrastructure foundations. Full compliance requires organizational controls and QSA validation.

---

## Automated PCI-DSS Validation

CloudForge CI includes comprehensive **PCI-DSS validation rules** that automatically enforce compliance requirements for production environments processing cardholder data.

The system automatically validates **12 PCI-DSS requirements** during deployment:

| Requirement | Control | Status |
|-------------|---------|--------|
| Req 1 | Firewall & Network Segmentation | ✅ Automated Validation |
| Req 2 | Secure Configurations | ✅ Automated Validation |
| Req 3 | Encryption at Rest | ✅ Automated Validation |
| Req 4 | Encryption in Transit | ✅ Automated Validation |
| Req 6.6 | Web Application Firewall | ✅ Automated Validation |
| Req 7 | Access Control | ✅ Automated Validation |
| Req 8 | Authentication & MFA | ✅ Automated Validation |
| Req 10 | Audit Logging | ✅ Automated Validation |
| Req 11 | Security Monitoring | ✅ Automated Validation |

**Implementation:** [`PciDssRules.java`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java)

The validation automatically runs for **PRODUCTION security profiles** only.

---

## Overview

This guide covers deploying CloudForge CI for environments that **process, store, or transmit cardholder data**. It assumes your Jenkins infrastructure will be part of the Cardholder Data Environment (CDE).

---

## Quick Start: Infrastructure Deployment

### Minimum Required Configuration

```json
{
  "securityProfile": "production",
  "tier": "production",
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
  "ssoGroupId": "your-sso-group-uuid",
  "ssoTargetAccountId": "123456789012",
  "wafEnabled": true,
  "awsConfigEnabled": true,
  "auditManagerEnabled": true,
  "enableEncryption": true,
  "enableMonitoring": true,
  "logRetentionDays": 730
}
```

**Important**: `"auditManagerEnabled": true` enables PCI-DSS compliance validation during deployment. Without this flag, validators are skipped.

### Deploy with Interactive CLI

```bash
cd cfc-testing
mvn spring-boot:run -Dspring-boot.run.arguments="--interactive"
```

When prompted:
1. **Security Profile**: Select `PRODUCTION`
2. **Network Mode**: Select `private-with-nat`
3. **Authentication**: Select `alb-oidc` or `jenkins-oidc`
4. **Configure SSO**: Provide AWS SSO details (with MFA enforced)
5. **Enable WAF**: Yes
6. **Enable AWS Config**: Yes
7. **Enable Audit Manager**: Yes
8. **Select Framework**: PCI DSS 3.2.1

---

## PCI-DSS Requirements Coverage

### Requirement 1: Install and maintain a firewall configuration

**Infrastructure Controls Provided:**
- VPC with private subnets (no direct internet access)
- Security groups restricting traffic between components
- NAT Gateway for controlled outbound access
- VPC Flow Logs capturing all network traffic

**Automated Validation:**
- VPC is configured
- Network mode is "private-with-nat" (not "public-no-nat")
- Security groups are properly configured

**Evidence Collection:**
- VPC Flow Logs (2-year retention)
- Security group rules in AWS Config
- Network architecture diagram (if using Audit Manager)

---

### Requirement 2: Do not use vendor-supplied defaults

**Infrastructure Controls Provided:**
- Custom security group configurations
- Least-privilege IAM roles (MINIMAL profile for production)
- Encrypted storage with customer-managed settings

**Manual Actions Required:**
- Change default Jenkins admin password immediately after deployment
- Configure Jenkins security realm (use SSO/OIDC, not local users)
- Disable/remove default Jenkins plugins not needed
- Review and harden Jenkins global security configuration

**Evidence:**
- IAM policies in AWS Config
- Jenkins configuration management logs
- Change control documentation

---

### Requirement 3: Protect stored cardholder data

**Infrastructure Controls Provided:**
- EBS encryption (all volumes encrypted at rest)
- EFS encryption at rest (AES-256)
- S3 encryption for artifacts and backups
- Automated backup with 90-day retention

**Application-Level Actions Required:**
1. **Data Classification**: Identify if/where cardholder data is stored
   - Jenkins build logs
   - Artifacts in S3
   - Configuration files
   - Test databases

2. **Data Retention**: Configure data retention policies
   ```groovy
   // Jenkins Pipeline - Mask sensitive data
   wrap([$class: 'MaskPasswordsBuildWrapper']) {
     // Your build steps
   }
   ```

3. **PAN Storage**: If storing PANs:
   - Encrypt using strong cryptography
   - Mask PAN (show only first 6 and last 4 digits)
   - Implement data retention and deletion policies

**Evidence:**
- AWS Config rules (EBS encryption, S3 encryption)
- Data flow diagrams
- Data retention policy documentation

---

### Requirement 4: Encrypt transmission of cardholder data

**Infrastructure Controls Provided:**
- TLS/SSL for all ALB traffic (ACM certificates)
- EFS encryption in transit (TLS)
- SSL-only S3 access enforcement
- VPC private endpoints (no internet exposure)

**Automated Validation:**
- TLS certificate is configured
- EFS encryption in transit is enabled

**Evidence Collection:**
- ACM certificate configuration
- ALB listener rules (HTTPS only)
- VPC endpoint configurations

---

### Requirement 6: Develop and maintain secure systems

**Infrastructure Controls Provided:**
- **Requirement 6.6**: Web Application Firewall (WAF) protection
  - SQL injection protection
  - Known bad inputs blocking
  - Linux OS exploit protection

**Manual Actions Required:**

1. **Patch Management** (Req 6.2):
   ```bash
   # Update Jenkins regularly
   # Automated with AWS Systems Manager Patch Manager
   aws ssm create-patch-baseline --name "Jenkins-PCI-Patches"
   ```

2. **Secure Development** (Req 6.3):
   - Implement code review process
   - Use SAST/DAST tools in Jenkins pipelines
   - Separate development/test/production environments

3. **Change Control** (Req 6.4):
   - Document all infrastructure changes via Git
   - Use pull request process
   - Implement approval workflows

**Evidence:**
- WAF logs in CloudWatch
- Code review records in Git
- Change control tickets
- Vulnerability scan reports

---

### Requirement 7: Restrict access to cardholder data

**Infrastructure Controls Provided:**
- IAM role-based access control (RBAC)
- Least-privilege IAM policies (MINIMAL profile)
- Security group-based network access control

**Manual Actions Required:**

1. **Access Control Matrix**: Document who needs access to what
   ```
   Role              | Jenkins Access | AWS Console | Cardholder Data
   ------------------|---------------|-------------|----------------
   Developer         | Build Jobs    | No          | No
   DevOps Admin      | Full          | Limited     | No
   Security Admin    | Audit Logs    | Full        | No
   ```

2. **Jenkins Authorization**:
   ```groovy
   // Configure role-based authorization in Jenkins
   // Use Matrix Authorization Strategy or Role Strategy Plugin
   ```

**Evidence:**
- IAM policies and roles
- Jenkins authorization configuration
- Access control matrix documentation
- Quarterly access reviews

---

### Requirement 8: Identify and authenticate access

**Infrastructure Controls Provided:**
- AWS SSO integration with MFA support
- ALB-OIDC authentication (before reaching Jenkins)
- Jenkins-OIDC authentication (at application level)

**Automated Validation:**
- Authentication is not set to "none"
- SSO Instance ARN is configured when using OIDC

**Manual Actions Required:**

1. **Enforce MFA** (Req 8.3):
   ```bash
   # In AWS SSO, enforce MFA for all users
   aws sso-admin create-instance-access-control-attribute-configuration \
     --instance-arn arn:aws:sso:::instance/ssoins-xxx \
     --require-mfa true
   ```

2. **Password Policy** (Req 8.2):
   - Minimum 8 characters
   - Must contain uppercase, lowercase, numbers, special characters
   - Cannot reuse last 4 passwords
   - Max 90-day password age

3. **Account Lockout** (Req 8.1.6):
   - Lock after 6 failed attempts
   - Lockout duration: 30 minutes

**Evidence:**
- AWS SSO configuration
- MFA enforcement logs
- Password policy documentation
- CloudTrail logs showing authentication events

---

### Requirement 9: Restrict physical access

**Manual Actions Required:**
This is an organizational control for physical data centers. For AWS-hosted infrastructure:

1. **Document AWS Physical Security**:
   - AWS SOC 2 Type II report
   - AWS ISO 27001 certification
   - AWS PCI-DSS Attestation of Compliance (AOC)

2. **Media Disposal**:
   - Document EBS volume deletion process
   - Ensure secure deletion of backups

**Evidence:**
- AWS compliance documentation
- Media disposal policy
- Visitor logs (for on-premises components)

---

### Requirement 10: Track and monitor all access

**Infrastructure Controls Provided:**
- **CloudTrail**: All AWS API calls (2-year retention)
- **VPC Flow Logs**: All network traffic (2-year retention)
- **ALB Access Logs**: All web requests (2-year retention)
- **CloudWatch Logs**: Application logs (2-year retention)
- **File Integrity**: CloudTrail log file validation enabled

**Automated Validation:**
- CloudTrail is enabled
- VPC Flow Logs are enabled
- ALB access logging is enabled
- Log retention >= 365 days

**Manual Actions Required:**

1. **Daily Log Review** (Req 10.6):
   ```bash
   # Set up CloudWatch Alarms for security events
   # Configure SNS notifications for:
   # - Failed login attempts
   # - Privilege escalation
   # - Unusual API activity
   ```

2. **Log Protection** (Req 10.5):
   - Logs are automatically protected (S3 with versioning)
   - CloudTrail log file validation enabled
   - IAM policies prevent unauthorized log deletion

**Evidence:**
- CloudTrail logs
- VPC Flow Logs
- Log review records
- Incident response logs

---

### Requirement 11: Regularly test security systems

**Infrastructure Controls Provided:**
- **GuardDuty**: Threat detection (24/7 monitoring)
- **AWS Config**: Configuration compliance monitoring
- **WAF**: Web attack detection and blocking
- **Security Monitoring**: CloudWatch alarms for security events

**Automated Validation:**
- GuardDuty is enabled
- Security monitoring is enabled
- AWS Config is enabled (recommended)

**Manual Actions Required:**

1. **Quarterly Vulnerability Scans** (Req 11.2):
   ```bash
   # Use AWS Inspector or approved scanning vendor
   aws inspector2 create-findings-report \
     --report-format JSON \
     --s3-destination bucket=my-pci-reports
   ```

2. **Annual Penetration Testing** (Req 11.3):
   - Engage qualified penetration testing firm
   - Test external and internal network perimeters
   - Test web applications (if public-facing)

3. **File Integrity Monitoring** (Req 11.5):
   - CloudTrail provides API-level FIM
   - Consider AWS Systems Manager for OS-level FIM

**Evidence:**
- Quarterly vulnerability scan reports
- Annual penetration test reports
- GuardDuty findings
- Security monitoring dashboards

---

### Requirement 12: Maintain a security policy

**Manual Actions Required:**

This requirement is entirely policy and procedure-based:

1. **Information Security Policy** (Req 12.1):
   - Document overall security policy
   - Define roles and responsibilities
   - Establish acceptable use policy

2. **Risk Assessment** (Req 12.2):
   - Conduct annual risk assessments
   - Document threats and vulnerabilities
   - Implement risk treatment plans

3. **Security Awareness** (Req 12.6):
   - Security awareness training for all personnel
   - Training upon hire and annually
   - Acknowledge understanding of policies

4. **Incident Response Plan** (Req 12.10):
   - Create incident response procedures
   - Define roles and responsibilities
   - Test plan annually

5. **Service Provider Management** (Req 12.8):
   - Maintain list of service providers
   - AWS PCI-DSS AOC
   - Ensure providers are also compliant

**Evidence:**
- Security policy documents
- Risk assessment reports
- Training records
- Incident response plan
- Service provider inventory
- AWS Audit Manager assessment reports

---

## Deployment Steps

### Step 1: Pre-Deployment Checklist

- [ ] AWS account has AWS Audit Manager enabled
- [ ] AWS SSO configured with MFA enforced
- [ ] Domain and TLS certificate available
- [ ] Network architecture planned (CDE segmentation)
- [ ] Reviewed PCI-DSS requirements above

### Step 2: Deploy Infrastructure

```bash
cd cfc-testing
mvn clean install

# Interactive deployment with PCI-DSS options
mvn spring-boot:run -Dspring-boot.run.arguments="--interactive"
```

Or use deployment context JSON:

```bash
# Create pci-dss-deployment.json
cat > pci-dss-deployment.json << 'EOF'
{
  "securityProfile": "production",
  "tier": "production",
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
  "ssoGroupId": "your-sso-group-uuid",
  "ssoTargetAccountId": "123456789012",
  "wafEnabled": true,
  "awsConfigEnabled": true,
  "auditManagerEnabled": true,
  "auditManagerFrameworkId": "PCI-DSS",
  "domain": "example.com",
  "subdomain": "jenkins-prod",
  "region": "us-east-1"
}
EOF

# Deploy
mvn spring-boot:run -Dspring-boot.run.arguments="--deployment-context=pci-dss-deployment.json"
```

### Step 3: Validate PCI-DSS Controls

The system automatically validates PCI-DSS controls during deployment:

```
=== PCI-DSS Validation Results ===

✓ Network Security (Req 1)
✓ Encryption at Rest (Req 3)
✓ Encryption in Transit (Req 4)
✓ WAF Protection (Req 6.6)
✓ Authentication (Req 8)
✓ Audit Logging (Req 10)
✓ Security Monitoring (Req 11)

VALIDATION PASSED: All infrastructure controls enabled
```

If validation fails, you'll see specific errors:

```
❌ PCI-DSS Req 1.3: Public network mode prohibited for cardholder data environment.
   Use 'private-with-nat' for production systems processing card data.

❌ PCI-DSS Req 8.2: Authentication must be enabled for production environments.
   Configure authMode='alb-oidc' or 'jenkins-oidc' with MFA-enabled identity provider.
```

### Step 4: Post-Deployment Configuration

1. **Configure Jenkins Security**:
   ```
   - Change admin password
   - Enable Matrix Authorization
   - Install security plugins
   - Configure OIDC authentication
   - Disable unnecessary features
   ```

2. **Enable AWS Audit Manager**:
   ```bash
   # Navigate to AWS Audit Manager console
   # Review the PCI-DSS assessment
   # Verify evidence collection is working
   aws auditmanager get-assessment --assessment-id <id>
   ```

3. **Configure Monitoring Alerts**:
   ```bash
   # Subscribe to SNS topics for security alerts
   aws sns subscribe \
     --topic-arn arn:aws:sns:us-east-1:123456789012:security-alerts-production \
     --protocol email \
     --notification-endpoint security-team@example.com
   ```

### Step 5: Document the Environment

Create the following documentation:

1. **System Architecture Diagram**:
   - Network topology
   - Data flow diagram
   - CDE boundaries

2. **Data Flow Documentation**:
   - Where cardholder data enters the system
   - How it's processed
   - Where it's stored
   - When it's deleted

3. **Configuration Management**:
   - All infrastructure as code (CDK)
   - Configuration baselines
   - Change control process

---

## Ongoing Compliance Activities

### Daily
- Review security monitoring alarms
- Check GuardDuty findings
- Monitor failed authentication attempts

### Weekly
- Review CloudTrail logs
- Check AWS Config compliance status
- Review WAF blocked requests

### Monthly
- Access review (validate users still need access)
- Review and approve changes
- Check backup completion status

### Quarterly
- Vulnerability scans (external and internal)
- Review security policies
- Update risk assessments
- Generate Audit Manager assessment reports

### Annually
- Penetration testing
- Security awareness training
- Policy review and updates
- Full PCI-DSS assessment by QSA

---

## Evidence Collection for PCI-DSS Validation

CloudForge CI automatically collects evidence through AWS Audit Manager:

### Automated Evidence Sources

1. **CloudTrail**: API activity, user actions, configuration changes
2. **AWS Config**: Resource configurations, compliance status
3. **VPC Flow Logs**: Network traffic patterns
4. **CloudWatch Logs**: Application logs, security events
5. **GuardDuty**: Threat detection findings
6. **WAF Logs**: Web attack attempts

### Manual Evidence Required

1. **Policies and Procedures**:
   - Information security policy
   - Acceptable use policy
   - Incident response plan
   - Change management procedures

2. **Training Records**:
   - Security awareness training completion
   - Training materials

3. **Testing Reports**:
   - Quarterly vulnerability scans
   - Annual penetration test reports

4. **Reviews**:
   - Quarterly access reviews
   - Annual policy reviews
   - Risk assessments

---

## Troubleshooting

### Validation Errors

**Error**: "Public network mode prohibited for cardholder data environment"
```bash
# Solution: Update networkMode in deployment context
"networkMode": "private-with-nat"
```

**Error**: "Authentication must be enabled for production environments"
```bash
# Solution: Configure SSO authentication
"authMode": "alb-oidc",
"ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxx",
"ssoGroupId": "your-group-id",
"ssoTargetAccountId": "123456789012"
```

**Error**: "Web Application Firewall (WAF) REQUIRED for PCI-DSS compliance in PRODUCTION"
```bash
# Solution: Enable WAF (REQUIRED for PCI-DSS)
"wafEnabled": true
```

**Error**: "Log retention must be at least 365 days"
```bash
# Solution: The production profile defaults to 2 years (730 days)
# This should not fail unless manually overridden
# Ensure you're using securityProfile: "production"
```

### AWS Audit Manager Not Collecting Evidence

1. **Enable Audit Manager**:
   ```bash
   aws auditmanager register-account --kms-key <key-id>
   ```

2. **Configure Data Sources**:
   - CloudTrail must be enabled
   - AWS Config must be enabled
   - Security Hub (optional but recommended)

3. **Check IAM Permissions**:
   - Audit Manager role needs read access to CloudTrail, Config, Security Hub

---

## Cost Considerations

Typical monthly costs for PCI-DSS compliant production deployment:

| Service | Approximate Cost |
|---------|-----------------|
| EC2/Fargate (Jenkins) | $50-300 |
| EFS Storage | $30-100 |
| NAT Gateway | $45 |
| ALB | $20 |
| CloudTrail | $5 |
| VPC Flow Logs | $10-50 |
| AWS Config | $10-20 |
| GuardDuty | $30-100 |
| WAF | $5 + per-request |
| Audit Manager | $1 per 100k evidence |
| S3 (logs/backups) | $10-50 |
| **Total** | **$215-690/month** |

Costs scale with:
- Number of instances
- Traffic volume
- Log volume
- Storage requirements

---

## Additional Resources

### AWS Resources
- [AWS PCI-DSS Compliance Documentation](https://aws.amazon.com/compliance/pci-dss-level-1-faqs/)
- [AWS Artifact (download PCI-DSS AOC)](https://console.aws.amazon.com/artifact/)
- [AWS Config PCI-DSS Conformance Pack](https://docs.aws.amazon.com/config/latest/developerguide/conformancepack-sample-templates.html)

### PCI-DSS Resources
- [PCI Security Standards Council](https://www.pcisecuritystandards.org/)
- [PCI-DSS v3.2.1 Documentation](https://www.pcisecuritystandards.org/document_library)
- [Cloud Computing Guidelines](https://www.pcisecuritystandards.org/pdfs/PCI_DSS_v2_Cloud_Guidelines.pdf)

### Jenkins Security
- [Jenkins Security Best Practices](https://www.jenkins.io/doc/book/security/)
- [Securing Jenkins](https://www.jenkins.io/doc/book/security/securing-jenkins/)

---

## Support and Questions

For questions about PCI-DSS compliance with CloudForge CI:

1. Review the validation errors during deployment
2. Check the AWS Audit Manager assessment reports
3. Consult with a Qualified Security Assessor (QSA)
4. Review AWS PCI-DSS compliance documentation

**Remember**: This infrastructure provides the technical foundation. Full PCI-DSS compliance requires organizational policies, procedures, and validation by a Qualified Security Assessor (QSA).
