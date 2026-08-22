#!/usr/bin/env bash
# Deploys a batch of already-generated canonical CFN templates (cfn-templates/<config_name>.json,
# produced by TruthTableValidationTest / compliance-report-generator.py's own test run) straight
# to a real LocalStack instance via LocalStackCli, one at a time with a restart between each --
# most of these templates carry a GuardDuty::Detector and/or Config::ConfigRule, which are
# account-level-singleton-shaped resources in real AWS/LocalStack, so a clean slate per config is
# needed the same way deploy-localstack-compliance-matrix.sh needs it for the 13 representative
# configs (see that script; this one reuses the same CI/local restart split).
#
# Unlike deploy-localstack-compliance-matrix.sh, this operates directly on a canonical template
# file rather than a deployment-context JSON + full InteractiveDeployer/CDK-synth round trip --
# LocalStackCli's `deploy` command takes the template straight, which is what lets this run
# against templates that don't have (and don't need) their own deployment-context file.
#
# Requires LOCALSTACK_AUTH_TOKEN, plus LOCALSTACK_CONTAINER_ID in CI (see
# deploy-localstack-compliance-matrix.sh's reset_localstack for the same local-vs-CI split).
#
# Usage: ./deploy-localstack-template-batch.sh <templates-dir> <output-tsv> <batch-index> <batch-count>
#   Deploys every Nth template (round-robin by sorted filename) where N == batch-count and this
#   is the batch-index'th one -- lets several parallel CI jobs split one template directory
#   between them without needing to pre-compute file lists.

set -uo pipefail
cd "$(dirname "$0")/.."

TEMPLATES_DIR="${1:?templates dir required}"
RESULTS="${2:?output tsv path required}"
BATCH_INDEX="${3:-0}"
BATCH_COUNT="${4:-1}"

export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
unset CFC_DEPLOYING

CP="../cloudforge-localstack/target/classes:../cloudforge-core/target/classes:target/classes:target/dependency/*"
CLI="com.cloudforgeci.localstack.LocalStackCli"
STAGING_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGING_DIR"' EXIT

mkdir -p "$(dirname "$RESULTS")"
echo -e "config_name\tresult\tadaptation_count" > "$RESULTS"

wait_for_health() {
  # local + a name distinct from the batch-partitioning counter below: bash for-loops don't
  # scope by default, so a bare `for i` here would silently clobber that outer counter on every
  # call and corrupt the mod-N batch split.
  local attempt
  for attempt in $(seq 1 30); do
    curl -sf http://localhost:4566/_localstack/health 2>/dev/null | grep -q '"cloudformation": "available"' && return 0
    sleep 3
  done
  return 1
}

reset_localstack() {
  if [ -n "${LOCALSTACK_CONTAINER_ID:-}" ]; then
    docker restart "$LOCALSTACK_CONTAINER_ID" >/dev/null
    wait_for_health || echo "⚠️  LocalStack didn't report healthy within 90s after restart"
    docker exec "$LOCALSTACK_CONTAINER_ID" apt-get install -y libpython3.14 >/dev/null 2>&1 || true
  else
    java -cp "$CP" StartLocalStack stop >/dev/null 2>&1 || true
    docker ps -aq --filter "name=^ls-" | xargs -r docker rm -f > /dev/null 2>&1 || true
    java -cp "$CP" StartLocalStack start
    wait_for_health || echo "⚠️  LocalStack didn't report healthy within 90s after restart"
    docker exec cfc-localstack apt-get install -y libpython3.14 >/dev/null 2>&1 || true
  fi
}

# LocalStackCli's `deploy` derives the CloudFormation StackName it creates from the template
# filename (stripping a literal ".template.json" suffix). The raw config_name (e.g.
# "ADVISORY_EC2_GDPR_eu-region-approved", upper/underscore) isn't safe to use directly:
# LocalStack's auto-generated S3 bucket physical name derives from the stack name and isn't
# case/underscore-safe the way AWS's own auto-naming is, so a stack with no explicit BucketName
# fails with InvalidBucketName. Deploys use a lowercase/hyphenated stack name instead; the TSV
# still keys on the original config_name so it matches compliance-report-generator.py's rows.
idx=0
find "$TEMPLATES_DIR" -maxdepth 1 -name '*.json' | sort | while read -r template; do
  config_name="$(basename "$template" .json)"
  if [ $(( idx % BATCH_COUNT )) -ne "$BATCH_INDEX" ]; then
    idx=$((idx + 1))
    continue
  fi
  idx=$((idx + 1))

  reset_localstack

  safe_name="$(echo "$config_name" | tr '[:upper:]_' '[:lower:]-')"
  staged="$STAGING_DIR/${safe_name}.template.json"
  cp "$template" "$staged"

  echo "========== Deploying ${config_name} (as ${safe_name}) =========="
  output=$(java -cp "$CP" "$CLI" deploy "$safe_name" "$staged" 2>&1) && result="PASS" || result="FAIL"
  echo "$output" | tail -15

  adaptation_count=$(echo "$output" | grep -oE "adaptations=[0-9]+" | grep -oE "[0-9]+" || echo 0)
  echo -e "${config_name}\t${result}\t${adaptation_count}" >> "$RESULTS"
done

echo "===== TEMPLATE BATCH ${BATCH_INDEX}/${BATCH_COUNT} DONE ====="
column -t -s$'\t' "$RESULTS"
