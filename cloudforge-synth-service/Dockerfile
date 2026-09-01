# syntax=docker/dockerfile:1
# cloudforge-synth-service — the jsii/AWS-CDK synthesis sidecar for cloudforge-manager's native
# image. See ManagerSynthesisService's javadoc (cloudforge-manager) and this module's own pom.xml
# comment for why it exists: jsii's Java<->Node.js dynamic-proxy bridge isn't GraalVM
# native-image-compatible, so CloudForgeSynthesizer.synthesize() — the only call in Manager's
# dependency graph that reaches jsii — runs here instead, behind a plain HTTP boundary, in a
# same-task ECS sidecar container next to Manager's own native binary.
#
# Build from cfc-core's repo root (this module is a reactor member, not a standalone repo, so
# it needs the reactor's own dependencyManagement/BOM):
#   docker build -f cloudforge-synth-service/Dockerfile -t cloudforgeci/cloudforge-synth-service:latest .
#
# Run:
#   docker run --rm -p 8090:8090 cloudforgeci/cloudforge-synth-service:latest
#
# No AWS credentials, no IAM task-role grants, no Docker socket — this container only ever runs
# CDK synthesis against a DeploymentConfig payload and writes the resulting template/assembly to
# its own local disk; it never calls an AWS API or talks to anything beyond the loopback interface
# it shares with cloudforge-manager's container in the same ECS task.

# ── Maven package (reactor build — needs cloudforge-api from the same checkout) ─────────────────
# temurin-25, matching the reactor's own JDK 25 target (every pom compiles at release 25 now) —
# this was still pinned to temurin-21 from before that migration, which fails the reactor build
# outright ("invalid target release: 25") rather than producing an old-but-working artifact.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml ./
COPY cloudforge-core/pom.xml cloudforge-core/pom.xml
COPY cloudforge-api/pom.xml cloudforge-api/pom.xml
COPY cloudforge-localstack/pom.xml cloudforge-localstack/pom.xml
COPY cloudforge-ministack/pom.xml cloudforge-ministack/pom.xml
COPY cloudforge-synth-service/pom.xml cloudforge-synth-service/pom.xml
COPY cfc-testing/pom.xml cfc-testing/pom.xml
COPY cloudforge-core/src cloudforge-core/src
COPY cloudforge-api/src cloudforge-api/src
COPY cloudforge-localstack/src cloudforge-localstack/src
COPY cloudforge-ministack/src cloudforge-ministack/src
COPY cloudforge-synth-service/src cloudforge-synth-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl cloudforge-synth-service -am package -DskipTests -Djacoco.skip=true

# ── Runtime ──────────────────────────────────────────────────────────────────────────────────
# Plain eclipse-temurin JRE, not a jlink-trimmed custom runtime like cloudforge-manager's own
# image: this sidecar carries none of the crypto-provider/charset reflection gaps that motivated
# jlink there (no LicenseSeat, no Ed25519 verification, no Jackson record serialization at all) —
# a full JRE is the simpler, lower-risk choice until this image's own footprint is worth trimming.
FROM eclipse-temurin:25-jre AS runtime

# Node.js is required at runtime, not just at build time: CloudForgeSynthesizer.synthesize runs
# aws-cdk-lib in-process via its jsii bridge, which spawns a `node` child process to execute the
# library's bundled JS — there is no pure-Java CDK synthesis path. Same static-binary approach
# as cloudforge-manager's own (now-removed) plain Dockerfile used: pull the official prebuilt
# tarball, checksum-verified against Node's own published SHASUMS256.txt, rather than NodeSource's
# apt repo dragging in gnupg/python3 for a ~140MB none of this image otherwise uses.
ARG NODE_VERSION=24.19.0
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates xz-utils \
 && rm -rf /var/lib/apt/lists/* \
 && NODE_ARCH="$(dpkg --print-architecture | sed 's/amd64/x64/;s/arm64/arm64/')" \
 && NODE_DIST="node-v${NODE_VERSION}-linux-${NODE_ARCH}" \
 && curl -fsSL -o "/tmp/${NODE_DIST}.tar.xz" "https://nodejs.org/dist/v${NODE_VERSION}/${NODE_DIST}.tar.xz" \
 && curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt" \
    | grep " ${NODE_DIST}.tar.xz\$" \
    | (cd /tmp && sha256sum -c -) \
 && tar -xJf "/tmp/${NODE_DIST}.tar.xz" -C /usr/local --strip-components=1 --no-same-owner "${NODE_DIST}/bin/node" \
 && rm -f "/tmp/${NODE_DIST}.tar.xz" \
 && apt-get purge -y --auto-remove xz-utils
# curl deliberately kept (not purged like xz-utils) — the HEALTHCHECK below needs it.

WORKDIR /app
COPY --from=build /src/cloudforge-synth-service/target/cloudforge-synth-service-*.jar /app/synth-service.jar
COPY --from=build /src/cloudforge-synth-service/target/dependency /app/dependency

# Fixed non-root UID, matching cloudforge-manager's own runtime-aws convention (no functional
# requirement to match — this container shares no volume/EFS with Manager's — just consistency).
# Node's os.homedir() (jsii's DiskCache base) needs a writable HOME or it falls back to "/",
# which this UID can't write to — same gap cloudforge-manager's old Dockerfile hit and fixed.
RUN mkdir -p /home/synth && chown -R 65532:65532 /home/synth /app
ENV HOME=/home/synth
USER 65532:65532

ENV SYNTH_SERVICE_PORT=8090
EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
    CMD curl -fsS http://localhost:8090/v1/healthz || exit 1

ENTRYPOINT ["java", "-cp", "/app/synth-service.jar:/app/dependency/*", "com.cloudforgeci.synthservice.SynthServiceMain"]
