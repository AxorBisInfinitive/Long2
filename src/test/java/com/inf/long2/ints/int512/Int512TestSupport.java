package com.inf.long2.ints.int512;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Random;

import com.inf.long2.Int;
import com.inf.long2.ints.Int512;

final class Int512TestSupport {
    static final BigInteger ONE = BigInteger.ONE;
    static final BigInteger MODULUS = ONE.shiftLeft(512);
    static final BigInteger MINIMUM = ONE.shiftLeft(511).negate();
    static final BigInteger MAXIMUM = ONE.shiftLeft(511).subtract(ONE);

    private Int512TestSupport() {
    }

    static BigInteger wrap(BigInteger value, int width) {
        BigInteger modulus = ONE.shiftLeft(width), raw = value.mod(modulus);
        return raw.testBit(width - 1) ? raw.subtract(modulus) : raw;
    }

    static byte[] bytes(BigInteger value) {
        byte[] result = new byte[64];
        for (int index = 0; index < result.length; index++) {
            result[63 - index] = value.shiftRight(index * 8).byteValue();
        }
        return result;
    }

    static Int512 from(BigInteger value) {
        return Int512.of(bytes(value));
    }

    static BigInteger number(Int512 value) {
        return new BigInteger(value.toBytes());
    }

    static void assertNumber(BigInteger expected, Int512 actual) {
        assertEquals(wrap(expected, 512), number(actual));
    }

    static Int512 random(Random random) {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Int512.of(bytes);
    }

    static Int<?> integer(int width, BigInteger value) {
        BigInteger signed = wrap(value, width);
        int groups = width / 64;
        return (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> new Int.Format(width);
                    case "isNegative" -> signed.signum() < 0;
                    case "getLong" -> {
                        int index = (int) arguments[0];
                        assertTrue(index >= 0 && index < groups);
                        yield signed.shiftRight((groups - 1 - index) * 64).longValue();
                    }
                    default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                });
    }
}
