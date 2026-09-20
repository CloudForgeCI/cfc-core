# Application Compliance Considerations

What CloudForge configures when a compliance framework is enabled, and which application-level controls remain the
operator's responsibility. This is general guidance, not legal or audit advice; confirm requirements with your
compliance team or assessor.

## Configuration

| Key | Values | Notes |
|-----|--------|-------|
| `complianceFrameworks` | `soc2`, `pci-dss`, `hipaa`, `gdpr` | Case-insensitive; comma, space, or `+` separated (for example `"SOC2,HIPAA"`) or a JSON array |
| `complianceMode` | `enforce`, `advisory`, `disabled` | Defaults to `enforce` for the `production` profile and `advisory` otherwise |
| `securityProfile` | `dev`, `staging`, `production` | Sets baseline security defaults (`stage`/`prod`/`development` are accepted aliases) |

The frameworks are defined by `ComplianceFrameworkType` in `cloudforge-core/src/main/java/com/cloudforge/core/enums/`.
Additional frameworks can be added as `FrameworkRules` plugins; see the
[compliance plugin guide](../plugins/COMPLIANCE-PLUGIN-GUIDE.md).

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "PCI-DSS,SOC2",
  "complianceMode": "enforce"
}
```

## Production profile defaults

With `securityProfile: production` (`ProductionSecurityProfileConfiguration`):

- EBS, EFS (at rest and in transit), and S3 encryption enabled.
- Private subnets with two NAT gateways, unless `networkMode` is `public`.
- VPC Flow Logs, CloudTrail, GuardDuty, AWS Config, and AWS Audit Manager enabled unless explicitly disabled in the
  deployment context. Frameworks that require a control keep it enabled regardless of the override.
- CloudWatch Logs retained for six years unless `logRetentionDays` is set; log groups use `RemovalPolicy.RETAIN`.
- ALB access logging enabled.
- Automated backups with 90-day retention; Multi-AZ enforced.
- AWS WAF disabled unless `wafEnabled` is `true` or an enabled framework requires it.

AWS Config allows one configuration recorder per region and account; set `createConfigInfrastructure: false` if the
account already has one. Audit Manager must be enabled in the account before CloudForge can use it.

Staging and dev profiles apply lighter defaults. See [Multi-framework compliance](../compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
and the [compliance documentation index](../compliance/README.md) for the full control mapping.

## Operator responsibilities by application type

CloudForge provisions infrastructure controls. The following are configured inside the application and are not
managed by CloudForge.

### Databases (`postgresql`, `redis`, and RDS instances)

Databases often hold personal data, PHI, or cardholder data, so every framework is likely in scope.

- Store credentials in AWS Secrets Manager; never keep default passwords.
- Require TLS for client connections.
- Enable database audit logging (for PostgreSQL, the `pgaudit` extension).
- Set backup retention to meet your framework's requirement.
- Keep the service in private subnets.
- For Redis, require authentication, set TTLs on cached sensitive data, and do not store unencrypted cardholder data.
- For regulated data, prefer RDS or ElastiCache over self-managed containers.

### Source control (`gitlab`, `gitea`)

Repositories can contain secrets, personal data in commit history, and test fixtures with customer data.

- Require MFA or SSO for all users and review access regularly.
- Enable the application's audit events.
- Enable secret detection, branch protection, and required code review on production branches.
- Provide user data export and deletion procedures (GDPR).

### CI/CD (`jenkins`, `gitlab`, `drone`)

Pipelines hold cloud credentials and can deploy to production.

- Keep secrets in the platform's credential store or Secrets Manager, never in job definitions.
- Require approval for production deployments and keep development and production pipelines separate (SOC 2 CC8.1,
  PCI-DSS 6.x).
- Retain build and deployment audit logs. With `application-oidc`, CloudForge enables the Jenkins Audit Trail plugin.
- Scan build artifacts and images before deployment.

### Monitoring (`grafana`, `prometheus`)

- Require authentication. Grafana supports `application-oidc`; Prometheus has no built-in authentication and no
  CloudForge OIDC integration, so keep it on a private network.
- Disable anonymous access and avoid personal data or PHI in dashboards and metric labels.
- Define metric retention.

### E-commerce, CRM, and LMS platforms

See the [CMS guide](CMS.md#compliance-considerations). Platforms that process payment data should enable
`pci-dss`; see the [PCI-DSS compliance guide](../compliance/PCI_DSS_COMPLIANCE.md).

## Scoping guidance

| Data handled | Suggested settings |
|--------------|--------------------|
| Payment card data | `production` profile, `pci-dss` framework, private network, RDS with encryption; quarterly scans and annual penetration tests are still required |
| Protected health information | `production` profile, `hipaa` framework, signed AWS BAA, TLS to the database, six-year log retention |
| Personal data of EU residents | `gdpr` framework; set `gdprDataTransferApproved` only after approving any transfer outside the EU |
| General business data | `staging` or `production` profile with `soc2` |

## References

- [PCI Security Standards Council](https://www.pcisecuritystandards.org/)
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/)
- [AICPA SOC 2](https://us.aicpa.org/interestareas/frc/assuranceadvisoryservices/aicpasoc2report)
- [GDPR text](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [OIDC authentication](OIDC.md)
