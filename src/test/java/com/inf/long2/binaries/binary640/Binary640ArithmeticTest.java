package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640ArithmeticTest {
    @Test
    void comparesArithmeticAgainstIndependentExactRationals() {
        Random random = new Random(12345);
        for (int i = 0; i < 220; i++) {
            Binary640 a = random(random, i < 24), b = random(random, i < 24);
            byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
            assertRaw(Binary640Reference.add(a, b), a.add(b));
            assertRaw(Binary640Reference.add(a, b.negate()), a.subtract(b));
            assertRaw(Binary640Reference.multiply(a, b), a.multiply(b));
            if (!b.isZero()) {
                assertRaw(Binary640Reference.divide(a, b), a.divide(b));
            }
            assertArrayEquals(beforeA, a.toBytes());
            assertArrayEquals(beforeB, b.toBytes());
        }
    }

    @Test
    void coversAlignmentThresholdsAndCancellation() {
        for (Binary640 a : new Binary640[] {Binary640.ONE, Binary640.ONE.nextUp(), Binary640.ONE.nextDown(), Binary640.MIN_NORMAL}) {
            for (int difference : new int[] {0, 1, 39, 40, 63, 64, 65, 575, 576, 615, 616, 617, 618, 619}) {
                Binary640 b = a.scalb(-difference);
                assertRaw(Binary640Reference.add(a, b), a.add(b));
                assertRaw(Binary640Reference.add(a, b.negate()), a.subtract(b));
                assertRaw(Binary640Reference.add(b, a.negate()), b.subtract(a));
                assertRaw(Binary640.ZERO, a.subtract(a));
                assertRaw(Binary640.ZERO, a.negate().add(a));
            }
        }
    }

    @Test
    void checksSpecialValueCombinationsAndSignedZeros() {
        double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -2, -0.0, 0.0, 2, Double.POSITIVE_INFINITY};
        for (double x : values) {
            for (double y : values) {
                Binary640 a = Binary640.of(x), b = Binary640.of(y);
                assertRaw(Binary640.of(x+y), a.add(b));
                assertRaw(Binary640.of(x-y), a.subtract(b));
                assertRaw(Binary640.of(x*y), a.multiply(b));
                assertRaw(Binary640.of(x/y), a.divide(b));
            }
        }
    }

    @Test
    void roundsProductsAndQuotientsAtSubnormalAndOverflowEdges() {
        Binary640[] a = {Binary640.MIN_VALUE, Binary640.MIN_VALUE.nextUp(), Binary640.MIN_NORMAL.nextDown(),
                Binary640.MIN_NORMAL, Binary640.ONE.nextDown(), Binary640.ONE.nextUp(), Binary640.MAX_VALUE};
        Binary640[] b = {Binary640.of(0.5), Binary640.of(1.5), Binary640.of(2L), Binary640.of(3L),
                Binary640.ONE.nextDown(), Binary640.ONE.nextUp(), Binary640.MIN_VALUE, Binary640.MAX_VALUE};
        for (Binary640 x : a) {
            for (Binary640 y : b) {
                assertRaw(Binary640Reference.multiply(x, y), x.multiply(y));
                assertRaw(Binary640Reference.divide(x, y), x.divide(y));
                assertRaw(Binary640Reference.multiply(x.negate(), y), x.negate().multiply(y));
            }
        }
    }
}
