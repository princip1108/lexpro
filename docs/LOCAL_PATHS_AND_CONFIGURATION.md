# LexPro 本机路径与配置索引

> 最后核实：2026-09-03  
> 适用机器：当前 Windows 开发机。安装位置变化后必须重新核实，不能凭常见默认路径猜测。

本文只记录开发路径、配置入口和环境变量名，不记录任何真实密码、JWT 密钥或 API Key。

## 1. 已核实的工具路径

### 2026-09-05 本地验收补充

- `tools/prepare_local_acceptance.py` 默认只读预览；`--apply --backup <已验证dump>` 仅连接本机开发库，补充典型案例元数据和演示账号／案件授权，不覆盖已有记录或密码。依赖现有虚拟环境的 `psycopg` 和 `bcrypt==4.3.0`；演示密码由 `LEXPRO_DEMO_PASSWORD` 读取。
- 已导入 100 条 SQLite 典型案例样本（本地总数 115），不生成向量。5 个已有演示案件向 `admin` 和三类演示账号授权。独立账号为 `demo_admin`、`demo_prosecutor`、`demo_reviewer`，登录页仅开发模式显示公开演示密码，不能将这些账号用于生产。
- 模型配置 V7 已于 2026-09-05 经批准通过 Flyway 执行，业务表从 32 变为 33；V1—V6 未重跑。升级前备份为 `backups/before_v7_20260905_153726.dump`，已校验备份目录。
- 执行 V7 并验证后才设置 `LEXPRO_MODEL_CONFIG_ENABLED=true`。`LEXPRO_MODEL_CONFIG_MASTER_KEY` 必须为随机 32 字节密钥的 Base64，经环境或秘密管理器提供并备份；遗失后无法解密已保存 API Key。`LEXPRO_MODEL_CONFIG_ALLOWED_URLS` 为逗号分隔的完整 base_url 允许列表，默认只允许 `http://127.0.0.1:8001/v1` 和既有部署 AI 地址。不要提交真实密钥。
- `LEXPRO_AI_ENABLE_THINKING=false` 是环境配置默认值；动态模型配置启用后以生效项为准。既有 `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA` 不会被网页开关绕过。
- 本地备份：`backups/before_acceptance_20260905.dump`、`backups/before_generic_demo_accounts_20260905.dump`。
- `tools/start_local_acceptance.ps1 -Port 8080` 复用 IDEA 现有环境，但按最新要求覆盖为统一 LexPro：`LEXPRO_MODEL_CONFIG_ENABLED=false`，生成地址 `http://127.0.0.1:8001/v1`，模型 `LexPro_8B`，无鉴权占位 Key `EMPTY`，输出预算 1536 token、读取超时 180 秒。IDEA 的 `LexproBackendApplication` 程序参数配置同样覆盖原环境值；如 IDEA 未自动读取外部修改，请重新加载运行配置。历史模型配置和主密钥保留，不能删除或重新生成。仅首次启动适配器时添加 `-StartAiAdapter`。脚本不创建 SSH 隧道；运行 JAR 副本与日志位于忽略目录 `.runtime`。不要在 IDEA 和脚本中同时启动 8080。

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
| Python AI 适配服务 | `D:\desktop\soph\lexpro\backend\ai-service` |
| SQLite 迁移工具 | `D:\desktop\soph\lexpro\tools\migrate_legal_llm.py` |
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
| Python AI 适配配置 | `backend\ai-service\app\config.py` | 从进程环境读取 `LEXPRO_AI_SERVICE_*` |
| Python AI 适配依赖 | `backend\ai-service\requirements.txt` | MinerU/LexPro 内部适配服务依赖 |
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
| `LEXPRO_PARTNER_TYPICAL_CASE_BASE_URL` | 默认 `http://127.0.0.1:8000`，只连接本机 SSH 转发端口 |

### 安全开关

| 变量 | 开发要求 |
|---|---|
| `LEXPRO_FLYWAY_ENABLED` | 默认 `false`，迁移须审批 |
| `LEXPRO_FLYWAY_BASELINE_ON_MIGRATE` | 保持 `false` |
| `LEXPRO_BOOTSTRAP_ADMIN_ENABLED` | 仅首次创建开发管理员时临时开启 |
| `LEXPRO_AI_ENABLED` | 是否启用 DeepSeek 适配器 |
| `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA` | 默认 `false`；已批准向 DeepSeek 外发的部署须显式设为 `true` |
| `LEXPRO_AI_SERVICE_ENABLED` | 默认 `false`；内部 MinerU/LexPro 服务和密钥配置完成后再开启 |
| `LEXPRO_RETRIEVAL_ENABLED` | Python 检索服务准备完成后再开启 |
| `LEXPRO_TYPICAL_CASE_PROVIDER` | 默认 `LOCAL`；合作方模式显式设为 `PARTNER`，不会自动降级 |
| `LEXPRO_PARTNER_TYPICAL_CASE_ALLOW_CASE_DATA` | 默认 `false`；发送真实案件事实前须另行批准并显式开启 |
| `LEXPRO_MCP_ENABLED` | 默认 `false`，确定客户端并批准本地验收后再开启 |

### DeepSeek

`LEXPRO_AI_BASE_URL`、`LEXPRO_AI_API_KEY`、`LEXPRO_AI_MODEL`、`LEXPRO_AI_CONNECT_TIMEOUT`、
`LEXPRO_AI_READ_TIMEOUT`、`LEXPRO_AI_MAX_INPUT_CHARS` 和 `LEXPRO_AI_MAX_OUTPUT_TOKENS` 由
`application.properties` 统一定义。真实 `LEXPRO_AI_API_KEY` 只放本机环境变量。

### 内部 MinerU/LexPro AI 服务

| 变量 | 当前约定 |
|---|---|
| `LEXPRO_AI_SERVICE_ENABLED` | Spring 端默认 `false` |
| `LEXPRO_AI_SERVICE_BASE_URL` | Spring 端默认 `http://127.0.0.1:8020` |
| `LEXPRO_AI_SERVICE_INTERNAL_TOKEN` | Java/Python 两端相同，至少 32 个字符，只放环境变量 |
| `LEXPRO_AI_SERVICE_CONNECT_TIMEOUT` | Spring 端默认 `PT5S` |
| `LEXPRO_AI_SERVICE_READ_TIMEOUT` | Spring 端默认 `PT15M` |
| `LEXPRO_AI_SERVICE_MAX_FILE_SIZE` | Spring 端默认 `25MB`；不得大于 Python 端字节上限 |
| `LEXPRO_AI_SERVICE_MINERU_BASE_URL` | Python 端 MinerU 地址，开发默认 `http://127.0.0.1:13456` |
| `LEXPRO_AI_SERVICE_MINERU_VERSION` | 写入解析结果的可追溯部署版本 |
| `LEXPRO_AI_SERVICE_LEXPRO_BASE_URL` | Python 端 LexPro vLLM 地址，开发默认 `http://127.0.0.1:8001` |
| `LEXPRO_AI_SERVICE_LEXPRO_MODEL_NAME` | vLLM 模型 ID，当前约定 `LexPro_8B` |
| `LEXPRO_AI_SERVICE_LEXPRO_MODEL_VERSION` | 写入实体结果的可追溯部署版本 |

该服务不自动读取 `.env`，真实密钥和服务器地址只注入启动进程。Spring 开关关闭时不会发送文件。

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

### 合作方典型案例服务

| 变量 | 当前约定 |
|---|---|
| `LEXPRO_PARTNER_TYPICAL_CASE_BASE_URL` | `http://127.0.0.1:8000`；明文 HTTP 只允许回环地址 |
| `LEXPRO_PARTNER_TYPICAL_CASE_CONNECT_TIMEOUT` | 默认 `PT3S` |
| `LEXPRO_PARTNER_TYPICAL_CASE_ANALYZE_TIMEOUT` | 默认 `PT60S` |
| `LEXPRO_PARTNER_TYPICAL_CASE_SEARCH_TIMEOUT` | 默认 `PT30S` |
| `LEXPRO_PARTNER_TYPICAL_CASE_MAX_RESULTS` | 默认 `20`，最大 `100` |
| `LEXPRO_PARTNER_TYPICAL_CASE_ANALYSIS_TTL` | 默认 `PT10M`，最大 `PT1H` |
| `LEXPRO_PARTNER_TYPICAL_CASE_TOKEN_SECRET` | `PARTNER` 模式必需，至少 32 字节，只放环境变量 |

合作方服务由对方托管。需要调用时，由运维在运行 Spring Boot 的同一台服务器上长期维护以下隧道；Java 只调用本机 `8000` 端口，不创建、重启或监控 SSH 进程：

```powershell
ssh -L 8000:localhost:8000 zcy@211.87.232.203
```

隧道建立不代表允许外发真实案件数据；`LEXPRO_PARTNER_TYPICAL_CASE_ALLOW_CASE_DATA` 仍保持默认关闭，开启前须单独批准。

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

Python AI 服务可在本地开发时复用检索服务虚拟环境；不要在共享环境中强制降级既有依赖：

```powershell
Set-Location 'D:\desktop\soph\lexpro\backend\ai-service'
$env:LEXPRO_AI_SERVICE_INTERNAL_TOKEN = '<至少32个字符的本机开发密钥>'
& '..\retrieval-service\.venv\Scripts\python.exe' -m uvicorn app.main:app --host 127.0.0.1 --port 8020
```

服务器部署时仍应使用 AI 服务独立虚拟环境，并按其 `requirements.txt` 固定依赖。

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
| Python AI 服务健康检查 | `http://127.0.0.1:8020/internal/v1/health`（需要内部令牌） |
| 合作方典型案例隧道 | `http://127.0.0.1:8000`（仅隧道运行时可用） |
| MCP Server（启用后） | `http://127.0.0.1:8080/mcp` |

## 8. 后续使用规则

1. 优先查本文，再查 `application.properties`、模块文档和本机注册表，不要反复扫描整个磁盘。
2. 不要把 `D:\SQL\PosrgreSQL\18\date` 自动改写为常见的 `PostgreSQL\data`。
3. 命令行找不到工具时先使用本文绝对路径，不要重复安装 Java、Maven、Git、Node 或 Python。
4. 路径存在不代表允许执行迁移、删除、外发数据或修改生产配置；这些操作仍需单独批准。
5. 新增或迁移关键工具后，只更新本文对应条目，不另建重复的环境说明文档。
