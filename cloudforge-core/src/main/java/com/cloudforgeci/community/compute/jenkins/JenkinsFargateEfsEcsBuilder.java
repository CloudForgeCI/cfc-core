package com.cloudforgeci.community.compute.jenkins;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.api.JenkinsConfig;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AuthorizationConfig;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.EfsVolumeConfiguration;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.MountPoint;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.efs.AccessPointOptions;
import software.amazon.awscdk.services.efs.Acl;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.PerformanceMode;
import software.amazon.awscdk.services.efs.PosixUser;
import software.amazon.awscdk.services.efs.ThroughputMode;
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
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;

import java.util.Arrays;
import java.util.List;

public class JenkinsFargateEfsEcsBuilder {

    /** Result now exposes FargateService (not the ecs-patterns wrapper). */
    public static class Result {
        public final Vpc vpc;
        public final Cluster cluster;
        public final FargateService service;                 // <- key change
        public final FileSystem efs;                         // may be null if you drop EFS
        public final ApplicationLoadBalancer alb;            // null if no domain/SSL

        public Result(Vpc vpc, Cluster cluster, FargateService service, FileSystem efs, ApplicationLoadBalancer alb) {
            this.vpc = vpc;
            this.cluster = cluster;
            this.service = service;
            this.efs = efs;
            this.alb = alb;
        }
    }

    @Deprecated(forRemoval = true)
    public static Result create(Stack stack, String id, JenkinsConfig cfg, DeploymentContext cfc) {


        // VPC
        Vpc vpc = Vpc.Builder.create(stack, id + "Vpc")
                .maxAzs(2)
                .natGateways(0)
                .build();

        // Security groups
        SecurityGroup albSg = SecurityGroup.Builder.create(stack, "AlbSg")
                .vpc(vpc).allowAllOutbound(true).build();
        // short descriptions (<=255 chars) to avoid SG description limit
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(80),  null,  false);
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(443), null, false);

        SecurityGroup svcSg = SecurityGroup.Builder.create(stack, id + "SvcSg")
                .vpc(vpc).allowAllOutbound(true).build();


        SecurityGroup efsSg = SecurityGroup.Builder.create(stack, id + "EfsSg")
                .vpc(vpc).description("EFS SG").allowAllOutbound(true).build();
        efsSg.addIngressRule(svcSg, Port.tcp(2049), null, false);


        // ECS cluster
        Cluster cluster = Cluster.Builder.create(stack, "Cluster").vpc(vpc).build();


        // EFS (Jenkins home)
        FileSystem fs = FileSystem.Builder.create(stack, id + "Fs")
                .vpc(vpc)
                .securityGroup(efsSg)
                .performanceMode(PerformanceMode.GENERAL_PURPOSE)
                .throughputMode(ThroughputMode.BURSTING)
                .encrypted(true)
                .build();


        AccessPoint ap = fs.addAccessPoint("JenkinsAp", AccessPointOptions.builder()
                .path("/jenkins")
                .posixUser(PosixUser.builder().uid("1000").gid("1000").build())
                .createAcl(Acl.builder().ownerUid("1000").ownerGid("1000").permissions("750").build())
                .build());

        // Create the execution role
        Role executionRole = Role.Builder.create(stack, "TaskExecutionRole")
                .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                ))
                .build();

        // Task & container
        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(stack, id + "TaskDef")
                .cpu(1024)
                .memoryLimitMiB(2048)
                .executionRole(executionRole)
                .build();

        // EFS IAM client permissions (since we enabled IAM auth on the AP)
        taskDef.getExecutionRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("elasticfilesystem:ClientMount","elasticfilesystem:ClientWrite","elasticfilesystem:ClientRootAccess"))
                .resources(List.of(fs.getFileSystemArn(), ap.getAccessPointArn()))
                .build());

        String volName = "jenkinsHome";

        taskDef.addVolume(Volume.builder()
                .name(volName)
                .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(fs.getFileSystemId())
                        .transitEncryption("ENABLED")
                        .authorizationConfig(AuthorizationConfig.builder()
                                .accessPointId(ap.getAccessPointId())
                                .iam("ENABLED")
                                .build())
                        .build())
                .build());

        LogGroup logGroup = LogGroup.Builder.create(stack, "JenkinsLogs")
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        ContainerDefinition container = taskDef.addContainer(id + "Container",
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("jenkins/jenkins:lts"))
                        .user("1000:1000")
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder().logGroup(logGroup).streamPrefix("jenkins").build()))
                        .build());

        container.addPortMappings(PortMapping
                .builder()
                .containerPort(8080)
                .build());

        container.addMountPoints(MountPoint
                .builder()
                .containerPath("/var/jenkins_home")
                .sourceVolume(volName)
                .readOnly(false)
                .build());

        // Fargate service
        FargateService service = FargateService.Builder.create(stack, id + "Service")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .assignPublicIp(true)
                .securityGroups(Arrays.asList(svcSg))
                .desiredCount(1)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .build();

        ApplicationLoadBalancer alb = null;

        if (cfg.enableDomainAndSsl) {
            // Route53 & ACM
            IHostedZone zone = HostedZone.fromLookup(stack, id + "Zone",
                    HostedZoneProviderProps.builder().domainName(cfg.hostedZoneDomain).build());

            // Certificate
            Certificate cert = Certificate.Builder.create(stack, id + "Cert")
                    .domainName(cfg.fullDomainName)
                    .validation(CertificateValidation.fromDns(zone))
                    .build();
            IListenerCertificate Lcert = ListenerCertificate.fromCertificateManager(cert);

            // ALB + HTTPS listener
            alb = ApplicationLoadBalancer.Builder.create(stack, id + "Alb")
                    .vpc(vpc)
                    .internetFacing(true)
                    .securityGroup(albSg)
                    .build();

            ApplicationListener https = alb.addListener(id + "Https",
                    BaseApplicationListenerProps.builder()
                            .port(443)
                            .protocol(ApplicationProtocol.HTTPS)
                            .certificates(Arrays.asList(Lcert))
                            .build());

            // Target: service:8080
            https.addTargets(id + "Tg", AddApplicationTargetsProps.builder()
                    .port(8080)
                    .protocol(ApplicationProtocol.HTTP)
                    .targets(Arrays.asList(service))
                    .healthCheck(HealthCheck.builder()
                            .path("/login")
                            .healthyHttpCodes("200-399")
                            .interval(Duration.seconds(30))
                            .build())
                    .build());

            // HTTP → HTTPS redirect
            alb.addListener(id + "Http",
                            BaseApplicationListenerProps.builder().port(80).protocol(ApplicationProtocol.HTTP).build())
                    .addAction(id + "Redirect",
                            AddApplicationActionProps.builder()
                                    .action(ListenerAction.redirect(RedirectOptions.builder().protocol("HTTPS").port("443").build()))
                                    .build());

            CfnOutput.Builder.create(stack, "JenkinsUrl")
                    .description("Jenkins URL (ALB DNS)")
                    .value("https://" + alb.getLoadBalancerDnsName())
                    .build();
            // DNS record
            new ARecord(stack, id + "ARecord", ARecordProps.builder()
                    .zone(zone)
                    .recordName(cfg.fullDomainName.replace("." + cfg.hostedZoneDomain, ""))
                    .target(RecordTarget.fromAlias(new LoadBalancerTarget(alb)))
                    .build());

            // SG rule from ALB → service:8080
            svcSg.addIngressRule(albSg, Port.tcp(8080), null, false);
        } else {


            // ALB + HTTPS listener
            alb = ApplicationLoadBalancer.Builder.create(stack, id + "Alb")
                    .vpc(vpc)
                    .internetFacing(true)
                    .securityGroup(albSg)
                    .build();

            ApplicationListener http = alb.addListener(id + "Http",
                    BaseApplicationListenerProps.builder()
                            .port(80)
                            .protocol(ApplicationProtocol.HTTP)
                            .build());

            // Target: service:8080
            http.addTargets(id + "Tg", AddApplicationTargetsProps.builder()
                    .port(8080)
                    .protocol(ApplicationProtocol.HTTP)
                    .targets(Arrays.asList(service))
                    .healthCheck(HealthCheck.builder()
                            .path("/login")
                            .healthyHttpCodes("200-399")
                            .interval(Duration.seconds(30))
                            .build())
                    .build());

            // If no ALB, optionally expose port 8080 directly (dev/test)
            svcSg.addIngressRule(Peer.anyIpv4(), Port.tcp(8080), null, false);

            CfnOutput.Builder.create(stack, "JenkinsUrl")
                    .description("Jenkins URL (ALB DNS)")
                    .value("http://" + alb.getLoadBalancerDnsName())
                    .build();
        }



        return new Result(vpc, cluster, service, fs, alb);
    }
}
