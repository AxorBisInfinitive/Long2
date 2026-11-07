package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640MultiplyOptimizationTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(615);
    private static final BigInteger FULL = ONE.shiftLeft(616).subtract(ONE);
    private static final BigInteger WORD_MASK = ONE.shiftLeft(64).subtract(ONE);

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

    private static Binary640 input(BigInteger magnitude) {
        assertTrue(magnitude.signum() >= 0 && magnitude.bitLength() <= 616);
        return Binary640Reference.fromRaw(magnitude.bitLength() < 616 ? magnitude
                : magnitude.clearBit(615).or(BigInteger.valueOf(8388607).shiftLeft(615)));
    }

    private static void checkKernel(Method low, Method high, BigInteger am, BigInteger bm) throws ReflectiveOperationException {
        Binary640 a = input(am).negate(), b = input(bm);
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        long[] product = new long[20];
        // 非零初值要求两段内核完整覆盖输出，不能依赖新数组的零填充。
        Arrays.fill(product, 0xa5a5_a5a5_a5a5_a5a5L);
        long atop = a.getSignificandLong(0), btop = b.getSignificandLong(0);
        long carry = (Long) low.invoke(null, a, atop, b, btop, product);
        assertTrue(carry >= 0 && carry <= 18);
        high.invoke(null, a, atop, b, btop, product, carry);
        BigInteger actual = BigInteger.ZERO;
        for (int i = 19; i >= 0; i--) {
            actual = actual.shiftLeft(64).or(BigInteger.valueOf(product[i]).and(WORD_MASK));
        }
        assertEquals(am.multiply(bm), actual);
        assertTrue(actual.bitLength() <= 1232);
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    @Test
    void checksEveryFullProductColumnBeforeRounding() throws ReflectiveOperationException {
        Class<?> math = Class.forName("com.inf.long2.binaries.Binary640Math");
        Method low = math.getDeclaredMethod("multiplyLowColumns", Binary640.class, long.class,
                Binary640.class, long.class, long[].class);
        Method high = math.getDeclaredMethod("multiplyHighColumns", Binary640.class, long.class,
                Binary640.class, long.class, long[].class, long.class);
        low.setAccessible(true);
        high.setAccessible(true);
        for (int i = 0; i < 10; i++) {
            BigInteger a = ONE.shiftLeft(i == 9 ? 40 : 64).subtract(ONE).shiftLeft(i * 64);
            for (int j = 0; j < 10; j++) {
                BigInteger b = ONE.shiftLeft(j == 9 ? 40 : 64).subtract(ONE).shiftLeft(j * 64);
                checkKernel(low, high, a, b);
            }
        }
        for (int bit = 0; bit < 616; bit++) {
            checkKernel(low, high, ONE.shiftLeft(bit), FULL);
            checkKernel(low, high, ONE.shiftLeft(bit + 1).subtract(ONE), FULL);
        }
        for (BigInteger a : List.of(BigInteger.ZERO, ONE, HIDDEN, FULL)) {
            for (BigInteger b : List.of(BigInteger.ZERO, ONE, HIDDEN, FULL)) checkKernel(low, high, a, b);
        }
        Random random = new Random(0x640F011);
        for (int i = 0; i < 2000; i++) {
            checkKernel(low, high, new BigInteger(616, random), new BigInteger(616, random));
        }
    }

    @Test
    void checksGuardStickyAndBothTieParities() {
        Set<String> cases = new HashSet<>();
        Binary640 center = Binary640.of(1.5);
        for (int low = 0; low < 8; low++) {
            Binary640 a = dyadic(false, HIDDEN.add(BigInteger.valueOf(low)), -615);
            for (Binary640 b : List.of(center.nextDown(), center, center.nextUp())) {
                BigInteger p = Binary640Reference.decode(a).magnitude().multiply(Binary640Reference.decode(b).magnitude());
                int discarded = p.bitLength() - 616;
                BigInteger tail = p.and(ONE.shiftLeft(discarded).subtract(ONE));
                int half = tail.compareTo(ONE.shiftLeft(discarded - 1));
                cases.add(tail.signum() == 0 ? "exact" : half < 0 ? "below" : half > 0 ? "above"
                        : p.testBit(discarded) ? "tieOdd" : "tieEven");
                check(a, b);
            }
        }
        assertEquals(Set.of("exact", "below", "above", "tieOdd", "tieEven"), cases);
    }

    @Test
    void normalizesRoundingCarryAndTheExactOverflowMidpoint() {
        BigInteger[] factors = ONE.shiftLeft(617).subtract(ONE).divideAndRemainder(BigInteger.valueOf(59233));
        assertEquals(BigInteger.ZERO, factors[1]);
        Binary640 a = Binary640.of(59233L);
        for (long scale : new long[] {-616, 8387991}) {
            Binary640 b = dyadic(false, factors[0], scale);
            for (Binary640 neighbor : List.of(b.nextDown(), b, b.nextUp())) check(a, neighbor);
            assertRaw(scale == -616 ? Binary640.of(2L) : Binary640.POSITIVE_INFINITY, a.multiply(b));
        }
        Binary640 twiceMinimum = Binary640.MIN_NORMAL.scalb(1).nextDown();
        check(twiceMinimum, Binary640.of(0.5));
        assertRaw(Binary640.MIN_NORMAL, twiceMinimum.multiply(Binary640.of(0.5)));
    }

    @Test
    void checksAllSubnormalWidthsAndSignedUnderflow() {
        Binary640 half = Binary640.of(0.5);
        for (int width = 1; width <= 615; width++) {
            for (BigInteger m : List.of(ONE.shiftLeft(width - 1), ONE.shiftLeft(width).subtract(ONE))) {
                Binary640 a = input(m);
                for (Binary640 b : List.of(half.nextDown(), half, half.nextUp(), Binary640.ONE.nextUp(), Binary640.MAX_VALUE)) {
                    check(a, b);
                }
            }
        }
        assertRaw(Binary640.ZERO, Binary640.MIN_VALUE.multiply(half));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.MIN_VALUE.negate().multiply(half));
        assertRaw(Binary640.MIN_VALUE, Binary640.MIN_VALUE.multiply(half.nextUp()));
        check(Binary640.MIN_VALUE, Binary640.MIN_VALUE);
        check(Binary640.MIN_NORMAL.nextDown(), Binary640.MIN_NORMAL.nextDown());
    }

    @Test
    void checksBothNormalProductLengthsAcrossExponentEdges() {
        int[] exponents = {-8388606, -8388605, -1024, -1, 0, 1, 8388606, 8388607};
        for (int ae : exponents) {
            for (int be : exponents) {
                for (BigInteger am : List.of(HIDDEN, FULL)) {
                    for (BigInteger bm : List.of(HIDDEN, FULL)) {
                        check(dyadic(false, am, ae - 615L), dyadic(false, bm, be - 615L));
                    }
                }
            }
        }
        Random random = new Random(0x640A117);
        for (int i = 0; i < 3000; i++) {
            int ac = random.nextInt(0xffffff), bc = random.nextInt(0xffffff);
            Binary640 a = Binary640Reference.fromRaw(BigInteger.valueOf(ac).shiftLeft(615).or(new BigInteger(615, random)));
            Binary640 b = Binary640Reference.fromRaw(BigInteger.valueOf(bc).shiftLeft(615).or(new BigInteger(615, random)));
            check(a, b);
        }
    }

    @Test
    void preservesNaNPayloadSemanticsAndSignsOfSpecialProducts() {
        BigInteger infinity = BigInteger.valueOf(0xffffff).shiftLeft(615);
        for (BigInteger payload : List.of(ONE, ONE.shiftLeft(614), HIDDEN.subtract(ONE))) {
            Binary640 nan = Binary640Reference.fromRaw(infinity.or(payload));
            for (Binary640 x : List.of(nan, Binary640Reference.fromRaw(infinity.or(payload).setBit(639)))) {
                for (Binary640 y : List.of(Binary640.ZERO, Binary640.NEGATIVE_ZERO, Binary640.MIN_VALUE,
                        Binary640.MAX_VALUE, Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY)) {
                    assertRaw(Binary640.NaN, x.multiply(y));
                    assertRaw(Binary640.NaN, y.multiply(x));
                }
            }
        }
        for (Binary640 zero : List.of(Binary640.ZERO, Binary640.NEGATIVE_ZERO)) {
            for (Binary640 inf : List.of(Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY)) {
                assertRaw(Binary640.NaN, zero.multiply(inf));
                assertRaw(Binary640.NaN, inf.multiply(zero));
            }
            for (Binary640 b : List.of(Binary640.MIN_VALUE, Binary640.MIN_VALUE.negate(), zero)) {
                Binary640 expected = zero.signBit() ^ b.signBit() ? Binary640.NEGATIVE_ZERO : Binary640.ZERO;
                assertRaw(expected, zero.multiply(b));
                assertRaw(expected, b.multiply(zero));
            }
        }
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.MAX_VALUE.multiply(Binary640.POSITIVE_INFINITY));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.MAX_VALUE.multiply(Binary640.NEGATIVE_INFINITY));
    }
}
