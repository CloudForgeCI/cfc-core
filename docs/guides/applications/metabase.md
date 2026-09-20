# Metabase Application Guide

Metabase is an open-source business intelligence tool for querying data and building dashboards.

**Status**: Verified (deployed and exercised end to end by maintainers)

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
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `application-oidc` (SAML, default), `alb-oidc`, `none` |
| **Database Required** | No (H2 by default; PostgreSQL optional) |

---

## Upstream Features

- Visual query builder and SQL editor
- Dashboards, alerts, and scheduled reports
- Connectors for many databases
- Query result caching

CloudForge deploys the `metabase/metabase-enterprise` image. Without a license token it runs with open-source features only; SAML, advanced permissions, and audit logging require a Metabase Pro or Enterprise license.

---

## Optional Ports

Metabase does not have optional ports. All traffic flows through port 3000.

---

## Database Configuration

Metabase can use two types of databases:

### 1. Application Database (Metadata Storage)

Stores Metabase configuration, questions, dashboards, and users.

Without `provisionDatabase`, Metabase uses an embedded H2 database at `/metabase-data/metabase.db`, which supports a single instance only. With `provisionDatabase`, CloudForge provisions PostgreSQL:

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 15 or later |
| Instance Class | `db.t3.small` (default) |
| Storage | 20 GB (default) |
| Database Name | `metabase` |

### 2. Data Sources (Analytics Data)

Separate databases containing your business data that Metabase queries. Configure these in the Metabase admin UI after deployment.

---

## Authentication

### Supported Auth Modes

| Mode | Description |
|------|-------------|
| `application-oidc` | Metabase SAML configuration (`MB_SAML_*`); requires a Metabase Pro or Enterprise license |
| `alb-oidc` | The load balancer authenticates users before requests reach Metabase |
| `none` | Metabase local accounts only |

### Authentication Notes

Metabase does not support OpenID Connect natively. The options are:

1. **`alb-oidc`:** the load balancer authenticates users. No Metabase license is required.
2. **`application-oidc`:** CloudForge sets Metabase's SAML variables. This requires a Metabase license, and the SAML-based integrations are incomplete (see [OIDC Integration](../../applications/OIDC.md)).

`application-oidc` is listed first in Metabase's supported modes, so it is the recommended mode the interactive deployer offers. Choose `alb-oidc` unless you have a license and have validated the SAML setup.

### ALB-OIDC Details

With `authMode: "alb-oidc"`, authentication happens at the load balancer. Metabase does not read the load balancer's identity headers, so users still sign in to Metabase with their Metabase accounts.

---

## Environment Variables

CloudForge sets these environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `MB_SITE_URL` | External URL (set when an FQDN is configured) | `https://analytics.example.com` |
| `MB_JETTY_HOST` | Bind address | `0.0.0.0` |
| `MB_DB_TYPE` | Application database type | `postgres` or `h2` |
| `MB_DB_FILE` | H2 database file (without RDS) | `/metabase-data/metabase.db` |
| `MB_DB_HOST` | Database host | RDS endpoint |
| `MB_DB_PORT` | Database port | `5432` |
| `MB_DB_DBNAME` | Database name | `metabase` |
| `MB_DB_USER` | Database user | `metabase` |
| `MB_DB_PASS` | Database password | Injected via ECS secret |

Metabase reads a license token from `MB_PREMIUM_EMBEDDING_TOKEN`. CloudForge does not currently create or inject a license secret; see Enterprise Features.

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

### Development

Metabase with the embedded H2 database.

```json
{
  "stackName": "Metabase-Dev",
  "applicationId": "metabase",
  "applicationName": "Metabase Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

H2 does not support multiple instances or auto scaling.


### Development with ALB Authentication

```json
{
  "stackName": "Metabase-Dev-Auth",
  "applicationId": "metabase",
  "applicationName": "Metabase Dev",
  "environment": "dev",

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

### Staging with PostgreSQL

RDS stores Metabase's application data.

```json
{
  "stackName": "Metabase-Staging",
  "applicationId": "metabase",
  "applicationName": "Metabase Staging",
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
  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

### Production with SOC 2 Controls

```json
{
  "stackName": "Metabase-Production",
  "applicationId": "metabase",
  "applicationName": "Metabase Analytics",
  "environment": "prod",

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

### Production in an EU Region

The same configuration deployed to `eu-west-1`. GDPR obligations depend on your data and processes; see the Compliance Considerations section.

```json
{
  "stackName": "Metabase-EU",
  "applicationId": "metabase",
  "applicationName": "Metabase Analytics EU",
  "environment": "prod",

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

### Production with PCI DSS Controls

An Aurora PostgreSQL database with longer backup retention.

```json
{
  "stackName": "Metabase-Fintech",
  "applicationId": "metabase",
  "applicationName": "Metabase Financial Analytics",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "secure.example.com",
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

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/` | Health check endpoint |
| Grace Period | 300 seconds | Time before health checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |
| Healthy Threshold | 2 | Consecutive successes |
| Unhealthy Threshold | 3 | Consecutive failures |

---

## Compliance Considerations

Setting `complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for those frameworks. It does not certify the deployment.

### SOC 2

**Infrastructure controls CloudForge can configure:**
- Encryption at rest (EBS, EFS, RDS) and in transit (TLS)
- Network isolation with security groups
- CloudWatch logging
- Database backup retention

**Controls you configure in Metabase:**
- Data source and collection permissions
- Row-level security (a licensed feature)
- Audit logging (a licensed feature)

### GDPR

Controls you configure in Metabase and your processes:
- Data retention policies
- User data export
- Records of processing activities
- Data subject access requests

### PCI DSS

Controls you configure in Metabase and your processes:
- Restricted access to cardholder data
- Query logging
- Masking of sensitive fields
- Documented data flows

---

## Post-Deployment Tasks

### 1. Initial Setup

After deployment:

1. Open `https://analytics.example.com` (your configured FQDN).
2. With `alb-oidc`, authenticate with Cognito first.
3. Complete Metabase's setup wizard, which creates the first administrator account.

### 2. Configure Data Sources

1. Go to **Admin** > **Databases**.
2. Choose **Add database**.
3. Select the database type.
4. Enter the connection details.

**Example PostgreSQL connection:**
```
Host: your-rds-endpoint.region.rds.amazonaws.com
Port: 5432
Database: your_database
Username: analyst_user
Password: ********
```

### 3. Create Questions and Dashboards

1. Choose **New** > **Question**.
2. Select a data source.
3. Use the query builder or SQL.
4. Save the question to a collection.

### 4. Set Up Permissions

1. In **Admin** > **People**, create groups.
2. In **Admin** > **Permissions**, configure data access per group.

### 5. Configure Caching (Optional)

In **Admin** > **Performance** (or **Caching** in older versions), configure query result caching.

---

## Troubleshooting

### Metabase won't start

**Check logs:**
```bash
# Fargate (log group name when storage is not retained; otherwise find the
# stack's log group in the CloudWatch console)
aws logs tail /aws/ecs/<stack-name>/fargate/<security-profile> --follow

# EC2 (via SSM Session Manager)
aws ssm start-session --target <instance-id>
# then: tail -f /opt/metabase/logs/metabase.log
```

### Database connection fails (metadata DB)

1. Verify the security group allows port 5432 from the application.
2. Check the `MB_DB_HOST` value against the RDS endpoint.
3. Verify the database credentials in Secrets Manager.

### Data source connection fails

1. Ensure security groups allow the outbound connection.
2. Check the data source credentials.
3. Test the connection from the Metabase admin UI.

### Slow queries

1. Enable query caching.
2. Check database indexes.
3. Use native queries for complex analytics.
4. Consider read replicas for data sources.

### SSO issues with ALB-OIDC

1. Verify the Cognito domain prefix is globally unique.
2. Check the ALB listener rules.
3. Check the Cognito app client's callback URLs.

---

## Enterprise Features

A Metabase Pro or Enterprise license enables SAML SSO, advanced permissions, audit logging, and other features. Metabase reads the token from the `MB_PREMIUM_EMBEDDING_TOKEN` environment variable, or you can enter it in **Admin** > **Settings** > **License**.

`MetabaseApplicationSpec` defines a secret name (`<stack name>/metabase/license-token`) and the variable name, but CloudForge does not currently create that secret or inject it into the container. Enter the token in the admin UI instead.

---

## Related Documentation

- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Compliance Guide](../../compliance/README.md)
- [Deployment Context Reference](../../examples/README.md)
- [Metabase Documentation](https://www.metabase.com/docs/latest/)
