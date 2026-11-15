package com.inf.long2.binaries.binary1280;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.BinaryMath;
import com.inf.long2.Int;
import com.inf.long2.binaries.internal.UnsignedWords;

import static com.inf.long2.binaries.binary1280.Binary1280Encoding.*;

/**
 * 1280 位二进制浮点数，原始交换编码为 160 字节大端序。
 * 符号占一位，偏置指数占 28 位，小数字段占 1251 位，正常数精度为 1252 位。
 */
public final class Binary1280 implements Binary<Binary1280>, BinaryMath {
    private static final long INFINITY_TOP = 0x7fff_fff8_0000_0000L;
    private static final long NAN_TOP = 0x7fff_fffc_0000_0000L;

    public static final Format FORMAT = new Format(PRECISION, EMIN, EMAX);
    public static final Binary1280 ZERO = new Binary1280(0, 0, 0);
    public static final Binary1280 NEGATIVE_ZERO = new Binary1280(Long.MIN_VALUE, 0, 0);
    public static final Binary1280 ONE = new Binary1280(0x3fff_fff8_0000_0000L, 0, 0);
    public static final Binary1280 MIN_VALUE = new Binary1280(0, 0, 1);
    public static final Binary1280 MIN_NORMAL = new Binary1280(HIDDEN_BIT, 0, 0);
    public static final Binary1280 MAX_VALUE = new Binary1280(0x7fff_fff7_ffff_ffffL, -1, -1);
    public static final Binary1280 POSITIVE_INFINITY = new Binary1280(INFINITY_TOP, 0, 0);
    public static final Binary1280 NEGATIVE_INFINITY = new Binary1280(INFINITY_TOP | Long.MIN_VALUE, 0, 0);
    public static final Binary1280 NaN = new Binary1280(NAN_TOP, 0, 0);

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
    private final long l16;
    private final long l17;
    private final long l18;
    private final long l19;

    private Binary1280(long top, long middle, long bottom) {
        this(top, middle, middle, middle, middle, middle, middle, middle, middle, middle,
                middle, middle, middle, middle, middle, middle, middle, middle, middle, bottom);
    }

    private Binary1280(long l0, long l1, long l2, long l3, long l4,
            long l5, long l6, long l7, long l8, long l9,
            long l10, long l11, long l12, long l13, long l14,
            long l15, long l16, long l17, long l18, long l19) {
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
        this.l16 = l16;
        this.l17 = l17;
        this.l18 = l18;
        this.l19 = l19;
    }

    /** 按大端序恢复原始编码，保留 NaN 的符号、payload 和 signaling 位。 */
    static Binary1280 fromRawBits(long l0, long l1, long l2, long l3, long l4,
            long l5, long l6, long l7, long l8, long l9,
            long l10, long l11, long l12, long l13, long l14,
            long l15, long l16, long l17, long l18, long l19) {
        return new Binary1280(l0, l1, l2, l3, l4, l5, l6, l7, l8, l9,
                l10, l11, l12, l13, l14, l15, l16, l17, l18, l19);
    }

    /** 输入二次幂的编码指数大于一，返回比它更靠近零的相邻幅值。 */
    static Binary1280 belowNormalPowerOfTwo(boolean negative, int encoded) {
        long top = (negative ? Long.MIN_VALUE : 0) | ((encoded - 1L) << TOP_FRACTION_BITS) | FRACTION_MASK;
        return new Binary1280(top, -1L, -1L);
    }

    /** 比较最高组和最低组之间的原始有效数字分组。 */
    boolean sameMiddleWords(Binary1280 other) {
        return ((l1 ^ other.l1) | (l2 ^ other.l2) | (l3 ^ other.l3) | (l4 ^ other.l4)
                | (l5 ^ other.l5) | (l6 ^ other.l6) | (l7 ^ other.l7) | (l8 ^ other.l8)
                | (l9 ^ other.l9) | (l10 ^ other.l10) | (l11 ^ other.l11) | (l12 ^ other.l12)
                | (l13 ^ other.l13) | (l14 ^ other.l14) | (l15 ^ other.l15) | (l16 ^ other.l16)
                | (l17 ^ other.l17) | (l18 ^ other.l18)) == 0;
    }

    private static Binary1280 fromLowWords(long[] words) {
        return fromRawBits(words[19], words[18], words[17], words[16], words[15],
                words[14], words[13], words[12], words[11], words[10],
                words[9], words[8], words[7], words[6], words[5],
                words[4], words[3], words[2], words[1], words[0]);
    }

    /** 返回一个原始编码分组，索引零为最高组，有效索引为零至十九。 */
    long getRawLong(int index) {
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
            case 16 -> l16;
            case 17 -> l17;
            case 18 -> l18;
            case 19 -> l19;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /** 只接受恰好 160 字节的大端序编码，不持有输入数组。 */
    public static Binary1280 of(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 160) {
            throw new IllegalArgumentException("bytes must contain exactly 160 elements");
        }
        long[] words = new long[WORDS];
        for (int i = 0; i < bytes.length; i++) {
            int group = WORDS - 1 - i / 8;
            words[group] = (words[group] << 8) | (bytes[i] & 0xffL);
        }
        return fromLowWords(words);
    }

    @Override
    public byte[] toBytes() {
        byte[] bytes = new byte[160];
        for (int group = 0; group < WORDS; group++) {
            long word = getRawLong(group);
            for (int i = 0; i < 8; i++) {
                bytes[group * 8 + i] = (byte) (word >>> (56 - i * 8));
            }
        }
        return bytes;
    }

    public static Binary1280 of(long value) {
        return Binary1280Rounding.roundDyadic(value < 0, new long[] {value < 0 ? -value : value}, 0);
    }

    public static Binary1280 of(float value) {
        int bits = Float.floatToRawIntBits(value);
        return fromPrimitive(bits < 0, (bits >>> 23) & 255, bits & 0x7fffffL, 23, 127, 255);
    }

    public static Binary1280 of(double value) {
        long bits = Double.doubleToRawLongBits(value);
        return fromPrimitive(bits < 0, (int) ((bits >>> 52) & 2047), bits & 0xfffffffffffffL, 52, 1023, 2047);
    }

    private static Binary1280 fromPrimitive(boolean negative, int exponent, long fraction,
            int fractionBits, int bias, int infinityExponent) {
        if (exponent == infinityExponent) {
            return fraction == 0 ? infinity(negative) : NaN;
        }
        long magnitude = exponent == 0 ? fraction : fraction | (1L << fractionBits);
        long scale = (exponent == 0 ? 1L : exponent) - bias - fractionBits;
        return Binary1280Rounding.roundDyadic(negative, new long[] {magnitude}, scale);
    }

    public static Binary1280 of(Int<?> value) {
        Objects.requireNonNull(value);
        boolean negative = value.isNegative();
        int length = value.bitLength();
        if (length > FORMAT.maxExponent() + 1L) {
            return infinity(negative);
        }
        long upperLength = length + 1L;
        // 上界可能比真实幅值多一位，保留 1255 位才能保证 sticky 严格低于 guard。
        long dropped = Math.max(upperLength - (PRECISION + 3), 0);
        int groups = value.format().bitWidth() / 64;
        int needed = (int) ((upperLength + 63) / 64);
        long[] window = new long[WORDS];
        long carry = negative ? 1 : 0;
        boolean sticky = false;
        for (int i = 0; i < needed; i++) {
            long raw = value.getLong(groups - 1 - i);
            long word = negative ? ~raw + carry : raw;
            if (negative) {
                carry = carry != 0 && word == 0 ? 1 : 0;
            }
            long offset = i * 64L - dropped;
            if (offset <= -64) {
                sticky |= word != 0;
            } else {
                if (offset < 0) {
                    sticky |= (word & ((1L << (int) -offset) - 1)) != 0;
                }
                Binary1280Rounding.insert(window, word, offset);
            }
        }
        if (sticky) {
            window[0] |= 1;
        }
        return Binary1280Rounding.roundDyadic(negative, window, dropped);
    }

    public static Binary1280 of(Binary<?> value) {
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
        if (value instanceof Binary1280 same) {
            return same;
        }
        int precision = value.format().precision();
        long scale = value.scaleExponent();
        long maximumExponent = scale + precision - 1L;
        if (maximumExponent < QMIN - 1L) {
            return zero(negative);
        }
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
        long dropped = Math.max(length - (PRECISION + 2), 0);
        long[] window = new long[WORDS];
        int last = groups - 1 - (int) (dropped / 64);
        long lastWord = top;
        for (int i = first - 1; i <= last; i++) {
            lastWord = i == first - 1 ? top : value.getSignificandLong(i);
            Binary1280Rounding.insert(window, lastWord, (groups - 1L - i) * 64 - dropped);
        }
        // 只在偶数侧中点读取低尾项，避免极大名义精度迫使无关分组参与转换。
        long discarded = Math.max(exponent - FRACTION_BITS, QMIN) - scale - dropped;
        if (dropped != 0 && UnsignedWords.bit(window, discarded - 1)
                && !UnsignedWords.bit(window, discarded) && !UnsignedWords.anyLow(window, discarded - 1)) {
            int partial = (int) (dropped & 63);
            boolean sticky = partial != 0 && (lastWord & ((1L << partial) - 1)) != 0;
            for (int i = groups - 1; i > last && !sticky; i--) {
                sticky = value.getSignificandLong(i) != 0;
            }
            if (sticky) {
                window[0] |= 1;
            }
        }
        return Binary1280Rounding.roundDyadic(negative, window, scale + dropped);
    }

    public static Binary1280 of(String value) {
        return Binary1280Decimal.parse(value);
    }

    public static boolean isValid(String value) {
        return Binary1280Decimal.isValid(value);
    }

    @Override
    public Format format() {
        return FORMAT;
    }

    private int encodedExponent() {
        return (int) ((l0 >>> TOP_FRACTION_BITS) & EXPONENT_MASK);
    }

    boolean emptyFraction() {
        return ((l0 & FRACTION_MASK) | l1 | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9
                | l10 | l11 | l12 | l13 | l14 | l15 | l16 | l17 | l18 | l19) == 0;
    }

    /** 有效数字能右移为单组的正常数返回其单组幅值，否则返回零。调用方已排除非有限值。 */
    long compactNormalSignificand() {
        int shift = PRECISION % Long.SIZE;
        long lowMask = (1L << shift) - 1;
        if (l19 != 0 || ((l1 & lowMask) | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9
                | l10 | l11 | l12 | l13 | l14 | l15 | l16 | l17 | l18) != 0 || encodedExponent() == 0) {
            return 0;
        }
        return (((l0 & FRACTION_MASK) | HIDDEN_BIT) << (Long.SIZE - shift)) | (l1 >>> shift);
    }

    @Override
    public boolean signBit() {
        return l0 < 0;
    }

    @Override
    public boolean isNaN() {
        return encodedExponent() == EXPONENT_MASK && !emptyFraction();
    }

    @Override
    public boolean isInfinite() {
        return encodedExponent() == EXPONENT_MASK && emptyFraction();
    }

    @Override
    public boolean isFinite() {
        return encodedExponent() != EXPONENT_MASK;
    }

    @Override
    public boolean isZero() {
        return encodedExponent() == 0 && emptyFraction();
    }

    @Override
    public boolean isNormal() {
        return encodedExponent() != 0 && isFinite();
    }

    @Override
    public boolean isSubnormal() {
        return encodedExponent() == 0 && !emptyFraction();
    }

    private void requireFinite() {
        if (!isFinite()) {
            throw new IllegalStateException("A finite value is required");
        }
    }

    @Override
    public long scaleExponent() {
        requireFinite();
        int encoded = encodedExponent();
        return encoded == 0 ? QMIN : encoded - (long) BIAS - FRACTION_BITS;
    }

    @Override
    public long getSignificandLong(int index) {
        requireFinite();
        return index == 0 ? (l0 & FRACTION_MASK) | (encodedExponent() == 0 ? 0 : HIDDEN_BIT) : getRawLong(index);
    }

    @Override
    public Binary1280 add(Binary1280 other) {
        Objects.requireNonNull(other);
        return Binary1280Arithmetic.add(this, other, false);
    }

    @Override
    public Binary1280 subtract(Binary1280 other) {
        Objects.requireNonNull(other);
        return Binary1280Arithmetic.add(this, other, true);
    }

    @Override
    public Binary1280 multiply(Binary1280 other) {
        Objects.requireNonNull(other);
        return Binary1280Arithmetic.multiply(this, other);
    }

    @Override
    public Binary1280 divide(Binary1280 other) {
        Objects.requireNonNull(other);
        return Binary1280Arithmetic.divide(this, other);
    }

    @Override
    public Binary1280 remainder(Binary1280 other) {
        Objects.requireNonNull(other);
        return Binary1280Arithmetic.remainder(this, other);
    }

    /** @see BinaryMath#ieeeRemainder(BinaryMath, BinaryMath) */
    public static Binary1280 ieeeRemainder(Binary1280 dividend, Binary1280 divisor) {
        Objects.requireNonNull(dividend);
        Objects.requireNonNull(divisor);
        return Binary1280Arithmetic.ieeeRemainder(dividend, divisor);
    }

    /** @see BinaryMath#fma(BinaryMath, BinaryMath, BinaryMath) */
    public static Binary1280 fma(Binary1280 multiplicand, Binary1280 multiplier, Binary1280 addend) {
        Objects.requireNonNull(multiplicand);
        Objects.requireNonNull(multiplier);
        Objects.requireNonNull(addend);
        return Binary1280Arithmetic.fma(multiplicand, multiplier, addend);
    }

    /** @see BinaryMath#sqrt(BinaryMath) */
    public static Binary1280 sqrt(Binary1280 value) {
        return Binary1280Arithmetic.sqrt(Objects.requireNonNull(value));
    }

    /** @see BinaryMath#scalb(BinaryMath, long) */
    public static Binary1280 scalb(Binary1280 value, long scaleFactor) {
        Objects.requireNonNull(value);
        if (value.isNaN()) {
            return NaN;
        }
        if (value.isZero() || value.isInfinite()) {
            return value;
        }
        long[] magnitude = magnitude(value);
        long scale = value.scaleExponent();
        long exponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        // 先比较允许的增量，避免 scaleFactor 在相加时回绕。
        if (scaleFactor > EMAX - exponent) {
            return infinity(value.signBit());
        }
        if (scaleFactor < QMIN - 1L - exponent) {
            return zero(value.signBit());
        }
        return Binary1280Rounding.roundDyadic(value.signBit(), magnitude, scale + scaleFactor);
    }

    /** @see BinaryMath#abs(BinaryMath) */
    public static Binary1280 abs(Binary1280 value) {
        Objects.requireNonNull(value);
        return value.isNaN() ? NaN : value.signBit() ? value.negate() : value;
    }

    @Override
    public Binary1280 negate() {
        if (isNaN()) {
            return NaN;
        }
        return new Binary1280(l0 ^ Long.MIN_VALUE, l1, l2, l3, l4, l5, l6, l7, l8, l9,
                l10, l11, l12, l13, l14, l15, l16, l17, l18, l19);
    }

    @Override
    public Binary1280 ulp() {
        if (isNaN()) {
            return NaN;
        }
        if (isInfinite()) {
            return POSITIVE_INFINITY;
        }
        return Binary1280Rounding.roundDyadic(false, new long[] {1}, scaleExponent());
    }

    @Override
    public Binary1280 nextUp() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE;
        }
        return equals(POSITIVE_INFINITY) ? this : step(!signBit());
    }

    @Override
    public Binary1280 nextDown() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE.negate();
        }
        return equals(NEGATIVE_INFINITY) ? this : step(signBit());
    }

    private Binary1280 step(boolean increment) {
        long[] words = new long[WORDS];
        long delta = increment ? 1 : -1;
        long carry = delta;
        for (int i = 0; i < WORDS; i++) {
            long word = getRawLong(WORDS - 1 - i);
            words[i] = word + carry;
            carry = carry != 0 && words[i] == (increment ? 0 : -1) ? delta : 0;
        }
        return fromLowWords(words);
    }

    private int compareOrdered(Binary1280 other) {
        if (isZero() && other.isZero()) {
            return 0;
        }
        if (signBit() != other.signBit()) {
            return signBit() ? -1 : 1;
        }
        for (int i = 0; i < WORDS; i++) {
            int comparison = Long.compareUnsigned(getRawLong(i), other.getRawLong(i));
            if (comparison != 0) {
                return signBit() ? -comparison : comparison;
            }
        }
        return 0;
    }

    @Override
    public Comparison compareNumerically(Binary1280 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Comparison.UNORDERED;
        }
        int comparison = compareOrdered(other);
        return comparison < 0 ? Comparison.LESS : comparison > 0 ? Comparison.GREATER : Comparison.EQUAL;
    }

    @Override
    public int compareTo(Binary1280 other) {
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
        if (!(obj instanceof Binary1280 other)) {
            return false;
        }
        return isNaN() && other.isNaN() || compareOrdered(other) == 0 && signBit() == other.signBit();
    }

    @Override
    public int hashCode() {
        if (isNaN()) {
            return Long.hashCode(NAN_TOP);
        }
        int hash = Long.hashCode(l0);
        for (int i = 1; i < WORDS; i++) {
            hash = 31 * hash + Long.hashCode(getRawLong(i));
        }
        return hash;
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
        int exponent = encodedExponent() - BIAS;
        int minimumScale = emin - fractionBits;
        if (exponent > emax) {
            return sign | infinity;
        }
        if (exponent < minimumScale - 1) {
            return sign;
        }
        long[] magnitude = magnitude(this);
        int quantum = Math.max(exponent - fractionBits, minimumScale);
        int discarded = quantum - exponent + FRACTION_BITS;
        long rounded = Binary1280Rounding.shiftedWord(magnitude, 0, discarded);
        if (UnsignedWords.bit(magnitude, discarded - 1)
                && ((rounded & 1) != 0 || UnsignedWords.anyLow(magnitude, discarded - 1))) {
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
        return sign | (encoded << fractionBits) | (rounded & ((1L << fractionBits) - 1));
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
        int exponent = encodedExponent() - BIAS;
        if (exponent >= width - 1) {
            return signBit() ? minimum : maximum;
        }
        if (exponent < 0) {
            return 0;
        }
        long result = Binary1280Rounding.shiftedWord(magnitude(this), 0, FRACTION_BITS - exponent);
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

    @Override
    public String toString() {
        return Binary1280Decimal.format(this, false);
    }

    @Override
    public String toString(int fractionDigits) {
        return Binary1280Decimal.format(this, false, fractionDigits);
    }

    @Override
    public String toScientificString() {
        return Binary1280Decimal.format(this, true);
    }

    @Override
    public String toScientificString(int fractionDigits) {
        return Binary1280Decimal.format(this, true, fractionDigits);
    }
}
