#!/usr/bin/env bash

# Build Snapshot Capture System
# Captures comprehensive state of each build for drift detection and progression tracking
# Tracks: template synthesis, test results, configuration evolution, resource changes

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="${BASE_DIR:-$(dirname "$SCRIPT_DIR")}"
CDK_OUT_DIR="$BASE_DIR/cdk.out"
VALIDATION_DIR="$SCRIPT_DIR/validation-results"
SNAPSHOT_DIR="$VALIDATION_DIR/snapshots"
CURRENT_DIR="$VALIDATION_DIR/current"

# Create directories
mkdir -p "$SNAPSHOT_DIR"

echo -e "${BLUE}📸 Build Snapshot Capture System${NC}"
echo -e "${BLUE}===============================${NC}"
echo ""

# Get timestamp and git info
TIMESTAMP=$(date -Iseconds)
BUILD_DATE=$(date +"%Y-%m-%d")
BUILD_TIME=$(date +"%H:%M:%S")
GIT_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo 'unknown')
GIT_SHORT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')
GIT_BRANCH=$(git branch --show-current 2>/dev/null || echo 'unknown')
VERSION=$(grep -o '<version>[^<]*' "$BASE_DIR/pom.xml" | sed 's/<version>//' | head -1 || echo 'unknown')

SNAPSHOT_FILE="$SNAPSHOT_DIR/snapshot-$(date +"%Y%m%d-%H%M%S")-${GIT_SHORT_COMMIT}.json"

echo -e "${CYAN}📋 Capturing build snapshot...${NC}"
echo "Git Commit: $GIT_SHORT_COMMIT ($GIT_BRANCH)"
echo "Version: $VERSION"
echo "Timestamp: $TIMESTAMP"
echo ""

# Initialize snapshot JSON structure
cat > "$SNAPSHOT_FILE" << EOF
{
  "snapshot_metadata": {
    "timestamp": "$TIMESTAMP",
    "build_date": "$BUILD_DATE",
    "build_time": "$BUILD_TIME",
    "git_commit": "$GIT_COMMIT",
    "git_short_commit": "$GIT_SHORT_COMMIT",
    "git_branch": "$GIT_BRANCH",
    "version": "$VERSION"
  },
  "configuration": {
    "cdk_context": {},
    "compliance_frameworks": [],
    "security_profiles_used": [],
    "feature_flags": {}
  },
  "templates": {},
  "test_results": {},
  "resource_inventory": {
    "total_resources": 0,
    "by_type": {},
    "by_security_profile": {}
  },
  "test_matrix": {
    "total_tests": 0,
    "passed": 0,
    "failed": 0,
    "by_security_profile": {},
    "by_runtime": {},
    "by_topology": {},
    "by_network_mode": {},
    "by_framework": {}
  },
  "configuration_complexity": {
    "minimal_configs": 0,
    "with_domain": 0,
    "with_ssl": 0,
    "with_waf": 0,
    "with_compliance": 0
  }
}
EOF

echo -e "${PURPLE}🔍 Analyzing CDK context and configuration...${NC}"

# Extract CDK context
if [ -f "$BASE_DIR/cdk.json" ]; then
    CDK_CONTEXT=$(jq -r '.context // {}' "$BASE_DIR/cdk.json")

    # Extract compliance frameworks
    COMPLIANCE_FRAMEWORKS=$(echo "$CDK_CONTEXT" | jq -r '.complianceFrameworks // ""' | tr ',' '\n' | jq -R . | jq -s .)

    # Update snapshot with context
    temp_file=$(mktemp)
    jq --argjson ctx "$CDK_CONTEXT" \
       --argjson frameworks "$COMPLIANCE_FRAMEWORKS" \
       '.configuration.cdk_context = $ctx |
        .configuration.compliance_frameworks = $frameworks' \
       "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
fi

echo -e "${PURPLE}📦 Analyzing synthesized templates...${NC}"

# Capture template information from multiple sources
TEMPLATE_COUNT=0
CFN_TEMPLATES_DIR="$VALIDATION_DIR/cfn-templates"

# Function to process a template file
process_template() {
    local template_file="$1"
    if [ -f "$template_file" ]; then
        TEMPLATE_COUNT=$((TEMPLATE_COUNT + 1))
        template_name=$(basename "$template_file" .json)
        template_name=$(basename "$template_name" .template)

        # Extract resource counts and types
        resource_count=$(jq '[.Resources // {} | to_entries[]] | length' "$template_file" 2>/dev/null || echo 0)
        resource_types=$(jq -r '[.Resources // {} | to_entries[] | .value.Type] | group_by(.) | map({key: .[0], count: length}) | from_entries' "$template_file" 2>/dev/null || echo '{}')

        # Calculate template hash for change detection
        template_hash=$(shasum -a 256 "$template_file" | awk '{print $1}')

        # Add template info to snapshot
        temp_file=$(mktemp)
        jq --arg name "$template_name" \
           --argjson count "$resource_count" \
           --argjson types "$resource_types" \
           --arg hash "$template_hash" \
           '.templates[$name] = {
               "resource_count": $count,
               "resource_types": $types,
               "template_hash": $hash
           }' \
           "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"

        echo "  ✓ $template_name: $resource_count resources"
    fi
}

# Check cdk.out directory (standard CDK synthesis output)
if [ -d "$CDK_OUT_DIR" ]; then
    for template_file in "$CDK_OUT_DIR"/*.template.json; do
        process_template "$template_file"
    done
fi

# Check validation-results/cfn-templates (compliance test output)
if [ -d "$CFN_TEMPLATES_DIR" ]; then
    for template_file in "$CFN_TEMPLATES_DIR"/*.json; do
        process_template "$template_file"
    done
fi

echo "  Total templates: $TEMPLATE_COUNT"

echo -e "${PURPLE}🧪 Analyzing test results...${NC}"

# Capture test results from multiple sources
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

declare -A PROFILE_STATS
declare -A RUNTIME_STATS
declare -A TOPOLOGY_STATS
declare -A COMPLEXITY_STATS
declare -A NETWORK_STATS
declare -A FRAMEWORK_STATS

JSONL_FILE="$VALIDATION_DIR/compliance-results-incremental.jsonl"

# Try JSONL file first (from compliance tests)
if [ -f "$JSONL_FILE" ] && [ -s "$JSONL_FILE" ]; then
    echo "  Reading from compliance-results-incremental.jsonl..."

    while IFS= read -r line; do
        [ -z "$line" ] && continue

        TOTAL_TESTS=$((TOTAL_TESTS + 1))

        # Extract fields from JSON line
        config_name=$(echo "$line" | jq -r '.config_name // "unknown"')
        test_status=$(echo "$line" | jq -r '.status // "unknown"')
        runtime=$(echo "$line" | jq -r '.runtime // "unknown"')
        framework=$(echo "$line" | jq -r '.framework // "unknown"')
        network_mode=$(echo "$line" | jq -r '.network_mode // "unknown"')
        is_negative=$(echo "$line" | jq -r '.is_negative_test // false')

        # Determine security profile from config name
        if [[ "$config_name" == *"PRODUCTION"* ]]; then
            security_profile="PRODUCTION"
        elif [[ "$config_name" == *"STAGING"* ]]; then
            security_profile="STAGING"
        else
            security_profile="DEV"
        fi

        # Track test status
        if [[ "$test_status" == "passed" ]]; then
            PASSED_TESTS=$((PASSED_TESTS + 1))
            PROFILE_STATS["${security_profile}_passed"]=$((${PROFILE_STATS["${security_profile}_passed"]:-0} + 1))
            RUNTIME_STATS["${runtime}_passed"]=$((${RUNTIME_STATS["${runtime}_passed"]:-0} + 1))
        else
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi

        # Track totals
        PROFILE_STATS["${security_profile}_total"]=$((${PROFILE_STATS["${security_profile}_total"]:-0} + 1))
        RUNTIME_STATS["${runtime}_total"]=$((${RUNTIME_STATS["${runtime}_total"]:-0} + 1))

        # Track complexity based on config name patterns
        # alb-oidc requires domain + SSL, so it indicates both
        if [[ "$config_name" == *"alb-oidc"* ]]; then
            COMPLEXITY_STATS["with_domain"]=$((${COMPLEXITY_STATS["with_domain"]:-0} + 1))
            COMPLEXITY_STATS["with_ssl"]=$((${COMPLEXITY_STATS["with_ssl"]:-0} + 1))
        else
            COMPLEXITY_STATS["minimal"]=$((${COMPLEXITY_STATS["minimal"]:-0} + 1))
        fi

        # WAF is enabled for PRODUCTION environments
        if [[ "$security_profile" == "PRODUCTION" ]]; then
            COMPLEXITY_STATS["with_waf"]=$((${COMPLEXITY_STATS["with_waf"]:-0} + 1))
        fi

        # Track by network mode
        NETWORK_STATS["${network_mode}_total"]=$((${NETWORK_STATS["${network_mode}_total"]:-0} + 1))
        if [[ "$test_status" == "passed" ]]; then
            NETWORK_STATS["${network_mode}_passed"]=$((${NETWORK_STATS["${network_mode}_passed"]:-0} + 1))
        fi

        # Track by framework (split comma-separated frameworks)
        IFS=',' read -ra FRAMEWORKS <<< "$framework"
        for fw in "${FRAMEWORKS[@]}"; do
            fw=$(echo "$fw" | xargs)  # trim whitespace
            FRAMEWORK_STATS["${fw}_total"]=$((${FRAMEWORK_STATS["${fw}_total"]:-0} + 1))
            if [[ "$test_status" == "passed" ]]; then
                FRAMEWORK_STATS["${fw}_passed"]=$((${FRAMEWORK_STATS["${fw}_passed"]:-0} + 1))
            fi
        done

        # Add test result to snapshot
        temp_file=$(mktemp)
        jq --arg name "$config_name" \
           --arg status "$test_status" \
           --arg runtime "$runtime" \
           --arg profile "$security_profile" \
           --arg framework "$framework" \
           --arg network "$network_mode" \
           --argjson negative "$is_negative" \
           '.test_results[$name] = {
               "status": $status,
               "runtime": $runtime,
               "security_profile": $profile,
               "framework": $framework,
               "network_mode": $network,
               "is_negative_test": $negative
           }' \
           "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"

    done < "$JSONL_FILE"

# Fallback to current directory (legacy format)
elif [ -d "$CURRENT_DIR" ]; then
    echo "  Reading from validation-results/current/..."
    for result_file in "$CURRENT_DIR"/*-validation.json; do
        if [ -f "$result_file" ]; then
            TOTAL_TESTS=$((TOTAL_TESTS + 1))

            test_name=$(basename "$result_file" -validation.json)
            test_status=$(jq -r '.summary.status // "UNKNOWN"' "$result_file")
            resource_count=$(jq -r '.summary.resource_count // 0' "$result_file")
            missing_resources=$(jq -r '.summary.missing_resources // ""' "$result_file")

            # Parse test name components
            IFS='_' read -ra COMPONENTS <<< "$test_name"
            runtime="${COMPONENTS[0]:-unknown}"
            topology="${COMPONENTS[1]:-unknown}"
            security_profile="${COMPONENTS[2]:-unknown}"
            domain_config="${COMPONENTS[3]:-unknown}"
            ssl_config="${COMPONENTS[4]:-unknown}"
            subdomain_config="${COMPONENTS[5]:-unknown}"

            # Track test status
            if [[ "$test_status" == "VALIDATION_PASSED" ]]; then
                PASSED_TESTS=$((PASSED_TESTS + 1))
                PROFILE_STATS["${security_profile}_passed"]=$((${PROFILE_STATS["${security_profile}_passed"]:-0} + 1))
                RUNTIME_STATS["${runtime}_passed"]=$((${RUNTIME_STATS["${runtime}_passed"]:-0} + 1))
                TOPOLOGY_STATS["${topology}_passed"]=$((${TOPOLOGY_STATS["${topology}_passed"]:-0} + 1))
            else
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi

            PROFILE_STATS["${security_profile}_total"]=$((${PROFILE_STATS["${security_profile}_total"]:-0} + 1))
            RUNTIME_STATS["${runtime}_total"]=$((${RUNTIME_STATS["${runtime}_total"]:-0} + 1))
            TOPOLOGY_STATS["${topology}_total"]=$((${TOPOLOGY_STATS["${topology}_total"]:-0} + 1))

            # Track configuration complexity
            if [[ "$domain_config" == "with-domain" ]]; then
                COMPLEXITY_STATS["with_domain"]=$((${COMPLEXITY_STATS["with_domain"]:-0} + 1))
            fi
            if [[ "$ssl_config" == "ssl-enabled" ]]; then
                COMPLEXITY_STATS["with_ssl"]=$((${COMPLEXITY_STATS["with_ssl"]:-0} + 1))
            fi
            if [[ "$security_profile" == "PRODUCTION" ]]; then
                COMPLEXITY_STATS["with_waf"]=$((${COMPLEXITY_STATS["with_waf"]:-0} + 1))
            fi
            if [[ "$domain_config" == "no-domain" && "$ssl_config" == "ssl-disabled" ]]; then
                COMPLEXITY_STATS["minimal"]=$((${COMPLEXITY_STATS["minimal"]:-0} + 1))
            fi

            # Add test result to snapshot
            temp_file=$(mktemp)
            jq --arg name "$test_name" \
               --arg status "$test_status" \
               --argjson count "$resource_count" \
               --arg missing "$missing_resources" \
               --arg runtime "$runtime" \
               --arg topology "$topology" \
               --arg profile "$security_profile" \
               --arg domain "$domain_config" \
               --arg ssl "$ssl_config" \
               --arg subdomain "$subdomain_config" \
               '.test_results[$name] = {
                   "status": $status,
                   "resource_count": $count,
                   "missing_resources": $missing,
                   "runtime": $runtime,
                   "topology": $topology,
                   "security_profile": $profile,
                   "domain_config": $domain,
                   "ssl_config": $ssl,
                   "subdomain_config": $subdomain
               }' \
               "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
        fi
    done
else
    echo "  No test results found"
fi

echo "  Total tests: $TOTAL_TESTS"
echo "  Passed: $PASSED_TESTS"
echo "  Failed: $FAILED_TESTS"

# Update test matrix statistics
temp_file=$(mktemp)
jq --argjson total "$TOTAL_TESTS" \
   --argjson passed "$PASSED_TESTS" \
   --argjson failed "$FAILED_TESTS" \
   '.test_matrix.total_tests = $total |
    .test_matrix.passed = $passed |
    .test_matrix.failed = $failed' \
   "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"

# Add profile stats
for profile in DEV STAGING PRODUCTION; do
    total=${PROFILE_STATS["${profile}_total"]:-0}
    passed=${PROFILE_STATS["${profile}_passed"]:-0}
    failed=$((total - passed))

    temp_file=$(mktemp)
    jq --arg profile "$profile" \
       --argjson total "$total" \
       --argjson passed "$passed" \
       --argjson failed "$failed" \
       '.test_matrix.by_security_profile[$profile] = {
           "total": $total,
           "passed": $passed,
           "failed": $failed
       }' \
       "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
done

# Add runtime stats
for runtime in EC2 FARGATE; do
    total=${RUNTIME_STATS["${runtime}_total"]:-0}
    passed=${RUNTIME_STATS["${runtime}_passed"]:-0}
    failed=$((total - passed))

    temp_file=$(mktemp)
    jq --arg runtime "$runtime" \
       --argjson total "$total" \
       --argjson passed "$passed" \
       --argjson failed "$failed" \
       '.test_matrix.by_runtime[$runtime] = {
           "total": $total,
           "passed": $passed,
           "failed": $failed
       }' \
       "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
done

# Add topology stats
for topology in JENKINS_SERVICE APPLICATION_SERVICE; do
    total=${TOPOLOGY_STATS["${topology}_total"]:-0}
    passed=${TOPOLOGY_STATS["${topology}_passed"]:-0}
    failed=$((total - passed))

    temp_file=$(mktemp)
    jq --arg topology "$topology" \
       --argjson total "$total" \
       --argjson passed "$passed" \
       --argjson failed "$failed" \
       '.test_matrix.by_topology[$topology] = {
           "total": $total,
           "passed": $passed,
           "failed": $failed
       }' \
       "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
done

# Add network mode stats
for network in "private-with-nat" "public-no-nat"; do
    total=${NETWORK_STATS["${network}_total"]:-0}
    passed=${NETWORK_STATS["${network}_passed"]:-0}
    failed=$((total - passed))

    temp_file=$(mktemp)
    jq --arg network "$network" \
       --argjson total "$total" \
       --argjson passed "$passed" \
       --argjson failed "$failed" \
       '.test_matrix.by_network_mode[$network] = {
           "total": $total,
           "passed": $passed,
           "failed": $failed
       }' \
       "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
done

# Add framework stats
for framework in GDPR HIPAA PCI-DSS SOC2; do
    total=${FRAMEWORK_STATS["${framework}_total"]:-0}
    passed=${FRAMEWORK_STATS["${framework}_passed"]:-0}
    failed=$((total - passed))

    temp_file=$(mktemp)
    jq --arg framework "$framework" \
       --argjson total "$total" \
       --argjson passed "$passed" \
       --argjson failed "$failed" \
       '.test_matrix.by_framework[$framework] = {
           "total": $total,
           "passed": $passed,
           "failed": $failed
       }' \
       "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"
done

# Add complexity stats
temp_file=$(mktemp)
jq --argjson minimal "${COMPLEXITY_STATS["minimal"]:-0}" \
   --argjson with_domain "${COMPLEXITY_STATS["with_domain"]:-0}" \
   --argjson with_ssl "${COMPLEXITY_STATS["with_ssl"]:-0}" \
   --argjson with_waf "${COMPLEXITY_STATS["with_waf"]:-0}" \
   '.configuration_complexity.minimal_configs = $minimal |
    .configuration_complexity.with_domain = $with_domain |
    .configuration_complexity.with_ssl = $with_ssl |
    .configuration_complexity.with_waf = $with_waf' \
   "$SNAPSHOT_FILE" > "$temp_file" && mv "$temp_file" "$SNAPSHOT_FILE"

echo ""
echo -e "${GREEN}✅ Build snapshot captured successfully${NC}"
echo "Snapshot file: $SNAPSHOT_FILE"
echo ""
echo -e "${CYAN}📊 Snapshot Summary:${NC}"
echo "  Templates: $TEMPLATE_COUNT"
echo "  Total tests: $TOTAL_TESTS (✓ $PASSED_TESTS, ✗ $FAILED_TESTS)"
echo "  Minimal configs: ${COMPLEXITY_STATS["minimal"]:-0}"
echo "  With domain: ${COMPLEXITY_STATS["with_domain"]:-0}"
echo "  With SSL: ${COMPLEXITY_STATS["with_ssl"]:-0}"
echo "  With WAF: ${COMPLEXITY_STATS["with_waf"]:-0}"
echo ""

# Create a symlink to the latest snapshot
ln -sf "$(basename "$SNAPSHOT_FILE")" "$SNAPSHOT_DIR/latest-snapshot.json"

echo -e "${GREEN}🔗 Latest snapshot linked at: $SNAPSHOT_DIR/latest-snapshot.json${NC}"
