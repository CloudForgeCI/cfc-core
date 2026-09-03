package com.cloudforge.core.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerAwsCapabilityCatalogTest {

    @Test
    void operatorBaselineIncludesRdsAndEcsActions() {
        var actions = ManagerAwsCapabilityCatalog.operatorBaselineIamActions();
        assertTrue(actions.contains("rds:CreateDBSnapshot"));
        assertTrue(actions.contains("rds:DeleteDBSnapshot"));
        // AWS calls this transparently on the caller's behalf right after CreateDBSnapshot
        // succeeds, for any RDS instance created with CopyTagsToSnapshot enabled -- confirmed
        // live: snapshot creation itself succeeded, but the automatic tag-copy step was denied
        // without this grant.
        assertTrue(actions.contains("rds:AddTagsToResource"));
        assertTrue(actions.contains("rds:RestoreDBInstanceFromDBSnapshot"));
        assertTrue(actions.contains("ecs:UpdateService"));
        assertTrue(actions.contains("cloudformation:DeleteStack"));
        assertTrue(actions.contains("cloudformation:GetTemplate"));
        // "Cognito as the whole Users directory" -- without ListUserPools, even discovering the
        // pool ID fails before the feature can do anything.
        assertTrue(actions.contains("cognito-idp:ListUserPools"));
        assertTrue(actions.contains("cognito-idp:AdminCreateUser"));
    }

    @Test
    void recognizesCatalogIamActions() {
        assertTrue(ManagerAwsCapabilityCatalog.isKnownIamAction("rds:DescribeDBInstances"));
        assertTrue(ManagerAwsCapabilityCatalog.isKnownIamAction("cloudformation:GetTemplate"));
        assertFalse(ManagerAwsCapabilityCatalog.isKnownIamAction("s3:DeleteBucket"));
    }

    @Test
    void deployCapabilitiesAreNeverPartOfTheOperatorBaseline() {
        var deployCapabilities = ManagerAwsCapabilityCatalog.deployCapabilities();
        var baseline = ManagerAwsCapabilityCatalog.operatorBaseline();
        for (var capability : deployCapabilities) {
            assertFalse(baseline.contains(capability),
                capability + " must never be silently included in the default operator policy");
        }
        var baselineActions = ManagerAwsCapabilityCatalog.operatorBaselineIamActions();
        assertFalse(baselineActions.contains("cloudformation:CreateStack"));
        assertFalse(baselineActions.contains("iam:PassRole"));
        assertFalse(baselineActions.contains("servicecatalog:ProvisionProduct"));
    }

    @Test
    void cfnDeployAndScProvisionHaveTheExpectedActions() {
        var actions = ManagerAwsCapabilityCatalog.iamActions(
            java.util.Set.of(ManagerAwsCapabilityCatalog.Capability.CFN_DEPLOY));
        assertTrue(actions.contains("cloudformation:CreateStack"));
        assertTrue(actions.contains("cloudformation:UpdateStack"));
        assertTrue(actions.contains("iam:PassRole"));
        assertFalse(actions.contains("cloudformation:DeleteStack"), "delete stays in CFN_DELETE only");

        var scActions = ManagerAwsCapabilityCatalog.iamActions(
            java.util.Set.of(ManagerAwsCapabilityCatalog.Capability.SC_PROVISION));
        assertTrue(scActions.contains("servicecatalog:ProvisionProduct"));
        assertTrue(scActions.contains("servicecatalog:TerminateProvisionedProduct"));
        assertFalse(scActions.stream().anyMatch(a -> a.startsWith("cloudformation:")),
            "Service Catalog path must carry no direct CFN permissions on Manager's own role");
    }
}
