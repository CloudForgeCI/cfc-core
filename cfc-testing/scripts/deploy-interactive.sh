#!/bin/bash

# CloudForge Community Interactive Deployer
# This script runs the Interactive Deployer for CloudForge Community sample applications

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 CloudForge Community Interactive Deployer${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}❌ Error: pom.xml not found. Please run this script from the cfc-testing directory.${NC}"
    exit 1
fi

# Check if CDK is installed
if ! command -v cdk &> /dev/null; then
    echo -e "${RED}❌ Error: AWS CDK CLI not found. Please install it first:${NC}"
    echo "   npm install -g aws-cdk"
    exit 1
fi

# Check if AWS credentials are configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}❌ Error: AWS credentials not configured. Please run:${NC}"
    echo "   aws configure"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites check passed${NC}"
echo ""

# Compile the project
echo -e "${YELLOW}🔧 Compiling project...${NC}"
mvn compile -q

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Compilation successful${NC}"
echo ""

# Run the interactive deployer
echo -e "${YELLOW}🚀 Starting interactive deployment...${NC}"
echo ""

# Run with proper input handling and force interactive mode
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer"$@"

echo ""
echo -e "${GREEN}✅ Interactive deployment completed!${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. Review the generated CDK stack"
echo "2. Run 'cdk deploy' to deploy to AWS"
echo "3. Or run 'cdk diff' to see what will be created"
echo ""
echo -e "${YELLOW}💡 Tip: Use 'cdk destroy' to clean up resources when done${NC}"
echo ""
echo -e "${BLUE}🔄 To run the interactive deployer again:${NC}"
echo "   ./deploy-interactive.sh"
echo ""
echo -e "${BLUE}🗑️  To delete an existing stack:${NC}"
echo "   cdk destroy"
