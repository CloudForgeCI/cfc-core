package com.cloudforgeci.api.interfaces;

import java.util.Optional;

public interface BaseSlot<T> {

    void set(T v);
    Optional<T> get();
    void onSet(java.util.function.Consumer<T> c);
}
