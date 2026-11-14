package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Int;

class Binary640ContractTest {
    @Test
    void declaresAllStaticContractMembersAndTenImmutableWords() throws Exception {
        for (String name : new String[] {"FORMAT", "ZERO", "NEGATIVE_ZERO", "ONE", "MIN_VALUE", "MIN_NORMAL",
                "MAX_VALUE", "POSITIVE_INFINITY", "NEGATIVE_INFINITY", "NaN"}) {
            var field = Binary640.class.getDeclaredField(name);
            assertTrue(Modifier.isPublic(field.getModifiers()));
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertNotNull(field.get(null));
            assertEquals(name.equals("FORMAT") ? Binary.Format.class : Binary640.class, field.getType());
        }
        for (Class<?> parameter : new Class<?>[] {long.class, float.class, double.class, String.class, byte[].class, Int.class, Binary.class}) {
            var method = Binary640.class.getDeclaredMethod("of", parameter);
            assertTrue(Modifier.isPublic(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            assertEquals(Binary640.class, method.getReturnType());
        }
        int fields = 0;
        for (var field : Binary640.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fields++;
                assertEquals(long.class, field.getType());
                assertTrue(Modifier.isPrivate(field.getModifiers()));
                assertTrue(Modifier.isFinal(field.getModifiers()));
            }
        }
        assertEquals(10, fields);
    }

    @Test
    void checksNullBeforeSpecialValueShortcuts() {
        for (Binary640 value : new Binary640[] {Binary640.ZERO, Binary640.ONE, Binary640.NaN, Binary640.POSITIVE_INFINITY}) {
            assertThrows(NullPointerException.class, () -> value.add(null));
            assertThrows(NullPointerException.class, () -> value.subtract(null));
            assertThrows(NullPointerException.class, () -> value.multiply(null));
            assertThrows(NullPointerException.class, () -> value.divide(null));
            assertThrows(NullPointerException.class, () -> value.remainder(null));
            assertThrows(NullPointerException.class, () -> Binary640.ieeeRemainder(value, null));
            assertThrows(NullPointerException.class, () -> Binary640.fma(null, value, Binary640.NaN));
            assertThrows(NullPointerException.class, () -> Binary640.fma(value, null, Binary640.NaN));
            assertThrows(NullPointerException.class, () -> Binary640.fma(value, Binary640.NaN, null));
            assertThrows(NullPointerException.class, () -> value.compareTo(null));
            assertThrows(NullPointerException.class, () -> value.compareNumerically(null));
        }
        assertThrows(NullPointerException.class, () -> Binary640.of((Binary<?>) null));
        assertThrows(NullPointerException.class, () -> Binary640.of((Int<?>) null));
        assertThrows(NullPointerException.class, () -> Binary640.of((String) null));
        assertFalse(Binary640.isValid(null));
    }

    @Test
    void keepsIndependentResultsAcrossAliasedAndConcurrentCalls() throws Exception {
        Binary640 a = Binary640.ONE.nextUp(), b = Binary640.of(3L);
        byte[] beforeA = a.toBytes(), beforeB = b.toBytes();
        Binary640 product = a.multiply(b), fused = Binary640.fma(a, a, a), root = Binary640.sqrt(b);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 80; i++) {
                tasks.add(() -> {
                    assertRaw(product, a.multiply(b));
                    assertRaw(fused, Binary640.fma(a, a, a));
                    assertRaw(root, Binary640.sqrt(b));
                    return null;
                });
            }
            for (var future : executor.invokeAll(tasks)) {
                future.get();
            }
        }
        assertArrayEquals(beforeA, a.toBytes());
        assertArrayEquals(beforeB, b.toBytes());
    }

    @Test
    void checksStaticMathNullsAndAbsoluteValueSpecialCases() {
        assertThrows(NullPointerException.class, () -> Binary640.scalb(null, 0));
        assertThrows(NullPointerException.class, () -> Binary640.ieeeRemainder(null, Binary640.ZERO));
        assertThrows(NullPointerException.class, () -> Binary640.abs(null));
        assertRaw(Binary640.ZERO, Binary640.abs(Binary640.NEGATIVE_ZERO));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.abs(Binary640.NEGATIVE_INFINITY));
        assertRaw(Binary640.NaN, Binary640.abs(Binary640.NaN));
    }
}
