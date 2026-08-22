package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSameApplicationStackReplacerTest {

    @Test
    void skipsWhenApplicationIdBlank() {
        var result = LocalSameApplicationStackReplacer.replace(
            "  ",
            "Jenkins-Dev-ministack",
            "CFC_MINISTACK_REPLACE_SAME_APP",
            DeploymentTarget.MINISTACK,
            () -> { throw new AssertionError("should not list stacks"); },
            stack -> Map.of("cloudforge:application", "jenkins"),
            stack -> Optional.empty(),
            stack -> { throw new AssertionError("should not delete"); });

        assertTrue(result.deletedStacks().isEmpty());
    }

    @Test
    void deletesStacksMatchingTagOrCatalog() throws IOException {
        var deleted = new java.util.ArrayList<String>();

        var result = LocalSameApplicationStackReplacer.replace(
            "jenkins",
            "Jenkins-New-ministack",
            "CFC_UNSET_REPLACE_FLAG_" + System.nanoTime(),
            DeploymentTarget.MINISTACK,
            () -> List.of(
                "Jenkins-New-ministack",
                "Jenkins-Old-ministack",
                "Grafana-Dev-ministack"),
            stack -> "Jenkins-Old-ministack".equals(stack)
                ? Map.of("cloudforge:application", "jenkins")
                : Map.of(),
            stack -> "Grafana-Dev-ministack".equals(stack)
                ? Optional.of("jenkins")
                : Optional.empty(),
            deleted::add);

        assertEquals(List.of("Jenkins-Old-ministack", "Grafana-Dev-ministack"), result.deletedStacks());
        assertEquals(List.of("Jenkins-Old-ministack", "Grafana-Dev-ministack"), deleted);
        assertTrue(result.warning().isEmpty());
    }

    @Test
    void returnsWarningWhenDeleteFails() {
        var result = LocalSameApplicationStackReplacer.replace(
            "jenkins",
            "keep-ministack",
            "CFC_UNSET_REPLACE_FLAG_" + System.nanoTime(),
            DeploymentTarget.MINISTACK,
            () -> List.of("stale-ministack"),
            stack -> Map.of("cloudforge:application", "jenkins"),
            stack -> Optional.empty(),
            stack -> { throw new IOException("boom"); });

        assertTrue(result.deletedStacks().isEmpty());
        assertTrue(result.warning().isPresent());
        assertTrue(result.warning().get().contains("jenkins"));
    }
}
