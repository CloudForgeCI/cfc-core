package com.cloudforgeci.api.interfaces;

import com.cloudforge.core.enums.RuntimeType;

public interface RuntimeConfiguration extends BaseConfiguration {

  RuntimeType kind();

}
