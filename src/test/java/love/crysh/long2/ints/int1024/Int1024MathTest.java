package love.crysh.long2.ints.int1024;

import love.crysh.long2.Binary;
import love.crysh.long2.ints.Int1024;
import love.crysh.long2.ints.IntMathTestSupport;

class Int1024MathTest extends IntMathTestSupport<Int1024> {
    @Override
    protected Int1024 parse(String value) {
        return Int1024.parseInt(value);
    }

    @Override
    protected Int1024 abs(Int1024 value) {
        return Int1024.abs(value);
    }

    @Override
    protected Int1024 max(Int1024 a, Int1024 b) {
        return Int1024.max(a, b);
    }

    @Override
    protected Int1024 min(Int1024 a, Int1024 b) {
        return Int1024.min(a, b);
    }

    @Override
    protected Int1024 ofFloor(double value) {
        return Int1024.ofFloor(value);
    }

    @Override
    protected Int1024 ofFloor(Binary<?> value) {
        return Int1024.ofFloor(value);
    }
}
