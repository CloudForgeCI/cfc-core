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
 * Universal Application Fargate Stack — CDK-consumer copy of {@code cfc-testing}'s launcher of
 * the same name/shape, relocated here so any in-process synthesizer (Manager's, in particular,
 * which cannot depend on {@code cfc-testing} — that's the public sample/reference repo, not a
 * library) can build one without duplicating the {@code ApplicationFactory} wiring by hand.
 * cfc-testing's own copy is untouched; this is not a shared dependency between them, just the
 * same well-tested ~40-line pattern kept in the one module both a CLI consumer and Manager can
 * actually depend on.
 *
 * <p>See {@link com.cloudforgeci.api.deploy.CloudForgeSynthesizer} for the orchestration that
 * builds the CDK {@code App}/context this stack expects and drives {@code app.synth()}.</p>
 */
public class ApplicationFargateStack extends Stack {

    public ApplicationFargateStack(final Construct scope, final String id, final StackProps props,
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
        Tags.of(this).add("cloudforge:runtime", "fargate");

        ApplicationFactory.createFargate(this, id, cfc, security, iamProfile, applicationSpec);

        // Explicit outputs so Manager can enrich the App column even when stack tags
        // are omitted by local emulators (MiniStack describeStacks tags are often empty).
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
