from modelscope import snapshot_download
import os

# 设置下载路径 (注意前面加了 r，防止反斜杠转义报错)
# 这里我帮你把路径补全到了 Qwen3-14B 文件夹
download_path = r"E:\类案检索\typical_case_service\models"

print(f"开始下载模型到: {download_path}")
print("这可能需要一些时间，取决于网速...")

try:
    # 执行下载
    model_dir = snapshot_download(
        'Qwen/Qwen3-14B',
        cache_dir=download_path
    )
    print("-" * 30)
    print("✅ 下载成功！")
    print(f"模型实际位置: {model_dir}")
except Exception as e:
    print(f"❌ 下载出错: {e}")