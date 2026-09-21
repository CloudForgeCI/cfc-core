# Drupal Application Guide

Drupal is an open-source CMS for structured content models, multilingual sites, and configurable access controls.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `drupal` |
| **Category** | CMS |
| **Default Image** | `drupal:10-php8.2-apache` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Recommended CPU / Memory (Fargate)** | 1024 / 2048 MB |
| **Recommended Instance Type (EC2)** | `t3.small` |
| **Health Check Path** | `/user/login` |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication Modes** | `alb-oidc`, `application-oidc` (OpenID Connect module), `none` |
| **Database** | Required (MySQL 8.0 default; MariaDB and PostgreSQL supported by the plugin metadata) |

The EC2 runtime installs nginx and PHP-FPM on the instance instead of using the container image.

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Media files (for use with the S3 File System module) |
| ElastiCache Redis | Yes | Cache backend (for use with the Redis module) |
| CloudFront CDN | Yes | Asset and media delivery |
| EFS | Yes | Mounted at `/var/www/html` (access point path `/drupal`) |
| Route53 records | When a hosted zone and domain are configured | A + AAAA alias records |

---

## Authentication

| Mode | Description |
|------|-------------|
| `alb-oidc` | Cognito at the ALB. Protects `/admin/*`, `/user/*`, and `/update.php`; other pages stay public. |
| `application-oidc` | Drupal handles OIDC itself (`DRUPAL_OIDC_*` environment variables). |
| `none` | No authentication in front of Drupal. |

---

## First-Run Install and Admin Password

On Fargate, the container installs Drupal automatically on first start:

1. CloudForge generates a random admin password in Secrets Manager (stack output `CloudForgeAutoAdminPasswordSecretArn`); it is also printed once to the container log.
2. The container installs Drush with Composer, waits for the database port, and runs `drush site:install standard` with user `admin`.
3. Restarts skip the install when `web/sites/default/settings.php` already exists.

The install uses a `mysql://` database URL, so the automatic install requires `databaseEngine` `mysql` (or MariaDB). For PostgreSQL, complete the installation manually.

---

## Environment Variables

CloudForge sets the following on the Fargate container:

| Variable | Value |
|----------|-------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | RDS connection settings (when `provisionDatabase` is `true`) |
| `DRUPAL_DATABASE_HOST`, `DRUPAL_DATABASE_PORT`, `DRUPAL_DATABASE_NAME`, `DRUPAL_DATABASE_USER` | Same values under Drupal-specific names |
| `DRUPAL_DATABASE_DRIVER` | `mysql` |
| `DATABASE_PASSWORD` | From the RDS Secrets Manager secret |
| `DRUPAL_TRUSTED_HOST_PATTERNS` | `^<fqdn>$` |
| `DRUPAL_ADMIN_EMAIL` | `admin@<fqdn>` (or `cognitoInitialAdminEmail` with `alb-oidc`) |
| `DRUPAL_ADMIN_PASSWORD` | From the generated admin password secret |

With `application-oidc`, CloudForge also sets `DRUPAL_OIDC_CLIENT_ID`, `DRUPAL_OIDC_AUTHORIZATION_ENDPOINT`, `DRUPAL_OIDC_TOKEN_ENDPOINT`, `DRUPAL_OIDC_USERINFO_ENDPOINT`, `DRUPAL_OIDC_END_SESSION_ENDPOINT`, and `DRUPAL_OIDC_SCOPES`.

The Redis endpoint and S3 bucket name are not injected; configure them in the Drupal modules.

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/drupal` |
| Volume Name | `drupalData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` (used when EFS is not available) |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/nginx/access.log`, `/var/log/nginx/error.log`, `/var/log/php-fpm/error.log`, `/var/www/html/sites/default/files/logs/drupal.log`, `/var/log/userdata.log` |
| CloudWatch Log Group | `/cloudforge/<stackName>/drupal` |

---

## Deployment Context Examples

Set `cpu` and `memory` (Fargate) or `instanceType` (EC2) explicitly; otherwise the framework defaults (`1024`, `2048`, `t3.micro`) apply.

### Development - Minimal

```json
{
  "stackName": "Drupal-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "drupal",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseName": "drupal",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Staging - With Authentication

```json
{
  "stackName": "Drupal-Staging",
  "runtime": "fargate",
  "securityProfile": "staging",
  "topology": "cms-service",
  "applicationId": "drupal",

  "domain": "staging.example.com",
  "subdomain": "cms",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "drupal-staging-yourcompany",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseName": "drupal",

  "enableMonitoring": true,
  "logRetentionDays": "90"
}
```

### Production - With SOC 2 and HIPAA Controls

`complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for the listed frameworks. It does not certify the deployment. This example uses EC2, where the database is configured manually during installation, so PostgreSQL is an option:

```json
{
  "stackName": "Drupal-Production",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "drupal",

  "domain": "example.com",
  "subdomain": "www",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "drupal-prod-yourcompany",
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

  "complianceFrameworks": "soc2,hipaa",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "731",
  "retainStorage": true
}
```

---

## Post-Deployment Tasks

### 1. Complete the Installation

- **Fargate**: the site is installed automatically. Sign in at `https://<your-domain>/user/login` as `admin`.
- **EC2**: open `https://<your-domain>`, select the **Standard** profile, and enter the RDS connection details (endpoint and credentials from the RDS Secrets Manager secret).

### 2. Install Modules

Commonly used modules for Drupal on AWS:

- **S3 File System** (`s3fs`): stores public files in the S3 media bucket (grant the task role or configured credentials access to the bucket first)
- **Redis** (`redis`): connects to ElastiCache using the PhpRedis backend
- **Metatag**, **Pathauto**, **Config Split**

On Fargate, Drush is at `/opt/drupal/vendor/bin/drush`. Install modules with Composer and Drush:

```bash
cd /opt/drupal
composer require drupal/s3fs drupal/redis
./vendor/bin/drush en s3fs redis -y
./vendor/bin/drush cr
```

### 3. Configuration Sync

Export configuration for version control, and import it on deployment:

```bash
drush cex -y
drush cim -y
drush cr
```

### 4. Database Updates

After a module update:

```bash
drush updb -y
drush cr
```

---

## Compliance Considerations

Application-level controls to review alongside CloudForge's infrastructure controls:

**SOC 2**

- Enable database logging (`dblog`) or syslog
- Configure session timeouts
- Enforce password policies (Password Policy module)
- Restrict admin role assignments
- Enable revision tracking on content types

**HIPAA**

- Apply field-level access controls to content types that hold PHI
- Log content access
- Use the private file system for restricted downloads

---

## Troubleshooting

### Permission denied errors on `sites/default/files`

The container runs as `www-data` (UID 33). Check the EFS access point:

```bash
aws efs describe-access-points --file-system-id <efs-id>
```

The access point should use `posixUser` UID/GID `33` and `creationInfo` permissions `755`.

### Drush not found

On Fargate, Drush is installed by Composer at `/opt/drupal/vendor/bin/drush` during the first start. On EC2, install Drush with Composer in the Drupal project directory.

---

## Related Documentation

- [CMS Guides Index](README.md)
- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)
