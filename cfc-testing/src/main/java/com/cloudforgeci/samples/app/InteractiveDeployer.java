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
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Interactive CDK Deployer that prompts users for configuration and deploys infrastructure.
 * 
 * Uses the SystemContext orchestration layer for modular, expandable deployments:
 * - Jenkins (Fargate/EC2)
 * - S3 + CloudFront (Static Website) - Coming Soon
 * - S3 + CloudFront + SES + Lambda (Website + Mailer) - Coming Soon
 */
public class InteractiveDeployer {

    private static final Logger LOG = Logger.getLogger(InteractiveDeployer.class.getName());
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
        boolean forceInteractive = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--interactive") || args[i].equals("-i")) {
                forceInteractive = true;
                System.out.println("🎯 Interactive mode enabled");
            } else if (customStackName == null) {
                customStackName = args[i];
                System.out.println("📝 Using custom stack name: " + customStackName);
            } else if (deploymentOption == null) {
                deploymentOption = args[i];
                System.out.println("📝 Using deployment option: " + deploymentOption);
            }
        }

        try {
            // Check if we have a saved context file
            String contextFile = "deployment-context.json";

            if (!forceInteractive && Files.exists(Paths.get(contextFile))) {
                System.out.println("📁 Found saved deployment context, using it...");
                System.out.println("   (Use --interactive flag to reconfigure)");
                loadContextFromFileAndDeploy(contextFile, deploymentOption, customStackName);
            } else {
                if (forceInteractive && Files.exists(Paths.get(contextFile))) {
                    System.out.println("🔄 Ignoring saved context, starting fresh configuration...");
                }

                // No saved context, collect configuration interactively
                System.out.println("📝 No saved configuration found, starting interactive setup...");
                System.out.println("");
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
        LOG.info("Building CDK Context...");

        Map<String, Object> cfcContext = buildCfcContext(config);

        LOG.fine("CDK Context: runtime=" + cfcContext.get("runtime") +
                 ", topology=" + cfcContext.get("topology") +
                 ", stackName=" + cfcContext.get("stackName"));
        
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
        LOG.fine("DeploymentContext loaded: runtime=" + cfc.runtime() +
                 ", topology=" + cfc.topology() +
                 ", stackName=" + cfc.stackName() +
                 ", region=" + cfc.region());
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(config.securityProfile);

        // Use region from DeploymentContext (deployment-context.json) with fallback to environment variable
        String region = cfc.region() != null ? cfc.region() :
                        System.getenv("CDK_DEFAULT_REGION") != null ? System.getenv("CDK_DEFAULT_REGION") : "us-east-1";

        String account = System.getenv("CDK_DEFAULT_ACCOUNT") != null ? System.getenv("CDK_DEFAULT_ACCOUNT") : "123456789012";

        LOG.info("Using AWS environment: account=" + account + ", region=" + region);

        StackProps props = StackProps.builder()
            .env(Environment.builder()
                .account(account)
                .region(region)
                .build())
            .build();
        
        // Create stacks based on runtime type (like CloudForgeCommunitySample)
        LOG.info("Creating stack for runtime: " + config.runtime + " with name: " + config.stackName);
        if (config.runtime == RuntimeType.EC2) {
            LOG.fine("Creating JenkinsEc2Stack");
            new JenkinsEc2Stack(app, config.stackName, props, config.securityProfile, iamProfile);
        } else if (config.runtime == RuntimeType.FARGATE) {
            LOG.fine("Creating JenkinsFargateStack");
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

                Object value = entry.getValue();
                String formattedValue;

                // Handle boolean and numeric types without quotes
                if (value instanceof Boolean) {
                    formattedValue = value.toString();  // true or false without quotes
                } else if (value instanceof Number) {
                    formattedValue = value.toString();  // numbers without quotes
                } else {
                    // String values with quotes
                    formattedValue = "\"" + value + "\"";
                }

                writer.write("    \"" + entry.getKey() + "\": " + formattedValue);
                first = false;
            }
            writer.write("\n  }\n");
            writer.write("}\n");
            writer.close();
            LOG.info("Deployment context saved to deployment-context.json");
        } catch (IOException e) {
            LOG.warning("Could not save context file: " + e.getMessage());
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

        LOG.fine("Extracted context: stackName=" + stackName +
                 ", runtime=" + runtime +
                 ", topology=" + topology +
                 ", securityProfile=" + securityProfile);
        
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
        
        // Set other required fields with defaults or from saved context
        config.environment = extractValue(content, "env");
        if (config.environment == null) config.environment = "dev";

        config.tier = extractValue(content, "tier");
        if (config.tier == null) config.tier = "public";

        config.deploymentType = "jenkins";

        // Network configuration - read from saved context
        config.networkMode = extractValue(content, "networkMode");
        if (config.networkMode == null) config.networkMode = "public-no-nat";

        String wafEnabledStr = extractValue(content, "wafEnabled");
        config.wafEnabled = "true".equalsIgnoreCase(wafEnabledStr);

        String cloudfrontEnabledStr = extractValue(content, "cloudfrontEnabled");
        config.cloudfrontEnabled = "true".equalsIgnoreCase(cloudfrontEnabledStr);

        config.authMode = extractValue(content, "authMode");
        if (config.authMode == null) config.authMode = "none";

        // Extract Cognito configuration from saved context
        String cognitoAutoProvisionStr = extractValue(content, "cognitoAutoProvision");
        config.cognitoAutoProvision = "true".equalsIgnoreCase(cognitoAutoProvisionStr);
        config.cognitoUserPoolName = extractValue(content, "cognitoUserPoolName");
        config.cognitoDomainPrefix = extractValue(content, "cognitoDomainPrefix");
        String cognitoMfaStr = extractValue(content, "cognitoMfaEnabled");
        config.cognitoMfaEnabled = "true".equalsIgnoreCase(cognitoMfaStr);
        String cognitoGroupsStr = extractValue(content, "cognitoCreateGroups");
        config.cognitoCreateGroups = "true".equalsIgnoreCase(cognitoGroupsStr);
        config.cognitoAdminGroupName = extractValue(content, "cognitoAdminGroupName");
        if (config.cognitoAdminGroupName == null) config.cognitoAdminGroupName = "Jenkins-Admins";
        config.cognitoUserGroupName = extractValue(content, "cognitoUserGroupName");
        if (config.cognitoUserGroupName == null) config.cognitoUserGroupName = "Jenkins-Users";
        config.cognitoInitialAdminEmail = extractValue(content, "cognitoInitialAdminEmail");
        config.cognitoUserPoolId = extractValue(content, "cognitoUserPoolId");
        config.cognitoAppClientId = extractValue(content, "cognitoAppClientId");

        // Extract OIDC configuration from saved context
        // Manual OIDC endpoints (for IAM Identity Center, Okta, Auth0, etc.)
        config.oidcIssuer = extractValue(content, "oidcIssuer");
        config.oidcAuthorizationEndpoint = extractValue(content, "oidcAuthorizationEndpoint");
        config.oidcTokenEndpoint = extractValue(content, "oidcTokenEndpoint");
        config.oidcUserInfoEndpoint = extractValue(content, "oidcUserInfoEndpoint");
        config.oidcClientId = extractValue(content, "oidcClientId");
        config.oidcClientSecretName = extractValue(content, "oidcClientSecretName");
        if (config.oidcClientSecretName == null) config.oidcClientSecretName = "jenkins/oidc/client-secret";

        // Legacy OIDC/Identity Center configuration
        String autoProvisionStr = extractValue(content, "autoProvisionIdentityCenter");
        config.autoProvisionIdentityCenter = "true".equalsIgnoreCase(autoProvisionStr);

        config.ssoInstanceArn = extractValue(content, "ssoInstanceArn");
        if (config.ssoInstanceArn == null) config.ssoInstanceArn = "";

        config.ssoGroupId = extractValue(content, "ssoGroupId");
        if (config.ssoGroupId == null) config.ssoGroupId = "";

        config.ssoTargetAccountId = extractValue(content, "ssoTargetAccountId");
        if (config.ssoTargetAccountId == null) config.ssoTargetAccountId = "";

        config.identityCenterGroupName = extractValue(content, "identityCenterGroupName");
        if (config.identityCenterGroupName == null) config.identityCenterGroupName = "Jenkins-Users";

        // Extract domain configuration from saved context
        config.domain = extractValue(content, "domain");
        if (config.domain == null) config.domain = "";

        config.subdomain = extractValue(content, "subdomain");
        if (config.subdomain == null) config.subdomain = "";

        String enableSslStr = extractValue(content, "enableSsl");
        config.enableSsl = "true".equalsIgnoreCase(enableSslStr);

        // CPU and Memory configuration - read from saved context
        String cpuStr = extractValue(content, "cpu");
        String memoryStr = extractValue(content, "memory");
        config.cpu = cpuStr != null ? Integer.parseInt(cpuStr) : 1024;
        config.memory = memoryStr != null ? Integer.parseInt(memoryStr) : 2048;

        // Instance capacity and auto-scaling - read from saved context
        String minCapacityStr = extractValue(content, "minInstanceCapacity");
        String maxCapacityStr = extractValue(content, "maxInstanceCapacity");
        String cpuTargetStr = extractValue(content, "cpuTargetUtilization");
        String enableAutoScalingStr = extractValue(content, "enableAutoScaling");

        config.minInstanceCapacity = minCapacityStr != null ? Integer.parseInt(minCapacityStr) : 1;
        config.maxInstanceCapacity = maxCapacityStr != null ? Integer.parseInt(maxCapacityStr) : 1;
        config.cpuTargetUtilization = cpuTargetStr != null ? Integer.parseInt(cpuTargetStr) : 60;
        config.enableAutoScaling = "true".equalsIgnoreCase(enableAutoScalingStr);

        // EC2 instance type - read from saved context
        if (config.runtime == RuntimeType.EC2) {
            config.instanceType = extractValue(content, "instanceType");
            if (config.instanceType == null) config.instanceType = "t3.micro";
        }

        // Advanced configuration - read from saved context
        String enableMonitoringStr = extractValue(content, "enableMonitoring");
        config.enableMonitoring = enableMonitoringStr == null || "true".equalsIgnoreCase(enableMonitoringStr);

        String enableEncryptionStr = extractValue(content, "enableEncryption");
        config.enableEncryption = enableEncryptionStr == null || "true".equalsIgnoreCase(enableEncryptionStr);

        String awsConfigEnabledStr = extractValue(content, "awsConfigEnabled");
        config.awsConfigEnabled = "true".equalsIgnoreCase(awsConfigEnabledStr);

        String createConfigInfrastructureStr = extractValue(content, "createConfigInfrastructure");
        config.createConfigInfrastructure = createConfigInfrastructureStr == null || "true".equalsIgnoreCase(createConfigInfrastructureStr);

        String guardDutyEnabledStr = extractValue(content, "guardDutyEnabled");
        config.guardDutyEnabled = "true".equalsIgnoreCase(guardDutyEnabledStr);

        String auditManagerEnabledStr = extractValue(content, "auditManagerEnabled");
        config.auditManagerEnabled = "true".equalsIgnoreCase(auditManagerEnabledStr);

        config.auditManagerFrameworkId = extractValue(content, "auditManagerFrameworkId");

        config.complianceFrameworks = extractValue(content, "complianceFrameworks");
        if (config.complianceFrameworks == null) config.complianceFrameworks = "";

        config.logRetentionDays = extractValue(content, "logRetentionDays");
        if (config.logRetentionDays == null) config.logRetentionDays = "7";

        config.region = extractValue(content, "region");
        if (config.region == null) config.region = "us-east-1";

        // Health check configuration - read from saved context
        String healthCheckGracePeriodStr = extractValue(content, "healthCheckGracePeriod");
        config.healthCheckGracePeriod = healthCheckGracePeriodStr != null ? Integer.parseInt(healthCheckGracePeriodStr) : 300;

        String healthCheckIntervalStr = extractValue(content, "healthCheckInterval");
        config.healthCheckInterval = healthCheckIntervalStr != null ? Integer.parseInt(healthCheckIntervalStr) : 30;

        String healthCheckTimeoutStr = extractValue(content, "healthCheckTimeout");
        config.healthCheckTimeout = healthCheckTimeoutStr != null ? Integer.parseInt(healthCheckTimeoutStr) : 5;

        String healthyThresholdStr = extractValue(content, "healthyThreshold");
        config.healthyThreshold = healthyThresholdStr != null ? Integer.parseInt(healthyThresholdStr) : 2;

        String unhealthyThresholdStr = extractValue(content, "unhealthyThreshold");
        config.unhealthyThreshold = unhealthyThresholdStr != null ? Integer.parseInt(unhealthyThresholdStr) : 3;

        // Infrastructure configuration - read from saved context
        config.bastionCidr = extractValue(content, "bastionCidr");
        if (config.bastionCidr == null) config.bastionCidr = "10.0.1.0/24";

        config.lbType = extractValue(content, "lbType");
        if (config.lbType == null) config.lbType = "alb";

        String enableFlowlogsStr = extractValue(content, "enableFlowlogs");
        config.enableFlowlogs = "true".equalsIgnoreCase(enableFlowlogsStr);

        String createZoneStr = extractValue(content, "createZone");
        config.createZone = "true".equalsIgnoreCase(createZoneStr);

        config.artifactsPrefix = extractValue(content, "artifactsPrefix");
        if (config.artifactsPrefix == null) config.artifactsPrefix = "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}";

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
        // Try quoted values first (strings) - use * instead of + to allow empty strings
        String quotedPattern = "\"" + key + "\":\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(quotedPattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        // Try unquoted values (booleans and numbers)
        String unquotedPattern = "\"" + key + "\":\\s*([^,}\\s]+)";
        p = java.util.regex.Pattern.compile(unquotedPattern);
        m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }
    
    /**
     * Builds the CFC context map from DeploymentConfig using Jackson.
     * This eliminates manual field mapping and automatically includes all DeploymentConfig fields.
     * Jackson introspection ensures we can't accidentally forget to include a field.
     */
    private static Map<String, Object> buildCfcContext(DeploymentConfig config) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Configure mapper to use fields instead of getters (DeploymentConfig has public fields)
        mapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
                           com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.GETTER,
                           com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE);

        // Configure mapper to handle enums as strings
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_ENUMS_USING_TO_STRING);

        // Configure mapper to exclude null values
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        // Convert DeploymentConfig to Map - Jackson handles all fields automatically
        @SuppressWarnings("unchecked")
        Map<String, Object> context = mapper.convertValue(config, Map.class);

        // Rename 'environment' field to 'env' to match DeploymentContext expectations
        if (context.containsKey("environment")) {
            context.put("env", context.remove("environment"));
        }

        return context;
    }
    
    // Utility methods for user input
    private static String promptRequired(String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        System.out.flush();

        try {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                return input.isEmpty() ? defaultValue : input;
            } else {
                System.out.println();
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.out.println();
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
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
                System.out.println();
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.out.println();
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
                    // Try to match by name
                    for (String choice : choices) {
                        if (choice.equalsIgnoreCase(input)) {
                            return choice;
                        }
                    }
                }

                System.out.println("Invalid choice, using default: " + defaultValue);
                return defaultValue;
            } else {
                System.out.println();
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.out.println();
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
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
                System.out.println();
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.out.println();
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static int promptInt(String prompt, int defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        System.out.flush();

        try {
            if (scanner.hasNextLine()) {
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
            } else {
                System.out.println();
                System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            System.out.println();
            System.err.println("⚠️  Error reading input, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static int promptIntWithValidation(String prompt, int defaultValue, int min, int max) {
        while (true) {
            System.out.print(prompt + " [" + defaultValue + "] (range: " + min + "-" + max + "): ");
            System.out.flush();

            try {
                if (!scanner.hasNextLine()) {
                    System.out.println();
                    System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                    return defaultValue;
                }

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
                System.out.println();
                System.err.println("⚠️  Error reading input, using default: " + defaultValue);
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
                    System.out.println();
                    System.err.println("⚠️  Warning: No interactive console available. Using default value: " + defaultValue);
                    return defaultValue;
                }

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) return defaultValue;

                for (String option : validOptions) {
                    if (option.equalsIgnoreCase(input)) {
                        return option.toLowerCase();
                    }
                }

                System.out.println("❌ Invalid option. Valid options: " + String.join(", ", validOptions));
            } catch (Exception e) {
                System.out.println();
                System.err.println("⚠️  Error reading input, using default: " + defaultValue);
                return defaultValue;
            }
        }
    }

    /**
     * Get availability zones for a region.
     * Returns an array of AZ suffixes based on the region and count requested.
     */
    private static String[] getAvailabilityZonesForRegion(String region, int count) {
        // Most regions have at least 3 AZs (a, b, c)
        // Some regions have more (d, e, f)
        String[] allAzs = {
            region + "a",
            region + "b",
            region + "c",
            region + "d",
            region + "e",
            region + "f"
        };

        // Return the requested number of AZs (up to what's available)
        int actualCount = Math.min(count, allAzs.length);
        String[] result = new String[actualCount];
        System.arraycopy(allAzs, 0, result, 0, actualCount);
        return result;
    }

    /**
     * Display compliance requirements for selected frameworks.
     */
    private static void displayComplianceRequirements(String frameworks) {
        String[] frameworkList = frameworks.split(",");

        for (String framework : frameworkList) {
            String fw = framework.trim();
            System.out.println("\n  " + fw + " Requirements:");

            switch (fw) {
                case "PCI-DSS":
                    System.out.println("    ✓ Encryption at rest (enableEncryption=true)");
                    System.out.println("    ✓ WAF protection (wafEnabled=true)");
                    System.out.println("    ✓ GuardDuty intrusion detection (guardDutyEnabled=true)");
                    System.out.println("    ✓ CloudWatch monitoring (enableMonitoring=true)");
                    System.out.println("    ✓ Vulnerability scanning with Inspector");
                    System.out.println("    ⚠ Quarterly vulnerability scans required");
                    System.out.println("    ⚠ File integrity monitoring recommended");
                    break;

                case "HIPAA":
                    System.out.println("    ✓ Encryption at rest (enableEncryption=true)");
                    System.out.println("    ✓ AWS Config compliance (awsConfigEnabled=true)");
                    System.out.println("    ✓ CloudWatch monitoring (enableMonitoring=true)");
                    System.out.println("    ✓ Macie for PHI discovery (macieEnabled=true)");
                    System.out.println("    ⚠ AWS Business Associate Agreement (BAA) required");
                    System.out.println("    ⚠ Breach notification procedures documented");
                    System.out.println("    ⚠ Workforce authorization procedures in place");
                    break;

                case "SOC2":
                    System.out.println("    ✓ Encryption at rest (enableEncryption=true)");
                    System.out.println("    ✓ CloudWatch monitoring (enableMonitoring=true)");
                    System.out.println("    ✓ AWS Config compliance (awsConfigEnabled=true)");
                    System.out.println("    ✓ Change management documented");
                    System.out.println("    ⚠ Incident response plan documented");
                    System.out.println("    ⚠ Disaster recovery plan tested");
                    break;

                case "GDPR":
                    System.out.println("    ✓ Encryption at rest (enableEncryption=true)");
                    System.out.println("    ✓ CloudWatch monitoring (enableMonitoring=true)");
                    System.out.println("    ⚠ Data Protection Agreement (DPA) required");
                    System.out.println("    ⚠ Data Protection Impact Assessment (DPIA) completed");
                    System.out.println("    ⚠ Data subject request procedures documented");
                    System.out.println("    ⚠ Legal basis for data processing documented");
                    System.out.println("    ⚠ International transfer safeguards if applicable");
                    break;

                default:
                    System.out.println("    See docs/compliance/ for detailed requirements");
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
        System.out.println("AWS Config Enabled: " + config.awsConfigEnabled);
        if (config.awsConfigEnabled) {
            System.out.println("  Create Config Infrastructure: " + config.createConfigInfrastructure);
            if (config.createConfigInfrastructure) {
                System.out.println("    ⚠️  This stack will CREATE Config Recorder and Delivery Channel (account-level singletons)");
                System.out.println("    ⚠️  Only ONE stack per region should have createConfigInfrastructure=true");
            } else {
                System.out.println("    ℹ️  This stack will USE existing Config Recorder and Delivery Channel");
                System.out.println("    ℹ️  Ensure another stack has already created the Config infrastructure in this region");
            }
        }
        System.out.println("Audit Manager Enabled: " + config.auditManagerEnabled);
        if (config.auditManagerEnabled && config.auditManagerFrameworkId != null) {
            System.out.println("  Framework ID: " + config.auditManagerFrameworkId);
        }
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
    static class DeploymentConfig {
        // Basic configuration
        String stackName;
        String environment;
        String deploymentType;
        String tier = "public";

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

        // Region and Availability Zone configuration
        String[] availabilityZones;
        
        // Jenkins configuration
        int minInstanceCapacity = 1;
        int maxInstanceCapacity = 1;
        int cpuTargetUtilization = 60;
        int cpu = 1024;
        int memory = 2048;
        String instanceType = "t3.micro";  // EC2 instance type
        String authMode = "none";

        // Cognito configuration (recommended for OIDC)
        boolean cognitoAutoProvision = false;
        String cognitoUserPoolName = null;
        String cognitoDomainPrefix = null;
        boolean cognitoMfaEnabled = false;
        boolean cognitoCreateGroups = true;
        String cognitoAdminGroupName = "Jenkins-Admins";
        String cognitoUserGroupName = "Jenkins-Users";
        String cognitoInitialAdminEmail = null;
        String cognitoInitialAdminPhone = null;
        String cognitoUserPoolId = null;
        String cognitoAppClientId = null;

        // OIDC configuration - manual endpoints (for IAM Identity Center, Okta, Auth0)
        String oidcIssuer = null;
        String oidcAuthorizationEndpoint = null;
        String oidcTokenEndpoint = null;
        String oidcUserInfoEndpoint = null;
        String oidcClientId = null;
        String oidcClientSecretName = "jenkins/oidc/client-secret";

        // OIDC configuration - legacy auto-constructed
        String ssoInstanceArn = "";
        String ssoGroupId = "";
        String ssoTargetAccountId = "";
        boolean autoProvisionIdentityCenter = false;
        String identityCenterGroupName = "Jenkins-Users";

        // Advanced configuration
        boolean enableMonitoring = true;
        boolean enableEncryption = true;
        boolean awsConfigEnabled = false;
        boolean createConfigInfrastructure = true;  // Only true for first stack per region
        boolean guardDutyEnabled = false;
        boolean auditManagerEnabled = false;
        String auditManagerFrameworkId = null;
        String complianceFrameworks = "";  // Comma-separated list: "PCI-DSS,HIPAA,SOC2,GDPR"
        String logRetentionDays = "7";
        String region = "us-east-1";
        boolean enableAutoScaling = false;
        int healthCheckGracePeriod = 300;
        int healthCheckInterval = 30;
        int healthCheckTimeout = 5;
        int healthyThreshold = 2;
        int unhealthyThreshold = 3;

        // Infrastructure configuration
        String bastionCidr = "10.0.1.0/24";
        String lbType = "alb";
        boolean enableFlowlogs = false;

        // Storage persistence configuration
        boolean retainStorage = false;
        String existingFileSystemId = null;
        boolean createZone = false;
        String artifactsPrefix = "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}";
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
            // Security Profile Selection (moved to top - determines many downstream defaults)
            System.out.println("\n🔒 Security Profile Selection:");
            System.out.println("================================");
            System.out.println("Security profiles determine compliance requirements and defaults:");
            System.out.println("  • DEV: Relaxed security, minimal compliance, lower costs");
            System.out.println("  • STAGING: Moderate security, recommended for pre-production testing");
            System.out.println("  • PRODUCTION: Strict security, full compliance enforcement");
            System.out.println("");
            config.securityProfile = SecurityProfile.valueOf(
                promptChoice("Security Profile", new String[]{"DEV", "STAGING", "PRODUCTION"}, "STAGING").toUpperCase());

            // Runtime Selection
            System.out.println("\n⚙️ Runtime Configuration:");
            System.out.println("========================");
            config.runtime = RuntimeType.valueOf(
                promptChoice("Runtime", new String[]{"FARGATE", "EC2"}, "FARGATE").toUpperCase());

            // Topology Selection
            config.topology = TopologyType.valueOf(
                promptChoice("Topology", new String[]{"JENKINS_SINGLE_NODE", "JENKINS_SERVICE"}, "JENKINS_SERVICE").toUpperCase());
            
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
            
            // Authentication Configuration
            System.out.println("\n🔐 Authentication Configuration:");
            System.out.println("=================================");
            config.authMode = promptChoice("Authentication Mode",
                new String[]{"none", "alb-oidc", "jenkins-oidc"}, "none");

            if (config.authMode.equals("alb-oidc") || config.authMode.equals("jenkins-oidc")) {
                System.out.println("\n📋 OIDC Authentication Setup:");
                System.out.println("=============================");
                System.out.println("Choose OIDC provider:");
                System.out.println("  1. Amazon Cognito (Recommended) - Auto-provision user pool with strong security");
                System.out.println("  2. AWS IAM Identity Center - Manual configuration with Identity Center application");
                System.out.println("  3. External IdP (Okta, Auth0, etc.) - Manual OIDC endpoints");
                System.out.println("  4. Legacy SSO - Auto-constructed from SSO instance ARN (not recommended)");

                String oidcProvider = promptChoice("OIDC Provider",
                    new String[]{"cognito", "identity-center", "external-idp", "legacy-sso"}, "cognito");

                if (oidcProvider.equals("cognito")) {
                    System.out.println("\n🔐 Amazon Cognito Configuration:");
                    System.out.println("=================================");

                    boolean useCognito = promptYesNo("Auto-provision new Cognito User Pool", true);

                    if (useCognito) {
                        config.cognitoAutoProvision = true;

                        // Domain prefix is required and must be globally unique
                        System.out.println("\n⚠️  Domain prefix must be globally unique across ALL AWS accounts");
                        System.out.println("   Example: jenkins-auth-mycompany-prod");
                        config.cognitoDomainPrefix = promptRequired("Cognito Domain Prefix (globally unique)",
                            config.stackName + "-auth");

                        // Optional customization
                        config.cognitoUserPoolName = promptOptional("User Pool Name", config.stackName + "-users");
                        config.cognitoMfaEnabled = promptYesNo("Enable MFA (Multi-Factor Authentication)", false);

                        // User Groups Configuration
                        System.out.println("\n👥 User Groups Configuration:");
                        config.cognitoCreateGroups = promptYesNo("Create admin and user groups", true);

                        if (config.cognitoCreateGroups) {
                            config.cognitoAdminGroupName = promptOptional("Admin Group Name", "Jenkins-Admins");
                            config.cognitoUserGroupName = promptOptional("User Group Name", "Jenkins-Users");
                        }

                        // Initial Admin User - ALWAYS ask (independent of groups)
                        System.out.println("\n👤 Initial Admin User:");
                        System.out.println("Create an admin user automatically during deployment");
                        boolean createInitialAdmin = promptYesNo("Create initial admin user", true);
                        if (createInitialAdmin) {
                            config.cognitoInitialAdminEmail = promptRequired("Admin email address", "");

                            // If MFA is enabled and includes SMS, ask for phone number
                            if (config.cognitoMfaEnabled) {
                                System.out.println("\nℹ️  MFA is enabled - phone number required for SMS MFA");
                                config.cognitoInitialAdminPhone = promptOptional("Admin phone number (E.164 format, e.g., +12025551234)", "");
                            }

                            System.out.println("   ✅ Admin user will be created with temporary password");
                            System.out.println("   📧 User will receive email with password reset instructions");
                            if (config.cognitoCreateGroups) {
                                System.out.println("   🔑 User will be added to '" + config.cognitoAdminGroupName + "' group");
                            }
                        } else {
                            config.cognitoInitialAdminEmail = null;
                            config.cognitoInitialAdminPhone = null;
                        }

                        // Construct redirect URL for reference
                        String redirectUrl;
                        if (config.domain != null && !config.domain.isEmpty()) {
                            if (config.subdomain != null && !config.subdomain.isEmpty()) {
                                redirectUrl = "https://" + config.subdomain + "." + config.domain + "/oauth2/idpresponse";
                            } else {
                                redirectUrl = "https://" + config.domain + "/oauth2/idpresponse";
                            }
                        } else {
                            redirectUrl = "https://<ALB-DNS-NAME>/oauth2/idpresponse";
                        }

                        System.out.println("\n✅ Cognito auto-provisioning configured");
                        System.out.println("📋 After deployment:");
                        System.out.println("   1. Get User Pool ID and Client ID from CloudFormation outputs");
                        System.out.println("   2. Retrieve client secret:");
                        System.out.println("      aws cognito-idp describe-user-pool-client --user-pool-id <POOL_ID> --client-id <CLIENT_ID>");
                        System.out.println("   3. Store secret in Secrets Manager:");
                        System.out.println("      aws secretsmanager put-secret-value --secret-id jenkins/oidc/client-secret --secret-string '<SECRET>'");
                        System.out.println("   4. Create users:");
                        System.out.println("      aws cognito-idp admin-create-user --user-pool-id <POOL_ID> --username admin@example.com");
                        System.out.println("   Redirect URL will be: " + redirectUrl);
                    } else {
                        // Use existing Cognito User Pool
                        System.out.println("\n📋 Existing Cognito User Pool Configuration:");
                        config.cognitoUserPoolId = promptRequired("Cognito User Pool ID (e.g., us-east-1_abc123xyz)", "");
                        config.cognitoAppClientId = promptOptional("App Client ID (leave empty to create new)", "");
                        config.cognitoDomainPrefix = promptRequired("Cognito Domain Prefix", "");

                        System.out.println("\n✅ Existing Cognito configuration captured");
                    }

                } else if (oidcProvider.equals("identity-center")) {
                    System.out.println("\n📝 AWS IAM Identity Center Configuration:");
                    System.out.println("==========================================");
                    System.out.println("Prerequisites:");
                    System.out.println("  1. Create an OIDC application in AWS IAM Identity Center console");
                    System.out.println("  2. Select 'I have an application I want to set up' > 'OAuth 2.0'");

                    // Construct redirect URL based on domain configuration
                    String redirectUrl;
                    if (config.domain != null && !config.domain.isEmpty()) {
                        if (config.subdomain != null && !config.subdomain.isEmpty()) {
                            redirectUrl = "https://" + config.subdomain + "." + config.domain + "/oauth2/idpresponse";
                        } else {
                            redirectUrl = "https://" + config.domain + "/oauth2/idpresponse";
                        }
                    } else {
                        redirectUrl = "https://<ALB-DNS-NAME>/oauth2/idpresponse (ALB DNS name will be available after deployment)";
                    }

                    System.out.println("  3. Configure redirect URL: " + redirectUrl);
                    System.out.println("  4. Copy the OIDC endpoints and client ID from the application");
                    System.out.println("");

                    config.oidcIssuer = promptRequired("OIDC Issuer URL", "");
                    config.oidcAuthorizationEndpoint = promptRequired("Authorization Endpoint URL", "");
                    config.oidcTokenEndpoint = promptRequired("Token Endpoint URL", "");
                    config.oidcUserInfoEndpoint = promptRequired("UserInfo Endpoint URL", "");
                    config.oidcClientId = promptRequired("Client ID", "");
                    config.oidcClientSecretName = promptOptional("Client Secret Name in Secrets Manager", "jenkins/oidc/client-secret");

                    System.out.println("\n✅ IAM Identity Center configuration captured");
                    System.out.println("⚠️  IMPORTANT: After deployment, update the client secret in Secrets Manager:");
                    System.out.println("   aws secretsmanager put-secret-value --secret-id " + config.oidcClientSecretName + " --secret-string 'YOUR_CLIENT_SECRET'");

                } else if (oidcProvider.equals("external-idp")) {
                    System.out.println("\n📝 External Identity Provider Configuration (Okta, Auth0, etc.):");
                    System.out.println("==================================================================");
                    System.out.println("Configure your IdP application with:");

                    // Construct redirect URL based on domain configuration
                    String redirectUrl;
                    if (config.domain != null && !config.domain.isEmpty()) {
                        if (config.subdomain != null && !config.subdomain.isEmpty()) {
                            redirectUrl = "https://" + config.subdomain + "." + config.domain + "/oauth2/idpresponse";
                        } else {
                            redirectUrl = "https://" + config.domain + "/oauth2/idpresponse";
                        }
                    } else {
                        redirectUrl = "https://<ALB-DNS-NAME>/oauth2/idpresponse";
                    }

                    System.out.println("  Redirect URL: " + redirectUrl);
                    System.out.println("  Grant Type: Authorization Code");
                    System.out.println("  Scopes: openid email profile");
                    System.out.println("");

                    config.oidcIssuer = promptRequired("OIDC Issuer URL", "");
                    config.oidcAuthorizationEndpoint = promptRequired("Authorization Endpoint URL", "");
                    config.oidcTokenEndpoint = promptRequired("Token Endpoint URL", "");
                    config.oidcUserInfoEndpoint = promptRequired("UserInfo Endpoint URL", "");
                    config.oidcClientId = promptRequired("Client ID", "");
                    config.oidcClientSecretName = promptOptional("Client Secret Name in Secrets Manager", "jenkins/oidc/client-secret");

                    System.out.println("\n✅ External IdP configuration captured");
                    System.out.println("⚠️  Store client secret in Secrets Manager:");
                    System.out.println("   aws secretsmanager create-secret --name " + config.oidcClientSecretName + " --secret-string 'YOUR_CLIENT_SECRET'");

                } else {
                    // Legacy SSO mode
                    System.out.println("\n⚠️  Legacy Auto-Constructed OIDC Configuration:");
                    System.out.println("===============================================");
                    System.out.println("This mode auto-constructs OIDC endpoints from the SSO instance ARN.");
                    System.out.println("This may not work with all IAM Identity Center configurations.");
                    System.out.println("⚠️  RECOMMENDED: Use option 1 (Cognito) or 2 (Identity Center) instead");
                    System.out.println("");

                    config.ssoInstanceArn = promptRequired("SSO Instance ARN (e.g., ssoins-xxxxxxxxxxxx)", "");

                    System.out.println("\n✅ Legacy configuration captured");
                    System.out.println("⚠️  If this doesn't work, use Cognito or manual OIDC endpoints instead");
                }
            }

            // Network Configuration
            System.out.println("\n🌐 Network Configuration:");
            System.out.println("==========================");
            config.networkMode = promptChoice("Network Mode",
                new String[]{"public-no-nat", "private-with-nat"}, "public-no-nat");
            config.wafEnabled = promptYesNo("Enable WAF Protection", false);
            config.cloudfrontEnabled = promptYesNo("Enable CloudFront CDN", false);

            // Bastion/Remote Access Configuration
            boolean enableRemoteAccess = promptYesNo("Enable Remote Shell Access (ECS Exec for Fargate)", false);
            if (enableRemoteAccess) {
                // Default CIDR varies by security profile: tighter for production, more permissive for dev
                String defaultCidr = (config.securityProfile == SecurityProfile.PRODUCTION) ? "10.0.0.0/24" : "0.0.0.0/32";
                config.bastionCidr = promptOptional("Bastion/Access CIDR (your IP/32 or VPN CIDR)", defaultCidr);
            } else {
                config.bastionCidr = null;  // Disable ECS Exec
            }

            // Storage Persistence Configuration
            System.out.println("\n💾 Storage Persistence Configuration:");
            System.out.println("======================================");
            System.out.println("ℹ️  Retain storage volumes (EFS/EBS) after stack deletion for disaster recovery.");
            System.out.println("    This allows you to destroy and redeploy with all data intact.");
            config.retainStorage = promptYesNo("Retain storage volumes on stack deletion", false);

            if (config.retainStorage) {
                System.out.println("⚠️  IMPORTANT: You must manually delete retained volumes from AWS Console.");
                System.out.println("    Retained volumes will continue to incur storage costs.");
            }

            // Ask about reusing existing file system (for disaster recovery)
            boolean reuseExisting = promptYesNo("Reuse existing file system from previous deployment", false);
            if (reuseExisting) {
                config.existingFileSystemId = promptOptional("Existing File System ID (fs-xxxxxx)", null);
            } else {
                config.existingFileSystemId = null;
            }

            // Advanced Configuration Section
            System.out.println("\n🔧 Advanced Monitoring & Security Configuration:");
            System.out.println("=================================================");
            System.out.println("📖 These settings are essential for compliance frameworks (PCI-DSS, HIPAA, SOC2, GDPR)");
            System.out.println("   For compliance requirements, see: docs/compliance/QUICK_START_GUIDE.md");
            System.out.println("");

            config.enableMonitoring = promptYesNo("Enable CloudWatch Monitoring (required for most compliance frameworks)", true);
            config.enableEncryption = promptYesNo("Enable Encryption at Rest (required for PCI-DSS, HIPAA, GDPR)", true);

            // AWS Config - only ask about infrastructure if monitoring is enabled
            config.awsConfigEnabled = promptYesNo("Enable AWS Config Compliance Monitoring (recommended for PRODUCTION)",
                config.securityProfile == SecurityProfile.PRODUCTION);

            // AWS Config infrastructure prompt (only if Config is enabled)
            if (config.awsConfigEnabled) {
                System.out.println("\n📋 AWS Config Infrastructure Setup:");
                System.out.println("AWS Config has account-level singleton resources (Recorder + Delivery Channel).");
                System.out.println("Only ONE stack per region should create these resources.");
                System.out.println("  ✓ If this is your FIRST stack in this region: Answer YES");
                System.out.println("  ✗ If another stack already created Config infrastructure: Answer NO");
                config.createConfigInfrastructure = promptYesNo("Create Config Infrastructure (Recorder + Delivery Channel)", true);
            } else {
                config.createConfigInfrastructure = true; // Default to true if not prompted
            }

            // GuardDuty prompt (account-level threat detection) - only for PRODUCTION/STAGING
            if (config.securityProfile == SecurityProfile.PRODUCTION || config.securityProfile == SecurityProfile.STAGING) {
                System.out.println("\n🛡️ AWS GuardDuty - Threat Detection:");
                System.out.println("====================================");
                System.out.println("Continuous monitoring for malicious activity and unauthorized behavior");
                System.out.println("  • Cost: ~$30-100/month (based on data volume)");
                System.out.println("  • Compliance: Required for PCI-DSS Req 11.4 (intrusion detection)");
                System.out.println("  • Recommended: PRODUCTION environments");
                config.guardDutyEnabled = promptYesNo("Enable AWS GuardDuty",
                    config.securityProfile == SecurityProfile.PRODUCTION);
            } else {
                config.guardDutyEnabled = false;
            }

            // Compliance Framework Selection - only for PRODUCTION/STAGING
            if (config.securityProfile == SecurityProfile.PRODUCTION || config.securityProfile == SecurityProfile.STAGING) {
                System.out.println("\n📜 Compliance Framework Validation:");
                System.out.println("===================================");
                System.out.println("Enable continuous compliance validation during CDK synthesis");
                System.out.println("  • Validates infrastructure against compliance requirements");
                System.out.println("  • Prevents deployment of non-compliant configurations");
                System.out.println("  • Documentation: docs/compliance/QUICK_START_GUIDE.md");
                System.out.println("");

                config.auditManagerEnabled = promptYesNo("Enable Compliance Framework Validation",
                    config.securityProfile == SecurityProfile.PRODUCTION);

                // Only ask about frameworks if Audit Manager is enabled
                if (config.auditManagerEnabled) {
                    System.out.println("\n📋 Select Compliance Frameworks:");
                    System.out.println("================================");
                    System.out.println("Choose which compliance frameworks to validate against:");
                    System.out.println("");
                    System.out.println("  1. All Standard Frameworks (PCI-DSS, HIPAA, SOC2, GDPR)");
                    System.out.println("     Coverage: 70% overall | Cost: ~$150-300/month");
                    System.out.println("");
                    System.out.println("  2. SOC 2 Only (Trust & Transparency)");
                    System.out.println("     Coverage: 94% | Cost: Minimal | Best for: SaaS applications");
                    System.out.println("");
                    System.out.println("  3. HIPAA Only (Healthcare)");
                    System.out.println("     Coverage: 68% | Requires: BAA, PHI discovery, breach notification");
                    System.out.println("");
                    System.out.println("  4. PCI-DSS Only (Payment Processing)");
                    System.out.println("     Coverage: 73% | Requires: WAF, GuardDuty, vulnerability scanning");
                    System.out.println("");
                    System.out.println("  5. GDPR Only (Data Protection)");
                    System.out.println("     Coverage: 65% | Requires: DPA, DPIA, data subject rights");
                    System.out.println("");
                    System.out.println("  6. Healthcare Focused (HIPAA + SOC2 + GDPR)");
                    System.out.println("     Coverage: 68% | Best for: Healthcare applications with PHI");
                    System.out.println("");
                    System.out.println("  7. Payment Processing (PCI-DSS + SOC2)");
                    System.out.println("     Coverage: 73% | Best for: E-commerce platforms");
                    System.out.println("");
                    System.out.println("  8. Custom (enter comma-separated list)");
                    System.out.println("");

                    String choice = promptWithValidation("Framework(s)", "1", new String[]{"1", "2", "3", "4", "5", "6", "7", "8"});

                    config.complianceFrameworks = switch (choice) {
                        case "1" -> "PCI-DSS,HIPAA,SOC2,GDPR";
                        case "2" -> "SOC2";
                        case "3" -> "HIPAA";
                        case "4" -> "PCI-DSS";
                        case "5" -> "GDPR";
                        case "6" -> "HIPAA,SOC2,GDPR";
                        case "7" -> "PCI-DSS,SOC2";
                        case "8" -> {
                            String custom = promptOptional("Frameworks (comma-separated, e.g., PCI-DSS,HIPAA)", "");
                            yield (custom != null && !custom.trim().isEmpty()) ? custom : "PCI-DSS,HIPAA,SOC2,GDPR";
                        }
                        default -> "PCI-DSS,HIPAA,SOC2,GDPR";
                    };

                    // Set auditManagerFrameworkId to null (deprecated, using complianceFrameworks instead)
                    config.auditManagerFrameworkId = null;

                    System.out.println("\n✅ Selected frameworks: " + config.complianceFrameworks);
                    System.out.println("");
                    System.out.println("📖 Compliance Requirements:");
                    displayComplianceRequirements(config.complianceFrameworks);
                    System.out.println("");
                    System.out.println("ℹ️ Prerequisites:");
                    System.out.println("  1. AWS Audit Manager must be enabled in your AWS account");
                    System.out.println("     (Go to AWS Console > Audit Manager > Get started)");
                    System.out.println("  2. Standard frameworks are pre-installed by AWS");
                    System.out.println("  3. Deployment will automatically query AWS for framework UUIDs");
                    System.out.println("");
                    System.out.println("📚 For detailed compliance guidance, see:");
                    System.out.println("   docs/compliance/QUICK_START_GUIDE.md");
                    System.out.println("   docs/compliance/MULTI_FRAMEWORK_COMPLIANCE.md");
                }
            } else {
                config.auditManagerEnabled = false;
            }

            if (config.enableMonitoring) {
                System.out.println("\n📋 Log Retention Guidelines:");
                System.out.println("  • PCI-DSS: 365 days minimum");
                System.out.println("  • SOC2: 730 days (2 years)");
                System.out.println("  • HIPAA: 2190 days (6 years)");
                System.out.println("  • Multi-framework: 2190-2555 days");
                config.logRetentionDays = promptWithValidation("Log Retention (days)", "7",
                    new String[]{"1", "3", "7", "14", "30", "60", "90", "120", "150", "180", "365", "730", "2190", "2555"});
            }
            
            // Health Check Configuration
            System.out.println("\n🏥 Health Check Configuration:");
            System.out.println("==============================");
            config.healthCheckGracePeriod = promptIntWithValidation("Health Check Grace Period (seconds)", 300, 60, 900);
            config.healthCheckInterval = promptIntWithValidation("Health Check Interval (seconds)", 30, 5, 300);
            config.healthCheckTimeout = promptIntWithValidation("Health Check Timeout (seconds)", 5, 2, 60);
            config.healthyThreshold = promptIntWithValidation("Healthy Threshold Count", 2, 1, 10);
            config.unhealthyThreshold = promptIntWithValidation("Unhealthy Threshold Count", 3, 1, 10);

            // Region and Availability Zone Configuration
            System.out.println("\n🌍 Region and Availability Zone Configuration:");
            System.out.println("==============================================");
            config.region = promptChoice("AWS Region",
                new String[]{"us-east-1", "us-west-2", "us-east-2", "us-west-1", "eu-west-1", "eu-central-1", "ap-southeast-1", "ap-northeast-1"}, "us-east-1");

            // Multi-AZ configuration based on region
            boolean enableMultiAz = promptYesNo("Enable Multi-AZ deployment (recommended for PRODUCTION)",
                config.securityProfile == SecurityProfile.PRODUCTION);

            if (!enableMultiAz) {
                String defaultAz = config.region + "a";
                String selectedAz = promptWithValidation("Availability Zone", defaultAz,
                    getAvailabilityZonesForRegion(config.region, 3));
                config.availabilityZones = new String[]{selectedAz};
                System.out.println("⚠️ Single-AZ deployment: " + selectedAz + " (not recommended for production)");
            } else {
                config.availabilityZones = getAvailabilityZonesForRegion(config.region, 2);
                System.out.println("✅ Multi-AZ enabled: " + String.join(", ", config.availabilityZones));
            }
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
