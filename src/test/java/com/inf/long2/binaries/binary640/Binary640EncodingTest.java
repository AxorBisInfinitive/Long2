package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.binaries.Binary640;

class Binary640EncodingTest {
    @Test
    void pinsFormatAndConstantsByIndependentRawValues() {
        assertEquals(new Binary.Format(616, -8388606, 8388607), Binary640.FORMAT);
        Binary640[] values = {Binary640.ZERO, Binary640.NEGATIVE_ZERO, Binary640.ONE, Binary640.MIN_VALUE,
                Binary640.MIN_NORMAL, Binary640.MAX_VALUE, Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY, Binary640.NaN};
        BigInteger infinity = BigInteger.valueOf(16777215).shiftLeft(615);
        BigInteger[] expected = {BigInteger.ZERO, ONE.shiftLeft(639), BigInteger.valueOf(8388607).shiftLeft(615), ONE,
                ONE.shiftLeft(615), BigInteger.valueOf(16777215).shiftLeft(615).subtract(ONE), infinity,
                infinity.setBit(639), infinity.setBit(614)};
        for (int i = 0; i < values.length; i++) {
            assertEquals(expected[i], Binary640Reference.raw(values[i]));
            assertEquals(Binary640.FORMAT, values[i].format());
        }
    }

    @Test
    void preservesEveryRawBitAndByteWithoutAliasing() {
        for (int bit = 0; bit < 640; bit++) {
            Binary640 value = Binary640Reference.fromRaw(ONE.shiftLeft(bit));
            byte[] bytes = value.toBytes();
            byte[] expected = new byte[80];
            expected[79 - bit / 8] = (byte) (1 << (bit & 7));
            assertArrayEquals(expected, bytes);
            Binary640 copy = Binary640.of(bytes);
            bytes[0] ^= 1;
            assertRaw(value, copy);
            assertArrayEquals(expected, copy.toBytes());
        }
        Random random = new Random(12345);
        for (int i = 0; i < 200; i++) {
            byte[] bytes = new byte[80];
            random.nextBytes(bytes);
            assertArrayEquals(bytes, Binary640.of(bytes).toBytes());
        }
    }

    @Test
    void classifiesAndDecomposesNormalSubnormalAndSpecialValues() {
        assertTrue(Binary640.ONE.isNormal());
        assertTrue(Binary640.MIN_NORMAL.isNormal());
        assertTrue(Binary640.MIN_VALUE.isSubnormal());
        assertTrue(Binary640.MIN_VALUE.isFinite());
        assertTrue(Binary640.NEGATIVE_ZERO.isZero());
        assertTrue(Binary640.NEGATIVE_ZERO.signBit());
        assertTrue(Binary640.POSITIVE_INFINITY.isInfinite());
        assertTrue(Binary640.NaN.isNaN());
        assertEquals(-615, Binary640.ONE.scaleExponent());
        assertEquals(-8389221, Binary640.MIN_VALUE.scaleExponent());
        assertEquals(1L << 39, Binary640.ONE.getSignificandLong(0));
        assertEquals(1, Binary640.MIN_VALUE.getSignificandLong(9));
        for (int index : new int[] {-1, 10}) {
            assertThrows(IndexOutOfBoundsException.class, () -> Binary640.ONE.getRawLong(index));
            assertThrows(IndexOutOfBoundsException.class, () -> Binary640.ONE.getSignificandLong(index));
            assertThrows(IllegalStateException.class, () -> Binary640.NaN.getSignificandLong(index));
        }
        assertThrows(IllegalStateException.class, () -> Binary640.POSITIVE_INFINITY.scaleExponent());
    }

    @Test
    void ordersNumbersAndCanonicalizesOnlyNumericNaNs() {
        Binary640 rawNaN = Binary640Reference.fromRaw(BigInteger.valueOf(16777215).shiftLeft(615).setBit(639).setBit(0));
        assertTrue(rawNaN.isNaN());
        assertEquals(Binary640.NaN, rawNaN);
        assertEquals(Binary640.NaN.hashCode(), rawNaN.hashCode());
        assertEquals(0, rawNaN.compareTo(Binary640.NaN));
        assertEquals(Binary.Comparison.UNORDERED, rawNaN.compareNumerically(rawNaN));
        assertRaw(Binary640.NaN, rawNaN.negate());
        assertRaw(Binary640.NaN, Binary640.of(rawNaN));
        assertEquals(Binary.Comparison.EQUAL, Binary640.ZERO.compareNumerically(Binary640.NEGATIVE_ZERO));
        assertNotEquals(Binary640.ZERO, Binary640.NEGATIVE_ZERO);
        Binary640[] ordered = {Binary640.NEGATIVE_INFINITY, Binary640.MAX_VALUE.negate(), Binary640.ONE.negate(),
                Binary640.MIN_VALUE.negate(), Binary640.NEGATIVE_ZERO, Binary640.ZERO, Binary640.MIN_VALUE,
                Binary640.ONE, Binary640.MAX_VALUE, Binary640.POSITIVE_INFINITY, Binary640.NaN};
        for (int i = 0; i < ordered.length; i++) {
            for (int j = 0; j < ordered.length; j++) {
                assertEquals(Integer.signum(i - j), Integer.signum(ordered[i].compareTo(ordered[j])));
                assertEquals(i == j, ordered[i].equals(ordered[j]));
            }
        }
        assertFalse(Binary640.ZERO.equals(null));
        assertFalse(Binary640.ONE.equals(1L));
    }

    @Test
    void stepsAcrossWordSubnormalAndInfinityBoundaries() {
        for (int bit = 0; bit < 639; bit++) {
            Binary640 a = Binary640Reference.fromRaw(ONE.shiftLeft(bit).subtract(ONE));
            if (!a.isNaN()) {
                Binary640 b = a.nextUp();
                assertEquals(ONE.shiftLeft(bit), Binary640Reference.raw(b));
                assertRaw(a, b.nextDown());
                if (!a.isZero()) {
                    assertRaw(a.negate(), a.negate().nextDown().nextUp());
                }
            }
        }
        assertRaw(Binary640.MIN_VALUE, Binary640.NEGATIVE_ZERO.nextUp());
        assertRaw(Binary640.MIN_VALUE.negate(), Binary640.ZERO.nextDown());
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.MIN_VALUE.negate().nextUp());
        assertRaw(Binary640.MAX_VALUE, Binary640.POSITIVE_INFINITY.nextDown());
        assertRaw(Binary640.MAX_VALUE.negate(), Binary640.NEGATIVE_INFINITY.nextUp());
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.POSITIVE_INFINITY.nextUp());
        assertRaw(Binary640.NEGATIVE_INFINITY, Binary640.NEGATIVE_INFINITY.nextDown());
    }

    @Test
    void rejectsInvalidRawArguments() {
        assertThrows(NullPointerException.class, () -> Binary640.of((byte[]) null));
        for (int length : new int[] {0, 1, 79, 81, 160}) {
            assertThrows(IllegalArgumentException.class, () -> Binary640.of(new byte[length]));
        }
    }
}
