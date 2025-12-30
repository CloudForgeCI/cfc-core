# CloudForge Application Plugin Development Guide

## Overview

CloudForge provides a powerful plugin system for custom applications, enabling developers to:

- ✅ Deploy any application on AWS using CloudForge infrastructure
- ✅ Support both Docker/ECS (Fargate) and EC2 deployments automatically
- ✅ Distribute applications as standalone JAR files
- ✅ Integrate with CloudForge's security, compliance, and OIDC systems
- ✅ Reuse battle-tested infrastructure patterns (VPC, ALB, EFS, monitoring)

## Quick Start

### 1. Create a New Maven Project

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>vault-application</artifactId>
    <version>1.0.0</version>
    <name>HashiCorp Vault Application for CloudForge</name>

    <dependencies>
        <!-- CloudForge Core API -->
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-core</artifactId>
            <version>3.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- CloudForge API (for ApplicationFactory) -->
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-api</artifactId>
            <version>3.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- AWS CDK -->
        <dependency>
            <groupId>software.amazon.awscdk</groupId>
            <artifactId>aws-cdk-lib</artifactId>
            <version>2.147.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### 2. Implement ApplicationSpec

```java
package com.example.applications;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HashiCorp Vault ApplicationSpec implementation.
 *
 * <p>Deploys HashiCorp Vault for secrets management and encryption on AWS.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Secrets management and encryption as a service</li>
 *   <li>Dynamic secrets for databases and cloud providers</li>
 *   <li>Centralized secrets storage</li>
 *   <li>Audit logging and access control</li>
 *   <li>KMS auto-unseal integration</li>
 * </ul>
 *
 * <h2>Deployment Modes:</h2>
 * <ul>
 *   <li><b>Fargate (Container):</b> Uses official vault:latest Docker image</li>
 *   <li><b>EC2:</b> Installs Vault binary via HashiCorp repository</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class VaultApplicationSpec implements ApplicationSpec {

    // ========== Application Identity ==========

    @Override
    public String applicationId() {
        return "vault";
    }

    // ========== Container Configuration (Fargate) ==========

    @Override
    public String defaultContainerImage() {
        return "hashicorp/vault:latest";
    }

    @Override
    public int applicationPort() {
        return 8200;  // Vault HTTP API port
    }

    @Override
    public String containerDataPath() {
        return "/vault/data";
    }

    @Override
    public String efsDataPath() {
        return "/vault";
    }

    @Override
    public String volumeName() {
        return "vaultData";
    }

    @Override
    public String containerUser() {
        return "100:1000";  // Vault runs as user 100
    }

    @Override
    public String efsPermissions() {
        return "755";
    }

    @Override
    public String healthCheckPath() {
        return "/v1/sys/health?standbyok=true";
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> environment = new HashMap<>();

        // Vault server configuration
        environment.put("VAULT_API_ADDR", (sslEnabled ? "https://" : "http://") + (fqdn != null ? fqdn : "localhost:8200"));
        environment.put("VAULT_ADDR", "http://127.0.0.1:8200");  // Internal communication
        environment.put("SKIP_SETCAP", "true");  // Required for container environments

        // Enable Vault UI
        environment.put("VAULT_UI", "true");

        // Vault log level
        environment.put("VAULT_LOG_LEVEL", "info");

        return environment;
    }

    // ========== EC2 Configuration ==========

    @Override
    public String ebsDeviceName() {
        return "/dev/xvdh";
    }

    @Override
    public String ec2DataPath() {
        return "/opt/vault/data";
    }

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/vault/vault.log",
            "/var/log/vault/audit.log",
            "/var/log/userdata.log",
            "/var/log/messages"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        // System updates
        builder.addSystemUpdate();

        // Install required packages
        builder.addCommands(
            "# Install dependencies",
            "command -v dnf >/dev/null && dnf -y install yum-utils || yum -y install yum-utils",
            "echo 'Dependencies installed' >> /var/log/userdata.log"
        );

        // Add HashiCorp repository
        builder.addCommands(
            "# Add HashiCorp repository",
            "yum-config-manager --add-repo https://rpm.releases.hashicorp.com/AmazonLinux/hashicorp.repo",
            "echo 'HashiCorp repository added' >> /var/log/userdata.log"
        );

        // Install Vault
        builder.addCommands(
            "# Install Vault",
            "command -v dnf >/dev/null && dnf -y install vault || yum -y install vault",
            "echo 'Vault installed' >> /var/log/userdata.log"
        );

        // Install and configure CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage (EFS or EBS based on availability)
        String[] userParts = containerUser().split(":");
        String uid = userParts[0];
        String gid = userParts[1];

        if (context.hasEfs()) {
            builder.mountEfs(
                context.efsId().orElseThrow(),
                context.accessPointId().orElseThrow(),
                ec2DataPath(),
                uid,
                gid
            );
        } else {
            builder.mountEbs(
                ebsDeviceName(),
                ec2DataPath(),
                uid,
                gid
            );
        }

        // Create Vault directories
        builder.addCommands(
            "# Create Vault directories",
            "mkdir -p /etc/vault.d",
            "mkdir -p /var/log/vault",
            "chown -R vault:vault /var/log/vault",
            "echo 'Vault directories created' >> /var/log/userdata.log"
        );

        // Configure Vault
        builder.addCommands(
            "# Configure Vault",
            "cat > /etc/vault.d/vault.hcl <<'EOF'",
            "ui = true",
            "",
            "storage \"file\" {",
            "  path = \"" + ec2DataPath() + "\"",
            "}",
            "",
            "listener \"tcp\" {",
            "  address     = \"0.0.0.0:8200\"",
            "  tls_disable = 1",
            "}",
            "",
            "api_addr = \"http://127.0.0.1:8200\"",
            "EOF",
            "chown vault:vault /etc/vault.d/vault.hcl",
            "chmod 640 /etc/vault.d/vault.hcl",
            "echo 'Vault configuration created' >> /var/log/userdata.log"
        );

        // Configure Vault systemd service
        builder.addCommands(
            "# Configure Vault systemd service",
            "cat > /etc/systemd/system/vault.service <<'EOF'",
            "[Unit]",
            "Description=HashiCorp Vault",
            "Documentation=https://www.vaultproject.io/docs/",
            "Requires=network-online.target",
            "After=network-online.target",
            "ConditionFileNotEmpty=/etc/vault.d/vault.hcl",
            "",
            "[Service]",
            "User=vault",
            "Group=vault",
            "ProtectSystem=full",
            "ProtectHome=read-only",
            "PrivateTmp=yes",
            "PrivateDevices=yes",
            "SecureBits=keep-caps",
            "AmbientCapabilities=CAP_IPC_LOCK",
            "CapabilityBoundingSet=CAP_SYSLOG CAP_IPC_LOCK",
            "NoNewPrivileges=yes",
            "ExecStart=/usr/bin/vault server -config=/etc/vault.d/vault.hcl",
            "ExecReload=/bin/kill --signal HUP $MAINPID",
            "KillMode=process",
            "KillSignal=SIGINT",
            "Restart=on-failure",
            "RestartSec=5",
            "TimeoutStopSec=30",
            "LimitNOFILE=65536",
            "LimitMEMLOCK=infinity",
            "",
            "[Install]",
            "WantedBy=multi-user.target",
            "EOF",
            "echo 'Vault systemd service configured' >> /var/log/userdata.log"
        );

        // Start Vault
        builder.addCommands(
            "# Start Vault",
            "systemctl daemon-reload",
            "systemctl enable vault",
            "systemctl start vault",
            "echo 'Vault service started' >> /var/log/userdata.log",
            "",
            "# Wait for Vault to start",
            "sleep 10",
            "",
            "# Check Vault status",
            "if systemctl is-active --quiet vault; then",
            "  echo 'Vault is running' >> /var/log/userdata.log",
            "  export VAULT_ADDR='http://127.0.0.1:8200'",
            "  vault status >> /var/log/userdata.log 2>&1 || echo 'Vault not initialized yet' >> /var/log/userdata.log",
            "else",
            "  echo 'ERROR: Vault failed to start' >> /var/log/userdata.log",
            "  journalctl -u vault -n 50 >> /var/log/userdata.log",
            "fi"
        );
    }

    // ========== OIDC Integration (Not supported by Vault Community) ==========

    @Override
    public boolean supportsOidcIntegration() {
        return false;  // OIDC is an Enterprise feature
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return null;
    }

    @Override
    public String toString() {
        return "VaultApplicationSpec{" +
                "applicationId='vault'" +
                ", defaultImage='hashicorp/vault:latest'" +
                ", applicationPort=8200" +
                ", containerDataPath='/vault/data'" +
                ", ec2DataPath='/opt/vault/data'" +
                '}';
    }
}
```

### 3. Register Your Application (ServiceLoader Pattern)

Create: `src/main/resources/META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`

```
# HashiCorp Vault Application
com.example.applications.VaultApplicationSpec
```

### 4. Build and Distribute

```bash
mvn clean package
```

---

## Using Your Custom Application

### In CDK Stack

```java
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.example.applications.VaultApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforge.core.enums.RuntimeType;

public class VaultStack extends Stack {
    public VaultStack(Construct scope, String id) {
        super(scope, id);

        // Create application spec
        ApplicationSpec vaultSpec = new VaultApplicationSpec();

        // Deploy on Fargate
        ApplicationFactory vaultFargate = new ApplicationFactory(
            this,
            "VaultFargate",
            RuntimeType.FARGATE,
            vaultSpec
        );

        // Or deploy on EC2
        ApplicationFactory vaultEc2 = new ApplicationFactory(
            this,
            "VaultEc2",
            RuntimeType.EC2,
            vaultSpec
        );
    }
}
```

### Via cdk.json Configuration

```json
{
  "context": {
    "stackName": "VaultProd",
    "applicationId": "vault",
    "runtimeType": "FARGATE",
    "securityProfile": "PRODUCTION",
    "domain": "example.com",
    "subdomain": "vault",
    "sslEnabled": "true"
  }
}
```

---

## Advanced Examples

### Example 1: GitLab with OIDC Integration

```java
package com.example.applications;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.GitLabOidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitLab ApplicationSpec implementation with OIDC support.
 */
public class GitLabApplicationSpec implements ApplicationSpec {

    @Override
    public String applicationId() {
        return "gitlab";
    }

    @Override
    public String defaultContainerImage() {
        return "gitlab/gitlab-ce:latest";
    }

    @Override
    public int applicationPort() {
        return 80;
    }

    @Override
    public String containerDataPath() {
        return "/var/opt/gitlab";
    }

    @Override
    public String efsDataPath() {
        return "/gitlab";
    }

    @Override
    public String volumeName() {
        return "gitlabData";
    }

    @Override
    public String containerUser() {
        return "998:998";  // GitLab git user
    }

    @Override
    public String efsPermissions() {
        return "755";
    }

    @Override
    public String healthCheckPath() {
        return "/-/health";
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> environment = new HashMap<>();

        // GitLab configuration
        StringBuilder omnibusConfig = new StringBuilder();

        // External URL
        if (fqdn != null && !fqdn.isBlank()) {
            String externalUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            omnibusConfig.append("external_url '").append(externalUrl).append("'; ");
        }

        // OIDC configuration for application-oidc mode
        if ("application-oidc".equals(authMode)) {
            omnibusConfig.append("gitlab_rails['omniauth_enabled'] = true; ");
            omnibusConfig.append("gitlab_rails['omniauth_allow_single_sign_on'] = ['openid_connect']; ");
            omnibusConfig.append("gitlab_rails['omniauth_block_auto_created_users'] = false; ");
            omnibusConfig.append("gitlab_rails['omniauth_auto_link_user'] = ['openid_connect']; ");
        }

        // Disable HTTPS redirect (ALB handles SSL)
        if (sslEnabled) {
            omnibusConfig.append("nginx['listen_https'] = false; ");
            omnibusConfig.append("nginx['listen_port'] = 80; ");
        }

        environment.put("GITLAB_OMNIBUS_CONFIG", omnibusConfig.toString().trim());

        return environment;
    }

    @Override
    public String ebsDeviceName() {
        return "/dev/xvdh";
    }

    @Override
    public String ec2DataPath() {
        return "/var/opt/gitlab";
    }

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/var/log/gitlab/gitlab-rails/production.log",
            "/var/log/gitlab/nginx/gitlab_access.log",
            "/var/log/userdata.log"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        // System updates
        builder.addSystemUpdate();

        // Install dependencies
        builder.addCommands(
            "# Install dependencies for GitLab",
            "command -v dnf >/dev/null && dnf -y install curl policycoreutils openssh-server perl postfix || " +
            "yum -y install curl policycoreutils openssh-server perl postfix",
            "systemctl enable sshd postfix",
            "systemctl start sshd postfix",
            "echo 'Dependencies installed' >> /var/log/userdata.log"
        );

        // Add GitLab repository
        builder.addCommands(
            "# Add GitLab repository",
            "curl https://packages.gitlab.com/install/repositories/gitlab/gitlab-ce/script.rpm.sh | bash",
            "echo 'GitLab repository added' >> /var/log/userdata.log"
        );

        // Install GitLab
        builder.addCommands(
            "# Install GitLab CE",
            "EXTERNAL_URL=\"http://" + context.fqdn().orElse("localhost") + "\" yum -y install gitlab-ce",
            "echo 'GitLab installed' >> /var/log/userdata.log"
        );

        // Install and configure CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage (EFS or EBS)
        String[] userParts = containerUser().split(":");
        String uid = userParts[0];
        String gid = userParts[1];

        if (context.hasEfs()) {
            builder.mountEfs(
                context.efsId().orElseThrow(),
                context.accessPointId().orElseThrow(),
                ec2DataPath(),
                uid,
                gid
            );
        } else {
            builder.mountEbs(
                ebsDeviceName(),
                ec2DataPath(),
                uid,
                gid
            );
        }

        // Reconfigure and start GitLab
        builder.addCommands(
            "# Reconfigure GitLab",
            "gitlab-ctl reconfigure",
            "echo 'GitLab configured and started' >> /var/log/userdata.log",
            "",
            "# Wait for GitLab to fully start",
            "sleep 60",
            "",
            "# Get initial root password",
            "if [ -f /etc/gitlab/initial_root_password ]; then",
            "  echo 'GitLab Root Password:' >> /var/log/userdata.log",
            "  cat /etc/gitlab/initial_root_password >> /var/log/userdata.log",
            "fi"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new GitLabOidcIntegration();
    }

    @Override
    public String toString() {
        return "GitLabApplicationSpec{" +
                "applicationId='gitlab'" +
                ", defaultImage='gitlab/gitlab-ce:latest'" +
                ", applicationPort=80" +
                '}';
    }
}
```

### Example 2: Grafana with OIDC

```java
public class GrafanaApplicationSpec implements ApplicationSpec {

    @Override
    public String applicationId() {
        return "grafana";
    }

    @Override
    public String defaultContainerImage() {
        return "grafana/grafana:latest";
    }

    @Override
    public int applicationPort() {
        return 3000;
    }

    @Override
    public String healthCheckPath() {
        return "/api/health";
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> environment = new HashMap<>();

        // Grafana server configuration
        if (fqdn != null && !fqdn.isBlank()) {
            environment.put("GF_SERVER_ROOT_URL", (sslEnabled ? "https://" : "http://") + fqdn);
            environment.put("GF_SERVER_DOMAIN", fqdn);
        }

        // OIDC configuration
        if ("application-oidc".equals(authMode)) {
            environment.put("GF_AUTH_GENERIC_OAUTH_ENABLED", "true");
            environment.put("GF_AUTH_GENERIC_OAUTH_NAME", "IAM Identity Center");
            environment.put("GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP", "true");
            environment.put("GF_AUTH_GENERIC_OAUTH_SCOPES", "openid profile email");
        }

        return environment;
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new GrafanaOidcIntegration();
    }

    // ... rest of implementation
}
```

---

## ApplicationSpec API Reference

### Required Methods

| Method | Purpose | Example |
|--------|---------|---------|
| `applicationId()` | Unique identifier | `"jenkins"`, `"gitlab"`, `"vault"` |
| `defaultContainerImage()` | Docker image | `"jenkins/jenkins:lts"` |
| `applicationPort()` | HTTP port | `8080`, `80`, `3000` |
| `containerDataPath()` | Mount path in container | `"/var/jenkins_home"` |
| `efsDataPath()` | Path in EFS | `"/jenkins"` |
| `volumeName()` | Volume reference name | `"jenkinsHome"` |
| `containerUser()` | UID:GID | `"1000:1000"` |
| `efsPermissions()` | File permissions | `"750"`, `"755"` |
| `ebsDeviceName()` | EC2 block device | `"/dev/xvdh"` |
| `ec2DataPath()` | EC2 mount path | `"/var/lib/jenkins"` |
| `ec2LogPaths()` | CloudWatch log paths | `["/var/log/app.log"]` |
| `configureUserData()` | EC2 installation script | See examples above |

### Optional Methods

| Method | Default | Purpose |
|--------|---------|---------|
| `healthCheckPath()` | `"/"` | ALB health check endpoint |
| `containerEnvironmentVariables()` | `{}` | Container env vars |
| `supportsOidcIntegration()` | `false` | Whether app supports OIDC |
| `getOidcIntegration()` | `null` | OIDC configuration handler |

---

## UserDataBuilder API

The `UserDataBuilder` provides helper methods for EC2 configuration:

### System Configuration

```java
// Update OS packages
builder.addSystemUpdate();

// Add custom commands
builder.addCommands(
    "# Install application",
    "yum install -y myapp",
    "systemctl enable myapp"
);
```

### Storage Mounting

```java
// Mount EFS
builder.mountEfs(
    efsId,
    accessPointId,
    mountPath,
    uid,
    gid
);

// Mount EBS
builder.mountEbs(
    deviceName,
    mountPath,
    uid,
    gid
);
```

### CloudWatch Integration

```java
// Install and configure CloudWatch Agent
builder.installCloudWatchAgent(
    logGroupName,
    List.of("/var/log/app.log", "/var/log/access.log")
);
```

---

## Ec2Context API

The `Ec2Context` provides deployment information:

```java
// Stack information
String stackName = context.stackName();
String securityProfile = context.securityProfile();
String runtimeType = context.runtimeType();

// Network information
Optional<String> fqdn = context.fqdn();
boolean sslEnabled = context.sslEnabled();

// Storage information
boolean hasEfs = context.hasEfs();
Optional<String> efsId = context.efsId();
Optional<String> accessPointId = context.accessPointId();
```

---

## Best Practices

### 1. ✅ DO: Support Both Runtimes

Test your application on both Fargate and EC2:

```bash
# Test Fargate deployment
cdk deploy -c runtimeType=FARGATE

# Test EC2 deployment
cdk deploy -c runtimeType=EC2
```

### 2. ✅ DO: Use Official Images

Prefer official Docker images when available:
- ✅ `jenkins/jenkins:lts`
- ✅ `gitlab/gitlab-ce:latest`
- ✅ `hashicorp/vault:latest`
- ❌ `random-user/jenkins:custom`

### 3. ✅ DO: Configure Health Checks

Provide application-specific health endpoints:

```java
@Override
public String healthCheckPath() {
    return "/api/health";  // Application health endpoint
}
```

### 4. ✅ DO: Log Everything

Send logs to CloudWatch for debugging:

```java
builder.addCommands(
    "echo 'Step completed' >> /var/log/userdata.log",
    "mycommand >> /var/log/userdata.log 2>&1"
);
```

### 5. ❌ DON'T: Hard-Code Credentials

Never hard-code passwords or secrets:

```java
// Bad
environment.put("DB_PASSWORD", "hardcoded");

// Good
environment.put("DB_PASSWORD_SECRET_ARN", secretArn);
```

### 6. ❌ DON'T: Assume Filesystem Paths

Check context for EFS vs EBS:

```java
if (context.hasEfs()) {
    // Use EFS mount
} else {
    // Use EBS mount
}
```

---

## Testing Your Application Plugin

### Unit Tests

```java
@Test
void testApplicationSpecMetadata() {
    ApplicationSpec spec = new VaultApplicationSpec();

    assertEquals("vault", spec.applicationId());
    assertEquals("hashicorp/vault:latest", spec.defaultContainerImage());
    assertEquals(8200, spec.applicationPort());
    assertEquals("/vault/data", spec.containerDataPath());
}

@Test
void testContainerEnvironmentVariables() {
    ApplicationSpec spec = new VaultApplicationSpec();

    Map<String, String> env = spec.containerEnvironmentVariables(
        "vault.example.com",
        true,
        "none"
    );

    assertTrue(env.containsKey("VAULT_API_ADDR"));
    assertEquals("https://vault.example.com", env.get("VAULT_API_ADDR"));
}
```

### Integration Tests

```java
@Test
void testFargateDeployment() {
    App app = new App();
    Stack stack = new Stack(app, "VaultFargateTest");

    ApplicationSpec spec = new VaultApplicationSpec();
    ApplicationFactory factory = new ApplicationFactory(
        stack,
        "Vault",
        RuntimeType.FARGATE,
        spec
    );

    assertDoesNotThrow(() -> app.synth());
}

@Test
void testEc2Deployment() {
    App app = new App();
    Stack stack = new Stack(app, "VaultEc2Test");

    ApplicationSpec spec = new VaultApplicationSpec();
    ApplicationFactory factory = new ApplicationFactory(
        stack,
        "Vault",
        RuntimeType.EC2,
        spec
    );

    assertDoesNotThrow(() -> app.synth());
}
```

---

## Distribution

### Maven Central

```xml
<dependency>
    <groupId>com.yourcompany</groupId>
    <artifactId>vault-application</artifactId>
    <version>1.0.0</version>
</dependency>
```

### GitHub Releases

```bash
# Download JAR from your plugin repository
wget https://github.com/yourcompany/vault-application/releases/download/v1.0.0/vault-application-1.0.0.jar

# Add to classpath
java -cp "cloudforge-api.jar:vault-application-1.0.0.jar" ...
```

---

## Example Application Plugins

Here are application ideas you can implement:

### CI/CD Tools
- ✅ Jenkins (built-in)
- 🚧 GitLab
- 🚧 Drone CI
- 🚧 Gitea
- 🚧 ArgoCD

### Monitoring & Observability
- 🚧 Grafana
- 🚧 Prometheus
- 🚧 Metabase
- 🚧 Apache Superset

### Databases
- 🚧 PostgreSQL
- 🚧 Redis
- 🚧 MongoDB
- 🚧 Cassandra

### Artifact Registries
- 🚧 Nexus Repository
- 🚧 Harbor
- 🚧 JFrog Artifactory

### Secrets Management
- 🚧 HashiCorp Vault
- 🚧 Infisical

### Collaboration
- 🚧 Mattermost
- 🚧 Rocket.Chat

---

## Support and Community

- **Documentation:** https://github.com/cloudforgeci/cfc-core/docs
- **Issues:** https://github.com/cloudforgeci/cfc-core/issues
- **Examples:** https://github.com/cloudforgeci/cfc-core/tree/main/examples/applications

---

**Happy Application Development!** 🚀
