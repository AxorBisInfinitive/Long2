package com.inf.long2.ints.int512;

import static com.inf.long2.ints.int512.Int512TestSupport.MAXIMUM;
import static com.inf.long2.ints.int512.Int512TestSupport.MINIMUM;
import static com.inf.long2.ints.int512.Int512TestSupport.ONE;
import static com.inf.long2.ints.int512.Int512TestSupport.assertNumber;
import static com.inf.long2.ints.int512.Int512TestSupport.from;
import static com.inf.long2.ints.int512.Int512TestSupport.integer;
import static com.inf.long2.ints.int512.Int512TestSupport.number;
import static com.inf.long2.ints.int512.Int512TestSupport.random;
import static com.inf.long2.ints.int512.Int512TestSupport.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.function.IntToLongFunction;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.binaries.Binary256;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

class Int512ConversionTest {
    @Test
    void extendsPrimitiveIntegersAndNarrowsByKeepingLowBits() {
        for (long value : new long[] {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE}) {
            Int512 integer = Int512.of(value);
            assertNumber(BigInteger.valueOf(value), integer);
            assertEquals(value, integer.toLong());
            assertEquals((int) value, integer.toInt());
        }
        Random random = new Random(0x5121A7L);
        for (int i = 0; i < 2000; i++) {
            Int512 value = random(random);
            assertEquals(number(value).intValue(), value.toInt());
            assertEquals(number(value).longValue(), value.toLong());
        }
    }

    @Test
    void extendsAndTruncatesIntegerSourcesIncludingInt256() {
        Random random = new Random(0x512C057L);
        for (int width : new int[] {64, 128, 192, 256, 448, 512, 576, 1024}) {
            BigInteger limit = ONE.shiftLeft(width - 1);
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.negate(), limit.negate(),
                    limit.subtract(ONE), ONE.shiftLeft(512).add(BigInteger.valueOf(37)))) {
                assertNumber(wrap(value, width), Int512.of(integer(width, value)));
            }
            for (int i = 0; i < 150; i++) {
                BigInteger value = new BigInteger(width, random);
                assertNumber(wrap(value, width), Int512.of(integer(width, value)));
            }
        }
        for (int i = 0; i < 300; i++) {
            Int512 value = random(random);
            Int256 narrow = Int256.of(value);
            BigInteger expected = wrap(number(value), 256);
            assertEquals(expected, new BigInteger(narrow.toBytes()));
            assertNumber(expected, Int512.of(narrow));
            assertSame(value, Int512.of((Int<?>) value));
        }
        assertEquals(Int512.of(-1L), Int512.of(Int256.of(-1L)));
        assertNumber(ONE.shiftLeft(255).negate(), Int512.of(Int256.MIN_VALUE));
        assertThrowsExactly(NullPointerException.class, () -> Int512.of((Int<?>) null));
    }

    @Test
    void readsOnlyEightLowWordsFromTheLargestIntegerFormat() {
        Int.Format format = new Int.Format(2147483584);
        int groups = format.bitWidth() / 64;
        long[] low = {Long.MIN_VALUE, 1, -1, 2, 0, 37, -2, Long.MAX_VALUE};
        for (boolean negative : new boolean[] {false, true}) {
            int[] reads = {0};
            Int<?> source = (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "format" -> format;
                        case "isNegative" -> negative;
                        case "getLong" -> {
                            int index = (int) arguments[0];
                            assertTrue(index >= groups - 8 && index < groups);
                            reads[0]++;
                            yield low[index - groups + 8];
                        }
                        default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                    });
            assertEquals(Int512.of(low), Int512.of(source));
            assertEquals(8, reads[0]);
        }
    }

    @Test
    void truncatesAndClampsPrimitiveFloatsUsingTheirExactValues() {
        for (double value : new double[] {0, -0.0, 0.5, -0.5, 1.75, -1.75, Double.MIN_VALUE,
                Double.MIN_NORMAL, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NaN, Math.nextDown(Math.scalb(1.0, 511)), Math.scalb(1.0, 511),
                Math.nextUp(Math.scalb(1.0, 511)), -Math.scalb(1.0, 511),
                -Math.nextDown(Math.scalb(1.0, 511)), -Math.nextUp(Math.scalb(1.0, 511))}) {
            checkDouble(value);
        }
        Random random = new Random(0x512D0B1EL);
        for (int i = 0; i < 3000; i++) {
            checkDouble(Double.longBitsToDouble(random.nextLong()));
            float value = Float.intBitsToFloat(random.nextInt());
            assertNumber(expected(value), Int512.of(value));
        }
        for (int exponent = 0; exponent <= 510; exponent++) {
            for (double fraction : new double[] {1.0, 1.5, Math.nextDown(2.0)}) {
                double value = Math.scalb(fraction, exponent);
                checkDouble(value);
                checkDouble(-value);
            }
        }
    }

    @Test
    void directlyRoundsRandomIntegerMagnitudesToBothPrimitiveFormats() {
        Random random = new Random(0x512F10A7L);
        for (int i = 0; i < 4000; i++) {
            int length = random.nextInt(512);
            BigInteger value = new BigInteger(length, random);
            if (random.nextBoolean()) {
                value = value.negate();
            }
            checkPrimitive(value);
        }
        checkPrimitive(BigInteger.ZERO);
        checkPrimitive(MINIMUM);
        checkPrimitive(MAXIMUM);
        assertEquals(0, Float.floatToRawIntBits(Int512.ZERO.toFloat()));
        assertEquals(0L, Double.doubleToRawLongBits(Int512.ZERO.toDouble()));
        assertTrue(Double.isFinite(Int512.MIN_VALUE.toDouble()));
        assertTrue(Double.isFinite(Int512.MAX_VALUE.toDouble()));
    }

    @Test
    void roundsEvenAndOddMidpointsAcrossAllBinadesAndFloatOverflow() {
        for (int precision : new int[] {24, 53}) {
            int maximumExponent = precision == 24 ? 127 : 510;
            long leading = 1L << (precision - 1);
            for (int exponent = precision; exponent <= maximumExponent; exponent++) {
                int discarded = exponent - precision + 1;
                for (long kept : new long[] {leading, leading + 1, leading + 2, (1L << precision) - 1}) {
                    BigInteger midpoint = BigInteger.valueOf(kept).shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
                    for (int delta : new int[] {-1, 0, 1}) {
                        BigInteger value = midpoint.add(BigInteger.valueOf(delta));
                        checkPrimitive(value);
                        checkPrimitive(value.negate());
                    }
                }
            }
        }
        BigInteger overflow = ONE.shiftLeft(128).subtract(ONE.shiftLeft(103));
        assertEquals(0x7f7fffff, Float.floatToRawIntBits(from(overflow.subtract(ONE)).toFloat()));
        assertEquals(0x7f800000, Float.floatToRawIntBits(from(overflow).toFloat()));
        assertEquals(0xff800000, Float.floatToRawIntBits(from(overflow.negate()).toFloat()));
        for (int exponent : new int[] {54, 63, 64, 80, 100, 127}) {
            int discarded = exponent - 23;
            for (int kept : new int[] {0x800000, 0x800001, 0xffffff}) {
                BigInteger midpoint = BigInteger.valueOf(kept).shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
                BigInteger value = midpoint.add((kept & 1) == 0 ? ONE : ONE.negate());
                for (BigInteger signed : List.of(value, value.negate())) {
                    Int512 integer = from(signed);
                    assertEquals(Float.floatToRawIntBits(signed.floatValue()), Float.floatToRawIntBits(integer.toFloat()));
                    assertNotEquals(Float.floatToRawIntBits((float) integer.toDouble()), Float.floatToRawIntBits(integer.toFloat()));
                }
            }
        }
    }

    @Test
    void convertsGenericBinarySourcesAtFractionalAndRangeBoundaries() {
        Random random = new Random(0x512B1A2L);
        for (int precision : new int[] {2, 24, 53, 64, 237, 511, 512, 513, 700, 1025}) {
            for (int exponent : new int[] {-1, 0, 1, 62, 63, 64, 127, 255, 256, 447, 448, 449, 510, 511, 512}) {
                BigInteger magnitude = new BigInteger(precision, random).setBit(precision - 1);
                int scale = exponent - precision + 1;
                int groups = (precision + 63) / 64;
                for (boolean negative : new boolean[] {false, true}) {
                    Binary<?> source = binary(new Binary.Format(precision, -1000000, 1000000), negative, scale,
                            index -> magnitude.shiftRight((groups - 1 - index) * 64).longValue());
                    BigInteger truncated = scale >= 0 ? magnitude.shiftLeft(scale) : magnitude.shiftRight(-scale);
                    assertNumber((negative ? truncated.negate() : truncated).max(MINIMUM).min(MAXIMUM), Int512.of(source));
                }
            }
        }
        for (Binary<?> source : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.NaN,
                Binary256.MIN_VALUE, Binary256.MIN_NORMAL, Binary256.MIN_VALUE.negate())) {
            assertEquals(Int512.ZERO, Int512.of(source));
        }
        assertEquals(Int512.MAX_VALUE, Int512.of((Binary<?>) Binary256.POSITIVE_INFINITY));
        assertEquals(Int512.MIN_VALUE, Int512.of((Binary<?>) Binary256.NEGATIVE_INFINITY));
        assertThrowsExactly(NullPointerException.class, () -> Int512.of((Binary<?>) null));
    }

    @Test
    void truncatesHighPrecisionBinarySourcesBeforeClampingAtSignedLimits() {
        BigInteger limit = MINIMUM.negate();
        for (BigInteger center : List.of(MAXIMUM, limit, limit.add(ONE))) {
            for (int fractionBits : new int[] {0, 1, 2, 63, 64, 65, 256, 513}) {
                BigInteger denominator = ONE.shiftLeft(fractionBits);
                for (int delta = -3; delta <= 3; delta++) {
                    BigInteger magnitude = center.multiply(denominator).add(BigInteger.valueOf(delta));
                    int precision = magnitude.bitLength(), groups = (precision + 63) / 64;
                    Binary.Format format = new Binary.Format(precision, -1000000, 1000000);
                    for (boolean negative : new boolean[] {false, true}) {
                        Binary<?> source = binary(format, negative, -fractionBits,
                                index -> magnitude.shiftRight((groups - 1 - index) * 64).longValue());
                        // 先对精确幅值向零截断，再按符号钳制，不经过其他浮点格式。
                        BigInteger truncated = magnitude.divide(denominator);
                        BigInteger expected = (negative ? truncated.negate() : truncated).max(MINIMUM).min(MAXIMUM);
                        assertEquals(expected, number(Int512.of(source)),
                                () -> "magnitude=" + magnitude + " fractionBits=" + fractionBits + " negative=" + negative);
                    }
                }
            }
        }
    }

    @Test
    void boundsSignificandReadsForTheLargestBinaryPrecision() {
        int precision = Integer.MAX_VALUE;
        Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (int exponent : new int[] {-1, 0, 63, 64, 447, 448, 510, 511, 512}) {
            for (boolean negative : new boolean[] {false, true}) {
                int[] reads = {0};
                Binary<?> source = binary(format, negative, exponent - (long) precision + 1, index -> {
                    reads[0]++;
                    assertTrue(index < 9);
                    return index == 0 ? (1L << 62) | (1L << 61) : 0;
                });
                BigInteger expected = exponent < 0 ? BigInteger.ZERO
                        : ONE.shiftLeft(exponent).add(exponent == 0 ? BigInteger.ZERO : ONE.shiftLeft(exponent - 1));
                expected = (negative ? expected.negate() : expected).max(MINIMUM).min(MAXIMUM);
                assertNumber(expected, Int512.of(source));
                assertTrue(reads[0] <= 9);
                if (exponent < 0 || exponent >= 511) {
                    assertEquals(0, reads[0]);
                }
            }
        }
    }

    private static void checkDouble(double value) {
        assertNumber(expected(value), Int512.of(value));
        assertEquals(Int512.of(value), Int512.of((Binary<?>) Binary256.of(value)));
    }

    private static BigInteger expected(double value) {
        if (Double.isNaN(value)) {
            return BigInteger.ZERO;
        }
        if (Double.isInfinite(value)) {
            return value < 0 ? MINIMUM : MAXIMUM;
        }
        return new BigDecimal(value).toBigInteger().max(MINIMUM).min(MAXIMUM);
    }

    private static void checkPrimitive(BigInteger value) {
        Int512 integer = from(value);
        assertEquals(Float.floatToRawIntBits(value.floatValue()), Float.floatToRawIntBits(integer.toFloat()),
                () -> "float source=" + value);
        assertEquals(Double.doubleToRawLongBits(value.doubleValue()), Double.doubleToRawLongBits(integer.toDouble()),
                () -> "double source=" + value);
    }

    private static Binary<?> binary(Binary.Format format, boolean negative, long scale, IntToLongFunction words) {
        int groups = (int) ((format.precision() + 63L) / 64);
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> format;
                    case "isNaN", "isInfinite", "isZero" -> false;
                    case "isNormal" -> true;
                    case "signBit" -> negative;
                    case "scaleExponent" -> scale;
                    case "getSignificandLong" -> {
                        int index = (int) arguments[0];
                        assertTrue(index >= 0 && index < groups);
                        yield words.applyAsLong(index);
                    }
                    default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                });
    }
}
