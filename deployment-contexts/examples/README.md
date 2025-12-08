# Deployment Context Examples

Ready-to-use deployment context configurations for CloudForge applications. Copy any of these files and customize for your environment.

## Quick Start

```bash
# Copy an example
cp deployment-contexts/examples/jenkins-dev.json deployment-context.json

# Edit required fields
vim deployment-context.json

# Deploy
cdk deploy -c cfc=@deployment-context.json
```

## Required Customizations

Before deploying, update these fields in any example:

| Field | Description | Example |
|-------|-------------|---------|
| `stackName` | Unique CloudFormation stack name | `MyCompany-Jenkins-Prod` |
| `domain` | Your domain (production) | `example.com` |
| `subdomain` | Service subdomain | `jenkins` |
| `cognitoDomainPrefix` | Globally unique Cognito prefix | `mycompany-jenkins-prod` |
| `region` | AWS region | `us-east-1` |

## Examples by Application

### Jenkins
| File | Environment | Features |
|------|-------------|----------|
| [jenkins-dev.json](jenkins-dev.json) | Development | Minimal, no auth |
| [jenkins-dev-auth.json](jenkins-dev-auth.json) | Development | With Cognito OIDC |
| [jenkins-production.json](jenkins-production.json) | Production | SOC2, HA, build agents |

### Mattermost
| File | Environment | Features |
|------|-------------|----------|
| [mattermost-dev.json](mattermost-dev.json) | Development | Minimal, no database |
| [mattermost-production.json](mattermost-production.json) | Production | SOC2, RDS PostgreSQL |

### Metabase
| File | Environment | Features |
|------|-------------|----------|
| [metabase-dev.json](metabase-dev.json) | Development | Embedded H2 database |
| [metabase-production.json](metabase-production.json) | Production | SOC2, RDS PostgreSQL |

### GitLab
| File | Environment | Features |
|------|-------------|----------|
| [gitlab-production.json](gitlab-production.json) | Production | SSH, Registry, Metrics |

### Grafana
| File | Environment | Features |
|------|-------------|----------|
| [grafana-production.json](grafana-production.json) | Production | RDS PostgreSQL, HA |

### Harbor
| File | Environment | Features |
|------|-------------|----------|
| [harbor-production.json](harbor-production.json) | Production | Trivy, Notary |

### SonarQube
| File | Environment | Features |
|------|-------------|----------|
| [sonarqube-production.json](sonarqube-production.json) | Production | ALB-OIDC |

## Examples by Compliance Framework

### SOC2
| File | Description |
|------|-------------|
| [compliance-soc2-staging.json](compliance-soc2-staging.json) | SOC2 staging template |
| [compliance-soc2-production.json](compliance-soc2-production.json) | SOC2 production template |

### HIPAA
| File | Description |
|------|-------------|
| [compliance-hipaa-production.json](compliance-hipaa-production.json) | HIPAA + SOC2 template |

### PCI-DSS
| File | Description |
|------|-------------|
| [compliance-pci-dss-production.json](compliance-pci-dss-production.json) | PCI-DSS + SOC2 template |

## File Naming Convention

```
{application}-{environment}.json
compliance-{framework}-{environment}.json
```

## Related Documentation

- [Deployment Context Reference](../README.md)
- [Application Guides](../../docs/guides/applications/)
- [Compliance Guide](../../docs/compliance/README.md)
