# Harbor Application Guide

Harbor is an open-source container registry that secures artifacts with policies and role-based access control, scans images for vulnerabilities, and signs images as trusted.

**Status**: Available (Not Yet Tested)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `harbor` |
| **Category** | Artifact Registry |
| **Default Image** | `goharbor/harbor-core:v2.9.0` |
| **Application Port** | `80` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Default Instance** | t3.medium (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **OIDC Support** | No (use ALB-OIDC) |
| **Database Required** | Yes (PostgreSQL) |

---

## Deployment Architecture

The following AWS architecture diagram shows the complete Harbor deployment:

```mermaid
graph TB
    Internet[🌐 Internet] --> IGW[🌉 Internet Gateway]
    
    IGW --> ALB[⚖️ Application Load Balancer]
    IGW --> NAT[🌉 NAT Gateway]
    
    ALB --> ECS1[☁️ ECS Fargate / EC2<br/>Harbor Container 1]
    ALB --> ECS2[☁️ ECS Fargate / EC2<br/>Harbor Container 2]
    
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

## Authentication Flow

Harbor uses ALB-OIDC for UI access and Docker/OCI credentials for registry access:

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant ALB
    participant Cognito
    participant Harbor
    participant Docker
    
    Note over User,Harbor: UI Access (ALB-OIDC)
    User->>ALB: Navigate to Harbor
    ALB->>ALB: Check session cookie
    ALB->>User: No session, redirect to Cognito
    
    User->>Cognito: Enter credentials
    Cognito->>User: Request MFA
    User->>Cognito: Provide MFA code
    Cognito->>User: Return authorization code
    
    User->>ALB: Callback with code
    ALB->>Cognito: Exchange code for tokens
    Cognito->>ALB: Return ID token + access token
    
    ALB->>ALB: Validate token & create session
    ALB->>Harbor: Forward with headers
    Harbor->>User: Return Harbor UI
    
    Note over Docker,Harbor: Registry Access (Docker Login)
    Docker->>Harbor: docker login
    Harbor->>Docker: Request credentials
    Docker->>Harbor: Provide username/password
    Harbor->>Harbor: Validate credentials
    Harbor->>Docker: Return JWT token
    Docker->>Harbor: Use token for operations
```

## Architecture Diagram

The following diagram shows the Harbor deployment architecture:

```mermaid
graph TB
    subgraph "Internet"
        User[User Browser]
        Docker[Docker Client]
        K8s[Kubernetes Cluster]
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
            Task1[Harbor Container 1]
            Task2[Harbor Container 2]
        end
        
        subgraph "EC2 Auto Scaling (EC2 Runtime)"
            ASG[Auto Scaling Group]
            Instance1[EC2 Instance 1]
            Instance2[EC2 Instance 2]
        end
        
        subgraph "Database"
            RDS[(PostgreSQL Database)]
            DBInstance[db.t3.medium]
        end
        
        subgraph "Storage"
            EFS[Elastic File System]
            Data["Harbor Data Path<br/>/data"]
            Registry[Registry Storage]
        end
        
        subgraph "Optional Services"
            Notary[Notary Service Port 4443]
            Trivy[Trivy Scanner Port 8080]
        end
        
        subgraph "CloudWatch"
            Logs[Application Logs]
            Metrics[CloudWatch Metrics]
        end
        
        subgraph "Security"
            SG[Security Groups]
            WAF[WAF Rules]
        end
    end
    
    User -->|HTTPS UI| DNS
    Docker -->|HTTPS Registry| DNS
    K8s -->|HTTPS Registry| DNS
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
    
    Task1 --> Notary
    Task1 --> Trivy
    Task2 --> Notary
    Task2 --> Trivy
    
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
    style Docker fill:#e1f5ff
    style K8s fill:#e1f5ff
    style Cognito fill:#fff4e1
    style RDS fill:#e8f5e9
    style EFS fill:#e8f5e9
    style CloudWatch fill:#f3e5f5
    style Notary fill:#fff9c4
    style Trivy fill:#fff9c4
```

---

## Capabilities

- Container image registry
- Image vulnerability scanning (Trivy)
- Content trust with image signing (Notary)
- Role-based access control
- Image replication across registries
- Garbage collection
- Audit logging
- Multi-tenancy with projects
- Helm chart repository
- OCI artifact support

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 4443 | TCP | Inbound | `enableNotary` | Content Trust (Notary) |
| 8080 | TCP | Inbound | `enableTrivy` | Trivy Scanner |

**Example enabling security features:**
```json
{
  "enableNotary": true,
  "enableTrivy": true
}
```

---

## Database Requirements

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 13+ |
| Instance Class | db.t3.medium (default) |
| Storage | 50 GB (default) |
| Database Name | `registry` |
| Backup Retention | 30 days |

**Database Parameters:**
- `max_connections`: 250
- `shared_buffers`: Optimized

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | Available | ALB-level authentication |
| `none` | Available | Local accounts only |

**Note:** Harbor has built-in OIDC support, but CloudForge integration is pending. Use ALB-OIDC for SSO.

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `HARBOR_HOSTNAME` | External hostname |
| `HARBOR_EXTERNAL_URL` | Full external URL |
| `POSTGRESQL_*` | Database connection |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/data` |
| EFS Path | `/harbor` |
| Volume Name | `harborData` |
| Container User | `10000:10000` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/data/harbor` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Harbor-Dev",
  "applicationId": "harbor",
  "applicationName": "Harbor Dev",
  "description": "Harbor development registry",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 50,
  "databaseName": "registry",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production - With Security Scanning

```json
{
  "stackName": "Harbor-Production",
  "applicationId": "harbor",
  "applicationName": "Harbor Registry",
  "description": "Production container registry",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "registry",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "harbor-prod-yourcompany",
  "cognitoMfaEnabled": true,

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "registry",
  "databaseBackupRetentionDays": 30,

  "enableNotary": true,
  "enableTrivy": true,

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

**Cost estimate:** ~$500/month

---

## Compliance Use Cases

- **SOC2**: Container image provenance and audit trails
- **PCI-DSS**: Secure storage of payment processing containers
- **HIPAA**: Vulnerability scanning for healthcare containers

---

## Post-Deployment Tasks

1. **Initial Login**: Navigate to Harbor URL, default: `admin` / `Harbor12345`
2. **Change Admin Password**: Immediately change default password
3. **Create Projects**: Organize images by team/application
4. **Configure Scanning**: Enable Trivy scanning policies
5. **Set Up Replication**: Configure replication to/from other registries

---

## Related Documentation

- [Harbor Documentation](https://goharbor.io/docs/)
- [Compliance Guide](../../compliance/README.md)
