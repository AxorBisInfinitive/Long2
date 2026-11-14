package com.inf.long2.binaries.binary1280;


import java.math.BigInteger;

/** 用独立的任意精度整数商余数计算预期编码，不调用生产代码的舍入或有效数字分解。 */
final class Binary1280Reference {
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger FRACTION_MASK = ONE.shiftLeft(1251).subtract(ONE);

    record Dyadic(boolean negative, BigInteger magnitude, long scale) {
    }

    private Binary1280Reference() {
    }

    static BigInteger raw(Binary1280 value) {
        return new BigInteger(1, value.toBytes());
    }

    static Binary1280 fromRaw(BigInteger value) {
        byte[] source = value.toByteArray();
        byte[] bytes = new byte[160];
        int length = Math.min(bytes.length, source.length);
        System.arraycopy(source, source.length - length, bytes, bytes.length - length, length);
        return Binary1280.of(bytes);
    }

    static Binary1280 normal(boolean negative, int exponent, BigInteger fraction) {
        BigInteger raw = BigInteger.valueOf(exponent + 134217727L).shiftLeft(1251).or(fraction);
        return fromRaw(negative ? raw.setBit(1279) : raw);
    }

    static Binary1280 power(int exponent) {
        return normal(false, exponent, BigInteger.ZERO);
    }

    static Dyadic decode(Binary1280 value) {
        BigInteger raw = raw(value);
        int exponent = raw.shiftRight(1251).and(BigInteger.valueOf(0xfffffff)).intValueExact();
        BigInteger magnitude = raw.and(FRACTION_MASK);
        if (exponent != 0) {
            magnitude = magnitude.setBit(1251);
        }
        return new Dyadic(raw.testBit(1279), magnitude, (exponent == 0 ? 1L : exponent) - 134217727 - 1251);
    }

    static Binary1280 round(boolean negative, BigInteger numerator, BigInteger denominator, long scale) {
        if (numerator.signum() == 0) {
            return negative ? Binary1280.NEGATIVE_ZERO : Binary1280.ZERO;
        }
        int shift = numerator.bitLength() - denominator.bitLength();
        int comparison = shift >= 0 ? numerator.compareTo(denominator.shiftLeft(shift))
                : numerator.shiftLeft(-shift).compareTo(denominator);
        long exponent = scale + shift - (comparison < 0 ? 1 : 0);
        if (exponent > 134217727) {
            return negative ? Binary1280.NEGATIVE_INFINITY : Binary1280.POSITIVE_INFINITY;
        }
        if (exponent < -134218978) {
            return negative ? Binary1280.NEGATIVE_ZERO : Binary1280.ZERO;
        }
        long quantum = Math.max(exponent - 1251, -134218977);
        long alignment = scale - quantum;
        BigInteger n = alignment >= 0 ? numerator.shiftLeft(Math.toIntExact(alignment)) : numerator;
        BigInteger d = alignment < 0 ? denominator.shiftLeft(Math.toIntExact(-alignment)) : denominator;
        BigInteger[] division = n.divideAndRemainder(d);
        BigInteger rounded = division[0];
        int half = division[1].shiftLeft(1).compareTo(d);
        if (half > 0 || (half == 0 && rounded.testBit(0))) {
            rounded = rounded.add(ONE);
        }
        if (rounded.bitLength() > 1252) {
            rounded = rounded.shiftRight(1);
            quantum++;
        }
        long finalExponent = quantum + rounded.bitLength() - 1;
        if (rounded.signum() != 0 && finalExponent > 134217727) {
            return negative ? Binary1280.NEGATIVE_INFINITY : Binary1280.POSITIVE_INFINITY;
        }
        long encoded = rounded.signum() == 0 || finalExponent < -134217726 ? 0 : finalExponent + 134217727;
        BigInteger bits = rounded.and(FRACTION_MASK).or(BigInteger.valueOf(encoded).shiftLeft(1251));
        return fromRaw(negative ? bits.setBit(1279) : bits);
    }

    static Binary1280 sum(Dyadic a, Dyadic b) {
        long scale = Math.min(a.scale(), b.scale());
        BigInteger x = a.magnitude().shiftLeft(Math.toIntExact(a.scale() - scale));
        BigInteger y = b.magnitude().shiftLeft(Math.toIntExact(b.scale() - scale));
        BigInteger result = (a.negative() ? x.negate() : x).add(b.negative() ? y.negate() : y);
        boolean negative = result.signum() < 0 || (result.signum() == 0 && a.magnitude().signum() == 0
                && b.magnitude().signum() == 0 && a.negative() && b.negative());
        return round(negative, result.abs(), ONE, scale);
    }

    static Binary1280 add(Binary1280 a, Binary1280 b) {
        return sum(decode(a), decode(b));
    }

    static Binary1280 multiply(Binary1280 a, Binary1280 b) {
        Dyadic x = decode(a), y = decode(b);
        return round(x.negative() ^ y.negative(), x.magnitude().multiply(y.magnitude()), ONE, x.scale() + y.scale());
    }

    static Binary1280 divide(Binary1280 a, Binary1280 b) {
        Dyadic x = decode(a), y = decode(b);
        return round(x.negative() ^ y.negative(), x.magnitude(), y.magnitude(), x.scale() - y.scale());
    }

    static Binary1280 remainder(Binary1280 a, Binary1280 b) {
        Dyadic x = decode(a), y = decode(b);
        long shift = x.scale() - y.scale();
        if (shift < 0) {
            BigInteger denominator = y.magnitude().shiftLeft(Math.toIntExact(-shift));
            return round(x.negative(), x.magnitude().mod(denominator), ONE, x.scale());
        }
        BigInteger factor = BigInteger.TWO.modPow(BigInteger.valueOf(shift), y.magnitude());
        BigInteger residue = x.magnitude().multiply(factor).mod(y.magnitude());
        return round(x.negative(), residue, ONE, y.scale());
    }
}
