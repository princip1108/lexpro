import torch


def calculate_issue_reliability(
    issues: list[str],
    sentences: list[str],
    issue_embeddings: torch.Tensor,
    sentence_embeddings: torch.Tensor,
) -> list[dict]:
    """
    计算每个争议焦点与案件事实句子的最大相似度。

    最大相似度即：
    1. reliability
    2. 事实覆盖度
    3. 后续使用的焦点权重
    """
    if not issues:
        raise ValueError("争议焦点不能为空")

    if not sentences:
        raise ValueError("案件事实分句结果不能为空")

    if issue_embeddings.ndim != 2:
        raise ValueError("issue_embeddings必须是二维Tensor")

    if sentence_embeddings.ndim != 2:
        raise ValueError("sentence_embeddings必须是二维Tensor")

    if issue_embeddings.shape[0] != len(issues):
        raise ValueError("焦点数量与焦点向量数量不一致")

    if sentence_embeddings.shape[0] != len(sentences):
        raise ValueError("事实句子数量与句子向量数量不一致")

    if issue_embeddings.shape[1] != sentence_embeddings.shape[1]:
        raise ValueError("焦点向量与句子向量维度不一致")

    issue_embeddings = (
        issue_embeddings.detach().cpu().float()
    )
    sentence_embeddings = (
        sentence_embeddings.detach().cpu().float()
    )

    # DELTA向量已经经过L2归一化，因此点积就是余弦相似度
    # [争议焦点数量, 事实句子数量]
    similarity_matrix = torch.matmul(
        issue_embeddings,
        sentence_embeddings.T,
    )

    reliability_scores, matched_indices = (
        similarity_matrix.max(dim=1)
    )

    results: list[dict] = []

    for issue_index, issue_text in enumerate(issues):
        matched_index = int(
            matched_indices[issue_index].item()
        )

        reliability = float(
            reliability_scores[issue_index].item()
        )

        results.append(
            {
                "issue_id": issue_index,
                "issue_text": issue_text,
                "reliability": reliability,

                # 按你的定义，weight就是最大事实覆盖度
                "weight": reliability,

                "matched_sentence_index": matched_index,
                "matched_sentence": sentences[matched_index],
            }
        )

    return results