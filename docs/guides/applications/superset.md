# Superset Application Guide

Apache Superset is a modern data exploration and visualization platform that enables users to explore and visualize their data from simple charts to highly detailed dashboards.

**Status**: Verified (Deployment tested)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `superset` |
| **Category** | Analytics |
| **Default Image** | `apache/superset:latest` |
| **Application Port** | `8088` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **OIDC Support** | `alb-oidc` only (application-level not implemented) |
| **Database Required** | Yes (PostgreSQL) |

---

## Deployment Architecture

The following AWS architecture diagram shows the complete Superset deployment:

```mermaid
graph TB
    Internet[🌐 Internet] --> IGW[🌉 Internet Gateway]
    
    IGW --> ALB[⚖️ Application Load Balancer]
    IGW --> NAT[🌉 NAT Gateway]
    
    ALB --> ECS1[☁️ ECS Fargate / EC2<br/>Superset Container 1]
    ALB --> ECS2[☁️ ECS Fargate / EC2<br/>Superset Container 2]
    
    NAT --> ECS1
    NAT --> ECS2
    
    ECS1 --> RDS[(🗄️ Amazon RDS PostgreSQL)]
    ECS2 --> RDS
    
    ECS1 --> EFS[(💾 Amazon EFS)]
    ECS2 --> EFS
    
    ALB --> Cognito[🔐 AWS Cognito]
    
    ECS1 --> CloudWatch[📊 CloudWatch Logs]
    ECS2 --> CloudWatch
```

## Architecture Diagram

The following diagram shows the Superset deployment architecture:

```mermaid
graph TB
    subgraph "Internet"
        User[User Browser]
    end
    
    subgraph "AWS Cloud"
        subgraph "Route 53"
            DNS[DNS Record]
        end
        
        subgraph "Certificate Manager"
            Cert[SSL Certificate]
        end
        
        subgraph "Cognito"
            UserPool[User Pool]
            AppClient[App Client]
        end
        
        subgraph "Application Load Balancer"
            ALB[ALB with OIDC Listener]
            TargetGroup[Target Group]
            AuthAction[OIDC Authentication Action]
        end
        
        subgraph "ECS Cluster (Fargate)"
            Service[ECS Service]
            Task1[Superset Container 1]
            Task2[Superset Container 2]
        end
        
        subgraph "EC2 Auto Scaling (EC2 Runtime)"
            ASG[Auto Scaling Group]
            Instance1[EC2 Instance 1]
            Instance2[EC2 Instance 2]
        end
        
        subgraph "Database"
            RDS[(PostgreSQL Database)]
            DBInstance[db.t3.small]
        end
        
        subgraph "Storage"
            EFS[Elastic File System]
            Data["Superset Data Path<br/>/app/superset_home"]
        end
        
        subgraph "CloudWatch"
            Logs[Application Logs]
            Metrics[CloudWatch Metrics]
        end
        
        subgraph "Data Sources"
            DataSource1[PostgreSQL]
            DataSource2[MySQL]
            DataSource3[Redshift]
            DataSource4[Other Sources]
        end
        
        subgraph "Security"
            SG[Security Groups]
            WAF[WAF Rules]
        end
    end
    
    User -->|HTTPS| DNS
    DNS --> ALB
    Cert --> ALB
    ALB --> AuthAction
    AuthAction --> Cognito
    AuthAction --> TargetGroup
    
    TargetGroup --> Service
    TargetGroup --> ASG
    
    Service --> Task1
    Service --> Task2
    ASG --> Instance1
    ASG --> Instance2
    
    Task1 --> EFS
    Task2 --> EFS
    Instance1 --> EFS
    Instance2 --> EFS
    
    Task1 --> RDS
    Task2 --> RDS
    Instance1 --> RDS
    Instance2 --> RDS
    
    Task1 --> DataSource1
    Task1 --> DataSource2
    Task1 --> DataSource3
    Task1 --> DataSource4
    
    Task1 --> Logs
    Task2 --> Logs
    Instance1 --> Logs
    Instance2 --> Logs
    
    Task1 --> Metrics
    Task2 --> Metrics
    
    UserPool --> AppClient
    AppClient --> AuthAction
    
    SG --> ALB
    SG --> Service
    SG --> ASG
    SG --> RDS
    WAF --> ALB
    
    style User fill:#e1f5ff
    style Cognito fill:#fff4e1
    style RDS fill:#e8f5e9
    style EFS fill:#e8f5e9
    style CloudWatch fill:#f3e5f5
    style DataSource1 fill:#fff9c4
    style DataSource2 fill:#fff9c4
    style DataSource3 fill:#fff9c4
    style DataSource4 fill:#fff9c4
```

---

## Capabilities

- SQL-based data exploration
- Rich visualizations (40+ chart types)
- Dashboard creation
- SQL Lab for ad-hoc queries
- Role-based access control
- Database connectivity (30+ databases)
- Caching with Redis
- Alerting and reports
- No-code chart builder
- Semantic layer

---

## Database Requirements

Superset **requires** a PostgreSQL (or MySQL) database for metadata storage.

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 13+ |
| Instance Class | db.t3.small (default) |
| Storage | 20 GB (default) |
| Database Name | `superset` |
| Backup Retention | 14 days |

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | ✅ Verified | ALB-level authentication (Recommended) |
| `application-oidc` | ❌ Not Implemented | Requires custom `superset_config.py` configuration |
| `none` | ✅ Available | Local accounts only |

### Authentication Notes

**Application-level OIDC (`application-oidc`) is not implemented:**
- Apache Superset supports OIDC via custom `superset_config.py` configuration
- CloudForge does not currently auto-configure Superset's OIDC integration
- **Workaround:** Use `alb-oidc` mode for SSO authentication (works correctly)

**ALB-OIDC Details:**
When using `authMode: "alb-oidc"`:
- Authentication happens at the load balancer
- Users are automatically created in Superset on first access
- User email is passed from Cognito to Superset
- No additional Superset configuration required

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `SUPERSET_SECRET_KEY` | Session encryption key (required) |
| `ENABLE_PROXY_FIX` | Enable ALB proxy support |
| `DATABASE_DIALECT` | `postgresql` |
| `DATABASE_HOST` | RDS endpoint |
| `SQLALCHEMY_DATABASE_URI` | Full connection string |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/app/superset_home` |
| EFS Path | `/superset` |
| Volume Name | `supersetData` |
| Container User | `0:0` (root) |
| EFS Permissions | `755` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Superset-Dev",
  "applicationId": "superset",
  "applicationName": "Superset Dev",
  "description": "Superset development environment",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseName": "superset",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$70/month

### Production

```json
{
  "stackName": "Superset-Production",
  "applicationId": "superset",
  "applicationName": "Superset Analytics",
  "description": "Production data exploration platform",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "data",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "superset-prod-yourcompany",
  "cognitoMfaEnabled": true,

  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,
  "databaseName": "superset",
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

**Cost estimate:** ~$350/month

---

## Post-Deployment Tasks

1. **Initialize Database:**
   ```bash
   superset db upgrade
   ```
2. **Create Admin User:**
   ```bash
   superset fab create-admin
   ```
3. **Load Examples (optional):**
   ```bash
   superset load_examples
   ```
4. **Initialize Superset:**
   ```bash
   superset init
   ```
5. **Connect Data Sources** in the UI

---

## Compliance Use Cases

- **SOC2**: Security event analytics and metrics
- **GDPR**: Data subject rights request tracking
- **PCI-DSS**: Transaction monitoring dashboards
- **Fintech**: Real-time payment dashboards, fraud detection

---

## Related Documentation

- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Superset Documentation](https://superset.apache.org/docs/intro)
