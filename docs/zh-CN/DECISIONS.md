# LexPro 技术决策

[English](../DECISIONS.md)

| 编号 | 状态 | 决策 | 原因 |
|---|---|---|---|
| ADR-001 | 已接受 | 使用模块化 Spring Boot 单体应用。 | 当前团队和项目规模不需要分布式服务。 |
| ADR-002 | 已接受 | 使用 Java 21、Spring Boot 3.5.x、Maven 和 MyBatis-Plus。 | 符合已选技术栈，也适合已有数据库结构。 |
| ADR-003 | 已接受 | 后端代码按业务功能组织。 | 让同一能力的 Controller、Service、DTO 和持久化代码保持集中。 |
| ADR-004 | 已接受 | 使用 PostgreSQL `lexpro` Schema 和 32 表 V3 基线。 | 这是已经审查并规范化的最终数据库。 |
| ADR-005 | 已接受 | 接口返回 DTO，不直接返回持久化 Entity。 | 防止数据库结构和敏感字段泄漏。 |
| ADR-006 | 已接受 | 成功返回资源 JSON，错误返回 `ProblemDetail`。 | 保留 HTTP 语义，并向前端提供稳定错误码。 |
| ADR-007 | 已接受 | 不开放公众注册，账号由内部管理员维护。 | 符合内部法律业务系统的使用方式。 |
| ADR-008 | 待最终确认 | 使用 Spring Security、BCrypt 和 JWT Access Token。 | 支持前端无状态认证，但 Token 有效期尚未确定。 |
| ADR-009 | 已接受 | 文件二进制保存在 PostgreSQL 之外，并通过存储接口访问。 | 开发期可使用本地存储，后续可替换为 MinIO。 |
| ADR-010 | 已接受 | 只为文档和 AI 处理增加独立 Python 服务。 | Java 负责业务数据，Python 使用更适合的 AI 生态。 |
| ADR-011 | 已接受 | 延后确定向量维度和索引。 | 向量维度和操作符必须匹配最终 Embedding 模型。 |
| ADR-012 | 已接受 | 当前后端不实现公告、日程和通知。 | 它们目前只是前端原型，不属于已确认核心数据库。 |

## 待确认决策

- JWT Access Token 有效期以及是否需要 Refresh Token。
- 本地存储根目录和生产 MinIO 目录结构。
- 身份证件字段的加密和脱敏机制。
- 案件状态流转矩阵。
- AI 服务提供方和数据分类限制。
