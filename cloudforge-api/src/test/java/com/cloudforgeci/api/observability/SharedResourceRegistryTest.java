package com.cloudforgeci.api.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SharedResourceRegistry.
 *
 * Tests the shared resource registry functionality for tracking and reusing
 * account-level AWS resources across multiple CloudFormation stacks.
 */
class SharedResourceRegistryTest {

    private App app;
    private Stack stack;
    private SharedResourceRegistry registry;
    private static final String TEST_REGION = "us-east-1";
    private static final String TEST_STACK_NAME = "TestStack";

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, TEST_STACK_NAME);
        registry = new SharedResourceRegistry(stack, TEST_REGION, TEST_STACK_NAME);
    }

    @Test
    void testConstructorWithValidParameters() {
        // When: Creating a registry with valid parameters
        SharedResourceRegistry reg = new SharedResourceRegistry(stack, "us-west-2", "TestStack2");

        // Then: Should initialize successfully
        assertNotNull(reg);
    }

    @Test
    void testGetCloudTrailParameterName() {
        // When: Getting CloudTrail parameter name
        String paramName = registry.getCloudTrailParameterName();

        // Then: Should follow the correct format with stack name
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/cloudtrail/arn", paramName);
        assertTrue(paramName.contains(TEST_REGION));
        assertTrue(paramName.contains("cloudtrail"));
        assertTrue(paramName.contains(TEST_STACK_NAME));
    }

    @Test
    void testGetCloudTrailParameterNameDifferentRegions() {
        // Given: Registries for different regions
        SharedResourceRegistry usEast1 = new SharedResourceRegistry(stack, "us-east-1", TEST_STACK_NAME);
        SharedResourceRegistry usWest2 = new SharedResourceRegistry(stack, "us-west-2", TEST_STACK_NAME);
        SharedResourceRegistry euWest1 = new SharedResourceRegistry(stack, "eu-west-1", TEST_STACK_NAME);

        // When: Getting CloudTrail parameter names
        String paramEast = usEast1.getCloudTrailParameterName();
        String paramWest = usWest2.getCloudTrailParameterName();
        String paramEu = euWest1.getCloudTrailParameterName();

        // Then: Each should have region-specific and stack-scoped parameter name
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/cloudtrail/arn", paramEast);
        assertEquals("/cloudforge/shared/us-west-2/stack/TestStack/cloudtrail/arn", paramWest);
        assertEquals("/cloudforge/shared/eu-west-1/stack/TestStack/cloudtrail/arn", paramEu);

        // And: All should be different
        assertNotEquals(paramEast, paramWest);
        assertNotEquals(paramEast, paramEu);
        assertNotEquals(paramWest, paramEu);
    }

    @Test
    void testGetConfigRecorderParameterName() {
        // When: Getting Config Recorder parameter name
        String paramName = registry.getConfigRecorderParameterName();

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/config/recorder-name", paramName);
        assertTrue(paramName.contains(TEST_REGION));
        assertTrue(paramName.contains("config"));
        assertTrue(paramName.contains("recorder-name"));
    }

    @Test
    void testGetConfigDeliveryChannelParameterName() {
        // When: Getting Config Delivery Channel parameter name
        String paramName = registry.getConfigDeliveryChannelParameterName();

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/config/delivery-channel-name", paramName);
        assertTrue(paramName.contains(TEST_REGION));
        assertTrue(paramName.contains("config"));
        assertTrue(paramName.contains("delivery-channel-name"));
    }

    @Test
    void testGetBucketParameterNameWithCloudTrailPurpose() {
        // When: Getting bucket parameter name for CloudTrail
        String paramName = registry.getBucketParameterName("cloudtrail");

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/s3/cloudtrail/name", paramName);
        assertTrue(paramName.contains("cloudtrail"));
    }

    @Test
    void testGetBucketParameterNameWithConfigPurpose() {
        // When: Getting bucket parameter name for AWS Config
        String paramName = registry.getBucketParameterName("config");

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/s3/config/name", paramName);
        assertTrue(paramName.contains("config"));
    }

    @Test
    void testGetBucketParameterNameWithAlbLogsPurpose() {
        // When: Getting bucket parameter name for ALB logs
        String paramName = registry.getBucketParameterName("alb-logs");

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/s3/alb-logs/name", paramName);
        assertTrue(paramName.contains("alb-logs"));
    }

    @Test
    void testGetBucketParameterNameWithCustomPurpose() {
        // When: Getting bucket parameter name for custom purpose
        String paramName = registry.getBucketParameterName("my-custom-bucket");

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/s3/my-custom-bucket/name", paramName);
        assertTrue(paramName.contains("my-custom-bucket"));
    }

    @Test
    void testGetCognitoUserPoolParameterName() {
        // When: Getting Cognito User Pool parameter name
        String paramName = registry.getCognitoUserPoolParameterName("jenkins-users");

        // Then: Should follow the correct format
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/cognito/jenkins-users/id", paramName);
        assertTrue(paramName.contains("cognito"));
        assertTrue(paramName.contains("jenkins-users"));
    }

    @Test
    void testGetCognitoUserPoolParameterNameWithDifferentPools() {
        // When: Getting parameter names for different user pools
        String param1 = registry.getCognitoUserPoolParameterName("jenkins-users");
        String param2 = registry.getCognitoUserPoolParameterName("admin-users");
        String param3 = registry.getCognitoUserPoolParameterName("api-users");

        // Then: Each should have pool-specific and stack-scoped parameter name
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/cognito/jenkins-users/id", param1);
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/cognito/admin-users/id", param2);
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/cognito/api-users/id", param3);

        // And: All should be different
        assertNotEquals(param1, param2);
        assertNotEquals(param1, param3);
        assertNotEquals(param2, param3);
    }

    @Test
    void testStoreParameter() {
        // When: Storing a parameter (should not throw exception)
        assertDoesNotThrow(() -> {
            registry.storeParameter(
                "/cloudforge/shared/us-east-1/test/resource-id",
                "test-value-123",
                "Test resource for unit testing"
            );
        });
    }

    @Test
    void testStoreParameterWithCloudTrail() {
        // When: Storing CloudTrail ARN
        String paramName = registry.getCloudTrailParameterName();
        String trailArn = "arn:aws:cloudtrail:us-east-1:123456789012:trail/MyTrail";

        assertDoesNotThrow(() -> {
            registry.storeParameter(paramName, trailArn, "Shared CloudTrail for account");
        });
    }

    @Test
    void testStoreParameterWithConfigRecorder() {
        // When: Storing Config Recorder name
        String paramName = registry.getConfigRecorderParameterName();
        String recorderName = "cloudforge-config-recorder";

        assertDoesNotThrow(() -> {
            registry.storeParameter(paramName, recorderName, "Shared Config Recorder");
        });
    }

    @Test
    void testStoreParameterWithBucket() {
        // When: Storing S3 bucket name
        String paramName = registry.getBucketParameterName("cloudtrail");
        String bucketName = "cloudforge-cloudtrail-logs-123456789012";

        assertDoesNotThrow(() -> {
            registry.storeParameter(paramName, bucketName, "CloudTrail logs bucket");
        });
    }

    @Test
    void testStoreParameterWithCognitoPool() {
        // When: Storing Cognito User Pool ID
        String paramName = registry.getCognitoUserPoolParameterName("jenkins-users");
        String poolId = "us-east-1_AbCdEfGhI";

        assertDoesNotThrow(() -> {
            registry.storeParameter(paramName, poolId, "Jenkins authentication user pool");
        });
    }

    @Test
    void testParameterNamingConsistency() {
        // Given: Multiple calls to the same parameter name methods
        String cloudTrail1 = registry.getCloudTrailParameterName();
        String cloudTrail2 = registry.getCloudTrailParameterName();

        String configRec1 = registry.getConfigRecorderParameterName();
        String configRec2 = registry.getConfigRecorderParameterName();

        String bucket1 = registry.getBucketParameterName("cloudtrail");
        String bucket2 = registry.getBucketParameterName("cloudtrail");

        // Then: Should return identical values
        assertEquals(cloudTrail1, cloudTrail2);
        assertEquals(configRec1, configRec2);
        assertEquals(bucket1, bucket2);
    }

    @Test
    void testParameterNamePrefixConsistency() {
        // When: Getting various parameter names
        String cloudTrail = registry.getCloudTrailParameterName();
        String configRec = registry.getConfigRecorderParameterName();
        String configChannel = registry.getConfigDeliveryChannelParameterName();
        String bucket = registry.getBucketParameterName("test");
        String cognito = registry.getCognitoUserPoolParameterName("test");

        // Then: All should start with the same prefix
        String expectedPrefix = "/cloudforge/shared/" + TEST_REGION;
        assertTrue(cloudTrail.startsWith(expectedPrefix));
        assertTrue(configRec.startsWith(expectedPrefix));
        assertTrue(configChannel.startsWith(expectedPrefix));
        assertTrue(bucket.startsWith(expectedPrefix));
        assertTrue(cognito.startsWith(expectedPrefix));
    }

    @Test
    void testParameterNameUniqueness() {
        // When: Getting all different parameter types
        String cloudTrail = registry.getCloudTrailParameterName();
        String configRec = registry.getConfigRecorderParameterName();
        String configChannel = registry.getConfigDeliveryChannelParameterName();
        String bucket = registry.getBucketParameterName("cloudtrail");
        String cognito = registry.getCognitoUserPoolParameterName("users");

        // Then: All should be unique
        assertNotEquals(cloudTrail, configRec);
        assertNotEquals(cloudTrail, configChannel);
        assertNotEquals(cloudTrail, bucket);
        assertNotEquals(cloudTrail, cognito);
        assertNotEquals(configRec, configChannel);
        assertNotEquals(configRec, bucket);
        assertNotEquals(configRec, cognito);
        assertNotEquals(configChannel, bucket);
        assertNotEquals(configChannel, cognito);
        assertNotEquals(bucket, cognito);
    }

    @Test
    void testMultipleRegistriesForSameRegion() {
        // Given: Multiple registries for the same region
        Stack stack2 = new Stack(app, "TestStack2");
        SharedResourceRegistry registry1 = new SharedResourceRegistry(stack, TEST_REGION, "Stack1");
        SharedResourceRegistry registry2 = new SharedResourceRegistry(stack2, TEST_REGION, "Stack2");

        // When: Getting parameter names from both
        String param1 = registry1.getCloudTrailParameterName();
        String param2 = registry2.getCloudTrailParameterName();

        // Then: Should return DIFFERENT parameter names (stack-scoped for isolation)
        assertNotEquals(param1, param2, "Different stacks should have different SSM parameters");
        assertTrue(param1.contains("Stack1"), "Stack1 parameter should contain stack name");
        assertTrue(param2.contains("Stack2"), "Stack2 parameter should contain stack name");
    }

    @Test
    void testStoreAndRetrieveWorkflow() {
        // This test verifies the intended workflow pattern
        // Given: Parameter names for various resources
        String cloudTrailParam = registry.getCloudTrailParameterName();
        String bucketParam = registry.getBucketParameterName("cloudtrail");
        String cognitoParam = registry.getCognitoUserPoolParameterName("jenkins-users");

        // When: Storing parameters (simulating first stack deployment)
        assertDoesNotThrow(() -> {
            registry.storeParameter(
                cloudTrailParam,
                "arn:aws:cloudtrail:us-east-1:123456789012:trail/MyTrail",
                "Shared CloudTrail"
            );
            registry.storeParameter(
                bucketParam,
                "cloudforge-cloudtrail-123456789012",
                "CloudTrail logs bucket"
            );
            registry.storeParameter(
                cognitoParam,
                "us-east-1_AbCdEfGhI",
                "Jenkins user pool"
            );
        });

        // Then: Parameter names should remain consistent for retrieval
        assertEquals(cloudTrailParam, registry.getCloudTrailParameterName());
        assertEquals(bucketParam, registry.getBucketParameterName("cloudtrail"));
        assertEquals(cognitoParam, registry.getCognitoUserPoolParameterName("jenkins-users"));
    }

    @Test
    void testRegionIsolation() {
        // Given: Registries for different regions
        SharedResourceRegistry usEast1 = new SharedResourceRegistry(stack, "us-east-1", TEST_STACK_NAME);
        SharedResourceRegistry usWest2 = new SharedResourceRegistry(stack, "us-west-2", TEST_STACK_NAME);

        // When: Getting same resource type in different regions
        String cloudTrailEast = usEast1.getCloudTrailParameterName();
        String cloudTrailWest = usWest2.getCloudTrailParameterName();

        String configEast = usEast1.getConfigRecorderParameterName();
        String configWest = usWest2.getConfigRecorderParameterName();

        // Then: Parameter names should be region-specific
        assertNotEquals(cloudTrailEast, cloudTrailWest);
        assertNotEquals(configEast, configWest);

        assertTrue(cloudTrailEast.contains("us-east-1"));
        assertTrue(cloudTrailWest.contains("us-west-2"));
    }

    @Test
    void testBucketPurposeVariety() {
        // When: Creating parameter names for various bucket purposes
        String[] purposes = {
            "cloudtrail",
            "config",
            "alb-logs",
            "vpc-flow-logs",
            "access-logs",
            "audit-logs"
        };

        // Then: Each should have unique parameter name
        for (int i = 0; i < purposes.length; i++) {
            String param1 = registry.getBucketParameterName(purposes[i]);
            assertTrue(param1.contains(purposes[i]));

            for (int j = i + 1; j < purposes.length; j++) {
                String param2 = registry.getBucketParameterName(purposes[j]);
                assertNotEquals(param1, param2);
            }
        }
    }

    @Test
    void testParameterNameFormatting() {
        // When: Getting parameter names
        String cloudTrail = registry.getCloudTrailParameterName();
        String configRec = registry.getConfigRecorderParameterName();
        String bucket = registry.getBucketParameterName("test-bucket");

        // Then: Should not contain spaces or invalid characters
        assertFalse(cloudTrail.contains(" "));
        assertFalse(configRec.contains(" "));
        assertFalse(bucket.contains(" "));

        // And: Should use forward slashes for hierarchy
        assertTrue(cloudTrail.contains("/"));
        assertTrue(configRec.contains("/"));
        assertTrue(bucket.contains("/"));
    }

    @Test
    void testConstructorWithDifferentConstructTypes() {
        // Given: Different types of constructs
        App app = new App();
        Stack stack1 = new Stack(app, "Stack1");
        Construct construct1 = new Construct(stack1, "Construct1");

        // When: Creating registries with different construct scopes
        SharedResourceRegistry reg1 = new SharedResourceRegistry(stack1, "us-east-1", "Stack1");
        SharedResourceRegistry reg2 = new SharedResourceRegistry(construct1, "us-east-1", "Construct1");

        // Then: Each should have their own stack-scoped parameter names
        assertNotEquals(
            reg1.getCloudTrailParameterName(),
            reg2.getCloudTrailParameterName(),
            "Different stack names should produce different parameter names"
        );
    }

    @Test
    void testEmptyStringPurpose() {
        // When: Using empty string as purpose
        String paramName = registry.getBucketParameterName("");

        // Then: Should still create valid parameter name
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/s3//name", paramName);
    }

    @Test
    void testSpecialCharactersInPurpose() {
        // When: Using special characters in purpose (hyphens are common in bucket names)
        String paramName = registry.getBucketParameterName("my-special-bucket-2024");

        // Then: Should preserve the special characters
        assertTrue(paramName.contains("my-special-bucket-2024"));
        assertEquals("/cloudforge/shared/us-east-1/stack/TestStack/s3/my-special-bucket-2024/name", paramName);
    }

    @Test
    void testMultipleStoresWithDifferentParameters() {
        // Given: Different parameter names
        String paramName1 = "/cloudforge/shared/us-east-1/test/resource-1";
        String paramName2 = "/cloudforge/shared/us-east-1/test/resource-2";

        // When: Storing multiple parameters
        assertDoesNotThrow(() -> {
            registry.storeParameter(paramName1, "value1", "First resource");
            registry.storeParameter(paramName2, "value2", "Second resource");
        });

        // Then: Should not throw exceptions
    }
}
