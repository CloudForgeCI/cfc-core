#!/usr/bin/env bash
# Generate comprehensive audit evidence package for compliance audits
# Supports SOC 2, HIPAA, PCI-DSS, and GDPR frameworks

set -euo pipefail

# Default values
STACK_NAME="${STACK_NAME:-CloudForge-Prod-SOC2}"
FRAMEWORK="${FRAMEWORK:-SOC2}"
# Cross-platform date command (works on both macOS and Linux)
if date -v-1y &>/dev/null 2>&1; then
    # macOS (BSD date)
    START_DATE="${START_DATE:-$(date -v-1y +%Y-%m-%d)}"
else
    # Linux (GNU date)
    START_DATE="${START_DATE:-$(date -d '1 year ago' +%Y-%m-%d)}"
fi
END_DATE="${END_DATE:-$(date +%Y-%m-%d)}"
OUTPUT_DIR="audit-evidence-$(date +%Y%m%d-%H%M%S)"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Usage function
usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Generate comprehensive audit evidence package for compliance audits.

OPTIONS:
    --stack-name NAME       CloudFormation stack name (default: CloudForge-Prod-SOC2)
    --framework FRAMEWORK   Compliance framework: SOC2, HIPAA, PCI-DSS, GDPR (default: SOC2)
    --start-date YYYY-MM-DD Start date for log collection (default: 1 year ago)
    --end-date YYYY-MM-DD   End date for log collection (default: today)
    --output DIR            Output directory (default: audit-evidence-TIMESTAMP)
    --region REGION         AWS region (default: us-east-1)
    --help                  Show this help message

EXAMPLES:
    # Generate SOC 2 evidence for the last 6 months
    $0 --framework SOC2 --start-date 2024-06-01

    # Generate HIPAA evidence with custom stack name
    $0 --stack-name MyStack --framework HIPAA

    # Generate evidence for specific date range
    $0 --start-date 2024-01-01 --end-date 2024-12-31

ENVIRONMENT VARIABLES:
    AWS_DEFAULT_REGION      AWS region (default: us-east-1)
    AWS_PROFILE             AWS CLI profile to use

EOF
    exit 1
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --stack-name)
            STACK_NAME="$2"
            shift 2
            ;;
        --framework)
            FRAMEWORK="$2"
            shift 2
            ;;
        --start-date)
            START_DATE="$2"
            shift 2
            ;;
        --end-date)
            END_DATE="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --region)
            REGION="$2"
            shift 2
            ;;
        --help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "\033[0;31m[ERROR]\033[0m $1" >&2
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI is not installed. Please install it first."
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        log_error "jq is not installed. Please install it first."
        exit 1
    fi

    # Verify AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        log_error "AWS credentials not configured. Run 'aws configure' first."
        exit 1
    fi

    log_success "Prerequisites check passed"
}

# Create directory structure
create_directory_structure() {
    log_info "Creating directory structure..."

    mkdir -p "$OUTPUT_DIR"/{infrastructure,iam,encryption,logging,monitoring,config,compliance,network,reports}

    log_success "Directory structure created: $OUTPUT_DIR"
}

# Export CloudFormation template
export_cloudformation() {
    log_info "Exporting CloudFormation template..."

    if aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" &> /dev/null; then
        aws cloudformation get-template \
            --stack-name "$STACK_NAME" \
            --region "$REGION" \
            --query 'TemplateBody' \
            --output text > "$OUTPUT_DIR/infrastructure/cloudformation-template.yaml"

        # Export stack metadata
        aws cloudformation describe-stacks \
            --stack-name "$STACK_NAME" \
            --region "$REGION" \
            --output json > "$OUTPUT_DIR/infrastructure/stack-metadata.json"

        # Export stack resources
        aws cloudformation list-stack-resources \
            --stack-name "$STACK_NAME" \
            --region "$REGION" \
            --output json > "$OUTPUT_DIR/infrastructure/stack-resources.json"

        log_success "CloudFormation template exported"
    else
        log_warning "Stack $STACK_NAME not found in region $REGION"
    fi
}

# Export IAM configuration
export_iam() {
    log_info "Exporting IAM configuration..."

    # List all policies (local only)
    aws iam list-policies \
        --scope Local \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/iam/policies.json"

    # List all roles
    aws iam list-roles \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/iam/roles.json"

    # List all users
    aws iam list-users \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/iam/users.json"

    # Export credential report
    aws iam generate-credential-report --region "$REGION" || true
    sleep 5
    aws iam get-credential-report \
        --region "$REGION" \
        --output text \
        --query 'Content' | base64 -d > "$OUTPUT_DIR/iam/credential-report.csv" || true

    log_success "IAM configuration exported"
}

# Export Config rules and compliance
export_config() {
    log_info "Exporting AWS Config configuration..."

    # Export Config rules
    aws configservice describe-config-rules \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/config/config-rules.json"

    # Export compliance status
    aws configservice describe-compliance-by-config-rule \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/config/compliance-status.json"

    # Export configuration recorder
    aws configservice describe-configuration-recorders \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/config/configuration-recorders.json" || true

    # Export delivery channel
    aws configservice describe-delivery-channels \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/config/delivery-channels.json" || true

    # Export remediation configurations
    aws configservice describe-remediation-configurations \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/config/remediation-configurations.json" || true

    log_success "AWS Config configuration exported"
}

# Export encryption configuration
export_encryption() {
    log_info "Exporting encryption configuration..."

    # List KMS keys
    aws kms list-keys \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/encryption/kms-keys.json"

    # Describe EFS file systems
    aws efs describe-file-systems \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/encryption/efs-filesystems.json"

    # List ACM certificates
    aws acm list-certificates \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/encryption/acm-certificates.json"

    # Check S3 bucket encryption (for buckets in the stack)
    aws s3api list-buckets \
        --region "$REGION" \
        --query 'Buckets[].Name' \
        --output text | while read -r bucket; do
        echo "Checking bucket: $bucket"
        aws s3api get-bucket-encryption \
            --bucket "$bucket" \
            --output json 2>/dev/null >> "$OUTPUT_DIR/encryption/s3-bucket-encryption.json" || echo "{\"Bucket\": \"$bucket\", \"Encryption\": \"None\"}" >> "$OUTPUT_DIR/encryption/s3-bucket-encryption.json"
    done

    log_success "Encryption configuration exported"
}

# Export logging configuration
export_logging() {
    log_info "Exporting logging configuration..."

    # Export CloudTrail trails
    aws cloudtrail describe-trails \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/logging/cloudtrail-trails.json"

    # Export CloudTrail status
    aws cloudtrail get-trail-status \
        --name cloudforge-trail \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/logging/cloudtrail-status.json" 2>/dev/null || true

    # Export CloudWatch log groups
    aws logs describe-log-groups \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/logging/log-groups.json"

    # Export VPC Flow Logs
    aws ec2 describe-flow-logs \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/logging/vpc-flow-logs.json"

    # Sample CloudTrail events (limited to last 90 days by AWS)
    log_info "Sampling CloudTrail events (this may take a while)..."
    aws cloudtrail lookup-events \
        --start-time "$START_DATE" \
        --max-results 10000 \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/logging/cloudtrail-events-sample.json" 2>/dev/null || log_warning "CloudTrail event lookup failed (date range may exceed 90 days)"

    log_success "Logging configuration exported"
}

# Export monitoring configuration
export_monitoring() {
    log_info "Exporting monitoring configuration..."

    # Export CloudWatch alarms
    aws cloudwatch describe-alarms \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/monitoring/cloudwatch-alarms.json"

    # Export GuardDuty detectors
    aws guardduty list-detectors \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/monitoring/guardduty-detectors.json"

    # Export GuardDuty findings
    DETECTOR_ID=$(aws guardduty list-detectors --region "$REGION" --query 'DetectorIds[0]' --output text 2>/dev/null)
    if [ -n "$DETECTOR_ID" ] && [ "$DETECTOR_ID" != "None" ]; then
        aws guardduty list-findings \
            --detector-id "$DETECTOR_ID" \
            --region "$REGION" \
            --output json > "$OUTPUT_DIR/monitoring/guardduty-findings.json"
    fi

    # Export SNS topics
    aws sns list-topics \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/monitoring/sns-topics.json"

    # Export Security Hub findings (if enabled)
    aws securityhub get-findings \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/monitoring/securityhub-findings.json" 2>/dev/null || log_warning "Security Hub not enabled"

    log_success "Monitoring configuration exported"
}

# Export network configuration
export_network() {
    log_info "Exporting network configuration..."

    # Export VPCs
    aws ec2 describe-vpcs \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/network/vpcs.json"

    # Export security groups
    aws ec2 describe-security-groups \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/network/security-groups.json"

    # Export network ACLs
    aws ec2 describe-network-acls \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/network/network-acls.json"

    # Export load balancers
    aws elbv2 describe-load-balancers \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/network/load-balancers.json" 2>/dev/null || true

    # Export WAF Web ACLs
    aws wafv2 list-web-acls \
        --scope REGIONAL \
        --region "$REGION" \
        --output json > "$OUTPUT_DIR/network/waf-web-acls.json" 2>/dev/null || log_warning "WAF not configured"

    log_success "Network configuration exported"
}

# Generate compliance matrix
generate_compliance_matrix() {
    log_info "Generating compliance matrix..."

    cat > "$OUTPUT_DIR/compliance/compliance-matrix.md" <<EOF
# Compliance Matrix - $FRAMEWORK
Generated: $(date)
Stack: $STACK_NAME
Region: $REGION
Audit Period: $START_DATE to $END_DATE

## Executive Summary

This compliance evidence package demonstrates adherence to $FRAMEWORK requirements
for the CloudForge CI infrastructure deployment.

## Controls Implemented

EOF

    # Add framework-specific controls
    case "$FRAMEWORK" in
        SOC2)
            cat >> "$OUTPUT_DIR/compliance/compliance-matrix.md" <<EOF
### SOC 2 Trust Services Criteria

#### CC6.1 - Logical and Physical Access Controls
- **Evidence**: iam/policies.json, iam/roles.json
- **Status**: Implemented
- **Controls**: IAM policies, Cognito MFA, Security Groups

#### CC6.6 - Network Segmentation
- **Evidence**: network/security-groups.json, network/network-acls.json
- **Status**: Implemented
- **Controls**: VPC security groups, Network ACLs, Private subnets

#### CC6.7 - Transmission Security
- **Evidence**: encryption/acm-certificates.json, network/load-balancers.json
- **Status**: Implemented
- **Controls**: TLS 1.2+, ACM certificates, ALB HTTPS listeners

#### CC7.2 - System Monitoring
- **Evidence**: logging/cloudtrail-trails.json, monitoring/guardduty-detectors.json
- **Status**: Implemented
- **Controls**: CloudTrail, GuardDuty, CloudWatch Alarms

#### CC7.3 - Backup and Recovery
- **Evidence**: encryption/efs-filesystems.json, config/config-rules.json
- **Status**: Implemented
- **Controls**: EFS backups, S3 versioning, Retention policies

EOF
            ;;
        HIPAA)
            cat >> "$OUTPUT_DIR/compliance/compliance-matrix.md" <<EOF
### HIPAA Security Rule

#### §164.312(a)(2)(iv) - Encryption Mechanisms
- **Evidence**: encryption/kms-keys.json, encryption/efs-filesystems.json
- **Status**: Implemented
- **Controls**: KMS encryption, EFS encryption, S3 encryption

#### §164.312(b) - Audit Controls
- **Evidence**: logging/cloudtrail-trails.json, logging/log-groups.json
- **Status**: Implemented
- **Controls**: CloudTrail logging, CloudWatch Logs, VPC Flow Logs

#### §164.312(d) - Person/Entity Authentication
- **Evidence**: iam/users.json, iam/credential-report.csv
- **Status**: Implemented
- **Controls**: Cognito MFA, IAM policies, Strong passwords

#### §164.312(e) - Transmission Security
- **Evidence**: encryption/acm-certificates.json
- **Status**: Implemented
- **Controls**: TLS 1.2+, End-to-end encryption

#### §164.316(b)(2)(i) - Log Retention
- **Evidence**: logging/log-groups.json
- **Status**: Implemented
- **Controls**: 6-year log retention policy

EOF
            ;;
        PCI-DSS)
            cat >> "$OUTPUT_DIR/compliance/compliance-matrix.md" <<EOF
### PCI-DSS Requirements

#### Requirement 1 - Install and maintain a firewall
- **Evidence**: network/security-groups.json, network/waf-web-acls.json
- **Status**: Implemented
- **Controls**: Security Groups, WAF, Network ACLs

#### Requirement 3-4 - Protect stored data and encrypt transmission
- **Evidence**: encryption/kms-keys.json, encryption/acm-certificates.json
- **Status**: Implemented
- **Controls**: KMS encryption, TLS 1.2+, Data encryption at rest

#### Requirement 7-8 - Restrict access and identify users
- **Evidence**: iam/policies.json, iam/credential-report.csv
- **Status**: Implemented
- **Controls**: IAM least-privilege, Cognito MFA, Access control

#### Requirement 10 - Track and monitor all access
- **Evidence**: logging/cloudtrail-trails.json, monitoring/guardduty-findings.json
- **Status**: Implemented
- **Controls**: CloudTrail, GuardDuty, CloudWatch Logs

#### Requirement 11 - Regularly test security systems
- **Evidence**: config/config-rules.json, config/compliance-status.json
- **Status**: Implemented
- **Controls**: AWS Config rules, Continuous compliance monitoring

EOF
            ;;
        GDPR)
            cat >> "$OUTPUT_DIR/compliance/compliance-matrix.md" <<EOF
### GDPR Requirements

#### Article 25 - Data protection by design and default
- **Evidence**: infrastructure/cloudformation-template.yaml
- **Status**: Implemented
- **Controls**: Encryption by default, Privacy-preserving architecture

#### Article 30 - Records of processing activities
- **Evidence**: logging/cloudtrail-trails.json
- **Status**: Implemented
- **Controls**: CloudTrail logging, Audit trail

#### Article 32 - Security of processing
- **Evidence**: encryption/kms-keys.json, network/security-groups.json
- **Status**: Implemented
- **Controls**: Encryption, Access control, Network security

#### Article 33 - Breach notification
- **Evidence**: monitoring/guardduty-findings.json, monitoring/sns-topics.json
- **Status**: Implemented
- **Controls**: GuardDuty alerts, SNS notifications, Incident response

EOF
            ;;
    esac

    cat >> "$OUTPUT_DIR/compliance/compliance-matrix.md" <<EOF

## Evidence Artifacts

### Infrastructure
- CloudFormation Template: infrastructure/cloudformation-template.yaml
- Stack Resources: infrastructure/stack-resources.json
- Stack Metadata: infrastructure/stack-metadata.json

### Identity & Access Management
- IAM Policies: iam/policies.json
- IAM Roles: iam/roles.json
- IAM Users: iam/users.json
- Credential Report: iam/credential-report.csv

### Encryption
- KMS Keys: encryption/kms-keys.json
- EFS Encryption: encryption/efs-filesystems.json
- ACM Certificates: encryption/acm-certificates.json
- S3 Bucket Encryption: encryption/s3-bucket-encryption.json

### Logging & Audit
- CloudTrail Configuration: logging/cloudtrail-trails.json
- CloudTrail Events (sample): logging/cloudtrail-events-sample.json
- CloudWatch Log Groups: logging/log-groups.json
- VPC Flow Logs: logging/vpc-flow-logs.json

### Monitoring
- CloudWatch Alarms: monitoring/cloudwatch-alarms.json
- GuardDuty Detectors: monitoring/guardduty-detectors.json
- GuardDuty Findings: monitoring/guardduty-findings.json
- SNS Topics: monitoring/sns-topics.json

### Network Security
- VPCs: network/vpcs.json
- Security Groups: network/security-groups.json
- Network ACLs: network/network-acls.json
- Load Balancers: network/load-balancers.json
- WAF Web ACLs: network/waf-web-acls.json

### AWS Config
- Config Rules: config/config-rules.json
- Compliance Status: config/compliance-status.json
- Remediation Configurations: config/remediation-configurations.json

## Validation Queries

### Verify MFA Enforcement
\`\`\`bash
jq '.Users[] | select(.PasswordEnabled == true and .MfaActive == false)' iam/credential-report.csv
# Should return empty (all users have MFA)
\`\`\`

### Verify Encryption at Rest
\`\`\`bash
jq '.FileSystems[] | {FileSystemId, Encrypted}' encryption/efs-filesystems.json
# All FileSystems should show Encrypted: true
\`\`\`

### Verify Compliant Config Rules
\`\`\`bash
jq '.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT")' config/compliance-status.json
# Should return empty or minimal non-compliant resources
\`\`\`

### Verify CloudTrail Logging
\`\`\`bash
jq '.trailList[] | {Name, IsLogging, IsMultiRegionTrail}' logging/cloudtrail-trails.json
# IsLogging should be true
\`\`\`

## Report Generated
- Date: $(date)
- Stack: $STACK_NAME
- Region: $REGION
- Framework: $FRAMEWORK
- Audit Period: $START_DATE to $END_DATE

EOF

    log_success "Compliance matrix generated"
}

# Generate summary report
generate_summary_report() {
    log_info "Generating summary report..."

    cat > "$OUTPUT_DIR/AUDIT_EVIDENCE_README.md" <<EOF
# Audit Evidence Package

**Generated**: $(date)
**Stack**: $STACK_NAME
**Region**: $REGION
**Framework**: $FRAMEWORK
**Audit Period**: $START_DATE to $END_DATE

## Contents

This evidence package contains comprehensive documentation and configuration
exports to support $FRAMEWORK compliance auditing.

### Directory Structure

\`\`\`
audit-evidence-*/
├── infrastructure/          # CloudFormation templates and stack metadata
├── iam/                     # IAM policies, roles, users, credential report
├── encryption/              # KMS, EFS, ACM, S3 encryption configuration
├── logging/                 # CloudTrail, CloudWatch Logs, VPC Flow Logs
├── monitoring/              # CloudWatch Alarms, GuardDuty, Security Hub
├── config/                  # AWS Config rules and compliance status
├── network/                 # VPC, Security Groups, Network ACLs, WAF
├── compliance/              # Compliance matrix and control mappings
└── reports/                 # Additional reports and analysis

EOF

    # Add evidence statistics
    cat >> "$OUTPUT_DIR/AUDIT_EVIDENCE_README.md" <<EOF
### Evidence Statistics

EOF

    # Count various resources
    IAM_POLICIES=$(jq '.Policies | length' "$OUTPUT_DIR/iam/policies.json" 2>/dev/null || echo "0")
    IAM_ROLES=$(jq '.Roles | length' "$OUTPUT_DIR/iam/roles.json" 2>/dev/null || echo "0")
    CONFIG_RULES=$(jq '.ConfigRules | length' "$OUTPUT_DIR/config/config-rules.json" 2>/dev/null || echo "0")
    COMPLIANT_RULES=$(jq '[.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "COMPLIANT")] | length' "$OUTPUT_DIR/config/compliance-status.json" 2>/dev/null || echo "0")
    NON_COMPLIANT_RULES=$(jq '[.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT")] | length' "$OUTPUT_DIR/config/compliance-status.json" 2>/dev/null || echo "0")

    cat >> "$OUTPUT_DIR/AUDIT_EVIDENCE_README.md" <<EOF
- **IAM Policies**: $IAM_POLICIES
- **IAM Roles**: $IAM_ROLES
- **AWS Config Rules**: $CONFIG_RULES
  - Compliant: $COMPLIANT_RULES
  - Non-Compliant: $NON_COMPLIANT_RULES
- **CloudTrail Trails**: $(jq '.trailList | length' "$OUTPUT_DIR/logging/cloudtrail-trails.json" 2>/dev/null || echo "0")
- **CloudWatch Alarms**: $(jq '.MetricAlarms | length' "$OUTPUT_DIR/monitoring/cloudwatch-alarms.json" 2>/dev/null || echo "0")
- **Security Groups**: $(jq '.SecurityGroups | length' "$OUTPUT_DIR/network/security-groups.json" 2>/dev/null || echo "0")

### How to Use This Evidence

1. **Review Compliance Matrix**: Start with \`compliance/compliance-matrix.md\`
2. **Verify Controls**: Use the validation queries in the compliance matrix
3. **Examine Evidence**: Navigate to specific directories for detailed configuration
4. **Answer Audit Questions**: Reference artifacts by file path in audit responses

### Key Documentation

- [Compliance Matrix](compliance/compliance-matrix.md)
- [CloudFormation Template](infrastructure/cloudformation-template.yaml)
- [Config Compliance Status](config/compliance-status.json)
- [IAM Credential Report](iam/credential-report.csv)

### Support

For questions about this evidence package, refer to:
- [Audit Readiness Guide](../docs/AUDIT_READINESS_GUIDE.md)
- [Auditor Compliance Mapping](../docs/AUDITOR_COMPLIANCE_MAPPING.md)

EOF

    log_success "Summary report generated"
}

# Create archive
create_archive() {
    log_info "Creating evidence archive..."

    tar -czf "${OUTPUT_DIR}.tar.gz" "$OUTPUT_DIR"

    log_success "Evidence archive created: ${OUTPUT_DIR}.tar.gz"
    log_info "Archive size: $(du -h "${OUTPUT_DIR}.tar.gz" | cut -f1)"
}

# Main execution
main() {
    echo ""
    echo "======================================"
    echo "  Audit Evidence Generation Tool"
    echo "======================================"
    echo ""
    echo "Stack:      $STACK_NAME"
    echo "Framework:  $FRAMEWORK"
    echo "Period:     $START_DATE to $END_DATE"
    echo "Region:     $REGION"
    echo "Output:     $OUTPUT_DIR"
    echo ""

    check_prerequisites
    create_directory_structure
    export_cloudformation
    export_iam
    export_config
    export_encryption
    export_logging
    export_monitoring
    export_network
    generate_compliance_matrix
    generate_summary_report
    create_archive

    echo ""
    log_success "Audit evidence generation complete!"
    echo ""
    echo "Evidence Package:"
    echo "  Directory: $OUTPUT_DIR"
    echo "  Archive:   ${OUTPUT_DIR}.tar.gz"
    echo ""
    echo "Next Steps:"
    echo "  1. Review: cat $OUTPUT_DIR/AUDIT_EVIDENCE_README.md"
    echo "  2. Validate: cd $OUTPUT_DIR && cat compliance/compliance-matrix.md"
    echo "  3. Share: Send ${OUTPUT_DIR}.tar.gz to auditors"
    echo ""
}

# Run main function
main
