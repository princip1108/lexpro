# LexPro

[English](README.md)

LexPro 是面向法律案件审查的应用。本仓库包含 Vue 前端、Spring Boot 后端、最终 PostgreSQL 数据库结构以及需求和设计源文件。

## 仓库结构

```text
lexpro/
|- src/                         # Vue 3 前端
|- backend/lexpro-backend/      # Spring Boot 后端
|- DBM/                         # PostgreSQL V1/V2/V3 脚本和 ER 文档
|- docs/                        # 英文工程控制文档
|- docs/zh-CN/                  # 中文工程文档
|- BACKEND_DEVELOPMENT_OVERVIEW.md
|- AGENTS.md                    # AI 必须遵守的英文规则
|- AGENTS.zh-CN.md              # 规则的中文解释
|- package.json
`- vite.config.js
```

## 当前状态

- 前端：已接入真实登录和路由保护；后续业务页面仍按说明使用 Mock JSON。
- 数据库：V1 + V2 + V3 已完成，`lexpro` Schema 最终为 32 张业务表。
- 后端：M0-M2 已完成，包括 JWT 认证、用户管理、组织/RBAC 查询、标准错误、OpenAPI、请求关联和审计日志。
- 尚未实现：案件完整流程、卷宗存储、智能处理、审查报告和类案推荐接口。

## 中文文档

- [中文文档索引](docs/zh-CN/README.md)
- [实施计划](docs/zh-CN/IMPLEMENTATION_PLAN.md)
- [API 规范](docs/zh-CN/API_CONVENTIONS.md)
- [业务规则](docs/zh-CN/BUSINESS_RULES.md)
- [数据库基线](docs/zh-CN/DATABASE_BASELINE.md)
- [技术决策](docs/zh-CN/DECISIONS.md)
- [后端启动说明](backend/README.zh-CN.md)
- [M2 安全审查](docs/zh-CN/M2_SECURITY_REVIEW.md)

英文文档供 AI 开发时作为稳定上下文，中文文档供项目负责人阅读和验收。核心内容发生变化时，两种语言必须同步更新。
