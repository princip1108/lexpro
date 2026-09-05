from __future__ import annotations

import math
from typing import Any

import numpy as np
import torch
from sqlalchemy import bindparam, text
from sqlalchemy.engine import Engine
from concurrent.futures import ThreadPoolExecutor, as_completed


YEAR_MIN = 2000
YEAR_MAX = 2025

TYPICAL_TABLE_PAIRS = (
    ("core_typical", "content_typical"),
    ("core_2025", "content_2025"),
    ("core_2024", "content_2024"),
    ("core_2023", "content_2023"),
)


def get_year_table_pairs(
    years: list[int],
) -> list[tuple[str, str]]:
    unique_years = sorted(set(years), reverse=True)

    invalid_years = [
        year
        for year in unique_years
        if year < YEAR_MIN or year > YEAR_MAX
    ]

    if invalid_years:
        raise ValueError(
            f"不支持的年份：{invalid_years}"
        )

    return [
        (
            f"core_{year}",
            f"content_{year}",
        )
        for year in unique_years
    ]


def get_typical_table_pairs() -> list[tuple[str, str]]:
    return list(TYPICAL_TABLE_PAIRS)


CORE_CONTENT_TABLE_MAP = {
    **{
        f"core_{year}": f"content_{year}"
        for year in range(YEAR_MIN, YEAR_MAX + 1)
    },
    "core_typical": "content_typical",
}

ALLOWED_CONTENT_TABLES = set(
    CORE_CONTENT_TABLE_MAP.values()
)


def _clean_text_array(value: Any) -> list[str]:
    if not isinstance(value, (list, tuple)):
        return []
    return [
        item.strip()
        for item in value
        if isinstance(item, str) and item.strip()
    ]

def _vector_to_pgvector(
    embedding: torch.Tensor | np.ndarray | list[float],
) -> str:
    """
    将Tensor、NumPy数组或列表转换为pgvector可接收的文本格式：
    [0.1,0.2,...]
    """
    if isinstance(embedding, torch.Tensor):
        values = (
            embedding
            .detach()
            .cpu()
            .float()
            .reshape(-1)
            .tolist()
        )

    elif isinstance(embedding, np.ndarray):
        values = (
            embedding
            .astype(np.float32)
            .reshape(-1)
            .tolist()
        )

    else:
        values = [
            float(value)
            for value in embedding
        ]

    if not values:
        raise ValueError("查询向量不能为空")

    if not all(math.isfinite(value) for value in values):
        raise ValueError("查询向量包含NaN或无穷值")

    return "[" + ",".join(
        format(value, ".9g")
        for value in values
    ) + "]"


def _parse_pgvector(
    vector_text: str | None,
) -> list[float]:
    """
    将PostgreSQL返回的向量文本转换为float列表。
    """
    if not vector_text:
        return []

    cleaned = vector_text.strip()

    if cleaned.startswith("[") and cleaned.endswith("]"):
        cleaned = cleaned[1:-1]

    if not cleaned:
        return []

    return [
        float(value)
        for value in cleaned.split(",")
    ]


class CaseRepository:
    """
    案例数据库访问层。
    """

    def __init__(
        self,
        db_engine: Engine,
        hnsw_ef_search: int = 200,
    ) -> None:
        self.db_engine = db_engine
        self.hnsw_ef_search = hnsw_ef_search

    def search_one_table(
            self,
            core_table: str,
            query_embedding,
            top_k: int = 100,
            filters: dict[str, Any] | None = None,
            include_embedding: bool = True,
    ) -> list[dict[str, Any]]:
        """
        在单张core表中执行向量检索和结构化条件筛选。
        """
        if core_table not in CORE_CONTENT_TABLE_MAP:
            raise ValueError(
                f"不允许查询的表：{core_table}"
            )

        if top_k <= 0:
            raise ValueError("top_k必须大于0")

        filters = filters or {}

        allowed_filter_names = {
            "title",
            "casecauses",
            "applicable_laws",
            "caselevel",
            "courtlevel",
            "county",
            "judgedate",
            "procedure",
            "doctype",
            "court",
            "casetype",
        }

        unknown_filters = (
                set(filters.keys()) - allowed_filter_names
        )

        if unknown_filters:
            raise ValueError(
                f"不支持的筛选条件：{sorted(unknown_filters)}"
            )

        content_table = CORE_CONTENT_TABLE_MAP[
            core_table
        ]

        query_vector = _vector_to_pgvector(
            query_embedding
        )

        where_conditions = [
            "embedding IS NOT NULL"
        ]

        params: dict[str, Any] = {
            "query_vector": query_vector,
            "top_k": top_k,
        }

        # 1. 标题：模糊匹配
        title = filters.get("title")

        if title:
            where_conditions.append(
                "title ILIKE :title"
            )
            params["title"] = f"%{title}%"

        # 2. 罪名：
        # casecause是text，casecausefull是text[]
        casecauses = filters.get("casecauses")

        if casecauses:
            where_conditions.append(
                """
                (
                    casecause = ANY(
                        CAST(:casecauses AS text[])
                    )
                    OR casecausefull && CAST(
                        :casecauses AS text[]
                    )
                )
                """
            )
            params["casecauses"] = casecauses

        # 3. 法条：applicable_law是text[]
        applicable_laws = filters.get(
            "applicable_laws"
        )

        if applicable_laws:
            where_conditions.append(
                """
                applicable_law && CAST(
                    :applicable_laws AS text[]
                )
                """
            )
            params["applicable_laws"] = (
                applicable_laws
            )

        # 4. 案件等级
        caselevel = filters.get("caselevel")

        if caselevel:
            where_conditions.append(
                "caselevel = :caselevel"
            )
            params["caselevel"] = caselevel

        # 5. 法院等级
        courtlevel = filters.get("courtlevel")

        if courtlevel:
            where_conditions.append(
                "courtlevel = :courtlevel"
            )
            params["courtlevel"] = courtlevel

        # 6. 地区：模糊匹配
        county = filters.get("county")

        if county:
            where_conditions.append(
                "county ILIKE :county"
            )
            params["county"] = f"%{county}%"

        # 7. 判决时间：该日期及之前
        judgedate = filters.get("judgedate")

        if judgedate:
            where_conditions.append(
                "judgedate <= :judgedate"
            )
            params["judgedate"] = judgedate

        # 8. 审理程序
        procedure = filters.get("procedure")

        if procedure:
            where_conditions.append(
                "procedure = :procedure"
            )
            params["procedure"] = procedure

        # 9. 文书类型
        doctype = filters.get("doctype")

        if doctype:
            where_conditions.append(
                "doctype = :doctype"
            )
            params["doctype"] = doctype

        # 11. 法院名称：模糊匹配
        court = filters.get("court")

        if court:
            where_conditions.append(
                "court ILIKE :court"
            )
            params["court"] = f"%{court}%"

        # 12. 案件类型
        casetype = filters.get("casetype")

        if casetype:
            where_conditions.append(
                "casetype = :casetype"
            )
            params["casetype"] = casetype

        where_sql = " AND ".join(
            where_conditions
        )

        embedding_select_sql = ""

        if include_embedding:
            embedding_select_sql = (
                ", embedding::text AS embedding_text"
            )

        sql = f"""
            SELECT
                id,
                title,
                caseid,
                applicable_law,
                casecause,
                casecausefull,
                caselevel,
                casetype,
                county,
                court,
                courtlevel,
                judgedate,
                procedure,
                doctype
                {embedding_select_sql},
                embedding <=> CAST(
                    :query_vector AS vector
                ) AS distance
            FROM public.{core_table}
            WHERE {where_sql}
            ORDER BY embedding <=> CAST(
                :query_vector AS vector
            )
            LIMIT :top_k
        """

        with self.db_engine.begin() as connection:
            connection.execute(
                text(
                    f"SET LOCAL hnsw.ef_search = "
                    f"{int(self.hnsw_ef_search)}"
                )
            )

            rows = connection.execute(
                text(sql),
                params,
            ).mappings().all()

        candidates: list[dict[str, Any]] = []

        for row in rows:
            distance = float(row["distance"])

            candidate = {
                "id": row["id"],
                "title": row["title"],
                "caseid": row["caseid"],
                "applicable_law": _clean_text_array(
                    row["applicable_law"]
                ),
                "casecause": row["casecause"],
                "casecausefull": _clean_text_array(
                    row["casecausefull"]
                ),
                "caselevel": row["caselevel"],
                "casetype": row["casetype"],
                "county": row["county"],
                "court": row["court"],
                "courtlevel": row["courtlevel"],
                "judgedate": (
                    str(row["judgedate"])
                    if row["judgedate"] is not None
                    else None
                ),
                "procedure": row["procedure"],
                "doctype": row["doctype"],
                "distance": distance,
                "fact_similarity": 1.0 - distance,
                "core_table": core_table,
                "content_table": content_table,
            }

            if include_embedding:
                candidate["embedding"] = (
                    _parse_pgvector(
                        row["embedding_text"]
                    )
                )

            candidates.append(candidate)

        return candidates

    def search_multiple_tables(
            self,
            table_pairs: list[tuple[str, str]],
            query_embedding,
            top_k: int = 100,
            per_table_k: int = 100,
            filters: dict[str, Any] | None = None,
            include_embedding: bool = True,
    ) -> list[dict[str, Any]]:
        """
        并行检索多张core表，合并后返回全局前top_k。
        """
        if not table_pairs:
            return []

        if top_k <= 0:
            raise ValueError("top_k必须大于0")

        if per_table_k <= 0:
            raise ValueError("per_table_k必须大于0")

        all_candidates: list[dict[str, Any]] = []

        max_workers = min(
            len(table_pairs),
            4,
        )

        with ThreadPoolExecutor(
                max_workers=max_workers
        ) as executor:

            future_to_table = {
                executor.submit(
                    self.search_one_table,
                    core_table=core_table,
                    query_embedding=query_embedding,
                    top_k=per_table_k,
                    filters=filters,
                    include_embedding=include_embedding,
                ): core_table
                for core_table, _ in table_pairs
            }

            for future in as_completed(
                    future_to_table
            ):
                core_table = future_to_table[
                    future
                ]

                try:
                    table_candidates = (
                        future.result()
                    )

                    all_candidates.extend(
                        table_candidates
                    )

                except Exception as exc:
                    raise RuntimeError(
                        f"检索表 {core_table} 失败：{exc}"
                    ) from exc

        all_candidates.sort(
            key=lambda item: item["distance"]
        )

        return all_candidates[:top_k]

    def fetch_contents_one_table(
            self,
            content_table: str,
            ids: list[int],
    ) -> dict[int, str | None]:
        """
        从一张content表中批量读取案件正文。

        返回：
        {
            案例ID: 正文内容
        }
        """
        if content_table not in ALLOWED_CONTENT_TABLES:
            raise ValueError(
                f"不允许查询的内容表：{content_table}"
            )

        unique_ids = list(dict.fromkeys(ids))

        if not unique_ids:
            return {}

        # 表名来自固定白名单，可以安全拼接
        sql = text(
            f"""
            SELECT
                id,
                content
            FROM public.{content_table}
            WHERE id IN :ids
            """
        ).bindparams(
            bindparam(
                "ids",
                expanding=True,
            )
        )

        with self.db_engine.connect() as connection:
            rows = connection.execute(
                sql,
                {
                    "ids": unique_ids,
                },
            ).mappings().all()

        return {
            int(row["id"]): row["content"]
            for row in rows
        }

    def attach_contents(
            self,
            candidates: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        """
        根据候选案例的content_table和id，
        并行批量读取正文并合并到候选结果。

        最多查询四张content表，
        每张表只执行一条批量SQL。
        """
        if not candidates:
            return []

        # 按content表分组
        table_ids: dict[str, list[int]] = {}

        for candidate in candidates:
            content_table = candidate.get(
                "content_table"
            )

            candidate_id = candidate.get("id")

            if not content_table:
                raise ValueError(
                    "候选案例缺少content_table"
                )

            if candidate_id is None:
                raise ValueError(
                    "候选案例缺少id"
                )

            table_ids.setdefault(
                content_table,
                [],
            ).append(
                int(candidate_id)
            )

        content_map: dict[
            tuple[str, int],
            str | None,
        ] = {}

        max_workers = min(
            len(table_ids),
            4,
        )

        # 不同content表并行读取
        with ThreadPoolExecutor(
                max_workers=max_workers
        ) as executor:

            future_to_table = {
                executor.submit(
                    self.fetch_contents_one_table,
                    content_table=content_table,
                    ids=ids,
                ): content_table
                for content_table, ids in table_ids.items()
            }

            for future in as_completed(
                    future_to_table
            ):
                content_table = future_to_table[
                    future
                ]

                try:
                    table_contents = future.result()

                except Exception as exc:
                    raise RuntimeError(
                        f"读取表 {content_table} 正文失败：{exc}"
                    ) from exc

                for candidate_id, content in (
                        table_contents.items()
                ):
                    content_map[
                        (
                            content_table,
                            candidate_id,
                        )
                    ] = content

        results: list[dict[str, Any]] = []

        # 保持原有候选顺序
        for candidate in candidates:
            result = candidate.copy()

            content_table = result[
                "content_table"
            ]

            candidate_id = int(
                result["id"]
            )

            result["content"] = content_map.get(
                (
                    content_table,
                    candidate_id,
                )
            )

            results.append(result)

        return results
