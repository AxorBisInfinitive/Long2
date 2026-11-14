package com.inf.long2.binaries.binary1280;

import java.util.Arrays;

import com.inf.long2.binaries.Binary1280;
import com.inf.long2.binaries.internal.UnsignedWords;

import static com.inf.long2.binaries.binary1280.Binary1280Encoding.*;

/** 将低组在前的整数幅值按最近偶数规则舍入并编码为 Binary1280。 */
public final class Binary1280Rounding {
    private Binary1280Rounding() {
    }

    /** 将一个源分组按位偏移并入独占窗口，窗口外的位丢弃。 */
    public static void insert(long[] target, long word, long offset) {
        if (offset < 0) {
            if (offset > -64) {
                target[0] |= word >>> (int) -offset;
            }
            return;
        }
        long group = offset / 64;
        int bits = (int) (offset & 63);
        if (group < target.length) {
            target[(int) group] |= word << bits;
        }
        if (bits != 0 && group + 1 < target.length) {
            target[(int) group + 1] |= word >>> (64 - bits);
        }
    }

    /** distance 为正时右移，为负时左移，范围外的源分组补零。 */
    public static long shiftedWord(long[] magnitude, int index, int distance) {
        int group = index + (distance >> 6), bits = distance & 63;
        long low = group < 0 ? 0 : UnsignedWords.word(magnitude, group);
        if (bits == 0) {
            return low;
        }
        long high = group < -1 ? 0 : UnsignedWords.word(magnitude, group + 1);
        return (low >>> bits) | (high << (64 - bits));
    }

    public static Binary1280 roundDyadic(boolean negative, long[] magnitude, long scale) {
        return roundDyadic(negative, magnitude, scale, 0);
    }

    /** tailDirection 只在精确中点决定幅值增减。调用方保证尾项无法跨过非中点与舍入边界之间的距离。 */
    static Binary1280 roundDyadic(boolean negative, long[] magnitude, long scale, int tailDirection) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > Binary1280.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - FRACTION_BITS, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long[] kept = new long[WORDS];
        for (int i = 0; i < kept.length; i++) {
            kept[i] = shiftedWord(magnitude, i, discarded);
        }
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && (UnsignedWords.anyLow(magnitude, discarded - 1)
                        || (tailDirection == 0 ? (kept[0] & 1) != 0 : tailDirection > 0));
        return packRounded(negative, kept, quantum, increment);
    }

    static Binary1280 roundRational(boolean negative, long[] numerator, long[] denominator, long scale) {
        if (UnsignedWords.bitLength(numerator) == 0) {
            return zero(negative);
        }
        int ratioExponent = UnsignedWords.bitLength(numerator) - UnsignedWords.bitLength(denominator);
        int comparison = ratioExponent >= 0
                ? UnsignedWords.compareShifted(numerator, denominator, ratioExponent)
                : -UnsignedWords.compareShifted(denominator, numerator, -ratioExponent);
        long exponent = scale + ratioExponent - (comparison < 0 ? 1 : 0);
        if (exponent > Binary1280.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - FRACTION_BITS, QMIN);
        long shift = scale - quantum;
        if (shift >= 0) {
            numerator = UnsignedWords.left(numerator, Math.toIntExact(shift));
        } else {
            denominator = UnsignedWords.left(denominator, Math.toIntExact(-shift));
        }
        UnsignedWords.Division division = UnsignedWords.divide(numerator, denominator);
        int half = -UnsignedWords.compareShifted(denominator, division.remainder(), 1);
        return packRounded(negative, Arrays.copyOf(division.quotient(), WORDS), quantum,
                half > 0 || half == 0 && UnsignedWords.bit(division.quotient(), 0));
    }

    /** kept 独占且恰好二十组，有效数字至多 1252 位。舍入与归一化在最终位权完成。 */
    static Binary1280 packRounded(boolean negative, long[] kept, long quantum, boolean increment) {
        if (increment) {
            for (int i = 0; i < kept.length; i++) {
                if (++kept[i] != 0) {
                    break;
                }
            }
        }
        if ((kept[WORDS - 1] & (HIDDEN_BIT << 1)) != 0) {
            for (int i = 0; i < WORDS - 1; i++) {
                kept[i] = (kept[i] >>> 1) | (kept[i + 1] << 63);
            }
            kept[WORDS - 1] >>>= 1;
            quantum++;
        }
        int length = UnsignedWords.bitLength(kept);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = quantum + length - 1;
        if (exponent > Binary1280.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        long encoded = exponent < Binary1280.FORMAT.minExponent() ? 0 : exponent + BIAS;
        kept[WORDS - 1] = (negative ? Long.MIN_VALUE : 0) | (encoded << TOP_FRACTION_BITS) | (kept[WORDS - 1] & FRACTION_MASK);
        return raw(kept);
    }
}
