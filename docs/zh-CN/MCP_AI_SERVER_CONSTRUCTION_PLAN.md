# LexPro AI MCP Server 施工方案

> 状态：核心代码已实施，待服务器 HTTPS 部署和真实客户端验收
> 
> 范围：将现有 Spring Boot MCP Server 改造为可部署到服务器、可供其他项目长期调用的法律智能能力服务。

## 1. 建设目标

本次建设只保留一个 MCP Server，通过现有 `/mcp` Streamable HTTP 端点提供四类能力：

1. 法律要素识别。
2. 文书实体识别。
3. 案件摘要生成。
4. 典型案例推送。

前三项使用项目已有的 OpenAI-compatible DeepSeek 客户端。第四项只保留占位工具；《典型案例推送接口》仅作为后续需求参考，本轮不调用、不联调典型案例服务。

MCP 只负责协议适配、输入校验、权限、审计和结果过滤，不成为新的业务数据权威来源，也不暴露通用 HTTP、SQL、Shell 或文件系统能力。

## 2. 现有基础

- 后端基线为 Java 21、Spring Boot 3.5.x、Maven。
- MCP 已使用官方 Java SDK、无状态 Streamable HTTP 和 `/mcp` 端点。
- 当前请求上下文只能解析站内用户 JWT、数字用户 ID、权限集合和请求 ID；这不满足外部项目以服务身份长期调用的要求，实施时必须替换。
- 现有 AI 客户端为：
  - `OpenAiCompatibleLegalElementRecognitionClient`
  - `OpenAiCompatibleEntityRecognitionClient`
  - `OpenAiCompatibleCaseSummaryClient`
- DeepSeek 配置已经具备启用开关、API 地址、模型、超时、输入长度和外部案件数据开关。
- 典型案例文档作为未来接口设计参考，描述了 `analyze` 和 `search` 两个阶段；本轮不实现该下游客户端。

本方案不包含数据库迁移，不修改已应用的 V1/V2/V3 脚本，也不保存任何外部服务凭据。

## 3. MCP 工具目录

### 3.1 法律要素识别

工具名：`lexpro_recognize_legal_elements`

输入 Schema：

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["text"],
  "properties": {
    "text": {"type": "string", "minLength": 5, "maxLength": 60000},
    "caseCause": {"type": "string", "maxLength": 255}
  }
}
```

处理规则：

- 调用现有法律要素识别客户端。
- 要素必须引用输入文本中的准确原文。
- 复用现有法律要素 JSON 校验器，校验引用、偏移量、置信度和字段结构。
- 返回业务结果、识别到的案由和校验警告。
- 不返回完整系统提示词、原始内部请求参数或外部服务认证信息。

### 3.2 文书实体识别

工具名：`lexpro_recognize_entities`

输入 Schema：

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["text"],
  "properties": {
    "text": {"type": "string", "minLength": 1, "maxLength": 60000}
  }
}
```

处理规则：

- 调用现有实体识别客户端。
- 只允许既定实体类型：人员、组织、地点、日期、时间、金额、案号、法条和其他。
- 每个实体的 `text` 必须在原文中出现；偏移量存在时必须精确匹配原文。
- 对实体数量和返回 JSON 大小设置上限。

### 3.3 案件摘要生成

工具名：`lexpro_summarize_case`

输入 Schema：

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["summaryType", "documents"],
  "properties": {
    "summaryType": {"type": "string", "enum": ["FACT", "PROCESS", "CONCLUSION", "FULL"]},
    "documents": {
      "type": "array",
      "minItems": 1,
      "maxItems": 20,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["text"],
        "properties": {
          "documentId": {"type": "string", "maxLength": 100},
          "text": {"type": "string", "minLength": 1, "maxLength": 60000}
        }
      }
    }
  }
}
```

处理规则：

- 将 MCP 文档输入转换为现有 `CaseSummarySource`。
- 校验汇总文本总长度，避免单个文档限制分别通过但整体超限。
- 输出 `summaryText`、摘要类型和来源标识。
- 不把文档 ID、数据库主键或实现说明写入摘要正文。

### 3.4 典型案例推送

工具名：`lexpro_push_typical_cases`

本轮只注册工具名称和占位说明，不接收业务参数：

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {}
}
```

调用该工具固定返回 MCP 错误结果：

```json
{
  "errorCode": "TYPICAL_CASE_PUSH_NOT_IMPLEMENTED",
  "message": "Typical-case push is reserved and not implemented"
}
```

占位工具不读取案件文本、不调用任何 HTTP 服务、不访问数据库、不创建推荐记录。后续正式立项时，再依据典型案例接口文档单独确定 `analyze/search` 契约、认证、超时、响应过滤和联调方案。

## 4. 认证、权限和数据安全

### 4.1 外部调用身份

外部项目不模拟 LexPro 普通用户登录，也不复用网页登录 JWT。`/mcp` 使用独立的 MCP 服务令牌认证：

- 每个调用项目分配唯一 `clientId`，一个项目不得与另一个项目共用令牌。
- 调用方只持有一次性生成的高熵明文令牌；服务端注册表只保存 SHA-256 摘要，不保存明文。
- 注册表放在服务器的密钥挂载文件或密钥管理系统中，不进入 Git、镜像、普通配置文件或日志。
- 认证时先计算令牌摘要，再使用 `MessageDigest.isEqual` 做常量时间比较。
- 令牌只允许通过 `Authorization: Bearer <token>` 传递，不接受 URL 查询参数、Cookie 或工具参数中的令牌。
- 注册项至少包含 `clientId`、`tokenId`、`tokenSha256`、`authorities`、`enabled` 和 `expiresAt`；允许同一 `clientId` 暂时保留两个有效 `tokenId`，用于无停机轮换。
- 令牌撤销通过禁用或删除对应注册项完成；注册表变更后应在 30 秒内热加载，加载失败时继续使用最后一份有效配置并触发告警。

`/mcp` 使用单独且优先级更高的 Spring Security 过滤链。该过滤链只接受 MCP 服务令牌；普通 `/api/v1/**` 继续使用现有用户 JWT，两类身份不会互相兜底或混用。

### 4.2 服务主体上下文

现有 `McpRequestContext(long userId, ...)` 改为服务主体上下文：

```java
public record McpRequestContext(
        PrincipalType principalType,
        String clientId,
        Long userId,
        Set<String> authorities,
        String requestId) {}
```

本轮四个文本处理工具使用 `principalType=SERVICE`、`clientId=<已认证项目>`、`userId=null`。当前工具不读取 LexPro 案件记录，因此不需要把服务主体伪装成人工用户。未来若增加案件绑定工具，必须另行设计服务账号映射或终端用户委托，不能自动继承全库权限。

### 4.3 工具授权和审计

- 三个 DeepSeek 工具要求服务主体具备 `AI_EXECUTE`。
- 典型案例占位工具只要求认证成功；正式实现时再根据实际契约补充 `RECOMMENDATION_USE`、`AI_EXECUTE` 和案件级访问校验。
- 前三个 DeepSeek 工具调用写入 `operation_log`。服务主体的 `userId` 允许为空，脱敏审计 detail 记录 `principalType`、`clientId`、工具名、结果、错误码和请求 ID。典型案例占位工具不访问数据库，仅写不含参数的应用事件日志。
- 审计和应用日志不保存原始案件文本、完整实体文本、完整摘要输入、令牌、令牌摘要或完整典型案例正文。
- `LEXPRO_AI_ENABLED=false` 或 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false` 时，前三个工具拒绝执行并返回稳定错误码。
- MCP 服务默认关闭，仍由 `LEXPRO_MCP_ENABLED=false` 控制；生产启用前必须先完成 TLS、令牌注册表和数据外发审批。
- 不在代码、配置模板、测试数据或日志中写入任何外部服务器账号、密码、API Key、MCP 明文令牌或 JWT 密钥。

## 5. 代码施工步骤

### 阶段一：冻结契约

1. 在 `McpToolCatalog` 中确定四个工具名、描述、严格 JSON Schema 和字段上限。
2. 确定统一成功响应和错误响应格式。
3. 确定典型案例占位工具的稳定错误码和空输入 Schema。
4. 冻结 MCP 服务令牌注册表格式、服务主体上下文和 HTTP 状态映射。
5. 将本方案作为实现依据，暂不修改数据库结构。

### 阶段二：建立服务器调用安全边界

1. 为 `/mcp` 增加独立 `SecurityFilterChain` 和服务令牌认证过滤器，禁止普通用户 JWT 进入该端点。
2. 实现严格的令牌注册表解析、摘要校验、过期/禁用检查和 30 秒内热加载。
3. 将 `McpRequestContext` 扩展为 `principalType/clientId/可选 userId/authorities/requestId`。
4. 未认证返回 HTTP 401，已认证但无权限返回 MCP 工具错误 `MCP_ACCESS_DENIED`；日志不得输出 Authorization 头。
5. 增加按 `clientId` 的并发上限和速率限制，拒绝时返回 HTTP 429 或 503，不在内存中排队保存原文。

### 阶段三：实现前三个 DeepSeek 工具

1. 在 MCP 服务层注入三个现有 AI client。
2. 增加字符串、数组、枚举、总长度和未知字段校验。
3. 将 client 输出映射为 MCP 安全响应，去除 prompt snapshot、认证信息和不必要的内部参数。
4. 统一处理 AI 未启用、外部数据未获批、超时、响应 JSON 非法和输出超限。
5. 保证异常不向 MCP 客户端泄露堆栈或底层 URL。

### 阶段四：实现典型案例占位工具

1. 在工具目录中注册 `lexpro_push_typical_cases`。
2. 使用空对象 Schema，拒绝所有未定义输入字段。
3. 统一返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`，不创建下游 HTTP 客户端、不增加配置、不访问数据库。
4. 在代码注释和文档中标明未来需要按原始接口文档实现 `analyze/search` 两阶段流程。

### 阶段五：替换旧 MCP 工具目录

1. `tools/list` 只暴露上述四个工具。
2. 旧的案件、卷宗和推荐查询工具不再加入 MCP 工具目录；底层业务代码和测试暂不删除，避免扩大本次变更范围。
3. 更新 MCP Server 的服务描述，明确它提供的是法律智能处理能力。
4. 更新英文架构文档和中文镜像，移除旧工具目录说明。

### 阶段六：服务器部署和联调

1. 构建可执行 JAR，以非 root 系统用户运行 Spring Boot，后端只监听回环地址或服务器私网地址。
2. 使用 Nginx、Caddy 或现有网关提供公网 HTTPS；公网只开放 `/mcp`，后端管理端口和健康检查保持内网可见。
3. 配置请求体上限、连接/读取超时、禁用代理缓冲、并发限制、请求 ID 转发和访问日志脱敏。
4. 通过密钥挂载或密钥管理系统注入 DeepSeek API Key、MCP 令牌注册表和其他运行密钥。
5. 使用一个真实外部 MCP 客户端完成 `initialize -> tools/list -> tools/call` 端到端验收；先用虚构数据做最小联调，确认日志脱敏和审计正确后，可在已批准的 DeepSeek 外发边界内使用真实案件文本验收。

### 阶段七：测试和验收

1. 单元测试：每个工具的成功、缺参、类型错误、未知字段、超长输入和输出超限。
2. 认证和权限测试：无令牌、错误令牌、过期令牌、禁用客户端、缺少 `AI_EXECUTE`、令牌轮换和注册表热加载。
3. AI 客户端测试：模拟 DeepSeek 返回合法 JSON、非法 JSON、证据不匹配和超时。
4. 典型案例占位测试：验证空 Schema、未知字段拒绝和稳定的 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED` 错误。
5. MCP 协议测试：`initialize`、`tools/list`、四个工具的 `tools/call` 和错误序列化。
6. 安全测试：确认响应和审计记录中没有 prompt、Token、API Key、内部表名、内部路径和未授权正文。
7. 部署测试：从服务器外部通过 HTTPS 验证 401/403/429/正常调用、超时、并发限制、请求 ID、健康检查隔离和日志脱敏。
8. 构建验证：运行后端 focused tests，再执行 Maven package；不进行真实案件文本的 DeepSeek 正向调用，除非另行批准。

## 6. 预计改动文件

- `backend/lexpro-backend/src/main/java/.../mcp/config/McpServerConfig.java`
- `backend/lexpro-backend/src/main/java/.../mcp/config/McpProperties.java`
- `backend/lexpro-backend/src/main/java/.../mcp/config/McpSecurityConfig.java`
- `backend/lexpro-backend/src/main/java/.../mcp/security/` 下的服务令牌认证与注册表组件
- `backend/lexpro-backend/src/main/java/.../mcp/McpRequestContext.java`
- `backend/lexpro-backend/src/main/java/.../mcp/service/McpToolCatalog.java`
- `backend/lexpro-backend/src/main/java/.../mcp/service/McpToolService.java`
- `backend/lexpro-backend/src/main/resources/application.properties`
- `backend/lexpro-backend/.env.example`（只列变量名和占位说明）
- `backend/lexpro-backend/src/test/java/.../mcp/` 下的现有测试
- `docs/AI_RETRIEVAL_AND_MCP_ARCHITECTURE.md`
- `docs/zh-CN/AI_RETRIEVAL_AND_MCP_ARCHITECTURE.md`

不新增表、不执行迁移、不删除现有业务服务，不把典型案例服务的内部数据库表直接暴露给 MCP。

## 7. 验收标准

- MCP 成功发现且只显示四个工具。
- 未携带服务令牌、令牌错误、过期或已撤销时返回 HTTP 401；不同 `clientId` 的权限和审计相互隔离。
- 三个 DeepSeek 工具能够在模拟客户端下返回符合 Schema 的结构化结果。
- 法律要素和实体的原文引用、偏移量、置信度均通过校验。
- 摘要类型和多文档输入限制生效。
- 典型案例工具可被发现，但调用只返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`，且不产生下游请求或业务数据。
- 无权限请求被拒绝；前三个 DeepSeek 工具写入审计记录，典型案例占位工具保持零数据库访问。
- 响应不包含 Token、API Key、内部表名、内部路径、查询向量或未授权敏感文本。
- 公网只能通过 HTTPS 访问 `/mcp`，不能直接访问 Spring Boot 监听端口；代理和应用超时配置一致。
- 令牌可轮换、可撤销，注册表变更在约定时间内生效，且令牌和摘要不会进入日志。
- 后端 focused tests、Maven package、MCP 协议级 HTTP 测试和一款真实外部客户端的服务器联调通过。

## 8. 必须在联调前确认的事项

- 未来实现典型案例时，下游服务实际是否接收 `analysis_id`，以及分析结果的保存周期。
- 未来实现典型案例时，下游服务的认证方式、TLS 和网络访问边界。
- 未来实现典型案例时，MCP 是否允许返回案例正文，正文单条和总长度上限是多少。
- 真实案件文本发送给 DeepSeek 已于 2026-08-12 获得批准；部署时仍须显式启用外发开关并完成日志脱敏检查。
- 最终使用哪个 MCP 客户端进行 Streamable HTTP 验收。
- 服务器已有的 HTTPS 反向代理/网关类型，以及允许访问 `/mcp` 的来源网段或调用方出口 IP。
- 首批外部项目的 `clientId`、所需工具权限、并发配额和令牌有效期；这里只记录标识和策略，不记录明文令牌。

## 9. 技术实现补充

### 9.1 MCP 请求调用链

请求处理顺序固定为：

```text
HTTP POST /mcp
  -> MCP 专用 SecurityFilterChain 提取 Bearer 服务令牌
  -> McpTokenAuthenticator 校验摘要、有效期、启用状态和权限
  -> 创建 McpServiceAuthenticationToken
  -> WebMvcStatelessServerTransport 提取 McpTransportContext
  -> McpRequestContext.from(context)
  -> McpToolCatalog 匹配工具名
  -> McpToolService 校验权限和 arguments
  -> AI client 同步调用（前三个工具）
  -> 安全结果映射和 JSON 序列化
  -> 输出大小检查
  -> operation_log 审计
  -> McpSchema.CallToolResult
```

工具处理必须继续使用现有的统一 `invoke(...)` 包装，保证成功、业务异常、输入异常和未知异常都经过同一套审计和错误序列化逻辑。未知异常只记录服务端日志，不把堆栈、URL 或底层异常消息返回给调用方。

### 9.2 建议的 Java 职责划分

`McpToolCatalog` 只负责工具元数据，不执行业务逻辑：

- 工具名称、标题、描述；
- 严格输入 Schema；
- `SyncToolSpecification` 到服务方法的绑定。

`McpToolService` 负责 MCP 边界逻辑：

- `requireAuthorities(caller, ...)` 权限检查；
- `stringArg`、`integerArg`、数组和对象参数解析；
- 调用现有 `EntityRecognitionClient`、`LegalElementRecognitionClient`、`CaseSummaryClient`；
- 将 AI 输出映射为白名单响应；
- 调用占位典型案例工具并返回固定错误；
- 统一审计和错误处理。

如果 `McpToolService` 的构造函数因注入三个 AI client 变得过长，可以新增一个窄接口 `McpAiToolFacade`，但它只能封装 AI 调用和结果映射，不得创建第二套权限或审计逻辑。

### 9.3 工具方法契约

服务层方法继续使用 MCP SDK 的标准签名：

```java
CallToolResult recognizeLegalElements(
        McpTransportContext context,
        McpSchema.CallToolRequest request);

CallToolResult recognizeEntities(
        McpTransportContext context,
        McpSchema.CallToolRequest request);

CallToolResult summarizeCase(
        McpTransportContext context,
        McpSchema.CallToolRequest request);

CallToolResult pushTypicalCases(
        McpTransportContext context,
        McpSchema.CallToolRequest request);
```

前三个方法执行顺序为“权限检查 -> 参数解析 -> AI 开关检查 -> client 调用 -> 结果校验/映射”。典型案例方法执行“认证上下文解析 -> 确认无未知参数 -> 返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`”，不得访问 AI 或网络服务。

### 9.4 成功响应结构

前三个工具的 `structuredContent` 和文本内容使用同一份 JSON，避免不同 MCP 客户端解析出不同结果。建议结构如下：

法律要素：

```json
{
  "tool": "lexpro_recognize_legal_elements",
  "schemaVersion": "element-result-v1",
  "model": "configured-model",
  "data": {
    "caseCause": null,
    "elements": [],
    "validation": {"warnings": [], "unsupportedClaims": []}
  }
}
```

实体识别：

```json
{
  "tool": "lexpro_recognize_entities",
  "schemaVersion": "entity-result-v1",
  "model": "configured-model",
  "data": {"entities": []}
}
```

案件摘要：

```json
{
  "tool": "lexpro_summarize_case",
  "schemaVersion": "case-summary-v1",
  "model": "configured-model",
  "data": {
    "summaryType": "FULL",
    "summaryText": "...",
    "documentIds": []
  }
}
```

`promptSnapshot`、完整 generation parameters、Token 使用明细和原始供应商响应默认不返回；如将来需要监控，应使用脱敏后的服务端指标或单独的管理员接口。

### 9.5 错误码和映射

| 错误码 | 触发条件 | MCP `isError` |
|---|---|---|
| `MCP_INPUT_INVALID` | 缺少参数、类型错误、未知字段、超过长度或数量限制 | `true` |
| `MCP_ACCESS_DENIED` | 服务令牌有效但缺少工具所需权限 | `true` |
| `AI_PROVIDER_DISABLED` | `LEXPRO_AI_ENABLED=false` | `true` |
| `AI_DATA_EXPORT_DISABLED` | 未批准向外部模型发送案件文本 | `true` |
| `AI_INPUT_TOO_LARGE` | 输入或摘要合计文本超过模型限制 | `true` |
| `AI_RESPONSE_INVALID` | 模型返回无法通过结构化校验的 JSON | `true` |
| `MCP_OUTPUT_LIMIT_EXCEEDED` | 序列化后的结果超过 MCP 输出上限 | `true` |
| `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED` | 典型案例占位工具被调用 | `true` |
| `MCP_TOOL_FAILED` | 未分类的服务端异常 | `true` |

错误响应固定为：

```json
{
  "errorCode": "MCP_INPUT_INVALID",
  "message": "Human-readable safe message"
}
```

错误消息不能包含原始文本、Token、API Key、远程 URL、堆栈或数据库异常详情。

### 9.6 超时、并发和大小限制

- MCP 请求超时必须大于单次 AI 读取超时，并预留序列化和审计时间；当前默认值存在不一致风险，实施时应在配置校验中阻止“外层超时小于内层 AI 超时”的组合。
- 不在 MCP 层增加无限队列。同步调用占用请求线程，应用使用有界信号量限制全局和单个 `clientId` 的在途 AI 调用数，反向代理再限制连接和请求速率；超过容量返回 429 或 503，而不是排队保存原文。
- 文本最大长度统一以 `AiProcessingProperties.maxInputChars` 为上限；摘要还要检查所有文档拼接后的总长度。
- `McpProperties.maxOutputChars` 是最终 JSON 序列化后的硬上限；不能只按对象数量估算。
- 实体、法律要素数组和摘要文档数量使用固定上限，拒绝超限请求，不自动截断用户输入。

### 9.7 审计字段

前三个 DeepSeek 工具每次调用记录一条 `MCP_TOOL_CALLED`；典型案例占位工具不写数据库审计：

```text
principalType SERVICE
clientId     已认证的外部项目标识
userId       本轮为空
caseId       本轮为空（MCP 直接处理文本，不绑定案件）
tool         MCP 工具名
result       SUCCESS 或 FAILED
errorCode    失败时填写
requestId    HTTP 请求 ID
transport    STREAMABLE_HTTP_STATELESS
```

审计 detail 只记录 `principalType`、`clientId`、工具名、结果和错误码，不记录工具 arguments。`clientId` 只能来自已经校验的注册表，写入前仍需限制字符集和长度。这样既能追踪调用，又不会把案件原文写入操作日志。

### 9.8 测试用例矩阵

| 测试层 | 最小覆盖 |
|---|---|
| 工具目录 | 只返回四个工具；名称、描述和 Schema 与方案一致 |
| 参数解析 | 缺失必填字段、错误类型、未知字段、空白字符串、超长文本、超限文档数组 |
| 认证/权限 | 无令牌、错误/过期/禁用令牌、无 `AI_EXECUTE`、占位工具已认证调用、不同 `clientId` 隔离 |
| AI client | 合法 JSON、非法 JSON、原文引用不匹配、偏移量错误、供应商超时 |
| 摘要 | 四种摘要类型、多文档合计长度、重复 documentId 的处理 |
| 占位工具 | 空对象成功解析；任意业务字段被拒绝；返回固定未实现错误码；没有网络调用 |
| 协议 | `initialize`、`tools/list`、`tools/call`、`isError` 和结构化内容一致性 |
| 安全 | 响应和审计均不包含原文输入、Token、API Key、prompt 或内部路径 |

### 9.9 配置变更原则

前三个工具直接复用现有 `lexpro.ai.*` 配置，不新增供应商专用配置。典型案例占位阶段不新增 `LEXPRO_TYPICAL_CASE_*` 环境变量。只有未来正式接入典型案例服务时，才单独增加 base URL、超时、启用开关和认证配置，并更新配置文档和人工操作清单。

计划新增的 MCP 配置只描述运行策略，不包含任何真实密钥：

| 环境变量 | 用途 | 建议默认值 |
|---|---|---|
| `LEXPRO_MCP_CLIENT_REGISTRY_PATH` | 密钥挂载的客户端注册表绝对路径 | 无默认值，启用 MCP 时必填 |
| `LEXPRO_MCP_TOKEN_RELOAD_INTERVAL` | 注册表变更检查间隔 | `PT30S` |
| `LEXPRO_MCP_MAX_CONCURRENT_REQUESTS` | 全局在途 AI 调用上限 | `8` |
| `LEXPRO_MCP_MAX_CONCURRENT_PER_CLIENT` | 单个客户端在途 AI 调用上限 | `2` |
| `LEXPRO_MCP_RATE_LIMIT_PER_MINUTE` | 单个客户端每分钟调用上限 | `60` |

注册表只保存令牌摘要，建议结构如下；示例值必须保持占位形式：

```json
{
  "schemaVersion": "mcp-client-registry-v1",
  "clients": [
    {
      "clientId": "project-a",
      "enabled": true,
      "authorities": ["AI_EXECUTE"],
      "tokens": [
        {
          "tokenId": "rotation-1",
          "tokenSha256": "<64位十六进制摘要>",
          "expiresAt": "<ISO-8601时间>"
        }
      ]
    }
  ]
}
```

## 10. 服务器部署方案

### 10.1 部署拓扑

```text
外部 MCP 客户端
  -> HTTPS 443 /mcp
  -> 反向代理或现有 API 网关
  -> HTTP 127.0.0.1:8080 /mcp
  -> Spring Boot MCP Server
  -> HTTPS DeepSeek API（仅前三个工具）
```

- Spring Boot 以非 root 用户运行，只监听 `127.0.0.1` 或受控私网地址；不得把 `8080` 直接暴露到公网。
- 反向代理终止 TLS，只转发 `/mcp`；健康检查、Swagger 和普通业务 API 不随 MCP 域名公开。
- TLS 最低使用 1.2，证书由现有证书平台或 ACME 管理；部署文档只记录证书路径变量，不提交私钥。
- 代理请求体上限建议为 1 MiB，足以覆盖 60000 字符输入及 JSON 开销，同时阻止无界请求。
- 代理读取超时应大于应用 MCP 超时至少 10 秒；建议 AI 读取超时 45 秒、MCP 超时 55 秒、代理读取超时 65 秒，并在启动校验中保证由内到外递增。
- Streamable HTTP 需要保留 `POST`、`Content-Type`、`Accept` 和 MCP 协议头，关闭代理响应缓冲，保持 HTTP/1.1 长连接。
- 反向代理覆盖外部传入的 `X-Forwarded-*`，只信任代理自身添加的头；`X-Request-Id` 缺失时由代理或应用生成。
- CORS 不是服务器间认证手段；MCP 域名不为任意浏览器来源开放跨域。

### 10.2 进程与配置

1. 使用 Java 21 构建可执行 JAR，运行前先执行 focused tests 和 Maven package。
2. 使用 systemd、Windows Service 或现有容器平台托管进程，配置自动重启、优雅停机和资源上限。
3. 普通非敏感参数可使用环境变量；DeepSeek API Key、MCP 客户端注册表和数据库凭据必须由密钥管理系统或权限受控的挂载文件提供。
4. 服务启动时对 MCP 端点、超时关系、注册表路径、注册表 Schema、重复 `clientId/tokenId` 和过期时间执行 fail-fast 校验。
5. 发布采用滚动或蓝绿方式；新实例先通过内部健康检查，再加入代理。停机时停止接收新请求并等待在途 AI 调用在超时内结束。

### 10.3 健康、日志和指标

- 存活检查只验证 Java 进程；就绪检查验证 MCP 配置已加载，不能通过发送真实案件文本探测 DeepSeek。
- 结构化日志至少包含 `requestId`、`clientId`、工具名、耗时、结果和安全错误码，不包含 Authorization、请求正文、模型 prompt 或模型原始响应。
- 指标至少包括按工具/结果统计的调用数、在途调用数、限流数、总耗时、DeepSeek 耗时、超时数、非法模型响应数和输出超限数。
- 告警覆盖连续 5xx/503、DeepSeek 超时率、注册表热加载失败、磁盘日志异常和健康检查失败。
- `clientId` 来自有限注册表，可以作为日志和指标维度；不得把 `requestId`、原文或令牌摘要作为指标标签。

## 11. 外部调用方契约

调用方获得以下非敏感接入信息：MCP HTTPS 地址、`clientId`、允许工具、输入/输出限制、超时和错误码。明文令牌通过独立安全渠道一次性交付，不写入接口文档或工单正文。

标准调用流程：

```text
1. POST /mcp: initialize
2. 发送 initialized 通知（按客户端 SDK 要求）
3. POST /mcp: tools/list
4. POST /mcp: tools/call
5. 读取 structuredContent；isError=true 时按 errorCode 处理
```

每个 HTTP 请求都携带：

```http
Authorization: Bearer <MCP_SERVICE_TOKEN>
Content-Type: application/json
Accept: application/json, text/event-stream
X-Request-Id: <调用方生成的唯一请求ID，可选>
```

调用约束：

- 客户端应使用支持 Streamable HTTP 的 MCP SDK，不应自行拼接不完整的协议消息。
- 401 表示令牌无效、过期或已撤销；403 表示端点级拒绝；429 表示超过客户端配额；503/504 表示临时过载或上游超时。
- MCP `tools/call` 返回 HTTP 200 但 `isError=true` 时属于工具级错误，调用方按 `errorCode` 处理。
- `MCP_INPUT_INVALID`、`MCP_ACCESS_DENIED`、`AI_DATA_EXPORT_DISABLED` 和 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED` 不得自动重试。
- 只有在未收到任何有效响应且遇到 502/503/504 时，客户端才可指数退避后重试一次；三个 AI 工具虽不写业务数据，但重试会产生额外模型费用。
- 调用方不得把 MCP 返回当作法律结论直接生效；结果仍需由其自身业务流程决定是否人工复核。

## 12. 上线验收顺序

1. 使用 mock AI 完成全部单元、认证、协议和并发测试。
2. 在测试环境部署 HTTPS 端点，用两个不同 `clientId` 验证权限、配额、审计和令牌隔离。
3. 执行旧令牌与新令牌重叠、旧令牌撤销、注册表错误回滚和过期令牌测试。
4. 使用虚构法律文本对 DeepSeek 做最小正向联调，并核对三种工具的结构化输出。
5. 从目标外部项目或指定 MCP 客户端完成真实网络链路验收。
6. 检查访问日志、应用日志、`operation_log` 和指标，确认没有正文、令牌、API Key、内部路径或堆栈泄露。
7. 真实案件数据发送给 DeepSeek 已获批准；完成虚构数据联调、日志脱敏和审计核对后，可在生产配置中显式设置 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`。若未设置，前三个工具仍须稳定拒绝外发。

生产可用的完成条件不是“本机能调用”，而是上述验证全部通过：HTTPS 网络、服务身份、工具契约、DeepSeek 联调、容量保护和可观测性。典型案例工具只需满足可发现、固定返回未实现、零下游调用。
