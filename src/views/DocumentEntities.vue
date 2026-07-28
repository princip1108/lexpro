<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">文书实体识别</h2>
      <el-button type="primary" :icon="Tickets" @click="ElMessage.success('已完成实体识别')">开始识别</el-button>
    </div>
    <div class="split-layout">
      <SectionCard title="文书输入 / 上传">
        <el-upload drag action="#" :auto-upload="false" class="upload-box">
          <el-icon><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文书到此处，或点击选择文件</div>
        </el-upload>
        <el-input v-model="sourceText" type="textarea" :rows="14" resize="none" />
      </SectionCard>
      <SectionCard title="实体识别结果">
        <div class="entity-overview">
          <div v-for="item in data.results" :key="item.title" class="entity-card">
            <span>{{ item.title }}</span>
            <strong>{{ entityCount(item.content) }}</strong>
          </div>
        </div>
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
import { Tickets, UploadFilled } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import data from '../mock/documentEntities.json'

const sourceText = ref(data.sourceText)
const entityCount = (content) => content.split('、').length
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

.entity-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.entity-card {
  display: grid;
  gap: 6px;
  padding: 14px;
  background: #f1fbfb;
  border: 1px solid #d5f0f0;
  border-radius: 6px;
}

.entity-card span {
  color: #64748b;
  font-size: 13px;
}

.entity-card strong {
  color: #0f9f9f;
  font-size: 22px;
}

.element-list {
  display: grid;
  gap: 14px;
}

.element-item {
  padding: 16px;
  background: #f7f9fd;
  border-left: 4px solid #15b8a6;
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
