from __future__ import annotations

from pathlib import Path
from typing import Sequence

import torch
import torch.nn as nn


class MlpEngine:
    """
    基于缓存向量的争议焦点匹配打分引擎。

    不加载Tokenizer和DELTA编码器，只从完整权重文件中
    提取scorer参数。

    输入向量必须与MLP训练时保持一致：
    Attention Mask Mean Pooling + L2归一化，维度768。
    """

    def __init__(
        self,
        weight_path: str | Path,
        device: str | None = None,
        hidden_size: int = 768,
    ) -> None:
        self.weight_path = Path(weight_path)
        self.hidden_size = hidden_size

        if not self.weight_path.exists():
            raise FileNotFoundError(
                f"MLP权重文件不存在："
                f"{self.weight_path.resolve()}"
            )

        if device is None:
            device = (
                "cuda"
                if torch.cuda.is_available()
                else "cpu"
            )

        self.device = torch.device(device)

        # 必须与训练时的scorer结构完全一致
        self.scorer = nn.Sequential(
            nn.Linear(
                hidden_size * 2,
                hidden_size,
            ),
            nn.ReLU(),
            nn.Linear(
                hidden_size,
                1,
            ),
        )

        self._load_scorer_weights()

        self.scorer.to(self.device)
        self.scorer.eval()

    def _load_scorer_weights(self) -> None:
        """
        从完整IssueDiscriminator权重中，
        只提取scorer.*参数。
        """
        checkpoint = torch.load(
            self.weight_path,
            map_location="cpu",
            weights_only=True,
        )

        if (
            isinstance(checkpoint, dict)
            and "state_dict" in checkpoint
        ):
            checkpoint = checkpoint["state_dict"]

        if not isinstance(checkpoint, dict):
            raise TypeError(
                "MLP权重文件必须是state_dict字典"
            )

        scorer_state_dict = {
            key.removeprefix("scorer."): value
            for key, value in checkpoint.items()
            if key.startswith("scorer.")
        }

        expected_keys = {
            "0.weight",
            "0.bias",
            "2.weight",
            "2.bias",
        }

        actual_keys = set(
            scorer_state_dict.keys()
        )

        if actual_keys != expected_keys:
            raise RuntimeError(
                "scorer权重结构不正确，"
                f"期望：{sorted(expected_keys)}，"
                f"实际：{sorted(actual_keys)}"
            )

        self.scorer.load_state_dict(
            scorer_state_dict,
            strict=True,
        )

    def _prepare_embeddings(
        self,
        embeddings: (
            torch.Tensor
            | Sequence[Sequence[float]]
            | Sequence[float]
        ),
        name: str,
    ) -> torch.Tensor:
        """
        将输入转换为二维float32 Tensor：
        [数量, 768]
        """
        if isinstance(embeddings, torch.Tensor):
            tensor = embeddings.detach()
        else:
            tensor = torch.as_tensor(
                embeddings,
                dtype=torch.float32,
            )

        tensor = tensor.float()

        if tensor.ndim == 1:
            tensor = tensor.unsqueeze(0)

        if tensor.ndim != 2:
            raise ValueError(
                f"{name}必须是二维向量，"
                f"当前形状：{tuple(tensor.shape)}"
            )

        if tensor.shape[1] != self.hidden_size:
            raise ValueError(
                f"{name}维度必须为"
                f"{self.hidden_size}，"
                f"当前维度：{tensor.shape[1]}"
            )

        if not torch.isfinite(tensor).all():
            raise ValueError(
                f"{name}包含NaN或无穷值"
            )

        return tensor.to(
            self.device,
            non_blocking=True,
        )

    @torch.inference_mode()
    def score_matrix(
        self,
        issue_embeddings,
        candidate_embeddings,
    ) -> torch.Tensor:
        """
        批量计算所有争议焦点与所有候选案例之间的原始分数。

        输入：
            issue_embeddings:     [I, 768]
            candidate_embeddings: [C, 768]

        输出：
            score_matrix: [I, C]

        I表示争议焦点数量，C表示候选案例数量。
        """
        issues = self._prepare_embeddings(
            issue_embeddings,
            name="issue_embeddings",
        )

        candidates = self._prepare_embeddings(
            candidate_embeddings,
            name="candidate_embeddings",
        )

        issue_count = issues.shape[0]
        candidate_count = candidates.shape[0]

        # [I, 1, 768]
        issue_vectors = issues.unsqueeze(1)

        # [1, C, 768]
        candidate_vectors = candidates.unsqueeze(0)

        # 与原model.score完全一致：
        # [v_f * v_c, abs(v_f - v_c)]
        features = torch.cat(
            [
                issue_vectors * candidate_vectors,
                torch.abs(
                    issue_vectors
                    - candidate_vectors
                ),
            ],
            dim=-1,
        )

        # [I * C, 1536]
        features = features.reshape(
            issue_count * candidate_count,
            self.hidden_size * 2,
        )

        # [I * C]
        raw_scores = (
            self.scorer(features)
            .squeeze(-1)
        )

        # [I, C]
        score_matrix = raw_scores.reshape(
            issue_count,
            candidate_count,
        )

        return (
            score_matrix
            .detach()
            .cpu()
        )

    @torch.inference_mode()
    def score_candidates(
        self,
        issue_embeddings,
        candidate_embeddings,
        reliabilities: Sequence[float],
    ) -> dict[str, torch.Tensor]:
        """
        计算候选案例的争议焦点匹配分数。

        聚合方式：
            sum(reliability_i * score_i)
            / sum(reliability_i)

        最后使用Sigmoid映射到0～1。
        """
        pair_scores = self.score_matrix(
            issue_embeddings=issue_embeddings,
            candidate_embeddings=(
                candidate_embeddings
            ),
        )

        reliability_tensor = torch.as_tensor(
            reliabilities,
            dtype=torch.float32,
        )

        if reliability_tensor.ndim != 1:
            raise ValueError(
                "reliabilities必须是一维列表"
            )

        if (
            reliability_tensor.shape[0]
            != pair_scores.shape[0]
        ):
            raise ValueError(
                "争议焦点数量与可靠性权重数量不一致："
                f"{pair_scores.shape[0]} != "
                f"{reliability_tensor.shape[0]}"
            )

        if not torch.isfinite(
            reliability_tensor
        ).all():
            raise ValueError(
                "reliabilities包含NaN或无穷值"
            )

        weight_sum = float(
            reliability_tensor.sum().item()
        )

        if weight_sum > 0:
            raw_candidate_scores = (
                pair_scores
                * reliability_tensor.unsqueeze(1)
            ).sum(dim=0) / weight_sum
        else:
            raw_candidate_scores = torch.zeros(
                pair_scores.shape[1],
                dtype=torch.float32,
            )

        sigmoid_scores = torch.sigmoid(
            raw_candidate_scores
        )

        return {
            # 每个焦点与每个候选之间的原始分数
            "pair_scores": pair_scores,

            # reliability加权平均后的原始分数
            "raw_scores": raw_candidate_scores,

            # Sigmoid后的焦点匹配分数
            "scores": sigmoid_scores,
        }

    def warm_up(self) -> None:
        """
        使用少量假向量预热MLP。
        """
        issue_embeddings = torch.zeros(
            2,
            self.hidden_size,
            dtype=torch.float32,
        )

        candidate_embeddings = torch.zeros(
            4,
            self.hidden_size,
            dtype=torch.float32,
        )

        self.score_candidates(
            issue_embeddings=issue_embeddings,
            candidate_embeddings=(
                candidate_embeddings
            ),
            reliabilities=[0.8, 0.7],
        )

    def close(self) -> None:
        """
        释放MLP资源。
        """
        self.scorer.to("cpu")

        if torch.cuda.is_available():
            torch.cuda.empty_cache()