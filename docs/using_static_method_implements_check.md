## 如何使用 AP 静态方法实现检查器

在工作目录运行：

```sh
mvn -f ap/pom.xml clean install
```

之后 Maven 和 VS Code JLS 会自动进行检查。若未通过检查，Maven 会编译失败，而 VS Code JLS 会在 Problems 中提示问题。

具体地，`StaticMethodImplementsCheckProcessor` 会识别所有注解了 `@StaticMethodImplementsCheck` 的契约类型，然后收集契约类型直接声明的 `public static` 方法。将契约自身的类型替换为实现类类型后，对所有契约类型的实现类进行检查，对照实现类直接声明的静态方法。

符合契约即通过检查。
