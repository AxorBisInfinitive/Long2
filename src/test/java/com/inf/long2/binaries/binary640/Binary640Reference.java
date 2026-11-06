package com.inf.long2.binaries.binary640;

import com.inf.long2.binaries.Binary640;

import java.math.BigInteger;

/** 用独立的任意精度整数商余数计算预期编码，不调用生产代码的舍入或有效数字分解。 */
final class Binary640Reference {
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger FRACTION_MASK = ONE.shiftLeft(615).subtract(ONE);

    record Dyadic(boolean negative, BigInteger magnitude, long scale) {
    }

    private Binary640Reference() {
    }

    static BigInteger raw(Binary640 value) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < 10; i++) {
            long word = value.getRawLong(i);
            BigInteger unsigned = BigInteger.valueOf(word & Long.MAX_VALUE);
            result = result.shiftLeft(64).or(word < 0 ? unsigned.setBit(63) : unsigned);
        }
        return result;
    }

    static Binary640 fromRaw(BigInteger value) {
        return Binary640.fromRawBits(value.shiftRight(576).longValue(), value.shiftRight(512).longValue(),
                value.shiftRight(448).longValue(), value.shiftRight(384).longValue(), value.shiftRight(320).longValue(),
                value.shiftRight(256).longValue(), value.shiftRight(192).longValue(), value.shiftRight(128).longValue(),
                value.shiftRight(64).longValue(), value.longValue());
    }

    static Dyadic decode(Binary640 value) {
        BigInteger raw = raw(value);
        int exponent = raw.shiftRight(615).and(BigInteger.valueOf(0xffffff)).intValueExact();
        BigInteger magnitude = raw.and(FRACTION_MASK);
        if (exponent != 0) {
            magnitude = magnitude.setBit(615);
        }
        return new Dyadic(raw.testBit(639), magnitude, (exponent == 0 ? 1L : exponent) - 8388607 - 615);
    }

    static Binary640 round(boolean negative, BigInteger numerator, BigInteger denominator, long scale) {
        if (numerator.signum() == 0) {
            return negative ? Binary640.NEGATIVE_ZERO : Binary640.ZERO;
        }
        int shift = numerator.bitLength() - denominator.bitLength();
        int comparison = shift >= 0 ? numerator.compareTo(denominator.shiftLeft(shift))
                : numerator.shiftLeft(-shift).compareTo(denominator);
        long exponent = scale + shift - (comparison < 0 ? 1 : 0);
        if (exponent > 8388607) {
            return negative ? Binary640.NEGATIVE_INFINITY : Binary640.POSITIVE_INFINITY;
        }
        if (exponent < -8389222) {
            return negative ? Binary640.NEGATIVE_ZERO : Binary640.ZERO;
        }
        long quantum = Math.max(exponent - 615, -8389221);
        long alignment = scale - quantum;
        BigInteger n = alignment >= 0 ? numerator.shiftLeft(Math.toIntExact(alignment)) : numerator;
        BigInteger d = alignment < 0 ? denominator.shiftLeft(Math.toIntExact(-alignment)) : denominator;
        BigInteger[] division = n.divideAndRemainder(d);
        BigInteger rounded = division[0];
        int half = division[1].shiftLeft(1).compareTo(d);
        if (half > 0 || (half == 0 && rounded.testBit(0))) {
            rounded = rounded.add(ONE);
        }
        if (rounded.bitLength() > 616) {
            rounded = rounded.shiftRight(1);
            quantum++;
        }
        long finalExponent = quantum + rounded.bitLength() - 1;
        if (rounded.signum() != 0 && finalExponent > 8388607) {
            return negative ? Binary640.NEGATIVE_INFINITY : Binary640.POSITIVE_INFINITY;
        }
        long encoded = rounded.signum() == 0 || finalExponent < -8388606 ? 0 : finalExponent + 8388607;
        BigInteger bits = rounded.and(FRACTION_MASK).or(BigInteger.valueOf(encoded).shiftLeft(615));
        return fromRaw(negative ? bits.setBit(639) : bits);
    }

    static Binary640 sum(Dyadic a, Dyadic b) {
        long scale = Math.min(a.scale(), b.scale());
        BigInteger x = a.magnitude().shiftLeft(Math.toIntExact(a.scale() - scale));
        BigInteger y = b.magnitude().shiftLeft(Math.toIntExact(b.scale() - scale));
        BigInteger result = (a.negative() ? x.negate() : x).add(b.negative() ? y.negate() : y);
        boolean negative = result.signum() < 0 || (result.signum() == 0 && a.magnitude().signum() == 0
                && b.magnitude().signum() == 0 && a.negative() && b.negative());
        return round(negative, result.abs(), ONE, scale);
    }

    static Binary640 add(Binary640 a, Binary640 b) {
        return sum(decode(a), decode(b));
    }

    static Binary640 multiply(Binary640 a, Binary640 b) {
        Dyadic x = decode(a), y = decode(b);
        return round(x.negative() ^ y.negative(), x.magnitude().multiply(y.magnitude()), ONE, x.scale() + y.scale());
    }

    static Binary640 divide(Binary640 a, Binary640 b) {
        Dyadic x = decode(a), y = decode(b);
        return round(x.negative() ^ y.negative(), x.magnitude(), y.magnitude(), x.scale() - y.scale());
    }

    static Binary640 fma(Binary640 a, Binary640 b, Binary640 c) {
        Dyadic x = decode(a), y = decode(b);
        return sum(new Dyadic(x.negative() ^ y.negative(), x.magnitude().multiply(y.magnitude()), x.scale() + y.scale()),
                decode(c));
    }

    static Binary640 remainder(Binary640 a, Binary640 b, boolean nearest) {
        Dyadic x = decode(a), y = decode(b);
        long scale = Math.min(x.scale(), y.scale());
        BigInteger numerator = x.magnitude().shiftLeft(Math.toIntExact(x.scale() - scale));
        BigInteger denominator = y.magnitude().shiftLeft(Math.toIntExact(y.scale() - scale));
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger residual = division[1];
        int half = residual.shiftLeft(1).compareTo(denominator);
        if (nearest && (half > 0 || (half == 0 && division[0].testBit(0)))) {
            residual = residual.subtract(denominator);
        }
        return round(x.negative() ^ (residual.signum() < 0), residual.abs(), ONE, scale);
    }

    static Binary640 sqrt(Binary640 value) {
        Dyadic x = decode(value);
        long exponent = Math.floorDiv(x.scale() + x.magnitude().bitLength() - 1, 2);
        long quantum = exponent - 615;
        BigInteger radicand = x.magnitude().shiftLeft(Math.toIntExact(x.scale() - 2 * quantum));
        BigInteger[] root = radicand.sqrtAndRemainder();
        // 整数被开方数不可能恰好落在相邻整数中点的平方上，余数大于根时向上舍入。
        BigInteger rounded = root[1].compareTo(root[0]) > 0 ? root[0].add(ONE) : root[0];
        return round(false, rounded, ONE, quantum);
    }
}
