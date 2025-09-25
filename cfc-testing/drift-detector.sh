#!/bin/bash

# Drift Detection System for CloudForge Core
# Compares resource validation results between builds to detect configuration drift

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
BASELINE_DIR="$VALIDATION_DIR/baseline"
CURRENT_DIR="$VALIDATION_DIR/current"
DRIFT_REPORT_DIR="$VALIDATION_DIR/drift-reports"

# Create directories
mkdir -p "$BASELINE_DIR" "$CURRENT_DIR" "$DRIFT_REPORT_DIR"

echo -e "${BLUE}🔍 CloudForge Core Drift Detection System${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Function to create baseline from current validation
create_baseline() {
    echo -e "${CYAN}📋 Creating new baseline from current validation results...${NC}"
    
    if [ ! -d "$CURRENT_DIR" ] || [ -z "$(ls -A $CURRENT_DIR 2>/dev/null)" ]; then
        echo -e "${RED}❌ No current validation results found. Run comprehensive validation first.${NC}"
        exit 1
    fi
    
    # Copy current results to baseline
    cp -r "$CURRENT_DIR"/* "$BASELINE_DIR/"
    
    # Create baseline metadata
    cat > "$BASELINE_DIR/baseline-metadata.json" << EOF
{
    "created_at": "$(date -Iseconds)",
    "git_commit": "$(git rev-parse HEAD 2>/dev/null || echo 'unknown')",
    "git_branch": "$(git branch --show-current 2>/dev/null || echo 'unknown')",
    "version": "$(grep -o '<version>[^<]*' ../pom.xml | sed 's/<version>//' | head -1)",
    "validation_files": $(ls "$BASELINE_DIR"/*.json 2>/dev/null | wc -l),
    "description": "Baseline created from comprehensive validation run"
}
EOF
    
    echo -e "${GREEN}✅ Baseline created successfully${NC}"
    echo "Files: $(ls "$BASELINE_DIR" | wc -l)"
    echo "Baseline metadata saved to: $BASELINE_DIR/baseline-metadata.json"
}

# Function to run drift detection
detect_drift() {
    echo -e "${PURPLE}🔍 Detecting configuration drift...${NC}"
    
    if [ ! -d "$BASELINE_DIR" ] || [ -z "$(ls -A $BASELINE_DIR 2>/dev/null)" ]; then
        echo -e "${RED}❌ No baseline found. Create baseline first.${NC}"
        exit 1
    fi
    
    if [ ! -d "$CURRENT_DIR" ] || [ -z "$(ls -A $CURRENT_DIR 2>/dev/null)" ]; then
        echo -e "${RED}❌ No current validation results found. Run comprehensive validation first.${NC}"
        exit 1
    fi
    
    local timestamp=$(date +"%Y%m%d-%H%M%S")
    local drift_report="$DRIFT_REPORT_DIR/drift-report-$timestamp.json"
    local drift_summary="$DRIFT_REPORT_DIR/drift-summary-$timestamp.txt"
    
    echo "Comparing baseline vs current validation results..."
    echo "Baseline: $BASELINE_DIR"
    echo "Current:  $CURRENT_DIR"
    echo ""
    
    # Initialize drift report
    cat > "$drift_report" << EOF
{
    "drift_detection": {
        "timestamp": "$(date -Iseconds)",
        "baseline_metadata": $(cat "$BASELINE_DIR/baseline-metadata.json" 2>/dev/null || echo '{}'),
        "current_git_commit": "$(git rev-parse HEAD 2>/dev/null || echo 'unknown')",
        "current_version": "$(grep -o '<version>[^<]*' ../pom.xml | sed 's/<version>//' | head -1)"
    },
    "configuration_changes": {},
    "summary": {
        "total_configs_baseline": 0,
        "total_configs_current": 0,
        "configs_with_drift": 0,
        "new_configs": 0,
        "removed_configs": 0,
        "resource_count_changes": 0
    }
}
EOF
    
    # Initialize summary
    echo "CloudForge Core - Configuration Drift Report" > "$drift_summary"
    echo "=============================================" >> "$drift_summary"
    echo "Generated: $(date)" >> "$drift_summary"
    echo "" >> "$drift_summary"
    
    local total_baseline=0
    local total_current=0
    local configs_with_drift=0
    local new_configs=0
    local removed_configs=0
    local resource_changes=0
    
    # Get all configuration files
    local all_configs=$(ls "$BASELINE_DIR"/*.json "$CURRENT_DIR"/*.json 2>/dev/null | grep -v metadata | xargs -n1 basename | sort | uniq)
    
    echo "📊 Analyzing configurations..." >> "$drift_summary"
    echo "" >> "$drift_summary"
    
    for config_file in $all_configs; do
        local config_name=$(echo "$config_file" | sed 's/-validation.json$//')
        local baseline_file="$BASELINE_DIR/$config_file"
        local current_file="$CURRENT_DIR/$config_file"
        
        if [[ ! -f "$baseline_file" ]]; then
            # New configuration
            echo -e "${GREEN}📋 NEW: $config_name${NC}"
            echo "NEW: $config_name" >> "$drift_summary"
            new_configs=$((new_configs + 1))
            total_current=$((total_current + 1))
            
        elif [[ ! -f "$current_file" ]]; then
            # Removed configuration
            echo -e "${RED}📋 REMOVED: $config_name${NC}"
            echo "REMOVED: $config_name" >> "$drift_summary"
            removed_configs=$((removed_configs + 1))
            total_baseline=$((total_baseline + 1))
            
        else
            # Compare configurations
            total_baseline=$((total_baseline + 1))
            total_current=$((total_current + 1))
            
            local baseline_status=$(jq -r '.summary.status // "UNKNOWN"' "$baseline_file" 2>/dev/null)
            local current_status=$(jq -r '.summary.status // "UNKNOWN"' "$current_file" 2>/dev/null)
            local baseline_resources=$(jq -r '.summary.missing_resources // ""' "$baseline_file" 2>/dev/null)
            local current_resources=$(jq -r '.summary.missing_resources // ""' "$current_file" 2>/dev/null)
            local baseline_count=$(jq -r '.summary.resource_count // 0' "$baseline_file" 2>/dev/null)
            local current_count=$(jq -r '.summary.resource_count // 0' "$current_file" 2>/dev/null)
            
            local has_drift=false
            local drift_details=""
            
            # Check for status changes
            if [[ "$baseline_status" != "$current_status" ]]; then
                has_drift=true
                drift_details+="Status changed: $baseline_status → $current_status; "
            fi
            
            # Check for missing resource changes
            if [[ "$baseline_resources" != "$current_resources" ]]; then
                has_drift=true
                if [[ -z "$baseline_resources" && -n "$current_resources" ]]; then
                    drift_details+="New missing resources: $current_resources; "
                elif [[ -n "$baseline_resources" && -z "$current_resources" ]]; then
                    drift_details+="Fixed missing resources: $baseline_resources; "
                else
                    drift_details+="Missing resources changed: $baseline_resources → $current_resources; "
                fi
            fi
            
            # Check for resource count changes
            if [[ "$baseline_count" != "$current_count" ]]; then
                has_drift=true
                resource_changes=$((resource_changes + 1))
                drift_details+="Resource count changed: $baseline_count → $current_count; "
            fi
            
            if [[ "$has_drift" == true ]]; then
                echo -e "${YELLOW}📋 DRIFT: $config_name${NC}"
                echo "  $drift_details"
                echo "DRIFT: $config_name - $drift_details" >> "$drift_summary"
                configs_with_drift=$((configs_with_drift + 1))
            else
                echo -e "${GREEN}📋 STABLE: $config_name${NC}"
            fi
        fi
    done
    
    # Update summary in JSON
    local temp_file=$(mktemp)
    jq --argjson total_baseline "$total_baseline" \
       --argjson total_current "$total_current" \
       --argjson configs_with_drift "$configs_with_drift" \
       --argjson new_configs "$new_configs" \
       --argjson removed_configs "$removed_configs" \
       --argjson resource_changes "$resource_changes" \
       '.summary.total_configs_baseline = $total_baseline |
        .summary.total_configs_current = $total_current |
        .summary.configs_with_drift = $configs_with_drift |
        .summary.new_configs = $new_configs |
        .summary.removed_configs = $removed_configs |
        .summary.resource_count_changes = $resource_changes' \
       "$drift_report" > "$temp_file" && mv "$temp_file" "$drift_report"
    
    # Add summary to text report
    echo "" >> "$drift_summary"
    echo "📈 Summary:" >> "$drift_summary"
    echo "==========" >> "$drift_summary"
    echo "Total configurations (baseline): $total_baseline" >> "$drift_summary"
    echo "Total configurations (current):  $total_current" >> "$drift_summary"
    echo "Configurations with drift:       $configs_with_drift" >> "$drift_summary"
    echo "New configurations:              $new_configs" >> "$drift_summary"
    echo "Removed configurations:          $removed_configs" >> "$drift_summary"
    echo "Resource count changes:          $resource_changes" >> "$drift_summary"
    echo "" >> "$drift_summary"
    
    if [[ $configs_with_drift -eq 0 && $new_configs -eq 0 && $removed_configs -eq 0 ]]; then
        echo -e "${GREEN}🎉 No configuration drift detected!${NC}" >> "$drift_summary"
        echo -e "${GREEN}🎉 No configuration drift detected!${NC}"
    else
        echo -e "${YELLOW}⚠️  Configuration drift detected!${NC}" >> "$drift_summary"
        echo -e "${YELLOW}⚠️  Configuration drift detected!${NC}"
        
        # Provide recommendations
        echo "" >> "$drift_summary"
        echo "🔧 Recommendations:" >> "$drift_summary"
        echo "==================" >> "$drift_summary"
        
        if [[ $configs_with_drift -gt 0 ]]; then
            echo "- Review configurations with drift to ensure changes are intentional" >> "$drift_summary"
            echo "- Check factory files for recent modifications" >> "$drift_summary"
        fi
        
        if [[ $new_configs -gt 0 ]]; then
            echo "- Validate new configurations work as expected" >> "$drift_summary"
            echo "- Update documentation if new features were added" >> "$drift_summary"
        fi
        
        if [[ $removed_configs -gt 0 ]]; then
            echo "- Confirm configuration removals are intentional" >> "$drift_summary"
            echo "- Update tests to remove obsolete test cases" >> "$drift_summary"
        fi
        
        if [[ $resource_changes -gt 0 ]]; then
            echo "- Investigate resource count changes for performance impact" >> "$drift_summary"
            echo "- Review factory modifications that may have affected resource creation" >> "$drift_summary"
        fi
    fi
    
    echo ""
    echo -e "${GREEN}📋 Drift detection completed${NC}"
    echo "Report saved to: $drift_report"
    echo "Summary saved to: $drift_summary"
    
    return $((configs_with_drift + new_configs + removed_configs))
}

# Function to generate drift history
generate_drift_history() {
    echo -e "${CYAN}📈 Generating drift history...${NC}"
    
    local history_file="$DRIFT_REPORT_DIR/drift-history.html"
    
    cat > "$history_file" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>CloudForge Core - Drift History</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f0f0; padding: 15px; border-radius: 5px; }
        .report { margin: 15px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .no-drift { background: #d4edda; }
        .drift-detected { background: #fff3cd; }
        .timestamp { color: #666; font-size: 0.9em; }
        .summary { font-weight: bold; }
    </style>
</head>
<body>
    <div class="header">
        <h1>CloudForge Core - Configuration Drift History</h1>
        <p>Track configuration drift over time</p>
    </div>
    
    <div id="reports">
EOF
    
    # Add each drift report
    for report_file in $(ls "$DRIFT_REPORT_DIR"/drift-summary-*.txt 2>/dev/null | sort -r); do
        local report_name=$(basename "$report_file" .txt)
        local timestamp=$(echo "$report_name" | sed 's/drift-summary-//')
        
        echo "        <div class=\"report\">" >> "$history_file"
        echo "            <h3>$timestamp</h3>" >> "$history_file"
        echo "            <div class=\"timestamp\">$(head -3 "$report_file" | tail -1)</div>" >> "$history_file"
        echo "            <div class=\"summary\">" >> "$history_file"
        
        if grep -q "No configuration drift detected" "$report_file"; then
            echo "                <span class=\"no-drift\">✅ No drift detected</span>" >> "$history_file"
        else
            echo "                <span class=\"drift-detected\">⚠️ Drift detected</span>" >> "$history_file"
        fi
        
        echo "            </div>" >> "$history_file"
        echo "            <pre>$(cat "$report_file")</pre>" >> "$history_file"
        echo "        </div>" >> "$history_file"
    done
    
    cat >> "$history_file" << 'EOF'
    </div>
</body>
</html>
EOF
    
    echo -e "${GREEN}📈 Drift history generated: $history_file${NC}"
}

# Function to move current validation to archive
archive_current_validation() {
    local timestamp=$(date +"%Y%m%d-%H%M%S")
    local archive_dir="$VALIDATION_DIR/archive-$timestamp"
    
    if [ -d "$CURRENT_DIR" ] && [ "$(ls -A $CURRENT_DIR 2>/dev/null)" ]; then
        echo -e "${CYAN}📦 Archiving current validation results...${NC}"
        mv "$CURRENT_DIR" "$archive_dir"
        mkdir -p "$CURRENT_DIR"
        echo "Archived to: $archive_dir"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  baseline    Create new baseline from current validation results"
    echo "  detect      Detect drift between baseline and current results"
    echo "  history     Generate drift history report"
    echo "  archive     Archive current validation results"
    echo "  help        Show this help message"
    echo ""
    echo "Workflow:"
    echo "  1. Run comprehensive-resource-validator.sh to generate current results"
    echo "  2. Run '$0 baseline' to create initial baseline"
    echo "  3. After code changes, run comprehensive-resource-validator.sh again"
    echo "  4. Run '$0 detect' to detect configuration drift"
    echo "  5. Run '$0 history' to see drift over time"
}

# Main execution
case "${1:-detect}" in
    "baseline")
        create_baseline
        ;;
    "detect")
        detect_drift
        ;;
    "history")
        generate_drift_history
        ;;
    "archive")
        archive_current_validation
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
