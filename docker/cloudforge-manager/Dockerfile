# syntax=docker/dockerfile:1
# CloudForge Manager — Operations Panel image for Fargate / MiniStack / EC2 Docker.
#
# Build from repository root:
#   docker build -t cloudforgeci/cloudforge-manager:latest -f docker/cloudforge-manager/Dockerfile .
# Or via compose:
#   docker compose build cloudforge-manager
#
# Run:
#   docker run --rm -p 1958:1958 \
#     -e LOCALSTACK_ENDPOINT=http://host.docker.internal:4566 \
#     -e CFC_MANAGER_TARGET=localstack \
#     cloudforgeci/cloudforge-manager:latest
#
# Faster local reinstall: `--mount=type=cache` below persists ~/.npm and ~/.m2 across separate
# `docker build`/`docker compose build` invocations (not just within one build's layer cache),
# so redeploying after an unrelated source change doesn't re-download every npm package or Maven
# dependency from scratch. Only the download cache is shared — actual compilation of this
# image's own source still happens fresh every time from the COPY'd tree below, so a real source
# change is never silently skipped. Requires BuildKit (default since Docker 23+; the `# syntax`
# line above pins the frontend that understands `--mount=type=cache` on older CLIs too).

# ── Angular UI ─────────────────────────────────────────────────────────────────
FROM node:20-bookworm AS ui
WORKDIR /work/ui
COPY cloudforge-manager/ui/package.json cloudforge-manager/ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY cloudforge-manager/ui/ ./
RUN npm run build:panel
# copy-to-public.mjs writes ../src/main/resources/public → /work/src/main/resources/public

# ── Maven package ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY cloudforge-core ./cloudforge-core
COPY cloudforge-api ./cloudforge-api
COPY cloudforge-ministack ./cloudforge-ministack
COPY cloudforge-localstack ./cloudforge-localstack
COPY cloudforge-manager-deployment ./cloudforge-manager-deployment
COPY cloudforge-manager ./cloudforge-manager
COPY --from=ui /work/src/main/resources/public ./cloudforge-manager/src/main/resources/public
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl cloudforge-manager -am package -DskipTests -Djacoco.skip=true -q

# ── Runtime ────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# Node.js is required at runtime, not just at build time: `deploy:create`'s CDK synthesis
# (CloudForgeSynthesizer -> ManagerSynthesisService, wired in this session) runs aws-cdk-lib
# in-process via its jsii bridge, which spawns a real `node` child process to execute the
# library's bundled JS — there is no pure-Java CDK synthesis path. Every test that exercised this
# passed because it ran on a dev machine with Node already on PATH; the deployed container never
# had it, so the first real "Deploy to AWS" click failed with "Cannot run program \"node\": Exec
# failed, error: 2 (No such file or directory)". Pin the same major version as the UI build
# stage's `node:20-bookworm` for consistency, installed via NodeSource (Ubuntu jammy has no
# current-enough `nodejs` package in its own repos).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*

# Docker CLI (client only, no daemon) — needed for deploy:create/deploy:catalog against the
# localstack/ministack targets, whose target-owned emulator helpers (e.g. LocalStackPostgresCompanion,
# the companion Postgres container LocalStack-hosted RDS-backed apps connect to) shell out to
# `docker` directly. Meaningless without also mounting the host's Docker socket into this
# container — see LocalStackTemplateAdapter.grantDockerSocketAccess, which does that only for
# Manager's own container and only when Manager deploys itself to localstack/ministack; the `aws`
# target's task definition is never touched, and this binary is simply unused there (real Fargate
# has no host Docker daemon to reach regardless).
RUN curl -fsSL https://download.docker.com/linux/static/stable/$(dpkg --print-architecture | sed 's/amd64/x86_64/;s/arm64/aarch64/')/docker-25.0.5.tgz \
    -o /tmp/docker.tgz \
 && tar -xzf /tmp/docker.tgz -C /tmp \
 && mv /tmp/docker/docker /usr/local/bin/docker \
 && rm -rf /tmp/docker.tgz /tmp/docker

WORKDIR /app

COPY --from=build /src/cloudforge-manager/target/cloudforge-manager-*.jar /app/manager.jar

# UID 1000 has no /etc/passwd entry in this image, so Node's os.homedir() (jsii's cache-directory
# base — see DiskCache.inDirectory in the bundled jsii runtime) can't resolve a real home and
# falls back to "/", which UID 1000 can't write to: the first real deploy after adding Node above
# failed with "EACCES: permission denied, mkdir '/.cache/aws/jsii/package-cache'". Give it an
# actual writable HOME instead.
RUN mkdir -p /home/manager && chown -R 1000:1000 /home/manager
ENV HOME=/home/manager

ENV CFC_MANAGER_PORT=1958 \
    CFC_MANAGER_BIND=0.0.0.0 \
    SERVER_PORT=1958 \
    SERVER_ADDRESS=0.0.0.0

EXPOSE 1958

# No USER directive here (deliberately — see entrypoint.sh): the container starts as root only
# long enough for the entrypoint to drop to UID 1000, adding the mounted Docker socket's actual
# host-side GID as a supplementary group first when that socket is present. setgroups() to an
# arbitrary GID requires privilege a non-root process doesn't have, so this can't be done after
# the fact — confirmed live: `docker run --user 1000:1000` denies socket access even with the
# right GID passed to setpriv, while running the same setpriv call as root succeeds. The `java`
# process itself still ends up running as unprivileged UID 1000 either way.
COPY docker/cloudforge-manager/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh", "java", "-jar", "/app/manager.jar"]
