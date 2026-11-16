package com.inf.long2;

import com.inf.long2.util.annotations.StaticMethodImplementsCheck;

/**
 * 定宽有符号整数的静态数学函数契约。
 * 注解处理器将签名中的 IntMath 替换为具体实现类型。
 * 方法体是声明占位，调用者应使用具体类型的静态方法。
 */
@StaticMethodImplementsCheck
public interface IntMath {

    /**
     * 返回绝对值，非负数返回原实例，负数按当前位宽取负。
     * 最小值取负后回绕，仍返回最小值。
     *
     * @throws NullPointerException 参数为 null
     */
    public static IntMath abs(IntMath value) {
        return null;
    }

    /**
     * 按有符号数值比较并返回较大的参数，数值相等时返回 a。
     *
     * @throws NullPointerException 任一参数为 null
     */
    public static IntMath max(IntMath a, IntMath b) {
        return null;
    }

    /**
     * 按有符号数值比较并返回较小的参数，数值相等时返回 a。
     *
     * @throws NullPointerException 任一参数为 null
     */
    public static IntMath min(IntMath a, IntMath b) {
        return null;
    }

    /**
     * 从任意 Binary 实现的精确源数值向负无穷取整，越界时钳制到当前位宽的对应极值。
     * NaN 和正负零转为零，正负无穷分别转为最大值和最小值。
     * 负的非零次正规数转为负一；通过有效数字分组和位权读取精确值。
     *
     * @throws NullPointerException 参数为 null
     */
    public static IntMath ofFloor(Binary<?> value) {
        return null;
    }

    /**
     * 将有限 double 向负无穷取整，越界时钳制到当前位宽的对应极值。
     * NaN 和正负零转为零，正负无穷分别转为最大值和最小值。
     */
    public static IntMath ofFloor(double value) {
        return null;
    }
}
