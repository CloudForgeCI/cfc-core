package com.cloudforgeci.api.application.artifactregistry;

import com.cloudforge.core.annotation.ApplicationPlugin;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * Sonatype Nexus Repository Manager ApplicationSpec implementation.
 *
 * <p>Nexus is an artifact repository manager supporting Maven, npm, Docker,
 * PyPI, RubyGems, and other repository formats.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Repositories for Maven, npm, Docker, and PyPI</li>
 *   <li>Proxy remote repositories</li>
 *   <li>Role-based access control</li>
 *   <li>Vulnerability scanning</li>
 *   <li>License analysis</li>
 * </ul>
 *
 * <p><strong>Compliance Use Cases:</strong></p>
 * <ul>
 *   <li>SOC2: Software bill of materials (SBOM) tracking</li>
 *   <li>PCI-DSS: Secure artifact storage for payment processing applications</li>
 *   <li>HIPAA: Audit trail for healthcare application deployments</li>
 * </ul>
 *
 * <p><strong>Fintech Applications:</strong></p>
 * <ul>
 *   <li>Store payment gateway SDK artifacts</li>
 *   <li>Host Docker images for financial services</li>
 *   <li>Proxy npm packages for fintech frontends</li>
 *   <li>Track artifact provenance and signatures</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong></p>
 * <ul>
 *   <li>Change default admin password immediately</li>
 *   <li>Enable LDAP/SAML/OIDC for production</li>
 *   <li>Configure blob store encryption</li>
 *   <li>Enable audit logging</li>
 * </ul>
 *
 * @see <a href="https://help.sonatype.com/repomanager3">Nexus Documentation</a>
 */
@ApplicationPlugin(
    value = "nexus",
    category = "artifactregistry",
    displayName = "Nexus",
    description = "Artifact repository manager for multiple package formats",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)

public class NexusApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "nexus";
    private static final String DEFAULT_IMAGE = "sonatype/nexus3:latest";
    private static final int APPLICATION_PORT = 8081;
    private static final String CONTAINER_DATA_PATH = "/nexus-data";
    private static final String EFS_DATA_PATH = "/nexus";
    private static final String VOLUME_NAME = "nexusData";
    private static final String CONTAINER_USER = "200:200"; // nexus user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/opt/nexus-data";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/opt/nexus-data/log/nexus.log",
        "/opt/nexus-data/log/audit/audit.log",
        "/var/log/userdata.log"
    );

    @Override
    public String applicationId() {
        return APPLICATION_ID;
    }

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
            // Docker Registry - inbound for Docker image hosting (configurable port range)
            OptionalPort.inboundTcp(5000, "enableDockerRegistry", "Docker Registry"),
            OptionalPort.inboundTcp(5001, "enableDockerRegistry", "Docker Registry (hosted)"),
            OptionalPort.inboundTcp(5002, "enableDockerRegistry", "Docker Registry (proxy)")
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
        builder.addSystemUpdate();

        // Install Docker
        builder.addCommands(
            "# Install Docker",
            "yum install -y docker",
            "systemctl enable docker",
            "systemctl start docker",
            "echo 'Docker installed' >> /var/log/userdata.log"
        );

        // Install CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage
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

        // Run Nexus container
        builder.addCommands(
            "# Run Nexus Repository Manager container",
            "docker run -d \\",
            "  --name nexus \\",
            "  -p 8081:8081 \\",
            "  -v " + ec2DataPath() + ":/nexus-data \\",
            "  -e INSTALL4J_ADD_VM_PARAMS='-Xms2703m -Xmx2703m -XX:MaxDirectMemorySize=2703m' \\",
            "  " + DEFAULT_IMAGE,
            "echo 'Nexus container started' >> /var/log/userdata.log",
            "",
            "# Wait for Nexus to start (can take 2-3 minutes)",
            "echo 'Waiting for Nexus to initialize...' >> /var/log/userdata.log",
            "sleep 120",
            "",
            "# Retrieve admin password",
            "if [ -f " + ec2DataPath() + "/admin.password ]; then",
            "  NEXUS_ADMIN_PASSWORD=$(cat " + ec2DataPath() + "/admin.password)",
            "  echo \"Nexus initial admin password: $NEXUS_ADMIN_PASSWORD\" >> /var/log/userdata.log",
            "  echo \"IMPORTANT: Change this password immediately!\" >> /var/log/userdata.log",
            "else",
            "  echo \"Nexus admin password file not found yet\" >> /var/log/userdata.log",
            "fi",
            "",
            "echo 'Nexus should be available on port 8081' >> /var/log/userdata.log",
            "echo 'Default credentials: admin / (see /nexus-data/admin.password)' >> /var/log/userdata.log"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        // Nexus Pro supports OIDC/SAML but no OidcIntegration implementation yet
        // Return false until getOidcIntegration() returns a valid implementation
        return false;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        // Nexus Pro supports OIDC/SAML
        // Requires Nexus Pro license for OIDC/SAML support
        return null;
    }

    @Override
    public String toString() {
        return "NexusApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
