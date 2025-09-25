#!/usr/bin/env python3
"""
Truth Table Generator for CloudForge Core Resource Validation
Creates comprehensive test matrices and identifies which files need attention
"""

import json
import os
import sys
from datetime import datetime
from typing import Dict, List, Set, Tuple
from dataclasses import dataclass
from enum import Enum

class Runtime(Enum):
    EC2 = "EC2"
    FARGATE = "FARGATE"

class Topology(Enum):
    JENKINS_SINGLE_NODE = "JENKINS_SINGLE_NODE"
    JENKINS_SERVICE = "JENKINS_SERVICE"

class SecurityProfile(Enum):
    DEV = "DEV"
    STAGING = "STAGING"
    PRODUCTION = "PRODUCTION"

class DomainConfig(Enum):
    WITH_DOMAIN = "with-domain"
    NO_DOMAIN = "no-domain"

class SSLConfig(Enum):
    SSL_ENABLED = "ssl-enabled"
    SSL_DISABLED = "ssl-disabled"

class SubdomainConfig(Enum):
    WITH_SUBDOMAIN = "with-subdomain"
    NO_SUBDOMAIN = "no-subdomain"

@dataclass
class TestConfiguration:
    runtime: Runtime
    topology: Topology
    security_profile: SecurityProfile
    domain_config: DomainConfig
    ssl_config: SSLConfig
    subdomain_config: SubdomainConfig
    
    def __str__(self):
        return f"{self.runtime.value}_{self.topology.value}_{self.security_profile.value}_{self.domain_config.value}_{self.ssl_config.value}_{self.subdomain_config.value}"
    
    def is_valid(self) -> bool:
        """Check if this configuration combination is valid"""
        # SSL requires domain
        if self.ssl_config == SSLConfig.SSL_ENABLED and self.domain_config == DomainConfig.NO_DOMAIN:
            return False
        
        # Subdomain requires domain
        if self.subdomain_config == SubdomainConfig.WITH_SUBDOMAIN and self.domain_config == DomainConfig.NO_DOMAIN:
            return False
        
        return True

class ResourceType(Enum):
    # Infrastructure
    VPC = "AWS::EC2::VPC"
    SUBNETS = "AWS::EC2::Subnet"
    SECURITY_GROUPS = "AWS::EC2::SecurityGroup"
    INTERNET_GATEWAY = "AWS::EC2::InternetGateway"
    
    # Load Balancing
    APPLICATION_LOAD_BALANCER = "AWS::ElasticLoadBalancingV2::LoadBalancer"
    TARGET_GROUPS = "AWS::ElasticLoadBalancingV2::TargetGroup"
    HTTP_LISTENER = "AWS::ElasticLoadBalancingV2::Listener"
    HTTPS_LISTENER = "AWS::ElasticLoadBalancingV2::Listener"
    
    # Compute - EC2
    EC2_INSTANCES = "AWS::EC2::Instance"
    AUTO_SCALING_GROUP = "AWS::AutoScaling::AutoScalingGroup"
    LAUNCH_TEMPLATE = "AWS::EC2::LaunchTemplate"
    
    # Compute - Fargate
    ECS_CLUSTER = "AWS::ECS::Cluster"
    ECS_SERVICE = "AWS::ECS::Service"
    FARGATE_TASK_DEFINITION = "AWS::ECS::TaskDefinition"
    
    # Storage
    EFS_FILE_SYSTEM = "AWS::EFS::FileSystem"
    EFS_ACCESS_POINT = "AWS::EFS::AccessPoint"
    EFS_MOUNT_TARGET = "AWS::EFS::MountTarget"
    
    # DNS & SSL
    ROUTE53_HOSTED_ZONE = "AWS::Route53::HostedZone"
    ROUTE53_RECORDS = "AWS::Route53::RecordSet"
    ACM_CERTIFICATE = "AWS::CertificateManager::Certificate"
    
    # IAM
    IAM_ROLES = "AWS::IAM::Role"
    IAM_POLICIES = "AWS::IAM::Policy"
    
    # Monitoring & Logging
    CLOUDWATCH_LOGS = "AWS::Logs::LogGroup"
    CLOUDWATCH_ALARMS = "AWS::CloudWatch::Alarm"
    
    # Security
    WAF_WEB_ACL = "AWS::WAFv2::WebACL"
    CLOUDTRAIL = "AWS::CloudTrail::Trail"
    CONFIG_RULES = "AWS::Config::ConfigRule"

class TruthTableGenerator:
    def __init__(self, output_dir: str):
        self.output_dir = output_dir
        self.truth_table: Dict[str, Set[ResourceType]] = {}
        self.file_mappings: Dict[ResourceType, List[str]] = {}
        self.initialize_file_mappings()
    
    def initialize_file_mappings(self):
        """Map resource types to the factory files that create them"""
        self.file_mappings = {
            # VpcFactory
            ResourceType.VPC: ["VpcFactory.java"],
            ResourceType.SUBNETS: ["VpcFactory.java"],
            ResourceType.INTERNET_GATEWAY: ["VpcFactory.java"],
            
            # AlbFactory
            ResourceType.APPLICATION_LOAD_BALANCER: ["AlbFactory.java"],
            ResourceType.SECURITY_GROUPS: ["AlbFactory.java", "SystemContext.java"],
            
            # FargateRuntimeConfiguration
            ResourceType.HTTP_LISTENER: ["AlbFactory.java", "FargateRuntimeConfiguration.java"],
            ResourceType.HTTPS_LISTENER: ["FargateRuntimeConfiguration.java"],
            ResourceType.TARGET_GROUPS: ["FargateRuntimeConfiguration.java", "SystemContext.java"],
            
            # FargateFactory
            ResourceType.ECS_CLUSTER: ["FargateFactory.java"],
            ResourceType.ECS_SERVICE: ["FargateFactory.java"],
            ResourceType.FARGATE_TASK_DEFINITION: ["FargateFactory.java"],
            
            # Ec2Factory
            ResourceType.EC2_INSTANCES: ["Ec2Factory.java", "JenkinsFactory.java"],
            ResourceType.AUTO_SCALING_GROUP: ["Ec2Factory.java"],
            ResourceType.LAUNCH_TEMPLATE: ["Ec2Factory.java"],
            
            # EfsFactory
            ResourceType.EFS_FILE_SYSTEM: ["EfsFactory.java"],
            ResourceType.EFS_ACCESS_POINT: ["EfsFactory.java", "FargateFactory.java"],
            ResourceType.EFS_MOUNT_TARGET: ["EfsFactory.java"],
            
            # DomainFactory
            ResourceType.ROUTE53_HOSTED_ZONE: ["DomainFactory.java"],
            ResourceType.ROUTE53_RECORDS: ["JenkinsServiceTopologyConfiguration.java", "JenkinsSingleNodeTopologyConfiguration.java"],
            ResourceType.ACM_CERTIFICATE: ["FargateRuntimeConfiguration.java", "Ec2RuntimeConfiguration.java"],
            
            # IAM Factories
            ResourceType.IAM_ROLES: ["IamStandardConfiguration.java", "FargateFactory.java", "Ec2Factory.java"],
            ResourceType.IAM_POLICIES: ["IamStandardConfiguration.java"],
            
            # LoggingCwFactory
            ResourceType.CLOUDWATCH_LOGS: ["LoggingCwFactory.java"],
            
            # AlarmFactory
            ResourceType.CLOUDWATCH_ALARMS: ["AlarmFactory.java"],
            
            # Security Configurations
            ResourceType.WAF_WEB_ACL: ["ProductionSecurityConfiguration.java"],
            ResourceType.CLOUDTRAIL: ["StagingSecurityConfiguration.java", "ProductionSecurityConfiguration.java"],
            ResourceType.CONFIG_RULES: ["StagingSecurityConfiguration.java", "ProductionSecurityConfiguration.java"],
        }
    
    def generate_expected_resources(self, config: TestConfiguration) -> Set[ResourceType]:
        """Generate expected resources for a given configuration"""
        if not config.is_valid():
            return set()
        
        resources = set()
        
        # Base infrastructure (always present)
        resources.update([
            ResourceType.VPC,
            ResourceType.SUBNETS,
            ResourceType.SECURITY_GROUPS,
            ResourceType.INTERNET_GATEWAY,
            ResourceType.IAM_ROLES,
            ResourceType.IAM_POLICIES,
            ResourceType.CLOUDWATCH_LOGS,
        ])
        
        # Runtime-specific resources
        if config.runtime == Runtime.FARGATE:
            resources.update([
                ResourceType.ECS_CLUSTER,
                ResourceType.ECS_SERVICE,
                ResourceType.FARGATE_TASK_DEFINITION,
            ])
        else:  # EC2
            resources.add(ResourceType.EC2_INSTANCES)
            if config.topology == Topology.JENKINS_SERVICE:
                resources.add(ResourceType.AUTO_SCALING_GROUP)
        
        # Topology-specific resources
        if config.topology == Topology.JENKINS_SERVICE:
            resources.update([
                ResourceType.APPLICATION_LOAD_BALANCER,
                ResourceType.TARGET_GROUPS,
            ])
        
        # EFS for Jenkins (both runtimes)
        resources.update([
            ResourceType.EFS_FILE_SYSTEM,
            ResourceType.EFS_ACCESS_POINT,
            ResourceType.EFS_MOUNT_TARGET,
        ])
        
        # Domain-specific resources
        if config.domain_config == DomainConfig.WITH_DOMAIN:
            resources.update([
                ResourceType.ROUTE53_HOSTED_ZONE,
                ResourceType.ROUTE53_RECORDS,
            ])
            
            if config.topology == Topology.JENKINS_SERVICE:
                if config.ssl_config == SSLConfig.SSL_ENABLED:
                    resources.update([
                        ResourceType.ACM_CERTIFICATE,
                        ResourceType.HTTPS_LISTENER,
                        ResourceType.HTTP_LISTENER,  # For redirect
                    ])
                else:
                    resources.add(ResourceType.HTTP_LISTENER)
        elif config.topology == Topology.JENKINS_SERVICE:
            # No domain but still need HTTP listener for ALB
            resources.add(ResourceType.HTTP_LISTENER)
        
        # Security profile-specific resources
        if config.security_profile == SecurityProfile.STAGING:
            resources.update([
                ResourceType.CLOUDTRAIL,
                ResourceType.CONFIG_RULES,
            ])
        elif config.security_profile == SecurityProfile.PRODUCTION:
            resources.update([
                ResourceType.WAF_WEB_ACL,
                ResourceType.CLOUDTRAIL,
                ResourceType.CONFIG_RULES,
                ResourceType.CLOUDWATCH_ALARMS,
            ])
        
        return resources
    
    def generate_truth_table(self) -> Dict[str, Dict]:
        """Generate complete truth table for all valid configurations"""
        truth_table = {}
        
        for runtime in Runtime:
            for topology in Topology:
                for security_profile in SecurityProfile:
                    for domain_config in DomainConfig:
                        for ssl_config in SSLConfig:
                            for subdomain_config in SubdomainConfig:
                                config = TestConfiguration(
                                    runtime, topology, security_profile,
                                    domain_config, ssl_config, subdomain_config
                                )
                                
                                key = str(config)
                                
                                if config.is_valid():
                                    expected_resources = self.generate_expected_resources(config)
                                    truth_table[key] = {
                                        "configuration": {
                                            "runtime": runtime.value,
                                            "topology": topology.value,
                                            "security_profile": security_profile.value,
                                            "domain_config": domain_config.value,
                                            "ssl_config": ssl_config.value,
                                            "subdomain_config": subdomain_config.value,
                                        },
                                        "expected_resources": [r.value for r in expected_resources],
                                        "resource_count": len(expected_resources),
                                        "files_involved": self.get_files_for_resources(expected_resources),
                                        "valid": True
                                    }
                                else:
                                    truth_table[key] = {
                                        "configuration": {
                                            "runtime": runtime.value,
                                            "topology": topology.value,
                                            "security_profile": security_profile.value,
                                            "domain_config": domain_config.value,
                                            "ssl_config": ssl_config.value,
                                            "subdomain_config": subdomain_config.value,
                                        },
                                        "expected_resources": [],
                                        "resource_count": 0,
                                        "files_involved": [],
                                        "valid": False,
                                        "reason": "Invalid combination"
                                    }
        
        return truth_table
    
    def get_files_for_resources(self, resources: Set[ResourceType]) -> List[str]:
        """Get list of files involved in creating the given resources"""
        files = set()
        for resource in resources:
            if resource in self.file_mappings:
                files.update(self.file_mappings[resource])
        return sorted(list(files))
    
    def generate_test_matrix(self) -> Dict[str, List[str]]:
        """Generate test matrix showing which files to test for each change"""
        test_matrix = {}
        
        for file_name in set().union(*self.file_mappings.values()):
            affected_configs = []
            
            # Find all configurations that use this file
            for config_key, config_data in self.truth_table.items():
                if file_name in config_data.get("files_involved", []):
                    affected_configs.append(config_key)
            
            test_matrix[file_name] = affected_configs
        
        return test_matrix
    
    def save_truth_table(self, filename: str):
        """Save truth table to JSON file"""
        os.makedirs(self.output_dir, exist_ok=True)
        
        truth_table_data = {
            "metadata": {
                "generated_at": datetime.now().isoformat(),
                "total_configurations": len(self.truth_table),
                "valid_configurations": len([c for c in self.truth_table.values() if c["valid"]]),
                "invalid_configurations": len([c for c in self.truth_table.values() if not c["valid"]]),
            },
            "configurations": self.truth_table,
            "test_matrix": self.generate_test_matrix(),
            "file_mappings": {k.value: v for k, v in self.file_mappings.items()}
        }
        
        filepath = os.path.join(self.output_dir, filename)
        with open(filepath, 'w') as f:
            json.dump(truth_table_data, f, indent=2)
        
        print(f"✅ Truth table saved to: {filepath}")
        return filepath
    
    def generate_test_strategies(self) -> Dict[str, Dict]:
        """Generate testing strategies for different scenarios"""
        strategies = {
            "smoke_test": {
                "description": "Minimal test set covering basic functionality",
                "configurations": [
                    "FARGATE_JENKINS_SERVICE_DEV_with-domain_ssl-enabled_with-subdomain",
                    "EC2_JENKINS_SINGLE_NODE_DEV_no-domain_ssl-disabled_no-subdomain",
                ]
            },
            "ssl_regression": {
                "description": "Test SSL certificate and HTTPS listener functionality",
                "configurations": [
                    config_key for config_key, config_data in self.truth_table.items()
                    if config_data["valid"] and "ssl-enabled" in config_key
                ]
            },
            "security_profile_regression": {
                "description": "Test security profile hardening",
                "configurations": [
                    config_key for config_key, config_data in self.truth_table.items()
                    if config_data["valid"] and any(profile in config_key for profile in ["STAGING", "PRODUCTION"])
                ]
            },
            "fargate_regression": {
                "description": "Test Fargate-specific functionality",
                "configurations": [
                    config_key for config_key, config_data in self.truth_table.items()
                    if config_data["valid"] and "FARGATE" in config_key
                ]
            },
            "ec2_regression": {
                "description": "Test EC2-specific functionality",
                "configurations": [
                    config_key for config_key, config_data in self.truth_table.items()
                    if config_data["valid"] and "EC2" in config_key
                ]
            },
            "domain_regression": {
                "description": "Test domain and DNS functionality",
                "configurations": [
                    config_key for config_key, config_data in self.truth_table.items()
                    if config_data["valid"] and "with-domain" in config_key
                ]
            },
            "full_matrix": {
                "description": "Complete test of all valid configurations",
                "configurations": [
                    config_key for config_key, config_data in self.truth_table.items()
                    if config_data["valid"]
                ]
            }
        }
        
        return strategies
    
    def generate_html_report(self, filename: str):
        """Generate HTML report with interactive truth table"""
        html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <title>CloudForge Core - Truth Table & Test Matrix</title>
    <style>
        body {{ font-family: 'Segoe UI', sans-serif; margin: 20px; background: #f8f9fa; }}
        .container {{ max-width: 1400px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }}
        .header {{ text-align: center; margin-bottom: 30px; }}
        .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }}
        .stat-card {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; text-align: center; }}
        .stat-number {{ font-size: 2em; font-weight: bold; }}
        .stat-label {{ font-size: 0.9em; opacity: 0.9; }}
        .section {{ margin: 30px 0; }}
        .section h2 {{ color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }}
        .truth-table {{ width: 100%; border-collapse: collapse; font-size: 11px; }}
        .truth-table th {{ background: #34495e; color: white; padding: 8px 4px; position: sticky; top: 0; }}
        .truth-table td {{ border: 1px solid #ddd; padding: 4px; text-align: center; }}
        .valid {{ background: #d4edda; }}
        .invalid {{ background: #f8d7da; }}
        .resource-count {{ font-weight: bold; color: #2980b9; }}
        .filter-controls {{ margin: 20px 0; padding: 15px; background: #ecf0f1; border-radius: 5px; }}
        .filter-controls input, .filter-controls select {{ margin: 5px; padding: 5px; }}
        .strategy-card {{ background: #fff; border: 1px solid #ddd; border-radius: 5px; padding: 15px; margin: 10px 0; }}
        .strategy-title {{ font-weight: bold; color: #2c3e50; }}
        .config-list {{ max-height: 200px; overflow-y: auto; background: #f8f9fa; padding: 10px; border-radius: 3px; font-family: monospace; font-size: 12px; }}
        .file-matrix {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 15px; }}
        .file-card {{ background: #fff; border: 1px solid #ddd; border-radius: 5px; padding: 15px; }}
        .file-name {{ font-weight: bold; color: #e74c3c; font-family: monospace; }}
        .affected-count {{ color: #7f8c8d; font-size: 0.9em; }}
    </style>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔍 CloudForge Core - Truth Table & Test Matrix</h1>
            <p>Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        </div>
        
        <div class="stats">
            <div class="stat-card">
                <div class="stat-number">{len(self.truth_table)}</div>
                <div class="stat-label">Total Configurations</div>
            </div>
            <div class="stat-card">
                <div class="stat-number">{len([c for c in self.truth_table.values() if c['valid']])}</div>
                <div class="stat-label">Valid Configurations</div>
            </div>
            <div class="stat-card">
                <div class="stat-number">{len([c for c in self.truth_table.values() if not c['valid']])}</div>
                <div class="stat-label">Invalid Combinations</div>
            </div>
            <div class="stat-card">
                <div class="stat-number">{len(set().union(*self.file_mappings.values()))}</div>
                <div class="stat-label">Factory Files</div>
            </div>
        </div>
        
        <div class="section">
            <h2>📊 Configuration Distribution</h2>
            <canvas id="configChart" width="400" height="200"></canvas>
        </div>
        
        <div class="section">
            <h2>🎯 Testing Strategies</h2>
            <div id="strategies">
                <!-- Strategies will be populated by JavaScript -->
            </div>
        </div>
        
        <div class="section">
            <h2>📋 Truth Table</h2>
            <div class="filter-controls">
                <input type="text" id="filterInput" placeholder="Filter configurations..." onkeyup="filterTable()">
                <select id="runtimeFilter" onchange="filterTable()">
                    <option value="">All Runtimes</option>
                    <option value="EC2">EC2</option>
                    <option value="FARGATE">Fargate</option>
                </select>
                <select id="validFilter" onchange="filterTable()">
                    <option value="">All Configurations</option>
                    <option value="true">Valid Only</option>
                    <option value="false">Invalid Only</option>
                </select>
            </div>
            <div style="max-height: 600px; overflow-y: auto;">
                <table class="truth-table" id="truthTable">
                    <thead>
                        <tr>
                            <th>Runtime</th>
                            <th>Topology</th>
                            <th>Security</th>
                            <th>Domain</th>
                            <th>SSL</th>
                            <th>Subdomain</th>
                            <th>Resources</th>
                            <th>Files</th>
                            <th>Valid</th>
                        </tr>
                    </thead>
                    <tbody>
                        <!-- Table rows will be populated by JavaScript -->
                    </tbody>
                </table>
            </div>
        </div>
        
        <div class="section">
            <h2>🔧 File Impact Matrix</h2>
            <p>Shows which configurations are affected when each factory file changes:</p>
            <div class="file-matrix" id="fileMatrix">
                <!-- File matrix will be populated by JavaScript -->
            </div>
        </div>
    </div>
    
    <script>
        // Truth table data
        const truthTableData = {json.dumps(self.truth_table, indent=8)};
        const testStrategies = {json.dumps(self.generate_test_strategies(), indent=8)};
        const testMatrix = {json.dumps(self.generate_test_matrix(), indent=8)};
        
        // Populate truth table
        function populateTruthTable() {{
            const tbody = document.querySelector('#truthTable tbody');
            tbody.innerHTML = '';
            
            Object.entries(truthTableData).forEach(([key, config]) => {{
                const row = document.createElement('tr');
                row.className = config.valid ? 'valid' : 'invalid';
                row.innerHTML = `
                    <td>${{config.configuration.runtime}}</td>
                    <td>${{config.configuration.topology}}</td>
                    <td>${{config.configuration.security_profile}}</td>
                    <td>${{config.configuration.domain_config}}</td>
                    <td>${{config.configuration.ssl_config}}</td>
                    <td>${{config.configuration.subdomain_config}}</td>
                    <td class="resource-count">${{config.resource_count}}</td>
                    <td>${{config.files_involved.length}}</td>
                    <td>${{config.valid ? '✅' : '❌'}}</td>
                `;
                tbody.appendChild(row);
            }});
        }}
        
        // Populate testing strategies
        function populateStrategies() {{
            const container = document.getElementById('strategies');
            
            Object.entries(testStrategies).forEach(([name, strategy]) => {{
                const card = document.createElement('div');
                card.className = 'strategy-card';
                card.innerHTML = `
                    <div class="strategy-title">${{name.replace('_', ' ').toUpperCase()}}</div>
                    <p>${{strategy.description}}</p>
                    <div><strong>Configurations:</strong> ${{strategy.configurations.length}}</div>
                    <div class="config-list">${{strategy.configurations.join('\\n')}}</div>
                `;
                container.appendChild(card);
            }});
        }}
        
        // Populate file matrix
        function populateFileMatrix() {{
            const container = document.getElementById('fileMatrix');
            
            Object.entries(testMatrix).forEach(([fileName, configs]) => {{
                const card = document.createElement('div');
                card.className = 'file-card';
                card.innerHTML = `
                    <div class="file-name">${{fileName}}</div>
                    <div class="affected-count">Affects ${{configs.length}} configurations</div>
                    <div class="config-list">${{configs.join('\\n')}}</div>
                `;
                container.appendChild(card);
            }});
        }}
        
        // Filter table
        function filterTable() {{
            const filterInput = document.getElementById('filterInput').value.toLowerCase();
            const runtimeFilter = document.getElementById('runtimeFilter').value;
            const validFilter = document.getElementById('validFilter').value;
            const rows = document.querySelectorAll('#truthTable tbody tr');
            
            rows.forEach(row => {{
                const text = row.textContent.toLowerCase();
                const runtime = row.cells[0].textContent;
                const valid = row.classList.contains('valid');
                
                let show = true;
                
                if (filterInput && !text.includes(filterInput)) show = false;
                if (runtimeFilter && runtime !== runtimeFilter) show = false;
                if (validFilter && String(valid) !== validFilter) show = false;
                
                row.style.display = show ? '' : 'none';
            }});
        }}
        
        // Initialize page
        document.addEventListener('DOMContentLoaded', function() {{
            populateTruthTable();
            populateStrategies();
            populateFileMatrix();
            
            // Create configuration distribution chart
            const ctx = document.getElementById('configChart').getContext('2d');
            const validCount = Object.values(truthTableData).filter(c => c.valid).length;
            const invalidCount = Object.values(truthTableData).filter(c => !c.valid).length;
            
            new Chart(ctx, {{
                type: 'doughnut',
                data: {{
                    labels: ['Valid Configurations', 'Invalid Combinations'],
                    datasets: [{{
                        data: [validCount, invalidCount],
                        backgroundColor: ['#2ecc71', '#e74c3c']
                    }}]
                }},
                options: {{
                    responsive: true,
                    plugins: {{
                        legend: {{
                            position: 'bottom'
                        }}
                    }}
                }}
            }});
        }});
    </script>
</body>
</html>
        """
        
        filepath = os.path.join(self.output_dir, filename)
        with open(filepath, 'w') as f:
            f.write(html_content)
        
        print(f"✅ HTML report saved to: {filepath}")
        return filepath
    
    def run(self):
        """Generate all outputs"""
        print("🚀 Generating truth table and test matrix...")
        
        # Generate truth table
        self.truth_table = self.generate_truth_table()
        
        # Save outputs
        json_file = self.save_truth_table("truth-table.json")
        html_file = self.generate_html_report("truth-table-report.html")
        
        # Print summary
        valid_count = len([c for c in self.truth_table.values() if c["valid"]])
        invalid_count = len([c for c in self.truth_table.values() if not c["valid"]])
        
        print(f"\n📊 Summary:")
        print(f"Total configurations: {len(self.truth_table)}")
        print(f"Valid configurations: {valid_count}")
        print(f"Invalid combinations: {invalid_count}")
        print(f"Factory files mapped: {len(set().union(*self.file_mappings.values()))}")
        
        print(f"\n📋 Files generated:")
        print(f"  - JSON: {json_file}")
        print(f"  - HTML: {html_file}")
        
        return json_file, html_file

def main():
    if len(sys.argv) > 1:
        output_dir = sys.argv[1]
    else:
        output_dir = "/Users/phillip/projects/cfc-core/cfc-testing/validation-results"
    
    generator = TruthTableGenerator(output_dir)
    generator.run()

if __name__ == "__main__":
    main()
