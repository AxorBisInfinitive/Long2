# Long2

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)

[AP 使用说明](docs/ap.md)

**转发门面**：[MathCore.java](src/main/java/love/crysh/long2/MathCore.java)

**数值类型**：

- [Int256.java](src/main/java/love/crysh/long2/ints/Int256.java)
- [Int512.java](src/main/java/love/crysh/long2/ints/Int512.java)
- [Int1024.java](src/main/java/love/crysh/long2/ints/Int1024.java)
- [Binary256.java](src/main/java/love/crysh/long2/binaries/binary256/Binary256.java)
- [Binary640.java](src/main/java/love/crysh/long2/binaries/binary640/Binary640.java)
- [Binary1280.java](src/main/java/love/crysh/long2/binaries/binary1280/Binary1280.java)

## 测试

```bash
mvn -f ap/pom.xml install
mvn test
mvn -Pnumeric-text test
mvn -Pnumeric-text-extreme '-DargLine=-Xmx2g' test
```

## 作为依赖

主库及 AP 已从 v0.2.2 起发布到 Maven Central。

```xml
<dependency>
    <groupId>love.crysh.long2</groupId>
    <artifactId>Long2</artifactId>
    <version>0.2.2</version>
</dependency>
```
