# PrestaShop Application Guide

PrestaShop is an open-source e-commerce platform with a storefront, back office, and a marketplace of modules and themes.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `prestashop` |
| **Category** | E-Commerce |
| **Default Image** | `prestashop/prestashop:8-8.1-apache` |
| **PHP Version** | 8.1 |
| **Application Port** | `80` |
| **Recommended CPU / Memory (Fargate)** | 2048 / 4096 MB |
| **Recommended Instance Type (EC2)** | `t3.medium` |
| **Health Check Path** | `/` |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication Modes** | `alb-oidc`, `application-oidc`, `none` |
| **Database** | Required (MySQL 8.0 default; MariaDB supported) |

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Product images and downloadable products |
| ElastiCache Redis | Yes | Cache layer |
| CloudFront CDN | Yes | Image and static file delivery |
| EFS | Yes | Mounted at `/var/www/html` (access point path `/prestashop`) |
| Route53 records | When a hosted zone and domain are configured | A + AAAA alias records |

---

## Authentication

| Mode | Description |
|------|-------------|
| `alb-oidc` | Cognito at the ALB. Protects `/admin*` (which covers PrestaShop's renamed admin folder, such as `/admin123abc`), `/admin-dev/*`, and `/install/*`; the storefront stays public. |
| `application-oidc` | PrestaShop handles OIDC itself (`PS_OIDC_*` environment variables). |
| `none` | No authentication in front of PrestaShop. |

---

## Environment Variables

CloudForge sets the following on the Fargate container:

| Variable | Value |
|----------|-------|
| `PS_DB_SERVER`, `PS_DB_PORT`, `PS_DB_NAME`, `PS_DB_USER` | RDS connection settings (when `provisionDatabase` is `true`) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | Generic copies of the database settings |
| `DATABASE_PASSWORD` | From the RDS Secrets Manager secret |
| `PS_DOMAIN` | The site FQDN (when a domain is set) |
| `PS_ENABLE_SSL` | `1` when `enableSsl` is `true`, otherwise `0` |
| `PS_DEV_MODE` | `0` |
| `PS_INSTALL_AUTO` | `0` (the web installer runs on first load) |

With `application-oidc`, CloudForge also sets `PS_OIDC_CLIENT_ID`, `PS_OIDC_AUTHORIZE_URL`, `PS_OIDC_TOKEN_URL`, `PS_OIDC_USERINFO_URL`, `PS_OIDC_LOGOUT_URL`, and `PS_OIDC_SCOPE`.

The database password is exposed as `DATABASE_PASSWORD`, not the `DB_PASSWD` name the PrestaShop image reads, so enter it in the installer. The Redis endpoint and S3 bucket name are not injected.

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/prestashop` |
| Volume Name | `prestashopData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` (used when EFS is not available) |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/httpd/access_log`, `/var/log/httpd/error_log`, `/var/log/php-fpm/error.log`, `/var/www/html/var/logs/dev.log`, `/var/www/html/var/logs/prod.log`, `/var/log/userdata.log` |
| CloudWatch Log Group | `/cloudforge/<stackName>/prestashop` |

---

## Deployment Context Examples

Set `cpu` and `memory` (Fargate) or `instanceType` (EC2) explicitly; otherwise the framework defaults (`1024`, `2048`, `t3.micro`) apply.

### Development - Minimal

```json
{
  "stackName": "PrestaShop-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "prestashop",

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
  "databaseName": "prestashop",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "PrestaShop-Production",
  "runtime": "fargate",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "prestashop",

  "domain": "example.com",
  "subdomain": "shop",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "prestashop-prod-yourcompany",
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
  "databaseInstanceClass": "db.r6g.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 30,
  "databaseName": "prestashop",

  "wafEnabled": true,
  "albAccessLogging": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365",
  "retainStorage": true
}
```

---

## Post-Deployment Tasks

### 1. Complete the Installation

PrestaShop runs its install wizard on first load:

1. Open `https://<your-domain>`.
2. Follow the installer (license, store information, database, admin account). Use the RDS endpoint and the credentials from the RDS Secrets Manager secret.
3. Remove the `/install` directory after setup. On Fargate, use ECS Exec; on EC2, use SSM:

```bash
rm -rf /var/www/html/install
```

### 2. Find the Admin Directory

The installer renames the admin folder to a random name (for example, `/admin1234abc`). Note the new path; the default `alb-oidc` rule `/admin*` still covers it.

### 3. Configure Redis

1. Find the ElastiCache cluster (named `prestashop-<env>-cache`) and note its endpoint.
2. **Back Office** > **Advanced Parameters** > **Performance**: set the caching system and add the Redis server on port `6379`.

---

## Troubleshooting

### "Oops! An error occurred"

Enable debug mode temporarily in `/var/www/html/config/defines.inc.php`:

```php
define('_PS_MODE_DEV_', true);
```

Check the logs in `/var/www/html/var/logs/`.

### Admin panel not accessible after install

Check the renamed admin directory:

```bash
ls /var/www/html/ | grep admin
```

---

## Related Documentation

- [CMS Guides Index](README.md)
- [Magento Guide](magento.md)
- [WooCommerce Guide](woocommerce.md): WordPress-based e-commerce
- [CMS Topology Reference](../../applications/CMS.md)
