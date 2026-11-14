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
                }
                """);
    }

    @Test
    void rejectsInstanceMethodsAndContractTypedReturns() throws IOException {
        var result = compile("""
                class Target implements com.inf.long2.BinaryMath {
                    public Target fma(Target b, Target c) { return this; }
                    public static com.inf.long2.BinaryMath sqrt(Target value) { return value; }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.messages().contains("fma("), result.messages());
        assertTrue(result.messages().contains("sqrt("), result.messages());
    }
}
