package com.cloudforgeci.api.application.secrets;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * HashiCorp Vault ApplicationSpec implementation.
 *
 * <p>Vault is a tool for securely accessing secrets, managing encryption keys,
 * and providing identity-based access.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Secrets management (API keys, passwords, certificates)</li>
 *   <li>Dynamic secrets generation</li>
 *   <li>Encryption as a service</li>
 *   <li>Identity-based access control</li>
 *   <li>Audit logging</li>
 * </ul>
 *
 * <p><strong>Compliance Use Cases:</strong></p>
 * <ul>
 *   <li>PCI-DSS: Secure storage of payment gateway credentials</li>
 *   <li>HIPAA: PHI encryption key management</li>
 *   <li>SOC2: Centralized secrets management and audit trails</li>
 *   <li>GDPR: Data encryption and key rotation</li>
 * </ul>
 *
 * <p><strong>Fintech Applications:</strong></p>
 * <ul>
 *   <li>Payment gateway API key storage (Stripe, PayPal, Square)</li>
 *   <li>Database credential rotation</li>
 *   <li>TLS certificate management</li>
 *   <li>Customer data encryption keys</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong></p>
 * <ul>
 *   <li>Initialize and unseal Vault after deployment</li>
 *   <li>Store unseal keys in separate secure locations</li>
 *   <li>Enable audit logging to CloudWatch</li>
 *   <li>Use auto-unseal with AWS KMS for production</li>
 * </ul>
 *
 * @see <a href="https://www.vaultproject.io/docs">Vault Documentation</a>
 */
@ApplicationPlugin(
    value = "vault",
    category = "secrets",
    displayName = "Vault",
    description = "Secrets and encryption management",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)

public class VaultApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "vault";
    private static final String DEFAULT_IMAGE = "hashicorp/vault:latest";
    private static final int APPLICATION_PORT = 8200;
    private static final String CONTAINER_DATA_PATH = "/vault/file";
    private static final String EFS_DATA_PATH = "/vault";
    private static final String VOLUME_NAME = "vaultData";
    private static final String CONTAINER_USER = "100:1000"; // vault user
    private static final String EFS_PERMISSIONS = "750";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/opt/vault/data";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/vault/vault.log",
        "/var/log/vault/audit.log",
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

        // Create Vault configuration
        builder.addCommands(
            "# Create Vault configuration directory",
            "mkdir -p /etc/vault.d",
            "mkdir -p /var/log/vault",
            "",
            "# Create Vault configuration file",
            "cat > /etc/vault.d/vault.hcl <<'EOF'",
            "# Vault Server Configuration",
            "",
            "storage \"file\" {",
            "  path = \"/vault/file\"",
            "}",
            "",
            "listener \"tcp\" {",
            "  address = \"0.0.0.0:8200\"",
            "  tls_disable = false",
            "  tls_cert_file = \"/vault/config/tls/cert.pem\"",
            "  tls_key_file = \"/vault/config/tls/key.pem\"",
            "}",
            "",
            "# Enable audit logging",
            "# audit_device \"file\" {",
            "#   file_path = \"/vault/logs/audit.log\"",
            "# }",
            "",
            "# UI Configuration",
            "ui = true",
            "",
            "# Disable mlock for containers",
            "disable_mlock = true",
            "",
            "# API address",
            "api_addr = \"https://vault.example.com:8200\"",
            "cluster_addr = \"https://vault.example.com:8201\"",
            "EOF",
            "",
            "# Set ownership",
            "chown -R " + uid + ":" + gid + " /etc/vault.d"
        );

        // Run Vault container
        builder.addCommands(
            "# Run Vault container",
            "# Note: TLS certificate required for production",
            "docker run -d \\",
            "  --name vault \\",
            "  -p 8200:8200 \\",
            "  -v " + ec2DataPath() + ":/vault/file \\",
            "  -v /etc/vault.d:/vault/config \\",
            "  -v /var/log/vault:/vault/logs \\",
            "  --cap-add=IPC_LOCK \\",
            "  -e 'VAULT_LOCAL_CONFIG={\"storage\": {\"file\": {\"path\": \"/vault/file\"}}, \"listener\": {\"tcp\": {\"address\": \"0.0.0.0:8200\", \"tls_disable\": \"1\"}}, \"ui\": true, \"disable_mlock\": true}' \\",
            "  " + DEFAULT_IMAGE + " server",
            "echo 'Vault container started' >> /var/log/userdata.log",
            "",
            "# Wait for Vault to start",
            "sleep 10",
            "",
            "# Display initialization instructions",
            "cat >> /var/log/userdata.log <<'INSTRUCTIONS'",
            "================================================================================",
            "VAULT INITIALIZATION REQUIRED",
            "================================================================================",
            "",
            "Vault is running but needs to be initialized and unsealed.",
            "",
            "1. Set Vault address:",
            "   export VAULT_ADDR='http://127.0.0.1:8200'",
            "",
            "2. Initialize Vault (do this ONCE):",
            "   vault operator init",
            "",
            "   IMPORTANT: Save the unseal keys and root token in a secure location!",
            "",
            "3. Unseal Vault (required after every restart):",
            "   vault operator unseal <unseal-key-1>",
            "   vault operator unseal <unseal-key-2>",
            "   vault operator unseal <unseal-key-3>",
            "",
            "4. Login with root token:",
            "   vault login <root-token>",
            "",
            "5. Enable audit logging:",
            "   vault audit enable file file_path=/vault/logs/audit.log",
            "",
            "For production, configure AWS KMS auto-unseal:",
            "https://www.vaultproject.io/docs/configuration/seal/awskms",
            "================================================================================",
            "INSTRUCTIONS",
            "",
            "echo 'Vault initialization instructions written to log' >> /var/log/userdata.log"
        );
    }

    @Override
    public String toString() {
        return "VaultApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
