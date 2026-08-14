package com.cloudforgeci.api.deploy.aws;

import java.util.List;
import java.util.Map;

/** Outcome of deploying (or no-op'ing) a stack via {@link AwsDirectDeployer}. */
public record AwsStackDeployResult(
        String stackName,
        boolean created,
        boolean noOp,
        List<String> changeSummaries,
        Map<String, String> outputs) {

    public AwsStackDeployResult {
        changeSummaries = changeSummaries == null ? List.of() : List.copyOf(changeSummaries);
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
