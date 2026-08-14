package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** File-based helpers for {@link TemplateAdapter} implementations. */
public final class TemplateAdapterSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TemplateAdapterSupport() {
    }

    public static TemplateAdaptationResult adaptFile(
            TemplateAdapter adapter,
            Path canonicalTemplate,
            Path localTemplate,
            Path report,
            String stackName) throws IOException {
        ObjectNode canonical = (ObjectNode) MAPPER.readTree(canonicalTemplate.toFile());
        TemplateAdaptationResult result = adapter.adapt(canonical, stackName);

        Files.createDirectories(localTemplate.toAbsolutePath().getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(localTemplate.toFile(), result.template());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), result.adaptations());
        return result;
    }
}
