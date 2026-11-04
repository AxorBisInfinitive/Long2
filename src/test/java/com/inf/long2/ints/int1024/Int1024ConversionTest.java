package com.inf.long2.ints.int1024;

import static com.inf.long2.ints.int1024.Int1024TestSupport.MAXIMUM;
import static com.inf.long2.ints.int1024.Int1024TestSupport.MINIMUM;
import static com.inf.long2.ints.int1024.Int1024TestSupport.ONE;
import static com.inf.long2.ints.int1024.Int1024TestSupport.assertNumber;
import static com.inf.long2.ints.int1024.Int1024TestSupport.from;
import static com.inf.long2.ints.int1024.Int1024TestSupport.integer;
import static com.inf.long2.ints.int1024.Int1024TestSupport.number;
import static com.inf.long2.ints.int1024.Int1024TestSupport.random;
import static com.inf.long2.ints.int1024.Int1024TestSupport.wrap;
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
import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

class Int1024ConversionTest {
    @Test
    void extendsPrimitiveIntegersAndNarrowsByKeepingLowBits() {
        for (long value : new long[] {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE}) {
            Int1024 integer = Int1024.of(value);
            assertNumber(BigInteger.valueOf(value), integer);
            assertEquals(value, integer.toLong());
            assertEquals((int) value, integer.toInt());
        }
        Random random = new Random(0x10241A7L);
        for (int i = 0; i < 2000; i++) {
            Int1024 value = random(random);
            assertEquals(number(value).intValue(), value.toInt());
            assertEquals(number(value).longValue(), value.toLong());
        }
    }

    @Test
    void extendsAndTruncatesIntegerSourcesIncludingNarrowerImplementations() {
        Random random = new Random(0x1024C057L);
        for (int width : new int[] {64, 128, 192, 256, 448, 512, 576, 960, 1024, 1088, 2048}) {
            BigInteger limit = ONE.shiftLeft(width - 1);
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.negate(), limit.negate(),
                    limit.subtract(ONE), ONE.shiftLeft(1024).add(BigInteger.valueOf(37)))) {
                assertNumber(wrap(value, width), Int1024.of(integer(width, value)));
            }
            for (int i = 0; i < 150; i++) {
                BigInteger value = new BigInteger(width, random);
                assertNumber(wrap(value, width), Int1024.of(integer(width, value)));
            }
        }
        for (int i = 0; i < 300; i++) {
            Int1024 value = random(random);
            Int256 narrow = Int256.of(value);
            BigInteger expected = wrap(number(value), 256);
            assertEquals(expected, new BigInteger(narrow.toBytes()));
            assertNumber(expected, Int1024.of(narrow));
            Int512 half = Int512.of(value);
            BigInteger expectedHalf = wrap(number(value), 512);
            assertEquals(expectedHalf, new BigInteger(half.toBytes()));
            assertNumber(expectedHalf, Int1024.of(half));
            assertSame(value, Int1024.of((Int<?>) value));
        }
        assertEquals(Int1024.of(-1L), Int1024.of(Int256.of(-1L)));
        assertNumber(ONE.shiftLeft(255).negate(), Int1024.of(Int256.MIN_VALUE));
        assertNumber(ONE.shiftLeft(511).negate(), Int1024.of(Int512.MIN_VALUE));
        assertNumber(ONE.shiftLeft(511).subtract(ONE), Int1024.of(Int512.MAX_VALUE));
        assertEquals(Int1024.of(-1L), Int1024.of(Int512.of(-1L)));
        assertThrowsExactly(NullPointerException.class, () -> Int1024.of((Int<?>) null));
    }

    @Test
    void readsOnlySixteenLowWordsFromTheLargestIntegerFormat() {
        Int.Format format = new Int.Format(2147483584);
        int groups = format.bitWidth() / 64;
        long[] low = {Long.MIN_VALUE, 1, -1, 2, 0, 37, -2, Long.MAX_VALUE,
                8, -9, 10, -11, 12, -13, 14, -15};
        for (boolean negative : new boolean[] {false, true}) {
            int[] reads = {0};
            Int<?> source = (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "format" -> format;
                        case "isNegative" -> negative;
                        case "getLong" -> {
                            int index = (int) arguments[0];
                            assertTrue(index >= groups - 16 && index < groups);
                            reads[0]++;
                            yield low[index - groups + 16];
                        }
                        default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                    });
            assertEquals(Int1024.of(low), Int1024.of(source));
            assertEquals(16, reads[0]);
        }
    }

    @Test
    void truncatesAndClampsPrimitiveFloatsUsingTheirExactValues() {
        for (double value : new double[] {0, -0.0, 0.5, -0.5, 1.75, -1.75, Double.MIN_VALUE,
                Double.MIN_NORMAL, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NaN, Math.nextDown(Math.scalb(1.0, 1023)), Math.scalb(1.0, 1023),
                Math.nextUp(Math.scalb(1.0, 1023)), -Math.scalb(1.0, 1023),
                -Math.nextDown(Math.scalb(1.0, 1023)), -Math.nextUp(Math.scalb(1.0, 1023))}) {
            checkDouble(value);
        }
        Random random = new Random(0x1024D0B1EL);
        for (int i = 0; i < 3000; i++) {
            checkDouble(Double.longBitsToDouble(random.nextLong()));
            float value = Float.intBitsToFloat(random.nextInt());
            assertNumber(expected(value), Int1024.of(value));
        }
        for (int exponent = 0; exponent <= 1022; exponent++) {
            for (double fraction : new double[] {1.0, 1.5, Math.nextDown(2.0)}) {
                double value = Math.scalb(fraction, exponent);
                checkDouble(value);
                checkDouble(-value);
            }
        }
    }

    @Test
    void directlyRoundsRandomIntegerMagnitudesToBothPrimitiveFormats() {
        Random random = new Random(0x1024F10A7L);
        for (int i = 0; i < 4000; i++) {
            int length = random.nextInt(1024);
            BigInteger value = new BigInteger(length, random);
            if (random.nextBoolean()) {
                value = value.negate();
            }
            checkPrimitive(value);
        }
        checkPrimitive(BigInteger.ZERO);
        checkPrimitive(MINIMUM);
        checkPrimitive(MAXIMUM);
        assertEquals(0, Float.floatToRawIntBits(Int1024.ZERO.toFloat()));
        assertEquals(0L, Double.doubleToRawLongBits(Int1024.ZERO.toDouble()));
        assertTrue(Double.isFinite(Int1024.MIN_VALUE.toDouble()));
        assertTrue(Double.isFinite(Int1024.MAX_VALUE.toDouble()));
        assertEquals(Math.scalb(1.0, 1023), Int1024.MAX_VALUE.toDouble());
        assertEquals(-Math.scalb(1.0, 1023), Int1024.MIN_VALUE.toDouble());
    }

    @Test
    void roundsEvenAndOddMidpointsAcrossAllBinadesAndFloatOverflow() {
        for (int precision : new int[] {24, 53}) {
            int maximumExponent = precision == 24 ? 127 : 1022;
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
                    Int1024 integer = from(signed);
                    assertEquals(Float.floatToRawIntBits(signed.floatValue()), Float.floatToRawIntBits(integer.toFloat()));
                    assertNotEquals(Float.floatToRawIntBits((float) integer.toDouble()), Float.floatToRawIntBits(integer.toFloat()));
                }
            }
        }
    }

    @Test
    void convertsGenericBinarySourcesAtFractionalAndRangeBoundaries() {
        Random random = new Random(0x1024B1A2L);
        for (int precision : new int[] {2, 24, 53, 64, 237, 511, 512, 513, 1023, 1024, 1025, 1600, 2049}) {
            for (int exponent : new int[] {-1, 0, 1, 62, 63, 64, 127, 255, 256, 447, 448, 449, 511, 512, 513, 959, 960, 961, 1022, 1023, 1024}) {
                BigInteger magnitude = new BigInteger(precision, random).setBit(precision - 1);
                int scale = exponent - precision + 1;
                int groups = (precision + 63) / 64;
                for (boolean negative : new boolean[] {false, true}) {
                    Binary<?> source = binary(new Binary.Format(precision, -1000000, 1000000), negative, scale,
                            index -> magnitude.shiftRight((groups - 1 - index) * 64).longValue());
                    BigInteger truncated = scale >= 0 ? magnitude.shiftLeft(scale) : magnitude.shiftRight(-scale);
                    assertNumber((negative ? truncated.negate() : truncated).max(MINIMUM).min(MAXIMUM), Int1024.of(source));
                }
            }
        }
        for (Binary<?> source : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.NaN,
                Binary256.MIN_VALUE, Binary256.MIN_NORMAL, Binary256.MIN_VALUE.negate())) {
            assertEquals(Int1024.ZERO, Int1024.of(source));
        }
        assertEquals(Int1024.MAX_VALUE, Int1024.of((Binary<?>) Binary256.POSITIVE_INFINITY));
        assertEquals(Int1024.MIN_VALUE, Int1024.of((Binary<?>) Binary256.NEGATIVE_INFINITY));
        assertThrowsExactly(NullPointerException.class, () -> Int1024.of((Binary<?>) null));
    }

    @Test
    void truncatesHighPrecisionBinarySourcesBeforeClampingAtSignedLimits() {
        BigInteger limit = MINIMUM.negate();
        for (BigInteger center : List.of(MAXIMUM, limit, limit.add(ONE))) {
            for (int fractionBits : new int[] {0, 1, 2, 63, 64, 65, 256, 511, 512, 513, 1025}) {
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
                        assertEquals(expected, number(Int1024.of(source)),
                                () -> "magnitude=" + magnitude + " fractionBits=" + fractionBits + " negative=" + negative);
                    }
                }
            }
        }
    }

    @Test
    void boundsSignificandReadsForTheLargestBinaryPrecision() {
        for (int precision : new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE - 62}) {
            int topBit = (precision - 1) & 63;
            Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
            for (int exponent : new int[] {-1, 0, 63, 64, 511, 512, 513, 959, 960, 961, 1022, 1023, 1024}) {
                for (boolean negative : new boolean[] {false, true}) {
                    int[] reads = {0};
                    Binary<?> source = binary(format, negative, exponent - (long) precision + 1, index -> {
                        reads[0]++;
                        assertTrue(index < 17);
                        if (index == 0) {
                            return (1L << topBit) | (topBit == 0 ? 0 : 1L << (topBit - 1));
                        }
                        return index == 1 && topBit == 0 ? Long.MIN_VALUE : 0;
                    });
                    BigInteger expected = exponent < 0 ? BigInteger.ZERO
                            : ONE.shiftLeft(exponent).add(exponent == 0 ? BigInteger.ZERO : ONE.shiftLeft(exponent - 1));
                    expected = (negative ? expected.negate() : expected).max(MINIMUM).min(MAXIMUM);
                    assertNumber(expected, Int1024.of(source));
                    assertTrue(reads[0] <= 17);
                    if (exponent < 0 || exponent >= 1023) {
                        assertEquals(0, reads[0]);
                    }
                }
            }
        }
    }

    private static void checkDouble(double value) {
        assertNumber(expected(value), Int1024.of(value));
        assertEquals(Int1024.of(value), Int1024.of((Binary<?>) Binary256.of(value)));
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
        Int1024 integer = from(value);
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
