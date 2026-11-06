package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640RoundingTest {
    @Test
    void roundsZeroNormalAndOverflowMidpointsFromExactSources() {
        int[] scales = {-8389224, -8389223, -8389222, -8389221, -8388607, -8388606, -616, -615, 8387989, 8387990, 8387991};
        BigInteger[] magnitudes = {ONE, BigInteger.TWO, BigInteger.valueOf(3), ONE.shiftLeft(615).subtract(ONE),
                ONE.shiftLeft(616).subtract(ONE), ONE.shiftLeft(617).subtract(ONE)};
        for (int scale : scales) {
            for (BigInteger magnitude : magnitudes) {
                for (boolean negative : new boolean[] {false, true}) {
                    assertRaw(dyadic(negative, magnitude, scale), Binary640.of(source(negative, magnitude, scale)));
                }
            }
        }
        assertRaw(Binary640.ZERO, Binary640.of(source(false, ONE, -8389222)));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.of(source(true, ONE, -8389222)));
        assertRaw(Binary640.MIN_VALUE, Binary640.of(source(false, BigInteger.valueOf(3), -8389223)));
        BigInteger threshold = ONE.shiftLeft(620).subtract(BigInteger.valueOf(8));
        assertRaw(Binary640.MAX_VALUE, Binary640.of(source(false, threshold.subtract(ONE), 8387988)));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.of(source(false, threshold, 8387988)));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.of(source(false, threshold.add(ONE), 8387988)));
    }

    @Test
    void preservesGuardStickyAndParityAtEveryDiscardedWordAlignment() {
        for (int discarded = 1; discarded <= 1025; discarded++) {
            BigInteger kept = ONE.shiftLeft(615).add(BigInteger.valueOf(discarded & 1));
            BigInteger middle = kept.shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger magnitude = middle.add(BigInteger.valueOf(delta));
                long scale = -615L - discarded;
                assertRaw(dyadic(false, magnitude, scale), Binary640.of(source(false, magnitude, scale)));
                assertRaw(dyadic(true, magnitude, scale), Binary640.of(source(true, magnitude, scale)));
            }
        }
    }

    @Test
    void propagatesRoundingCarriesAcrossAllRetainedWords() {
        for (int words = 1; words <= 9; words++) {
            BigInteger kept = ONE.shiftLeft(615).or(ONE.shiftLeft(words * 64).subtract(ONE));
            BigInteger magnitude = kept.shiftLeft(3).add(BigInteger.valueOf(5));
            assertRaw(dyadic(false, magnitude, -618), Binary640.of(source(false, magnitude, -618)));
        }
        BigInteger magnitude = ONE.shiftLeft(619).subtract(BigInteger.valueOf(3));
        assertRaw(Binary640.of(2L), Binary640.of(source(false, magnitude, -618)));
    }

    @Test
    void handlesScalingExtremesAndUlpAtBothEnds() {
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.ONE.scalb(Long.MAX_VALUE));
        assertRaw(Binary640.ZERO, Binary640.ONE.scalb(Long.MIN_VALUE));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.ONE.negate().scalb(Long.MIN_VALUE));
        assertRaw(Binary640.ONE, Binary640.MIN_VALUE.scalb(8389221));
        assertRaw(Binary640.MIN_VALUE, Binary640.ONE.scalb(-8389221));
        assertRaw(Binary640.ZERO, Binary640.ONE.scalb(-8389222));
        assertRaw(Binary640.ONE.scalb(-615), Binary640.ONE.ulp());
        assertRaw(Binary640.ONE.scalb(8387992), Binary640.MAX_VALUE.ulp());
        assertRaw(Binary640.MIN_VALUE, Binary640.NEGATIVE_ZERO.ulp());
        assertRaw(Binary640.MIN_VALUE, Binary640.MIN_NORMAL.nextDown().ulp());
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY.ulp());
        assertRaw(Binary640.NaN, Binary640.NaN.ulp());
        Random random = new Random(12345);
        for (int i = 0; i < 200; i++) {
            Binary640 value = random(random, i < 30);
            var decoded = Binary640Reference.decode(value);
            int shift = random.nextInt(34000001) - 17000000;
            assertRaw(dyadic(decoded.negative(), decoded.magnitude(), decoded.scale() + shift), value.scalb(shift));
        }
    }
}
