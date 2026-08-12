# LexPro 本机路径与配置索引

> 最后核实：2026-07-30  
> 适用机器：当前 Windows 开发机。安装位置变化后必须重新核实，不能凭常见默认路径猜测。

本文只记录开发路径、配置入口和环境变量名，不记录任何真实密码、JWT 密钥或 API Key。

## 1. 已核实的工具路径

| 用途 | 当前路径 | 已核实版本/说明 |
|---|---|---|
| 项目根目录 | `D:\desktop\soph\lexpro` | Git 仓库根目录 |
| Git | `D:\Git\cmd\git.exe` | Git 2.49.0 |
| JDK | `D:\jdk-21.0.5` | Java 21.0.5 LTS |
| Java | `D:\jdk-21.0.5\bin\java.exe` | 后端要求 Java 21 |
| IntelliJ IDEA | `D:\idea\IntelliJ IDEA 2025.1` | 中文版可在运行配置中设置环境变量 |
| IDEA 内置 Maven | `D:\idea\IntelliJ IDEA 2025.1\plugins\maven\lib\maven3` | Maven 3.9.9 |
| Maven 命令 | `D:\idea\IntelliJ IDEA 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd` | 当前终端不能直接使用 `mvn` 时使用绝对路径 |
| Maven 本地仓库 | `C:\Users\princip\.m2\repository` | Maven 依赖缓存 |
| Node.js | `D:\npm-global\node\node.exe` | Node.js 24.15.0 |
| npm | `D:\npm-global\node\npm.cmd` | npm 11.12.1 |
| Python | `D:\python\python.exe` | Python 3.11.9 |
| 检索服务 Python | `D:\desktop\soph\lexpro\backend\retrieval-service\.venv\Scripts\python.exe` | 项目虚拟环境，Python 3.11.9 |
| 检索服务 Uvicorn | `D:\desktop\soph\lexpro\backend\retrieval-service\.venv\Scripts\uvicorn.exe` | 启动 M7 Python 服务 |
| DBeaver | `D:\DBeaver\dbeaver.exe` | DBeaver 25.3.4 |
| DBeaver 工作区 | `C:\Users\princip\AppData\Roaming\DBeaverData` | 连接配置和工作区数据，不纳入项目 Git |
| Hugging Face 缓存 | `C:\Users\princip\.cache\huggingface` | BGE-M3 首次下载后的默认缓存位置 |

## 2. PostgreSQL 特殊路径

本机 PostgreSQL 路径包含非标准拼写，必须原样使用：`PosrgreSQL` 和 `date` 不是本文笔误。

| 用途 | 当前值 |
|---|---|
| Windows 服务名 | `postgresql-x64-18` |
| PostgreSQL 安装目录 | `D:\SQL\PosrgreSQL\18` |
| 命令目录 | `D:\SQL\PosrgreSQL\18\bin` |
| `psql.exe` | `D:\SQL\PosrgreSQL\18\bin\psql.exe` |
| `pg_ctl.exe` | `D:\SQL\PosrgreSQL\18\bin\pg_ctl.exe` |
| 数据目录 | `D:\SQL\PosrgreSQL\18\date` |
| 主配置 | `D:\SQL\PosrgreSQL\18\date\postgresql.conf` |
| 客户端认证配置 | `D:\SQL\PosrgreSQL\18\date\pg_hba.conf` |
| 已核实版本 | PostgreSQL 18.4 |
| 已核实端口 | `5432` |
| 开发数据库 | `lexpro` |
| 业务 Schema | `lexpro` |
| JDBC URL | `jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro` |

只读状态检查：

```powershell
Get-Service -Name 'postgresql-x64-18'
& 'D:\SQL\PosrgreSQL\18\bin\psql.exe' --version
& 'D:\SQL\PosrgreSQL\18\bin\pg_ctl.exe' status -D 'D:\SQL\PosrgreSQL\18\date'
```

数据库脚本位置：

- 已评审源脚本：`D:\desktop\soph\lexpro\DBM`
- Flyway 迁移：`D:\desktop\soph\lexpro\backend\lexpro-backend\src\main\resources\db\migration`
- 本地备份目录：`D:\desktop\soph\lexpro\backups`（被 Git 忽略）

现有开发库已经处于 V3 基线。V1/V2/V3 不得重跑；V4 或以后迁移仍须先备份并取得明确批准。

## 3. 项目目录

| 模块 | 路径 |
|---|---|
| Vue 前端 | `D:\desktop\soph\lexpro` |
| 前端源码 | `D:\desktop\soph\lexpro\src` |
| 前端依赖 | `D:\desktop\soph\lexpro\node_modules` |
| 前端构建输出 | `D:\desktop\soph\lexpro\dist` |
| Spring Boot 后端 | `D:\desktop\soph\lexpro\backend\lexpro-backend` |
| 后端源码 | `D:\desktop\soph\lexpro\backend\lexpro-backend\src` |
| 后端构建输出 | `D:\desktop\soph\lexpro\backend\lexpro-backend\target` |
| 本地卷宗默认目录 | `D:\desktop\soph\lexpro\backend\lexpro-backend\storage` |
| Python 检索服务 | `D:\desktop\soph\lexpro\backend\retrieval-service` |
| Python 虚拟环境 | `D:\desktop\soph\lexpro\backend\retrieval-service\.venv` |
| 数据库设计与升级 SQL | `D:\desktop\soph\lexpro\DBM` |
| 工程文档 | `D:\desktop\soph\lexpro\docs` |
| 中文工程文档 | `D:\desktop\soph\lexpro\docs\zh-CN` |

`storage`、`target`、`dist`、`.venv`、`backups` 和真实 `.env` 文件均不应提交 Git。

## 4. 配置文件入口

| 配置范围 | 权威位置 | 说明 |
|---|---|---|
| 后端运行配置 | `backend\lexpro-backend\src\main\resources\application.properties` | Spring 配置与环境变量默认值 |
| 后端环境变量示例 | `backend\lexpro-backend\.env.example` | 仅为模板，Spring Boot 不会自动加载该文件 |
| 前端环境变量示例 | `.env.example` | 定义 `VITE_API_BASE_URL` |
| Vite 配置 | `vite.config.js` | 当前开发端口 `5173` |
| 前端命令和依赖 | `package.json` | `dev`、`build`、`preview` |
| Python 检索配置 | `backend\retrieval-service\app\config.py` | 从进程环境读取 `LEXPRO_RETRIEVAL_*` |
| Python 依赖 | `backend\retrieval-service\requirements.txt` | 检索服务固定依赖版本 |
| AI 开发规则 | `AGENTS.md` | AI 修改项目前必须遵守 |
| 人工操作清单 | `docs\zh-CN\MANUAL_ACTIONS.md` | 迁移、真实环境和待确认事项 |

重要区别：

- IntelliJ 后端环境变量应配置在 **运行 -> 编辑配置 -> LexproBackendApplication -> 环境变量**。
- `.env.example` 只说明变量名称，不能改成真实凭据并提交。
- Vite 可在项目根目录使用被 Git 忽略的 `.env.local`。
- Python 检索服务当前直接读取启动进程的环境变量，没有配置自动加载 `.env`。

## 5. 环境变量索引

### 后端必需项

| 变量 | 用途 |
|---|---|
| `LEXPRO_DB_PASSWORD` | PostgreSQL 密码 |
| `LEXPRO_JWT_SECRET` | JWT HS256 密钥，至少 32 字节 |

### 后端常用连接与路径

| 变量 | 当前默认值/用途 |
|---|---|
| `LEXPRO_DB_URL` | 本机 `lexpro` JDBC URL |
| `LEXPRO_DB_USERNAME` | 默认 `postgres` |
| `LEXPRO_CORS_ALLOWED_ORIGINS` | 默认允许本机 `5173` 前端 |
| `LEXPRO_DOSSIER_LOCAL_ROOT` | 默认 `./storage`，相对于后端工作目录 |
| `LEXPRO_REPORT_PDF_FONT_PATH` | 本机可使用 `C:\Windows\Fonts\simhei.ttf` |
| `LEXPRO_RETRIEVAL_BASE_URL` | 默认 `http://127.0.0.1:8010` |

### 安全开关

| 变量 | 开发要求 |
|---|---|
| `LEXPRO_FLYWAY_ENABLED` | 默认 `false`，迁移须审批 |
| `LEXPRO_FLYWAY_BASELINE_ON_MIGRATE` | 保持 `false` |
| `LEXPRO_BOOTSTRAP_ADMIN_ENABLED` | 仅首次创建开发管理员时临时开启 |
| `LEXPRO_AI_ENABLED` | 是否启用 DeepSeek 适配器 |
| `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA` | 默认 `false`；已批准向 DeepSeek 外发的部署须显式设为 `true` |
| `LEXPRO_RETRIEVAL_ENABLED` | Python 检索服务准备完成后再开启 |
| `LEXPRO_MCP_ENABLED` | 默认 `false`，确定客户端并批准本地验收后再开启 |

### DeepSeek

`LEXPRO_AI_BASE_URL`、`LEXPRO_AI_API_KEY`、`LEXPRO_AI_MODEL`、`LEXPRO_AI_CONNECT_TIMEOUT`、
`LEXPRO_AI_READ_TIMEOUT`、`LEXPRO_AI_MAX_INPUT_CHARS` 和 `LEXPRO_AI_MAX_OUTPUT_TOKENS` 由
`application.properties` 统一定义。真实 `LEXPRO_AI_API_KEY` 只放本机环境变量。

### Python 检索服务

| 变量 | 当前约定 |
|---|---|
| `LEXPRO_RETRIEVAL_DATABASE_URL` | Python 只读 PostgreSQL 账号连接串，启动服务前必需 |
| `LEXPRO_RETRIEVAL_MODEL_NAME` | `BAAI/bge-m3` |
| `LEXPRO_RETRIEVAL_MODEL_REVISION` | `main` |
| `LEXPRO_RETRIEVAL_DIMENSION` | `1024` |
| `LEXPRO_RETRIEVAL_DISTANCE` | `cosine` |
| `LEXPRO_RETRIEVAL_MAX_CANDIDATES` | 候选上限，默认 `1000` |
| `LEXPRO_RETRIEVAL_DEFAULT_LIMIT` | 默认结果数，默认 `10` |

### MCP Server

| 变量 | 当前约定 |
|---|---|
| `LEXPRO_MCP_ENABLED` | 默认 `false` |
| `LEXPRO_MCP_ENDPOINT` | `/mcp` |
| `LEXPRO_MCP_REQUEST_TIMEOUT` | `PT55S`，必须大于 AI 读取超时，最大允许 `PT60S` |
| `LEXPRO_MCP_MAX_ITEMS` | 摘要输入文档最多 `20` 条 |
| `LEXPRO_MCP_MAX_OUTPUT_CHARS` | 单次结构化结果最多 `50000` 字符 |
| `LEXPRO_MCP_CLIENT_REGISTRY_PATH` | 启用 MCP 时必填；指向只保存令牌 SHA-256 摘要的绝对路径 |
| `LEXPRO_MCP_TOKEN_RELOAD_INTERVAL` | 注册表重载检查周期，默认 `PT30S` |
| `LEXPRO_MCP_MAX_CONCURRENT_REQUESTS` | 全局在途 AI 调用上限，默认 `8` |
| `LEXPRO_MCP_MAX_CONCURRENT_PER_CLIENT` | 单客户端在途 AI 调用上限，默认 `2` |
| `LEXPRO_MCP_RATE_LIMIT_PER_MINUTE` | 单客户端 HTTP 请求上限，默认每分钟 `60` |

## 6. 当前终端的可靠启动方式

当前 Codex/PowerShell 终端不保证 `java`、`mvn`、`node` 已加入 `PATH`。先在当前终端设置：

```powershell
$env:JAVA_HOME = 'D:\jdk-21.0.5'
$env:Path = "$env:JAVA_HOME\bin;C:\Windows\System32\WindowsPowerShell\v1.0;D:\Git\cmd;D:\npm-global\node;D:\python;$env:Path"
```

验证 Java 和 Maven：

```powershell
& 'D:\jdk-21.0.5\bin\java.exe' -version
& 'D:\idea\IntelliJ IDEA 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd' -version
```

后端构建或启动：

```powershell
Set-Location 'D:\desktop\soph\lexpro\backend\lexpro-backend'
.\mvnw.cmd -q test
.\mvnw.cmd spring-boot:run
```

前端启动：

```powershell
Set-Location 'D:\desktop\soph\lexpro'
& 'D:\npm-global\node\npm.cmd' run dev
```

Python 检索服务启动：

```powershell
Set-Location 'D:\desktop\soph\lexpro\backend\retrieval-service'
$env:LEXPRO_RETRIEVAL_DATABASE_URL = 'postgresql://lexpro_retrieval:<本机密码>@127.0.0.1:5432/lexpro'
& '.\.venv\Scripts\python.exe' -m uvicorn app.main:app --host 127.0.0.1 --port 8010
```

`<本机密码>` 只是占位符，不能把真实值写入本文、Git 或聊天记录。

## 7. 本地服务地址

| 服务 | 地址 |
|---|---|
| Vue 开发服务 | `http://127.0.0.1:5173` |
| Spring Boot API | `http://127.0.0.1:8080` |
| Swagger UI | `http://127.0.0.1:8080/swagger-ui.html` |
| OpenAPI JSON | `http://127.0.0.1:8080/v3/api-docs` |
| 后端健康检查 | `http://127.0.0.1:8080/api/health` |
| 数据库健康检查 | `http://127.0.0.1:8080/api/health/database` |
| Python 检索健康检查 | `http://127.0.0.1:8010/health` |
| MCP Server（启用后） | `http://127.0.0.1:8080/mcp` |

## 8. 后续使用规则

1. 优先查本文，再查 `application.properties`、模块文档和本机注册表，不要反复扫描整个磁盘。
2. 不要把 `D:\SQL\PosrgreSQL\18\date` 自动改写为常见的 `PostgreSQL\data`。
3. 命令行找不到工具时先使用本文绝对路径，不要重复安装 Java、Maven、Git、Node 或 Python。
4. 路径存在不代表允许执行迁移、删除、外发数据或修改生产配置；这些操作仍需单独批准。
5. 新增或迁移关键工具后，只更新本文对应条目，不另建重复的环境说明文档。
