package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640SqrtTest {
    @Test
    void coversEverySubnormalMagnitudeWidthAndExponentParity() {
        for (int length = 1; length <= 616; length++) {
            BigInteger magnitude = ONE.shiftLeft(length-1).or(ONE);
            for (int scale : new int[] {-8389221, -617, -616, 0, 1, 8387376}) {
                Binary640 value = dyadic(false, magnitude, scale);
                if (value.isFinite()) {
                    assertRaw(Binary640Reference.sqrt(value), Binary640.sqrt(value));
                }
            }
        }
    }

    @Test
    void checksRandomRootsAndExactSquaresWithoutChangingInputs() {
        Random random = new Random(12345);
        for (int i = 0; i < 200; i++) {
            Binary640 value = Binary640.abs(random(random, true));
            byte[] before = value.toBytes();
            assertRaw(Binary640Reference.sqrt(value), Binary640.sqrt(value));
            assertArrayEquals(before, value.toBytes());
        }
        for (long root = 1; root <= 100; root++) {
            Binary640 square = Binary640.of(root * root);
            assertRaw(Binary640.of(root), Binary640.sqrt(square));
            assertRaw(Binary640Reference.sqrt(square.nextUp()), Binary640.sqrt(square.nextUp()));
            assertRaw(Binary640Reference.sqrt(square.nextDown()), Binary640.sqrt(square.nextDown()));
        }
    }

    @Test
    void roundsBothSidesOfRootMidpointsAndHandlesAnAllOnesPrefix() {
        BigInteger hidden = ONE.shiftLeft(615);
        BigInteger last = hidden.shiftLeft(1).subtract(ONE);
        for (BigInteger root : new BigInteger[] {hidden, hidden.add(ONE), hidden.add(ONE.shiftLeft(308)),
                last.subtract(ONE), last}) {
            BigInteger twiceMidpoint = root.shiftLeft(1).add(ONE);
            for (int quantum : new int[] {-616, -615, 0}) {
                Binary640 atMidpoint = dyadic(false, twiceMidpoint.multiply(twiceMidpoint), 2L * quantum - 2);
                for (Binary640 input : new Binary640[] {atMidpoint.nextDown(), atMidpoint, atMidpoint.nextUp()}) {
                    assertRaw(Binary640Reference.sqrt(input), Binary640.sqrt(input));
                }
            }
        }
        Binary640 allOnes = dyadic(false, last, -614);
        assertRaw(Binary640Reference.sqrt(allOnes), Binary640.sqrt(allOnes));
    }

    @Test
    void checksNewtonCorrectionAndRoundingNearPowersOfTwo() {
        BigInteger hidden = ONE.shiftLeft(615);
        BigInteger mask = hidden.shiftLeft(1).subtract(ONE);
        for (BigInteger magnitude : new BigInteger[] {hidden, hidden.add(ONE), hidden.add(ONE.shiftLeft(306)),
                mask.subtract(ONE), mask}) {
            for (int scale : new int[] {-8389221, -616, -615, -614, 8387992}) {
                Binary640 input = dyadic(false, magnitude, scale);
                assertRaw(Binary640Reference.sqrt(input), Binary640.sqrt(input));
            }
        }
        assertRaw(Binary640.ONE, Binary640.sqrt(Binary640.ONE.nextDown()).nextUp());
        assertRaw(Binary640.ONE, Binary640.sqrt(Binary640.ONE.nextUp()));
    }

    @Test
    void handlesSpecialValuesAndExtremeFiniteRoots() {
        assertRaw(Binary640.of(2L),Binary640.sqrt(Binary640.of(4L)));
        assertRaw(Binary640.NEGATIVE_ZERO,Binary640.sqrt(Binary640.NEGATIVE_ZERO));
        assertRaw(Binary640.ZERO,Binary640.sqrt(Binary640.ZERO));
        assertRaw(Binary640.POSITIVE_INFINITY,Binary640.sqrt(Binary640.POSITIVE_INFINITY));
        assertRaw(Binary640.NaN,Binary640.sqrt(Binary640.NEGATIVE_INFINITY));
        assertRaw(Binary640.NaN,Binary640.sqrt(Binary640.MIN_VALUE.negate()));
        assertRaw(Binary640.NaN,Binary640.sqrt(Binary640.NaN));
        for (Binary640 value : new Binary640[] {Binary640.MIN_VALUE,Binary640.MIN_NORMAL,Binary640.MAX_VALUE}) {
            assertTrue(Binary640.sqrt(value).isNormal());
            assertRaw(Binary640Reference.sqrt(value), Binary640.sqrt(value));
        }
    }
}
