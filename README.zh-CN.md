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

- 前端：登录、路由保护、工作台、案件流程、知识与任务、组织与用户、卷宗解析、智能结果历史、摘要、报告和类案推荐均已接入真实后端接口。AI 生成、报告生成和新建类案检索仍需满足文档所述的运行开关与服务条件。
- 数据库：V1 + V2 + V3 已完成，`lexpro` Schema 最终为 32 张业务表。
- 后端：M0-M7 和 M8 首版只读 MCP Server 已实现，包括类案混合检索和受 JWT 保护的 `/mcp` 端点。
- 仍暂缓：案件状态流转、身份证原值处理、生产 MinIO、PDF/Office 解析、指定 MCP 客户端验收以及任何 MCP 写操作。真实案件数据外发仍等待明确批准。

## 中文文档

- [中文文档索引](docs/zh-CN/README.md)
- [实施计划](docs/zh-CN/IMPLEMENTATION_PLAN.md)
- [API 规范](docs/zh-CN/API_CONVENTIONS.md)
- [业务规则](docs/zh-CN/BUSINESS_RULES.md)
- [数据库基线](docs/zh-CN/DATABASE_BASELINE.md)
- [技术决策](docs/zh-CN/DECISIONS.md)
- [类案检索与 MCP 架构](docs/zh-CN/AI_RETRIEVAL_AND_MCP_ARCHITECTURE.md)
- [后端启动说明](backend/README.zh-CN.md)
- [本机路径与配置索引](docs/LOCAL_PATHS_AND_CONFIGURATION.md)
- [M2 安全审查](docs/zh-CN/M2_SECURITY_REVIEW.md)
- [M3 案件流程](docs/zh-CN/M3_CASE_WORKFLOW.md)
- [M4 卷宗管理](docs/zh-CN/M4_DOSSIER_MANAGEMENT.md)
- [M5 文档解析](docs/zh-CN/M5_DOCUMENT_PROCESSING.md)
- [M6 案卡和报告](docs/zh-CN/M6_CASE_CARDS_AND_REPORTS.md)
- [人工待办和验收](docs/zh-CN/MANUAL_ACTIONS.md)

英文文档供 AI 开发时作为稳定上下文，中文文档供项目负责人阅读和验收。核心内容发生变化时，两种语言必须同步更新。
