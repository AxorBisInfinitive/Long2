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
        long ae = aq + UnsignedWords.bitLength(am) - 1;
        long be = bq + UnsignedWords.bitLength(bm) - 1;
        // 正常二次幂的下侧中点更近，须保留指数差为 p+1 的项。
        if (ae - be >= PRECISION + 2) {
            return a;
        }
        if (be - ae >= PRECISION + 2) {
            return subtract ? b.negate() : b;
        }
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
        if (a.isNaN() || b.isNaN() || a.isZero() && b.isInfinite() || a.isInfinite() && b.isZero()) {
            return Binary1280.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isInfinite()) {
            return infinity(negative);
        }
        if (a.isZero() || b.isZero()) {
            return zero(negative);
        }
        long[] am = UnsignedWords.trim(magnitude(a)), bm = UnsignedWords.trim(magnitude(b));
        int az = lowZeroWords(am), bz = lowZeroWords(bm);
        if (az != 0) {
            am = Arrays.copyOfRange(am, az, am.length);
        }
        if (bz != 0) {
            bm = Arrays.copyOfRange(bm, bz, bm.length);
        }
        long scale = a.scaleExponent() + b.scaleExponent() + 64L * (az + bz);
        return roundDyadic(negative, UnsignedWords.multiply(am, bm), scale);
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
        if (a.isNaN() || b.isNaN() || a.isInfinite() || b.isZero()) {
            return Binary1280.NaN;
        }
        if (a.isZero() || b.isInfinite()) {
            return a;
        }
        long[] am = magnitude(a), bm = magnitude(b);
        long aq = a.scaleExponent(), bq = b.scaleExponent();
        long ae = aq + UnsignedWords.bitLength(am) - 1, be = bq + UnsignedWords.bitLength(bm) - 1;
        if (ae < be) {
            return a;
        }
        long shift = aq - bq;
        long[] residue;
        if (shift < 0) {
            residue = UnsignedWords.remainder(am, UnsignedWords.left(bm, Math.toIntExact(-shift)));
        } else if (shift <= PRECISION) {
            residue = UnsignedWords.remainder(UnsignedWords.left(am, (int) shift), bm);
        } else {
            long[] factor = UnsignedWords.powerOfTwoMod(shift, bm);
            long[] reduced = UnsignedWords.remainder(am, bm);
            residue = UnsignedWords.remainder(UnsignedWords.multiply(reduced, factor), bm);
        }
        return roundDyadic(a.signBit(), residue, Math.min(aq, bq));
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
