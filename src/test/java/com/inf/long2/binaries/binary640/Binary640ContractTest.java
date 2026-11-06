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
import com.inf.long2.binaries.Binary640;

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
            assertThrows(NullPointerException.class, () -> value.ieeeRemainder(null));
            assertThrows(NullPointerException.class, () -> value.fma(null, Binary640.NaN));
            assertThrows(NullPointerException.class, () -> value.fma(Binary640.NaN, null));
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
        Binary640 product = a.multiply(b), fused = a.fma(a, a), root = b.sqrt();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 80; i++) {
                tasks.add(() -> {
                    assertRaw(product, a.multiply(b));
                    assertRaw(fused, a.fma(a, a));
                    assertRaw(root, b.sqrt());
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
}
