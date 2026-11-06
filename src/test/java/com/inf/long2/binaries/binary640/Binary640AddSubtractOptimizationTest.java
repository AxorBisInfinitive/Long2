package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640AddSubtractOptimizationTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(615);
    private static final BigInteger FULL = ONE.shiftLeft(616).subtract(ONE);

    private static void check(Binary640 a, Binary640 b) {
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        assertRaw(Binary640Reference.add(a, b), a.add(b));
        assertRaw(Binary640Reference.add(a, b.negate()), a.subtract(b));
        assertRaw(Binary640Reference.add(b, a), b.add(a));
        assertRaw(Binary640Reference.add(b, a.negate()), b.subtract(a));
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    private static void signs(Binary640 a, Binary640 b) {
        check(a, b);
        check(a.negate(), b);
        check(a, b.negate());
        check(a.negate(), b.negate());
    }

    @Test
    void coversEveryAlignmentIncludingTheFarReturnThreshold() {
        BigInteger[] patterns = {HIDDEN, HIDDEN.add(ONE), FULL,
                HIDDEN.or(new BigInteger("aa".repeat(76), 16))};
        for (int gap = 0; gap <= 619; gap++) {
            for (int i = 0; i < patterns.length; i++) {
                signs(dyadic(false, patterns[i], -615),
                        dyadic(false, patterns[(i + 1) % patterns.length], -615L - gap));
            }
        }
        Binary640 lowerHalf = Binary640.ONE.scalb(-617);
        assertRaw(Binary640.ONE, Binary640.ONE.subtract(lowerHalf));
        assertRaw(Binary640.ONE.nextDown(), Binary640.ONE.subtract(lowerHalf.nextUp()));
    }

    @Test
    void coversEverySubnormalWidthAndMixedMinimumNormalInputs() {
        for (int width = 1; width <= 615; width++) {
            Binary640 dense = Binary640Reference.fromRaw(ONE.shiftLeft(width).subtract(ONE));
            Binary640 single = Binary640Reference.fromRaw(ONE.shiftLeft(width - 1));
            signs(dense, Binary640.MIN_VALUE);
            signs(single, dense);
            signs(dense, Binary640.MIN_NORMAL);
        }
        assertRaw(Binary640.MIN_NORMAL, Binary640.MIN_NORMAL.nextDown().add(Binary640.MIN_VALUE));
        assertRaw(Binary640.MIN_NORMAL.nextDown(), Binary640.MIN_NORMAL.subtract(Binary640.MIN_VALUE));
    }

    @Test
    void preservesEveryResidualBitAndFullCarryOrBorrowChains() {
        Binary640 a = dyadic(false, FULL, -615);
        for (int bit = 0; bit < 616; bit++) {
            Binary640 b = dyadic(false, FULL.subtract(ONE.shiftLeft(bit)), -615);
            signs(a, b);
            assertRaw(dyadic(false, ONE, bit - 615L), a.subtract(b));
            Binary640 chain = dyadic(false, ONE.shiftLeft(bit + 1).subtract(ONE), -615);
            signs(chain, dyadic(false, ONE, -615));
        }
        Binary640 copy = Binary640.of(a.toBytes());
        assertNotSame(a, copy);
        assertRaw(Binary640.ZERO, a.subtract(copy));
        assertRaw(Binary640.ZERO, a.add(copy.negate()));
    }

    @Test
    void keepsRoundingParityStickyAndTheExactOverflowMidpoint() {
        for (int parity = 0; parity < 2; parity++) {
            Binary640 a = dyadic(false, HIDDEN.add(BigInteger.valueOf(parity)), -615);
            Binary640 half = Binary640.ONE.scalb(-616);
            for (Binary640 b : List.of(half.nextDown(), half, half.nextUp(), half.scalb(-1))) signs(a, b);
        }
        Binary640 half = Binary640.ONE.scalb(8387991);
        for (Binary640 b : List.of(half.nextDown(), half, half.nextUp())) signs(Binary640.MAX_VALUE, b);
        assertRaw(Binary640.MAX_VALUE, Binary640.MAX_VALUE.add(half.nextDown()));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.MAX_VALUE.add(half));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.MAX_VALUE.negate().subtract(half));
        for (int tail = 1; tail <= 615; tail++) {
            Binary640 b = dyadic(false, HIDDEN.add(ONE.shiftLeft(615 - tail)), -1231);
            signs(Binary640.ONE, b);
        }
    }

    @Test
    void normalizesRoundingCarryOnceWhenTheExactWorkspaceExceedsTenWords() {
        Binary640 belowOne = Binary640.ONE.nextDown();
        Binary640 half = Binary640.ONE.scalb(-617);
        assertRaw(Binary640.ONE, belowOne.add(half));
        assertRaw(Binary640.ONE, belowOne.subtract(half.negate()));
        assertRaw(Binary640.ONE.negate(), belowOne.negate().subtract(half));
        assertRaw(belowOne, belowOne.add(half.nextDown()));
        Binary640 overflowHalf = Binary640.ONE.scalb(8387991);
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.MAX_VALUE.add(overflowHalf));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.MAX_VALUE.negate().subtract(overflowHalf));
    }

    @Test
    void checksSameQuantumAcrossTheEntireExponentRange() {
        BigInteger[] patterns = {HIDDEN, HIDDEN.add(ONE), FULL, ONE.shiftLeft(616).subtract(BigInteger.valueOf(2))};
        for (int exponent : new int[] {-8388606, -8388605, -1024, -1, 0, 1, 1024, 8388606, 8388607}) {
            for (BigInteger a : patterns) {
                for (BigInteger b : patterns) signs(dyadic(false, a, exponent - 615L), dyadic(false, b, exponent - 615L));
            }
        }
    }

    @Test
    void checksSeededDenseSparseAndAdjacentPairsAgainstExactIntegers() {
        Random random = new Random(0x640ADD5B);
        for (int i = 0; i < 2500; i++) {
            int gap = random.nextInt(620);
            long scale = random.nextInt(4097) - 2048L;
            BigInteger am = new BigInteger(616, random).setBit(615);
            BigInteger bm = new BigInteger(616, random).setBit(615);
            if (i % 4 == 0) am = ONE.shiftLeft(random.nextInt(616));
            if (i % 4 == 1) bm = ONE.shiftLeft(random.nextInt(616)).add(ONE);
            Binary640 a = dyadic(random.nextBoolean(), am, scale);
            Binary640 b = i % 4 == 2 ? (i % 8 == 2 ? a.nextUp() : a.nextDown())
                    : dyadic(random.nextBoolean(), bm, scale - gap);
            check(a, b);
        }
    }

    @Test
    void preservesAllSpecialValueEncodingsAndNullPrecedence() {
        double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -2, -0.0, 0.0, 2, Double.POSITIVE_INFINITY};
        for (double x : values) {
            Binary640 a = Binary640.of(x);
            assertThrows(NullPointerException.class, () -> a.add(null));
            assertThrows(NullPointerException.class, () -> a.subtract(null));
            for (double y : values) {
                Binary640 b = Binary640.of(y);
                assertRaw(Binary640.of(x + y), a.add(b));
                assertRaw(Binary640.of(x - y), a.subtract(b));
            }
        }
        BigInteger infinity = BigInteger.valueOf(0xffffff).shiftLeft(615);
        for (int bit = 0; bit < 615; bit++) {
            for (boolean negative : new boolean[] {false, true}) {
                BigInteger bits = infinity.setBit(bit);
                Binary640 nan = Binary640Reference.fromRaw(negative ? bits.setBit(639) : bits);
                assertRaw(Binary640.NaN, nan.add(Binary640.ONE));
                assertRaw(Binary640.NaN, Binary640.ONE.add(nan));
                assertRaw(Binary640.NaN, nan.subtract(Binary640.NEGATIVE_INFINITY));
                assertRaw(Binary640.NaN, Binary640.ZERO.subtract(nan));
            }
        }
    }

    @Test
    void keepsOwnedWorkspacesIndependentAcrossConcurrentAndAliasedCalls() throws Exception {
        Binary640 a = dyadic(false, FULL, -615), b = dyadic(false, HIDDEN.add(ONE), -1232);
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int task = 0; task < 24; task++) {
            tasks.add(() -> {
                for (int i = 0; i < 40; i++) {
                    check(a, b);
                    check(a, a);
                }
                return null;
            });
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var future : executor.invokeAll(tasks)) future.get();
        }
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }
}
