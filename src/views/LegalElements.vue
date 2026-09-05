<template>
  <LegalFeatureContext title="法律要素识别" @document="setDocument">
    <template #preview>
      <div v-if="document?.rawText" class="doc-preview evidence-preview"><template v-for="segment in segments" :key="segment.key"><i v-for="key in segment.starts" :key="key" :ref="(element)=>registerAnchor(key,element)" class="evidence-anchor"/><mark v-if="segment.keys.length" class="evidence-mark" :class="{focused:segment.keys.includes(focusedKey)}" @click="focusEvidence(segment.keys[0],false)">{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></div><div v-else class="doc-preview">选择卷宗后显示解析文本</div>
    </template>

    <div class="card mt16"><div class="card-title"><span class="bar"></span>法律要素识别</div><div class="card-pad">
      <div class="analysis-bar"><button class="btn primary sm" :disabled="!document||running" @click="recognize">{{ running?'识别中…':'开始要素识别' }}</button><select v-model="selectedId" :disabled="!history.length"><option :value="null">识别结果版本</option><option v-for="item in history" :key="item.elementResultId" :value="item.elementResultId">{{ formatVersion(item) }}</option></select><button class="btn ghost sm" :disabled="!locatedEvidence.length" @click="navigate(-1)">上一处</button><button class="btn ghost sm" :disabled="!locatedEvidence.length" @click="navigate(1)">下一处</button><span v-if="selected" class="muted" style="margin-left:auto">{{ selected.confirmedAt?'已确认':'待人工确认' }}</span></div>
      <div v-if="!elements.length" class="empty">点击上方按钮开始法律要素识别</div>
      <div v-else class="result-sect"><div class="sect-head"><h4>🧩 法律要素识别结果</h4><div class="flex"><span class="tag" :class="selected.confirmedAt?'green':'gold'">{{ selected.confirmedAt?'已确认':'待确认' }}</span><button v-if="!selected.confirmedAt&&canConfirm" class="btn success sm" @click="confirm">确认并保存</button></div></div>
        <div v-if="selected.caseCause||draft.caseCause" class="cause-banner"><b>案由:</b> {{ selected.caseCause||draft.caseCause }}<span v-if="draft.courtName"> · {{ draft.courtName }}</span><span class="mode">模型:{{ selected.modelName||'未记录' }}</span></div>
        <div class="card"><div class="card-pad">
          <div class="check-rows">
            <div v-for="(item,index) in elements" :key="index" class="check-row">
              <span class="nm" :title="item.content">{{ item.name }}</span>
              <template v-if="isBooleanElement(item)">
                <button type="button" class="switch" :class="{on:item.value===true}" :disabled="readonly" role="switch" :aria-label="item.name" :aria-checked="item.value===true" title="点击切换" @click="item.value=item.value!==true"></button>
                <span class="val">{{ item.value===true?'是':item.value===false?'否':'未识别' }}</span>
              </template>
              <span v-else class="in"><input :type="isNumericElement(item)?'number':'text'" :value="elementValue(item)" :disabled="readonly" :aria-label="item.name" placeholder="未识别" step="any" @input="editValue(item,$event.target.value)"></span>
              <button v-if="item.evidence?.some(evidence=>evidenceLocation(evidence))" class="btn ghost sm locate-btn" :title="item.evidence.map(evidence=>evidence.quote).join('；')" @click="locateElement(index)">定位原文</button>
            </div>
          </div>
          <div v-if="warnings.length" class="mt8 element-warnings">⚠ {{ warnings.join('；') }}</div>
        </div></div>
      </div>
    </div></div>
  </LegalFeatureContext>
</template>

<script setup>
import { computed, nextTick, ref, toRaw, watch } from 'vue'
import { ElMessage } from 'element-plus'
import LegalFeatureContext from '../components/common/LegalFeatureContext.vue'
import { hasPermission } from '../auth/session'
import { confirmLegalElementResult, getLegalElementJob, listLegalElementResults, startLegalElementRecognition } from '../api/processing'
import { waitForJob } from '../utils/jobs'

const document=ref(null),history=ref([]),selectedId=ref(null),running=ref(false),focusedKey=ref(null),anchors=new Map()
const canConfirm=hasPermission('AI_EXECUTE'),draft=ref({elements:[]})
const booleanNames=new Set(['自首','坦白','累犯','立功','认罪认罚','主犯','从犯','犯罪既遂','犯罪未遂','犯罪中止','缓刑','谅解','悔罪','前科劣迹','初犯偶犯','未成年','多次盗窃','入室盗窃','扒窃','电信网络诈骗','合同诈骗','持械抢劫','入户抢劫','致人重伤','致人死亡','防卫过当'])
const numberNames=new Set(['涉案金额','盗窃数额','诈骗数额','抢劫数额','罚金','退赔金额','赔偿金额','毒品数量','刑期月数'])
let documentVersion=0
const selected=computed(()=>history.value.find((item)=>item.elementResultId===selectedId.value))
const elements=computed(()=>draft.value.elements||[])
const warnings=computed(()=>draft.value.validation?.warnings||selected.value?.validationReport?.warnings||[])
watch(selected,(value)=>{draft.value=structuredClone(toRaw(value?.finalElements||value?.originalElements||{elements:[]}));focusedKey.value=null},{immediate:true})
function isBooleanElement(item){return Object.hasOwn(item,'value')&&(booleanNames.has(item.name)||typeof item.value==='boolean')}
function isNumericElement(item){return Object.hasOwn(item,'value')&&(numberNames.has(item.name)||typeof item.value==='number')}
function elementValue(item){return Object.hasOwn(item,'value')?item.value:item.content}
function editValue(item,value){if(!Object.hasOwn(item,'value'))item.content=value;else item.value=value===''?null:isNumericElement(item)?Number(value):value}
function locateElement(index){const entry=locatedEvidence.value.find(entry=>entry.key.startsWith(index+':'));if(entry)focusEvidence(entry.key)}
const readonly=computed(()=>Boolean(selected.value?.confirmedAt))
const locatedEvidence=computed(()=>elements.value.flatMap((item,elementIndex)=>(item.evidence||[]).map((evidence,evidenceIndex)=>({key:evidenceKey(elementIndex,evidenceIndex),evidence,location:evidenceLocation(evidence)}))).filter((entry)=>entry.location))
const segments=computed(()=>{const source=document.value?.rawText||'';if(!source)return[];const located=locatedEvidence.value.map((entry)=>({...entry,...entry.location}));const bounds=new Set([0,source.length]);located.forEach(({start,end})=>{bounds.add(start);bounds.add(end)});const ordered=[...bounds].sort((a,b)=>a-b);return ordered.slice(0,-1).map((start,index)=>{const end=ordered[index+1],active=located.filter((entry)=>entry.start<end&&entry.end>start);return{key:`${start}-${end}`,text:source.slice(start,end),keys:active.map((entry)=>entry.key),starts:active.filter((entry)=>entry.start===start).map((entry)=>entry.key)}})})

async function setDocument(value){const version=++documentVersion;document.value=value;history.value=[];selectedId.value=null;focusedKey.value=null;if(value)try{const results=await listLegalElementResults(value.caseId,value.docId);if(version===documentVersion){history.value=results;selectedId.value=results[0]?.elementResultId||null}}catch(error){if(version===documentVersion)ElMessage.error(error.message)}}
async function recognize(){running.value=true;try{const created=await startLegalElementRecognition(document.value.caseId,document.value.docId),completed=await waitForJob(()=>getLegalElementJob(document.value.caseId,document.value.docId,created.requestId));if(completed){history.value=await listLegalElementResults(document.value.caseId,document.value.docId);selectedId.value=completed.elementResultId;ElMessage.success('法律要素识别完成')}else ElMessage.info('任务仍在后台执行')}catch(error){ElMessage.error(error.message)}finally{running.value=false}}

function evidenceKey(elementIndex,evidenceIndex){return `${elementIndex}:${evidenceIndex}`}
function evidenceLocation(evidence){const source=document.value?.rawText||'',quote=evidence?.quote?.trim();if(!quote)return null;const explicit=Number.isInteger(evidence.startOffset)&&source.slice(evidence.startOffset,evidence.endOffset)===quote?evidence.startOffset:-1,start=explicit>=0?explicit:source.indexOf(quote);return start<0?null:{start,end:start+quote.length}}
function locateEvidence(evidence){const location=evidenceLocation({...evidence,startOffset:null,endOffset:null});if(location){evidence.startOffset=location.start;evidence.endOffset=location.end}else{delete evidence.startOffset;delete evidence.endOffset}}
function focusEvidence(key,scroll=true){focusedKey.value=key;if(scroll)nextTick(()=>anchors.get(key)?.scrollIntoView({behavior:'smooth',block:'center'}))}function navigate(direction){const keys=locatedEvidence.value.map((entry)=>entry.key),current=keys.indexOf(focusedKey.value),next=current<0?(direction>0?0:keys.length-1):(current+direction+keys.length)%keys.length;focusEvidence(keys[next])}function registerAnchor(key,element){if(element)anchors.set(key,element);else anchors.delete(key)}
async function confirm(){for(const item of elements.value){if(!item.code||!item.name?.trim()||!item.content?.trim())return ElMessage.warning('请完整填写要素代码、名称和内容');if(!item.evidence?.length||item.evidence.some((evidence)=>!evidence.quote?.trim()||!document.value.rawText.includes(evidence.quote.trim())))return ElMessage.warning('每个要素至少需要一条可在原文定位的引文')}try{const result=await confirmLegalElementResult(document.value.caseId,document.value.docId,selectedId.value,draft.value),index=history.value.findIndex((item)=>item.elementResultId===selectedId.value);history.value[index]=result;ElMessage.success('法律要素已确认')}catch(error){ElMessage.error(error.message)}}
function formatVersion(item){return `${new Date(item.createdAt).toLocaleString('zh-CN')}${item.confirmedAt?' · 已确认':''}`}
</script>

<style scoped>
.analysis-bar select{padding:6px 9px;border:1px solid var(--border);border-radius:7px;background:#fff}
.evidence-preview{max-height:520px}.evidence-anchor{display:inline;scroll-margin-top:80px}.evidence-mark{color:inherit;background:#fdf3e0;border-bottom:2px solid var(--gold);cursor:pointer}.evidence-mark.focused{box-shadow:0 0 0 2px var(--gold)}
.element-warnings{color:var(--orange);font-size:12.5px}.locate-btn{margin-left:auto}.check-row .in input:disabled{color:var(--text);opacity:1}
</style>
