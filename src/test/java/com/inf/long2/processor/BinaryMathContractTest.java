package com.inf.long2.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class BinaryMathContractTest extends ProcessorTestSupport {
    @Test
    void acceptsConcreteStaticSignatures() throws IOException {
        assertCompiles("""
                class Target implements com.inf.long2.BinaryMath {
                    public static Target fma(Target a, Target b, Target c) { return a; }
                    public static Target sqrt(Target value) { return value; }
                    public static Target scalb(Target value, long scaleFactor) { return value; }
                    public static Target ieeeRemainder(Target dividend, Target divisor) { return dividend; }
                    public static Target abs(Target value) { return value; }
                }
                """);
    }

    @Test
    void rejectsInstanceMethodsAndContractTypedReturns() throws IOException {
        var result = compile("""
                class Target implements com.inf.long2.BinaryMath {
                    public Target fma(Target b, Target c) { return this; }
                    public static com.inf.long2.BinaryMath sqrt(Target value) { return value; }
                    public static Target scalb(Target value, int scaleFactor) { return value; }
                    public Target ieeeRemainder(Target divisor) { return this; }
                    public Target abs() { return this; }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.messages().contains("fma("), result.messages());
        assertTrue(result.messages().contains("sqrt("), result.messages());
        assertTrue(result.messages().contains("scalb("), result.messages());
        assertTrue(result.messages().contains("ieeeRemainder("), result.messages());
        assertTrue(result.messages().contains("abs("), result.messages());
    }
}
