import torch

from app.core.config import settings
from app.engines.delta_engine import DeltaEngine
from app.utils.score_utils import (
    calculate_issue_reliability,
)
from app.utils.text_utils import split_sentences


def main() -> None:
    delta_engine = DeltaEngine(
        model_path=settings.delta_model_path,
        device=settings.DELTA_DEVICE,
        batch_size=settings.DELTA_BATCH_SIZE,
        max_length=settings.DELTA_MAX_LENGTH,
    )

    try:
        case_fact = """
        被告人张某因琐事与被害人李某发生争执。
        张某持刀刺伤李某腹部。
        经鉴定，李某的损伤程度为重伤二级。
        张某案发后主动到公安机关投案，
        并如实供述了主要犯罪事实。
        """

        issues = [
            "张某的行为是否构成故意伤害罪",
            "张某是否构成自首",
            "被害人的伤情程度如何认定",
        ]

        sentences = split_sentences(case_fact)

        sentence_embeddings = delta_engine.encode(
            sentences
        )

        issue_embeddings = delta_engine.encode(
            issues
        )

        results = calculate_issue_reliability(
            issues=issues,
            sentences=sentences,
            issue_embeddings=issue_embeddings,
            sentence_embeddings=sentence_embeddings,
        )

        print("案件事实分句：")

        for index, sentence in enumerate(sentences):
            print(f"{index}. {sentence}")

        print("\n争议焦点计算结果：")

        for item in results:
            print("-" * 50)
            print("争议焦点：", item["issue_text"])
            print("可靠性：", item["reliability"])
            print("权重：", item["weight"])
            print(
                "对应事实句：",
                item["matched_sentence"],
            )

        weight_sum = sum(
            item["weight"]
            for item in results
        )

        print("-" * 50)
        print("所有焦点权重之和：", weight_sum)



        for embedding in issue_embeddings:
            norm = torch.linalg.vector_norm(
                embedding
            ).item()

            assert abs(norm - 1.0) < 1e-4

        print("争议焦点可靠性测试成功")

    finally:
        delta_engine.close()


if __name__ == "__main__":
    main()