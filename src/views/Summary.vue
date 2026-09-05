<template>
  <LegalFeatureContext title="案例摘要生成" @case="setCase" @document="setDocument">
    <div class="card mt16"><div class="card-title"><span class="bar"></span>案例摘要生成</div><div class="card-pad">
      <div class="analysis-bar"><button class="btn primary sm" :disabled="!document||running" @click="generate">{{ running?'生成中…':'生成摘要' }}</button><span class="muted" style="margin-left:auto">卷宗: {{ document?.fileName || document?.dossierFileName || '请选择卷宗' }}</span></div>
      <div class="result-sect">
        <div class="sect-head"><h4>📝 案例摘要生成</h4><div class="summ-opts"><div class="seg"><button v-for="item in summaryTypes" :key="item.value" :class="{on:summaryType===item.value}" @click="changeType(item.value)">{{ item.label }}</button></div></div><div class="flex"><select v-model="selectedId" :disabled="!history.length"><option :value="null">历史版本</option><option v-for="item in history" :key="item.summaryId" :value="item.summaryId">{{ formatTime(item.createdAt) }}{{ item.current?' · 当前':'' }}</option></select><span v-if="selected" class="tag" :class="selected.confirmedAt?'green':'gold'">{{ selected.confirmedAt?'已确认':'待确认' }}</span></div></div>
        <div class="card"><div class="card-pad report-editor"><div v-if="!selected" class="empty">选择摘要类型后，点击“生成摘要”</div><template v-else><textarea :value="selected.summaryText" rows="12" readonly></textarea><div class="flex flex-end mt8"><span class="muted">类型: {{ typeLabel(selected.summaryType) }} · 模型: {{ selected.modelName || '-' }} · {{ formatTime(selected.createdAt) }}</span><button v-if="!selected.confirmedAt&&canExecute" class="btn success sm" @click="confirm">确认并保存</button></div></template></div></div>
      </div>
    </div></div>
  </LegalFeatureContext>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import LegalFeatureContext from '../components/common/LegalFeatureContext.vue'
import { hasPermission } from '../auth/session'
import { confirmCaseSummary, getCaseSummaryJob, listCaseSummaries, startCaseSummary } from '../api/processing'
import { waitForJob } from '../utils/jobs'

const summaryTypes=[{label:'事实摘要',value:'FACT'},{label:'办理过程',value:'PROCESS'},{label:'审查结论',value:'CONCLUSION'},{label:'完整摘要',value:'FULL'}]
const caseId=ref(null),document=ref(null),summaryType=ref('FULL'),history=ref([]),selectedId=ref(null),running=ref(false)
const canExecute=hasPermission('AI_EXECUTE'),selected=computed(()=>history.value.find((item)=>item.summaryId===selectedId.value))
function setCase(value){caseId.value=value;history.value=[];selectedId.value=null}async function setDocument(value){document.value=value;await loadHistory()}
async function changeType(value){summaryType.value=value;await loadHistory()}
async function loadHistory(){history.value=[];selectedId.value=null;if(!caseId.value)return;try{history.value=await listCaseSummaries(caseId.value,summaryType.value);selectedId.value=(history.value.find((item)=>item.current)||history.value[0])?.summaryId||null}catch(error){ElMessage.error(error.message)}}
async function generate(){running.value=true;try{const created=await startCaseSummary(caseId.value,{summaryType:summaryType.value,sourceDocIds:[document.value.docId]}),completed=await waitForJob(()=>getCaseSummaryJob(caseId.value,created.requestId));if(completed){await loadHistory();selectedId.value=completed.summaryId;ElMessage.success('摘要生成完成')}else ElMessage.info('任务仍在后台执行')}catch(error){ElMessage.error(error.message)}finally{running.value=false}}
async function confirm(){try{await confirmCaseSummary(caseId.value,selectedId.value);await loadHistory();ElMessage.success('摘要已确认')}catch(error){ElMessage.error(error.message)}}
function typeLabel(value){return summaryTypes.find((item)=>item.value===value)?.label||value}function formatTime(value){return value?new Date(value).toLocaleString('zh-CN'):'-'}
</script>

<style scoped>
.analysis-bar select,.sect-head select{padding:6px 9px;border:1px solid var(--border);border-radius:7px;background:#fff}.seg button{font-family:inherit}.report-editor textarea{width:100%;padding:12px 14px;border:1px solid var(--border);border-radius:8px;color:var(--text);font:14px/1.9 inherit;resize:vertical;background:#fff}
</style>
