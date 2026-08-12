# LexPro 功能验收清单

> 目的：启动前后端后，按编号逐项验证功能是否真的可用。  
> 记录方式：每项填写 `PASS`、`FAIL` 或 `BLOCKED`，并记录必要的 ID、HTTP 状态、`errorCode` 和时间。  
> 数据要求：只使用虚构数据，建议所有名称使用 `ACCEPTANCE_TEST_` 前缀。
> 如果你想按固定顺序手工验收，先看 [ACCEPTANCE_RUNBOOK.md](./ACCEPTANCE_RUNBOOK.md)。

## 0. 这份清单和历史测试的关系

这不是一份“所有项目都已经通过”的测试报告，而是一份完整的最终验收清单。之前的开发验收只覆盖了其中一部分，具体情况如下：

### 之前已经做过真实 HTTP/UI 验收的项目

- `F-01` 健康检查、数据库健康检查和 Swagger/OpenAPI。
- `F-03` 未登录访问、错误 Token、认证失败和前端 CORS。
- `F-04` 管理员登录、当前用户和退出登录。
- `F-07` 案件列表、创建、详情、更新。
- `F-08/F-09` 虚构普通用户的分案、授权、撤权和案件级越权边界。
- `F-10` 卷宗目录、上传、列表、下载等非破坏流程。
- `F-13` 本地 UTF-8 文本解析。
- `F-19` 首页统计、知识内容、工作任务和任务关联约束。
- `F-20/F-21` M7 典型案例导入幂等、列表/详情、推荐、历史、收藏和敏感字段过滤。
- `F-22` MCP 的 HTTP 协议、工具发现、允许/拒绝访问、输出限制、审计和错误序列化。
- `F-23` 前端登录、Dashboard、待办案件、知识内容和待办任务真实 API 对接。

### 之前只部分验证，仍建议你按清单补测的项目

- `F-05` 登录限流：有自动化测试，但没有作为生产验收完整地连续操作并记录 `429`。
- `F-06` 用户禁用、启用和密码重置：代码和部分管理员流程已测，建议用你当前数据库中的真实测试账号再走一遍。
- `F-11/F-12` 卷宗软删除、恢复和异常文件：代码测试覆盖较多，完整人工生命周期和异常文件组合仍应补测。
- `F-14` 实体、法律要素和摘要：本地模拟客户端及服务测试已完成，真实页面和完整异步确认流程没有全部走完。
- `F-16/F-17/F-18` 报告模板、案卡、报告复核/退回/定稿和 DOCX/PDF 导出：已验证部分创建和保护逻辑，完整人工流程需要补测。
- `F-25/F-26/F-27`：重要边界有自动化或专项验证，但没有对每一个接口逐项人工检查。

### 之前尚未完成或必须满足条件后才能测的项目

- `F-15` DeepSeek 正向调用：因为 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false`，之前没有发送案件文本到外部服务。
- `F-22` 真实 MCP 客户端验收：之前使用 HTTP 协议级测试，尚未确定具体 MCP 客户端并完成客户端连接验收。
- 生产规模性能、备份恢复、生产对象存储、TLS/反向代理和部署验证：之前没有在生产环境完成。

因此，你现在可以把“之前已经通过”的项目作为回归复查，把“部分验证”和“尚未完成”项目作为本轮重点。最终是否能交付，应以本清单中本轮实际记录的结果为准，不能直接把历史自动化测试结果当成全部人工验收结果。

### 2026-08-11 本轮实测结果

本轮使用开发数据库中的虚构验收账号和 `ACCEPTANCE_TEST_` 数据完成了以下接口测试：

| 编号 | 实测结果 | 关键证据 |
|---|---|---|
| F-01 | PASS | `/api/health` 和 `/api/health/database` 均为 `200`；业务表数量 `32` |
| F-05 | PASS | 同一虚构用户名错误登录 5 次均为 `401`，第 6 次为 `429` |
| F-06 | PASS | 创建用户 `userId=4`；禁用后登录 `401`；启用后登录 `200`；新密码登录 `200` |
| F-11 | PASS | 案件 `4` 的文件 `3`：删除 `204`、删除后下载 `410`、恢复 `200`、恢复后下载 `200` |
| F-12 | PASS | 空文件 `400 DOSSIER_FILE_EMPTY`；不允许扩展名 `415 DOSSIER_EXTENSION_NOT_ALLOWED`；超限文件 `413 DOSSIER_FILE_TOO_LARGE` |
| F-13 | PASS | 文件 `3` 解析任务 `202`，解析结果为 `SUCCESS`，`docId=3` |
| F-14 | BLOCKED | 外部 AI 开关关闭，实体/法律要素返回 `503 AI_DATA_EXPORT_DISABLED`，摘要同样被安全开关阻止 |
| F-16 | PASS | 报告模板 `templateId=4` 创建 `201`，启用 `200 ACTIVE`，停用 `200 DISABLED` |
| F-17/F-18 | BLOCKED | 案卡和报告生成在外部 AI 关闭时返回 `503 AI_DATA_EXPORT_DISABLED`，未伪造成功结果 |
| F-25 | PASS | 缺少案件必填字段返回 `400 VALIDATION_FAILED`；非法任务关联返回 `400 WORK_TASK_SUBJECT_INVALID` |
| F-26 | PASS | 普通用户访问用户管理返回 `403 ACCESS_DENIED`；未分配案件返回 `404 CASE_NOT_FOUND` |
| F-27 | PASS | 检查 5 个真实 JSON 响应，没有密码哈希、Token、原始 `embedding`、内部存储路径或 `stackTrace` 属性 |

本轮没有执行 DeepSeek 正向调用、报告完整复核/定稿/导出和真实 MCP 客户端连接，因为这些步骤仍需要外部数据发送批准或额外运行条件。

## 1. 验收规则

### 1.1 成功的判定

只有同时满足“HTTP 状态正确”和“返回数据/数据库结果正确”才算通过。例如：创建案件不能只看页面弹出成功提示，还必须使用返回的 `caseId` 再查询详情，确认记录确实存在。

通用规则：

- 查询成功通常为 `200`。
- 创建资源通常为 `201`，响应应包含新资源 ID。
- 异步任务创建通常为 `202`，响应应包含 `requestId`，之后必须轮询结果。
- 成功删除/退出/收藏操作可能返回 `204`，响应正文为空是正常的。
- 参数错误为 `400`，响应 `Content-Type` 应为 `application/problem+json`，并包含 `errorCode=VALIDATION_FAILED` 或具体业务错误码。
- 未登录或 Token 无效为 `401`；有登录身份但权限不足为 `403`；案件不可见通常按 `404` 返回，避免泄露案件是否存在。
- 错误响应不得包含密码哈希、JWT、API Key、服务器绝对路径、异常堆栈或数据库 SQL。

### 1.2 测试准备

1. 启动 PostgreSQL 服务和 Spring Boot 后端。
2. 确认后端配置：

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false
LEXPRO_RETRIEVAL_ENABLED=false
LEXPRO_MCP_ENABLED=false
```

3. 打开 `http://127.0.0.1:8080/swagger-ui.html`。
4. 至少准备：

| 测试账号 | 用途 |
|---|---|
| `ADMIN` | 管理用户、案件、知识、任务和报告 |
| `USER_A` | 被分配案件的普通用户 |
| `USER_B` | 未被分配案件，用于越权测试 |

5. 记录两个 ID：

```text
CASE_ID：本次创建的虚构案件 ID
DOSSIER_ID：本次上传的虚构 txt 文件 ID
```

后续步骤中的 `{caseId}` 和 `{dossierId}` 替换为实际值。

## 2. 基础设施和接口规范

### F-01 健康检查

**操作步骤**

1. 浏览器访问 `GET /api/health`。
2. 再访问 `GET /api/health/database`。

**成功标准**

- 两个接口均返回 `200`。
- 数据库接口显示数据库可连接，数据库名为 `lexpro`，业务表数量为 `32`。
- 不能把 `flyway_schema_history` 算入业务表数量。

### F-02 Swagger 和统一错误

**操作步骤**

1. 打开 Swagger UI，确认接口文档能加载。
2. 不填请求体调用一个需要 JSON 的 POST 接口，例如 `POST /api/v1/cases`。
3. 填入缺少必填字段的 JSON 再调用一次。

**成功标准**

- Swagger 页面可加载，能看到 `/api/v1` 接口。
- 错误状态为 `400`。
- 响应类型为 `application/problem+json`。
- JSON 中包含 `errorCode`、`detail`、`requestId`；字段错误时包含 `fieldErrors`。

### F-03 未登录、错误 Token 和 CORS

**操作步骤**

1. 不带 `Authorization` 调用 `GET /api/v1/auth/me`。
2. 使用随机字符串作为 Bearer Token 再调用一次。
3. 从 `http://127.0.0.1:5173` 打开前端并执行一次真实 API 请求。

**成功标准**

- 前两次均为 `401`，不能返回用户数据。
- 前端请求不被 CORS 拦截。
- 每次响应包含 `X-Request-Id`。

## 3. 登录、用户和权限

### F-04 管理员登录、当前用户和退出

**操作步骤**

1. `POST /api/v1/auth/login`，请求体：

```json
{"username":"ADMIN用户名","password":"ADMIN密码"}
```

2. 保存响应中的 `accessToken`，在 Swagger 点击 `Authorize`，填写 `Bearer <accessToken>`。
3. 调用 `GET /api/v1/auth/me`。
4. 调用 `POST /api/v1/auth/logout`。
5. 再调用一次 `GET /api/v1/auth/me`。

**成功标准**

- 登录返回 `200`，包含 `accessToken`、`tokenType`、`expiresAt` 和用户信息。
- 用户信息包含用户名、组织、角色和权限，不包含密码哈希。
- `/auth/me` 返回 `200`。
- 退出返回 `204`；客户端丢弃 Token 后再次访问应为 `401`。

### F-05 登录失败和限流

**操作步骤**

1. 使用正确用户名和错误密码登录一次。
2. 在限流窗口内连续使用错误密码尝试超过配置次数。

**成功标准**

- 单次错误密码为 `401`。
- 超过限制后为 `429`，`errorCode=LOGIN_RATE_LIMITED`，并有 `Retry-After` 响应头。
- 响应不说明用户名是否存在。

### F-06 用户管理

**操作步骤**

1. 管理员调用 `GET /api/v1/organizations/tree`、`GET /api/v1/roles`、`GET /api/v1/permissions`，取得 `organizationId` 和 `roleId`。
2. `POST /api/v1/users` 创建 `USER_A`：

```json
{
  "username":"ACCEPTANCE_TEST_USER_A",
  "password":"本地测试强密码",
  "realName":"验收用户A",
  "organizationId":1,
  "roleId":1
}
```

3. 用 `GET /api/v1/users` 和 `GET /api/v1/users/{userId}` 查询。
4. `PATCH /api/v1/users/{userId}/status`，请求体 `{"status":"DISABLED"}`。
5. 用该账号登录，确认失败；再改回 `ACTIVE` 并确认可以登录。
6. `PUT /api/v1/users/{userId}/password`，请求体 `{"newPassword":"新的本地测试强密码"}`，再用新密码登录。

**成功标准**

- 创建返回 `201` 和 `userId`。
- 查询返回 `200`，不包含密码哈希、明文密码或内部字段。
- 禁用/启用返回 `200`，禁用用户登录返回 `401`。
- 重置密码返回 `204`，旧密码失效，新密码有效。

## 4. 案件和案件级权限

### F-07 案件创建、查询和更新

**操作步骤**

1. 管理员调用 `POST /api/v1/cases`：

```json
{
  "caseName":"ACCEPTANCE_TEST_案件",
  "caseNo":"ACCEPTANCE_TEST_001",
  "caseType":"刑事",
  "caseCause":"诈骗",
  "caseSource":"验收数据",
  "currentStage":"立案",
  "acceptDate":"2026-08-10",
  "deadlineAt":"2026-12-31T18:00:00+08:00"
}
```

2. 保存返回的 `caseId`。
3. 调用 `GET /api/v1/cases/{caseId}`。
4. 调用 `GET /api/v1/cases?page=1&size=20`，再按关键字或状态筛选。
5. 用 `PUT /api/v1/cases/{caseId}` 修改案件名称或当前阶段。

**成功标准**

- 创建返回 `201`，响应包含 `caseId`，初始状态为 `PENDING`。
- 详情和列表均返回 `200`，数据与创建/更新内容一致。
- 响应包含期限或逾期计算字段，时间格式为带时区的 ISO-8601。
- 普通更新不能偷偷改变案件状态。

### F-08 当事人和分案

**操作步骤**

1. `GET /api/v1/cases/{caseId}/parties`，确认初始列表。
2. 按 Swagger 的 `CasePartyRequest` schema 新增一个虚构当事人，再查询并更新该当事人。
3. `GET /api/v1/cases/{caseId}/assignments`。
4. 使用 `POST /api/v1/cases/{caseId}/assignments` 把 `USER_A` 分配为允许的案件角色。
5. 再次查询分案，记录 `assignmentId`；调用 `POST /api/v1/cases/{caseId}/assignments/{assignmentId}/end` 结束分案。

**成功标准**

- 新增返回 `201`，更新和查询返回 `200`。
- 当前分案的 `endedAt` 为空；结束后 `endedAt` 有值，历史记录仍保留。
- 给禁用用户分案返回 `409`，`errorCode=CASE_ASSIGNEE_INACTIVE`。
- 重复当前分案返回 `409`，不能产生两条相同的当前分案。

### F-09 案件级越权

**操作步骤**

1. 用管理员把 `USER_A` 分配到 `{caseId}`。
2. 用 `USER_A` Token 调用案件列表和详情。
3. 不给 `USER_B` 分配该案件，使用 `USER_B` Token 调用同一个详情、当事人、卷宗和推荐接口。

**成功标准**

- `USER_A` 能看到被分配案件，并且只能执行其角色允许的操作。
- `USER_B` 得到 `404` 或受控拒绝，响应不包含案件名称、当事人或文件信息。
- 前端隐藏菜单不算通过，必须直接调用后端接口验证。

## 5. 卷宗文件

### F-10 目录、标签、上传和下载

**操作步骤**

1. 创建文件夹：`POST /api/v1/cases/{caseId}/dossier/folders`。
2. 创建标签：`POST /api/v1/cases/{caseId}/dossier/tags`。
3. 通过 Swagger 的 multipart 表单上传一个 UTF-8 文件 `ACCEPTANCE_TEST_document.txt`，字段名必须为 `file`。
4. 保存返回的 `dossierId`，调用文件列表和下载接口：

```text
GET /api/v1/cases/{caseId}/dossier/files
GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/content
```

**成功标准**

- 文件夹、标签、上传返回 `201`；列表返回 `200`。
- 上传响应包含 `dossierId`、文件名、大小、哈希和元数据，但不包含本地绝对存储路径。
- 下载返回 `200`，`Content-Disposition` 有文件名，下载内容与上传内容一致。

### F-11 软删除和恢复

**操作步骤**

1. 调用 `DELETE /api/v1/cases/{caseId}/dossier/files/{dossierId}`。
2. 不带 `includeDeleted` 查询文件列表。
3. 带 `includeDeleted=true` 查询文件列表。
4. 调用 `POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/restore`。
5. 再次列表和下载。

**成功标准**

- 删除返回 `204`。
- 默认列表不显示已删除文件；`includeDeleted=true` 可以看到删除状态。
- 恢复返回 `200`，文件重新出现在默认列表并且可以下载。
- 已删除文件直接下载应返回 `410`，`errorCode=DOSSIER_FILE_DELETED`。

### F-12 文件异常

**操作步骤**

1. 上传空文件。
2. 上传不允许的扩展名或扩展名与内容类型不匹配的文件。
3. 上传超过 25 MB 的文件。

**成功标准**

- 空文件返回 `400`，扩展名/类型问题返回 `400` 或 `415`。
- 超过限制返回 `413`，`errorCode=DOSSIER_FILE_TOO_LARGE`。
- 失败后数据库不产生可下载的脏文件记录。

## 6. 文档解析和 AI 处理

> 这一组接口是异步的。创建任务返回 `202` 不等于处理成功，必须继续查询结果状态。

### F-13 UTF-8 文本解析

**操作步骤**

1. 先完成 F-10，取得 `{dossierId}`。
2. 调用 `POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-jobs`，请求体可为空或 `{}`。
3. 记录返回的 `docId`，调用 `GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-results`，直到 `parseStatus=SUCCESS` 或 `FAILED`。
4. 成功后调用 `GET /api/v1/cases/{caseId}/documents/{docId}`。

**成功标准**

- 创建任务返回 `202`，包含 `docId`、`requestId` 和状态。
- 成功结果的 `parseStatus=SUCCESS`，`rawText` 与上传的 UTF-8 文本一致，`current=true`。
- 不支持的 PDF/Office 文件返回明确失败状态和错误码，不应假装解析成功。

### F-14 实体识别、法律要素和摘要

**操作步骤**

1. 在 F-13 的解析成功后，分别调用：

```text
POST /api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs
POST /api/v1/cases/{caseId}/documents/{docId}/legal-element-jobs
POST /api/v1/cases/{caseId}/summary-jobs
```

2. 摘要请求体示例：

```json
{"summaryType":"FACT","sourceDocIds":[docId]}
```

3. 保存每个任务返回的 `requestId`，轮询对应 job 查询接口，直到 `status=SUCCESS` 或 `status=FAILED`。
4. 成功后查询结果列表和详情；实体/法律要素使用确认接口提交原结果或人工修改后的 JSON；摘要使用确认接口。

**成功标准**

- 创建均返回 `202`，含 `requestId`。
- 成功任务有对应结果 ID；失败任务有非空 `errorCode`，不会覆盖上一版成功结果。
- 实体结果同时保留 `originalEntities` 和 `finalEntities`。
- 法律要素结果含原始要素、最终要素和校验报告；证据引用必须能在原文中找到。
- 摘要返回 `summaryText`、版本号和 `current=true`；确认后有 `confirmedBy`、`confirmedAt`。

### F-15 DeepSeek 正向流程（需单独批准）

**前置条件**：明确批准将虚构或已脱敏案件文本发送到 DeepSeek，并在本地设置 `LEXPRO_AI_ENABLED=true`、模型名和 API Key。未批准时不要执行。

**操作步骤**：重复 F-14，观察后端日志、任务状态和审计记录。

**成功标准**：任务在超时范围内成功或明确失败；响应不包含 API Key；审计记录有调用结果但不保存 Token 和完整敏感正文；外部服务不可用时返回受控失败，不阻塞案件普通查询。

## 7. 案卡和报告

### F-16 报告模板

**操作步骤**

1. `POST /api/v1/report-templates`，按 Swagger schema 填写唯一的大写 `templateCode`、名称、类型、JSON 内容和 `schemaVersion`。
2. 保存 `templateId`，调用列表和详情。
3. 分别调用启用和停用接口。

**成功标准**

- 创建返回 `201` 和 `templateId`，初始状态为草稿/未启用状态。
- 列表、详情、启用、停用均返回 `200`，状态变化与操作一致。
- 使用不存在或无权限的模板生成报告应受控失败。

### F-17 案卡生成和确认

**操作步骤**

1. 确认案件有成功解析结果；按 Swagger 的 `StartCaseCardRequest` 提供 `fillMode=AUTO` 或 `HYBRID` 和至少一个来源，例如 `DOCUMENT`、`ENTITY`、`LEGAL_ELEMENT` 或 `SUMMARY`。
2. 调用 `POST /api/v1/cases/{caseId}/case-card-jobs`，记录 `requestId`。
3. 轮询 job 查询，成功后查询 `/case-cards` 和 `/case-cards/{fillTaskId}`。
4. 对一个字段调用确认接口，再确认整张案卡。

**成功标准**

- 创建返回 `202`；最终任务状态为 `SUCCESS`，详情有字段和来源。
- 字段确认后 `confirmStatus`、`confirmedBy`、`confirmedAt` 正确。
- 不存在的来源或未成功解析的来源返回 `400`/`409`，不生成无来源字段。

### F-18 报告生成、草稿、复核、定稿和导出

**操作步骤**

1. 调用 `POST /api/v1/cases/{caseId}/report-jobs`，按 Swagger 填写 `reportType`、标题、`templateId` 及已存在的来源 ID。
2. 轮询 job，成功后调用 `GET /reports` 和 `GET /reports/{reportId}`。
3. 使用返回的 `lockVersion` 调用 `PUT /reports/{reportId}/draft` 修改标题和 JSON 内容。
4. 调用 `PUT /reports/{reportId}/review-submission` 提交复核。
5. 使用复核角色退回：`PUT /reports/{reportId}/review-return`，请求体包含当前 `lockVersion` 和非空 `reason`。
6. 再次修改草稿并提交，调用 `PUT /reports/{reportId}/finalization` 定稿。
7. 分别调用：

```text
GET /api/v1/cases/{caseId}/reports/{reportId}/exports/docx
GET /api/v1/cases/{caseId}/reports/{reportId}/exports/pdf
```

**成功标准**

- 任务返回 `202`，最终状态为 `SUCCESS`。
- 每次修改返回 `200`，`lockVersion` 递增；使用旧版本应冲突，不能覆盖别人的修改。
- 状态顺序为草稿 -> 待复核 -> 退回或定稿；定稿后再次修改应失败。
- DOCX 返回 `200` 且 `Content-Type` 为 Word 类型；PDF 返回 `200` 且 `Content-Type=application/pdf`，文件可打开、中文不乱码。

## 8. 工作台功能

### F-19 首页、知识内容和任务

**操作步骤**

1. 浏览器打开 Dashboard，刷新页面。
2. 创建一条知识内容：`contentType` 使用 `KNOWLEDGE`、`RULE` 或 `CHECKLIST`，填写标题和正文。
3. 列表查询、详情查询、统计查询，修改标题后再次查询。
4. 创建一条关联案件的任务：`caseId` 有值、`contentId` 为 `null`。
5. 再创建一条关联知识内容的任务：`contentId` 有值、`caseId` 为 `null`。
6. 修改任务标题和优先级，查询列表和详情。

**成功标准**

- 创建内容和任务返回 `201`，初始任务状态为 `PENDING`，初始知识状态为 `DRAFT`。
- 列表、详情、统计和首页返回 `200`；新增/修改后的数据能在刷新后保留。
- 同时传 `caseId` 和 `contentId`，或二者都为空，返回 `400`，`errorCode=WORK_TASK_SUBJECT_INVALID`。
- Dashboard 中的案件/任务数量与对应列表数据一致。

## 9. 典型案例推荐（M7，需 Python 服务）

### F-20 导入和幂等

**前置条件**：V4 已按批准流程应用，Python 服务健康，Java 设置 `LEXPRO_RETRIEVAL_ENABLED=true`。

**操作步骤**

1. `POST /api/v1/typical-cases/imports` 导入至少两条虚构案例，至少填写 `externalCaseId`、`title`，并填写 `content` 或 `fact`。
2. 使用同样的 `externalCaseId` 再导入一次，但修改标题。
3. 调用 `GET /api/v1/typical-cases` 和 `GET /api/v1/typical-cases/{typicalCaseId}`。

**成功标准**

- 导入返回 `201`，每条有 `typicalCaseId`。
- 重复 `externalCaseId` 更新原记录，不产生重复记录。
- 查询返回 `200`；不返回 Embedding 数组、数据库连接串或本地存储路径。

### F-21 生成推荐、历史和收藏

**操作步骤**

1. 使用有权限且可见的 `{caseId}` 调用 `POST /api/v1/cases/{caseId}/recommendations`。
2. 请求体二选一：提供 `sourceSummaryId`，或提供 `factText`；不要同时提供。
3. 可选填写 `limit`、争议焦点和结构化筛选条件。
4. 查询推荐列表和详情；对一个典型案例执行收藏和取消收藏。

**成功标准**

- 创建返回 `201`，详情有推荐 ID、排名、分数和推荐理由。
- 历史和详情返回 `200`，结果顺序稳定且不重复。
- 收藏/取消收藏返回 `204`，再次查询 `favorite` 状态正确。
- Python 不可用时返回受控 `503` 或标记词法降级，不产生半条推荐历史。

## 10. MCP Server（M8，需真实客户端）

### F-22 工具发现和只读调用

**前置条件**：选择一个支持 Streamable HTTP 和 Bearer 的 MCP 客户端，临时设置 `LEXPRO_MCP_ENABLED=true`。

**操作步骤**

1. 连接 `http://127.0.0.1:8080/mcp`，完成 initialize。
2. 执行 tools/list，确认工具目录。
3. 依次调用当前用户、案件搜索、案件详情、卷宗文件列表、推荐列表和推荐详情。
4. 使用无权限账号访问其他用户的案件；提交超过最大 `limit` 的请求；发送格式错误的 JSON。

**成功标准**

- initialize 和 tools/list 成功，工具数量为 6。
- 每个成功工具调用只返回当前用户有权看到的数据。
- 不提供写工具、通用 SQL、Shell、文件正文、Token、密码哈希、Embedding 或内部路径。
- 越权调用返回受控错误；超限输入被拒绝或截断；格式错误响应不含 `stackTrace`。
- `operation_log` 中存在 `MCP_TOOL_CALLED` 成功和失败记录，且不保存 Token/原始输入正文。

## 11. 前端真实对接验收

### F-23 已对接页面

在 `http://127.0.0.1:5173` 逐项操作：

1. 登录：检查错误密码、刷新、退出。
2. Dashboard：点击刷新，检查网络请求确实为 `/api/v1/dashboard`，不是 Mock JSON。
3. 待办案件：查询、创建、打开详情、修改，刷新后数据仍在。
4. 知识内容：创建、编辑、刷新列表。
5. 待办任务：创建案件任务、创建知识任务、编辑和非法关联校验。

**成功标准**

- 浏览器开发者工具 Network 中出现真实后端请求，状态码符合 F-01 至 F-21 的规则。
- 刷新浏览器后数据仍存在，说明已经写入数据库。
- 后端返回 401 后，前端清除会话并跳转登录页。

### F-24 后续业务页面真实对接

以下页面已改为使用真实后端接口，不再读取前端 Mock JSON：

```text
CaseRecommend.vue
Organization.vue
DocumentEntities.vue
LegalElements.vue
Summary.vue
ReviewReport.vue
```

验收时应确认：

1. 页面先查询当前用户可访问的案件，并继续遵守案件级访问控制。
2. 组织与用户页查询真实组织树、角色和用户，并通过真实接口创建、启停用户和重置密码。
3. 实体、法律要素和摘要页从真实卷宗与解析版本读取正文和历史结果；启动按钮返回后端真实的 `202` 或受控错误。
4. 报告页读取真实报告历史和内容，支持模板选择、生成、提交复核、退回、定稿及 DOCX/PDF 导出。
5. 类案页读取真实推荐历史和详情，并通过 Java 调用 Python 检索服务创建新推荐。
6. 浏览器刷新后数据仍来自数据库，Network 中不得出现 `src/mock` 请求。

这些页面接入真实接口不等于所有外部条件已经就绪。AI 正向生成仍受 `LEXPRO_AI_ENABLED` 和 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA` 控制；新建类案推荐要求 Python 检索服务和 `LEXPRO_RETRIEVAL_ENABLED` 已启用；报告生成还要求存在 `ACTIVE` 模板。缺少前置条件时应显示后端稳定错误，不能伪造成功结果。

## 12. 安全和鲁棒性必测项

### F-25 输入校验

对案件、用户、知识内容、任务和报告接口分别提交：缺少必填字段、超长字符串、非法枚举、错误日期、错误 ID 和错误 JSON。

**成功标准**：返回 `400` 和 `VALIDATION_FAILED`/具体业务错误码；数据库不新增半条记录；响应不包含 Java 堆栈。

### F-26 权限边界

用 `USER_A` 和 `USER_B` 分别测试用户管理、案件、卷宗、任务、报告和推荐接口。

**成功标准**：无权限接口为 `403`；不可见案件为 `404`；不因为前端隐藏按钮就认为通过。

### F-27 敏感信息检查

在登录、用户详情、案件详情、卷宗、推荐、报告和 MCP 响应中搜索：

```text
password / passwordHash / token / secret / apiKey / embedding / storage path / stackTrace
```

**成功标准**：这些内部字段不出现在对外响应、审计详情或错误堆栈中。

## 13. 验收记录模板

每个功能只记录一行即可：

| 编号 | 功能 | 操作账号 | 资源 ID | 实际状态码 | 实际 errorCode | 结果 | 备注 |
|---|---|---|---|---:|---|---|---|
| F-01 | 健康检查 | 无 | - | 200 | - | PASS | 数据库 32 张业务表 |
| F-07 | 案件创建 | ADMIN | caseId= |  |  |  |  |
| F-10 | 卷宗上传 | ADMIN | dossierId= |  |  |  |  |

`BLOCKED` 只用于前置条件未准备好，例如没有普通账号、未批准外部 AI、Python 服务未启动或尚未选择 MCP 客户端；不能把“没有测试”写成 `PASS`。
