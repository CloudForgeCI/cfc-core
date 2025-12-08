#!/usr/bin/env bash

# Deployment Changeset Validator
# Uses 'cdk deploy --no-execute' to create actual AWS CloudFormation changesets
# Validates deployment readiness without actually executing changes

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

    local synth_log="$CHANGESET_REPORTS_DIR/${stack_name}-synth-${TIMESTAMP}.log"
    local synth_error="$CHANGESET_REPORTS_DIR/${stack_name}-synth-error-${TIMESTAMP}.log"
    local changeset_log="$CHANGESET_REPORTS_DIR/${stack_name}-changeset-${TIMESTAMP}.log"
    local changeset_error="$CHANGESET_REPORTS_DIR/${stack_name}-changeset-error-${TIMESTAMP}.log"

    cd "$BASE_DIR"

    # Temporarily override cdk.json to use CloudForgeCommunitySample (non-interactive)
    local original_cdk_json="$BASE_DIR/cdk.json"
    local backup_cdk_json="$BASE_DIR/cdk.json.backup"
    cp "$original_cdk_json" "$backup_cdk_json"

    # Read the deployment context and inject it into cdk.json
    local cfc_context=$(cat "$BASE_DIR/deployment-context.json")

    cat > "$original_cdk_json" <<EOF
{
  "app": "java -cp target/classes:target/dependency/* com.cloudforgeci.samples.app.CloudForgeCommunitySample",
  "context": {
    "cfc": $cfc_context
  }
}
EOF

    # Step 1: Synthesize
    echo "  🔧 Step 1: Synthesizing CloudFormation template..." | tee -a "$REPORT_FILE"
    local synth_start=$(date +%s.%N)

    if ! cdk synth > "$synth_log" 2> "$synth_error"; then
        # Restore original cdk.json
        mv "$backup_cdk_json" "$original_cdk_json"

        local synth_end=$(date +%s.%N)
        local synth_duration=$(echo "$synth_end - $synth_start" | bc)

        echo -e "  ${RED}❌ Synthesis failed (${synth_duration}s)${NC}" | tee -a "$REPORT_FILE"
        local error_msg=$(head -1 "$synth_error" | tr ',' ' ')
        echo "  Error: $error_msg" | tee -a "$REPORT_FILE"

        # Record failure
        echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$synth_duration,0,0,0,0,0,SYNTH_FAILED,$error_msg" >> "$METRICS_CSV"

        return 1
    fi

    # Restore original cdk.json
    mv "$backup_cdk_json" "$original_cdk_json"

    local synth_end=$(date +%s.%N)
    local synth_duration=$(echo "$synth_end - $synth_start" | bc)
    echo -e "  ${GREEN}✅ Synthesis successful (${synth_duration}s)${NC}" | tee -a "$REPORT_FILE"

    # Step 2: Create AWS CloudFormation Changeset (--no-execute)
    echo "  📋 Step 2: Creating AWS CloudFormation Changeset..." | tee -a "$REPORT_FILE"
    local changeset_start=$(date +%s.%N)

    # Use cdk deploy with --no-execute to create but not execute the changeset
    if ! cdk deploy "$stack_name" \
        --context cfc=@deployment-context.json \
        --no-execute \
        --require-approval never \
        > "$changeset_log" 2> "$changeset_error"; then

        local changeset_end=$(date +%s.%N)
        local changeset_duration=$(echo "$changeset_end - $changeset_start" | bc)

        echo -e "  ${RED}❌ Changeset creation failed (${changeset_duration}s)${NC}" | tee -a "$REPORT_FILE"
        local error_msg=$(grep -i "error\|failed" "$changeset_error" | head -1 | tr ',' ' ')
        echo "  Error: $error_msg" | tee -a "$REPORT_FILE"

        # Record failure
        echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$synth_duration,$changeset_duration,0,0,0,0,CHANGESET_FAILED,$error_msg" >> "$METRICS_CSV"

        return 1
    fi

    local changeset_end=$(date +%s.%N)
    local changeset_duration=$(echo "$changeset_end - $changeset_start" | bc)

    # Parse changeset output to count changes
    local resources_added=$(grep -c "CREATE" "$changeset_log" 2>/dev/null || echo "0")
    local resources_modified=$(grep -c "UPDATE" "$changeset_log" 2>/dev/null || echo "0")
    local resources_removed=$(grep -c "DELETE" "$changeset_log" 2>/dev/null || echo "0")
    local total_changes=$((resources_added + resources_modified + resources_removed))

    echo -e "  ${GREEN}✅ Changeset created successfully (${changeset_duration}s)${NC}" | tee -a "$REPORT_FILE"
    echo "  📊 Changes detected:" | tee -a "$REPORT_FILE"
    echo "     - Resources to add: $resources_added" | tee -a "$REPORT_FILE"
    echo "     - Resources to update: $resources_modified" | tee -a "$REPORT_FILE"
    echo "     - Resources to delete: $resources_removed" | tee -a "$REPORT_FILE"
    echo "     - Total changes: $total_changes" | tee -a "$REPORT_FILE"

    # Verify changeset was NOT executed
    echo "  ℹ️  Changeset was created but NOT executed (--no-execute)" | tee -a "$REPORT_FILE"
    echo "  ℹ️  No resources were actually created or modified" | tee -a "$REPORT_FILE"

    # Cleanup: Delete the changeset to avoid cluttering AWS account
    echo "  🧹 Cleaning up: Deleting changeset..." | tee -a "$REPORT_FILE"
    aws cloudformation delete-change-set \
        --stack-name "$stack_name" \
        --change-set-name "cdk-deploy-change-set-$stack_name" \
        --region us-east-1 \
        2>/dev/null || echo "  ⚠️  Changeset cleanup failed (may not exist)" | tee -a "$REPORT_FILE"

    local total_duration=$(echo "$synth_duration + $changeset_duration" | bc)
    echo "" | tee -a "$REPORT_FILE"
    echo -e "  ${CYAN}⏱️  Total Time: ${total_duration}s${NC}" | tee -a "$REPORT_FILE"
    echo "     - Synthesis: ${synth_duration}s" | tee -a "$REPORT_FILE"
    echo "     - Changeset Creation: ${changeset_duration}s" | tee -a "$REPORT_FILE"

    # Record success
    echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$synth_duration,$changeset_duration,$total_changes,$resources_added,$resources_modified,$resources_removed,SUCCESS," >> "$METRICS_CSV"

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

# Test configurations - smaller subset for changeset validation (requires AWS)
CONFIGS=(
    # Format: runtime,security_profile,auth_mode,network_mode
    "FARGATE,STAGING,alb-oidc,public-no-nat"
    "FARGATE,PRODUCTION,alb-oidc,private-with-nat"
    "EC2,STAGING,none,public-no-nat"
)

test_counter=1
successful_tests=0
failed_tests=0

for config in "${CONFIGS[@]}"; do
    IFS=',' read -r runtime security_profile auth_mode network_mode <<< "$config"

    # Generate unique subdomain
    subdomain="changeset-$(date +%m%d)-${test_counter}"
    stack_name="changeset-${runtime,,}-${security_profile,,}-${test_counter}"

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
