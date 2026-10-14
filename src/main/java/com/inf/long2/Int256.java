package com.inf.long2;

import java.util.Objects;

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

    /**
     * 必须带有 0x 前缀，必须使用小写字母。
     * 位数不足时补齐前导零，位数溢出时截断高位。
     * 按原始位模式解释。位全空时不合法。
     */
    public static Int256 parseHexInt256(String value) {
        if (!value.startsWith("0x")) {
            throw new NumberFormatException("Missing 0x prefix");
        }

        String hex = value.substring(2);
        if (hex.isEmpty()) {
            throw new NumberFormatException("Empty hexadecimal value");
        }

        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new NumberFormatException("Invalid hexadecimal digit: " + c);
            }
        }

        if (hex.length() > 64) {
            hex = hex.substring(hex.length() - 64);
        }

        hex = "0".repeat(64 - hex.length()) + hex;

        return new Int256(
                Long.parseUnsignedLong(hex.substring(0, 16), 16),
                Long.parseUnsignedLong(hex.substring(16, 32), 16),
                Long.parseUnsignedLong(hex.substring(32, 48), 16),
                Long.parseUnsignedLong(hex.substring(48, 64), 16));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Int256 other)) {
            return false;
        }
        return l0 == other.l0
                && l1 == other.l1
                && l2 == other.l2
                && l3 == other.l3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(l0, l1, l2, l3);
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
