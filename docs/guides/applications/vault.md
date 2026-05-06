# Vault Application Guide

HashiCorp Vault is a tool for securely storing and accessing secrets, providing encryption as a service, and managing access to secrets and systems.

**Status**: Available (Not Yet Tested)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `vault` |
| **Category** | Secrets Management |
| **Default Image** | `hashicorp/vault:latest` |
| **Application Port** | `8200` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes (recommended) |
| **OIDC Support** | No (use ALB-OIDC) |
| **Database Required** | No |

---

## Deployment Architecture

The following AWS architecture diagram shows the complete Vault deployment:

```mermaid
graph TB
    Internet[🌐 Internet] --> IGW[🌉 Internet Gateway]
    
    IGW --> ALB[⚖️ Application Load Balancer]
    IGW --> NAT[🌉 NAT Gateway]
    
    ALB --> ECS1[☁️ ECS Fargate / EC2<br/>Vault Container 1]
    ALB --> ECS2[☁️ ECS Fargate / EC2<br/>Vault Container 2]
    
    NAT --> ECS1
    NAT --> ECS2
    
    ECS1 --> EFS[(💾 Amazon EFS)]
    ECS2 --> EFS
    
    ALB --> Cognito[🔐 AWS Cognito]
    
    ECS1 --> CloudWatch[📊 CloudWatch Logs]
    ECS2 --> CloudWatch
```

## Authentication Flow

Vault uses ALB-OIDC for UI access and Vault tokens for API access:

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant ALB
    participant Cognito
    participant Vault
    
    Note over User,Vault: UI Access (ALB-OIDC)
    User->>ALB: Navigate to Vault
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
    ALB->>Vault: Forward with headers
    Vault->>User: Return Vault UI
    
    Note over User,Vault: API Access (Vault Tokens)
    User->>Vault: API request with token
    Vault->>Vault: Validate token
    Vault->>User: Return API response
```

## Architecture Diagram

The following diagram shows the Vault deployment architecture:

```mermaid
graph TB
    subgraph "Internet"
        User[User Browser]
        API[API Client]
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
            Task1[Vault Container 1]
            Task2[Vault Container 2]
        end
        
        subgraph "EC2 Auto Scaling (EC2 Runtime)"
            ASG[Auto Scaling Group]
            Instance1[EC2 Instance 1]
            Instance2[EC2 Instance 2]
        end
        
        subgraph "Storage"
            EFS[Elastic File System]
            Data["Vault Data Path<br/>/vault/file"]
            VaultStorage[Vault Storage Backend]
        end
        
        subgraph "CloudWatch"
            Logs[Application Logs]
            Metrics[CloudWatch Metrics]
        end
        
        subgraph "Security"
            SG[Security Groups]
            WAF[WAF Rules]
        end
        
        subgraph "Vault Secrets"
            Secrets[Secrets Engine]
            Policies[Access Policies]
            Audit[Audit Logs]
        end
    end
    
    User -->|HTTPS UI| DNS
    API -->|HTTPS API| DNS
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
    
    Task1 --> Secrets
    Task2 --> Secrets
    Task1 --> Policies
    Task2 --> Policies
    Task1 --> Audit
    Task2 --> Audit
    
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
    WAF --> ALB
    
    style User fill:#e1f5ff
    style API fill:#e1f5ff
    style Cognito fill:#fff4e1
    style EFS fill:#e8f5e9
    style CloudWatch fill:#f3e5f5
    style Secrets fill:#ffebee
    style Policies fill:#ffebee
```

---

## Capabilities

- Secret storage and management
- Dynamic secrets generation
- Encryption as a service
- PKI/certificate authority
- Database credential rotation
- AWS IAM credential management
- Kubernetes secrets sync
- Audit logging
- Access policies

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/vault/file` |
| EFS Path | `/vault` |
| Volume Name | `vaultData` |
| Container User | `100:1000` |
| EFS Permissions | `750` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/opt/vault/data` |
| Log Paths | `/var/log/vault/vault.log`, `/var/log/vault/audit.log` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Vault-Dev",
  "applicationId": "vault",
  "applicationName": "Vault Dev",
  "description": "Vault development secrets manager",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$50/month

### Production - With Auto-Unseal

For production, EC2 with KMS auto-unseal is recommended:

```json
{
  "stackName": "Vault-Production",
  "applicationId": "vault",
  "applicationName": "Vault",
  "description": "Production secrets management",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "vault",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "vault-prod-yourcompany",
  "cognitoMfaEnabled": true,

  "instanceType": "t3.small",
  "minInstanceCapacity": 3,
  "maxInstanceCapacity": 5,

  "complianceFrameworks": "SOC2,PCI-DSS",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$300/month

---

## Important Notes

### Initialization and Unsealing

Vault requires **manual initialization** after first deployment:

1. **Initialize Vault:**
   ```bash
   vault operator init
   ```
   Save the unseal keys and root token securely!

2. **Unseal Vault:**
   ```bash
   vault operator unseal <key1>
   vault operator unseal <key2>
   vault operator unseal <key3>
   ```

### Production Recommendations

- Use **KMS auto-unseal** to avoid manual unsealing
- Deploy **minimum 3 instances** for HA
- Enable **audit logging** for compliance
- Use **Integrated Storage (Raft)** for clustering
- Store unseal keys in **separate secure locations**

---

## Compliance Use Cases

- **PCI-DSS**: Payment gateway API key storage
- **HIPAA**: PHI encryption key management
- **SOC2**: Centralized secrets and audit trails
- **GDPR**: Data encryption key rotation

---

## Related Documentation

- [Compliance Guide](../../compliance/README.md)
- [Vault Documentation](https://developer.hashicorp.com/vault/docs)
