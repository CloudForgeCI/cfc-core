package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.enums.AuthMode;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Identity Center User Factory for programmatic user and group management.
 *
 * <p>This factory uses the AWS Identity Store API to create users and groups
 * directly in IAM Identity Center's identity store, enabling fully automated
 * user provisioning without external identity providers.</p>
 *
 * <p><b>Why Use This Approach:</b></p>
 * <ul>
 *   <li>Fully automated - Identity Store API supports complete user/group management</li>
 *   <li>No external IdP needed (Cognito, Azure AD, etc.)</li>
 *   <li>Identity Center issues SAML directly from its own identity store</li>
 *   <li>Simpler architecture - single identity source</li>
 * </ul>
 *
 * <p><b>Quick Start:</b></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "oidcProvider": "identity-center-saml",
 *   "autoProvisionIdentityCenter": true,
 *   "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx",
 *   "identityCenterInitialAdminEmail": "admin@example.com",
 *   "identityCenterGroups": ["Administrators", "Analysts", "Viewers"]
 * }
 * </pre>
 *
 * <p><b>What Gets Created:</b></p>
 * <ul>
 *   <li>Initial admin user in Identity Center identity store</li>
 *   <li>Groups in Identity Center identity store</li>
 *   <li>Admin user added to Administrators group</li>
 *   <li>SAML 2.0 application in Identity Center (via IdentityCenterSamlFactory)</li>
 * </ul>
 *
 * <p><b>Prerequisites:</b></p>
 * <ul>
 *   <li>AWS Organizations enabled</li>
 *   <li>IAM Identity Center enabled</li>
 *   <li>SSO Instance ARN available</li>
 * </ul>
 *
 * <p><b>Identity Store API Reference:</b></p>
 * @see <a href="https://docs.aws.amazon.com/singlesignon/latest/IdentityStoreAPIReference/welcome.html">Identity Store API</a>
 */
public class IdentityCenterUserFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(IdentityCenterUserFactory.class.getName());

    @DeploymentContext("authMode")
    private AuthMode authMode;

    @DeploymentContext("autoProvisionIdentityCenter")
    private Boolean autoProvisionIdentityCenter;

    @DeploymentContext("ssoInstanceArn")
    private String ssoInstanceArn;

    @DeploymentContext("identityCenterInitialAdminEmail")
    private String initialAdminEmail;

    @DeploymentContext("identityCenterGroups")
    private List<String> groups;

    @DeploymentContext("stackName")
    private String stackName;

    @DeploymentContext("region")
    private String region;

    public IdentityCenterUserFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only provision if authMode is APPLICATION_OIDC
        if (authMode != AuthMode.APPLICATION_OIDC) {
            LOG.info("Application-level OIDC not enabled - skipping Identity Center user provisioning");
            return;
        }

        // Check if auto-provisioning is enabled
        if (autoProvisionIdentityCenter == null || !autoProvisionIdentityCenter) {
            LOG.info("Identity Center auto-provisioning not enabled - skipping user provisioning");
            return;
        }

        // Validate SSO instance ARN
        if (ssoInstanceArn == null || ssoInstanceArn.isEmpty()) {
            LOG.severe("ssoInstanceArn is required for Identity Center user provisioning");
            throw new IllegalArgumentException(
                "ssoInstanceArn is required when autoProvisionIdentityCenter = true. " +
                "Find it in IAM Identity Center console > Settings > ARN"
            );
        }

        LOG.info("Creating users and groups in Identity Center identity store");
        LOG.info("SSO Instance ARN: " + ssoInstanceArn);

        // Get identity store ID from SSO instance
        String identityStoreId = getIdentityStoreId();

        // Create groups
        Map<String, String> groupIds = createGroups(identityStoreId);

        // Create initial admin user
        if (initialAdminEmail != null && !initialAdminEmail.isEmpty()) {
            String userId = createInitialAdminUser(identityStoreId, initialAdminEmail);

            // Add admin to Administrators group
            if (groupIds.containsKey("Administrators")) {
                addUserToGroup(identityStoreId, userId, groupIds.get("Administrators"));
            }
        }

        LOG.info("Identity Center users and groups created successfully");
    }

    /**
     * Get the identity store ID associated with the SSO instance.
     */
    private String getIdentityStoreId() {
        // Use ListInstances API to get identity store ID
        AwsSdkCall listInstancesCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("listInstances")
                .parameters(Map.of())
                .physicalResourceId(PhysicalResourceId.of("IdentityStoreId"))
                .region(region)
                .build();

        AwsCustomResource listInstances = AwsCustomResource.Builder.create(this, "GetIdentityStoreId")
                .onCreate(listInstancesCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of("sso:ListInstances"))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        // Extract identity store ID from response
        // Response format: {"Instances":[{"InstanceArn":"...","IdentityStoreId":"d-xxxxxxxxxx"}]}
        String identityStoreId = listInstances.getResponseField("Instances.0.IdentityStoreId");

        LOG.info("Identity Store ID: " + identityStoreId);

        return identityStoreId;
    }

    /**
     * Create groups in Identity Center identity store.
     */
    private Map<String, String> createGroups(String identityStoreId) {
        Map<String, String> groupIds = new HashMap<>();

        if (groups == null || groups.isEmpty()) {
            LOG.info("No groups specified - skipping group creation");
            return groupIds;
        }

        LOG.info("Creating " + groups.size() + " groups in Identity Center");

        for (String groupName : groups) {
            String groupId = createGroup(identityStoreId, groupName);
            groupIds.put(groupName, groupId);
            LOG.info("Created group: " + groupName + " (ID: " + groupId + ")");
        }

        return groupIds;
    }

    /**
     * Create a single group in the identity store.
     */
    private String createGroup(String identityStoreId, String groupName) {
        String resourceId = "Group-" + groupName.replaceAll("[^a-zA-Z0-9]", "");

        AwsSdkCall createGroupCall = AwsSdkCall.builder()
                .service("IdentityStore")
                .action("createGroup")
                .parameters(Map.of(
                    "IdentityStoreId", identityStoreId,
                    "DisplayName", groupName,
                    "Description", "Auto-created group for " + stackName
                ))
                .physicalResourceId(PhysicalResourceId.fromResponse("GroupId"))
                .region(region)
                .build();

        AwsSdkCall deleteGroupCall = AwsSdkCall.builder()
                .service("IdentityStore")
                .action("deleteGroup")
                .parameters(Map.of(
                    "IdentityStoreId", identityStoreId,
                    "GroupId", new software.amazon.awscdk.customresources.PhysicalResourceIdReference()
                ))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        AwsCustomResource group = AwsCustomResource.Builder.create(this, resourceId)
                .onCreate(createGroupCall)
                .onDelete(deleteGroupCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of(
                            "identitystore:CreateGroup",
                            "identitystore:DeleteGroup",
                            "identitystore:DescribeGroup"
                        ))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        return group.getResponseField("GroupId");
    }

    /**
     * Create the initial admin user in the identity store.
     */
    private String createInitialAdminUser(String identityStoreId, String email) {
        LOG.info("Creating initial admin user: " + email);

        // Extract name from email
        String username = email.substring(0, email.indexOf("@"));
        String displayName = username.substring(0, 1).toUpperCase() + username.substring(1);

        AwsSdkCall createUserCall = AwsSdkCall.builder()
                .service("IdentityStore")
                .action("createUser")
                .parameters(Map.of(
                    "IdentityStoreId", identityStoreId,
                    "UserName", email,
                    "DisplayName", displayName,
                    "Name", Map.of(
                        "GivenName", displayName,
                        "FamilyName", "Admin"
                    ),
                    "Emails", List.of(
                        Map.of(
                            "Value", email,
                            "Type", "work",
                            "Primary", true
                        )
                    )
                ))
                .physicalResourceId(PhysicalResourceId.fromResponse("UserId"))
                .region(region)
                .build();

        AwsSdkCall deleteUserCall = AwsSdkCall.builder()
                .service("IdentityStore")
                .action("deleteUser")
                .parameters(Map.of(
                    "IdentityStoreId", identityStoreId,
                    "UserId", new software.amazon.awscdk.customresources.PhysicalResourceIdReference()
                ))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        AwsCustomResource user = AwsCustomResource.Builder.create(this, "InitialAdminUser")
                .onCreate(createUserCall)
                .onDelete(deleteUserCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of(
                            "identitystore:CreateUser",
                            "identitystore:DeleteUser",
                            "identitystore:DescribeUser"
                        ))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        String userId = user.getResponseField("UserId");

        LOG.info("Created admin user: " + email + " (ID: " + userId + ")");

        // Create CloudFormation output
        CfnOutput.Builder.create(this, "InitialAdminEmail")
                .description("Initial admin user email for Identity Center")
                .value(email)
                .build();

        CfnOutput.Builder.create(this, "InitialAdminUserId")
                .description("Initial admin user ID in Identity Center")
                .value(userId)
                .build();

        return userId;
    }

    /**
     * Add a user to a group in the identity store.
     */
    private void addUserToGroup(String identityStoreId, String userId, String groupId) {
        LOG.info("Adding user to Administrators group");

        AwsSdkCall createMembershipCall = AwsSdkCall.builder()
                .service("IdentityStore")
                .action("createGroupMembership")
                .parameters(Map.of(
                    "IdentityStoreId", identityStoreId,
                    "GroupId", groupId,
                    "MemberId", Map.of(
                        "UserId", userId
                    )
                ))
                .physicalResourceId(PhysicalResourceId.fromResponse("MembershipId"))
                .region(region)
                .build();

        AwsSdkCall deleteMembershipCall = AwsSdkCall.builder()
                .service("IdentityStore")
                .action("deleteGroupMembership")
                .parameters(Map.of(
                    "IdentityStoreId", identityStoreId,
                    "MembershipId", new software.amazon.awscdk.customresources.PhysicalResourceIdReference()
                ))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        AwsCustomResource membership = AwsCustomResource.Builder.create(this, "AdminGroupMembership")
                .onCreate(createMembershipCall)
                .onDelete(deleteMembershipCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of(
                            "identitystore:CreateGroupMembership",
                            "identitystore:DeleteGroupMembership",
                            "identitystore:DescribeGroupMembership"
                        ))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        LOG.info("User added to Administrators group");
    }
}
