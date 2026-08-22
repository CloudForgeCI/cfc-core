package com.cloudforgeci.api.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the canonical synthesized CloudFormation template on disk.
 */
public final class CanonicalTemplateResolver {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalTemplateResolver() {
    }

    /**
     * Return {@code outputDirectory/{stackName}.template.json} when it already exists.
     */
    public static Path resolveExisting(Path outputDirectory, String stackName) throws IOException {
        Path expected = outputDirectory.resolve(stackName + ".template.json");
        if (!Files.exists(expected)) {
            throw new IOException("Canonical template not found: " + expected
                + ". Synthesize first (menu option 1, then deploy).");
        }
        return expected;
    }

    /**
     * Prefer an on-disk template under {@code outputDirectory}; otherwise materialize from
     * {@code assembly}.
     */
    public static Path resolve(Path outputDirectory, String stackName, CloudAssembly assembly)
            throws IOException {
        Path expected = outputDirectory.resolve(stackName + ".template.json");
        if (Files.exists(expected)) {
            return expected;
        }
        if (assembly == null) {
            throw new IOException("No CloudAssembly available for stack '" + stackName + "'.");
        }
        CloudFormationStackArtifact stack = assembly.getStackByName(stackName);
        if (stack == null) {
            List<String> stacks = assembly.getStacks().stream()
                .map(CloudFormationStackArtifact::getStackName)
                .sorted()
                .toList();
            throw new IOException(
                "Stack '" + stackName + "' not found in cloud assembly."
                    + (stacks.isEmpty() ? "" : " Available stacks: " + stacks));
        }
        return materialize(stack, expected);
    }

    /**
     * Ensure the synthesized stack template exists at {@code expectedPath}.
     */
    public static Path materialize(CloudFormationStackArtifact stack, Path expectedPath)
            throws IOException {
        String fullPath = stack.getTemplateFullPath();
        if (fullPath != null && !fullPath.isBlank()) {
            Path onDisk = Path.of(fullPath);
            if (Files.exists(onDisk)) {
                return onDisk;
            }
        }

        Object template = stack.getTemplate();
        if (template == null) {
            throw new IOException(
                "No template available for stack '" + stack.getStackName()
                    + "'. Synthesize first.");
        }

        Files.createDirectories(expectedPath.getParent());
        MAPPER.writeValue(expectedPath.toFile(), template);
        return expectedPath;
    }
}
