package com.inf.long2.binaries.binary1280;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Binary.Comparison;
import com.inf.long2.BinaryMath;
import com.inf.long2.Int;
import com.inf.long2.binaries.Binary1280;
import com.inf.long2.binaries.Binary256;
import com.inf.long2.binaries.Binary640;
import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

import static com.inf.long2.binaries.Binary1280.*;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary1280Test {
    private static final int EMIN = -134217726;
    private static final int EMAX = 134217727;

    @Test
    void rawEncodingAndValueContracts() {
        assertEquals(1252, FORMAT.precision());
        assertEquals(EMIN, FORMAT.minExponent());
        assertEquals(EMAX, FORMAT.maxExponent());
        assertEquals(-134218977, NEGATIVE_ZERO.scaleExponent());
        assertEquals(1L << 35, ONE.getSignificandLong(0));
        assertEquals(1, MIN_VALUE.getSignificandLong(19));
        assertThrows(IndexOutOfBoundsException.class, () -> ONE.getSignificandLong(20));
        assertThrows(IllegalStateException.class, () -> NaN.getSignificandLong(0));
        assertThrows(IllegalStateException.class, POSITIVE_INFINITY::scaleExponent);
        assertThrows(IllegalArgumentException.class, () -> of(new byte[159]));
        assertThrows(IllegalArgumentException.class, () -> of(new byte[161]));
        assertThrows(NullPointerException.class, () -> of((byte[]) null));
        assertTrue(BinaryMath.class.isAssignableFrom(Binary1280.class));

        for (int i = 0; i < 1280; i++) {
            BigInteger pattern = BigInteger.ONE.shiftLeft(i);
            assertEquals(pattern, raw(fromRaw(pattern)));
        }
        BigInteger signaling = raw(NEGATIVE_INFINITY).setBit(1199).setBit(3);
        Binary1280 value = fromRaw(signaling);
        assertEquals(signaling, raw(value));
        assertTrue(value.signBit());
        assertEquals(NaN, value);
        assertEquals(NaN.hashCode(), value.hashCode());
        assertEquals(Comparison.UNORDERED, value.compareNumerically(value));

        byte[] bytes = value.toBytes();
        Binary1280 restored = of(bytes);
        bytes[0] = 0;
        assertEquals(signaling, raw(restored));
        restored.toBytes()[159] = 0;
        assertEquals(signaling, raw(restored));
        assertEquals(Comparison.EQUAL, NEGATIVE_ZERO.compareNumerically(ZERO));
        assertNotEquals(NEGATIVE_ZERO, ZERO);
        assertTrue(NEGATIVE_ZERO.compareTo(ZERO) < 0);
        assertTrue(POSITIVE_INFINITY.compareTo(NaN) < 0);
    }

    @Test
    void neighborsAndQuantizationBoundaries() {
        assertEquals(MIN_VALUE, ZERO.nextUp());
        assertEquals(NEGATIVE_ZERO, MIN_VALUE.negate().nextUp());
        assertEquals(ZERO, MIN_VALUE.nextDown());
        assertEquals(MIN_VALUE.negate(), NEGATIVE_ZERO.nextDown());
        assertEquals(POSITIVE_INFINITY, MAX_VALUE.nextUp());
        assertEquals(MAX_VALUE, POSITIVE_INFINITY.nextDown());
        assertEquals(NEGATIVE_INFINITY, MAX_VALUE.negate().nextDown());
        assertEquals(MAX_VALUE.negate(), NEGATIVE_INFINITY.nextUp());
        assertEquals(MIN_VALUE, MIN_NORMAL.ulp());
        assertEquals(power(-1251), ONE.ulp());
        assertEquals(power(EMAX - 1251), MAX_VALUE.ulp());
        assertEquals(POSITIVE_INFINITY, NEGATIVE_INFINITY.ulp());

        Binary1280 threeMin = MIN_VALUE.multiply(of(3));
        Binary1280 twoMin = MIN_VALUE.nextUp();
        assertEquals(ZERO, MIN_VALUE.multiply(of(0.5)));
        assertEquals(NEGATIVE_ZERO, MIN_VALUE.negate().divide(of(2)));
        assertEquals(twoMin, threeMin.divide(of(2)));
        assertEquals(MIN_NORMAL, MIN_NORMAL.nextDown().add(MIN_VALUE));
        assertEquals(MIN_VALUE, MIN_NORMAL.subtract(MIN_NORMAL.nextDown()));
        assertEquals(MIN_NORMAL, MIN_NORMAL.multiply(ONE.nextDown()));
        assertEquals(MAX_VALUE, MAX_VALUE.add(power(EMAX - 1253)));
        assertEquals(POSITIVE_INFINITY, MAX_VALUE.add(power(EMAX - 1252)));
        assertEquals(ONE, ONE.add(power(-1252)));
        assertEquals(ONE.nextUp().nextUp(), ONE.nextUp().add(power(-1252)));
        assertEquals(ONE, ONE.subtract(power(-1253)));
        assertEquals(ONE.nextDown(), ONE.subtract(power(-1253).nextUp()));
        assertEquals(ONE, ONE.subtract(power(-1254).nextUp()));
        assertEquals(ONE, ONE.subtract(power(-1255)));
        assertEquals(ZERO, MAX_VALUE.remainder(threeMin));
        assertEquals(twoMin, MAX_VALUE.remainder(MIN_VALUE.multiply(of(7))));
        assertEquals(twoMin.negate(), MAX_VALUE.negate().remainder(MIN_VALUE.multiply(of(7))));
    }

    @Test
    void specialArithmeticAndNullArguments() {
        assertEquals(NaN, ZERO.multiply(POSITIVE_INFINITY));
        assertEquals(NaN, ZERO.divide(NEGATIVE_ZERO));
        assertEquals(NaN, POSITIVE_INFINITY.divide(NEGATIVE_INFINITY));
        assertEquals(NaN, NEGATIVE_INFINITY.add(POSITIVE_INFINITY));
        assertEquals(NaN, NEGATIVE_INFINITY.subtract(NEGATIVE_INFINITY));
        assertEquals(NEGATIVE_INFINITY, ONE.divide(NEGATIVE_ZERO));
        assertEquals(POSITIVE_INFINITY, NEGATIVE_INFINITY.divide(NEGATIVE_ZERO));
        assertEquals(NEGATIVE_ZERO, ONE.divide(NEGATIVE_INFINITY));
        assertEquals(NEGATIVE_ZERO, NEGATIVE_ZERO.add(NEGATIVE_ZERO));
        assertEquals(ZERO, NEGATIVE_ZERO.subtract(NEGATIVE_ZERO));
        assertEquals(ZERO, ONE.subtract(ONE));
        assertEquals(of(2), of(5).remainder(of(3)));
        assertEquals(NEGATIVE_ZERO, of(-6).remainder(of(3)));
        assertEquals(NEGATIVE_ZERO, NEGATIVE_ZERO.remainder(POSITIVE_INFINITY));
        assertEquals(NaN, ONE.remainder(ZERO));
        assertEquals(NaN, POSITIVE_INFINITY.remainder(ONE));
        assertThrows(NullPointerException.class, () -> NaN.add(null));
        assertThrows(NullPointerException.class, () -> ZERO.remainder(null));
    }

    @Test
    void arithmeticMatchesExactReference() {
        Random random = new Random(1280);
        for (int i = 0; i < 300; i++) {
            int exponent = switch (i % 3) {
                case 0 -> EMIN + random.nextInt(8);
                case 1 -> EMAX - random.nextInt(8);
                default -> random.nextInt(-1500, 1501);
            };
            BigInteger aFraction = new BigInteger(1251, random);
            BigInteger bFraction = new BigInteger(1251, random);
            if ((i & 7) == 0) {
                aFraction = aFraction.shiftRight(640).shiftLeft(640);
            }
            Binary1280 a = normal(random.nextBoolean(), exponent, aFraction);
            int otherExponent = Math.clamp(exponent + random.nextInt(-1260, 1261), EMIN, EMAX);
            Binary1280 b = i % 3 == 0 && (i & 7) == 1 ? fromRaw(bFraction.setBit(0))
                    : normal(random.nextBoolean(), otherExponent, bFraction);
            assertArrayEquals(add(a, b).toBytes(), a.add(b).toBytes());
            assertArrayEquals(add(a, b.negate()).toBytes(), a.subtract(b).toBytes());
            assertArrayEquals(multiply(a, b).toBytes(), a.multiply(b).toBytes());
            assertArrayEquals(divide(a, b).toBytes(), a.divide(b).toBytes());
            assertArrayEquals(remainder(a, b).toBytes(), a.remainder(b).toBytes());
        }
    }

    @Test
    void conversionsUseTheExactSourceAndTargetRanges() {
        for (Binary640 value : new Binary640[] {Binary640.MIN_VALUE, Binary640.MAX_VALUE,
                Binary640.NEGATIVE_ZERO, Binary640.NEGATIVE_INFINITY}) {
            assertEquals(value, Binary640.of(of(value)));
        }
        for (Binary256 value : new Binary256[] {Binary256.MIN_VALUE, Binary256.MAX_VALUE,
                Binary256.NEGATIVE_ZERO, Binary256.POSITIVE_INFINITY}) {
            assertEquals(value, Binary256.of(of(value)));
        }
        assertEquals(Int256.MIN_VALUE, Int256.of(of(Int256.MIN_VALUE)));
        assertEquals(Int512.MAX_VALUE, Int512.of(of(Int512.MAX_VALUE)));
        assertEquals(Int1024.MIN_VALUE, Int1024.of(of(Int1024.MIN_VALUE)));
        assertEquals(Int1024.MAX_VALUE, Int1024.of(of(Int1024.MAX_VALUE)));
        assertEquals(Long.MIN_VALUE, of(Long.MIN_VALUE).toLong());
        assertEquals(Integer.MAX_VALUE, of(Long.MAX_VALUE).toInt());
        assertEquals(Integer.MIN_VALUE, of(Long.MIN_VALUE).toInt());
        assertEquals(-1, of(-1.75).toInt());
        assertEquals(0, NaN.toLong());
        assertEquals(Long.MAX_VALUE, MAX_VALUE.toLong());

        for (double value : new double[] {Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE,
                -0.0, Double.NEGATIVE_INFINITY}) {
            assertEquals(Double.doubleToRawLongBits(value), Double.doubleToRawLongBits(of(value).toDouble()));
        }
        for (float value : new float[] {Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE,
                -0.0f, Float.POSITIVE_INFINITY}) {
            assertEquals(Float.floatToRawIntBits(value), Float.floatToRawIntBits(of(value).toFloat()));
        }
        assertEquals(0L, Double.doubleToRawLongBits(power(-1075).toDouble()));
        assertEquals(1L, Double.doubleToRawLongBits(power(-1075).nextUp().toDouble()));
        assertEquals(0x8000_0000, Float.floatToRawIntBits(power(-150).negate().toFloat()));
        assertEquals(Float.POSITIVE_INFINITY, power(128).toFloat());

        Binary1280 source = ONE.add(power(-237)).add(power(-617));
        assertEquals(Binary256.ONE.nextUp(), Binary256.of(source));
        assertEquals(Binary256.ONE, Binary256.of(Binary640.of(source)));
        Binary1280 single = ONE.add(power(-24)).add(power(-100));
        assertEquals(Math.nextUp(1.0f), single.toFloat());
        assertEquals(1.0f, (float) single.toDouble());
    }

    @Test
    void readsWideForeignSignificandsAndIntegersAtTheRoundingBoundary() {
        int precision = Integer.MAX_VALUE;
        AtomicInteger reads = new AtomicInteger();
        Binary<?> one = (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(),
                new Class<?>[] {Binary.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "format" -> new Binary.Format(precision, EMIN, EMAX);
                    case "signBit", "isNaN", "isInfinite", "isZero" -> false;
                    case "scaleExponent" -> 1L - precision;
                    case "getSignificandLong" -> {
                        assertTrue(reads.incrementAndGet() <= 21);
                        yield (int) args[0] == 0 ? 1L << 62 : 0L;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(ONE, of(one));

        BigInteger midpoint = BigInteger.ONE.shiftLeft(2047).setBit(795);
        for (BigInteger bits : new BigInteger[] {midpoint, midpoint.setBit(0)}) {
            Binary<?> finite = (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(),
                    new Class<?>[] {Binary.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "format" -> new Binary.Format(2048, 0, 1);
                        case "signBit", "isNaN", "isInfinite", "isZero" -> false;
                        case "scaleExponent" -> -2047L;
                        case "getSignificandLong" -> bits.shiftRight(64 * (31 - (int) args[0])).longValue();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            assertEquals(bits.testBit(0) ? ONE.nextUp() : ONE, of(finite));
        }

        BigInteger tie = BigInteger.ONE.shiftLeft(1252).add(BigInteger.ONE);
        for (BigInteger integer : new BigInteger[] {tie, tie.add(BigInteger.TWO), tie.negate(),
                tie.add(BigInteger.TWO).negate(), BigInteger.ONE.shiftLeft(1279).negate()}) {
            Int<?> source = (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(),
                    new Class<?>[] {Int.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "format" -> new Int.Format(1280);
                        case "isNegative" -> integer.signum() < 0;
                        case "bitLength" -> integer.bitLength();
                        case "getLong" -> integer.shiftRight(64 * (19 - (int) args[0])).longValue();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            assertEquals(round(integer.signum() < 0, integer.abs(), BigInteger.ONE, 0), of(source));
        }
    }
}
