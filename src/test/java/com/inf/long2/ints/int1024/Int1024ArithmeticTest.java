package com.inf.long2.ints.int1024;

import static com.inf.long2.ints.int1024.Int1024TestSupport.MAXIMUM;
import static com.inf.long2.ints.int1024.Int1024TestSupport.MINIMUM;
import static com.inf.long2.ints.int1024.Int1024TestSupport.MODULUS;
import static com.inf.long2.ints.int1024.Int1024TestSupport.ONE;
import static com.inf.long2.ints.int1024.Int1024TestSupport.assertNumber;
import static com.inf.long2.ints.int1024.Int1024TestSupport.from;
import static com.inf.long2.ints.int1024.Int1024TestSupport.number;
import static com.inf.long2.ints.int1024.Int1024TestSupport.random;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

class Int1024ArithmeticTest {
    @Test
    void exposesTheSignedFormatAndWrapsAtBothExtremes() {
        assertEquals(1024, Int1024.FORMAT.bitWidth());
        assertEquals(Int1024.FORMAT, Int1024.ZERO.format());
        assertNumber(MINIMUM, Int1024.MIN_VALUE);
        assertNumber(MAXIMUM, Int1024.MAX_VALUE);
        assertEquals(Int1024.MIN_VALUE, Int1024.MAX_VALUE.successor());
        assertEquals(Int1024.MAX_VALUE, Int1024.MIN_VALUE.subtract(Int1024.ONE));
        assertEquals(Int1024.MIN_VALUE, Int1024.MIN_VALUE.negate());
        assertEquals(Int1024.ZERO, Int1024.MIN_VALUE.add(Int1024.MIN_VALUE));
        assertEquals(Int1024.ONE, Int1024.of(-1L).multiply(Int1024.of(-1L)));
        assertEquals(Int1024.ZERO, Int1024.ONE.shift(512).multiply(Int1024.ONE.shift(512)));
        assertEquals(Int1024.MIN_VALUE, Int1024.MIN_VALUE.multiply(Int1024.of(-1L)));
    }

    @Test
    void propagatesCarriesBorrowsAndProductsAcrossEveryWord() {
        List<BigInteger> values = new ArrayList<>(List.of(BigInteger.ZERO, ONE, ONE.negate(), MINIMUM, MAXIMUM));
        for (int bit = 32; bit < 1024; bit += 32) {
            BigInteger power = ONE.shiftLeft(bit);
            for (int delta : new int[] {-1, 0, 1}) {
                values.add(power.add(BigInteger.valueOf(delta)));
                values.add(power.add(BigInteger.valueOf(delta)).negate());
            }
        }
        for (BigInteger a : values) {
            Int1024 x = from(a);
            assertNumber(a.negate(), x.negate());
            assertNumber(a.add(ONE), x.successor());
            for (BigInteger b : values) {
                checkPair(x, from(b));
            }
        }
    }

    @Test
    void matchesBigIntegerForRandomArithmeticBitwiseOperationsAndOrdering() {
        Random random = new Random(0x1024A117L);
        for (int i = 0; i < 3000; i++) {
            Int1024 a = random(random), b = random(random);
            byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
            checkPair(a, b);
            BigInteger value = number(a);
            assertNumber(value.and(number(b)), a.and(b));
            assertNumber(value.or(number(b)), a.or(b));
            assertNumber(value.xor(number(b)), a.xor(b));
            assertNumber(value.not(), a.not());
            assertNumber(value.negate(), a.negate());
            assertNumber(value.add(ONE), a.successor());
            assertEquals(value.signum(), a.signum());
            assertEquals(value.signum() < 0, a.isNegative());
            assertEquals(value.signum() == 0, a.isZero());
            assertEquals(value.bitLength(), a.bitLength());
            assertEquals(value.mod(MODULUS).bitCount(), a.bitCount());
            int bit = random.nextInt(1024);
            assertEquals(value.testBit(bit), a.testBit(bit));
            assertArrayEquals(beforeA, a.toBytes());
            assertArrayEquals(beforeB, b.toBytes());
        }
    }

    @Test
    void shiftsAllDistancesWithModuloAndSignExtension() {
        List<Int1024> values = List.of(Int1024.ZERO, Int1024.ONE, Int1024.of(-1L), Int1024.of(-2L),
                Int1024.MIN_VALUE, Int1024.MAX_VALUE, random(new Random(0x1024B17L)));
        for (Int1024 value : values) {
            for (int distance = -2049; distance <= 2049; distance++) {
                checkShift(value, distance);
            }
            for (int distance : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE, -65536, 65536}) {
                checkShift(value, distance);
            }
            assertSame(value, value.shift(0));
            assertSame(value, value.shift(1024));
            assertSame(value, value.shift(-1024));
            assertSame(value, value.shift(Integer.MIN_VALUE));
        }
        for (int bit = 0; bit < 1024; bit++) {
            Int1024 value = from(ONE.shiftLeft(bit));
            assertEquals(1, value.bitCount());
            for (int index = 0; index < 1024; index++) {
                assertEquals(index == bit, value.testBit(index));
            }
            for (int distance : new int[] {-1023, -961, -960, -513, -512, -511, -449, -448, -385, -384, -257, -256, -65, -64,
                    -63, -1, 1, 63, 64, 65, 256, 257, 384, 385, 448, 449, 511, 512, 513, 960, 961, 1023}) {
                checkShift(value, distance);
            }
        }
        assertEquals(0, Int1024.of(-1L).bitLength());
        assertEquals(1024, Int1024.of(-1L).bitCount());
        assertEquals(1023, Int1024.MIN_VALUE.bitLength());
    }

    @Test
    void preservesLowHalfProductHighBitsAndCrossHalfCarries() {
        BigInteger half = ONE.shiftLeft(512);
        Int1024 low = from(half.subtract(ONE)), high = from(half);
        assertEquals(high, from(ONE.shiftLeft(256)).multiply(from(ONE.shiftLeft(256))));
        assertEquals(high, low.successor());
        assertEquals(low, high.subtract(Int1024.ONE));
        assertEquals(Int1024.ONE, high.shift(-512));
        assertEquals(high, Int1024.ONE.shift(512));
        for (BigInteger a : List.of(half.subtract(ONE), half, half.add(ONE), half.negate(),
                ONE.shiftLeft(960), ONE.shiftLeft(1023), MAXIMUM)) {
            for (BigInteger b : List.of(half.subtract(ONE), half, half.add(ONE), ONE.negate(), MAXIMUM)) {
                checkPair(from(a), from(b));
            }
        }
    }

    @Test
    void accumulatesDensePartialProductsIncludingTopWordOverflow() {
        long[] patterns = {0, 1, -1, -2, Long.MIN_VALUE, Long.MAX_VALUE,
                0xaaaa_aaaa_aaaa_aaaaL, 0x5555_5555_5555_5555L};
        for (long first : patterns) {
            for (long second : patterns) {
                for (int phase = 0; phase < 16; phase++) {
                    long[] a = new long[16], b = new long[16];
                    for (int index = 0; index < 16; index++) {
                        a[index] = (index & 1) == 0 ? first : second;
                        b[index] = index == phase ? second : first;
                    }
                    checkPair(Int1024.of(a), Int1024.of(b));
                }
            }
        }
    }

    @Test
    void placesUnsignedWordProductsOnBothSidesOfTheKernelBoundary() {
        BigInteger wordMask = ONE.shiftLeft(64).subtract(ONE);
        long[] patterns = {1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (int first = 0; first < 16; first++) {
            for (int second = 0; second < 16; second++) {
                for (long a : patterns) {
                    for (long b : patterns) {
                        BigInteger x = BigInteger.valueOf(a).and(wordMask).shiftLeft(first * 64);
                        BigInteger y = BigInteger.valueOf(b).and(wordMask).shiftLeft(second * 64);
                        assertNumber(x.multiply(y), from(x).multiply(from(y)));
                    }
                }
            }
        }
    }

    @Test
    void transfersMultiBitColumnCarryAcrossThe512BitBoundary() {
        BigInteger half = ONE.shiftLeft(512), halfMask = half.subtract(ONE);
        List<BigInteger> lows = List.of(halfMask, halfMask.subtract(ONE), ONE.shiftLeft(511), ONE);
        List<BigInteger> highs = List.of(BigInteger.ZERO, ONE, halfMask, ONE.shiftLeft(511));
        for (BigInteger lowA : lows) {
            for (BigInteger lowB : lows) {
                for (BigInteger highA : highs) {
                    for (BigInteger highB : highs) {
                        Int1024 a = from(highA.shiftLeft(512).or(lowA));
                        Int1024 b = from(highB.shiftLeft(512).or(lowB));
                        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
                        BigInteger expected = number(a).multiply(number(b));
                        assertNumber(expected, a.multiply(b));
                        assertNumber(expected, b.multiply(a));
                        assertNumber(number(a).pow(2), a.multiply(a));
                        assertArrayEquals(beforeA, a.toBytes());
                        assertArrayEquals(beforeB, b.toBytes());
                    }
                }
            }
        }
        // 低 512 位全一相乘时，进入第 8 列的进位为 7，而不是一个 bit。
        assertNumber(halfMask.multiply(halfMask), from(halfMask).multiply(from(halfMask)));
        assertThrowsExactly(NullPointerException.class, () -> Int1024.ZERO.multiply(null));
    }

    @Test
    void multipliesOddAndEvenUnsignedWordsAroundTheSignBit() {
        long[] words = {0L, 1L, 2L, 3L, Long.MAX_VALUE - 1, Long.MAX_VALUE,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, -3L, -2L, -1L};
        for (int adjacent = 0; adjacent < 15; adjacent++) {
            for (long first : words) {
                for (long second : words) {
                    long[] a = new long[16], b = new long[16];
                    for (int index = 0; index < 16; index++) {
                        a[index] = -1L;
                        b[index] = -1L;
                    }
                    a[adjacent] = first;
                    a[adjacent + 1] = second;
                    b[adjacent] = second;
                    b[adjacent + 1] = first;
                    Int1024 x = Int1024.of(a), y = Int1024.of(b);
                    BigInteger expected = number(x).multiply(number(y));
                    assertNumber(expected, x.multiply(y));
                    assertNumber(expected, y.multiply(x));
                }
            }
        }
    }

    @Test
    void normalizesDenseColumnSumsBeforePassingTheirCarries() {
        assertEquals(Int1024.ONE, Int1024.of(-1L).multiply(Int1024.of(-1L)));
        for (int lowWords = 2; lowWords <= 16; lowWords++) {
            BigInteger dense = ONE.shiftLeft(lowWords * 64).subtract(ONE);
            for (int delta = -2; delta <= 2; delta++) {
                BigInteger other = dense.add(BigInteger.valueOf(delta));
                Int1024 a = from(dense), b = from(other);
                BigInteger expected = dense.multiply(other);
                assertNumber(expected, a.multiply(b));
                assertNumber(expected, b.multiply(a));
            }
        }
    }

    @Test
    void transfersNormalizedMultiBitCarryAcrossThe704BitBoundary() {
        BigInteger split = ONE.shiftLeft(704), lowMask = split.subtract(ONE);
        BigInteger highMask = ONE.shiftLeft(320).subtract(ONE);
        List<BigInteger> lows = List.of(lowMask, lowMask.subtract(ONE), ONE.shiftLeft(703), ONE);
        List<BigInteger> highs = List.of(BigInteger.ZERO, ONE, highMask, ONE.shiftLeft(319));
        for (BigInteger lowA : lows) {
            for (BigInteger lowB : lows) {
                for (BigInteger highA : highs) {
                    for (BigInteger highB : highs) {
                        Int1024 a = from(highA.shiftLeft(704).or(lowA));
                        Int1024 b = from(highB.shiftLeft(704).or(lowB));
                        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
                        BigInteger expected = number(a).multiply(number(b));
                        assertNumber(expected, a.multiply(b));
                        assertNumber(expected, b.multiply(a));
                        assertNumber(number(a).pow(2), a.multiply(a));
                        assertArrayEquals(beforeA, a.toBytes());
                        assertArrayEquals(beforeB, b.toBytes());
                    }
                }
            }
        }
        // 低 704 位全一相乘时，C11 为 10，包含归一化产生的额外一。
        assertNumber(ONE.subtract(ONE.shiftLeft(705)), from(lowMask).multiply(from(lowMask)));
    }

    @Test
    void keepsEqualityHashingAndNullHandlingConsistent() {
        Int1024 value = random(new Random(0x1024E0L));
        Int1024 same = Int1024.of(value.toBytes());
        assertEquals(value, same);
        assertEquals(value.hashCode(), same.hashCode());
        assertEquals(0, value.compareTo(same));
        assertNotEquals(Int1024.ONE, Int256.ONE);
        assertNotEquals(Int1024.ONE, Int512.ONE);
        assertNotEquals(Int1024.ZERO, null);
        assertNotEquals(value, value.successor());
        for (UnaryOperator<Int1024> operation : List.<UnaryOperator<Int1024>>of(
                value::add, value::subtract, value::multiply, value::and, value::or, value::xor)) {
            assertThrowsExactly(NullPointerException.class, () -> operation.apply(null));
        }
        assertThrowsExactly(NullPointerException.class, () -> value.compareTo(null));
        for (int index : new int[] {-1, 16, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> value.getLong(index));
        }
        for (int index : new int[] {-1, 1024, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> value.testBit(index));
        }
    }

    private static void checkPair(Int1024 a, Int1024 b) {
        BigInteger x = number(a), y = number(b);
        assertNumber(x.add(y), a.add(b));
        assertNumber(x.subtract(y), a.subtract(b));
        assertNumber(x.multiply(y), a.multiply(b));
        assertEquals(Integer.signum(x.compareTo(y)), Integer.signum(a.compareTo(b)));
    }

    private static void checkShift(Int1024 value, int distance) {
        int effective = (int) (Math.abs((long) distance) % 1024);
        BigInteger expected = distance >= 0 ? number(value).shiftLeft(effective) : number(value).shiftRight(effective);
        assertNumber(expected, value.shift(distance));
    }
}
