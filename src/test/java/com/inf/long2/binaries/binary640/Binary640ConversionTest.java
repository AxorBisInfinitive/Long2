package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.binaries.Binary256;
import com.inf.long2.binaries.Binary640;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;
import com.inf.long2.ints.Int1024;

class Binary640ConversionTest {
    @Test
    void preservesAllPrimitiveInputsExactlyIncludingSignedZeros() {
        for (long value : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertRaw(dyadic(value < 0, BigInteger.valueOf(value).abs(), 0), Binary640.of(value));
            assertEquals(value, Binary640.of(value).toLong());
        }
        Random random = new Random(12345);
        for (int i = 0; i < 2000; i++) {
            double d = Double.longBitsToDouble(random.nextLong());
            float f = Float.intBitsToFloat(random.nextInt());
            assertEquals(Double.doubleToLongBits(d), Double.doubleToLongBits(Binary640.of(d).toDouble()));
            assertEquals(Float.floatToIntBits(f), Float.floatToIntBits(Binary640.of(f).toFloat()));
        }
    }

    private static long primitive(Binary640 value, int p, int emin, int emax, int w) {
        var x = Binary640Reference.decode(value);
        long sign = x.negative() ? 1L << (p - 1 + w) : 0;
        long infinity = ((1L << w) - 1) << (p - 1);
        if (x.magnitude().signum() == 0) {
            return sign;
        }
        long exponent = x.scale() + x.magnitude().bitLength() - 1;
        if (exponent > emax) {
            return sign | infinity;
        }
        long quantum = Math.max(exponent - p + 1, emin - p + 1);
        int discarded = Math.toIntExact(quantum - x.scale());
        BigInteger kept;
        if (discarded > 0) {
            BigInteger[] qr = x.magnitude().divideAndRemainder(ONE.shiftLeft(discarded));
            kept = qr[0];
            int half = qr[1].shiftLeft(1).compareTo(ONE.shiftLeft(discarded));
            if (half > 0 || half == 0 && kept.testBit(0)) {
                kept = kept.add(ONE);
            }
        } else {
            kept = x.magnitude().shiftLeft(-discarded);
        }
        if (kept.signum() == 0) {
            return sign;
        }
        if (kept.bitLength() > p) {
            kept = kept.shiftRight(1);
            quantum++;
        }
        exponent = quantum + kept.bitLength() - 1;
        if (exponent > emax) {
            return sign | infinity;
        }
        long encoded = exponent < emin ? 0 : exponent + emax;
        return sign | encoded << (p - 1) | kept.and(ONE.shiftLeft(p - 1).subtract(ONE)).longValue();
    }

    @Test
    void roundsDirectlyToFloatAndDoubleAtTheirMidpointsAndRangeEdges() {
        Random random = new Random(12345);
        for (int i = 0; i < 500; i++) {
            Binary640 value = dyadic(random.nextBoolean(), new BigInteger(616, random).setBit(615), -1800 + random.nextInt(3000));
            assertEquals(primitive(value, 24, -126, 127, 8), Integer.toUnsignedLong(Float.floatToRawIntBits(value.toFloat())));
            assertEquals(primitive(value, 53, -1022, 1023, 11), Double.doubleToRawLongBits(value.toDouble()));
        }
        for (int p : new int[] {24, 53}) {
            for (int exponent : p == 24 ? new int[] {-150, -149, -127, -126, 0, 127} : new int[] {-1075, -1074, -1023, -1022, 0, 1023}) {
                BigInteger middle = ONE.shiftLeft(p + 2).add(BigInteger.valueOf(4));
                for (int delta : new int[] {-1, 0, 1}) {
                    Binary640 value = dyadic(false, middle.add(BigInteger.valueOf(delta)), exponent - p - 2L);
                    assertEquals(primitive(value, 24, -126, 127, 8), Integer.toUnsignedLong(Float.floatToRawIntBits(value.toFloat())));
                    assertEquals(primitive(value, 53, -1022, 1023, 11), Double.doubleToRawLongBits(value.toDouble()));
                }
            }
        }
        Binary640 midpoint = Binary640.ONE.add(Binary640.ONE.scalb(-24));
        Binary640 above = midpoint.add(Binary640.ONE.scalb(-100));
        assertNotEquals(Float.floatToRawIntBits(above.toFloat()), Float.floatToRawIntBits((float) above.toDouble()));
    }

    @Test
    void integratesWithBinary256WithoutChangingItsNumericalValues() {
        Random random = new Random(12345);
        for (int i = 0; i < 400; i++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            Binary256 source = Binary256.of(bytes);
            Binary640 wide = Binary640.of(source);
            Binary256 back = Binary256.of(wide);
            assertArrayEquals(source.isNaN() ? Binary256.NaN.toBytes() : bytes, back.toBytes());
        }
        for (Binary256 value : new Binary256[] {Binary256.MIN_VALUE, Binary256.MIN_NORMAL, Binary256.MAX_VALUE,
                Binary256.NEGATIVE_ZERO, Binary256.NEGATIVE_INFINITY}) {
            assertArrayEquals(value.toBytes(), Binary256.of(Binary640.of(value)).toBytes());
        }
        Binary640 midpoint = Binary640.ONE.add(Binary640.ONE.scalb(-237));
        assertEquals(Binary256.ONE, Binary256.of(midpoint));
        assertEquals(Binary256.ONE.nextUp(), Binary256.of(midpoint.add(Binary640.ONE.scalb(-600))));
        assertEquals(Binary256.ZERO, Binary256.of(Binary640.MIN_VALUE));
        assertEquals(Binary256.POSITIVE_INFINITY, Binary256.of(Binary640.MAX_VALUE));
    }

    private static Int<?> integer(int width, BigInteger value) {
        byte[] bytes = new byte[width / 8];
        Arrays.fill(bytes, value.signum() < 0 ? (byte) -1 : 0);
        byte[] raw = value.toByteArray();
        int count = Math.min(raw.length, bytes.length);
        System.arraycopy(raw, raw.length - count, bytes, bytes.length - count, count);
        return switch (width) {
            case 256 -> Int256.of(bytes);
            case 512 -> Int512.of(bytes);
            default -> Int1024.of(bytes);
        };
    }

    @Test
    void readsSignedIntegersIncludingMinimumValuesAndAllLowCarryPositions() {
        Random random = new Random(12345);
        for (int width : new int[] {256, 512, 1024}) {
            BigInteger minimum = ONE.shiftLeft(width - 1).negate();
            BigInteger maximum = ONE.shiftLeft(width - 1).subtract(ONE);
            for (BigInteger value : new BigInteger[] {BigInteger.ZERO, ONE, ONE.negate(), minimum, minimum.add(ONE), maximum}) {
                assertRaw(dyadic(value.signum() < 0, value.abs(), 0), Binary640.of(integer(width, value)));
            }
            for (int i = 0; i < 200; i++) {
                BigInteger value = new BigInteger(width - 1, random);
                if (random.nextBoolean()) {
                    value = value.negate();
                }
                assertRaw(dyadic(value.signum() < 0, value.abs(), 0), Binary640.of(integer(width, value)));
            }
        }
        for (int bit = 1; bit < 1024; bit++) {
            BigInteger value = ONE.shiftLeft(bit).negate();
            assertRaw(dyadic(true, value.abs(), 0), Binary640.of(integer(1024, value)));
        }
        for (int drop = 1; drop <= 407; drop++) {
            BigInteger midpoint = ONE.shiftLeft(615).add(ONE).shiftLeft(drop).add(ONE.shiftLeft(drop-1));
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger value = midpoint.add(BigInteger.valueOf(delta));
                assertRaw(dyadic(false, value, 0), Binary640.of(integer(1024, value)));
                assertRaw(dyadic(true, value, 0), Binary640.of(integer(1024, value.negate())));
            }
        }
    }

    @Test
    void truncatesAndClampsPrimitiveAndWideIntegerOutputsIndependently() {
        for (int width : new int[] {32, 64, 256, 512, 1024}) {
            BigInteger bound = ONE.shiftLeft(width-1);
            for (BigInteger integer : new BigInteger[] {bound.negate().subtract(ONE), bound.negate(), bound.negate().add(ONE),
                    bound.subtract(ONE), bound, bound.add(ONE)}) {
                Binary640 value = dyadic(integer.signum() < 0, integer.abs(), 0);
                var x = Binary640Reference.decode(value);
                BigInteger truncated = x.magnitude().shiftLeft(Math.toIntExact(x.scale()));
                if (x.negative()) {
                    truncated = truncated.negate();
                }
                BigInteger expected = truncated.max(bound.negate()).min(bound.subtract(ONE));
                switch (width) {
                    case 32 -> assertEquals(expected.intValue(), value.toInt());
                    case 64 -> assertEquals(expected.longValue(), value.toLong());
                    case 256 -> assertEquals(expected.toString(), Int256.of(value).toString());
                    case 512 -> assertEquals(expected.toString(), Int512.of(value).toString());
                    default -> assertEquals(expected.toString(), Int1024.of(value).toString());
                }
            }
        }
        assertEquals(Integer.MAX_VALUE, Binary640.of(Long.MAX_VALUE).toInt());
        assertEquals(Integer.MIN_VALUE, Binary640.of(Long.MIN_VALUE).toInt());
        assertEquals(0, Binary640.NaN.toLong());
        assertEquals(Long.MIN_VALUE, Binary640.NEGATIVE_INFINITY.toLong());
        assertEquals(0, Binary640.MIN_VALUE.toInt());
        assertEquals(-3, Binary640.of(-3.75).toLong());
    }

    @Test
    void readsAnExtremePrecisionSourceThroughABoundedWindow() {
        int precision = Integer.MAX_VALUE;
        AtomicInteger reads = new AtomicInteger();
        Binary<?> source = (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "format" -> new Binary.Format(precision, -8388606, 8388607);
                    case "signBit", "isNaN", "isInfinite", "isZero" -> false;
                    case "scaleExponent" -> 1L - precision;
                    case "getSignificandLong" -> {
                        assertTrue(reads.incrementAndGet() < 15);
                        yield (int) args[0] == 0 ? 1L << 62 : 0L;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertRaw(Binary640.ONE, Binary640.of(source));
    }

    @Test
    void readsForeignSubnormalSignificandsWithLeadingZeroWords() {
        for (int precision : new int[] {617, 640, 641, 1024, 4096}) {
            for (int length : new int[] {1, 39, 64, 65, 615, 616}) {
                BigInteger magnitude = ONE.shiftLeft(length - 1).or(ONE);
                int groups = (precision + 63) / 64;
                Binary<?> source = (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "format" -> new Binary.Format(precision, 0, 100);
                            case "signBit" -> true;
                            case "isNaN", "isInfinite", "isZero", "isNormal" -> false;
                            case "isFinite", "isSubnormal" -> true;
                            case "scaleExponent" -> 1L - precision;
                            case "getSignificandLong" -> magnitude.shiftRight((groups - 1 - (int) args[0]) * 64).longValue();
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
                assertRaw(dyadic(true, magnitude, 1L - precision), Binary640.of(source));
            }
        }
    }

    @Test
    void keepsIntegerStickyBelowGuardWhenTheLengthBoundHasAnExtraBit() {
        BigInteger power = ONE.shiftLeft(620);
        for (int tail : new int[] {1, 2, 15, 16, 17}) {
            BigInteger value = power.add(BigInteger.valueOf(tail));
            assertRaw(dyadic(false, value, 0), Binary640.of(integer(1024, value)));
            assertRaw(dyadic(true, value, 0), Binary640.of(integer(1024, value.negate())));
        }
    }
}
