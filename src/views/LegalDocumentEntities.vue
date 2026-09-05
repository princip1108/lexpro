<template>
  <LegalFeatureContext title="询问讯问笔录实体识别" @document="setDocument">
    <template #preview>
      <div v-if="document?.rawText" class="doc-preview entity-preview">
        <template v-for="segment in segments" :key="segment.key"><i v-for="index in segment.starts" :key="index" :ref="(element)=>registerAnchor(index,element)" class="entity-anchor"/><mark v-if="segment.indexes.length" class="ent-hl" :class="[typeMeta(draft[segment.indexes[0]]?.type).css,{focused:segment.indexes.includes(focusedIndex)}]" :title="segment.indexes.map(index=>`${typeMeta(draft[index].type).label}: ${draft[index].text}`).join('\n')" @click="focus(segment.indexes[0],false)">{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template>
      </div>
      <div v-else class="doc-preview">选择卷宗后显示解析文本</div>
    </template>

    <div class="card mt16">
      <div class="card-title"><span class="bar"></span>询问讯问笔录实体识别</div>
      <div class="card-pad">
        <div class="analysis-bar"><button class="btn primary sm" :disabled="!document||running" type="button" @click="recognize">{{ running ? '识别中…' : '开始实体识别' }}</button><select v-model="selectedId" :disabled="!history.length" @change="resetDraft"><option :value="null">识别结果版本</option><option v-for="item in history" :key="item.entityResultId" :value="item.entityResultId">{{ formatVersion(item) }}</option></select><button class="btn ghost sm" :disabled="!locatedIndexes.length" @click="navigate(-1)">上一处</button><button class="btn ghost sm" :disabled="!locatedIndexes.length" @click="navigate(1)">下一处</button><span v-if="selected" class="muted" style="margin-left:auto">模型: {{ selected.modelName || '-' }} · {{ selected.modelVersion || '-' }}</span></div>
        <div v-if="!selected" class="empty">点击上方按钮开始实体识别</div>
        <div v-else class="result-sect">
          <div class="sect-head"><h4>📌 文书实体识别结果</h4><div class="flex"><span class="tag" :class="selected.confirmedAt?'green':'gold'">{{ selected.confirmedAt ? '已确认' : '待确认' }}</span><button v-if="!selected.confirmedAt&&canConfirm" class="btn success sm" @click="confirm">确认结果</button></div></div>
          <div v-if="ignoredCount" class="context-notice info">已忽略 {{ ignoredCount }} 个旧类别实体；当前仅使用确定的六类。</div>
          <div v-for="type in entityTypes" :key="type.code" class="entity-group"><div class="eg-title">{{ type.label }}<span class="cnt">{{ grouped[type.code]?.length || 0 }} 项</span></div><button v-for="entry in grouped[type.code] || []" :key="entry.index" class="chip" :class="{active:entry.index===focusedIndex}" type="button" @click="focus(entry.index)">{{ entry.item.text }}<span class="pos">{{ positionLabel(entry.item) }}</span></button></div>
          <hr class="divider">
          <div class="table-wrap"><table class="table field-table"><thead><tr><th>类别</th><th>原文实体</th><th>规范值</th><th>置信度</th><th>定位</th><th v-if="!selected.confirmedAt">操作</th></tr></thead><tbody><tr v-for="(item,index) in draft" :key="index"><td><select v-model="item.type" :disabled="selected.confirmedAt"><option v-for="type in entityTypes" :key="type.code" :value="type.code">{{ type.label }}</option></select></td><td class="fv"><input v-model="item.text" :disabled="selected.confirmedAt" @blur="locate(item)"></td><td class="fv"><input v-model="item.normalizedText" :disabled="selected.confirmedAt" placeholder="可选"></td><td><input v-model.number="item.confidence" :disabled="selected.confirmedAt" class="confidence" type="number" min="0" max="1" step="0.01"></td><td><button class="btn ghost sm" :disabled="!isLocated(item)" @click="focus(index)">{{ isLocated(item)?'定位':'未定位' }}</button></td><td v-if="!selected.confirmedAt"><button class="btn ghost danger sm" @click="draft.splice(index,1)">删除</button></td></tr></tbody></table></div>
          <button v-if="!selected.confirmedAt" class="btn ghost sm mt8" @click="draft.push({type:'SUSPECT',text:'',normalizedText:null,confidence:null})">+ 新增实体</button>
        </div>
      </div>
    </div>
  </LegalFeatureContext>
</template>

<script setup>
import { computed, nextTick, ref, toRaw } from 'vue'
import { ElMessage } from 'element-plus'
import LegalFeatureContext from '../components/common/LegalFeatureContext.vue'
import { hasPermission } from '../auth/session'
import { confirmEntityResult, getEntityRecognitionJob, listEntityResults, startEntityRecognition } from '../api/processing'
import { waitForJob } from '../utils/jobs'

const entityTypes = [
  {code:'SUSPECT',label:'犯罪嫌疑人',css:'ent-suspect'},{code:'CRIME',label:'罪名',css:'ent-charge'},{code:'DRUG',label:'毒品种类',css:'ent-drug'},
  {code:'LOCATION',label:'地名',css:'ent-place'},{code:'ORGANIZATION',label:'组织机构名',css:'ent-org'},{code:'TIME',label:'时间',css:'ent-time'}
]
const typeMap = Object.fromEntries(entityTypes.map((item)=>[item.code,item]))
const document = ref(null), history = ref([]), selectedId = ref(null), draft = ref([]), ignoredCount = ref(0), running = ref(false), focusedIndex = ref(-1)
const anchors = new Map(), canConfirm = hasPermission('AI_EXECUTE')
const selected = computed(()=>history.value.find((item)=>item.entityResultId===selectedId.value))
const grouped = computed(()=>draft.value.reduce((result,item,index)=>{(result[item.type] ||= []).push({item,index});return result},{}))
const locatedIndexes = computed(()=>draft.value.map((item,index)=>isLocated(item)?index:null).filter(Number.isInteger))
const segments = computed(()=>{const source=document.value?.rawText||'';if(!source)return[];const located=draft.value.map((item,index)=>({index,start:startOffset(item),end:endOffset(item),item})).filter(({item,start,end})=>validRange(source,item.text,start,end));const bounds=new Set([0,source.length]);located.forEach(({start,end})=>{bounds.add(start);bounds.add(end)});const ordered=[...bounds].sort((a,b)=>a-b);return ordered.slice(0,-1).map((start,i)=>{const end=ordered[i+1],active=located.filter((entry)=>entry.start<end&&entry.end>start);return{key:`${start}-${end}`,text:source.slice(start,end),indexes:active.map((entry)=>entry.index),starts:active.filter((entry)=>entry.start===start).map((entry)=>entry.index)}})})

let documentRequest = 0
async function setDocument(value){const request=++documentRequest;document.value=value;history.value=[];selectedId.value=null;draft.value=[];if(value){try{const results=await listEntityResults(value.caseId,value.docId);if(request!==documentRequest)return;history.value=results;selectedId.value=history.value[0]?.entityResultId||null;resetDraft()}catch(error){if(request===documentRequest)ElMessage.error(error.message)}}}
function resetDraft(){focusedIndex.value=-1;anchors.clear();const entities=toRaw((selected.value?.finalEntities||selected.value?.originalEntities)?.entities||[]);draft.value=structuredClone(entities.filter((item)=>typeMap[item.type]));ignoredCount.value=entities.length-draft.value.length}
async function recognize(){running.value=true;try{const created=await startEntityRecognition(document.value.caseId,document.value.docId),completed=await waitForJob(()=>getEntityRecognitionJob(document.value.caseId,document.value.docId,created.requestId));if(completed){history.value=await listEntityResults(document.value.caseId,document.value.docId);selectedId.value=completed.entityResultId;resetDraft();ElMessage.success('实体识别完成')}else ElMessage.info('任务仍在后台执行')}catch(error){ElMessage.error(error.message)}finally{running.value=false}}
async function confirm(){if(!draft.value.length||draft.value.some((item)=>!item.text?.trim()))return ElMessage.warning('实体文本不能为空');if(draft.value.some((item)=>!document.value.rawText.includes(item.text.trim())))return ElMessage.warning('每个实体文本必须存在于解析原文中');try{const original=selected.value.originalEntities||{};const result=await confirmEntityResult(document.value.caseId,document.value.docId,selectedId.value,{schemaVersion:original.schemaVersion||'lexpro.entity.v2',offsetUnit:original.offsetUnit||'UTF16_CODE_UNIT',sourceTextSha256:original.sourceTextSha256,entities:draft.value});const index=history.value.findIndex((item)=>item.entityResultId===selectedId.value);history.value[index]=result;resetDraft();ElMessage.success('实体结果已确认')}catch(error){ElMessage.error(error.message)}}
function locate(item){const source=document.value?.rawText||'',value=item.text?.trim();clearLocation(item);item.text=value||'';if(!value)return;const start=source.indexOf(value);if(start<0)return ElMessage.warning('该实体未在原文中出现');item.globalStartUtf16=start;item.globalEndUtf16=start+value.length}
function focus(index,scroll=true){focusedIndex.value=index;if(scroll&&isLocated(draft.value[index]))nextTick(()=>anchors.get(index)?.scrollIntoView({behavior:'smooth',block:'center'}))}
function navigate(direction){const indexes=locatedIndexes.value,current=indexes.indexOf(focusedIndex.value),next=current<0?(direction>0?0:indexes.length-1):(current+direction+indexes.length)%indexes.length;focus(indexes[next])}
function registerAnchor(index,element){if(element)anchors.set(index,element);else anchors.delete(index)}
function typeMeta(code){return typeMap[code]||{label:code||'未知',css:''}}
function startOffset(item){return Number.isInteger(item.globalStartUtf16)?item.globalStartUtf16:item.startOffset}function endOffset(item){return Number.isInteger(item.globalEndUtf16)?item.globalEndUtf16:item.endOffset}
function validRange(source,text,start,end){return Number.isInteger(start)&&Number.isInteger(end)&&start>=0&&end>start&&end<=source.length&&source.slice(start,end)===text}function isLocated(item){return validRange(document.value?.rawText||'',item.text,startOffset(item),endOffset(item))}
function clearLocation(item){['blockId','blockStartUtf16','blockEndUtf16','globalStartUtf16','globalEndUtf16','startOffset','endOffset','occurrenceIndex','prefix','suffix'].forEach((key)=>delete item[key])}
function positionLabel(item){return isLocated(item)?`${startOffset(item)}-${endOffset(item)}`:'未定位'}
function formatVersion(item){return `${new Date(item.createdAt).toLocaleString('zh-CN')}${item.confirmedAt?' · 已确认':''}`}
</script>

<style scoped>
.analysis-bar select,.field-table select,.field-table input{padding:6px 9px;border:1px solid var(--border);border-radius:7px;background:#fff}.entity-preview{max-height:520px}.entity-anchor{display:inline;scroll-margin-top:80px}.ent-hl{cursor:pointer}.ent-hl.focused{box-shadow:0 0 0 2px var(--blue)}.chip{cursor:pointer}.chip.active{border-color:var(--blue);background:var(--blue-light)}.confidence{width:84px}.context-notice{margin-bottom:12px;padding:9px 12px;border-radius:7px;font-size:12.5px}.context-notice.info{color:var(--navy-700);background:var(--blue-light)}
</style>
