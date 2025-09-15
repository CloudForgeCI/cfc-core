package com.cloudforgeci.api.api;

import software.amazon.awscdk.StackProps;

@Deprecated(forRemoval = true)
public class JenkinsConfig {
    public final String stackName;
    public final boolean enableDomainAndSsl;
    public final String hostedZoneDomain;
    public final String fullDomainName;
    public final StackProps stackProps;

    public JenkinsConfig(String stackName,
                         boolean enableDomainAndSsl,
                         String hostedZoneDomain,
                         String fullDomainName,
                         StackProps stackProps) {
        this.stackName = stackName;
        this.enableDomainAndSsl = enableDomainAndSsl;
        this.hostedZoneDomain = hostedZoneDomain;
        this.fullDomainName = fullDomainName;
        this.stackProps = stackProps;
    }

}
