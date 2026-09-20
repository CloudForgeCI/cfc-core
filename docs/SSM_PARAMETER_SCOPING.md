# SSM Parameter Stack Scoping

## Overview

CloudForge records the identifiers of some shared resources (log buckets, Cognito user pools, AWS Config resources) in SSM Parameter Store. Most parameters are **stack-scoped**, so that several independent stacks can be deployed in the same account and region without overwriting each other's parameters.

## Naming Convention

### Stack-Scoped Parameters

```
/cloudforge/shared/{region}/stack/{stackName}/{resource}
```

| Parameter | Written by |
|-----------|-----------|
| `/cloudforge/shared/{region}/stack/{stackName}/alb-logs/bucket-arn` | `AlbFactory` |
| `/cloudforge/shared/{region}/stack/{stackName}/cognito/user-pool-arn` | `CognitoAuthenticationFactory` |
| `/cloudforge/shared/{region}/stack/{stackName}/cloudtrail/bucket-arn` | `ComplianceFactory` |
| `/cloudforge/shared/{region}/stack/{stackName}/config/bucket-arn` | `ComplianceFactory` |
| `/cloudforge/shared/{region}/stack/{stackName}/audit-manager/bucket-arn` | `ComplianceFactory` |

`ComplianceFactory` also records the CloudTrail trail ARN at `/cloudforge/{stackName}/{region}/cloudtrail/arn`.

`SharedResourceRegistry` builds names in the same layout:

| Method | Parameter |
|--------|-----------|
| `getCloudTrailParameterName()` | `/cloudforge/shared/{region}/stack/{stackName}/cloudtrail/arn` |
| `getBucketParameterName(purpose)` | `/cloudforge/shared/{region}/stack/{stackName}/s3/{purpose}/name` |
| `getCognitoUserPoolParameterName(poolName)` | `/cloudforge/shared/{region}/stack/{stackName}/cognito/{poolName}/id` |

### Region-Scoped Parameters

The AWS Config recorder and delivery channel are region-scoped because AWS allows only one of each per account and region:

| Parameter | Source |
|-----------|--------|
| `/cloudforge/shared/{region}/config/recorder-arn` | Written by `ComplianceFactory` when it creates the recorder |
| `/cloudforge/shared/{region}/config/channel-arn` | Written by `ComplianceFactory` when it creates the delivery channel |
| `/cloudforge/shared/{region}/config/recorder-name` | `SharedResourceRegistry.getConfigRecorderParameterName()` |
| `/cloudforge/shared/{region}/config/delivery-channel-name` | `SharedResourceRegistry.getConfigDeliveryChannelParameterName()` |

See [AWS Config Multi-Stack](compliance/AWS_CONFIG_MULTI_STACK.md).

## Behavior

1. **Stack isolation**: Each stack has its own SSM parameters.
2. **No conflicts**: Multiple stacks can coexist in the same region.
3. **Independent lifecycles**: Stacks can be created and destroyed independently.
4. **Clear ownership**: Parameter names identify the owning stack.

## Example Usage

```java
String stackName = Stack.of(this).getStackName();
SharedResourceRegistry registry = new SharedResourceRegistry(scope, region, stackName);

String bucketParam = registry.getBucketParameterName("alb-logs");
// /cloudforge/shared/us-east-1/stack/MyStack/s3/alb-logs/name

String recorderParam = registry.getConfigRecorderParameterName();
// /cloudforge/shared/us-east-1/config/recorder-name
```

## Multi-Stack Environments

Give each stack a distinct `stackName` in its deployment context. Each stack then has its own set of parameters:

- `/cloudforge/shared/us-east-1/stack/MyApp-Prod/...`
- `/cloudforge/shared/us-east-1/stack/MyApp-Staging/...`

Set `createConfigInfrastructure` on only one of them.

## Testing

```bash
mvn -pl cloudforge-api test -Dtest=SharedResourceRegistryTest
```

## See Also

- [SharedResourceRegistry.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/observability/SharedResourceRegistry.java)
