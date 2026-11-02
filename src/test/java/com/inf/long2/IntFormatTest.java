package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.inf.long2.ints.Int256;

class IntFormatTest {
    @ParameterizedTest
    @ValueSource(ints = {64, 128, 192, 256, 320, 1024, 2147483584})
    void acceptsPositiveWordAlignedWidths(int width) {
        assertEquals(width, new Int.Format(width).bitWidth());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -256, -64, -1, 0, 1, 63, 65, 255, Integer.MAX_VALUE})
    void rejectsNonpositiveOrUnalignedWidths(int width) {
        assertThrowsExactly(IllegalArgumentException.class, () -> new Int.Format(width));
    }

    @Test
    void comparesFormatsByWidth() {
        Int.Format format = new Int.Format(256), same = new Int.Format(256);
        assertEquals(format, same);
        assertEquals(format.hashCode(), same.hashCode());
        assertNotEquals(format, new Int.Format(512));
    }

    @Test
    void reportsTheSameWidthForEveryValueWithoutChangingBitLengthSemantics() {
        assertEquals(new Int.Format(256), Int256.FORMAT);
        for (Int<?> value : List.of(Int256.ZERO, Int256.ONE, Int256.of(-1L), Int256.MIN_VALUE, Int256.MAX_VALUE)) {
            assertEquals(Int256.FORMAT, value.format());
            assertEquals(value.format().bitWidth() / 8, value.toBytes().length);
        }
        assertEquals(0, Int256.ZERO.bitLength());
        assertEquals(0, Int256.of(-1L).bitLength());
        assertEquals(255, Int256.MIN_VALUE.bitLength());
    }
}
