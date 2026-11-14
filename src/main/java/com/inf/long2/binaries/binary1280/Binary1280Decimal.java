package com.inf.long2.binaries.binary1280;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;


import static com.inf.long2.binaries.binary1280.Binary1280Encoding.*;

/** 对短文本使用有向整数区间，临界比较和长输出使用精确整数。 */
final class Binary1280Decimal {
    private static final BigInteger FIVE = BigInteger.valueOf(5);
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
        StringBuilder digits = new StringBuilder(count);
        int ordinal = 0;
        for (int i = scanned.start(); i < scanned.end() && digits.length() < count; i++) {
            char c = text.charAt(i);
            if (c != '.' && ordinal++ >= scanned.first()) {
                digits.append(c);
            }
        }
        BigInteger prefix = new BigInteger(digits.toString());
        boolean truncated = scanned.last() - scanned.first() + 1 > count;
        BigInteger upper = truncated ? prefix.add(BigInteger.ONE) : prefix;
        int decimalScale = Math.toIntExact(exponent - count + 1);
        for (int bits = INITIAL_BITS; ; bits *= 2) {
            Bounds bounds = scaleByTen(new Bounds(prefix, upper, 0), decimalScale, bits);
            Binary1280 low = roundEndpoint(bounds.low(), bounds.scale());
            Binary1280 high = roundEndpoint(bounds.high(), bounds.scale());
            if (low.equals(high)) {
                return negative ? low.negate() : low;
            }
            if (low.nextUp().equals(high)) {
                Boundary boundary = midpoint(low, high);
                int comparison = -compareScaled(boundary.value(), -decimalScale, prefix);
                if (truncated && comparison <= 0) {
                    // 截断系数含非零尾部，两个前缀端点都是严格界。
                    if (comparison == 0) {
                        comparison = 1;
                    } else if (compareScaled(boundary.value(), -decimalScale, upper) < 0) {
                        comparison = compareText(text, scanned, exponent, exactDecimal(boundary.value()));
                    }
                }
                Binary1280 result = comparison < 0 || comparison == 0 && boundary.included() ? low : high;
                return negative ? result.negate() : result;
            }
            // 420 位前缀比最窄的相对中点间距更细，扩充幂精度后至多跨一个中点。
        }
    }

    private static int compareText(String input, ScannedDecimal scanned, long exponent, Decimal exact) {
        long exactExponent = exact.digits().length() - 1L + exact.scale();
        if (exponent != exactExponent) {
            return Long.compare(exponent, exactExponent);
        }
        int ordinal = 0, position = 0;
        for (int i = scanned.start(); i < scanned.end() && ordinal <= scanned.last(); i++) {
            char c = input.charAt(i);
            if (c == '.' || ordinal++ < scanned.first()) {
                continue;
            }
            char expected = position < exact.digits().length() ? exact.digits().charAt(position) : '0';
            if (c != expected) {
                return Character.compare(c, expected);
            }
            position++;
        }
        while (position < exact.digits().length()) {
            if (exact.digits().charAt(position++) != '0') {
                return -1;
            }
        }
        return 0;
    }

    private static Binary1280 roundEndpoint(BigInteger magnitude, long scale) {
        byte[] bytes = magnitude.toByteArray();
        long[] words = new long[(magnitude.bitLength() + 63) / 64];
        for (int i = 0; i < Math.min(bytes.length, words.length * 8); i++) {
            words[i / 8] |= (bytes[bytes.length - 1 - i] & 0xffL) << (8 * (i & 7));
        }
        return Binary1280Rounding.roundDyadic(false, words, scale);
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
            digits = roundedDecimalInteger(source, Math.toIntExact(shift)).toString();
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

    private record Dyadic(BigInteger magnitude, long scale) {
    }

    private record Boundary(Dyadic value, boolean included) {
    }

    private record Decimal(String digits, int scale) {
    }

    private record Bounds(BigInteger low, BigInteger high, long scale) {
    }

    private static Dyadic dyadic(Binary1280 value) {
        byte[] bytes = value.toBytes();
        // 原始最高组有 29 位编码字段，有效数字由公开数学分解提供。
        long top = value.getSignificandLong(0);
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (top >>> (56 - 8 * i));
        }
        return new Dyadic(new BigInteger(1, bytes), value.scaleExponent());
    }

    private static boolean even(Binary1280 value) {
        return !value.isFinite() || (value.getSignificandLong(WORDS - 1) & 1) == 0;
    }

    private static Boundary midpoint(Binary1280 lower, Binary1280 upper) {
        Dyadic a = dyadic(lower);
        Dyadic b = upper.isInfinite() ? new Dyadic(BigInteger.ONE, EMAX + 1L) : dyadic(upper);
        long scale = Math.min(a.scale(), b.scale());
        BigInteger sum = a.magnitude().shiftLeft(Math.toIntExact(a.scale() - scale))
                .add(b.magnitude().shiftLeft(Math.toIntExact(b.scale() - scale)));
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
        BigInteger coefficient = candidate(source, lower, upper, scale);
        if (coefficient == null) {
            throw new AssertionError("No round-trip decimal within 378 significant digits");
        }
        String digits = coefficient.toString();
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') {
            end--;
        }
        return new Decimal(digits.substring(0, end), scale + digits.length() - end);
    }

    private static BigInteger candidate(Dyadic source, Boundary lower, Boundary upper, int scale) {
        int shift = -scale;
        BigInteger down = floorScaled(source, shift), up = down.add(BigInteger.ONE);
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
        long binaryExponent = source.scale() + source.magnitude().bitLength() - 1;
        int exponent = (int) Math.floor(binaryExponent * 0.3010299956639812);
        while (compareScaled(source, -exponent, BigInteger.ONE) < 0) {
            exponent--;
        }
        while (compareScaled(source, -exponent - 1, BigInteger.ONE) >= 0) {
            exponent++;
        }
        return exponent;
    }

    private static Bounds scaleByTen(Bounds value, int power, int bits) {
        if (power == 0) {
            return value;
        }
        Bounds factor = powerOfFive(Math.abs(power), bits);
        if (power > 0) {
            Bounds product = multiply(value, factor, bits);
            return new Bounds(product.low(), product.high(), product.scale() + power);
        }
        int shift = bits + factor.high().bitLength();
        BigInteger low = value.low().shiftLeft(shift).divide(factor.high());
        BigInteger[] upper = value.high().shiftLeft(shift).divideAndRemainder(factor.low());
        BigInteger high = upper[1].signum() == 0 ? upper[0] : upper[0].add(BigInteger.ONE);
        return enclose(low, high, value.scale() - factor.scale() - shift + power, bits);
    }

    private static Bounds powerOfFive(int exponent, int bits) {
        Bounds result = new Bounds(BigInteger.ONE, BigInteger.ONE, 0);
        Bounds factor = new Bounds(FIVE, FIVE, 0);
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result = multiply(result, factor, bits);
            }
            exponent >>>= 1;
            if (exponent != 0) {
                factor = multiply(factor, factor, bits);
            }
        }
        return result;
    }

    private static Bounds multiply(Bounds a, Bounds b, int bits) {
        return enclose(a.low().multiply(b.low()), a.high().multiply(b.high()), a.scale() + b.scale(), bits);
    }

    private static Bounds enclose(BigInteger low, BigInteger high, long scale, int bits) {
        int discard = high.bitLength() - bits;
        if (discard <= 0) {
            return new Bounds(low, high, scale);
        }
        BigInteger upper = high.shiftRight(discard);
        if (high.getLowestSetBit() < discard) {
            upper = upper.add(BigInteger.ONE);
        }
        return new Bounds(low.shiftRight(discard), upper, scale + discard);
    }

    private static Bounds scaledBounds(Dyadic value, int power, int bits) {
        return scaleByTen(new Bounds(value.magnitude(), value.magnitude(), value.scale()), power, bits);
    }

    private static int compareDyadic(BigInteger a, long scale, BigInteger b) {
        if (a.signum() == 0 || b.signum() == 0) {
            return Integer.compare(a.signum(), b.signum());
        }
        long bits = a.bitLength() + scale;
        if (bits != b.bitLength()) {
            return Long.compare(bits, b.bitLength());
        }
        return scale >= 0 ? a.shiftLeft(Math.toIntExact(scale)).compareTo(b)
                : a.compareTo(b.shiftLeft(Math.toIntExact(-scale)));
    }

    /** 比较 value × 10^power 与 integer。上下界包含精确值。 */
    private static int compareScaled(Dyadic value, int power, BigInteger integer) {
        for (int bits = INITIAL_BITS; bits <= 2 * INITIAL_BITS; bits *= 2) {
            Bounds bounds = scaledBounds(value, power, bits);
            int low = compareDyadic(bounds.low(), bounds.scale(), integer);
            int high = compareDyadic(bounds.high(), bounds.scale(), integer);
            if (low == high) {
                return low;
            }
        }
        // 消去两侧的二进制因子，减少精确比较所需的移位空间。
        int aZeros = value.magnitude().getLowestSetBit();
        int bZeros = integer.getLowestSetBit();
        BigInteger a = value.magnitude().shiftRight(aZeros);
        BigInteger b = integer.shiftRight(bZeros);
        long scale = value.scale() + power + aZeros - bZeros;
        if (power >= 0) {
            return compareDyadic(a.multiply(FIVE.pow(power)), scale, b);
        }
        return compareDyadic(a, scale, b.multiply(FIVE.pow(-power)));
    }

    private static BigInteger floorScaled(Dyadic value, int power) {
        for (int bits = INITIAL_BITS; bits <= 2 * INITIAL_BITS; bits *= 2) {
            Bounds bounds = scaledBounds(value, power, bits);
            BigInteger low = shift(bounds.low(), bounds.scale()), high = shift(bounds.high(), bounds.scale());
            if (low.equals(high)) {
                return low;
            }
            if (high.equals(low.add(BigInteger.ONE))) {
                return compareScaled(value, power, high) < 0 ? low : high;
            }
        }
        long scale = value.scale() + power;
        if (power >= 0) {
            return shift(value.magnitude().multiply(FIVE.pow(power)), scale);
        }
        return shift(value.magnitude(), scale).divide(FIVE.pow(-power));
    }

    private static BigInteger shift(BigInteger value, long scale) {
        return scale >= 0 ? value.shiftLeft(Math.toIntExact(scale))
                : -scale >= value.bitLength() ? BigInteger.ZERO : value.shiftRight(Math.toIntExact(-scale));
    }

    private static BigInteger roundedDecimalInteger(Dyadic value, int power) {
        BigInteger floor = floorScaled(value, power);
        int comparison = compareScaled(new Dyadic(value.magnitude(), value.scale() + 1),
                power, floor.shiftLeft(1).add(BigInteger.ONE));
        return comparison > 0 || comparison == 0 && floor.testBit(0) ? floor.add(BigInteger.ONE) : floor;
    }

    private static Decimal exactDecimal(Dyadic value) {
        int zeros = value.magnitude().getLowestSetBit();
        BigInteger magnitude = value.magnitude().shiftRight(zeros);
        int scale = Math.toIntExact(value.scale() + zeros);
        return scale >= 0 ? new Decimal(magnitude.shiftLeft(scale).toString(), 0)
                : new Decimal(magnitude.multiply(FIVE.pow(-scale)).toString(), scale);
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
