#!/bin/bash

# Performance Synthesis Benchmark System
# Tests the new Interactive Deployer with various configurations to measure synthesis performance

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BENCHMARK_DIR="benchmark-results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_FILE="$BENCHMARK_DIR/synth_performance_$TIMESTAMP.txt"
SUMMARY_FILE="$BENCHMARK_DIR/synth_summary_$TIMESTAMP.txt"

# Create benchmark directory
mkdir -p "$BENCHMARK_DIR"

echo -e "${BLUE}🚀 Performance Synthesis Benchmark System${NC}"
echo -e "${BLUE}===========================================${NC}"
echo ""

# Function to run a benchmark test
run_benchmark() {
    local test_name="$1"
    local stack_name="$2"
    local deployment_option="$3"
    local description="$4"
    
    echo -e "${YELLOW}📊 Running benchmark: $test_name${NC}"
    echo -e "${YELLOW}Description: $description${NC}"
    echo -e "${YELLOW}Stack: $stack_name, Option: $deployment_option${NC}"
    
    # Clean up any existing context
    rm -f deployment-context.json
    
    # Measure synthesis time
    local start_time=$(date +%s.%N)
    
    # Run the Interactive Deployer with the specified configuration
    java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer "$stack_name" "$deployment_option" > "$BENCHMARK_DIR/${test_name}_output.log" 2>&1
    
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    # Check if synthesis was successful
    if grep -q "CDK Stack synthesized successfully" "$BENCHMARK_DIR/${test_name}_output.log"; then
        echo -e "${GREEN}✅ $test_name completed successfully${NC}"
        echo -e "${GREEN}⏱️  Duration: ${duration}s${NC}"
        
        # Record results
        echo "$test_name,$stack_name,$deployment_option,$duration,SUCCESS" >> "$RESULTS_FILE"
    else
        echo -e "${RED}❌ $test_name failed${NC}"
        echo -e "${RED}⏱️  Duration: ${duration}s${NC}"
        
        # Record results
        echo "$test_name,$stack_name,$deployment_option,$duration,FAILED" >> "$RESULTS_FILE"
    fi
    
    echo ""
}

# Function to run comprehensive benchmarks
run_comprehensive_benchmarks() {
    echo -e "${BLUE}🔬 Running Comprehensive Synthesis Benchmarks${NC}"
    echo -e "${BLUE}===============================================${NC}"
    echo ""
    
    # Initialize results file
    echo "Test Name,Stack Name,Deployment Option,Duration (s),Status" > "$RESULTS_FILE"
    
    # Test 1: Basic Fargate Jenkins (Synthesis Only)
    run_benchmark "basic_fargate_synth" "perf-basic-fargate" "1" "Basic Fargate Jenkins deployment (synthesis only)"
    
    # Test 2: Basic EC2 Jenkins (Synthesis Only)
    run_benchmark "basic_ec2_synth" "perf-basic-ec2" "1" "Basic EC2 Jenkins deployment (synthesis only)"
    
    # Test 3: Fargate with Domain and SSL (Synthesis Only)
    run_benchmark "fargate_domain_ssl_synth" "perf-fargate-domain" "1" "Fargate Jenkins with domain and SSL (synthesis only)"
    
    # Test 4: EC2 with Domain and SSL (Synthesis Only)
    run_benchmark "ec2_domain_ssl_synth" "perf-ec2-domain" "1" "EC2 Jenkins with domain and SSL (synthesis only)"
    
    # Test 5: Production Security Profile (Synthesis Only)
    run_benchmark "production_security_synth" "perf-production" "1" "Production security profile with all features (synthesis only)"
    
    # Test 6: Staging Security Profile (Synthesis Only)
    run_benchmark "staging_security_synth" "perf-staging" "1" "Staging security profile (synthesis only)"
    
    # Test 7: Dev Security Profile (Synthesis Only)
    run_benchmark "dev_security_synth" "perf-dev" "1" "Dev security profile (synthesis only)"
    
    # Test 8: Fargate with Auto Scaling (Synthesis Only)
    run_benchmark "fargate_autoscaling_synth" "perf-fargate-as" "1" "Fargate Jenkins with auto scaling (synthesis only)"
    
    # Test 9: EC2 with EFS (Synthesis Only)
    run_benchmark "ec2_efs_synth" "perf-ec2-efs" "1" "EC2 Jenkins with EFS storage (synthesis only)"
    
    # Test 10: Complex Multi-Component (Synthesis Only)
    run_benchmark "complex_multi_component_synth" "perf-complex" "1" "Complex deployment with all components (synthesis only)"
}

# Function to run deployment benchmarks (actual AWS deployment)
run_deployment_benchmarks() {
    echo -e "${BLUE}🚀 Running Deployment Benchmarks${NC}"
    echo -e "${BLUE}===================================${NC}"
    echo ""
    
    # Test 11: Basic Fargate Deployment (Actual AWS)
    run_benchmark "basic_fargate_deploy" "perf-deploy-fargate" "2" "Basic Fargate Jenkins deployment (actual AWS deployment)"
    
    # Test 12: Basic EC2 Deployment (Actual AWS)
    run_benchmark "basic_ec2_deploy" "perf-deploy-ec2" "2" "Basic EC2 Jenkins deployment (actual AWS deployment)"
}

# Function to run stress tests
run_stress_tests() {
    echo -e "${BLUE}💪 Running Stress Tests${NC}"
    echo -e "${BLUE}=======================${NC}"
    echo ""
    
    # Test 13: Multiple Rapid Syntheses
    echo -e "${YELLOW}📊 Running rapid synthesis stress test${NC}"
    local stress_start=$(date +%s.%N)
    
    for i in {1..5}; do
        run_benchmark "stress_rapid_$i" "perf-stress-$i" "1" "Rapid synthesis stress test iteration $i"
    done
    
    local stress_end=$(date +%s.%N)
    local stress_duration=$(echo "$stress_end - $stress_start" | bc)
    echo -e "${GREEN}💪 Stress test completed in ${stress_duration}s${NC}"
    echo ""
}

# Function to generate performance summary
generate_summary() {
    echo -e "${BLUE}📈 Generating Performance Summary${NC}"
    echo -e "${BLUE}===================================${NC}"
    echo ""
    
    # Create summary file
    echo "Performance Synthesis Benchmark Summary" > "$SUMMARY_FILE"
    echo "Generated: $(date)" >> "$SUMMARY_FILE"
    echo "======================================" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    
    # Calculate statistics
    local total_tests=$(tail -n +2 "$RESULTS_FILE" | wc -l)
    local successful_tests=$(grep "SUCCESS" "$RESULTS_FILE" | wc -l)
    local failed_tests=$(grep "FAILED" "$RESULTS_FILE" | wc -l)
    
    # Calculate average duration for successful tests
    local avg_duration=$(grep "SUCCESS" "$RESULTS_FILE" | cut -d',' -f4 | awk '{sum+=$1} END {print sum/NR}')
    
    # Find fastest and slowest tests
    local fastest=$(grep "SUCCESS" "$RESULTS_FILE" | sort -t',' -k4 -n | head -1 | cut -d',' -f1,4)
    local slowest=$(grep "SUCCESS" "$RESULTS_FILE" | sort -t',' -k4 -n | tail -1 | cut -d',' -f1,4)
    
    echo "Test Statistics:" >> "$SUMMARY_FILE"
    echo "- Total Tests: $total_tests" >> "$SUMMARY_FILE"
    echo "- Successful: $successful_tests" >> "$SUMMARY_FILE"
    echo "- Failed: $failed_tests" >> "$SUMMARY_FILE"
    echo "- Success Rate: $(echo "scale=2; $successful_tests * 100 / $total_tests" | bc)%" >> "$SUMMARY_FILE"
    echo "- Average Duration: ${avg_duration}s" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    
    echo "Performance Highlights:" >> "$SUMMARY_FILE"
    echo "- Fastest Test: $fastest" >> "$SUMMARY_FILE"
    echo "- Slowest Test: $slowest" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    
    echo "Detailed Results:" >> "$SUMMARY_FILE"
    echo "=================" >> "$SUMMARY_FILE"
    cat "$RESULTS_FILE" >> "$SUMMARY_FILE"
    
    # Display summary
    echo -e "${GREEN}📊 Benchmark Summary:${NC}"
    echo -e "${GREEN}Total Tests: $total_tests${NC}"
    echo -e "${GREEN}Successful: $successful_tests${NC}"
    echo -e "${GREEN}Failed: $failed_tests${NC}"
    echo -e "${GREEN}Success Rate: $(echo "scale=2; $successful_tests * 100 / $total_tests" | bc)%${NC}"
    echo -e "${GREEN}Average Duration: ${avg_duration}s${NC}"
    echo ""
    echo -e "${GREEN}📁 Results saved to:${NC}"
    echo -e "${GREEN}  - $RESULTS_FILE${NC}"
    echo -e "${GREEN}  - $SUMMARY_FILE${NC}"
    echo ""
}

# Function to clean up test resources
cleanup() {
    echo -e "${YELLOW}🧹 Cleaning up test resources...${NC}"
    
    # Remove deployment context files
    rm -f deployment-context.json
    
    # Remove CDK output (optional - comment out if you want to keep it)
    # rm -rf cdk.out
    
    echo -e "${GREEN}✅ Cleanup completed${NC}"
}

# Main execution
main() {
    echo -e "${BLUE}🎯 Starting Performance Synthesis Benchmark${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
    
    # Check if Java and dependencies are available
    if [ ! -f "target/classes/com/cloudforgeci/samples/app/InteractiveDeployer.class" ]; then
        echo -e "${RED}❌ InteractiveDeployer not compiled. Running mvn compile...${NC}"
        mvn compile -q
    fi
    
    # Run benchmarks
    run_comprehensive_benchmarks
    
    # Ask user if they want to run deployment benchmarks (actual AWS deployment)
    echo -e "${YELLOW}🤔 Do you want to run deployment benchmarks (actual AWS deployment)?${NC}"
    echo -e "${YELLOW}   This will create actual AWS resources and may incur costs.${NC}"
    read -p "Continue with deployment benchmarks? (y/N): " -n 1 -r
    echo ""
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        run_deployment_benchmarks
    else
        echo -e "${BLUE}⏭️  Skipping deployment benchmarks${NC}"
    fi
    
    # Run stress tests
    run_stress_tests
    
    # Generate summary
    generate_summary
    
    # Cleanup
    cleanup
    
    echo -e "${GREEN}🎉 Performance Synthesis Benchmark completed!${NC}"
    echo -e "${GREEN}=============================================${NC}"
}

# Run main function
main "$@"
