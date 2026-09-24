package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for Amazon Inspector v2 account enablement.
 *
 * <p>Unlike GuardDuty/Security Hub/Macie, Inspector2 has no CloudFormation-native enablement
 * resource -- {@code inspector2:Enable} is API-only, so this uses an {@link AwsCustomResource}
 * the same way {@code ComplianceFactory}'s Config Recorder starter does.
 *
 * <p>Inspector2's continuous rescanning is the default behavior for an enabled resource type,
 * not a separate opt-in -- there's no API parameter for it, so {@code inspectorContinuousScanning}
 * doesn't map to anything here and is left unread.
 */
public class InspectorFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(InspectorFactory.class.getName());

    private static final List<String> ALL_RESOURCE_TYPES = List.of("EC2", "ECR");

    @DeploymentContext("inspectorEnabled")
    private Boolean inspectorEnabled;

    @DeploymentContext("inspectorEc2Scanning")
    private Boolean inspectorEc2Scanning;

    @DeploymentContext("inspectorEcrScanning")
    private Boolean inspectorEcrScanning;

    /** @param scope parent construct
     *  @param id construct ID */
    public InspectorFactory(Construct scope, String id) {
        super(scope, id);
    }

    /** Resolves whether Inspector is required, then enables it via the account-scoped
     *  {@code inspector2:Enable} SDK call for the currently-selected resource types. */
    @Override
    public void create() {
        if (inspectorEnabled == null) {
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
            if (securityProfileConfig != null) {
                inspectorEnabled = securityProfileConfig.isInspectorEnabled();
                LOG.info("Inspector inherited from security profile: " + inspectorEnabled);
            }
        }

        if (!Boolean.TRUE.equals(inspectorEnabled)) {
            LOG.info("Inspector disabled");
            return;
        }

        List<String> resourceTypes = new ArrayList<>();
        if (!Boolean.FALSE.equals(inspectorEc2Scanning)) {
            resourceTypes.add("EC2");
        }
        if (!Boolean.FALSE.equals(inspectorEcrScanning)) {
            resourceTypes.add("ECR");
        }
        if (resourceTypes.isEmpty()) {
            LOG.info("Inspector enabled but both EC2 and ECR scanning explicitly disabled -- nothing to enable");
            return;
        }

        String account = Stack.of(this).getAccount();
        String region = Stack.of(this).getRegion();

        // Inspector2:Enable only turns on the resource types listed -- it never turns off a type
        // that was previously enabled and is now absent from the list. Disable exactly the
        // deselected types (the complement of resourceTypes) before re-enabling the selected
        // ones, so flipping e.g. inspectorEcrScanning from true to false disables ECR scanning
        // instead of leaving it running indefinitely. Deriving the disable list from the current
        // selection (rather than always disabling everything) also means this resource's declared
        // parameters change whenever the selection changes, so CloudFormation actually invokes its
        // onUpdate call instead of treating it as unchanged.
        List<String> deselectedTypes = ALL_RESOURCE_TYPES.stream()
            .filter(type -> !resourceTypes.contains(type))
            .toList();

        AwsCustomResource resetResource = null;
        if (!deselectedTypes.isEmpty()) {
            AwsSdkCall resetCall = AwsSdkCall.builder()
                .service("Inspector2")
                .action("disable")
                .parameters(Map.of(
                    "accountIds", List.of(account),
                    "resourceTypes", deselectedTypes
                ))
                .physicalResourceId(PhysicalResourceId.of(
                    "inspector2-reset-" + account + "-" + region + "-" + String.join("-", deselectedTypes)))
                .region(region)
                .build();

            resetResource = AwsCustomResource.Builder.create(this, "Inspector2Reset")
                .onCreate(resetCall)
                .onUpdate(resetCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                    SdkCallsPolicyOptions.builder()
                        .resources(List.of("*"))
                        .build()
                ))
                .build();
        }

        AwsSdkCall enableCall = AwsSdkCall.builder()
            .service("Inspector2")
            .action("enable")
            .parameters(Map.of(
                "accountIds", List.of(account),
                "resourceTypes", resourceTypes
            ))
            .physicalResourceId(PhysicalResourceId.of("inspector2-enable-" + account + "-" + region))
            .region(region)
            .build();

        // No onDelete: Inspector2:Disable applies to the whole account and region, not to this
        // stack alone. Deleting this stack must not disable vulnerability scanning for every
        // other stack or operator relying on it in the same account/region.
        AwsCustomResource enableResource = AwsCustomResource.Builder.create(this, "Inspector2Enable")
            .onCreate(enableCall)
            .onUpdate(enableCall)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        // inspector2:Enable provisions the AWSServiceRoleForAmazonInspector2 service-linked role
        // on first use in the account; fromSdkCalls only infers the inspector2:Enable/Disable
        // actions, not this IAM side effect, so it needs an explicit grant.
        enableResource.getGrantPrincipal().addToPrincipalPolicy(PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(List.of("iam:CreateServiceLinkedRole"))
            .resources(List.of(
                "arn:aws:iam::" + account + ":role/aws-service-role/inspector2.amazonaws.com/AWSServiceRoleForAmazonInspector2"))
            .conditions(Map.of("StringEquals", Map.of("iam:AWSServiceName", "inspector2.amazonaws.com")))
            .build());

        if (resetResource != null) {
            enableResource.getNode().addDependency(resetResource);
        }

        LOG.info("Inspector enabled: " + resourceTypes);
    }
}
