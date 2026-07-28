# LexPro 数据库基线

[English](../DATABASE_BASELINE.md)

## 最终基线

- 数据库：PostgreSQL 15+，当前本地开发环境为 PostgreSQL 18。
- 数据库名称：`lexpro`。
- 业务 Schema：`lexpro`。
- 扩展：`public` Schema 中的 `pgvector`。
- 执行全部脚本后的最终表数：32。

三个一次性脚本为：

```text
DBM/lexpro_schema_postgresql.sql             # V1，21 张表
DBM/lexpro_schema_upgrade_v2_core.sql         # V2，增加 5 张表
DBM/lexpro_schema_upgrade_v3_workspace.sql    # V3，增加 6 张表
```

当前开发数据库已经升级完成，不能再次执行这些脚本。

## 验证方法

```sql
SELECT current_database(), current_user;

SELECT count(*)
FROM information_schema.tables
WHERE table_schema = 'lexpro'
  AND table_type = 'BASE TABLE';
```

预期表数为 `32`。其他只读检查位于 `DBM/lexpro_schema_validation.sql` 和 `DBM/SCHEMA_UPGRADE_README.md`。

## 后续 Flyway 策略

1. 将 V1/V2/V3 原样保留为历史基线。
2. 只有在备份和审查完成后，才能把现有非空开发数据库标记为基线版本 3。
3. 新结构修改从 `V4__<description>.sql` 开始。
4. 每个迁移只向前执行；在 PostgreSQL 允许时使用事务，并提供验证 SQL。
5. 迁移必须先在可丢弃或测试数据库执行，再进入开发或生产数据库。
6. 应用启动不能被视为自动批准修改未备份的生产数据库。

## 设计不变量

- 主键使用 identity `bigint`。
- 业务时间使用 `timestamptz`。
- 状态值由数据库 CHECK 约束保护。
- 用户角色和组织已经规范化，每个用户各一个。
- `(实体 ID, case_id)` 组合键用于禁止跨案件关联。
- 当前分配使用 `ended_at IS NULL`。
- 当前解析、报告和摘要版本使用文档中定义的部分唯一索引。
- 为跨案件完整性重复保存的 `case_id` 是有意设计，不是错误冗余。

## 向量字段

在确认 Embedding 模型前，`typical_case.embedding` 和 `case_recommendation.query_fact_embedding` 不固定维度。后续迁移必须为两个字段设置相同维度，并创建与 cosine 距离匹配的 HNSW 索引。
