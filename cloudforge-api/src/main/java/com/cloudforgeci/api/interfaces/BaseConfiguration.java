package com.cloudforgeci.api.interfaces;

import com.cloudforgeci.api.core.SystemContext;
import java.util.List;

public interface BaseConfiguration {
  List<Rule> rules(SystemContext c);
  void wire(SystemContext c);
  String id();
}
