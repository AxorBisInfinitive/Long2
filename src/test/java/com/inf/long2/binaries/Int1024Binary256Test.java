package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Int;
import com.inf.long2.ints.Int1024;

class Int1024Binary256Test {
    @Test
    void roundsFullWidthIntegersAgainstExactRationalReference() {
        Random random = new Random(0x1024B256L);
        for (int i = 0; i < 1200; i++) {
            byte[] bytes = new byte[128];
            random.nextBytes(bytes);
            Int1024 integer = Int1024.of(bytes);
            check(integer);
            assertArrayEquals(bytes, integer.toBytes());
        }
        for (Int1024 value : List.of(Int1024.ZERO, Int1024.ONE, Int1024.of(-1L), Int1024.MIN_VALUE, Int1024.MAX_VALUE)) {
            check(value);
        }
    }

    @Test
    void roundsBothSignsAtEveryRelevantIntegerMidpoint() {
        BigInteger one = BigInteger.ONE;
        for (int exponent = 237; exponent <= 1022; exponent++) {
            int discarded = exponent - 236;
            for (boolean odd : new boolean[] {false, true}) {
                BigInteger kept = one.shiftLeft(236).add(odd ? one : BigInteger.ZERO);
                BigInteger midpoint = kept.shiftLeft(discarded).add(one.shiftLeft(discarded - 1));
                for (int delta : new int[] {-1, 0, 1}) {
                    BigInteger value = midpoint.add(BigInteger.valueOf(delta));
                    check(Int1024.parseInt(value.toString()));
                    check(Int1024.parseInt(value.negate().toString()));
                }
            }
        }
    }

    @Test
    void roundTripsIntegersWithAtMost237SignificantBits() {
        Random random = new Random(0x1024E7AC7L);
        for (int i = 0; i < 400; i++) {
            BigInteger magnitude = new BigInteger(237, random).shiftLeft(random.nextInt(787));
            BigInteger value = random.nextBoolean() ? magnitude : magnitude.negate();
            Int1024 integer = Int1024.parseInt(value.toString());
            assertEquals(integer, Int1024.of(Binary256.of(integer)));
        }
    }

    private static void check(Int1024 integer) {
        BigInteger value = new BigInteger(integer.toBytes());
        Binary256 expected = Binary256Reference.round(value.signum() < 0, value.abs(), BigInteger.ONE, 0);
        assertArrayEquals(expected.toBytes(), Binary256.of((Int<?>) integer).toBytes());
    }
}
