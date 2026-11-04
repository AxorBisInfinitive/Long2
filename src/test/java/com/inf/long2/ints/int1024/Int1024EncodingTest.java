package com.inf.long2.ints.int1024;

import static com.inf.long2.ints.int1024.Int1024TestSupport.MAXIMUM;
import static com.inf.long2.ints.int1024.Int1024TestSupport.MINIMUM;
import static com.inf.long2.ints.int1024.Int1024TestSupport.MODULUS;
import static com.inf.long2.ints.int1024.Int1024TestSupport.ONE;
import static com.inf.long2.ints.int1024.Int1024TestSupport.assertNumber;
import static com.inf.long2.ints.int1024.Int1024TestSupport.from;
import static com.inf.long2.ints.int1024.Int1024TestSupport.number;
import static com.inf.long2.ints.int1024.Int1024TestSupport.random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int1024;

class Int1024EncodingTest {
    @Test
    void preservesBigEndianGroupsAndIsolatesInputAndOutputArrays() {
        long[] words = {Long.MIN_VALUE, 0x0123_4567_89ab_cdefL, -1, 1, 0, Long.MAX_VALUE, -2, 7,
                8, -9, 10, 0x7654_3210_fedc_ba98L, -12, 13, -14, 37};
        Int1024 value = Int1024.of(words);
        assertEquals(value, Int1024.of(words[0], words[1], words[2], words[3], words[4], words[5], words[6], words[7],
                words[8], words[9], words[10], words[11], words[12], words[13], words[14], words[15]));
        byte[] encoded = value.toBytes();
        for (int group = 0; group < words.length; group++) {
            assertEquals(words[group], value.getLong(group));
            for (int index = 0; index < 8; index++) {
                assertEquals((byte) (words[group] >>> (56 - 8 * index)), encoded[group * 8 + index]);
            }
        }
        Int1024 restored = Int1024.of(encoded);
        words[0] = 0;
        encoded[0] = 0;
        byte[] output = value.toBytes();
        output[127] = 0;
        assertEquals(Long.MIN_VALUE, value.getLong(0));
        assertEquals(value, restored);
        assertNotSame(value.toBytes(), value.toBytes());
        assertEquals(37, value.toBytes()[127]);
        assertThrowsExactly(NullPointerException.class, () -> Int1024.of((long[]) null));
        assertThrowsExactly(NullPointerException.class, () -> Int1024.of((byte[]) null));
        for (int length : new int[] {0, 1, 8, 15, 17, 32}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> Int1024.of(new long[length]));
        }
        for (int length : new int[] {0, 1, 64, 127, 129, 256}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> Int1024.of(new byte[length]));
        }
    }

    @Test
    void roundTripsRandomEncodingsAndFormatsAgainstBigInteger() {
        Random random = new Random(0x1024DEC1L);
        for (int i = 0; i < 1500; i++) {
            Int1024 value = random(random);
            BigInteger expected = number(value);
            assertEquals(expected.toString(), value.toString());
            String hex = expected.mod(MODULUS).toString(16);
            assertEquals("0x" + "0".repeat(256 - hex.length()) + hex, value.toHexString());
            assertEquals(value, Int1024.parseInt(value.toString()));
            assertEquals(value, Int1024.parseHexInt(value.toHexString()));
            assertEquals(value, Int1024.of(value.toBytes()));
            assertTrue(Int1024.isValid(value.toString()));
            assertTrue(Int1024.isHexValid(value.toHexString()));
        }
    }

    @Test
    void parsesExtremeAndLongDecimalValuesModulo1024Bits() {
        for (BigInteger value : List.of(BigInteger.ZERO, ONE, ONE.negate(), MINIMUM, MAXIMUM,
                MINIMUM.subtract(ONE), MAXIMUM.add(ONE), MODULUS, MODULUS.negate(),
                BigInteger.TEN.pow(1000).subtract(ONE))) {
            assertNumber(value, Int1024.parseInt(value.toString()));
            Int1024 wrapped = from(value);
            assertEquals(number(wrapped).toString(), wrapped.toString());
        }
        for (int length = 1; length <= 30; length++) {
            String text = "9".repeat(length);
            assertNumber(new BigInteger(text), Int1024.parseInt(text));
            assertNumber(new BigInteger(text).negate(), Int1024.parseInt("-" + text));
        }
        assertEquals(Int1024.ONE, Int1024.parseInt("+000000001"));
        assertEquals(Int1024.ZERO, Int1024.parseInt("-000000000"));
        assertEquals(Int1024.ONE, Int1024.parseInt("0".repeat(10000) + "1"));
        assertEquals(Int1024.ZERO, Int1024.parseHexInt("0x1" + "0".repeat(256)));
        assertEquals(Int1024.of(-1), Int1024.parseHexInt("0x" + "f".repeat(1000)));
        assertEquals(Int1024.ONE, Int1024.parseHexInt("0x" + "0".repeat(10000) + "1"));
    }

    @Test
    void validatesTheWholeTextAndPreservesNullExceptionContracts() {
        for (String invalid : new String[] {null, "", "+", "-", " 1", "1 ", "1\n", "1_0", "1.0",
                "1e2", "0x1", "++1", "--1", "１２", "١", "9".repeat(1000) + "x"}) {
            assertFalse(Int1024.isValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> Int1024.parseInt(invalid));
        }
        for (String invalid : new String[] {"", "0x", "0X1", "0xA", "-0x1", "+0x1", "0x1 ", "0x1_0",
                "0xg" + "0".repeat(256), "0x" + "f".repeat(256) + "g"}) {
            assertFalse(Int1024.isHexValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> Int1024.parseHexInt(invalid));
        }
        assertFalse(Int1024.isHexValid(null));
        assertThrowsExactly(NullPointerException.class, () -> Int1024.parseHexInt(null));
        assertTrue(Int1024.isValid("9".repeat(10000)));
        assertTrue(Int1024.isHexValid("0x" + "f".repeat(10000)));
    }
}
