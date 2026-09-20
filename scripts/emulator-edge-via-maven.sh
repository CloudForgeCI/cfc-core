#!/usr/bin/env bash
# Thin wrapper — edge lifecycle is owned by EmulatorEdgeLifecycle (Maven / Java).
# Docs: docs/guides/LOCAL_EMULATOR_EDGE.md
#
# Runs EmulatorEdgeCli through exec-maven-plugin's fully qualified coordinates. There is no
# `cloudforge:` Maven plugin prefix, so `mvn cloudforge:emulator-edge-<goal>` does not work.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GOAL="${1:?usage: $0 <start|stop|restart|rebuild|status|reconcile|reload>}"
cd "$ROOT"
exec mvn -pl cloudforge-core -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.cloudforge.core.local.EmulatorEdgeCli \
  -Dexec.args="${GOAL}" \
  -Dexec.classpathScope=runtime
