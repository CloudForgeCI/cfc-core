# CloudForge CI - Quick Start Guide

Get CloudForge CI running in your AWS account in **under 10 minutes** with zero compliance overhead, or configure a fully compliant production environment in under 30 minutes.

## Prerequisites

- AWS Account with admin access
- AWS CLI configured (`aws configure`)
- Node.js 18+ and npm
- AWS CDK installed (`npm install -g aws-cdk`)
- Java 21 (for building from source)

## Path 1: Fastest Start (5 Minutes)

**Goal**: Get Jenkins running with minimal configuration for evaluation/development.

### Step 1: Clone and Setup

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core
mvn clean install -DskipTests
cd cfc-testing
```

### Step 2: Use Minimal Dev Template

```bash
cp docs/examples/dev-minimal.json deployment-context.json
```

### Step 3: Bootstrap CDK (First Time Only)

```bash
cdk bootstrap
```

### Step 4: Deploy

```bash
cdk deploy
```

**Note**: The Interactive Deployer will automatically prompt you to configure if `deployment-context.json` doesn't exist.

### Step 5: Access Jenkins

After deployment completes (3-5 minutes):

```bash
# Get ALB DNS name
aws cloudformation describe-stacks \
  --stack-name CloudForge-Dev \
  --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerDNS`].OutputValue' \
  --output text
```

Navigate to the DNS name in your browser. **No authentication required** for dev-minimal.

### What You Get

- ✅ Application on Fargate (no server management)
- ✅ Public ALB (internet accessible)
- ✅ EFS storage (persistent data)
- ✅ Auto-scaling (1 task)
- ✅ CloudWatch monitoring
- ❌ No encryption
- ❌ No authentication
- ❌ No compliance controls
- **Cost**: ~$35/month

**⚠️ WARNING**: This setup is for **development/evaluation only**. Do not use for production.

---

## Path 2: Standard Development (10 Minutes)

**Goal**: Team development environment with basic security.

### Step 1: Setup

```bash
cd cfc-testing
cp docs/examples/dev-standard.json deployment-context.json
```

### Step 2: Customize Configuration

Edit `deployment-context.json`:

```json
{
  "stackName": "MyTeam-Jenkins-Dev",
  "cognitoDomainPrefix": "myteam-jenkins-dev-unique123"  // Must be globally unique
}
```

### Step 3: Deploy

```bash
cdk deploy
```

### Step 4: Access Jenkins

```bash
# Get ALB DNS and Cognito info
aws cloudformation describe-stacks \
  --stack-name MyTeam-Jenkins-Dev \
  --query 'Stacks[0].Outputs'
```

Navigate to ALB DNS, authenticate with Cognito (you'll create an account on first access).

### What You Get

- ✅ Application on Fargate with auto-scaling (1-2 tasks)
- ✅ Private subnets with NAT
- ✅ Cognito authentication (no MFA)
- ✅ Encryption at rest
- ✅ CloudWatch monitoring
- ❌ No compliance controls
- **Cost**: ~$95/month

---

## Path 3: Production with SOC 2 (30 Minutes)

**Goal**: Production-ready deployment with SOC 2 compliance.

### Step 1: Prepare Configuration

```bash
cd cfc-testing
cp docs/examples/production-soc2.json deployment-context.json
```

### Step 2: Customize for Your Environment

Edit `deployment-context.json`:

```json
{
  "stackName": "MyCompany-Jenkins-Prod",
  "region": "us-east-1",
  "domain": "mycompany.com",
  "subdomain": "jenkins",
  "createZone": false,  // Set true if Route53 zone doesn't exist
  "cognitoDomainPrefix": "mycompany-jenkins-prod",  // Must be globally unique
  "enableS3VersioningRemediation": true,
  "enableCloudTrailBucketAccessRemediation": true
}
```

### Step 3: Verify Prerequisites

```bash
# Verify Route53 hosted zone exists (if createZone=false)
aws route53 list-hosted-zones-by-name --dns-name mycompany.com

# Verify AWS Config is not already configured (or set createConfigInfrastructure=false)
aws configservice describe-configuration-recorders
```

### Step 4: Review Template

```bash
# Synthesize and review CloudFormation template
cdk synth > /tmp/template.yaml

# Check resource counts
grep "Type: AWS::" /tmp/template.yaml | wc -l
```

### Step 5: Deploy

```bash
cdk deploy --require-approval never
```

Deployment takes 15-20 minutes. Components deployed:
1. VPC, subnets, NAT gateways (2 min)
2. Security groups (1 min)
3. ALB, target groups (3 min)
4. EFS file system (2 min)
5. EC2 Auto Scaling Group (5 min)
6. Cognito User Pool (2 min)
7. AWS Config, CloudTrail, GuardDuty (3 min)
8. Config Rules and auto-remediation (2 min)

### Step 6: Verify Deployment

```bash
# Get outputs
aws cloudformation describe-stacks \
  --stack-name MyCompany-Jenkins-Prod \
  --query 'Stacks[0].Outputs' \
  --output table

# Verify Config Recorder is running
aws configservice describe-configuration-recorder-status

# Check compliance status
aws configservice describe-compliance-by-config-rule \
  --compliance-types COMPLIANT NON_COMPLIANT \
  --output table
```

### Step 7: Initial Admin Setup

```bash
# Create Cognito admin user
aws cognito-idp admin-create-user \
  --user-pool-id <pool-id-from-outputs> \
  --username admin@mycompany.com \
  --user-attributes Name=email,Value=admin@mycompany.com \
  --temporary-password TempPassword123! \
  --message-action SUPPRESS
```

### Step 8: Access Application

Navigate to `https://jenkins.mycompany.com` (or ALB DNS if domain not configured).

1. Authenticate with Cognito
2. Complete MFA setup (TOTP - use Google Authenticator, Authy, etc.)
3. Complete application-specific setup (varies by application)

### What You Get

- ✅ Application on EC2 with auto-scaling (2-6 instances)
- ✅ Private subnets with NAT
- ✅ Custom domain with SSL/TLS
- ✅ Cognito authentication with MFA
- ✅ Encryption at rest and in transit
- ✅ **20+ AWS Config rules** (SOC 2)
- ✅ **Auto-remediation** (S3 versioning, CloudTrail logging, RDS security)
- ✅ CloudTrail audit logging
- ✅ GuardDuty threat detection
- ✅ WAF web application firewall
- ✅ VPC Flow Logs
- ✅ AWS Audit Manager
- ✅ 2-year log retention
- **Cost**: ~$400/month

**✅ SOC 2 Type II Ready** - All technical controls implemented

---

## Path 4: HIPAA Compliance (30 Minutes)

**For healthcare applications handling PHI/ePHI.**

### Quick Setup

```bash
cd cfc-testing
cp docs/examples/production-hipaa.json deployment-context.json

# Customize (same as SOC 2 Path 3 above)
vim deployment-context.json

# Deploy
cdk deploy
```

### What's Different from SOC 2?

- ✅ **30+ Config rules** (HIPAA + SOC 2)
- ✅ **6-year log retention** (HIPAA §164.316(b)(2)(i))
- ✅ Enhanced encryption validation
- ✅ Additional audit controls
- **Cost**: ~$550/month

---

## Path 5: PCI-DSS Compliance (30 Minutes)

**For payment card processing systems.**

### Quick Setup

```bash
cd cfc-testing
cp docs/examples/production-pci-dss.json deployment-context.json

# Customize
vim deployment-context.json

# Deploy
cdk deploy
```

### What's Different?

- ✅ **40+ Config rules** (PCI-DSS + HIPAA + SOC 2)
- ✅ Certificate expiration monitoring
- ✅ Enhanced WAF rules
- ✅ CloudFront access logging (if enabled)
- ✅ Comprehensive network segmentation validation
- **Cost**: ~$710/month

---

## Path 6: Applications with Databases (20 Minutes)

**Goal**: Deploy applications that require RDS databases (GitLab, Mattermost, Metabase, etc.)

### Quick Setup

```bash
cd cfc-testing
# Create deployment context for application with database
cat > deployment-context.json <<EOF
{
  "stackName": "MyCompany-GitLab",
  "context": {
    "applicationId": "gitlab",
    "securityProfile": "production",
    "runtime": "FARGATE",
    "domain": "mycompany.com",
    "subdomain": "gitlab",
    "enableSsl": true,
    "cognitoDomainPrefix": "mycompany-gitlab-unique123"
  }
}
EOF

# Deploy (database automatically provisioned)
cdk deploy
```

### What's Different?

- ✅ **RDS PostgreSQL** automatically provisioned
- ✅ **Secrets Manager** for database credentials
- ✅ **Encryption** at rest with KMS
- ✅ **Automated backups** (7-30 days based on profile)
- ✅ **Multi-AZ** for production environments
- ✅ **Auto-remediation** for database compliance (deletion protection, auto-upgrades)

### Optional Database Provisioning

For applications that support both RDS and embedded databases (Metabase, Grafana):

```json
{
  "applicationId": "metabase",
  "securityProfile": "production",
  "provisionDatabase": true  // ← Optional: use RDS instead of H2
}
```

**Cost**: Add ~$15-80/month for RDS (depending on instance size)

---

## Common Customizations

### Change Instance Type

```json
{
  "instanceType": "t3.large"  // t3.small, t3.medium, t3.large, m5.xlarge
}
```

### Adjust Auto-Scaling

```json
{
  "minInstanceCapacity": 3,
  "maxInstanceCapacity": 10,
  "cpuTargetUtilization": 50
}
```

### Scope Config Rules to Stack Only

```json
{
  "scopeConfigRulesToDeployment": true  // Only monitor this stack's resources
}
```

### Enable Automated Remediation

```json
{
  "awsConfigEnabled": true,
  "complianceFrameworks": "SOC2,HIPAA",
  "enableS3VersioningRemediation": true,
  "enableCloudTrailBucketAccessRemediation": true,
  "enableRdsDeletionProtectionRemediation": true,
  "enableRdsAutoMinorVersionUpgradeRemediation": true
}
```

### Enable CloudFront CDN

```json
{
  "cloudfront": true
}
```

### Access Running Instances

CloudForge does not open port 22. All instance and container access goes through AWS Systems Manager — no SSH keys, no open ports, and every session is CloudTrail-logged.

**EC2 instances** (e.g. Jenkins):
```bash
aws ssm start-session --target <instance-id>
```

**Fargate tasks** (ECS Exec, enabled on all tasks):
```bash
aws ecs execute-command \
  --cluster <cluster-name> \
  --task <task-id> \
  --container <container-name> \
  --interactive \
  --command "/bin/sh"
```

Both require the caller's IAM identity to have `ssm:StartSession` or `ecs:ExecuteCommand` permission respectively. The `bastionCidr` deployment context field is retained for backwards compatibility but no longer gates access.

---

## Upgrading Between Environments

### Dev → Staging

```bash
# Start with dev config
cat deployment-context.json > deployment-context-staging.json

# Add compliance
cat > patch.json <<EOF
{
  "stackName": "MyCompany-Jenkins-Staging",
  "securityProfile": "staging",
  "awsConfigEnabled": true,
  "complianceFrameworks": "SOC2",
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "cognitoMfaEnabled": true,
  "logRetentionDays": 365
}
EOF

# Merge configurations
jq -s '.[0] * .[1]' deployment-context-staging.json patch.json > temp.json
mv temp.json deployment-context-staging.json

# Deploy
cdk deploy
```

### Staging → Production

```bash
# Copy staging config
cp deployment-context-staging.json deployment-context-prod.json

# Update for production
jq '.stackName = "MyCompany-Jenkins-Prod" |
    .securityProfile = "production" |
    .runtime = "ec2" |
    .instanceType = "t3.medium" |
    .minInstanceCapacity = 2 |
    .auditManagerEnabled = true |
    .scopeConfigRulesToDeployment = false |
    .logRetentionDays = 730' \
  deployment-context-prod.json > temp.json
mv temp.json deployment-context-prod.json

# Deploy
cdk deploy
```

---

## Troubleshooting

### Error: "Cognito domain prefix already in use"

```bash
# Generate unique prefix
UNIQUE_PREFIX="mycompany-jenkins-$(openssl rand -hex 4)"
jq ".cognitoDomainPrefix = \"$UNIQUE_PREFIX\"" deployment-context.json > temp.json
mv temp.json deployment-context.json
```

### Error: "Route53 hosted zone not found"

```bash
# Option 1: Create zone automatically
jq '.createZone = true' deployment-context.json > temp.json
mv temp.json deployment-context.json

# Option 2: Remove domain requirement
jq 'del(.domain, .subdomain)' deployment-context.json > temp.json
mv temp.json deployment-context.json
```

### Error: "Config recorder already exists"

```bash
# Use existing recorder
jq '.createConfigInfrastructure = false' deployment-context.json > temp.json
mv temp.json deployment-context.json
```

### Deployment Stuck or Failed

```bash
# Check CloudFormation events
aws cloudformation describe-stack-events \
  --stack-name YourStackName \
  --max-items 20

# Rollback if needed
cdk destroy
```

---

## Post-Deployment Tasks

### 1. Configure Jenkins

```bash
# Access Jenkins via ALB DNS or custom domain
# Install recommended plugins
# Configure:
# - GitHub integration
# - Pipeline libraries
# - Build agents
# - Credentials
```

### 2. Set Up Monitoring

```bash
# Subscribe to SNS topics for alerts
aws sns subscribe \
  --topic-arn <guardduty-topic-arn> \
  --protocol email \
  --notification-endpoint security@mycompany.com
```

### 3. Verify Compliance

```bash
# Run compliance check
aws configservice describe-compliance-by-config-rule \
  --compliance-types NON_COMPLIANT \
  --output table

# Fix any non-compliant resources
# Auto-remediation will handle some automatically
```

### 4. Backup Configuration

```bash
# Export deployment context
aws s3 cp deployment-context.json \
  s3://my-backup-bucket/jenkins/deployment-context-$(date +%Y%m%d).json

# Export CloudFormation template
aws cloudformation get-template \
  --stack-name YourStackName \
  --query 'TemplateBody' > template-backup.yaml
```

---

## Cost Optimization Tips

1. **Use Fargate Spot** (50% savings):
   ```json
   {
     "runtime": "fargate",
     "capacityProviderStrategy": [{"capacityProvider": "FARGATE_SPOT", "weight": 1}]
   }
   ```

2. **Reduce NAT Gateway costs** (dev only):
   ```json
   {
     "networkMode": "public-no-nat"  // Only for dev!
   }
   ```

3. **Scope Config rules**:
   ```json
   {
     "scopeConfigRulesToDeployment": true  // Fewer evaluations
   }
   ```

4. **Adjust log retention**:
   ```json
   {
     "logRetentionDays": 30  // Instead of 730 for non-production
   }
   ```

5. **Use Reserved Instances** (production EC2 - 40% savings)

6. **Enable S3 lifecycle policies** for log archival

---

## Next Steps

- **Development**: [Extended Testing Guide](guides/EXTENDED-TESTING.md)
- **Production**: [Deployment Guide](compliance/DEPLOYMENT_GUIDE.md)
- **Compliance**: [Quick Start Compliance Guide](compliance/QUICK_START_GUIDE.md)
- **Auditing**: [Audit Readiness Guide](AUDIT_READINESS_GUIDE.md)
- **Security**: [IAM Rules Guide](guides/IAM_RULES.md)

---

## Support

- **Documentation**: `/docs` directory
- **Issues**: [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- **AWS Support**: [AWS Support Center](https://console.aws.amazon.com/support)
- **Compliance**: See [Auditor Compliance Mapping](AUDITOR_COMPLIANCE_MAPPING.md)

---

## Quick Reference Commands

```bash
# Deploy
cdk deploy

# Check status
aws cloudformation describe-stacks --stack-name StackName

# Get outputs
aws cloudformation describe-stacks \
  --stack-name StackName \
  --query 'Stacks[0].Outputs'

# Check compliance
aws configservice describe-compliance-by-config-rule

# View logs
aws logs tail /aws/ecs/jenkins --follow

# Destroy stack
cdk destroy
```
