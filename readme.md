# CloudForgeCI — cfc-core

Core libraries for CloudForgeCI. This repository contains foundational APIs and CDK constructs used by the community and enterprise stacks.

**Organization:** cloudforgeci  
**Company:** CloudForgeCI  
**Repo:** https://github.com/CloudForgeCI/cfc-core

```
cfc-core/
  pom.xml                  # parent (packaging=pom)
  cloudforge-api/          # API: config models, types, shared contracts
  cloudforge-core/         # Core: CDK constructs & builders
```

---

## What is this?
`cfc-core` provides the building blocks (Java libraries) that higher‑level wrappers use to assemble complete Jenkins deployments on AWS. It is consumed by the **cloudforge-community** repository (wrapper modules) and any custom CDK apps you write.

- **cloudforge-api** – data models and configuration contracts (pure Java).
- **cloudforge-core** – AWS CDK v2 constructs and helpers (Jenkins on EC2/Fargate, VPC, ALB, etc.).

Companion repo (wrapper library): https://github.com/CloudForgeCI/cloudforge-community

---

## Prerequisites

- **Java 21 (JDK 21+)**
- **Maven 3.9+** (or use the Maven Wrapper `./mvnw` if committed)
- **Node.js 18+** (required by AWS CDK apps at synth time)
- **Git 2.30+**
- **GPG** (only for publishing signed releases to Maven Central)

---

## Local Development (SNAPSHOT workflow)

Clone and build everything into your local `~/.m2`:

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core

# fast build without tests while iterating
./mvnw -T1C -DskipTests install

# full verification
./mvnw clean verify
```

Work on a single module:

```bash
# API only (+ build what it depends on)
./mvnw -q -pl cloudforge-api -am package

# Core constructs only
./mvnw -q -pl cloudforge-core -am package
```

Run tests:

```bash
# all modules
./mvnw test

# just core
./mvnw -pl cloudforge-core -am test
```

If a downstream project still uses a cached SNAPSHOT, force updates:

```bash
./mvnw -U clean install
```

---

## Using cfc-core from other projects

In your project’s `pom.xml` add dependencies (choose the version you need):

```xml
<dependencies>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-api</artifactId>
    <version>2.0.0</version>
  </dependency>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-core</artifactId>
    <version>2.0.0</version>
  </dependency>
</dependencies>
```

If you are consuming published artifacts from Maven Central, replace with the released version (e.g., `1.0.0`).

---

## Release Process (Maven Release Plugin)

This repository uses the classic **Maven Release Plugin** flow to promote from `x.y.z-SNAPSHOT` → `x.y.z`, tag, and bump to the next `x.y.(z+1)-SNAPSHOT`.

Dry run (no changes committed):

```bash
./mvnw -B -Prelease release:prepare -DdryRun   -DreleaseVersion=1.0.0   -DdevelopmentVersion=1.0.1-SNAPSHOT   -Dtag=v1.0.0
```

Perform the real release:

```bash
# prepare: bumps versions, commits, tags
./mvnw -B -Prelease release:prepare   -DreleaseVersion=1.0.0   -DdevelopmentVersion=1.0.1-SNAPSHOT   -Dtag=v1.0.0

# perform: checks out the tag under target/checkout and runs `mvn deploy`
./mvnw -B -Prelease -Pcentral release:perform
```

**Publishing to Maven Central:** configure the `central-publishing-maven-plugin` in the parent POM and add a `server` with id `central` in `~/.m2/settings.xml` using your Central user token. Artifacts must be GPG-signed.

Example `~/.m2/settings.xml` fragment:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

Deploy **SNAPSHOTs** (after enabling snapshots in the Central Portal):

```bash
./mvnw -Pcentral -DskipTests deploy
```

---

## Troubleshooting

- **Wrong Java version**: ensure CLI and IDE use JDK 21. Set `JAVA_HOME` accordingly.
- **Live AWS lookups in tests**: avoid; prefer synth + assertions with stubbed inputs.
- **`NoClassDefFoundError: software/constructs/Construct`**: align consumer with `software.constructs:constructs:10.x` and the matching `aws-cdk-lib` 2.x; reinstall SNAPSHOTs locally if needed.
- **Release plugin edits the POM unexpectedly**: ensure you use literal `<version>x.y.z-SNAPSHOT</version>` in the parent POM; let modules inherit the parent version.

---

## License

Apache License 2.0 — see `LICENSE`.
