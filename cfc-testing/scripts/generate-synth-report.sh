#!/bin/bash

# Generate HTML Report for Comprehensive Synthesis Tests
# Creates an interactive HTML dashboard showing synthesis test results across all security profiles and runtimes

set -e

OUTPUT_DIR="$(dirname "$0")/validation-results"
mkdir -p "$OUTPUT_DIR"

# Generate HTML report
cat > "$OUTPUT_DIR/comprehensive-synth-report.html" << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Comprehensive Synthesis Test Results</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        .header {
            text-align: center;
            color: white;
            padding: 40px 20px;
        }
        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.2);
        }
        .header p {
            font-size: 1.1em;
            opacity: 0.9;
        }
        .content {
            background: white;
            border-radius: 12px;
            padding: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            margin-bottom: 30px;
        }
        .back-link {
            display: inline-block;
            margin-bottom: 20px;
            color: #667eea;
            text-decoration: none;
            font-weight: 500;
        }
        .back-link:hover {
            text-decoration: underline;
        }
        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin: 30px 0;
        }
        .summary-card {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
            border-left: 4px solid #667eea;
        }
        .summary-number {
            font-size: 2.5em;
            font-weight: bold;
            color: #667eea;
            margin-bottom: 5px;
        }
        .summary-label {
            color: #7f8c8d;
            font-size: 0.9em;
        }
        .test-results {
            margin-top: 30px;
        }
        .test-section {
            margin: 30px 0;
        }
        .test-section h3 {
            color: #2c3e50;
            padding-bottom: 10px;
            border-bottom: 2px solid #ecf0f1;
            margin-bottom: 20px;
        }
        .test-item {
            background: #f8f9fa;
            padding: 15px 20px;
            margin: 10px 0;
            border-radius: 6px;
            border-left: 4px solid #27ae60;
        }
        .test-item.failed {
            border-left-color: #e74c3c;
            background: #fff5f5;
        }
        .test-item-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
        }
        .test-name {
            font-weight: 600;
            color: #2c3e50;
        }
        .test-status {
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 0.85em;
            font-weight: 600;
        }
        .test-status.success {
            background: #d4edda;
            color: #155724;
        }
        .test-status.failed {
            background: #f8d7da;
            color: #721c24;
        }
        .test-details {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
            gap: 10px;
            font-size: 0.9em;
            color: #666;
        }
        .detail-item {
            display: flex;
            justify-content: space-between;
        }
        .detail-label {
            font-weight: 500;
        }
        pre {
            background: #f6f8fa;
            padding: 20px;
            border-radius: 6px;
            overflow-x: auto;
            font-size: 0.85em;
            line-height: 1.6;
            margin-top: 20px;
        }
        .info-box {
            background: #e8f4f8;
            border-left: 4px solid #3498db;
            padding: 20px;
            margin: 20px 0;
            border-radius: 6px;
        }
        .info-box h4 {
            color: #2c3e50;
            margin-bottom: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 Comprehensive Synthesis Tests</h1>
            <p>Complete Infrastructure Synthesis Validation</p>
        </div>

        <div class="content">
            <a href="../index.html" class="back-link">← Back to Dashboard</a>

            <div class="info-box">
                <h4>📋 Test Overview</h4>
                <p>This report shows the results of comprehensive CDK synthesis tests across all infrastructure combinations. These tests validate that CloudFormation templates can be generated successfully for all supported runtime types, security profiles, and configurations.</p>
            </div>

            <div id="summary-section"></div>
            <div id="results-section"></div>

            <h3 style="margin-top: 40px;">Raw Test Output</h3>
            <pre id="raw-output">Loading test results...</pre>
        </div>
    </div>

    <script>
        // Function to parse the comprehensive synth test output
        async function loadTestResults() {
            try {
                // Try to load from multiple possible locations
                const possiblePaths = [
                    'comprehensive-synth.log',
                    '../comprehensive-synth.log',
                    'synth-results/comprehensive-synth.log'
                ];

                let response = null;
                for (const path of possiblePaths) {
                    try {
                        const r = await fetch(path);
                        if (r.ok) {
                            response = r;
                            break;
                        }
                    } catch (e) {
                        continue;
                    }
                }

                if (!response || !response.ok) {
                    document.getElementById('raw-output').textContent =
                        'Test results not available. The comprehensive synthesis tests may not have run yet.';
                    return;
                }

                const text = await response.text();
                document.getElementById('raw-output').textContent = text;

                // Parse results
                const lines = text.split('\n');
                const results = {
                    total: 0,
                    successful: 0,
                    failed: 0,
                    tests: []
                };

                let currentTest = null;

                for (const line of lines) {
                    // Match test start
                    if (line.includes('Testing:')) {
                        const match = line.match(/Testing:\s+(\w+)\s+\+\s+(\w+)\s+\+\s+(\w+)/);
                        if (match) {
                            currentTest = {
                                runtime: match[1],
                                profile: match[2],
                                config: match[3],
                                status: 'running',
                                details: {}
                            };
                        }
                    }

                    // Match success
                    if (line.includes('✅ Synthesis successful') && currentTest) {
                        currentTest.status = 'success';
                        results.successful++;
                        results.tests.push({...currentTest});
                    }

                    // Match resource counts
                    if (line.includes('Security Groups:') && currentTest) {
                        const match = line.match(/Security Groups:\s+(\d+)/);
                        if (match) currentTest.details.securityGroups = match[1];
                    }
                    if (line.includes('IAM Roles:') && currentTest) {
                        const match = line.match(/IAM Roles:\s+(\d+)/);
                        if (match) currentTest.details.iamRoles = match[1];
                    }
                    if (line.includes('Route53 records') && currentTest) {
                        const match = line.match(/(\d+)\s+Route53 records/);
                        if (match) currentTest.details.route53Records = match[1];
                    }
                }

                // Extract total counts
                const totalMatch = text.match(/Total Tests:\s+(\d+)/);
                const successMatch = text.match(/Successful:\s+(\d+)/);
                const failedMatch = text.match(/Failed:\s+(\d+)/);

                if (totalMatch) results.total = parseInt(totalMatch[1]);
                if (successMatch) results.successful = parseInt(successMatch[1]);
                if (failedMatch) results.failed = parseInt(failedMatch[1]);

                displayResults(results);

            } catch (error) {
                document.getElementById('raw-output').textContent =
                    'Error loading test results: ' + error.message;
            }
        }

        function displayResults(results) {
            // Display summary
            const summaryHTML = `
                <div class="summary-grid">
                    <div class="summary-card">
                        <div class="summary-number">${results.total || results.tests.length * 2}</div>
                        <div class="summary-label">Total Tests</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-number" style="color: #27ae60;">${results.successful || results.tests.length}</div>
                        <div class="summary-label">Successful</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-number" style="color: #e74c3c;">${results.failed || 0}</div>
                        <div class="summary-label">Failed</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-number">${Math.round((results.successful / (results.total || results.tests.length)) * 100) || 100}%</div>
                        <div class="summary-label">Success Rate</div>
                    </div>
                </div>
            `;
            document.getElementById('summary-section').innerHTML = summaryHTML;

            // Group tests by runtime
            const ec2Tests = results.tests.filter(t => t.runtime === 'EC2');
            const fargateTests = results.tests.filter(t => t.runtime === 'FARGATE');

            let resultsHTML = '<div class="test-results">';

            if (ec2Tests.length > 0) {
                resultsHTML += '<div class="test-section"><h3>🖥️ EC2 Runtime Tests</h3>';
                ec2Tests.forEach(test => {
                    resultsHTML += generateTestItem(test);
                });
                resultsHTML += '</div>';
            }

            if (fargateTests.length > 0) {
                resultsHTML += '<div class="test-section"><h3>🐳 Fargate Runtime Tests</h3>';
                fargateTests.forEach(test => {
                    resultsHTML += generateTestItem(test);
                });
                resultsHTML += '</div>';
            }

            resultsHTML += '</div>';
            document.getElementById('results-section').innerHTML = resultsHTML;
        }

        function generateTestItem(test) {
            const statusClass = test.status === 'success' ? 'success' : 'failed';
            const statusIcon = test.status === 'success' ? '✅' : '❌';

            return `
                <div class="test-item ${statusClass === 'failed' ? 'failed' : ''}">
                    <div class="test-item-header">
                        <div class="test-name">${test.runtime} + ${test.profile} + ${test.config}</div>
                        <div class="test-status ${statusClass}">${statusIcon} ${test.status.toUpperCase()}</div>
                    </div>
                    <div class="test-details">
                        ${test.details.securityGroups ? `<div class="detail-item"><span class="detail-label">Security Groups:</span><span>${test.details.securityGroups}</span></div>` : ''}
                        ${test.details.iamRoles ? `<div class="detail-item"><span class="detail-label">IAM Roles:</span><span>${test.details.iamRoles}</span></div>` : ''}
                        ${test.details.route53Records ? `<div class="detail-item"><span class="detail-label">Route53 Records:</span><span>${test.details.route53Records}</span></div>` : ''}
                    </div>
                </div>
            `;
        }

        // Load results on page load
        loadTestResults();
    </script>
</body>
</html>
EOF

echo "✅ Generated comprehensive synthesis test HTML report: $OUTPUT_DIR/comprehensive-synth-report.html"
