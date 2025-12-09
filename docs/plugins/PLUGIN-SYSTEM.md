# CloudForge Plugin System

CloudForge provides two powerful plugin systems for extending functionality:

## 🚀 Plugin Types

| Plugin Type | Purpose | Interface | Examples |
|-------------|---------|-----------|----------|
| **Application Plugins** | Deploy custom applications | `ApplicationSpec` | Vault, GitLab, Grafana, Mattermost |
| **Compliance Plugins** | Add compliance frameworks | `FrameworkRules<SystemContext>` + `@ComplianceFramework` | NIST 800-53, FedRAMP, custom policies |

---

## 📦 Application Plugins

**Purpose:** Deploy any application on AWS using CloudForge infrastructure patterns.

**Key Features:**
- ✅ Automatic support for Docker/ECS (Fargate) and EC2 deployments
- ✅ Built-in VPC, ALB, EFS, and monitoring configuration
- ✅ OIDC integration for SSO
- ✅ Health check configuration
- ✅ CloudWatch logging

**Quick Example:**

```java
public class VaultApplicationSpec implements ApplicationSpec {
    @Override
    public String applicationId() {
        return "vault";
    }

    @Override
    public String defaultContainerImage() {
        return "hashicorp/vault:latest";
    }

    @Override
    public int applicationPort() {
        return 8200;
    }

    // ... more configuration
}
```

**Register:**
```
META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec
```

**📖 Full Guide:** [APPLICATION-PLUGIN-GUIDE.md](APPLICATION-PLUGIN-GUIDE.md)

---

## 🔒 Compliance Framework Plugins

**Purpose:** Add custom compliance validation for industry standards or internal policies.

**Key Features:**
- ✅ Priority-based execution order
- ✅ Always-load vs conditional frameworks
- ✅ Infrastructure vs organizational control distinction
- ✅ Support for Docker/ECS and EC2 runtime-specific validation
- ✅ Integration with compliance reporting

**Quick Example:**

```java
@ComplianceFramework(
    value = "NIST-800-53",
    priority = 25,
    displayName = "NIST 800-53 Rev 5",
    description = "Validates NIST 800-53 security controls"
)
public class Nist80053Rules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();
            rules.addAll(validateAccessControl(ctx));
            rules.addAll(validateAuditLogging(ctx));
            return rules;
        });
    }
}
```

**Register:**
```
META-INF/services/com.cloudforge.core.interfaces.FrameworkRules
```

**📖 Full Guide:** [COMPLIANCE-PLUGIN-GUIDE.md](COMPLIANCE-PLUGIN-GUIDE.md)

---

## 🏗️ Plugin Architecture

Both plugin systems use Java's **ServiceLoader** pattern for automatic discovery:

```
your-plugin.jar
├── META-INF/
│   └── services/
│       ├── com.cloudforge.core.interfaces.ApplicationSpec       (for apps)
│       └── com.cloudforge.core.interfaces.FrameworkRules        (for compliance)
├── com/example/
│   ├── VaultApplicationSpec.class
│   └── Nist80053Rules.class
```

### How It Works

1. **Discovery:** CloudForge scans classpath using ServiceLoader
2. **Registration:** Plugins register via META-INF/services files
3. **Loading:** Plugins are instantiated automatically at runtime
4. **Execution:**
   - Applications: Deployed via `ApplicationFactory`
   - Compliance: Validated via `FrameworkLoader.discover()`

---

## 🎯 Priority System (Compliance Only)

Compliance frameworks use priorities to control execution order:

| Priority | Type | Examples |
|----------|------|----------|
| **-10 to -5** | Always-Load Foundation | KeyManagement (-10), DatabaseSecurity (-5) |
| **0** | Always-Load General | ThreatProtection (0), IncidentResponse (0) |
| **10-50** | Conditional Frameworks | HIPAA (10), PCI-DSS (20), GDPR (30), SOC2 (40), ISO-27001 (50) |
| **60-90** | Custom Internal | Organization-specific policies |
| **100+** | Experimental | Beta frameworks |

---

## 📚 Built-in Plugins

### Applications (Out of the Box)

#### CI/CD
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **Jenkins** | ✅ Built-in | ✅ | ✅ | ✅ |
| **GitLab** | ✅ Built-in | ✅ | ✅ | ✅ |
| **Drone** | ✅ Built-in | ✅ | ✅ | ❌ |

#### Version Control
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **Gitea** | ✅ Built-in | ✅ | ✅ | ✅ |

#### Monitoring
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **Grafana** | ✅ Built-in | ✅ | ✅ | ✅ |
| **Prometheus** | ✅ Built-in | ✅ | ✅ | ❌ |

#### Analytics
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **Metabase** | ✅ Built-in | ✅ | ✅ | ❌ |
| **Apache Superset** | ✅ Built-in | ✅ | ✅ | ❌ |

#### Databases
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **PostgreSQL** | ✅ Built-in | ✅ | ✅ | ❌ |
| **Redis** | ✅ Built-in | ✅ | ✅ | ❌ |

#### Artifact Registries
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **Nexus Repository** | ✅ Built-in | ✅ | ✅ | ❌ |
| **Harbor** | ✅ Built-in | ✅ | ✅ | ❌ |

#### Secrets Management
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **HashiCorp Vault** | ✅ Built-in | ✅ | ✅ | ❌ |

#### Collaboration
| Application | Status | Docker/ECS | EC2 | OIDC |
|-------------|--------|------------|-----|------|
| **Mattermost** | ✅ Built-in | ✅ | ✅ | ❌ |

### Compliance Frameworks (Out of the Box)

| Framework | Priority | Always-Load | Status |
|-----------|----------|-------------|--------|
| **KeyManagement** | -10 | ✅ | ✅ Built-in |
| **DatabaseSecurity** | -5 | ✅ | ✅ Built-in |
| **AdvancedMonitoring** | -5 | ✅ | ✅ Built-in |
| **ThreatProtection** | 0 | ✅ | ✅ Built-in |
| **IncidentResponse** | 0 | ✅ | ✅ Built-in |
| **HIPAA** | 10 | ❌ | ✅ Built-in |
| **HIPAA-Organizational** | 15 | ❌ | ✅ Built-in |
| **PCI-DSS** | 20 | ❌ | ✅ Built-in |
| **GDPR** | 30 | ❌ | ✅ Built-in |
| **GDPR-Organizational** | 35 | ❌ | ✅ Built-in |
| **SOC2** | 40 | ❌ | ✅ Built-in |
| **ISO-27001** | 50 | ❌ | ✅ Built-in |
| NIST 800-53 | 25 | ❌ | 🚧 Plugin |
| FedRAMP | 26 | ❌ | 🚧 Plugin |

---

## 🔧 Development Workflow

### 1. Create Plugin Project

```bash
mvn archetype:generate \
  -DgroupId=com.example \
  -DartifactId=my-plugin \
  -DarchetypeArtifactId=maven-archetype-quickstart
```

### 2. Add CloudForge Dependencies

```xml
<dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-core</artifactId>
    <version>3.0.0</version>
    <scope>provided</scope>
</dependency>
```

### 3. Implement Interface

- **Application:** Implement `ApplicationSpec`
- **Compliance:** Implement `FrameworkRules<SystemContext>` + add `@ComplianceFramework`

### 4. Register via ServiceLoader

Create `META-INF/services/` file with your implementation class name.

### 5. Build and Test

```bash
mvn clean package
mvn test
```

### 6. Distribute

- Maven Central
- GitHub Packages
- Direct JAR download

---

## 📖 Documentation

- **Application Plugins:** [APPLICATION-PLUGIN-GUIDE.md](APPLICATION-PLUGIN-GUIDE.md)
- **Compliance Plugins:** [COMPLIANCE-PLUGIN-GUIDE.md](COMPLIANCE-PLUGIN-GUIDE.md)
- **Core API:** [cloudforge-core/src/main/java/com/cloudforge/core/interfaces/](src/main/java/com/cloudforge/core/interfaces/)

---

## 🤝 Community

- **Report Issues:** https://github.com/cloudforgeci/cfc-core/issues
- **Contribute:** https://github.com/cloudforgeci/cfc-core/pulls
- **Examples:** https://github.com/cloudforgeci/cfc-core/tree/main/examples/plugins

---

## ✨ Why Use Plugins?

### For Application Developers
- ✅ Deploy any app without writing infrastructure code
- ✅ Automatic high-availability, monitoring, backups
- ✅ Support both container and VM deployments
- ✅ OIDC SSO integration built-in

### For Compliance Teams
- ✅ Codify internal security policies
- ✅ Automated validation at deployment time
- ✅ Prevent non-compliant infrastructure from deploying
- ✅ Generate compliance reports automatically

### For Organizations
- ✅ Standardize application deployment
- ✅ Enforce compliance across all projects
- ✅ Distribute best practices as reusable plugins
- ✅ Reduce duplicated infrastructure code

---

**Ready to build your first plugin?** 🚀

Choose your adventure:
- 📦 [Build an Application Plugin →](APPLICATION-PLUGIN-GUIDE.md)
- 🔒 [Build a Compliance Plugin →](COMPLIANCE-PLUGIN-GUIDE.md)
