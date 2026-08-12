# M6 案卡和报告

[English](../M6_CASE_CARDS_AND_REPORTS.md)

> 状态：已于 2026-07-30 实现。M6 直接复用 V1/V2/V3 基线，不新增数据库迁移。

## 范围和数据存储

M6 包括案卡异步生成与确认、报告模板版本、报告异步生成、草稿编辑、复核/定稿、规范化引用及 DOCX/PDF 导出。

使用现有的 `case_card_fill_task`、`case_card_task_source`、`case_card_field`、`report_template`、`case_report`、`report_evidence`、`report_legal_element_result` 和 `report_typical_case_reference`。复核退回原因及所有状态变化/导出操作写入 `operation_log`，不增加报告修订表。

## JSON 协议

模板正文是一个包含 1 到 50 个有序段落的对象。`code` 必须是唯一的大写蛇形编码，`instructions` 可选。

```json
{
  "sections": [
    {
      "code": "FACTS",
      "title": "案件事实",
      "instructions": "概述已确认的案件事实"
    }
  ]
}
```

生成或人工编辑的报告使用同样的有序段落列表，以非空 `content` 代替 `instructions`。

```json
{
  "sections": [
    {
      "code": "FACTS",
      "title": "案件事实",
      "content": "经审查查明……"
    }
  ]
}
```

段落数量、顺序、编码和标题必须与所选模板完全一致。创建模板、接收模型输出、编辑草稿、送审和导出时都会校验该协议。

## 生命周期

| 资源 | 流转 | 规则 |
|---|---|---|
| 模板 | `DRAFT -> ACTIVE` | 启用草稿时，在同一事务中禁用相同 `templateCode` 的上一 `ACTIVE` 版本 |
| 模板 | `ACTIVE -> DISABLED` | 草稿不能直接禁用；`DISABLED` 是终态，不能重新启用 |
| 报告 | `GENERATING -> DRAFT` | 生成成功；只有此时新版本才成为当前版本 |
| 报告 | `GENERATING -> FAILED` | 保留失败版本，原当前报告不变 |
| 报告 | `DRAFT -> REVIEWING` | 使用乐观锁 `lockVersion` 人工送审 |
| 报告 | `REVIEWING -> DRAFT` | 必须填写退回原因并携带 `lockVersion` |
| 报告 | `REVIEWING -> FINAL` | 携带 `lockVersion` 人工定稿；`FINAL` 不可修改 |

## 权限

| 操作 | 系统权限 | 案件访问级别 |
|---|---|---|
| 查询案卡、任务、报告或导出 | `CASE_READ` | 案件可见 |
| 生成案卡/报告 | `REPORT_MANAGE` + `AI_EXECUTE` | `EDIT` |
| 确认案卡字段/整卡、编辑草稿、送审或退回 | `REPORT_MANAGE` | `EDIT` |
| 报告定稿 | `REPORT_MANAGE` | `MANAGE` |
| 管理报告模板 | `REPORT_MANAGE` | 不属于具体案件 |

Controller 和 Service 层都会执行授权。跨案件来源由服务检查和数据库复合外键共同阻止。

## 案卡和报告

案卡来源类型为 `DOCUMENT`、`ENTITY`、`LEGAL_ELEMENT` 和 `SUMMARY`。生成字段保留类型化来源 ID、准确原文、可选位置/卷宗及置信度。每个字段只能确认或驳回一次；所有字段处理完成后才能确认整张案卡。

报告生成接收启用模板以及明确选择的案卡、证据、法律要素和典型案例引用。证据、法律要素和典型案例存入规范化关系表。重新生成会创建新版本；`GENERATING` 或 `FAILED` 版本不会替换最后一份成功的当前报告。

完整接口表见 [API_CONVENTIONS.md](API_CONVENTIONS.md)。公开路径统一位于 `/api/v1`，包括案卡任务/案卡、模板创建/启用/禁用、报告任务/历史/详情/草稿编辑、送审/退回/定稿和导出。

## 导出

`GET /api/v1/cases/{caseId}/reports/{reportId}/exports/{format}` 支持把 `DRAFT`、`REVIEWING` 和 `FINAL` 报告导出为 `DOCX` 或 `PDF`。DOCX 可继续编辑；PDF 自动换行并按 A4 自动分页；非定稿文件显示 `草稿 - <status>`。

PDF 导出必须配置可读取的中文 TTF：

```text
LEXPRO_REPORT_PDF_FONT_PATH=C:\Windows\Fonts\simhei.ttf
```

字体路径只属于本机配置，API 不会返回。未配置或文件不可读时分别返回 `503 REPORT_PDF_FONT_NOT_CONFIGURED` 或 `503 REPORT_PDF_FONT_UNAVAILABLE`。下载响应使用附件、`Cache-Control: no-store` 和 `X-Content-Type-Options: nosniff`。

## 人工验收

1. 使用虚构案件和满足上表权限/案件访问级别的用户。真实案件数据未经另行批准不能发送给外部 AI。
2. 创建并启用一个模板，再创建、启用第二版，确认第一版自动变成 `DISABLED`。
3. 使用所需的类型化来源启动案卡任务，轮询任务接口，逐个确认/驳回字段，最后确认整张案卡。
4. 使用启用模板和明确来源启动报告任务，轮询到成功，并确认生成成功前上一当前报告不变。
5. 使用响应中的 `lockVersion` 编辑草稿；再次使用旧版本号必须返回 `409`。
6. 送审、填写原因退回、再次送审，然后使用案件 `MANAGE` 分配定稿；定稿后再次编辑必须返回 `409`。
7. 导出并打开 DOCX/PDF，检查中文、分页和版式，并确认非定稿导出显示草稿标记。
8. 通过批准的只读检查查询 `operation_log`，确认生成、确认、状态流转和导出均有审计，同时 API 响应不含 Prompt、密钥或本地路径。
