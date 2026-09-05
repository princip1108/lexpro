from __future__ import annotations


ENTITY_LABELS = ["犯罪嫌疑人", "地名", "组织机构名", "时间", "罪名", "毒品种类"]
ENTITY_DEFINITIONS = {
    "犯罪嫌疑人": "涉嫌实施违法犯罪行为的自然人或法人。示例：张三（犯罪嫌疑人）",
    "地名": "行政区域。示例：北京市朝阳区（地名）",
    "组织机构名": "正式机构名称。示例：中国农业银行（组织机构名）",
    "时间": "包括年月日。示例：2023年5月12日（时间）",
    "罪名": "法律名称。示例：故意伤害罪（罪名）",
    "毒品种类": "毒品名称。示例：冰毒（毒品种类）",
}


def create_full_prompt(text: str) -> str:
    prompt = "请你作为法律领域的专家，从询问笔录中的问答对中提出指定类别的实体，并按照指定格式返回结果。\n实体类别定义：\n"
    for label, definition in ENTITY_DEFINITIONS.items():
        prompt += f"- {label}: {definition}\n"
    prompt += "注意：\n1. 严格按照文本内容提取\n2. 类别必须是：" + ", ".join(ENTITY_LABELS)
    prompt += """
示例1：
## 文本内容:
....
## 提取结果:
#### category：实体类别
#### entity：实体文本
<Labeled End>
如果无实体示例2：
## 文本内容:
....
## 提取结果:
#### None
<Labeled End>
"""
    return prompt + f"现在开始提取：\n## 文本内容:\n{text}\n## 提取结果:\n"
