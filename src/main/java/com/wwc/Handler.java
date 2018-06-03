package com.wwc;


@FunctionalInterface
public interface Handler<T> {
    void handleEvent(T k);
}
