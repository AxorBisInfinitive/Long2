package com.inf.long2;

import com.inf.long2.util.annotations.HaveToImplements;
import com.inf.long2.util.annotations.StaticMethodImplementsCheck;

@StaticMethodImplementsCheck
public interface Int<T extends Int<T>> {
    public static Int<?> of(long last) {
        return null;
    }

    @HaveToImplements
    public String toString();
}
