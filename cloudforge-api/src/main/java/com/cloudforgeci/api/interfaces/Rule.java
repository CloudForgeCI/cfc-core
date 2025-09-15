package com.cloudforgeci.api.interfaces;

import com.cloudforgeci.api.core.SystemContext;
import java.util.List;

@FunctionalInterface
public interface Rule {
  List<String> check(SystemContext c);
}