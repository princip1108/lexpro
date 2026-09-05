from app.core.config import settings
from app.engines.delta_engine import DeltaEngine
import torch

def main() -> None:
    engine = DeltaEngine(
        model_path=settings.delta_model_path,
        device=settings.DELTA_DEVICE,
        batch_size=settings.DELTA_BATCH_SIZE,
        max_length=settings.DELTA_MAX_LENGTH,
    )

    try:
        print(1)
        texts = [
            "被告人张某持刀将被害人刺伤。",
            "被害人的伤情经鉴定为重伤二级。",
        ]

        embeddings = engine.encode(texts)

        print("DELTA加载成功")
        print("向量形状：", embeddings.shape)
        print("向量设备：", embeddings.device)
        print(
            "第一条向量L2范数：",
            torch.linalg.vector_norm(embeddings[0]).item(),
        )

        print(
            "第二条向量L2范数：",
            torch.linalg.vector_norm(embeddings[1]).item(),
        )

    finally:
        engine.close()


if __name__ == "__main__":
    main()