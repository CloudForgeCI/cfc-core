package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.assertions.Template;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDK-synthesis coverage for {@link ManagerOperatorIamSupport}'s deploy-capability methods
 * ({@code deployStatements}/{@code catalogProvisionStatement}/{@code attachDeployCapabilities}).
 *
 * <p>{@code deployStatements}/{@code catalogProvisionStatement} are pure catalog queries gated
 * only by {@link ManagerOperatorIamSupport#isCloudForgeManager} — what the capabilities would
 * look like, independent of whether this deployment actually grants them. {@code
 * attachDeployCapabilities} is the one CDK side-effect method, gated additionally behind {@code
 * DeploymentConfig.managerDirectDeployEnabled} (default false) — these tests invoke it directly
 * against a manually-created role rather than relying on {@code createFargate()}, since IAM
 * configuration classes call it unconditionally and let the flag do the gating.</p>
 */
class ManagerDeployIamSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deployStatementsReturnsTwelveStatementsForManager() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployStatements", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        assertEquals(12, statements.size());
    }

    /** DescribeChangeSet/ExecuteChangeSet/DeleteChangeSet can't share a tag condition with
     *  CreateChangeSet at all -- aws:RequestTag never has a value (those three don't accept a
     *  Tags parameter), and aws:ResourceTag fails for a CREATE-type change set specifically,
     *  since CloudFormation only applies a change set's Tags to the stack when it executes, not
     *  when it's merely created: a brand-new stack sits in REVIEW_IN_PROGRESS with zero tags for
     *  as long as its first change set is pending. No condition at all, matching this class's
     *  SC_PROVISION precedent -- CreateChangeSet's own tag requirement already gates who can
     *  start a managed change set in the first place. */
    @Test
    void deployStatementsGrantsChangeSetLifecycleWithNoTagCondition() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployChangeSetLifecycle", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        PolicyStatement changeSetStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployChangeSetLifecycle".equals(s.getSid()))
            .findFirst()
            .orElseThrow();

        assertTrue(changeSetStatement.getActions().contains("cloudformation:DescribeChangeSet"));
        assertTrue(changeSetStatement.getActions().contains("cloudformation:ExecuteChangeSet"));
        assertTrue(changeSetStatement.getActions().contains("cloudformation:DeleteChangeSet"));
        assertFalse(changeSetStatement.toJSON().toString().contains("cloudforge:managed"));

        PolicyStatement createStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployCreate".equals(s.getSid()))
            .findFirst()
            .orElseThrow();
        assertFalse(createStatement.getActions().contains("cloudformation:DescribeChangeSet"));
    }

    /** deploy:create's own template upload needs an S3 grant beyond the three tag-conditioned
     *  statements above, which only ever cover CloudFormation/IAM actions: AwsDirectDeployer puts
     *  the CloudFormation template into a bucket before CreateStack/CreateChangeSet ever runs. */
    @Test
    void deployStatementsGrantsS3OnTheTemplateBucketPrefix() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployTemplateBucket", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        PolicyStatement bucketStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployTemplateBucket".equals(s.getSid()))
            .findFirst()
            .orElseThrow();

        assertTrue(bucketStatement.getActions().contains("s3:*"));
        assertTrue(bucketStatement.getResources().stream()
            .anyMatch(r -> r.contains(com.cloudforgeci.api.deploy.aws.AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX)));
    }

    /** The template bucket grant above isn't the only S3 access a real deploy needs: the standard
     *  CDK bootstrap asset bucket also needs its own grant, since AwsDirectDeployer.deploy()
     *  always publishes to it via LocalStackCdkAssetPublisher (for real AWS as much as a local
     *  emulator, despite the class name). */
    @Test
    void deployStatementsGrantsS3OnTheCdkAssetBucketPrefix() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployCdkAssetBucket", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        PolicyStatement bucketStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployCdkAssetBucket".equals(s.getSid()))
            .findFirst()
            .orElseThrow();

        assertTrue(bucketStatement.getActions().contains("s3:*"));
        assertTrue(bucketStatement.getResources().stream()
            .anyMatch(r -> r.contains("cdk-hnb659fds-assets-")));
    }

    /** Even with both S3 grants in place, CloudFormation itself resolves the synthesized
     *  template's BootstrapVersion dynamic reference using the deploying principal's own
     *  credentials -- Manager's task role needs its own read access to that SSM parameter,
     *  regardless of whether the account has ever run a real cdk bootstrap. */
    @Test
    void deployStatementsGrantsSsmReadOnTheCdkBootstrapVersionParameter() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployCdkBootstrapParameter", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        PolicyStatement ssmStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployCdkBootstrapParameter".equals(s.getSid()))
            .findFirst()
            .orElseThrow();

        assertTrue(ssmStatement.getActions().contains("ssm:GetParameters"));
        assertTrue(ssmStatement.getResources().stream()
            .anyMatch(r -> r.contains("cdk-bootstrap/hnb659fds")));
    }

    /** iam:PassRole alone isn't enough: CloudFormation creates and manages the deployed
     *  application's own IAM roles (task role, task execution role) using the deploying
     *  principal's own credentials, the same as every other resource type in the template. An
     *  automatic rollback triggered by an unrelated resource failure needs
     *  iam:DeleteRole/iam:DetachRolePolicy on those same roles to tear them back down. */
    @Test
    void deployStatementsGrantsIamRoleLifecycleSplitByCreateVsManage() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployIamRoleLifecycle", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());

        PolicyStatement createStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployIamRoleCreate".equals(s.getSid()))
            .findFirst()
            .orElseThrow();
        assertTrue(createStatement.getActions().contains("iam:CreateRole"));
        assertTrue(createStatement.getActions().contains("iam:TagRole"));
        // Resource-name-scoped, not iam:RequestTag-conditioned -- CloudFormation's IAM::Role
        // provider doesn't reliably surface template tags to iam:RequestTag evaluation, so a
        // genuinely first-ever CreateRole call can be denied by that condition even when the
        // synthesized template carries the correct inline tag.
        assertTrue(createStatement.getResources().contains("arn:aws:iam::*:role/*SystemContextExtendedTask*"));
        assertTrue(createStatement.getResources().contains("arn:aws:iam::*:role/*LogRetention*"));
        assertFalse(createStatement.toJSON().toString().contains("iam:RequestTag"));

        PolicyStatement manageStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployIamRoleManage".equals(s.getSid()))
            .findFirst()
            .orElseThrow();
        assertTrue(manageStatement.getActions().contains("iam:DeleteRole"));
        assertTrue(manageStatement.getActions().contains("iam:AttachRolePolicy"));
        assertTrue(manageStatement.getActions().contains("iam:DetachRolePolicy"));
        assertTrue(manageStatement.getActions().contains("iam:PutRolePolicy"));
        // Resource-name-scoped, not iam:ResourceTag-conditioned -- CloudFormation's automatic
        // rollback can delete a just-created role faster than its own tags reliably propagate
        // into tag-based condition evaluation, a real IAM tag-propagation race.
        assertTrue(manageStatement.getResources().contains("arn:aws:iam::*:role/*SystemContextExtendedTask*"));
        // CDK's own builtin custom-resource Lambda service role -- see ManagerOperatorIamSupport's
        // own comment for why this pattern has no "ServiceRole" requirement (truncated away
        // before that point in a real observed physical name).
        assertTrue(manageStatement.getResources().contains("arn:aws:iam::*:role/*LogRetention*"));
        assertFalse(manageStatement.toJSON().toString().contains("iam:ResourceTag"));

        // The LogRetention custom resource is a Lambda *function*, not just the IAM role above --
        // without a lambda:* grant, lambda:CreateFunction is denied outright, and the same gap
        // then blocks the stack's own rollback on lambda:DeleteFunction, leaving it stuck in
        // ROLLBACK_FAILED.
        PolicyStatement functionStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployLogRetentionFunctionManage".equals(s.getSid()))
            .findFirst()
            .orElseThrow();
        assertTrue(functionStatement.getActions().contains("lambda:CreateFunction"));
        assertTrue(functionStatement.getActions().contains("lambda:DeleteFunction"));
        assertTrue(functionStatement.getResources().contains("arn:aws:lambda:*:*:function:*LogRetention*"));
    }

    /** The layer {@link PermissionMatrix} does not cover: Manager's own role actually creating a
     *  target app's VPC/EFS/ALB/ECS-cluster resources, not the deployed app's own workload
     *  permissions once running. */
    @Test
    void deployStatementsGrantsTargetInfrastructureProvisioning() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerTargetInfrastructure", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        // Split across two statements/managed policies -- see ManagerOperatorIamSupport's own
        // comment on why: a role's combined inline-policy size is capped at 10,240 bytes total,
        // which the flat 212-action single statement this used to be blew past the moment the
        // database/KMS/secrets permissions were added.
        PolicyStatement networkStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployTargetInfrastructureNetwork".equals(s.getSid()))
            .findFirst()
            .orElseThrow();
        PolicyStatement computeDataStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployTargetInfrastructureComputeData".equals(s.getSid()))
            .findFirst()
            .orElseThrow();

        assertTrue(networkStatement.getActions().contains("ec2:CreateVpc"));
        assertTrue(networkStatement.getActions().contains("ec2:CreateInternetGateway"));
        assertTrue(networkStatement.getActions().contains("elasticfilesystem:CreateFileSystem"));
        assertTrue(networkStatement.getActions().contains("elasticfilesystem:TagResource"));
        assertTrue(networkStatement.getActions().contains("elasticloadbalancing:CreateLoadBalancer"));

        assertTrue(computeDataStatement.getActions().contains("ecs:CreateCluster"));
        assertTrue(computeDataStatement.getActions().contains("logs:CreateLogGroup"));
        // Compliance dimension -- account-level singleton services (Config, GuardDuty) need
        // their own service-linked role the first time either is ever enabled in the account.
        assertTrue(computeDataStatement.getActions().contains("config:PutConfigurationRecorder"));
        assertTrue(computeDataStatement.getActions().contains("guardduty:CreateDetector"));
        assertTrue(computeDataStatement.getActions().contains("wafv2:CreateWebACL"));
        assertTrue(computeDataStatement.getActions().contains("iam:CreateServiceLinkedRole"));
        // Database dimension -- KMS key creation for RDS encryption, and the auto-generated
        // Secrets Manager credential RdsFactory always pairs with it, both need their own grants.
        assertTrue(computeDataStatement.getActions().contains("rds:CreateDBInstance"));
        assertTrue(computeDataStatement.getActions().contains("rds:DescribeDBParameterGroups"));
        assertTrue(computeDataStatement.getActions().contains("rds:DescribeEngineDefaultParameters"));
        assertTrue(computeDataStatement.getActions().contains("kms:CreateKey"));
        assertTrue(computeDataStatement.getActions().contains("secretsmanager:GetRandomPassword"));
        // Deliberately unconditioned -- see OperatorProvisioningPermissionMatrix's own javadoc:
        // these resources have no stable name pattern to scope by the way IAM roles do, and
        // individually splitting ~150 actions by RequestTag/ResourceTag would reproduce the same
        // tag-propagation race iamRoleManage was fixed for above.
        assertFalse(networkStatement.toJSON().toString().contains("cloudforge:managed"));
        assertFalse(computeDataStatement.toJSON().toString().contains("cloudforge:managed"));
    }

    @Test
    void deployStatementsIsEmptyForNonManagerApplication() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsDeployStatements", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        assertTrue(ManagerOperatorIamSupport.deployStatements(builder.getSystemContext()).isEmpty());
        assertTrue(ManagerOperatorIamSupport.catalogProvisionStatement(builder.getSystemContext()).isEmpty());
    }

    /** Real bug this locks in: a connected account's trust policy can match byte-for-byte what
     *  Manager itself generated and {@code sts:AssumeRole} still comes back {@code AccessDenied}
     *  if Manager's own task role was never granted permission to make the call at all — this
     *  statement (unlike {@link #deployStatementsReturnsTwelveStatementsForManager}'s twelve) had
     *  never existed anywhere in either repo despite {@code CrossAccountRoleTemplateFactory}'s
     *  own javadoc claiming it did. */
    @Test
    void crossAccountAssumeRoleStatementIsScopedToTheConnectionRolePrefixForManager() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerCrossAccountAssumeRole", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Optional<PolicyStatement> statement =
            ManagerOperatorIamSupport.crossAccountAssumeRoleStatement(builder.getSystemContext());
        assertTrue(statement.isPresent());
        String json = statement.get().toJSON().toString();
        assertTrue(json.contains("sts:AssumeRole"));
        assertTrue(json.contains("arn:aws:iam::*:role/CloudForgeManagerAccess-*"));
        assertFalse(json.contains("\"Resource\":\"*\""), "must not be a blanket assume-anything grant");
    }

    @Test
    void crossAccountAssumeRoleStatementIsEmptyForNonManagerApplication() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsCrossAccountAssumeRole", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        assertTrue(ManagerOperatorIamSupport.crossAccountAssumeRoleStatement(builder.getSystemContext()).isEmpty());
    }

    /** {@code attachOperatorBaselinePolicies}/{@code addOperatorBaselineToStatements} are the two
     *  call sites every IAM profile (Minimal/Standard/Extended, both the PRODUCTION single-policy
     *  and DEV/STAGING incremental-role branches) already wires unconditionally — confirming the
     *  grant lands via those, rather than needing every IAM profile class covered separately. */
    @Test
    void attachOperatorBaselinePoliciesIncludesTheCrossAccountAssumeRoleGrant() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerAttachBaselineAssumeRole", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestBaselineRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachOperatorBaselinePolicies(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());
        assertTrue(templateJson.contains("CloudForgeManagerCrossAccountAssumeRole"));
        assertTrue(iamActions(template).contains("sts:AssumeRole"));
    }

    @Test
    void addOperatorBaselineToStatementsIncludesTheCrossAccountAssumeRoleGrant() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerAddBaselineAssumeRole", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = new java.util.ArrayList<>();
        ManagerOperatorIamSupport.addOperatorBaselineToStatements(builder.getSystemContext(), statements);

        assertTrue(statements.stream().anyMatch(s ->
            "CloudForgeManagerCrossAccountAssumeRole".equals(s.getSid())));
    }

    @Test
    void catalogProvisionStatementIsPresentAndConditionFreeForManager() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerCatalogProvision", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Optional<PolicyStatement> statement =
            ManagerOperatorIamSupport.catalogProvisionStatement(builder.getSystemContext());
        assertTrue(statement.isPresent());
        assertTrue(statement.get().toJSON().toString().contains("servicecatalog:ProvisionProduct"));
        assertFalse(statement.get().toJSON().toString().contains("cloudformation:"));
    }

    @Test
    void attachDeployCapabilitiesAddsAllFourSidsWithExpectedActionsAndConditionsWhenFlagEnabled()
            throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerAttachDeploy", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .withManagerDirectDeployEnabled(true)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestDeployRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachDeployCapabilities(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());

        assertTrue(templateJson.contains("CloudForgeManagerDeployCreate"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployUpdate"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployPassRole"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployCatalog"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployTemplateBucket"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployCdkAssetBucket"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployCdkBootstrapParameter"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployChangeSetLifecycle"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployIamRoleCreate"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployIamRoleManage"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployTargetInfrastructure"));

        Set<String> actions = iamActions(template);
        assertTrue(actions.contains("cloudformation:CreateStack"));
        assertTrue(actions.contains("cloudformation:UpdateStack"));
        assertTrue(actions.contains("iam:PassRole"));
        assertTrue(actions.contains("servicecatalog:ProvisionProduct"));

        assertTrue(templateJson.contains("aws:RequestTag/cloudforge:managed"));
        assertTrue(templateJson.contains("aws:ResourceTag/cloudforge:managed"));
        assertTrue(templateJson.contains("iam:ResourceTag/cloudforge:managed"));
    }

    @Test
    void attachDeployCapabilitiesIsNoOpForManagerWhenFlagIsUnsetOrDefault() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerAttachDeployFlagOff", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestDeployRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachDeployCapabilities(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());
        assertFalse(templateJson.contains("CloudForgeManagerDeployCreate"));
        assertFalse(templateJson.contains("CloudForgeManagerDeployCatalog"));
    }

    @Test
    void attachDeployCapabilitiesIsNoOpForNonManagerApplicationEvenWithFlagEnabled() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsAttachDeploy", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .withManagerDirectDeployEnabled(true)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestDeployRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachDeployCapabilities(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());
        assertFalse(templateJson.contains("CloudForgeManagerDeployCreate"));
        assertFalse(templateJson.contains("CloudForgeManagerDeployCatalog"));
    }

    private static Set<String> iamActions(Template template) throws Exception {
        JsonNode root = MAPPER.valueToTree(template.toJSON());
        JsonNode resources = root.path("Resources");
        Set<String> actions = new HashSet<>();
        Iterator<String> names = resources.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode resource = resources.get(name);
            if (!"AWS::IAM::Policy".equals(resource.path("Type").asText())
                && !"AWS::IAM::ManagedPolicy".equals(resource.path("Type").asText())) {
                continue;
            }
            collectActions(resource.path("Properties").path("PolicyDocument").path("Statement"), actions);
        }
        Iterator<String> roleNames = resources.fieldNames();
        while (roleNames.hasNext()) {
            JsonNode resource = resources.get(roleNames.next());
            if (!"AWS::IAM::Role".equals(resource.path("Type").asText())) {
                continue;
            }
            JsonNode policies = resource.path("Properties").path("Policies");
            if (policies.isArray()) {
                for (JsonNode policy : policies) {
                    collectActions(policy.path("PolicyDocument").path("Statement"), actions);
                }
            }
        }
        return actions;
    }

    private static void collectActions(JsonNode statements, Set<String> actions) {
        if (statements.isArray()) {
            for (JsonNode statement : statements) {
                addActionNode(statement.path("Action"), actions);
            }
        } else if (statements.isObject()) {
            addActionNode(statements.path("Action"), actions);
        }
    }

    private static void addActionNode(JsonNode actionNode, Set<String> actions) {
        if (actionNode.isArray()) {
            actionNode.forEach(node -> actions.add(node.asText()));
        } else if (actionNode.isTextual()) {
            actions.add(actionNode.asText());
        }
    }
}
