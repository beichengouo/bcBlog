# 密码重置工具

管理员忘记密码时，用本工具把数据库里指定账号的密码**重置为新密码**。

> 说明：密码使用 BCrypt 单向哈希存储，无法从密文还原明文。本工具是“重置为新密码”，而不是解密旧密码，这样更安全。

## 构建

在 `tools/password-reset/` 目录执行（需要已配置 Maven 和 JDK 8）：

```
mvn package
```

生成 `target/bcblog-password-reset.jar`。

## 使用

```
java -jar target/bcblog-password-reset.jar
```

按提示输入：数据库地址、端口、库名、账号、密码，以及要重置的用户名和新密码。
