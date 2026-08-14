#!/usr/bin/env bash
# Thin wrapper — edge lifecycle is owned by EmulatorEdgeLifecycle (Maven / Java).
# Docs: docs/guides/LOCAL_EMULATOR_EDGE.md
#
# `cloudforge:emulator-edge-<goal>` (the old invocation here) never resolved — no Maven plugin
# ever registered a "cloudforge" prefix, so it 404'd with "No plugin found for prefix 'cloudforge'"
# every time. EmulatorEdgeCli is the real entry point; invoked via exec-maven-plugin's fully
# qualified coordinates so there's no prefix to resolve in the first place.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GOAL="${1:?usage: $0 <start|stop|restart|rebuild|status|reconcile|reload>}"
cd "$ROOT"
exec mvn -pl cloudforge-core -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.cloudforge.core.local.EmulatorEdgeCli \
  -Dexec.args="${GOAL}" \
  -Dexec.classpathScope=runtime
