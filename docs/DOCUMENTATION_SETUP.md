# Documentation Maintenance Guide

The CloudForge CI project publishes a Docusaurus documentation site and an aggregated
JavaDoc API reference alongside its test and validation reports. This guide describes
how to maintain and verify that documentation.

## Published Structure

The GitHub Pages site uses this layout:

```
https://cloudforgeci.github.io/cfc-core/
├── index.html               # Reports dashboard
├── documentation/           # Docusaurus user documentation
├── javadoc/                 # JavaDoc API reference
├── coverage/                # Code coverage reports
├── validation/              # Validation reports
├── sbom/                    # Security reports
└── history/                 # Historical versions
```

The publishing workflow builds:

1. **Docusaurus documentation**
   - Source: Markdown files in `docs/`
   - Configuration: `docs/web/`
   - Output: the documentation site
2. **JavaDoc API reference**
   - Source: Java documentation comments
   - Command: `mvn javadoc:aggregate`
   - Output: aggregated API documentation for `cloudforge-api` and `cloudforge-core`
3. **GitHub Pages content**
   - Publishes the documentation and JavaDoc with the existing reports
   - Retains historical versions according to the workflow configuration

Review `.github/workflows/publish-reports.yml` before changing build triggers, retention, or publication paths.

## Maintain the Docusaurus Site

The main files are:

- `docs/web/package.json` - Node.js dependencies and scripts
- `docs/web/docusaurus.config.js` - Site and navigation configuration
- `docs/web/sidebars.js` - Sidebar structure
- `docs/web/src/css/custom.css` - Site styling
- `docs/web/static/img/` - Logo and favicon
- `docs/web/.gitignore` - Generated-file exclusions
- `docs/web/README.md` - Docusaurus-specific setup notes

When adding or moving a document:

1. Add or move the Markdown source under `docs/`.
2. Update its entry in `docs/web/sidebars.js`.
3. Check links from `docs/README.md` and other index pages.
4. Run the local Docusaurus build.

### Update Site Metadata

Edit `docs/web/docusaurus.config.js` when the publication location changes:

- `url` - GitHub Pages URL
- `organizationName` - GitHub organization
- `projectName` - Repository name

Keep `docs/web/static/img/logo.svg` and `docs/web/static/img/favicon.ico` aligned with the current project identity.

### Configure Search

If Algolia DocSearch is enabled, set the values in `docs/web/docusaurus.config.js`:

```javascript
algolia: {
  appId: 'YOUR_APP_ID',
  apiKey: 'YOUR_SEARCH_API_KEY',
  indexName: 'cfc-core',
}
```

Apply through [Algolia DocSearch](https://docsearch.algolia.com/), replace the placeholder values, or remove the Algolia configuration when hosted search is not in use.

## Verify Changes Locally

### Run Docusaurus

```bash
cd docs/web
npm install
npm start
```

The development server defaults to `http://localhost:3000` and reloads when source files change.

### Build Docusaurus

```bash
cd docs/web
npm run build
npm run serve
```

Use the production build to detect broken links, invalid sidebar entries, and configuration errors before publication.

### Generate JavaDoc

From the repository root:

```bash
mvn javadoc:aggregate
# Output: target/site/apidocs/
```

Review JavaDoc warnings and add or correct source comments where appropriate. The Maven configuration currently uses `<failOnError>false</failOnError>`, so warnings may not fail the build.

## Publishing Checks

Before merging documentation changes:

1. Build the Docusaurus site.
2. Generate JavaDoc when Java API comments or signatures changed.
3. Confirm that sidebar paths match files under `docs/`.
4. Check links to the documentation site, JavaDoc, repository, and contributing guide.
5. Review `.github/workflows/publish-reports.yml` if publication behavior changed.

The reports dashboard should link to:

```
Documentation
  - Docusaurus site
  - Java API reference
  - GitHub repository
  - Contributing guide
```

## Troubleshooting

### `npm ci` reports a missing lockfile

Generate and commit the lockfile:

```bash
cd docs/web
npm install
git add package-lock.json
git commit -m "Add package-lock.json for Docusaurus"
git push
```

### Markdown links are broken

Check that paths in `docs/web/sidebars.js` match the corresponding Markdown files under `docs/`, then run `npm run build`.

### JavaDoc emits warnings

Run `mvn javadoc:aggregate`, review the reported source locations, and update the relevant JavaDoc comments. Because warnings may not fail the build, inspect the command output explicitly.


## References

- [Docusaurus documentation](https://docusaurus.io/docs)
- [JavaDoc documentation comment specification](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html)
- [CloudForge issues](https://github.com/CloudForgeCI/cfc-core/issues)
