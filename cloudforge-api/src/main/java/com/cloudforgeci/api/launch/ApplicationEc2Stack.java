package com.cloudforgeci.api.launch;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.constructs.Construct;

/**
 * Universal Application EC2 Stack — see {@link ApplicationFargateStack}'s javadoc for why this
 * lives here rather than only in {@code cfc-testing}'s identically-shaped launcher.
 */
public class ApplicationEc2Stack extends Stack {

    public ApplicationEc2Stack(final Construct scope, final String id, final StackProps props,
                              final SecurityProfile security, final IAMProfile iamProfile,
                              final ApplicationSpec applicationSpec) {
        super(scope, id, props);

        if (applicationSpec == null) {
            throw new IllegalArgumentException("ApplicationSpec cannot be null. "
                + "Use ApplicationLoader.findById(\"appName\") to get a valid ApplicationSpec.");
        }

        DeploymentContext cfc = DeploymentContext.from(scope);

        Tags.of(this).add("cloudforge:managed", "true");
        Tags.of(this).add("cloudforge:application", applicationSpec.applicationId());
        Tags.of(this).add("cloudforge:runtime", "ec2");

        ApplicationFactory.createEc2(this, id, cfc, security, iamProfile, applicationSpec);

        CfnOutput.Builder.create(this, "CloudForgeApplicationId")
            .value(applicationSpec.applicationId())
            .description("CloudForge application plugin id")
            .build();
        String display = applicationSpec.displayName();
        if (display == null || display.isBlank()) {
            display = applicationSpec.applicationId();
        }
        CfnOutput.Builder.create(this, "CloudForgeApplicationName")
            .value(display)
            .description("CloudForge application display name")
            .build();
    }
}
