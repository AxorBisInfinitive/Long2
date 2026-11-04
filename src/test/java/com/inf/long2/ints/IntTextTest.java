package com.inf.long2.ints;

import static com.inf.long2.ints.IntSupportTestSupport.unsigned;
import static com.inf.long2.ints.IntSupportTestSupport.words;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class IntTextTest {
    private static final BigInteger ONE = BigInteger.ONE;

    @Test
    void parsesBothSignsModuloEachTargetWidthAndKeepsHighZeroWords() {
        Random random = new Random(0x1A7DEC1L);
        for (int count : new int[] {1, 2, 4, 8, 16}) {
            BigInteger modulus = ONE.shiftLeft(count * 64);
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, modulus.shiftRight(1).subtract(ONE),
                    modulus.shiftRight(1), modulus.subtract(ONE), modulus, modulus.add(ONE),
                    BigInteger.TEN.pow(1000).subtract(ONE))) {
                checkDecimal(value, count, modulus);
                checkDecimal(value.negate(), count, modulus);
            }
            for (int i = 0; i < 300; i++) {
                BigInteger value = new BigInteger(count * 128, random);
                checkDecimal(value, count, modulus);
                checkDecimal(value.negate(), count, modulus);
            }
            long[] first = IntText.parseDecimal("1", count), second = IntText.parseDecimal("1", count);
            assertNotSame(first, second);
            first[0] = 37;
            assertEquals(ONE, unsigned(second));
            assertEquals(ONE, unsigned(IntText.parseDecimal("+" + "0".repeat(1000) + "1", count)));
            assertEquals(BigInteger.ZERO, unsigned(IntText.parseDecimal("-0000", count)));
        }
    }

    @Test
    void formatsUnsignedMagnitudesAndSignsAcrossWidthsAndDecimalChunks() {
        Random random = new Random(0x1A7F02A7L);
        for (int count : new int[] {1, 2, 4, 8, 16, 17}) {
            int width = count * 64;
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(width - 1), ONE.shiftLeft(width).subtract(ONE))) {
                checkFormat(value, count);
            }
            for (int exponent = 1; ; exponent++) {
                BigInteger power = BigInteger.TEN.pow(exponent * 9);
                if (power.bitLength() > width) {
                    break;
                }
                for (int delta : new int[] {-1, 0, 1}) {
                    checkFormat(power.add(BigInteger.valueOf(delta)), count);
                }
            }
            for (int i = 0; i < 300; i++) {
                checkFormat(new BigInteger(random.nextInt(width + 1), random), count);
            }
        }
        assertEquals("0", IntText.toDecimal(new long[0], false));
        assertEquals("0", IntText.toDecimal(new long[16], true));
    }

    @Test
    void parsesHexAcrossWordBoundariesAndTruncatesOnlyAfterFullValidation() {
        Random random = new Random(0x1A7AE7L);
        for (int count : new int[] {1, 2, 4, 8, 16}) {
            BigInteger modulus = ONE.shiftLeft(count * 64);
            for (int digits = 1; digits <= count * 16 + 17; digits++) {
                String text = "f".repeat(digits);
                long[] parsed = IntText.parseHex("0x" + text, count);
                assertEquals(count, parsed.length);
                assertEquals(new BigInteger(text, 16).mod(modulus), unsigned(parsed));
            }
            for (int i = 0; i < 300; i++) {
                BigInteger value = new BigInteger(count * 128, random);
                assertEquals(value.mod(modulus), unsigned(IntText.parseHex("0x" + value.toString(16), count)));
            }
            assertEquals(ONE, unsigned(IntText.parseHex("0x" + "0".repeat(1000) + "1", count)));
            assertNotSame(IntText.parseHex("0x0", count), IntText.parseHex("0x0", count));
            String invalidHigh = "0xg" + "0".repeat(count * 16);
            assertFalse(IntText.isHexValid(invalidHigh));
            assertThrowsExactly(NumberFormatException.class, () -> IntText.parseHex(invalidHigh, count));
        }
    }

    @Test
    void preservesAsciiGrammarAndDistinctNullExceptions() {
        for (String invalid : new String[] {null, "", "+", "-", " 1", "1 ", "1\n", "1_0", "1.0",
                "1e2", "0x1", "++1", "--1", "１２", "١", "9".repeat(1000) + "x"}) {
            assertFalse(IntText.isValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> IntText.parseDecimal(invalid, 16));
        }
        for (String invalid : new String[] {"", "0x", "0X1", "0xA", "-0x1", "+0x1", "0x1 ", "0x1_0",
                "0xg" + "0".repeat(256), "0x" + "f".repeat(256) + "g"}) {
            assertFalse(IntText.isHexValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> IntText.parseHex(invalid, 16));
        }
        assertFalse(IntText.isHexValid(null));
        assertThrowsExactly(NullPointerException.class, () -> IntText.parseHex(null, 16));
        assertTrue(IntText.isValid("9".repeat(10000)));
        assertTrue(IntText.isHexValid("0x" + "f".repeat(10000)));
    }

    private static void checkDecimal(BigInteger value, int count, BigInteger modulus) {
        long[] parsed = IntText.parseDecimal(value.toString(), count);
        assertEquals(count, parsed.length);
        assertEquals(value.mod(modulus), unsigned(parsed));
    }

    private static void checkFormat(BigInteger value, int count) {
        for (boolean negative : new boolean[] {false, true}) {
            BigInteger signed = negative ? value.negate() : value;
            assertEquals(signed.toString(), IntText.toDecimal(words(value, count), negative));
        }
    }
}
