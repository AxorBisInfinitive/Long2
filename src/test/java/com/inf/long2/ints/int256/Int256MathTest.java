package com.inf.long2.ints.int256;

import com.inf.long2.Binary;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.IntMathTestSupport;

class Int256MathTest extends IntMathTestSupport<Int256> {
    @Override
    protected Int256 parse(String value) {
        return Int256.parseInt(value);
    }

    @Override
    protected Int256 abs(Int256 value) {
        return Int256.abs(value);
    }

    @Override
    protected Int256 max(Int256 a, Int256 b) {
        return Int256.max(a, b);
    }

    @Override
    protected Int256 min(Int256 a, Int256 b) {
        return Int256.min(a, b);
    }

    @Override
    protected Int256 ofFloor(double value) {
        return Int256.ofFloor(value);
    }

    @Override
    protected Int256 ofFloor(Binary<?> value) {
        return Int256.ofFloor(value);
    }
}
