<template>
  <div class="page processing-page">
    <div class="page-title-row"><h2 class="page-title">法律要素识别</h2><el-button type="primary" :icon="Aim" :disabled="!document" :loading="running" @click="recognize">开始识别</el-button></div>
    <DocumentContextPanel @document="setDocument" />
    <div class="split-layout">
      <SectionCard title="解析正文"><el-input :model-value="document?.rawText || ''" type="textarea" :rows="22" readonly resize="none" placeholder="请选择已成功解析的文书" /></SectionCard>
      <SectionCard title="法律要素结果">
        <template #extra><el-select v-model="selectedId" size="small" placeholder="结果版本"><el-option v-for="item in history" :key="item.elementResultId" :value="item.elementResultId" :label="formatVersion(item)" /></el-select></template>
        <el-empty v-if="!elements.length" description="暂无真实识别结果" />
        <div v-else class="element-list"><div v-for="(item, index) in elements" :key="index" class="element-item"><div><el-tag type="primary">{{ item.name || item.code }}</el-tag><span v-if="item.confidence != null">{{ percent(item.confidence) }}</span></div><p>{{ item.content }}</p><blockquote v-for="(evidence, i) in item.evidence || []" :key="i">{{ evidence.quote }}</blockquote></div></div>
      </SectionCard>
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import DocumentContextPanel from '../components/common/DocumentContextPanel.vue'
import { getLegalElementJob, listLegalElementResults, startLegalElementRecognition } from '../api/processing'
import { waitForJob } from '../utils/jobs'
const document=ref(null);const history=ref([]);const selectedId=ref(null);const running=ref(false)
const selected=computed(()=>history.value.find((item)=>item.elementResultId===selectedId.value));const elements=computed(()=>(selected.value?.finalElements||selected.value?.originalElements)?.elements||[])
async function setDocument(value){document.value=value;history.value=[];selectedId.value=null;if(value){history.value=await listLegalElementResults(value.caseId,value.docId);selectedId.value=history.value[0]?.elementResultId||null}}
async function recognize(){running.value=true;try{const created=await startLegalElementRecognition(document.value.caseId,document.value.docId);const completed=await waitForJob(()=>getLegalElementJob(document.value.caseId,document.value.docId,created.requestId));if(completed){history.value=await listLegalElementResults(document.value.caseId,document.value.docId);selectedId.value=completed.elementResultId;ElMessage.success('法律要素识别完成')}else ElMessage.info('任务仍在后台执行，请稍后重新进入页面')}catch(error){ElMessage.error(error.message)}finally{running.value=false}}
function percent(value){return `${Math.round(value*100)}%`}function formatVersion(item){return `${new Date(item.createdAt).toLocaleString('zh-CN')}${item.confirmedAt?' · 已确认':''}`}
</script>
<style scoped>.processing-page{display:grid;gap:16px}.split-layout{display:grid;grid-template-columns:1fr 1fr;gap:16px;align-items:start}.element-list{display:grid;gap:12px}.element-item{padding:14px;background:#f7f9fd;border-left:4px solid var(--primary);border-radius:6px}.element-item>div{display:flex;justify-content:space-between}.element-item p{line-height:1.7}.element-item blockquote{margin:8px 0 0;padding:8px 12px;color:#52657a;background:#fff;border-left:3px solid #cbd8eb}@media(max-width:900px){.split-layout{grid-template-columns:1fr}}</style>
