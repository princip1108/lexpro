# LexPro 后端

## 技术栈

- Java 21
- Spring Boot 3.5.16
- Maven
- MyBatis-Plus 3.5.17
- PostgreSQL
- Flyway
- SpringDoc OpenAPI

Maven 工程位于 `backend/lexpro-backend`。

## 必需环境变量

本地 DBeaver 连接使用 `127.0.0.1:5432` 上的 `lexpro` 数据库。在 IntelliJ 运行配置中添加：

```text
LEXPRO_DB_PASSWORD=<你的 PostgreSQL 密码>
LEXPRO_JWT_SECRET=<至少 32 字节的唯一随机值>
```

可选覆盖项：

```text
LEXPRO_DB_URL=jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro
LEXPRO_DB_USERNAME=postgres
LEXPRO_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
LEXPRO_JWT_ISSUER=https://lexpro.local
LEXPRO_JWT_ACCESS_TOKEN_TTL=PT30M
```

不能把真实数据库密码、JWT 密钥或管理员密码写入 `application.properties`，也不能提交到 Git。修改 JWT 密钥会让所有已签发的 Access Token 失效。

## 首个开发管理员

管理员初始化默认关闭，并且只在 `app_user` 为空时创建账号。第一次本地运行时，可以暂时在 IntelliJ 运行配置中增加：

```text
LEXPRO_BOOTSTRAP_ADMIN_ENABLED=true
LEXPRO_BOOTSTRAP_ADMIN_USERNAME=admin
LEXPRO_BOOTSTRAP_ADMIN_PASSWORD=<本地强密码>
LEXPRO_BOOTSTRAP_ADMIN_REAL_NAME=System Administrator
LEXPRO_BOOTSTRAP_ORGANIZATION_CODE=LEXPRO
LEXPRO_BOOTSTRAP_ORGANIZATION_NAME=LexPro
```

密码至少 12 个字符，并且包含大小写字母、数字和特殊字符。启动一次并确认账号创建成功后，把 `LEXPRO_BOOTSTRAP_ADMIN_ENABLED` 改回 `false`。已有用户数据不会被覆盖。

## Flyway 安全说明

现有开发数据库已于 2026-07-28 登记为 Flyway 版本 3 基线。Flyway 仍默认关闭，保证每次迁移都经过明确决定：

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
```

保持 `LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false`。只有经过批准的验证或迁移才开启 Flyway，后续结构修改从 V4 开始。当前数据库包含 32 张业务表和基础设施表 `lexpro.flyway_schema_history`。

## 在中文版 IntelliJ IDEA 中运行

1. 将 `backend/lexpro-backend` 作为 Maven 工程打开。
2. 项目 SDK 选择 Java 21。
3. 打开 **运行 -> 编辑配置**。
4. 选择 `LexproBackendApplication`。
5. 在 **环境变量** 中添加 `LEXPRO_DB_PASSWORD` 和 `LEXPRO_JWT_SECRET`。
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
POST /api/v1/auth/login
GET /api/v1/auth/me
POST /api/v1/auth/logout
GET /api/v1/users?page=1&size=20
GET /api/v1/users/{userId}
POST /api/v1/users
PATCH /api/v1/users/{userId}/status
PUT /api/v1/users/{userId}/password
GET /api/v1/organizations/tree
GET /api/v1/roles
GET /api/v1/permissions
```

只有健康检查、Swagger/OpenAPI 和登录接口公开。用户、组织、角色和权限接口都要求 `USER_MANAGE` 权限。

Access Token 默认 30 分钟过期，不提供 Refresh Token。退出接口会写审计，前端会丢弃 Token；服务端不维护 Token 黑名单。

本地接口文档：

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

## 公共 API 基础

- 错误响应使用 `application/problem+json`，包含稳定的 `errorCode`、`requestId` 和可选的 `fieldErrors`。
- 分页从第 1 页开始，默认每页 20 条，最大 100 条。
- 每个响应都包含 `X-Request-Id`；格式安全的客户端请求 ID 会被保留。
- 请求完成日志包含方法、路径、状态码、耗时和请求 ID。
- `AuditService` 把安全敏感或改变业务状态的事件写入现有 `operation_log` 表。
- CORS 仅对配置的 Vue 开发来源和 `/api/**` 路径生效。

## 数据库检查预期

访问 `GET /api/health/database` 应返回数据库名称 `lexpro` 和业务表数量 `32`。该接口会有意排除 Flyway 的基础设施表 `flyway_schema_history`。
