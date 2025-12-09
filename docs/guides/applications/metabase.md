# Metabase Application Guide

Metabase is an open-source business intelligence and analytics platform that enables non-technical users to ask questions about their data and visualize the answers.

**Status**: Verified

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `metabase` |
| **Category** | Analytics |
| **Default Image** | `metabase/metabase-enterprise:latest` |
| **Application Port** | `3000` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/api/health` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **OIDC Support** | Via SAML (Verified) |
| **Database Required** | Optional (recommended for production) |

---

## Capabilities

- Self-service business intelligence
- SQL and visual query builder
- Interactive dashboards
- Automated reports and alerts
- Embedded analytics
- Data exploration with filters
- Support for 20+ data sources
- Question sharing and collaboration
- Row-level security
- Cached queries

**Note:** The Enterprise Edition image runs in "Open Source" mode without a license. Enterprise features (SAML, advanced permissions, audit logging) require a license token.

---

## Optional Ports

Metabase does not have optional ports. All traffic flows through port 3000.

---

## Database Configuration

Metabase can use two types of databases:

### 1. Application Database (Metadata Storage)

Stores Metabase configuration, questions, dashboards, and users.

**Development:** H2 embedded database (single instance only)
**Production:** PostgreSQL or MySQL

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 15+ (recommended) |
| Instance Class | db.t3.small (default) |
| Storage | 20 GB (default) |
| Database Name | `metabase` |

### 2. Data Sources (Analytics Data)

Separate databases containing your business data that Metabase queries. Configure these in the Metabase admin UI after deployment.

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | **Verified** | ALB-level authentication |
| `application-oidc` | Via SAML | Requires Enterprise license for native SAML |
| `none` | Available | Local accounts only |

### Authentication Notes

**Important:** Metabase does **not** support native OpenID Connect. For SSO:

1. **ALB-OIDC (Recommended):** Use ALB-level authentication, which works without Metabase Enterprise license
2. **SAML (Enterprise):** Requires Metabase Pro/Enterprise license and uses SAML 2.0

For most deployments, **ALB-OIDC** provides the best balance of security and simplicity without requiring a license.

### ALB-OIDC Details

When using `authMode: "alb-oidc"`:
- Authentication happens at the load balancer
- Users are automatically created in Metabase on first access
- User email is passed from Cognito to Metabase
- No additional Metabase configuration required

---

## Environment Variables

CloudForge automatically configures these environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `MB_SITE_URL` | External URL (critical for OAuth) | `https://analytics.example.com` |
| `MB_JETTY_HOST` | Bind address | `0.0.0.0` |
| `MB_DB_TYPE` | Application database type | `postgres` or `h2` |
| `MB_DB_HOST` | Database host | RDS endpoint |
| `MB_DB_PORT` | Database port | `5432` |
| `MB_DB_DBNAME` | Database name | `metabase` |
| `MB_DB_USER` | Database user | `metabase` |
| `MB_DB_PASS` | Database password | Injected via ECS secret |

**Enterprise License (if provided):**
| Variable | Description |
|----------|-------------|
| `MB_PREMIUM_EMBEDDING_TOKEN` | License token for Enterprise features |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/metabase-data` |
| EFS Path | `/metabase` |
| Volume Name | `metabaseData` |
| Container User | `2000:2000` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/opt/metabase/data` |
| Log Paths | `/opt/metabase/logs/metabase.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal Setup

Quick Metabase for testing with embedded H2 database.

```json
{
  "stackName": "Metabase-Dev",
  "applicationId": "metabase",
  "applicationName": "Metabase Dev",
  "description": "Metabase development environment",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Warning:** H2 database doesn't support multiple instances or auto-scaling.

**Cost estimate:** ~$35/month

### Development - With Authentication

Metabase with ALB-OIDC for team access.

```json
{
  "stackName": "Metabase-Dev-Auth",
  "applicationId": "metabase",
  "applicationName": "Metabase Dev",
  "description": "Metabase with Cognito authentication",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "domain": "dev.example.com",
  "subdomain": "analytics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-dev-yourcompany",
  "cognitoCreateGroups": true,

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "30"
}
```

**Cost estimate:** ~$90/month

### Staging - With PostgreSQL Database

Pre-production with RDS for metadata storage.

```json
{
  "stackName": "Metabase-Staging",
  "applicationId": "metabase",
  "applicationName": "Metabase Staging",
  "description": "Metabase staging with PostgreSQL",
  "environment": "staging",

  "runtime": "fargate",
  "securityProfile": "staging",
  "topology": "application-service",

  "domain": "staging.example.com",
  "subdomain": "analytics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-staging-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,

  "cpu": 1024,
  "memory": 2048,
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 20,
  "databaseName": "metabase",
  "databaseBackupRetentionDays": 7,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": true,
  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

**Cost estimate:** ~$170/month

### Production - SOC2 Compliance

Full production deployment for business analytics.

```json
{
  "stackName": "Metabase-Production",
  "applicationId": "metabase",
  "applicationName": "Metabase Analytics",
  "description": "Production Metabase with SOC2 compliance",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "analytics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "MetabaseAdmins",
  "cognitoUserGroupName": "MetabaseUsers",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,
  "databaseName": "metabase",
  "databaseBackupRetentionDays": 30,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": false,
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$400/month

### Production - GDPR (EU Data)

For European organizations with GDPR requirements.

```json
{
  "stackName": "Metabase-EU",
  "applicationId": "metabase",
  "applicationName": "Metabase Analytics EU",
  "description": "Metabase for EU data with GDPR compliance",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "eu.example.com",
  "subdomain": "analytics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "eu-west-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-eu-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,

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
  "databaseName": "metabase",
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

**Cost estimate:** ~$400/month

### Production - Fintech (PCI-DSS)

For financial analytics with payment data.

```json
{
  "stackName": "Metabase-Fintech",
  "applicationId": "metabase",
  "applicationName": "Metabase Financial Analytics",
  "description": "PCI-DSS compliant analytics platform",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "secure.fintech.com",
  "subdomain": "analytics",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-fintech-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 50,

  "provisionDatabase": true,
  "databaseEngine": "aurora-postgresql",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.r5.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "metabase",
  "databaseBackupRetentionDays": 90,

  "complianceFrameworks": "PCI-DSS,SOC2",
  "scopeConfigRulesToDeployment": false,
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$700/month

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/api/health` | Health check endpoint |
| Grace Period | 300 seconds | Time before health checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |
| Healthy Threshold | 2 | Consecutive successes |
| Unhealthy Threshold | 3 | Consecutive failures |

---

## Compliance Considerations

### SOC2

**Automatic Controls:**
- Encryption at rest (EBS/EFS/RDS)
- Encryption in transit (TLS)
- Network isolation (Security Groups)
- CloudWatch logging
- Database backup retention

**Use Cases:**
- Audit log analytics
- Security metrics dashboards
- Compliance reporting

**User Responsibilities:**
- [ ] Configure data source permissions
- [ ] Enable audit logging (Enterprise)
- [ ] Set up row-level security
- [ ] Configure collection permissions

### GDPR

**User Responsibilities:**
- [ ] Configure data retention policies
- [ ] Enable user data export
- [ ] Document data processing activities
- [ ] Implement data subject access requests

### PCI-DSS

**User Responsibilities:**
- [ ] Restrict access to cardholder data
- [ ] Enable query logging
- [ ] Configure data masking for sensitive fields
- [ ] Document data flows

---

## Post-Deployment Tasks

### 1. Initial Setup

After deployment:

1. Navigate to `https://analytics.your-domain.com`
2. If using ALB-OIDC, authenticate with Cognito
3. First user becomes admin

### 2. Configure Data Sources

1. Go to **Admin** > **Databases**
2. Click **Add database**
3. Select database type (PostgreSQL, MySQL, etc.)
4. Enter connection details

**Example PostgreSQL connection:**
```
Host: your-rds-endpoint.region.rds.amazonaws.com
Port: 5432
Database: your_database
Username: analyst_user
Password: ********
```

### 3. Create Questions and Dashboards

1. Click **New** > **Question**
2. Select data source
3. Use visual query builder or SQL
4. Save and organize in collections

### 4. Set Up Permissions

1. **Admin** > **People**
2. Create groups (Analysts, Viewers, etc.)
3. **Admin** > **Permissions**
4. Configure data access per group

### 5. Configure Caching (Optional)

1. **Admin** > **Caching**
2. Set default caching duration
3. Configure query result caching

---

## Troubleshooting

### Metabase won't start

**Check logs:**
```bash
# Fargate
aws logs tail /aws/ecs/metabase --follow

# EC2
ssh ec2-user@instance 'tail -f /opt/metabase/logs/metabase.log'
```

### Database connection fails (metadata DB)

1. Verify security group allows port 5432
2. Check RDS endpoint in SSM parameters
3. Verify database credentials in Secrets Manager

### Data source connection fails

1. Ensure VPC security groups allow outbound connection
2. Check data source credentials
3. Test connection from Metabase admin UI

### Slow queries

1. Enable query caching
2. Check database indexes
3. Use native queries for complex analytics
4. Consider read replicas for data sources

### SSO issues with ALB-OIDC

1. Verify Cognito domain prefix is globally unique
2. Check ALB listener rules
3. Verify user attributes are passed correctly

---

## Enterprise Features

With a Metabase Pro/Enterprise license:

- **SAML SSO**: Native SAML 2.0 support
- **Advanced Permissions**: Granular access controls
- **Audit Logging**: Track user actions
- **Content Verification**: Mark trusted answers
- **Official Collections**: Verified content organization
- **Embedded Analytics**: White-label embedding

To activate, set the license token in Secrets Manager:
```bash
aws secretsmanager put-secret-value \
  --secret-id Metabase-Production/metabase/license-token \
  --secret-string "your-license-token"
```

---

## Related Documentation

- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Compliance Guide](../compliance/README.md)
- [Deployment Context Reference](../../../deployment-contexts/README.md)
- [Metabase Documentation](https://www.metabase.com/docs/latest/)
