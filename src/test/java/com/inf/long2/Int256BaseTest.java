package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class Int256BaseTest {
    @Test
    void testToHexString() {
        Int256 value = new Int256(~1119L);
        assertEquals("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffba0",
                value.toHexString());
    }

    @Test
    void testNot() {
        Int256 value1 = new Int256(-2147483649L);
        Int256 value2 = new Int256(2147483648L);
        assertEquals(
                value1.toHexString(),
                value2.not().toHexString());
    }

    @Test
    void testNotFullWidth() {
        Int256 value1 = new Int256(0L, -1L, 0L, -1L);
        Int256 value2 = new Int256(-1L, 0L, -1L, 0L);
        assertEquals(
                value1.toHexString(),
                value2.not().toHexString());
    }

    @Test
    void testAndFullWidth() {
        Int256 value1 = new Int256(-1L, 0L, -1L, 0L);
        Int256 value2 = new Int256(0L, -1L, -1L, -1L);
        Int256 expected = new Int256(0L, 0L, -1L, 0L);

        assertEquals(
                expected.toHexString(),
                value1.and(value2).toHexString());
    }

    @Test
    void testOrFullWidth() {
        Int256 value1 = new Int256(-1L, 0L, -1L, 0L);
        Int256 value2 = new Int256(0L, -1L, 0L, -1L);
        Int256 expected = new Int256(-1L, -1L, -1L, -1L);

        assertEquals(
                expected.toHexString(),
                value1.or(value2).toHexString());
    }

    @Test
    void testXorFullWidth() {
        Int256 value1 = new Int256(-1L, 0L, -1L, 0L);
        Int256 value2 = new Int256(0L, -1L, -1L, 0L);
        Int256 expected = new Int256(-1L, -1L, 0L, 0L);

        assertEquals(
                expected.toHexString(),
                value1.xor(value2).toHexString());
    }

    @Test
    void testFourBitOperation() {
        Int256 a = new Int256(
                0x123456789ABCDEFL, -1L, 0L, Long.MIN_VALUE);
        Int256 b = new Int256(
                -1L, 0x123456789ABCDEFL, Long.MAX_VALUE, 0L);

        // ~(a & b) == ~a | ~b
        assertEquals(
                a.and(b).not().toHexString(),
                a.not().or(b.not()).toHexString());

        // ~(a | b) == ~a & ~b
        assertEquals(
                a.or(b).not().toHexString(),
                a.not().and(b.not()).toHexString());

        // a ^ b == (a | b) & ~(a & b)
        assertEquals(
                a.xor(b).toHexString(),
                a.or(b).and(a.and(b).not()).toHexString());
    }

    @Test
    void testMinMaxValue() {
        assertEquals(
                "0x7fffffffffffffff" + "ffffffffffffffff".repeat(3),
                Int256.MAX_VALUE.toHexString());

        assertEquals(
                "0x8000000000000000" + "0000000000000000".repeat(3),
                Int256.MIN_VALUE.toHexString());
    }
}
