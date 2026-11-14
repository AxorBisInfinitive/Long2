package com.inf.long2.binaries.binary640;

import java.util.Arrays;

import com.inf.long2.binaries.Binary640;
import com.inf.long2.binaries.internal.UnsignedWords;

import static com.inf.long2.binaries.binary640.Binary640Encoding.BIAS;
import static com.inf.long2.binaries.binary640.Binary640Encoding.FRACTION_MASK;
import static com.inf.long2.binaries.binary640.Binary640Encoding.HIDDEN_BIT;
import static com.inf.long2.binaries.binary640.Binary640Encoding.QMIN;
import static com.inf.long2.binaries.binary640.Binary640Encoding.infinity;
import static com.inf.long2.binaries.binary640.Binary640Encoding.raw;
import static com.inf.long2.binaries.binary640.Binary640Encoding.zero;

/** 将低组在前的整数幅值按最近偶数规则舍入并编码为 Binary640。 */
public final class Binary640Rounding {
    private Binary640Rounding() {
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

    public static Binary640 roundDyadic(boolean negative, long[] magnitude, long scale) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 615, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long[] kept = new long[10];
        for (int i = 0; i < kept.length; i++) {
            kept[i] = shiftedWord(magnitude, i, discarded);
        }
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && ((kept[0] & 1) != 0 || UnsignedWords.anyLow(magnitude, discarded - 1));
        return packRounded(negative, kept, quantum, increment);
    }

    static Binary640 roundRational(boolean negative, long[] numerator, long[] denominator, long scale) {
        if (UnsignedWords.bitLength(numerator) == 0) {
            return zero(negative);
        }
        int ratioExponent = UnsignedWords.bitLength(numerator) - UnsignedWords.bitLength(denominator);
        int comparison = ratioExponent >= 0
                ? UnsignedWords.compareShifted(numerator, denominator, ratioExponent)
                : -UnsignedWords.compareShifted(denominator, numerator, -ratioExponent);
        long exponent = scale + ratioExponent - (comparison < 0 ? 1 : 0);
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 615, QMIN);
        long shift = scale - quantum;
        if (shift >= 0) {
            numerator = UnsignedWords.left(numerator, Math.toIntExact(shift));
        } else {
            denominator = UnsignedWords.left(denominator, Math.toIntExact(-shift));
        }
        UnsignedWords.Division division = UnsignedWords.divide(numerator, denominator);
        int half = -UnsignedWords.compareShifted(denominator, division.remainder(), 1);
        return packRounded(negative, Arrays.copyOf(division.quotient(), 10), quantum,
                half > 0 || half == 0 && UnsignedWords.bit(division.quotient(), 0));
    }

    /** kept 独占且至少十组，至多 616 位，额外高组为零；舍入与归一化在最终位权完成。 */
    static Binary640 packRounded(boolean negative, long[] kept, long quantum, boolean increment) {
        if (increment) {
            for (int i = 0; i < kept.length; i++) {
                if (++kept[i] != 0) {
                    break;
                }
            }
        }
        if ((kept[9] & (HIDDEN_BIT << 1)) != 0) {
            for (int i = 0; i < 9; i++) {
                kept[i] = (kept[i] >>> 1) | (kept[i + 1] << 63);
            }
            kept[9] >>>= 1;
            quantum++;
        }
        int length = UnsignedWords.bitLength(kept);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = quantum + length - 1;
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        long encoded = exponent < Binary640.FORMAT.minExponent() ? 0 : exponent + BIAS;
        kept[9] = (negative ? Long.MIN_VALUE : 0) | (encoded << 39) | (kept[9] & FRACTION_MASK);
        return raw(kept);
    }

    /** 独占工作区至少十组，先读取全部舍入信息，再按安全方向原地整理。 */
    static Binary640 roundOwnedDyadic(boolean negative, long[] magnitude, long scale) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 615, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && (UnsignedWords.bit(magnitude, discarded) || UnsignedWords.anyLow(magnitude, discarded - 1));
        if (discarded > 0) {
            for (int i = 0; i < 10; i++) {
                magnitude[i] = shiftedWord(magnitude, i, discarded);
            }
        } else if (discarded < 0) {
            for (int i = 9; i >= 0; i--) {
                magnitude[i] = shiftedWord(magnitude, i, discarded);
            }
        }
        // 原高组已参与移位，必须在全部目标组写完后清除，避免被 packRounded 重新视作有效位。
        Arrays.fill(magnitude, 10, magnitude.length, 0);
        return packRounded(negative, magnitude, quantum, increment);
    }

    /**
     * product 是至少十组的独占完整乘积。先读取全部舍入信息，再整理低十组。
     * 正常结果已知保留 616 位；低端结果在 QMIN 位权上的整数直接对应零、次正规或最低正常数编码。
     * 高组不再参与结果编码，无须清零或重新扫描位长。
     */
    static Binary640 roundProduct(boolean negative, long[] product, long scale) {
        int length = UnsignedWords.bitLength(product);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        boolean lowRange = exponent <= Binary640.FORMAT.minExponent();
        long quantum = lowRange ? QMIN : exponent - 615;
        int discarded = Math.toIntExact(quantum - scale);
        boolean increment = discarded > 0 && UnsignedWords.bit(product, discarded - 1)
                && (UnsignedWords.bit(product, discarded) || UnsignedWords.anyLow(product, discarded - 1));
        if (discarded > 0) {
            for (int i = 0; i < 10; i++) {
                product[i] = shiftedWord(product, i, discarded);
            }
        } else if (discarded < 0) {
            for (int i = 9; i >= 0; i--) {
                product[i] = shiftedWord(product, i, discarded);
            }
        }
        if (increment) {
            for (int i = 0; i < 10; i++) {
                if (++product[i] != 0) {
                    break;
                }
            }
        }
        if (lowRange) {
            // 舍入整数至多为 2^616，该端点直接编码为第二个正常 binade 的起点。
            product[9] |= negative ? Long.MIN_VALUE : 0;
            return raw(product);
        }
        if ((product[9] & (HIDDEN_BIT << 1)) != 0) {
            for (int i = 0; i < 9; i++) {
                product[i] = (product[i] >>> 1) | (product[i + 1] << 63);
            }
            product[9] >>>= 1;
            exponent++;
            if (exponent > Binary640.FORMAT.maxExponent()) {
                return infinity(negative);
            }
        }
        product[9] = (negative ? Long.MIN_VALUE : 0) | ((exponent + BIAS) << 39)
                | (product[9] & FRACTION_MASK);
        return raw(product);
    }

    /** 有限非零幅值至多有九个低零组，最高组不得将编码字段当作有效位。 */
    /** 非零尾项小于乘积网格间距，只在精确中点决定向哪一侧舍入。 */
    static Binary640 productWithTail(boolean negative, long[] product, long scale, long exponent, boolean increases) {
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return infinity(negative);
        }
        int discarded = UnsignedWords.bitLength(product) - 616;
        if (discarded > 0 && UnsignedWords.bit(product, discarded - 1) && !UnsignedWords.anyLow(product, discarded - 1)) {
            long[] kept = new long[10];
            for (int i = 0; i < kept.length; i++) {
                kept[i] = shiftedWord(product, i, discarded);
            }
            return packRounded(negative, kept, scale + discarded, increases);
        }
        return roundDyadic(negative, product, scale);
    }
}
