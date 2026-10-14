package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class Int256Base2Test {
    @Test
    void testBitLength() {
        // 零及基本正负数
        assertEquals(0, Int256.ZERO.bitLength());
        assertEquals(1, new Int256(1L).bitLength());
        assertEquals(2, new Int256(2L).bitLength());
        assertEquals(2, new Int256(3L).bitLength());
        assertEquals(0, new Int256(-1L).bitLength());
        assertEquals(1, new Int256(-2L).bitLength());
        assertEquals(2, new Int256(-3L).bitLength());

        // 64-bit 边界
        assertEquals(63, new Int256(Long.MAX_VALUE).bitLength());
        assertEquals(63, new Int256(Long.MIN_VALUE).bitLength());
        assertEquals(64,
                new Int256(0L, 0L, 0L, Long.MIN_VALUE).bitLength());

        // 跨 long 边界
        assertEquals(65,
                new Int256(0L, 0L, 1L, 0L).bitLength());
        assertEquals(129,
                new Int256(0L, 1L, 0L, 0L).bitLength());
        assertEquals(193,
                new Int256(1L, 0L, 0L, 0L).bitLength());

        // 负数的符号扩展
        assertEquals(64,
                new Int256(-1L, -1L, -1L, 0L).bitLength());
        assertEquals(65,
                new Int256(-1L, -1L, -2L, 0L).bitLength());

        // 256-bit 极值
        assertEquals(255, Int256.MAX_VALUE.bitLength());
        assertEquals(255, Int256.MIN_VALUE.bitLength());
    }

    @Test
    void testBitCount() {
        // 全零、全一与极值
        assertEquals(0, Int256.ZERO.bitCount());
        assertEquals(256, new Int256(-1L).bitCount());
        assertEquals(1, Int256.MIN_VALUE.bitCount());
        assertEquals(255, Int256.MAX_VALUE.bitCount());

        // 单 bit
        assertEquals(1, new Int256(1L).bitCount());
        assertEquals(1,
                new Int256(0L, 0L, 1L, 0L).bitCount());
        assertEquals(1,
                new Int256(0L, 1L, 0L, 0L).bitCount());
        assertEquals(1,
                new Int256(1L, 0L, 0L, 0L).bitCount());

        // 四个 long 独立统计
        assertEquals(128,
                new Int256(
                        0x5555555555555555L,
                        0xAAAAAAAAAAAAAAAAL,
                        -1L,
                        0L).bitCount());

        // 固定宽度负数
        assertEquals(255, new Int256(-2L).bitCount());
        assertEquals(254, new Int256(-4L).bitCount());
    }

    @Test
    void testToLongExact() {
        // 有效范围
        assertEquals(0L, Int256.ZERO.toLongExact());
        assertEquals(1L, new Int256(1L).toLongExact());
        assertEquals(-1L, new Int256(-1L).toLongExact());
        assertEquals(Long.MAX_VALUE,
                new Int256(Long.MAX_VALUE).toLongExact());
        assertEquals(Long.MIN_VALUE,
                new Int256(Long.MIN_VALUE).toLongExact());

        // 显式符号扩展
        assertEquals(Long.MAX_VALUE,
                new Int256(0L, 0L, 0L, Long.MAX_VALUE)
                        .toLongExact());

        assertEquals(Long.MIN_VALUE,
                new Int256(-1L, -1L, -1L, Long.MIN_VALUE)
                        .toLongExact());

        // 正数超过 long 范围
        assertThrows(ArithmeticException.class,
                () -> new Int256(0L, 0L, 0L, Long.MIN_VALUE)
                        .toLongExact());

        assertThrows(ArithmeticException.class,
                () -> new Int256(0L, 0L, 1L, 0L)
                        .toLongExact());

        // 负数超过 long 范围
        assertThrows(ArithmeticException.class,
                () -> new Int256(-1L, -1L, -1L, Long.MAX_VALUE)
                        .toLongExact());

        assertThrows(ArithmeticException.class,
                () -> new Int256(-1L, -1L, -1L, 0L)
                        .toLongExact());

        // 256-bit 极值
        assertThrows(ArithmeticException.class,
                () -> Int256.MAX_VALUE.toLongExact());

        assertThrows(ArithmeticException.class,
                () -> Int256.MIN_VALUE.toLongExact());
    }

    @Test
    void testToDouble() {
        // 基本精确转换
        assertEquals(0.0, Int256.ZERO.toDouble());
        assertEquals(1.0, new Int256(1L).toDouble());
        assertEquals(-1.0, new Int256(-1L).toDouble());
        assertEquals(123456789.0,
                new Int256(123456789L).toDouble());

        // 53-bit 有效精度边界
        assertEquals(9007199254740992.0,
                new Int256(9007199254740992L).toDouble());

        // 舍入到最近偶数
        assertEquals(9007199254740992.0,
                new Int256(9007199254740993L).toDouble());

        assertEquals(9007199254740996.0,
                new Int256(9007199254740995L).toDouble());

        assertEquals(-9007199254740992.0,
                new Int256(-9007199254740993L).toDouble());

        assertEquals(-9007199254740996.0,
                new Int256(-9007199254740995L).toDouble());

        // long 边界
        assertEquals((double) Long.MAX_VALUE,
                new Int256(Long.MAX_VALUE).toDouble());

        assertEquals((double) Long.MIN_VALUE,
                new Int256(Long.MIN_VALUE).toDouble());

        // 跨 long 边界
        assertEquals(0x1.0p64,
                new Int256(0L, 0L, 1L, 0L).toDouble());

        assertEquals(0x1.0p128,
                new Int256(0L, 1L, 0L, 0L).toDouble());

        assertEquals(0x1.0p192,
                new Int256(1L, 0L, 0L, 0L).toDouble());

        // 256-bit 极值
        assertEquals(0x1.0p255,
                Int256.MAX_VALUE.toDouble());

        assertEquals(-0x1.0p255,
                Int256.MIN_VALUE.toDouble());

        // 随机 256-bit 整数与标准高精度转换对比
        Random random = new Random(0x256D0B1EL);
        BigInteger modulus = BigInteger.ONE.shiftLeft(256);

        for (int i = 0; i < 10000; i++) {
            Int256 value = new Int256(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            BigInteger reference = new BigInteger(
                    value.toHexString().substring(2), 16);

            if (value.isNegative()) {
                reference = reference.subtract(modulus);
            }

            double expected = reference.doubleValue();
            double actual = value.toDouble();

            assertEquals(
                    Double.doubleToRawLongBits(expected),
                    Double.doubleToRawLongBits(actual),
                    value.toHexString());
        }
    }
}
