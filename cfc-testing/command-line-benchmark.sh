#!/bin/bash

# Command-Line Parameter Benchmark
# Tests the new command-line parameter system for deployment options

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}🎯 Command-Line Parameter Benchmark${NC}"
echo -e "${BLUE}====================================${NC}"
echo ""

# Function to test command-line parameters
test_cli_params() {
    local test_name="$1"
    local stack_name="$2"
    local deployment_option="$3"
    local expected_behavior="$4"
    
    echo -e "${YELLOW}🧪 Testing: $test_name${NC}"
    echo -e "${YELLOW}Stack: $stack_name, Option: $deployment_option${NC}"
    echo -e "${YELLOW}Expected: $expected_behavior${NC}"
    
    # Clean up
    rm -f deployment-context.json
    
    # Run with command-line parameters
    local start_time=$(date +%s.%N)
    java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer "$stack_name" "$deployment_option" > "cli_test_${test_name}.log" 2>&1
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    # Check results
    if grep -q "Using deployment option from command line: $deployment_option" "cli_test_${test_name}.log"; then
        echo -e "${GREEN}✅ Command-line parameter correctly recognized${NC}"
    else
        echo -e "${RED}❌ Command-line parameter not recognized${NC}"
    fi
    
    if grep -q "CDK Stack synthesized successfully" "cli_test_${test_name}.log"; then
        echo -e "${GREEN}✅ Synthesis completed successfully${NC}"
    else
        echo -e "${RED}❌ Synthesis failed${NC}"
    fi
    
    echo -e "${GREEN}⏱️  Duration: ${duration}s${NC}"
    echo ""
}

# Test different command-line scenarios
echo -e "${BLUE}🚀 Testing Command-Line Parameter System${NC}"
echo ""

# Test 1: Synthesis only (option 1)
test_cli_params "synthesis_only" "cli-test-synth" "1" "Should synthesize only, no deployment"

# Test 2: Deploy to AWS (option 2)
test_cli_params "deploy_aws" "cli-test-deploy" "2" "Should synthesize and deploy to AWS"

# Test 3: Delete and redeploy (option 3)
test_cli_params "delete_redeploy" "cli-test-redeploy" "3" "Should delete existing stack and redeploy"

# Test 4: Cancel (option 4)
test_cli_params "cancel" "cli-test-cancel" "4" "Should cancel deployment"

# Test 5: Invalid option
test_cli_params "invalid_option" "cli-test-invalid" "99" "Should handle invalid option gracefully"

# Test 6: No deployment option (should prompt interactively)
echo -e "${YELLOW}🧪 Testing: No deployment option provided${NC}"
echo -e "${YELLOW}Expected: Should prompt for deployment option interactively${NC}"

rm -f deployment-context.json
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer "cli-test-no-option" > "cli_test_no_option.log" 2>&1

if grep -q "Choose option \[1-4\]:" "cli_test_no_option.log"; then
    echo -e "${GREEN}✅ Interactive prompt correctly displayed${NC}"
else
    echo -e "${RED}❌ Interactive prompt not displayed${NC}"
fi
echo ""

# Test 7: Stack name only (no deployment option)
echo -e "${YELLOW}🧪 Testing: Stack name only (no deployment option)${NC}"
echo -e "${YELLOW}Expected: Should use stack name and prompt for deployment option${NC}"

rm -f deployment-context.json
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer "cli-test-stack-only" > "cli_test_stack_only.log" 2>&1

if grep -q "Using custom stack name: cli-test-stack-only" "cli_test_stack_only.log"; then
    echo -e "${GREEN}✅ Stack name correctly recognized${NC}"
else
    echo -e "${RED}❌ Stack name not recognized${NC}"
fi

if grep -q "Choose option \[1-4\]:" "cli_test_stack_only.log"; then
    echo -e "${GREEN}✅ Interactive prompt correctly displayed${NC}"
else
    echo -e "${RED}❌ Interactive prompt not displayed${NC}"
fi
echo ""

# Display summary
echo -e "${BLUE}📊 Command-Line Parameter Test Summary${NC}"
echo -e "${BLUE}=======================================${NC}"
echo ""

# Count successful tests
successful_tests=$(grep -l "Command-line parameter correctly recognized" cli_test_*.log 2>/dev/null | wc -l)
total_tests=5

echo -e "${GREEN}✅ Successful Tests: $successful_tests/$total_tests${NC}"
echo ""

# List test files created
echo -e "${BLUE}📁 Test Log Files Created:${NC}"
ls -la cli_test_*.log 2>/dev/null || echo "No test log files found"
echo ""

# Cleanup
echo -e "${YELLOW}🧹 Cleaning up test files...${NC}"
rm -f deployment-context.json
rm -f cli_test_*.log
echo -e "${GREEN}✅ Cleanup completed${NC}"
echo ""

echo -e "${GREEN}🎉 Command-Line Parameter Benchmark completed!${NC}"
echo -e "${GREEN}===============================================${NC}"
