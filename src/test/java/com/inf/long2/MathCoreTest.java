package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.inf.long2.binaries.binary1280.Binary1280;
import com.inf.long2.binaries.binary256.Binary256;
import com.inf.long2.binaries.binary640.Binary640;
import com.inf.long2.binaries.binary256.baseline.Binary256Baseline;

class MathCoreTest {
    @Test
    void forwardsBinary256ResultsBitForBit() {
        Binary256 multiplier = Binary256.of(3);
        Binary256 addend = Binary256.of(-15);
        byte[] nanBits = Binary256.NEGATIVE_INFINITY.toBytes();
        nanBits[nanBits.length - 1] = 1;
        for (Binary256 value : new Binary256[] {Binary256.of(5), Binary256.of(-6), Binary256.ONE.nextUp(),
                Binary256.MIN_VALUE, Binary256.MAX_VALUE, Binary256.ZERO, Binary256.NEGATIVE_ZERO,
                Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY,
                Binary256.of(nanBits)}) {
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
        byte[] nanBits = Binary640.NEGATIVE_INFINITY.toBytes();
        nanBits[nanBits.length - 1] = 1;
        for (Binary640 value : new Binary640[] {Binary640.of(5), Binary640.of(-6), Binary640.ONE.nextUp(),
                Binary640.MIN_VALUE, Binary640.MAX_VALUE, Binary640.ZERO, Binary640.NEGATIVE_ZERO,
                Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY,
                Binary640.of(nanBits)}) {
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
    void forwardsBinary1280ResultsBitForBit() {
        Binary1280 multiplier = Binary1280.of(3);
        Binary1280 addend = Binary1280.of(-15);
        byte[] nanBits = Binary1280.NEGATIVE_INFINITY.toBytes();
        nanBits[nanBits.length - 1] = 1;
        for (Binary1280 value : new Binary1280[] {Binary1280.of(5), Binary1280.of(-6), Binary1280.ONE.nextUp(),
                Binary1280.MIN_VALUE, Binary1280.MAX_VALUE, Binary1280.ZERO, Binary1280.NEGATIVE_ZERO,
                Binary1280.POSITIVE_INFINITY, Binary1280.NEGATIVE_INFINITY, Binary1280.of(nanBits)}) {
            Binary<?> dynamic = value;
            Binary1280 root = MathCore.sqrt(value);
            Binary1280 fused = MathCore.fma(value, multiplier, addend);
            Binary1280 remainder = MathCore.ieeeRemainder(value, multiplier);
            Binary1280 absolute = MathCore.abs(value);
            assertRaw(Binary1280.sqrt(value), root, MathCore.sqrt(dynamic));
            assertRaw(Binary1280.fma(value, multiplier, addend), fused, MathCore.fma(dynamic, multiplier, addend));
            assertRaw(Binary1280.ieeeRemainder(value, multiplier), remainder, MathCore.ieeeRemainder(dynamic, multiplier));
            assertRaw(Binary1280.abs(value), absolute, MathCore.abs(dynamic));
            for (long scale : new long[] {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
                Binary1280 scaled = MathCore.scalb(value, scale);
                assertRaw(Binary1280.scalb(value, scale), scaled, MathCore.scalb(dynamic, scale));
            }
        }
    }

    @Test
    void rejectsMixedFormatsInEveryOperandPosition() {
        Binary<?>[] values = {Binary256.ONE, Binary640.ONE, Binary1280.ONE};
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertMixedFormatsRejected(values[i], values[j]);
                assertMixedFormatsRejected(values[j], values[i]);
            }
        }
    }

    private static void assertMixedFormatsRejected(Binary<?> left, Binary<?> right) {
        for (Executable call : new Executable[] {
                () -> MathCore.fma(left, right, right),
                () -> MathCore.fma(right, left, right),
                () -> MathCore.fma(right, right, left),
                () -> MathCore.ieeeRemainder(left, right)}) {
            var failure = assertThrows(IllegalArgumentException.class, call);
            assertTrue(failure.getMessage().contains(left.getClass().getName()));
            assertTrue(failure.getMessage().contains(right.getClass().getName()));
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
        Binary1280 widest = Binary1280.ONE;
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
                () -> MathCore.sqrt((Binary1280) null),
                () -> MathCore.fma(null, widest, widest),
                () -> MathCore.fma(widest, null, widest),
                () -> MathCore.fma(widest, widest, null),
                () -> MathCore.scalb((Binary1280) null, 1),
                () -> MathCore.ieeeRemainder(null, widest),
                () -> MathCore.ieeeRemainder(widest, null),
                () -> MathCore.abs((Binary1280) null),
                () -> MathCore.sqrt(missing),
                () -> MathCore.fma(missing, narrow, wide),
                () -> MathCore.fma(narrow, missing, wide),
                () -> MathCore.fma(widest, wide, missing),
                () -> MathCore.scalb(missing, 1),
                () -> MathCore.ieeeRemainder(missing, narrow),
                () -> MathCore.ieeeRemainder(widest, missing),
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
