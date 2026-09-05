import logging
from pathlib import Path

import torch
from transformers import AutoModel, AutoTokenizer


logger = logging.getLogger(__name__)


class DeltaEngine:
    """
    DELTA文本编码器。

    模型在服务启动时加载一次，
    后续请求只调用encode方法。
    """

    def __init__(
        self,
        model_path: str | Path,
        device: str = "cuda:0",
        batch_size: int = 16,
        max_length: int = 512,
    ) -> None:
        self.model_path = Path(model_path)
        self.batch_size = batch_size
        self.max_length = max_length

        if not self.model_path.exists():
            raise FileNotFoundError(
                f"DELTA模型目录不存在：{self.model_path}"
            )

        if device.startswith("cuda") and not torch.cuda.is_available():
            logger.warning(
                "CUDA不可用，DELTA自动切换到CPU"
            )
            device = "cpu"

        self.device = torch.device(device)

        logger.info(
            "正在加载DELTA模型，path=%s，device=%s",
            self.model_path,
            self.device,
        )

        self.tokenizer = AutoTokenizer.from_pretrained(
            str(self.model_path),
            local_files_only=True,
            trust_remote_code=True,
        )

        self.model = AutoModel.from_pretrained(
            str(self.model_path),
            local_files_only=True,
            trust_remote_code=True,
        )

        self.model.to(self.device)
        self.model.eval()

        self.embedding_dim = int(
            self.model.config.hidden_size
        )

        logger.info(
            "DELTA模型加载完成，向量维度=%s",
            self.embedding_dim,
        )

    def encode(
            self,
            texts: list[str],
            batch_size: int | None = None,
            max_length: int | None = None,
    ) -> torch.Tensor:
        """
        使用Mean Pooling + L2归一化批量编码文本。

        返回：
            CPU上的二维Tensor：
            [文本数量, embedding_dim]
        """
        if not texts:
            return torch.empty(
                (0, self.embedding_dim),
                dtype=torch.float32,
            )

        cleaned_texts = [
            text.strip() if text else ""
            for text in texts
        ]

        current_batch_size = batch_size or self.batch_size
        current_max_length = max_length or self.max_length

        embeddings: list[torch.Tensor] = []

        with torch.inference_mode():
            for start in range(
                    0,
                    len(cleaned_texts),
                    current_batch_size,
            ):
                batch = cleaned_texts[
                    start:start + current_batch_size
                ]

                encoded = self.tokenizer(
                    batch,
                    padding=True,
                    truncation=True,
                    max_length=current_max_length,
                    return_tensors="pt",
                )

                encoded = {
                    key: value.to(self.device)
                    for key, value in encoded.items()
                }

                output = self.model(**encoded)

                last_hidden = output.last_hidden_state
                attention_mask = encoded["attention_mask"]

                # 将padding位置排除在Mean Pooling之外
                mask = attention_mask.unsqueeze(-1).to(
                    last_hidden.dtype
                )

                pooled = (
                        (last_hidden * mask).sum(dim=1)
                        / mask.sum(dim=1).clamp(min=1e-9)
                )

                # L2归一化
                pooled = torch.nn.functional.normalize(
                    pooled,
                    p=2,
                    dim=-1,
                )

                embeddings.append(
                    pooled.detach().cpu().float()
                )

        return torch.cat(embeddings, dim=0)

    def encode_one(
        self,
        text: str,
    ) -> torch.Tensor:
        """
        编码单条文本，返回一维向量。
        """
        return self.encode([text])[0]

    def warm_up(self) -> None:
        """
        服务启动时预热一次。
        """
        logger.info("开始预热DELTA模型")

        _ = self.encode(
            ["DELTA模型预热文本。"],
            batch_size=1,
            max_length=32,
        )

        if self.device.type == "cuda":
            torch.cuda.synchronize(self.device)

        logger.info("DELTA模型预热完成")

    def close(self) -> None:
        """
        服务关闭时释放模型资源。
        """
        logger.info("正在释放DELTA模型")

        self.model = None
        self.tokenizer = None

        if self.device.type == "cuda":
            torch.cuda.empty_cache()

        logger.info("DELTA模型已释放")