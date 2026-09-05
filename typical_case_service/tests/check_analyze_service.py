import os

# 必须放在torch、Qwen、DELTA相关导入之前
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

from app.cache.task_store import TaskStore
from app.core.config import settings
from app.engines.delta_engine import DeltaEngine
from app.engines.qwen_engine import QwenEngine
from app.services.analyze_service import AnalyzeService


def main() -> None:
    print("开始加载Qwen")

    qwen_engine = QwenEngine(
        model_path=settings.qwen_model_path,
        max_seq_length=settings.QWEN_MAX_SEQ_LENGTH,
        max_new_tokens=settings.QWEN_MAX_NEW_TOKENS,
        temperature=settings.QWEN_TEMPERATURE,
        top_p=settings.QWEN_TOP_P,
        repetition_penalty=settings.QWEN_REPETITION_PENALTY,
        load_in_4bit=settings.QWEN_LOAD_IN_4BIT,
    )

    print("开始加载DELTA")

    delta_engine = DeltaEngine(
        model_path=settings.delta_model_path,
        device=settings.DELTA_DEVICE,
        batch_size=settings.DELTA_BATCH_SIZE,
        max_length=settings.DELTA_MAX_LENGTH,
    )

    task_store = TaskStore()

    analyze_service = AnalyzeService(
        delta_engine=delta_engine,
        qwen_engine=qwen_engine,
        task_store=task_store,
    )

    try:
        case_fact = (
            "被告人张某因琐事与被害人李某发生争执，"
            "后持刀刺伤李某腹部。"
            "经鉴定，李某的损伤程度为重伤二级。"
            "张某案发后主动到公安机关投案，"
            "并如实供述了主要犯罪事实。"
        )

        # 执行完整的案件分析流程
        result = analyze_service.analyze(case_fact)

        print("\n案件分析接口结果：")
        print("analysis_id：", result["analysis_id"])
        print("事实句子数量：", result["sentence_count"])
        print("争议焦点数量：", result["issue_count"])
        print("Qwen耗时：", result["qwen_time_ms"], "ms")
        print("DELTA耗时：", result["delta_time_ms"], "ms")
        print("总耗时：", result["total_time_ms"], "ms")

        print("\n争议焦点：")

        for issue in result["issues"]:
            print("-" * 60)
            print("焦点ID：", issue["issue_id"])
            print("焦点文本：", issue["issue_text"])
            print("可靠性：", issue["reliability"])
            print("权重：", issue["weight"])
            print("对应事实句：", issue["matched_sentence"])

        # 从TaskStore中读取后端保存的完整结果
        analysis = task_store.get_analysis(
            result["analysis_id"]
        )

        print("\n缓存结果检查：")
        print(
            "案件整体向量形状：",
            analysis["query_embedding"].shape,
        )
        print(
            "事实句子向量形状：",
            analysis["sentence_embeddings"].shape,
        )
        print(
            "争议焦点向量形状：",
            analysis["issue_embeddings"].shape,
        )
        print(
            "案件整体向量设备：",
            analysis["query_embedding"].device,
        )
        print(
            "争议焦点向量设备：",
            analysis["issue_embeddings"].device,
        )

        # 基本正确性验证
        assert analysis["query_embedding"].shape == (768,)

        assert (
            analysis["issue_embeddings"].shape[0]
            == result["issue_count"]
        )

        assert analysis["issue_embeddings"].shape[1] == 768

        assert (
            analysis["sentence_embeddings"].shape[0]
            == result["sentence_count"]
        )

        assert analysis["query_embedding"].device.type == "cpu"
        assert analysis["issue_embeddings"].device.type == "cpu"

        print("\nAnalyzeService测试成功")

    finally:
        # 先释放小模型，再释放大模型
        delta_engine.close()
        qwen_engine.close()


if __name__ == "__main__":
    main()