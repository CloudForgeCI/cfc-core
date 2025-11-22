package com.cloudforgeci.api.core.iam;

import com.cloudforgeci.api.interfaces.IAMProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for PermissionMatrix.
 *
 * Tests permission definitions and matrix logic.
 */
class PermissionMatrixTest {

    @Test
    void testPermissionMatrixClassExists() {
        // When: Accessing PermissionMatrix class
        Class<?> matrixClass = PermissionMatrix.class;

        // Then: Should exist
        assertNotNull(matrixClass);
        assertEquals("PermissionMatrix", matrixClass.getSimpleName());
    }

    @Test
    void testPermissionMatrixIsFinal() {
        // When: Checking PermissionMatrix modifiers
        Class<?> matrixClass = PermissionMatrix.class;

        // Then: Should be final (utility class)
        assertTrue(java.lang.reflect.Modifier.isFinal(matrixClass.getModifiers()));
    }

    @Test
    void testPermissionMatrixIsPublic() {
        // When: Checking PermissionMatrix modifiers
        Class<?> matrixClass = PermissionMatrix.class;

        // Then: Should be public
        assertTrue(java.lang.reflect.Modifier.isPublic(matrixClass.getModifiers()));
    }

    @Test
    void testCorePermissionsExists() {
        // When: Getting CORE_PERMISSIONS
        List<String> corePermissions = PermissionMatrix.CORE_PERMISSIONS;

        // Then: Should exist and not be null
        assertNotNull(corePermissions);
    }

    @Test
    void testCorePermissionsNotEmpty() {
        // When: Getting CORE_PERMISSIONS
        List<String> corePermissions = PermissionMatrix.CORE_PERMISSIONS;

        // Then: Should have permissions
        assertFalse(corePermissions.isEmpty(), "Core permissions should not be empty");
    }

    @Test
    void testCorePermissionsHasLoggingPermissions() {
        // When: Getting CORE_PERMISSIONS
        List<String> corePermissions = PermissionMatrix.CORE_PERMISSIONS;

        // Then: Should contain CloudWatch Logs permissions
        assertTrue(corePermissions.contains("logs:CreateLogGroup"));
        assertTrue(corePermissions.contains("logs:CreateLogStream"));
        assertTrue(corePermissions.contains("logs:PutLogEvents"));
    }

    @Test
    void testCorePermissionsIsImmutable() {
        // When: Getting CORE_PERMISSIONS
        List<String> corePermissions = PermissionMatrix.CORE_PERMISSIONS;

        // Then: Should throw when trying to modify
        assertThrows(UnsupportedOperationException.class, () -> {
            corePermissions.add("new:Permission");
        });
    }

    @Test
    void testEc2PermissionsExists() {
        // When: Getting EC2_PERMISSIONS
        var ec2Permissions = PermissionMatrix.EC2_PERMISSIONS;

        // Then: Should exist and not be null
        assertNotNull(ec2Permissions);
    }

    @Test
    void testEc2PermissionsNotEmpty() {
        // When: Getting EC2_PERMISSIONS
        var ec2Permissions = PermissionMatrix.EC2_PERMISSIONS;

        // Then: Should have mappings for IAM profiles
        assertFalse(ec2Permissions.isEmpty());
    }

    @Test
    void testEc2PermissionsHasAllProfiles() {
        // When: Getting EC2_PERMISSIONS
        var ec2Permissions = PermissionMatrix.EC2_PERMISSIONS;

        // Then: Should have entries for all IAM profiles
        assertTrue(ec2Permissions.containsKey(IAMProfile.MINIMAL));
        assertTrue(ec2Permissions.containsKey(IAMProfile.STANDARD));
        assertTrue(ec2Permissions.containsKey(IAMProfile.EXTENDED));
    }

    @Test
    void testEc2MinimalPermissions() {
        // When: Getting minimal EC2 permissions
        List<String> minimalPerms = PermissionMatrix.EC2_PERMISSIONS.get(IAMProfile.MINIMAL);

        // Then: Should have SSM and CloudWatch permissions
        assertNotNull(minimalPerms);
        assertFalse(minimalPerms.isEmpty());
        assertTrue(minimalPerms.stream().anyMatch(p -> p.startsWith("ssm:")));
        assertTrue(minimalPerms.stream().anyMatch(p -> p.startsWith("cloudwatch:")));
    }

    @Test
    void testEc2StandardPermissions() {
        // When: Getting standard EC2 permissions
        List<String> standardPerms = PermissionMatrix.EC2_PERMISSIONS.get(IAMProfile.STANDARD);

        // Then: Should have more permissions than minimal
        assertNotNull(standardPerms);
        assertFalse(standardPerms.isEmpty());
        assertTrue(standardPerms.stream().anyMatch(p -> p.startsWith("s3:")));
    }

    @Test
    void testEc2ExtendedPermissions() {
        // When: Getting extended EC2 permissions
        List<String> extendedPerms = PermissionMatrix.EC2_PERMISSIONS.get(IAMProfile.EXTENDED);

        // Then: Should have comprehensive permissions
        assertNotNull(extendedPerms);
        assertFalse(extendedPerms.isEmpty());
        assertTrue(extendedPerms.stream().anyMatch(p -> p.startsWith("ec2:")));
    }

    @Test
    void testFargatePermissionsExists() {
        // When: Getting FARGATE_PERMISSIONS
        var fargatePermissions = PermissionMatrix.FARGATE_PERMISSIONS;

        // Then: Should exist and not be null
        assertNotNull(fargatePermissions);
    }

    @Test
    void testFargatePermissionsNotEmpty() {
        // When: Getting FARGATE_PERMISSIONS
        var fargatePermissions = PermissionMatrix.FARGATE_PERMISSIONS;

        // Then: Should have mappings for IAM profiles
        assertFalse(fargatePermissions.isEmpty());
    }

    @Test
    void testFargatePermissionsHasAllProfiles() {
        // When: Getting FARGATE_PERMISSIONS
        var fargatePermissions = PermissionMatrix.FARGATE_PERMISSIONS;

        // Then: Should have entries for all IAM profiles
        assertTrue(fargatePermissions.containsKey(IAMProfile.MINIMAL));
        assertTrue(fargatePermissions.containsKey(IAMProfile.STANDARD));
        assertTrue(fargatePermissions.containsKey(IAMProfile.EXTENDED));
    }

    @Test
    void testFargateMinimalPermissions() {
        // When: Getting minimal Fargate permissions
        List<String> minimalPerms = PermissionMatrix.FARGATE_PERMISSIONS.get(IAMProfile.MINIMAL);

        // Then: Should have ECR permissions
        assertNotNull(minimalPerms);
        assertFalse(minimalPerms.isEmpty());
        assertTrue(minimalPerms.stream().anyMatch(p -> p.startsWith("ecr:")));
    }

    @Test
    void testFargateStandardPermissions() {
        // When: Getting standard Fargate permissions
        List<String> standardPerms = PermissionMatrix.FARGATE_PERMISSIONS.get(IAMProfile.STANDARD);

        // Then: Should have ECR permissions
        assertNotNull(standardPerms);
        assertFalse(standardPerms.isEmpty());
        assertTrue(standardPerms.stream().anyMatch(p -> p.startsWith("ecr:")));
    }

    @Test
    void testFargateExtendedPermissions() {
        // When: Getting extended Fargate permissions
        List<String> extendedPerms = PermissionMatrix.FARGATE_PERMISSIONS.get(IAMProfile.EXTENDED);

        // Then: Should have comprehensive permissions
        assertNotNull(extendedPerms);
        assertFalse(extendedPerms.isEmpty());
    }

    @Test
    void testEfsPermissionsExists() {
        // When: Getting EFS_PERMISSIONS
        var efsPermissions = PermissionMatrix.EFS_PERMISSIONS;

        // Then: Should exist and not be null
        assertNotNull(efsPermissions);
    }

    @Test
    void testEfsPermissionsHasAllProfiles() {
        // When: Getting EFS_PERMISSIONS
        var efsPermissions = PermissionMatrix.EFS_PERMISSIONS;

        // Then: Should have entries for all IAM profiles
        assertTrue(efsPermissions.containsKey(IAMProfile.MINIMAL));
        assertTrue(efsPermissions.containsKey(IAMProfile.STANDARD));
        assertTrue(efsPermissions.containsKey(IAMProfile.EXTENDED));
    }

    @Test
    void testAlbPermissionsExists() {
        // When: Getting ALB_PERMISSIONS
        var albPermissions = PermissionMatrix.ALB_PERMISSIONS;

        // Then: Should exist and not be null
        assertNotNull(albPermissions);
    }

    @Test
    void testAlbPermissionsHasAllProfiles() {
        // When: Getting ALB_PERMISSIONS
        var albPermissions = PermissionMatrix.ALB_PERMISSIONS;

        // Then: Should have entries for all IAM profiles
        assertTrue(albPermissions.containsKey(IAMProfile.MINIMAL));
        assertTrue(albPermissions.containsKey(IAMProfile.STANDARD));
        assertTrue(albPermissions.containsKey(IAMProfile.EXTENDED));
    }

    @Test
    void testPermissionMatrixPackage() {
        // When: Getting package
        Package pkg = PermissionMatrix.class.getPackage();

        // Then: Should be in iam package
        assertNotNull(pkg);
        assertEquals("com.cloudforgeci.api.core.iam", pkg.getName());
    }

    @Test
    void testPermissionMatrixHasPrivateConstructor() throws NoSuchMethodException {
        // When: Getting constructor
        var constructor = PermissionMatrix.class.getDeclaredConstructor();

        // Then: Should be private (utility class)
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void testPermissionMatrixHasNoPublicConstructor() {
        // When: Getting public constructors
        var constructors = PermissionMatrix.class.getConstructors();

        // Then: Should have no public constructors
        assertEquals(0, constructors.length, "Utility class should have no public constructors");
    }

    @Test
    void testGetRequiredPermissionsMethodExists() throws NoSuchMethodException {
        // When: Getting getRequiredPermissions method
        var method = PermissionMatrix.class.getDeclaredMethod(
            "getRequiredPermissions",
            com.cloudforgeci.api.interfaces.TopologyType.class,
            com.cloudforgeci.api.interfaces.RuntimeType.class,
            com.cloudforgeci.api.interfaces.IAMProfile.class
        );

        // Then: Should exist and be public static
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void testGetRequiredPermissionsReturnType() throws NoSuchMethodException {
        // When: Getting getRequiredPermissions method
        var method = PermissionMatrix.class.getDeclaredMethod(
            "getRequiredPermissions",
            com.cloudforgeci.api.interfaces.TopologyType.class,
            com.cloudforgeci.api.interfaces.RuntimeType.class,
            com.cloudforgeci.api.interfaces.IAMProfile.class
        );

        // Then: Should return List
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void testGetRequiredPermissionsEc2Minimal() {
        // When: Getting required permissions for EC2 minimal
        List<String> permissions = PermissionMatrix.getRequiredPermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL
        );

        // Then: Should include core, EC2, EFS, and ALB permissions
        assertNotNull(permissions);
        assertTrue(permissions.size() > PermissionMatrix.CORE_PERMISSIONS.size());
        assertTrue(permissions.containsAll(PermissionMatrix.CORE_PERMISSIONS));
    }

    @Test
    void testGetRequiredPermissionsFargateMinimal() {
        // When: Getting required permissions for Fargate minimal
        List<String> permissions = PermissionMatrix.getRequiredPermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.FARGATE,
            IAMProfile.MINIMAL
        );

        // Then: Should include core, Fargate, EFS, and ALB permissions
        assertNotNull(permissions);
        assertTrue(permissions.size() > PermissionMatrix.CORE_PERMISSIONS.size());
        assertTrue(permissions.containsAll(PermissionMatrix.CORE_PERMISSIONS));
    }

    @Test
    void testGetRequiredPermissionsS3Website() {
        // When: Getting required permissions for S3 website
        List<String> permissions = PermissionMatrix.getRequiredPermissions(
            com.cloudforgeci.api.interfaces.TopologyType.S3_WEBSITE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL
        );

        // Then: Should include core and runtime permissions
        assertNotNull(permissions);
        assertTrue(permissions.containsAll(PermissionMatrix.CORE_PERMISSIONS));
    }

    @Test
    void testValidatePermissionsMethodExists() throws NoSuchMethodException {
        // When: Getting validatePermissions method
        var method = PermissionMatrix.class.getDeclaredMethod(
            "validatePermissions",
            com.cloudforgeci.api.interfaces.TopologyType.class,
            com.cloudforgeci.api.interfaces.RuntimeType.class,
            IAMProfile.class,
            List.class
        );

        // Then: Should exist and be public static
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void testValidatePermissionsValid() {
        // Given: Complete required permissions
        List<String> required = PermissionMatrix.getRequiredPermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL
        );

        // When: Validating with all required permissions
        var result = PermissionMatrix.validatePermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL,
            required
        );

        // Then: Should be valid
        assertNotNull(result);
        assertTrue(result.isValid());
        assertFalse(result.hasIssues());
    }

    @Test
    void testValidatePermissionsMissing() {
        // Given: Incomplete permissions
        List<String> incomplete = List.of("logs:CreateLogGroup");

        // When: Validating with missing permissions
        var result = PermissionMatrix.validatePermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL,
            incomplete
        );

        // Then: Should be invalid with issues
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.hasIssues());
        assertFalse(result.issues().isEmpty());
    }

    @Test
    void testValidationResultRecord() {
        // When: Creating ValidationResult
        var result = new PermissionMatrix.ValidationResult(true, List.of());

        // Then: Should have expected properties
        assertTrue(result.isValid());
        assertFalse(result.hasIssues());
        assertNotNull(result.issues());
    }

    @Test
    void testValidationResultWithIssues() {
        // When: Creating ValidationResult with issues
        var result = new PermissionMatrix.ValidationResult(false, List.of("Issue 1", "Issue 2"));

        // Then: Should have issues
        assertFalse(result.isValid());
        assertTrue(result.hasIssues());
        assertEquals(2, result.issues().size());
    }

    @Test
    void testValidationResultGetIssuesAsString() {
        // When: Creating ValidationResult with issues
        var result = new PermissionMatrix.ValidationResult(false, List.of("Issue 1", "Issue 2"));

        // Then: Issues should be joined with newlines
        String issuesString = result.getIssuesAsString();
        assertNotNull(issuesString);
        assertTrue(issuesString.contains("Issue 1"));
        assertTrue(issuesString.contains("Issue 2"));
    }

    @Test
    void testPermissionMatrixFieldsArePublicStaticFinal() {
        // When: Getting declared fields
        var fields = PermissionMatrix.class.getDeclaredFields();

        // Then: All fields should be public static final
        for (var field : fields) {
            assertTrue(java.lang.reflect.Modifier.isPublic(field.getModifiers()),
                "Field " + field.getName() + " should be public");
            assertTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()),
                "Field " + field.getName() + " should be static");
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                "Field " + field.getName() + " should be final");
        }
    }

    @Test
    void testPermissionMatrixHasExpectedFieldCount() {
        // When: Getting declared fields
        var fields = PermissionMatrix.class.getDeclaredFields();

        // Then: Should have reasonable number of permission maps
        assertTrue(fields.length >= 3, "Should have at least 3 permission constants");
        assertTrue(fields.length < 20, "Should not have too many fields");
    }

    @Test
    void testCorePermissionsSize() {
        // When: Getting CORE_PERMISSIONS
        List<String> corePermissions = PermissionMatrix.CORE_PERMISSIONS;

        // Then: Should have expected number of permissions
        assertTrue(corePermissions.size() >= 3, "Should have at least 3 core permissions");
        assertTrue(corePermissions.size() < 20, "Core permissions should be minimal");
    }

    @Test
    void testPermissionsFollowAwsFormat() {
        // When: Getting CORE_PERMISSIONS
        List<String> corePermissions = PermissionMatrix.CORE_PERMISSIONS;

        // Then: All permissions should follow AWS format (service:Action)
        for (String permission : corePermissions) {
            assertTrue(permission.contains(":"),
                "Permission '" + permission + "' should follow AWS format (service:Action)");
        }
    }
}
