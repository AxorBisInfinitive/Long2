package com.inf.long2.ints.int512;

import com.inf.long2.Binary;
import com.inf.long2.ints.Int512;
import com.inf.long2.ints.IntMathTestSupport;

class Int512MathTest extends IntMathTestSupport<Int512> {
    @Override
    protected Int512 parse(String value) {
        return Int512.parseInt(value);
    }

    @Override
    protected Int512 abs(Int512 value) {
        return Int512.abs(value);
    }

    @Override
    protected Int512 max(Int512 a, Int512 b) {
        return Int512.max(a, b);
    }

    @Override
    protected Int512 min(Int512 a, Int512 b) {
        return Int512.min(a, b);
    }

    @Override
    protected Int512 ofFloor(double value) {
        return Int512.ofFloor(value);
    }

    @Override
    protected Int512 ofFloor(Binary<?> value) {
        return Int512.ofFloor(value);
    }
}
