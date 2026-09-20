# Maven Release Process

[![Maven Central](https://img.shields.io/maven-central/v/com.cloudforgeci/cfc-core.svg)](https://central.sonatype.com/artifact/com.cloudforgeci/cfc-core)

This page describes how the `cfc-core` Maven artifacts are published. The badge shows the
latest release on Maven Central; the in-progress `-SNAPSHOT` version is in the root
`pom.xml` on `develop`.

## Published modules

Both publishing workflows deploy the same modules:

- `cfc-core` (the root POM, used as a BOM)
- `cloudforge-core`
- `cloudforge-api`
- `cloudforge-localstack`
- `cloudforge-ministack`

`cfc-testing` is a sample consumer and is not published. Projects maintained in other
repositories (for example `cloudforge-manager-deployment`) are released from their own
workflows.

## Workflows

### `publish-snapshot.yml` (continuous release)

`develop` always carries a `-SNAPSHOT` version. The workflow has four jobs:

1. **build-test**: `mvn -Pci ... clean verify` for the published modules. Nothing else runs
   unless this passes.
2. **publish-snapshot**: deploys the current `-SNAPSHOT` to the Central Portal snapshots
   repository. Runs for pull requests against `develop` (when published modules change) and
   for pushes to `develop`, so downstream projects can resolve a snapshot during review.
3. **release** (pushes to `develop` only): removes `-SNAPSHOT`, builds, signs, and publishes
   that version to Maven Central without re-running tests, then tags `vX.Y.Z`. If the tag
   already exists, the job does nothing.
4. **bump-develop** (pushes to `develop` only, in parallel with release): sets the next patch
   `-SNAPSHOT` version and pushes that commit with `[skip ci]`.

Every merge to `develop` therefore produces a snapshot and a numbered release. Contributors
should not edit `<version>` in pull requests; `bump-develop` is the only job that advances it.

### `publish-maven-central.yml` (manual)

Runs on `workflow_dispatch`, and on pushes to `develop` that touch the published modules. It
publishes only when the root POM's version is not already tagged, so it is normally a no-op
after `publish-snapshot.yml` has released. Use it to re-run a publish after a partial failure,
with the `force` input set to `true` to publish even if the version is tagged. The
`central-publishing-maven-plugin` is configured with `ignorePublishedComponents`, so
re-publishing a version that already reached Central does not fail.

## Required secrets

Configure these under **Settings > Secrets and variables > Actions**. Secrets are per
repository, so each repository that publishes needs its own copy.

| Secret | Value |
|---|---|
| `GPG_PRIVATE_KEY` | The ASCII-armored private key, including the `BEGIN`/`END` lines, base64-encoded on one line. |
| `GPG_PASSPHRASE` | The key's passphrase. |
| `CENTRAL_PORTAL_USERNAME` | Username half of a Central Portal user token (not the account login). |
| `CENTRAL_PORTAL_PASSWORD` | Password half of the same token. |

### Create and export a signing key

```bash
cat > /tmp/newkey.batch <<'EOF'
%echo Generating signing key
Key-Type: RSA
Key-Length: 4096
Key-Usage: sign,cert
Subkey-Type: RSA
Subkey-Length: 4096
Subkey-Usage: encrypt
Name-Real: CloudForgeCI
Name-Email: <signing-email>
Expire-Date: 3y
%ask-passphrase
%commit
EOF
gpg --batch --gen-key /tmp/newkey.batch
rm /tmp/newkey.batch

gpg --list-secret-keys --keyid-format long <signing-email>   # note the key ID

# Central verifies signatures against a public keyserver.
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# Value for GPG_PRIVATE_KEY (armor block with markers, base64 on one line).
gpg --armor --export-secret-keys <KEY_ID> | base64 -w0     # on macOS: base64 | tr -d '\n'
```

### Central Portal access

1. Sign in at [central.sonatype.com](https://central.sonatype.com).
2. Verify the `com.cloudforgeci` namespace (GitHub repository ownership or a DNS TXT record).
3. Open **View Account > Generate User Token** and store the two halves as
   `CENTRAL_PORTAL_USERNAME` and `CENTRAL_PORTAL_PASSWORD`.

## How signing is configured

The `release` profile's `maven-gpg-plugin` reads its keyring from `${env.GPG_HOMEDIR}` and uses
`--pinentry-mode loopback`. Each signing job:

1. runs on `macos-latest` and installs GnuPG with Homebrew;
2. decodes `GPG_PRIVATE_KEY` with `base64 -d`, masks each line of the decoded key in the log,
   and imports it into a fresh `GNUPGHOME`;
3. writes `allow-loopback-pinentry` to `$GNUPGHOME/gpg-agent.conf`, which `gpg-agent` needs
   before it honors loopback pinentry;
4. exports both `GNUPGHOME` (for the `gpg` CLI) and `GPG_HOMEDIR` (for the Maven plugin) with
   the same path;
5. passes the passphrase explicitly as `-Dgpg.passphrase` on the `mvn deploy` command line.

Signing runs on macOS because `gpg-agent` on GitHub's Linux runners fails with
`gpg: signing failed: Too much data for IPC layer`, independent of the GnuPG build, plugin
version, key, or agent configuration. The job that only bumps the version runs on Linux.

## Verifying a release

- Central Portal deployments: [central.sonatype.com](https://central.sonatype.com) > **Deployments**
- Released artifacts (indexing can take 30 minutes or more):
  [search.maven.org](https://search.maven.org/search?q=g:com.cloudforgeci)
- Snapshots: `https://central.sonatype.com/repository/maven-snapshots/com/cloudforgeci/`
- Tags: `git tag -l 'v*'` or the repository's tags page

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `gpg: signing failed: No secret key`, or the import reports 0 keys | `GPG_PRIVATE_KEY` is missing the `BEGIN`/`END` lines, or is base64 of only the inner content | Re-export with the command above. |
| `gpg: signing failed: No pinentry` | `allow-loopback-pinentry` is missing, so `gpg-agent` ignores loopback mode | Write it to `$GNUPGHOME/gpg-agent.conf` before importing. |
| `gpg: signing failed: Too much data for IPC layer` | `gpg-agent` on Linux runners | Run signing jobs on macOS. |
| A downstream project cannot resolve the parent POM or a dependency | The snapshot has not been published, or the project lacks the snapshots repository | Check the latest `publish-snapshot.yml` run, and add `https://central.sonatype.com/repository/maven-snapshots/` to the project's `<repositories>`. |
| `Root pom version 'X' isn't a SNAPSHOT` | `develop` is not on a `-SNAPSHOT` version | Restore a `-SNAPSHOT` version; only the release job uses a non-snapshot version, and it never commits it. |

## Rolling back

Maven Central does not allow a version to be replaced or deleted. To correct a bad release,
publish a new version with the fix and note the affected version in the release notes.
