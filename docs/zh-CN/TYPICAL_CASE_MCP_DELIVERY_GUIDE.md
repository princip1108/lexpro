# 典型案例推荐 MCP 完整调用说明

本文只说明 `lexpro_push_typical_cases` 的生产调用链路。前三个 MCP 工具不需要合作方典型案例服务和内网隧道。

## 1. 运行拓扑

```text
内网机器
  Python 典型案例服务 127.0.0.1:8000
          │
          │ SSH 反向隧道（内网机器发起 -R）
          ▼
公网 Ubuntu 服务器
  127.0.0.1:8000  -> 合作方 Python 服务
  Java LexPro     127.0.0.1:8080/mcp
  Nginx           https://mcp.graphwisdom.cn/mcp
          ▲
          │ HTTPS + Bearer Token
          │
调用方电脑
```

端口含义：

| 位置 | 地址 | 用途 |
|---|---|---|
| 内网机器 | `127.0.0.1:8000` | Python 分析、检索和排序服务 |
| 公网服务器 | `127.0.0.1:8000` | SSH 反向隧道入口，Java 只访问这个地址 |
| 公网服务器 | `127.0.0.1:8080` | Spring Boot MCP 原始端点 |
| 公网域名 | `https://mcp.graphwisdom.cn/mcp` | 调用方最终使用的 HTTPS 端点 |

调用方不需要知道内网 IP、SSH 账号或 Python 服务路径。

## 2. 内网 Python 服务

在运行典型案例 Python 服务的内网机器上操作。使用项目原有的虚拟环境和启动方式，不要同时启动第二个 Uvicorn 进程。

确认服务监听端口：

```bash
ss -ltnp | grep ':8000'
```

本机健康检查：

```bash
curl -sS --max-time 15 http://127.0.0.1:8000/health
```

必须看到类似结果：

```json
{
  "code": 0,
  "message": "service is ready",
  "data": {
    "service_ready": true,
    "database_ready": true,
    "delta_loaded": true,
    "qwen_loaded": true,
    "mlp_loaded": true
  }
}
```

如果服务尚未启动，在 Python 项目目录和原有环境中启动，例如：

```bash
cd /path/to/typical_case_service
source .venv/bin/activate
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

实际项目如果使用 Conda、systemd 或其他启动脚本，应继续使用原有启动脚本；不要为了测试重复占用 `8000`。

## 3. 建立 SSH 反向隧道

仍在内网机器上执行。下面的 `ubuntu@175.27.133.89` 是公网 LexPro 服务器登录目标；不要把密码写进脚本或文档。

```bash
ssh -N -T \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -R 127.0.0.1:8000:127.0.0.1:8000 \
  ubuntu@175.27.133.89
```

说明：

- `-R` 表示由内网机器发起连接，并在公网服务器建立回环端口转发。
- 公网服务器的 `127.0.0.1:8000` 会转到内网机器的 `127.0.0.1:8000`。
- 命令保持运行时隧道才存在；关闭终端、断开 SSH 或网络中断都会使推荐不可用。
- 生产环境建议改用 SSH 私钥，并保留 `ServerAlive*` 参数。
- 如果 SSH 使用非 22 端口，在命令中增加 `-p <ssh-port>`。

## 4. 公网服务器检查

登录公网服务器：

```bash
ssh ubuntu@175.27.133.89
```

确认反向隧道已经监听：

```bash
sudo ss -ltnp | grep ':8000'
```

确认公网服务器可以通过隧道访问内网服务：

```bash
curl -sS --max-time 15 http://127.0.0.1:8000/health
```

然后确认 Java 后端：

```bash
sudo systemctl status lexpro-backend --no-pager -l
curl -i --max-time 10 http://127.0.0.1:8080/mcp
```

最后一条在不带 Token 时应返回 `HTTP/1.1 401` 和 `MCP_AUTHENTICATION_REQUIRED`。这表示 MCP 认证层正常，不是故障。

确认 HTTPS 反向代理：

```bash
curl -i --max-time 15 \
  --resolve mcp.graphwisdom.cn:443:127.0.0.1 \
  https://mcp.graphwisdom.cn/mcp
```

同样预期返回 `401`。出现 `502` 时优先检查公网服务器的 `127.0.0.1:8000/health` 和 Java 日志；出现 `525` 时检查域名证书、Nginx 和 DNS/CDN 的 TLS 模式。

## 5. 服务端配置前提

公网服务器上的 `/etc/lexpro/lexpro-backend.env` 必须已配置并重启 Java：

```text
LEXPRO_MCP_ENABLED=true
LEXPRO_RETRIEVAL_ENABLED=true
LEXPRO_TYPICAL_CASE_PROVIDER=PARTNER
LEXPRO_PARTNER_TYPICAL_CASE_BASE_URL=http://127.0.0.1:8000
LEXPRO_PARTNER_TYPICAL_CASE_ALLOW_CASE_DATA=true
```

`LEXPRO_PARTNER_TYPICAL_CASE_TOKEN_SECRET` 必须是至少 32 字节的服务端密钥。不要把它、数据库密码或 Java 环境文件交给调用方。

## 6. 调用方连接

当前 HTTPS 交付方式不需要调用方运行 SSH 隧道，也不需要保持本地程序窗口打开。调用方只使用：

```text
https://mcp.graphwisdom.cn/mcp
```

每个请求都携带交付包中的 `mcp-token.txt`：

```http
Authorization: Bearer <token>
Accept: application/json, text/event-stream
Content-Type: application/json; charset=utf-8
```

旧版本地 SSH 客户端仍可使用，但它连接的是 `http://127.0.0.1:18080/mcp`，并且需要保持本地隧道窗口运行；新交付优先使用 HTTPS 地址。

## 7. 推荐调用流程

推荐分为两次 MCP 调用：

1. `lexpro_summarize_case` 生成 `FACT` 摘要。
2. 读取 `data.summaryText`，作为 `lexpro_push_typical_cases.factText`。
3. 读取 `data.search.candidates`。

不要直接把完整卷宗反复提交给推荐工具。长文本会增加 Qwen 分析耗时，也更容易触发 MCP、Nginx 或上游超时。

### 7.1 PowerShell 5.1 完整示例

下面的 `curl.exe --max-time` 会保证请求不会无限等待。示例使用 UTF-8 文件传输，避免 PowerShell 5.1 的中文响应解码问题。

```powershell
$token = (Get-Content '.\mcp-token.txt' -Raw -Encoding UTF8).Trim()
$documentText = Get-Content '.\case.txt' -Raw -Encoding UTF8

function Invoke-McpJson {
  param(
    [Parameter(Mandatory=$true)][object]$Payload,
    [int]$TimeoutSec = 90
  )

  $requestFile = Join-Path $env:TEMP 'lexpro-mcp-request.json'
  $responseFile = Join-Path $env:TEMP 'lexpro-mcp-response.json'
  $json = $Payload | ConvertTo-Json -Depth 20
  [IO.File]::WriteAllText($requestFile, $json, (New-Object Text.UTF8Encoding($false)))

  curl.exe --silent --show-error --max-time $TimeoutSec `
    --request POST 'https://mcp.graphwisdom.cn/mcp' `
    --header "Authorization: Bearer $token" `
    --header 'Accept: application/json, text/event-stream' `
    --header 'Content-Type: application/json; charset=utf-8' `
    --data-binary "@$requestFile" `
    --output $responseFile

  if ($LASTEXITCODE -ne 0) {
    throw "MCP HTTP request failed with curl exit code $LASTEXITCODE"
  }

  $responseText = [IO.File]::ReadAllText($responseFile, [Text.Encoding]::UTF8)
  return ($responseText | ConvertFrom-Json)
}

$summaryResponse = Invoke-McpJson @{ 
  jsonrpc = '2.0'
  id = 1001
  method = 'tools/call'
  params = @{
    name = 'lexpro_summarize_case'
    arguments = @{
      summaryType = 'FACT'
      documents = @(@{
        documentId = 'CASE-DEMO-001'
        text = $documentText
      })
    }
  }
} -TimeoutSec 180

$factSummary = $summaryResponse.result.structuredContent.data.summaryText
if ([string]::IsNullOrWhiteSpace($factSummary)) {
  $summaryResponse.result.structuredContent | ConvertTo-Json -Depth 30
  throw 'FACT summary was empty'
}

$recommendationResponse = Invoke-McpJson @{
  jsonrpc = '2.0'
  id = 1002
  method = 'tools/call'
  params = @{
    name = 'lexpro_push_typical_cases'
    arguments = @{
      factText = $factSummary
      limit = 5
    }
  }
} -TimeoutSec 90

$recommendationResponse.result.structuredContent.data.search.candidates |
  Select-Object rank, title, caseNumber, factSimilarity, finalScore
```

### 7.2 请求和返回路径

推荐请求的核心参数：

```json
{
  "factText": "已授权的案件事实摘要",
  "limit": 5,
  "filters": {
    "caseType": "刑事",
    "region": "北京市"
  }
}
```

主要返回路径：

```text
result.structuredContent.data.analysis.issues
result.structuredContent.data.search.candidateCount
result.structuredContent.data.search.candidates
```

候选项是只读展示数据，可能包含：`rank`、`caseNumber`、`title`、`caseCause`、`caseCauses`、`applicableLaws`、`caseLevel`、`caseType`、`region`、`court`、`courtLevel`、`judgmentDate`、`procedure`、`docType`、`factSimilarity`、`issueScore`、`finalScore`、`issueDetails`、`contentExcerpt` 和 `contentTruncated`。可选字段可能为 `null`。

该工具不写入 LexPro 的 `case_recommendation` 历史，不返回 provider 分析 ID、检索 ID、内部表名、向量或完整案例正文。

## 8. 错误处理

| 错误 | 含义和处理 |
|---|---|
| HTTP `401` | Token 缺失、错误、过期或已撤销；不要修改 JSON，先核对 Token |
| HTTP `429` | 客户端并发或速率过高；降低并发后再试 |
| HTTP `502` | Java 收到合作方无效响应或 Nginx 无法连接 Java；检查 Java 日志和隧道健康 |
| HTTP `504` | 请求超过网关等待时间；缩短事实摘要，最多退避重试一次 |
| `TYPICAL_CASE_PARTNER_PROVIDER_INACTIVE` | Java 未选择 `PARTNER` |
| `TYPICAL_CASE_EXTERNAL_DATA_DISABLED` | 服务端未允许向合作方发送案件事实 |
| `TYPICAL_CASE_PROVIDER_UNAVAILABLE` | 反向隧道、Python 服务或合作方数据库不可用 |
| `TYPICAL_CASE_PROVIDER_RESPONSE_INVALID` | 合作方返回字段不符合契约，联系服务管理员 |
| `MCP_CAPACITY_EXCEEDED` | MCP 正在处理过多 AI 请求，降低并发并稍后重试 |

排查顺序固定为：

```text
内网 Python /health
 -> 公网服务器 127.0.0.1:8000/health
 -> systemctl status lexpro-backend
 -> 公网服务器 127.0.0.1:8080/mcp
 -> HTTPS 域名和 Token
```

## 9. 停止和安全

- 调用完成后，内网 Python 服务可以继续运行，也可以由运维按既有方式停止。
- 反向隧道终端关闭后，典型案例推荐会不可用；前三个 MCP 工具不受此隧道影响。
- 不要把 Token、SSH 私钥、数据库密码、案件原文或模型原始响应写入普通日志。
- Token 泄露时立即停用对应 `clientId` 并重新发放交付包。
- 推荐结果是检索辅助信息，不替代承办人员的证据审查和法律判断。
