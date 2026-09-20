# CloudForge CI

[![Maven Central](https://img.shields.io/maven-central/v/com.cloudforgeci/cloudforge-api)](https://central.sonatype.com/artifact/com.cloudforgeci/cloudforge-api)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

CloudForge CI is an open-source Java framework, built on the AWS CDK, for deploying
containerized applications to AWS. You describe a deployment in a small JSON file
(`deployment-context.json`): which application, EC2 or Fargate, a security profile, and
optional domain, authentication, and compliance settings. CloudForge synthesizes a
CloudFormation stack with the VPC, load balancer, storage, database, logging, and IAM
resources that application needs. The same template can be deployed to AWS or, without an
AWS account, to a local MiniStack or LocalStack emulator.

> CloudForge implements and validates infrastructure controls mapped to compliance
> frameworks. It is not compliance-certified and does not make a deployment compliant on its
> own. You remain responsible for your own assessments, audits, and organizational controls.
> The software is provided "AS IS" under the [Apache License 2.0](LICENSE).

## Features

- **Application catalog**: built-in specifications for CI/CD and code quality (Jenkins,
  GitLab, Drone, SonarQube), version control (Gitea), monitoring (Grafana, Prometheus),
  analytics (Metabase, Superset), databases (PostgreSQL, Redis), artifact registries (Nexus,
  Harbor), secrets (Vault), collaboration (Mattermost), and PHP CMS, commerce, forum, wiki,
  LMS, and CRM platforms (WordPress, WooCommerce, Drupal, Joomla, TYPO3, Concrete CMS,
  October CMS, Magento, PrestaShop, OpenCart, Sylius, Bagisto, phpBB, Flarum, MyBB, MediaWiki,
  Moodle, SuiteCRM, UNA). Additional applications can be added as plugins through Java
  `ServiceLoader`.
- **Runtimes**: Amazon ECS on Fargate or EC2 Auto Scaling groups behind an Application Load
  Balancer.
- **Security profiles**: `dev`, `staging`, and `production` profiles set defaults for
  networking, encryption, logging, backups, and compliance enforcement.
- **Authentication**: ALB-level or application-level OIDC, depending on what each application
  supports, with Amazon Cognito (optionally auto-provisioned), an external OIDC provider, or
  IAM Identity Center.
- **Managed dependencies**: Amazon RDS for applications that need a database, and S3 media
  storage, Redis, and a CloudFront distribution for the CMS topology.
- **Compliance validation**: controls mapped to SOC 2, PCI DSS, HIPAA, and GDPR, checked at
  synthesis time (framework rules and cdk-nag), optionally with cfn-guard, and at runtime with
  AWS Config rules and remediation.
- **Local emulators**: deploy the synthesized template to MiniStack or LocalStack for
  development and testing.

Published test, coverage, and compliance reports: <https://cloudforgeci.github.io/cfc-core/>

## Prerequisites

- Java 25 and Maven 3.9+
- For AWS deployments: Node.js, the [AWS CDK CLI](https://docs.aws.amazon.com/cdk/v2/guide/cli.html)
  (`npm install -g aws-cdk`), and AWS credentials
- For local deployments: Docker; `LOCALSTACK_AUTH_TOKEN` when using LocalStack
- Optional: [`cfn-guard`](https://github.com/aws-cloudformation/cloudformation-guard) for
  template validation when `complianceMode` is `enforce`

## Quick Start

### 1. Build

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core
mvn clean install                                      # tests are skipped by default
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true
```

`cfc-testing` is the sample application in this repository. It contains the Interactive
Deployer, a command-line tool that prompts for a configuration, saves it to
`deployment-context.json`, and then synthesizes or deploys it.

### 2. Run locally without an AWS account

Start an emulator from the platform menu (choose `ministack` or `localstack`, then `start`):

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
```

Then configure and deploy an application:

```bash
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Answer the prompts, then choose **6** (Deploy to MiniStack) or **8** (Deploy to LocalStack).
MiniStack and LocalStack share port 4566, so run one at a time. See the
[Local Emulator Quick Start](docs/guides/LOCAL_EMULATOR_QUICK_START.md) for details.

### 3. Deploy to AWS

```bash
cd cfc-testing
cdk bootstrap          # once per account and region
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Choose **2** (Deploy to AWS). After the Interactive Deployer has saved
`deployment-context.json`, you can also use the CDK CLI directly. `cdk.json` runs the same
entry point, which synthesizes that file without prompting (and synthesizes nothing if the
file does not exist):

```bash
cdk diff
cdk deploy
cdk destroy <stackName>
```

### Use CloudForge in your own project

The [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample) repository is a
standalone project with the same layout as `cfc-testing`. To add CloudForge to an existing
Maven project, import the BOM and depend on `cloudforge-api`, using the latest version shown
on [Maven Central](https://central.sonatype.com/artifact/com.cloudforgeci/cloudforge-api):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.cloudforgeci</groupId>
      <artifactId>cfc-core</artifactId>
      <version>${cloudforge.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-api</artifactId>
  </dependency>
</dependencies>
```

See the [sample project BOM template](docs/architecture/cloudforge-sample-bom.template.md)
for a complete POM and project layout.

## Minimal deployment context

```json
{
  "stackName": "jenkins-dev",
  "applicationId": "jenkins",
  "runtime": "fargate",
  "securityProfile": "dev"
}
```

This deploys Jenkins on Fargate with the `dev` profile, reachable through the load balancer's
DNS name over HTTP. Fields you leave out take their defaults from the security profile and the
application specification. Add a domain, TLS, and Cognito sign-in with:

```json
{
  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "example-jenkins-auth"
}
```

## Documentation

- **[Advanced guide](docs/ADVANCED.md)**: configuration reference, example configurations,
  authentication, databases, backups, scaling, compliance, local emulators, testing, and
  command-line reference
- [Documentation index](docs/README.md)
- [Interactive Deployer](docs/guides/INTERACTIVE_DEPLOYER.md)
- [Application guides](docs/guides/applications/README.md) and
  [CMS guides](docs/guides/cms/README.md)
- [Compliance documentation](docs/compliance/README.md)
- [Plugin system](docs/plugins/PLUGIN-SYSTEM.md)

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup,
build and test commands, and pull request process. Release history is in
[CHANGELOG.md](CHANGELOG.md).

## Support

- Bugs and feature requests: [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- Security issues: see [SECURITY.md](SECURITY.md); do not open a public issue
- Sponsorship: [SPONSORS.md](SPONSORS.md)

## License

Apache License 2.0. See [LICENSE](LICENSE).
