package com.cloudforgeci.api.network;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.DeploymentContext;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.List;

/**
 * Factory for creating VPC (Virtual Private Cloud) infrastructure.
 * 
 * <p>This factory creates AWS VPCs with configurable subnet configurations
 * and NAT gateways based on the network mode setting. It uses annotation-based
 * context injection for clean, maintainable code.</p>
 * 
 * <p><strong>Network Configurations:</strong></p>
 * <ul>
 *   <li><strong>public-no-nat:</strong> Creates VPC with public subnets only, no NAT gateways (unless security profile requires them)</li>
 *   <li><strong>private-with-nat:</strong> Creates VPC with public and private subnets, NAT gateways based on security profile</li>
 *   <li><strong>Security profiles:</strong> Automatically determine NAT gateway count based on topology, runtime, and security requirements</li>
 * </ul>
 * 
 * <p><strong>Subnet Configuration:</strong></p>
 * <ul>
 *   <li><strong>Public subnets:</strong> For resources that need direct internet access</li>
 *   <li><strong>Private subnets:</strong> For resources that use NAT gateway for outbound access</li>
 *   <li><strong>CIDR:</strong> /24 masks for both public and private subnets</li>
 * </ul>
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Centralized NAT gateway configuration via security profiles</li>
 *   <li>Automatic NAT gateway count determination based on topology, runtime, and security requirements</li>
 *   <li>Flow logs integration (when configured)</li>
 *   <li>Multi-AZ deployment (2 availability zones)</li>
 *   <li>Annotation-based context injection</li>
 * </ul>
 * 
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * VpcFactory factory = new VpcFactory(scope, "JenkinsVPC");
 * factory.create();
 * 
 * // Access created VPC
 * Vpc vpc = ctx.vpc.get().orElseThrow();
 * }</pre>
 * 
 * @author CloudForgeCI
 * @since 1.0.0
 * @see BaseFactory
 * @see DeploymentContext#networkMode()
 * @see SystemContext#flowlogs
 */
public final class VpcFactory extends BaseFactory {

    public VpcFactory(Construct scope, String id) {
        super(scope, id);
    }

    /**
     * Creates the VPC infrastructure.
     * 
     * <p>This method creates a VPC with appropriate subnet configuration and NAT gateways
     * based on the network mode setting. It also integrates flow logs if they are
     * configured in the system context.</p>
     * 
     * <p>The created VPC is stored in the SystemContext for use by other factories.</p>
     * 
     * @see #createVpc()
     * @see SystemContext#vpc
     * @see SystemContext#flowlogs
     */
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
        // Get NAT gateway count from security profile configuration
        // This encapsulates all logic for topology, runtime, security, and network mode
        int natGateways = config.getNatGatewayCount(ctx.topology, ctx.runtime, cfc.networkMode());
        
        return Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .vpcName(getNode().getId() + "Vpc")
                .natGateways(natGateways)
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