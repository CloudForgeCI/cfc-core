#!/bin/bash

# Deployment Dry-Run Tracker
# Performs CDK deploy --dry-run tests and tracks deployment timing metrics
# Suitable for cron scheduling for continuous deployment validation

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
DEPLOYMENT_REPORTS_DIR="$BASE_DIR/test-results/deployment-reports"
HISTORICAL_DATA_DIR="$DEPLOYMENT_REPORTS_DIR/historical"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RUN_ID=$(date +"%Y%m%d-%H%M")
REPORT_FILE="$DEPLOYMENT_REPORTS_DIR/dry-run-report-$TIMESTAMP.txt"
METRICS_CSV="$HISTORICAL_DATA_DIR/deployment-metrics.csv"
CDK_OUT_DIR="$BASE_DIR/cdk.out"

# Create directories
mkdir -p "$DEPLOYMENT_REPORTS_DIR"
mkdir -p "$HISTORICAL_DATA_DIR"

# Initialize metrics CSV if it doesn't exist
if [ ! -f "$METRICS_CSV" ]; then
    echo "RunID,Timestamp,StackName,Runtime,SecurityProfile,AuthMode,NetworkMode,ComplianceFrameworks,SynthTime,AnalysisTime,ResourceCount,Status,ErrorMessage" > "$METRICS_CSV"
fi

echo -e "${BLUE}🚀 Deployment Dry-Run Tracker${NC}" | tee "$REPORT_FILE"
echo -e "${BLUE}==============================${NC}" | tee -a "$REPORT_FILE"
echo "Run ID: $RUN_ID" | tee -a "$REPORT_FILE"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "$REPORT_FILE"
echo "Domain: $DOMAIN" | tee -a "$REPORT_FILE"
echo "Reports Directory: $DEPLOYMENT_REPORTS_DIR" | tee -a "$REPORT_FILE"
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
    local audit_manager_enabled="false"
    local compliance_frameworks=""
    local cognito_auto_provision="false"
    local cognito_domain_prefix=""
    local aws_config_enabled="false"

    case "$security_profile" in
        "PRODUCTION")
            waf_enabled="true"
            alb_access_logging="true"
            guard_duty_enabled="true"
            aws_config_enabled="true"
            audit_manager_enabled="true"
            compliance_frameworks="PCI-DSS|HIPAA|SOC2|GDPR"
            if [[ "$auth_mode" == "alb-oidc" ]]; then
                cognito_auto_provision="true"
                cognito_domain_prefix="${stack_name}-auth"
            fi
            ;;
        "STAGING")
            alb_access_logging="true"
            aws_config_enabled="true"
            audit_manager_enabled="true"
            compliance_frameworks="SOC2|HIPAA"
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
  "healthCheckTimeout": "5",
  "memory": "2048",
  "enableMonitoring": "true",
  "healthCheckInterval": "30",
  "enableSsl": "true",
  "tier": "public",
  "wafEnabled": "$waf_enabled",
  "albAccessLogging": "$alb_access_logging",
  "guardDutyEnabled": "$guard_duty_enabled",
  "awsConfigEnabled": "$aws_config_enabled",
  "securityProfile": "$security_profile",
  "cloudfrontEnabled": "false",
  "healthCheckGracePeriod": "300",
  "unhealthyThreshold": "3",
  "healthyThreshold": "2",
  "networkMode": "$network_mode",
  "topology": "JENKINS_SERVICE",
  "instanceType": "t3.micro",
  "minInstanceCapacity": "2",
  "runtime": "$runtime",
  "cpu": "1024",
  "cpuTargetUtilization": "60",
  "enableAutoScaling": "true",
  "env": "dev",
  "maxInstanceCapacity": "4",
  "authMode": "$auth_mode",
  "cognitoAutoProvision": "$cognito_auto_provision",
  "cognitoDomainPrefix": "$cognito_domain_prefix",
  "cognitoUserPoolName": "${stack_name}-users",
  "cognitoMfaEnabled": "false",
  "cognitoCreateGroups": "true",
  "auditManagerEnabled": "$audit_manager_enabled",
  "complianceFrameworks": "$compliance_frameworks",
  "createConfigInfrastructure": "false",
  "domain": "$DOMAIN",
  "subdomain": "$subdomain",
  "logRetentionDays": "7",
  "region": "us-east-1",
  "enableEncryption": "true"
}
EOF

    echo "$compliance_frameworks"
}

# Function to run deployment dry-run
run_dry_run_deployment() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4
    local auth_mode=$5
    local network_mode=$6

    echo -e "\n${PURPLE}═══════════════════════════════════════════════════${NC}" | tee -a "$REPORT_FILE"
    echo -e "${YELLOW}🧪 Testing Deployment: $stack_name${NC}" | tee -a "$REPORT_FILE"
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

    local synth_log="$DEPLOYMENT_REPORTS_DIR/${stack_name}-synth-${TIMESTAMP}.log"
    local synth_error="$DEPLOYMENT_REPORTS_DIR/${stack_name}-synth-error-${TIMESTAMP}.log"
    local changeset_log="$DEPLOYMENT_REPORTS_DIR/${stack_name}-changeset-${TIMESTAMP}.log"

    cd "$BASE_DIR"

    # Step 1: Synthesize and create changeset (dry-run deployment)
    echo "  🔧 Step 1: Synthesizing CloudFormation template and creating changeset..." | tee -a "$REPORT_FILE"
    local synth_start=$(date +%s.%N)

    # Temporarily override cdk.json to use CloudForgeCommunitySample (non-interactive)
    # and inject the deployment context directly
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

    if ! cdk synth > "$synth_log" 2> "$synth_error"; then
        # Restore original cdk.json
        mv "$backup_cdk_json" "$original_cdk_json"

        local synth_end=$(date +%s.%N)
        local synth_duration=$(echo "$synth_end - $synth_start" | bc)

        echo -e "  ${RED}❌ Synthesis failed (${synth_duration}s)${NC}" | tee -a "$REPORT_FILE"
        local error_msg=$(head -1 "$synth_error" | tr ',' ' ')
        echo "  Error: $error_msg" | tee -a "$REPORT_FILE"

        # Record failure (0 for analysis time since we didn't get there)
        echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$synth_duration,0,0,SYNTH_FAILED,$error_msg" >> "$METRICS_CSV"

        return 1
    fi

    # Restore original cdk.json
    mv "$backup_cdk_json" "$original_cdk_json"

    local synth_end=$(date +%s.%N)
    local synth_duration=$(echo "$synth_end - $synth_start" | bc)
    echo -e "  ${GREEN}✅ Synthesis successful (${synth_duration}s)${NC}" | tee -a "$REPORT_FILE"

    # Count resources in template
    local template_file="$CDK_OUT_DIR/$stack_name.template.json"
    local resource_count=0
    if [ -f "$template_file" ]; then
        resource_count=$(jq '.Resources | length' "$template_file" 2>/dev/null || echo "0")
        echo "  📊 Resources in template: $resource_count" | tee -a "$REPORT_FILE"
    fi

    # Step 2: Create ChangeSet (dry-run equivalent)
    echo "  📋 Step 2: Creating CloudFormation ChangeSet..." | tee -a "$REPORT_FILE"
    local changeset_start=$(date +%s.%N)

    # Note: We can't actually create a changeset without AWS credentials and an existing stack
    # This simulates the process by analyzing the template
    echo "  ℹ️  ChangeSet creation requires AWS credentials and actual deployment" | tee -a "$REPORT_FILE"
    echo "  ℹ️  Using template analysis as proxy for deployment validation" | tee -a "$REPORT_FILE"

    # Analyze template for deployment readiness
    analyze_deployment_readiness "$template_file" "$auth_mode" "$security_profile"

    local changeset_end=$(date +%s.%N)
    local changeset_duration=$(echo "$changeset_end - $changeset_start" | bc)

    local total_duration=$(echo "$synth_duration + $changeset_duration" | bc)
    echo "" | tee -a "$REPORT_FILE"
    echo -e "  ${CYAN}⏱️  Total Time: ${total_duration}s${NC}" | tee -a "$REPORT_FILE"
    echo "     - Synthesis: ${synth_duration}s" | tee -a "$REPORT_FILE"
    echo "     - Analysis: ${changeset_duration}s" | tee -a "$REPORT_FILE"

    # Record success - now writes actual analysis time instead of count
    echo "$RUN_ID,$(date '+%Y-%m-%d %H:%M:%S'),$stack_name,$runtime,$security_profile,$auth_mode,$network_mode,$compliance_frameworks,$synth_duration,$changeset_duration,$resource_count,SUCCESS," >> "$METRICS_CSV"

    echo "" | tee -a "$REPORT_FILE"
    return 0
}

# Function to analyze deployment readiness
analyze_deployment_readiness() {
    local template_file=$1
    local auth_mode=$2
    local security_profile=$3

    if [ ! -f "$template_file" ]; then
        echo -e "  ${RED}❌ Template file not found${NC}" | tee -a "$REPORT_FILE"
        return 1
    fi

    echo "  🔍 Analyzing deployment readiness..." | tee -a "$REPORT_FILE"

    # Check for critical resources
    local has_vpc=$(grep -c "AWS::EC2::VPC" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
    local has_alb=$(grep -c "AWS::ElasticLoadBalancingV2::LoadBalancer" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
    local has_compute=$(grep -c "AWS::ECS::Service\|AWS::AutoScaling::AutoScalingGroup" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")

    local readiness_checks=0
    local readiness_passed=0

    # Check 1: VPC exists
    readiness_checks=$((readiness_checks + 1))
    if [[ $has_vpc -gt 0 ]]; then
        echo -e "     ${GREEN}✅ VPC configured${NC}" | tee -a "$REPORT_FILE"
        readiness_passed=$((readiness_passed + 1))
    else
        echo -e "     ${RED}❌ VPC missing${NC}" | tee -a "$REPORT_FILE"
    fi

    # Check 2: ALB exists
    readiness_checks=$((readiness_checks + 1))
    if [[ $has_alb -gt 0 ]]; then
        echo -e "     ${GREEN}✅ Application Load Balancer configured${NC}" | tee -a "$REPORT_FILE"
        readiness_passed=$((readiness_passed + 1))
    else
        echo -e "     ${RED}❌ ALB missing${NC}" | tee -a "$REPORT_FILE"
    fi

    # Check 3: Compute resources exist
    readiness_checks=$((readiness_checks + 1))
    if [[ $has_compute -gt 0 ]]; then
        echo -e "     ${GREEN}✅ Compute resources configured${NC}" | tee -a "$REPORT_FILE"
        readiness_passed=$((readiness_passed + 1))
    else
        echo -e "     ${RED}❌ Compute resources missing${NC}" | tee -a "$REPORT_FILE"
    fi

    # Check 4: OIDC resources if auth_mode is alb-oidc
    if [[ "$auth_mode" == "alb-oidc" ]]; then
        readiness_checks=$((readiness_checks + 1))
        local has_cognito=$(grep -c "AWS::Cognito::UserPool\"" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        if [[ $has_cognito -gt 0 ]]; then
            echo -e "     ${GREEN}✅ OIDC authentication configured (Cognito)${NC}" | tee -a "$REPORT_FILE"
            readiness_passed=$((readiness_passed + 1))
        else
            echo -e "     ${RED}❌ OIDC authentication missing${NC}" | tee -a "$REPORT_FILE"
        fi
    fi

    # Check 5: Compliance resources for PRODUCTION
    if [[ "$security_profile" == "PRODUCTION" ]]; then
        readiness_checks=$((readiness_checks + 1))
        local has_waf=$(grep -c "AWS::WAFv2::WebACL" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        if [[ $has_waf -gt 0 ]]; then
            echo -e "     ${GREEN}✅ WAF configured${NC}" | tee -a "$REPORT_FILE"
            readiness_passed=$((readiness_passed + 1))
        else
            echo -e "     ${YELLOW}⚠️  WAF missing (recommended for PRODUCTION)${NC}" | tee -a "$REPORT_FILE"
        fi
    fi

    echo "" | tee -a "$REPORT_FILE"
    echo "  📊 Readiness Score: $readiness_passed/$readiness_checks" | tee -a "$REPORT_FILE"

    if [[ $readiness_passed -eq $readiness_checks ]]; then
        echo -e "  ${GREEN}✅ Deployment ready${NC}" | tee -a "$REPORT_FILE"
        return 0
    else
        echo -e "  ${YELLOW}⚠️  Some checks failed - review before deploying${NC}" | tee -a "$REPORT_FILE"
        return 1
    fi
}

# Function to generate historical analysis
generate_historical_analysis() {
    echo "" | tee -a "$REPORT_FILE"
    echo -e "${BLUE}📈 Historical Analysis${NC}" | tee -a "$REPORT_FILE"
    echo -e "${BLUE}=====================${NC}" | tee -a "$REPORT_FILE"

    if [ ! -f "$METRICS_CSV" ] || [ $(wc -l < "$METRICS_CSV") -le 1 ]; then
        echo "No historical data available yet" | tee -a "$REPORT_FILE"
        return
    fi

    # Calculate statistics
    local total_runs=$(tail -n +2 "$METRICS_CSV" | wc -l | tr -d ' ')
    local successful_runs=$(tail -n +2 "$METRICS_CSV" | grep -c ",SUCCESS," || echo "0")
    local failed_runs=$(tail -n +2 "$METRICS_CSV" | grep -c "FAILED" || echo "0")

    echo "Total Runs: $total_runs" | tee -a "$REPORT_FILE"
    echo -e "Successful: ${GREEN}$successful_runs${NC}" | tee -a "$REPORT_FILE"
    echo -e "Failed: ${RED}$failed_runs${NC}" | tee -a "$REPORT_FILE"

    if [[ $total_runs -gt 0 ]]; then
        local success_rate=$(echo "scale=1; ($successful_runs * 100) / $total_runs" | bc)
        echo "Success Rate: ${success_rate}%" | tee -a "$REPORT_FILE"
    fi

    echo "" | tee -a "$REPORT_FILE"

    # Average synthesis time
    if [[ $successful_runs -gt 0 ]]; then
        local avg_synth=$(tail -n +2 "$METRICS_CSV" | grep ",SUCCESS," | awk -F',' '{sum+=$9; count++} END {if (count>0) printf "%.2f", sum/count}')
        local min_synth=$(tail -n +2 "$METRICS_CSV" | grep ",SUCCESS," | awk -F',' '{print $9}' | sort -n | head -1)
        local max_synth=$(tail -n +2 "$METRICS_CSV" | grep ",SUCCESS," | awk -F',' '{print $9}' | sort -n | tail -1)

        echo "Synthesis Time Statistics:" | tee -a "$REPORT_FILE"
        echo "  Average: ${avg_synth}s" | tee -a "$REPORT_FILE"
        echo "  Min: ${min_synth}s" | tee -a "$REPORT_FILE"
        echo "  Max: ${max_synth}s" | tee -a "$REPORT_FILE"
        echo "" | tee -a "$REPORT_FILE"
    fi

    # Top 5 slowest deployments
    echo "Top 5 Slowest Synthesis Times:" | tee -a "$REPORT_FILE"
    tail -n +2 "$METRICS_CSV" | grep ",SUCCESS," | sort -t',' -k9 -rn | head -5 | while IFS=',' read -r run_id timestamp stack_name runtime security_profile auth_mode network_mode compliance synth_time changeset resources status error; do
        echo "  ${stack_name}: ${synth_time}s ($security_profile, $auth_mode)" | tee -a "$REPORT_FILE"
    done

    echo "" | tee -a "$REPORT_FILE"

    # Resource count trends
    echo "Average Resource Counts by Security Profile:" | tee -a "$REPORT_FILE"
    for profile in "DEV" "STAGING" "PRODUCTION"; do
        local avg_resources=$(tail -n +2 "$METRICS_CSV" | grep ",$profile," | grep ",SUCCESS," | awk -F',' '{sum+=$11; count++} END {if (count>0) printf "%.0f", sum/count}')
        if [ -n "$avg_resources" ] && [ "$avg_resources" != "0" ]; then
            echo "  $profile: $avg_resources resources" | tee -a "$REPORT_FILE"
        fi
    done

    echo "" | tee -a "$REPORT_FILE"
}

# Main execution
echo "Starting deployment dry-run tests..." | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# Test configurations based on expected deployment frequency
# Using unique subdomains to avoid conflicts

CONFIGS=(
    # Format: runtime,security_profile,auth_mode,network_mode
    "FARGATE,PRODUCTION,alb-oidc,private-with-nat"
    "EC2,PRODUCTION,alb-oidc,private-with-nat"
    "FARGATE,STAGING,alb-oidc,public-no-nat"
    "EC2,STAGING,none,public-no-nat"
    "FARGATE,DEV,none,public-no-nat"
)

test_counter=1
successful_tests=0
failed_tests=0

for config in "${CONFIGS[@]}"; do
    IFS=',' read -r runtime security_profile auth_mode network_mode <<< "$config"

    # Generate unique subdomain using date and counter to avoid collisions
    subdomain="dryrun-$(date +%m%d)-${test_counter}"
    stack_name="dry-${runtime,,}-${security_profile,,}-${test_counter}"

    if run_dry_run_deployment "$runtime" "$security_profile" "$subdomain" "$stack_name" "$auth_mode" "$network_mode"; then
        successful_tests=$((successful_tests + 1))
    else
        failed_tests=$((failed_tests + 1))
    fi

    test_counter=$((test_counter + 1))
done

# Generate historical analysis
generate_historical_analysis

# Final summary
echo "" | tee -a "$REPORT_FILE"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}" | tee -a "$REPORT_FILE"
echo -e "${BLUE}📊 Dry-Run Summary${NC}" | tee -a "$REPORT_FILE"
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
    echo -e "${GREEN}🎉 All dry-run tests passed!${NC}" | tee -a "$REPORT_FILE"
    exit 0
else
    echo -e "${YELLOW}⚠️  Some tests failed - check error logs${NC}" | tee -a "$REPORT_FILE"
    exit 1
fi
