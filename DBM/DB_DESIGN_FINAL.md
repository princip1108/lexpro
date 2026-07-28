# LexPro 数据库设计最终版

> 文档状态：V1 开发基线
>
> 当前最终结构：依次执行 V1、V2、V3，最终为 32 张表。升级范围、范式调整和执行方式以 `SCHEMA_UPGRADE_README.md` 为准。
>
> 适用范围：案件管理、电子卷宗解析、实体识别、法律要素识别、案卡回填、审查报告、典型案例推荐、操作审计。
>
> 版本：1.0
>
> 说明：本文档取图三的业务范围作为基础，并修正重复实体、缺失实体、悬空外键、错误基数和不可追溯的结果引用。后续代码、接口和迁移脚本应以本文档为准；如需改变字段语义或关系基数，应先更新本文档。

## 1. 设计决策摘要

### 1.1 命名规范

- 数据库表名统一使用小写 `snake_case`。
- 不直接使用 `user`、`case` 等可能与 SQL 关键字冲突的表名。
- 用户表使用 `app_user`，案件表使用 `case_record`。
- 主键统一使用 `<entity>_id`。
- 外键名称使用目标实体语义，例如 `created_by`、`operator_id`、`upload_user_id`、`assignee_id`。
- 时间字段统一使用 `_at` 后缀，并使用带时区的时间类型（如果数据库支持）。

### 1.2 重要业务决策

1. `case_party` 恢复为独立实体，`case_record.suspect_name` 不作为参与人权威数据。
2. `case_card_fill_task` 表示案卡回填任务，不再把任务误命名为实际案卡实体。
3. 一个案件可以有多份报告，使用报告版本管理；不对 `report.case_id` 做唯一约束。
4. 一个卷宗允许多次解析，解析结果按版本保存。
5. 典型案例与真实案件不直接建立 1:1 关系，通过推荐结果和推荐明细建立关系。
6. AI 结果必须保存模型、提示词和结构版本，保证结果可追溯。
7. 多值来源关系使用关系表，不把外键列表作为 JSON 唯一存储方式。

## 2. 最终表清单

### 2.1 业务核心

| 表 | 用途 |
|---|---|
| `app_user` | 用户、角色和权限主体 |
| `case_record` | 案件基本信息和生命周期 |
| `case_party` | 案件参与人，包括嫌疑人、被害人、证人等 |
| `case_assignment` | 案件人员分配及角色历史，可选但推荐 |
| `evidence_file` | 电子卷宗及文件元数据 |
| `document_parse_result` | 卷宗解析结果及解析版本 |

### 2.2 智能处理结果

| 表 | 用途 |
|---|---|
| `entity_result` | 文档实体识别结果 |
| `legal_element_result` | 法律要素识别结果 |
| `case_summary` | 案件摘要结果 |

### 2.3 案卡和报告

| 表 | 用途 |
|---|---|
| `case_card_fill_task` | 案卡生成/回填任务 |
| `case_card_field` | 一次回填任务中的字段明细 |
| `report_template` | 报告模板 |
| `case_report` | 审查报告及其版本 |
| `report_evidence` | 报告引用的卷宗文件 |
| `report_legal_element_result` | 报告引用的法律要素结果 |

### 2.4 典型案例和审计

| 表 | 用途 |
|---|---|
| `typical_case` | 典型案例基础信息和向量 |
| `typical_case_content` | 典型案例正文，一对一扩展表 |
| `case_recommendation` | 一次案件推荐请求及查询快照 |
| `case_recommendation_item` | 推荐结果中的典型案例、排序和分数 |
| `operation_log` | 用户和案件操作审计日志 |

## 3. 表结构定义

以下字段类型为逻辑类型，实际类型按所选数据库映射。`json` 表示结构化 JSON；大段正文使用 `text`；向量使用数据库的向量类型（例如 PostgreSQL `vector(n)`）。

### 3.1 `app_user`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `user_id` | bigint/uuid | 是 | PK |
| `username` | varchar(100) | 是 | UNIQUE，登录名 |
| `password_hash` | varchar(255) | 是 | 只保存哈希，不保存明文密码 |
| `real_name` | varchar(100) | 是 | 展示姓名 |
| `role` | varchar(30) | 是 | `ADMIN`、`USER` 等系统角色 |
| `permissions_json` | json | 否 | 角色之外的额外权限；需定义覆盖规则 |
| `case_scope` | json/varchar | 否 | 数据范围或组织范围 |
| `status` | varchar(20) | 是 | `ACTIVE`、`DISABLED` |
| `created_at` | timestamp | 是 | 默认当前时间 |
| `updated_at` | timestamp | 是 | 更新时刷新 |

建议后续引入独立的角色、权限和组织表；当前版本可先保留 JSON，但必须在服务层明确 `role`、`permissions_json`、`case_scope` 的优先级。

### 3.2 `case_record`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `case_id` | bigint/uuid | 是 | PK |
| `case_name` | varchar(255) | 是 | 案件名称 |
| `case_no` | varchar(100) | 否 | UNIQUE；外部/司法案件编号 |
| `case_type` | varchar(50) | 是 | 案件类型 |
| `case_cause` | varchar(255) | 否 | 案由；不要与案件名称混用 |
| `prosecutor_id` | bigint/uuid | 否 | FK -> `app_user.user_id`；若是承办人，建议改名 `assignee_id` |
| `creator_id` | bigint/uuid | 是 | FK -> `app_user.user_id` |
| `accept_date` | date/timestamp | 否 | 统一日期或时间语义 |
| `deadline_date` | date/timestamp | 否 | 统一日期或时间语义 |
| `case_status` | varchar(30) | 是 | `PENDING`、`PROCESSING`、`CLOSED`、`ARCHIVED` |
| `created_at` | timestamp | 是 | 创建时间 |
| `updated_at` | timestamp | 是 | 更新时间 |

不建议保存 `suspect_name` 作为正式字段；嫌疑人、被害人和证人统一进入 `case_party`。如果为了列表性能保留该字段，必须标注为缓存字段，并明确同步策略。

### 3.3 `case_party`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `party_id` | bigint/uuid | 是 | PK |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `party_name` | varchar(255) | 是 | 姓名或单位名称 |
| `party_role` | varchar(30) | 是 | `SUSPECT`、`VICTIM`、`WITNESS`、`OTHER` |
| `party_type` | varchar(30) | 是 | `PERSON`、`ORGANIZATION` |
| `identity_type` | varchar(30) | 否 | 证件类型 |
| `identity_number` | varchar(100) | 否 | 证件号；涉及隐私时应加密/脱敏 |
| `gender` | varchar(20) | 否 | 适用于自然人 |
| `age` | integer | 否 | 应有非负约束 |
| `description` | text | 否 | 补充描述 |
| `created_at` | timestamp | 是 | 创建时间 |
| `updated_at` | timestamp | 是 | 更新时间 |

### 3.4 `case_assignment`（推荐）

用于替代 `case_record.prosecutor_id` 的单一人员字段，支持人员变更和多角色协作。

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `assignment_id` | bigint/uuid | 是 | PK |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `user_id` | bigint/uuid | 是 | FK -> `app_user.user_id` |
| `assignment_role` | varchar(30) | 是 | `PROSECUTOR`、`ASSIGNEE`、`REVIEWER` |
| `is_current` | boolean | 是 | 当前是否有效 |
| `assigned_at` | timestamp | 是 | 分配时间 |
| `ended_at` | timestamp | 否 | 结束时间 |

### 3.5 `evidence_file`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `dossier_id` | bigint/uuid | 是 | PK；如团队更偏好 `file_id`，全项目统一即可 |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `file_name` | varchar(255) | 是 | 原始文件名 |
| `file_type` | varchar(100) | 是 | MIME 类型或业务类型，需统一定义 |
| `file_url` | text | 是 | 推荐保存对象存储 key/URI，不保存长期公开链接 |
| `upload_user_id` | bigint/uuid | 是 | FK -> `app_user.user_id` |
| `uploaded_at` | timestamp | 是 | 上传时间 |
| `file_size` | bigint | 否 | 字节数 |
| `file_hash` | varchar(128) | 否 | 去重和完整性校验 |
| `parse_status` | varchar(30) | 是 | `PENDING`、`PROCESSING`、`SUCCESS`、`FAILED` |

如果允许多次解析，`parse_status` 应作为当前状态缓存，同时以 `document_parse_result` 保存每次解析历史。

### 3.6 `document_parse_result`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `doc_id` | bigint/uuid | 是 | PK |
| `dossier_id` | bigint/uuid | 是 | FK -> `evidence_file.dossier_id` |
| `version_no` | integer | 是 | 从 1 开始 |
| `parse_status` | varchar(30) | 是 | `PROCESSING`、`SUCCESS`、`FAILED` |
| `parsed_text_json` | json | 否 | 分段、页码、结构化解析结果 |
| `raw_text` | text | 否 | 原始纯文本 |
| `parser_version` | varchar(100) | 否 | 解析器版本 |
| `error_message` | text | 否 | 失败原因 |
| `created_at` | timestamp | 是 | 创建时间 |
| `completed_at` | timestamp | 否 | 完成时间 |

约束：`UNIQUE(dossier_id, version_no)`。当前版本可通过最大版本号或 `is_current` 查询。

### 3.7 `entity_result`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `entity_result_id` | bigint/uuid | 是 | PK |
| `doc_id` | bigint/uuid | 是 | FK -> `document_parse_result.doc_id` |
| `entities_json` | json | 是 | 实体列表及实体属性 |
| `model_name` | varchar(100) | 否 | 模型名称 |
| `model_version` | varchar(100) | 否 | 模型版本 |
| `schema_version` | varchar(50) | 否 | JSON 结构版本 |
| `created_by` | bigint/uuid | 否 | FK -> `app_user.user_id`；系统任务可为空 |
| `created_at` | timestamp | 是 | 创建时间 |

不建议同时保存 `case_id`；案件可通过 `doc_id` 关联得到。若保留，必须校验与文档所属案件一致。

### 3.8 `legal_element_result`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `element_result_id` | bigint/uuid | 是 | PK |
| `doc_id` | bigint/uuid | 是 | FK -> `document_parse_result.doc_id` |
| `case_cause` | varchar(255) | 否 | 识别出的案由 |
| `raw_elements_json` | json | 是 | 原始法律要素 |
| `final_elements_json` | json | 否 | 修订或确认后的要素 |
| `validation_report_json` | json | 否 | 校验报告 |
| `model_name` | varchar(100) | 否 | 模型名称 |
| `model_version` | varchar(100) | 否 | 模型版本 |
| `schema_version` | varchar(50) | 否 | JSON 结构版本 |
| `created_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `created_at` | timestamp | 是 | 创建时间 |
| `confirmed_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `confirmed_at` | timestamp | 否 | 确认时间 |

### 3.9 `case_summary`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `summary_id` | bigint/uuid | 是 | PK |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `summary_type` | varchar(30) | 是 | `FACT`、`PROCESS`、`CONCLUSION`、`FULL` |
| `summary_text` | text | 是 | 摘要正文 |
| `model_name` | varchar(100) | 否 | 生成模型 |
| `model_version` | varchar(100) | 否 | 模型版本 |
| `is_current` | boolean | 是 | 当前版本 |
| `created_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `created_at` | timestamp | 是 | 创建时间 |

建议约束：同一案件、同一 `summary_type` 至多一个 `is_current = true`。

### 3.10 `case_card_fill_task`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `fill_task_id` | bigint/uuid | 是 | PK |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `fill_mode` | varchar(30) | 是 | `AUTO`、`MANUAL`、`HYBRID` |
| `fill_status` | varchar(30) | 是 | `PENDING`、`PROCESSING`、`DRAFT`、`CONFIRMED`、`FAILED` |
| `operator_id` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `created_at` | timestamp | 是 | 创建时间 |
| `confirmed_at` | timestamp | 否 | 确认时间 |
| `confirmed_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |

### 3.11 `case_card_field`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `field_id` | bigint/uuid | 是 | PK |
| `fill_task_id` | bigint/uuid | 是 | FK -> `case_card_fill_task.fill_task_id` |
| `field_code` | varchar(100) | 是 | 稳定的程序字段编码 |
| `field_name` | varchar(255) | 是 | 展示名称 |
| `field_value` | text | 否 | 展示值 |
| `field_value_json` | json | 否 | 复杂值；与 `field_value` 二选一或定义优先级 |
| `source_text` | text | 否 | 来源原文片段 |
| `source_file_id` | bigint/uuid | 否 | FK -> `evidence_file.dossier_id` |
| `source_location_json` | json | 否 | 页码、段落、字符位置 |
| `confidence` | decimal(5,4) | 否 | 0 到 1 |
| `confirm_status` | varchar(30) | 是 | `UNCONFIRMED`、`CONFIRMED`、`REJECTED` |
| `confirmed_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `confirmed_at` | timestamp | 否 | 确认时间 |

### 3.12 `report_template`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `template_id` | bigint/uuid | 是 | PK |
| `template_name` | varchar(255) | 是 | 模板名称 |
| `template_type` | varchar(50) | 是 | 报告类型 |
| `template_content_json` | json | 是 | 模板结构 |
| `version_no` | integer | 是 | 模板版本 |
| `status` | varchar(20) | 是 | `DRAFT`、`ACTIVE`、`DISABLED` |
| `created_by` | bigint/uuid | 是 | FK -> `app_user.user_id` |
| `created_at` | timestamp | 是 | 创建时间 |

### 3.13 `case_report`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `report_id` | bigint/uuid | 是 | PK |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `template_id` | bigint/uuid | 否 | FK -> `report_template.template_id` |
| `version_no` | integer | 是 | 案件报告版本 |
| `report_type` | varchar(50) | 是 | 报告类型 |
| `generate_mode` | varchar(30) | 是 | `AUTO`、`MANUAL`、`HYBRID` |
| `operator_id` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `report_status` | varchar(30) | 是 | `GENERATING`、`DRAFT`、`REVIEWING`、`FINAL`、`FAILED` |
| `report_title` | varchar(255) | 是 | 报告标题 |
| `report_content_json` | json | 否 | 结构化报告内容 |
| `error_message` | text | 否 | 生成失败原因 |
| `generated_at` | timestamp | 否 | 生成时间 |
| `updated_at` | timestamp | 是 | 更新时间 |
| `finalized_at` | timestamp | 否 | 定稿时间 |
| `finalized_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |

约束：`UNIQUE(case_id, report_type, version_no)`。

### 3.14 `report_evidence`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `report_id` | bigint/uuid | 是 | PK/FK 组成部分 |
| `dossier_id` | bigint/uuid | 是 | PK/FK 组成部分 |
| `sort_no` | integer | 否 | 报告中展示顺序 |

主键建议为 `(report_id, dossier_id)`。

### 3.15 `report_legal_element_result`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `report_id` | bigint/uuid | 是 | PK/FK 组成部分 |
| `element_result_id` | bigint/uuid | 是 | PK/FK 组成部分 |

如果报告永远只引用一个法律要素结果，可以把该字段直接放在 `case_report`；如果可能引用多个结果，使用本表。

### 3.16 `typical_case`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `typical_case_id` | bigint/uuid | 是 | PK |
| `external_case_id` | varchar(100) | 否 | 外部案例编号，建议 UNIQUE |
| `title` | varchar(255) | 是 | 案例标题 |
| `case_cause` | varchar(255) | 否 | 案由 |
| `case_cause_full_json` | json | 否 | 完整案由结构 |
| `case_type` | varchar(50) | 否 | 案件类型 |
| `country` | varchar(100) | 否 | 国家/地区 |
| `court` | varchar(255) | 否 | 法院 |
| `court_level` | varchar(50) | 否 | 法院层级 |
| `doc_type` | varchar(50) | 否 | 文书类型 |
| `dispute_focus_json` | json | 否 | 争议焦点 |
| `judgment_date` | date | 否 | 裁判日期 |
| `procedure` | varchar(100) | 否 | 审理程序 |
| `applicable_law_json` | json | 否 | 适用法律 |
| `case_level` | varchar(50) | 否 | 案例等级 |
| `embedding` | vector(n) | 否 | 向量维度必须固定 |
| `created_at` | timestamp | 是 | 创建时间 |
| `updated_at` | timestamp | 是 | 更新时间 |

### 3.17 `typical_case_content`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `typical_case_id` | bigint/uuid | 是 | PK，同时 FK -> `typical_case.typical_case_id` |
| `content` | text | 否 | 案例正文 |
| `fact` | text | 否 | 事实部分 |

该表与 `typical_case` 是严格 1:1，使用主键同时作为外键。

### 3.18 `case_recommendation`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `recommend_id` | bigint/uuid | 是 | PK |
| `case_id` | bigint/uuid | 是 | FK -> `case_record.case_id` |
| `source_summary_id` | bigint/uuid | 否 | FK -> `case_summary.summary_id` |
| `query_fact_text` | text | 否 | 推荐时的事实快照 |
| `query_fact_embedding` | vector(n) | 否 | 查询向量 |
| `query_dispute_focus_json` | json | 否 | 查询争议焦点快照 |
| `model_name` | varchar(100) | 否 | 推荐模型 |
| `model_version` | varchar(100) | 否 | 推荐模型版本 |
| `created_by` | bigint/uuid | 否 | FK -> `app_user.user_id` |
| `created_at` | timestamp | 是 | 创建时间 |

### 3.19 `case_recommendation_item`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `item_id` | bigint/uuid | 是 | PK |
| `recommend_id` | bigint/uuid | 是 | FK -> `case_recommendation.recommend_id` |
| `typical_case_id` | bigint/uuid | 是 | FK -> `typical_case.typical_case_id` |
| `rank_no` | integer | 是 | 排名，从 1 开始 |
| `similarity_score` | decimal(8,6) | 否 | 相似度 |
| `reason_json` | json | 否 | 推荐理由 |

约束：`UNIQUE(recommend_id, rank_no)`，`UNIQUE(recommend_id, typical_case_id)`。

### 3.20 `operation_log`

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---:|---|
| `log_id` | bigint/uuid | 是 | PK |
| `user_id` | bigint/uuid | 否 | FK -> `app_user.user_id`；系统任务可为空 |
| `case_id` | bigint/uuid | 否 | FK -> `case_record.case_id`；用户级操作可为空 |
| `operation_type` | varchar(50) | 是 | 操作类型 |
| `object_type` | varchar(50) | 否 | 操作对象类型 |
| `object_id` | varchar(100) | 否 | 操作对象 ID |
| `operation_result` | varchar(30) | 是 | `SUCCESS`、`FAILED` |
| `operation_time` | timestamp | 是 | 操作时间 |
| `detail` | json/text | 否 | 操作详情 |
| `request_id` | varchar(100) | 否 | 请求链路 ID |

操作日志建议只追加、不更新；案件删除时不要级联删除日志。

## 4. 最终关系基数

| 关系 | 基数 |
|---|---:|
| `app_user` -> `case_record`（创建人） | 1:N |
| `app_user` -> `evidence_file`（上传人） | 1:N |
| `case_record` -> `case_party` | 1:N |
| `case_record` -> `evidence_file` | 1:N |
| `evidence_file` -> `document_parse_result` | 1:N |
| `document_parse_result` -> `entity_result` | 1:N |
| `document_parse_result` -> `legal_element_result` | 1:N |
| `case_record` -> `case_summary` | 1:N |
| `case_record` -> `case_card_fill_task` | 1:N |
| `case_card_fill_task` -> `case_card_field` | 1:N |
| `case_record` -> `case_report` | 1:N |
| `case_report` <-> `evidence_file` | N:M，通过 `report_evidence` |
| `case_report` <-> `legal_element_result` | N:M，通过 `report_legal_element_result` |
| `typical_case` -> `typical_case_content` | 1:1 |
| `case_record` -> `case_recommendation` | 1:N |
| `case_recommendation` -> `case_recommendation_item` | 1:N |
| `case_recommendation_item` -> `typical_case` | N:1 |
| `case_record` -> `operation_log` | 1:N |

## 5. 删除或避免的设计

以下设计不应进入最终数据库：

- 重复的 `case_card_field` 表。
- 图二中重复的 `document_parse_result` 表。
- `case_record` 与 `typical_case` 的直接 1:1 外键关系。
- `source_result_id` 这种没有目标表的模糊外键。
- `report.template_id` 在没有 `report_template` 表时直接使用。
- 用 `dossier_ids_json` 代替报告与卷宗的关系表。
- 用户密码明文 `password`。
- 仅用 `case.suspect_name` 表示所有案件参与人。

## 6. 推荐开发顺序

### 第一阶段：核心业务

先实现：

```text
app_user
case_record
case_party
evidence_file
operation_log
```

### 第二阶段：文档处理

再实现：

```text
document_parse_result
entity_result
legal_element_result
case_summary
```

### 第三阶段：案卡和报告

然后实现：

```text
case_card_fill_task
case_card_field
report_template
case_report
report_evidence
report_legal_element_result
```

### 第四阶段：典型案例推荐

最后实现：

```text
typical_case
typical_case_content
case_recommendation
case_recommendation_item
```

## 7. 开发交接检查表

- [ ] 确认数据库类型、主键类型和向量扩展。
- [ ] 确认所有状态枚举及非法状态转换。
- [ ] 确认 `prosecutor_id` 是否实际表示承办人。
- [ ] 确认案件编号是否全局唯一。
- [ ] 确认案件参与人身份证号是否需要加密或脱敏。
- [ ] 确认卷宗解析是否支持多版本。
- [ ] 确认 AI 结果是否允许人工修订及如何记录修订人。
- [ ] 确认报告是否支持草稿、定稿和历史版本。
- [ ] 确认报告模板表及模板版本规则。
- [ ] 确认推荐结果是否需要保存历史快照。
- [ ] 确认所有 JSON 字段的结构版本。
- [ ] 为所有外键、状态筛选字段和时间筛选字段建立索引。
- [ ] 明确删除策略；案件和操作日志不建议物理级联删除。
- [ ] 先编写迁移脚本和种子数据，再编写业务接口。

## 8. 变更规则

以后如果出现以下变更，必须同步修改本文档和数据库迁移：

- 新增或删除表、字段、索引、外键。
- 修改字段含义或可空性。
- 修改任何关系基数。
- 修改状态枚举。
- 修改 JSON 结构、向量维度或模型版本字段。
- 修改报告、案卡、解析结果的版本策略。

本文档是数据库和服务接口之间的共同契约。代码实现与文档不一致时，应优先提交设计变更，而不是在代码中保留未说明的隐式行为。

## 9. PostgreSQL 物理实现

对应的 PostgreSQL 建表脚本为：

```text
lexpro_schema_postgresql.sql
```

物理实现约定：

- 目标版本为 PostgreSQL 15 或更高版本。
- 主键使用 `bigint GENERATED BY DEFAULT AS IDENTITY`。
- 时间字段使用 `timestamptz`。
- JSON 字段使用 `jsonb`。
- 向量字段使用 `pgvector` 的 `vector` 类型。
- 全部业务表位于 `lexpro` schema。
- 状态值使用 `varchar + CHECK`，避免 PostgreSQL enum 给后续迁移带来额外限制。
- SQL 脚本包含外键索引、状态索引、唯一约束、检查约束和 `updated_at` 触发器。

执行前需要在 PostgreSQL 服务器安装 `pgvector`。创建数据库后执行：

```bash
psql -v ON_ERROR_STOP=1 -d lexpro -f lexpro_schema_postgresql.sql
```

### 9.1 案卡任务来源补充表

物理模型增加 `case_card_task_source`，用于替代无法建立真实外键的 `source_result_id`。

每条来源记录只能引用以下一种来源：

```text
document_parse_result
entity_result
legal_element_result
case_summary
```

数据库通过 `num_nonnulls(...) = 1` 检查约束保证恰好选择一种来源，并通过外键保证来源记录存在。

关系为：

```text
case_card_fill_task 1:N case_card_task_source
```

### 9.2 报告与案卡任务

`case_report.card_fill_task_id` 可选引用 `case_card_fill_task.fill_task_id`，用于记录报告生成时使用的案卡任务。应用层必须验证报告和案卡任务属于同一个案件。

### 9.3 向量维度和索引

当前 SQL 使用不限定维度的 `vector`，这样在嵌入模型尚未确定时仍可完成建表。上线向量检索前必须确定统一维度，例如 `1024` 或 `1536`，再执行类似迁移：

```sql
ALTER TABLE lexpro.typical_case
    ALTER COLUMN embedding TYPE vector(1024);

ALTER TABLE lexpro.case_recommendation
    ALTER COLUMN query_fact_embedding TYPE vector(1024);

CREATE INDEX ix_typical_case_embedding_hnsw
    ON lexpro.typical_case
    USING hnsw (embedding vector_cosine_ops);
```

实际维度必须以选定模型输出为准，不能直接照抄示例中的 `1024`。两个向量字段必须使用相同维度和相同距离算法。
