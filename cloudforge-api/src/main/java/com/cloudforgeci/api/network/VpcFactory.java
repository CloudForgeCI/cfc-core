package com.cloudforgeci.api.network;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.List;

/**
 * VPC Factory using annotation-based context injection.
 * This demonstrates the cleaner approach without passing SystemContext as parameters.
 */
public class VpcFactory extends BaseFactory {

    public VpcFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Create VPC with basic configuration
        Vpc vpc = createVpc();
        
        // Add flow logs if configured
        if (ctx.flowlogs.get().isPresent()) {
            vpc.addFlowLog("VpcFlowlog", ctx.flowlogs.get().orElseThrow());
        }
        
        // Store VPC in SystemContext
        ctx.vpc.set(vpc);
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .vpcName(getNode().getId() + "Vpc")
                .natGateways(0)  // No NAT gateways by default - users can add them later
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("private")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build()
                ))
                .build();
    }
}