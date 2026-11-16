package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static com.inf.long2.binaries.binary1280.Binary1280.*;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Binary1280MultiplyPathsTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger MAX_SIGNIFICAND = HIDDEN.shiftLeft(1).subtract(UNIT);
    private static final BigInteger THREE_HALVES = HIDDEN.add(HIDDEN.shiftRight(1));
    private static final BigInteger WORD_MASK = UNIT.shiftLeft(64).subtract(UNIT);
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();

    @Test
    void denseRowsCarryThroughEachCommonRangeAndItsExtraColumn() {
        for (int row = 0; row < 18; row += 2) {
            int start = 18 - row;
            BigInteger x = HIDDEN.or(UNIT).or(WORD_MASK.shiftLeft(64 * row))
                    .or(WORD_MASK.shiftLeft(64 * (row + 1)));
            BigInteger y = HIDDEN.or(UNIT).or(WORD_MASK.shiftLeft(64 * (start - 1)))
                    .or(WORD_MASK.shiftLeft(64 * start));
            check(value(false, 0, x), value(true, 3, y));
            check(value(true, EMIN, x.or(y)), value(false, 0, MAX_SIGNIFICAND));
        }
        check(value(false, 0, MAX_SIGNIFICAND), value(false, 0, MAX_SIGNIFICAND));
    }

    @Test
    void denseReadsHiddenBitAndAllTopFractionBitsInBothOrders() {
        BigInteger x = HIDDEN.or(UNIT);
        BigInteger y = HIDDEN.or(UNIT.shiftLeft(35).subtract(UNIT).shiftLeft(1216))
                .or(WORD_MASK.shiftLeft(18 * 64)).or(WORD_MASK);
        check(value(true, EMIN, x), value(false, 0, y));
        check(value(false, EMAX, y), value(true, -1, x));
        check(value(false, EMIN + 2, x), value(false, -3, y));

        // 两种正规化位移的中点两侧；完整积必须带回窗口省略的低位。
        BigInteger a = HIDDEN.add(UNIT);
        BigInteger b = THREE_HALVES.subtract(UNIT);
        BigInteger c = THREE_HALVES.add(BigInteger.valueOf(3));
        assertMidpointOffset(a, b, 1251, -1);
        assertMidpointOffset(a, b.add(BigInteger.TWO), 1251, 1);
        assertMidpointOffset(b, c, 1252, -3);
        assertMidpointOffset(c, c, 1252, 9);
        check(value(false, -2, a), value(true, 1, b));
        check(value(false, -2, a), value(true, 1, b.add(BigInteger.TWO)));
        check(value(true, EMAX, b), value(false, -1, c));
        check(value(true, EMAX, c), value(false, -1, c));
    }

    @Test
    void compactOperandTransitionsAtSixtyFourAndSixtyFiveBits() {
        int[] widths = {1, 24, 35, 36, 37, 53, 63, 64, 65, 66};
        for (int width : widths) {
            Binary1280 a = value(true, -24, significand(width));
            for (int otherWidth : new int[] {53, 64, 65, 128, 129, 640, 1252}) {
                Binary1280 b = value(false, 25, significand(otherWidth));
                check(a, b);
            }
        }
    }

    @Test
    void compactByWideHandlesSignedZeroMinimumNormalAndOverflow() {
        BigInteger belowFourThirds = HIDDEN.shiftLeft(2).divide(BigInteger.valueOf(3));
        BigInteger aboveFourThirds = belowFourThirds.add(UNIT);
        Binary1280 small = value(true, EMIN, THREE_HALVES);
        assertBoth(NEGATIVE_ZERO, small, value(false, -1253, belowFourThirds));
        assertBoth(MIN_VALUE.negate(), small, value(false, -1253, aboveFourThirds));
        assertBoth(MIN_NORMAL.negate(), small, value(false, -1, belowFourThirds));
        assertBoth(MIN_NORMAL.nextDown().negate(), small, value(false, -1, belowFourThirds.subtract(UNIT)));

        Binary1280 huge = value(false, EMAX, THREE_HALVES);
        assertBoth(MAX_VALUE, huge, value(false, 0, belowFourThirds));
        assertBoth(NEGATIVE_INFINITY, huge, value(true, 0, aboveFourThirds));
    }

    @Test
    void compactBySubnormalReadsOnlyTheEffectiveRange() {
        BigInteger shortSignificand = significand(64);
        Binary1280 factor = value(true, 1200, shortSignificand);
        for (int group = 0; group < 20; group++) {
            int low = group * 64, high = Math.min(low + 63, 1250);
            BigInteger twoBits = UNIT.shiftLeft(low).setBit(high);
            check(factor, fromRaw(twoBits));
            check(factor, fromRaw(UNIT.shiftLeft(low)).negate());
        }
        check(factor, fromRaw(HIDDEN.subtract(UNIT)));
    }

    @Test
    void seededMixedWidthsMatchExactReference() {
        Random random = new Random(0x4053_1280aL);
        int[] exponents = {EMIN, -1253, -1, 0, 1, 1251, EMAX - 1, EMAX};
        for (int i = 0; i < 2048; i++) {
            int width = 1 + random.nextInt(65);
            int ea = exponents[i % exponents.length], eb = exponents[(i / exponents.length) % exponents.length];
            Binary1280 a = value(random.nextBoolean(), ea, randomSignificand(random, width));
            Binary1280 b;
            if ((i & 3) == 0) {
                int subnormalWidth = 1 + random.nextInt(1251);
                b = fromRaw(new BigInteger(subnormalWidth, random).setBit(subnormalWidth - 1));
                if (random.nextBoolean()) {
                    b = b.negate();
                }
            } else {
                b = value(random.nextBoolean(), eb, randomSignificand(random, random.nextInt(65, 1253)));
            }
            check(a, b);
        }
    }

    private static void assertMidpointOffset(BigInteger a, BigInteger b, int discarded, long offset) {
        BigInteger step = UNIT.shiftLeft(discarded);
        assertEquals(BigInteger.valueOf(offset), a.multiply(b).mod(step).subtract(step.shiftRight(1)));
    }

    private static BigInteger significand(int width) {
        return UNIT.shiftLeft(width).subtract(UNIT).shiftLeft(1252 - width);
    }

    private static BigInteger randomSignificand(Random random, int width) {
        return new BigInteger(width, random).setBit(width - 1).setBit(0).shiftLeft(1252 - width);
    }

    private static Binary1280 value(boolean negative, int exponent, BigInteger significand) {
        return normal(negative, exponent, significand.subtract(HIDDEN));
    }

    private static void check(Binary1280 a, Binary1280 b) {
        assertBoth(multiply(a, b), a, b);
    }

    private static void assertBoth(Binary1280 expected, Binary1280 a, Binary1280 b) {
        byte[] bits = expected.toBytes();
        assertArrayEquals(bits, a.multiply(b).toBytes());
        assertArrayEquals(bits, b.multiply(a).toBytes());
    }
}
