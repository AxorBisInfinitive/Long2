package com.inf.long2.binaries.binary640;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import com.inf.long2.binaries.Binary640;

/** 与独立数值工具交换原始编码，不经 double 或十进制文本中转操作数。 */
public final class Binary640MpfrProbe {
    private Binary640MpfrProbe() {
    }

    public static void main(String[] args) throws Exception {
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                var output = new PrintWriter(System.out, false, StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split(" ");
                String operation = fields[0];
                if (operation.equals("parse")) {
                    output.println(raw(Binary640.of(fields[1])));
                    continue;
                }
                Binary640 a = parse(fields[1]);
                Binary640 b = fields.length > 2 && !operation.equals("scalb") ? parse(fields[2]) : Binary640.ZERO;
                Binary640 c = fields.length > 3 ? parse(fields[3]) : Binary640.ZERO;
                String result = switch (operation) {
                    case "add" -> raw(a.add(b));
                    case "sub" -> raw(a.subtract(b));
                    case "mul" -> raw(a.multiply(b));
                    case "div" -> raw(a.divide(b));
                    case "fma" -> raw(a.fma(b, c));
                    case "rem" -> raw(a.remainder(b));
                    case "ieee" -> raw(a.ieeeRemainder(b));
                    case "sqrt" -> raw(a.sqrt());
                    case "scalb" -> raw(a.scalb(Long.parseLong(fields[2])));
                    case "float" -> Integer.toHexString(Float.floatToRawIntBits(a.toFloat()));
                    case "double" -> Long.toHexString(Double.doubleToRawLongBits(a.toDouble()));
                    case "int" -> Integer.toHexString(a.toInt());
                    case "long" -> Long.toHexString(a.toLong());
                    case "scientific" -> a.toScientificString();
                    default -> throw new IllegalArgumentException(operation);
                };
                output.println(result);
            }
        }
    }

    private static Binary640 parse(String bits) {
        long[] words = new long[10];
        for (int i = 0; i < words.length; i++) {
            words[i] = Long.parseUnsignedLong(bits, i * 16, i * 16 + 16, 16);
        }
        return Binary640.fromRawBits(words[0], words[1], words[2], words[3], words[4],
                words[5], words[6], words[7], words[8], words[9]);
    }

    private static String raw(Binary640 value) {
        StringBuilder result = new StringBuilder(160);
        for (int i = 0; i < 10; i++) {
            String word = Long.toHexString(value.getRawLong(i));
            result.append("0".repeat(16 - word.length())).append(word);
        }
        return result.toString();
    }
}
