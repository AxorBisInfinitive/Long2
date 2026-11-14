package com.inf.long2.binaries.binary1280;

import java.util.Arrays;

import com.inf.long2.binaries.Binary1280;
import com.inf.long2.binaries.internal.UnsignedWords;

import static com.inf.long2.binaries.binary1280.Binary1280Encoding.*;
import static com.inf.long2.binaries.binary1280.Binary1280Rounding.*;

public final class Binary1280Arithmetic {
    private Binary1280Arithmetic() {
    }

    public static Binary1280 add(Binary1280 a, Binary1280 b, boolean subtract) {
        if (a.isNaN() || b.isNaN()) {
            return Binary1280.NaN;
        }
        boolean aNegative = a.signBit(), bNegative = b.signBit() ^ subtract;
        if (a.isInfinite() || b.isInfinite()) {
            return a.isInfinite() && b.isInfinite() && aNegative != bNegative
                    ? Binary1280.NaN : a.isInfinite() ? a : infinity(bNegative);
        }
        if (a.isZero()) {
            return b.isZero() ? zero(aNegative && bNegative) : subtract ? b.negate() : b;
        }
        if (b.isZero()) {
            return a;
        }
        long[] am = magnitude(a), bm = magnitude(b);
        long aq = a.scaleExponent(), bq = b.scaleExponent();
        long ae = exponent(am, aq), be = exponent(bm, bq);
        // 正常二次幂的下侧中点更近，须保留指数差为 p+1 的项。
        if (ae - be >= PRECISION + 2) {
            return a;
        }
        if (be - ae >= PRECISION + 2) {
            return subtract ? b.negate() : b;
        }
        return sum(am, aq, ae, aNegative, bm, bq, be, bNegative);
    }

    private static long exponent(long[] magnitude, long scale) {
        return scale + UnsignedWords.bitLength(magnitude) - 1;
    }

    private static Binary1280 sum(long[] am, long aq, long ae, boolean aNegative,
            long[] bm, long bq, long be, boolean bNegative) {
        long scale = Math.min(aq, bq);
        int ashift = Math.toIntExact(aq - scale), bshift = Math.toIntExact(bq - scale);
        boolean adding = aNegative == bNegative;
        int order = adding ? 1 : compareAligned(am, ashift, bm, bshift, ae, be);
        if (order == 0) {
            return Binary1280.ZERO;
        }
        int span = Math.toIntExact(Math.max(ae, be) - scale + 1 + (adding ? 1 : 0));
        long[] exact = new long[(span + 63) / 64];
        long carry = 0;
        for (int i = 0; i < exact.length; i++) {
            long x = shiftedWord(am, i, -ashift), y = shiftedWord(bm, i, -bshift);
            if (adding) {
                long sum = x + y;
                long next = sum + carry;
                carry = Long.compareUnsigned(sum, x) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                exact[i] = next;
            } else {
                long high = order > 0 ? x : y, low = order > 0 ? y : x;
                long difference = high - low;
                exact[i] = difference - carry;
                carry = Long.compareUnsigned(high, low) < 0 || Long.compareUnsigned(difference, carry) < 0 ? 1 : 0;
            }
        }
        return roundDyadic(order > 0 ? aNegative : bNegative, exact, scale);
    }

    private static int compareAligned(long[] a, int ashift, long[] b, int bshift, long ae, long be) {
        if (ae != be) {
            return Long.compare(ae, be);
        }
        return ashift >= bshift ? -UnsignedWords.compareShifted(b, a, ashift - bshift)
                : UnsignedWords.compareShifted(a, b, bshift - ashift);
    }

    public static Binary1280 multiply(Binary1280 a, Binary1280 b) {
        if (invalidProduct(a, b)) {
            return Binary1280.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isInfinite()) {
            return infinity(negative);
        }
        if (a.isZero() || b.isZero()) {
            return zero(negative);
        }
        Product product = multiplyFinite(a, b);
        return roundDyadic(negative, product.magnitude(), product.scale());
    }

    private static boolean invalidProduct(Binary1280 a, Binary1280 b) {
        return a.isNaN() || b.isNaN() || a.isZero() && b.isInfinite() || a.isInfinite() && b.isZero();
    }

    private record Product(long[] magnitude, long scale) {
    }

    private static Product multiplyFinite(Binary1280 a, Binary1280 b) {
        long[] am = UnsignedWords.trim(magnitude(a)), bm = UnsignedWords.trim(magnitude(b));
        int az = lowZeroWords(am), bz = lowZeroWords(bm);
        if (az != 0) {
            am = Arrays.copyOfRange(am, az, am.length);
        }
        if (bz != 0) {
            bm = Arrays.copyOfRange(bm, bz, bm.length);
        }
        long scale = a.scaleExponent() + b.scaleExponent() + 64L * (az + bz);
        return new Product(UnsignedWords.multiply(am, bm), scale);
    }

    public static Binary1280 fma(Binary1280 a, Binary1280 b, Binary1280 c) {
        if (c.isNaN() || invalidProduct(a, b)) {
            return Binary1280.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isInfinite()) {
            return c.isInfinite() && negative != c.signBit() ? Binary1280.NaN : infinity(negative);
        }
        if (c.isInfinite()) {
            return c;
        }
        if (a.isZero() || b.isZero()) {
            return c.isZero() ? zero(negative && c.signBit()) : c;
        }
        Product product = multiplyFinite(a, b);
        if (c.isZero()) {
            return roundDyadic(negative, product.magnitude(), product.scale());
        }
        long pe = exponent(product.magnitude(), product.scale());
        if (pe >= EMAX + 2L) {
            return infinity(negative);
        }
        long[] cm = magnitude(c);
        long cq = c.scaleExponent(), ce = exponent(cm, cq);
        long difference = pe - ce;
        if (difference <= -(PRECISION + 2)) {
            return c;
        }
        // 尾项小于乘积的最低位间距，只会改变精确中点的舍入方向。
        if (difference >= 2 * PRECISION + 1) {
            return roundDyadic(negative, product.magnitude(), product.scale(), negative == c.signBit() ? 1 : -1);
        }
        return sum(product.magnitude(), product.scale(), pe, negative, cm, cq, ce, c.signBit());
    }

    public static Binary1280 divide(Binary1280 a, Binary1280 b) {
        if (a.isNaN() || b.isNaN() || a.isZero() && b.isZero() || a.isInfinite() && b.isInfinite()) {
            return Binary1280.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isZero()) {
            return infinity(negative);
        }
        if (a.isZero() || b.isInfinite()) {
            return zero(negative);
        }
        long[] am = magnitude(a), bm = magnitude(b);
        int az = trailingZeros(am), bz = trailingZeros(bm);
        long scale = a.scaleExponent() - b.scaleExponent() + az - bz;
        am = removeLowZeros(am, az);
        bm = removeLowZeros(bm, bz);
        return bm.length == 1 && bm[0] == 1 ? roundDyadic(negative, am, scale)
                : roundRational(negative, am, bm, scale);
    }

    public static Binary1280 remainder(Binary1280 a, Binary1280 b) {
        return remainder(a, b, false);
    }

    public static Binary1280 ieeeRemainder(Binary1280 a, Binary1280 b) {
        return remainder(a, b, true);
    }

    private static Binary1280 remainder(Binary1280 a, Binary1280 b, boolean nearest) {
        if (a.isNaN() || b.isNaN() || a.isInfinite() || b.isZero()) {
            return Binary1280.NaN;
        }
        if (a.isZero() || b.isInfinite()) {
            return a;
        }
        long[] am = magnitude(a), bm = magnitude(b);
        long aq = a.scaleExponent(), bq = b.scaleExponent();
        long ae = exponent(am, aq), be = exponent(bm, bq);
        long scale = Math.min(aq, bq);
        if (nearest ? atMostHalf(am, aq, ae, bm, bq, be) : ae < be) {
            return a;
        }
        long shift = aq - bq;
        long[] residue;
        boolean odd;
        if (shift <= PRECISION) {
            if (shift < 0) {
                bm = UnsignedWords.left(bm, Math.toIntExact(-shift));
            } else {
                am = UnsignedWords.left(am, (int) shift);
            }
            long[] quotientLow = nearest ? new long[1] : null;
            residue = UnsignedWords.remainder(am, bm, quotientLow);
            odd = nearest && (quotientLow[0] & 1) != 0;
        } else {
            // 模 2D 同时保留余数和截断商的奇偶。
            long[] modulus = nearest ? UnsignedWords.left(bm, 1) : bm;
            long[] factor = UnsignedWords.powerOfTwoMod(shift, modulus);
            long[] reduced = UnsignedWords.remainder(am, modulus);
            residue = UnsignedWords.remainder(UnsignedWords.multiply(reduced, factor), modulus);
            odd = nearest && UnsignedWords.compare(residue, bm) >= 0;
            if (odd) {
                residue = UnsignedWords.subtract(residue, bm);
            }
        }
        boolean negative = a.signBit();
        if (nearest) {
            int half = -UnsignedWords.compareShifted(bm, residue, 1);
            if (half > 0 || half == 0 && odd) {
                residue = UnsignedWords.subtract(bm, residue);
                negative = !negative;
            }
        }
        return roundDyadic(negative, residue, scale);
    }

    private static boolean atMostHalf(long[] a, long aq, long ae, long[] b, long bq, long be) {
        if (ae + 1 != be) {
            return ae + 1 < be;
        }
        long scale = Math.min(aq + 1, bq);
        return compareAligned(a, Math.toIntExact(aq + 1 - scale), b, Math.toIntExact(bq - scale),
                ae + 1, be) <= 0;
    }

    public static Binary1280 sqrt(Binary1280 value) {
        if (value.isNaN() || value.signBit() && !value.isZero()) {
            return Binary1280.NaN;
        }
        if (value.isZero() || value.isInfinite()) {
            return value;
        }
        long[] magnitude = magnitude(value);
        long scale = value.scaleExponent();
        long quantum = Math.floorDiv(exponent(magnitude, scale), 2) - FRACTION_BITS;
        int shift = Math.toIntExact(scale - 2 * quantum);
        long[] root = new long[WORDS], residue = new long[WORDS], trial = new long[WORDS];
        for (int pair = PRECISION - 1; pair >= 0; pair--) {
            // 位对从偶数位开始，始终完整地落在一个分组内。
            int bit = 2 * pair;
            long next = (shiftedWord(magnitude, bit / 64, -shift) >>> (bit & 63)) & 3;
            for (int i = WORDS - 1; i >= 0; i--) {
                residue[i] = shiftedWord(residue, i, -2);
                trial[i] = shiftedWord(root, i, -2);
            }
            residue[0] |= next;
            trial[0] |= 1;
            boolean accepted = UnsignedWords.compare(residue, trial) >= 0;
            if (accepted) {
                subtractInPlace(residue, trial);
            }
            for (int i = WORDS - 1; i >= 0; i--) {
                root[i] = shiftedWord(root, i, -1);
            }
            root[0] |= accepted ? 1 : 0;
        }
        // 若 N = S² + R，则 N 超过中点平方 S² + S + 1/4 当且仅当 R > S。
        return packRounded(false, root, quantum, UnsignedWords.compare(residue, root) > 0);
    }

    private static void subtractInPlace(long[] larger, long[] smaller) {
        long borrow = 0;
        for (int i = 0; i < larger.length; i++) {
            long word = larger[i], subtrahend = smaller[i];
            long difference = word - subtrahend;
            larger[i] = difference - borrow;
            borrow = Long.compareUnsigned(word, subtrahend) < 0
                    || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
        }
    }

    private static int lowZeroWords(long[] nonzero) {
        int index = 0;
        while (nonzero[index] == 0) {
            index++;
        }
        return index;
    }

    private static int trailingZeros(long[] nonzero) {
        int index = lowZeroWords(nonzero);
        return 64 * index + Long.numberOfTrailingZeros(nonzero[index]);
    }

    private static long[] removeLowZeros(long[] magnitude, int zeros) {
        int bits = UnsignedWords.bitLength(magnitude) - zeros;
        long[] compact = new long[(bits + 63) / 64];
        for (int i = 0; i < compact.length; i++) {
            compact[i] = shiftedWord(magnitude, i, zeros);
        }
        return compact;
    }
}
