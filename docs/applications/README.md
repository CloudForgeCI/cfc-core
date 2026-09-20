# Application Catalog

Each deployable application is an `ApplicationSpec` implementation discovered through Java `ServiceLoader`. Set
`applicationId` in the deployment context to one of the IDs below.

- General applications use `@ApplicationPlugin` and live in `cloudforge-api/src/main/java/com/cloudforgeci/api/application/`.
- PHP content platforms use `@CmsPlugin` and implement `CmsSpec`; they are listed here and described in the
  [CMS guide](CMS.md).
- `cloudforge-manager` is provided by the separate `cloudforge-manager-deployment` artifact.

An `ApplicationSpec` declares the container image, port, data paths, user and permissions, EC2 user data, optional
ports, database requirements, and OIDC integration. The framework chooses EFS or EBS storage and wires networking,
load balancing, and security from the deployment context.

## General applications

| ID | Name | Category | Port | Default image | Default Fargate size | Database |
|----|------|----------|------|---------------|----------------------|----------|
| `jenkins` | Jenkins | `cicd` | 8080 | `jenkins/jenkins:lts` | 1024 / 2048 | — |
| `gitlab` | GitLab | `cicd` | 80 | `gitlab/gitlab-ce:latest` | 2048 / 4096 | PostgreSQL 16 (required) |
| `drone` | Drone | `cicd` | 80 | `drone/drone:2` | 1024 / 2048 | — |
| `sonarqube` | SonarQube | `code-quality` | 9000 | `sonarqube:lts-community` | 2048 / 4096 | — |
| `gitea` | Gitea | `vcs` | 3000 | `gitea/gitea:latest` | 512 / 1024 | — |
| `grafana` | Grafana | `monitoring` | 3000 | `grafana/grafana:latest` | 512 / 1024 | PostgreSQL 14 (optional) |
| `prometheus` | Prometheus | `monitoring` | 9090 | `prom/prometheus:latest` | 1024 / 2048 | — |
| `postgresql` | PostgreSQL | `database` | 5432 | `postgres:15` | 1024 / 2048 | — |
| `redis` | Redis | `database` | 6379 | `redis:7-alpine` | 512 / 1024 | — |
| `vault` | Vault | `secrets` | 8200 | `hashicorp/vault:latest` | 1024 / 2048 | — |
| `nexus` | Nexus | `artifactregistry` | 8081 | `sonatype/nexus3:latest` | 2048 / 4096 | — |
| `harbor` | Harbor | `artifactregistry` | 80 | `goharbor/harbor-core:v2.9.0` | 2048 / 4096 | PostgreSQL 13 (required) |
| `mattermost-team` | Mattermost Team (Free) | `collaboration` | 8065 | `mattermost/mattermost-enterprise-edition:latest` | 1024 / 2048 | PostgreSQL 14 (required) |
| `mattermost-enterprise` | Mattermost Enterprise | `collaboration` | 8065 | `mattermost/mattermost-enterprise-edition:latest` | 1024 / 2048 | PostgreSQL 14 (required) |
| `metabase` | Metabase | `analytics` | 3000 | `metabase/metabase-enterprise:latest` | 1024 / 2048 | PostgreSQL 15 (optional) |
| `superset` | Superset | `analytics` | 8088 | `apache/superset:latest` | 1024 / 2048 | PostgreSQL 13 (required) |
| `cloudforge-manager` | CloudForge Manager | `operations` | 1958 | `cloudforgeci/cloudforge-manager:native-latest` | 512 / 1024 | MySQL 8.0 (optional) |

Fargate size is CPU units / memory MiB; override with `cpu` and `memory`. Every application above supports both
Fargate and EC2. Override the image with `containerImage`. Required databases are provisioned on RDS; optional ones
only when `provisionDatabase` is `true`. `databaseEngine`, `databaseVersion`, `databaseInstanceClass`, and
`databaseAllocatedStorageGB` override the application defaults.

### Authentication support

| ID | Supported `authMode` values | Integration |
|----|-----------------------------|-------------|
| `jenkins` | `application-oidc`, `alb-oidc`, `none` | `oic-auth` plugin configured through JCasC |
| `gitlab` | `application-oidc`, `alb-oidc`, `none` | OmniAuth OpenID Connect |
| `grafana` | `application-oidc`, `alb-oidc`, `none` | `generic_oauth` |
| `mattermost-team` | `application-oidc`, `alb-oidc`, `none` | GitLab OAuth provider settings (`MM_GITLABSETTINGS_*`); no single logout |
| `mattermost-enterprise` | `application-oidc`, `alb-oidc`, `none` | Native OpenID Connect (`MM_OPENIDSETTINGS_*`) |
| `metabase` | `application-oidc`, `alb-oidc`, `none` | SAML (`MetabaseSamlIntegration`) |
| `cloudforge-manager` | `application-oidc`, `alb-oidc`, `none` | Built-in OIDC client; `none` or `application-oidc` on LocalStack and MiniStack |
| all others | `none` | — |

See the [OIDC guide](OIDC.md) for provider configuration.

### Optional ports

Optional ports stay closed unless the matching flag is `true` in the deployment context.

| ID | Flag | Port | Purpose |
|----|------|------|---------|
| `jenkins` | `enableAgents` | 50000/tcp inbound | JNLP build agents |
| `gitlab` | `enableSsh` | 22/tcp inbound | Git over SSH |
| `gitlab` | `enableMetrics` | 9090/tcp inbound | Prometheus metrics |
| `gitea` | `enableSsh` | 2222/tcp inbound | Git over SSH |
| `nexus` | `enableDockerRegistry` | 5000–5002/tcp inbound | Docker registry connectors |
| `harbor` | `enableNotary` | 4443/tcp inbound | Notary content trust |
| `harbor` | `enableTrivy` | 8080/tcp inbound | Trivy scanner |
| `mattermost-*` | `enableSmtp` / `enableSmtps` | 587 / 465 tcp outbound | Email |
| `mattermost-enterprise` | `enableClustering` | 8074–8075/tcp inbound | Cluster gossip |
| `redis` | `enableCluster` | 16379/tcp inbound | Cluster bus |
| `redis` | `enableSentinel` | 26379/tcp inbound | Sentinel |

## CMS and PHP platforms

All use the `cms-service` topology, listen on port 80 (phpBB: 8080), and require a MySQL-compatible database by
default. See the [CMS guide](CMS.md) for images, sizing, and platform notes.

| ID | Name | Category | Supported `authMode` values |
|----|------|----------|-----------------------------|
| `wordpress` | WordPress | `cms` | `application-oidc`, `alb-oidc`, `none` |
| `drupal` | Drupal | `cms` | `application-oidc`, `alb-oidc`, `none` |
| `joomla` | Joomla | `cms` | `application-oidc`, `alb-oidc`, `none` |
| `typo3` | TYPO3 | `cms` | `alb-oidc`, `none` |
| `concrete-cms` | Concrete CMS | `cms` | `alb-oidc`, `none` |
| `october-cms` | October CMS | `cms` | `alb-oidc`, `none` |
| `woocommerce` | WooCommerce | `ecommerce` | `application-oidc`, `alb-oidc`, `none` |
| `magento` | Magento 2 / Adobe Commerce | `ecommerce` | `application-oidc`, `alb-oidc`, `none` |
| `prestashop` | PrestaShop | `ecommerce` | `application-oidc`, `alb-oidc`, `none` |
| `opencart` | OpenCart | `ecommerce` | `alb-oidc`, `none` |
| `sylius` | Sylius | `ecommerce` | `alb-oidc`, `none` |
| `bagisto` | Bagisto | `ecommerce` | `alb-oidc`, `none` |
| `phpbb` | phpBB | `forum` | `alb-oidc`, `none` |
| `flarum` | Flarum | `forum` | `alb-oidc`, `none` |
| `mybb` | MyBB | `forum` | `alb-oidc`, `none` |
| `suitecrm` | SuiteCRM | `crm` | `alb-oidc`, `none` |
| `mediawiki` | MediaWiki | `wiki` | `alb-oidc`, `none` |
| `moodle` | Moodle | `lms` | `application-oidc`, `alb-oidc`, `none` |
| `dolphin-una` | UNA (Dolphin) | `social` | `alb-oidc`, `none` |

## Discovering applications in code

```java
Optional<ApplicationSpec> jenkins = ApplicationLoader.findById("jenkins");
List<ApplicationSpec> cicd = ApplicationLoader.discoverByCategory("cicd");
List<ApplicationSpec> oidc = ApplicationLoader.discoverOidcEnabled();

ApplicationSpec spec = jenkins.orElseThrow();
spec.defaultContainerImage(); // "jenkins/jenkins:lts"
spec.applicationPort();       // 8080
spec.getSupportedAuthModes(); // [application-oidc, alb-oidc, none]
```

`ApplicationLoader` and `CmsLoader` are in `cloudforge-api/src/main/java/com/cloudforgeci/api/compute/`.

## Adding an application

Implement `ApplicationSpec` (or `CmsSpec` for PHP platforms), annotate it with `@ApplicationPlugin` or
`@CmsPlugin`, and register the class in `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`. See the
[plugin system](../plugins/PLUGIN-SYSTEM.md) and [application plugin guide](../plugins/APPLICATION-PLUGIN-GUIDE.md).
`cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java` is a working external
plugin.

## Related documentation

- [Application compliance considerations](COMPLIANCE.md)
- [OIDC authentication](OIDC.md)
- [CMS guide](CMS.md)
- [Per-application guides](../guides/applications/README.md)
- [Deployment context examples](../examples/README.md)
- Interfaces: [`ApplicationSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/ApplicationSpec.java),
  [`DatabaseSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/DatabaseSpec.java),
  [`CmsSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/CmsSpec.java),
  [`OidcIntegration`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/OidcIntegration.java)
