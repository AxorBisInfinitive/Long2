package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Int;
import com.inf.long2.ints.Int512;

class Int512Binary256Test {
    @Test
    void roundsFullWidthIntegersAgainstExactRationalReference() {
        Random random = new Random(0x512B256L);
        for (int i = 0; i < 1200; i++) {
            byte[] bytes = new byte[64];
            random.nextBytes(bytes);
            Int512 integer = Int512.of(bytes);
            check(integer);
            assertArrayEquals(bytes, integer.toBytes());
        }
        for (Int512 value : List.of(Int512.ZERO, Int512.ONE, Int512.of(-1L), Int512.MIN_VALUE, Int512.MAX_VALUE)) {
            check(value);
        }
    }

    @Test
    void roundsBothSignsAtEveryRelevantIntegerMidpoint() {
        BigInteger one = BigInteger.ONE;
        for (int exponent = 237; exponent <= 510; exponent++) {
            int discarded = exponent - 236;
            for (boolean odd : new boolean[] {false, true}) {
                BigInteger kept = one.shiftLeft(236).add(odd ? one : BigInteger.ZERO);
                BigInteger midpoint = kept.shiftLeft(discarded).add(one.shiftLeft(discarded - 1));
                for (int delta : new int[] {-1, 0, 1}) {
                    BigInteger value = midpoint.add(BigInteger.valueOf(delta));
                    check(Int512.parseInt(value.toString()));
                    check(Int512.parseInt(value.negate().toString()));
                }
            }
        }
    }

    @Test
    void roundTripsIntegersWithAtMost237SignificantBits() {
        Random random = new Random(0x512E7AC7L);
        for (int i = 0; i < 400; i++) {
            BigInteger magnitude = new BigInteger(237, random).shiftLeft(random.nextInt(275));
            BigInteger value = random.nextBoolean() ? magnitude : magnitude.negate();
            Int512 integer = Int512.parseInt(value.toString());
            assertEquals(integer, Int512.of(Binary256.of(integer)));
        }
    }

    private static void check(Int512 integer) {
        BigInteger value = new BigInteger(integer.toBytes());
        Binary256 expected = Binary256Reference.round(value.signum() < 0, value.abs(), BigInteger.ONE, 0);
        assertArrayEquals(expected.toBytes(), Binary256.of((Int<?>) integer).toBytes());
    }
}
