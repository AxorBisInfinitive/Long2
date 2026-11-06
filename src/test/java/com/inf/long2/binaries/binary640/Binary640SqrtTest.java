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
                    assertRaw(Binary640Reference.sqrt(value), value.sqrt());
                }
            }
        }
    }

    @Test
    void checksRandomRootsAndExactSquaresWithoutChangingInputs() {
        Random random = new Random(12345);
        for (int i = 0; i < 200; i++) {
            Binary640 value = random(random, true).abs();
            byte[] before = value.toBytes();
            assertRaw(Binary640Reference.sqrt(value), value.sqrt());
            assertArrayEquals(before, value.toBytes());
        }
        for (long root = 1; root <= 100; root++) {
            Binary640 square = Binary640.of(root * root);
            assertRaw(Binary640.of(root), square.sqrt());
            assertRaw(Binary640Reference.sqrt(square.nextUp()), square.nextUp().sqrt());
            assertRaw(Binary640Reference.sqrt(square.nextDown()), square.nextDown().sqrt());
        }
    }

    @Test
    void handlesSpecialValuesAndExtremeFiniteRoots() {
        assertRaw(Binary640.of(2L),Binary640.of(4L).sqrt());
        assertRaw(Binary640.NEGATIVE_ZERO,Binary640.NEGATIVE_ZERO.sqrt());
        assertRaw(Binary640.ZERO,Binary640.ZERO.sqrt());
        assertRaw(Binary640.POSITIVE_INFINITY,Binary640.POSITIVE_INFINITY.sqrt());
        assertRaw(Binary640.NaN,Binary640.NEGATIVE_INFINITY.sqrt());
        assertRaw(Binary640.NaN,Binary640.MIN_VALUE.negate().sqrt());
        assertRaw(Binary640.NaN,Binary640.NaN.sqrt());
        for (Binary640 value : new Binary640[] {Binary640.MIN_VALUE,Binary640.MIN_NORMAL,Binary640.MAX_VALUE}) {
            assertTrue(value.sqrt().isNormal());
            assertRaw(Binary640Reference.sqrt(value), value.sqrt());
        }
    }
}
