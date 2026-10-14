package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class Int256Base32StringsTest {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";

    @Test
    void testToBase32String() {
        // 零
        assertEquals(
                "a".repeat(52),
                Int256.ZERO.toBase32String());

        // 正一
        assertEquals(
                "a".repeat(51) + "q",
                new Int256(1L).toBase32String());

        // 负一
        assertEquals(
                "7".repeat(51) + "q",
                new Int256(-1L).toBase32String());

        // 256-bit 极值
        assertEquals(
                "p" + "7".repeat(50) + "q",
                Int256.MAX_VALUE.toBase32String());

        assertEquals(
                "q" + "a".repeat(51),
                Int256.MIN_VALUE.toBase32String());

        // 编码长度恒为 52
        assertEquals(52, Int256.ZERO.toBase32String().length());
        assertEquals(52, Int256.MAX_VALUE.toBase32String().length());
        assertEquals(52, Int256.MIN_VALUE.toBase32String().length());
    }

    @Test
    void testParseBase32Int256() {
        // 基本数值
        assertEquals(
                Int256.ZERO,
                Int256.parseBase32Int256("a".repeat(52)));

        assertEquals(
                new Int256(1L),
                Int256.parseBase32Int256(
                        "a".repeat(51) + "q"));

        assertEquals(
                new Int256(-1L),
                Int256.parseBase32Int256(
                        "7".repeat(51) + "q"));

        // 256-bit 极值
        assertEquals(
                Int256.MAX_VALUE,
                Int256.parseBase32Int256(
                        "p" + "7".repeat(50) + "q"));

        assertEquals(
                Int256.MIN_VALUE,
                Int256.parseBase32Int256(
                        "q" + "a".repeat(51)));

        // 完整 256-bit 位模式
        Int256 value = new Int256(
                0x0123456789ABCDEFL,
                0xFEDCBA9876543210L,
                0x13579BDF2468ACE0L,
                0x0F1E2D3C4B5A6978L);

        assertEquals(
                value,
                Int256.parseBase32Int256(
                        referenceBase32(value)));
    }

    @Test
    void testParseBase32Invalid() {
        // null 与长度错误
        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256(null));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256(""));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256("a".repeat(51)));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256("a".repeat(53)));

        // 不允许大写字母、数字 0/1/8/9 及特殊字符
        char[] invalid = {
                'A', 'Q', '0', '1', '8', '9',
                '=', '+', '/', '_', '-', ' '
        };

        for (char c : invalid) {
            String value = "a".repeat(10)
                    + c + "a".repeat(41);

            assertThrows(
                    NumberFormatException.class,
                    () -> Int256.parseBase32Int256(value),
                    "Invalid character: " + c);
        }

        // 最后一组必须有四个零填充 bit
        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256(
                        "a".repeat(51) + "b"));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256(
                        "a".repeat(51) + "7"));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256(
                        "a".repeat(51) + "Q"));

        // 不允许 Base32 padding
        assertThrows(NumberFormatException.class,
                () -> Int256.parseBase32Int256(
                        "a".repeat(51) + "="));
    }

    @Test
    void testBase32BitBoundary() {
        int[] bits = {
                0, 1, 4, 5,
                62, 63, 64, 65,
                126, 127, 128, 129,
                190, 191, 192, 193,
                254, 255
        };

        for (int bit : bits) {
            Int256 value = new Int256(1L).shift(bit);
            String encoded = value.toBase32String();

            // 独立验证 Base32 编码
            assertEquals(
                    referenceBase32(value),
                    encoded,
                    "Bit index: " + bit);

            // 验证跨 long 边界的解码
            assertEquals(
                    value,
                    Int256.parseBase32Int256(encoded),
                    "Bit index: " + bit);
        }
    }

    @Test
    void testBase32RandomRoundTrip() {
        Random random = new Random(0xB32F00DL);

        for (int i = 0; i < 10000; i++) {
            Int256 value = new Int256(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            String encoded = value.toBase32String();

            // 固定长度
            assertEquals(52, encoded.length());

            // 与独立参考算法比较
            assertEquals(
                    referenceBase32(value),
                    encoded,
                    value.toHexString());

            // 编码、解码往返
            assertEquals(
                    value,
                    Int256.parseBase32Int256(encoded));

            // 规范编码的唯一性
            assertEquals(
                    encoded,
                    Int256.parseBase32Int256(encoded)
                            .toBase32String());
        }
    }

    // 独立参考实现：使用 BigInteger 按 bit 提取 Base32
    private static String referenceBase32(Int256 value) {
        BigInteger bits = new BigInteger(
                value.toHexString().substring(2), 16);

        char[] result = new char[52];

        for (int i = 0; i < 52; i++) {
            int digit = 0;

            for (int j = 0; j < 5; j++) {
                int bit = 255 - (i * 5 + j);

                digit <<= 1;

                if (bit >= 0 && bits.testBit(bit)) {
                    digit |= 1;
                }
            }

            result[i] = ALPHABET.charAt(digit);
        }

        return new String(result);
    }
}
