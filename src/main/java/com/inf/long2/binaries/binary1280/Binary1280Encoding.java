package com.inf.long2.binaries.binary1280;

import com.inf.long2.binaries.Binary1280;

public final class Binary1280Encoding {
    public static final int WORDS = 20;
    public static final int PRECISION = 1252;
    public static final int FRACTION_BITS = PRECISION - 1;
    public static final int TOP_FRACTION_BITS = 35;
    public static final int EXPONENT_MASK = 0x0fff_ffff;
    public static final int BIAS = 134217727;
    public static final int EMIN = 1 - BIAS;
    public static final int EMAX = BIAS;
    public static final int QMIN = EMIN - FRACTION_BITS;
    public static final long HIDDEN_BIT = 1L << TOP_FRACTION_BITS;
    public static final long FRACTION_MASK = HIDDEN_BIT - 1;

    private Binary1280Encoding() {
    }

    public static long[] magnitude(Binary1280 value) {
        long[] words = new long[WORDS];
        words[WORDS - 1] = value.getSignificandLong(0);
        for (int i = 0; i < WORDS - 1; i++) {
            words[i] = value.getRawLong(WORDS - 1 - i);
        }
        return words;
    }

    public static Binary1280 zero(boolean negative) {
        return negative ? Binary1280.NEGATIVE_ZERO : Binary1280.ZERO;
    }

    public static Binary1280 infinity(boolean negative) {
        return negative ? Binary1280.NEGATIVE_INFINITY : Binary1280.POSITIVE_INFINITY;
    }

    static Binary1280 raw(long[] words) {
        return Binary1280.fromRawBits(words[19], words[18], words[17], words[16], words[15],
                words[14], words[13], words[12], words[11], words[10],
                words[9], words[8], words[7], words[6], words[5],
                words[4], words[3], words[2], words[1], words[0]);
    }
}
