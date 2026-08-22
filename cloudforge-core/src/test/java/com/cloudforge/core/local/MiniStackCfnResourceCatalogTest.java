package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniStackCfnResourceCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void policyMarksRdsAsUnsupported() {
        assertEquals(MiniStackResourcePolicy.UNSUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::RDS::DBInstance"));
        assertEquals(MiniStackResourcePolicy.UNSUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::RDS::DBParameterGroup"));
    }

    @Test
    void policyMarksEfsAsAdapted() {
        assertEquals(MiniStackResourcePolicy.ADAPTED, MiniStackCfnResourceCatalog.policyFor("AWS::EFS::FileSystem"));
    }

    @Test
    void unsupportedResourcesFindsRdsTypes() throws Exception {
        ObjectNode template = MAPPER.createObjectNode();
        ObjectNode resources = template.putObject("Resources");
        resources.putObject("DbParams").put("Type", "AWS::RDS::DBParameterGroup");
        resources.putObject("Alb").put("Type", "AWS::ElasticLoadBalancingV2::LoadBalancer");

        List<MiniStackCfnResourceCatalog.TemplateResourceRef> unsupported =
            MiniStackCfnResourceCatalog.unsupportedResources(template);

        assertEquals(1, unsupported.size());
        assertEquals("DbParams", unsupported.getFirst().logicalId());
        assertTrue(MiniStackCfnResourceCatalog.templateRequiresRds(template));
    }

    @Test
    void distinctTypesCollectsAllResourceTypes() throws Exception {
        ObjectNode template = MAPPER.createObjectNode();
        ObjectNode resources = template.putObject("Resources");
        resources.putObject("Task").put("Type", "AWS::ECS::TaskDefinition");
        resources.putObject("Service").put("Type", "AWS::ECS::Service");

        assertEquals(2, MiniStackCfnResourceCatalog.distinctTypes(template).size());
        assertFalse(MiniStackCfnResourceCatalog.templateRequiresRds(template));
    }
}
