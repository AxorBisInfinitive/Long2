package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640AddSubtractCombinedTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(615);
    private static final BigInteger MASK = HIDDEN.subtract(ONE);

    private static void check(Binary640 a, Binary640 b) {
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        for (Binary640 x : List.of(a, a.negate())) {
            for (Binary640 y : List.of(b, b.negate())) {
                assertRaw(Binary640Reference.add(x, y), x.add(y));
                assertRaw(Binary640Reference.add(x, y.negate()), x.subtract(y));
                assertRaw(Binary640Reference.add(y, x), y.add(x));
                assertRaw(Binary640Reference.add(y, x.negate()), y.subtract(x));
            }
        }
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    @Test
    void checksMixedInputsAtBothEncodedAndActualFarThresholds() {
        for (int width = 1; width <= 615; width++) {
            for (BigInteger magnitude : List.of(ONE.shiftLeft(width - 1), ONE.shiftLeft(width).subtract(ONE))) {
                Binary640 subnormal = Binary640Reference.fromRaw(magnitude);
                for (int code : new int[] {1, 2, 615, 616, 617, 618, 619}) {
                    for (BigInteger fraction : List.of(BigInteger.ZERO, MASK)) {
                        Binary640 normal = Binary640Reference.fromRaw(BigInteger.valueOf(code).shiftLeft(615).or(fraction));
                        check(normal, subnormal);
                    }
                }
            }
        }
    }

    @Test
    void keepsTheSharedQuantumAtTheNormalSubnormalBoundary() {
        Binary640 maximumSubnormal = Binary640.MIN_NORMAL.nextDown();
        assertRaw(Binary640.MIN_VALUE, Binary640.MIN_NORMAL.subtract(maximumSubnormal));
        assertRaw(maximumSubnormal, Binary640.MIN_NORMAL.subtract(Binary640.MIN_VALUE));
        assertRaw(Binary640.MIN_NORMAL, maximumSubnormal.add(Binary640.MIN_VALUE));
        Binary640 twiceMinimumNormal = Binary640.MIN_NORMAL.scalb(1);
        assertRaw(Binary640.MIN_NORMAL.nextUp(), twiceMinimumNormal.subtract(maximumSubnormal));
        check(Binary640.MIN_NORMAL, maximumSubnormal);
        check(Binary640.MIN_NORMAL, Binary640.MIN_VALUE);
        check(twiceMinimumNormal, maximumSubnormal);
    }

    @Test
    void keepsThe617RoundingBoundaryAndEffectiveSignForEitherDominantOperand() {
        Binary640 half = Binary640.ONE.scalb(-617);
        assertRaw(Binary640.ONE, Binary640.ONE.subtract(half));
        assertRaw(Binary640.ONE.nextDown(), Binary640.ONE.subtract(half.nextUp()));
        for (Binary640 b : List.of(half.nextDown(), half, half.nextUp(), half.scalb(-1))) {
            check(Binary640.ONE, b);
        }
        for (Binary640 dominant : List.of(Binary640.MAX_VALUE, Binary640.MAX_VALUE.negate())) {
            for (Binary640 small : List.of(Binary640.MIN_VALUE, Binary640.MIN_VALUE.negate())) {
                byte[] beforeDominant = dominant.toBytes(), beforeSmall = small.toBytes();
                assertRaw(dominant, dominant.add(small));
                assertRaw(dominant, dominant.subtract(small));
                assertRaw(dominant, small.add(dominant));
                assertRaw(dominant.negate(), small.subtract(dominant));
                assertArrayEquals(beforeDominant, dominant.toBytes());
                assertArrayEquals(beforeSmall, small.toBytes());
            }
        }
    }

    @Test
    void handlesSpecialValuesAndSignedZerosBeforeExponentDispatch() {
        BigInteger infinity = BigInteger.valueOf(0xffffff).shiftLeft(615);
        for (BigInteger payload : List.of(ONE, ONE.shiftLeft(614), MASK)) {
            for (BigInteger sign : List.of(BigInteger.ZERO, ONE.shiftLeft(639))) {
                Binary640 nan = Binary640Reference.fromRaw(infinity.or(payload).or(sign));
                for (Binary640 b : List.of(Binary640.MIN_VALUE, Binary640.MAX_VALUE, Binary640.POSITIVE_INFINITY,
                        Binary640.NEGATIVE_INFINITY, Binary640.ZERO, Binary640.NEGATIVE_ZERO)) {
                    assertRaw(Binary640.NaN, nan.add(b));
                    assertRaw(Binary640.NaN, nan.subtract(b));
                    assertRaw(Binary640.NaN, b.add(nan));
                    assertRaw(Binary640.NaN, b.subtract(nan));
                }
            }
        }
        assertRaw(Binary640.NaN, Binary640.POSITIVE_INFINITY.add(Binary640.NEGATIVE_INFINITY));
        assertRaw(Binary640.NaN, Binary640.POSITIVE_INFINITY.subtract(Binary640.POSITIVE_INFINITY));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.MIN_VALUE.subtract(Binary640.NEGATIVE_INFINITY));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.NEGATIVE_INFINITY.subtract(Binary640.MAX_VALUE));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.NEGATIVE_ZERO.add(Binary640.NEGATIVE_ZERO));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.NEGATIVE_ZERO.subtract(Binary640.ZERO));
        assertRaw(Binary640.ZERO, Binary640.NEGATIVE_ZERO.subtract(Binary640.NEGATIVE_ZERO));
        assertRaw(Binary640.ZERO, Binary640.ZERO.add(Binary640.NEGATIVE_ZERO));
    }
}
