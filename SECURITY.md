# Security

## Supported Versions

We're currently supporting 2.0.x with security patches. If you're on anything older, time to upgrade.

| Version | Supported          |
| ------- | ------------------ |
| 2.0.x   | :white_check_mark: |
| < 2.0   | :x:                |

## Found a Security Issue?

Please don't open a public issue. Instead:

**Preferred:** Use [GitHub Security Advisories](https://github.com/CloudForgeCI/cfc-core/security/advisories/new)

**Alternative:** Email security@cloudforgeci.com with "SECURITY" in the subject

Include whatever helps us reproduce and fix it:
- What's broken and why it matters
- Steps to reproduce
- Your environment (version, region, config)
- Ideas for fixing it (if you have any)

**Response times:**
- We'll acknowledge within 48 hours
- Update you within a week
- Critical issues (RCE, creds exposed): 1-3 days
- High severity (privilege escalation, data leaks): 1-2 weeks
- Medium/Low: 30-90 days depending on impact

## What's Built In

### Infrastructure

- VPC with public/private subnets
- Security groups following least privilege
- Encryption everywhere (EFS, S3, EBS at rest; TLS in transit)
- IAM roles scoped to what they actually need

### Authentication

Pick what works for your setup:
- **ALB OIDC**: Authentication at the load balancer (before traffic hits Jenkins)
- **Cognito**: Managed user pools with password policies
- **AWS Identity Center**: SSO with your existing IdP
- **MFA**: Optional but recommended for production

### Security Profiles

We've got three profiles you can pick based on your environment:

| Profile | When to Use | What You Get |
|---------|-------------|--------------|
| **DEV** | Local/dev environments | Loose restrictions, fast iteration |
| **STAGING** | Pre-prod testing | Moderate hardening |
| **PRODUCTION** | Production workloads | Full hardening, compliance ready |

Check [SECURITY_RULES_README.md](SECURITY_RULES_README.md) for the full breakdown.

### Compliance

CloudForge automates infrastructure-level technical controls for compliance frameworks:

- **SOC2**: 16 AWS Config rules (9 base + 7 SOC2-specific) - ~17% of TSC criteria
- **HIPAA**: 17 AWS Config rules (9 base + 8 HIPAA-specific) - ~38% of implementation specs
- **PCI-DSS**: 17 AWS Config rules (9 base + 8 PCI-specific) - ~48% of technical requirements
- **GDPR**: 17 AWS Config rules (9 base + 8 GDPR-specific) - Technical measures only (~7% of total GDPR)

**What's Automated:**
- Encryption at rest (EBS, RDS, S3) and in transit (TLS 1.2+)
- IAM password policies, MFA enforcement, access key rotation
- Audit logging (CloudTrail, VPC Flow Logs) with tamper protection
- Network security (security groups, SSH restrictions)
- Continuous compliance monitoring with AWS Config

**What's Not Automated (Requires Organizational Policies):**
- Employee training and awareness programs
- Incident response procedures and breach notification
- Risk assessments and data protection impact assessments (DPIAs)
- Vendor management and business associate agreements
- Physical security controls
- Privacy notices and data subject rights workflows

**⚠️ Important:** Passing technical controls does not constitute full regulatory compliance. Organizational controls must be implemented by your security/compliance team. See [docs/AUDITOR_COMPLIANCE_MAPPING.md](docs/AUDITOR_COMPLIANCE_MAPPING.md) for a complete matrix of supported, partially supported, and unsupported controls across all frameworks.

### Monitoring & Logging

Everything's logged and monitored:
- **CloudTrail**: Every API call (enabled by default)
- **AWS Config**: Continuous compliance checks (enabled by default for PRODUCTION/STAGING)
- **CloudWatch**: Centralized security event logs (enabled by default)
- **VPC Flow Logs**: Network traffic (enabled by default)
- **Audit Manager**: Automated evidence collection (optional - set `auditManagerEnabled: true`)
- **GuardDuty**: Threat detection (enabled by default for PRODUCTION profile)
- **WAF**: Web Application Firewall (enabled by default for PRODUCTION profile)
- **Security Hub**: Centralized security findings (deployed via SOC2-specific Config rule)

**Service Enablement by Security Profile:**

| Service | DEV | STAGING | PRODUCTION |
|---------|-----|---------|------------|
| CloudTrail | ✅ | ✅ | ✅ |
| AWS Config | ❌ | ✅ | ✅ |
| CloudWatch Logs | ✅ | ✅ | ✅ |
| VPC Flow Logs | ❌ | ✅ | ✅ |
| WAF | ❌ | ❌ | ✅ |
| GuardDuty | ❌ | ❌ | ✅ |
| ALB Access Logs | ❌ | ✅ | ✅ |

To customize these settings, modify your deployment configuration:
```json
{
  "securityProfile": "PRODUCTION",
  "enableMonitoring": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "auditManagerEnabled": false,
  "awsConfigEnabled": true
}
```

### Secrets

No secrets in code. Period.
- Everything goes in **AWS Secrets Manager**
- Automatic rotation supported
- Reference secrets at runtime via environment

## Best Practices

### Deploying Securely

Production checklist:

```json
{
  "securityProfile": "PRODUCTION",
  "enableSsl": true,
  "domain": "jenkins.yourcompany.com",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoMfaEnabled": true,
  "enableMonitoring": true,
  "enableLogging": true,
  "networkMode": "private-with-nat"
}
```

### Access Control

- Grant minimum required permissions
- Use IAM roles, not access keys
- Enable MFA on privileged accounts
- Audit permissions regularly

### Secrets

- Never commit secrets to git (seriously, never)
- Store everything in Secrets Manager
- Rotate credentials regularly
- Use environment variables for config, not secrets

### Network

- SSH through bastion or VPN only
- HTTPS everywhere in production
- Least privilege on security groups
- Use VPC endpoints for AWS services

### Monitoring

- Turn on CloudTrail (enabled by default)
- Set up CloudWatch alarms (automated for key security events)
- Review logs regularly (automated compliance checks via AWS Config)
- Have an incident response plan (**required but not automated** - see [docs/AUDITOR_COMPLIANCE_MAPPING.md](docs/AUDITOR_COMPLIANCE_MAPPING.md#guidance-for-auditors-management-letter-language) for incident response guidance)

## Staying Updated

Security patches come as patch versions (2.0.1 → 2.0.2) and are documented in the [CHANGELOG](CHANGELOG.md).

**Security Patch Lifecycle:**
- **Critical vulnerabilities** (CVE with CVSS 9.0+): Patched within 7 days
- **High severity** (CVSS 7.0-8.9): Patched within 30 days
- **Medium/Low**: Addressed in next scheduled release

**Vulnerability Disclosure:**
- CVE references and security advisories: [GitHub Security Advisories](https://github.com/CloudForgeCI/cfc-core/security/advisories)
- Release notes with security fixes: [CHANGELOG.md](CHANGELOG.md)
- Subscribe for notifications: Watch this repo → Custom → Security alerts

To stay in the loop:
- Watch this repo (releases only)
- Subscribe to GitHub Security Advisories (critical for CVE notifications)
- Check the CHANGELOG before upgrading
- Review [GitHub Security tab](https://github.com/CloudForgeCI/cfc-core/security) for dependency alerts

## Things to Know

### Stack Deletion

Some resources are kept around when you delete stacks (safety first):

- **Cognito User Pools**: Retained to prevent data loss
- **EFS/S3**: Depends on your config

Clean these up manually once you're sure they're not needed.

### No Default Credentials

We don't ship default passwords. You create all credentials yourself and store them in Secrets Manager.

### IAM Policies

We create IAM roles with tight permissions. Review the generated CloudFormation templates to make sure they fit your org's requirements.

### Network Exposure

- **DEV**: More open for convenience
- **STAGING**: Moderate restrictions
- **PRODUCTION**: Locked down, SSH via bastion/VPN only

Pick the right profile for your environment.

## Dependencies

We use AWS CDK, AWS SDK for Java, and various Maven deps (see [pom.xml](pom.xml)).

Check for vulnerable dependencies:

```bash
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
```

## Security Testing

Run the security checks:

```bash
# Static analysis
mvn clean verify

# Check dependencies
mvn dependency:analyze

# Validate CloudFormation
cd cfc-testing
cdk synth
```

## Resources

- [AWS Security Best Practices](https://aws.amazon.com/security/best-practices/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CIS AWS Foundations Benchmark](https://www.cisecurity.org/benchmark/amazon_web_services)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)

## Questions?

General security questions (not vulnerabilities):
- [GitHub Discussions](https://github.com/CloudForgeCI/cfc-core/discussions/categories/security)
- security@cloudforgeci.com

Urgent security issues: see [Found a Security Issue?](#found-a-security-issue) above.

## Thanks

Security is important to us. If you find a vulnerability, please report it responsibly and we'll work to address it promptly.

---

**Last Updated**: 2025-11-20 | **Version**: 2.0.6
