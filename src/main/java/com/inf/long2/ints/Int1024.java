package com.inf.long2.ints;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.Int;

/**
 * 位宽为 1024-bit 的 {@link Int} 实现，以十六个 long 保存完整位模式。
 * {@code l0} 为最高组，包含符号位；{@code l15} 为最低组。
 */
public final class Int1024 implements Int<Int1024> {
    public static final Format FORMAT = new Format(1024);
    public static final Int1024 MAX_VALUE = new Int1024(
            Long.MAX_VALUE, -1L, -1L, -1L,
            -1L, -1L, -1L, -1L,
            -1L, -1L, -1L, -1L,
            -1L, -1L, -1L, -1L);
    public static final Int1024 MIN_VALUE = new Int1024(
            Long.MIN_VALUE, 0L, 0L, 0L,
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L);
    public static final Int1024 ZERO = new Int1024(0L);
    public static final Int1024 ONE = new Int1024(1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;
    private final long l5;
    private final long l6;
    private final long l7;
    private final long l8;
    private final long l9;
    private final long l10;
    private final long l11;
    private final long l12;
    private final long l13;
    private final long l14;
    private final long l15;

    private Int1024(long last) {
        this(last >> 63, last >> 63, last >> 63, last >> 63,
                last >> 63, last >> 63, last >> 63, last >> 63,
                last >> 63, last >> 63, last >> 63, last >> 63,
                last >> 63, last >> 63, last >> 63, last);
    }

    private Int1024(long l0, long l1, long l2, long l3,
            long l4, long l5, long l6, long l7,
            long l8, long l9, long l10, long l11,
            long l12, long l13, long l14, long l15) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.l5 = l5;
        this.l6 = l6;
        this.l7 = l7;
        this.l8 = l8;
        this.l9 = l9;
        this.l10 = l10;
        this.l11 = l11;
        this.l12 = l12;
        this.l13 = l13;
        this.l14 = l14;
        this.l15 = l15;
    }

    public static Int1024 of(long last) {
        return new Int1024(last);
    }

    /** 按大端序拼接十六个 long 的原始位模式，l0 包含符号位，各参数的 long 符号不改变其位权。 */
    public static Int1024 of(long l0, long l1, long l2, long l3,
            long l4, long l5, long l6, long l7,
            long l8, long l9, long l10, long l11,
            long l12, long l13, long l14, long l15) {
        return new Int1024(l0, l1, l2, l3,
                l4, l5, l6, l7,
                l8, l9, l10, l11,
                l12, l13, l14, l15);
    }

    /**
     * 按大端序读取恰好十六个 long，不持有输入数组。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不等于 16
     */
    public static Int1024 of(long[] longs) {
        Objects.requireNonNull(longs);
        if (longs.length != 16) {
            throw new IllegalArgumentException("longs must contain exactly 16 elements");
        }
        return new Int1024(longs[0], longs[1], longs[2], longs[3],
                longs[4], longs[5], longs[6], longs[7],
                longs[8], longs[9], longs[10], longs[11],
                longs[12], longs[13], longs[14], longs[15]);
    }

    /**
     * 从恰好 128 字节的大端序完整补码恢复整数，不持有输入数组。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不等于 128
     */
    public static Int1024 of(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 128) {
            throw new IllegalArgumentException("bytes must contain exactly 128 elements");
        }
        return new Int1024(readRawLong(bytes, 0), readRawLong(bytes, 8), readRawLong(bytes, 16), readRawLong(bytes, 24),
                readRawLong(bytes, 32), readRawLong(bytes, 40), readRawLong(bytes, 48), readRawLong(bytes, 56),
                readRawLong(bytes, 64), readRawLong(bytes, 72), readRawLong(bytes, 80), readRawLong(bytes, 88),
                readRawLong(bytes, 96), readRawLong(bytes, 104), readRawLong(bytes, 112), readRawLong(bytes, 120));
    }

    private static long readRawLong(byte[] bytes, int offset) {
        long word = 0;
        for (int index = 0; index < 8; index++) {
            word = (word << 8) | (bytes[offset + index] & 0xffL);
        }
        return word;
    }

    @Override
    public byte[] toBytes() {
        byte[] result = new byte[128];
        for (int group = 0; group < 16; group++) {
            long word = getLong(group);
            for (int index = 0; index < 8; index++) {
                result[group * 8 + index] = (byte) (word >>> (56 - index * 8));
            }
        }
        return result;
    }

    public static Int1024 of(float value) {
        // float 的有限值可由 double 精确表示，扩宽不会改变截断结果。
        return of((double) value);
    }

    public static Int1024 of(double value) {
        long raw = Double.doubleToRawLongBits(value);
        int encoded = (int) ((raw >>> 52) & 0x7ff);
        long fraction = raw & 0x000f_ffff_ffff_ffffL;
        boolean negative = raw < 0;
        if (encoded == 0x7ff && fraction != 0) {
            return ZERO;
        }
        int exponent = encoded - 1023;
        if (exponent >= 1023) {
            return negative ? MIN_VALUE : MAX_VALUE;
        }
        if (exponent < 0) {
            return ZERO;
        }
        long significand = (1L << 52) | fraction;
        Int1024 result;
        if (exponent <= 52) {
            result = of(significand >>> (52 - exponent));
        } else {
            int shift = exponent - 52, group = shift >>> 6, bits = shift & 63;
            long[] words = new long[16];
            words[group] = significand << bits;
            if (bits != 0 && group + 1 < words.length) {
                words[group + 1] = significand >>> (64 - bits);
            }
            result = fromLowWords(words);
        }
        return negative ? result.negate() : result;
    }

    public static Int1024 of(Int<?> value) {
        Objects.requireNonNull(value);
        if (value instanceof Int1024 same) {
            return same;
        }
        int groups = value.format().bitWidth() / 64;
        long sign = value.isNegative() ? -1L : 0L;
        return new Int1024(groups >= 16 ? value.getLong(groups - 16) : sign,
                groups >= 15 ? value.getLong(groups - 15) : sign,
                groups >= 14 ? value.getLong(groups - 14) : sign,
                groups >= 13 ? value.getLong(groups - 13) : sign,
                groups >= 12 ? value.getLong(groups - 12) : sign,
                groups >= 11 ? value.getLong(groups - 11) : sign,
                groups >= 10 ? value.getLong(groups - 10) : sign,
                groups >= 9 ? value.getLong(groups - 9) : sign,
                groups >= 8 ? value.getLong(groups - 8) : sign,
                groups >= 7 ? value.getLong(groups - 7) : sign,
                groups >= 6 ? value.getLong(groups - 6) : sign,
                groups >= 5 ? value.getLong(groups - 5) : sign,
                groups >= 4 ? value.getLong(groups - 4) : sign,
                groups >= 3 ? value.getLong(groups - 3) : sign,
                groups >= 2 ? value.getLong(groups - 2) : sign,
                value.getLong(groups - 1));
    }

    public static Int1024 of(Binary<?> value) {
        Objects.requireNonNull(value);
        if (value.isNaN() || value.isZero()) {
            return ZERO;
        }
        boolean negative = value.signBit();
        if (value.isInfinite()) {
            return negative ? MIN_VALUE : MAX_VALUE;
        }
        // emin 不大于零，次正规数的绝对值严格小于一。
        if (!value.isNormal()) {
            return ZERO;
        }
        int precision = value.format().precision();
        long scale = value.scaleExponent(), exponent = scale + precision - 1L;
        if (exponent < 0) {
            return ZERO;
        }
        if (exponent >= 1023) {
            return negative ? MIN_VALUE : MAX_VALUE;
        }
        int groups = (int) ((precision + 63L) / 64);
        long[] words = new long[16];
        for (int group = 0; group < groups; group++) {
            long wordScale = scale + (groups - 1L - group) * 64;
            if (wordScale <= -64) {
                break;
            }
            long word = value.getSignificandLong(group);
            if (wordScale < 0) {
                words[0] |= word >>> (int) -wordScale;
            } else {
                int index = (int) (wordScale / 64), bits = (int) (wordScale & 63);
                words[index] |= word << bits;
                if (bits != 0 && index + 1 < words.length) {
                    words[index + 1] |= word >>> (64 - bits);
                }
            }
        }
        Int1024 result = fromLowWords(words);
        return negative ? result.negate() : result;
    }

    @Override
    public Format format() {
        return FORMAT;
    }

    @Override
    public int toInt() {
        return (int) l15;
    }

    @Override
    public long toLong() {
        return l15;
    }

    @Override
    public float toFloat() {
        return Float.intBitsToFloat((int) IntWords.primitiveBits(magnitude(), isNegative(), 24, 127, 8));
    }

    @Override
    public double toDouble() {
        return Double.longBitsToDouble(IntWords.primitiveBits(magnitude(), isNegative(), 53, 1023, 11));
    }

    /** 低组在前的独占无符号幅值，最小负数也保留完整的正绝对值位模式。 */
    private long[] magnitude() {
        long[] words = {l15, l14, l13, l12, l11, l10, l9, l8, l7, l6, l5, l4, l3, l2, l1, l0};
        if (isNegative()) {
            IntWords.negateInPlace(words);
        }
        return words;
    }

    private static Int1024 fromLowWords(long[] words) {
        return new Int1024(words[15], words[14], words[13], words[12],
                words[11], words[10], words[9], words[8],
                words[7], words[6], words[5], words[4],
                words[3], words[2], words[1], words[0]);
    }

    @Override
    public String toString() {
        if (isZero()) {
            return "0";
        }
        return IntText.toDecimal(magnitude(), isNegative());
    }

    public static Int1024 parseInt(String value) {
        return fromLowWords(IntText.parseDecimal(value, 16));
    }

    public static boolean isValid(String value) {
        return IntText.isValid(value);
    }

    @Override
    public String toHexString() {
        return "0x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x%016x".formatted(
                l0, l1, l2, l3,
                l4, l5, l6, l7,
                l8, l9, l10, l11,
                l12, l13, l14, l15);
    }

    public static Int1024 parseHexInt(String value) {
        return fromLowWords(IntText.parseHex(value, 16));
    }

    public static boolean isHexValid(String value) {
        return IntText.isHexValid(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Int1024 other
                && l0 == other.l0 && l1 == other.l1 && l2 == other.l2 && l3 == other.l3
                && l4 == other.l4 && l5 == other.l5 && l6 == other.l6 && l7 == other.l7
                && l8 == other.l8 && l9 == other.l9 && l10 == other.l10 && l11 == other.l11
                && l12 == other.l12 && l13 == other.l13 && l14 == other.l14 && l15 == other.l15;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int group = 0; group < 16; group++) {
            hash = 31 * hash + Long.hashCode(getLong(group));
        }
        return hash;
    }

    @Override
    public Int1024 and(Int1024 other) {
        return new Int1024(l0 & other.l0, l1 & other.l1, l2 & other.l2, l3 & other.l3,
                l4 & other.l4, l5 & other.l5, l6 & other.l6, l7 & other.l7,
                l8 & other.l8, l9 & other.l9, l10 & other.l10, l11 & other.l11,
                l12 & other.l12, l13 & other.l13, l14 & other.l14, l15 & other.l15);
    }

    @Override
    public Int1024 or(Int1024 other) {
        return new Int1024(l0 | other.l0, l1 | other.l1, l2 | other.l2, l3 | other.l3,
                l4 | other.l4, l5 | other.l5, l6 | other.l6, l7 | other.l7,
                l8 | other.l8, l9 | other.l9, l10 | other.l10, l11 | other.l11,
                l12 | other.l12, l13 | other.l13, l14 | other.l14, l15 | other.l15);
    }

    @Override
    public Int1024 not() {
        return new Int1024(~l0, ~l1, ~l2, ~l3,
                ~l4, ~l5, ~l6, ~l7,
                ~l8, ~l9, ~l10, ~l11,
                ~l12, ~l13, ~l14, ~l15);
    }

    @Override
    public Int1024 xor(Int1024 other) {
        return new Int1024(l0 ^ other.l0, l1 ^ other.l1, l2 ^ other.l2, l3 ^ other.l3,
                l4 ^ other.l4, l5 ^ other.l5, l6 ^ other.l6, l7 ^ other.l7,
                l8 ^ other.l8, l9 ^ other.l9, l10 ^ other.l10, l11 ^ other.l11,
                l12 ^ other.l12, l13 ^ other.l13, l14 ^ other.l14, l15 ^ other.l15);
    }

    @Override
    public Int1024 shift(int bits) {
        int distance = (int) (Math.abs((long) bits) % 1024);
        if (distance == 0) {
            return this;
        }
        boolean left = bits > 0;
        return new Int1024(shiftedWord(15, distance, left), shiftedWord(14, distance, left),
                shiftedWord(13, distance, left), shiftedWord(12, distance, left),
                shiftedWord(11, distance, left), shiftedWord(10, distance, left),
                shiftedWord(9, distance, left), shiftedWord(8, distance, left),
                shiftedWord(7, distance, left), shiftedWord(6, distance, left),
                shiftedWord(5, distance, left), shiftedWord(4, distance, left),
                shiftedWord(3, distance, left), shiftedWord(2, distance, left),
                shiftedWord(1, distance, left), shiftedWord(0, distance, left));
    }

    /** 低组在前读取位移结果，整组位移单独返回，避免组内移位 64 位被解释为零。 */
    private long shiftedWord(int index, int distance, boolean left) {
        int group = index + (left ? -(distance >>> 6) : distance >>> 6), bits = distance & 63;
        long extension = left ? 0 : l0 >> 63;
        long word = lowWord(group, extension);
        if (bits == 0) {
            return word;
        }
        return left ? (word << bits) | (lowWord(group - 1, extension) >>> (64 - bits))
                : (word >>> bits) | (lowWord(group + 1, extension) << (64 - bits));
    }

    private long lowWord(int index, long extension) {
        return index < 0 ? 0 : index >= 16 ? extension : getLong(15 - index);
    }

    @Override
    public Int1024 add(Int1024 other) {
        long r15 = l15 + other.l15;
        long carry = Long.compareUnsigned(r15, l15) < 0 ? 1L : 0L;
        // 带入进位且结果等于原组时，另一组为全一，也必须向高组进位。
        long r14 = l14 + other.l14 + carry;
        carry = Long.compareUnsigned(r14, l14) < 0 || (carry != 0 && r14 == l14) ? 1L : 0L;
        long r13 = l13 + other.l13 + carry;
        carry = Long.compareUnsigned(r13, l13) < 0 || (carry != 0 && r13 == l13) ? 1L : 0L;
        long r12 = l12 + other.l12 + carry;
        carry = Long.compareUnsigned(r12, l12) < 0 || (carry != 0 && r12 == l12) ? 1L : 0L;
        long r11 = l11 + other.l11 + carry;
        carry = Long.compareUnsigned(r11, l11) < 0 || (carry != 0 && r11 == l11) ? 1L : 0L;
        long r10 = l10 + other.l10 + carry;
        carry = Long.compareUnsigned(r10, l10) < 0 || (carry != 0 && r10 == l10) ? 1L : 0L;
        long r9 = l9 + other.l9 + carry;
        carry = Long.compareUnsigned(r9, l9) < 0 || (carry != 0 && r9 == l9) ? 1L : 0L;
        long r8 = l8 + other.l8 + carry;
        carry = Long.compareUnsigned(r8, l8) < 0 || (carry != 0 && r8 == l8) ? 1L : 0L;
        long r7 = l7 + other.l7 + carry;
        carry = Long.compareUnsigned(r7, l7) < 0 || (carry != 0 && r7 == l7) ? 1L : 0L;
        long r6 = l6 + other.l6 + carry;
        carry = Long.compareUnsigned(r6, l6) < 0 || (carry != 0 && r6 == l6) ? 1L : 0L;
        long r5 = l5 + other.l5 + carry;
        carry = Long.compareUnsigned(r5, l5) < 0 || (carry != 0 && r5 == l5) ? 1L : 0L;
        long r4 = l4 + other.l4 + carry;
        carry = Long.compareUnsigned(r4, l4) < 0 || (carry != 0 && r4 == l4) ? 1L : 0L;
        long r3 = l3 + other.l3 + carry;
        carry = Long.compareUnsigned(r3, l3) < 0 || (carry != 0 && r3 == l3) ? 1L : 0L;
        long r2 = l2 + other.l2 + carry;
        carry = Long.compareUnsigned(r2, l2) < 0 || (carry != 0 && r2 == l2) ? 1L : 0L;
        long r1 = l1 + other.l1 + carry;
        carry = Long.compareUnsigned(r1, l1) < 0 || (carry != 0 && r1 == l1) ? 1L : 0L;
        long r0 = l0 + other.l0 + carry;
        return new Int1024(r0, r1, r2, r3,
                r4, r5, r6, r7,
                r8, r9, r10, r11,
                r12, r13, r14, r15);
    }

    @Override
    public Int1024 subtract(Int1024 other) {
        long r15 = l15 - other.l15;
        long borrow = Long.compareUnsigned(l15, other.l15) < 0 ? 1L : 0L;
        // 相等的两组带入借位时，必须继续向高组借位。
        long r14 = l14 - other.l14 - borrow;
        borrow = Long.compareUnsigned(l14, other.l14) < 0 || (borrow != 0 && l14 == other.l14) ? 1L : 0L;
        long r13 = l13 - other.l13 - borrow;
        borrow = Long.compareUnsigned(l13, other.l13) < 0 || (borrow != 0 && l13 == other.l13) ? 1L : 0L;
        long r12 = l12 - other.l12 - borrow;
        borrow = Long.compareUnsigned(l12, other.l12) < 0 || (borrow != 0 && l12 == other.l12) ? 1L : 0L;
        long r11 = l11 - other.l11 - borrow;
        borrow = Long.compareUnsigned(l11, other.l11) < 0 || (borrow != 0 && l11 == other.l11) ? 1L : 0L;
        long r10 = l10 - other.l10 - borrow;
        borrow = Long.compareUnsigned(l10, other.l10) < 0 || (borrow != 0 && l10 == other.l10) ? 1L : 0L;
        long r9 = l9 - other.l9 - borrow;
        borrow = Long.compareUnsigned(l9, other.l9) < 0 || (borrow != 0 && l9 == other.l9) ? 1L : 0L;
        long r8 = l8 - other.l8 - borrow;
        borrow = Long.compareUnsigned(l8, other.l8) < 0 || (borrow != 0 && l8 == other.l8) ? 1L : 0L;
        long r7 = l7 - other.l7 - borrow;
        borrow = Long.compareUnsigned(l7, other.l7) < 0 || (borrow != 0 && l7 == other.l7) ? 1L : 0L;
        long r6 = l6 - other.l6 - borrow;
        borrow = Long.compareUnsigned(l6, other.l6) < 0 || (borrow != 0 && l6 == other.l6) ? 1L : 0L;
        long r5 = l5 - other.l5 - borrow;
        borrow = Long.compareUnsigned(l5, other.l5) < 0 || (borrow != 0 && l5 == other.l5) ? 1L : 0L;
        long r4 = l4 - other.l4 - borrow;
        borrow = Long.compareUnsigned(l4, other.l4) < 0 || (borrow != 0 && l4 == other.l4) ? 1L : 0L;
        long r3 = l3 - other.l3 - borrow;
        borrow = Long.compareUnsigned(l3, other.l3) < 0 || (borrow != 0 && l3 == other.l3) ? 1L : 0L;
        long r2 = l2 - other.l2 - borrow;
        borrow = Long.compareUnsigned(l2, other.l2) < 0 || (borrow != 0 && l2 == other.l2) ? 1L : 0L;
        long r1 = l1 - other.l1 - borrow;
        borrow = Long.compareUnsigned(l1, other.l1) < 0 || (borrow != 0 && l1 == other.l1) ? 1L : 0L;
        long r0 = l0 - other.l0 - borrow;
        return new Int1024(r0, r1, r2, r3,
                r4, r5, r6, r7,
                r8, r9, r10, r11,
                r12, r13, r14, r15);
    }

    @Override
    public Int1024 negate() {
        return ZERO.subtract(this);
    }

    @Override
    public Int1024 successor() {
        return add(ONE);
    }

    @Override
    public Int1024 multiply(Int1024 other) {
        Objects.requireNonNull(other);
        // 各列先独立累加部分积，最后加入输入进位并归一化。
        // 下一列进位包含整列溢出次数与归一化的额外进位。
        // 两段固定列内核共用一个低组在前的独占结果数组，无临时整数对象。
        long[] result = new long[16];
        long carry = multiplyLowColumns(other, result);
        multiplyHighColumns(other, result, carry);
        return new Int1024(result[15], result[14], result[13], result[12],
                result[11], result[10], result[9], result[8],
                result[7], result[6], result[5], result[4],
                result[3], result[2], result[1], result[0]);
    }

    /**
     * 完整覆盖 result[0..7]，返回第 8 列的输入进位 C8（0 至 14）。
     * 列和包含低半积、前一对角线的高半积及 Ck；
     * 每次无符号溢出给 C(k+1) 加一，其上界为 2k，不能视为单 bit。
     */
    private long multiplyLowColumns(Int1024 other, long[] result) {
        long carry = 0;
        long t;

        {
            long u0 = l15 * other.l15;
            result[0] = u0;
        }
        {
            long u1 = Math.unsignedMultiplyHigh(l15, other.l15);
            long q1 = 0;
            t = u1 + l15 * other.l14;
            q1 += Long.compareUnsigned(t, u1) < 0 ? 1L : 0L;
            u1 = t;
            t = u1 + l14 * other.l15;
            q1 += Long.compareUnsigned(t, u1) < 0 ? 1L : 0L;
            u1 = t;
            result[1] = u1;
            carry = q1;
        }
        {
            long u2 = Math.unsignedMultiplyHigh(l15, other.l14);
            long q2 = 0;
            t = u2 + Math.unsignedMultiplyHigh(l14, other.l15);
            q2 += Long.compareUnsigned(t, u2) < 0 ? 1L : 0L;
            u2 = t;
            t = u2 + l15 * other.l13;
            q2 += Long.compareUnsigned(t, u2) < 0 ? 1L : 0L;
            u2 = t;
            t = u2 + l14 * other.l14;
            q2 += Long.compareUnsigned(t, u2) < 0 ? 1L : 0L;
            u2 = t;
            t = u2 + l13 * other.l15;
            q2 += Long.compareUnsigned(t, u2) < 0 ? 1L : 0L;
            u2 = t;
            t = u2 + carry;
            carry = q2 + (Long.compareUnsigned(t, u2) < 0 ? 1L : 0L);
            result[2] = t;
        }
        {
            long u3 = Math.unsignedMultiplyHigh(l15, other.l13);
            long q3 = 0;
            t = u3 + Math.unsignedMultiplyHigh(l14, other.l14);
            q3 += Long.compareUnsigned(t, u3) < 0 ? 1L : 0L;
            u3 = t;
            t = u3 + Math.unsignedMultiplyHigh(l13, other.l15);
            q3 += Long.compareUnsigned(t, u3) < 0 ? 1L : 0L;
            u3 = t;
            t = u3 + l15 * other.l12;
            q3 += Long.compareUnsigned(t, u3) < 0 ? 1L : 0L;
            u3 = t;
            t = u3 + l14 * other.l13;
            q3 += Long.compareUnsigned(t, u3) < 0 ? 1L : 0L;
            u3 = t;
            t = u3 + l13 * other.l14;
            q3 += Long.compareUnsigned(t, u3) < 0 ? 1L : 0L;
            u3 = t;
            t = u3 + l12 * other.l15;
            q3 += Long.compareUnsigned(t, u3) < 0 ? 1L : 0L;
            u3 = t;
            t = u3 + carry;
            carry = q3 + (Long.compareUnsigned(t, u3) < 0 ? 1L : 0L);
            result[3] = t;
        }
        {
            long u4 = Math.unsignedMultiplyHigh(l15, other.l12);
            long q4 = 0;
            t = u4 + Math.unsignedMultiplyHigh(l14, other.l13);
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + Math.unsignedMultiplyHigh(l13, other.l14);
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + Math.unsignedMultiplyHigh(l12, other.l15);
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + l15 * other.l11;
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + l14 * other.l12;
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + l13 * other.l13;
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + l12 * other.l14;
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + l11 * other.l15;
            q4 += Long.compareUnsigned(t, u4) < 0 ? 1L : 0L;
            u4 = t;
            t = u4 + carry;
            carry = q4 + (Long.compareUnsigned(t, u4) < 0 ? 1L : 0L);
            result[4] = t;
        }
        {
            long u5 = Math.unsignedMultiplyHigh(l15, other.l11);
            long q5 = 0;
            t = u5 + Math.unsignedMultiplyHigh(l14, other.l12);
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + Math.unsignedMultiplyHigh(l13, other.l13);
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + Math.unsignedMultiplyHigh(l12, other.l14);
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + Math.unsignedMultiplyHigh(l11, other.l15);
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + l15 * other.l10;
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + l14 * other.l11;
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + l13 * other.l12;
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + l12 * other.l13;
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + l11 * other.l14;
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + l10 * other.l15;
            q5 += Long.compareUnsigned(t, u5) < 0 ? 1L : 0L;
            u5 = t;
            t = u5 + carry;
            carry = q5 + (Long.compareUnsigned(t, u5) < 0 ? 1L : 0L);
            result[5] = t;
        }
        {
            long u6 = Math.unsignedMultiplyHigh(l15, other.l10);
            long q6 = 0;
            t = u6 + Math.unsignedMultiplyHigh(l14, other.l11);
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + Math.unsignedMultiplyHigh(l13, other.l12);
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + Math.unsignedMultiplyHigh(l12, other.l13);
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + Math.unsignedMultiplyHigh(l11, other.l14);
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + Math.unsignedMultiplyHigh(l10, other.l15);
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l15 * other.l9;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l14 * other.l10;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l13 * other.l11;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l12 * other.l12;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l11 * other.l13;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l10 * other.l14;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + l9 * other.l15;
            q6 += Long.compareUnsigned(t, u6) < 0 ? 1L : 0L;
            u6 = t;
            t = u6 + carry;
            carry = q6 + (Long.compareUnsigned(t, u6) < 0 ? 1L : 0L);
            result[6] = t;
        }
        {
            long u7 = Math.unsignedMultiplyHigh(l15, other.l9);
            long q7 = 0;
            t = u7 + Math.unsignedMultiplyHigh(l14, other.l10);
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + Math.unsignedMultiplyHigh(l13, other.l11);
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + Math.unsignedMultiplyHigh(l12, other.l12);
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + Math.unsignedMultiplyHigh(l11, other.l13);
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + Math.unsignedMultiplyHigh(l10, other.l14);
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + Math.unsignedMultiplyHigh(l9, other.l15);
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l15 * other.l8;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l14 * other.l9;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l13 * other.l10;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l12 * other.l11;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l11 * other.l12;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l10 * other.l13;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l9 * other.l14;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + l8 * other.l15;
            q7 += Long.compareUnsigned(t, u7) < 0 ? 1L : 0L;
            u7 = t;
            t = u7 + carry;
            carry = q7 + (Long.compareUnsigned(t, u7) < 0 ? 1L : 0L);
            result[7] = t;
        }
        return carry;
    }

    /** 完整覆盖 result[8..15]；C15 不超过 28，最高保留列的向外进位丢弃。 */
    private void multiplyHighColumns(Int1024 other, long[] result, long carry) {
        long t;

        {
            long u8 = Math.unsignedMultiplyHigh(l15, other.l8);
            long q8 = 0;
            t = u8 + Math.unsignedMultiplyHigh(l14, other.l9);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + Math.unsignedMultiplyHigh(l13, other.l10);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + Math.unsignedMultiplyHigh(l12, other.l11);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + Math.unsignedMultiplyHigh(l11, other.l12);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + Math.unsignedMultiplyHigh(l10, other.l13);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + Math.unsignedMultiplyHigh(l9, other.l14);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + Math.unsignedMultiplyHigh(l8, other.l15);
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l15 * other.l7;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l14 * other.l8;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l13 * other.l9;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l12 * other.l10;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l11 * other.l11;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l10 * other.l12;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l9 * other.l13;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l8 * other.l14;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + l7 * other.l15;
            q8 += Long.compareUnsigned(t, u8) < 0 ? 1L : 0L;
            u8 = t;
            t = u8 + carry;
            carry = q8 + (Long.compareUnsigned(t, u8) < 0 ? 1L : 0L);
            result[8] = t;
        }
        {
            long u9 = Math.unsignedMultiplyHigh(l15, other.l7);
            long q9 = 0;
            t = u9 + Math.unsignedMultiplyHigh(l14, other.l8);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l13, other.l9);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l12, other.l10);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l11, other.l11);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l10, other.l12);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l9, other.l13);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l8, other.l14);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + Math.unsignedMultiplyHigh(l7, other.l15);
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l15 * other.l6;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l14 * other.l7;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l13 * other.l8;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l12 * other.l9;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l11 * other.l10;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l10 * other.l11;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l9 * other.l12;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l8 * other.l13;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l7 * other.l14;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + l6 * other.l15;
            q9 += Long.compareUnsigned(t, u9) < 0 ? 1L : 0L;
            u9 = t;
            t = u9 + carry;
            carry = q9 + (Long.compareUnsigned(t, u9) < 0 ? 1L : 0L);
            result[9] = t;
        }
        {
            long u10 = Math.unsignedMultiplyHigh(l15, other.l6);
            long q10 = 0;
            t = u10 + Math.unsignedMultiplyHigh(l14, other.l7);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l13, other.l8);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l12, other.l9);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l11, other.l10);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l10, other.l11);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l9, other.l12);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l8, other.l13);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l7, other.l14);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + Math.unsignedMultiplyHigh(l6, other.l15);
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l15 * other.l5;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l14 * other.l6;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l13 * other.l7;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l12 * other.l8;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l11 * other.l9;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l10 * other.l10;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l9 * other.l11;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l8 * other.l12;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l7 * other.l13;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l6 * other.l14;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + l5 * other.l15;
            q10 += Long.compareUnsigned(t, u10) < 0 ? 1L : 0L;
            u10 = t;
            t = u10 + carry;
            carry = q10 + (Long.compareUnsigned(t, u10) < 0 ? 1L : 0L);
            result[10] = t;
        }
        {
            long u11 = Math.unsignedMultiplyHigh(l15, other.l5);
            long q11 = 0;
            t = u11 + Math.unsignedMultiplyHigh(l14, other.l6);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l13, other.l7);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l12, other.l8);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l11, other.l9);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l10, other.l10);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l9, other.l11);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l8, other.l12);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l7, other.l13);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l6, other.l14);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + Math.unsignedMultiplyHigh(l5, other.l15);
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l15 * other.l4;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l14 * other.l5;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l13 * other.l6;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l12 * other.l7;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l11 * other.l8;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l10 * other.l9;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l9 * other.l10;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l8 * other.l11;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l7 * other.l12;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l6 * other.l13;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l5 * other.l14;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + l4 * other.l15;
            q11 += Long.compareUnsigned(t, u11) < 0 ? 1L : 0L;
            u11 = t;
            t = u11 + carry;
            carry = q11 + (Long.compareUnsigned(t, u11) < 0 ? 1L : 0L);
            result[11] = t;
        }
        {
            long u12 = Math.unsignedMultiplyHigh(l15, other.l4);
            long q12 = 0;
            t = u12 + Math.unsignedMultiplyHigh(l14, other.l5);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l13, other.l6);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l12, other.l7);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l11, other.l8);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l10, other.l9);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l9, other.l10);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l8, other.l11);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l7, other.l12);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l6, other.l13);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l5, other.l14);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + Math.unsignedMultiplyHigh(l4, other.l15);
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l15 * other.l3;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l14 * other.l4;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l13 * other.l5;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l12 * other.l6;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l11 * other.l7;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l10 * other.l8;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l9 * other.l9;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l8 * other.l10;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l7 * other.l11;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l6 * other.l12;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l5 * other.l13;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l4 * other.l14;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + l3 * other.l15;
            q12 += Long.compareUnsigned(t, u12) < 0 ? 1L : 0L;
            u12 = t;
            t = u12 + carry;
            carry = q12 + (Long.compareUnsigned(t, u12) < 0 ? 1L : 0L);
            result[12] = t;
        }
        {
            long u13 = Math.unsignedMultiplyHigh(l15, other.l3);
            long q13 = 0;
            t = u13 + Math.unsignedMultiplyHigh(l14, other.l4);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l13, other.l5);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l12, other.l6);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l11, other.l7);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l10, other.l8);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l9, other.l9);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l8, other.l10);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l7, other.l11);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l6, other.l12);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l5, other.l13);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l4, other.l14);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + Math.unsignedMultiplyHigh(l3, other.l15);
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l15 * other.l2;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l14 * other.l3;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l13 * other.l4;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l12 * other.l5;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l11 * other.l6;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l10 * other.l7;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l9 * other.l8;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l8 * other.l9;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l7 * other.l10;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l6 * other.l11;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l5 * other.l12;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l4 * other.l13;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l3 * other.l14;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + l2 * other.l15;
            q13 += Long.compareUnsigned(t, u13) < 0 ? 1L : 0L;
            u13 = t;
            t = u13 + carry;
            carry = q13 + (Long.compareUnsigned(t, u13) < 0 ? 1L : 0L);
            result[13] = t;
        }
        {
            long u14 = Math.unsignedMultiplyHigh(l15, other.l2);
            long q14 = 0;
            t = u14 + Math.unsignedMultiplyHigh(l14, other.l3);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l13, other.l4);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l12, other.l5);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l11, other.l6);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l10, other.l7);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l9, other.l8);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l8, other.l9);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l7, other.l10);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l6, other.l11);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l5, other.l12);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l4, other.l13);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l3, other.l14);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + Math.unsignedMultiplyHigh(l2, other.l15);
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l15 * other.l1;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l14 * other.l2;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l13 * other.l3;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l12 * other.l4;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l11 * other.l5;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l10 * other.l6;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l9 * other.l7;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l8 * other.l8;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l7 * other.l9;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l6 * other.l10;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l5 * other.l11;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l4 * other.l12;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l3 * other.l13;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l2 * other.l14;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + l1 * other.l15;
            q14 += Long.compareUnsigned(t, u14) < 0 ? 1L : 0L;
            u14 = t;
            t = u14 + carry;
            carry = q14 + (Long.compareUnsigned(t, u14) < 0 ? 1L : 0L);
            result[14] = t;
        }
        {
            long u15 = Math.unsignedMultiplyHigh(l15, other.l1);
            t = u15 + Math.unsignedMultiplyHigh(l14, other.l2);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l13, other.l3);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l12, other.l4);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l11, other.l5);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l10, other.l6);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l9, other.l7);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l8, other.l8);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l7, other.l9);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l6, other.l10);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l5, other.l11);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l4, other.l12);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l3, other.l13);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l2, other.l14);
            u15 = t;
            t = u15 + Math.unsignedMultiplyHigh(l1, other.l15);
            u15 = t;
            t = u15 + l15 * other.l0;
            u15 = t;
            t = u15 + l14 * other.l1;
            u15 = t;
            t = u15 + l13 * other.l2;
            u15 = t;
            t = u15 + l12 * other.l3;
            u15 = t;
            t = u15 + l11 * other.l4;
            u15 = t;
            t = u15 + l10 * other.l5;
            u15 = t;
            t = u15 + l9 * other.l6;
            u15 = t;
            t = u15 + l8 * other.l7;
            u15 = t;
            t = u15 + l7 * other.l8;
            u15 = t;
            t = u15 + l6 * other.l9;
            u15 = t;
            t = u15 + l5 * other.l10;
            u15 = t;
            t = u15 + l4 * other.l11;
            u15 = t;
            t = u15 + l3 * other.l12;
            u15 = t;
            t = u15 + l2 * other.l13;
            u15 = t;
            t = u15 + l1 * other.l14;
            u15 = t;
            t = u15 + l0 * other.l15;
            u15 = t;
            t = u15 + carry;
            result[15] = t;
        }
    }

    @Override
    public int compareTo(Int1024 other) {
        int comparison = Long.compare(l0, other.l0);
        for (int group = 1; comparison == 0 && group < 16; group++) {
            comparison = Long.compareUnsigned(getLong(group), other.getLong(group));
        }
        return comparison;
    }

    @Override
    public int signum() {
        return isNegative() ? -1 : isZero() ? 0 : 1;
    }

    @Override
    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9 | l10 | l11 | l12 | l13 | l14 | l15) == 0;
    }

    @Override
    public boolean isNegative() {
        return l0 < 0;
    }

    @Override
    public long getLong(int index) {
        return switch (index) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            case 4 -> l4;
            case 5 -> l5;
            case 6 -> l6;
            case 7 -> l7;
            case 8 -> l8;
            case 9 -> l9;
            case 10 -> l10;
            case 11 -> l11;
            case 12 -> l12;
            case 13 -> l13;
            case 14 -> l14;
            case 15 -> l15;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public boolean testBit(int index) {
        if (index < 0 || index >= 1024) {
            throw new IndexOutOfBoundsException(index);
        }
        return (getLong(15 - (index >>> 6)) & (1L << (index & 63))) != 0;
    }

    @Override
    public int bitLength() {
        long mask = l0 >> 63;
        for (int group = 0; group < 16; group++) {
            long word = getLong(group) ^ mask;
            if (word != 0) {
                return (16 - group) * 64 - Long.numberOfLeadingZeros(word);
            }
        }
        return 0;
    }

    @Override
    public int bitCount() {
        return Long.bitCount(l0) + Long.bitCount(l1) + Long.bitCount(l2) + Long.bitCount(l3)
                + Long.bitCount(l4) + Long.bitCount(l5) + Long.bitCount(l6) + Long.bitCount(l7)
                + Long.bitCount(l8) + Long.bitCount(l9) + Long.bitCount(l10) + Long.bitCount(l11)
                + Long.bitCount(l12) + Long.bitCount(l13) + Long.bitCount(l14) + Long.bitCount(l15);
    }
}
