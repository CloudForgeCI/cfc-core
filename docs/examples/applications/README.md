# Application Deployment Context Examples

Per-application `deployment-context.json` examples. See the [parent README](../README.md) for how to load a file
with `cfc-testing` or as CDK context, and for the Private CA option when no domain is configured.

## Before deploying

| Key | Notes |
|-----|-------|
| `stackName` | Unique CloudFormation stack name in the target account and region. |
| `domain`, `subdomain` | Your DNS zone and host name, or omit both for a Private CA certificate on the load balancer DNS name. |
| `cognitoDomainPrefix` | Replace the `-changeme` suffix. Lowercase letters, digits, and hyphens; CloudForge appends the stack name. |
| `region` | Target AWS region. |
| `applicationId` | The `compliance-*` files use the placeholder `REPLACE_WITH_APP_ID`; set it to an ID from the [catalog](../../applications/README.md). |

## By application

| File | Application | Profile | Notes |
|------|-------------|---------|-------|
| [jenkins-dev.json](jenkins-dev.json) | `jenkins` | `dev` | Fargate, public network, no auth |
| [jenkins-dev-auth.json](jenkins-dev-auth.json) | `jenkins` | `dev` | `application-oidc` with Cognito |
| [jenkins-dev-quick.json](jenkins-dev-quick.json) | `jenkins` | `dev` | `alb-oidc` with Cognito, no domain (Private CA) |
| [jenkins-production.json](jenkins-production.json) | `jenkins` | `production` | EC2, `application-oidc`, SOC 2, build agent port |
| [mattermost-dev.json](mattermost-dev.json) | `mattermost-team` | `dev` | Fargate, public network, no auth |
| [mattermost-production.json](mattermost-production.json) | `mattermost-team` | `production` | EC2, RDS PostgreSQL, `application-oidc`, SOC 2 |
| [metabase-dev.json](metabase-dev.json) | `metabase` | `dev` | No RDS database (Metabase uses its embedded H2 store) |
| [metabase-production.json](metabase-production.json) | `metabase` | `production` | EC2, RDS PostgreSQL, `alb-oidc`, SOC 2 |
| [gitlab-production.json](gitlab-production.json) | `gitlab` | `production` | EC2, RDS PostgreSQL 16, `application-oidc`, SSH and metrics ports |
| [grafana-production.json](grafana-production.json) | `grafana` | `production` | EC2, RDS PostgreSQL, `application-oidc` |
| [harbor-production.json](harbor-production.json) | `harbor` | `production` | EC2, RDS PostgreSQL, Notary and Trivy ports; no OIDC integration |
| [sonarqube-production.json](sonarqube-production.json) | `sonarqube` | `production` | EC2, SOC 2; no OIDC integration |
| [cloudforge-manager-dev.json](cloudforge-manager-dev.json) | `cloudforge-manager` | `dev` | Fargate, no database, no auth |
| [cloudforge-manager-dev-auth.json](cloudforge-manager-dev-auth.json) | `cloudforge-manager` | `dev` | `application-oidc` with Cognito |
| [cloudforge-manager-production.json](cloudforge-manager-production.json) | `cloudforge-manager` | `production` | RDS PostgreSQL (Multi-AZ), `alb-oidc` |

`cloudforge-manager` is provided by the separate `com.cloudforgeci:cloudforge-manager-deployment` artifact (version
managed by the `cfc-core` BOM), not by `cloudforge-api`; it must be on the classpath for its `applicationId` to
resolve. Its database is optional and defaults to MySQL 8.0 when
`provisionDatabase` is `true` and no engine is given.

Optional ports (`enableAgents`, `enableSsh`, `enableMetrics`, `enableNotary`, `enableTrivy`, `enableSmtp`) are only
opened for applications that declare the matching port; see the [catalog](../../applications/README.md).

If `authMode` names a mode the application does not support, CloudForge replaces it with the application's
recommended mode and prints a warning.

## By compliance framework

| File | Frameworks | Mode | Notes |
|------|-----------|------|-------|
| [compliance-soc2-quick.json](compliance-soc2-quick.json) | SOC 2 | `advisory` | `staging`, no domain (Private CA) |
| [compliance-hipaa-quick.json](compliance-hipaa-quick.json) | SOC 2 + HIPAA | `advisory` | `staging`, no domain (Private CA), 2190-day log retention |
| [compliance-soc2-staging.json](compliance-soc2-staging.json) | SOC 2 | `advisory` | `staging` |
| [compliance-soc2-production.json](compliance-soc2-production.json) | SOC 2 | `enforce` | `production` |
| [compliance-hipaa-production.json](compliance-hipaa-production.json) | HIPAA + SOC 2 | `enforce` | `production`, RDS PostgreSQL, Config remediations |
| [compliance-pci-dss-production.json](compliance-pci-dss-production.json) | PCI-DSS + SOC 2 | `enforce` | `production`, Aurora PostgreSQL |

## File naming

```
{applicationId}-{environment}[-variant].json
compliance-{framework}-{environment}.json
```

## Related documentation

- [Deployment context examples](../README.md)
- [Application catalog](../../applications/README.md)
- [Per-application guides](../../guides/applications/README.md)
- [Compliance documentation](../../compliance/README.md)
