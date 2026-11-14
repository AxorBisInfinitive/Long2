package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.inf.long2.binaries.binary1280.Binary1280DecimalIntegers.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary1280DecimalIntegersTest {
    private static final BigInteger UNIT = BigInteger.ONE;

    @Test
    void unsignedOperationsCrossWordBoundariesAndKeepInputsReadOnly() {
        long[] a = {-1, -1}, b = {1};
        BigInteger allBits = UNIT.shiftLeft(128).subtract(UNIT);
        assertEquals(allBits.add(UNIT), reference(add(a, b)));
        assertEquals(allBits.add(UNIT), reference(increment(a)));
        assertEquals(allBits.multiply(BigInteger.valueOf(1_000_000_000)).add(BigInteger.valueOf(999_999_999)),
                reference(multiplyAdd(a, 1_000_000_000, 999_999_999)));
        assertEquals(130, trailingZeros(new long[] {0, 0, 4}));
        for (int shift : new int[] {0, 1, 63, 64, 65, 127, 128, 129}) {
            assertEquals(allBits.shiftRight(shift), reference(right(a, shift)));
            assertEquals(0, compareDyadic(a, shift, words(allBits.shiftLeft(shift))));
        }
        assertEquals(-1, compareDyadic(a, -134_218_978L, a));
        assertArrayEquals(new long[] {-1, -1}, a);
        assertArrayEquals(new long[] {1}, b);
    }

    @Test
    @Tag("numeric-text-extended")
    void exactProductsCrossAlgorithmThresholdsAndTransformBlocks() {
        Random random = new Random(1280246);
        for (int length : new int[] {1, 47, 48, 49, 1023, 1024, 1025, 2049}) {
            BigInteger a = sample(random, length);
            for (int other : new int[] {1, 48, length, length + 3}) {
                BigInteger b = sample(random, other);
                assertEquals(a.multiply(b), reference(multiply(words(a), words(b))), length + "/" + other);
            }
            long[] words = words(a);
            assertEquals(a.pow(2), reference(multiply(words, words)), Integer.toString(length));
        }
        for (int length : new int[] {0, 1, 15, 16, 17, 31, 32, 33}) {
            BigInteger a = length == 0 ? BigInteger.ZERO : UNIT.shiftLeft(64 * length).subtract(UNIT);
            BigInteger b = sample(random, 19);
            assertEquals(a.multiply(b), reference(Binary1280DecimalTransform.multiply(words(a), words(b), 128)));
            long[] words = words(a);
            assertEquals(a.pow(2), reference(Binary1280DecimalTransform.multiply(words, words, 128)));
        }
        assertEquals(BigInteger.valueOf(5).pow(100_000), reference(powerOfFive(100_000)));
    }

    @Test
    @Tag("numeric-text-extended")
    void divideAndRemainderCrossLeafAndBlockBoundaries() {
        Random random = new Random(1280288);
        for (int length : new int[] {1, 64, 65, 127, 128, 129, 257, 1025}) {
            BigInteger divisor = sample(random, length);
            for (int quotientLength : new int[] {0, 1, 64, 65, 128, 258, length}) {
                BigInteger quotient = quotientLength == 0 ? BigInteger.ZERO : sample(random, quotientLength);
                for (BigInteger remainder : new BigInteger[] {BigInteger.ZERO, divisor.subtract(UNIT)}) {
                    BigInteger numerator = divisor.multiply(quotient).add(remainder);
                    var result = divide(words(numerator), words(divisor));
                    assertEquals(quotient, reference(result.quotient()), length + "/" + quotientLength);
                    assertEquals(remainder, reference(result.remainder()), length + "/" + quotientLength);
                }
            }
        }
        BigInteger divisor = UNIT.shiftLeft(64 * 127).add(UNIT);
        BigInteger quotient = UNIT.shiftLeft(64 * 128).subtract(UNIT);
        BigInteger numerator = divisor.multiply(quotient).add(divisor.subtract(UNIT));
        var result = divide(words(numerator), words(divisor));
        assertEquals(quotient, reference(result.quotient()));
        assertEquals(divisor.subtract(UNIT), reference(result.remainder()));
    }

    @Test
    @Tag("numeric-text-extended")
    void decimalBlocksPreserveLeadingWidthsAndInternalZeros() {
        Random random = new Random(128090);
        assertEquals("0", decimalDigits(ZERO));
        for (int digits : new int[] {9, 18, 72, 144, 576, 2048, 20_000}) {
            BigInteger power = BigInteger.TEN.pow(digits);
            for (BigInteger value : new BigInteger[] {power.subtract(UNIT), power, power.add(UNIT),
                    power.multiply(BigInteger.valueOf(9)).add(sample(random, 4))}) {
                assertEquals(value.toString(), decimalDigits(words(value)));
            }
        }
    }

    private static BigInteger sample(Random random, int length) {
        int bits = 64 * length - random.nextInt(64);
        return new BigInteger(bits, random).setBit(bits - 1);
    }

    private static long[] words(BigInteger value) {
        byte[] source = value.toByteArray();
        long[] words = new long[(value.bitLength() + 63) / 64];
        for (int i = 0; i < Math.min(source.length, 8 * words.length); i++) {
            words[i / 8] |= (source[source.length - 1 - i] & 0xffL) << (8 * (i & 7));
        }
        return words;
    }

    private static BigInteger reference(long[] value) {
        ByteBuffer bytes = ByteBuffer.allocate(value.length * 8);
        for (int i = value.length - 1; i >= 0; i--) {
            bytes.putLong(value[i]);
        }
        return new BigInteger(1, bytes.array());
    }
}
