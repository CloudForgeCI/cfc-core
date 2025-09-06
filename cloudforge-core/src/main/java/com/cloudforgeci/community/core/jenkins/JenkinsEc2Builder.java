package com.cloudforgeci.community.core.jenkins;

import com.cloudforgeci.core.api.DeploymentContext;
import com.cloudforgeci.core.api.JenkinsConfig;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ec2.Instance;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.MachineImage;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationActionProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.IListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.RedirectOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.targets.InstanceTarget;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;

import java.util.Arrays;

public class JenkinsEc2Builder {

    public static class Result {
        public final Vpc vpc;
        public final Instance instance;
        public final ApplicationLoadBalancer alb; // may be null
        public Result(Vpc vpc, Instance instance, ApplicationLoadBalancer alb) {
            this.vpc = vpc; this.instance = instance; this.alb = alb;
        }
    }

    public static Result create(Stack stack, String id, JenkinsConfig cfg) {

        DeploymentContext ctx = DeploymentContext.from(stack);
        CfnOutput.Builder.create(stack, "Context Loaded")
                .description("Loaded Context Values")
                .value(ctx.toString())
                .build();

        Vpc vpc = Vpc.Builder.create(stack, id + "Vpc").maxAzs(2).natGateways(0).build();

        SecurityGroup sg = SecurityGroup.Builder.create(stack, id + "Sg")
                .vpc(vpc).allowAllOutbound(true).description("Jenkins EC2 SG").build();
        sg.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "SSH", false);
        sg.addIngressRule(Peer.anyIpv4(), Port.tcp(8080), "Jenkins", false);
        sg.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "HTTP", false);
        sg.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "HTTPS", false);

        Role role = Role.Builder.create(stack, id + "Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                )).build();

        Instance instance = Instance.Builder.create(stack, id + "Instance")
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                .machineImage(MachineImage.latestAmazonLinux2023())
                .securityGroup(sg).role(role).requireImdsv2(true)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .build();

        software.amazon.awscdk.Tags.of(instance).add("backup", "true");

        instance.getUserData().addCommands(
                "set -eux",
                "sudo dnf update -y",
                "sudo dnf install -y java-17-amazon-corretto-headless wget",
                "sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key",
                "sudo curl -fsSL https://pkg.jenkins.io/redhat-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo",
                "sudo dnf install -y jenkins",
                "sudo systemctl enable jenkins",
                "sudo systemctl start jenkins"
        );

        ApplicationLoadBalancer alb = null;
        if (cfg.enableDomainAndSsl) {
            HostedZone zone = (HostedZone) HostedZone.fromLookup(stack, id + "Zone",
                    software.amazon.awscdk.services.route53.HostedZoneProviderProps.builder()
                            .domainName(cfg.hostedZoneDomain).build());
            Certificate cert = Certificate.Builder.create(stack, id + "Cert")
                    .domainName(cfg.fullDomainName)
                    .validation(CertificateValidation.fromDns(zone)).build();

            IListenerCertificate listenerCertificate = ListenerCertificate.fromCertificateManager(cert);


            alb = ApplicationLoadBalancer.Builder.create(stack, id + "Alb")
                    .vpc(vpc).internetFacing(true).securityGroup(sg).build();

            ApplicationListener https = alb.addListener(id + "Https", BaseApplicationListenerProps.builder()
                    .port(443).protocol(ApplicationProtocol.HTTPS)
                    .certificates(Arrays.asList(listenerCertificate)).build());

            https.addTargets(id + "JenkinsTg", AddApplicationTargetsProps.builder()
                    .port(8080).protocol(ApplicationProtocol.HTTP)
                    .targets(Arrays.asList(new InstanceTarget(instance, 8080)))
                    .healthCheck(HealthCheck.builder().path("/login").healthyHttpCodes("200-399")
                            .interval(Duration.seconds(30)).build()).build());

            alb.addListener(id + "Http", BaseApplicationListenerProps.builder()
                    .port(80).protocol(ApplicationProtocol.HTTP).build())
               .addAction(id + "Redirect", AddApplicationActionProps.builder()
                    .action(ListenerAction.redirect(RedirectOptions.builder().protocol("HTTPS").port("443").build()))
                    .build());

            new ARecord(stack, id + "ARecord", software.amazon.awscdk.services.route53.ARecordProps.builder()
                    .zone(zone)
                    .recordName(cfg.fullDomainName.replace("." + cfg.hostedZoneDomain, ""))
                    .target(RecordTarget.fromAlias(new LoadBalancerTarget(alb))).build());
        }

        return new Result(vpc, instance, alb);
    }
}
