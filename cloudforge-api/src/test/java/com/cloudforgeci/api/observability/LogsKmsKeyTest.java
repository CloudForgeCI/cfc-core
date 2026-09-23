package com.cloudforgeci.api.observability;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogsKmsKeyTest {

    private Template synth(RemovalPolicy removalPolicy) {
        Stack stack = new Stack(new App(), "LogsKeyTest");
        LogsKmsKey.create(stack, "LogsKey", "logs key", removalPolicy);
        return Template.fromStack(stack);
    }

    @Test
    void enablesKeyRotation() {
        synth(RemovalPolicy.DESTROY).hasResourceProperties("AWS::KMS::Key", Map.of("EnableKeyRotation", true));
    }

    @Test
    void letsTheCloudWatchLogsServiceUseTheKey() {
        String keys = synth(RemovalPolicy.DESTROY).findResources("AWS::KMS::Key").toString();

        assertTrue(keys.contains("Allow CloudWatch Logs"));
        assertTrue(keys.contains("kms:GenerateDataKey*"));
        assertTrue(keys.contains("logs."));
    }

    @Test
    void retainsTheKeyOnlyWhenTheLogsAreRetained() {
        synth(RemovalPolicy.RETAIN).hasResource("AWS::KMS::Key", Map.of("DeletionPolicy", "Retain"));
        synth(RemovalPolicy.DESTROY).hasResource("AWS::KMS::Key", Map.of("DeletionPolicy", "Delete"));
    }

    @Test
    void usesTheDescriptionItIsGiven() {
        synth(RemovalPolicy.DESTROY).hasResourceProperties("AWS::KMS::Key", Map.of("Description", "logs key"));
    }
}
