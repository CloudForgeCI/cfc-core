package com.cloudforgeci.api.observability;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.Key;
import software.constructs.Construct;

import java.util.List;

/** Creates the customer-managed KMS key used to encrypt CloudWatch log groups. */
public final class LogsKmsKey {

    private LogsKmsKey() {
    }

    /**
     * Creates a key with rotation enabled and a policy statement that lets the CloudWatch Logs service
     * use it. The key is retained only when the log removal policy is {@code RETAIN}.
     */
    public static Key create(Construct scope, String id, String description, RemovalPolicy logRemovalPolicy) {
        Key key = Key.Builder.create(scope, id)
                .description(description)
                .enableKeyRotation(true)
                .removalPolicy(logRemovalPolicy == RemovalPolicy.RETAIN ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        key.addToResourcePolicy(PolicyStatement.Builder.create()
                .sid("Allow CloudWatch Logs")
                .principals(List.of(new ServicePrincipal("logs." + Stack.of(scope).getRegion() + ".amazonaws.com")))
                .actions(List.of("kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*",
                        "kms:CreateGrant", "kms:DescribeKey"))
                .resources(List.of("*"))
                .build());
        return key;
    }
}
