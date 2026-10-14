package com.inf.long2;

public final class Int256 {
    // 大端序
    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    public Int256(long l0) {
        this(l0 >> 63, l0 >> 63, l0 >> 63, l0);
    }

    public Int256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    public String toHexString() {
        return "0x%016x%016x%016x%016x".formatted(
                l0, l1, l2, l3);
    }

    public Int256 not() {
        return new Int256(~l0, ~l1, ~l2, ~l3);
    }
}
