#!/usr/bin/env bash

# Comprehensive Synthesis Test for All Security Profiles
# Tests EC2 and Fargate runtimes across DEV, STAGING, PRODUCTION security profiles

# Don't exit on error - we want to run all tests and report failures at the end
# set -e

# Track overall success
OVERALL_SUCCESS=true
FAILED_TESTS=()
ADVISORY_TESTS=()
LAST_TEST_HAS_ADVISORIES=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
ORANGE='\033[0;33m'
NC='\033[0m' # No Color

# Configuration - dynamically determine script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# BASE_DIR should be cfc-testing/ (parent of scripts/) where cdk.json exists
BASE_DIR="${BASE_DIR:-$(dirname "$SCRIPT_DIR")}"
DOMAIN="cloudforgeci.com"
RESULTS_DIR="$SCRIPT_DIR/synth-results"
CDK_OUT_DIR="$BASE_DIR/cdk.out"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Function to parse cdk-nag CSV reports for warnings (L1 advisories)
# Returns count of warnings found and prints them
parse_cdknag_csv_warnings() {
    local stack_name="$1"
    local warning_count=0

    # Find cdk-nag CSV reports for this stack
    for csv_file in "$CDK_OUT_DIR"/*-"${stack_name}"-NagReport.csv "$CDK_OUT_DIR"/*NagReport.csv; do
        if [ -f "$csv_file" ]; then
            # Extract pack name from filename (e.g., "AwsSolutions" from "AwsSolutions-stack-NagReport.csv")
            local pack_name=$(basename "$csv_file" | cut -d'-' -f1)

            # Parse CSV for Warning level + Non-Compliant entries
            # CSV format: Rule ID,Resource ID,Compliance,Exception Reason,Rule Level,Rule Info
            while IFS=',' read -r rule_id resource_id compliance exception rule_level rule_info; do
                # Clean up quoted fields
                rule_id="${rule_id//\"/}"
                compliance="${compliance//\"/}"
                rule_level="${rule_level//\"/}"
                rule_info="${rule_info//\"/}"

                # Check for Warning level with Non-Compliant status
                if [[ "$rule_level" == "Warning" && "$compliance" == "Non-Compliant" ]]; then
                    ((warning_count++))
                    # Print first 10 warnings
                    if [ $warning_count -le 10 ]; then
                        echo "      ⚠ [$pack_name] $rule_id: $rule_info"
                    fi
                fi
            done < <(tail -n +2 "$csv_file")  # Skip header row
        fi
    done

    if [ $warning_count -gt 10 ]; then
        echo "      ... and $((warning_count - 10)) more warnings"
    fi

    return $warning_count
}

echo -e "${BLUE}🚀 Comprehensive Synthesis Test${NC}"
echo -e "${BLUE}================================${NC}"
echo "Domain: $DOMAIN"
echo "Results Directory: $RESULTS_DIR"
echo ""

# Function to create deployment context
create_deployment_context() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4

    # Configure security-profile-specific settings
    local waf_enabled="false"
    local alb_access_logging="false"
    local guard_duty_enabled="false"
    local auth_mode="none"
    local cognito_auto_provision="false"
    local audit_manager_enabled="false"
    local compliance_frameworks=""
    local aws_config_enabled="false"
    local create_config_infrastructure="false"

    case "$security_profile" in
        "PRODUCTION")
            waf_enabled="true"
            alb_access_logging="true"
            guard_duty_enabled="true"
            auth_mode="alb-oidc"
            cognito_auto_provision="true"
            audit_manager_enabled="false"  # Disabled in tests (requires per-region setup)
            compliance_frameworks="SOC2,HIPAA,PCI-DSS,GDPR"
            aws_config_enabled="true"
            create_config_infrastructure="false"  # Don't create in tests (account-level singleton)
            ;;
        "STAGING")
            alb_access_logging="true"
            auth_mode="alb-oidc"
            cognito_auto_provision="true"
            audit_manager_enabled="false"  # Disabled in tests
            compliance_frameworks="SOC2"
            aws_config_enabled="true"
            create_config_infrastructure="false"
            ;;
        "DEV")
            # Development uses minimal security
            ;;
    esac

    cat > "$BASE_DIR/deployment-context.json" << EOF
{
  "stackName": "$stack_name",
  "applicationId": "jenkins",
  "applicationName": "Jenkins",
  "healthCheckTimeout": "5",
  "memory": "2048",
  "enableMonitoring": "true",
  "healthCheckInterval": "30",
  "enableSsl": "true",
  "tier": "public",
  "wafEnabled": "$waf_enabled",
  "albAccessLogging": "$alb_access_logging",
  "guardDutyEnabled": "$guard_duty_enabled",
  "securityProfile": "$security_profile",
  "cloudfrontEnabled": "false",
  "healthCheckGracePeriod": "300",
  "unhealthyThreshold": "3",
  "healthyThreshold": "2",
  "networkMode": "public-no-nat",
  "topology": "APPLICATION_SERVICE",
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
  "cognitoDomainPrefix": "${stack_name}-auth",
  "cognitoUserPoolName": "${stack_name}-users",
  "cognitoMfaEnabled": "false",
  "cognitoCreateGroups": "true",
  "auditManagerEnabled": "$audit_manager_enabled",
  "complianceFrameworks": "$compliance_frameworks",
  "awsConfigEnabled": "$aws_config_enabled",
  "createConfigInfrastructure": "$create_config_infrastructure",
  "domain": "$DOMAIN",
  "subdomain": "$subdomain",
  "logRetentionDays": "7",
  "region": "us-east-1",
  "enableEncryption": "true"
}
EOF
}

# Function to run synthesis and capture results
run_synthesis() {
    local runtime=$1
    local security_profile=$2
    local subdomain=$3
    local stack_name=$4
    
    echo -e "${YELLOW}📋 Testing: $runtime + $security_profile + $subdomain${NC}"
    
    # Create deployment context
    create_deployment_context "$runtime" "$security_profile" "$subdomain" "$stack_name"

    # Verify deployment context was created
    if [ ! -f "$BASE_DIR/deployment-context.json" ]; then
        echo -e "  ${RED}❌ Failed to create deployment-context.json${NC}"
        return 1
    fi

    # Debug: Show first few lines of context (for troubleshooting)
    echo "  📋 Deployment context created (stack: $stack_name)"

    # Clean previous CDK output
    rm -rf "$CDK_OUT_DIR"

    # Run synthesis
    echo "  🔧 Synthesizing..."
    cd "$BASE_DIR"
    
    # Capture synthesis output
    local synth_output="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-synth.log"
    local synth_error="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-error.log"

    # Use cdk synth with deployment-context.json (written by create_deployment_context)
    # Note: cdk-nag may cause exit code 1 even when synthesis succeeds, so we check for template file instead
    # Add timeout to prevent hanging (5 minutes max) - use gtimeout on macOS if available
    local timeout_cmd="timeout"
    if command -v gtimeout &> /dev/null; then
        timeout_cmd="gtimeout"
    elif ! command -v timeout &> /dev/null; then
        # No timeout command available, run without timeout
        timeout_cmd=""
    fi

    if [ -n "$timeout_cmd" ]; then
        $timeout_cmd 300 cdk synth --quiet --context cfc=@deployment-context.json > "$synth_output" 2> "$synth_error"
    else
        cdk synth --quiet --context cfc=@deployment-context.json > "$synth_output" 2> "$synth_error"
    fi
    local synth_exit_code=$?

    # Check if timeout occurred (exit code 124)
    if [ $synth_exit_code -eq 124 ]; then
        echo -e "  ${RED}❌ Synthesis timed out after 5 minutes${NC}"
        echo "Synthesis timed out" > "$synth_error"
        return 1
    fi

    # Check if synthesis succeeded by looking for the generated template file
    local template_file="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.json"
    if [ -f "$CDK_OUT_DIR/$stack_name.template.json" ]; then
        cp "$CDK_OUT_DIR/$stack_name.template.json" "$template_file"
        echo "  📄 Template saved: $template_file"

        # Parse cdk-nag output for advisories and errors
        local has_errors=false
        local has_advisories=false
        local advisory_layers=""

        # Check synth output/error for cdk-nag warnings (advisories) vs errors
        # cdk-nag output format: "[Error at /path] PCI.DSS.321-XXX: message" or "[Warning at /path] AwsSolutions-XXX: message"
        if [ -f "$synth_error" ] && [ -s "$synth_error" ]; then
            # Look for cdk-nag errors - format: [Error at /path] RuleId: message
            if grep -qE "\[Error at.*\] (AwsSolutions-|NIST|HIPAA|PCI)" "$synth_error" 2>/dev/null; then
                has_errors=true
            fi
            # Look for cdk-nag warnings/advisories - format: [Warning at /path] RuleId: message
            if grep -qE "\[(Warning|Info) at.*\] (AwsSolutions-|NIST|HIPAA|PCI)" "$synth_error" 2>/dev/null; then
                has_advisories=true
                # Extract which layers have advisories
                if grep -qE "Layer 1|cdk-nag|\[Warning at" "$synth_error" 2>/dev/null; then
                    advisory_layers="L1"
                fi
            fi
        fi

        # Also check synth_output for advisories
        if [ -f "$synth_output" ] && [ -s "$synth_output" ]; then
            # cdk-nag format: [Warning at /path] RuleId: message
            if grep -qE "\[(Warning|Info) at.*\] (AwsSolutions-|NIST|HIPAA|PCI)" "$synth_output" 2>/dev/null; then
                has_advisories=true
            fi
            # Check for FrameworkRules warnings (Layer 2)
            if grep -qE "⚠️.*Layer 2|FrameworkRules.*warning" "$synth_output" 2>/dev/null; then
                has_advisories=true
                if [ -n "$advisory_layers" ]; then
                    advisory_layers="$advisory_layers,L2"
                else
                    advisory_layers="L2"
                fi
            fi
            # Check for cfn-guard advisories (Layer 3)
            if grep -qE "⚠️.*Layer 3|cfn-guard.*warning" "$synth_output" 2>/dev/null; then
                has_advisories=true
                if [ -n "$advisory_layers" ]; then
                    advisory_layers="$advisory_layers,L3"
                else
                    advisory_layers="L3"
                fi
            fi
        fi

        # Check cdk-nag CSV reports for L1 warnings
        local csv_warnings=""
        csv_warnings=$(parse_cdknag_csv_warnings "$stack_name" 2>&1)
        local csv_warning_count=$?
        if [ $csv_warning_count -gt 0 ]; then
            has_advisories=true
            if [ -z "$advisory_layers" ]; then
                advisory_layers="L1"
            elif [[ ! "$advisory_layers" =~ "L1" ]]; then
                advisory_layers="L1,$advisory_layers"
            fi
        fi

        # Display synthesis result with appropriate status
        if [ "$has_errors" = true ]; then
            echo -e "  ${RED}❌ Synthesis completed with errors${NC}"
            LAST_TEST_HAS_ADVISORIES=false
            return 1
        elif [ "$has_advisories" = true ]; then
            echo -e "  ${ORANGE}⚠️  Synthesis successful with advisories [$advisory_layers]${NC}"
            LAST_TEST_HAS_ADVISORIES=true
            # List advisories from the log
            echo -e "  ${YELLOW}📋 Advisories:${NC}"
            # Show cdk-nag CSV warnings first (L1)
            if [ -n "$csv_warnings" ]; then
                echo "$csv_warnings"
            fi
            # Show other advisories from synth output (cdk-nag format: [Warning at /path] RuleId: message)
            grep -E "\[(Warning|Info) at.*\] (AwsSolutions-|NIST|HIPAA|PCI)" "$synth_error" "$synth_output" 2>/dev/null | head -5 | sed 's/^/     /'
        else
            echo -e "  ${GREEN}✅ Synthesis successful${NC}"
            LAST_TEST_HAS_ADVISORIES=false
        fi

        # Check for Route53 records in template
        if grep -q "AWS::Route53::RecordSet" "$template_file" 2>/dev/null; then
            echo -e "  ${GREEN}✅ Route53 records found${NC}"
        else
            echo -e "  ${RED}❌ No Route53 records found${NC}"
        fi

        # Check for security groups
        local sg_count=$(grep -c "AWS::EC2::SecurityGroup" "$template_file" 2>/dev/null || echo "0")
        echo "  🔒 Security Groups: $sg_count"

        # Check for IAM roles
        local iam_count=$(grep -c "AWS::IAM::Role" "$template_file" 2>/dev/null || echo "0")
        echo "  👤 IAM Roles: $iam_count"

        # Check for load balancer
        if grep -q "AWS::ElasticLoadBalancingV2::LoadBalancer" "$template_file" 2>/dev/null; then
            echo -e "  ${GREEN}✅ Load Balancer found${NC}"
        else
            echo -e "  ${RED}❌ No Load Balancer found${NC}"
        fi

        return 0
    else
        echo -e "  ${RED}❌ Synthesis failed - no template generated${NC}"
        echo "  📄 Error log: $synth_error"

        # Show first 10 lines of error for debugging
        if [ -f "$synth_error" ] && [ -s "$synth_error" ]; then
            echo "  📋 Error details (first 10 lines):"
            head -10 "$synth_error" | sed 's/^/     /'
        fi

        return 1
    fi
}

# Function to analyze results
analyze_results() {
    echo ""
    echo -e "${BLUE}📊 Analysis Results${NC}"
    echo -e "${BLUE}==================${NC}"
    
    local total_tests=0
    local successful_tests=0
    local failed_tests=0
    
    # Count tests
    for runtime in "EC2" "FARGATE"; do
        for security_profile in "DEV" "STAGING" "PRODUCTION"; do
            for subdomain in "ec1" "ec2" "ec3" "fc1" "fc2" "fc3"; do
                total_tests=$((total_tests + 1))
                local stack_name="$(echo $runtime | tr '[:upper:]' '[:lower:]')-$(echo $security_profile | tr '[:upper:]' '[:lower:]')-${subdomain}"
                local template_file="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.json"
                
                if [ -f "$template_file" ]; then
                    successful_tests=$((successful_tests + 1))
                else
                    failed_tests=$((failed_tests + 1))
                fi
            done
        done
    done
    
    echo "Total Tests: $total_tests"
    echo -e "Successful: ${GREEN}$successful_tests${NC}"
    echo -e "Failed: ${RED}$failed_tests${NC}"
    
    # Check for Route53 consistency
    echo ""
    echo -e "${BLUE}🌐 Route53 Record Analysis${NC}"
    echo -e "${BLUE}=========================${NC}"
    
    for runtime in "EC2" "FARGATE"; do
        for security_profile in "DEV" "STAGING" "PRODUCTION"; do
            for subdomain in "ec1" "ec2" "ec3" "fc1" "fc2" "fc3"; do
                local template_file="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.json"
                if [ -f "$template_file" ]; then
                    local route53_count=$(grep -c "AWS::Route53::RecordSet" "$template_file" 2>/dev/null || echo "0")
                    echo "$runtime-$security_profile-$subdomain: $route53_count Route53 records"
                fi
            done
        done
    done
    
    # Check for security group consistency
    echo ""
    echo -e "${BLUE}🔒 Security Group Analysis${NC}"
    echo -e "${BLUE}===========================${NC}"
    
    for runtime in "EC2" "FARGATE"; do
        for security_profile in "DEV" "STAGING" "PRODUCTION"; do
            for subdomain in "ec1" "ec2" "ec3" "fc1" "fc2" "fc3"; do
                local template_file="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.json"
                if [ -f "$template_file" ]; then
                    local sg_count=$(grep -c "AWS::EC2::SecurityGroup" "$template_file" 2>/dev/null || echo "0")
                    echo "$runtime-$security_profile-$subdomain: $sg_count Security Groups"
                fi
            done
        done
    done
}

# Main execution
echo "Starting comprehensive synthesis tests..."
echo ""

# Test EC2 runtime across all security profiles
echo -e "${BLUE}🖥️  Testing EC2 Runtime${NC}"
echo -e "${BLUE}======================${NC}"

for security_profile in "DEV" "STAGING" "PRODUCTION"; do
    for subdomain in "ec1" "ec2" "ec3"; do
        stack_name="ec2-$(echo $security_profile | tr '[:upper:]' '[:lower:]')-${subdomain}"
        LAST_TEST_HAS_ADVISORIES=false
        if ! run_synthesis "EC2" "$security_profile" "$subdomain" "$stack_name"; then
            OVERALL_SUCCESS=false
            FAILED_TESTS+=("EC2-$security_profile-$subdomain")
        elif [ "$LAST_TEST_HAS_ADVISORIES" = true ]; then
            ADVISORY_TESTS+=("EC2-$security_profile-$subdomain")
        fi
        echo ""
    done
done

# Test Fargate runtime across all security profiles
echo -e "${BLUE}🐳 Testing Fargate Runtime${NC}"
echo -e "${BLUE}=========================${NC}"

for security_profile in "DEV" "STAGING" "PRODUCTION"; do
    for subdomain in "fc1" "fc2" "fc3"; do
        stack_name="fargate-$(echo $security_profile | tr '[:upper:]' '[:lower:]')-${subdomain}"
        LAST_TEST_HAS_ADVISORIES=false
        if ! run_synthesis "FARGATE" "$security_profile" "$subdomain" "$stack_name"; then
            OVERALL_SUCCESS=false
            FAILED_TESTS+=("FARGATE-$security_profile-$subdomain")
        elif [ "$LAST_TEST_HAS_ADVISORIES" = true ]; then
            ADVISORY_TESTS+=("FARGATE-$security_profile-$subdomain")
        fi
        echo ""
    done
done

# Analyze results
analyze_results

echo ""

# Report failed tests
if [ ${#FAILED_TESTS[@]} -gt 0 ]; then
    echo -e "${RED}❌ Failed Tests (${#FAILED_TESTS[@]}):${NC}"
    for test in "${FAILED_TESTS[@]}"; do
        echo -e "  ${RED}- $test${NC}"
    done
    echo ""
fi

# Report advisory tests (passed with warnings)
if [ ${#ADVISORY_TESTS[@]} -gt 0 ]; then
    echo -e "${ORANGE}⚠️  Tests with Advisories (${#ADVISORY_TESTS[@]}):${NC}"
    for test in "${ADVISORY_TESTS[@]}"; do
        echo -e "  ${ORANGE}- $test${NC}"
    done
    echo ""
fi

# Determine overall status message
if [ "$OVERALL_SUCCESS" = true ] && [ ${#ADVISORY_TESTS[@]} -eq 0 ]; then
    echo -e "${GREEN}🎉 Comprehensive synthesis test completed successfully!${NC}"
elif [ "$OVERALL_SUCCESS" = true ] && [ ${#ADVISORY_TESTS[@]} -gt 0 ]; then
    echo -e "${ORANGE}✅ Comprehensive synthesis test passed with advisories${NC}"
else
    echo -e "${RED}❌ Comprehensive synthesis test completed with failures${NC}"
fi
echo "Results saved in: $RESULTS_DIR"
echo ""
echo -e "${YELLOW}📋 Next Steps:${NC}"
echo "1. Review synthesis logs for any errors"
echo "2. Compare templates for inconsistencies"
echo "3. Check Route53 record creation patterns"
echo "4. Verify security group configurations"
echo "5. Test actual deployments for working combinations"

# Generate HTML report with links to templates
generate_html_report() {
    local html_file="$RESULTS_DIR/comprehensive-synth-report.html"
    local validation_dir="$SCRIPT_DIR/validation-results"
    mkdir -p "$validation_dir"

    # Count results
    local total_tests=0
    local successful_tests=0
    local failed_tests_count=0
    local advisory_tests_count=${#ADVISORY_TESTS[@]}

    # Convert JSON templates to YAML for better readability
    for template in "$RESULTS_DIR"/*-template.json; do
        if [ -f "$template" ]; then
            local yaml_file="${template%.json}.yaml"
            if command -v python3 &> /dev/null; then
                python3 -c "import json, yaml, sys; yaml.dump(json.load(open('$template')), open('$yaml_file', 'w'), default_flow_style=False, sort_keys=False)" 2>/dev/null || true
            fi
        fi
    done

    cat > "$html_file" << 'HTMLHEAD'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CloudForge Comprehensive Synth Report</title>
    <style>
        :root {
            --bg-primary: #0d1117;
            --bg-secondary: #161b22;
            --bg-tertiary: #21262d;
            --text-primary: #c9d1d9;
            --text-secondary: #8b949e;
            --border-color: #30363d;
            --success-color: #3fb950;
            --warning-color: #d29922;
            --error-color: #f85149;
            --info-color: #58a6ff;
        }
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
            background: var(--bg-primary);
            color: var(--text-primary);
            line-height: 1.6;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        .header {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 24px;
            margin-bottom: 24px;
        }
        h1 { color: var(--text-primary); font-size: 24px; margin-bottom: 8px; }
        .subtitle { color: var(--text-secondary); font-size: 14px; }
        .stats {
            display: flex;
            gap: 16px;
            margin-top: 16px;
            flex-wrap: wrap;
        }
        .stat {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 12px 20px;
            text-align: center;
        }
        .stat-value { font-size: 24px; font-weight: 600; }
        .stat-label { font-size: 12px; color: var(--text-secondary); }
        .stat.success .stat-value { color: var(--success-color); }
        .stat.warning .stat-value { color: var(--warning-color); }
        .stat.error .stat-value { color: var(--error-color); }
        .section {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            margin-bottom: 24px;
            overflow: hidden;
        }
        .section-header {
            background: var(--bg-tertiary);
            padding: 16px 20px;
            border-bottom: 1px solid var(--border-color);
            font-weight: 600;
        }
        .test-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
            gap: 16px;
            padding: 20px;
        }
        .test-card {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 16px;
        }
        .test-card.success { border-left: 3px solid var(--success-color); }
        .test-card.warning { border-left: 3px solid var(--warning-color); }
        .test-card.error { border-left: 3px solid var(--error-color); }
        .test-name { font-weight: 600; margin-bottom: 8px; }
        .test-details { font-size: 13px; color: var(--text-secondary); }
        .test-links { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
        .test-links a {
            color: var(--info-color);
            text-decoration: none;
            font-size: 12px;
            padding: 4px 8px;
            background: rgba(88, 166, 255, 0.1);
            border-radius: 4px;
        }
        .test-links a:hover { background: rgba(88, 166, 255, 0.2); }
        .badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 11px;
            font-weight: 500;
        }
        .badge.success { background: rgba(63, 185, 80, 0.2); color: var(--success-color); }
        .badge.warning { background: rgba(210, 153, 34, 0.2); color: var(--warning-color); }
        .badge.error { background: rgba(248, 81, 73, 0.2); color: var(--error-color); }
        pre {
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 16px;
            overflow-x: auto;
            font-size: 12px;
            max-height: 600px;
        }
        .template-viewer {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.8);
            z-index: 1000;
            padding: 20px;
        }
        .template-viewer.active { display: flex; flex-direction: column; }
        .template-viewer-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 16px;
            background: var(--bg-secondary);
            border-radius: 8px 8px 0 0;
        }
        .template-viewer-content {
            flex: 1;
            background: var(--bg-secondary);
            border-radius: 0 0 8px 8px;
            overflow: auto;
            padding: 16px;
        }
        .close-btn {
            background: var(--error-color);
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 4px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>☁️ CloudForge Comprehensive Synth Report</h1>
            <div class="subtitle">Generated: TIMESTAMP_PLACEHOLDER</div>
            <div class="stats">
                <div class="stat success">
                    <div class="stat-value">SUCCESS_COUNT</div>
                    <div class="stat-label">Successful</div>
                </div>
                <div class="stat warning">
                    <div class="stat-value">ADVISORY_COUNT</div>
                    <div class="stat-label">With Advisories</div>
                </div>
                <div class="stat error">
                    <div class="stat-value">FAILED_COUNT</div>
                    <div class="stat-label">Failed</div>
                </div>
                <div class="stat">
                    <div class="stat-value">TOTAL_COUNT</div>
                    <div class="stat-label">Total Tests</div>
                </div>
            </div>
        </div>
HTMLHEAD

    # Generate test cards for each runtime
    for runtime in "EC2" "FARGATE"; do
        local runtime_icon="🖥️"
        if [ "$runtime" = "FARGATE" ]; then
            runtime_icon="🐳"
        fi

        cat >> "$html_file" << EOF
        <div class="section">
            <div class="section-header">$runtime_icon $runtime Runtime Tests</div>
            <div class="test-grid">
EOF

        for security_profile in "DEV" "STAGING" "PRODUCTION"; do
            for subdomain in "ec1" "ec2" "ec3" "fc1" "fc2" "fc3"; do
                # Filter subdomains by runtime
                if [[ "$runtime" == "EC2" && ! "$subdomain" =~ ^ec ]]; then
                    continue
                fi
                if [[ "$runtime" == "FARGATE" && ! "$subdomain" =~ ^fc ]]; then
                    continue
                fi

                local test_name="${runtime}-${security_profile}-${subdomain}"
                local template_json="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.json"
                local template_yaml="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.yaml"
                local synth_log="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-synth.log"
                local error_log="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-error.log"

                total_tests=$((total_tests + 1))

                local status="error"
                local status_badge="Failed"
                local has_template="false"
                local resource_count="0"
                local sg_count="0"
                local iam_count="0"

                if [ -f "$template_json" ]; then
                    has_template="true"
                    successful_tests=$((successful_tests + 1))
                    status="success"
                    status_badge="Passed"
                    resource_count=$(grep -c '"Type":' "$template_json" 2>/dev/null || echo "0")
                    sg_count=$(grep -c '"AWS::EC2::SecurityGroup"' "$template_json" 2>/dev/null || echo "0")
                    iam_count=$(grep -c '"AWS::IAM::Role"' "$template_json" 2>/dev/null || echo "0")

                    # Check if this test has advisories
                    for advisory_test in "${ADVISORY_TESTS[@]}"; do
                        if [ "$advisory_test" = "$test_name" ]; then
                            status="warning"
                            status_badge="Advisories"
                            break
                        fi
                    done
                else
                    failed_tests_count=$((failed_tests_count + 1))
                fi

                # Build links
                local links=""
                if [ "$has_template" = "true" ]; then
                    local json_filename="${runtime}-${security_profile}-${subdomain}-template.json"
                    local yaml_filename="${runtime}-${security_profile}-${subdomain}-template.yaml"
                    links="<a href=\"../synth-results/$json_filename\" target=\"_blank\">📄 JSON</a>"
                    if [ -f "$template_yaml" ]; then
                        links="$links <a href=\"../synth-results/$yaml_filename\" target=\"_blank\">📜 YAML</a>"
                    fi
                fi
                local log_filename="${runtime}-${security_profile}-${subdomain}-synth.log"
                local err_filename="${runtime}-${security_profile}-${subdomain}-error.log"
                if [ -f "$synth_log" ]; then
                    links="$links <a href=\"../synth-results/$log_filename\" target=\"_blank\">📋 Log</a>"
                fi
                if [ -f "$error_log" ] && [ -s "$error_log" ]; then
                    links="$links <a href=\"../synth-results/$err_filename\" target=\"_blank\">⚠️ Errors</a>"
                fi

                cat >> "$html_file" << EOF
                <div class="test-card $status">
                    <div class="test-name">
                        $test_name
                        <span class="badge $status">$status_badge</span>
                    </div>
                    <div class="test-details">
                        Security Profile: <strong>$security_profile</strong><br>
                        Resources: $resource_count | Security Groups: $sg_count | IAM Roles: $iam_count
                    </div>
                    <div class="test-links">$links</div>
                </div>
EOF
            done
        done

        cat >> "$html_file" << 'EOF'
            </div>
        </div>
EOF
    done

    # Close HTML
    cat >> "$html_file" << 'EOF'
    </div>
    <div class="template-viewer" id="templateViewer">
        <div class="template-viewer-header">
            <span id="viewerTitle">Template Viewer</span>
            <button class="close-btn" onclick="closeViewer()">Close</button>
        </div>
        <div class="template-viewer-content">
            <pre id="viewerContent"></pre>
        </div>
    </div>
    <script>
        function viewTemplate(path, title) {
            fetch(path)
                .then(r => r.text())
                .then(content => {
                    document.getElementById('viewerTitle').textContent = title;
                    document.getElementById('viewerContent').textContent = content;
                    document.getElementById('templateViewer').classList.add('active');
                });
        }
        function closeViewer() {
            document.getElementById('templateViewer').classList.remove('active');
        }
        document.addEventListener('keydown', e => { if (e.key === 'Escape') closeViewer(); });
    </script>
</body>
</html>
EOF

    # Replace placeholders
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local clean_successful=$((successful_tests - advisory_tests_count))

    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s/TIMESTAMP_PLACEHOLDER/$timestamp/g" "$html_file"
        sed -i '' "s/SUCCESS_COUNT/$clean_successful/g" "$html_file"
        sed -i '' "s/ADVISORY_COUNT/$advisory_tests_count/g" "$html_file"
        sed -i '' "s/FAILED_COUNT/$failed_tests_count/g" "$html_file"
        sed -i '' "s/TOTAL_COUNT/$total_tests/g" "$html_file"
    else
        sed -i "s/TIMESTAMP_PLACEHOLDER/$timestamp/g" "$html_file"
        sed -i "s/SUCCESS_COUNT/$clean_successful/g" "$html_file"
        sed -i "s/ADVISORY_COUNT/$advisory_tests_count/g" "$html_file"
        sed -i "s/FAILED_COUNT/$failed_tests_count/g" "$html_file"
        sed -i "s/TOTAL_COUNT/$total_tests/g" "$html_file"
    fi

    # Copy report to validation-results for consistency
    cp "$html_file" "$validation_dir/comprehensive-synth-report.html"

    echo -e "${GREEN}📊 HTML Report generated: $html_file${NC}"
}

# Start HTTP server to browse results
start_http_server() {
    local port="${1:-8888}"
    local server_dir="$SCRIPT_DIR"

    echo ""
    echo -e "${BLUE}🌐 Starting HTTP server on port $port...${NC}"
    echo -e "   ${GREEN}Synth Report: http://localhost:$port/validation-results/comprehensive-synth-report.html${NC}"
    echo -e "   ${GREEN}Template Files: http://localhost:$port/synth-results/${NC}"
    echo ""
    echo -e "${YELLOW}Press Ctrl+C to stop the server${NC}"

    cd "$server_dir"
    python3 -m http.server "$port" 2>/dev/null || python -m SimpleHTTPServer "$port" 2>/dev/null
}

# Generate the HTML report
generate_html_report

# Start HTTP server if requested
if [[ "${1:-}" == "--serve" ]] || [[ "${2:-}" == "--serve" ]]; then
    start_http_server "${HTTP_PORT:-8888}"
fi

# Exit with error if any tests failed (advisories don't cause failure)
if [ "$OVERALL_SUCCESS" = false ]; then
    exit 1
fi
