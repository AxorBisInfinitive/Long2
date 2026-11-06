package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

class Binary640FixedStringsTest {
    private static void check(Binary640 value, int digits) {
        BigDecimal exact = Binary640StringsTest.exact(value).abs();
        String sign = value.signBit() ? "-" : "";
        assertEquals(sign + exact.setScale(digits, RoundingMode.HALF_EVEN).toPlainString(), value.toString(digits));
        BigDecimal rounded = exact.round(new MathContext(digits + 1, RoundingMode.HALF_EVEN));
        int exponent = rounded.signum() == 0 ? 0 : rounded.precision() - rounded.scale() - 1;
        String mantissa = rounded.scaleByPowerOfTen(-exponent).setScale(digits, RoundingMode.HALF_EVEN).toPlainString();
        assertEquals(sign + mantissa + "e" + exponent, value.toScientificString(digits));
    }

    @Test
    void roundsStoredValuesDirectlyInsteadOfPaddingShortestStrings() {
        for (String text : new String[] {"0.1", "1e-100", "1e100", "-0.00125", "0", "-0", "0.125", "0.375", "2.5", "3.5", "9.99609375", "99.96875"}) {
            for (int digits : new int[] {0, 1, 2, 20, 185, 200}) {
                check(Binary640.of(text), digits);
            }
        }
        assertNotEquals("0.1" + "0".repeat(219), Binary640.of("0.1").toString(220));
        assertEquals("1.00e1", Binary640.of("9.99609375").toScientificString(2));
    }

    @Test
    void checksRandomFixedOutputsAndMidpointNeighbors() {
        Random random = new Random(12345);
        for (int i = 0; i < 100; i++) {
            check(random(random, false), random.nextInt(221));
        }
        for (String text : new String[] {"0.125", "0.375", "1.25", "2.5", "3.5", "9.5", "99.5"}) {
            Binary640 middle = Binary640.of(text);
            for (Binary640 value : new Binary640[] {middle.nextDown(), middle, middle.nextUp()}) {
                for (int digits : new int[] {0, 1, 2}) {
                    check(value, digits);
                    check(value.negate(), digits);
                }
            }
        }
    }

    @Test
    void checksNegativeDigitsBeforeSpecialValuesAndRejectsLengthOverflow() {
        for (Binary640 value : new Binary640[] {Binary640.ZERO, Binary640.ONE, Binary640.NaN,
                Binary640.POSITIVE_INFINITY, Binary640.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> value.toString(-1));
            assertThrows(IllegalArgumentException.class, () -> value.toScientificString(-1));
            if (value.isFinite()) {
                assertThrows(IllegalArgumentException.class, () -> value.toString(Integer.MAX_VALUE));
                assertThrows(IllegalArgumentException.class, () -> value.toScientificString(Integer.MAX_VALUE));
            } else {
                assertEquals(value.toString(), value.toString(Integer.MAX_VALUE));
                assertEquals(value.toScientificString(), value.toScientificString(Integer.MAX_VALUE));
            }
        }
    }
}
