# WordPress Application Guide

WordPress is an open-source CMS for blogs, marketing sites, and portfolios, and for e-commerce through plugins such as WooCommerce.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `wordpress` |
| **Category** | CMS |
| **Default Image** | `wordpress:php8.2-apache` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Recommended CPU / Memory (Fargate)** | 1024 / 2048 MB |
| **Recommended Instance Type (EC2)** | `t3.small` |
| **Health Check Path** | `/wp-admin/install.php` |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication Modes** | `alb-oidc`, `application-oidc` (OpenID Connect Generic plugin), `none` |
| **Database** | Required (MySQL 8.0 default; MariaDB supported) |

The EC2 runtime installs nginx and PHP-FPM on the instance instead of using the container image.

---

## Auto-Provisioned Infrastructure

The `cms-service` topology provisions the following based on WordPress capabilities:

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Media offloading (WP Offload Media) |
| ElastiCache Redis | Yes | Object cache (Redis Object Cache plugin) |
| CloudFront CDN | Yes | Media (`/wp-content/uploads/*`) and static assets (themes, plugins, `wp-includes`) |
| EFS | Yes | `/var/www/html` (themes, plugins, uploads) |
| Route53 records | When a hosted zone and domain are configured | A + AAAA alias records |

---

## Authentication

| Mode | Description |
|------|-------------|
| `alb-oidc` | Cognito at the ALB. Protects `/wp-admin/*` and `/wp-login.php`; the public site stays reachable without signing in. |
| `application-oidc` | WordPress handles OIDC through the OpenID Connect Generic plugin (`OIDC_*` environment variables). |
| `none` | No authentication in front of WordPress. |

---

## First-Run Install and Admin Password

On Fargate, the container installs WordPress automatically on first start:

1. CloudForge generates a random admin password in Secrets Manager and exposes its ARN as the stack output `CloudForgeAutoAdminPasswordSecretArn`.
2. The container downloads WP-CLI, waits for the image entrypoint to write `wp-config.php`, and runs `wp core install` with user `admin` and that password.
3. The password is also printed once to the container log at startup.

Restarts skip the install because `wp core is-installed` succeeds. The admin email is `admin@<fqdn>`, or `cognitoInitialAdminEmail` when `authMode` is `alb-oidc` and that property is set.

Retrieve the password:

```bash
aws secretsmanager get-secret-value --secret-id <CloudForgeAutoAdminPasswordSecretArn> \
  --query SecretString --output text
```

---

## Environment Variables

CloudForge sets the following on the Fargate container:

| Variable | Value |
|----------|-------|
| `WORDPRESS_DB_HOST` | `<rds-endpoint>:<port>` (when `provisionDatabase` is `true`) |
| `WORDPRESS_DB_NAME`, `WORDPRESS_DB_USER` | RDS database name and user |
| `WORDPRESS_DB_PASSWORD` | From the RDS Secrets Manager secret |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | Generic copies of the database settings |
| `WORDPRESS_CONFIG_EXTRA` | Defines `WP_HOME`, `WP_SITEURL`, `FORCE_SSL_ADMIN` (when a domain is set), `DISABLE_WP_CRON`, and `DISALLOW_FILE_EDIT` |
| `WORDPRESS_SITE_URL`, `WORDPRESS_ADMIN_EMAIL` | Used by the first-run install (when a domain is set) |
| `WORDPRESS_ADMIN_PASSWORD` | From the generated admin password secret |

The Redis endpoint and S3 bucket name are not injected; see [Configure Redis Object Cache](#4-configure-redis-object-cache) and [Configure S3 Media Offloading](#3-configure-s3-media-offloading).

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/wordpress` |
| Volume Name | `wordpressData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` (used when EFS is not available) |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/nginx/access.log`, `/var/log/nginx/error.log`, `/var/log/php-fpm/error.log`, `/var/log/userdata.log` |
| CloudWatch Log Group | `/cloudforge/<stackName>/wordpress` |

---

## Deployment Context Examples

Set `cpu` and `memory` (Fargate) or `instanceType` (EC2) explicitly. The framework defaults (`cpu: 1024`, `memory: 2048`, `instanceType: t3.micro`) apply when they are omitted.

### Development - Minimal

```json
{
  "stackName": "WordPress-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "wordpress",

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

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Development - With Authentication

```json
{
  "stackName": "WordPress-Dev-Auth",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "wordpress",

  "domain": "dev.example.com",
  "subdomain": "blog",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "wordpress-dev-yourcompany",

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 20,

  "enableMonitoring": true,
  "logRetentionDays": "30"
}
```

### Production - High Traffic

```json
{
  "stackName": "WordPress-Production",
  "runtime": "fargate",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "wordpress",

  "domain": "example.com",
  "subdomain": "www",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "wordpress-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "cpu": 2048,
  "memory": 4096,
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 30,

  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365",
  "retainStorage": true
}
```

### Production - With SOC 2 and HIPAA Controls

`complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for the listed frameworks. It does not certify the deployment; application-level controls and audits remain your responsibility.

```json
{
  "stackName": "WordPress-Compliant",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "wordpress",

  "domain": "example.com",
  "subdomain": "www",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "wordpress-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.large",
  "databaseAllocatedStorageGB": 100,
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

## Health Check Configuration

| Property | Default | Deployment-context property |
|----------|---------|-----------------------------|
| Path | `/wp-admin/install.php` | — |
| Grace Period | 300 seconds | `healthCheckGracePeriod` |
| Interval | 30 seconds | `healthCheckInterval` |
| Timeout | 5 seconds | `healthCheckTimeout` |
| Healthy Threshold | 2 | `healthyThreshold` |
| Unhealthy Threshold | 3 | `unhealthyThreshold` |

---

## Post-Deployment Tasks

### 1. Sign In

On Fargate the site is installed automatically (see [First-Run Install](#first-run-install-and-admin-password)). Sign in at `https://<your-domain>/wp-admin/` as `admin`. On EC2, WordPress core is downloaded to `/var/www/html` but not configured; complete the installer in the browser.

### 2. Install Plugins

Commonly used plugins for WordPress on AWS:

- **WP Offload Media Lite**: syncs uploads to S3
- **Redis Object Cache**: connects to ElastiCache Redis
- **W3 Total Cache** or **WP Super Cache**: page caching
- **Wordfence**: application-level security scanning (complements the ALB WAF when `wafEnabled` is `true`)

`DISALLOW_FILE_EDIT` is set, which disables the theme and plugin file editors but not plugin installation.

### 3. Configure S3 Media Offloading

1. Find the media bucket in the stack's resources (logical ID prefix `wordpressmedia`).
2. Grant the ECS task role (or an IAM user configured in the plugin) read/write access to the bucket.
3. Install WP Offload Media Lite, open **Settings** > **Offload Media**, and select the bucket.

### 4. Configure Redis Object Cache

1. Find the ElastiCache cluster (named `wordpress-<env>-cache`) and note its endpoint.
2. Add `define('WP_REDIS_HOST', '<endpoint>');` to `wp-config.php` (or extend `WORDPRESS_CONFIG_EXTRA`).
3. Install Redis Object Cache and click **Enable Object Cache** under **Settings** > **Redis**.

### 5. Scheduled Tasks

`DISABLE_WP_CRON` is set, so WP-Cron does not run on page loads. Schedule `wp-cron.php` externally (for example, every 15 minutes).

---

## Troubleshooting

### White screen or 500 error on first load

WordPress requires a database connection on startup. Check the logs:

```bash
# Fargate: the log group is /aws/ecs/<stackName>/fargate/<securityProfile>
# (CloudFormation generates the name when logs are retained)
aws logs tail /aws/ecs/<stackName>/fargate/dev --follow

# EC2 (via SSM)
aws ssm start-session --target <instance-id>
# then: tail -f /var/log/php-fpm/error.log
```

Verify that the database secret exists and that the RDS security group allows traffic from the service.

### Login redirect loop

The ALB terminates TLS and forwards HTTP to the container. If WordPress does not detect HTTPS, add to `wp-config.php` (or to `WORDPRESS_CONFIG_EXTRA`):

```php
if (isset($_SERVER['HTTP_X_FORWARDED_PROTO']) && $_SERVER['HTTP_X_FORWARDED_PROTO'] === 'https') {
    $_SERVER['HTTPS'] = 'on';
}
```

CloudForge sets `WP_HOME`, `WP_SITEURL`, and `FORCE_SSL_ADMIN` through `WORDPRESS_CONFIG_EXTRA`, but not this proxy check.

### Uploads not persisting (Fargate)

EFS is mounted at `/var/www/html` (access point path `/wordpress`). Verify the mount in the task definition and that the access point uses UID/GID `33` with permissions `755`.

---

## Related Documentation

- [CMS Guides Index](README.md)
- [WooCommerce Guide](woocommerce.md): e-commerce on top of WordPress
- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)
