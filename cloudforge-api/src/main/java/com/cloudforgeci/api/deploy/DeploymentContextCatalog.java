package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists per-stack deployment context snippets for Manager multi-stack enrichment.
 */
public final class DeploymentContextCatalog {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeploymentContextCatalog() {
    }

    /**
     * Write {@code deployment-contexts/{stackName}.json} and optional Manager volume copies.
     *
     * @return paths written (primary catalog first)
     */
    public static List<Path> persist(
            DeploymentConfig config,
            Path catalogDirectory,
            List<String> managerVolumeRoots) throws IOException {
        if (config == null || config.stackName == null || config.stackName.isBlank()) {
            return List.of();
        }

        Map<String, Object> body = catalogBody(config);
        List<Path> written = new ArrayList<>();

        Files.createDirectories(catalogDirectory);
        Path primary = catalogDirectory.resolve(config.stackName + ".json");
        MAPPER.writeValue(primary.toFile(), body);
        written.add(primary);
        registerKnownStack(catalogDirectory.resolveSibling("panel-stacks.json"), config.stackName);

        if (managerVolumeRoots != null) {
            for (String volumeRoot : managerVolumeRoots) {
                if (volumeRoot == null || volumeRoot.isBlank()) {
                    continue;
                }
                Path managerCatalogDir = Path.of(volumeRoot)
                    .resolve("CloudForgeManager-Dev")
                    .resolve("managerData")
                    .resolve("deployment-contexts");
                try {
                    Files.createDirectories(managerCatalogDir);
                    Path managerFile = managerCatalogDir.resolve(config.stackName + ".json");
                    MAPPER.writeValue(managerFile.toFile(), body);
                    written.add(managerFile);
                    registerKnownStack(managerCatalogDir.resolveSibling("panel-stacks.json"), config.stackName);
                } catch (IOException ignored) {
                    // volume may not exist until Manager itself is deployed
                }
            }
        }
        return List.copyOf(written);
    }

    /**
     * Upserts {@code stackName} into {@code panel-stacks.json} (the AWS-target inventory
     * allow-list {@code StackListingPolicy}/{@code CatalogService} read — see
     * {@code cloudforge-manager}'s {@code StackListingPolicy.loadPanelStacksAllowList} and
     * {@code CatalogService.resolvePanelStacksPath}, which both expect it as a sibling of the
     * {@code deployment-contexts/} directory). Nothing wrote this file before — it was a
     * pure manual allow-list, so any stack not carrying {@code cloudforge:managed}/
     * {@code cloudforge:application} tags (the primary mechanism — see
     * {@code ApplicationFargateStack}/{@code ApplicationEc2Stack}) stayed invisible in AWS
     * inventory until someone hand-edited this file. Best-effort: a merge race with another
     * concurrent deploy can only ever lose an *addition*, never corrupt the file or drop an
     * existing entry — worst case that other stack is picked up on the next deploy/write.
     */
    private static void registerKnownStack(Path panelStacksFile, String stackName) {
        try {
            Set<String> stacks = new LinkedHashSet<>();
            if (Files.isRegularFile(panelStacksFile)) {
                JsonNode root = MAPPER.readTree(panelStacksFile.toFile());
                JsonNode arr = root.isArray() ? root : root.path("stacks");
                if (arr.isArray()) {
                    for (JsonNode node : arr) {
                        if (node.isTextual() && !node.asText().isBlank()) {
                            stacks.add(node.asText().trim());
                        }
                    }
                }
            }
            if (!stacks.add(stackName)) {
                return; // already known — avoid an unnecessary write
            }
            // getParent() is null when panelStacksFile is itself a bare single-segment relative
            // path (e.g. catalogDirectory == Path.of("deployment-contexts"), whose resolveSibling
            // is just Path.of("panel-stacks.json") — no parent to create; write directly into the
            // process's working directory in that case.
            Path parent = panelStacksFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(panelStacksFile.toFile(), Map.of("stacks", List.copyOf(stacks)));
        } catch (IOException | RuntimeException ignored) {
            // Best-effort, deliberately broad: this must never propagate. It previously only
            // caught IOException, and a null-parent NullPointerException (the case this method
            // now guards against above) escaped uncaught all the way out of
            // CloudForgeDeployment.finalizeResult — aborting the deploy's emulator-edge
            // reconciliation that runs *after* the catalog-persist block, not just this file
            // write. Tag-based AWS inventory matching remains the primary mechanism regardless.
        }
    }

    static Map<String, Object> catalogBody(DeploymentConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stackName", config.stackName);
        body.put("applicationId", config.applicationId);
        body.put("applicationName", config.applicationName != null
            ? config.applicationName
            : config.applicationId);
        body.put("environment", config.environment);
        body.put("runtime", config.runtime == null ? null : config.runtime.name());
        body.put("authMode", config.authMode == null ? null : config.authMode.getValue());

        // Compliance/security posture, so a not-yet-deployed catalog entry (source: "local",
        // no CFN outputs to read from yet) carries the same fields a live instance gets from
        // FargateFactory.createDeploymentMetadataOutputs() — see CatalogService.applyContext /
        // applyCloudFormationMetadata on the Manager side, which read these under matching keys.
        body.put("topology", config.topology == null ? null : config.topology.name());
        body.put("securityProfile", config.securityProfile == null ? null : config.securityProfile.name());
        body.put("complianceMode", config.complianceMode == null ? null : config.complianceMode.name());
        body.put("complianceFrameworks", config.complianceFrameworks == null ? List.of()
            : config.complianceFrameworks.stream().map(Enum::name).toList());
        body.put("region", config.region);
        body.put("domain", config.domain);
        body.put("fqdn", config.fqdn);
        return body;
    }
}
