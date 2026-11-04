package com.inf.long2.ints.int512;

import static com.inf.long2.ints.int512.Int512TestSupport.MAXIMUM;
import static com.inf.long2.ints.int512.Int512TestSupport.MINIMUM;
import static com.inf.long2.ints.int512.Int512TestSupport.MODULUS;
import static com.inf.long2.ints.int512.Int512TestSupport.ONE;
import static com.inf.long2.ints.int512.Int512TestSupport.assertNumber;
import static com.inf.long2.ints.int512.Int512TestSupport.from;
import static com.inf.long2.ints.int512.Int512TestSupport.number;
import static com.inf.long2.ints.int512.Int512TestSupport.random;
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

import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

class Int512ArithmeticTest {
    @Test
    void exposesTheSignedFormatAndWrapsAtBothExtremes() {
        assertEquals(512, Int512.FORMAT.bitWidth());
        assertEquals(Int512.FORMAT, Int512.ZERO.format());
        assertNumber(MINIMUM, Int512.MIN_VALUE);
        assertNumber(MAXIMUM, Int512.MAX_VALUE);
        assertEquals(Int512.MIN_VALUE, Int512.MAX_VALUE.successor());
        assertEquals(Int512.MAX_VALUE, Int512.MIN_VALUE.subtract(Int512.ONE));
        assertEquals(Int512.MIN_VALUE, Int512.MIN_VALUE.negate());
        assertEquals(Int512.ZERO, Int512.MIN_VALUE.add(Int512.MIN_VALUE));
        assertEquals(Int512.ONE, Int512.of(-1L).multiply(Int512.of(-1L)));
        assertEquals(Int512.ZERO, Int512.ONE.shift(256).multiply(Int512.ONE.shift(256)));
        assertEquals(Int512.MIN_VALUE, Int512.MIN_VALUE.multiply(Int512.of(-1L)));
    }

    @Test
    void propagatesCarriesBorrowsAndProductsAcrossEveryWord() {
        List<BigInteger> values = new ArrayList<>(List.of(BigInteger.ZERO, ONE, ONE.negate(), MINIMUM, MAXIMUM));
        for (int bit = 32; bit < 512; bit += 32) {
            BigInteger power = ONE.shiftLeft(bit);
            for (int delta : new int[] {-1, 0, 1}) {
                values.add(power.add(BigInteger.valueOf(delta)));
                values.add(power.add(BigInteger.valueOf(delta)).negate());
            }
        }
        for (BigInteger a : values) {
            Int512 x = from(a);
            assertNumber(a.negate(), x.negate());
            assertNumber(a.add(ONE), x.successor());
            for (BigInteger b : values) {
                checkPair(x, from(b));
            }
        }
    }

    @Test
    void matchesBigIntegerForRandomArithmeticBitwiseOperationsAndOrdering() {
        Random random = new Random(0x512A117L);
        for (int i = 0; i < 3000; i++) {
            Int512 a = random(random), b = random(random);
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
            int bit = random.nextInt(512);
            assertEquals(value.testBit(bit), a.testBit(bit));
            assertArrayEquals(beforeA, a.toBytes());
            assertArrayEquals(beforeB, b.toBytes());
        }
    }

    @Test
    void shiftsAllDistancesWithModuloAndSignExtension() {
        List<Int512> values = List.of(Int512.ZERO, Int512.ONE, Int512.of(-1L), Int512.of(-2L),
                Int512.MIN_VALUE, Int512.MAX_VALUE, random(new Random(0x512B17L)));
        for (Int512 value : values) {
            for (int distance = -1025; distance <= 1025; distance++) {
                checkShift(value, distance);
            }
            for (int distance : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE, -65536, 65536}) {
                checkShift(value, distance);
            }
            assertSame(value, value.shift(0));
            assertSame(value, value.shift(512));
            assertSame(value, value.shift(-512));
            assertSame(value, value.shift(Integer.MIN_VALUE));
        }
        for (int bit = 0; bit < 512; bit++) {
            Int512 value = from(ONE.shiftLeft(bit));
            assertEquals(1, value.bitCount());
            for (int index = 0; index < 512; index++) {
                assertEquals(index == bit, value.testBit(index));
            }
            for (int distance : new int[] {-511, -449, -448, -385, -384, -257, -256, -65, -64,
                    -63, -1, 1, 63, 64, 65, 256, 257, 384, 385, 448, 449, 511}) {
                checkShift(value, distance);
            }
        }
        assertEquals(0, Int512.of(-1L).bitLength());
        assertEquals(512, Int512.of(-1L).bitCount());
        assertEquals(511, Int512.MIN_VALUE.bitLength());
    }

    @Test
    void keepsEqualityHashingAndNullHandlingConsistent() {
        Int512 value = random(new Random(0x512E0L));
        Int512 same = Int512.of(value.toBytes());
        assertEquals(value, same);
        assertEquals(value.hashCode(), same.hashCode());
        assertEquals(0, value.compareTo(same));
        assertNotEquals(Int512.ONE, Int256.ONE);
        assertNotEquals(Int512.ZERO, null);
        assertNotEquals(value, value.successor());
        for (UnaryOperator<Int512> operation : List.<UnaryOperator<Int512>>of(
                value::add, value::subtract, value::multiply, value::and, value::or, value::xor)) {
            assertThrowsExactly(NullPointerException.class, () -> operation.apply(null));
        }
        assertThrowsExactly(NullPointerException.class, () -> value.compareTo(null));
        for (int index : new int[] {-1, 8, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> value.getLong(index));
        }
        for (int index : new int[] {-1, 512, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> value.testBit(index));
        }
    }

    private static void checkPair(Int512 a, Int512 b) {
        BigInteger x = number(a), y = number(b);
        assertNumber(x.add(y), a.add(b));
        assertNumber(x.subtract(y), a.subtract(b));
        assertNumber(x.multiply(y), a.multiply(b));
        assertEquals(Integer.signum(x.compareTo(y)), Integer.signum(a.compareTo(b)));
    }

    private static void checkShift(Int512 value, int distance) {
        int effective = (int) (Math.abs((long) distance) % 512);
        BigInteger expected = distance >= 0 ? number(value).shiftLeft(effective) : number(value).shiftRight(effective);
        assertNumber(expected, value.shift(distance));
    }
}
