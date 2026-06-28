# PrestaShop Application Guide

PrestaShop is an open-source e-commerce platform widely used in Europe and Latin America. It provides a full-featured storefront out of the box with a large marketplace of modules and themes.

**Status**: Available

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `prestashop` |
| **Category** | E-Commerce |
| **Default Image** | `prestashop/prestashop:8-8.1-apache` |
| **PHP Version** | 8.1 |
| **Application Port** | `80` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Health Check Path** | `/index.php` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication** | ALB-OIDC (Cognito) |
| **Database Required** | Yes (MySQL 8.0) |

---

## Capabilities

- Full storefront with product catalog, cart, and checkout
- Multi-store from a single admin
- Multi-currency and multi-language
- Advanced SEO with URL rewriting
- Module marketplace (4,000+ modules)
- Native B2B features
- REST API for headless commerce

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Product images and downloadable products |
| ElastiCache Redis | Yes | Session storage, cache layer |
| CloudFront CDN | Yes | Image and static file delivery |
| EFS | Yes | `/var/www/html` (modules, themes, uploads) |
| Route53 records | When domain configured | A + AAAA records to ALB |

---

## Authentication

For storefronts with public browsing, set `publicPaths` to allow unauthenticated product catalog access while protecting the admin panel:

```json
{
  "authMode": "alb-oidc",
  "publicPaths": ["/", "/index.php*", "/category/*", "/product/*", "/cart*", "/order*", "/api/*", "/img/*", "/themes/*"]
}
```

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | **Recommended** | Cognito at ALB |
| `none` | Dev only | No authentication |

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `PS_OIDC_CLIENT_ID` | Cognito client ID |
| `PS_OIDC_AUTHORIZATION_ENDPOINT` | Cognito authorization endpoint |
| `PS_OIDC_TOKEN_ENDPOINT` | Cognito token endpoint |
| `PS_DB_SERVER` | RDS endpoint |
| `PS_DB_NAME` | Database name |
| `PS_DB_USER` | Database user |
| `PS_DB_PASSWD` | From Secrets Manager |
| `PS_DOMAIN` | Your domain (e.g., `shop.example.com`) |
| `PS_ENABLE_SSL` | `1` when `enableSsl: true` |
| `REDIS_HOST` | ElastiCache endpoint |

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

---

## Deployment Context Examples

### Development - Minimal

```json
{
  "stackName": "PrestaShop-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "application": "prestashop",

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
  "databaseName": "prestashop",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$40/month

### Production

```json
{
  "stackName": "PrestaShop-Production",
  "runtime": "fargate",
  "securityProfile": "production",
  "topology": "cms-service",
  "application": "prestashop",

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
  "publicPaths": ["/", "/index.php*", "/category/*", "/product/*", "/cart*", "/order*", "/api/*", "/img/*", "/themes/*"],

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

**Cost estimate:** ~$350-500/month

---

## Post-Deployment Tasks

### 1. Complete Installation

PrestaShop runs an install wizard on first load:

1. Navigate to `https://your-domain.com`
2. Follow the installer (license, database, store info, admin account)
3. **Delete the `/install` directory** after setup — PrestaShop will not function with it present:

```bash
aws ssm start-session --target <instance-id>
rm -rf /var/www/html/install
```

### 2. Change the Admin Directory Name

PrestaShop installs the admin panel at `/admin` by default, but renames it to a random string during install (e.g., `/admin1234abc`). Verify the new path in the installer output.

### 3. Configure Redis

1. **Back Office** > **Advanced Parameters** > **Performance**
2. Set **Caching** to **Memcache(d)/Redis**
3. Add Redis server: `REDIS_HOST` on port `6379`

---

## Troubleshooting

### Site shows "Oops! An error occurred"

Enable debug mode temporarily:

Edit `/var/www/html/config/defines.inc.php`:
```php
define('_PS_MODE_DEV_', true);
```

Check logs at `/var/www/html/var/logs/`.

### Admin panel not accessible after install

Verify the admin directory was renamed during installation. Check the actual directory name:

```bash
ls /var/www/html/ | grep admin
```

---

## Related Documentation

- [CMS Guides Index](README.md)
- [Magento Guide](magento.md) — Enterprise e-commerce alternative
- [WooCommerce Guide](woocommerce.md) — WordPress-based e-commerce
- [CMS Topology Reference](../../applications/CMS.md)
