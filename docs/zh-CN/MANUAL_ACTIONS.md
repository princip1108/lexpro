# LexPro 人工操作与验收清单

[English](../MANUAL_ACTIONS.md)

以下事项需要真实本地环境、业务决定或显式审批，不能由 AI 自动代替。所有功能验收使用虚构或脱敏数据，不在文档、截图、Git 或聊天中保存密码、JWT、API Key 和真实案件内容。

## 当前状态

- 2026-08-01 本机验收时，PostgreSQL `5432`、Spring Boot `8080` 和 Vue `5173` 已通过启动门禁；检索服务 `8010` 保持关闭。
- 现有开发数据库已经是 V3 基线，包含 32 张业务表。V1/V2/V3 不得重跑。
- 管理员核心流程、M9 工作台、M4 非删除文件流程和 M5 本地文本解析已使用虚构数据通过真实数据库 HTTP/UI 验收。
- M9 核心功能使用 V3 的 `knowledge_content`、`work_task`，不依赖 V4；只有 M7 向量检索需要 V4。
- 当前工作树包含尚未提交的 M3-M9 代码和文档。人工检查差异后，应先建立一个可回退的 Git 提交，再进行迁移或部署操作。

## 验收进度（2026-08-01）

- **PASS：**健康检查、数据库/OpenAPI、认证拒绝和 CORS、管理员案件/知识/任务 CRUD、首页统计一致性、任务关联约束、卷宗目录/上传/整理/下载和本地 UTF-8 解析。
- **PARTIAL：**M4 软删除/恢复和审计日志查询仍需人工完成；M6 当前只通过模板草稿创建和外部数据保护检查。
- **BLOCKED：**`USER_A`/`USER_B` 越权边界需要先准备两个普通账号；M5/M6 外部 AI 正向流程需要明确批准数据外发；M7 迁移/检索和 M8 真实客户端仍需单独批准。
- 本轮验收发现并修复了普通 MyBatis 注解 SQL 中错误保留 XML 转义的问题；重启后已在 PostgreSQL 上复测首页和任务创建/详情。

## 一、开始验收前必须完成

### 1. 后端运行配置

在中文版 IntelliJ IDEA 的 **运行 -> 编辑配置 -> LexproBackendApplication -> 环境变量** 中确认：

```text
LEXPRO_DB_PASSWORD=<本机 PostgreSQL 密码>
LEXPRO_JWT_SECRET=<至少 32 字节的本机随机值>
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false
LEXPRO_RETRIEVAL_ENABLED=false
LEXPRO_MCP_ENABLED=false
LEXPRO_REPORT_PDF_FONT_PATH=C:\Windows\Fonts\simhei.ttf
```

真实 `LEXPRO_AI_API_KEY` 只放 IntelliJ 环境变量。不要把 `.env.example` 改成真实配置。

### 2. 测试账号和测试数据

至少准备三个账号：

| 账号 | 用途 |
|---|---|
| `ADMIN` | 管理用户、内容、报告和全部功能 |
| `USER_A` | 被分配案件，验证正常访问 |
| `USER_B` | 不分配同一案件，验证越权访问被拒绝 |

如果 `app_user` 为空，只临时开启一次 `LEXPRO_BOOTSTRAP_ADMIN_ENABLED=true` 创建管理员；成功后立即改回 `false` 并重启。测试案件、人员、文件和 AI 文本必须全部为虚构数据。

### 3. 建立回退点

1. 检查 `git status` 和 `git diff`，确认没有密码、Token、真实 `.env`、`storage`、`target`、`dist` 或真实案件文件。
2. 人工确认当前 M3-M9 差异后创建 Git 提交，作为验收前代码回退点。
3. 在执行 V4 或部署前，使用 `D:\SQL\PosrgreSQL\18\bin\pg_dump.exe` 对 `lexpro` 做自定义格式备份，保存到被 Git 忽略的 `D:\desktop\soph\lexpro\backups`。
4. 备份完成后记录文件名、时间和大小；不要在记录中写数据库密码。

## 二、启动顺序与基础检查

1. 确认 PostgreSQL 服务 `postgresql-x64-18` 正常。
2. 从 IntelliJ 启动 `LexproBackendApplication`，确认日志没有迁移、数据库认证或端口冲突错误。
3. 依次访问：
   - `http://127.0.0.1:8080/api/health`：应返回 `200`。
   - `http://127.0.0.1:8080/api/health/database`：应返回 `200`、数据库 `lexpro`、业务表数量 `32`。
   - `http://127.0.0.1:8080/swagger-ui.html`：应能打开接口文档。
4. 在项目根目录运行 `D:\npm-global\node\npm.cmd run dev`，打开 `http://127.0.0.1:5173`。
5. 此阶段只启动 Java 和 Vue。Python 检索服务、MCP 和外部 AI 都保持关闭，先验收不依赖它们的主流程。

任一健康检查失败时先停止，不继续后面的业务验收。

## 三、分阶段验收流程

### A. 认证、用户、案件和 M9 工作台

1. 使用管理员登录，确认错误密码返回 `401`，正确密码返回 Token 和当前用户权限，但不返回密码哈希。
2. 创建 `USER_A`、`USER_B`，检查禁用账号不能登录，重新启用后可以登录。
3. 创建一个虚构案件，把 `USER_A` 分配为 `EDIT` 或 `MANAGE`，不向 `USER_B` 分配。
4. `USER_A` 应能查看案件；`USER_B` 直接请求同一案件 ID 应得到 `404`，且响应不能泄露案件字段。
5. 管理员新增知识内容：初始状态必须为 `DRAFT`。管理员能看到；无 `CONTENT_MANAGE` 的普通用户不能看到该草稿。
6. 管理员分别创建一个关联案件的任务和一个关联知识内容的任务：初始状态必须为 `PENDING`，负责人必须是创建人。
7. 用 Swagger 提交同时包含 `caseId` 和 `contentId` 的任务，应返回 `400 WORK_TASK_SUBJECT_INVALID`。
8. 打开首页，核对可见案件数量、案件分类、最近案件、当前用户任务数量和紧急任务与列表一致。
9. 查询 `operation_log`，确认知识和任务新增/修改产生相应审计记录。

当前不验收案件、知识和任务状态按钮，因为其流转矩阵尚未批准。普通用户读取 `PUBLISHED` 知识的正向场景也需要已有已发布数据或流转接口后再验收；不得为验收直接修改数据库状态。

### B. M4 卷宗管理

按 [M4 卷宗管理](M4_DOSSIER_MANAGEMENT.md) 验收：

1. 使用虚构案件创建目录和标签，上传一个 UTF-8 `.txt` 文件。
2. 验证列表、重命名、移动、标签替换、下载、软删除和恢复。
3. 响应中不得出现 `fileUrl`、对象键或本地磁盘路径。
4. `USER_B` 访问未授权案件文件应返回 `404`。
5. 可选将 `LEXPRO_DOSSIER_LOCAL_ROOT` 指向空间充足的开发机绝对目录后重启后端。

### C. M5 文档和 AI 处理

按 [M5 文档处理](M5_DOCUMENT_PROCESSING.md) 验收：

1. 先保持 AI 关闭，对上传的 UTF-8 `.txt` 启动解析，轮询到成功，检查版本和解析正文。
2. 仅使用虚构文本时，临时设置 `LEXPRO_AI_ENABLED=true`、`LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`，确认 DeepSeek 地址、模型和 API Key 后重启。
3. 依次验收实体识别、法律要素、案件摘要的异步状态、结果、来源引用和第一次人工确认。
4. 结束外部 AI 验收后，将 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA` 改回 `false` 并重启。
5. PDF/Office 解析仍未接入正式解析器，不作为当前通过条件。

### D. M6 案卡、报告和导出

按 [M6 案卡和报告](M6_CASE_CARDS_AND_REPORTS.md) 使用虚构数据验收：

1. 创建并启用报告模板，确认同编码旧版本自动禁用。
2. 生成案卡，核对字段来源、原文和人工确认。
3. 生成报告，完成草稿编辑、送审、退回、再次送审和定稿。
4. 导出 DOCX/PDF，检查中文字体、分页和非定稿草稿标记。
5. 定稿报告必须不可继续编辑。

### E. M7 典型案例检索（需要单独批准 V4）

M7 不阻塞 A-D 和 M9 验收。要验收 M7 时：

1. 先查询 `lexpro.flyway_schema_history`，确认 V4 是否已经成功执行。
2. 如果 V4 未执行，先完成数据库备份，再明确批准执行 `V4__configure_typical_case_vectors.sql`；不要重跑 V1/V2/V3。
3. 创建只读检索数据库账号，并配置 `LEXPRO_RETRIEVAL_DATABASE_URL`。Java 仍使用原业务账号写入。
4. 启动 `backend/retrieval-service`，确认 `http://127.0.0.1:8010/health` 返回成功。
5. 设置 `LEXPRO_RETRIEVAL_ENABLED=true` 并重启 Java。
6. 导入少量虚构典型案例，检查幂等导入、全文/向量混合检索、推荐理由、推荐历史和收藏。
7. 停止 Python 服务后再请求一次，确认出现明确降级或稳定错误，不得绕过 Java 授权。

### F. M8 MCP 真实客户端

1. 先选定一个支持 Streamable HTTP 和 Bearer Header 的 MCP 客户端。
2. 为虚构测试客户端生成独立高熵令牌，在本地注册表中只保存 SHA-256 摘要，并设置 `LEXPRO_MCP_CLIENT_REGISTRY_PATH`。
3. 临时设置 `LEXPRO_MCP_ENABLED=true` 并重启 Java。
4. 客户端连接 `http://127.0.0.1:8080/mcp`，使用测试客户端的 MCP 服务令牌，不使用 LexPro 登录 Token。
5. 验收四工具发现、前三个 DeepSeek 工具的虚构文本调用、权限拒绝、输入/输出限制，以及典型案例固定返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`。
6. 在 `operation_log` 中确认 `MCP_TOOL_CALLED` 和 `clientId`，且审计详情不包含请求正文、令牌或令牌摘要。
7. 验收结束后把 `LEXPRO_MCP_ENABLED` 改回 `false`；生产暴露必须使用 HTTPS 和独立生产注册表。

## 四、每个阶段的通过标准

每个阶段只记录以下证据，不另写重复测试报告：

- 使用的账号和角色，不记录密码或 Token。
- 请求路径、实际 HTTP 状态、稳定 `errorCode` 和 `X-Request-Id`。
- 一张必要页面截图或导出样例；必须使用虚构数据。
- 对应 `operation_log` 的操作类型和时间。
- 结果标记为 `PASS`、`FAIL` 或 `BLOCKED`；失败时记录复现步骤。

出现 `500`、敏感字段泄露、越权读取、审计缺失或数据库约束错误时，该阶段不能通过，应停止后续关联验收并修复。

## 五、仍需项目负责人决定

### 阻塞功能补全

- 案件状态流转矩阵、退回/重开规则和允许角色。
- 知识内容与人工任务的状态流转矩阵和允许角色。
- 身份证件号码的加密算法、密钥来源、哈希查询、脱敏格式和修改规则。
- 案件编号的生成规则和最终唯一性要求。

### 生产环境前决定

- MinIO/对象存储布局、文件保留和永久清理策略、病毒扫描要求。
- PDF/Office 正式解析器或 MinerU 提供方、超时和并发限制。
- 设置 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true` 时，真实案件数据发送给 DeepSeek 已获批准；生产 AI 超时、并发和降级策略仍待最终确定。
- 指定 MCP 客户端、TLS/网络暴露方式和首批生产客户端权限/配额。
- 部署目标、域名/TLS、日志保留、监控告警和密钥管理方式。

## 六、上线前交付验收

本地功能验收通过后，再执行以下工作；这些不是当前编码完成的证明：

1. 在隔离环境建立基础性能数据：登录、案件分页、仪表盘、文件列表和知识/任务列表的延迟及并发错误率。
2. 使用 `pg_restore.exe` 把最新备份恢复到可丢弃数据库，验证 32 张业务表、Flyway 版本和关键记录数量；不要覆盖开发库。
3. 完成部署环境配置、TLS、最小权限数据库账号、目录权限和日志脱敏检查。
4. 在部署环境重复健康检查和 A 阶段核心冒烟流程。
5. 人工确认 Git 差异、构建产物和验收结果后，再决定发布；部署和发布必须单独批准。
