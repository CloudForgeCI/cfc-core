# CloudForge Community Interactive Deployer

An interactive command-line tool that guides you through configuring and deploying CloudForge Community infrastructure.

## Features

- **Interactive Configuration**: Prompts for all necessary parameters with sensible defaults
- **Modular Architecture**: Uses SystemContext orchestration layer for expandable deployment types
- **Strategy Pattern**: Easily extensible deployment strategies
- **Multiple Deployment Types**: 
  - Jenkins (Fargate/EC2) - ✅ Fully Implemented
  - S3 + CloudFront (Static Website) - 🚧 Coming Soon
  - S3 + CloudFront + SES + Lambda (Website + Mailer) - 🚧 Coming Soon
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

```bash
# From the cfc-testing directory
cd cfc-testing
./deploy-interactive.sh
```

Or manually:

```bash
# From the cfc-testing directory
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

## Configuration Options

### Basic Configuration
- **Stack Name**: Name for your CDK stack
- **Environment**: dev, staging, or prod
- **Deployment Type**: jenkins, s3-website, or s3-website-mailer

### Domain Configuration
- **Domain**: Your domain name (e.g., example.com)
- **Subdomain**: Subdomain prefix (e.g., ci, app) - skipped if no domain
- **SSL Certificate**: Enable SSL - skipped if no domain

### Jenkins Deployment
- **Runtime**: Fargate or EC2
- **Topology**: JENKINS_SERVICE or JENKINS_SINGLE_NODE
- **Instance Capacity**: Min/max instances (EC2 only)
- **CPU/Memory**: Resource allocation
- **Authentication**: none, alb-oidc, or jenkins-oidc

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

## Example Session

```
🚀 CloudForge Community Interactive Deployer
=============================================

Stack Name [my-cloudforge-stack]: jenkins-ci
Environment:
  1. dev (default)
  2. staging
  3. prod
Choose [dev]: 1

Deployment Type:
  1. jenkins (default)
  2. s3-website (Coming Soon)
  3. s3-website-mailer (Coming Soon)
Choose [jenkins]: 1

Domain (e.g., example.com) []: mycompany.com
Subdomain (e.g., ci, app) []: ci
Enable SSL Certificate [Y/n]: y

Runtime:
  1. FARGATE (default)
  2. EC2
Choose [FARGATE]: 1

Topology:
  1. JENKINS_SERVICE (default)
  2. JENKINS_SINGLE_NODE
Choose [JENKINS_SERVICE]: 1

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
   - Topology: JENKINS_SERVICE
   - Domain: mycompany.com
   - SSL: Enabled

✅ CDK Stack synthesized successfully!
Run 'cdk deploy' to deploy to AWS
```

## Generated CDK Context

The interactive deployer builds a CDK context map with all your configuration:

```json
{
  "env": "dev",
  "tier": "public",
  "runtime": "FARGATE",
  "topology": "JENKINS_SERVICE",
  "securityProfile": "DEV",
  "domain": "mycompany.com",
  "subdomain": "ci",
  "enableSsl": true,
  "networkMode": "public-no-nat",
  "wafEnabled": false,
  "cloudfrontEnabled": false,
  "cpu": 2048,
  "memory": 4096,
  "authMode": "none"
}
```

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
