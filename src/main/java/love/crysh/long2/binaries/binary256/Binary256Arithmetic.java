package love.crysh.long2.binaries.binary256;

import love.crysh.long2.binaries.internal.UnsignedWords;

import static love.crysh.long2.binaries.binary256.Binary256.FORMAT;
import static love.crysh.long2.binaries.binary256.Binary256.NaN;
import static love.crysh.long2.binaries.binary256.Binary256.ONE;
import static love.crysh.long2.binaries.binary256.Binary256.POSITIVE_INFINITY;
import static love.crysh.long2.binaries.binary256.Binary256.ZERO;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.BIAS;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.FRACTION_MASK;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.HIDDEN_BIT;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.QMIN;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.emptyFraction;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.encodedExponent;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.fromUnsignedLong;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.infinity;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.magnitude;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.magnitudeWord;
import static love.crysh.long2.binaries.binary256.Binary256Encoding.zero;
import static love.crysh.long2.binaries.binary256.Binary256Rounding.bitLength;
import static love.crysh.long2.binaries.binary256.Binary256Rounding.fourWord;
import static love.crysh.long2.binaries.binary256.Binary256Rounding.fromDyadic;
import static love.crysh.long2.binaries.binary256.Binary256Rounding.packRounded;
import static love.crysh.long2.binaries.binary256.Binary256Rounding.roundProductWithTail;
import static love.crysh.long2.binaries.binary256.Binary256Rounding.shiftedWord;

/** Binary256 算术。整数幅值低组在前，原地操作只修改本次调用独占的数组。 */
final class Binary256Arithmetic {
    private static final int FMA_ALIGNMENT_WORDS = 12;

    private Binary256Arithmetic() {
    }

    static Binary256 add(Binary256 value, Binary256 other, boolean subtract) {
        if (value.isNaN() || other.isNaN()) {
            return NaN;
        }
        boolean negative = value.signBit();
        boolean otherNegative = other.signBit() ^ subtract;
        if (value.isInfinite() || other.isInfinite()) {
            return value.isInfinite() && other.isInfinite() && negative != otherNegative
                    ? NaN : value.isInfinite() ? value : infinity(otherNegative);
        }
        if (value.isZero()) {
            return other.isZero() ? zero(negative && otherNegative) : subtract ? other.negate() : other;
        }
        if (other.isZero()) {
            return value;
        }
        // 两项均已按 237 位编码；指数差至少 239 时，小项严格小于较大值两侧舍入边界的距离。
        int difference = magnitudeExponent(value) - magnitudeExponent(other);
        if (difference >= 239) {
            return value;
        }
        if (difference <= -239) {
            return subtract ? other.negate() : other;
        }
        return sumFinite(value, other, negative, otherNegative);
    }

    /** 有限非零输入的实际指数差小于 239，完整对齐和连同进位最多需要 476 位。 */
    private static Binary256 sumFinite(Binary256 value, Binary256 other, boolean negative, boolean otherNegative) {
        boolean subtract = negative != otherNegative;
        int comparison = subtract ? compareScaledMagnitude(value, other, 0) : 1;
        if (comparison == 0) {
            return ZERO;
        }
        Binary256 a = comparison > 0 ? value : other, b = comparison > 0 ? other : value;
        long as = a.scaleExponent(), bs = b.scaleExponent(), scale = Math.min(as, bs);
        int ashift = (int) (as - scale), bshift = (int) (bs - scale);
        long[] result = new long[8];
        long carry = 0;
        for (int i = 0; i < result.length; i++) {
            long av = alignedMagnitudeWord(a, i, ashift), bv = alignedMagnitudeWord(b, i, bshift);
            if (subtract) {
                long difference = av - bv;
                result[i] = difference - carry;
                carry = Long.compareUnsigned(av, bv) < 0 || Long.compareUnsigned(difference, carry) < 0 ? 1 : 0;
            } else {
                long sum = av + bv;
                long next = sum + carry;
                carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i] = next;
            }
        }
        return fromDyadic(comparison > 0 ? negative : otherNegative, result, scale);
    }

    static Binary256 multiply(Binary256 value, Binary256 other) {
        if (value.isNaN() || other.isNaN() || (value.isZero() && other.isInfinite()) || (value.isInfinite() && other.isZero())) {
            return NaN;
        }
        boolean negative = value.signBit() ^ other.signBit();
        if (value.isInfinite() || other.isInfinite()) {
            return infinity(negative);
        }
        if (value.isZero() || other.isZero()) {
            return zero(negative);
        }
        int minimumExponent = magnitudeExponent(value) + magnitudeExponent(other);
        // 精确积位于 [2^S, 2^(S+2))，只在整个区间都越过舍入边界时提前返回。
        if (minimumExponent >= FORMAT.maxExponent() + 1) {
            return infinity(negative);
        }
        if (minimumExponent <= QMIN - 3) {
            return zero(negative);
        }
        int power = powerOfTwoExponent(other);
        if (power != Integer.MIN_VALUE) {
            return scalbFinite(value, power, negative);
        }
        power = powerOfTwoExponent(value);
        if (power != Integer.MIN_VALUE) {
            return scalbFinite(other, power, negative);
        }
        if (encodedExponent(value) != 0 && encodedExponent(other) != 0) {
            return multiplyNormal(value, other, negative, minimumExponent - 472L);
        }
        return multiplyFinite(value, other, negative);
    }

    /** 接收有限非零输入；低零位转移到位权，不丢弃乘积中任何会影响舍入的 bit。 */
    private static Binary256 multiplyFinite(Binary256 value, Binary256 other, boolean negative) {
        long atop = magnitudeWord(value, 3), btop = magnitudeWord(other, 3);
        int abits = bitLength(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop);
        int bbits = bitLength(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop);
        int ashift = compactMagnitudeShift(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop, abits);
        int bshift = compactMagnitudeShift(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop, bbits);
        int alength = (abits - ashift + 63) / 64, blength = (bbits - bshift + 63) / 64;
        long scale = value.scaleExponent() + other.scaleExponent() + ashift + bshift;
        long a0 = shiftedWord(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop, 0, ashift);
        long b0 = shiftedWord(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop, 0, bshift);
        if (alength == 1 && blength == 1) {
            return fromDyadic(negative, a0 * b0, Math.unsignedMultiplyHigh(a0, b0), scale);
        }
        long a1 = alength > 1 ? shiftedWord(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop, 1, ashift) : 0;
        long a2 = alength > 2 ? shiftedWord(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop, 2, ashift) : 0;
        long a3 = alength > 3 ? shiftedWord(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop, 3, ashift) : 0;
        long b1 = blength > 1 ? shiftedWord(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop, 1, bshift) : 0;
        long b2 = blength > 2 ? shiftedWord(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop, 2, bshift) : 0;
        long b3 = blength > 3 ? shiftedWord(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop, 3, bshift) : 0;
        return fromDyadic(negative, multiplyMagnitude(a0, a1, a2, a3, alength, b0, b1, b2, b3, blength), scale);
    }

    /** 正常幅值恰为 237 位，只检查减少分组所需的低零位，不定位全部低零位。 */
    private static int normalMultiplyGroups(Binary256 value) {
        long mask = (1L << 45) - 1;
        if ((value.getRawLong(3) & mask) != 0) {
            return 4;
        }
        if (value.getRawLong(3) != 0 || (value.getRawLong(2) & mask) != 0) {
            return 3;
        }
        if (value.getRawLong(2) != 0 || (value.getRawLong(1) & mask) != 0) {
            return 2;
        }
        return 1;
    }

    /** 两侧为正常数；scale 是未压缩乘积的位权，各路径只移除其容量允许的低零位。 */
    private static Binary256 multiplyNormal(Binary256 value, Binary256 other, boolean negative, long scale) {
        int agroups = normalMultiplyGroups(value), bgroups = normalMultiplyGroups(other);
        if (agroups != bgroups) {
            return multiplyNormalMixed(value, other, negative, scale, agroups, bgroups);
        }
        return switch (agroups) {
            case 1 -> multiplyNormal64(value, other, negative, scale);
            case 2 -> multiplyNormal128(value, other, negative, scale);
            case 3 -> multiplyNormal192(value, other, negative, scale);
            default -> multiplyNormalFull(value, other, negative, scale);
        };
    }

    /** 两侧各移除 173 个低零位，完整 128 位乘积无需数组。 */
    private static Binary256 multiplyNormal64(Binary256 value, Binary256 other, boolean negative, long scale) {
        long a = (value.getRawLong(1) >>> 45) | (((value.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT) << 19);
        long b = (other.getRawLong(1) >>> 45) | (((other.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT) << 19);
        return fromDyadic(negative, a * b, Math.unsignedMultiplyHigh(a, b), scale + 346);
    }

    /** 两侧各移除 109 个低零位，以四组保存完整乘积。 */
    private static Binary256 multiplyNormal128(Binary256 value, Binary256 other, boolean negative, long scale) {
        long a0 = (value.getRawLong(2) >>> 45) | (value.getRawLong(1) << 19);
        long a1 = (value.getRawLong(1) >>> 45) | (((value.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT) << 19);
        long b0 = (other.getRawLong(2) >>> 45) | (other.getRawLong(1) << 19);
        long b1 = (other.getRawLong(1) >>> 45) | (((other.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT) << 19);
        return fromDyadic(negative, multiplyNormalMagnitude(a0, a1, 0, 0, 2, b0, b1, 0, 0, 2), scale + 218);
    }

    /** 两侧各移除 45 个低零位，以六组保存完整乘积。 */
    private static Binary256 multiplyNormal192(Binary256 value, Binary256 other, boolean negative, long scale) {
        long a0 = (value.getRawLong(3) >>> 45) | (value.getRawLong(2) << 19);
        long a1 = (value.getRawLong(2) >>> 45) | (value.getRawLong(1) << 19);
        long a2 = (value.getRawLong(1) >>> 45) | (((value.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT) << 19);
        long b0 = (other.getRawLong(3) >>> 45) | (other.getRawLong(2) << 19);
        long b1 = (other.getRawLong(2) >>> 45) | (other.getRawLong(1) << 19);
        long b2 = (other.getRawLong(1) >>> 45) | (((other.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT) << 19);
        return fromDyadic(negative, multiplyNormalMagnitude(a0, a1, a2, 0, 3, b0, b1, b2, 0, 3), scale + 90);
    }

    /** 两侧为正常数，直接保留原始四组幅值；scale 是未压缩乘积的位权。 */
    private static Binary256 multiplyNormalFull(Binary256 value, Binary256 other, boolean negative, long scale) {
        return fromDyadic(negative, multiplyNormalMagnitude(
                value.getRawLong(3), value.getRawLong(2), value.getRawLong(1),
                (value.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT, 4,
                other.getRawLong(3), other.getRawLong(2), other.getRawLong(1),
                (other.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT, 4), scale);
    }

    private static Binary256 multiplyNormalMixed(Binary256 value, Binary256 other, boolean negative,
            long scale, int agroups, int bgroups) {
        long adjusted = scale + (agroups == 4 ? 0 : 237 - agroups * 64) + (bgroups == 4 ? 0 : 237 - bgroups * 64);
        return fromDyadic(negative, multiplyNormalMagnitude(
                normalMultiplyWord(value, agroups, 0), normalMultiplyWord(value, agroups, 1),
                normalMultiplyWord(value, agroups, 2), normalMultiplyWord(value, agroups, 3), agroups,
                normalMultiplyWord(other, bgroups, 0), normalMultiplyWord(other, bgroups, 1),
                normalMultiplyWord(other, bgroups, 2), normalMultiplyWord(other, bgroups, 3), bgroups), adjusted);
    }

    /** groups 是正常幅值的最少组数；压缩距离只改变来源分组，组内移位固定为 45。 */
    private static long normalMultiplyWord(Binary256 value, int groups, int index) {
        if (index >= groups) {
            return 0;
        }
        long top = (value.getRawLong(0) & FRACTION_MASK) | HIDDEN_BIT;
        if (groups == 4) {
            return fourWord(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), top, index);
        }
        return switch (index + 3 - groups) {
            case 0 -> (value.getRawLong(3) >>> 45) | (value.getRawLong(2) << 19);
            case 1 -> (value.getRawLong(2) >>> 45) | (value.getRawLong(1) << 19);
            case 2 -> (value.getRawLong(1) >>> 45) | (top << 19);
            default -> 0;
        };
    }

    static Binary256 fma(Binary256 value, Binary256 multiplier, Binary256 addend) {
        if (value.isNaN() || multiplier.isNaN() || addend.isNaN()
                || (value.isZero() && multiplier.isInfinite()) || (value.isInfinite() && multiplier.isZero())) {
            return NaN;
        }
        boolean negative = value.signBit() ^ multiplier.signBit();
        if (value.isInfinite() || multiplier.isInfinite()) {
            return addend.isInfinite() && negative != addend.signBit() ? NaN : infinity(negative);
        }
        if (addend.isInfinite()) {
            return addend;
        }
        if ((multiplier.getRawLong(0) & Long.MAX_VALUE) == ONE.getRawLong(0)
                && (multiplier.getRawLong(1) | multiplier.getRawLong(2) | multiplier.getRawLong(3)) == 0) {
            return multiplier.signBit() ? addend.subtract(value) : value.add(addend);
        }
        return fmaFinite(value, multiplier, addend, negative);
    }

    private static Binary256 fmaFinite(Binary256 value, Binary256 multiplier, Binary256 addend, boolean negative) {
        if (value.isZero() || multiplier.isZero()) {
            return addend.isZero() ? zero(negative && addend.signBit()) : addend;
        }
        int minimumProductExponent = magnitudeExponent(value) + magnitudeExponent(multiplier);
        // 乘积至少为 2^(emax+2) 时，任何有限加数都不足以将结果抵消回有限范围。
        if (minimumProductExponent >= FORMAT.maxExponent() + 2) {
            return infinity(negative);
        }
        boolean addendZero = addend.isZero();
        int addendExponent = addendZero ? 0 : magnitudeExponent(addend);
        // 精确乘积的实际指数最多比两个输入指数之和大一，先用此上界省去无关乘积的构造。
        if (!addendZero && addendExponent - minimumProductExponent - 1 >= 239) {
            return addend;
        }
        long[] product = multiplyMagnitude(value, multiplier);
        long productScale = value.scaleExponent() + multiplier.scaleExponent();
        long productExponent = productScale + UnsignedWords.bitLength(product) - 1;
        if (productExponent >= FORMAT.maxExponent() + 2L) {
            return infinity(negative);
        }
        if (addendZero) {
            return fromDyadic(negative, product, productScale);
        }
        long difference = productExponent - addendExponent;
        if (difference <= -239) {
            return addend;
        }
        if (difference >= 475) {
            return roundProductWithTail(negative, product, productScale, productExponent, negative == addend.signBit());
        }
        return sumFma(negative, product, productScale, productExponent, addend, addendExponent);
    }

    private static Binary256 sumFma(boolean negative, long[] product, long productScale, long productExponent,
            Binary256 addend, int addendExponent) {
        long addendScale = addend.scaleExponent();
        long scale = Math.min(productScale, addendScale);
        // 乘积最多 474 位、加数最多 237 位；两个方向的指数差分别小于 475 和 239，连同进位最多 713 位。
        long span = Math.max(productExponent, addendExponent) - scale + 1;
        int capacity = span < 512 ? 8 : FMA_ALIGNMENT_WORDS;
        int productShift = Math.toIntExact(productScale - scale), addendShift = Math.toIntExact(addendScale - scale);
        boolean subtract = negative != addend.signBit();
        int comparison = 1;
        if (subtract) {
            comparison = Long.compare(productExponent, addendExponent);
            if (comparison == 0) {
                for (int i = capacity - 1; i >= 0; i--) {
                    comparison = Long.compareUnsigned(shiftedWord(product, i, -productShift), alignedMagnitudeWord(addend, i, addendShift));
                    if (comparison != 0) {
                        break;
                    }
                }
                if (comparison == 0) {
                    return ZERO;
                }
            }
        }
        // 仅在乘积不移位时复用其数组；按低组写入后，不会覆盖后续仍需读取的乘积分组。
        long[] result = productShift == 0 && product.length == capacity ? product : new long[capacity];
        long carry = 0;
        for (int i = 0; i < capacity; i++) {
            long av = shiftedWord(product, i, -productShift), bv = alignedMagnitudeWord(addend, i, addendShift);
            if (subtract) {
                long greater = comparison > 0 ? av : bv, smaller = comparison > 0 ? bv : av;
                long difference = greater - smaller;
                result[i] = difference - carry;
                carry = Long.compareUnsigned(greater, smaller) < 0 || Long.compareUnsigned(difference, carry) < 0 ? 1 : 0;
            } else {
                long sum = av + bv;
                long next = sum + carry;
                carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i] = next;
            }
        }
        return fromDyadic(comparison > 0 ? negative : addend.signBit(), result, scale);
    }

    /** 两值均为有限非零数，保留完整乘积，结果工作区由本次调用独占。 */
    private static long[] multiplyMagnitude(Binary256 value, Binary256 other) {
        long atop = magnitudeWord(value, 3), btop = magnitudeWord(other, 3);
        int alength = (bitLength(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop) + 63) / 64;
        int blength = (bitLength(other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop) + 63) / 64;
        return multiplyMagnitude(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), atop, alength,
                other.getRawLong(3), other.getRawLong(2), other.getRawLong(1), btop, blength);
    }

    /** 输入分组低组在前，结果保留指定容量供 FMA 对齐时复用；位权由调用方随幅值调整。 */
    private static long[] multiplyMagnitude(long a0, long a1, long a2, long a3, int alength,
            long b0, long b1, long b2, long b3, int blength) {
        int astart = a0 != 0 ? 0 : a1 != 0 ? 1 : a2 != 0 ? 2 : 3;
        int bstart = b0 != 0 ? 0 : b1 != 0 ? 1 : b2 != 0 ? 2 : 3;
        // 低零组保留原有位权和结果容量；每行跳过的低零组不产生进位，后续零组仍须传播进位。
        long[] result = new long[alength + blength];
        for (int i = astart; i < alength; i++) {
            long av = fourWord(a0, a1, a2, a3, i), carry = 0;
            for (int j = bstart; j < blength; j++) {
                long bv = fourWord(b0, b1, b2, b3, j);
                long low = av * bv, high = Math.unsignedMultiplyHigh(av, bv);
                long sum = low + result[i + j];
                high += Long.compareUnsigned(sum, low) < 0 ? 1 : 0;
                long next = sum + carry;
                high += Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i + j] = next;
                carry = high;
            }
            result[i + blength] = carry;
        }
        return result;
    }

    /** 正常幅值已按最少容量打包，组数为 1 至 4 且最低组非零，无需扫描低零组。 */
    private static long[] multiplyNormalMagnitude(long a0, long a1, long a2, long a3, int alength,
            long b0, long b1, long b2, long b3, int blength) {
        long[] result = new long[alength + blength];
        for (int i = 0; i < alength; i++) {
            long av = fourWord(a0, a1, a2, a3, i), carry = 0;
            for (int j = 0; j < blength; j++) {
                long bv = fourWord(b0, b1, b2, b3, j);
                long low = av * bv, high = Math.unsignedMultiplyHigh(av, bv);
                long sum = low + result[i + j];
                high += Long.compareUnsigned(sum, low) < 0 ? 1 : 0;
                long next = sum + carry;
                high += Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i + j] = next;
                carry = high;
            }
            result[i + blength] = carry;
        }
        return result;
    }

    /** 幅值非零；只有右移能减少 long 分组数时才压缩，避免为稠密幅值增加移位工作。 */
    private static int compactMagnitudeShift(long w0, long w1, long w2, long w3, int length) {
        if (length <= 64) {
            return 0;
        }
        int bits = ((length - 1) & 63) + 1;
        // 1L << 64 会回绕为一，减少整组所需的 64 个低零位须直接检查整个最低组。
        if (bits == 64 ? w0 != 0 : (w0 & ((1L << bits) - 1)) != 0) {
            return 0;
        }
        return w0 != 0 ? Long.numberOfTrailingZeros(w0)
                : w1 != 0 ? 64 + Long.numberOfTrailingZeros(w1)
                : w2 != 0 ? 128 + Long.numberOfTrailingZeros(w2) : 192 + Long.numberOfTrailingZeros(w3);
    }

    static Binary256 divide(Binary256 value, Binary256 other) {
        if (value.isNaN() || other.isNaN() || (value.isZero() && other.isZero()) || (value.isInfinite() && other.isInfinite())) {
            return NaN;
        }
        boolean negative = value.signBit() ^ other.signBit();
        if (value.isInfinite() || other.isZero()) {
            return infinity(negative);
        }
        if (value.isZero() || other.isInfinite()) {
            return zero(negative);
        }
        long[] a = magnitude(value), b = magnitude(other);
        long scale = value.scaleExponent() - other.scaleExponent();
        int ratioExponent = UnsignedWords.bitLength(a) - UnsignedWords.bitLength(b);
        int comparison = ratioExponent >= 0 ? UnsignedWords.compare(a, UnsignedWords.left(b, ratioExponent))
                : UnsignedWords.compare(UnsignedWords.left(a, -ratioExponent), b);
        if (comparison < 0) {
            ratioExponent--;
        }
        long exponent = scale + ratioExponent;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        long shift = scale - quantum;
        if (shift >= 0) {
            a = UnsignedWords.left(a, Math.toIntExact(shift));
        } else {
            b = UnsignedWords.left(b, Math.toIntExact(-shift));
        }
        UnsignedWords.Division division = UnsignedWords.divide(a, b);
        long[] result = division.quotient();
        int half = UnsignedWords.compare(UnsignedWords.left(division.remainder(), 1), b);
        return packRounded(negative, UnsignedWords.word(result, 0), UnsignedWords.word(result, 1),
                UnsignedWords.word(result, 2), UnsignedWords.word(result, 3), quantum,
                half > 0 || (half == 0 && UnsignedWords.bit(result, 0)));
    }

    static Binary256 remainder(Binary256 value, Binary256 other, boolean nearest) {
        if (value.isNaN() || other.isNaN() || value.isInfinite() || other.isZero()) {
            return NaN;
        }
        if (value.isZero() || other.isInfinite()) {
            return value;
        }
        int comparison = compareScaledMagnitude(value, other, nearest ? 1 : 0);
        if (nearest ? comparison <= 0 : comparison < 0) {
            return value;
        }
        long[] a = magnitude(value), b = magnitude(other), remainder;
        long difference = value.scaleExponent() - other.scaleExponent();
        long scale;
        boolean odd;
        if (difference >= 0) {
            int divisorPower = powerOfTwoExponent(other);
            int modulusBits = divisorPower == Integer.MIN_VALUE ? -1 : (int) (divisorPower - other.scaleExponent()) + (nearest ? 1 : 0);
            if (modulusBits >= 0 && difference >= modulusBits) {
                return zero(value.signBit());
            }
            long[] modulus = nearest ? UnsignedWords.left(b, 1) : b;
            long[] reduced;
            if (modulusBits >= 0) {
                reduced = UnsignedWords.left(UnsignedWords.lowBits(a, modulusBits - (int) difference), (int) difference);
            } else if (difference <= 237) {
                // 对齐后的被除数不超过 474 位，可直接求余而无须模重复平方。
                reduced = UnsignedWords.remainder(UnsignedWords.left(a, (int) difference), modulus);
            } else {
                long[] power = UnsignedWords.powerOfTwoMod(difference, modulus);
                reduced = UnsignedWords.remainder(UnsignedWords.multiply(a, power), modulus);
            }
            odd = nearest && UnsignedWords.compare(reduced, b) >= 0;
            remainder = odd ? UnsignedWords.subtract(reduced, b) : reduced;
            scale = other.scaleExponent();
        } else {
            // 位权较大的除数必为正常数，对齐后大于被除数，向零截断的整数商为零。
            if (!nearest || UnsignedWords.bitLength(b) - difference > UnsignedWords.bitLength(a) + 1L) {
                return value;
            }
            b = UnsignedWords.left(b, Math.toIntExact(-difference));
            remainder = a;
            odd = false;
            scale = value.scaleExponent();
        }
        boolean negative = value.signBit();
        if (nearest) {
            int half = UnsignedWords.compare(UnsignedWords.left(remainder, 1), b);
            if (half > 0 || (half == 0 && odd)) {
                remainder = UnsignedWords.subtract(b, remainder);
                negative = !negative;
            }
        }
        return fromDyadic(negative, remainder, scale);
    }

    /** 两值均为有限非零数；只调整比较位权，不对接收值执行浮点舍入。 */
    private static int compareScaledMagnitude(Binary256 value, Binary256 other, int scale) {
        int exponent = magnitudeExponent(value), otherExponent = magnitudeExponent(other);
        int comparison = Integer.compare(exponent + scale, otherExponent);
        if (comparison != 0) {
            return comparison;
        }
        int shift = (int) (236 - exponent + value.scaleExponent());
        int otherShift = (int) (236 - otherExponent + other.scaleExponent());
        for (int group = 3; group >= 0; group--) {
            comparison = Long.compareUnsigned(alignedMagnitudeWord(value, group, shift), alignedMagnitudeWord(other, group, otherShift));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static long alignedMagnitudeWord(Binary256 value, int group, int shift) {
        int whole = shift >>> 6, bits = shift & 63;
        long word = magnitudeWord(value, group - whole) << bits;
        return bits == 0 ? word : word | (magnitudeWord(value, group - whole - 1) >>> (64 - bits));
    }

    /** 有限非零值为二次幂时返回其指数，否则返回 Integer.MIN_VALUE。 */
    private static int powerOfTwoExponent(Binary256 value) {
        int encoded = encodedExponent(value);
        if (encoded != 0) {
            return emptyFraction(value) ? encoded - BIAS : Integer.MIN_VALUE;
        }
        for (int group = 0; group < 4; group++) {
            long word = magnitudeWord(value, group);
            if (word != 0) {
                if ((word & (word - 1)) != 0) {
                    return Integer.MIN_VALUE;
                }
                for (int higher = group + 1; higher < 4; higher++) {
                    if (magnitudeWord(value, higher) != 0) {
                        return Integer.MIN_VALUE;
                    }
                }
                return QMIN + group * 64 + Long.numberOfTrailingZeros(word);
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 调用方保证值为有限非零数，次正规数按最高非零 bit 计算实际指数。 */
    private static int magnitudeExponent(Binary256 value) {
        int encoded = encodedExponent(value);
        if (encoded != 0) {
            return encoded - BIAS;
        }
        long top = value.getRawLong(0) & FRACTION_MASK;
        int length = top != 0 ? 256 - Long.numberOfLeadingZeros(top)
                : value.getRawLong(1) != 0 ? 192 - Long.numberOfLeadingZeros(value.getRawLong(1))
                : value.getRawLong(2) != 0 ? 128 - Long.numberOfLeadingZeros(value.getRawLong(2)) : 64 - Long.numberOfLeadingZeros(value.getRawLong(3));
        return QMIN + length - 1;
    }

    static Binary256 sqrt(Binary256 value) {
        if (value.isNaN() || (value.signBit() && !value.isZero())) {
            return NaN;
        }
        if (value.isZero() || value.isInfinite()) {
            return value;
        }
        // 正有限输入的平方根都在正常范围内，精确命中时可直接扩展为编码。
        int inputExponent = magnitudeExponent(value);
        long scale = value.scaleExponent(), top = magnitudeWord(value, 3);
        int length = (int) (inputExponent - scale + 1);
        int trailing = length <= 64 ? Long.numberOfTrailingZeros(value.getRawLong(3))
                : compactMagnitudeShift(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), top, length);
        long reducedScale = scale + trailing;
        if (length - trailing <= 64 && (reducedScale & 1) == 0) {
            long magnitude = shiftedWord(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), top, 0, trailing);
            // 奇数平方模 8 为一；整数根最多 32 位，其平方可按完整的无符号 long 位模式验证。
            if ((magnitude & 7) == 1) {
                long exactRoot = UnsignedWords.sqrtUnsignedLong(magnitude);
                if (exactRoot * exactRoot == magnitude) {
                    return fromUnsignedLong(false, exactRoot, reducedScale / 2);
                }
            }
        }
        long exponent = Math.floorDiv(inputExponent, 2);
        long quantum = exponent - 236;
        int shift = Math.toIntExact(scale - 2 * quantum);
        long[] workspace = new long[8];
        int comparison = UnsignedWords.sqrtScaled(value.getRawLong(3), value.getRawLong(2), value.getRawLong(1), top, shift, workspace);
        // N = S² + R，中点平方为 S² + S + 1/4；R 为整数，向上舍入恰好等价于 R > S。
        return packRounded(false, workspace[0], workspace[1], workspace[2], workspace[3], quantum, comparison > 0);
    }

    static Binary256 scalb(Binary256 value, long scaleFactor) {
        if (value.isNaN()) {
            return NaN;
        }
        if (value.isZero() || value.isInfinite()) {
            return value;
        }
        return scalbFinite(value, scaleFactor, value.signBit());
    }

    static Binary256 ulp(Binary256 value) {
        if (value.isNaN()) {
            return NaN;
        }
        if (value.isInfinite()) {
            return POSITIVE_INFINITY;
        }
        return scalb(ONE, value.scaleExponent());
    }

    /** 接收值为有限非零数，negative 已包含调用方需要的符号变换。 */
    private static Binary256 scalbFinite(Binary256 value, long scaleFactor, boolean negative) {
        int encoded = encodedExponent(value);
        // 先限制增量，再相加，保证全部 long 参数都不会使指数回绕。
        if (encoded != 0 && scaleFactor >= 1L - encoded && scaleFactor <= 0x7fffeL - encoded) {
            return Binary256.fromRawBits((negative ? Long.MIN_VALUE : 0) | (value.getRawLong(0) & FRACTION_MASK)
                    | ((encoded + scaleFactor) << 44), value.getRawLong(1), value.getRawLong(2), value.getRawLong(3));
        }
        long[] magnitude = magnitude(value);
        long scale = value.scaleExponent();
        long exponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        // 先与有限的小范围界限比较，避免将 Long.MIN_VALUE 或 Long.MAX_VALUE 直接加到指数上。
        if (scaleFactor > FORMAT.maxExponent() - exponent) {
            return infinity(negative);
        }
        if (scaleFactor < QMIN - 1L - exponent) {
            return zero(negative);
        }
        return fromDyadic(negative, magnitude, scale + scaleFactor);
    }
}
