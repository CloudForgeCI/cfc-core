package com.cloudforgeci.api.network;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.route53.CfnHostedZone;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;


/**
 * Domain Factory using annotation-based context extraction.
 * Fields annotated with @DeploymentContext automatically extract values from the context.
 */
public class DomainFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(DomainFactory.class.getName());

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("createZone")
    private boolean createZone;

    @SystemContext("security")
    private SecurityProfile security;

    public DomainFactory(Construct scope, String id) {
        super(scope, id);
        // Values are automatically injected by BaseFactory via annotations
    }


    @Override
    public void create() {
        if (domain != null && !domain.isBlank()) {
            IHostedZone zone = createHostedZone(domain);
            ctx.zone.set(zone);
            ctx.domain.set(domain);
            ctx.subdomain.set(subdomain);
        }
    }

    private IHostedZone createHostedZone(String domainName) {
        if (createZone) {
            // Create a new hosted zone resource when createZone = true
            HostedZone zone = HostedZone.Builder.create(this, getNode().getId() + "Zone")
                    .zoneName(domainName)
                    .build();

            // Set removal policy based on security profile
            // PRODUCTION: RETAIN (keep DNS records for safety)
            // DEV/STAGING: DESTROY (clean up test resources)
            RemovalPolicy policy = (security == SecurityProfile.PRODUCTION)
                ? RemovalPolicy.RETAIN
                : RemovalPolicy.DESTROY;
            zone.applyRemovalPolicy(policy);

            LOG.info("Created hosted zone for " + domainName + " with removal policy: " + policy);

            // Configure Route53 query logging when required by compliance frameworks (SOC2, NIST)
            if (config.isRoute53QueryLoggingEnabled()) {
                configureQueryLogging(zone, domainName, policy);
            }

            return zone;
        } else {
            // Use existing hosted zone lookup when createZone = false (normal behavior)
            LOG.info("Looking up existing hosted zone for " + domainName);
            return HostedZone.fromLookup(this, getNode().getId() + "Zone",
                    HostedZoneProviderProps.builder()
                            .privateZone(false)
                            .domainName(domainName)
                            .build());
        }
    }

    /**
     * Configure Route53 DNS query logging for compliance monitoring.
     *
     * <p>DNS query logging captures all DNS queries made to the hosted zone,
     * providing network visibility for security monitoring and forensics.</p>
     *
     * <p>Required for SOC2 CC7.2 (network monitoring) and NIST AU-2 (audit events).</p>
     *
     * <p>Note: Route53 query logs must be in us-east-1 region. The log group
     * must have the prefix "/aws/route53/" for Route53 to write to it.</p>
     *
     * <p>KMS encryption is enabled for HIPAA/PCI-DSS compliance (log data at rest).</p>
     *
     * @param zone The hosted zone to configure logging for
     * @param domainName The domain name for logging identification
     * @param removalPolicy The removal policy to apply to the log group
     */
    private void configureQueryLogging(HostedZone zone, String domainName, RemovalPolicy removalPolicy) {
        LOG.info("Enabling Route53 query logging for " + domainName + " (SOC2/NIST compliance)");

        // Create CloudWatch Log Group for DNS query logs
        // Note: Route53 requires the log group name to start with /aws/route53/
        String logGroupName = "/aws/route53/" + domainName.replace(".", "-");

        // Create KMS key for log encryption (HIPAA/PCI-DSS compliance requirement)
        Key queryLogsKmsKey = Key.Builder.create(this, "Route53QueryLogsKmsKey")
                .description("KMS key for Route53 query logs encryption")
                .enableKeyRotation(true)
                .removalPolicy(removalPolicy)
                .build();

        // Grant CloudWatch Logs permission to use the KMS key
        queryLogsKmsKey.grantEncryptDecrypt(new ServicePrincipal("logs.amazonaws.com"));

        LogGroup queryLogGroup = LogGroup.Builder.create(this, "Route53QueryLogs")
                .logGroupName(logGroupName)
                .retention(config.getLogRetentionDays())
                .removalPolicy(removalPolicy)
                .encryptionKey(queryLogsKmsKey)
                .build();

        LOG.info("Route53 query logs KMS encryption enabled");

        // Grant Route53 permission to write to the log group
        queryLogGroup.addToResourcePolicy(PolicyStatement.Builder.create()
                .principals(List.of(new ServicePrincipal("route53.amazonaws.com")))
                .actions(List.of(
                        "logs:CreateLogStream",
                        "logs:PutLogEvents"
                ))
                .resources(List.of(queryLogGroup.getLogGroupArn()))
                .build());

        // Configure query logging on the hosted zone using L1 construct
        CfnHostedZone cfnZone = (CfnHostedZone) zone.getNode().getDefaultChild();
        cfnZone.addPropertyOverride("QueryLoggingConfig", java.util.Map.of(
                "CloudWatchLogsLogGroupArn", queryLogGroup.getLogGroupArn()
        ));

        LOG.info("Route53 query logging configured for " + domainName + " -> " + logGroupName);
    }

}
