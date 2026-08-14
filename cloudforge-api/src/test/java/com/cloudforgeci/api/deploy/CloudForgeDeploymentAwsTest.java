package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.local.DeploymentTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code CloudForgeDeployment}'s {@code AWS} case actually routes (no longer the
 * {@code IllegalArgumentException}/{@code "not supported here"} it used to throw) without
 * requiring live AWS credentials — DRY_RUN never calls {@link
 * com.cloudforgeci.api.deploy.aws.AwsDirectDeployer#deploy}, only DEPLOY/VERIFY do, so this is
 * the one AWS mode fully exercisable in this environment. See {@code AwsDirectDeployerTest} for
 * coverage of the deployer itself.
 */
class CloudForgeDeploymentAwsTest {

    @Test
    void dryRunRoutesToAwsCaseInsteadOfThrowing() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "AwsDryRunTest";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.region = "eu-west-1";

        DeploymentRequest request = DeploymentRequest.dryRun(
            config, DeploymentTarget.AWS, Path.of("cdk.out/AwsDryRunTest.template.json"), Path.of("cdk.out"));

        DeploymentResult result = CloudForgeDeployment.deploy(request);

        assertEquals(DeploymentTarget.AWS, result.target());
        assertEquals(DeployMode.DRY_RUN, result.mode());
        assertEquals("AwsDryRunTest", result.localStackName());
        assertEquals("eu-west-1", result.endpoint());
        assertTrue(result.outputs().isEmpty());
        // AWS has no local adaptation pipeline or same-application stack replacement —
        // unlike MiniStack/LocalStack, these stay null rather than populated.
        assertNull(result.adaptation());
        assertNull(result.deployment());
        assertNull(result.replaceResult());
        assertNull(result.adaptationReport());
    }
}
