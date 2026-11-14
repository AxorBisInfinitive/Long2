package com.inf.long2.binaries.binary1280;

import java.util.Arrays;

import com.inf.long2.binaries.internal.UnsignedWords;

import static com.inf.long2.binaries.binary1280.Binary1280Encoding.*;
import static com.inf.long2.binaries.binary1280.Binary1280Rounding.*;

final class Binary1280Arithmetic {
    private Binary1280Arithmetic() {
    }

    static Binary1280 add(Binary1280 a, Binary1280 b, boolean subtract) {
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
        long ar = a.getRawLong(0), br = b.getRawLong(0);
        int ac = (int) ((ar >>> TOP_FRACTION_BITS) & EXPONENT_MASK);
        int bc = (int) ((br >>> TOP_FRACTION_BITS) & EXPONENT_MASK);
        // 编码指数差是正常数和次正规数混合时实际指数差的下界。
        int gap = ac - bc;
        if (gap >= PRECISION + 2) {
            return a;
        }
        if (gap <= -(PRECISION + 2)) {
            return subtract ? b.negate() : b;
        }
        if (ac == bc) {
            long atop = ar & FRACTION_MASK, btop = br & FRACTION_MASK;
            if (ac == 0) {
                return addSubnormal(a, atop, aNegative, b, btop, bNegative);
            }
            return addSameExponent(a, atop | HIDDEN_BIT, aNegative, b, btop | HIDDEN_BIT, bNegative, ac);
        }
        if (ac != 0 && bc != 0) {
            if (gap > 0 && gap < Long.SIZE) {
                return addNearNormal(a, ar, aNegative, b, br, bNegative, gap, bc);
            }
            if (gap < 0 && gap > -Long.SIZE) {
                return addNearNormal(b, br, bNegative, a, ar, aNegative, -gap, ac);
            }
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

    /** 正常数指数差为 1 至 63，高指数项左移至低指数项的位权。 */
    private static Binary1280 addNearNormal(Binary1280 high, long highRaw, boolean highNegative,
            Binary1280 low, long lowRaw, boolean lowNegative, int gap, int lowEncoded) {
        boolean adding = highNegative == lowNegative;
        int count = (PRECISION + gap + (adding ? 1 : 0) + Long.SIZE - 1) / Long.SIZE;
        long[] exact = new long[count];
        long highTop = (highRaw & FRACTION_MASK) | HIDDEN_BIT;
        long lowTop = (lowRaw & FRACTION_MASK) | HIDDEN_BIT;
        long previous = 0, carry = 0;
        if (adding) {
            for (int i = 0; i < WORDS; i++) {
                long source = finiteWord(high, highTop, i);
                long x = (source << gap) | previous;
                previous = source >>> (Long.SIZE - gap);
                long y = finiteWord(low, lowTop, i);
                long partial = x + y;
                exact[i] = partial + carry;
                carry = Long.compareUnsigned(partial, x) < 0
                        || Long.compareUnsigned(exact[i], partial) < 0 ? 1 : 0;
            }
        } else {
            for (int i = 0; i < WORDS; i++) {
                long source = finiteWord(high, highTop, i);
                long x = (source << gap) | previous;
                previous = source >>> (Long.SIZE - gap);
                long y = finiteWord(low, lowTop, i);
                long difference = x - y;
                exact[i] = difference - carry;
                carry = Long.compareUnsigned(x, y) < 0
                        || Long.compareUnsigned(difference, carry) < 0 ? 1 : 0;
            }
        }
        if (count > WORDS) {
            exact[WORDS] = adding ? previous + carry : previous - carry;
        }
        return roundDyadic(highNegative, exact, lowEncoded - (long) BIAS - FRACTION_BITS);
    }

    private static Binary1280 addSameExponent(Binary1280 a, long atop, boolean aNegative,
            Binary1280 b, long btop, boolean bNegative, int encoded) {
        if (aNegative == bNegative) {
            if (encoded == EXPONENT_MASK - 1) {
                return infinity(aNegative);
            }
            long[] result = addEqualScale(a, atop, b, btop);
            boolean increment = (result[0] & 3) == 3;
            for (int i = 0; i < WORDS - 1; i++) {
                result[i] = (result[i] >>> 1) | (result[i + 1] << 63);
            }
            result[WORDS - 1] >>>= 1;
            if (increment) {
                for (int i = 0; i < WORDS; i++) {
                    if (++result[i] != 0) {
                        break;
                    }
                }
            }
            result[WORDS - 1] = (aNegative ? Long.MIN_VALUE : 0)
                    | ((encoded + 1L) << TOP_FRACTION_BITS) | (result[WORDS - 1] & FRACTION_MASK);
            return raw(result);
        }
        int highest = highestDifferentWord(a, atop, b, btop);
        if (highest < 0) {
            return Binary1280.ZERO;
        }
        boolean aLarger = Long.compareUnsigned(finiteWord(a, atop, highest), finiteWord(b, btop, highest)) > 0;
        boolean negative = aLarger ? aNegative : bNegative;
        long[] result = aLarger ? subtractEqualScale(a, atop, b, btop, highest)
                : subtractEqualScale(b, btop, a, atop, highest);
        if (encoded == 1) {
            result[WORDS - 1] |= negative ? Long.MIN_VALUE : 0;
            return raw(result);
        }
        int length = UnsignedWords.bitLength(result);
        int shift = Math.min(PRECISION - length, encoded - 1);
        for (int i = WORDS - 1; i >= 0; i--) {
            result[i] = shiftedWord(result, i, -shift);
        }
        long resultEncoded = result[WORDS - 1] >= HIDDEN_BIT ? encoded - shift : 0;
        result[WORDS - 1] = (negative ? Long.MIN_VALUE : 0)
                | (resultEncoded << TOP_FRACTION_BITS) | (result[WORDS - 1] & FRACTION_MASK);
        return raw(result);
    }

    private static long finiteWord(Binary1280 value, long top, int index) {
        return index == WORDS - 1 ? top : value.getRawLong(WORDS - 1 - index);
    }

    private static int highestDifferentWord(Binary1280 a, long atop, Binary1280 b, long btop) {
        for (int i = WORDS - 1; i >= 0; i--) {
            if (finiteWord(a, atop, i) != finiteWord(b, btop, i)) {
                return i;
            }
        }
        return -1;
    }

    private static long[] addEqualScale(Binary1280 a, long atop, Binary1280 b, long btop) {
        long[] result = new long[WORDS];
        long carry = 0;
        for (int i = 0; i < WORDS; i++) {
            long x = finiteWord(a, atop, i), y = finiteWord(b, btop, i);
            long partial = x + y;
            result[i] = partial + carry;
            carry = Long.compareUnsigned(partial, x) < 0
                    || Long.compareUnsigned(result[i], partial) < 0 ? 1 : 0;
        }
        return result;
    }

    /** 较高各组相等，最高不同组吸收低位传入的借位。 */
    private static long[] subtractEqualScale(Binary1280 high, long highTop,
            Binary1280 low, long lowTop, int highestDifferent) {
        long[] result = new long[WORDS];
        long borrow = 0;
        for (int i = 0; i <= highestDifferent; i++) {
            long x = finiteWord(high, highTop, i), y = finiteWord(low, lowTop, i);
            long difference = x - y;
            result[i] = difference - borrow;
            borrow = Long.compareUnsigned(x, y) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
        }
        return result;
    }

    /** 结果整数按 QMIN 计量，向隐藏位的进位正好形成最低指数的正常数。 */
    private static Binary1280 addSubnormal(Binary1280 a, long atop, boolean aNegative,
            Binary1280 b, long btop, boolean bNegative) {
        boolean negative = aNegative;
        long[] result;
        if (aNegative == bNegative) {
            result = addEqualScale(a, atop, b, btop);
        } else {
            int highest = highestDifferentWord(a, atop, b, btop);
            if (highest < 0) {
                return Binary1280.ZERO;
            }
            if (Long.compareUnsigned(finiteWord(a, atop, highest), finiteWord(b, btop, highest)) > 0) {
                result = subtractEqualScale(a, atop, b, btop, highest);
            } else {
                result = subtractEqualScale(b, btop, a, atop, highest);
                negative = bNegative;
            }
        }
        result[WORDS - 1] |= negative ? Long.MIN_VALUE : 0;
        return raw(result);
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

    static Binary1280 multiply(Binary1280 a, Binary1280 b) {
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

    static Binary1280 fma(Binary1280 a, Binary1280 b, Binary1280 c) {
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

    static Binary1280 divide(Binary1280 a, Binary1280 b) {
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

    static Binary1280 remainder(Binary1280 a, Binary1280 b) {
        return remainder(a, b, false);
    }

    static Binary1280 ieeeRemainder(Binary1280 a, Binary1280 b) {
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

    static Binary1280 sqrt(Binary1280 value) {
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
