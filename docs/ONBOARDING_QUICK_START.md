# Onboarding Quick Start

This guide deploys one of the example configurations in [`docs/examples/`](examples/README.md)
to AWS, starting with a minimal development stack and moving to production profiles with
compliance controls. To try CloudForge without an AWS account, use the
[Local Emulator Quick Start](guides/LOCAL_EMULATOR_QUICK_START.md) instead.

Deployment time and AWS cost depend on the resources you enable, the region, and your
account. The compliance configurations enable and validate technical controls; they do not
certify an environment.

## Prerequisites

- An AWS account and credentials with permission to create the resources (`aws configure`)
- Java 25 and Maven 3.9+
- Node.js and the AWS CDK CLI (`npm install -g aws-cdk`)
- Optional: `jq` for editing JSON from the command line, and `cfn-guard` for template
  validation in `enforce` mode

## Build

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core
mvn clean install
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true
cd cfc-testing
cdk bootstrap        # once per account and region
```

All commands below run from `cfc-testing`. `cdk.json` runs the Interactive Deployer, which
synthesizes `deployment-context.json` without prompting when the CDK CLI invokes it.

## Path 1: Minimal development deployment

```bash
cp ../docs/examples/dev-minimal.json deployment-context.json
cdk deploy
```

This deploys Jenkins on Fargate with the `dev` profile: public subnets, no authentication, no
TLS, and no compliance controls. Use it only for evaluation.

Find the application URL in the stack outputs:

```bash
aws cloudformation describe-stacks \
  --stack-name CloudForge-Dev \
  --query "Stacks[0].Outputs[?OutputKey=='ApplicationUrl'].OutputValue" \
  --output text
```

## Path 2: Development with Cognito sign-in

```bash
cp ../docs/examples/dev-standard.json deployment-context.json
```

Edit `stackName`, and set `cognitoDomainPrefix` to a value that is unique across all AWS
accounts in the region, then deploy:

```bash
cdk deploy
aws cloudformation describe-stacks --stack-name <stackName> --query 'Stacks[0].Outputs'
```

## Path 3: Production with SOC 2 controls

```bash
cp ../docs/examples/production-soc2.json deployment-context.json
```

Edit at least `stackName`, `region`, `domain`, `subdomain`, and `cognitoDomainPrefix`. Then
check the prerequisites:

```bash
# A Route 53 hosted zone must exist for the domain unless createZone is true.
aws route53 list-hosted-zones-by-name --dns-name example.com

# Only one AWS Config recorder can exist per account and region. If one exists,
# set "createConfigInfrastructure": false.
aws configservice describe-configuration-recorders
```

Review and deploy:

```bash
cdk synth
cdk diff
cdk deploy
```

The `production` profile defaults `complianceMode` to `enforce`, so synthesis fails if a
selected framework's rules are violated. With `cfn-guard` installed, the synthesized template
is also checked against the framework's guard rules.

After deployment:

```bash
aws cloudformation describe-stacks --stack-name <stackName> --query 'Stacks[0].Outputs' --output table
aws configservice describe-compliance-by-config-rule --compliance-types NON_COMPLIANT --output table
```

Sign in through Cognito and complete MFA setup with an authenticator app. If
`cognitoInitialAdminEmail` is set, the first admin user receives an invitation at that
address.

## Path 4: HIPAA and PCI DSS examples

```bash
cp ../docs/examples/production-hipaa.json deployment-context.json     # HIPAA and SOC 2
cp ../docs/examples/production-pci-dss.json deployment-context.json   # PCI DSS, HIPAA, and SOC 2
```

Edit and deploy them the same way as Path 3. Compared with SOC 2 alone, these enable longer log
retention, private networking, and additional monitoring such as WAF, GuardDuty, and
certificate expiry alarms. See [Multi-Framework Compliance](compliance/MULTI_FRAMEWORK_COMPLIANCE.md).

## Path 5: Applications with a database

Applications such as GitLab, Mattermost, and Superset require a database, and CloudForge
provisions Amazon RDS for them automatically. Deployment contexts are flat JSON objects:

```json
{
  "stackName": "gitlab",
  "applicationId": "gitlab",
  "runtime": "fargate",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "gitlab",
  "enableSsl": true
}
```

Grafana and Metabase can use RDS or an embedded database; set `"provisionDatabase": true` to
use RDS. Credentials are stored in AWS Secrets Manager. See the
[Database Deployment Guide](databases/DATABASE-DEPLOYMENT-GUIDE.md).

## Common changes

| Change | Properties |
|---|---|
| Instance size | `instanceType` (EC2) or `cpu` and `memory` (Fargate) |
| Scaling | `minInstanceCapacity`, `maxInstanceCapacity`, `cpuTargetUtilization` |
| CloudFront | `"cloudfrontEnabled": true` |
| Log retention | `logRetentionDays` |
| AWS Config remediation | `enableS3VersioningRemediation`, `enableCloudTrailBucketAccessRemediation`, `enableRdsDeletionProtectionRemediation`, `enableRdsAutoMinorVersionUpgradeRemediation` (with `awsConfigEnabled`) |

The full list is in the [Advanced Guide](ADVANCED.md#configuration-reference).

### Moving from staging to production

```bash
jq '.stackName = "jenkins-prod"
    | .securityProfile = "production"
    | .runtime = "ec2"
    | .instanceType = "t3.medium"
    | .minInstanceCapacity = 2
    | .maxInstanceCapacity = 4' \
  deployment-context.json > deployment-context-prod.json
CFC_CONTEXT_FILE=deployment-context-prod.json cdk deploy
```

## Accessing instances and containers

CloudForge does not open port 22. Use AWS Systems Manager:

```bash
# EC2 instances
aws ssm start-session --target <instance-id>

# Fargate tasks (ECS Exec)
aws ecs execute-command --cluster <cluster> --task <task-id> \
  --container <container> --interactive --command "/bin/sh"
```

Your IAM identity needs `ssm:StartSession` or `ecs:ExecuteCommand` respectively.

## Troubleshooting

| Error | Resolution |
|---|---|
| Cognito domain prefix already in use | Choose another `cognitoDomainPrefix`, for example with a random suffix: `jq ".cognitoDomainPrefix = \"myapp-$(openssl rand -hex 4)\"" deployment-context.json` |
| Route 53 hosted zone not found | Set `"createZone": true`, or remove `domain` and `subdomain` |
| Config recorder already exists | Set `"createConfigInfrastructure": false` |
| Synthesis fails in `enforce` mode | Read the reported rule, fix the configuration, or set `"complianceMode": "advisory"` to review all findings |
| Stack creation failed | `aws cloudformation describe-stack-events --stack-name <stackName> --max-items 20` |

To remove a stack: `cdk destroy <stackName>`. Some resources are retained on deletion; see
[SECURITY.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/SECURITY.md#resources-retained-on-deletion).

## Next steps

- [Advanced Guide](ADVANCED.md): configuration reference and command-line reference
- [Application guides](guides/applications/README.md) and [CMS guides](guides/cms/README.md)
- [Compliance Deployment Guide](compliance/DEPLOYMENT_GUIDE.md)
- [Audit Readiness Guide](AUDIT_READINESS_GUIDE.md)
