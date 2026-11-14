package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static com.inf.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Binary1280AddSubtractTest {
    private static final int FRACTION_BITS = 1251;
    private static final int EMIN = -134217726;
    private static final int EMAX = 134217727;
    private static final BigInteger HIDDEN = BigInteger.ONE.shiftLeft(FRACTION_BITS);
    private static final BigInteger MASK = HIDDEN.subtract(BigInteger.ONE);

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
