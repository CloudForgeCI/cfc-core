package com.cloudforge.core.annotation;

/**
 * Tags that describe the impact and characteristics of configuration field changes.
 *
 * <p>These tags drive change impact analysis, warnings, and approval workflows.</p>
 *
 * <h2>Usage in Configuration Introspection</h2>
 * <pre>{@code
 * @ConfigField(
 *     displayName = "Database Instance Class",
 *     tags = {FieldTag.DESTRUCTIVE, FieldTag.BILLING_IMPACT}
 * )
 * public String databaseInstanceClass;
 * }</pre>
 *
 * <p>When this field changes, the system shows:</p>
 * <pre>
 * - databaseInstanceClass: db.t3.small → db.m5.large
 *   ⚠️  DESTRUCTIVE: Resource will be replaced (potential data loss)
 *   💰 BILLING_IMPACT: Estimated +$132/month
 * </pre>
 *
 * @since 3.1.0
 */
public enum FieldTag {

    /**
     * Changing this field requires resource replacement.
     *
     * <p><b>Risk:</b> Potential data loss or service interruption</p>
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Changing RDS instance class</li>
     *   <li>Changing EFS mount encryption</li>
     *   <li>Changing VPC CIDR block</li>
     * </ul>
     *
     * <p><b>Recommended Action:</b> Require manual confirmation for production</p>
     */
    DESTRUCTIVE,

    /**
     * Changing this field requires service restart.
     *
     * <p><b>Downtime:</b> ~1-2 minutes typically</p>
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Changing container memory limits</li>
     *   <li>Changing environment variables</li>
     *   <li>Changing health check configuration</li>
     * </ul>
     *
     * <p><b>Recommended Action:</b> Schedule during maintenance window</p>
     */
    REQUIRES_RESTART,

    /**
     * Changing this field impacts AWS costs.
     *
     * <p><b>Impact:</b> Monthly billing change</p>
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Changing instance type (EC2/RDS)</li>
     *   <li>Enabling GuardDuty</li>
     *   <li>Increasing storage allocation</li>
     *   <li>Enabling Multi-AZ deployment</li>
     * </ul>
     *
     * <p><b>Recommended Action:</b> Show estimated cost delta if possible</p>
     */
    BILLING_IMPACT,

    /**
     * This field cannot be changed after resource creation.
     *
     * <p><b>Consequence:</b> Requires resource replacement to change</p>
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>VPC CIDR block</li>
     *   <li>RDS database engine (postgres vs mysql)</li>
     *   <li>S3 bucket name</li>
     *   <li>KMS key deletion window</li>
     * </ul>
     *
     * <p><b>Recommended Action:</b> Validate carefully during initial deployment</p>
     */
    IMMUTABLE,

    /**
     * Production changes require manual approval.
     *
     * <p><b>Use Case:</b> Compliance-sensitive or high-risk changes</p>
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Disabling encryption</li>
     *   <li>Changing security group rules</li>
     *   <li>Modifying IAM policies</li>
     *   <li>Disabling audit logging</li>
     * </ul>
     *
     * <p><b>Recommended Action:</b> Require approval workflow for PRODUCTION profile</p>
     */
    REQUIRES_APPROVAL,

    /**
     * This feature is experimental and not production-ready.
     *
     * <p><b>Warning:</b> API may change, behavior not fully validated</p>
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Beta AWS features</li>
     *   <li>Newly added application integrations</li>
     *   <li>Unreleased CloudForge features</li>
     * </ul>
     *
     * <p><b>Recommended Action:</b> Block usage in PRODUCTION security profile</p>
     */
    EXPERIMENTAL
}
