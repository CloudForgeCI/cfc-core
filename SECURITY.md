# Security Policy

## Supported versions

Security fixes are released for the current 3.2.x series.

| Version | Supported |
|---|---|
| 3.2.x | Yes |
| < 3.2 | No |

Every merge to `develop` is published as a new patch release, so upgrading to the latest
3.2.x version is the way to pick up fixes.

## Reporting a vulnerability

Do not open a public issue for a security problem.

- **Preferred:** [open a private security advisory](https://github.com/CloudForgeCI/cfc-core/security/advisories/new)
  on GitHub.
- **Alternative:** email security@cloudforgeci.com with "SECURITY" in the subject.

Include what is affected and why it matters, steps to reproduce, your environment (version,
deployment target, region, relevant configuration with secrets removed), and a suggested fix if
you have one.

Response targets:

- Acknowledgement within 48 hours, and a status update within a week.
- Critical issues (remote code execution, credential exposure): fix targeted within 1-3 days.
- High severity (privilege escalation, data exposure): 1-2 weeks.
- Medium and low severity: 30-90 days, depending on impact.

Published advisories are listed under the repository's
[Security Advisories](https://github.com/CloudForgeCI/cfc-core/security/advisories). To be
notified, watch the repository and enable security alerts.

## Security model

CloudForge generates AWS infrastructure from a deployment context. What it provides:

- **Network**: VPCs with public, private-with-NAT, or isolated subnets, and security groups
  scoped to the ports each application declares. No inbound SSH: EC2 instances are reached
  through SSM Session Manager and Fargate tasks through ECS Exec.
- **Encryption**: encryption at rest for EFS, EBS, S3, and RDS is on by default; TLS on the
  load balancer when `enableSsl` is set.
- **Authentication**: ALB-level or application-level OIDC with Amazon Cognito, an external OIDC
  provider, or IAM Identity Center, with optional MFA.
- **Secrets**: database credentials and OIDC client secrets are stored in AWS Secrets Manager
  and referenced at runtime, not written into templates. CloudForge does not ship default
  passwords.
- **IAM**: roles are generated per deployment from the security profile's IAM profile. Review
  the synthesized template to confirm the permissions fit your organization's requirements.
- **Security profiles**: `dev`, `staging`, and `production` set defaults for logging, flow
  logs, WAF, backups, and compliance enforcement. See
  [Security Profiles](docs/guides/SECURITY_RULES_README.md) for the exact defaults.

### Compliance

CloudForge implements and validates infrastructure controls mapped to SOC 2, PCI DSS, HIPAA,
and GDPR, using synthesis-time rules, cdk-nag, cfn-guard, and AWS Config. These are technical
controls only. Organizational controls (training, incident response procedures, risk
assessments, vendor agreements, physical security, privacy notices, and data subject rights)
are outside its scope, and a passing validation does not by itself make a deployment
compliant. See [Auditor Compliance Mapping](docs/AUDITOR_COMPLIANCE_MAPPING.md) for which
controls are supported, partially supported, or not covered.

### Resources retained on deletion

Some resources are kept when a stack is deleted, to prevent data loss:

- Cognito user pools created with the `production` profile.
- EFS and EBS volumes when `retainStorage` is `true`.
- Log groups and compliance buckets with a `RETAIN` removal policy in `staging` and
  `production`.

Delete these manually once you no longer need them.

## Recommendations for deployments

- Use the `production` profile for production workloads, with `enableSsl`, an OIDC
  `authMode`, `cognitoMfaEnabled`, and `networkMode: "private-with-nat"`.
- Grant the minimum IAM permissions needed to deploy, and use roles instead of long-lived
  access keys.
- Never commit `deployment-context.json` files that contain secrets or account-specific
  identifiers; reference secrets by Secrets Manager name.
- Enable CloudTrail, GuardDuty, and AWS Config (`cloudTrailEnabled`, `guardDutyEnabled`,
  `awsConfigEnabled`) where your account does not already provide them centrally.

## Dependency and supply-chain scanning

The `security-scan.yml` workflow generates a CycloneDX SBOM and runs OWASP Dependency-Check on
pushes and pull requests to `develop` and weekly. To run the checks locally:

```bash
mvn clean package                            # SBOM in target/cfc-core-sbom.json
mvn dependency-check:check -Psecurity-scan   # report in target/dependency-check-report.html
mvn versions:display-dependency-updates
```

## Questions

For general security questions that are not vulnerabilities, open a
[GitHub Discussion](https://github.com/CloudForgeCI/cfc-core/discussions) or email
security@cloudforgeci.com.
