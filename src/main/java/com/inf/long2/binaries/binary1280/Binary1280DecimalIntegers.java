package com.inf.long2.binaries.binary1280;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.inf.long2.binaries.internal.UnsignedWords;
import com.inf.long2.binaries.internal.UnsignedWords.Division;

import static com.inf.long2.binaries.internal.UnsignedWords.*;

/** 供十进制转换使用的精确无符号算术，幅值低组在前。 */
final class Binary1280DecimalIntegers {
    static final long[] ZERO = {};
    static final long[] ONE = {1};
    static final long[] FIVE = {5};
    private static final int DECIMAL_BASE = 1_000_000_000;
    private static final int KARATSUBA_WORDS = 48;
    private static final int NTT_WORDS = 1024;
    private static final int DIVISION_WORDS = 64;

    private Binary1280DecimalIntegers() {
    }

    static long[] add(long[] a, long[] b) {
        long[] sum = new long[Math.max(a.length, b.length) + 1];
        addAt(sum, a, 0);
        addAt(sum, b, 0);
        return trim(sum);
    }

    /** 向独占工作区累加，调用方预留全部进位空间。 */
    static void addAt(long[] result, long[] value, int offset) {
        long carry = 0;
        int index = offset;
        for (long word : value) {
            long old = result[index];
            long sum = old + word;
            result[index++] = sum + carry;
            carry = Long.compareUnsigned(sum, old) < 0 || Long.compareUnsigned(sum + carry, sum) < 0 ? 1 : 0;
        }
        while (carry != 0) {
            carry = ++result[index++] == 0 ? 1 : 0;
        }
    }

    static long[] increment(long[] value) {
        long[] result = Arrays.copyOf(value, value.length + 1);
        int i = 0;
        while (++result[i] == 0) {
            i++;
        }
        return i == value.length ? result : Arrays.copyOf(result, value.length);
    }

    static int trailingZeros(long[] nonzero) {
        int i = 0;
        while (nonzero[i] == 0) {
            i++;
        }
        return 64 * i + Long.numberOfTrailingZeros(nonzero[i]);
    }

    static long[] right(long[] value, long distance) {
        if (distance >= bitLength(value)) {
            return ZERO;
        }
        if (distance == 0) {
            return value;
        }
        int shift = (int) distance;
        long[] result = new long[(bitLength(value) - shift + 63) / 64];
        for (int i = 0; i < result.length; i++) {
            result[i] = Binary1280Rounding.shiftedWord(value, i, shift);
        }
        return result;
    }

    static long[] shift(long[] value, long distance) {
        return distance < 0 ? right(value, -distance) : left(value, Math.toIntExact(distance));
    }

    static int compareDyadic(long[] a, long scale, long[] b) {
        if (a.length == 0 || b.length == 0) {
            return Integer.compare(a.length, b.length);
        }
        long bitCount = bitLength(a) + scale;
        if (bitCount != bitLength(b)) {
            return Long.compare(bitCount, bitLength(b));
        }
        return scale >= 0 ? -compareShifted(b, a, (int) scale) : compareShifted(a, b, (int) -scale);
    }

    static long[] multiplyAdd(long[] value, int factor, int addend) {
        long[] result = new long[value.length + 1];
        long carry = addend;
        for (int i = 0; i < value.length; i++) {
            long low = value[i] * factor;
            long sum = low + carry;
            carry = Math.unsignedMultiplyHigh(value[i], factor) + (Long.compareUnsigned(sum, low) < 0 ? 1 : 0);
            result[i] = sum;
        }
        result[value.length] = carry;
        return trim(result);
    }

    static long[] multiply(long[] a, long[] b) {
        if (a.length == 0 || b.length == 0) {
            return ZERO;
        }
        int shorter = Math.min(a.length, b.length), longer = Math.max(a.length, b.length);
        if (shorter < KARATSUBA_WORDS) {
            return a.length <= b.length ? UnsignedWords.multiply(a, b) : UnsignedWords.multiply(b, a);
        }
        if (longer >= 2 * shorter) {
            long[] large = a.length >= b.length ? a : b;
            long[] small = a.length >= b.length ? b : a;
            long[] result = new long[a.length + b.length];
            for (int start = 0; start < large.length; start += shorter) {
                addAt(result, multiply(slice(large, start, shorter), small), start);
            }
            return trim(result);
        }
        if (shorter >= NTT_WORDS) {
            return Binary1280DecimalTransform.multiply(a, b);
        }
        int half = longer / 2;
        long[] al = slice(a, 0, half), ah = slice(a, half, a.length);
        boolean square = a == b;
        long[] bl = square ? al : slice(b, 0, half);
        long[] bh = square ? ah : slice(b, half, b.length);
        long[] low = multiply(al, bl), high = multiply(ah, bh);
        long[] aSum = add(al, ah), bSum = square ? aSum : add(bl, bh);
        long[] middle = subtract(subtract(multiply(aSum, bSum), low), high);
        long[] result = new long[a.length + b.length];
        addAt(result, low, 0);
        addAt(result, middle, half);
        addAt(result, high, 2 * half);
        return trim(result);
    }

    static long[] powerOfFive(int exponent) {
        long[] power = ONE;
        for (int bit = Integer.highestOneBit(exponent); bit != 0; bit >>>= 1) {
            power = multiply(power, power);
            if ((exponent & bit) != 0) {
                power = multiplyAdd(power, 5, 0);
            }
        }
        return power;
    }

    static Division divide(long[] numerator, long[] denominator) {
        int distance = numerator.length - denominator.length;
        if (denominator.length <= DIVISION_WORDS || distance <= DIVISION_WORDS) {
            return UnsignedWords.divide(numerator, denominator);
        }
        // 规格化到可反复等分至叶子大小的满组，避免把整个除数扩到下一二次幂。
        int unit = Integer.highestOneBit((denominator.length - 1) / DIVISION_WORDS) << 1;
        int size = (denominator.length + unit - 1) & -unit;
        int shift = 64 * size - bitLength(denominator);
        long[] divisor = left(denominator, shift);
        long[] dividend = left(numerator, shift);
        int blocks = (dividend.length + size - 1) / size;
        long[] quotient = new long[distance + 1];
        long[] remainder = slice(dividend, (blocks - 1) * size, size);
        if (compare(remainder, divisor) >= 0) {
            quotient[(blocks - 1) * size] = 1;
            remainder = subtract(remainder, divisor);
        }
        for (int block = blocks - 2; block >= 0; block--) {
            long[] next = slice(dividend, block * size, size);
            Division division = divideTwoBlocks(join(remainder, next, size), divisor, size);
            long[] part = division.quotient();
            System.arraycopy(part, 0, quotient, block * size, part.length);
            remainder = division.remainder();
        }
        return new Division(trim(quotient), right(remainder, shift));
    }

    /** 除数占 size 组，分子的高 size 组小于除数。 */
    private static Division divideTwoBlocks(long[] numerator, long[] denominator, int size) {
        if (size <= DIVISION_WORDS || numerator.length - size <= DIVISION_WORDS) {
            return UnsignedWords.divide(numerator, denominator);
        }
        int half = size / 2;
        long[] high = slice(denominator, half, half), low = slice(denominator, 0, half);
        Division upper = divideThreeBlocks(slice(numerator, half, numerator.length), high, low, half);
        long[] bottom = slice(numerator, 0, half);
        Division lower = divideThreeBlocks(join(upper.remainder(), bottom, half), high, low, half);
        return new Division(join(upper.quotient(), lower.quotient(), half), lower.remainder());
    }

    /** 除数为 high × B + low，B = 2^(64 × half)，商小于 B。 */
    private static Division divideThreeBlocks(long[] numerator, long[] high, long[] low, int half) {
        long[] topTwo = slice(numerator, half, 2 * half);
        long[] top = slice(topTwo, half, half);
        long[] quotient, remainder;
        if (compare(top, high) < 0) {
            Division division = divideTwoBlocks(topTwo, high, half);
            quotient = division.quotient();
            remainder = division.remainder();
        } else {
            quotient = new long[half];
            Arrays.fill(quotient, -1L);
            // top == high，使用最大单块商，余数等于中块加 high。
            remainder = add(slice(topTwo, 0, half), high);
        }
        long[] residual = join(remainder, slice(numerator, 0, half), half);
        long[] product = multiply(quotient, low);
        if (compare(residual, product) < 0) {
            long[] divisor = join(high, low, half);
            do {
                residual = add(residual, divisor);
                quotient = subtract(quotient, ONE);
            } while (compare(residual, product) < 0);
        }
        return new Division(quotient, subtract(residual, product));
    }

    static long[] slice(long[] value, int start, int count) {
        return start >= value.length ? ZERO : trim(Arrays.copyOfRange(value, start,
                Math.min(value.length, start + count)));
    }

    private static long[] join(long[] high, long[] low, int width) {
        if (high.length == 0) {
            return low;
        }
        long[] joined = Arrays.copyOf(low, width + high.length);
        System.arraycopy(high, 0, joined, width, high.length);
        return joined;
    }

    static String decimalDigits(long[] value) {
        if (value.length == 0) {
            return "0";
        }
        StringBuilder out = new StringBuilder(1 + (int) (bitLength(value) * 0.3010299956639812));
        visitDecimal(value, (block, width) -> {
            String digits = Integer.toString(block);
            if (width > digits.length()) {
                out.repeat('0', width - digits.length());
            }
            out.append(digits);
            return true;
        });
        return out.toString();
    }

    @FunctionalInterface
    interface DecimalBlockConsumer {
        /** 返回 false 可以结束逐块读取。宽度 0 表示首块按实际位数输出。 */
        boolean accept(int block, int width);
    }

    static void visitDecimal(long[] value, DecimalBlockConsumer consumer) {
        List<long[]> powers = new ArrayList<>();
        powers.add(new long[] {DECIMAL_BASE});
        while (compare(powers.getLast(), value) <= 0) {
            // 若平方的最小位长已经超过输入，只需要当前最大可用的幂。
            long[] power = powers.getLast();
            if (2 * bitLength(power) - 1 > bitLength(value)) {
                break;
            }
            long[] square = multiply(power, power);
            if (compare(square, value) > 0) {
                break;
            }
            powers.add(square);
        }
        visitLeading(value, powers.size() - 1, powers, consumer);
    }

    private static boolean visitLeading(long[] value, int level, List<long[]> powers, DecimalBlockConsumer consumer) {
        if (value.length <= 8 || level < 0) {
            return visitSmall(value, 0, consumer);
        }
        if (compare(value, powers.get(level)) < 0) {
            return visitLeading(value, level - 1, powers, consumer);
        }
        Division division = divide(value, powers.get(level));
        return visitLeading(division.quotient(), level - 1, powers, consumer)
                && visitFixed(division.remainder(), level, powers, consumer);
    }

    private static boolean visitFixed(long[] value, int level, List<long[]> powers, DecimalBlockConsumer consumer) {
        if (level <= 3 || value.length <= 8) {
            return visitSmall(value, 1 << level, consumer);
        }
        Division division = divide(value, powers.get(level - 1));
        return visitFixed(division.quotient(), level - 1, powers, consumer)
                && visitFixed(division.remainder(), level - 1, powers, consumer);
    }

    private static boolean visitSmall(long[] value, int width, DecimalBlockConsumer consumer) {
        // 8 个 long 最多 18 个九位块；固定分治叶子的八个块也在范围内。
        int[] blocks = new int[18];
        long[] work = value.clone();
        int used = work.length;
        int count = 0;
        do {
            long remainder = 0;
            for (int i = used - 1; i >= 0; i--) {
                long word = work[i];
                long high = (remainder << 32) | (word >>> 32);
                long low = ((high % DECIMAL_BASE) << 32) | (word & 0xffff_ffffL);
                work[i] = ((high / DECIMAL_BASE) << 32) | (low / DECIMAL_BASE);
                remainder = low % DECIMAL_BASE;
            }
            blocks[count++] = (int) remainder;
            if (used != 0 && work[used - 1] == 0) {
                used--;
            }
        } while (used != 0);
        for (int i = count; i < width; i++) {
            if (!consumer.accept(0, 9)) {
                return false;
            }
        }
        if (!consumer.accept(blocks[--count], width == 0 ? 0 : 9)) {
            return false;
        }
        while (count > 0) {
            if (!consumer.accept(blocks[--count], 9)) {
                return false;
            }
        }
        return true;
    }
}
