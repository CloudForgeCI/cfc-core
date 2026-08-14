package com.cloudforge.core.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Deploys adapted CloudFormation templates to a local emulator endpoint.
 *
 * <p>Implementations use the AWS SDK against {@code AWS_ENDPOINT_URL} rather than real AWS.</p>
 */
public interface LocalDeployer extends AutoCloseable {

    LocalDeployResult deploy(String stackName, Path template) throws IOException;

    void delete(String stackName) throws IOException;

    boolean stackExists(String stackName);

    Map<String, String> outputs(String stackName);

    @Override
    void close();
}
