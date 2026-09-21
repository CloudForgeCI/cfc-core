# GitLab Application Guide

GitLab provides Git repository hosting, CI/CD pipelines, issue tracking, and a container registry in one application.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `gitlab` |
| **Category** | CI/CD |
| **Default Image** | `gitlab/gitlab-ce:latest` |
| **Application Port** | `80` |
| **SSH Port** | `22` (optional, see below) |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Default Instance** | t3.medium (EC2) |
| **Health Check Path** | `/users/sign_in` |
| **Health Check Grace** | 900 seconds (application default) |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `application-oidc` (default), `alb-oidc`, `none` |
| **Database Required** | Yes (PostgreSQL) |

---

## Upstream Features

- Git repository hosting and merge requests
- CI/CD pipelines (runners are deployed separately)
- Container and package registries
- Issues, wikis, and project management

CloudForge deploys GitLab Community Edition (`gitlab/gitlab-ce`). Some features require GitLab Premium or Ultimate.

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 22 | TCP | Inbound | `enableSsh` | Git SSH |
| 5050 | TCP | Inbound | `enableRegistry` | Container Registry |
| 9090 | TCP | Inbound | `enableMetrics` | Prometheus Metrics |

`enableRegistry` is not a recognized deployment-context key, so port 5050 cannot currently be opened through the deployment context.

**Example:**
```json
{
  "enableSsh": true,
  "enableMetrics": true
}
```

---

## Database Requirements

GitLab requires a PostgreSQL database. The interactive deployer and `CloudForgeDeployment` enable `provisionDatabase` automatically when it is not set. If a context is synthesized without it, GitLab falls back to the image's embedded PostgreSQL, which is suitable only for a single development instance.

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 16 or later |
| Instance Class | `db.t3.medium` (default) |
| Storage | 50 GB (default) |
| Database Name | `gitlabhq_production` |
| Backup Retention | 30 days |

**Database parameters** include `max_connections=300`, `shared_buffers={DBInstanceClassMemory/4096}`, `effective_cache_size={DBInstanceClassMemory*3/4096}`, `work_mem=16MB`, `maintenance_work_mem=256MB`, and `random_page_cost=1.1`.

---

## Authentication

### Supported Auth Modes

| Mode | Description |
|------|-------------|
| `application-oidc` | GitLab signs users in through OmniAuth OpenID Connect (default) |
| `alb-oidc` | The load balancer authenticates users before requests reach GitLab |
| `none` | GitLab local accounts only |

### OIDC Integration Details

With `application-oidc`, CloudForge adds an OmniAuth `openid_connect` provider to `GITLAB_OMNIBUS_CONFIG`:

- Users are created on first sign-in (`omniauth_block_auto_created_users = false`) and linked to existing accounts by email (`omniauth_auto_link_user`).
- PKCE is enabled according to the OIDC provider configuration.
- Group synchronization and admin-role assignment are not configured. Grant administrator rights with the GitLab Rails console or the admin UI.

**Callback Path:** `/users/auth/openid_connect/callback`

---

## Environment Variables

CloudForge configures GitLab through the `GITLAB_OMNIBUS_CONFIG` environment variable:

| Setting | Description |
|---------|-------------|
| `external_url` | External URL |
| `nginx['listen_port']` | `80` |
| `nginx['listen_https']` | `false` (TLS terminates at the load balancer) |
| `nginx['real_ip_*']`, `nginx['proxy_set_headers']` | Trust `X-Forwarded-*` headers from private address ranges |
| `gitlab_rails['monitoring_whitelist']` | `['0.0.0.0/0', '::/0']` |
| `postgresql['enable']` | `false` when an RDS database is provisioned, otherwise `true` |
| `gitlab_rails['db_*']` | RDS connection settings; the password is read from `GITLAB_DATABASE_PASSWORD`, injected from Secrets Manager |
| `redis['enable']` | `true` (embedded Redis) |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/var/opt/gitlab` |
| EFS Path | `/gitlab` |
| Volume Name | `gitlabData` |
| Container User | Not set (the image runs as root) |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/opt/gitlab` |
| Log Paths | `/var/log/gitlab/gitlab-rails/production.log`, `/var/log/gitlab/gitlab-rails/api_json.log`, `/var/log/gitlab/puma/puma_stderr.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "GitLab-Dev",
  "applicationId": "gitlab",
  "applicationName": "GitLab Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "enableMonitoring": true,
  "logRetentionDays": "7",
  "healthCheckGracePeriod": 900
}
```

### Development with Database, SSH, and OIDC

```json
{
  "stackName": "GitLab-Dev-Full",
  "applicationId": "gitlab",
  "applicationName": "GitLab Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "domain": "dev.example.com",
  "subdomain": "gitlab",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "gitlab-dev-yourcompany",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseName": "gitlabhq_production",

  "enableSsh": true,

  "enableMonitoring": true,
  "logRetentionDays": "30",
  "healthCheckGracePeriod": 900
}
```

### Production

Repository data and the embedded Redis are local to each instance, so run a single instance.

```json
{
  "stackName": "GitLab-Production",
  "applicationId": "gitlab",
  "applicationName": "GitLab",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "gitlab",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "gitlab-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.large",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.t3.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "gitlabhq_production",
  "databaseBackupRetentionDays": 30,

  "enableSsh": true,
  "enableMetrics": true,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true,
  "healthCheckGracePeriod": 900
}
```

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/users/sign_in` | Health check endpoint |
| Grace Period | 900 seconds | GitLab's application default, to allow for database migrations |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |

GitLab's first start runs database migrations and service initialization, so its default grace period is 900 seconds rather than the usual 300. Setting `healthCheckGracePeriod` overrides it.

---

## Compliance Considerations

Setting `complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for those frameworks. It does not certify the deployment.

### SOC 2

Controls you configure in GitLab:
- Audit events
- Secret scanning
- Protected branches and required approvals
- Signed commits
- Session timeouts
- Two-factor authentication

### GDPR

Controls you configure in GitLab and your processes:
- Consent for profile data
- Data export
- Erasure procedures

---

## Post-Deployment Tasks

### 1. Initial Login

1. Open `https://gitlab.example.com` (your configured FQDN).
2. Sign in as `root`. Recent GitLab images generate the initial password in `/etc/gitlab/initial_root_password` inside the container.
3. With `application-oidc`, sign in through OIDC and grant administrator rights to the appropriate users.

### 2. Configure Container Registry

CloudForge does not configure GitLab's container registry. To use it, set `registry_external_url` and a storage backend (for example S3) in the Omnibus configuration.

### 3. Configure CI/CD Runners

1. Go to **Admin** > **CI/CD** > **Runners**.
2. Register a GitLab Runner.
3. Configure its executor (for example Docker or Kubernetes).

---

## Troubleshooting

### GitLab takes too long to start

The first start can take 10 to 15 minutes for database migrations and service initialization.

Monitor `/var/log/gitlab/gitlab-rails/production.log`.

### Container Registry not accessible

1. Confirm the registry is configured in the Omnibus configuration.
2. Check that the security group allows port 5050 (see Optional Ports).
3. Verify DNS resolution.

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [GitLab Documentation](https://docs.gitlab.com/)
