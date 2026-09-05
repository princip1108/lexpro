import gc
import logging
import re
import threading
from pathlib import Path

import torch
from unsloth import FastLanguageModel


logger = logging.getLogger(__name__)


class QwenEngine:
    """
    Qwen3-14B争议焦点提取引擎。

    模型在服务启动时加载一次，
    后续请求只调用extract_issues()。
    """

    SYSTEM_PROMPT = "你是一名经验丰富的中国执业律师。"

    USER_PROMPT_TEMPLATE = """
你是一名专业中国律师，请严格依据中国现行有效法律，从以下案件事实中精准提取法律争议焦点。

要求：
- 仅输出争议焦点，不要任何解释、标题、编号或额外文字；
- 用中文表述，语言简洁、专业；
- 多个焦点用中文逗号分隔，整体用英文方括号包围；
- 请直接给出最终结论，不要输出任何推理过程、思考步骤或分析内容。

输出格式示例：
[借款合同是否成立，利息约定是否合法，诉讼时效是否已过]

案件事实：
{case_facts}
""".strip()

    def __init__(
        self,
        model_path: str | Path,
        max_seq_length: int = 32768,
        max_new_tokens: int = 2048,
        temperature: float = 0.1,
        top_p: float = 0.95,
        repetition_penalty: float = 1.05,
        load_in_4bit: bool = False,
    ) -> None:
        self.model_path = Path(model_path)

        self.max_seq_length = max_seq_length
        self.max_new_tokens = max_new_tokens
        self.temperature = temperature
        self.top_p = top_p
        self.repetition_penalty = repetition_penalty
        self.load_in_4bit = load_in_4bit

        if not self.model_path.exists():
            raise FileNotFoundError(
                f"Qwen模型目录不存在：{self.model_path}"
            )

        logger.info(
            "正在加载Qwen模型，path=%s，load_in_4bit=%s",
            self.model_path,
            self.load_in_4bit,
        )

        self.model, self.tokenizer = (
            FastLanguageModel.from_pretrained(
                model_name=str(self.model_path),
                max_seq_length=self.max_seq_length,
                dtype=None,
                load_in_4bit=self.load_in_4bit,
                local_files_only=True,
            )
        )

        self.tokenizer.padding_side = "left"

        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = (
                self.tokenizer.eos_token
            )

        FastLanguageModel.for_inference(self.model)

        self.device = next(
            self.model.parameters()
        ).device

        # 防止多个请求同时执行generate导致显存问题
        self._generate_lock = threading.Lock()

        logger.info(
            "Qwen模型加载完成，device=%s",
            self.device,
        )

    @staticmethod
    def _clean_thinking_output(raw_output: str) -> str:
        """
        删除Qwen输出中的思考过程。
        """
        if not raw_output:
            return ""

        cleaned = raw_output.strip()

        # 原清洗逻辑：优先保留最后一个</think>后的内容
        if "</think>" in cleaned:
            cleaned = cleaned.rsplit(
                "</think>",
                1,
            )[1].strip()

        # 兼容完整的<think>...</think>结构
        cleaned = re.sub(
            r"<think>.*?</think>",
            "",
            cleaned,
            flags=re.DOTALL | re.IGNORECASE,
        ).strip()

        cleaned = cleaned.replace(
            "<think>",
            "",
        ).replace(
            "</think>",
            "",
        ).strip()

        return cleaned

    @classmethod
    def _parse_issues(
        cls,
        raw_output: str,
    ) -> list[str]:
        """
        将模型输出解析为争议焦点列表。

        优先解析：
            [焦点1，焦点2，焦点3]

        方括号缺失时进行容错切分。
        """
        cleaned = cls._clean_thinking_output(
            raw_output
        )

        if not cleaned:
            raise ValueError("Qwen清理后的输出为空")

        bracket_match = re.search(
            r"\[([^\[\]]+)\]",
            cleaned,
            flags=re.DOTALL,
        )

        if bracket_match:
            issue_content = bracket_match.group(1)
        else:
            logger.warning(
                "Qwen输出未包含标准方括号，执行容错解析：%s",
                cleaned[:200],
            )
            issue_content = cleaned

        issue_content = re.sub(
            r"^\s*(?:法律)?争议焦点\s*[:：]\s*",
            "",
            issue_content,
        )

        raw_issues = re.split(
            r"[，,；;\n]+",
            issue_content,
        )

        issues: list[str] = []
        seen: set[str] = set()

        for raw_issue in raw_issues:
            issue = raw_issue.strip(
                " \t\r\n[]【】"
                "\"'“”‘’"
            )

            # 删除可能出现的编号
            issue = re.sub(
                r"^\s*(?:"
                r"\d+[.、)）]|"
                r"[一二三四五六七八九十]+[.、]"
                r")\s*",
                "",
                issue,
            ).strip()

            if len(issue) < 2:
                continue

            if issue in seen:
                continue

            seen.add(issue)
            issues.append(issue)

        if not issues:
            raise ValueError(
                f"无法从Qwen输出中解析争议焦点：{cleaned}"
            )

        return issues

    def _build_input_text(
        self,
        case_facts: str,
    ) -> str:
        prompt = self.USER_PROMPT_TEMPLATE.format(
            case_facts=case_facts
        )

        messages = [
            {
                "role": "system",
                "content": self.SYSTEM_PROMPT,
            },
            {
                "role": "user",
                "content": prompt,
            },
        ]

        # 某些Qwen3 tokenizer支持直接关闭thinking
        try:
            return self.tokenizer.apply_chat_template(
                messages,
                tokenize=False,
                add_generation_prompt=True,
                enable_thinking=False,
            )
        except TypeError:
            # 不支持enable_thinking参数时回退
            return self.tokenizer.apply_chat_template(
                messages,
                tokenize=False,
                add_generation_prompt=True,
            )

    def _generate_raw(
        self,
        case_facts: str,
        max_new_tokens: int | None = None,
    ) -> str:
        """
        执行一次Qwen生成，返回未经解析的原始结果。
        """
        if not case_facts:
            raise ValueError("案件事实不能为空")

        case_facts = case_facts.strip()

        if len(case_facts) < 5:
            raise ValueError("案件事实过短")

        current_max_new_tokens = (
            max_new_tokens
            if max_new_tokens is not None
            else self.max_new_tokens
        )

        max_input_length = (
            self.max_seq_length
            - current_max_new_tokens
        )

        if max_input_length <= 0:
            raise ValueError(
                "max_new_tokens不能大于或等于"
                "max_seq_length"
            )

        input_text = self._build_input_text(
            case_facts
        )

        inputs = self.tokenizer(
            input_text,
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=max_input_length,
        ).to(self.device)

        input_length = inputs[
            "input_ids"
        ].shape[1]

        with self._generate_lock:
            with torch.inference_mode():
                outputs = self.model.generate(
                    **inputs,
                    max_new_tokens=current_max_new_tokens,
                    do_sample=self.temperature > 0,
                    temperature=self.temperature,
                    top_p=self.top_p,
                    repetition_penalty=(
                        self.repetition_penalty
                    ),
                    eos_token_id=(
                        self.tokenizer.eos_token_id
                    ),
                    pad_token_id=(
                        self.tokenizer.pad_token_id
                    ),
                )

        generated_tokens = outputs[
            0,
            input_length:,
        ]

        response = self.tokenizer.decode(
            generated_tokens,
            skip_special_tokens=True,
        ).strip()

        if not response:
            raise ValueError("Qwen模型输出为空")

        return response

    def extract_issues(
        self,
        case_facts: str,
    ) -> list[str]:
        """
        从案件事实中提取争议焦点。
        """
        raw_output = self._generate_raw(
            case_facts=case_facts
        )

        issues = self._parse_issues(
            raw_output
        )

        logger.info(
            "争议焦点提取完成，数量=%s",
            len(issues),
        )

        return issues

    def warm_up(self) -> None:
        """
        服务启动时预热Qwen。

        仅生成少量Token，避免第一次请求承担初始化开销。
        """
        logger.info("开始预热Qwen模型")

        response = self._generate_raw(
            case_facts=(
                "原告主张被告未按约定偿还借款，"
                "被告对借款金额及利息提出异议。"
            ),
            max_new_tokens=64,
        )

        if not response:
            raise RuntimeError("Qwen模型预热失败")

        if self.device.type == "cuda":
            torch.cuda.synchronize(self.device)

        logger.info("Qwen模型预热完成")

    def close(self) -> None:
        """
        服务关闭时释放Qwen资源。
        """
        logger.info("正在释放Qwen模型")

        self.model = None
        self.tokenizer = None

        gc.collect()

        if torch.cuda.is_available():
            torch.cuda.empty_cache()

        logger.info("Qwen模型已释放")