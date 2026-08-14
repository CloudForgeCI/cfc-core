# CloudForge CI Deployment Context Templates

This directory contains pre-configured deployment context templates for common use cases. These templates simplify onboarding by providing validated configurations for different environments and compliance requirements.

## CloudForge sample project

For a **standalone Maven project** (cloudforge-sample), use:

- [CloudForge Sample BOM template](../architecture/cloudforge-sample-bom.template.md)
- Starter POM: [cloudforge-sample/pom.xml](cloudforge-sample/pom.xml)

The in-repo reference entrypoint is **`cfc-testing/`** at the repository root.

## Quick Start

Choose a template based on your requirements and customize it for your environment:

```bash
# Copy template to your project
cp docs/examples/dev-minimal.json deployment-context.json

# Edit with your settings (domain, region, etc.)
vim deployment-context.json

# Deploy
cdk deploy
```

## Available Templates

### Quick Start (No Domain Required)

These templates use **AWS Private CA** to enable HTTPS without requiring a custom domain. They are intended for short setup cycles, testing, and internal applications.

#### **dev-oidc-quick.json** - Fastest OIDC Setup
- **Use Case**: Quick development with authentication, no domain setup required
- **Runtime**: Fargate
- **Network**: Private with NAT
- **Authentication**: Cognito ALB-OIDC with Private CA certificate
- **Compliance**: Disabled (but compliance rules can be tested)
- **Cost**: ~$450/month (includes ~$400 Private CA)
- **Setup Time**: ~5 minutes

**When to use:**
- Rapid OIDC testing without domain registration
- Development environments needing authentication
- Testing compliance rules in lower environments
- Internal tools where browser warnings are acceptable

```json
{
  "authMode": "alb-oidc",
  "enableSsl": true,
  "cognitoAutoProvision": true
  // No domain/subdomain needed - Private CA handles HTTPS
}
```

#### **staging-oidc-quick.json** - Compliance Testing Without Domain
- **Use Case**: Test full compliance stack without domain infrastructure
- **Runtime**: Fargate with auto-scaling
- **Network**: Private with NAT
- **Authentication**: Cognito application-oidc with MFA
- **Compliance**: SOC2 (advisory mode)
- **Cost**: ~$650/month (includes Private CA + compliance services)
- **Setup Time**: ~10 minutes

**When to use:**
- Testing compliance rules before production
- Validating HIPAA/PCI-DSS/SOC2 configurations
- Pre-production security testing without domain setup

#### **production-oidc-internal.json** - Internal Production Apps
- **Use Case**: Production internal applications (not customer-facing)
- **Runtime**: EC2 with Auto Scaling
- **Network**: Private with NAT
- **Authentication**: Cognito application-oidc with MFA and groups
- **Compliance**: SOC2 + HIPAA (enforce mode)
- **Cost**: ~$800/month (includes Private CA + full compliance)
- **Setup Time**: ~15 minutes

**When to use:**
- Internal production tools (CI/CD, monitoring, wikis)
- Applications where browser warnings are acceptable
- Fully compliant deployments without domain infrastructure

> **Note:** Private CA certificates are cryptographically valid and meet all compliance requirements (HIPAA, PCI-DSS, SOC2, GDPR). They are NOT trusted by browsers, so users will see certificate warnings. For customer-facing production, use a custom domain with public DNS validation.

---

### Development Environments

#### 1. **dev-minimal.json** - Fastest Setup
- **Use Case**: Local development, proof-of-concept, quick testing
- **Runtime**: Fargate (no EC2 management)
- **Network**: Public (no NAT gateway costs)
- **Authentication**: None (open access)
- **Compliance**: Disabled
- **Cost**: Lowest (~$30-50/month)
- **Setup Time**: ~5 minutes

**When to use:**
- Initial evaluation of CloudForge
- Development workstations
- Non-sensitive workloads
- Cost-conscious environments

#### 2. **dev-standard.json** - Balanced Development
- **Use Case**: Team development environments
- **Runtime**: Fargate with auto-scaling
- **Network**: Private with NAT
- **Authentication**: Cognito (no MFA)
- **Compliance**: Disabled
- **Cost**: Medium (~$100-150/month)
- **Setup Time**: ~10 minutes

**When to use:**
- Multi-developer teams
- Internal development environments
- Pre-production testing
- Basic security requirements

### Staging Environments

#### 3. **staging-soc2.json** - Pre-Production Validation
- **Use Case**: Staging environment with SOC 2 compliance testing
- **Runtime**: Fargate with auto-scaling (2-4 tasks)
- **Network**: Private with NAT
- **Authentication**: Cognito with MFA (TOTP)
- **Compliance**: SOC 2 (scoped to deployment)
- **Security**: WAF, GuardDuty, Config auto-remediation
- **Log Retention**: 365 days
- **Cost**: High (~$200-300/month)
- **Setup Time**: ~15 minutes

**When to use:**
- Pre-production validation
- SOC 2 compliance testing
- Security testing
- Load testing

### Production Environments

#### 4. **production-soc2.json** - Recommended for Most Organizations
- **Use Case**: Production deployments with SOC 2 compliance
- **Runtime**: EC2 with Auto Scaling (2-6 instances)
- **Network**: Private with NAT, custom domain
- **Authentication**: Cognito with MFA (TOTP)
- **Compliance**: SOC 2 (account-wide rules)
- **Security**: WAF, GuardDuty, AWS Audit Manager, Config auto-remediation
- **Log Retention**: 730 days (2 years)
- **Cost**: Highest (~$400-600/month)
- **Setup Time**: ~20 minutes

**When to use:**
- Production workloads
- SOC 2 Type II audits
- Customer-facing services
- SaaS applications

**SOC 2 Controls Implemented:**
- CC6.1: Encryption at rest and in transit
- CC6.6: Network segmentation with security groups
- CC6.7: Secure protocols (TLS 1.2+)
- CC7.2: Audit logging and monitoring
- CC7.3: Backup and recovery (EFS, S3 versioning)

#### 5. **production-hipaa.json** - Healthcare Applications
- **Use Case**: Production with HIPAA compliance (healthcare data)
- **Runtime**: EC2 with Auto Scaling (2-8 instances)
- **Network**: Private with NAT, custom domain
- **Authentication**: Cognito with MFA (TOTP)
- **Compliance**: HIPAA + SOC 2
- **Security**: Enhanced encryption, GuardDuty, Audit Manager
- **Log Retention**: 2190 days (6 years - HIPAA requirement)
- **Cost**: Highest (~$500-700/month)
- **Setup Time**: ~25 minutes

**When to use:**
- Healthcare applications (PHI/ePHI)
- HIPAA compliance requirements
- Medical device software
- Health insurance platforms

**Additional HIPAA Controls:**
- §164.312(a)(2)(iv): Encryption mechanisms
- §164.312(b): Audit controls and logging
- §164.312(d): Person/entity authentication
- §164.312(e): Transmission security
- §164.316(b)(2)(i): Log retention (6 years)

#### 6. **production-pci-dss.json** - Payment Card Processing
- **Use Case**: Production with PCI-DSS compliance (payment data)
- **Runtime**: EC2 with Auto Scaling (3-10 instances)
- **Network**: Private with NAT, custom domain
- **Authentication**: Cognito with MFA (TOTP)
- **Compliance**: PCI-DSS + HIPAA + SOC 2
- **Security**: All controls enabled
- **Log Retention**: 730 days (minimum PCI-DSS requirement)
- **Cost**: Highest (~$600-900/month)
- **Setup Time**: ~25 minutes

**When to use:**
- Payment processing systems
- E-commerce platforms
- PCI-DSS Level 1-4 compliance
- Financial services

**PCI-DSS Requirements Addressed:**
- Req 1: Network segmentation and firewalls (WAF, SGs)
- Req 2: Secure configurations
- Req 3: Data encryption (at rest and in transit)
- Req 4: Encryption in transit (TLS 1.2+)
- Req 6: Secure development (compliance validation)
- Req 7: Access control (IAM, Cognito MFA)
- Req 8: Authentication (MFA required)
- Req 10: Audit logging (CloudTrail, VPC Flow Logs)
- Req 11: Security testing (Config rules)

## Configuration Guide

### Required Customizations

All templates require the following customizations before deployment:

1. **Domain Settings** (Optional with Private CA):

   **Option A: Custom Domain (recommended for customer-facing production)**
   ```json
   {
     "domain": "your-domain.com",
     "subdomain": "jenkins",
     "enableSsl": true,
     "createZone": false  // Set true if Route53 zone doesn't exist
   }
   ```

   **Option B: No Domain (uses Private CA - ideal for dev/internal)**
   ```json
   {
     "enableSsl": true,
     "authMode": "alb-oidc"  // or "application-oidc"
     // No domain/subdomain needed - Private CA certificate issued for ALB DNS name
   }
   ```

   > **Private CA Notes:**
   > - Enables HTTPS without domain registration or DNS setup
   > - Fully compliant with HIPAA, PCI-DSS, SOC2, GDPR (encryption requirements met)
   > - Costs ~$400/month per CA (auto-deleted when stack destroyed)
   > - Browser will show certificate warnings (not publicly trusted)

2. **Region**:
   ```json
   {
     "region": "us-east-1"  // Change to your preferred region
   }
   ```

3. **Cognito Domain Prefix** (if using Cognito):
   ```json
   {
     "cognitoDomainPrefix": "your-unique-prefix"  // Must be globally unique
   }
   ```

4. **Stack Name**:
   ```json
   {
     "stackName": "YourCompany-Jenkins-Prod"  // Must be unique in account/region
   }
   ```

### Optional Customizations

#### Instance Sizing

**Fargate (CPU/Memory in MB):**
```json
{
  "cpu": 2048,      // 0.25, 0.5, 1, 2, 4 vCPU
  "memory": 4096    // See AWS Fargate sizing guide
}
```

**EC2:**
```json
{
  "instanceType": "t3.medium",  // t3.small, t3.medium, t3.large, m5.xlarge, etc.
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6
}
```

#### Scaling Configuration

```json
{
  "cpuTargetUtilization": 60,  // CPU % target for auto-scaling
  "enableAutoScaling": true
}
```

#### Compliance Scoping

```json
{
  "scopeConfigRulesToDeployment": true,  // true = stack-only, false = account-wide
  "complianceMode": "enforce"            // "enforce" or "advisory"
}
```

## Deployment Workflows

### Development Workflow

```bash
# 1. Quick start with minimal dev
cp docs/examples/dev-minimal.json deployment-context.json
cdk deploy

# 2. Upgrade to standard dev
cp docs/examples/dev-standard.json deployment-context.json
# Update cognitoDomainPrefix
cdk deploy

# 3. Test staging with compliance
cp docs/examples/staging-soc2.json deployment-context.json
# Update domain and cognitoDomainPrefix
cdk deploy
```

### Production Deployment

```bash
# 1. Choose compliance template
cp docs/examples/production-soc2.json deployment-context.json

# 2. Customize required fields
vim deployment-context.json
# - domain
# - region
# - cognitoDomainPrefix
# - stackName

# 3. Review configuration
cat deployment-context.json

# 4. Synthesize and review template
cdk synth > template.yaml

# 5. Deploy
cdk deploy

# 6. Verify compliance
aws configservice describe-compliance-by-config-rule \
  --compliance-types NON_COMPLIANT
```

## Cost Estimates

### Monthly AWS Costs (Approximate)

| Template | Compute | Network | Storage | Compliance | Total |
|----------|---------|---------|---------|------------|-------|
| dev-minimal | $25 | $0 | $10 | $0 | **$35** |
| dev-standard | $50 | $30 | $15 | $0 | **$95** |
| staging-soc2 | $100 | $45 | $25 | $50 | **$220** |
| production-soc2 | $200 | $50 | $50 | $100 | **$400** |
| production-hipaa | $300 | $50 | $75 | $125 | **$550** |
| production-pci-dss | $400 | $60 | $100 | $150 | **$710** |

**Cost breakdown:**
- **Compute**: EC2/Fargate runtime costs
- **Network**: NAT Gateway, data transfer, ALB
- **Storage**: EFS, EBS, S3 (logs, artifacts)
- **Compliance**: AWS Config, Config Rules, GuardDuty, Audit Manager

**Cost optimization tips:**
1. Use Fargate Spot for development (50% savings)
2. Enable S3 lifecycle policies for log expiration
3. Use CloudWatch Logs Insights instead of retention
4. Scope Config rules to deployment (reduce rule evaluations)
5. Disable GuardDuty in dev/staging
6. Use Reserved Instances for production EC2

## Compliance Comparison

| Feature | dev-minimal | dev-standard | staging-soc2 | prod-soc2 | prod-hipaa | prod-pci-dss |
|---------|-------------|--------------|--------------|-----------|------------|--------------|
| **AWS Config** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **Config Rules** | 0 | 0 | 20+ | 20+ | 30+ | 40+ |
| **Auto-Remediation** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **CloudTrail** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **GuardDuty** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **WAF** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **VPC Flow Logs** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **Encryption** | ✗ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **MFA** | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| **Audit Manager** | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ |
| **Log Retention** | 7d | 30d | 365d | 730d | 2190d | 730d |

## Migration Paths

### From Dev to Staging
```bash
# Start with dev configuration
cat docs/examples/dev-standard.json > deployment-context.json

# Add compliance controls
jq '.awsConfigEnabled = true |
    .complianceFrameworks = "SOC2" |
    .guardDutyEnabled = true' deployment-context.json

# Redeploy
cdk deploy
```

### From Staging to Production
```bash
# Copy staging config
cp deployment-context.json deployment-context-prod.json

# Update for production
jq '.stackName = "CloudForge-Prod" |
    .securityProfile = "production" |
    .runtime = "ec2" |
    .instanceType = "t3.medium" |
    .minInstanceCapacity = 2 |
    .scopeConfigRulesToDeployment = false' deployment-context-prod.json

# Deploy to production
cdk deploy
```

## Troubleshooting

### Common Issues

**1. "cognitoDomainPrefix already exists"**
```bash
# Solution: Use a unique prefix
sed -i 's/"cognitoDomainPrefix": ".*"/"cognitoDomainPrefix": "my-unique-prefix-123"/g' deployment-context.json
```

**2. "Route53 hosted zone not found"**
```bash
# Solution: Create zone first or set createZone=true
jq '.createZone = true' deployment-context.json
```

**3. "Insufficient capacity for t3.medium"**
```bash
# Solution: Try different instance type or availability zones
jq '.instanceType = "t3.small"' deployment-context.json
```

**4. "Config recorder already exists"**
```bash
# Solution: Use existing recorder
jq '.createConfigInfrastructure = false' deployment-context.json
```

## Application-Specific Templates

### 7. **gitlab-production.json** - GitLab with Container Registry
- **Use Case**: Full GitLab deployment with CI/CD and container registry
- **Runtime**: EC2 (t3.large minimum for GitLab)
- **Network**: Private with NAT
- **Authentication**: Cognito OIDC
- **Ports**: Container registry (5050), Git SSH (22), Prometheus metrics (9090)

**Configuration highlights:**
```json
{
  "applicationId": "gitlab",
  "runtime": "ec2",
  "securityProfile": "production",
  "instanceType": "t3.large",
  "enableDockerRegistry": true,
  "enableSsh": true,
  "enableMetrics": true
}
```

### 8. **mattermost-production.json** - Team Collaboration
- **Use Case**: Secure team messaging with HIPAA-compliant options
- **Runtime**: Fargate
- **Database**: PostgreSQL RDS (required)
- **Authentication**: Cognito OIDC or SAML *(SAML in development)*
- **Ports**: SMTP (587), Clustering (8074-8075)

### 9. **harbor-production.json** - Enterprise Container Registry
- **Use Case**: Private Docker registry with vulnerability scanning
- **Runtime**: EC2 (storage-intensive workload)
- **Database**: PostgreSQL + Redis (required)
- **Ports**: Registry (443), Notary (4443), Trivy (8080)

### 10. **vault-production.json** - Secrets Management
- **Use Case**: HashiCorp Vault for secrets and PKI
- **Runtime**: EC2 (recommended for auto-unseal)
- **Network**: Private with NAT (required for production secrets)
- **Ports**: Clustering (8201)

## Additional Resources

- [Full Deployment Guide](../compliance/DEPLOYMENT_GUIDE.md)
- [Compliance Quick Start](../compliance/QUICK_START_GUIDE.md)
- [AWS Config Multi-Stack](../compliance/AWS_CONFIG_MULTI_STACK.md)
- [Cognito MFA Setup](../setup/COGNITO_MFA_COMPLIANCE_SETUP.md)
- [IAM Rules Guide](../guides/IAM_RULES.md)

## Support

For issues or questions:
1. Check existing documentation in `/docs`
2. Review [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
3. Consult [AWS Config documentation](https://docs.aws.amazon.com/config/)
4. Review [SOC 2 compliance guide](../COMPLIANCE_POSTURE.md)
