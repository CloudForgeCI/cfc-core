#!/usr/bin/env bash

# Comprehensive Synthesis Test for All Security Profiles
# Tests EC2 and Fargate runtimes across DEV, STAGING, PRODUCTION security profiles

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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
    # Add timeout to prevent hanging (5 minutes max)
    timeout 300 cdk synth --quiet --context cfc=@deployment-context.json > "$synth_output" 2> "$synth_error"
    local synth_exit_code=$?

    # Check if timeout occurred
    if [ $synth_exit_code -eq 124 ]; then
        echo -e "  ${RED}❌ Synthesis timed out after 5 minutes${NC}"
        echo "Synthesis timed out" > "$synth_error"
        return 1
    fi

    # Check if synthesis succeeded by looking for the generated template file
    local template_file="$RESULTS_DIR/${runtime}-${security_profile}-${subdomain}-template.json"
    if [ -f "$CDK_OUT_DIR/$stack_name.template.json" ]; then
        echo -e "  ${GREEN}✅ Synthesis successful${NC}"
        cp "$CDK_OUT_DIR/$stack_name.template.json" "$template_file"
        echo "  📄 Template saved: $template_file"

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
        run_synthesis "EC2" "$security_profile" "$subdomain" "$stack_name"
        echo ""
    done
done

# Test Fargate runtime across all security profiles
echo -e "${BLUE}🐳 Testing Fargate Runtime${NC}"
echo -e "${BLUE}=========================${NC}"

for security_profile in "DEV" "STAGING" "PRODUCTION"; do
    for subdomain in "fc1" "fc2" "fc3"; do
        stack_name="fargate-$(echo $security_profile | tr '[:upper:]' '[:lower:]')-${subdomain}"
        run_synthesis "FARGATE" "$security_profile" "$subdomain" "$stack_name"
        echo ""
    done
done

# Analyze results
analyze_results

echo ""
echo -e "${GREEN}🎉 Comprehensive synthesis test completed!${NC}"
echo "Results saved in: $RESULTS_DIR"
echo ""
echo -e "${YELLOW}📋 Next Steps:${NC}"
echo "1. Review synthesis logs for any errors"
echo "2. Compare templates for inconsistencies"
echo "3. Check Route53 record creation patterns"
echo "4. Verify security group configurations"
echo "5. Test actual deployments for working combinations"
