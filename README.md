# Long2

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)

**Long2** 是一个 **Java** 库，提供不可变的数值类型。

## 快速开始

```java
import love.crysh.long2.ints.Int256;
import love.crysh.long2.MathCore;

Int256 a = Int256.of(1234);
Int256 b = Int256.of(5678);
Int256 result = a.multiply(b);
Int256 max = MathCore.max(a, b);
```

## 功能

基于软件实现不可变的数值类型，例如 [Int](src/main/java/love/crysh/long2/Int.java) 和 [Binary](src/main/java/love/crysh/long2/Binary.java)。

实现类继承了这两个接口，详见：

- [Int256.java](src/main/java/love/crysh/long2/ints/Int256.java) / [Int512.java](src/main/java/love/crysh/long2/ints/Int512.java) / [Int1024.java](src/main/java/love/crysh/long2/ints/Int1024.java)
- [Binary256.java](src/main/java/love/crysh/long2/binaries/binary256/Binary256.java) / [Binary640.java](src/main/java/love/crysh/long2/binaries/binary640/Binary640.java) / [Binary1280.java](src/main/java/love/crysh/long2/binaries/binary1280/Binary1280.java)

同时，在 [MathCore.java](src/main/java/love/crysh/long2/MathCore.java) 中，Long2 也实现了多个便捷的重载方法。

## 测试

关于 Annotation Processor，请见 [Annotation Processor 使用说明](docs/ap.md)。

```bash
mvn -f ap/pom.xml install
mvn test
mvn -Pnumeric-text test
mvn -Pnumeric-text-extreme '-DargLine=-Xmx2g' test
```

## 作为依赖

主库及 Annotation Processor 已从 v0.2.2 起发布到 Maven Central。

```xml
<dependency>
    <groupId>love.crysh.long2</groupId>
    <artifactId>Long2</artifactId>
    <version>0.2.2</version>
</dependency>
```
