package com.inf.long2.ints;

import static com.inf.long2.ints.IntSupportTestSupport.unsigned;
import static com.inf.long2.ints.IntSupportTestSupport.words;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class IntWordsTest {
    private static final BigInteger ONE = BigInteger.ONE;

    @Test
    void negatesWithinEachFixedWidthIncludingZeroAndTheMinimumValue() {
        Random random = new Random(0x1A70E6A7L);
        for (int count : new int[] {1, 2, 4, 8, 16}) {
            BigInteger modulus = ONE.shiftLeft(count * 64);
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, modulus.subtract(ONE),
                    modulus.shiftRight(1), modulus.shiftRight(1).subtract(ONE))) {
                checkNegation(value, count, modulus);
            }
            for (int i = 0; i < 300; i++) {
                checkNegation(new BigInteger(count * 64, random), count, modulus);
            }
        }
    }

    @Test
    void readsBitsStickyAndShiftedWordsWithoutModuloOrInputMutation() {
        Random random = new Random(0x1A7B175L);
        for (int count : new int[] {1, 2, 4, 8, 16, 17}) {
            int width = count * 64;
            for (int i = 0; i < 100; i++) {
                BigInteger value = i == 0 ? BigInteger.ZERO : new BigInteger(width, random);
                long[] magnitude = words(value, count), before = magnitude.clone();
                assertEquals(value.bitLength(), IntWords.bitLength(magnitude));
                for (int bit = -1; bit <= width + 1; bit++) {
                    assertEquals(bit >= 0 && value.testBit(bit), IntWords.bit(magnitude, bit));
                    int lowest = value.getLowestSetBit();
                    assertEquals(lowest >= 0 && lowest < bit, IntWords.anyLow(magnitude, bit));
                }
                for (int distance : new int[] {0, 1, 63, 64, 65, width - 1, width, width + 1, Integer.MAX_VALUE}) {
                    assertEquals(value.shiftRight(distance).longValue(), IntWords.shiftedWord(magnitude, distance));
                }
                assertEquals(false, IntWords.bit(magnitude, Integer.MIN_VALUE));
                assertEquals(false, IntWords.bit(magnitude, Integer.MAX_VALUE));
                assertEquals(false, IntWords.anyLow(magnitude, Integer.MIN_VALUE));
                assertEquals(value.signum() != 0, IntWords.anyLow(magnitude, Integer.MAX_VALUE));
                assertArrayEquals(before, magnitude);
            }
            for (int bit = 0; bit < width; bit++) {
                long[] magnitude = words(ONE.shiftLeft(bit), count);
                assertEquals(bit + 1, IntWords.bitLength(magnitude));
                assertEquals(false, IntWords.anyLow(magnitude, bit));
                assertEquals(true, IntWords.anyLow(magnitude, bit + 1));
            }
        }
        assertEquals(0, IntWords.bitLength(new long[0]));
        assertEquals(0L, IntWords.shiftedWord(new long[0], 0));
    }

    @Test
    void roundsRandomMagnitudesAcrossWidthsWithoutChangingTheInput() {
        Random random = new Random(0x1A7F10A7L);
        for (int count : new int[] {1, 2, 4, 8, 16, 17}) {
            int width = count * 64;
            for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(width - 1), ONE.shiftLeft(width).subtract(ONE))) {
                checkPrimitive(value, count);
            }
            for (int i = 0; i < 500; i++) {
                checkPrimitive(new BigInteger(random.nextInt(width + 1), random), count);
            }
        }
        for (int precision : new int[] {24, 53}) {
            int bias = precision == 24 ? 127 : 1023, exponentBits = precision == 24 ? 8 : 11;
            assertEquals(0L, IntWords.primitiveBits(new long[0], true, precision, bias, exponentBits));
            assertEquals(0L, IntWords.primitiveBits(new long[16], true, precision, bias, exponentBits));
        }
    }

    @Test
    void roundsEvenAndOddMidpointsThroughBothPrimitiveOverflowBoundaries() {
        for (int precision : new int[] {24, 53}) {
            int maximumExponent = precision == 24 ? 127 : 1023;
            long leading = 1L << (precision - 1);
            for (int exponent = precision; exponent <= maximumExponent; exponent++) {
                int discarded = exponent - precision + 1;
                for (long kept : new long[] {leading, leading + 1, (1L << precision) - 1}) {
                    BigInteger midpoint = BigInteger.valueOf(kept).shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
                    for (int delta : new int[] {-1, 0, 1}) {
                        checkPrimitive(midpoint.add(BigInteger.valueOf(delta)), 16);
                    }
                }
            }
        }
    }

    private static void checkNegation(BigInteger value, int count, BigInteger modulus) {
        long[] encoded = words(value, count), before = encoded.clone();
        IntWords.negateInPlace(encoded);
        assertEquals(count, encoded.length);
        assertEquals(value.negate().mod(modulus), unsigned(encoded));
        IntWords.negateInPlace(encoded);
        assertArrayEquals(before, encoded);
    }

    private static void checkPrimitive(BigInteger value, int count) {
        long[] magnitude = words(value, count), before = magnitude.clone();
        for (boolean negative : new boolean[] {false, true}) {
            BigInteger signed = negative ? value.negate() : value;
            assertEquals(Float.floatToRawIntBits(signed.floatValue()),
                    (int) IntWords.primitiveBits(magnitude, negative, 24, 127, 8),
                    () -> "float source=" + signed);
            assertEquals(Double.doubleToRawLongBits(signed.doubleValue()),
                    IntWords.primitiveBits(magnitude, negative, 53, 1023, 11),
                    () -> "double source=" + signed);
        }
        assertArrayEquals(before, magnitude);
    }
}
