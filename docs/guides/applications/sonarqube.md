# SonarQube Application Guide

SonarQube performs static analysis of source code to report bugs, vulnerabilities, and maintainability issues.

**Status**: Available (not yet verified end to end)

SonarQube is a built-in application in `cloudforge-api` (`com.cloudforgeci.api.application.cicd.SonarQubeApplicationSpec`). Because it is a compact spec, it is also a useful reference when writing your own ApplicationSpec plugin.

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `sonarqube` |
| **Category** | Code Quality |
| **Default Image** | `sonarqube:lts-community` |
| **Application Port** | `9000` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Default Instance** | t3.medium (EC2) |
| **Health Check Path** | `/api/system/health` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | No (embedded H2, evaluation only) |

---

## Editions

SonarQube is distributed as separate editions (Community, Developer, Enterprise, Data Center). CloudForge deploys the Community Edition image `sonarqube:lts-community` by default. Other editions require a license and a different image (set `containerImage`). See the SonarQube documentation for the features of each edition.

---

## Upstream Features

- Static analysis for bugs, vulnerabilities, and maintainability issues
- Quality profiles and quality gates
- CI/CD and IDE integration

---

## Optional Ports

SonarQube does not have optional ports. All traffic flows through port 9000.

---

## Authentication

| Mode | Description |
|------|-------------|
| `none` | SonarQube local accounts |

SonarQube declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

Native SAML in SonarQube requires a commercial edition; CloudForge does not configure it.

---

## Environment Variables

| Variable | Value |
|----------|-------|
| `SONAR_WEB_CONTEXT` | `/` (set when a domain is configured) |
| `SONAR_WEB_HOST` | `0.0.0.0` (set when a domain is configured) |
| `SONAR_WEB_PORT` | `9000` (set when a domain is configured) |
| `SONAR_WEB_PUBLIC_URL` | `https://<fqdn>` or `http://<fqdn>` (set when a domain is configured) |
| `SONAR_WEB_JAVAADDITIONALOPTS` | `-XX:+UseG1GC -Xmx2g -Xms512m` |
| `SONAR_CE_JAVAADDITIONALOPTS` | `-XX:+UseG1GC -Xmx1g -Xms256m` |

---

## System Requirements

SonarQube's embedded Elasticsearch has these host requirements:

| Requirement | Value |
|-------------|-------|
| `vm.max_map_count` | 262144 |
| `nofile` limit | 65536 |
| `nproc` limit | 4096 |
| Java | 17 or later |

On EC2, the user data installs Java 17, sets the limits in `/etc/security/limits.conf`, and sets `vm.max_map_count`. On Fargate, kernel parameters cannot be changed.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/opt/sonarqube/data` |
| EFS Path | `/sonarqube` |
| Volume Name | `sonarqubeData` |
| Container User | `1000:1000` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/opt/sonarqube/data` |
| Log Paths | `/opt/sonarqube/logs/sonar.log`, `/opt/sonarqube/logs/web.log`, `/opt/sonarqube/logs/ce.log` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "SonarQube-Dev",
  "applicationId": "sonarqube",
  "applicationName": "SonarQube Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "SonarQube-Production",
  "applicationId": "sonarqube",
  "applicationName": "SonarQube",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "sonar",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

### External Database

SonarQube does not implement CloudForge's database integration, so CloudForge does not pass JDBC settings to it even when `provisionDatabase` is set. The embedded H2 database is intended for evaluation and supports a single instance only. To use PostgreSQL, configure `sonar.jdbc.url`, `sonar.jdbc.username`, and `sonar.jdbc.password` yourself.

---

## Plugin Development Reference

The SonarQube spec shows the minimal ApplicationSpec pattern:

```java
@ApplicationPlugin(
    value = "sonarqube",
    category = "code-quality",
    displayName = "SonarQube",
    description = "Continuous code quality and security inspection platform",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false  // Community Edition
)
public class SonarQubeApplicationSpec implements ApplicationSpec {
    // Implementation
}
```

**Location:** `cloudforge-api/src/main/java/com/cloudforgeci/api/application/cicd/SonarQubeApplicationSpec.java`

---

## Post-Deployment Tasks

### 1. Initial Login

1. Open `https://sonar.example.com` (your configured FQDN).
2. Sign in with SonarQube's default credentials, `admin` / `admin`.
3. Change the password when prompted.

### 2. Create Quality Profiles

1. **Quality Profiles** > **Create**
2. Select language
3. Activate rules based on standards

### 3. Create Quality Gates

1. **Quality Gates** > **Create**
2. Set conditions (coverage, duplications, etc.)
3. Assign to projects

### 4. Generate Tokens

For CI/CD integration:
1. **My Account** > **Security**
2. **Generate Tokens**
3. Use in CI/CD pipelines

### 5. Configure Project Analysis

**Maven:**
```bash
mvn sonar:sonar \
  -Dsonar.host.url=https://sonar.example.com \
  -Dsonar.token=your-token
```

**Gradle:**
```bash
./gradlew sonar \
  -Dsonar.host.url=https://sonar.example.com \
  -Dsonar.token=your-token
```

---

## Troubleshooting

### SonarQube won't start

**Check Elasticsearch requirements (EC2):**
```bash
# Verify vm.max_map_count
sysctl vm.max_map_count
# Should be 262144

# Check logs
tail -f /opt/sonarqube/logs/sonar.log
tail -f /opt/sonarqube/logs/es.log
```

### Out of memory

Increase task resources (Fargate):
```json
{
  "cpu": 4096,
  "memory": 8192
}
```

Or for EC2:
```json
{
  "instanceType": "t3.large"
}
```

### Analysis taking too long

1. Check Compute Engine logs
2. Increase CE workers in settings
3. Consider an external PostgreSQL database (see External Database)

---

## Related Documentation

- [Plugin Development Guide](../../plugins/APPLICATION-PLUGIN-GUIDE.md)
- [Compliance Guide](../../compliance/README.md)
- [SonarQube Documentation](https://docs.sonarsource.com/sonarqube/)
