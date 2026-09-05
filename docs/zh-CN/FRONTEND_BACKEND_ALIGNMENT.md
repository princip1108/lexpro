# LexPro 前后端待对接清单

> 更新日期：2026-09-03  
> 用途：整理后端已经实现、但前端尚未接入或仅部分接入的功能，作为前端开发和联调依据。  
> 接口以当前 OpenAPI 和后端 Controller 为准：`http://127.0.0.1:8080/swagger-ui.html`。

## 1. 对接结论

当前登录、案件基础管理、TXT 上传解析、实体识别、法律要素识别、案件摘要、用户组织、工作台和报告基础流程已经接入真实后端。

当前最主要的前后端断点是：

```text
文档智能处理
  -> 人工确认（实体、法律要素、摘要已接入）
  -> 案卡生成与确认（已接入基础页面）
  -> 报告来源选择（案卡来源已接入，证据/推荐案例待补）
  -> 报告草稿编辑（已接入章节编辑与 lockVersion 保存）
  -> 复核、定稿和导出
```

建议优先完成案卡、智能结果确认、报告模板和报告草稿编辑，使案件材料到正式报告形成完整前端闭环。

## 2. P0：主流程必须补齐

### 2.1 案卡管理

后端已完成：

- 按文档、实体结果、法律要素结果或摘要生成案卡。
- 异步任务创建和状态轮询。
- 案卡历史与详情查询。
- 展示字段值、来源原文、来源位置和置信度。
- 单字段确认、修改后确认或拒绝。
- 所有字段处理完成后确认整张案卡。

前端状态（2026-08-22）：

- 已新增 `/case-cards` 路由和页面。
- 已接入文档、实体、法律要素、摘要来源读取、异步任务轮询、字段确认及整卡确认。
- 报告页面可读取已确认案卡；证据和典型案例来源仍待补齐。

接口：

```text
POST /api/v1/cases/{caseId}/case-card-jobs
GET  /api/v1/cases/{caseId}/case-card-jobs/{requestId}
GET  /api/v1/cases/{caseId}/case-cards
GET  /api/v1/cases/{caseId}/case-cards/{fillTaskId}
PUT  /api/v1/cases/{caseId}/case-cards/{fillTaskId}/fields/{fieldId}/confirmation
PUT  /api/v1/cases/{caseId}/case-cards/{fillTaskId}/confirmation
```

关键请求：

```json
{
  "fillMode": "AUTO",
  "sources": [
    { "sourceType": "DOCUMENT", "sourceId": 7 }
  ]
}
```

字段确认时，保留原值应省略 `value`，不能显式传入 `null`：

```json
{ "status": "CONFIRMED" }
```

修改后确认：

```json
{
  "status": "CONFIRMED",
  "value": "人工修订后的值"
}
```

拒绝字段：

```json
{ "status": "REJECTED" }
```

验收标准：

- 创建任务返回 `202`，前端轮询至 `SUCCESS` 或 `FAILED`。
- 成功后能查看案卡字段和来源。
- 所有字段处理前，整卡确认应被禁止。
- 字段全部处理后，整卡状态变为 `CONFIRMED`。
- 已确认案卡能在报告生成页面中选择。

### 2.2 实体识别人工确认

后端已完成人工修订后的首次确认，模型原始结果不会被覆盖。

前端状态：已支持六类实体统计与筛选、逐次出现高亮、列表到原文滚动定位、原文高亮反选列表、结果版本切换、结果编辑增删、首次确认和已确认只读展示。编辑文本时会重新按解析原文生成 UTF-16 位置；无法精确定位的项会明确标记，不伪造坐标。

接口：

```text
PUT /api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}/confirmation
```

请求体：

```json
{
  "finalEntities": {
    "entities": []
  }
}
```

验收标准：

- 只能确认一次。
- 原始识别结果保持不变。
- 最终实体仍须能够追溯到原文。

### 2.3 法律要素人工确认

后端已完成法律要素修订和首次确认，并校验证据引文及偏移。

前端状态：已支持要素、满足状态、置信度、引文编辑和首次确认；前端会先校验引文存在于解析原文。

仍需补充：

- 要素内容、满足状态和置信度编辑。
- 证据引文编辑和校验提示。
- 提交确认按钮和确认状态展示。

接口：

```text
PUT /api/v1/cases/{caseId}/documents/{docId}/legal-element-results/{elementResultId}/confirmation
```

请求体：

```json
{
  "finalElements": {
    "elements": []
  }
}
```

验收标准：

- 证据引文必须存在于解析原文中。
- 只能确认一次。
- 原始模型输出和人工确认结果分别保存。

### 2.4 摘要确认

后端支持指定摘要版本的首次确认。

前端需要补充：

- 确认按钮。
- 已确认状态、确认人和确认时间展示。
- 已确认版本禁止重复确认。

接口：

```text
PUT /api/v1/cases/{caseId}/summaries/{summaryId}/confirmation
```

### 2.5 报告模板管理

后端已完成：

- 创建模板及模板版本。
- 模板列表和详情。
- 激活草稿模板。
- 停用启用模板。
- 激活新版本时停用同模板编码的旧启用版本。

前端状态：已新增 `/report-templates` 管理页面，支持筛选、创建草稿、激活和停用。

接口：

```text
POST /api/v1/report-templates
GET  /api/v1/report-templates
GET  /api/v1/report-templates/{templateId}
PUT  /api/v1/report-templates/{templateId}/activation
PUT  /api/v1/report-templates/{templateId}/disablement
```

模板内容结构：

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

验收标准：

- 页面能按类型和状态筛选模板。
- 能查看版本、状态和章节结构。
- 只能激活 `DRAFT`，只能停用 `ACTIVE`。
- 报告生成时只允许选择 `ACTIVE` 模板。

### 2.6 报告草稿编辑

后端支持修改报告标题和章节正文，并通过 `lockVersion` 防止并发覆盖。

前端状态：已支持 DRAFT 章节编辑并携带 `lockVersion` 保存，409 由通用错误提示展示。

接口：

```text
PUT /api/v1/cases/{caseId}/reports/{reportId}/draft
```

请求体需要携带当前详情返回的 `lockVersion`：

```json
{
  "reportTitle": "审查报告",
  "content": {
    "sections": [
      {
        "code": "FACTS",
        "title": "案件事实",
        "content": "经审查查明……"
      }
    ]
  },
  "lockVersion": 0
}
```

验收标准：

- 仅 `DRAFT` 报告可编辑。
- 章节代码、顺序和标题必须与模板一致。
- 保存成功后使用响应中的新 `lockVersion`。
- 使用旧版本号保存应展示版本冲突提示。

### 2.7 报告来源选择

后端支持四类报告来源：

- 已确认案卡 `cardFillTaskId`。
- 卷宗证据 `evidence`。
- 法律要素结果 `legalElementResultIds`。
- 典型案例 `typicalCases`。

前端目前只提供案卡下拉框，其余来源未接入，并且应阻止无来源提交。

接口：

```text
POST /api/v1/cases/{caseId}/report-jobs
```

验收标准：

- 至少选择一个有效来源。
- 案卡必须为 `CONFIRMED`。
- 来源必须属于当前案件并处于可用状态。
- 报告类型必须与模板类型一致。

## 3. P1：案件与卷宗管理

### 3.1 当事人管理

后端支持列表、新增和修改。前端状态：案件详情已支持新增、编辑，并继续使用脱敏身份信息。

```text
GET  /api/v1/cases/{caseId}/parties
POST /api/v1/cases/{caseId}/parties
PUT  /api/v1/cases/{caseId}/parties/{partyId}
```

前端需要增加新增和编辑弹窗，并继续使用后端返回的脱敏身份信息。

### 3.2 案件人员分配

后端支持分配历史、新增分配和结束当前分配。前端状态：案件详情已支持新增分配、结束当前分配，并区分当前和历史记录。

```text
GET  /api/v1/cases/{caseId}/assignments
POST /api/v1/cases/{caseId}/assignments
POST /api/v1/cases/{caseId}/assignments/{assignmentId}/end
```

前端需要支持选择人员、职责和访问级别，并区分当前分配和已结束记录。

### 3.3 卷宗目录和标签

后端支持文件夹树、创建文件夹、标签列表和创建标签。前端状态：已新增 `/dossier-management` 页面接入上述能力。

```text
GET  /api/v1/cases/{caseId}/dossier/folders
POST /api/v1/cases/{caseId}/dossier/folders
GET  /api/v1/cases/{caseId}/dossier/tags
POST /api/v1/cases/{caseId}/dossier/tags
```

### 3.4 文件维护、删除和恢复

后端支持重命名、调整文件夹、更新标签、软删除和恢复。前端状态：已支持上传、列表、下载、重命名、移动文件夹、标签更新、软删除和恢复。

```text
PUT    /api/v1/cases/{caseId}/dossier/files/{dossierId}
DELETE /api/v1/cases/{caseId}/dossier/files/{dossierId}
POST   /api/v1/cases/{caseId}/dossier/files/{dossierId}/restore
GET    /api/v1/cases/{caseId}/dossier/files?includeDeleted=true
```

## 4. P2：典型案例与辅助管理

### 4.1 典型案例库

后端已完成案例导入、分页检索、筛选、详情、收藏和取消收藏。正式 Vue 推荐页面已嵌入典型案例库区域，并接入列表、详情、收藏和取消收藏。

```text
POST   /api/v1/typical-cases/imports
GET    /api/v1/typical-cases
GET    /api/v1/typical-cases/{typicalCaseId}
PUT    /api/v1/typical-cases/{typicalCaseId}/favorite
DELETE /api/v1/typical-cases/{typicalCaseId}/favorite
```

前端当前已实现：

- 关键词、案由、案件类型以及单个精确裁判日期筛选。
- 只看收藏。
- 案例详情抽屉。
- 收藏和取消收藏。
- 筛选项只显示名称，不展示各类别数据条目数。

管理员导入入口以及依赖 V6 扩展字段的来源、案例层级等筛选仍待后续实施；数据库结构变更和迁移执行需单独审批。

### 4.2 推荐结果补充操作

案件推荐任务、历史和基础结果已经接入。仍缺少：

- 从推荐结果进入典型案例详情。
- 收藏和取消收藏。
- 显示更多结构化筛选条件。

### 4.3 用户详情与权限目录

用户列表、创建、启停和密码重置已经接入，不需要重复开发。

仍可补充：

- `GET /api/v1/users/{userId}` 用户详情抽屉。
- `GET /api/v1/permissions` 权限目录展示。

### 4.4 基础健康状态

后端仅提供基础健康检查，前端暂无状态页：

```text
GET /api/health
GET /api/health/database
```

该项优先级较低，不应按完整监控平台设计。

## 5. 已对齐功能

以下功能已经存在真实前端和 API 调用，不应作为“完全缺失”重复开发：

- 登录、退出、当前用户和路由权限。
- 用户列表、创建用户、启停用户和重置密码。
- 组织树和角色列表。
- 案件列表、筛选、创建、详情和编辑。
- 当事人及分配历史的只读展示。
- TXT 文件上传、列表、下载和解析。
- 实体识别任务、历史和结果展示。
- 法律要素识别任务、历史和结果展示。
- 摘要生成、历史和详情展示。
- 报告生成、历史、复核、退回、定稿和 DOCX/PDF 导出。
- 案件推荐任务、推荐历史和基础结果展示。
- Dashboard、知识内容和工作任务。

## 6. 不能仅由前端补齐的能力

以下能力后端尚未提供完整业务接口，不属于单纯前端对接任务：

- 组织新增、编辑、启停和层级调整。
- 角色新增、编辑和权限分配。
- 修改现有用户所属组织或角色。
- 审计日志查询和管理。
- AI 调用统计、异步队列监控和告警。
- 模型配置管理。
- PDF、Word、图片和 OCR 的 MinerU 适配代码已完成；真实服务器联调和启用仍待验收。
- 案件正式状态流转。
- 完整生产监控平台。

## 7. 权限与通用约定

前端隐藏按钮不能替代后端授权，仍应根据当前用户权限控制交互入口。

主要权限：

- `CASE_READ`：查看案件、案卡和报告。
- `CASE_WRITE`：编辑案件及案件相关数据。
- `CASE_ASSIGN`：分配或结束案件人员职责。
- `DOSSIER_MANAGE`：卷宗文件夹、标签和文件维护。
- `AI_EXECUTE`：启动 AI 任务。
- `REPORT_MANAGE`：案卡确认、模板管理和报告流转。
- `RECOMMENDATION_USE`：典型案例检索和推荐。
- `USER_MANAGE`：用户管理。

通用约定：

- 成功响应直接返回资源对象，不使用统一外层包装。
- 错误响应为标准 `ProblemDetail`，前端应展示 `detail`，并保留 `errorCode` 用于分支处理。
- 异步任务创建返回 `202`，前端使用 `requestId` 轮询任务接口。
- 分页接口使用共享分页响应。
- 时间字段为带时区的 ISO-8601 字符串。
- 报告草稿等并发更新必须传递最新 `lockVersion`。
- 页面切换案件后必须清空上一个案件的详情、选择项和任务状态。

## 8. 推荐实施顺序

1. 案卡生成、字段确认和整卡确认页面。
2. 实体、法律要素和摘要人工确认。
3. 报告模板管理。
4. 报告草稿编辑和完整来源选择。
5. 当事人维护和案件人员分配。
6. 卷宗文件夹、标签、文件维护、删除和恢复。
7. 典型案例库、详情和收藏。
8. 用户详情、权限目录和基础健康状态。
