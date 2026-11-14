package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.inf.long2.binaries.Binary256;
import com.inf.long2.binaries.Binary640;
import com.inf.long2.binaries.binary256.baseline.Binary256Baseline;

class MathCoreTest {
    @Test
    void forwardsBinary256ResultsBitForBit() {
        Binary256 multiplier = Binary256.of(3);
        Binary256 addend = Binary256.of(-15);
        for (Binary256 value : new Binary256[] {Binary256.of(5), Binary256.of(-6), Binary256.ONE.nextUp(),
                Binary256.MIN_VALUE, Binary256.MAX_VALUE, Binary256.ZERO, Binary256.NEGATIVE_ZERO,
                Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY,
                Binary256.fromRawBits(0xffff_f000_0000_0000L, 0, 0, 1)}) {
            Binary<?> dynamic = value;
            Binary256 root = MathCore.sqrt(value);
            Binary256 fused = MathCore.fma(value, multiplier, addend);
            Binary256 remainder = MathCore.ieeeRemainder(value, multiplier);
            Binary256 absolute = MathCore.abs(value);
            assertRaw(Binary256.sqrt(value), root, MathCore.sqrt(dynamic));
            assertRaw(Binary256.fma(value, multiplier, addend), fused, MathCore.fma(dynamic, multiplier, addend));
            assertRaw(Binary256.ieeeRemainder(value, multiplier), remainder, MathCore.ieeeRemainder(dynamic, multiplier));
            assertRaw(Binary256.abs(value), absolute, MathCore.abs(dynamic));
            for (long scale : new long[] {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
                Binary256 scaled = MathCore.scalb(value, scale);
                assertRaw(Binary256.scalb(value, scale), scaled, MathCore.scalb(dynamic, scale));
            }
        }
    }

    @Test
    void forwardsBinary640ResultsBitForBit() {
        Binary640 multiplier = Binary640.of(3);
        Binary640 addend = Binary640.of(-15);
        for (Binary640 value : new Binary640[] {Binary640.of(5), Binary640.of(-6), Binary640.ONE.nextUp(),
                Binary640.MIN_VALUE, Binary640.MAX_VALUE, Binary640.ZERO, Binary640.NEGATIVE_ZERO,
                Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY,
                Binary640.fromRawBits(0xffff_ff80_0000_0000L, 0, 0, 0, 0, 0, 0, 0, 0, 1)}) {
            Binary<?> dynamic = value;
            Binary640 root = MathCore.sqrt(value);
            Binary640 fused = MathCore.fma(value, multiplier, addend);
            Binary640 remainder = MathCore.ieeeRemainder(value, multiplier);
            Binary640 absolute = MathCore.abs(value);
            assertRaw(Binary640.sqrt(value), root, MathCore.sqrt(dynamic));
            assertRaw(Binary640.fma(value, multiplier, addend), fused, MathCore.fma(dynamic, multiplier, addend));
            assertRaw(Binary640.ieeeRemainder(value, multiplier), remainder, MathCore.ieeeRemainder(dynamic, multiplier));
            assertRaw(Binary640.abs(value), absolute, MathCore.abs(dynamic));
            for (long scale : new long[] {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
                Binary640 scaled = MathCore.scalb(value, scale);
                assertRaw(Binary640.scalb(value, scale), scaled, MathCore.scalb(dynamic, scale));
            }
        }
    }

    @Test
    void rejectsMixedFormatsInEveryOperandPosition() {
        Binary<?> narrow = Binary256.ONE;
        Binary<?> wide = Binary640.ONE;
        for (Executable call : new Executable[] {
                () -> MathCore.fma(wide, narrow, narrow),
                () -> MathCore.fma(narrow, wide, narrow),
                () -> MathCore.fma(narrow, narrow, wide),
                () -> MathCore.ieeeRemainder(narrow, wide),
                () -> MathCore.ieeeRemainder(wide, narrow)}) {
            var failure = assertThrows(IllegalArgumentException.class, call);
            assertTrue(failure.getMessage().contains(Binary256.class.getName()));
            assertTrue(failure.getMessage().contains(Binary640.class.getName()));
        }
    }

    @Test
    void rejectsUnknownImplementationsEvenWhenFormatsMatch() {
        Binary<?> unknown = Binary256Baseline.ONE;
        for (Executable call : new Executable[] {
                () -> MathCore.sqrt(unknown),
                () -> MathCore.fma(unknown, unknown, unknown),
                () -> MathCore.scalb(unknown, 1),
                () -> MathCore.ieeeRemainder(unknown, unknown),
                () -> MathCore.abs(unknown)}) {
            var failure = assertThrows(IllegalArgumentException.class, call);
            assertTrue(failure.getMessage().contains("Unsupported Binary type"));
            assertTrue(failure.getMessage().contains(unknown.getClass().getName()));
        }
    }

    @Test
    void rejectsTypedNullsAndChecksAllOperandsBeforeDispatch() {
        Binary256 narrow = Binary256.ONE;
        Binary640 wide = Binary640.ONE;
        Binary<?> missing = null;
        for (Executable call : new Executable[] {
                () -> MathCore.sqrt((Binary256) null),
                () -> MathCore.fma((Binary256) null, narrow, narrow),
                () -> MathCore.scalb((Binary256) null, 1),
                () -> MathCore.ieeeRemainder(narrow, null),
                () -> MathCore.abs((Binary256) null),
                () -> MathCore.sqrt((Binary640) null),
                () -> MathCore.fma((Binary640) null, wide, wide),
                () -> MathCore.scalb((Binary640) null, 1),
                () -> MathCore.ieeeRemainder(wide, null),
                () -> MathCore.abs((Binary640) null),
                () -> MathCore.sqrt(missing),
                () -> MathCore.fma(missing, narrow, wide),
                () -> MathCore.fma(narrow, missing, wide),
                () -> MathCore.fma(narrow, wide, missing),
                () -> MathCore.scalb(missing, 1),
                () -> MathCore.ieeeRemainder(missing, narrow),
                () -> MathCore.ieeeRemainder(narrow, missing),
                () -> MathCore.abs(missing)}) {
            assertThrows(NullPointerException.class, call);
        }
    }

    private static void assertRaw(Binary<?> expected, Binary<?> concrete, Binary<?> dynamic) {
        assertSame(expected.getClass(), dynamic.getClass());
        assertArrayEquals(expected.toBytes(), concrete.toBytes());
        assertArrayEquals(expected.toBytes(), dynamic.toBytes());
    }
}
