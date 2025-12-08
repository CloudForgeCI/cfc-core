# GitLab Application Guide

GitLab is a complete DevOps platform that provides source code management, CI/CD pipelines, container registry, and security scanning in a single application.

**Status**: Available (Not Yet Tested)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `gitlab` |
| **Category** | CI/CD |
| **Default Image** | `gitlab/gitlab-ce:latest` |
| **Application Port** | `80` |
| **SSH Port** | `22` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Default Instance** | t3.medium (EC2) |
| **Health Check Path** | `/users/sign_in` |
| **Health Check Grace** | 900 seconds (15 min) |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **OIDC Support** | Yes (via OmniAuth) |
| **Database Required** | Yes (PostgreSQL) |

---

## Capabilities

- Git repository hosting
- Built-in CI/CD pipelines
- Container registry
- Issue tracking and project management
- Code review with merge requests
- Security scanning (SAST, DAST, dependency scanning)
- Wiki and documentation
- Package registry (npm, Maven, NuGet, PyPI)
- Kubernetes integration
- Auto DevOps

**Note:** GitLab CE (Community Edition) is deployed. Some features require GitLab Premium/Ultimate.

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 22 | TCP | Inbound | `enableSsh` | Git SSH |
| 5050 | TCP | Inbound | `enableDockerRegistry` | Container Registry |
| 9090 | TCP | Inbound | `enableMetrics` | Prometheus Metrics |

**Example enabling all optional ports:**
```json
{
  "enableSsh": true,
  "enableDockerRegistry": true,
  "enableMetrics": true
}
```

---

## Database Requirements

GitLab **requires** a PostgreSQL database.

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 16+ |
| Instance Class | db.t3.medium (default) |
| Storage | 50 GB (default) |
| Database Name | `gitlabhq_production` |
| Backup Retention | 30 days |

**Database Parameters:**
- `max_connections`: 300
- `shared_buffers`: Optimized for instance class
- `work_mem`: 16MB

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `application-oidc` | Available | Native OIDC via OmniAuth OpenID Connect |
| `alb-oidc` | Available | ALB-level authentication |
| `none` | Available | Local accounts only |

### OIDC Integration Details

GitLab uses **OmniAuth OpenID Connect** configured via `gitlab.rb`.

**Features:**
- Auto-create users on first login
- Group synchronization (GitLab Premium/Ultimate)
- Admin role assignment
- PKCE support
- Block external OAuth sign-ins option

**Callback Path:** `/users/auth/openid_connect/callback`

---

## Environment Variables

CloudForge configures GitLab via `GITLAB_OMNIBUS_CONFIG` environment variable:

| Setting | Description |
|---------|-------------|
| `external_url` | Full external URL |
| `nginx['listen_port']` | Internal port (80) |
| `nginx['listen_https']` | Disabled (ALB terminates TLS) |
| `nginx['proxy_set_headers']` | X-Forwarded headers |
| `postgresql['enable']` | Embedded PostgreSQL (false when using RDS) |
| `gitlab_rails['db_*']` | Database connection settings |
| `redis['enable']` | Embedded Redis for caching |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/var/opt/gitlab` |
| EFS Path | `/gitlab` |
| Volume Name | `gitlabData` |
| Container User | `null` (runs as root) |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/opt/gitlab` |
| Log Paths | `/var/log/gitlab/gitlab-rails/production.log`, `/var/log/gitlab/gitlab-rails/api_json.log`, `/var/log/gitlab/unicorn/unicorn_stderr.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal Setup

```json
{
  "stackName": "GitLab-Dev",
  "applicationId": "gitlab",
  "applicationName": "GitLab Dev",
  "description": "GitLab development environment",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "enableMonitoring": true,
  "logRetentionDays": "7",
  "healthCheckGracePeriod": 900
}
```

**Cost estimate:** ~$80/month

### Development - With Database and SSH

```json
{
  "stackName": "GitLab-Dev-Full",
  "applicationId": "gitlab",
  "applicationName": "GitLab Dev",
  "description": "GitLab with PostgreSQL and SSH",
  "environment": "development",

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

**Cost estimate:** ~$180/month

### Production - Full DevOps Platform

```json
{
  "stackName": "GitLab-Production",
  "applicationId": "gitlab",
  "applicationName": "GitLab",
  "description": "Production GitLab with all features",
  "environment": "production",

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
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.t3.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "gitlabhq_production",
  "databaseBackupRetentionDays": 30,

  "enableSsh": true,
  "enableDockerRegistry": true,
  "enableMetrics": true,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": false,
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

**Cost estimate:** ~$600/month

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/users/sign_in` | Health check endpoint |
| Grace Period | **900 seconds** | Extended for database migrations |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |

**Important:** GitLab requires a longer health check grace period (15 minutes) due to database migrations and initial setup.

---

## Compliance Considerations

### SOC2

**User Responsibilities:**
- [ ] Enable audit logging (`gitlab_rails['audit_events_enabled'] = true`)
- [ ] Configure secret scanning
- [ ] Enable branch protection on production branches
- [ ] Require code reviews (minimum 1 approver)
- [ ] Enable signed commits
- [ ] Configure session timeouts
- [ ] Enable 2FA for all users

### GDPR

**User Responsibilities:**
- [ ] User consent for profile data
- [ ] Data export capability
- [ ] Right to erasure procedures

---

## Post-Deployment Tasks

### 1. Initial Login

1. Navigate to `https://gitlab.your-domain.com`
2. Set root password (first access) or use OIDC
3. Create initial admin account

### 2. Configure Container Registry

If `enableDockerRegistry: true`:

1. **Admin** > **Settings** > **Container Registry**
2. Enable registry
3. Configure storage backend (S3 recommended)

### 3. Configure CI/CD Runners

1. **Admin** > **Runners**
2. Register GitLab Runner
3. Configure executor (Docker, Kubernetes, etc.)

---

## Troubleshooting

### GitLab takes too long to start

GitLab requires significant startup time (10-15 minutes) for:
- Database migrations
- Asset compilation
- Service initialization

Monitor logs: `/var/log/gitlab/gitlab-rails/production.log`

### Container Registry not accessible

1. Verify `enableDockerRegistry: true`
2. Check security group allows port 5050
3. Verify DNS resolution

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [GitLab Documentation](https://docs.gitlab.com/)
