<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">法律要素识别</h2>
      <el-button type="primary" :icon="Aim" @click="ElMessage.success('已完成要素识别')">开始识别</el-button>
    </div>
    <div class="split-layout">
      <SectionCard title="文书输入 / 上传">
        <el-upload drag action="#" :auto-upload="false" class="upload-box">
          <el-icon><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文书到此处，或点击选择文件</div>
        </el-upload>
        <el-input v-model="sourceText" type="textarea" :rows="14" resize="none" />
      </SectionCard>
      <SectionCard title="法律要素识别结果">
        <div class="element-list">
          <div v-for="item in data.results" :key="item.title" class="element-item">
            <h3>{{ item.title }}</h3>
            <p>{{ item.content }}</p>
          </div>
        </div>
      </SectionCard>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim, UploadFilled } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import data from '../mock/legalElements.json'

const sourceText = ref(data.sourceText)
</script>

<style scoped>
.split-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.upload-box {
  margin-bottom: 16px;
}

.element-list {
  display: grid;
  gap: 14px;
}

.element-item {
  padding: 16px;
  background: #f7f9fd;
  border-left: 4px solid var(--primary);
  border-radius: 6px;
}

.element-item h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.element-item p {
  margin: 0;
  color: #5f6f82;
  line-height: 1.7;
}
</style>
