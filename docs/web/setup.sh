#!/bin/bash
# Quick setup script for Docusaurus documentation

set -e

echo "Setting up Docusaurus documentation..."
echo ""

# Check if we're in the right directory
if [ ! -f "package.json" ]; then
  echo "Error: package.json not found. Run this script from docs/web/."
  exit 1
fi

# Install dependencies
echo "Installing Node.js dependencies..."
npm install

# Generate package-lock.json
echo "Generated package-lock.json"

# Test build
echo ""
echo "Testing documentation build..."
npm run build

echo ""
echo "Documentation build succeeded."
echo ""
echo "Next steps:"
echo "  1. Replace static/img/logo.svg with your brand logo"
echo "  2. Replace static/img/favicon.ico with your favicon"
echo "  3. Commit package-lock.json: git add package-lock.json"
echo "  4. Push to develop branch to trigger GitHub Pages deployment"
echo ""
echo "To preview locally:"
echo "  npm start"
echo ""
