package com.inf.long2.ints;

import java.math.BigInteger;

final class IntSupportTestSupport {
    private IntSupportTestSupport() {
    }

    static long[] words(BigInteger value, int count) {
        long[] words = new long[count];
        for (int index = 0; index < count; index++) {
            words[index] = value.shiftRight(index * 64).longValue();
        }
        return words;
    }

    static BigInteger unsigned(long[] words) {
        BigInteger result = BigInteger.ZERO;
        for (int index = words.length - 1; index >= 0; index--) {
            result = result.shiftLeft(64).add(new BigInteger(Long.toUnsignedString(words[index])));
        }
        return result;
    }
}
