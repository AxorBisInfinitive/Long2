package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static love.crysh.long2.binaries.binary1280.Binary1280.*;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Binary1280MultiplyCarryAndWord65Test {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger MAX_SIGNIFICAND = HIDDEN.shiftLeft(1).subtract(UNIT);
    private static final BigInteger WORD_MASK = UNIT.shiftLeft(64).subtract(UNIT);
    private static final BigInteger TWO = HIDDEN.multiply(HIDDEN).shiftLeft(1);
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();

    @Test
    void denseWindowCarriesAcrossCutAndAcrossMaximalWords() {
        for (int i = 0; i < 19; i++) {
            BigInteger group = WORD_MASK.shiftLeft(64 * i);
            BigInteger a = MAX_SIGNIFICAND.xor(group).setBit(0);
            BigInteger b = HIDDEN.or(WORD_MASK.shiftLeft(64 * (18 - i))).setBit(0);
            check(value(false, 0, a), value(true, 0, b));
            check(value(true, EMIN, a), value(false, 0, MAX_SIGNIFICAND));
            check(value(false, EMAX - 1, MAX_SIGNIFICAND), value(false, 0, b));
        }
        check(value(false, 0, MAX_SIGNIFICAND), value(true, 0, MAX_SIGNIFICAND));
    }

    @Test
    void word65RoundsExactMidpointsWithNonzeroLowestDenseGroup() {
        Binary1280 a = value(true, 0, HIDDEN.setBit(1187));
        BigInteger coefficient = UNIT.setBit(64);
        for (int parity = 0; parity < 2; parity++) {
            BigInteger sig = HIDDEN.add(BigInteger.valueOf(2L * parity + 1).shiftLeft(63));
            BigInteger[] division = coefficient.multiply(sig).divideAndRemainder(UNIT.shiftLeft(64));
            assertEquals(UNIT.shiftLeft(63), division[1]);
            assertEquals(parity != 0, division[0].testBit(0));
            check(a, value(false, 0, sig.subtract(UNIT)));
            check(a, value(false, 0, sig));
            check(a, value(false, 0, sig.add(UNIT)));
        }
    }

    @Test
    void word65CrossesSignedZeroNormalAndOverflowBoundaries() {
        BigInteger left = HIDDEN.add(HIDDEN.shiftRight(1)).setBit(1187);
        BigInteger tiny = TWO.divide(left);
        Binary1280 belowZero = value(false, -1253, tiny.subtract(UNIT).setBit(0));
        Binary1280 aboveZero = value(false, -1253, tiny.add(BigInteger.TWO).setBit(0));
        Binary1280 small = value(true, EMIN, left);
        assertBoth(NEGATIVE_ZERO, small, belowZero);
        assertBoth(MIN_VALUE.negate(), small, aboveZero);

        BigInteger normalBoundary = TWO.subtract(HIDDEN).divide(left);
        Binary1280 belowNormal = value(false, -1, normalBoundary.subtract(UNIT).setBit(0));
        Binary1280 aboveNormal = value(false, -1, normalBoundary.add(BigInteger.TWO).setBit(0));
        assertTrue(multiply(small, belowNormal).isSubnormal());
        assertTrue(multiply(small, aboveNormal).isNormal());
        check(small, belowNormal);
        check(small, aboveNormal);

        BigInteger overflow = TWO.subtract(HIDDEN.shiftRight(1)).divide(left);
        Binary1280 large = value(true, EMAX, left);
        Binary1280 belowOverflow = value(false, 0, overflow.subtract(UNIT).setBit(0));
        assertTrue(multiply(large, belowOverflow).isFinite());
        check(large, belowOverflow);
        assertBoth(NEGATIVE_INFINITY, large, value(false, 0, overflow.add(BigInteger.TWO).setBit(0)));
    }

    @Test
    void exactWidthAndDenseEligibilityAreIndependentOfOperandOrder() {
        for (int width : new int[] {63, 64, 65, 66, 67, 1252}) {
            BigInteger sig = UNIT.shiftLeft(width).subtract(UNIT).shiftLeft(1252 - width);
            Binary1280 a = value(true, 0, sig);
            for (Binary1280 b : new Binary1280[] {
                    value(false, 0, MAX_SIGNIFICAND), value(true, 0, MAX_SIGNIFICAND.clearBit(0)),
                    value(false, 0, MAX_SIGNIFICAND.andNot(WORD_MASK)), MIN_NORMAL,
                    MIN_VALUE, MIN_NORMAL.nextDown(), ZERO, NEGATIVE_ZERO}) {
                check(a, b);
            }
            assertBoth(NaN, a, NaN);
            assertBoth(NEGATIVE_INFINITY, a, POSITIVE_INFINITY);
        }
    }

    @Test
    void seededWidthsAndExponentPairsMatchExactReference() {
        Random random = new Random(0x130e_d065L);
        int[] sums = {EMIN - 1253, EMIN - 1252, EMIN - 1, EMIN, -1, 0, EMAX - 1, EMAX, EMAX + 1};
        for (int i = 0; i < 768; i++) {
            int total = sums[i % sums.length];
            int exponent = Math.floorDiv(total, 2) + random.nextInt(-256, 257);
            int width = i % 4 == 0 ? 64 : i % 4 == 1 ? 66 : 65;
            BigInteger sig = new BigInteger(width - 1, random).setBit(width - 1).setBit(0).shiftLeft(1252 - width);
            BigInteger dense = new BigInteger(1251, random).setBit(1251).setBit(0);
            check(value(random.nextBoolean(), total - exponent, sig), value(random.nextBoolean(), exponent, dense));
        }
    }

    private static Binary1280 value(boolean negative, int exponent, BigInteger significand) {
        return normal(negative, exponent, significand.subtract(HIDDEN));
    }

    private static void check(Binary1280 a, Binary1280 b) {
        assertBoth(multiply(a, b), a, b);
    }

    private static void assertBoth(Binary1280 expected, Binary1280 a, Binary1280 b) {
        assertArrayEquals(expected.toBytes(), a.multiply(b).toBytes());
        assertArrayEquals(expected.toBytes(), b.multiply(a).toBytes());
    }
}
