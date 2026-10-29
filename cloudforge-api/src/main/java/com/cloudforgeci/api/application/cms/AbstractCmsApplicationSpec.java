package com.cloudforgeci.api.application.cms;

import com.cloudforge.core.annotation.CmsPlugin;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * Values and EC2 user data steps shared by the built-in PHP CMS specs. A spec overrides a method
 * only where it differs.
 */
public abstract class AbstractCmsApplicationSpec implements CmsSpec {

    private CmsPlugin plugin() {
        return getClass().getAnnotation(CmsPlugin.class);
    }

    @Override
    public String applicationId() {
        return plugin().value();
    }

    @Override
    public String phpVersion() {
        return plugin().phpVersion();
    }

    @Override
    public String cmsCategory() {
        return plugin().category();
    }

    @Override
    public boolean supportsMultisite() {
        return plugin().supportsMultisite();
    }

    @Override
    public String containerDataPath() {
        return "/var/www/html";
    }

    @Override
    public String efsDataPath() {
        return "/" + applicationId();
    }

    @Override
    public String volumeName() {
        return applicationId() + "Data";
    }

    @Override
    public String containerUser() {
        return "33:33";
    }

    @Override
    public String efsPermissions() {
        return "755";
    }

    @Override
    public int applicationPort() {
        return 80;
    }

    @Override
    public boolean useSystemCron() {
        return true;
    }

    @Override
    public boolean hasScheduledTasks() {
        return true;
    }

    @Override
    public boolean supportsObjectCache() {
        return true;
    }

    @Override
    public String preferredCacheBackend() {
        return "redis";
    }

    @Override
    public boolean supportsCdnIntegration() {
        return true;
    }

    @Override
    public boolean supportsS3MediaStorage() {
        return true;
    }

    @Override
    public int phpUploadMaxFilesize() {
        return 64;
    }

    @Override
    public int phpPostMaxSize() {
        return 64;
    }

    @Override
    public String ec2DataPath() {
        return "/var/www/html";
    }

    @Override
    public String ebsDeviceName() {
        return "/dev/xvdh";
    }

    @Override
    public List<String> getSupportedAuthModes() {
        return List.of("alb-oidc", "none");
    }

    /** Installs Apache with TLS support and enables it at boot. */
    protected final void installApache(UserDataBuilder builder) {
        builder.addCommands(
            "# Install Apache",
            "dnf install -y httpd mod_ssl",
            "systemctl enable httpd",
            "echo 'Apache installed' >> /var/log/userdata.log"
        );
    }

    /** Mounts the application's data path from EFS when the stack has one, otherwise from EBS. */
    protected final void mountStorage(UserDataBuilder builder, Ec2Context context) {
        String[] userParts = containerUser().split(":");
        if (context.hasEfs()) {
            builder.mountEfs(
                context.efsId().orElseThrow(),
                context.accessPointId().orElseThrow(),
                ec2DataPath(),
                userParts[0], userParts[1]
            );
        } else {
            builder.mountEbs(ebsDeviceName(), ec2DataPath(), userParts[0], userParts[1]);
        }
    }

    /** Ships the application's log files to CloudWatch. */
    protected final void installLogAgent(UserDataBuilder builder, Ec2Context context) {
        String logGroupName = String.format("/cloudforge/%s/%s",
            context.stackName(), applicationId());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());
    }
}
