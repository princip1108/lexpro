import re


def clean_case_fact(text: str) -> str:
    """
    清理案件事实文本。
    """
    if not text:
        return ""

    text = text.strip()

    # 合并连续空格、制表符
    text = re.sub(r"[ \t]+", " ", text)

    # 合并连续换行
    text = re.sub(r"\n+", "\n", text)

    return text


def split_sentences(
    text: str,
    min_length: int = 4,
) -> list[str]:
    """
    对案件事实进行分句。

    按照：
    。！？；
    以及换行符切分。
    """
    cleaned_text = clean_case_fact(text)

    if not cleaned_text:
        return []

    raw_sentences = re.split(
        r"[。！？；\n]+",
        cleaned_text,
    )

    sentences = [
        sentence.strip()
        for sentence in raw_sentences
        if len(sentence.strip()) >= min_length
    ]

    # 避免短文本全部被过滤
    if not sentences and cleaned_text:
        sentences = [cleaned_text]

    return sentences