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
import xml.etree.ElementTree as ET


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


class ComplianceReportGenerator:
    """Generates compliance validation reports by running tests and parsing results."""

    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.cloudforge_api_dir = project_root / "cloudforge-api"
        self.output_dir = project_root / "cfc-testing" / "scripts" / "validation-results"
        self.results: List[ComplianceTestResult] = []
        self.incremental_results_file = self.output_dir / "compliance-results-incremental.jsonl"

    def run_compliance_tests(self) -> bool:
        """Run Maven compliance tests and stream output, parsing incrementally."""
        print("🧪 Running compliance validation tests with incremental parsing...")
        print(f"   Project root: {self.project_root}")
        print(f"   Working directory: {self.cloudforge_api_dir}")

        # Clear incremental results file
        if self.incremental_results_file.exists():
            self.incremental_results_file.unlink()

        # Run Maven tests with XML output
        cmd = [
            "mvn", "test",
            "-Dtest=TruthTableValidationTest#testComplianceFrameworkIntegrationCsv",
            "--batch-mode"
        ]

        try:
            output_file = self.output_dir / "compliance-test-output.log"

            # Start the Maven process
            print(f"   📝 Streaming output to: {output_file}")
            print(f"   📊 Incremental results: {self.incremental_results_file}")

            with open(output_file, 'w') as log_file:
                process = subprocess.Popen(
                    cmd,
                    cwd=str(self.cloudforge_api_dir),
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1  # Line buffered
                )

                # Stream output line by line and parse incrementally
                current_test = None
                test_count = 0

                for line in process.stdout:
                    log_file.write(line)
                    log_file.flush()

                    # Parse each line and update results incrementally
                    result = self._parse_line_incremental(line, current_test)
                    if result:
                        current_test = result
                        if "Testing compliance configuration" in line:
                            test_count += 1
                            if test_count % 10 == 0:
                                print(f"   ✓ Processed {test_count} tests...")

                process.wait(timeout=1800)  # 30 minute timeout

                print(f"   ✅ Test execution completed")
                print(f"   📊 Processed {test_count} test configurations")

                # Parse JUnit XML for final details
                self._parse_junit_xml_incremental()

                return process.returncode == 0

        except subprocess.TimeoutExpired:
            print("   ❌ Tests timed out after 30 minutes")
            if process:
                process.kill()
            return False
        except Exception as e:
            print(f"   ❌ Failed to run tests: {e}")
            return False

    def _parse_line_incremental(self, line: str, current_test: Optional[ComplianceTestResult]) -> Optional[ComplianceTestResult]:
        """Parse a single line of output and update results incrementally."""
        # Detect test start
        match = re.search(r'Testing compliance configuration \(CSV\): (\S+) \[(.+?)\]', line)
        if match:
            config_name = match.group(1)
            framework = match.group(2)

            # Extract runtime and network mode from config name
            parts = config_name.split('_')
            # Handle negative tests that start with FAIL_
            if parts[0] == 'FAIL':
                runtime = parts[1] if len(parts) > 1 else "unknown"
            else:
                runtime = parts[0] if len(parts) > 0 else "unknown"
            network_mode = parts[-1] if len(parts) > 0 else "unknown"

            current_test = ComplianceTestResult(config_name, framework, runtime, network_mode)
            return current_test

        if current_test:
            # Parse deployment context JSON
            if "DEPLOYMENT_CONTEXT_JSON:" in line:
                match = re.search(r'DEPLOYMENT_CONTEXT_JSON:\s*(\{.*\})', line)
                if match:
                    current_test.deployment_context = match.group(1)

            # Parse Layer 1 (cdk-nag) results
            if "✅ Layer 1 (cdk-nag):" in line:
                current_test.cdk_nag_status = "passed"
                current_test.cdk_nag_packs_applied = 1
                current_test._current_layer = None
            elif "❌ Layer 1 (cdk-nag)" in line:
                current_test.cdk_nag_status = "failed"
                current_test._current_layer = "cdk_nag"

            # Detect FrameworkRules validation failure (appears before Layer summary)
            if "validation failed with" in line and "violations" in line and "SEVERE:" in line:
                # This indicates FrameworkRules is about to report violations
                current_test._current_layer = "framework_rules"
                current_test.framework_rules_status = "failed"

            # Parse Layer 2 (FrameworkRules) results
            if "✅ Layer 2 (FrameworkRules):" in line:
                current_test.framework_rules_status = "passed"
                current_test._current_layer = None
            elif "❌ Layer 2 (FrameworkRules):" in line:
                current_test.framework_rules_status = "failed"
                current_test._current_layer = "framework_rules"
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
            elif "❌ Layer 3 (cfn-guard):" in line:
                current_test.cfn_guard_status = "failed"
                current_test._current_layer = "cfn_guard"
            elif "⏭️  Layer 3 (cfn-guard): Skipped (no template)" in line:
                current_test.cfn_guard_status = "skipped (no template)"
                current_test._current_layer = None
            elif "⏭️  Layer 3 (cfn-guard): Skipped" in line:
                current_test.cfn_guard_status = "skipped"
                current_test._current_layer = None

            # Parse Layer 4 (AWS Config) results - extract rule count
            if "✅ Layer 4 (AWS Config):" in line and "rules would be deployed" in line:
                match = re.search(r'(\d+)\s+rules would be deployed', line)
                if match:
                    current_test.aws_config_status = f"passed ({match.group(1)} rules)"
                else:
                    current_test.aws_config_status = "passed"
                current_test._current_layer = None
            elif "⏭️  Layer 4 (AWS Config): Skipped" in line:
                current_test.aws_config_status = "skipped"
                current_test._current_layer = None

            # Capture individual violation messages
            # Format 1: "SEVERE:   - SOC2-CC6.2-Auth: ..." (FrameworkRules)
            # Format 2: "      - violation message" (cdk-nag, cfn-guard)
            violation_match = re.match(r'SEVERE:\s+- (.+)', line) or re.match(r'\s+- (.+)', line)
            if violation_match and current_test._current_layer:
                violation = violation_match.group(1).strip()
                if current_test._current_layer == "cdk_nag":
                    current_test.cdk_nag_violations.append(violation)
                elif current_test._current_layer == "framework_rules":
                    current_test.framework_rules_violations.append(violation)
                elif current_test._current_layer == "cfn_guard":
                    current_test.cfn_guard_violations.append(violation)

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
            "layers": {
                "cdk_nag": {
                    "status": result.cdk_nag_status,
                    "packs_applied": result.cdk_nag_packs_applied,
                    "violations": result.cdk_nag_violations
                },
                "framework_rules": {
                    "status": result.framework_rules_status,
                    "violations": result.framework_rules_violations,
                    "known_gaps": result.framework_rules_known_gaps
                },
                "cfn_guard": {
                    "status": result.cfn_guard_status,
                    "violations": result.cfn_guard_violations
                },
                "aws_config": {
                    "status": result.aws_config_status
                }
            },
            "error_message": result.error_message,
            "deployment_context": result.deployment_context,
            "is_negative_test": result.is_negative_test,
            "rejection_layers": result.rejection_layers
        }

        # Append to JSONL file (one JSON object per line)
        with open(self.incremental_results_file, 'a') as f:
            f.write(json.dumps(result_dict) + '\n')

    def _parse_junit_xml_incremental(self):
        """Parse JUnit XML and create/update results with timing, error info, and layer status."""
        xml_file = self.cloudforge_api_dir / "target" / "surefire-reports" / \
                   "TEST-com.cloudforgeci.api.integration.deployment.TruthTableValidationTest.xml"

        if not xml_file.exists():
            print(f"   ⚠️  JUnit XML not found: {xml_file}")
            return

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

            updated_count = 0
            created_count = 0

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

                if system_out is not None and system_out.text:
                    text = system_out.text

                    # Extract config name and framework
                    match = re.search(r'Testing compliance configuration \(CSV\):\s*(\S+)\s+\[(.+?)\]', text)
                    if match:
                        config_name = match.group(1)
                        framework = match.group(2)
                        # Parse runtime and network mode from config name
                        parts = config_name.split('_')
                        # Handle negative tests that start with FAIL_
                        if parts[0] == 'FAIL':
                            runtime = parts[1] if len(parts) > 1 else "unknown"
                        else:
                            runtime = parts[0] if len(parts) > 0 else "unknown"
                        network_mode = parts[-1] if len(parts) > 0 else "unknown"

                    # Extract deployment context
                    ctx_match = re.search(r'DEPLOYMENT_CONTEXT_JSON:\s*(\{.*\})', text)
                    if ctx_match:
                        deployment_context = ctx_match.group(1)

                    # Parse Layer 1 (cdk-nag) status
                    if "✅ Layer 1 (cdk-nag):" in text:
                        layer_statuses['cdk_nag'] = 'passed'
                    elif "❌ Layer 1 (cdk-nag)" in text or "❌ Layer 1" in text:
                        layer_statuses['cdk_nag'] = 'failed'

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
                    aws_config_match = re.search(r'✅ Layer 4 \(AWS Config\):\s+(\d+)\s+rules would be deployed', text)
                    if aws_config_match:
                        layer_statuses['aws_config'] = f"passed ({aws_config_match.group(1)} rules)"
                    elif "⏭️  Layer 4 (AWS Config): Skipped" in text:
                        layer_statuses['aws_config'] = 'skipped'

                    # Check if this is a negative test that passed
                    if "NEGATIVE TEST PASSED" in text:
                        is_negative_test = True

                # Skip non-CSV tests (e.g., testTruthTableLoaded)
                if not config_name:
                    continue

                # Check for test failures
                failure = testcase.find('failure')
                error = testcase.find('error')
                failure_elem = failure if failure is not None else error
                test_failed = failure_elem is not None

                # Create or update result
                if config_name in results_by_config:
                    # Update existing result
                    result = results_by_config[config_name]
                    result['duration'] = time_val
                    result['layers']['cdk_nag']['status'] = layer_statuses['cdk_nag']
                    result['layers']['framework_rules']['status'] = layer_statuses['framework_rules']
                    result['layers']['cfn_guard']['status'] = layer_statuses['cfn_guard']
                    result['layers']['aws_config']['status'] = layer_statuses.get('aws_config', result['layers'].get('aws_config', {}).get('status', 'deployed'))
                    result['is_negative_test'] = is_negative_test

                    # For negative tests: test passing means deployment would fail
                    # For positive tests: test failing means deployment failed
                    if is_negative_test:
                        result['status'] = 'failed' if not test_failed else 'error'
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
                    # For negative tests: test passing means deployment would fail
                    # For positive tests: use normal test status
                    if is_negative_test:
                        status = 'failed' if not test_failed else 'error'
                    else:
                        status = 'failed' if test_failed else 'passed'

                    result = {
                        'config_name': config_name,
                        'framework': framework,
                        'runtime': runtime,
                        'network_mode': network_mode,
                        'status': status,
                        'duration': time_val,
                        'layers': {
                            'cdk_nag': {'status': layer_statuses['cdk_nag'], 'packs_applied': 1 if layer_statuses['cdk_nag'] == 'passed' else 0},
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

            print(f"   ✅ Updated {updated_count} results with JUnit XML timing data")
            if created_count > 0:
                print(f"   ✅ Created {created_count} new results from JUnit XML")

        except Exception as e:
            print(f"   ⚠️  Failed to parse JUnit XML: {e}")

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
                result.cdk_nag_status = data['layers']['cdk_nag']['status']
                result.cdk_nag_packs_applied = data['layers']['cdk_nag']['packs_applied']
                result.cdk_nag_violations = data['layers']['cdk_nag'].get('violations', [])
                result.framework_rules_status = data['layers']['framework_rules']['status']
                result.framework_rules_violations = data['layers']['framework_rules'].get('violations', [])
                result.framework_rules_known_gaps = data['layers']['framework_rules'].get('known_gaps', [])
                result.cfn_guard_status = data['layers']['cfn_guard']['status']
                result.cfn_guard_violations = data['layers']['cfn_guard'].get('violations', [])
                result.aws_config_status = data['layers']['aws_config']['status']
                result.error_message = data.get('error_message')
                result.deployment_context = data.get('deployment_context')
                result.is_negative_test = data.get('is_negative_test', False)
                result.rejection_layers = data.get('rejection_layers')
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
                runtime = parts[0] if len(parts) > 0 else "unknown"
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
            print(f"   ⚠️  Failed to parse JUnit XML: {e}")

    def _update_layer_statuses_for_failures(self):
        """Update layer statuses based on error message patterns."""
        for result in self.results:
            # Check if this was a FrameworkRules validation failure
            is_framework_rules_failure = False
            is_test_setup_failure = False
            if result.error_message and result.status == "failed":
                # FrameworkRules validation errors contain specific patterns
                framework_rules_patterns = [
                    'SOC2-', 'PCI-DSS-', 'HIPAA-', 'GDPR-',
                    'User authentication required',
                    'Private network mode required',
                    'Anti-malware protection required',
                    'File integrity monitoring required',
                    'SystemContext'  # FrameworkRules errors come from SystemContext
                ]
                is_framework_rules_failure = any(p in result.error_message for p in framework_rules_patterns)

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
        report = {
            "metadata": {
                "generated_at": datetime.now().isoformat(),
                "total_tests": len(self.results),
                "passed": len([r for r in self.results if r.status == "passed"]),
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
                "is_negative_test": result.is_negative_test,
                "rejection_layers": result.rejection_layers,
                "layers": {
                    "cdk_nag": {
                        "status": result.cdk_nag_status,
                        "packs_applied": result.cdk_nag_packs_applied,
                        "violations": result.cdk_nag_violations
                    },
                    "framework_rules": {
                        "status": result.framework_rules_status,
                        "violations": result.framework_rules_violations,
                        "known_gaps": result.framework_rules_known_gaps
                    },
                    "cfn_guard": {
                        "status": result.cfn_guard_status,
                        "violations": result.cfn_guard_violations
                    },
                    "aws_config": {
                        "status": result.aws_config_status
                    }
                },
                "error_message": result.error_message,
                "deployment_context": result.deployment_context
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
        duration = sum(r.duration for r in self.results)

        # Layer statistics
        cdk_nag_passed = len([r for r in self.results if r.cdk_nag_status == "passed"])
        cfn_guard_passed = len([r for r in self.results if r.cfn_guard_status == "passed"])
        framework_rules_passed = len([r for r in self.results if r.framework_rules_status == "passed"])

        # Framework breakdown
        frameworks = {}
        for result in self.results:
            if result.framework not in frameworks:
                frameworks[result.framework] = {"passed": 0, "failed": 0, "total": 0}
            frameworks[result.framework]["total"] += 1
            if result.status == "passed":
                frameworks[result.framework]["passed"] += 1
            else:
                frameworks[result.framework]["failed"] += 1

        html_content = f"""<!DOCTYPE html>
<html>
<head>
    <title>CloudForge Compliance Validation Dashboard</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; }}
        .container {{ max-width: 1400px; margin: 0 auto; background: white; border-radius: 10px; box-shadow: 0 10px 40px rgba(0,0,0,0.3); overflow: hidden; }}
        .header {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; }}
        .header h1 {{ font-size: 2.5em; margin-bottom: 10px; }}
        .header p {{ opacity: 0.9; font-size: 1.1em; }}
        .version-selector {{ text-align: center; margin: 15px 0; }}
        .version-dropdown {{ display: inline-block; background: rgba(255,255,255,0.2); border-radius: 25px; padding: 5px; }}
        .version-dropdown select {{ background: white; border: none; padding: 10px 20px; font-size: 1em; border-radius: 20px; cursor: pointer; color: #667eea; font-weight: 600; min-width: 200px; }}
        .version-dropdown select:focus {{ outline: none; box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.3); }}
        .back-link {{ display: inline-block; margin: 15px 0 0 30px; padding: 10px 20px; background: rgba(255,255,255,0.2); color: white; text-decoration: none; border-radius: 6px; font-weight: 500; transition: background 0.3s ease; }}
        .back-link:hover {{ background: rgba(255,255,255,0.3); }}

        .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; background: #f8f9fa; }}
        .stat-card {{ background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }}
        .stat-number {{ font-size: 2.5em; font-weight: bold; }}
        .stat-number.success {{ color: #27ae60; }}
        .stat-number.warning {{ color: #f39c12; }}
        .stat-number.danger {{ color: #e74c3c; }}
        .stat-label {{ color: #7f8c8d; margin-top: 5px; }}

        .section {{ padding: 30px; }}
        .section h2 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; margin-bottom: 20px; }}

        .layer-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }}
        .layer-card {{ background: #ecf0f1; padding: 20px; border-radius: 8px; border-left: 5px solid #3498db; }}
        .layer-card h3 {{ color: #2c3e50; margin-bottom: 10px; }}
        .layer-stats {{ font-size: 1.5em; font-weight: bold; color: #27ae60; }}
        .layer-badge {{ display: inline-block; padding: 5px 10px; border-radius: 3px; font-size: 12px; font-weight: bold; margin: 5px; }}
        .badge-layer1 {{ background: #3498db; color: white; }}
        .badge-layer2 {{ background: #2ecc71; color: white; }}
        .badge-layer3 {{ background: #f39c12; color: white; }}
        .badge-layer4 {{ background: #9b59b6; color: white; }}

        .results-table {{ width: 100%; border-collapse: collapse; margin: 20px 0; }}
        .results-table th {{ background: #34495e; color: white; padding: 15px; text-align: left; position: sticky; top: 0; }}
        .results-table td {{ padding: 12px; border-bottom: 1px solid #ecf0f1; }}
        .results-table tr:hover {{ background: #f8f9fa; }}
        .status-badge {{ display: inline-block; padding: 5px 10px; border-radius: 3px; font-size: 11px; font-weight: bold; }}
        .status-passed {{ background: #d4edda; color: #155724; }}
        .status-failed {{ background: #f8d7da; color: #721c24; }}
        .status-blocked {{ background: #f5c6cb; color: #721c24; }}
        .status-skipped {{ background: #fff3cd; color: #856404; }}
        .status-unknown {{ background: #e2e3e5; color: #383d41; }}

        .config-name {{ cursor: pointer; color: #3498db; text-decoration: underline; }}
        .config-name:hover {{ color: #2980b9; }}
        .detail-row {{ display: none; background: #f8f9fa; }}
        .detail-row.expanded {{ display: table-row; }}
        .detail-content {{ padding: 15px; font-family: monospace; font-size: 12px; white-space: pre-wrap; word-break: break-word; background: #2c3e50; color: #ecf0f1; border-radius: 5px; max-height: 300px; overflow-y: auto; }}

        .framework-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }}
        .framework-card {{ background: white; border: 2px solid #ecf0f1; border-radius: 8px; padding: 15px; text-align: center; }}
        .framework-name {{ font-weight: bold; color: #2c3e50; margin-bottom: 10px; }}
        .framework-stats {{ font-size: 1.2em; color: #27ae60; }}

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
            <div class="version-selector">
                <div class="version-dropdown">
                    <select id="report-version" onchange="navigateToVersion(this.value)">
                        <option value="" selected>📅 Latest Reports</option>
                        <option value="../history/">📁 Browse History...</option>
                    </select>
                </div>
            </div>
        </div>

        <div class="stats">
            <div class="stat-card">
                <div class="stat-number success">{passed}</div>
                <div class="stat-label">Tests Passed</div>
            </div>
            <div class="stat-card">
                <div class="stat-number danger">{failed}</div>
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
            <div class="framework-grid">"""

        for framework, stats in sorted(frameworks.items()):
            pass_rate = (stats['passed'] / stats['total'] * 100) if stats['total'] > 0 else 0
            html_content += f"""
                <div class="framework-card">
                    <div class="framework-name">{framework}</div>
                    <div class="framework-stats">{stats['passed']}/{stats['total']}</div>
                    <div style="margin-top: 5px; font-size: 12px; color: #7f8c8d;">{pass_rate:.0f}% pass rate</div>
                </div>"""

        html_content += """
            </div>
        </div>

        <div class="section">
            <h2>📋 Detailed Test Results</h2>
            <div style="max-height: 600px; overflow-y: auto;">
                <table class="results-table">
                    <thead>
                        <tr>
                            <th>Configuration</th>
                            <th>Framework</th>
                            <th>Runtime</th>
                            <th>cdk-nag</th>
                            <th>FrameworkRules</th>
                            <th>cfn-guard</th>
                            <th>AWS Config</th>
                            <th>Status</th>
                            <th>Duration</th>
                        </tr>
                    </thead>
                    <tbody>"""

        for idx, result in enumerate(sorted(self.results, key=lambda r: (r.framework, r.runtime, r.network_mode))):
            status_class = "status-passed" if result.status == "passed" else "status-failed"

            cdk_nag_class = f"status-{result.cdk_nag_status}"
            fr_class = f"status-{result.framework_rules_status}"
            cfg_class = f"status-{result.cfn_guard_status}"
            aws_config_class = f"status-{result.aws_config_status.split()[0]}"  # Extract status word (e.g., "passed" from "passed (64 rules)")

            # Build detail content with deployment context, violations, and error message
            detail_parts = []

            # Add deployment context
            if result.deployment_context:
                context_formatted = result.deployment_context.replace(', ', ',\n  ').replace('{', '{\n  ').replace('}', '\n}')
                detail_parts.append(f"📋 DEPLOYMENT CONTEXT:\n{context_formatted}")

            # Add rejection layers for negative tests
            if result.is_negative_test and result.rejection_layers:
                detail_parts.append(f"\n🔒 REJECTION LAYERS: {result.rejection_layers}")

            # Add Layer 1 (cdk-nag) violations
            if result.cdk_nag_violations:
                violations_text = "\n".join(f"  • {v}" for v in result.cdk_nag_violations)
                detail_parts.append(f"\n❌ LAYER 1 (cdk-nag) VIOLATIONS:\n{violations_text}")

            # Add Layer 2 (FrameworkRules) violations
            if result.framework_rules_violations:
                violations_text = "\n".join(f"  • {v}" for v in result.framework_rules_violations)
                detail_parts.append(f"\n❌ LAYER 2 (FrameworkRules) VIOLATIONS:\n{violations_text}")

            # Add Layer 2 known gaps
            if result.framework_rules_known_gaps:
                gaps_text = "\n".join(f"  ⚠ {g}" for g in result.framework_rules_known_gaps)
                detail_parts.append(f"\n⚠️  KNOWN GAPS:\n{gaps_text}")

            # Add Layer 3 (cfn-guard) violations
            if result.cfn_guard_violations:
                violations_text = "\n".join(f"  • {v}" for v in result.cfn_guard_violations)
                detail_parts.append(f"\n❌ LAYER 3 (cfn-guard) VIOLATIONS:\n{violations_text}")

            # Add error message (from JUnit failure)
            if result.error_message:
                detail_parts.append(f"\n📄 TEST ERROR MESSAGE:\n{result.error_message}")

            detail_content = html_module.escape('\n\n'.join(detail_parts) if detail_parts else "No additional details available")

            # Show negative test indicator
            neg_test_badge = '<span class="status-badge" style="background: #e8daef; color: #6c3483; margin-left: 5px;">NEG</span>' if result.is_negative_test else ''

            # Format config name with violations count if any
            violations_count = len(result.cdk_nag_violations) + len(result.framework_rules_violations) + len(result.cfn_guard_violations)
            violations_indicator = f' <span style="color: #e74c3c; font-size: 10px;">({violations_count} violations)</span>' if violations_count > 0 else ''

            html_content += f"""
                        <tr>
                            <td style="font-family: monospace; font-size: 11px;"><span class="config-name" onclick="toggleDetail({idx})">&#9658; {result.config_name}</span>{neg_test_badge}{violations_indicator}</td>
                            <td><strong>{result.framework}</strong></td>
                            <td>{result.runtime}</td>
                            <td><span class="status-badge {cdk_nag_class}">{result.cdk_nag_status}</span></td>
                            <td><span class="status-badge {fr_class}">{result.framework_rules_status}</span></td>
                            <td><span class="status-badge {cfg_class}">{result.cfn_guard_status}</span></td>
                            <td><span class="status-badge {aws_config_class}">{result.aws_config_status}</span></td>
                            <td><span class="status-badge {status_class}">{result.status}</span></td>
                            <td>{result.duration:.2f}s</td>
                        </tr>
                        <tr class="detail-row" id="detail-{idx}">
                            <td colspan="9"><div class="detail-content">{detail_content}</div></td>
                        </tr>"""

        html_content += f"""
                    </tbody>
                </table>
            </div>
        </div>

        <div class="section">
            <h2>📈 Validation Charts</h2>
            <div class="chart-container">
                <canvas id="layerChart" width="400" height="200"></canvas>
            </div>
        </div>
    </div>

    <script>
        // Navigate to selected version
        function navigateToVersion(path) {{
            if (path) {{
                window.location.href = path;
            }}
        }}

        // Load historical report dates dynamically
        async function loadHistoricalDates() {{
            try {{
                const response = await fetch('../history/index.html');
                if (!response.ok) return;

                const html = await response.text();
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');

                // Extract dates from archive links (format: YYYY-MM-DD)
                const dateLinks = doc.querySelectorAll('.date-list a');
                const dates = Array.from(dateLinks).map(link => {{
                    const href = link.getAttribute('href');
                    const match = href.match(/(\\d{{4}}-\\d{{2}}-\\d{{2}})/);
                    return match ? match[1] : null;
                }}).filter(date => date !== null);

                // Add dates to dropdown
                const select = document.getElementById('report-version');
                dates.forEach(date => {{
                    const option = document.createElement('option');
                    option.value = `../history/${{date}}/validation/compliance-validation-dashboard.html`;
                    option.textContent = `📅 ${{date}}`;
                    select.appendChild(option);
                }});
            }} catch (err) {{
                console.log('No historical reports available yet');
            }}
        }}

        // Load historical dates on page load
        loadHistoricalDates();

        // Toggle detail row visibility
        function toggleDetail(idx) {{
            const detailRow = document.getElementById('detail-' + idx);
            if (detailRow) {{
                detailRow.classList.toggle('expanded');
            }}
        }}

        // Layer validation chart - wait for Chart.js to load
        window.addEventListener('load', function() {{
            if (typeof Chart !== 'undefined') {{
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
                            title: {{
                                display: true,
                                text: 'Multi-Layer Validation Coverage'
                            }},
                            legend: {{
                                display: false
                            }}
                        }},
                        scales: {{
                            y: {{
                                beginAtZero: true,
                                max: {total_tests}
                            }}
                        }}
                    }}
                }});
            }} else {{
                console.error('Chart.js library failed to load');
            }}
        }});
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
        # Skip test execution and load results from existing JUnit XML
        print("⏭️  Skipping test execution, loading existing results...")
        generator.output_dir.mkdir(parents=True, exist_ok=True)
        generator._parse_junit_xml_incremental()
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
