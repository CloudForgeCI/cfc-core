# Grafana Application Guide

Grafana is an open-source platform for querying, visualizing, and alerting on metrics, logs, and traces.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `grafana` |
| **Category** | Monitoring |
| **Default Image** | `grafana/grafana:latest` |
| **Application Port** | `3000` |
| **Default CPU** | 512 (Fargate) |
| **Default Memory** | 1024 MB (Fargate) |
| **Default Instance** | t3.micro (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `application-oidc` (default), `alb-oidc`, `none` |
| **Database Required** | No (SQLite by default; PostgreSQL optional) |

---

## Upstream Features

- Dashboards with templating and annotations
- Alerting and notification contact points
- Explore mode for ad hoc queries
- Data sources including Prometheus, CloudWatch, and InfluxDB
- Panel and data-source plugins

---

## Optional Ports

Grafana does not have optional ports. All traffic flows through port 3000.

---

## Database Configuration

### Without a Database

Without `provisionDatabase`, Grafana uses SQLite at `/var/lib/grafana/grafana.db` (`GF_DATABASE_TYPE=sqlite3`). SQLite supports a single instance only.

### With PostgreSQL

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 14 or later |
| Instance Class | `db.t3.micro` (default) |
| Storage | 20 GB (default) |
| Database Name | `grafana` |

When an RDS database is provisioned, CloudForge sets:
- `GF_DATABASE_TYPE`: `postgres`
- `GF_DATABASE_HOST`: `<endpoint>:<port>`
- `GF_DATABASE_NAME` and `GF_DATABASE_USER`: from the database connection
- `GF_DATABASE_SSL_MODE`: `require`
- `GF_DATABASE_PASSWORD`: injected from Secrets Manager

---

## Authentication

### Supported Auth Modes

| Mode | Description |
|------|-------------|
| `application-oidc` | Grafana signs users in through its `generic_oauth` provider (default) |
| `alb-oidc` | The load balancer authenticates users before requests reach Grafana |
| `none` | Grafana local accounts only |

### OIDC Integration Details

With `application-oidc`, CloudForge configures Grafana's `generic_oauth` provider through `GF_AUTH_GENERIC_OAUTH_*` environment variables:

- Users are created on first sign-in when automatic user creation is enabled (`GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP`).
- The groups claim is passed through `GF_AUTH_GENERIC_OAUTH_GROUPS_ATTRIBUTE_PATH`.
- PKCE is enabled according to the OIDC provider configuration.
- Role mapping is not configured: `GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH` is empty, so new users receive Grafana's default organization role. Set a role attribute path yourself to map groups to Grafana roles.

**Callback Path:** `/login/generic_oauth`

---

## Environment Variables

CloudForge sets:

| Variable | Description | Example |
|----------|-------------|---------|
| `GF_SERVER_ROOT_URL` | External URL, required for OAuth redirects (set when an FQDN is configured) | `https://grafana.example.com` |
| `GF_SERVER_DOMAIN` | Domain name (set when an FQDN is configured) | `grafana.example.com` |
| `GF_SERVER_ENFORCE_DOMAIN` | Disabled so load balancer health checks succeed | `false` |
| `GF_SERVER_PROTOCOL` | TLS terminates at the load balancer | `http` |
| `GF_DATABASE_TYPE` | Database type | `postgres` or `sqlite3` |

**OIDC Variables (when enabled):**
| Variable | Description |
|----------|-------------|
| `GF_AUTH_GENERIC_OAUTH_ENABLED` | Enable OAuth |
| `GF_AUTH_GENERIC_OAUTH_CLIENT_ID` | OAuth client ID |
| `GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET` | Set to the literal `${GRAFANA_OAUTH_CLIENT_SECRET}` on Fargate (see note below) |
| `GF_AUTH_GENERIC_OAUTH_AUTH_URL` | Authorization endpoint |
| `GF_AUTH_GENERIC_OAUTH_TOKEN_URL` | Token endpoint |
| `GF_AUTH_GENERIC_OAUTH_API_URL` | UserInfo endpoint |

On Fargate, the client secret is injected from Secrets Manager as `GRAFANA_OIDC_CLIENT_SECRET`, while `GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET` references `${GRAFANA_OAUTH_CLIENT_SECRET}`. ECS does not expand such references, so verify the client secret Grafana receives before relying on `application-oidc` on Fargate.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/var/lib/grafana` |
| EFS Path | `/grafana` |
| Volume Name | `grafanaData` |
| Container User | `472:472` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/lib/grafana` |
| Log Paths | `/var/log/grafana/grafana.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Grafana-Dev",
  "applicationId": "grafana",
  "applicationName": "Grafana Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 512,
  "memory": 1024,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Development with OIDC

```json
{
  "stackName": "Grafana-Dev-Auth",
  "applicationId": "grafana",
  "applicationName": "Grafana Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "domain": "dev.example.com",
  "subdomain": "grafana",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-dev-yourcompany",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "GrafanaAdmins",
  "cognitoUserGroupName": "GrafanaViewers",

  "cpu": 512,
  "memory": 1024,

  "enableMonitoring": true,
  "logRetentionDays": "30"
}
```

### Production with PostgreSQL

```json
{
  "stackName": "Grafana-Production",
  "applicationId": "grafana",
  "applicationName": "Grafana",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "grafana",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "GrafanaAdmins",
  "cognitoUserGroupName": "GrafanaViewers",

  "instanceType": "t3.small",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseMultiAz": true,
  "databaseName": "grafana",
  "databaseBackupRetentionDays": 30,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

### Fargate with ALB Authentication

Grafana on Fargate behind load balancer authentication, for example as the front end for a separately deployed Prometheus stack.

```json
{
  "stackName": "Grafana-Observability",
  "applicationId": "grafana",
  "applicationName": "Grafana Observability",
  "environment": "prod",

  "runtime": "fargate",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "metrics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-obs-yourcompany",
  "cognitoMfaEnabled": true,

  "cpu": 1024,
  "memory": 2048,
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/` | Health check endpoint |
| Grace Period | 300 seconds | Time before health checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |

---

## Compliance Considerations

Setting `complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for those frameworks. It does not certify the deployment.

### SOC 2

**Infrastructure controls CloudForge can configure:**
- Encryption at rest and in transit (TLS)
- Network isolation
- CloudWatch logging

**Controls you configure in Grafana:**
- Session timeouts
- Anonymous access disabled
- Dashboard and data-source permissions
- Audit logging (a Grafana Enterprise feature)

---

## Post-Deployment Tasks

### 1. Initial Login

1. Open `https://grafana.example.com` (your configured FQDN).
2. With `application-oidc`, choose the OAuth sign-in button.
3. Otherwise, sign in with Grafana's default credentials, `admin` / `admin`, and change the password.

### 2. Add Data Sources

1. Go to **Connections** > **Data sources**.
2. Choose **Add data source**.
3. Select a type (for example Prometheus or CloudWatch).
4. Configure the connection.

**Example Prometheus data source:**
```
URL: https://prometheus.example.com
Access: Server (default)
```

**Example CloudWatch data source:**
```
Authentication Provider: AWS SDK Default
Default Region: us-east-1
```

### 3. Import Dashboards

1. Go to **Dashboards** > **Import**.
2. Enter a Grafana.com dashboard ID or upload JSON.
3. Select the data source.

For example, dashboard 1860 visualizes Node Exporter metrics.

### 4. Configure Alerting

1. Go to **Alerting** > **Contact points**.
2. Add contact points (for example email, Slack, or PagerDuty).
3. Create alert rules.

---

## Troubleshooting

### Grafana won't start

**Check logs:**
```bash
# Fargate (log group name when storage is not retained; otherwise find the
# stack's log group in the CloudWatch console)
aws logs tail /aws/ecs/<stack-name>/fargate/<security-profile> --follow

# EC2 (via SSM Session Manager)
aws ssm start-session --target <instance-id>
# then: tail -f /var/log/grafana/grafana.log
```

### OIDC login fails

1. Verify `GF_SERVER_ROOT_URL` matches the URL users open.
2. Check the Cognito callback URLs (`/login/generic_oauth`).
3. Verify the OAuth client configuration and client secret.

### Dashboards not loading

1. Check data source connectivity.
2. Verify IAM permissions for CloudWatch.
3. Check security group rules.

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Grafana Documentation](https://grafana.com/docs/)
