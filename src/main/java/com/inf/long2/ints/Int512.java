package com.inf.long2.ints;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.Int;

/**
 * 位宽为 512-bit 的 {@link Int} 实现，以八个 long 保存完整位模式。
 * {@code l0} 为最高组，包含符号位；{@code l7} 为最低组。
 */
public final class Int512 implements Int<Int512> {
    public static final Format FORMAT = new Format(512);
    public static final Int512 MAX_VALUE = new Int512(
            Long.MAX_VALUE, -1L, -1L, -1L, -1L, -1L, -1L, -1L);
    public static final Int512 MIN_VALUE = new Int512(
            Long.MIN_VALUE, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    public static final Int512 ZERO = new Int512(0L);
    public static final Int512 ONE = new Int512(1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;
    private final long l5;
    private final long l6;
    private final long l7;

    private Int512(long last) {
        this(last >> 63, last >> 63, last >> 63, last >> 63,
                last >> 63, last >> 63, last >> 63, last);
    }

    private Int512(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.l5 = l5;
        this.l6 = l6;
        this.l7 = l7;
    }

    public static Int512 of(long last) {
        return new Int512(last);
    }

    /** 按大端序拼接八个 long 的原始位模式，l0 包含符号位，各参数的 long 符号不改变其位权。 */
    public static Int512 of(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7) {
        return new Int512(l0, l1, l2, l3, l4, l5, l6, l7);
    }

    /**
     * 按大端序读取恰好八个 long，不持有输入数组。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不等于 8
     */
    public static Int512 of(long[] longs) {
        Objects.requireNonNull(longs);
        if (longs.length != 8) {
            throw new IllegalArgumentException("longs must contain exactly 8 elements");
        }
        return new Int512(longs[0], longs[1], longs[2], longs[3], longs[4], longs[5], longs[6], longs[7]);
    }

    /**
     * 从恰好 64 字节的大端序完整补码恢复整数，不持有输入数组。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不等于 64
     */
    public static Int512 of(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 64) {
            throw new IllegalArgumentException("bytes must contain exactly 64 elements");
        }
        return new Int512(readRawLong(bytes, 0), readRawLong(bytes, 8),
                readRawLong(bytes, 16), readRawLong(bytes, 24), readRawLong(bytes, 32),
                readRawLong(bytes, 40), readRawLong(bytes, 48), readRawLong(bytes, 56));
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
        byte[] result = new byte[64];
        for (int group = 0; group < 8; group++) {
            long word = getLong(group);
            for (int index = 0; index < 8; index++) {
                result[group * 8 + index] = (byte) (word >>> (56 - index * 8));
            }
        }
        return result;
    }

    public static Int512 of(float value) {
        // float 的有限值可由 double 精确表示，扩宽不会改变截断结果。
        return of((double) value);
    }

    public static Int512 of(double value) {
        long raw = Double.doubleToRawLongBits(value);
        int encoded = (int) ((raw >>> 52) & 0x7ff);
        long fraction = raw & 0x000f_ffff_ffff_ffffL;
        boolean negative = raw < 0;
        if (encoded == 0x7ff && fraction != 0) {
            return ZERO;
        }
        int exponent = encoded - 1023;
        if (exponent >= 511) {
            return negative ? MIN_VALUE : MAX_VALUE;
        }
        if (exponent < 0) {
            return ZERO;
        }
        long significand = (1L << 52) | fraction;
        Int512 result;
        if (exponent <= 52) {
            result = of(significand >>> (52 - exponent));
        } else {
            int shift = exponent - 52, group = shift >>> 6, bits = shift & 63;
            long[] words = new long[8];
            words[group] = significand << bits;
            if (bits != 0 && group + 1 < words.length) {
                words[group + 1] = significand >>> (64 - bits);
            }
            result = fromLowWords(words);
        }
        return negative ? result.negate() : result;
    }

    public static Int512 of(Int<?> value) {
        Objects.requireNonNull(value);
        if (value instanceof Int512 same) {
            return same;
        }
        int groups = value.format().bitWidth() / 64;
        long sign = value.isNegative() ? -1L : 0L;
        return new Int512(groups >= 8 ? value.getLong(groups - 8) : sign,
                groups >= 7 ? value.getLong(groups - 7) : sign,
                groups >= 6 ? value.getLong(groups - 6) : sign,
                groups >= 5 ? value.getLong(groups - 5) : sign,
                groups >= 4 ? value.getLong(groups - 4) : sign,
                groups >= 3 ? value.getLong(groups - 3) : sign,
                groups >= 2 ? value.getLong(groups - 2) : sign, value.getLong(groups - 1));
    }

    public static Int512 of(Binary<?> value) {
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
        if (exponent >= 511) {
            return negative ? MIN_VALUE : MAX_VALUE;
        }
        int groups = (int) ((precision + 63L) / 64);
        long[] words = new long[8];
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
        Int512 result = fromLowWords(words);
        return negative ? result.negate() : result;
    }

    @Override
    public Format format() {
        return FORMAT;
    }

    @Override
    public int toInt() {
        return (int) l7;
    }

    @Override
    public long toLong() {
        return l7;
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
        long[] words = {l7, l6, l5, l4, l3, l2, l1, l0};
        if (isNegative()) {
            IntWords.negateInPlace(words);
        }
        return words;
    }

    private static Int512 fromLowWords(long[] words) {
        return new Int512(words[7], words[6], words[5], words[4], words[3], words[2], words[1], words[0]);
    }

    @Override
    public String toString() {
        if (isZero()) {
            return "0";
        }
        return IntText.toDecimal(magnitude(), isNegative());
    }

    public static Int512 parseInt(String value) {
        return fromLowWords(IntText.parseDecimal(value, 8));
    }

    public static boolean isValid(String value) {
        return IntText.isValid(value);
    }

    @Override
    public String toHexString() {
        return "0x%016x%016x%016x%016x%016x%016x%016x%016x".formatted(l0, l1, l2, l3, l4, l5, l6, l7);
    }

    public static Int512 parseHexInt(String value) {
        return fromLowWords(IntText.parseHex(value, 8));
    }

    public static boolean isHexValid(String value) {
        return IntText.isHexValid(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Int512 other && l0 == other.l0 && l1 == other.l1
                && l2 == other.l2 && l3 == other.l3 && l4 == other.l4 && l5 == other.l5
                && l6 == other.l6 && l7 == other.l7;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int group = 0; group < 8; group++) {
            hash = 31 * hash + Long.hashCode(getLong(group));
        }
        return hash;
    }

    @Override
    public Int512 and(Int512 other) {
        return new Int512(l0 & other.l0, l1 & other.l1, l2 & other.l2, l3 & other.l3,
                l4 & other.l4, l5 & other.l5, l6 & other.l6, l7 & other.l7);
    }

    @Override
    public Int512 or(Int512 other) {
        return new Int512(l0 | other.l0, l1 | other.l1, l2 | other.l2, l3 | other.l3,
                l4 | other.l4, l5 | other.l5, l6 | other.l6, l7 | other.l7);
    }

    @Override
    public Int512 not() {
        return new Int512(~l0, ~l1, ~l2, ~l3, ~l4, ~l5, ~l6, ~l7);
    }

    @Override
    public Int512 xor(Int512 other) {
        return new Int512(l0 ^ other.l0, l1 ^ other.l1, l2 ^ other.l2, l3 ^ other.l3,
                l4 ^ other.l4, l5 ^ other.l5, l6 ^ other.l6, l7 ^ other.l7);
    }

    @Override
    public Int512 shift(int bits) {
        int distance = (int) (Math.abs((long) bits) % 512);
        if (distance == 0) {
            return this;
        }
        boolean left = bits > 0;
        return new Int512(shiftedWord(7, distance, left), shiftedWord(6, distance, left),
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
        return index < 0 ? 0 : index >= 8 ? extension : getLong(7 - index);
    }

    @Override
    public Int512 add(Int512 other) {
        long r7 = l7 + other.l7;
        long carry = Long.compareUnsigned(r7, l7) < 0 ? 1L : 0L;
        long r6 = l6 + other.l6 + carry;
        // 带入进位且结果等于原组时，另一组为全一，也必须向高组进位。
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
        return new Int512(r0, r1, r2, r3, r4, r5, r6, r7);
    }

    @Override
    public Int512 subtract(Int512 other) {
        long r7 = l7 - other.l7;
        long borrow = Long.compareUnsigned(l7, other.l7) < 0 ? 1L : 0L;
        long r6 = l6 - other.l6 - borrow;
        // 相等的两组带入借位时，必须继续向高组借位。
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
        return new Int512(r0, r1, r2, r3, r4, r5, r6, r7);
    }

    @Override
    public Int512 negate() {
        return ZERO.subtract(this);
    }

    @Override
    public Int512 successor() {
        return add(ONE);
    }

    @Override
    public Int512 multiply(Int512 other) {
        Objects.requireNonNull(other);
        // 固定列展开由 debug/multiplication-analysis/generate_int_multiplication.py 生成。
        // 按原始补码累加低 512 位；列进位为溢出次数，可能大于一。
        long r7 = l7 * other.l7;
        int carry = 0;
        long t;

        // 第 1 列，位权为 2^64。
        long r6 = Math.unsignedMultiplyHigh(l7, other.l7);
        t = r6 + l7 * other.l6;
        if (Long.compareUnsigned(t, r6) < 0)
            carry++;
        r6 = t;
        t = r6 + l6 * other.l7;
        if (Long.compareUnsigned(t, r6) < 0)
            carry++;
        r6 = t;

        // 第 2 列，位权为 2^128。
        long r5 = carry;
        carry = 0;
        t = r5 + Math.unsignedMultiplyHigh(l7, other.l6);
        if (Long.compareUnsigned(t, r5) < 0)
            carry++;
        r5 = t;
        t = r5 + Math.unsignedMultiplyHigh(l6, other.l7);
        if (Long.compareUnsigned(t, r5) < 0)
            carry++;
        r5 = t;
        t = r5 + l7 * other.l5;
        if (Long.compareUnsigned(t, r5) < 0)
            carry++;
        r5 = t;
        t = r5 + l6 * other.l6;
        if (Long.compareUnsigned(t, r5) < 0)
            carry++;
        r5 = t;
        t = r5 + l5 * other.l7;
        if (Long.compareUnsigned(t, r5) < 0)
            carry++;
        r5 = t;

        // 第 3 列，位权为 2^192。
        long r4 = carry;
        carry = 0;
        t = r4 + Math.unsignedMultiplyHigh(l7, other.l5);
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;
        t = r4 + Math.unsignedMultiplyHigh(l6, other.l6);
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;
        t = r4 + Math.unsignedMultiplyHigh(l5, other.l7);
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;
        t = r4 + l7 * other.l4;
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;
        t = r4 + l6 * other.l5;
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;
        t = r4 + l5 * other.l6;
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;
        t = r4 + l4 * other.l7;
        if (Long.compareUnsigned(t, r4) < 0)
            carry++;
        r4 = t;

        // 第 4 列，位权为 2^256。
        long r3 = carry;
        carry = 0;
        t = r3 + Math.unsignedMultiplyHigh(l7, other.l4);
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + Math.unsignedMultiplyHigh(l6, other.l5);
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + Math.unsignedMultiplyHigh(l5, other.l6);
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + Math.unsignedMultiplyHigh(l4, other.l7);
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + l7 * other.l3;
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + l6 * other.l4;
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + l5 * other.l5;
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + l4 * other.l6;
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;
        t = r3 + l3 * other.l7;
        if (Long.compareUnsigned(t, r3) < 0)
            carry++;
        r3 = t;

        // 第 5 列，位权为 2^320。
        long r2 = carry;
        carry = 0;
        t = r2 + Math.unsignedMultiplyHigh(l7, other.l3);
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + Math.unsignedMultiplyHigh(l6, other.l4);
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + Math.unsignedMultiplyHigh(l5, other.l5);
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + Math.unsignedMultiplyHigh(l4, other.l6);
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + Math.unsignedMultiplyHigh(l3, other.l7);
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + l7 * other.l2;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + l6 * other.l3;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + l5 * other.l4;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + l4 * other.l5;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + l3 * other.l6;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;
        t = r2 + l2 * other.l7;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;

        // 第 6 列，位权为 2^384。
        long r1 = carry;
        carry = 0;
        t = r1 + Math.unsignedMultiplyHigh(l7, other.l2);
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + Math.unsignedMultiplyHigh(l6, other.l3);
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + Math.unsignedMultiplyHigh(l5, other.l4);
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + Math.unsignedMultiplyHigh(l4, other.l5);
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + Math.unsignedMultiplyHigh(l3, other.l6);
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + Math.unsignedMultiplyHigh(l2, other.l7);
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l7 * other.l1;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l6 * other.l2;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l5 * other.l3;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l4 * other.l4;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l3 * other.l5;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l2 * other.l6;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;
        t = r1 + l1 * other.l7;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        // 第 7 列，位权为 2^448。
        // 最高保留列只需回绕加法，向外进位不影响结果。
        long r0 = carry
                + Math.unsignedMultiplyHigh(l7, other.l1)
                + Math.unsignedMultiplyHigh(l6, other.l2)
                + Math.unsignedMultiplyHigh(l5, other.l3)
                + Math.unsignedMultiplyHigh(l4, other.l4)
                + Math.unsignedMultiplyHigh(l3, other.l5)
                + Math.unsignedMultiplyHigh(l2, other.l6)
                + Math.unsignedMultiplyHigh(l1, other.l7)
                + l7 * other.l0
                + l6 * other.l1
                + l5 * other.l2
                + l4 * other.l3
                + l3 * other.l4
                + l2 * other.l5
                + l1 * other.l6
                + l0 * other.l7;

        return new Int512(r0, r1, r2, r3, r4, r5, r6, r7);
    }

    @Override
    public int compareTo(Int512 other) {
        int comparison = Long.compare(l0, other.l0);
        for (int group = 1; comparison == 0 && group < 8; group++) {
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
        return (l0 | l1 | l2 | l3 | l4 | l5 | l6 | l7) == 0;
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
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public boolean testBit(int index) {
        if (index < 0 || index >= 512) {
            throw new IndexOutOfBoundsException(index);
        }
        return (getLong(7 - (index >>> 6)) & (1L << (index & 63))) != 0;
    }

    @Override
    public int bitLength() {
        long mask = l0 >> 63;
        for (int group = 0; group < 8; group++) {
            long word = getLong(group) ^ mask;
            if (word != 0) {
                return (8 - group) * 64 - Long.numberOfLeadingZeros(word);
            }
        }
        return 0;
    }

    @Override
    public int bitCount() {
        return Long.bitCount(l0) + Long.bitCount(l1) + Long.bitCount(l2) + Long.bitCount(l3)
                + Long.bitCount(l4) + Long.bitCount(l5) + Long.bitCount(l6) + Long.bitCount(l7);
    }
}
