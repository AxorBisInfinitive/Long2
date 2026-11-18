package love.crysh.long2.binaries.binary640;

import love.crysh.long2.binaries.internal.UnsignedWords;

/** Binary640 的原始编码与整数幅值操作，供值类型和实现类共用。 */
final class Binary640Encoding {
    static final int BIAS = 8388607;
    static final int QMIN = -8389221;
    static final long FRACTION_MASK = 0x0000_007f_ffff_ffffL;
    static final long HIDDEN_BIT = 0x0000_0080_0000_0000L;

    private Binary640Encoding() {
    }

    static long[] magnitude(Binary640 value) {
        long[] words = new long[10];
        for (int i = 0; i < words.length; i++) {
            words[i] = value.getSignificandLong(9 - i);
        }
        return UnsignedWords.trim(words);
    }

    static Binary640 raw(long[] lowWords) {
        return Binary640.fromRawBits(lowWords[9], lowWords[8], lowWords[7], lowWords[6], lowWords[5],
                lowWords[4], lowWords[3], lowWords[2], lowWords[1], lowWords[0]);
    }

    static Binary640 zero(boolean negative) {
        return negative ? Binary640.NEGATIVE_ZERO : Binary640.ZERO;
    }

    static Binary640 infinity(boolean negative) {
        return negative ? Binary640.NEGATIVE_INFINITY : Binary640.POSITIVE_INFINITY;
    }
}
