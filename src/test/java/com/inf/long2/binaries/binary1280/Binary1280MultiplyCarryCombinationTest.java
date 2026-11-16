package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static com.inf.long2.binaries.binary1280.Binary1280.FORMAT;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.multiply;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.normal;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Binary1280MultiplyCarryCombinationTest {
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger HIDDEN = ONE.shiftLeft(1251);
    private static final BigInteger THREE_HALVES = HIDDEN.add(HIDDEN.shiftRight(1));
    private static final BigInteger MAX_SIGNIFICAND = HIDDEN.shiftLeft(1).subtract(ONE);

    @Test
    void alternatesDenseWindowFallbackAndWord65MidpointAtOneCallSite() {
        BigInteger dense = HIDDEN.add(ONE);
        BigInteger coefficient = ONE.setBit(64);
        BigInteger compact = coefficient.shiftLeft(1187);
        for (int exponent : new int[] {FORMAT.minExponent(), 0, FORMAT.maxExponent() - 1}) {
            for (int parity = 0; parity < 2; parity++) {
                BigInteger midpoint = HIDDEN.add(BigInteger.valueOf(2L * parity + 1).shiftLeft(63));
                BigInteger[] qr = coefficient.multiply(midpoint).divideAndRemainder(ONE.shiftLeft(64));
                assertEquals(ONE.shiftLeft(63), qr[1]);
                assertEquals(parity != 0, qr[0].testBit(0));
                for (int offset : new int[] {-1, 0, 1}) {
                    check(value(true, exponent, compact), value(false, 0, midpoint.add(BigInteger.valueOf(offset))));
                    // 稠密高位窗口必须回退才能区分中点两侧的低位。
                    int side = offset < 0 ? -1 : 1;
                    BigInteger near = THREE_HALVES.add(BigInteger.valueOf(side));
                    assertEquals(BigInteger.valueOf(side), dense.multiply(near).mod(HIDDEN).subtract(HIDDEN.shiftRight(1)));
                    check(value(true, exponent, dense), value(false, 0, near));
                }
            }
        }
    }

    @Test
    void alternatesMaximalCarryAndDispatchOnBothSidesOfWord65() {
        BigInteger mask = ONE.shiftLeft(64).subtract(ONE);
        for (int group = 0; group < 19; group++) {
            BigInteger almostFull = MAX_SIGNIFICAND.xor(mask.shiftLeft(64 * group)).setBit(0);
            for (int width : new int[] {64, 65, 66}) {
                BigInteger shortSignificand = ONE.shiftLeft(width).subtract(ONE).shiftLeft(1252 - width);
                check(value(false, -1, shortSignificand), value(true, 0, almostFull));
                check(value(false, -1, MAX_SIGNIFICAND), value(true, 0, almostFull));
                check(value(false, -1, shortSignificand), value(true, 0, almostFull.andNot(mask)));
            }
        }
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
