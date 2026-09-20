# WooCommerce Application Guide

WooCommerce is a WordPress plugin that adds an e-commerce storefront to WordPress. The `woocommerce` application extends the [WordPress](wordpress.md) specification with larger recommended resources, additional PHP extensions (`gmp`, `sodium`), and e-commerce-oriented WordPress settings.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `woocommerce` |
| **Category** | E-Commerce |
| **Default Image** | `wordpress:php8.2-apache` (same as WordPress) |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Recommended CPU / Memory (Fargate)** | 2048 / 4096 MB |
| **Recommended Instance Type (EC2)** | `t3.medium` |
| **Health Check Path** | `/wp-admin/install.php` |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication Modes** | `alb-oidc`, `application-oidc` (OpenID Connect Generic plugin), `none` |
| **Database** | Required (MySQL 8.0 default; MariaDB supported; default database name `woocommerce`) |

The container installs WordPress on first start, as described in the [WordPress guide](wordpress.md#first-run-install-and-admin-password). The WooCommerce plugin itself is **not** installed automatically; install it after the first sign-in.

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Product images and downloadable products |
| ElastiCache Redis | Yes | Object cache and session storage |
| CloudFront CDN | Yes | Product image and static asset delivery |
| EFS | Yes | `/var/www/html` (themes, plugins, uploads) |
| Route53 records | When a hosted zone and domain are configured | A + AAAA alias records |

---

## Authentication

| Mode | Description |
|------|-------------|
| `alb-oidc` | Cognito at the ALB. Protects `/wp-admin/*` and `/wp-login.php`; the storefront, cart, and checkout stay public. |
| `application-oidc` | WordPress handles OIDC through the OpenID Connect Generic plugin. |
| `none` | No authentication in front of the store. |

---

## Environment Variables

WooCommerce uses the same variables as [WordPress](wordpress.md#environment-variables), including `WORDPRESS_DB_PASSWORD` from Secrets Manager and the generated `WORDPRESS_ADMIN_PASSWORD`. `WORDPRESS_CONFIG_EXTRA` additionally defines:

- `FORCE_SSL_LOGIN` and `FORCE_SSL_ADMIN` (when `enableSsl` is `true`)
- `DISABLE_WP_CRON`
- `WP_MEMORY_LIMIT` and `WP_MAX_MEMORY_LIMIT` (`512M`)
- `WC_LOG_HANDLER` (`WC_Log_Handler_File`)

The Redis endpoint and S3 bucket name are not injected.

---

## Storage Configuration

### Container (Fargate)

WooCommerce inherits the WordPress storage settings:

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/wordpress` |
| Volume Name | `wordpressData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

---

## Deployment Context Examples

Set `cpu` and `memory` (Fargate) or `instanceType` (EC2) explicitly; otherwise the framework defaults (`1024`, `2048`, `t3.micro`) apply.

### Development - Minimal

```json
{
  "stackName": "WooCommerce-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "woocommerce",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 20,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "WooCommerce-Production",
  "runtime": "fargate",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "woocommerce",

  "domain": "example.com",
  "subdomain": "shop",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "shop-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "cpu": 2048,
  "memory": 4096,
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 8,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.r6g.large",
  "databaseAllocatedStorageGB": 200,
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

### Production - With PCI DSS and SOC 2 Controls

`complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for the listed frameworks. It does not make the store PCI DSS compliant. A hosted payment gateway reduces, but does not eliminate, PCI DSS scope.

```json
{
  "stackName": "WooCommerce-PCI",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "woocommerce",

  "domain": "example.com",
  "subdomain": "shop",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "shop-pci-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.r6g.large",
  "databaseAllocatedStorageGB": 200,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 90,

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
  "retainStorage": true
}
```

---

## Post-Deployment Tasks

### 1. Install WooCommerce

1. Sign in at `https://<your-domain>/wp-admin/` as `admin` (password from the `CloudForgeAutoAdminPasswordSecretArn` stack output).
2. **Plugins** > **Add New**: install and activate **WooCommerce**, or use the WP-CLI copy that the container downloads at startup: `php /tmp/wp-cli.phar --allow-root --path=/var/www/html plugin install woocommerce --activate`.
3. Run the WooCommerce setup wizard: store country, currency, payment methods, shipping zones.

### 2. Configure a Payment Gateway

WooCommerce does not include a card payment gateway by default. Common options:

- **WooCommerce Stripe Payment Gateway**
- **WooCommerce PayPal Payments**
- **WooCommerce Square**

Store gateway API keys in AWS Secrets Manager rather than in `wp-config.php`.

### 3. Configure Redis

Follow [Configure Redis Object Cache](wordpress.md#4-configure-redis-object-cache) in the WordPress guide, using the cluster named `woocommerce-<env>-cache`.

### 4. Configure S3 Product Images

Follow [Configure S3 Media Offloading](wordpress.md#3-configure-s3-media-offloading) in the WordPress guide, using the bucket with logical ID prefix `woocommercemedia`.

### 5. Scheduled Tasks

`DISABLE_WP_CRON` is set. Schedule `wp-cron.php` and the Action Scheduler (`wp action-scheduler run`) externally.

---

## Compliance Considerations

### PCI DSS

With a hosted payment gateway, card data does not reach your servers, which reduces PCI DSS scope. Application-level items to review:

- Use only PCI DSS compliant payment gateways
- Enable WAF (`wafEnabled`)
- Store no card data in the database
- Enable audit logging for order events
- Schedule quarterly vulnerability scans

---

## Related Documentation

- [WordPress Guide](wordpress.md)
- [CMS Guides Index](README.md)
- [CMS Topology Reference](../../applications/CMS.md)
