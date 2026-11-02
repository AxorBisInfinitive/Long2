package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.function.IntToLongFunction;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.binaries.Binary256;
import com.inf.long2.ints.Int256;

class Int256ConversionTest {
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger MINIMUM = ONE.shiftLeft(255).negate();
    private static final BigInteger MAXIMUM = ONE.shiftLeft(255).subtract(ONE);

    @Test
    void narrowsPrimitiveIntegersByDiscardingHighBitsAtSignedBoundaries() {
        assertEquals(0, Int256.MIN_VALUE.toInt());
        assertEquals(0L, Int256.MIN_VALUE.toLong());
        assertEquals(-1, Int256.MAX_VALUE.toInt());
        assertEquals(-1L, Int256.MAX_VALUE.toLong());
        assertEquals(Integer.MIN_VALUE, Int256.parseInt("2147483648").toInt());
        assertEquals(Integer.MAX_VALUE, Int256.parseInt("-2147483649").toInt());
        assertEquals(0, Int256.parseInt("4294967296").toInt());
        assertEquals(Long.MIN_VALUE, Int256.parseInt("9223372036854775808").toLong());
        assertEquals(Long.MAX_VALUE, Int256.parseInt("-9223372036854775809").toLong());
        assertEquals(0L, Int256.parseInt("18446744073709551616").toLong());
        for (BigInteger center : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(31), ONE.shiftLeft(32),
                ONE.shiftLeft(63), ONE.shiftLeft(64), ONE.shiftLeft(127), ONE.shiftLeft(192), MAXIMUM)) {
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger magnitude = center.add(BigInteger.valueOf(delta));
                for (BigInteger signed : List.of(magnitude, magnitude.negate())) {
                    Int<?> value = Int256.parseInt(signed.toString());
                    assertEquals(signed.intValue(), value.toInt());
                    assertEquals(signed.longValue(), value.toLong());
                }
            }
        }
    }

    @Test
    void narrowsRandomEncodingsAgainstBigIntegerWithoutChangingTheSource() {
        Random random = new Random(0x1A220256L);
        for (int i = 0; i < 5000; i++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            BigInteger expected = new BigInteger(bytes);
            Int<?> value = Int256.of(bytes);
            assertEquals(expected.intValue(), value.toInt());
            assertEquals(expected.longValue(), value.toLong());
            assertArrayEquals(bytes, value.toBytes());
        }
    }

    @Test
    void extendsAndTruncatesIntegerSourcesAcrossWordBoundaries() {
        Random random = new Random(0x1256C057L);
        for (int width : new int[] {64, 128, 192, 256, 320, 512, 1024}) {
            BigInteger limit = ONE.shiftLeft(width - 1);
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.negate(), limit.negate(),
                    limit.negate().add(ONE), limit.subtract(ONE), ONE.shiftLeft(256).add(BigInteger.valueOf(37)),
                    ONE.shiftLeft(256).add(BigInteger.valueOf(37)).negate())) {
                assertIntegerConversion(width, value);
            }
            for (int i = 0; i < 150; i++) {
                assertIntegerConversion(width, new BigInteger(width, random));
            }
        }
    }

    @Test
    void convertsSameTypeValuesWithoutChangingTheirEncoding() {
        for (Int256 source : List.of(Int256.ZERO, Int256.ONE, Int256.of(-1L), Int256.MIN_VALUE, Int256.MAX_VALUE)) {
            byte[] before = source.toBytes();
            assertEquals(source, Int256.of((Int<?>) source));
            assertArrayEquals(before, source.toBytes());
        }
    }

    @Test
    void readsOnlyTheFourLowGroupsOfTheLargestSupportedIntegerWidth() {
        Int.Format format = new Int.Format(2147483584);
        int groups = format.bitWidth() / 64;
        long[] low = {0x1234_5678_9abc_def0L, -1L, Long.MIN_VALUE, 37};
        for (boolean negative : new boolean[] {false, true}) {
            int[] reads = {0};
            Int<?> source = (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "format" -> format;
                        case "isNegative" -> negative;
                        case "getLong" -> {
                            int index = (int) arguments[0];
                            assertTrue(index >= groups - 4 && index < groups);
                            reads[0]++;
                            yield low[index - groups + 4];
                        }
                        default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                    });
            assertEquals(Int256.of(low), Int256.of(source));
            assertEquals(4, reads[0]);
        }
    }

    @Test
    void convertsSpecialFloatingValuesAndRejectsNullSources() {
        for (Binary<?> zero : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.NaN,
                Binary256.fromRawBits(0xffff_f000_0000_0000L, 0, 0, 1))) {
            assertEquals(Int256.ZERO, Int256.of(zero));
        }
        assertEquals(Int256.MAX_VALUE, Int256.of((Binary<?>) Binary256.POSITIVE_INFINITY));
        assertEquals(Int256.MIN_VALUE, Int256.of((Binary<?>) Binary256.NEGATIVE_INFINITY));
        assertThrowsExactly(NullPointerException.class, () -> Int256.of((Int<?>) null));
        assertThrowsExactly(NullPointerException.class, () -> Int256.of((Binary<?>) null));
    }

    @Test
    void matchesThePrimitiveDoubleContractWithoutIntermediateRounding() {
        Random random = new Random(0xD0B1E256L);
        for (double value : new double[] {0, -0.0, 1.5, -1.5, Double.MIN_VALUE, Double.MIN_NORMAL,
                Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
                Math.nextDown(Math.scalb(1.0, 255)), Math.scalb(1.0, 255), -Math.scalb(1.0, 255)}) {
            assertDoubleConversion(value);
        }
        for (int i = 0; i < 2000; i++) {
            assertDoubleConversion(Double.longBitsToDouble(random.nextLong()));
        }
    }

    @Test
    void matchesConcreteAndGenericBinarySourcesAtEveryIntegerWordBoundary() {
        Random random = new Random(0xB1256E7ACL);
        for (int exponent : new int[] {-262142, -1, 0, 1, 62, 63, 64, 65, 126, 127, 128, 129,
                190, 191, 192, 193, 235, 236, 237, 253, 254, 255, 256, 262143}) {
            for (int i = 0; i < 30; i++) {
                BigInteger fraction = new BigInteger(236, random);
                BigInteger magnitude = fraction.setBit(236);
                long scale = exponent - 236L;
                BigInteger encoded = fraction.or(BigInteger.valueOf(exponent + 262143L).shiftLeft(236));
                for (boolean negative : new boolean[] {false, true}) {
                    BigInteger bits = negative ? encoded.setBit(255) : encoded;
                    Binary256 concrete = Binary256.fromRawBits(bits.shiftRight(192).longValue(),
                            bits.shiftRight(128).longValue(), bits.shiftRight(64).longValue(), bits.longValue());
                    Binary<?> generic = binary(Binary256.FORMAT, negative, 237, scale,
                            index -> magnitude.shiftRight((3 - index) * 64).longValue());
                    BigInteger expected = truncated(magnitude, (int) scale, negative);
                    assertEquals(expected, number(Int256.of(concrete)));
                    assertEquals(expected, number(Int256.of(generic)));
                }
            }
        }
    }

    @Test
    void truncatesBothSignsAroundFractionalAndIntegralRangeBoundaries() {
        for (int bit : new int[] {0, 1, 63, 64, 127, 128, 191, 192, 254, 255, 256}) {
            BigInteger center = ONE.shiftLeft(bit + 1);
            for (int delta : new int[] {-3, -2, -1, 0, 1, 2, 3}) {
                BigInteger magnitude = center.add(BigInteger.valueOf(delta));
                if (magnitude.signum() <= 0) {
                    continue;
                }
                assertDyadicConversion(magnitude, -1);
            }
        }
        BigInteger exactMaximum = MAXIMUM.shiftLeft(100);
        for (BigInteger magnitude : List.of(exactMaximum.subtract(ONE), exactMaximum, exactMaximum.add(ONE),
                ONE.shiftLeft(355).subtract(ONE), ONE.shiftLeft(355), ONE.shiftLeft(355).add(ONE))) {
            assertDyadicConversion(magnitude, -100);
        }
    }

    @Test
    void convertsArbitraryPrecisionsAndAlignmentsAgainstAnIntegerReference() {
        Random random = new Random(0xB1256C057L);
        for (int precision : new int[] {2, 24, 53, 64, 65, 127, 128, 129, 237, 255, 256, 257, 511, 512, 513, 1024}) {
            BigInteger magnitude = new BigInteger(precision, random).setBit(precision - 1);
            for (int scale : new int[] {-precision - 1, -precision, -precision + 1, -255, -192, -128,
                    -65, -64, -63, -1, 0, 1, 63, 64, 65, 127, 128, 191, 192, 254, 255}) {
                assertDyadicConversion(magnitude, scale);
            }
        }
        for (int i = 0; i < 1500; i++) {
            int precision = 2 + random.nextInt(1023);
            BigInteger magnitude = new BigInteger(precision, random).setBit(precision - 1);
            assertDyadicConversion(magnitude, random.nextInt(1601) - 1200);
        }
    }

    @Test
    void convertsSubnormalSourcesToZeroWithoutReadingTheirSignificands() {
        for (int precision : new int[] {2, 53, 237, 1024, Integer.MAX_VALUE}) {
            for (int emin : new int[] {Integer.MIN_VALUE, -262142, -1, 0}) {
                Binary.Format format = new Binary.Format(precision, emin, 0);
                for (boolean negative : new boolean[] {false, true}) {
                    Binary<?> source = binary(format, negative, precision - 1, (long) emin - precision + 1,
                            index -> { throw new AssertionError("Unexpected significand read: " + index); });
                    assertEquals(Int256.ZERO, Int256.of(source));
                }
            }
        }
    }

    @Test
    void handlesExtremeSourceFormatsAndReadsOnlyTheIntegerWindow() {
        int precision = Integer.MAX_VALUE;
        Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (int exponent : new int[] {Integer.MIN_VALUE, -1, 0, 63, 64, 127, 128, 191, 192, 254, 255,
                Integer.MAX_VALUE}) {
            long scale = (long) exponent - precision + 1;
            int[] bits = {precision - 1, precision - 2, 0};
            int groups = (int) ((precision + 63L) / 64);
            for (boolean negative : new boolean[] {false, true}) {
                int[] reads = {0};
                Binary<?> source = binary(format, negative, precision, scale, index -> {
                    assertTrue(index >= 0 && index < groups);
                    assertTrue(scale + (groups - 1L - index) * 64 > -64);
                    reads[0]++;
                    long word = 0;
                    for (int bit : bits) {
                        if (bit / 64 == groups - 1 - index) {
                            word |= 1L << (bit & 63);
                        }
                    }
                    return word;
                });
                BigInteger expected;
                if (exponent < 0) {
                    expected = BigInteger.ZERO;
                } else if (exponent >= 255) {
                    expected = negative ? MINIMUM : MAXIMUM;
                } else {
                    expected = ONE.shiftLeft(exponent);
                    if (exponent > 0) {
                        expected = expected.setBit(exponent - 1);
                    }
                    if (negative) {
                        expected = expected.negate();
                    }
                }
                assertEquals(expected, number(Int256.of(source)));
                assertTrue(reads[0] <= 5, "significand reads=" + reads[0]);
                if (exponent < 0 || exponent >= 255) {
                    assertEquals(0, reads[0]);
                }
            }
        }
    }

    @Test
    void roundTripsExactlyRepresentableIntegersThroughConcreteAndGenericSources() {
        Random random = new Random(0xE7AC7256L);
        for (int i = 0; i < 300; i++) {
            BigInteger magnitude = new BigInteger(237, random);
            BigInteger signed = random.nextBoolean() ? magnitude.negate() : magnitude;
            Int256 value = Int256.parseInt(signed.toString());
            Binary256 specialized = Binary256.of(value);
            Binary256 generic = Binary256.of(integer(256, signed));
            assertArrayEquals(specialized.toBytes(), generic.toBytes());
            assertEquals(value, Int256.of(specialized));
            assertEquals(value, Int256.of(generic));
        }
    }

    private static void assertIntegerConversion(int width, BigInteger value) {
        BigInteger source = wrap(value, width);
        assertEquals(wrap(source, 256), number(Int256.of(integer(width, source))), "source width=" + width);
    }

    private static void assertDoubleConversion(double value) {
        Binary256 source = Binary256.of(value);
        byte[] before = source.toBytes();
        assertEquals(Int256.of(value), Int256.of(source), "double=" + value);
        assertArrayEquals(before, source.toBytes());
    }

    private static void assertDyadicConversion(BigInteger magnitude, int scale) {
        int precision = Math.max(2, magnitude.bitLength());
        int padding = precision - magnitude.bitLength();
        BigInteger significand = magnitude.shiftLeft(padding);
        Binary.Format format = new Binary.Format(precision, -1000000, 1000000);
        for (boolean negative : new boolean[] {false, true}) {
            BigInteger expected = truncated(magnitude, scale, negative);
            int groups = (int) ((precision + 63L) / 64);
            Binary<?> source = binary(format, negative, precision, scale - (long) padding,
                    index -> significand.shiftRight((groups - 1 - index) * 64).longValue());
            assertEquals(expected, number(Int256.of(source)),
                    "precision=" + precision + " scale=" + scale + " negative=" + negative);
        }
    }

    private static BigInteger truncated(BigInteger magnitude, int scale, boolean negative) {
        BigInteger integer = scale >= 0 ? magnitude.shiftLeft(scale) : magnitude.shiftRight(-scale);
        return (negative ? integer.negate() : integer).max(MINIMUM).min(MAXIMUM);
    }

    private static BigInteger number(Int256 value) {
        return new BigInteger(value.toBytes());
    }

    private static BigInteger wrap(BigInteger value, int width) {
        BigInteger modulus = ONE.shiftLeft(width), raw = value.mod(modulus);
        return raw.testBit(width - 1) ? raw.subtract(modulus) : raw;
    }

    private static Int<?> integer(int width, BigInteger value) {
        BigInteger signed = wrap(value, width), raw = signed.mod(ONE.shiftLeft(width));
        int groups = width / 64;
        return (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> new Int.Format(width);
                    case "isNegative" -> signed.signum() < 0;
                    case "negate" -> integer(width, signed.negate());
                    case "bitLength" -> signed.bitLength();
                    case "bitCount" -> raw.bitCount();
                    case "testBit" -> {
                        int index = (int) arguments[0];
                        assertTrue(index >= 0 && index < width);
                        yield raw.testBit(index);
                    }
                    case "getLong" -> {
                        int index = (int) arguments[0];
                        assertTrue(index >= 0 && index < groups);
                        yield raw.shiftRight((groups - 1 - index) * 64).longValue();
                    }
                    default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                });
    }

    /** 只提供转换所需的数值观察方法，其他操作调用即失败。 */
    private static Binary<?> binary(Binary.Format format, boolean negative, int magnitudeLength, long scale,
            IntToLongFunction words) {
        boolean normal = magnitudeLength == format.precision();
        long exponent = scale + magnitudeLength - 1;
        if (normal ? exponent < format.minExponent() || exponent > format.maxExponent()
                : scale != (long) format.minExponent() - format.precision() + 1) {
            throw new IllegalArgumentException("Invalid Binary decomposition");
        }
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> format;
                    case "isNaN", "isInfinite", "isZero" -> false;
                    case "signBit" -> negative;
                    case "isNormal" -> normal;
                    case "scaleExponent" -> scale;
                    case "getSignificandLong" -> words.applyAsLong((int) arguments[0]);
                    default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                });
    }
}
