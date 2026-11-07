package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640MultiplyCandidateTest {
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

    private static Method rounder() throws ReflectiveOperationException {
        Class<?> math = Class.forName("com.inf.long2.binaries.Binary640Math");
        Method result;
        try {
            result = math.getDeclaredMethod("roundProduct", boolean.class, long[].class, long.class);
        } catch (NoSuchMethodException exception) {
            result = math.getDeclaredMethod("roundOwnedDyadic", boolean.class, long[].class, long.class);
        }
        result.setAccessible(true);
        return result;
    }

    private static void checkRounding(Method rounder, BigInteger magnitude, long scale) throws ReflectiveOperationException {
        assertTrue(magnitude.signum() >= 0 && magnitude.bitLength() <= 1232);
        for (boolean negative : new boolean[] {false, true}) {
            long[] product = new long[20];
            for (int i = 0; i < 20; i++) product[i] = magnitude.shiftRight(i * 64).longValue();
            Binary640 expected = Binary640Reference.round(negative, magnitude, ONE, scale);
            assertRaw(expected, (Binary640) rounder.invoke(null, negative, product, scale));
        }
    }

    @Test
    void checksFullSingleWordProductsWithEveryRemainingWindow() throws ReflectiveOperationException {
        Class<?> math = Class.forName("com.inf.long2.binaries.Binary640Math");
        Method kernel = math.getDeclaredMethod("multiplyTrimmed", Binary640.class, long.class, int.class,
                Binary640.class, long.class, int.class);
        kernel.setAccessible(true);
        Random random = new Random(0x640C101);
        for (int start = 0; start < 10; start++) {
            int scalarBits = start == 9 ? 40 : 64;
            for (BigInteger scalar : List.of(ONE, ONE.shiftLeft(scalarBits - 1), ONE.shiftLeft(scalarBits).subtract(ONE))) {
                BigInteger am = scalar.shiftLeft(start * 64);
                for (int low = 0; low < 10; low++) {
                    for (int high = low; high < 10; high++) {
                        int bits = (high - low) * 64 + (high == 9 ? 40 : 64);
                        for (BigInteger m : List.of(ONE.shiftLeft(bits).subtract(ONE),
                                new BigInteger(bits, random).setBit(bits - 1).setBit(0))) {
                            BigInteger bm = m.shiftLeft(low * 64);
                            for (boolean swap : new boolean[] {false, true}) {
                                Binary640 a = input(swap ? bm : am), b = input(swap ? am : bm).negate();
                                byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
                                long[] product = (long[]) kernel.invoke(null, a, a.getSignificandLong(0), swap ? low : start,
                                        b, b.getSignificandLong(0), swap ? start : low);
                                BigInteger actual = BigInteger.ZERO;
                                for (int i = product.length - 1; i >= 0; i--) {
                                    actual = actual.shiftLeft(64).or(BigInteger.valueOf(product[i]).and(WORD_MASK));
                                }
                                assertEquals(am.multiply(bm), actual.shiftLeft(64 * (start + low)));
                                assertArrayEquals(beforeA, a.toBytes());
                                assertArrayEquals(beforeB, b.toBytes());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void checksPublicSingleWordProductsAcrossExponentEdges() {
        int[] exponents = {-8388606, -8388605, -1, 0, 1, 8388606, 8388607};
        for (int zeroGroups = 0; zeroGroups < 10; zeroGroups++) {
            BigInteger am = FULL.shiftRight(64 * zeroGroups).shiftLeft(64 * zeroGroups);
            for (BigInteger bm : List.of(HIDDEN, HIDDEN.or(ONE.shiftLeft(614)), HIDDEN.add(ONE), FULL)) {
                for (int ae : exponents) {
                    for (int be : exponents) check(dyadic(false, am, ae - 615L), dyadic(false, bm, be - 615L));
                }
            }
        }
        check(Binary640.MIN_VALUE, Binary640.MAX_VALUE);
        check(Binary640.MIN_NORMAL.scalb(1).nextDown(), Binary640.of(0.5));
    }

    @Test
    void checksEveryProductLengthAndAllNormalizationDirections() throws ReflectiveOperationException {
        Method rounder = rounder();
        Random random = new Random(0x640C102);
        checkRounding(rounder, BigInteger.ZERO, 0);
        for (int bits = 1; bits <= 1232; bits++) {
            for (BigInteger m : List.of(ONE.shiftLeft(bits - 1), ONE.shiftLeft(bits).subtract(ONE),
                    new BigInteger(bits, random).setBit(bits - 1))) {
                checkRounding(rounder, m, 1L - bits);
            }
        }
    }

    @Test
    void checksLowRangeRawEncodingAndTheExactOverflowThreshold() throws ReflectiveOperationException {
        Method rounder = rounder();
        for (BigInteger kept : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(3), HIDDEN.subtract(ONE),
                HIDDEN, FULL, HIDDEN.shiftLeft(1))) {
            for (int extra : new int[] {1, 2, 39, 40, 63, 64, 65, 614, 615}) {
                BigInteger half = ONE.shiftLeft(extra - 1), center = kept.shiftLeft(extra);
                for (BigInteger offset : List.of(half.negate().subtract(ONE), half.negate(), half.negate().add(ONE),
                        BigInteger.ZERO, half.subtract(ONE), half, half.add(ONE))) {
                    BigInteger m = center.add(offset);
                    if (m.signum() >= 0 && m.bitLength() <= 1232) checkRounding(rounder, m, -8389221L - extra);
                }
            }
        }
        for (int extra : new int[] {0, 1, 39, 40, 63, 64, 615}) {
            BigInteger center = ONE.shiftLeft(617).subtract(ONE).shiftLeft(extra);
            for (int delta : new int[] {-1, 0, 1}) checkRounding(rounder, center.add(BigInteger.valueOf(delta)), 8387991L - extra);
        }
        for (int extra = 0; extra < 1232; extra++) {
            BigInteger half = ONE.shiftLeft(extra);
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger m = half.add(BigInteger.valueOf(delta));
                if (m.bitLength() <= 1232) checkRounding(rounder, m, -8389222L - extra);
            }
        }
    }

    @Test
    void checksLongRoundingIncrementChainsAndBinadeCarry() throws ReflectiveOperationException {
        Method rounder = rounder();
        for (int bit = 0; bit <= 615; bit++) {
            BigInteger kept = HIDDEN.or(ONE.shiftLeft(bit).subtract(ONE));
            for (int delta : new int[] {-1, 0, 1}) {
                checkRounding(rounder, kept.shiftLeft(3).add(BigInteger.valueOf(4 + delta)), -618);
            }
        }
        for (int extra = 1; extra <= 616; extra++) {
            BigInteger center = FULL.shiftLeft(extra).add(ONE.shiftLeft(extra - 1));
            for (int delta : new int[] {-1, 0, 1}) checkRounding(rounder, center.add(BigInteger.valueOf(delta)), -615L - extra);
        }
    }
}
