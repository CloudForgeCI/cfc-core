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

## Testing & Validation

### Unit Tests

Run the complete test suite:

```bash
# All modules
./mvnw test

# Just API module
./mvnw -pl cloudforge-api -am test

# Just Core module  
./mvnw -pl cloudforge-core -am test
```

### CDK Synthesis Testing

The `cfc-testing` application provides comprehensive testing of all runtime, topology, and security profile combinations. Navigate to the testing directory and run the complete test suite:

```bash
cd cfc-testing
rm -rf cdk.out cdk.context.json  # Clear CDK cache
```

#### Working Combinations ✅

These combinations are fully functional and should pass synthesis:

```bash
# EC2 + Service + Production + Domain + SSL
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"service","securityProfile":"production"}'

# EC2 + Service + Production + Domain + No SSL
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"EC2","topology":"service","securityProfile":"production"}'

# EC2 + Service + Production + No Domain
cdk synth -c cfc='{"enableSsl":false,"runtime":"EC2","topology":"service","securityProfile":"production"}'

# Fargate + Service + Production + Domain + No SSL
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'

# Fargate + Service + Production + No Domain
cdk synth -c cfc='{"enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'
```

#### Known Issues ❌

These combinations currently have known issues and may fail synthesis:

```bash
# EC2 + Node topology (single-node architectural incompatibility)
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"node","securityProfile":"production"}'

# Fargate + SSL (HTTP listener missing default action)
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'
```

#### Complete Test Matrix

Run all combinations systematically using the provided test script:

```bash
# Run the complete test suite
./test-synth.sh
```

Or run individual tests manually:

```bash
cd cfc-testing
rm -rf cdk.out cdk.context.json

# Individual test examples
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"service","securityProfile":"production"}'
cdk synth -c cfc='{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'
```

#### Test Results Summary

| Combination | Status | Notes |
|-------------|--------|-------|
| EC2 + Service + Production + Domain + SSL | ✅ SUCCESS | Working perfectly |
| EC2 + Service + Production + Domain + No SSL | ✅ SUCCESS | Working perfectly |
| EC2 + Service + Production + No Domain | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Production + Domain + No SSL | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Production + No Domain | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Production + Domain + SSL | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Dev + Domain + SSL | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Staging + Domain + SSL | ✅ SUCCESS | Working perfectly |
| EC2 + Node + Production + Domain + SSL | ✅ SUCCESS | Fixed - HTTP listener routing resolved |
| EC2 + Node + Dev + Domain + SSL | ✅ SUCCESS | Fixed - HTTP listener routing resolved |

**Success Rate:** 10/10 combinations (100%) 🎉

**Recent Fixes:**
- ✅ **DNS Record Duplication**: Fixed duplicate DNS record creation using `dnsRecordsCreated` slot-based approach
- ✅ **HTTP Listener Routing**: Fixed "Jenkins is starting up..." issue by configuring HTTP listeners to route to Fargate services in SSL mode
- ✅ **Target Group Configuration**: Resolved ALB target group creation and listener configuration for both HTTP and HTTPS

### Performance Benchmarking

The `benchmark-synth.sh` script provides comprehensive performance testing for CDK synthesis operations. It allows you to select specific test cases and run multiple iterations to measure synthesis performance.

#### Running Benchmarks

```bash
# Run the benchmark tool
./benchmark-synth.sh
```

The script will:
1. **Display available test cases** (1-10) with descriptive names
2. **Prompt for test selection** (validates input)
3. **Prompt for number of runs** (defaults to 10 if empty)
4. **Run benchmark** with progress indicators
5. **Show statistics** and save results to file

#### Available Test Cases

| # | Test Case | Status | Description |
|---|-----------|--------|-------------|
| 1 | EC2 + Service + Production + Domain + SSL | ✅ Working | Full production setup with SSL |
| 2 | EC2 + Service + Production + Domain + No SSL | ✅ Working | Production setup without SSL |
| 3 | EC2 + Service + Production + No Domain | ✅ Working | Minimal production setup |
| 4 | Fargate + Service + Production + Domain + No SSL | ✅ Working | Fargate production without SSL |
| 5 | Fargate + Service + Production + No Domain | ✅ Working | Minimal Fargate setup |
| 6 | EC2 + Node + Production + Domain + SSL | ❌ Known Issue | Single-node topology issue |
| 7 | EC2 + Node + Dev + Domain + SSL | ❌ Known Issue | Single-node topology issue |
| 8 | Fargate + Service + Production + Domain + SSL | ✅ Working | Fargate production with SSL |
| 9 | Fargate + Service + Dev + Domain + SSL | ✅ Working | Fargate dev with SSL |
| 10 | Fargate + Service + Staging + Domain + SSL | ✅ Working | Fargate staging with SSL |

#### Benchmark Output

The script generates detailed performance metrics:

```
📊 Benchmark Results:
====================
runs=20
min=2.345678
median=2.456789
p95=2.567890
max=2.678901
avg=2.456789
```

#### Benchmark Results File

Detailed timing data is saved to `cfc-testing/synth_times.txt`:

```
# synth timings (seconds) - EC2 + Service + Production + Domain + SSL
2.345678
2.456789
2.567890
...
```

#### Use Cases

- **Performance Regression Testing**: Compare synthesis times across different versions
- **Configuration Optimization**: Identify which configurations are fastest/slowest
- **Resource Planning**: Estimate synthesis time for CI/CD pipelines
- **Debugging**: Identify performance bottlenecks in specific configurations

#### Example Usage

```bash
# Benchmark a working configuration
./benchmark-synth.sh
# Select: 1 (EC2 + Service + Production + Domain + SSL)
# Runs: 30
# Result: Detailed performance metrics

# Benchmark a failing configuration (to measure failure time)
./benchmark-synth.sh
# Select: 6 (EC2 + Node + Production + Domain + SSL)
# Runs: 10
# Result: Error handling and failure timing
```

#### Configuration Options

The `cfc` context parameter supports the following options:

- **`runtime`**: `"EC2"` | `"FARGATE"`
- **`topology`**: `"service"` | `"node"`
- **`securityProfile`**: `"dev"` | `"staging"` | `"production"`
- **`domain`**: Custom domain name (e.g., `"cloudforgeci.com"`)
- **`subdomain`**: Subdomain for the application (e.g., `"jenkins"`)
- **`enableSsl`**: `true` | `false`

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
