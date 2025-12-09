# CloudForge Plugin Ecosystem

## 🌟 Overview

CloudForge provides two extensible plugin systems that enable organizations to:

1. **Application Plugins** - Deploy any application on AWS with built-in compliance
2. **Compliance Framework Plugins** - Add custom compliance validators for industry standards

Both systems use **Java ServiceLoader** for automatic plugin discovery and loading.

---

## 📦 Built-in Applications (14 Applications)

CloudForge ships with 14 production-ready applications out-of-the-box:

### CI/CD (3)
- **Jenkins** - Automation server with OIDC support
- **GitLab** - Complete DevOps platform with OIDC support
- **Drone** - Container-native CI/CD

### Version Control (1)
- **Gitea** - Lightweight self-hosted Git service with OIDC support

### Monitoring (2)
- **Grafana** - Observability platform with OIDC support
- **Prometheus** - Metrics collection and alerting

### Analytics (2)
- **Metabase** - BI and analytics platform
- **Apache Superset** - Modern data exploration platform

### Databases (2)
- **PostgreSQL** - Relational database
- **Redis** - In-memory data store

### Artifact Registries (2)
- **Nexus Repository** - Universal artifact manager
- **Harbor** - Container registry

### Secrets Management (1)
- **HashiCorp Vault** - Secrets and encryption management

### Collaboration (1)
- **Mattermost** - Team collaboration platform

**All applications support:**
- ✅ Docker/ECS (Fargate) deployment
- ✅ EC2 deployment
- ✅ Automatic infrastructure (VPC, ALB, EFS, monitoring)
- ✅ Security profiles (DEV, STAGING, PRODUCTION)
- ✅ OIDC integration (where supported)

---

## 🔒 Built-in Compliance Frameworks (12 Frameworks)

### Always-Load Cross-Framework Validators (5)

These run for ALL deployments:

| Framework | Priority | Purpose |
|-----------|----------|---------|
| **KeyManagement** | -10 | KMS rotation, secrets management, certificates |
| **DatabaseSecurity** | -5 | RDS/DynamoDB security controls |
| **AdvancedMonitoring** | -5 | Security Hub, Inspector, Macie integration |
| **ThreatProtection** | 0 | Malware protection, IDS, file integrity monitoring |
| **IncidentResponse** | 0 | Disaster recovery, backup, forensics |

### Conditional Industry-Specific Frameworks (7)

These run when explicitly enabled via `complianceFrameworks`:

| Framework | Priority | Standard |
|-----------|----------|----------|
| **HIPAA** | 10 | Healthcare technical safeguards |
| **HIPAA-Organizational** | 15 | Healthcare administrative safeguards |
| **PCI-DSS** | 20 | Payment card industry security |
| **GDPR** | 30 | EU privacy regulation (technical) |
| **GDPR-Organizational** | 35 | EU privacy regulation (organizational) |
| **SOC 2** | 40 | Service organization controls |
| **ISO 27001** | 50 | Information security management |

**All frameworks provide:**
- ✅ Automated infrastructure validation
- ✅ Runtime-specific controls (Docker/ECS vs EC2)
- ✅ Security profile enforcement (PRODUCTION vs STAGING)
- ✅ Compliance reporting integration

---

## 🚀 Creating Custom Plugins

### Application Plugin Example

Deploy SonarQube as a custom application:

```java
package com.example.applications;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

public class SonarQubeApplicationSpec implements ApplicationSpec {
    @Override
    public String applicationId() {
        return "sonarqube";
    }

    @Override
    public String defaultContainerImage() {
        return "sonarqube:lts-community";
    }

    @Override
    public int applicationPort() {
        return 9000;
    }

    @Override
    public String healthCheckPath() {
        return "/api/system/health";
    }

    // ... implement other required methods
}
```

**Register:** `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`

### Compliance Plugin Example

Add NIST 800-53 compliance validation:

```java
package com.example.compliance;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;

@ComplianceFramework(
    value = "NIST-800-53",
    priority = 25,
    displayName = "NIST 800-53 Rev 5",
    description = "Federal information system security controls"
)
public class Nist80053Rules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // AC-6: Least Privilege
            rules.addAll(validateAccessControl(ctx));

            // AU-2: Event Logging
            rules.addAll(validateAuditLogging(ctx));

            return rules;
        });
    }
}
```

**Register:** `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`

---

## 📚 Documentation

- **Plugin System Overview:** [cloudforge-core/PLUGIN-SYSTEM.md](cloudforge-core/PLUGIN-SYSTEM.md)
- **Application Plugin Guide:** [cloudforge-core/APPLICATION-PLUGIN-GUIDE.md](cloudforge-core/APPLICATION-PLUGIN-GUIDE.md)
- **Compliance Plugin Guide:** [cloudforge-core/COMPLIANCE-PLUGIN-GUIDE.md](cloudforge-core/COMPLIANCE-PLUGIN-GUIDE.md)

---

## 🎯 Use Cases

### For Enterprises
- **Standardize deployments** across all teams
- **Enforce compliance** at infrastructure-as-code level
- **Distribute best practices** as reusable plugins
- **Reduce duplicated** infrastructure code

### For ISVs
- **Package your application** as a CloudForge plugin
- **Leverage battle-tested** infrastructure patterns
- **Provide turnkey** AWS deployment for customers
- **Support multiple** deployment modes (container/VM)

### For Compliance Teams
- **Codify internal policies** as validators
- **Prevent non-compliant** infrastructure from deploying
- **Generate compliance reports** automatically
- **Track control effectiveness** over time

---

## 🔧 Plugin Discovery

CloudForge discovers plugins automatically using Java ServiceLoader:

```
your-application.jar
├── META-INF/
│   └── services/
│       ├── com.cloudforge.core.interfaces.ApplicationSpec
│       └── com.cloudforge.core.interfaces.FrameworkRules
├── com/example/
│   ├── MyApplicationSpec.class
│   └── MyComplianceRules.class
```

1. Add your JAR to the classpath
2. CloudForge discovers it automatically
3. Use it like any built-in application/framework

---

## 📊 Plugin Ecosystem Stats

| Category | Built-in | Priorities | Always-Load |
|----------|----------|------------|-------------|
| **Applications** | 14 | N/A | N/A |
| **Compliance Frameworks** | 12 | -10 to 50 | 5 frameworks |

### Application Coverage
- **CI/CD:** 3 applications
- **Databases:** 2 applications
- **Monitoring:** 2 applications
- **Analytics:** 2 applications
- **Artifact Registries:** 2 applications
- **Collaboration:** 1 application
- **Secrets Management:** 1 application
- **Version Control:** 1 application

### Compliance Coverage
- **Healthcare:** HIPAA (2 frameworks)
- **Finance:** PCI-DSS (1 framework)
- **Privacy:** GDPR (2 frameworks)
- **Enterprise:** SOC 2, ISO 27001 (2 frameworks)
- **Cross-Framework:** 5 always-load frameworks

---

## 🤝 Contributing

We welcome community contributions!

1. **Report issues:** https://github.com/cloudforgeci/cfc-core/issues
2. **Submit plugins:** https://github.com/cloudforgeci/cfc-core/pulls
3. **Share examples:** https://github.com/cloudforgeci/cfc-core/tree/main/examples

---

## 🌐 Plugin Registry (Coming Soon)

We're building a central plugin registry where developers can:

- ✅ Publish application and compliance plugins
- ✅ Browse community-contributed plugins
- ✅ Review and rate plugins
- ✅ Track plugin versions and compatibility

**Stay tuned!** 🎉

---

## ✨ Quick Start

### Deploy a Built-in Application

```bash
# Create a Jenkins deployment
cdk deploy -c applicationId=jenkins -c runtimeType=FARGATE

# Create a GitLab deployment with OIDC
cdk deploy -c applicationId=gitlab -c runtimeType=EC2 -c authMode=application-oidc
```

### Enable Compliance Frameworks

```json
{
  "context": {
    "complianceFrameworks": "HIPAA,PCI-DSS",
    "securityProfile": "PRODUCTION"
  }
}
```

### Add a Custom Plugin

```bash
# Build your plugin
mvn clean package

# Add to your project
cp target/my-plugin-1.0.0.jar lib/

# Deploy (plugin discovered automatically)
cdk deploy
```

---

**Ready to extend CloudForge?** 🚀

- 📦 [Build an Application Plugin →](cloudforge-core/APPLICATION-PLUGIN-GUIDE.md)
- 🔒 [Build a Compliance Plugin →](cloudforge-core/COMPLIANCE-PLUGIN-GUIDE.md)
- 📖 [Read the Plugin System Overview →](cloudforge-core/PLUGIN-SYSTEM.md)
