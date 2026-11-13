package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640FmaTest {
    @Test
    void preservesDeepCancellationAndOverflowRecovery() {
        Binary640 delta = Binary640.ONE.scalb(-309);
        Binary640 a = Binary640.ONE.add(delta), b = Binary640.ONE.subtract(delta);
        assertRaw(Binary640.ONE.scalb(-618).negate(), a.fma(b, Binary640.ONE.negate()));
        assertRaw(Binary640.ZERO, a.multiply(b).subtract(Binary640.ONE));
        assertRaw(Binary640.MAX_VALUE, Binary640.MAX_VALUE.fma(Binary640.of(2L), Binary640.MAX_VALUE.negate()));
        assertRaw(Binary640.MIN_VALUE.nextUp(), Binary640.MIN_VALUE.fma(Binary640.of(0.5), Binary640.MIN_VALUE));
    }

    @Test
    void comparesExactProductsAndSumsAcrossAllAlignmentThresholds() {
        Random random = new Random(12345);
        int[] differences = {-619, -618, -617, -616, -1, 0, 1, 615, 616, 1231, 1232, 1233, 1234};
        for (int difference : differences) {
            for (int i = 0; i < 6; i++) {
                Binary640 a = random(random, false), b = random(random, false);
                var left = Binary640Reference.decode(a);
                var right = Binary640Reference.decode(b);
                long pe = left.scale() + right.scale() + left.magnitude().multiply(right.magnitude()).bitLength() - 1;
                Binary640 c = Binary640.ONE.nextUp().scalb(pe - difference);
                for (Binary640 addend : new Binary640[] {c, c.negate()}) {
                    byte[] before = a.toBytes();
                    assertRaw(Binary640Reference.fma(a, b, addend), a.fma(b, addend));
                    assertArrayEquals(before, a.toBytes());
                }
            }
        }
    }

    @Test
    void preservesRoundingAtTheAddendDominanceBoundary() {
        Binary640 a = Binary640.of(-1.5);
        Binary640 b = Binary640.of(1.5);
        Binary640 c = Binary640.ONE;
        // The product exponent is one above the sum of operand exponents.
        assertRaw(c.nextDown(), a.fma(b.scalb(-618), c));
        assertRaw(c, a.fma(b.scalb(-619), c));
        assertRaw(c, a.fma(b.scalb(-620), c));
    }

    @Test
    void alignsLowZeroGroupProductsAtTheirAdjustedScale() {
        // Mixed and dense products have the same leading exponent but different low word positions.
        Binary640 shortValue = Binary640.of(1.5);
        Binary640 denseValue = Binary640.ONE.nextUp();
        for (Binary640 a : new Binary640[] {shortValue, denseValue}) {
            for (Binary640 b : new Binary640[] {shortValue, denseValue}) {
                Binary640 cancellation = Binary640Reference.multiply(a, b).negate();
                assertRaw(Binary640Reference.fma(a, b, cancellation), a.fma(b, cancellation));
                for (int exponent : new int[] {-65, -64, -63, -1, 0, 1, 63, 64, 65}) {
                    Binary640 addend = denseValue.scalb(exponent);
                    assertRaw(Binary640Reference.fma(a, b, addend), a.fma(b, addend));
                    assertRaw(Binary640Reference.fma(a, b, addend.negate()), a.fma(b, addend.negate()));
                }
            }
        }
    }

    @Test
    void resolvesBothDirectionsOfTinyMidpointTails() {
        Binary640 a = Binary640.ONE.nextUp();
        Binary640 b = Binary640.of(1.5);
        Binary640 tiny = Binary640.ONE.scalb(-2000);
        for (Binary640 x : new Binary640[] {a, a.negate()}) {
            for (Binary640 tail : new Binary640[] {tiny,tiny.negate()}) {
                assertRaw(Binary640Reference.fma(x, b, tail), x.fma(b, tail));
            }
        }
    }

    @Test
    void followsSpecialValueAndZeroSignRules() {
        double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -1, -0.0, 0.0, 1, Double.POSITIVE_INFINITY};
        for (double a : values) {
            for (double b : values) {
                for (double c : values) {
                    assertRaw(Binary640.of(Math.fma(a, b, c)), Binary640.of(a).fma(Binary640.of(b),Binary640.of(c)));
                }
            }
        }
    }

    @Test
    void preservesMidpointNeighborsAtFullProductPrecision() {
        BigInteger modulus = ONE.shiftLeft(616), half = ONE.shiftLeft(615);
        for (int delta : new int[] {-1, 1}) {
            BigInteger a = null, b = null;
            for (int odd = 1; odd <= 63; odd += 2) {
                BigInteger candidate = modulus.subtract(BigInteger.valueOf(odd));
                BigInteger partner = half.add(BigInteger.valueOf(delta)).multiply(candidate.modInverse(modulus)).mod(modulus);
                if (partner.bitLength() == 616 && candidate.multiply(partner).bitLength() == 1232) {
                    a = candidate;
                    b = partner;
                    break;
                }
            }
            assertNotNull(a);
            assertNotNull(b);
            Binary640 left = dyadic(false, a, -615), right = dyadic(false, b, -615);
            for (int gap : new int[] {1230, 1231, 1232, 1233, 1234}) {
                for (Binary640 x : new Binary640[] {left, left.negate()}) {
                    for (Binary640 tail : new Binary640[] {Binary640.ONE.scalb(1L - gap), Binary640.ONE.scalb(1L - gap).negate()}) {
                        assertRaw(Binary640Reference.fma(x, right, tail), x.fma(right, tail));
                    }
                }
            }
        }
    }
}
