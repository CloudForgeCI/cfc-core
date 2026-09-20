# Plugin Documentation

CloudForge can be extended with application plugins and compliance framework plugins, both
discovered through Java `ServiceLoader`.

- [Plugin System](PLUGIN-SYSTEM.md): plugin types, discovery, and development workflow
- [Built-in Plugins](PLUGIN-ECOSYSTEM.md): the 35 application specs and 18 compliance
  frameworks registered by `cloudforge-api`, and the sample plugins
- [Application Plugin Guide](APPLICATION-PLUGIN-GUIDE.md): implement `ApplicationSpec`,
  `CmsSpec`, and `DatabaseSpec`
- [Compliance Plugin Guide](COMPLIANCE-PLUGIN-GUIDE.md): implement `FrameworkRules`

## Built-in applications

| Category | Applications |
|----------|--------------|
| CI/CD | Jenkins, GitLab, Drone |
| Code quality | SonarQube |
| Version control | Gitea |
| Monitoring | Grafana, Prometheus |
| Analytics | Metabase, Superset |
| Databases | PostgreSQL, Redis |
| Artifact registries | Nexus, Harbor |
| Secrets | Vault |
| Collaboration | Mattermost Enterprise, Mattermost Team |
| CMS | WordPress, Drupal, Joomla, TYPO3, Concrete CMS, October CMS |
| E-commerce | WooCommerce, Magento 2, PrestaShop, OpenCart, Sylius, Bagisto |
| Forum | phpBB, Flarum, MyBB |
| CRM | SuiteCRM |
| Wiki | MediaWiki |
| LMS | Moodle |
| Social | UNA (Dolphin) |

See the [application catalog](../applications/README.md) for per-application documentation.

## Sample plugins

The [`cfc-testing`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cfc-testing) module contains working plugins:

- [`CraftCmsApplicationSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java): a `CmsSpec` + `DatabaseSpec` application plugin
- [`CustomSecurityPolicyRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/CustomSecurityPolicyRules.java) and
  [`OpenSourceSecurityPolicyRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/OpenSourceSecurityPolicyRules.java): compliance framework plugins

## Contributing

To add a plugin to this repository, follow the relevant guide and the
[contribution guidelines](../CONTRIBUTING.md#adding-an-application). To distribute a plugin
separately, publish it as a JAR and have consumers add it as a Maven dependency.

Report issues at https://github.com/CloudForgeCI/cfc-core/issues.
