package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Reads canonical CloudFormation templates for preflight scans.
 */
public final class TemplateResourceScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TemplateResourceScanner() {
    }

    public static JsonNode readTemplate(Path canonicalTemplate) throws IOException {
        return MAPPER.readTree(canonicalTemplate.toFile());
    }

    /**
     * Primary container port from the first {@code AWS::ECS::Service} load balancer mapping.
     */
    public static int findEcsContainerPort(JsonNode template) {
        JsonNode resources = template == null ? null : template.path("Resources");
        if (resources == null || !resources.isObject()) {
            return 0;
        }
        Iterator<JsonNode> values = resources.elements();
        while (values.hasNext()) {
            JsonNode resource = values.next();
            if ("AWS::ECS::Service".equals(resource.path("Type").asText())) {
                int port = resource.path("Properties")
                    .path("LoadBalancers").path(0).path("ContainerPort").asInt();
                if (port > 0) {
                    return port;
                }
            }
        }
        return 0;
    }
}
