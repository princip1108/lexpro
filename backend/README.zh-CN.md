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
LEXPRO_LOGIN_RATE_LIMIT_ENABLED=true
LEXPRO_LOGIN_USERNAME_MAX_ATTEMPTS=5
LEXPRO_LOGIN_ADDRESS_MAX_ATTEMPTS=100
LEXPRO_LOGIN_RATE_LIMIT_WINDOW=PT5M
LEXPRO_LOGIN_BLOCK_DURATION=PT15M
LEXPRO_LOGIN_MAX_TRACKED_ENTRIES=10000
LEXPRO_DOSSIER_LOCAL_ROOT=./storage
LEXPRO_DOSSIER_MAX_FILE_SIZE=25MB
LEXPRO_DOSSIER_MAX_REQUEST_SIZE=26MB
LEXPRO_PROCESSING_CORE_THREADS=2
LEXPRO_PROCESSING_MAX_THREADS=4
LEXPRO_PROCESSING_QUEUE_CAPACITY=50
LEXPRO_PROCESSING_MAX_EXTRACTED_CHARS=2000000
LEXPRO_PROCESSING_STALE_AFTER=PT30M
LEXPRO_AI_ENABLED=false
LEXPRO_AI_BASE_URL=https://api.deepseek.com
LEXPRO_AI_API_KEY=<你的本机 API Key>
LEXPRO_AI_MODEL=deepseek-v4-flash
LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false
LEXPRO_AI_CONNECT_TIMEOUT=PT5S
LEXPRO_AI_READ_TIMEOUT=PT45S
LEXPRO_AI_MAX_INPUT_CHARS=60000
LEXPRO_AI_MAX_OUTPUT_TOKENS=4096
LEXPRO_REPORT_PDF_FONT_PATH=C:\Windows\Fonts\simhei.ttf
LEXPRO_RETRIEVAL_ENABLED=false
LEXPRO_RETRIEVAL_BASE_URL=http://127.0.0.1:8010
LEXPRO_RETRIEVAL_CONNECT_TIMEOUT=PT3S
LEXPRO_RETRIEVAL_READ_TIMEOUT=PT20S
LEXPRO_RETRIEVAL_RETRIES=1
LEXPRO_RETRIEVAL_MODEL_NAME=BAAI/bge-m3
LEXPRO_RETRIEVAL_MODEL_VERSION=main
LEXPRO_MCP_ENABLED=false
LEXPRO_MCP_ENDPOINT=/mcp
LEXPRO_MCP_REQUEST_TIMEOUT=PT55S
LEXPRO_MCP_MAX_ITEMS=20
LEXPRO_MCP_MAX_OUTPUT_CHARS=50000
LEXPRO_MCP_CLIENT_REGISTRY_PATH=<客户端摘要注册表的绝对路径>
LEXPRO_MCP_TOKEN_RELOAD_INTERVAL=PT30S
LEXPRO_MCP_MAX_CONCURRENT_REQUESTS=8
LEXPRO_MCP_MAX_CONCURRENT_PER_CLIENT=2
LEXPRO_MCP_RATE_LIMIT_PER_MINUTE=60
```

不能把真实数据库密码、JWT 密钥、管理员密码或 AI API Key 写入 `application.properties`，也不能提交到 Git。修改 JWT 密钥会让所有已签发的 Access Token 失效。启用 AI 适配器和允许案件文本外发仍是两个独立开关。发送给 DeepSeek 已获批准，但每个需要外发的部署都必须显式设置 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`；默认值继续为 `false`。

登录限流默认启用，并且只在单个后端进程内生效。生产环境必须保持启用；如果以后部署多个后端实例，必须先批准并实现共享限流，再分发流量。

MCP 速率和并发限制同样只在单个后端进程内生效。多实例 MCP 部署必须在反向代理或 API 网关统一执行客户端总配额。客户端注册表必须放在仓库和容器镜像之外，项目中只记录严格 JSON 格式及其环境变量路径。

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
GET /api/v1/cases?page=1&size=20
GET /api/v1/cases/{caseId}
POST /api/v1/cases
PUT /api/v1/cases/{caseId}
GET /api/v1/cases/{caseId}/parties
POST /api/v1/cases/{caseId}/parties
PUT /api/v1/cases/{caseId}/parties/{partyId}
GET /api/v1/cases/{caseId}/assignments
POST /api/v1/cases/{caseId}/assignments
POST /api/v1/cases/{caseId}/assignments/{assignmentId}/end
GET /api/v1/cases/{caseId}/dossier/folders
POST /api/v1/cases/{caseId}/dossier/folders
GET /api/v1/cases/{caseId}/dossier/tags
POST /api/v1/cases/{caseId}/dossier/tags
GET /api/v1/cases/{caseId}/dossier/files
POST /api/v1/cases/{caseId}/dossier/files
PUT /api/v1/cases/{caseId}/dossier/files/{dossierId}
DELETE /api/v1/cases/{caseId}/dossier/files/{dossierId}
POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/restore
GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/content
POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-jobs
GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-results
GET /api/v1/cases/{caseId}/documents/{docId}
POST /api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs
GET /api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs/{requestId}
GET /api/v1/cases/{caseId}/documents/{docId}/entity-results
GET /api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}
PUT /api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}/confirmation
```

只有健康检查、Swagger/OpenAPI 和登录接口公开。用户、组织、角色和权限接口都要求 `USER_MANAGE` 权限。
案件接口要求 `CASE_READ`、`CASE_WRITE` 或 `CASE_ASSIGN`，同时还会在服务层检查案件级访问权限。
卷宗查询和下载要求 `CASE_READ` 加案件可见性；卷宗修改要求 `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别。
解析结果查询要求 `CASE_READ`；启动或重试解析要求 `AI_EXECUTE` 加案件 `EDIT` 访问级别。
实体结果查询要求 `CASE_READ`；生成和确认要求 `AI_EXECUTE` 加案件 `EDIT` 访问级别。

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

## M3 案件流程限制

新建案件固定为 `PENDING` 状态；普通修改接口不改变案件状态。状态流转接口需要等状态流转矩阵确认后再实现。参与人接口只返回脱敏身份信息，暂不接收身份证原值；身份证加密、哈希、脱敏和查询策略确认后再继续补充。

## M4 开发环境存储

卷宗文件内容保存在 `LEXPRO_DOSSIER_LOCAL_ROOT`（默认 `./storage`）下，并使用随机内部对象键。API 元数据不返回对象键或本地路径。默认单文件上限为 25 MB；扩展名、MIME 和大小限制均可配置。完整规则和验收步骤见 `docs/zh-CN/M4_DOSSIER_MANAGEMENT.md`。

## M5 文档和智能处理

解析按版本异步执行。内置开发解析器支持状态为 `ACTIVE` 的 UTF-8 `.txt` 文件；其他类型在批准的解析器适配器加入前，会使用稳定的“供应商未配置”错误码失败。完成或失败后再次提交会创建新版本。详见 `docs/zh-CN/M5_DOCUMENT_PROCESSING.md`。

实体识别使用 OpenAI 兼容的 DeepSeek 适配器，同样异步执行。模型原始结果与第一次人工确认结果分开保存。自动化测试只使用本地模拟供应商；只有在批准后明确配置 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`，才允许真实外部传输。

法律要素识别复用同一个有界异步 AI 传输层，并要求证据引用与原文准确一致。案件摘要使用显式选择的成功解析结果，按摘要类型保留版本历史；生成失败不会替换最后一个当前摘要。两个流程都支持第一次人工确认，并在服务层执行案件访问控制。

## M6 案卡和报告

M6 已实现案卡/报告异步生成、报告模板版本、草稿乐观锁编辑、复核/定稿流转、规范化引用及 DOCX/PDF 导出。PDF 导出要求 `LEXPRO_REPORT_PDF_FONT_PATH` 指向可读取的中文 TTF。协议、权限和验收步骤见 `../docs/zh-CN/M6_CASE_CARDS_AND_REPORTS.md`。

## M7 典型案例推荐

M7 增加本地 Python 检索服务、幂等典型案例导入、结构化/词法/向量混合检索、推荐历史和收藏。Java 仍是授权和写入边界。V4 与 Python 只读数据库账号必须经过明确人工批准后再使用，详见 `../docs/zh-CN/M7_TYPICAL_CASE_RECOMMENDATION.md`。

## M8 MCP Server

M8 在 Spring Boot 进程内提供一个 Java MCP Server，通过 `POST /mcp` 提供无状态 Streamable HTTP，默认关闭。启用后，每个请求必须携带来自摘要注册表的独立客户端服务令牌，`/mcp` 不接受网页登录 JWT。工具白名单只包含法律要素识别、文书实体识别、案件摘要和不可执行的典型案例占位工具；前三个工具复用已校验的 DeepSeek-compatible 客户端并要求 `AI_EXECUTE`，占位工具固定返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`。不开放通用 SQL、Shell、文件系统或文件正文能力。生产验收仍需完成 HTTPS 部署和指定外部 MCP 客户端联调。

## 数据库检查预期

访问 `GET /api/health/database` 应返回数据库名称 `lexpro` 和业务表数量 `32`。该接口会有意排除 Flyway 的基础设施表 `flyway_schema_history`。
