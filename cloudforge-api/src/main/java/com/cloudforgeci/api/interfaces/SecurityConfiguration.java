package com.cloudforgeci.api.interfaces;

import com.cloudforge.core.enums.SecurityProfile;

public interface SecurityConfiguration extends BaseConfiguration {
    SecurityProfile kind();
}
