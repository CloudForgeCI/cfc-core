# Database Deployment Guide

How CloudForge provisions Amazon RDS databases for applications, which settings you can override, and how the RDS compliance rules and remediations work.

## Contents

1. [Overview](#overview)
2. [Supported engines](#supported-engines)
3. [Database requirements by application](#database-requirements-by-application)
4. [Configuration](#configuration)
5. [Security defaults](#security-defaults)
6. [Compliance rules and remediation](#compliance-rules-and-remediation)
7. [Implementing DatabaseSpec](#implementing-databasespec)
8. [Troubleshooting](#troubleshooting)
9. [Reference](#reference)

---

## Overview

An application declares its database needs by implementing
[`DatabaseSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/DatabaseSpec.java).
During synthesis, `ApplicationFactory` reads `databaseRequirement()`, merges any deployment-context
overrides, and calls
[`RdsFactory`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/database/RdsFactory.java)
to create:

- an RDS DB instance in the VPC's private subnets (never publicly accessible),
- a Secrets Manager secret holding a generated 32-character password (username `<databaseName>admin`),
- a dedicated security group, a subnet group, and an engine-specific parameter group,
- a customer-managed KMS key (with rotation) when encryption is enabled,
- optional read replicas.

The resulting `DatabaseConnection` (endpoint, port, database name, username, secret ARN) is passed to
the application's `containerEnvironmentVariables(...)` so it can configure its container.

A requirement has one of three types:

| Type | Behavior |
|------|----------|
| `REQUIRED` | A database is always provisioned. |
| `OPTIONAL` | A database is provisioned only when `provisionDatabase` is `true`; otherwise the app uses its embedded store. |
| `NONE` / no `DatabaseSpec` | No database is provisioned. |

---

## Supported engines

`RdsFactory` supports three RDS engines. Aurora values appear in the `databaseEngine` field's allowed
values and in the `DatabaseSpec` javadoc, but `RdsFactory` rejects them with
`Unsupported database engine`.

| Engine (`databaseEngine`) | Versions mapped to CDK constants | Other versions |
|---------------------------|----------------------------------|----------------|
| `postgres` (alias `postgresql`) | 11, 12, 13, 14, 15, 16 | Passed through as-is |
| `mysql` | 5.7, 8.0, 8.0.32–8.0.35 | Passed through as-is |
| `mariadb` | 10.6, 10.11 | Passed through as-is |

MySQL 5.7 is end-of-life; synthesis prints a warning when it is selected.

---

## Database requirements by application

Values come from each application's `databaseRequirement()` in
`cloudforge-api/src/main/java/com/cloudforgeci/api/application/`.

### Required

| Application (`applicationId`) | Engine | Instance class | Storage (GB) | Database name |
|-------------------------------|--------|----------------|--------------|---------------|
| `gitlab` | PostgreSQL 16 | db.t3.medium | 50 | `gitlabhq_production` |
| `harbor` | PostgreSQL 13 | db.t3.medium | 50 | `registry` |
| `mattermost-enterprise`, `mattermost-team` | PostgreSQL 14 | db.t3.small | 30 | `mattermost` |
| `superset` | PostgreSQL 13 | db.t3.small | 20 | `superset` |
| `wordpress` | MySQL 8.0 | db.t3.micro | 20 | `wordpress` |
| `woocommerce` | MySQL 8.0 | db.t3.small | 50 | `woocommerce` |
| `drupal` | MySQL 8.0 | db.t3.micro | 20 | `drupal` |
| `joomla` | MySQL 8.0 | db.t3.micro | 20 | `joomla` |
| `opencart` | MySQL 8.0 | db.t3.micro | 20 | `opencart` |
| `moodle` | MySQL 8.0 | db.t3.small | 50 | `moodle` |
| `bagisto` | MySQL 8.0 | db.t3.small | 50 | `bagisto` |
| `prestashop` | MySQL 8.0 | db.t3.small | 50 | `prestashop` |
| `suitecrm` | MySQL 8.0 | db.t3.small | 50 | `suitecrm` |
| `sylius` | MySQL 8.0 | db.t3.small | 50 | `sylius` |
| `typo3` | MySQL 8.0 | db.t3.small | 50 | `typo3` |
| `magento` | MySQL 8.0 | db.r6g.large | 100 | `magento` |
| `concrete-cms` | MySQL 5.7 | db.t3.micro | 20 | `concrete` |
| `dolphin-una` | MySQL 5.7 | db.t3.small | 50 | `una` |
| `flarum` | MySQL 5.7 | db.t3.micro | 20 | `flarum` |
| `mediawiki` | MySQL 5.7 | db.t3.micro | 20 | `mediawiki` |
| `mybb` | MySQL 5.7 | db.t3.micro | 20 | `mybb` |
| `october-cms` | MySQL 5.7 | db.t3.micro | 20 | `october` |
| `phpbb` | MySQL 5.7 | db.t3.micro | 20 | `phpbb` |

CMS applications also declare the engines they can run on through `@CmsPlugin(supportedDatabases = ...)`;
see [CMS applications](../applications/CMS.md).

### Optional

| Application | RDS engine | Instance class | Embedded fallback |
|-------------|------------|----------------|-------------------|
| `metabase` | PostgreSQL 15 | db.t3.small | H2 |
| `grafana` | PostgreSQL 14 | db.t3.micro | SQLite |

Embedded H2 and SQLite are file-based and do not support more than one running task. Provision RDS
before scaling these applications beyond one instance.

### None

`jenkins`, `gitea`, `drone`, `nexus`, `sonarqube`, `prometheus`, `vault`, `redis`, and `postgresql`
do not implement `DatabaseSpec` and never get an RDS instance.

---

## Configuration

Database settings are deployment-context keys (for example in `deployment-context.json` or the `cfc`
context in `cdk.json`). Every override is optional; unset values fall back to the application's
`databaseRequirement()` or the security-profile default.

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `provisionDatabase` | boolean | `false` | Enables RDS for `OPTIONAL` applications. Also gates the RDS AWS Config rules (see below). |
| `databaseEngine` | string | from app | `postgres`, `mysql`, `mariadb`. |
| `databaseVersion` | string | from app | For example `15` or `8.0`. |
| `databaseInstanceClass` | string | from app | Allowed: db.t3.micro/small/medium/large, db.m5.large/xlarge/2xlarge, db.r5.large/xlarge/2xlarge. Changing it replaces the instance. |
| `databaseAllocatedStorageGB` | integer | from app | 20–65536. Storage autoscaling is capped at twice this value. |
| `databaseName` | string | from app | Immutable after creation. |
| `databaseBackupRetentionDays` | integer | profile default | 0–35. |
| `databaseMultiAz` | boolean | profile default | Doubles instance cost. |
| `databaseReadReplicaCount` | integer | app default (usually 0) | 0–5. `0` disables replicas. |
| `enableEncryption` | boolean | `true` | Storage encryption with a dedicated KMS key. |

Example (`deployment-context.json`):

```json
{
  "stackName": "my-metabase",
  "applicationId": "metabase",
  "runtime": "FARGATE",
  "securityProfile": "production",
  "provisionDatabase": true,
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseBackupRetentionDays": 30,
  "databaseMultiAz": true
}
```

For a `REQUIRED` application such as GitLab, the database is created without `provisionDatabase`, but
set it to `true` if you want the RDS AWS Config rules deployed.

---

## Security defaults

`RdsFactory` applies these settings based on the security profile:

| Setting | DEV | STAGING | PRODUCTION |
|---------|-----|---------|------------|
| Backup retention (unless `databaseBackupRetentionDays` is set) | 7 days | 14 days | 30 days |
| Multi-AZ (unless `databaseMultiAz` is set) | off | per compliance matrix | per compliance matrix |
| Deletion protection | off | per compliance matrix | per compliance matrix |
| Auto minor version upgrade | off | off | on |
| IAM database authentication | off | on | on |
| Performance Insights + Enhanced Monitoring (60 s) | off | off | on, except `*.micro` instance classes |
| CloudWatch log retention for exported DB logs | 1 month | 1 month | 1 year (1 month on `*.micro`) |

Applied in every profile:

- Storage type gp3, private subnets, `publiclyAccessible=false`.
- Backup window `03:00-04:00` UTC, maintenance window `sun:04:00-sun:05:00` UTC (not configurable).
- `copyTagsToSnapshot=true`.
- Engine logs exported to CloudWatch: `postgresql` for PostgreSQL; `error`, `general`, `slowquery` for MySQL/MariaDB.
- Parameter group settings:

  | Engine | Parameters |
  |--------|------------|
  | PostgreSQL | `log_statement=ddl`, `log_connections=1`, `log_disconnections=1` |
  | MySQL / MariaDB | `general_log=1`, `slow_query_log=1`, `log_output=FILE`, `long_query_time=2` |

Performance Insights uses long-term retention (731 days) and is encrypted with the instance's KMS key.
AWS does not support Performance Insights on micro instance classes, so those instances skip it even
in PRODUCTION.

TLS is not enforced by the parameter group. Applications choose their own connection settings; for
example, Mattermost's datasource URL (stored as an SSM parameter because its container has no shell)
uses `sslmode=require`.

Credential rotation is not configured. The secret is suppressed for cdk-nag rule `AwsSolutions-SMG4`;
rotate credentials through Secrets Manager if your policy requires it.

---

## Compliance rules and remediation

When `awsConfigEnabled` is `true` and a framework is listed in `complianceFrameworks`,
`ComplianceFactory` creates framework-specific AWS Config rules for RDS. These rules are deployed only
when `provisionDatabase` is `true`.

Two rules can have automatic SSM remediation attached. Each is off by default and must be enabled
explicitly:

| Key | Config rule | Frameworks that attach it |
|-----|-------------|---------------------------|
| `enableRdsDeletionProtectionRemediation` | `RDS_INSTANCE_DELETION_PROTECTION_ENABLED` | HIPAA, GDPR (all profiles); SOC 2 (PRODUCTION only) |
| `enableRdsAutoMinorVersionUpgradeRemediation` | `RDS_AUTOMATIC_MINOR_VERSION_UPGRADE_ENABLED` | PCI-DSS, SOC 2, HIPAA, GDPR |

Each remediation creates an SSM Automation document (`<stackName>-enable-rds-deletion-protection` or
`<stackName>-enable-rds-auto-minor-version-upgrade`) and an IAM role, and retries up to 3 times at
120-second intervals.

```json
{
  "stackName": "compliant-gitlab",
  "applicationId": "gitlab",
  "runtime": "FARGATE",
  "securityProfile": "production",
  "provisionDatabase": true,
  "complianceFrameworks": "hipaa,soc2",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "enableRdsDeletionProtectionRemediation": true,
  "enableRdsAutoMinorVersionUpgradeRemediation": true
}
```

`createConfigInfrastructure` creates the account/region-wide Config recorder and delivery channel. Set
it on exactly one stack per account and region; see
[AWS Config across multiple stacks](../compliance/AWS_CONFIG_MULTI_STACK.md).

Static validation of database settings lives in
[`DatabaseSecurityRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/DatabaseSecurityRules.java).

---

## Implementing DatabaseSpec

```java
public class MyApplicationSpec implements ApplicationSpec, DatabaseSpec {

    @Override
    public DatabaseRequirement databaseRequirement() {
        return DatabaseRequirement.optional("postgres", "15")
            .withInstanceClass("db.t3.small")
            .withStorage(20)
            .withDatabaseName("myapp");
    }

    @Override
    public boolean requiresReadReplicas() { return true; }

    @Override
    public int readReplicaCount() { return 1; }

    @Override
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        if (dbConn == null) {
            return Map.of("DB_TYPE", "embedded");
        }
        return Map.of(
            "DB_HOST", dbConn.endpoint(),
            "DB_PORT", String.valueOf(dbConn.port()),
            "DB_NAME", dbConn.databaseName(),
            "DB_USER", dbConn.username());
    }
}
```

`DatabaseRequirement.required(...)` and `optional(...)` default to `db.t3.micro`, 20 GB, and database
name `applicationdb`. Read replica endpoints are available through `dbConn.readReplicaEndpoints()`.

`DatabaseSpec` also declares `databaseParameters()`, `databaseInitScripts()`, and
`backupRetentionDays()`. `RdsFactory` does not currently consume them: the parameter group always uses
the engine defaults above, init scripts are not executed, and retention comes from
`databaseBackupRetentionDays` or the security profile.

---

## Troubleshooting

### Retrieve database credentials

The secret has a generated name. Find it through the stack's resources, then read it:

```bash
aws cloudformation describe-stack-resources --stack-name <stack-name> \
  --query "StackResources[?ResourceType=='AWS::SecretsManager::Secret'].PhysicalResourceId"

aws secretsmanager get-secret-value --secret-id <secret-arn> \
  --query SecretString --output text | jq -r '.password'
```

### The application cannot connect

1. Confirm the task definition has the expected database environment variables:
   ```bash
   aws ecs describe-task-definition --task-definition <task-family> \
     --query 'taskDefinition.containerDefinitions[0].environment'
   ```
2. Confirm the database security group allows ingress from the application's security group:
   ```bash
   aws ec2 describe-security-groups \
     --filters "Name=tag:aws:cloudformation:stack-name,Values=<stack-name>" \
     --query 'SecurityGroups[*].[GroupName,GroupId,IpPermissions]'
   ```

### "Database is locked" with H2 or SQLite

More than one task is sharing an embedded database file. Run a single task, or set
`provisionDatabase: true`.

### Remediation does not run

- Check that `awsConfigEnabled`, `provisionDatabase`, and the relevant `enableRds*Remediation` key are all `true`.
- Check that the Config recorder is running:
  ```bash
  aws configservice describe-configuration-recorder-status
  ```

### Instance identifier already exists

The DB instance identifier is `<stackName>-<applicationId>-db` (truncated to 63 characters). A
leftover instance from an earlier stack with the same name blocks creation. Delete it or choose a
different stack name.

### Reducing cost in non-production

Use DEV, a micro instance class, and leave `databaseMultiAz` unset or `false`. For Metabase and
Grafana, leave `provisionDatabase` unset to use the embedded store.

---

## Reference

- [`DatabaseSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/DatabaseSpec.java)
- [`RdsFactory`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/database/RdsFactory.java)
- [`DatabaseSecurityRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/DatabaseSecurityRules.java)
- [Compliance documentation](../compliance/README.md)
- [Multi-framework compliance](../compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
- [Advanced configuration](../ADVANCED.md)
