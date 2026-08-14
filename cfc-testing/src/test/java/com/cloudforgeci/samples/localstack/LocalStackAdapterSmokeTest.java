package com.cloudforgeci.samples.localstack;

import com.cloudforgeci.localstack.LocalStackTemplateAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight LocalStack adapter checks (no running LocalStack required).
 * Live deploy smoke tests can be added under {@code @Tag("localstack")} later.
 */
@Tag("localstack")
class LocalStackAdapterSmokeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void adapterKeepsForwardStripsAuthAndEfs() {
        ObjectNode canonical = MAPPER.createObjectNode();
        ObjectNode resources = canonical.putObject("Resources");

        resources.putObject("Fs").put("Type", "AWS::EFS::FileSystem");

        ObjectNode alb = resources.putObject("Alb");
        alb.put("Type", "AWS::ElasticLoadBalancingV2::LoadBalancer");
        alb.putObject("Properties").put("Name", "cfc-smoke");

        ObjectNode listener = resources.putObject("Http");
        listener.put("Type", "AWS::ElasticLoadBalancingV2::Listener");
        ArrayNode actions = listener.putObject("Properties").putArray("DefaultActions");
        actions.addObject().put("Type", "authenticate-oidc");
        actions.addObject().put("Type", "forward").putObject("ForwardConfig");

        ObjectNode task = resources.putObject("Task");
        task.put("Type", "AWS::ECS::TaskDefinition");
        ObjectNode props = task.putObject("Properties");
        props.putArray("ContainerDefinitions").addObject()
            .putArray("PortMappings").addObject().put("ContainerPort", 8080);
        props.putArray("Volumes").addObject()
            .put("Name", "data")
            .putObject("EFSVolumeConfiguration").put("FileSystemId", "fs-1");

        var result = LocalStackTemplateAdapter.INSTANCE.adapt(canonical, "Smoke");
        ObjectNode adaptedResources = (ObjectNode) result.template().get("Resources");

        assertFalse(adaptedResources.has("Fs"));
        ArrayNode adaptedActions = (ArrayNode) adaptedResources
            .path("Http").path("Properties").path("DefaultActions");
        assertEquals(1, adaptedActions.size());
        assertEquals("forward", adaptedActions.get(0).path("Type").asText());
        assertTrue(result.template().path("Outputs").has("LocalStackApplicationUrl"));
    }
}
