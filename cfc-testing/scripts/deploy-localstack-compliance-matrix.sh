#!/usr/bin/env bash
# Deploys the compliance matrix (framework x security profile, plus one EC2 runtime sample) to
# real LocalStack, restarting the LocalStack container fully between each config instead of
# tearing down individual stacks. LocalStack has no persistence, so a restart is a guaranteed
# clean slate -- surgical per-resource teardown (RDS deletion protection, EFS access points,
# security group dependency ordering, etc.) has too many AWS resource-cleanup edge cases for a
# full reset to be worth avoiding.
#
# Requires LOCALSTACK_AUTH_TOKEN. Two ways to run:
#   Local dev:  a StartLocalStack.class driver (wrapping LocalStackEmulatorRuntime.start()/stop())
#               on LOCALSTACK_STARTER_CP controls a container named cfc-localstack.
#   CI:         set LOCALSTACK_CONTAINER_ID to the id of an already-running GitHub Actions
#               `services: localstack:` container (${{ job.services.localstack.id }}) -- restarts
#               that container directly instead, since the services: block owns its lifecycle.
# See .github/workflows/localstack-compliance-verification.yml for the CI wiring.
#
# If you need to clean up a specific retained resource without a full reset instead, see
# docs/compliance/RETAINED_RESOURCES.md.
#
# Usage: ./deploy-localstack-compliance-matrix.sh [output-tsv-path]

set -uo pipefail
cd "$(dirname "$0")/.."
export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
unset CFC_DEPLOYING

CP="target/classes:target/dependency/*"
DEPLOYER="com.cloudforgeci.samples.app.InteractiveDeployer"
STARTER_CP="${LOCALSTACK_STARTER_CP:-$CP}"
RESULTS="${1:-scripts/validation-results/localstack-compliance-matrix-results.tsv}"
mkdir -p "$(dirname "$RESULTS")"
echo -e "config\tframework\tprofile\truntime\tresult\tstack_status\tconfig_rules\tguardduty\tcloudtrail\twaf" > "$RESULTS"

wait_for_health() {
  local attempt
  for attempt in $(seq 1 30); do
    curl -sf http://localhost:4566/_localstack/health 2>/dev/null | grep -q '"cloudformation": "available"' && return 0
    sleep 3
  done
  return 1
}

reset_localstack() {
  echo "---- restarting LocalStack for a clean slate ----"
  if [ -n "${LOCALSTACK_CONTAINER_ID:-}" ]; then
    # CI: the services: block owns start/stop, we just bounce the same container.
    docker restart "$LOCALSTACK_CONTAINER_ID" >/dev/null
    wait_for_health || echo "⚠️  LocalStack didn't report healthy within 90s after restart"
    docker exec "$LOCALSTACK_CONTAINER_ID" apt-get install -y libpython3.14 >/dev/null 2>&1 || true
  else
    java -cp "$STARTER_CP" StartLocalStack stop >/dev/null 2>&1 || true
    docker ps -aq --filter "name=^ls-" | xargs -r docker rm -f > /dev/null 2>&1 || true
    java -cp "$STARTER_CP" StartLocalStack start
    wait_for_health || echo "⚠️  LocalStack didn't report healthy within 90s after restart"
    # Postgres/MySQL RDS emulation needs this on arm64 hosts (missing from the base image) --
    # see LocalStackRdsSupportTest / the session notes on the libpython3.14 gap.
    docker exec cfc-localstack apt-get install -y libpython3.14 >/dev/null 2>&1 || true
  fi
}

deploy() {
  local ctx_file="$1" framework="$2" profile="$3" runtime="$4"
  local ctx_name="${ctx_file%.json}"
  ctx_name="${ctx_name%-LocalStack}"
  local stack="${ctx_name}-localstack"
  local config="${framework}/${profile}/${runtime}"

  reset_localstack

  echo "========== Deploying $config =========="
  java -cp "$CP" "$DEPLOYER" --context "deployment-contexts/compliance/$ctx_file" 8 > /tmp/deploy-$$.log 2>&1
  tail -15 /tmp/deploy-$$.log

  local status
  status=$(curl -s "http://localhost:4566/?Action=DescribeStacks&StackName=${stack}&Version=2010-05-15" \
    | grep -o "<StackStatus>[^<]*</StackStatus>" | sed -E 's#</?StackStatus>##g')

  local result="FAIL"
  [ "$status" = "CREATE_COMPLETE" ] || [ "$status" = "UPDATE_COMPLETE" ] && result="PASS"

  # Resource-level detail (exact Config rules created, what LocalStackTemplateAdapter stripped,
  # etc.) comes from the cdk.out/<ctx_name>.template.json / .localstack.template.json /
  # .localstack-adaptations.json files CDK synth already writes for every deploy -- no need to
  # query live stack state for that; see localstack-compliance-comparison.py. This just confirms
  # pass/fail and a few headline resources for the summary table.
  local resources
  resources=$(curl -s "http://localhost:4566/?Action=DescribeStackResources&StackName=${stack}&Version=2010-05-15")
  local config_rules guardduty cloudtrail waf
  config_rules=$(echo "$resources" | grep -c "AWS::Config::ConfigRule" || true)
  guardduty=$(echo "$resources" | grep -qc "AWS::GuardDuty::Detector" && echo yes || echo no)
  cloudtrail=$(echo "$resources" | grep -qc "AWS::CloudTrail::Trail" && echo yes || echo no)
  waf=$(echo "$resources" | grep -qc "AWS::WAFv2::WebACL" && echo yes || echo no)

  echo -e "${ctx_name}\t${framework}\t${profile}\t${runtime}\t${result}\t${status:-none}\t${config_rules}\t${guardduty}\t${cloudtrail}\t${waf}" >> "$RESULTS"
  rm -f /tmp/deploy-$$.log
}

for fw in soc2 pcidss hipaa gdpr; do
  fw_label=$(echo "$fw" | sed 's/pcidss/PCI-DSS/;s/soc2/SOC2/;s/hipaa/HIPAA/;s/gdpr/GDPR/')
  for profile in dev staging production; do
    deploy "CFCompliance-${fw}-${profile}-fargate-LocalStack.json" "$fw_label" "$(echo $profile | tr a-z A-Z)" FARGATE
  done
done
deploy "CFCompliance-allframeworks-production-ec2-LocalStack.json" "SOC2+PCI-DSS+HIPAA+GDPR" PRODUCTION EC2

echo "===== COMPLIANCE MATRIX DONE ====="
column -t -s$'\t' "$RESULTS"
