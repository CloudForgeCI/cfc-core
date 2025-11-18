# AWS Config Multi-Stack Deployment

## Problem

AWS Config only allows **ONE Configuration Recorder** and **ONE Delivery Channel** per region per account. These are account-level singleton resources that cannot be duplicated.

When deploying multiple stacks in the same region with `awsConfigEnabled=true`, the second stack will fail with:
```
cloudforge-config-channel already exists
```

## Solution

Use the `createConfigInfrastructure` deployment context flag to control which stack creates the Config infrastructure.

### First Stack (Primary)
Set `createConfigInfrastructure=true` (or omit it - defaults to true):

```json
{
  "stackName": "stack1",
  "context": {
    "awsConfigEnabled": true,
    "createConfigInfrastructure": true
  }
}
```

This stack will create:
- Configuration Recorder: `cloudforge-config-recorder`
- Delivery Channel: `cloudforge-config-channel`
- S3 Bucket: `stack1-config-{accountId}-{region}`
- All Config Rules

### Subsequent Stacks
Set `createConfigInfrastructure=false`:

```json
{
  "stackName": "stack2",
  "context": {
    "awsConfigEnabled": true,
    "createConfigInfrastructure": false
  }
}
```

This stack will:
- ✅ Reference existing Config Recorder
- ✅ Create its own Config Rules
- ❌ NOT create Recorder/Delivery Channel (already exists)

## Bucket Strategy

Each stack gets its own S3 bucket for Config data:
- Stack 1: `stack1-config-{accountId}-{region}`
- Stack 2: `stack2-config-{accountId}-{region}`

But they all share the same Configuration Recorder and Delivery Channel.

## Best Practices

1. **Single Stack per Region**: Only enable AWS Config in ONE stack per region
2. **Disable for Testing**: Set `awsConfigEnabled=false` for test stacks
3. **Manual Cleanup**: If Config Recorder exists from deleted stack, manually delete it:
   ```bash
   aws configservice delete-configuration-recorder --configuration-recorder-name cloudforge-config-recorder
   aws configservice delete-delivery-channel --delivery-channel-name cloudforge-config-channel
   ```

## References

- [AWS Config Limits](https://docs.aws.amazon.com/config/latest/developerguide/resource-config-reference.html)
- [ComplianceFactory.java](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java)
