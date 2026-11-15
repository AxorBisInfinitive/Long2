package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static com.inf.long2.binaries.binary1280.Binary1280.*;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Binary1280DenseMultiplyTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger MAX_SIGNIFICAND = HIDDEN.shiftLeft(1).subtract(UNIT);
    private static final BigInteger THREE_HALVES = HIDDEN.add(HIDDEN.shiftRight(1));
    private static final BigInteger HALF_MASK = UNIT.shiftLeft(640).subtract(UNIT);
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();
    private static final int QMIN = EMIN - 1251;

    @Test
    void roundsAcrossTheWholeSignificandAndExponentLimit() {
        BigInteger nearTwo = MAX_SIGNIFICAND.subtract(UNIT);
        Binary1280 a = value(false, 0, HIDDEN.add(UNIT));
        // (H+1)(2H-2) = 2H²-2，从低位一路进位到下一指数区间。
        assertBoth(power(1), a, value(false, 0, nearTwo));
        check(a, value(false, 0, nearTwo.subtract(UNIT)));
        check(a, value(false, 0, nearTwo.add(UNIT)));
        Binary1280 huge = value(false, EMAX, HIDDEN.add(UNIT));
        assertBoth(POSITIVE_INFINITY, huge, value(false, 0, nearTwo));
        checkFused(huge, value(false, 0, nearTwo), MAX_VALUE.negate());
        assertBoth(MAX_VALUE.negate(),
                value(false, EMAX, HIDDEN.add(UNIT)), value(true, 0, nearTwo.subtract(UNIT)));
        assertBoth(MIN_NORMAL, value(false, EMIN, HIDDEN.add(UNIT)), value(false, -1, nearTwo));
    }

    @Test
    void retainsStickyWithSixtyThreeTrailingZerosOnEachSide() {
        BigInteger lowGroup = UNIT.shiftLeft(63);
        Binary1280 a = value(false, 0, HIDDEN.add(lowGroup));
        for (BigInteger sig : new BigInteger[] {HIDDEN.add(lowGroup), THREE_HALVES.add(lowGroup),
                MAX_SIGNIFICAND.andNot(UNIT.shiftLeft(63).subtract(UNIT))}) {
            check(a, value(true, 0, sig));
            check(value(false, 0, sig), value(true, 0, sig));
        }
    }

    @Test
    void checksSameAndOppositeHalfDifferencesAndZeroDifference() {
        BigInteger evenHigh = HIDDEN.shiftRight(640), oddHigh = evenHigh.add(UNIT);
        Binary1280[] inputs = {
                byHalves(evenHigh, evenHigh.subtract(UNIT)),
                byHalves(evenHigh, evenHigh.add(UNIT)),
                byHalves(oddHigh, oddHigh),
                byHalves(oddHigh, UNIT),
                byHalves(oddHigh, HALF_MASK),
                byHalves(HALF_MASK.shiftRight(28), HALF_MASK)
        };
        for (Binary1280 a : inputs) {
            for (Binary1280 b : inputs) {
                check(a, b.negate());
                checkFusedResidual(a, b);
            }
        }
    }

    @Test
    void straddlesMidpointsInBothProductBinades() {
        BigInteger[] left = {HIDDEN.add(UNIT), HIDDEN.add(UNIT),
                THREE_HALVES.add(BigInteger.valueOf(3)), THREE_HALVES.subtract(UNIT)};
        BigInteger[] right = {THREE_HALVES.subtract(UNIT), THREE_HALVES.add(UNIT),
                THREE_HALVES.add(BigInteger.valueOf(3)), THREE_HALVES.add(BigInteger.valueOf(3))};
        int[] offsets = {-1, 1, 9, -3};
        for (int i = 0; i < left.length; i++) {
            BigInteger product = left[i].multiply(right[i]);
            int discarded = product.bitLength() - 1252;
            assertEquals(i < 2 ? 1251 : 1252, discarded);
            BigInteger step = UNIT.shiftLeft(discarded);
            assertEquals(BigInteger.valueOf(offsets[i]), product.mod(step).subtract(step.shiftRight(1)));
            assertNotEquals(roundWindowEndpoint(left[i], right[i], false),
                    roundWindowEndpoint(left[i], right[i], true));
            check(value(false, 0, left[i]), value(i % 2 != 0, 0, right[i]));
        }

        BigInteger a = HIDDEN.add(UNIT), b = HIDDEN.add(BigInteger.valueOf(5));
        assertEquals(roundWindowEndpoint(a, b, false), roundWindowEndpoint(a, b, true));
        check(value(false, 0, a), value(false, 0, b));
    }

    @Test
    void coversSubnormalOperandsAndSubnormalResultsWithFullWordRanges() {
        BigInteger highBit = UNIT.shiftLeft(1216);
        for (BigInteger subnormal : new BigInteger[] {highBit.add(UNIT), HIDDEN.subtract(UNIT)}) {
            Binary1280 a = fromRaw(subnormal);
            Binary1280 b = value(true, 1240, MAX_SIGNIFICAND);
            check(a, b);
            checkFusedResidual(a, b);
            assertBoth(NEGATIVE_ZERO, a, a.negate());
        }
        BigInteger plusOne = HIDDEN.add(UNIT);
        assertBoth(MIN_VALUE, value(false, EMIN, plusOne), value(false, -1252, plusOne));
        assertBoth(NEGATIVE_ZERO, value(true, EMIN, plusOne), value(false, -1253, plusOne));
        assertBoth(MIN_VALUE.negate(),
                value(true, EMIN, MAX_SIGNIFICAND), value(false, -1253, MAX_SIGNIFICAND));
    }

    @Test
    void seededDenseProductsMatchExactReference() {
        Random random = new Random(0x1280_d3a5eL);
        int[] targets = {QMIN - 3, QMIN - 2, QMIN - 1, EMIN - 2, EMIN - 1, EMIN, EMIN + 1,
                -250, -1, 0, 1, 250, EMAX - 2, EMAX - 1, EMAX, EMAX + 1};
        for (int i = 0; i < 4096; i++) {
            int target = targets[i % targets.length];
            int ea = Math.floorDiv(target, 2) + random.nextInt(-256, 257);
            int eb = target - ea;
            Binary1280 a = value(random.nextBoolean(), ea, denseSignificand(random, i % 64));
            Binary1280 b = value(random.nextBoolean(), eb, denseSignificand(random, (i / 64) % 64));
            check(a, b);
        }
    }

    @Test
    void seededDenseFusedCancellationMatchesExactProductResidual() {
        Random random = new Random(0x1280_d3fAAL);
        for (int i = 0; i < 512; i++) {
            int exponent = random.nextInt(-2000, 2001);
            Binary1280 a = value(random.nextBoolean(), exponent, denseSignificand(random, i % 64));
            Binary1280 b = value(random.nextBoolean(), -exponent, denseSignificand(random, (i / 8) % 64));
            checkFusedResidual(a, b);
        }
    }

    private static BigInteger denseSignificand(Random random, int zeros) {
        return new BigInteger(1251, random).setBit(1251).shiftRight(zeros).setBit(0).shiftLeft(zeros);
    }

    private static BigInteger roundWindowEndpoint(BigInteger a, BigInteger b, boolean upper) {
        BigInteger bound = a.multiply(b).subtract(omittedLowColumns(a, b));
        if (upper) {
            bound = bound.add(BigInteger.valueOf(20).shiftLeft(19 * 64));
        }
        return raw(round(false, bound, UNIT, -2502));
    }

    /** 仅用于独立构造区间跨舍入中点的测试样本。 */
    private static BigInteger omittedLowColumns(BigInteger a, BigInteger b) {
        BigInteger omitted = BigInteger.ZERO, wordMask = UNIT.shiftLeft(64).subtract(UNIT);
        for (int i = 0; i < 18; i++) {
            BigInteger left = a.shiftRight(64 * i).and(wordMask);
            BigInteger right = b.and(UNIT.shiftLeft(64 * (18 - i)).subtract(UNIT));
            omitted = omitted.add(left.multiply(right).shiftLeft(64 * i));
        }
        return omitted;
    }

    private static Binary1280 byHalves(BigInteger high, BigInteger low) {
        return value(false, 0, high.shiftLeft(640).or(low));
    }

    private static Binary1280 value(boolean negative, int exponent, BigInteger significand) {
        return normal(negative, exponent, significand.subtract(HIDDEN));
    }

    private static void checkFusedResidual(Binary1280 a, Binary1280 b) {
        checkFused(a, b, multiply(a, b).negate());
    }

    private static void checkFused(Binary1280 a, Binary1280 b, Binary1280 c) {
        Dyadic x = decode(a), y = decode(b);
        Dyadic product = new Dyadic(x.negative() ^ y.negative(),
                x.magnitude().multiply(y.magnitude()), x.scale() + y.scale());
        byte[] expected = sum(product, decode(c)).toBytes();
        assertArrayEquals(expected, fma(a, b, c).toBytes());
        assertArrayEquals(expected, fma(b, a, c).toBytes());
    }

    private static void check(Binary1280 a, Binary1280 b) {
        assertBoth(multiply(a, b), a, b);
    }

    private static void assertBoth(Binary1280 expected, Binary1280 a, Binary1280 b) {
        assertArrayEquals(expected.toBytes(), a.multiply(b).toBytes());
        assertArrayEquals(expected.toBytes(), b.multiply(a).toBytes());
    }
}
