package com.cloudforgeci.core.context;

public record Context(
    String tier,
    String variant,
    JenkinsConfiguration jenkins,
    FargateConfiguration fargate,
    Ec2Configuration ec2,
    Configuration configuration

) {}