# Moodle Application Guide

Moodle is the world's most widely deployed Learning Management System (LMS). It is used by universities, schools, and corporate training teams to deliver courses, assessments, and certifications.

**Status**: Available

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `moodle` |
| **Category** | LMS |
| **Default Image** | `moodlehq/moodle-php-apache:8.2` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Health Check Path** | `/login/index.php` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication** | ALB-OIDC (Cognito) |
| **Database Required** | Yes (PostgreSQL 16 or MySQL 8.0) |

---

## Capabilities

- Course creation with activities (quizzes, assignments, forums, SCORM)
- Grading and outcomes tracking
- Student progress reporting and competency frameworks
- Video content with H5P
- Badges and certificates
- Multilingual interface
- Mobile app support (Moodle Mobile)
- Bulk enrollment via CSV or LDAP
- Native OpenID Connect authentication plugin
- REST API for integrations

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Course files, submissions, video content |
| ElastiCache Redis | Yes | MUC (Moodle Universal Cache) — sessions, application cache |
| CloudFront CDN | Yes | Course asset delivery |
| EFS | Yes | `/var/www/html/moodledata` (user files, temp data) |
| Route53 records | When domain configured | A + AAAA records to ALB |

Moodle's `moodledata` directory must be **outside** the webroot and persistent across deploys. EFS is mounted at the configured `moodledata` path automatically.

---

## Authentication

Moodle is typically deployed for a known user population (students, employees). The entire site can be gated at the ALB with Cognito, or Moodle's native OIDC plugin can be used for SSO while leaving the login page accessible.

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | **Recommended** | Cognito at ALB — all traffic authenticated |
| `none` | Dev only | Moodle local accounts only |

For universities using existing identity providers (Active Directory, Google Workspace), configure Cognito as a federation layer in front of the IdP, then use `alb-oidc`.

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `MOODLE_OIDC_CLIENT_ID` | Cognito client ID |
| `MOODLE_OIDC_AUTHORIZATION_ENDPOINT` | Cognito authorization endpoint |
| `MOODLE_OIDC_TOKEN_ENDPOINT` | Cognito token endpoint |
| `MOODLE_OIDC_USERINFO_ENDPOINT` | Cognito userinfo endpoint |
| `MOODLE_DB_HOST` | RDS endpoint |
| `MOODLE_DB_NAME` | Database name (default: `moodle`) |
| `MOODLE_DB_USER` | Database user |
| `MOODLE_DB_PASSWORD` | From Secrets Manager |
| `REDIS_HOST` | ElastiCache endpoint |
| `REDIS_PORT` | `6379` |

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path (webroot) | `/var/www/html` |
| Moodledata Path | `/var/moodledata` |
| EFS Path | `/moodle` |
| Volume Name | `moodleData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

**Important:** `moodledata` must not be web-accessible. It is mounted on EFS outside the NGINX document root.

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/apache2/error.log`, `/var/log/php-fpm/error.log`, `/var/moodledata/moodle.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal

```json
{
  "stackName": "Moodle-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "application": "moodle",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 30,
  "databaseName": "moodle",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$90/month

### Production - University / Corporate Training

```json
{
  "stackName": "Moodle-Production",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "application": "moodle",

  "domain": "example.edu",
  "subdomain": "learn",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "moodle-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.r6g.large",
  "databaseAllocatedStorageGB": 200,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 30,
  "databaseName": "moodle",

  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365",
  "retainStorage": true
}
```

**Cost estimate:** ~$450-650/month

### Production - HIPAA (Healthcare Training)

Moodle is frequently used for HIPAA compliance training and healthcare employee onboarding:

```json
{
  "stackName": "Moodle-HIPAA",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "application": "moodle",

  "domain": "example.com",
  "subdomain": "training",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "moodle-hipaa-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.r6g.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 90,
  "databaseName": "moodle",

  "complianceFrameworks": "HIPAA,SOC2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "2555",
  "retainStorage": true
}
```

**Cost estimate:** ~$550-750/month

---

## Post-Deployment Tasks

### 1. Complete Moodle Installation

1. Navigate to `https://your-domain.com`
2. Accept the license and follow the installer
3. Configure the database (pre-populated from env vars)
4. Set `moodledata` directory to `/var/moodledata`
5. Create the admin account

### 2. Configure Cron

Moodle depends heavily on cron for grade calculations, notifications, and scheduled tasks:

```bash
aws ssm start-session --target <instance-id>
crontab -e
# Add:
* * * * * www-data /usr/bin/php /var/www/html/admin/cli/cron.php > /dev/null 2>&1
```

Or use Moodle's **Task Scheduler** to run cron via the web:
**Site Administration** > **Server** > **Scheduled Tasks**

### 3. Configure Redis Cache (MUC)

1. **Site Administration** > **Plugins** > **Caching** > **Configuration**
2. Add a Redis store pointing to `REDIS_HOST:REDIS_PORT`
3. Map **Application**, **Session**, and **Request** caches to the Redis store

### 4. Configure File Storage

For large course files (video, SCORM packages), configure S3 as the file system backend:

1. Install the **Object File System** plugin for Moodle
2. Point it to the CloudForge-provisioned S3 bucket
3. Enable S3 as the default file system for new uploads

---

## Compliance Considerations

### FERPA (US Education)

Student records in Moodle are protected under FERPA:

- [ ] Restrict grade export to authorized staff only
- [ ] Enable logging of grade access events
- [ ] Configure data retention policies for student submissions
- [ ] Require staff authentication before accessing reports

### HIPAA (Healthcare Training)

If training content references PHI:

- [ ] Encrypt `moodledata` directory (EFS encryption is automatic with CloudForge)
- [ ] Enable detailed audit logging: **Administration** > **Site Administration** > **Reports** > **Logs**
- [ ] Restrict course enrollment access
- [ ] Set session timeout ≤ 30 minutes
- [ ] Enable automatic logout on inactivity

---

## Troubleshooting

### Cron not running / stale content

Check that cron is scheduled and running:

```bash
# Fargate — run cron manually via ECS Exec
aws ecs execute-command --cluster <cluster> --task <task-id> \
  --container moodle --interactive \
  --command "/usr/bin/php /var/www/html/admin/cli/cron.php"
```

### `moodledata` not found

Moodle will fail startup if `moodledata` is not writable by `www-data`. Verify the EFS mount in the task definition and check permissions on the mounted directory.

### Slow page loads under load

Enable Moodle's caching stores (Redis MUC). Without Redis configured, Moodle falls back to file-based caching which is slow on EFS under concurrent access.

---

## Related Documentation

- [CMS Guides Index](README.md)
- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)
