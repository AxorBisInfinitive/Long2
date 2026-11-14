# Long2

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)

[如何使用 AP 静态方法实现检查器](docs/using_static_method_implements_check.md)

**转发门面**：[MathCore.java](src/main/java/com/inf/long2/MathCore.java)

**数值类型**：

- [Int256.java](src/main/java/com/inf/long2/ints/Int256.java)
- [Int512.java](src/main/java/com/inf/long2/ints/Int512.java)
- [Int1024.java](src/main/java/com/inf/long2/ints/Int1024.java)
- [Binary256.java](src/main/java/com/inf/long2/binaries/binary256/Binary256.java)
- [Binary640.java](src/main/java/com/inf/long2/binaries/binary640/Binary640.java)
- [Binary1280.java](src/main/java/com/inf/long2/binaries/binary1280/Binary1280.java)

## 测试

`mvn test` 运行日常回归，包含六种数值类型的基本字符串解析、格式化和语法校验。全指数范围往返、随机差分、长文本以及耗时的精确中点用例通过 profile 显式运行。

```powershell
mvn test
mvn -Pnumeric-text test
mvn -Pnumeric-text-extreme '-DargLine=-Xmx2g' test
```

`numeric-text` 在日常回归之外运行扩展字符串用例。`numeric-text-extreme` 单独运行 Binary640 和 Binary1280 的极端十进制检查，其中包含千万字符级文本，可能耗时较长。也可在对应 profile 下使用 `-Dtest=类名#方法名` 选取单项。

## 作为依赖

**仓库地址**：`https://axorbisinfinitive.github.io/Long2/maven/`。
