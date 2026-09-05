import time
from typing import Any

from app.cache.task_store import TaskStore
from app.engines.delta_engine import DeltaEngine
from app.engines.qwen_engine import QwenEngine
from app.utils.score_utils import calculate_issue_reliability
from app.utils.text_utils import clean_case_fact, split_sentences


class AnalyzeService:
    """
    案件分析服务。

    负责：
    1. 清理案件事实
    2. 提取争议焦点
    3. 编码案件事实、事实句子和争议焦点
    4. 计算争议焦点可靠性
    5. 保存中间结果
    """

    def __init__(
        self,
        delta_engine: DeltaEngine,
        qwen_engine: QwenEngine,
        task_store: TaskStore,
    ) -> None:
        self.delta_engine = delta_engine
        self.qwen_engine = qwen_engine
        self.task_store = task_store

    def analyze(
        self,
        case_fact: str,
    ) -> dict[str, Any]:
        total_start = time.perf_counter()

        # =========================
        # 1. 清理案件事实
        # =========================
        cleaned_fact = clean_case_fact(case_fact)

        if len(cleaned_fact) < 5:
            raise ValueError("案件事实过短")

        # =========================
        # 2. Qwen提取争议焦点
        # =========================
        qwen_start = time.perf_counter()

        issues = self.qwen_engine.extract_issues(
            cleaned_fact
        )

        qwen_time_ms = round(
            (time.perf_counter() - qwen_start) * 1000,
            2,
        )

        if not issues:
            raise ValueError("未提取到争议焦点")

        # =========================
        # 3. 案件事实分句
        # =========================
        sentences = split_sentences(cleaned_fact)

        if not sentences:
            raise ValueError("案件事实分句结果为空")

        # =========================
        # 4. DELTA统一批量编码
        # =========================
        delta_start = time.perf_counter()

        # 一次DELTA调用完成全部编码，减少重复调用开销
        all_texts = [
            cleaned_fact,
            *sentences,
            *issues,
        ]

        all_embeddings = self.delta_engine.encode(
            all_texts
        )

        delta_time_ms = round(
            (time.perf_counter() - delta_start) * 1000,
            2,
        )

        sentence_start = 1
        sentence_end = sentence_start + len(sentences)
        issue_start = sentence_end

        # 完整案件事实embedding，接口2检索使用
        query_embedding = (
            all_embeddings[0]
            .detach()
            .cpu()
            .contiguous()
        )

        # 事实句子embedding，可靠性计算使用
        sentence_embeddings = (
            all_embeddings[sentence_start:sentence_end]
            .detach()
            .cpu()
            .contiguous()
        )

        # 争议焦点embedding，接口3的MLP使用
        issue_embeddings = (
            all_embeddings[issue_start:]
            .detach()
            .cpu()
            .contiguous()
        )

        # =========================
        # 5. 计算焦点可靠性
        # =========================
        issue_items = calculate_issue_reliability(
            issues=issues,
            sentences=sentences,
            issue_embeddings=issue_embeddings,
            sentence_embeddings=sentence_embeddings,
        )

        # =========================
        # 6. 保存全部中间结果
        # =========================
        analysis_id = self.task_store.save_analysis(
            {
                "case_fact": cleaned_fact,

                # 接口2使用
                "query_embedding": query_embedding,

                # 焦点可靠性和解释信息
                "issues": issue_items,

                # 接口3的MLP使用
                "issue_embeddings": issue_embeddings,

                # 保留用于调试和解释
                "sentences": sentences,
                "sentence_embeddings": sentence_embeddings,
            }
        )

        total_time_ms = round(
            (time.perf_counter() - total_start) * 1000,
            2,
        )

        # =========================
        # 7. 构造前端返回结果
        # embedding不返回前端
        # =========================
        response_issues = []

        for item in issue_items:
            response_issues.append(
                {
                    "issue_id": item["issue_id"],
                    "issue_text": item["issue_text"],
                    "reliability": round(
                        item["reliability"],
                        6,
                    ),
                    "weight": round(
                        item["weight"],
                        6,
                    ),
                    "matched_sentence_index": (
                        item["matched_sentence_index"]
                    ),
                    "matched_sentence": (
                        item["matched_sentence"]
                    ),
                }
            )

        return {
            "analysis_id": analysis_id,
            "sentence_count": len(sentences),
            "issue_count": len(response_issues),
            "qwen_time_ms": qwen_time_ms,
            "delta_time_ms": delta_time_ms,
            "total_time_ms": total_time_ms,
            "issues": response_issues,
        }