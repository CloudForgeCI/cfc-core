package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.constructs.Construct;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Centralized SSL management system that creates SSL certificates and DNS records.
 * HTTPS listener creation is handled by runtime configurations to ensure proper target group setup.
 */
public class SslManager extends Construct {
    
    private static final Map<String, Certificate> certificates = new ConcurrentHashMap<>();
    
    private final Props props;
    
    public record Props(
        DeploymentContext cfc
    ) {}
    
    public SslManager(Construct scope, String id, Props props) {
        super(scope, id);
        this.props = props;
    }
    
    /**
     * Creates SSL certificate and HTTPS listener if SSL is enabled.
     * This method is idempotent - it will only create resources once per ALB.
     */
    public void createSslIfEnabled(SystemContext ctx) {
        if (!props.cfc.enableSsl() || props.cfc.domain() == null || props.cfc.domain().isBlank()) {
            return;
        }
        
        String albId = getAlbId(ctx);
        
        // Create certificate if not already created
        if (!certificates.containsKey(albId)) {
            createCertificate(ctx, albId);
        }
        
        // Create DNS record if not already created
        createDnsRecord(ctx);
        
        // Note: HTTPS listener creation is handled by FargateRuntimeConfiguration
        // to ensure proper target group configuration
    }
    
    private void createCertificate(SystemContext ctx, String albId) {
        String fqdn = props.cfc.fqdn();
        if (fqdn == null || fqdn.isBlank()) {
            String subdomain = props.cfc.subdomain();
            String domain = props.cfc.domain();
            fqdn = subdomain + "." + domain;
        }
        
        Certificate cert = Certificate.Builder.create(this, "SslCert-" + albId)
                .domainName(fqdn)
                .validation(CertificateValidation.fromDns(ctx.zone.get().orElseThrow()))
                .build();
        
        certificates.put(albId, cert);
        ctx.cert.set(cert);
    }
    
    private void createDnsRecord(SystemContext ctx) {
        ctx.zone.get().ifPresent(zone -> {
            ctx.alb.get().ifPresent(alb -> {
                new ARecord(this, "SslARecord", ARecordProps.builder()
                        .zone(zone)
                        .recordName(props.cfc.subdomain() == null ? "" : props.cfc.subdomain())
                        .target(RecordTarget.fromAlias(new LoadBalancerTarget(alb)))
                        .build());
            });
        });
    }
    
    private String getAlbId(SystemContext ctx) {
        return ctx.alb.get()
                .map(alb -> alb.getNode().getId())
                .orElse("default");
    }
}
