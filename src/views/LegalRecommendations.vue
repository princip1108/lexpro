<template>
  <div v-if="libraryMode">
    <div class="card cb-search-card">
      <div class="cb-filters">
        <div class="cb-f cb-f-type"><span class="cb-lbl">案件类型</span><select v-model="filters.caseType"><option value="">全部类型</option><option v-for="type in caseTypes" :key="type">{{ type }}</option></select></div>
        <div class="cb-f cb-f-cause"><span class="cb-lbl">罪名 / 案由</span><input v-model="filters.caseCause" placeholder="全部罪名 / 案由"></div>
        <div class="cb-f cb-f-src"><span class="cb-lbl">来源</span><input v-model="filters.sourceName" placeholder="全部来源"></div>
        <div class="cb-f cb-f-lvl"><span class="cb-lbl">层级</span><input v-model="filters.caseLevel" placeholder="全部层级"></div>
      </div>
      <div class="cb-datekw">
        <div class="cb-f cb-f-date"><span class="cb-lbl">裁判日期<em class="cb-hint" title="留空不限;只填一端表示该日期之前 / 之后">(单端=之前/之后)</em></span><span class="cb-date-row"><input v-model="filters.judgmentDateFrom" type="date" aria-label="裁判起始日期" @change="searchLibrary"><span class="cb-sep">至</span><input v-model="filters.judgmentDateTo" type="date" aria-label="裁判结束日期" @change="searchLibrary"></span></div>
        <div class="cb-f cb-f-kw"><span class="cb-lbl">全文检索</span><input v-model="filters.keyword" class="cb-kw" placeholder="标题 / 案号 / 关键词 / 事实 / 裁判结果 / 要旨 / 来源…" @keydown.enter="searchLibrary"></div>
        <label class="favorite-check"><input v-model="filters.favoritesOnly" type="checkbox"> 仅收藏</label><button class="btn primary" @click="searchLibrary">检索</button><button class="btn ghost" @click="resetLibrary">重置</button>
      </div>
    </div>
    <div v-loading="loadingLibrary" class="card"><div class="cb-pager cb-top"><div class="cb-pg-l"><span class="cb-pg-total">共 <b>{{ page.totalItems }}</b> 条案例</span><span class="cb-pg-size">每页 <select v-model="filters.size" @change="searchLibrary"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</span></div><div class="cb-pg-r"><button class="pg-btn" :disabled="filters.page<=1" @click="changePage(-1)">上一页</button><span class="cb-pg-cur">第 {{ filters.page }} / {{ Math.max(page.totalPages,1) }} 页</span><button class="pg-btn" :disabled="filters.page>=page.totalPages" @click="changePage(1)">下一页</button></div></div><div class="table-wrap"><table class="table cb-table"><thead><tr><th>案例编号</th><th>案例标题</th><th>案件类型</th><th>罪名 / 案由</th><th>来源</th><th>案例层级</th><th>裁判日期</th><th>操作</th></tr></thead><tbody><tr v-if="!page.items.length"><td colspan="8" class="empty">未检索到案例,请调整条件</td></tr><tr v-for="item in page.items" :key="item.typicalCaseId"><td class="mono cb-caseid" :title="item.externalCaseId || item.caseNumber">{{ item.externalCaseId || item.caseNumber || '-' }}</td><td class="cb-title"><span :title="item.title">{{ item.title }}</span></td><td><span class="tag" :class="typeColor(item.caseType)">{{ item.caseType||'-' }}</span></td><td><span class="tag gold">{{ item.caseCause||'-' }}</span></td><td><span class="muted-sm m-ellip" :title="item.sourceName||''">{{ item.sourceName||'-' }}</span></td><td><span class="muted-sm m-ellip" :title="item.caseLevel||''">{{ item.caseLevel||'-' }}</span></td><td>{{ item.judgmentDate||'-' }}</td><td><button class="btn ghost sm" @click="openTypicalCase(item)">详情</button> <button class="btn ghost sm" @click="toggleFavorite(item)">{{ item.favorite?'取消收藏':'收藏' }}</button></td></tr></tbody></table></div><div class="cb-pager cb-bottom"><span>当前第 {{ filters.page }} 页</span><div class="cb-pg-r"><button class="pg-btn" :disabled="filters.page<=1" @click="changePage(-1)">上一页</button><button class="pg-btn" :disabled="filters.page>=page.totalPages" @click="changePage(1)">下一页</button></div></div></div>
  </div>

  <LegalFeatureContext v-else title="典型案例推送" @case="setCase" @document="document=$event">
    <div class="card mt16"><div class="card-title"><span class="bar"></span>典型案例推送</div><div class="card-pad">
      <div class="analysis-bar"><select v-model="selectedSummaryId" :disabled="!summaries.length"><option :value="null">选择案件事实摘要</option><option v-for="item in summaries" :key="item.summaryId" :value="item.summaryId">{{ summaryLabel(item) }}</option></select><select v-model="recommendCaseType"><option value="">全部案件类型</option><option v-for="type in caseTypes" :key="type">{{ type }}</option></select><label class="limit-field">推荐数量 <input v-model.number="limit" type="number" min="1" max="20"></label><button class="btn ghost sm" :disabled="!document||generating" @click="generateSummary">{{ generating?'生成中…':'生成事实摘要' }}</button><button class="btn primary sm" :disabled="!selectedSummaryId||searching" @click="analyzeAndRecommend">{{ searching?'检索中…':'分析并推荐' }}</button></div>
      <div v-if="selectedSummary" class="cause-banner"><b>案件事实摘要</b><span>{{ selectedSummary.summaryText }}</span><span class="mode">{{ selectedSummary.modelName||'-' }}</span></div>
      <div v-if="detail||analysis" class="result-sect">
        <div class="sect-head"><h4>🔗 典型案例推送结果</h4><div class="flex"><span class="tag">案由:{{ detail?.items?.[0]?.caseCause||'待确认' }}</span><span class="muted">模型:{{ detail?.modelName||'待返回' }}</span></div></div>
        <div class="g2 grid">
          <div class="card"><div class="card-title" style="font-size:13px"><span class="bar"></span>争议焦点</div><div class="card-pad">
            <div v-for="(issue,index) in displayIssues" :key="issue.issueId??index" class="focus-item"><span class="n">{{ index+1 }}</span><span class="t">{{ issue.text }}</span><span class="badge b-blue">{{ percent(issue.reliability) }}</span></div>
            <div v-if="!displayIssues.length" class="empty">未生成争议焦点</div>
          </div></div>
          <div>
            <div class="muted-sm" style="margin-bottom:8px">推荐案例(按融合相似度排序)</div>
            <div v-if="!detail?.items?.length" class="empty">{{ searching?'正在检索参考案例…':'未检索到参考案例' }}</div>
            <div v-for="(item,index) in detail?.items||[]" :key="item.itemId" class="rank-item">
              <div class="rt"><h4><button class="case-link" @click="openTypicalCase(item)">{{ index+1 }}. {{ item.title }}</button></h4><span class="score">{{ score(item.rankingScore??item.score) }}<small> 综合分</small></span></div>
              <div class="meta"><span class="tag gold">{{ item.caseCause||'' }}</span><span>{{ item.court||'' }}</span><span>{{ item.judgmentDate||'' }}</span><span>{{ resultCases[item.typicalCaseId]?.procedure||'' }}</span><span>案号 {{ item.caseNumber||item.externalCaseId||'' }}</span></div>
              <div class="sim-bars"><span style="flex:1">事实相似 <b>{{ score(item.reasons?.factSimilarity) }}</b><div class="sim-bar"><i :style="{width:barWidth(item.reasons?.factSimilarity)}"></i></div></span><span style="flex:1">焦点相似 <b>{{ score(item.reasons?.focusSimilarity??item.reasons?.issueScore) }}</b><div class="sim-bar"><i :style="{width:barWidth(item.reasons?.focusSimilarity??item.reasons?.issueScore)}"></i></div></span></div>
              <div v-if="resultCases[item.typicalCaseId]?.disputeFocus?.length" class="meta mt8">争议焦点:{{ resultCases[item.typicalCaseId].disputeFocus.join('；') }}</div>
              <div class="fact">{{ factExcerpt(resultCases[item.typicalCaseId]?.fact) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div></div>
  </LegalFeatureContext>

  <div v-if="caseModal" class="overlay" @click.self="caseModal=false"><div class="modal case-modal"><div class="modal-header"><h3>案例详情</h3><button class="close-x" @click="caseModal=false">×</button></div><div v-loading="loadingCase" class="modal-body"><template v-if="caseDetail"><div class="case-head"><div><h2>{{ caseDetail.title }}</h2><div class="sub">{{ caseDetail.caseNumber||'案号未记录' }}</div></div><span class="tag gold">{{ caseDetail.caseCause||caseDetail.caseType||'-' }}</span></div><div class="card"><div class="kv-grid"><div><div class="k">案件类型</div><div class="v">{{ caseDetail.caseType||'-' }}</div></div><div><div class="k">法院</div><div class="v">{{ caseDetail.court||'-' }}</div></div><div><div class="k">裁判日期</div><div class="v">{{ caseDetail.judgmentDate||'-' }}</div></div><div><div class="k">案例层级</div><div class="v">{{ caseDetail.caseLevel||'-' }}</div></div><div><div class="k">来源</div><div class="v">{{ caseDetail.sourceName||'-' }}</div></div></div></div><div v-for="section in detailSections" :key="section.title" v-show="section.text" class="detail-section"><h4>{{ section.title }}</h4><p>{{ section.text }}</p></div></template></div></div></div>
</template>

<script setup>
import { computed,onMounted,reactive,ref,watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import LegalFeatureContext from '../components/common/LegalFeatureContext.vue'
import { getCaseSummaryJob,listCaseSummaries,startCaseSummary } from '../api/processing'
import { createRecommendation,createRecommendationAnalysis,favoriteTypicalCase,getRecommendation,getTypicalCase,listRecommendations,listTypicalCases,unfavoriteTypicalCase } from '../api/recommendations'
import { waitForJob } from '../utils/jobs'

const route=useRoute(),libraryMode=computed(()=>route.query.view==='library'),caseTypes=['刑事','民事','行政','公益诉讼','执行','赔偿']
const resultCases=ref({})
const displayIssues=computed(()=>{const issues=analysis.value?.issues||detail.value?.queryDisputeFocus||[];return Array.isArray(issues)?issues.map(issue=>typeof issue==='string'?{text:issue}:issue):[]})
function barWidth(value){return `${Math.max(0,Math.min(1,Number(value)||0))*100}%`}
function factExcerpt(value){return value?value.slice(0,160)+(value.length>160?'…':''):''}
async function loadResultCases(result){
  resultCases.value={}
  const rows=await Promise.allSettled((result?.items||[]).map(item=>getTypicalCase(item.typicalCaseId)))
  if(detail.value?.recommendId!==result?.recommendId)return
  resultCases.value=Object.fromEntries(rows.filter(row=>row.status==='fulfilled').map(row=>[row.value.typicalCaseId,row.value]))
  if(rows.some(row=>row.status==='rejected'))ElMessage.warning('部分案例正文加载失败，可点击案例标题重试')
}
const loadingLibrary=ref(false),filters=reactive({keyword:'',caseCause:'',caseType:'',sourceName:'',caseLevel:'',judgmentDateFrom:'',judgmentDateTo:'',favoritesOnly:false,page:1,size:20}),page=reactive({items:[],totalItems:0,totalPages:0})
const caseId=ref(null),document=ref(null),summaries=ref([]),selectedSummaryId=ref(null),recommendCaseType=ref(''),limit=ref(5),analysis=ref(null),history=ref([]),historyId=ref(null),detail=ref(null),generating=ref(false),searching=ref(false)
const caseModal=ref(false),loadingCase=ref(false),caseDetail=ref(null),selectedSummary=computed(()=>summaries.value.find((item)=>item.summaryId===selectedSummaryId.value)),detailSections=computed(()=>caseDetail.value?[{title:'案件事实',text:caseDetail.value.fact},{title:'案例摘要',text:caseDetail.value.summary},{title:'检察过程',text:caseDetail.value.prosecutorialProcess},{title:'裁判结果',text:caseDetail.value.adjudicationResult},{title:'裁判理由',text:caseDetail.value.reasoning},{title:'典型意义',text:caseDetail.value.guidingSignificance},{title:'案例正文',text:caseDetail.value.content}]:[])

onMounted(()=>{if(libraryMode.value)loadTypicalCases()})
watch(detail,(value)=>{if(value)loadResultCases(value);else resultCases.value={}})
watch(libraryMode,(value)=>{if(value&&!page.items.length)loadTypicalCases()})
async function loadTypicalCases(){loadingLibrary.value=true;try{Object.assign(page,await listTypicalCases(filters))}catch(error){ElMessage.error(error.message)}finally{loadingLibrary.value=false}}
function searchLibrary(){if(filters.judgmentDateFrom&&filters.judgmentDateTo&&filters.judgmentDateFrom>filters.judgmentDateTo)return ElMessage.warning('起始日期不能晚于结束日期');filters.page=1;loadTypicalCases()}function resetLibrary(){Object.assign(filters,{keyword:'',caseCause:'',caseType:'',sourceName:'',caseLevel:'',judgmentDateFrom:'',judgmentDateTo:'',favoritesOnly:false,page:1,size:20});loadTypicalCases()}function changePage(direction){filters.page+=direction;loadTypicalCases()}
async function toggleFavorite(item){try{await(item.favorite?unfavoriteTypicalCase(item.typicalCaseId):favoriteTypicalCase(item.typicalCaseId));await loadTypicalCases();ElMessage.success(item.favorite?'已取消收藏':'已收藏')}catch(error){ElMessage.error(error.message)}}
async function setCase(value){caseId.value=value;summaries.value=[];selectedSummaryId.value=null;analysis.value=null;history.value=[];detail.value=null;if(value)await Promise.all([loadSummaries(),loadHistory()])}
async function loadSummaries(preferredId=null){try{const groups=await Promise.all(['FACT','PROCESS','CONCLUSION','FULL'].map((type)=>listCaseSummaries(caseId.value,type)));summaries.value=groups.flat().sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt));selectedSummaryId.value=(summaries.value.find((item)=>item.summaryId===preferredId)||summaries.value.find((item)=>item.current)||summaries.value[0])?.summaryId||null}catch(error){ElMessage.error(error.message)}}
async function generateSummary(){if(!document.value)return ElMessage.warning('请先选择解析成功的文书');generating.value=true;try{const created=await startCaseSummary(caseId.value,{summaryType:'FACT',sourceDocIds:[document.value.docId]}),completed=await waitForJob(()=>getCaseSummaryJob(caseId.value,created.requestId));if(completed){await loadSummaries(completed.summaryId);ElMessage.success('事实摘要生成完成')}}catch(error){ElMessage.error(error.message)}finally{generating.value=false}}
async function analyzeAndRecommend(){searching.value=true;analysis.value=null;try{const payload={sourceSummaryId:selectedSummaryId.value,limit:limit.value,partnerFilters:recommendCaseType.value?{caseType:recommendCaseType.value}:null};if((import.meta.env.VITE_TYPICAL_CASE_PROVIDER||'PARTNER').toUpperCase()==='PARTNER'){analysis.value=await createRecommendationAnalysis(caseId.value,{sourceSummaryId:selectedSummaryId.value});payload.analysisToken=analysis.value.analysisToken}detail.value=await createRecommendation(caseId.value,payload);history.value=await listRecommendations(caseId.value);historyId.value=detail.value.recommendId||history.value[0]?.recommendId||null;ElMessage.success('典型案例推荐完成')}catch(error){ElMessage.error(error.message)}finally{searching.value=false}}
async function loadHistory(){try{history.value=await listRecommendations(caseId.value);if(history.value.length){historyId.value=history.value[0].recommendId;await selectHistory()}}catch(error){ElMessage.error(error.message)}}async function selectHistory(){if(historyId.value)detail.value=await getRecommendation(caseId.value,historyId.value)}
async function openTypicalCase(item){caseModal.value=true;caseDetail.value=null;loadingCase.value=true;try{caseDetail.value=await getTypicalCase(item.typicalCaseId)}catch(error){ElMessage.error(error.message)}finally{loadingCase.value=false}}
function typeColor(type){return type==='刑事'?'gold':type==='民事'?'green':type==='公益诉讼'?'red':'gray'}function summaryLabel(item){return `${item.current?'当前':`V${item.versionNo}`}${item.confirmedAt?' · 已确认':''} · ${formatTime(item.createdAt)}`}
function reasonList(value){if(Array.isArray(value))return value.map((item)=>typeof item==='string'?item:(item.text||item.label||item.type||JSON.stringify(item)));if(!value||typeof value!=='object')return[];const reasons=[];if(value.factSimilarity!=null)reasons.push(`事实相似度 ${score(value.factSimilarity)}`);if(value.issueScore!=null)reasons.push(`争议焦点 ${score(value.issueScore)}`);return reasons}
function score(value){return value==null?'-':Number(value).toFixed(3)}function percent(value){return value==null?'-':`${Math.round(Number(value)*100)}%`}function duration(value){return value==null?'-':`${Math.round(Number(value))} ms`}function formatTime(value){return value?new Date(value).toLocaleString('zh-CN'):'-'}
</script>

<style scoped>
.cb-f input{height:34px;padding:5px 10px;border:1px solid var(--border);border-radius:8px;background:#fff}.cb-f-cause input{width:250px}.cb-f-src input{width:168px}.cb-f-lvl input{width:132px}.favorite-check,.limit-field{display:flex;align-items:center;gap:6px;color:var(--muted);font-size:13px;white-space:nowrap}.favorite-check input{accent-color:var(--blue)}.analysis-bar select,.sect-head select,.limit-field input{height:34px;padding:5px 9px;border:1px solid var(--border);border-radius:7px;background:#fff}.limit-field input{width:58px}.cause-banner>span:not(.mode){flex:1;min-width:240px;line-height:1.7}.focus-item small{display:block;color:var(--muted);font-weight:400;margin-top:3px}.case-link{color:var(--navy-800);font:inherit;font-weight:600;text-align:left;background:none;border:0}.case-link:hover{color:var(--blue)}.case-modal{width:min(94vw,880px)}.detail-section{margin-top:18px}.detail-section h4{margin-bottom:8px;color:var(--navy-800)}.detail-section p{white-space:pre-wrap;line-height:1.9;color:#46566b}
</style>
