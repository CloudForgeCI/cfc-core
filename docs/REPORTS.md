# Reports Dashboard

This project automatically publishes test coverage, validation reports, and drift detection reports to GitHub Pages.

## 📊 Accessing Reports

The reports dashboard is available at:
**https://cloudforge ci.github.io/cfc-core/**

## Available Reports

### 1. Code Coverage Reports (JaCoCo)
- **cloudforge-api**: Unit test coverage for the API module
- **cloudforge-core**: Unit test coverage for the Core module

These reports show:
- Line coverage
- Branch coverage
- Method coverage
- Class coverage

### 2. Validation Reports
- **Truth Table**: Shows all valid configuration combinations
- **Validation Results**: Per-configuration validation status
- **Validation Log**: Detailed log of validation run

### 3. Drift Detection Reports
- Configuration drift detection over time
- Changes in valid/invalid configuration combinations
- Historical comparison reports

## 🚀 How It Works

The reports are automatically generated and published when:
1. Code is pushed to `develop` or `main` branches
2. Manually triggered via workflow dispatch

The workflow:
1. Builds the project with tests (`mvn clean verify`)
2. Runs CFC validation in smoke mode
3. Collects all reports (coverage, validation, drift)
4. Publishes them to GitHub Pages

## 🔧 Local Testing

To generate reports locally:

```bash
# Generate coverage reports
mvn clean verify

# View coverage reports
open cloudforge-api/target/site/jacoco/index.html
open cloudforge-core/target/site/jacoco/index.html

# Run validation (requires AWS CDK)
cd cfc-testing
bash master-validation-system.sh smoke
```

## 📁 Report Structure

```
github-pages-reports/
├── index.html              # Main dashboard
├── coverage/
│   ├── cloudforge-api/    # API coverage reports
│   └── cloudforge-core/   # Core coverage reports
├── validation/
│   ├── truth-table.json   # Configuration truth table
│   ├── truth-table.html   # HTML visualization
│   └── validation-run.log # Validation log
└── drift/
    └── drift-reports/     # Drift detection reports
```

## 🛠️ Configuration

The reports are published via the `.github/workflows/publish-reports.yml` workflow.

### GitHub Pages Settings

Ensure GitHub Pages is enabled:
1. Go to repository Settings
2. Navigate to Pages (under "Code and automation")
3. Source: GitHub Actions
4. No branch selection needed (GitHub Actions deploys directly)

## 🔍 Troubleshooting

### Reports not updating?
1. Check the workflow run status in Actions tab
2. Ensure GitHub Pages is enabled in repository settings
3. Verify the workflow has proper permissions

### Coverage reports missing?
1. Ensure tests are passing (`mvn clean verify`)
2. Check JaCoCo plugin configuration in `pom.xml`
3. Verify the `prepare-package` phase is running

### Validation reports empty?
1. Check if CDK dependencies are properly configured
2. Verify mock AWS credentials are set in workflow
3. Review validation logs in workflow output

## 📝 Adding Custom Reports

To add custom reports to the dashboard:

1. Generate your report in the workflow
2. Copy it to `github-pages-reports/your-report/`
3. Update `index.html` to link to your report
4. Commit and push changes

## 🤝 Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines on contributing to this project.
