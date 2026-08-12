<template>
  <div class="page processing-page">
    <div class="page-title-row"><h2 class="page-title">文书实体识别</h2><el-button type="primary" :icon="Tickets" :disabled="!document" :loading="running" @click="recognize">开始识别</el-button></div>
    <DocumentContextPanel @document="setDocument" />
    <div class="split-layout">
      <SectionCard title="解析正文"><el-input :model-value="document?.rawText || ''" type="textarea" :rows="22" readonly resize="none" placeholder="请选择已成功解析的文书" /></SectionCard>
      <SectionCard title="识别结果">
        <template #extra><el-select v-model="selectedId" size="small" placeholder="结果版本" @change="selectResult"><el-option v-for="item in history" :key="item.entityResultId" :value="item.entityResultId" :label="formatVersion(item)" /></el-select></template>
        <el-empty v-if="!entities.length" description="暂无真实识别结果" />
        <div v-else class="result-list"><div v-for="(item, index) in entities" :key="index" class="result-item"><el-tag>{{ item.type || 'UNKNOWN' }}</el-tag><strong>{{ item.text }}</strong><span v-if="item.confidence != null">置信度 {{ percent(item.confidence) }}</span></div></div>
      </SectionCard>
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import DocumentContextPanel from '../components/common/DocumentContextPanel.vue'
import { getEntityRecognitionJob, listEntityResults, startEntityRecognition } from '../api/processing'
import { waitForJob } from '../utils/jobs'
const document = ref(null); const history = ref([]); const selectedId = ref(null); const running = ref(false)
const selected = computed(() => history.value.find((item) => item.entityResultId === selectedId.value))
const entities = computed(() => (selected.value?.finalEntities || selected.value?.originalEntities)?.entities || [])
async function setDocument(value) { document.value = value; history.value = []; selectedId.value = null; if (value) { history.value = await listEntityResults(value.caseId, value.docId); selectedId.value = history.value[0]?.entityResultId || null } }
async function recognize() { running.value = true; try { const created=await startEntityRecognition(document.value.caseId,document.value.docId);const completed=await waitForJob(()=>getEntityRecognitionJob(document.value.caseId,document.value.docId,created.requestId));if(completed){history.value=await listEntityResults(document.value.caseId,document.value.docId);selectedId.value=completed.entityResultId;ElMessage.success('实体识别完成')}else ElMessage.info('任务仍在后台执行，请稍后重新进入页面') } catch (error) { ElMessage.error(error.message) } finally { running.value = false } }
function selectResult() {} function percent(value) { return `${Math.round(value * 100)}%` } function formatVersion(item) { return `${new Date(item.createdAt).toLocaleString('zh-CN')}${item.confirmedAt ? ' · 已确认' : ''}` }
</script>
<style scoped>.processing-page{display:grid;gap:16px}.split-layout{display:grid;grid-template-columns:1fr 1fr;gap:16px;align-items:start}.result-list{display:grid;gap:10px}.result-item{display:grid;grid-template-columns:110px minmax(0,1fr) 90px;gap:10px;align-items:center;padding:12px;background:#f7f9fd;border-radius:6px}.result-item span{color:#64748b;font-size:12px}@media(max-width:900px){.split-layout{grid-template-columns:1fr}}</style>
