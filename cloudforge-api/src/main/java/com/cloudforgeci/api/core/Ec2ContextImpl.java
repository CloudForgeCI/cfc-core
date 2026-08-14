package com.cloudforgeci.api.core;

import com.cloudforge.core.interfaces.Ec2Context;

import java.util.Optional;

/**
 * Implementation of Ec2Context providing runtime information for UserData configuration.
 */
public class Ec2ContextImpl implements Ec2Context {
    private final String stackName;
    private final String runtimeType;
    private final String securityProfile;
    private final boolean hasEfs;
    private final String efsId;
    private final String accessPointId;
    private final String authMode;
    private final String fqdn;
    private final boolean sslEnabled;

    public Ec2ContextImpl(String stackName, String runtimeType, String securityProfile,
                          boolean hasEfs, String efsId, String accessPointId) {
        this(stackName, runtimeType, securityProfile, hasEfs, efsId, accessPointId,
            "none", null, false);
    }

    public Ec2ContextImpl(String stackName, String runtimeType, String securityProfile,
                          boolean hasEfs, String efsId, String accessPointId,
                          String authMode, String fqdn, boolean sslEnabled) {
        this.stackName = stackName;
        this.runtimeType = runtimeType;
        this.securityProfile = securityProfile;
        this.hasEfs = hasEfs;
        this.efsId = efsId;
        this.accessPointId = accessPointId;
        this.authMode = authMode == null || authMode.isBlank() ? "none" : authMode;
        this.fqdn = fqdn;
        this.sslEnabled = sslEnabled;
    }

    @Override
    public String stackName() {
        return stackName;
    }

    @Override
    public String runtimeType() {
        return runtimeType;
    }

    @Override
    public String securityProfile() {
        return securityProfile;
    }

    @Override
    public boolean hasEfs() {
        return hasEfs;
    }

    @Override
    public Optional<String> efsId() {
        return Optional.ofNullable(efsId);
    }

    @Override
    public Optional<String> accessPointId() {
        return Optional.ofNullable(accessPointId);
    }

    @Override
    public String authMode() {
        return authMode;
    }

    @Override
    public String fqdn() {
        return fqdn;
    }

    @Override
    public boolean sslEnabled() {
        return sslEnabled;
    }
}
