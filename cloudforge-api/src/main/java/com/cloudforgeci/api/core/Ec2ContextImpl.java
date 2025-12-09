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

    public Ec2ContextImpl(String stackName, String runtimeType, String securityProfile,
                          boolean hasEfs, String efsId, String accessPointId) {
        this.stackName = stackName;
        this.runtimeType = runtimeType;
        this.securityProfile = securityProfile;
        this.hasEfs = hasEfs;
        this.efsId = efsId;
        this.accessPointId = accessPointId;
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
}
