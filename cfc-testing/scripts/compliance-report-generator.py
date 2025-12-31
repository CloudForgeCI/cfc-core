#!/usr/bin/env python3
"""
Compliance Report Generator for CloudForge Core

Runs compliance validation tests and generates an interactive HTML dashboard
showing multi-layer validation results (cdk-nag, FrameworkRules, cfn-guard, AWS Config).
"""

import html as html_module
import json
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

# Use defusedxml to prevent XML vulnerabilities (XXE, billion laughs, etc.)
try:
    import defusedxml.ElementTree as ET
except ImportError:
    # Fallback to standard library if defusedxml is not available
    # This should trigger a warning in production environments
    import xml.etree.ElementTree as ET
    print("⚠️  WARNING: defusedxml not found. Using standard xml.etree.ElementTree.", file=sys.stderr)
    print("⚠️  Install defusedxml for secure XML parsing: pip install defusedxml", file=sys.stderr)


class ComplianceTestResult:
    """Represents the result of a single compliance test configuration."""

    def __init__(self, config_name: str, framework: str, runtime: str, network_mode: str):
        self.config_name = config_name
        self.framework = framework
        self.runtime = runtime
        self.network_mode = network_mode
        self.status = "pending"  # pending, passed, failed
        self.duration = 0.0

        # Layer validation results
        self.cdk_nag_status = "unknown"  # passed, failed, skipped, unknown
        self.cdk_nag_packs_applied = 0
        self.cdk_nag_violations = []

        self.framework_rules_status = "unknown"
        self.framework_rules_violations = []
        self.framework_rules_known_gaps = []

        self.cfn_guard_status = "unknown"
        self.cfn_guard_violations = []

        self.aws_config_status = "deployed"  # Always deployed at runtime

        self.error_message = None
        self.deployment_context = None
        self.is_negative_test = False  # True for tests expected to fail
        self.rejection_layers = None  # Which layers rejected this config (for negative tests)
        self._current_layer = None  # Track which layer violations belong to during parsing

        # CloudFormation template paths (for download links)
        self.cfn_template_json = None
        self.cfn_template_yaml = None

        # Advisory tracking (warnings that don't cause failure)
        self.has_advisories = False
        self.advisory_layers = []  # List of layers with advisories: "L1", "L2", "L3"
        self.cdk_nag_warnings = []  # Warnings (not errors) from cdk-nag
        self.framework_rules_advisories = []  # Advisories from FrameworkRules
        self.cfn_guard_warnings = []  # Warnings from cfn-guard

        # Installed rules tracking
        self.installed_rules = []  # List of FrameworkRules installed for this test
        self.aws_config_rules = []  # List of AWS Config rules that would be deployed


class ComplianceReportGenerator:
    """Generates compliance validation reports by running tests and parsing results."""

    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.cloudforge_api_dir = project_root / "cloudforge-api"
        self.output_dir = project_root / "cfc-testing" / "scripts" / "validation-results"
        self.results: List[ComplianceTestResult] = []
        self.incremental_results_file = self.output_dir / "compliance-results-incremental.jsonl"

    def run_compliance_tests(self) -> bool:
        """Run split test methods sequentially to avoid JSII memory issues."""
        print("🧪 Running compliance validation tests...")
        print(f"   Project root: {self.project_root}")
        print(f"   Working directory: {self.cloudforge_api_dir}")

        if self.incremental_results_file.exists():
            self.incremental_results_file.unlink()

        test_methods = self._discover_split_test_methods()
        if not test_methods:
            print("   ❌ No split test methods discovered!")
            return False

        total_methods = len(test_methods)
        print(f"   📋 Discovered {total_methods} split test methods")
        print(f"   🔄 Running tests sequentially...")

        output_file = self.output_dir / "compliance-test-output.log"
        print(f"   📝 Streaming output to: {output_file}")
        print(f"   📊 Incremental results: {self.incremental_results_file}")

        process = None  # Initialize to prevent UnboundLocalError in exception handler
        try:
            with open(output_file, 'w') as log_file:
                all_passed = True

                for idx, method in enumerate(test_methods, 1):
                    print(f"   [{idx}/{total_methods}] Running {method}...")

                    cmd = [
                        "mvn", "test",
                        f"-Dtest=TruthTableValidationTest#{method}",
                        "--batch-mode",
                        "-Dsurefire.useSystemClassLoader=false",  # Prevent classloader issues
                        "-Dsurefire.useFile=true",  # Force XML output
                        "-Dsurefire.trimStackTrace=false",  # Full stack traces in XML
                        "-Dsurefire.redirectTestOutputToFile=false",  # Keep output in console AND XML
                        "-Dsurefire.reportFormat=plain",  # Plain text for better parsing
                        "-Dsurefire.printSummary=true"  # Print test summary
                    ]

                    process = subprocess.Popen(
                        cmd,
                        cwd=str(self.cloudforge_api_dir),
                        stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT,
                        text=True,
                        bufsize=0,  # Unbuffered for real-time output
                        env={**os.environ, 'MAVEN_OPTS': '-Xmx2048m'}  # Increase memory
                    )

                    current_test = None
                    for line in process.stdout:
                        log_file.write(line)
                        log_file.flush()

                        result = self._parse_line_incremental(line, current_test)
                        if result:
                            current_test = result

                    process.wait(timeout=300)  # 5 minute timeout per test method

                    if process.returncode != 0:
                        print(f"      ⚠️  {method} failed (exit code {process.returncode})")
                        all_passed = False
                    else:
                        print(f"      ✓ {method} passed")

                    # Save JUnit XML with unique name before next test overwrites it
                    xml_file = self.cloudforge_api_dir / "target" / "surefire-reports" / \
                              "TEST-com.cloudforgeci.api.integration.deployment.TruthTableValidationTest.xml"
                    if xml_file.exists():
                        saved_xml = self.output_dir / f"junit-xml-{method}.xml"
                        import shutil
                        shutil.copy(xml_file, saved_xml)
                        print(f"      📁 Saved JUnit XML: {saved_xml.name}")

                    # Kill lingering JSII processes between tests
                    # Use more specific pattern to avoid matching unintended processes
                    try:
                        # Use full command pattern for safer matching
                        result = subprocess.run(
                            ["pkill", "-9", "-f", "jsii-runtime.Runtime.*jsii-java-runtime"],
                            capture_output=True,
                            timeout=2,
                            text=True
                        )
                        if result.returncode == 0:
                            print(f"      🧹 Cleaned up JSII processes")
                    except subprocess.TimeoutExpired:
                        print(f"      ⚠️  Process cleanup timed out", file=sys.stderr)
                    except FileNotFoundError:
                        # pkill not available on this system
                        pass
                    except Exception as e:
                        # Log unexpected errors but don't fail the test run
                        print(f"      ⚠️  Process cleanup failed: {e}", file=sys.stderr)

                print(f"   ✅ All test methods completed")

                # Wait for JUnit XML to be fully written (critical for CI environments)
                print(f"   ⏳ Waiting for JUnit XML to be fully written...")
                import time
                time.sleep(2)  # Give Maven Surefire time to flush XML

                # Parse ALL saved JUnit XML files for timing information
                max_retries = 3
                for attempt in range(max_retries):
                    try:
                        self._parse_all_junit_xml_files()
                        break
                    except Exception as e:
                        if attempt < max_retries - 1:
                            print(f"   ⚠️  XML parse attempt {attempt + 1} failed, retrying...")
                            time.sleep(1)
                        else:
                            print(f"   ❌ Failed to parse JUnit XML after {max_retries} attempts: {e}")
                            raise

                return all_passed

        except subprocess.TimeoutExpired:
            print("   ❌ Test timed out")
            if process:
                process.kill()
            return False
        except Exception as e:
            print(f"   ❌ Failed to run tests: {e}")
            import traceback
            traceback.print_exc()
            return False

    def _discover_split_test_methods(self) -> List[str]:
        """Discover all split test methods from CSV files."""
        matrices_dir = self.project_root / "cloudforge-api/src/test/resources/compliance-matrices"
        if not matrices_dir.exists():
            print(f"   ⚠️  Compliance matrices directory not found: {matrices_dir}")
            return []

        csv_files = sorted(matrices_dir.glob("*.csv"))
        test_methods = []

        for csv_file in csv_files:
            # Convert filename to test method name
            # e.g., "soc2_ec2_pass.csv" -> "testSoc2Ec2Pass"
            name_parts = csv_file.stem.replace(',', '_').replace('-', '_').split('_')
            method_name = "test" + "".join(word.capitalize() for word in name_parts)
            method_name = method_name.replace('__', '_')

            test_methods.append(method_name)

        return test_methods

    def _parse_line_incremental(self, line: str, current_test: Optional[ComplianceTestResult]) -> Optional[ComplianceTestResult]:
        """Parse a single line of output and update results incrementally."""
        # Detect test start
        match = re.search(r'Testing compliance configuration \(CSV\): (\S+) \[(.+?)\]', line)
        if match:
            config_name = match.group(1)
            framework = match.group(2)

            # Extract runtime and network mode from config name
            parts = config_name.split('_')
            # Handle test prefixes: FAIL_, L1_, ADVISORY_, NEGATIVE_, etc.
            # Find the actual runtime (EC2, FARGATE) in the parts
            runtime = "unknown"
            for part in parts:
                if part.upper() in ['EC2', 'FARGATE']:
                    runtime = part.upper()
                    break
            network_mode = parts[-1] if len(parts) > 0 else "unknown"

            current_test = ComplianceTestResult(config_name, framework, runtime, network_mode)
            return current_test

        if current_test:
            # Parse deployment context JSON
            if "DEPLOYMENT_CONTEXT_JSON:" in line:
                match = re.search(r'DEPLOYMENT_CONTEXT_JSON:\s*(\{.*\})', line)
                if match:
                    current_test.deployment_context = match.group(1)

            # Parse installed FrameworkRules (Layer 2)
            # Format: "INFO:   ✓ Rule Name (priority=X)" - use non-emoji pattern for GitHub compatibility
            if "INFO:" in line and "(priority=" in line:
                match = re.search(r'INFO:\s+.?\s*(.+?)\s*\(priority=(-?\d+)\)', line)
                if match:
                    rule_name = match.group(1).strip()
                    if rule_name and rule_name not in [r for r in current_test.installed_rules]:
                        current_test.installed_rules.append(rule_name)

            # Parse Layer 1 (cdk-nag) results
            if "✅ Layer 1 (cdk-nag):" in line:
                current_test.cdk_nag_status = "passed"
                current_test.cdk_nag_packs_applied = 1
                current_test._current_layer = None
            elif "⚠️ Layer 1 (cdk-nag):" in line or "⚠️  Layer 1 (cdk-nag):" in line:
                # Passed with warnings (advisory)
                current_test.cdk_nag_status = "passed"
                current_test.cdk_nag_packs_applied = 1
                current_test.has_advisories = True
                if "L1" not in current_test.advisory_layers:
                    current_test.advisory_layers.append("L1")
                current_test._current_layer = "cdk_nag_warnings"
            elif "❌ Layer 1 (cdk-nag)" in line:
                current_test.cdk_nag_status = "failed"
                current_test._current_layer = "cdk_nag"
            elif "📋 cdk-nag Failure Details:" in line:
                # Start capturing cdk-nag violation details
                current_test._current_layer = "cdk_nag_details"
            elif "📋 cdk-nag Warnings:" in line or "📋 cdk-nag Advisories" in line:
                # Start capturing cdk-nag warning/advisory details
                current_test._current_layer = "cdk_nag_warnings"

            # Detect FrameworkRules validation failure (appears before Layer summary)
            # Format 1: "SEVERE: validation failed with N violations" (Java logger)
            # Format 2: "ValidationError: Validation failed with the following errors:" (CDK)
            if ("validation failed with" in line.lower() and "violations" in line.lower() and "SEVERE:" in line) or \
               ("ValidationError:" in line and "Validation failed" in line):
                # This indicates FrameworkRules is about to report violations
                current_test._current_layer = "framework_rules"
                current_test.framework_rules_status = "failed"

            # Parse Layer 2 (FrameworkRules) results
            if "✅ Layer 2 (FrameworkRules):" in line:
                current_test.framework_rules_status = "passed"
                current_test._current_layer = None
            elif "⚠️ Layer 2 (FrameworkRules):" in line or "⚠️  Layer 2 (FrameworkRules):" in line:
                # Passed with advisories
                current_test.framework_rules_status = "passed"
                current_test.has_advisories = True
                if "L2" not in current_test.advisory_layers:
                    current_test.advisory_layers.append("L2")
                current_test._current_layer = "framework_rules_advisories"
            elif "❌ Layer 2 (FrameworkRules):" in line:
                current_test.framework_rules_status = "failed"
                current_test._current_layer = "framework_rules"
            elif "📋 FrameworkRules Advisories:" in line:
                # Start capturing FrameworkRules advisory details
                current_test._current_layer = "framework_rules_advisories"
            elif "📋 FrameworkRules Violation Details:" in line:
                # Start capturing FrameworkRules violation details
                current_test._current_layer = "framework_rules"
                current_test.framework_rules_status = "failed"
            elif "⏭️  Layer 2 (FrameworkRules): Skipped (no template)" in line:
                current_test.framework_rules_status = "skipped (no template)"
                current_test._current_layer = None
            elif "⏭️  Layer 2 (FrameworkRules): Skipped" in line:
                current_test.framework_rules_status = "skipped"
                current_test._current_layer = None

            # Parse Layer 3 (cfn-guard) results
            if "✅ Layer 3 (cfn-guard):" in line:
                current_test.cfn_guard_status = "passed"
                current_test._current_layer = None
            elif "⚠️ Layer 3 (cfn-guard):" in line or "⚠️  Layer 3 (cfn-guard):" in line:
                # Passed with warnings
                current_test.cfn_guard_status = "passed"
                current_test.has_advisories = True
                if "L3" not in current_test.advisory_layers:
                    current_test.advisory_layers.append("L3")
                current_test._current_layer = "cfn_guard_warnings"
            elif "❌ Layer 3 (cfn-guard):" in line:
                current_test.cfn_guard_status = "failed"
                current_test._current_layer = "cfn_guard"
            elif "📋 cfn-guard Failure Details:" in line:
                # Start capturing cfn-guard violation details
                current_test._current_layer = "cfn_guard_details"
            elif "📋 cfn-guard Warnings:" in line:
                # Start capturing cfn-guard warning details
                current_test._current_layer = "cfn_guard_warnings"
            elif "⏭️  Layer 3 (cfn-guard): Skipped (no template)" in line:
                current_test.cfn_guard_status = "skipped (no template)"
                current_test._current_layer = None
            elif "⛔ Layer 3 (cfn-guard): Blocked" in line:
                current_test.cfn_guard_status = "blocked"
                current_test._current_layer = None
            elif "⏭️  Layer 3 (cfn-guard): Skipped" in line:
                current_test.cfn_guard_status = "skipped"
                current_test._current_layer = None

            # Parse Layer 4 (AWS Config) results - extract rule count
            if "Layer 4 (AWS Config):" in line and "rules would be deployed" in line:
                match = re.search(r'(\d+)\s+rules would be deployed', line)
                if match:
                    current_test.aws_config_status = f"{match.group(1)} rules"
                else:
                    current_test.aws_config_status = "unknown count"
                current_test._current_layer = None
            elif "Layer 4 (AWS Config): Skipped" in line:
                current_test.aws_config_status = "skipped"
                current_test._current_layer = None
            elif "⛔ Layer 4 (AWS Config): Blocked" in line:
                current_test.aws_config_status = "blocked"
                current_test._current_layer = None

            # Parse AWS Config Rules list header
            # Format: "📋 AWS Config Rules (Layer 4):"
            if "AWS Config Rules" in line and "Layer 4" in line:
                current_test._current_layer = "aws_config_rules"

            # Capture individual AWS Config rule names
            # Format: "      ✓ SystemContext...ComplianceRuleName..."
            if current_test._current_layer == "aws_config_rules":
                stripped = line.strip()
                # Stop at section end markers
                if "Compliance validation" in line or stripped.startswith("=") or (not stripped and current_test.aws_config_rules):
                    current_test._current_layer = None
                elif "SystemContext" in line or "Compliance" in line:
                    # Extract rule name from line like "✓ RuleName" or just "RuleName"
                    parts = stripped.split()
                    for part in parts:
                        if part.startswith("SystemContext") or "Compliance" in part:
                            if part not in current_test.aws_config_rules:
                                current_test.aws_config_rules.append(part)
                            break

            # Capture individual violation messages
            # Format 1: "SEVERE:   - SOC2-CC6.2-Auth: ..." (FrameworkRules Java logger)
            # Format 2: "      - violation message" (cdk-nag, cfn-guard)
            # Format 3: "WARNING:  - ..." (advisories)
            # Format 4: "      ⚠ warning message" (cdk-nag advisories from JUnit tests)
            # Format 5: "[stack/construct] RULE-ID: message" (CDK ValidationError format)
            # Format 6: "   • RULE-ID: message" (TruthTableValidationTest output)
            violation_match = (re.match(r'SEVERE:\s+- (.+)', line) or
                             re.match(r'WARNING:\s+- (.+)', line) or
                             re.match(r'\s+⚠\s*(.+)', line) or
                             re.match(r'\s+•\s*(.+)', line) or
                             re.match(r'\s*\[[\w\-\.]+/\w+\]\s+(.+)', line) or
                             re.match(r'\s+- (.+)', line))
            if violation_match and current_test._current_layer:
                violation = violation_match.group(1).strip()
                if current_test._current_layer == "cdk_nag":
                    current_test.cdk_nag_violations.append(violation)
                elif current_test._current_layer == "cdk_nag_warnings":
                    current_test.cdk_nag_warnings.append(violation)
                elif current_test._current_layer == "framework_rules":
                    current_test.framework_rules_violations.append(violation)
                elif current_test._current_layer == "framework_rules_advisories":
                    current_test.framework_rules_advisories.append(violation)
                elif current_test._current_layer == "cfn_guard":
                    current_test.cfn_guard_violations.append(violation)
                elif current_test._current_layer == "cfn_guard_warnings":
                    current_test.cfn_guard_warnings.append(violation)

            # Capture cdk-nag failure detail lines (after "📋 cdk-nag Failure Details:")
            # These lines start with "   " (3 spaces) and are NOT separator lines (=====)
            if current_test._current_layer == "cdk_nag_details":
                stripped = line.strip()
                # Stop capturing on Layer 2 marker or closing separator
                if "Layer 2" in line or "FrameworkRules" in line:
                    current_test._current_layer = None
                elif stripped.startswith("=") and len(stripped) > 20:
                    # Closing separator - continue but prepare to end
                    pass
                elif stripped and not stripped.startswith("=") and "📋" not in line:
                    # Capture actual violation content (skip empty lines and markers)
                    current_test.cdk_nag_violations.append(stripped)

            # Capture cfn-guard failure detail lines (after "📋 cfn-guard Failure Details:")
            if current_test._current_layer == "cfn_guard_details":
                stripped = line.strip()
                # Stop capturing on Layer 4 marker or closing separator
                if "Layer 4" in line or "AWS Config" in line:
                    current_test._current_layer = None
                elif stripped.startswith("=") and len(stripped) > 20:
                    # Closing separator - continue but prepare to end
                    pass
                elif stripped and not stripped.startswith("=") and "📋" not in line:
                    # Capture actual violation content (skip empty lines and markers)
                    current_test.cfn_guard_violations.append(stripped)

            # Capture FrameworkRules violation detail lines (after "📋 FrameworkRules Violation Details:")
            # Also captures lines from CDK ValidationError format: [stack/construct] RULE-ID: message
            if current_test._current_layer == "framework_rules":
                stripped = line.strip()
                # Stop capturing on Layer 3 marker, closing separator, or next test
                if "Layer 3" in line or "cfn-guard" in line or "Testing:" in line:
                    current_test._current_layer = None
                elif stripped.startswith("=") and len(stripped) > 20:
                    # Separator line - skip
                    pass
                elif stripped and not stripped.startswith("=") and "📋" not in line:
                    # Capture violation content in various formats:
                    # Format 1: "• RULE-ID: message" (bullet point)
                    # Format 2: "[stack/construct] RULE-ID: message" (CDK ValidationError)
                    # Skip empty lines and section markers
                    if stripped.startswith("•") or re.match(r'\[[\w\-\.]+/\w+\]', stripped):
                        violation = re.sub(r'^•\s*', '', stripped)  # Remove bullet
                        violation = re.sub(r'^\[[\w\-\.]+/\w+\]\s*', '', violation)  # Remove [stack/construct]
                        if violation and violation not in current_test.framework_rules_violations:
                            current_test.framework_rules_violations.append(violation)

            # Capture known gaps
            if "⚠️  Known gaps:" in line:
                current_test._current_layer = "known_gaps"
            elif current_test._current_layer == "known_gaps" and line.strip().startswith("-"):
                gap = line.strip()[1:].strip()
                current_test.framework_rules_known_gaps.append(gap)

            # Capture rejection layers for negative tests (BEFORE test completion check)
            if "📋 Rejection layers:" in line:
                match = re.search(r'📋 Rejection layers:\s*(.+)', line)
                if match:
                    current_test.rejection_layers = match.group(1).strip()

            # Capture CloudFormation template paths for download links
            if "CFN_TEMPLATE_JSON:" in line:
                match = re.search(r'CFN_TEMPLATE_JSON:\s*(.+)', line)
                if match:
                    current_test.cfn_template_json = match.group(1).strip()

            if "CFN_TEMPLATE_YAML:" in line:
                match = re.search(r'CFN_TEMPLATE_YAML:\s*(.+)', line)
                if match:
                    current_test.cfn_template_yaml = match.group(1).strip()

            # Detect test success (positive tests)
            if "✅ Compliance validation passed" in line:
                current_test.status = "passed"
                current_test._current_layer = None
                self._write_result_incremental(current_test)
                return None  # Test completed

            # Detect negative test success (expected failures that correctly failed)
            if "✅ NEGATIVE TEST PASSED" in line:
                current_test.status = "passed"
                current_test.is_negative_test = True
                current_test._current_layer = None
                self._write_result_incremental(current_test)
                return None  # Test completed

        return current_test

    def _write_result_incremental(self, result: ComplianceTestResult):
        """Write a single test result to the incremental JSONL file."""
        result_dict = {
            "config_name": result.config_name,
            "framework": result.framework,
            "runtime": result.runtime,
            "network_mode": result.network_mode,
            "status": result.status,
            "duration": result.duration,
            "has_advisories": result.has_advisories,
            "advisory_layers": result.advisory_layers,
            "installed_rules": result.installed_rules,
            "aws_config_rules": result.aws_config_rules,
            "layers": {
                "cdk_nag": {
                    "status": result.cdk_nag_status,
                    "packs_applied": result.cdk_nag_packs_applied,
                    "violations": result.cdk_nag_violations,
                    "warnings": result.cdk_nag_warnings
                },
                "framework_rules": {
                    "status": result.framework_rules_status,
                    "violations": result.framework_rules_violations,
                    "advisories": result.framework_rules_advisories,
                    "known_gaps": result.framework_rules_known_gaps
                },
                "cfn_guard": {
                    "status": result.cfn_guard_status,
                    "violations": result.cfn_guard_violations,
                    "warnings": result.cfn_guard_warnings
                },
                "aws_config": {
                    "status": result.aws_config_status
                }
            },
            "error_message": result.error_message,
            "deployment_context": result.deployment_context,
            "is_negative_test": result.is_negative_test,
            "rejection_layers": result.rejection_layers,
            "cfn_template_json": result.cfn_template_json,
            "cfn_template_yaml": result.cfn_template_yaml
        }

        # Append to JSONL file (one JSON object per line)
        with open(self.incremental_results_file, 'a') as f:
            f.write(json.dumps(result_dict) + '\n')

    def _parse_all_junit_xml_files(self):
        """Parse all saved JUnit XML files from split test method runs and aggregate timing data."""
        # Find all saved XML files
        saved_xml_files = sorted(self.output_dir.glob("junit-xml-*.xml"))

        if not saved_xml_files:
            print(f"   ⚠️  No saved JUnit XML files found in {self.output_dir}")
            # Fall back to parsing the final XML file
            self._parse_junit_xml_incremental()
            return

        print(f"   📊 Parsing {len(saved_xml_files)} saved JUnit XML files...")

        for xml_file in saved_xml_files:
            self._parse_single_junit_xml(xml_file)

        print(f"   ✅ Aggregated timing data from {len(saved_xml_files)} XML files")

    def _parse_single_junit_xml(self, xml_file: Path):
        """Parse a single JUnit XML file and update results with timing, advisories, and installed rules."""
        if not xml_file.exists():
            return

        if xml_file.stat().st_size == 0:
            return

        try:
            # Read existing incremental results
            results_by_config = {}
            if self.incremental_results_file.exists():
                with open(self.incremental_results_file, 'r') as f:
                    for line in f:
                        result = json.loads(line)
                        results_by_config[result['config_name']] = result

            tree = ET.parse(xml_file)
            root = tree.getroot()

            for testcase in root.findall('testcase'):
                time_val = float(testcase.get('time', 0.0))

                # Extract config name and other data from both system-out and system-err
                system_out = testcase.find('system-out')
                system_err = testcase.find('system-err')
                config_name = None

                # Combine both outputs for full parsing
                stdout_text = system_out.text if system_out is not None and system_out.text else ""
                stderr_text = system_err.text if system_err is not None and system_err.text else ""
                combined_text = stdout_text + "\n" + stderr_text

                # Config name is typically in stdout
                match = re.search(r'Testing compliance configuration \(CSV\):\s*(\S+)\s+\[(.+?)\]', combined_text)
                if match:
                    config_name = match.group(1)

                    if config_name and config_name in results_by_config:
                        result = results_by_config[config_name]
                        # Update duration for this config
                        result['duration'] = time_val

                        # Extract cdk-nag advisories (warnings) from stdout
                        cdk_nag_warnings = []
                        if "cdk-nag Advisories" in combined_text:
                            lines = combined_text.split('\n')
                            in_advisories = False
                            for line in lines:
                                if "cdk-nag Advisories" in line:
                                    in_advisories = True
                                    continue
                                if in_advisories:
                                    # Stop at next section - check without relying on emoji
                                    if "Layer 2" in line or "CFN_TEMPLATE" in line or line.strip().startswith("---"):
                                        break
                                    # Extract warning lines (start with ⚠ or contain warning text)
                                    warn_match = re.match(r'\s*[⚠]\s*(.+)', line)
                                    if warn_match:
                                        cdk_nag_warnings.append(warn_match.group(1).strip())

                        # Extract installed rules from stderr (format: "INFO:   ✓ Rule Name (priority=X)")
                        # Use pattern without emoji for GitHub compatibility
                        installed_rules = []
                        seen_rules = set()  # Avoid duplicates
                        for line in combined_text.split('\n'):
                            # Match "INFO: ... (priority=X)" pattern, extract text before (priority=)
                            if 'INFO:' in line and '(priority=' in line:
                                # Extract the rule name between INFO: marker and (priority=
                                rule_match = re.search(r'INFO:\s+.?\s*(.+?)\s*\(priority=(-?\d+)\)', line)
                                if rule_match:
                                    rule_name = rule_match.group(1).strip()
                                    if rule_name and rule_name not in seen_rules:
                                        installed_rules.append(rule_name)
                                        seen_rules.add(rule_name)

                        # Extract AWS Config rules (Layer 4)
                        # Format: "📋 AWS Config Rules (Layer 4):" followed by "✓ RuleName"
                        aws_config_rules = []
                        if "AWS Config Rules" in combined_text:
                            lines = combined_text.split('\n')
                            in_config_rules = False
                            for line in lines:
                                if "AWS Config Rules" in line and "Layer 4" in line:
                                    in_config_rules = True
                                    continue
                                if in_config_rules:
                                    # Stop at section end markers
                                    stripped = line.strip()
                                    if "Compliance validation" in line or stripped.startswith("---") or stripped.startswith("=") or (not stripped and aws_config_rules):
                                        break
                                    # Extract rule names - look for "SystemContext" or "Compliance" prefixes
                                    # Use text-based matching for GitHub compatibility (avoid emoji chars)
                                    if "SystemContext" in line or "Compliance" in line:
                                        # Extract the rule name (everything after whitespace and optional checkmark)
                                        parts = stripped.split()
                                        if parts:
                                            # Last part is usually the rule name, or first part if checkmark
                                            rule_name = parts[-1] if len(parts) == 1 else parts[-1]
                                            # Also try to extract from the format "✓ RuleName"
                                            for part in parts:
                                                if part.startswith("SystemContext") or "Compliance" in part:
                                                    rule_name = part
                                                    break
                                            if rule_name and rule_name not in aws_config_rules:
                                                aws_config_rules.append(rule_name)

                        # Update result with new data
                        result['cdk_nag_warnings'] = cdk_nag_warnings
                        result['installed_rules'] = installed_rules
                        result['aws_config_rules'] = aws_config_rules
                        # Check for advisories using text patterns (avoid emoji for GitHub compatibility)
                        result['has_advisories'] = len(cdk_nag_warnings) > 0 or "Layer 1 (cdk-nag): Passed with" in combined_text
                        if result['has_advisories']:
                            result['advisory_layers'] = ['L1']
                        if 'layers' in result and 'cdk_nag' in result['layers']:
                            result['layers']['cdk_nag']['warnings'] = cdk_nag_warnings

            # Rewrite the incremental file with updated data
            with open(self.incremental_results_file, 'w') as f:
                for result in results_by_config.values():
                    f.write(json.dumps(result) + '\n')

        except Exception as e:
            import traceback
            print(f"   ⚠️  Failed to parse {xml_file.name}: {e}")
            print(f"   Traceback: {traceback.format_exc()}", file=sys.stderr)

    def _parse_junit_xml_incremental(self):
        """Parse JUnit XML and create/update results with timing, error info, and layer status."""
        xml_file = self.cloudforge_api_dir / "target" / "surefire-reports" / \
                   "TEST-com.cloudforgeci.api.integration.deployment.TruthTableValidationTest.xml"

        if not xml_file.exists():
            print(f"   ⚠️  JUnit XML not found: {xml_file}")
            return

        # Verify file is not empty and is valid XML
        if xml_file.stat().st_size == 0:
            print(f"   ⚠️  JUnit XML is empty (0 bytes)")
            return

        print(f"   📊 Parsing JUnit XML ({xml_file.stat().st_size} bytes)...")

        try:
            # Read existing incremental results (if any)
            results_by_config = {}
            if self.incremental_results_file.exists():
                with open(self.incremental_results_file, 'r') as f:
                    for line in f:
                        result = json.loads(line)
                        results_by_config[result['config_name']] = result

            tree = ET.parse(xml_file)
            root = tree.getroot()

            total_testcases = len(root.findall('testcase'))
            print(f"   📋 Found {total_testcases} testcases in JUnit XML")

            updated_count = 0
            created_count = 0
            skipped_count = 0

            for testcase in root.findall('testcase'):
                time_val = float(testcase.get('time', 0.0))

                # Extract config name and layer status from system-out
                system_out = testcase.find('system-out')
                config_name = None
                framework = 'unknown'
                runtime = 'unknown'
                network_mode = 'unknown'
                deployment_context = None
                layer_statuses = {
                    'cdk_nag': 'unknown',
                    'framework_rules': 'unknown',
                    'cfn_guard': 'unknown'
                }
                is_negative_test = False

                # Check if system-out exists and has content
                if system_out is None:
                    # Try system-err as fallback
                    system_out = testcase.find('system-err')

                if system_out is not None and system_out.text:
                    text = system_out.text

                    # Extract config name and framework
                    match = re.search(r'Testing compliance configuration \(CSV\):\s*(\S+)\s+\[(.+?)\]', text)
                    if match:
                        config_name = match.group(1)
                        framework = match.group(2)
                        # Parse runtime and network mode from config name
                        parts = config_name.split('_')
                        # Handle test prefixes: FAIL_, L1_, ADVISORY_, NEGATIVE_, etc.
                        # Find the actual runtime (EC2, FARGATE) in the parts
                        runtime = "unknown"
                        for part in parts:
                            if part.upper() in ['EC2', 'FARGATE']:
                                runtime = part.upper()
                                break
                        network_mode = parts[-1] if len(parts) > 0 else "unknown"

                    # Extract deployment context
                    ctx_match = re.search(r'DEPLOYMENT_CONTEXT_JSON:\s*(\{.*\})', text)
                    if ctx_match:
                        deployment_context = ctx_match.group(1)

                    # Parse Layer 1 (cdk-nag) status
                    if "✅ Layer 1 (cdk-nag):" in text:
                        layer_statuses['cdk_nag'] = 'passed'
                    elif "⚠️ Layer 1 (cdk-nag):" in text or "⚠️  Layer 1 (cdk-nag):" in text:
                        layer_statuses['cdk_nag'] = 'passed'
                        layer_statuses['cdk_nag_has_advisories'] = True
                    elif "❌ Layer 1 (cdk-nag)" in text or "❌ Layer 1" in text:
                        layer_statuses['cdk_nag'] = 'failed'

                    # Extract cdk-nag advisories (warnings)
                    cdk_nag_warnings = []
                    if "📋 cdk-nag Advisories" in text:
                        # Find the advisories section and extract warning lines
                        lines = text.split('\n')
                        in_advisories = False
                        for line in lines:
                            if "📋 cdk-nag Advisories" in line:
                                in_advisories = True
                                continue
                            if in_advisories:
                                # Stop at next section marker
                                if line.strip().startswith("✅") or line.strip().startswith("❌") or line.strip().startswith("📋") or line.strip().startswith("📄") or "Layer 2" in line:
                                    break
                                # Extract warning lines (start with ⚠)
                                warn_match = re.match(r'\s*⚠\s*(.+)', line)
                                if warn_match:
                                    cdk_nag_warnings.append(warn_match.group(1).strip())
                    layer_statuses['cdk_nag_warnings'] = cdk_nag_warnings

                    # Extract installed rules (from "✓ Rule Name (priority=X)")
                    installed_rules = []
                    for line in text.split('\n'):
                        rule_match = re.search(r'✓\s+(.+?)\s+\(priority=(\d+)\)', line)
                        if rule_match:
                            installed_rules.append(rule_match.group(1).strip())
                    layer_statuses['installed_rules'] = installed_rules

                    # Parse Layer 2 (FrameworkRules) status
                    if "✅ Layer 2 (FrameworkRules):" in text:
                        layer_statuses['framework_rules'] = 'passed'
                    elif "❌ Layer 2 (FrameworkRules):" in text:
                        layer_statuses['framework_rules'] = 'failed'
                    elif "⏭️  Layer 2 (FrameworkRules): Skipped (no template)" in text:
                        layer_statuses['framework_rules'] = 'skipped (no template)'
                    elif "⏭️  Layer 2 (FrameworkRules): Skipped" in text:
                        layer_statuses['framework_rules'] = 'skipped'

                    # Parse Layer 3 (cfn-guard) status
                    if "✅ Layer 3 (cfn-guard):" in text:
                        layer_statuses['cfn_guard'] = 'passed'
                    elif "❌ Layer 3 (cfn-guard):" in text:
                        layer_statuses['cfn_guard'] = 'failed'
                    elif "⏭️  Layer 3 (cfn-guard): Skipped (no template)" in text:
                        layer_statuses['cfn_guard'] = 'skipped (no template)'
                    elif "⏭️  Layer 3 (cfn-guard): Skipped" in text:
                        layer_statuses['cfn_guard'] = 'skipped'

                    # Parse Layer 4 (AWS Config) status - extract rule count
                    # Try multiple patterns to handle different formatting
                    aws_config_match = re.search(r'Layer 4 \(AWS Config\):\s+(\d+)\s+rules would be deployed', text)
                    if aws_config_match:
                        layer_statuses['aws_config'] = f"{aws_config_match.group(1)} rules"
                    elif "Layer 4 (AWS Config): Skipped" in text:
                        layer_statuses['aws_config'] = 'skipped'

                    # Check if this is a negative test that passed
                    if "NEGATIVE TEST PASSED" in text:
                        is_negative_test = True

                # Fallback: Extract config name from test method name if system-out didn't have it
                if not config_name:
                    test_method = testcase.get('name', '')
                    # Test method names like "testSoc2Ec2Pass" -> extract from CSV file mapping
                    # For now, skip if we can't determine config name
                    if test_method and not test_method.startswith('testTruthTableLoaded'):
                        print(f"   ⚠️  No config name found for test: {test_method} (system-out may be empty)")
                    skipped_count += 1
                    continue

                # Check for test failures
                failure = testcase.find('failure')
                error = testcase.find('error')
                failure_elem = failure if failure is not None else error
                test_failed = failure_elem is not None

                # If deployment_context not found in system-out, try to extract from failure message
                if not deployment_context and failure_elem is not None:
                    failure_text = failure_elem.text or ''
                    failure_message = failure_elem.get('message', '')
                    combined_failure = f"{failure_message}\n{failure_text}"
                    ctx_match = re.search(r'DEPLOYMENT_CONTEXT_JSON:\s*(\{.*\})', combined_failure)
                    if ctx_match:
                        deployment_context = ctx_match.group(1)

                # Create or update result
                if config_name in results_by_config:
                    # Update existing result
                    result = results_by_config[config_name]
                    result['duration'] = time_val
                    result['layers']['cdk_nag']['status'] = layer_statuses['cdk_nag']
                    result['layers']['cdk_nag']['warnings'] = layer_statuses.get('cdk_nag_warnings', [])
                    result['layers']['framework_rules']['status'] = layer_statuses['framework_rules']
                    result['layers']['cfn_guard']['status'] = layer_statuses['cfn_guard']
                    result['layers']['aws_config']['status'] = layer_statuses.get('aws_config', result['layers'].get('aws_config', {}).get('status', 'deployed'))
                    result['is_negative_test'] = is_negative_test
                    result['cdk_nag_warnings'] = layer_statuses.get('cdk_nag_warnings', [])
                    result['installed_rules'] = layer_statuses.get('installed_rules', [])
                    result['has_advisories'] = layer_statuses.get('cdk_nag_has_advisories', False) or len(layer_statuses.get('cdk_nag_warnings', [])) > 0
                    if result['has_advisories']:
                        result['advisory_layers'] = ['L1']

                    # Update deployment_context if we found one
                    if deployment_context:
                        result['deployment_context'] = deployment_context

                    # For negative tests: test passing means deployment correctly failed (expected) → status='passed'
                    # For negative tests: test failing means deployment succeeded when it shouldn't → status='failed'
                    # For positive tests: test failing means deployment failed → status='failed'
                    if is_negative_test:
                        result['status'] = 'passed' if not test_failed else 'failed'
                        if test_failed:
                            error_text = failure_elem.text or ''
                            message = failure_elem.get('message', '')
                            result['error_message'] = f"{message}\n\n{error_text}" if error_text else message
                    else:
                        if test_failed:
                            result['status'] = 'failed'
                            error_text = failure_elem.text or ''
                            message = failure_elem.get('message', '')
                            result['error_message'] = f"{message}\n\n{error_text}" if error_text else message
                    updated_count += 1
                else:
                    # Create new result from JUnit XML
                    # For negative tests: test passing means deployment correctly failed (expected) → status='passed'
                    # For negative tests: test failing means deployment succeeded when it shouldn't → status='failed'
                    # For positive tests: use normal test status
                    if is_negative_test:
                        status = 'passed' if not test_failed else 'failed'
                    else:
                        status = 'failed' if test_failed else 'passed'

                    has_advisories = layer_statuses.get('cdk_nag_has_advisories', False) or len(layer_statuses.get('cdk_nag_warnings', [])) > 0
                    result = {
                        'config_name': config_name,
                        'framework': framework,
                        'runtime': runtime,
                        'network_mode': network_mode,
                        'status': status,
                        'duration': time_val,
                        'has_advisories': has_advisories,
                        'advisory_layers': ['L1'] if has_advisories else [],
                        'cdk_nag_warnings': layer_statuses.get('cdk_nag_warnings', []),
                        'installed_rules': layer_statuses.get('installed_rules', []),
                        'layers': {
                            'cdk_nag': {'status': layer_statuses['cdk_nag'], 'packs_applied': 1 if layer_statuses['cdk_nag'] == 'passed' else 0, 'warnings': layer_statuses.get('cdk_nag_warnings', [])},
                            'framework_rules': {'status': layer_statuses['framework_rules']},
                            'cfn_guard': {'status': layer_statuses['cfn_guard']},
                            'aws_config': {'status': layer_statuses.get('aws_config', 'deployed')}
                        },
                        'error_message': None,
                        'deployment_context': deployment_context,
                        'is_negative_test': is_negative_test
                    }
                    if test_failed:
                        error_text = failure_elem.text or ''
                        message = failure_elem.get('message', '')
                        result['error_message'] = f"{message}\n\n{error_text}" if error_text else message
                    results_by_config[config_name] = result
                    created_count += 1

            # Rewrite the incremental file with all results
            with open(self.incremental_results_file, 'w') as f:
                for result in results_by_config.values():
                    f.write(json.dumps(result) + '\n')

            print(f"   ✅ Parsed {total_testcases} testcases: {updated_count} updated, {created_count} created, {skipped_count} skipped (non-CSV)")

            # Diagnostic: Show sample of parsed data
            if results_by_config:
                sample_config = next(iter(results_by_config.keys()))
                sample_duration = results_by_config[sample_config].get('duration', 0)
                print(f"   📊 Sample: {sample_config} = {sample_duration:.2f}s")
            else:
                print(f"   ⚠️  WARNING: No compliance test results extracted from JUnit XML!")

        except Exception as e:
            import traceback
            print(f"   ⚠️  Failed to parse JUnit XML: {e}")
            print(f"   Traceback: {traceback.format_exc()}", file=sys.stderr)

    def _load_incremental_results(self):
        """Load all results from the incremental JSONL file."""
        self.results = []
        if not self.incremental_results_file.exists():
            return

        with open(self.incremental_results_file, 'r') as f:
            for line in f:
                data = json.loads(line)
                result = ComplianceTestResult(
                    data['config_name'],
                    data['framework'],
                    data['runtime'],
                    data['network_mode']
                )
                result.status = data['status']
                result.duration = data['duration']
                result.has_advisories = data.get('has_advisories', False)
                result.advisory_layers = data.get('advisory_layers', [])
                result.installed_rules = data.get('installed_rules', [])
                result.aws_config_rules = data.get('aws_config_rules', [])
                result.cdk_nag_status = data['layers']['cdk_nag']['status']
                result.cdk_nag_packs_applied = data['layers']['cdk_nag'].get('packs_applied', 0)
                result.cdk_nag_violations = data['layers']['cdk_nag'].get('violations', [])
                result.cdk_nag_warnings = data['layers']['cdk_nag'].get('warnings', [])
                result.framework_rules_status = data['layers']['framework_rules']['status']
                result.framework_rules_violations = data['layers']['framework_rules'].get('violations', [])
                result.framework_rules_advisories = data['layers']['framework_rules'].get('advisories', [])
                result.framework_rules_known_gaps = data['layers']['framework_rules'].get('known_gaps', [])
                result.cfn_guard_status = data['layers']['cfn_guard']['status']
                result.cfn_guard_violations = data['layers']['cfn_guard'].get('violations', [])
                result.cfn_guard_warnings = data['layers']['cfn_guard'].get('warnings', [])
                result.aws_config_status = data['layers']['aws_config']['status']
                result.error_message = data.get('error_message')
                result.deployment_context = data.get('deployment_context')
                result.is_negative_test = data.get('is_negative_test', False)
                result.rejection_layers = data.get('rejection_layers')
                result.cfn_template_json = data.get('cfn_template_json')
                result.cfn_template_yaml = data.get('cfn_template_yaml')
                self.results.append(result)

    def _parse_maven_output(self, output: str):
        """Parse Maven test output to extract validation results."""
        print("📊 Parsing test output...")

        lines = output.split('\n')
        current_test = None
        results_by_config = {}

        # First pass: create test results and parse inline statuses
        for line in lines:
            # Detect test start
            match = re.search(r'Testing compliance configuration \(CSV\): (\S+) \[(\S+)\]', line)
            if match:
                config_name = match.group(1)
                framework = match.group(2)

                # Extract runtime and network mode from config name
                parts = config_name.split('_')
                # Handle test prefixes: FAIL_, L1_, ADVISORY_, NEGATIVE_, etc.
                # Find the actual runtime (EC2, FARGATE) in the parts
                runtime = "unknown"
                for part in parts:
                    if part.upper() in ['EC2', 'FARGATE']:
                        runtime = part.upper()
                        break
                network_mode = parts[-1] if len(parts) > 0 else "unknown"

                current_test = ComplianceTestResult(config_name, framework, runtime, network_mode)
                self.results.append(current_test)
                results_by_config[config_name] = current_test
                continue

            if current_test:
                # Parse deployment context JSON
                if "DEPLOYMENT_CONTEXT_JSON:" in line:
                    match = re.search(r'DEPLOYMENT_CONTEXT_JSON:\s*(\{.*\})', line)
                    if match:
                        current_test.deployment_context = match.group(1)

                # Parse installed FrameworkRules (Layer 2)
                if "✓ " in line and "(priority=" in line:
                    # Format: "✓ HIPAA Rules (priority=100)"
                    match = re.search(r'✓\s+(.+?)\s+\(priority=(\d+)\)', line)
                    if match:
                        rule_name = match.group(1).strip()
                        current_test.installed_rules.append(rule_name)

                # Parse Layer 1 (cdk-nag) results - new format
                if "✅ Layer 1 (cdk-nag):" in line:
                    current_test.cdk_nag_status = "passed"
                    current_test.cdk_nag_packs_applied = 1
                elif "❌ Layer 1 (cdk-nag)" in line or "❌ Layer 1" in line:
                    current_test.cdk_nag_status = "failed"

                # Parse Layer 2 (FrameworkRules) results - new format
                if "✅ Layer 2 (FrameworkRules):" in line:
                    current_test.framework_rules_status = "passed"
                elif "❌ Layer 2 (FrameworkRules):" in line:
                    current_test.framework_rules_status = "failed"
                elif "⏭️  Layer 2 (FrameworkRules): Skipped" in line:
                    current_test.framework_rules_status = "skipped"

                # Parse Layer 3 (cfn-guard) results - new format
                if "✅ Layer 3 (cfn-guard):" in line:
                    current_test.cfn_guard_status = "passed"
                elif "❌ Layer 3 (cfn-guard):" in line:
                    current_test.cfn_guard_status = "failed"
                elif "⏭️  Layer 3 (cfn-guard): Skipped" in line:
                    current_test.cfn_guard_status = "skipped"

                # Legacy: Parse cdk-nag results from old format
                if "Applied" in line and "cdk-nag validation packs" in line:
                    match = re.search(r'Applied (\d+) cdk-nag validation packs', line)
                    if match:
                        current_test.cdk_nag_packs_applied = int(match.group(1))
                        if current_test.cdk_nag_status == "unknown":
                            current_test.cdk_nag_status = "passed" if int(match.group(1)) > 0 else "skipped"

                # Detect test success - both new and old formats
                if "✅ Compliance validation passed" in line:
                    current_test.status = "passed"
                elif "✅ NEGATIVE TEST PASSED" in line:
                    current_test.status = "passed"
                    current_test.is_negative_test = True

        # Second pass: parse cfn-guard and FrameworkRules by config name
        for line in lines:
            # Parse cfn-guard results (Layer 3) - includes config name
            match = re.search(r'cfn-guard validation (passed|failed) for (\S+) \[', line)
            if match:
                status = match.group(1)
                config_name = match.group(2)
                if config_name in results_by_config:
                    results_by_config[config_name].cfn_guard_status = status

            # Parse FrameworkRules installation - associated with framework
            if "Successfully installed" in line and "FrameworkRules validators" in line:
                match = re.search(r'Successfully installed (\d+) CloudForge FrameworkRules validators', line)
                if match:
                    # Mark all results with matching framework as having FrameworkRules passed
                    for result in self.results:
                        if result.framework_rules_status == "unknown":
                            result.framework_rules_status = "passed"

        print(f"   Parsed {len(self.results)} test results")

    def _parse_junit_xml(self):
        """Parse JUnit XML results for detailed test information."""
        xml_file = self.cloudforge_api_dir / "target" / "surefire-reports" / \
                   "TEST-com.cloudforgeci.api.integration.deployment.TruthTableValidationTest.xml"

        if not xml_file.exists():
            print(f"   ⚠️  JUnit XML not found: {xml_file}")
            return

        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()

            for testcase in root.findall('testcase'):
                name = testcase.get('name', '')
                time = float(testcase.get('time', 0.0))

                # Check for failures/errors first to extract config name
                failure = testcase.find('failure')
                error = testcase.find('error')
                failure_elem = failure if failure is not None else error

                config_name = None
                error_message = None

                if failure_elem is not None:
                    message = failure_elem.get('message', '')
                    # Get full error text from element body
                    error_text = failure_elem.text or ''
                    error_message = f"{message}\n\n{error_text}" if error_text else message
                    # Extract config name from message like "EC2_PRODUCTION_SOC2_none_public-no-nat [SOC2]"
                    match = re.search(r'compliance config:\s*(\S+)\s+\[', message)
                    if match:
                        config_name = match.group(1)

                # Extract test index from name like [1], [2], etc.
                idx_match = re.search(r'\[(\d+)\]$', name)
                test_idx = int(idx_match.group(1)) - 1 if idx_match else -1  # Convert to 0-based

                # Find matching result by config name or by index
                matched = False
                for result in self.results:
                    if config_name and result.config_name == config_name:
                        result.duration = time
                        if failure_elem is not None:
                            result.status = "failed"
                            result.error_message = error_message
                        elif result.status == "pending":
                            result.status = "passed"
                        matched = True
                        break

                # If not matched by config name, try by index
                if not matched and 0 <= test_idx < len(self.results):
                    self.results[test_idx].duration = time
                    if failure_elem is not None:
                        self.results[test_idx].status = "failed"
                        self.results[test_idx].error_message = error_message
                    elif self.results[test_idx].status == "pending":
                        self.results[test_idx].status = "passed"

            print(f"   ✅ Parsed JUnit XML results")

            # Now that we have error messages, update layer statuses for failures
            self._update_layer_statuses_for_failures()

        except Exception as e:
            import traceback
            print(f"   ⚠️  Failed to parse JUnit XML: {e}")
            print(f"   Traceback: {traceback.format_exc()}", file=sys.stderr)

    def _update_layer_statuses_for_failures(self):
        """Update layer statuses based on error message patterns."""
        for result in self.results:
            # Check if this was a FrameworkRules validation failure
            is_framework_rules_failure = False
            is_test_setup_failure = False
            if result.error_message and result.status == "failed":
                # FrameworkRules validation errors can be detected by:
                # 1. CDK ValidationError format: [stack-name/SystemContext] RULE-ID: message
                # 2. ValidationError header: "Validation failed with the following errors:"
                # Use regex to detect structural patterns rather than hardcoded rule names

                # Pattern 1: [stack/construct] RULE-ID: message
                # RULE-ID format: FRAMEWORK-CONTROL (e.g., HIPAA-164.312(b)-FlowLogs, SOC2-CC7.2-FlowLogs)
                rule_id_pattern = re.compile(r'\[[\w\-\.]+/\w+\]\s+[A-Z][A-Za-z0-9\.\-\(\)]+:')

                # Pattern 2: CDK ValidationError wrapper
                validation_error_pattern = re.compile(r'ValidationError:\s*Validation failed', re.IGNORECASE)

                # Pattern 3: SystemContext indicates FrameworkRules validation
                system_context_pattern = re.compile(r'/SystemContext\]')

                is_framework_rules_failure = (
                    bool(rule_id_pattern.search(result.error_message)) or
                    bool(validation_error_pattern.search(result.error_message)) or
                    bool(system_context_pattern.search(result.error_message))
                )

                # Test setup failures (e.g., truth table constraint violations)
                test_setup_patterns = [
                    'violates truth table constraints',
                    'IllegalArgumentException'
                ]
                is_test_setup_failure = any(p in result.error_message for p in test_setup_patterns)

            if is_framework_rules_failure:
                # FrameworkRules blocked this configuration
                result.framework_rules_status = "failed"
                result.cdk_nag_status = "blocked"  # Never got to cdk-nag
                result.cfn_guard_status = "blocked"  # Never got to cfn-guard
            elif is_test_setup_failure:
                # Test failed during setup, before any validation layers ran
                result.framework_rules_status = "skipped"
                result.cdk_nag_status = "skipped"
                result.cfn_guard_status = "skipped"

    def generate_json_report(self) -> Path:
        """Generate JSON report with all test results."""
        advisory_count = len([r for r in self.results if r.status == "passed" and r.has_advisories])
        report = {
            "metadata": {
                "generated_at": datetime.now().isoformat(),
                "total_tests": len(self.results),
                "passed": len([r for r in self.results if r.status == "passed"]),
                "passed_clean": len([r for r in self.results if r.status == "passed" and not r.has_advisories]),
                "passed_with_advisories": advisory_count,
                "failed": len([r for r in self.results if r.status == "failed"]),
                "duration_total": sum(r.duration for r in self.results)
            },
            "results": []
        }

        for result in self.results:
            report["results"].append({
                "config_name": result.config_name,
                "framework": result.framework,
                "runtime": result.runtime,
                "network_mode": result.network_mode,
                "status": result.status,
                "duration": result.duration,
                "has_advisories": result.has_advisories,
                "advisory_layers": result.advisory_layers,
                "is_negative_test": result.is_negative_test,
                "rejection_layers": result.rejection_layers,
                "layers": {
                    "cdk_nag": {
                        "status": result.cdk_nag_status,
                        "packs_applied": result.cdk_nag_packs_applied,
                        "violations": result.cdk_nag_violations,
                        "warnings": result.cdk_nag_warnings
                    },
                    "framework_rules": {
                        "status": result.framework_rules_status,
                        "violations": result.framework_rules_violations,
                        "advisories": result.framework_rules_advisories,
                        "known_gaps": result.framework_rules_known_gaps
                    },
                    "cfn_guard": {
                        "status": result.cfn_guard_status,
                        "violations": result.cfn_guard_violations,
                        "warnings": result.cfn_guard_warnings
                    },
                    "aws_config": {
                        "status": result.aws_config_status,
                        "rules": result.aws_config_rules
                    }
                },
                "error_message": result.error_message,
                "deployment_context": result.deployment_context,
                "installed_rules": result.installed_rules,
                "aws_config_rules": result.aws_config_rules
            })

        json_file = self.output_dir / "compliance-validation-results.json"
        with open(json_file, 'w') as f:
            json.dump(report, f, indent=2)

        print(f"✅ JSON report saved to: {json_file}")
        return json_file

    def generate_html_dashboard(self) -> Path:
        """Generate interactive HTML dashboard with validation results."""
        # Calculate statistics
        total_tests = len(self.results)
        passed = len([r for r in self.results if r.status == "passed"])
        failed = len([r for r in self.results if r.status == "failed"])
        advisory = len([r for r in self.results if r.status == "passed" and r.has_advisories])
        clean_passed = passed - advisory
        duration = sum(r.duration for r in self.results)

        # Layer statistics
        cdk_nag_passed = len([r for r in self.results if r.cdk_nag_status == "passed"])
        cdk_nag_failed = len([r for r in self.results if r.cdk_nag_status == "failed"])
        cfn_guard_passed = len([r for r in self.results if r.cfn_guard_status == "passed"])
        cfn_guard_failed = len([r for r in self.results if r.cfn_guard_status == "failed"])
        framework_rules_passed = len([r for r in self.results if r.framework_rules_status == "passed"])
        framework_rules_failed = len([r for r in self.results if r.framework_rules_status == "failed"])

        # Framework breakdown
        frameworks = {}
        runtimes = set()
        for result in self.results:
            runtimes.add(result.runtime)
            if result.framework not in frameworks:
                frameworks[result.framework] = {"passed": 0, "failed": 0, "advisory": 0, "total": 0}
            frameworks[result.framework]["total"] += 1
            if result.status == "passed":
                frameworks[result.framework]["passed"] += 1
                if result.has_advisories:
                    frameworks[result.framework]["advisory"] += 1
            else:
                frameworks[result.framework]["failed"] += 1

        # Generate JSON data for JavaScript
        results_json = json.dumps([{
            "config_name": r.config_name,
            "framework": r.framework,
            "runtime": r.runtime,
            "network_mode": r.network_mode,
            "status": r.status,
            "duration": r.duration,
            "has_advisories": r.has_advisories,
            "advisory_layers": r.advisory_layers,
            "is_negative_test": r.is_negative_test,
            "rejection_layers": r.rejection_layers,
            "cdk_nag_status": r.cdk_nag_status,
            "cdk_nag_violations": r.cdk_nag_violations,
            "cdk_nag_warnings": r.cdk_nag_warnings,
            "framework_rules_status": r.framework_rules_status,
            "framework_rules_violations": r.framework_rules_violations,
            "framework_rules_advisories": r.framework_rules_advisories,
            "framework_rules_known_gaps": r.framework_rules_known_gaps,
            "cfn_guard_status": r.cfn_guard_status,
            "cfn_guard_violations": r.cfn_guard_violations,
            "cfn_guard_warnings": r.cfn_guard_warnings,
            "aws_config_status": r.aws_config_status,
            "error_message": r.error_message,
            "deployment_context": r.deployment_context,
            "cfn_template_json": r.cfn_template_json,
            "cfn_template_yaml": r.cfn_template_yaml,
            "installed_rules": r.installed_rules,
            "aws_config_rules": r.aws_config_rules
        } for r in self.results])

        frameworks_json = json.dumps(list(frameworks.keys()))
        runtimes_json = json.dumps(list(runtimes))

        html_content = f"""<!DOCTYPE html>
<html>
<head>
    <title>CloudForge Compliance Validation Dashboard</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }}
        .container {{ max-width: 1600px; margin: 0 auto; }}
        .header {{ text-align: center; color: white; padding: 30px 20px; }}
        .header h1 {{ font-size: 2.5em; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.2); }}
        .header p {{ opacity: 0.9; font-size: 1.1em; }}
        .back-link {{ display: inline-block; margin-bottom: 20px; color: white; text-decoration: none; font-weight: 500; padding: 10px 20px; background: rgba(255,255,255,0.2); border-radius: 6px; }}
        .back-link:hover {{ background: rgba(255,255,255,0.3); }}
        .content {{ background: white; border-radius: 12px; padding: 30px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); margin-bottom: 30px; }}

        .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 20px; margin: 30px 0; }}
        .stat-card {{ background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; border-left: 4px solid #667eea; transition: transform 0.2s; }}
        .stat-card:hover {{ transform: translateY(-2px); }}
        .stat-card.success {{ border-left-color: #27ae60; }}
        .stat-card.advisory {{ border-left-color: #f39c12; }}
        .stat-card.failed {{ border-left-color: #e74c3c; }}
        .stat-number {{ font-size: 2.5em; font-weight: bold; color: #667eea; margin-bottom: 5px; }}
        .stat-card.success .stat-number {{ color: #27ae60; }}
        .stat-card.advisory .stat-number {{ color: #f39c12; }}
        .stat-card.failed .stat-number {{ color: #e74c3c; }}
        .stat-label {{ color: #7f8c8d; font-size: 0.9em; }}

        .section {{ margin: 30px 0; }}
        .section h2 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; margin-bottom: 20px; }}

        .layer-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }}
        .layer-card {{ background: #f8f9fa; padding: 20px; border-radius: 8px; border-left: 5px solid #3498db; }}
        .layer-card h3 {{ color: #2c3e50; margin-bottom: 10px; }}
        .layer-stats {{ font-size: 1.5em; font-weight: bold; color: #27ae60; }}
        .layer-badge {{ display: inline-block; padding: 5px 10px; border-radius: 4px; font-size: 12px; font-weight: bold; margin-bottom: 10px; }}
        .badge-layer1 {{ background: #3498db; color: white; }}
        .badge-layer2 {{ background: #2ecc71; color: white; }}
        .badge-layer3 {{ background: #f39c12; color: white; }}
        .badge-layer4 {{ background: #9b59b6; color: white; }}

        /* Filter Controls */
        .filter-controls {{ display: flex; flex-wrap: wrap; gap: 15px; margin: 25px 0; padding: 20px; background: #f8f9fa; border-radius: 8px; align-items: center; }}
        .filter-group {{ display: flex; flex-direction: column; gap: 5px; }}
        .filter-group label {{ font-size: 0.85em; font-weight: 600; color: #7f8c8d; text-transform: uppercase; }}
        .filter-group select, .filter-group input {{ padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 0.95em; min-width: 150px; }}
        .filter-group select:focus, .filter-group input:focus {{ outline: none; border-color: #667eea; box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1); }}
        .filter-buttons {{ display: flex; gap: 10px; margin-left: auto; }}
        .filter-btn {{ padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; transition: all 0.2s; }}
        .filter-btn.primary {{ background: #667eea; color: white; }}
        .filter-btn.primary:hover {{ background: #5a6fd6; }}
        .filter-btn.secondary {{ background: #e0e0e0; color: #333; }}
        .filter-btn.secondary:hover {{ background: #d0d0d0; }}

        /* Results Table */
        .results-table-container {{ overflow-x: auto; margin-top: 20px; }}
        .results-table {{ width: 100%; border-collapse: collapse; font-size: 0.9em; }}
        .results-table th {{ background: #34495e; color: white; padding: 14px 10px; text-align: left; font-weight: 600; cursor: pointer; user-select: none; white-space: nowrap; position: sticky; top: 0; }}
        .results-table th:hover {{ background: #3d566e; }}
        .results-table th .sort-icon {{ margin-left: 6px; opacity: 0.5; }}
        .results-table th.sorted .sort-icon {{ opacity: 1; }}
        .results-table td {{ padding: 10px; border-bottom: 1px solid #eee; vertical-align: middle; }}
        .results-table tbody tr:hover {{ background: #f8f9fa; }}
        .results-table tbody tr.status-passed {{ border-left: 4px solid #27ae60; }}
        .results-table tbody tr.status-advisory {{ border-left: 4px solid #f39c12; }}
        .results-table tbody tr.status-failed {{ border-left: 4px solid #e74c3c; }}

        /* Status Badges */
        .status-badge {{ display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 12px; font-size: 0.8em; font-weight: 600; white-space: nowrap; }}
        .status-passed {{ background: #d4edda; color: #155724; }}
        .status-advisory {{ background: #fff3cd; color: #856404; }}
        .status-failed {{ background: #f8d7da; color: #721c24; }}
        .status-blocked {{ background: #f5c6cb; color: #721c24; }}
        .status-skipped {{ background: #fff3cd; color: #856404; }}
        .status-skipped-no-template {{ background: #e2e3e5; color: #383d41; }}
        .status-deployed {{ background: #d1ecf1; color: #0c5460; }}
        .status-unknown {{ background: #e2e3e5; color: #383d41; }}

        /* Advisory layer badges */
        .advisory-layers {{ display: inline-flex; gap: 3px; margin-left: 5px; }}
        .layer-badge-small {{ display: inline-block; padding: 2px 5px; border-radius: 3px; font-size: 0.65em; font-weight: 600; }}
        .layer-badge-small.l1 {{ background: #3498db; color: white; }}
        .layer-badge-small.l2 {{ background: #2ecc71; color: white; }}
        .layer-badge-small.l3 {{ background: #f39c12; color: white; }}

        /* Framework & Runtime Badges */
        .framework-badge {{ padding: 4px 8px; border-radius: 4px; font-size: 0.8em; font-weight: 600; }}
        .framework-badge.soc2 {{ background: #e3f2fd; color: #1565c0; }}
        .framework-badge.hipaa {{ background: #fce4ec; color: #c2185b; }}
        .framework-badge.pci {{ background: #fff3e0; color: #ef6c00; }}
        .framework-badge.gdpr {{ background: #e8f5e9; color: #2e7d32; }}
        .framework-badge.fedramp {{ background: #f3e5f5; color: #7b1fa2; }}
        .runtime-badge {{ padding: 4px 8px; border-radius: 4px; font-size: 0.8em; font-weight: 500; }}
        .runtime-badge.ec2 {{ background: #e3f2fd; color: #1565c0; }}
        .runtime-badge.fargate {{ background: #fce4ec; color: #c2185b; }}

        .config-name {{ cursor: pointer; color: #3498db; font-family: monospace; font-size: 0.85em; }}
        .config-name:hover {{ text-decoration: underline; }}
        .neg-badge {{ display: inline-block; padding: 2px 6px; border-radius: 3px; font-size: 0.7em; font-weight: 600; background: #e8daef; color: #6c3483; margin-left: 5px; }}
        .violations-count {{ color: #e74c3c; font-size: 0.75em; margin-left: 5px; }}
        .template-link {{ color: #27ae60; text-decoration: none; font-weight: 600; font-size: 0.75em; padding: 2px 6px; background: #d5f5e3; border-radius: 3px; margin: 0 2px; }}
        .template-link:hover {{ background: #abebc6; color: #1e8449; }}

        .detail-row {{ display: none; background: #f8f9fa; }}
        .detail-row.expanded {{ display: table-row; }}
        .detail-content {{ padding: 15px; font-family: monospace; font-size: 0.8em; white-space: pre-wrap; word-break: break-word; background: #1e1e1e; color: #d4d4d4; border-radius: 6px; max-height: 300px; overflow-y: auto; }}

        .framework-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 15px; margin: 20px 0; }}
        .framework-card {{ background: #f8f9fa; border: 2px solid #ecf0f1; border-radius: 8px; padding: 15px; text-align: center; cursor: pointer; transition: all 0.2s; }}
        .framework-card:hover {{ border-color: #667eea; transform: translateY(-2px); }}
        .framework-card.active {{ border-color: #667eea; background: #f0f4ff; }}
        .framework-name {{ font-weight: bold; color: #2c3e50; margin-bottom: 10px; }}
        .framework-stats {{ font-size: 1.2em; color: #27ae60; }}

        /* Pagination */
        .pagination {{ display: flex; justify-content: center; gap: 5px; margin-top: 20px; }}
        .pagination button {{ padding: 8px 14px; border: 1px solid #ddd; background: white; border-radius: 4px; cursor: pointer; }}
        .pagination button:hover {{ background: #f0f0f0; }}
        .pagination button.active {{ background: #667eea; color: white; border-color: #667eea; }}
        .pagination button:disabled {{ opacity: 0.5; cursor: not-allowed; }}

        .result-count {{ color: #7f8c8d; font-size: 0.9em; margin-bottom: 15px; }}
        .chart-container {{ margin: 30px 0; padding: 20px; background: #f8f9fa; border-radius: 8px; }}
    </style>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div class="container">
        <div class="header">
            <a href="../index.html" class="back-link">← Back to Dashboard</a>
            <h1>🔒 Multi-Layer Compliance Validation Dashboard</h1>
            <p>Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
            <p style="margin-top: 10px;">Defense-in-depth validation across 4 independent layers</p>
        </div>

        <div class="content">
            <div class="stats">
                <div class="stat-card success">
                    <div class="stat-number">{clean_passed}</div>
                    <div class="stat-label">Tests Passed</div>
                </div>
                <div class="stat-card advisory">
                    <div class="stat-number">{advisory}</div>
                    <div class="stat-label">With Advisories</div>
                </div>
                <div class="stat-card failed">
                    <div class="stat-number">{failed}</div>
                    <div class="stat-label">Tests Failed</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number">{total_tests}</div>
                    <div class="stat-label">Total Tests</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number">{duration:.1f}s</div>
                    <div class="stat-label">Duration</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number">{(passed/total_tests*100) if total_tests > 0 else 0:.0f}%</div>
                    <div class="stat-label">Pass Rate</div>
                </div>
            </div>

            <div class="section">
                <h2>📊 Multi-Layer Validation Status</h2>
                <div class="layer-grid">
                    <div class="layer-card">
                        <span class="layer-badge badge-layer1">LAYER 1</span>
                        <h3>cdk-nag</h3>
                        <p style="font-size: 13px; color: #7f8c8d; margin-bottom: 10px;">Construct-level validation</p>
                        <div class="layer-stats">{cdk_nag_passed}/{total_tests}</div>
                        <p style="font-size: 12px; color: #7f8c8d;">configurations validated</p>
                        <a href="cdk-nag/" style="display: inline-block; margin-top: 10px; padding: 5px 10px; background: #3498db; color: white; text-decoration: none; border-radius: 4px; font-size: 11px;">📄 NagReports</a>
                    </div>

                    <div class="layer-card">
                        <span class="layer-badge badge-layer2">LAYER 2</span>
                        <h3>FrameworkRules</h3>
                        <p style="font-size: 13px; color: #7f8c8d; margin-bottom: 10px;">Business logic validation</p>
                        <div class="layer-stats">{framework_rules_passed}/{total_tests}</div>
                        <p style="font-size: 12px; color: #7f8c8d;">configurations validated</p>
                    </div>

                    <div class="layer-card">
                        <span class="layer-badge badge-layer3">LAYER 3</span>
                        <h3>cfn-guard</h3>
                        <p style="font-size: 13px; color: #7f8c8d; margin-bottom: 10px;">Template-level policy</p>
                        <div class="layer-stats">{cfn_guard_passed}/{total_tests}</div>
                        <p style="font-size: 12px; color: #7f8c8d;">configurations validated</p>
                    </div>

                    <div class="layer-card">
                        <span class="layer-badge badge-layer4">LAYER 4</span>
                        <h3>AWS Config</h3>
                        <p style="font-size: 13px; color: #7f8c8d; margin-bottom: 10px;">Runtime monitoring</p>
                        <div class="layer-stats">140+</div>
                        <p style="font-size: 12px; color: #7f8c8d;">rules deployed at runtime</p>
                    </div>
                </div>
            </div>

            <div class="section">
                <h2>🎯 Framework Breakdown</h2>
                <div class="framework-grid" id="framework-grid">"""

        for framework, stats in sorted((k, v) for k, v in frameworks.items() if k is not None):
            pass_rate = (stats['passed'] / stats['total'] * 100) if stats['total'] > 0 else 0
            framework_class = framework.lower().replace('-', '').replace('_', '')
            html_content += f"""
                    <div class="framework-card" onclick="filterByFramework('{framework}')" data-framework="{framework}">
                        <div class="framework-name">{framework}</div>
                        <div class="framework-stats">{stats['passed']}/{stats['total']}</div>
                        <div style="margin-top: 5px; font-size: 12px; color: #7f8c8d;">{pass_rate:.0f}% pass rate</div>
                    </div>"""

        html_content += f"""
                </div>
            </div>

            <div class="section">
                <h2>📋 Detailed Test Results</h2>

                <div class="filter-controls">
                    <div class="filter-group">
                        <label>Framework</label>
                        <select id="filter-framework">
                            <option value="">All Frameworks</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>Runtime</label>
                        <select id="filter-runtime">
                            <option value="">All Runtimes</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>Status</label>
                        <select id="filter-status">
                            <option value="">All Statuses</option>
                            <option value="passed">✅ Passed (clean)</option>
                            <option value="advisory">⚠️ Advisory</option>
                            <option value="failed">❌ Failed</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>Layer 1 (cdk-nag)</label>
                        <select id="filter-layer1">
                            <option value="">All</option>
                            <option value="passed">✅ Passed</option>
                            <option value="failed">❌ Failed</option>
                            <option value="skipped">⏭️ Skipped</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>Layer 2 (Rules)</label>
                        <select id="filter-layer2">
                            <option value="">All</option>
                            <option value="passed">✅ Passed</option>
                            <option value="failed">❌ Failed</option>
                            <option value="skipped">⏭️ Skipped</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>Search</label>
                        <input type="text" id="filter-search" placeholder="Search configs...">
                    </div>
                    <div class="filter-buttons">
                        <button class="filter-btn secondary" onclick="resetFilters()">Reset</button>
                        <button class="filter-btn primary" onclick="applyFilters()">Apply</button>
                    </div>
                </div>

                <div class="result-count" id="result-count"></div>
                <div class="results-table-container">
                    <table class="results-table" id="results-table">
                        <thead>
                            <tr>
                                <th class="sorted" onclick="handleSort('config_name')">Configuration <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('framework')">Framework <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('runtime')">Runtime <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('cdk_nag_status')">cdk-nag <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('framework_rules_status')">FrameworkRules <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('cfn_guard_status')">cfn-guard <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('aws_config_status')">AWS Config <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('status')">Status <span class="sort-icon">↕</span></th>
                                <th onclick="handleSort('duration')">Duration <span class="sort-icon">↕</span></th>
                                <th>Template</th>
                            </tr>
                        </thead>
                        <tbody id="results-tbody">
                        </tbody>
                    </table>
                </div>
                <div class="pagination" id="pagination"></div>
            </div>

            <div class="section">
                <h2>📈 Validation Charts</h2>
                <div class="chart-container">
                    <canvas id="layerChart" width="400" height="200"></canvas>
                </div>
            </div>
        </div>
    </div>

    <script>
        // Test results data
        const allTests = {results_json};
        const frameworks = {frameworks_json};
        const runtimes = {runtimes_json};

        let filteredTests = [...allTests];
        let currentSort = {{ column: 'config_name', direction: 'asc' }};
        let currentPage = 1;
        const testsPerPage = 25;
        let expandedRows = new Set();

        // Initialize
        document.addEventListener('DOMContentLoaded', function() {{
            populateFilters();
            applyFilters();
            initChart();
        }});

        function populateFilters() {{
            const frameworkSelect = document.getElementById('filter-framework');
            frameworks.forEach(f => {{
                const opt = document.createElement('option');
                opt.value = f;
                opt.textContent = f;
                frameworkSelect.appendChild(opt);
            }});

            const runtimeSelect = document.getElementById('filter-runtime');
            runtimes.forEach(r => {{
                const opt = document.createElement('option');
                opt.value = r;
                opt.textContent = r;
                runtimeSelect.appendChild(opt);
            }});
        }}

        function filterByFramework(framework) {{
            document.getElementById('filter-framework').value = framework;
            // Update framework cards
            document.querySelectorAll('.framework-card').forEach(card => {{
                card.classList.toggle('active', card.dataset.framework === framework);
            }});
            applyFilters();
        }}

        function applyFilters() {{
            const framework = document.getElementById('filter-framework').value;
            const runtime = document.getElementById('filter-runtime').value;
            const status = document.getElementById('filter-status').value;
            const layer1 = document.getElementById('filter-layer1').value;
            const layer2 = document.getElementById('filter-layer2').value;
            const search = document.getElementById('filter-search').value.toLowerCase();

            filteredTests = allTests.filter(test => {{
                if (framework && test.framework !== framework) return false;
                if (runtime && test.runtime !== runtime) return false;
                // Handle status filter with advisory distinction
                if (status) {{
                    if (status === 'passed') {{
                        // Only clean passes (not advisory)
                        if (test.status !== 'passed' || test.has_advisories) return false;
                    }} else if (status === 'advisory') {{
                        // Only passed with advisories
                        if (test.status !== 'passed' || !test.has_advisories) return false;
                    }} else if (status === 'failed') {{
                        if (test.status !== 'failed') return false;
                    }}
                }}
                if (layer1 && !test.cdk_nag_status.includes(layer1)) return false;
                if (layer2 && !test.framework_rules_status.includes(layer2)) return false;
                if (search && !test.config_name.toLowerCase().includes(search)) return false;
                return true;
            }});

            sortTests();
            currentPage = 1;
            displayResults();
        }}

        function resetFilters() {{
            document.getElementById('filter-framework').value = '';
            document.getElementById('filter-runtime').value = '';
            document.getElementById('filter-status').value = '';
            document.getElementById('filter-layer1').value = '';
            document.getElementById('filter-layer2').value = '';
            document.getElementById('filter-search').value = '';
            document.querySelectorAll('.framework-card').forEach(card => card.classList.remove('active'));
            applyFilters();
        }}

        function sortTests() {{
            const {{ column, direction }} = currentSort;
            const modifier = direction === 'asc' ? 1 : -1;

            filteredTests.sort((a, b) => {{
                let aVal = a[column];
                let bVal = b[column];

                if (column === 'duration') {{
                    aVal = parseFloat(aVal) || 0;
                    bVal = parseFloat(bVal) || 0;
                }}

                if (column === 'status') {{
                    const statusOrder = {{ failed: 0, passed: 1 }};
                    aVal = statusOrder[aVal] ?? 2;
                    bVal = statusOrder[bVal] ?? 2;
                }}

                if (aVal < bVal) return -1 * modifier;
                if (aVal > bVal) return 1 * modifier;
                return 0;
            }});
        }}

        function handleSort(column) {{
            // Update header styling
            document.querySelectorAll('.results-table th').forEach(th => th.classList.remove('sorted'));
            event.target.closest('th').classList.add('sorted');

            if (currentSort.column === column) {{
                currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
            }} else {{
                currentSort.column = column;
                currentSort.direction = 'asc';
            }}
            sortTests();
            displayResults();
        }}

        function displayResults() {{
            const start = (currentPage - 1) * testsPerPage;
            const end = start + testsPerPage;
            const pageTests = filteredTests.slice(start, end);

            document.getElementById('result-count').textContent =
                `Showing ${{start + 1}}-${{Math.min(end, filteredTests.length)}} of ${{filteredTests.length}} tests`;

            const tbody = document.getElementById('results-tbody');
            tbody.innerHTML = '';

            pageTests.forEach((test, idx) => {{
                const globalIdx = start + idx;
                const row = document.createElement('tr');
                // Determine row class: passed (clean), advisory (passed with warnings), or failed
                let rowClass = 'status-failed';
                if (test.status === 'passed') {{
                    rowClass = test.has_advisories ? 'status-advisory' : 'status-passed';
                }}
                row.className = rowClass;

                const frameworkClass = test.framework.toLowerCase().replace(/[^a-z]/g, '');
                const runtimeClass = test.runtime.toLowerCase();
                const violationsCount = (test.cdk_nag_violations?.length || 0) +
                                        (test.framework_rules_violations?.length || 0) +
                                        (test.cfn_guard_violations?.length || 0);
                const warningsCount = (test.cdk_nag_warnings?.length || 0) +
                                      (test.framework_rules_advisories?.length || 0) +
                                      (test.cfn_guard_warnings?.length || 0);

                row.innerHTML = `
                    <td>
                        <span class="config-name" onclick="toggleDetail(${{globalIdx}})">▶ ${{test.config_name}}</span>
                        ${{test.is_negative_test ? '<span class="neg-badge">NEG</span>' : ''}}
                        ${{violationsCount > 0 ? `<span class="violations-count">(${{violationsCount}} violations)</span>` : ''}}
                        ${{warningsCount > 0 ? `<span class="violations-count" style="color:#f39c12;">(${{warningsCount}} advisories)</span>` : ''}}
                    </td>
                    <td><span class="framework-badge ${{frameworkClass}}">${{test.framework}}</span></td>
                    <td><span class="runtime-badge ${{runtimeClass}}">${{test.runtime}}</span></td>
                    <td>${{getStatusBadge(test.cdk_nag_status)}}</td>
                    <td>${{getStatusBadge(test.framework_rules_status)}}</td>
                    <td>${{getStatusBadge(test.cfn_guard_status)}}</td>
                    <td>${{getStatusBadge(test.aws_config_status)}}</td>
                    <td>${{getOverallStatusBadge(test)}}</td>
                    <td>${{test.duration.toFixed(2)}}s</td>
                    <td>${{getTemplateLinks(test)}}</td>
                `;
                tbody.appendChild(row);

                // Detail row
                const detailRow = document.createElement('tr');
                detailRow.className = 'detail-row' + (expandedRows.has(globalIdx) ? ' expanded' : '');
                detailRow.id = 'detail-' + globalIdx;
                detailRow.innerHTML = `<td colspan="10"><div class="detail-content">${{getDetailContent(test)}}</div></td>`;
                tbody.appendChild(detailRow);
            }});

            displayPagination();
        }}

        function getStatusBadge(status) {{
            if (!status) return '<span class="status-badge status-unknown">unknown</span>';
            const normalized = status.replace(/[\\s()]/g, '-').toLowerCase();
            let icon = '';
            if (status.includes('passed')) icon = '✅';
            else if (status.includes('failed')) icon = '❌';
            else if (status.includes('skipped')) icon = '⏭️';
            else if (status.includes('deployed') || /^\\d+/.test(status)) icon = '📋';
            return `<span class="status-badge status-${{normalized}}">${{icon}} ${{status}}</span>`;
        }}

        function getOverallStatusBadge(test) {{
            if (test.status === 'failed') {{
                return '<span class="status-badge status-failed">❌ failed</span>';
            }}
            if (test.has_advisories) {{
                // Build advisory layers badges
                let layerBadges = '';
                if (test.advisory_layers && test.advisory_layers.length > 0) {{
                    layerBadges = '<span class="advisory-layers">' +
                        test.advisory_layers.map(l => `<span class="layer-badge-small ${{l.toLowerCase()}}">${{l}}</span>`).join('') +
                        '</span>';
                }}
                return `<span class="status-badge status-advisory">⚠️ advisory</span>${{layerBadges}}`;
            }}
            return '<span class="status-badge status-passed">✅ passed</span>';
        }}

        function getTemplateLinks(test) {{
            const links = [];
            if (test.cfn_template_json) {{
                const filename = test.cfn_template_json.split('/').pop();
                links.push(`<a href="cfn-templates/${{filename}}" download class="template-link">JSON</a>`);
            }}
            if (test.cfn_template_yaml) {{
                const filename = test.cfn_template_yaml.split('/').pop();
                links.push(`<a href="cfn-templates/${{filename}}" download class="template-link">YAML</a>`);
            }}
            return links.length > 0 ? links.join(' ') : '-';
        }}

        function getDetailContent(test) {{
            const parts = [];

            // Deployment context (collapsible summary)
            if (test.deployment_context) {{
                try {{
                    const ctx = JSON.parse(test.deployment_context);
                    parts.push('📋 DEPLOYMENT CONTEXT:\\n' + JSON.stringify(ctx, null, 2));
                }} catch {{
                    parts.push('📋 DEPLOYMENT CONTEXT:\\n' + test.deployment_context);
                }}
            }}

            // Negative test info
            if (test.is_negative_test && test.rejection_layers) {{
                parts.push('\\n🔒 REJECTION LAYERS: ' + test.rejection_layers);
            }}

            // ═══════════════════════════════════════════════════════════════
            // LAYER 1: CDK-NAG (Static Analysis / Infrastructure Best Practices)
            // Validates synthesized CloudFormation against AWS best practices
            // Severity: Critical for security issues, Medium for configuration warnings
            // ═══════════════════════════════════════════════════════════════
            const hasL1Content = (test.cdk_nag_violations?.length > 0) || (test.cdk_nag_warnings?.length > 0);
            if (hasL1Content) {{
                let l1Content = '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                l1Content += '\\n🔍 LAYER 1: CDK-NAG (Infrastructure Best Practices)';
                l1Content += '\\n   Validates CloudFormation against AWS security best practices';
                l1Content += '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';

                if (test.cdk_nag_violations?.length > 0) {{
                    l1Content += '\\n\\n❌ VIOLATIONS [Severity: Critical]';
                    l1Content += '\\n   Audit Note: These findings block deployment and must be remediated';
                    l1Content += '\\n' + test.cdk_nag_violations.map(v => '  • ' + v).join('\\n');
                }}

                if (test.cdk_nag_warnings?.length > 0) {{
                    l1Content += '\\n\\n⚠️ ADVISORIES [Severity: Medium]';
                    l1Content += '\\n   Audit Note: Review recommended - may require compensating controls';
                    l1Content += '\\n' + test.cdk_nag_warnings.map(v => '  ⚠ ' + v).join('\\n');
                }}

                parts.push(l1Content);
            }}

            // ═══════════════════════════════════════════════════════════════
            // LAYER 2: FRAMEWORKRULES (Business Logic / Compliance Controls)
            // Enforces organization-specific security and compliance policies
            // Severity: Critical for compliance violations, High for policy warnings
            // ═══════════════════════════════════════════════════════════════
            const hasL2Content = (test.installed_rules?.length > 0) || (test.framework_rules_violations?.length > 0) || (test.framework_rules_advisories?.length > 0) || (test.framework_rules_known_gaps?.length > 0);
            if (hasL2Content) {{
                let l2Content = '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                l2Content += '\\n📋 LAYER 2: FRAMEWORKRULES (Compliance Controls)';
                l2Content += '\\n   Enforces regulatory compliance requirements (SOC2, HIPAA, PCI-DSS, GDPR)';
                l2Content += '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';

                if (test.installed_rules?.length > 0) {{
                    l2Content += '\\n\\n📦 ACTIVE COMPLIANCE RULES:';
                    l2Content += '\\n' + test.installed_rules.map(r => '  ✓ ' + r).join('\\n');
                }}

                if (test.framework_rules_violations?.length > 0) {{
                    l2Content += '\\n\\n❌ VIOLATIONS [Severity: Critical]';
                    l2Content += '\\n   Audit Note: Non-compliant configurations - must be remediated before production';
                    l2Content += '\\n' + test.framework_rules_violations.map(v => '  • ' + v).join('\\n');
                }}

                if (test.framework_rules_advisories?.length > 0) {{
                    l2Content += '\\n\\n⚠️ ADVISORIES [Severity: High]';
                    l2Content += '\\n   Audit Note: Configuration recommendations - document justification if not implemented';
                    l2Content += '\\n' + test.framework_rules_advisories.map(v => '  ⚠ ' + v).join('\\n');
                }}

                if (test.framework_rules_known_gaps?.length > 0) {{
                    l2Content += '\\n\\n📌 KNOWN GAPS [Severity: Medium]';
                    l2Content += '\\n   Audit Note: Documented deviations - ensure compensating controls are in place';
                    l2Content += '\\n' + test.framework_rules_known_gaps.map(g => '  📌 ' + g).join('\\n');
                }}

                parts.push(l2Content);
            }}

            // ═══════════════════════════════════════════════════════════════
            // LAYER 3: CFN-GUARD (Policy-as-Code / Template Validation)
            // Validates CloudFormation templates against custom guard rules
            // Severity: Critical for violations, Medium for warnings
            // ═══════════════════════════════════════════════════════════════
            const hasL3Content = (test.cfn_guard_violations?.length > 0) || (test.cfn_guard_warnings?.length > 0);
            if (hasL3Content) {{
                let l3Content = '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                l3Content += '\\n📜 LAYER 3: CFN-GUARD (Policy-as-Code)';
                l3Content += '\\n   Validates templates against AWS Guard rules (HIPAA, PCI-DSS, GDPR)';
                l3Content += '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';

                if (test.cfn_guard_violations?.length > 0) {{
                    l3Content += '\\n\\n❌ VIOLATIONS [Severity: Critical]';
                    l3Content += '\\n   Audit Note: Template policy violations - must be remediated';
                    l3Content += '\\n' + test.cfn_guard_violations.map(v => '  • ' + v).join('\\n');
                }}

                if (test.cfn_guard_warnings?.length > 0) {{
                    l3Content += '\\n\\n⚠️ ADVISORIES [Severity: Medium]';
                    l3Content += '\\n   Audit Note: Policy recommendations - review for applicability';
                    l3Content += '\\n' + test.cfn_guard_warnings.map(v => '  ⚠ ' + v).join('\\n');
                }}

                parts.push(l3Content);
            }}

            // ═══════════════════════════════════════════════════════════════
            // LAYER 4: AWS CONFIG (Runtime Compliance / Continuous Monitoring)
            // Lists AWS Config rules that will monitor deployed resources
            // Severity: Informational - these are detective controls
            // ═══════════════════════════════════════════════════════════════
            if (test.aws_config_status && test.aws_config_status !== 'skipped') {{
                let l4Content = '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                l4Content += '\\n🔎 LAYER 4: AWS CONFIG (Runtime Compliance)';
                l4Content += '\\n   Continuous compliance monitoring for deployed resources';
                l4Content += '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                l4Content += '\\n\\n📊 STATUS: ' + test.aws_config_status;
                l4Content += '\\n   Audit Note: These rules provide detective controls post-deployment';

                if (test.aws_config_rules?.length > 0) {{
                    l4Content += '\\n\\n📋 CONFIG RULES TO BE DEPLOYED:';
                    l4Content += '\\n' + test.aws_config_rules.map(r => '  ✓ ' + r).join('\\n');
                }}

                parts.push(l4Content);
            }}

            // Error message section
            if (test.error_message) {{
                let errContent = '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                errContent += '\\n🚨 TEST ERROR MESSAGE';
                errContent += '\\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
                errContent += '\\n' + test.error_message;
                parts.push(errContent);
            }}

            return parts.length > 0 ? parts.join('\\n\\n') : 'No additional details available';
        }}

        function toggleDetail(idx) {{
            const detailRow = document.getElementById('detail-' + idx);
            if (detailRow) {{
                detailRow.classList.toggle('expanded');
                if (detailRow.classList.contains('expanded')) {{
                    expandedRows.add(idx);
                }} else {{
                    expandedRows.delete(idx);
                }}
            }}
        }}

        function displayPagination() {{
            const totalPages = Math.ceil(filteredTests.length / testsPerPage);
            const pagination = document.getElementById('pagination');

            if (totalPages <= 1) {{
                pagination.innerHTML = '';
                return;
            }}

            let html = `<button ${{currentPage === 1 ? 'disabled' : ''}} onclick="goToPage(${{currentPage - 1}})">← Prev</button>`;

            for (let i = 1; i <= totalPages; i++) {{
                if (i === 1 || i === totalPages || (i >= currentPage - 2 && i <= currentPage + 2)) {{
                    html += `<button class="${{i === currentPage ? 'active' : ''}}" onclick="goToPage(${{i}})">${{i}}</button>`;
                }} else if (i === currentPage - 3 || i === currentPage + 3) {{
                    html += '<button disabled>...</button>';
                }}
            }}

            html += `<button ${{currentPage === totalPages ? 'disabled' : ''}} onclick="goToPage(${{currentPage + 1}})">Next →</button>`;
            pagination.innerHTML = html;
        }}

        function goToPage(page) {{
            currentPage = page;
            displayResults();
            document.querySelector('.results-table-container').scrollIntoView({{ behavior: 'smooth' }});
        }}

        // Event listeners
        document.getElementById('filter-search').addEventListener('keyup', function(e) {{
            if (e.key === 'Enter') applyFilters();
        }});

        function initChart() {{
            if (typeof Chart === 'undefined') return;

            const ctx = document.getElementById('layerChart').getContext('2d');
            new Chart(ctx, {{
                type: 'bar',
                data: {{
                    labels: ['cdk-nag', 'FrameworkRules', 'cfn-guard', 'AWS Config'],
                    datasets: [{{
                        label: 'Passed',
                        data: [{cdk_nag_passed}, {framework_rules_passed}, {cfn_guard_passed}, {total_tests}],
                        backgroundColor: ['#3498db', '#2ecc71', '#f39c12', '#9b59b6']
                    }}]
                }},
                options: {{
                    responsive: true,
                    plugins: {{
                        title: {{ display: true, text: 'Multi-Layer Validation Coverage' }},
                        legend: {{ display: false }}
                    }},
                    scales: {{
                        y: {{ beginAtZero: true, max: {total_tests} }}
                    }}
                }}
            }});
        }}
    </script>
</body>
</html>"""

        html_file = self.output_dir / "compliance-validation-dashboard.html"
        with open(html_file, 'w') as f:
            f.write(html_content)

        print(f"✅ HTML dashboard saved to: {html_file}")
        return html_file

    def run(self):
        """Run the complete report generation process."""
        print("=" * 80)
        print("CloudForge Compliance Report Generator")
        print("=" * 80)

        # Ensure output directory exists
        self.output_dir.mkdir(parents=True, exist_ok=True)

        # Run tests (writes results incrementally)
        success = self.run_compliance_tests()

        # Load incremental results into memory for report generation
        self._load_incremental_results()

        # Generate reports even if tests failed (to show what failed)
        json_file = self.generate_json_report()
        html_file = self.generate_html_dashboard()

        # Print summary
        total = len(self.results)
        passed = len([r for r in self.results if r.status == "passed"])
        failed = len([r for r in self.results if r.status == "failed"])

        print("\n" + "=" * 80)
        print("📊 Summary:")
        print(f"  Total tests: {total}")
        print(f"  Passed: {passed} ({passed/total*100:.1f}%)" if total > 0 else "  Passed: 0")
        print(f"  Failed: {failed} ({failed/total*100:.1f}%)" if total > 0 else "  Failed: 0")
        print(f"\n📋 Reports generated:")
        print(f"  - JSON: {json_file}")
        print(f"  - HTML: {html_file}")
        print("=" * 80)

        return success


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description='Generate CloudForge compliance validation dashboard with multi-layer validation results'
    )
    parser.add_argument(
        '--skip-tests',
        action='store_true',
        help='Skip running tests and use existing JUnit XML results (faster when tests already ran)'
    )

    args = parser.parse_args()

    # Find project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent.parent

    generator = ComplianceReportGenerator(project_root)

    if args.skip_tests:
        # Skip test execution and load results from existing saved JUnit XML files
        print("⏭️  Skipping test execution, loading existing results...")
        generator.output_dir.mkdir(parents=True, exist_ok=True)
        # Try to use saved split XML files first (more complete), fall back to incremental
        generator._parse_all_junit_xml_files()
        generator._load_incremental_results()

        # Generate reports from loaded results
        if generator.results:
            generator.generate_json_report()
            generator.generate_html_dashboard()
            success = True
        else:
            print("❌ No existing test results found. Run without --skip-tests first.")
            success = False
    else:
        success = generator.run()

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
