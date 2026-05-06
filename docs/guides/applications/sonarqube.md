# SonarQube Application Guide

SonarQube is an open-source platform for continuous code quality and security inspection, detecting bugs, vulnerabilities, and code smells across 30+ programming languages.

**Status**: Plugin Example (Community Contribution)

**Note**: SonarQube is implemented as a **plugin example** in `cfc-testing` to demonstrate the ApplicationSpec plugin system. It serves as a template for creating custom application plugins.

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
| **OIDC Support** | No (Community Edition) |
| **Database Required** | No (embedded H2) |

---

## Deployment Architecture

The following AWS architecture diagram shows the complete SonarQube deployment:

```mermaid
graph TD
    Start[CDK Deploy Starts] --> Validate[Validate Deployment Context]
    Validate --> CheckAuth{Auth Mode?}
    
    CheckAuth -->|alb-oidc| CreateCognito[Create Cognito User Pool]
    CheckAuth -->|none| SkipAuth[Skip Authentication]
    
    CreateCognito --> CreateAppClient[Create Cognito App Client]
    SkipAuth --> CheckRuntime{Runtime?}
    CreateAppClient --> CheckRuntime
    
    CheckRuntime -->|fargate| CreateECS[Create ECS Cluster & Service]
    CheckRuntime -->|ec2| CreateASG[Create Auto Scaling Group]
    
    CreateECS --> CreateALB[Create Application Load Balancer]
    CreateASG --> CreateALB
    
    CreateALB --> ConfigureTarget[Configure Target Group]
    CreateALB --> ConfigureAuth{ALB-OIDC?}
    
    ConfigureAuth -->|Yes| SetupALBAuth[Configure ALB OIDC Authentication]
    ConfigureAuth -->|No| SkipALBAuth[Skip ALB Auth]
    
    SetupALBAuth --> CreateEFS[Create EFS for Persistent Storage]
    SkipALBAuth --> CreateEFS
    
    CreateEFS --> MountStorage[Mount Storage to Container/Instance]
    MountStorage --> StartSonarQube[Start SonarQube Container]
    
    StartSonarQube --> InitDB[Initialize H2 Database]
    InitDB --> HealthCheck{Health Check Passes?}
    
    HealthCheck -->|No| Wait[Wait & Retry]
    Wait --> HealthCheck
    HealthCheck -->|Yes| Ready[SonarQube Ready]
    
    Ready --> Access[Access SonarQube UI]
    
    Note1[Note: Community Edition uses embedded H2]
    Note2[Note: ALB-OIDC recommended for UI access]
    Note3[Note: Enterprise features require license]
```

## Architecture Diagram

The following diagram shows the SonarQube deployment architecture:

```mermaid
graph TB
    Internet[🌐 Internet] --> ALB[⚖️ Application Load Balancer]
    
    ALB --> ECS1[☁️ ECS Fargate / EC2<br/>SonarQube Container 1]
    
    ECS1 --> EFS[(💾 Amazon EFS)]
    
    ALB --> Cognito[🔐 AWS Cognito]
    
    ECS1 --> CloudWatch[📊 CloudWatch Logs]
```

---

## Editions

**Important**: Unlike Mattermost and Metabase, SonarQube editions are separate products:

| Edition | License | OIDC/SAML | Features |
|---------|---------|-----------|----------|
| **Community** | Free | No | Basic analysis, 15+ languages |
| **Developer** | Paid | Yes | Branch analysis, PR decoration |
| **Enterprise** | Paid | Yes | Portfolio management, security reports |
| **Data Center** | Paid | Yes | High availability, horizontal scaling |

CloudForge deploys **Community Edition** by default. Enterprise features require purchasing and deploying a different image.

---

## Capabilities

- Static code analysis
- Security vulnerability detection (OWASP Top 10, CWE)
- Code smell detection
- Technical debt tracking
- Quality gates
- Multi-language support (30+ languages)
- CI/CD integration
- IDE integration (SonarLint)
- Quality profiles
- Custom rules

---

## Optional Ports

SonarQube does not have optional ports. All traffic flows through port 9000.

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | Available | ALB-level authentication |
| `none` | Available | Local accounts only |

**Note:** Native OIDC/SAML requires Developer Edition or higher.

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SONAR_WEB_CONTEXT` | Context path | `/` |
| `SONAR_WEB_HOST` | Bind address | `0.0.0.0` |
| `SONAR_WEB_PORT` | Application port | `9000` |
| `SONAR_WEB_PUBLIC_URL` | External URL | `https://sonar.example.com` |
| `SONAR_WEB_JAVAADDITIONALOPTS` | Web JVM options | `-XX:+UseG1GC -Xmx2g` |
| `SONAR_CE_JAVAADDITIONALOPTS` | Compute Engine JVM options | `-XX:+UseG1GC -Xmx1g` |

---

## System Requirements

SonarQube has specific system requirements for Elasticsearch:

| Requirement | Value |
|-------------|-------|
| `vm.max_map_count` | 262144 |
| `nofile` limit | 65536 |
| `nproc` limit | 4096 |
| Java | 17+ |

CloudForge automatically configures these for EC2 deployments.

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
  "description": "SonarQube code quality server",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$60/month

### Production - With ALB Authentication

```json
{
  "stackName": "SonarQube-Production",
  "applicationId": "sonarqube",
  "applicationName": "SonarQube",
  "description": "Production code quality server",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "sonar",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "sonarqube-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": false,
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

**Cost estimate:** ~$250/month

### Production - With External Database

For high availability, use PostgreSQL instead of embedded H2:

```json
{
  "stackName": "SonarQube-HA",
  "applicationId": "sonarqube",
  "applicationName": "SonarQube HA",
  "description": "High availability SonarQube",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "sonar",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "sonarqube-ha-yourcompany",
  "cognitoMfaEnabled": true,

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,
  "databaseName": "sonarqube",
  "databaseBackupRetentionDays": 30,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Note:** When using external database, update `sonar.properties` with JDBC connection.

**Cost estimate:** ~$400/month

---

## Plugin Development Reference

SonarQube in CloudForge demonstrates the ApplicationSpec plugin pattern:

```java
@ApplicationPlugin(
    value = "sonarqube",
    category = "code-quality",
    displayName = "SonarQube",
    description = "Continuous code quality inspection",
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

**Location:** `cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/application/`

---

## Post-Deployment Tasks

### 1. Initial Login

1. Navigate to `https://sonar.your-domain.com`
2. Default credentials: `admin` / `admin`
3. **Immediately change password**

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
./gradlew sonarqube \
  -Dsonar.host.url=https://sonar.example.com \
  -Dsonar.token=your-token
```

---

## Troubleshooting

### SonarQube won't start

**Check Elasticsearch requirements:**
```bash
# Verify vm.max_map_count
sysctl vm.max_map_count
# Should be 262144

# Check logs
tail -f /opt/sonarqube/logs/sonar.log
tail -f /opt/sonarqube/logs/es.log
```

### Out of memory

Increase JVM heap:
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
3. Consider dedicated database

---

## Related Documentation

- [Plugin Development Guide](../../plugins/APPLICATION-PLUGIN-GUIDE.md)
- [Compliance Guide](../../compliance/README.md)
- [SonarQube Documentation](https://docs.sonarsource.com/sonarqube/)
