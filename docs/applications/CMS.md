# CMS and PHP Platforms

CloudForge deploys PHP content management, e-commerce, forum, CRM, wiki, LMS, and social platforms with the
`cms-service` topology. Each platform is a `CmsSpec` implementation annotated with `@CmsPlugin` in
`cloudforge-api/src/main/java/com/cloudforgeci/api/application/cms/`.

## Quick start

```json
{
  "cfc": {
    "topology": "cms-service",
    "applicationId": "wordpress",
    "runtime": "fargate",
    "env": "prod",
    "domain": "example.com",
    "subdomain": "blog",
    "enableSsl": true,
    "authMode": "alb-oidc"
  }
}
```

The `cms-service` topology (`CmsServiceTopologyConfiguration`) reads the platform's `CmsSpec` capabilities and
provisions, in addition to the usual container service and load balancer:

| Capability | Provisions |
|------------|------------|
| `supportsS3MediaStorage()` | S3 media bucket |
| `supportsCdnIntegration()` (with S3 media) | CloudFront distribution and DNS records, created once the load balancer exists |
| `supportsObjectCache()` with `preferredCacheBackend() == "redis"` | ElastiCache Redis cluster |
| `databaseRequirement()` | RDS instance |
| `hasScheduledTasks()` / `cronCommands()` | System cron entries |

Connection settings reach the container through each spec's `databaseEnvVars()` and `redisEnvVars()`, so a new
platform only overrides those methods.

## Platforms

Port is 80 for every platform except phpBB (8080). Size is Fargate CPU units / memory MiB, followed by the default
EC2 instance type.

| ID | Name | Category | PHP | Default image | Size |
|----|------|----------|-----|---------------|------|
| `wordpress` | WordPress | `cms` | 8.2 | `wordpress:php8.2-apache` | 1024 / 2048, `t3.small` |
| `drupal` | Drupal | `cms` | 8.2 | `drupal:10-php8.2-apache` | 1024 / 2048, `t3.small` |
| `joomla` | Joomla | `cms` | 8.2 | `joomla:5-php8.2-apache` | 1024 / 2048, `t3.small` |
| `typo3` | TYPO3 | `cms` | 8.2 | `martinhelmich/typo3:12` | 2048 / 4096, `t3.medium` |
| `concrete-cms` | Concrete CMS | `cms` | 8.2 | `php:8.2-apache` | 1024 / 2048, `t3.small` |
| `october-cms` | October CMS | `cms` | 8.2 | `php:8.2-apache` | 1024 / 2048, `t3.small` |
| `woocommerce` | WooCommerce | `ecommerce` | 8.2 | `wordpress:php8.2-apache` | 2048 / 4096, `t3.medium` |
| `magento` | Magento 2 / Adobe Commerce | `ecommerce` | 8.2 | `magento/magento-cloud-docker-php:8.2-fpm` | 4096 / 8192, `t3.xlarge` |
| `prestashop` | PrestaShop | `ecommerce` | 8.1 | `prestashop/prestashop:8-8.1-apache` | 2048 / 4096, `t3.medium` |
| `opencart` | OpenCart | `ecommerce` | 8.1 | `vimagick/opencart:latest` | 1024 / 2048, `t3.small` |
| `sylius` | Sylius | `ecommerce` | 8.3 | `sylius/standard:latest` | 2048 / 4096, `t3.medium` |
| `bagisto` | Bagisto | `ecommerce` | 8.2 | `php:8.2-apache` | 2048 / 4096, `t3.medium` |
| `phpbb` | phpBB | `forum` | 8.2 | `php:8.2-apache` | 1024 / 2048, `t3.small` |
| `flarum` | Flarum | `forum` | 8.2 | `php:8.2-apache` | 1024 / 2048, `t3.small` |
| `mybb` | MyBB | `forum` | 8.2 | `php:8.2-apache` | 1024 / 2048, `t3.small` |
| `suitecrm` | SuiteCRM | `crm` | 8.2 | `bitnami/suitecrm:8` | 2048 / 4096, `t3.medium` |
| `mediawiki` | MediaWiki | `wiki` | 8.2 | `mediawiki:1.42` | 1024 / 2048, `t3.small` |
| `moodle` | Moodle | `lms` | 8.2 | `moodlehq/moodle-php-apache:8.2` | 2048 / 4096, `t3.medium` |
| `dolphin-una` | UNA (Dolphin) | `social` | 8.2 | `php:8.2-apache` | 2048 / 4096, `t3.medium` |

Override the image with `containerImage` and the size with `cpu`, `memory`, or `instanceType`.

### Capabilities, databases, and authentication

Every platform supports the Redis object cache. The default database engine is MySQL (8.0, or 5.7 where noted);
`databaseEngine` can select another supported engine.

| ID | S3 media | Multisite | Databases | `authMode` values | OIDC method |
|----|----------|-----------|-----------|-------------------|-------------|
| `wordpress` | yes | yes | mysql, mariadb | `application-oidc`, `alb-oidc`, `none` | OpenID Connect Generic plugin |
| `drupal` | yes | yes | mysql, mariadb, postgresql | `application-oidc`, `alb-oidc`, `none` | OpenID Connect module |
| `joomla` | yes | no | mysql, mariadb, postgresql | `application-oidc`, `alb-oidc`, `none` | miniOrange OIDC plugin |
| `typo3` | yes | yes | mysql, mariadb, postgresql, sqlite | `alb-oidc`, `none` | — |
| `concrete-cms` | yes | yes | mysql (5.7), mariadb | `alb-oidc`, `none` | OAuth add-on / ALB OIDC |
| `october-cms` | yes | yes | mysql (5.7), mariadb, postgresql, sqlite | `alb-oidc`, `none` | Laravel Socialite / OAuth plugin |
| `woocommerce` | yes | no | mysql, mariadb | `application-oidc`, `alb-oidc`, `none` | OpenID Connect Generic plugin |
| `magento` | yes | yes | mysql, mariadb | `application-oidc`, `alb-oidc`, `none` | miniOrange OIDC module |
| `prestashop` | yes | yes | mysql, mariadb | `application-oidc`, `alb-oidc`, `none` | OAuth admin API module |
| `opencart` | yes | yes | mysql, mariadb | `alb-oidc`, `none` | OAuth extension / ALB OIDC |
| `sylius` | yes | yes | mysql, mariadb, postgresql | `alb-oidc`, `none` | Symfony Security / OAuth2 |
| `bagisto` | yes | yes | mysql, mariadb | `alb-oidc`, `none` | Laravel Socialite / Passport |
| `phpbb` | no | no | mysql (5.7), mariadb, postgresql, sqlite | `alb-oidc`, `none` | OAuth extension / ALB OIDC |
| `flarum` | yes | no | mysql (5.7), mariadb | `alb-oidc`, `none` | FoF OAuth extension / ALB OIDC |
| `mybb` | no | no | mysql (5.7), mariadb, postgresql, sqlite | `alb-oidc`, `none` | OAuth plugin / ALB OIDC |
| `suitecrm` | no | no | mysql, mariadb | `alb-oidc`, `none` | OAuth2 / LDAP / ALB OIDC |
| `mediawiki` | yes | yes | mysql (5.7), mariadb, postgresql, sqlite | `alb-oidc`, `none` | OpenID Connect extension (PluggableAuth) |
| `moodle` | yes | no | mysql, mariadb, postgresql | `application-oidc`, `alb-oidc`, `none` | Microsoft OIDC plugin |
| `dolphin-una` | yes | no | mysql (5.7), mariadb | `alb-oidc`, `none` | OAuth Connect app / ALB OIDC |

With `alb-oidc`, only the paths returned by `protectedPaths()` require sign-in. For CMS platforms this defaults to
`cdnAdminPaths()` (for example `/wp-admin/*` and `/wp-login.php` for WordPress, `/administrator/*` for Joomla), so
the public site stays open.

## CDN path routing

`CmsCdnConfiguration` maps each platform's path groups to CloudFront behaviors:

| Method | Origin | Caching |
|--------|--------|---------|
| `cdnMediaPaths()` | S3 media bucket | 7-day default TTL |
| `cdnStaticPaths()` | Load balancer | 1-day default TTL |
| `cdnAdminPaths()` | Load balancer, all headers forwarded | Disabled |

## Platform notes

### WordPress and WooCommerce

- WP-Cron is disabled (`DISABLE_WP_CRON`); a system cron entry requests `wp-cron.php` every 15 minutes.
- CLI tool: WP-CLI (`wp`).
- Multisite (WordPress only) defaults to subdirectory mode.
- WooCommerce extends the WordPress spec with larger defaults.

### Magento 2

- Defaults to a `db.r6g.large` RDS instance and `t3.xlarge` EC2 instances.
- Requires OpenSearch or Elasticsearch for catalog search.
- Declares system cron entries for its cron groups (`index`, `consumers`, `ddg_automation`). `cronCommands()` is
  keyed by schedule, so the `default` group, which shares the every-minute schedule with `index`, is not
  registered.
- Uses separate Redis databases for cache, page cache, and sessions.

### Drupal

- CLI tool: Drush.
- `/admin/*`, `/user/*`, and `/update.php` bypass the CDN cache and are the `alb-oidc` protected paths.

### Joomla

- `/administrator/*` bypasses the CDN cache and is the `alb-oidc` protected path.

Platforms that store or process payment data (WooCommerce, Magento, PrestaShop, and the other `ecommerce` platforms)
should run with `complianceFrameworks` including `pci-dss`; see the
[PCI-DSS compliance guide](../compliance/PCI_DSS_COMPLIANCE.md).

## Writing a CMS plugin

`cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java` is a complete
external plugin (also published as [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)). It shows
a document root in a subdirectory (`/var/www/html/web`), a queue runner in cron, platform-specific environment
variable names, and `alb-oidc` protection of `/admin/*` only.

A minimal plugin:

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

    @Override public List<String> cdnMediaPaths()  { return List.of("/uploads/*"); }
    @Override public List<String> cdnStaticPaths() { return List.of("/assets/*", "/themes/*"); }
    @Override public List<String> cdnAdminPaths()  { return List.of("/admin/*"); }

    @Override
    public Map<String, String> databaseEnvVars(String host, int port, String name, String user) {
        return Map.of("DB_HOST", host, "DB_PORT", String.valueOf(port), "DB_NAME", name, "DB_USER", user);
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

Register it in `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`:

```
com.example.MyCmsApplicationSpec
```

and deploy with `"topology": "cms-service", "applicationId": "my-cms"`.

## Discovery API

```java
Map<String, CmsSpec> all      = CmsLoader.discover();
List<CmsSpec> ecommerce       = CmsLoader.discoverEcommerce();
List<CmsSpec> forums          = CmsLoader.discoverForums();
List<CmsSpec> oidcReady       = CmsLoader.discoverOidcEnabled();
List<CmsSpec> s3Ready         = CmsLoader.discoverS3MediaSupported();
Optional<CmsSpec> wordpress   = CmsLoader.findById("wordpress");
System.out.println(CmsLoader.printCatalog());
```

## Compliance considerations

| Platform type | Frameworks to consider | Reason |
|---------------|------------------------|--------|
| E-commerce | `pci-dss`, `soc2` | Payment data |
| CRM (SuiteCRM) | `gdpr`, `soc2` | Customer personal data |
| LMS (Moodle) | `gdpr` | Student records (FERPA also applies in the US but is not a built-in framework) |
| CMS, forum, wiki, social | `soc2`, `gdpr` | User accounts and content |

```json
{
  "cfc": {
    "securityProfile": "production",
    "complianceFrameworks": "PCI-DSS,SOC2",
    "complianceMode": "enforce"
  }
}
```

## Local development

`docker-compose.yml` defines containers for seven platforms, backed by shared MySQL, PostgreSQL, and Redis
containers:

| Service | URL |
|---------|-----|
| `wordpress` | http://localhost:8087 |
| `woocommerce` | http://localhost:8089 |
| `drupal` | http://localhost:8090 |
| `joomla` | http://localhost:8091 |
| `dolphin-una` | http://localhost:8092 |
| `magento` (with `opensearch`) | http://localhost:8093 |
| `opencart` | http://localhost:8094 |

```bash
# WordPress, WooCommerce, Drupal, and Joomla plus databases
./scripts/docker-start.sh infrastructure cms

# The others are not in a start-script group
docker compose up -d mysql opensearch dolphin-una magento opencart
```

## Related documentation

- [Application catalog](README.md)
- [Per-platform guides](../guides/cms/README.md)
- [Plugin ecosystem](../plugins/PLUGIN-ECOSYSTEM.md) — `@CmsPlugin` reference
- [Application plugin guide](../plugins/APPLICATION-PLUGIN-GUIDE.md)
- [OIDC authentication](OIDC.md)
- [PCI-DSS compliance](../compliance/PCI_DSS_COMPLIANCE.md)
