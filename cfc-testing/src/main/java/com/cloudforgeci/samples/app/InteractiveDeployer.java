package com.cloudforgeci.samples.app;

import com.cloudforgeci.api.compute.ApplicationLoader;
import com.cloudforge.core.config.ApplicationInfo;
import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.samples.launchers.ApplicationEc2Stack;
import com.cloudforgeci.samples.launchers.ApplicationFargateStack;

// Auto-discovery via ApplicationLoader - no need to import all ApplicationSpecs manually
import com.cloudforge.core.interfaces.ApplicationSpec;

// CDK-NAG for construct-level compliance validation
import io.github.cdklabs.cdknag.AwsSolutionsChecks;
import io.github.cdklabs.cdknag.HIPAASecurityChecks;
import io.github.cdklabs.cdknag.NagReportFormat;
import io.github.cdklabs.cdknag.PCIDSS321Checks;
import io.github.cdklabs.cdknag.NagPack;

// Configuration Introspection imports
import com.cloudforge.core.config.ConfigFieldInfo;
import com.cloudforge.core.config.ConfigurationIntrospector;
import com.cloudforge.core.config.DefaultValueResolver;
import com.cloudforge.core.config.ValidationResult;
import com.cloudforge.core.annotation.FieldTag;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Aspects;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Environment;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

/**
 * CloudForge 3.0.0 Universal Application Deployer
 *
 * <p>Uses ApplicationLoader for auto-discovery of application plugins via ServiceLoader.
 * Built-in applications and external plugins are automatically discovered from
 * META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec registrations.</p>
 *
 * <p>Revamped with intelligent question skipping using truth tables:</p>
 * <ul>
 *   <li>Application selection determines available runtimes</li>
 *   <li>Security profile determines default compliance settings</li>
 *   <li>Runtime type determines resource configuration options</li>
 *   <li>OIDC support is application-specific</li>
 * </ul>
 *
 * <p>Built-in applications (14+) across 8 categories:</p>
 * <ul>
 *   <li>CI/CD: Jenkins, GitLab, Drone</li>
 *   <li>VCS: Gitea</li>
 *   <li>Monitoring: Grafana, Prometheus</li>
 *   <li>Databases: PostgreSQL, Redis</li>
 *   <li>Secrets Management: HashiCorp Vault</li>
 *   <li>Artifact Registry: Nexus, Harbor</li>
 *   <li>Collaboration: Mattermost</li>
 *   <li>Analytics: Metabase, Superset</li>
 * </ul>
 *
 * <p>External plugins (like SonarQube in cfc-testing) are automatically included.</p>
 */
public class InteractiveDeployer {

    private static final Logger LOG = Logger.getLogger(InteractiveDeployer.class.getName());
    private static final Scanner scanner = createScanner();

    /**
     * Create a Scanner that reads from /dev/tty on Unix systems.
     * This bypasses stdin redirection when running as a CDK subprocess.
     */
    private static Scanner createScanner() {
        // Try /dev/tty first (works on macOS/Linux even when stdin is redirected)
        try {
            java.io.File tty = new java.io.File("/dev/tty");
            if (tty.exists()) {
                return new Scanner(new java.io.FileInputStream(tty));
            }
        } catch (Exception e) {
            // Fall back to System.in
        }
        return new Scanner(System.in);
    }

    // Application Registry - Auto-discovered via ServiceLoader (includes built-in + custom plugins)
    private static final Map<String, ApplicationSpec> APPLICATION_REGISTRY = ApplicationLoader.discover();

    // Application Categories - Auto-generated from ApplicationSpec metadata
    private static final Map<String, List<ApplicationInfo>> APPLICATION_CATEGORIES = createApplicationCategories();

    private static Map<String, List<ApplicationInfo>> createApplicationCategories() {
        // Auto-generate categories from discovered ApplicationSpecs
        Map<String, List<ApplicationInfo>> categories = new HashMap<>();

        // Group applications by category using ApplicationLoader
        Map<String, List<ApplicationSpec>> grouped = ApplicationLoader.discoverGroupedByCategory();

        // Convert ApplicationSpec to ApplicationInfo for UI display
        for (Map.Entry<String, List<ApplicationSpec>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<ApplicationInfo> apps = new ArrayList<>();

            for (ApplicationSpec spec : entry.getValue()) {
                apps.add(new ApplicationInfo(
                    spec.applicationId(),
                    spec.displayName(),
                    spec.description(),
                    spec.supportsFargate(),
                    spec.supportsEc2(),
                    spec.supportsOidcIntegration(),
                    spec.defaultCpu(),
                    spec.defaultMemory(),
                    spec.defaultInstanceType()
                ));
            }

            categories.put(category, apps);
        }

        return categories;
    }

    public static void main(String[] args) {
        // Check if we're being called from our own subprocess or non-interactive CDK command
        String cfcDeploying = System.getenv("CFC_DEPLOYING");

        // Check parent process for cdk destroy/diff/list commands (skip menu for these)
        boolean skipMenu = cfcDeploying != null;
        if (!skipMenu) {
            try {
                // Check grandparent since CDK spawns node which spawns java
                ProcessHandle current = ProcessHandle.current();
                for (int i = 0; i < 3; i++) {  // Check up to 3 levels
                    ProcessHandle ancestor = current.parent().orElse(null);
                    if (ancestor == null) break;
                    String cmd = ancestor.info().commandLine().orElse("");
                    if (cmd.contains("cdk destroy") || cmd.contains("cdk diff") || cmd.contains("cdk list")) {
                        skipMenu = true;
                        break;
                    }
                    current = ancestor;
                }
            } catch (Exception e) {
                // Ignore - can't determine parent process
            }
        }

        if (skipMenu) {
            // We're in a deploy subprocess or cdk destroy - just synthesize quietly without menu
            try {
                String contextFile = "deployment-context.json";
                if (Files.exists(Paths.get(contextFile))) {
                    DeploymentConfig config = DeploymentConfig.fromFile(contextFile);
                    // Reconstruct applicationSpec from applicationId
                    if (config.applicationId != null) {
                        config.applicationSpec = APPLICATION_REGISTRY.get(config.applicationId);
                    }
                    // Pass "1" to force synth-only, false to not save context again
                    deployInfrastructure(config, "1", false);
                }
            } catch (Exception e) {
                System.err.println("❌ Synthesis failed: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        printWelcomeBanner();

        // Check for command line arguments
        String customStackName = null;
        String deploymentOption = null;
        boolean forceInteractive = false;
        boolean forceDelete = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--interactive") || args[i].equals("-i")) {
                forceInteractive = true;
                System.out.println("🎯 Interactive mode enabled via CLI flag");
            } else if (args[i].equals("--force") || args[i].equals("-f")) {
                forceDelete = true;
                System.out.println("🗑️  Force mode enabled - will delete existing context");
            } else if (customStackName == null) {
                customStackName = args[i];
                System.out.println("📝 Using custom stack name: " + customStackName);
            } else if (deploymentOption == null) {
                deploymentOption = args[i];
                System.out.println("📝 Using deployment option: " + deploymentOption);
            }
        }

        // Also check INTERACTIVE environment variable
        // This allows: INTERACTIVE=true cdk synth
        if (!forceInteractive) {
            String envInteractive = System.getenv("INTERACTIVE");
            if (envInteractive != null &&
                ("true".equalsIgnoreCase(envInteractive) || "1".equals(envInteractive) || "yes".equalsIgnoreCase(envInteractive))) {
                forceInteractive = true;
                System.out.println("🎯 Interactive mode enabled via INTERACTIVE env var");
            }
        }

        try {
            String contextFile = "deployment-context.json";

            // --force: delete existing context and start fresh
            if (forceDelete && Files.exists(Paths.get(contextFile))) {
                System.out.println("🗑️  Deleting existing deployment context...");
                Files.delete(Paths.get(contextFile));
                System.out.println("✅ Context deleted. Starting fresh configuration...\n");
            }

            if (Files.exists(Paths.get(contextFile))) {
                // Context exists - load it and go directly to deployment options
                if (forceInteractive) {
                    System.out.println("🔄 Ignoring saved context, starting fresh configuration...\n");
                    DeploymentConfig config = collectConfiguration(customStackName);
                    deployInfrastructure(config, deploymentOption);
                } else {
                    System.out.println("📁 Found saved deployment context: " + contextFile);
                    loadContextFromFileAndDeploy(contextFile, deploymentOption, customStackName);
                }
            } else {
                // No context exists - run interactive prompts
                System.out.println("📝 No saved configuration found, starting interactive setup...\n");
                DeploymentConfig config = collectConfiguration(customStackName);
                deployInfrastructure(config, deploymentOption);
            }
        } catch (Exception e) {
            System.err.println("❌ Deployment failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Show action menu when existing context is found.
     * Returns the deployment option (1-7) or null if user wants to reconfigure.
     */
    private static String showActionMenu() {
        System.out.println("\n📋 What would you like to do?");
        System.out.println("==============================");
        System.out.println("1. Synthesize only");
        System.out.println("2. Deploy");
        System.out.println("3. Redeploy (delete + deploy)");
        System.out.println("4. Dry-run (changeset)");
        System.out.println("5. Export Template (YAML/JSON)");
        System.out.println("6. Reconfigure (start fresh interactive setup)");
        System.out.println("7. Cancel");
        System.out.print("\nChoose option [1-7]: ");

        try {
            if (scanner.hasNextLine()) {
                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1", "2", "3", "4", "5":
                        return choice;
                    case "6":
                        return null; // Signal to reconfigure
                    case "7":
                        System.out.println("❌ Cancelled by user");
                        System.exit(0);
                    default:
                        System.out.println("Invalid choice. Defaulting to synthesis only.");
                        return "1";
                }
            }
        } catch (Exception e) {
            System.out.println("Input error: " + e.getMessage());
        }
        return "1";
    }

    private static void printWelcomeBanner() {
        int appCount = APPLICATION_REGISTRY.size();
        int categoryCount = APPLICATION_CATEGORIES.size();

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  🚀 CloudForge 3.0.0 Universal Application Deployer          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println("");
        System.out.println("📖 Deploy containerized applications with compliance-first infrastructure:");
        System.out.println("   ✓ " + appCount + " Applications across " + categoryCount + " categories (auto-discovered via ServiceLoader)");
        System.out.println("   ✓ EC2 or Fargate runtime options");
        System.out.println("   ✓ OIDC Authentication (Cognito, IAM Identity Center)");
        System.out.println("   ✓ Automatic SSL certificate management");
        System.out.println("   ✓ Security profiles (DEV/STAGING/PRODUCTION)");
        System.out.println("   ✓ SOC2, PCI-DSS, HIPAA, GDPR compliance validation");
        System.out.println("   ✓ Advanced monitoring and encryption");
        System.out.println("");
    }

    /**
     * TRUTH TABLE-DRIVEN CONFIGURATION COLLECTION
     *
     * Questions are intelligently skipped based on previous answers:
     * - Application selection → determines available runtimes & OIDC support
     * - Security profile → auto-enables compliance features
     * - Runtime type → determines resource configuration options
     * - OIDC support → only asked if application supports it
     */
    private static DeploymentConfig collectConfiguration(String customStackName) {
        DeploymentConfig config = new DeploymentConfig();

        // ========== BASIC CONFIGURATION ==========
        if (customStackName != null && !customStackName.trim().isEmpty()) {
            config.stackName = customStackName;
            System.out.println("✅ Stack name set to: " + config.stackName);
        } else {
            config.stackName = promptRequired("Stack Name", "my-cloudforge-stack");
        }
        config.environment = promptChoice("Environment", new String[]{"dev", "staging", "prod"}, "dev");

        // ========== APPLICATION SELECTION ==========
        System.out.println("\n📦 Application Selection:");
        System.out.println("=========================");
        ApplicationInfo selectedApp = selectApplication();
        config.applicationId = selectedApp.id;
        config.applicationName = selectedApp.name;
        config.applicationSpec = APPLICATION_REGISTRY.get(selectedApp.id);

        // Auto-enable database provisioning for applications that require it
        if (config.applicationSpec instanceof com.cloudforge.core.interfaces.DatabaseSpec dbSpec) {
            var dbRequirement = dbSpec.databaseRequirement();
            if (dbRequirement != null &&
                dbRequirement.type() == com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement.RequirementType.REQUIRED) {
                config.provisionDatabase = true;
                System.out.println("ℹ️  " + config.applicationName + " requires a database - auto-enabling RDS provisioning");
            }
        }

        // ========== SECURITY PROFILE (determines many defaults) ==========
        System.out.println("\n🔒 Security Profile Selection:");
        System.out.println("================================");
        System.out.println("Security profiles determine compliance requirements and defaults:");
        System.out.println("  • DEV: Relaxed security, minimal compliance, lower costs");
        System.out.println("  • STAGING: Moderate security, recommended for pre-production testing");
        System.out.println("  • PRODUCTION: Strict security, full compliance enforcement");
        System.out.println("");
        config.securityProfile = SecurityProfile.valueOf(
            promptChoice("Security Profile", new String[]{"DEV", "STAGING", "PRODUCTION"}, "STAGING").toUpperCase());

        // ========== RUNTIME SELECTION (truth table: based on application support) ==========
        System.out.println("\n⚙️  Runtime Configuration:");
        System.out.println("========================");

        if (selectedApp.supportsFargate && selectedApp.supportsEc2) {
            // Application supports both - let user choose
            config.runtime = RuntimeType.valueOf(
                promptChoice("Runtime", new String[]{"FARGATE", "EC2"}, "FARGATE").toUpperCase());
        } else if (selectedApp.supportsFargate) {
            // Only Fargate supported
            config.runtime = RuntimeType.FARGATE;
            System.out.println("✅ Runtime: FARGATE (only option for " + selectedApp.name + ")");
        } else if (selectedApp.supportsEc2) {
            // Only EC2 supported
            config.runtime = RuntimeType.EC2;
            System.out.println("✅ Runtime: EC2 (only option for " + selectedApp.name + ")");
        } else {
            // Default to Fargate
            config.runtime = RuntimeType.FARGATE;
            System.out.println("⚠️  No runtime specified, defaulting to FARGATE");
        }

        // Topology automatically set based on application
        // Use APPLICATION_SERVICE for all universal applications deployed through ApplicationFactory
        config.topology = TopologyType.APPLICATION_SERVICE;

        // ========== DOMAIN CONFIGURATION ==========
        System.out.println("\n🌐 Domain Configuration:");
        System.out.println("========================");
        config.domain = promptOptional("Domain (e.g., example.com)", "");
        if (!config.domain.isEmpty()) {
            config.subdomain = promptOptional("Subdomain (e.g., ci, app, gitlab)", "");
            config.enableSsl = promptYesNo("Enable SSL Certificate", true);
        } else {
            config.subdomain = "";
            config.enableSsl = false;
        }

        // ========== OIDC AUTHENTICATION (truth table: only if application supports it) ==========
        if (selectedApp.supportsOidc) {
            System.out.println("\n🔐 OIDC Authentication (Application Supports It!):");
            System.out.println("===================================================");
            System.out.println("✅ " + selectedApp.name + " supports OIDC integration");
            System.out.println("");

            boolean enableOidc = promptYesNo("Enable OIDC authentication for " + selectedApp.name,
                config.securityProfile == SecurityProfile.PRODUCTION);

            if (enableOidc) {
                configureOidcAuthentication(config, config.applicationId);
            } else {
                config.oidcProvider = "none";
            }
        } else {
            // Application doesn't support OIDC - skip entirely
            System.out.println("\nℹ️  " + selectedApp.name + " does not support OIDC authentication");
            config.oidcProvider = "none";
        }

        // ========== RESOURCE CONFIGURATION (truth table: based on runtime) ==========
        System.out.println("\n💻 Resource Configuration:");
        System.out.println("==========================");

        if (config.runtime == RuntimeType.EC2) {
            // EC2-specific configuration with minimum requirements
            System.out.println("⚠️  Minimum recommended instance type for " + selectedApp.name + ": " + selectedApp.minInstanceType);
            config.instanceType = promptChoice("EC2 Instance Type",
                new String[]{"t3.micro", "t3.small", "t3.medium", "t3.large", "t3.xlarge", "t3.2xlarge"},
                selectedApp.minInstanceType);

            // CPU/Memory are informational for EC2 (determined by instance type)
            config.cpu = 1024;  // Not used for EC2
            config.memory = 2048;  // Not used for EC2
        } else {
            // Fargate-specific configuration with minimum requirements
            if (selectedApp.minCpu > 0) {
                System.out.println("⚠️  Minimum requirements for " + selectedApp.name + ":");
                System.out.println("   CPU: " + selectedApp.minCpu + " units, Memory: " + selectedApp.minMemory + " MB");
                System.out.println("");
            }

            int minCpu = Math.max(selectedApp.minCpu, 256);
            int minMemory = Math.max(selectedApp.minMemory, 512);

            config.cpu = promptIntWithValidation("CPU (units)", selectedApp.minCpu > 0 ? selectedApp.minCpu : 1024, minCpu, 16384);
            config.memory = promptIntWithValidation("Memory (MB)", selectedApp.minMemory > 0 ? selectedApp.minMemory : 2048, minMemory, 30720);

            // Validate Fargate CPU/Memory combinations
            // https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-cpu-memory-error.html
            if (!isValidFargateCpuMemoryCombination(config.cpu, config.memory)) {
                System.out.println("\n⚠️  Invalid CPU/Memory combination for Fargate.");
                System.out.println("   Adjusting memory to valid value for " + config.cpu + " CPU units...");
                config.memory = getValidFargateMemoryForCpu(config.cpu, config.memory);
                System.out.println("   ✅ Adjusted to: " + config.cpu + " CPU / " + config.memory + " MB Memory");
            }
        }

        // ========== SCALING CONFIGURATION ==========
        System.out.println("\n📈 Scaling Configuration:");
        System.out.println("=========================");
        config.minInstanceCapacity = promptIntWithValidation("Minimum Instance Capacity", 1, 1, 10);
        config.maxInstanceCapacity = promptIntWithValidation("Maximum Instance Capacity", 3, 1, 20);

        // Truth table: Auto-scaling only makes sense if max > min
        config.enableAutoScaling = config.maxInstanceCapacity > config.minInstanceCapacity;
        if (config.enableAutoScaling) {
            System.out.println("✅ Auto Scaling enabled (max capacity > min capacity)");
            config.cpuTargetUtilization = promptIntWithValidation("CPU Target Utilization (%)", 60, 10, 90);
        } else {
            System.out.println("ℹ️  Auto Scaling disabled (max capacity = min capacity)");
            config.cpuTargetUtilization = 60; // Default
        }

        // ========== NETWORK CONFIGURATION ==========
        System.out.println("\n🌐 Network Configuration:");
        System.out.println("==========================");
        String networkModeStr = promptChoice("Network Mode",
            new String[]{"public-no-nat", "private-with-nat"}, "public-no-nat");
        config.networkMode = NetworkMode.fromString(networkModeStr);

        // Truth table: WAF and CloudFront recommended for PRODUCTION
        config.wafEnabled = promptYesNo("Enable WAF Protection",
            config.securityProfile == SecurityProfile.PRODUCTION);
        config.cloudfrontEnabled = promptYesNo("Enable CloudFront CDN", false);

        // ========== COMPLIANCE CONFIGURATION (truth table: synthesis based on security profile) ==========
        configureCompliance(config);

        // ========== ADVANCED CONFIGURATION ==========
        configureAdvancedSettings(config);

        return config;
    }

    /**
     * Configure OIDC authentication with application-level integration.
     *
     * Two completely separate authentication systems:
     * 1. Amazon Cognito - Standalone user directory (OIDC)
     * 2. IAM Identity Center - Enterprise SSO (SAML for apps that support it)
     *
     * Delegates to ApplicationSpec to determine supported auth modes (single source of truth).
     * Uses OidcIntegration.supportsCognito() and supportsIdentityCenterSaml() to filter options.
     */
    private static void configureOidcAuthentication(DeploymentConfig config, String applicationId) {
        // Get supported auth modes from ApplicationSpec (single source of truth)
        ApplicationSpec appSpec = APPLICATION_REGISTRY.get(applicationId);
        if (appSpec == null) {
            LOG.warning("ApplicationSpec not found for: " + applicationId);
            config.oidcProvider = "none";
            return;
        }

        List<String> supportedAuthModes = appSpec.getSupportedAuthModes();
        String recommendedAuthMode = appSpec.getRecommendedAuthMode();

        // Check OidcIntegration capabilities to filter provider options
        boolean supportsCognito = true;  // Default for apps without OidcIntegration
        boolean supportsIdentityCenterSaml = false;  // Default: most apps don't support SAML
        String authType = "OIDC";

        if (appSpec.supportsOidcIntegration()) {
            var oidcIntegration = appSpec.getOidcIntegration();
            if (oidcIntegration != null) {
                supportsCognito = oidcIntegration.supportsCognito();
                supportsIdentityCenterSaml = oidcIntegration.supportsIdentityCenterSaml();
                authType = oidcIntegration.getAuthenticationType();
            }
        }

        // Build list of available providers based on capabilities
        List<String> availableProviders = new java.util.ArrayList<>();
        List<String> providerDescriptions = new java.util.ArrayList<>();

        // For SAML apps, only show SAML providers (not OIDC)
        // For OIDC apps, only show OIDC providers (not SAML)
        if (supportsCognito && "OIDC".equals(authType)) {
            availableProviders.add("cognito");
            providerDescriptions.add("Amazon Cognito - Standalone user directory (OIDC)");
        }

        // For SAML apps (Mattermost, Metabase), offer Cognito SAML instead of Identity Center
        // Cognito SAML has full API support - no manual console steps required
        if (supportsIdentityCenterSaml && "SAML".equals(authType)) {
            availableProviders.add("cognito-saml");
            providerDescriptions.add("Cognito SAML - Full API support, group sync (recommended for SAML apps)");
        }

        // External IdP is always available as fallback
        availableProviders.add("external-idp");
        providerDescriptions.add("External IdP - Okta, Auth0, etc. (manual OIDC endpoints)");

        System.out.println("\n📋 OIDC/SAML Provider Selection:");
        System.out.println("=================================");
        System.out.println("Application: " + applicationId);
        System.out.println("Auth Type: " + authType);
        System.out.println("");
        System.out.println("Available providers for this application:");
        for (int i = 0; i < availableProviders.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + providerDescriptions.get(i));
        }

        // Show why some options are unavailable
        if ("SAML".equals(authType)) {
            System.out.println("");
            System.out.println("ℹ️  This application requires SAML authentication");
            System.out.println("   Cognito OIDC is not available (use Cognito SAML instead)");
        } else if ("OIDC".equals(authType)) {
            System.out.println("");
            System.out.println("ℹ️  This application uses OIDC authentication");
            System.out.println("   Cognito SAML is not available (use Cognito OIDC instead)");
        }
        System.out.println("");

        // Determine default provider based on auth type
        String defaultProvider;
        if ("SAML".equals(authType) && supportsIdentityCenterSaml) {
            defaultProvider = "cognito-saml";  // SAML apps default to cognito-saml
        } else if ("OIDC".equals(authType) && supportsCognito) {
            defaultProvider = "cognito";  // OIDC apps default to cognito
        } else {
            defaultProvider = "external-idp";  // Fallback
        }

        config.oidcProvider = promptChoice("OIDC Provider",
            availableProviders.toArray(String[]::new), defaultProvider);

        switch (config.oidcProvider) {
            case "cognito" -> configureCognitoOidc(config, supportedAuthModes, recommendedAuthMode);
            case "cognito-saml" -> configureCognitoSaml(config, supportedAuthModes, recommendedAuthMode);
            case "external-idp" -> configureExternalOidc(config, supportedAuthModes, recommendedAuthMode);
        }
    }

    /**
     * Helper to select authMode from ApplicationSpec-provided supported modes.
     * Delegates all logic to the library (single source of truth).
     */
    private static void selectAuthMode(DeploymentConfig config, List<String> supportedAuthModes, String recommendedAuthMode) {
        // Filter out "none" since we're already configuring OIDC
        List<String> oidcAuthModes = supportedAuthModes.stream()
            .filter(mode -> !mode.equals("none"))
            .toList();

        if (oidcAuthModes.isEmpty()) {
            // Application doesn't support OIDC at all
            config.authMode = AuthMode.NONE;
            System.out.println("\n⚠️  Application doesn't support OIDC authentication");
            return;
        }

        if (oidcAuthModes.size() == 1) {
            // Only one OIDC mode supported - use it automatically
            config.authMode = AuthMode.fromString(oidcAuthModes.get(0));
            System.out.println("\n✅ Using " + config.authMode + " (only OIDC mode supported by this application)");
        } else {
            // Multiple modes supported - let user choose
            System.out.println("\n🔧 Authentication Mode:");
            System.out.println("======================");
            System.out.println("Choose where OIDC authentication happens:");
            System.out.println("  1. alb-oidc - Authentication at ALB (works for all applications)");
            System.out.println("  2. application-oidc - Authentication within the application (deeper integration)");
            System.out.println("");
            System.out.println("Recommendation: " + recommendedAuthMode);
            System.out.println("");

            String authModeStr = promptChoice("Authentication Mode",
                oidcAuthModes.toArray(String[]::new), recommendedAuthMode);
            config.authMode = AuthMode.fromString(authModeStr);
        }

                // OIDC requires SSL - auto-enable if not already set
        if (!config.enableSsl) {
            config.enableSsl = true;
            if (config.domain == null || config.domain.isEmpty()) {
                System.out.println("\n🔒 SSL automatically enabled (OIDC requires HTTPS)");
                System.out.println("   Using AWS Private CA for ALB DNS name (~$400/month, auto-deleted with stack)");
            } else {
                System.out.println("\n🔒 SSL automatically enabled (OIDC requires HTTPS)");
            }
        }
    }

    private static void configureCognitoOidc(DeploymentConfig config, List<String> supportedAuthModes, String recommendedAuthMode) {
        System.out.println("\n🔐 Amazon Cognito Configuration:");
        System.out.println("=================================");

        boolean autoProvision = promptYesNo("Auto-provision new Cognito User Pool", true);

        if (autoProvision) {
            config.cognitoAutoProvision = true;

            System.out.println("\n⚠️  Domain prefix must be globally unique across ALL AWS accounts");
            System.out.println("   Example: " + config.applicationId + "-auth-mycompany-prod");
            config.cognitoDomainPrefix = promptRequired("Cognito Domain Prefix (globally unique)",
                config.stackName + "-auth");

            config.cognitoUserPoolName = promptOptional("User Pool Name", config.stackName + "-users");
            config.cognitoMfaEnabled = promptYesNo("Enable MFA (Multi-Factor Authentication)",
                config.securityProfile == SecurityProfile.PRODUCTION);

            // User Groups
            System.out.println("\n👥 User Groups Configuration:");
            config.cognitoCreateGroups = promptYesNo("Create admin and user groups", true);

            if (config.cognitoCreateGroups) {
                config.cognitoAdminGroupName = promptOptional("Admin Group Name", config.applicationId + "-Admins");
                config.cognitoUserGroupName = promptOptional("User Group Name", config.applicationId + "-Users");
            }

            // Initial Admin User
            System.out.println("\n👤 Initial Admin User:");
            boolean createAdmin = promptYesNo("Create initial admin user", true);
            if (createAdmin) {
                config.cognitoInitialAdminEmail = promptRequired("Admin email address", "");
                if (config.cognitoMfaEnabled) {
                    config.cognitoInitialAdminPhone = promptOptional("Admin phone number (E.164 format, e.g., +12025551234)", "");
                }

                System.out.println("   ✅ Admin user will be created with temporary password");
                System.out.println("   📧 User will receive email with password reset instructions");
            }

            System.out.println("\n✅ Cognito auto-provisioning configured");
        } else {
            // Use existing User Pool
            config.cognitoUserPoolId = promptRequired("Cognito User Pool ID (e.g., us-east-1_abc123xyz)", "");
            config.cognitoAppClientId = promptOptional("App Client ID (leave empty to create new)", "");
            config.cognitoDomainPrefix = promptRequired("Cognito Domain Prefix", "");

            System.out.println("\n✅ Existing Cognito configuration captured");
        }

        // Delegate authMode selection to library (single source of truth)
        selectAuthMode(config, supportedAuthModes, recommendedAuthMode);
    }

    /**
     * Configure Cognito SAML authentication.
     * Cognito User Pool acts as SAML 2.0 Identity Provider.
     * Used for applications that need SAML for group sync (Mattermost, Metabase).
     *
     * <p><b>Cognito SAML Endpoints:</b></p>
     * <ul>
     *   <li>SSO URL: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/SSO</li>
     *   <li>Metadata: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/metadata</li>
     * </ul>
     */
    private static void configureCognitoSaml(DeploymentConfig config, List<String> supportedAuthModes, String recommendedAuthMode) {
        System.out.println("\n📝 Cognito SAML Configuration:");
        System.out.println("================================");
        System.out.println("✅ Cognito User Pool will act as SAML 2.0 Identity Provider");
        System.out.println("");
        System.out.println("Cognito SAML provides:");
        System.out.println("  • Full API support - no manual console steps required");
        System.out.println("  • Automatic SAML attribute mapping");
        System.out.println("  • Group sync support for team/channel membership");
        System.out.println("");

        boolean autoProvision = promptYesNo("Auto-provision new Cognito User Pool", true);

        if (autoProvision) {
            config.cognitoAutoProvision = true;

            System.out.println("\n⚠️  Domain prefix must be globally unique across ALL AWS accounts");
            System.out.println("   Example: " + config.applicationId + "-auth-mycompany-prod");
            config.cognitoDomainPrefix = promptRequired("Cognito Domain Prefix (globally unique)",
                config.stackName + "-auth");

            config.cognitoUserPoolName = promptOptional("User Pool Name", config.stackName + "-users");
            config.cognitoMfaEnabled = promptYesNo("Enable MFA (Multi-Factor Authentication)",
                config.securityProfile == SecurityProfile.PRODUCTION);

            // User Groups for SAML group sync
            System.out.println("\n👥 User Groups Configuration (for SAML group sync):");
            config.cognitoCreateGroups = promptYesNo("Create admin and user groups", true);

            if (config.cognitoCreateGroups) {
                config.cognitoAdminGroupName = promptOptional("Admin Group Name", config.applicationId + "-Admins");
                config.cognitoUserGroupName = promptOptional("User Group Name", config.applicationId + "-Users");
            }

            // Initial Admin User
            System.out.println("\n👤 Initial Admin User:");
            boolean createAdmin = promptYesNo("Create initial admin user", true);
            if (createAdmin) {
                config.cognitoInitialAdminEmail = promptRequired("Admin email address", "");
                if (config.cognitoMfaEnabled) {
                    config.cognitoInitialAdminPhone = promptOptional("Admin phone number (E.164 format, e.g., +12025551234)", "");
                }

                System.out.println("   ✅ Admin user will be created with temporary password");
                System.out.println("   📧 User will receive email with password reset instructions");
            }

            System.out.println("\n✅ Cognito SAML IdP auto-provisioning configured");
            System.out.println("ℹ️  SAML endpoints will be auto-generated after deployment:");
            System.out.println("   • SSO URL: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/SSO");
            System.out.println("   • Metadata: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/metadata");
        } else {
            // Use existing User Pool
            config.cognitoUserPoolId = promptRequired("Cognito User Pool ID (e.g., us-east-1_abc123xyz)", "");
            config.cognitoAppClientId = promptOptional("App Client ID (leave empty to create new)", "");
            config.cognitoDomainPrefix = promptRequired("Cognito Domain Prefix", "");

            System.out.println("\n✅ Existing Cognito SAML configuration captured");
            System.out.println("ℹ️  SAML endpoints (use these in your application):");
            System.out.println("   • SSO URL: https://cognito-idp.{region}.amazonaws.com/" + config.cognitoUserPoolId + "/saml2/idp/SSO");
            System.out.println("   • Metadata: https://cognito-idp.{region}.amazonaws.com/" + config.cognitoUserPoolId + "/saml2/idp/metadata");
        }

        // For SAML apps, use application-oidc mode (auth happens at application level)
        config.authMode = AuthMode.APPLICATION_OIDC;
        System.out.println("\n✅ Using APPLICATION_OIDC mode (SAML authentication at application level)");
    }

    private static void configureExternalOidc(DeploymentConfig config, List<String> supportedAuthModes, String recommendedAuthMode) {
        System.out.println("\n📝 External Identity Provider Configuration:");
        System.out.println("==============================================");
        System.out.println("Configure your IdP application with:");
        System.out.println("  Grant Type: Authorization Code");
        System.out.println("  Scopes: openid email profile");
        System.out.println("");

        config.oidcIssuer = promptRequired("OIDC Issuer URL", "");
        config.oidcAuthorizationEndpoint = promptRequired("Authorization Endpoint URL", "");
        config.oidcTokenEndpoint = promptRequired("Token Endpoint URL", "");
        config.oidcUserInfoEndpoint = promptRequired("UserInfo Endpoint URL", "");
        config.oidcClientId = promptRequired("Client ID", "");
        config.oidcClientSecretName = promptOptional("Client Secret Name in Secrets Manager",
            config.applicationId + "/oidc/client-secret");

        System.out.println("\n✅ External IdP configuration captured");

        // Delegate authMode selection to library (single source of truth)
        selectAuthMode(config, supportedAuthModes, recommendedAuthMode);
    }

    /**
     * COMPLIANCE CONFIGURATION using truth table synthesis:
     * - PRODUCTION → Enable all compliance features by default
     * - STAGING → Enable monitoring and encryption
     * - DEV → Minimal compliance
     */
    private static void configureCompliance(DeploymentConfig config) {
        System.out.println("\n🔧 Compliance & Security Configuration:");
        System.out.println("========================================");
        System.out.println("📖 Settings are auto-configured based on security profile");
        System.out.println("");

        // Truth table: Encryption required for STAGING/PRODUCTION
        boolean defaultEncryption = (config.securityProfile == SecurityProfile.STAGING ||
                                     config.securityProfile == SecurityProfile.PRODUCTION);
        config.enableEncryption = promptYesNo("Enable Encryption at Rest (required for PCI-DSS, HIPAA, GDPR)",
            defaultEncryption);

        // Truth table: Monitoring required for STAGING/PRODUCTION
        boolean defaultMonitoring = (config.securityProfile == SecurityProfile.STAGING ||
                                     config.securityProfile == SecurityProfile.PRODUCTION);
        config.enableMonitoring = promptYesNo("Enable CloudWatch Monitoring", defaultMonitoring);

        // Truth table: AWS Config only for PRODUCTION
        if (config.securityProfile == SecurityProfile.PRODUCTION) {
            config.awsConfigEnabled = promptYesNo("Enable AWS Config Compliance Monitoring", true);

            if (config.awsConfigEnabled) {
                System.out.println("\n📋 AWS Config Infrastructure Setup:");
                System.out.println("AWS Config has account-level singleton resources (Recorder + Delivery Channel).");
                System.out.println("Only ONE stack per region should create these resources.");
                config.createConfigInfrastructure = promptYesNo("Create Config Infrastructure (first stack in region)", true);
            } else {
                config.createConfigInfrastructure = false; // No infrastructure if Config disabled
            }
        } else {
            config.awsConfigEnabled = false;
            config.createConfigInfrastructure = false; // No infrastructure for non-production profiles
        }

        // Truth table: GuardDuty for PRODUCTION/STAGING
        if (config.securityProfile == SecurityProfile.PRODUCTION ||
            config.securityProfile == SecurityProfile.STAGING) {
            System.out.println("\n🛡️  AWS GuardDuty - Threat Detection:");
            System.out.println("====================================");
            System.out.println("Continuous monitoring for malicious activity");
            System.out.println("  • Cost: ~$30-100/month");
            System.out.println("  • Compliance: Required for PCI-DSS Req 11.4");
            config.guardDutyEnabled = promptYesNo("Enable AWS GuardDuty",
                config.securityProfile == SecurityProfile.PRODUCTION);
        } else {
            config.guardDutyEnabled = false;
        }

        // Truth table: Compliance frameworks only for PRODUCTION/STAGING
        if (config.securityProfile == SecurityProfile.PRODUCTION ||
            config.securityProfile == SecurityProfile.STAGING) {
            System.out.println("\n📜 Compliance Framework Validation:");
            System.out.println("===================================");

            // PRODUCTION mode: Always enable and select frameworks
            if (config.securityProfile == SecurityProfile.PRODUCTION) {
                config.auditManagerEnabled = true;
                System.out.println("✓ Compliance framework validation is REQUIRED for PRODUCTION deployments");
                selectComplianceFrameworks(config);
            } else {
                // STAGING mode: Ask if they want it
                config.auditManagerEnabled = promptYesNo("Enable Compliance Framework Validation", false);
                if (config.auditManagerEnabled) {
                    selectComplianceFrameworks(config);
                } else {
                    config.complianceFrameworks.clear();
                }
            }
        } else {
            config.auditManagerEnabled = false;
            config.complianceFrameworks.clear();
        }

        // Compliance validation mode (cdk-nag + cfn-guard)
        if (!config.complianceFrameworks.isEmpty()) {
            System.out.println("\n🔍 Compliance Validation Mode:");
            System.out.println("==============================");
            System.out.println("Controls how cdk-nag and cfn-guard handle violations:");
            System.out.println("  • ENFORCE: Block deployment on violations (recommended for PRODUCTION)");
            System.out.println("  • ADVISORY: Log warnings only, allow deployment (recommended for DEV/STAGING)");
            System.out.println("  • DISABLED: Skip validation (not recommended)");
            System.out.println("");

            String defaultMode = config.securityProfile == SecurityProfile.PRODUCTION ? "enforce" : "advisory";
            String modeStr = promptWithValidation("Validation Mode", defaultMode,
                new String[]{"enforce", "advisory", "disabled"});
            config.complianceMode = ComplianceMode.fromString(modeStr, ComplianceMode.ADVISORY);
        } else {
            config.complianceMode = ComplianceMode.DISABLED;
        }

        // Database configuration
        configureDatabaseOptions(config);

        // Log retention based on compliance
        if (config.enableMonitoring) {
            System.out.println("\n📋 Log Retention Guidelines:");
            System.out.println("  • PCI-DSS: 365 days minimum");
            System.out.println("  • SOC2: 730 days (2 years)");
            System.out.println("  • HIPAA: 2190 days (6 years)");
            config.logRetentionDays = promptWithValidation("Log Retention (days)", "7",
                new String[]{"1", "3", "7", "14", "30", "60", "90", "120", "150", "180", "365", "730", "2190", "2555"});
        }
    }

    /**
     * DATABASE CONFIGURATION (CloudForge 3.1+ with Configuration Introspection)
     * - Automatically discovers and prompts for database fields using @ConfigField annotations
     * - Application-aware: only shows relevant fields based on ApplicationSpec capabilities
     * - Enables automated database remediation (opt-in)
     */
    private static void configureDatabaseOptions(DeploymentConfig config) {
        // Discover database configuration fields for this application
        List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverVisibleFields(
            config.applicationSpec, config, "database"
        );

        if (!databaseFields.isEmpty()) {
            System.out.println("\n💾 Database Configuration:");
            System.out.println("=========================");
            System.out.println("This application supports RDS database provisioning:");
            System.out.println("  • Embedded (H2/SQLite): FREE, single instance only");
            System.out.println("  • RDS (PostgreSQL): ~$15-30/month, supports HA/scaling");
            System.out.println("");

            // Prompt for each discovered field
            for (ConfigFieldInfo field : databaseFields) {
                promptForField(field, config);
            }
        }

        // Database remediations (only if AWS Config is enabled)
        if (config.awsConfigEnabled && !config.complianceFrameworks.isEmpty()) {
            System.out.println("\n🔧 Database Automated Remediation:");
            System.out.println("==================================");
            System.out.println("Automatically fix database compliance violations:");
            System.out.println("");

            // RDS Deletion Protection
            System.out.println("📌 RDS Deletion Protection:");
            System.out.println("  • Prevents accidental database deletion");
            System.out.println("  • Required for: HIPAA, SOC2, GDPR");
            System.out.println("  • Safety: SAFE (only enables, never disables)");
            config.enableRdsDeletionProtectionRemediation = promptYesNo(
                "Enable automatic deletion protection remediation",
                config.securityProfile == SecurityProfile.PRODUCTION);

            // RDS Auto Minor Version Upgrade
            System.out.println("\n🔄 RDS Auto Minor Version Upgrade:");
            System.out.println("  • Automatically apply security patches");
            System.out.println("  • Required for: PCI-DSS, SOC2, HIPAA, GDPR");
            System.out.println("  • Safety: SAFE (only minor versions, during maintenance window)");
            config.enableRdsAutoMinorVersionUpgradeRemediation = promptYesNo(
                "Enable automatic version upgrade remediation",
                config.securityProfile == SecurityProfile.PRODUCTION);

            // General remediations
            System.out.println("\n📦 General Automated Remediation:");
            System.out.println("==================================");

            System.out.println("S3 Versioning:");
            System.out.println("  • Automatically enable S3 bucket versioning");
            System.out.println("  • Required for: SOC2, GDPR (data protection)");
            config.enableS3VersioningRemediation = promptYesNo(
                "Enable S3 versioning remediation", false);

            System.out.println("\nCloudTrail Bucket Access Logging:");
            System.out.println("  • Automatically enable CloudTrail S3 bucket logging");
            System.out.println("  • Required for: PCI-DSS, HIPAA (audit trail)");
            config.enableCloudTrailBucketAccessRemediation = promptYesNo(
                "Enable CloudTrail bucket logging remediation", false);
        }
    }

    /**
     * Generic field prompting using Configuration Introspection metadata.
     *
     * <p>Automatically determines the correct prompt type based on field metadata:</p>
     * <ul>
     *   <li>Boolean fields → Yes/No prompt</li>
     *   <li>Fields with allowedValues → Choice prompt</li>
     *   <li>Integer/Double fields with min/max → Numeric validation</li>
     *   <li>String fields with pattern → Pattern validation</li>
     *   <li>Required fields → Required prompt</li>
     *   <li>Optional fields → Optional prompt</li>
     * </ul>
     *
     * <p>Features:</p>
     * <ul>
     *   <li>Smart defaults from ApplicationSpec via DefaultValueResolver</li>
     *   <li>Field validation using ConfigFieldInfo.validate()</li>
     *   <li>Tag-based warnings (DESTRUCTIVE, BILLING_IMPACT, IMMUTABLE)</li>
     * </ul>
     */
    private static void promptForField(ConfigFieldInfo field, DeploymentConfig config) {
        // Get smart default from ApplicationSpec or FrameworkRules
        Object defaultValue = DefaultValueResolver.resolveWithFallback(
            field, config.applicationSpec, null, config
        );

        // Show field description
        if (!field.description().isEmpty()) {
            System.out.println("\n" + field.description());
        }

        // Show warnings for tagged fields
        if (field.hasTag(FieldTag.DESTRUCTIVE)) {
            System.out.println("⚠️  WARNING: Changing this may require resource replacement (potential data loss)");
        }
        if (field.hasTag(FieldTag.BILLING_IMPACT)) {
            System.out.println("💰 This setting impacts AWS costs");
        }
        if (field.hasTag(FieldTag.IMMUTABLE)) {
            System.out.println("🔒 Cannot be changed after creation");
        }

        // Prompt based on field type
        Object value;
        if (field.type() == boolean.class || field.type() == Boolean.class) {
            // Boolean field → Yes/No prompt
            boolean defaultBool = defaultValue instanceof Boolean ? (Boolean) defaultValue : false;
            value = promptYesNo(field.displayName(), defaultBool);
        } else if (field.allowedValues().length > 0) {
            // Enum-like field → Choice prompt
            String defaultStr = defaultValue != null ? defaultValue.toString() : field.allowedValues()[0];
            String choiceStr = promptChoice(field.displayName(), field.allowedValues(), defaultStr);

            // Convert to appropriate type based on field type
            if (field.type() == int.class || field.type() == Integer.class) {
                value = Integer.parseInt(choiceStr);
            } else if (field.type() == long.class || field.type() == Long.class) {
                value = Long.parseLong(choiceStr);
            } else if (field.type() == double.class || field.type() == Double.class) {
                value = Double.parseDouble(choiceStr);
            } else {
                value = choiceStr;
            }
        } else if (field.type() == int.class || field.type() == Integer.class) {
            // Integer field → Numeric validation
            int defaultInt = defaultValue instanceof Number ? ((Number) defaultValue).intValue() : 0;
            int min = (int) field.min();
            int max = (int) field.max();

            if (min != Integer.MIN_VALUE || max != Integer.MAX_VALUE) {
                value = promptIntWithValidation(field.displayName(), defaultInt, min, max);
            } else {
                value = promptIntWithValidation(field.displayName(), defaultInt, Integer.MIN_VALUE, Integer.MAX_VALUE);
            }
        } else if (field.type() == String.class) {
            // String field
            String defaultStr = defaultValue != null ? defaultValue.toString() : "";

            if (field.required()) {
                value = promptRequired(field.displayName(), defaultStr);
            } else {
                value = promptOptional(field.displayName(), defaultStr);
            }
        } else {
            // Unsupported type - skip
            LOG.warning("Unsupported field type for introspection: " + field.type().getSimpleName());
            return;
        }

        // Validate the value
        ValidationResult validation = field.validate(value, config);
        if (validation.isError()) {
            System.out.println("❌ Validation failed: " + validation.getMessage());
            System.out.println("   Using default value instead");
            value = defaultValue;
        }

        // Set the value using reflection
        field.setValue(config, value);
    }

    private static void selectComplianceFrameworks(DeploymentConfig config) {
        System.out.println("\n📋 Select Compliance Frameworks:");
        System.out.println("================================");
        System.out.println("  1. All Standard Frameworks (PCI-DSS, HIPAA, SOC2, GDPR)");
        System.out.println("  2. SOC 2 Only (SaaS applications)");
        System.out.println("  3. HIPAA Only (Healthcare)");
        System.out.println("  4. PCI-DSS Only (Payment Processing)");
        System.out.println("  5. GDPR Only (Data Protection)");
        System.out.println("  6. Healthcare Focused (HIPAA + SOC2 + GDPR)");
        System.out.println("  7. Payment Processing (PCI-DSS + SOC2)");
        System.out.println("  8. Custom (comma-separated list)");
        System.out.println("");

        String choice = promptWithValidation("Framework(s)", "1",
            new String[]{"1", "2", "3", "4", "5", "6", "7", "8"});

        String frameworksStr = switch (choice) {
            case "1" -> "pci-dss,hipaa,soc2,gdpr";
            case "2" -> "soc2";
            case "3" -> "hipaa";
            case "4" -> "pci-dss";
            case "5" -> "gdpr";
            case "6" -> "hipaa,soc2,gdpr";
            case "7" -> "pci-dss,soc2";
            case "8" -> promptOptional("Frameworks (comma-separated)", "soc2");
            default -> "soc2";
        };
        config.complianceFrameworks = new ArrayList<>(ComplianceFrameworkType.parseCommaSeparated(frameworksStr));

        System.out.println("\n✅ Selected frameworks: " + config.getComplianceFrameworksAsString());
    }

    private static void configureAdvancedSettings(DeploymentConfig config) {
        System.out.println("\n⚙️  Advanced Configuration:");
        System.out.println("==========================");

        // Optional Ports Configuration - Only show if application has optional ports
        configureOptionalPorts(config);

        // Region Configuration
        System.out.println("\n🌍 Region Configuration:");
        config.region = promptChoice("AWS Region",
            new String[]{"us-east-1", "us-west-2", "us-east-2", "us-west-1", "eu-west-1", "eu-central-1",
                        "ap-southeast-1", "ap-northeast-1"}, "us-east-1");

        // Multi-AZ Configuration (truth table: recommended for PRODUCTION)
        boolean enableMultiAz = promptYesNo("Enable Multi-AZ deployment",
            config.securityProfile == SecurityProfile.PRODUCTION);

        if (enableMultiAz) {
            config.availabilityZones = getAvailabilityZonesForRegion(config.region, 2);
            System.out.println("✅ Multi-AZ enabled: " + String.join(", ", config.availabilityZones));
        } else {
            String defaultAz = config.region + "a";
            config.availabilityZones = new String[]{defaultAz};
            System.out.println("ℹ️  Single-AZ deployment: " + defaultAz);
        }
    }

    /**
     * Configure optional ports for applications that support them.
     *
     * <p>Optional ports are NOT exposed by default - users must explicitly enable them.
     * This follows security-conscious design where unused ports stay closed.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Jenkins: JNLP agents port (50000)</li>
     *   <li>GitLab: SSH (22), Container Registry (5050), Metrics (9090)</li>
     *   <li>Mattermost: SMTP (587/465), Clustering (8074-8075)</li>
     *   <li>Redis: Sentinel (26379), Cluster Bus (16379)</li>
     * </ul>
     */
    private static void configureOptionalPorts(DeploymentConfig config) {
        if (config.applicationSpec == null) {
            return;
        }

        List<ApplicationSpec.OptionalPort> optionalPorts = config.applicationSpec.optionalPorts();
        if (optionalPorts == null || optionalPorts.isEmpty()) {
            return;
        }

        System.out.println("\n🔌 Optional Ports Configuration:");
        System.out.println("=================================");
        System.out.println("The following optional service ports can be enabled for " + config.applicationName + ".");
        System.out.println("⚠️  Ports are NOT exposed by default for security. Only enable what you need.");
        System.out.println("");

        // Group ports by configKey to avoid duplicate prompts
        Map<String, List<ApplicationSpec.OptionalPort>> portsByConfigKey = new java.util.LinkedHashMap<>();
        for (ApplicationSpec.OptionalPort port : optionalPorts) {
            portsByConfigKey.computeIfAbsent(port.configKey(), k -> new java.util.ArrayList<>()).add(port);
        }

        for (Map.Entry<String, List<ApplicationSpec.OptionalPort>> entry : portsByConfigKey.entrySet()) {
            String configKey = entry.getKey();
            List<ApplicationSpec.OptionalPort> ports = entry.getValue();

            // Build description from all ports with this config key
            StringBuilder description = new StringBuilder();
            for (int i = 0; i < ports.size(); i++) {
                ApplicationSpec.OptionalPort port = ports.get(i);
                if (i > 0) description.append(", ");
                description.append(port.service())
                          .append(" (")
                          .append(port.port())
                          .append("/")
                          .append(port.protocol())
                          .append(port.inbound() ? " inbound" : " outbound")
                          .append(")");
            }

            // Use first port's service name for the prompt
            String promptLabel = "Enable " + ports.get(0).service();
            boolean enabled = promptYesNo(promptLabel + " - " + description, false);

            // Set the appropriate config field based on configKey
            switch (configKey) {
                case "enableAgents" -> config.enableAgents = enabled;
                case "enableSsh" -> config.enableSsh = enabled;
                case "enableSmtp" -> config.enableSmtp = enabled;
                case "enableSmtps" -> config.enableSmtps = enabled;
                case "enableClustering" -> config.enableClustering = enabled;
                case "enableDockerRegistry" -> config.enableDockerRegistry = enabled;
                case "enableMetrics" -> config.enableMetrics = enabled;
                case "enableNotary" -> config.enableNotary = enabled;
                case "enableTrivy" -> config.enableTrivy = enabled;
                case "enableSentinel" -> config.enableSentinel = enabled;
                case "enableCluster" -> config.enableCluster = enabled;
                default -> LOG.warning("Unknown optional port config key: " + configKey);
            }

            if (enabled) {
                System.out.println("  ✅ " + description + " will be exposed");
            }
        }
    }

    /**
     * Interactive application selection with categorical browsing.
     */
    private static ApplicationInfo selectApplication() {
        System.out.println("Choose how to select your application:");
        System.out.println("  1. Browse by category (recommended)");
        System.out.println("  2. View all applications");
        System.out.println("");

        String selectionMethod = promptChoice("Selection method", new String[]{"category", "all"}, "category");

        if (selectionMethod.equals("category")) {
            return selectApplicationByCategory();
        } else {
            return selectFromAllApplications();
        }
    }

    private static ApplicationInfo selectApplicationByCategory() {
        System.out.println("\n📂 Select Category:");
        System.out.println("===================");

        // Dynamically list all categories from discovered applications
        List<String> categoryKeys = new ArrayList<>(APPLICATION_CATEGORIES.keySet());
        java.util.Collections.sort(categoryKeys);

        int num = 1;
        for (String key : categoryKeys) {
            List<ApplicationInfo> apps = APPLICATION_CATEGORIES.get(key);
            String appNames = apps.stream()
                .limit(3)
                .map(a -> a.name)
                .collect(java.util.stream.Collectors.joining(", "));
            if (apps.size() > 3) {
                appNames += ", ...";
            }
            String defaultMarker = (num == 1) ? " (default)" : "";
            System.out.println("  " + num + ". " + getCategoryName(key) + " (" + appNames + ")" + defaultMarker);
            num++;
        }
        System.out.println("");

        // Handle category selection directly (don't use promptChoice which prints its own list)
        int categoryIndex = promptNumberChoice("Category", categoryKeys.size(), 1) - 1;
        String categoryKey = categoryKeys.get(categoryIndex);

        List<ApplicationInfo> apps = APPLICATION_CATEGORIES.get(categoryKey);

        System.out.println("\n📦 Applications in " + getCategoryName(categoryKey) + ":");
        System.out.println("=".repeat(50));
        for (int i = 0; i < apps.size(); i++) {
            ApplicationInfo app = apps.get(i);
            String oidcBadge = app.supportsOidc ? " [OIDC ✓]" : "";
            System.out.println("  " + (i + 1) + ". " + app.name + " - " + app.description + oidcBadge);
        }
        System.out.println("");

        int appIndex;
        if (apps.size() == 1) {
            appIndex = 0;
            System.out.println("✅ Selected: " + apps.get(0).name);
        } else {
            appIndex = promptNumberChoice("Application", apps.size(), 1) - 1;
        }

        ApplicationInfo selected = apps.get(appIndex);
        printApplicationDetails(selected);
        return selected;
    }

    private static ApplicationInfo selectFromAllApplications() {
        System.out.println("\n📦 All Available Applications:");
        System.out.println("==============================");

        List<ApplicationInfo> allApps = new ArrayList<>();
        int index = 1;

        for (Map.Entry<String, List<ApplicationInfo>> entry : APPLICATION_CATEGORIES.entrySet()) {
            String category = getCategoryName(entry.getKey());
            for (ApplicationInfo app : entry.getValue()) {
                allApps.add(app);
                String oidcBadge = app.supportsOidc ? " [OIDC ✓]" : "";
                System.out.println("  " + index + ". " + app.name + " - " + app.description +
                    " [" + category + "]" + oidcBadge);
                index++;
            }
        }
        System.out.println("");

        int appIndex = promptNumberChoice("Application", allApps.size(), 1) - 1;
        ApplicationInfo selected = allApps.get(appIndex);
        printApplicationDetails(selected);
        return selected;
    }

    private static void printApplicationDetails(ApplicationInfo app) {
        System.out.println("\n✅ Selected: " + app.name);
        System.out.println("   " + app.description);
        System.out.println("   Supports Fargate: " + (app.supportsFargate ? "✓" : "✗"));
        System.out.println("   Supports EC2: " + (app.supportsEc2 ? "✓" : "✗"));
        System.out.println("   Supports OIDC: " + (app.supportsOidc ? "✓ Yes - Authentication can be configured" : "✗ No"));

        // Display resource requirements
        if (app.supportsFargate && app.minCpu > 0) {
            System.out.println("\n   💻 Minimum Requirements (Fargate):");
            System.out.println("      CPU: " + app.minCpu + " units");
            System.out.println("      Memory: " + app.minMemory + " MB");
        }
        if (app.supportsEc2 && app.minInstanceType != null && !app.minInstanceType.isEmpty()) {
            System.out.println("\n   💻 Minimum Requirements (EC2):");
            System.out.println("      Instance Type: " + app.minInstanceType + " or larger");
        }
        System.out.println("");
    }

    private static String getCategoryName(String key) {
        return switch (key) {
            case "cicd" -> "CI/CD";
            case "vcs" -> "Version Control";
            case "monitoring" -> "Monitoring";
            case "database" -> "Databases";
            case "secrets" -> "Secrets Management";
            case "artifactregistry" -> "Artifact Registry";
            case "collaboration" -> "Collaboration";
            case "analytics" -> "Analytics";
            // CMS categories from @CmsPlugin
            case "cms" -> "Content Management";
            case "ecommerce" -> "E-commerce";
            case "forum" -> "Forum Software";
            case "wiki" -> "Wiki Platforms";
            case "lms" -> "Learning Management";
            case "social" -> "Social Networking";
            case "crm" -> "CRM Platforms";
            default -> key; // Return raw key instead of "Unknown"
        };
    }

    // ============================================================================
    // DEPLOYMENT LOGIC
    // ============================================================================

    /**
     * Shows a comprehensive configuration summary before deployment.
     *
     * <p>Displays all configured values grouped by category, highlighting:</p>
     * <ul>
     *   <li>Application and runtime configuration</li>
     *   <li>Network and domain settings</li>
     *   <li>Resource allocation</li>
     *   <li>Security and compliance settings</li>
     *   <li>Database configuration (if applicable)</li>
     *   <li>Fields with BILLING_IMPACT or DESTRUCTIVE tags</li>
     * </ul>
     */
    private static void showConfigurationSummary(DeploymentConfig config) {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║            📋 Configuration Summary                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Get application friendly name
        String appDisplayName = config.applicationId;
        for (List<ApplicationInfo> apps : APPLICATION_CATEGORIES.values()) {
            for (ApplicationInfo app : apps) {
                if (app.id.equals(config.applicationId)) {
                    appDisplayName = app.name;
                    break;
                }
            }
        }

        // Basic Configuration
        System.out.println("🎯 Application & Environment");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Application:        " + appDisplayName + " (" + config.applicationId + ")");
        System.out.println("  Stack Name:         " + config.stackName);
        System.out.println("  Environment:        " + config.environment);
        System.out.println("  Runtime:            " + config.runtime);
        System.out.println("  Security Profile:   " + config.securityProfile);
        System.out.println();

        // Domain Configuration
        if (config.domain != null && !config.domain.isEmpty()) {
            System.out.println("🌐 Domain & SSL");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("  Domain:             " + config.domain);
            if (config.subdomain != null && !config.subdomain.isEmpty()) {
                System.out.println("  Subdomain:          " + config.subdomain);
                System.out.println("  FQDN:               " + config.subdomain + "." + config.domain);
            } else {
                System.out.println("  FQDN:               " + config.domain);
            }
            System.out.println("  SSL Enabled:        " + (config.enableSsl ? "✓ Yes" : "✗ No"));
            System.out.println();
        }

        // OIDC/SAML Authentication
        if (config.applicationSpec != null && config.applicationSpec.supportsOidcIntegration() &&
            config.oidcProvider != null && !config.oidcProvider.equals("none")) {
            System.out.println("🔐 Authentication");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("  Provider:           " + config.oidcProvider);
            System.out.println("  Auth Mode:          " + config.authMode);
            if (config.oidcProvider.equals("cognito") && config.cognitoAutoProvision) {
                System.out.println("  Cognito:            Auto-provisioning (OIDC)");
                System.out.println("  Domain Prefix:      " + config.cognitoDomainPrefix);
                System.out.println("  MFA Enabled:        " + (config.cognitoMfaEnabled ? "✓ Yes" : "✗ No"));
                if (config.cognitoInitialAdminEmail != null && !config.cognitoInitialAdminEmail.isEmpty()) {
                    System.out.println("  Initial Admin:      " + config.cognitoInitialAdminEmail);
                }
            }
            if (config.oidcProvider.equals("cognito-saml") && config.cognitoAutoProvision) {
                System.out.println("  Cognito:            Auto-provisioning (SAML IdP)");
                System.out.println("  Domain Prefix:      " + config.cognitoDomainPrefix);
                System.out.println("  SAML Enabled:       ✓ Yes (group sync supported)");
                System.out.println("  MFA Enabled:        " + (config.cognitoMfaEnabled ? "✓ Yes" : "✗ No"));
                if (config.cognitoInitialAdminEmail != null && !config.cognitoInitialAdminEmail.isEmpty()) {
                    System.out.println("  Initial Admin:      " + config.cognitoInitialAdminEmail);
                }
            }
            System.out.println();
        }

        // Resource Configuration
        System.out.println("💻 Resources & Scaling");
        System.out.println("═══════════════════════════════════════════════════════════════");
        if (config.runtime == RuntimeType.EC2) {
            System.out.println("  Instance Type:      " + config.instanceType + " 💰");
        } else {
            System.out.println("  CPU:                " + config.cpu + " units");
            System.out.println("  Memory:             " + config.memory + " MB");
        }
        System.out.println("  Min Capacity:       " + config.minInstanceCapacity);
        System.out.println("  Max Capacity:       " + config.maxInstanceCapacity);
        System.out.println("  Auto Scaling:       " + (config.enableAutoScaling ? "✓ Enabled" : "✗ Disabled"));
        System.out.println();

        // Network Configuration
        System.out.println("🌐 Network & Security");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Network Mode:       " + config.networkMode);
        System.out.println("  WAF Protection:     " + (config.wafEnabled ? "✓ Enabled" : "✗ Disabled"));
        System.out.println("  Region:             " + config.region);
        System.out.println();

        // Database Configuration (using introspection)
        List<ConfigFieldInfo> databaseFields = ConfigurationIntrospector.discoverFields(
            config.applicationSpec, "database"
        );
        if (!databaseFields.isEmpty() && config.provisionDatabase) {
            System.out.println("💾 Database Configuration");
            System.out.println("═══════════════════════════════════════════════════════════════");
            for (ConfigFieldInfo field : databaseFields) {
                Object value = field.getValue(config);
                if (value != null) {
                    String displayValue = value.toString();
                    String warning = "";

                    if (field.hasTag(FieldTag.BILLING_IMPACT)) {
                        warning = " 💰";
                    }
                    if (field.hasTag(FieldTag.DESTRUCTIVE)) {
                        warning = " ⚠️";
                    }
                    if (field.hasTag(FieldTag.IMMUTABLE)) {
                        warning = " 🔒";
                    }

                    System.out.printf("  %-20s %s%s%n", field.displayName() + ":", displayValue, warning);
                }
            }
            System.out.println();
        }

        // Compliance Configuration
        System.out.println("🔧 Compliance & Monitoring");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Monitoring:         " + (config.enableMonitoring ? "✓ Enabled" : "✗ Disabled"));
        System.out.println("  Encryption:         " + (config.enableEncryption ? "✓ Enabled" : "✗ Disabled"));
        System.out.println("  AWS Config:         " + (config.awsConfigEnabled ? "✓ Enabled" : "✗ Disabled"));
        System.out.println("  GuardDuty:          " + (config.guardDutyEnabled ? "✓ Enabled 💰" : "✗ Disabled"));
        System.out.println("  Audit Manager:      " + (config.auditManagerEnabled ? "✓ Enabled" : "✗ Disabled"));
        if (config.auditManagerEnabled && config.complianceFrameworks != null && !config.complianceFrameworks.isEmpty()) {
            System.out.println("  Frameworks:         " + config.complianceFrameworks);
        }
        if (config.enableMonitoring) {
            System.out.println("  Log Retention:      " + config.logRetentionDays + " days");
        }
        System.out.println();

        // Cost Impact Warnings
        List<String> costImpactItems = new ArrayList<>();
        if (config.runtime == RuntimeType.EC2) {
            costImpactItems.add("EC2 Instance: " + config.instanceType);
        } else {
            costImpactItems.add("Fargate: " + config.cpu + " CPU / " + config.memory + " MB");
        }
        if (config.guardDutyEnabled) {
            costImpactItems.add("GuardDuty (~$30-100/month)");
        }
        if (config.provisionDatabase) {
            costImpactItems.add("RDS Database (~$15-100/month)");
        }

        if (!costImpactItems.isEmpty()) {
            System.out.println("💰 Cost Impact Summary");
            System.out.println("═══════════════════════════════════════════════════════════════");
            for (String item : costImpactItems) {
                System.out.println("  • " + item);
            }
            System.out.println();
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    private static void deployInfrastructure(DeploymentConfig config, String deploymentOption) {
        deployInfrastructure(config, deploymentOption, true);
    }

    private static void deployInfrastructure(DeploymentConfig config, String deploymentOption, boolean saveContext) {
        LOG.info("Building CDK Context...");

        Map<String, Object> cfcContext = buildCfcContext(config);

        LOG.fine("CDK Context: runtime=" + cfcContext.get("runtime") +
                 ", topology=" + cfcContext.get("topology") +
                 ", stackName=" + cfcContext.get("stackName"));

        // Show comprehensive configuration summary
        showConfigurationSummary(config);

        System.out.println("\n🚀 Deployment Options:");
        System.out.println("========================");
        System.out.println("1. Synthesize only");
        System.out.println("2. Deploy");
        System.out.println("3. Redeploy (delete + deploy)");
        System.out.println("4. Dry-run (changeset)");
        System.out.println("5. Export Template (YAML/JSON)");
        System.out.println("6. Reconfigure (start fresh interactive setup)");
        System.out.println("7. Cancel");

        String choice;
        if (deploymentOption != null && !deploymentOption.trim().isEmpty()) {
            choice = deploymentOption.trim();
            System.out.println("Using deployment option from command line: " + choice);
        } else {
            // Read from terminal
            try {
                java.io.BufferedReader ttyReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/dev/tty")));
                System.out.print("Choose option [1-7]: ");
                System.out.flush();
                choice = ttyReader.readLine();
                if (choice == null || choice.trim().isEmpty()) {
                    choice = "1";
                } else {
                    choice = choice.trim();
                }
            } catch (Exception e) {
                System.out.println("\nInput error, defaulting to synthesis only: " + e.getMessage());
                choice = "1";
            }
        }

        switch (choice) {
            case "1":
                System.out.println("\n🚀 Starting CDK Synthesis...");
                break;
            case "2":
                System.out.println("\n🚀 Starting CDK Deployment...");
                break;
            case "3":
                System.out.println("\n🗑️  Deleting existing stack...");
                deleteExistingStack(config.stackName);
                System.out.println("🚀 Starting fresh deployment...");
                break;
            case "4":
                System.out.println("\n🔍 Starting Dry-Run Deployment...");
                break;
            case "5":
                System.out.println("\n📄 Exporting CloudFormation Template...");
                break;
            case "6":
                // Reconfigure - start fresh interactive setup
                System.out.println("\n🔄 Starting fresh configuration...\n");
                DeploymentConfig newConfig = collectConfiguration(null);
                deployInfrastructure(newConfig, null);
                return;
            case "7":
                System.out.println("❌ Deployment cancelled by user");
                return;
            default:
                System.out.println("Invalid choice. Defaulting to synthesis only.");
                System.out.println("\n🚀 Starting CDK Synthesis...");
        }

        App app = new App();
        app.getNode().setContext("cfc", cfcContext);

        // Only save context if this is from interactive configuration, not when loaded from file
        if (saveContext) {
            saveContextToFile(cfcContext, config.stackName);
        }

        DeploymentContext cfc = DeploymentContext.from(app);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(config.securityProfile);

        String region = cfc.region() != null ? cfc.region() :
                        System.getenv("CDK_DEFAULT_REGION") != null ? System.getenv("CDK_DEFAULT_REGION") : "us-east-1";
        String account = System.getenv("CDK_DEFAULT_ACCOUNT") != null ? System.getenv("CDK_DEFAULT_ACCOUNT") : "123456789012";

        StackProps props = StackProps.builder()
            .env(Environment.builder()
                .account(account)
                .region(region)
                .build())
            .build();

        // Create stack based on runtime type using universal Application stacks
        // Get the application spec from config
        ApplicationSpec appSpec = config.applicationSpec;

        if (appSpec == null) {
            System.err.println("\n❌ ERROR: ApplicationSpec is null for application: " + config.applicationId);
            return;
        }

        // Use universal stacks for all applications
        if (config.runtime == RuntimeType.EC2) {
            new ApplicationEc2Stack(app, config.stackName, props, config.securityProfile, iamProfile, appSpec);
        } else {
            new ApplicationFargateStack(app, config.stackName, props, config.securityProfile, iamProfile, appSpec);
        }

        // Add CDK-NAG suppressions for PRODUCTION security profile (Option A: Enforce with documented exceptions)
        if (config.securityProfile == SecurityProfile.PRODUCTION) {
            applyProductionNagSuppressions(app, config);
        }

        // Apply cdk-nag validation based on complianceMode (Layer 1: construct-level compliance checks)
        ComplianceMode complianceMode = config.complianceMode != null
            ? config.complianceMode
            : ComplianceMode.defaultForProfile(config.securityProfile);

        // Only apply CDK Nag if compliance frameworks are specified
        // CDK Nag (Layer 1) runs independently of Layer 2 (FrameworkRules) and Layer 4 (AWS Config)
        boolean hasComplianceFrameworks = config.hasAnyComplianceFramework();

        if (complianceMode != ComplianceMode.DISABLED && hasComplianceFrameworks) {
            System.out.println("\n🔍 Applying cdk-nag validation for compliance frameworks...");
            System.out.println("   Mode: " + complianceMode);

            // Iterate over enabled frameworks
            int appliedCount = 0;

            for (ComplianceFrameworkType framework : config.complianceFrameworks) {
                NagPack pack = mapFrameworkToNagPack(framework.getMatrixKey(), complianceMode);

                if (pack != null) {
                    Aspects.of(app).add(pack);
                    appliedCount++;
                    System.out.println("   ✓ Applied cdk-nag pack for " + framework.getMatrixKey());
                }
            }

            if (appliedCount > 0) {
                if (complianceMode == ComplianceMode.ADVISORY) {
                    System.out.println("   ⚠️  Violations will be logged as warnings only");
                } else if (complianceMode == ComplianceMode.ENFORCE) {
                    System.out.println("   🚫 Violations will block deployment");
                }
                System.out.println("   Applied " + appliedCount + " cdk-nag validation pack(s)");
            } else {
                System.out.println("   ⚠️  No framework-specific cdk-nag packs available");
            }
        } else {
            if (complianceMode == ComplianceMode.DISABLED) {
                System.out.println("\n⏭️  Skipping cdk-nag validation (complianceMode disabled)");
            } else {
                System.out.println("\n⏭️  Skipping cdk-nag validation (no compliance frameworks enabled)");
            }
        }

        // Execute deployment based on choice
        executeDeployment(app, config, choice);
    }

    private static void executeDeployment(App app, DeploymentConfig config, String choice) {
        // Use compliance mode from config, defaulting by profile if null
        ComplianceMode complianceMode = config.complianceMode != null
            ? config.complianceMode
            : ComplianceMode.defaultForProfile(config.securityProfile);

        // Synthesize the stack
        System.out.println("\n✅ CDK Stack synthesized successfully!");
        app.synth();

        // Run cfn-guard validation if enforce mode is enabled
        if (complianceMode == ComplianceMode.ENFORCE && !config.complianceFrameworks.isEmpty()) {
            boolean guardPassed = runCfnGuardValidation(config);
            if (!guardPassed) {
                System.out.println("\n❌ cfn-guard validation FAILED!");
                System.out.println("   Fix the violations or switch to ADVISORY mode to proceed.");
                return;
            }
        }

        // Execute deployment based on choice
        switch (choice) {
            case "2" -> {
                runCdkDeploy();
            }
            case "3" -> {
                System.out.println("\n🗑️  Deleting existing stack...");
                runCdkDestroy(config.stackName);
                System.out.println("\n🚀 Starting CDK deployment...");
                runCdkDeploy("--require-approval", "never");
            }
            case "4" -> {
                System.out.println("\n📋 Synthesis complete. To create changeset (dry-run), run:");
                System.out.println("   cdk deploy --no-execute --require-approval never");
            }
            case "5" -> {
                exportTemplate(config.stackName);
            }
            default -> {
                System.out.println("\n📋 Synthesis complete. To deploy, run:");
                System.out.println("   cdk deploy");
            }
        }
    }

    private static void exportTemplate(String stackName) {
        try {
            // Read the synthesized JSON template from cdk.out
            java.nio.file.Path templatePath = java.nio.file.Paths.get("cdk.out", stackName + ".template.json");
            if (!java.nio.file.Files.exists(templatePath)) {
                System.out.println("❌ Template not found: " + templatePath);
                return;
            }

            String jsonContent = java.nio.file.Files.readString(templatePath);

            // Ask user for format
            System.out.println("\nExport format:");
            System.out.println("  1. JSON");
            System.out.println("  2. YAML");
            System.out.print("Choose format [1-2]: ");
            System.out.flush();

            String formatChoice = "1";
            try {
                java.io.BufferedReader ttyReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/dev/tty")));
                String input = ttyReader.readLine();
                if (input != null && !input.trim().isEmpty()) {
                    formatChoice = input.trim();
                }
            } catch (Exception e) {
                // Default to JSON
            }

            String extension = formatChoice.equals("2") ? ".yaml" : ".json";
            String outputFileName = stackName + "-template" + extension;

            if (formatChoice.equals("2")) {
                // Convert JSON to YAML using Jackson
                com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Object jsonObj = jsonMapper.readValue(jsonContent, Object.class);

                com.fasterxml.jackson.dataformat.yaml.YAMLFactory yamlFactory = new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()
                    .disable(com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
                com.fasterxml.jackson.databind.ObjectMapper yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(yamlFactory);
                yamlMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

                String yamlContent = yamlMapper.writeValueAsString(jsonObj);
                java.nio.file.Files.writeString(java.nio.file.Paths.get(outputFileName), yamlContent);
            } else {
                // Pretty print JSON
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                Object jsonObj = mapper.readValue(jsonContent, Object.class);
                String prettyJson = mapper.writeValueAsString(jsonObj);
                java.nio.file.Files.writeString(java.nio.file.Paths.get(outputFileName), prettyJson);
            }

            System.out.println("✅ Template exported to: " + outputFileName);

        } catch (Exception e) {
            System.out.println("❌ Error exporting template: " + e.getMessage());
        }
    }

    private static void runCdkDeploy(String... extraArgs) {
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add("cdk");
            cmd.add("deploy");
            cmd.add("--output");
            cmd.add("cdk.out.deploy");
            for (String arg : extraArgs) {
                cmd.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            pb.environment().put("CFC_DEPLOYING", "true");
            Process proc = pb.start();
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                System.out.println("\n✅ Deployment completed successfully!");
            } else {
                System.out.println("\n❌ Deployment failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.out.println("\n❌ Error during deployment: " + e.getMessage());
        }
    }

    private static void runCdkDestroy(String stackName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cdk", "destroy", "--force", stackName);
            pb.inheritIO();
            pb.environment().put("CFC_DEPLOYING", "true");
            Process proc = pb.start();
            int exitCode = proc.waitFor();

            if (exitCode != 0) {
                System.out.println("⚠️  Stack deletion returned exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.out.println("❌ Error during stack deletion: " + e.getMessage());
        }
    }

    /**
     * Run cfn-guard validation on synthesized CloudFormation templates.
     * Returns true if validation passes, false if it fails.
     *
     * <p>Validation runs in two phases:</p>
     * <ol>
     *   <li><b>Cross-framework rules</b> - Security best practices that apply to ALL deployments
     *       (IAM, compute, Lambda, CDN/API, ELB, database, messaging, monitoring, etc.)</li>
     *   <li><b>Framework-specific rules</b> - Rules specific to selected compliance frameworks
     *       (SOC2, PCI-DSS, HIPAA, GDPR, ISO 27001)</li>
     * </ol>
     */
    private static boolean runCfnGuardValidation(DeploymentConfig config) {
        System.out.println("\n🛡️  Running cfn-guard validation (Layer 3)...");
        System.out.println("   Frameworks: " + config.complianceFrameworks);

        try {
            // Find the synthesized CloudFormation template
            String templatePath = "cdk.out/" + config.stackName + ".template.json";
            if (!Files.exists(Paths.get(templatePath))) {
                System.out.println("⚠️  Template not found at: " + templatePath);
                System.out.println("   Skipping cfn-guard validation");
                return true;
            }

            boolean allPassed = true;

            // ================================================================
            // Phase 1: Cross-framework security rules (always applied)
            // Guard files are loaded from classpath (cloudforge-api resources)
            // ================================================================
            String[] crossFrameworkRules = {
                "iam-security.guard",           // IAM policy least privilege, trust policies
                "compute-security.guard",       // EC2, EKS, VPC subnet security
                "lambda-security.guard",        // Lambda runtime, VPC, code signing
                "cdn-api-security.guard",       // CloudFront, API Gateway, WAF
                "elb-security.guard",           // ALB/NLB, Classic ELB security
                "database-security.guard",      // RDS, DynamoDB, Redshift, DAX, ElastiCache
                "messaging-security.guard",     // SQS, SNS, Secrets Manager, Kinesis
                "key-management.guard",         // KMS, encryption at rest/transit
                "advanced-monitoring.guard",    // CloudWatch, CloudTrail, VPC Flow Logs
                "threat-protection.guard",      // GuardDuty, WAF, security groups
                "incident-response.guard",      // Forensics, backup, evidence preservation
                "iso-27001-controls.guard"      // ISO 27001 Annex A controls
            };

            System.out.println("\n   Phase 1: Cross-framework security rules...");
            for (String ruleFile : crossFrameworkRules) {
                String guardFile = resolveGuardFile(ruleFile);
                if (guardFile == null) {
                    continue; // Skip missing rule files silently
                }

                boolean passed = runGuardValidation(guardFile, templatePath, ruleFile.replace(".guard", ""));
                if (!passed) {
                    allPassed = false;
                }
            }

            // ================================================================
            // Phase 2: Framework-specific rules (based on selected frameworks)
            // ================================================================
            if (!config.complianceFrameworks.isEmpty()) {
                System.out.println("\n   Phase 2: Framework-specific rules...");

                for (ComplianceFrameworkType framework : config.complianceFrameworks) {
                    // Map framework to guard rule file name
                    String ruleFileName = switch (framework) {
                        case SOC2 -> "soc2-trust-services.guard";
                        case PCI_DSS -> "pci-dss-v4.0.1.guard";
                        case HIPAA -> "hipaa-security-rule.guard";
                        case GDPR -> "gdpr-data-protection.guard";
                    };

                    String guardFile = resolveGuardFile(ruleFileName);
                    if (guardFile == null) {
                        System.out.println("   ⚠️  No guard rules found for: " + framework.getJsonValue());
                        continue;
                    }

                    boolean passed = runGuardValidation(guardFile, templatePath, framework.getMatrixKey());
                    if (!passed) {
                        allPassed = false;
                    }
                }
            }

            return allPassed;

        } catch (Exception e) {
            System.out.println("⚠️  cfn-guard validation error: " + e.getMessage());
            System.out.println("   Proceeding without cfn-guard validation");
            return true; // Don't block on cfn-guard errors
        }
    }

    /**
     * Run cfn-guard validate for a single rule file.
     * Returns true if validation passes, false if it fails.
     */
    private static boolean runGuardValidation(String guardFile, String templatePath, String ruleName) {
        try {
            ProcessBuilder guardProcess = new ProcessBuilder(
                "cfn-guard", "validate",
                "--rules", guardFile,
                "--data", templatePath,
                "--show-summary", "fail"
            );
            guardProcess.inheritIO();
            Process guardProc = guardProcess.start();
            int exitCode = guardProc.waitFor();

            if (exitCode != 0) {
                System.out.println("   ❌ " + ruleName + " validation FAILED");
                return false;
            } else {
                System.out.println("   ✅ " + ruleName + " validation PASSED");
                return true;
            }
        } catch (Exception e) {
            System.out.println("   ⚠️  " + ruleName + " validation error: " + e.getMessage());
            return true; // Don't block on individual rule errors
        }
    }

    /**
     * Resolve a guard rule file from the classpath (cloudforge-api resources).
     *
     * <p>Guard files are packaged as resources in cloudforge-api at /cfn-guard/frameworks/.
     * This method finds them via the classpath and returns a filesystem path for cfn-guard CLI.</p>
     *
     * @param ruleFileName The guard rule file name (e.g., "soc2-trust-services.guard")
     * @return The filesystem path to the guard file, or null if not found
     */
    private static String resolveGuardFile(String ruleFileName) {
        String resourcePath = "/cfn-guard/frameworks/" + ruleFileName;

        try {
            // Try to find resource on classpath (from cloudforge-api dependency)
            URL resourceUrl = InteractiveDeployer.class.getResource(resourcePath);
            if (resourceUrl == null) {
                return null;
            }

            // If it's a file:// URL (IDE/exploded classes), use the path directly
            if ("file".equals(resourceUrl.getProtocol())) {
                return Paths.get(resourceUrl.toURI()).toString();
            }

            // If it's a jar:// URL, extract to temp file for cfn-guard CLI
            if ("jar".equals(resourceUrl.getProtocol())) {
                Path tempFile = Files.createTempFile("cfn-guard-", "-" + ruleFileName);
                tempFile.toFile().deleteOnExit();

                try (InputStream is = InteractiveDeployer.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        return tempFile.toString();
                    }
                }
            }

            return null;
        } catch (IOException | URISyntaxException e) {
            LOG.warning("Failed to resolve guard file: " + ruleFileName + " - " + e.getMessage());
            return null;
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private static void deleteExistingStack(String stackName) {
        // Implementation unchanged from original
        try {
            System.out.println("🗑️  Checking if stack '" + stackName + "' exists...");

            ProcessBuilder checkProcess = new ProcessBuilder("aws", "cloudformation", "describe-stacks",
                "--stack-name", stackName, "--query", "Stacks[0].StackStatus", "--output", "text");
            Process checkProc = checkProcess.start();
            int checkExitCode = checkProc.waitFor();

            if (checkExitCode == 0) {
                System.out.println("✅ Stack found. Proceeding with deletion...");

                ProcessBuilder deleteProcess = new ProcessBuilder("aws", "cloudformation", "delete-stack",
                    "--stack-name", stackName);
                Process deleteProc = deleteProcess.start();
                int deleteExitCode = deleteProc.waitFor();

                if (deleteExitCode == 0) {
                    System.out.println("✅ Stack deletion initiated!");
                    System.out.println("⏳ Waiting for deletion to complete...");

                    ProcessBuilder waitProcess = new ProcessBuilder("aws", "cloudformation", "wait",
                        "stack-delete-complete", "--stack-name", stackName);
                    Process waitProc = waitProcess.start();
                    int waitExitCode = waitProc.waitFor();

                    if (waitExitCode == 0) {
                        System.out.println("✅ Stack deleted successfully!");
                    }
                }
            } else {
                System.out.println("ℹ️  Stack does not exist. Proceeding with fresh deployment...");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Error during stack deletion: " + e.getMessage());
        }
    }

    private static void saveContextToFile(Map<String, Object> context, String stackName) {
        try {
            // Use Jackson for proper JSON serialization (handles nested objects, arrays, etc.)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

            // Save flat structure (same format as deployment-contexts/*.json)
            FileWriter writer = new FileWriter("deployment-context.json");
            mapper.writeValue(writer, context);
            writer.close();

            // Debug: Print what was saved for key fields
            System.out.println("\n📝 Saved to deployment-context.json:");
            System.out.println("  complianceFrameworks: " + context.get("complianceFrameworks"));
            System.out.println("  logRetentionDays: " + context.get("logRetentionDays"));
            System.out.println("  region: " + context.get("region"));
            System.out.println("  auditManagerEnabled: " + context.get("auditManagerEnabled"));
            System.out.println("  cognitoInitialAdminEmail: " + context.get("cognitoInitialAdminEmail"));

            LOG.info("Deployment context saved to deployment-context.json");
        } catch (IOException e) {
            LOG.warning("Could not save context file: " + e.getMessage());
        }
    }

    private static void loadContextFromFileAndDeploy(String contextFile, String deploymentOption, String customStackName) throws Exception {
        // Load configuration using Jackson deserialization (replaces 80+ manual extract calls)
        DeploymentConfig config = DeploymentConfig.fromFile(contextFile);

        // Apply custom stack name override if provided
        if (customStackName != null && !customStackName.trim().isEmpty()) {
            config.stackName = customStackName;
        }

        // Reconstruct applicationSpec from applicationId (not serialized to JSON)
        if (config.applicationId != null) {
            config.applicationSpec = APPLICATION_REGISTRY.get(config.applicationId);

            if (config.applicationSpec == null) {
                System.err.println("❌ ERROR: Unknown application ID: " + config.applicationId);
                return;
            }
        } else {
            System.err.println("❌ ERROR: No applicationId found in deployment-context.json");
            return;
        }

        // Debug: Print what was loaded for key fields
        System.out.println("\n📖 Loaded from deployment-context.json:");
        System.out.println("  applicationId: " + config.applicationId);
        System.out.println("  runtime: " + config.runtime);
        System.out.println("  securityProfile: " + config.securityProfile);
        System.out.println("  provisionDatabase: " + config.provisionDatabase);
        System.out.println("  complianceFrameworks: '" + config.complianceFrameworks + "'");
        System.out.println("  region: '" + config.region + "'");

        // Pass false to prevent overwriting the saved deployment-context.json
        deployInfrastructure(config, deploymentOption, false);
    }

    private static Map<String, Object> buildCfcContext(DeploymentConfig config) {
        // Use DeploymentConfig's built-in Jackson serialization (replaces manual ObjectMapper config)
        return config.toContextMap();
    }

    private static void printConfiguration(DeploymentConfig config) {
        // Get application friendly name
        String appDisplayName = config.applicationId;
        for (List<ApplicationInfo> apps : APPLICATION_CATEGORIES.values()) {
            for (ApplicationInfo app : apps) {
                if (app.id.equals(config.applicationId)) {
                    appDisplayName = app.name;
                    break;
                }
            }
        }

        System.out.println("\n🎯 Application: " + appDisplayName + " (" + config.applicationId + ")");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Stack Name: " + config.stackName);
        System.out.println("Environment: " + config.environment);
        System.out.println("Runtime: " + config.runtime);
        System.out.println("Security Profile: " + config.securityProfile);

        if (config.domain != null && !config.domain.isEmpty()) {
            System.out.println("Domain: " + config.domain);
            if (config.subdomain != null && !config.subdomain.isEmpty()) {
                System.out.println("Subdomain: " + config.subdomain);
            }
            System.out.println("SSL Enabled: " + config.enableSsl);
        }

        if (config.applicationSpec != null && config.applicationSpec.supportsOidcIntegration() && !config.oidcProvider.equals("none")) {
            System.out.println("\n🔐 OIDC Authentication:");
            System.out.println("  Provider: " + config.oidcProvider);
        }

        System.out.println("\nNetwork Mode: " + config.networkMode);
        System.out.println("WAF Enabled: " + config.wafEnabled);
        System.out.println("CloudFront Enabled: " + config.cloudfrontEnabled);

        System.out.println("\n📈 Scaling:");
        System.out.println("  Min Capacity: " + config.minInstanceCapacity);
        System.out.println("  Max Capacity: " + config.maxInstanceCapacity);
        System.out.println("  Auto Scaling: " + config.enableAutoScaling);

        if (config.runtime == RuntimeType.EC2) {
            System.out.println("  Instance Type: " + config.instanceType);
        } else {
            System.out.println("  CPU: " + config.cpu);
            System.out.println("  Memory: " + config.memory + " MB");
        }

        System.out.println("\n🔧 Compliance:");
        System.out.println("  Monitoring: " + config.enableMonitoring);
        System.out.println("  Encryption: " + config.enableEncryption);
        System.out.println("  AWS Config: " + config.awsConfigEnabled);
        System.out.println("  Audit Manager: " + config.auditManagerEnabled);
        System.out.println("  GuardDuty: " + config.guardDutyEnabled);
        System.out.println("  Frameworks: " + (config.complianceFrameworks != null && !config.complianceFrameworks.isEmpty()
            ? config.complianceFrameworks : "(none)"));
        System.out.println("  Log Retention: " + config.logRetentionDays + " days");
    }

    // ============================================================================
    // INPUT UTILITY METHODS
    // ============================================================================

    private static String promptRequired(String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        System.out.flush();

        try {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                return input.isEmpty() ? defaultValue : input;
            } else {
                System.out.println();
                System.err.println("⚠️  Using default: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.out.println();
            System.err.println("⚠️  Error, using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static String promptOptional(String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "] (optional): ");
        System.out.flush();

        try {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                return input.isEmpty() ? defaultValue : input;
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String promptChoice(String prompt, String[] choices, String defaultValue) {
        System.out.println(prompt + ":");
        for (int i = 0; i < choices.length; i++) {
            System.out.println("  " + (i + 1) + ". " + choices[i] +
                (choices[i].equals(defaultValue) ? " (default)" : ""));
        }
        System.out.print("Choose [" + defaultValue + "]: ");
        System.out.flush();

        try {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    return defaultValue;
                }

                try {
                    int choice = Integer.parseInt(input);
                    if (choice >= 1 && choice <= choices.length) {
                        return choices[choice - 1];
                    }
                } catch (NumberFormatException e) {
                    for (String choice : choices) {
                        if (choice.equalsIgnoreCase(input)) {
                            return choice;
                        }
                    }
                }

                System.out.println("Invalid choice, using default: " + defaultValue);
                return defaultValue;
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static boolean promptYesNo(String prompt, boolean defaultValue) {
        System.out.print(prompt + " [" + (defaultValue ? "Y/n" : "y/N") + "]: ");
        System.out.flush();

        try {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim().toLowerCase();
                if (input.isEmpty()) {
                    return defaultValue;
                }
                return input.startsWith("y") || input.startsWith("t") || input.equals("1");
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static int promptIntWithValidation(String prompt, int defaultValue, int min, int max) {
        while (true) {
            System.out.print(prompt + " [" + defaultValue + "] (range: " + min + "-" + max + "): ");
            System.out.flush();

            try {
                if (!scanner.hasNextLine()) {
                    return defaultValue;
                }

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) return defaultValue;

                int value = Integer.parseInt(input);
                if (value < min || value > max) {
                    System.out.println("❌ Value must be between " + min + " and " + max);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("❌ Invalid number format");
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }

    private static String promptWithValidation(String prompt, String defaultValue, String[] validOptions) {
        while (true) {
            System.out.print(prompt + " [" + defaultValue + "]: ");
            System.out.flush();

            try {
                if (!scanner.hasNextLine()) {
                    return defaultValue;
                }

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) return defaultValue;

                for (String option : validOptions) {
                    if (option.equalsIgnoreCase(input)) {
                        return option.toLowerCase();
                    }
                }

                System.out.println("❌ Invalid option. Valid: " + String.join(", ", validOptions));
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }

    private static int promptNumberChoice(String prompt, int maxChoice, int defaultChoice) {
        System.out.print(prompt + " [" + defaultChoice + "]: ");
        System.out.flush();

        try {
            if (!scanner.hasNextLine()) {
                return defaultChoice;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return defaultChoice;

            int value = Integer.parseInt(input);
            if (value < 1 || value > maxChoice) {
                System.out.println("❌ Choice must be between 1 and " + maxChoice);
                return promptNumberChoice(prompt, maxChoice, defaultChoice);
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid number format");
            return promptNumberChoice(prompt, maxChoice, defaultChoice);
        } catch (Exception e) {
            return defaultChoice;
        }
    }

    private static String[] getAvailabilityZonesForRegion(String region, int count) {
        String[] allAzs = {
            region + "a",
            region + "b",
            region + "c",
            region + "d",
            region + "e",
            region + "f"
        };

        int actualCount = Math.min(count, allAzs.length);
        String[] result = new String[actualCount];
        System.arraycopy(allAzs, 0, result, 0, actualCount);
        return result;
    }

    /**
     * Validates Fargate CPU/Memory combination against AWS constraints.
     * See: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-cpu-memory-error.html
     */
    private static boolean isValidFargateCpuMemoryCombination(int cpu, int memory) {
        return switch (cpu) {
            case 256 -> memory >= 512 && memory <= 2048;
            case 512 -> memory >= 1024 && memory <= 4096;
            case 1024 -> memory >= 2048 && memory <= 8192;
            case 2048 -> memory >= 4096 && memory <= 16384;
            case 4096 -> memory >= 8192 && memory <= 30720;
            case 8192, 16384 -> memory >= 16384 && memory <= 30720;
            default -> false;
        };
    }

    /**
     * Returns a valid Fargate memory value for the given CPU, preferring the requested memory
     * or the closest valid value.
     */
    private static int getValidFargateMemoryForCpu(int cpu, int requestedMemory) {
        int minMemory = switch (cpu) {
            case 256 -> 512;
            case 512 -> 1024;
            case 1024 -> 2048;
            case 2048 -> 4096;
            case 4096 -> 8192;
            case 8192, 16384 -> 16384;
            default -> 2048;
        };

        int maxMemory = switch (cpu) {
            case 256 -> 2048;
            case 512 -> 4096;
            case 1024 -> 8192;
            case 2048 -> 16384;
            case 4096, 8192, 16384 -> 30720;
            default -> 8192;
        };

        // Return requested memory if valid, otherwise return the closest valid value
        if (requestedMemory < minMemory) {
            return minMemory;
        } else if (requestedMemory > maxMemory) {
            return maxMemory;
        } else {
            return requestedMemory;
        }
    }

    /**
     * Apply CDK-NAG suppressions for PRODUCTION security profile.
     * Option A: Enforce everywhere, suppress only documented exceptions.
     *
     * Suppressions are added for:
     * 1. Application IAM roles with AWS-required wildcards (SSM, ECR)
     * 2. CDK Custom Resource Provider Lambdas (framework internals)
     * 3. S3 buckets and other resources with valid exceptions
     */
    private static void applyProductionNagSuppressions(App app, DeploymentConfig config) {
        System.out.println("\n🔒 Applying PRODUCTION CDK-NAG suppressions...");

        // Get the stack
        software.amazon.awscdk.Stack stack = (software.amazon.awscdk.Stack) app.getNode().findChild(config.stackName);

        // Suppress wildcards for application IAM roles (AWS service requirements)
        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                // SSM service endpoints require wildcard (application EC2/ECS roles)
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("SSM service endpoints require Resource:* - This is an AWS API requirement for ssm:UpdateInstanceInformation")
                    .appliesTo(List.of("Resource::*"))
                    .build(),
                // CloudWatch Logs patterns use wildcards for log group matching
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("CloudWatch Logs path pattern requires wildcards for log group access")
                    .appliesTo(List.of(
                        "Resource::arn:aws:logs:<AWS::Region>:<AWS::AccountId>:log-group:/aws/*/" + config.stackName + "*",
                        "Resource::arn:aws:logs:<AWS::Region>:<AWS::AccountId>:log-group:/aws/ecs/" + config.stackName + "*"
                    ))
                    .build()
            ),
            Boolean.TRUE // Apply to all nested constructs
        );

        // Suppress AWS managed policies for CDK Custom Resource Providers (framework internals)
        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM4")
                    .reason("CDK Custom Resource Provider uses AWS managed policy - This is CDK framework code, not application code. Deployment-time only.")
                    .appliesTo(List.of(
                        "Policy::arn:<AWS::Partition>:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
                    ))
                    .build(),
                // Suppress wildcard for CDK Custom Resource Provider Lambda
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("CDK Custom Resource Provider requires wildcards - This is CDK framework code for deployment-time operations")
                    .appliesTo(List.of("Resource::*"))
                    .build()
            ),
            Boolean.TRUE
        );

        // Suppress S3 bucket warnings for compliance infrastructure
        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                // CloudTrail bucket doesn't need access logs (would cause circular dependency)
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-S1")
                    .reason("CloudTrail bucket access logging would create circular dependency. CloudTrail itself provides audit trail.")
                    .build(),
                // Audit Manager bucket doesn't need access logs (compliance reporting only)
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-S1")
                    .reason("Audit Manager report bucket doesn't require access logs - contains compliance reports only")
                    .build(),
                // SSL policy is enforced via bucket policy
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-S10")
                    .reason("SSL enforcement is handled via bucket policy conditions")
                    .build(),
                // Audit Manager S3 actions require wildcards for object operations
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("S3 object operations require action wildcards (DeleteObject*, Abort*) and bucket path wildcards - Standard S3 pattern")
                    .appliesTo(List.of(
                        "Action::s3:DeleteObject*",
                        "Action::s3:Abort*",
                        "Resource::<SystemContextFargatePRODUCTIONGDPRboundaryretentionComplianceAuditManagerReportBucket2C743036.Arn>/*"
                    ))
                    .build(),
                // SSM parameter path wildcards for shared parameters
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("SSM parameter paths use wildcards for stack-scoped shared parameters")
                    .appliesTo(List.of(
                        "Resource::arn:aws:ssm:" + config.region + ":*:parameter/cloudforge/shared/" + config.region + "/stack/" + config.stackName + "/*"
                    ))
                    .build()
            ),
            Boolean.TRUE
        );

        // Suppress ALB warnings (public internet-facing is intentional)
        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-EC23")
                    .reason("ALB security group allows 0.0.0.0/0 on ports 80/443 - This is intentional for public web application")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-ELB2")
                    .reason("ALB access logs enabled via albAccessLogging configuration flag")
                    .build()
            ),
            Boolean.TRUE
        );

        // Suppress Cognito warnings (MFA is configurable, Advanced Security has cost implications)
        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-COG2")
                    .reason("Cognito MFA is configurable via cognitoMfaEnabled flag - User's choice based on requirements")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-COG3")
                    .reason("Cognito Advanced Security Mode has cost implications - User's choice based on budget")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("Cognito SMS role requires wildcard for SNS publish - AWS service requirement")
                    .appliesTo(List.of("Resource::*"))
                    .build()
            ),
            Boolean.TRUE
        );

        // Suppress ECS warnings
        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-ECS2")
                    .reason("ECS task definition uses environment variables for non-sensitive configuration - Secrets use Secrets Manager")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-L1")
                    .reason("Lambda runtime version is managed by CDK framework - Updated with CDK version upgrades")
                    .build(),
                // AWS Backup service managed policy
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("AwsSolutions-IAM4")
                    .reason("AWS Backup service requires managed policy for proper operation - AWS service requirement")
                    .appliesTo(List.of(
                        "Policy::arn:<AWS::Partition>:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup"
                    ))
                    .build()
            ),
            Boolean.TRUE
        );

        System.out.println("   ✅ PRODUCTION suppressions applied (documented exceptions only)");

        // Apply HIPAA-specific suppressions if HIPAA compliance is enabled
        if (config.hasComplianceFramework(ComplianceFrameworkType.HIPAA)) {
            applyHipaaSuppressions(app, config);
        }

        // Apply PCI-DSS-specific suppressions if PCI-DSS compliance is enabled
        if (config.hasComplianceFramework(ComplianceFrameworkType.PCI_DSS)) {
            applyPciDssSuppressions(app, config);
        }
    }

    /**
     * Apply HIPAA-specific CDK-NAG suppressions for configurable compliance settings.
     *
     * These suppressions document configurable settings and cost trade-offs for HIPAA compliance.
     * Core IAM security (Customer Managed Policies) is enforced without suppressions.
     */
    private static void applyHipaaSuppressions(App app, DeploymentConfig config) {
        System.out.println("\n🏥 Applying HIPAA-specific CDK-NAG suppressions...");

        software.amazon.awscdk.Stack stack = (software.amazon.awscdk.Stack) app.getNode().findChild(config.stackName);

        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                // IAM Inline Policies - CDK framework resources (unavoidable)
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-IAMNoInlinePolicy")
                    .reason("CDK framework resources (Custom Resources, VPC FlowLog, Cognito SMS role) use inline policies by design. " +
                            "Application IAM roles use Customer Managed Policies per HIPAA best practices. " +
                            "Control IDs: 164.308(a)(3)(i), 164.308(a)(3)(ii)(A), 164.308(a)(3)(ii)(B), " +
                            "164.308(a)(4)(i), 164.308(a)(4)(ii)(A), 164.308(a)(4)(ii)(B), 164.308(a)(4)(ii)(C), 164.312(a)(1)")
                    .build(),
                // S3 Bucket Logging - CloudTrail/Audit buckets create circular dependency
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-S3BucketLoggingEnabled")
                    .reason("CloudTrail and Audit Manager buckets do not enable access logging to avoid circular dependency. " +
                            "CloudTrail itself provides comprehensive audit trail. Control IDs: 164.308(a)(3)(ii)(A), 164.312(b)")
                    .build(),
                // S3 Replication - Configurable based on RTO/RPO requirements
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-S3BucketReplicationEnabled")
                    .reason("S3 replication is configurable based on disaster recovery RTO/RPO requirements and cost implications. " +
                            "Can be enabled via s3ReplicationEnabled flag for cross-region compliance. " +
                            "Control IDs: 164.308(a)(7)(i), 164.308(a)(7)(ii)(A), 164.308(a)(7)(ii)(B)")
                    .build(),
                // S3 SSL - Enforced via bucket policy, not resource property
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-S3BucketSSLRequestsOnly")
                    .reason("SSL enforcement is implemented via bucket policy conditions (aws:SecureTransport). " +
                            "Control IDs: 164.312(a)(2)(iv), 164.312(c)(2), 164.312(e)(1), 164.312(e)(2)(i), 164.312(e)(2)(ii)")
                    .build(),
                // S3 KMS Encryption - Cost vs SSE-S3 trade-off
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-S3DefaultEncryptionKMS")
                    .reason("Default SSE-S3 encryption provides encryption at rest. KMS encryption adds ~$0.03 per 10k requests " +
                            "and is configurable via s3KmsEncryption flag for additional key management controls. " +
                            "Control IDs: 164.312(a)(2)(iv), 164.312(e)(2)(ii)")
                    .build(),
                // CloudTrail KMS Encryption - Cost vs SSE-S3 trade-off
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-CloudTrailEncryptionEnabled")
                    .reason("CloudTrail uses SSE-S3 encryption by default. KMS encryption is configurable via cloudTrailKmsEncryption flag. " +
                            "Control ID: 164.312(a)(2)(iv), 164.312(e)(2)(ii)")
                    .build(),
                // CloudWatch Logs KMS Encryption - Cost implications
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-CloudWatchLogGroupEncrypted")
                    .reason("KMS encryption for CloudWatch Logs has cost implications (~$1-3/GB ingested). " +
                            "Default encryption at rest is enabled. KMS is configurable for ePHI data. " +
                            "Control IDs: 164.312(a)(2)(iv), 164.312(e)(2)(ii)")
                    .build(),
                // VPC Public Subnets - Required for NAT/ALB, apps in private subnets
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-VPCSubnetAutoAssignPublicIpDisabled")
                    .reason("Public subnets are used exclusively for NAT Gateways and internet-facing ALB. " +
                            "Application workloads run in private subnets with no direct internet access. " +
                            "Control IDs: 164.308(a)(3)(i), 164.308(a)(4)(ii)(A), 164.308(a)(4)(ii)(C), 164.312(a)(1), 164.312(e)(1)")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-VPCNoUnrestrictedRouteToIGW")
                    .reason("Public subnet routes to IGW are required for NAT Gateway and ALB internet connectivity. " +
                            "Application workloads are isolated in private subnets. Control ID: 164.312(e)(1)")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-VPCDefaultSecurityGroupClosed")
                    .reason("VPC default security group is not used by application resources. " +
                            "All resources use custom security groups with least-privilege rules. Control ID: 164.312(e)(1)")
                    .build(),
                // ALB Configuration - Configurable security settings
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-ALBHttpDropInvalidHeaderEnabled")
                    .reason("ALB HTTP header validation is configurable based on application compatibility requirements. " +
                            "Can be enabled via albDropInvalidHeaderFields flag. " +
                            "Control IDs: 164.312(a)(2)(iv), 164.312(e)(1), 164.312(e)(2)(i), 164.312(e)(2)(ii)")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-ELBLoggingEnabled")
                    .reason("ALB access logging is configurable via albAccessLogging flag. " +
                            "When enabled, logs are stored in S3 with encryption and retention policies. Control ID: 164.312(b)")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-ELBv2ACMCertificateRequired")
                    .reason("HTTP listener redirects to HTTPS when SSL is enabled. " +
                            "HTTPS listener uses ACM certificate. Both listeners are required for proper redirect flow. " +
                            "Control IDs: 164.312(a)(2)(iv), 164.312(e)(2)(ii)")
                    .build(),
                // WAF Logging - Cost implications
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-WAFv2LoggingEnabled")
                    .reason("WAF logging is configurable based on cost and compliance requirements (~$0.50 per million requests). " +
                            "Can be enabled via wafLoggingEnabled flag when audit trail is required. Control ID: 164.312(b)")
                    .build(),
                // Lambda (CDK Framework) - Deployment-time only
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-LambdaConcurrency")
                    .reason("CDK Custom Resource Lambdas are deployment-time framework functions with low concurrency needs. " +
                            "Not application code. Control ID: 164.312(b)")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-LambdaDLQ")
                    .reason("CDK Custom Resource Lambdas are deployment-time framework functions. " +
                            "Failures are visible in CloudFormation stack events. Not application code. Control ID: 164.312(b)")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("HIPAA.Security-LambdaInsideVPC")
                    .reason("CDK Custom Resource Lambdas are deployment-time framework functions that require public API access. " +
                            "They do not process ePHI and are not part of the application runtime. " +
                            "Control IDs: 164.308(a)(3)(i), 164.308(a)(4)(ii)(A), 164.308(a)(4)(ii)(C), 164.312(a)(1), 164.312(e)(1)")
                    .build()
            ),
            Boolean.TRUE
        );

        System.out.println("   ✅ HIPAA-specific suppressions applied (configurable settings and cost trade-offs)");
    }

    /**
     * Apply PCI-DSS-specific CDK-NAG suppressions for configurable compliance settings.
     *
     * These suppressions document configurable settings and CDK framework limitations for PCI-DSS compliance.
     */
    private static void applyPciDssSuppressions(App app, DeploymentConfig config) {
        System.out.println("\n💳 Applying PCI-DSS-specific CDK-NAG suppressions...");

        software.amazon.awscdk.Stack stack = (software.amazon.awscdk.Stack) app.getNode().findChild(config.stackName);

        io.github.cdklabs.cdknag.NagSuppressions.addStackSuppressions(
            stack,
            List.of(
                // IAM Inline Policies - CDK framework resources (unavoidable)
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-IAMNoInlinePolicy")
                    .reason("CDK framework resources (Custom Resources, VPC FlowLog, Cognito SMS role, CloudTrail role) use inline policies by design. " +
                            "These are auto-generated deployment-time policies. Control IDs: 2.2, 7.1.2, 7.1.3, 7.2.1, 7.2.2")
                    .build(),
                // S3 Bucket Logging - CloudTrail/Audit buckets create circular dependency
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3BucketLoggingEnabled")
                    .reason("CloudTrail and Audit Manager buckets do not enable access logging to avoid circular dependency. " +
                            "CloudTrail S3 data events provide comprehensive audit trail. Control IDs: 2.2, 10.1, 10.2.x, 10.3.x")
                    .build(),
                // S3 Replication - Single-region deployment
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3BucketReplicationEnabled")
                    .reason("S3 replication is not required for single-region deployments. " +
                            "Versioning is enabled for data recovery. Control IDs: 2.2, 10.5.3")
                    .build(),
                // S3 KMS Encryption - Using SSE-S3 with SSL enforcement
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3DefaultEncryptionKMS")
                    .reason("Buckets use encryption with SSL enforcement via bucket policy. " +
                            "CloudTrail trail-level KMS encryption is enabled for PRODUCTION. Control IDs: 3.4, 8.2.1, 10.5")
                    .build(),
                // CloudTrail Encryption - Enabled for PCI-DSS PRODUCTION
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-CloudTrailEncryptionEnabled")
                    .reason("CloudTrail uses KMS encryption for HIPAA/PCI-DSS PRODUCTION deployments. " +
                            "S3 bucket has encryption at rest enabled. Control IDs: 2.2, 3.4, 10.5")
                    .build(),
                // CloudWatch Logs KMS Encryption - Cost implications
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-CloudWatchLogGroupEncrypted")
                    .reason("CloudWatch Logs groups for CDK framework resources use default encryption. " +
                            "KMS encryption is enabled for WAF and CloudTrail log groups. Control ID: 3.4")
                    .build(),
                // VPC Public Subnets - Required for NAT/ALB architecture
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-VPCSubnetAutoAssignPublicIpDisabled")
                    .reason("Public subnets are used exclusively for NAT Gateways and internet-facing ALB. " +
                            "Application workloads run in private subnets. Control IDs: 1.2, 1.2.1, 1.3.x, 2.2.2")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-VPCNoUnrestrictedRouteToIGW")
                    .reason("Public subnets require IGW routes for NAT Gateway and ALB internet access. " +
                            "Application workloads in private subnets have no direct internet access. Control IDs: 1.2, 1.2.1, 1.3.x, 2.2.2")
                    .build(),
                // ALB Access Logs Bucket - CDK logAccessLogs() limitation
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-S3BucketSSLRequestsOnly")
                    .reason("ALB access logs bucket is written exclusively by AWS ELB service via internal HTTPS connections. " +
                            "CDK logAccessLogs() creates bucket policy that doesn't merge with addToResourcePolicy() statements. " +
                            "Bucket has blockPublicAccess enabled. Control IDs: 2.2, 4.1, 8.2.1")
                    .build(),
                // ALB Configuration
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-ALBHttpDropInvalidHeaderEnabled")
                    .reason("ALB drop invalid headers is enabled via LoadBalancerAttributes override. Control IDs: 4.1, 8.2.1")
                    .build(),
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-ELBv2ACMCertificateRequired")
                    .reason("HTTP listener redirects to HTTPS when SSL is enabled. " +
                            "HTTPS listener uses ACM certificate. Both listeners required for redirect flow. Control ID: 4.1")
                    .build(),
                // WAF Logging - Enabled in WafFactory
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-WAFv2LoggingEnabled")
                    .reason("WAF logging is enabled to CloudWatch Logs with KMS encryption. " +
                            "Control IDs: 10.1, 10.3.x, 10.5.4")
                    .build(),
                // Lambda (CDK Framework) - Deployment-time only
                io.github.cdklabs.cdknag.NagPackSuppression.builder()
                    .id("PCI.DSS.321-LambdaInsideVPC")
                    .reason("CDK Custom Resource Lambdas are deployment-time framework functions that make AWS API calls. " +
                            "They do not process cardholder data and are not part of the CDE. Control IDs: 1.2, 1.2.1, 1.3.x, 2.2.2")
                    .build()
            ),
            Boolean.TRUE
        );

        System.out.println("   ✅ PCI-DSS-specific suppressions applied (CDK framework limitations and architecture justifications)");
    }

    /**
     * Maps a compliance framework to its corresponding cdk-nag rule pack.
     *
     * @param framework the framework ID (uppercase)
     * @param complianceMode the compliance mode (enforce or advisory)
     * @return the corresponding NagPack, or null if no mapping exists
     */
    private static NagPack mapFrameworkToNagPack(String framework, ComplianceMode complianceMode) {
        boolean enforce = complianceMode == ComplianceMode.ENFORCE;
        // Report formats for compliance auditing (always generate reports)
        var reportFormats = List.of(NagReportFormat.JSON, NagReportFormat.CSV);

        return switch (framework) {
            case "HIPAA" -> HIPAASecurityChecks.Builder.create()
                    .verbose(true)
                    .reports(true)
                    .reportFormats(reportFormats)
                    .logIgnores(!enforce)
                    .build();
            case "PCI-DSS", "PCIDSS", "PCI" -> PCIDSS321Checks.Builder.create()
                    .verbose(true)
                    .reports(true)
                    .reportFormats(reportFormats)
                    .logIgnores(!enforce)
                    .build();
            case "SOC2", "SOC-2" -> AwsSolutionsChecks.Builder.create()
                    .verbose(true)
                    .reports(true)
                    .reportFormats(reportFormats)
                    .logIgnores(!enforce)
                    .build();
            case "FEDRAMP", "FEDRAMPHIGH", "FEDRAMP-HIGH" -> {
                // FEDRAMP: Handled by existing FedRampRules.java plugin only
                // Not integrated with cdk-nag to avoid conflicts
                System.out.println("      (FedRAMP uses FedRampRules.java plugin, not cdk-nag)");
                yield null;
            }
            case "GDPR" -> {
                // GDPR: Organizational controls, not infrastructure-level cdk-nag checks
                System.out.println("      (GDPR uses organizational rules, not cdk-nag)");
                yield null;
            }
            // Custom frameworks: fallback to AWS Solutions best practices
            default -> {
                System.out.println("      (Using AwsSolutionsChecks as fallback for " + framework + ")");
                yield AwsSolutionsChecks.Builder.create()
                        .verbose(true)
                        .reports(true)
                        .reportFormats(reportFormats)
                        .logIgnores(!enforce)
                        .build();
            }
        };
    }

    // ============================================================================
    // DATA CLASSES - Now imported from cloudforge-core library (contract layer)
    // DeploymentConfig: com.cloudforge.core.config.DeploymentConfig
    // ApplicationInfo: com.cloudforge.core.config.ApplicationInfo
}
