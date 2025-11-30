# CloudForge Plugin System Documentation

This directory contains documentation for the CloudForge plugin system, enabling extensibility for applications and compliance frameworks.

## Plugin System Overview

- **[Plugin Ecosystem](PLUGIN-ECOSYSTEM.md)** - Overview of 14 built-in applications and the plugin architecture
- **[Plugin System Guide](PLUGIN-SYSTEM.md)** - Core architecture and development patterns

## Plugin Development Guides

### Application Plugins
- **[Application Plugin Guide](APPLICATION-PLUGIN-GUIDE.md)** - Build custom application deployment plugins

### Compliance Plugins
- **[Compliance Plugin Guide](COMPLIANCE-PLUGIN-GUIDE.md)** - Build custom compliance framework validators

## Built-in Plugins

CloudForge includes 14 pre-built application plugins across 8 categories:

- **CI/CD**: Jenkins, GitLab, Drone
- **Version Control**: Gitea
- **Monitoring**: Grafana, Prometheus
- **Databases**: PostgreSQL, Redis
- **Secrets Management**: Vault
- **Artifact Registry**: Nexus, Harbor
- **Collaboration**: Mattermost
- **Analytics**: Metabase, Superset

See the [Application Catalog](../applications/README.md) for detailed documentation on each application.

## Plugin Examples

For practical examples of plugin development, see:
- **[Plugin Examples](../../cfc-testing/PLUGIN-EXAMPLES.md)** - SonarQube application plugin and custom compliance framework examples

## Contributing

To contribute a plugin to the CloudForge ecosystem:

1. Follow the appropriate plugin guide above
2. Package your plugin as a standalone JAR
3. Submit to the [CloudForge Plugin Registry](https://github.com/CloudForgeCI/plugin-registry)

## Support

- GitHub Issues: https://github.com/CloudForgeCI/cfc-core/issues
- Documentation: https://github.com/CloudForgeCI/cfc-core/tree/main/docs
