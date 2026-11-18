package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static love.crysh.long2.binaries.binary1280.Binary1280.*;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Binary1280MultiplyCombinationTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final int EMIN = FORMAT.minExponent(), EMAX = FORMAT.maxExponent();
    private static final int SHORT_BOUNDARY = 1252 - Long.SIZE;

    @Test
    void bothRoutesRoundOnEitherSideOfSubnormalMidpoints() {
        Binary1280 factor = power(-1250);
        BigInteger quarter = HIDDEN.shiftRight(2), threeQuarters = quarter.multiply(BigInteger.valueOf(3));
        assertBoth(fromRaw(BigInteger.TWO), normal(false, EMIN, quarter), factor);
        assertBoth(fromRaw(BigInteger.valueOf(4)), normal(false, EMIN, threeQuarters), factor);
        // 保留同一个中点，只改变最低非零位的位置，让输入分别落入短乘法和有效组路径。
        for (int bit : new int[] {SHORT_BOUNDARY + 1, SHORT_BOUNDARY, SHORT_BOUNDARY - 1, 1152, 1151, 0}) {
            BigInteger step = UNIT.shiftLeft(bit);
            assertBoth(fromRaw(BigInteger.TWO),
                    normal(false, EMIN, quarter.subtract(step)), factor);
            assertBoth(fromRaw(BigInteger.valueOf(3)),
                    normal(false, EMIN, quarter.add(step)), factor);
            assertBoth(fromRaw(BigInteger.valueOf(3)).negate(),
                    normal(true, EMIN, threeQuarters.subtract(step)), factor);
            assertBoth(fromRaw(BigInteger.valueOf(4)).negate(),
                    normal(true, EMIN, threeQuarters.add(step)), factor);
        }
    }

    @Test
    void tinyProductsPreserveHalfOfMinimumAndItsSignedNeighbors() {
        assertBoth(ZERO, MIN_NORMAL, power(-1253));
        assertBoth(ZERO, MIN_NORMAL, power(-1252));
        assertBoth(NEGATIVE_ZERO, MIN_NORMAL.negate(), power(-1252));
        for (int bit : new int[] {SHORT_BOUNDARY, SHORT_BOUNDARY - 1, 0}) {
            BigInteger step = UNIT.shiftLeft(bit);
            assertBoth(MIN_VALUE, MIN_NORMAL, normal(false, -1252, step));
            assertBoth(MIN_VALUE.negate(), MIN_NORMAL, normal(true, -1252, step));
        }
    }

    @Test
    void compactLookingSubnormalInputsRetainTheirExactScale() {
        BigInteger subnormal = UNIT.shiftLeft(SHORT_BOUNDARY);
        Binary1280 factor = power(Long.SIZE - 1);
        assertBoth(MIN_NORMAL, fromRaw(subnormal), factor);
        assertBoth(fromRaw(HIDDEN.subtract(UNIT.shiftLeft(63))).negate(),
                fromRaw(subnormal.subtract(UNIT)).negate(), factor);
        assertBoth(normal(false, EMIN, UNIT.shiftLeft(63)), fromRaw(subnormal.add(UNIT)), factor);
        assertBoth(MIN_NORMAL.negate(), fromRaw(subnormal.shiftRight(1)), power(Long.SIZE).negate());
        assertBoth(NEGATIVE_ZERO, fromRaw(subnormal), fromRaw(subnormal).negate());
    }

    @Test
    void shortAndLongProductsNormalizeAtTheUpperExponentLimit() {
        Binary1280 a = normal(false, EMAX, HIDDEN.shiftRight(1));  // 1.5 × 2^EMAX
        for (int width : new int[] {63, 64, 65, 66}) {
            BigInteger unit = UNIT.shiftLeft(width - 1);
            BigInteger floor = unit.shiftLeft(2).divide(BigInteger.valueOf(3));
            // 取 4/3 两侧的奇数系数，让最低非零位恰好落在指定宽度。
            BigInteger below = floor.testBit(0) ? floor : floor.subtract(UNIT);
            BigInteger above = floor.add(UNIT).setBit(0);
            Binary1280 b = normal(false, 0, below.subtract(unit).shiftLeft(1252 - width));
            Binary1280 c = normal(true, 0, above.subtract(unit).shiftLeft(1252 - width));
            Binary1280 expected = multiply(a, b);
            assertFalse(expected.isInfinite());
            assertBoth(expected, a, b);
            assertBoth(NEGATIVE_INFINITY, a, c);
        }
    }

    @Test
    void fusedCancellationPreservesLowWordsAcrossDispatchShapes() {
        BigInteger allOnes = HIDDEN.subtract(UNIT);
        Binary1280 dense = normal(true, 0, allOnes);
        for (int bit : new int[] {SHORT_BOUNDARY, SHORT_BOUNDARY - 1, 640, 0}) {
            assertFusedResidual(normal(false, 0, UNIT.shiftLeft(bit)), dense);
        }
        assertFusedResidual(fromRaw(UNIT.shiftLeft(SHORT_BOUNDARY).setBit(0)),
                normal(false, 63, allOnes));
    }

    private static void assertFusedResidual(Binary1280 a, Binary1280 b) {
        Binary1280 opposite = multiply(a, b).negate();
        Dyadic x = decode(a), y = decode(b);
        Dyadic product = new Dyadic(x.negative() ^ y.negative(),
                x.magnitude().multiply(y.magnitude()), x.scale() + y.scale());
        Binary1280 expected = sum(product, decode(opposite));
        assertFalse(expected.isZero());
        assertArrayEquals(expected.toBytes(), fma(a, b, opposite).toBytes());
        assertArrayEquals(expected.toBytes(), fma(b, a, opposite).toBytes());
    }

    private static void assertBoth(Binary1280 expected, Binary1280 a, Binary1280 b) {
        assertArrayEquals(expected.toBytes(), a.multiply(b).toBytes());
        assertArrayEquals(expected.toBytes(), b.multiply(a).toBytes());
    }
}
