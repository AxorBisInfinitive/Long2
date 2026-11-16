package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static com.inf.long2.binaries.binary1280.Binary1280.*;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.multiply;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.normal;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Binary1280Word65RoundingBoundaryTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final BigInteger B = UNIT.shiftLeft(64);
    private static final BigInteger HIDDEN = UNIT.shiftLeft(1251);
    private static final BigInteger TWO_THRESHOLD = HIDDEN.multiply(B).shiftLeft(1);

    @Test
    void roundsBothShiftsAtTheNearestReachableGuardBoundaries() {
        for (int shift : new int[] {64, 65}) {
            BigInteger step = UNIT.shiftLeft(shift), half = step.shiftRight(1), modulus = step.shiftLeft(1);
            BigInteger coefficient = shift == 64 ? B.add(UNIT) : B.shiftLeft(1).subtract(UNIT);
            BigInteger prefix = shift == 64 ? HIDDEN : HIDDEN.add(HIDDEN.shiftRight(1));
            for (int parity = 0; parity < 2; parity++) {
                for (int delta : shift == 64 ? new int[] {-1, 0, 1} : new int[] {-1, 1}) {
                    BigInteger residue = step.multiply(BigInteger.valueOf(parity)).add(half).add(BigInteger.valueOf(delta));
                    BigInteger significand = prefix.add(residue.multiply(coefficient.modInverse(modulus)).mod(modulus));
                    assertTrue(significand.mod(B).signum() != 0);
                    BigInteger exact = coefficient.multiply(significand);
                    assertEquals(1252 + shift, exact.bitLength());
                    assertEquals(residue, exact.mod(modulus));
                    for (int exponent : new int[] {FORMAT.minExponent(), 0, FORMAT.maxExponent() - 1}) {
                        check(value(false, exponent, coefficient.shiftLeft(1187)), value(true, 0, significand));
                    }
                    check(value(false, FORMAT.minExponent(), coefficient.shiftLeft(1187)), value(true, -1, significand));
                }
            }
        }
    }

    @Test
    void roundsAcrossANewExponentAndAcrossOverflow() {
        BigInteger coefficient = B.add(B.shiftRight(1)).add(UNIT);
        BigInteger dense = TWO_THRESHOLD.subtract(UNIT).divide(coefficient);
        assertTrue(TWO_THRESHOLD.subtract(dense.multiply(coefficient)).compareTo(B.shiftRight(1)) <= 0);
        assertTrue(dense.mod(B).signum() != 0);
        BigInteger shorter = coefficient.shiftLeft(1187);
        Binary1280 b = value(false, 0, dense);
        assertEquals(value(false, 1, HIDDEN), multiply(value(false, 0, shorter), b));
        check(value(false, 0, shorter), b);
        assertEquals(NEGATIVE_INFINITY, multiply(value(true, FORMAT.maxExponent(), shorter), b));
        check(value(true, FORMAT.maxExponent(), shorter), b);
        check(value(true, FORMAT.maxExponent(), shorter), value(false, 0, dense.subtract(UNIT)));
    }

    private static Binary1280 value(boolean negative, int exponent, BigInteger significand) {
        return normal(negative, exponent, significand.subtract(HIDDEN));
    }

    private static void check(Binary1280 a, Binary1280 b) {
        byte[] expected = multiply(a, b).toBytes();
        assertArrayEquals(expected, a.multiply(b).toBytes());
        assertArrayEquals(expected, b.multiply(a).toBytes());
    }
}
