#!/bin/sh
set -e

# The image's default USER is root (see Dockerfile) specifically so this entrypoint can run before
# the app process drops to the unprivileged runtime UID — the `java` process itself never runs as
# root. The one thing that needs root is `setpriv`'s ability to grant an arbitrary supplementary
# GID: when /var/run/docker.sock is bind-mounted in (LocalStackTemplateAdapter.grantDockerSocketAccess
# for the ECS/LocalStack path, or docker-compose.yml's cloudforge-manager service for the standalone
# path), its host-side group ownership denies UID 1000 access by default — confirmed live: Docker
# Desktop for Mac mounts it group-owned by GID 0, and a non-root process cannot add GID 0 as a
# supplementary group for itself (setgroups() requires privilege). Detecting the socket's actual GID
# here and handing it to setpriv, instead of hardcoding a guessed "docker" group GID, is what makes
# this portable across hosts where the socket's ownership differs (plain Linux Docker installs
# typically use a real, non-zero "docker" group GID rather than Desktop-for-Mac's GID 0).
RUN_UID=1000
RUN_GID=1000

if [ -S /var/run/docker.sock ]; then
    SOCK_GID=$(stat -c '%g' /var/run/docker.sock)
    exec setpriv --reuid="$RUN_UID" --regid="$RUN_GID" --groups="$SOCK_GID" "$@"
fi

exec setpriv --reuid="$RUN_UID" --regid="$RUN_GID" --clear-groups "$@"
