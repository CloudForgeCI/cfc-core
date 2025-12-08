package com.cloudforgeci.api.application;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.JenkinsOidcIntegration;

import java.util.List;

/**
 * Jenkins ApplicationSpec implementation.
 *
 * <p>Defines all Jenkins-specific configuration including:</p>
 * <ul>
 *   <li>Container image (jenkins/jenkins:lts)</li>
 *   <li>Application port (8080)</li>
 *   <li>Data persistence paths (container and EC2)</li>
 *   <li>Container user and permissions (1000:1000)</li>
 *   <li>EC2 UserData installation and configuration</li>
 * </ul>
 *
 * <p>This spec is used by the universal application framework to deploy Jenkins
 * using either Fargate or EC2 runtime with appropriate infrastructure (EFS or EBS).</p>
 *
 * <p>CloudForge 3.0.0: Extracted from hardcoded Jenkins implementation</p>
 */
@ApplicationPlugin(
    value = "jenkins",
    category = "cicd",
    displayName = "Jenkins",
    description = "Open-source automation server for CI/CD",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = true
)
public class JenkinsApplicationSpec implements ApplicationSpec {

    // Application Identity
    private static final String APPLICATION_ID = "jenkins";

    // Container Configuration
    private static final String DEFAULT_IMAGE = "jenkins/jenkins:lts";
    private static final int APPLICATION_PORT = 8080;
    private static final String CONTAINER_DATA_PATH = "/var/jenkins_home";
    private static final String EFS_DATA_PATH = "/jenkins";
    private static final String VOLUME_NAME = "jenkinsHome";
    private static final String CONTAINER_USER = "1000:1000";
    private static final String EFS_PERMISSIONS = "750";

    // EC2 Configuration
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/jenkins";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/jenkins/jenkins.log",
        "/var/log/userdata.log",
        "/var/log/messages"
    );

    // ========== Application Identity ==========

    @Override
    public String applicationId() {
        return APPLICATION_ID;
    }

    // ========== Container Configuration ==========

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public int applicationPort() {
        return APPLICATION_PORT;
    }

    @Override
    public java.util.List<OptionalPort> optionalPorts() {
        return java.util.List.of(
            // Build agents - inbound connections from Jenkins agents
            OptionalPort.inboundTcp(50000, "enableAgents", "JNLP Build Agents")
        );
    }

    @Override
    public String containerDataPath() {
        return CONTAINER_DATA_PATH;
    }

    @Override
    public String efsDataPath() {
        return EFS_DATA_PATH;
    }

    @Override
    public String volumeName() {
        return VOLUME_NAME;
    }

    @Override
    public String containerUser() {
        return CONTAINER_USER;
    }

    @Override
    public String efsPermissions() {
        return EFS_PERMISSIONS;
    }

    @Override
    public String healthCheckPath() {
        return "/login";
    }

    @Override
    public java.util.Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        java.util.Map<String, String> environment = new java.util.HashMap<>();

        // Configure JAVA_OPTS for Jenkins
        StringBuilder javaOpts = new StringBuilder();

        // Configure Jenkins reverse proxy settings for ALB
        // This is CRITICAL to fix 403 CSRF errors when behind a load balancer

        // Allow Jenkins to trust X-Forwarded-* headers from ALB
        javaOpts.append("-Dorg.eclipse.jetty.server.Request.maxFormContentSize=1000000 ");

        // Disable CSP that can interfere with reverse proxy
        javaOpts.append("-Dhudson.model.DirectoryBrowserSupport.CSP=\"\" ");

        // Configure Jenkins to properly handle reverse proxy headers
        // This ensures Jenkins knows the correct external URL for CSRF token validation
        if (fqdn != null && !fqdn.isBlank()) {
            javaOpts.append("-Dhudson.TcpSlaveAgentListener.hostName=").append(fqdn).append(" ");

            // Set Jenkins root URL directly via system property
            // This fixes "reverse proxy setup is broken" errors
            String jenkinsRootUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            javaOpts.append("-Djenkins.model.Jenkins.rootUrl=").append(jenkinsRootUrl).append(" ");
        }

        // Skip setup wizard when using application-oidc mode
        // OIDC configuration will be applied via JenkinsOidcIntegration
        if ("application-oidc".equals(authMode)) {
            javaOpts.append("-Djenkins.install.runSetupWizard=false ");
        }

        // Configure JENKINS_OPTS for reverse proxy support
        StringBuilder jenkinsOpts = new StringBuilder();
        jenkinsOpts.append("--httpListenAddress=0.0.0.0 ");
        jenkinsOpts.append("--httpsPort=-1 ");  // Disable direct HTTPS on Jenkins (ALB handles SSL)

        // Set Jenkins URL for proper CSRF token generation
        if (fqdn != null && !fqdn.isBlank()) {
            String jenkinsUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("JENKINS_URL", jenkinsUrl);
        }

        environment.put("JENKINS_OPTS", jenkinsOpts.toString().trim());

        if (javaOpts.length() > 0) {
            environment.put("JAVA_OPTS", javaOpts.toString().trim());
        }

        return environment;
    }

    // ========== EC2 Configuration ==========

    @Override
    public String ebsDeviceName() {
        return EBS_DEVICE_NAME;
    }

    @Override
    public String ec2DataPath() {
        return EC2_DATA_PATH;
    }

    @Override
    public List<String> ec2LogPaths() {
        return EC2_LOG_PATHS;
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        // System updates
        builder.addSystemUpdate();

        // Install Java 17 (required for Jenkins)
        builder.addCommands(
            "# Install Java 17",
            "command -v dnf >/dev/null && dnf -y install java-17-amazon-corretto-headless || yum -y install java-17-amazon-corretto-headless",
            "echo 'Java installed' >> /var/log/userdata.log"
        );

        // Install Jenkins
        builder.addCommands(
            "# Install Jenkins",
            "curl -fsSL https://pkg.jenkins.io/redhat-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo",
            "rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key",
            "command -v dnf >/dev/null && dnf -y install jenkins || yum -y install jenkins",
            "echo 'Jenkins installed' >> /var/log/userdata.log"
        );

        // Install and configure CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage (EFS or EBS based on availability)
        String[] userParts = containerUser().split(":");
        String uid = userParts[0];
        String gid = userParts[1];

        if (context.hasEfs()) {
            builder.mountEfs(
                context.efsId().orElseThrow(),
                context.accessPointId().orElseThrow(),
                ec2DataPath(),
                uid,
                gid
            );
        } else {
            builder.mountEbs(
                ebsDeviceName(),
                ec2DataPath(),
                uid,
                gid
            );
        }

        // Configure and start Jenkins
        builder.addCommands(
            "# Start Jenkins",
            "systemctl enable jenkins",
            "systemctl start jenkins",
            "echo 'Jenkins started' >> /var/log/userdata.log",
            "",
            "# Wait for Jenkins to generate admin password",
            "echo 'Waiting for Jenkins admin password...' >> /var/log/userdata.log",
            "sleep 60",
            "if [ -f " + ec2DataPath() + "/secrets/initialAdminPassword ]; then",
            "  echo 'Jenkins Admin Password:' >> /var/log/userdata.log",
            "  cat " + ec2DataPath() + "/secrets/initialAdminPassword >> /var/log/userdata.log",
            "else",
            "  echo 'Jenkins admin password file not found yet' >> /var/log/userdata.log",
            "fi"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new JenkinsOidcIntegration();
    }

    @Override
    public String toString() {
        return "JenkinsApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                ", containerDataPath='" + CONTAINER_DATA_PATH + '\'' +
                ", ec2DataPath='" + EC2_DATA_PATH + '\'' +
                ", efsDataPath='" + EFS_DATA_PATH + '\'' +
                '}';
    }
}
