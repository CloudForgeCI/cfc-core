#!/usr/bin/env bash
# Deploy all MiniStack-compatible apps (no RDS). Skips already-deployed stacks.
set -euo pipefail
cd "$(dirname "$0")/.."
export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
unset CFC_DEPLOYING

CP="target/classes:target/dependency/*"
DEPLOYER="com.cloudforgeci.samples.app.InteractiveDeployer"

deploy() {
  local ctx="$1"
  local stack="$2"
  echo "========== Deploying $stack =========="
  if curl -s "http://localhost:4566/?Action=DescribeStacks&StackName=${stack}-ministack&Version=2010-05-15" \
      | grep -q "<StackStatus>CREATE_COMPLETE</StackStatus>"; then
    echo "SKIP: $stack already CREATE_COMPLETE"
    return 0
  fi
  java -cp "$CP" "$DEPLOYER" --context "$ctx" 6 2>&1 | tail -8
}

deploy deployment-contexts/Prometheus-Stack.json Prometheus-Stack
deploy deployment-contexts/Drone-Stack.json Drone-Stack
deploy deployment-contexts/Vault-Stack.json Vault-Stack
deploy deployment-contexts/Redis-Stack.json Redis-Stack
deploy deployment-contexts/Nexus-Stack.json Nexus-Stack
deploy deployment-contexts/SonarQube-Stack.json SonarQube-Stack
deploy deployment-contexts/PostgreSQL-Stack.json PostgreSQL-Stack
deploy deployment-contexts/Metabase-Stack.json Metabase-Stack
deploy deployment-contexts/Gitea-Stack.json Gitea-Stack

echo "Done."
