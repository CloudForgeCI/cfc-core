# Magento Application Guide

Magento (Adobe Commerce Open Source) is an enterprise e-commerce platform designed for high-traffic storefronts, complex product catalogs, and multi-store deployments. It has the highest resource requirements of any CMS in CloudForge.

**Status**: Available

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `magento` |
| **Category** | E-Commerce |
| **Default Image** | `magento/magento-cloud-docker-php:8.2-fpm` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Default CPU** | 4096 (Fargate) |
| **Default Memory** | 8192 MB (Fargate) |
| **Health Check Path** | `/health_check.php` |
| **Health Check Grace** | 600 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes (recommended for production) |
| **Authentication** | ALB-OIDC (Cognito) |
| **Database Required** | Yes (MySQL 8.0) |

**Note:** Magento requires significantly more CPU and memory than other CMS applications. Do not reduce below 4096 CPU / 8192 MB without testing — Magento will fail to serve requests under typical catalog load.

---

## Capabilities

- Multi-store and multi-website management from a single admin
- Layered navigation with Elasticsearch
- Advanced pricing rules (catalog, cart, customer groups)
- B2B features (company accounts, requisition lists, negotiated quotes)
- Visual Merchandiser for category sorting
- GraphQL API for headless/PWA storefronts
- Page Builder for content creation
- Magento CLI for deployments and indexing

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Media storage (`pub/media`) |
| ElastiCache Redis | Yes | Full-page cache, session storage, default cache backend |
| CloudFront CDN | Yes | Static assets (`pub/static`), media delivery |
| EFS | Yes | `/var/www/html/pub` (generated assets, media) |
| Route53 records | When domain configured | A + AAAA records to ALB |

Magento uses Redis for three separate cache backends (default cache, page cache, sessions). All three are configured automatically against the provisioned ElastiCache cluster.

---

## Authentication

The Magento storefront (product catalog, cart, checkout) can be public or restricted. The Magento Admin panel (`/admin`) is protected separately. CloudForge gates the entire site at the ALB with Cognito.

For a public storefront with protected admin:

```json
{
  "authMode": "alb-oidc",
  "publicPaths": ["/", "/catalog/*", "/catalogsearch/*", "/checkout/*", "/customer/*", "/graphql", "/rest/*", "/pub/*"]
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
| `MAGENTO_DB_HOST` | RDS endpoint |
| `MAGENTO_DB_NAME` | Database name |
| `MAGENTO_DB_USER` | Database user |
| `MAGENTO_DB_PASSWORD` | From Secrets Manager |
| `MAGENTO_OIDC_CLIENT_ID` | Cognito client ID |
| `MAGENTO_OIDC_AUTHORIZATION_ENDPOINT` | Cognito authorization endpoint |
| `MAGENTO_OIDC_TOKEN_ENDPOINT` | Cognito token endpoint |
| `REDIS_HOST` | ElastiCache endpoint |
| `REDIS_PORT` | `6379` |

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/magento` |
| Volume Name | `magentoData` |
| Media Upload Path | `/var/www/html/pub/media` |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/nginx/error.log`, `/var/log/php-fpm/error.log`, `/var/www/html/var/log/system.log`, `/var/www/html/var/log/exception.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal

Magento requires more resources even in development. A reduced setup for testing:

```json
{
  "stackName": "Magento-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "magento",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 4096,
  "memory": 8192,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseName": "magento",

  "enableMonitoring": true,
  "logRetentionDays": "7",
  "healthCheckGracePeriod": 600
}
```

**Cost estimate:** ~$200/month

### Production - EC2 (Recommended)

EC2 is strongly recommended for Magento production due to the consistent resource requirements and lower cost at sustained CPU:

```json
{
  "stackName": "Magento-Production",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "magento",

  "domain": "example.com",
  "subdomain": "store",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "magento-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "c5.2xlarge",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 50,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.r6g.xlarge",
  "databaseAllocatedStorageGB": 500,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 30,
  "databaseName": "magento",

  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365",
  "retainStorage": true,
  "healthCheckGracePeriod": 600
}
```

**Cost estimate:** ~$800-1200/month

### Production - PCI-DSS

```json
{
  "stackName": "Magento-PCI",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "magento",

  "domain": "example.com",
  "subdomain": "store",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "magento-pci-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "c5.2xlarge",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 8,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 50,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.r6g.xlarge",
  "databaseAllocatedStorageGB": 500,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 90,
  "databaseName": "magento",

  "complianceFrameworks": "PCI-DSS,SOC2",
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
  "healthCheckGracePeriod": 600
}
```

**Cost estimate:** ~$1200-1800/month

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/health_check.php` | Lightweight built-in endpoint |
| Grace Period | **600 seconds** | Magento requires time for caches and DI compilation |
| Interval | 30 seconds | Time between checks |
| Timeout | 10 seconds | Longer timeout for heavy PHP bootstrap |

**Important:** Magento's startup is slow due to dependency injection compilation and cache warming. Do not reduce the grace period below 300 seconds.

---

## Post-Deployment Tasks

### 1. Run Magento Setup

Magento requires a CLI setup command after first deployment:

```bash
aws ssm start-session --target <instance-id>

cd /var/www/html
php bin/magento setup:install \
  --base-url=https://store.example.com \
  --db-host=$MAGENTO_DB_HOST \
  --db-name=$MAGENTO_DB_NAME \
  --db-user=$MAGENTO_DB_USER \
  --db-password=$MAGENTO_DB_PASSWORD \
  --admin-firstname=Admin \
  --admin-lastname=User \
  --admin-email=admin@example.com \
  --admin-user=admin \
  --admin-password=Admin123! \
  --backend-frontname=admin \
  --session-save=redis \
  --session-save-redis-host=$REDIS_HOST \
  --cache-backend=redis \
  --cache-backend-redis-server=$REDIS_HOST \
  --page-cache=redis \
  --page-cache-redis-server=$REDIS_HOST
```

### 2. Set Production Mode and Compile

```bash
php bin/magento deploy:mode:set production
php bin/magento setup:di:compile
php bin/magento setup:static-content:deploy en_US
php bin/magento cache:flush
```

### 3. Configure Cron

Magento relies heavily on cron for indexing, email, and order processing:

```bash
php bin/magento cron:install
```

### 4. Configure Admin Security

1. Change the default admin URL (`--backend-frontname`) to something non-obvious
2. Enable two-factor authentication for admin users: **Admin** > **Stores** > **Configuration** > **Security** > **2FA**

---

## Compliance Considerations

### PCI-DSS

Magento using a hosted payment gateway (Stripe, Braintree) reduces PCI scope. If using Magento Payments or direct card capture, full PCI-DSS applies.

- [ ] Use hosted payment fields (Stripe Elements, Braintree Hosted Fields) — never handle raw card data
- [ ] Enable Magento's built-in brute force protection
- [ ] Enable TLS 1.2+ only (ALB default)
- [ ] Change admin URL from default `/admin`
- [ ] Enable WAF with OWASP ruleset
- [ ] Quarterly vulnerability scans

---

## Troubleshooting

### Magento shows blank page or 500 error

Enable developer mode temporarily to see error details:

```bash
php bin/magento deploy:mode:set developer
# reproduce the error, then check:
tail -100 var/log/exception.log
```

### Slow admin panel

Run the indexers and flush caches:

```bash
php bin/magento indexer:reindex
php bin/magento cache:flush
```

### `var/` directory permission errors

Magento writes heavily to `var/`, `pub/`, and `generated/`. Ensure these are writable by the web user:

```bash
find /var/www/html/var /var/www/html/pub /var/www/html/generated -type d -exec chmod 755 {} \;
find /var/www/html/var /var/www/html/pub -type f -exec chmod 644 {} \;
chown -R www-data:www-data /var/www/html
```

---

## Related Documentation

- [CMS Guides Index](README.md)
- [WooCommerce Guide](woocommerce.md) — Lighter-weight e-commerce alternative
- [CMS Topology Reference](../../applications/CMS.md)
