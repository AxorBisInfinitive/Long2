package com.inf.long2.binaries.binary1280;

import static com.inf.long2.binaries.binary1280.Binary1280DecimalIntegers.addAt;
import static com.inf.long2.binaries.binary1280.Binary1280DecimalIntegers.slice;
import static com.inf.long2.binaries.internal.UnsignedWords.bitLength;
import static com.inf.long2.binaries.internal.UnsignedWords.trim;

/** 用两个 NTT 模数精确恢复基数 2^16 的乘积系数。 */
final class Binary1280DecimalTransform {
    private static final int MAX_POINTS = 1 << 25;
    private static final int DIGIT_MASK = 0xffff;
    private static final Prime FIRST = new Prime(2_013_265_921, 31);
    private static final Prime SECOND = new Prime(1_811_939_329, 13);
    private static final int FIRST_INVERSE = SECOND.power(
            SECOND.encode(FIRST.modulus - SECOND.modulus), SECOND.modulus - 2);

    private Binary1280DecimalTransform() {
    }

    static long[] multiply(long[] a, long[] b) {
        return multiply(a, b, MAX_POINTS);
    }

    /** 可用较小的点数验证分块路径。 */
    static long[] multiply(long[] a, long[] b, int maxPoints) {
        int aDigits = digits(a), bDigits = digits(b);
        if (aDigits == 0 || bDigits == 0) {
            return new long[0];
        }
        if (aDigits + bDigits <= maxPoints) {
            int points = Integer.highestOneBit(aDigits + bDigits - 1) << 1;
            int[] left = new int[points];
            int[] right = a == b ? left : new int[points];
            int[] first = new int[points];
            convolve(a, b, aDigits, bDigits, left, right, FIRST);
            System.arraycopy(left, 0, first, 0, first.length);
            convolve(a, b, aDigits, bDigits, left, right, SECOND);
            return recover(first, left, aDigits + bDigits);
        }
        return blocked(a, b, maxPoints);
    }

    private static long[] blocked(long[] a, long[] b, int maxPoints) {
        int blockWords = maxPoints / 8;
        long[] result = new long[a.length + b.length];
        for (int i = 0; i < a.length; i += blockWords) {
            long[] left = slice(a, i, blockWords);
            for (int j = 0; j < b.length; j += blockWords) {
                long[] right = a == b && i == j ? left : slice(b, j, blockWords);
                addAt(result, multiply(left, right, maxPoints), i + j);
            }
        }
        return trim(result);
    }

    private static int digits(long[] value) {
        return (bitLength(value) + 15) / 16;
    }

    private static void load(long[] value, int length, int[] output, Prime prime) {
        for (int i = 0; i < length; i++) {
            int digit = (int) (value[i / 4] >>> (16 * (i & 3))) & DIGIT_MASK;
            output[i] = prime.encode(digit);
        }
        java.util.Arrays.fill(output, length, output.length, 0);
    }

    private static void convolve(long[] a, long[] b, int aDigits, int bDigits, int[] left, int[] right, Prime prime) {
        load(a, aDigits, left, prime);
        prime.transform(left, false);
        if (left != right) {
            load(b, bDigits, right, prime);
            prime.transform(right, false);
        }
        for (int i = 0; i < left.length; i++) {
            left[i] = prime.multiply(left[i], right[i]);
        }
        prime.transform(left, true);
        int inverseSize = prime.montgomery(prime.power(prime.encode(left.length), prime.modulus - 2));
        for (int i = 0; i < aDigits + bDigits - 1; i++) {
            // 乘以普通表示的逆元，同时退出 Montgomery 表示。
            left[i] = prime.multiply(left[i], inverseSize);
        }
    }

    private static long[] recover(int[] first, int[] second, int length) {
        long[] result = new long[(length + 3) / 4];
        long carry = 0;
        for (int i = 0; i < length - 1; i++) {
            int a = first[i], b = second[i];
            if (a >= SECOND.modulus) {
                a -= SECOND.modulus;
            }
            int delta = b - a;
            if (delta < 0) {
                delta += SECOND.modulus;
            }
            // 第一模数的逆元已经以 Montgomery 表示保存。
            int multiplier = SECOND.multiply(delta, FIRST_INVERSE);
            long coefficient = first[i] + (long) FIRST.modulus * multiplier;
            long digit = coefficient + carry;
            result[i / 4] |= (digit & DIGIT_MASK) << (16 * (i & 3));
            carry = digit >>> 16;
        }
        int last = length - 1;
        result[last / 4] |= carry << (16 * (last & 3));
        return trim(result);
    }

    private static final class Prime {
        private final int modulus, negativeInverse, one, generator;

        Prime(int modulus, int generator) {
            this.modulus = modulus;
            int inverse = modulus;
            for (int i = 0; i < 5; i++) {
                inverse *= 2 - modulus * inverse;
            }
            negativeInverse = -inverse;
            one = encode(1);
            this.generator = encode(generator);
        }

        int encode(int value) {
            return (int) (((long) value << 32) % modulus);
        }

        int montgomery(long value) {
            long factor = Integer.toUnsignedLong((int) value * negativeInverse);
            // 无符号和小于 2^64，逻辑右移取出高 32 位。
            long reduced = (value + factor * modulus) >>> 32;
            return (int) (reduced >= modulus ? reduced - modulus : reduced);
        }

        int multiply(int a, int b) {
            return montgomery((long) a * b);
        }

        int power(int base, int exponent) {
            int result = one;
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    result = multiply(result, base);
                }
                base = multiply(base, base);
                exponent >>>= 1;
            }
            return result;
        }

        /** 正变换从大块向小块，逆变换反向处理，省去位逆序置换。 */
        void transform(int[] data, boolean inverse) {
            int[] factors = new int[Math.min(1024, data.length / 2)];
            for (int half = inverse ? 1 : data.length / 2; half > 0 && half < data.length;
                    half = inverse ? half << 1 : half >>> 1) {
                int exponent = (modulus - 1) / (half * 2);
                int root = power(generator, inverse ? modulus - 1 - exponent : exponent);
                int width = Math.min(half, factors.length);
                factors[0] = one;
                for (int i = 1; i < width; i++) {
                    factors[i] = multiply(factors[i - 1], root);
                }
                for (int offset = 0; offset < half; offset += width) {
                    for (int block = 0; block < data.length; block += 2 * half) {
                        int start = block + offset;
                        if (inverse) {
                            inverseButterflies(data, start, half, factors, width);
                        } else {
                            forwardButterflies(data, start, half, factors, width);
                        }
                    }
                    if (offset + width < half) {
                        int next = multiply(factors[width - 1], root);
                        for (int i = 0; i < width; i++) {
                            factors[i] = next;
                            next = multiply(next, root);
                        }
                    }
                }
            }
        }

        private void forwardButterflies(int[] data, int start, int half, int[] factors, int width) {
            for (int i = 0; i < width; i++) {
                int low = start + i, high = low + half;
                int a = data[low], b = data[high];
                data[low] = add(a, b);
                data[high] = multiply(difference(a, b), factors[i]);
            }
        }

        private void inverseButterflies(int[] data, int start, int half, int[] factors, int width) {
            for (int i = 0; i < width; i++) {
                int low = start + i, high = low + half;
                int a = data[low], b = multiply(data[high], factors[i]);
                data[low] = add(a, b);
                data[high] = difference(a, b);
            }
        }

        private int add(int a, int b) {
            long sum = (long) a + b;
            return (int) (sum >= modulus ? sum - modulus : sum);
        }

        private int difference(int a, int b) {
            int difference = a - b;
            return difference < 0 ? difference + modulus : difference;
        }
    }
}
