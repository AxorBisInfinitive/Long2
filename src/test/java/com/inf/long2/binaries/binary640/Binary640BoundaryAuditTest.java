package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640BoundaryAuditTest {
    @Test
    void preservesForeignStickyBelowTheMinimumValueMidpoint() {
        for (int precision : new int[] {617, 618, 619, 639, 640, 641, 1024, 4096}) {
            BigInteger midpoint = ONE.shiftLeft(precision - 1);
            long scale = -8389221L - precision;
            for (int bit : new int[] {0, 1, 38, 39, 63, 64, 65, precision - 2}) {
                for (int direction : new int[] {-1, 0, 1}) {
                    BigInteger magnitude = midpoint.add(ONE.shiftLeft(bit).multiply(BigInteger.valueOf(direction)));
                    for (boolean negative : new boolean[] {false, true}) {
                        assertRaw(dyadic(negative, magnitude, scale), Binary640.of(source(negative, magnitude, scale)));
                    }
                }
            }
        }
    }

    @Test
    void preservesForeignMidpointTailsAtTheFiniteOverflowThreshold() {
        BigInteger midpoint = ONE.shiftLeft(617).subtract(ONE);
        for (int extra : new int[] {1, 2, 39, 40, 63, 64, 65, 639, 4096}) {
            BigInteger center = midpoint.shiftLeft(extra);
            long scale = 8387991L - extra;
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger magnitude = center.add(BigInteger.valueOf(delta));
                for (boolean negative : new boolean[] {false, true}) {
                    assertRaw(dyadic(negative, magnitude, scale), Binary640.of(source(negative, magnitude, scale)));
                }
            }
        }
    }

    @Test
    void resolvesTinyTailsAtAnExactOverflowMidpointProduct() {
        BigInteger threshold = ONE.shiftLeft(617).subtract(ONE);
        BigInteger factor = BigInteger.valueOf(59233);
        BigInteger[] qr = threshold.divideAndRemainder(factor);
        assertEquals(BigInteger.ZERO, qr[1]);
        Binary640 a = Binary640.of(59233L);
        Binary640 b = dyadic(false, qr[0], 8387991);
        for (Binary640 x : new Binary640[] {a, a.negate()}) {
            for (Binary640 y : new Binary640[] {b, b.negate()}) {
                boolean negative = x.signBit() ^ y.signBit();
                Binary640 increasing = negative ? Binary640.MIN_VALUE.negate() : Binary640.MIN_VALUE;
                Binary640 decreasing = increasing.negate();
                assertRaw(negative ? Binary640.NEGATIVE_INFINITY : Binary640.POSITIVE_INFINITY, x.fma(y, increasing));
                assertRaw(negative ? Binary640.MAX_VALUE.negate() : Binary640.MAX_VALUE, x.fma(y, decreasing));
                for (Binary640 tail : new Binary640[] {increasing, decreasing, Binary640.ZERO, Binary640.NEGATIVE_ZERO}) {
                    assertRaw(Binary640Reference.fma(x, y, tail), x.fma(y, tail));
                }
            }
        }
    }

    @Test
    void coversTheMaximumFmaAlignmentSpanForBothProductDirections() {
        Random random = new Random(12345);
        for (int gap : new int[] {-619, -618, -617, 1231, 1232, 1233, 1234}) {
            for (int i = 0; i < 30; i++) {
                Binary640 a = dyadic(false, new BigInteger(616, random).setBit(615), -615);
                Binary640 b = dyadic(false, new BigInteger(616, random).setBit(615), -615);
                BigInteger product = Binary640Reference.decode(a).magnitude().multiply(Binary640Reference.decode(b).magnitude());
                long pe = product.bitLength() - 1L - 1230;
                Binary640 c = dyadic(false, new BigInteger(616, random).setBit(615), pe - gap - 615);
                for (Binary640 tail : new Binary640[] {c, c.negate()}) {
                    assertRaw(Binary640Reference.fma(a, b, tail), a.fma(b, tail));
                    assertRaw(Binary640Reference.fma(a.negate(), b, tail), a.negate().fma(b, tail));
                }
            }
        }
    }

    @Test
    void distinguishesSignedUnderflowAfterExactFmaCancellation() {
        for (long odd = 1; odd < 20; odd += 2) {
            Binary640 a = Binary640.MIN_VALUE.multiply(Binary640.of(odd));
            for (Binary640 x : new Binary640[] {a, a.negate()}) {
                for (Binary640 y : new Binary640[] {Binary640.of(0.5), Binary640.of(-0.5)}) {
                    for (Binary640 c : new Binary640[] {Binary640.MIN_VALUE, Binary640.MIN_VALUE.negate()}) {
                        assertRaw(Binary640Reference.fma(x, y, c), x.fma(y, c));
                    }
                }
            }
            assertRaw(Binary640Reference.divide(a, Binary640.of(2L)), a.divide(Binary640.of(2L)));
        }
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.MIN_VALUE.fma(Binary640.of(0.5), Binary640.MIN_VALUE.negate()));
    }

    @Test
    void keepsSquareRootRoundingAcrossBinadesAndExponentParity() {
        for (int exponent : new int[] {-8389221, -8388606, -1001, -1, 0, 1, 1001, 8388606, 8388607}) {
            Binary640 center = Binary640.ONE.scalb(exponent);
            for (Binary640 value : new Binary640[] {center.nextDown(), center, center.nextUp()}) {
                if (value.isFinite() && !value.isZero()) {
                    assertRaw(Binary640Reference.sqrt(value), value.sqrt());
                }
            }
        }
    }
}
