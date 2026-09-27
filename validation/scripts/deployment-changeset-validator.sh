#!/usr/bin/env bash

# Deployment Changeset Validator
# Uses 'cloudforge-cli deploy --target aws --dry-run' to create actual AWS CloudFormation
# changesets -- creates the change set, reports what it would do, deletes it unexecuted.
# Validates deployment readiness without actually executing changes.
# Requires cloudforge-cli on PATH.

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN="${DOMAIN:-cloudforgeci.com}"
CHANGESET_REPORTS_DIR="$BASE_DIR/test-results/changeset-reports"
HISTORICAL_DATA_DIR="$CHANGESET_REPORTS_DIR/historical"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RUN_ID=$(date +"%Y%m%d-%H%M")
REPORT_FILE="$CHANGESET_REPORTS_DIR/changeset-report-$TIMESTAMP.txt"
METRICS_CSV="$HISTORICAL_DATA_DIR/changeset-metrics.csv"
CDK_OUT_DIR="$BASE_DIR/cdk.out"

# Create directories
mkdir -p "$CHANGESET_REPORTS_DIR"
mkdir -p "$HISTORICAL_DATA_DIR"

# Initialize metrics CSV if it doesn't exist
if [ ! -f "$METRICS_CSV" ]; then
    echo "RunID,Timestamp,StackName,Runtime,SecurityProfile,AuthMode,NetworkMode,ComplianceFrameworks,SynthTime,ChangesetTime,TotalChanges,ResourcesAdded,ResourcesModified,ResourcesRemoved,Status,ErrorMessage" > "$METRICS_CSV"
fi

echo -e "${BLUE}🔍 Deployment Changeset Validator${NC}" | tee "$REPORT_FILE"
echo -e "${BLUE}===================================${NC}" | tee -a "$REPORT_FILE"
echo "Run ID: $RUN_ID" | tee -a "$REPORT_FILE"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "$REPORT_FILE"
echo "Domain: $DOMAIN" | tee -a "$REPORT_FILE"
echo "AWS Account: $(aws sts get-caller-identity --query Account --output text 2>/dev/null || echo 'Not configured')" | tee -a "$REPORT_FILE"
echo "AWS Region: ${AWS_DEFAULT_REGION:-us-east-1}" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# Function to create deployment context
create_deployment_context() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4
    local auth_mode=$5
    local network_mode=$6

    local waf_enabled="false"
    local alb_access_logging="false"
    local guard_duty_enabled="false"
    local aws_config_enabled="false"
    local create_config_infrastructure="false"
    local compliance_frameworks=""
    local cognito_auto_provision="false"
    local cognito_domain_prefix=""

    # For synthesis-only tests, disable Audit Manager (requires AWS API calls)
    local audit_manager_enabled="false"

    case "$security_profile" in
        "PRODUCTION")
            waf_enabled="true"
            alb_access_logging="true"
            guard_duty_enabled="true"
            aws_config_enabled="true"
            create_config_infrastructure="false"  # Use existing Config infrastructure
            compliance_frameworks="PCI-DSS,HIPAA,SOC2,GDPR"
            if [[ "$auth_mode" == "alb-oidc" ]]; then
                cognito_auto_provision="true"
                cognito_domain_prefix="${stack_name}-auth"
            fi
            ;;
        "STAGING")
            alb_access_logging="true"
            aws_config_enabled="true"
            create_config_infrastructure="false"  # Use existing Config infrastructure
            compliance_frameworks="SOC2,HIPAA"
            if [[ "$auth_mode" == "alb-oidc" ]]; then
                cognito_auto_provision="true"
                cognito_domain_prefix="${stack_name}-auth"
            fi
            ;;
        "DEV")
            auth_mode="none"
            ;;
    esac

    cat > "$BASE_DIR/deployment-context.json" << EOF
{
  "stackName": "$stack_name",
  "applicationId": "jenkins",
  "applicationName": "Jenkins",
  "deploymentType": "jenkins",
  "tier": "public",
  "domain": "$DOMAIN",
  "subdomain": "$subdomain",
  "enableSsl": "true",
  "runtime": "$runtime",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "$security_profile",
  "networkMode": "$network_mode",
  "wafEnabled": "$waf_enabled",
  "albAccessLogging": "$alb_access_logging",
  "guardDutyEnabled": "$guard_duty_enabled",
  "awsConfigEnabled": "$aws_config_enabled",
  "createConfigInfrastructure": "$create_config_infrastructure",
  "auditManagerEnabled": "$audit_manager_enabled",
  "complianceFrameworks": "$compliance_frameworks",
  "cloudfrontEnabled": "false",
  "minInstanceCapacity": "2",
  "maxInstanceCapacity": "4",
  "cpuTargetUtilization": "60",
  "cpu": "1024",
  "memory": "2048",
  "instanceType": "t3.micro",
  "authMode": "$auth_mode",
  "cognitoAutoProvision": "$cognito_auto_provision",
  "cognitoDomainPrefix": "$cognito_domain_prefix",
  "cognitoUserPoolName": "${stack_name}-users",
  "cognitoMfaEnabled": "false",
  "cognitoCreateGroups": "true",
  "enableMonitoring": "true",
  "enableEncryption": "true",
  "logRetentionDays": "7",
  "region": "us-east-1",
  "enableAutoScaling": "true",
  "healthCheckGracePeriod": "300",
  "healthCheckInterval": "30",
  "healthCheckTimeout": "5",
  "healthyThreshold": "2",
  "unhealthyThreshold": "3",
  "bastionCidr": "10.0.1.0/24",
  "lbType": "alb",
  "enableFlowlogs": "false",
  "retainStorage": "false",
  "createZone": "true",
  "artifactsPrefix": "jenkins/job/\${JOB_NAME}/\${BUILD_NUMBER}",
  "env": "dev",
  "awsBaaSigned": "true",
  "thirdPartyBaasDocumented": "true",
  "baaProvisionsVerified": "true",
  "subcontractorBaasTracked": "true",
  "workforceAuthorizationProcedures": "true",
  "terminationProcedures": "true",
  "hipaaTrainingProgram": "true",
  "emergencyAccessProcedures": "true",
  "automaticLogoffEnabled": "true",
  "incidentResponsePlan": "true",
  "breachNotificationProcedures": "true",
  "breachDetectionAutomation": "true",
  "customConfigurationApplied": "true",
  "kmsKeyRotationEnabled": "true",
  "useCustomerManagedKeys": "true",
  "gdprLegalBasisDocumented": "true",
  "gdprConsentMechanismImplemented": "true",
  "gdprPrivacyNoticeProvided": "true",
  "gdprDataSubjectRequestProcedures": "true",
  "gdprRightToErasureCapability": "true",
  "gdprDataPortabilityCapability": "true",
  "gdprDpiaCompleted": "true",
  "gdprPrivacyByDesignImplemented": "true",
  "gdprDataLocalizationEnforced": "true",
  "gdprDataRetentionPolicyDefined": "true",
  "gdprRecordsOfProcessingActivities": "true"
}
EOF

    echo "$compliance_frameworks"
}

# Function to run deployment with changeset creation
run_changeset_deployment() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4
    local auth_mode=$5
    local network_mode=$6

    echo -e "\n${PURPLE}═══════════════════════════════════════════════════${NC}" | tee -a "$REPORT_FILE"
    echo -e "${YELLOW}🧪 Testing Deployment Changeset: $stack_name${NC}" | tee -a "$REPORT_FILE"
    echo -e "${PURPLE}═══════════════════════════════════════════════════${NC}" | tee -a "$REPORT_FILE"
    echo "  Runtime: $runtime" | tee -a "$REPORT_FILE"
    echo "  Security Profile: $security_profile" | tee -a "$REPORT_FILE"
    echo "  Auth Mode: $auth_mode" | tee -a "$REPORT_FILE"
    echo "  Network Mode: $network_mode" | tee -a "$REPORT_FILE"
    echo "  Subdomain: $subdomain.$DOMAIN" | tee -a "$REPORT_FILE"
    echo "" | tee -a "$REPORT_FILE"

    # Create deployment context and capture compliance frameworks
    local compliance_frameworks=$(create_deployment_context "$runtime" "$security_profile" "$subdomain" "$stack_name" "$auth_mode" "$network_mode")

    # Clean previous CDK output
    rm -rf "$CDK_OUT_DIR"

    local deploy_log="$CHANGESET_REPORTS_DIR/${stack_name}-deploy-${TIMESTAMP}.log"

    cd "$BASE_DIR"

    # Synthesize and create the AWS CloudFormation changeset in one call -- cloudforge-cli's
    # --dry-run creates the change set, reports the resource changes it would make, then deletes
    # it unexecuted (see AwsDirectDeployer#previewChangeSet). Synth and changeset creation aren't
    # separately timed here the way the two `cdk` CLI calls used to be; the combined duration
    # below covers both.
    echo "  🔧 Synthesizing and creating AWS CloudFormation changeset (dry run)..." | tee -a "$REPORT_FILE"
    local start=$(date +%s.%N)

    if ! cloudforge-cli deploy --context "$BASE_DIR/deployment-context.json" --target aws --dry-run \
            > "$deploy_log" 2>&1; then
        local end=$(date +%s.%N)
        local duration=$(echo "$end - $start" | bc)

        echo -e "  ${RED}❌ Dry run failed (${duration}s)${NC}" | tee -a "$REPORT_FILE"
        local error_msg=$(grep -m1 '"status":"error"' "$deploy_log" | jq -r '.message // "unknown error"' 2>/dev/null)
        echo "  Error: $error_msg" | tee -a "$REPORT_FILE"

        # Record failure
        echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$duration,0,0,0,0,0,DRY_RUN_FAILED,$error_msg" >> "$METRICS_CSV"

        return 1
    fi

    local end=$(date +%s.%N)
    local duration=$(echo "$end - $start" | bc)

    # The final "deploy complete" JSON line carries the change summary in its messages array --
    # one line per resource change, formatted "<Add|Modify|Remove> <LogicalId> (<ResourceType>)".
    local complete_line=$(grep -m1 '"phase":"deploy"' "$deploy_log" | grep '"status":"complete"')
    local resources_added=$(echo "$complete_line" | jq -r '.messages[]?' 2>/dev/null | grep -ic '^Add ')
    local resources_modified=$(echo "$complete_line" | jq -r '.messages[]?' 2>/dev/null | grep -ic '^Modify ')
    local resources_removed=$(echo "$complete_line" | jq -r '.messages[]?' 2>/dev/null | grep -ic '^Remove ')
    local total_changes=$((resources_added + resources_modified + resources_removed))

    echo -e "  ${GREEN}✅ Dry run succeeded (${duration}s)${NC}" | tee -a "$REPORT_FILE"
    echo "  📊 Changes detected:" | tee -a "$REPORT_FILE"
    echo "     - Resources to add: $resources_added" | tee -a "$REPORT_FILE"
    echo "     - Resources to modify: $resources_modified" | tee -a "$REPORT_FILE"
    echo "     - Resources to remove: $resources_removed" | tee -a "$REPORT_FILE"
    echo "     - Total changes: $total_changes" | tee -a "$REPORT_FILE"
    echo "  ℹ️  Change set was created but NOT executed, then deleted (cloudforge-cli --dry-run)" | tee -a "$REPORT_FILE"

    echo "" | tee -a "$REPORT_FILE"
    echo -e "  ${CYAN}⏱️  Total Time: ${duration}s${NC}" | tee -a "$REPORT_FILE"

    # Record success
    echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$duration,0,$total_changes,$resources_added,$resources_modified,$resources_removed,SUCCESS," >> "$METRICS_CSV"

    echo "" | tee -a "$REPORT_FILE"
    return 0
}

# Check AWS credentials
if ! aws sts get-caller-identity &>/dev/null; then
    echo -e "${RED}❌ AWS credentials not configured${NC}" | tee -a "$REPORT_FILE"
    echo "This script requires AWS credentials to create changesets" | tee -a "$REPORT_FILE"
    echo "Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables" | tee -a "$REPORT_FILE"
    exit 1
fi

echo -e "${GREEN}✅ AWS credentials verified${NC}" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

echo "Starting changeset validation tests..." | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# Configuration for random subset selection
RANDOM_SUBSET_COUNT="${RANDOM_SUBSET_COUNT:-5}"  # Number of random configs to test (default: 5)
USE_CSV_CONFIGS="${USE_CSV_CONFIGS:-true}"        # Use CSV configs (default: true)
CSV_FILE="${CSV_FILE:-$SCRIPT_DIR/../cloudforge-api/src/test/resources/compliance-test-matrix.csv}"

# Try alternate location if first doesn't exist
if [ ! -f "$CSV_FILE" ]; then
    CSV_FILE="$BASE_DIR/../cloudforge-api/src/test/resources/compliance-test-matrix.csv"
fi

echo "Configuration:" | tee -a "$REPORT_FILE"
echo "  - Random subset count: $RANDOM_SUBSET_COUNT" | tee -a "$REPORT_FILE"
echo "  - Use CSV configs: $USE_CSV_CONFIGS" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# Build configuration array
declare -a CONFIGS

if [[ "$USE_CSV_CONFIGS" == "true" ]] && [[ -f "$CSV_FILE" ]]; then
    echo "📋 Reading configurations from CSV: $CSV_FILE" | tee -a "$REPORT_FILE"

    # Read CSV and extract passing configurations (exclude FAIL_ prefixed and comments)
    # CSV format: config_name,runtime,security_profile,domain_config,ssl_config,subdomain_config,auth_mode,network_mode,compliance_framework,...
    mapfile -t ALL_CONFIGS < <(
        grep -v "^#" "$CSV_FILE" | \
        grep -v "^FAIL_" | \
        grep -v "^config_name" | \
        awk -F',' '{
            # Only include PRODUCTION configs to test actual compliance
            if ($3 == "PRODUCTION" && $4 == "with-domain" && $5 == "ssl-enabled") {
                print $2 "," $3 "," $7 "," $8 "," $9
            }
        }' | sort -u
    )

    total_available=${#ALL_CONFIGS[@]}
    echo "  - Total eligible configs: $total_available" | tee -a "$REPORT_FILE"

    if [[ $total_available -eq 0 ]]; then
        echo "  ⚠️  No eligible configs found in CSV, falling back to defaults" | tee -a "$REPORT_FILE"
        USE_CSV_CONFIGS="false"
    else
        # Select random subset
        if [[ $RANDOM_SUBSET_COUNT -ge $total_available ]]; then
            echo "  - Using all $total_available configs (subset size >= total)" | tee -a "$REPORT_FILE"
            CONFIGS=("${ALL_CONFIGS[@]}")
        else
            echo "  - Selecting $RANDOM_SUBSET_COUNT random configs from $total_available" | tee -a "$REPORT_FILE"

            # Shuffle and select N items
            mapfile -t CONFIGS < <(
                printf '%s\n' "${ALL_CONFIGS[@]}" | shuf -n "$RANDOM_SUBSET_COUNT"
            )
        fi

        echo "" | tee -a "$REPORT_FILE"
        echo "Selected configurations:" | tee -a "$REPORT_FILE"
        for config in "${CONFIGS[@]}"; do
            echo "  - $config" | tee -a "$REPORT_FILE"
        done
        echo "" | tee -a "$REPORT_FILE"
    fi
fi

# Fallback to default configs if CSV not available or disabled
if [[ "$USE_CSV_CONFIGS" != "true" ]] || [[ ${#CONFIGS[@]} -eq 0 ]]; then
    echo "📋 Using default test configurations" | tee -a "$REPORT_FILE"
    CONFIGS=(
        # Format: runtime,security_profile,auth_mode,network_mode,compliance_framework
        "FARGATE,STAGING,alb-oidc,public-no-nat,SOC2"
        "FARGATE,PRODUCTION,alb-oidc,private-with-nat,PCI-DSS"
        "EC2,STAGING,none,public-no-nat,HIPAA"
    )
fi

test_counter=1
successful_tests=0
failed_tests=0
total_tests=${#CONFIGS[@]}

echo "🧪 Running $total_tests changeset validation tests..." | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

for config in "${CONFIGS[@]}"; do
    IFS=',' read -r runtime security_profile auth_mode network_mode compliance_framework <<< "$config"

    # Generate unique subdomain
    subdomain="changeset-$(date +%m%d)-${test_counter}"
    stack_name="changeset-${runtime,,}-${security_profile,,}-${test_counter}"

    echo "[$test_counter/$total_tests] Testing: $runtime / $security_profile / $auth_mode / $network_mode / $compliance_framework" | tee -a "$REPORT_FILE"

    if run_changeset_deployment "$runtime" "$security_profile" "$subdomain" "$stack_name" "$auth_mode" "$network_mode"; then
        successful_tests=$((successful_tests + 1))
    else
        failed_tests=$((failed_tests + 1))
    fi

    test_counter=$((test_counter + 1))
done

# Generate summary
echo "" | tee -a "$REPORT_FILE"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}" | tee -a "$REPORT_FILE"
echo -e "${BLUE}📊 Changeset Validation Summary${NC}" | tee -a "$REPORT_FILE"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}" | tee -a "$REPORT_FILE"
echo "Run ID: $RUN_ID" | tee -a "$REPORT_FILE"
echo "Total Tests: $test_counter" | tee -a "$REPORT_FILE"
echo -e "Successful: ${GREEN}$successful_tests${NC}" | tee -a "$REPORT_FILE"
echo -e "Failed: ${RED}$failed_tests${NC}" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"
echo "Report saved: $REPORT_FILE" | tee -a "$REPORT_FILE"
echo "Metrics CSV: $METRICS_CSV" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

if [[ $failed_tests -eq 0 ]]; then
    echo -e "${GREEN}🎉 All changeset validation tests passed!${NC}" | tee -a "$REPORT_FILE"
    exit 0
else
    echo -e "${YELLOW}⚠️  Some tests failed - check error logs${NC}" | tee -a "$REPORT_FILE"
    exit 1
fi
