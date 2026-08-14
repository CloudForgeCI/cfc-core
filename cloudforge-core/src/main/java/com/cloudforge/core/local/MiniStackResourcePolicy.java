package com.cloudforge.core.local;

/**
 * How MiniStack treats a CloudFormation resource type.
 */
public enum MiniStackResourcePolicy {
    /** Deployed without adaptation. */
    SUPPORTED,
    /** Removed or rewritten by {@code MiniStackTemplateAdapter}. */
    ADAPTED,
    /** Not supported — deploy will fail at CFN create. */
    UNSUPPORTED
}
