#!/usr/bin/env python3
"""
Deployment Metrics Dashboard Generator
Visualizes deployment dry-run metrics and trends
"""

import csv
import json
import os
from datetime import datetime
from collections import defaultdict
from typing import Dict, List

class DeploymentMetricsDashboard:
    def __init__(self, metrics_csv: str, output_dir: str):
        self.metrics_csv = metrics_csv
        self.output_dir = output_dir
        self.metrics = []

    def load_metrics(self):
        """Load metrics from CSV file"""
        with open(self.metrics_csv, 'r') as f:
            reader = csv.DictReader(f)
            self.metrics = list(reader)
        print(f"📊 Loaded {len(self.metrics)} deployment metrics")

    def analyze_metrics(self) -> Dict:
        """Analyze metrics and generate statistics"""
        total = len(self.metrics)
        successful = len([m for m in self.metrics if m['Status'] == 'SUCCESS'])
        failed = len([m for m in self.metrics if m['Status'] != 'SUCCESS'])

        # Group by runtime
        by_runtime = defaultdict(lambda: {'total': 0, 'success': 0, 'failed': 0})
        for m in self.metrics:
            runtime = m['Runtime']
            by_runtime[runtime]['total'] += 1
            if m['Status'] == 'SUCCESS':
                by_runtime[runtime]['success'] += 1
            else:
                by_runtime[runtime]['failed'] += 1

        # Group by security profile
        by_security = defaultdict(lambda: {'total': 0, 'success': 0, 'failed': 0})
        for m in self.metrics:
            security = m['SecurityProfile']
            by_security[security]['total'] += 1
            if m['Status'] == 'SUCCESS':
                by_security[security]['success'] += 1
            else:
                by_security[security]['failed'] += 1

        # Group by auth mode
        by_auth = defaultdict(lambda: {'total': 0, 'success': 0, 'failed': 0})
        for m in self.metrics:
            auth = m.get('AuthMode', 'none')
            by_auth[auth]['total'] += 1
            if m['Status'] == 'SUCCESS':
                by_auth[auth]['success'] += 1
            else:
                by_auth[auth]['failed'] += 1

        # Group by network mode
        by_network = defaultdict(lambda: {'total': 0, 'success': 0, 'failed': 0})
        for m in self.metrics:
            network = m.get('NetworkMode', 'public-no-nat')
            by_network[network]['total'] += 1
            if m['Status'] == 'SUCCESS':
                by_network[network]['success'] += 1
            else:
                by_network[network]['failed'] += 1

        # Synthesis time statistics (successful only)
        synth_times = [float(m['SynthTime']) for m in self.metrics if m['Status'] == 'SUCCESS' and m['SynthTime']]
        avg_synth = sum(synth_times) / len(synth_times) if synth_times else 0
        min_synth = min(synth_times) if synth_times else 0
        max_synth = max(synth_times) if synth_times else 0

        # Resource count statistics
        resource_counts = [int(m['ResourceCount']) for m in self.metrics if m['Status'] == 'SUCCESS' and m['ResourceCount']]
        avg_resources = sum(resource_counts) / len(resource_counts) if resource_counts else 0
        min_resources = min(resource_counts) if resource_counts else 0
        max_resources = max(resource_counts) if resource_counts else 0

        return {
            'summary': {
                'total': total,
                'successful': successful,
                'failed': failed,
                'success_rate': f"{(successful/total*100):.1f}%" if total > 0 else "0%"
            },
            'by_runtime': dict(by_runtime),
            'by_security': dict(by_security),
            'by_auth': dict(by_auth),
            'by_network': dict(by_network),
            'synthesis_time': {
                'avg': f"{avg_synth:.3f}s",
                'min': f"{min_synth:.3f}s",
                'max': f"{max_synth:.3f}s"
            },
            'resource_count': {
                'avg': f"{avg_resources:.0f}",
                'min': min_resources,
                'max': max_resources
            }
        }

    def generate_html_dashboard(self, analysis: Dict):
        """Generate HTML dashboard"""
        html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Deployment Metrics Dashboard</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            margin: 0;
            padding: 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
        }}
        .container {{
            max-width: 1400px;
            margin: 0 auto;
        }}
        h1 {{
            color: white;
            text-align: center;
            font-size: 2.5em;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }}
        .subtitle {{
            color: rgba(255,255,255,0.9);
            text-align: center;
            margin-bottom: 30px;
            font-size: 1.1em;
        }}
        .grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }}
        .card {{
            background: white;
            border-radius: 12px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            transition: transform 0.2s, box-shadow 0.2s;
        }}
        .card:hover {{
            transform: translateY(-5px);
            box-shadow: 0 8px 12px rgba(0,0,0,0.15);
        }}
        .card h2 {{
            margin: 0 0 20px 0;
            color: #333;
            font-size: 1.3em;
            border-bottom: 3px solid #667eea;
            padding-bottom: 10px;
        }}
        .metric {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 0;
            border-bottom: 1px solid #eee;
        }}
        .metric:last-child {{
            border-bottom: none;
        }}
        .metric-label {{
            font-weight: 600;
            color: #555;
        }}
        .metric-value {{
            font-size: 1.2em;
            font-weight: bold;
            color: #667eea;
        }}
        .stat-box {{
            text-align: center;
            padding: 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            border-radius: 8px;
            color: white;
            margin-bottom: 10px;
        }}
        .stat-box h3 {{
            margin: 0 0 10px 0;
            font-size: 0.9em;
            opacity: 0.9;
        }}
        .stat-box .value {{
            font-size: 2.5em;
            font-weight: bold;
            margin: 0;
        }}
        .success {{
            color: #10b981;
        }}
        .failed {{
            color: #ef4444;
        }}
        .bar {{
            height: 30px;
            background: linear-gradient(90deg, #10b981 0%, #10b981 var(--success), #ef4444 var(--success), #ef4444 100%);
            border-radius: 15px;
            margin: 10px 0;
            position: relative;
            overflow: hidden;
        }}
        .bar-label {{
            position: absolute;
            width: 100%;
            text-align: center;
            line-height: 30px;
            color: white;
            font-weight: bold;
            font-size: 0.9em;
            text-shadow: 1px 1px 2px rgba(0,0,0,0.3);
        }}
        .timestamp {{
            text-align: center;
            color: rgba(255,255,255,0.8);
            margin-top: 20px;
            font-size: 0.9em;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Deployment Metrics Dashboard</h1>
        <div class="subtitle">CloudForge Core Deployment Testing Analytics</div>

        <div class="grid">
            <div class="card">
                <h2>📊 Overall Summary</h2>
                <div class="stat-box">
                    <h3>Total Deployments</h3>
                    <p class="value">{analysis['summary']['total']}</p>
                </div>
                <div class="metric">
                    <span class="metric-label success">✅ Successful</span>
                    <span class="metric-value success">{analysis['summary']['successful']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label failed">❌ Failed</span>
                    <span class="metric-value failed">{analysis['summary']['failed']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Success Rate</span>
                    <span class="metric-value">{analysis['summary']['success_rate']}</span>
                </div>
                <div class="bar" style="--success: {analysis['summary']['success_rate']}">
                    <div class="bar-label">{analysis['summary']['success_rate']} Success</div>
                </div>
            </div>

            <div class="card">
                <h2>⚡ Performance Metrics</h2>
                <div class="metric">
                    <span class="metric-label">Avg Synthesis Time</span>
                    <span class="metric-value">{analysis['synthesis_time']['avg']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Min Synthesis Time</span>
                    <span class="metric-value">{analysis['synthesis_time']['min']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Max Synthesis Time</span>
                    <span class="metric-value">{analysis['synthesis_time']['max']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Avg Resource Count</span>
                    <span class="metric-value">{analysis['resource_count']['avg']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Resource Range</span>
                    <span class="metric-value">{analysis['resource_count']['min']} - {analysis['resource_count']['max']}</span>
                </div>
            </div>

            <div class="card">
                <h2>💻 Runtime Distribution</h2>
"""
        for runtime, stats in analysis['by_runtime'].items():
            success_pct = (stats['success']/stats['total']*100) if stats['total'] > 0 else 0
            html += f"""
                <div class="metric">
                    <span class="metric-label">{runtime}</span>
                    <span class="metric-value">{stats['success']}/{stats['total']}</span>
                </div>
                <div class="bar" style="--success: {success_pct}%">
                    <div class="bar-label">{success_pct:.0f}% Success</div>
                </div>
"""

        html += """
            </div>

            <div class="card">
                <h2>🔒 Security Profile</h2>
"""
        for security, stats in analysis['by_security'].items():
            success_pct = (stats['success']/stats['total']*100) if stats['total'] > 0 else 0
            html += f"""
                <div class="metric">
                    <span class="metric-label">{security}</span>
                    <span class="metric-value">{stats['success']}/{stats['total']}</span>
                </div>
                <div class="bar" style="--success: {success_pct}%">
                    <div class="bar-label">{success_pct:.0f}% Success</div>
                </div>
"""

        html += """
            </div>

            <div class="card">
                <h2>🔐 Authentication Mode</h2>
"""
        for auth, stats in analysis['by_auth'].items():
            success_pct = (stats['success']/stats['total']*100) if stats['total'] > 0 else 0
            html += f"""
                <div class="metric">
                    <span class="metric-label">{auth}</span>
                    <span class="metric-value">{stats['success']}/{stats['total']}</span>
                </div>
                <div class="bar" style="--success: {success_pct}%">
                    <div class="bar-label">{success_pct:.0f}% Success</div>
                </div>
"""

        html += """
            </div>

            <div class="card">
                <h2>🌐 Network Mode</h2>
"""
        for network, stats in analysis['by_network'].items():
            success_pct = (stats['success']/stats['total']*100) if stats['total'] > 0 else 0
            html += f"""
                <div class="metric">
                    <span class="metric-label">{network}</span>
                    <span class="metric-value">{stats['success']}/{stats['total']}</span>
                </div>
                <div class="bar" style="--success: {success_pct}%">
                    <div class="bar-label">{success_pct:.0f}% Success</div>
                </div>
"""

        html += f"""
            </div>
        </div>

        <div class="timestamp">
            Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
        </div>
    </div>
</body>
</html>
"""
        return html

    def generate_dashboard(self):
        """Generate complete dashboard"""
        self.load_metrics()
        analysis = self.analyze_metrics()

        # Save JSON analysis
        os.makedirs(self.output_dir, exist_ok=True)
        json_file = os.path.join(self.output_dir, 'deployment-analysis.json')
        with open(json_file, 'w') as f:
            json.dump(analysis, f, indent=2)

        # Generate HTML dashboard
        html = self.generate_html_dashboard(analysis)
        html_file = os.path.join(self.output_dir, 'deployment-dashboard.html')
        with open(html_file, 'w') as f:
            f.write(html)

        print(f"\n✅ Dashboard generated:")
        print(f"  📄 JSON: {json_file}")
        print(f"  🌐 HTML: {html_file}")
        print(f"\n📊 Summary:")
        print(f"  Total Deployments: {analysis['summary']['total']}")
        print(f"  Success Rate: {analysis['summary']['success_rate']}")
        print(f"  Avg Synthesis Time: {analysis['synthesis_time']['avg']}")
        print(f"  Avg Resources: {analysis['resource_count']['avg']}")

if __name__ == "__main__":
    import sys

    # Default paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    base_dir = os.path.dirname(script_dir)
    metrics_csv = os.path.join(base_dir, "test-results/deployment-reports/historical/deployment-metrics.csv")
    output_dir = os.path.join(base_dir, "test-results/deployment-reports/dashboard")

    # Allow custom paths from command line
    if len(sys.argv) > 1:
        metrics_csv = sys.argv[1]
    if len(sys.argv) > 2:
        output_dir = sys.argv[2]

    if not os.path.exists(metrics_csv):
        print(f"❌ Metrics file not found: {metrics_csv}")
        sys.exit(1)

    print("🚀 Generating Deployment Metrics Dashboard...")
    dashboard = DeploymentMetricsDashboard(metrics_csv, output_dir)
    dashboard.generate_dashboard()
