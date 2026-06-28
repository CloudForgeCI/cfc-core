# CloudForge Applications Documentation

This directory contains documentation for CloudForge application deployments.

## Application Catalog

**[Application Catalog](README.md)** - Complete catalog of 33 built-in applications with deployment specifications, compliance requirements, and usage examples.

**[CMS Deployment Guide](CMS.md)** — WordPress, Magento, Drupal, and 16 more PHP/CMS platforms via the `cms-service` topology.

## Application Categories

### CI/CD (Continuous Integration/Continuous Deployment)
- Jenkins - Open-source automation server
- GitLab - Complete DevOps platform with Git + CI/CD
- Drone - Container-native CI platform

### Version Control Systems
- Gitea - Lightweight Git hosting

### Monitoring & Observability
- Grafana - Metrics visualization and dashboards
- Prometheus - Systems monitoring and alerting

### Databases & Caching
- PostgreSQL - Object-relational database
- Redis - In-memory data store and cache

### Secrets Management
- Vault - Secrets and encryption management

### Artifact Registry
- Nexus - Universal artifact repository
- Harbor - Container image registry

### Collaboration
- Mattermost - Team collaboration and messaging

### Analytics
- Metabase - Business intelligence and analytics
- Superset - Data exploration and visualization

### CMS / E-commerce / Forum / Wiki / LMS / Social (19 Platforms — `cms-service` topology)

| Category | Platforms |
|----------|-----------|
| Content Management | WordPress, WooCommerce, Drupal, Joomla, TYPO3, Concrete CMS, October CMS |
| E-commerce | Magento 2, PrestaShop, OpenCart, Sylius, Bagisto |
| Forum | phpBB, Flarum, MyBB |
| CRM | SuiteCRM |
| Wiki | MediaWiki |
| LMS | Moodle |
| Social | UNA / Dolphin |

## Authentication & OIDC

**[OIDC Integration Guide](OIDC.md)** - Application-level OIDC authentication integration for Grafana, GitLab, and Jenkins with AWS Cognito and IAM Identity Center.

## Compliance Requirements

**[Application Compliance](COMPLIANCE.md)** - Detailed compliance requirements and controls for each application across SOC2, HIPAA, PCI-DSS, GDPR, and FERPA frameworks.

## Related Documentation

- **[Plugin System](../plugins/README.md)** - Build custom application plugins
- **[CMS Deployment Guide](CMS.md)** - Deploy WordPress, Magento, Drupal and 16 more CMS platforms
- **[Compliance Documentation](../../compliance/README.md)** - Compliance frameworks and controls
- **[Setup Guides](../setup/)** - Authentication and infrastructure setup guides
