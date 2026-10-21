package com.inf.long2;

@StaticMethodImplementsCheck
public interface Int<T extends Int<T>> {
    public static Int<?> of(long last) {
        return null;
    }
}
