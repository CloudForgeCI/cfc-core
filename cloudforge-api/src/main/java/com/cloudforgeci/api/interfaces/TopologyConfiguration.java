package com.cloudforgeci.api.interfaces;

import com.cloudforge.core.enums.TopologyType;

public interface TopologyConfiguration extends BaseConfiguration {
  TopologyType kind();
}
