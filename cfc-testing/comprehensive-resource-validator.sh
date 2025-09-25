#!/bin/bash

# Comprehensive Resource Validation System
# Creates a truth table of expected resources for every configuration combination
# and validates actual synthesized resources against expectations

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
DOMAIN="cloudforgeci.com"
BASE_DIR="/Users/phillip/projects/cfc-core/cfc-testing"
VALIDATION_DIR="$BASE_DIR/validation-results"
TRUTH_TABLE_FILE="$VALIDATION_DIR/truth-table.json"
DRIFT_REPORT_FILE="$VALIDATION_DIR/drift-report.txt"
CDK_OUT_DIR="$BASE_DIR/cdk.out"

# Create directories
mkdir -p "$VALIDATION_DIR"

echo -e "${BLUE}🔍 Comprehensive Resource Validation System${NC}"
echo -e "${BLUE}===========================================${NC}"
echo "Domain: $DOMAIN"
echo "Validation Directory: $VALIDATION_DIR"
echo ""

# Configuration matrix
RUNTIMES=("EC2" "FARGATE")
TOPOLOGIES=("JENKINS_SINGLE_NODE" "JENKINS_SERVICE")
SECURITY_PROFILES=("DEV" "STAGING" "PRODUCTION")
DOMAIN_CONFIGS=("with-domain" "no-domain")
SSL_CONFIGS=("ssl-enabled" "ssl-disabled")
SUBDOMAIN_CONFIGS=("with-subdomain" "no-subdomain")

# Expected resources truth table
declare -A EXPECTED_RESOURCES

# Function to initialize expected resources truth table
initialize_truth_table() {
    echo -e "${CYAN}📋 Initializing truth table...${NC}"
    
    # Base resources that should ALWAYS exist
    local base_resources="VPC,Subnets,SecurityGroups,IAMRoles,CloudWatchLogs"
    
    for runtime in "${RUNTIMES[@]}"; do
        for topology in "${TOPOLOGIES[@]}"; do
            for security_profile in "${SECURITY_PROFILES[@]}"; do
                for domain_config in "${DOMAIN_CONFIGS[@]}"; do
                    for ssl_config in "${SSL_CONFIGS[@]}"; do
                        for subdomain_config in "${SUBDOMAIN_CONFIGS[@]}"; do
                            local key="${runtime}_${topology}_${security_profile}_${domain_config}_${ssl_config}_${subdomain_config}"
                            
                            # Start with base resources
                            local expected="$base_resources"
                            
                            # Add runtime-specific resources
                            if [[ "$runtime" == "FARGATE" ]]; then
                                expected+=",ECSCluster,ECSService,FargateTaskDefinition"
                            else
                                expected+=",EC2Instances"
                                if [[ "$topology" == "JENKINS_SERVICE" ]]; then
                                    expected+=",AutoScalingGroup"
                                fi
                            fi
                            
                            # Add topology-specific resources
                            if [[ "$topology" == "JENKINS_SERVICE" ]]; then
                                expected+=",ApplicationLoadBalancer,TargetGroups"
                            fi
                            
                            # Add EFS for both runtimes (Jenkins persistent storage)
                            expected+=",EFSFileSystem,EFSAccessPoint"
                            
                            # Add domain-specific resources
                            if [[ "$domain_config" == "with-domain" ]]; then
                                expected+=",Route53HostedZone,Route53Records"
                                
                                # Add SSL-specific resources
                                if [[ "$ssl_config" == "ssl-enabled" ]]; then
                                    expected+=",ACMCertificate,HTTPSListener,HTTPRedirect"
                                else
                                    expected+=",HTTPListener"
                                fi
                            else
                                # No domain means only HTTP listener (if ALB exists)
                                if [[ "$topology" == "JENKINS_SERVICE" ]]; then
                                    expected+=",HTTPListener"
                                fi
                            fi
                            
                            # Add security profile-specific resources
                            case "$security_profile" in
                                "DEV")
                                    # Basic security, minimal monitoring
                                    ;;
                                "STAGING")
                                    expected+=",CloudTrail,ConfigRules"
                                    ;;
                                "PRODUCTION")
                                    expected+=",WAFWebACL,CloudTrail,ConfigRules,AutoScaling"
                                    ;;
                            esac
                            
                            # Skip invalid combinations
                            if [[ "$ssl_config" == "ssl-enabled" && "$domain_config" == "no-domain" ]]; then
                                expected="INVALID_COMBINATION"
                            fi
                            
                            if [[ "$subdomain_config" == "with-subdomain" && "$domain_config" == "no-domain" ]]; then
                                expected="INVALID_COMBINATION"
                            fi
                            
                            EXPECTED_RESOURCES[$key]="$expected"
                        done
                    done
                done
            done
        done
    done
}

# Function to create deployment context for testing
create_deployment_context() {
    local runtime=$1
    local topology=$2
    local security_profile=$3
    local domain_config=$4
    local ssl_config=$5
    local subdomain_config=$6
    local stack_name=$7
    
    local domain_value=""
    local subdomain_value=""
    local ssl_value="false"
    
    if [[ "$domain_config" == "with-domain" ]]; then
        domain_value="$DOMAIN"
        if [[ "$subdomain_config" == "with-subdomain" ]]; then
            subdomain_value="test-$(echo $stack_name | tr '[:upper:]' '[:lower:]')"
        fi
        if [[ "$ssl_config" == "ssl-enabled" ]]; then
            ssl_value="true"
        fi
    fi
    
    cat > "$BASE_DIR/deployment-context.json" << EOF
{
  "stackName": "$stack_name",
  "context": {
    "healthCheckTimeout": "5",
    "memory": "2048",
    "enableMonitoring": "true",
    "stackName": "$stack_name",
    "healthCheckInterval": "30",
    "enableSsl": "$ssl_value",
    "tier": "public",
    "wafEnabled": "false",
    "securityProfile": "$security_profile",
    "cloudfrontEnabled": "false",
    "healthCheckGracePeriod": "300",
    "unhealthyThreshold": "3",
    "healthyThreshold": "2",
    "networkMode": "public-no-nat",
    "topology": "$topology",
    "instanceType": "t3.micro",
    "minInstanceCapacity": "1",
    "runtime": "$runtime",
    "cpu": "1024",
    "cpuTargetUtilization": "60",
    "enableAutoScaling": "true",
    "env": "dev",
    "maxInstanceCapacity": "3",
    "authMode": "none",
    "domain": "$domain_value",
    "subdomain": "$subdomain_value",
    "logRetentionDays": "7",
    "region": "us-east-1",
    "enableEncryption": "true"
  }
}
EOF
}

# Function to synthesize and validate a configuration
synthesize_and_validate() {
    local runtime=$1
    local topology=$2
    local security_profile=$3
    local domain_config=$4
    local ssl_config=$5
    local subdomain_config=$6
    
    local key="${runtime}_${topology}_${security_profile}_${domain_config}_${ssl_config}_${subdomain_config}"
    local expected="${EXPECTED_RESOURCES[$key]}"
    
    # Skip invalid combinations
    if [[ "$expected" == "INVALID_COMBINATION" ]]; then
        echo -e "${YELLOW}⚠️  Skipping invalid combination: $key${NC}"
        return 0
    fi
    
    local stack_name="val-$(echo $key | tr '[:upper:]' '[:lower:]' | tr '_' '-')"
    
    echo -e "${PURPLE}🧪 Testing: $key${NC}"
    echo "  Stack: $stack_name"
    echo "  Expected: $expected"
    
    # Create deployment context
    create_deployment_context "$runtime" "$topology" "$security_profile" "$domain_config" "$ssl_config" "$subdomain_config" "$stack_name"
    
    # Clean previous CDK output
    rm -rf "$CDK_OUT_DIR"
    
    # Run synthesis
    local synth_output="$VALIDATION_DIR/${key}-synth.log"
    local synth_error="$VALIDATION_DIR/${key}-error.log"
    local template_file="$CDK_OUT_DIR/$stack_name.template.json"
    
    cd "$BASE_DIR"
    
    if cdk synth --quiet \
        --context cfc.runtime="$runtime" \
        --context cfc.topology="$topology" \
        --context cfc.securityProfile="$security_profile" \
        --context cfc.stackName="$stack_name" \
        > "$synth_output" 2> "$synth_error"; then
        
        echo -e "  ${GREEN}✅ Synthesis successful${NC}"
        
        # Validate resources
        if [ -f "$template_file" ]; then
            validate_resources "$template_file" "$expected" "$key"
        else
            echo -e "  ${RED}❌ Template file not found${NC}"
            echo "$key: SYNTHESIS_FAILED - Template not generated" >> "$DRIFT_REPORT_FILE"
        fi
        
    else
        echo -e "  ${RED}❌ Synthesis failed${NC}"
        echo "$key: SYNTHESIS_FAILED - $(head -1 $synth_error)" >> "$DRIFT_REPORT_FILE"
    fi
    
    echo ""
}

# Function to validate resources in a CloudFormation template
validate_resources() {
    local template_file=$1
    local expected_resources=$2
    local config_key=$3
    
    local validation_output="$VALIDATION_DIR/${config_key}-validation.json"
    
    echo "  🔍 Validating resources..."
    
    # Extract all resource types from template
    local actual_resources=$(jq -r '.Resources | to_entries[] | .value.Type' "$template_file" 2>/dev/null | sort | uniq | tr '\n' ',' | sed 's/,$//')
    
    # Create validation report
    cat > "$validation_output" << EOF
{
  "config": "$config_key",
  "expected": "$expected_resources",
  "actual_resource_types": "$actual_resources",
  "validation_results": {
EOF
    
    local validation_results=""
    local missing_resources=""
    local unexpected_resources=""
    local all_good=true
    
    # Check each expected resource type
    IFS=',' read -ra EXPECTED_ARRAY <<< "$expected_resources"
    for expected in "${EXPECTED_ARRAY[@]}"; do
        local found=false
        case "$expected" in
            "VPC")
                if grep -q "AWS::EC2::VPC" "$template_file"; then found=true; fi
                ;;
            "Subnets")
                if grep -q "AWS::EC2::Subnet" "$template_file"; then found=true; fi
                ;;
            "SecurityGroups")
                if grep -q "AWS::EC2::SecurityGroup" "$template_file"; then found=true; fi
                ;;
            "ApplicationLoadBalancer")
                if grep -q "AWS::ElasticLoadBalancingV2::LoadBalancer" "$template_file"; then found=true; fi
                ;;
            "TargetGroups")
                if grep -q "AWS::ElasticLoadBalancingV2::TargetGroup" "$template_file"; then found=true; fi
                ;;
            "HTTPListener")
                if grep -q "Port: 80" "$template_file" && grep -q "Protocol: HTTP" "$template_file"; then found=true; fi
                ;;
            "HTTPSListener")
                if grep -q "Port: 443" "$template_file" && grep -q "Protocol: HTTPS" "$template_file"; then found=true; fi
                ;;
            "ACMCertificate")
                if grep -q "AWS::CertificateManager::Certificate" "$template_file"; then found=true; fi
                ;;
            "Route53HostedZone")
                if grep -q "AWS::Route53::HostedZone" "$template_file"; then found=true; fi
                ;;
            "Route53Records")
                if grep -q "AWS::Route53::RecordSet" "$template_file"; then found=true; fi
                ;;
            "ECSCluster")
                if grep -q "AWS::ECS::Cluster" "$template_file"; then found=true; fi
                ;;
            "ECSService")
                if grep -q "AWS::ECS::Service" "$template_file"; then found=true; fi
                ;;
            "FargateTaskDefinition")
                if grep -q "AWS::ECS::TaskDefinition" "$template_file"; then found=true; fi
                ;;
            "EC2Instances")
                if grep -q "AWS::EC2::Instance" "$template_file"; then found=true; fi
                ;;
            "AutoScalingGroup")
                if grep -q "AWS::AutoScaling::AutoScalingGroup" "$template_file"; then found=true; fi
                ;;
            "EFSFileSystem")
                if grep -q "AWS::EFS::FileSystem" "$template_file"; then found=true; fi
                ;;
            "EFSAccessPoint")
                if grep -q "AWS::EFS::AccessPoint" "$template_file"; then found=true; fi
                ;;
            "IAMRoles")
                if grep -q "AWS::IAM::Role" "$template_file"; then found=true; fi
                ;;
            "CloudWatchLogs")
                if grep -q "AWS::Logs::LogGroup" "$template_file"; then found=true; fi
                ;;
            "WAFWebACL")
                if grep -q "AWS::WAFv2::WebACL" "$template_file"; then found=true; fi
                ;;
            "CloudTrail")
                if grep -q "AWS::CloudTrail::Trail" "$template_file"; then found=true; fi
                ;;
            "ConfigRules")
                if grep -q "AWS::Config::ConfigRule" "$template_file"; then found=true; fi
                ;;
        esac
        
        if [[ "$found" == true ]]; then
            validation_results+="\n    \"$expected\": \"FOUND\","
            echo -e "    ${GREEN}✅ $expected${NC}"
        else
            validation_results+="\n    \"$expected\": \"MISSING\","
            missing_resources+="$expected,"
            all_good=false
            echo -e "    ${RED}❌ $expected${NC}"
        fi
    done
    
    # Close validation JSON
    echo -e "$validation_results" | sed '$ s/,$//' >> "$validation_output"
    echo "" >> "$validation_output"
    echo "  }," >> "$validation_output"
    echo "  \"summary\": {" >> "$validation_output"
    echo "    \"status\": \"$(if $all_good; then echo 'PASS'; else echo 'FAIL'; fi)\"," >> "$validation_output"
    echo "    \"missing_resources\": \"${missing_resources%,}\"," >> "$validation_output"
    echo "    \"resource_count\": $(grep -c '"Type"' "$template_file" 2>/dev/null || echo 0)" >> "$validation_output"
    echo "  }" >> "$validation_output"
    echo "}" >> "$validation_output"
    
    # Add to drift report
    if ! $all_good; then
        echo "$config_key: MISSING_RESOURCES - ${missing_resources%,}" >> "$DRIFT_REPORT_FILE"
    else
        echo "$config_key: VALIDATION_PASSED" >> "$DRIFT_REPORT_FILE"
    fi
}

# Function to generate comprehensive report
generate_comprehensive_report() {
    echo -e "${BLUE}📊 Generating comprehensive validation report...${NC}"
    
    local report_file="$VALIDATION_DIR/comprehensive-validation-report.html"
    
    cat > "$report_file" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>CloudForge Core - Comprehensive Resource Validation Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f0f0; padding: 15px; border-radius: 5px; }
        .matrix { margin: 20px 0; }
        .config { margin: 10px 0; padding: 10px; border: 1px solid #ddd; border-radius: 3px; }
        .pass { background: #d4edda; border-color: #c3e6cb; }
        .fail { background: #f8d7da; border-color: #f5c6cb; }
        .invalid { background: #e2e3e5; border-color: #d6d8db; }
        .resource-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; }
        .resource-item { padding: 5px; border-radius: 3px; font-size: 12px; }
        .found { background: #d1ecf1; color: #0c5460; }
        .missing { background: #f8d7da; color: #721c24; }
        .truth-table { width: 100%; border-collapse: collapse; font-size: 11px; }
        .truth-table th, .truth-table td { border: 1px solid #ddd; padding: 4px; text-align: center; }
        .truth-table th { background: #f8f9fa; }
    </style>
</head>
<body>
    <div class="header">
        <h1>CloudForge Core - Comprehensive Resource Validation Report</h1>
        <p>Generated: $(date)</p>
        <p>Validation Directory: $VALIDATION_DIR</p>
    </div>
    
    <h2>Validation Summary</h2>
    <div id="summary">
        <!-- Summary will be populated -->
    </div>
    
    <h2>Configuration Matrix Results</h2>
    <div class="matrix" id="matrix">
        <!-- Matrix will be populated -->
    </div>
    
    <h2>Truth Table</h2>
    <table class="truth-table">
        <thead>
            <tr>
                <th>Runtime</th>
                <th>Topology</th>
                <th>Security</th>
                <th>Domain</th>
                <th>SSL</th>
                <th>Subdomain</th>
                <th>Expected Resources</th>
                <th>Status</th>
            </tr>
        </thead>
        <tbody id="truth-table-body">
            <!-- Truth table will be populated -->
        </tbody>
    </table>
    
    <h2>Detailed Validation Results</h2>
    <div id="detailed-results">
        <!-- Detailed results will be populated -->
    </div>
</body>
</html>
EOF
    
    echo -e "${GREEN}📋 Report generated: $report_file${NC}"
}

# Main execution
main() {
    echo -e "${CYAN}🚀 Starting comprehensive resource validation...${NC}"
    
    # Initialize truth table
    initialize_truth_table
    
    # Clear previous drift report
    > "$DRIFT_REPORT_FILE"
    
    local total_configs=0
    local passed_configs=0
    local failed_configs=0
    local invalid_configs=0
    
    # Test all combinations
    for runtime in "${RUNTIMES[@]}"; do
        for topology in "${TOPOLOGIES[@]}"; do
            for security_profile in "${SECURITY_PROFILES[@]}"; do
                for domain_config in "${DOMAIN_CONFIGS[@]}"; do
                    for ssl_config in "${SSL_CONFIGS[@]}"; do
                        for subdomain_config in "${SUBDOMAIN_CONFIGS[@]}"; do
                            local key="${runtime}_${topology}_${security_profile}_${domain_config}_${ssl_config}_${subdomain_config}"
                            local expected="${EXPECTED_RESOURCES[$key]}"
                            
                            total_configs=$((total_configs + 1))
                            
                            if [[ "$expected" == "INVALID_COMBINATION" ]]; then
                                invalid_configs=$((invalid_configs + 1))
                            else
                                synthesize_and_validate "$runtime" "$topology" "$security_profile" "$domain_config" "$ssl_config" "$subdomain_config"
                                
                                # Check if validation passed (simple check)
                                if grep -q "${key}: VALIDATION_PASSED" "$DRIFT_REPORT_FILE" 2>/dev/null; then
                                    passed_configs=$((passed_configs + 1))
                                else
                                    failed_configs=$((failed_configs + 1))
                                fi
                            fi
                        done
                    done
                done
            done
        done
    done
    
    # Generate reports
    generate_comprehensive_report
    
    # Print summary
    echo -e "${BLUE}📈 Validation Summary${NC}"
    echo -e "${BLUE}====================${NC}"
    echo "Total Configurations: $total_configs"
    echo "Valid Configurations: $((total_configs - invalid_configs))"
    echo "Invalid Combinations: $invalid_configs"
    echo "Passed Validations: $passed_configs"
    echo "Failed Validations: $failed_configs"
    echo "Success Rate: $(( (passed_configs * 100) / (total_configs - invalid_configs) ))%"
    echo ""
    echo -e "${GREEN}✅ Comprehensive validation completed!${NC}"
    echo "Results saved in: $VALIDATION_DIR"
    echo "Drift report: $DRIFT_REPORT_FILE"
    echo ""
    
    if [[ $failed_configs -gt 0 ]]; then
        echo -e "${RED}⚠️  Failures detected. Check drift report for details.${NC}"
        echo "Failed configurations:"
        grep "MISSING_RESOURCES\|SYNTHESIS_FAILED" "$DRIFT_REPORT_FILE" | head -10
    else
        echo -e "${GREEN}🎉 All validations passed!${NC}"
    fi
}

# Run main function
main "$@"
