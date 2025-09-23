#!/usr/bin/env bash
set -euo pipefail

# CDK Synthesis Benchmark Tool
# Integrates with test-synth.sh test cases for performance testing

echo "🚀 CDK Synthesis Benchmark Tool"
echo "==============================="
echo ""

# Navigate to testing directory
cd cfc-testing

# Clear CDK cache
echo "🧹 Clearing CDK cache..."
rm -rf cdk.out cdk.context.json

# Define test cases (matching test-synth.sh)
declare -a TEST_NAMES=(
    "EC2 + Service + Production + Domain + SSL"
    "EC2 + Service + Production + Domain + No SSL"
    "EC2 + Service + Production + No Domain"
    "Fargate + Service + Production + Domain + No SSL"
    "Fargate + Service + Production + No Domain"
    "EC2 + Node + Production + Domain + SSL"
    "EC2 + Node + Dev + Domain + SSL"
    "Fargate + Service + Production + Domain + SSL"
    "Fargate + Service + Dev + Domain + SSL"
    "Fargate + Service + Staging + Domain + SSL"
)

declare -a TEST_CONFIGS=(
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"service","securityProfile":"production"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"EC2","topology":"service","securityProfile":"production"}'
    '{"enableSsl":false,"runtime":"EC2","topology":"service","securityProfile":"production"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'
    '{"enableSsl":false,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"node","securityProfile":"production"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"EC2","topology":"node","securityProfile":"dev"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"production"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"dev"}'
    '{"domain":"cloudforgeci.com","subdomain":"jenkins","enableSsl":true,"runtime":"FARGATE","topology":"service","securityProfile":"staging"}'
)

# Display test cases for selection
echo "📋 Available Test Cases:"
echo "------------------------"
for i in "${!TEST_NAMES[@]}"; do
    printf "%2d) %s\n" $((i+1)) "${TEST_NAMES[$i]}"
done
echo ""

# Get user selection
while true; do
    read -p "Select test case (1-${#TEST_NAMES[@]}): " selection
    if [[ "$selection" =~ ^[0-9]+$ ]] && [ "$selection" -ge 1 ] && [ "$selection" -le "${#TEST_NAMES[@]}" ]; then
        break
    else
        echo "❌ Invalid selection. Please enter a number between 1 and ${#TEST_NAMES[@]}"
    fi
done

# Get number of runs
while true; do
    read -p "Number of benchmark runs (default: 10): " runs
    if [[ -z "$runs" ]]; then
        runs=10
        break
    elif [[ "$runs" =~ ^[0-9]+$ ]] && [ "$runs" -gt 0 ]; then
        break
    else
        echo "❌ Invalid input. Please enter a positive number."
    fi
done

# Set up variables
selected_index=$((selection-1))
test_name="${TEST_NAMES[$selected_index]}"
test_config="${TEST_CONFIGS[$selected_index]}"
RUNS=$runs
OUTFILE="./synth_times.txt"

echo ""
echo "🎯 Selected Test: $test_name"
echo "🔄 Running $RUNS benchmark iterations..."
echo ""

# Clear previous results
rm -f "$OUTFILE"
echo "# synth timings (seconds) - $test_name" > "$OUTFILE"

# Run benchmark
for i in $(seq 1 $RUNS); do
    echo -n "Run $i/$RUNS... "
    
    start=$(date +%s.%N)
    
    # Run the CDK synth command
    if cdk synth -c "cfc=$test_config" > /dev/null 2>&1; then
        end=$(date +%s.%N)
        elapsed=$(python3 - <<PY
s=${end}; e=${start}
print(float(s)-float(e))
PY
)
        printf "%.6f\n" "$elapsed" | tee -a "$OUTFILE"
        echo "✅"
    else
        echo "❌ FAILED"
        echo "ERROR: CDK synthesis failed for this test case" >> "$OUTFILE"
        break
    fi
done

echo ""
echo "📊 Benchmark Results:"
echo "===================="

# Calculate statistics using standard awk (compatible with macOS)
awk 'NR>1 && $1 != "ERROR:" {a[NR-1]=$1; s+=$1} END{
    n=NR-1; 
    if(n>0) {
        as=s/n; 
        # Simple bubble sort for small arrays (standard awk)
        for(i=1; i<=n; i++) {
            for(j=i+1; j<=n; j++) {
                if(a[i] > a[j]) {
                    temp = a[i]; a[i] = a[j]; a[j] = temp;
                }
            }
        }
        min=a[1]; 
        max=a[n]; 
        med=a[int((n+1)/2)]; 
        p95=a[int(n*0.95)]; 
        printf "runs=%d\nmin=%.6f\nmedian=%.6f\np95=%.6f\nmax=%.6f\navg=%.6f\n", n, min, med, p95, max, as
    } else {
        print "No successful runs to analyze"
    }
}' "$OUTFILE"

echo ""
echo "📁 Detailed results saved to: $OUTFILE"
echo "🎉 Benchmark complete!"
