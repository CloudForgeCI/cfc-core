package com.cloudforgeci.community.core.net;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import static org.junit.jupiter.api.Assertions.*;

public class PublicVpcFactoryTest {
    @Test
    void createsPublicAndPrivateSubnetsAndNat() {
        App app = new App();
        Stack stack = new Stack(app, "VpcStack");

        var props = PublicVpcFactory.Props.builder()
                .id("PubVpc")
                .name("PublicVpc")
                .maxAzs(2)
                .natGateways(1)
                .build();

        var vpc = PublicVpcFactory.createVpc(stack, props);
        assertNotNull(vpc);

        Template t = Template.fromStack(stack);
        // VPC exists
        assertFalse(t.findResources("AWS::EC2::VPC").isEmpty());
        // Subnets: public + private-with-egress (at least 2)
        assertFalse(t.findResources("AWS::EC2::Subnet").isEmpty());
        // NAT gateway count = 1
        assertEquals(1, t.findResources("AWS::EC2::NatGateway").size());
    }
}
