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
 * Universal Application EC2 Stack for CloudForge 3.0.0.
 *
 * <p>This stack creates any application deployment running on AWS EC2 with:</p>
 * <ul>
 *   <li>EC2 instance(s) with auto-scaling or single instance</li>
 *   <li>Application Load Balancer with SSL termination</li>
 *   <li>EFS or EBS for persistent storage</li>
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
public class ApplicationEc2Stack extends Stack {

    // Note: Convenience constructors removed in CloudForge 3.0.0
    // ApplicationSpec is required for all deployments. Use the full constructor
    // or CloudForgeCommunitySample which loads applicationSpec from deployment context.

    public ApplicationEc2Stack(final Construct scope, final String id, final StackProps props,
                              final SecurityProfile security, final IAMProfile iamProfile,
                              final ApplicationSpec applicationSpec) {
        super(scope, id, props);

        if (applicationSpec == null) {
            throw new IllegalArgumentException("ApplicationSpec cannot be null. " +
                "Use ApplicationLoader.findById(\"appName\") to get a valid ApplicationSpec.");
        }

        System.out.println("Creating Universal Application EC2 stack:");
        System.out.println("  Application: " + applicationSpec.applicationId());
        System.out.println("  Security Profile: " + security);
        System.out.println("  IAM Profile: " + iamProfile);

        DeploymentContext cfc = DeploymentContext.from(scope);

        // Use ApplicationFactory with ApplicationSpec pattern
        ApplicationFactory.createEc2(this, id, cfc, security, iamProfile, applicationSpec);

        System.out.println("Universal Application EC2 deployment created successfully");
    }
}
