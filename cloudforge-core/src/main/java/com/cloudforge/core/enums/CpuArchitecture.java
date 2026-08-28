package com.cloudforge.core.enums;

/**
 * Target CPU architecture for a Fargate task definition. Deliberately not the AWS CDK
 * {@code software.amazon.awscdk.services.ecs.CpuArchitecture} type -- cloudforge-core has no
 * AWS CDK dependency, and this needs to be settable from {@link
 * com.cloudforge.core.interfaces.ApplicationSpec} implementations that live outside
 * cloudforge-api too. cloudforge-api's FargateFactory maps this to the real CDK type when
 * building the actual task definition.
 */
public enum CpuArchitecture {
    X86_64,
    ARM64
}
