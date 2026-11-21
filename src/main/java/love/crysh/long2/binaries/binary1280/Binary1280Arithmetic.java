package love.crysh.long2.binaries.binary1280;

import love.crysh.long2.binaries.internal.UnsignedWords;

import static love.crysh.long2.binaries.binary1280.Binary1280Encoding.*;
import static love.crysh.long2.binaries.binary1280.Binary1280Rounding.*;

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
        if ((ac | bc) == 1) {
            long atop = ar & FRACTION_MASK, btop = br & FRACTION_MASK;
            return ac == 1 ? addAtMinimumScale(a, atop | HIDDEN_BIT, aNegative, b, btop, bNegative)
                    : addAtMinimumScale(b, btop | HIDDEN_BIT, bNegative, a, atop, aNegative);
        }
        if (ac != 0 && bc != 0) {
            if (gap == PRECISION + 1) {
                return addAtFarBoundary(a, ar, ac, b, aNegative != bNegative, false);
            }
            if (gap == -(PRECISION + 1)) {
                return addAtFarBoundary(b, br, bc, a, aNegative != bNegative, subtract);
            }
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

    /** 两个正常数指数差为 p+1，只有从二次幂朝零越过下侧中点时改变幅值。 */
    private static Binary1280 addAtFarBoundary(Binary1280 high, long highRaw, int highEncoded,
            Binary1280 low, boolean oppositeSigns, boolean flipHigh) {
        if (oppositeSigns && (highRaw & FRACTION_MASK) == 0 && high.emptyFraction() && !low.emptyFraction()) {
            return Binary1280.belowNormalPowerOfTwo(high.signBit() ^ flipHigh, highEncoded);
        }
        return flipHigh ? high.negate() : high;
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
            return encodeCarriedSum(result, aNegative, encoded + 1);
        }
        int highest = highestDifferentWord(a, atop, b, btop);
        if (highest < 0) {
            return Binary1280.ZERO;
        }
        long aw = finiteWord(a, atop, highest), bw = finiteWord(b, btop, highest);
        boolean aLarger = Long.compareUnsigned(aw, bw) > 0;
        boolean negative = aLarger ? aNegative : bNegative;
        if (highest == 0) {
            return fromExactWord(negative, aLarger ? aw - bw : bw - aw, encoded - 1);
        }
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

    /** 最低正常数区间和次正规数共享 QMIN。正常数幅值一定占优。 */
    private static Binary1280 addAtMinimumScale(Binary1280 normal, long normalTop, boolean normalNegative,
            Binary1280 subnormal, long subnormalTop, boolean subnormalNegative) {
        long[] result;
        if (normalNegative == subnormalNegative) {
            result = addEqualScale(normal, normalTop, subnormal, subnormalTop);
            if ((result[WORDS - 1] & (HIDDEN_BIT << 1)) != 0) {
                return encodeCarriedSum(result, normalNegative, 2);
            }
        } else {
            result = subtractEqualScale(normal, normalTop, subnormal, subnormalTop, WORDS - 1);
        }
        result[WORDS - 1] |= normalNegative ? Long.MIN_VALUE : 0;
        return raw(result);
    }

    /** 除以二并按最近偶数舍入，再写入进位后的指数。 */
    private static Binary1280 encodeCarriedSum(long[] result, boolean negative, int encoded) {
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
        result[WORDS - 1] = (negative ? Long.MIN_VALUE : 0)
                | ((long) encoded << TOP_FRACTION_BITS) | (result[WORDS - 1] & FRACTION_MASK);
        return raw(result);
    }

    private static long finiteWord(Binary1280 value, long top, int index) {
        return index == WORDS - 1 ? top : value.getRawLong(WORDS - 1 - index);
    }

    private static int highestDifferentWord(Binary1280 a, long atop, Binary1280 b, long btop) {
        if (atop != btop) {
            return WORDS - 1;
        }
        if (a.sameMiddleWords(b)) {
            return a.getRawLong(WORDS - 1) == b.getRawLong(WORDS - 1) ? -1 : 0;
        }
        for (int i = WORDS - 2; i >= 0; i--) {
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
            long aw = finiteWord(a, atop, highest), bw = finiteWord(b, btop, highest);
            boolean aLarger = Long.compareUnsigned(aw, bw) > 0;
            if (highest == 0) {
                return fromExactWord(aLarger ? aNegative : bNegative, aLarger ? aw - bw : bw - aw, 0);
            }
            if (aLarger) {
                result = subtractEqualScale(a, atop, b, btop, highest);
            } else {
                result = subtractEqualScale(b, btop, a, atop, highest);
                negative = bNegative;
            }
        }
        result[WORDS - 1] |= negative ? Long.MIN_VALUE : 0;
        return raw(result);
    }

    /** 编码非零无符号单组幅值，原位权为 2^(QMIN + offset)。 */
    private static Binary1280 fromExactWord(boolean negative, long magnitude, int offset) {
        long sign = negative ? Long.MIN_VALUE : 0;
        int length = Long.SIZE - Long.numberOfLeadingZeros(magnitude);
        int normalize = PRECISION - length;
        if (offset >= normalize) {
            // 归一化后仅占最高一组或两组。
            int distance = (WORDS - 1) * Long.SIZE - normalize;
            long top = distance <= 0 ? magnitude << -distance : magnitude >>> distance;
            long next = distance <= 0 ? 0 : magnitude << (Long.SIZE - distance);
            long encoded = offset - (long) normalize + 1;
            return Binary1280.fromRawBits(sign | (encoded << TOP_FRACTION_BITS) | (top & FRACTION_MASK),
                    next, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        int group = offset / Long.SIZE;
        int bits = offset % Long.SIZE;
        long low = magnitude << bits, high = bits == 0 ? 0 : magnitude >>> (Long.SIZE - bits);
        return Binary1280.fromRawBits(sign | shiftedPairWord(19 - group, low, high),
                shiftedPairWord(18 - group, low, high), shiftedPairWord(17 - group, low, high),
                shiftedPairWord(16 - group, low, high), shiftedPairWord(15 - group, low, high),
                shiftedPairWord(14 - group, low, high), shiftedPairWord(13 - group, low, high),
                shiftedPairWord(12 - group, low, high), shiftedPairWord(11 - group, low, high),
                shiftedPairWord(10 - group, low, high), shiftedPairWord(9 - group, low, high),
                shiftedPairWord(8 - group, low, high), shiftedPairWord(7 - group, low, high),
                shiftedPairWord(6 - group, low, high), shiftedPairWord(5 - group, low, high),
                shiftedPairWord(4 - group, low, high), shiftedPairWord(3 - group, low, high),
                shiftedPairWord(2 - group, low, high), shiftedPairWord(1 - group, low, high),
                shiftedPairWord(-group, low, high));
    }

    private static long shiftedPairWord(int index, long low, long high) {
        return switch (index) {
            case 0 -> low;
            case 1 -> high;
            default -> 0;
        };
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
        long aw = a.compactNormalSignificand();
        if (aw != 0) {
            long bw = b.compactNormalSignificand();
            if (bw != 0) {
                long scale = a.scaleExponent() + b.scaleExponent() + 2L * (PRECISION - Long.SIZE);
                return multiplyShortWords(negative, aw, bw, scale);
            }
            return multiplyWordByValue(negative, aw, a.scaleExponent() + PRECISION - Long.SIZE, b);
        }
        if (a.getRawLong(WORDS - 1) != 0 && b.getRawLong(WORDS - 1) != 0 && a.isNormal() && b.isNormal()) {
            long scale = a.scaleExponent() + b.scaleExponent();
            long exponentSum = scale + 2L * FRACTION_BITS;
            if (exponentSum >= EMIN && exponentSum < EMAX) {
                return multiplyBoundedHigh(negative, a, magnitude(b), scale);
            }
        }
        long bw = b.compactNormalSignificand();
        if (bw != 0) {
            return multiplyWordByValue(negative, bw, b.scaleExponent() + PRECISION - Long.SIZE, a);
        }
        Binary1280 shortProduct = multiply65IfDense(negative, a, b);
        if (shortProduct != null) {
            return shortProduct;
        }
        Product product = multiplyFinite(a, b);
        return roundDyadic(negative, product.magnitude(), product.scale());
    }

    private static Binary1280 multiply65IfDense(boolean negative, Binary1280 a, Binary1280 b) {
        boolean aDense = a.getRawLong(WORDS - 1) != 0, bDense = b.getRawLong(WORDS - 1) != 0;
        if (aDense == bDense) {
            return null;
        }
        Binary1280 dense = aDense ? a : b, shorter = aDense ? b : a;
        if (!dense.isNormal()) {
            return null;
        }
        long low = shorter.compact65NormalLow();
        return low == 0 ? null : multiply65ByDense(negative, low, shorter.scaleExponent(), dense);
    }

    private static Binary1280 multiply65ByDense(boolean negative, long low, long scale, Binary1280 dense) {
        long top = (dense.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT;
        long[] product = new long[WORDS + 1];
        long carry = 0, previous = 0;
        for (int i = 0; i < WORDS; i++) {
            long word = finiteWord(dense, top, i);
            long lo = low * word;
            // (2^64 + low) * dense，将错位的一行和乘积行合并写出。
            long sum = lo + previous, next = sum + carry;
            carry = Math.unsignedMultiplyHigh(low, word) + (Long.compareUnsigned(sum, lo) < 0 ? 1 : 0)
                    + (Long.compareUnsigned(next, sum) < 0 ? 1 : 0);
            product[i] = next;
            previous = word;
        }
        // 完整积至多 PRECISION + 65 位，最高组内的和不会溢出。
        product[WORDS] = carry + previous;
        return roundOwnedWord65Product(negative, product, scale + dense.scaleExponent() + PRECISION - Long.SIZE - 1);
    }

    /** 两个正常数的精确幅值分别为 65 位和 1252 位，正常结果仅需右移 64 或 65 位。 */
    private static Binary1280 roundOwnedWord65Product(boolean negative, long[] product, long scale) {
        boolean extraBit = (product[WORDS] & (HIDDEN_BIT << 1)) != 0;
        long exponent = scale + (long) WORDS * Long.SIZE + TOP_FRACTION_BITS + (extraBit ? 1 : 0);
        if (exponent < EMIN) {
            return roundDyadic(negative, product, scale);
        }
        boolean increment;
        if (extraBit) {
            increment = (product[1] & 1) != 0 && (product[0] != 0 || (product[1] & 2) != 0);
            for (int i = 0; i < WORDS - 1; i++) {
                product[i] = (product[i + 1] >>> 1) | (product[i + 2] << 63);
            }
            product[WORDS - 1] = product[WORDS] >>> 1;
        } else {
            increment = product[0] < 0 && ((product[0] << 1) != 0 || (product[1] & 1) != 0);
            System.arraycopy(product, 1, product, 0, WORDS);
        }
        if (increment) {
            for (int i = 0; i < WORDS; i++) {
                if (++product[i] != 0) {
                    break;
                }
            }
            if ((product[WORDS - 1] & (HIDDEN_BIT << 1)) != 0) {
                exponent++;
            }
        }
        if (exponent > EMAX) {
            return infinity(negative);
        }
        product[WORDS - 1] = (negative ? Long.MIN_VALUE : 0) | ((exponent + BIAS) << TOP_FRACTION_BITS)
                | (product[WORDS - 1] & FRACTION_MASK);
        return raw(product);
    }

    /** 对方为有限非零值，直接从有效组读取，逐组写出精确乘积。 */
    private static Binary1280 multiplyWordByValue(boolean negative, long word, long wordScale, Binary1280 value) {
        long raw = value.getRawLong(0);
        int encoded = (int) ((raw >>> TOP_FRACTION_BITS) & EXPONENT_MASK);
        long top = (raw & FRACTION_MASK) | (encoded == 0 ? 0 : HIDDEN_BIT);
        int first = lowZeroGroups(value), last = lastNonzeroGroup(value, top, first);
        long[] product = new long[last - first + 2];
        long carry = 0;
        for (int i = first; i <= last; i++) {
            long part = finiteWord(value, top, i);
            long low = word * part;
            long next = low + carry;
            product[i - first] = next;
            carry = Math.unsignedMultiplyHigh(word, part) + (Long.compareUnsigned(next, low) < 0 ? 1 : 0);
        }
        product[product.length - 1] = carry;
        long scale = (encoded == 0 ? QMIN : encoded - (long) BIAS - FRACTION_BITS)
                + wordScale + (long) first * Long.SIZE;
        return roundDyadic(negative, product, scale);
    }

    private static Binary1280 multiplyBoundedHigh(boolean negative, Binary1280 a, long[] b, long scale) {
        int firstColumn = WORDS - 2;
        long[] window = multiplyHighWindow(a, b, firstColumn);
        long windowScale = scale + (long) firstColumn * Long.SIZE;
        int discarded = UnsignedWords.bitLength(window) - PRECISION;
        int guardShift = discarded - Long.SIZE - 1;
        long guardAndAbove = window[1] >>> guardShift;
        boolean increment = (guardAndAbove & 1) != 0;
        // 省略项之和小于 WORDS * B^(firstColumn + 1)，这里的上界恰好给第二组加 WORDS。
        long upper = window[1] + WORDS;
        boolean spansGuard = (upper >>> guardShift) != guardAndAbove;
        long belowGuard = (1L << guardShift) - 1;
        boolean lowerRoundsDownAtTie = increment && (guardAndAbove & 2) == 0
                && window[0] == 0 && (window[1] & belowGuard) == 0;
        if (spansGuard || lowerRoundsDownAtTie) {
            return roundDyadic(negative, UnsignedWords.multiply(magnitude(a), b), scale);
        }

        // 舍入方向已确定。保留位固定右移一组再移 35 或 36 位，从低组写回不会覆盖后续源位。
        int shift = discarded - Long.SIZE;
        int complement = Long.SIZE - shift;
        for (int i = 0; i < WORDS; i++) {
            window[i] = (window[i + 1] >>> shift) | (window[i + 2] << complement);
        }
        long encoded = windowScale + discarded + FRACTION_BITS + BIAS;
        if (increment) {
            for (int i = 0; i < WORDS; i++) {
                if (++window[i] != 0) {
                    break;
                }
            }
            // 进位越过隐藏位时，低组已经全零，掩码会清除最高组的进位。
            if ((window[WORDS - 1] & (HIDDEN_BIT << 1)) != 0) {
                encoded++;
            }
        }
        if (encoded >= EXPONENT_MASK) {
            return infinity(negative);
        }
        window[WORDS - 1] = (negative ? Long.MIN_VALUE : 0) | (encoded << TOP_FRACTION_BITS)
                | (window[WORDS - 1] & FRACTION_MASK);
        return raw(window);
    }

    /** 左操作数为正常数，每行只读取一个分组。窗口按 B^firstColumn 重新计位。 */
    private static long[] multiplyHighWindow(Binary1280 a, long[] b, int firstColumn) {
        long[] window = new long[2 * WORDS - firstColumn];
        long top = (a.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT;
        for (int i = 0; i < WORDS; i++) {
            long x = finiteWord(a, top, i), carry = 0;
            int start = Math.max(0, firstColumn - i);
            for (int j = start; j < WORDS; j++) {
                long y = b[j];
                int index = i + j - firstColumn;
                long low = x * y, high = Math.unsignedMultiplyHigh(x, y);
                long sum = low + window[index], next = sum + carry;
                carry = high + (Long.compareUnsigned(sum, low) < 0 ? 1L : 0L)
                        + (Long.compareUnsigned(next, sum) < 0 ? 1L : 0L);
                window[index] = next;
            }
            window[i + WORDS - firstColumn] = carry;
        }
        return window;
    }

    /** 两个紧凑正常幅值的第 63 位均为一，完整乘积为 127 或 128 位。 */
    private static Binary1280 multiplyShortWords(boolean negative, long a, long b, long scale) {
        long low = a * b, high = Math.unsignedMultiplyHigh(a, b);
        int length = high < 0 ? 128 : 127;
        long exponent = scale + length - 1;
        if (exponent > EMAX) {
            return infinity(negative);
        }
        if (exponent < EMIN) {
            return roundDyadic(negative, new long[] {low, high}, scale);
        }
        // 结果在正常区间，两个分组的精确积左对齐后至多占编码的前三组。
        int left = (PRECISION - length) % Long.SIZE;
        int right = Long.SIZE - left;
        long top = (negative ? Long.MIN_VALUE : 0) | ((exponent + BIAS) << TOP_FRACTION_BITS)
                | ((high >>> right) & FRACTION_MASK);
        return Binary1280.fromRawBits(top, (high << left) | (low >>> right), low << left,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static boolean invalidProduct(Binary1280 a, Binary1280 b) {
        return a.isNaN() || b.isNaN() || a.isZero() && b.isInfinite() || a.isInfinite() && b.isZero();
    }

    private record Product(long[] magnitude, long scale) {
    }

    private static Product multiplyFinite(Binary1280 a, Binary1280 b) {
        long ar = a.getRawLong(0), br = b.getRawLong(0);
        int ac = (int) ((ar >>> TOP_FRACTION_BITS) & EXPONENT_MASK);
        int bc = (int) ((br >>> TOP_FRACTION_BITS) & EXPONENT_MASK);
        long atop = (ar & FRACTION_MASK) | (ac == 0 ? 0 : HIDDEN_BIT);
        long btop = (br & FRACTION_MASK) | (bc == 0 ? 0 : HIDDEN_BIT);
        int az = lowZeroGroups(a), bz = lowZeroGroups(b);
        long[] am = magnitudeRange(a, atop, az), bm = magnitudeRange(b, btop, bz);
        long scale = (ac == 0 ? QMIN : ac - (long) BIAS - FRACTION_BITS)
                + (bc == 0 ? QMIN : bc - (long) BIAS - FRACTION_BITS) + 64L * (az + bz);
        return new Product(UnsignedWords.multiply(am, bm), scale);
    }

    /** 输入非零；最低可能的非零组由调用方用于修正位权。 */
    private static int lowZeroGroups(Binary1280 value) {
        int index = 0;
        while (index < WORDS - 1 && value.getRawLong(WORDS - 1 - index) == 0) {
            index++;
        }
        return index;
    }

    private static int lastNonzeroGroup(Binary1280 value, long top, int first) {
        int last = WORDS - 1;
        while (last > first && finiteWord(value, top, last) == 0) {
            last--;
        }
        return last;
    }

    private static long[] magnitudeRange(Binary1280 value, long top, int first) {
        int last = lastNonzeroGroup(value, top, first);
        long[] result = new long[last - first + 1];
        for (int i = first; i <= last; i++) {
            result[i - first] = finiteWord(value, top, i);
        }
        return result;
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
        if (zeros == 0) {
            return magnitude;
        }
        int bits = UnsignedWords.bitLength(magnitude) - zeros;
        long[] compact = new long[(bits + 63) / 64];
        for (int i = 0; i < compact.length; i++) {
            compact[i] = shiftedWord(magnitude, i, zeros);
        }
        return compact;
    }
}
