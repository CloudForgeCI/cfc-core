package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.services.ec2.FlowLogDestination;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * VPC Flow Log Factory using annotation-based context injection.
 * Configures VPC flow logs based on security profile settings.
 */
public class FlowLogFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(FlowLogFactory.class.getName());

    @SystemContext("security")
    private SecurityProfile security;

    public FlowLogFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        LOG.info("Configuring flow logs for security profile: " + security);

        // Check if flow logs are enabled for this security profile
        if (!config.isFlowLogsEnabled()) {
            LOG.info("Flow logs disabled for security profile: " + security);
            return;
        }

        // Check if flow logs are already configured
        if (ctx.flowlogs.get().isPresent()) {
            LOG.info("Flow logs already configured, skipping");
            return;
        }

        // Create flow log log group with security profile-based settings
        // Note: logGroupName is intentionally omitted to allow CloudFormation to auto-generate unique names
        // This prevents naming conflicts when deploying multiple stacks with the same security profile
        // Use getLogRetentionDays() which is compliance-aware (respects logRetentionDays override)
        LogGroup.Builder logGroupBuilder = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                    .retention(config.getLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy());

        // Add KMS encryption when enabled (required for PCI-DSS, HIPAA, SOC2 compliance)
        Key flowLogsKmsKey = null;
        if (config.isCloudWatchLogsKmsEncryptionEnabled()) {
            flowLogsKmsKey = Key.Builder.create(this, "FlowLogsKmsKey")
                    .description("KMS key for VPC Flow Logs encryption")
                    .enableKeyRotation(true)
                    .removalPolicy(config.getLogRemovalPolicy())
                    .build();
            logGroupBuilder.encryptionKey(flowLogsKmsKey);
            LOG.info("VPC Flow Logs KMS encryption enabled");

            // Register AWS Config rule for CloudWatch Logs KMS encryption compliance
            ctx.requireConfigRule(AwsConfigRule.CLOUDWATCH_LOG_GROUP_ENCRYPTED);
        }

        LogGroup logGroup = logGroupBuilder.build();

        if (flowLogsKmsKey != null) {
            // CDK's LogGroup#encryptionKey does NOT grant the CloudWatch Logs service permission
            // to use the key — a customer-managed key defaults to an account-root-only policy, so
            // without this explicit grant CreateLogGroup fails with AccessDenied the moment this
            // path is actually exercised (see ComplianceFactory's identical CloudTrail-log-group
            // fix for the full explanation).
            //
            // Resource is "*", not this key's own explicit ARN — verified that spelling out the key's own ARN here creates
            // a CDK circular dependency: it's a self-reference (Ref to this same key's own
            // logical id) inside this key's OWN resource policy, which CDK's dependency graph
            // rejects as a self-loop (the kms:EncryptionContext condition referencing the log
            // group's ARN was a red herring — removing just that changed nothing, this was the
            // actual cause). "*" in a resource-based policy on the key itself isn't a real wildcard
            // grant — the policy is already scoped to this one key by being attached to it — it's
            // the standard AWS pattern specifically to avoid this self-reference, and matches every
            // other working KMS-log grant already in this codebase (LoggingCwFactory's,
            // ComplianceFactory's identical CloudTrail-log-group fix).
            String region = software.amazon.awscdk.Stack.of(this).getRegion();
            flowLogsKmsKey.addToResourcePolicy(
                software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                    .sid("Enable CloudWatch Logs encryption")
                    .effect(software.amazon.awscdk.services.iam.Effect.ALLOW)
                    .principals(java.util.List.of(new software.amazon.awscdk.services.iam.ServicePrincipal(
                        "logs." + region + ".amazonaws.com")))
                    .actions(java.util.List.of("kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*",
                        "kms:GenerateDataKey*", "kms:Describe*"))
                    .resources(java.util.List.of("*"))
                    .build());
        }

        // Create flow log options with security profile-based traffic type
        FlowLogOptions flowLogOptions = FlowLogOptions.builder()
                .trafficType(config.getFlowLogTrafficType())
                .destination(FlowLogDestination.toCloudWatchLogs(logGroup))
                .build();

        ctx.flowlogs.set(flowLogOptions);

        // Register AWS Config rule for VPC Flow Logs compliance
        ctx.requireConfigRule(AwsConfigRule.VPC_FLOW_LOGS_ENABLED);

        LOG.info("Flow logs configured for " + security + " profile: " +
                "traffic = " + config.getFlowLogTrafficType() +
                ", retention = " + config.getLogRetentionDays() +
                ", removal = " + config.getLogRemovalPolicy());
    }

}
