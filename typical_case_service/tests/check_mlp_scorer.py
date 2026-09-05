from pathlib import Path

import torch

from app.engines.mlp_engine import MlpEngine


def main() -> None:
    project_root = (
        Path(__file__)
        .resolve()
        .parents[1]
    )

    engine = MlpEngine(
        weight_path=(
            project_root
            / "weights"
            / "qwen3_14B_DELTA.pt"
        )
    )

    try:
        # 模拟接口1的3个争议焦点向量
        issue_embeddings = torch.randn(
            3,
            768,
        )

        issue_embeddings = (
            torch.nn.functional.normalize(
                issue_embeddings,
                p=2,
                dim=-1,
            )
        )

        # 模拟接口2的100个候选向量
        candidate_embeddings = torch.randn(
            100,
            768,
        )

        candidate_embeddings = (
            torch.nn.functional.normalize(
                candidate_embeddings,
                p=2,
                dim=-1,
            )
        )

        result = engine.score_candidates(
            issue_embeddings=issue_embeddings,
            candidate_embeddings=(
                candidate_embeddings
            ),
            reliabilities=[
                0.75,
                0.68,
                0.81,
            ],
        )

        print(
            "焦点-候选分数矩阵：",
            result["pair_scores"].shape,
        )

        print(
            "候选原始分数：",
            result["raw_scores"].shape,
        )

        print(
            "候选Sigmoid分数：",
            result["scores"].shape,
        )

        print(
            "前5个分数：",
            result["scores"][:5].tolist(),
        )

        assert result["pair_scores"].shape == (
            3,
            100,
        )

        assert result["scores"].shape == (
            100,
        )

        print("MLP缓存向量打分测试成功")

    finally:
        engine.close()


if __name__ == "__main__":
    main()