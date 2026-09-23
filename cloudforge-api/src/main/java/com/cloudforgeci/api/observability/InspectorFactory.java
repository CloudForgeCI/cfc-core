package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
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

    @DeploymentContext("inspectorEnabled")
    private Boolean inspectorEnabled;

    @DeploymentContext("inspectorEc2Scanning")
    private Boolean inspectorEc2Scanning;

    @DeploymentContext("inspectorEcrScanning")
    private Boolean inspectorEcrScanning;

    public InspectorFactory(Construct scope, String id) {
        super(scope, id);
    }

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

        AwsSdkCall disableCall = AwsSdkCall.builder()
            .service("Inspector2")
            .action("disable")
            .parameters(Map.of(
                "accountIds", List.of(account),
                "resourceTypes", resourceTypes
            ))
            .physicalResourceId(PhysicalResourceId.of("inspector2-enable-" + account + "-" + region))
            .region(region)
            .build();

        // inspector2:Enable provisions a service-linked role on first use.
        AwsCustomResource.Builder.create(this, "Inspector2Enable")
            .onCreate(enableCall)
            .onUpdate(enableCall)
            .onDelete(disableCall)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        LOG.info("Inspector enabled: " + resourceTypes);
    }
}
