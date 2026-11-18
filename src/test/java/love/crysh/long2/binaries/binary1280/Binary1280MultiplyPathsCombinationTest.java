package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static love.crysh.long2.binaries.binary1280.Binary1280.*;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Binary1280MultiplyPathsCombinationTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger THREE_HALVES = HIDDEN.add(HIDDEN.shiftRight(1));
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();

    @Test
    void roundsAroundTwoWithCompactNextWidthAndDenseInputs() {
        BigInteger two = HIDDEN.multiply(HIDDEN).shiftLeft(1);
        for (int width : new int[] {53, 64, 65, 1252}) {
            BigInteger shortOrDense = THREE_HALVES.setBit(1252 - width);
            BigInteger near = two.divide(shortOrDense);
            BigInteger below = near.subtract(BigInteger.valueOf(3)).setBit(0);
            BigInteger above = near.add(UNIT).setBit(0);
            assertTrue(shortOrDense.multiply(below).compareTo(two) < 0);
            assertTrue(shortOrDense.multiply(above).compareTo(two) > 0);
            for (int exponent : new int[] {EMIN, 0, EMAX - 1}) {
                Binary1280 a = value(true, exponent, shortOrDense);
                check(a, value(false, 0, below));
                check(a, value(false, 0, above));
            }
            Binary1280 high = value(false, EMAX, shortOrDense);
            assertTrue(multiply(high, value(false, 0, below)).isFinite());
            check(high, value(false, 0, below));
            assertBoth(NEGATIVE_INFINITY, high, value(true, 0, above));
        }
    }

    @Test
    void roundsExactMidpointsOfCompactAndFallbackWidthsByParity() {
        for (int width : new int[] {53, 64, 65}) {
            BigInteger a = HIDDEN.setBit(1252 - width);
            for (int odd = 0; odd <= 1; odd++) {
                BigInteger b = THREE_HALVES.add(BigInteger.valueOf(2L * odd + 1).shiftLeft(width - 2));
                BigInteger[] parts = a.multiply(b).divideAndRemainder(HIDDEN);
                assertEquals(HIDDEN.shiftRight(1), parts[1]);
                assertEquals(odd != 0, parts[0].testBit(0));
                Binary1280 x = value(true, 0, a);
                assertBoth(value(true, 0, parts[0].add(BigInteger.valueOf(odd))), x, value(false, 0, b));
                assertBoth(value(true, 0, parts[0].subtract(UNIT)), x, value(false, 0, b.subtract(UNIT)));
                assertBoth(value(true, 0, parts[0].add(BigInteger.TWO)), x, value(false, 0, b.add(UNIT)));
            }
        }
    }

    @Test
    void denseMidpointFallbackRecoversTheLowestBitsInBothOrders() {
        BigInteger a = HIDDEN.add(UNIT), b = THREE_HALVES;
        checkDenseMidpoint(a, b.subtract(UNIT), 1251, -1);
        checkDenseMidpoint(a, b.add(UNIT), 1251, 1);
        BigInteger c = THREE_HALVES.add(BigInteger.valueOf(3));
        checkDenseMidpoint(b.subtract(UNIT), c, 1252, -3);
        checkDenseMidpoint(c, c, 1252, 9);
    }

    @Test
    void mixedWidthsCrossTheNormalBoundaryWithASubnormalOperand() {
        BigInteger threshold = HIDDEN.multiply(HIDDEN).subtract(HIDDEN.shiftRight(1));
        for (int width : new int[] {53, 64, 65}) {
            BigInteger a = THREE_HALVES.setBit(1252 - width);
            BigInteger below = threshold.subtract(UNIT).divide(a);
            Binary1280 factor = value(true, 0, a);
            Binary1280 subnormal = fromRaw(below), normalResultOperand = fromRaw(below.add(UNIT));
            Binary1280 expectedLow = multiply(factor, subnormal), expectedHigh = multiply(factor, normalResultOperand);
            assertTrue(expectedLow.isSubnormal());
            assertTrue(expectedHigh.isNormal());
            assertBoth(expectedLow, factor, subnormal);
            assertBoth(expectedHigh, factor, normalResultOperand);
        }
    }

    @Test
    void seededCorrelatedInputsExerciseAllWidthDispatches() {
        Random random = new Random(0x4053_c064L);
        int[] exponents = {EMIN - 1, EMIN, -1253, -1, 0, EMAX - 1, EMAX};
        for (int i = 0; i < 512; i++) {
            int exponent = exponents[i % exponents.length];
            int wideExponent = Math.floorDiv(exponent, 2) + random.nextInt(-512, 513);
            BigInteger dense = new BigInteger(1251, random).setBit(1251).setBit(0);
            Binary1280 wide = value(random.nextBoolean(), wideExponent, dense);
            BigInteger prefix = new BigInteger(65, random).setBit(64);
            boolean negative = random.nextBoolean();
            for (int width : new int[] {53, 64, 65}) {
                BigInteger shortSignificand = prefix.shiftRight(65 - width).setBit(0).shiftLeft(1252 - width);
                check(value(negative, exponent - wideExponent, shortSignificand), wide);
            }
            check(value(negative, exponent - wideExponent, prefix.shiftLeft(1187).setBit(0)), wide);
        }
    }

    private static void checkDenseMidpoint(BigInteger a, BigInteger b, int discarded, long offset) {
        BigInteger step = UNIT.shiftLeft(discarded);
        assertEquals(BigInteger.valueOf(offset), a.multiply(b).mod(step).subtract(step.shiftRight(1)));
        check(value(false, EMIN, a), value(true, 0, b));
        check(value(true, EMAX - 1, a), value(false, 0, b));
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
