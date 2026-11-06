package com.inf.long2.binaries;

import java.util.Arrays;

/** 无符号幅值低组在前，所有原地写入只作用于本次调用独占的数组。 */
final class Binary640Math {
    private Binary640Math() {
    }

    static long[] magnitude(Binary640 value) {
        long[] words = new long[10];
        for (int i = 0; i < words.length; i++) {
            words[i] = value.getSignificandLong(9 - i);
        }
        return UnsignedWords.trim(words);
    }

    static Binary640 raw(long[] lowWords) {
        return Binary640.fromRawBits(lowWords[9], lowWords[8], lowWords[7], lowWords[6], lowWords[5],
                lowWords[4], lowWords[3], lowWords[2], lowWords[1], lowWords[0]);
    }

    /** 将一个源分组按位偏移并入独占窗口，窗口外的位丢弃。 */
    static void insert(long[] target, long word, long offset) {
        if (offset < 0) {
            if (offset > -64) {
                target[0] |= word >>> (int) -offset;
            }
            return;
        }
        long group = offset / 64;
        int bits = (int) (offset & 63);
        if (group < target.length) {
            target[(int) group] |= word << bits;
        }
        if (bits != 0 && group + 1 < target.length) {
            target[(int) group + 1] |= word >>> (64 - bits);
        }
    }

    /** distance 为正时右移，为负时左移，范围外的源分组补零。 */
    static long shiftedWord(long[] magnitude, int index, int distance) {
        int group = index + (distance >> 6), bits = distance & 63;
        long low = group < 0 ? 0 : UnsignedWords.word(magnitude, group);
        if (bits == 0) {
            return low;
        }
        long high = group < -1 ? 0 : UnsignedWords.word(magnitude, group + 1);
        return (low >>> bits) | (high << (64 - bits));
    }

    static Binary640 roundDyadic(boolean negative, long[] magnitude, long scale) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return Binary640.zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return Binary640.infinity(negative);
        }
        if (exponent < Binary640.QMIN - 1L) {
            return Binary640.zero(negative);
        }
        long quantum = Math.max(exponent - 615, Binary640.QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long[] kept = new long[10];
        for (int i = 0; i < kept.length; i++) {
            kept[i] = shiftedWord(magnitude, i, discarded);
        }
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && ((kept[0] & 1) != 0 || UnsignedWords.anyLow(magnitude, discarded - 1));
        return packRounded(negative, kept, quantum, increment);
    }

    static Binary640 roundRational(boolean negative, long[] numerator, long[] denominator, long scale) {
        if (UnsignedWords.bitLength(numerator) == 0) {
            return Binary640.zero(negative);
        }
        int ratioExponent = UnsignedWords.bitLength(numerator) - UnsignedWords.bitLength(denominator);
        int comparison = ratioExponent >= 0
                ? UnsignedWords.compare(numerator, UnsignedWords.left(denominator, ratioExponent))
                : UnsignedWords.compare(UnsignedWords.left(numerator, -ratioExponent), denominator);
        long exponent = scale + ratioExponent - (comparison < 0 ? 1 : 0);
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return Binary640.infinity(negative);
        }
        if (exponent < Binary640.QMIN - 1L) {
            return Binary640.zero(negative);
        }
        long quantum = Math.max(exponent - 615, Binary640.QMIN);
        long shift = scale - quantum;
        if (shift >= 0) {
            numerator = UnsignedWords.left(numerator, Math.toIntExact(shift));
        } else {
            denominator = UnsignedWords.left(denominator, Math.toIntExact(-shift));
        }
        UnsignedWords.Division division = UnsignedWords.divide(numerator, denominator);
        int half = UnsignedWords.compare(UnsignedWords.left(division.remainder(), 1), denominator);
        return packRounded(negative, Arrays.copyOf(division.quotient(), 10), quantum,
                half > 0 || half == 0 && UnsignedWords.bit(division.quotient(), 0));
    }

    /** kept 独占且至多 616 位，舍入加一与次正规归一化都在最终位权完成。 */
    private static Binary640 packRounded(boolean negative, long[] kept, long quantum, boolean increment) {
        if (increment) {
            for (int i = 0; i < kept.length; i++) {
                if (++kept[i] != 0) {
                    break;
                }
            }
        }
        if ((kept[9] & (Binary640.HIDDEN_BIT << 1)) != 0) {
            for (int i = 0; i < kept.length - 1; i++) {
                kept[i] = (kept[i] >>> 1) | (kept[i + 1] << 63);
            }
            kept[9] >>>= 1;
            quantum++;
        }
        int length = UnsignedWords.bitLength(kept);
        if (length == 0) {
            return Binary640.zero(negative);
        }
        long exponent = quantum + length - 1;
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return Binary640.infinity(negative);
        }
        long encoded = exponent < Binary640.FORMAT.minExponent() ? 0 : exponent + Binary640.BIAS;
        kept[9] = (negative ? Long.MIN_VALUE : 0) | (encoded << 39) | (kept[9] & Binary640.FRACTION_MASK);
        return raw(kept);
    }

    private static long exponent(long[] magnitude, long scale) {
        return scale + UnsignedWords.bitLength(magnitude) - 1;
    }

    static Binary640 add(Binary640 a, Binary640 b, boolean subtract) {
        if (a.isNaN() || b.isNaN()) {
            return Binary640.NaN;
        }
        boolean aneg = a.signBit(), bneg = b.signBit() ^ subtract;
        if (a.isInfinite() || b.isInfinite()) {
            return a.isInfinite() && b.isInfinite() && aneg != bneg
                    ? Binary640.NaN : a.isInfinite() ? a : Binary640.infinity(bneg);
        }
        if (a.isZero()) {
            return b.isZero() ? Binary640.zero(aneg && bneg) : subtract ? b.negate() : b;
        }
        if (b.isZero()) {
            return a;
        }
        long[] am = magnitude(a), bm = magnitude(b);
        long as = a.scaleExponent(), bs = b.scaleExponent();
        long difference = exponent(am, as) - exponent(bm, bs);
        if (difference >= 618) {
            return a;
        }
        if (difference <= -618) {
            return subtract ? b.negate() : b;
        }
        return sum(aneg, am, as, bneg, bm, bs, 20);
    }

    /** 对齐范围已由调用方证明，源数组不变，结果只在最终位权上舍入。 */
    private static Binary640 sum(boolean aneg, long[] a, long as, boolean bneg, long[] b, long bs, int capacity) {
        long scale = Math.min(as, bs);
        int ashift = Math.toIntExact(as - scale), bshift = Math.toIntExact(bs - scale);
        int comparison = 1;
        if (aneg != bneg) {
            comparison = compareAligned(a, ashift, b, bshift, capacity);
            if (comparison == 0) {
                return Binary640.ZERO;
            }
        }
        long[] result = new long[capacity];
        long carry = 0;
        for (int i = 0; i < capacity; i++) {
            long av = shiftedWord(a, i, -ashift), bv = shiftedWord(b, i, -bshift);
            if (aneg == bneg) {
                long partial = av + bv;
                long next = partial + carry;
                carry = Long.compareUnsigned(partial, av) < 0 || Long.compareUnsigned(next, partial) < 0 ? 1 : 0;
                result[i] = next;
            } else {
                long greater = comparison > 0 ? av : bv, smaller = comparison > 0 ? bv : av;
                long partial = greater - smaller;
                result[i] = partial - carry;
                carry = Long.compareUnsigned(greater, smaller) < 0 || Long.compareUnsigned(partial, carry) < 0 ? 1 : 0;
            }
        }
        return roundDyadic(comparison > 0 ? aneg : bneg, result, scale);
    }

    private static int compareAligned(long[] a, int ashift, long[] b, int bshift, int capacity) {
        for (int i = capacity - 1; i >= 0; i--) {
            int comparison = Long.compareUnsigned(shiftedWord(a, i, -ashift), shiftedWord(b, i, -bshift));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    static Binary640 multiply(Binary640 a, Binary640 b) {
        if (a.isNaN() || b.isNaN() || a.isZero() && b.isInfinite() || a.isInfinite() && b.isZero()) {
            return Binary640.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isInfinite()) {
            return Binary640.infinity(negative);
        }
        if (a.isZero() || b.isZero()) {
            return Binary640.zero(negative);
        }
        return roundDyadic(negative, UnsignedWords.multiply(magnitude(a), magnitude(b)), a.scaleExponent() + b.scaleExponent());
    }

    static Binary640 divide(Binary640 a, Binary640 b) {
        if (a.isNaN() || b.isNaN() || a.isZero() && b.isZero() || a.isInfinite() && b.isInfinite()) {
            return Binary640.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isZero()) {
            return Binary640.infinity(negative);
        }
        if (a.isZero() || b.isInfinite()) {
            return Binary640.zero(negative);
        }
        return roundRational(negative, magnitude(a), magnitude(b), a.scaleExponent() - b.scaleExponent());
    }

    static Binary640 remainder(Binary640 a, Binary640 b, boolean nearest) {
        if (a.isNaN() || b.isNaN() || a.isInfinite() || b.isZero()) {
            return Binary640.NaN;
        }
        if (a.isZero() || b.isInfinite()) {
            return a;
        }
        long[] am = magnitude(a), bm = magnitude(b);
        long as = a.scaleExponent(), bs = b.scaleExponent();
        long ae = exponent(am, as), be = exponent(bm, bs);
        if (ae + (nearest ? 1 : 0) < be) {
            return a;
        }
        long difference = as - bs, scale;
        long[] remainder;
        boolean odd;
        if (difference >= 0) {
            long[] modulus = nearest ? UnsignedWords.left(bm, 1) : bm;
            long[] reduced = difference <= 616
                    ? UnsignedWords.remainder(UnsignedWords.left(am, (int) difference), modulus)
                    : UnsignedWords.remainder(UnsignedWords.multiply(am, UnsignedWords.powerOfTwoMod(difference, modulus)), modulus);
            odd = nearest && UnsignedWords.compare(reduced, bm) >= 0;
            remainder = odd ? UnsignedWords.subtract(reduced, bm) : reduced;
            scale = bs;
        } else {
            // 通过实际指数比较后，仍需对齐的除数最多只比被除数多一位。
            if (!nearest) {
                return a;
            }
            bm = UnsignedWords.left(bm, Math.toIntExact(-difference));
            remainder = am;
            odd = false;
            scale = as;
        }
        boolean negative = a.signBit();
        if (nearest) {
            int half = UnsignedWords.compare(UnsignedWords.left(remainder, 1), bm);
            if (half > 0 || half == 0 && odd) {
                remainder = UnsignedWords.subtract(bm, remainder);
                negative = !negative;
            }
        }
        return roundDyadic(negative, remainder, scale);
    }

    static Binary640 fma(Binary640 a, Binary640 b, Binary640 c) {
        if (a.isNaN() || b.isNaN() || c.isNaN() || a.isZero() && b.isInfinite() || a.isInfinite() && b.isZero()) {
            return Binary640.NaN;
        }
        boolean negative = a.signBit() ^ b.signBit();
        if (a.isInfinite() || b.isInfinite()) {
            return c.isInfinite() && negative != c.signBit() ? Binary640.NaN : Binary640.infinity(negative);
        }
        if (c.isInfinite()) {
            return c;
        }
        if (a.isZero() || b.isZero()) {
            return c.isZero() ? Binary640.zero(negative && c.signBit()) : c;
        }
        long[] product = UnsignedWords.multiply(magnitude(a), magnitude(b));
        long scale = a.scaleExponent() + b.scaleExponent();
        if (c.isZero()) {
            return roundDyadic(negative, product, scale);
        }
        long[] cm = magnitude(c);
        long pe = exponent(product, scale), ce = exponent(cm, c.scaleExponent());
        if (pe >= Binary640.FORMAT.maxExponent() + 2L) {
            return Binary640.infinity(negative);
        }
        long difference = pe - ce;
        if (difference <= -618) {
            return c;
        }
        if (difference >= 1233) {
            return productWithTail(negative, product, scale, pe, negative == c.signBit());
        }
        return sum(negative, product, scale, c.signBit(), cm, c.scaleExponent(), 29);
    }

    /** 非零尾项小于乘积网格间距，只在精确中点决定向哪一侧舍入。 */
    private static Binary640 productWithTail(boolean negative, long[] product, long scale, long exponent, boolean increases) {
        if (exponent > Binary640.FORMAT.maxExponent()) {
            return Binary640.infinity(negative);
        }
        int discarded = UnsignedWords.bitLength(product) - 616;
        if (discarded > 0 && UnsignedWords.bit(product, discarded - 1) && !UnsignedWords.anyLow(product, discarded - 1)) {
            long[] kept = new long[10];
            for (int i = 0; i < kept.length; i++) {
                kept[i] = shiftedWord(product, i, discarded);
            }
            return packRounded(negative, kept, scale + discarded, increases);
        }
        return roundDyadic(negative, product, scale);
    }

    static Binary640 sqrt(Binary640 value) {
        if (value.isNaN() || value.signBit() && !value.isZero()) {
            return Binary640.NaN;
        }
        if (value.isZero() || value.isInfinite()) {
            return value;
        }
        long[] magnitude = magnitude(value);
        long exponent = exponent(magnitude, value.scaleExponent());
        long quantum = Math.floorDiv(exponent, 2) - 615;
        int shift = Math.toIntExact(value.scaleExponent() - 2 * quantum);
        long[] radicand = new long[20];
        for (int i = 0; i < radicand.length; i++) {
            radicand[i] = shiftedWord(magnitude, i, -shift);
        }
        long[] root = new long[10], remainder = new long[10], trial = new long[10];
        for (int pair = 615; pair >= 0; pair--) {
            shiftInPlace(remainder, 2);
            remainder[0] |= (radicand[pair / 32] >>> ((pair & 31) * 2)) & 3;
            System.arraycopy(root, 0, trial, 0, root.length);
            shiftInPlace(trial, 2);
            trial[0] |= 1;
            boolean subtract = UnsignedWords.compare(remainder, trial) >= 0;
            if (subtract) {
                subtractInPlace(remainder, trial);
            }
            shiftInPlace(root, 1);
            if (subtract) {
                root[0] |= 1;
            }
        }
        // N=S²+R，中点平方为 S²+S+1/4，整数 N 向上舍入恰好等价于 R>S。
        return packRounded(false, root, quantum, UnsignedWords.compare(remainder, root) > 0);
    }

    private static void shiftInPlace(long[] words, int bits) {
        long carry = 0;
        for (int i = 0; i < words.length; i++) {
            long word = words[i];
            words[i] = (word << bits) | carry;
            carry = word >>> (64 - bits);
        }
    }

    private static void subtractInPlace(long[] target, long[] value) {
        long borrow = 0;
        for (int i = 0; i < target.length; i++) {
            long word = target[i], difference = word - value[i];
            target[i] = difference - borrow;
            borrow = Long.compareUnsigned(word, value[i]) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
        }
    }
}
