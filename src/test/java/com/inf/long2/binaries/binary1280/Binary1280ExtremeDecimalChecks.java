package com.inf.long2.binaries.binary1280;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


import static com.inf.long2.binaries.binary1280.Binary1280.*;
import static org.junit.jupiter.api.Assertions.*;

/** 极端指数和长输出单独运行。 */
@Tag("numeric-text-extreme")
class Binary1280ExtremeDecimalChecks {
    @Test
    void endpointScientificStringsAndExtremeFixedPrecision() {
        assertEquals("9.0e-40403939", MIN_VALUE.toScientificString());
        assertEquals("8.6e-40403939", MIN_VALUE.toScientificString(1));
        assertEquals("0.0000", MIN_VALUE.toString(4));
        assertEquals("-0.0000", MIN_VALUE.negate().toString(4));
        assertEquals("1.1964e40403562", MAX_VALUE.toScientificString(4));
        assertEquals(ZERO, of("4.3e-40403939"));
        assertEquals(MIN_VALUE, of("4.4e-40403939"));
        assertEquals(NEGATIVE_ZERO, of("-4.3e-40403939"));
        assertEquals(MIN_VALUE.negate(), of("-4.4e-40403939"));

        for (Binary1280 value : new Binary1280[] {MIN_VALUE, MIN_NORMAL.negate(), MAX_VALUE}) {
            String text = value.toScientificString();
            assertTrue(text.length() <= 391);
            assertEquals(value, of(text));
            assertEquals(value.signBit(), text.startsWith("-"));
        }
        assertEquals(NEGATIVE_ZERO, of("-1e-41000001"));
        assertEquals(POSITIVE_INFINITY, of("1e41000001"));
    }

    @Test
    void fullLengthPlainOutputUsesTheSameShortestCoefficient() {
        String text = MAX_VALUE.toString();
        assertEquals(40_403_563, text.length());
        BigDecimal scientific = new BigDecimal(MAX_VALUE.toScientificString()).stripTrailingZeros();
        String coefficient = scientific.unscaledValue().toString();
        assertTrue(text.startsWith(coefficient));
        assertTrue(text.chars().skip(coefficient.length()).allMatch(c -> c == '0'));
        assertEquals(MAX_VALUE, of(text));
    }

    @Test
    void exactOverflowMidpointUsesTheEvenUpperCandidate() {
        int scale = 134216475;
        BigInteger midpoint = BigInteger.ONE.shiftLeft(1253).subtract(BigInteger.ONE).shiftLeft(scale);
        String exact = midpoint.toString();
        assertEquals(POSITIVE_INFINITY, of(exact));
        int prefixLength = 420;
        BigInteger prefix = new BigInteger(exact.substring(0, prefixLength));
        String exponent = "e" + (exact.length() - prefixLength);
        assertEquals(MAX_VALUE, of(prefix + exponent));
        assertEquals(POSITIVE_INFINITY, of(prefix.add(BigInteger.ONE) + exponent));
        assertEquals(NEGATIVE_INFINITY, of("-" + prefix.add(BigInteger.ONE) + exponent));
    }

    @Test
    void maximumIntegerFormattingMatchesTheExactCoefficient() {
        BigInteger exact = BigInteger.ONE.shiftLeft(1252).subtract(BigInteger.ONE).shiftLeft(134_216_476);
        String expected = exact.toString();
        String actual = MAX_VALUE.toString(0);
        assertEquals(40_403_563, actual.length());
        assertTrue(expected.equals(actual), "Maximum finite integer differs from the exact reference");
    }

    @Test
    void exactZeroMidpointAndItsDecimalNeighborsPreserveSigns() throws NoSuchAlgorithmException {
        int places = 134_218_978;
        // 独立参考用 GMP 生成：SHA-256(str(mpz(5)**134218978).encode("ascii"))。
        String coefficient = Binary1280DecimalIntegers.decimalDigits(Binary1280DecimalIntegers.powerOfFive(places));
        assertEquals(93_815_040, coefficient.length());
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(coefficient.getBytes(StandardCharsets.US_ASCII));
        assertEquals("ecde1935707a70bf848b3ac4aa5b1239b5a0570952ca21904fc4d187fa9a17e2",
                HexFormat.of().formatHex(digest));
        String suffix = "e-" + places;
        assertEquals(NEGATIVE_ZERO, of("-" + coefficient + suffix));
        // 5^places ends in 125 or 625 for places >= 3, so ±1 changes only the last digit.
        String leading = coefficient.substring(0, coefficient.length() - 1);
        assertEquals(ZERO, of(leading + "4" + suffix));
        assertEquals(MIN_VALUE, of(leading + "6" + suffix));
    }
}
