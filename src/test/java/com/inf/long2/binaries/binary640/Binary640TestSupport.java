package com.inf.long2.binaries.binary640;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Random;

import com.inf.long2.Binary;
import com.inf.long2.binaries.Binary640;

final class Binary640TestSupport {
    static final BigInteger ONE = BigInteger.ONE;

    private Binary640TestSupport() {
    }

    static void assertRaw(Binary640 expected, Binary640 actual) {
        assertEquals(Binary640Reference.raw(expected), Binary640Reference.raw(actual));
    }

    static Binary640 dyadic(boolean negative, BigInteger magnitude, long scale) {
        return Binary640Reference.round(negative, magnitude, ONE, scale);
    }

    static Binary640 random(Random random, boolean wide) {
        int[] edges = {0, 1, 2, 8388606, 8388607, 8388608, 16777213, 16777214};
        int exponent = wide ? edges[random.nextInt(edges.length)] : 8388607 + random.nextInt(1601) - 800;
        BigInteger bits = new BigInteger(615, random).or(BigInteger.valueOf(exponent).shiftLeft(615));
        return Binary640Reference.fromRaw(random.nextBoolean() ? bits.setBit(639) : bits);
    }

    static Binary<?> source(boolean negative, BigInteger magnitude, long scale) {
        if (magnitude.bitLength() == 1) {
            magnitude = magnitude.shiftLeft(1);
            scale--;
        }
        BigInteger m = magnitude;
        long q = scale;
        int precision = Math.max(2, magnitude.bitLength());
        int exponent = Math.toIntExact(scale + precision - 1L);
        Binary.Format format = new Binary.Format(precision, Math.min(0, exponent), Math.max(0, exponent));
        int groups = (precision + 63) / 64;
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "format" -> format;
                    case "signBit" -> negative;
                    case "isNaN", "isInfinite" -> false;
                    case "isFinite" -> true;
                    case "isZero" -> m.signum() == 0;
                    case "isNormal" -> m.bitLength() == precision;
                    case "isSubnormal" -> m.signum() != 0 && m.bitLength() < precision;
                    case "scaleExponent" -> q;
                    case "getSignificandLong" -> {
                        int index = (int) args[0];
                        if (index < 0 || index >= groups) {
                            throw new IndexOutOfBoundsException(index);
                        }
                        yield m.shiftRight((groups - 1 - index) * 64).longValue();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
