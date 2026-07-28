# LexPro 后端

## 技术栈

- Java 21
- Spring Boot 3.5.16
- Maven
- MyBatis-Plus 3.5.17
- PostgreSQL

Maven 工程位于 `backend/lexpro-backend`。

## 必需环境变量

本地 DBeaver 连接使用 `127.0.0.1:5432` 上的 `lexpro` 数据库。在 IntelliJ 运行配置中添加：

```text
LEXPRO_DB_PASSWORD=<你的 PostgreSQL 密码>
```

可选覆盖项：

```text
LEXPRO_DB_URL=jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro
LEXPRO_DB_USERNAME=postgres
```

不能把真实密码写入 `application.properties` 或提交到 Git。

## Flyway 安全说明

现有 V3 开发数据库尚未经过批准的基线登记，因此 Flyway 默认关闭：

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
```

在现有数据库完成备份并通过32张业务表验证前，不能开启baseline开关。`lexpro.flyway_schema_history` 成功登记版本3后，必须立即删除一次性baseline开关。后续结构修改从V4开始。

## 在中文版 IntelliJ IDEA 中运行

1. 将 `backend/lexpro-backend` 作为 Maven 工程打开。
2. 项目 SDK 选择 Java 21。
3. 打开 **运行 -> 编辑配置**。
4. 选择 `LexproBackendApplication`。
5. 在 **环境变量** 中添加 `LEXPRO_DB_PASSWORD`。
6. 运行 `com.lexpro.lexprobackend.LexproBackendApplication`。

## 命令行运行

在 `JAVA_HOME` 已指向 Java 21 时执行：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## 当前接口

```http
GET /api/health
GET /api/health/database
GET /api/v1/users
```

用户列表目前暂未鉴权，完成认证里程碑后会增加权限保护。

## 数据库检查预期

访问 `GET /api/health/database` 应返回数据库名称 `lexpro` 和业务表数量 `32`。该接口会有意排除 Flyway 的基础设施表 `flyway_schema_history`。
