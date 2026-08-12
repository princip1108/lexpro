# M4 卷宗管理

[English](../M4_DOSSIER_MANAGEMENT.md)

## 已实现范围

- 建立 `DossierStorage` 存储边界，并提供开发环境本地文件系统适配器。
- 使用现有 V2 表实现案件内的目录树和标签。
- 流式上传，包含可配置大小限制、扩展名/MIME 白名单、常见文件签名校验和 SHA-256 哈希。
- 文件元数据列表、鉴权下载、重命名/移动/标签替换、软删除和恢复。
- 服务层案件权限检查，所有修改和下载均写入审计日志。
- 不新增数据库迁移，正常卷宗生命周期操作不物理删除文件。

## 权限规则

- 查询目录、标签和文件要求 `CASE_READ`，同时案件访问级别至少为 `VIEW`。
- 下载执行相同的读取权限检查，并记录审计。
- 创建目录/标签以及上传、整理、删除、恢复文件要求 `DOSSIER_MANAGE`，同时案件访问级别至少为 `EDIT`。
- 无权访问的案件统一返回 `404`，与 M3 一致，避免泄露案件是否存在。

## 存储与安全

本地适配器在配置的根目录下使用随机内部键保存内容，例如 `cases/9/<uuid>.pdf`。原始文件名只作为数据库元数据。API 响应绝不返回对象键或文件系统路径。

开发环境默认配置：

```text
LEXPRO_DOSSIER_LOCAL_ROOT=./storage
LEXPRO_DOSSIER_MAX_FILE_SIZE=25MB
LEXPRO_DOSSIER_MAX_REQUEST_SIZE=26MB
LEXPRO_DOSSIER_ALLOWED_EXTENSIONS=pdf,doc,docx,xls,xlsx,ppt,pptx,txt,jpg,jpeg,png
```

可通过 `LEXPRO_DOSSIER_ALLOWED_CONTENT_TYPES` 覆盖 MIME 白名单。程序同时校验声明大小、流式实际大小、扩展名、MIME 和常见文件签名。这是上传边界校验，不等同于病毒扫描；生产环境是否需要杀毒或内容净化仍需确认。

如果文件已写入存储，但同一次服务调用内的数据库操作失败，程序会补偿删除新对象。软删除只修改 `evidence_file.file_status/deleted_at/deleted_by`，故意保留文件内容，以支持恢复。

## 暂缓范围

- 生产存储布局、MinIO 适配器和凭据。
- 保留期限、永久清理和备份恢复策略。
- 病毒扫描或内容净化要求。
- 异步解析和 AI 处理，这些从 M5 开始。

## 人工验收

1. 配置数据库/JWT 环境变量；可选把 `LEXPRO_DOSSIER_LOCAL_ROOT` 设为开发机绝对目录。
2. 启动后端，使用拥有 `CASE_READ`、`DOSSIER_MANAGE` 且对某案件具有 `EDIT` 分配的用户登录。
3. 在 Swagger UI 中创建目录和标签，上传一个较小的允许类型文件，查询元数据并下载。
4. 确认元数据 JSON 不包含 `fileUrl`、对象键或存储路径，SHA-256 为 64 位十六进制字符串。
5. 软删除文件，确认下载返回 `410`；恢复后再次下载。
6. 确认 `operation_log` 中存在目录/标签/文件修改事件以及下载事件。
