package love.crysh.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import love.crysh.long2.binaries.internal.UnsignedWords;
import org.junit.jupiter.api.Test;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TWO;
import static java.math.BigInteger.ZERO;
import static love.crysh.long2.binaries.binary1280.Binary1280Encoding.EMAX;
import static love.crysh.long2.binaries.binary1280.Binary1280Encoding.EMIN;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.fromRaw;
import static love.crysh.long2.binaries.binary1280.Binary1280Reference.normal;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Binary1280DivisionTest {
    private static final BigInteger HIDDEN = ONE.shiftLeft(1251);

    @Test
    void fusedShiftMatchesBigIntegerWithPaddingAndWholeWordOffsets() {
        Random random = new Random(0x1280a11L);
        for (int width : new int[] {32, 33, 63, 64, 65, 127, 128, 616, 1252}) {
            BigInteger divisor = new BigInteger(width - 1, random).setBit(width - 1).setBit(0);
            for (BigInteger numerator : new BigInteger[] {ZERO, ONE, divisor.subtract(ONE), divisor, divisor.add(ONE)}) {
                for (int shift = 0; shift <= 1280; shift++) {
                    long[] a = words(numerator, 2), b = words(divisor, 1);
                    var result = UnsignedWords.divideShifted(a, shift, b);
                    var expected = numerator.shiftLeft(shift).divideAndRemainder(divisor);
                    assertArrayEquals(words(expected[0], 0), UnsignedWords.trim(result.quotient()));
                    assertArrayEquals(words(expected[1], 0), UnsignedWords.trim(result.remainder()));
                    assertArrayEquals(words(numerator, 2), a);
                    assertArrayEquals(words(divisor, 1), b);
                }
            }
        }
    }

    @Test
    void denseIntegerDivisionMatchesExactQuotientAndRemainderAtBothDistances() {
        BigInteger almostFull = HIDDEN.shiftLeft(1).subtract(ONE);
        Random random = new Random(0x1280d20L);
        for (BigInteger divisor : new BigInteger[] {HIDDEN.add(ONE), HIDDEN.add(TWO), almostFull,
                new BigInteger(1251, random).setBit(1251)}) {
            for (int distance : new int[] {1251, 1252}) {
                BigInteger minimum = ONE.shiftLeft(divisor.bitLength() + distance - 1);
                // 商的最低组取 2。除数为 HIDDEN + 1 且 delta = -1 时，
                // 低轮试商仍为 2，完整乘减后必须回加一次并修正为 1。
                BigInteger boundary = minimum.divide(divisor).shiftRight(64).add(ONE).shiftLeft(64).add(TWO);
                BigInteger exactProduct = divisor.multiply(boundary);
                for (BigInteger delta : new BigInteger[] {ONE.negate(), ZERO, ONE,
                        divisor.shiftRight(1), divisor.subtract(ONE)}) {
                    BigInteger numerator = exactProduct.add(delta);
                    assertEquals(distance, numerator.bitLength() - divisor.bitLength());
                    assertIntegerDivision(numerator, divisor, 0);
                    assertIntegerDivision(numerator, divisor, 2);
                }
            }
        }
    }

    @Test
    void shortIntegerDivisionChecksAllNormalizationShiftsAndWordBoundaries() {
        Random random = new Random(0x1280d64L);
        for (int bits = 32; bits <= 65; bits++) {
            BigInteger minimum = ONE.shiftLeft(bits - 1);
            for (BigInteger divisor : new BigInteger[] {minimum, minimum.add(ONE),
                    minimum.shiftLeft(1).subtract(ONE), new BigInteger(bits, random).setBit(bits - 1)}) {
                for (int width : new int[] {1, 63, 64, 65, 127, 128, 129, 1252}) {
                    BigInteger quotient = new BigInteger(width, random).setBit(width - 1);
                    for (BigInteger residue : new BigInteger[] {ZERO, ONE, divisor.subtract(ONE)}) {
                        assertIntegerDivision(divisor.multiply(quotient).add(residue), divisor, width % 3);
                    }
                }
            }
        }
    }

    @Test
    void floatDivisionMatchesExactRoundingAcrossFormatsAndOperandWidths() {
        Random random = new Random(0x1280d25L);
        for (int i = 0; i < 600; i++) {
            int exponent = switch (i % 5) {
                case 0 -> EMIN + random.nextInt(2);
                case 1 -> EMAX - random.nextInt(2);
                default -> random.nextInt(-256, 257);
            };
            int otherExponent = random.nextInt(-4, 5);
            BigInteger fraction = new BigInteger(1251, random).setBit(0);
            int width = switch (i % 7) {
                case 0 -> 32;
                case 1 -> 33;
                case 2 -> 64;
                case 3 -> 65;
                case 4 -> 616;
                default -> 1252;
            };
            BigInteger shortFraction = new BigInteger(width - 1, random).setBit(0).shiftLeft(1252 - width);
            Binary1280 a = i % 5 == 2 ? fromRaw(fraction) : normal(random.nextBoolean(), exponent, fraction);
            Binary1280 b = normal(random.nextBoolean(), otherExponent, shortFraction);
            assertRoundedDivision(a, b);
            assertRoundedDivision(b, a);
        }
    }

    @Test
    void floatDivisionPreservesTiesAtZeroAndTheNormalBoundary() {
        Binary1280 two = Binary1280.of(2);
        Binary1280 largestAtMinimumExponent = normal(false, EMIN, HIDDEN.subtract(ONE));
        for (Binary1280 a : new Binary1280[] {Binary1280.MIN_VALUE, fromRaw(BigInteger.valueOf(3)),
                largestAtMinimumExponent.nextDown(), largestAtMinimumExponent, largestAtMinimumExponent.nextUp(),
                Binary1280.MAX_VALUE}) {
            for (Binary1280 b : new Binary1280[] {two.nextDown(), two, two.nextUp(), Binary1280.ONE.nextDown()}) {
                assertRoundedDivision(a, b);
                assertRoundedDivision(a.negate(), b);
            }
        }
        assertArrayEquals(Binary1280.NEGATIVE_ZERO.toBytes(), Binary1280.MIN_VALUE.negate().divide(two).toBytes());
        assertArrayEquals(Binary1280.MIN_NORMAL.toBytes(), largestAtMinimumExponent.divide(two).toBytes());
    }

    private static void assertRoundedDivision(Binary1280 a, Binary1280 b) {
        assertArrayEquals(Binary1280Reference.divide(a, b).toBytes(), a.divide(b).toBytes());
    }

    private static void assertIntegerDivision(BigInteger numerator, BigInteger divisor, int padding) {
        long[] a = words(numerator, padding), b = words(divisor, padding);
        var result = UnsignedWords.divide(a, b);
        var expected = numerator.divideAndRemainder(divisor);
        assertArrayEquals(words(expected[0], 0), result.quotient());
        assertArrayEquals(words(expected[1], 0), result.remainder());
        assertArrayEquals(words(numerator, padding), a);
        assertArrayEquals(words(divisor, padding), b);
    }

    private static long[] words(BigInteger value, int padding) {
        int count = (value.bitLength() + 63) / 64;
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = value.longValue();
            value = value.shiftRight(64);
        }
        return padding == 0 ? result : Arrays.copyOf(result, count + padding);
    }
}
