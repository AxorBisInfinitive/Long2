package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class Int256BaseTest {
    @Test
    void testToHexString() {
        Int256 value = new Int256(~1119L);
        assertEquals(
                value.toHexString(),
                "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffba0");
    }
}
