package com.cloudforgeci.api.observability;

import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Registry for shared/account-level resources that should be reused across stacks.
 *
 * Uses SSM Parameter Store to track resources, with stack-scoped naming to prevent
 * conflicts when deploying multiple independent stacks in the same region.
 *
 * Supported resources:
 * - CloudTrail (stack-scoped)
 * - S3 buckets (stack-scoped, with RETAIN policy)
 * - AWS Config Recorder/Delivery Channel (region-scoped, NOT stack-scoped)
 * - Cognito User Pools (stack-scoped, with RETAIN policy)
 *
 * SSM parameter naming:
 * - Stack-scoped: /cloudforge/shared/{region}/stack/{stackName}/{resource}
 * - Region-scoped: /cloudforge/shared/{region}/{resource}
 *
 * Pattern:
 * 1. Check if SSM parameter exists with resource ARN/ID
 * 2. If exists, import existing resource
 * 3. If not, create new resource and store in SSM
 */
public class SharedResourceRegistry {

    private static final Logger LOG = Logger.getLogger(SharedResourceRegistry.class.getName());
    private static final String PARAMETER_PREFIX = "/cloudforge/shared";

    private final Construct scope;
    private final String region;
    private final String stackName;

    public SharedResourceRegistry(Construct scope, String region, String stackName) {
        this.scope = scope;
        this.region = region;
        this.stackName = stackName;
    }

    /**
     * Get SSM parameter name for CloudTrail in this stack.
     * Stack-scoped to allow multiple independent stacks.
     */
    public String getCloudTrailParameterName() {
        return String.format("%s/%s/stack/%s/cloudtrail/arn", PARAMETER_PREFIX, region, stackName);
    }

    /**
     * Get SSM parameter name for Config Recorder in this region.
     * AWS Config allows only one recorder per region - NOT stack-scoped.
     */
    public String getConfigRecorderParameterName() {
        return String.format("%s/%s/config/recorder-name", PARAMETER_PREFIX, region);
    }

    /**
     * Get SSM parameter name for Config Delivery Channel in this region.
     * AWS Config allows only one delivery channel per region - NOT stack-scoped.
     */
    public String getConfigDeliveryChannelParameterName() {
        return String.format("%s/%s/config/delivery-channel-name", PARAMETER_PREFIX, region);
    }

    /**
     * Get SSM parameter name for a retained S3 bucket.
     * Stack-scoped to prevent conflicts between stacks.
     *
     * @param purpose Bucket purpose (e.g., "cloudtrail", "config", "alb-logs")
     */
    public String getBucketParameterName(String purpose) {
        return String.format("%s/%s/stack/%s/s3/%s/name", PARAMETER_PREFIX, region, stackName, purpose);
    }

    /**
     * Get SSM parameter name for a Cognito User Pool.
     * Stack-scoped to allow independent user pools per stack.
     *
     * @param poolName The user pool name
     */
    public String getCognitoUserPoolParameterName(String poolName) {
        return String.format("%s/%s/stack/%s/cognito/%s/id", PARAMETER_PREFIX, region, stackName, poolName);
    }

    /**
     * Try to read a shared resource ID from SSM Parameter Store.
     * Returns null if parameter doesn't exist.
     *
     * @param parameterName The SSM parameter name
     * @return The resource ID/ARN, or null if not found
     */
    public String tryReadParameter(String parameterName) {
        try {
            software.amazon.awscdk.services.ssm.IStringParameter param = StringParameter.fromStringParameterName(
                scope,
                "Param-" + parameterName.replace("/", "-"),
                parameterName
            );
            String value = param.getStringValue();
            LOG.info("Found existing shared resource in SSM: " + parameterName + " = " + value);
            return value;
        } catch (Exception e) {
            LOG.info("No existing shared resource found in SSM: " + parameterName);
            return null;
        }
    }

    /**
     * Store a shared resource ID in SSM Parameter Store for future reuse.
     *
     * @param parameterName The SSM parameter name
     * @param value The resource ID/ARN to store
     * @param description Human-readable description
     */
    public void storeParameter(String parameterName, String value, String description) {
        StringParameter.Builder.create(scope, "Param-" + parameterName.replace("/", "-"))
            .parameterName(parameterName)
            .stringValue(value)
            .description(description)
            .build();

        LOG.info("Stored shared resource in SSM: " + parameterName + " = " + value);
    }
}
