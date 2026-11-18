package love.crysh.long2.ints;

import love.crysh.long2.Binary;

/** 固定补码和无符号幅值均按低组在前存放，方法不裁剪高零组；原地方法仅接受调用方独占的数组。 */
final class IntWords {
    private IntWords() {
    }

    /** 检查有限非零源值的小数部分，幅值小于一时不读取有效数字分组。 */
    static boolean hasFractionalPart(Binary<?> value) {
        if (!value.isNormal()) {
            return true;
        }
        int precision = value.format().precision();
        long scale = value.scaleExponent();
        if (scale + precision - 1L < 0) {
            return true;
        }
        if (scale >= 0) {
            return false;
        }
        int groups = (int) ((precision + 63L) / 64);
        long discarded = -scale;
        int whole = (int) (discarded / 64);
        for (int index = 0; index < whole; index++) {
            if (value.getSignificandLong(groups - 1 - index) != 0) {
                return true;
            }
        }
        int bits = (int) (discarded & 63);
        return bits != 0 && (value.getSignificandLong(groups - 1 - whole) & ((1L << bits) - 1)) != 0;
    }

    /** 在原有位宽内取补码，最终进位丢弃，最小负数的位模式保持不变。 */
    static void negateInPlace(long[] words) {
        long carry = 1;
        for (int index = 0; index < words.length; index++) {
            words[index] = ~words[index] + carry;
            carry = carry != 0 && words[index] == 0 ? 1 : 0;
        }
    }

    /** 按无符号幅值计算，包含最高组的全部 64 位，数组总位宽须可由 int 表示。 */
    static int bitLength(long[] words) {
        for (int index = words.length - 1; index >= 0; index--) {
            if (words[index] != 0) {
                return index * 64 + 64 - Long.numberOfLeadingZeros(words[index]);
            }
        }
        return 0;
    }

    static boolean bit(long[] words, int index) {
        return index >= 0 && (index >>> 6) < words.length
                && (words[index >>> 6] & (1L << (index & 63))) != 0;
    }

    /** 检查低 count 位，非正数量返回 false，超过数组位宽的数量不取模。 */
    static boolean anyLow(long[] words, int count) {
        if (count <= 0) {
            return false;
        }
        int whole = Math.min(count >>> 6, words.length);
        for (int index = 0; index < whole; index++) {
            if (words[index] != 0) {
                return true;
            }
        }
        int bits = count & 63;
        return whole < words.length && bits != 0 && (words[whole] & ((1L << bits) - 1)) != 0;
    }

    /** 返回无符号幅值右移非负 distance 后的低 64 位，超出数组的高位补零。 */
    static long shiftedWord(long[] words, int distance) {
        int group = distance >>> 6, bits = distance & 63;
        if (group >= words.length) {
            return 0;
        }
        long result = words[group] >>> bits;
        if (bits != 0 && group + 1 < words.length) {
            result |= words[group + 1] << (64 - bits);
        }
        return result;
    }

    /**
     * 从只读的完整整数幅值直接舍入为 binary32 或 binary64 编码，零始终返回正零。
     * precision、bias 和 exponentBits 分别使用对应目标格式的参数。
     * sticky 覆盖全部舍弃分组，不经过其他浮点格式。
     */
    static long primitiveBits(long[] magnitude, boolean negative, int precision, int bias, int exponentBits) {
        int length = bitLength(magnitude);
        if (length == 0) {
            return 0;
        }
        int exponent = length - 1, fractionBits = precision - 1;
        long sign = negative ? 1L << (fractionBits + exponentBits) : 0;
        long infinity = ((1L << exponentBits) - 1) << fractionBits;
        if (exponent > bias) {
            return sign | infinity;
        }
        int discarded = exponent - fractionBits;
        long significand;
        if (discarded <= 0) {
            significand = magnitude[0] << -discarded;
        } else {
            significand = shiftedWord(magnitude, discarded);
            if (bit(magnitude, discarded - 1) && (anyLow(magnitude, discarded - 1) || (significand & 1) != 0)) {
                significand++;
            }
            if (significand == (1L << precision)) {
                significand >>>= 1;
                exponent++;
            }
        }
        if (exponent > bias) {
            return sign | infinity;
        }
        return sign | ((long) (exponent + bias) << fractionBits)
                | (significand & ((1L << fractionBits) - 1));
    }
}
