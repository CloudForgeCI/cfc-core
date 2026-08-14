---
name: cloudforge-core
description: Infrastructure-as-Code framework for deploying secure, compliance-ready application workloads on AWS. Supports 33 applications across 14 categories (Jenkins, GitLab, Grafana, WordPress, Magento, Drupal, and more) with SOC2 AWS Config validated controls and HIPAA/PCI-DSS/GDPR/FedRAMP configurations. Built with Java 21, AWS CDK 2.232.1, and Maven. Features 38+ factories, type-safe enums, plugin architecture, CMS_SERVICE topology, and compliance-first design. Use for AWS infrastructure deployment, compliance automation, CDK development, and custom plugin creation.
license: Apache License 2.0
---

# CloudForge Core - AI Assistant Skill Documentation

## Overview

**CloudForge** is an open-source Infrastructure-as-Code (IaC) framework for deploying secure, compliance-ready application workloads on AWS. This document provides comprehensive context for AI assistants to effectively help with CloudForge development, usage, and troubleshooting.

**Version:** 3.1.0-SNAPSHOT
**License:** Apache 2.0
**Repository:** https://github.com/CloudForgeCI/cfc-core
**Language:** Java 21
**Build Tool:** Maven 3.9+
**AWS CDK Version:** 2.232.1
**Architecture:** 38+ specialized factories, 50+ typed slots, 19+ compliance frameworks

---

## Core Purpose & Value Proposition

CloudForge enables organizations to:

1. **Deploy applications to AWS in minutes** with automatic compliance validation
2. **Support 33 built-in applications** across 14 categories (CI/CD, monitoring, databases, CMS, e-commerce, etc.)
3. **Achieve multi-framework compliance** (SOC2, HIPAA, PCI-DSS, GDPR, ISO 27001, FedRAMP)
4. **Integrate enterprise authentication** (OIDC, Cognito, IAM Identity Center)
5. **Choose compute runtimes** (EC2 or Fargate)
6. **Extend via plugins** for custom applications and compliance rules

**Target Users:** DevOps engineers, platform engineers, compliance officers, security engineers

**Key Differentiator:** Compliance-first infrastructure - compliance rules are integrated at the infrastructure layer, not bolted on afterward.

---

## Project Structure

```
cfc-core/
├── cloudforge-core/          # Core interfaces & business logic
│   └── src/main/java/com/cloudforge/core/
│       ├── annotation/       # Plugin metadata (@ApplicationPlugin, @ComplianceFramework)
│       ├── config/           # Configuration validation & field introspection
│       ├── enums/            # RuntimeType, SecurityProfile, TopologyType, ComplianceMode
│       ├── interfaces/       # ApplicationSpec, FrameworkRules, OidcConfiguration
│       ├── oidc/             # 15+ OIDC integrations (Jenkins, GitLab, Grafana, Mattermost, etc.)
│       ├── iam/              # IAM profiles and policies
│       └── utilities/        # Validation helpers
│
├── cloudforge-api/           # AWS CDK integration & orchestration
│   └── src/main/java/com/cloudforgeci/api/
│       ├── application/      # 14+ application specs (Jenkins, GitLab, Mattermost, etc.)
│       ├── compute/          # FargateFactory, Ec2Factory
│       ├── core/             # SystemContext, DeploymentContext, runtime/topology/security configs
│       ├── database/         # RdsFactory
│       ├── ingress/          # AlbFactory
│       ├── network/          # VpcFactory, DomainFactory
│       ├── observability/    # LoggingCwFactory, GuardDutyFactory, AlarmFactory
│       ├── security/         # CertificateFactory, CognitoAuthenticationFactory, OidcAuthenticationFactory
│       └── storage/          # EfsFactory, ContainerFactory
│
├── cfc-testing/              # Testing framework & sample CDK app
│   ├── src/main/java/com/cloudforgeci/samples/
│   │   ├── app/              # InteractiveDeployer, CloudForgeCommunitySample
│   │   └── launchers/        # Deployment type launchers
│   └── src/test/java/        # Unit tests (26+ tests)
│
├── docs/                     # Comprehensive documentation
│   ├── compliance/           # Compliance guides (SOC2, HIPAA, PCI-DSS, GDPR)
│   ├── setup/                # Authentication setup guides
│   ├── plugins/              # Plugin system documentation
│   └── guides/               # Application guides, testing, IAM
│
├── docs/
│   └── examples/             # Example deployment configurations
│       ├── examples/         # Application-specific examples
│       ├── dev-minimal.json  # Minimal dev setup
│       ├── staging-soc2.json # SOC2 compliance
│       └── production-hipaa.json # HIPAA compliance
│
└── pom.xml                   # Multi-module Maven configuration
```

**Total Files:** 425 Java and JSON files
**Project Size:** 180MB
**Main Source Files:** 203 Java classes
**Test Files:** 100+ test classes

---

## Key Technologies

### Core Stack
- **Java 21** - Modern JVM features (records, sealed classes, virtual threads)
- **Maven 3.9+** - Build automation and dependency management
- **AWS CDK 2.232.1** - Infrastructure-as-code in Java
- **Constructs 10.3.0** - CDK base construct library

### Data Processing
- **Jackson 2.19.4** - JSON serialization/deserialization
- **Jakarta Validation 3.0.2** - Bean validation annotations
- **Hibernate Validator 8.0.1** - Validation implementation

### Testing & Quality
- **JUnit 5 (Jupiter 5.12.2)** - Unit testing framework
- **JaCoCo 0.8.13** - Code coverage (50% instruction, 40% branch minimum)
- **CDK Nag 2.36.40** - AWS best practices linting
- **OWASP Dependency-Check 12.1.0** - Vulnerability scanning
- **CycloneDX 2.8.2** - SBOM generation

### AWS Services
- **Compute:** ECS/Fargate, EC2, Auto Scaling Groups
- **Network:** VPC, ALB, NLB, CloudFront, Route53
- **Storage:** EFS, S3, RDS (PostgreSQL, MySQL)
- **Security:** Cognito, IAM, ACM, WAF, GuardDuty, Secrets Manager
- **Monitoring:** CloudWatch, AWS Config, Audit Manager
- **Compliance:** AWS Config Rules, CloudTrail

---

## Architectural Patterns

### 1. Plugin Architecture (ServiceLoader)
- **Type:** Extensibility pattern
- **Usage:** Automatic discovery of `ApplicationSpec` and `FrameworkRules` implementations
- **Benefit:** Add custom applications and compliance rules without modifying core code
- **Implementation:** Java `ServiceLoader` + `META-INF/services/` files

### 2. Factory Pattern with Context Injection (@ContextInjector)
- **Type:** Creational pattern
- **Components:** 38+ specialized factories extending `BaseFactory`
  - **Infrastructure:** VpcFactory, AlbFactory, EfsFactory, LoggingCwFactory, GuardDutyFactory
  - **Security/Auth:** CertificateFactory, CognitoAuthenticationFactory, OidcAuthenticationFactory, ApplicationOidcFactory, IdentityCenterFactory, IdentityCenterSamlFactory, ApplicationSamlFactory
  - **Compute:** FargateFactory, Ec2Factory, ContainerFactory, ApplicationLoader
  - **Database:** RdsFactory (PostgreSQL, MySQL, MariaDB, Aurora)
  - **Observability:** AlarmFactory, SecurityMonitoringFactory, ComplianceFactory, WafFactory
  - **Backup:** BackupFactory (AWS Backup with profile-based retention)
  - **Domain:** DomainFactory
- **Orchestration:** `SystemContext` manages factory creation order and dependency injection via `@ContextInjector`
- **Benefit:** Centralized control of AWS resource creation with proper initialization sequences

### 3. Slot-Based State Management
- **Type:** Concurrency and dependency resolution pattern
- **Implementation:** `Slot<T>` generic wrapper for optional/lazy-loaded values
- **Usage:** Factories set values in slots; dependent factories retrieve them via `SystemContext.of(Construct)`
- **Core Slots (50+ typed fields):** vpc, alb, efs, logs, asg, ec2Instance, fargateService, fargateTaskDef, container, cert, privateCa, cognitoUserPool, applicationOidcConfig, rdsDatabase, dbCredentials, topology, runtime, security, iamProfile
- **Deferred Wiring:** `once()` method + `executeDeferredActions()` pattern for post-construction resource linking
- **Benefit:** Decouples factories while maintaining type safety, preventing NPE, and enabling proper dependency ordering

### 4. Rules Engine with Priority-Based Execution (RuleKit Pattern)
- **Type:** Business rules pattern with declarative validation
- **Implementation:** Compliance rules with priority levels (-10 to 50)
- **Execution:** `Rules.installAll()` loads all applicable rules in priority order
- **Three-Phase Wiring:**
  - `rules()` → Declare requirements and constraints
  - `wire()` → Construct CDK resources
  - `deferredActions()` → Post-synthesis resource linking
- **Helper Methods:** `require()`, `forbid()`, `whenBoth()`, `whenAll()` for type-safe rule composition
- **Always-Load Cross-Framework Rules (priority < 0):**
  - KeyManagementRules (priority -10)
  - DatabaseSecurityRules (priority -5)
  - AdvancedMonitoringRules (priority -5)
  - ThreatProtectionRules (priority 0)
  - IncidentResponseRules (priority 0)
- **Framework-Specific Rules (priority 10-50):** Soc2Rules, HipaaRules, PciDssRules, GdprRules, Iso27001Rules, FedRampRules
- **Additional Security Rules:** ComputeSecurityRules, ElbSecurityRules, IamSecurityRules, LambdaSecurityRules, MessagingSecurityRules, CdnApiSecurityRules
- **Benefit:** Cross-cutting compliance concerns integrated without breaking layering, automatic rule discovery via ServiceLoader

### 5. Strategy Pattern for Runtime/Topology/Security
- **Type:** Behavioral pattern
- **Implementations:**
  - `RuntimeConfiguration` (EC2 vs Fargate)
  - `TopologyConfiguration` (Application Service, Jenkins Service, S3 Website)
  - `SecurityProfileConfiguration` (Dev, Staging, Production)
- **Switching:** Configuration chosen via deployment context enums
- **Benefit:** Pluggable behavior for different deployment scenarios

### 6. Template Method Pattern
- **Type:** Behavioral pattern
- **Implementation:** `BaseFactory` abstract class with template structure
- **Usage:** `createInfrastructureFactories()` orchestrates high-level steps
- **Benefit:** Consistent factory creation patterns across all 15+ factories

---

## Core Concepts

### 1. Deployment Context (Type-Safe Configuration with Enums)
**Purpose:** Type-safe configuration loader from `deployment-context.json`

**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/DeploymentContext.java` (536 lines)

**Type-Safe Enums (NEW in 3.1.0):**
- `RuntimeType`: EC2 | FARGATE
- `TopologyType`: JENKINS_SERVICE | APPLICATION_SERVICE | S3_WEBSITE (JENKINS_SINGLE_NODE removed in 3.0.0)
- `SecurityProfile`: DEV | STAGING | PRODUCTION
- `AuthMode`: NONE | ALB_OIDC | APPLICATION_OIDC (legacy alias: jenkins-oidc)
- `NetworkMode`: PUBLIC | PRIVATE_WITH_NAT | ISOLATED (legacy alias: public-no-nat)
- `LoadBalancerType`: ALB | NLB (NEW - supports ALB vs NLB selection)
- `ComplianceMode`: DISABLED | ADVISORY | ENFORCE
- `ComplianceFrameworkType`: SOC2 | PCI_DSS | HIPAA | GDPR | ISO_27001 | FEDRAMP_MODERATE | FEDRAMP_HIGH

**Configuration Categories:**
1. **Core:** stackName, applicationId, env, region
2. **Network/DNS:** domain, subdomain, fqdn, createZone, wafEnabled, albAccessLogging, networkMode (PUBLIC | PRIVATE_WITH_NAT | ISOLATED)
3. **Load Balancer:** lbType (ALB | NLB), enableSsl, sslCertificateArn
4. **Compute:** runtime (EC2 | FARGATE), topology (JENKINS_SERVICE | APPLICATION_SERVICE | S3_WEBSITE), instanceType, cpu, memory, minInstanceCapacity, maxInstanceCapacity
5. **Security:** securityProfile (DEV | STAGING | PRODUCTION), enableEncryption
6. **Authentication:** authMode (NONE | ALB_OIDC | APPLICATION_OIDC), cognitoAutoProvision, cognitoDomainPrefix, cognitoMfaEnabled, cognitoMfaMethod, cognitoInitialAdminEmail, ssoInstanceArn, autoProvisionIdentityCenter
7. **Compliance:** complianceMode (DISABLED | ADVISORY | ENFORCE), complianceFrameworks (List<ComplianceFrameworkType>), logRetentionDays, guardDutyEnabled, macieEnabled
8. **AWS Config:** awsConfigEnabled, createConfigInfrastructure, scopeConfigRulesToDeployment
9. **Audit Manager:** auditManagerEnabled, auditManagerFrameworkId
10. **Automated Remediation:** enableS3VersioningRemediation, enableCloudTrailBucketAccessRemediation, enableRdsDeletionProtectionRemediation, enableRdsAutoMinorVersionUpgradeRemediation, enableSecurityHubRemediation, enableInspectorRemediation, enableMacieRemediation, enableGuardDutyRemediation
11. **Database:** provisionDatabase, databaseEngine (postgresql | mysql | mariadb), databaseVersion, databaseInstanceClass
12. **Monitoring:** enableMonitoring, securityMonitoringEnabled
13. **Backup:** automatedBackupEnabled, backupRetentionDays, crossRegionBackupEnabled, retainStorage
14. **Health Checks:** healthCheckGracePeriod, healthCheckInterval, healthCheckTimeout, healthCheckPath

**Backward Compatibility:** Enums support string-based JSON deserialization for migration compatibility

**File Location:** `deployment-context.json` or passed via CDK context: `--context cfc=@deployment-context.json`

### 2. System Context (Central Orchestration Hub)
**Purpose:** Central orchestration hub for factory creation and state management

**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/SystemContext.java` (1,045 lines)

**Key Responsibilities:**
1. Load and validate configuration from `DeploymentContext`
2. Install compliance rules via `ComplianceFactory` and `Rules.installAll()`
3. Create infrastructure factories in dependency order (VPC, ALB, EFS, RDS, etc.)
4. Create application-specific factories (Fargate/EC2, Container, ApplicationLoader)
5. Create security factories (Cognito, OIDC, SAML, Certificates)
6. Execute deferred actions (post-deployment hooks via `executeDeferredActions()`)
7. Synthesize CloudFormation stack with CDK-nag validation

**Singleton Pattern:** Stack-level singleton retrieved via `SystemContext.of(Construct)` or created via `SystemContext.start()`

**Factory Creation Methods:**
- `createInfrastructureFactories()` - VPC, ALB, EFS, Logging, GuardDuty, Security Groups, Target Groups
- `createApplicationSpecificFactories()` - Runtime-dependent (Fargate/EC2), Container, RDS
- `createDomainAndSslFactories()` - Route53, ACM certificates
- `createSecurityFactories()` - Cognito, OIDC, SAML, Identity Center
- `createBackupFactory()` - AWS Backup configuration (NEW in 3.0)

### 3. Application Spec Interface (Plugin Architecture)
**Purpose:** Contract for pluggable application deployments

**File:** `cloudforge-core/src/main/java/com/cloudforge/core/interfaces/ApplicationSpec.java`

**Core Methods:**
- `applicationId()` - Unique identifier (from @ApplicationPlugin annotation)
- `defaultContainerImage()` - Container image with optional override
- `applicationPort()` - Primary port (e.g., 8080 for Jenkins)
- `containerDataPath()` / `efsDataPath()` - Volume mounting paths
- `ec2DataPath()` / `ec2LogPaths()` - EC2 storage and logging
- `configureUserData()` - EC2 setup script configuration
- `containerEnvironmentVariables()` - Dynamic env vars based on deployment config
- `healthCheckPath()` - ALB health check path

**Authentication Support:**
- `supportsOidcIntegration()` - Built-in OIDC support
- `getOidcIntegration()` - OIDC handler implementation
- `getSupportedAuthModes()` - ["application-oidc", "alb-oidc", "none"]
- `getRecommendedAuthMode()` - Default preference

**Path-Based Authentication:**
- `protectedPaths()` - Paths requiring auth (ALB-level OIDC)
- `publicPaths()` - Paths always public
- `additionalProtectedPaths()` - Override flexibility

**Optional Ports:**
- `OptionalPort` record with configKey-based enablement
- Inbound (security group rules needed) vs outbound (no SG rules)

**Plugin Discovery:**
- Annotation: `@ApplicationPlugin(value = "jenkins", category = "cicd", displayName = "Jenkins")`
- ServiceLoader registration: `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`
- ApplicationLoader: Discovers all implementations via `discover()`, `findById()`, `discoverByCategory()`

**Implementations:** 14+ built-in apps in `cloudforge-api/src/main/java/com/cloudforgeci/api/application/`

**OIDC Integrations (15+):**
**File:** `cloudforge-core/src/main/java/com/cloudforge/core/oidc/`

CloudForge includes built-in OIDC integrations for:
- **Jenkins** - JenkinsOidcIntegration (enhanced)
- **GitLab** - GitLabOidcIntegration (NEW - 107 lines, 44 test cases)
- **Grafana** - GrafanaOidcIntegration (enhanced)
- **Mattermost** - MattermostOidcIntegration (NEW)
- **Mattermost (GitLab OIDC)** - MattermostGitLabOidcIntegration (NEW)
- **Mattermost (SAML)** - MattermostSamlIntegration (NEW)
- **Metabase (SAML)** - MetabaseSamlIntegration (NEW)
- **Generic OIDC** - GenericOidcIntegration
- **Cognito OIDC** - CognitoOidcConfiguration
- **IAM Identity Center OIDC** - IdentityCenterOidcConfiguration

**OIDC Integration Features:**
- Automatic provider discovery from issuer URL
- Client secret management via AWS Secrets Manager
- Callback URL configuration (ALB DNS or custom domain)
- User/group claim mapping
- Token refresh handling
- Logout URL configuration

### 4. Framework Rules Interface (Compliance Plugin System)
**Purpose:** Contract for pluggable compliance frameworks

**File:** `cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java`

**Core Methods:**
- `install(T ctx)` - Register CDK validations
- `frameworkId()` - From @ComplianceFramework annotation
- `displayName()` / `description()` - Metadata
- `priority()` - Load ordering (lower = earlier, -10 to 50)
- `alwaysLoad()` - Always load or only on explicit enable
- `getRequiredConfiguration()` - Framework minimum config (NEW in 3.1.0)

**Plugin Discovery:**
- Annotation: `@ComplianceFramework(value = "SOC2", priority = 40, displayName = "SOC 2 Trust Services Criteria")`
- ServiceLoader registration: `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`
- ComplianceFactory: Discovers, filters by enablement, sorts by priority, installs validations

**Implementations (19+ Framework Rules):**

**Always-Load Cross-Framework Rules (priority ≤ 0):**
- `KeyManagementRules` (priority -10) - KMS encryption, key rotation, key policies
- `DatabaseSecurityRules` (priority -5) - RDS encryption, backup, deletion protection
- `AdvancedMonitoringRules` (priority -5) - CloudWatch, AWS Config
- `ThreatProtectionRules` (priority 0) - GuardDuty, Macie
- `IncidentResponseRules` (priority 0) - SNS alerting, EventBridge
- `ComputeSecurityRules` (priority 0) - EC2/Fargate hardening, IMDSv2, EBS encryption, termination protection
- `ElbSecurityRules` (priority 0) - ALB/NLB access logging, TLS/SSL, deletion protection
- `IamSecurityRules` (priority 0) - IAM least privilege, MFA, root account protection, credential rotation
- `LambdaSecurityRules` (priority 0) - Deprecated runtime detection, VPC config, DLQ, code signing
- `MessagingSecurityRules` (priority 0) - SNS/SQS KMS encryption, DLQ configuration
- `CdnApiSecurityRules` (priority 0) - CloudFront/API Gateway hardening, cache settings, logging

**Framework-Specific Rules (priority 10-50):**
- `FedRampHighRules` (priority 10) - FedRAMP High baseline (stricter than Moderate)
- `FedRampRules` (priority 15, 1,185 lines) - FedRAMP Moderate
- `PciDssRules` (priority 20, 705 lines) - PCI-DSS payment card compliance
- `GdprRules` (priority 25, 520 lines) - GDPR privacy compliance
- `GdprOrganizationalRules` (priority 25) - GDPR operational requirements
- `HipaaRules` (priority 30, 596 lines) - HIPAA healthcare compliance
- `HipaaOrganizationalRules` (priority 30) - HIPAA administrative safeguards
- `Soc2Rules` (priority 40, 524 lines) - SOC2 Type II compliance
- `Iso27001Rules` (priority 50) - ISO 27001 information security

**Total Compliance Code:** 12,000+ lines across 19+ implementations

### 5. Runtime Configuration
**Purpose:** Compute platform-specific behavior

**Variants:**
- **FargateRuntimeConfiguration:** ECS Fargate tasks, no instance management
- **Ec2RuntimeConfiguration:** EC2 instances, auto-scaling groups, user data scripts

**Key Properties:**
- `instanceType` (EC2) - e.g., "t3.micro", "m5.large"
- `cpu`, `memory` (Fargate) - vCPU units and MiB
- `minInstanceCapacity`, `maxInstanceCapacity` - Auto-scaling bounds
- `cpuTargetUtilization` - Auto-scaling trigger

### 6. Topology Configuration
**Purpose:** Architecture pattern-specific behavior

**Variants:**
- **ApplicationServiceTopology:** Generic containerized app with ALB
- **JenkinsServiceTopology:** CI/CD with persistent EFS, optional agents
- **S3WebsiteTopology:** Static website with CloudFront CDN

**Key Properties:**
- Target group configuration
- Health check paths
- Port mappings
- Persistent storage requirements

### 7. Security Profile Configuration
**Purpose:** Environment-specific security controls

**Variants:**
- **DevSecurityProfile:** Minimal controls, cost-optimized, HTTP allowed
- **StagingSecurityProfile:** Moderate controls, HTTPS recommended, MFA optional
- **ProductionSecurityProfile:** Maximum controls, HTTPS required, MFA required, private network, backup retention

**Key Properties:**
- Password complexity requirements
- MFA enforcement
- Network isolation
- Log retention days
- Backup retention policies
- Encryption requirements

### 8. Type-Safe Enums - Detailed Reference

#### NetworkMode Enum
**File:** `cloudforge-core/src/main/java/com/cloudforge/core/enums/NetworkMode.java`

**Values:**
- `PUBLIC` ("public") - Public subnets with internet gateway, legacy alias "public-no-nat"
- `PRIVATE_WITH_NAT` ("private-with-nat") - Private subnets with NAT Gateway for outbound internet (~$45/month per NAT)
- `ISOLATED` ("isolated") - **NEW** - No internet access, requires VPC endpoints for AWS services

**Helper Methods:**
- `isPrivate()` - Returns true for PRIVATE_WITH_NAT and ISOLATED
- `hasInternetAccess()` - Returns true for PUBLIC and PRIVATE_WITH_NAT
- `defaultForProfile()` - Returns PRIVATE_WITH_NAT for PRODUCTION/STAGING, PUBLIC for DEV

**Compliance Impact:**
- PCI-DSS/HIPAA require PRIVATE_WITH_NAT or ISOLATED for production workloads
- PUBLIC mode only recommended for DEV environments

#### LoadBalancerType Enum
**File:** `cloudforge-core/src/main/java/com/cloudforge/core/enums/LoadBalancerType.java`

**Values:**
- `ALB` ("alb") - Application Load Balancer with OIDC/WAF support, path routing, HTTP/HTTPS
- `NLB` ("nlb") - Network Load Balancer with static IPs, TCP/UDP, higher performance, no OIDC/WAF

**Feature Comparison:**
| Feature | ALB | NLB |
|---------|-----|-----|
| OIDC Auth | ✅ | ❌ |
| WAF Support | ✅ | ❌ |
| Path Routing | ✅ | ❌ |
| Static IPs | ❌ | ✅ |
| Protocol | HTTP/HTTPS | TCP/UDP |
| Latency | Higher | Lower |

**Helper Methods:**
- `supportsOidc()` - Returns true only for ALB
- `supportsWaf()` - Returns true only for ALB
- `supportsPathRouting()` - Returns true only for ALB
- `hasStaticIp()` - Returns true only for NLB

#### AuthMode Enum
**File:** `cloudforge-core/src/main/java/com/cloudforge/core/enums/AuthMode.java`

**Values:**
- `NONE` ("none") - No authentication required
- `ALB_OIDC` ("alb-oidc") - **RECOMMENDED** - ALB enforces OIDC before traffic reaches application (zero code changes)
- `APPLICATION_OIDC` ("application-oidc") - Application handles OIDC integration (legacy alias: "jenkins-oidc")

**ALB-OIDC Benefits:**
- Zero code changes to applications
- Consistent authentication across all applications
- Automatic token refresh handling
- Works with Cognito, IAM Identity Center, Okta, Auth0, etc.
- Centralized access control at load balancer level

**Requirements:**
- `ALB_OIDC` requires `LoadBalancerType.ALB` (not NLB)
- `ALB_OIDC` requires SSL/TLS enabled (`enableSsl: true`)

**Helper Methods:**
- `usesOidc()` - Returns true for ALB_OIDC and APPLICATION_OIDC
- `requiresAlb()` - Returns true for ALB_OIDC
- `requiresSsl()` - Returns true for ALB_OIDC
- `isAlbAuthenticated()` - Returns true for ALB_OIDC

#### ComplianceMode Enum
**File:** `cloudforge-core/src/main/java/com/cloudforge/core/enums/ComplianceMode.java`

**Values:**
- `DISABLED` ("disabled") - No compliance validation (not recommended, use ADVISORY instead)
- `ADVISORY` ("advisory") - Compliance warnings logged, deployment proceeds
- `ENFORCE` ("enforce") - Compliance violations block deployment

**Use Cases:**
- **ENFORCE mode:** Production deployments with strict compliance requirements
- **ADVISORY mode:** Dev/staging testing compliance configurations without blocking deployments
- **DISABLED mode:** Local development only (not recommended)

**Profile Defaults:**
- `defaultForProfile()` returns ENFORCE for PRODUCTION, ADVISORY for DEV/STAGING

#### ComplianceFrameworkType Enum
**File:** `cloudforge-core/src/main/java/com/cloudforge/core/enums/ComplianceFrameworkType.java`

**Values:**
- `SOC2` ("soc2") - SOC 2 Trust Services Criteria
- `PCI_DSS` ("pci-dss") - Payment Card Industry Data Security Standard
- `HIPAA` ("hipaa") - Health Insurance Portability and Accountability Act
- `GDPR` ("gdpr") - General Data Protection Regulation
- `ISO_27001` ("iso-27001") - ISO 27001 Information Security Management
- `FEDRAMP_MODERATE` ("fedramp-moderate") - FedRAMP Moderate baseline
- `FEDRAMP_HIGH` ("fedramp-high") - FedRAMP High baseline

**Helper Methods:**
- `getJsonValue()` - Returns JSON representation (e.g., "soc2")
- `getDisplayName()` - Returns human-readable name
- `getMatrixKey()` - Returns ComplianceMatrix key (e.g., "SOC2")
- `fromString(String)` - Parse from JSON value
- `parseCommaSeparated(String)` - Parse comma/space/plus separated list (e.g., "SOC2,HIPAA,PCI-DSS")
- `toCommaSeparated(List)` - Convert list to comma-separated string

**Backward Compatibility:**
- Supports multiple delimiters: `,`, ` `, `+`
- Example: "SOC2+HIPAA,PCI-DSS" parses to [SOC2, HIPAA, PCI_DSS]

---

## Main Workflows

### 1. CDK Application Synthesis

```
deployment-context.json (user config)
  ↓
cdk.json (context: { cfc: {...} })
  ↓
CDK App instantiation
  ↓
CloudForgeCommunitySample.java
  ↓
SystemContext.start(topology, runtime, security, iamProfile, cfc)
  ↓
Rules.installAll(ctx) - Load compliance rules
  ↓
createInfrastructureFactories() - VPC, ALB, EFS, etc.
  ↓
createApplicationSpecificFactories() - Fargate/EC2, Container
  ↓
executeDeferredActions() - Post-deployment hooks
  ↓
Stack synthesis → CloudFormation template
```

### 2. Infrastructure Creation Flow

```
SystemContext.createInfrastructureFactories()
  1. VpcFactory         → VPC, subnets, availability zones
  2. AlbFactory         → Application Load Balancer, security groups
  3. EfsFactory         → Elastic File System, access points
  4. LoggingFactory     → CloudWatch Log Groups
  5. GuardDutyFactory   → Threat detection (if enabled)
  6. InstanceSG         → EC2 security group (if EC2 runtime)
  7. TargetGroups       → ALB target groups
  → InfrastructureFactories record returned
```

### 3. Application-Specific Deployment

```
SystemContext.createJenkinsDeployment()
  1. createInfrastructureFactories() [above]
  2. createJenkinsSpecificFactories() [runtime-dependent]
     - If Fargate: FargateFactory, ContainerFactory
     - If EC2: Ec2Factory, user data configuration
  3. createDomainAndSslFactories() [if domain configured]
     - DomainFactory    → Route53 hosted zone
     - CertificateFactory → ACM certificate
  4. createSecurityFactories()
     - CognitoAuthenticationFactory (if cognitoAutoProvision)
     - OidcAuthenticationFactory (if OIDC configured)
     - ApplicationOidcFactory (if application-level OIDC)
  → JenkinsDeployment record returned
```

### 4. Compliance Validation Flow

```
SystemContext.start()
  ↓
Rules.installAll(ctx)
  ├─ RuntimeRules.install() - Compute validation
  ├─ TopologyRules.install() - Architecture validation
  ├─ SecurityRules.install() - Security controls
  ├─ IAMRules.install() - Permission validation
  └─ ComplianceRules (conditional per framework)
     ├─ PciDssRules (if PCI-DSS in complianceFrameworks)
     ├─ HipaaRules (if HIPAA in complianceFrameworks)
     ├─ Soc2Rules (if SOC2 in complianceFrameworks)
     └─ GdprRules (if GDPR in complianceFrameworks)
  ↓
During synthesis:
  ├─ cdk-nag validates CloudFormation templates
  ├─ cfn-guard validates against compliance rules
  └─ ComplianceMode determines failure handling
     ├─ "enforce" → Build fails on non-compliance
     └─ "advisory" → Warnings only
```

### 5. Database Provisioning Flow

```
provisionDatabase flag (config)
  ↓
RdsFactory.create()
  ├─ RDS instance (PostgreSQL/MySQL)
  ├─ DB security group
  ├─ Secrets Manager credentials
  └─ Store in SystemContext slots
  ↓
ApplicationSpec.configureUserData() uses connection info
  ├─ Inject DB_HOST, DB_PORT env vars
  ├─ Retrieve credentials from Secrets Manager
  └─ Configure application database settings
```

---

## Supported Applications

### CI/CD (3)
1. **Jenkins** - Automation server with pipeline support
2. **GitLab** - Complete DevOps platform (Git, CI/CD, container registry)
3. **Drone** - Cloud-native CI/CD

### Version Control (1)
4. **Gitea** - Lightweight Git service

### Monitoring (2)
5. **Grafana** - Observability dashboards
6. **Prometheus** - Metrics collection and alerting

### Analytics (2)
7. **Metabase** - Business intelligence and analytics
8. **Apache Superset** - Data exploration and visualization

### Databases (2)
9. **PostgreSQL** - Relational database
10. **Redis** - In-memory data store

### Artifact Registry (2)
11. **Nexus** - Universal artifact repository
12. **Harbor** - Container registry with security scanning

### Secrets Management (1)
13. **Vault** - HashiCorp Vault for secrets management

### Collaboration (1)
14. **Mattermost** - Team chat and collaboration

**Database Requirements:**
- **REQUIRED DB:** GitLab, Mattermost, Harbor, Superset (always provision RDS)
- **OPTIONAL DB:** Metabase, Grafana (choose RDS or embedded H2/SQLite)
- **NO DB:** Jenkins, Gitea, Nexus, Vault, Prometheus, Drone

---

## Compliance Frameworks

### ComplianceMatrix - Multi-Framework Control Mapping

**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ComplianceMatrix.java` (252+ lines)

**Purpose:** Maps CloudForge security controls to requirements across all supported frameworks

**Supported Standards:**
- PCI-DSS v3.2.1 (Payment Card Industry Data Security Standard)
- HIPAA Security Rule (45 CFR §164.308-316)
- SOC 2 Trust Services Criteria
- GDPR (Articles 32, 33, 34)
- NIST SP 800-53 (Rev 5)
- FedRAMP Moderate/High
- ISO 27001:2013

**SecurityControl Enum with FrameworkRequirement Mappings:**
- Each control maps to specific framework requirements
- RequirementLevel: REQUIRED | ADVISORY | NOT_APPLICABLE
- Enforcement depends on ComplianceMode: ENFORCE | ADVISORY | DISABLED

**Complete Security Controls (30+):**

**Encryption Controls:**
1. `ENCRYPTION_AT_REST` - EBS, EFS, S3, RDS encryption at rest
2. `ENCRYPTION_IN_TRANSIT` - TLS/SSL, EFS encryption in transit
3. `HTTPS_STRICT` - HTTPS-only mode for ALB/CloudFront
4. `CLOUDWATCH_LOGS_KMS_ENCRYPTION` - CloudWatch log group encryption
5. `SNS_ENCRYPTION` - SNS topic KMS encryption
6. `SQS_ENCRYPTION` - SQS queue KMS encryption

**Network Security Controls:**
7. `NETWORK_SEGMENTATION` - VPC, private subnets, security groups
8. `NETWORK_ISOLATION` - Private network mode, no direct internet access
9. `PRIVATE_SUBNETS` - Database and compute in private subnets
10. `VPC_FLOW_LOGS` - Network traffic monitoring
11. `NETWORK_FLOW_LOGS` - VPC Flow Logs for forensics

**Access Control:**
12. `ACCESS_CONTROL` - IAM, least privilege
13. `ACCESS_LOGGING` - ALB access logs, CloudTrail
14. `AUTHENTICATION` - SSO, OIDC, MFA
15. `MFA_ENFORCEMENT` - Multi-factor authentication required
16. `IAM_LEAST_PRIVILEGE` - Minimal IAM permissions

**High Availability:**
17. `MULTI_AZ_DEPLOYMENT` - Resources across multiple availability zones
18. `AUTO_SCALING` - Auto Scaling Groups for resilience
19. `DELETION_PROTECTION` - RDS, ALB deletion protection

**Data Protection:**
20. `DATA_BACKUP` - Automated backups, retention
21. `BACKUP_RETENTION` - Backup retention policies
22. `S3_VERSIONING` - S3 object versioning for data protection

**Threat Detection:**
23. `THREAT_DETECTION` - GuardDuty threat detection
24. `VULNERABILITY_SCANNING` - Macie, Inspector scanning
25. `INTRUSION_DETECTION` - GuardDuty, VPC Flow Logs

**Incident Response:**
26. `INCIDENT_RESPONSE_ALERTING` - SNS notifications for security events
27. `SECURITY_MONITORING` - CloudWatch alarms, Security Hub
28. `AUDIT_LOGGING` - CloudTrail, VPC Flow Logs, ALB access logs

**Key & Secrets Management:**
29. `KEY_MANAGEMENT` - KMS key rotation, policies
30. `KMS_KEY_ROTATION` - Automatic KMS key rotation
31. `SECRETS_MANAGEMENT` - Secrets Manager rotation
32. `SECRETS_MANAGER_ROTATION` - Credential rotation policies

**Monitoring & Compliance:**
33. `CLOUDWATCH_ALARMS` - Proactive alerting
34. `AWS_CONFIG_MONITORING` - Continuous compliance monitoring
35. `COMPLIANCE_REPORTING` - Audit Manager evidence collection
36. `ERROR_HANDLING` - Lambda DLQ, graceful degradation

**Compliance-First Configuration:**
- `getRequiredConfiguration()` method in FrameworkRules specifies framework minimums
- ComplianceMatrix resolves control-to-requirement mappings
- Precedence: User Config > Framework Requirements > Security Profile Defaults

### CFN-Guard Rules - Infrastructure Policy Validation

**Location:** `cloudforge-api/src/main/resources/cfn-guard/frameworks/`

CloudForge includes 13+ CFN-Guard rule files for CloudFormation template validation:

**Cross-Framework Guard Rules (Always Applied):**
1. **key-management.guard** (103 lines) - KMS key configuration, rotation, policies
2. **database-security.guard** (245 lines, 11KB) - RDS encryption, backup, deletion protection, multi-AZ
3. **advanced-monitoring.guard** (279 lines, 14KB) - CloudWatch, AWS Config, alarm configuration
4. **threat-protection.guard** (144 lines) - GuardDuty, Macie, Security Hub
5. **incident-response.guard** (152 lines) - SNS alerting, EventBridge rules
6. **compute-security.guard** (221 lines) - EC2/ECS security, IMDSv2, EBS encryption, termination protection
7. **elb-security.guard** (222 lines) - ALB/NLB access logging, TLS/SSL, deletion protection
8. **iam-security.guard** (261 lines) - IAM least privilege, wildcard detection, iam:PassRole restrictions
9. **lambda-security.guard** (172 lines) - Deprecated runtime detection, VPC config, DLQ, code signing
10. **messaging-security.guard** (182 lines) - SNS/SQS KMS encryption, DLQ configuration
11. **cdn-api-security.guard** (247 lines) - CloudFront/API Gateway hardening, origin config, logging

**Framework-Specific Guard Rules:**
12. **iso-27001-controls.guard** (166 lines) - ISO 27001 control validation
13. **gdpr-data-protection.guard** (updated with retention policies) - GDPR data protection requirements

**Total Guard Rules:** 2,500+ lines of infrastructure policy validation

**Validation Process:**
1. CDK synthesizes CloudFormation templates
2. CFN-Guard validates templates against applicable guard files
3. Violations reported based on ComplianceMode (ENFORCE blocks deployment, ADVISORY warns)
4. Maps to ComplianceMatrix SecurityControls for cross-framework consistency

### Framework Comparison

| Requirement | SOC2 | HIPAA | PCI-DSS | GDPR | ISO 27001 | FedRAMP |
|-------------|------|-------|---------|------|-----------|---------|
| **Min Password Length** | 12 | 14 | 8 | 12 | 12 | 14 |
| **Password Rotation** | 90 days | 90 days | 90 days | 90 days | 90 days | 90 days |
| **MFA Required** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Log Retention** | 2 years | 6 years | 1 year | 2 years | 2 years | 3 years |
| **Encryption** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **WAF** | Recommended | Recommended | Required | Recommended | Recommended | Required |
| **Threat Detection** | Recommended | Recommended | Required | Recommended | Recommended | Required |
| **Private Network** | Recommended | Required | Required | Recommended | Recommended | Required |
| **Storage Retention** | Optional | Required | Optional | Optional | Optional | Required |

### Testing Status
- ✅ **SOC2** - Fully tested in production
- ⚠️ **HIPAA, PCI-DSS, GDPR, ISO 27001, FedRAMP** - Configuration provided, not yet tested in production

### Key Compliance Controls

**SOC2 Type II:**
- IAM password policy enforcement (12+ chars, rotation)
- S3 versioning remediation
- CloudTrail audit logging
- 2-year log retention
- MFA enforcement
- Cost: ~$50-100/month

**HIPAA:**
- 14-char passwords with TOTP+SMS MFA
- Private network (VPC isolation)
- 6-year log retention
- Encrypted storage (EFS, RDS, S3)
- Bastion host for SSH access
- Cost: ~$150-250/month

**PCI-DSS:**
- WAF (Requirement 6.6)
- GuardDuty threat detection (Req 11.4)
- ALB access logging (Req 10.2)
- Certificate expiration monitoring (Req 4.1)
- 1-year log retention (Req 10.7)
- Network segmentation
- Cost: ~$200-300/month

**GDPR:**
- EU region deployment (data residency)
- Encryption at rest and in transit
- S3 versioning for data protection
- 2-year audit trails
- MFA for access control
- Cost: ~$50-100/month

---

## AWS Compliance & Audit Integration

### AWS Config Integration
**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java`

CloudForge integrates with AWS Config for continuous compliance monitoring and automated remediation.

**Configuration Fields:**
- `awsConfigEnabled` (boolean) - Enable AWS Config for compliance monitoring
- `createConfigInfrastructure` (boolean) - Create CloudFormation stack for AWS Config (recorder, delivery channel)
- `scopeConfigRulesToDeployment` (boolean) - Limit Config rules to this deployment only (vs all resources)

**AWS Config Rules Deployed:**

**Cross-Framework Rules (always deployed when awsConfigEnabled: true):**
- `GUARDDUTY_ENABLED` - GuardDuty threat detection enabled
- `CLOUDTRAIL_ENABLED` - CloudTrail audit logging enabled
- `CLOUDTRAIL_LOG_FILE_VALIDATION` - CloudTrail log file integrity validation
- `MULTI_REGION_CLOUDTRAIL` - CloudTrail enabled in all regions
- `VPC_FLOW_LOGS_ENABLED` - VPC Flow Logs for network monitoring
- `ELB_LOGGING_ENABLED` - Load balancer access logging
- `S3_BUCKET_ENCRYPTION` - S3 bucket encryption at rest
- `EBS_ENCRYPTION_BY_DEFAULT` - EBS volume encryption
- `RDS_STORAGE_ENCRYPTED` - RDS database encryption
- `EFS_ENCRYPTED` - EFS file system encryption
- `CLOUDWATCH_LOG_GROUP_ENCRYPTED` - CloudWatch log encryption
- `ALB_HTTPS_ONLY` - ALB HTTPS-only listeners
- `S3_BUCKET_SSL_REQUESTS` - S3 bucket SSL/TLS enforcement

**Framework-Specific Rules:**

**SOC2-Specific:**
- `IAM_USER_GROUP_MEMBERSHIP` - IAM users in groups
- `IAM_MFA_ENABLED` - MFA for IAM users
- `IAM_ROOT_MFA_ENABLED` - MFA for root account
- `S3_BUCKET_VERSIONING_ENABLED` - S3 versioning for data protection

**HIPAA-Specific:**
- All SOC2 rules plus:
- `PRIVATE_SUBNET_REQUIRED` - Database in private subnets
- `DB_DELETION_PROTECTION_ENABLED` - RDS deletion protection

**PCI-DSS-Specific:**
- All SOC2 + HIPAA rules plus:
- `WAF_ENABLED` - WAF for web application firewall
- `SECURITY_HUB_ENABLED` - Security Hub for findings aggregation
- `INSPECTOR_ENABLED` - Amazon Inspector for vulnerability scanning

**GDPR-Specific:**
- `DATA_ENCRYPTION_AT_REST` - Encryption for all data stores
- `DATA_ENCRYPTION_IN_TRANSIT` - TLS/SSL for all communications
- `BACKUP_ENABLED` - Automated backups configured

### AWS Audit Manager Integration
**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java`

CloudForge can automatically collect evidence for compliance audits using AWS Audit Manager.

**Configuration Fields:**
- `auditManagerEnabled` (boolean) - Enable AWS Audit Manager for automated evidence collection
- `auditManagerFrameworkId` (String) - Account-specific framework ID for Audit Manager assessment

**Supported Frameworks:**
- SOC 2 - Service Organization Control 2
- HIPAA - Health Insurance Portability and Accountability Act
- PCI-DSS v3.2.1 - Payment Card Industry Data Security Standard
- GDPR - General Data Protection Regulation
- NIST 800-53 - NIST Cybersecurity Framework
- ISO 27001 - Information Security Management

**Evidence Collection:**
- CloudTrail logs for user activity auditing
- AWS Config snapshots for resource configuration
- Security Hub findings for security posture
- GuardDuty findings for threat detection
- IAM credential reports for access control
- VPC Flow Logs for network monitoring

### Automated Remediation
**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java`

CloudForge supports automated remediation for common compliance violations.

**Remediation Flags:**
- `enableS3VersioningRemediation` - Auto-enable S3 versioning on non-compliant buckets
- `enableCloudTrailBucketAccessRemediation` - Auto-fix CloudTrail bucket access logging
- `enableRdsDeletionProtectionRemediation` - Auto-enable RDS deletion protection
- `enableRdsAutoMinorVersionUpgradeRemediation` - Auto-enable RDS minor version upgrades
- `enableSecurityHubRemediation` - Auto-remediate Security Hub findings
- `enableInspectorRemediation` - Auto-remediate Amazon Inspector findings
- `enableMacieRemediation` - Auto-remediate Amazon Macie findings
- `enableGuardDutyRemediation` - Auto-remediate GuardDuty findings

**Remediation Mechanism:**
- AWS Systems Manager Automation Documents
- Lambda functions triggered by AWS Config rule violations
- CloudFormation drift detection and auto-correction

### EC2 Auto Scaling Group Notifications
**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/compute/Ec2Factory.java`

When using EC2 runtime in STAGING or PRODUCTION security profiles, CloudForge creates SNS topics for Auto Scaling Group lifecycle notifications.

**Features:**
- SNS topic creation for ASG notifications
- KMS encryption for SNS topics (required for HIPAA/PCI-DSS compliance)
- SSL/TLS requirement for SNS publishers
- Notifications for scaling events:
  - Instance launch success/failure
  - Instance terminate success/failure
  - Instance refresh start/complete/failed

**Security Enhancements:**
- IMDSv2 enforcement on all EC2 instances (prevents SSRF attacks)
- KMS key rotation enabled
- SNS topic policy restricts access to Auto Scaling service

**Compliance Integration:**
- Maps to ComplianceMatrix.SecurityControl.INCIDENT_RESPONSE_ALERTING
- Required for HIPAA (45 CFR §164.308(a)(6)) - Security incident procedures
- Required for PCI-DSS (Requirement 10.6) - Review logs and security events

---

## Configuration Reference

### Minimal Dev Setup
```json
{
  "runtime": "fargate",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "DEV"
}
```

### Production with SSL & Cognito MFA
```json
{
  "runtime": "ec2",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "PRODUCTION",
  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "my-jenkins-auth",
  "cognitoMfaEnabled": true,
  "cognitoInitialAdminEmail": "admin@example.com",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4
}
```

### Multi-Framework Compliance (SOC2 + HIPAA + PCI-DSS) with AWS Config & Remediation
```json
{
  "runtime": "ec2",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "PRODUCTION",
  "lbType": "alb",
  "networkMode": "private-with-nat",
  "complianceMode": "enforce",
  "complianceFrameworks": "SOC2,HIPAA,PCI-DSS",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "scopeConfigRulesToDeployment": false,
  "auditManagerEnabled": true,
  "auditManagerFrameworkId": "arn:aws:auditmanager:us-east-1:123456789012:framework/abc123",
  "enableS3VersioningRemediation": true,
  "enableRdsDeletionProtectionRemediation": true,
  "enableSecurityHubRemediation": true,
  "guardDutyEnabled": true,
  "macieEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableEncryption": true,
  "logRetentionDays": 2190,
  "retainStorage": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "both",
  "cognitoInitialAdminEmail": "admin@example.com"
}
```

### GitLab with OIDC & Container Registry
```json
{
  "applicationId": "gitlab",
  "runtime": "ec2",
  "securityProfile": "PRODUCTION",
  "domain": "example.com",
  "subdomain": "gitlab",
  "enableSsl": true,
  "instanceType": "t3.large",
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "enableDockerRegistry": true,
  "enableSsh": true,
  "enableMetrics": true
}
```

---

## Common Development Tasks

### Building the Project

```bash
# Fast build (skip tests)
./mvnw -T1C -DskipTests install

# Full build with tests
./mvnw clean verify

# Single module build
./mvnw -pl cloudforge-api -am package

# Generate SBOM
mvn clean package -DskipTests
cat target/cfc-core-sbom.json

# Security scan
mvn dependency-check:check
open target/dependency-check-report.html
```

### Testing

```bash
# Quick syntax test
cd cfc-testing
cdk synth --context cfc=@deployment-context.json

# Full test suite
cd cfc-testing
./test-synth.sh

# Performance benchmarking
cd cfc-testing
./benchmark-synth.sh

# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify
```

### Deployment

```bash
# Interactive deployment (recommended for first-time users)
cd cfc-testing
mvn clean package
java -jar target/cfc-testing-3.1.0-SNAPSHOT.jar

# Direct deployment
cd cfc-testing
vi deployment-context.json  # Edit configuration
mvn clean package
cdk deploy --context cfc=@deployment-context.json

# Bootstrap AWS account (one-time)
cdk bootstrap aws://ACCOUNT-ID/REGION
```

---

## Plugin Development

### Creating an Application Plugin

1. **Implement `ApplicationSpec` interface:**
```java
package com.example.plugins;

@ApplicationPlugin
public class SonarQubeSpec implements ApplicationSpec {
    @Override public String getId() { return "sonarqube"; }
    @Override public String getName() { return "SonarQube"; }
    @Override public int getDefaultPort() { return 9000; }
    @Override public boolean requiresDatabase() { return true; }
    @Override public boolean supportsOidc() { return true; }
    // ... implement other methods
}
```

2. **Register plugin via ServiceLoader:**
Create `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`:
```
com.example.plugins.SonarQubeSpec
```

3. **Package and deploy:**
```bash
mvn clean package
# Copy JAR to classpath or install to local Maven repo
```

### Creating a Compliance Plugin

1. **Implement `FrameworkRules` interface:**
```java
package com.example.compliance;

@ComplianceFramework
public class Iso27001Rules implements FrameworkRules {
    @Override public String getFrameworkId() { return "ISO27001"; }
    @Override public String getFrameworkName() { return "ISO 27001"; }
    @Override public int getPriority() { return 20; }

    @Override
    public void install(SystemContext ctx) {
        // Install compliance rules
        ctx.addRule(new MinPasswordLengthRule(12));
        ctx.addRule(new MfaRequiredRule());
        // ... more rules
    }
}
```

2. **Register plugin via ServiceLoader:**
Create `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`:
```
com.example.compliance.Iso27001Rules
```

---

## Troubleshooting Guide

### Common Issues

**Issue:** `cdk synth` fails with "No ApplicationSpec found for ID: xyz"
- **Cause:** Missing or incorrectly registered application plugin
- **Solution:** Check `META-INF/services/` registration, verify plugin on classpath

**Issue:** "Validation failed: UNIQUE constraint violation"
- **Cause:** Multiple resources with same name/ID
- **Solution:** Ensure unique stack names, avoid duplicate resource IDs

**Issue:** RDS database not created
- **Cause:** `provisionDatabase` flag not set for optional DB apps
- **Solution:** Set `"provisionDatabase": true` for Metabase/Grafana if RDS needed

**Issue:** ALB health checks failing
- **Cause:** Incorrect health check path, instance not ready, security group misconfiguration
- **Solution:** Check application logs, verify security group rules, increase grace period

**Issue:** Compliance rules failing build
- **Cause:** Configuration doesn't meet framework requirements
- **Solution:** Review compliance framework requirements, set `complianceMode: "advisory"` for warnings only

**Issue:** OIDC authentication not working
- **Cause:** Incorrect issuer URL, client secret, or callback URL
- **Solution:** Verify OIDC configuration, check Secrets Manager for client secret, ensure callback URL matches ALB DNS

### Debug Techniques

1. **Enable verbose logging:**
```bash
export CDK_DEBUG=true
cdk synth --context cfc=@deployment-context.json
```

2. **Inspect synthesized CloudFormation:**
```bash
cdk synth --context cfc=@deployment-context.json > template.yaml
cat template.yaml
```

3. **Check CloudWatch Logs:**
```bash
aws logs tail /aws/ecs/jenkins-service --follow
```

4. **Validate configuration:**
```bash
# Run unit tests to validate configuration parsing
./mvnw test -Dtest=DeploymentContextTest
```

5. **Check compliance rule violations:**
```bash
# CDK Nag will output violations during synthesis
cdk synth 2>&1 | grep -i "error\|warning"
```

---

## Important Files & Locations

### Configuration
- **Deployment Context:** `deployment-context.json` (user-editable)
- **CDK Context:** `cdk.json` (CDK configuration)
- **Maven Config:** `pom.xml` (dependencies, plugins)

### Core Components
- **System Context:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/SystemContext.java` (1,045 lines)
- **Deployment Context:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/DeploymentContext.java` (536 lines)
- **Application Spec:** `cloudforge-core/src/main/java/com/cloudforge/core/interfaces/ApplicationSpec.java`
- **Framework Rules:** `cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java`
- **Compliance Matrix:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ComplianceMatrix.java` (252+ lines)

### Factories
- **VPC Factory:** `cloudforge-api/src/main/java/com/cloudforgeci/api/network/VpcFactory.java`
- **ALB Factory:** `cloudforge-api/src/main/java/com/cloudforgeci/api/ingress/AlbFactory.java`
- **Fargate Factory:** `cloudforge-api/src/main/java/com/cloudforgeci/api/compute/FargateFactory.java`
- **EC2 Factory:** `cloudforge-api/src/main/java/com/cloudforgeci/api/compute/Ec2Factory.java`
- **RDS Factory:** `cloudforge-api/src/main/java/com/cloudforgeci/api/database/RdsFactory.java`

### Compliance Rules (19+ Implementations)
- **SOC2:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/Soc2Rules.java` (524 lines)
- **HIPAA:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/HipaaRules.java` (596 lines)
- **HIPAA Organizational:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/HipaaOrganizationalRules.java`
- **PCI-DSS:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java` (705 lines)
- **GDPR:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/GdprRules.java` (520 lines)
- **GDPR Organizational:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/GdprOrganizationalRules.java`
- **FedRAMP Moderate:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/FedRampRules.java` (1,185 lines)
- **FedRAMP High:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/FedRampHighRules.java`
- **ISO 27001:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/Iso27001Rules.java`
- **Compute Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ComputeSecurityRules.java`
- **ELB Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ElbSecurityRules.java`
- **IAM Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/IamSecurityRules.java`
- **Lambda Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/LambdaSecurityRules.java`
- **Messaging Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/MessagingSecurityRules.java`
- **CDN/API Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/CdnApiSecurityRules.java`
- **Key Management:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/KeyManagementRules.java`
- **Database Security:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/DatabaseSecurityRules.java`
- **Advanced Monitoring:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/AdvancedMonitoringRules.java`
- **Threat Protection:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ThreatProtectionRules.java`
- **Incident Response:** `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/IncidentResponseRules.java`

### Documentation
- **Main README:** `README.md`
- **Plugin System:** `docs/plugins/PLUGIN-SYSTEM.md`
- **Compliance Overview:** `docs/compliance/README.md`
- **OIDC Integration:** `docs/applications/OIDC.md`
- **Database Guide:** `docs/databases/DATABASE-DEPLOYMENT-GUIDE.md`

---

## Code Style & Conventions

### Naming Conventions
- **Classes:** PascalCase (e.g., `SystemContext`, `VpcFactory`)
- **Methods:** camelCase (e.g., `createInfrastructure()`, `installRules()`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `DEFAULT_PORT`, `MAX_RETRIES`)
- **Packages:** lowercase (e.g., `com.cloudforge.core.interfaces`)

### Java Features Used
- **Records:** Immutable data classes (e.g., `DeploymentContext`, `InfrastructureFactories`)
- **Sealed Classes:** Restricted inheritance for domain models
- **Pattern Matching:** `instanceof` with pattern variables
- **Text Blocks:** Multi-line strings for user data scripts
- **Virtual Threads:** Concurrent factory initialization (experimental)

### Best Practices
1. **Immutability:** Prefer immutable data structures (records, `List.of()`, `Map.of()`)
2. **Null Safety:** Use `Optional<T>` for nullable returns, `Slot<T>` for lazy init
3. **Type Safety:** Leverage strong typing, avoid stringly-typed APIs
4. **Factory Pattern:** Use factories for complex object creation
5. **Dependency Injection:** Pass dependencies via constructors, not static access
6. **Documentation:** Javadoc for public APIs, inline comments for complex logic
7. **Testing:** Unit tests for business logic, integration tests for CDK synthesis

---

## Security Considerations

### Secrets Management
- **Never hardcode secrets** in code or configuration files
- **Use AWS Secrets Manager** for OIDC client secrets, database credentials
- **Use SSM Parameter Store** for non-sensitive configuration
- **Rotate credentials** according to compliance framework requirements

### IAM Best Practices
- **Principle of Least Privilege:** Grant minimum permissions required
- **IAM Profiles:** Use predefined profiles (Minimal, Standard, Extended)
- **Service Roles:** Create dedicated roles for each service
- **No Root Account:** Never use AWS root credentials

### Network Security
- **Private Subnets:** Use for production databases and compute
- **Security Groups:** Restrict ingress to known CIDR blocks
- **WAF:** Enable for production applications (required for PCI-DSS)
- **TLS/SSL:** Enable HTTPS for all production deployments
- **GuardDuty:** Enable threat detection for compliance frameworks

### Compliance Automation
- **AWS Config:** Enable for continuous compliance monitoring
- **CloudTrail:** Enable for audit logging
- **Automated Remediation:** Use remediation flags for auto-fixing non-compliant resources
- **Audit Manager:** Enable for automated evidence collection

---

## Testing Strategy

### Unit Tests
**Location:** `*/src/test/java/`
**Framework:** JUnit 5
**Coverage:** 50% instruction, 40% branch (enforced by JaCoCo)

**Test Categories:**
1. Configuration validation (DeploymentContext parsing)
2. Field introspection and visibility expressions
3. JSON serialization/deserialization
4. Compliance rule logic
5. Factory initialization

### Integration Tests
**Location:** `cfc-testing/src/test/java/`
**Purpose:** Validate CDK synthesis and CloudFormation templates

**Test Scenarios:**
1. Minimal deployment (dev, no auth, no SSL)
2. Production deployment (SSL, Cognito, multi-AZ)
3. Compliance frameworks (SOC2, HIPAA, PCI-DSS, GDPR)
4. All 14 applications
5. Runtime variants (EC2 vs Fargate)

### Compliance Testing
**Location:** `cloudforge-api/src/test/java/com/cloudforgeci/api/integration/deployment/TruthTableValidationTest.java`
**Purpose:** Systematic truth table validation across all deployment configurations

**Validation Layers:**
1. **Layer 1 (cdk-nag):** CloudFormation template validation using CDK Nag packs
2. **Layer 2 (FrameworkRules):** Java-based compliance rule validation
3. **Layer 3 (cfn-guard):** CFN-Guard template policy validation
4. **Layer 4 (AWS Config):** AWS Config rule deployment verification

**Test Coverage:**
- **108+ valid deployment configurations** tested systematically
- **200+ CSV test cases** covering single and multi-framework combinations
- **Cross-dimensional testing:** runtime × topology × security × compliance
- **Pass/fail scenarios** for each framework with expected violation counts

**Test Phases:**
1. Load truth-table.json metadata and CSV test matrices
2. Generate parameterized test cases for each configuration
3. Synthesize CDK CloudFormation templates
4. Extract and validate CloudFormation JSON
5. Verify resource counts and types match expectations
6. Generate JSONL compliance results (incremental)
7. Write CloudFormation templates to disk for audit linkage

**Compliance Report Generator:**
**File:** `cfc-testing/scripts/compliance-report-generator.py` (1,400+ lines)

**Features:**
- Sequential test execution to avoid JSII memory issues
- Interactive HTML dashboard with validation results
- Multi-layer validation tracking:
  - cdk-nag: status, pack counts, violations
  - FrameworkRules: violations, known gaps
  - cfn-guard: status, violations
  - AWS Config: deployment status, rule counts
- Advisory tracking (warnings that don't cause failure)
- Installed rules tracking per test configuration
- CloudFormation template download links
- Incremental results file (JSONL format)
- Secure XML parsing with defusedxml

**Test Results:**
- Historical reports preserved for regression detection
- Pagination support for large compliance reports
- Filtering by framework, severity, validation layer
- Three-tier violation severity: Advisory, Error, Informational

---

## Performance Characteristics

### Synthesis Time
- **Minimal deployment:** ~10-15 seconds
- **Production deployment:** ~30-45 seconds
- **Compliance frameworks:** +5-10 seconds per framework
- **All 14 applications:** ~5-7 minutes (parallel synthesis)

### Deployment Time
- **Fargate (single service):** ~5-8 minutes
- **EC2 (auto-scaling group):** ~8-12 minutes
- **With RDS database:** +10-15 minutes
- **With AWS Config:** +5-8 minutes

### Resource Counts
- **Minimal deployment:** ~30-40 CloudFormation resources
- **Production deployment:** ~60-80 resources
- **With compliance:** +20-30 resources (Config rules, CloudTrail, etc.)

### Cost Estimates
- **Dev (Fargate, no compliance):** ~$30-50/month
- **Staging (EC2, SOC2):** ~$100-150/month
- **Production (EC2, multi-framework):** ~$250-400/month
- **With RDS database:** +$50-100/month
- **With GuardDuty + WAF:** +$100-150/month

---

## Roadmap & Known Limitations

### Current Limitations
1. **Single Region:** Only supports single-region deployments (multi-region planned)
2. **Single Account:** Cross-account deployment not yet supported
3. **SAML Authentication:** In development, may have breaking changes
4. **Keycloak Integration:** Experimental
5. **Blue/Green Deployments:** Not yet implemented
6. **Auto-rollback:** Limited rollback automation
7. **Multi-framework compliance:** HIPAA, PCI-DSS, GDPR controls implemented but unverified (SOC2 AWS Config validated)
8. **FedRAMP:** Minimal implementation, untested

### Breaking Changes & Deprecations

**CloudForge 3.0.0:**
- **REMOVED:** `JENKINS_SINGLE_NODE` topology type
  - **Migration:** Use `JENKINS_SERVICE` topology instead
  - **Impact:** Deployment context files using `"topology": "JENKINS_SINGLE_NODE"` will fail

**Deprecated (Backward Compatible):**
- `"networkMode": "public-no-nat"` → Use `"networkMode": "public"` instead
- `"authMode": "jenkins-oidc"` → Use `"authMode": "application-oidc"` instead

**Legacy Alias Support:**
- Deprecated values still work but should be updated to new syntax
- JSON deserialization supports both old and new values

### Planned Features
- **Multi-region deployments** with cross-region replication
- **Multi-account support** via AWS Organizations
- **Blue/green deployment** strategies
- **Canary deployments** with automatic rollback
- **Cost optimization** recommendations
- **Performance monitoring** dashboards
- **Automated security patching**
- **Container image scanning** integration
- **Policy-as-Code** for organizational compliance

---

## Related Resources

### Official Documentation
- **Live Dashboard:** https://cloudforgeci.github.io/cfc-core/
- **GitHub Repository:** https://github.com/CloudForgeCI/cfc-core
- **Sample Project:** https://github.com/CloudForgeCI/cloudforge-sample
- **Maven Central:** https://central.sonatype.com/artifact/com.cloudforgeci/cloudforge-api

### External Dependencies
- **AWS CDK Docs:** https://docs.aws.amazon.com/cdk/v2/guide/home.html
- **CDK Nag:** https://github.com/cdklabs/cdk-nag
- **AWS Well-Architected:** https://aws.amazon.com/architecture/well-architected/

### Compliance Resources
- **SOC2:** https://www.aicpa.org/soc
- **HIPAA:** https://www.hhs.gov/hipaa/
- **PCI-DSS:** https://www.pcisecuritystandards.org/
- **GDPR:** https://gdpr.eu/
- **ISO 27001:** https://www.iso.org/isoiec-27001-information-security.html
- **FedRAMP:** https://www.fedramp.gov/

---

## Support & Community

### Getting Help
- **GitHub Issues:** https://github.com/CloudForgeCI/cfc-core/issues
- **Documentation:** Full docs at `docs/README.md`
- **Sample Configurations:** See `docs/examples/`

### Contributing
- **Prerequisites:** Java 21, Maven 3.9+, Node.js 18+, AWS CDK CLI
- **Guidelines:** See `CONTRIBUTING.md`
- **Code Style:** Follow existing patterns, run tests before PR
- **Security:** Report vulnerabilities to security@cloudforge.com

### License
Apache License 2.0 - See `LICENSE` file

**Disclaimer:** This software is "compliance-ready," not compliance-certified. Organizations are solely responsible for their own compliance assessments and regulatory requirements.

---

## Glossary

- **ALB:** Application Load Balancer
- **CDK:** AWS Cloud Development Kit
- **ECS:** Elastic Container Service
- **EFS:** Elastic File System
- **Fargate:** Serverless container runtime
- **IAM:** Identity and Access Management
- **OIDC:** OpenID Connect
- **RDS:** Relational Database Service
- **SBOM:** Software Bill of Materials
- **SSO:** Single Sign-On
- **VPC:** Virtual Private Cloud
- **WAF:** Web Application Firewall

---

---

## Recent Architectural Enhancements (3.1.0-SNAPSHOT)

### Type-Safe Enum Refactoring (Major - CloudForge 3.1.0)
**Commit:** ee82d70 - 15,281 insertions, 3,198 deletions across 201 files

**Changes:**
- Replaced string-based configuration with type-safe enums
- New enums: `AuthMode`, `NetworkMode`, `LoadBalancerType`, `ComplianceMode`, `ComplianceFrameworkType`
- Backward-compatible JSON deserialization for migration (supports legacy aliases)
- DeploymentContext refactored for type safety (536 lines)
- SystemContext expanded with orchestration improvements (1,045 lines)
- ComplianceMatrix created for multi-framework control mapping (252+ lines, 36+ SecurityControls)

**New Features:**
- ALB-OIDC authentication mode for zero-code-change OIDC
- ISOLATED network mode for no internet access (VPC endpoints required)
- NLB load balancer type for static IPs and TCP/UDP
- Type-safe compliance framework selection with multi-delimiter parsing
- Compliance mode defaults per security profile

### Security Hardening Enhancements

**ASG Notifications & IMDSv2 Enforcement (Commit d39f459):**
- Auto Scaling Group notifications to SNS topics
- SNS topic KMS encryption for compliance
- IMDSv2 enforcement on EC2 instances (prevents SSRF attacks)
- Compliance integration for all new resources

**Additional CFN-Guard Rules & Cross-Framework Security (Commit 4c8d28d):**
- **CdnApiSecurityRules** (247 lines) - CloudFront/API Gateway hardening, origin config, logging
- **ComputeSecurityRules** (221 lines) - EC2/Fargate security, IMDSv2, EBS encryption, termination protection
- **ElbSecurityRules** (222 lines) - ALB/NLB access logging, TLS/SSL, deletion protection
- **IamSecurityRules** (261 lines) - IAM least privilege, wildcard detection, iam:PassRole restrictions
- **LambdaSecurityRules** (172 lines) - Deprecated runtime detection, VPC config, DLQ, code signing
- **MessagingSecurityRules** (182 lines) - SNS/SQS KMS encryption, DLQ configuration
- **13+ CFN-Guard files** (2,500+ lines) for infrastructure policy validation
- Production SecurityProfile validation with CDK-nag integration and suppression rules

### Compliance Validation & Reporting (Commit 3933b23)

**Advisory/Error/Informational Levels:**
- Three-tier violation severity (Advisory, Error, Informational)
- Enhanced report table structure with filtering
- Pagination support for large compliance reports
- GDPR guard rule updates for retention policies
- Expanded TruthTableValidationTest (1,199 line addition) for 108+ configurations
- Compliance report generator Python script (1,400+ lines) with HTML dashboard
- Multi-layer validation tracking (cdk-nag, FrameworkRules, cfn-guard, AWS Config)
- Sequential test execution to avoid JSII memory issues
- Incremental JSONL results with CloudFormation template audit linkage

### AWS Config & Audit Manager Integration (CloudForge 3.1.0)
**File:** `cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java`

**Features:**
- **AWS Config Integration:** 30+ managed rules for continuous compliance monitoring
- **AWS Audit Manager Integration:** Automated evidence collection for SOC2, HIPAA, PCI-DSS, GDPR
- **Automated Remediation:** 8+ remediation flags for auto-fixing compliance violations
- **Framework-Specific Rules:** Conditional deployment based on enabled compliance frameworks
- **Remediation Mechanisms:** Systems Manager Automation, Lambda triggers, CloudFormation drift detection

**New Configuration Fields (10+):**
- `awsConfigEnabled`, `createConfigInfrastructure`, `scopeConfigRulesToDeployment`
- `auditManagerEnabled`, `auditManagerFrameworkId`
- `enableS3VersioningRemediation`, `enableCloudTrailBucketAccessRemediation`
- `enableRdsDeletionProtectionRemediation`, `enableRdsAutoMinorVersionUpgradeRemediation`
- `enableSecurityHubRemediation`, `enableInspectorRemediation`
- `enableMacieRemediation`, `enableGuardDutyRemediation`

### Truth Table Testing Infrastructure

**File:** `cloudforge-api/src/test/java/com/cloudforgeci/api/integration/deployment/TruthTableValidationTest.java`

**Coverage:** 108+ valid deployment configurations across all dimensions

**Test Strategy:**
- Parameterized tests for each configuration combination
- CloudFormation template synthesis validation
- Resource creation verification
- Resource count matching expectations
- Cross-dimensional testing (runtime × topology × security × compliance)

**Test Phases:**
1. Load truth-table.json metadata
2. Generate parameterized test cases
3. Synthesize CDK for each configuration
4. Extract and validate CloudFormation templates
5. Verify resource counts and types
6. Generate JSONL compliance results
7. Write CloudFormation templates for audit linkage

**CSV-Based Test Matrices (200+ configurations):**
- Single-framework validation (SOC2, HIPAA, PCI-DSS, GDPR)
- Multi-framework combinations
- Runtime variations (EC2 vs Fargate)
- Security profile variations (DEV vs STAGING vs PRODUCTION)
- Pass/fail scenarios for each framework
- Edge cases and advisory-level violations

---

**Last Updated:** 2025-12-28
**Document Version:** 2.0
**CloudForge Version:** 3.1.0-SNAPSHOT<>