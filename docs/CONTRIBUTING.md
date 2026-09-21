---
id: contributing
title: Contributing to CloudForge CI
sidebar_label: Contributing
---

# Contributing to CloudForge CI

Thank you for your interest in contributing to CloudForge CI. This page mirrors
[CONTRIBUTING.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/CONTRIBUTING.md) in the repository root. It covers the
development setup, the build and test commands, and how changes are reviewed.

- [Code of Conduct](#code-of-conduct)
- [Development setup](#development-setup)
- [Reporting issues](#reporting-issues)
- [Submitting changes](#submitting-changes)
- [Where code belongs](#where-code-belongs)
- [Coding standards](#coding-standards)
- [Testing](#testing)
- [Documentation](#documentation)
- [Adding an application](#adding-an-application)
- [License](#license)

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive
environment for all contributors.

## Development setup

### Prerequisites

- Java 25 and Maven 3.9+
- Docker, to run MiniStack or LocalStack locally
- Node.js and the AWS CDK CLI (`npm install -g aws-cdk`), and an AWS account, only for
  deploying to AWS

### Get the code

Fork the repository on GitHub, then:

```bash
git clone https://github.com/<your-username>/cfc-core.git
cd cfc-core
git remote add upstream https://github.com/CloudForgeCI/cfc-core.git
```

### Build

Tests are skipped by default (`skipTests=true` in the root `pom.xml`); the `ci` profile
enables them.

```bash
mvn clean install                                          # build all modules, no tests
mvn -T1C clean install -Djacoco.skip=true                  # faster parallel build
mvn clean verify -Pci                                      # build with tests and coverage checks
mvn -pl cloudforge-api -am install                         # one module and its dependencies
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true  # sample application
```

See [Advanced commands](ADVANCED.md#advanced-commands) for more.

## Reporting issues

Use the [issue templates](https://github.com/CloudForgeCI/cfc-core/issues/new/choose).
Include steps to reproduce, expected and actual behavior, your environment (OS, Java version,
deployment target, AWS region), the relevant part of your `deployment-context.json` with
secrets removed, and any error output.

Prefix the title with the owning module when you know it, for example `[module:localstack]`:

| Prefix | Module |
|---|---|
| `module:core` | `cloudforge-core` |
| `module:api` | `cloudforge-api` |
| `module:ministack` | `cloudforge-ministack` |
| `module:localstack` | `cloudforge-localstack` |
| `module:sample` | `cfc-testing`, documentation, or the sample entry point |

Report security vulnerabilities privately as described in [SECURITY.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/SECURITY.md).

## Submitting changes

1. Create a branch from `develop`:
   ```bash
   git fetch upstream
   git checkout -b feature/your-feature-name upstream/develop
   ```
2. Make your change, with tests and documentation updates.
3. Run the tests for the modules you changed, for example `mvn -pl cloudforge-api -am -Pci verify`.
4. Commit with a clear message and push to your fork.
5. Open a pull request against `develop`. Describe the change, reference related issues
   (for example "Fixes #123"), and list any breaking changes.

A maintainer reviews each pull request. Every merge to `develop` is published as a release
(see [Maven Release Process](MAVEN_RELEASE_PROCESS.md)), so keep `develop` releasable.
Do not change the `<version>` in `pom.xml`; the release workflow manages it.

## Where code belongs

Put changes in the module that owns the behavior:

| Module | Owns |
|---|---|
| `cloudforge-core` | Contracts: `DeploymentConfig`, enums, `ApplicationSpec` and other interfaces, local-emulator interfaces (`com.cloudforge.core.local`) |
| `cloudforge-api` | `CloudForgeDeployment`, application specifications, CDK factories, compliance rules |
| `cloudforge-ministack` | MiniStack template adapter, deployer, and platform runtime |
| `cloudforge-localstack` | LocalStack template adapter, deployer, and platform runtime |
| `cfc-testing` | Sample entry point (`InteractiveDeployer`, `LocalDeploymentShell`), example plugins, and scripts; not library logic |

Emulator-specific fixes go in the target module, CMS and factory fixes in `cloudforge-api`,
and shared interface changes in `cloudforge-core`.

## Coding standards

- Follow standard Java conventions: `PascalCase` classes, `camelCase` methods,
  `UPPER_SNAKE_CASE` constants, lowercase packages.
- Add JavaDoc to public APIs.
- Keep methods focused and nesting shallow.
- Write comments that explain what the code does and why a non-obvious choice was made.
  Keep investigation history in pull requests and issues, not in comments.

## Testing

- New features need tests; bug fixes need a regression test.
- The build enforces JaCoCo coverage minimums in `verify` with the `ci` profile.
- Prefer tests in the module that owns the behavior. Keep `cfc-testing` tests focused on
  entry-point wiring and context propagation.

```bash
mvn -pl cloudforge-api -Pci test                                  # one module
mvn -pl cloudforge-api -Pci test -Dtest=ComplianceFactoryTest     # one class
mvn -pl cloudforge-api -Pci test -Dtest=TruthTableValidationTest  # compliance truth tables
mvn -pl cloudforge-ministack -Pci,ministack test                  # includes tests that need a running MiniStack
mvn -f cfc-testing/pom.xml test                                   # sample application tests
```

See [Compliance Truth Tables](testing/COMPLIANCE_TRUTH_TABLES.md) and
[Extended Testing](guides/EXTENDED-TESTING.md) for the synthesis and validation scripts.

## Documentation

- Update documentation in the same pull request as the behavior it describes.
- The root [readme.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/readme.md) is a short entry point. Put configuration and command
  details in [docs/ADVANCED.md](ADVANCED.md) and topic guides under `docs/`.
- Application guides live in `docs/guides/applications/` and `docs/guides/cms/`; example
  deployment contexts in `docs/examples/`.
- When adding or moving a page, update `docs/web/sidebars.js` and the
  [documentation index](README.md). See
  [Documentation Maintenance](DOCUMENTATION_SETUP.md).
- Write plainly: describe behavior and limitations, and avoid marketing language.

## Adding an application

1. Implement `com.cloudforge.core.interfaces.ApplicationSpec` (and `DatabaseSpec`, `CmsSpec`,
   or OIDC integration interfaces as needed) in the appropriate package under
   `cloudforge-api/src/main/java/com/cloudforgeci/api/application/`.
2. Register the class in
   `cloudforge-api/src/main/resources/META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`.
3. Add tests for the specification.
4. Add a guide under `docs/guides/applications/` (or `docs/guides/cms/`) and an example
   context under `docs/examples/applications/`.

Applications can also live in your own project as plugins; see the
[Application Plugin Guide](plugins/APPLICATION-PLUGIN-GUIDE.md) and the
`CraftCmsApplicationSpec` example in `cfc-testing`.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](https://github.com/CloudForgeCI/cfc-core/blob/develop/LICENSE).
