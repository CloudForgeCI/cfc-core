// Client-side module to configure Mermaid for Visio-style diagrams
// This runs on the client side to initialize Mermaid with straight lines

import ExecutionEnvironment from '@docusaurus/ExecutionEnvironment';

export function onClientEntry() {
  if (!ExecutionEnvironment.canUseDOM) {
    return;
  }

  // Wait for Mermaid to be available
  const initMermaid = () => {
    if (typeof window !== 'undefined' && window.mermaid) {
      // Configure Mermaid for Visio-style straight lines
      window.mermaid.initialize({
        startOnLoad: false, // We'll render manually
        theme: 'default',
        securityLevel: 'loose',
        flowchart: {
          // Use straight lines (Visio-style) - linear curve for straight connectors
          curve: 'linear',
          useMaxWidth: true,
          htmlLabels: true,
          // Right-angle routing for Visio-style appearance (like sequence diagrams)
          defaultRenderer: 'elk',
          // Ensure straight lines with 90-degree turns
          nodeSpacing: 50,
          rankSpacing: 50,
        },
        graph: {
          // Same configuration for graph syntax
          curve: 'linear',
          defaultRenderer: 'elk',
          nodeSpacing: 50,
          rankSpacing: 50,
        },
        themeVariables: {
          primaryColor: '#667eea',
          primaryTextColor: '#fff',
          primaryBorderColor: '#4c63e8',
          lineColor: '#333333',
          secondaryColor: '#f0f0f0',
          tertiaryColor: '#ffffff',
        },
      });
    }
  };

  // Initialize immediately if mermaid is already loaded
  if (typeof window !== 'undefined' && window.mermaid) {
    initMermaid();
  } else {
    // Wait for mermaid to load
    window.addEventListener('load', () => {
      setTimeout(initMermaid, 100);
    });
  }
}

