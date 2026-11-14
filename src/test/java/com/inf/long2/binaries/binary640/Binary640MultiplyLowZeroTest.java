package com.inf.long2.binaries.binary640;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640MultiplyLowZeroTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(615);
    private static final BigInteger FULL = ONE.shiftLeft(616).subtract(ONE);
    private static final BigInteger WORD_MASK = ONE.shiftLeft(64).subtract(ONE);

    private static Binary640 input(BigInteger magnitude) {
        return Binary640Reference.fromRaw(magnitude.bitLength() < 616 ? magnitude
                : magnitude.clearBit(615).or(BigInteger.valueOf(8388607).shiftLeft(615)));
    }

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

    private static BigInteger window(int low, int high, int pattern, Random random) {
        int bits = (high - low) * 64 + (high == 9 ? 40 : 64);
        BigInteger m = pattern == 0 ? ONE.shiftLeft(bits).subtract(ONE)
                : pattern == 1 ? ONE.shiftLeft(bits - 1).or(ONE)
                : new BigInteger(bits, random).setBit(bits - 1).setBit(0);
        return m.shiftLeft(low * 64);
    }

    @Test
    void checksCompleteTrimmedProductsForEveryOccupiedGroupWindow() throws ReflectiveOperationException {
        Class<?> math = Class.forName("com.inf.long2.binaries.binary640.Binary640Arithmetic");
        Method zeros = math.getDeclaredMethod("lowZeroGroups", Binary640.class);
        Method kernel = math.getDeclaredMethod("multiplyTrimmed", Binary640.class, long.class, int.class,
                Binary640.class, long.class, int.class);
        zeros.setAccessible(true);
        kernel.setAccessible(true);
        Random random = new Random(0x6402070);
        for (int az = 0; az < 10; az++) {
            for (int ah = az; ah < 10; ah++) {
                for (int bz = 0; bz < 10; bz++) {
                    for (int bh = bz; bh < 10; bh++) {
                        for (int pattern = 0; pattern < 3; pattern++) {
                            BigInteger am = window(az, ah, pattern, random), bm = window(bz, bh, pattern, random);
                            Binary640 a = input(am).negate(), b = input(bm);
                            byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
                            assertEquals(az, zeros.invoke(null, a));
                            assertEquals(bz, zeros.invoke(null, b));
                            long[] product = (long[]) kernel.invoke(null, a, a.getSignificandLong(0), az,
                                    b, b.getSignificandLong(0), bz);
                            assertTrue(product.length >= 10 && product.length <= 20);
                            BigInteger actual = BigInteger.ZERO;
                            for (int i = product.length - 1; i >= 0; i--) {
                                actual = actual.shiftLeft(64).or(BigInteger.valueOf(product[i]).and(WORD_MASK));
                            }
                            assertEquals(am.multiply(bm), actual.shiftLeft(64 * (az + bz)));
                            assertArrayEquals(beforeA, a.toBytes());
                            assertArrayEquals(beforeB, b.toBytes());
                        }
                    }
                }
            }
        }
    }

    @Test
    void checksEveryTrailingBitOnBothSidesOfWholeGroupBoundaries() {
        for (int bit = 0; bit < 616; bit++) {
            Binary640 a = input(HIDDEN.or(ONE.shiftLeft(bit)));
            for (int other : new int[] {0, 1, 63, 64, 65, 127, 128, 575, 576, 614, 615}) {
                check(a, input(HIDDEN.or(ONE.shiftLeft(other))));
            }
            if (bit < 615) {
                check(input(ONE.shiftLeft(bit)), Binary640.MAX_VALUE);
                check(input(ONE.shiftLeft(bit)), Binary640.MIN_NORMAL);
            }
        }
    }

    @Test
    void preservesRoundingWhenTheTrimmedProductNeedsLeftRightOrNoShift() {
        assertRaw(Binary640.ONE, Binary640.ONE.multiply(Binary640.ONE));
        check(Binary640.ONE, Binary640.ONE);
        check(input(FULL), Binary640.ONE);
        check(input(ONE.shiftLeft(64)), Binary640.MAX_VALUE);
        Binary640 half = Binary640.of(0.5);
        for (long odd = 1; odd < 16; odd += 2) {
            Binary640 a = Binary640Reference.fromRaw(BigInteger.valueOf(odd));
            for (Binary640 b : List.of(half.nextDown(), half, half.nextUp())) check(a, b);
        }
        Binary640 normalBoundary = Binary640.scalb(Binary640.MIN_NORMAL, 1).nextDown();
        assertRaw(Binary640.MIN_NORMAL, normalBoundary.multiply(half));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.MIN_VALUE.negate().multiply(half));
        BigInteger[] factors = ONE.shiftLeft(617).subtract(ONE).divideAndRemainder(BigInteger.valueOf(59233));
        assertEquals(BigInteger.ZERO, factors[1]);
        for (long scale : new long[] {-616, 8387991}) {
            Binary640 b = dyadic(false, factors[0], scale);
            for (Binary640 neighbor : List.of(b.nextDown(), b, b.nextUp())) check(Binary640.of(59233L), neighbor);
            assertRaw(scale == -616 ? Binary640.of(2L) : Binary640.POSITIVE_INFINITY, Binary640.of(59233L).multiply(b));
        }
    }

    @Test
    void checksEveryZeroGroupCountAcrossExponentEdgesAndRandomMagnitudes() {
        int[] exponents = {-8388606, -8388605, -1, 0, 1, 8388606, 8388607};
        for (int az = 0; az < 10; az++) {
            BigInteger am = FULL.shiftRight(64 * az).shiftLeft(64 * az);
            for (int bz = 0; bz < 10; bz++) {
                BigInteger bm = FULL.shiftRight(64 * bz).shiftLeft(64 * bz);
                for (int ae : exponents) {
                    for (int be : exponents) check(dyadic(false, am, ae - 615L), dyadic(false, bm, be - 615L));
                }
            }
        }
        Random random = new Random(0x6402071);
        for (int i = 0; i < 2500; i++) {
            int az = random.nextInt(10), bz = random.nextInt(10);
            BigInteger am = window(az, 9, 2, random), bm = window(bz, 9, 2, random);
            int ae = random.nextInt(16777214) - 8388606, be = random.nextInt(16777214) - 8388606;
            check(dyadic(false, am, ae - 615L), dyadic(false, bm, be - 615L));
        }
    }
}
