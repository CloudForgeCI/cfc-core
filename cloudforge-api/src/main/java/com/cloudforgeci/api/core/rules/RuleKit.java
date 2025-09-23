package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.Slot;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.Rule;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class RuleKit {
  private RuleKit() {}

  public static Rule require(String name, Function<SystemContext, ? extends Slot<?>> get) {
    return c -> get.apply(c).get().isPresent() ? List.of() : List.of("required: " + name);
  }

  public static Rule forbid(String name, Function<SystemContext, ? extends Slot<?>> get) {
    return c -> get.apply(c).get().isEmpty() ? List.of() : List.of("forbidden: " + name);
  }

  public static Rule when(boolean cond, Rule r) { return cond ? r : c -> List.of(); }

  // --- combinators ---
  public static <A,B> void whenBoth(Slot<A> a, Slot<B> b, BiConsumer<A,B> fn) {
    Runnable tryRun = () -> {
      var ao = a.get(); var bo = b.get();
      if (ao.isPresent() && bo.isPresent()) fn.accept(ao.get(), bo.get());
    };
    a.onSet(x -> tryRun.run()); b.onSet(y -> tryRun.run()); tryRun.run();
  }

  @FunctionalInterface public interface TriConsumer<A,B,C> { void accept(A a,B b,C c); }
  @FunctionalInterface public interface QuadConsumer<A,B,C,D> { void accept(A a,B b,C c,D d); }
  @FunctionalInterface public interface PentaConsumer<A,B,C,D,E> { void accept(A a,B b,C c,D d,E e); }

  public static <A,B,C> void whenAll(Slot<A> a, Slot<B> b, Slot<C> c, TriConsumer<A,B,C> fn) {
    Runnable tryRun = () -> {
      var ao = a.get(); var bo = b.get(); var co = c.get();
      if (ao.isPresent() && bo.isPresent() && co.isPresent()) fn.accept(ao.get(), bo.get(), co.get());
    };
    a.onSet(x -> tryRun.run()); b.onSet(x -> tryRun.run()); c.onSet(x -> tryRun.run()); tryRun.run();
  }

  public static <A,B,C,D> void whenAll4(Slot<A> a, Slot<B> b, Slot<C> c, Slot<D> d, QuadConsumer<A,B,C,D> fn) {
    Runnable tryRun = () -> {
      var ao = a.get(); var bo = b.get(); var co = c.get(); var do_ = d.get();
      if (ao.isPresent() && bo.isPresent() && co.isPresent() && do_.isPresent()) 
        fn.accept(ao.get(), bo.get(), co.get(), do_.get());
    };
    a.onSet(x -> tryRun.run()); b.onSet(x -> tryRun.run()); c.onSet(x -> tryRun.run()); d.onSet(x -> tryRun.run()); tryRun.run();
  }

  public static <A,B,C,D,E> void whenAll5(Slot<A> a, Slot<B> b, Slot<C> c, Slot<D> d, Slot<E> e, PentaConsumer<A,B,C,D,E> fn) {
    Runnable tryRun = () -> {
      var ao = a.get(); var bo = b.get(); var co = c.get(); var do_ = d.get(); var eo = e.get();
      if (ao.isPresent() && bo.isPresent() && co.isPresent() && do_.isPresent() && eo.isPresent()) 
        fn.accept(ao.get(), bo.get(), co.get(), do_.get(), eo.get());
    };
    a.onSet(x -> tryRun.run()); b.onSet(x -> tryRun.run()); c.onSet(x -> tryRun.run()); 
    d.onSet(x -> tryRun.run()); e.onSet(x -> tryRun.run()); tryRun.run();
  }
}