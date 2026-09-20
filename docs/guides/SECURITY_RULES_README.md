# Security Profiles

Every CloudForge deployment has a **security profile**: a set of defaults for network
access, encryption, logging, threat detection, backups, and compliance validation. Set it in
`deployment-context.json`:

```json
{
  "securityProfile": "production"
}
```

The profiles are `dev` (the default), `staging`, and `production`. They are implemented by
`DevSecurityProfileConfiguration`, `StagingSecurityProfileConfiguration`, and
`ProductionSecurityProfileConfiguration` in `com.cloudforgeci.api.core.security`.

The security profile also selects the default IAM profile; see [IAM Rules](IAM_RULES.md).

---

## Profiles

### `dev`

For evaluation, internal tools, and feature branches.

- Single-AZ; VPC flow logs, CloudTrail, GuardDuty, AWS Config, WAF, ALB access logs, and automated backups are off by default
- EBS and EFS encryption at rest on by default (`enableEncryption: false` turns it off)
- Compliance findings are reported as warnings (`complianceMode` defaults to `advisory`)

> Do not use `dev` for workloads that handle real user data.

### `staging`

For testing configuration before production.

- Multi-AZ
- WAF, VPC flow logs, and automated backups on by default
- CloudTrail, GuardDuty, AWS Config, and ALB access logs off unless set in the deployment context or required by a selected framework
- Compliance findings are reported as warnings (`complianceMode` defaults to `advisory`)

### `production`

- Multi-AZ, with at least two instances and auto scaling
- VPC flow logs and automated backups on by default
- CloudTrail, GuardDuty, AWS Config, and ALB access logs off unless set in the deployment context or required by a selected framework
- Flow logs (and ALB access logs, when enabled) retained for six years; logs use a `RETAIN` removal policy
- EBS, EFS, and S3 encryption on
- WAF off by default unless a selected compliance framework requires it
- Compliance violations fail synthesis (`complianceMode` defaults to `enforce`)

---

## Comparison

Defaults when no compliance framework is selected and no field is overridden:

| | `dev` | `staging` | `production` |
|--|-------|-----------|--------------|
| Multi-AZ | Off | On | On |
| WAF (`wafEnabled`) | Off | On | Off |
| ALB access logs (`albAccessLogging`) | Off | Off | Off |
| VPC flow logs (`enableFlowlogs`) | Off | On | On |
| CloudTrail (`cloudTrailEnabled`) | Off | Off | Off |
| GuardDuty (`guardDutyEnabled`) | Off | Off | Off |
| AWS Config (`awsConfigEnabled`) | Off | Off | Off |
| Automated backups (`automatedBackupEnabled`) | Off | On | On |
| EBS/EFS encryption at rest | On | On | On |
| HTTPS strict (`httpsStrictEnabled`) | Off | Off | Off |
| RDS deletion protection | Off | When required by a framework | When required by a framework |
| Compliance mode | `advisory` | `advisory` | `enforce` |

`cloudTrailEnabled`, `guardDutyEnabled`, `awsConfigEnabled`, and `albAccessLogging` default to
`false` in the deployment context, so the profile's own default for them does not apply; set
them explicitly to enable them. A selected compliance framework turns on the controls it requires (for example WAF, HTTPS
strict, or deletion protection), regardless of the profile default.

When `httpsStrictEnabled` is on and `enableSsl` is true, the load balancer has no HTTP
listener on port 80; only HTTPS is served.

---

## Overriding Individual Settings

The profile supplies defaults. You can override individual fields in
`deployment-context.json` without changing the profile. For example, turning on GuardDuty
in staging:

```json
{
  "securityProfile": "staging",
  "guardDutyEnabled": true
}
```

Or testing HTTPS and authentication in dev:

```json
{
  "securityProfile": "dev",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true
}
```

For most controls, a requirement from a selected compliance framework takes precedence over
an override. Where an explicit override can still turn a required control off (for example
`wafEnabled: false`), the framework's validation rules report
the violation, which fails synthesis in `enforce` mode.

For the full list of `deployment-context.json` fields, see the
[configuration reference](../ADVANCED.md).

---

## Compliance Frameworks

Compliance frameworks are independent of security profiles. They add required controls and
validation rules on top of the profile and your overrides.

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "PCI-DSS,SOC2"
}
```

Supported values: `PCI-DSS`, `HIPAA`, `SOC2`, `GDPR` (comma-separated, case-insensitive).

Framework rules check items such as authentication, encryption at rest, log retention,
network isolation, and access control. For example, the HIPAA and GDPR rules report
`authMode: "none"` as a violation.

`complianceMode` controls what happens when a rule fails:

- `enforce` — synthesis fails with the rule's remediation message (default for `production`)
- `advisory` — the failure is logged as a warning and synthesis continues (default for `dev` and `staging`)

Passing these checks shows that the synthesized configuration includes the checked
controls. It is not a certification. See the [compliance documentation](../compliance/README.md)
for per-framework details.

---

## Accessing Instances and Containers

No profile opens port 22. Shell access goes through AWS Systems Manager.

**EC2 instances:**

```bash
aws ssm start-session --target <instance-id>
```

**Fargate tasks** (ECS Exec is enabled on every Fargate service):

```bash
aws ecs execute-command \
  --cluster <cluster-name> \
  --task <task-id> \
  --container <container-name> \
  --interactive \
  --command "/bin/sh"
```

The caller's IAM identity needs `ssm:StartSession` (EC2) or `ecs:ExecuteCommand` (Fargate).
Both actions are recorded by CloudTrail when a trail is enabled in the account.
