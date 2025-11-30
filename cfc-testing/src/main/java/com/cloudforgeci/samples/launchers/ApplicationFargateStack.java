package com.cloudforgeci.samples.launchers;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

/**
 * Universal Application Fargate Stack for CloudForge 3.0.0.
 *
 * <p>This stack creates any application deployment running on AWS Fargate with:</p>
 * <ul>
 *   <li>Fargate service with auto-scaling</li>
 *   <li>Application Load Balancer with SSL termination</li>
 *   <li>EFS for persistent storage (always EFS with Fargate)</li>
 *   <li>Full SOC2/PCI-DSS/HIPAA/GDPR compliance when securityProfile=PRODUCTION</li>
 *   <li>CloudWatch logging and monitoring</li>
 * </ul>
 *
 * <p><strong>Supported Applications:</strong></p>
 * <ul>
 *   <li>CI/CD: Jenkins, GitLab, Drone, Gitea</li>
 *   <li>Monitoring: Grafana, Prometheus</li>
 *   <li>Databases: PostgreSQL, Redis</li>
 *   <li>Secrets Management: HashiCorp Vault</li>
 *   <li>Artifact Registry: Nexus, Harbor</li>
 *   <li>Collaboration: Mattermost</li>
 *   <li>Analytics: Metabase, Apache Superset</li>
 * </ul>
 *
 * @author CloudForgeCI
 * @since 3.0.0
 */
public class ApplicationFargateStack extends Stack {

    public ApplicationFargateStack(final Construct scope, final String id) {
        this(scope, id, null, SecurityProfile.DEV, IAMProfile.EXTENDED, null);
    }

    public ApplicationFargateStack(final Construct scope, final String id, final StackProps props) {
        this(scope, id, props, SecurityProfile.DEV, IAMProfile.EXTENDED, null);
    }

    public ApplicationFargateStack(final Construct scope, final String id, final StackProps props,
                                   final SecurityProfile security, final IAMProfile iamProfile,
                                   final ApplicationSpec applicationSpec) {
        super(scope, id, props);

        if (applicationSpec == null) {
            throw new IllegalArgumentException("ApplicationSpec cannot be null");
        }

        System.out.println("Creating Universal Application Fargate stack:");
        System.out.println("  Application: " + applicationSpec.applicationId());
        System.out.println("  Security Profile: " + security);
        System.out.println("  IAM Profile: " + iamProfile);

        DeploymentContext cfc = DeploymentContext.from(scope);

        // Use ApplicationFactory with ApplicationSpec pattern
        ApplicationFactory.createFargate(this, id, cfc, security, iamProfile, applicationSpec);

        System.out.println("Universal Application Fargate deployment created successfully");
    }
}
