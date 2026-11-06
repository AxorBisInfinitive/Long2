package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640StringsTest {
    static BigDecimal exact(Binary640 value) {
        var x = Binary640Reference.decode(value);
        int scale = Math.toIntExact(x.scale());
        BigDecimal magnitude = scale >= 0 ? new BigDecimal(x.magnitude().shiftLeft(scale))
                : new BigDecimal(x.magnitude().multiply(BigInteger.valueOf(5).pow(-scale)), -scale);
        return x.negative() ? magnitude.negate() : magnitude;
    }

    static Binary640 reference(BigDecimal value, boolean negativeZero) {
        BigInteger numerator = value.unscaledValue().abs();
        BigInteger denominator = ONE;
        if (value.scale() >= 0) {
            denominator = BigInteger.TEN.pow(value.scale());
        } else {
            numerator = numerator.multiply(BigInteger.TEN.pow(-value.scale()));
        }
        return Binary640Reference.round(value.signum() < 0 || value.signum() == 0 && negativeZero, numerator, denominator, 0);
    }

    @Test
    void acceptsOnlyTheSpecifiedGrammarWithoutNumericConversion() {
        for (String text : List.of("0", "-0", "+0", ".0", "1.", "+.25", "0001.20e-03", "NaN", "Infinity", "+Infinity", "-Infinity",
                "1e" + "9".repeat(10000), "1e-" + "9".repeat(10000))) {
            assertTrue(Binary640.isValid(text), text);
        }
        for (String text : List.of("", ".", "+", "-", "1e", "1e+", "1e-", "1..2", "1.2.3", " 1", "1 ", "1\n", "1_000", "1,000",
                "0x1p0", "1f", "1d", "nan", "infinity", "+NaN", "-NaN", "１２", "١", "1e99999999999999999x", "0e-999999999999x")) {
            assertFalse(Binary640.isValid(text), text);
            assertThrows(NumberFormatException.class, () -> Binary640.of(text), text);
        }
        assertFalse(Binary640.isValid(null));
        assertThrows(NullPointerException.class, () -> Binary640.of((String) null));
    }

    @Test
    void handlesUnboundedExponentsAndCompensatingCoefficientZeros() {
        String exponent = "9".repeat(400);
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.of("1e" + exponent));
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.of("-1e" + exponent));
        assertRaw(Binary640.ZERO, Binary640.of("1e-" + exponent));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.of("-1e-" + exponent));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.of("-0e" + exponent));
        assertRaw(Binary640.ONE, Binary640.of("0." + "0".repeat(10000) + "1e10001"));
        assertRaw(Binary640.ONE, Binary640.of("1" + "0".repeat(10000) + "e-10000"));
        assertRaw(Binary640.ONE, Binary640.of("1e+" + "0".repeat(10000)));
        for (String text : List.of("NaN", "Infinity", "-Infinity", "+Infinity")) {
            assertEquals(text.equals("+Infinity") ? "Infinity" : text, Binary640.of(text).toString());
        }
    }

    @Test
    void parsesExactDecimalsAgainstIndependentRationalRounding() {
        Random random = new Random(12345);
        for (int i = 0; i < 250; i++) {
            BigInteger coefficient = new BigInteger(1 + random.nextInt(2000), random);
            if (random.nextBoolean()) {
                coefficient = coefficient.negate();
            }
            BigDecimal decimal = new BigDecimal(coefficient, random.nextInt(2001) - 1000);
            assertRaw(reference(decimal, false), Binary640.of(decimal.toString()));
        }
        for (Binary640 lower : new Binary640[] {Binary640.ONE.nextDown(), Binary640.ONE, Binary640.ONE.nextUp(), Binary640.of(2L).nextDown()}) {
            BigDecimal midpoint = exact(lower).add(exact(lower.nextUp())).divide(BigDecimal.TWO);
            BigDecimal epsilon = BigDecimal.ONE.scaleByPowerOfTen(-midpoint.scale() - 1);
            for (BigDecimal decimal : new BigDecimal[] {midpoint.subtract(epsilon), midpoint, midpoint.add(epsilon)}) {
                assertRaw(reference(decimal, false), Binary640.of(decimal.toString()));
                assertRaw(reference(decimal.negate(), false), Binary640.of(decimal.negate().toString()));
            }
        }
    }

    private static void shortest(Binary640 value) {
        String scientific = value.toScientificString();
        String plain = value.toString();
        assertRaw(value, Binary640.of(scientific));
        assertRaw(value, Binary640.of(plain));
        BigDecimal decimal = new BigDecimal(scientific).stripTrailingZeros();
        int digits = decimal.precision();
        assertTrue(digits <= 187);
        assertEquals(0, exact(value).round(new MathContext(digits, RoundingMode.HALF_EVEN)).compareTo(decimal));
        if (digits > 1) {
            BigInteger shortCoefficient = decimal.unscaledValue().abs().divide(BigInteger.TEN);
            for (BigInteger coefficient : new BigInteger[] {shortCoefficient, shortCoefficient.add(ONE)}) {
                BigDecimal shorter = new BigDecimal(value.signBit() ? coefficient.negate() : coefficient, decimal.scale() - 1);
                assertNotEquals(Binary640Reference.raw(value), Binary640Reference.raw(reference(shorter, false)));
            }
        }
    }

    @Test
    void choosesShortestClosestDecimalAndChecksMinimalityIndependently() {
        Random random = new Random(12345);
        for (int i = 0; i < 150; i++) {
            shortest(random(random, false));
        }
        for (String text : List.of("0.1", "1", "1.25", "10", "1000", "1e-1000", "1e1000")) {
            shortest(Binary640.of(text));
        }
        assertEquals("0", Binary640.ZERO.toString());
        assertEquals("-0", Binary640.NEGATIVE_ZERO.toString());
        assertEquals("0.0e0", Binary640.ZERO.toScientificString());
        assertEquals("-0.0e0", Binary640.NEGATIVE_ZERO.toScientificString());
        assertEquals("1.0e0", Binary640.ONE.toScientificString());
        assertEquals("NaN", Binary640.NaN.toScientificString());
    }
}
