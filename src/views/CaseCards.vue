<template>
  <LegalFeatureContext title="案卡回填" :show-dossier="false" @case="loadHistory">
    <div class="card mt16">
      <div class="card-title"><span class="bar"></span>案卡回填
        <span class="muted-sm" style="margin-left:8px">根据案件信息 + 实体/要素/摘要结果自动映射案卡字段</span>
        <button v-if="canGenerate" class="btn primary sm" style="margin-left:auto" :disabled="!caseId||generating||loading||saving" @click="generate">{{ generating?'生成中…':detail?'重新生成':'生成回填任务' }}</button>
      </div>
      <div v-loading="loading" class="card-pad">
        <div v-if="generating" class="empty">正在生成回填任务…</div>
        <template v-else-if="detail">
          <div class="cause-banner">
            <b>回填任务 {{ detail.task.fillTaskId }}</b><span>字段确认 {{ confirmedCount }} / {{ detail.fields.length }}</span>
            <span class="badge" :class="detail.task.fillStatus==='CONFIRMED'?'b-green':'b-blue'">{{ statusLabel(detail.task.fillStatus) }}</span>
            <button v-if="canManage&&detail.task.fillStatus==='DRAFT'" class="btn success sm" style="margin-left:auto" :disabled="saving" @click="confirmCard">{{ saving?'保存中…':'整体确认回填' }}</button>
          </div>
          <div class="card"><div class="table-wrap"><table class="table field-table"><thead><tr><th>字段</th><th>候选值(可编辑)</th><th>来源</th><th>置信度</th><th>状态</th><th>操作</th></tr></thead>
            <tbody><tr v-for="row in detail.fields" :key="row.fieldId">
              <td><b>{{ row.fieldName }}</b><div class="src-tag">{{ row.fieldCode }}</div></td>
              <td class="fv"><input v-model="row._value" :disabled="!editable(row)||saving" :aria-label="row.fieldName"></td>
              <td><span class="src-tag" :title="row.sourceText||''">{{ fileNames[row.sourceFileId]||row.sourceText||'—' }}</span></td>
              <td><span class="conf-rate">{{ row.confidence==null?'—':Math.round(row.confidence*100)+'%' }}</span></td>
              <td><span class="badge" :class="row.confirmStatus==='CONFIRMED'?'b-green':row.confirmStatus==='REJECTED'?'b-red':'b-gray'">{{ fieldStatusLabel(row.confirmStatus) }}</span></td>
              <td><button v-if="editable(row)" class="btn success sm" :disabled="saving" @click="confirmField(row)">确认</button><span v-else>{{ row.confirmStatus==='CONFIRMED'?'✓':'—' }}</span></td>
            </tr></tbody>
          </table></div></div>
        </template>
        <div v-else class="empty">点击右上角“生成回填任务”,自动提取案卡字段</div>
      </div>
    </div>
  </LegalFeatureContext>
</template>

<script setup>
import { computed,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import LegalFeatureContext from '../components/common/LegalFeatureContext.vue'
import { hasPermission } from '../auth/session'
import { listCaseSummaries,listEntityResults,listLegalElementResults } from '../api/processing'
import { listDossierFiles,listParseResults } from '../api/dossier'
import { confirmCaseCard,confirmCaseCardField,getCaseCard,getCaseCardJob,listCaseCards,startCaseCard } from '../api/reports'
import { waitForJob } from '../utils/jobs'
import { cardFieldPending,cardFieldValue } from '../utils/caseCard'

const caseId=ref(null),detail=ref(null),generating=ref(false),loading=ref(false),saving=ref(false),fileNames=ref({})
let selectionVersion=0
const canGenerate=hasPermission('REPORT_MANAGE')&&hasPermission('AI_EXECUTE'),canManage=hasPermission('REPORT_MANAGE')
const confirmedCount=computed(()=>detail.value?.fields.filter(row=>row.confirmStatus==='CONFIRMED').length||0)
function applyDetail(value){value.fields.forEach(row=>{row._value=displayValue(row.value)});detail.value=value}
async function loadHistory(value){
  const version=++selectionVersion;caseId.value=value;detail.value=null;fileNames.value={};loading.value=Boolean(value)
  if(!value)return
  try{
    const [history,files]=await Promise.all([listCaseCards(value),listDossierFiles(value)])
    if(version!==selectionVersion)return
    fileNames.value=Object.fromEntries(files.map(file=>[file.dossierId,file.fileName]))
    const latest=history.find(item=>['DRAFT','CONFIRMED'].includes(item.fillStatus))
    if(latest){const result=await getCaseCard(value,latest.fillTaskId);if(version===selectionVersion)applyDetail(result)}
  }catch(error){if(version===selectionVersion)ElMessage.error(error.message)}finally{if(version===selectionVersion)loading.value=false}
}
async function loadSources(id){
  const sources=[]
  for(const file of await listDossierFiles(id)){
    const parses=await listParseResults(id,file.dossierId),parsed=parses.find(item=>item.current&&item.parseStatus==='SUCCESS')||parses.find(item=>item.parseStatus==='SUCCESS')
    if(!parsed)continue
    sources.push({sourceType:'DOCUMENT',sourceId:parsed.docId})
    const [entities,elements]=await Promise.all([listEntityResults(id,parsed.docId),listLegalElementResults(id,parsed.docId)])
    const entity=entities.find(item=>item.confirmedAt)||entities[0],element=elements.find(item=>item.confirmedAt)||elements[0]
    if(entity)sources.push({sourceType:'ENTITY',sourceId:entity.entityResultId})
    if(element)sources.push({sourceType:'LEGAL_ELEMENT',sourceId:element.elementResultId})
  }
  const groups=await Promise.all(['FACT','PROCESS','CONCLUSION','FULL'].map(type=>listCaseSummaries(id,type)))
  for(const group of groups){const summary=group.find(item=>item.current)||group[0];if(summary)sources.push({sourceType:'SUMMARY',sourceId:summary.summaryId})}
  return sources
}
async function generate(){
  if(detail.value){try{await ElMessageBox.confirm('该案件已有回填任务，重新生成将新建任务并替换当前展示，确认继续？','重新生成',{confirmButtonText:'确认',cancelButtonText:'取消'})}catch{return}}
  const id=caseId.value,version=selectionVersion;generating.value=true
  try{
    const sources=await loadSources(id)
    if(!sources.length){ElMessage.warning('请先上传并解析案件文书，或确认实体、要素、摘要结果');return}
    const created=await startCaseCard(id,{fillMode:'AUTO',sources}),completed=await waitForJob(()=>getCaseCardJob(id,created.requestId))
    if(completed){const result=await getCaseCard(id,completed.fillTaskId);if(version===selectionVersion){applyDetail(result);ElMessage.success('案卡生成完成')}}
    else ElMessage.info('案卡仍在后台生成，请稍后重新选择案件查看')
  }catch(error){ElMessage.error(error.message)}finally{generating.value=false}
}
async function saveField(id,taskId,row){
  const payload={status:'CONFIRMED'}
  if(String(row._value)!==displayValue(row.value))payload.value=cardFieldValue(row)
  const result=await confirmCaseCardField(id,taskId,row.fieldId,payload)
  Object.assign(row,result,{_value:displayValue(result.value)})
}
async function confirmField(row){saving.value=true;try{await saveField(caseId.value,detail.value.task.fillTaskId,row);ElMessage.success('字段已确认')}catch(error){ElMessage.error(error.message)}finally{saving.value=false}}
async function confirmCard(){
  const id=caseId.value,task=detail.value,version=selectionVersion;saving.value=true
  try{
    for(const row of task.fields.filter(cardFieldPending))await saveField(id,task.task.fillTaskId,row)
    const result=await confirmCaseCard(id,task.task.fillTaskId)
    if(version===selectionVersion)applyDetail(result)
    ElMessage.success('案卡已确认')
  }catch(error){ElMessage.error(error.message)}finally{saving.value=false}
}
function editable(row){return canManage&&detail.value?.task.fillStatus==='DRAFT'&&cardFieldPending(row)}
function displayValue(value){return value==null?'':typeof value==='object'?JSON.stringify(value):String(value)}
function fieldStatusLabel(value){return({CONFIRMED:'已确认',REJECTED:'已拒绝',UNCONFIRMED:'待确认'})[value]||'待确认'}
function statusLabel(value){return({PROCESSING:'生成中',DRAFT:'待确认',CONFIRMED:'已回填',FAILED:'失败'})[value]||value}
</script>

<style scoped>
.field-table .fv input{min-width:170px}.field-table .fv input:disabled{color:var(--text);opacity:1;background:var(--bg)}
</style>
