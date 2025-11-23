#!/bin/bash
# Generate compliance status report for a CloudForge deployment

set -euo pipefail

STACK_NAME="${1:-CloudForge-Prod-SOC2}"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "  CloudForge Compliance Status Report"
echo "========================================"
echo ""
echo "Stack: $STACK_NAME"
echo "Region: $REGION"
echo "Generated: $(date)"
echo ""

# Check if stack exists
if ! aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" &> /dev/null; then
    echo -e "${RED}ERROR:${NC} Stack $STACK_NAME not found in region $REGION"
    exit 1
fi

# Get AWS Config compliance status
echo "## AWS Config Compliance Status"
echo ""

COMPLIANCE_DATA=$(aws configservice describe-compliance-by-config-rule --region "$REGION" --output json 2>/dev/null || echo '{"ComplianceByConfigRules":[]}')

COMPLIANT=$(echo "$COMPLIANCE_DATA" | jq '[.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "COMPLIANT")] | length')
NON_COMPLIANT=$(echo "$COMPLIANCE_DATA" | jq '[.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT")] | length')
TOTAL=$((COMPLIANT + NON_COMPLIANT))

if [ "$TOTAL" -gt 0 ]; then
    COMPLIANCE_PCT=$((COMPLIANT * 100 / TOTAL))
else
    COMPLIANCE_PCT=0
fi

echo "Total Rules:        $TOTAL"
echo -e "${GREEN}Compliant:${NC}          $COMPLIANT"
if [ "$NON_COMPLIANT" -gt 0 ]; then
    echo -e "${RED}Non-Compliant:${NC}      $NON_COMPLIANT"
else
    echo -e "${GREEN}Non-Compliant:${NC}      $NON_COMPLIANT"
fi
echo "Compliance Rate:    ${COMPLIANCE_PCT}%"
echo ""

# List non-compliant resources if any
if [ "$NON_COMPLIANT" -gt 0 ]; then
    echo "## Non-Compliant Resources"
    echo ""
    echo "$COMPLIANCE_DATA" | jq -r '.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT") | "- " + .ConfigRuleName'
    echo ""
fi

# Check CloudTrail status
echo "## CloudTrail Status"
echo ""
TRAIL_STATUS=$(aws cloudtrail get-trail-status --name cloudforge-trail --region "$REGION" 2>/dev/null || echo '{"IsLogging":false}')
IS_LOGGING=$(echo "$TRAIL_STATUS" | jq -r '.IsLogging')

if [ "$IS_LOGGING" = "true" ]; then
    echo -e "Status: ${GREEN}Logging Enabled${NC}"
else
    echo -e "Status: ${RED}Logging Disabled${NC}"
fi
echo ""

# Check GuardDuty status
echo "## GuardDuty Status"
echo ""
DETECTOR_ID=$(aws guardduty list-detectors --region "$REGION" --query 'DetectorIds[0]' --output text 2>/dev/null || echo "None")

if [ "$DETECTOR_ID" != "None" ] && [ -n "$DETECTOR_ID" ]; then
    DETECTOR_STATUS=$(aws guardduty get-detector --detector-id "$DETECTOR_ID" --region "$REGION" 2>/dev/null || echo '{"Status":"DISABLED"}')
    STATUS=$(echo "$DETECTOR_STATUS" | jq -r '.Status')

    if [ "$STATUS" = "ENABLED" ]; then
        echo -e "Status: ${GREEN}Enabled${NC}"

        # Get finding counts
        FINDINGS=$(aws guardduty list-findings --detector-id "$DETECTOR_ID" --region "$REGION" 2>/dev/null || echo '{"FindingIds":[]}')
        FINDING_COUNT=$(echo "$FINDINGS" | jq '.FindingIds | length')
        echo "Active Findings: $FINDING_COUNT"
    else
        echo -e "Status: ${YELLOW}Not Enabled${NC}"
    fi
else
    echo -e "Status: ${YELLOW}Not Configured${NC}"
fi
echo ""

# Check encryption status
echo "## Encryption Status"
echo ""

# EFS Encryption
EFS_DATA=$(aws efs describe-file-systems --region "$REGION" --output json 2>/dev/null || echo '{"FileSystems":[]}')
EFS_COUNT=$(echo "$EFS_DATA" | jq '.FileSystems | length')
EFS_ENCRYPTED=$(echo "$EFS_DATA" | jq '[.FileSystems[] | select(.Encrypted == true)] | length')

if [ "$EFS_COUNT" -gt 0 ]; then
    if [ "$EFS_ENCRYPTED" -eq "$EFS_COUNT" ]; then
        echo -e "EFS Encryption: ${GREEN}$EFS_ENCRYPTED/$EFS_COUNT encrypted${NC}"
    else
        echo -e "EFS Encryption: ${RED}$EFS_ENCRYPTED/$EFS_COUNT encrypted${NC}"
    fi
else
    echo "EFS Encryption: No EFS file systems"
fi

# S3 Encryption (sample)
S3_BUCKETS=$(aws s3api list-buckets --region "$REGION" --query 'Buckets[].Name' --output text 2>/dev/null || echo "")
S3_TOTAL=0
S3_ENCRYPTED=0

for bucket in $S3_BUCKETS; do
    S3_TOTAL=$((S3_TOTAL + 1))
    if aws s3api get-bucket-encryption --bucket "$bucket" &> /dev/null; then
        S3_ENCRYPTED=$((S3_ENCRYPTED + 1))
    fi
done

if [ "$S3_TOTAL" -gt 0 ]; then
    if [ "$S3_ENCRYPTED" -eq "$S3_TOTAL" ]; then
        echo -e "S3 Encryption:  ${GREEN}$S3_ENCRYPTED/$S3_TOTAL encrypted${NC}"
    else
        echo -e "S3 Encryption:  ${YELLOW}$S3_ENCRYPTED/$S3_TOTAL encrypted${NC}"
    fi
else
    echo "S3 Encryption:  No S3 buckets"
fi
echo ""

# Check MFA status (if Cognito is used)
echo "## Authentication Security"
echo ""

COGNITO_POOL_ID=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query 'Stacks[0].Outputs[?OutputKey==`CognitoUserPoolId`].OutputValue' \
    --output text 2>/dev/null || echo "")

if [ -n "$COGNITO_POOL_ID" ] && [ "$COGNITO_POOL_ID" != "None" ]; then
    POOL_CONFIG=$(aws cognito-idp describe-user-pool --user-pool-id "$COGNITO_POOL_ID" --region "$REGION" 2>/dev/null || echo '{"UserPool":{"MfaConfiguration":"OFF"}}')
    MFA_CONFIG=$(echo "$POOL_CONFIG" | jq -r '.UserPool.MfaConfiguration')

    if [ "$MFA_CONFIG" = "ON" ] || [ "$MFA_CONFIG" = "OPTIONAL" ]; then
        echo -e "Cognito MFA: ${GREEN}$MFA_CONFIG${NC}"
    else
        echo -e "Cognito MFA: ${RED}$MFA_CONFIG${NC}"
    fi
else
    echo "Cognito MFA: Not configured"
fi
echo ""

# Summary and recommendations
echo "## Recommendations"
echo ""

if [ "$NON_COMPLIANT" -gt 0 ]; then
    echo "⚠️  Address non-compliant Config rules:"
    echo "   aws configservice describe-compliance-by-config-rule --compliance-types NON_COMPLIANT"
    echo ""
fi

if [ "$IS_LOGGING" != "true" ]; then
    echo "⚠️  Enable CloudTrail logging for audit trail"
    echo ""
fi

if [ "$S3_ENCRYPTED" -lt "$S3_TOTAL" ]; then
    echo "⚠️  Enable encryption for all S3 buckets"
    echo ""
fi

if [ "$COMPLIANCE_PCT" -eq 100 ]; then
    echo -e "${GREEN}✅ All compliance checks passed!${NC}"
else
    echo -e "${YELLOW}⚠️  Compliance rate: ${COMPLIANCE_PCT}% - review and remediate issues${NC}"
fi
echo ""

echo "========================================"
echo "  For detailed evidence package:"
echo "  ./scripts/generate-audit-evidence.sh"
echo "========================================"
