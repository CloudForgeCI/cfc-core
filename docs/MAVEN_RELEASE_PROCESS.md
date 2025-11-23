# Maven Release Process

This document describes the automated Maven release process for `cfc-core`.

## Overview

The project uses an automated GitHub Actions workflow to:
1. **Auto-bump** the version based on commit messages or manual selection
2. **Build and test** the release
3. **Sign artifacts** with GPG
4. **Publish** to Maven Central
5. **Create** GitHub releases and tags
6. **Update** to the next development version

## Versioning Strategy

### Semantic Versioning

We follow [Semantic Versioning 2.0.0](https://semver.org/):
- **MAJOR** (X.0.0): Breaking changes
- **MINOR** (x.X.0): New features, backward compatible
- **PATCH** (x.x.X): Bug fixes, backward compatible

### Version Bump Detection

When pushing to `main`, the workflow automatically determines the version bump type:

#### Automatic Detection (from commit messages)

| Commit Message Pattern | Version Bump | Example |
|------------------------|--------------|---------|
| `BREAKING CHANGE:` in commit body | **MAJOR** | 2.0.6 → 3.0.0 |
| `feat!:` or `fix!:` | **MAJOR** | 2.0.6 → 3.0.0 |
| `feat:` or `feat(scope):` | **MINOR** | 2.0.6 → 2.1.0 |
| `fix:`, `chore:`, `docs:`, etc. | **PATCH** | 2.0.6 → 2.0.7 |

#### Manual Selection

Trigger the workflow manually via GitHub Actions UI and select:
- **patch** - Bug fixes (2.0.6 → 2.0.7)
- **minor** - New features (2.0.6 → 2.1.0)
- **major** - Breaking changes (2.0.6 → 3.0.0)

## Triggering a Release

### Automatic Release (Recommended)

1. **Merge PR to `main`**:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/my-feature
   # ... make changes ...
   git commit -m "feat: add new feature"
   git push origin feature/my-feature
   # Create PR to main, get approval, and merge
   ```

2. **Use conventional commits** to control version bumping:
   ```bash
   # Patch release (2.0.6 → 2.0.7)
   git commit -m "fix: resolve authentication bug"

   # Minor release (2.0.6 → 2.1.0)
   git commit -m "feat: add new IAM role configuration"

   # Major release (2.0.6 → 3.0.0)
   git commit -m "feat!: redesign security configuration API"
   # or
   git commit -m "feat: redesign security API

   BREAKING CHANGE: SecurityConfig constructor signature changed"
   ```

3. **Workflow runs automatically** when changes are pushed to `main`

### Manual Release

1. Go to **Actions** → **Maven Release**
2. Click **Run workflow**
3. Select branch: `main`
4. Choose version bump type: `patch`, `minor`, or `major`
5. Click **Run workflow**

## What Happens During a Release

### Step-by-Step Process

1. **Checkout**: Fetches the latest code from `main`
2. **Version Calculation**:
   - Reads current version from `pom.xml` (e.g., `2.0.6-SNAPSHOT`)
   - Determines bump type (patch/minor/major)
   - Calculates next release version (e.g., `2.0.7`)
   - Calculates next dev version (e.g., `2.0.8-SNAPSHOT`)

3. **Version Update (Release)**:
   - Updates `pom.xml` to release version `2.0.7`
   - Commits: `chore: bump version to 2.0.7 [skip ci]`

4. **Build and Test**:
   - Runs `mvn clean verify -P release`
   - Executes all unit tests
   - Verifies code coverage thresholds
   - Generates Javadoc and source JARs

5. **Deploy to Maven Central**:
   - Signs artifacts with GPG
   - Publishes to Sonatype Central Portal
   - Auto-publishes to Maven Central

6. **Create Git Tag**:
   - Tags commit as `v2.0.7`

7. **Version Update (Development)**:
   - Updates `pom.xml` to next dev version `2.0.8-SNAPSHOT`
   - Commits: `chore: bump version to 2.0.8-SNAPSHOT [skip ci]`

8. **Push Changes**:
   - Pushes version commits to `main`
   - Pushes tag `v2.0.7`

9. **Create GitHub Release**:
   - Creates release with changelog
   - Attaches SBOM (Software Bill of Materials)

## Required Secrets

Configure these in **GitHub Repository Settings** → **Secrets and variables** → **Actions**:

### Maven Central (Sonatype)

| Secret | Description | How to Get |
|--------|-------------|------------|
| `OSSRH_USERNAME` | Sonatype username | Register at [issues.sonatype.org](https://issues.sonatype.org) |
| `OSSRH_TOKEN` | Sonatype token | Generate in Sonatype profile |

### GPG Signing

| Secret | Description | How to Get |
|--------|-------------|------------|
| `GPG_PRIVATE_KEY` | GPG private key (ASCII-armored) | `gpg --armor --export-secret-keys KEY_ID` |
| `GPG_PASSPHRASE` | GPG key passphrase | The passphrase you set when creating the key |
| `GPG_KEYNAME` | GPG key ID | `gpg --list-secret-keys --keyid-format LONG` |

### GitHub Token

| Secret | Description |
|--------|-------------|
| `GITHUB_TOKEN` | Automatically provided by GitHub Actions |

## Setting Up GPG for Signing

### Generate GPG Key

```bash
# Generate a new GPG key
gpg --full-generate-key

# Select:
# - Key type: RSA and RSA
# - Key size: 4096
# - Expiration: 0 (no expiration) or set as needed
# - Real name: Your name
# - Email: Your verified GitHub email

# List keys and note the key ID
gpg --list-secret-keys --keyid-format LONG

# Output example:
# sec   rsa4096/ABCD1234EF567890 2024-01-01 [SC]
#       ^^^^^^^^^^^^^^^^^^^^ This is your KEY_ID
```

### Export GPG Key for GitHub

```bash
# Export private key (ASCII-armored)
gpg --armor --export-secret-keys ABCD1234EF567890 > private-key.asc

# Copy the entire contents of private-key.asc to GPG_PRIVATE_KEY secret
cat private-key.asc

# Upload public key to keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EF567890
```

### Configure Secrets

1. Go to **Repository Settings** → **Secrets and variables** → **Actions**
2. Add the following secrets:
   - `GPG_PRIVATE_KEY`: Contents of `private-key.asc`
   - `GPG_PASSPHRASE`: The passphrase for your GPG key
   - `GPG_KEYNAME`: Your key ID (e.g., `ABCD1234EF567890`)

## Maven Central Setup

### Register with Sonatype

1. Create account at [issues.sonatype.org](https://issues.sonatype.org)
2. Create a JIRA ticket to claim your group ID (e.g., `com.cloudforgeci`)
3. Verify domain ownership (GitHub repository or DNS TXT record)
4. Wait for approval (~1 business day)

### Generate User Token

1. Log in to [central.sonatype.com](https://central.sonatype.com)
2. Click your username → **View Account**
3. Click **Generate User Token**
4. Copy the username and password
5. Add to GitHub secrets:
   - `OSSRH_USERNAME`: The generated username
   - `OSSRH_TOKEN`: The generated password

## POM Configuration

The `pom.xml` is already configured with:

- **Parent POM**: `com.cloudforgeci:cloudforge:1.1.1`
- **Version**: `2.0.6` (auto-bumped by workflow)
- **Release Profile**: Includes GPG signing, Javadoc, sources
- **Central Publishing Plugin**: Deploys to Sonatype Central Portal
- **SCM Section**: GitHub repository URLs
- **Distribution Management**: Maven Central repository

## Verification

### Check Release Status

1. **GitHub**:
   - Go to **Releases** → verify new release is created
   - Go to **Tags** → verify tag `v2.0.7` exists
   - Go to **Actions** → verify workflow completed successfully

2. **Maven Central**:
   - Visit [search.maven.org](https://search.maven.org/search?q=g:com.cloudforgeci%20AND%20a:cfc-core)
   - Note: Indexing may take 2-4 hours

3. **Sonatype Central Portal**:
   - Log in to [central.sonatype.com](https://central.sonatype.com)
   - Go to **Deployments** → verify deployment status

### Test Published Artifact

```xml
<!-- Add to a test project pom.xml -->
<dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cfc-core</artifactId>
    <version>2.0.7</version>
</dependency>
```

```bash
# Clear local cache and force download from Maven Central
rm -rf ~/.m2/repository/com/cloudforgeci/cfc-core/2.0.7
mvn dependency:get -Dartifact=com.cloudforgeci:cfc-core:2.0.7
```

## Troubleshooting

### Common Issues

#### 1. GPG Signing Fails

**Error**: `gpg: signing failed: No secret key`

**Solution**:
- Verify `GPG_PRIVATE_KEY` secret contains the full ASCII-armored key
- Ensure key starts with `-----BEGIN PGP PRIVATE KEY BLOCK-----`
- Check `GPG_KEYNAME` matches the key ID

#### 2. Maven Central Deployment Fails

**Error**: `401 Unauthorized`

**Solution**:
- Verify `OSSRH_USERNAME` and `OSSRH_TOKEN` are correct
- Regenerate token in Sonatype Central Portal
- Ensure Sonatype account is approved for `com.cloudforgeci` group

#### 3. Version Already Exists

**Error**: `Version 2.0.7 already exists`

**Solution**:
- Maven Central doesn't allow re-deploying the same version
- Delete the Git tag: `git push --delete origin v2.0.7`
- Manually trigger workflow with the next version

#### 4. Tests Fail During Release

**Solution**:
- Fix tests on `develop` branch first
- Ensure PR validation passes before merging to `main`
- Check [cfc-validation.yml](.github/workflows/cfc-validation.yml) results

#### 5. Push to Main Fails

**Error**: `failed to push some refs`

**Solution**:
- Ensure workflow has write permissions
- Check branch protection rules allow `github-actions[bot]` to push
- Verify `GITHUB_TOKEN` has sufficient permissions

## Rollback Procedure

If a release needs to be rolled back:

1. **Do NOT delete from Maven Central** (not allowed)
2. **Release a new version** with the fix
3. **Update documentation** to skip the problematic version
4. **Notify users** via GitHub release notes

## Development Workflow

### Feature Development

```bash
# Work on develop branch
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/my-feature

# Make changes and commit with conventional commits
git commit -m "feat: add new feature"

# Push and create PR to develop
git push origin feature/my-feature

# After PR approval, merge to develop
# After testing, merge develop to main to trigger release
```

### Hotfix Workflow

```bash
# Create hotfix branch from main
git checkout main
git pull origin main
git checkout -b hotfix/critical-fix

# Make fix and commit
git commit -m "fix: resolve critical security issue"

# Push and create PR to main
git push origin hotfix/critical-fix

# Merge triggers automatic patch release
```

## Conventional Commits Reference

Use these commit message formats to control version bumping:

| Type | Description | Version Bump |
|------|-------------|--------------|
| `fix:` | Bug fix | PATCH |
| `feat:` | New feature | MINOR |
| `feat!:` | Breaking feature | MAJOR |
| `fix!:` | Breaking bug fix | MAJOR |
| `BREAKING CHANGE:` | In commit body | MAJOR |
| `chore:` | Maintenance | PATCH |
| `docs:` | Documentation | PATCH |
| `style:` | Code style | PATCH |
| `refactor:` | Code refactoring | PATCH |
| `perf:` | Performance | PATCH |
| `test:` | Tests | PATCH |
| `ci:` | CI/CD changes | PATCH |

### Examples

```bash
# Patch release (2.0.6 → 2.0.7)
git commit -m "fix: handle null pointer in SecurityConfig"

# Minor release (2.0.6 → 2.1.0)
git commit -m "feat: add CloudTrail auto-remediation support"

# Major release (2.0.6 → 3.0.0)
git commit -m "feat!: redesign IAM role configuration API"

# Major release with body
git commit -m "feat: redesign security configuration

BREAKING CHANGE: SecurityConfig constructor now requires ComplianceMode parameter"
```

## Best Practices

1. **Always use conventional commits** for automatic version bumping
2. **Test thoroughly on develop** before merging to main
3. **Run local builds** with release profile: `mvn clean verify -P release`
4. **Update CHANGELOG** for significant releases
5. **Monitor Maven Central sync** after deployment (2-4 hours)
6. **Verify artifacts** are accessible before announcing release
7. **Tag important milestones** with annotated tags
8. **Keep GPG key secure** and backed up
9. **Rotate Sonatype tokens** periodically
10. **Document breaking changes** in release notes

## Monitoring

### Workflow Notifications

- **Success**: GitHub Action summary shows release details
- **Failure**: GitHub Action failure notification sent to committers
- **Artifacts**: SBOM and build reports uploaded as artifacts

### Maven Central Status

Check deployment status:
- Sonatype Central Portal: [central.sonatype.com](https://central.sonatype.com)
- Maven Central Search: [search.maven.org](https://search.maven.org/search?q=g:com.cloudforgeci%20AND%20a:cfc-core)
- Maven Central Repository: [repo1.maven.org](https://repo1.maven.org/maven2/com/cloudforgeci/cfc-core/)

## Security Considerations

### Secret Protection in Workflow

The Maven release workflow implements multiple layers of security to prevent secret leakage:

1. **GitHub Secret Masking**: GitHub Actions automatically masks all secret values in logs
2. **Environment Variables Only**: Secrets are passed via environment variables, never as command-line arguments
3. **Disabled Command Echoing**: `set +x` disables bash command echoing during sensitive operations
4. **Minimal GPG Output**: GPG operations suppress verbose key information
5. **No Secret Logging**: Scripts never echo or print secret values

### GPG Key Security

1. **Never commit GPG private keys** to repository
2. **Use strong passphrase** (20+ characters, mix of characters)
3. **Backup key to secure location** (encrypted offline storage)
4. **Revoke key if compromised** immediately
5. **Upload public key to keyserver** for verification
6. **Set key expiration** (recommended: 2-3 years)

### Token Security

1. **Use GitHub encrypted secrets** for all credentials
2. **Rotate tokens every 90 days** as a best practice
3. **Limit token scope** to minimum required permissions
4. **Monitor token usage** in Sonatype Central Portal
5. **Never share tokens** across projects or teams
6. **Revoke unused tokens** immediately

### Access Control

1. **Limit who can push to `main`** branch
2. **Require PR reviews** before merge (minimum 1-2 reviewers)
3. **Enable branch protection rules**:
   - Require status checks to pass
   - Require up-to-date branches
   - Include administrators in restrictions
4. **Use CODEOWNERS file** for critical paths
5. **Enable 2FA** for all maintainers
6. **Audit access logs** regularly

## Additional Resources

- [Maven Central Publishing Guide](https://central.sonatype.org/publish/publish-guide/)
- [Semantic Versioning](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [GPG Signing Guide](https://docs.github.com/en/authentication/managing-commit-signature-verification)

## Support

For issues with the release process:

1. Check workflow logs in GitHub Actions
2. Review this documentation
3. Check Sonatype Central Portal for deployment status
4. Open an issue in the repository with:
   - Workflow run URL
   - Error messages
   - Steps to reproduce
