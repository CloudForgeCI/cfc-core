# Magento Application Guide

Magento Open Source (Adobe Commerce) is an e-commerce platform for storefronts with large product catalogs and multi-store setups. It has the highest resource requirements of the CMS applications in CloudForge.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `magento` |
| **Category** | E-Commerce |
| **Default Image** | `magento/magento-cloud-docker-php:8.2-fpm` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Recommended CPU / Memory (Fargate)** | 4096 / 8192 MB |
| **Recommended Instance Type (EC2)** | `t3.xlarge` |
| **Health Check Path** | `/health_check.php` |
| **Recommended Health Check Grace** | 600 seconds |
| **Supports Fargate** | Yes (see the note below) |
| **Supports EC2** | Yes |
| **Authentication Modes** | `alb-oidc`, `application-oidc` (miniOrange OIDC module), `none` |
| **Database** | Required (MySQL 8.0 default; MariaDB supported) |

**Runtime notes:**

- **Fargate**: the default image is a PHP-FPM runtime image. It does not serve HTTP on port 80 by itself and does not contain Magento. Plan to build your own image that includes Magento and a web server.
- **EC2**: UserData installs nginx, PHP-FPM, and a single-node Elasticsearch 8 on each instance, but does not download Magento. Install the Magento code base (for example, with Composer) into `/var/www/html` before running `setup:install`.

Magento 2.4 requires OpenSearch or Elasticsearch for catalog search. Apart from the per-instance Elasticsearch on EC2, CloudForge does not provision a search cluster.

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Media storage (`pub/media`) |
| ElastiCache Redis | Yes | Default cache, full-page cache, and session storage |
| CloudFront CDN | Yes | Static assets (`/pub/static/*`, `/static/*`) and media (`/pub/media/*`, `/media/*`) |
| EFS | Yes | Mounted at `/var/www/html` (access point path `/magento`) |
| Route53 records | When a hosted zone and domain are configured | A + AAAA alias records |

The Redis endpoint is not injected into the container. Configure the cache and session backends during `setup:install` (see below).

---

## Authentication

| Mode | Description |
|------|-------------|
| `alb-oidc` | Cognito at the ALB. Protects `/admin/*`, `/backend/*`, and `/setup/*`; the storefront stays public. |
| `application-oidc` | Magento handles OIDC through the miniOrange module (`MAGENTO_OIDC_*` environment variables). |
| `none` | No authentication in front of Magento. |

---

## Environment Variables

CloudForge sets the following on the Fargate container:

| Variable | Value |
|----------|-------|
| `MAGENTO_DATABASE_HOST`, `MAGENTO_DATABASE_PORT`, `MAGENTO_DATABASE_NAME`, `MAGENTO_DATABASE_USER` | RDS connection settings (when `provisionDatabase` is `true`) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | Generic copies of the database settings |
| `DATABASE_PASSWORD` | From the RDS Secrets Manager secret |
| `MAGE_MODE` | `production` |
| `MAGENTO_BASE_URL`, `MAGENTO_BASE_URL_SECURE`, `MAGENTO_USE_SECURE`, `MAGENTO_USE_SECURE_ADMIN`, `MAGENTO_BACKEND_FRONTNAME` | Set when a domain is configured |
| `MAGENTO_CACHE_BACKEND`, `MAGENTO_SESSION_SAVE` | `redis` |

With `application-oidc`, CloudForge also sets `MAGENTO_OIDC_CLIENT_ID`, `MAGENTO_OIDC_AUTHORIZE_URL`, `MAGENTO_OIDC_TOKEN_URL`, `MAGENTO_OIDC_USERINFO_URL`, `MAGENTO_OIDC_LOGOUT_URL`, and `MAGENTO_OIDC_SCOPE`.

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/magento` |
| Volume Name | `magentoData` |
| Media Upload Path | `/var/www/html/pub/media` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` (used when EFS is not available) |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/nginx/access.log`, `/var/log/nginx/error.log`, `/var/log/php-fpm/error.log`, `/var/www/html/var/log/system.log`, `/var/www/html/var/log/exception.log`, `/var/www/html/var/log/debug.log`, `/var/log/userdata.log` |
| CloudWatch Log Group | `/cloudforge/<stackName>/magento` |

---

## Deployment Context Examples

Set `cpu`/`memory` (Fargate) or `instanceType` (EC2) and `healthCheckGracePeriod` explicitly. When omitted, the framework defaults (`1024` CPU, `2048` MB, `t3.micro`, 300 seconds) apply, which are too small for Magento.

### Development - Minimal

```json
{
  "stackName": "Magento-Dev",
  "runtime": "ec2",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "magento",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.xlarge",

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

### Production

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

### Production - With PCI DSS and SOC 2 Controls

`complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for the listed frameworks. It does not make the store PCI DSS compliant; scope depends on how payments are handled (see below).

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

  "complianceFrameworks": "pci-dss,soc2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "731",
  "retainStorage": true,
  "healthCheckGracePeriod": 600
}
```

---

## Health Check Configuration

| Property | Recommended | Deployment-context property |
|----------|-------------|-----------------------------|
| Path | `/health_check.php` | — |
| Grace Period | 600 seconds (startup includes dependency-injection compilation and cache warm-up) | `healthCheckGracePeriod` (framework default 300, maximum 900) |
| Interval | 30 seconds (default) | `healthCheckInterval` |
| Timeout | 10 seconds (framework default is 5) | `healthCheckTimeout` |

---

## Post-Deployment Tasks

### 1. Run Magento Setup

Install Magento from the instance (EC2 via SSM). Use the RDS endpoint and credentials from the RDS Secrets Manager secret, the ElastiCache endpoint from the stack's resources, and your search engine host (`localhost` for the per-instance Elasticsearch on EC2):

```bash
aws ssm start-session --target <instance-id>

cd /var/www/html
php bin/magento setup:install \
  --base-url=https://store.example.com/ \
  --db-host=<rds-endpoint> \
  --db-name=magento \
  --db-user=<db-user> \
  --db-password=<db-password> \
  --admin-firstname=Admin \
  --admin-lastname=User \
  --admin-email=admin@example.com \
  --admin-user=admin \
  --admin-password=<choose-a-strong-password> \
  --backend-frontname=admin \
  --search-engine=elasticsearch8 \
  --elasticsearch-host=<search-host> \
  --session-save=redis \
  --session-save-redis-host=<redis-endpoint> \
  --session-save-redis-db=2 \
  --cache-backend=redis \
  --cache-backend-redis-server=<redis-endpoint> \
  --cache-backend-redis-db=0 \
  --page-cache=redis \
  --page-cache-redis-server=<redis-endpoint> \
  --page-cache-redis-db=1
```

### 2. Set Production Mode and Compile

```bash
php bin/magento deploy:mode:set production
php bin/magento setup:di:compile
php bin/magento setup:static-content:deploy en_US
php bin/magento cache:flush
```

### 3. Configure Cron

Magento uses cron for indexing, email, and order processing:

```bash
php bin/magento cron:install
```

### 4. Secure the Admin

1. Use a non-default admin path (`--backend-frontname`). If you change it from `admin`, the default `alb-oidc` protected paths no longer cover it.
2. Enable two-factor authentication for admin users: **Stores** > **Configuration** > **Security** > **2FA**.

---

## Compliance Considerations

### PCI DSS

Using a hosted payment gateway with hosted payment fields keeps raw card data off your servers and reduces PCI DSS scope. Direct card capture brings the full environment into scope. Application-level items to review:

- Use hosted payment fields; never handle raw card data
- Enable Magento's brute-force protection
- Use a non-default admin URL
- Enable WAF (`wafEnabled`)
- Schedule quarterly vulnerability scans

---

## Troubleshooting

### Blank page or 500 error

Switch to developer mode temporarily to see error details:

```bash
php bin/magento deploy:mode:set developer
# reproduce the error, then:
tail -100 var/log/exception.log
```

### Slow admin panel

Run the indexers and flush caches:

```bash
php bin/magento indexer:reindex
php bin/magento cache:flush
```

### `var/` directory permission errors

Magento writes to `var/`, `pub/`, and `generated/`. Make them writable by the web server user (`nginx` on the EC2 runtime, `www-data` in Debian-based images):

```bash
find /var/www/html/var /var/www/html/pub /var/www/html/generated -type d -exec chmod 755 {} \;
find /var/www/html/var /var/www/html/pub -type f -exec chmod 644 {} \;
chown -R <web-user>:<web-user> /var/www/html
```

---

## Related Documentation

- [CMS Guides Index](README.md)
- [WooCommerce Guide](woocommerce.md): lighter-weight e-commerce option
- [CMS Topology Reference](../../applications/CMS.md)
