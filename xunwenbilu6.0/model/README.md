# 模型目录说明

交付完成后，本目录应包含：

```text
model/
├── LexPro_8B/
│   ├── config.json
│   ├── tokenizer.json
│   ├── model.safetensors.index.json
│   └── model-00001-of-00004.safetensors ... model-00004-of-00004.safetensors
├── MinerU2.5-2509-1.2B/
├── checksums.sha256
└── README.md
```

项目默认直接使用随交付包提供的模型，不需要联网。

## MinerU 备用下载方式

Hugging Face：

```bash
pip install -U huggingface_hub
hf download opendatalab/MinerU2.5-2509-1.2B \
  --local-dir model/MinerU2.5-2509-1.2B
```

ModelScope：

```bash
pip install -U modelscope
modelscope download --model OpenDataLab/MinerU2.5-2509-1.2B \
  --local_dir model/MinerU2.5-2509-1.2B
```

下载工具会复用已完成文件，命令中断后重新执行即可继续。若需指定项目内缓存或网络代理：

```bash
export HF_HOME="$PWD/model/.download_cache/huggingface"
export MODELSCOPE_CACHE="$PWD/model/.download_cache/modelscope"
export HTTPS_PROXY=http://代理地址:端口       # 无代理时不要设置
# Hugging Face 镜像（可选，使用前由部署方确认镜像可信）：
export HF_ENDPOINT=https://hf-mirror.com
```

官方仓库页面：

- Hugging Face：<https://huggingface.co/opendatalab/MinerU2.5-2509-1.2B>
- ModelScope：<https://modelscope.cn/models/OpenDataLab/MinerU2.5-2509-1.2B>

下载必须固定为 `MinerU2.5-2509-1.2B`，不要用其他版本覆盖交付模型。下载完成后运行：

```bash
find model/LexPro_8B model/MinerU2.5-2509-1.2B -type f | sort
sha256sum -c model/checksums.sha256
```

`checksums.sha256` 只校验两个模型目录中的普通文件。若重新下载的仓库包含不同缓存元数据，应重新生成校验清单并保留下载版本信息。
