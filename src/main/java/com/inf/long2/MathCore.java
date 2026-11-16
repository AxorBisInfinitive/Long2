package com.inf.long2;

import java.util.Objects;

import com.inf.long2.binaries.binary1280.Binary1280;
import com.inf.long2.binaries.binary256.Binary256;
import com.inf.long2.binaries.binary640.Binary640;
import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

/**
 * 转发 {@link BinaryMath} 的数学函数及 {@link IntMath} 的 abs、max 和 min。
 * 具体类型重载保留结果类型，{@code Binary<?>} 和 {@code Int<?>} 重载根据操作数的实际类型分派。
 *
 * <p>支持 {@link Binary256}、{@link Binary640}、{@link Binary1280}、{@link Int256}、{@link Int512} 和 {@link Int1024}。
 * 多操作数函数要求各操作数的具体类型相同。
 * 所有重载的引用参数为 null 时抛出 {@link NullPointerException}。
 * 接口类型重载在操作数类型不受支持或不一致时抛出 {@link IllegalArgumentException}。
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

    public static Int256 abs(Int256 value) {
        return Int256.abs(value);
    }

    public static Int512 abs(Int512 value) {
        return Int512.abs(value);
    }

    public static Int1024 abs(Int1024 value) {
        return Int1024.abs(value);
    }

    /** 按当前位宽取绝对值，最小值仍返回最小值。 */
    public static Int<?> abs(Int<?> value) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case Int256 v -> abs(v);
            case Int512 v -> abs(v);
            case Int1024 v -> abs(v);
            default -> throw unsupported(value);
        };
    }

    public static Int256 max(Int256 a, Int256 b) {
        return Int256.max(a, b);
    }

    public static Int512 max(Int512 a, Int512 b) {
        return Int512.max(a, b);
    }

    public static Int1024 max(Int1024 a, Int1024 b) {
        return Int1024.max(a, b);
    }

    /** 返回同一具体类型中数值较大的参数，相等时返回 a。 */
    public static Int<?> max(Int<?> a, Int<?> b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.getClass() != b.getClass()) {
            throw new IllegalArgumentException("max requires operands of the same Int type: "
                    + a.getClass().getName() + ", " + b.getClass().getName());
        }
        return switch (a) {
            case Int256 v -> max(v, (Int256) b);
            case Int512 v -> max(v, (Int512) b);
            case Int1024 v -> max(v, (Int1024) b);
            default -> throw unsupported(a);
        };
    }

    public static Int256 min(Int256 a, Int256 b) {
        return Int256.min(a, b);
    }

    public static Int512 min(Int512 a, Int512 b) {
        return Int512.min(a, b);
    }

    public static Int1024 min(Int1024 a, Int1024 b) {
        return Int1024.min(a, b);
    }

    /** 返回同一具体类型中数值较小的参数，相等时返回 a。 */
    public static Int<?> min(Int<?> a, Int<?> b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.getClass() != b.getClass()) {
            throw new IllegalArgumentException("min requires operands of the same Int type: "
                    + a.getClass().getName() + ", " + b.getClass().getName());
        }
        return switch (a) {
            case Int256 v -> min(v, (Int256) b);
            case Int512 v -> min(v, (Int512) b);
            case Int1024 v -> min(v, (Int1024) b);
            default -> throw unsupported(a);
        };
    }

    private static IllegalArgumentException unsupported(Int<?> value) {
        return new IllegalArgumentException("Unsupported Int type: " + value.getClass().getName());
    }

    private static IllegalArgumentException unsupported(Binary<?> value) {
        return new IllegalArgumentException("Unsupported Binary type: " + value.getClass().getName());
    }
}
