#!/bin/bash

# Master Benchmark Runner
# Executes all performance synthesis benchmarks for the Interactive Deployer

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}🎯 Master Benchmark Runner${NC}"
echo -e "${BLUE}===========================${NC}"
echo ""
echo -e "${YELLOW}This script will run comprehensive performance benchmarks for the Interactive Deployer${NC}"
echo -e "${YELLOW}including synthesis performance, command-line parameter testing, and stress tests.${NC}"
echo ""

# Function to display menu
show_menu() {
    echo -e "${BLUE}📋 Available Benchmark Types:${NC}"
    echo ""
    echo "1. Quick Synthesis Benchmark (5 tests, ~2 minutes)"
    echo "2. Command-Line Parameter Benchmark (7 tests, ~3 minutes)"
    echo "3. Comprehensive Performance Benchmark (10+ tests, ~10 minutes)"
    echo "4. Stress Test Benchmark (5 rapid tests, ~5 minutes)"
    echo "5. Run All Benchmarks (Complete suite, ~20 minutes)"
    echo "6. Custom Benchmark Selection"
    echo "7. Exit"
    echo ""
}

# Function to run quick benchmark
run_quick_benchmark() {
    echo -e "${GREEN}🚀 Running Quick Synthesis Benchmark${NC}"
    echo -e "${GREEN}=====================================${NC}"
    ./quick-synth-benchmark.sh
}

# Function to run command-line benchmark
run_cli_benchmark() {
    echo -e "${GREEN}🚀 Running Command-Line Parameter Benchmark${NC}"
    echo -e "${GREEN}============================================${NC}"
    ./command-line-benchmark.sh
}

# Function to run comprehensive benchmark
run_comprehensive_benchmark() {
    echo -e "${GREEN}🚀 Running Comprehensive Performance Benchmark${NC}"
    echo -e "${GREEN}===============================================${NC}"
    ./performance-synth-benchmark.sh
}

# Function to run stress test
run_stress_test() {
    echo -e "${GREEN}🚀 Running Stress Test Benchmark${NC}"
    echo -e "${GREEN}===============================${NC}"
    
    echo -e "${YELLOW}📊 Running rapid synthesis stress test${NC}"
    echo ""
    
    for i in {1..5}; do
        echo -e "${YELLOW}Stress Test Iteration $i/5${NC}"
        rm -f deployment-context.json
        
        local start_time=$(date +%s.%N)
        java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer "stress-test-$i" "1" > /dev/null 2>&1
        local end_time=$(date +%s.%N)
        local duration=$(echo "$end_time - $start_time" | bc)
        
        echo -e "${GREEN}✅ Iteration $i completed in ${duration}s${NC}"
        echo ""
    done
    
    echo -e "${GREEN}💪 Stress test completed!${NC}"
}

# Function to run all benchmarks
run_all_benchmarks() {
    echo -e "${GREEN}🚀 Running Complete Benchmark Suite${NC}"
    echo -e "${GREEN}====================================${NC}"
    echo ""
    
    # Run all benchmark types
    run_quick_benchmark
    echo ""
    
    run_cli_benchmark
    echo ""
    
    run_comprehensive_benchmark
    echo ""
    
    run_stress_test
    echo ""
    
    echo -e "${GREEN}🎉 Complete benchmark suite finished!${NC}"
}

# Function to run custom selection
run_custom_selection() {
    echo -e "${BLUE}🎯 Custom Benchmark Selection${NC}"
    echo -e "${BLUE}=============================${NC}"
    echo ""
    
    echo "Select benchmarks to run (comma-separated, e.g., 1,3,4):"
    echo "1. Quick Synthesis Benchmark"
    echo "2. Command-Line Parameter Benchmark"
    echo "3. Comprehensive Performance Benchmark"
    echo "4. Stress Test Benchmark"
    echo ""
    
    read -p "Enter your selection: " selection
    
    # Parse selection
    IFS=',' read -ra ADDR <<< "$selection"
    for i in "${ADDR[@]}"; do
        case $i in
            1)
                run_quick_benchmark
                echo ""
                ;;
            2)
                run_cli_benchmark
                echo ""
                ;;
            3)
                run_comprehensive_benchmark
                echo ""
                ;;
            4)
                run_stress_test
                echo ""
                ;;
            *)
                echo -e "${RED}❌ Invalid selection: $i${NC}"
                ;;
        esac
    done
}

# Function to check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}🔍 Checking prerequisites...${NC}"
    
    # Check if Java is available
    if ! command -v java &> /dev/null; then
        echo -e "${RED}❌ Java is not installed or not in PATH${NC}"
        exit 1
    fi
    
    # Check if InteractiveDeployer is compiled
    if [ ! -f "target/classes/com/cloudforgeci/samples/app/InteractiveDeployer.class" ]; then
        echo -e "${YELLOW}⚠️  InteractiveDeployer not compiled. Running mvn compile...${NC}"
        mvn compile -q
    fi
    
    # Check if benchmark scripts exist
    local scripts=("quick-synth-benchmark.sh" "command-line-benchmark.sh" "performance-synth-benchmark.sh")
    for script in "${scripts[@]}"; do
        if [ ! -f "$script" ]; then
            echo -e "${RED}❌ Benchmark script not found: $script${NC}"
            exit 1
        fi
    done
    
    echo -e "${GREEN}✅ Prerequisites check passed${NC}"
    echo ""
}

# Main menu loop
main() {
    # Check prerequisites
    check_prerequisites
    
    while true; do
        show_menu
        read -p "Select benchmark type (1-7): " choice
        
        case $choice in
            1)
                run_quick_benchmark
                ;;
            2)
                run_cli_benchmark
                ;;
            3)
                run_comprehensive_benchmark
                ;;
            4)
                run_stress_test
                ;;
            5)
                run_all_benchmarks
                ;;
            6)
                run_custom_selection
                ;;
            7)
                echo -e "${GREEN}👋 Goodbye!${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}❌ Invalid selection. Please choose 1-7.${NC}"
                ;;
        esac
        
        echo ""
        read -p "Press Enter to continue or Ctrl+C to exit..."
        echo ""
    done
}

# Run main function
main "$@"
