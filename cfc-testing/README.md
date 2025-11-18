# CloudForge Community Testing Platform

This directory contains the CloudForge Community testing framework and utilities, including the Interactive Deployer for testing purposes.

## Purpose

`cfc-testing` is designed to **test** the CloudForge Community libraries (`cloudforge-api` and `cloudforge-core`) to ensure they work correctly. It includes the Interactive Deployer for testing deployment functionality.

## Interactive Deployer (Testing)

The Interactive Deployer is available here for testing purposes. It's a command-line tool that guides you through configuring and deploying CloudForge Community infrastructure using the SystemContext orchestration layer.

### Quick Start

```bash
# Simply run CDK deploy (uses saved configuration from deployment-context.json)
cdk deploy

# Or synthesize only
cdk synth
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

For production sample applications that demonstrate how to use CloudForge Community, see the **`cloudforge-sample`** repository at https://github.com/CloudForgeCI/cloudforge-sample.

## Testing Framework

This directory contains:
- **Unit Tests**: Comprehensive test suite (26 tests) for `InteractiveDeployer` utility methods
- Test utilities for validating CloudForge Community functionality
- Integration tests for the core libraries
- Performance benchmarks
- Validation tools
- Interactive Deployer for testing deployment functionality

### Unit Tests

```bash
# Run unit tests
mvn test

# Run specific test class
mvn test -Dtest=InteractiveDeployerTest
mvn test -Dtest=DeploymentContextPropagationTest
```

**Test Coverage:**
- ✅ Field propagation from `DeploymentConfig` → `deployment-context.json` → `DeploymentContext`
- ✅ JSON parsing and serialization
- ✅ Enum type conversions (RuntimeType, TopologyType, SecurityProfile)
- ✅ Validation rules (authMode requirements, topology constraints)
- ✅ Type compatibility (String/Integer for logRetentionDays)
- ✅ Default value behavior

**Jackson Integration:**
- Uses Jackson ObjectMapper for automatic field serialization (no manual mapping)
- Reduces `buildCfcContext()` from 130 lines to 30 lines (77% code reduction)
- Eliminates dead code risk - automatically includes all DeploymentConfig fields
- Jackson dependency only in cfc-testing (no impact on cloudforge-api/core)

## Architecture

The testing framework validates:
- `cloudforge-api`: Core interfaces and orchestration layer
- `cloudforge-core`: Business logic and factory implementations

## Comprehensive Testing & Validation

CloudForge includes a suite of comprehensive testing and validation tools:

### Synthesis Testing
```bash
# Test synthesis across all security profiles (DEV, STAGING, PRODUCTION)
# Tests EC2 and Fargate runtimes with proper security configurations
scripts/comprehensive-synth-test.sh
```

Tests all combinations of:
- **Runtimes**: EC2, Fargate
- **Security Profiles**: DEV (minimal), STAGING (medium), PRODUCTION (full)
- **Features per profile**:
  - PRODUCTION: WAF, ALB access logging, Cognito OIDC, all compliance frameworks
  - STAGING: ALB access logging, Cognito OIDC, SOC2 compliance
  - DEV: Minimal security for fast iteration

### Resource Validation
```bash
# Validate synthesized templates against expected resource truth table
scripts/comprehensive-resource-validator.sh
```

Creates a truth table of expected resources and validates:
- VPC, security groups, load balancers
- Authentication resources (Cognito User Pool, OIDC)
- Compliance resources (S3 buckets for ALB logs, WAF, CloudTrail)
- Runtime-specific resources (ECS, EC2, Auto Scaling)

### Drift Detection
```bash
# Create baseline from current validation results
scripts/drift-detector.sh baseline

# Detect configuration drift after code changes
scripts/drift-detector.sh detect

# Generate drift history report
scripts/drift-detector.sh history
```

Tracks configuration changes over time:
- Resource count changes
- Missing/added resources
- Status changes (PASS → FAIL)
- New/removed configurations

### Performance Benchmarks
```bash
# Run synthesis performance benchmarks
scripts/quick-synth-benchmark.sh
scripts/performance-synth-benchmark.sh
scripts/run-all-benchmarks.sh
```

## Files

### Source Files
- `src/main/java/com/cloudforgeci/samples/app/InteractiveDeployer.java` - Interactive Deployer for testing
- `src/main/java/com/cloudforgeci/samples/app/CloudForgeCommunitySample.java` - Sample CDK application for testing
- `src/main/java/com/cloudforgeci/samples/launchers/` - Test launchers for different deployment types

### Testing Scripts
- `scripts/deploy-interactive.sh` - Run the Interactive Deployer
- `scripts/comprehensive-synth-test.sh` - Test synthesis across all security profiles
- `scripts/comprehensive-resource-validator.sh` - Validate resources against truth table
- `scripts/drift-detector.sh` - Detect configuration drift over time
- `scripts/detailed-analysis.sh` - Detailed resource analysis
- `scripts/enhanced-synth-test.sh` - Enhanced synthesis testing with OIDC and compliance
- `scripts/deployment-dry-run-tracker.sh` - Deployment dry-run testing with AWS credentials
- `scripts/master-validation-system.sh` - Master validation orchestrator

### Configuration
- `cdk.json` - CDK configuration for testing
- `logging.properties` - Logging configuration for tests
- `deployment-context.json` - Generated deployment context
