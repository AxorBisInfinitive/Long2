package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static love.crysh.long2.binaries.binary1280.Binary1280.*;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Binary1280HighWindowMultiplyTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger THREE_HALVES = HIDDEN.add(HIDDEN.shiftRight(1));
    private static final BigInteger WORD_MASK = UNIT.shiftLeft(64).subtract(UNIT);
    private static final BigInteger ERROR = BigInteger.valueOf(20).shiftLeft(19 * 64);
    private static final BigInteger HALF_DIAGONAL_ERROR = BigInteger.valueOf(37).shiftLeft(19 * 64);
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();

    @Test
    void strictLowerBoundAtEvenMidpointRoundsUpInBothBinades() {
        BigInteger[][] pairs = {
                {HIDDEN.add(UNIT), THREE_HALVES.add(UNIT)},
                {THREE_HALVES.add(BigInteger.valueOf(3)), THREE_HALVES.add(BigInteger.valueOf(3))}
        };
        for (int i = 0; i < pairs.length; i++) {
            BigInteger a = pairs[i][0], b = pairs[i][1];
            int discarded = 1251 + i;
            BigInteger half = UNIT.shiftLeft(discarded - 1), step = half.shiftLeft(1);
            for (boolean halfDiagonal : new boolean[] {false, true}) {
                BigInteger lower = lowerBound(a, b, halfDiagonal);
                assertEquals(half, lower.mod(step));
                assertFalse(lower.testBit(discarded));
                assertTrue(a.multiply(b).mod(step).compareTo(half) > 0);
            }
            check(a, b, EMIN);
            check(a, b, EMAX - 1);
        }
    }

    @Test
    void crossesRepresentableValuesWithinAndBetweenBinades() {
        BigInteger belowTwo = HIDDEN.shiftLeft(1).subtract(BigInteger.TWO);
        BigInteger a = HIDDEN.add(UNIT);
        assertCrossesRepresentableValue(a, belowTwo, HIDDEN.multiply(HIDDEN).shiftLeft(1));
        assertBoth(power(1), value(false, 0, a), value(false, 0, belowTwo));
        check(a, belowTwo, EMAX - 1);

        a = THREE_HALVES.add(UNIT);
        BigInteger b = THREE_HALVES.subtract(UNIT);
        assertCrossesRepresentableValue(a, b, THREE_HALVES.multiply(THREE_HALVES));
        check(a, b, 0);
    }

    @Test
    void upperBoundCanReachMidpointBeforeExactProductDoes() {
        BigInteger a = HIDDEN.add(UNIT), b = THREE_HALVES.subtract(ERROR).add(UNIT);
        BigInteger lower = lowerBound(a, b, false);
        assertEquals(HIDDEN.shiftRight(1), lower.add(ERROR).mod(HIDDEN));
        assertTrue(a.multiply(b).mod(HIDDEN).compareTo(HIDDEN.shiftRight(1)) < 0);
        check(a, b, EMIN);
        check(a, b.add(UNIT.shiftLeft(19 * 64)), -1);
    }

    @Test
    void highAndLowHalvesOfBoundaryColumnMatterAroundMidpoint() {
        BigInteger a = HIDDEN.add(WORD_MASK), fraction = THREE_HALVES.divide(WORD_MASK);
        BigInteger half = HIDDEN.shiftRight(1);
        for (int offset = 0; offset <= 1; offset++) {
            BigInteger b = HIDDEN.add(fraction).add(BigInteger.valueOf(offset));
            BigInteger columnTerm = WORD_MASK.multiply(b.shiftRight(18 * 64).and(WORD_MASK));
            assertTrue(columnTerm.bitLength() > 64);
            assertTrue(columnTerm.and(WORD_MASK).signum() != 0);
            BigInteger lower = lowerBound(a, b, true), exact = a.multiply(b);
            assertTrue(lower.compareTo(lowerBound(a, b, false)) < 0);
            assertTrue(exact.compareTo(lower.add(HALF_DIAGONAL_ERROR)) < 0);
            assertTrue(lower.mod(HIDDEN).compareTo(half) < 0);
            assertTrue(lower.add(HALF_DIAGONAL_ERROR).mod(HIDDEN).compareTo(half) > 0);
            assertEquals(offset == 0 ? -1 : 1, exact.mod(HIDDEN).compareTo(half));
            check(a, b, 0);
        }
    }

    @Test
    void carriesThroughThreeGroupsAndAcrossTheEntireWindow() {
        BigInteger lowThree = UNIT.shiftLeft(192).subtract(UNIT);
        BigInteger offset = UNIT.shiftLeft(611).add(BigInteger.valueOf(3).modInverse(UNIT.shiftLeft(193)));
        BigInteger a = THREE_HALVES.subtract(offset);
        assertCertainIncrement(a, a, 1252, lowThree);
        check(a, a, -2);
        check(a, a, EMAX - 1);

        offset = UNIT.shiftLeft(611).add(UNIT);
        a = HIDDEN.add(offset);
        BigInteger b = HIDDEN.subtract(offset).shiftLeft(1);
        assertCertainIncrement(a, b, 1251, HIDDEN.shiftLeft(1).subtract(UNIT));
        assertBoth(power(EMAX), value(false, EMAX - 1, a), value(false, 0, b));
        assertBoth(power(EMIN + 1).negate(), value(true, EMIN, a), value(false, 0, b));
    }

    @Test
    void seededInputsOnBothSidesOfMidpointMatchExactReference() {
        Random random = new Random(0x1280_b018L);
        int[] exponents = {EMIN, -1, 0, EMAX - 1};
        for (int i = 0; i < 256; i++) {
            BigInteger low = new BigInteger(64, random).setBit(63).setBit(0);
            BigInteger fraction = THREE_HALVES.divide(low);
            BigInteger a = HIDDEN.add(low);
            int exponent = exponents[i % exponents.length];
            check(a, HIDDEN.add(fraction), exponent);
            check(a, HIDDEN.add(fraction).add(UNIT), exponent);
        }
    }

    private static void assertCrossesRepresentableValue(BigInteger a, BigInteger b, BigInteger gridPoint) {
        BigInteger lower = lowerBound(a, b, false);
        assertTrue(lower.compareTo(gridPoint) < 0);
        assertTrue(lower.add(ERROR).compareTo(gridPoint) > 0);
    }

    private static void assertCertainIncrement(BigInteger a, BigInteger b, int discarded, BigInteger mask) {
        BigInteger lower = lowerBound(a, b, false);
        assertEquals(lower.shiftRight(discarded - 1), lower.add(ERROR).shiftRight(discarded - 1));
        BigInteger half = UNIT.shiftLeft(discarded - 1);
        assertTrue(lower.mod(half.shiftLeft(1)).compareTo(half) > 0);
        assertEquals(mask, lower.shiftRight(discarded).and(mask));
    }

    private static BigInteger lowerBound(BigInteger a, BigInteger b, boolean halfDiagonal) {
        BigInteger omitted = BigInteger.ZERO;
        for (int i = 0; i < 18; i++) {
            BigInteger left = a.shiftRight(64 * i).and(WORD_MASK);
            BigInteger right = b.and(UNIT.shiftLeft(64 * (18 - i)).subtract(UNIT));
            omitted = omitted.add(left.multiply(right).shiftLeft(64 * i));
        }
        if (halfDiagonal) {
            for (int i = 0; i <= 18; i++) {
                BigInteger left = a.shiftRight(64 * i).and(WORD_MASK);
                BigInteger right = b.shiftRight(64 * (18 - i)).and(WORD_MASK);
                omitted = omitted.add(left.multiply(right).and(WORD_MASK).shiftLeft(18 * 64));
            }
        }
        return a.multiply(b).subtract(omitted);
    }

    private static Binary1280 value(boolean negative, int exponent, BigInteger significand) {
        return normal(negative, exponent, significand.subtract(HIDDEN));
    }

    private static void check(BigInteger a, BigInteger b, int exponent) {
        Binary1280 x = value(false, exponent, a), y = value(true, 0, b);
        assertBoth(multiply(x, y), x, y);
    }

    private static void assertBoth(Binary1280 expected, Binary1280 a, Binary1280 b) {
        byte[] bits = expected.toBytes();
        assertArrayEquals(bits, a.multiply(b).toBytes());
        assertArrayEquals(bits, b.multiply(a).toBytes());
    }
}
