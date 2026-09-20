# Moodle Application Guide

Moodle is an open-source learning management system (LMS) for courses, assessments, and training programs.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `moodle` |
| **Category** | LMS |
| **Default Image** | `moodlehq/moodle-php-apache:8.2` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Recommended CPU / Memory (Fargate)** | 2048 / 4096 MB |
| **Recommended Instance Type (EC2)** | `t3.medium` |
| **Health Check Path** | `/login/index.php` |
| **Supports Fargate** | Yes (see the note below) |
| **Supports EC2** | Yes |
| **Authentication Modes** | `alb-oidc`, `application-oidc` (OpenID Connect auth plugin), `none` |
| **Database** | Required (MySQL 8.0 default; MariaDB and PostgreSQL supported by the plugin metadata) |

**Runtime notes:**

- **Fargate**: the default image provides PHP and Apache for Moodle but does not include the Moodle code base, and EFS is mounted over `/var/www/html`. Place Moodle on the EFS volume, or build your own image, before the site can serve pages.
- **EC2**: UserData installs Apache and PHP, and downloads the Moodle 4.4 release into `/var/www/html` when `config.php` is absent.

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | File storage (for use with an object file system plugin) |
| ElastiCache Redis | Yes | Moodle Universal Cache (MUC) and session store |
| CloudFront CDN | Yes | Theme and library assets (`/theme/*`, `/lib/*`, `/pluginfile.php/*`) |
| EFS | Yes | Mounted at `/var/www/html` (access point path `/moodle`) |
| Route53 records | When a hosted zone and domain are configured | A + AAAA alias records |

The `moodledata` directory is `/var/moodledata` (`MOODLE_DATA`). It is outside the web root and is **not** on EFS: on Fargate it is task-local storage, and on EC2 it is on each instance's root volume. For multi-task or multi-instance deployments, or to keep data across task replacement, move `moodledata` to shared storage.

---

## Authentication

| Mode | Description |
|------|-------------|
| `alb-oidc` | Cognito at the ALB. Protects `/admin/*` and `/login/*`; course pages are not gated at the ALB and rely on Moodle's own access control. |
| `application-oidc` | Moodle handles OIDC itself (`MOODLE_OIDC_*` environment variables). |
| `none` | No authentication in front of Moodle; Moodle local accounts only. |

To use an existing identity provider (for example, Microsoft Entra ID or Google Workspace), federate it into the Cognito user pool and keep `alb-oidc`.

---

## Environment Variables

CloudForge sets the following on the Fargate container:

| Variable | Value |
|----------|-------|
| `MOODLE_URL` | `https://<fqdn>` (when a domain is set) |
| `MOODLE_DATA` | `/var/moodledata` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | RDS connection settings (when `provisionDatabase` is `true`) |
| `DATABASE_PASSWORD` | From the RDS Secrets Manager secret |

With `application-oidc`, CloudForge also sets `MOODLE_OIDC_CLIENT_ID`, `MOODLE_OIDC_AUTH_ENDPOINT`, `MOODLE_OIDC_TOKEN_ENDPOINT`, `MOODLE_OIDC_USERINFO_ENDPOINT`, `MOODLE_OIDC_LOGOUT_ENDPOINT`, `MOODLE_OIDC_SCOPE`, and `MOODLE_OIDC_IDP_TYPE`.

The Redis endpoint and S3 bucket name are not injected.

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path (web root) | `/var/www/html` |
| Moodledata Path | `/var/moodledata` (not on EFS) |
| EFS Path | `/moodle` |
| Volume Name | `moodleData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` (used when EFS is not available) |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/httpd/access_log`, `/var/log/httpd/error_log`, `/var/log/php-fpm/error.log`, `/var/log/userdata.log` |
| CloudWatch Log Group | `/cloudforge/<stackName>/moodle` |

---

## Deployment Context Examples

Set `cpu` and `memory` (Fargate) or `instanceType` (EC2) explicitly; otherwise the framework defaults (`1024`, `2048`, `t3.micro`) apply.

### Development - Minimal

```json
{
  "stackName": "Moodle-Dev",
  "runtime": "ec2",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "moodle",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.medium",

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

### Production

```json
{
  "stackName": "Moodle-Production",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "moodle",

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

Before scaling beyond one instance, move `moodledata` to shared storage (see [Auto-Provisioned Infrastructure](#auto-provisioned-infrastructure)).

### Production - With HIPAA and SOC 2 Controls

`complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for the listed frameworks. It does not certify the deployment.

```json
{
  "stackName": "Moodle-HIPAA",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "moodle",

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

  "complianceFrameworks": "hipaa,soc2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "1827",
  "retainStorage": true
}
```

---

## Post-Deployment Tasks

### 1. Complete the Moodle Installation

1. Open `https://<your-domain>` and follow the installer.
2. Enter the database settings: the RDS endpoint and the credentials from the RDS Secrets Manager secret.
3. Set the data directory to `/var/moodledata`.
4. Create the admin account.

### 2. Configure Cron

Moodle uses cron for notifications, grade calculations, and scheduled tasks. On EC2:

```bash
aws ssm start-session --target <instance-id>
sudo crontab -u apache -e
# Add:
* * * * * /usr/bin/php /var/www/html/admin/cli/cron.php > /dev/null 2>&1
```

Review scheduled tasks under **Site administration** > **Server** > **Scheduled tasks**.

### 3. Configure the Redis Cache (MUC)

1. Find the ElastiCache cluster (named `moodle-<env>-cache`) and note its endpoint.
2. **Site administration** > **Plugins** > **Caching** > **Configuration**: add a Redis store pointing to `<endpoint>:6379`.
3. Map the **Application** and **Session** caches to the Redis store.

### 4. Configure File Storage

For large course files (video, SCORM packages), install an object file system plugin, grant the instance or task role access to the media bucket, and point the plugin at it.

---

## Compliance Considerations

Application-level controls to review alongside CloudForge's infrastructure controls:

**FERPA (US education)**

- Restrict grade export to authorized staff
- Log grade access events
- Set data retention policies for student submissions

**HIPAA (healthcare training)**

- Store `moodledata` on encrypted storage (it is not on EFS by default)
- Review logs under **Site administration** > **Reports** > **Logs**
- Restrict course enrollment
- Set a session timeout of 30 minutes or less

---

## Troubleshooting

### Cron not running

On Fargate, run cron manually with ECS Exec (ECS Exec must be enabled on the service):

```bash
aws ecs execute-command --cluster <cluster> --task <task-id> \
  --container <container-name> --interactive \
  --command "/usr/local/bin/php /var/www/html/admin/cli/cron.php"
```

### `moodledata` not writable

Moodle fails to start if `/var/moodledata` is not writable by the web server user (`www-data` in the container, `apache` on EC2). Check ownership and permissions of the directory.

### Slow page loads

Configure the Redis MUC store. Without it, Moodle uses file-based caching.

---

## Related Documentation

- [CMS Guides Index](README.md)
- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)
