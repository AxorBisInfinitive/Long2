package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static love.crysh.long2.binaries.binary1280.Binary1280.*;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Binary1280MultiplyTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger FRACTION = HIDDEN.subtract(UNIT);
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();
    private static final int QMIN = EMIN - 1251;

    @Test
    void handlesSpecialValuesAndBothSigns() {
        Binary1280 payloadNaN = fromRaw(raw(POSITIVE_INFINITY).setBit(1279).setBit(7));
        for (Binary1280 x : new Binary1280[] {ZERO, NEGATIVE_ZERO, ONE, ONE.negate(),
                MIN_VALUE, MAX_VALUE, POSITIVE_INFINITY, NEGATIVE_INFINITY, payloadNaN}) {
            assertBoth(NaN, payloadNaN, x);
        }
        assertBoth(NaN, ZERO, NEGATIVE_INFINITY);
        assertBoth(NaN, NEGATIVE_ZERO, POSITIVE_INFINITY);
        assertBoth(NEGATIVE_ZERO, NEGATIVE_ZERO, MAX_VALUE);
        assertBoth(NEGATIVE_ZERO, ZERO, MIN_VALUE.negate());
        assertBoth(ZERO, NEGATIVE_ZERO, ONE.negate());
        assertBoth(POSITIVE_INFINITY, NEGATIVE_INFINITY, MIN_VALUE.negate());
        assertBoth(NEGATIVE_INFINITY, POSITIVE_INFINITY, NEGATIVE_INFINITY);
        assertThrows(NullPointerException.class, () -> NaN.multiply(null));
    }

    @Test
    void denseProductsCoverLongCarryChainsAndNormalizationAcrossTwo() {
        BigInteger alternating = new BigInteger("aaaaaaaaaaaaaaaa".repeat(20), 16).and(FRACTION);
        Binary1280 allOnes = normal(false, 0, FRACTION);
        Binary1280 almostAllOnes = normal(true, 0, FRACTION.clearBit(0).clearBit(640));
        Binary1280 striped = normal(false, -1, alternating.setBit(0));
        check(allOnes, allOnes);
        check(allOnes, almostAllOnes);
        check(allOnes, striped);
        check(fromRaw(FRACTION), allOnes);
        check(fromRaw(FRACTION.xor(UNIT.shiftLeft(1230))), striped);

        BigInteger belowTwo = HIDDEN.shiftLeft(2).divide(BigInteger.valueOf(3)).subtract(HIDDEN);
        Binary1280 factor = of(1.5);
        check(normal(false, 0, belowTwo), factor);
        check(normal(false, 0, belowTwo.add(UNIT)), factor);
        assertEquals(power(1), ONE.multiply(power(1)));
    }

    @Test
    void roundsNormalMidpointsByParityAndBetweenTheEndpoints() {
        Binary1280 factor = of(1.5);
        Binary1280 a = ONE.nextUp(), b = normal(false, 0, BigInteger.valueOf(3));
        BigInteger half = HIDDEN.shiftRight(1);
        assertBoth(normal(false, 0, half.add(BigInteger.TWO)), a, factor);
        assertBoth(normal(false, 0, half.add(BigInteger.valueOf(4))), b, factor);
        for (Binary1280 x : new Binary1280[] {a, b}) {
            check(x, factor.nextDown());
            check(x, factor.nextUp());
            check(x.negate(), factor);
        }
    }

    @Test
    void handlesZeroNormalAndInfinityMidpoints() {
        Binary1280 half = of(0.5);
        assertBoth(ZERO, MIN_VALUE, half);
        assertBoth(NEGATIVE_ZERO, MIN_VALUE.negate(), half);
        assertBoth(ZERO, MIN_VALUE, half.nextDown());
        assertBoth(MIN_VALUE, MIN_VALUE, half.nextUp());
        assertBoth(MIN_NORMAL, MIN_NORMAL, ONE.nextDown());
        assertBoth(MIN_NORMAL.nextDown(), MIN_NORMAL, ONE.nextDown().nextDown());
        check(MIN_NORMAL, ONE.nextDown().nextDown().nextDown());
        check(MIN_NORMAL.negate(), ONE.nextDown().nextUp());

        BigInteger factor = UNIT.shiftLeft(1253).subtract(UNIT).divide(BigInteger.valueOf(127));
        Binary1280 threshold = round(false, factor, UNIT, EMAX - 1252L);
        assertBoth(POSITIVE_INFINITY, of(127), threshold);
        assertBoth(NEGATIVE_INFINITY, of(-127), threshold);
        check(of(127), threshold.nextDown());
        check(of(127), threshold.nextUp());
        check(MAX_VALUE, MAX_VALUE);
        check(MIN_NORMAL, MIN_NORMAL);
    }

    @Test
    void coversCompactedWidthsAndShortProductsAtExponentLimits() {
        int[] widths = {1, 2, 24, 35, 36, 37, 52, 53, 63, 64, 65, 66, 127, 128, 129};
        for (int aw : widths) {
            for (int bw : widths) {
                BigInteger a = shortFraction(aw), b = shortFraction(bw);
                check(normal(false, -20, a), normal(true, 20, b));
                check(normal(true, EMAX, a), normal(true, 0, b));
                check(normal(true, EMIN, a), normal(false, -1, b));
                check(normal(false, EMIN, a), normal(false, -1252, b));
            }
        }
    }

    @Test
    void coversEffectiveGroupRangesAndInPlaceShiftDirections() {
        for (int i = 0; i < 20; i++) {
            BigInteger low = UNIT.shiftLeft(i * 64);
            BigInteger top = UNIT.shiftLeft(Math.min(i * 64 + 63, 1250));
            Binary1280 a = fromRaw(top.or(low));
            check(a, normal(false, 0, FRACTION));
            check(a.negate(), normal(false, 1251, FRACTION));
            check(a, fromRaw(top));
        }
        // 两侧各去十个低零组后积仍有二十组，舍入窗口需要原地左移。
        Binary1280 left = normal(false, 0, UNIT.shiftLeft(640));
        check(left, left);
        // 有效数字乘一且位权为 QMIN，二十组窗口的移位距离为零。
        check(normal(true, 1251, FRACTION), MIN_VALUE);
        check(normal(true, 0, FRACTION), normal(false, 0, FRACTION));
    }

    @Test
    void seededProductsMatchExactReferenceAcrossInputShapes() {
        Random random = new Random(0x1280_4d55L);
        int[] targets = {QMIN - 3, QMIN - 2, QMIN - 1, EMIN - 2, EMIN - 1, EMIN, EMIN + 1,
                -1, 0, 1, EMAX - 1, EMAX, EMAX + 1};
        for (int i = 0; i < 4096; i++) {
            int target = targets[(i / 4) % targets.length];
            int ea = Math.floorDiv(target, 2) + random.nextInt(-256, 257);
            int eb = target - ea;
            boolean aNegative = random.nextBoolean(), bNegative = random.nextBoolean();
            Binary1280 a, b;
            switch (i & 3) {
                case 0 -> {
                    a = normal(aNegative, ea, new BigInteger(1251, random).setBit(0));
                    b = normal(bNegative, eb, new BigInteger(1251, random).setBit(0));
                }
                case 1 -> {
                    a = normal(aNegative, ea, randomFraction(random, 1 + random.nextInt(129)));
                    b = normal(bNegative, eb, randomFraction(random, 1 + random.nextInt(129)));
                }
                case 2 -> {
                    a = normal(aNegative, ea, randomFraction(random, 1 + random.nextInt(1252)));
                    b = normal(bNegative, eb, randomFraction(random, 1 + random.nextInt(1252)));
                }
                default -> {
                    int width = 1 + random.nextInt(1251);
                    a = fromRaw(new BigInteger(width, random).setBit(width - 1));
                    if (aNegative) {
                        a = a.negate();
                    }
                    eb = Math.clamp((long) target - (QMIN + width - 1), EMIN, EMAX);
                    b = normal(bNegative, eb, new BigInteger(1251, random));
                }
            }
            check(a, b);
        }
    }

    @Test
    void fusedCancellationRetainsExactProductWords() {
        Random random = new Random(0x1280_faaL);
        for (int i = 0; i < 384; i++) {
            int ea = random.nextInt(-800, 801);
            int aw = i % 4 == 0 ? 1252 : 1 + random.nextInt(1252);
            int bw = i % 4 == 0 ? 1252 : 1 + random.nextInt(1252);
            Binary1280 a = normal(random.nextBoolean(), ea, randomFraction(random, aw));
            Binary1280 b = normal(random.nextBoolean(), -ea, randomFraction(random, bw));
            Binary1280 c = multiply(a, b).negate();
            Dyadic x = decode(a), y = decode(b);
            Dyadic product = new Dyadic(x.negative() ^ y.negative(),
                    x.magnitude().multiply(y.magnitude()), x.scale() + y.scale());
            Binary1280 expected = sum(product, decode(c));
            assertArrayEquals(expected.toBytes(), fma(a, b, c).toBytes());
            assertArrayEquals(expected.toBytes(), fma(b, a, c).toBytes());
        }
    }

    private static BigInteger shortFraction(int width) {
        return UNIT.shiftLeft(width - 1).subtract(UNIT).shiftLeft(1252 - width);
    }

    private static BigInteger randomFraction(Random random, int width) {
        return width == 1 ? BigInteger.ZERO
                : new BigInteger(width - 1, random).setBit(0).shiftLeft(1252 - width);
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
