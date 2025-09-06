package com.cloudforgeci.community.core.net;

import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.constructs.Construct;

import java.util.Arrays;

public final class PublicVpcFactory {


    public static class Props {

        private final String id;
        private final String name;
        private final Integer maxAzs;
        private final Integer natGateways;



        private Props(Builder b) {
            this.id = b.id;
            this.name = b.name;
            this.maxAzs = b.maxAzs;
            this.natGateways = b.natGateways;
        }
        public static Builder builder(){ return new Builder(); }
        public static class Builder {
            private String id;
            private String name;
            private Integer maxAzs;
            private Integer natGateways;

            public Builder id(final String v) { this.id = v; return this;}
            public Builder name(final String v) { this.name = v; return this;}
            public Builder maxAzs(final Integer v) { this.maxAzs = v; return this;}
            public Builder natGateways(final Integer v) { this.natGateways = v; return this;}

            public Props build(){ return new Props(this); }
        }
    }
    private static Vpc vpc = null;

    private PublicVpcFactory() {}
    public static Vpc createVpc(final Construct scope, final Props p) {
        PublicVpcFactory.vpc = Vpc.Builder.create(scope, p.id)
                .maxAzs(p.maxAzs)
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder().name(p.id + "Public").subnetType(SubnetType.PUBLIC).build(),
                        SubnetConfiguration.builder().name(p.id + "PrivE").subnetType(SubnetType.PRIVATE_WITH_EGRESS).build()
                ))
                .natGateways(p.natGateways)
                .build();

        return vpc;
    }

    public static Vpc getVpc() {
        return PublicVpcFactory.vpc;
    }
}
