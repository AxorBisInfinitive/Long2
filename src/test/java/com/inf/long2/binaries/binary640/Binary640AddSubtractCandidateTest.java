package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary640AddSubtractCandidateTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(615);
    private static final BigInteger FULL = HIDDEN.shiftLeft(1).subtract(ONE);

    private static void check(Binary640 a, Binary640 b) {
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        for (Binary640 x : List.of(a, a.negate())) {
            for (Binary640 y : List.of(b, b.negate())) {
                assertRaw(Binary640Reference.add(x, y), x.add(y));
                assertRaw(Binary640Reference.add(x, y.negate()), x.subtract(y));
                assertRaw(Binary640Reference.add(y, x), y.add(x));
                assertRaw(Binary640Reference.add(y, x.negate()), y.subtract(x));
            }
        }
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    @Test
    void checksRollingAlignmentForEveryDistanceAndBothCarryChains() {
        Random random = new Random(0x6407011);
        for (int distance = 0; distance <= 65; distance++) {
            for (int exponent : new int[] {-8388540, -1, 0, 8388607}) {
                for (int pattern = 0; pattern < 6; pattern++) {
                    BigInteger a = pattern == 0 ? HIDDEN : pattern == 1 ? FULL
                            : new BigInteger(615, random).or(HIDDEN);
                    BigInteger b = pattern == 2 ? HIDDEN : pattern == 3 ? FULL
                            : new BigInteger(615, random).or(HIDDEN);
                    check(dyadic(false, a, exponent - 615L), dyadic(false, b, exponent - 615L - distance));
                }
            }
        }
    }

    @Test
    void checksEverySubnormalPositionAgainstDenseAndSparsePartners() {
        BigInteger max = HIDDEN.subtract(ONE);
        for (int bit = 0; bit < 615; bit++) {
            Binary640 single = Binary640Reference.fromRaw(ONE.shiftLeft(bit));
            Binary640 dense = Binary640Reference.fromRaw(ONE.shiftLeft(bit + 1).subtract(ONE));
            check(single, dense);
            check(single, Binary640Reference.fromRaw(max));
            check(dense, Binary640Reference.fromRaw(max.xor(ONE.shiftLeft(bit))));
        }
        Binary640 maximumSubnormal = Binary640.MIN_NORMAL.nextDown();
        assertRaw(Binary640.MIN_NORMAL, maximumSubnormal.add(Binary640.MIN_VALUE));
        assertRaw(dyadic(false, HIDDEN.subtract(ONE).shiftLeft(1), -8389221), maximumSubnormal.add(maximumSubnormal));
        assertRaw(Binary640.ZERO, maximumSubnormal.subtract(Binary640.of(maximumSubnormal.toBytes())));
    }

    @Test
    void checksSameExponentRoundingForAllFourLowBitCombinations() {
        for (int exponent : new int[] {-8388606, -8388605, -8387991, -1, 0, 1, 8388606, 8388607}) {
            for (int lowA = 0; lowA < 4; lowA++) {
                for (int lowB = 0; lowB < 4; lowB++) {
                    for (BigInteger high : List.of(HIDDEN, FULL.clearBit(0).clearBit(1))) {
                        BigInteger a = high.or(BigInteger.valueOf(lowA));
                        BigInteger b = FULL.clearBit(0).clearBit(1).or(BigInteger.valueOf(lowB));
                        check(dyadic(false, a, exponent - 615L), dyadic(false, b, exponent - 615L));
                    }
                }
            }
        }
    }

    @Test
    void checksExactSameExponentSubtractionAtEachNormalSubnormalBoundary() {
        for (int code = 1; code <= 618; code++) {
            int normalShift = Math.max(0, 616 - code);
            for (int bit : new int[] {0, 1, 63, 64, 65, 127, 128, 575, 576, 614}) {
                long scale = -8389221L + code - 1;
                Binary640 a = dyadic(false, HIDDEN.add(ONE.shiftLeft(bit)), scale);
                Binary640 b = dyadic(false, HIDDEN, scale);
                check(a, b);
                assertRaw(dyadic(false, ONE.shiftLeft(bit), scale), a.subtract(b));
            }
            if (normalShift < 615) {
                long scale = -8389221L + code - 1;
                check(dyadic(false, FULL, scale), dyadic(false, FULL.subtract(ONE.shiftLeft(normalShift)), scale));
            }
        }
    }

    @Test
    void checksRandomizedSpecializedAndFallbackInputs() {
        Random random = new Random(0x640CA11);
        for (int i = 0; i < 1600; i++) {
            BigInteger a = new BigInteger(615, random).or(HIDDEN);
            BigInteger b = new BigInteger(615, random).or(HIDDEN);
            int exponent = random.nextInt(16777214) - 8388606;
            int gap = i % 3 == 0 ? 0 : i % 3 == 1 ? random.nextInt(63) + 1 : random.nextInt(800);
            int otherExponent = Math.max(-8388606, exponent - gap);
            check(dyadic(false, a, exponent - 615L), dyadic(false, b, otherExponent - 615L));
            if (i % 8 == 0) {
                check(Binary640Reference.fromRaw(a.and(HIDDEN.subtract(ONE))), Binary640Reference.fromRaw(b.and(HIDDEN.subtract(ONE))));
            }
        }
    }
}
