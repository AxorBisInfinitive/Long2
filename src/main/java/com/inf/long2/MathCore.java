package com.inf.long2;

import java.util.Objects;

import com.inf.long2.binaries.Binary1280;
import com.inf.long2.binaries.Binary256;
import com.inf.long2.binaries.Binary640;

/**
 * 转发 {@link BinaryMath} 的数学函数。具体类型重载保留结果类型，
 * {@code Binary<?>} 重载根据操作数的实际类型分派。
 *
 * <p>支持 {@link Binary256}、{@link Binary640} 和 {@link Binary1280}。多操作数函数要求各操作数的具体类型相同。
 * 所有重载的引用参数为 null 时抛出 {@link NullPointerException}。
 * {@code Binary<?>} 重载在操作数类型不受支持或不一致时抛出 {@link IllegalArgumentException}。
 */
public final class MathCore {
    private MathCore() {
    }

    public static Binary256 sqrt(Binary256 value) {
        return Binary256.sqrt(value);
    }

    public static Binary640 sqrt(Binary640 value) {
        return Binary640.sqrt(value);
    }

    public static Binary1280 sqrt(Binary1280 value) {
        return Binary1280.sqrt(value);
    }

    public static Binary<?> sqrt(Binary<?> value) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case Binary256 v -> sqrt(v);
            case Binary640 v -> sqrt(v);
            case Binary1280 v -> sqrt(v);
            default -> throw unsupported(value);
        };
    }

    public static Binary256 fma(Binary256 multiplicand, Binary256 multiplier, Binary256 addend) {
        return Binary256.fma(multiplicand, multiplier, addend);
    }

    public static Binary640 fma(Binary640 multiplicand, Binary640 multiplier, Binary640 addend) {
        return Binary640.fma(multiplicand, multiplier, addend);
    }

    public static Binary1280 fma(Binary1280 multiplicand, Binary1280 multiplier, Binary1280 addend) {
        return Binary1280.fma(multiplicand, multiplier, addend);
    }

    public static Binary<?> fma(Binary<?> multiplicand, Binary<?> multiplier, Binary<?> addend) {
        Objects.requireNonNull(multiplicand, "multiplicand");
        Objects.requireNonNull(multiplier, "multiplier");
        Objects.requireNonNull(addend, "addend");
        if (multiplicand.getClass() != multiplier.getClass() || multiplicand.getClass() != addend.getClass()) {
            throw new IllegalArgumentException("FMA requires operands of the same Binary type: "
                    + multiplicand.getClass().getName() + ", " + multiplier.getClass().getName()
                    + ", " + addend.getClass().getName());
        }
        return switch (multiplicand) {
            case Binary256 v -> fma(v, (Binary256) multiplier, (Binary256) addend);
            case Binary640 v -> fma(v, (Binary640) multiplier, (Binary640) addend);
            case Binary1280 v -> fma(v, (Binary1280) multiplier, (Binary1280) addend);
            default -> throw unsupported(multiplicand);
        };
    }

    public static Binary256 scalb(Binary256 value, long scaleFactor) {
        return Binary256.scalb(value, scaleFactor);
    }

    public static Binary640 scalb(Binary640 value, long scaleFactor) {
        return Binary640.scalb(value, scaleFactor);
    }

    public static Binary1280 scalb(Binary1280 value, long scaleFactor) {
        return Binary1280.scalb(value, scaleFactor);
    }

    public static Binary<?> scalb(Binary<?> value, long scaleFactor) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case Binary256 v -> scalb(v, scaleFactor);
            case Binary640 v -> scalb(v, scaleFactor);
            case Binary1280 v -> scalb(v, scaleFactor);
            default -> throw unsupported(value);
        };
    }

    public static Binary256 ieeeRemainder(Binary256 dividend, Binary256 divisor) {
        return Binary256.ieeeRemainder(dividend, divisor);
    }

    public static Binary640 ieeeRemainder(Binary640 dividend, Binary640 divisor) {
        return Binary640.ieeeRemainder(dividend, divisor);
    }

    public static Binary1280 ieeeRemainder(Binary1280 dividend, Binary1280 divisor) {
        return Binary1280.ieeeRemainder(dividend, divisor);
    }

    public static Binary<?> ieeeRemainder(Binary<?> dividend, Binary<?> divisor) {
        Objects.requireNonNull(dividend, "dividend");
        Objects.requireNonNull(divisor, "divisor");
        if (dividend.getClass() != divisor.getClass()) {
            throw new IllegalArgumentException("IEEE remainder requires operands of the same Binary type: "
                    + dividend.getClass().getName() + ", " + divisor.getClass().getName());
        }
        return switch (dividend) {
            case Binary256 v -> ieeeRemainder(v, (Binary256) divisor);
            case Binary640 v -> ieeeRemainder(v, (Binary640) divisor);
            case Binary1280 v -> ieeeRemainder(v, (Binary1280) divisor);
            default -> throw unsupported(dividend);
        };
    }

    public static Binary256 abs(Binary256 value) {
        return Binary256.abs(value);
    }

    public static Binary640 abs(Binary640 value) {
        return Binary640.abs(value);
    }

    public static Binary1280 abs(Binary1280 value) {
        return Binary1280.abs(value);
    }

    public static Binary<?> abs(Binary<?> value) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case Binary256 v -> abs(v);
            case Binary640 v -> abs(v);
            case Binary1280 v -> abs(v);
            default -> throw unsupported(value);
        };
    }

    private static IllegalArgumentException unsupported(Binary<?> value) {
        return new IllegalArgumentException("Unsupported Binary type: " + value.getClass().getName());
    }
}
