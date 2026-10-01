package com.cloudforgeci.api.core.customresource;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@link AssetFreeCustomResource} must depend on its own CloudFormation invoke permission,
 * or CloudFormation has no ordering guarantee that the permission exists before the resource's
 * first invoke -- a real access-denied failure at deploy time, invisible to any test that only
 * checks resource counts or properties. Verified generically here (not hardcoded per call site)
 * so every current and future {@code AssetFreeCustomResource.create(...)} call gets this check
 * for free.
 */
public class AssetFreeCustomResourceDependencyTest {

    @SuppressWarnings("unchecked")
    @Test
    public void everyAssetFreeCustomResourceDependsOnItsInvokePermission() throws Exception {
        Map<String, Object> context = new HashMap<>();
        context.put("awsConfigEnabled", true);
        context.put("createConfigInfrastructure", true);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "AssetFreeCustomResourceDependencyTestStack", SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure().createCompliance();

        Template template = Template.fromStack(builder.getStack());
        Map<String, Object> json = (Map<String, Object>) template.toJSON();
        Map<String, Object> resources = (Map<String, Object>) json.get("Resources");

        // functionLogicalId -> permission logicalId, for every CloudFormation invoke permission
        Map<String, String> invokePermissionByFunction = new HashMap<>();
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Map<String, Object> resource = (Map<String, Object>) entry.getValue();
            if (!"AWS::Lambda::Permission".equals(resource.get("Type"))) {
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) resource.get("Properties");
            if (!"lambda:InvokeFunction".equals(properties.get("Action"))
                    || !"cloudformation.amazonaws.com".equals(properties.get("Principal"))) {
                continue;
            }
            invokePermissionByFunction.put(functionLogicalId(properties.get("FunctionName")), entry.getKey());
        }
        assertFalse(invokePermissionByFunction.isEmpty(),
            "Expected at least one CloudFormation invoke permission in this synthesized stack");

        int checked = 0;
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Map<String, Object> resource = (Map<String, Object>) entry.getValue();
            if (!"AWS::CloudFormation::CustomResource".equals(resource.get("Type"))) {
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) resource.get("Properties");
            String functionLogicalId = functionLogicalId(properties.get("ServiceToken"));
            String permissionLogicalId = invokePermissionByFunction.get(functionLogicalId);
            if (permissionLogicalId == null) {
                // Not an AssetFreeCustomResource (or its permission wasn't found) -- not this
                // test's concern.
                continue;
            }
            checked++;

            List<String> dependsOn = (List<String>) resource.getOrDefault("DependsOn", List.of());
            assertTrue(dependsOn.contains(permissionLogicalId),
                entry.getKey() + " (ServiceToken -> " + functionLogicalId + ") must depend on its "
                    + "own invoke permission " + permissionLogicalId + " but DependsOn was: " + dependsOn);
        }
        assertTrue(checked >= 4,
            "Expected to check several AssetFreeCustomResource sites in a Config-enabled stack, only found "
                + checked);
    }

    /**
     * {@code fn.addToRolePolicy(...)} attaches each statement to the role's own generated
     * {@code DefaultPolicy} (a separate {@code AWS::IAM::Policy} resource) -- CDK does not make
     * anything invoking the function wait on that policy automatically. Verified the same
     * generic, non-hardcoded way as the invoke-permission dependency above: every
     * {@code AssetFreeCustomResource} whose function has a granted policy must depend on it too,
     * or CloudFormation can invoke the function before the policy is attached and fail with
     * {@code AccessDenied}.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void everyAssetFreeCustomResourceWithGrantedPermissionsDependsOnItsDefaultPolicy() throws Exception {
        Map<String, Object> context = new HashMap<>();
        context.put("awsConfigEnabled", true);
        context.put("createConfigInfrastructure", true);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "AssetFreeCustomResourceDefaultPolicyTestStack", SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure().createCompliance();

        Template template = Template.fromStack(builder.getStack());
        Map<String, Object> json = (Map<String, Object>) template.toJSON();
        Map<String, Object> resources = (Map<String, Object>) json.get("Resources");

        // roleLogicalId -> policyLogicalId, for every generated DefaultPolicy.
        Map<String, String> policyByRole = new HashMap<>();
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Map<String, Object> resource = (Map<String, Object>) entry.getValue();
            if (!"AWS::IAM::Policy".equals(resource.get("Type"))) {
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) resource.get("Properties");
            for (Object roleRef : (List<Object>) properties.get("Roles")) {
                policyByRole.put(roleLogicalId(roleRef), entry.getKey());
            }
        }
        assertFalse(policyByRole.isEmpty(),
            "Expected at least one generated DefaultPolicy in this synthesized stack");

        // functionLogicalId -> roleLogicalId, for every Lambda function with a role.
        Map<String, String> roleByFunction = new HashMap<>();
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Map<String, Object> resource = (Map<String, Object>) entry.getValue();
            if (!"AWS::Lambda::Function".equals(resource.get("Type"))) {
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) resource.get("Properties");
            Object roleProperty = properties.get("Role");
            if (roleProperty != null) {
                roleByFunction.put(entry.getKey(), roleLogicalId(roleProperty));
            }
        }

        int checked = 0;
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Map<String, Object> resource = (Map<String, Object>) entry.getValue();
            if (!"AWS::CloudFormation::CustomResource".equals(resource.get("Type"))) {
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) resource.get("Properties");
            String functionLogicalId = functionLogicalId(properties.get("ServiceToken"));
            String roleLogicalId = roleByFunction.get(functionLogicalId);
            String policyLogicalId = roleLogicalId == null ? null : policyByRole.get(roleLogicalId);
            if (policyLogicalId == null) {
                // Not an AssetFreeCustomResource with granted policy statements -- not this
                // test's concern.
                continue;
            }
            checked++;

            List<String> dependsOn = (List<String>) resource.getOrDefault("DependsOn", List.of());
            assertTrue(dependsOn.contains(policyLogicalId),
                entry.getKey() + " (function " + functionLogicalId + ", role " + roleLogicalId
                    + ") must depend on its own DefaultPolicy " + policyLogicalId
                    + " but DependsOn was: " + dependsOn);
        }
        assertTrue(checked >= 1,
            "Expected to check at least one AssetFreeCustomResource with a granted DefaultPolicy, found none");
    }

    /** Resolves the logical id a {@code Ref}/{@code Fn::GetAtt} intrinsic points at. */
    @SuppressWarnings("unchecked")
    private static String functionLogicalId(Object ref) {
        Map<String, Object> intrinsic = (Map<String, Object>) ref;
        if (intrinsic.containsKey("Ref")) {
            return (String) intrinsic.get("Ref");
        }
        List<Object> getAtt = (List<Object>) intrinsic.get("Fn::GetAtt");
        return (String) getAtt.get(0);
    }

    /** Resolves the Role logical id a {@code Ref}/{@code Fn::GetAtt} intrinsic points at. */
    @SuppressWarnings("unchecked")
    private static String roleLogicalId(Object ref) {
        Map<String, Object> intrinsic = (Map<String, Object>) ref;
        if (intrinsic.containsKey("Ref")) {
            return (String) intrinsic.get("Ref");
        }
        List<Object> getAtt = (List<Object>) intrinsic.get("Fn::GetAtt");
        return (String) getAtt.get(0);
    }
}
