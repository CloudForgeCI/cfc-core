package com.cloudforgeci.samples.app;

import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.LocalResourceChange;
import com.cloudforgeci.api.deploy.DeploymentResult;

import java.nio.file.Path;

/**
 * Console formatting for {@link DeploymentResult} — sample entrypoint only (not cloudforge-api).
 */
public final class DeploymentResultPrinter {

    private DeploymentResultPrinter() {
    }

    public static String targetLabel(DeploymentTarget target) {
        return target == DeploymentTarget.MINISTACK ? "MiniStack" : "LocalStack";
    }

    public static void printDeployStarted(String targetLabel) {
        System.out.println("\n🏗️  Deploying canonical template to " + targetLabel + "...");
    }

    public static void printDeployMetadata(DeploymentResult result) {
        System.out.println("   Stack: " + result.localStackName());
        System.out.println("   Endpoint: " + result.endpoint());
    }

    public static void printDryRun(DeploymentResult result) {
        System.out.println("\n🔍 MiniStack adaptation dry-run complete");
        if (result.adaptation() != null) {
            System.out.println("   Adaptations: " + result.adaptation().adaptations().size());
        }
        if (result.adaptationReport() != null) {
            System.out.println("   Audit report: " + result.adaptationReport());
        }
    }

    public static void printVerify(DeploymentResult result) {
        System.out.println("   Stack exists and is queryable");
        result.outputs().forEach((key, value) -> System.out.println("   " + key + ": " + value));
    }

    public static void printOutcome(DeploymentResult result, String targetLabel, String applicationId) {
        if (result.replaceResult() != null && applicationId != null) {
            for (String stack : result.replaceResult().deletedStacks()) {
                System.out.println("🗑️  Replacing prior " + targetLabel + " stack for "
                    + applicationId.trim() + ": " + stack);
            }
            result.replaceResult().warning().ifPresent(w -> System.out.println("⚠️  " + w));
        }
        result.warning().ifPresent(w -> System.out.println("⚠️  " + w));

        if (result.adaptation() != null && result.adaptation().hasAdaptations()) {
            System.out.println("   Local adaptations: "
                + result.adaptation().adaptations().size());
            if (result.adaptationReport() != null) {
                System.out.println("   Audit report: " + result.adaptationReport());
            }
        }

        if (result.deployment() != null) {
            if (result.deployment().noOp()) {
                System.out.println("   ✅ Stack is already current (no changes)");
            } else {
                System.out.println("   ✅ "
                    + (result.deployment().created() ? "Stack created" : "Stack updated"));
                for (LocalResourceChange change : result.deployment().changes()) {
                    System.out.println("      " + change.action() + " "
                        + change.resourceType() + " " + change.logicalResourceId());
                }
            }
        }

        result.outputs().forEach((key, value) ->
            System.out.println("   Output " + key + ": " + value));

        if (result.target() == DeploymentTarget.LOCALSTACK) {
            System.out.println("   Resource browser: http://localhost:8888"
                + " (start/reconcile it from the Interactive Deployer platform menu)");
        } else if (result.target() == DeploymentTarget.MINISTACK) {
            System.out.println("   Resource browser: http://localhost:8888"
                + " (start/reconcile it from the Interactive Deployer platform menu)");
        }

        for (int i = 0; i < result.catalogPaths().size(); i++) {
            Path catalog = result.catalogPaths().get(i);
            if (i == 0) {
                System.out.println("   Catalog: " + catalog);
            } else {
                System.out.println("   Catalog (Manager volume): " + catalog);
            }
        }

        for (String message : result.messages()) {
            System.out.println("⚠️  " + message);
        }
    }
}
