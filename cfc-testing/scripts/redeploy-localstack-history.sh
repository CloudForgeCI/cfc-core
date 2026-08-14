#!/usr/bin/env bash
# Redeploy LocalStack apps (option 8) so Manager reads their canonical
# CloudFormation inventory and stack events after deployment.
# Uses LOCALSTACK_PREFLIGHT=warn so in-place updates are not blocked by host-port checks.
set -euo pipefail
cd "$(dirname "$0")/.."

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://127.0.0.1:4566}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
export LOCALSTACK_PREFLIGHT=warn
unset CFC_DEPLOYING

CP="target/classes:target/dependency/*"
DEPLOYER="com.cloudforgeci.samples.app.InteractiveDeployer"

redeploy() {
  local ctx="$1"
  local stack="$2"
  echo ""
  echo "========== Redeploying $stack =========="
  if ! java -cp "$CP" "$DEPLOYER" --context "$ctx" 8; then
    echo "FAILED: $stack" >&2
    return 1
  fi
}

# Application stacks first; Manager last (restarts the panel mid-batch if done earlier).
redeploy deployment-contexts/Jenkins-Stack.json Jenkins-Stack
redeploy deployment-contexts/Grafana-Stack.json Grafana-Stack
redeploy deployment-contexts/Prometheus-Stack.json Prometheus-Stack
redeploy deployment-contexts/Vault-Stack.json Vault-Stack
redeploy deployment-contexts/Redis-Stack.json Redis-Stack
redeploy deployment-contexts/Nexus-Stack.json Nexus-Stack
redeploy deployment-contexts/SonarQube-Stack.json SonarQube-Stack
redeploy deployment-contexts/PostgreSQL-Stack.json PostgreSQL-Stack
redeploy deployment-contexts/Drone-Stack.json Drone-Stack
redeploy deployment-contexts/CloudForgeManager-Dev.json CloudForgeManager-Dev

echo ""
echo "Done. Verify CloudFormation history, resources, and stack events in CloudForge Manager."
