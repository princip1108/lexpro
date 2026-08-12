# M3 案件流程

[English](../M3_CASE_WORKFLOW.md)

## 已交付范围

- 后端新增 `casework` 模块，遵循 `Controller -> Service -> Mapper` 分层。
- 案件列表、详情、新建、修改接口：`/api/v1/cases`。
- 参与人列表、新建、修改接口：`/api/v1/cases/{caseId}/parties`。
- 分配历史、新增分配、结束分配接口：`/api/v1/cases/{caseId}/assignments`。
- 前端 `TodoCases` 和首页第一批案件面板改为调用真实案件接口。
- 改变案件的操作会写入 `operation_log` 审计记录。

## 案件访问规则

- 用户可以看到自己创建的案件，或当前 `case_assignment.ended_at IS NULL` 分配给自己的案件。
- 读取要求 `CASE_READ`。
- 修改案件/参与人要求 `CASE_WRITE`，服务层还要求案件访问级别至少为 `EDIT`。
- 修改分配要求 `CASE_ASSIGN`，服务层还要求案件访问级别为 `MANAGE`。
- 无权访问或不可见的案件返回 `404`，避免泄露案件是否存在。

## 当前行为

- 新建案件固定为 `PENDING`。
- 创建人自动获得当前 `ASSIGNEE` + `MANAGE` 分配。
- 案件列表支持分页、关键词、状态、案件类型和超期筛选。
- 超期判断为 `deadline_at < CURRENT_TIMESTAMP`，且状态不是 `CLOSED` 或 `ARCHIVED`。
- 普通案件修改只改基础信息，不修改 `case_status`。
- 新增分配只允许启用状态用户，重复的当前分配会在数据库/服务层被拒绝。
- 参与人接口不会查询或返回身份证原值 `identity_number` 和哈希 `identity_number_hash`。

## 暂缓决策

- 案件状态流转矩阵，以及哪些角色可以执行状态流转。
- 案号生成规则；当前只做已有唯一性保护。
- 身份证加密算法、密钥来源、哈希/查询、脱敏格式和修改规则。

## 人工验收步骤

1. 启动 PostgreSQL、后端和前端，并使用真实开发管理员登录。
2. 使用拥有 `CASE_READ`、`CASE_WRITE`、`CASE_ASSIGN` 的用户。
3. 打开 `/todo-cases`，新建案件，确认状态为 `PENDING`。
4. 修改案件基础信息，确认状态没有变化。
5. 新增参与人，确认响应只在已有数据时返回 `identityNumberMasked`，不返回身份证原值字段。
6. 给启用状态用户新增分配，再结束该分配。
7. 换一个没有分配的用户，确认看不到该案件。
8. 检查 `operation_log` 中存在案件创建、修改、参与人和分配相关事件。
