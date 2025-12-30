# CloudForge CI Documentation Website

This directory contains the Docusaurus configuration for the CloudForge CI documentation website.

## Structure

```
docs/web/
├── package.json           # Node.js dependencies
├── docusaurus.config.js   # Docusaurus configuration
├── sidebars.js           # Sidebar navigation structure
├── src/                  # Custom React components and CSS
│   └── css/
│       └── custom.css    # Custom styling
└── static/               # Static assets
    └── img/
        ├── logo.svg      # Replace with your brand logo
        └── favicon.ico   # Replace with your favicon
```

## Documentation Content

The actual Markdown documentation is in the parent `docs/` folder. Docusaurus reads from there via the `path: '../'` configuration.

## Local Development

```bash
cd docs/web
npm install
npm start
```

This starts a local development server at `http://localhost:3000`.

## Build

```bash
cd docs/web
npm run build
```

This generates static content into the `build` directory, which can be served on GitHub Pages.

## Customization

### Replace Placeholder Logo

Replace `static/img/logo.svg` with your CloudForge CI brand logo (SVG format recommended).

### Replace Favicon

Replace `static/img/favicon.ico` with your actual favicon file.

### Customize Colors

Edit `src/css/custom.css` to change the color scheme. Current primary color: `#667eea`

## GitHub Actions Integration

The `.github/workflows/publish-reports.yml` workflow automatically:
1. Builds this Docusaurus site
2. Deploys it to GitHub Pages at `/documentation/`
3. Alongside coverage reports, validation results, and JavaDoc

## More Information

- [Docusaurus Documentation](https://docusaurus.io/)
- [CloudForge CI Main README](../README.md)
