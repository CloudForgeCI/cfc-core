---
id: contributing
title: Contributing to CloudForge CI
sidebar_label: Contributing
---

# Contributing to CloudForge CI

Thank you for your interest in contributing to CloudForge CI! This document provides guidelines for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Documentation](#documentation)

---

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

---

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/CloudForgeCI/cfc-core.git
   cd cfc-core
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/CloudForgeCI/cfc-core.git
   ```

---

## Development Setup

### Prerequisites

- **Java 21+** (OpenJDK recommended)
- **Maven 3.9+**
- **Node.js 18+**
- **AWS CDK CLI** (`npm install -g aws-cdk`)
- **AWS Account** (for testing deployments)

### Build Commands

```bash
# Fast build (skip tests)
mvn -T1C -DskipTests install

# Full build with tests
mvn clean verify

# Build single module
mvn -pl cloudforge-api -am package

# Run tests only
mvn test

# Run specific test
mvn test -Dtest=YourTestClass
```

---

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
2. If not, create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Your environment (OS, Java version, AWS region)
   - Relevant logs or error messages

### Suggesting Features

1. Open a GitHub issue with the `enhancement` label
2. Describe the feature and its use case
3. Explain why it would be valuable
4. Consider implementation approaches

### Submitting Changes

1. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make your changes following our [Coding Standards](#coding-standards)
3. Add or update tests as needed
4. Update documentation
5. Commit with clear messages:
   ```bash
   git commit -m "Add feature: description of what you did"
   ```
6. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
7. Open a Pull Request

---

## Pull Request Process

1. **Before submitting:**
   - Ensure all tests pass: `mvn clean verify`
   - Run code formatting (if applicable)
   - Update documentation for any changed functionality
   - Add tests for new features

2. **PR Description:**
   - Describe what the PR does
   - Reference related issues (e.g., "Fixes #123")
   - Include screenshots for UI changes
   - List any breaking changes

3. **Review Process:**
   - Maintainers will review your PR
   - Address feedback and comments
   - Once approved, a maintainer will merge

4. **After Merge:**
   - Delete your feature branch
   - Update your fork:
     ```bash
     git checkout main
     git pull upstream main
     git push origin main
     ```

---

## Coding Standards

### Java Code

- Follow standard Java conventions
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Keep methods focused and concise
- Avoid deep nesting (max 3-4 levels)

### Code Organization

- Place new features in appropriate packages:
  - `cloudforge-core`: Core interfaces and annotations
  - `cloudforge-api`: Implementation classes
  - `cfc-testing`: Test utilities and examples

### Naming Conventions

- Classes: `PascalCase` (e.g., `JenkinsApplicationSpec`)
- Methods: `camelCase` (e.g., `applicationId()`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_PORT`)
- Packages: `lowercase` (e.g., `com.cloudforgeci.api.application`)

---

## Testing

### Test Requirements

- All new features must include tests
- Bug fixes should include regression tests
- Aim for >80% code coverage for new code

### Test Types

1. **Unit Tests:**
   ```java
   @Test
   public void testApplicationId() {
       ApplicationSpec spec = new JenkinsApplicationSpec();
       assertEquals("jenkins", spec.applicationId());
   }
   ```

2. **Integration Tests:**
   - Located in `cfc-testing/src/test/java/`
   - Test complete deployment scenarios
   - Use `cdk synth` to validate CloudFormation

3. **Truth Table Tests:**
   - Test compliance rule combinations
   - See [COMPLIANCE_TRUTH_TABLES.md](docs/testing/COMPLIANCE_TRUTH_TABLES.md)

### Running Tests

```bash
# All tests
mvn test

# Specific module
mvn -pl cloudforge-api test

# Integration tests
cd cfc-testing
./test-synth.sh

# Compliance validation
cd cfc-testing
mvn test -Dtest=ComplianceTruthTableTest
```

---

## Documentation

### Documentation Standards

- Update README.md for major features
- Add application guides for new applications
- Document configuration options
- Include examples and use cases

### Documentation Locations

- **Main README:** `/readme.md`
- **Compliance Docs:** `/docs/compliance/`
- **Application Guides:** `/docs/guides/applications/`
- **Setup Guides:** `/docs/setup/`
- **Examples:** `/docs/examples/`

### Writing Style

- Use clear, concise language
- Include code examples
- Add troubleshooting sections
- Link to related documentation

---

## Adding New Applications

To add a new application:

1. **Create ApplicationSpec:**
   ```java
   package com.cloudforgeci.api.application.category;

   public class MyAppApplicationSpec implements ApplicationSpec {
       @Override
       public String applicationId() {
           return "myapp";
       }
       // Implement other methods...
   }
   ```

2. **Register in ServiceLoader:**
   - Add to `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`

3. **Add Application Guide:**
   - Create `/docs/guides/applications/myapp.md`
   - Follow the template from existing guides

4. **Add Example Configuration:**
   - Create `/docs/examples/examples/myapp-dev.json`
   - Include production example if applicable

5. **Update Documentation:**
   - Add to main README application list
   - Update application catalog
   - Add to plugin ecosystem docs

---

## License

By contributing to CloudForge CI, you agree that your contributions will be licensed under the Apache License 2.0.

---

## Questions?

- Open a GitHub Discussion
- Comment on relevant issues
- Check existing documentation in `/docs`

---

**Thank you for contributing to CloudForge CI!**
