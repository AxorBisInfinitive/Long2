package com.inf.long2.ints;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;
import java.util.function.IntToLongFunction;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.binaries.binary1280.Binary1280;
import com.inf.long2.binaries.binary256.Binary256;
import com.inf.long2.binaries.binary640.Binary640;

/** 三种目标位宽共用精确数值参考和边界用例。 */
public abstract class IntMathTestSupport<T extends Int<T>> {
    protected abstract T parse(String value);

    protected abstract T abs(T value);

    protected abstract T max(T a, T b);

    protected abstract T min(T a, T b);

    protected abstract T ofFloor(double value);

    protected abstract T ofFloor(Binary<?> value);

    private int width() {
        return ofFloor(0.0).format().bitWidth();
    }

    private BigInteger minimum() {
        return BigInteger.ONE.shiftLeft(width() - 1).negate();
    }

    private BigInteger maximum() {
        return minimum().negate().subtract(BigInteger.ONE);
    }

    private BigInteger clamp(BigInteger value) {
        return value.max(minimum()).min(maximum());
    }

    private BigInteger number(T value) {
        return new BigInteger(value.toBytes());
    }

    @Test
    protected void handlesAbsoluteValueExtremesAndReusesNonnegativeInstances() {
        T zero = parse("0"), one = parse("1"), positive = parse(maximum().toString());
        assertSame(zero, abs(zero));
        assertSame(one, abs(one));
        assertSame(positive, abs(positive));
        assertEquals(BigInteger.ONE, number(abs(parse("-1"))));
        assertEquals(minimum(), number(abs(parse(minimum().toString()))));
        assertThrows(NullPointerException.class, () -> abs(null));
    }

    @Test
    protected void selectsOriginalArgumentsUsingSignedOrdering() {
        List<BigInteger> values = List.of(minimum(), minimum().add(BigInteger.ONE), BigInteger.valueOf(-1),
                BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(64),
                BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE), maximum());
        for (BigInteger a : values) {
            for (BigInteger b : values) {
                T first = parse(a.toString()), second = parse(b.toString());
                byte[] beforeFirst = first.toBytes(), beforeSecond = second.toBytes();
                assertSame(a.compareTo(b) >= 0 ? first : second, max(first, second));
                assertSame(a.compareTo(b) <= 0 ? first : second, min(first, second));
                assertArrayEquals(beforeFirst, first.toBytes());
                assertArrayEquals(beforeSecond, second.toBytes());
            }
        }
        T zero = parse("0");
        assertThrows(NullPointerException.class, () -> max(null, zero));
        assertThrows(NullPointerException.class, () -> max(zero, null));
        assertThrows(NullPointerException.class, () -> max(null, null));
        assertThrows(NullPointerException.class, () -> min(null, zero));
        assertThrows(NullPointerException.class, () -> min(zero, null));
        assertThrows(NullPointerException.class, () -> min(null, null));
    }

    @Test
    protected void matchesRandomSignedAbsoluteValuesAndSelections() {
        Random random = new Random(0xA85A117L + width());
        BigInteger modulus = BigInteger.ONE.shiftLeft(width());
        for (int index = 0; index < 400; index++) {
            BigInteger a = new BigInteger(width(), random).add(minimum());
            BigInteger b = new BigInteger(width(), random).add(minimum());
            T first = parse(a.toString()), second = parse(b.toString());
            BigInteger absolute = a.abs();
            if (absolute.compareTo(maximum()) > 0) {
                absolute = absolute.subtract(modulus);
            }
            assertEquals(absolute, number(abs(first)));
            assertSame(a.compareTo(b) >= 0 ? first : second, max(first, second));
            assertSame(a.compareTo(b) <= 0 ? first : second, min(first, second));
        }
    }

    @Test
    protected void floorsDoubleSpecialValuesAndIntegerNeighbors() {
        for (double value : new double[] {0.0, -0.0, Double.NaN,
                Double.longBitsToDouble(0xfff8_0000_0000_0001L), Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -Double.MIN_VALUE,
                Double.MIN_NORMAL, -Double.MIN_NORMAL, Double.MAX_VALUE, -Double.MAX_VALUE,
                1.75, -1.75, 2.0, -2.0}) {
            assertDouble(value);
        }
        for (int exponent : new int[] {0, 1, 31, 52, 53, 63, 64, 127, width() - 2, width() - 1, 1023}) {
            double power = Math.scalb(1.0, exponent);
            for (double value : new double[] {power, -power, Math.nextUp(power), Math.nextDown(power),
                    Math.nextUp(-power), Math.nextDown(-power)}) {
                assertDouble(value);
            }
        }
        assertThrows(NullPointerException.class, () -> ofFloor((Binary<?>) null));
    }

    @Test
    protected void floorsRandomDoublesAgainstExactDecimalValues() {
        Random random = new Random(0xF100D0B1L + width());
        for (int index = 0; index < 600; index++) {
            assertDouble(Double.longBitsToDouble(random.nextLong()));
        }
    }

    private void assertDouble(double value) {
        BigInteger expected;
        if (Double.isNaN(value)) {
            expected = BigInteger.ZERO;
        } else if (Double.isInfinite(value)) {
            expected = value < 0 ? minimum() : maximum();
        } else {
            expected = clamp(new BigDecimal(value).setScale(0, RoundingMode.FLOOR).toBigIntegerExact());
        }
        assertEquals(expected, number(ofFloor(value)), "double=" + value);
        for (Binary<?> source : List.of(Binary256.of(value), Binary640.of(value), Binary1280.of(value))) {
            byte[] before = source.toBytes();
            assertEquals(expected, number(ofFloor(source)), source.getClass().getSimpleName() + " double=" + value);
            assertArrayEquals(before, source.toBytes());
        }
    }

    @Test
    protected void floorsConcreteBinarySubnormalsAndClampsAtWideIntegerBoundaries() {
        for (Binary<?> source : List.of(Binary256.MIN_VALUE, Binary640.MIN_VALUE, Binary1280.MIN_VALUE)) {
            assertEquals(BigInteger.ZERO, number(ofFloor(source)));
        }
        for (Binary<?> source : List.of(Binary256.MIN_VALUE.negate(), Binary640.MIN_VALUE.negate(),
                Binary1280.MIN_VALUE.negate())) {
            assertEquals(BigInteger.valueOf(-1), number(ofFloor(source)));
        }
        // Binary1280 可精确保存所有目标位宽在下界附近的半整数。
        Binary1280 boundary = Binary1280.scalb(Binary1280.ONE, width() - 1);
        Binary1280 half = Binary1280.of(0.5);
        assertEquals(minimum(), number(ofFloor(boundary.negate())));
        assertEquals(minimum(), number(ofFloor(boundary.negate().subtract(half))));
        assertEquals(minimum(), number(ofFloor(boundary.negate().add(half))));
        assertEquals(minimum().add(BigInteger.ONE),
                number(ofFloor(boundary.negate().add(Binary1280.of(1.5)))));
        assertEquals(maximum(), number(ofFloor(boundary.subtract(half))));
        assertEquals(maximum(), number(ofFloor(boundary.add(half))));
    }

    @Test
    protected void detectsFractionalBitsAcrossEveryWordBoundary() {
        for (int discarded : new int[] {1, 2, 63, 64, 65, 127, 128, 129, 255, 256, 257, 1024}) {
            BigInteger integer = BigInteger.ONE.shiftLeft(discarded);
            assertDyadic(integer, -discarded);
            for (int bit : new int[] {0, Math.max(0, discarded - 1), 63, 64, 65}) {
                if (bit < discarded) {
                    assertDyadic(integer.setBit(bit), -discarded);
                }
            }
        }
        BigInteger boundary = BigInteger.ONE.shiftLeft(width());
        for (int delta = -3; delta <= 3; delta++) {
            assertDyadic(boundary.add(BigInteger.valueOf(delta)), -1);
        }
    }

    @Test
    protected void floorsArbitraryBinaryPrecisionsAgainstSignedIntegerShifts() {
        Random random = new Random(0xB1F100L + width());
        for (int precision : new int[] {2, 24, 53, 63, 64, 65, 127, 128, 129, 237, 616, 1252, 4097}) {
            BigInteger magnitude = new BigInteger(precision, random).setBit(precision - 1);
            for (int scale : new int[] {-precision - 1, -precision + 1, -129, -65, -64, -63, -1,
                    0, 1, width() - precision - 1, width() - precision, width() - precision + 1}) {
                assertDyadic(magnitude, scale);
            }
        }
        for (int index = 0; index < 500; index++) {
            int precision = 2 + random.nextInt(1600);
            BigInteger magnitude = new BigInteger(precision, random).setBit(precision - 1);
            assertDyadic(magnitude, random.nextInt(2401) - 1600);
        }
    }

    private void assertDyadic(BigInteger magnitude, int scale) {
        int precision = Math.max(2, magnitude.bitLength());
        int padding = precision - magnitude.bitLength();
        BigInteger significand = magnitude.shiftLeft(padding);
        Binary.Format format = new Binary.Format(precision, -1_000_000, 1_000_000);
        int groups = (int) ((precision + 63L) / 64);
        for (boolean negative : new boolean[] {false, true}) {
            BigInteger signed = negative ? magnitude.negate() : magnitude;
            BigInteger expected = clamp(scale >= 0 ? signed.shiftLeft(scale) : signed.shiftRight(-scale));
            Binary<?> source = binary(format, negative, true, scale - (long) padding,
                    index -> significand.shiftRight((groups - 1 - index) * 64).longValue());
            assertEquals(expected, number(ofFloor(source)),
                    "precision=" + precision + " scale=" + scale + " negative=" + negative);
        }
    }

    @Test
    protected void handlesExtremeFormatsAndSkipsIrrelevantSignificandReads() {
        int precision = Integer.MAX_VALUE;
        Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (boolean negative : new boolean[] {false, true}) {
            Binary<?> subnormal = binary(format, negative, false,
                    (long) format.minExponent() - precision + 1,
                    index -> { throw new AssertionError("Unexpected subnormal read"); });
            assertEquals(negative ? BigInteger.valueOf(-1) : BigInteger.ZERO, number(ofFloor(subnormal)));
            for (int exponent : new int[] {Integer.MIN_VALUE, -1, 0, 63, 64, width() - 2,
                    width() - 1, Integer.MAX_VALUE}) {
                int groups = (int) ((precision + 63L) / 64);
                int[] reads = {0};
                Binary<?> source = binary(format, negative, true, (long) exponent - precision + 1, index -> {
                    assertTrue(++reads[0] <= 24, "Unexpected scan of the source precision");
                    long top = index == 0 ? 1L << ((precision - 1) & 63) : 0;
                    return top | (index == groups - 1 ? 1L : 0);
                });
                BigInteger expected;
                if (exponent < 0) {
                    expected = negative ? BigInteger.valueOf(-1) : BigInteger.ZERO;
                } else if (exponent >= width() - 1) {
                    expected = negative ? minimum() : maximum();
                } else {
                    expected = BigInteger.ONE.shiftLeft(exponent);
                    if (negative) {
                        expected = expected.negate().subtract(BigInteger.ONE);
                    }
                }
                assertEquals(expected, number(ofFloor(source)), "exponent=" + exponent + " negative=" + negative);
            }
        }
    }

    private static Binary<?> binary(Binary.Format format, boolean negative, boolean normal, long scale,
            IntToLongFunction words) {
        int groups = (int) ((format.precision() + 63L) / 64);
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> format;
                    case "isNaN", "isInfinite", "isZero" -> false;
                    case "isFinite" -> true;
                    case "signBit" -> negative;
                    case "isNormal" -> normal;
                    case "scaleExponent" -> scale;
                    case "getSignificandLong" -> {
                        int index = (int) arguments[0];
                        assertTrue(index >= 0 && index < groups, "Invalid significand index " + index);
                        yield words.applyAsLong(index);
                    }
                    default -> throw new AssertionError("Unexpected source operation " + method.getName());
                });
    }
}
