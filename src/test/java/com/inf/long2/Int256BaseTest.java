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
}
