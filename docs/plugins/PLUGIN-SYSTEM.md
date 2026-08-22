# CloudForge Plugin System

CloudForge supports two plugin types for extending application deployment and compliance validation:

## Plugin Types

| Plugin Type | Purpose | Interface | Examples |
|-------------|---------|-----------|----------|
| **Application Plugins** | Deploy custom applications | `ApplicationSpec` | Vault, GitLab, Grafana, Mattermost |
| **Compliance Plugins** | Add compliance frameworks | `FrameworkRules<SystemContext>` + `@ComplianceFramework` | NIST 800-53, custom policies |

---

## Application Plugins

**Purpose:** Deploy any application on AWS using CloudForge infrastructure patterns.

**Key Features:**
- Docker/ECS (Fargate) and EC2 deployment hooks
- VPC, ALB, EFS, and monitoring configuration
- Optional OIDC integration
- Health check configuration
- CloudWatch logging

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

## Compliance Framework Plugins

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

## Plugin Architecture

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

## Priority System (Compliance Only)

Compliance frameworks use priorities to control execution order:

| Priority | Type | Examples |
|----------|------|----------|
| **-10 to -5** | Always-Load Foundation | KeyManagement (-10), DatabaseSecurity (-5) |
| **0** | Always-Load General | ThreatProtection (0), IncidentResponse (0) |
| **10-50** | Conditional Frameworks | HIPAA (10), PCI-DSS (20), GDPR (30), SOC2 (40), ISO-27001 (50) |
| **60-90** | Custom Internal | Organization-specific policies |
| **100+** | Experimental | Beta frameworks |

---

## Built-in Plugins

### Included Applications

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

### Included Compliance Frameworks

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

---

## Development Workflow

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
    <version>3.1.0</version>
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

## Documentation

- **Application Plugins:** [APPLICATION-PLUGIN-GUIDE.md](APPLICATION-PLUGIN-GUIDE.md)
- **Compliance Plugins:** [COMPLIANCE-PLUGIN-GUIDE.md](COMPLIANCE-PLUGIN-GUIDE.md)
- **Core API:** [`cloudforge-core` interfaces](../../cloudforge-core/src/main/java/com/cloudforge/core/interfaces/)

---

## Community

- **Report Issues:** https://github.com/cloudforgeci/cfc-core/issues
- **Contribute:** https://github.com/cloudforgeci/cfc-core/pulls
- **Examples:** https://github.com/cloudforgeci/cfc-core/tree/main/examples/plugins

---

## Plugin Uses

### For Application Developers
- Reuse application infrastructure configuration
- Configure monitoring and backups through the host project
- Support container and VM deployment implementations
- Integrate OIDC where the application supports it

### For Compliance Teams
- Codify internal infrastructure policies
- Run validation at deployment time
- Reject configurations that fail plugin validation
- Add findings to compliance reports

### For Organizations
- Standardize application deployment configuration
- Share validation rules across projects
- Distribute organization-specific patterns as plugins
- Reduce duplicated infrastructure code

---

## Next Steps

- [Build an application plugin](APPLICATION-PLUGIN-GUIDE.md)
- [Build a compliance plugin](COMPLIANCE-PLUGIN-GUIDE.md)
