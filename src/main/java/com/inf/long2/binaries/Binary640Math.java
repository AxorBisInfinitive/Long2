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

    /** kept 独占且至少十组，至多 616 位，额外高组为零；舍入与归一化在最终位权完成。 */
    private static Binary640 packRounded(boolean negative, long[] kept, long quantum, boolean increment) {
        if (increment) {
            for (int i = 0; i < kept.length; i++) {
                if (++kept[i] != 0) {
                    break;
                }
            }
        }
        if ((kept[9] & (Binary640.HIDDEN_BIT << 1)) != 0) {
            for (int i = 0; i < 9; i++) {
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
        long ar = a.getRawLong(0), br = b.getRawLong(0);
        int ac = (int) ((ar >>> 39) & 0xffffff), bc = (int) ((br >>> 39) & 0xffffff);
        int encodedDifference = ac - bc;
        // 混合输入的实际指数间距至少等于编码间距；618 保证二的幂向下相减也不越过舍入中点。
        if (encodedDifference >= 618) {
            return a;
        }
        if (encodedDifference <= -618) {
            return subtract ? b.negate() : b;
        }
        long atop = (ar & Binary640.FRACTION_MASK) | (ac == 0 ? 0 : Binary640.HIDDEN_BIT);
        long btop = (br & Binary640.FRACTION_MASK) | (bc == 0 ? 0 : Binary640.HIDDEN_BIT);
        if (encodedDifference == 0) {
            return ac == 0 ? addSubnormal(a, atop, aneg, b, btop, bneg)
                    : addSameExponent(a, atop, aneg, b, btop, bneg, ac);
        }
        if (ac != 0 && bc != 0) {
            if (encodedDifference > 0 && encodedDifference < 64) {
                return addNearNormal(a, atop, aneg, b, btop, bneg, encodedDifference, bc - (long) Binary640.BIAS - 615);
            }
            if (encodedDifference < 0 && encodedDifference > -64) {
                return addNearNormal(b, btop, bneg, a, atop, aneg, -encodedDifference, ac - (long) Binary640.BIAS - 615);
            }
        }
        long as = ac == 0 ? Binary640.QMIN : ac - (long) Binary640.BIAS - 615;
        long bs = bc == 0 ? Binary640.QMIN : bc - (long) Binary640.BIAS - 615;
        long ae = as + (ac == 0 ? finiteLength(a, atop) : 616) - 1;
        long be = bs + (bc == 0 ? finiteLength(b, btop) : 616) - 1;
        long difference = ae - be;
        // 编码差不足 618 的混合输入仍可能相距至少 618，必须按实际指数再次判断。
        if (difference >= 618) {
            return a;
        }
        if (difference <= -618) {
            return subtract ? b.negate() : b;
        }
        boolean adding = aneg == bneg;
        int comparison = 1;
        if (!adding) {
            comparison = difference == 0 ? compareFiniteMagnitude(a, b) : Long.compare(ae, be);
            if (comparison == 0) {
                return Binary640.ZERO;
            }
        }
        long scale = Math.min(as, bs);
        int ashift = Math.toIntExact(as - scale), bshift = Math.toIntExact(bs - scale);
        int capacity = Math.toIntExact((Math.max(ae, be) - scale + 1 + (adding ? 1 : 0) + 63) / 64);
        long[] result = new long[Math.max(10, capacity)];
        if (adding) {
            addFiniteWords(a, atop, ashift, b, btop, bshift, result, capacity);
        } else if (comparison > 0) {
            subtractFiniteWords(a, atop, ashift, b, btop, bshift, result, capacity);
        } else {
            subtractFiniteWords(b, btop, bshift, a, atop, ashift, result, capacity);
        }
        return roundOwnedSum(comparison > 0 ? aneg : bneg, result, scale);
    }

    /** 双次正规输入的精确幅值小于 2^616，在 QMIN 位权上直接对应正常或次正规编码。 */
    private static Binary640 addSubnormal(Binary640 a, long atop, boolean aneg,
            Binary640 b, long btop, boolean bneg) {
        boolean adding = aneg == bneg;
        int comparison = adding ? 1 : compareFiniteMagnitude(a, b);
        if (comparison == 0) {
            return Binary640.ZERO;
        }
        long[] result = new long[10];
        if (adding) {
            addFiniteWords(a, atop, 0, b, btop, 0, result, 10);
        } else if (comparison > 0) {
            subtractFiniteWords(a, atop, 0, b, btop, 0, result, 10);
        } else {
            subtractFiniteWords(b, btop, 0, a, atop, 0, result, 10);
        }
        assert (result[9] >>> 40) == 0;
        result[9] |= (comparison > 0 ? aneg : bneg) ? Long.MIN_VALUE : 0;
        return raw(result);
    }

    /** 同指数正常数相加恰为 617 位，相减至多 615 位，分别只需单比特舍入或精确归一化。 */
    private static Binary640 addSameExponent(Binary640 a, long atop, boolean aneg,
            Binary640 b, long btop, boolean bneg, int encoded) {
        if (aneg == bneg) {
            if (encoded == 0xfffffe) {
                return Binary640.infinity(aneg);
            }
            long[] result = new long[10];
            addFiniteWords(a, atop, 0, b, btop, 0, result, 10);
            boolean increment = (result[0] & 3) == 3;
            for (int i = 0; i < 9; i++) {
                result[i] = (result[i] >>> 1) | (result[i + 1] << 63);
            }
            result[9] >>>= 1;
            if (increment) {
                for (int i = 0; i < 10; i++) {
                    if (++result[i] != 0) {
                        break;
                    }
                }
            }
            // 精确和至多 2^617-2，舍入后不可能再次超出 616 位。
            assert (result[9] & (Binary640.HIDDEN_BIT << 1)) == 0;
            result[9] = (aneg ? Long.MIN_VALUE : 0) | ((encoded + 1L) << 39) | (result[9] & Binary640.FRACTION_MASK);
            return raw(result);
        }
        int comparison = compareFiniteMagnitude(a, b);
        if (comparison == 0) {
            return Binary640.ZERO;
        }
        boolean negative = comparison > 0 ? aneg : bneg;
        long[] result = new long[10];
        if (comparison > 0) {
            subtractFiniteWords(a, atop, 0, b, btop, 0, result, 10);
        } else {
            subtractFiniteWords(b, btop, 0, a, atop, 0, result, 10);
        }
        if (encoded == 1) {
            result[9] |= negative ? Long.MIN_VALUE : 0;
            return raw(result);
        }
        int length = UnsignedWords.bitLength(result);
        long scale = encoded - (long) Binary640.BIAS - 615;
        long exponent = scale + length - 1;
        int shift = (int) Math.min(616 - length, scale - Binary640.QMIN);
        if (shift != 0) {
            for (int i = 9; i >= 0; i--) {
                result[i] = shiftedWord(result, i, -shift);
            }
        }
        long resultEncoded = exponent < Binary640.FORMAT.minExponent() ? 0 : exponent + Binary640.BIAS;
        result[9] = (negative ? Long.MIN_VALUE : 0) | (resultEncoded << 39) | (result[9] & Binary640.FRACTION_MASK);
        return raw(result);
    }

    /** 两侧正常且指数差为 1 至 63，较大项左移，较小项原样读取，完整保存精确结果。 */
    private static Binary640 addNearNormal(Binary640 high, long highTop, boolean highNegative,
            Binary640 low, long lowTop, boolean lowNegative, int shift, long scale) {
        boolean adding = highNegative == lowNegative;
        int count = (616 + shift + (adding ? 1 : 0) + 63) / 64;
        long[] result = new long[count];
        long previous = 0;
        if (adding) {
            long carry = 0;
            for (int i = 0; i < 10; i++) {
                long source = finiteWord(high, highTop, i);
                long av = (source << shift) | previous;
                previous = source >>> (64 - shift);
                long bv = finiteWord(low, lowTop, i);
                long partial = av + bv;
                long next = partial + carry;
                carry = Long.compareUnsigned(partial, av) < 0 || Long.compareUnsigned(next, partial) < 0 ? 1 : 0;
                result[i] = next;
            }
            if (count == 11) {
                result[10] = previous + carry;
            } else {
                assert previous == 0 && carry == 0;
            }
        } else {
            long borrow = 0;
            for (int i = 0; i < 10; i++) {
                long source = finiteWord(high, highTop, i);
                long av = (source << shift) | previous;
                previous = source >>> (64 - shift);
                long bv = finiteWord(low, lowTop, i);
                long partial = av - bv;
                result[i] = partial - borrow;
                borrow = Long.compareUnsigned(av, bv) < 0 || Long.compareUnsigned(partial, borrow) < 0 ? 1 : 0;
            }
            if (count == 11) {
                assert Long.compareUnsigned(previous, borrow) >= 0;
                result[10] = previous - borrow;
            } else {
                assert previous == 0 && borrow == 0;
            }
        }
        return roundOwnedSum(highNegative, result, scale);
    }

    /** 加减法独占工作区至少十组，先读取全部舍入信息，再按安全方向原地整理。 */
    private static Binary640 roundOwnedSum(boolean negative, long[] magnitude, long scale) {
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
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && (UnsignedWords.bit(magnitude, discarded) || UnsignedWords.anyLow(magnitude, discarded - 1));
        if (discarded > 0) {
            for (int i = 0; i < 10; i++) {
                magnitude[i] = shiftedWord(magnitude, i, discarded);
            }
        } else if (discarded < 0) {
            for (int i = 9; i >= 0; i--) {
                magnitude[i] = shiftedWord(magnitude, i, discarded);
            }
        }
        // 原高组已参与移位，必须在全部目标组写完后清除，避免被 packRounded 重新视作有效位。
        Arrays.fill(magnitude, 10, magnitude.length, 0);
        return packRounded(negative, magnitude, quantum, increment);
    }

    /** 有限非零幅值，最高组已经清除编码字段并补入正常数隐含位。 */
    private static int finiteLength(Binary640 value, long top) {
        if (top != 0) {
            return 640 - Long.numberOfLeadingZeros(top);
        }
        for (int index = 1; index < 10; index++) {
            long word = value.getRawLong(index);
            if (word != 0) {
                return (10 - index) * 64 - Long.numberOfLeadingZeros(word);
            }
        }
        return 0;
    }

    /** 有限非零数去掉符号位后的原始编码与幅值顺序一致。 */
    private static int compareFiniteMagnitude(Binary640 a, Binary640 b) {
        int comparison = Long.compareUnsigned(a.getRawLong(0) & Long.MAX_VALUE, b.getRawLong(0) & Long.MAX_VALUE);
        for (int index = 1; comparison == 0 && index < 10; index++) {
            comparison = Long.compareUnsigned(a.getRawLong(index), b.getRawLong(index));
        }
        return comparison;
    }

    private static long finiteWord(Binary640 value, long top, int index) {
        return index < 0 || index >= 10 ? 0 : index == 9 ? top : value.getRawLong(9 - index);
    }

    /** 只左移完整输入幅值，工作区范围外由容量证明为零。 */
    private static long alignedFiniteWord(Binary640 value, long top, int index, int shift) {
        int group = index - (shift >>> 6), bits = shift & 63;
        long low = finiteWord(value, top, group);
        return bits == 0 ? low : (low << bits) | (finiteWord(value, top, group - 1) >>> (64 - bits));
    }

    private static void addFiniteWords(Binary640 a, long atop, int ashift,
            Binary640 b, long btop, int bshift, long[] result, int count) {
        long carry = 0;
        if ((ashift | bshift) == 0) {
            for (int i = 0; i < count; i++) {
                long av = finiteWord(a, atop, i), bv = finiteWord(b, btop, i);
                long partial = av + bv;
                long next = partial + carry;
                carry = Long.compareUnsigned(partial, av) < 0 || Long.compareUnsigned(next, partial) < 0 ? 1 : 0;
                result[i] = next;
            }
        } else {
            for (int i = 0; i < count; i++) {
                long av = alignedFiniteWord(a, atop, i, ashift), bv = alignedFiniteWord(b, btop, i, bshift);
                long partial = av + bv;
                long next = partial + carry;
                carry = Long.compareUnsigned(partial, av) < 0 || Long.compareUnsigned(next, partial) < 0 ? 1 : 0;
                result[i] = next;
            }
        }
        assert carry == 0;
    }

    /** a 的幅值不小于 b，结果容量覆盖对齐后的所有输入位。 */
    private static void subtractFiniteWords(Binary640 a, long atop, int ashift,
            Binary640 b, long btop, int bshift, long[] result, int count) {
        long borrow = 0;
        if ((ashift | bshift) == 0) {
            for (int i = 0; i < count; i++) {
                long av = finiteWord(a, atop, i), bv = finiteWord(b, btop, i);
                long partial = av - bv;
                result[i] = partial - borrow;
                borrow = Long.compareUnsigned(av, bv) < 0 || Long.compareUnsigned(partial, borrow) < 0 ? 1 : 0;
            }
        } else {
            for (int i = 0; i < count; i++) {
                long av = alignedFiniteWord(a, atop, i, ashift), bv = alignedFiniteWord(b, btop, i, bshift);
                long partial = av - bv;
                result[i] = partial - borrow;
                borrow = Long.compareUnsigned(av, bv) < 0 || Long.compareUnsigned(partial, borrow) < 0 ? 1 : 0;
            }
        }
        assert borrow == 0;
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
