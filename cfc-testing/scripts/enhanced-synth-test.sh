#!/bin/bash

# Enhanced Synthesis Test for All Security Profiles with OIDC Authentication
# Covers EC2, Fargate, DEV/STAGING/PRODUCTION with multiple auth modes

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration - dynamically determine script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN="cloudforgeci.com"
RESULTS_DIR="$BASE_DIR/test-results/enhanced-synth-results"
CDK_OUT_DIR="$BASE_DIR/cdk.out"

# Create results directory
mkdir -p "$RESULTS_DIR"

echo -e "${BLUE}🚀 Enhanced Synthesis Test with OIDC Authentication${NC}"
echo -e "${BLUE}===================================================${NC}"
echo "Domain: $DOMAIN"
echo "Results Directory: $RESULTS_DIR"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Function to create deployment context
create_deployment_context() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4
    local auth_mode=$5
    local network_mode=$6

    # Configure security-profile-specific settings
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
            compliance_frameworks="PCI-DSS,HIPAA,SOC2,GDPR"
            if [[ "$auth_mode" == "alb-oidc" ]]; then
                cognito_auto_provision="true"
                cognito_domain_prefix="${stack_name}-auth"
            fi
            ;;
        "STAGING")
            alb_access_logging="true"
            aws_config_enabled="true"
            audit_manager_enabled="true"
            compliance_frameworks="SOC2,HIPAA"
            if [[ "$auth_mode" == "alb-oidc" ]]; then
                cognito_auto_provision="true"
                cognito_domain_prefix="${stack_name}-auth"
            fi
            ;;
        "DEV")
            # Development uses minimal security
            # No OIDC authentication in DEV
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
  "enableEncryption": "true",
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
  "breachNotectionProcedures": "true",
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
}

# Function to run synthesis and capture results
run_synthesis() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4
    local auth_mode=$5
    local network_mode=$6

    echo -e "${YELLOW}📋 Testing: $runtime + $security_profile + $auth_mode + $network_mode${NC}"
    echo "  Subdomain: $subdomain"
    echo "  Stack: $stack_name"

    # Create deployment context
    create_deployment_context "$runtime" "$security_profile" "$subdomain" "$stack_name" "$auth_mode" "$network_mode"

    # Clean previous CDK output
    rm -rf "$CDK_OUT_DIR"

    # Run synthesis
    echo "  🔧 Synthesizing..."
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

    # Capture synthesis output
    local synth_output="$RESULTS_DIR/${runtime}-${security_profile}-${auth_mode}-${network_mode}-synth.log"
    local synth_error="$RESULTS_DIR/${runtime}-${security_profile}-${auth_mode}-${network_mode}-error.log"
    local start_time=$(date +%s.%N)

    if cdk synth --quiet > "$synth_output" 2> "$synth_error"; then
        # Restore original cdk.json
        mv "$backup_cdk_json" "$original_cdk_json"

        local end_time=$(date +%s.%N)
        local duration=$(echo "$end_time - $start_time" | bc)

        echo -e "  ${GREEN}✅ Synthesis successful (${duration}s)${NC}"

        # Copy synthesized template
        local template_file="$RESULTS_DIR/${runtime}-${security_profile}-${auth_mode}-${network_mode}-template.json"
        if [ -f "$CDK_OUT_DIR/$stack_name.template.json" ]; then
            cp "$CDK_OUT_DIR/$stack_name.template.json" "$template_file"
            echo "  📄 Template saved: $template_file"

            # Analyze resources
            analyze_template "$template_file" "$auth_mode" "$security_profile" "$network_mode"
        fi

        # Record metrics
        echo "$runtime,$security_profile,$auth_mode,$network_mode,$duration,SUCCESS,$(date '+%Y-%m-%d %H:%M:%S')" >> "$RESULTS_DIR/synthesis-metrics.csv"

        return 0
    else
        # Restore original cdk.json on failure
        mv "$backup_cdk_json" "$original_cdk_json"

        local end_time=$(date +%s.%N)
        local duration=$(echo "$end_time - $start_time" | bc)

        echo -e "  ${RED}❌ Synthesis failed (${duration}s)${NC}"
        echo "  📄 Error log: $synth_error"

        # Record metrics
        echo "$runtime,$security_profile,$auth_mode,$network_mode,$duration,FAILED,$(date '+%Y-%m-%d %H:%M:%S')" >> "$RESULTS_DIR/synthesis-metrics.csv"

        return 1
    fi
}

# Function to analyze template resources
analyze_template() {
    local template_file=$1
    local auth_mode=$2
    local security_profile=$3
    local network_mode=$4

    # Count key resources
    local sg_count=$(grep -c "AWS::EC2::SecurityGroup" "$template_file" 2>/dev/null || echo "0")
    local iam_count=$(grep -c "AWS::IAM::Role" "$template_file" 2>/dev/null || echo "0")
    local alb_count=$(grep -c "AWS::ElasticLoadBalancingV2::LoadBalancer" "$template_file" 2>/dev/null || echo "0")
    local r53_count=$(grep -c "AWS::Route53::RecordSet" "$template_file" 2>/dev/null || echo "0")

    echo "  📊 Resource Counts:"
    echo "     Security Groups: $sg_count"
    echo "     IAM Roles: $iam_count"
    echo "     Load Balancers: $alb_count"
    echo "     Route53 Records: $r53_count"

    # Check OIDC-specific resources
    if [[ "$auth_mode" == "alb-oidc" ]]; then
        local cognito_pool=$(grep -c "AWS::Cognito::UserPool\"" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        local cognito_client=$(grep -c "AWS::Cognito::UserPoolClient" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        local cognito_domain=$(grep -c "AWS::Cognito::UserPoolDomain" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        local secrets=$(grep -c "AWS::SecretsManager::Secret" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")

        echo "  🔐 OIDC Resources:"
        echo "     Cognito User Pool: $cognito_pool"
        echo "     Cognito Client: $cognito_client"
        echo "     Cognito Domain: $cognito_domain"
        echo "     Secrets Manager: $secrets"

        if [[ $cognito_pool -gt 0 && $cognito_client -gt 0 && $cognito_domain -gt 0 ]]; then
            echo -e "     ${GREEN}✅ Cognito OIDC complete${NC}"
        else
            echo -e "     ${RED}❌ Cognito OIDC incomplete${NC}"
        fi
    fi

    # Check compliance resources for PRODUCTION/STAGING
    if [[ "$security_profile" == "PRODUCTION" || "$security_profile" == "STAGING" ]]; then
        local waf_count=$(grep -c "AWS::WAFv2::WebACL" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        local config_count=$(grep -c "AWS::Config::ConfigRule" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        local audit_mgr_count=$(grep -c "AWS::AuditManager::Assessment" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")

        echo "  🛡️  Compliance Resources:"
        echo "     WAF Web ACL: $waf_count"
        echo "     Config Rules: $config_count"
        echo "     Audit Manager Assessments: $audit_mgr_count"

        if [[ "$security_profile" == "PRODUCTION" && $waf_count -eq 0 ]]; then
            echo -e "     ${YELLOW}⚠️  WAF expected in PRODUCTION${NC}"
        fi
    fi

    # Check network mode resources
    if [[ "$network_mode" == "private-with-nat" ]]; then
        local nat_count=$(grep -c "AWS::EC2::NatGateway" "$template_file" 2>/dev/null | tr -d '\n' || echo "0")
        echo "  🌐 Private Network:"
        echo "     NAT Gateways: $nat_count"

        if [[ $nat_count -eq 0 ]]; then
            echo -e "     ${RED}❌ NAT Gateway missing for private network${NC}"
        fi
    fi
}

# Function to generate summary report
generate_summary() {
    echo ""
    echo -e "${BLUE}📊 Enhanced Synthesis Test Summary${NC}"
    echo -e "${BLUE}====================================${NC}"

    if [ ! -f "$RESULTS_DIR/synthesis-metrics.csv" ]; then
        echo -e "${RED}No metrics found${NC}"
        return
    fi

    local total_tests=$(wc -l < "$RESULTS_DIR/synthesis-metrics.csv")
    local successful=$(grep -c ",SUCCESS," "$RESULTS_DIR/synthesis-metrics.csv" || echo "0")
    local failed=$(grep -c ",FAILED," "$RESULTS_DIR/synthesis-metrics.csv" || echo "0")

    echo "Total Tests: $total_tests"
    echo -e "Successful: ${GREEN}$successful${NC}"
    echo -e "Failed: ${RED}$failed${NC}"
    echo ""

    # Calculate average synthesis time for successful tests
    if [[ $successful -gt 0 ]]; then
        local avg_time=$(awk -F',' '/,SUCCESS,/ {sum+=$5; count++} END {if (count>0) print sum/count}' "$RESULTS_DIR/synthesis-metrics.csv")
        echo -e "Average Synthesis Time: ${GREEN}${avg_time}s${NC}"

        # Find min/max
        local min_time=$(awk -F',' '/,SUCCESS,/ {print $5}' "$RESULTS_DIR/synthesis-metrics.csv" | sort -n | head -1)
        local max_time=$(awk -F',' '/,SUCCESS,/ {print $5}' "$RESULTS_DIR/synthesis-metrics.csv" | sort -n | tail -1)
        echo "Min: ${min_time}s | Max: ${max_time}s"
    fi

    echo ""
    echo -e "${CYAN}📋 Test Matrix Coverage:${NC}"
    echo "  Runtimes: EC2, Fargate"
    echo "  Security Profiles: DEV, STAGING, PRODUCTION"
    echo "  Auth Modes: none, alb-oidc (Cognito)"
    echo "  Network Modes: public-no-nat, private-with-nat"
    echo ""
}

# Main execution
echo "Starting enhanced synthesis tests..."
echo "Runtime,SecurityProfile,AuthMode,NetworkMode,Duration,Status,Timestamp" > "$RESULTS_DIR/synthesis-metrics.csv"
echo ""

# Test matrix
RUNTIMES=("EC2" "FARGATE")
SECURITY_PROFILES=("DEV" "STAGING" "PRODUCTION")
AUTH_MODES=("none" "alb-oidc")
NETWORK_MODES=("public-no-nat" "private-with-nat")

test_counter=1

for runtime in "${RUNTIMES[@]}"; do
    for security_profile in "${SECURITY_PROFILES[@]}"; do
        for network_mode in "${NETWORK_MODES[@]}"; do
            # DEV doesn't use OIDC
            if [[ "$security_profile" == "DEV" ]]; then
                subdomain="test-dev-${test_counter}"
                stack_name="${runtime,,}-dev-${network_mode}-${test_counter}"
                run_synthesis "$runtime" "$security_profile" "$subdomain" "$stack_name" "none" "$network_mode"
                test_counter=$((test_counter + 1))
                echo ""
            else
                # STAGING and PRODUCTION test both auth modes
                for auth_mode in "${AUTH_MODES[@]}"; do
                    subdomain="test-${security_profile,,}-${test_counter}"
                    stack_name="${runtime,,}-${security_profile,,}-${auth_mode}-${network_mode}-${test_counter}"

                    # Note: PRODUCTION + public-no-nat is expected to FAIL (PCI-DSS validation)
                    # This validates that compliance blocking works correctly
                    run_synthesis "$runtime" "$security_profile" "$subdomain" "$stack_name" "$auth_mode" "$network_mode"
                    test_counter=$((test_counter + 1))
                    echo ""
                done
            fi
        done
    done
done

# Generate summary
generate_summary

echo ""
echo -e "${GREEN}🎉 Enhanced synthesis test completed!${NC}"
echo "Results saved in: $RESULTS_DIR"
echo "Metrics CSV: $RESULTS_DIR/synthesis-metrics.csv"
echo ""
echo -e "${YELLOW}📋 Next Steps:${NC}"
echo "1. Review synthesis-metrics.csv for performance trends"
echo "2. Check error logs for any failures"
echo "3. Validate OIDC resource creation in templates"
echo "4. Test actual deployments for working combinations"
echo ""
