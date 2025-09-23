package com.cloudforgeci.api.core.iam;

import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;

import java.util.List;
import java.util.Map;

/**
 * Permission Matrix defining the minimum required permissions for each topology/runtime combination.
 * This ensures that no unnecessary permissions are granted and follows the principle of least privilege.
 */
public final class PermissionMatrix {
    private PermissionMatrix() {}

    /**
     * Core permissions required for all Jenkins deployments regardless of topology/runtime.
     */
    public static final List<String> CORE_PERMISSIONS = List.of(
        "logs:CreateLogGroup",
        "logs:CreateLogStream", 
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
    );

    /**
     * EC2-specific permissions based on IAM profile.
     */
    public static final Map<IAMProfile, List<String>> EC2_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "ssm:GetParameter",
            "ssm:GetParameters",
            "ssm:GetParametersByPath",
            "ssm:SendCommand",
            "ssm:ListCommandInvocations",
            "cloudwatch:PutMetricData"
        ),
        IAMProfile.STANDARD, List.of(
            "ssm:GetParameter",
            "ssm:GetParameters", 
            "ssm:GetParametersByPath",
            "ssm:SendCommand",
            "ssm:ListCommandInvocations",
            "ssm:DescribeInstanceInformation",
            "cloudwatch:PutMetricData",
            "cloudwatch:GetMetricStatistics",
            "cloudwatch:ListMetrics",
            "s3:GetObject",
            "s3:PutObject",
            "s3:ListBucket"
        ),
        IAMProfile.EXTENDED, List.of(
            "ssm:*",
            "cloudwatch:*",
            "ec2:DescribeInstances",
            "ec2:DescribeVolumes",
            "ec2:DescribeSnapshots",
            "ec2:DescribeImages",
            "ec2:DescribeSecurityGroups",
            "ec2:DescribeVpcs",
            "ec2:DescribeSubnets",
            "s3:*"
        )
    );

    /**
     * Fargate-specific permissions based on IAM profile.
     */
    public static final Map<IAMProfile, List<String>> FARGATE_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "ecr:GetAuthorizationToken",
            "ecr:BatchCheckLayerAvailability",
            "ecr:GetDownloadUrlForLayer",
            "ecr:BatchGetImage"
        ),
        IAMProfile.STANDARD, List.of(
            "ecr:GetAuthorizationToken",
            "ecr:BatchCheckLayerAvailability", 
            "ecr:GetDownloadUrlForLayer",
            "ecr:BatchGetImage",
            "ecr:DescribeRepositories",
            "ecr:ListImages",
            "cloudwatch:PutMetricData",
            "cloudwatch:GetMetricStatistics",
            "cloudwatch:ListMetrics",
            "s3:GetObject",
            "s3:PutObject",
            "s3:ListBucket"
        ),
        IAMProfile.EXTENDED, List.of(
            "ecr:*",
            "ecs:DescribeClusters",
            "ecs:DescribeServices", 
            "ecs:DescribeTasks",
            "ecs:DescribeTaskDefinition",
            "ecs:ListTasks",
            "ecs:ListServices",
            "cloudwatch:*",
            "s3:*"
        )
    );

    /**
     * EFS permissions based on IAM profile.
     */
    public static final Map<IAMProfile, List<String>> EFS_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "elasticfilesystem:ClientMount",
            "elasticfilesystem:ClientWrite"
        ),
        IAMProfile.STANDARD, List.of(
            "elasticfilesystem:ClientMount",
            "elasticfilesystem:ClientWrite",
            "elasticfilesystem:ClientRootAccess",
            "elasticfilesystem:DescribeMountTargets"
        ),
        IAMProfile.EXTENDED, List.of(
            "elasticfilesystem:*"
        )
    );

    /**
     * ALB permissions based on IAM profile.
     */
    public static final Map<IAMProfile, List<String>> ALB_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "elasticloadbalancing:DescribeLoadBalancers",
            "elasticloadbalancing:DescribeTargetGroups",
            "elasticloadbalancing:DescribeTargetHealth"
        ),
        IAMProfile.STANDARD, List.of(
            "elasticloadbalancing:DescribeLoadBalancers",
            "elasticloadbalancing:DescribeTargetGroups", 
            "elasticloadbalancing:DescribeTargetHealth",
            "elasticloadbalancing:DescribeListeners",
            "elasticloadbalancing:DescribeRules"
        ),
        IAMProfile.EXTENDED, List.of(
            "elasticloadbalancing:*"
        )
    );

    /**
     * Gets the required permissions for a specific topology/runtime/iam combination.
     * 
     * @param topology the topology type
     * @param runtime the runtime type
     * @param iamProfile the IAM profile
     * @return list of required permissions
     */
    public static List<String> getRequiredPermissions(TopologyType topology, RuntimeType runtime, IAMProfile iamProfile) {
        List<String> permissions = new java.util.ArrayList<>(CORE_PERMISSIONS);
        
        // Add runtime-specific permissions
        if (runtime == RuntimeType.EC2) {
            permissions.addAll(EC2_PERMISSIONS.get(iamProfile));
        } else if (runtime == RuntimeType.FARGATE) {
            permissions.addAll(FARGATE_PERMISSIONS.get(iamProfile));
        }
        
        // Add topology-specific permissions
        if (topology == TopologyType.JENKINS_SERVICE || topology == TopologyType.JENKINS_SINGLE_NODE) {
            permissions.addAll(EFS_PERMISSIONS.get(iamProfile));
            permissions.addAll(ALB_PERMISSIONS.get(iamProfile));
        }
        
        return permissions;
    }

    /**
     * Validates that the provided permissions are appropriate for the given combination.
     * 
     * @param topology the topology type
     * @param runtime the runtime type
     * @param iamProfile the IAM profile
     * @param providedPermissions the permissions being granted
     * @return validation result with any issues found
     */
    public static ValidationResult validatePermissions(TopologyType topology, RuntimeType runtime, 
                                                     IAMProfile iamProfile, List<String> providedPermissions) {
        List<String> requiredPermissions = getRequiredPermissions(topology, runtime, iamProfile);
        List<String> issues = new java.util.ArrayList<>();
        
        // Check for missing required permissions
        for (String required : requiredPermissions) {
            if (!hasPermission(providedPermissions, required)) {
                issues.add("Missing required permission: " + required);
            }
        }
        
        // Check for excessive permissions (only for MINIMAL profile)
        if (iamProfile == IAMProfile.MINIMAL) {
            for (String provided : providedPermissions) {
                if (!isPermissionAllowed(requiredPermissions, provided)) {
                    issues.add("Excessive permission for MINIMAL profile: " + provided);
                }
            }
        }
        
        return new ValidationResult(issues.isEmpty(), issues);
    }

    /**
     * Checks if a permission is present in the provided list (supports wildcards).
     */
    private static boolean hasPermission(List<String> permissions, String required) {
        return permissions.stream().anyMatch(perm -> 
            perm.equals(required) || 
            perm.equals("*") || 
            perm.endsWith("*") && required.startsWith(perm.substring(0, perm.length() - 1))
        );
    }

    /**
     * Checks if a permission is allowed based on the required permissions list.
     */
    private static boolean isPermissionAllowed(List<String> allowedPermissions, String permission) {
        return allowedPermissions.stream().anyMatch(allowed -> 
            allowed.equals(permission) || 
            allowed.equals("*") || 
            allowed.endsWith("*") && permission.startsWith(allowed.substring(0, allowed.length() - 1))
        );
    }

    /**
     * Validation result containing success status and any issues found.
     */
    public static record ValidationResult(boolean isValid, List<String> issues) {
        public boolean hasIssues() {
            return !issues.isEmpty();
        }
        
        public String getIssuesAsString() {
            return String.join("\n", issues);
        }
    }
}
