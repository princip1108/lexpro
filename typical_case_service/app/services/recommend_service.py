from __future__ import annotations

import time
from typing import Any

from app.schemas.recommend import (
    TypicalCaseSearchRequest,
)
from app.schemas.retrieve import (
    TypicalRetrieveRequest,
)
from app.services.retrieval_service import (
    RetrievalService,
)
from app.services.rerank_service import (
    RerankService,
)


class RecommendService:
    """
    典型案例最终检索编排服务。

    自动串联：
    1. 数据库向量召回；
    2. 争议焦点融合重排序；
    3. 案例正文读取。
    """

    def __init__(
        self,
        retrieval_service: RetrievalService,
        rerank_service: RerankService,
    ) -> None:
        self.retrieval_service = (
            retrieval_service
        )
        self.rerank_service = rerank_service

    def search_typical_cases(
        self,
        request: TypicalCaseSearchRequest,
    ) -> dict[str, Any]:
        total_start = time.perf_counter()

        # ==================================================
        # 1. 自动执行接口2：数据库召回
        # ==================================================
        analysis_id = request.analysis_id

        if not analysis_id:
            analysis_id = (
                self.retrieval_service
                .task_store
                .get_latest_analysis_id()
            )

        retrieval_request = TypicalRetrieveRequest(
            analysis_id=analysis_id,
            filters=request.filters,
            top_k=100,
        )

        retrieval_result = (
            self.retrieval_service
            .retrieve_typical_cases(
                retrieval_request
            )
        )

        retrieval_id = retrieval_result.get(
            "retrieval_id"
        )

        if not retrieval_id:
            raise RuntimeError(
                "数据库召回未生成retrieval_id"
            )

        # ==================================================
        # 2. 自动执行接口3：融合重排序
        # ==================================================
        rerank_result = (
            self.rerank_service.rerank(
                retrieval_id=retrieval_id,
                top_k=request.top_k,
            )
        )

        total_elapsed_ms = (
            time.perf_counter()
            - total_start
        ) * 1000

        # 接口2和接口3的时间均保留下来
        rerank_result["pipeline_timing"] = {
            "retrieval_database_ms": (
                retrieval_result
                .get("timing", {})
                .get("database_ms", 0.0)
            ),
            "retrieval_total_ms": (
                retrieval_result
                .get("timing", {})
                .get("total_ms", 0.0)
            ),
            "rerank_mlp_ms": (
                rerank_result
                .get("timing", {})
                .get("mlp_ms", 0.0)
            ),
            "content_ms": (
                rerank_result
                .get("timing", {})
                .get("content_ms", 0.0)
            ),
            "pipeline_total_ms": round(
                total_elapsed_ms,
                2,
            ),
        }

        return rerank_result