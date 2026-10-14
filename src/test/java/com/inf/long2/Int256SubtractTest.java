package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class Int256SubtractTest {

    @Test
    void testSubtractRandom() {
        Random random = new Random(0x2565B7L);
        BigInteger mask = BigInteger.ONE.shiftLeft(256)
                .subtract(BigInteger.ONE);

        for (int i = 0; i < 10000; i++) {
            Int256 a = new Int256(
                    random.nextLong(), random.nextLong(),
                    random.nextLong(), random.nextLong());
            Int256 b = new Int256(
                    random.nextLong(), random.nextLong(),
                    random.nextLong(), random.nextLong());

            BigInteger bigA = new BigInteger(
                    a.toHexString().substring(2), 16);
            BigInteger bigB = new BigInteger(
                    b.toHexString().substring(2), 16);

            BigInteger difference = bigA.subtract(bigB).and(mask);
            Int256 expected = Int256.parseHexInt256(
                    "0x" + difference.toString(16));

            assertEquals(expected, a.subtract(b));
        }
    }

    @Test
    void testSubtractBoundary() {
        Int256 zero = new Int256(0L);
        Int256 one = new Int256(1L);
        Int256 negativeOne = new Int256(-1L);

        // 基本运算
        assertEquals(zero, zero.subtract(zero));
        assertEquals(zero, one.subtract(one));
        assertEquals(negativeOne, zero.subtract(one));
        assertEquals(new Int256(-2L),
                negativeOne.subtract(one));

        // long 边界，不发生 256-bit 溢出
        assertEquals(
                new Int256(-1L, -1L, -1L, Long.MAX_VALUE),
                new Int256(Long.MIN_VALUE).subtract(one));

        assertEquals(
                new Int256(0L, 0L, 0L, Long.MIN_VALUE),
                new Int256(Long.MAX_VALUE).subtract(negativeOne));

        // 跨越第一个 64-bit 边界
        assertEquals(
                new Int256(0L, 0L, 0L, -1L),
                new Int256(0L, 0L, 1L, 0L).subtract(one));

        // 跨越第二个 64-bit 边界
        assertEquals(
                new Int256(0L, 0L, -1L, -1L),
                new Int256(0L, 1L, 0L, 0L).subtract(one));

        // 跨越第三个 64-bit 边界
        assertEquals(
                new Int256(0L, -1L, -1L, -1L),
                new Int256(1L, 0L, 0L, 0L).subtract(one));

        // 完整 256-bit 溢出回绕
        assertEquals(Int256.MAX_VALUE,
                Int256.MIN_VALUE.subtract(one));

        assertEquals(Int256.MIN_VALUE,
                Int256.MAX_VALUE.subtract(negativeOne));

        assertEquals(zero,
                Int256.MIN_VALUE.subtract(Int256.MIN_VALUE));

        assertEquals(negativeOne,
                Int256.MAX_VALUE.subtract(Int256.MIN_VALUE));
    }

}
