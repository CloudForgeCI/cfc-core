package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.local.DeploymentTarget;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@code CloudForgeDeployment}'s {@code AWS} case routes {@code DRY_RUN} to {@link
 * com.cloudforgeci.api.deploy.aws.AwsDirectDeployer#previewChangeSet} instead of leaving it a
 * no-op or misrouting it. {@code previewChangeSet} creates a real CloudFormation change set, so
 * this environment can't exercise a successful preview — same limitation {@code
 * AwsDirectDeployerTest} documents for {@code deploy()}. Reaching an {@link SdkException} (its
 * client- and service-side subtypes are siblings, not parent/child — a fully credential-less
 * environment fails locally with {@code SdkClientException}, one with a stale or invalid cached
 * credential reaches AWS and fails server-side with {@code StsException}, so this asserts the
 * shared base type) instead of any other error (an unsupported-target exception, a routing bug)
 * is exactly what proves the routing is correct.
 */
class CloudForgeDeploymentAwsTest {

    @Test
    void dryRunRoutesToAwsCasePreviewInsteadOfBeingANoOp() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "AwsDryRunTest";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.region = "eu-west-1";

        DeploymentRequest request = DeploymentRequest.dryRun(
            config, DeploymentTarget.AWS, Path.of("cdk.out/AwsDryRunTest.template.json"), Path.of("cdk.out"));

        assertThrows(SdkException.class, () -> CloudForgeDeployment.deploy(request));
    }
}
