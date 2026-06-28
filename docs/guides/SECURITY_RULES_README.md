# Security Profiles

Every CloudForge deployment has a **security profile** — a curated set of defaults that controls network access, encryption, authentication enforcement, observability, and compliance checks. You pick one in `deployment-context.json` and CloudForge wires everything automatically.

```json
{
  "securityProfile": "production"
}
```

Three profiles are available: `dev`, `staging`, and `production`.

---

## What each profile gives you

### `dev` — Evaluate freely

The lowest-friction option. Designed for local evaluation, internal tooling, and feature branches where you want things running quickly without compliance overhead.

- Application accessible from anywhere over HTTP or HTTPS
- No authentication required (set `authMode` to enable it)
- No WAF, no CloudTrail, no GuardDuty
- Instance and container access via AWS SSM Session Manager — no port 22 open
- ECS Exec enabled on all Fargate tasks
- Estimated cost floor: ~$35/month

> **Do not use `dev` for anything with real user data or internet-facing traffic.**

---

### `staging` — Test before you ship

A middle ground — strict enough to catch configuration problems before production, relaxed enough to allow external testing without a full domain and certificate setup.

- Application accessible from anywhere over HTTP or HTTPS
- Authentication optional — configure `authMode` to test your auth flow end-to-end
- WAF optional (`wafEnabled: true` to enable)
- Instance and container access via SSM Session Manager — no port 22 open
- ComplianceFactory runs (CloudTrail, AWS Config) when enabled

---

### `production` — Hardened by default

The most opinionated profile. Designed to be compliant out of the box with PCI-DSS, HIPAA, SOC 2, and GDPR when paired with the appropriate compliance framework.

- HTTP redirects to HTTPS — plain HTTP is never forwarded to the application
- WAF enabled automatically
- ALB access logs written to S3 (6-year retention, Glacier lifecycle)
- CloudTrail, AWS Config, and GuardDuty enabled
- EBS, EFS, and S3 encryption enforced
- Automated backups enabled
- Multi-AZ enforced
- Instance and container access via SSM Session Manager — no port 22 open
- If `authMode` is `none` and a compliance framework is active, the build fails with a remediation message

---

## Comparison

| | `dev` | `staging` | `production` |
|--|-------|-----------|--------------|
| HTTP access | Allowed | Allowed | Redirects to HTTPS |
| HTTPS access | Allowed | Allowed | Allowed |
| Authentication | Optional | Optional | Required by compliance frameworks |
| WAF | Off | Optional | On |
| ALB access logs | Off | Off | On (6-year S3 retention) |
| CloudTrail | Off | Optional | On |
| GuardDuty | Off | Optional | On |
| EC2 instance access | SSM Session Manager | SSM Session Manager | SSM Session Manager |
| Fargate shell access | ECS Exec (SSM) | ECS Exec (SSM) | ECS Exec (SSM) |
| Deletion protection | Off | Off | On |
| Estimated cost floor | ~$35/mo | ~$80/mo | ~$200/mo |

---

## Overriding individual settings

The profile sets the defaults. You can override individual fields in `deployment-context.json` without changing the profile. For example, enabling WAF in staging:

```json
{
  "securityProfile": "staging",
  "wafEnabled": true
}
```

Or disabling HTTP in dev to test an HTTPS flow:

```json
{
  "securityProfile": "dev",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true
}
```

The profile provides the floor; your config can raise it.

---

## Compliance frameworks

Compliance frameworks are independent of security profiles — they're a validation layer that runs after the profile and your overrides are applied. If the combined configuration doesn't satisfy the standard's requirements, the build fails with specific remediation steps.

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "PCI-DSS,SOC2"
}
```

Available frameworks: `PCI-DSS`, `HIPAA`, `SOC2`, `GDPR`.

Each framework checks things like authentication being enabled, encryption at rest, log retention periods, network isolation, and access control. See [compliance/](../compliance/) for per-framework details.

---

## Accessing instances and containers

No profile opens port 22. All shell access goes through AWS Systems Manager.

**EC2 instances** (e.g. Jenkins):
```bash
aws ssm start-session --target <instance-id>
```

**Fargate tasks** (ECS Exec):
```bash
aws ecs execute-command \
  --cluster <cluster-name> \
  --task <task-id> \
  --container <container-name> \
  --interactive \
  --command "/bin/sh"
```

Both require the caller's IAM identity to have `ssm:StartSession` or `ecs:ExecuteCommand` respectively. Sessions are CloudTrail-logged automatically.
