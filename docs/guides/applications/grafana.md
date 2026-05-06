# Grafana Application Guide

Grafana is an open-source platform for monitoring and observability that allows you to query, visualize, alert on, and understand your metrics.

**Status**: Available (Not Yet Tested)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `grafana` |
| **Category** | Monitoring |
| **Default Image** | `grafana/grafana:latest` |
| **Application Port** | `3000` |
| **Default CPU** | 512 (Fargate) |
| **Default Memory** | 1024 MB (Fargate) |
| **Default Instance** | t3.micro (EC2) |
| **Health Check Path** | `/api/health` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **OIDC Support** | Yes (via generic_oauth) |
| **Database Required** | Optional |

---

## Deployment Architecture

The following AWS architecture diagram shows the complete Grafana deployment:

```mermaid
%%{init: {'theme':'architecture-beta'}}%%
graph TD
    Start[🚀 CDK Deploy Starts] --> Validate[Validate Deployment Context]
    Validate --> CheckAuth{Auth Mode?}
    
    CheckAuth -->|application-oidc| CreateCognito[🔐 Create Cognito User Pool]
    CheckAuth -->|alb-oidc| CreateCognito
    CheckAuth -->|none| SkipAuth[Skip Authentication]
    
    CreateCognito --> CreateAppClient[🔐 Create Cognito App Client]
    SkipAuth --> CheckDB{Database Required?}
    CreateAppClient --> CheckDB
    
    CheckDB -->|Yes| CreateRDS[🗄️ Create RDS Database Instance]
    CheckDB -->|No| UseSQLite[💾 Use SQLite Embedded Database]
    CreateRDS --> WaitDB[Wait for Database Ready]
    WaitDB --> CheckRuntime{Runtime?}
    UseSQLite --> CheckRuntime
    
    CheckRuntime -->|fargate| CreateECS[☁️ Create ECS Cluster & Service]
    CheckRuntime -->|ec2| CreateASG[☁️ Create Auto Scaling Group]
    
    CreateECS --> CreateALB[⚖️ Create Application Load Balancer]
    CreateASG --> CreateALB
    
    CreateALB --> ConfigureTarget[Configure Target Group]
    ConfigureTarget --> CreateEFS[💾 Create EFS for Persistent Storage]
    
    CreateEFS --> MountStorage[Mount Storage to Container/Instance]
    MountStorage --> StartGrafana[📊 Start Grafana Container]
    
    StartGrafana --> HealthCheck{Health Check Passes?}
    HealthCheck -->|No| Wait[Wait & Retry]
    Wait --> HealthCheck
    HealthCheck -->|Yes| ConfigureOIDC{application-oidc?}
    
    ConfigureOIDC -->|Yes| SetupGenericOAuth[🔐 Configure Generic OAuth]
    ConfigureOIDC -->|No| Ready[📊 Grafana Ready]
    SetupGenericOAuth --> Ready
    
    Ready --> Access[🌐 Access Grafana UI]
    
    Note1[Note: SQLite for single instance only]
    Note2[Note: PostgreSQL recommended for production]
```

## Authentication Flow

When using `application-oidc` authentication mode, the following sequence diagram shows how users authenticate:

```mermaid
%%{init: {'theme':'architecture-beta'}}%%
sequenceDiagram
    autonumber
    participant User as 👤 User
    participant ALB as ⚖️ ALB
    participant Grafana as 📊 Grafana
    participant Cognito as 🔐 Cognito
    
    Note over User,Cognito: 1. Initial Request
    User->>ALB: Navigate to Grafana
    ALB->>Grafana: Forward request
    Grafana->>User: Redirect to Cognito
    
    Note over User,Cognito: 2. Authentication
    User->>Cognito: Enter credentials
    Cognito->>User: Request MFA
    User->>Cognito: Provide MFA code
    Cognito->>User: Return authorization code
    
    Note over User,Grafana: 3. Token Exchange
    User->>Grafana: Callback with code
    Grafana->>Cognito: Exchange code for tokens
    Cognito->>Grafana: Return ID token + access token
    
    Note over User,Grafana: 4. User Creation
    Grafana->>Grafana: Extract user info
    Grafana->>Grafana: Create/update account
    Grafana->>User: Redirect to dashboard
    
    Note over User,Grafana: User authenticated
```

## Architecture Diagram

The following diagram shows the Grafana deployment architecture:

```mermaid
%%{init: {'theme':'architecture-beta'}}%%
graph TB
    Internet[🌐 Internet] --> ALB[⚖️ Application Load Balancer]
    
    ALB --> ECS1[☁️ ECS Fargate / EC2<br/>Grafana Container 1]
    ALB --> ECS2[☁️ ECS Fargate / EC2<br/>Grafana Container 2]
    
    ECS1 --> RDS[(🗄️ Amazon RDS PostgreSQL)]
    ECS2 --> RDS
    
    ECS1 --> EFS[(💾 Amazon EFS)]
    ECS2 --> EFS
    
    ALB --> Cognito[🔐 AWS Cognito]
    
    ECS1 --> CloudWatch[📊 CloudWatch Logs]
    ECS2 --> CloudWatch
```

---

## Capabilities

- Multi-source metrics visualization
- Interactive dashboards
- Alerting and notifications
- Team and user management
- Plugin ecosystem (panels, data sources)
- Dashboard templating
- Annotations and events
- Explore mode for ad-hoc queries
- Dashboard sharing and embedding
- Built-in support for Prometheus, CloudWatch, InfluxDB, etc.

---

## Optional Ports

Grafana does not have optional ports. All traffic flows through port 3000.

---

## Database Configuration

### Development

Uses SQLite (H2 embedded) - single instance only.

### Production (Recommended)

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 14+ |
| Instance Class | db.t3.micro (default) |
| Storage | 20 GB (default) |
| Database Name | `grafana` |

When using RDS, environment variables are set:
- `GF_DATABASE_TYPE`: postgres
- `GF_DATABASE_HOST`: RDS endpoint
- `GF_DATABASE_NAME`: grafana
- `GF_DATABASE_USER`: grafana
- `GF_DATABASE_PASSWORD`: From Secrets Manager

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `application-oidc` | Available | Native OIDC via generic_oauth |
| `alb-oidc` | Available | ALB-level authentication |
| `none` | Available | Local accounts only |

### OIDC Integration Details

Grafana uses **generic_oauth** provider configured via environment variables.

**Features:**
- Auto-create users on first login
- Group/role mapping from OIDC claims
- Admin role assignment via group membership
- PKCE support
- Automatic user provisioning

**Callback Path:** `/login/generic_oauth`

**Role Mapping:**
- Users in admin group → Grafana Admin role
- Others → Grafana Editor role

---

## Environment Variables

CloudForge automatically configures:

| Variable | Description | Example |
|----------|-------------|---------|
| `GF_SERVER_ROOT_URL` | External URL (critical for OAuth) | `https://grafana.example.com` |
| `GF_SERVER_DOMAIN` | Domain name | `grafana.example.com` |
| `GF_SERVER_ENFORCE_DOMAIN` | Allow ALB health checks | `false` |
| `GF_SERVER_PROTOCOL` | Protocol (ALB handles HTTPS) | `http` |
| `GF_DATABASE_TYPE` | Database type | `postgres` or `sqlite3` |

**OIDC Variables (when enabled):**
| Variable | Description |
|----------|-------------|
| `GF_AUTH_GENERIC_OAUTH_ENABLED` | Enable OAuth |
| `GF_AUTH_GENERIC_OAUTH_CLIENT_ID` | OAuth client ID |
| `GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET` | OAuth client secret |
| `GF_AUTH_GENERIC_OAUTH_AUTH_URL` | Authorization endpoint |
| `GF_AUTH_GENERIC_OAUTH_TOKEN_URL` | Token endpoint |
| `GF_AUTH_GENERIC_OAUTH_API_URL` | UserInfo endpoint |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/var/lib/grafana` |
| EFS Path | `/grafana` |
| Volume Name | `grafanaData` |
| Container User | `472:472` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/lib/grafana` |
| Log Paths | `/var/log/grafana/grafana.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal Setup

```json
{
  "stackName": "Grafana-Dev",
  "applicationId": "grafana",
  "applicationName": "Grafana Dev",
  "description": "Grafana development environment",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 512,
  "memory": 1024,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$25/month

### Development - With OIDC

```json
{
  "stackName": "Grafana-Dev-Auth",
  "applicationId": "grafana",
  "applicationName": "Grafana Dev",
  "description": "Grafana with Cognito authentication",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "domain": "dev.example.com",
  "subdomain": "grafana",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-dev-yourcompany",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "GrafanaAdmins",
  "cognitoUserGroupName": "GrafanaViewers",

  "cpu": 512,
  "memory": 1024,

  "enableMonitoring": true,
  "logRetentionDays": "30"
}
```

**Cost estimate:** ~$70/month

### Production - With Database

```json
{
  "stackName": "Grafana-Production",
  "applicationId": "grafana",
  "applicationName": "Grafana",
  "description": "Production Grafana with PostgreSQL",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "grafana",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "GrafanaAdmins",
  "cognitoUserGroupName": "GrafanaViewers",

  "instanceType": "t3.small",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseMultiAz": true,
  "databaseName": "grafana",
  "databaseBackupRetentionDays": 30,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": false,
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$250/month

### Observability Stack (with Prometheus)

Deploy Grafana alongside Prometheus for complete observability.

```json
{
  "stackName": "Grafana-Observability",
  "applicationId": "grafana",
  "applicationName": "Grafana Observability",
  "description": "Grafana for observability stack",
  "environment": "production",

  "runtime": "fargate",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "metrics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-obs-yourcompany",
  "cognitoMfaEnabled": true,

  "cpu": 1024,
  "memory": 2048,
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

**Cost estimate:** ~$300/month

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/api/health` | Health check endpoint |
| Grace Period | 300 seconds | Time before health checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |

---

## Compliance Considerations

### SOC2

**Automatic Controls:**
- Encryption at rest
- Encryption in transit (TLS)
- Network isolation
- CloudWatch logging

**User Responsibilities:**
- [ ] Configure session timeouts
- [ ] Disable anonymous access
- [ ] Implement dashboard access controls
- [ ] Enable audit logging (Grafana Enterprise)
- [ ] Configure data source access controls

---

## Post-Deployment Tasks

### 1. Initial Login

1. Navigate to `https://grafana.your-domain.com`
2. If OIDC: Click "Sign in with OAuth"
3. If local: Default credentials `admin` / `admin`

### 2. Add Data Sources

1. **Configuration** > **Data Sources**
2. Click **Add data source**
3. Select type (Prometheus, CloudWatch, etc.)
4. Configure connection

**Example Prometheus data source:**
```
URL: http://prometheus:9090
Access: Server (default)
```

**Example CloudWatch data source:**
```
Authentication Provider: AWS SDK Default
Default Region: us-east-1
```

### 3. Import Dashboards

1. **Dashboards** > **Import**
2. Enter Grafana.com dashboard ID or upload JSON
3. Select data source

**Recommended dashboards:**
- AWS CloudWatch: 11541, 139
- Prometheus: 1860 (Node Exporter)
- Docker: 893

### 4. Configure Alerting

1. **Alerting** > **Contact points**
2. Add notification channels (Slack, Email, PagerDuty)
3. Create alert rules on dashboards

---

## Troubleshooting

### Grafana won't start

**Check logs:**
```bash
# Fargate
aws logs tail /aws/ecs/grafana --follow

# EC2
ssh ec2-user@instance 'tail -f /var/log/grafana/grafana.log'
```

### OIDC login fails

1. Verify `GF_SERVER_ROOT_URL` matches actual URL
2. Check Cognito callback URLs
3. Verify OAuth client configuration

### Dashboards not loading

1. Check data source connectivity
2. Verify IAM permissions for CloudWatch
3. Check network security groups

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Grafana Documentation](https://grafana.com/docs/)
