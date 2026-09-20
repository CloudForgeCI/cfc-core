package com.cloudforgeci.api.deploy;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.nio.file.Path;
import java.util.List;

/**
 * Optional post-deploy behavior for {@link CloudForgeDeployment}.
 */
public record DeployOptions(
        boolean replaceSameApplication,
        boolean persistCatalog,
        Path catalogDirectory,
        List<String> managerVolumeRoots,
        AwsCredentialsProvider credentialsOverride) {

    private static final List<String> DEFAULT_MANAGER_VOLUME_ROOTS =
        List.of(".ministack-volumes", ".localstack-volumes");

    public DeployOptions {
        if (catalogDirectory == null) {
            catalogDirectory = Path.of("deployment-contexts");
        }
        if (managerVolumeRoots == null || managerVolumeRoots.isEmpty()) {
            managerVolumeRoots = DEFAULT_MANAGER_VOLUME_ROOTS;
        } else {
            managerVolumeRoots = List.copyOf(managerVolumeRoots);
        }
        // credentialsOverride is nullable; null means the default credential chain. It is passed
        // through to AwsDirectDeployer.
    }

    public static DeployOptions defaults() {
        return new DeployOptions(true, true, null, null, null);
    }

    public DeployOptions withoutCatalog() {
        return new DeployOptions(
            replaceSameApplication,
            false,
            catalogDirectory,
            managerVolumeRoots,
            credentialsOverride);
    }

    /**
     * Credentials to use instead of the default chain for AWS calls — any caller needing to
     * act as a different principal (e.g. an assumed cross-account role) can supply one. Ignored
     * for local-emulator targets (LocalStack/MiniStack always use their fixed test credentials —
     * see {@code AwsDirectDeployer.resolveLocalEmulatorEndpoint()}).
     */
    public DeployOptions withCredentialsOverride(AwsCredentialsProvider credentialsOverride) {
        return new DeployOptions(
            replaceSameApplication,
            persistCatalog,
            catalogDirectory,
            managerVolumeRoots,
            credentialsOverride);
    }
}
