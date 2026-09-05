# LexPro 多模态询问笔录实体识别系统

LexPro 接收 PDF、PNG/JPG、DOC/DOCX 法律文书，先由 MinerU 解析为有序文本块，再由 LexPro_8B 对当前文档的文本块进行批量实体识别。输出同时包含每个子句的实体以及文档级去重实体。

## 1. 交付内容

完整交付目录已经包含代码、两个模型和九份测试文件：

```text
model/LexPro_8B/                 LexPro 实体识别模型
model/MinerU2.5-2509-1.2B/       MinerU 文档解析模型
data/sample_case/                九份 PDF/PNG/DOCX 示例
code/                            流水线、服务和环境文件
output/                          服务日志和推理结果
docs/                            完整部署说明书
```

所有模型路径均相对于项目根目录。整个目录移动到其他位置后无需修改 Python 代码。

## 2. 环境准备

推荐 Ubuntu 20.04/22.04、两张 NVIDIA GPU，其中 LexPro_8B 使用一张 48GB GPU，MinerU 使用另一张 GPU。服务器验证环境为 Python 3.12.12、PyTorch 2.8.0+cu128、MinerU 2.7.1、vLLM 0.11.0。

```bash
cd xunwenbilu6.0
conda env create -f code/requirements/environment.yml
conda activate LexPro
sudo apt-get update
sudo apt-get install -y libreoffice curl
cp code/config/deploy.env.example code/config/deploy.env
```

检查 `code/config/deploy.env`。默认模型路径已经指向交付包内模型，GPU 0 运行 MinerU，GPU 1 运行 vLLM。

## 3. 启动服务

```bash
bash code/services/start_services.sh
bash code/services/check_services.sh
```

查看日志：

```bash
tail -f output/services/mineru.log
tail -f output/services/vllm.log
```

停止服务：

```bash
bash code/services/stop_services.sh
```

## 4. 执行推理

单文件：

```bash
bash code/scripts/infer_file.sh "data/sample_case/（2011）惠中法刑一初字第112号_第一次询问笔录.pdf"
```

批量目录：

```bash
bash code/scripts/infer_batch.sh data/sample_case --run-id sample_9files --force
```

自定义子句 batch size：

```bash
bash code/scripts/infer_batch.sh data/sample_case --batch-size 64
```

批量任务逐文档执行；同一个文档的全部子句按 batch size 分批推理，不会把不同文档的子句混在一个 batch 中。

九文件完成后可执行只读结果审计：

```bash
python code/scripts/audit_results.py output/runs/sample_9files --expected-count 9
```

需要同时校验模型 SHA256、代码、样例、文档、运行结果和服务状态时：

```bash
bash code/scripts/verify_delivery.sh output/runs/sample_9files
```

## 5. 输出

结果位于 `output/runs/<run_id>/`：

```text
parsed/<document_id>/raw.json       MinerU 原始响应
parsed/<document_id>/content.json   规范化子句
parsed/<document_id>/document.txt   纯文本
results/<document_id>/entities.json 子句实体与文档级实体
summary.json                         批次汇总
failures.jsonl                       失败记录与重试命令
```

`sentences[].entities` 是子句级实体；`document_entities` 按 `(category, entity)` 去重，并记录出现次数和来源子句。没有识别到实体时输出空数组，这是正常结果。

## 6. OOM 调整

48GB GPU 首先使用：

```text
VLLM_MAX_NUM_SEQS=128
INFERENCE_BATCH_SIZE=128
```

出现 CUDA OOM 时，在 `code/config/deploy.env` 中将两项依次调整为 64、32、16，然后重启服务。通常先降低 `INFERENCE_BATCH_SIZE` 即可降低当前文档的推理峰值。

详细安装、模型下载、故障处理和验收步骤参见 [部署说明书](docs/LexPro多模态实体识别模型部署说明书.md)。

服务器最终实测记录：九份示例文件 9/9 成功、失败 0；共解析 210 个子句，生成 205 个子句级实体和 135 个文档级去重实体，实体位置、去重元数据及 3 份 Word 转换产物均通过自动审计。
