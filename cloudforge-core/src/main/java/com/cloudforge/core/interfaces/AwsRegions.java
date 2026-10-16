package com.cloudforge.core.interfaces;

import java.util.List;

/**
 * The commercial AWS regions CloudForge offers for deployment. Enabled by default in every
 * account; opt-in regions are not listed. The Manager and CLI region pickers read this list, and a
 * test keeps the {@code region} option on {@code DeploymentConfig} in step with it.
 */
public final class AwsRegions {

    private static final List<String> COMMERCIAL = List.of(
        "us-east-1", "us-east-2", "us-west-1", "us-west-2",
        "ca-central-1", "sa-east-1",
        "eu-west-1", "eu-west-2", "eu-west-3", "eu-central-1", "eu-north-1",
        "ap-south-1", "ap-northeast-1", "ap-northeast-2", "ap-northeast-3",
        "ap-southeast-1", "ap-southeast-2");

    private AwsRegions() {
    }

    public static List<String> commercial() {
        return COMMERCIAL;
    }
}
