# CloudForge Community Interactive Deployer

An interactive command-line tool that guides you through configuring and deploying CloudForge Community infrastructure.

## Features

- **Interactive Configuration**: Prompts for all necessary parameters with sensible defaults
- **Plugin-Based Architecture**: Automatic discovery of applications via ServiceLoader
- **Multiple Applications**:
  - CI/CD: Jenkins, GitLab, Drone
  - Analytics: Metabase, Superset, Grafana
  - Collaboration: Mattermost
  - Container Registry: Harbor, Nexus
  - VCS: Gitea
  - Databases: PostgreSQL, Redis
  - Secrets Management: Vault
  - Monitoring: Prometheus
- **Automatic Database Provisioning**: RDS databases for applications that require them
- **Smart Defaults**: Skips irrelevant questions based on your choices
- **Validation**: Ensures all required fields are provided
- **CDK Integration**: Generates proper CDK context and synthesizes stacks

## Quick Start

### Prerequisites

1. **AWS CDK CLI**: `npm install -g aws-cdk`
2. **AWS Credentials**: `aws configure`
3. **Java 17+**: Required for compilation
4. **Maven**: For building the project

### Running the Interactive Deployer

The Interactive Deployer **automatically activates** when `deployment-context.json` is not found. Simply run:

```bash
# Synthesize CloudFormation template (option 1)
cdk synth

# Deploy to AWS (option 2)
cdk deploy

# Create changeset without executing (option 4)
cdk deploy --no-execute
```

The interactive prompts will guide you through configuration and generate `deployment-context.json`.

**Manual Invocation** (if deployment-context.json exists but you want to reconfigure):

```bash
cd cfc-testing
mvn compile
mvn exec:java -Dexec.mainClass="com.cloudforgeci.samples.app.InteractiveDeployer"
```

## Architecture

### Modular Design

The Interactive Deployer uses a **Strategy Pattern** with the **SystemContext Orchestration Layer** to provide a modular, expandable architecture:

#### Strategy Pattern
- Each deployment type implements the `DeploymentStrategy` interface
- Strategies handle their own configuration collection and deployment logic
- New deployment types can be added by implementing the interface and registering in `DEPLOYMENT_STRATEGIES`

#### SystemContext Orchestration Layer
- Uses `SystemContext.createJenkinsDeployment()` for Jenkins deployments
- Uses `SystemContext.createS3CloudFrontDeployment()` for S3 website deployments
- Handles infrastructure creation, dependency management, and context injection
- Ensures consistent resource creation across all deployment types

#### Extensibility
Adding a new deployment type requires:
1. Implement `DeploymentStrategy` interface
2. Add strategy to `DEPLOYMENT_STRATEGIES` map
3. Implement `collectConfiguration()` and `deploy()` methods
4. Use SystemContext orchestration methods for deployment

## Deployment Sequence

The Interactive Deployer follows this sequence when deploying applications:

```mermaid
sequenceDiagram
    participant EndUser as End User
    participant Deployer as Interactive Deployer
    participant SystemContext as SystemContext
    participant AppFactory as ApplicationFactory
    participant ComplianceFactory as ComplianceFactory
    participant CDK as CDK
    participant AWS as AWS Resources
    
    EndUser->>Deployer: Run cdk deploy
    Deployer->>SystemContext: createJenkinsDeployment()
    SystemContext->>SystemContext: createInfrastructureFactories()
    SystemContext->>AWS: createVpcFactory()<br/>🏗️ Amazon VPC
    SystemContext->>AWS: createAlbFactory()<br/>⚖️ Application Load Balancer
    SystemContext->>AWS: createEfsFactory()<br/>💾 Amazon EFS
    SystemContext->>SystemContext: createJenkinsSpecificFactories()
    SystemContext->>AppFactory: ApplicationFactory.create()
    AppFactory->>AppFactory: FlowLogFactory.create()<br/>📊 VPC Flow Logs
    AppFactory->>AppFactory: DomainFactory.create()<br/>🌐 Route 53 (if domain)
    AppFactory->>SystemContext: createInfrastructureFactories()<br/>VPC, ALB, EFS, Logging
    AppFactory->>SystemContext: createSecurityFactories()<br/>Cognito, Identity Center, OIDC
    AppFactory->>AWS: Create RDS if required<br/>🗄️ Amazon RDS
    AppFactory->>AWS: Create compute resources<br/>🖥️ EC2 / ☁️ ECS Fargate
    AppFactory->>AWS: Create backup & alarms<br/>💿 AWS Backup, 📈 CloudWatch
    SystemContext->>SecurityProfile: SecurityProfileFactory.configureObservability()
    SecurityProfile->>ComplianceFactory: ComplianceFactory.create()
    ComplianceFactory->>AWS: Create Config rules<br/>✅ AWS Config
    ComplianceFactory->>AWS: Create remediation<br/>🔧 AWS Systems Manager
    ComplianceFactory->>AWS: Create Audit Manager<br/>📋 Evidence Collection
    SystemContext->>CDK: Synthesize template
    CDK-->>SystemContext: CloudFormation template
    SystemContext-->>Deployer: Template ready
    Deployer->>AWS: Deploy stack
    AWS-->>EndUser: Deployment complete
```

## Configuration Options

### Basic Configuration
- **Stack Name**: Name for your CDK stack
- **Environment**: dev, staging, or prod
- **Deployment Type**: jenkins, s3-website, or s3-website-mailer

### Domain Configuration
- **Domain**: Your domain name (e.g., example.com) - *optional with Private CA*
- **Subdomain**: Subdomain prefix (e.g., ci, app) - skipped if no domain
- **SSL Certificate**: Enable SSL - uses public ACM cert with domain, Private CA without

> **No Domain Quick Start:** If you skip domain configuration but enable SSL, the system automatically creates an AWS Private CA and issues a certificate for your ALB DNS name. This allows HTTPS without domain registration, ideal for development and internal applications. Private CA costs ~$400/month and is auto-deleted when the stack is destroyed.

### Application Deployment
- **Application**: Choose from 15+ pre-configured applications
- **Runtime**: Fargate or EC2
- **Topology**: APPLICATION_SERVICE (multi-instance) or S3_WEBSITE (static sites)
- **Instance Capacity**: Min/max instances (EC2 only)
- **CPU/Memory**: Resource allocation
- **Authentication**: Cognito OIDC or application-native OIDC
- **Database**: Automatic RDS provisioning for database-required applications

### S3 Website Deployment
- **Bucket Name**: S3 bucket for hosting
- **Index/Error Documents**: Default pages
- **CloudFront**: CDN distribution

### S3 Website + Mailer Deployment
- **SES Configuration**: Email address and region
- **Lambda Function**: Function name, memory, timeout

### Advanced Configuration
- **Network Mode**: public-no-nat or private-with-nat
- **WAF Protection**: Enable/disable
- **CloudFront CDN**: Enable/disable
- **Security Profile**: DEV, STAGING, or PRODUCTION

## Example Sessions

### With Custom Domain

```
🚀 CloudForge Community Interactive Deployer
=============================================

Stack Name [my-cloudforge-stack]: jenkins-ci
Environment:
  1. dev (default)
  2. staging
  3. prod
Choose [dev]: 1

Application:
  1. jenkins
  2. gitlab
  3. metabase
  4. grafana
  5. mattermost
  ... (15+ total)
Choose: 1

Domain (e.g., example.com) []: mycompany.com
Subdomain (e.g., ci, app) []: ci
Enable SSL Certificate [Y/n]: y

Runtime:
  1. FARGATE (default)
  2. EC2
Choose [FARGATE]: 1

Topology:
  1. APPLICATION_SERVICE (default)
  2. S3_WEBSITE
Choose [APPLICATION_SERVICE]: 1

CPU (units) [1024]: 2048
Memory (MB) [2048]: 4096

Authentication Mode:
  1. none (default)
  2. alb-oidc
  3. jenkins-oidc
Choose [none]: 1

Network Mode:
  1. public-no-nat (default)
  2. private-with-nat
Choose [public-no-nat]: 1

Enable WAF Protection [y/N]: n
Enable CloudFront CDN [y/N]: n

Security Profile:
  1. DEV (default)
  2. STAGING
  3. PRODUCTION
Choose [DEV]: 1

🔧 Building CDK Context...

📋 Deployment Configuration:
============================
Stack Name: jenkins-ci
Environment: dev
Deployment Type: jenkins
Runtime: FARGATE
Topology: JENKINS_SERVICE
Security Profile: DEV
Domain: mycompany.com
Subdomain: ci
SSL Enabled: true
Network Mode: public-no-nat
WAF Enabled: false
CloudFront Enabled: false
CPU: 2048
Memory: 4096 MB
Auth Mode: none

Proceed with deployment? [Y/n]: y

🚀 Starting CDK Deployment...

🚀 Deploying Jenkins using SystemContext orchestration layer...
✅ Jenkins deployment created successfully!
   - Infrastructure: VPC, ALB, EFS
   - Runtime: FARGATE
   - Topology: APPLICATION_SERVICE
   - Domain: mycompany.com
   - SSL: Enabled

✅ CDK Stack synthesized successfully!
Run 'cdk deploy' to deploy to AWS
```

### Without Domain (Private CA Quick Start)

```
🚀 CloudForge Community Interactive Deployer
=============================================

Stack Name [my-cloudforge-stack]: jenkins-quick
Environment:
  1. dev (default)
  2. staging
  3. prod
Choose [dev]: 1

Application:
  1. jenkins
  ...
Choose: 1

Domain (e.g., example.com) []: <enter to skip>
Enable SSL Certificate [Y/n]: y

⚠️  No domain configured - will use AWS Private CA for HTTPS
   - Certificate issued for ALB DNS name
   - Browser will show certificate warnings (not publicly trusted)
   - Private CA costs ~$400/month (auto-deleted with stack)
   - Fully compliant: meets HIPAA, PCI-DSS, SOC2 encryption requirements

Authentication Mode:
  1. none (default)
  2. alb-oidc
  3. application-oidc
Choose [none]: 2

Cognito Domain Prefix []: jenkins-quick-myco

📋 Deployment Configuration:
============================
Stack Name: jenkins-quick
Environment: dev
Deployment Type: jenkins
Runtime: FARGATE
Topology: JENKINS_SERVICE
Security Profile: DEV
SSL Enabled: true (Private CA)
Network Mode: private-with-nat
Auth Mode: alb-oidc
Cognito: Auto-provisioned

Proceed with deployment? [Y/n]: y

🚀 Deploying Jenkins using SystemContext orchestration layer...
✅ Jenkins deployment created successfully!
   - Infrastructure: VPC, ALB, EFS, Private CA
   - Runtime: FARGATE
   - Topology: APPLICATION_SERVICE
   - SSL: Private CA Certificate
   - Auth: Cognito ALB-OIDC

✅ CDK Stack synthesized successfully!
Run 'cdk deploy' to deploy to AWS
```

## Generated CDK Context

The interactive deployer builds a CDK context map with all your configuration:

### With Custom Domain

```json
{
  "env": "dev",
  "runtime": "FARGATE",
  "topology": "APPLICATION_SERVICE",
  "applicationId": "jenkins",
  "securityProfile": "DEV",
  "domain": "mycompany.com",
  "subdomain": "ci",
  "enableSsl": true,
  "networkMode": "public-no-nat",
  "authMode": "none"
}
```

### Without Domain (Private CA)

```json
{
  "env": "dev",
  "runtime": "FARGATE",
  "topology": "APPLICATION_SERVICE",
  "applicationId": "jenkins",
  "securityProfile": "DEV",
  "enableSsl": true,
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-quick-myco"
}
```

> **Note:** When no domain is configured but `enableSsl: true`, the system automatically creates AWS Private CA resources and issues a certificate for the ALB DNS name.

## Next Steps

After running the interactive deployer:

1. **Review the stack**: `cdk diff`
2. **Deploy to AWS**: `cdk deploy`
3. **Clean up**: `cdk destroy` (when done)

## Troubleshooting

### Common Issues

1. **AWS Credentials**: Ensure `aws configure` is run
2. **CDK Bootstrap**: Run `cdk bootstrap` for first-time setup
3. **Permissions**: Ensure your AWS user has necessary permissions
4. **Region**: Set `CDK_DEFAULT_REGION` environment variable

### Getting Help

- Check the CloudForge Community documentation
- Review CDK documentation for AWS-specific issues
- Check AWS CloudFormation console for deployment errors

## Advanced Usage

### Custom Configuration

You can also modify the generated CDK context manually or create custom deployment scripts based on the interactive deployer's output.

### Integration with CI/CD

The interactive deployer can be integrated into CI/CD pipelines by providing configuration via environment variables or configuration files.

## Contributing

To extend the interactive deployer:

1. Add new deployment types in `collectConfiguration()`
2. Implement deployment logic in the corresponding `deploy*()` methods
3. Update the `buildCfcContext()` method to include new parameters
4. Add validation logic as needed

## License

This tool is part of the CloudForge Community project and follows the same licensing terms.
