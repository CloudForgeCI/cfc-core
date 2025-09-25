package com.cloudforgeci.api.test;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.compute.Ec2Factory;
import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.storage.ContainerFactory;
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.observability.AlarmFactory;
import com.cloudforgeci.api.scaling.ScalingFactory;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.iam.Role;
import java.util.HashMap;
import java.util.Map;

/**
 * Test infrastructure builder that creates a complete infrastructure setup
 * for testing purposes, ensuring all validation requirements are met.
 */
public class TestInfrastructureBuilder {
    
    private final App app;
    private final Stack stack;
    private DeploymentContext cfc;
    private SystemContext ctx;
    
    public TestInfrastructureBuilder(String stackName, SecurityProfile securityProfile, RuntimeType runtimeType) {
        this.app = new App();
        this.stack = new Stack(app, stackName);
        
        // Set required context values for testing BEFORE creating DeploymentContext
        Map<String, Object> cfcContext = new HashMap<>();
        // Don't set domain to avoid hosted zone requirement by default
        // cfcContext.put("domain", "test.example.com");
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);
        
        this.cfc = DeploymentContext.from(stack);
        
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(securityProfile);
        // Use JENKINS_SERVICE topology for both FARGATE and EC2 runtime
        TopologyType topology = TopologyType.JENKINS_SERVICE;
        this.ctx = SystemContext.start(stack, topology, runtimeType, securityProfile, iamProfile, cfc);
    }
    
    public TestInfrastructureBuilder(String stackName, SecurityProfile securityProfile, RuntimeType runtimeType, String domainName) {
        this.app = new App();
        this.stack = new Stack(app, stackName);
        
        // Set required context values for testing BEFORE creating DeploymentContext
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("domain", domainName);
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);
        
        this.cfc = DeploymentContext.from(stack);
        
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(securityProfile);
        // Use JENKINS_SERVICE topology for both FARGATE and EC2 runtime
        TopologyType topology = TopologyType.JENKINS_SERVICE;
        this.ctx = SystemContext.start(stack, topology, runtimeType, securityProfile, iamProfile, cfc);
    }
    
    public TestInfrastructureBuilder createVpc() {
        VpcFactory vpcFactory = new VpcFactory(stack, "Vpc");
        vpcFactory.injectContexts(); // Manual injection after SystemContext.start()
        vpcFactory.create();
        return this;
    }
    
    public TestInfrastructureBuilder createAlb() {
        AlbFactory albFactory = new AlbFactory(stack, "Alb");
        albFactory.injectContexts(); // Manual injection after SystemContext.start()
        albFactory.create();
        return this;
    }
    
    public TestInfrastructureBuilder createEfs() {
        EfsFactory efsFactory = new EfsFactory(stack, "Efs");
        efsFactory.injectContexts(); // Manual injection after SystemContext.start()
        efsFactory.create();
        return this;
    }
    
    public TestInfrastructureBuilder createEc2() {
        Ec2Factory ec2Factory = new Ec2Factory(stack, "Ec2");
        ec2Factory.injectContexts(); // Manual injection after SystemContext.start()
        ec2Factory.create();
        return this;
    }
    
    public TestInfrastructureBuilder createMockInstanceSecurityGroup() {
        // Create a mock instance security group for FARGATE runtime to satisfy security wiring
        SecurityGroup mockInstanceSg = SecurityGroup.Builder.create(stack, "MockInstanceSg")
                .vpc(ctx.vpc.get().orElseThrow())
                .description("Mock Instance Security Group for FARGATE")
                .allowAllOutbound(true)
                .build();
        ctx.instanceSg.set(mockInstanceSg);
        return this;
    }
    
    public TestInfrastructureBuilder createMockEfsSecurityGroup() {
        // Create a mock EFS security group for EC2 runtime to satisfy validation
        SecurityGroup mockEfsSg = SecurityGroup.Builder.create(stack, "MockEfsSg")
                .vpc(ctx.vpc.get().orElseThrow())
                .description("Mock EFS Security Group for EC2")
                .allowAllOutbound(true)
                .build();
        ctx.efsSg.set(mockEfsSg);
        return this;
    }
    
    public TestInfrastructureBuilder createMockAsg() {
        // Create a mock ASG for EC2 runtime to satisfy validation
        AutoScalingGroup mockAsg = AutoScalingGroup.Builder.create(stack, "MockAsg")
                .vpc(ctx.vpc.get().orElseThrow())
                .instanceType(software.amazon.awscdk.services.ec2.InstanceType.of(
                        software.amazon.awscdk.services.ec2.InstanceClass.T3,
                        software.amazon.awscdk.services.ec2.InstanceSize.MICRO))
                .machineImage(software.amazon.awscdk.services.ec2.MachineImage.latestAmazonLinux())
                .minCapacity(1)
                .maxCapacity(1)
                .desiredCapacity(1)
                .build();
        ctx.asg.set(mockAsg);
        return this;
    }
    
    public TestInfrastructureBuilder createFargate() {
        // Create Fargate factory (which will create the container internally)
        FargateFactory fargateFactory = new FargateFactory(stack, "Fargate", new FargateFactory.Props(cfc));
        fargateFactory.injectContexts(); // Manual injection after SystemContext.start()
        fargateFactory.create(); // Call create() to populate fargateTaskDef and fargateService slots
        return this;
    }
    
    public TestInfrastructureBuilder createDomain() {
        DomainFactory domainFactory = new DomainFactory(stack, "Domain");
        domainFactory.injectContexts(); // Manual injection after SystemContext.start()
        domainFactory.create();
        return this;
    }
    
    public TestInfrastructureBuilder createAlarms() {
        AlarmFactory alarmFactory = new AlarmFactory(stack, "Alarms", null);
        return this;
    }
    
    public TestInfrastructureBuilder createScaling() {
        ScalingFactory scalingFactory = new ScalingFactory(stack, "Scaling");
        return this;
    }
    
    public TestInfrastructureBuilder createCompleteInfrastructure() {
        if (ctx.runtime == RuntimeType.FARGATE) {
            // For FARGATE runtime with JENKINS_SERVICE topology, create EFS
            return createVpc()
                    .createAlb()
                    .createEfs()
                    .createFargate()
                    // Skip domain creation for now due to AWS environment requirements
                    // .createDomain()
                    .createAlarms()
                    .createScaling();
        } else {
            // For EC2 runtime with JENKINS_SERVICE topology, create EC2 resources
            return createVpc()
                    .createAlb()
                    .createEc2()                         // Create EC2 with ASG first (creates instance security group)
                    .createEfs()                         // EFS is allowed for JENKINS_SERVICE
                    // Skip Fargate creation for EC2 runtime
                    // Skip domain creation for now due to AWS environment requirements
                    // .createDomain()
                    .createAlarms()
                    .createScaling();
        }
    }
    
    public TestInfrastructureBuilder createMinimalInfrastructure() {
        if (ctx.runtime == RuntimeType.FARGATE) {
            return createVpc()
                    .createAlb()
                    .createEfs()
                    .createFargate();  // Required for FARGATE runtime
        } else {
            return createVpc()
                    .createAlb()
                    .createEc2()  // Create EC2 before EFS so instance security group is available
                    .createEfs();
        }
    }
    
    public Stack getStack() {
        return stack;
    }
    
    public SystemContext getSystemContext() {
        return ctx;
    }
    
    public DeploymentContext getDeploymentContext() {
        return cfc;
    }
    
    public DeploymentContext getCfc() {
        return cfc;
    }
    
    public App getApp() {
        return app;
    }
    
    // Helper methods to access created resources
    public Vpc getVpc() {
        return ctx.vpc.get().orElseThrow();
    }
    
    public SecurityGroup getAlbSecurityGroup() {
        return ctx.albSg.get().orElseThrow();
    }
    
    public SecurityGroup getEfsSecurityGroup() {
        return ctx.efsSg.get().orElseThrow();
    }
    
    public SecurityGroup getInstanceSecurityGroup() {
        if (ctx.runtime == RuntimeType.FARGATE) {
            throw new UnsupportedOperationException("Instance security group not available for Fargate runtime");
        }
        return ctx.instanceSg.get().orElseThrow();
    }
    
    public RuntimeType getRuntime() {
        return ctx.runtime;
    }
    
    public ApplicationLoadBalancer getAlb() {
        return ctx.alb.get().orElseThrow();
    }
    
    public ApplicationListener getHttpListener() {
        return ctx.http.get().orElseThrow();
    }
    
    public FileSystem getEfs() {
        return ctx.efs.get().orElseThrow();
    }
    
    public AccessPoint getAccessPoint() {
        return ctx.ap.get().orElseThrow();
    }
    
    public AutoScalingGroup getAsg() {
        return ctx.asg.get().orElseThrow();
    }
    
    public FargateService getFargateService() {
        return ctx.fargateService.get().orElseThrow();
    }
    
    public TaskDefinition getTaskDefinition() {
        return ctx.fargateTaskDef.get().orElseThrow();
    }
    
    public ContainerDefinition getContainer() {
        return ctx.container.get().orElseThrow();
    }
    
    public IHostedZone getHostedZone() {
        return ctx.zone.get().orElseThrow();
    }
    
    public LogGroup getLogGroup() {
        return ctx.logs.get().orElseThrow();
    }
    
    public Role getEc2InstanceRole() {
        return ctx.ec2InstanceRole.get().orElseThrow();
    }
    
    public Role getFargateExecutionRole() {
        return ctx.fargateExecutionRole.get().orElseThrow();
    }
    
    public Role getFargateTaskRole() {
        return ctx.fargateTaskRole.get().orElseThrow();
    }
}
