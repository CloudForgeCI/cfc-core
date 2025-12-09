package com.cloudforge.core.interfaces;

import java.util.Optional;

/**
 * Runtime context information for EC2 UserData configuration.
 *
 * <p>This interface provides applications with the runtime information they need
 * to make decisions during UserData script generation, such as:</p>
 * <ul>
 *   <li>Whether to use EFS or EBS for storage</li>
 *   <li>Stack name for resource naming and logging</li>
 *   <li>Runtime environment (EC2 vs Fargate)</li>
 *   <li>Security profile for compliance requirements</li>
 * </ul>
 *
 * <p>Applications use this context to adapt their configuration based on the
 * deployment environment.</p>
 *
 * @see ApplicationSpec#configureUserData(UserDataBuilder, Ec2Context)
 */
public interface Ec2Context {

    /**
     * Returns the CloudFormation stack name.
     * Used for resource naming and CloudWatch log group names.
     *
     * @return stack name (e.g., "jenkins-prod")
     */
    String stackName();

    /**
     * Returns the runtime type as a string.
     * Typically "EC2" or "FARGATE".
     *
     * @return runtime type in lowercase (e.g., "ec2")
     */
    String runtimeType();

    /**
     * Returns the security profile as a string.
     * Used for compliance-aware logging and configuration.
     *
     * @return security profile in lowercase (e.g., "dev", "staging", "production")
     */
    String securityProfile();

    /**
     * Returns whether EFS is available in this deployment.
     * If true, applications should use EFS for storage.
     * If false, applications should use EBS for storage.
     *
     * @return true if EFS is configured and available
     */
    boolean hasEfs();

    /**
     * Returns the EFS filesystem ID if EFS is available.
     *
     * @return EFS filesystem ID (e.g., "fs-12345678"), or empty if EFS is not available
     */
    Optional<String> efsId();

    /**
     * Returns the EFS access point ID if EFS is available.
     *
     * @return EFS access point ID (e.g., "fsap-12345678"), or empty if EFS is not available
     */
    Optional<String> accessPointId();
}
