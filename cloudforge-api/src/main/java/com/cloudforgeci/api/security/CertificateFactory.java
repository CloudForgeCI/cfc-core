package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

public class CertificateFactory extends BaseFactory {

    @com.cloudforge.core.annotation.SystemContext("zone")
    private IHostedZone zone;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("fqdn")
    private String fqdn;

    @DeploymentContext("subdomain")
    private String subdomain;

    public CertificateFactory(Construct scope, String id) {
        super(scope, id);
        // enableSsl, domain, fqdn, subdomain, and zone are automatically injected by BaseFactory
    }

    @Override
    public void create() {
        // IMPORTANT: Certificate creation is now handled by runtime configurations (Ec2RuntimeConfiguration, FargateRuntimeConfiguration)
        // This ensures proper dependency ordering: Certificate -> Listener -> ALB
        // CloudFormation will automatically delete in reverse order: ALB -> Listener -> Certificate
        //
        // DO NOT create certificates here - it causes deletion order issues where CloudFormation
        // tries to delete the certificate before the listener, resulting in "ResourceInUseException"
        //
        // The runtime configuration creates the certificate with the listener dependency established,
        // ensuring clean deletion without errors.
    }

}
