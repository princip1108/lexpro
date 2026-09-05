from pathlib import Path

import torch


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]

    weight_path = (
        project_root
        / "weights"
        / "qwen3_14B_DELTA.pt"
    )

    if not weight_path.exists():
        raise FileNotFoundError(
            f"权重文件不存在：{weight_path.resolve()}"
        )

    state_dict = torch.load(
        weight_path,
        map_location="cpu",
        weights_only=True,
    )

    if (
        isinstance(state_dict, dict)
        and "state_dict" in state_dict
    ):
        state_dict = state_dict["state_dict"]

    print("权重文件：", weight_path.resolve())
    print("参数数量：", len(state_dict))
    print("-" * 80)

    for name, tensor in state_dict.items():
        print(name, tuple(tensor.shape))


if __name__ == "__main__":
    main()