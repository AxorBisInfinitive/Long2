package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640MultiplyWindowTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(615);
    private static final BigInteger FULL = ONE.shiftLeft(616).subtract(ONE);
    private static final BigInteger WORD_MASK = ONE.shiftLeft(64).subtract(ONE);
    private static final BigInteger LOW_MASK = ONE.shiftLeft(576).subtract(ONE);

    private static void check(Binary640 a, Binary640 b) {
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        for (Binary640 x : List.of(a, a.negate())) {
            for (Binary640 y : List.of(b, b.negate())) {
                Binary640 expected = Binary640Reference.multiply(x, y);
                assertRaw(expected, x.multiply(y));
                assertRaw(expected, y.multiply(x));
            }
        }
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    private static BigInteger integer(long[] words) {
        BigInteger result = BigInteger.ZERO;
        for (int i = words.length - 1; i >= 0; i--) {
            result = result.shiftLeft(64).or(BigInteger.valueOf(words[i]).and(WORD_MASK));
        }
        return result;
    }

    @Test
    void checksShortSubnormalsAtEveryWidthAndDispatchCutoff() {
        for (int bits = 1; bits <= 615; bits++) {
            for (BigInteger m : List.of(ONE.shiftLeft(bits - 1).or(ONE), ONE.shiftLeft(bits).subtract(ONE))) {
                Binary640 a = Binary640Reference.fromRaw(m);
                for (int exponent : new int[] {-1, 0, 8388607}) {
                    check(a, dyadic(false, HIDDEN.add(ONE), exponent - 615L));
                    check(a, dyadic(false, FULL, exponent - 615L));
                }
            }
        }
    }

    @Test
    void preservesRoundingForShortSubnormalsNearTheNormalBoundary() {
        for (int bits : new int[] {1, 63, 64, 65, 127, 128, 129, 191, 192, 193, 255, 256, 257,
                319, 320, 321, 575, 576, 577, 614, 615}) {
            for (BigInteger m : List.of(ONE.shiftLeft(bits - 1).or(ONE), ONE.shiftLeft(bits).subtract(ONE))) {
                Binary640 a = Binary640Reference.fromRaw(m);
                for (int offset = -1; offset <= 1; offset++) {
                    long scale = -bits + offset;
                    check(a, dyadic(false, HIDDEN.add(ONE), scale));
                    check(a, dyadic(false, FULL, scale));
                }
            }
        }
        check(Binary640.MIN_NORMAL.nextDown(), Binary640.MAX_VALUE);
        check(Binary640.MIN_VALUE, Binary640.ONE);
        check(Binary640.MIN_VALUE, Binary640.of(0.5));
    }

    @Test
    void comparesTheCompressedWindowWithTheEntireIntegerProduct() throws ReflectiveOperationException {
        Class<?> math = Class.forName("com.inf.long2.binaries.Binary640Arithmetic");
        Method low, high;
        boolean compressed;
        try {
            low = math.getDeclaredMethod("multiplyLowWindow", Binary640.class, long.class, Binary640.class, long.class, long[].class);
            high = math.getDeclaredMethod("multiplyHighWindow", Binary640.class, long.class, Binary640.class, long.class, long[].class, long.class);
            compressed = true;
        } catch (NoSuchMethodException exception) {
            low = math.getDeclaredMethod("multiplyLowColumns", Binary640.class, long.class, Binary640.class, long.class, long[].class);
            high = math.getDeclaredMethod("multiplyHighColumns", Binary640.class, long.class, Binary640.class, long.class, long[].class, long.class);
            compressed = false;
        }
        low.setAccessible(true);
        high.setAccessible(true);
        Random random = new Random(0x6407101);
        boolean multipleCarry = false;
        for (int index = 0; index < 6500; index++) {
            BigInteger am, bm;
            if (index < 4096) {
                am = HIDDEN.or(ONE.shiftLeft(index / 64));
                bm = HIDDEN.or(ONE.shiftLeft(index % 64));
            } else if (index == 4096) {
                am = FULL;
                bm = FULL;
            } else {
                am = new BigInteger(615, random).or(HIDDEN).or(ONE.shiftLeft(random.nextInt(64)));
                bm = new BigInteger(615, random).or(HIDDEN).or(ONE.shiftLeft(random.nextInt(64)));
            }
            Binary640 a = dyadic(true, am, -615), b = dyadic(false, bm, -615);
            byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
            long[] product = new long[compressed ? 11 : 20];
            java.util.Arrays.fill(product, 0xa5a5_a5a5_a5a5_a5a5L);
            long atop = a.getSignificandLong(0), btop = b.getSignificandLong(0);
            long carry = (Long) low.invoke(null, a, atop, b, btop, product);
            multipleCarry |= carry > 1;
            high.invoke(null, a, atop, b, btop, product, carry);
            BigInteger exact = am.multiply(bm);
            assertNotEquals(BigInteger.ZERO, exact.and(LOW_MASK));
            BigInteger expected = exact.shiftRight(576).setBit(0);
            BigInteger actual = integer(product);
            if (!compressed) {
                assertEquals(exact, actual);
                actual = actual.shiftRight(576).setBit(0);
            }
            assertEquals(expected, actual);
            assertTrue(actual.bitLength() == 655 || actual.bitLength() == 656);
            assertArrayEquals(beforeA, a.toBytes());
            assertArrayEquals(beforeB, b.toBytes());
        }
        assertTrue(multipleCarry);
    }

    @Test
    void checksNormalWindowsAtAllRoundingAndExponentTransitions() {
        BigInteger[] patterns = {HIDDEN.add(ONE), FULL, HIDDEN.or(ONE.shiftLeft(63)),
                FULL.shiftRight(63).shiftLeft(63)};
        for (int sum : new int[] {-8389224, -8389223, -8389222, -8389221, -8388607, -8388606,
                -8388605, -1, 0, 1, 8388606, 8388607, 8388608}) {
            int ae = Math.floorDiv(sum, 2), be = sum - ae;
            for (BigInteger am : patterns) {
                for (BigInteger bm : patterns) check(dyadic(false, am, ae - 615L), dyadic(false, bm, be - 615L));
            }
        }
        Random random = new Random(0x6407102);
        for (int index = 0; index < 2500; index++) {
            int ae = random.nextInt(16777214) - 8388606, be = random.nextInt(16777214) - 8388606;
            BigInteger am = new BigInteger(615, random).or(HIDDEN).or(ONE);
            BigInteger bm = new BigInteger(615, random).or(HIDDEN).or(ONE);
            check(dyadic(false, am, ae - 615L), dyadic(false, bm, be - 615L));
        }
    }

    @Test
    void retainsTheOtherRoutesForClippedAndSpecialInputs() {
        Binary640[] values = {Binary640.ONE, Binary640.ONE.nextUp(), Binary640.MIN_NORMAL,
                Binary640.MIN_NORMAL.nextDown(), Binary640.MIN_VALUE, Binary640.MAX_VALUE};
        for (Binary640 a : values) for (Binary640 b : values) check(a, b);
        assertRaw(Binary640.NaN, Binary640.ZERO.multiply(Binary640.POSITIVE_INFINITY));
        assertRaw(Binary640.NaN, Binary640.NEGATIVE_INFINITY.multiply(Binary640.NEGATIVE_ZERO));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.NEGATIVE_ZERO.multiply(Binary640.ONE.nextUp()));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.MAX_VALUE.multiply(Binary640.NEGATIVE_INFINITY));
    }
}
