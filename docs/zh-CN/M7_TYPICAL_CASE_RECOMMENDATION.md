# M7 典型案例推荐

[English](../M7_TYPICAL_CASE_RECOMMENDATION.md)

## 已实现边界

- Java 负责业务案件授权、语料/推荐/收藏/审计写入和 DTO 输出。
- Python 负责导入规范化与向量化，只读典型案例语料，并完成结构化、词法和向量召回、RRF 融合、重排及推荐理由。
- Embedding 在本地使用 `BAAI/bge-m3`，维度为 `1024`，距离为余弦距离。不使用 DeepSeek Key，也不向外部模型发送案件文本。
- 向量生成或召回失败时，Python 可以返回明确标记的纯词法降级结果；服务或数据库整体失败时返回 `503`，不创建推荐批次。

## 内部协议

- `POST /internal/v1/normalize` 接收协议版本 `1.0`、请求 ID 和 1-50 条语料，返回规范化字段、每条 1024 维向量及模型追溯信息。
- `POST /internal/v1/retrieve` 接收协议版本 `1.0`、已授权事实快照、争议焦点、结构化筛选和 1-50 的数量限制，返回相同请求 ID、模型/索引/流水线信息、降级状态、查询向量，以及连续且不重复的排名、有限分数和结构化理由。
- Java 遇到协议/请求 ID/模型/维度/排名不一致时返回 `502 RETRIEVAL_RESPONSE_INVALID`。网络错误和 Python 5xx 最多重试一次；Python 4xx 不重试。最终传输失败返回稳定 `503`，并使用独立事务写失败审计。

## 一次性数据库准备

V4 是只向前执行的迁移，不新增业务表。它把两个现有向量列固定为 `vector(1024)`，并创建 `ix_typical_case_embedding_hnsw`。明确批准执行前，先备份开发库并检查已有向量：

```sql
SELECT vector_dims(embedding), count(*)
FROM lexpro.typical_case
WHERE embedding IS NOT NULL
GROUP BY vector_dims(embedding);

SELECT vector_dims(query_fact_embedding), count(*)
FROM lexpro.case_recommendation
WHERE query_fact_embedding IS NOT NULL
GROUP BY vector_dims(query_fact_embedding);
```

已审查脚本为 `DBM/lexpro_schema_upgrade_v4_retrieval.sql`，Flyway 资源为 `V4__configure_typical_case_vectors.sql`。M7 交付过程中没有自动执行迁移。

批准后为 Python 创建独立只读账号，并在本地替换密码占位符：

```sql
CREATE ROLE lexpro_retrieval LOGIN PASSWORD '<本地强密码>';
GRANT CONNECT ON DATABASE lexpro TO lexpro_retrieval;
GRANT USAGE ON SCHEMA lexpro TO lexpro_retrieval;
GRANT SELECT ON lexpro.typical_case, lexpro.typical_case_content TO lexpro_retrieval;
ALTER ROLE lexpro_retrieval SET default_transaction_read_only = on;
```

不要授予 `case_record`、用户、卷宗、推荐记录或审计表的访问权限。

## 启动 Python 服务

在 `backend/retrieval-service` 下执行：

```powershell
D:\python\python.exe -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:LEXPRO_RETRIEVAL_DATABASE_URL='postgresql://lexpro_retrieval:<密码>@127.0.0.1:5432/lexpro'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8010
```

第一次向量化请求会下载本地 BGE-M3 权重。受控环境应把 `LEXPRO_RETRIEVAL_MODEL_REVISION` 设置为已批准的固定本地或 Hugging Face revision，并让 Java 的 `LEXPRO_RETRIEVAL_MODEL_VERSION` 使用相同值。

## 启用 Java 检索

在 IDEA 运行配置中增加：

```text
LEXPRO_RETRIEVAL_ENABLED=true
LEXPRO_RETRIEVAL_BASE_URL=http://127.0.0.1:8010
LEXPRO_RETRIEVAL_MODEL_NAME=BAAI/bge-m3
LEXPRO_RETRIEVAL_MODEL_VERSION=main
```

默认连接超时 3 秒、读取超时 20 秒、最多重试一次。导入要求 `REPORT_MANAGE + AI_EXECUTE`；查询、收藏和推荐要求 `RECOMMENDATION_USE`；案件推荐还要求 `CASE_READ` 和 Java 案件可见性。

## 验收步骤

1. `GET http://127.0.0.1:8010/internal/v1/health` 返回 `UP` 和 1024/余弦模型协议。
2. 通过 `/api/v1/typical-cases/imports` 导入至少两条典型案例；再次导入相同 `externalCaseId` 时应更新而不是新增重复记录。
3. 通过 `/api/v1/cases/{caseId}/recommendations` 创建推荐，再读取历史/详情并收藏一个结果。
4. 检查 `case_recommendation`、`case_recommendation_item`、`typical_case_favorite` 和 `operation_log` 已保存追溯信息，且 API 不返回向量或数据库凭据。
