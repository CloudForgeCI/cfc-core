package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.AaaaRecordProps;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.constructs.Construct;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Centralized SSL management system that creates SSL certificates and DNS records.
 * HTTPS listener creation is handled by runtime configurations to ensure proper target group setup.
 */
public class SslManager extends BaseFactory {
    
    private static final Logger LOG = Logger.getLogger(SslManager.class.getName());
    
    @com.cloudforgeci.api.core.annotation.SystemContext
    private SystemContext ctx;

    @com.cloudforgeci.api.core.annotation.DeploymentContext
    private DeploymentContext cfc;
    
    private static final Map<String, Certificate> certificates = new ConcurrentHashMap<>();
    
    private final Props props;
    
    public record Props(
        DeploymentContext cfc
    ) {}
    
    public SslManager(Construct scope, String id, Props props) {
        super(scope, id);
        this.props = props;
    }
    
    @Override
    public void create() {
        createSslIfEnabled(ctx);
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
        
        // DNS record creation is handled by topology configurations to avoid conflicts
        // DNS records are created by JenkinsServiceTopologyConfiguration and JenkinsSingleNodeTopologyConfiguration
        
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
        // Skip DNS record creation - topology configurations handle this to avoid conflicts
        // DNS records are created by JenkinsServiceTopologyConfiguration and JenkinsSingleNodeTopologyConfiguration
        LOG.info("*** SslManager: Skipping DNS record creation - handled by topology configuration ***");
    }
    
    private String getAlbId(SystemContext ctx) {
        return ctx.alb.get()
                .map(alb -> alb.getNode().getId())
                .orElse("default");
    }
}
