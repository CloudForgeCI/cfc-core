# Retained / Deletion-Protected Resources

CloudForge deliberately protects certain resources from accidental deletion depending on
security profile and compliance framework. A plain `DeleteStack` (or `cdk destroy`) will not
remove them: CloudFormation either leaves the stack in `DELETE_FAILED` (deletion-protected
resources) or the resource outlives the stack (`RemovalPolicy.RETAIN`). This is intentional
for AWS environments; for LocalStack testing and cleanup, each of these needs an explicit extra
step before a stack can be fully torn down.

All commands below target LocalStack (`--endpoint-url=http://localhost:4566`). Drop that flag
for real AWS (and add proper `--region`/credentials).

## RDS DB Instance — `DeletionProtection`

**Where:** `RdsFactory.java` (`.deletionProtection(...)`), condition in
`{Dev,Staging,Production}SecurityProfileConfiguration#isRdsDeletionProtectionEnabled()`.
**When:** DEV — never. STAGING/PRODUCTION — whenever *any* selected compliance framework marks
`DELETION_PROTECTION` as a required control (`ComplianceMatrix.isControlRequired`). This applies
under both ADVISORY and ENFORCE compliance mode; only `DISABLED` turns it off. ENFORCE instead
controls whether validation failures (framework validators, cdk-nag, and the Interactive
Deployer's `cfn-guard` step) block the deployment, which is independent of which resources are
retained.
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 rds modify-db-instance \
  --db-instance-identifier <db-id> --no-deletion-protection --region us-east-1
aws --endpoint-url=http://localhost:4566 rds delete-db-instance \
  --db-instance-identifier <db-id> --skip-final-snapshot --region us-east-1
```
**Caveat:** if AWS Config auto-remediation is active for this stack (see "AWS Config
auto-remediation" below), it can re-enable deletion protection shortly after you disable it. Delete the
Config remediation rule first, or delete the DB instance immediately after disabling protection.

## ALB — `deletionProtection`

**Where:** `AlbFactory.java#shouldEnableDeletionProtection()`.
**When:** PRODUCTION only (unconditional — not compliance-framework-gated).
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 elbv2 modify-load-balancer-attributes \
  --load-balancer-arn <alb-arn> \
  --attributes Key=deletion_protection.enabled,Value=false --region us-east-1
```

## S3 Buckets — `RemovalPolicy.RETAIN`

Several buckets are retained in PRODUCTION. None of these are deletion-*protected* (no API call
blocks deleting them); they outlive the stack and need to be emptied and deleted manually:
| Bucket | Where | Condition |
|---|---|---|
| ALB access-log bucket | `AlbFactory.java` | PRODUCTION |
| CMS media storage bucket | `CmsMediaStorageConfiguration.java#determineRemovalPolicy` | PRODUCTION |
| Compliance/audit bucket (e.g. CloudTrail) | `ComplianceFactory.java#getOrCreateBucket` | `security == PRODUCTION \|\| enableObjectLock` (also disables `autoDeleteObjects`) |

**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 s3 rm s3://<bucket-name> --recursive --region us-east-1
aws --endpoint-url=http://localhost:4566 s3api delete-bucket --bucket <bucket-name> --region us-east-1
```
Object Lock buckets (`enableObjectLock=true`) may refuse deletion until retained objects'
retain-until dates pass. This cannot be overridden.

## EFS FileSystem — `RemovalPolicy.RETAIN`

**Where:** `EfsFactory.java#createFileSystem`.
**When:** only when the deployment context sets `retainStorage: true`. This is an explicit
opt-in and does not depend on the security profile.
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 efs delete-file-system \
  --file-system-id <fs-id> --region us-east-1
```
(Delete any mount targets/access points on it first if the API complains about dependents.)

## AWS Backup Vault — `RemovalPolicy.RETAIN`

**Where:** `BackupFactory.java#createBackupVault`, condition in
`{Dev,Staging,Production}SecurityProfileConfiguration#isBackupVaultRetentionEnabled()`.
**When:** DEV — never. STAGING/PRODUCTION — same compliance-matrix-driven pattern as RDS
deletion protection above (any framework requiring the control, ADVISORY or ENFORCE alike).
**Remove:**
```bash
# Delete all recovery points in the vault first -- AWS refuses to delete a non-empty vault.
aws --endpoint-url=http://localhost:4566 backup list-recovery-points-by-backup-vault \
  --backup-vault-name <vault-name> --region us-east-1
aws --endpoint-url=http://localhost:4566 backup delete-recovery-point \
  --backup-vault-name <vault-name> --recovery-point-arn <arn> --region us-east-1
aws --endpoint-url=http://localhost:4566 backup delete-backup-vault \
  --backup-vault-name <vault-name> --region us-east-1
```

## Route53 Hosted Zone — `RemovalPolicy.RETAIN`

**Where:** `DomainFactory.java`.
**When:** PRODUCTION only.
**Remove:**
```bash
# Delete all non-NS/SOA record sets first.
aws --endpoint-url=http://localhost:4566 route53 list-resource-record-sets --hosted-zone-id <zone-id>
aws --endpoint-url=http://localhost:4566 route53 delete-hosted-zone --id <zone-id>
```

## CloudWatch Log Groups — `RemovalPolicy.RETAIN`

**Where:** `LoggingCwFactory.java`, `ProductionSecurityProfileConfiguration.java`,
`StagingSecurityProfileConfiguration.java`.
**When:** STAGING and PRODUCTION both (DEV does not retain).
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 logs delete-log-group --log-group-name <name> --region us-east-1
```

## Cognito User Pool — `RemovalPolicy.RETAIN`

**Where:** `CognitoAuthenticationFactory.java`. `CognitoSamlFactory.java` applies the same policy
to a SAML identity provider, but SAML federation is an incomplete feature that is not reachable
through `authMode` yet.
**When:** PRODUCTION only.
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 cognito-idp delete-user-pool \
  --user-pool-id <pool-id> --region us-east-1
```

## AWS Config infrastructure (Recorder, Delivery Channel, IAM Role) — `RemovalPolicy.RETAIN`

**Where:** `ComplianceFactory.java#createConfigInfrastructure`.
**When:** whenever the stack creates them (`awsConfigEnabled` and `createConfigInfrastructure`
both `true`). These are account-level singletons (one recorder per account and region), and are
retained so that other stacks in the region that rely on them keep working after this stack is
deleted. See [AWS Config Multi-Stack](AWS_CONFIG_MULTI_STACK.md).
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 configservice stop-configuration-recorder \
  --configuration-recorder-name cloudforge-config-recorder --region us-east-1
aws --endpoint-url=http://localhost:4566 configservice delete-configuration-recorder \
  --configuration-recorder-name cloudforge-config-recorder --region us-east-1
aws --endpoint-url=http://localhost:4566 configservice delete-delivery-channel \
  --delivery-channel-name cloudforge-config-channel --region us-east-1
# IAM role deletion needs its attached policies detached first.
aws --endpoint-url=http://localhost:4566 iam delete-role --role-name <config-role-name>
```

## AWS Config auto-remediation

`ComplianceFactory.java` defines 9 `CfnRemediationConfiguration`s as AWS Config automatic
remediation actions (`automatic(true)`, 3-5 retries). Each one watches a specific Config rule
and, when AWS Config finds a non-compliant resource, runs an SSM Automation document to fix it,
independently of CloudFormation. Changes made by hand (like
`modify-db-instance --no-deletion-protection` above) can be reverted a few minutes later while
the remediation is active. Two use AWS-managed SSM documents; the other seven use custom
documents defined in `ComplianceFactory`.

| Remediation | SSM Document | Kind | Targets | Method |
|---|---|---|---|---|
| Set IAM account password policy | `AWSConfigRemediation-SetIAMPasswordPolicy` | AWS-managed | Account without the required password policy | `createPasswordPolicyRemediation` |
| Enable S3 bucket versioning | `AWS-ConfigureS3BucketVersioning` | AWS-managed | Bucket with versioning disabled | `createS3VersioningRemediation` |
| Fix CloudTrail bucket policy | custom | custom | CloudTrail S3 bucket with an incorrect policy | `addCloudTrailBucketAccessRemediation` |
| Enable RDS deletion protection | custom | custom | RDS instance without `DeletionProtection` | `createRdsDeletionProtectionRemediation` |
| Enable RDS auto minor-version upgrade | custom | custom | RDS instance without `AutoMinorVersionUpgrade` | `createRdsAutoMinorVersionUpgradeRemediation` |
| Enable Security Hub | custom | custom | Account with Security Hub disabled | `createSecurityHubRemediation` |
| Enable Inspector | custom | custom | Account with Inspector disabled | `createInspectorRemediation` |
| Enable Macie | custom | custom | Account with Macie disabled | `createMacieRemediation` |
| Enable GuardDuty | custom | custom | Account with GuardDuty disabled | `createGuardDutyRemediation` |

The IAM password policy, S3 versioning, RDS, and CloudTrail bucket remediations affect this
deployment's resources and the account password policy. The Security Hub, Inspector, Macie, and
GuardDuty remediations are **account-level service toggles**: they re-enable the service for the
whole account and region, which matters when disabling these services on a shared test account.
They are created by default for PRODUCTION stacks, and the `enable*Remediation` keys that
`ComplianceFactory` reads to override that are not yet exposed through `DeploymentContext`.

Which remediations apply to a given configuration depends on which Config rules it deploys
(framework- and profile-driven). The compliance dashboard generated by
`cfc-testing/scripts/compliance-report-generator.py` lists them per configuration.

**Remove a remediation configuration** (stops it from re-applying, does not undo what it already
changed):
```bash
aws --endpoint-url=http://localhost:4566 configservice delete-remediation-configuration \
  --config-rule-name <rule-name> --region us-east-1
```
Find `<rule-name>` from the stack's `AWS::Config::ConfigRule` resources
(`aws configservice describe-config-rules --region us-east-1`).

## Alternative for LocalStack: reset the container

For LocalStack testing, restarting the container (without persistence enabled) discards all
state, including retained resources. `cfc-testing/scripts/deploy-localstack-compliance-matrix.sh`
restarts LocalStack between configurations for this reason. Use the commands in this file when
you need to remove a specific retained resource without a full reset, or when working against
AWS.
