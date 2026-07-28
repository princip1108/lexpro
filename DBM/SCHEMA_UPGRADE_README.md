# LexPro 数据库升级说明

## 最终规模

| 版本 | 新增表 | 累计表数 | 作用 |
|---|---:|---:|---|
| V1 | 21 | 21 | 案件、卷宗、智能结果、案卡、报告、类案和审计 |
| V2 | 5 | 26 | 卷宗目录/标签、案例收藏、报告案例引用 |
| V3 | 6 | 32 | 组织、单角色 RBAC、知识内容和人工待办 |

V2/V3 会修改现有表的字段和约束，但不再为报告修订、模型调用、任务事件、公告、日程、通知等分别建表：

- 报告历史使用现有 `case_report.version_no + is_current`。
- AI 调用信息保存在对应结果、案卡、报告或推荐记录中。
- 待办时间线使用现有 `operation_log`。
- 案件共享使用 `case_assignment` 的 `COLLABORATOR + access_level`。
- 公告、日程和通知不是核心业务数据库范围。

## 功能覆盖

| 项目能力 | 最终数据表 |
|---|---|
| 用户、组织、RBAC | `app_user`、`organization_unit`、`auth_role`、`auth_permission`、`auth_role_permission` |
| 案件、参与人、承办/共享 | `case_record`、`case_party`、`case_assignment` |
| 卷宗、目录、标签、解析版本 | `evidence_file`、`dossier_folder`、`file_tag`、`evidence_file_tag`、`document_parse_result` |
| 实体、法律要素、摘要 | `entity_result`、`legal_element_result`、`case_summary` |
| 案卡回填 | `case_card_fill_task`、`case_card_task_source`、`case_card_field` |
| 模板和审查报告版本 | `report_template`、`case_report`、`report_evidence`、`report_legal_element_result`、`report_typical_case_reference` |
| 典型案例、推荐、收藏 | `typical_case`、`typical_case_content`、`case_recommendation`、`case_recommendation_item`、`typical_case_favorite` |
| 内容管理和人工待办 | `knowledge_content`、`work_task` |
| 统计和操作时间线 | 基于业务表聚合，并使用 `operation_log` 审计 |

以下能力不需要新增业务表：JWT 访问令牌、MinerU/模型服务连接、Word/PDF 导出、对象存储下载地址和统计图表，它们属于后端运行时或派生数据。工作台公告、日程、消息目前只属于前端展示范围；确认成为正式业务后再独立设计。

## 文件和顺序

```bash
psql -v ON_ERROR_STOP=1 -d lexpro -f DBM/lexpro_schema_postgresql.sql
psql -v ON_ERROR_STOP=1 -d lexpro -f DBM/lexpro_schema_upgrade_v2_core.sql
psql -v ON_ERROR_STOP=1 -d lexpro -f DBM/lexpro_schema_upgrade_v3_workspace.sql
```

数据库已经执行 V1 时，只执行 V2、V3。三个文件都是一次性脚本，不可重复执行；脚本均使用事务，失败不会提交部分结构。

## 执行前检查

1. 先备份目标数据库，并在测试库执行。
2. V2 会检查跨案件关联；存在串案数据时外键创建会失败并回滚。
3. V3 会把 `app_user.role` 迁移到 `role_id`，然后删除旧角色和 JSON 权限字段。
4. 如果 `permissions_json` 或 `case_scope` 非空，V3 会中止，必须先人工迁移权限。
5. 如果只有 `case_party.age` 而没有 `birth_date`，V3 会中止，必须先补出生日期。

示例备份：

```bash
pg_dump -Fc -d lexpro -f lexpro_before_upgrade.dump
```

## 自动回填

- `deadline_date` 按 `Asia/Shanghai` 当日 `23:59:59` 转为 `deadline_at`。
- `prosecutor_id` 转为 `case_assignment` 的 `PROSECUTOR` 记录。
- 历史摘要按创建顺序生成 `version_no`。
- 每类案件报告的最高版本标记为 `is_current = true`。
- 报告模板生成稳定 `template_code`，同一模板只保留一个启用版本。
- 解析结果、智能结果和关系表回填案件判别列，并通过组合外键禁止跨案件引用。
- 解析结果增加 `is_current`；V3 删除文件表中可推导的解析状态缓存。
- V3 删除 `case_assignment.is_current`，统一以 `ended_at IS NULL` 表示当前分配。

## 向量配置

向量维度取决于最终 Embedding 模型，迁移没有猜测维度。选定模型后另建迁移：

```sql
ALTER TABLE lexpro.typical_case
    ALTER COLUMN embedding TYPE vector(1024);

ALTER TABLE lexpro.case_recommendation
    ALTER COLUMN query_fact_embedding TYPE vector(1024);

CREATE INDEX ix_typical_case_embedding_hnsw
    ON lexpro.typical_case
    USING hnsw (embedding vector_cosine_ops);
```

必须把 `1024` 换成真实模型维度，两个向量字段保持一致。

## 执行后检查

```sql
SELECT count(*) AS lexpro_table_count
FROM information_schema.tables
WHERE table_schema = 'lexpro' AND table_type = 'BASE TABLE';

SELECT to_regclass('lexpro.dossier_folder');
SELECT to_regclass('lexpro.organization_unit');
SELECT to_regclass('lexpro.auth_role');
SELECT to_regclass('lexpro.work_task');
```

完整执行后 `lexpro_table_count` 应为 `32`。
