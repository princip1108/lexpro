from __future__ import annotations

import time
from typing import Any

from app.cache.task_store import TaskStore
from app.engines.mlp_engine import MlpEngine
from app.repositories.case_repository import CaseRepository


class RerankService:
    """
    典型案例争议焦点融合重排序服务。

    排序流程：
    1. 从TaskStore读取接口1生成的争议焦点向量；
    2. 读取接口2召回的候选案例向量；
    3. 使用MLP计算每个争议焦点与每个候选案例的匹配分数；
    4. 根据争议焦点reliability进行加权平均；
    5. 使用Sigmoid将争议焦点得分映射到0～1；
    6. 按照以下公式计算最终得分：

       final_score = fact_similarity + 0.3 * issue_score

    7. 按final_score降序排列；
    8. 批量读取最终候选案例的content正文。
    """

    FACT_WEIGHT = 1.0
    ISSUE_WEIGHT = 0.3

    def __init__(
        self,
        mlp_engine: MlpEngine,
        task_store: TaskStore,
        case_repository: CaseRepository,
    ) -> None:
        self.mlp_engine = mlp_engine
        self.task_store = task_store
        self.case_repository = case_repository

    def rerank(
        self,
        retrieval_id: str,
        top_k: int = 100,
    ) -> dict[str, Any]:
        """
        根据retrieval_id对接口2返回的候选案例进行重排序。
        """
        total_start = time.perf_counter()

        if not retrieval_id:
            raise ValueError("retrieval_id不能为空")

        if top_k < 1 or top_k > 100:
            raise ValueError(
                "top_k必须在1到100之间"
            )

        # =====================================================
        # 1. 读取接口2保存的完整检索结果
        # =====================================================
        retrieval_data = (
            self.task_store.get_retrieval(
                retrieval_id
            )
        )

        analysis_id = retrieval_data.get(
            "analysis_id"
        )

        issues = retrieval_data.get(
            "issues"
        )

        issue_embeddings = retrieval_data.get(
            "issue_embeddings"
        )

        candidates = retrieval_data.get(
            "candidates"
        )

        if not issues:
            raise ValueError(
                "检索任务中缺少争议焦点"
            )

        if issue_embeddings is None:
            raise ValueError(
                "检索任务中缺少issue_embeddings"
            )

        if len(issues) != len(issue_embeddings):
            raise ValueError(
                "争议焦点数量与焦点向量数量不一致："
                f"{len(issues)} != "
                f"{len(issue_embeddings)}"
            )

        # 没有候选案例时直接返回
        if not candidates:
            total_elapsed_ms = (
                time.perf_counter()
                - total_start
            ) * 1000

            return {
                "retrieval_id": retrieval_id,
                "analysis_id": analysis_id,
                "candidate_count": 0,
                "ranking_rule": (
                    "fact_similarity "
                    "+ 0.3 * issue_score"
                ),
                "score_weights": {
                    "fact_similarity": (
                        self.FACT_WEIGHT
                    ),
                    "issue_score": (
                        self.ISSUE_WEIGHT
                    ),
                },
                "candidates": [],
                "timing": {
                    "mlp_ms": 0.0,
                    "content_ms": 0.0,
                    "total_ms": round(
                        total_elapsed_ms,
                        2,
                    ),
                },
            }

        # =====================================================
        # 2. 整理候选案例向量
        # =====================================================
        candidate_embeddings = []

        for candidate in candidates:
            embedding = candidate.get(
                "embedding"
            )

            if embedding is None:
                raise ValueError(
                    "候选案例缺少embedding，"
                    f"id={candidate.get('id')}，"
                    f"来源表={candidate.get('core_table')}"
                )

            candidate_embeddings.append(
                embedding
            )

        # =====================================================
        # 3. 获取争议焦点可靠性权重
        # =====================================================
        reliabilities: list[float] = []

        for issue in issues:
            reliability = issue.get(
                "reliability"
            )

            if reliability is None:
                reliability = issue.get(
                    "weight",
                    1.0,
                )

            reliabilities.append(
                float(reliability)
            )

        # =====================================================
        # 4. 使用MLP批量计算争议焦点匹配分数
        # =====================================================
        mlp_start = time.perf_counter()

        score_result = (
            self.mlp_engine.score_candidates(
                issue_embeddings=(
                    issue_embeddings
                ),
                candidate_embeddings=(
                    candidate_embeddings
                ),
                reliabilities=reliabilities,
            )
        )

        mlp_elapsed_ms = (
            time.perf_counter()
            - mlp_start
        ) * 1000

        # [焦点数量, 候选数量]
        pair_scores = score_result[
            "pair_scores"
        ]

        # reliability加权平均后的原始得分
        raw_scores = score_result[
            "raw_scores"
        ]

        # Sigmoid之后的争议焦点得分
        sigmoid_scores = score_result[
            "scores"
        ]

        # =====================================================
        # 5. 计算每个候选案例的最终融合得分
        # =====================================================
        reranked_candidates: list[
            dict[str, Any]
        ] = []

        for candidate_index, candidate in enumerate(
            candidates
        ):
            result_candidate = candidate.copy()

            # 保存接口2中的原始排名
            result_candidate[
                "retrieval_rank"
            ] = candidate_index + 1

            issue_details: list[
                dict[str, Any]
            ] = []

            # 每个争议焦点对当前候选案例的得分
            for issue_index, issue in enumerate(
                issues
            ):
                pair_raw_score = float(
                    pair_scores[
                        issue_index,
                        candidate_index,
                    ].item()
                )

                reliability = reliabilities[
                    issue_index
                ]

                issue_details.append(
                    {
                        "issue_id": issue.get(
                            "issue_id",
                            issue_index,
                        ),
                        "issue_text": issue.get(
                            "issue_text",
                            issue.get(
                                "issue",
                                "",
                            ),
                        ),
                        "reliability": round(
                            reliability,
                            6,
                        ),
                        "raw_score": round(
                            pair_raw_score,
                            6,
                        ),
                        "weighted_score": round(
                            reliability
                            * pair_raw_score,
                            6,
                        ),
                    }
                )

            issue_raw_score = float(
                raw_scores[
                    candidate_index
                ].item()
            )

            issue_score = float(
                sigmoid_scores[
                    candidate_index
                ].item()
            )

            fact_similarity = float(
                result_candidate.get(
                    "fact_similarity",
                    0.0,
                )
            )

            # =============================================
            # 最终融合公式
            #
            # 事实相似度权重：1.0
            # 争议焦点得分权重：0.3
            # =============================================
            final_score = (
                self.FACT_WEIGHT
                * fact_similarity
                + self.ISSUE_WEIGHT
                * issue_score
            )

            # 此处先保留完整浮点数，用于精确排序
            result_candidate[
                "issue_raw_score"
            ] = issue_raw_score

            result_candidate[
                "issue_score"
            ] = issue_score

            result_candidate[
                "final_score"
            ] = final_score

            result_candidate[
                "issue_details"
            ] = issue_details

            reranked_candidates.append(
                result_candidate
            )

        # =====================================================
        # 6. 按最终融合分数降序排列
        # =====================================================
        reranked_candidates.sort(
            key=lambda item: item[
                "final_score"
            ],
            reverse=True,
        )

        final_candidates = (
            reranked_candidates[:top_k]
        )

        # =====================================================
        # 7. 批量读取最终候选案例正文
        # =====================================================
        content_start = time.perf_counter()

        final_candidates = (
            self.case_repository.attach_contents(
                final_candidates
            )
        )

        content_elapsed_ms = (
            time.perf_counter()
            - content_start
        ) * 1000

        # =====================================================
        # 8. 整理前端返回结果
        # =====================================================
        response_candidates: list[
            dict[str, Any]
        ] = []

        for rank, candidate in enumerate(
            final_candidates,
            start=1,
        ):
            # 不向前端返回768维向量
            response_candidate = {
                key: value
                for key, value
                in candidate.items()
                if key != "embedding"
            }

            response_candidate[
                "rank"
            ] = rank

            # 返回时统一控制小数位数
            response_candidate[
                "fact_similarity"
            ] = round(
                float(
                    response_candidate.get(
                        "fact_similarity",
                        0.0,
                    )
                ),
                6,
            )

            response_candidate[
                "issue_raw_score"
            ] = round(
                float(
                    response_candidate[
                        "issue_raw_score"
                    ]
                ),
                6,
            )

            response_candidate[
                "issue_score"
            ] = round(
                float(
                    response_candidate[
                        "issue_score"
                    ]
                ),
                6,
            )

            response_candidate[
                "final_score"
            ] = round(
                float(
                    response_candidate[
                        "final_score"
                    ]
                ),
                6,
            )

            response_candidates.append(
                response_candidate
            )

        total_elapsed_ms = (
            time.perf_counter()
            - total_start
        ) * 1000

        return {
            "retrieval_id": retrieval_id,
            "analysis_id": analysis_id,
            "candidate_count": len(
                response_candidates
            ),
            "ranking_rule": (
                "fact_similarity "
                "+ 0.3 * issue_score"
            ),
            "score_weights": {
                "fact_similarity": (
                    self.FACT_WEIGHT
                ),
                "issue_score": (
                    self.ISSUE_WEIGHT
                ),
            },
            "candidates": response_candidates,
            "timing": {
                "mlp_ms": round(
                    mlp_elapsed_ms,
                    2,
                ),
                "content_ms": round(
                    content_elapsed_ms,
                    2,
                ),
                "total_ms": round(
                    total_elapsed_ms,
                    2,
                ),
            },
        }