package com.cloudforgeci.api.core;

import com.cloudforgeci.api.interfaces.BaseSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class Slot<T> implements BaseSlot<T> {
    private T value;
    private final List<Consumer<T>> waiters = new ArrayList<>();

    @Override
    public void set(T v) {
        if (value != null) return;
        value = v;
        for (var w : List.copyOf(waiters)) w.accept(v);
        waiters.clear();
    }
    @Override
    public Optional<T> get() { return Optional.ofNullable(value); }

    @Override
    public void onSet(java.util.function.Consumer<T> c) { if (value != null) c.accept(value); else waiters.add(c); }
}