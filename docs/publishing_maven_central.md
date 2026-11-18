# 发布到 Maven Central

主库和注解处理器使用相同的发布版本。当前准备发布的坐标为 `love.crysh.long2:Long2:0.2.2` 和 `love.crysh.long2:int-contract-processor:0.2.2`。

## 首次配置

在 Central Portal 注册并验证 `love.crysh` 命名空间。使用 `crysh.love` 根域名的 DNS TXT 记录验证所有权。

创建 Central Portal User Token，并准备 PGP 签名密钥。将对应公钥发布到 Central 支持的公钥服务器。具体要求见 [Central 发布要求](https://central.sonatype.org/publish/requirements/)。

在 GitHub 仓库的 Actions Secrets 中配置以下值。

| Secret | 内容 |
| --- | --- |
| `CENTRAL_USERNAME` | Portal User Token 的用户名 |
| `CENTRAL_PASSWORD` | Portal User Token 的密码 |
| `MAVEN_GPG_KEY` | 通过 `gpg --armor --export-secret-keys <KEY_ID>` 导出的指定签名私钥 |
| `MAVEN_GPG_PASSPHRASE` | 签名私钥口令，密钥有口令时填写 |

签名使用 Maven GPG 插件的 Bouncy Castle signer，通过环境变量读取密钥和口令。

## 验证发布制品

使用 Java 25。以下命令生成主 JAR、源码包、Javadoc 包并签名，同时将处理器安装到本地供主库构建使用。

```sh
mvn -B -ntp -Prelease -f ap/pom.xml clean install
mvn -B -ntp -Prelease clean verify
```

本地尚未配置签名密钥时，可添加 `-Dgpg.skip=true` 检查编译、测试和文档打包。正式上传需要完整签名。

配置签名密钥后，添加 `-DskipUpload=true` 执行 `deploy`，可在本地生成并检查 Central 上传包。两个项目的上传包分别位于 `target/central-publishing/central-bundle.zip` 和 `ap/target/central-publishing/central-bundle.zip`。

## 上传和发布

两份 POM 的版本需要与标签一致。更新版本时，同时修改根目录和 `ap/` 的 POM。

发布正式 GitHub Release，或手动运行 `Publish to Maven Central` 工作流并填写标签，例如 `v0.2.2`。工作流检出该标签，先构建、安装并上传处理器，再构建、测试并上传主库。

每个制品对应一项 Portal deployment。工作流等待两项 deployment 验证完成。打开 Central Portal，确认两项均已通过验证，然后先发布处理器，再发布主库。

如果处理器已上传并通过验证，而主库步骤失败，可先修复失败原因。重新上传前，在 Portal 删除需要重新上传的未发布 deployment。已经正式发布的坐标无法覆盖，需要使用新版本。

本地上传时，在 Maven `settings.xml` 中配置 ID 为 `central` 的 server，填写 User Token 的用户名和密码，并设置签名环境变量后执行以下命令。

```sh
mvn -B -ntp -Prelease -f ap/pom.xml clean deploy
mvn -B -ntp -Prelease clean deploy
```

上传插件和凭据配置见 [Central Maven 插件文档](https://central.sonatype.org/publish/publish-portal-maven/)。

发布完成后，在独立 Maven 本地仓库中下载两个坐标，并编译包含 Long2 调用及契约检查的示例，确认 Central 上的制品可用，再更新 README 中的首次发布状态。

## 历史制品

GitHub Pages 已关闭，`maven-repository` 分支保留旧制品。当前发布流程使用 Maven Central。
