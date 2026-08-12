# LexPro 手工验收执行手册

> 依据：`docs/zh-CN/FUNCTION_ACCEPTANCE_CHECKLIST.md`
> 顺序：登录 / 权限 -> 案件主流程 -> 卷宗 -> 文档智能处理 -> 报告 / 案卡 -> 工作台 -> DeepSeek -> MCP
> 目标：给你一份可以直接照着操作的验收路线，只看真实请求、返回和持久化结果，不把可点击界面本身当成通过。

## 1. 先决条件

先把下面四件事确认好，再开始验收：

1. PostgreSQL 已启动，开发库 `lexpro` 可连。
2. 后端已启动，`http://127.0.0.1:8080/api/health` 和 `http://127.0.0.1:8080/api/health/database` 都能打开。
3. 前端已启动，`http://127.0.0.1:5173` 能访问。
4. 你已经准备好至少 3 类测试身份：
   - 管理员
   - 已分配案件的普通用户
   - 未分配案件的普通用户

建议所有验收数据统一用 `ACCEPTANCE_TEST_` 前缀，方便后面清理和定位。

## 2. 页面与验收方式对照

| 顺序 | 主题 | 页面 / 接口入口 | 当前状态 | 主要验收方式 |
|---|---|---|---|---|
| 1 | 登录 / 权限 | `/login`、`/dashboard`、`/organization`、`/pending-tasks` | 真实 | 前端 + API |
| 2 | 案件主流程 | `/todo-cases`、`/api/v1/cases*` | 真实 | 前端 + Swagger |
| 3 | 卷宗 | 智能处理页、`/api/v1/cases/{caseId}/dossier/*` | 真实 | 前端 + Swagger |
| 4 | 文档智能处理 | `/document-entities`、`/legal-elements`、`/summary` | 真实，受 AI 开关控制 | 前端 + Swagger |
| 5 | 报告 / 案卡 | `/review-report`、案卡和报告接口 | 报告页真实；案卡使用 Swagger | 前端 + Swagger |
| 6 | 工作台 | `/dashboard`、`/content-management`、`/pending-tasks` | 真实 | 前端 + API |
| 7 | DeepSeek | AI 处理相关接口 | 受开关控制 | Swagger + 日志 |
| 8 | MCP | `/mcp` | 服务已实现，需真 MCP 客户端 | 真 MCP 客户端 |

说明一下：

- `DocumentEntities.vue`、`LegalElements.vue`、`Summary.vue`、`ReviewReport.vue`、`CaseRecommend.vue`、`Organization.vue` 已接入真实后端接口。
- 真正要算通过，仍要同时查看浏览器 Network、后端返回码、返回体和刷新后的持久化结果。

## 3. 验收顺序

### 3.1 登录 / 权限

入口：`http://127.0.0.1:5173/#/login`

按这个顺序验：

1. 用管理员账号登录。
2. 打开浏览器开发者工具的 Network，确认登录请求真的是 `POST /api/v1/auth/login`。
3. 看响应里有没有 `accessToken`、`tokenType`、`expiresAt` 和当前用户信息。
4. 登录后刷新页面，确认不会掉回登录页。
5. 打开 `GET /api/v1/auth/me`，确认还能拿到当前用户信息。
6. 点击退出登录，确认 `POST /api/v1/auth/logout` 返回 `204`。
7. 退出后再访问 `GET /api/v1/auth/me`，应返回 `401`。
8. 用普通用户登录，直接访问 `/organization` 和 `/pending-tasks`：
   - 没有 `USER_MANAGE` 的用户，`/organization` 应该进不去；
   - 没有 `TASK_MANAGE` 的用户，`/pending-tasks` 应该进不去。
9. 故意输错密码 1 次，确认返回 `401`。
10. 连续输错到限流阈值，确认返回 `429 LOGIN_RATE_LIMITED`。

通过标准：

- 登录、退出、会话恢复都正常。
- 权限不足时被拦住，而不是只靠前端按钮隐藏。
- 错误密码和限流返回码正确。

### 3.2 案件主流程

入口：`http://127.0.0.1:5173/#/todo-cases`

建议你用一个管理员或有案件写权限的账号来做：

1. 打开案件列表，看是否能正常加载。
2. 点“新建案件”，填一个带 `ACCEPTANCE_TEST_` 前缀的案件。
3. 保存后确认返回 `201`，并拿到 `caseId`。
4. 回到列表，确认这条案件真的出现了。
5. 打开详情，确认 `GET /api/v1/cases/{caseId}` 返回的内容和创建时一致。
6. 打开“参与人”和“分配历史”页签，确认能看到对应数据。
7. 修改一个普通字段，再次保存，确认返回 `200`，并且详情页同步更新。
8. 用未分配该案件的普通用户登录，再访问同一案件详情：
   - 应返回 `404` 或被受控拒绝；
   - 不能把案件内容直接泄露出来。

如果你要补充分配操作，先走 Swagger：

- `POST /api/v1/cases/{caseId}/assignments`
- `POST /api/v1/cases/{caseId}/assignments/{assignmentId}/end`

通过标准：

- 新建、查询、修改都通。
- 案件级越权被拦住。
- 参与人和分配历史一致。

### 3.3 卷宗

卷宗上传和 TXT 解析可从实体识别、法律要素或摘要页操作；文件夹、标签、软删除和恢复仍建议用 Swagger 补测。

先打开：`http://127.0.0.1:8080/swagger-ui.html`

按这个顺序做：

1. 先给某个案件建一个文件夹。
2. 再建一个标签。
3. 上传一个 UTF-8 的 `.txt` 文件。
4. 确认上传返回 `201`，并拿到 `dossierId`。
5. 打开文件列表，确认能看到刚上传的文件。
6. 打开下载接口，确认能下载到原始内容。
7. 把这个文件软删除。
8. 再查列表，默认视图里应该看不到它。
9. 带 `includeDeleted=true` 再查一次，应该还能看到已删除状态。
10. 恢复这个文件，再下载一次。
11. 直接测几个错误输入：
    - 空文件 -> `400 DOSSIER_FILE_EMPTY`
    - 不允许的扩展名 -> `415 DOSSIER_EXTENSION_NOT_ALLOWED`
    - 超过 25MB -> `413 DOSSIER_FILE_TOO_LARGE`

通过标准：

- 上传、列表、下载、删除、恢复都闭环。
- 删除后默认不可见，但还能恢复。
- 返回体里不暴露本地存储路径。

### 3.4 文档智能处理

先从真实前端页面走主流程，再用 Swagger 核对接口状态和异常边界。

你要盯的是这些接口：

- `POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-jobs`
- `GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-results`
- `GET /api/v1/cases/{caseId}/documents/{docId}`
- `POST /api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs`
- `POST /api/v1/cases/{caseId}/documents/{docId}/legal-element-jobs`
- `POST /api/v1/cases/{caseId}/summary-jobs`

建议顺序：

1. 先完成卷宗上传，拿到一个 `dossierId`。
2. 发起解析任务。
3. 确认创建任务返回 `202`，并拿到 `requestId` 和 `docId`。
4. 轮询结果，直到 `parseStatus=SUCCESS`。
5. 打开文档详情，确认 `rawText` 和上传的文本一致。
6. 再分别发起实体识别、法律要素和摘要任务。
7. 每个任务都先看 `202`，然后轮询到 `SUCCESS` 或明确失败。
8. 读取结果时，确认原始结果和确认结果都能保留。

额外要测的错误场景：

- 如果你用的是本地默认配置，DeepSeek 外发关着时，相关 AI 任务应返回 `503 AI_DATA_EXPORT_DISABLED`。
- 本地开发当前只建议用 UTF-8 文本文件做主验收，别拿不支持的 Office/PDF 当主流程。

通过标准：

- 异步任务是先 `202`，再轮询成功。
- 结果可查，且不泄露敏感原文之外的内部信息。

### 3.5 报告 / 案卡

`ReviewReport.vue` 已接入真实报告历史、生成、复核流转和导出接口；案卡字段确认和模板生命周期仍以 Swagger 补测。

先看这些接口：

- `POST /api/v1/cases/{caseId}/case-card-jobs`
- `GET /api/v1/cases/{caseId}/case-card-jobs/{requestId}`
- `GET /api/v1/cases/{caseId}/case-cards`
- `GET /api/v1/cases/{caseId}/case-cards/{fillTaskId}`
- `PUT /api/v1/cases/{caseId}/case-cards/{fillTaskId}/fields/{fieldId}/confirmation`
- `PUT /api/v1/cases/{caseId}/case-cards/{fillTaskId}/confirmation`
- `POST /api/v1/report-templates`
- `PUT /api/v1/report-templates/{templateId}/activation`
- `PUT /api/v1/report-templates/{templateId}/disablement`
- `POST /api/v1/cases/{caseId}/report-jobs`
- `GET /api/v1/cases/{caseId}/reports`
- `GET /api/v1/cases/{caseId}/reports/{reportId}`
- `PUT /api/v1/cases/{caseId}/reports/{reportId}/draft`
- `PUT /api/v1/cases/{caseId}/reports/{reportId}/review-submission`
- `PUT /api/v1/cases/{caseId}/reports/{reportId}/review-return`
- `PUT /api/v1/cases/{caseId}/reports/{reportId}/finalization`
- `GET /api/v1/cases/{caseId}/reports/{reportId}/exports/{format}`

建议顺序：

1. 先创建一个报告模板。
2. 再启用 / 停用一次，确认状态流转正常。
3. 用一个案件启动案卡任务。
4. 等任务完成后，检查案卡字段和字段来源。
5. 逐个确认一个字段，再整卡确认一次。
6. 再用同一个案件启动报告任务。
7. 检查草稿、送审、退回、定稿这几个状态流转。
8. 尝试导出 `DOCX` 和 `PDF`。
9. 故意用旧的 `lockVersion` 提交一次，确认返回冲突。

通过标准：

- 案卡和报告都能走完整状态流。
- 旧版本修改会被拦住。
- 导出的文件能正常打开。

### 3.6 工作台

这部分前端是真接 API 的，适合你直接看页面。

入口：

- `http://127.0.0.1:5173/#/dashboard`
- `http://127.0.0.1:5173/#/content-management`
- `http://127.0.0.1:5173/#/pending-tasks`

按这个顺序看：

1. 先进 Dashboard，刷新一次，看指标是不是来自 `GET /api/v1/dashboard`。
2. 点案件快捷入口，确认能跳到案件列表。
3. 进“知识内容”，新建一条 `DRAFT` 内容。
4. 列表里查到它后，打开详情、编辑、刷新，再确认数据还在。
5. 换一个没有 `CONTENT_MANAGE` 的用户，确认只能看到已发布内容。
6. 进“待办任务”，创建一条和案件相关的任务。
7. 再创建一条和知识内容相关的任务。
8. 验证“案件 / 知识内容”二选一，不能同时都空，也不能同时乱填。
9. 打开任务详情，编辑标题、优先级、截止时间后再保存。

通过标准：

- Dashboard、知识内容、待办任务都是真接口。
- 刷新后数据还在，不是前端假数据。
- 任务主体只能选一类，校验正确。

### 3.7 DeepSeek

这个项先分成“安全验证”和“正向验证”两种。

安全验证，建议默认先做：

1. 确认下面这些环境变量已经按你的本机配置好了：
   - `LEXPRO_AI_ENABLED=true`
   - `LEXPRO_AI_BASE_URL=https://api.deepseek.com`
   - `LEXPRO_AI_MODEL=deepseek-v4-flash`
   - `LEXPRO_AI_API_KEY=...`
   - `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false`
2. 发起一次会走 AI 的任务。
3. 确认返回的是受控失败，比如 `503 AI_DATA_EXPORT_DISABLED`。
4. 确认日志和响应里没有把 API Key、Token、完整案件原文打出来。
5. 确认审计里有记录，但没有敏感字段泄漏。

正向验证，只有在你明确同意外发后才做：

1. 把 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA` 临时改成 `true`。
2. 用虚构或脱敏数据再跑一次。
3. 看任务是否成功，或者至少能给出明确失败原因。
4. 做完后立刻改回 `false`。

通过标准：

- 默认安全开关必须生效。
- 不允许默默把真实案件文本外发。
- 响应和日志都不泄露密钥。

### 3.8 MCP

MCP 这一项必须用真客户端，不要只拿浏览器看一下 `/mcp` 就算完。

建议验收顺序：

1. 先把 `LEXPRO_MCP_ENABLED=true` 临时打开。
2. 用支持 Streamable HTTP + Bearer 的真实 MCP 客户端连接 `http://127.0.0.1:8080/mcp`。
3. 先做 `initialize`。
4. 再做 `tools/list`。
5. 确认只出现当前 4 个工具：
   - `lexpro_recognize_legal_elements`
   - `lexpro_recognize_entities`
   - `lexpro_summarize_case`
   - `lexpro_push_typical_cases`（保留占位，固定返回未实现错误）
6. 使用独立 MCP 服务令牌逐个调用前三个 AI 工具，确认输入输出受 Schema 和长度限制。
7. 确认网页 JWT 不能用于 `/mcp`，无效、撤销或权限不足的服务令牌会被拒绝。
8. 故意发送超长输入或格式错误 JSON，确认返回稳定错误且不带堆栈。
9. 查看 `operation_log`，确认存在 `MCP_TOOL_CALLED` 记录且不保存令牌或完整输入正文。
10. 测完把 `LEXPRO_MCP_ENABLED` 改回 `false`。

通过标准：

- 真客户端能连上。
- 工具目录正确。
- 越权、超限、格式错误都被稳稳拦住。

## 4. 记录建议

你可以直接照这个模板记验收结果：

| 项目 | 结果 | 关键证据 | 备注 |
|---|---|---|---|
| 登录 / 权限 |  |  |  |
| 案件主流程 |  |  |  |
| 卷宗 |  |  |  |
| 文档智能处理 |  |  |  |
| 报告 / 案卡 |  |  |  |
| 工作台 |  |  |  |
| DeepSeek |  |  |  |
| MCP |  |  |  |

## 5. 一句话结论

先验收前 6 项，再看 DeepSeek 和 MCP。登录、案件、卷宗、智能处理、报告和工作台均可从真实前端进入；最终仍以 Network、HTTP 返回、数据库持久化和权限边界共同判定，案卡的细粒度操作继续通过 Swagger 验收。
