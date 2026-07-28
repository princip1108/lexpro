<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">案例摘要生成</h2>
      <div class="toolbar">
        <el-button :icon="Refresh" @click="ElMessage.success('已重新生成摘要')">重新生成</el-button>
        <el-button type="primary" :icon="DocumentChecked" @click="ElMessage.success('摘要已保存')">保存摘要</el-button>
      </div>
    </div>
    <div class="split-layout">
      <SectionCard title="原始案例文本">
        <el-input v-model="sourceText" type="textarea" :rows="22" resize="none" />
      </SectionCard>
      <SectionCard title="生成摘要">
        <div class="summary-list">
          <div v-for="item in data.sections" :key="item.title" class="summary-item">
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
import { DocumentChecked, Refresh } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import data from '../mock/summary.json'

const sourceText = ref(data.sourceText)
</script>

<style scoped>
.split-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.summary-list {
  display: grid;
  gap: 14px;
}

.summary-item {
  padding: 16px 18px;
  background: #f6f8fc;
  border: 1px solid #edf1f7;
  border-radius: 6px;
}

.summary-item h3 {
  margin: 0 0 10px;
  color: var(--primary-deep);
  font-size: 16px;
}

.summary-item p {
  margin: 0;
  color: #5f6f82;
  line-height: 1.8;
}
</style>
