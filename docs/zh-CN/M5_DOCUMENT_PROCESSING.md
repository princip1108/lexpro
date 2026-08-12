# M5 文档解析

[English](../M5_DOCUMENT_PROCESSING.md)

## 已实现范围

### M5-S1 文档解析

- 使用现有 V1/V2 表实现版本化 `document_parse_result` 工作流。
- 提供返回 `202 Accepted` 的启动/重试接口、历史接口和结果详情接口。
- 对单个文件加事务锁，保证唯一当前结果、版本号单调递增，并阻止新鲜任务重复启动。
- 事务提交后才通过有界本地线程池异步调度。
- 稳定的成功/失败状态、队列拒绝处理，以及中断过期任务的重试恢复。
- 建立解析器接口，并提供 UTF-8 `.txt` 开发环境本地解析器。
- 执行案件级权限检查，审计事件保留原始请求 ID。

### M5-S2 实体识别

- 使用已配置地址和模型 ID 的 OpenAI 兼容 DeepSeek 适配器。
- 事务提交后通过有界线程池异步执行；前端收到 `202 Accepted`，并轮询由持久化审计记录推导的任务状态。
- 使用现有 `entity_result` 表分别保存模型原始 JSON 和第一次人工确认 JSON。
- 保存模型、响应模型、提示词/Schema 版本、提示词快照、生成参数、Token 用量、请求 ID 和耗时。
- 生成和确认都执行案件级权限及 `AI_EXECUTE` 检查。
- 供应商、响应和队列失败使用稳定错误码，日志及错误响应不包含密钥、供应商正文、提示词或源文本。
- 不需要迁移：已审查的 V1/V2/V3 表已经包含所需字段。

适配器代码已经实现，但自动化测试和构建都不会向 DeepSeek 发送数据。真实外部传输仍由独立开关默认关闭。

### M5-S3 法律要素

- 复用结构化 JSON AI 传输层，并提供法律要素专用提示词和校验器。
- 使用现有 `legal_element_result` 表保存不可覆盖的模型输出、校验报告、来源信息和第一次人工确认结果。
- 每个要素必须有非空证据列表和准确原文引用。v2 DeepSeek 适配器只要求供应商返回引用，Java 服务端根据原文确定性生成并校验 UTF-16 偏移量后再持久化。
- 文档查询限定同一案件，服务层执行案件权限，生成需要 `AI_EXECUTE`，异步任务状态由审计记录推导。
- 要素代码、置信度、证据或偏移量不合法时，供应商输出不会写入结果表。

### M5-S4 案件摘要

- 明确支持 `FACT`、`PROCESS`、`CONCLUSION` 和 `FULL` 类型，每次使用一到二十个不可重复的指定解析结果。
- 每个来源必须属于目标案件，并且已成功提取文本。
- 版本号按案件和摘要类型分别计算；案件行锁保证版本分配和当前版本切换串行执行。
- 生成期间或生成失败时，旧的当前摘要保持不变；只有新摘要成功写入的事务才切换标记。
- 确认只记录接受生成文本的人员和时间，不覆盖文本；需要调整时重新生成新版本。
- 保存模型来源信息和选中的来源文档 ID，但应用 API 不返回已保存的提示词快照。

## 状态和重试规则

```text
POST 启动 -> PROCESSING -> SUCCESS
                           -> FAILED
FAILED 后再次 POST -> 新的 PROCESSING 版本
新鲜 PROCESSING 时再次 POST -> 409 PARSE_IN_PROGRESS
过期 PROCESSING 时再次 POST -> 旧任务 FAILED，新建 PROCESSING 版本
```

当前结果标记会移动到新版本，旧内容和失败记录保留为不可覆盖的历史。系统不做无限自动重试。

## 本地解析器和配置

内置解析器只读取状态为 `ACTIVE` 的 UTF-8 文本文卷。PDF、Office 和图片在批准 MinerU/文档解析适配器前，会以 `PARSER_UNAVAILABLE_FOR_FILE_TYPE` 失败。

```text
LEXPRO_PROCESSING_CORE_THREADS=2
LEXPRO_PROCESSING_MAX_THREADS=4
LEXPRO_PROCESSING_QUEUE_CAPACITY=50
LEXPRO_PROCESSING_MAX_EXTRACTED_CHARS=2000000
LEXPRO_PROCESSING_STALE_AFTER=PT30M
```

内部对象键只在 Java 内传给解析器。API 返回解析内容和来源信息，不返回对象键或本地路径。队列已满时记录 `PROCESSING_QUEUE_FULL`；未预期内部错误使用稳定的 `DOCUMENT_PROCESSING_FAILED`，堆栈只留在服务端。

## DeepSeek 配置和数据边界

DeepSeek 只处理可信解析后的文本，不能替代 PDF/Office 解析。API Key 只从环境变量读取。`LEXPRO_AI_ENABLED=true` 表示启用适配器，独立的 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true` 才表示允许发送案件解析文本。项目负责人明确接受数据分级和供应商风险前，第二个开关保持 `false`。

```text
LEXPRO_AI_ENABLED=false
LEXPRO_AI_BASE_URL=https://api.deepseek.com
LEXPRO_AI_API_KEY=<本机密钥>
LEXPRO_AI_MODEL=deepseek-v4-flash
LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false
LEXPRO_AI_CONNECT_TIMEOUT=PT5S
LEXPRO_AI_READ_TIMEOUT=PT60S
LEXPRO_AI_MAX_INPUT_CHARS=60000
LEXPRO_AI_MAX_OUTPUT_TOKENS=4096
```

实体协议是包含 `entities` 数组的对象。每个实体必须有 `type` 和准确的原文 `text`；可选偏移量用于追溯原文位置，可选置信度限制为 `0..1`。供应商结果通过校验后才会保存。人工确认只写一次 `final_entities_json`，绝不覆盖 `entities_json`。

法律要素协议是包含非空 `elements` 数组的对象。每个要素使用允许的代码，包含分析内容和至少一条准确原文引用。供应商不得计算偏移量；v2 适配器按引用在原文中的出现顺序定位，写入 Java UTF-16 `startOffset`/`endOffset`，随后依据同一来源文本校验。摘要生成只接受允许的类型和显式选择的同案件成功解析结果。

## M5 后续工作

已完成 S1-S4 代码范围之外仍待完成：

- 批准真实案件数据外发及生产超时/并发上限；当前数值只是开发默认值。
- 增加批准的 PDF/Office 解析器，预计通过规划中的 Python 处理边界实现。

## 人工验收

1. 使用 M4 给某案件上传一个较小的 UTF-8 `.txt` 文件。
2. 使用拥有 `AI_EXECUTE` 且对案件具有 `EDIT` 访问级别的用户调用解析启动接口。
3. 确认响应为 `202` 和 `PROCESSING`，然后轮询返回的 `Location`，直到状态为 `SUCCESS`。
4. 确认结果包含 `rawText`、`parserVersion=local-text/1`、版本和请求 ID，并且不含 `fileUrl` 或存储路径。
5. 完成后再次启动同一文件，确认创建新版本，历史仍保留旧结果。
6. 在 PDF 适配器未配置时尝试解析 PDF，确认任务变成 `FAILED`，错误码为 `PARSER_UNAVAILABLE_FOR_FILE_TYPE`。
7. 保持外发开关关闭，启动实体识别，确认返回 `503 AI_DATA_EXPORT_DISABLED`，且没有供应商调用。
8. 仅使用已经批准的虚构/脱敏测试文本时，把 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`，重启后端，启动识别，轮询返回的任务 `Location`，并检查实体结果历史。
9. 用修改后的 `finalEntities` 确认一条结果，验证模型原始结果未变化；第二次确认应返回 `409 ENTITY_RESULT_ALREADY_CONFIRMED`。
10. 对同一解析文档启动法律要素任务，轮询其 `Location`，确认每个返回要素都有准确原文引用。使用虚构引用或不匹配偏移量确认时，应返回 `400 LEGAL_ELEMENT_RESULT_INVALID`。
11. 成功确认一条合法要素结果，验证模型原始输出未变化；第二次确认应返回 `409 LEGAL_ELEMENT_ALREADY_CONFIRMED`。
12. 使用一个或多个成功的 `sourceDocIds` 启动 `FULL` 摘要，轮询其 `Location`，验证版本 `1` 为当前版本。再次生成后，验证版本 `2` 成为当前版本，版本 `1` 仍保留在历史中。
13. 确认一条摘要一次，验证第二次确认返回 `409 CASE_SUMMARY_ALREADY_CONFIRMED`。制造一次生成失败并验证最后一次成功摘要仍保持当前状态。
