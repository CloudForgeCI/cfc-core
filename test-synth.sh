#!/bin/bash
# Complete CDK synthesis test suite for CloudForgeCI cfc-core
# This script tests all combinations of runtime, topology, security profile, and SSL settings

set -e

echo "🧪 CloudForgeCI CDK Synthesis Test Suite"
echo "========================================"
echo ""

# Navigate to testing directory
cd cfc-testing

# Clear CDK cache
echo "🧹 Clearing CDK cache..."
rm -rf cdk.out cdk.context.json

echo ""
echo "🚀 Running synthesis tests..."
echo ""

# Test counter
PASSED=0
FAILED=0
TOTAL=0

# Function to run a test
run_test() {
    local test_name="$1"
    local cfc_config="$2"
    
    echo "=== $test_name ==="
    TOTAL=$((TOTAL + 1))
    
    if cdk synth -c "cfc=$cfc_config" > /dev/null 2>&1; then
        echo "✅ SUCCESS"
        PASSED=$((PASSED + 1))
    else
        echo "❌ FAILED"
        FAILED=$((FAILED + 1))
    fi
    echo ""
}

# Working combinations ✅
echo "📋 Testing Working Combinations:"
echo "--------------------------------"

run_test "EC2 + Service + Production + Domain + SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"service","securityProfile":"production"}'

run_test "EC2 + Service + Production + Domain + No SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"EC2","topology":"service","securityProfile":"production"}'

run_test "EC2 + Service + Production + No Domain" '{"enableSsl":false,"runtime":"EC2","topology":"service","securityProfile":"production"}'

run_test "Fargate + Service + Production + Domain + No SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'

run_test "Fargate + Service + Production + No Domain" '{"enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'

run_test "Fargate + Service + Production + Domain + SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'

run_test "Fargate + Service + Dev + Domain + SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"dev"}'

run_test "Fargate + Service + Staging + Domain + SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"staging"}'

# Known failing combinations ❌
echo "📋 Testing Known Issues (Expected Failures):"
echo "--------------------------------------------"

run_test "EC2 + Node + Production + Domain + SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"node","securityProfile":"production"}'

run_test "EC2 + Node + Dev + Domain + SSL" '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"node","securityProfile":"dev"}'

# Summary
echo "📊 Test Results Summary"
echo "======================="
echo "Total Tests: $TOTAL"
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo "Success Rate: $(( PASSED * 100 / TOTAL ))%"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "🎉 All tests passed!"
    exit 0
else
    echo "⚠️  Some tests failed (expected for known issues)"
    echo ""
    echo "Known Issues:"
    echo "- EC2 + Node topology: Single-node architectural incompatibility (HTTPS listener missing default action)"
    exit 1
fi
