package com.inf.long2;

/**
 * 256 bit 定宽整型。
 *
 * 有符号、大端序。
 *
 * 已支持的算术运算：
 *
 * 按位运算：与、或、非、异或。
 */
public final class Int256 {
    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    public long getL0() {
        return l0;
    }

    public long getL1() {
        return l1;
    }

    public long getL2() {
        return l2;
    }

    public long getL3() {
        return l3;
    }

    public Int256(long l0) {
        this(l0 >> 63, l0 >> 63, l0 >> 63, l0);
    }

    public Int256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    public static final Int256 MAX_VALUE = new Int256(
            Long.MAX_VALUE, -1L, -1L, -1L);

    public static final Int256 MIN_VALUE = new Int256(
            Long.MIN_VALUE, 0L, 0L, 0L);

    public String toHexString() {
        return "0x%016x%016x%016x%016x".formatted(
                l0, l1, l2, l3);
    }

    public Int256 not() {
        return new Int256(~l0, ~l1, ~l2, ~l3);
    }

    public Int256 and(Int256 other) {
        return new Int256(
                l0 & other.l0,
                l1 & other.l1,
                l2 & other.l2,
                l3 & other.l3);
    }

    public Int256 or(Int256 other) {
        return new Int256(
                l0 | other.l0,
                l1 | other.l1,
                l2 | other.l2,
                l3 | other.l3);
    }

    public Int256 xor(Int256 other) {
        return new Int256(
                l0 ^ other.l0,
                l1 ^ other.l1,
                l2 ^ other.l2,
                l3 ^ other.l3);
    }
}
