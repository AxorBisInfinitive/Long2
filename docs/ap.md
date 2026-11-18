# AP 使用说明

在工作目录运行：

```sh
mvn -f ap/pom.xml clean install
```

之后 Maven 和 Java Language Server 会自动进行检查。若未通过检查，Maven 会编译失败，而 Java Language Server 会在 Problems 中提示问题。

## AP 依赖配置

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
