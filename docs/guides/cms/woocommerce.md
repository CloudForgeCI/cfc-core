# WooCommerce Application Guide

WooCommerce is a WordPress plugin that adds a full e-commerce storefront to WordPress. It is the most widely deployed e-commerce platform on the web. CloudForge deploys WooCommerce as a first-class application — it is pre-configured with the WooCommerce plugin, Storefront theme, and higher default resources than plain WordPress.

**Status**: Available

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `woocommerce` |
| **Category** | E-Commerce |
| **Base Image** | `wordpress:php8.2-fpm-alpine` + WooCommerce pre-installed |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Health Check Path** | `/wp-admin/install.php` |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication** | ALB-OIDC (Cognito) |
| **Database Required** | Yes (MySQL 8.0) |

WooCommerce requires more CPU and memory than plain WordPress due to cart/checkout operations, product catalog queries, and order processing.

---

## Capabilities

- Full WordPress + WooCommerce stack
- Product catalog with variants and attributes
- Cart, checkout, and order management
- Payment gateway integrations (Stripe, PayPal, etc.)
- Shipping rate calculations
- Coupon and discount engine
- Customer accounts and order history
- REST API for headless storefronts
- S3 media offloading for product images

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Product images and downloadable products |
| ElastiCache Redis | Yes | Session storage, cart persistence, object cache |
| CloudFront CDN | Yes | Product image delivery |
| EFS | Yes | `/var/www/html` (themes, plugins, uploads) |
| Route53 records | When domain configured | A + AAAA records to ALB |

Redis session storage is particularly important for WooCommerce — it ensures cart contents persist across Fargate task replacements.

---

## Authentication

The storefront (public product pages, checkout) is typically unauthenticated. The WordPress admin (`/wp-admin`) and any protected store pages are protected by Cognito at the ALB.

For a **public storefront** (no login required to browse and buy), use `authMode: "none"` and restrict only the admin path:

```json
{
  "authMode": "alb-oidc",
  "publicPaths": ["/", "/shop/*", "/product/*", "/cart/*", "/checkout/*", "/my-account/*", "/wp-json/*"]
}
```

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `WORDPRESS_DB_HOST` | RDS endpoint |
| `WORDPRESS_DB_USER` | Database user |
| `WORDPRESS_DB_NAME` | Database name |
| `WORDPRESS_DB_PASSWORD` | From Secrets Manager |
| `REDIS_HOST` | ElastiCache endpoint |
| `REDIS_PORT` | `6379` |
| `WC_CART_SESSION_HANDLER` | `WC_Session_Handler_Redis` (when Redis provisioned) |

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/woocommerce` |
| Volume Name | `woocommerceData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

---

## Deployment Context Examples

### Development - Minimal Storefront

```json
{
  "stackName": "WooCommerce-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "woocommerce",

  "networkMode": "public-no-nat",
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

**Cost estimate:** ~$80/month

### Production - Full E-Commerce

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
  "publicPaths": ["/", "/shop/*", "/product/*", "/cart/*", "/checkout/*", "/my-account/*", "/wp-json/*"],

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

**Cost estimate:** ~$500-800/month

### Production - PCI-DSS (Payment Processing)

WooCommerce handles payment tokens but relies on third-party gateways (Stripe, PayPal) for card processing. PCI-DSS scope is reduced but not eliminated.

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
  "retainStorage": true
}
```

**Cost estimate:** ~$700-1000/month

---

## Post-Deployment Tasks

### 1. Complete WooCommerce Setup Wizard

1. Navigate to `https://your-domain.com`
2. Complete the WordPress install, then activate WooCommerce
3. Run the WooCommerce setup wizard: store country, currency, payment methods, shipping zones

### 2. Configure Payment Gateway

WooCommerce does not include a payment gateway by default. Install one:

- **Stripe** — `WooCommerce Stripe Payment Gateway` (recommended)
- **PayPal** — `WooCommerce PayPal Payments`
- **Square** — `WooCommerce Square`

Store API keys in AWS Secrets Manager and inject via environment variables — never commit them to `wp-config.php`.

### 3. Configure Redis Sessions

Install **WooCommerce Redis Session Handler** or **Redis Object Cache** plugin:

1. **Plugins** > **Add New** > search `Redis Object Cache`
2. Install and activate
3. The `REDIS_HOST` env var is pre-set — click **Enable Object Cache**

This prevents cart loss when Fargate replaces tasks.

### 4. Configure S3 Product Images

1. Install **WP Offload Media Lite**
2. Point to the S3 bucket provisioned by CloudForge (`{stackName}-media`)
3. CloudFront is already pointed at this bucket

---

## Compliance Considerations

### PCI-DSS

Using a hosted payment gateway (Stripe, PayPal) reduces PCI-DSS scope significantly — card data never touches your servers. User responsibilities:

- [ ] Use only PCI-compliant payment gateways
- [ ] Enable TLS 1.2+ only (ALB default)
- [ ] Enable WAF with OWASP ruleset
- [ ] Store no card data in the database
- [ ] Enable audit logging for order events
- [ ] Quarterly vulnerability scans

---

## Related Documentation

- [WordPress Guide](wordpress.md) — WordPress without WooCommerce
- [CMS Guides Index](README.md)
- [CMS Topology Reference](../../applications/CMS.md)
