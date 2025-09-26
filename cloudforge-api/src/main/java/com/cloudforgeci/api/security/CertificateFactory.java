package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.constructs.Construct;

public class CertificateFactory extends BaseFactory {

    @com.cloudforgeci.api.core.annotation.SystemContext
    private SystemContext ctx;

    @com.cloudforgeci.api.core.annotation.DeploymentContext
    private DeploymentContext cfc;

    private final Props p;

    public record Props(
            DeploymentContext cfc
    ) {}

    public CertificateFactory(Construct scope, String id, Props props) {
        super(scope, id);
        this.p = props;
    }

    @Override
    public void create() {
        if (p.cfc.enableSsl() && p.cfc.domain() != null && !p.cfc.domain().isBlank()) {
            createSslCertificate(ctx);
            // Note: ARecord creation is handled by runtime configuration (Ec2RuntimeConfiguration, FargateRuntimeConfiguration)
            // to avoid duplicate record creation
        }
    }

    public void create(final SystemContext ctx) {
        if (p.cfc.enableSsl() && p.cfc.domain() != null && !p.cfc.domain().isBlank()) {
            createSslCertificate(ctx);
            // Note: ARecord creation is handled by runtime configuration (Ec2RuntimeConfiguration, FargateRuntimeConfiguration)
            // to avoid duplicate record creation
        }
    }

    private void createSslCertificate(SystemContext ctx) {
        String fqdn = p.cfc.fqdn();
        if (fqdn == null || fqdn.isBlank()) {
            // Construct FQDN from subdomain and domain
            String subdomain = p.cfc.subdomain();
            String domain = p.cfc.domain();
            fqdn = subdomain + "." + domain;
        }

        // Create SSL certificate
        Certificate cert = Certificate.Builder.create(this, "Cert")
                .domainName(fqdn)
                .validation(CertificateValidation.fromDns(ctx.zone.get().orElseThrow()))
                .build();

        // Store certificate in context for runtime configuration to use
        // Only set if not already present to avoid duplicate HTTPS listener creation
        if (!ctx.cert.get().isPresent()) {
            ctx.cert.set(cert);
        }
        
        // Note: HTTPS listener creation is handled by FargateRuntimeConfiguration
        // to avoid duplicate listener creation
    }

}