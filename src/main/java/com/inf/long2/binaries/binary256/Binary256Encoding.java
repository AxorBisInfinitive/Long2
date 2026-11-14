package com.inf.long2.binaries.binary256;

import com.inf.long2.binaries.Binary256;
import com.inf.long2.binaries.internal.UnsignedWords;

/** Binary256 的原始编码与整数幅值操作，供值类型和实现类共用。 */
public final class Binary256Encoding {
    public static final int BIAS = 262143;
    public static final int QMIN = -262378;
    public static final long FRACTION_MASK = 0x0000_0fff_ffff_ffffL;
    public static final long HIDDEN_BIT = 0x0000_1000_0000_0000L;

    private Binary256Encoding() {
    }

    /** 原始无符号 long 幅值的精确扩展，调用方保证非零值落在 Binary256 的正常范围内。 */
    public static Binary256 fromUnsignedLong(boolean negative, long magnitude, long scale) {
        if (magnitude == 0) {
            return zero(negative);
        }
        int length = 64 - Long.numberOfLeadingZeros(magnitude);
        long top = length <= 45 ? magnitude << (45 - length) : magnitude >>> (length - 45);
        long next = length <= 45 ? 0 : magnitude << (109 - length);
        long encoded = scale + length - 1 + BIAS;
        return Binary256.fromRawBits((negative ? Long.MIN_VALUE : 0) | (encoded << 44) | (top & FRACTION_MASK), next, 0, 0);
    }

    public static int encodedExponent(Binary256 value) {
        return (int) ((value.getRawLong(0) >>> 44) & 0x7ffff);
    }

    public static boolean emptyFraction(Binary256 value) {
        return ((value.getRawLong(0) & FRACTION_MASK) | value.getRawLong(1) | value.getRawLong(2) | value.getRawLong(3)) == 0;
    }

    static long[] magnitude(Binary256 value) {
        return UnsignedWords.trim(new long[] {value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), value.getSignificandLong(0)});
    }

    /** 内部按低位分组读取有限幅值，超出四个分组的部分补零。 */
    public static long magnitudeWord(Binary256 value, int index) {
        return switch (index) {
            case 0 -> value.getRawLong(3);
            case 1 -> value.getRawLong(2);
            case 2 -> value.getRawLong(1);
            case 3 -> (value.getRawLong(0) & FRACTION_MASK) | (encodedExponent(value) == 0 ? 0 : HIDDEN_BIT);
            default -> 0;
        };
    }

    public static Binary256 zero(boolean negative) {
        return negative ? Binary256.NEGATIVE_ZERO : Binary256.ZERO;
    }

    public static Binary256 infinity(boolean negative) {
        return negative ? Binary256.NEGATIVE_INFINITY : Binary256.POSITIVE_INFINITY;
    }
}
