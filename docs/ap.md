# Annotation Processor 使用说明

本项目必须使用 Annotation Processor 进行编译。

执行命令：

```bash
mvn -f ap/pom.xml clean install
```

之后 Maven 和 Java Language Server 会自动检查。

## Annotation Processor 依赖配置

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
