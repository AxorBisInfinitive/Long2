package com.inf.long2.binaries.binary1280;

import java.util.Arrays;
import java.util.Objects;

import com.inf.long2.binaries.internal.UnsignedWords.Division;

import static com.inf.long2.binaries.binary1280.Binary1280Encoding.*;
import static com.inf.long2.binaries.binary1280.Binary1280DecimalIntegers.*;
import static com.inf.long2.binaries.internal.UnsignedWords.anyLow;
import static com.inf.long2.binaries.internal.UnsignedWords.bit;
import static com.inf.long2.binaries.internal.UnsignedWords.bitLength;
import static com.inf.long2.binaries.internal.UnsignedWords.compare;
import static com.inf.long2.binaries.internal.UnsignedWords.left;
import static com.inf.long2.binaries.internal.UnsignedWords.trim;

/** 对短文本使用有向整数区间，临界比较和长输出使用精确整数。 */
final class Binary1280Decimal {
    private static final long EXPONENT_LIMIT = Long.MAX_VALUE / 4;
    private static final int DECIMAL_LIMIT = 41_000_000;
    private static final int PREFIX_DIGITS = 420;
    private static final int INITIAL_BITS = PRECISION + 128;
    private static final int MAX_ROUND_TRIP_DIGITS = 378;

    private Binary1280Decimal() {
    }

    private enum TextKind {
        FINITE, NAN, INFINITY
    }

    /** 固定大小的语法扫描结果。首末非零数字索引不包括小数点，scale 已合并小数位和末尾零。 */
    private record ScannedDecimal(TextKind kind, boolean negative, int start, int end,
            int first, int last, long scale) {
    }

    static boolean isValid(String text) {
        return scan(text) != null;
    }

    /** 唯一的语法扫描入口，不抛格式异常，不构造数值或随输入长度增长的工作区。 */
    private static ScannedDecimal scan(String text) {
        if (text == null) {
            return null;
        }
        switch (text) {
            case "NaN": return new ScannedDecimal(TextKind.NAN, false, 0, 0, 0, 0, 0);
            case "Infinity", "+Infinity": return new ScannedDecimal(TextKind.INFINITY, false, 0, 0, 0, 0, 0);
            case "-Infinity": return new ScannedDecimal(TextKind.INFINITY, true, 0, 0, 0, 0, 0);
            default: break;
        }
        int length = text.length(), start = 0;
        boolean negative = length != 0 && text.charAt(0) == '-';
        if (length != 0 && (negative || text.charAt(0) == '+')) {
            start++;
        }
        int index = start, digits = 0, fraction = 0, first = -1, last = -1;
        boolean point = false;
        while (index < length && text.charAt(index) != 'e' && text.charAt(index) != 'E') {
            char c = text.charAt(index++);
            if (c == '.' && !point) {
                point = true;
                continue;
            }
            if (c < '0' || c > '9') {
                return null;
            }
            if (c != '0') {
                if (first < 0) {
                    first = digits;
                }
                last = digits;
            }
            digits++;
            if (point) {
                fraction++;
            }
        }
        if (digits == 0) {
            return null;
        }
        int end = index;
        long exponent = 0;
        if (index < length) {
            index++;
            boolean exponentNegative = index < length && text.charAt(index) == '-';
            if (index < length && (exponentNegative || text.charAt(index) == '+')) {
                index++;
            }
            int exponentStart = index;
            while (index < length) {
                char c = text.charAt(index++);
                if (c < '0' || c > '9') {
                    return null;
                }
                // 饱和值远大于字符串可能提供的小数位修正，故不会误判相互抵消的指数。
                exponent = exponent > (EXPONENT_LIMIT - (c - '0')) / 10
                        ? EXPONENT_LIMIT : exponent * 10 + c - '0';
            }
            if (index == exponentStart) {
                return null;
            }
            if (exponentNegative) {
                exponent = -exponent;
            }
        }
        long scale = exponent - fraction + (digits - 1L - last);
        return new ScannedDecimal(TextKind.FINITE, negative, start, end, first, last, scale);
    }

    static Binary1280 parse(String text) {
        Objects.requireNonNull(text);
        ScannedDecimal scanned = scan(text);
        if (scanned == null) {
            throw new NumberFormatException("Invalid decimal value: " + text);
        }
        boolean negative = scanned.negative();
        if (scanned.kind() == TextKind.NAN) {
            return Binary1280.NaN;
        }
        if (scanned.kind() == TextKind.INFINITY) {
            return infinity(negative);
        }
        if (scanned.first() < 0) {
            return zero(negative);
        }
        long exponent = scanned.scale() + (long) scanned.last() - scanned.first();
        if (exponent > DECIMAL_LIMIT) {
            return infinity(negative);
        }
        if (exponent < -DECIMAL_LIMIT) {
            return zero(negative);
        }

        int count = Math.min(PREFIX_DIGITS, scanned.last() - scanned.first() + 1);
        long[] prefix = ZERO;
        int ordinal = 0, read = 0, chunk = 0, chunkDigits = 0;
        for (int i = scanned.start(); i < scanned.end() && read < count; i++) {
            char c = text.charAt(i);
            if (c != '.' && ordinal++ >= scanned.first()) {
                chunk = 10 * chunk + c - '0';
                read++;
                if (++chunkDigits == 9) {
                    prefix = multiplyAdd(prefix, 1_000_000_000, chunk);
                    chunk = chunkDigits = 0;
                }
            }
        }
        if (chunkDigits != 0) {
            int factor = 1;
            for (int i = 0; i < chunkDigits; i++) {
                factor *= 10;
            }
            prefix = multiplyAdd(prefix, factor, chunk);
        }
        boolean truncated = scanned.last() - scanned.first() + 1 > count;
        long[] upper = truncated ? increment(prefix) : prefix;
        int decimalScale = Math.toIntExact(exponent - count + 1);
        Binary1280 low = null, high = null;
        for (int bits = INITIAL_BITS; bits <= 2 * INITIAL_BITS; bits *= 2) {
            Bounds bounds = scaleByTen(new Bounds(prefix, upper, 0), decimalScale, bits);
            low = Binary1280Rounding.roundDyadic(false, bounds.low(), bounds.scale());
            high = Binary1280Rounding.roundDyadic(false, bounds.high(), bounds.scale());
            if (low.equals(high) || low.nextUp().equals(high)) {
                break;
            }
        }
        if (!low.equals(high) && !low.nextUp().equals(high)) {
            long[] power = powerOfFive(Math.abs(decimalScale));
            low = roundExactEndpoint(prefix, decimalScale, power);
            high = truncated ? roundExactEndpoint(upper, decimalScale, power) : low;
        }
        Binary1280 result = low;
        if (!low.equals(high)) {
            Boundary boundary = midpoint(low, high);
            int comparison = -compareScaled(boundary.value(), -decimalScale, prefix);
            if (truncated && comparison <= 0) {
                // 截断系数含非零尾部，两个前缀端点都是严格界。
                if (comparison == 0) {
                    comparison = 1;
                } else if (compareScaled(boundary.value(), -decimalScale, upper) < 0) {
                    comparison = compareText(text, scanned, exponent, boundary.value());
                }
            }
            result = comparison < 0 || comparison == 0 && boundary.included() ? low : high;
        }
        return negative ? result.negate() : result;
    }

    private static int compareText(String input, ScannedDecimal scanned, long exponent, Dyadic boundary) {
        int exactExponent = decimalExponent(boundary);
        if (exponent != exactExponent) {
            return Long.compare(exponent, exactExponent);
        }
        TextComparison comparison = new TextComparison(input, scanned);
        visitDecimal(exactCoefficient(boundary).magnitude(), comparison);
        return comparison.result == 0 && comparison.ordinal <= scanned.last() ? 1 : comparison.result;
    }

    private static final class TextComparison implements DecimalBlockConsumer {
        private final String input;
        private final ScannedDecimal scanned;
        private int position, ordinal, result;

        TextComparison(String input, ScannedDecimal scanned) {
            this.input = input;
            this.scanned = scanned;
            position = scanned.start();
            while (ordinal < scanned.first()) {
                nextDigit();
            }
        }

        private int nextDigit() {
            if (ordinal > scanned.last()) {
                return 0;
            }
            char digit = input.charAt(position++);
            if (digit == '.') {
                digit = input.charAt(position++);
            }
            ordinal++;
            return digit - '0';
        }

        @Override
        public boolean accept(int block, int width) {
            int divisor = 100_000_000;
            if (width == 0) {
                while (divisor > 1 && block < divisor) {
                    divisor /= 10;
                }
            }
            for (; divisor != 0; divisor /= 10) {
                int digit = block / divisor;
                result = Integer.compare(nextDigit(), digit);
                if (result != 0) {
                    return false;
                }
                block -= digit * divisor;
            }
            return true;
        }
    }

    private static Binary1280 roundExactEndpoint(long[] coefficient, int scale, long[] powerOfFive) {
        if (scale >= 0) {
            return Binary1280Rounding.roundDyadic(false, multiply(coefficient, powerOfFive), scale);
        }
        int difference = bitLength(coefficient) - bitLength(powerOfFive);
        long exponent = scale + (long) difference
                - (compareDyadic(coefficient, -difference, powerOfFive) < 0 ? 1 : 0);
        if (exponent > EMAX) {
            return Binary1280.POSITIVE_INFINITY;
        }
        if (exponent < QMIN - 1L) {
            return Binary1280.ZERO;
        }
        long quantum = Math.max(exponent - FRACTION_BITS, QMIN);
        int alignment = Math.toIntExact(scale - quantum);
        long[] denominator = alignment < 0 ? left(powerOfFive, -alignment) : powerOfFive;
        long[] numerator = alignment >= 0 ? left(coefficient, alignment) : coefficient;
        Division division = divide(numerator, denominator);
        int half = compareDyadic(division.remainder(), 1, denominator);
        return Binary1280Rounding.packRounded(false, Arrays.copyOf(division.quotient(), WORDS), quantum,
                half > 0 || half == 0 && bit(division.quotient(), 0));
    }

    static String format(Binary1280 value, boolean scientific) {
        if (!value.isFinite() || value.isZero()) {
            return special(value, scientific, -1);
        }
        Decimal decimal = shortest(value);
        String digits = decimal.digits();
        int exponent = digits.length() - 1 + decimal.scale();
        if (scientific) {
            String fraction = digits.length() == 1 ? "0" : digits.substring(1);
            return sign(value.signBit()) + digits.charAt(0) + "." + fraction + "e" + exponent;
        }
        int fraction = Math.max(-decimal.scale(), 0);
        return plainFixed(value.signBit(), digits, Math.max(decimal.scale(), 0), fraction);
    }

    static String format(Binary1280 value, boolean scientific, int fractionDigits) {
        if (fractionDigits < 0) {
            throw new IllegalArgumentException("fractionDigits must not be negative");
        }
        if (!value.isFinite() || value.isZero()) {
            return special(value, scientific, fractionDigits);
        }
        boolean negative = value.signBit();
        checkLength((negative ? 1L : 0L) + 1 + (fractionDigits == 0 ? 0 : 1L + fractionDigits)
                + (scientific ? 2 : 0));
        Dyadic source = dyadic(value);
        int exponent = decimalExponent(source);
        if (!scientific) {
            checkLength((negative ? 1L : 0L) + Math.max(1L, exponent + 1L)
                    + (fractionDigits == 0 ? 0 : 1L + fractionDigits));
        }
        long shift = fractionDigits - (scientific ? (long) exponent : 0L);
        long order = exponent + shift;
        String digits;
        int zeros = 0;
        if (order < -1) {
            digits = "0";
        } else if (order < PREFIX_DIGITS) {
            digits = decimalDigits(roundedDecimalInteger(source, Math.toIntExact(shift)));
        } else {
            Decimal exact = exactDecimal(source);
            long places = exact.scale() + shift;
            if (places >= 0) {
                digits = exact.digits();
                if (!scientific) {
                    zeros = Math.toIntExact(places);
                }
            } else {
                digits = roundDigits(exact.digits(), Math.toIntExact(-places));
            }
        }
        if (!scientific) {
            return plainFixed(negative, digits, zeros, fractionDigits);
        }
        if (digits.length() > fractionDigits + 1L) {
            digits = digits.substring(0, digits.length() - 1);
            exponent++;
        }
        return scientificFixed(negative, digits, fractionDigits, exponent);
    }

    private static String sign(boolean negative) {
        return negative ? "-" : "";
    }

    private static String special(Binary1280 value, boolean scientific, int fractionDigits) {
        if (value.isNaN()) {
            return "NaN";
        }
        boolean negative = value.signBit();
        if (value.isInfinite()) {
            return sign(negative) + "Infinity";
        }
        if (fractionDigits < 0) {
            return sign(negative) + (scientific ? "0.0e0" : "0");
        }
        return scientific ? scientificFixed(negative, "0", fractionDigits, 0)
                : plainFixed(negative, "0", 0, fractionDigits);
    }

    private record Dyadic(long[] magnitude, long scale) {
    }

    private record Boundary(Dyadic value, boolean included) {
    }

    private record Decimal(String digits, int scale) {
    }

    private record DecimalCoefficient(long[] magnitude, int scale) {
    }

    private record Bounds(long[] low, long[] high, long scale) {
    }

    private static Dyadic dyadic(Binary1280 value) {
        return new Dyadic(trim(magnitude(value)), value.scaleExponent());
    }

    private static boolean even(Binary1280 value) {
        return !value.isFinite() || (value.getSignificandLong(WORDS - 1) & 1) == 0;
    }

    private static Boundary midpoint(Binary1280 lower, Binary1280 upper) {
        Dyadic a = dyadic(lower);
        Dyadic b = upper.isInfinite() ? new Dyadic(ONE, EMAX + 1L) : dyadic(upper);
        long scale = Math.min(a.scale(), b.scale());
        long[] sum = add(shift(a.magnitude(), a.scale() - scale), shift(b.magnitude(), b.scale() - scale));
        return new Boundary(new Dyadic(sum, scale - 1), even(lower));
    }

    private static Decimal shortest(Binary1280 value) {
        Binary1280 positive = value.signBit() ? value.negate() : value;
        Dyadic source = dyadic(positive);
        Boundary lower = midpoint(positive.nextDown(), positive);
        Boundary upper = midpoint(positive, positive.nextUp());
        int exponent = decimalExponent(source);
        int left = 1, right = MAX_ROUND_TRIP_DIGITS;
        while (left < right) {
            int middle = (left + right) >>> 1;
            if (candidate(source, lower, upper, exponent - middle + 1) == null) {
                left = middle + 1;
            } else {
                right = middle;
            }
        }
        int scale = exponent - left + 1;
        long[] coefficient = candidate(source, lower, upper, scale);
        if (coefficient == null) {
            throw new AssertionError("No round-trip decimal within 378 significant digits");
        }
        String digits = decimalDigits(coefficient);
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') {
            end--;
        }
        return new Decimal(digits.substring(0, end), scale + digits.length() - end);
    }

    private static long[] candidate(Dyadic source, Boundary lower, Boundary upper, int scale) {
        int shift = -scale;
        long[] down = floorScaled(source, shift), up = increment(down);
        int low = compareScaled(lower.value(), shift, down);
        int high = compareScaled(upper.value(), shift, up);
        // 中点以较低候选的奇偶定开闭。下边界属于其上方的值时取反。
        boolean downValid = low < 0 || low == 0 && !lower.included();
        boolean upValid = high > 0 || high == 0 && upper.included();
        if (!downValid) {
            return upValid ? up : null;
        }
        if (!upValid) {
            return down;
        }
        return roundedDecimalInteger(source, shift);
    }

    private static int decimalExponent(Dyadic source) {
        long binaryExponent = source.scale() + bitLength(source.magnitude()) - 1;
        int exponent = (int) Math.floor(binaryExponent * 0.3010299956639812);
        while (compareScaled(source, -exponent, ONE) < 0) {
            exponent--;
        }
        while (compareScaled(source, -exponent - 1, ONE) >= 0) {
            exponent++;
        }
        return exponent;
    }

    private static Bounds scaleByTen(Bounds value, int power, int bits) {
        if (power == 0) {
            return value;
        }
        Bounds factor = enclosePowerOfFive(Math.abs(power), bits);
        if (power > 0) {
            Bounds product = multiplyBounds(value, factor, bits);
            return new Bounds(product.low(), product.high(), product.scale() + power);
        }
        int shift = bits + bitLength(factor.high());
        long[] low = divide(left(value.low(), shift), factor.high()).quotient();
        Division upper = divide(left(value.high(), shift), factor.low());
        long[] high = upper.remainder().length == 0 ? upper.quotient() : increment(upper.quotient());
        return enclose(low, high, value.scale() - factor.scale() - shift + power, bits);
    }

    private static Bounds enclosePowerOfFive(int exponent, int bits) {
        Bounds result = new Bounds(ONE, ONE, 0);
        Bounds factor = new Bounds(FIVE, FIVE, 0);
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result = multiplyBounds(result, factor, bits);
            }
            exponent >>>= 1;
            if (exponent != 0) {
                factor = multiplyBounds(factor, factor, bits);
            }
        }
        return result;
    }

    private static Bounds multiplyBounds(Bounds a, Bounds b, int bits) {
        return enclose(multiply(a.low(), b.low()), multiply(a.high(), b.high()), a.scale() + b.scale(), bits);
    }

    private static Bounds enclose(long[] low, long[] high, long scale, int bits) {
        int discard = bitLength(high) - bits;
        if (discard <= 0) {
            return new Bounds(low, high, scale);
        }
        long[] upper = right(high, discard);
        if (anyLow(high, discard)) {
            upper = increment(upper);
        }
        if (bitLength(upper) > bits) {
            discard++;
            upper = right(upper, 1);
        }
        return new Bounds(right(low, discard), upper, scale + discard);
    }

    private static Bounds scaledBounds(Dyadic value, int power, int bits) {
        return scaleByTen(new Bounds(value.magnitude(), value.magnitude(), value.scale()), power, bits);
    }

    /** 比较 value × 10^power 与 integer。上下界包含精确值。 */
    private static int compareScaled(Dyadic value, int power, long[] integer) {
        if (value.magnitude().length == 0 || integer.length == 0) {
            return Integer.compare(value.magnitude().length, integer.length);
        }
        for (int bits = INITIAL_BITS; bits <= 2 * INITIAL_BITS; bits *= 2) {
            Bounds bounds = scaledBounds(value, power, bits);
            int low = compareDyadic(bounds.low(), bounds.scale(), integer);
            int high = compareDyadic(bounds.high(), bounds.scale(), integer);
            if (low == high) {
                return low;
            }
        }
        // 消去两侧的二进制因子，减少精确比较所需的移位空间。
        int aZeros = trailingZeros(value.magnitude());
        int bZeros = trailingZeros(integer);
        long[] a = right(value.magnitude(), aZeros);
        long[] b = right(integer, bZeros);
        long scale = value.scale() + power + aZeros - bZeros;
        // 去掉二因子后两侧幅值为奇数，位权相同时可用小系数精确判等及比较。
        if (scale == 0) {
            return power >= 0 ? compareTimesPowerOfFive(a, b, power)
                    : -compareTimesPowerOfFive(b, a, -power);
        }
        return power >= 0 ? compareDyadic(multiply(a, powerOfFive(power)), scale, b)
                : compareDyadic(a, scale, multiply(b, powerOfFive(-power)));
    }

    /** 比较 a × 5^power 与 b，通过缩小 b 避免构造巨大幂。 */
    private static int compareTimesPowerOfFive(long[] a, long[] b, int power) {
        if (bitLength(a) + 2L * power > bitLength(b)) {
            return 1;
        }
        boolean fractional = false;
        while (power > 0 && b.length != 0) {
            int count = Math.min(power, 13);
            int divisor = 1;
            for (int i = 0; i < count; i++) {
                divisor *= 5;
            }
            Division division = divide(b, new long[] {divisor});
            fractional |= division.remainder().length != 0;
            b = division.quotient();
            power -= count;
        }
        int comparison = compare(a, b);
        return comparison == 0 && fractional ? -1 : comparison;
    }

    private static long[] floorScaled(Dyadic value, int power) {
        for (int bits = INITIAL_BITS; bits <= 2 * INITIAL_BITS; bits *= 2) {
            Bounds bounds = scaledBounds(value, power, bits);
            long[] low = shift(bounds.low(), bounds.scale()), high = shift(bounds.high(), bounds.scale());
            if (Arrays.equals(low, high)) {
                return low;
            }
            if (Arrays.equals(high, increment(low))) {
                return compareScaled(value, power, high) < 0 ? low : high;
            }
        }
        long scale = value.scale() + power;
        if (power >= 0) {
            return shift(multiply(value.magnitude(), powerOfFive(power)), scale);
        }
        return divide(shift(value.magnitude(), scale), powerOfFive(-power)).quotient();
    }

    private static long[] roundedDecimalInteger(Dyadic value, int power) {
        long[] floor = floorScaled(value, power);
        int comparison = compareScaled(new Dyadic(value.magnitude(), value.scale() + 1),
                power, increment(left(floor, 1)));
        return comparison > 0 || comparison == 0 && bit(floor, 0) ? increment(floor) : floor;
    }

    private static Decimal exactDecimal(Dyadic value) {
        DecimalCoefficient exact = exactCoefficient(value);
        return new Decimal(decimalDigits(exact.magnitude()), exact.scale());
    }

    private static DecimalCoefficient exactCoefficient(Dyadic value) {
        int zeros = trailingZeros(value.magnitude());
        long[] magnitude = right(value.magnitude(), zeros);
        int scale = Math.toIntExact(value.scale() + zeros);
        return scale >= 0 ? new DecimalCoefficient(left(magnitude, scale), 0)
                : new DecimalCoefficient(multiply(magnitude, powerOfFive(-scale)), scale);
    }

    private static String roundDigits(String exact, int discard) {
        int kept = exact.length() - discard;
        if (kept < 0) {
            return "0";
        }
        char firstDiscarded = exact.charAt(kept);
        boolean increment = firstDiscarded > '5';
        if (firstDiscarded == '5') {
            increment = kept > 0 && ((exact.charAt(kept - 1) - '0') & 1) != 0;
            for (int i = kept + 1; i < exact.length() && !increment; i++) {
                increment = exact.charAt(i) != '0';
            }
        }
        if (!increment) {
            return kept == 0 ? "0" : exact.substring(0, kept);
        }
        char[] rounded = new char[kept];
        exact.getChars(0, kept, rounded, 0);
        for (int i = rounded.length - 1; i >= 0; i--) {
            if (rounded[i] != '9') {
                rounded[i]++;
                return new String(rounded);
            }
            rounded[i] = '0';
        }
        return "1" + "0".repeat(kept);
    }

    private static int checkLength(long length) {
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Formatted decimal exceeds the maximum string length");
        }
        return (int) length;
    }

    private static String plainFixed(boolean negative, String digits, int appendedZeros, int fractionDigits) {
        long totalDigits = digits.length() + (long) appendedZeros;
        int length = checkLength((negative ? 1L : 0L) + Math.max(totalDigits, fractionDigits + 1L)
                + (fractionDigits == 0 ? 0 : 1));
        char[] result = new char[length];
        Arrays.fill(result, '0');
        int offset = negative ? 1 : 0;
        if (negative) {
            result[0] = '-';
        }
        int point = Math.toIntExact(totalDigits - fractionDigits);
        if (fractionDigits == 0) {
            digits.getChars(0, digits.length(), result, offset);
        } else if (point <= 0) {
            result[offset + 1] = '.';
            digits.getChars(0, digits.length(), result, offset + 2 - point);
        } else {
            result[offset + point] = '.';
            int integerDigits = Math.min(point, digits.length());
            digits.getChars(0, integerDigits, result, offset);
            digits.getChars(integerDigits, digits.length(), result, offset + point + 1);
        }
        return new String(result);
    }

    private static String scientificFixed(boolean negative, String digits, int fractionDigits, int exponent) {
        String exponentText = Integer.toString(exponent);
        int length = checkLength((negative ? 1L : 0L) + 1 + (fractionDigits == 0 ? 0L : 1L + fractionDigits)
                + 1 + exponentText.length());
        char[] result = new char[length];
        Arrays.fill(result, '0');
        int offset = negative ? 1 : 0;
        if (negative) {
            result[0] = '-';
        }
        result[offset] = digits.charAt(0);
        if (fractionDigits != 0) {
            result[offset + 1] = '.';
            digits.getChars(1, digits.length(), result, offset + 2);
        }
        int exponentPosition = length - exponentText.length() - 1;
        result[exponentPosition] = 'e';
        exponentText.getChars(0, exponentText.length(), result, exponentPosition + 1);
        return new String(result);
    }
}
