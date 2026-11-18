## 如何使用 AP 静态方法实现检查器

在工作目录运行：

```sh
mvn -f ap/pom.xml clean install
```

之后 Maven 和 VS Code JLS 会自动进行检查。若未通过检查，Maven 会编译失败，而 VS Code JLS 会在 Problems 中提示问题。

主库和处理器已发布到 Maven Central，使用配套版本 `0.2.2`。外部项目可以在依赖 Long2 的同时配置以下编译插件，以检查自己的契约实现。

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.15.0</version>
    <configuration>
        <release>25</release>
        <proc>full</proc>
        <annotationProcessorPaths>
            <path>
                <groupId>love.crysh.long2</groupId>
                <artifactId>int-contract-processor</artifactId>
                <version>0.2.2</version>
            </path>
        </annotationProcessorPaths>
        <annotationProcessors>
            <annotationProcessor>love.crysh.long2.processor.StaticMethodImplementsCheckProcessor</annotationProcessor>
        </annotationProcessors>
    </configuration>
</plugin>
```

处理器自身以 Java 17 为编译目标，使用 Java 25 编译的 Long2 类型时需要 Java 25。

具体地，`StaticMethodImplementsCheckProcessor` 会识别所有注解了 `@StaticMethodImplementsCheck` 的契约类型，然后收集契约类型直接声明的 `public static` 方法。将契约自身的类型替换为实现类类型后，对所有契约类型的实现类进行检查，对照实现类直接声明的静态方法。

符合契约即通过检查。
