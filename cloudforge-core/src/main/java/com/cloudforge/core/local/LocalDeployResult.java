package com.cloudforge.core.local;

import java.util.List;
import java.util.Map;

/** Outcome of deploying an adapted template to a local emulator endpoint. */
public record LocalDeployResult(
        String stackName,
        boolean created,
        boolean noOp,
        List<LocalResourceChange> changes,
        Map<String, String> outputs) {
}
