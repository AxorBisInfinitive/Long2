package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.Test;

import com.inf.long2.Int;

class Binary256IntegerConversionTest {
    private static final BigInteger ONE = BigInteger.ONE;

    @Test
    void convertsEveryShortMagnitudeLengthWithoutConstructingAnAbsoluteValue() {
        for (int length = 1; length <= 512; length++) {
            BigInteger power = ONE.shiftLeft(length - 1);
            int width = ((length + 64) / 64) * 64;
            for (BigInteger magnitude : List.of(power.subtract(ONE), power, power.add(ONE),
                    ONE.shiftLeft(length).subtract(ONE))) {
                for (BigInteger signed : List.of(magnitude, magnitude.negate())) {
                    assertConversion(width, signed);
                }
            }
        }
    }

    @Test
    void roundsBothSignsAtMidpointsAndAcrossCarryChains() {
        for (int length : new int[] {238, 239, 240, 255, 256, 257, 300, 4096, 65536, 262144}) {
            int discarded = length - 237, width = ((length + 64) / 64) * 64;
            for (int carry : new int[] {0, 1, 63, 64, 127, 128, 191, 192, 236}) {
                BigInteger retained = ONE.shiftLeft(236).or(ONE.shiftLeft(carry).subtract(ONE));
                BigInteger midpoint = retained.shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
                for (int delta : new int[] {-1, 0, 1}) {
                    BigInteger magnitude = midpoint.add(BigInteger.valueOf(delta));
                    assertConversion(width, magnitude);
                    assertConversion(width, magnitude.negate());
                }
            }
        }
    }

    @Test
    void readsAtMostFiveGroupsWhenTheRetainedWindowDeterminesRounding() {
        for (int length : new int[] {238, 239, 240, 256, 257, 4096, 65536, 262144}) {
            int discarded = length - 237, width = ((length + 64) / 64) * 64;
            BigInteger even = ONE.shiftLeft(236), odd = even.add(ONE);
            BigInteger half = ONE.shiftLeft(discarded - 1);
            List<BigInteger> values = List.of(
                    even.shiftLeft(discarded),
                    odd.shiftLeft(discarded).add(half),
                    even.shiftLeft(discarded).add(half).negate(),
                    odd.shiftLeft(discarded).add(half).add(ONE).negate(),
                    ONE.shiftLeft(length - 1).negate());
            for (BigInteger signed : values) {
                int groups = width / 64, bits = signed.bitLength();
                int quantum = Math.max(bits - 237, 0);
                int first = groups - 1 - (bits - 1) / 64;
                int last = groups - 1 - (quantum == 0 ? 0 : (quantum - 1) / 64);
                int[] reads = {0};
                Binary256 actual = Binary256.of(integer(width, signed, index -> {
                    assertTrue(index >= first && index <= last, "irrelevant source group=" + index);
                    reads[0]++;
                }));
                assertReference(signed, actual);
                assertTrue(reads[0] <= 5, "source reads=" + reads[0]);
            }
        }
    }

    @Test
    void stopsTailQueriesAtTheFirstNonzeroGroup() {
        int length = 65536, width = 65600, discarded = length - 237;
        BigInteger even = ONE.shiftLeft(236), odd = even.add(ONE);
        BigInteger half = ONE.shiftLeft(discarded - 1);
        BigInteger positive = even.shiftLeft(discarded).add(half).add(ONE);
        BigInteger negative = odd.shiftLeft(discarded).add(half).subtract(ONE).negate();
        for (BigInteger signed : List.of(positive, negative)) {
            int[] reads = {0};
            Binary256 actual = Binary256.of(integer(width, signed, index -> {
                reads[0]++;
                assertTrue(reads[0] <= 6, "tail scan did not stop");
            }));
            assertReference(signed, actual);
        }
    }

    @Test
    void addressesLowValueGroupsInTheLargestSupportedSourceWidth() {
        int width = 2147483584;
        for (BigInteger signed : List.of(BigInteger.ZERO, ONE.negate(), BigInteger.valueOf(Long.MIN_VALUE),
                ONE.shiftLeft(128).negate(), ONE.shiftLeft(236).add(ONE), ONE.shiftLeft(256).negate(),
                ONE.shiftLeft(300).add(ONE))) {
            int[] reads = {0};
            Binary256 actual = Binary256.of(integer(width, signed, index -> {
                assertTrue(index >= width / 64 - 5, "leading sign-extension read=" + index);
                reads[0]++;
            }));
            assertReference(signed, actual);
            assertTrue(reads[0] <= 5);
            if (signed.bitLength() == 0) {
                assertEquals(0, reads[0]);
            }
        }
    }

    @Test
    void returnsInfinityForCertainOverflowWithoutReadingSourceGroups() {
        for (int length : new int[] {262145, 262208}) {
            int width = ((length + 64) / 64) * 64;
            BigInteger magnitude = ONE.shiftLeft(length - 1);
            for (BigInteger signed : List.of(magnitude, magnitude.add(ONE).negate())) {
                Binary256 actual = Binary256.of(integer(width, signed, index -> {
                    throw new AssertionError("Unexpected source group=" + index);
                }));
                assertEquals(signed.signum() < 0 ? Binary256.NEGATIVE_INFINITY : Binary256.POSITIVE_INFINITY, actual);
            }
        }
    }

    @Test
    void matchesIndependentRoundingForRandomSignedIntegersAndPaddingWidths() {
        Random random = new Random(0xB1256A850L);
        for (int i = 0; i < 2000; i++) {
            int length = 1 + random.nextInt(4096);
            BigInteger magnitude = new BigInteger(length, random).setBit(length - 1);
            int width = ((length + 64) / 64 + random.nextInt(4)) * 64;
            assertConversion(width, random.nextBoolean() ? magnitude : magnitude.negate());
        }
    }

    private static void assertConversion(int width, BigInteger signed) {
        assertReference(signed, Binary256.of(integer(width, signed, index -> { })));
    }

    private static void assertReference(BigInteger signed, Binary256 actual) {
        Binary256 expected = Binary256Reference.round(signed.signum() < 0, signed.abs(), ONE, 0);
        assertArrayEquals(expected.toBytes(), actual.toBytes(), "source bitLength=" + signed.bitLength());
    }

    /** 除格式、符号、有效位数和分组外，任何源操作都使测试失败。 */
    private static Int<?> integer(int width, BigInteger signed, IntConsumer read) {
        Int.Format format = new Int.Format(width);
        assertTrue(signed.bitLength() < width);
        int groups = width / 64;
        return (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> format;
                    case "isNegative" -> signed.signum() < 0;
                    case "bitLength" -> signed.bitLength();
                    case "getLong" -> {
                        int index = (int) arguments[0];
                        assertTrue(index >= 0 && index < groups);
                        read.accept(index);
                        yield signed.shiftRight((groups - 1 - index) * 64).longValue();
                    }
                    default -> throw new AssertionError("Unexpected source operation: " + method.getName());
                });
    }
}
