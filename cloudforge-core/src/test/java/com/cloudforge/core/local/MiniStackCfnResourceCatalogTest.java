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
    void policyAdaptsDatabaseInstancesAndStillBlocksClusters() {
        assertEquals(MiniStackResourcePolicy.ADAPTED, MiniStackCfnResourceCatalog.policyFor("AWS::RDS::DBInstance"));
        assertEquals(MiniStackResourcePolicy.SUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::RDS::DBParameterGroup"));
        assertEquals(MiniStackResourcePolicy.SUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::RDS::DBSubnetGroup"));
        assertEquals(MiniStackResourcePolicy.UNSUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::RDS::DBCluster"));
    }

    @Test
    void policyAdaptsTheTypesTheMiniStackAdapterRewritesOrCreatesAfterwards() {
        for (String type : new String[] {"AWS::ACMPCA::CertificateAuthority", "AWS::ACMPCA::Permission",
                "AWS::Cognito::UserPoolUser", "AWS::Cognito::UserPoolUserToGroupAttachment",
                "AWS::Cognito::ManagedLoginBranding", "Custom::LogRetention"}) {
            assertEquals(MiniStackResourcePolicy.ADAPTED, MiniStackCfnResourceCatalog.policyFor(type), type);
        }
        assertEquals(MiniStackResourcePolicy.SUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::Cognito::UserPool"));
    }

    @Test
    void policyAdaptsTheComplianceResourcesMiniStackCannotCreateAndSupportsTheRest() {
        for (String type : MiniStackCfnResourceCatalog.REMOVED_COMPLIANCE_TYPES) {
            assertEquals(MiniStackResourcePolicy.ADAPTED, MiniStackCfnResourceCatalog.policyFor(type), type);
        }
        for (String type : new String[] {"AWS::WAFv2::WebACL", "AWS::Backup::BackupVault", "AWS::Backup::BackupPlan"}) {
            assertEquals(MiniStackResourcePolicy.SUPPORTED, MiniStackCfnResourceCatalog.policyFor(type), type);
        }
        assertEquals(MiniStackResourcePolicy.UNSUPPORTED, MiniStackCfnResourceCatalog.policyFor("AWS::Config::ConfigurationRecorder"));
    }

    @Test
    void policyMarksEfsAsAdapted() {
        assertEquals(MiniStackResourcePolicy.ADAPTED, MiniStackCfnResourceCatalog.policyFor("AWS::EFS::FileSystem"));
    }

    @Test
    void unsupportedResourcesFindsRdsTypes() throws Exception {
        ObjectNode template = MAPPER.createObjectNode();
        ObjectNode resources = template.putObject("Resources");
        resources.putObject("DbParams").put("Type", "AWS::RDS::DBCluster");
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
