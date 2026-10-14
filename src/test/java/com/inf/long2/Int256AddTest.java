package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class Int256AddTest {

    @Test
    void testAddRandom() {
        Random random = new Random(0x256ADD);
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

            BigInteger sum = bigA.add(bigB).and(mask);
            Int256 expected = Int256.parseHexInt256(
                    "0x" + sum.toString(16));

            assertEquals(expected, a.add(b));
        }
    }

    @Test
    void testAddBoundary() {
        Int256 zero = new Int256(0L);
        Int256 one = new Int256(1L);
        Int256 negativeOne = new Int256(-1L);

        // 基本运算
        assertEquals(zero, zero.add(zero));
        assertEquals(one, zero.add(one));
        assertEquals(zero, negativeOne.add(one));
        assertEquals(new Int256(-2L),
                negativeOne.add(negativeOne));

        // long 边界，不发生 256-bit 溢出
        assertEquals(
                new Int256(0L, 0L, 0L, Long.MIN_VALUE),
                new Int256(Long.MAX_VALUE).add(one));

        assertEquals(
                new Int256(-1L, -1L, -1L, Long.MAX_VALUE),
                new Int256(Long.MIN_VALUE).add(negativeOne));

        // 跨越第一个 64-bit 边界
        assertEquals(
                new Int256(0L, 0L, 1L, 0L),
                new Int256(0L, 0L, 0L, -1L).add(one));

        // 跨越第二个 64-bit 边界
        assertEquals(
                new Int256(0L, 1L, 0L, 0L),
                new Int256(0L, 0L, -1L, -1L).add(one));

        // 跨越第三个 64-bit 边界
        assertEquals(
                new Int256(1L, 0L, 0L, 0L),
                new Int256(0L, -1L, -1L, -1L).add(one));

        // 完整 256-bit 溢出回绕
        assertEquals(Int256.MIN_VALUE,
                Int256.MAX_VALUE.add(one));

        assertEquals(Int256.MAX_VALUE,
                Int256.MIN_VALUE.add(negativeOne));

        assertEquals(zero,
                Int256.MIN_VALUE.add(Int256.MIN_VALUE));

        assertEquals(new Int256(-2L),
                Int256.MAX_VALUE.add(Int256.MAX_VALUE));
    }
}
