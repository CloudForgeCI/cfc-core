/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */

// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    {
      type: 'doc',
      id: 'README',
      label: 'Documentation Home',
    },
    {
      type: 'category',
      label: 'Quick Start',
      items: [
        'ONBOARDING_QUICK_START',
        'compliance/QUICK_START_GUIDE',
        'guides/INTERACTIVE_DEPLOYER',
      ],
    },
    {
      type: 'category',
      label: 'Applications & Plugins',
      collapsed: false,
      items: [
        'applications/README',
        'applications/COMPLIANCE',
        'applications/OIDC',
        {
          type: 'category',
          label: 'Application Guides',
          items: [
            'guides/applications/README',
            'guides/applications/jenkins',
            'guides/applications/mattermost',
            'guides/applications/metabase',
            'guides/applications/gitlab',
            'guides/applications/grafana',
            'guides/applications/harbor',
            'guides/applications/nexus',
            'guides/applications/sonarqube',
            'guides/applications/drone',
            'guides/applications/gitea',
            'guides/applications/postgresql',
            'guides/applications/prometheus',
            'guides/applications/redis',
            'guides/applications/superset',
            'guides/applications/vault',
          ],
        },
        {
          type: 'category',
          label: 'Plugin System',
          items: [
            'plugins/PLUGIN-ECOSYSTEM',
            'plugins/PLUGIN-SYSTEM',
            'plugins/APPLICATION-PLUGIN-GUIDE',
            'plugins/COMPLIANCE-PLUGIN-GUIDE',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Setup & Configuration',
      items: [
        {
          type: 'category',
          label: 'Authentication',
          items: [
            'setup/AWS_IDENTITY_CENTER_SETUP',
            'setup/COGNITO_MFA_COMPLIANCE_SETUP',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Compliance & Security',
      collapsed: false,
      items: [
        'compliance/MULTI_FRAMEWORK_COMPLIANCE',
        'compliance/AUTOMATED_COMPLIANCE',
        'compliance/DEPLOYMENT_GUIDE',
        {
          type: 'category',
          label: 'Compliance Frameworks',
          items: [
            'compliance/PCI_DSS_COMPLIANCE',
            'compliance/PCI_DSS_APPLICATION_SECURITY',
          ],
        },
        {
          type: 'category',
          label: 'AWS Config & Remediation',
          items: [
            'compliance/AWS_CONFIG_MULTI_STACK',
            'compliance/S3_VERSIONING_REMEDIATION',
          ],
        },
        {
          type: 'category',
          label: 'Security',
          items: [
            'guides/SECURITY_RULES_README',
            'guides/IAM_RULES',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Advanced Topics',
      items: [
        {
          type: 'category',
          label: 'Audit & Monitoring',
          items: [
            'AUDIT_MANAGER',
            'AUDIT_READINESS_GUIDE',
            'AUDITOR_COMPLIANCE_MAPPING',
            'CLOUDTRAIL_AUTO_REMEDIATION',
          ],
        },
        {
          type: 'category',
          label: 'MiniStack (Local AWS)',
          items: [
            'ministack/README',
            'ministack/SETUP',
            'ministack/DEPLOYMENT',
            'ministack/JENKINS',
            'ministack/VERIFICATION',
            'ministack/ADVANCED',
            'ministack/TROUBLESHOOTING',
          ],
        },
        {
          type: 'category',
          label: 'Testing & Validation',
          items: [
            'guides/EXTENDED-TESTING',
            'testing/INTEGRATION_TESTS',
            'testing/COMPLIANCE_TRUTH_TABLES',
          ],
        },
        {
          type: 'category',
          label: 'Databases',
          items: [
            'databases/DATABASE-DEPLOYMENT-GUIDE',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      items: [
        'COMPLIANCE_POSTURE',
        'COMPLIANCE_SEVERITY_LEVELS',
        'EVIDENCE_GENERATION_OUTPUT_EXAMPLE',
        'MAVEN_RELEASE_PROCESS',
        'SSM_PARAMETER_SCOPING',
      ],
    },
  ],
};

module.exports = sidebars;
