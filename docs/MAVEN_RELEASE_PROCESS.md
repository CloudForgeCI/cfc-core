# Maven Release Process

[![Maven Central](https://img.shields.io/maven-central/v/com.cloudforgeci/cfc-core.svg)](https://central.sonatype.com/artifact/com.cloudforgeci/cfc-core)

This document describes how `cfc-core`'s Maven artifacts actually get published today.
(A much older version of this doc described a `main`-branch, conventional-commit-driven
auto-release workflow that doesn't exist anymore — this is a full rewrite.)

The badge above always reflects the real latest *published* release (live-queried from Central
each time it renders) — it can't show the in-progress `-SNAPSHOT` version on `develop`, only
real releases. For that, check `pom.xml` directly.

## Overview

Two separate workflows, in `.github/workflows/`:

- **`publish-snapshot.yml`** — the day-to-day pipeline. `develop`'s `pom.xml` always carries a
  `-SNAPSHOT` version.
  - **PR opened/updated against `develop`** → build, test, publish that `-SNAPSHOT` to Central's
    snapshots repository. Requesting review is what makes a snapshot available to downstream
    consumers (`cloudforge-manager-deployment`, `cloudforge-manager`), not waiting for the merge.
  - **Push to `develop`** (i.e. the PR actually merged) → same snapshot publish, **plus**
    auto-release: strip `-SNAPSHOT`, build/sign/publish that as a real numbered release to Maven
    Central (no re-test — the snapshot build already validated this exact code), tag `vX.Y.Z`,
    bump `develop`'s `pom.xml` to the next patch `-SNAPSHOT`, and push that bump commit with
    `[skip ci]` so it doesn't re-trigger the workflow.

  So **every merge to `develop` is both a snapshot and a real Central release** — deliberate,
  continuous-release design, not a staged/manual process.

- **`publish-maven-central.yml`** — a manual `workflow_dispatch`-only escape hatch. Only fires
  automatically on a push to `develop` if the root pom's `<version>` isn't already tagged
  (`v<version>`) in the repo, which never happens under the flow above (the only commit
  `publish-snapshot.yml` ever pushes back is a `-SNAPSHOT` bump). Useful for re-running a
  publish after a partial failure (`force: true` input) without going through the full
  snapshot→auto-release cycle again.

`cloudforge-manager-deployment` (a separate repo, its own single-module `pom.xml`, `com.cloudforgeci`
parent resolved from Central) has its own identically-shaped pair of workflows, publishing
itself the same way. `cloudforge-manager-deployment` **secrets are separate from `cfc-core`'s**
— GitHub secrets don't cross repos, even within the same org.

## What Gets Published

From `cfc-core`, one command publishes all of these together (same `-pl` list in both
workflows):

- `cfc-core` itself (the root/BOM `pom`)
- `cloudforge-core`
- `cloudforge-api`
- `cloudforge-localstack`
- `cloudforge-ministack`

**Not published:**
- `cfc-testing` — the reference/sample consumer, deliberately not part of the reactor's publish
  scope (see `pom.xml`'s own module comments)
- `cloudforge-manager-deployment` — separate repo, own independent versioning, own workflow
- `cloudforge-manager` — the Spring Boot app itself (LicenseSeat/Checkpoint A/B logic lives here);
  distribution strategy (Docker Hub vs. Central vs. something else) is still an open decision,
  deliberately not published anywhere yet

## Required Secrets

Configure under **Repository Settings → Secrets and variables → Actions**, separately in
`cfc-core` and in `cloudforge-manager-deployment`:

| Secret | Description |
|--------|-------------|
| `GPG_PRIVATE_KEY` | The full ASCII-armored private key block, base64-encoded as **one line** — see exact command below. Not just the base64 of the content between the markers; the `-----BEGIN.../-----END...` markers must be included in what gets base64-encoded. |
| `GPG_PASSPHRASE` | The key's passphrase |
| `CENTRAL_PORTAL_USERNAME` | Central Portal "Generate User Token" username half — **not** your account login |
| `CENTRAL_PORTAL_PASSWORD` | Same token's password half |

There is no `GPG_KEYNAME` secret and no `OSSRH_USERNAME`/`OSSRH_TOKEN` — those belonged to the
old Sonatype OSSRH staging flow, which Central Portal replaced.

### Generating and exporting the key

```bash
# Generate (RSA 4096 sign+cert primary, RSA 4096 encrypt subkey, 3-year expiry)
cat > /tmp/newkey.batch <<'EOF'
%echo Generating new signing key
Key-Type: RSA
Key-Length: 4096
Key-Usage: sign,cert
Subkey-Type: RSA
Subkey-Length: 4096
Subkey-Usage: encrypt
Name-Real: CloudForgeCI
Name-Email: support@cloudforgeci.com
Expire-Date: 3y
%ask-passphrase
%commit
EOF
gpg --batch --gen-key /tmp/newkey.batch
rm /tmp/newkey.batch

# Note the key ID
gpg --list-secret-keys --keyid-format long support@cloudforgeci.com

# Publish the PUBLIC key to a keyserver -- Central Portal needs to verify your
# signature against a publicly known key, or release validation rejects it
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# Export the private key for the GitHub secret -- full armor block INCLUDING the
# BEGIN/END markers, base64-encoded as one line. This exact form is what
# GPG_PRIVATE_KEY must contain.
gpg --armor --export-secret-keys <KEY_ID> | base64 -w0
```

Paste that whole base64 string as the `GPG_PRIVATE_KEY` secret value. `GPG_PASSPHRASE` is
whatever passphrase you gave it above.

### Central Portal setup

Sonatype's OSSRH/JIRA namespace-claim flow is retired. Current process:

1. Create an account at [central.sonatype.com](https://central.sonatype.com)
2. Verify the `com.cloudforgeci` namespace (GitHub repo ownership or DNS TXT record)
3. Log in → username → **View Account** → **Generate User Token** → copy the username/password
   halves into `CENTRAL_PORTAL_USERNAME`/`CENTRAL_PORTAL_PASSWORD`

## GPG Signing — How It's Actually Wired

The release Maven profile's `maven-gpg-plugin` config reads `<homedir>${env.GPG_HOMEDIR}</homedir>`
and `--pinentry-mode loopback`. Each signing job:

1. Installs **Homebrew's `gnupg`** on the runner and puts it first on `PATH`, ahead of Ubuntu's
   system-packaged GnuPG.
2. Decodes `GPG_PRIVATE_KEY` itself (`base64 -d`) and imports with plain `gpg --batch --import`
   — masking each line of the decoded multi-line armor block individually via `::add-mask::`
   before it's ever written anywhere (a single `::add-mask::` call only masks its first line for
   a multi-line value — this matters).
3. Writes `allow-loopback-pinentry` into a fresh `$GNUPGHOME/gpg-agent.conf` before anything else
   touches that homedir.
4. Exports both `GNUPGHOME` (the `gpg` CLI's own var) and `GPG_HOMEDIR` (what the Maven plugin
   config actually reads) pointing at the same imported keyring.
5. Passes `-Dgpg.passphrase="$MAVEN_GPG_PASSPHRASE"` explicitly on the `mvn ... deploy` command
   line, rather than relying on env-var auto-detection (version-dependent in `maven-gpg-plugin`).

The signing jobs are also pinned to `runs-on: ubuntu-22.04` (not `ubuntu-latest`).

**Why all of this**: a real, reproduced `gpg-agent` bug — `gpg: signing failed: Too much data for
IPC layer` — on GitHub's Ubuntu runners, on the very first sign, every time. Ruled out
individually and in combination: `maven-gpg-plugin` version (3.1.0 vs 3.2.4), `GNUPGHOME`/
`GPG_HOMEDIR` config, `allow-loopback-pinentry`, explicit passphrase, two completely different
GPG keys, the import mechanism (hand-rolled vs. `crazy-max/ghaction-import-gpg`), `gpg1` (turned
out to be a `gpg2` compatibility shim on Ubuntu, not real legacy GnuPG — same bug), and
`ubuntu-22.04` vs `ubuntu-latest`. Never once reproduced locally (macOS, Homebrew GnuPG 2.4.8).
The Homebrew-on-the-runner step is the current attempt at a fix, on the theory that it's specific
to Ubuntu's own GnuPG *build* rather than GnuPG in general. **As of this writing that fix has not
yet been confirmed working end-to-end against a real publish** — if you're reading this because
publishing is broken again, that's the place to start looking, and `Import GPG signing key` /
`Deploy SNAPSHOT` step logs are where every one of the above symptoms actually showed up.

## Verification

- **Central Portal**: [central.sonatype.com](https://central.sonatype.com) → **Deployments**
- **Maven Central search** (snapshots don't appear here, only real releases, and indexing lags
  15-30+ minutes): [search.maven.org](https://search.maven.org/search?q=g:com.cloudforgeci)
- **Snapshots repository** (browsable directly):
  `https://central.sonatype.com/repository/maven-snapshots/com/cloudforgeci/`
- **Git tags**: a successful auto-release pushes `v<version>` — check `git tag -l` /
  the repo's Tags page

## Troubleshooting

Real issues actually hit while building this pipeline, not theoretical:

| Symptom | Cause | Fix |
|---|---|---|
| `gpg: signing failed: No secret key` / import reports 0 keys | Malformed `GPG_PRIVATE_KEY` secret — missing `BEGIN`/`END` markers, or base64 of just the inner content | Re-export with the exact one-line command above, markers included |
| `Misformed armored text` (from `crazy-max/ghaction-import-gpg` specifically) | That action's own base64 auto-detection doesn't reliably handle this secret's format | Decode with plain `base64 -d` yourself first (see step 2 above), don't rely on the action's detection |
| `gpg: signing failed: No pinentry` | `--pinentry-mode loopback` is silently ignored by `gpg-agent` without an explicit `allow-loopback-pinentry` permission | Write `allow-loopback-pinentry` into `$GNUPGHOME/gpg-agent.conf` before importing |
| `gpg: signing failed: Too much data for IPC layer` | Still under investigation — see the GPG Signing section above | Currently: Homebrew's `gnupg` on the runner instead of the system package |
| Non-resolvable parent POM / dependency for a downstream repo (`cloudforge-manager-deployment`, `cloudforge-manager`, `cfc-testing`) | The SNAPSHOT they depend on hasn't actually been published yet — check whether `publish-snapshot.yml` has run (successfully) on `cfc-core` recently, and whether the downstream repo's own `pom.xml` has a `<repositories>` block pointing at `https://central.sonatype.com/repository/maven-snapshots/` | Open/update a PR against `develop` in the repo that owns the missing artifact to trigger its snapshot publish; add the snapshots `<repositories>` block if it's missing |
| `Root pom version 'X' isn't a SNAPSHOT` | `develop`'s `pom.xml` isn't on a `-SNAPSHOT` version, which `publish-snapshot.yml` requires by design | `develop` should always carry `-SNAPSHOT`; the auto-release job is what's responsible for the brief non-SNAPSHOT window (never actually pushed as its own commit) |

## Rollback

Maven Central doesn't allow re-publishing or deleting a version. If a release needs to be
undone, publish a new version with the fix and note the bad version in release notes — normal
Maven Central practice, not `cfc-core`-specific.
