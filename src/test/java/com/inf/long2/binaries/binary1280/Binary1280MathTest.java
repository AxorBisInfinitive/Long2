package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.binary1280.Binary1280Reference.Dyadic;

import static com.inf.long2.binaries.binary1280.Binary1280.*;
import static com.inf.long2.binaries.binary1280.Binary1280Reference.*;
import static org.junit.jupiter.api.Assertions.*;

class Binary1280MathTest {
    private static final BigInteger UNIT = BigInteger.ONE;
    private static final int EMAX = 134217727;
    private static final int EMIN = -134217726;
    private static final int QMIN = -134218977;

    @Test
    void fmaRoundsOnlyTheExactSum() {
        Binary1280 delta = power(-627);
        Binary1280 above = ONE.add(delta), below = ONE.subtract(delta);
        assertEquals(ZERO, above.multiply(below).subtract(ONE));
        assertEquals(power(-1254).negate(), fma(above, below, ONE.negate()));
        assertEquals(MAX_VALUE, fma(MAX_VALUE, of(2), MAX_VALUE.negate()));
        assertEquals(MIN_VALUE.nextUp(), fma(MIN_VALUE, of(0.5), MIN_VALUE));
        assertEquals(NEGATIVE_ZERO, fma(MIN_VALUE.negate(), of(0.5), NEGATIVE_ZERO));
        assertEquals(ZERO, fma(ONE, ONE, ONE.negate()));
        assertEquals(NEGATIVE_ZERO, fma(NEGATIVE_ZERO, of(2), NEGATIVE_ZERO));
        assertEquals(ZERO, fma(ZERO, of(2), NEGATIVE_ZERO));
        assertEquals(NaN, fma(ZERO, POSITIVE_INFINITY, ONE));
        assertEquals(NaN, fma(POSITIVE_INFINITY, of(2), NEGATIVE_INFINITY));
        assertEquals(NEGATIVE_INFINITY, fma(ONE, ONE, NEGATIVE_INFINITY));
        assertEquals(NaN, fma(ONE, ONE, NaN));
        assertThrows(NullPointerException.class, () -> fma(NaN, ONE, null));
    }

    @Test
    void fmaAlignmentAndSignedTailBoundaries() {
        Binary1280 a = ONE.nextUp(), b = of(1.5);
        for (int distance : new int[] {2503, 2504, 2505, 2506}) {
            Binary1280 tail = power(-distance);
            checkFma(a, b, tail);
            checkFma(a, b, tail.negate());
            checkFma(a.negate(), b, tail);
        }
        for (int exponent : new int[] {-1255, -1254, -1253, -1252}) {
            checkFma(power(exponent).nextUp().negate(), ONE, ONE);
        }

        // 1253 = 7 × 179，两个因子均可精确表示，积恰为上溢中点。
        BigInteger first = BigInteger.valueOf(127);
        BigInteger second = UNIT.shiftLeft(1253).subtract(UNIT).divide(first);
        Binary1280 factor = round(false, second, UNIT, EMAX - 1252);
        assertEquals(POSITIVE_INFINITY, fma(of(127), factor, ZERO));
        assertEquals(MAX_VALUE, fma(of(127), factor, MIN_VALUE.negate()));
        assertEquals(POSITIVE_INFINITY, fma(of(127), factor, MIN_VALUE));
        assertEquals(MAX_VALUE.negate(), fma(of(-127), factor, MIN_VALUE));
    }

    @Test
    void fmaMatchesExactReference() {
        Random random = new Random(128012);
        for (int i = 0; i < 130; i++) {
            int exponent = random.nextInt(-1300, 1301);
            Binary1280 a = normal(random.nextBoolean(), exponent, new BigInteger(1251, random));
            Binary1280 b = normal(random.nextBoolean(), -exponent, new BigInteger(1251, random));
            Binary1280 c = normal(random.nextBoolean(), random.nextInt(-2508, 1260),
                    new BigInteger(1251, random));
            checkFma(a, b, c);
        }
    }

    private static void checkFma(Binary1280 a, Binary1280 b, Binary1280 c) {
        Dyadic x = decode(a), y = decode(b);
        Dyadic product = new Dyadic(x.negative() ^ y.negative(),
                x.magnitude().multiply(y.magnitude()), x.scale() + y.scale());
        assertArrayEquals(sum(product, decode(c)).toBytes(), fma(a, b, c).toBytes());
    }

    @Test
    void sqrtRoundsFromExactRootAndRemainder() {
        assertEquals(of(2), sqrt(of(4)));
        assertEquals(ONE, sqrt(ONE.nextUp()));
        assertEquals(ONE.nextUp(), sqrt(ONE.nextUp().nextUp()));
        assertEquals(power(67108864).nextDown(), sqrt(MAX_VALUE));
        assertEquals(NEGATIVE_ZERO, sqrt(NEGATIVE_ZERO));
        assertEquals(ZERO, sqrt(ZERO));
        assertEquals(POSITIVE_INFINITY, sqrt(POSITIVE_INFINITY));
        assertEquals(NaN, sqrt(NEGATIVE_INFINITY));
        assertEquals(NaN, sqrt(ONE.negate()));
        assertEquals(NaN, sqrt(NaN));
        assertThrows(NullPointerException.class, () -> sqrt(null));
        Random random = new Random(1252);
        for (int length = 1; length < 1252; length++) {
            checkRoot(fromRaw(new BigInteger(length, random).setBit(length - 1)));
        }
        for (int exponent : new int[] {EMIN, EMIN + 1, -2, -1, 0, 1, EMAX - 1, EMAX}) {
            checkRoot(power(exponent));
            checkRoot(normal(false, exponent, new BigInteger(1251, random)));
        }
    }

    private static void checkRoot(Binary1280 value) {
        Dyadic input = decode(value);
        long exponent = input.scale() + input.magnitude().bitLength() - 1;
        long quantum = Math.floorDiv(exponent, 2) - 1251;
        BigInteger radicand = input.magnitude().shiftLeft(Math.toIntExact(input.scale() - 2 * quantum));
        BigInteger[] result = radicand.sqrtAndRemainder();
        BigInteger half = result[0].shiftLeft(1).add(UNIT);
        BigInteger root = radicand.shiftLeft(2).compareTo(half.multiply(half)) > 0
                ? result[0].add(UNIT) : result[0];
        assertArrayEquals(round(false, root, UNIT, quantum).toBytes(), sqrt(value).toBytes());
    }

    @Test
    void ieeeRemainderUsesTheNearestEvenQuotient() {
        assertEquals(of(-1), ieeeRemainder(of(5), of(3)));
        assertEquals(ONE, ieeeRemainder(of(5), of(-2)));
        assertEquals(ONE.negate(), ieeeRemainder(of(7), of(2)));
        assertEquals(ONE, ieeeRemainder(of(-7), of(2)));
        assertEquals(ONE, ieeeRemainder(ONE, of(2)));
        assertEquals(ONE.nextDown().nextDown().negate(), ieeeRemainder(ONE.nextUp(), of(2)));
        assertEquals(NEGATIVE_ZERO, ieeeRemainder(of(-6), of(3)));
        assertEquals(NEGATIVE_ZERO, ieeeRemainder(NEGATIVE_ZERO, NEGATIVE_INFINITY));
        assertEquals(ONE, ieeeRemainder(ONE, POSITIVE_INFINITY));
        assertEquals(NaN, ieeeRemainder(ONE, ZERO));
        assertEquals(NaN, ieeeRemainder(POSITIVE_INFINITY, ONE));
        assertThrows(NullPointerException.class, () -> ieeeRemainder(NaN, null));

        Random random = new Random(786);
        for (int difference : new int[] {-1252, -1, 0, 1, 1251, 1252, 1253, 2505}) {
            for (int i = 0; i < 16; i++) {
                Binary1280 a = normal(random.nextBoolean(), difference, new BigInteger(1251, random));
                Binary1280 b = normal(random.nextBoolean(), 0, new BigInteger(1251, random));
                checkRemainder(a, b);
            }
        }
        checkRemainder(MAX_VALUE, MIN_VALUE.multiply(of(7)));
        checkRemainder(MAX_VALUE, MIN_VALUE.multiply(of(11)));
        checkRemainder(MIN_VALUE.negate(), MIN_NORMAL);
    }

    private static void checkRemainder(Binary1280 a, Binary1280 b) {
        Dyadic x = decode(a), y = decode(b);
        long quantum = Math.min(x.scale(), y.scale());
        int shift = Math.toIntExact(x.scale() - quantum);
        BigInteger divisor = y.magnitude().shiftLeft(Math.toIntExact(y.scale() - quantum));
        BigInteger[] qr;
        if (shift < 4000) {
            qr = x.magnitude().shiftLeft(shift).divideAndRemainder(divisor);
        } else {
            BigInteger modulus = divisor.shiftLeft(1);
            qr = x.magnitude().multiply(BigInteger.TWO.modPow(BigInteger.valueOf(shift), modulus))
                    .mod(modulus).divideAndRemainder(divisor);
        }
        int half = qr[1].shiftLeft(1).compareTo(divisor);
        BigInteger residue = half > 0 || half == 0 && qr[0].testBit(0) ? qr[1].subtract(divisor) : qr[1];
        assertArrayEquals(round(x.negative() ^ (residue.signum() < 0), residue.abs(), UNIT, quantum).toBytes(),
                ieeeRemainder(a, b).toBytes());
    }

    @Test
    void scalbAndAbsRespectTheFullRangeAndSpecialValues() {
        assertEquals(NEGATIVE_INFINITY, scalb(ONE.negate(), Long.MAX_VALUE));
        assertEquals(NEGATIVE_ZERO, scalb(ONE.negate(), Long.MIN_VALUE));
        assertEquals(NEGATIVE_ZERO, scalb(NEGATIVE_ZERO, Long.MAX_VALUE));
        assertEquals(NEGATIVE_INFINITY, scalb(NEGATIVE_INFINITY, Long.MIN_VALUE));
        assertEquals(ZERO, scalb(ONE, QMIN - 1L));
        assertEquals(MIN_VALUE, scalb(ONE.nextUp(), QMIN - 1L));
        assertEquals(MIN_VALUE, scalb(ONE, QMIN));
        assertEquals(MIN_NORMAL, scalb(MIN_VALUE, 1251));
        assertEquals(MIN_NORMAL, scalb(power(1).nextDown(), EMIN - 1L));
        assertEquals(power(EMAX), scalb(ONE, EMAX));
        assertEquals(POSITIVE_INFINITY, scalb(ONE, EMAX + 1L));
        assertEquals(NaN, scalb(NaN, 0));
        assertEquals(ZERO, abs(NEGATIVE_ZERO));
        assertEquals(POSITIVE_INFINITY, abs(NEGATIVE_INFINITY));
        assertEquals(MIN_VALUE, abs(MIN_VALUE.negate()));
        assertEquals(NaN, abs(NaN));
        assertThrows(NullPointerException.class, () -> scalb(null, 0));
        assertThrows(NullPointerException.class, () -> abs(null));
    }
}
