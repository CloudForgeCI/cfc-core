# IAM Rules

This guide describes how CloudForge selects and creates the IAM roles for an application
deployment. An IAM profile (`MINIMAL`, `STANDARD`, or `EXTENDED`) determines which
permissions the instance, task execution, and task roles receive.

## Overview

IAM rules follow the same pattern as `RuntimeRules`, `TopologyRules`, and `SecurityRules`:
a configuration class for each profile validates the `SystemContext` and wires resources
into it. The IAM profile is derived from the security profile by default.

## Components

### IAMProfile

`com.cloudforge.core.enums.IAMProfile`

```java
public enum IAMProfile {
    MINIMAL,    // Least privilege; intended for production
    STANDARD,   // Operational permissions; intended for staging
    EXTENDED    // Broader permissions; intended for development only
}
```

### IAMConfiguration

`com.cloudforgeci.api.interfaces.IAMConfiguration`

Extends `BaseConfiguration` (`rules`, `wire`, `id`) and adds `kind()`, which returns the
`IAMProfile` the configuration implements.

### Profile Implementations

All three are in `com.cloudforgeci.api.core.iam`. Each creates an EC2 instance role for the
EC2 runtime, or a task execution role and task role for Fargate, and registers them on the
`SystemContext`. Refer to the classes for the exact statements; the summaries below describe
the differences.

**`MinimalIAMConfiguration`**
- SSM Session Manager and log permissions scoped to the application's log groups
- ECR image pull permissions for Fargate
- No wildcard service actions

**`StandardIAMConfiguration`**
- Everything in MINIMAL
- CloudWatch metric permissions (`PutMetricData`, `GetMetricStatistics`, `ListMetrics`)
- Log retention (`logs:PutRetentionPolicy`)
- EFS mount and describe permissions
- S3 object read, write, delete, and list

**`ExtendedIAMConfiguration`**
- Service-wide actions for `logs:*`, `cloudwatch:*`, `elasticfilesystem:*`, and `s3:*`
- EC2 describe actions and SSM command actions for troubleshooting
- Not intended for the PRODUCTION security profile

Each configuration's `rules()` checks that the VPC, the ALB security group, and (for EC2) the
instance security group are present in the `SystemContext`. Failures are reported as CDK
construct validation errors at synthesis.

### IAMProfileMapper

`com.cloudforge.core.iam.IAMProfileMapper`

Maps a security profile to its default IAM profile:

| Security profile | IAM profile |
|------------------|-------------|
| PRODUCTION | MINIMAL |
| STAGING | STANDARD |
| DEV | EXTENDED |

`isValidCombination(securityProfile, iamProfile)` reports which combinations are considered safe:

| Security profile | Allowed IAM profiles |
|------------------|----------------------|
| PRODUCTION | MINIMAL, STANDARD |
| STAGING | STANDARD, EXTENDED |
| DEV | Any |

`isValidCombination` is a helper; the factory methods do not call it. If you pass an explicit
IAM profile, check the combination yourself.

### IAMRules

`com.cloudforgeci.api.core.rules.IAMRules`

`IAMRules.install(SystemContext)` picks the configuration class for `ctx.iamProfile`, registers
its validation rules on the construct node, and calls `wire()` immediately so that runtime
factories can use the roles.

### PermissionMatrix

`com.cloudforgeci.api.core.iam.PermissionMatrix`

A reference table of the permissions expected for each runtime, topology, and IAM profile,
with a `validatePermissions` helper. It is not used to generate policies: the profile
implementations above define the statements that are actually synthesized.

## Usage

### Default IAM Profile

`ApplicationFactory` derives the IAM profile from the security profile:

```java
// IAM profile is IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION) = MINIMAL
ApplicationFactory.createFargate(scope, "App", cfc, SecurityProfile.PRODUCTION, applicationSpec);

// Security profile taken from the deployment context
ApplicationFactory.createEc2(scope, "App", cfc, applicationSpec);
```

### Explicit IAM Profile

```java
IAMProfile iam = IAMProfile.STANDARD;
if (!IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, iam)) {
    throw new IllegalArgumentException("Unsupported IAM profile for PRODUCTION: " + iam);
}
ApplicationFactory.createFargate(scope, "App", cfc, SecurityProfile.PRODUCTION, iam, applicationSpec);
```

When you start a `SystemContext` directly, pass the IAM profile explicitly:

```java
SystemContext.start(scope, TopologyType.APPLICATION_SERVICE, RuntimeType.FARGATE,
    SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);
```

Starting a second `SystemContext` in the same stack with a different IAM profile throws
`IllegalStateException`.

### Permission Validation

```java
var result = PermissionMatrix.validatePermissions(
    TopologyType.JENKINS_SERVICE,
    RuntimeType.EC2,
    IAMProfile.MINIMAL,
    providedPermissions
);

if (!result.isValid()) {
    System.out.println(result.getIssuesAsString());
}
```

`validatePermissions` reports missing permissions for every profile, and for `MINIMAL` also
reports permissions that are not in the matrix. `cloudforge-api/src/main/java/com/cloudforgeci/api/examples/IAMExample.java`
shows these calls together.

## PermissionMatrix Reference

Every combination includes the core log permissions:
`logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`,
`logs:DescribeLogGroups`, `logs:DescribeLogStreams`.

### EC2

| Profile | Permissions |
|---------|-------------|
| MINIMAL | `ssm:GetParameter`, `ssm:GetParameters`, `ssm:GetParametersByPath`, `ssm:SendCommand`, `ssm:ListCommandInvocations`, `cloudwatch:PutMetricData` |
| STANDARD | MINIMAL plus `ssm:DescribeInstanceInformation`, `cloudwatch:GetMetricStatistics`, `cloudwatch:ListMetrics`, `s3:GetObject`, `s3:PutObject`, `s3:ListBucket` |
| EXTENDED | `ssm:*`, `cloudwatch:*`, `s3:*`, and `ec2:Describe*` for instances, volumes, snapshots, images, security groups, VPCs, and subnets |

### Fargate

| Profile | Permissions |
|---------|-------------|
| MINIMAL | `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage` |
| STANDARD | MINIMAL plus `ecr:DescribeRepositories`, `ecr:ListImages`, `cloudwatch:PutMetricData`, `cloudwatch:GetMetricStatistics`, `cloudwatch:ListMetrics`, `s3:GetObject`, `s3:PutObject`, `s3:ListBucket` |
| EXTENDED | `ecr:*`, `ecs:DescribeClusters`, `ecs:DescribeServices`, `ecs:DescribeTasks`, `ecs:DescribeTaskDefinition`, `ecs:ListTasks`, `ecs:ListServices`, `cloudwatch:*`, `s3:*` |

### EFS and ALB (JENKINS_SERVICE topology only)

| Profile | EFS | ALB |
|---------|-----|-----|
| MINIMAL | `elasticfilesystem:ClientMount`, `elasticfilesystem:ClientWrite` | `elasticloadbalancing:DescribeLoadBalancers`, `DescribeTargetGroups`, `DescribeTargetHealth` |
| STANDARD | MINIMAL plus `elasticfilesystem:ClientRootAccess`, `elasticfilesystem:DescribeMountTargets` | MINIMAL plus `DescribeListeners`, `DescribeRules` |
| EXTENDED | `elasticfilesystem:*` | `elasticloadbalancing:*` |

## Compliance Considerations

IAM profiles contribute to, but do not by themselves satisfy, access-control requirements in
frameworks such as SOC 2, HIPAA, and PCI DSS:

- MINIMAL scopes log permissions to the application's log groups and avoids wildcard service actions.
- Separate profiles per environment support separating development and production access.
- EXTENDED grants service-wide actions and should not be used where these frameworks apply.

Review the synthesized policies for your workload. For how compliance checks are tested, see
[CSV Parameterized Testing](../compliance/CSV_PARAMETERIZED_TESTING.md).

## Troubleshooting

**`SystemContext already started with iamProfile = ...`**
The stack already has a `SystemContext` with a different IAM profile. Use one IAM profile per stack.

**`Missing required permission: ssm:GetParameter`** (from `PermissionMatrix.validatePermissions`)
The permission list you validated lacks a permission the matrix expects for that combination.

**`Excessive permission for MINIMAL profile: s3:*`**
The permission list includes a permission outside the MINIMAL matrix entry.

**Debugging**
- `SystemContext.debugPath(scope)` prints the runtime, topology, security profile, IAM profile, and populated slots.
- `IAMProfileMapper.mapFromSecurity(profile)` returns the default IAM profile for a security profile.
