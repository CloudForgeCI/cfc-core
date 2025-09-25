#!/bin/bash

# Master Validation System for CloudForge Core
# Orchestrates comprehensive resource validation, truth table generation, and drift detection

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
BASE_DIR="/Users/phillip/projects/cfc-core/cfc-testing"
VALIDATION_DIR="$BASE_DIR/validation-results"

echo -e "${BLUE}🚀 CloudForge Core - Master Validation System${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

# Function to check prerequisites
check_prerequisites() {
    echo -e "${CYAN}🔍 Checking prerequisites...${NC}"
    
    local missing_deps=()
    
    # Check required tools
    if ! command -v jq &> /dev/null; then
        missing_deps+=("jq")
    fi
    
    if ! command -v python3 &> /dev/null; then
        missing_deps+=("python3")
    fi
    
    if ! command -v cdk &> /dev/null; then
        missing_deps+=("aws-cdk")
    fi
    
    if ! command -v mvn &> /dev/null; then
        missing_deps+=("maven")
    fi
    
    # Check required scripts
    local required_scripts=(
        "comprehensive-resource-validator.sh"
        "truth-table-generator.py"
        "drift-detector.sh"
    )
    
    for script in "${required_scripts[@]}"; do
        if [[ ! -f "$BASE_DIR/$script" ]]; then
            missing_deps+=("$script")
        fi
    done
    
    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        echo -e "${RED}❌ Missing dependencies:${NC}"
        printf '%s\n' "${missing_deps[@]}"
        echo ""
        echo "Please install missing dependencies and ensure all scripts are present."
        exit 1
    fi
    
    echo -e "${GREEN}✅ All prerequisites met${NC}"
}

# Function to run full validation suite
run_full_validation() {
    echo -e "${PURPLE}🧪 Running comprehensive validation suite...${NC}"
    echo ""
    
    # Step 1: Generate truth table
    echo -e "${CYAN}📋 Step 1: Generating truth table and test matrix...${NC}"
    cd "$BASE_DIR"
    python3 truth-table-generator.py "$VALIDATION_DIR"
    echo ""
    
    # Step 2: Run comprehensive resource validation
    echo -e "${CYAN}🔍 Step 2: Running comprehensive resource validation...${NC}"
    bash comprehensive-resource-validator.sh
    echo ""
    
    # Step 3: Move results to current directory for drift detection
    echo -e "${CYAN}📁 Step 3: Organizing validation results...${NC}"
    mkdir -p "$VALIDATION_DIR/current"
    
    # Move validation JSON files to current directory
    if ls "$VALIDATION_DIR"/*-validation.json 1> /dev/null 2>&1; then
        mv "$VALIDATION_DIR"/*-validation.json "$VALIDATION_DIR/current/"
        echo "Moved validation results to current directory"
    fi
    
    echo -e "${GREEN}✅ Comprehensive validation completed${NC}"
}

# Function to detect and report drift
detect_and_report_drift() {
    echo -e "${PURPLE}🔍 Detecting configuration drift...${NC}"
    echo ""
    
    # Run drift detection
    bash "$BASE_DIR/drift-detector.sh" detect
    
    local drift_status=$?
    echo ""
    
    if [[ $drift_status -eq 0 ]]; then
        echo -e "${GREEN}🎉 No configuration drift detected${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Configuration drift detected ($drift_status changes)${NC}"
        
        # Generate drift history
        bash "$BASE_DIR/drift-detector.sh" history
        
        return $drift_status
    fi
}

# Function to create testing strategy
create_testing_strategy() {
    local target_files="$1"
    
    echo -e "${PURPLE}🎯 Creating targeted testing strategy...${NC}"
    echo ""
    
    if [[ -f "$VALIDATION_DIR/truth-table.json" ]]; then
        echo "Analyzing which configurations are affected by file changes..."
        
        # Parse truth table to find affected configurations
        local affected_configs=$(python3 -c "
import json
import sys

try:
    with open('$VALIDATION_DIR/truth-table.json', 'r') as f:
        data = json.load(f)
    
    test_matrix = data.get('test_matrix', {})
    target_files = '$target_files'.split(',') if '$target_files' else []
    
    affected = set()
    for file_name in target_files:
        file_name = file_name.strip()
        if file_name in test_matrix:
            affected.update(test_matrix[file_name])
    
    print(f'Configurations affected by changes: {len(affected)}')
    if affected:
        print('Recommended test configurations:')
        for config in sorted(list(affected))[:10]:  # Show first 10
            print(f'  - {config}')
        if len(affected) > 10:
            print(f'  ... and {len(affected) - 10} more')
    
except Exception as e:
    print(f'Error analyzing truth table: {e}')
")
        
        echo "$affected_configs"
    else
        echo -e "${YELLOW}⚠️  Truth table not found. Run full validation first.${NC}"
    fi
}

# Function to run smoke tests
run_smoke_tests() {
    echo -e "${PURPLE}💨 Running smoke tests...${NC}"
    echo ""
    
    # Define minimal test configurations
    local smoke_configs=(
        "FARGATE,JENKINS_SERVICE,DEV,with-domain,ssl-enabled,with-subdomain"
        "EC2,JENKINS_SINGLE_NODE,DEV,no-domain,ssl-disabled,no-subdomain"
        "FARGATE,JENKINS_SERVICE,PRODUCTION,with-domain,ssl-enabled,with-subdomain"
    )
    
    local passed=0
    local failed=0
    
    for config in "${smoke_configs[@]}"; do
        IFS=',' read -ra CONFIG_PARTS <<< "$config"
        local runtime="${CONFIG_PARTS[0]}"
        local topology="${CONFIG_PARTS[1]}"
        local security="${CONFIG_PARTS[2]}"
        local domain="${CONFIG_PARTS[3]}"
        local ssl="${CONFIG_PARTS[4]}"
        local subdomain="${CONFIG_PARTS[5]}"
        
        local stack_name="smoke-$(echo "$config" | tr '[:upper:],' '[:lower:]-' | tr -d ',')"
        
        echo -e "${CYAN}🧪 Testing: $runtime + $topology + $security${NC}"
        
        # Create deployment context
        local domain_value=""
        local subdomain_value=""
        local ssl_value="false"
        
        if [[ "$domain" == "with-domain" ]]; then
            domain_value="cloudforgeci.com"
            if [[ "$subdomain" == "with-subdomain" ]]; then
                subdomain_value="smoke-test"
            fi
            if [[ "$ssl" == "ssl-enabled" ]]; then
                ssl_value="true"
            fi
        fi
        
        cat > "$BASE_DIR/deployment-context.json" << EOF
{
  "stackName": "$stack_name",
  "context": {
    "runtime": "$runtime",
    "topology": "$topology",
    "securityProfile": "$security",
    "domain": "$domain_value",
    "subdomain": "$subdomain_value",
    "enableSsl": "$ssl_value",
    "stackName": "$stack_name"
  }
}
EOF
        
        # Run synthesis
        cd "$BASE_DIR"
        if cdk synth --quiet \
            --context cfc.runtime="$runtime" \
            --context cfc.topology="$topology" \
            --context cfc.securityProfile="$security" \
            --context cfc.stackName="$stack_name" \
            > /dev/null 2>&1; then
            
            echo -e "  ${GREEN}✅ PASS${NC}"
            passed=$((passed + 1))
        else
            echo -e "  ${RED}❌ FAIL${NC}"
            failed=$((failed + 1))
        fi
    done
    
    echo ""
    echo -e "${BLUE}📊 Smoke Test Results:${NC}"
    echo "Passed: $passed"
    echo "Failed: $failed"
    echo "Total:  $((passed + failed))"
    
    if [[ $failed -eq 0 ]]; then
        echo -e "${GREEN}🎉 All smoke tests passed${NC}"
        return 0
    else
        echo -e "${RED}💥 Some smoke tests failed${NC}"
        return 1
    fi
}

# Function to generate comprehensive report
generate_comprehensive_report() {
    echo -e "${PURPLE}📊 Generating comprehensive validation report...${NC}"
    echo ""
    
    local report_file="$VALIDATION_DIR/comprehensive-report.html"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    
    cat > "$report_file" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>CloudForge Core - Comprehensive Validation Report</title>
    <style>
        body { font-family: 'Segoe UI', sans-serif; margin: 20px; background: #f8f9fa; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); }
        .header { text-align: center; margin-bottom: 40px; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border-radius: 10px; }
        .section { margin: 30px 0; padding: 20px; border: 1px solid #e9ecef; border-radius: 8px; }
        .section h2 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; }
        .stat-card { background: linear-gradient(135deg, #74b9ff 0%, #0984e3 100%); color: white; padding: 20px; border-radius: 8px; text-align: center; }
        .stat-number { font-size: 2.5em; font-weight: bold; margin-bottom: 5px; }
        .stat-label { font-size: 1em; opacity: 0.9; }
        .success { background: linear-gradient(135deg, #00b894 0%, #00a085 100%); }
        .warning { background: linear-gradient(135deg, #fdcb6e 0%, #e17055 100%); }
        .error { background: linear-gradient(135deg, #d63031 0%, #74b9ff 100%); }
        .file-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 15px; }
        .file-card { background: #f8f9fa; border: 1px solid #dee2e6; border-radius: 5px; padding: 15px; }
        .file-name { font-weight: bold; color: #e74c3c; font-family: 'Courier New', monospace; }
        iframe { width: 100%; height: 500px; border: 1px solid #ddd; border-radius: 5px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔍 CloudForge Core</h1>
            <h2>Comprehensive Validation Report</h2>
            <p>Generated: $timestamp</p>
        </div>
        
        <div class="section">
            <h2>📊 Validation Overview</h2>
            <div class="stats-grid">
EOF

    # Add statistics if available
    if [[ -f "$VALIDATION_DIR/truth-table.json" ]]; then
        local total_configs=$(jq -r '.metadata.total_configurations' "$VALIDATION_DIR/truth-table.json" 2>/dev/null || echo "0")
        local valid_configs=$(jq -r '.metadata.valid_configurations' "$VALIDATION_DIR/truth-table.json" 2>/dev/null || echo "0")
        local invalid_configs=$(jq -r '.metadata.invalid_configurations' "$VALIDATION_DIR/truth-table.json" 2>/dev/null || echo "0")
        
        cat >> "$report_file" << EOF
                <div class="stat-card">
                    <div class="stat-number">$total_configs</div>
                    <div class="stat-label">Total Configurations</div>
                </div>
                <div class="stat-card success">
                    <div class="stat-number">$valid_configs</div>
                    <div class="stat-label">Valid Configurations</div>
                </div>
                <div class="stat-card warning">
                    <div class="stat-number">$invalid_configs</div>
                    <div class="stat-label">Invalid Combinations</div>
                </div>
EOF
    fi
    
    cat >> "$report_file" << EOF
            </div>
        </div>
        
        <div class="section">
            <h2>🎯 Truth Table & Test Matrix</h2>
            <p>Interactive truth table showing expected resources for every configuration combination:</p>
EOF

    if [[ -f "$VALIDATION_DIR/truth-table-report.html" ]]; then
        cat >> "$report_file" << EOF
            <iframe src="truth-table-report.html"></iframe>
EOF
    else
        cat >> "$report_file" << EOF
            <p><em>Truth table report not available. Run full validation to generate.</em></p>
EOF
    fi
    
    cat >> "$report_file" << EOF
        </div>
        
        <div class="section">
            <h2>🔍 Drift Detection</h2>
            <p>Configuration drift detection between baseline and current validation results:</p>
EOF

    # Add drift detection results if available
    local latest_drift_report=$(ls "$VALIDATION_DIR/drift-reports"/drift-summary-*.txt 2>/dev/null | sort | tail -1)
    if [[ -f "$latest_drift_report" ]]; then
        cat >> "$report_file" << EOF
            <div style="background: #f8f9fa; padding: 15px; border-radius: 5px; font-family: monospace; white-space: pre-wrap;">$(cat "$latest_drift_report")</div>
EOF
    else
        cat >> "$report_file" << EOF
            <p><em>No drift detection results available. Run drift detection to generate.</em></p>
EOF
    fi
    
    cat >> "$report_file" << EOF
        </div>
        
        <div class="section">
            <h2>📋 Validation Results</h2>
            <p>Detailed validation results for each configuration:</p>
            <div class="file-grid">
EOF

    # Add validation results
    for validation_file in "$VALIDATION_DIR/current"/*-validation.json; do
        if [[ -f "$validation_file" ]]; then
            local config_name=$(basename "$validation_file" -validation.json)
            local status=$(jq -r '.summary.status // "UNKNOWN"' "$validation_file" 2>/dev/null)
            local resource_count=$(jq -r '.summary.resource_count // 0' "$validation_file" 2>/dev/null)
            local missing_resources=$(jq -r '.summary.missing_resources // ""' "$validation_file" 2>/dev/null)
            
            local status_class="success"
            if [[ "$status" == "FAIL" ]]; then
                status_class="error"
            fi
            
            cat >> "$report_file" << EOF
                <div class="file-card $status_class">
                    <div class="file-name">$config_name</div>
                    <div>Status: $status</div>
                    <div>Resources: $resource_count</div>
                    $(if [[ -n "$missing_resources" ]]; then echo "<div>Missing: $missing_resources</div>"; fi)
                </div>
EOF
        fi
    done
    
    cat >> "$report_file" << EOF
            </div>
        </div>
        
        <div class="section">
            <h2>🚀 Next Steps</h2>
            <ul>
                <li><strong>Review Failures:</strong> Investigate any failed validations</li>
                <li><strong>Address Drift:</strong> Review configuration drift and ensure changes are intentional</li>
                <li><strong>Update Tests:</strong> Add regression tests for any new functionality</li>
                <li><strong>Documentation:</strong> Update documentation for any new features or changes</li>
                <li><strong>Create Baseline:</strong> Create new baseline after confirming all changes are correct</li>
            </ul>
        </div>
        
        <div style="text-align: center; margin-top: 40px; color: #7f8c8d;">
            <p>CloudForge Core Validation System v2.0.5</p>
        </div>
    </div>
</body>
</html>
EOF
    
    echo -e "${GREEN}📊 Comprehensive report generated: $report_file${NC}"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  full              Run complete validation suite (truth table + validation + drift)"
    echo "  validate          Run comprehensive resource validation only"
    echo "  drift             Detect configuration drift"
    echo "  smoke             Run smoke tests (quick validation)"
    echo "  baseline          Create new baseline from current results"
    echo "  report            Generate comprehensive HTML report"
    echo "  strategy [files]  Create testing strategy for specific file changes"
    echo "  help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 full                                    # Run complete validation suite"
    echo "  $0 smoke                                   # Quick smoke tests"
    echo "  $0 strategy \"FargateFactory.java,AlbFactory.java\"  # Test strategy for specific files"
    echo "  $0 drift                                   # Detect configuration drift"
    echo ""
    echo "Workflow:"
    echo "  1. Run '$0 full' for initial complete validation"
    echo "  2. Run '$0 baseline' to create baseline"
    echo "  3. After code changes, run '$0 validate'"
    echo "  4. Run '$0 drift' to detect changes"
    echo "  5. Run '$0 report' to generate summary"
}

# Main execution
case "${1:-help}" in
    "full")
        check_prerequisites
        run_full_validation
        detect_and_report_drift
        generate_comprehensive_report
        echo ""
        echo -e "${GREEN}🎉 Complete validation suite finished${NC}"
        ;;
    "validate")
        check_prerequisites
        run_full_validation
        ;;
    "drift")
        check_prerequisites
        detect_and_report_drift
        ;;
    "smoke")
        check_prerequisites
        run_smoke_tests
        ;;
    "baseline")
        check_prerequisites
        bash "$BASE_DIR/drift-detector.sh" baseline
        ;;
    "report")
        generate_comprehensive_report
        ;;
    "strategy")
        check_prerequisites
        create_testing_strategy "$2"
        ;;
    "help"|"-h"|"--help")
        show_usage
        ;;
    *)
        echo -e "${RED}❌ Unknown command: $1${NC}"
        echo ""
        show_usage
        exit 1
        ;;
esac
