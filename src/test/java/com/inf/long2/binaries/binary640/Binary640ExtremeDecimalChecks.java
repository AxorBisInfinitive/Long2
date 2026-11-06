package com.inf.long2.binaries.binary640;

import static com.inf.long2.binaries.binary640.Binary640TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

import com.inf.long2.binaries.Binary640;

/** 百万位指数的精确文本检查，使用显式测试类名执行。 */
class Binary640ExtremeDecimalChecks {
    @Test
    void roundTripsExtremeScientificValuesAndChecksTheirExactRoundingIntervals() {
        for (Binary640 value : new Binary640[] {Binary640.MIN_VALUE, Binary640.MIN_NORMAL.negate(), Binary640.MAX_VALUE}) {
            String text = value.toScientificString();
            assertTrue(text.length() < 210);
            assertRaw(value, Binary640.of(text));
            BigDecimal decimal = new BigDecimal(text);
            assertRaw(value, Binary640StringsTest.reference(decimal, value.signBit()));
        }
    }

    @Test
    void emitsAndParsesLongPlainOutputAndHandlesBeyondRangeExponents() {
        Binary640 value = Binary640.ONE.scalb(1000000);
        String plain = value.toString();
        assertTrue(plain.length() > 300000);
        BigDecimal decimal = new BigDecimal(plain).stripTrailingZeros();
        BigDecimal expected = new BigDecimal(BigInteger.ONE.shiftLeft(1000000))
                .round(new MathContext(decimal.precision(), RoundingMode.HALF_EVEN));
        assertEquals(0, expected.compareTo(decimal));
        assertRaw(value, Binary640.of(plain));
        assertRaw(Binary640.POSITIVE_INFINITY, Binary640.of("1e2600001"));
        assertRaw(Binary640.NEGATIVE_ZERO, Binary640.of("-1e-2600001"));
    }
}
