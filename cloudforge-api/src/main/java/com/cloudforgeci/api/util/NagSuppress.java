package com.cloudforgeci.api.util;

import io.github.cdklabs.cdknag.NagPackSuppression;

/** Shorthand for building cdk-nag suppressions. */
public final class NagSuppress {

    private NagSuppress() {
    }

    /** A suppression of rule {@code id}, with the justification {@code reason}. */
    public static NagPackSuppression of(String id, String reason) {
        return NagPackSuppression.builder().id(id).reason(reason).build();
    }
}
