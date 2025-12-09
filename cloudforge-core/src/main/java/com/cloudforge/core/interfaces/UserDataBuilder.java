package com.cloudforge.core.interfaces;

import java.util.List;

/**
 * Builder interface for constructing EC2 UserData scripts.
 *
 * <p>This interface provides infrastructure-level helpers for common EC2 setup tasks
 * (storage mounting, CloudWatch configuration) while allowing applications to inject
 * their specific installation and configuration commands.</p>
 *
 * <p>The UserDataBuilder abstracts away the complexity of bash scripting for:</p>
 * <ul>
 *   <li>System updates (dnf/yum compatibility)</li>
 *   <li>EFS mounting with proper error handling</li>
 *   <li>EBS device detection (handles both /dev/xvdh and /dev/nvme1n1)</li>
 *   <li>CloudWatch Agent installation and configuration</li>
 *   <li>File ownership and permissions</li>
 * </ul>
 *
 * <p>Applications can focus on their specific installation logic while the
 * infrastructure handles platform concerns.</p>
 *
 * @see ApplicationSpec#configureUserData(UserDataBuilder, Ec2Context)
 */
public interface UserDataBuilder {

    /**
     * Add system update commands (handles both dnf and yum).
     * Automatically detects whether to use dnf (Amazon Linux 2023) or yum (Amazon Linux 2).
     */
    void addSystemUpdate();

    /**
     * Install and configure CloudWatch Agent for log streaming.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Downloads and installs the CloudWatch Agent</li>
     *   <li>Configures log file collection for the specified paths</li>
     *   <li>Starts the agent service</li>
     * </ul>
     *
     * @param logGroupName The CloudWatch Logs group name (e.g., "/aws/jenkins/mystack/ec2/dev")
     * @param logFilePaths List of absolute paths to log files to monitor
     */
    void installCloudWatchAgent(String logGroupName, List<String> logFilePaths);

    /**
     * Mount EFS filesystem with IAM authentication.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Installs amazon-efs-utils</li>
     *   <li>Creates the mount directory</li>
     *   <li>Adds fstab entry with TLS and IAM auth</li>
     *   <li>Mounts the filesystem</li>
     *   <li>Sets ownership to the specified uid:gid</li>
     * </ul>
     *
     * @param efsId The EFS filesystem ID (e.g., "fs-12345678")
     * @param accessPointId The EFS access point ID (e.g., "fsap-12345678")
     * @param mountPath The local mount path (e.g., "/var/lib/jenkins")
     * @param uid The user ID for ownership (e.g., "1000")
     * @param gid The group ID for ownership (e.g., "1000")
     */
    void mountEfs(String efsId, String accessPointId, String mountPath, String uid, String gid);

    /**
     * Format and mount EBS volume.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Detects the EBS device (handles both /dev/xvdh and /dev/nvme1n1)</li>
     *   <li>Formats the device with XFS filesystem</li>
     *   <li>Creates the mount directory</li>
     *   <li>Adds fstab entry</li>
     *   <li>Mounts the filesystem</li>
     *   <li>Sets ownership to the specified uid:gid</li>
     * </ul>
     *
     * @param deviceName The EBS device name (e.g., "/dev/xvdh")
     * @param mountPath The local mount path (e.g., "/var/lib/jenkins")
     * @param uid The user ID for ownership (e.g., "1000")
     * @param gid The group ID for ownership (e.g., "1000")
     */
    void mountEbs(String deviceName, String mountPath, String uid, String gid);

    /**
     * Add custom commands to the UserData script.
     * Commands are executed in the order they are added.
     *
     * @param commands One or more shell commands to execute
     */
    void addCommands(String... commands);

    /**
     * Add a single custom command to the UserData script.
     *
     * @param command Shell command to execute
     */
    void addCommand(String command);
}
