package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640RemainderTest {
    @Test
    void distinguishesTruncatedAndNearestEvenQuotients() {
        for (int a = -12; a <= 12; a++) {
            for (int b = -5; b <= 5; b++) {
                Binary640 x = Binary640.of((long) a), y = Binary640.of((long) b);
                assertRaw(Binary640.of((double) a % b), x.remainder(y));
                assertRaw(Binary640.of(Math.IEEEremainder(a, b)), x.ieeeRemainder(y));
            }
        }
    }

    @Test
    void comparesRemaindersAtDirectAndModularReductionBoundaries() {
        Random random = new Random(12345);
        for (int shift : new int[] {-619,-618,-617,-1,0,1,615,616,617,618,10000,8388000}) {
            Binary640 a = Binary640.of(5L).scalb(shift);
            for (Binary640 b : new Binary640[] {Binary640.of(2L),Binary640.of(3L),Binary640.ONE.nextUp()}) {
                for (Binary640 x : new Binary640[] {a, a.negate()}) {
                    assertRaw(Binary640Reference.remainder(x, b, false), x.remainder(b));
                    assertRaw(Binary640Reference.remainder(x, b, true), x.ieeeRemainder(b));
                }
            }
        }
        for (int i = 0; i < 160; i++) {
            Binary640 a = random(random, false), b = random(random, false);
            assertRaw(Binary640Reference.remainder(a, b, false), a.remainder(b));
            assertRaw(Binary640Reference.remainder(a, b, true), a.ieeeRemainder(b));
        }
    }

    @Test
    void checksSpecialValuesAndSignedZeroRemainders() {
        double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -3, -0.0, 0.0, 3, Double.POSITIVE_INFINITY};
        for (double a : values) {
            for (double b : values) {
                assertRaw(Binary640.of(a % b), Binary640.of(a).remainder(Binary640.of(b)));
                assertRaw(Binary640.of(Math.IEEEremainder(a, b)), Binary640.of(a).ieeeRemainder(Binary640.of(b)));
            }
        }
    }
}
