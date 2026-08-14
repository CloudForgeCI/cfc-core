# CMS & E-commerce Deployment Guide

CloudForge 3.1.0 introduces first-class support for PHP-based content management systems, e-commerce platforms, forums, wikis, LMS, and social networking applications — **19 built-in platforms** plus a public plugin example ([cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)) — all deployable with a single topology type: `cms-service`.

**Local development:** 7 of the 19 built-in platforms have Docker containers in `docker-compose.yml` and have been verified running locally. Since Fargate is also a Docker runtime, local verification directly validates the container behavior for AWS deployments — CloudForge handles all environment-specific wiring (RDS endpoint, ElastiCache, EFS) via the `ApplicationSpec`.

---

## Quick Start

```json
{
  "cfc": {
    "topology":    "cms-service",
    "applicationId": "wordpress",
    "runtime":     "fargate",
    "env":         "prod",
    "domain":      "example.com",
    "subdomain":   "blog",
    "enableSsl":   true,
    "authMode":    "alb-oidc"
  }
}
```

That single config block provisions:
- **ECS Fargate** container running the official WordPress PHP-FPM image
- **ALB** with HTTPS listener and health checks
- **RDS MySQL** (from `DatabaseSpec` requirements)
- **S3** media bucket (WordPress `supportsS3MediaStorage() = true`)
- **ElastiCache Redis** (WordPress `supportsObjectCache() = true`)
- **CloudFront CDN** with separate behaviors for media, static assets, and the wp-admin bypass
- **Route53** A + AAAA alias records
- All correct Redis/DB env vars injected into the container automatically

---

## Available Platforms

### CMS — Content Management

| Application | ID | PHP | OIDC | S3 Media | Redis | Multisite | Local Docker | Source |
|-------------|-----|-----|------|---------|-------|-----------|--------------|--------|
| WordPress | `wordpress` | 8.2 | ✅ | ✅ | ✅ | ✅ | ✅ :8087 | built-in |
| Drupal | `drupal` | 8.2 | ✅ | ✅ | ✅ | ✅ | ✅ :8090 | built-in |
| Joomla | `joomla` | 8.2 | ✅ | ✅ | ✅ | ✅ | ✅ :8091 | built-in |
| TYPO3 | `typo3` | 8.2 | — | — | ✅ | — | — | built-in |
| Concrete CMS | `concrete-cms` | 8.2 | — | — | — | — | — | built-in |
| October CMS | `october-cms` | 8.2 | — | — | — | — | — | built-in |
| **Craft CMS** | `craft-cms` | 8.2 | ✅ (ALB) | ✅ | ✅ | — | — | [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample) |

### E-commerce

| Application | ID | PHP | OIDC | S3 Media | Redis | Local Docker | Notes |
|-------------|-----|-----|------|---------|-------|--------------|-------|
| WooCommerce | `woocommerce` | 8.2 | ✅ | ✅ | ✅ | ✅ :8089 | Extends WordPress |
| Magento 2 | `magento` | 8.2 | ✅ | ✅ | ✅ | ✅ :8093 | 3 Redis DBs; requires OpenSearch |
| PrestaShop | `prestashop` | 8.1 | ✅ | ✅ | ✅ | — | — |
| OpenCart | `opencart` | 8.2 | — | — | — | ✅ :8094 | — |
| Sylius | `sylius` | 8.2 | — | — | — | — | — |
| Bagisto | `bagisto` | 8.2 | — | — | — | — | — |

### Forum / Community

| Application | ID | PHP | Local Docker | Notes |
|-------------|-----|-----|--------------|-------|
| phpBB | `phpbb` | 8.2 | — | Classic bulletin board |
| Flarum | `flarum` | 8.2 | — | Modern discussion platform |
| MyBB | `mybb` | 8.2 | — | Free bulletin board |

### CRM

| Application | ID | PHP | Local Docker | Notes |
|-------------|-----|-----|--------------|-------|
| SuiteCRM | `suitecrm` | 8.2 | — | Open-source CRM |

### Wiki

| Application | ID | PHP | Local Docker | Notes |
|-------------|-----|-----|--------------|-------|
| MediaWiki | `mediawiki` | 8.2 | — | Powers Wikipedia |

### LMS — Learning Management

| Application | ID | PHP | Local Docker | Notes |
|-------------|-----|-----|--------------|-------|
| Moodle | `moodle` | 8.2 | — | Most popular open-source LMS |

### Social Networking

| Application | ID | PHP | OIDC | Local Docker | Notes |
|-------------|-----|-----|------|--------------|-------|
| UNA (Dolphin) | `dolphin-una` | 8.2 | ✅ (ALB) | ✅ :8092 | Social platform framework |

---

## How It Works

### The `cms-service` Topology

`CmsServiceTopologyConfiguration` reads each platform's declared capabilities via the `CmsSpec` interface and conditionally provisions infrastructure — no hardcoded platform names anywhere in the wiring:

```
CmsSpec.supportsS3MediaStorage()  → creates S3 media bucket
CmsSpec.supportsCdnIntegration()  → creates CloudFront distribution (deferred until ALB is ready)
CmsSpec.supportsObjectCache()     → creates ElastiCache Redis cluster
CmsSpec.hasScheduledTasks()       → registers system cron commands
```

### Environment Variables

Each CMS declares its own connection variable names via `redisEnvVars()` and `databaseEnvVars()` on the spec. There are no switch statements on application IDs — a new plugin just overrides the default methods.

**WordPress example:**
```
REDIS_HOST=…  WP_REDIS_HOST=…  WP_REDIS_PORT=6379  WP_REDIS_DATABASE=0
DB_HOST=…     WORDPRESS_DB_HOST=…:3306  WORDPRESS_DB_NAME=wordpress
```

**Magento example (Redis uses 3 databases):**
```
MAGENTO_CACHE_BACKEND_REDIS_DATABASE=0
MAGENTO_PAGE_CACHE_BACKEND_REDIS_DATABASE=1
MAGENTO_SESSION_BACKEND_REDIS_DATABASE=2
```

### CDN Path Routing

Each CMS declares three path groups; `CmsCdnConfiguration` maps them to CloudFront behaviors automatically:

| Method | Behavior | Cache |
|--------|---------|-------|
| `cdnMediaPaths()` | S3 origin | 7-day TTL |
| `cdnStaticPaths()` | ALB origin | 1-day TTL |
| `cdnAdminPaths()` | ALB origin, all headers forwarded | **Disabled** |

---

## Platform-Specific Notes

### WordPress / WooCommerce

**Resources required:** MySQL 8.0, Redis 7, S3 bucket, CloudFront  
**System cron:** WordPress WP-Cron disabled; system cron fires every 15 min via `curl`  
**CLI tool:** WP-CLI installed automatically on EC2  
**OIDC method:** OpenID Connect Generic plugin  
**Multisite:** Supported (subdirectory mode by default)  

```json
{
  "cfc": {
    "topology":    "cms-service",
    "applicationId": "wordpress",
    "runtime":     "fargate",
    "domain":      "myblog.com",
    "enableSsl":   true,
    "authMode":    "alb-oidc"
  }
}
```

### Magento 2

**Resources required:** MySQL 8.0, Redis 7 (3 databases), S3 bucket, CloudFront  
**Instance type:** `t3.xlarge` default (4 vCPU / 8 GB); production requires `m5.xlarge`+  
**OIDC method:** miniOrange OIDC module  
**Cron groups:** `default`, `index`, `consumers`, `ddg_automation` — all registered as system cron  

```json
{
  "cfc": {
    "topology":      "cms-service",
    "applicationId": "magento",
    "runtime":       "fargate",
    "cpu":           4096,
    "memory":        8192,
    "securityProfile": "production",
    "complianceFrameworks": "PCI-DSS,SOC2"
  }
}
```

> ⚠️ **PCI-DSS note:** WooCommerce and Magento process or touch payment data. Enable `complianceFrameworks: "PCI-DSS"` and review the [PCI-DSS Compliance Guide](../compliance/PCI_DSS_COMPLIANCE.md).

### Drupal

**Resources required:** MySQL 8.0 or PostgreSQL 14, Redis 7, S3 bucket (via s3fs module)  
**OIDC method:** Native OpenID Connect module  
**CLI tool:** Drush installed automatically on EC2  

### Joomla

**Resources required:** MySQL 8.0, Redis 7  
**OIDC method:** miniOrange OAuth plugin  
**Admin bypass path:** `/administrator/*` routes through CloudFront with caching disabled  

### Craft CMS *(cloudforge-sample plugin)*

**Source:** [`CraftCmsApplicationSpec`](https://github.com/CloudForgeCI/cloudforge-sample) — demonstrates the `CmsSpec` + `DatabaseSpec` plugin pattern for external plugin authors.

**Resources required:** MySQL 8.0 or PostgreSQL 14, Redis 7 (native since Craft 4), S3 (`craftcms/aws-s3`), CloudFront  
**OIDC method:** `verbb/auth` plugin (ALB-OIDC mode; application-level OIDC via verbb/auth is optional)  
**CLI tool:** `php craft`  
**Queue runner:** System cron every minute — `php craft queue/run` (preferred over Craft's internal runner for production)

> **Critical:** Craft's public web root is the `web/` **subdirectory** (`/var/www/html/web`), not the application root (`/var/www/html`). Nginx must point at the subdirectory or requests will fail. The `documentRoot()` and `containerDataPath()` methods return different values precisely for this reason.

**Protected paths:** `/admin/*` only — Craft's public front-end is unauthenticated by design.

```json
{
  "cfc": {
    "topology":    "cms-service",
    "applicationId": "craft-cms",
    "runtime":     "fargate",
    "domain":      "example.com",
    "subdomain":   "site",
    "enableSsl":   true,
    "authMode":    "alb-oidc"
  }
}
```

**Environment variables injected automatically:**

| Variable | Description |
|----------|-------------|
| `CRAFT_DB_DRIVER` | `mysql` or `pgsql` |
| `CRAFT_DB_SERVER` | RDS endpoint |
| `CRAFT_DB_PORT` / `CRAFT_DB_DATABASE` / `CRAFT_DB_USER` | Database connection |
| `CRAFT_REDIS_HOSTNAME` / `CRAFT_REDIS_PORT` / `CRAFT_REDIS_DATABASE` | Redis cache |
| `CRAFT_ENVIRONMENT` | `production` (SSL) or `staging` |
| `CRAFT_SECURITY_KEY` | Injected from Secrets Manager at deploy time |
| `PRIMARY_SITE_URL` | Full HTTPS/HTTP URL including protocol |

---

## Resource Sizing by Platform

| Category | Default CPU | Default Memory | EC2 Instance |
|----------|------------|----------------|--------------|
| Simple CMS (WordPress, Joomla) | 1024 (1 vCPU) | 2048 MB | `t3.small` |
| E-commerce (WooCommerce, PrestaShop) | 2048 (2 vCPU) | 4096 MB | `t3.medium` |
| Enterprise (Magento) | 4096 (4 vCPU) | 8192 MB | `t3.xlarge` |
| Forum / Wiki / LMS | 1024 (1 vCPU) | 2048 MB | `t3.small` |

Defaults are declared on the `@CmsPlugin` annotation and can be overridden in `cdk.json`:

```json
{
  "cfc": {
    "cpu": 2048,
    "memory": 4096
  }
}
```

---

## Writing a Custom CMS Plugin

The [Craft CMS plugin in cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample) is a complete, production-quality reference implementation. It demonstrates: non-root document root, queue-based cron, Craft-native env var naming, and ALB-OIDC path protection.

If you have a PHP application not listed above, create a plugin in minutes:

```java
@CmsPlugin(
    value = "my-cms",
    category = "cms",
    displayName = "My CMS",
    description = "A custom CMS platform",
    phpVersion = "8.2",
    defaultCpu = 1024,
    defaultMemory = 2048,
    supportsOidc = true,
    requiresDatabase = true,
    supportsS3Media = true,
    supportsObjectCache = true,
    defaultImage = "my-org/my-cms:latest"
)
public class MyCmsApplicationSpec implements CmsSpec, DatabaseSpec {

    @Override public String applicationId()         { return "my-cms"; }
    @Override public String defaultContainerImage() { return "my-org/my-cms:latest"; }
    @Override public int    applicationPort()       { return 80; }
    @Override public String containerDataPath()     { return "/var/www/html"; }
    @Override public String efsDataPath()           { return "/my-cms"; }
    @Override public String volumeName()            { return "myCmsData"; }
    @Override public String containerUser()         { return "33:33"; }
    @Override public String efsPermissions()        { return "755"; }
    @Override public String mediaUploadPath()       { return "/var/www/html/uploads"; }
    @Override public String phpVersion()            { return "8.2"; }
    @Override public List<String> requiredPhpExtensions() {
        return List.of("mysqli", "pdo_mysql", "gd", "curl", "mbstring", "zip");
    }

    // CDN path routing — declare what goes where
    @Override public List<String> cdnMediaPaths()  { return List.of("/uploads/*"); }
    @Override public List<String> cdnStaticPaths() { return List.of("/assets/*", "/themes/*"); }
    @Override public List<String> cdnAdminPaths()  { return List.of("/admin/*"); }

    // CMS-specific Redis env vars (merged with generic REDIS_HOST/REDIS_PORT)
    @Override
    public Map<String, String> redisEnvVars(String host, int port) {
        Map<String, String> env = new HashMap<>();
        env.put("REDIS_HOST", host);
        env.put("REDIS_PORT", String.valueOf(port));
        env.put("MY_CMS_REDIS_HOST", host);
        env.put("MY_CMS_REDIS_PORT", String.valueOf(port));
        return env;
    }

    // CMS-specific DB env vars
    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", host); env.put("DB_PORT", String.valueOf(port));
        env.put("DB_NAME", name); env.put("DB_USER", user);
        env.put("MY_CMS_DB_HOST", host);
        env.put("MY_CMS_DB_NAME", name);
        return env;
    }

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.required("mysql", "8.0")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("my_cms");
    }
}
```

Then register in `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`:
```
com.example.MyCmsApplicationSpec
```

Deploy with:
```json
{ "cfc": { "topology": "cms-service", "applicationId": "my-cms" } }
```

---

## Discovery API

`CmsLoader` provides programmatic access to all registered CMS plugins:

```java
// All platforms
Map<String, CmsSpec> all = CmsLoader.discover();

// By category
List<CmsSpec> ecommerce = CmsLoader.discoverEcommerce();
List<CmsSpec> forums    = CmsLoader.discoverForums();

// Feature-filtered
List<CmsSpec> oidcReady = CmsLoader.discoverOidcEnabled();
List<CmsSpec> s3Ready   = CmsLoader.discoverS3MediaSupported();

// Lookup by ID
Optional<CmsSpec> wp = CmsLoader.findById("wordpress");

// Print catalog
System.out.println(CmsLoader.printCatalog());
```

---

## Compliance Considerations

| Platform Category | Frameworks | Notes |
|-------------------|-----------|-------|
| E-commerce (Magento, WooCommerce) | **PCI-DSS required**, SOC2 | Store/process payment data |
| LMS (Moodle) | **FERPA**, GDPR | Student records |
| CRM (SuiteCRM) | GDPR, SOC2 | Customer PII |
| CMS (WordPress, Drupal) | SOC2, GDPR | User content and PII |
| Forum / Social | GDPR, (FERPA if educational) | User posts, PII |

Enable compliance frameworks in `cdk.json`:
```json
{
  "cfc": {
    "complianceFrameworks": "PCI-DSS,SOC2",
    "complianceMode":       "enforce",
    "securityProfile":      "production"
  }
}
```

---

## Local Development

Seven platforms have verified Docker containers in `docker-compose.yml`. Start them with:

```bash
./scripts/docker-start.sh infrastructure cms
```

| Container | Port | Status |
|-----------|------|--------|
| WordPress | http://localhost:8087 | ✅ Verified |
| WooCommerce | http://localhost:8089 | ✅ Verified |
| Drupal | http://localhost:8090 | ✅ Verified |
| Joomla | http://localhost:8091 | ✅ Verified |
| UNA (Dolphin) | http://localhost:8092 | ✅ Verified |
| Magento 2 | http://localhost:8093 | ✅ Verified |
| OpenCart | http://localhost:8094 | ✅ Verified |

Local containers connect to shared MySQL (port 3306), PostgreSQL (port 5432), and Redis (port 6379) also in `docker-compose.yml`. These mirror the RDS/ElastiCache resources CloudForge provisions on AWS.

> Craft CMS (`craft-cms`) deploys to AWS via the cloudforge-sample plugin but has no local Docker container — test it by deploying to a dev Fargate environment.

---

## Related Documentation

- [Plugin Ecosystem](../plugins/PLUGIN-ECOSYSTEM.md) — CmsPlugin annotation reference
- [Application Plugin Guide](../plugins/APPLICATION-PLUGIN-GUIDE.md) — Build custom CMS plugins
- [OIDC Integration Guide](OIDC.md) — CMS OIDC authentication
- [PCI-DSS Compliance](../compliance/PCI_DSS_COMPLIANCE.md) — For e-commerce platforms
- [Quick Start Guide](../compliance/QUICK_START_GUIDE.md) — Deployment reference

---

*CloudForge 3.1.0 — CMS/E-commerce Platform Support*
