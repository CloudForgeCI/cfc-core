package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHostPortConflictCheckerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void allowsSameStackRedeployOnSamePort() {
        ObjectNode template = ecsServiceTemplate(3000);
        List<PreflightViolation> violations = new ArrayList<>();
        LocalHostPortConflictChecker.validateAgainstOccupants(
            DeploymentTarget.MINISTACK,
            "Grafana-Stack-ministack",
            null,
            template,
            List.of(new LocalHostPortOccupant(
                "Grafana-Stack-ministack",
                3000,
                "stack output MiniStackApplicationUrl")),
            violations);

        assertFalse(violations.stream().anyMatch(v -> v.ruleId().equals("HOST_PORT_CONFLICT")));
    }

    @Test
    void blocksWhenDifferentStackUsesPort() {
        ObjectNode template = ecsServiceTemplate(3000);
        List<PreflightViolation> violations = new ArrayList<>();
        LocalHostPortConflictChecker.validateAgainstOccupants(
            DeploymentTarget.MINISTACK,
            "Gitea-Stack-ministack",
            null,
            template,
            List.of(new LocalHostPortOccupant(
                "Grafana-Stack-ministack",
                3000,
                "running container ministack-ecs-deadbeef-Grafana-StackFargateContainer")),
            violations);

        assertEquals(1, violations.stream()
            .filter(v -> v.ruleId().equals("HOST_PORT_CONFLICT"))
            .count());
        assertTrue(violations.getFirst().message().contains("3000"));
        assertTrue(violations.getFirst().message().contains("Grafana-Stack-ministack"));
    }

    @Test
    void localstackAllowsDuplicateContainerPortsBecauseTasksUseAllocatedHostPorts() {
        ObjectNode template = ecsServiceTemplate(1958);
        List<PreflightViolation> violations = new ArrayList<>();
        LocalHostPortConflictChecker.validateAgainstOccupants(
            DeploymentTarget.LOCALSTACK,
            "CloudForgeManager-RDS-MySQL-localstack",
            null,
            template,
            List.of(new LocalHostPortOccupant(
                "CloudForgeManager-Dev-localstack",
                1958,
                "stack output LocalStackApplicationUrl")),
            violations);

        assertTrue(violations.isEmpty());
    }

    @Test
    void warnsOnPrivilegedMiniStackPort() {
        ObjectNode template = ecsServiceTemplate(80);
        List<PreflightViolation> violations = new ArrayList<>();
        LocalHostPortConflictChecker.validateAgainstOccupants(
            DeploymentTarget.MINISTACK,
            "Drone-Stack-localstack",
            null,
            template,
            List.of(),
            violations);

        assertTrue(violations.stream().anyMatch(v -> v.ruleId().equals("PRIVILEGED_HOST_PORT")));
    }

    @Test
    void inferStackNameFromContainerStripsHashPrefix() {
        assertEquals(
            "Grafana-Stack-ministack",
            LocalEmulatorHostPortProbe.inferStackNameFromContainer(
                "ministack-ecs-e63e6a24-Grafana-StackFargateContainer",
                DeploymentTarget.MINISTACK));
        assertEquals(
            "CloudForgeManager-Dev-ministack",
            LocalEmulatorHostPortProbe.inferStackNameFromContainer(
                "ministack-ecs-ade3b6cb-CloudForgeManager-DevFargateContainer",
                DeploymentTarget.MINISTACK));
    }

    @Test
    void findEcsContainerPortReadsFirstServiceMapping() throws Exception {
        ObjectNode template = ecsServiceTemplate(9090);
        assertEquals(9090, TemplateResourceScanner.findEcsContainerPort(template));
    }

    private static ObjectNode ecsServiceTemplate(int port) {
        ObjectNode template = MAPPER.createObjectNode();
        ObjectNode service = template.putObject("Resources").putObject("Service");
        service.put("Type", "AWS::ECS::Service");
        service.putObject("Properties")
            .putArray("LoadBalancers")
            .addObject()
            .put("ContainerPort", port);
        return template;
    }
}
