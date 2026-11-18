package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static love.crysh.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Binary1280AddSubtractTest {
    private static final int FRACTION_BITS = 1251;
    private static final int EMIN = -134217726;
    private static final int EMAX = 134217727;
    private static final BigInteger HIDDEN = BigInteger.ONE.shiftLeft(FRACTION_BITS);
    private static final BigInteger MASK = HIDDEN.subtract(BigInteger.ONE);
    private static final BigInteger WORD_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);
    private static final BigInteger COMMON_HIGH_WORDS = BigInteger.ONE.shiftLeft(FRACTION_BITS - 1).setBit(Long.SIZE);

    @Test
    void coversSmallAlignmentGapsAndMidpointTails() {
        BigInteger[] fractions = {BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO, MASK.clearBit(0), MASK};
        for (int gap = 0; gap <= 65; gap++) {
            for (int highExponent : new int[] {EMIN + gap, 0, EMAX}) {
                for (BigInteger fraction : fractions) {
                    Binary1280 a = positiveNormal(highExponent, fraction);
                    checkSignsAndOrder(a, positiveNormal(highExponent - gap, MASK.xor(fraction)));
                    checkSignsAndOrder(a, positiveNormal(highExponent - gap, fraction));
                }
            }
        }
        for (int gap : new int[] {1251, 1252, 1253, 1254, 1255, 2048, 8192}) {
            for (int exponent : new int[] {EMIN + gap, 0, EMAX}) {
                for (BigInteger a : fractions) {
                    for (BigInteger b : fractions) {
                        checkSignsAndOrder(positiveNormal(exponent, a), positiveNormal(exponent - gap, b));
                    }
                }
            }
        }
    }

    @Test
    void coversCancellationBorrowChainsAndAllSubnormalWidths() {
        Binary1280 largestSubnormal = fromRaw(MASK);
        for (int bit = 0; bit < FRACTION_BITS; bit++) {
            BigInteger residual = BigInteger.ONE.shiftLeft(bit);
            for (int exponent : new int[] {EMIN, EMIN + 1, EMIN + 64, 0, EMAX}) {
                checkSignsAndOrder(positiveNormal(exponent, residual), positiveNormal(exponent, BigInteger.ONE));
            }
            Binary1280 subnormal = fromRaw(residual);
            checkSignsAndOrder(subnormal, fromRaw(residual.subtract(BigInteger.ONE)));
            checkSignsAndOrder(subnormal, largestSubnormal);
            checkSignsAndOrder(subnormal, positiveNormal(EMIN, BigInteger.ZERO));
            // 编码差与实际指数差分别跨越 1253/1254。
            checkSignsAndOrder(subnormal, positiveNormal(EMIN + bit + 2, BigInteger.ZERO));
            checkSignsAndOrder(subnormal, positiveNormal(EMIN + bit + 3, BigInteger.ZERO));
            checkSignsAndOrder(subnormal, positiveNormal(EMIN + 1253, BigInteger.ZERO));
        }
    }

    @Test
    void coversSeededDenseAndSparseInputs() {
        Random random = new Random(0x1280ADD5L);
        int[] gaps = {0, 1, 2, 27, 28, 29, 63, 64, 65, 127, 128, 1249, 1252, 1253, 1254, 1255};
        for (int i = 0; i < 4096; i++) {
            int exponent = random.nextInt(4097) - 2048;
            int gap = gaps[i % gaps.length];
            BigInteger a = new BigInteger(FRACTION_BITS, random);
            BigInteger b = new BigInteger(FRACTION_BITS, random);
            if ((i & 4) == 0) {
                a = a.shiftRight(i % FRACTION_BITS).shiftLeft(i % FRACTION_BITS);
            }
            if ((i & 8) == 0) {
                b = b.shiftRight(i % FRACTION_BITS);
            }
            Binary1280 x = positiveNormal(exponent, a);
            Binary1280 y = positiveNormal(exponent - gap, b);
            checkPair(random.nextBoolean() ? x : x.negate(), random.nextBoolean() ? y : y.negate());
        }
    }

    @Test
    void farBoundaryIncludesExactMidpointsAndEveryEffectiveSign() {
        BigInteger[] fractions = {BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(64),
                BigInteger.ONE.shiftLeft(1216), BigInteger.ONE.shiftLeft(1250), MASK};
        for (int gap : new int[] {1252, 1253, 1254}) {
            for (int exponent : new int[] {EMIN + gap, -1, 0, 1, EMAX}) {
                for (BigInteger high : fractions) {
                    for (BigInteger low : fractions) {
                        checkSignsAndOrder(positiveNormal(exponent, high), positiveNormal(exponent - gap, low));
                    }
                }
            }
        }
        for (int encoded : new int[] {1252, 1253, 1254}) {
            for (BigInteger fraction : fractions) {
                if (fraction.signum() != 0) {
                    checkSignsAndOrder(positiveNormal(EMIN + encoded - 1, BigInteger.ZERO),
                            fromRaw(fraction));
                }
            }
        }
    }

    @Test
    void singleWordCoversEachUnsignedLengthAndTheNormalBoundary() {
        for (int length = 1; length <= Long.SIZE; length++) {
            int normalOffset = FRACTION_BITS + 1 - length;
            BigInteger first = BigInteger.ONE.shiftLeft(length - 1);
            BigInteger last = first.shiftLeft(1).subtract(BigInteger.ONE);
            for (int offset : new int[] {0, 63, 64, 65, normalOffset - 1, normalOffset, normalOffset + 1, EMAX - EMIN}) {
                Binary1280 common = positiveNormal(EMIN + offset, COMMON_HIGH_WORDS);
                checkSignsAndOrder(common, positiveNormal(EMIN + offset, COMMON_HIGH_WORDS.or(first)));
                checkSignsAndOrder(common, positiveNormal(EMIN + offset, COMMON_HIGH_WORDS.or(last)));
            }
            Binary1280 subnormal = fromRaw(COMMON_HIGH_WORDS);
            checkSignsAndOrder(subnormal, fromRaw(COMMON_HIGH_WORDS.or(first)));
            checkSignsAndOrder(subnormal, fromRaw(COMMON_HIGH_WORDS.or(last)));
        }
    }

    @Test
    void singleWordCoversAllShiftedSubnormalPlacementsAndCrossWordBorrow() {
        for (int offset = 0; offset <= FRACTION_BITS; offset++) {
            int exponent = EMIN + offset;
            Binary1280 common = positiveNormal(exponent, COMMON_HIGH_WORDS);
            checkSignsAndOrder(common, positiveNormal(exponent, COMMON_HIGH_WORDS.or(BigInteger.ONE)));
            checkSignsAndOrder(common, positiveNormal(exponent, COMMON_HIGH_WORDS.or(WORD_MASK)));
            checkSignsAndOrder(positiveNormal(exponent, BigInteger.ONE.shiftLeft(Long.SIZE)), positiveNormal(exponent, WORD_MASK));
        }
    }

    @Test
    void exactWordResidualFeedsFarMidpointAndThenCancelsAgain() {
        for (int exponent : new int[] {EMIN + 1253, -1, 0, 1, EMAX - FRACTION_BITS}) {
            Binary1280 midpoint = positiveNormal(exponent - 1253, BigInteger.ZERO);
            Binary1280 beyondMidpoint = positiveNormal(exponent - 1253, BigInteger.ONE);
            Binary1280 expectedPower = positiveNormal(exponent, BigInteger.ZERO);
            Binary1280 expectedPredecessor = positiveNormal(exponent - 1, MASK);
            Binary1280 lowerNeighbor = positiveNormal(exponent - 1, MASK.subtract(BigInteger.ONE));
            for (int bit : new int[] {0, 35, 36, 63}) {
                int operandExponent = exponent + FRACTION_BITS - bit;
                Binary1280 a = positiveNormal(operandExponent, COMMON_HIGH_WORDS.setBit(bit));
                Binary1280 b = positiveNormal(operandExponent, COMMON_HIGH_WORDS);
                Binary1280 residual = a.subtract(b);
                assertArrayEquals(expectedPower.toBytes(), residual.toBytes());
                checkSignsAndOrder(residual, midpoint);
                checkSignsAndOrder(residual, beyondMidpoint);
                Binary1280 stepped = residual.subtract(beyondMidpoint);
                assertArrayEquals(expectedPredecessor.toBytes(), stepped.toBytes());
                checkSignsAndOrder(stepped, lowerNeighbor);
            }
        }
    }

    @Test
    void mixedMinimumScaleCoversEverySubnormalWidthAndCarryParity() {
        for (int width = 1; width <= FRACTION_BITS; width++) {
            BigInteger subnormalMagnitude = BigInteger.ONE.shiftLeft(width - 1);
            Binary1280 subnormal = fromRaw(subnormalMagnitude);
            checkSignsAndOrder(positiveNormal(EMIN, BigInteger.ZERO), subnormal);
            checkSignsAndOrder(positiveNormal(EMIN + 1, BigInteger.ZERO), subnormal);
            BigInteger carryThreshold = HIDDEN.subtract(subnormalMagnitude);
            for (int offset = -1; offset <= 3; offset++) {
                BigInteger fraction = carryThreshold.add(BigInteger.valueOf(offset));
                if (fraction.compareTo(MASK) <= 0) {
                    checkSignsAndOrder(positiveNormal(EMIN, fraction), subnormal);
                }
            }
        }
        checkSignsAndOrder(positiveNormal(EMIN, BigInteger.ZERO), fromRaw(MASK));
        checkSignsAndOrder(positiveNormal(EMIN, MASK), fromRaw(MASK));
    }

    @Test
    void detectsDifferencesInEveryFractionBitAndBorrowAcrossEveryWord() {
        for (int bit = 0; bit < FRACTION_BITS; bit++) {
            BigInteger changed = MASK.clearBit(bit);
            checkSignsAndOrder(positiveNormal(0, MASK), positiveNormal(0, changed));
            checkSignsAndOrder(fromRaw(MASK), fromRaw(changed));
        }
        for (int bit = Long.SIZE; bit < FRACTION_BITS; bit += Long.SIZE) {
            BigInteger crossWord = BigInteger.ONE.shiftLeft(bit);
            checkSignsAndOrder(positiveNormal(EMIN, crossWord), positiveNormal(EMIN, crossWord.subtract(BigInteger.ONE)));
            checkSignsAndOrder(fromRaw(crossWord), fromRaw(crossWord.subtract(BigInteger.ONE)));
        }
        checkSignsAndOrder(positiveNormal(0, MASK), positiveNormal(0, MASK));
        checkSignsAndOrder(fromRaw(MASK), fromRaw(MASK));
    }

    @Test
    void mixedResultsOnBothSidesOfMinimumNormalCancelWithinTheirScale() {
        Binary1280 minimumNormal = positiveNormal(EMIN, BigInteger.ZERO);
        for (int bit = 1; bit < FRACTION_BITS; bit++) {
            BigInteger magnitude = BigInteger.ONE.shiftLeft(bit).subtract(BigInteger.ONE);
            Binary1280 subnormal = fromRaw(magnitude);
            Binary1280 below = minimumNormal.subtract(subnormal);
            Binary1280 above = minimumNormal.add(subnormal);
            BigInteger belowBits = HIDDEN.subtract(magnitude);
            assertArrayEquals(fromRaw(belowBits).toBytes(), below.toBytes());
            assertArrayEquals(positiveNormal(EMIN, magnitude).toBytes(), above.toBytes());
            checkSignsAndOrder(below, fromRaw(belowBits.subtract(BigInteger.ONE)));
            checkSignsAndOrder(above, positiveNormal(EMIN, magnitude.subtract(BigInteger.ONE)));
        }
    }

    @Test
    void carriedMixedSumPreservesTieParityBeforeCancellationAtTheNextExponent() {
        for (int bit : new int[] {2, 35, 36, 63, 64, 65, 127, 128, 1216, 1250}) {
            BigInteger magnitude = BigInteger.ONE.shiftLeft(bit);
            Binary1280 subnormal = fromRaw(magnitude);
            BigInteger threshold = HIDDEN.subtract(magnitude);
            for (int offset = 0; offset <= 3; offset++) {
                Binary1280 left = positiveNormal(EMIN, threshold.add(BigInteger.valueOf(offset)));
                BigInteger expectedFraction = BigInteger.valueOf((offset + (offset == 3 ? 1 : 0)) / 2);
                Binary1280 expected = positiveNormal(EMIN + 1, expectedFraction);
                Binary1280 result = left.add(subnormal);
                assertArrayEquals(expected.toBytes(), result.toBytes());
                assertArrayEquals(expected.negate().toBytes(), left.negate().add(subnormal.negate()).toBytes());
                checkSignsAndOrder(result, expected);
                checkSignsAndOrder(result, positiveNormal(EMIN + 1, expectedFraction.add(BigInteger.ONE)));
            }
        }
    }

    private static Binary1280 positiveNormal(int exponent, BigInteger fraction) {
        return normal(false, exponent, fraction);
    }

    private static void checkSignsAndOrder(Binary1280 a, Binary1280 b) {
        Binary1280 negativeA = a.negate(), negativeB = b.negate();
        checkPair(a, b);
        checkPair(a, negativeB);
        checkPair(negativeA, b);
        checkPair(negativeA, negativeB);
        checkPair(b, a);
        checkPair(b, negativeA);
        checkPair(negativeB, a);
        checkPair(negativeB, negativeA);
    }

    private static void checkPair(Binary1280 a, Binary1280 b) {
        var x = decode(a);
        var y = decode(b);
        var negativeY = new Dyadic(!y.negative(), y.magnitude(), y.scale());
        assertArrayEquals(sum(x, y).toBytes(), a.add(b).toBytes(), () -> description("add", a, b));
        assertArrayEquals(sum(x, negativeY).toBytes(), a.subtract(b).toBytes(),
                () -> description("subtract", a, b));
    }

    private static String description(String operation, Binary1280 a, Binary1280 b) {
        return operation + " for raw operands " + raw(a).toString(16)
                + " and " + raw(b).toString(16);
    }
}
