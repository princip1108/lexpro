# LexPro MCP 调用交付手册

版本：2026-08-13  
适用服务：LexPro 法律智能 MCP Server  
当前能力：法律要素识别、文书实体识别、案件摘要生成  
典型案例推送：已注册占位接口，当前不执行实际推送

## 1. 交付内容

每个调用方只接收与其平台对应的客户端压缩包：

- Windows：`lexpro-mcp-windows-client-a.zip`
- Linux：`lexpro-mcp-linux-client-a.zip`

压缩包包含：

- 自动建立 SSH 隧道的脚本；
- 该调用方专用 SSH 私钥；
- 该调用方专用 MCP Token 文件 `mcp-token.txt`；
- 本手册。

不要把服务器安装包、服务器环境文件、数据库备份、数据库密码、JWT 密钥、DeepSeek API Key 或 `ubuntu` 登录凭据交给调用方。

## 2. 连接方式

调用方先启动压缩包中的隧道脚本。脚本把调用方本机的 `18080` 端口转发到服务器上的 MCP 服务：

```text
调用方 127.0.0.1:18080
    -> SSH 隧道
服务器 127.0.0.1:8080
    -> LexPro MCP /mcp
```

因此，MCP 客户端配置的地址始终是：

```text
http://127.0.0.1:18080/mcp
```

这个地址是调用方自己电脑上的地址，不是服务器公网地址。隧道窗口必须保持运行。

### Windows

解压 ZIP 后，在 PowerShell 执行：

```powershell
Set-Location '解压后的客户端目录'
Set-ExecutionPolicy -Scope Process Bypass
.\start-lexpro-mcp-tunnel.ps1
```

如果出现私钥权限错误，在该目录执行：

```powershell
$key = Join-Path (Get-Location) 'lexpro_windows_client'
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls $key /inheritance:r
icacls $key /grant:r "${currentUser}:(R)"
icacls $key /grant:r 'SYSTEM:(F)'
```

### Linux

解压 ZIP 后执行：

```bash
cd /path/to/解压后的客户端目录
chmod 700 start-lexpro-mcp-tunnel.sh
./start-lexpro-mcp-tunnel.sh
```

出现 `Starting LexPro MCP tunnel...` 后不要关闭该终端。

## 3. 认证配置

每个 HTTP 请求都必须携带该调用方自己的 Token：

```http
Authorization: Bearer <mcp-token.txt中的完整内容>
Content-Type: application/json; charset=utf-8
Accept: application/json, text/event-stream
```

不要把 Token 放入 URL、Cookie、工具参数或日志。Token 文件只读，不要修改其中的换行或字符。

## 4. MCP 标准流程

支持 MCP Streamable HTTP 的 SDK 通常会自动完成以下流程：

1. `initialize`
2. `notifications/initialized`
3. `tools/list`
4. `tools/call`

手工测试时，先调用 `tools/list`：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

应看到四个工具：

```text
lexpro_recognize_legal_elements
lexpro_recognize_entities
lexpro_summarize_case
lexpro_push_typical_cases
```

## 5. 三个可用工具

### 5.1 法律要素识别

工具名：`lexpro_recognize_legal_elements`

用途：从一段法律文本中识别法律要素，并返回原文证据、偏移量、置信度和校验信息。

输入：

```json
{
  "text": "必填，5 至 60000 个字符",
  "caseCause": "可选，民间借贷纠纷等案由，最长255个字符"
}
```

请求示例：

```json
{
  "jsonrpc": "2.0",
  "id": 101,
  "method": "tools/call",
  "params": {
    "name": "lexpro_recognize_legal_elements",
    "arguments": {
      "text": "2025年3月15日，赵明通过银行转账向陈伟出借人民币120000元，双方签订借款合同，约定借款期限一年、年利率4%。借款到期后陈伟未还款。",
      "caseCause": "民间借贷纠纷"
    }
  }
}
```

成功时读取 `result.structuredContent`：

```json
{
  "tool": "lexpro_recognize_legal_elements",
  "schemaVersion": "...",
  "model": "...",
  "data": {
    "caseCause": "民间借贷纠纷",
    "elements": [],
    "validation": {}
  }
}
```

`elements` 中的证据引用应来自输入原文。调用方不得把识别结果直接当作未经复核的法律结论。

### 5.2 文书实体识别

工具名：`lexpro_recognize_entities`

用途：识别人名、组织、地点、日期、金额、案号、法条等文书实体，并返回实体原文和可用的偏移信息。

输入：

```json
{
  "text": "必填，1 至 60000 个字符"
}
```

请求示例：

```json
{
  "jsonrpc": "2.0",
  "id": 102,
  "method": "tools/call",
  "params": {
    "name": "lexpro_recognize_entities",
    "arguments": {
      "text": "原告赵明向上海市浦东新区人民法院提交借款合同和银行转账凭证，主张陈伟偿还120000元。"
    }
  }
}
```

成功时读取：

```json
{
  "tool": "lexpro_recognize_entities",
  "schemaVersion": "...",
  "model": "...",
  "data": {
    "entities": []
  }
}
```

### 5.3 案件摘要生成

工具名：`lexpro_summarize_case`

用途：根据一个或多个文档生成案件摘要。

`summaryType` 只能是：

- `FACT`：事实摘要；
- `PROCESS`：诉讼或办理过程摘要；
- `CONCLUSION`：结论摘要；
- `FULL`：完整摘要。

输入：

```json
{
  "summaryType": "FACT | PROCESS | CONCLUSION | FULL",
  "documents": [
    {
      "documentId": "可选，最长100个字符",
      "text": "必填，1 至 60000 个字符"
    }
  ]
}
```

文档数组至少 1 项，最多 20 项；所有文档拼接后的总输入也不能超过服务限制。文档对象不能增加未定义字段。

请求示例：

```json
{
  "jsonrpc": "2.0",
  "id": 103,
  "method": "tools/call",
  "params": {
    "name": "lexpro_summarize_case",
    "arguments": {
      "summaryType": "FULL",
      "documents": [
        {
          "documentId": "case-001",
          "text": "2025年3月15日，赵明通过银行转账向陈伟出借人民币120000元，双方签订借款合同，约定借款期限一年、年利率4%。借款到期后陈伟未还款。赵明向法院提起诉讼。"
        }
      ]
    }
  }
}
```

成功时读取：

```json
{
  "tool": "lexpro_summarize_case",
  "schemaVersion": "...",
  "model": "...",
  "data": {
    "summaryType": "FULL",
    "summaryText": "...",
    "documentIds": ["case-001"]
  }
}
```

## 6. 典型案例接口

工具名：`lexpro_push_typical_cases`

当前只保留接口占位，不接收业务参数、不访问下游服务、不写入推荐数据。调用后固定返回：

```json
{
  "isError": true,
  "structuredContent": {
    "errorCode": "TYPICAL_CASE_PUSH_NOT_IMPLEMENTED",
    "message": "Typical-case push is reserved and not implemented"
  }
}
```

## 7. PowerShell UTF-8 调用模板

Windows PowerShell 5.1 不建议直接使用 `Invoke-RestMethod` 解析中文响应。请使用 UTF-8 字节请求和 `StreamReader`：

```powershell
$token = (Get-Content '.\mcp-token.txt' -Raw).Trim()
$body = @{
  jsonrpc = '2.0'
  id = 101
  method = 'tools/call'
  params = @{
    name = 'lexpro_recognize_entities'
    arguments = @{ text = '原告赵明向上海市浦东新区人民法院提交借款合同。' }
  }
} | ConvertTo-Json -Depth 12

$web = Invoke-WebRequest 'http://127.0.0.1:18080/mcp' `
  -UseBasicParsing -Method Post `
  -Headers @{ Authorization = "Bearer $token"; Accept = 'application/json, text/event-stream' } `
  -ContentType 'application/json; charset=utf-8' `
  -Body ([Text.Encoding]::UTF8.GetBytes($body))

$web.RawContentStream.Position = 0
$reader = New-Object IO.StreamReader(
  $web.RawContentStream, (New-Object Text.UTF8Encoding($false)), $true)
$jsonText = $reader.ReadToEnd()
$reader.Dispose()
$response = $jsonText | ConvertFrom-Json
$response.result.structuredContent | ConvertTo-Json -Depth 30
```

## 8. 错误处理

HTTP 层错误：

| HTTP 状态 | 含义 |
|---:|---|
| `401` | Token 缺失、错误、过期或已撤销 |
| `403` | 客户端无权访问 MCP 端点 |
| `429` | 超过客户端速率限制 |
| `5xx` | 服务或上游 AI 临时故障 |

工具调用通常返回 HTTP `200`，但通过 `result.isError=true` 表示工具级错误。调用方应读取 `result.structuredContent.errorCode`，常见错误包括：

| 错误码 | 处理建议 |
|---|---|
| `MCP_INPUT_INVALID` | 修正字段、类型、长度或未知参数后重试 |
| `MCP_ACCESS_DENIED` | 联系服务管理员检查客户端权限 |
| `AI_PROVIDER_DISABLED` | 服务端未启用 AI，不能由调用方自行修复 |
| `AI_DATA_EXPORT_DISABLED` | 服务端禁止向外部模型发送文本 |
| `AI_INPUT_TOO_LARGE` | 缩短文本或拆分文档后重试 |
| `AI_RESPONSE_INVALID` | 稍后重试；连续发生时联系管理员 |
| `AI_PROVIDER_UNAVAILABLE` | 稍后重试一次 |
| `MCP_CAPACITY_EXCEEDED` | 降低并发，稍后重试 |
| `MCP_OUTPUT_LIMIT_EXCEEDED` | 缩短输入或减少文档后重试 |
| `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED` | 当前接口尚未实现，不要自动重试 |

不要自动重试 `MCP_INPUT_INVALID`、`MCP_ACCESS_DENIED`、`AI_DATA_EXPORT_DISABLED` 和典型案例占位错误。上游超时或 `503/504` 最多退避重试一次，避免重复产生模型费用。

## 9. 数据和安全要求

- 只提交完成授权的数据；服务会将前三个工具的输入发送给配置的 DeepSeek 服务。
- 调用方负责在自身系统中进行脱敏、访问控制和人工复核。
- 不要把 Token、SSH 私钥、案件原文或模型返回写入普通日志。
- MCP 返回的是智能处理结果，不等同于最终法律意见或自动生效的诉讼结论。
- 当客户端包泄露时，立即联系管理员停用对应 `clientId` 并重新发放新包。

## 10. 最小验收清单

1. 隧道脚本启动且保持运行。
2. `tools/list` 返回四个工具。
3. 实体识别返回 `data.entities`。
4. 法律要素识别返回 `data.elements` 和 `data.validation`。
5. 案件摘要返回 `data.summaryText`。
6. 典型案例返回 `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`。
7. 关闭隧道后请求失败，重新启动隧道后恢复。
