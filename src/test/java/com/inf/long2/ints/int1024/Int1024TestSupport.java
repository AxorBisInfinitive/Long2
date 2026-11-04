package com.inf.long2.ints.int1024;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Random;

import com.inf.long2.Int;
import com.inf.long2.ints.Int1024;

final class Int1024TestSupport {
    static final BigInteger ONE = BigInteger.ONE;
    static final BigInteger MODULUS = ONE.shiftLeft(1024);
    static final BigInteger MINIMUM = ONE.shiftLeft(1023).negate();
    static final BigInteger MAXIMUM = ONE.shiftLeft(1023).subtract(ONE);

    private Int1024TestSupport() {
    }

    static BigInteger wrap(BigInteger value, int width) {
        BigInteger modulus = ONE.shiftLeft(width), raw = value.mod(modulus);
        return raw.testBit(width - 1) ? raw.subtract(modulus) : raw;
    }

    static byte[] bytes(BigInteger value) {
        byte[] result = new byte[128];
        for (int index = 0; index < result.length; index++) {
            result[127 - index] = value.shiftRight(index * 8).byteValue();
        }
        return result;
    }

    static Int1024 from(BigInteger value) {
        return Int1024.of(bytes(value));
    }

    static BigInteger number(Int1024 value) {
        return new BigInteger(value.toBytes());
    }

    static void assertNumber(BigInteger expected, Int1024 actual) {
        assertEquals(wrap(expected, 1024), number(actual));
    }

    static Int1024 random(Random random) {
        byte[] bytes = new byte[128];
        random.nextBytes(bytes);
        return Int1024.of(bytes);
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
