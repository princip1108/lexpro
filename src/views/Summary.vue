<template>
  <div class="page summary-page">
    <div class="page-title-row"><h2 class="page-title">案件摘要</h2><el-button type="primary" :icon="DocumentChecked" :disabled="!document" :loading="running" @click="generate">生成摘要</el-button></div>
    <DocumentContextPanel @document="setDocument" @case="setCase" />
    <div class="summary-toolbar">
      <el-segmented v-model="summaryType" :options="summaryTypes" @change="loadHistory" />
      <el-select v-model="selectedId" placeholder="历史版本" @change="selectSummary"><el-option v-for="item in history" :key="item.summaryId" :value="item.summaryId" :label="`V${item.versionNo}${item.current ? ' · 当前' : ''}${item.confirmedAt ? ' · 已确认' : ''}`" /></el-select>
      <el-button v-if="selected && !selected.confirmedAt" :disabled="!canExecute" @click="confirm">确认当前摘要</el-button>
    </div>
    <div class="split-layout">
      <SectionCard title="来源文书"><el-input :model-value="document?.rawText || ''" type="textarea" :rows="22" readonly resize="none" /></SectionCard>
      <SectionCard title="摘要结果"><el-empty v-if="!selected" description="暂无该类型的真实摘要" /><div v-else class="summary-result"><div class="summary-meta"><el-tag>{{ typeLabel(summaryType) }}</el-tag><span>{{ selected.modelName || '模型信息未记录' }}</span><span>{{ formatTime(selected.createdAt) }}</span></div><p>{{ selected.summaryText }}</p></div></SectionCard>
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import DocumentContextPanel from '../components/common/DocumentContextPanel.vue'
import { hasPermission } from '../auth/session'
import { confirmCaseSummary, getCaseSummaryJob, listCaseSummaries, startCaseSummary } from '../api/processing'
import { waitForJob } from '../utils/jobs'
const summaryTypes=[{label:'事实',value:'FACT'},{label:'过程',value:'PROCESS'},{label:'结论',value:'CONCLUSION'},{label:'完整摘要',value:'FULL'}]
const caseId=ref(null);const document=ref(null);const summaryType=ref('FULL');const history=ref([]);const selectedId=ref(null);const running=ref(false);const canExecute=hasPermission('AI_EXECUTE')
const selected=computed(()=>history.value.find((item)=>item.summaryId===selectedId.value))
function setCase(value){caseId.value=value}
async function setDocument(value){document.value=value;await loadHistory()}
async function loadHistory(){history.value=[];selectedId.value=null;if(!caseId.value)return;try{history.value=await listCaseSummaries(caseId.value,summaryType.value);selectedId.value=(history.value.find((i)=>i.current)||history.value[0])?.summaryId||null}catch(error){ElMessage.error(error.message)}}
async function generate(){running.value=true;try{const created=await startCaseSummary(caseId.value,{summaryType:summaryType.value,sourceDocIds:[document.value.docId]});const completed=await waitForJob(()=>getCaseSummaryJob(caseId.value,created.requestId));if(completed){await loadHistory();selectedId.value=completed.summaryId;ElMessage.success('摘要生成完成')}else ElMessage.info('任务仍在后台执行，请稍后刷新')}catch(error){ElMessage.error(error.message)}finally{running.value=false}}
async function confirm(){try{await confirmCaseSummary(caseId.value,selectedId.value);ElMessage.success('摘要已确认');await loadHistory()}catch(error){ElMessage.error(error.message)}}
function selectSummary(){}function typeLabel(value){return summaryTypes.find((i)=>i.value===value)?.label||value}function formatTime(value){return value?new Date(value).toLocaleString('zh-CN'):'-'}
</script>
<style scoped>.summary-page{display:grid;gap:16px}.summary-toolbar{display:grid;grid-template-columns:minmax(380px,1fr) 220px auto;gap:12px;align-items:center}.split-layout{display:grid;grid-template-columns:1fr 1fr;gap:16px;align-items:start}.summary-result{display:grid;gap:18px}.summary-meta{display:flex;gap:12px;align-items:center;color:#64748b;font-size:13px}.summary-result p{margin:0;white-space:pre-wrap;line-height:2;color:#35465a}@media(max-width:900px){.summary-toolbar,.split-layout{grid-template-columns:1fr}}</style>
