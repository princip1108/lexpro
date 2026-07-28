# LexPro API 规范

[English](../API_CONVENTIONS.md)

## 通用约定

- API 基础路径：`/api/v1`。
- JSON 字段使用 `camelCase`。
- 数据库继续使用 `snake_case`，但数据库命名不作为 API 命名约定暴露给前端。
- 除文件上传下载外，内容类型使用 `application/json; charset=UTF-8`。
- 时间使用带偏移量的 ISO-8601，例如 `2026-07-28T10:30:00+08:00`。
- 日期使用 ISO `yyyy-MM-dd`。

## HTTP 语义

| 操作 | 状态码 |
|---|---:|
| 查询或修改成功 | `200 OK` |
| 创建资源成功 | `201 Created` |
| 已接受异步任务 | `202 Accepted` |
| 成功且无需响应体 | `204 No Content` |
| 请求参数错误 | `400 Bad Request` |
| 未登录或认证信息无效 | `401 Unauthorized` |
| 已登录但无权访问 | `403 Forbidden` |
| 资源不存在 | `404 Not Found` |
| 状态或版本冲突 | `409 Conflict` |
| 不支持的文件/媒体类型 | `415 Unsupported Media Type` |
| 未预期的服务器错误 | `500 Internal Server Error` |

成功响应直接返回资源或操作结果，不为每个响应重复包装 `{code, message, data}`。

## 请求关联

- 每个 HTTP 响应都包含 `X-Request-Id`。
- 客户端可以传入由 8 到 64 个 ASCII 字母、数字、点、下划线或连字符组成的 `X-Request-Id`。
- 缺失或格式不安全的值会替换为服务端生成的 UUID。
- 同一个值会出现在请求完成日志、错误响应以及本次请求产生的审计记录中。

## 错误响应

错误使用 Spring `ProblemDetail`（`application/problem+json`），并增加稳定扩展字段：

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid",
  "instance": "/api/v1/cases",
  "errorCode": "VALIDATION_FAILED",
  "requestId": "01J...",
  "fieldErrors": {
    "caseName": "must not be blank"
  }
}
```

前端使用稳定的 `errorCode` 进行逻辑判断，`detail` 用于展示或诊断。响应中不得出现堆栈、SQL、密码、Token 或内部存储路径。

## 分页

- 请求参数 `page` 从 1 开始。
- `size` 默认 20，最大 100。
- 排序字段必须使用每个接口自己的允许列表，不能把前端传入的原始字段名直接拼入 SQL。
- Java 公共分页契约为 `PageRequest` 和 `PageResponse<T>`。

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## 认证

- 受保护接口使用 `Authorization: Bearer <access-token>`。
- 公开接口仅限健康检查、本地接口文档和登录。
- 权限和案件可见范围以后端判断为准，前端隐藏按钮不能代替权限控制。
- Access Token 使用 HS256，默认 30 分钟过期，不提供 Refresh Token。
- `POST /api/v1/auth/logout` 记录退出操作，客户端必须丢弃 Token；服务端没有 Token 黑名单。
- 受保护请求会重新读取账号状态和角色权限。账号被禁用、删除或修改后，旧 Token 不能继续使用。
- 登录、当前用户和 Token 响应使用 `Cache-Control: no-store`。

### M2 接口

| 方法 | 路径 | 权限 | 结果 |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | 公开 | Access Token、过期时间和当前用户 |
| `GET` | `/api/v1/auth/me` | 已登录 | 当前用户、组织、角色和权限 |
| `POST` | `/api/v1/auth/logout` | 已登录 | `204 No Content` |
| `GET` | `/api/v1/users` | `USER_MANAGE` | 分页用户列表 |
| `GET` | `/api/v1/users/{userId}` | `USER_MANAGE` | 用户详情 |
| `POST` | `/api/v1/users` | `USER_MANAGE` | `201 Created` 用户 |
| `PATCH` | `/api/v1/users/{userId}/status` | `USER_MANAGE` | 启用或禁用后的用户 |
| `PUT` | `/api/v1/users/{userId}/password` | `USER_MANAGE` | `204 No Content` |
| `GET` | `/api/v1/organizations/tree` | `USER_MANAGE` | 组织树 |
| `GET` | `/api/v1/roles` | `USER_MANAGE` | 角色及权限码 |
| `GET` | `/api/v1/permissions` | `USER_MANAGE` | 权限目录 |

## 接口路径命名

- 使用复数名词：`/cases`、`/users`、`/files`。
- 父资源决定权限边界时使用嵌套路径：`/cases/{caseId}/parties`。
- 业务状态变化使用明确的命令资源：`/cases/{caseId}/status-transitions`。
- 路径中不使用 `getUserList` 之类的方法名。
