#!/bin/bash

# Quick Synthesis Benchmark
# Focused performance testing for the Interactive Deployer

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}⚡ Quick Synthesis Benchmark${NC}"
echo -e "${BLUE}============================${NC}"
echo ""

# Function to measure synthesis time
measure_synth() {
    local test_name="$1"
    local stack_name="$2"
    local description="$3"
    
    echo -e "${YELLOW}📊 $test_name${NC}"
    echo -e "${YELLOW}$description${NC}"
    
    # Clean up
    rm -f deployment-context.json
    
    # Measure time
    local start_time=$(date +%s.%N)
    
    # Run synthesis
    java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer "$stack_name" "1" > /dev/null 2>&1
    
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    echo -e "${GREEN}✅ Completed in ${duration}s${NC}"
    echo ""
    
    # Return duration for comparison
    echo "$duration"
}

# Run quick benchmarks
echo -e "${BLUE}🚀 Running Quick Synthesis Benchmarks${NC}"
echo ""

# Test 1: Basic Fargate
fargate_time=$(measure_synth "Basic Fargate" "quick-fargate" "Basic Fargate Jenkins deployment")

# Test 2: Basic EC2
ec2_time=$(measure_synth "Basic EC2" "quick-ec2" "Basic EC2 Jenkins deployment")

# Test 3: Fargate with Domain
fargate_domain_time=$(measure_synth "Fargate + Domain" "quick-fargate-domain" "Fargate Jenkins with domain configuration")

# Test 4: EC2 with Domain
ec2_domain_time=$(measure_synth "EC2 + Domain" "quick-ec2-domain" "EC2 Jenkins with domain configuration")

# Test 5: Production Security
production_time=$(measure_synth "Production Security" "quick-production" "Production security profile with all features")

# Display results
echo -e "${BLUE}📈 Quick Benchmark Results${NC}"
echo -e "${BLUE}===========================${NC}"
echo ""
echo -e "${GREEN}Basic Fargate:        ${fargate_time}s${NC}"
echo -e "${GREEN}Basic EC2:           ${ec2_time}s${NC}"
echo -e "${GREEN}Fargate + Domain:     ${fargate_domain_time}s${NC}"
echo -e "${GREEN}EC2 + Domain:        ${ec2_domain_time}s${NC}"
echo -e "${GREEN}Production Security: ${production_time}s${NC}"
echo ""

# Calculate fastest and slowest
fastest=$(echo -e "$fargate_time\n$ec2_time\n$fargate_domain_time\n$ec2_domain_time\n$production_time" | sort -n | head -1)
slowest=$(echo -e "$fargate_time\n$ec2_time\n$fargate_domain_time\n$ec2_domain_time\n$production_time" | sort -n | tail -1)

echo -e "${BLUE}🏆 Performance Highlights${NC}"
echo -e "${GREEN}Fastest: ${fastest}s${NC}"
echo -e "${GREEN}Slowest: ${slowest}s${NC}"
echo ""

# Cleanup
rm -f deployment-context.json
echo -e "${GREEN}✅ Quick benchmark completed!${NC}"
