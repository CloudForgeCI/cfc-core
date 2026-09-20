# Applications Documentation

| Document | Contents |
|----------|----------|
| [Application Catalog](README.md) | Every built-in `applicationId` with its image, port, default size, database, auth modes, and optional ports |
| [CMS Guide](CMS.md) | The 19 PHP platforms deployed with the `cms-service` topology, and how to write a CMS plugin |
| [OIDC Authentication](OIDC.md) | `authMode` values, Cognito and external provider settings, per-application integration |
| [Application Compliance](COMPLIANCE.md) | Framework settings and per-application controls that remain the operator's responsibility |

## Categories

| Category | Applications |
|----------|--------------|
| CI/CD | Jenkins, GitLab, Drone |
| Code quality | SonarQube |
| Version control | Gitea |
| Monitoring | Grafana, Prometheus |
| Databases | PostgreSQL, Redis |
| Secrets | Vault |
| Artifact registries | Nexus, Harbor |
| Collaboration | Mattermost Team, Mattermost Enterprise |
| Analytics | Metabase, Superset |
| Operations | CloudForge Manager (`cloudforge-manager-deployment` artifact) |
| CMS | WordPress, Drupal, Joomla, TYPO3, Concrete CMS, October CMS |
| E-commerce | WooCommerce, Magento 2, PrestaShop, OpenCart, Sylius, Bagisto |
| Forum | phpBB, Flarum, MyBB |
| CRM | SuiteCRM |
| Wiki | MediaWiki |
| LMS | Moodle |
| Social | UNA (Dolphin) |

## Related documentation

- [Plugin system](../plugins/README.md)
- [Per-application guides](../guides/applications/README.md)
- [Deployment context examples](../examples/README.md)
- [Compliance documentation](../compliance/README.md)
- Setup guides: [AWS Identity Center](../setup/AWS_IDENTITY_CENTER_SETUP.md), [Cognito MFA](../setup/COGNITO_MFA_COMPLIANCE_SETUP.md)
