package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;

import java.util.Random;

import org.junit.jupiter.api.Test;


class Binary640RemainderTest {
    @Test
    void distinguishesTruncatedAndNearestEvenQuotients() {
        for (int a = -12; a <= 12; a++) {
            for (int b = -5; b <= 5; b++) {
                Binary640 x = Binary640.of((long) a), y = Binary640.of((long) b);
                assertRaw(Binary640.of((double) a % b), x.remainder(y));
                assertRaw(Binary640.of(Math.IEEEremainder(a, b)), Binary640.ieeeRemainder(x, y));
            }
        }
    }

    @Test
    void comparesRemaindersAtDirectAndModularReductionBoundaries() {
        Random random = new Random(12345);
        for (int shift : new int[] {-619,-618,-617,-1,0,1,615,616,617,618,10000,8388000}) {
            Binary640 a = Binary640.scalb(Binary640.of(5L), shift);
            for (Binary640 b : new Binary640[] {Binary640.of(2L),Binary640.of(3L),Binary640.ONE.nextUp()}) {
                for (Binary640 x : new Binary640[] {a, a.negate()}) {
                    assertRaw(Binary640Reference.remainder(x, b, false), x.remainder(b));
                    assertRaw(Binary640Reference.remainder(x, b, true), Binary640.ieeeRemainder(x, b));
                }
            }
        }
        for (int i = 0; i < 160; i++) {
            Binary640 a = random(random, false), b = random(random, false);
            assertRaw(Binary640Reference.remainder(a, b, false), a.remainder(b));
            assertRaw(Binary640Reference.remainder(a, b, true), Binary640.ieeeRemainder(a, b));
        }
    }

    @Test
    void comparesHalfDivisorsAtNormalAndSubnormalBoundaries() {
        for (Binary640 half : new Binary640[] {Binary640.MIN_VALUE, Binary640.scalb(Binary640.MIN_NORMAL, -1),
                Binary640.MIN_NORMAL, Binary640.ONE.nextUp()}) {
            Binary640 divisor = Binary640.scalb(half, 1);
            for (Binary640 magnitude : new Binary640[] {half.nextDown(), half, half.nextUp()}) {
                for (Binary640 dividend : new Binary640[] {magnitude, magnitude.negate()}) {
                    checkBothRemainders(dividend, divisor);
                    checkBothRemainders(dividend, divisor.negate());
                }
            }
        }
    }

    @Test
    void checksQuotientParityForDenseAndTrailingZeroDivisors() {
        for (Binary640 divisor : new Binary640[] {Binary640.ONE, Binary640.ONE.nextUp(),
                Binary640.ONE.nextUp().nextUp(), Binary640.of(1.5), Binary640.scalb(Binary640.MIN_VALUE, 63),
                Binary640.scalb(Binary640.MIN_VALUE, 64)}) {
            for (int gap : new int[] {0, 1, 63, 64, 615, 616, 617, 618, 1024, 65536}) {
                Binary640 dividend = Binary640.scalb(divisor.nextUp(), gap);
                checkBothRemainders(dividend, divisor);
                checkBothRemainders(dividend.negate(), divisor);
            }
        }
        Binary640 half = Binary640.scalb(Binary640.of(1.5), -1);
        for (int quotient : new int[] {0, 1, 2, 3, 4, 5}) {
            Binary640 divisor = Binary640.of(1.5);
            Binary640 dividend = Binary640.fma(Binary640.of(quotient), divisor, half);
            checkBothRemainders(dividend, divisor);
            checkBothRemainders(dividend.negate(), divisor);
        }
    }

    private static void checkBothRemainders(Binary640 dividend, Binary640 divisor) {
        assertRaw(Binary640Reference.remainder(dividend, divisor, false), dividend.remainder(divisor));
        assertRaw(Binary640Reference.remainder(dividend, divisor, true), Binary640.ieeeRemainder(dividend, divisor));
    }

    @Test
    void checksSpecialValuesAndSignedZeroRemainders() {
        double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -3, -0.0, 0.0, 3, Double.POSITIVE_INFINITY};
        for (double a : values) {
            for (double b : values) {
                assertRaw(Binary640.of(a % b), Binary640.of(a).remainder(Binary640.of(b)));
                assertRaw(Binary640.of(Math.IEEEremainder(a, b)), Binary640.ieeeRemainder(Binary640.of(a), Binary640.of(b)));
            }
        }
    }
}
