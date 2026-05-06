// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer').themes.github;
const darkCodeTheme = require('prism-react-renderer').themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'CloudForge CI',
  tagline: 'Secure, compliant infrastructure on AWS',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://CloudForgeCI.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/cfc-core/documentation/',

  // GitHub pages deployment config.
  organizationName: 'CloudForgeCI',
  projectName: 'cfc-core',

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
  },

  // Client modules for Mermaid configuration
  clientModules: [require.resolve('./src/clientModules.js')],

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          path: '../', // Point to parent docs/ folder
          routeBasePath: '/', // Serve docs at the root of the documentation site
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/CloudForgeCI/cfc-core/edit/develop/docs/',
          exclude: ['web/**'], // Exclude the web folder itself from docs
        },
        blog: false, // Disable blog
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themes: ['@docusaurus/theme-mermaid'],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: 'CloudForge CI',
        logo: {
          alt: 'CloudForge CI Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            href: '../coverage/cloudforge-api/index.html',
            label: 'Coverage',
            position: 'left',
          },
          {
            href: '../javadoc/index.html',
            label: 'JavaDoc',
            position: 'left',
          },
          {
            href: 'https://github.com/CloudForgeCI/cfc-core',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              {
                label: 'Quick Start',
                to: '/compliance/QUICK_START_GUIDE',
              },
              {
                label: 'Applications',
                to: '/applications/',
              },
              {
                label: 'Compliance',
                to: '/compliance/MULTI_FRAMEWORK_COMPLIANCE',
              },
            ],
          },
          {
            title: 'Reports',
            items: [
              {
                label: 'Reports Dashboard',
                href: '../index.html',
              },
              {
                label: 'Code Coverage',
                href: '../coverage/cloudforge-api/index.html',
              },
              {
                label: 'JavaDoc API',
                href: '../javadoc/index.html',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/CloudForgeCI/cfc-core',
              },
              {
                label: 'Issues',
                href: 'https://github.com/CloudForgeCI/cfc-core/issues',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} CloudForge CI. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['java', 'bash', 'json'],
      },
      mermaid: {
        theme: { light: 'default', dark: 'dark' },
        // Note: Flowchart configuration for straight lines is done in clientModules.js
      },
      algolia: {
        // The application ID provided by Algolia
        appId: 'YOUR_APP_ID',
        // Public API key: it is safe to commit it
        apiKey: 'YOUR_SEARCH_API_KEY',
        indexName: 'cfc-core',
        // Optional: see doc section below
        contextualSearch: true,
        // Optional: Algolia search parameters
        searchParameters: {},
        // Optional: path for search page that enabled by default (`false` to disable it)
        searchPagePath: 'search',
      },
    }),
};

module.exports = config;
