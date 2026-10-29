package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.Stack;

import java.util.ArrayList;
import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.require;

/**
 * The rules and runtime dispatch shared by the IAM profiles. A profile supplies the roles for each
 * runtime.
 */
abstract class AbstractIAMConfiguration implements IAMConfiguration {

    @Override
    public String id() {
        return "iam:" + kind().name();
    }

    /** Whether the profile's roles depend on the EFS security group. */
    protected boolean requiresEfsSecurityGroup() {
        return true;
    }

    @Override
    public List<Rule> rules(SystemContext c) {
        List<Rule> rules = new ArrayList<>();
        rules.add(require("vpc", x -> x.vpc));

        // Instance security group is only required for EC2 runtime
        if (c.runtime == RuntimeType.EC2) {
            rules.add(require("instance security group", x -> x.instanceSg));
        }

        rules.add(require("alb security group", x -> x.albSg));
        if (requiresEfsSecurityGroup()) {
            rules.add(require("efs security group", x -> x.efsSg));
        }
        return rules;
    }

    @Override
    public void wire(SystemContext c) {
        if (c.runtime == RuntimeType.EC2) {
            createEc2Role(c);
        } else if (c.runtime == RuntimeType.FARGATE) {
            createFargateRoles(c);
        }
    }

    /** The account the stack deploys to. */
    protected static String accountId(SystemContext c) {
        return Stack.of(c).getAccount();
    }

    /** Log group path the roles may write to, narrowed to the stack when it has a name. */
    protected static String logGroupPattern(SystemContext c) {
        return hasStackName(c) ? "/aws/*/" + c.stackName + "*" : "/aws/*";
    }

    /** Backup bucket name pattern the roles may use, narrowed to the stack when it has a name. */
    protected static String backupBucketPattern(SystemContext c) {
        return hasStackName(c) ? c.stackName.toLowerCase() + "-backup-*" : "*-backup-*";
    }

    private static boolean hasStackName(SystemContext c) {
        return c.stackName != null && !c.stackName.isEmpty();
    }

    protected abstract void createEc2Role(SystemContext c);

    protected abstract void createFargateRoles(SystemContext c);
}
