#!/usr/bin/env bash
# Deploy LocalStack-compatible apps with unique host ports (no 3000 collisions).
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
  echo "========== Deploying $stack to LocalStack =========="
  if curl -s "http://localhost:4566/?Action=DescribeStacks&StackName=${stack}-localstack&Version=2010-05-15" \
      | grep -q "<StackStatus>CREATE_COMPLETE</StackStatus>"; then
    echo "SKIP: $stack already CREATE_COMPLETE"
    return 0
  fi
  java -cp "$CP" "$DEPLOYER" --context "$ctx" 8 2>&1 | tail -12
}

deploy deployment-contexts/CloudForgeManager-Dev.json CloudForgeManager-Dev
deploy deployment-contexts/Jenkins-Stack.json Jenkins-Stack
# Metabase is the flagship analytics deployment. It shares port 3000 with
# Grafana and Gitea, so deploy it as the sole service from that port group.
deploy deployment-contexts/Metabase-Stack.json Metabase-Stack
deploy deployment-contexts/Mattermost-Stack-LocalStack.json Mattermost-Stack
deploy deployment-contexts/Prometheus-Stack.json Prometheus-Stack
deploy deployment-contexts/Vault-Stack.json Vault-Stack
deploy deployment-contexts/Redis-Stack.json Redis-Stack
deploy deployment-contexts/Nexus-Stack.json Nexus-Stack
deploy deployment-contexts/SonarQube-Stack.json SonarQube-Stack
deploy deployment-contexts/PostgreSQL-Stack.json PostgreSQL-Stack
deploy deployment-contexts/Drone-Stack.json Drone-Stack

echo "Done. Skipped Grafana and Gitea (port 3000 conflict with Metabase)."
