package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.ONE;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.assertRaw;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.dyadic;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640DivisionTest {
    private static final long QMIN = -8389221L;

    private static void check(Binary640 a, Binary640 b) {
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        assertRaw(Binary640Reference.divide(a, b), a.divide(b));
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    @Test
    void checksEveryTailLengthOnEachSideOfTheDivision() {
        Random random = new Random(0x640D101L);
        for (int zeros = 0; zeros <= 615; zeros++) {
            BigInteger normal = new BigInteger(616 - zeros, random).setBit(615 - zeros).setBit(0).shiftLeft(zeros);
            BigInteger other = new BigInteger(616, random).setBit(615).setBit(0);
            Binary640 a = dyadic((zeros & 1) != 0, normal, -615);
            Binary640 b = dyadic((zeros & 2) != 0, other, -616);
            check(a, b);
            check(b, a);
            check(a, dyadic(false, ONE.shiftLeft((zeros * 29) % 616), -615));
            if (zeros < 615) {
                Binary640 subnormal = dyadic(true, normal.clearBit(615), QMIN);
                check(subnormal, b);
                check(b, subnormal);
                check(subnormal, Binary640.MIN_VALUE);
                check(Binary640.MIN_VALUE, subnormal);
            }
        }
    }

    @Test
    void preservesRoundingAtTheUnderflowAndNormalBoundaries() {
        Binary640 two = Binary640.of(2L);
        Binary640 smallest = Binary640.MIN_VALUE;
        assertRaw(smallest, smallest.divide(two.nextDown()));
        assertRaw(Binary640.ZERO, smallest.divide(two));
        assertRaw(Binary640.NEGATIVE_ZERO, smallest.negate().divide(two));
        assertRaw(Binary640.ZERO, smallest.divide(two.nextUp()));
        assertRaw(smallest.scalb(1), dyadic(false, BigInteger.valueOf(3), QMIN).divide(two));

        Binary640 midpoint = Binary640.MIN_NORMAL.scalb(1).nextDown();
        assertRaw(Binary640.MIN_NORMAL.nextDown(), midpoint.nextDown().divide(two));
        assertRaw(Binary640.MIN_NORMAL, midpoint.divide(two));
        assertRaw(Binary640.MIN_NORMAL, midpoint.nextUp().divide(two));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.MAX_VALUE.divide(Binary640.ONE.nextDown()));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.MAX_VALUE.divide(Binary640.ONE.nextDown().negate()));
    }

    @Test
    void checksShortOddDivisorsAcrossTheUnsignedDigitBoundary() {
        BigInteger magnitude = ONE.shiftLeft(616).subtract(ONE);
        for (long digit : new long[] {1, 3, 0x7fff_ffffL, 0x8000_0001L, 0xffff_fffdL, 0xffff_ffffL, 0x1_0000_0001L}) {
            BigInteger d = BigInteger.valueOf(digit);
            for (int exponent : new int[] {-1, 0, 1, 32}) {
                Binary640 b = dyadic(false, d, exponent - (long) d.bitLength() + 1);
                for (Binary640 a : new Binary640[] {Binary640.MIN_VALUE, Binary640.MIN_NORMAL.nextDown(),
                        Binary640.MIN_NORMAL, dyadic(false, magnitude, -615), Binary640.MAX_VALUE}) {
                    check(a, b);
                    check(a.negate(), b);
                }
            }
            check(dyadic(false, magnitude, QMIN), dyadic(false, d, QMIN));
        }
    }
}
