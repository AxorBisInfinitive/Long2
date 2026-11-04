package com.inf.long2.ints.int512;

import static com.inf.long2.ints.int512.Int512TestSupport.MAXIMUM;
import static com.inf.long2.ints.int512.Int512TestSupport.MINIMUM;
import static com.inf.long2.ints.int512.Int512TestSupport.MODULUS;
import static com.inf.long2.ints.int512.Int512TestSupport.ONE;
import static com.inf.long2.ints.int512.Int512TestSupport.assertNumber;
import static com.inf.long2.ints.int512.Int512TestSupport.from;
import static com.inf.long2.ints.int512.Int512TestSupport.number;
import static com.inf.long2.ints.int512.Int512TestSupport.random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int512;

class Int512EncodingTest {
    @Test
    void preservesBigEndianGroupsAndIsolatesInputAndOutputArrays() {
        long[] words = {Long.MIN_VALUE, 0x0123_4567_89ab_cdefL, -1, 1, 0, Long.MAX_VALUE, -2, 37};
        Int512 value = Int512.of(words);
        assertEquals(value, Int512.of(words[0], words[1], words[2], words[3], words[4], words[5], words[6], words[7]));
        byte[] encoded = value.toBytes();
        for (int group = 0; group < words.length; group++) {
            assertEquals(words[group], value.getLong(group));
            for (int index = 0; index < 8; index++) {
                assertEquals((byte) (words[group] >>> (56 - 8 * index)), encoded[group * 8 + index]);
            }
        }
        Int512 restored = Int512.of(encoded);
        words[0] = 0;
        encoded[0] = 0;
        byte[] output = value.toBytes();
        output[63] = 0;
        assertEquals(Long.MIN_VALUE, value.getLong(0));
        assertEquals(value, restored);
        assertNotSame(value.toBytes(), value.toBytes());
        assertEquals(37, value.toBytes()[63]);
        assertThrowsExactly(NullPointerException.class, () -> Int512.of((long[]) null));
        assertThrowsExactly(NullPointerException.class, () -> Int512.of((byte[]) null));
        for (int length : new int[] {0, 1, 7, 9, 16}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> Int512.of(new long[length]));
        }
        for (int length : new int[] {0, 1, 32, 63, 65, 128}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> Int512.of(new byte[length]));
        }
    }

    @Test
    void roundTripsRandomEncodingsAndFormatsAgainstBigInteger() {
        Random random = new Random(0x512DEC1L);
        for (int i = 0; i < 1500; i++) {
            Int512 value = random(random);
            BigInteger expected = number(value);
            assertEquals(expected.toString(), value.toString());
            String hex = expected.mod(MODULUS).toString(16);
            assertEquals("0x" + "0".repeat(128 - hex.length()) + hex, value.toHexString());
            assertEquals(value, Int512.parseInt(value.toString()));
            assertEquals(value, Int512.parseHexInt(value.toHexString()));
            assertEquals(value, Int512.of(value.toBytes()));
            assertTrue(Int512.isValid(value.toString()));
            assertTrue(Int512.isHexValid(value.toHexString()));
        }
    }

    @Test
    void parsesExtremeAndLongDecimalValuesModulo512Bits() {
        for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.negate(), MINIMUM, MAXIMUM,
                MINIMUM.subtract(ONE), MAXIMUM.add(ONE), MODULUS, MODULUS.negate(),
                BigInteger.TEN.pow(1000).subtract(ONE))) {
            assertNumber(value, Int512.parseInt(value.toString()));
            Int512 wrapped = from(value);
            assertEquals(number(wrapped).toString(), wrapped.toString());
        }
        for (int length = 1; length <= 30; length++) {
            String text = "9".repeat(length);
            assertNumber(new BigInteger(text), Int512.parseInt(text));
            assertNumber(new BigInteger(text).negate(), Int512.parseInt("-" + text));
        }
        assertEquals(Int512.ONE, Int512.parseInt("+000000001"));
        assertEquals(Int512.ZERO, Int512.parseInt("-000000000"));
        assertEquals(Int512.ONE, Int512.parseInt("0".repeat(10000) + "1"));
        assertEquals(Int512.ZERO, Int512.parseHexInt("0x1" + "0".repeat(128)));
        assertEquals(Int512.of(-1), Int512.parseHexInt("0x" + "f".repeat(1000)));
        assertEquals(Int512.ONE, Int512.parseHexInt("0x" + "0".repeat(10000) + "1"));
    }

    @Test
    void validatesTheWholeTextAndPreservesNullExceptionContracts() {
        for (String invalid : new String[] {null, "", "+", "-", " 1", "1 ", "1\n", "1_0", "1.0",
                "1e2", "0x1", "++1", "--1", "１２", "١", "9".repeat(1000) + "x"}) {
            assertFalse(Int512.isValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> Int512.parseInt(invalid));
        }
        for (String invalid : new String[] {"", "0x", "0X1", "0xA", "-0x1", "+0x1", "0x1 ", "0x1_0",
                "0xg" + "0".repeat(128), "0x" + "f".repeat(128) + "g"}) {
            assertFalse(Int512.isHexValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> Int512.parseHexInt(invalid));
        }
        assertFalse(Int512.isHexValid(null));
        assertThrowsExactly(NullPointerException.class, () -> Int512.parseHexInt(null));
        assertTrue(Int512.isValid("9".repeat(10000)));
        assertTrue(Int512.isHexValid("0x" + "f".repeat(10000)));
    }
}
