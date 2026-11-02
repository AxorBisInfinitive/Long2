package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Int;
import com.inf.long2.ints.Int256;

class Int256FloatConversionTest {
    private static final BigInteger ONE = BigInteger.ONE;

    @Test
    void mapsFloatSpecialValuesToIntegerZeroOrExtremes() {
        for (int bits : new int[] {0, Integer.MIN_VALUE, 0x7fc00000, 0xffc00000, 0x7f800001, 0xff800001,
                0x7fffffff, 0xffffffff}) {
            assertEquals(Int256.ZERO, Int256.of(Float.intBitsToFloat(bits)));
        }
        assertEquals(Int256.MAX_VALUE, Int256.of(Float.POSITIVE_INFINITY));
        assertEquals(Int256.MIN_VALUE, Int256.of(Float.NEGATIVE_INFINITY));
    }

    @Test
    void truncatesFloatFractionsAndPreservesLargeFiniteIntegers() {
        assertEquals(Int256.ONE, Int256.of(1.99f));
        assertEquals(Int256.of(-1L), Int256.of(-1.99f));
        for (float value : new float[] {Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, -Float.MIN_NORMAL,
                Math.nextDown(1.0f), -Math.nextDown(1.0f), Math.nextUp(1.0f), -Math.nextUp(1.0f)}) {
            assertFloatSource(value);
        }
        for (int exponent : new int[] {0, 1, 23, 24, 31, 32, 63, 64, 65, 126, 127}) {
            float center = Math.scalb(1.0f, exponent);
            for (float magnitude : new float[] {Math.nextDown(center), center, Math.nextUp(center)}) {
                assertFloatSource(magnitude);
                assertFloatSource(-magnitude);
            }
        }
        BigInteger maximum = ONE.shiftLeft(128).subtract(ONE.shiftLeft(104));
        assertEquals(maximum, new BigInteger(Int256.of(Float.MAX_VALUE).toBytes()));
        assertEquals(maximum.negate(), new BigInteger(Int256.of(-Float.MAX_VALUE).toBytes()));
    }

    @Test
    void matchesExactDecimalTruncationForRandomFloatEncodings() {
        Random random = new Random(0x0FF10A7256L);
        for (int i = 0; i < 5000; i++) {
            assertFloatSource(Float.intBitsToFloat(random.nextInt()));
        }
    }

    @Test
    void roundTripsIntegralFloatSourcesAcrossAllLargeFiniteExponents() {
        for (int exponent = 23; exponent <= 127; exponent++) {
            for (int fraction : new int[] {0, 1, 0x400000, 0x7fffff}) {
                int positive = ((exponent + 127) << 23) | fraction;
                for (int bits : new int[] {positive, positive | Integer.MIN_VALUE}) {
                    assertBits(bits, Int256.of(Float.intBitsToFloat(bits)));
                }
            }
        }
    }

    @Test
    void matchesDirectPrimitiveCastsAndReturnsPositiveZero() {
        assertBits(0, Int256.ZERO);
        for (long value : new long[] {1, -1, 2, -2, Integer.MIN_VALUE, Integer.MAX_VALUE,
                (1L << 24) - 1, 1L << 24, (1L << 24) + 1, (1L << 24) + 3,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE}) {
            assertBits(Float.floatToRawIntBits((float) value), Int256.of(value));
        }
    }

    @Test
    void roundsEveryIntegerBinadeAtEvenAndOddMidpoints() {
        for (int exponent = 24; exponent <= 127; exponent++) {
            int discarded = exponent - 23;
            for (int retained : new int[] {0x800000, 0x800001, 0x800002, 0xabcdef, 0xfffffe, 0xffffff}) {
                BigInteger midpoint = BigInteger.valueOf(retained).shiftLeft(discarded)
                        .add(ONE.shiftLeft(discarded - 1));
                int lowerBits = ((exponent + 127) << 23) | (retained & 0x7fffff);
                for (int delta : new int[] {-1, 0, 1}) {
                    BigInteger magnitude = midpoint.add(BigInteger.valueOf(delta));
                    int expected = delta > 0 || (delta == 0 && (retained & 1) != 0)
                            ? lowerBits + 1 : lowerBits;
                    assertBits(expected, Int256.parseInt(magnitude.toString()));
                    assertBits(expected | Integer.MIN_VALUE, Int256.parseInt(magnitude.negate().toString()));
                }
            }
        }
    }

    @Test
    void roundsAtTheFiniteOverflowBoundaryAndHandlesFullWidthExtremes() {
        BigInteger maximum = ONE.shiftLeft(128).subtract(ONE.shiftLeft(104));
        BigInteger overflowMidpoint = ONE.shiftLeft(128).subtract(ONE.shiftLeft(103));
        assertBits(0x7f7fffff, Int256.parseInt(maximum.toString()));
        assertBits(0xff7fffff, Int256.parseInt(maximum.negate().toString()));
        assertBits(0x7f7fffff, Int256.parseInt(overflowMidpoint.subtract(ONE).toString()));
        assertBits(0xff7fffff, Int256.parseInt(overflowMidpoint.subtract(ONE).negate().toString()));
        for (BigInteger magnitude : new BigInteger[] {overflowMidpoint, overflowMidpoint.add(ONE),
                ONE.shiftLeft(128), ONE.shiftLeft(192), ONE.shiftLeft(254)}) {
            assertBits(0x7f800000, Int256.parseInt(magnitude.toString()));
            assertBits(0xff800000, Int256.parseInt(magnitude.negate().toString()));
        }
        assertBits(0x7f800000, Int256.MAX_VALUE);
        assertBits(0xff800000, Int256.MIN_VALUE);
    }

    @Test
    void avoidsDoubleRoundingOnBothSidesOfMidpoints() {
        for (int exponent : new int[] {54, 63, 64, 80, 100, 127}) {
            int discarded = exponent - 23;
            for (int retained : new int[] {0x800000, 0x800001, 0xffffff}) {
                boolean odd = (retained & 1) != 0;
                BigInteger midpoint = BigInteger.valueOf(retained).shiftLeft(discarded)
                        .add(ONE.shiftLeft(discarded - 1));
                BigInteger magnitude = odd ? midpoint.subtract(ONE) : midpoint.add(ONE);
                int lowerBits = ((exponent + 127) << 23) | (retained & 0x7fffff);
                int directBits = odd ? lowerBits : lowerBits + 1;
                for (boolean negative : new boolean[] {false, true}) {
                    Int256 value = Int256.parseInt((negative ? magnitude.negate() : magnitude).toString());
                    int expected = directBits | (negative ? Integer.MIN_VALUE : 0);
                    assertBits(expected, value);
                    assertNotEquals(expected, Float.floatToRawIntBits((float) value.toDouble()));
                }
            }
        }
    }

    @Test
    void matchesBigIntegerForRandomMagnitudesAcrossTheWholeRange() {
        Random random = new Random(0xF10A7256L);
        for (int i = 0; i < 5000; i++) {
            int length = 1 + random.nextInt(255);
            BigInteger magnitude = new BigInteger(length, random).setBit(length - 1);
            BigInteger signed = random.nextBoolean() ? magnitude : magnitude.negate();
            Int<?> value = Int256.parseInt(signed.toString());
            byte[] before = value.toBytes();
            assertBits(Float.floatToRawIntBits(signed.floatValue()), value);
            assertArrayEquals(before, value.toBytes());
        }
    }

    private static void assertBits(int expected, Int<?> value) {
        assertEquals(expected, Float.floatToRawIntBits(value.toFloat()));
    }

    private static void assertFloatSource(float value) {
        Int256 actual = Int256.of(value);
        if (Float.isNaN(value)) {
            assertEquals(Int256.ZERO, actual);
        } else if (Float.isInfinite(value)) {
            assertEquals(value < 0 ? Int256.MIN_VALUE : Int256.MAX_VALUE, actual);
        } else {
            BigInteger expected = new BigDecimal((double) value).toBigInteger();
            assertEquals(expected, new BigInteger(actual.toBytes()), "float bits=" + Float.floatToRawIntBits(value));
        }
    }
}
