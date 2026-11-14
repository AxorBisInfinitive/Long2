package com.inf.long2.binaries.binary256;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.BinaryMath;
import com.inf.long2.Int;
import com.inf.long2.binaries.internal.UnsignedWords;
import com.inf.long2.ints.Int256;

import static com.inf.long2.binaries.binary256.Binary256Encoding.BIAS;
import static com.inf.long2.binaries.binary256.Binary256Encoding.FRACTION_MASK;
import static com.inf.long2.binaries.binary256.Binary256Encoding.HIDDEN_BIT;
import static com.inf.long2.binaries.binary256.Binary256Encoding.QMIN;
import static com.inf.long2.binaries.binary256.Binary256Encoding.emptyFraction;
import static com.inf.long2.binaries.binary256.Binary256Encoding.encodedExponent;
import static com.inf.long2.binaries.binary256.Binary256Encoding.fromUnsignedLong;
import static com.inf.long2.binaries.binary256.Binary256Encoding.infinity;
import static com.inf.long2.binaries.binary256.Binary256Encoding.magnitudeWord;
import static com.inf.long2.binaries.binary256.Binary256Encoding.zero;
import static com.inf.long2.binaries.binary256.Binary256Rounding.bitLength;
import static com.inf.long2.binaries.binary256.Binary256Rounding.fromDyadic;
import static com.inf.long2.binaries.binary256.Binary256Rounding.packRounded;
import static com.inf.long2.binaries.binary256.Binary256Rounding.shiftedWord;

/**
 * 256-bit 二进制浮点数，以四个 long 按大端序保存原始编码。
 * bit 255 为符号，bit 236 至 bit 254 为 19 位偏置指数，其余 236 位为小数字段。
 * 正常数补入隐含位后具有 237 位精度，指数偏置为 262143。
 * 加减与 FMA 的精确对齐均使用有界工作区，FMA 对齐幅值最多使用 12 个 long，不随指数差扩张。
 */
public final class Binary256 implements Binary<Binary256>, BinaryMath {
    private static final long INFINITY_TOP = 0x7fff_f000_0000_0000L;
    private static final long NAN_TOP = 0x7fff_f800_0000_0000L;

    /**
     * 237 位有效数字，正常指数范围为 -262142 至 262143。
     *
     * @see Binary#FORMAT
     */
    public static final Format FORMAT = new Format(237, -262142, 262143);

    /**
     * 正零。
     *
     * @see Binary#ZERO
     */
    public static final Binary256 ZERO = new Binary256(0, 0, 0, 0);
    /**
     * 负零。
     *
     * @see Binary#NEGATIVE_ZERO
     */
    public static final Binary256 NEGATIVE_ZERO = new Binary256(Long.MIN_VALUE, 0, 0, 0);
    /**
     * 精确的一。
     *
     * @see Binary#ONE
     */
    public static final Binary256 ONE = new Binary256(0x3fff_f000_0000_0000L, 0, 0, 0);
    /**
     * 最小非零正数，数值为 {@code 2^-262378}。
     *
     * @see Binary#MIN_VALUE
     */
    public static final Binary256 MIN_VALUE = new Binary256(0, 0, 0, 1);
    /**
     * 最小正常正数，数值为 {@code 2^-262142}。
     *
     * @see Binary#MIN_NORMAL
     */
    public static final Binary256 MIN_NORMAL = new Binary256(HIDDEN_BIT, 0, 0, 0);
    /**
     * 最大有限正数。
     *
     * @see Binary#MAX_VALUE
     */
    public static final Binary256 MAX_VALUE = new Binary256(0x7fff_efff_ffff_ffffL, -1, -1, -1);
    /**
     * 正无穷。
     *
     * @see Binary#POSITIVE_INFINITY
     */
    public static final Binary256 POSITIVE_INFINITY = new Binary256(INFINITY_TOP, 0, 0, 0);
    /**
     * 负无穷。
     *
     * @see Binary#NEGATIVE_INFINITY
     */
    public static final Binary256 NEGATIVE_INFINITY = new Binary256(INFINITY_TOP | Long.MIN_VALUE, 0, 0, 0);
    /**
     * 正的 quiet NaN，数值运算产生的 NaN 统一使用该编码。
     *
     * @see Binary#NaN
     */
    public static final Binary256 NaN = new Binary256(NAN_TOP, 0, 0, 0);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Binary256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /** 按大端序保存完整原始编码，包括 NaN 的符号、payload 和 signaling 位，不执行数值转换。 */
    static Binary256 fromRawBits(long l0, long l1, long l2, long l3) {
        return new Binary256(l0, l1, l2, l3);
    }

    /**
     * 从恰好 32 字节的大端序原始编码恢复数值，所有位模式均可接受。
     * 与 toBytes() 逐位互逆，包括 NaN 的符号、payload 和 signaling 位，不执行数值转换。
     * 不修改或持有输入数组。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不等于 32
     * @see Binary#of(byte[])
     */
    public static Binary256 of(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("bytes must contain exactly 32 elements");
        }
        return fromRawBits(readRawLong(bytes, 0), readRawLong(bytes, 8),
                readRawLong(bytes, 16), readRawLong(bytes, 24));
    }

    private static long readRawLong(byte[] bytes, int offset) {
        long word = 0;
        for (int index = 0; index < 8; index++) {
            word = (word << 8) | (bytes[offset + index] & 0xffL);
        }
        return word;
    }

    /**
     * 返回原始编码的一个 long 分组，索引 0 为最高组，3 为最低组。
     *
     * @throws IndexOutOfBoundsException 索引小于 0 或大于 3
     */
    long getRawLong(int index) {
        return switch (index) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /**
     * 返回新的 32 字节大端序原始编码，与 getRawLong(0..3) 的顺序一致。
     * bit 255 为符号，bit 236 至 bit 254 为指数，其余位为小数；包括 NaN 编码在内的所有位均原样保留。
     */
    @Override
    public byte[] toBytes() {
        byte[] result = new byte[32];
        for (int group = 0; group < 4; group++) {
            long word = getRawLong(group);
            for (int index = 0; index < 8; index++) {
                result[group * 8 + index] = (byte) (word >>> (56 - index * 8));
            }
        }
        return result;
    }

    /**
     * 从 long 精确构造，包含 Long.MIN_VALUE。
     *
     * @see Binary#of(long)
     */
    public static Binary256 of(long value) {
        // Long.MIN_VALUE 取负后的位模式仍是正确的无符号幅值。
        return fromUnsignedLong(value < 0, value < 0 ? -value : value, 0);
    }

    /**
     * 精确接收 float 的有限数值，保留零和无穷的符号。
     *
     * @see Binary#of(float)
     */
    public static Binary256 of(float value) {
        int bits = Float.floatToRawIntBits(value);
        return fromPrimitive(bits < 0, (bits >>> 23) & 255, bits & 0x7fffffL, 23, 127, 255);
    }

    /**
     * 精确接收 double 的有限数值，保留零和无穷的符号。
     *
     * @see Binary#of(double)
     */
    public static Binary256 of(double value) {
        long bits = Double.doubleToRawLongBits(value);
        return fromPrimitive(bits < 0, (int) ((bits >>> 52) & 2047), bits & 0xfffffffffffffL, 52, 1023, 2047);
    }

    private static Binary256 fromPrimitive(boolean negative, int exponent, long fraction,
            int fractionBits, int bias, int infinityExponent) {
        if (exponent == infinityExponent) {
            return fraction == 0 ? infinity(negative) : NaN;
        }
        long magnitude = exponent == 0 ? fraction : fraction | (1L << fractionBits);
        long scale = (exponent == 0 ? 1L : exponent) - bias - fractionBits;
        return fromUnsignedLong(negative, magnitude, scale);
    }

    /**
     * 从完整整数直接舍入，最小负数的幅值按原始位模式读取。
     *
     * @see Binary#of(Int)
     */
    public static Binary256 of(Int<?> value) {
        Objects.requireNonNull(value);
        if (value instanceof Int256 fixed) {
            return fromInt256(fixed);
        }
        boolean negative = value.isNegative();
        int length = value.bitLength();
        if (length > FORMAT.maxExponent() + 1L) {
            return infinity(negative);
        }
        if (length == 0) {
            return negative ? fromUnsignedLong(true, 1, 0) : ZERO;
        }
        int groups = value.format().bitWidth() / 64;
        if (length < 64) {
            long raw = value.getLong(groups - 1);
            return fromUnsignedLong(negative, negative ? -raw : raw, 0);
        }
        // 负数先读取 ~value 的高位，补码取负所需的加一合并到最终舍入，不构造完整幅值对象。
        int discarded = Math.max(length - 237, 0);
        int first = groups - 1 - (length - 1) / 64;
        int last = groups - 1 - (discarded == 0 ? 0 : (discarded - 1) / 64);
        long[] words = new long[4];
        long boundary = 0;
        for (int index = first; index <= last; index++) {
            long raw = value.getLong(index);
            boundary = raw;
            long word = negative ? ~raw : raw;
            int offset = (groups - 1 - index) * 64 - discarded;
            if (offset > -64 && offset < 0) {
                words[0] |= word >>> -offset;
            } else if (offset >= 0) {
                int group = offset / 64, bits = offset & 63;
                words[group] |= word << bits;
                if (bits != 0 && group + 1 < words.length) {
                    words[group + 1] |= word >>> (64 - bits);
                }
            }
        }
        if (discarded == 0) {
            if (negative) {
                for (int index = 0; index < words.length; index++) {
                    if (++words[index] != 0) {
                        break;
                    }
                }
            }
            return fromDyadic(negative, words, 0);
        }
        boolean guard = ((negative ? ~boundary : boundary) & (1L << ((discarded - 1) & 63))) != 0;
        boolean odd = (words[0] & 1) != 0;
        // |负数| = ~value + 1。guard 为一时必越过中点；为零时仅奇数侧的临界尾项可能进位。
        boolean increment = negative
                ? guard || (odd && !anyLowIntegerBits(value, groups, discarded - 1, boundary))
                : guard && (odd || anyLowIntegerBits(value, groups, discarded - 1, boundary));
        return packRounded(negative, words[0], words[1], words[2], words[3], discarded, increment);
    }

    /** boundary 是包含 bit count 的原始分组，检查其下方的 count 位，发现非零组即停止。 */
    private static boolean anyLowIntegerBits(Int<?> value, int groups, int count, long boundary) {
        int bits = count & 63;
        if (bits != 0 && (boundary & ((1L << bits) - 1)) != 0) {
            return true;
        }
        int boundaryIndex = groups - 1 - count / 64;
        for (int index = groups - 1; index > boundaryIndex; index--) {
            if (value.getLong(index) != 0) {
                return true;
            }
        }
        return false;
    }

    private static Binary256 fromInt256(Int256 value) {
        boolean negative = value.isNegative();
        long m0 = value.getLong(0), m1 = value.getLong(1), m2 = value.getLong(2), m3 = value.getLong(3);
        if (negative) {
            // 低组全零时继续传播补码取负的进位，最小负数也得到正确的无符号幅值。
            m3 = -m3;
            m2 = ~m2 + (m3 == 0 ? 1 : 0);
            m1 = ~m1 + ((m3 | m2) == 0 ? 1 : 0);
            m0 = ~m0 + ((m3 | m2 | m1) == 0 ? 1 : 0);
        }
        if ((m0 | m1 | m2) == 0) {
            return fromUnsignedLong(negative, m3, 0);
        }
        int discarded = bitLength(m3, m2, m1, m0) - 237;
        long low = shiftedWord(m3, m2, m1, m0, 0, discarded);
        // 整数幅值最多 256 位，舍弃部分最多 19 位，guard 与 sticky 均在最低分组内。
        boolean increment = discarded > 0 && (m3 & (1L << (discarded - 1))) != 0
                && ((low & 1) != 0 || (m3 & ((1L << (discarded - 1)) - 1)) != 0);
        return packRounded(negative, low, shiftedWord(m3, m2, m1, m0, 1, discarded),
                shiftedWord(m3, m2, m1, m0, 2, discarded), shiftedWord(m3, m2, m1, m0, 3, discarded),
                discarded, increment);
    }

    /**
     * 从源格式直接舍入，读取有限值分解，不经过 double。
     *
     * @see Binary#of(Binary)
     */
    public static Binary256 of(Binary<?> value) {
        Objects.requireNonNull(value);
        if (value.isNaN()) {
            return NaN;
        }
        boolean negative = value.signBit();
        if (value.isInfinite()) {
            return infinity(negative);
        }
        if (value.isZero()) {
            return zero(negative);
        }
        if (value instanceof Binary256 same) {
            return same;
        }
        Format sourceFormat = value.format();
        int precision = sourceFormat.precision();
        long scale = value.scaleExponent();
        long maximumExponent = scale + precision - 1L;
        if (maximumExponent < QMIN - 1L) {
            return zero(negative);
        }
        // 次正规源的 scale+p-1 等于 emin，且 emin 不大于零；越过目标上界时源必为正常数。
        if (maximumExponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        int groups = (int) ((precision + 63L) / 64);
        long guard = QMIN - 1L - scale;
        int lastLeadingGroup = guard > 0 ? groups - 1 - (int) (guard / 64) : groups - 1;
        int first = 0;
        long top;
        do {
            top = value.getSignificandLong(first++);
            // 半个 MIN_VALUE 对应 bit 及以上均为零时，更低分组不能改变舍入为零的结果。
            if (top == 0 && first > lastLeadingGroup) {
                return zero(negative);
            }
        } while (top == 0 && first < groups);
        long length = (groups - (long) first) * 64 + 64 - Long.numberOfLeadingZeros(top);
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        int kept = (int) Math.min(length, 239);
        long dropped = length - kept;
        long[] magnitude = new long[(kept + 63) / 64];
        int last = groups - 1 - (int) (dropped / 64);
        long lastWord = top;
        for (int index = first - 1; index <= last; index++) {
            long word = index == first - 1 ? top : value.getSignificandLong(index);
            lastWord = word;
            long offset = (groups - 1L - index) * 64 - dropped;
            if (offset < 0) {
                magnitude[0] |= word >>> (int) -offset;
            } else {
                int group = (int) (offset / 64);
                int shift = (int) (offset & 63);
                magnitude[group] |= word << shift;
                if (shift != 0 && group + 1 < magnitude.length) {
                    magnitude[group + 1] |= word >>> (64 - shift);
                }
            }
        }
        long discarded = Math.max(exponent - 236, QMIN) - scale - dropped;
        // 只有已保留部分恰为偶数一侧的中点，才需读取更低尾项；其他情况的舍入方向已经确定。
        if (dropped != 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && !UnsignedWords.bit(magnitude, discarded) && !UnsignedWords.anyLow(magnitude, discarded - 1)) {
            int partial = (int) (dropped & 63);
            boolean sticky = partial != 0 && (lastWord & ((1L << partial) - 1)) != 0;
            for (int index = groups - 1; index > last && !sticky; index--) {
                sticky = value.getSignificandLong(index) != 0;
            }
            if (sticky) {
                // 两位舍入余量保证最低位低于最终 guard。
                magnitude[0] |= 1;
            }
        }
        return fromDyadic(negative, magnitude, scale + dropped);
    }

    @Override
    public Format format() {
        return FORMAT;
    }

    @Override
    public boolean signBit() {
        return l0 < 0;
    }

    @Override
    public boolean isNaN() {
        return encodedExponent(this) == 0x7ffff && !emptyFraction(this);
    }

    @Override
    public boolean isInfinite() {
        return encodedExponent(this) == 0x7ffff && emptyFraction(this);
    }

    @Override
    public boolean isFinite() {
        return encodedExponent(this) != 0x7ffff;
    }

    @Override
    public boolean isZero() {
        return encodedExponent(this) == 0 && emptyFraction(this);
    }

    @Override
    public boolean isNormal() {
        return encodedExponent(this) != 0 && isFinite();
    }

    @Override
    public boolean isSubnormal() {
        return encodedExponent(this) == 0 && !emptyFraction(this);
    }

    private void requireFinite() {
        if (!isFinite()) {
            throw new IllegalStateException("A finite value is required");
        }
    }

    @Override
    public long scaleExponent() {
        requireFinite();
        int exponent = encodedExponent(this);
        return exponent == 0 ? QMIN : exponent - (long) BIAS - 236;
    }

    @Override
    public long getSignificandLong(int index) {
        requireFinite();
        return switch (index) {
            case 0 -> (l0 & FRACTION_MASK) | (isNormal() ? HIDDEN_BIT : 0);
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /** 返回有限幅值右移后的低 64 位，distance 不小于零。 */
    private long shiftedMagnitude(int distance) {
        int group = distance >>> 6;
        int bits = distance & 63;
        long result = magnitudeWord(this, group) >>> bits;
        return bits == 0 ? result : result | (magnitudeWord(this, group + 1) << (64 - bits));
    }

    private boolean magnitudeBit(int index) {
        return (magnitudeWord(this, index >>> 6) & (1L << (index & 63))) != 0;
    }

    private boolean anyLowMagnitude(int count) {
        int whole = count >>> 6;
        for (int i = 0; i < whole; i++) {
            if (magnitudeWord(this, i) != 0) {
                return true;
            }
        }
        int bits = count & 63;
        return bits != 0 && (magnitudeWord(this, whole) & ((1L << bits) - 1)) != 0;
    }

    @Override
    public Binary256 add(Binary256 other) {
        Objects.requireNonNull(other);
        return Binary256Arithmetic.add(this, other, false);
    }

    @Override
    public Binary256 subtract(Binary256 other) {
        Objects.requireNonNull(other);
        return Binary256Arithmetic.add(this, other, true);
    }

    @Override
    public Binary256 multiply(Binary256 other) {
        Objects.requireNonNull(other);
        return Binary256Arithmetic.multiply(this, other);
    }

    @Override
    public Binary256 divide(Binary256 other) {
        Objects.requireNonNull(other);
        return Binary256Arithmetic.divide(this, other);
    }

    @Override
    public Binary256 remainder(Binary256 other) {
        Objects.requireNonNull(other);
        return Binary256Arithmetic.remainder(this, other, false);
    }

    /** @see BinaryMath#ieeeRemainder(BinaryMath, BinaryMath) */
    public static Binary256 ieeeRemainder(Binary256 dividend, Binary256 divisor) {
        Objects.requireNonNull(dividend);
        Objects.requireNonNull(divisor);
        return Binary256Arithmetic.remainder(dividend, divisor, true);
    }

    @Override
    public Binary256 negate() {
        return isNaN() ? NaN : new Binary256(l0 ^ Long.MIN_VALUE, l1, l2, l3);
    }

    /** @see BinaryMath#abs(BinaryMath) */
    public static Binary256 abs(Binary256 value) {
        Objects.requireNonNull(value);
        return value.isNaN() ? NaN : value.signBit() ? new Binary256(value.l0 & Long.MAX_VALUE, value.l1, value.l2, value.l3) : value;
    }

    /** @see BinaryMath#fma(BinaryMath, BinaryMath, BinaryMath) */
    public static Binary256 fma(Binary256 multiplicand, Binary256 multiplier, Binary256 addend) {
        Objects.requireNonNull(multiplicand);
        Objects.requireNonNull(multiplier);
        Objects.requireNonNull(addend);
        return Binary256Arithmetic.fma(multiplicand, multiplier, addend);
    }

    /** @see BinaryMath#sqrt(BinaryMath) */
    public static Binary256 sqrt(Binary256 value) {
        Objects.requireNonNull(value);
        return Binary256Arithmetic.sqrt(value);
    }

    /** @see BinaryMath#scalb(BinaryMath, long) */
    public static Binary256 scalb(Binary256 value, long scaleFactor) {
        Objects.requireNonNull(value);
        return Binary256Arithmetic.scalb(value, scaleFactor);
    }

    @Override
    public Binary256 ulp() {
        return Binary256Arithmetic.ulp(this);
    }

    @Override
    public Binary256 nextUp() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE;
        }
        if (equals(POSITIVE_INFINITY)) {
            return this;
        }
        return stepRaw(!signBit());
    }

    @Override
    public Binary256 nextDown() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE.negate();
        }
        if (equals(NEGATIVE_INFINITY)) {
            return this;
        }
        return stepRaw(signBit());
    }

    private Binary256 stepRaw(boolean increment) {
        long delta = increment ? 1 : -1;
        long propagated = increment ? 0 : -1;
        long next3 = l3 + delta, next2 = l2, next1 = l1, next0 = l0;
        if (next3 == propagated) {
            next2 += delta;
            if (next2 == propagated) {
                next1 += delta;
                if (next1 == propagated) {
                    next0 += delta;
                }
            }
        }
        return new Binary256(next0, next1, next2, next3);
    }

    private int compareOrdered(Binary256 other) {
        if (isZero() && other.isZero()) {
            return 0;
        }
        if (signBit() != other.signBit()) {
            return signBit() ? -1 : 1;
        }
        int result = Long.compareUnsigned(l0, other.l0);
        if (result == 0) {
            result = Long.compareUnsigned(l1, other.l1);
        }
        if (result == 0) {
            result = Long.compareUnsigned(l2, other.l2);
        }
        if (result == 0) {
            result = Long.compareUnsigned(l3, other.l3);
        }
        return signBit() ? -result : result;
    }

    @Override
    public Comparison compareNumerically(Binary256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Comparison.UNORDERED;
        }
        int comparison = compareOrdered(other);
        return comparison < 0 ? Comparison.LESS : comparison > 0 ? Comparison.GREATER : Comparison.EQUAL;
    }

    @Override
    public int compareTo(Binary256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Boolean.compare(isNaN(), other.isNaN());
        }
        if (isZero() && other.isZero()) {
            return Boolean.compare(other.signBit(), signBit());
        }
        return compareOrdered(other);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Binary256 other && ((isNaN() && other.isNaN())
                || (l0 == other.l0 && l1 == other.l1 && l2 == other.l2 && l3 == other.l3));
    }

    @Override
    public int hashCode() {
        if (isNaN()) {
            return Long.hashCode(NAN_TOP);
        }
        int hash = Long.hashCode(l0);
        hash = 31 * hash + Long.hashCode(l1);
        hash = 31 * hash + Long.hashCode(l2);
        return 31 * hash + Long.hashCode(l3);
    }

    /** 从普通或科学计数法十进制文本直接按最近偶数规则舍入，详见 {@link Binary#of(String)}。 */
    public static Binary256 of(String value) {
        return Binary256Decimal.parse(value);
    }

    /** 仅判断文本是否符合 {@link Binary#of(String)} 的语法，null 返回 false，不执行数值转换。 */
    public static boolean isValid(String value) {
        return Binary256Decimal.isValid(value);
    }

    @Override
    public String toString() {
        return Binary256Decimal.format(this, false);
    }

    @Override
    public String toString(int fractionDigits) {
        return Binary256Decimal.format(this, false, fractionDigits);
    }

    @Override
    public String toScientificString() {
        return Binary256Decimal.format(this, true);
    }

    @Override
    public String toScientificString(int fractionDigits) {
        return Binary256Decimal.format(this, true, fractionDigits);
    }

    private long primitiveBits(int precision, int emin, int emax, int exponentBits) {
        int fractionBits = precision - 1;
        long sign = signBit() ? 1L << (fractionBits + exponentBits) : 0;
        long infinity = ((1L << exponentBits) - 1) << fractionBits;
        if (isNaN()) {
            return infinity | (1L << (fractionBits - 1));
        }
        if (isInfinite()) {
            return sign | infinity;
        }
        int exponent = encodedExponent(this) - BIAS;
        if (exponent > emax) {
            return sign | infinity;
        }
        int minimumScale = emin - fractionBits;
        if (exponent < minimumScale - 1) {
            return sign;
        }
        // 能在 binary32 或 binary64 中舍入为非零的源值必为 Binary256 正常数。
        int quantum = Math.max(exponent - fractionBits, minimumScale);
        int discarded = quantum - exponent + 236;
        long rounded = shiftedMagnitude(discarded);
        if (magnitudeBit(discarded - 1) && ((rounded & 1) != 0 || anyLowMagnitude(discarded - 1))) {
            rounded++;
        }
        if (rounded == 0) {
            return sign;
        }
        exponent = quantum + 63 - Long.numberOfLeadingZeros(rounded);
        if (exponent > emax) {
            return sign | infinity;
        }
        long encoded = exponent < emin ? 0 : exponent + emax;
        return sign | (encoded << fractionBits)
                | (rounded & ((1L << fractionBits) - 1));
    }

    @Override
    public float toFloat() {
        return Float.intBitsToFloat((int) primitiveBits(24, -126, 127, 8));
    }

    @Override
    public double toDouble() {
        return Double.longBitsToDouble(primitiveBits(53, -1022, 1023, 11));
    }

    private long toIntegral(int width) {
        long minimum = width == 64 ? Long.MIN_VALUE : Integer.MIN_VALUE;
        long maximum = width == 64 ? Long.MAX_VALUE : Integer.MAX_VALUE;
        if (isNaN()) {
            return 0;
        }
        if (isInfinite()) {
            return signBit() ? minimum : maximum;
        }
        int exponent = encodedExponent(this) - BIAS;
        if (exponent >= width - 1) {
            return signBit() ? minimum : maximum;
        }
        if (exponent < 0) {
            return 0;
        }
        long result = shiftedMagnitude(236 - exponent);
        return signBit() ? -result : result;
    }

    @Override
    public int toInt() {
        return (int) toIntegral(32);
    }

    @Override
    public long toLong() {
        return toIntegral(64);
    }
}
