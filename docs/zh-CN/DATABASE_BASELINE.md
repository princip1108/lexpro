# LexPro 数据库基线

[English](../DATABASE_BASELINE.md)

## 最终基线

- 数据库：PostgreSQL 15+，当前本地开发环境为 PostgreSQL 18。
- 数据库名称：`lexpro`。
- 业务 Schema：`lexpro`。
- 扩展：`public` Schema 中的 `pgvector`。
- 执行全部脚本后的最终业务表数：32。
- 完成 Flyway 基线登记后，Schema 中包含 32 张业务表以及 1 张基础设施表 `flyway_schema_history`。

三个一次性脚本为：

```text
DBM/lexpro_schema_postgresql.sql             # V1，21 张表
DBM/lexpro_schema_upgrade_v2_core.sql         # V2，增加 5 张表
DBM/lexpro_schema_upgrade_v3_workspace.sql    # V3，增加 6 张表
```

当前开发数据库已经升级完成，并登记为 Flyway 版本 3 基线，不能再次执行这些脚本。

## 基线执行记录

已于 2026-07-28 完成：

- 在可丢弃数据库中通过 Flyway 执行 V1/V2/V3，得到 32 张业务表和 `flyway_schema_history`。
- 三个 SQL 迁移均成功，pgvector 版本为 0.8.5，不存在未验证约束。
- 验证完成后已删除可丢弃数据库。
- 现有 `lexpro` 数据库已备份到 `backups/lexpro_before_flyway_baseline_20260728.dump`。
- 备份 SHA-256：`CBA7F856ED8756E4EE7B4249D2B430E70D81FE61263121EE02680130DE9AE20E`。
- 现有 Schema 只登记了一条成功的版本 3 `BASELINE` 记录，没有重放 V1/V2/V3。
- 关闭 `baseline-on-migrate` 后再次启动 Flyway，验证数据库仍为最新版本 3。

Flyway 11.7.2 会提示 PostgreSQL 18.4 超出其已测试支持范围（最高 PostgreSQL 17）。本地完整迁移和验证已经通过，但生产部署前应重新检查 Spring Boot 管理的 Flyway 版本。

## 验证方法

```sql
SELECT current_database(), current_user;

SELECT count(*)
FROM information_schema.tables
WHERE table_schema = 'lexpro'
  AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';
```

预期业务表数为 `32`。Flyway 基线登记前，这也是物理表总数；登记后 Schema 中会有 33 张物理表，其中 `flyway_schema_history` 是 Flyway 基础设施元数据，不计入业务验证和 ER 图。其他只读检查位于 `DBM/lexpro_schema_validation.sql` 和 `DBM/SCHEMA_UPGRADE_README.md`。

## Flyway 策略

1. `DBM` 中已经审查的 V1/V2/V3 原文件保持不变，作为历史来源。
2. 后端资源中的迁移副本只删除最外层 `BEGIN/COMMIT`，因为每个迁移的事务由 Flyway 管理；每个副本记录来源文件的 SHA-256。
3. Flyway和baseline-on-migrate默认关闭，并通过环境变量控制。
4. 可丢弃数据库验证和现有数据库 V3 基线已于 2026-07-28 完成。
5. 一次性基线记录创建后，必须保持 `baseline-on-migrate=false`。
6. 新结构修改从 `V4__<description>.sql` 开始。
7. 每个迁移只向前执行；在PostgreSQL允许时使用事务，并提供验证SQL。
8. 应用启动不能被视为自动批准修改未备份的生产数据库。

当前安全控制：

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
spring.flyway.clean-disabled=true
```

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
