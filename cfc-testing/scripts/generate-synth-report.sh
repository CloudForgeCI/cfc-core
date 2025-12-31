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
            max-width: 1600px;
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
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 20px;
            margin: 30px 0;
        }
        .summary-card {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
            border-left: 4px solid #667eea;
            transition: transform 0.2s;
        }
        .summary-card:hover {
            transform: translateY(-2px);
        }
        .summary-card.success { border-left-color: #27ae60; }
        .summary-card.failed { border-left-color: #e74c3c; }
        .summary-card.advisory { border-left-color: #f39c12; }
        .summary-number {
            font-size: 2.5em;
            font-weight: bold;
            color: #667eea;
            margin-bottom: 5px;
        }
        .summary-card.success .summary-number { color: #27ae60; }
        .summary-card.failed .summary-number { color: #e74c3c; }
        .summary-card.advisory .summary-number { color: #f39c12; }
        .summary-label {
            color: #7f8c8d;
            font-size: 0.9em;
        }

        /* Filter Controls */
        .filter-controls {
            display: flex;
            flex-wrap: wrap;
            gap: 15px;
            margin: 25px 0;
            padding: 20px;
            background: #f8f9fa;
            border-radius: 8px;
            align-items: center;
        }
        .filter-group {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }
        .filter-group label {
            font-size: 0.85em;
            font-weight: 600;
            color: #7f8c8d;
            text-transform: uppercase;
        }
        .filter-group select, .filter-group input {
            padding: 8px 12px;
            border: 1px solid #ddd;
            border-radius: 6px;
            font-size: 0.95em;
            min-width: 150px;
        }
        .filter-group select:focus, .filter-group input:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }
        .filter-buttons {
            display: flex;
            gap: 10px;
            margin-left: auto;
        }
        .filter-btn {
            padding: 8px 16px;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-weight: 500;
            transition: all 0.2s;
        }
        .filter-btn.primary {
            background: #667eea;
            color: white;
        }
        .filter-btn.primary:hover {
            background: #5a6fd6;
        }
        .filter-btn.secondary {
            background: #e0e0e0;
            color: #333;
        }
        .filter-btn.secondary:hover {
            background: #d0d0d0;
        }

        /* Results Table */
        .results-table-container {
            overflow-x: auto;
            margin-top: 20px;
        }
        .results-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.95em;
        }
        .results-table th {
            background: #34495e;
            color: white;
            padding: 14px 12px;
            text-align: left;
            font-weight: 600;
            cursor: pointer;
            user-select: none;
            white-space: nowrap;
            position: relative;
        }
        .results-table th:hover {
            background: #3d566e;
        }
        .results-table th .sort-icon {
            margin-left: 6px;
            opacity: 0.5;
        }
        .results-table th.sorted .sort-icon {
            opacity: 1;
        }
        .results-table td {
            padding: 12px;
            border-bottom: 1px solid #eee;
            vertical-align: middle;
        }
        .results-table tbody tr:hover {
            background: #f8f9fa;
        }
        .results-table tbody tr.status-success { border-left: 4px solid #27ae60; }
        .results-table tbody tr.status-failed { border-left: 4px solid #e74c3c; }
        .results-table tbody tr.status-advisory { border-left: 4px solid #f39c12; }

        /* Status Badges */
        .status-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 0.85em;
            font-weight: 600;
        }
        .status-badge.success {
            background: #d4edda;
            color: #155724;
        }
        .status-badge.failed {
            background: #f8d7da;
            color: #721c24;
        }
        .status-badge.advisory {
            background: #fff3cd;
            color: #856404;
        }

        /* Layer Badges */
        .layer-badges {
            display: flex;
            gap: 4px;
            flex-wrap: wrap;
        }
        .layer-badge {
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 0.8em;
            font-weight: 500;
        }
        .layer-badge.pass { background: #d4edda; color: #155724; }
        .layer-badge.fail { background: #f8d7da; color: #721c24; }
        .layer-badge.warn { background: #fff3cd; color: #856404; }
        .layer-badge.skip { background: #e2e3e5; color: #383d41; }

        /* Runtime & Profile Badges */
        .runtime-badge {
            padding: 4px 10px;
            border-radius: 4px;
            font-size: 0.85em;
            font-weight: 500;
        }
        .runtime-badge.ec2 { background: #e3f2fd; color: #1565c0; }
        .runtime-badge.fargate { background: #fce4ec; color: #c2185b; }

        .profile-badge {
            padding: 4px 10px;
            border-radius: 4px;
            font-size: 0.85em;
            font-weight: 500;
        }
        .profile-badge.dev { background: #e8f5e9; color: #2e7d32; }
        .profile-badge.staging { background: #fff8e1; color: #f57f17; }
        .profile-badge.production { background: #ffebee; color: #c62828; }

        /* Resource counts */
        .resource-count {
            font-family: monospace;
            font-size: 0.9em;
            color: #555;
        }

        /* Details toggle */
        .details-toggle {
            color: #667eea;
            cursor: pointer;
            font-size: 0.85em;
            text-decoration: underline;
        }
        .details-content {
            display: none;
            padding: 10px;
            margin-top: 10px;
            background: #f8f9fa;
            border-radius: 6px;
            font-size: 0.85em;
        }
        .details-content.visible {
            display: block;
        }

        /* No results message */
        .no-results {
            text-align: center;
            padding: 40px;
            color: #7f8c8d;
        }

        /* Raw output section */
        .raw-section {
            margin-top: 40px;
        }
        .raw-section h3 {
            margin-bottom: 15px;
            color: #2c3e50;
        }
        .raw-toggle {
            display: inline-block;
            padding: 8px 16px;
            background: #667eea;
            color: white;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-weight: 500;
            margin-bottom: 15px;
        }
        .raw-toggle:hover {
            background: #5a6fd6;
        }
        pre {
            background: #1e1e1e;
            color: #d4d4d4;
            padding: 20px;
            border-radius: 8px;
            overflow-x: auto;
            font-size: 0.85em;
            line-height: 1.6;
            max-height: 500px;
            display: none;
        }
        pre.visible {
            display: block;
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
        .info-box.warning {
            background: #fff3cd;
            border-left-color: #ffc107;
        }

        /* Pagination */
        .pagination {
            display: flex;
            justify-content: center;
            gap: 5px;
            margin-top: 20px;
        }
        .pagination button {
            padding: 8px 14px;
            border: 1px solid #ddd;
            background: white;
            border-radius: 4px;
            cursor: pointer;
        }
        .pagination button:hover {
            background: #f0f0f0;
        }
        .pagination button.active {
            background: #667eea;
            color: white;
            border-color: #667eea;
        }
        .pagination button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        /* Result count */
        .result-count {
            color: #7f8c8d;
            font-size: 0.9em;
            margin-bottom: 15px;
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
                <p>This report shows the results of comprehensive CDK synthesis tests across all infrastructure combinations. Tests validate that CloudFormation templates can be generated successfully for all supported runtime types, security profiles, and configurations.</p>
            </div>

            <div id="summary-section"></div>

            <div class="filter-controls" id="filter-controls">
                <div class="filter-group">
                    <label>Runtime</label>
                    <select id="filter-runtime">
                        <option value="">All Runtimes</option>
                        <option value="EC2">EC2</option>
                        <option value="FARGATE">Fargate</option>
                    </select>
                </div>
                <div class="filter-group">
                    <label>Security Profile</label>
                    <select id="filter-profile">
                        <option value="">All Profiles</option>
                        <option value="DEV">DEV</option>
                        <option value="STAGING">STAGING</option>
                        <option value="PRODUCTION">PRODUCTION</option>
                    </select>
                </div>
                <div class="filter-group">
                    <label>Status</label>
                    <select id="filter-status">
                        <option value="">All Statuses</option>
                        <option value="success">✅ Passed</option>
                        <option value="advisory">⚠️ Advisory</option>
                        <option value="failed">❌ Failed</option>
                    </select>
                </div>
                <div class="filter-group">
                    <label>Search</label>
                    <input type="text" id="filter-search" placeholder="Search tests...">
                </div>
                <div class="filter-buttons">
                    <button class="filter-btn secondary" onclick="resetFilters()">Reset</button>
                    <button class="filter-btn primary" onclick="applyFilters()">Apply</button>
                </div>
            </div>

            <div class="result-count" id="result-count"></div>
            <div class="results-table-container" id="results-section"></div>
            <div class="pagination" id="pagination"></div>

            <div class="raw-section">
                <h3>📝 Raw Test Output</h3>
                <button class="raw-toggle" onclick="toggleRawOutput()">Show/Hide Raw Output</button>
                <pre id="raw-output">Loading test results...</pre>
            </div>
        </div>
    </div>

    <script>
        let allTests = [];
        let filteredTests = [];
        let currentSort = { column: 'order', direction: 'asc' };
        let currentPage = 1;
        const testsPerPage = 20;

        // Parse the comprehensive synth test output
        async function loadTestResults() {
            try {
                const possiblePaths = [
                    'comprehensive-synth.log',
                    '../comprehensive-synth.log',
                    '../../comprehensive-synth.log',
                    '../../../comprehensive-synth.log'
                ];

                let response = null;
                let foundPath = null;
                for (const path of possiblePaths) {
                    try {
                        const r = await fetch(path);
                        if (r.ok) {
                            response = r;
                            foundPath = path;
                            break;
                        }
                    } catch (e) {
                        continue;
                    }
                }

                if (!response || !response.ok) {
                    showNoResults();
                    return;
                }

                const text = await response.text();
                document.getElementById('raw-output').textContent = text;
                console.log('Loaded test results from:', foundPath);

                parseTestResults(text);
                displaySummary();
                applyFilters();

            } catch (error) {
                document.getElementById('raw-output').textContent =
                    'Error loading test results: ' + error.message;
            }
        }

        function parseTestResults(text) {
            const lines = text.split('\n');
            let currentTest = null;
            let testOrder = 0;

            for (const line of lines) {
                // Match test start: "Testing: EC2 + DEV + ec1"
                if (line.includes('Testing:')) {
                    const match = line.match(/Testing:\s+(\w+)\s+\+\s+(\w+)\s+\+\s+(\w+)/);
                    if (match) {
                        if (currentTest && currentTest.status !== 'running') {
                            allTests.push(currentTest);
                        }
                        testOrder++;
                        currentTest = {
                            order: testOrder,
                            runtime: match[1],
                            profile: match[2],
                            config: match[3],
                            status: 'running',
                            advisoryLayers: '',
                            advisories: [],
                            securityGroups: '-',
                            iamRoles: '-',
                            route53: false,
                            loadBalancer: false
                        };
                    }
                }

                if (!currentTest) continue;

                // Match success with advisories
                if (line.includes('Synthesis successful with advisories')) {
                    currentTest.status = 'advisory';
                    const layerMatch = line.match(/\[(L[\d,L]+)\]/);
                    if (layerMatch) {
                        currentTest.advisoryLayers = layerMatch[1];
                    }
                }
                // Match clean success
                else if (line.includes('✅ Synthesis successful')) {
                    currentTest.status = 'success';
                }
                // Match failure
                else if (line.includes('❌ Synthesis failed') || line.includes('❌ Synthesis completed with errors')) {
                    currentTest.status = 'failed';
                }

                // Match resource counts
                if (line.includes('Security Groups:')) {
                    const match = line.match(/Security Groups:\s+(\d+)/);
                    if (match) currentTest.securityGroups = match[1];
                }
                if (line.includes('IAM Roles:')) {
                    const match = line.match(/IAM Roles:\s+(\d+)/);
                    if (match) currentTest.iamRoles = match[1];
                }
                if (line.includes('✅ Route53 records found')) {
                    currentTest.route53 = true;
                }
                if (line.includes('✅ Load Balancer found')) {
                    currentTest.loadBalancer = true;
                }

                // Capture advisories
                if (line.includes('AwsSolutions-') || line.includes('NIST') || line.includes('HIPAA') || line.includes('PCI')) {
                    if (line.includes('Warning') || line.includes('Info')) {
                        currentTest.advisories.push(line.trim());
                    }
                }
            }

            // Push last test
            if (currentTest && currentTest.status !== 'running') {
                allTests.push(currentTest);
            }
        }

        function displaySummary() {
            const total = allTests.length;
            const successful = allTests.filter(t => t.status === 'success').length;
            const advisory = allTests.filter(t => t.status === 'advisory').length;
            const failed = allTests.filter(t => t.status === 'failed').length;
            const passRate = total > 0 ? Math.round(((successful + advisory) / total) * 100) : 0;

            document.getElementById('summary-section').innerHTML = `
                <div class="summary-grid">
                    <div class="summary-card">
                        <div class="summary-number">${total}</div>
                        <div class="summary-label">Total Tests</div>
                    </div>
                    <div class="summary-card success">
                        <div class="summary-number">${successful}</div>
                        <div class="summary-label">Passed</div>
                    </div>
                    <div class="summary-card advisory">
                        <div class="summary-number">${advisory}</div>
                        <div class="summary-label">With Advisories</div>
                    </div>
                    <div class="summary-card failed">
                        <div class="summary-number">${failed}</div>
                        <div class="summary-label">Failed</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-number">${passRate}%</div>
                        <div class="summary-label">Pass Rate</div>
                    </div>
                </div>
            `;
        }

        function applyFilters() {
            const runtime = document.getElementById('filter-runtime').value;
            const profile = document.getElementById('filter-profile').value;
            const status = document.getElementById('filter-status').value;
            const search = document.getElementById('filter-search').value.toLowerCase();

            filteredTests = allTests.filter(test => {
                if (runtime && test.runtime !== runtime) return false;
                if (profile && test.profile !== profile) return false;
                if (status && test.status !== status) return false;
                if (search) {
                    const searchStr = `${test.runtime} ${test.profile} ${test.config}`.toLowerCase();
                    if (!searchStr.includes(search)) return false;
                }
                return true;
            });

            sortTests();
            currentPage = 1;
            displayResults();
        }

        function resetFilters() {
            document.getElementById('filter-runtime').value = '';
            document.getElementById('filter-profile').value = '';
            document.getElementById('filter-status').value = '';
            document.getElementById('filter-search').value = '';
            applyFilters();
        }

        function sortTests() {
            const { column, direction } = currentSort;
            const modifier = direction === 'asc' ? 1 : -1;

            filteredTests.sort((a, b) => {
                let aVal = a[column];
                let bVal = b[column];

                // Handle numeric sorting
                if (column === 'order' || column === 'securityGroups' || column === 'iamRoles') {
                    aVal = parseInt(aVal) || 0;
                    bVal = parseInt(bVal) || 0;
                }

                // Handle status sorting (failed first, then advisory, then success)
                if (column === 'status') {
                    const statusOrder = { failed: 0, advisory: 1, success: 2 };
                    aVal = statusOrder[aVal] ?? 3;
                    bVal = statusOrder[bVal] ?? 3;
                }

                if (aVal < bVal) return -1 * modifier;
                if (aVal > bVal) return 1 * modifier;
                return 0;
            });
        }

        function handleSort(column) {
            if (currentSort.column === column) {
                currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
            } else {
                currentSort.column = column;
                currentSort.direction = 'asc';
            }
            sortTests();
            displayResults();
        }

        function displayResults() {
            const start = (currentPage - 1) * testsPerPage;
            const end = start + testsPerPage;
            const pageTests = filteredTests.slice(start, end);

            document.getElementById('result-count').textContent =
                `Showing ${start + 1}-${Math.min(end, filteredTests.length)} of ${filteredTests.length} tests`;

            if (filteredTests.length === 0) {
                document.getElementById('results-section').innerHTML = `
                    <div class="no-results">
                        <p>No tests match your filters.</p>
                    </div>
                `;
                document.getElementById('pagination').innerHTML = '';
                return;
            }

            const sortIcon = (col) => {
                if (currentSort.column !== col) return '<span class="sort-icon">↕</span>';
                return `<span class="sort-icon">${currentSort.direction === 'asc' ? '↑' : '↓'}</span>`;
            };
            const sortedClass = (col) => currentSort.column === col ? 'sorted' : '';

            let html = `
                <table class="results-table">
                    <thead>
                        <tr>
                            <th class="${sortedClass('order')}" onclick="handleSort('order')"># ${sortIcon('order')}</th>
                            <th class="${sortedClass('runtime')}" onclick="handleSort('runtime')">Runtime ${sortIcon('runtime')}</th>
                            <th class="${sortedClass('profile')}" onclick="handleSort('profile')">Profile ${sortIcon('profile')}</th>
                            <th class="${sortedClass('config')}" onclick="handleSort('config')">Config ${sortIcon('config')}</th>
                            <th class="${sortedClass('status')}" onclick="handleSort('status')">Status ${sortIcon('status')}</th>
                            <th>Advisories</th>
                            <th class="${sortedClass('securityGroups')}" onclick="handleSort('securityGroups')">SGs ${sortIcon('securityGroups')}</th>
                            <th class="${sortedClass('iamRoles')}" onclick="handleSort('iamRoles')">IAM ${sortIcon('iamRoles')}</th>
                            <th>Route53</th>
                            <th>ALB</th>
                        </tr>
                    </thead>
                    <tbody>
            `;

            pageTests.forEach(test => {
                const statusClass = test.status;
                const statusBadge = getStatusBadge(test.status);
                const runtimeBadge = `<span class="runtime-badge ${test.runtime.toLowerCase()}">${test.runtime}</span>`;
                const profileBadge = `<span class="profile-badge ${test.profile.toLowerCase()}">${test.profile}</span>`;

                html += `
                    <tr class="status-${statusClass}">
                        <td>${test.order}</td>
                        <td>${runtimeBadge}</td>
                        <td>${profileBadge}</td>
                        <td><code>${test.config}</code></td>
                        <td>${statusBadge}</td>
                        <td>${test.advisoryLayers ? `<span class="layer-badge warn">${test.advisoryLayers}</span>` : '-'}</td>
                        <td class="resource-count">${test.securityGroups}</td>
                        <td class="resource-count">${test.iamRoles}</td>
                        <td>${test.route53 ? '✅' : '❌'}</td>
                        <td>${test.loadBalancer ? '✅' : '❌'}</td>
                    </tr>
                `;
            });

            html += '</tbody></table>';
            document.getElementById('results-section').innerHTML = html;

            displayPagination();
        }

        function getStatusBadge(status) {
            switch (status) {
                case 'success':
                    return '<span class="status-badge success">✅ Passed</span>';
                case 'advisory':
                    return '<span class="status-badge advisory">⚠️ Advisory</span>';
                case 'failed':
                    return '<span class="status-badge failed">❌ Failed</span>';
                default:
                    return '<span class="status-badge">Unknown</span>';
            }
        }

        function displayPagination() {
            const totalPages = Math.ceil(filteredTests.length / testsPerPage);
            if (totalPages <= 1) {
                document.getElementById('pagination').innerHTML = '';
                return;
            }

            let html = '';
            html += `<button ${currentPage === 1 ? 'disabled' : ''} onclick="goToPage(${currentPage - 1})">← Prev</button>`;

            for (let i = 1; i <= totalPages; i++) {
                if (i === 1 || i === totalPages || (i >= currentPage - 2 && i <= currentPage + 2)) {
                    html += `<button class="${i === currentPage ? 'active' : ''}" onclick="goToPage(${i})">${i}</button>`;
                } else if (i === currentPage - 3 || i === currentPage + 3) {
                    html += '<button disabled>...</button>';
                }
            }

            html += `<button ${currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${currentPage + 1})">Next →</button>`;

            document.getElementById('pagination').innerHTML = html;
        }

        function goToPage(page) {
            currentPage = page;
            displayResults();
            document.querySelector('.results-table-container').scrollIntoView({ behavior: 'smooth' });
        }

        function toggleRawOutput() {
            const pre = document.getElementById('raw-output');
            pre.classList.toggle('visible');
        }

        function showNoResults() {
            document.getElementById('raw-output').textContent =
                'Test results not available. The comprehensive synthesis tests may not have run yet.\n\n' +
                'To generate results, run:\n' +
                '  cd cfc-testing\n' +
                '  bash scripts/comprehensive-synth-test.sh 2>&1 | tee comprehensive-synth.log';
            document.getElementById('summary-section').innerHTML = `
                <div class="info-box warning">
                    <h4>⚠️ No Test Results Found</h4>
                    <p>The comprehensive synthesis tests have not been run yet. Run the tests to populate this dashboard.</p>
                </div>
            `;
            document.getElementById('filter-controls').style.display = 'none';
        }

        // Event listeners
        document.getElementById('filter-search').addEventListener('keyup', function(e) {
            if (e.key === 'Enter') applyFilters();
        });

        // Load results on page load
        loadTestResults();
    </script>
</body>
</html>
EOF

echo "✅ Generated comprehensive synthesis test HTML report: $OUTPUT_DIR/comprehensive-synth-report.html"
