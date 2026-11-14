package com.inf.long2;

import com.inf.long2.util.annotations.StaticMethodImplementsCheck;

/**
 * 二进制浮点数的静态数学函数契约。
 * 注解处理器将签名中的 BinaryMath 替换为具体实现类型。
 * 方法体是声明占位，调用者应使用具体类型的静态方法。
 */
@StaticMethodImplementsCheck
public interface BinaryMath {

    /**
     * 精确计算 {@code multiplicand * multiplier + addend}，再按最近偶数规则舍入一次。
     * 任一参数为 NaN、零乘无穷，或无穷乘积加上异号无穷时返回 NaN。
     * 零结果的符号遵循精确乘积与加数的浮点加法规则。
     *
     * @throws NullPointerException 任一参数为 null
     */
    public static BinaryMath fma(BinaryMath multiplicand, BinaryMath multiplier, BinaryMath addend) {
        return null;
    }

    /**
     * 返回按最近偶数规则正确舍入的平方根。
     * 负的非零数和 NaN 返回 NaN，负零保留负号，正无穷返回正无穷。
     *
     * @throws NullPointerException 参数为 null
     */
    public static BinaryMath sqrt(BinaryMath value) {
        return null;
    }

    /**
     * 精确缩放为 {@code value * 2^scaleFactor}，再按最近偶数规则舍入。
     * 全部 long 指数均有效，指数运算不得回绕。
     * 保留零和无穷的符号，NaN 返回 NaN，上溢和下溢按目标格式处理。
     *
     * @throws NullPointerException 参数为 null
     */
    public static BinaryMath scalb(BinaryMath value, long scaleFactor) {
        return null;
    }

    /**
     * 返回 {@code dividend - n * divisor}，n 是精确商的最近整数，中点取偶数。
     * 任一操作数为 NaN、被除数为无穷或除数为零时返回 NaN。
     * 有限数对无穷取余返回被除数，零余数保留被除数符号。
     * 例如五对三的 IEEE 余数为负一。
     *
     * @throws NullPointerException 任一参数为 null
     */
    public static BinaryMath ieeeRemainder(BinaryMath dividend, BinaryMath divisor) {
        return null;
    }

    /**
     * 返回绝对值，将负零变为正零、负无穷变为正无穷，NaN 返回 NaN。
     *
     * @throws NullPointerException 参数为 null
     */
    public static BinaryMath abs(BinaryMath value) {
        return null;
    }
}
