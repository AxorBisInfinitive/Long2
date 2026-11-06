package com.inf.long2.binaries;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.Int;

/**
 * 640 位二进制浮点数，原始编码按大端序保存在十个 long 中。
 * 符号占一位，偏置指数占 24 位，小数字段占 615 位，正常数精度为 616 位。
 */
public final class Binary640 implements Binary<Binary640> {
    static final int BIAS = 8388607;
    static final int QMIN = -8389221;
    static final long FRACTION_MASK = 0x0000_007f_ffff_ffffL;
    static final long HIDDEN_BIT = 0x0000_0080_0000_0000L;
    private static final long INFINITY_TOP = 0x7fff_ff80_0000_0000L;
    private static final long NAN_TOP = 0x7fff_ffc0_0000_0000L;

    public static final Format FORMAT = new Format(616, -8388606, 8388607);
    public static final Binary640 ZERO = new Binary640(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Binary640 NEGATIVE_ZERO = new Binary640(Long.MIN_VALUE, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Binary640 ONE = new Binary640(0x3fff_ff80_0000_0000L, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Binary640 MIN_VALUE = new Binary640(0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    public static final Binary640 MIN_NORMAL = new Binary640(HIDDEN_BIT, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Binary640 MAX_VALUE = new Binary640(0x7fff_ff7f_ffff_ffffL, -1, -1, -1, -1, -1, -1, -1, -1, -1);
    public static final Binary640 POSITIVE_INFINITY = new Binary640(INFINITY_TOP, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Binary640 NEGATIVE_INFINITY = new Binary640(INFINITY_TOP | Long.MIN_VALUE, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Binary640 NaN = new Binary640(NAN_TOP, 0, 0, 0, 0, 0, 0, 0, 0, 0);

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

    private Binary640(long l0, long l1, long l2, long l3, long l4,
            long l5, long l6, long l7, long l8, long l9) {
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
    }

    /** 按大端序恢复完整编码，保留 NaN 的符号、payload 和 signaling 位。 */
    public static Binary640 fromRawBits(long l0, long l1, long l2, long l3, long l4,
            long l5, long l6, long l7, long l8, long l9) {
        return new Binary640(l0, l1, l2, l3, l4, l5, l6, l7, l8, l9);
    }

    /** 返回一个原始编码分组，索引零为最高组，有效索引为零至九。 */
    public long getRawLong(int index) {
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
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /** 只接受恰好 80 字节的大端序编码，不持有输入数组。 */
    public static Binary640 of(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 80) {
            throw new IllegalArgumentException("bytes must contain exactly 80 elements");
        }
        long[] words = new long[10];
        for (int i = 0; i < bytes.length; i++) {
            words[i / 8] = (words[i / 8] << 8) | (bytes[i] & 0xffL);
        }
        return fromRawBits(words[0], words[1], words[2], words[3], words[4],
                words[5], words[6], words[7], words[8], words[9]);
    }

    @Override
    public byte[] toBytes() {
        byte[] bytes = new byte[80];
        for (int group = 0; group < 10; group++) {
            long word = getRawLong(group);
            for (int i = 0; i < 8; i++) {
                bytes[group * 8 + i] = (byte) (word >>> (56 - i * 8));
            }
        }
        return bytes;
    }

    public static Binary640 of(long value) {
        return Binary640Math.roundDyadic(value < 0, new long[] {value < 0 ? -value : value}, 0);
    }

    public static Binary640 of(float value) {
        int bits = Float.floatToRawIntBits(value);
        return fromPrimitive(bits < 0, (bits >>> 23) & 255, bits & 0x7fffffL, 23, 127, 255);
    }

    public static Binary640 of(double value) {
        long bits = Double.doubleToRawLongBits(value);
        return fromPrimitive(bits < 0, (int) ((bits >>> 52) & 2047), bits & 0xfffffffffffffL, 52, 1023, 2047);
    }

    private static Binary640 fromPrimitive(boolean negative, int exponent, long fraction,
            int fractionBits, int bias, int infinityExponent) {
        if (exponent == infinityExponent) {
            return fraction == 0 ? infinity(negative) : NaN;
        }
        long magnitude = exponent == 0 ? fraction : fraction | (1L << fractionBits);
        long scale = (exponent == 0 ? 1L : exponent) - bias - fractionBits;
        return Binary640Math.roundDyadic(negative, new long[] {magnitude}, scale);
    }

    public static Binary640 of(Int<?> value) {
        Objects.requireNonNull(value);
        boolean negative = value.isNegative();
        int length = value.bitLength();
        if (length > FORMAT.maxExponent() + 1L) {
            return infinity(negative);
        }
        long upperLength = length + 1L;
        // 上界可能比真实幅值多一位，保留 619 位才能保证 sticky 严格低于 guard。
        long dropped = Math.max(upperLength - 619, 0);
        int groups = value.format().bitWidth() / 64;
        int needed = (int) ((upperLength + 63) / 64);
        long[] window = new long[10];
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
                Binary640Math.insert(window, word, offset);
            }
        }
        if (sticky) {
            window[0] |= 1;
        }
        return Binary640Math.roundDyadic(negative, window, dropped);
    }

    public static Binary640 of(Binary<?> value) {
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
        if (value instanceof Binary640 same) {
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
        long dropped = Math.max(length - 618, 0);
        long[] window = new long[10];
        int last = groups - 1 - (int) (dropped / 64);
        long lastWord = top;
        for (int i = first - 1; i <= last; i++) {
            lastWord = i == first - 1 ? top : value.getSignificandLong(i);
            Binary640Math.insert(window, lastWord, (groups - 1L - i) * 64 - dropped);
        }
        // 只在偶数侧中点读取低尾项，避免极大名义精度迫使无关分组参与转换。
        long discarded = Math.max(exponent - 615, QMIN) - scale - dropped;
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
        return Binary640Math.roundDyadic(negative, window, scale + dropped);
    }

    public static Binary640 of(String value) {
        return Binary640Decimal.parse(value);
    }

    public static boolean isValid(String value) {
        return Binary640Decimal.isValid(value);
    }

    @Override
    public Format format() {
        return FORMAT;
    }

    private int encodedExponent() {
        return (int) ((l0 >>> 39) & 0xffffff);
    }

    private boolean emptyFraction() {
        return ((l0 & FRACTION_MASK) | l1 | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9) == 0;
    }

    @Override
    public boolean signBit() {
        return l0 < 0;
    }

    @Override
    public boolean isNaN() {
        return encodedExponent() == 0xffffff && !emptyFraction();
    }

    @Override
    public boolean isInfinite() {
        return encodedExponent() == 0xffffff && emptyFraction();
    }

    @Override
    public boolean isFinite() {
        return encodedExponent() != 0xffffff;
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
        return encoded == 0 ? QMIN : encoded - (long) BIAS - 615;
    }

    @Override
    public long getSignificandLong(int index) {
        requireFinite();
        return index == 0 ? (l0 & FRACTION_MASK) | (encodedExponent() == 0 ? 0 : HIDDEN_BIT) : getRawLong(index);
    }

    static Binary640 zero(boolean negative) {
        return negative ? NEGATIVE_ZERO : ZERO;
    }

    static Binary640 infinity(boolean negative) {
        return negative ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
    }

    @Override
    public Binary640 add(Binary640 other) {
        Objects.requireNonNull(other);
        return Binary640Math.add(this, other, false);
    }

    @Override
    public Binary640 subtract(Binary640 other) {
        Objects.requireNonNull(other);
        return Binary640Math.add(this, other, true);
    }

    @Override
    public Binary640 multiply(Binary640 other) {
        Objects.requireNonNull(other);
        return Binary640Math.multiply(this, other);
    }

    @Override
    public Binary640 divide(Binary640 other) {
        Objects.requireNonNull(other);
        return Binary640Math.divide(this, other);
    }

    @Override
    public Binary640 remainder(Binary640 other) {
        Objects.requireNonNull(other);
        return Binary640Math.remainder(this, other, false);
    }

    @Override
    public Binary640 ieeeRemainder(Binary640 other) {
        Objects.requireNonNull(other);
        return Binary640Math.remainder(this, other, true);
    }

    @Override
    public Binary640 negate() {
        return isNaN() ? NaN : new Binary640(l0 ^ Long.MIN_VALUE, l1, l2, l3, l4, l5, l6, l7, l8, l9);
    }

    @Override
    public Binary640 abs() {
        return isNaN() ? NaN : signBit() ? negate() : this;
    }

    @Override
    public Binary640 fma(Binary640 multiplier, Binary640 addend) {
        Objects.requireNonNull(multiplier);
        Objects.requireNonNull(addend);
        return Binary640Math.fma(this, multiplier, addend);
    }

    @Override
    public Binary640 sqrt() {
        return Binary640Math.sqrt(this);
    }

    @Override
    public Binary640 scalb(long scaleFactor) {
        if (isNaN()) {
            return NaN;
        }
        if (isZero() || isInfinite()) {
            return this;
        }
        long[] magnitude = Binary640Math.magnitude(this);
        long scale = scaleExponent();
        long exponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        if (scaleFactor > FORMAT.maxExponent() - exponent) {
            return infinity(signBit());
        }
        if (scaleFactor < QMIN - 1L - exponent) {
            return zero(signBit());
        }
        return Binary640Math.roundDyadic(signBit(), magnitude, scale + scaleFactor);
    }

    @Override
    public Binary640 ulp() {
        return isNaN() ? NaN : isInfinite() ? POSITIVE_INFINITY : ONE.scalb(scaleExponent());
    }

    @Override
    public Binary640 nextUp() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE;
        }
        return equals(POSITIVE_INFINITY) ? this : step(!signBit());
    }

    @Override
    public Binary640 nextDown() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE.negate();
        }
        return equals(NEGATIVE_INFINITY) ? this : step(signBit());
    }

    private Binary640 step(boolean increment) {
        long[] words = new long[10];
        long delta = increment ? 1 : -1;
        long carry = delta;
        for (int i = 0; i < 10; i++) {
            long word = getRawLong(9 - i);
            words[i] = word + carry;
            carry = carry != 0 && words[i] == (increment ? 0 : -1) ? delta : 0;
        }
        return Binary640Math.raw(words);
    }

    private int compareOrdered(Binary640 other) {
        if (isZero() && other.isZero()) {
            return 0;
        }
        if (signBit() != other.signBit()) {
            return signBit() ? -1 : 1;
        }
        for (int i = 0; i < 10; i++) {
            int comparison = Long.compareUnsigned(getRawLong(i), other.getRawLong(i));
            if (comparison != 0) {
                return signBit() ? -comparison : comparison;
            }
        }
        return 0;
    }

    @Override
    public Comparison compareNumerically(Binary640 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Comparison.UNORDERED;
        }
        int comparison = compareOrdered(other);
        return comparison < 0 ? Comparison.LESS : comparison > 0 ? Comparison.GREATER : Comparison.EQUAL;
    }

    @Override
    public int compareTo(Binary640 other) {
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
        if (!(obj instanceof Binary640 other)) {
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
        for (int i = 1; i < 10; i++) {
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
        long[] magnitude = Binary640Math.magnitude(this);
        int quantum = Math.max(exponent - fractionBits, minimumScale);
        int discarded = quantum - exponent + 615;
        long rounded = Binary640Math.shiftedWord(magnitude, 0, discarded);
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
        long result = Binary640Math.shiftedWord(Binary640Math.magnitude(this), 0, 615 - exponent);
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
        return Binary640Decimal.format(this, false);
    }

    @Override
    public String toString(int fractionDigits) {
        return Binary640Decimal.format(this, false, fractionDigits);
    }

    @Override
    public String toScientificString() {
        return Binary640Decimal.format(this, true);
    }

    @Override
    public String toScientificString(int fractionDigits) {
        return Binary640Decimal.format(this, true, fractionDigits);
    }
}
