package com.inf.long2.binaries.binary256;

import com.inf.long2.binaries.Binary256;
import com.inf.long2.binaries.internal.UnsignedWords;

import static com.inf.long2.binaries.Binary256.FORMAT;
import static com.inf.long2.binaries.binary256.Binary256Encoding.BIAS;
import static com.inf.long2.binaries.binary256.Binary256Encoding.FRACTION_MASK;
import static com.inf.long2.binaries.binary256.Binary256Encoding.HIDDEN_BIT;
import static com.inf.long2.binaries.binary256.Binary256Encoding.QMIN;
import static com.inf.long2.binaries.binary256.Binary256Encoding.infinity;
import static com.inf.long2.binaries.binary256.Binary256Encoding.zero;

/** 将低组在前的整数幅值按最近偶数规则舍入并编码为 Binary256。 */
public final class Binary256Rounding {
    private Binary256Rounding() {
    }

    /** 最终位权同时考虑精度和次正规下界，只在该位权上舍入一次。 */
    public static Binary256 fromDyadic(boolean negative, long[] magnitude, long scale) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long low = shiftedWord(magnitude, 0, discarded);
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && ((low & 1) != 0 || UnsignedWords.anyLow(magnitude, discarded - 1));
        return packRounded(negative, low, shiftedWord(magnitude, 1, discarded),
                shiftedWord(magnitude, 2, discarded), shiftedWord(magnitude, 3, discarded), quantum, increment);
    }

    /** 两个低组在前的 long 保存非零完整乘积，不分配幅值数组；次正规边界仍在最终位权上舍入。 */
    static Binary256 fromDyadic(boolean negative, long w0, long w1, long scale) {
        int length = bitLength(w0, w1, 0, 0);
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long low = shiftedWord(w0, w1, 0, 0, 0, discarded);
        boolean increment = false;
        if (discarded > 0) {
            int guard = discarded - 1;
            long word = guard < 64 ? w0 : w1;
            if ((word & (1L << (guard & 63))) != 0) {
                // 已排除低于半个 MIN_VALUE 的输入，guard 不超过 127；两侧掩码的移位均小于 64。
                increment = (low & 1) != 0 || (guard < 64
                        ? (w0 & ((1L << guard) - 1)) != 0
                        : w0 != 0 || (w1 & ((1L << (guard - 64)) - 1)) != 0);
            }
        }
        return packRounded(negative, low, shiftedWord(w0, w1, 0, 0, 1, discarded),
                shiftedWord(w0, w1, 0, 0, 2, discarded), shiftedWord(w0, w1, 0, 0, 3, discarded), quantum, increment);
    }

    /** 直接舍入 numerator / denominator * 2^scale，整数商和余数共同决定最近偶数方向。 */
    static Binary256 roundRational(boolean negative, long[] numerator, long[] denominator, long scale) {
        int ratioExponent = UnsignedWords.bitLength(numerator) - UnsignedWords.bitLength(denominator);
        int comparison = ratioExponent >= 0
                ? UnsignedWords.compare(numerator, UnsignedWords.left(denominator, ratioExponent))
                : UnsignedWords.compare(UnsignedWords.left(numerator, -ratioExponent), denominator);
        long exponent = scale + ratioExponent - (comparison < 0 ? 1 : 0);
        if (exponent > 262143) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        long alignment = scale - quantum;
        if (alignment >= 0) {
            numerator = UnsignedWords.left(numerator, Math.toIntExact(alignment));
        } else {
            denominator = UnsignedWords.left(denominator, Math.toIntExact(-alignment));
        }
        UnsignedWords.Division division = UnsignedWords.divide(numerator, denominator);
        long[] result = division.quotient();
        int half = UnsignedWords.compare(UnsignedWords.left(division.remainder(), 1), denominator);
        return packRounded(negative, UnsignedWords.word(result, 0), UnsignedWords.word(result, 1),
                UnsignedWords.word(result, 2), UnsignedWords.word(result, 3), quantum,
                half > 0 || (half == 0 && UnsignedWords.bit(result, 0)));
    }

    /** distance 为正时右移，为负时左移；幅值保持只读，范围外的分组补零。 */
    static long shiftedWord(long[] magnitude, int index, int distance) {
        int group = index + (distance >> 6), bits = distance & 63;
        long low = group < 0 ? 0 : UnsignedWords.word(magnitude, group);
        if (bits == 0) {
            return low;
        }
        long high = group < -1 ? 0 : UnsignedWords.word(magnitude, group + 1);
        return (low >>> bits) | (high << (64 - bits));
    }

    public static long shiftedWord(long w0, long w1, long w2, long w3, int index, int distance) {
        int group = index + (distance >> 6), bits = distance & 63;
        long low = fourWord(w0, w1, w2, w3, group);
        return bits == 0 ? low : (low >>> bits) | (fourWord(w0, w1, w2, w3, group + 1) << (64 - bits));
    }

    static long fourWord(long w0, long w1, long w2, long w3, int index) {
        return switch (index) {
            case 0 -> w0;
            case 1 -> w1;
            case 2 -> w2;
            case 3 -> w3;
            default -> 0;
        };
    }

    public static int bitLength(long w0, long w1, long w2, long w3) {
        return w3 != 0 ? 256 - Long.numberOfLeadingZeros(w3)
                : w2 != 0 ? 192 - Long.numberOfLeadingZeros(w2)
                : w1 != 0 ? 128 - Long.numberOfLeadingZeros(w1) : 64 - Long.numberOfLeadingZeros(w0);
    }

    /** 幅值最多 237 位；increment 表示最终舍入加一，额外进位归一化后再判断上溢。 */
    public static Binary256 packRounded(boolean negative, long w0, long w1, long w2, long w3,
            long scale, boolean increment) {
        if (increment && ++w0 == 0 && ++w1 == 0 && ++w2 == 0) {
            w3++;
        }
        if ((w3 & (HIDDEN_BIT << 1)) != 0) {
            w0 = (w0 >>> 1) | (w1 << 63);
            w1 = (w1 >>> 1) | (w2 << 63);
            w2 = (w2 >>> 1) | (w3 << 63);
            w3 >>>= 1;
            scale++;
        }
        int length = bitLength(w0, w1, w2, w3);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        long encoded = exponent < FORMAT.minExponent() ? 0 : exponent + BIAS;
        return Binary256.fromRawBits((negative ? Long.MIN_VALUE : 0) | (encoded << 44)
                | (w3 & FRACTION_MASK), w2, w1, w0);
    }

    static Binary256 roundProductWithTail(boolean negative, long[] product, long scale, long exponent,
            boolean magnitudeIncreases) {
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        // 加数非零且指数至少低 475，乘积必为正常范围；其与非中点舍入边界的距离大于尾项。
        int discarded = UnsignedWords.bitLength(product) - 237;
        if (discarded > 0 && UnsignedWords.bit(product, discarded - 1) && !UnsignedWords.anyLow(product, discarded - 1)) {
            // 精确中点处，尾项使绝对值增大就选上界，减小就选下界，不再按保留位奇偶处理。
            return packRounded(negative, shiftedWord(product, 0, discarded), shiftedWord(product, 1, discarded),
                    shiftedWord(product, 2, discarded), shiftedWord(product, 3, discarded), scale + discarded, magnitudeIncreases);
        }
        return fromDyadic(negative, product, scale);
    }
}
