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

```bash
mvn test
mvn -Pnumeric-text test
mvn -Pnumeric-text-extreme '-DargLine=-Xmx2g' test
```

## 作为依赖

**仓库地址**：`https://axorbisinfinitive.github.io/Long2/maven/`。
