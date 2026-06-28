# CloudForge CMS Guides

Guides for deploying PHP CMS applications with CloudForge using the `cms-service` topology.

## What `cms-service` Provides

Unlike `application-service`, the `cms-service` topology reads each CMS plugin's declared capabilities and automatically provisions:

| Infrastructure | Condition |
|----------------|-----------|
| **S3 media bucket** | When `CmsSpec.supportsS3MediaStorage() == true` |
| **ElastiCache Redis** | When `CmsSpec.supportsObjectCache() == true` |
| **CloudFront CDN** | When `CmsSpec.supportsCdnIntegration() == true` |
| **Route53 DNS** | When a hosted zone and custom domain are provided |
| **EFS persistent storage** | Always (for uploads, themes, plugins) |
| **ECS auto-scaling** | When `minInstanceCapacity` / `maxInstanceCapacity` differ |

Authentication is handled at the ALB level via Cognito (`authMode: "alb-oidc"`). No application-level auth configuration is required.

---

## Available CMS Applications

### Content Management

| Application | ID | PHP | Status | Guide |
|-------------|-----|-----|--------|-------|
| **WordPress** | `wordpress` | 8.2 | Verified | [WordPress Guide](wordpress.md) |
| **Drupal** | `drupal` | 8.2 | Available | [Drupal Guide](drupal.md) |
| **Joomla** | `joomla` | 8.2 | Available | — |
| **TYPO3** | `typo3` | 8.2 | Available | — |
| **Concrete CMS** | `concrete-cms` | 8.2 | Available | — |
| **October CMS** | `october-cms` | 8.2 | Available | — |

### E-Commerce

| Application | ID | PHP | Status | Guide |
|-------------|-----|-----|--------|-------|
| **WooCommerce** | `woocommerce` | 8.2 | Available | [WooCommerce Guide](woocommerce.md) |
| **Magento** | `magento` | 8.2 | Available | [Magento Guide](magento.md) |
| **PrestaShop** | `prestashop` | 8.1 | Available | [PrestaShop Guide](prestashop.md) |
| **OpenCart** | `opencart` | 8.2 | Available | — |
| **Sylius** | `sylius` | 8.2 | Available | — |
| **Bagisto** | `bagisto` | 8.2 | Available | — |

### Forums & Community

| Application | ID | PHP | Status | Guide |
|-------------|-----|-----|--------|-------|
| **phpBB** | `phpbb` | 8.2 | Available | — |
| **Flarum** | `flarum` | 8.2 | Available | — |
| **MyBB** | `mybb` | 8.2 | Available | — |
| **Dolphin/UNA** | `dolphin-una` | 8.2 | Available | — |

### Business & Education

| Application | ID | PHP | Status | Guide |
|-------------|-----|-----|--------|-------|
| **SuiteCRM** | `suitecrm` | 8.2 | Available | — |
| **MediaWiki** | `mediawiki` | 8.2 | Available | — |
| **Moodle** | `moodle` | 8.2 | Available | [Moodle Guide](moodle.md) |

**Status Legend:**
- **Verified**: Tested end-to-end including local Docker environment
- **Available**: Implementation complete, awaiting verification

---

## Minimal Deployment Example

All CMS apps follow the same pattern — only `application` changes:

```json
{
  "stackName": "WordPress-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "application": "wordpress",

  "domain": "example.com",
  "subdomain": "blog",
  "enableSsl": true,

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "myapp-auth"
}
```

Switch to a different CMS by changing `"application": "drupal"`, `"application": "magento"`, etc.

---

## Authentication

All CMS applications use ALB-level authentication. Cognito handles login before requests reach the container — no plugin or module configuration inside the CMS is required.

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | **Recommended** | Cognito at the ALB — zero config inside the app |
| `none` | Dev only | No authentication |

---

## Database

All CMS applications require a relational database. Provision one alongside the CMS:

| CMS | Default Engine | Notes |
|-----|---------------|-------|
| WordPress, WooCommerce | MySQL 8.0 | Most plugins assume MySQL |
| Drupal | PostgreSQL 16 or MySQL 8.0 | Native PostgreSQL support |
| Magento | MySQL 8.0 | Requires MySQL; PostgreSQL not supported |
| Moodle | PostgreSQL 16 or MySQL 8.0 | Both fully supported |
| PrestaShop | MySQL 8.0 | MySQL only |

```json
{
  "provisionDatabase": true,
  "databaseEngine": "mysql",
  "databaseVersion": "8.0",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50
}
```

---

## Related Documentation

- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)
- [Plugin System](../../plugins/PLUGIN-SYSTEM.md)
- [Application Plugin Guide](../../plugins/APPLICATION-PLUGIN-GUIDE.md)
