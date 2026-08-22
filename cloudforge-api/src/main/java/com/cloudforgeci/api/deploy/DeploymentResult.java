package com.cloudforgeci.api.deploy;

import com.cloudforge.core.local.LocalDeployResult;
import com.cloudforge.core.local.LocalSameApplicationStackReplacer;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.TemplateAdaptationResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Outcome from {@link CloudForgeDeployment}. */
public record DeploymentResult(
        DeploymentTarget target,
        DeployMode mode,
        String localStackName,
        String endpoint,
        Path adaptationReport,
        TemplateAdaptationResult adaptation,
        LocalDeployResult deployment,
        LocalSameApplicationStackReplacer.Result replaceResult,
        Map<String, String> outputs,
        List<Path> catalogPaths,
        List<String> messages,
        boolean historyRecorded) {

    public DeploymentResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        catalogPaths = catalogPaths == null ? List.of() : List.copyOf(catalogPaths);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public boolean success() {
        return mode != DeployMode.VERIFY || !outputs.isEmpty();
    }

    public Optional<String> warning() {
        if (replaceResult != null && replaceResult.warning().isPresent()) {
            return replaceResult.warning();
        }
        return Optional.empty();
    }
}
