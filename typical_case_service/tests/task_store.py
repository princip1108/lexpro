import torch

from app.cache.task_store import TaskStore


def main() -> None:
    store = TaskStore()

    issue_embeddings = torch.randn(3, 768)

    analysis_id = store.save_analysis(
        {
            "case_fact": "测试案件事实",
            "query_embedding": torch.randn(768),
            "issues": [
                {
                    "issue_id": 0,
                    "issue_text": "测试争议焦点",
                    "weight": 0.8,
                }
            ],
            "issue_embeddings": issue_embeddings,
        }
    )

    analysis = store.get_analysis(analysis_id)

    print("analysis_id：", analysis_id)
    print("案件事实：", analysis["case_fact"])
    print(
        "争议焦点向量形状：",
        analysis["issue_embeddings"].shape,
    )

    assert analysis["case_fact"] == "测试案件事实"
    assert analysis["issue_embeddings"].shape == (3, 768)

    print("TaskStore测试成功")


if __name__ == "__main__":
    main()