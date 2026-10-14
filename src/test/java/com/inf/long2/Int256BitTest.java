package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class Int256BitTest {

    @Test
    void testShift() {
        Int256 value = new Int256(1L);

        // 跨 long 边界
        assertEquals(
                new Int256(0L, 0L, 1L, 0L),
                value.shift(64));

        assertEquals(
                new Int256(0L, 1L, 0L, 0L),
                value.shift(128));

        assertEquals(
                new Int256(1L, 0L, 0L, 0L),
                value.shift(192));

        // 算术右移，符号扩展
        assertEquals(
                new Int256(-1L),
                new Int256(-2L).shift(-1));

        // 左移溢出
        assertEquals(
                new Int256(0L),
                value.shift(256));

        // 右移超出位宽
        assertEquals(
                new Int256(-1L),
                Int256.MIN_VALUE.shift(-256));

        // int 最小值不会导致取负溢出
        assertEquals(
                new Int256(-1L),
                Int256.MIN_VALUE.shift(Integer.MIN_VALUE));

        // 不移位
        assertEquals(value, value.shift(0));
    }

    @Test
    void testMinecraftCoordinateShifts() {
        // 正坐标：Block -> Chunk / Section -> Region
        Int256 block = new Int256(8193L);
        Int256 chunk = new Int256(512L);
        Int256 region = new Int256(16L);

        assertEquals(chunk, block.shift(-4));
        assertEquals(region, chunk.shift(-5));
        assertEquals(region, block.shift(-9));

        // 反向转换：Region -> Chunk -> Block
        assertEquals(new Int256(8192L), chunk.shift(4));
        assertEquals(chunk, region.shift(5));
        assertEquals(new Int256(8192L), region.shift(9));

        // 负坐标：验证算术右移向负无穷取整
        Int256 negativeBlock = new Int256(-513L);
        Int256 negativeChunk = new Int256(-33L);
        Int256 negativeRegion = new Int256(-2L);

        assertEquals(negativeChunk, negativeBlock.shift(-4));
        assertEquals(negativeRegion, negativeChunk.shift(-5));
        assertEquals(negativeRegion, negativeBlock.shift(-9));

        // 负坐标反向转换与对齐
        assertEquals(new Int256(-528L), negativeChunk.shift(4));
        assertEquals(new Int256(-64L), negativeRegion.shift(5));
        assertEquals(new Int256(-1024L), negativeRegion.shift(9));

        // 原点附近的负坐标
        assertEquals(new Int256(-1L), new Int256(-1L).shift(-4));
        assertEquals(new Int256(-1L), new Int256(-1L).shift(-9));
        assertEquals(new Int256(-512L),
                new Int256(-1L).shift(-9).shift(9));

        // 跨 64-bit 边界：Block = 2^64
        Int256 largeBlock = new Int256(0L, 0L, 1L, 0L);
        Int256 largeChunk = new Int256(0L, 0L, 0L, 1L << 60);
        Int256 largeRegion = new Int256(0L, 0L, 0L, 1L << 55);

        assertEquals(largeChunk, largeBlock.shift(-4));
        assertEquals(largeRegion, largeChunk.shift(-5));
        assertEquals(largeRegion, largeBlock.shift(-9));

        // 跨边界反向转换，验证完整恢复
        assertEquals(largeBlock, largeChunk.shift(4));
        assertEquals(largeChunk, largeRegion.shift(5));
        assertEquals(largeBlock, largeRegion.shift(9));
    }

}
