package com.inf.long2;

public class Main {
    public static void main(String[] args) {
        Int256 value = new Int256(~1119);
        System.out.print(value.toHexString());
    }
}
