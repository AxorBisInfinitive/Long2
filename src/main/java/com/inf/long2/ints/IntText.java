package com.inf.long2.ints;

import java.util.Objects;

/** 固定补码的文本转换，数组按低组在前存放；解析结果保留指定的正分组数。 */
final class IntText {
    private IntText() {
    }

    static boolean isValid(String value) {
        return scanDecimal(value) >= 0;
    }

    /** 返回首位数字索引，非法文本和 null 返回 -1，不执行数值转换。 */
    static int scanDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        char first = value.charAt(0);
        int start = first == '+' || first == '-' ? 1 : 0;
        if (start == value.length()) {
            return -1;
        }
        for (int index = start; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c < '0' || c > '9') {
                return -1;
            }
        }
        return start;
    }

    /** 解析十进制的低目标位宽，包括 null 在内的非法文本抛出 NumberFormatException。 */
    static long[] parseDecimal(String value, int wordCount) {
        int index = scanDecimal(value);
        if (index < 0) {
            throw new NumberFormatException("Invalid decimal value: " + value);
        }
        boolean negative = value.charAt(0) == '-';
        long[] words = new long[wordCount];
        while (index < value.length()) {
            int count = Math.min(9, value.length() - index), factor = 1, chunk = 0;
            for (int digit = 0; digit < count; digit++) {
                chunk = chunk * 10 + value.charAt(index++) - '0';
                factor *= 10;
            }
            long carry = chunk;
            for (int group = 0; group < words.length; group++) {
                long low = (words[group] & 0xffff_ffffL) * factor + carry;
                long high = (words[group] >>> 32) * factor + (low >>> 32);
                words[group] = (high << 32) | (low & 0xffff_ffffL);
                carry = high >>> 32;
            }
            // 丢弃目标位宽之外的进位，长文本同样按固定补码回绕。
        }
        if (negative) {
            IntWords.negateInPlace(words);
        }
        return words;
    }

    /** 消耗调用方独占的非负幅值数组，保留数组长度；零忽略 negative 并输出 "0"。 */
    static String toDecimal(long[] magnitude, boolean negative) {
        int length = IntWords.bitLength(magnitude);
        if (length == 0) {
            return "0";
        }
        // 30103/100000 略大于 log10(2)，向上估计字符数并按九位分组预留空间。
        int digits = (int) (length * 30103L / 100000) + 1;
        char[] chars = new char[((digits + 8) / 9) * 9 + (negative ? 1 : 0)];
        int position = chars.length;
        long remaining;
        do {
            long remainder = 0;
            remaining = 0;
            for (int index = magnitude.length - 1; index >= 0; index--) {
                // remainder 小于 10^9，拼接 32 位后仍可使用有符号 long 除法。
                long pair = (remainder << 32) | (magnitude[index] >>> 32);
                long high = pair / 1_000_000_000;
                remainder = pair % 1_000_000_000;
                pair = (remainder << 32) | (magnitude[index] & 0xffff_ffffL);
                magnitude[index] = (high << 32) | (pair / 1_000_000_000);
                remainder = pair % 1_000_000_000;
                remaining |= magnitude[index];
            }
            for (int digit = 0; digit < 9; digit++) {
                chars[--position] = (char) ('0' + remainder % 10);
                remainder /= 10;
            }
        } while (remaining != 0);
        while (chars[position] == '0') {
            position++;
        }
        if (negative) {
            chars[--position] = '-';
        }
        return new String(chars, position, chars.length - position);
    }

    static boolean isHexValid(String value) {
        if (value == null || value.length() <= 2 || !value.startsWith("0x")) {
            return false;
        }
        for (int index = 2; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    /** 校验完整文本后保留低目标位宽，null 抛出 NullPointerException，非法文本抛出 NumberFormatException。 */
    static long[] parseHex(String value, int wordCount) {
        Objects.requireNonNull(value);
        if (!isHexValid(value)) {
            throw new NumberFormatException("Invalid hexadecimal value: " + value);
        }
        long[] words = new long[wordCount];
        int end = value.length();
        for (int group = 0; group < words.length && end > 2; group++) {
            int start = Math.max(2, end - 16);
            words[group] = Long.parseUnsignedLong(value, start, end, 16);
            end = start;
        }
        return words;
    }
}
