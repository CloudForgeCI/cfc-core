package com.cloudforgeci.samples.app;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.samples.launchers.JenkinsEc2Stack;
import com.cloudforgeci.samples.launchers.JenkinsFargateStack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Environment;

import java.io.Console;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Interactive CDK Deployer that prompts users for configuration and deploys infrastructure.
 * 
 * Uses the SystemContext orchestration layer for modular, expandable deployments:
 * - Jenkins (Fargate/EC2)
 * - S3 + CloudFront (Static Website) - Coming Soon
 * - S3 + CloudFront + SES + Lambda (Website + Mailer) - Coming Soon
 */
public class InteractiveDeployer {
    
    private static final Scanner scanner = new Scanner(System.in);
    private static final Console console = System.console();
    
    // Check if we have a proper console for interactive input
    private static boolean hasConsole() {
        // Always return true to allow input reading
        // The error handling in the input methods will catch any issues
        return true;
    }
    
    // Deployment strategy registry - easily expandable
    private static final Map<String, DeploymentStrategy> DEPLOYMENT_STRATEGIES = Map.of(
        "jenkins", new JenkinsDeploymentStrategy(),
        "s3-website", new S3WebsiteDeploymentStrategy(),
        "s3-website-mailer", new S3WebsiteMailerDeploymentStrategy()
    );
    
    public static void main(String[] args) {
        System.out.println("🚀 CloudForge Community Interactive Deployer");
        System.out.println("=============================================");
        System.out.println("📖 This tool helps you deploy Jenkins infrastructure with:");
        System.out.println("   • EC2 or Fargate runtime options");
        System.out.println("   • Automatic SSL certificate management");
        System.out.println("   • Domain and subdomain configuration");
        System.out.println("   • Security profiles (DEV/STAGING/PRODUCTION)");
        System.out.println("   • Advanced monitoring and encryption options");
        System.out.println("   • Health check configuration");
        System.out.println("   • Network and security settings");
        System.out.println("");
        
        // Check for command line arguments
        String customStackName = null;
        String deploymentOption = null;
        
        if (args.length > 0) {
            customStackName = args[0];
            System.out.println("📝 Using custom stack name: " + customStackName);
        }
        if (args.length > 1) {
            deploymentOption = args[1];
            System.out.println("📝 Using deployment option: " + deploymentOption);
        }
        
        try {
            // Check if we have a saved context file
            String contextFile = "deployment-context.json";
            if (Files.exists(Paths.get(contextFile))) {
                System.out.println("📁 Found saved deployment context, using it...");
                loadContextFromFileAndDeploy(contextFile, deploymentOption, customStackName);
            } else {
                // No saved context, collect configuration interactively
                DeploymentConfig config = collectConfiguration(customStackName);
                deployInfrastructure(config, deploymentOption);
            }
        } catch (Exception e) {
            System.err.println("❌ Deployment failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static DeploymentConfig collectConfiguration(String customStackName) {
        DeploymentConfig config = new DeploymentConfig();
        
        // Basic Configuration
        if (customStackName != null && !customStackName.trim().isEmpty()) {
            config.stackName = customStackName;
            System.out.println("✅ Stack name set to: " + config.stackName);
        } else {
            config.stackName = promptRequired("Stack Name", "my-cloudforge-stack");
        }
        config.environment = promptChoice("Environment", new String[]{"dev", "staging", "prod"}, "dev");
        
        // Deployment Type - dynamically populated from strategies
        String[] availableTypes = DEPLOYMENT_STRATEGIES.keySet().toArray(new String[0]);
        config.deploymentType = promptChoice("Deployment Type", availableTypes, "jenkins");
        
        // Domain Configuration
        config.domain = promptOptional("Domain (e.g., example.com)", "");
        if (!config.domain.isEmpty()) {
            config.subdomain = promptOptional("Subdomain (e.g., ci, app)", "");
            config.enableSsl = promptYesNo("Enable SSL Certificate", true);
        } else {
            config.subdomain = "";
            config.enableSsl = false;
        }
        
        // Deployment-specific configuration using strategy pattern
        DeploymentStrategy strategy = DEPLOYMENT_STRATEGIES.get(config.deploymentType);
        if (strategy != null) {
            strategy.collectConfiguration(config);
        } else {
            throw new IllegalArgumentException("Unknown deployment type: " + config.deploymentType);
        }
        
        return config;
    }
    
    
    
    private static void deployInfrastructure(DeploymentConfig config, String deploymentOption) {
        System.out.println("\n🔧 Building CDK Context...");
        
        Map<String, Object> cfcContext = buildCfcContext(config);
        
        System.out.println("\n🔍 DEBUG: CDK Context being set:");
        System.out.println("  - runtime: " + cfcContext.get("runtime"));
        System.out.println("  - topology: " + cfcContext.get("topology"));
        System.out.println("  - stackName: " + cfcContext.get("stackName"));
        
        System.out.println("\n📋 Deployment Configuration:");
        System.out.println("============================");
        printConfiguration(config);
        
        System.out.println("\n🚀 Deployment Options:");
        System.out.println("========================");
        System.out.println("1. Synthesize only (generate CloudFormation template)");
        System.out.println("2. Deploy to AWS (synthesize + deploy)");
        System.out.println("3. Delete existing stack and redeploy");
        System.out.println("4. Cancel");
        
        String choice;
        if (deploymentOption != null && !deploymentOption.trim().isEmpty()) {
            // Use command-line parameter if provided
            choice = deploymentOption.trim();
            System.out.println("Using deployment option from command line: " + choice);
        } else {
            // Interactive input
            System.out.print("Choose option [1-4]: ");
            try {
                if (scanner.hasNextLine()) {
                    choice = scanner.nextLine().trim();
                } else {
                    System.out.println("No input available, defaulting to synthesis only");
                    choice = "1";
                }
            } catch (Exception e) {
                System.out.println("Input error, defaulting to synthesis only: " + e.getMessage());
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
                System.out.println("❌ Deployment cancelled by user");
                return;
            default:
                System.out.println("Invalid choice. Defaulting to synthesis only.");
                System.out.println("\n🚀 Starting CDK Synthesis...");
        }
        
        App app = new App();
        
        // Set CDK context on the app level
        app.getNode().setContext("cfc", cfcContext);
        
        // Save context to file for cdk deploy to use
        saveContextToFile(cfcContext, config.stackName);
        
        DeploymentContext cfc = DeploymentContext.from(app);
        System.out.println("🔍 DEBUG: DeploymentContext.from(app) returned:");
        System.out.println("  - runtime: " + cfc.runtime());
        System.out.println("  - topology: " + cfc.topology());
        System.out.println("  - stackName: " + cfc.stackName());
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(config.securityProfile);
        
        StackProps props = StackProps.builder()
            .env(Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT") != null ? System.getenv("CDK_DEFAULT_ACCOUNT") : "123456789012")
                .region(System.getenv("CDK_DEFAULT_REGION") != null ? System.getenv("CDK_DEFAULT_REGION") : "us-east-1")
                .build())
            .build();
        
        // Create stacks based on runtime type (like CloudForgeCommunitySample)
        System.out.println("🔍 DEBUG: Creating stack for runtime: " + config.runtime + " with name: " + config.stackName);
        if (config.runtime == RuntimeType.EC2) {
            System.out.println("🔍 DEBUG: Creating JenkinsEc2Stack");
            new JenkinsEc2Stack(app, config.stackName, props, config.securityProfile, iamProfile);
        } else if (config.runtime == RuntimeType.FARGATE) {
            System.out.println("🔍 DEBUG: Creating JenkinsFargateStack");
            new JenkinsFargateStack(app, config.stackName, props, config.securityProfile, iamProfile);
        } else {
            throw new IllegalArgumentException("Unsupported runtime type: " + config.runtime);
        }
        
        // Show appropriate completion message based on choice
        if (choice.equals("2") || choice.equals("3")) {
            System.out.println("\n✅ CDK Stack synthesized successfully!");
            System.out.println("🚀 Starting CDK deployment to AWS...");
            app.synth();
            
            // Execute cdk deploy
            try {
                System.out.println("⏳ Deploying stack '" + config.stackName + "' to AWS...");
                ProcessBuilder deployProcess = new ProcessBuilder("cdk", "deploy", "--require-approval", "never");
                Process deployProc = deployProcess.start();
                int deployExitCode = deployProc.waitFor();
                
                if (deployExitCode == 0) {
                    System.out.println("✅ Stack '" + config.stackName + "' deployed successfully to AWS!");
                } else {
                    System.out.println("❌ CDK deployment failed with exit code: " + deployExitCode);
                    System.out.println("Check the output above for details.");
                }
            } catch (Exception e) {
                System.out.println("❌ Error during CDK deployment: " + e.getMessage());
                System.out.println("You can manually run: cdk deploy");
            }
        } else {
            System.out.println("\n✅ CDK Stack synthesized successfully!");
            System.out.println("Run 'cdk deploy' to deploy to AWS or 'cdk diff' to see changes");
            app.synth();
        }
    }
    
    private static void saveContextToFile(Map<String, Object> context, String stackName) {
        try {
            FileWriter writer = new FileWriter("deployment-context.json");
            writer.write("{\n");
            writer.write("  \"stackName\": \"" + stackName + "\",\n");
            writer.write("  \"context\": {\n");
            boolean first = true;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (!first) writer.write(",\n");
                writer.write("    \"" + entry.getKey() + "\": \"" + entry.getValue() + "\"");
                first = false;
            }
            writer.write("\n  }\n");
            writer.write("}\n");
            writer.close();
            System.out.println("💾 Deployment context saved to deployment-context.json");
        } catch (IOException e) {
            System.err.println("⚠️  Warning: Could not save context file: " + e.getMessage());
        }
    }
    
    private static void deleteExistingStack(String stackName) {
        try {
            System.out.println("🗑️  Checking if stack '" + stackName + "' exists...");
            
            // Check if stack exists using AWS CLI
            ProcessBuilder checkProcess = new ProcessBuilder("aws", "cloudformation", "describe-stacks", 
                "--stack-name", stackName, "--query", "Stacks[0].StackStatus", "--output", "text");
            Process checkProc = checkProcess.start();
            int checkExitCode = checkProc.waitFor();
            
            if (checkExitCode == 0) {
                System.out.println("✅ Stack '" + stackName + "' found. Proceeding with deletion...");
                
                // Delete the stack
                ProcessBuilder deleteProcess = new ProcessBuilder("aws", "cloudformation", "delete-stack", 
                    "--stack-name", stackName);
                Process deleteProc = deleteProcess.start();
                int deleteExitCode = deleteProc.waitFor();
                
                if (deleteExitCode == 0) {
                    System.out.println("✅ Stack deletion initiated successfully!");
                    System.out.println("⏳ Waiting for stack deletion to complete...");
                    
                    // Wait for stack deletion to complete
                    ProcessBuilder waitProcess = new ProcessBuilder("aws", "cloudformation", "wait", 
                        "stack-delete-complete", "--stack-name", stackName);
                    Process waitProc = waitProcess.start();
                    int waitExitCode = waitProc.waitFor();
                    
                    if (waitExitCode == 0) {
                        System.out.println("✅ Stack '" + stackName + "' deleted successfully!");
                        
                        // Prompt to clean up local files
                        cleanupLocalFiles();
                    } else {
                        System.out.println("⚠️  Stack deletion may still be in progress. Continuing with deployment...");
                    }
                } else {
                    System.out.println("❌ Failed to delete stack. Continuing with deployment...");
                }
            } else {
                System.out.println("ℹ️  Stack '" + stackName + "' does not exist. Proceeding with fresh deployment...");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Error during stack deletion: " + e.getMessage());
            System.out.println("Continuing with deployment...");
        }
    }
    
    private static void cleanupLocalFiles() {
        try {
            System.out.println("\n🧹 Local Cleanup Options:");
            System.out.println("=========================");
            System.out.println("1. Delete deployment-context.json and empty cdk.out folder");
            System.out.println("2. Delete deployment-context.json only");
            System.out.println("3. Keep all local files");
            System.out.print("Choose cleanup option [1-3]: ");
            
            String cleanupChoice;
            try {
                if (scanner.hasNextLine()) {
                    cleanupChoice = scanner.nextLine().trim();
                } else {
                    System.out.println("No input available, defaulting to keep all files");
                    cleanupChoice = "3";
                }
            } catch (Exception e) {
                System.out.println("Input error, defaulting to keep all files: " + e.getMessage());
                cleanupChoice = "3";
            }
            
            switch (cleanupChoice) {
                case "1":
                    deleteDeploymentContext();
                    emptyCdkOutFolder();
                    break;
                case "2":
                    deleteDeploymentContext();
                    break;
                case "3":
                    System.out.println("ℹ️  Keeping all local files");
                    break;
                default:
                    System.out.println("Invalid choice. Keeping all local files");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Error during local cleanup: " + e.getMessage());
        }
    }
    
    private static void deleteDeploymentContext() {
        try {
            Path contextFile = Paths.get("deployment-context.json");
            if (Files.exists(contextFile)) {
                Files.delete(contextFile);
                System.out.println("✅ deployment-context.json deleted");
            } else {
                System.out.println("ℹ️  deployment-context.json not found");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Error deleting deployment-context.json: " + e.getMessage());
        }
    }
    
    private static void emptyCdkOutFolder() {
        try {
            Path cdkOutDir = Paths.get("cdk.out");
            if (Files.exists(cdkOutDir) && Files.isDirectory(cdkOutDir)) {
                // Delete all files and subdirectories in cdk.out
                Files.walk(cdkOutDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.out.println("⚠️  Could not delete: " + path + " - " + e.getMessage());
                        }
                    });
                System.out.println("✅ cdk.out folder emptied");
            } else {
                System.out.println("ℹ️  cdk.out folder not found");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Error emptying cdk.out folder: " + e.getMessage());
        }
    }
    
    private static void loadContextFromFileAndDeploy(String contextFile, String deploymentOption, String customStackName) throws Exception {
        // Read the context file and create a DeploymentConfig from it
        String content = Files.readString(Paths.get(contextFile));
        
        // Parse the JSON (simple parsing for this use case)
        String stackName = extractValue(content, "stackName");
        String runtime = extractValue(content, "runtime");
        String topology = extractValue(content, "topology");
        String securityProfile = extractValue(content, "securityProfile");
        
        System.out.println("🔍 DEBUG: Extracted values from context:");
        System.out.println("  - stackName: " + stackName);
        System.out.println("  - runtime: " + runtime);
        System.out.println("  - topology: " + topology);
        System.out.println("  - securityProfile: " + securityProfile);
        
            // Create DeploymentConfig from saved context
            DeploymentConfig config = new DeploymentConfig();
            // Use custom stack name from command line if provided, otherwise use saved context
            if (customStackName != null && !customStackName.trim().isEmpty()) {
                config.stackName = customStackName;
                System.out.println("✅ Overriding stack name with custom value: " + customStackName);
            } else {
                config.stackName = stackName;
                System.out.println("✅ Using stack name from saved context: " + stackName);
            }
            config.runtime = RuntimeType.valueOf(runtime);
            
            // Read topology from saved context
            if (topology != null && !topology.isEmpty()) {
                config.topology = TopologyType.valueOf(topology);
            } else {
                // Default fallback based on runtime if topology not found in context
                if (config.runtime == RuntimeType.EC2) {
                    config.topology = TopologyType.JENKINS_SERVICE;
                } else {
                    config.topology = TopologyType.JENKINS_SERVICE;
                }
            }
            
            config.securityProfile = SecurityProfile.valueOf(securityProfile);
        
        // Set other required fields with defaults
        config.environment = "dev";
        config.deploymentType = "jenkins";
        config.networkMode = "public-no-nat";
        config.wafEnabled = false;
        config.cloudfrontEnabled = false;
        config.cpu = 1024;
        config.memory = 2048;
        config.authMode = "none";
        
        // Extract domain configuration from saved context
        config.domain = extractValue(content, "domain");
        if (config.domain == null) config.domain = "";
        
        config.subdomain = extractValue(content, "subdomain");
        if (config.subdomain == null) config.subdomain = "";
        
        String enableSslStr = extractValue(content, "enableSsl");
        config.enableSsl = "true".equalsIgnoreCase(enableSslStr);
        
        // Instance capacity applies to both EC2 and Fargate - read from saved context
        String minCapacityStr = extractValue(content, "minInstanceCapacity");
        String maxCapacityStr = extractValue(content, "maxInstanceCapacity");
        String cpuTargetStr = extractValue(content, "cpuTargetUtilization");
        String enableAutoScalingStr = extractValue(content, "enableAutoScaling");
        
        config.minInstanceCapacity = minCapacityStr != null ? Integer.parseInt(minCapacityStr) : 1;
        config.maxInstanceCapacity = maxCapacityStr != null ? Integer.parseInt(maxCapacityStr) : 1;
        config.cpuTargetUtilization = cpuTargetStr != null ? Integer.parseInt(cpuTargetStr) : 60;
        config.enableAutoScaling = "true".equalsIgnoreCase(enableAutoScalingStr);
        
        if (config.runtime == RuntimeType.EC2) {
            config.instanceType = "t3.micro";
        }
        
        System.out.println("📋 Using saved configuration:");
        System.out.println("Stack Name: " + config.stackName);
        System.out.println("Runtime: " + config.runtime);
        System.out.println("Topology: " + config.topology);
        System.out.println("Security Profile: " + config.securityProfile);
        System.out.println("Domain: " + (config.domain.isEmpty() ? "none" : config.domain));
        System.out.println("Subdomain: " + (config.subdomain.isEmpty() ? "none" : config.subdomain));
        System.out.println("SSL Enabled: " + config.enableSsl);
        
        // Deploy using the saved configuration
        deployInfrastructure(config, deploymentOption);
    }
    
    private static String extractValue(String json, String key) {
        String pattern = "\"" + key + "\":\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    private static Map<String, Object> buildCfcContext(DeploymentConfig config) {
        Map<String, Object> context = new HashMap<>();
        
        // Basic configuration
        context.put("env", config.environment);
        context.put("tier", "public");
        context.put("runtime", config.runtime.name());
        context.put("topology", config.topology.name());
        context.put("securityProfile", config.securityProfile.name());
        context.put("stackName", config.stackName);
        
        // Domain configuration - always include these fields
        context.put("domain", config.domain);
        context.put("subdomain", config.subdomain);
        context.put("enableSsl", config.enableSsl);
        
        // Network configuration
        context.put("networkMode", config.networkMode);
        context.put("wafEnabled", config.wafEnabled);
        context.put("cloudfrontEnabled", config.cloudfrontEnabled);
        
        // Jenkins-specific configuration
        // Instance capacity and auto-scaling apply to both EC2 and Fargate
        context.put("minInstanceCapacity", config.minInstanceCapacity);
        context.put("maxInstanceCapacity", config.maxInstanceCapacity);
        context.put("cpuTargetUtilization", config.cpuTargetUtilization);
        context.put("enableAutoScaling", config.enableAutoScaling);
        
        if (config.runtime == RuntimeType.EC2) {
            context.put("instanceType", config.instanceType);
        }
        context.put("cpu", config.cpu);
        context.put("memory", config.memory);
        context.put("authMode", config.authMode);
        
        // Advanced configuration
        context.put("enableMonitoring", config.enableMonitoring);
        context.put("enableEncryption", config.enableEncryption);
        context.put("logRetentionDays", config.logRetentionDays);
        context.put("region", config.region);
        
        // Health check configuration
        context.put("healthCheckGracePeriod", config.healthCheckGracePeriod);
        context.put("healthCheckInterval", config.healthCheckInterval);
        context.put("healthCheckTimeout", config.healthCheckTimeout);
        context.put("healthyThreshold", config.healthyThreshold);
        context.put("unhealthyThreshold", config.unhealthyThreshold);
        
        if (!config.authMode.equals("none")) {
            context.put("ssoInstanceArn", config.ssoInstanceArn);
            context.put("ssoGroupId", config.ssoGroupId);
            context.put("ssoTargetAccountId", config.ssoTargetAccountId);
        }
        
        return context;
    }
    
    // Utility methods for user input
    private static String promptRequired(String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        
        if (!hasConsole()) {
            System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
            return defaultValue;
        }
        
        try {
            String input = scanner.nextLine().trim();
            return input.isEmpty() ? defaultValue : input;
        } catch (Exception e) {
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static String promptOptional(String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "] (optional): ");
        
        if (!hasConsole()) {
            System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
            return defaultValue;
        }
        
        try {
            String input = scanner.nextLine().trim();
            return input.isEmpty() ? defaultValue : input;
        } catch (Exception e) {
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
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
        
        if (!hasConsole()) {
            System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
            return defaultValue;
        }
        
        try {
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
                // Try to match by name
                for (String choice : choices) {
                    if (choice.equalsIgnoreCase(input)) {
                        return choice;
                    }
                }
            }
            
            System.out.println("Invalid choice, using default: " + defaultValue);
            return defaultValue;
        } catch (Exception e) {
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static boolean promptYesNo(String prompt, boolean defaultValue) {
        System.out.print(prompt + " [" + (defaultValue ? "Y/n" : "y/N") + "]: ");
        
        if (!hasConsole()) {
            System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
            return defaultValue;
        }
        
        try {
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.isEmpty()) {
                return defaultValue;
            }
            return input.startsWith("y") || input.startsWith("t") || input.equals("1");
        } catch (Exception e) {
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static int promptInt(String prompt, int defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        
        if (!hasConsole()) {
            System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
            return defaultValue;
        }
        
        try {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number, using default: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static int promptIntWithValidation(String prompt, int defaultValue, int min, int max) {
        while (true) {
            System.out.print(prompt + " [" + defaultValue + "] (range: " + min + "-" + max + "): ");
            
            if (!hasConsole()) {
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
            
            try {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) return defaultValue;
                
                int value = Integer.parseInt(input);
                if (value < min || value > max) {
                    System.out.println("❌ Value must be between " + min + " and " + max + ". Please try again.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("❌ Invalid number format. Please enter a valid integer.");
            } catch (Exception e) {
                System.err.println("⚠️  Error reading input, using default: " + defaultValue);
                return defaultValue;
            }
        }
    }
    
    private static String promptWithValidation(String prompt, String defaultValue, String[] validOptions) {
        while (true) {
            System.out.print(prompt + " [" + defaultValue + "]: ");
            
            if (!hasConsole()) {
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
            
            try {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) return defaultValue;
                
                for (String option : validOptions) {
                    if (option.equalsIgnoreCase(input)) {
                        return option.toLowerCase();
                    }
                }
                
                System.out.println("❌ Invalid option. Valid options: " + String.join(", ", validOptions));
            } catch (Exception e) {
                System.err.println("⚠️  Error reading input, using default: " + defaultValue);
                return defaultValue;
            }
        }
    }
    
    private static void printConfiguration(DeploymentConfig config) {
        System.out.println("Stack Name: " + config.stackName);
        System.out.println("Environment: " + config.environment);
        System.out.println("Deployment Type: " + config.deploymentType);
        System.out.println("Runtime: " + config.runtime);
        System.out.println("Topology: " + config.topology);
        System.out.println("Security Profile: " + config.securityProfile);
        
        if (!config.domain.isEmpty()) {
            System.out.println("Domain: " + config.domain);
            if (!config.subdomain.isEmpty()) {
                System.out.println("Subdomain: " + config.subdomain);
            }
            System.out.println("SSL Enabled: " + config.enableSsl);
        }
        
        System.out.println("Network Mode: " + config.networkMode);
        System.out.println("WAF Enabled: " + config.wafEnabled);
        System.out.println("CloudFront Enabled: " + config.cloudfrontEnabled);
        
        // Instance Capacity (applies to both EC2 and Fargate)
        System.out.println("Min Instance Capacity: " + config.minInstanceCapacity);
        System.out.println("Max Instance Capacity: " + config.maxInstanceCapacity);
        System.out.println("Auto Scaling: " + config.enableAutoScaling);
        if (config.enableAutoScaling) {
            System.out.println("CPU Target Utilization: " + config.cpuTargetUtilization + "%");
        }
        
        if (config.runtime == RuntimeType.EC2) {
            System.out.println("Instance Type: " + config.instanceType);
        }
        System.out.println("CPU: " + config.cpu);
        System.out.println("Memory: " + config.memory + " MB");
        System.out.println("Auth Mode: " + config.authMode);
        
        System.out.println("\n🔧 Advanced Configuration:");
        System.out.println("==========================");
        System.out.println("Monitoring Enabled: " + config.enableMonitoring);
        System.out.println("Encryption Enabled: " + config.enableEncryption);
        if (config.enableMonitoring) {
            System.out.println("Log Retention: " + config.logRetentionDays + " days");
        }
        
        System.out.println("\n🏥 Health Check Configuration:");
        System.out.println("==============================");
        System.out.println("Grace Period: " + config.healthCheckGracePeriod + " seconds");
        System.out.println("Interval: " + config.healthCheckInterval + " seconds");
        System.out.println("Timeout: " + config.healthCheckTimeout + " seconds");
        System.out.println("Healthy Threshold: " + config.healthyThreshold);
        System.out.println("Unhealthy Threshold: " + config.unhealthyThreshold);
        
        System.out.println("\n🌍 AWS Configuration:");
        System.out.println("=====================");
        System.out.println("Region: " + config.region);
    }
    
    // Configuration data class
    private static class DeploymentConfig {
        // Basic configuration
        String stackName;
        String environment;
        String deploymentType;
        
        // Domain configuration
        String domain;
        String subdomain;
        boolean enableSsl;
        
        // Runtime configuration
        RuntimeType runtime;
        TopologyType topology;
        SecurityProfile securityProfile;
        
        // Network configuration
        String networkMode;
        boolean wafEnabled;
        boolean cloudfrontEnabled;
        
        // Jenkins configuration
        int minInstanceCapacity = 1;
        int maxInstanceCapacity = 1;
        int cpuTargetUtilization = 60;
        int cpu = 1024;
        int memory = 2048;
        String instanceType = "t3.micro";  // EC2 instance type
        String authMode = "none";
        String ssoInstanceArn = "";
        String ssoGroupId = "";
        String ssoTargetAccountId = "";
        
        // Advanced configuration
        boolean enableMonitoring = true;
        boolean enableEncryption = true;
        String logRetentionDays = "7";
        String region = "us-east-1";
        String availabilityZone = "us-east-1a";
        boolean enableAutoScaling = false;
        int healthCheckGracePeriod = 300;
        int healthCheckInterval = 30;
        int healthCheckTimeout = 5;
        int healthyThreshold = 2;
        int unhealthyThreshold = 3;
    }
    
    // ============================================================================
    // DEPLOYMENT STRATEGY PATTERN - Easily expandable deployment types
    // ============================================================================
    
    /**
     * Interface for deployment strategies. Each deployment type implements this interface
     * to handle its specific configuration collection and deployment logic.
     */
    private interface DeploymentStrategy {
        void collectConfiguration(DeploymentConfig config);
        void deploy(SystemContext ctx, Stack stack, DeploymentConfig config);
        String getDescription();
    }
    
    /**
     * Jenkins deployment strategy using SystemContext orchestration layer.
     */
    private static class JenkinsDeploymentStrategy implements DeploymentStrategy {
        @Override
        public void collectConfiguration(DeploymentConfig config) {
            config.runtime = RuntimeType.valueOf(
                promptChoice("Runtime", new String[]{"FARGATE", "EC2"}, "FARGATE").toUpperCase());
            
            // Topology Selection
            config.topology = TopologyType.valueOf(
                promptChoice("Topology", new String[]{"JENKINS_SINGLE_NODE", "JENKINS_SERVICE"}, "JENKINS_SERVICE").toUpperCase());
            
            // Security Profile Selection
            config.securityProfile = SecurityProfile.valueOf(
                promptChoice("Security Profile", new String[]{"DEV", "STAGING", "PRODUCTION"}, "STAGING").toUpperCase());
            
            // Instance Capacity Configuration (applies to both EC2 and Fargate)
            config.minInstanceCapacity = promptIntWithValidation("Minimum Instance Capacity", 1, 1, 10);
            config.maxInstanceCapacity = promptIntWithValidation("Maximum Instance Capacity", 3, 1, 20);
            
            // Auto Scaling Configuration (applies to both runtimes)
            config.enableAutoScaling = config.maxInstanceCapacity > 1;
            if (config.enableAutoScaling) {
                System.out.println("✅ Auto Scaling enabled (max capacity > 1)");
                config.cpuTargetUtilization = promptIntWithValidation("CPU Target Utilization (%)", 60, 10, 90);
            } else {
                config.cpuTargetUtilization = 60; // Default when no auto-scaling
            }
            
            if (config.runtime == RuntimeType.EC2) {
                // EC2 Instance Type Selection
                config.instanceType = promptChoice("EC2 Instance Type", 
                    new String[]{"t3.micro", "t3.small", "t3.medium", "t3.large", "t3.xlarge", "t3.2xlarge"}, "t3.micro");
            }
            
            // Resource Configuration with Validation
            if (config.runtime == RuntimeType.FARGATE) {
                config.cpu = promptIntWithValidation("CPU (units)", 1024, 256, 4096);
                config.memory = promptIntWithValidation("Memory (MB)", 2048, 512, 8192);
            } else {
                config.cpu = promptIntWithValidation("CPU (units)", 1024, 256, 4096);
                config.memory = promptIntWithValidation("Memory (MB)", 2048, 512, 8192);
            }
            
            config.authMode = promptChoice("Authentication Mode", 
                new String[]{"none", "alb-oidc", "jenkins-oidc"}, "none");
            
            // Network Configuration
            System.out.println("\n🌐 Network Configuration:");
            System.out.println("==========================");
            config.networkMode = promptChoice("Network Mode", 
                new String[]{"public-no-nat", "private-with-nat"}, "public-no-nat");
            config.wafEnabled = promptYesNo("Enable WAF Protection", false);
            config.cloudfrontEnabled = promptYesNo("Enable CloudFront CDN", false);
            
            if (!config.authMode.equals("none")) {
                config.ssoInstanceArn = promptRequired("SSO Instance ARN", "");
                config.ssoGroupId = promptRequired("SSO Group ID", "");
                config.ssoTargetAccountId = promptRequired("SSO Target Account ID", "");
            }
            
            
            // Advanced Configuration Section
            System.out.println("\n🔧 Advanced Configuration:");
            System.out.println("==========================");
            
            config.enableMonitoring = promptYesNo("Enable CloudWatch Monitoring", true);
            config.enableEncryption = promptYesNo("Enable Encryption at Rest", true);
            
            if (config.enableMonitoring) {
                config.logRetentionDays = promptWithValidation("Log Retention (days)", "7", 
                    new String[]{"1", "3", "7", "14", "30", "60", "90", "120", "150", "180", "365"});
            }
            
            // Health Check Configuration
            System.out.println("\n🏥 Health Check Configuration:");
            System.out.println("==============================");
            config.healthCheckGracePeriod = promptIntWithValidation("Health Check Grace Period (seconds)", 300, 60, 900);
            config.healthCheckInterval = promptIntWithValidation("Health Check Interval (seconds)", 30, 5, 300);
            config.healthCheckTimeout = promptIntWithValidation("Health Check Timeout (seconds)", 5, 2, 60);
            config.healthyThreshold = promptIntWithValidation("Healthy Threshold Count", 2, 1, 10);
            config.unhealthyThreshold = promptIntWithValidation("Unhealthy Threshold Count", 3, 1, 10);
            
            // Region Configuration
            config.region = promptChoice("AWS Region", 
                new String[]{"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1"}, "us-east-1");
        }
        
        @Override
        public void deploy(SystemContext ctx, Stack stack, DeploymentConfig config) {
            System.out.println("🚀 Deploying Jenkins using SystemContext orchestration layer...");
            
            // Use SystemContext orchestration layer for Jenkins deployment
            SystemContext.JenkinsDeployment jenkinsDeployment = ctx.createJenkinsDeployment(stack, "Jenkins");
            
            System.out.println("✅ Jenkins deployment created successfully!");
            System.out.println("   - Infrastructure: VPC, ALB, EFS");
            System.out.println("   - Runtime: " + config.runtime);
            System.out.println("   - Topology: " + config.topology);
            if (config.domain != null && !config.domain.isEmpty()) {
                System.out.println("   - Domain: " + config.domain);
                if (config.enableSsl) {
                    System.out.println("   - SSL: Enabled");
                }
            }
        }
        
        @Override
        public String getDescription() {
            return "Jenkins CI/CD server with Fargate or EC2 runtime";
        }
    }
    
    /**
     * S3 Website deployment strategy (placeholder for future implementation).
     */
    private static class S3WebsiteDeploymentStrategy implements DeploymentStrategy {
        @Override
        public void collectConfiguration(DeploymentConfig config) {
            config.runtime = RuntimeType.FARGATE; // S3 websites don't use compute
            config.topology = TopologyType.S3_WEBSITE;
            
            System.out.println("⚠️  S3 Website deployment not yet implemented");
            System.out.println("   This will support static websites with S3 + CloudFront");
        }
        
        @Override
        public void deploy(SystemContext ctx, Stack stack, DeploymentConfig config) {
            System.out.println("🚀 S3 Website deployment not yet implemented");
            System.out.println("   This will use SystemContext.createS3CloudFrontDeployment()");
        }
        
        @Override
        public String getDescription() {
            return "Static website with S3 + CloudFront (Coming Soon)";
        }
    }
    
    /**
     * S3 Website + Mailer deployment strategy (placeholder for future implementation).
     */
    private static class S3WebsiteMailerDeploymentStrategy implements DeploymentStrategy {
        @Override
        public void collectConfiguration(DeploymentConfig config) {
            config.runtime = RuntimeType.FARGATE; // S3 websites don't use compute
            config.topology = TopologyType.S3_WEBSITE;
            
            System.out.println("⚠️  S3 Website + Mailer deployment not yet implemented");
            System.out.println("   This will support websites with S3 + CloudFront + SES + Lambda");
        }
        
        @Override
        public void deploy(SystemContext ctx, Stack stack, DeploymentConfig config) {
            System.out.println("🚀 S3 Website + Mailer deployment not yet implemented");
            System.out.println("   This will extend S3CloudFrontDeployment with SES + Lambda");
        }
        
        @Override
        public String getDescription() {
            return "Website + Mailer with S3 + CloudFront + SES + Lambda (Coming Soon)";
        }
    }
}
