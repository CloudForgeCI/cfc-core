# SSM Parameter Stack Scoping

## Overview

SSM parameters used to track shared resources are now **stack-scoped** to prevent conflicts when deploying multiple independent CloudFormation stacks in the same AWS region.

## Motivation

Previously, SSM parameters were region-scoped only, using paths like:
```
/cloudforge/shared/{region}/alb-logs/bucket-arn
/cloudforge/shared/{region}/cognito/user-pool-arn
/cloudforge/shared/{region}/cloudtrail/bucket-arn
```

This caused conflicts when multiple stacks were deployed in the same region, as they would overwrite each other's parameters.

## New Naming Convention

### Stack-Scoped Resources
Most resources are now stack-scoped to allow independent stacks:

```
/cloudforge/shared/{region}/stack/{stackName}/{resource}
```

**Examples:**
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/alb-logs/bucket-arn`
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/cognito/user-pool-arn`
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/cloudtrail/bucket-arn`
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/config/bucket-arn`
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/audit-manager/bucket-arn`
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/s3/{purpose}/name`
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/cognito/{poolName}/id`

### Region-Scoped Resources (Exceptions)

AWS Config Recorder and Delivery Channel remain region-scoped because **AWS only allows one per region**:

```
/cloudforge/shared/{region}/config/recorder-name
/cloudforge/shared/{region}/config/delivery-channel-name
```

## Benefits

1. **Stack Isolation**: Each stack has its own SSM parameters
2. **No Conflicts**: Multiple stacks can coexist in the same region
3. **Independent Lifecycles**: Stacks can be created/destroyed independently
4. **Clear Ownership**: Parameter names clearly indicate which stack owns the resource

## Implementation Details

### Code Changes

**SharedResourceRegistry.java:**
- Constructor now requires `stackName` parameter
- All resource parameter names (except Config Recorder/Delivery Channel) include stack name

**Factory Classes:**
- `AlbFactory`: ALB logs bucket ARN is stack-scoped
- `CognitoAuthenticationFactory`: User Pool ARN is stack-scoped
- `ComplianceFactory`: CloudTrail, Config, and Audit Manager buckets are stack-scoped

### Example Usage

```java
// Create registry with stack name
String stackName = Stack.of(this).getStackName();
SharedResourceRegistry registry = new SharedResourceRegistry(scope, region, stackName);

// Get stack-scoped parameter name
String bucketParam = registry.getBucketParameterName("alb-logs");
// Returns: /cloudforge/shared/us-east-1/stack/MyStack/s3/alb-logs/name

// Get region-scoped parameter name (Config only)
String recorderParam = registry.getConfigRecorderParameterName();
// Returns: /cloudforge/shared/us-east-1/config/recorder-name
```

## Migration Notes

### For Existing Deployments

Existing stacks will automatically adopt the new naming convention on next deployment. The stack name from the CloudFormation stack will be used in the parameter path.

### For Multi-Stack Environments

You can now safely deploy multiple independent stacks in the same region:

```bash
# Deploy production stack
cdk deploy MyApp-Prod --context stackName=MyApp-Prod

# Deploy staging stack (no conflicts!)
cdk deploy MyApp-Staging --context stackName=MyApp-Staging
```

Each stack will have its own set of SSM parameters:
- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/...`
- `/cloudforge/shared/us-east-1/stack/MyApp-Staging/...`

## Testing

All tests have been updated to reflect the new naming convention. Run the test suite to verify:

```bash
mvn test -Dtest=SharedResourceRegistryTest
```

## See Also

- [SharedResourceRegistry.java](../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/SharedResourceRegistry.java)
- [SHARED_RESOURCES.md](./SHARED_RESOURCES.md)
