package com.inf.long2.binaries.binary1280;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


import static com.inf.long2.binaries.binary1280.Binary1280.*;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary1280DecimalTest {
    @Test
    void grammarAndSignedSpecialValues() {
        for (String bad : new String[] {"", " ", ".", "+", "1..0", "1e", "1e+", "1_0", "0x1p0",
                "１", "+NaN", "-Infinityx", "1e999999999999999999999999q"}) {
            assertFalse(isValid(bad), bad);
            assertThrows(NumberFormatException.class, () -> of(bad));
        }
        assertFalse(isValid(null));
        assertThrows(NullPointerException.class, () -> of((String) null));
        assertEquals(NEGATIVE_ZERO, of("-0e999999999999999999999"));
        assertEquals(NEGATIVE_ZERO, of("-1e-999999999999999999999"));
        assertEquals(POSITIVE_INFINITY, of("1e999999999999999999999"));
        assertEquals(NEGATIVE_INFINITY, of("-Infinity"));
        assertEquals(NaN, of("NaN"));
        assertEquals(ONE, of("+.01E2"));
        assertEquals(ONE, of("1" + "0".repeat(100_000) + "e-100000"));
        assertEquals("-0", NEGATIVE_ZERO.toString());
        assertEquals("-0.0e0", NEGATIVE_ZERO.toScientificString());
        assertEquals("-0.000", NEGATIVE_ZERO.toString(3));
        assertEquals("-0.00e0", NEGATIVE_ZERO.toScientificString(2));
        assertEquals("Infinity", POSITIVE_INFINITY.toString(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> NaN.toScientificString(-1));
        assertThrows(IllegalArgumentException.class, () -> ONE.toString(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ONE.toScientificString(Integer.MAX_VALUE));
    }

    @Test
    @Tag("numeric-text-extended")
    void exactMidpointsAndVeryLateStickyDigits() {
        BigDecimal midpoint = BigDecimal.ONE.add(
                new BigDecimal(BigInteger.valueOf(5).pow(1252), 1252));
        String text = midpoint.toPlainString();
        assertEquals(ONE, of(text));
        assertEquals(ONE.nextUp(), of(text + "0".repeat(10_000) + "1"));
        assertEquals(ONE.nextUp().negate(), of("-" + text + "01"));
        BigDecimal delta = BigDecimal.ONE.scaleByPowerOfTen(-2000);
        assertEquals(ONE, of(midpoint.subtract(delta).toPlainString()));
        BigDecimal oddMidpoint = midpoint.add(new BigDecimal(BigInteger.valueOf(5).pow(1251), 1251));
        assertEquals(ONE.nextUp().nextUp(), of(oddMidpoint.toPlainString()));
        assertEquals(ONE.nextUp(), of(oddMidpoint.subtract(delta).toPlainString()));
    }

    @Test
    @Tag("numeric-text-extended")
    void preciseMidpointComparisonHandlesSparseAndPaddedSignificands() {
        BigInteger numerator = BigInteger.ONE.shiftLeft(1253).subtract(BigInteger.ONE);
        BigDecimal midpoint = new BigDecimal(numerator.multiply(BigInteger.valueOf(5).pow(1253)), 1253);
        BigDecimal epsilon = BigDecimal.ONE.scaleByPowerOfTen(-1600);
        assertEquals(ONE.nextDown(), of("000" + midpoint.subtract(epsilon).toPlainString() + "000"));
        assertEquals(ONE, of("000" + midpoint.toPlainString() + "000"));
        assertEquals(ONE, of(midpoint.add(epsilon).toEngineeringString()));
        assertEquals(ONE.negate(), of("-000" + midpoint.toPlainString() + "000"));
    }

    @Test
    @Tag("numeric-text-extended")
    void randomDecimalInputMatchesExactRationalReference() {
        Random random = new Random(0x1280decaL);
        for (int i = 0; i < 150; i++) {
            BigInteger coefficient = new BigInteger(random.nextInt(1, 2100), random).setBit(0);
            int scale = random.nextInt(-600, 601);
            boolean negative = random.nextBoolean();
            String text = (negative ? "-" : "") + coefficient + "e" + scale;
            BigInteger power = BigInteger.valueOf(5).pow(Math.abs(scale));
            BigInteger numerator = scale >= 0 ? coefficient.multiply(power) : coefficient;
            BigInteger denominator = scale >= 0 ? BigInteger.ONE : power;
            assertArrayEquals(round(negative, numerator, denominator, scale).toBytes(), of(text).toBytes());
        }
    }

    @Test
    void fixedStringsRoundTheExactValue() {
        assertEquals("1.2", of(1.25).toString(1));
        assertEquals("1.8", of(1.75).toString(1));
        assertEquals("1.0e1", of(9.96875).toScientificString(1));
        assertEquals("-0.0", of(-0.03125).toString(1));
        assertEquals("-1.25" + "0".repeat(498), of(-1.25).toString(500));
        assertEquals("1.25" + "0".repeat(498) + "e0", of(1.25).toScientificString(500));

        Random random = new Random(1252);
        for (int i = 0; i < 35; i++) {
            Binary1280 value = normal(random.nextBoolean(), random.nextInt(-500, 501),
                    new BigInteger(1251, random));
            int places = i % 3 == 0 ? 450 : random.nextInt(100);
            BigDecimal exact = decimal(value);
            BigDecimal expected = exact.setScale(places, RoundingMode.HALF_EVEN);
            String plain = expected.signum() == 0 && value.signBit()
                    ? "-" + expected.toPlainString() : expected.toPlainString();
            assertEquals(plain, value.toString(places));
            int order = exact.precision() - exact.scale() - 1;
            BigDecimal tail = exact.scaleByPowerOfTen(-order).setScale(places, RoundingMode.HALF_EVEN);
            if (tail.abs().compareTo(BigDecimal.TEN) >= 0) {
                tail = tail.movePointLeft(1).setScale(places);
                order++;
            }
            assertEquals(tail.toPlainString() + "e" + order, value.toScientificString(places));
        }
    }

    @Test
    @Tag("numeric-text-extended")
    void shortestRepresentationMatchesIndependentDecimalSearch() {
        Random random = new Random(377);
        for (int i = 0; i < 24; i++) {
            Binary1280 value = normal(false, random.nextInt(-550, 551),
                    i % 4 == 0 ? BigInteger.ZERO : new BigInteger(1251, random));
            BigDecimal exact = decimal(value);
            BigDecimal lower = exact.add(decimal(value.nextDown())).divide(BigDecimal.valueOf(2));
            BigDecimal upper = exact.add(decimal(value.nextUp())).divide(BigDecimal.valueOf(2));
            boolean even = !raw(value).testBit(0);
            BigDecimal shortest = null;
            int exponent = exact.precision() - exact.scale() - 1;
            for (int digits = 1; digits <= 378 && shortest == null; digits++) {
                int scale = digits - 1 - exponent;
                BigDecimal down = exact.setScale(scale, RoundingMode.FLOOR);
                BigDecimal up = down.add(down.ulp());
                boolean useDown = down.compareTo(lower) > 0 || even && down.compareTo(lower) == 0;
                boolean useUp = up.compareTo(upper) < 0 || even && up.compareTo(upper) == 0;
                if (useDown && useUp) {
                    shortest = exact.setScale(scale, RoundingMode.HALF_EVEN);
                } else if (useDown || useUp) {
                    shortest = useDown ? down : up;
                }
            }
            assertNotNull(shortest);
            String scientific = value.toScientificString();
            assertEquals(0, shortest.compareTo(new BigDecimal(scientific)));
            assertEquals(shortest.stripTrailingZeros().toPlainString(), value.toString());
            assertEquals(value, of(scientific));
        }
    }

    private static BigDecimal decimal(Binary1280 value) {
        Dyadic number = decode(value);
        int scale = Math.toIntExact(number.scale());
        BigInteger signed = number.negative() ? number.magnitude().negate() : number.magnitude();
        return scale >= 0 ? new BigDecimal(signed.shiftLeft(scale))
                : new BigDecimal(signed.multiply(BigInteger.valueOf(5).pow(-scale)), -scale);
    }
}
