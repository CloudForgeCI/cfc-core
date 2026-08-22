# WordPress Application Guide

WordPress is an open-source CMS for blogs, marketing sites, portfolios, and e-commerce through plugins such as WooCommerce.

**Status**: Verified

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `wordpress` |
| **Category** | CMS |
| **Default Image** | `wordpress:php8.2-fpm-alpine` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Health Check Path** | `/wp-admin/install.php` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication** | ALB-OIDC (Cognito) |
| **Database Required** | Yes (MySQL 8.0) |

---

## Capabilities

- Full plugin ecosystem (60,000+ plugins)
- Gutenberg block editor
- Custom post types and taxonomies
- Multisite network support
- WP-CLI for automation
- WooCommerce for e-commerce
- REST API
- S3 media offloading via WP Offload Media

---

## Auto-Provisioned Infrastructure

The `cms-service` topology automatically provisions based on WordPress capabilities:

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Media uploads offloading |
| ElastiCache Redis | Yes | Object cache (transients, sessions) |
| CloudFront CDN | Yes | Media and static asset delivery |
| EFS | Yes | `/var/www/html` (themes, plugins, uploads) |
| Route53 records | When domain configured | A + AAAA records to ALB |

---

## Authentication

WordPress is protected at the ALB level by Cognito. No WordPress plugin or configuration is required — users authenticate with Cognito before the request reaches the container.

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | **Recommended** | Cognito at ALB — full site protection |
| `none` | Dev only | No authentication |

**How it works:** Cognito issues a session cookie at the ALB. Authenticated users land directly on the WordPress site. The WordPress login page (`/wp-login.php`) remains accessible for wp-admin emergency access if needed.

---

## Environment Variables

CloudForge automatically injects:

| Variable | Description |
|----------|-------------|
| `WORDPRESS_DB_HOST` | RDS endpoint |
| `WORDPRESS_DB_USER` | Database user |
| `WORDPRESS_DB_NAME` | Database name |
| `WORDPRESS_DB_PASSWORD` | Retrieved from Secrets Manager at runtime |
| `WORDPRESS_TABLE_PREFIX` | `wp_` (default) |
| `WORDPRESS_DEBUG` | `false` in production |
| `REDIS_HOST` | ElastiCache endpoint (when Redis provisioned) |
| `REDIS_PORT` | `6379` |

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
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/nginx/error.log`, `/var/log/php-fpm/error.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal

```json
{
  "stackName": "WordPress-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "wordpress",

  "networkMode": "public-no-nat",
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

**Cost estimate:** ~$40/month

### Development - With Auth and Redis

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

**Cost estimate:** ~$120/month

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

**Cost estimate:** ~$350-550/month

### Production - SOC2 / HIPAA

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

  "complianceFrameworks": "SOC2,HIPAA",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$500-700/month

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/wp-admin/install.php` | Responds 200 before and after setup |
| Grace Period | 300 seconds | Time before checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |
| Healthy Threshold | 2 | Consecutive successes |
| Unhealthy Threshold | 3 | Consecutive failures |

---

## Post-Deployment Tasks

### 1. Complete WordPress Setup

1. Navigate to `https://your-domain.com`
2. The setup wizard appears on first load
3. Enter site title, admin username, and admin email
4. **Important**: Save the admin password — it cannot be recovered later

### 2. Install Recommended Plugins

For production WordPress on AWS:

- **WP Offload Media Lite** — Sync uploads to the auto-provisioned S3 bucket
- **Redis Object Cache** — Connect to the auto-provisioned ElastiCache Redis
- **W3 Total Cache** or **WP Super Cache** — Page caching layer
- **Wordfence** — Security scanning (WAF is handled at ALB but app-level scanning adds depth)

### 3. Configure S3 Media Offloading

After installing WP Offload Media Lite:

1. **Settings** > **Offload Media**
2. Select the S3 bucket provisioned by CloudForge (named `{stackName}-media`)
3. Enable **Remove Files From Server** to save EFS space

### 4. Configure Redis Object Cache

After installing Redis Object Cache plugin:

1. **Settings** > **Redis**
2. The `REDIS_HOST` env var is already set — click **Enable Object Cache**

---

## Troubleshooting

### White screen / 500 error on first load

WordPress requires a database connection on startup. Check:

```bash
# Fargate
aws logs tail /aws/ecs/wordpress --follow

# EC2 (via SSM)
aws ssm start-session --target <instance-id>
# then: tail -f /var/log/php-fpm/error.log
```

Verify the database credentials in Secrets Manager are correct and the security group allows the container to reach RDS.

### Login redirects loop

The ALB terminates TLS and forwards HTTP to the container. Add to `wp-config.php`:

```php
if (isset($_SERVER['HTTP_X_FORWARDED_PROTO']) && $_SERVER['HTTP_X_FORWARDED_PROTO'] === 'https') {
    $_SERVER['HTTPS'] = 'on';
}
```

CloudForge pre-configures this via the `WORDPRESS_CONFIG_EXTRA` environment variable.

### Uploads not persisting (Fargate)

EFS must be mounted at `/var/www/html/wp-content/uploads`. Verify EFS mount in task definition and that the EFS access point has correct permissions (`755`, UID `33`).

---

## Related Documentation

- [CMS Guides Index](README.md)
- [WooCommerce Guide](woocommerce.md) — E-commerce on top of WordPress
- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)
