# Retained / Deletion-Protected Resources

CloudForge deliberately protects certain resources from accidental deletion depending on
security profile and compliance framework. A plain `DeleteStack` (or `cdk destroy`) will not
remove them — CloudFormation either leaves the stack in `DELETE_FAILED` (deletion-protected
resources) or the resource just outlives the stack (`RemovalPolicy.RETAIN`). This is correct,
intentional behavior for real AWS environments; for LocalStack testing/cleanup it means every
one of these needs an explicit extra step before a stack can be fully torn down.

All commands below target LocalStack (`--endpoint-url=http://localhost:4566`). Drop that flag
for real AWS (and add proper `--region`/credentials).

## RDS DB Instance — `DeletionProtection`

**Where:** `RdsFactory.java` (`.deletionProtection(...)`), condition in
`{Dev,Staging,Production}SecurityProfileConfiguration#isRdsDeletionProtectionEnabled()`.
**When:** DEV — never. STAGING/PRODUCTION — whenever *any* selected compliance framework marks
`DELETION_PROTECTION` as a required control (`ComplianceMatrix.isControlRequired`). This fires
under **both ADVISORY and ENFORCE** compliance mode — mode only ever excludes `DISABLED`, it
does not distinguish ADVISORY from ENFORCE for this check. ENFORCE's real effect is elsewhere:
it's what makes `cfn-guard` (L3) block the deploy outright when a framework's rules are
violated (e.g. `HIPAA/PRODUCTION` fails to deploy at all under ENFORCE) — a separate mechanism
from which resources get retained.
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 rds modify-db-instance \
  --db-instance-identifier <db-id> --no-deletion-protection --region us-east-1
aws --endpoint-url=http://localhost:4566 rds delete-db-instance \
  --db-instance-identifier <db-id> --skip-final-snapshot --region us-east-1
```
**Caveat:** if AWS Config auto-remediation is active for this stack (see "SSM auto-remediation"
below), it can silently re-enable deletion protection shortly after you disable it. Delete the
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

Several buckets retain on PRODUCTION; none of these are deletion-*protected* (no API call
blocks deleting them), they just outlive the stack and need manual cleanup + delete:
| Bucket | Where | Condition |
|---|---|---|
| ALB access-log bucket | `AlbFactory.java` | PRODUCTION |
| CMS media storage bucket | `CmsMediaStorageConfiguration.java#determineRemovalPolicy` | PRODUCTION |
| Compliance/audit bucket (e.g. CloudTrail) | `ComplianceFactory.java` ~line 4289 | `security == PRODUCTION \|\| enableObjectLock` (also disables `autoDeleteObjects`, separate from this) |

**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 s3 rm s3://<bucket-name> --recursive --region us-east-1
aws --endpoint-url=http://localhost:4566 s3api delete-bucket --bucket <bucket-name> --region us-east-1
```
Object Lock buckets (`enableObjectLock=true`) may refuse deletion until retained objects'
retain-until dates pass — not overridable, by design.

## EFS FileSystem — `RemovalPolicy.RETAIN`

**Where:** `EfsFactory.java#createFileSystem`.
**When:** only when the deployment context explicitly sets `retainStorage: true` — not
profile-driven, purely a user opt-in.
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

## Cognito User Pool (+ SAML identity provider) — `RemovalPolicy.RETAIN`

**Where:** `CognitoAuthenticationFactory.java`, `CognitoSamlFactory.java`.
**When:** PRODUCTION only.
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 cognito-idp delete-user-pool \
  --user-pool-id <pool-id> --region us-east-1
```

## AWS Config infrastructure (Recorder, Delivery Channel, IAM Role) — `RemovalPolicy.RETAIN`

**Where:** `ComplianceFactory.java` (~lines 884/899/909).
**When:** always, unconditionally — these are account-level singletons (only one recorder per
region per account is allowed), retained deliberately so a second stack in the same region
doesn't try to recreate them (see `createConfigInfrastructure` / `awsConfigEnabled` split
documented in that class).
**Remove:**
```bash
aws --endpoint-url=http://localhost:4566 configservice stop-configuration-recorder \
  --configuration-recorder-name cloudforge-config-recorder --region us-east-1
aws --endpoint-url=http://localhost:4566 configservice delete-configuration-recorder \
  --configuration-recorder-name cloudforge-config-recorder --region us-east-1
aws --endpoint-url=http://localhost:4566 configservice delete-delivery-channel \
  --delivery-channel-name cloudforge-config-delivery-channel --region us-east-1
# IAM role deletion needs its attached policies detached first.
aws --endpoint-url=http://localhost:4566 iam delete-role --role-name <config-role-name>
```

## AWS Config auto-remediation — all 9 actions

`ComplianceFactory.java` wires 9 `CfnRemediationConfiguration`s as AWS Config auto-remediation
actions (`automatic(true)`, 3-5 retries). Each one watches a specific Config rule and, if AWS
Config finds a non-compliant resource, runs an SSM Automation document to fix it automatically —
independent of anything CloudFormation itself does. Manually undoing any of these by hand (like
`modify-db-instance --no-deletion-protection` above) can get silently reverted a few minutes
later if the remediation is still active. Two use AWS-managed SSM documents directly; the other
seven use a custom document authored in this codebase.

| Remediation | SSM Document | Kind | Targets | Line |
|---|---|---|---|---|
| Set IAM account password policy | `AWSConfigRemediation-SetIAMPasswordPolicy` | AWS-managed | Account found without the required password policy | ~1264 |
| Enable S3 bucket versioning | `AWS-ConfigureS3BucketVersioning` | AWS-managed | Bucket found with versioning disabled | ~1332 |
| Fix CloudTrail bucket policy | `cloudTrailFixDocument` | custom | CloudTrail S3 bucket with an incorrect/insecure policy | ~1526 |
| Enable RDS deletion protection | `rdsDeletionProtectionDocument` | custom | RDS instance found without `DeletionProtection` | ~1642 |
| Enable RDS auto minor-version upgrade | `rdsAutoUpgradeDocument` | custom | RDS instance found without `AutoMinorVersionUpgrade` | ~1753 |
| Enable Security Hub | `securityHubDocument` | custom | Account found with Security Hub disabled | ~4675 |
| Enable Inspector | `inspectorDocument` | custom | Account found with Inspector disabled | ~4749 |
| Enable Macie | `macieDocument` | custom | Account found with Macie disabled | ~4822 |
| Enable GuardDuty | `guardDutyDocument` | custom | Account found with GuardDuty disabled | ~4936 |

The IAM password policy, S3 versioning, RDS, and CloudTrail-bucket ones are stack-scoped (only
relevant to that one deployment's resources). The Security Hub / Inspector / Macie / GuardDuty
ones are **account-level service toggles** — remediation re-enables the service for the whole
account/region, not just this stack, which matters if you're trying to disable these services
broadly on a shared test account rather than clean up one specific deployment.

Which of the 9 actually apply to a given configuration depends on which Config rules that
config deploys (framework/profile-driven, same as everything else in this doc) — see the
"Remediation" tab of the 🚀 LocalStack button on any row of the
[compliance dashboard](../../cfc-testing/scripts/validation-results/compliance-validation-dashboard.html)
for the real, per-configuration list.

**Remove a remediation configuration** (stops it from re-applying, does not undo what it already
changed):
```bash
aws --endpoint-url=http://localhost:4566 configservice delete-remediation-configuration \
  --config-rule-name <rule-name> --region us-east-1
```
Find `<rule-name>` from the stack's `AWS::Config::ConfigRule` resources
(`aws configservice describe-config-rules --region us-east-1`).

## The pragmatic alternative: don't clean up individual resources at all

For LocalStack testing specifically, none of the above is actually necessary — LocalStack has
no real persistence, so restarting the container is a guaranteed clean slate regardless of what
any individual stack retained. `deploy-localstack-compliance-matrix.sh` restarts LocalStack
between every config for exactly this reason. Only use the commands in this file when you need
to clean up a *specific* retained resource without a full reset (e.g. investigating one config's
real output), or when working against real AWS where a full reset isn't an option and these
commands are what you'd actually run in production too.
