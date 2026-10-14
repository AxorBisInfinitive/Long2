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
public final class Int256 implements Comparable<Int256> {
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

    public static final Int256 ZERO = new Int256(0L);

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

    /**
     * bits 为正时左移，为负时算术右移。
     * 超出 256-bit 范围的位被丢弃，右移时进行符号扩展。
     */
    public Int256 shift(int bits) {
        if (bits == 0) {
            return this;
        }
        if (bits >= 256) {
            return new Int256(0L);
        }
        if (bits <= -256) {
            return new Int256(l0 >> 63);
        }

        long a = l0;
        long b = l1;
        long c = l2;
        long d = l3;

        if (bits > 0) {
            switch (bits >>> 6) {
                case 1 -> {
                    a = b;
                    b = c;
                    c = d;
                    d = 0L;
                }
                case 2 -> {
                    a = c;
                    b = d;
                    c = d = 0L;
                }
                case 3 -> {
                    a = d;
                    b = c = d = 0L;
                }
            }

            int n = bits & 63;
            if (n != 0) {
                return new Int256(
                        (a << n) | (b >>> (64 - n)),
                        (b << n) | (c >>> (64 - n)),
                        (c << n) | (d >>> (64 - n)),
                        d << n);
            }
        } else {
            int distance = -bits;
            long sign = a >> 63;

            switch (distance >>> 6) {
                case 1 -> {
                    d = c;
                    c = b;
                    b = a;
                    a = sign;
                }
                case 2 -> {
                    d = b;
                    c = a;
                    a = b = sign;
                }
                case 3 -> {
                    d = a;
                    a = b = c = sign;
                }
            }

            int n = distance & 63;
            if (n != 0) {
                return new Int256(
                        a >> n,
                        (b >>> n) | (a << (64 - n)),
                        (c >>> n) | (b << (64 - n)),
                        (d >>> n) | (c << (64 - n)));
            }
        }

        return new Int256(a, b, c, d);
    }

    public Int256 add(Int256 other) {
        long r3 = l3 + other.l3;
        long carry = Long.compareUnsigned(r3, l3) < 0 ? 1L : 0L;

        long r2 = l2 + other.l2 + carry;
        carry = Long.compareUnsigned(r2, l2) < 0
                || (carry != 0 && r2 == l2) ? 1L : 0L;

        long r1 = l1 + other.l1 + carry;
        carry = Long.compareUnsigned(r1, l1) < 0
                || (carry != 0 && r1 == l1) ? 1L : 0L;

        long r0 = l0 + other.l0 + carry;

        return new Int256(r0, r1, r2, r3);
    }

    public Int256 subtract(Int256 other) {
        long r3 = l3 - other.l3;
        long borrow = Long.compareUnsigned(l3, other.l3) < 0 ? 1L : 0L;

        long r2 = l2 - other.l2 - borrow;
        borrow = Long.compareUnsigned(l2, other.l2) < 0
                || (borrow != 0 && l2 == other.l2) ? 1L : 0L;

        long r1 = l1 - other.l1 - borrow;
        borrow = Long.compareUnsigned(l1, other.l1) < 0
                || (borrow != 0 && l1 == other.l1) ? 1L : 0L;

        long r0 = l0 - other.l0 - borrow;

        return new Int256(r0, r1, r2, r3);
    }

    public Int256 negate() {
        return ZERO.subtract(this);
    }

    public Int256 multiply(Int256 other) {
        long r3 = l3 * other.l3;

        // 第二个 64-bit
        long r2 = Math.unsignedMultiplyHigh(l3, other.l3);
        long carry = 0L;
        long v = l3 * other.l2;
        long t = r2 + v;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;

        v = l2 * other.l3;
        t = r2 + v;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;

        // 第三个 64-bit
        long r1 = carry;
        carry = 0L;

        v = Math.unsignedMultiplyHigh(l3, other.l2);
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = Math.unsignedMultiplyHigh(l2, other.l3);
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = l3 * other.l1;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = l2 * other.l2;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = l1 * other.l3;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        // 最高 64-bit，最终进位直接丢弃
        long r0 = carry
                + Math.unsignedMultiplyHigh(l3, other.l1)
                + Math.unsignedMultiplyHigh(l2, other.l2)
                + Math.unsignedMultiplyHigh(l1, other.l3)
                + l3 * other.l0
                + l2 * other.l1
                + l1 * other.l2
                + l0 * other.l3;

        return new Int256(r0, r1, r2, r3);
    }

    @Override
    public int compareTo(Int256 other) {
        int cmp = Long.compare(l0, other.l0);
        if (cmp != 0)
            return cmp;

        cmp = Long.compareUnsigned(l1, other.l1);
        if (cmp != 0)
            return cmp;

        cmp = Long.compareUnsigned(l2, other.l2);
        if (cmp != 0)
            return cmp;

        return Long.compareUnsigned(l3, other.l3);
    }

    public int signum() {
        if (isNegative())
            return -1;
        return isZero() ? 0 : 1;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3) == 0L;
    }

    public boolean isNegative() {
        return l0 < 0L;
    }

    /**
     * bit 0 为最低有效位，bit 255 为符号位。
     */
    public boolean testBit(int index) {
        if (index < 0 || index >= 256) {
            throw new IndexOutOfBoundsException(index);
        }

        long word = switch (index >>> 6) {
            case 0 -> l3;
            case 1 -> l2;
            case 2 -> l1;
            default -> l0;
        };

        return (word & (1L << (index & 63))) != 0L;
    }

    /** 返回去除冗余符号扩展位后的有效位数，不计符号位。 */
    public int bitLength() {
        boolean negative = l0 < 0L;

        long a0 = negative ? ~l0 : l0;
        long a1 = negative ? ~l1 : l1;
        long a2 = negative ? ~l2 : l2;
        long a3 = negative ? ~l3 : l3;

        if (a0 != 0L)
            return 256 - Long.numberOfLeadingZeros(a0);
        if (a1 != 0L)
            return 192 - Long.numberOfLeadingZeros(a1);
        if (a2 != 0L)
            return 128 - Long.numberOfLeadingZeros(a2);
        return 64 - Long.numberOfLeadingZeros(a3);
    }

    /** 返回完整 256-bit 位模式中值为 1 的 bit 数量。 */
    public int bitCount() {
        return Long.bitCount(l0)
                + Long.bitCount(l1)
                + Long.bitCount(l2)
                + Long.bitCount(l3);
    }

    /** 精确转换为 long，超出有符号 64-bit 范围时抛出异常。 */
    public long toLongExact() {
        long sign = l3 >> 63;

        if (l0 != sign || l1 != sign || l2 != sign) {
            throw new ArithmeticException("Int256 out of long range");
        }

        return l3;
    }

    /** 转换为 double，按照 IEEE 754 最近偶数规则舍入。 */
    public double toDouble() {
        boolean negative = l0 < 0L;

        long a0 = l0;
        long a1 = l1;
        long a2 = l2;
        long a3 = l3;

        // 计算无符号绝对值
        if (negative) {
            a3 = -l3;
            a2 = ~l2 + (l3 == 0L ? 1L : 0L);
            a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
            a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        }

        if ((a0 | a1 | a2 | a3) == 0L) {
            return 0.0;
        }

        long top;
        long next;
        long rest;
        int offset;

        if (a0 != 0L) {
            top = a0;
            next = a1;
            rest = a2 | a3;
            offset = 192;
        } else if (a1 != 0L) {
            top = a1;
            next = a2;
            rest = a3;
            offset = 128;
        } else if (a2 != 0L) {
            top = a2;
            next = a3;
            rest = 0L;
            offset = 64;
        } else {
            top = a3;
            next = 0L;
            rest = 0L;
            offset = 0;
        }

        // 将最高有效 bit 对齐至 bit 63
        int leading = Long.numberOfLeadingZeros(top);
        long normalized = top << leading;

        if (leading != 0) {
            normalized |= next >>> (64 - leading);
        }

        // 提取 53-bit 有效数字
        long significand = normalized >>> 11;

        // 最近偶数舍入
        boolean guard = (normalized & (1L << 10)) != 0L;
        boolean sticky = (normalized & 0x3ffL) != 0L
                || (next << leading) != 0L
                || rest != 0L;

        if (guard && (sticky || (significand & 1L) != 0L)) {
            significand++;
        }

        double result = Math.scalb(
                (double) significand, offset + 11 - leading);

        return negative ? -result : result;
    }

}
