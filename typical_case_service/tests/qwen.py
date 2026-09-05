from app.core.config import settings
from app.engines.qwen_engine import QwenEngine


def main() -> None:
    engine = QwenEngine(
        model_path=settings.qwen_model_path,
        max_seq_length=settings.QWEN_MAX_SEQ_LENGTH,
        max_new_tokens=settings.QWEN_MAX_NEW_TOKENS,
        temperature=settings.QWEN_TEMPERATURE,
        top_p=settings.QWEN_TOP_P,
        repetition_penalty=(
            settings.QWEN_REPETITION_PENALTY
        ),
        load_in_4bit=settings.QWEN_LOAD_IN_4BIT,
    )

    try:
        case_facts = """
        被告人张某因琐事与被害人李某发生争执，
        后持刀刺伤李某腹部。经鉴定，李某的损伤
        程度为重伤二级。张某案发后主动到公安机关
        投案，并如实供述了主要犯罪事实。
        """

        issues = engine.extract_issues(
            case_facts
        )

        print("Qwen加载成功")
        print("争议焦点数量：", len(issues))

        for index, issue in enumerate(
            issues,
            start=1,
        ):
            print(f"{index}. {issue}")

    finally:
        engine.close()


if __name__ == "__main__":
    main()