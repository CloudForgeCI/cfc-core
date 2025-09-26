# CloudForge Community Testing Platform

This directory contains the CloudForge Community testing framework and utilities, including the Interactive Deployer for testing purposes.

## Purpose

`cfc-testing` is designed to **test** the CloudForge Community libraries (`cloudforge-api` and `cloudforge-core`) to ensure they work correctly. It includes the Interactive Deployer for testing deployment functionality.

## Interactive Deployer (Testing)

The Interactive Deployer is available here for testing purposes. It's a command-line tool that guides you through configuring and deploying CloudForge Community infrastructure using the SystemContext orchestration layer.

### Quick Start

```bash
# Run the interactive deployer
./deploy-interactive.sh

# Or manually
mvn compile
mvn exec:java -Dexec.mainClass="com.cloudforgeci.samples.app.InteractiveDeployer"
```

### Features

- **Modular Architecture**: Uses SystemContext orchestration layer for expandable deployment types
- **Strategy Pattern**: Easily extensible deployment strategies
- **Multiple Deployment Types**: 
  - Jenkins (Fargate/EC2) - ✅ Complete
  - S3 + CloudFront (Static Website) - 🚧 Coming Soon
  - S3 + CloudFront + SES + Lambda (Website + Mailer) - 🚧 Coming Soon
- **Interactive Configuration**: Prompts for all necessary parameters with sensible defaults
- **CDK Integration**: Generates proper CDK context and synthesizes stacks

### Prerequisites

1. **AWS CDK CLI**: `npm install -g aws-cdk`
2. **AWS Credentials**: `aws configure`
3. **Java 21+**: Required for compilation
4. **Maven**: For building the project

### Testing

```bash
# Test the interactive deployer
./test-ec2-deploy.sh
```

### Usage Examples

#### With Custom Stack Name
```bash
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer my-jenkins-ec2
```

#### Interactive Mode
```bash
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer
```

## Sample Applications

For production sample applications that demonstrate how to use CloudForge Community, see the **`cloudforge-sample`** project at `/Users/phillip/projects/cloudforge-sample`.

## Testing Framework

This directory contains:
- Test utilities for validating CloudForge Community functionality
- Integration tests for the core libraries
- Performance benchmarks
- Validation tools
- Interactive Deployer for testing deployment functionality

## Architecture

The testing framework validates:
- `cloudforge-api`: Core interfaces and orchestration layer
- `cloudforge-core`: Business logic and factory implementations

## Files

- `src/main/java/com/cloudforgeci/samples/app/InteractiveDeployer.java` - Interactive Deployer for testing
- `src/main/java/com/cloudforgeci/samples/app/CloudForgeCommunitySample.java` - Sample CDK application for testing
- `src/main/java/com/cloudforgeci/samples/launchers/` - Test launchers for different deployment types
- `deploy-interactive.sh` - Script to run the Interactive Deployer
- `test-ec2-deploy.sh` - Test script for EC2 deployment
- `cdk.json` - CDK configuration for testing
- `logging.properties` - Logging configuration for tests
