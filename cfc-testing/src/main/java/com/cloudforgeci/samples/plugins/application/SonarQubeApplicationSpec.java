package com.cloudforgeci.samples.plugins.application;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SonarQube ApplicationSpec - Example Custom Application Plugin.
 *
 * <p>This demonstrates how to create a custom application plugin that integrates
 * seamlessly with CloudForge infrastructure. SonarQube is a code quality and security
 * analysis platform.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Continuous code quality inspection</li>
 *   <li>Security vulnerability detection</li>
 *   <li>Code smell and technical debt tracking</li>
 *   <li>Multi-language support</li>
 * </ul>
 *
 * <h2>Plugin Registration:</h2>
 * <p>This plugin demonstrates the {@link ApplicationPlugin} annotation system for
 * auto-discovery via ServiceLoader. Simply register this class in your
 * {@code META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec} file.</p>
 *
 * <h2>Deployment:</h2>
 * <ul>
 *   <li><b>Fargate:</b> Uses sonarqube:lts-community Docker image</li>
 *   <li><b>EC2:</b> Installs SonarQube from ZIP distribution</li>
 * </ul>
 *
 * <h2>Usage via ApplicationLoader:</h2>
 * <pre>{@code
 * // Auto-discovery via ApplicationLoader
 * Optional<ApplicationSpec> sonarQube = ApplicationLoader.findById("sonarqube");
 *
 * // Or use in InteractiveDeployer (automatically discovered)
 * mvn clean package
 * cdk deploy --context cfc=@deployment-context.json
 * }</pre>
 *
 * @since 1.0.0
 * @author CloudForge Community
 */
@ApplicationPlugin(
    value = "sonarqube",
    category = "code-quality",
    displayName = "SonarQube",
    description = "Continuous code quality and security inspection platform",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)
public class SonarQubeApplicationSpec implements ApplicationSpec {

    // ========== Application Identity ==========

    @Override
    public String applicationId() {
        return "sonarqube";
    }

    // ========== Container Configuration (Fargate) ==========

    @Override
    public String defaultContainerImage() {
        return "sonarqube:lts-community";
    }

    @Override
    public int applicationPort() {
        return 9000;
    }

    @Override
    public String containerDataPath() {
        return "/opt/sonarqube/data";
    }

    @Override
    public String efsDataPath() {
        return "/sonarqube";
    }

    @Override
    public String volumeName() {
        return "sonarqubeData";
    }

    @Override
    public String containerUser() {
        return "1000:1000";  // SonarQube runs as user 1000
    }

    @Override
    public String efsPermissions() {
        return "755";
    }

    @Override
    public String healthCheckPath() {
        return "/api/system/health";
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> environment = new HashMap<>();

        // SonarQube server configuration
        if (fqdn != null && !fqdn.isBlank()) {
            String serverUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("SONAR_WEB_CONTEXT", "/");
            environment.put("SONAR_WEB_HOST", "0.0.0.0");
            environment.put("SONAR_WEB_PORT", String.valueOf(applicationPort()));

            // Set public URL for webhooks and links
            environment.put("SONAR_WEB_PUBLIC_URL", serverUrl);
        }

        // JVM options for performance
        environment.put("SONAR_WEB_JAVAADDITIONALOPTS", "-XX:+UseG1GC -Xmx2g -Xms512m");
        environment.put("SONAR_CE_JAVAADDITIONALOPTS", "-XX:+UseG1GC -Xmx1g -Xms256m");

        return environment;
    }

    // ========== EC2 Configuration ==========

    @Override
    public String ebsDeviceName() {
        return "/dev/xvdh";
    }

    @Override
    public String ec2DataPath() {
        return "/opt/sonarqube/data";
    }

    @Override
    public List<String> ec2LogPaths() {
        return List.of(
            "/opt/sonarqube/logs/sonar.log",
            "/opt/sonarqube/logs/web.log",
            "/opt/sonarqube/logs/ce.log",
            "/var/log/userdata.log",
            "/var/log/messages"
        );
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        // System updates
        builder.addSystemUpdate();

        // Install Java 17 (required for SonarQube LTS)
        builder.addCommands(
            "# Install Java 17",
            "command -v dnf >/dev/null && dnf -y install java-17-amazon-corretto-headless unzip || " +
            "yum -y install java-17-amazon-corretto-headless unzip",
            "echo 'Java 17 installed' >> /var/log/userdata.log"
        );

        // Download and install SonarQube
        String sonarVersion = "10.3.0.82913";  // LTS version
        builder.addCommands(
            "# Download SonarQube",
            "cd /tmp",
            "wget https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-" + sonarVersion + ".zip",
            "unzip sonarqube-" + sonarVersion + ".zip",
            "mv sonarqube-" + sonarVersion + " /opt/sonarqube",
            "echo 'SonarQube downloaded and extracted' >> /var/log/userdata.log"
        );

        // Create SonarQube user
        builder.addCommands(
            "# Create SonarQube user",
            "useradd -r -s /bin/bash sonarqube || true",
            "echo 'SonarQube user created' >> /var/log/userdata.log"
        );

        // Install and configure CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage (EFS or EBS)
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

        // Configure SonarQube
        builder.addCommands(
            "# Configure SonarQube",
            "mkdir -p /opt/sonarqube/data/es7",
            "mkdir -p /opt/sonarqube/logs",
            "mkdir -p /opt/sonarqube/temp",
            "chown -R sonarqube:sonarqube /opt/sonarqube",
            "echo 'SonarQube directories configured' >> /var/log/userdata.log"
        );

        // Configure SonarQube properties
        builder.addCommands(
            "# Configure SonarQube properties",
            "cat > /opt/sonarqube/conf/sonar.properties <<'EOF'",
            "# SonarQube Server Configuration",
            "sonar.web.host=0.0.0.0",
            "sonar.web.port=9000",
            "sonar.web.context=/",
            "sonar.path.data=" + ec2DataPath(),
            "sonar.path.logs=/opt/sonarqube/logs",
            "sonar.path.temp=/opt/sonarqube/temp",
            "",
            "# Public URL (configure via ALB DNS or custom domain)",
            "# sonar.core.serverBaseURL=https://your-domain.com",
            "",
            "# Elasticsearch",
            "sonar.search.javaOpts=-Xmx512m -Xms512m -XX:MaxDirectMemorySize=256m -XX:+HeapDumpOnOutOfMemoryError",
            "",
            "# Web Server",
            "sonar.web.javaOpts=-Xmx512m -Xms128m -XX:+HeapDumpOnOutOfMemoryError",
            "",
            "# Compute Engine",
            "sonar.ce.javaOpts=-Xmx512m -Xms128m -XX:+HeapDumpOnOutOfMemoryError",
            "EOF",
            "chown sonarqube:sonarqube /opt/sonarqube/conf/sonar.properties",
            "echo 'SonarQube properties configured' >> /var/log/userdata.log"
        );

        // Create systemd service
        builder.addCommands(
            "# Create SonarQube systemd service",
            "cat > /etc/systemd/system/sonarqube.service <<'EOF'",
            "[Unit]",
            "Description=SonarQube service",
            "After=network.target network-online.target",
            "Requires=network-online.target",
            "",
            "[Service]",
            "Type=forking",
            "ExecStart=/opt/sonarqube/bin/linux-x86-64/sonar.sh start",
            "ExecStop=/opt/sonarqube/bin/linux-x86-64/sonar.sh stop",
            "ExecReload=/opt/sonarqube/bin/linux-x86-64/sonar.sh restart",
            "User=sonarqube",
            "Group=sonarqube",
            "Restart=on-failure",
            "RestartSec=10",
            "LimitNOFILE=65536",
            "LimitNPROC=4096",
            "",
            "[Install]",
            "WantedBy=multi-user.target",
            "EOF",
            "echo 'SonarQube systemd service created' >> /var/log/userdata.log"
        );

        // Set system limits for SonarQube (Elasticsearch requirements)
        builder.addCommands(
            "# Set system limits for SonarQube",
            "echo 'sonarqube - nofile 65536' >> /etc/security/limits.conf",
            "echo 'sonarqube - nproc 4096' >> /etc/security/limits.conf",
            "echo 'vm.max_map_count=262144' >> /etc/sysctl.conf",
            "sysctl -w vm.max_map_count=262144",
            "echo 'System limits configured for SonarQube' >> /var/log/userdata.log"
        );

        // Start SonarQube
        builder.addCommands(
            "# Start SonarQube",
            "systemctl daemon-reload",
            "systemctl enable sonarqube",
            "systemctl start sonarqube",
            "echo 'SonarQube service started' >> /var/log/userdata.log",
            "",
            "# Wait for SonarQube to fully start",
            "sleep 60",
            "",
            "# Check SonarQube status",
            "if systemctl is-active --quiet sonarqube; then",
            "  echo 'SonarQube is running' >> /var/log/userdata.log",
            "  echo 'Access SonarQube via ALB endpoint' >> /var/log/userdata.log",
            "  echo 'Default credentials: admin / admin' >> /var/log/userdata.log",
            "else",
            "  echo 'ERROR: SonarQube failed to start' >> /var/log/userdata.log",
            "  tail -50 /opt/sonarqube/logs/sonar.log >> /var/log/userdata.log",
            "fi"
        );
    }

    // ========== OIDC Integration ==========

    @Override
    public boolean supportsOidcIntegration() {
        return false;  // SonarQube Community Edition doesn't support OIDC (Enterprise feature)
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return null;
    }

    @Override
    public String toString() {
        return "SonarQubeApplicationSpec{" +
                "applicationId='sonarqube'" +
                ", defaultImage='sonarqube:lts-community'" +
                ", applicationPort=9000" +
                ", containerDataPath='/opt/sonarqube/data'" +
                ", ec2DataPath='/opt/sonarqube/data'" +
                '}';
    }
}
