from __future__ import annotations

import time
from typing import Any

from app.cache.task_store import TaskStore
from app.repositories.case_repository import (
    CaseRepository,
    get_typical_table_pairs,
)
from app.schemas.retrieve import TypicalRetrieveRequest


class RetrievalService:
    """
    典型案例数据库检索服务。

    流程：
    1. 根据analysis_id读取接口1生成的查询向量；
    2. 在固定四张core表中并行检索；
    3. 合并并保留全局前100条；
    4. 将包含候选embedding的完整结果保存到TaskStore；
    5. 向前端返回不包含embedding的候选案例。
    """

    def __init__(
        self,
        case_repository: CaseRepository,
        task_store: TaskStore,
    ) -> None:
        self.case_repository = case_repository
        self.task_store = task_store

    def retrieve_typical_cases(
        self,
        request: TypicalRetrieveRequest,
    ) -> dict[str, Any]:
        start_time = time.perf_counter()

        # 1. 读取接口1保存的分析结果
        analysis_data = self.task_store.get_analysis(
            request.analysis_id
        )

        query_embedding = analysis_data.get(
            "query_embedding"
        )

        if query_embedding is None:
            raise ValueError(
                "案件分析结果中缺少query_embedding"
            )

        # 日期保持为date对象，直接交给SQLAlchemy
        filters = request.filters.model_dump(
            mode="python",
            exclude_none=True,
        )

        table_pairs = get_typical_table_pairs()

        caselevel = filters.get("caselevel")

        # 按案件等级直接路由表 caselevel = 普通案例
        # → 只查 core_2025、core_2024、core_2023
        # → 不再把 caselevel = 普通案例 写进 SQL
        #
        # caselevel = 指导性案例 / 典型案例 / 参考案例
        # → 只查 core_typical
        # → 保留 caselevel 筛选
        #
        # 没有传 caselevel
        # → 查 core_typical、core_2025、core_2024、core_2023
        if caselevel == "普通案例":
            # 普通案例只查近三年普通案例表
            # 这三张表本身就是普通案例，因此不需要再加caselevel过滤
            table_pairs = [
                ("core_2025", "content_2025"),
                ("core_2024", "content_2024"),
                ("core_2023", "content_2023"),
            ]

            filters.pop("caselevel", None)

        elif caselevel in {
    		"指导性案例",
    		"典型案例",
    		"参考性案例",
		}:
            # 这些等级只在core_typical中检索
            table_pairs = [
                ("core_typical", "content_typical"),
            ]

        # 2. 固定检索四张表
        database_start = time.perf_counter()

        candidates = (
            self.case_repository.search_multiple_tables(
                table_pairs=table_pairs,
                query_embedding=query_embedding,
                top_k=request.top_k,

                # 为保证全局前100不遗漏，
                # 每张表分别最多取100条
                per_table_k=request.top_k,

                filters=filters,

                # 接口3的MLP需要候选案例向量
                include_embedding=True,
            )
        )

        database_elapsed_ms = (
            time.perf_counter() - database_start
        ) * 1000

        # 3. 保存完整检索结果
        # 此处必须保留candidate embedding，供接口3使用
        retrieval_data = {
            "analysis_id": request.analysis_id,
            "query_embedding": query_embedding,
            "issues": analysis_data.get(
                "issues",
                [],
            ),
            "issue_embeddings": analysis_data.get(
                "issue_embeddings"
            ),
            "filters": filters,
            "candidates": candidates,
        }

        retrieval_id = self.task_store.save_retrieval(
            retrieval_data
        )

        # 4. 删除返回前端时不需要暴露的向量
        response_candidates: list[dict[str, Any]] = []

        for rank, candidate in enumerate(
            candidates,
            start=1,
        ):
            response_candidate = {
                key: value
                for key, value in candidate.items()
                if key != "embedding"
            }

            response_candidate["rank"] = rank

            response_candidates.append(
                response_candidate
            )

        total_elapsed_ms = (
            time.perf_counter() - start_time
        ) * 1000

        return {
            "retrieval_id": retrieval_id,
            "analysis_id": request.analysis_id,
            "candidate_count": len(
                response_candidates
            ),
            "filters": filters,
            "candidates": response_candidates,
            "timing": {
                "database_ms": round(
                    database_elapsed_ms,
                    2,
                ),
                "total_ms": round(
                    total_elapsed_ms,
                    2,
                ),
            },
        }