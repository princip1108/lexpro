# LexPro API 规范

[English](../API_CONVENTIONS.md)

## 通用约定

- API 基础路径：`/api/v1`。
- JSON 字段使用 `camelCase`。
- 数据库继续使用 `snake_case`，但数据库命名不作为 API 命名约定暴露给前端。
- 除文件上传下载外，内容类型使用 `application/json; charset=UTF-8`。
- 时间使用带偏移量的 ISO-8601，例如 `2026-07-28T10:30:00+08:00`。
- 日期使用 ISO `yyyy-MM-dd`。

## HTTP 语义

| 操作 | 状态码 |
|---|---:|
| 查询或修改成功 | `200 OK` |
| 创建资源成功 | `201 Created` |
| 已接受异步任务 | `202 Accepted` |
| 成功且无需响应体 | `204 No Content` |
| 请求参数错误 | `400 Bad Request` |
| 未登录或认证信息无效 | `401 Unauthorized` |
| 已登录但无权访问 | `403 Forbidden` |
| 资源不存在 | `404 Not Found` |
| 状态或版本冲突 | `409 Conflict` |
| 不支持的文件/媒体类型 | `415 Unsupported Media Type` |
| 未预期的服务器错误 | `500 Internal Server Error` |

成功响应直接返回资源或操作结果，不为每个响应重复包装 `{code, message, data}`。

## 请求关联

- 每个 HTTP 响应都包含 `X-Request-Id`。
- 客户端可以传入由 8 到 64 个 ASCII 字母、数字、点、下划线或连字符组成的 `X-Request-Id`。
- 缺失或格式不安全的值会替换为服务端生成的 UUID。
- 同一个值会出现在请求完成日志、错误响应以及本次请求产生的审计记录中。

## 错误响应

错误使用 Spring `ProblemDetail`（`application/problem+json`），并增加稳定扩展字段：

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid",
  "instance": "/api/v1/cases",
  "errorCode": "VALIDATION_FAILED",
  "requestId": "01J...",
  "fieldErrors": {
    "caseName": "must not be blank"
  }
}
```

前端使用稳定的 `errorCode` 进行逻辑判断，`detail` 用于展示或诊断。响应中不得出现堆栈、SQL、密码、Token 或内部存储路径。

## 分页

- 请求参数 `page` 从 1 开始。
- `size` 默认 20，最大 100。
- 排序字段必须使用每个接口自己的允许列表，不能把前端传入的原始字段名直接拼入 SQL。
- Java 公共分页契约为 `PageRequest` 和 `PageResponse<T>`。

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## 认证

- 受保护接口使用 `Authorization: Bearer <access-token>`。
- 公开接口仅限健康检查、本地接口文档和登录。
- 权限和案件可见范围以后端判断为准，前端隐藏按钮不能代替权限控制。
- Access Token 使用 HS256，默认 30 分钟过期，不提供 Refresh Token。
- `POST /api/v1/auth/logout` 记录退出操作，客户端必须丢弃 Token；服务端没有 Token 黑名单。
- 受保护请求会重新读取账号状态和角色权限。账号被禁用、删除或修改后，旧 Token 不能继续使用。
- 登录、当前用户和 Token 响应使用 `Cache-Control: no-store`。

### M2 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | 公开 | Access Token、过期时间和当前用户 |
| `GET` | `/api/v1/auth/me` | 已登录 | 当前用户、组织、角色和权限 |
| `POST` | `/api/v1/auth/logout` | 已登录 | `204 No Content` |
| `GET` | `/api/v1/users` | `USER_MANAGE` | 分页用户列表 |
| `GET` | `/api/v1/users/{userId}` | `USER_MANAGE` | 用户详情 |
| `POST` | `/api/v1/users` | `USER_MANAGE` | `201 Created` 用户 |
| `PATCH` | `/api/v1/users/{userId}/status` | `USER_MANAGE` | 启用或禁用后的用户 |
| `PUT` | `/api/v1/users/{userId}/password` | `USER_MANAGE` | `204 No Content` |
| `GET` | `/api/v1/organizations/tree` | `USER_MANAGE` | 组织树 |
| `GET` | `/api/v1/roles` | `USER_MANAGE` | 角色及权限码 |
| `GET` | `/api/v1/permissions` | `USER_MANAGE` | 权限目录 |

### M3 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `GET` | `/api/v1/cases` | `CASE_READ` 加案件可见性 | 当前用户可见的分页案件列表 |
| `GET` | `/api/v1/cases/{caseId}` | `CASE_READ` 加案件可见性 | 案件详情 |
| `POST` | `/api/v1/cases` | `CASE_WRITE` | `201 Created`；新案件状态为 `PENDING`，创建人自动获得当前 `ASSIGNEE` + `MANAGE` 分配 |
| `PUT` | `/api/v1/cases/{caseId}` | `CASE_WRITE` 加案件 `EDIT` 访问级别 | 修改案件基础信息；不修改状态 |
| `GET` | `/api/v1/cases/{caseId}/parties` | `CASE_READ` 加案件可见性 | 参与人列表，不返回身份证原值或哈希 |
| `POST` | `/api/v1/cases/{caseId}/parties` | `CASE_WRITE` 加案件 `EDIT` 访问级别 | `201 Created` 参与人；暂不接收身份证原值 |
| `PUT` | `/api/v1/cases/{caseId}/parties/{partyId}` | `CASE_WRITE` 加案件 `EDIT` 访问级别 | 修改参与人的安全字段 |
| `GET` | `/api/v1/cases/{caseId}/assignments` | `CASE_READ` 加案件可见性 | 分配历史 |
| `POST` | `/api/v1/cases/{caseId}/assignments` | `CASE_ASSIGN` 加案件 `MANAGE` 访问级别 | `201 Created`；给启用状态用户新增当前分配 |
| `POST` | `/api/v1/cases/{caseId}/assignments/{assignmentId}/end` | `CASE_ASSIGN` 加案件 `MANAGE` 访问级别 | 结束一个当前分配 |

案件状态流转和身份证原值采集暂未开放，因为对应的业务/安全策略仍是待确认决策。

### M4 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `GET` | `/api/v1/cases/{caseId}/dossier/folders` | `CASE_READ` 加案件可见性 | 嵌套卷宗目录树 |
| `POST` | `/api/v1/cases/{caseId}/dossier/folders` | `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别 | `201 Created` 目录 |
| `GET` | `/api/v1/cases/{caseId}/dossier/tags` | `CASE_READ` 加案件可见性 | 案件内的文件标签 |
| `POST` | `/api/v1/cases/{caseId}/dossier/tags` | `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别 | `201 Created` 标签 |
| `GET` | `/api/v1/cases/{caseId}/dossier/files` | `CASE_READ` 加案件可见性 | 文件元数据；可使用 `folderId` 和 `includeDeleted` 筛选 |
| `POST` | `/api/v1/cases/{caseId}/dossier/files` | `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别 | multipart 上传，字段为 `file`、可选 `folderId` 和可重复 `tagId` |
| `PUT` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}` | `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别 | 重命名、移动并整体替换标签，不允许改变扩展名 |
| `DELETE` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}` | `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别 | `204 No Content`；只执行软删除 |
| `POST` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/restore` | `DOSSIER_MANAGE` 加案件 `EDIT` 访问级别 | 恢复软删除文件 |
| `GET` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/content` | `CASE_READ` 加案件可见性 | 经过鉴权的附件下载 |

文件元数据响应绝不返回 `fileUrl`、对象键或本地存储路径。下载响应设置 `no-store`、附件下载和 `nosniff` 响应头。

### M5-S1 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-jobs` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | `202 Accepted`；创建新的当前解析版本，并在事务提交后调度 |
| `GET` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-results` | `CASE_READ` 加案件可见性 | 不包含解析正文的版本历史 |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}` | `CASE_READ` 加案件可见性 | 单个解析结果，包含结构化 JSON 和提取原文 |

同一文件的当前任务仍新鲜时再次启动，返回 `409 PARSE_IN_PROGRESS`。失败后再次提交会创建新版本。超过配置过期阈值的 `PROCESSING` 任务会先以 `PROCESSING_INTERRUPTED` 标记为 `FAILED`，再创建重试版本。解析异步执行，响应不返回内部对象键或路径。

### M5-S2 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | `202 Accepted`；事务提交后调度实体识别 |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs/{requestId}` | `CASE_READ` 加案件可见性 | 根据持久化审计记录返回 `PROCESSING`、`SUCCESS` 或 `FAILED` |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/entity-results` | `CASE_READ` 加案件可见性 | 该解析结果的实体识别历史 |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}` | `CASE_READ` 加案件可见性 | 原始/确认实体及模型来源信息 |
| `PUT` | `/api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}/confirmation` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | 保存第一次人工确认结果，不覆盖模型原始输出 |

启动识别要求解析结果已经成功且包含提取文本。任务异步执行，`requestId` 是服务端生成的 UUID。确认请求必须是包含 `entities` 数组的对象，第一次确认后不可覆盖。AI 服务错误使用稳定错误码，不返回供应商响应正文、凭据或提示词。环境未明确允许外发时返回 `503 AI_DATA_EXPORT_DISABLED`，不会发送文本。

### M5-S3 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-jobs` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | `202 Accepted`；事务提交后调度法律要素识别 |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-jobs/{requestId}` | `CASE_READ` 加案件可见性 | 根据持久化审计记录返回 `PROCESSING`、`SUCCESS` 或 `FAILED` |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-results` | `CASE_READ` 加案件可见性 | 该解析结果的法律要素历史 |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-results/{elementResultId}` | `CASE_READ` 加案件可见性 | 原始/确认要素、证据和模型来源信息 |
| `PUT` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-results/{elementResultId}/confirmation` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | 保存第一次人工确认结果，不覆盖模型原始输出 |

每个要素至少包含一条与解析文本完全一致的原文引用。可选的 `startOffset` 和 `endOffset` 使用 Java UTF-16 索引，起点包含、终点不包含，并且必须准确选中同一引用。确认请求必须包含非空 `elements` 数组，且不能覆盖已有确认。

### M5-S4 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/summary-jobs` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | `202 Accepted`；使用一到二十个指定解析结果生成摘要 |
| `GET` | `/api/v1/cases/{caseId}/summary-jobs/{requestId}` | `CASE_READ` 加案件可见性 | 根据持久化审计记录返回 `PROCESSING`、`SUCCESS` 或 `FAILED` |
| `GET` | `/api/v1/cases/{caseId}/summaries?summaryType=FULL` | `CASE_READ` 加案件可见性 | 指定摘要类型的版本历史 |
| `GET` | `/api/v1/cases/{caseId}/summaries/{summaryId}` | `CASE_READ` 加案件可见性 | 一个摘要版本及模型来源信息 |
| `PUT` | `/api/v1/cases/{caseId}/summaries/{summaryId}/confirmation` | `AI_EXECUTE` 加案件 `EDIT` 访问级别 | 将一个生成摘要标记为已人工确认 |

摘要类型为 `FACT`、`PROCESS`、`CONCLUSION` 和 `FULL`。启动请求体包含 `summaryType` 以及不可重复的 `sourceDocIds` 数组，每个来源都必须是同一案件内成功的解析结果。版本按案件和摘要类型分别编号；生成失败时原来的当前版本保持不变。确认表示接受现有生成文本；需要修改文本时应重新生成一个新版本。

### M6 案卡接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/case-card-jobs` | `REPORT_MANAGE` + `AI_EXECUTE` 加案件 `EDIT` 访问级别 | `202 Accepted`；从类型化来源生成案卡 |
| `GET` | `/api/v1/cases/{caseId}/case-card-jobs/{requestId}` | `CASE_READ` 加案件可见性 | 持久化案卡任务状态 |
| `GET` | `/api/v1/cases/{caseId}/case-cards` | `CASE_READ` 加案件可见性 | 案卡历史 |
| `GET` | `/api/v1/cases/{caseId}/case-cards/{fillTaskId}` | `CASE_READ` 加案件可见性 | 任务、类型化来源和生成字段 |
| `PUT` | `/api/v1/cases/{caseId}/case-cards/{fillTaskId}/fields/{fieldId}/confirmation` | `REPORT_MANAGE` 加案件 `EDIT` 访问级别 | 一次性确认或驳回字段 |
| `PUT` | `/api/v1/cases/{caseId}/case-cards/{fillTaskId}/confirmation` | `REPORT_MANAGE` 加案件 `EDIT` 访问级别 | 全部字段处理后确认草稿案卡 |

来源类型为 `DOCUMENT`、`ENTITY`、`LEGAL_ELEMENT` 和 `SUMMARY`。生成字段保留准确来源引文、类型化来源标识、位置、可用时的卷宗文件及置信度。

### M6 报告接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/report-templates` | `REPORT_MANAGE` | 为模板编码创建下一个草稿版本 |
| `GET` | `/api/v1/report-templates` | `REPORT_MANAGE` | 按可选类型/状态查询模板版本 |
| `GET` | `/api/v1/report-templates/{templateId}` | `REPORT_MANAGE` | 一个模板版本 |
| `PUT` | `/api/v1/report-templates/{templateId}/activation` | `REPORT_MANAGE` | 启用草稿，并在同一事务中禁用同编码的上一启用版本 |
| `PUT` | `/api/v1/report-templates/{templateId}/disablement` | `REPORT_MANAGE` | 禁用启用状态的模板版本 |
| `POST` | `/api/v1/cases/{caseId}/report-jobs` | `REPORT_MANAGE` + `AI_EXECUTE` 加案件 `EDIT` 访问级别 | `202 Accepted`；使用启用模板和显式来源生成新版本 |
| `GET` | `/api/v1/cases/{caseId}/report-jobs/{requestId}` | `CASE_READ` 加案件可见性 | 持久化报告任务状态 |
| `GET` | `/api/v1/cases/{caseId}/reports` | `CASE_READ` 加案件可见性 | 报告历史，可按类型筛选 |
| `GET` | `/api/v1/cases/{caseId}/reports/{reportId}` | `CASE_READ` 加案件可见性 | 报告正文和规范化引用 |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/draft` | `REPORT_MANAGE` 加案件 `EDIT` 访问级别 | 使用 `lockVersion` 乐观锁编辑标题/正文 |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/review-submission` | `REPORT_MANAGE` 加案件 `EDIT` 访问级别 | 使用 `lockVersion` 将当前 `DRAFT` 送至 `REVIEWING` |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/review-return` | `REPORT_MANAGE` 加案件 `EDIT` 访问级别 | 将当前 `REVIEWING` 退回 `DRAFT`，必须填写原因 |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/finalization` | `REPORT_MANAGE` 加案件 `MANAGE` 访问级别 | 将当前 `REVIEWING` 变为不可修改的 `FINAL` |
| `GET` | `/api/v1/cases/{caseId}/reports/{reportId}/exports/{format}` | `CASE_READ` 加案件可见性 | 下载 `DOCX` 或 `PDF`，仅支持 `DRAFT`、`REVIEWING`、`FINAL` |

新版本生成成功前不会替换上一份当前报告。模板和报告使用有序 `sections` 协议，报告段落编码、标题和顺序必须匹配所选模板；状态请求包含 `lockVersion`。下载响应使用附件、`no-store` 和 `nosniff`，非定稿导出带草稿标记。

### M7 典型案例推荐接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/typical-cases/imports` | `REPORT_MANAGE` + `AI_EXECUTE` | 规范化、向量化并按 `externalCaseId` 幂等导入，单次最多 50 条 |
| `GET` | `/api/v1/typical-cases` | `RECOMMENDATION_USE` | 分页查询语料，支持关键词、案由/案件类型和仅收藏筛选 |
| `GET` | `/api/v1/typical-cases/{typicalCaseId}` | `RECOMMENDATION_USE` | 返回典型案例元数据和正文，不返回向量 |
| `PUT` | `/api/v1/typical-cases/{typicalCaseId}/favorite` | `RECOMMENDATION_USE` | `204 No Content`；幂等收藏 |
| `DELETE` | `/api/v1/typical-cases/{typicalCaseId}/favorite` | `RECOMMENDATION_USE` | `204 No Content`；幂等取消收藏 |
| `POST` | `/api/v1/cases/{caseId}/recommendations` | `RECOMMENDATION_USE` + `CASE_READ` 加案件可见性 | `201 Created`；执行检索并保存一个推荐批次 |
| `GET` | `/api/v1/cases/{caseId}/recommendations` | `RECOMMENDATION_USE` + `CASE_READ` 加案件可见性 | 推荐历史 |
| `GET` | `/api/v1/cases/{caseId}/recommendations/{recommendId}` | `RECOMMENDATION_USE` + `CASE_READ` 加案件可见性 | 查询快照、检索参数和排序结果 |

推荐输入必须在 `sourceSummaryId` 和 `factText` 中恰好提供一个，可包含 `disputeFocus`、结构化筛选和 1 到 50 的结果数量。Java 在调用 Python 前完成案件授权。纯词法降级结果以 `degraded=true` 保存；Python 或数据库服务不可用时返回 `503`，且不创建推荐批次。

### M8 MCP 端点

- 端点：`POST /mcp`，使用 MCP Java SDK `0.18.3` 的无状态 Streamable HTTP。
- 访问：每个请求都携带 `Authorization: Bearer <service-token>`。令牌通过外部 MCP 客户端注册表解析为具名 `clientId` 和受限权限；网页用户 JWT 会被拒绝。
- 开关：默认关闭，只在已批准环境设置 `LEXPRO_MCP_ENABLED=true`。
- 输入：每个工具都使用封闭 JSON Schema（`additionalProperties=false`）；结果受 `LEXPRO_MCP_MAX_ITEMS` 和 `LEXPRO_MCP_MAX_OUTPUT_CHARS` 限制。
- 审计：每次工具调用以 HTTP 请求 ID 写入 `MCP_TOOL_CALLED`；审计详情不保存 MCP 输入正文。

| MCP 工具 | 所需权限 | 结果 |
|---|---|---|
| `lexpro_recognize_legal_elements` | `AI_EXECUTE` | 经校验且保留原文证据的法律要素 |
| `lexpro_recognize_entities` | `AI_EXECUTE` | 经校验的文书实体及可用的原文位置 |
| `lexpro_summarize_case` | `AI_EXECUTE` | 对显式传入文书文本生成的有界摘要 |
| `lexpro_push_typical_cases` | 已注册客户端 | 保留占位，固定返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED` |

当前目录不开放 MCP Resource、Prompt、通用 SQL、Shell、文件系统或不受限 HTTP 能力。前三个工具复用已批准的 DeepSeek 兼容客户端，并继续受案件数据外发开关和输入输出上限控制。

### M9 工作台接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `GET` | `/api/v1/knowledge-contents` | 已登录 | 分页查询可见知识；非管理员只能看到 `PUBLISHED` |
| `GET` | `/api/v1/knowledge-contents/statistics` | 已登录 | 当前用户可见知识范围的统计 |
| `GET` | `/api/v1/knowledge-contents/{contentId}` | 已登录 | 可见内容详情，包含正文或结构化 JSON |
| `POST` | `/api/v1/knowledge-contents` | `CONTENT_MANAGE` | `201 Created`；新建 `DRAFT` 内容 |
| `PUT` | `/api/v1/knowledge-contents/{contentId}` | `CONTENT_MANAGE` | 编辑类型、标题、正文和所属组织，不改变状态 |
| `GET` | `/api/v1/work-tasks` | `TASK_MANAGE` | 分页查询可访问任务，默认只看分配给当前用户的任务 |
| `GET` | `/api/v1/work-tasks/{taskId}` | `TASK_MANAGE`，关联案件时加案件可见性 | 可访问任务详情 |
| `POST` | `/api/v1/work-tasks` | `TASK_MANAGE`，关联案件时加案件 `EDIT` | `201 Created`；创建分配给当前用户的 `PENDING` 任务 |
| `PUT` | `/api/v1/work-tasks/{taskId}` | `TASK_MANAGE`，关联案件时加案件 `EDIT` | 编辑关联对象和基础信息，不改变状态或负责人 |
| `GET` | `/api/v1/dashboard` | `DASHBOARD_VIEW` | 可见案件指标、分类、最近案件，以及当前用户任务指标和紧急任务 |

知识内容必须包含正文或对象/数组 JSON。人工任务必须在 `caseId` 和 `contentId` 中恰好填写一个。知识和任务状态流转矩阵及授权角色批准前，M9 不开放状态命令；公告、日程和通知不在本期范围。

## 接口路径命名

- 使用复数名词：`/cases`、`/users`、`/files`。
- 父资源决定权限边界时使用嵌套路径：`/cases/{caseId}/parties`。
- 业务状态变化使用明确的命令资源：`/cases/{caseId}/status-transitions`。
- 路径中不使用 `getUserList` 之类的方法名。
